#!/usr/bin/env python3
"""Pack the emulated Brilliant Cut planes into a deterministic BCM1 blob.

BCM1 is deliberately tiny and renderer-oriented.  All integers and floats are
little-endian.  The header is ``BCM1`` + uint32 geometry count.  Each geometry
contains uint32 id, plane count and vertex count, then one uint8 arity per
plane, followed by tightly packed float32 XYZ vertices.

The stock renderer calls glDrawArrays(GL_TRIANGLES), so every plane arity is a
multiple of three and the implicit index stream is simply 0..vertexCount-1.
Plane arities are retained because stock alpha/normal bookkeeping is per plane.
"""

from __future__ import annotations

import argparse
import base64
import gzip
import hashlib
import json
import re
import struct
from pathlib import Path


EXPECTED = {
    0: (149, 543, {3: 124, 6: 18, 9: 7}),
    1: (125, 477, {3: 92, 6: 32, 9: 1}),
    2: (149, 528, {3: 125, 6: 21, 9: 3}),
    3: (125, 477, {3: 92, 6: 32, 9: 1}),
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def pack_geometry(document: dict[str, object]) -> bytes:
    geometries = document["geometries"]
    if not isinstance(geometries, list) or len(geometries) != 4:
        raise ValueError("expected exactly four geometries")

    output = bytearray(b"BCM1")
    output += struct.pack("<I", len(geometries))

    for geometry in sorted(geometries, key=lambda item: int(item["type"])):
        geometry_id = int(geometry["type"])
        planes = geometry["planes"]
        if not isinstance(planes, list):
            raise ValueError(f"geometry {geometry_id}: planes is not a list")

        arities = [int(plane["vertex_count"]) for plane in planes]
        vertex_count = sum(arities)
        counts = {arity: arities.count(arity) for arity in sorted(set(arities))}
        expected_planes, expected_vertices, expected_counts = EXPECTED[geometry_id]
        if (len(planes), vertex_count, counts) != (
            expected_planes,
            expected_vertices,
            expected_counts,
        ):
            raise ValueError(
                f"geometry {geometry_id}: unexpected counts "
                f"{len(planes)}, {vertex_count}, {counts}"
            )
        if any(arity % 3 for arity in arities):
            raise ValueError(f"geometry {geometry_id}: non-triangle plane arity")

        output += struct.pack("<III", geometry_id, len(planes), vertex_count)
        output += bytes(arities)
        for plane in planes:
            vertices = plane["vertices"]
            if len(vertices) != int(plane["vertex_count"]):
                raise ValueError(f"geometry {geometry_id}: plane length mismatch")
            for vertex in vertices:
                if len(vertex) != 3:
                    raise ValueError(f"geometry {geometry_id}: vertex is not XYZ")
                output += struct.pack("<3f", *(float(component) for component in vertex))

    return bytes(output)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("--binary-output", type=Path)
    parser.add_argument("--gzip-output", type=Path)
    parser.add_argument("--base64-output", type=Path)
    parser.add_argument("--verify-binary", type=Path)
    parser.add_argument("--verify-gzip", type=Path)
    parser.add_argument("--verify-java-source", type=Path)
    args = parser.parse_args()

    document = json.loads(args.input.read_text(encoding="utf-8"))
    payload = pack_geometry(document)
    compressed = gzip.compress(payload, compresslevel=9, mtime=0)
    encoded = base64.b64encode(compressed).decode("ascii")

    if args.binary_output:
        args.binary_output.write_bytes(payload)
    if args.gzip_output:
        args.gzip_output.write_bytes(compressed)
    if args.base64_output:
        args.base64_output.write_text(encoded + "\n", encoding="ascii", newline="\n")

    if args.verify_binary and args.verify_binary.read_bytes() != payload:
        raise ValueError(f"BCM1 verification failed: {args.verify_binary}")
    if args.verify_gzip and args.verify_gzip.read_bytes() != compressed:
        raise ValueError(f"gzip verification failed: {args.verify_gzip}")
    if args.verify_java_source:
        source = args.verify_java_source.read_text(encoding="utf-8")
        marker = "private static final String PACKED_BCM1_GZIP_BASE64 ="
        if marker not in source:
            raise ValueError("Java source does not contain the BCM1 marker")
        block = source.split(marker, 1)[1].split(";", 1)[0]
        embedded = "".join(re.findall(r'"([A-Za-z0-9+/=]+)"', block))
        if base64.b64decode(embedded) != compressed:
            raise ValueError("Java embedded gzip does not match generated BCM1 payload")

    print(f"BCM1 bytes: {len(payload)}")
    print(f"BCM1 SHA-256: {sha256(payload)}")
    print(f"gzip bytes: {len(compressed)}")
    print(f"gzip SHA-256: {sha256(compressed)}")
    print(f"Base64 chars: {len(encoded)}")


if __name__ == "__main__":
    main()
