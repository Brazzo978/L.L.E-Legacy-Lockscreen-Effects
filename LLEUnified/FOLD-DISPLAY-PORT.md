# Fold dual-panel port

## Runtime model

The Fold port is shared Java logic and is therefore present in both the ARM32
and ARM64 products. It does not assume that cover and inner panels always have
different logical display IDs. On the tested Galaxy Z Fold7 (`SM-F966B`,
Android 16), Samsung keeps logical display ID `0` and changes its physical size:

- cover profile: `1080x2520`;
- main/inner profile: `1968x2184`.

`FoldDisplayTarget` detects fold capability through the public hinge-angle
feature, resolves the focused/active display, and classifies the current panel
from its aspect ratio. Regular single-screen devices retain the old `single`
profile and file names.

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
- no `AndroidRuntime` crash or accessibility-overlay token failure occurred.

The device-state test override was reset to physical `CLOSED` state after the
validation.
