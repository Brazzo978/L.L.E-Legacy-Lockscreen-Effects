# L.L.E 1.0.6 TODO

Updated: 2026-08-28

## Firmware acquisition

- [ ] LG Optimus G E975 stock `V10`, Android 4.1.x Jelly Bean.
- [ ] LG G2 D802 stock `V10`, Android 4.2.2 Jelly Bean.
- [ ] Extract SystemUI/keyguard, LG framework jars, resources and relevant EGL
  libraries. Firmware is research material only; do not flash a device and do
  not ship proprietary LG/XLocker binaries or assets.

## Effect port priority

1. [x] **G1 White Hole** — restored and device-tested.
2. [x] **G1 Dewdrop** — restored and device-tested.
3. [x] **G2 Particle** — restored and device-tested.
4. [x] **G2 Light Particle** — restored and device-tested.
5. [x] **G2 Soda** — restored and device-tested.
6. [x] **G2 Crystal** — restored ten-facet GL mesh, OEM overlay passes,
   50/201 px drag geometry and 300/400 ms release timing.
7. [x] **Z1 Blinds** — visually accepted restoration; promoted out of WIP.
8. [x] **Revolving Glass** — inspired modern restoration with independent
   lockscreen and Last screen sources, bounded spin and deterministic handoff.
9. [x] **Z2 Particle — not planned.** A sparkle-only partial port is not useful.
10. [x] **G1 Ripple — not planned.** Its moving/deformable background dependency
  is impractical without reducing the restoration to a clone.
11. [x] **X10 — not planned.** Its restoration value is too low to justify a
  dedicated renderer and maintenance burden.

- [x] **G2 Pixelate.** The donor-derived triangular mesh, two-source cache path,
  progressive drag pixelation and corrected colour mapping are included in the
  stable ARM64 release.

Every port requires tap, stationary hold, drag, incomplete release, unlock-tail,
orientation and 60/90/120/144 Hz validation. Route future audio only through
`EffectAudio`; XLocker audio is oracle-only.

## S3 None

- [x] Keep stable internal ID 31 and rename the user-facing effect to **S3 None**.
- [x] Place it first in the effect picker.
- [x] Include it in tester no-colormap mode.
- [x] Reconstruct geometry and timing from S3 Neo firmware rather than the
  XLocker Circle package.
- [ ] Physical S3/Note oracle comparison for arrow/lock artwork and scale.

## Deferred to 1.0.6.1

- The configurable Random effect mode has moved to `TODO-1.0.6.1.md`; it is not
  part of the 1.0.6.0 release.

## Under-lockscreen source experiment

- [ ] On Android 14+, probe `AccessibilityService.takeScreenshotOfWindow()` for
  the launcher/application accessibility window below SystemUI.
- [ ] If unavailable or secure, use a per-display/orientation last-unlocked
  underlay cache on the next wake.
- [ ] Retain current SystemUI colormap and no-colormap paths as safe fallbacks.
- [ ] Validate phone, fold, tablet and non-Samsung SystemUI without stretching or
  cross-profile cache reuse.

## Deferred fidelity audits

- [ ] Lens Flare Original, Blue Ring, Blood and Lightning: GL/Canvas A/B against
  the physical Note 4 oracle.
- [ ] Geometric Mosaic device attribution: currently A5-era at medium confidence;
  obtain an A5 firmware before changing that wording to confirmed origin.
