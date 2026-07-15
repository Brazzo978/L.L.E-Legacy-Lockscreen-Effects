# Unified ARM32/ARM64 architecture

## One application source, two native products

`LLEUnified` produces two alternative APKs with the same package, version,
certificate, Java bytecode inputs, resources and preference keys. Android runs
one ABI per installed application process, so each product packages only its
matching native libraries.

| Effect | ARM32 process | ARM64 process |
|---|---|---|
| S4 Lens Flare | app-owned renderer | app-owned renderer |
| S5 Popping Colours | Samsung dex renderer | Samsung dex renderer |
| S3 Water Ripple | original patched ARM32 engine | reconstructed ARM64 GLES engine |
| N3 Watercolor | original patched ARM32 engine | reconstructed ARM64 GLES engine |
| N5 Colored Droplet | original patched ARM32 engine | Note 5 ARM64 engine |
| N5 Colored Droplet + Gyro | original patched ARM32 engine | Note 5 ARM64 engine |
| N5 Sparkling Bubbles | original patched ARM32 engine | Note 5 ARM64 engine |
| N4 Abstract Tiles | original patched ARM32 engine | unavailable/hidden |
| N4 Geometric Mosaic | original patched ARM32 engine | unavailable/hidden |

Unimplemented WIP slots are hidden on both targets.

## Runtime rules

`EffectAvailability` reads `Process.is64Bit()` once and is the source of truth
for the picker and preference validation. `ChargingAccessibilityService`
selects the ABI-specific renderer only after an available effect is requested.
If construction or native loading fails, the service tears down the partial
renderer and persists S4 Lens Flare as the safe fallback.

Do not use `Build.SUPPORTED_ABIS` to decide which library can be loaded: it
describes the device, while `Process.is64Bit()` describes the process that must
load the ELF.

## Build isolation

- `build/armeabi-v7a` contains the ARM32 staging tree and APK.
- `build/arm64-v8a` contains the ARM64 staging tree and APK.
- `build.ps1 -Target All` builds both sequentially from the same source.
- Each target verifies/signs independently; ARM64 additionally checks exact
  native entries, ELF machine, SONAMEs, dependencies and JNI exports.

The old application trees are preserved by Git tag
`lle-pre-unification-2026-07-15` and are not development trunks.
