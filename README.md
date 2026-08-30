# L.L.E. — Legacy Lockscreen Effects

Bring classic Android lockscreen effects back to modern devices—without root.

**Current stable release:** `1.0.6.1` · **Recommended build:** ARM64

[Download the latest release](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/releases/latest)
· [Watch the effect showcase](https://youtu.be/RO4WV7Z48Sk)
· [Read the XDA thread](https://xdaforums.com/t/app-beta-no-root-l-l-e-legacy-samsung-lockscreen-effects.4794942/)
· [Telegram support group](https://t.me/legacylockscreeneffect)
· [L.L.E Companion](https://github.com/Brazzo978/LLE-Companion)

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

- **Recommended:** [install the APK manually](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/blob/feature/lle-1.0.5.6-effects/LLEUnified/docs/INSTALL_APK.md) —
  complete illustrated Samsung setup, Play Protect, Accessibility, and
  Restricted Settings flow.
- **Computer/advanced:** [install or update with ADB](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/blob/feature/lle-1.0.5.6-effects/LLEUnified/docs/INSTALL_ADB.md).
- **More details:** [complete setup and troubleshooting](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/blob/feature/lle-1.0.5.6-effects/LLEUnified/README.md).
- **Problems or questions:** [read the L.L.E. FAQ](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/blob/feature/lle-1.0.5.6-effects/LLEUnified/docs/FAQ.md).
- **XDA Thread:**[XDA](https://xdaforums.com/t/app-no-root-l-l-e-legacy-samsung-lockscreen-effects.4794942/)

The first-launch wizard configures Accessibility, battery optimization,
wallpaper dimming, lockscreen capture, enabled features, and the touch region.

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
[illustrated APK guide](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/blob/feature/lle-1.0.5.6-effects/LLEUnified/docs/INSTALL_APK.md) shows every screen.

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

- S3 None
- S3 Water Ripple
- S4 Lens Flare
- S5 Popping Colours, Stone Skipping, and Brilliant Ring
- N2 Ink in Water / Indigo
- N3 Watercolor and Ripple Ink
- N4 Abstract Tiles and Geometric Mosaic
- N5 Colored Droplet, Gyro Droplet, and Sparkling Bubbles
- Tab S Blind and Brilliant Cut
- Good Lock Popping Color, Rectangle Traveller, and Bouncing Color
- LG G1 White Hole and Dewdrop
- LG G2 Soda, Particle, Light Particle, Pixelate, and Crystal
- Sony Xperia Z1 Blinds and Revolving Glass
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
[PolyForm Noncommercial License 1.0.0](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/blob/feature/lle-1.0.5.6-effects/LICENSE.md).

Some builds contain legacy proprietary compatibility components extracted from
historical firmware. Rights in those components remain with their respective
owners; they are not relicensed by this project. L.L.E is not affiliated with
or endorsed by those owners.
