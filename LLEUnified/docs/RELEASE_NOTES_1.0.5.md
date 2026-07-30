# L.L.E 1.0.5

L.L.E 1.0.5 completes the ARM64 transition to app-owned renderers for the
Note 5 droplet family and Galaxy S6 Water Droplet. The normal release APK is
Samsung-free and contains no proprietary Note 5 or S6 effect runtime ELF.

## App-owned effect ports

- Added the final app-owned N5 Colored Droplet renderer.
- Added its separate accelerometer-driven **Colored Droplet + Gyro** variant.
- Added the final app-owned N5 Sparkling Bubbles renderer.
- Added the distinct app-owned S6 Water Droplet renderer with stock-style
  refraction, touch physics, affordance, unlock expansion, audio and gyro.
- Existing selections of the removed Samsung runtime effects migrate to their
  matching L.L.E renderer without changing the visible effect.
- Fixed the S6 migration path so an old S6 selection cannot fall back to
  Colored Droplet.

## Sparkling Bubbles parity gate

- Reproduced the stock 25-group, 1,100-particle simulation layout and its
  press, affordance and unlock ranges.
- Preserved wallpaper-sampled particle color, persistent post-touch motion,
  stock fade behavior and the 6x unlock velocity step.
- Verified hint, drag, release tail, unlock peak and complete cleanup against
  the Note 5 oracle.
- Tap, drag, lock and unlock samples match the extracted stock files
  byte-for-byte.

## S6 Water Droplet parity gate

- Matched the stock SPH particle injection, wall dynamics, attraction,
  particle-size projection and unlock growth constants.
- Matched Samsung's accelerometer coefficients and corrected display-rotation
  mapping.
- Restored full wallpaper refraction inside the water mass instead of a soft
  transparent color overlay.
- Verified hint, hold, fast drag, release persistence, unlock expansion,
  cleanup and gyro behavior against the stock renderer.
- Normal-map, edge-density and audio resources match the stock S6 assets
  byte-for-byte.

## Mass Tension

- Added the hidden Mass Tension effect as an app-owned renderer.
- Added resolution-aware scaling for phones and tablets.
- Preserved the stock interaction delay and sound timing.

## Packaging

- The production ARM64 APK contains only the L.L.E-owned replacements for
  Colored Droplet, Colored Droplet + Gyro, Sparkling Bubbles and S6 Water
  Droplet.
- The build rejects accidental inclusion of `libColourDropletEffect.so`,
  `libSparklingBubblesEffect.so`, `libWaterDropletEffect.so` or
  `libstlport.so`.
- A separately signed legacy-vendor diagnostic APK preserves the frozen stock
  engines beside the L.L.E renderers for oracle comparison. It is optional,
  contains proprietary compatibility libraries and is not the recommended
  build.
- **L.L.E 1.0.5 Legacy is the final release containing Samsung code or
  binaries. All subsequent releases will remove the vendor engines entirely.**
- Promoted the app-owned effect labels from WIP to their normal user-facing
  names.

## Builds

- `LLE64-1.0.5-64-bit.apk` - recommended ARM64 Samsung-free build.
- `LLE64-1.0.5-64-bit-legacy-vendor.apk` - final optional ARM64 diagnostic build.
- `SHA256SUMS.txt` - SHA-256 checksums for both release APKs.
- ARM32 is not rebuilt. The historical `LLE-1.0.4.1-32-bit.apk` remains
  available only for continuity and critical fixes.
