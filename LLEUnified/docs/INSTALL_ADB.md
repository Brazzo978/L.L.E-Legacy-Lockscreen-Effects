# Install L.L.E 64 with ADB

This guide installs or updates the official ARM64 APK from a Windows, macOS, or
Linux computer. ADB is useful for testing and troubleshooting, but the device
owner must still approve Accessibility in Android's interface.

## 1. Install Android Platform Tools

Download the current
[Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools)
and extract the archive. Open a terminal in that directory or add it to the
system `PATH`.

On Windows, install the Samsung USB driver if the phone does not appear.

## 2. Enable USB debugging

On the phone:

1. Open **Settings → About phone → Software information**.
2. Tap **Build number** seven times.
3. Open **Developer options** and enable **USB debugging**.
4. Connect the phone and accept the computer's RSA fingerprint.

Use a trusted computer. Disable USB debugging again when it is no longer needed.
Samsung Auto Blocker can prevent USB commands; temporarily disable it only if it
explicitly blocks ADB, then enable it again after finishing.

## 3. Download and verify the APK

Download the current ARM64 APK and `SHA256SUMS.txt` from the
[official L.L.E releases page](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/releases/latest).

Verify the APK on Windows PowerShell:

```powershell
Get-FileHash -Algorithm SHA256 ".\LLE64-1.0.4.3-64-bit.apk"
```

On macOS:

```shell
shasum -a 256 "./LLE64-1.0.4.3-64-bit.apk"
```

On Linux:

```shell
sha256sum "./LLE64-1.0.4.3-64-bit.apk"
```

Compare the complete value with `SHA256SUMS.txt` from the same release.

## 4. Check the connection and device ABI

```shell
adb devices
adb shell getprop ro.product.cpu.abilist
```

The device must be listed as `device` and support `arm64-v8a`. If it says
`unauthorized`, unlock the phone and accept the USB-debugging prompt.

## 5. Install or update L.L.E 64

Open the terminal in the directory containing the APK:

```shell
adb install --no-incremental -r "LLE64-1.0.4.3-64-bit.apk"
```

`-r` preserves app data. `--no-incremental` avoids device-specific issues with
the native effect libraries. If an older ADB version does not recognize it,
update Platform Tools or temporarily use:

```shell
adb install -r "LLE64-1.0.4.3-64-bit.apk"
```

Do not uninstall before a routine update: uninstalling deletes preferences,
wallpaper caches, imported wallpapers, and touch-box calibration.

## 6. Confirm and open the application

Windows:

```powershell
adb shell dumpsys package com.codex.lle64 | Select-String "versionName|versionCode"
```

macOS or Linux:

```shell
adb shell dumpsys package com.codex.lle64 | grep -E "versionName|versionCode"
```

Open L.L.E:

```shell
adb shell am start -n com.codex.lle64/com.codex.lle.ControlActivity
```

## 7. Complete the first-start wizard

ADB installation may or may not trigger Android's sideload restriction,
depending on the device and OS build. Start with the wizard's normal
Accessibility step.

If Accessibility is blocked, use the tested recovery flow:

1. In Accessibility, open **Installed apps → L.L.E 64**.
2. Make the first enable attempt and let Android block it.
3. Return to L.L.E.
4. On the recovery page, open **L.L.E App info**.
5. Open the three-dot menu and tap **Allow restricted settings**.
6. Return to L.L.E and open Accessibility again.
7. Open **Installed apps → L.L.E 64**, enable the service, and confirm.

The first blocked attempt matters: Samsung may not display **Allow restricted
settings** before it. See the complete
[illustrated S23 flow](INSTALL_APK.md#2-make-the-first-accessibility-attempt).

Do not grant Accessibility through `settings put secure`, root commands, or
other bypasses.

## Useful commands

Open application info:

```shell
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.codex.lle64
```

Open general Accessibility settings:

```shell
adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
```

Samsung protects the service-details activity with a privileged permission, so
L.L.E and ADB may only be able to open the general Accessibility page. Continue
manually through **Installed apps → L.L.E 64**.

Read recent L.L.E logs on Windows:

```powershell
adb logcat -d -v threadtime | Select-String "LLE|AndroidRuntime"
```

On macOS or Linux:

```shell
adb logcat -d -v threadtime | grep -Ei "LLE|AndroidRuntime"
```

## Installation errors

### `INSTALL_FAILED_NO_MATCHING_ABIS`

The device does not support `arm64-v8a`.

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

The installed package uses another signing certificate. Confirm that both APKs
are official L.L.E releases. Removing the incompatible copy is a last resort
because it deletes all app data:

```shell
adb uninstall com.codex.lle64
```

### `INSTALL_FAILED_VERSION_DOWNGRADE`

The installed version is newer. Install the latest official release instead of
forcing a downgrade.

### Device is `unauthorized`

Reconnect USB, unlock the phone, and accept the RSA fingerprint. If the prompt
does not reappear, revoke USB-debugging authorizations in Developer options and
reconnect.
