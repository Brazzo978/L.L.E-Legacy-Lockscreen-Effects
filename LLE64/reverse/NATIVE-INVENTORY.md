# Native inventory

All legacy binaries below are reference-only and are excluded from the LLE64 APK.

| Library | Bytes | ELF | JNI style | Key non-platform dependency |
|---|---:|---|---|---|
| `libWaterRipple.so` | 70896 | ARM32 | static `Java_*` exports | none |
| `libsecveWaterColor.so` | 79060 | ARM32 | engine through common library | `libsecveSrkCommon.so`, `libstlport.so` |
| `libsecveSrkCommon.so` | 341296 | ARM32 | static `Native_*` exports | `libstlport.so` |
| `libColourDropletEffect.so` | 419132 | ARM32 | dynamic `JNI_OnLoad` | `libstlport.so` |
| `libSparklingBubblesEffect.so` | 333116 | ARM32 | dynamic `JNI_OnLoad` | `libstlport.so` |
| `libsecveAbstractTile.so` | 115932 | ARM32 | common-engine path | `libsecveSrkCommon.so`, `libstlport.so` |
| `libsecveGeometricMosaic.so` | 115932 | ARM32 | common-engine path | `libsecveSrkCommon.so`, `libstlport.so` |
| `libstlport.so` | 214352 | ARM32 | n/a | legacy C++ runtime |

`libWaterRipple.so` is the cleanest first exact port: its `DT_NEEDED` set is limited to Android/GLES system libraries plus the old `libstdc++.so`; unlike the secve and Note 5 engines it does not require STLport.

Confirmed AArch64 Note 5 candidates are tracked separately under `reference/arm64-candidates`; their missing AArch64 STLport dependency is being audited before any packaging decision.
