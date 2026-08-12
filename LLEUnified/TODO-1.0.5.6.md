# L.L.E. 1.0.5.6 release gate

## Good Lock-inspired particle effects

Effect IDs `28`, `29` and `30` are the app-owned Popping Color, Rectangle
Traveller and Bouncing Color variants. They use the per-profile
`BackgroundSource` cache only to sample particle colour; they never paint that
bitmap as a fullscreen background. The renderers are transparent outside their
particles, report first-frame readiness, support the standard per-effect HFR
toggle and release their bitmap/lifecycle resources on teardown.

Completed release boundary:

- [x] ARM64-only product scope; no vendor library or copied protected asset.
- [x] App-owned Java implementation with independent assets and standard
  readiness/background-cache ownership.
- [x] HFR presentation and the shared HFR-only 1.0x--2.0x speed control;
  stock mode remains the default.

Extended post-release device coverage:

- [ ] Repeat automatic/imported-background, cancel, unlock, screen-off/on,
  Quick Settings, replacement and rotation tests on tablet and Fold cover/main
  profiles when that hardware is available.
- [ ] Expand the visual comparison set beyond the ARM64 phone observations.

## N3 Ripple Ink

Effect ID `27` is the production ARM64 N3 Ripple Ink renderer. Its
independently implemented state machine, GLES pipeline and native worker were
recovered against Note 3 ENB4 `libRippleInkEffect.so` (SHA-256
`828229BC756C30E9F7F6F70C11985C0C6FC68F860AF7E8B2AF18BBCC579A0C8D`).
The extracted oracle is never packaged. Production ships only the app-owned
`liblleN3RippleInk.so` and fails closed if its JNI/resource chain cannot start.
ARM32 retains the safe Lens Flare fallback for a persisted selection of `27`.

The picker exposes eight one-based stock palette slots, defaulting to slot `4`.
The HFR option presents the vanilla water mesh at display refresh while Ink
source, worker, advection, AddInk and dissipation remain on an independent
integral 60 Hz clock. There is no Ink speed control.

Completed production gates:

- [x] Raw integer touch callbacks update the native-style state machine
  immediately; there is no timestamped MOVE history, interpolation, debt or
  synthetic event replay.
- [x] State-1 press, state-2 classification, held-state emission, persistent
  profiles/backstep, release dissipation and previous-worker upload order match
  the ENB4 oracle.
- [x] The native worker matches the recovered self-advection, divergence,
  10-step Jacobi projection, strict 60/12/10 margins and Bionic `lrand48`
  contract.
- [x] Java golden trace, concurrency, JNI wiring, palette/compositor, HFR
  separation and native C suites pass.
- [x] The signed tester installs and runs on SM-S918B with the accessibility
  service, lockscreen surface and ordinary-finger press/drag/release working in
  normal and HFR modes; the user accepted the visual comparison.
- [x] The ABI, lifecycle and reverse boundary are documented under
  `reverse/ripple-ink/n3/`.

Remaining extended QA is non-blocking for the ARM64 phone release: repeat
cancel/unlock/rotation and long-run lifecycle stress on additional OEMs and
tablet/Fold profiles as hardware becomes available.
