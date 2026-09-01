# Install L.L.E from the APK

This is the tested first-start flow for installing the official ARM64 release
from GitHub without a computer.

> **Draft screenshot set:** the captures below use the co-installable
> **L.L.E Tester** build. Before publishing the final guide, replace them with
> captures of the signed **L.L.E** release. The navigation and prompts are
> otherwise the same.

## Before you start

Download the APK and `SHA256SUMS.txt` only from the
[official L.L.E releases page](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/releases/latest).
Compare the APK's SHA-256 value with the value in the same release. Do not
install a copy received through a chat, mirror, or unofficial download page.

L.L.E uses Android Accessibility to detect the lockscreen and draw the selected
effect. Android therefore applies extra safeguards to manually installed copies.
Keep Play Protect enabled and approve the restricted setting only if you trust
the downloaded file.

## 1. Install and scan the APK

Open the verified APK from **My Files**. If Android asks, temporarily enable
**Allow from this source** for My Files and return to the installer.

Let Play Protect scan the file.

<img src="images/install-s23/01-play-protect-scan.jpg" width="320" alt="Play Protect scan prompt">

Wait until the scan finishes.

<img src="images/install-s23/02-play-protect-scanning.jpg" width="320" alt="Play Protect scanning the APK">

A manually installed Accessibility app may produce a strong warning. Only use
**Install anyway** when the file came from the official release and its checksum
matches. Otherwise cancel the installation.

<img src="images/install-s23/03-play-protect-warning.jpg" width="320" alt="Play Protect installation warning">

Do not disable Play Protect.

## 2. Make the first Accessibility attempt

Open L.L.E and start the wizard. On the Accessibility step, tap
**Open Accessibility settings**.

<img src="images/install-s23/04-accessibility-settings.jpg" width="320" alt="L.L.E Accessibility wizard step">

Samsung does not allow L.L.E to deep-link directly to its service page. In
Android Settings, open:

```text
Accessibility → Installed apps → L.L.E
```

On Android 13 and newer, the first attempt may say that the setting is
restricted.

<img src="images/install-s23/05-accessibility-restricted.jpg" width="320" alt="Restricted Accessibility setting">

Try to enable the service once. If Android denies the action, close the message
and return to L.L.E.

<img src="images/install-s23/06-accessibility-denied.jpg" width="320" alt="Accessibility action denied">

This first blocked attempt is important: on the tested Samsung flow it makes
the **Allow restricted settings** action available on the app-info page.

## 3. Allow restricted settings

L.L.E now displays the recovery page. Tap **Open L.L.E App info**.

<img src="images/install-s23/07-lle-recovery.jpg" width="320" alt="L.L.E restricted-settings recovery page">

On the application-info page, open the three-dot menu in the upper-right corner.

<img src="images/install-s23/08-app-info.jpg" width="320" alt="L.L.E application-info page">

Tap **Allow restricted settings** and confirm the system prompt.

<img src="images/install-s23/09-allow-restricted-settings.jpg" width="320" alt="Allow restricted settings menu action">

If this menu action is not present, return to step 2 and make the blocked
Accessibility attempt first.

## 4. Enable L.L.E Accessibility

Return to L.L.E and tap **Open Accessibility settings** again.

<img src="images/install-s23/10-accessibility-retry.jpg" width="320" alt="Retry Accessibility from the L.L.E wizard">

Open **Installed apps**.

<img src="images/install-s23/11-installed-services.jpg" width="320" alt="Installed Accessibility services">

Select **L.L.E** and turn the service on.

<img src="images/install-s23/12-lle-service-toggle.jpg" width="320" alt="L.L.E Accessibility service toggle">

Read Android's Accessibility disclosure, then tap **Allow** if you agree.

<img src="images/install-s23/13-accessibility-confirm.jpg" width="320" alt="Android Accessibility confirmation">

Only one L.L.E Accessibility service should be enabled if both the frozen
historical 32-bit app and the current 64-bit app are installed.

## 5. Complete the wizard

Continue to battery optimization. Unrestricted battery use is strongly
recommended because stopping the service can prevent effects from appearing.

<img src="images/install-s23/14-battery-step.jpg" width="320" alt="L.L.E battery optimization step">

Confirm unrestricted battery use in the Samsung prompt.

<img src="images/install-s23/15-battery-confirm.jpg" width="320" alt="Samsung unrestricted battery prompt">

Follow the Samsung wallpaper compatibility recommendation. Disable both
wallpaper dimming and Dynamic Lock Screen when L.L.E flags them. Dimming can
make the lockscreen darker than L.L.E's captured layer, while Dynamic Lock
Screen can replace the image after each lock before L.L.E can reliably capture
it for that same unlock.

<img src="images/install-s23/16-wallpaper-dimming.jpg" width="320" alt="Samsung wallpaper compatibility recommendation">

Choose the lockscreen background source. Automatic screenshot capture is the
normal option; direct wallpaper modes remain available for compatible setups.

<img src="images/install-s23/17-wallpaper-source.jpg" width="320" alt="L.L.E wallpaper source selection">

Choose which L.L.E features to enable.

<img src="images/install-s23/18-feature-selection.jpg" width="320" alt="L.L.E feature selection">

When screenshot mode is selected, lock the phone, wait for the lockscreen to
settle, then unlock it so L.L.E can capture a clean background.

<img src="images/install-s23/19-lockscreen-capture.jpg" width="320" alt="Lockscreen capture instructions">

The wizard then offers touch-box calibration.

<img src="images/install-s23/20-touch-box-step.jpg" width="320" alt="Touch-box wizard step">

Move and resize the box if required, then save it.

<img src="images/install-s23/21-touch-box-editor.jpg" width="320" alt="Touch-box editor">

Finish the wizard.

<img src="images/install-s23/22-setup-complete.jpg" width="320" alt="L.L.E setup complete">

The main screen is now ready.

<img src="images/install-s23/23-main-screen.jpg" width="320" alt="L.L.E main screen">

## Updating an existing installation

Install the newer official APK over the existing copy. Do not uninstall first:
an in-place update preserves preferences, wallpaper caches, and touch-box
calibration.

Android refuses an update when the package name or signing identity does not
match. An unofficial or tester build cannot update the official application.

## Troubleshooting

### Accessibility is greyed out or controlled by Restricted Setting

Follow the tested two-pass sequence in steps 2–4. The restricted-settings menu
may not appear until after the first blocked Accessibility attempt.

### The installer reports a signature conflict

Confirm that both the installed copy and update are official L.L.E releases.
Uninstalling resolves a genuine conflict, but also deletes the app's settings
and local caches. Use it only as a last resort.

### `INSTALL_FAILED_NO_MATCHING_ABIS`

The current release requires `arm64-v8a`. Very old 32-bit-only devices need the
frozen historical ARM32 build, which is maintained only for critical fixes.

### Samsung Auto Blocker stops the installation

Auto Blocker is separate from Play Protect. After checking the APK and checksum,
temporarily turn it off under **Settings → Security and privacy → Auto Blocker**,
install L.L.E, then turn Auto Blocker back on.
