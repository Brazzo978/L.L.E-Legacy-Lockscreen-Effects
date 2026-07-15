# Fold dual-panel port

## Runtime model

The Fold port is shared Java logic and is therefore present in both the ARM32
and ARM64 products. It does not assume that cover and inner panels always have
different logical display IDs. On the tested Galaxy Z Fold7 (`SM-F966B`,
Android 16), Samsung keeps logical display ID `0` and changes its physical size:

- cover profile: `1080x2520`;
- main/inner profile: `1968x2184`.

`FoldDisplayTarget` detects fold capability through the public hinge-angle
feature or multiple built-in panels, resolves the focused/active display, and
classifies the current panel from its aspect ratio. Regular single-screen
devices retain the old `single` profile and file names.

`FOLD MODE (dual panels)` is exposed in the lockscreen debug section. Its
default is enabled when fold hardware is detected, while an explicitly saved
toggle value wins for diagnostics. Disabling it returns screenshot and touch
storage to the original `single` profile.

## Screenshot caches

Fold devices use two persistent files:

- `unlock_effect_background_cover.png`;
- `unlock_effect_background_main.png`.

Only the active profile bitmap is decoded in RAM. Each slot has independent
last-capture and handled-refresh metadata. Before a bitmap is loaded or a
legacy file is migrated, its orientation and aspect ratio are checked against
the active panel. This prevents a cover screenshot from reaching the inner
renderer, and vice versa.

The former `unlock_effect_background.png` remains the canonical path on normal
phones. On a Fold it is copied once into the matching slot only when its
dimensions match that panel. Legacy per-effect files follow the same guarded
migration path.

## Dual touch boxes

With Fold mode enabled, touch geometry and wizard screenshots are independent:

- preferences use the `_cover` and `_main` suffixes;
- wizard images are `touch_box_lockscreen_cover.png` and
  `touch_box_lockscreen_main.png`;
- the editor exposes a `Cover <-> Main` switch and can edit either cached
  screenshot without scaling its coordinates through the currently active
  panel;
- switching editor pages saves the page being left, so both boxes can be
  drawn in one wizard session;
- a missing wizard screenshot is captured only when its requested physical
  panel is active.

The old unsuffixed box and screenshot are migrated once into the profile whose
aspect ratio matches their reference size. The other panel starts from its own
safe default box until it is drawn. Screenshot callbacks validate both display
ID and panel profile because Samsung can keep ID `0` across a fold transition.

The wizard now opens on a dual-panel dashboard with a thumbnail, dimensions,
saved-area status and independent edit/capture actions for each panel. If a
dedicated `touch_box_lockscreen_<profile>.png` is missing, the editor reuses the
validated `unlock_effect_background_<profile>.png` instead of incorrectly
showing `Capture needed`. This was device-validated for the Main `1968x2184`
cache. Cancelling while a capture is armed clears the service request token and
returns to the dashboard, so a later request cannot be completed by a stale
callback.

## Panel transition

Display callbacks and coalesced accessibility events trigger a target refresh.
When the profile changes, the service:

1. invalidates in-flight screenshot generations;
2. detaches touch, doodle and effect windows from the old `WindowManager`;
3. destroys the old native renderer;
4. creates an accessibility-overlay window context for the active display;
5. recreates the renderer with the new display resources;
6. loads only the matching cache and rebuilds the touch layer.

Screenshot callbacks capture the resolved active display ID and profile. A
result is discarded if either changes before the callback completes.

## ARM64 device validation (2026-07-15)

The unified ARM64 APK was installed in place with preferences preserved and
tested through closed -> opened -> closed transitions:

- cover cache migrated and loaded at `1080x2520`;
- inner screenshot captured and persisted at `1968x2184`;
- Watercolor overlay frame verified at `1080x2520` on cover;
- Watercolor overlay frame verified at `1968x2184` on inner;
- touch listener resized from `1080x1560` to `1968x1352` and back;
- a service restart in inner mode loaded the main file from disk with
  `memoryCache=false`;
- returning to cover selected the cover file again;
- Fold mode auto-enabled on the Fold7 (`single -> cover` at service connect);
- inner touch window mounted at `0,688 - 1968,1980` and an ADB gesture produced
  `DOWN/MOVE/UP` plus `unlock effect gesture begin`;
- cover touch window mounted independently at `0,730 - 1080,2100` and produced
  the same complete gesture sequence;
- no `AndroidRuntime` crash or accessibility-overlay token failure occurred.
- the dual dashboard showed Cover `1080x2520` and reused the existing Main
  effect screenshot at `1968x2184`, eliminating the false Main capture warning;
- the combined screenshot viewer displayed both cached panel slots explicitly.

The device-state test override was reset after validation; the phone then
reported its current physical state `3` (`1968x2184`, inner panel).
