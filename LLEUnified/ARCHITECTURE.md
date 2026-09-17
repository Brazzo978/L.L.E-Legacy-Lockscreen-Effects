# ARM64 architecture

## One active product

`LLEUnified` produces the ARM64 `L.L.E` application with package
`com.codex.lle64`. The Java/JNI namespace remains `com.codex.lle` so existing
reconstructed native entry points stay stable.

The retired ARM32 product, its original native engines and the old application
trees are preserved in `archive/full-unified-1.0.6.5`. They are historical
references, not build inputs.

## Runtime rules

`EffectAvailability` is the source of truth for picker visibility and
preference validation. `ChargingAccessibilityService` constructs a renderer
only after an available effect is requested. If construction or native loading
fails, the service tears down the partial renderer and persists the safe
fallback.

Native libraries load lazily. The application must remain usable when an
optional renderer is unavailable, a screenshot cannot be captured or the
device changes display state.

## Build isolation

- `build/arm64-v8a` contains the primary staging tree and APK.
- `build.ps1` builds only ARM64.
- `build-arm64.ps1` verifies package/label, resource-package DEX relocation,
  native entries, ELF machine, SONAMEs, dependencies and JNI exports.
- Tester signing material is generated locally under ignored `.keys/`.
- Stable signing keys and the compatible legacy signer remain external to Git.
- Optional legacy-vendor output is retained only for oracle comparison and
  diagnostics; it is not the normal release product.
