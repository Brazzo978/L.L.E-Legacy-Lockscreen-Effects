# L.L.E / L.L.E 64

LLE restores a collection of legacy lockscreen effects on modern Android.
The ARM32 and ARM64 applications share one project and can be installed
together. ARM32 is permanently named **L.L.E** (`com.codex.lle`); ARM64 is
permanently named **L.L.E 64** (`com.codex.lle64`).

ARM64 is the actively developed edition. The ARM32 build is retained for
historical continuity and now receives compatibility and critical bug fixes
only; new effects and features target ARM64.

## APK selection

| APK | ABI | Recommended use |
|---|---|---|
| `LLE-1.0.4.1-32-bit.apk` | `armeabi-v7a` | Frozen historical compatibility build |
| `LLE64-1.0.4.4-64-bit.apk` | `arm64-v8a` | Recommended build and active development target |

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
| S5 Stone Skipping | Available | Available |
| S5 Brilliant Ring | Available | Available |
| N3 Watercolor | Available | Available |
| N2 Ink in Water / Indigo | Available | Available |
| N4 Abstract Tiles | Available | **Beta** |
| N4 Geometric Mosaic | Available | **Beta** |
| N5 Colored Droplet | Available | Available |
| N5 Colored Droplet + Gyro | Available | Available |
| N5 Sparkling Bubbles | Available | Available |
| Tab S Blind | Available | Available |
| Tab S Brilliant Cut | Available | Available |
| Seasonal / Spring / Summer / Autumn / Winter | Available | Available |

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

## Start here

Use the current ARM64 release unless the device is genuinely 32-bit-only:

- [Install the official APK manually — illustrated Samsung flow](docs/INSTALL_APK.md)
- [Install or update from a computer with ADB](docs/INSTALL_ADB.md)
- [Frequently asked questions and troubleshooting](docs/FAQ.md)

The tested Samsung first-start sequence is:

1. Install the verified APK and let Play Protect scan it.
2. Open L.L.E's Accessibility step.
3. In **Accessibility → Installed apps → L.L.E 64**, make the first enable
   attempt.
4. If Android blocks it, return to L.L.E and use the recovery page.
5. Open **L.L.E App info → three-dot menu → Allow restricted settings**.
6. Return to Accessibility and enable L.L.E 64.
7. Complete battery, wallpaper, feature, capture, and touch-box setup.

On tested Samsung firmware, **Allow restricted settings** may not appear until
after the first blocked Accessibility attempt. The illustrated guide shows
every screen in order.

Quick ARM64 ADB update:

```shell
adb install --no-incremental -r "LLE64-1.0.4.4-64-bit.apk"
```

Open ARM64:

```shell
adb shell am start -n com.codex.lle64/com.codex.lle.ControlActivity
```

Historical ARM32 update:

```shell
adb install --no-incremental -r "LLE-1.0.4.1-32-bit.apk"
```

Both can remain installed, but their preferences and screenshot caches are
separate. Enable only one of the LLE/LLE64 accessibility services at a time.

## First-time setup

1. Open the intended L.L.E. application and follow the first-launch wizard.
2. Enable the matching Accessibility service when Android Settings opens.
3. Allow unrestricted battery use, or continue with the displayed warning.
4. On Samsung devices, disable lockscreen wallpaper dimming and Dynamic Lock
   Screen when prompted. This is strongly recommended because protected dimming
   is not exposed to L.L.E, while Dynamic Lock Screen can replace the image
   after every lock; either can make effect regions flash or mismatch.
5. Choose automatic lockscreen capture or one of the Beta direct-wallpaper modes.
6. Select whether to run the charging doodle, lockscreen effects, or both.
7. Return to the main screen, select an effect and configure the touch box.
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

### Effects are silent

On Samsung, open **Settings → Sounds and vibration → System sound**, then turn
on **Screen lock/unlock**. This switch is required because L.L.E. respects the
device's lockscreen sound policy.

Also use the normal Sound profile rather than Silent or Vibrate, raise the
System sound volume, and keep **Effect sounds** enabled in L.L.E. Media volume
is a separate channel.

See the [audio troubleshooting FAQ](docs/FAQ.md) for the complete Samsung flow,
other-vendor equivalents, and the information recorded by a debug report.

### The wrong Fold screen is used

Open the Fold wizard again, activate the requested screen, and save its
screenshot and touch box separately.

## Known limitations

- ARM64 Abstract Tiles Line movement can still differ slightly across devices.
- Beta effects can differ slightly across GPUs, refresh rates and Android
  versions.
- Screenshot capture can fail on protected or unusual lockscreen surfaces.
- Direct wallpaper import is a Beta feature and needs precise crop alignment.
- Samsung does not expose panel-specific wallpaper setting APIs to third-party
  apps on Fold devices; use automatic capture or provide Cover/Main images
  separately.
- Fold detection may require adjustment on untested models.

## Privacy and device access

L.L.E has no Internet permission. Lockscreen screenshots, imported wallpapers
and effect caches remain in the app's private local storage. Accessibility is
used to detect lockscreen state and render the selected effect. “All files
access” is requested only for the optional attempt to read the current Samsung
lockscreen wallpaper; the manual picker remains available when that layer is
not exposed.

Beginning with version 1.0.4, L.L.E migrates from the historical Beta
certificate to the registered
stable certificate using Android signing lineage. Existing Beta installations
can be updated in place: Android 13 and newer use the stable certificate, while
older supported Android versions retain the compatible historical signer.

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

- [1.0.4.4 stable release notes](docs/RELEASE_NOTES_1.0.4.4.md)
- [1.0.4.3 stable release notes](docs/RELEASE_NOTES_1.0.4.3.md)
- [1.0.4.2 stable release notes](docs/RELEASE_NOTES_1.0.4.2.md)
- [1.0.4.1 stable release notes](docs/RELEASE_NOTES_1.0.4.1.md)
- [1.0.4 stable release notes](docs/RELEASE_NOTES_1.0.4.md)
- [1.0.4 Beta 1969 release notes](docs/RELEASE_NOTES_1.0.4_BETA_1969.md)
- [1.0.3 Beta 1 release notes](docs/RELEASE_NOTES_1.0.3_BETA_1.md)
- [1.0.2 Beta 2 release notes](docs/RELEASE_NOTES_1.0.2_BETA_2.md)
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

## License

Project-authored source code is available under the
[PolyForm Noncommercial License 1.0.0](../LICENSE.md). The license does not
relicense legacy or proprietary third-party components; those remain subject to
their respective owners' rights and terms.
