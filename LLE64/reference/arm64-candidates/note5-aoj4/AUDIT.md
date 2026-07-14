# Note 5 AOJ4 AArch64 candidate audit

Static audit performed with the NDK r27d `llvm-readelf`.

| Library | Size | SHA-256 | ELF |
|---|---:|---|---|
| `libColourDropletEffect.so` | 517896 | `634DC703FF9288A4961B3E636B83DD89DDBF86DF6087D624DC19B4231E6C010C` | ELF64 AArch64 DYN |
| `libSparklingBubblesEffect.so` | 435976 | `F96E287CD20B411A863D07D012631FA61761FC35AEC50D4B4A4B454577B2C944` | ELF64 AArch64 DYN |

Both export `JNI_OnLoad` and the same `PhysicsEngineJNI` family (`Init_JNI`, `Init_PhysicsEngine`, `Draw_PhysicsEngine`, `onTouchEvent`, `onSensorEvent`, texture and custom-event methods). This is dynamic JNI registration, not static `Java_*` exports.

Common `DT_NEEDED`:

- Android public/runtime libraries: `libEGL.so`, `libGLESv2.so`, `libandroid.so`, `libc.so`, `libjnigraphics.so`, `liblog.so`, `libm.so`, `libz.so`.
- C++ dependencies: `libstdc++.so` and `libstlport.so`.

## Current blocker

No AArch64 `libstlport.so` was found in the repository/dumps; every discovered `libstlport.so` is ARM32 (`EM_ARM`). Therefore these two candidates are **not yet packageable** on the Fold7. Next step is to enumerate their unresolved STLport symbols and determine whether a narrow AArch64 compatibility shim is sufficient or whether the engine must be relinked/reimplemented.

Confidence: ELF architecture and dependencies `CONFIRMED`; runtime compatibility `UNRESOLVED`.
