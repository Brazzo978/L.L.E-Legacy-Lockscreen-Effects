# L.L.E. — Legacy Lockscreen Effects

Bring classic Android lockscreen effects back to modern devices—without root.

**Current stable release:** `1.0.5.3` · **Recommended build:** ARM64

[Download the latest release](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/releases/latest)
· [Watch the effect showcase](https://youtu.be/RO4WV7Z48Sk)
· [Read the XDA thread](https://xdaforums.com/t/app-no-root-l-l-e-legacy-samsung-lockscreen-effects.4794942/)
· [Join the Telegram support group](https://t.me/legacylockscreeneffect)
· [Join the L.L.E Companion closed test](https://play.google.com/apps/testing/com.codex.lle.companion)

> **Open source and local by design.** L.L.E's source code is published so its
> permission and data handling can be inspected. The app does not request the
> Android `INTERNET` permission: lockscreen captures, imported wallpapers,
> settings, effect caches, and debug reports remain on the device. L.L.E does
> not automatically upload them. Data leaves the phone only when you explicitly
> export or share a file yourself.

Source availability makes the official project auditable, but it cannot prove
that an APK from an unrelated mirror was built from this source. Download only
from the official GitHub release and verify the supplied SHA-256 checksum.

## Start here

Choose the installation method:

- **Recommended:** [install the APK manually](LLEUnified/docs/INSTALL_APK.md) —
  complete illustrated Samsung setup, Play Protect, Accessibility, and
  Restricted Settings flow.
- **Computer/advanced:** [install or update with ADB](LLEUnified/docs/INSTALL_ADB.md).
- **More details:** [complete setup and troubleshooting](LLEUnified/README.md).
- **Problems or questions:** [read the L.L.E. FAQ](LLEUnified/docs/FAQ.md).
- **XDA thread:** [project discussion and support](https://xdaforums.com/t/app-no-root-l-l-e-legacy-samsung-lockscreen-effects.4794942/).

The first-launch wizard configures Accessibility, battery optimization,
wallpaper dimming, lockscreen capture, enabled features, and the touch region.

## Maintenance update 1.0.5.3

The releases after `1.0.5` focus on bug fixes, compatibility, recovery, and
closer stock fidelity rather than adding new effect engines. The current stable
release includes:

- more accurate movement and sound cadence for S3 Water Ripple and Popping Colours;
- restored lock sounds for Ink in Water, Stone Skipping, Blind, and Brilliant Cut;
- corrected Mass Tension behaviour and audio based on the Galaxy Trend 2 implementation;
- fixes for held Geometric Mosaic gestures and Seasonal touch-sprite drift/flicker;
- adaptive Lens Flare visibility on very bright lockscreen backgrounds;
- a safe 120-second cold-boot recovery window with an explicit advanced bypass;
- Samsung Notes and Screen Off Memo protection through the runtime blacklist.

See the [complete 1.0.5.3 release notes](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/releases/tag/v1.0.5.3)
for validation details and the official APK checksum.

## Stay updated with L.L.E Companion

[L.L.E Companion](https://github.com/Brazzo978/LLE-Companion) is an optional
companion app that checks the installed public L.L.E version and helps users
reach new official GitHub releases. Tester builds are intentionally ignored.
An optional battery-aware background check can notify users approximately once
a day. Android still keeps the user in control: Companion never downloads or
installs an APK and opens the official GitHub release only after confirmation.

The first Google Play release is currently in closed testing. We need at least
12 genuine testers who can install the app, try its version check and guided
download/setup tutorials, report problems, and remain opted in for at least
14 consecutive days.

1. [Join the L.L.E Companion Google Group](https://groups.google.com/g/lle-companion-testers).
2. [Opt in to the Google Play closed test](https://play.google.com/apps/testing/com.codex.lle.companion).
3. [Install L.L.E Companion from Google Play](https://play.google.com/store/apps/details?id=com.codex.lle.companion).
4. [Report a problem or suggestion](https://github.com/Brazzo978/LLE-Companion/issues).

Use the same Google account for the Group, the opt-in page, and the Play Store
on the Android device. Testers must explicitly opt in; joining the Group alone
does not count toward the closed-test requirement.

### Samsung Restricted Settings

On recent Samsung firmware, a sideloaded Accessibility app may be blocked the
first time:

1. In the L.L.E wizard, open Accessibility.
2. Open **Installed apps → L.L.E 64** and try to enable the service once.
3. If Samsung blocks it, return to L.L.E.
4. Follow **Open App info → ⋮ → Allow restricted settings**.
5. Return to Accessibility and enable L.L.E 64.

The **Allow restricted settings** menu item may not appear until after the first
blocked activation attempt. The
[illustrated APK guide](LLEUnified/docs/INSTALL_APK.md) shows every screen.

## Current builds

| Build | ABI | Status |
|---|---|---|
| **L.L.E 64** | `arm64-v8a` | Recommended and actively developed |
| **L.L.E** | `armeabi-v7a` | Historical continuity; critical fixes only |

New features and effects target ARM64. The 32-bit edition remains available for
older compatible devices but is no longer developed in parallel.

## Compatibility

This table is based on completed tests and user debug reports. “Working” means
the service and effects have been reported operational; it is not a guarantee
for every device, GPU, lockscreen theme, or vendor update.

| Manufacturer | Tested software/device | Android | Result | Notes |
|---|---|:---:|---|---|
| Samsung | Galaxy phones and foldables, **One UI 6–9** | Varies | **Working** | Fold devices use separate Cover/Main setup; wallpaper layers can vary |
| Xiaomi / POCO | **POCO X8 Pro Global** (`2511FPC34G`, `klee`) on HyperOS | 16 | **Working** | Confirmed by an ARM64 L.L.E 1.0.4.2 debug report |
| LG | **LG Velvet** (`LM-G910`, `caymanslm`) | 11 | **Working** | Effects and lock/unlock runtime confirmed; 1.0.4.4 improves intermittent Colored Droplet audio and Android 11 diagnostics |

To add a device to this table, use **Create debug report** in L.L.E and include
whether the effect, screenshot capture, doodle, sound, and lock/unlock cycle
worked. Reports redact saved wallpaper paths and remain local until shared by
the user.

## Included effects

- S3 Water Ripple
- S4 Lens Flare
- S5 Popping Colours, Stone Skipping, and Brilliant Ring
- N2 Ink in Water / Indigo
- N3 Watercolor
- N4 Abstract Tiles and Geometric Mosaic
- N5 Colored Droplet, Gyro Droplet, and Sparkling Bubbles
- Tab S Blind and Brilliant Cut
- Seasonal Spring, Summer, Autumn, and Winter effects
- Charging doodles and seasonal companion effects


## Requirements

- Android 6.0 or newer.
- An ARM64 device for the current recommended build.
- Accessibility permission.
- Unrestricted battery use is strongly recommended.
- A lockscreen capture or user-provided wallpaper and a configured touch box.
- No root access is required.

## Fold support

Fold mode stores separate Cover and Main configurations:

- lockscreen background;
- touch box;
- effect enable/disable state;
- doodle enable/disable state.

Complete each part of the dual-panel wizard while the requested display is
active. When Samsung does not expose a panel-specific wallpaper layer, use
automatic capture or provide the Cover/Main images manually.

## Privacy and permissions

L.L.E has no `INTERNET` permission. Its main permissions are used for:

- **Accessibility:** detect lockscreen state and display the selected effect;
- **Battery optimization exemption:** keep the renderer ready between
  lock/unlock cycles;
- **Wallpaper access:** optional local import or setting of a user-selected
  wallpaper;
- **Wake lock:** keep short effect and capture operations reliable.

The debug-report button creates a local text file for troubleshooting. It is not
sent anywhere automatically.

## Known limitations

- Vendor lockscreen behavior can change after a system update.
- Protected or layered wallpapers may require manual image selection.
- Samsung nighttime wallpaper dimming can make captured and displayed
  brightness differ; disabling that option is strongly recommended.
- Fold wallpaper APIs do not expose every Cover/Main layer to third-party apps.
- Beta effects can show small timing or rendering differences across GPUs.


<p align="center"><sub><a href="https://support.legacylockscreeneffects.app">L.L.E will always be free - but if you insist... ♡</a></sub></p>

## License

Project-authored source code is available under the
[PolyForm Noncommercial License 1.0.0](LICENSE.md).

Some builds contain legacy proprietary compatibility components extracted from
historical firmware. Rights in those components remain with their respective
owners; they are not relicensed by this project. L.L.E is not affiliated with
or endorsed by those owners.
