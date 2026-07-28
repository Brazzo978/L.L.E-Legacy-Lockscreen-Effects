# L.L.E 1.0.4.4

L.L.E 1.0.4.4 is a focused ARM64 usability update for screenshot-backed
lockscreen effects.

## Colormap readiness

- Added a compact warning at the very top of the Effects tab when the selected
  renderer requires a colormap screenshot and no readable cache is available.
- The warning validates the real image file instead of relying only on a saved
  preference or timestamp.
- Fold configurations identify the missing Cover and/or Main cache separately.
- Added **Force recapture now** with a clear lock → wait → unlock instruction.
- Added a direct **Change source** action for users who prefer a fixed
  wallpaper.
- The warning is hidden for effects that do not use a screenshot background and
  when a valid direct wallpaper is active.
- Colormap readiness is checked again when the app resumes, so the warning
  disappears as soon as a valid capture has been saved.

## Samsung wallpaper compatibility

- The wizard now checks Samsung Dynamic Lock Screen alongside Dark mode
  wallpaper dimming.
- When Dynamic Lock Screen is active, L.L.E identifies the real Samsung state,
  explains why a rotating protected wallpaper cannot be captured reliably
  before the same unlock effect starts, and opens its settings page directly.
- Returning to L.L.E refreshes both compatibility cards without skipping the
  remaining wizard.
- Users can still continue after a strong compatibility warning, but disabling
  every flagged feature is recommended for reliable effect backgrounds.

## Watercolor

- Fixed Watercolor losing its direct-wallpaper texture after the Samsung
  TextureView/EGL surface was recreated across lock and unlock cycles.
- The background is now uploaded again only when the renderer receives a new
  surface, without reloading it on every touch.

## Lens Flare and unlock hints

- Rebuilt Lens Flare's additive composite calculations with full `float`
  precision while preserving the 1.0.4.3 RGB-to-alpha safety clamp.
- Limited reconstructed alpha to the source flare alpha, retaining the stock
  flare brightness without dark or oversized compositing artifacts.
- Fixed the shared screen-off restore order so renderer reset happens before
  the next unlock hint is scheduled. Lens Flare hints now play reliably again;
  the lifecycle correction applies to every unlock-effect renderer.

## Audio and older Android diagnostics

- Colored Droplet now queues tap and unlock sounds until `SoundPool` confirms
  that each sample is loaded, preventing intermittent missing audio on slower
  devices.
- Audio suppression and playback failures now identify the exact reason in the
  app log.
- Debug reports now include ringer mode, System and Media volumes, the system
  lockscreen-sound setting, and the effective L.L.E sound switches.
- Added an audio FAQ explaining that Samsung's **Settings → Sounds and
  vibration → System sound → Screen lock/unlock** switch must be enabled
  because L.L.E. follows the system lockscreen sound policy.
- Added a PID-filtered logcat fallback for Android versions and vendor builds
  that do not support `logcat --uid`, including the tested LG Android 11 build.
- Added LG Velvet (`LM-G910`, Android 11) to the working compatibility matrix.

## Runtime surface exclusions

- The Samsung **Phone options** power menu is now detected as a dedicated
  SystemUI surface. L.L.E. temporarily hides the unlock effect, doodle, and
  touch listener without blacklisting SystemUI or interfering with the
  lockscreen and AOD.
- Gemini's lockscreen floaty activity is now covered through its Google app
  package.
- Added **Advanced settings → Custom app blacklist**. Users can add and remove
  vendor-specific package names without changing L.L.E.'s protected built-in
  safety rules; changes take effect immediately and appear in debug reports.

## Builds

- `LLE64-1.0.4.4-64-bit.apk` — recommended ARM64 build.
- `SHA256SUMS.txt` — SHA-256 checksum for the release APK.
- ARM32 is not rebuilt. The historical 1.0.4.1 build remains available for
  continuity and critical fixes only.
