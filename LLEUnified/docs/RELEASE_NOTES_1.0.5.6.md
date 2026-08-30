# L.L.E 1.0.5.6

L.L.E 1.0.5.6 adds the production ARM64 Note 3 Ripple Ink port and the
app-owned Good Lock-inspired particle set. The recommended APK remains
Samsung-free.

## N3 Ripple Ink

- Added N3 Ripple Ink to the ARM64 production picker immediately after
  S4 Lens Flare.
- Recovered its touch state machine, pressure profiles, velocity worker,
  density advection, AddInk ordering and release behavior from the Note 3 ENB4
  oracle.
- Added eight stock one-based Ink palette choices with rendered-colour
  previews and live selection.
- Ordinary finger input is always accepted; an S Pen is not required.
- Added hybrid HFR operation: the water mesh follows the display refresh rate
  while all Ink simulation passes remain on their integral 60 Hz cadence.
- Added an app-owned ARM64 JNI worker. The extracted Samsung oracle library is
  used only for reverse validation and is not included in the APK.

## Good Lock-inspired effects

- Added app-owned Popping Color, Rectangle Traveller and Bouncing Color
  variants.
- Particles sample the selected lockscreen image without painting it as an
  opaque fullscreen layer.
- Added per-effect HFR presentation and HFR-only physics-speed controls.

## Reliability and packaging

- Ripple Ink fails closed if its native/GLES resource chain is unavailable and
  reports readiness before accepting a gesture.
- Kept the production ARM64 package free of legacy Samsung effect binaries.
- Added Java oracle/concurrency/JNI wiring tests and a native C worker suite.
- Version: `1.0.5.6` (`versionCode 32`).

## Release files

- `LLE64-1.0.5.6-64-bit.apk` — recommended ARM64 Samsung-free build.
- `SHA256SUMS.txt` — SHA-256 checksum for the release APK.
- ARM32 is not rebuilt for this release; the historical ARM32 artifact remains
  available for continuity.
