# Abstract Tiles Note 4 acquisition and BOB4 oracle audit

Date: 2026-07-17

This directory preserves the direct stock-device evidence used for the final
Abstract Tiles fidelity pass. The physical Note 4 and the byte-exact BOB4
native library are the primary references. LLE ARM32 is not a visual oracle,
because its transparent-host patch intentionally changes native compositing.

## Reference device

- Device: Samsung SM-N910F (`trlte`), ADB serial `44d857ce`
- Firmware fingerprint:
  `samsung/trltexx/trlte:5.0.1/LRX22C/N910FXXU1BOB4:user/release-keys`
- Display: 1440 x 2560, nominal refresh period 16,666,667 ns (60 Hz)
- Selected stock effect: `settings system lockscreen_ripple_effect = 11`
- Selection helper: LSE 1.0. Its role is limited to writing the system setting;
  rendering is performed by the stock SystemUI/secvisualeffect stack.
- Root: Magisk shell was available for read-only extraction and inspection. No
  lock credential, display mode, resolution, or refresh-rate setting was changed.

## Preserved artifacts

- `secvisualeffect.jar`, `secvisualeffect.odex`, and
  `secvisualeffect-res.apk`: files pulled from the running Note 4.
- `lse-base.apk` and `lse-unpacked/`: selector APK and its decoded setting map.
- `captures/abstract-tiles-note4-stock-unlock-clean.mp4`: clean stock unlock run.
- `captures/abstract-tiles-note4-stock-unlock-clean-sheet.png`: broad timeline.
- `captures/abstract-tiles-note4-stock-unlock-clean-motion-sheet.png`: frames
  selected around visible motion.
- `captures/abstract-tiles-note4-stock-lines-window.png`: 20 fps resampling of
  the last 800 ms around the visible stock Line/slab window, annotated in
  50 ms increments. This is a presentation-sample aid, not an engine-cadence
  measurement.
- `captures/abstract-tiles-note4-stock-unlock.mp4`: rejected first run containing
  a pending Magisk prompt; retained only to explain the rejected evidence.

The clean run used a 900 ms ADB swipe from `(720,1620)` to `(1060,900)` after a
controlled wake. Its encoded stream contains 105 frames over 4.437578 seconds.
The approximately 23.7 encoded frames/s are not the engine cadence: Android 5
screen recording itself loaded the device and dropped presentation samples.

A separate laboratory-host attempt was rejected because the installed build
was actually displaying Lens Flare. Its files are isolated under
`rejected-nonstock-host/`, explicitly marked invalid, and are not used by this
audit. The temporary package was removed from the Note 4; the stock selection
remained `11`.

## Cadence measured without screen recording

The StatusBar buffer timestamps were collected directly from SurfaceFlinger:

```text
dumpsys SurfaceFlinger --latency-clear
input swipe 720 1620 1060 900 900
dumpsys SurfaceFlinger --latency StatusBar
```

Results during the Abstract Tiles gesture/unlock window:

- reported display period: 16,666,667 ns;
- 127 valid presentation timestamps in the SurfaceFlinger ring;
- median valid interval: 16.90 ms;
- most intervals: 16.7-17.0 ms;
- longer gaps occurred at lifecycle transitions after dismissal.

Conclusion: the stock Note 4 presents the active effect at approximately 60 Hz.
The clean video must not be used to infer a 24/30/40 Hz design cadence. The
ARM64 port's roughly 60 requests/s with elapsed-time physics is the correct
policy for both 60 and 120 Hz panels. LLE ARM32's older 33.333 ms limiter is a
host adaptation and not a stock timing reference.

## Byte-exact native oracle

The local BOB4 firmware copy and LLE's preserved native oracle are identical:

```text
SHA-256 F8E8BDF48D069F76AF9923D68474A7047C621DD763D3E6D96C4F940025643840
size    115,932 bytes
```

Compared files:

- `unlock-effects-test/extracted/note4_bob4_full/system/lib/libsecveAbstractTile.so`
- `LLEUnified/vendor/original-native/libsecveAbstractTile.so`

The BOB4 file was imported through Ghidra MCP as
`/note4_bob4_exact/libsecveAbstractTile.so`. This is the program that must be
used for future decompilation. The older Ghidra program
`/note4/libsecveAbstractTile.so` points at a patched test-host copy and is not
the byte-exact stock binary.

## Ghidra MCP confirmations

The exact BOB4 program independently reconfirmed the existing reverse:

- `0x2169C`, unlock wrapper:
  `FUN_00020BD4(scene, 1.0, 2.0, 0, 0.8, 20.0, 0.9)`;
- target Line scalar: `scene + 0x5EC`, reset to zero at unlock;
- Line animator: `0.0 -> 1.0`, from `mNow` to `mNow + 0.4` seconds;
- `0x13F64`: cosine interpolation using pi = `3.1415927`;
- `0x13B10`: Line geometry update reads the clamped scalar at `scene + 0x5EC`;
- `0x23214`: frame order is Background, Tile, Line, then Scatter;
- exact draw sequence at `0x2340C` onward:
  `Background::renderFrame`, object `+0x53C`, object `+0x540`, then object
  `+0x538` after the additive blend switch.

The current ARM64 core therefore has the correct 400 ms duration, cosine
direction, geometry update, and render-pass order. Remaining visual error must
be investigated in host compositing, screenshot mapping, lifecycle timing, or
capture alignment before changing binary-derived geometry/tables.

## Why LLE ARM32 has no authentic Lines

The transparent ARM32 build starts from the exact BOB4 library, then applies
`vendor/native-patches/patch-abstract-tile-transparent.ps1`. The current staged
output has SHA-256
`B33F8421CC4FF0B442761AAA8E92EE0471BFF26AA9ED0D42A201403151185C4B`
and differs from stock in 176 bytes across 10 ranges.

Three changes are decisive:

1. File offset `0x13410`, virtual address `0x23410`:
   stock `F1 B8 FF EB` (`BL Background::renderFrame`) becomes
   `00 00 A0 E1` (`MOV r0,r0`, a NOP).
2. A stock additive shader literal changes final alpha from `1.0` to `0.0`.
3. The stock Line fragment test
   `if (line_mask.a == 0.0) discard;` becomes
   `if (line_mask.a != 0.0) discard;`.

The third change deliberately discards the visible Line-mask fragments because
the original Line pass writes absolute wallpaper RGB and depended on the now
omitted opaque Background. It explains the missing Lines in LLE ARM32. It is
not evidence that the stock effect lacks Lines.

The ARM64 reconstruction uses a different solution: it preserves the original
`== 0.0` mask semantics, samples the cached background only inside the Line
fragments, and outputs those fragments through the transparent overlay. This is
why ARM64 Lines can be faithful without restoring the opaque fullscreen pass.

## What the stock capture can and cannot prove

The Note 4 video is valid evidence for tile scale, brightness, scatter,
touch-relative placement, and the beginning of striped/seam motion. Subtle
striped quadrilaterals are visible while the gesture is active.

It is not a complete observation of the 400 ms Line unlock track. Stock
SystemUI dismisses/fades the effect surface almost immediately when the home
screen appears, so the latter part of the native track is occluded by host
lifecycle. The complete Line trajectory must therefore be checked against the
exact BOB4 code/tables and a controlled renderer capture, not inferred from
missing post-dismiss video frames.
