# LLE / LLE64

LLE restores a collection of legacy lockscreen effects on modern Android.
The ARM32 and ARM64 applications share one project and can be installed
together. ARM32 is permanently named **LLE** (`com.codex.lle`); ARM64 is
permanently named **LLE64** (`com.codex.lle64`).

> **Beta:** expect some device-specific differences. Keep another unlock method
> available while testing.

## APK selection

| APK | ABI | Recommended use |
|---|---|---|
| `LLE-1.0.2-Beta-1-32-bit.apk` | `armeabi-v7a` | Daily use on devices that support 32-bit ARM applications |
| `LLE64-1.0.2-Beta-1-64-bit.apk` | `arm64-v8a` | ARM64-only devices and ARM64 Beta testing |

Check the supported ABIs:

```shell
adb shell getprop ro.product.cpu.abilist
```

`INSTALL_FAILED_NO_MATCHING_ABIS` means the selected APK cannot run on the
device.

## Effect availability

| Effect | ARM32 | ARM64 |
|---|:---:|:---:|
| S4 Lens Flare | Available | Available |
| S3 Water Ripple | Available | Available |
| S5 Popping Colours | Available | Available |
| N3 Watercolor | Available | Available |
| N4 Abstract Tiles | Available | **Beta** |
| N4 Geometric Mosaic | Available | **Beta** |
| N5 Colored Droplet | Available | Available |
| N5 Colored Droplet + Gyro | Available | Available |
| N5 Sparkling Bubbles | Available | Available |

Water Ripple and Watercolor are included as Beta effects. Effects unavailable
for the running application are automatically hidden.

On ARM64, the effect picker exposes two Abstract Tiles variants:
**Abstract Tiles · Lines** for the full Line pass and
**Abstract Tiles · No lines** for the clean tiles-only renderer.

## Requirements

- Android 6.0 or newer.
- Accessibility-service permission.
- USB debugging for ADB installation.
- A valid lockscreen screenshot and touch-box setup for effects that use them.

## Install with ADB

Install or update ARM32:

```shell
adb install --no-incremental -r "LLE-1.0.1-Beta-1-32-bit.apk"
```

Install or update ARM64:

```shell
adb install --no-incremental -r "LLE-1.0.1-Beta-1-64-bit.apk"
```

Open ARM32:

```shell
adb shell am start -n com.codex.lle/.ControlActivity
```

Open ARM64:

```shell
adb shell am start -n com.codex.lle64/com.codex.lle.ControlActivity
```

Both can remain installed, but their preferences and screenshot caches are
separate. Enable only one of the LLE/LLE64 accessibility services at a time.

## First-time setup

1. Open the intended L.L.E. application.
2. Tap the accessibility status badge and enable that L.L.E. service.
3. Return to the app and enable the main switch.
4. Open **LOCKSCREEN** and enable **Unlock effect on lockscreen**.
5. Select an effect and wait for it to be applied.
6. Capture the lockscreen background.
7. Configure the touch box.
8. Lock and wake the device, then test the saved region.

If both APKs are installed and Android shows two services with the same label,
disable the current service before enabling the other one.

## Screenshot and touch box

Most effects need a recent lockscreen screenshot.

1. Select the intended effect.
2. Under **Screenshot service**, choose **Force screenshot recapture**.
3. Lock and wake the device, leaving the lockscreen visible briefly.
4. Return to L.L.E. and verify the saved image.
5. Open the touch-box wizard and draw the region that should accept gestures.

If an effect is blank or uses the wrong image, recapture the screenshot before
changing other settings.

## Fold support

Fold mode supports separate Cover and Main configurations:

- screenshot cache for each screen;
- touch box for each screen;
- effect enable/disable switch for each screen;
- doodle enable/disable switch for each screen.

Use the dual-panel wizard and complete each step while the requested screen is
active.

## Troubleshooting

### The effect does not appear

- Confirm that the intended L.L.E. accessibility service is enabled.
- Disable the other L.L.E. service if both APKs are installed.
- Check the main switch and **Unlock effect on lockscreen**.
- Wait briefly after changing the selected effect.
- Verify the screenshot cache and touch box.
- Check the active Cover/Main panel on a Fold.

### The service stops

Remove battery restrictions for L.L.E. in Android or device battery settings.
Some devices stop background accessibility services aggressively.

### The wrong Fold screen is used

Open the Fold wizard again, activate the requested screen, and save its
screenshot and touch box separately.

## Known limitations

- ARM64 Abstract Tiles Line movement can still differ slightly across devices.
- Beta effects can differ slightly across GPUs, refresh rates and Android
  versions.
- Screenshot capture can fail on protected or unusual lockscreen surfaces.
- Fold detection may require adjustment on untested models.
- Geometric Mosaic is ARM32-only.
- The APKs are debug-signed Beta builds, not production releases.

## Build from source

Build both normal targets:

```powershell
powershell -ExecutionPolicy Bypass -File .\LLEUnified\build.ps1
```

Build one target:

```powershell
powershell -ExecutionPolicy Bypass -File .\LLEUnified\build.ps1 -Target Arm32
powershell -ExecutionPolicy Bypass -File .\LLEUnified\build.ps1 -Target Arm64
```

The ARM64 target is always co-installable as `com.codex.lle64`:

```powershell
powershell -ExecutionPolicy Bypass -File .\LLEUnified\build-arm64.ps1
```

Build requirements are Android SDK Platform 35, Build Tools 35.0.1 and Android
NDK r27d.

## More documentation

- [1.0.2 Beta 1 release notes](docs/RELEASE_NOTES_1.0.2_BETA_1.md)
- [Architecture notes](ARCHITECTURE.md)
- [Fold support notes](FOLD-DISPLAY-PORT.md)

Detailed development and research records are kept under `docs/` and `ports/`
instead of this user-facing README.

## Third-party components

Some builds contain **legacy third-party compatibility components**. Rights in
those components remain with their respective owners. This project does not
claim ownership of them and is not affiliated with or endorsed by their
owners. Anyone redistributing binary builds is responsible for confirming the
applicable permissions.
