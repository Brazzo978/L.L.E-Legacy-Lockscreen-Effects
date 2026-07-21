# L.L.E / L.L.E 64 1.0.4 Beta 1969

## Highlights

- Added S5 Stone Skipping on ARM32 and ARM64, reconstructed from Samsung's
  legacy Mass Ripple lineage and calibrated against stock-device captures.
- Added S5 Brilliant Ring on both ABIs, including stock-style ring geometry,
  refraction, fading and interaction audio.
- Added Tab S Brilliant Cut on both ABIs. ARM32 uses the patched original
  Samsung engine; ARM64 uses an app-owned GLES reconstruction of the stock
  mesh, LightBrush mask, glint and distortion pipeline.
- Added the Note II Ripple Ink lineage as Ink in Water / Indigo, with a native
  ARM32 path and a matching ARM64 renderer.

## Fidelity and runtime

- Refined S3 Water Ripple event pacing, propagation, sound and native lifecycle
  behaviour using stock hardware as the oracle.
- Brilliant Cut now keeps untouched lockscreen pixels transparent and applies
  the stock touch radius and brightness at the correct rendering stages.
- Improved persistent renderer readiness and effect warm-up across repeated
  lock/unlock cycles.
- Preserved screenshot-service and Beta direct-wallpaper background modes for
  all newly added effects.

## Build and validation

- Version: `1.0.4-beta.1969` (`versionCode 18`).
- ARM64 was installed and visually validated on a Samsung Galaxy S23 Ultra
  (`SM-S918B`).
- The shared source was built successfully for both `armeabi-v7a` and
  `arm64-v8a`.
- Both APKs verify with Android signature schemes v1, v2 and v3.

Release artifacts (SHA-256):

- `LLE-1.0.4-Beta-1969-32-bit.apk` — `08354C4AB90438D968EC175D6A6DFB00AC5397425BB74EDACED4F2BBB7B5A0E1`
- `LLE64-1.0.4-Beta-1969-64-bit.apk` — `DC12B10F7B67002A52EAF5E69F619157002D7028110CAD32DE7A8FA7AB7EA473`

```shell
adb install --no-incremental -r "LLE-1.0.4-Beta-1969-32-bit.apk"
adb install --no-incremental -r "LLE64-1.0.4-Beta-1969-64-bit.apk"
```

Only one L.L.E Accessibility service should be active at a time.

## Beta notes

- Direct wallpaper import still needs broader device and aspect-ratio testing.
- Brilliant Cut is visually close to the stock oracle; small GPU- and
  refresh-rate-dependent differences can remain.
- Legacy compatibility components remain the property of their respective
  owners. The project does not claim ownership, affiliation or endorsement.
