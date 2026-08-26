# L.L.E 1.0.6 TODO

Updated: 2026-08-15

## Firmware acquisition

- [ ] LG Optimus G E975 stock `V10`, Android 4.1.x Jelly Bean.
- [ ] LG G2 D802 stock `V10`, Android 4.2.2 Jelly Bean.
- [ ] Extract SystemUI/keyguard, LG framework jars, resources and relevant EGL
  libraries. Firmware is research material only; do not flash a device and do
  not ship proprietary LG/XLocker binaries or assets.

## Effect port priority

1. [ ] **G1 White Hole** — high interest.
2. [ ] **G1 Dewdrop** — high interest.
3. [ ] **G2 Particle** — high interest.
4. [ ] **G2 Light Particle** — high interest.
5. [ ] **G2 Soda** — high interest.
6. [x] **G2 Crystal** — restored ten-facet GL mesh, OEM overlay passes,
   50/201 px drag geometry and 300/400 ms release timing.
7. [ ] **Z1 Blinds** — next active port; validate the lockscreen-source and
   two-shader path against the archived APK.
8. [ ] **Revolving Glass** — third active experiment and first deliberate
   two-source carrier; validate separate lockscreen and home/underlay captures
   without changing the current single-source LG effects.
9. [ ] **Z2 Particle** — maybe/final group; do not ship a sparkle-only partial port.
10. [ ] **G1 Ripple** — maybe/final group; it requires the captured source plus
    moving/deformable state, so do not ship a partial clone.
11. [ ] **X10** — maybe/final group; low restoration value.

- [ ] **G2 Pixelate — master priority 14.** Defer the replacement until the
  Revolving Glass experiment proves distinct lockscreen and home/underlay
  captures; do not promote the current one-source inspired beta.

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

## Random effect mode

- [ ] Add a **Random** picker entry and a configuration page containing a
  selectable pool of effects.
- [ ] Resolve one compatible effect once per lock cycle and keep it latched until
  that cycle ends. QS, AOD, rotation and renderer recreation must not reroll it.
- [ ] Filter the pool through `EffectAvailability`, build/ABI support and current
  no-colormap compatibility before choosing.
- [ ] Migrate legacy/WIP aliases to their effective app-owned IDs before storing
  or resolving the pool.
- [ ] If the filtered pool is empty, fall back to S3 None in tester no-colormap
  mode; otherwise use the normal safe fallback.
- [ ] Avoid immediate repetition when at least two compatible effects remain.
- [ ] Log only selected effect ID, candidate count and fallback reason; do not
  expose package names, wallpaper content or user input.

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
