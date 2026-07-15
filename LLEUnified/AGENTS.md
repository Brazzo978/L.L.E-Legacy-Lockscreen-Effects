# LLE Unified working rules

- This directory is the only active LLE application source tree.
- Keep `src`, `res`, `assets`, package name and preference schema shared across
  ARM32 and ARM64.
- Put ABI selection in `EffectAvailability`; picker visibility and renderer
  construction must agree with that registry.
- ARM32 uses original Samsung engines staged by `build-arm32.ps1`.
- ARM64 uses reconstructed/validated engines staged by `build-arm64.ps1`.
- Native code must load lazily when its effect is selected. App startup must not
  initialize libraries for the other ABI.
- A saved unavailable effect must fall back to S4 Lens Flare safely.
- Run both target builds after any shared Java/resource/lifecycle change.
- Do not edit `../LLE64` or `../unlock-effects-test/charging-touch-test-apk` to
  implement new behavior; consult them only as frozen references.
