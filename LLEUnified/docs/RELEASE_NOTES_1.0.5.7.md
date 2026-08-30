# L.L.E 1.0.5.7

L.L.E 1.0.5.7 expands the ARM64 Samsung-free effect set, reduces colormap
startup work, and adds safer diagnostics and lockscreen handoff handling.

## Effects and rendering

- Added a procedural Lightning mode to the GLES Lens Flare renderer.

- Added the production Note 3 Ripple Ink renderer with eight selectable ink
  palettes.
- Added the app-owned Good Lock Popping Color, Rectangle Traveller, and
  Bouncing Color effects.
- Added optional per-effect high-frame-rate presentation or adaptive physics
  where the recovered renderer can preserve wall-clock behaviour.
- Added display-aware scaling and longer visible tails for the Seasonal
  effects.
- Replaced the default Lens Flare Canvas path with an app-owned GLES renderer;
  the original Canvas renderer remains selectable for A/B comparison.
- Added Original, Blue Ring, and Blood Lens Flare texture sets to both the GLES
  and Canvas renderers with shared timing and geometry.

## Colormap and display handling

- Automatic colormaps are persisted as validated raw ARGB8888 data, avoiding a
  PNG decode on renderer startup.
- Improved profile-aware portrait/landscape handling for tablets and Fold
  devices, including stricter dimension validation and clearer source/preview
  diagnostics.
- Added a tester-only no-colormap mode for low-memory or very slow devices. It
  exposes only compatible effects and safely falls back to Mass Tension if an
  incompatible selection is encountered.
- Moved automatic screenshot recapture controls and cache timing into the
  expandable Wallpaper source section.

## Reliability and diagnostics

- Hardened touch-window removal and unlock handoff on slower devices, with a
  lock-cycle fail-open path if the input window cannot be removed safely.
- Preserved full visual unlock tails before the synthetic SystemUI handoff for
  effects with measured terminal animations.
- Expanded structured display, colormap, renderer-readiness, and touch-window
  diagnostics.
- Added an explicitly warned advanced unredacted report for trusted private
  debugging. The standard shareable report remains privacy-filtered.

## Package

- Version: `1.0.5.7` (`versionCode 39`).
- Package: `com.codex.lle64`.
- ABI: `arm64-v8a` only.
- Normal production build remains Samsung-free and excludes legacy vendor
  effect binaries.
- Recommended APK: `LLE64-1.0.5.7-64-bit.apk`.
