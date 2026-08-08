# L.L.E. 1.0.5.5 TODO

Working baseline: `1.0.5.4` (`versionCode 30`), ARM64-only production build.

## Adaptive-refresh HP physics mode

- [ ] Verify, effect by effect, whether the app-owned physics can be driven
  directly at the active display refresh rate instead of only presenting
  additional frames over a stock-rate simulation.
- [ ] Investigate an optional adaptive-refresh / HP mode that follows real
  panel changes such as 60, 90 and 120 Hz without requiring a renderer restart.
- [ ] Keep the validated stock cadence as the default. HP mode must be enabled
  only for effects whose simulation remains stable and visually useful at the
  higher tick rate.
- [ ] Separate simulation cadence from presentation cadence where direct
  high-rate physics would make an effect too fast, unstable or oracle-inaccurate.
- [ ] Audit native/JNI timestep assumptions, accumulators, input sampling,
  particle lifetime, damping and unlock completion before enabling an effect.
- [ ] Validate runtime refresh-rate changes, portrait/landscape, phone, tablet
  and Fold profiles without physics jumps, accumulated backlog or state loss.
- [ ] Compare each candidate against its stock oracle and document whether HP
  mode intentionally favors smoothness over exact legacy speed.

No effect is implicitly eligible: support must be decided and validated per
renderer.

## Additional OEM validation

- [ ] Validate normal lockscreen, Quick Settings suppression/restoration,
  touch delivery and unlock handoff on at least one Lenovo device.
- [ ] Run the same matrix on at least one Xiaomi/HyperOS device.
- [ ] Revisit the modern-Android delayed/unrecognized-touch warning and missed
  MOVE events when an affected Motorola or equivalent device is available.
- [ ] Keep OEM-specific signatures optional: the default detector must not
  regress Samsung phone, tablet or Fold behavior.
