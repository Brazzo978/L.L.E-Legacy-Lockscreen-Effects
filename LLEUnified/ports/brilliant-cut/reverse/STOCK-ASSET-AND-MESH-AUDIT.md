# Brilliant Cut stock asset and mesh audit

This folder records the stock inputs needed for an eventual proprietary ARM64
renderer. It does not wire Brilliant Cut into the application.

## Canonical inputs

The historical oracle is the first-generation Galaxy Tab S 8.4 SM-T705 ANF8
engine:

- binary: `unlock-effects-test/tabs/T705_ANF8_brilliantcut_native/libBrilliantCutEffect.so`
- binary size: 189,664 bytes
- binary SHA-256: `694E860290A277570992142E965B858DBB8D75FF168030AC0661EDB01B426EC2`
- ABI: ELF32, little-endian ARM EABI5
- Ghidra project path: `/tabs/libBrilliantCutEffect.so`

The stock light brush has been extracted to
`res/drawable-nodpi/brilliantcut_light_brush.png`:

- APK member: `Keyguard.apk!/res/drawable-nodpi/brilliantcut_light_brush.png`
- size: 11,661 bytes
- dimensions: 256 x 256
- pixel format: 32-bit ARGB
- SHA-256: `D4A6C4E27203812A506B3C78AA4833D3BEBB09FDEE23171045F8BC87EB6C3151`

The same PNG is byte-identical in the later S4/Note4
`secvisualeffect-res.apk` package. The secvisualeffect loader requests it by
the exact resource name `brilliantcut_light_brush`.

## Stock sounds

The three stock files already present under `res/raw` are byte-identical to the
Tab S, Note4 BOB4, and Note4 BOD2 resources. They have deliberately not been
connected to a new effect host in this change.

| Resource | Bytes | SHA-256 |
| --- | ---: | --- |
| `brilliantcut_tap.ogg` | 17,674 | `AD285DC0F44BC435D5D9D6906F07784BE06128AAC966173D0CC1BD1B32097687` |
| `brilliantcut_drag.ogg` | 31,262 | `4F3ADDA2E7610D0C9B10DD2EBF1731DB66C02963DE5602F1BD72CA1D57176A2C` |
| `brilliantcut_unlock.ogg` | 54,898 | `A1C36ECA2BE232983E0543A18F1460F53F061E14626994FB662F21384B3E5989` |

## Four stock geometries

`CompositeRenderingObject::CreateGeometry(GeometryType)` starts at ELF virtual
address `0x0aaf8`. Its computed jump selects four straight-line mesh builders.
Every `Vector3` constructed by a branch is consumed by exactly one `Plane`, and
every `Plane` is passed to `AddPlane` exactly once. This gives a strong static
integrity check on the extracted counts.

| ID | Meaning | ELF range | Planes | Vertices | 3-point | 6-point | 9-point |
| ---: | --- | --- | ---: | ---: | ---: | ---: | ---: |
| 0 | portrait special | `0x20c74..0x29190` | 149 | 543 | 124 | 18 | 7 |
| 1 | portrait normal | `0x1ae10..0x20c74` | 125 | 477 | 92 | 32 | 1 |
| 2 | landscape special | `0x0ab28..0x134d4` | 149 | 528 | 125 | 21 | 3 |
| 3 | landscape normal | `0x134e0..0x1ae10` | 125 | 477 | 92 | 32 | 1 |

There are no 12-point planes in any of the four branches. The constructor and
sink addresses used for the audit are:

- `Vector3(float,float,float)`: `0x079d4`
- `Plane(3 vertices)`: `0x08448`
- `Plane(6 vertices)`: `0x0895c`
- `Plane(9 vertices)`: `0x08b74`
- `Plane(12 vertices)`: `0x08588`
- `CompositeRenderingObject::AddPlane`: `0x0a79c`

The machine-readable call-count result is `stock-mesh-call-counts.json`.
`dump_stock_geometry.py` executes only the deterministic ARM instructions in
`CreateGeometry()` under Unicorn and intercepts the named Vector3, Plane and
AddPlane functions. Its exact ordered output is `stock_geometry.json`.

## Reproduction

Run from PowerShell:

```powershell
.\ports\brilliant-cut\reverse\Extract-BrilliantCutStock.ps1
```

The PowerShell script verifies the oracle and brush hashes, disassembles each exact branch
with the checked-in Android NDK `llvm-objdump`, counts constructor and
`AddPlane` calls, and rejects inconsistent vertex or plane totals. To recreate
the brush from the stock APK, add `-ExtractBrush`. To persist the JSON report,
pass `-OutputJson <path>`.

To reproduce the exact coordinates and compact payload:

```powershell
python .\ports\brilliant-cut\reverse\dump_stock_geometry.py `
  "F:\New project\unlock-effects-test\tabs\T705_ANF8_brilliantcut_native\libBrilliantCutEffect.so" `
  -o .\ports\brilliant-cut\reverse\stock_geometry.json
python .\ports\brilliant-cut\reverse\pack_stock_geometry.py `
  .\ports\brilliant-cut\reverse\stock_geometry.json `
  --binary-output .\ports\brilliant-cut\reverse\stock_geometry.bcm `
  --gzip-output .\ports\brilliant-cut\reverse\stock_geometry.bcm.gz
```

The checked-in payload and Java embedding can be verified without rewriting
them:

```powershell
python .\ports\brilliant-cut\reverse\pack_stock_geometry.py `
  .\ports\brilliant-cut\reverse\stock_geometry.json `
  --verify-binary .\ports\brilliant-cut\reverse\stock_geometry.bcm `
  --verify-gzip .\ports\brilliant-cut\reverse\stock_geometry.bcm.gz `
  --verify-java-source .\src\com\codex\lle\BrilliantCutStockGeometry.java
```

The deterministic BCM1 payload is 24,904 bytes with SHA-256
`37F49EC15DF52E768610C34712E5F31175A4EEF194CEFC20AA4E46533A1C4616`.
Its deterministic gzip form is 4,096 bytes with SHA-256
`BC0AEF3E952124F3BEA56FEE8CFCBFF136B01498BD4C36248C09BF2F8EB4B3B8`.
The gzip bytes are embedded in `BrilliantCutStockGeometry.java` as 5,464 Base64
characters, so the renderer can call `BrilliantCutStockGeometry.get(type)`
without a Context or AssetManager.

## Renderer implications

The plane boundaries must remain available to the ARM64 renderer; flattening
all vertices without arity or plane offsets would lose stock per-plane alpha
and normal behavior. BCM1 therefore retains, for every geometry, the ordered
list of plane arities followed by the original float32 XYZ coordinates.

Ghidra decompilation of `CompositeShaderProgram::Render()` confirms that stock
uses one `glDrawArrays(GL_TRIANGLES, 0, totalVertexCount)` call. Plane arities
3, 6 and 9 are therefore already one, two and three ordered triangles, not
polygon fans. The Java decoder exposes a sequential ushort index stream only
for renderers that prefer `glDrawElements`; drawing the XYZ array directly is
stock-equivalent.

## Static integration audit

`BrilliantCutGlesPipeline` consumes the decoded boundaries consistently:

- portrait special selects geometry 0 and landscape special selects geometry 2;
- `buildMeshStreams()` reads each unsigned `planeFirstVertices` and
  `planeVertexCounts` entry together;
- each plane range is used to compute its area-weighted stock center and to
  replicate one normal, auxiliary normal and alpha value across exactly that
  plane's vertices;
- alpha updates rewrite the same `[firstVertex, firstVertex + vertexCount)`
  interval;
- final rendering uses `glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount)`.

The BCM1 decoder rejects any payload whose plane counts do not sum to the
geometry vertex count. Therefore no vertex can be skipped or assigned to two
planes with the current checked-in data. `Mesh.indices` is intentionally not
used by this pipeline; it is a sequential compatibility stream for a future
`glDrawElements` implementation.

The stock composite shaders always write an opaque final alpha. A future
transparent-overlay port must preserve the sampled-wallpaper distortion while
limiting output coverage to modified pixels; setting one global alpha to zero
is not equivalent.
