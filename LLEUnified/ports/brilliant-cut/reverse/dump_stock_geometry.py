#!/usr/bin/env python3
"""Emulate Samsung Brilliant Cut's ARM32 CreateGeometry and dump its planes.

The stock function contains only deterministic mesh construction, but the
coordinates are encoded as ARM instructions rather than stored as a resource.
This harness executes that one function and replaces the C++ Vector3/Plane
constructors with small Unicorn hooks.  It never runs Android/JNI code.

Dependencies: ``python -m pip install unicorn pyelftools``
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
from collections import Counter
from pathlib import Path

from elftools.elf.elffile import ELFFile
from unicorn import Uc, UC_ARCH_ARM, UC_HOOK_CODE, UC_MODE_ARM
from unicorn.arm_const import UC_ARM_REG_LR, UC_ARM_REG_PC, UC_ARM_REG_R0
from unicorn.arm_const import UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3
from unicorn.arm_const import UC_ARM_REG_SP


PAGE = 0x1000
STACK_BASE = 0x70000000
STACK_SIZE = 0x20000
OBJECT_BASE = 0x71000000
OBJECT_SIZE = 0x10000
RETURN_SENTINEL = 0x72000000

CREATE_GEOMETRY = 0xAAF8
ORACLE_SHA256 = "694E860290A277570992142E965B858DBB8D75FF168030AC0661EDB01B426EC2"
VECTOR3_CTOR = 0x79D4
PLANE_CTORS = {
    0x8448: 3,
    0x895C: 6,
    0x8B74: 9,
}
ADD_PLANE = 0xA79C
NOOP_FUNCTIONS = {0x5958, 0x5A3C, 0xA030}


def align_down(value: int) -> int:
    return value & ~(PAGE - 1)


def align_up(value: int) -> int:
    return (value + PAGE - 1) & ~(PAGE - 1)


def f32(value: int) -> float:
    return struct.unpack("<f", struct.pack("<I", value & 0xFFFFFFFF))[0]


def read_vec3(uc: Uc, address: int) -> list[float]:
    return list(struct.unpack("<3f", bytes(uc.mem_read(address, 12))))


def return_from_hook(uc: Uc) -> None:
    uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))


def map_elf(uc: Uc, binary: Path) -> None:
    with binary.open("rb") as stream:
        elf = ELFFile(stream)
        for segment in elf.iter_segments():
            if segment["p_type"] != "PT_LOAD":
                continue
            vaddr = int(segment["p_vaddr"])
            memsz = int(segment["p_memsz"])
            start = align_down(vaddr)
            end = align_up(vaddr + memsz)
            try:
                uc.mem_map(start, end - start)
            except Exception:
                # Adjacent LOAD segments may share a page.
                pass
            uc.mem_write(vaddr, segment.data())


def dump_geometry(binary: Path, geometry_type: int) -> list[dict[str, object]]:
    uc = Uc(UC_ARCH_ARM, UC_MODE_ARM)
    map_elf(uc, binary)
    uc.mem_map(STACK_BASE, STACK_SIZE)
    uc.mem_map(OBJECT_BASE, OBJECT_SIZE)
    uc.mem_map(RETURN_SENTINEL, PAGE)

    plane_vertices: dict[int, list[list[float]]] = {}
    emitted: list[dict[str, object]] = []

    def hook_code(engine: Uc, address: int, _size: int, _user: object) -> None:
        if address in NOOP_FUNCTIONS:
            return_from_hook(engine)
            return
        if address == VECTOR3_CTOR:
            destination = engine.reg_read(UC_ARM_REG_R0)
            values = [
                f32(engine.reg_read(UC_ARM_REG_R1)),
                f32(engine.reg_read(UC_ARM_REG_R2)),
                f32(engine.reg_read(UC_ARM_REG_R3)),
            ]
            engine.mem_write(destination, struct.pack("<3f", *values))
            engine.reg_write(UC_ARM_REG_R0, destination)
            return_from_hook(engine)
            return
        if address in PLANE_CTORS:
            count = PLANE_CTORS[address]
            destination = engine.reg_read(UC_ARM_REG_R0)
            pointers = [
                engine.reg_read(UC_ARM_REG_R1),
                engine.reg_read(UC_ARM_REG_R2),
                engine.reg_read(UC_ARM_REG_R3),
            ]
            stack_pointer = engine.reg_read(UC_ARM_REG_SP)
            if count > 3:
                raw = bytes(engine.mem_read(stack_pointer, (count - 3) * 4))
                pointers.extend(struct.unpack("<" + "I" * (count - 3), raw))
            plane_vertices[destination] = [read_vec3(engine, p) for p in pointers]
            engine.reg_write(UC_ARM_REG_R0, destination)
            return_from_hook(engine)
            return
        if address == ADD_PLANE:
            plane = engine.reg_read(UC_ARM_REG_R1)
            vertices = plane_vertices.get(plane)
            if vertices is None:
                raise RuntimeError("AddPlane received an unknown Plane at 0x%x" % plane)
            emitted.append({"vertex_count": len(vertices), "vertices": vertices})
            return_from_hook(engine)
            return
        if address == RETURN_SENTINEL:
            engine.emu_stop()

    uc.hook_add(UC_HOOK_CODE, hook_code)
    uc.reg_write(UC_ARM_REG_SP, STACK_BASE + STACK_SIZE - 0x100)
    uc.reg_write(UC_ARM_REG_LR, RETURN_SENTINEL)
    uc.reg_write(UC_ARM_REG_R0, OBJECT_BASE)
    uc.reg_write(UC_ARM_REG_R1, geometry_type)
    uc.emu_start(CREATE_GEOMETRY, RETURN_SENTINEL + 4, count=2_000_000)
    return emitted


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("binary", type=Path)
    parser.add_argument("--output", "-o", type=Path)
    args = parser.parse_args()

    digest = hashlib.sha256(args.binary.read_bytes()).hexdigest().upper()
    if digest != ORACLE_SHA256:
        raise ValueError(f"unexpected Brilliant Cut oracle SHA-256: {digest}")

    result: dict[str, object] = {
        "source": str(args.binary),
        "source_sha256": digest,
        "create_geometry_address": hex(CREATE_GEOMETRY),
        "geometries": [],
    }
    geometries: list[dict[str, object]] = []
    for geometry_type in range(4):
        planes = dump_geometry(args.binary, geometry_type)
        counts = Counter(int(plane["vertex_count"]) for plane in planes)
        geometries.append({
            "type": geometry_type,
            "plane_count": len(planes),
            "vertex_counts": {str(key): counts[key] for key in sorted(counts)},
            "planes": planes,
        })
    result["geometries"] = geometries

    encoded = json.dumps(result, indent=2, sort_keys=False) + "\n"
    if args.output:
        args.output.write_text(encoded, encoding="utf-8", newline="\n")
    else:
        print(encoded, end="")


if __name__ == "__main__":
    main()
