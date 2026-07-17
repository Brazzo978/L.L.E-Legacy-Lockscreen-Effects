# LLE / LLE64 1.0.2 Beta 1

## Permanent application identities

- ARM32: launcher `LLE`, application ID `com.codex.lle`.
- ARM64: launcher `LLE64`, application ID `com.codex.lle64`.
- Both APKs can remain installed and have isolated settings/caches.
- The former development ID `com.codex.lle.arm64dev` is retired.

The internal Java/JNI namespace remains `com.codex.lle` for binary
compatibility; only the Android application/resource identity differs.

## Fixes and effect updates

- Fixed ARM64 Watercolor in the co-installable build: its DEX now loads masks,
  brush and noise from `com.codex.lle64` instead of the ARM32 package.
- Added deterministic DEX hash verification for both resource package variants.
- Abstract Tiles ARM64 now follows the Note 4 touch/unlock visibility lifecycle,
  keeps the visible Line geometry static and uses the original two-pass grid
  vertex order so Line indices remain symmetric with the tiles.
- Abstract Tiles retains selectable Lines and No-lines variants.
- Geometric Mosaic ARM64 remains available as a Beta reconstruction.
- Existing Fold display routing, per-panel screenshot caches and touch boxes are
  retained.

## Install

Release artifacts (SHA-256):

- `LLE-1.0.2-Beta-1-32-bit.apk` — `4347C5A7C3DEE08DC191FEC4ACB87936EBCE36F3AE51BDD354722BBF30208A9A`
- `LLE64-1.0.2-Beta-1-64-bit.apk` — `A44EA32EEE0D83A8A8255F43D9F56604A02C5AA8EA0FC05C24E9210FAA1F5C4C`

```shell
adb install --no-incremental -r "LLE-1.0.2-Beta-1-32-bit.apk"
adb install --no-incremental -r "LLE64-1.0.2-Beta-1-64-bit.apk"
```

Because LLE64 has a new permanent application ID, Android treats it as a new
application. Enable its Accessibility service and create/recapture its settings
and screenshot cache before retiring the old development package.

## Compatibility note

Some builds contain legacy third-party compatibility components. Rights remain
with their respective owners; this project does not claim ownership or
affiliation.
