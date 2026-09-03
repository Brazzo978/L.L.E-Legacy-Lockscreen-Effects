# LLE themed launcher icon - 1.0.6.3

## Scope and source

The mechanism was reviewed directly in [PR #41](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/pull/41),
head `8f54c4fe144c7e636992dba29e63f7cb9196998f`: add a `monochrome` layer
to both `ic_launcher` and `ic_launcher_round` adaptive icons.

This integration does not import that PR's alternate colored artwork, legacy
module changes, fonts or README edits. The monochrome vector traces the current
LLE lettering, central sparkle, orbit, particles and water rings. It intentionally
omits the rainbow tile and its white frame, which would become an opaque block
when treated as an alpha mask.

- `res/drawable/ic_lle_monochrome.xml`: white-on-transparent vector, 108dp.
- `res/mipmap-anydpi-v33/`: normal and round adaptive definitions with monochrome.
- Existing `mipmap-anydpi-v26`, raster mipmaps, colored foreground/background,
  manifest icon references, package IDs and signing configuration are unchanged.
- `tools/preview-themed-icon.cjs` renders illustrative palettes from the Android
  vector using Sharp. It is not a runtime dependency or a forced app palette.

The [Android adaptive-icon documentation](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
describes Android 13+ monochrome support and launcher-controlled tinting. The
launcher must support themed icons and the user must enable them. On supported
Samsung launchers, use Wallpaper and style > Color palette > Apply palette to
app icons ([Samsung instructions](https://www.samsung.com/us/support/answer/ANS10001905/)).

## Verification on 2026-09-03

- ARM64 tester built successfully; package `com.codex.lle64.test`, version
  `1.0.6.3`, code `45`; only `arm64-v8a` native entries.
- Signature verified and in-place installation on S23 Ultra succeeded without
  clearing app data or changing accessibility settings.
- A standalone adb-shell resource probe loaded both installed mipmap resources
  on the S23 (API 36). Each was an `AdaptiveIconDrawable`, and each returned a
  non-null `VectorDrawable` from `getMonochrome()`.
- Normal and tinted icons were rendered with Android's actual drawable code and
  visually inspected. Example files are under the ignored directory
  `build/icon-generation/material-you/` (`s23-normal-render.png`,
  `s23-themed-render.png`). The probe uses an illustrative tint; it does not
  enable Samsung's palette or validate the user's final launcher color choice.
- Existing colored launcher resources and `LLE_VERSION.txt` have no source diff.
  Older-Android fallback is preserved structurally, not newly device-tested.
- Vector host tests pass (522 assertions); runtime-surface regression tests pass.

Tester artifact: `build/beta/LLE64-1.0.6.3-Vector-MaterialYou-tester.apk`.
SHA-256: `69dd747b3042ae278c4c6766d82c70195d21f980ed3f333e4cf1e143b5036c1a`.
This is not a published stable release; Companion remains on the current public
version until stable signing and publication.
