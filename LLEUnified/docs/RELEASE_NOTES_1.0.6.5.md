# L.L.E 1.0.6.5 — S Pen Ripple Ink and Light Particle variants

This release adds stylus-aware interaction to N3 Ripple Ink, preserves the
distinct LG and XLocker Light Particle behaviors, and polishes their picker
controls on modern devices.

## N3 Ripple Ink

- Added an optional **S Pen mode** for devices with Samsung stylus support.
- Stylus pressure now scales the injected ink mass while preserving the normal
  finger-driven renderer when the option is disabled.
- Hardened pointer transitions, cancellation and repeated-gesture cleanup so
  stylus input cannot leave stale ink or an unresponsive overlay behind.
- Kept **HFR** in the standard left-hand position used by the other effect
  cards and placed the additional S Pen control on the right.

## LG Light Particle

- Split the recovered behaviors into selectable **V1** and **V2** variants.
- Preserved their distinct geometry, anchoring, timing, rotation and particle
  distribution instead of treating the variants as a simple density change.
- Added deterministic scene coverage for the variant-specific paths.

## Interface and credits

- HFR remains enabled by default for supported effects through the current
  preference schema.
- Credits now include Erik Kiorai's S Pen contribution and historical
  SamsungEffectTester work, plus Curtis's Material You interface contribution.

## Package

- Version: `1.0.6.5` (`versionCode 47`).
- Application ID: `com.codex.lle64`.
- ABI: `arm64-v8a`.
- Minimum Android version: Android 6.0 / API 23.
