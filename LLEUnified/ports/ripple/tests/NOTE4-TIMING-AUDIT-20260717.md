# S3 Water Ripple timing audit — Note 4 reference

Date: 2026-07-17

## Reference and test target

- Native reference: Samsung SM-N910F, Android 5.0.1, 1440x2560, fixed 60 Hz.
- Port target: Samsung SM-S918B, Android 16, 1440x3088, active 120 Hz mode.
- LLE target: ARM64 companion package `com.codex.lle.arm64dev`, effect type 10.
- Input: one ADB-injected tap at the normalized display center after a controlled
  sleep/wake cycle and one second of recording preroll.
- Analysis: presentation timestamps from `ffprobe`; grayscale radial difference
  profile around the touch center after subtraction of a stable preroll frame.

Raw videos, contact sheets, extracted analysis frames, and enhanced differences
are retained under `results/note4-vs-s23-20260717/`.

## Measured propagation

Times are relative to the first detected water front. Radius is normalized to
half of the 1440-pixel display width.

| Normalized radius | Note 4 observed | ARM64 at intended 60 steps/s | Experimental ARM64 at 40 steps/s |
|---|---:|---:|---:|
| 0.25 | 0.2374 s | 0.0999 s | 0.1475 s |
| 0.50 | 0.3764 s | 0.2502 s | 0.3721 s |
| 0.75 | 0.5401 s | 0.3999 s | 0.5977 s |

The 60-step port reached the half-width marker about 1.50 times faster than the
specific Note 4 capture. The temporary 40-step experiment differed by about
1.1% at that marker. This does not establish that 40 Hz was Samsung's intended
timing: the original renderer calls `move()` once per rendered frame without a
delta-time argument, so the Note 4 result can reflect the effective frame rate
of its old GPU at 1440p.

## Implementation

The shipping implementation keeps the intended 60 solver steps per real
second. Rendering remains free to run at the display cadence. Therefore
switching the panel between 60, 120, or 144 Hz changes presentation smoothness
but not physical propagation time.

The 40-step build was an empirical experiment only and was reverted. A future
capture from an original Galaxy S3 would be the correct evidence for changing
the canonical timing.

The following original simulation parameters were deliberately preserved:

- reduction rate: `0.94`
- wave coefficient: `0.5`
- draw-before-move ordering
- fixed-step accumulator with a bounded four-step catch-up

## Validation

- ARM32 unified build: passed and APK Signature Scheme v2 verified.
- ARM64 companion build: passed and APK Signature Scheme v2 verified.
- The experimental 40-step APK was installed and measured on the SM-S918B,
  then reverted to the intended 60-step implementation.
- Accessibility service list was captured before installation and restored
  exactly afterward.
