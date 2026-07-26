# L.L.E 1.0.4.1

L.L.E 1.0.4.1 is a stable setup-flow update for both ARM32 and ARM64.
It extends first launch through the lockscreen-source check and touch-area
calibration instead of marking setup complete immediately after feature
selection.

## Downloads

- `LLE64-1.0.4.1-64-bit.apk` — recommended ARM64 build.
- `LLE-1.0.4.1-32-bit.apk` — historical ARM32 compatibility build.
- `SHA256SUMS.txt` — release artifact checksums.

ARM64 remains the active development target. ARM32 is retained for historical
continuity and receives compatibility or critical bug fixes only.

## Setup wizard

- Added a final source-preparation page after feature selection.
- Automatic screenshot mode now explicitly guides the user through one
  lock, wait and unlock cycle.
- A screenshot from before the wizard request cannot satisfy the new capture
  check; the wizard waits for a newly completed capture.
- Set-and-cache and exact-wallpaper modes proceed with their prepared fixed
  source without asking for an unnecessary lockscreen capture.
- Added a final touch-box page with the choice to open the existing editor or
  keep the current/default area.
- Setup is marked complete only after touch areas are saved or the user
  explicitly keeps the current/default touch box.
- Cancelling the editor returns to the touch-box decision instead of silently
  completing setup.
- The editor can reuse an imported wallpaper as its calibration image, avoiding
  a duplicate capture for the two direct-wallpaper modes.
- Fold devices keep their existing Cover/Main touch-box workflow.

## Signing and compatibility

The APKs use the registered stable certificate and the existing Android APK
Signature Scheme v3.1 lineage introduced with version 1.0.4. Compatible Beta
and 1.0.4 installations can therefore update in place while preserving local
settings and caches.

The application still has no Internet permission. Screenshots, imported
wallpapers and effect caches remain in app-private local storage.

## Known limitations

- Direct wallpaper modes remain Beta and require precise crop alignment.
- Samsung does not expose safe panel-specific wallpaper-setting APIs to
  third-party apps on Fold devices.
- Protected or layered lockscreen surfaces may require manual wallpaper
  selection.
- Reconstructed effects can vary slightly by GPU, refresh rate and Android
  build.
