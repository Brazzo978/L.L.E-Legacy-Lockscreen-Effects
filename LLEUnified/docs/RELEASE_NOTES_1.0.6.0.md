# L.L.E 1.0.6.0

L.L.E 1.0.6.0 is the stable ARM64 restoration release. It promotes the new LG,
Sony and Samsung effects, adds animated picker previews and strengthens the
capture, renderer and unlock handoff paths used on modern Android.

## Restored effects

- Added **S3 None** as the first Samsung effect, including the recovered lock
  affordance animation and no-colormap support.
- Added **G1 White Hole**, **G1 Dewdrop**, **G2 Soda**, **G2 Particle**,
  **G2 Light Particle**, **G2 Pixelate** and **G2 Crystal** under LG effects.
- Added **Xperia Z1 Blinds** and **Revolving Glass** under Sony effects.
- Revolving Glass is an inspired modern restoration: it preserves the rotating
  glass interaction while using separate lockscreen and Last screen sources,
  a luminous rounded tile, bounded spin velocity and a deterministic fade/scale
  handoff suitable for current lockscreen overlays.
- Xperia Z2 Particle, LG G1 Ripple and Xperia X10 are not planned because a
  partial restoration would omit their coupled background behaviour or add a
  renderer with too little practical value.

## Fidelity and presentation

- Added matching effect icons and animated long-press previews for the restored
  effects, with picker text describing the visual result rather than provenance.
- Reorganized the picker as Samsung, Good Lock, LG effects and Sony, followed by
  Seasonal effects.
- N3 Ripple Ink now shares the Ink in Water icon, matching their visual family.
- Removed provisional labels from the promoted effect list.
- Tuned LG/Sony source persistence independently so the Last screen remains
  visible through the renderer tail and SystemUI unlock handoff.

## Capture and reliability

- Added the private per-profile **Last screen** cache used by LG/Sony effects
  without replacing the existing lockscreen colormap cache.
- Exposed both caches in the setup wizard and diagnostics, including fallback
  controls when the preferred source is unavailable.
- Hardened renderer teardown/recreation, touch cancellation and repeated gesture
  handling across Lens Flare, Abstract Tiles and the restored effects.
- Restored the native Canvas Lens Flare path as the production default for all
  four variants; GLES remains an explicit diagnostic comparison path.
- Fixed the Abstract Tiles hint path so a failed or dismissed hint cannot leave
  a retained tile or block subsequent touches.

## Scope

- The configurable Random effect pool is deferred to **1.0.6.1**.
- Version: `1.0.6.0` (`versionCode 42`).
- Package: `com.codex.lle64`.
- ABI: `arm64-v8a` only.
- The production APK is Samsung-free and does not ship legacy vendor native
  binaries or archived applications.
- Recommended APK: `LLE64-1.0.6.0-64-bit.apk`.
