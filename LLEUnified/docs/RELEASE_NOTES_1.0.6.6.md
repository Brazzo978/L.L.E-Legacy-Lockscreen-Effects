# L.L.E 1.0.6.6 — Advanced controls and compatibility

This release reorganizes L.L.E's advanced controls, adds safer compatibility
options for unusual lockscreen apps and improves effect behavior across slower
devices, modern displays and stricter mobile GPU drivers.

## Interface

- Added a dedicated **ADV** tab with focused expandable sections for Setup,
  app lists, Battery, Display, Compatibility, Tester tools and Dangerous
  controls.
- Moved wallpaper-source controls back beside the effects that use them and
  separated the ordinary lockscreen cache from the LG **Last screen** source.
- Removed the retired Doodle debug panel while keeping useful AOD controls.
- Added a persistent hidden customization page unlocked after 17 swipes. The
  page is explicitly marked as unfinished and under active development.

## Lockscreen compatibility

- Added optional automatic protection against focused third-party activities
  appearing above the lockscreen.
- Added a dedicated allowlist picker so compatible lockscreen apps can remain
  visible without disabling L.L.E globally.
- Kept L.L.E, SystemUI, AOD, launchers, keyboards and other core surfaces
  protected from accidental filtering.

## LG Last screen

- Added **Force custom wallpaper for Last screen effects** as an independent
  per-display source for devices where Android's screen-off capture is too late
  or unavailable.
- Kept the custom Last screen source separate from the normal lockscreen cache:
  effects that need both images, such as Revolving Glass, retain both sources.
- Documented the automatic-capture timing limitation on slower devices and the
  manual fallback path.

## Effect fixes

- Fixed Revolving Glass shader linkage on stricter Mali drivers by using
  matching vertex/fragment precision and preventing failed surfaces from being
  reported as ready.
- Increased the drag-ripple interval from the stock-equivalent `150/720` to a
  modernized `180/720` scale for Water Ripple and Ink in Water.
- Applied the same distance scaling to Ripple Ink, including S Pen and
  water-only paths, and aligned its visual and audio ripple thresholds.

## Package

- Version: `1.0.6.6` (`versionCode 48`).
- Application ID: `com.codex.lle64`.
- ABI: `arm64-v8a`.
- Minimum Android version: Android 6.0 / API 23.
