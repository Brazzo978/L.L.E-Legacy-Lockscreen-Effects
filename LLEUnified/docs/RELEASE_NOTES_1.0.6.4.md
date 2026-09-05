# L.L.E 1.0.6.4 — Hula Hoop and Circle Mosaic

This release expands the restored LG family with two effects recovered from
original LG firmware and keeps the implementation app-owned: no donor APK is
embedded or executed.

## New LG effects

- **Hula Hoop:** includes the distinct V1 and V2 render paths, selectable from
  the effect card. Original geometry, layered hoop motion, touch response and
  LG sounds are integrated with L.L.E.'s lockscreen and Last Screen caches.
- **Circle Mosaic:** restores LG G4's 15-by-25 cell shader. Nine circular cells
  appear around the initial touch and progressively expand with the drag;
  blurred lockscreen cells reveal the captured Last Screen underneath. The
  original 50.2dp start radius, physical 25mm unlock threshold, 250ms terminal
  clocks and LG lock/touch/unlock sounds are preserved.

## Refinements

- Added matching glossy L.L.E. picker artwork for Hula Hoop and Circle Mosaic.
- Added the recovered Light Particle visual variants to the existing variant
  selector.
- Refined White Hole background distortion against the LG firmware behavior.
- Integrated the refreshed effect-family filters, runtime status indicators,
  horizontal-control gestures and explicit Random pool editor.
- Added deterministic scene tests for repeated gestures, cancellation,
  terminal cleanup and the nine-cell Circle Mosaic touchdown geometry.

## Package

- Version: `1.0.6.4` (`versionCode 46`).
- Application ID: `com.codex.lle64`.
- ABI: `arm64-v8a`.
- Minimum Android version: Android 6.0 / API 23.
