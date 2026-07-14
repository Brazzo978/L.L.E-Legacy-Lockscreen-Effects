# S3 Water Ripple reverse benchmark

Target: ARM32 `libWaterRipple.so`, Ghidra image base `0x10000`, 217 functions. The original Ghidra project was used read-only.

## Tool verdict

- Ghidra decompile: best for loop structure, constant discovery and array flow; types and JNI signatures remain unreliable without manual recovery.
- High P-code: useful only for disputed signedness/casts or single basic blocks; a full dump of JNI `ripple` is disproportionately large.
- ARM32 assembly: final authority for loop order, aliasing, float operation order and branch comparisons.
- Selected workflow: decompile first, targeted P-code second, assembly cross-check before marking `CONFIRMED`.

Bridge stability was adequate for decompile/search/P-code, but `disassemble_function` returned an empty result for JNI `ripple` and its call-graph request returned no relationship despite the known export. Raw assembly cross-checks therefore use NDK `llvm-objdump` against the untouched reference ELF.

## Benchmark functions

| Function | Ghidra address | Result | Confidence |
|---|---:|---|---|
| JNI `ripple` | `0x1bfe4` | mesh-to-detail mapping, radius-3 cone, X-major/Y-minor indexing | `CONFIRMED` |
| JNI `move` | `0x1bc04` | three passes, clamp, empty threshold and in-place final Laplacian | `CONFIRMED` |
| `Fluid::Update` | `0x164f8` | broad call/order map recovered; struct typing still incomplete | `PROBABLE` |
| `AdvectDensity` | `0x14118` | pass identified; exact field layout still requires assembly annotation | `PROBABLE` |
| `Jacobi` | `0x14400` | pressure iteration identified; offsets require typed struct | `PROBABLE` |
| `SubtractGradient` | `0x14880` | central differences and clamped sampling recovered | `PROBABLE` |
| `ComputeDivergence` | `0x14be4` | `((top.y + right.x - left.x) - bottom.y) * 0.5/cellSize` | `CONFIRMED` |
| `AddInk` | `0x14f60` | GLES pass/uniform ordering recovered; shader strings remain primary formula source | `PROBABLE` |
| `AddVelocity` | `0x15398` | normalized drag vector and per-cell callback flow recovered | `PROBABLE` |

## JNI `move` exact pass order

For every pass the outer loop is X and the inner loop is Y, indexing `y * detailWidth + x`.

1. `velocity = (velocity + laplacian(height) * waveCoefficient) * damping`.
2. `height = clamp(height + velocity, -100, 100)`.
3. `height += laplacian(height) * (damping == 0.94 ? 0.068 : 0.018)`.

The height JNI array is acquired twice and aliases the same storage. Pass 3 is therefore deliberately in-place and order-dependent. Replacing it with a temporary output buffer changes Samsung's simulation.

ARM32 assembly cross-check at ELF `0xbc04` confirms the operation order (`vmls`/successive `vadd`/`vmla`), X-major pointer increments, strict empty comparisons against `+/-0.01`, and the exact literals `0.94f` (`0x3f70a3d7`), `0.018f` (`0x3c9374bc`) and `0.068f` (`0x3d8b4396`). The AArch64 core is compiled with fast-math and FP contraction disabled until frame-level parity proves a different choice.

Historical `S3RippleMeshEffectView` cross-check found one fidelity bug that is not carried into LLE64: it clamped again after pass 3. The ARM32 function clamps only pass 2; the final in-place relaxation write is not clamped.

The first AArch64 port unit is in `ports/water-ripple/native`; it is not packaged or exposed in the effect picker until the remaining renderer/JNI/GL mapping is complete.

The AArch64 core compiled with NDK r27d and passed its deterministic test on the Fold7:

```text
PASS hash=59890e7812c02590 centerVelocity=2.50805116 centerHeight=7.21869993
```

An isolated ARM64 APK subsequently loaded `libWaterRipple.so` through the exact
`com.android.internal.policy.impl.keyguard.sec.JniWaterRippleRender` Java ABI and
called the three implemented native methods through ART. Mesh vertices/indices,
the radius-3 impulse and the active-field `move` result all passed:

```text
I LLE64NativeProbe: PASS Water Ripple ARM64 initWaters/ripple/move through ART
```

This does not cover the GPU JNI surface. The probe library intentionally omits
those entry points and is not packaged by the normal LLE64 build.
