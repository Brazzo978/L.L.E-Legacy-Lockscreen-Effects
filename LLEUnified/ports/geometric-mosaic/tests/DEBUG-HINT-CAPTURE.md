# Geometric Mosaic hint capture over ADB

This debug endpoint captures the Geometric Mosaic effect layer at a requested
time after the unlock hint starts. It is available in both LLE builds.

## Prerequisites

- The LLE accessibility service must be enabled and running.
- The master switch and unlock effects must be enabled.
- `Geometric Mosaic` must be selected and its overlay renderer must already be
  mounted/visible. Otherwise the request is logged and ignored.
- ARM64 capture uses `PixelCopy` and therefore requires Android 7.0 / API 24 or
  newer.
- Replace `<SERIAL>` below with the target shown by `adb devices`.

## ARM32

Request a frame 1000 ms after the hint starts:

```powershell
adb -s <SERIAL> shell am broadcast -f 0x10000000 -a com.codex.lle.DEBUG_CAPTURE_GEOMETRIC_HINT -p com.codex.lle --el phase_ms 1000
```

Pull the resulting PNG:

```powershell
adb -s <SERIAL> pull /sdcard/Android/data/com.codex.lle/files/debug-captures/geometric_hint_arm32_1000ms.png .
```

## ARM64 companion

Request the equivalent frame from the companion package:

```powershell
adb -s <SERIAL> shell am broadcast -f 0x10000000 -a com.codex.lle.DEBUG_CAPTURE_GEOMETRIC_HINT -p com.codex.lle.arm64dev --el phase_ms 1000
```

Pull the resulting PNG:

```powershell
adb -s <SERIAL> pull /sdcard/Android/data/com.codex.lle.arm64dev/files/debug-captures/geometric_hint_arm64_1000ms.png .
```

## Timing and output

`phase_ms` is the capture time relative to a newly triggered hint. Accepted
values are clamped to `0..2400`; when omitted, the default is `800`. The phase
is included in the output filename, so change `1000` consistently in the
broadcast and pull commands when testing another point in the animation.

The PNG contains the effect render layer, not a normal ADB screenshot. Areas
outside the effect may therefore appear black or transparent. Saving is
asynchronous; wait briefly after the broadcast before pulling the file. The app
log records whether the request was accepted, ignored, saved, or failed.

## Runtime cost and privacy

The endpoint does not poll and adds no delay or per-frame capture work during
normal use. Bitmap allocation, surface/texture readback, the writer thread, and
PNG compression occur only after the explicit debug broadcast.

Captured frames are stored in the app-specific external `debug-captures`
directory. They can contain screen-derived pixels, so treat them as potentially
sensitive, pull them only to a trusted workstation, and delete them after use.
