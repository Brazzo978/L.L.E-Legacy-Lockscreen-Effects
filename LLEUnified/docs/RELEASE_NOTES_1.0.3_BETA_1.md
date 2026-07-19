# L.L.E / L.L.E 64 1.0.3 Beta 1

## Highlights

- Tab S Blind is now available without a WIP label on both ARM32 and ARM64.
  The renderer keeps the untouched slats transparent and reveals only the
  portions modified by the original Samsung interaction.
- Added an animated first-launch wizard for Accessibility, Samsung battery
  optimization, background source and feature-mode setup. The wizard can also
  be opened again from the application.
- Added Beta direct-wallpaper workflows: set and cache a lockscreen wallpaper,
  or import the exact existing lockscreen image with zoom and crop alignment.
  Automatic screenshot capture remains available as the default mode.
- Reorganized the main screen around the application enable control, effect
  list and expandable screenshot-service settings.

## Runtime and fidelity

- Added persistent readiness and warm-up handling for every available unlock
  effect so the selected renderer stays prepared across normal lock/unlock
  cycles.
- Refined S3 Water Ripple timing, touch-event pacing, propagation and stock
  sound behaviour from device-side reference captures.
- Improved lifecycle handling for the ARM64 Abstract Tiles and Geometric
  Mosaic reconstructions and the Samsung DEX-backed effects.
- Added an all-effects ARM64 profiling workflow and recorded the S23 CPU, GPU,
  RAM and latency baseline.

## Build and validation

- Version: `1.0.3-beta.1` (`versionCode 17`).
- ARM64 was installed and smoke-tested on a Samsung Galaxy S23 Ultra
  (`SM-S918B`): package ABI, application launch and the Accessibility service
  all passed.
- Both APKs verify with Android signature schemes v1, v2 and v3.

Release artifacts (SHA-256):

- `LLE-1.0.3-Beta-1-32-bit.apk` — `FD5B8302DEC2D0F142DAAB764863A9A17E2E6DE0A74DF0660585D24C9B35EC56`
- `LLE64-1.0.3-Beta-1-64-bit.apk` — `9C0BE3BC6A3C02DF3EC09D0818F13E879D1319C068F627A94F1540AC8E707A5A`

```shell
adb install --no-incremental -r "LLE-1.0.3-Beta-1-32-bit.apk"
adb install --no-incremental -r "LLE64-1.0.3-Beta-1-64-bit.apk"
```

Only one L.L.E Accessibility service should be active at a time.

## Beta notes

- Direct wallpaper import still needs broader device and aspect-ratio testing.
- Some effects can show a small first-interaction frame hitch; subsequent
  interaction is smooth and per-effect residence timing will be tuned later.
- Legacy compatibility components remain the property of their respective
  owners. The project does not claim ownership, affiliation or endorsement.
