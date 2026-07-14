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

## Blocker resolution

The matching AOJ4 firmware was later supplied and contains an authentic
AArch64 `libstlport.so` (SHA-256
`821B11D1EA2E1853D0DE0F547F9FE224100AAA53A500F69441765BB089615CCA`).
Both renderers now load and run with that library on the Fold7; no compatibility
shim is used. See `reverse/note5-arm64-candidates/FIRMWARE-AUDIT.md` and
`RUNTIME-PROBE.md` for the extraction and device results.

Confidence: ELF architecture, dependencies and Fold7 runtime compatibility
`CONFIRMED`. Public redistribution rights remain outside this technical audit.
