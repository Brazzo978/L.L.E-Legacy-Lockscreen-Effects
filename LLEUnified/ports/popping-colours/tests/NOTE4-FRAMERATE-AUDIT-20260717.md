# Popping Colours frame-rate audit — Note 4 vs S23

Date: 2026-07-17

## Question

Determine whether the slower Popping Colours motion observed on the original
SM-N910F is caused by insufficient Note 4 hardware performance, or whether the
modern implementation is advancing the legacy effect too quickly on a high-refresh
display.

## Devices and captures

- Original reference: SM-N910F, Android 5.0.1, 1440 x 2560, 60 Hz.
- Port test device: SM-S918B, Android 16, 1440 x 3088, active render rate 120 Hz.
- Captures are stored in `results/note4-vs-s23-20260717`.
- `popping-note4-native-tap.mp4`: original Note 4 reference.
- `popping-s23-arm64-tap.mp4`: ARM64 port before frame pacing.
- `popping-s23-arm64-tap-paced60.mp4`: ARM64 port after frame pacing.

The Note 4 capture contains 235 frames in 3.995 seconds (about 58.8 captured
frames/s). The pre-fix S23 capture contains 253 frames in 2.482 seconds (about
101.9 captured frames/s). The post-fix S23 capture was recorded while the display
reported an active 120 Hz render rate and contains 424 frames in 3.794 seconds.

## Code evidence

The legacy particle engine is frame-counted rather than time-counted:

1. `ParticleEffect` initializes `drawingDelayTime` to 2 ms.
2. `ParticleEffect$1.handleMessage()` calls `invalidate()` and posts its next
   message after `drawingDelayTime`.
3. `ParticleEffect.onDraw()` calls `move()` and `draw()` for every particle.
4. `Particle.move()` adds fixed `dx` and `dy` values once per draw; it receives no
   elapsed-time or delta-time value.
5. `Particle.draw()` decrements the integer particle life once per draw and derives
   alpha from frame-counted life thresholds.

The 2 ms Handler interval is therefore not a 500 Hz physics target. It keeps the
view continuously invalidated so the old 60 Hz display determines the effective
physics rate. On a 120 Hz display, the same code can execute nearly twice as many
motion/life/alpha steps per second. A separate 700 ms screen-on animator does not
correct the particle simulation because the core particle motion and lifetime are
still advanced in `onDraw()`.

## Conclusion

The difference is not a Note 4 GPU limitation. The original code assumes a 60 Hz
display and accidentally binds its particle physics to the display frame rate.
Running the unpaced loop on the S23 at 120 Hz makes Popping Colours too fast.

## Applied correction

`vendor/secvisualeffect/patch-note5-lifecycle.ps1` changes the particle redraw
request interval from 2 ms to 16 ms in the dex staged for both APKs. This keeps the
particle simulation near 60 steps/s on 60, 120 and 144 Hz displays without changing
the physical display refresh rate. The patch script also disassembles the generated
dex again and fails the build unless the 16 ms value is present exactly once.

Verified generated dex SHA-256:

`34F0B18B323178760324BF65323F3BE3C262F49162D7E601973B7FC72A4F7046`

## Verification

- ARM32 APK built successfully and passed APK signature verification.
- ARM64 companion APK built successfully and passed APK signature verification.
- ARM64 companion installed on the SM-S918B with all pre-existing accessibility
  services preserved.
- The corrected effect rendered and completed on the 120 Hz display with no fatal
  exception in the test log.
- Final visual validation by the project owner: corrected.
