# L.L.E. Unified

**L.L.E. - Legacy Lockscreen Effects** restores selected Samsung lockscreen
effects inside a modern Android accessibility overlay. The ARM32 application ID
is `com.codex.lle`; the co-installable ARM64 Beta uses
`com.codex.lle.arm64dev`. Java and JNI classes remain in `com.codex.lle`.

This repository is the canonical, shared source tree for both ARM32 and ARM64.
Java logic, resources, preferences, Fold routing, touch geometry, background
capture and the application UI are developed once. Each APK then packages the
native engines appropriate for its process architecture.

> **Beta:** this project contains reconstructed renderers, patched
> legacy binaries and device-specific lockscreen integration. Back up anything
> important, keep another unlock method available and expect visual or
> lifecycle differences between Android/One UI versions. ARM64 Abstract Tiles
> remains explicitly Alpha.

## Contents

- [Choose an APK](#choose-an-apk)
- [Effect availability](#effect-availability)
- [How the unified architecture works](#how-the-unified-architecture-works)
- [Fold dual-screen support](#fold-dual-screen-support)
- [Prerequisites](#prerequisites)
- [Install with ADB](#install-with-adb)
- [First-time setup](#first-time-setup)
- [Screenshot capture and touch-box setup](#screenshot-capture-and-touch-box-setup)
- [Update, switch ABI or uninstall](#update-switch-abi-or-uninstall)
- [Troubleshooting](#troubleshooting)
- [Beta limitations](#beta-limitations)
- [Build from source](#build-from-source)
- [Technical documentation](#technical-documentation)
- [Firmware and redistribution warning](#firmware-and-redistribution-warning)

## Choose an APK

The Beta release produces a normal ARM32 package and a co-installable ARM64
package. Both have the launcher label **L.L.E.**, but keep separate preferences
and screenshot caches.

| APK | Use it when | Native ABI |
|---|---|---|
| `LLE-1.0.1-Beta-1-32-bit.apk` | The device supports 32-bit ARM applications and you want the original Samsung ARM32 engines, including Geometric Mosaic | `armeabi-v7a` |
| `LLE-1.0.1-Beta-1-64-bit.apk` | The device is ARM64-only, or you want the ARM64 ports and Note 5 ARM64 engines | `arm64-v8a` |

The decision is about the **application process**, not merely the first value
reported by `ro.product.cpu.abilist`. A modern 32/64-bit device can run either
product. An ARM64-only device cannot run the ARM32 APK.

Check the device ABI list with:

```shell
adb shell getprop ro.product.cpu.abilist
```

## Effect availability

This table is derived from the current `EffectAvailability` registry and the
renderer construction paths in `ChargingAccessibilityService`.

| Effect | Internal ID | ARM32 process | ARM64 process |
|---|---:|---|---|
| S4 Lens Flare | 0 | Available - app-owned renderer | Available - app-owned renderer |
| S5 Popping Colours | 2 | Available - Samsung dex renderer | Available - Samsung dex renderer |
| N3 Watercolor | 3 | Available - original patched Samsung ARM32 engine | Available - reconstructed ARM64 GLES engine |
| N5 Colored Droplet | 4 | Available - original patched Samsung ARM32 engine | Available - Note 5 ARM64 engine with overlay integration |
| N5 Sparkling Bubbles | 5 | Available - original patched Samsung ARM32 engine | Available - Note 5 ARM64 engine with overlay integration |
| N4 Abstract Tiles | 7 | Available - original patched Samsung ARM32 engine | **Alpha** - reconstructed ARM64 GLES engine |
| N4 Geometric Mosaic | 8 | Available - original patched Samsung ARM32 engine | Unavailable and hidden |
| N5 Colored Droplet + Gyro | 9 | Available - original patched Samsung ARM32 engine | Available - Note 5 ARM64 engine with accelerometer gravity |
| S3 Water Ripple | 10 | Available - original patched Samsung ARM32 engine | Available - reconstructed ARM64 GLES engine |
| Tabs Blind WIP | 11 | Unavailable and hidden | Unavailable and hidden |
| N3 Ink in Water WIP | 12 | Unavailable and hidden | Unavailable and hidden |

The picker and preference validation use the same registry. If a saved effect
is not available in the running process, L.L.E. safely changes the selection to
S4 Lens Flare. Native libraries are loaded lazily only when their effect is
selected.

All currently exposed effects use a lockscreen screenshot as a background or
colour source somewhere in their rendering path. The exact composition differs
by effect: reconstructed overlays generally reveal the screenshot only inside
local particles, waves, brush strokes or tiles.

## How the unified architecture works

```text
Shared app source and preferences
             |
             +-- ARM32 APK --> original Samsung ARM32 engines + patches
             |
             +-- ARM64 APK --> reconstructed GLES engines + Note 5 ARM64 engines
```

The important runtime rules are:

- `Process.is64Bit()` is the source of truth for ABI-specific availability.
- The accessibility service creates an effect renderer only after that effect
  is selected.
- ARM32 retains the original Samsung renderer host where an original engine is
  available.
- ARM64 clean-room ports use app-owned JNI and lifecycle boundaries instead of
  attempting to load ARM32 C++/STLport objects into a 64-bit process.
- Renderer construction, context loss, resize and teardown are bounded. A
  failed native renderer is destroyed and replaced with Lens Flare.
- Screenshot caches and touch boxes are shared application features, while the
  native library set remains ABI-specific.

The frozen pre-unification implementations remain available as references in
`../unlock-effects-test/charging-touch-test-apk` and `../LLE64`. New application
behavior belongs in this unified tree.

## Fold dual-screen support

Fold support is shared by both products. `FOLD MODE (dual panels)` defaults to
enabled when Fold hardware is detected and is also available in the lockscreen
Debug section for diagnostics.

With Fold mode enabled, L.L.E. maintains independent state for Cover and Main:

- screenshot caches:
  `unlock_effect_background_cover.png` and
  `unlock_effect_background_main.png`;
- touch-box geometry and wizard screenshots for each panel;
- independent **Allow lockscreen effect** switches;
- independent **Allow charging doodle** switches;
- active-display renderer/window contexts and guarded capture callbacks.

Only the active panel's screenshot is normally decoded into RAM. Disabling an
effect or doodle on one panel does not erase that panel's saved screenshot or
touch box.

Use **Dual touch box wizard** to capture and draw both touch regions. A capture
request must be completed while the requested physical panel is active. Fold
detection and panel classification are still experimental and should be checked
after an OS update.

See [Fold dual-panel port](FOLD-DISPLAY-PORT.md) for the runtime model and the
device-validation record.

## Prerequisites

### To install and run

- An ARM Android device supporting the APK ABI you selected.
- Android 6.0/API 23 or later to install the application.
- Android 11/API 30 or later is strongly recommended and is required for the
  accessibility screenshot API used by automatic effect-background capture.
- ADB from Android Platform Tools and USB debugging enabled for command-line
  installation.
- Permission to enable the L.L.E. accessibility service.
- Root is **not** required for normal effects, screenshot capture or Fold mode.

### To build

- Windows PowerShell.
- JDK tools providing `javac.exe` and `jar.exe`; the scripts compile Java with
  source/target level 8.
- Android SDK Platform 35.
- Android SDK Build Tools `35.0.1`.
- Android NDK r27d, either through `ANDROID_NDK_HOME` or at the sibling path
  expected by `build-arm64.ps1`.
- The tracked Samsung dex/native inputs and the sibling reverse/build tools
  referenced by the scripts.

Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` if the SDK is not located under the
default `%LOCALAPPDATA%\Android\Sdk` path.

## Install with ADB

First verify that exactly the intended device is connected:

```shell
adb devices
```

Use `adb -s SERIAL ...` on every command if more than one device is listed.

Install the ARM64 Beta release product:

```shell
adb install --no-incremental -r "LLE-1.0.1-Beta-1-64-bit.apk"
```

Install the ARM32 Beta release product:

```shell
adb install --no-incremental -r "LLE-1.0.1-Beta-1-32-bit.apk"
```

Launch the ARM32 or ARM64 control application respectively:

```shell
adb shell am start -n com.codex.lle/.ControlActivity
adb shell am start -n com.codex.lle.arm64dev/com.codex.lle.ControlActivity
```

`INSTALL_FAILED_NO_MATCHING_ABIS` means the device cannot run the selected
APK. Use the other product only if its ABI appears in the device ABI list.

## First-time setup

1. Open L.L.E.
2. Tap the accessibility status badge in the header. Android opens
   Accessibility settings.
3. Find **L.L.E.** and enable its
   accessibility service. Read Android's warning
   and continue only if you understand the access granted to the application.
4. Return to L.L.E. and enable the master switch in the header if it is off.
5. Open the **LOCKSCREEN** tab.
6. Enable **Unlock effect on lockscreen**.
7. Select an effect shown by the picker. The UI intentionally applies a new
   selection after a two-second delay.
8. Lock and wake the device without immediately leaving the lockscreen. Touch
   or swipe inside the configured touch box.

Effect sounds, the matching lock sound and their active-hour schedules are
separate controls. Charging Doodle has its own tab and can take priority over
the effect surface when enabled.

## Screenshot capture and touch-box setup

### Capture the effect background

Most effects need a validated lockscreen screenshot/colour map.

1. Select the intended effect and wait for its two-second apply delay.
2. Under **Screenshot service**, check the cache status.
3. Tap **Force screenshot recapture**.
4. Lock/wake the device and leave the actual lockscreen visible long enough for
   the accessibility service to capture it. The service rejects captures made
   against the L.L.E. control app, AOD or the wrong Fold panel.
5. Return to the app and use **View colormap screenshot** or
   **View both panel screenshots** to verify the result.

In the Debug section, **Wake lockscreen for hard recapture** allows scheduled
refresh work to wake and relock the lockscreen. It is off by default. Secure or
DRM-protected surfaces may prevent Android from providing a usable screenshot.

### Configure the touch area

1. Expand the lockscreen Debug section and find **Touch box**.
2. Open **Touch box screenshot wizard** on a phone, or
   **Dual touch box wizard** in Fold mode.
3. Capture the requested lockscreen image if no valid image is available.
4. Draw/save the region in which L.L.E. should accept unlock gestures.
5. On a Fold, repeat for Cover and Main while each requested panel is active.
6. Use **Show touch box** temporarily if visual confirmation is needed, then
   hide it for normal use.

Resetting a touch box restores the safe default for that profile; it does not
delete the panel screenshot cache.

## Update, switch ABI or uninstall

### Update in place

Install the newer APK with `-r`:

```shell
adb install --no-incremental -r "path/to/new/LLE-1.0.1-Beta-1-64-bit.apk"
```

An in-place update preserves preferences, touch boxes and screenshot caches
when the package is signed with the same certificate.

### Co-install ARM32 and ARM64 Beta

The release products use separate application IDs, so installing one does not
replace the other:

```shell
adb install --no-incremental -r "LLE-1.0.1-Beta-1-32-bit.apk"
adb install --no-incremental -r "LLE-1.0.1-Beta-1-64-bit.apk"
```

Their preferences and caches remain separate. Enable only the service belonging
to the build you are actively testing.

### Build the ARM64 companion from source

For side-by-side phone testing, build the ARM64-only companion variant:

```powershell
powershell -ExecutionPolicy Bypass -File .\LLEUnified\build-arm64.ps1 -Companion
adb install --no-incremental -r ".\LLEUnified\build\arm64-v8a-dev\LLE-arm64-dev.apk"
adb shell am start -n com.codex.lle.arm64dev/com.codex.lle.ControlActivity
```

The companion installs as `com.codex.lle.arm64dev` with the same launcher label
**L.L.E.**. Its preferences, screenshot caches and process ABI are
separate from the normal `com.codex.lle` installation. Keep only one L.L.E.
accessibility service enabled at a time: two enabled render services would both
listen to the lockscreen and could create competing overlays. This internal
variant is used for the co-installable ARM64 beta artifact.

### Uninstall

```shell
adb uninstall com.codex.lle
adb uninstall com.codex.lle.arm64dev
```

This removes the application, preferences and private screenshot caches. You
may also disable the accessibility service before uninstalling.

## Troubleshooting

### The effect does not appear

- Confirm the accessibility status badge is green and the header master switch
  is enabled.
- Confirm **Unlock effect on lockscreen** and its active-hour schedule.
- Wait two seconds after changing the effect.
- Check that Charging Doodle is not occupying the active surface.
- On a Fold, check the active panel's **Allow lockscreen effect** switch.
- Re-run the touch-box wizard and verify that the gesture begins inside the
  saved region.

### The effect is black, blank or uses the wrong image

- Force a screenshot recapture while the real lockscreen is visible.
- Verify the cached image in the Screenshot service viewer.
- On a Fold, capture Cover and Main independently; a cache with the wrong
  aspect ratio is deliberately rejected.
- Confirm Android 11/API 30 or newer for automatic accessibility screenshots.
- Some secure lockscreen content cannot be captured by Android.

### Selection returns to Lens Flare

The requested effect is unavailable for this process or its renderer failed to
load/initialize. Confirm that the correct ABI APK was installed and that the
APK contains the expected native libraries:

```shell
adb shell pm path com.codex.lle
adb logcat -d -s ChargingA11y:V LLE64AbstractTiles:V AndroidRuntime:E '*:S'
```

### The accessibility service stops in the background

Samsung battery policy can stop long-lived services. Open the Debug section,
check the battery-optimization status and use **Request battery unrestricted**
or Samsung's battery settings if appropriate for your device.

### The wrong panel is active on a Fold

- Verify **FOLD MODE (dual panels)** in Debug.
- Fold/unfold once, reopen L.L.E. and check the active-panel label.
- Re-capture and redraw only the panel reported by the wizard.
- Collect `adb logcat -d -s ChargingA11y:V '*:S'` before changing more state.

## Beta limitations

- ARM64 Water Ripple and Watercolor are beta reconstructions. Their recovered
  geometry, timing and shader behavior target the originals, but byte-for-byte
  or pixel-for-pixel parity across GPUs is not expected.
- ARM64 Abstract Tiles remains Alpha while its original scatter channels and
  animation curves receive another fidelity pass.
- Transparent lockscreen composition intentionally differs from Samsung's
  original opaque wallpaper framebuffer.
- High-refresh displays, power-saving refresh changes, GPU drivers and One UI
  lockscreen changes can expose timing or lifecycle differences.
- Screenshot-backed rendering can show an older lockscreen image until a valid
  replacement capture succeeds.
- Fold panel detection relies on public display/hinge information plus aspect
  classification and may need adjustment for untested models.
- The co-installable APKs keep separate preferences and screenshot caches. Do
  not enable both accessibility render services at the same time.
- Geometric Mosaic is ARM32-only. Tabs Blind and Ink in Water are registered
  WIP slots but are hidden on both ABIs.
- ARM32 engines cannot run on ARM64-only devices.
- Diagnostic/root controls are not part of the normal setup and should remain
  disabled unless a test explicitly requires them.
- Current APKs are debug-signed Beta artifacts, not production releases.

## Build from source

From the `LLEUnified` directory, build both products:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Build one target:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Target Arm32
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Target Arm64
powershell -ExecutionPolicy Bypass -File .\build-arm64.ps1 -Companion
```

Outputs:

- `build/armeabi-v7a/LLE-armeabi-v7a-debug.apk`
- `build/arm64-v8a/LLE-arm64-debug.apk`
- `build/arm64-v8a-dev/LLE-arm64-dev.apk` (co-installable ARM64 release base)

Advanced ARM64 diagnostic switches are exposed by `build.ps1` and
`build-arm64.ps1`, including the Note 5/Ripple probes and Watercolor feedback
mode. They are not required for a normal Stable build.

The build scripts compile resources/Java, stage the ABI-specific Samsung dex
and native inputs, sign each APK and verify its contents. ARM64 additionally
checks exact native entries, ELF architecture, SONAMEs, dependencies and JNI
exports. After shared Java, resource or lifecycle changes, build both targets.

## Technical documentation

Core architecture and validation:

- [Historical Early Alpha release notes](docs/RELEASE_NOTES_EARLY_ALPHA.md)
- [1.0.1 Beta 1 release notes](docs/RELEASE_NOTES_1.0.1_BETA_1.md)
- [Unified ARM32/ARM64 architecture](ARCHITECTURE.md)
- [Fold dual-panel port](FOLD-DISPLAY-PORT.md)
- [Performance optimization and lifecycle](PERFORMANCE-OPTIMIZATION-2026-07-15.md)
- [ABI comparison on S23 Ultra](ABI-COMPARISON-S23U-2026-07-15.md)
- [Validation record](VALIDATION-2026-07-15.md)

Port documentation:

- [Abstract Tiles ARM64 port specification](ports/abstract-tiles/docs/ABSTRACT_TILES_ARM64_PORT_SPEC.md)
- [Water Ripple port overview](ports/water-ripple/README.md)
- [Water Ripple fidelity specification](ports/water-ripple/FIDELITY-SPEC.md)
- [Water Ripple lifecycle](ports/water-ripple/IMPLEMENTATION-LIFECYCLE.md)
- [Water Ripple transparent overlay](ports/water-ripple/IMPLEMENTATION-OVERLAY.md)
- [Watercolor port overview](ports/watercolor/README.md)
- [Watercolor implementation](ports/watercolor/IMPLEMENTATION.md)
- [Watercolor physics](ports/watercolor/reverse/PHYSICS.md)
- [Watercolor rendering](ports/watercolor/reverse/RENDERING.md)

Reverse-engineering inventories and immutable inputs are documented under
[`reverse/`](reverse/) and [`reference/`](reference/README.md).

## Firmware and redistribution warning

Parts of the build consume proprietary Samsung firmware libraries, Samsung
bytecode, sounds or other extracted assets. Those files are not relicensed by
this README. The ARM32 APK and the current ARM64 APK may contain proprietary
firmware-derived components even where individual effects use clean-room code.

Do not publish APKs, firmware blobs or GitHub release assets unless you have
separately established the right to redistribute every included component.
This repository is intended for research, preservation and private/local
compatibility testing; it does not grant trademark, copyright or patent
permissions from Samsung or any other rights holder.

The pre-unification state is preserved by Git tag
`lle-pre-unification-2026-07-15` for development provenance.
