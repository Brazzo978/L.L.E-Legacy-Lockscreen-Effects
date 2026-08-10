# L.L.E. 1.0.5.5 TODO

Working baseline: `1.0.5.4` (`versionCode 30`), ARM64-only production build.

## Adaptive-refresh HP physics mode

Implementation status (2026-08-09): an opt-in experimental high-refresh master
now exists beside the unchanged stock/default variants. Adaptive simulation is
implemented for app-owned S6 Water Droplet, Sparkling Bubbles, Coloured Droplet,
Coloured Droplet + Gyro, S5 Popping Colours, S3 Ripple, Brilliant Ring and
Brilliant Cut. N2 Ink in Water ships fixed-60 only in `1.0.5.5`, with no HFR
or Smootify control. The existing HFR/Smootify experiments are superseded for
this release and must not be treated as ship-ready.
Presentation-only high refresh is implemented and host-tested for N4 Abstract
Tiles and N4 Geometric Mosaic; neither exposes a speed control.
Adaptive paths advance physics
from measured display frame time across the supported 60-144 Hz interactive
range while preserving recovered 60 Hz wall-clock speed;
live refresh changes do not recreate the renderer and stalled frames do not
replay a backlog. Keep both variants until phone, tablet and Fold visual testing
is complete. `tools/test-native-refresh.ps1` is the host regression gate.

- [x] Verify, effect by effect, whether the app-owned physics can be driven
  directly at the active display refresh rate instead of only presenting
  additional frames over a stock-rate simulation.
- [x] Investigate an optional adaptive-refresh / HP mode that follows real
  panel changes such as 60, 90 and 120 Hz without requiring a renderer restart.
- [x] Keep the validated stock cadence as the default. HP mode must be enabled
  only for effects whose simulation remains stable and visually useful at the
  higher tick rate.
- [x] Separate simulation cadence from presentation cadence where direct
  high-rate physics would make an effect too fast, unstable or oracle-inaccurate.
- [x] Audit native/JNI timestep assumptions, accumulators, input sampling,
  particle lifetime, damping and unlock completion before enabling an effect.
- [ ] Post-.5 non-blocking: validate runtime refresh-rate changes, portrait/landscape, phone, tablet
  and Fold profiles without physics jumps, accumulated backlog or state loss.
- [ ] Post-.5 non-blocking: compare each candidate against its stock oracle and document whether HP
  mode intentionally favors smoothness over exact legacy speed.

Completed engineering gates:

- [x] Preserve the recovered stock physics as the default selectable variant.
- [x] Add a separate experimental selector for each eligible app-owned renderer;
  do not enable unsupported vendor-binary effects.
- [x] Temporalize integration, growth, damping, emissions, RNG cadence,
  lifetime, warm/idle accounting and unlock completion for 60-144 Hz. Keep the
  30 Hz host case only as an unsupported defensive/low-cadence stress test.
- [x] Add strict host tests for 30/60/90/120/144 Hz, live cadence changes,
  jitter, first frame, stalls/no-backlog and stock/adaptive isolation.
- [x] Add the initial experimental physics-speed slider from 1.0x to 2.0x in
  0.1x increments for native-refresh simulation; audio and stock mode remain
  unchanged. It does not apply to Abstract Tiles or Geometric Mosaic.
- [x] Build and install the ARM64 Samsung-free tester on the S23.
- [x] Protect both beta non-automatic wallpaper paths with two consecutive
  confirmations. Either warning can switch directly to Automatic screenshot;
  the direct per-profile wallpaper picker uses the same guard.
- [x] Add a default-enabled, user-selectable three-finger swipe recovery
  gesture inside the touch box. A real 48 dp centroid swipe cancels the active
  effect and removes the touch/effect overlays for the current lock cycle; a
  new screen-off/on cycle rearms L.L.E, while a stationary three-finger touch
  does not trigger it.
- [x] Fix Coloured Droplet's high-refresh first-emission failure. A first
  presentation worth less than one whole particle used to let idle cleanup
  delete the zero-particle TOUCH/AFFORDANCE group, after which MOVE/UP input
  was rejected. Preserve groups awaiting fractional emission, protect stale
  asynchronous idle stops with an animation generation, and cover both paths
  in the host regression test. Controlled S23 reproduction after the fix
  accepted repeated long-press/tap gestures with zero native-input failures.
- [x] Fix S6 Water Droplet's mature-drag release cutoff in both stock and
  adaptive modes. `RELEASE_EDGE` now enters the recovered shrinking cleanup
  curve instead of deleting every still-visible particle after 50 phone ticks;
  host coverage verifies progressive cleanup at 60/120/144 Hz.
- [x] Expand the experimental master to S5 Popping Colours, N4 Abstract Tiles
  and N4 Geometric Mosaic. Popping uses fractional wall-time position/lifetime
  with a no-backlog stall guard; Tiles and Mosaic initially only requested
  display-vsync presentation; both retain that HFR path without speed controls.
- [x] Build and install the integrated ARM64 tester on the S23; Java compilation,
  APK verification and the Spark/Colour/S6 native refresh host suite pass.
- [x] Temporalize Brilliant Cut's seconds-based simulation and terminal hold,
  and Brilliant Ring's ages, emission timeout, growth/fade, unlock geometry and
  stock RNG/noise cadence. Dedicated 60/90/120/144, jitter, stall, live-refresh
  and recreation tests pass; both remain fixed 1.0x in this wave.
- [x] Add true display-refresh Ripple simulation. Ripple q=1 delegates to the
  legacy solver bit-for-bit; fractional PDE steps remain <=1 stock tick. Host
  60/90/120/144/stability tests and strict ARM64 NDK compilation pass.
- [x] Close the N2 Ink in Water `.5` scope at recovered fixed-60 only. The
  current HFR/Smootify experiments are superseded and do not ship in this release.
- [x] Keep Lens Flare, Stone Skipping, Blind, Mass Tension and Seasonal off the
  HFR picker: they already schedule presentation at display vsync, so an opt-in
  presentation switch adds nothing. Blind's separate follower-normalization
  fidelity work is implemented and host-tested; Watercolor HFR is implemented
  and first accepted visually on S23, pending its broader release-gate coverage.
- [x] Scale Seasonal unlock sprites automatically from Samsung's 360 dp
  short-side reference, including sprite size, intentional drift, particle
  spread and emission spacing. Apply a user-accepted 1.5x visibility boost over
  the display-derived scale and clamp the final multiplier to 1.0x-2.5x so
  modern phones and tablet/Fold layouts no longer render tiny seasonal assets.
- [x] Preserve emitted Seasonal particles after normal finger release so their
  recovered fade/rotation timeline completes; cancel/reset remain hard cleanup.
- [x] Confirm the corrected S6 mature-drag release tail visually on the S23.

Candidate audit for expanding high-refresh support:

- [x] Popping Colours: adaptive simulation implemented under the master while
  preserving the 16 ms stock branch and independent tuned drag-audio cadence.
- [x] Abstract Tiles: display-vsync presentation is implemented and host-tested;
  native simulation continues to consume real elapsed seconds, with no speed
  control exposed.
- [x] Geometric Mosaic: display-vsync presentation has no speed control. Its
  full-cover expansion and real-window neutralization complete at 400 ms; the
  standard swipe follows after 60 ms (about 460 ms), with generic cleanup after
  a further 300 ms. The internal 600 ms fade is not visible and is not expected.
  The S23 visual gate was accepted 100% by the user.
- [x] Brilliant Cut and Brilliant Ring: true adaptive simulation implemented at
  fixed 1.0x, with recovered stock branches retained as the default.
- [x] Ripple: true adaptive simulation implemented at fixed 1.0x behind the
  master with direct array/hash coverage. Ink remains fixed-60 in `.5`.
- [x] Blind: normalize its fixed per-vsync follow coefficient with elapsed time;
  this automatic refresh-fidelity correction is implemented and host-tested.
- [x] Prepare Watercolor's adaptive math path and strict portable host regression
  gate. HFR is implemented and first accepted visually on S23; its recovered
  feedback pipeline still requires the broader forced-refresh/GPU/device coverage.

Pre-existing Ink fidelity debt (post-.5 non-blocking) found through the preserved
S3 Neo Ghidra oracle:

- [ ] Separately evaluate restoring the original one `Fluid::Update` / one
  `AdvectDensity` pass, stock timestep/shader formula and recovered coefficient/
  Jacobi set. The current accepted production baseline uses four passes plus a
  compensating shader factor. This adaptive work deliberately preserves that
  baseline at q=1; do not mix the oracle correction into refresh validation.

## HFR and speed control matrix

This is the target release classification; an enabled HFR control still requires
the validation and blocker work listed below. Automatic correction is not a user
toggle, and none means no HFR/speed control is exposed.

| Effect(s) | HFR | Speed | Release state / note |
| --- | --- | --- | --- |
| S6 app-owned Water Droplet, Sparkling Bubbles, Coloured Droplet, Coloured Droplet + Gyro, Popping Colours | Yes | Yes, 1.0x-2.0x | Implemented; complete device validation. |
| Abstract Tiles | Yes | No speed control | HFR presentation implemented and host-tested. |
| Geometric Mosaic | Yes | No speed control | Full-cover/window-neutralization fix accepted visually 100% on S23. |
| Watercolor | Yes | No, fixed 1.0x | Implemented; first S23 visual acceptance confirmed. Broader forced-refresh, GPU and device coverage remains. |
| Ripple, Brilliant Ring, Brilliant Cut | Yes | No, fixed 1.0x | Complete forced-refresh/device coverage. |
| Blind | Automatic temporal correction | None | Implemented and host-tested; no user-facing HFR toggle. |
| Ink in Water (1.0.5.5), Lens Flare, Stone Skipping, Mass Tension, Seasonal | None | None | Ink ships fixed-60 only; do not add an HFR/speed toggle. |

## 1.0.5.6 Ink in Water port (not part of .5)

- [ ] Create a new Ink library/port with improved colour and rendering quality.
  Treat it as new `1.0.5.6` work; the superseded HFR/Smootify experiments from
  `.5` are not a shipping baseline or pre-release gate.

## Release .5 MUST-SHIP / BLOCKER closure

All release gates in this section are complete and accepted. `.5` is approved to
ship; unchecked work elsewhere is explicitly post-release/non-blocking follow-up.

- [x] **Geometric Mosaic:** full-cover expansion/window neutralization at 400 ms,
  standard swipe at about 460 ms, and generic cleanup after a further 300 ms are
  accepted visually 100% on S23. The non-visible internal 600 ms fade is not a
  release expectation.
- [x] **Release scope closed:** Ink in Water is fixed-60 only in `.5`; do not
  ship its superseded HFR or Smootify experiments.
- [x] Confirm the corrected S6 mature-drag release tail visually on the S23.
- [x] Confirm Sparkling Bubbles' accepted 875 ms residual-particle tail; S6 and
  Coloured remain at their established 340 ms timing.
- [x] Watercolor: HFR is implemented and first accepted visually on S23; complete
  comparative forced-refresh coverage at 60/96/120/144 Hz, including long
  stroke, release tail and unlock; profile 120/144 Hz GPU/jank and repeat with
  stable ping-pong and named stock-feedback A/B across device profiles.
- [x] Complete forced-refresh coverage for Ripple, Brilliant Ring and Brilliant
  Cut at 60/90/120/144 Hz.
- [x] Complete the agreed device/lifecycle validation: S23 visual/unlock A/B at
  60/96/120 Hz; tablet portrait/landscape including active rotation; Fold
  cover/main including active display transition; System/Media audio and
  QS/AOD lifecycle on all three profiles; additionally cover live refresh,
  pause/resume, jitter, first frame and stalls/no-backlog on-device for every
  enabled HFR candidate against its stock oracle.

Historical implementation and acceptance notes:

- [x] Move high-refresh controls from the global Advanced panel into each
  compatible effect card. Enabled state and 1.0x-2.0x speed are now persisted
  per effect, with a one-shot migration from the former global tester values.
  Cards are expanded only when at least one built-in panel reports a supported
  mode above 60.5 Hz; true 60-Hz-only devices keep the original compact cards.
  Initial implementation exposed speed only on S6/Sparkling/Colour/Popping;
  Abstract Tiles and Geometric Mosaic retain HFR presentation without a speed
  control. Ripple/Ring/Cut and Watercolor remain fixed 1.0x; Ink is fixed-60
  only for `.5`.

- [x] User visually accepted Popping at 144 Hz/1.5x and found Ripple, Abstract
  Tiles and Geometric Mosaic fluid on the S23 with the master enabled.
- [x] The prior N2 Ink in Water HFR/Smootify experiments are superseded for `.5`.
  This release retains recovered fixed-60 Ink only; a separate improved-colour/
  rendering library is planned as a new `1.0.5.6` port.
- [x] Restore complete finite unlock tails before the modern Android handoff
  alpha-neutralizes the real effect window. The initial default estimates are:
  Popping 325 ms, Brilliant Cut 415 ms, Mass Tension 510 ms, Geometric Mosaic
  630 ms, Watercolor 800 ms, Abstract Tiles 925 ms, Brilliant Ring 930 ms and
  Lens Flare 600 ms. The normal path then settles for 60 ms before dispatching
  the synthetic swipe; Conservative uses the same visual-tail delay and only
  adds 80 ms of compositor/input settling (140 ms total). Ripple, Ink, Stone,
  Blind, S6, Colour, Sparkling and Seasonal retain their established wrapper
  delays because they have variable/ambient tails or intentional early parking.
  Ring/Cut tails were physically accepted on the S23 with the master enabled and
  disabled. First direct timing pass: Popping 375 ms was too long and Cut 440 ms
  was fractionally too long, hence the reductions above; Mass 520 ms, Watercolor
  825 ms and Abstract Tiles 940 ms were accepted before the final global 10 ms
  latency trim. Ring 975 ms felt slightly slow; its next candidate is 930 ms after
  the same trim.
  Popping 325 ms, Cut 415 ms, Mass 510 ms and Mosaic 630 ms are now physically
  accepted. Watercolor 815 ms still wanted 15 ms removed, Abstract 930 ms wanted
  5 ms removed, and Lens 620 ms wanted 20 ms removed, producing the values above.
  Lens 1240 ms had been physically far too slow because waiting for its mathematically
  complete, nearly transparent fade harms handoff latency. Mosaic's former
  missing full-cover is fixed: expansion/window neutralization completes at
  400 ms, the standard swipe follows at about 460 ms, and generic cleanup comes
  300 ms later. Its internal 600 ms fade is not visible or expected; the S23
  visual gate is accepted. Tune the remaining default estimates by direct device
  feedback.

- [x] Sparkling Bubbles retains a variable residual-particle tail rather than a
  fixed terminal envelope. Both 340 ms and the first 420 ms candidate were
  physically perceived as cutting it too early; 750 ms still left a clearly
  visible animation, while 950 ms felt slightly generous. The accepted midpoint
  is 875 ms. S6 and Coloured remain at their established 340 ms timing.
- [x] Build and install the ARM64 Tester as `com.codex.lle64.test` version
  `1.0.5.5` (`versionCode 31`) on the S23 without a crash.

## Follow-up, not a .5 release blocker

- [ ] Attach the two standalone Java seam tests to an automated test runner;
  they currently pass only as standalone tests.
- [ ] If ARM32 is distributed, investigate its ineffective controls. This does
  not affect the ARM64 Tester or the ARM64 production release gate.

No effect is implicitly eligible: support must be decided and validated per
renderer.

## Additional OEM validation (post-.5 non-blocking)

- [ ] Reproduce the Galaxy A16 (`SM-A156U`) N2 Ink in Water boundary report on
  the current build. The supplied 1.0.5.3 report was captured while unlocked
  with the renderer parked and uses a 720x1280 imported map for a 1080x2340
  profile, so obtain a screenshot/video plus a report while the effect is
  visibly active before changing full-screen layout or recovered mesh math.
- [ ] Validate normal lockscreen, Quick Settings suppression/restoration,
  touch delivery and unlock handoff on at least one Lenovo device.
- [ ] Run the same matrix on at least one Xiaomi/HyperOS device.
- [ ] Revisit the modern-Android delayed/unrecognized-touch warning and missed
  MOVE events when an affected Motorola or equivalent device is available.
- [ ] Keep OEM-specific signatures optional: the default detector must not
  regress Samsung phone, tablet or Fold behavior.
