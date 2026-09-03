# L.L.E 1.0.6.3

## G2 Vector

- Added an app-owned port of G2 Vector with the recovered ring, bubble and ray
  behavior, original textures and original sounds. No donor APK is embedded.
- Uses the existing independent lockscreen and Last screen caches, with the
  Last screen visible inside the opening and during the unlock handoff.
- Added the approved LLE-style G2 Vector picker icon, including real transparent
  outer corners, at the same 512 x 512 size as the other effect icons.

## Themed launcher icon

- Added a monochrome adaptation of the current LLE logo for Android 13+ themed
  icons, including compatible Samsung One UI launchers.
- The normal rainbow icon, package identity and signing configuration are
  unchanged. The launcher supplies the palette; the user must enable themed
  icons / apply the color palette to app icons in their system settings.
- Reused only the adaptive-icon monochrome mechanism reviewed in PR #41, not
  its replacement artwork, font changes, legacy module rename or README edits.
- Android 8-12 retain the existing adaptive icon resources; earlier versions
  retain the existing raster icons.

## Possible Samsung Cocktail Bar fix - issue #35

- Added a targeted fix for a stale runtime block after a Samsung Cocktail Bar
  window disappears. The last matching window package is cleared only after
  a conclusive absent scan and the existing grace period.
- Active/focused blocked surfaces and inconclusive scans remain protected.
  This may resolve the effect interruption/disappearance associated with a
  stale Cocktail Bar block in [issue #35](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/issues/35).
- Host regression tests pass, but confirmation on the reporting Galaxy A51 is
  still pending. This is a candidate fix, not a confirmed resolution of every
  symptom in the issue or of Android's compatibility warning. The issue remains
  open for feedback.

## Build

- Version: `1.0.6.3` (`versionCode 45`).
- Stable ARM64 package: `com.codex.lle64`; signing identity is unchanged from
  1.0.6.2. Install over the existing stable app to preserve its data.
- Signed stable APK installed successfully and user-tested on Galaxy S23 Ultra.
- G2 Vector host tests: 522 assertions passed. Cocktail Bar host regression
  tests, APK signature/integrity checks and native-library checks passed.
- Download `LLE64-1.0.6.3-64-bit.apk`. ARM64 only; no ARM32 build is published.
- If the separate Tester app is installed, enable only one L.L.E accessibility
  service while testing to avoid overlapping effects.
- Companion is advertised only after the release and APK are publicly available.

SHA-256:

```text
b14c9f8c5918c0ee2b9451e58968407c96d3c536f23e4e277f0eb8a822a5b27c  LLE64-1.0.6.3-64-bit.apk
```
