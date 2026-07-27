# L.L.E 1.0.4.3

L.L.E 1.0.4.3 is a stable first-run, rendering-reliability, and documentation
update for ARM64.

## First-run setup

- Added a Samsung Restricted Settings recovery flow for sideloaded installs.
- The wizard now detects when Accessibility remains blocked after the first
  activation attempt instead of reporting a false success.
- Added a clear three-step path through **App info → ⋮ → Allow restricted
  settings → return to L.L.E**.
- Added an App info reminder toast and an explicit approval recheck.
- The first Accessibility page now shows the full Samsung navigation path
  through **Installed apps → L.L.E 64**.
- Existing installations with an older wizard schema restart at the
  Accessibility check so the new safeguards are not skipped.

## Rendering and capture reliability

- Unified the lockscreen-background capture delay across effects to avoid
  effect-dependent screenshot timing.
- Capture now waits for the lockscreen clock and final brightness state to
  settle before saving the shared background.
- Clamped Lens Flare color channels to their alpha contribution, preventing
  hidden RGB values from darkening or corrupting the composed background.
- Preserved warm renderer behavior while reducing first-interaction UI stalls.

## Interface and support

- Added the installed version beside the L.L.E header.
- Simplified the settings below the effect picker and replaced technical effect
  summaries with short visual descriptions.
- Removed the obsolete root-debug controls and implementation.
- Added a co-installable tester build path for clean first-run validation.
- The debug-report share subject now uses the installed application version.

## Documentation and privacy

- Added illustrated manual-install and ADB guides.
- Documented the tested two-pass Samsung Restricted Settings flow.
- Reworked the project README with direct setup links, an initial compatibility
  table, privacy details, and the active ARM64 support policy.
- L.L.E still requests no Android `INTERNET` permission. Wallpapers, lockscreen
  captures, caches, settings, and generated debug reports remain local unless
  the user explicitly exports or shares them.

## Builds

- `LLE64-1.0.4.3-64-bit.apk` — recommended ARM64 build.
- `SHA256SUMS.txt` — SHA-256 checksum for the release APK.
- ARM32 is not rebuilt for this release. The historical
  `LLE-1.0.4.1-32-bit.apk` remains available for continuity and critical fixes.

## Known limitations

- Direct wallpaper modes remain Beta and require precise crop alignment.
- Layered and protected lockscreen wallpapers may require manual selection.
- Fold devices may require separate Cover/Main images when Samsung does not
  expose the panel-specific wallpaper layer.
- Reconstructed effects can vary slightly across GPUs, refresh rates, and
  Android builds.
