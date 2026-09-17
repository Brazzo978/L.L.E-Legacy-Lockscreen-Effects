# LLE ARM64 working rules

- This directory is the only active L.L.E. application source tree.
- The supported APK is ARM64 `com.codex.lle64`.
- Keep ABI and effect availability decisions centralized in
  `EffectAvailability`; picker visibility and renderer construction must agree.
- Load native code lazily only when its effect is selected.
- A saved unavailable effect must fall back safely.
- Do not add dependencies on deleted legacy trees or archived ARM32 binaries.
- Do not store signing credentials in the repository. Stable builds require the
  external release keystore, signing lineage inputs and compatible legacy signer.
- Run `build.ps1` after Java/resource/lifecycle changes and exercise the
  affected effect on a real device.
