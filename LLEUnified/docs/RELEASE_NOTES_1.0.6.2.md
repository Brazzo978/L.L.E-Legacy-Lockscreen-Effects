# L.L.E 1.0.6.2

L.L.E 1.0.6.2 hardens Last screen capture and fallback behavior across normal
phones and dual-screen Fold layouts while preserving the 1.0.6.1 Random mode.

## Last screen reliability

- Added an automatic exact-profile lockscreen-cache fallback for LG effects when
  a fresh Last screen capture is temporarily unavailable.
- Kept Last screen and lockscreen caches independent: a successful pre-lock
  capture remains the preferred source and replaces a temporary fallback.
- Rejects stale asynchronous captures after a display, profile, orientation or
  buffer-geometry change instead of applying them to the wrong screen.
- Validates cache display profile and exact dimensions before use, preventing a
  Main-screen frame from leaking into a Cover-screen effect or vice versa.

## Fold and setup wizard

- Added independent Main and Cover capture guidance and readiness status for
  Fold devices.
- Fixed a Fold handoff state that could leave capture permanently blocked after
  switching panels even though no L.L.E overlay remained attached.
- Fixed setup previews that could show a stale solid-black tester placeholder
  instead of a valid Last screen or effect-background cache.
- Fixed red/blue channel inversion in sampled raw ARGB previews by decoding rows
  through Android's bitmap pixel contract instead of assuming host byte order.
- Touch-box and cache previews now resolve the active display profile and geometry
  consistently with the runtime renderer.

## Compatibility and scope

- Confirmed the updated capture and fallback path on a Galaxy S23 Ultra and on a
  Galaxy Fold using separate Main and Cover cache profiles.
- Random mode continues to select and preload effects normally on Fold.
- Effect scale on very large inner Fold displays remains device-dependent; this
  maintenance release does not rescale or otherwise change effect artwork.
- Version: `1.0.6.2` (`versionCode 44`).
- Package: `com.codex.lle64`.
- ABI: `arm64-v8a` only.
- Recommended APK: `LLE64-1.0.6.2-64-bit.apk`.
