# L.L.E / L.L.E 64 1.0.2 Beta 2

## Visible application names

- ARM32 launcher: `L.L.E`, application ID `com.codex.lle`.
- ARM64 launcher: `L.L.E 64`, application ID `com.codex.lle64`.
- The Java/JNI namespace remains `com.codex.lle` in both builds.

## Fixes

- Fixed the first-run screenshot-cache bootstrap for screenshot-backed effects.
  A renderer with no cache is no longer pre-armed while the screen is off,
  because its attached overlay would prevent the first clean lockscreen capture.
- Added automatic first-run capture retries while a required background is
  missing.
- Confirmed Watercolor ARM64 on the lockscreen with all five assets loaded,
  `assetsReady=1`, `bgReady=1`, touch DOWN/UP delivery and approximately 58 FPS
  on the test device.
- Retains the Watercolor resource-package correction introduced in Beta 1.

## Install

Release artifacts (SHA-256):

- `LLE-1.0.2-Beta-2-32-bit.apk` — `058855A5915D73F89CAE6F15C2313CDA5CC876C14F411DCEC32E1A7AEEC93D98`
- `LLE64-1.0.2-Beta-2-64-bit.apk` — `CB713BF994DA8C23E0897492172F14BC6E752B8E1803881F9B65187399B298BE`

```shell
adb install --no-incremental -r "LLE-1.0.2-Beta-2-32-bit.apk"
adb install --no-incremental -r "LLE64-1.0.2-Beta-2-64-bit.apk"
```

Only one L.L.E accessibility service should be active at a time.

## Compatibility note

Some builds contain legacy third-party compatibility components. Rights remain
with their respective owners; this project does not claim ownership or
affiliation.
