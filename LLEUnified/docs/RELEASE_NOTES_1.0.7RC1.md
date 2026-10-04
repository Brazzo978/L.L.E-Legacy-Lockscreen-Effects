# L.L.E 1.0.7RC1 — Effects Workshop (Tester prerelease)

This release candidate introduces the Effects Workshop with **705 controls
across 39 selectable effects**: Samsung and Good Lock, LG, Sony, Seasonal and
Emoji Trail.

## Effects Workshop

- Open **Effects → Effects workshop → Open advanced workshop**, at the bottom
  of the Effects page. The editor shows the currently selected effect's controls.
- Adjust appearance, particles, geometry, motion, fluid simulation, shaders and
  audio where supported by each renderer.
- Compact cards use teal sliders, decimal steps, light haptic feedback and
  minus/plus buttons for precise changes. Values can also be entered directly.
- Each parameter has an English explanation through **ⓘ** and a **↺** button
  to restore its default. Hula Hoop and Light Particle show the active variant's
  relevant controls.
- Editing enables custom settings. **Apply** saves; **Cancel** discards the
  draft. **Reset all** restores draft defaults and takes effect after Apply.
- Disabling custom settings restores the original effect while keeping saved
  values. Final unlock and handoff timing remains unchanged.
- Renderer configuration checks recover from missed preference notifications
  and apply changes after any active gesture or protected transition finishes.

## Installation and update channel

Download **LLE64-1.0.7RC1-Workshop-Tester.apk**. This is the separate
**L.L.E Tester** application (`com.codex.lle64.test`), versionCode **53**, for
ARM64 devices running Android 6.0 / API 23 or later. It can coexist with the
stable application and updates an existing compatible Tester installation.

This GitHub release is a **prerelease**, explicitly excluded from **Latest**.
The stable release remains **1.0.6.7**. Neither `LLE_VERSION.txt` feed is
changed, and Companion ignores the Tester package, so it does not announce
this RC as an update.

## Validation

- Complete ARM64 build and APK signature verification.
- Workshop schema: **7,137 assertions**, covering all 705 controls and 39 IDs.
- Slider arithmetic: **160,188 assertions**, including decimal drift, negative
  ranges, endpoints, partial final steps and manual precision.
- Java scene, configuration, timing and native fluid/geometry tests validated
  the effect settings; persistence and renderer identity checks passed.
- The Samsung user confirmed that custom parameters take effect. Complete
  physical coverage of every effect, asset, sound and gesture is still ongoing.

See [Workshop documentation](EFFECTS_WORKSHOP.md) for coverage and behavior.
Use **SHA256SUMS.txt** to verify the downloaded APK.
