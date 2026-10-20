# App appearance

Open **ADV → Appearance** and use the compact **Auto / moon / sun** selector.
Auto is the default and follows Android's night configuration. The moon forces
Dark and the sun forces Light. Accessible labels and long-press tooltips identify
the icon controls in English. Explicit Light
and Dark choices override the device theme for L.L.E's settings screens.
The selection persists under the additive `ui_theme_mode` key in the existing
preferences; unknown or malformed values safely fall back to System.

The shared palette covers the main settings, effect picker, Doodle settings,
Effects Workshop, setup wizard, app picker, touch area editor and wallpaper
editor controls. Dark surfaces use slate and charcoal with aqua accents.
Framework dialogs inherit the selected light/dark theme. System bar icons follow
the app appearance where visible. Automatic Force Dark is disabled so Android
does not recolor an already themed screen or its preview artwork.

Changing appearance recreates the settings activity with the selected tab,
effect filter, expanded Advanced section and main scroll position restored. The Workshop preserves
its unsaved raw values, enabled state and scroll position across activity
recreation. Its snapshot/restore path never applies changes. Apply persists the
draft and Cancel discards it as before. Open help popups and keyboard cursor
position are not restored.

The touch area editor preserves unsaved rectangles and selected rectangle across
recreation. A draft must still match its display profile, source dimensions and
cached screenshot path/modification timestamp/size. It observes an existing
capture request instead of creating another request during recreation.

Appearance is UI-only. The accessibility service ignores its preference key;
switching themes does not replace a live effect renderer or modify its colors,
wallpaper, Random pool, physics or Workshop settings. Screenshot calibration
artwork, effect thumbnails and wallpaper pixels retain their source colors.

## Verification

Tester 1.0.7RC2 (versionCode 54) adds a 440 ms circular theme reveal originating
at the selected appearance row. It briefly draws the previous app UI over the
new theme, using at most one million ARGB pixels (4 MB). The snapshot travels
through Android's non-configuration instance mechanism, never a static Activity
or View reference. It is released after completion or when the screen stops.
Snapshots older than five seconds or with different layout dimensions are
discarded. Allocation failure falls back to an immediate theme change. Android's
disabled animator setting skips capture and animation entirely. Existing native
entrance, tab and expanding-section motion remains available; activity recreation
does not replay the entrance animation over a restored page.

Build the ARM64 application with `build.ps1` and the separately packaged Tester
with `build-arm64.ps1 -Tester`. Tester remains `com.codex.lle64.test` and uses the
existing tester version/signing configuration. Stable 1.0.7.0 (versionCode 55)
promotes this UI to `com.codex.lle64` with the established stable signing lineage.
The corresponding stable release is advertised to Companion only after the
public release and APK are available.

`AppearanceContrastTest` checks actual palette color contrast for dark primary,
secondary and accent text on the shared surfaces, primary action contrast, mode
override precedence, and fallback for invalid saved values. Host regressions
also cover `WorkshopSliderScaleTest` and `AppPickerModelTest`.

On-device checks should include switching Auto/moon/sun, persistence after
reopening, selected tab and filter retention, Workshop editing/help/reset/Cancel,
night configuration changes while a Workshop draft is open, and touch area
draft restoration. Record which flows were exercised; a successful APK build or
host test alone does not confirm visual or Android lifecycle behavior.
