# L.L.E 1.0.4

L.L.E 1.0.4 is the first stable release of the unified ARM32/ARM64 project.
It completes the current legacy effect collection, adds the new setup and
wallpaper workflows, and hardens the service for daily lock/unlock use.

## Downloads

- `LLE64-1.0.4-64-bit.apk` — recommended ARM64 build.
- `LLE-1.0.4-32-bit.apk` — historical ARM32 compatibility build.
- `SHA256SUMS.txt` — release artifact checksums.

ARM64 is now the active development target. ARM32 is retained for historical
continuity and receives compatibility or critical bug fixes only; it will not
receive new effects or feature work.

## Highlights

- Complete shared picker for the Samsung legacy effect ports, including Blind,
  Stone Skipping, Brilliant Ring, Brilliant Cut and the four Seasonal effects.
- Animated in-app previews and dedicated effect/doodle artwork.
- Charging doodle and unlock effects can coexist, with Seasonal available as an
  automatic or forced season.
- First-launch wizard for Accessibility, battery exemption, Samsung wallpaper
  dim compatibility, wallpaper source and initial feature selection.
- Automatic screenshot source plus Beta set-and-cache and exact-wallpaper
  import modes.
- Separate Cover/Main caches, touch regions and runtime switches on Fold
  devices.
- Warm renderer preparation, buffered first gesture, per-effect sound routing
  and reliability fixes across repeated lock/unlock cycles.

## Stability and security fixes

- Prevented stale hint cleanup from cancelling a real user gesture.
- Prevented repeated imported-wallpaper reloads from rebuilding the active
  renderer.
- Disabled externally launchable internal fullscreen/wake components.
- Hard recapture is now disarmed when L.L.E or the active effect route is off.
- Screenshot workers cannot rewrite cache state after the Accessibility service
  has been destroyed.
- Invalid imported images return safely to automatic capture; malformed cached
  PNGs are no longer accepted as ready sources.
- Superseded private wallpaper imports are pruned after a successful replacement
  instead of accumulating indefinitely.
- Renderer readiness failures fall back to Lens Flare instead of leaving sound
  with no visible effect.
- Effect selections are persisted even if the settings Activity closes during
  its short debounce.
- Fold detection also inspects inactive built-in displays.

## Samsung wallpaper dimming

Samsung's “dim wallpaper when Dark mode is on” post-processing is not exposed
to third-party apps as part of the lockscreen image. Keeping it enabled can make
the live lockscreen and the L.L.E source differ visibly. The wizard detects the
setting, marks disabling it as strongly recommended, and opens Samsung Settings.
Samsung protects the exact page, so L.L.E cannot toggle it automatically.

## Signing migration

The public Beta builds used the project's historical development certificate.
The stable APKs use Android APK Signature Scheme v3.1 signing lineage:

- Android 13 and newer use the registered stable certificate.
- Earlier supported Android versions use the compatible historical signer.

This preserves in-place updates and local L.L.E preferences while completing
the move to the stable signing identity.

## Privacy

The application has no Internet permission. Screenshots, wallpaper imports and
derived effect caches remain local in app-private storage. Accessibility is
used to observe lockscreen state and host the selected effect. Optional “All
files access” is used only when attempting to read the current Samsung
lockscreen wallpaper; a normal document picker is available as fallback.

## Known limitations

- Direct wallpaper modes remain Beta and precise alignment matters.
- Samsung does not expose safe panel-specific wallpaper-setting APIs to
  third-party apps on Fold devices. “Set lockscreen + cache” is therefore
  disabled there; use automatic capture or separate exact Cover/Main images.
- Abstract Tiles line motion and effect timing can vary slightly by GPU,
  refresh rate and Android build.
- Very unusual protected or layered lockscreen surfaces may require manual
  wallpaper selection.

## Third-party components

Some builds contain legacy Samsung compatibility components extracted from
firmware. Rights in those components remain with their respective owners. L.L.E
is not affiliated with or endorsed by Samsung.
