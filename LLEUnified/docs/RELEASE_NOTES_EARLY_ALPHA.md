# L.L.E. Unified Early Alpha release notes

These notes describe the verified Early Alpha artifacts and provide the
matching GitHub release metadata.

## Proposed release metadata

- **Title:** `L.L.E. Unified 1.1.0 - Early Alpha 1`
- **Tag:** `v1.1.0-unified-alpha.1`
- **Target:** the reviewed default branch commit used to build both APKs
- **Pre-release:** yes
- **Assets:**
  - `LLE-armeabi-v7a-debug.apk`
  - `LLE-arm64-debug.apk`

Current locally verified candidate hashes:

| Asset/component | SHA-256 |
|---|---|
| `LLE-armeabi-v7a-debug.apk` | `0CF300333C1C300A00DEB74100333DB96526742DC66D5AC18E6763CBEEF50830` |
| `LLE-arm64-debug.apk` | `1773D4296D87BFD2E2D668E9D1A0AA68C187C6CEF87726AD812ACA2A3CA7B015` |
| ARM64 `libsecveAbstractTile.so` | `FF9237D441D69EE2065CC749F022356F2D0565F3E5DF263CB2A90B6A1B20D1F7` |

Regenerate and re-check hashes from the exact release commit. Documentation or
code changes after these builds make the table a historical candidate record,
not proof of a later artifact.

## Highlights

- One application source, package and preference schema for ARM32 and ARM64.
- ABI-aware effect picker and renderer construction with safe Lens Flare
  fallback when a saved/native effect is unavailable.
- Original patched Samsung ARM32 engines retained on compatible 32-bit
  devices.
- ARM64 Water Ripple, Watercolor and Abstract Tiles reconstructions.
- Note 5 ARM64 Colored Droplet, Gyro and Sparkling Bubbles integration.
- Transparent, screenshot-backed composition instead of an opaque fullscreen
  Samsung wallpaper pass.
- Fold Cover/Main screenshot caches, touch boxes, renderer recreation and
  independent effect/doodle routing.
- Bounded native lifecycle cleanup, context-loss recovery and lazy library
  loading.
- Built-in background recapture, cache viewer, touch wizard and runtime memory
  profiling controls.

## Effect coverage

The exact current table is maintained in the
[README effect availability section](../README.md#effect-availability).
Geometric Mosaic remains ARM32-only. Tabs Blind and Ink in Water remain hidden
WIP slots on both products.

## Installation notes

- The APKs are alternatives and cannot coexist because both use
  `com.codex.lle`.
- Use ARM32 only when the device supports `armeabi-v7a` applications.
- Use ARM64 on ARM64-only devices.
- Enable the L.L.E. accessibility service after installation.
- Android 11/API 30 or newer is recommended for accessibility screenshot
  capture.
- Configure a screenshot cache and touch box before judging an effect as blank
  or non-responsive.

Full installation and setup instructions are in the [README](../README.md).

## Verification status

- ARM32 build completed with the expected patched ARM32 native entries.
- ARM64 build completed with exact AArch64 entry, SONAME, dependency, JNI and
  stage-to-APK hash checks.
- Both candidate APKs passed v1/v2/v3 signature verification.
- ARM64 Abstract Tiles passed compile/link/package validation after transparent
  scatter-alpha, bitmap-stride and resize/destroy lifecycle fixes.
- On-device visual fidelity and long-cycle lifecycle testing remain part of the
  Early Alpha validation program; build success alone is not a 1:1 fidelity
  claim.

## Known limitations

- Reconstructed ARM64 effects are not guaranteed pixel-identical across modern
  GPUs and refresh rates.
- Transparent SystemUI composition necessarily differs from Samsung's original
  opaque framebuffer.
- Cached screenshots can become stale or be rejected when Android exposes a
  secure/wrong-panel surface.
- Fold detection and transition handling have not been validated on every Fold
  model or One UI release.
- Battery policy can stop the accessibility service unless the app is allowed
  to run in the background.
- Current artifacts are debug-signed and intended for testing.

## Firmware and legal notice

The build consumes proprietary Samsung firmware-derived libraries, bytecode,
sounds and/or assets. These release notes and this repository do not grant a
license to those components. Redistributors are responsible for establishing
the necessary rights separately.

## Copy-ready GitHub release body

```markdown
## L.L.E. Unified 1.1.0 - Early Alpha 1

L.L.E. (Legacy Lockscreen Effects) now uses one shared application source and
preference schema for ARM32 and ARM64. Choose one APK for the process ABI your
device can run; both packages are `com.codex.lle` and cannot coexist.

### Included

- ABI-aware effect picker with safe Lens Flare fallback
- Original patched Samsung engines on ARM32-compatible devices
- ARM64 Water Ripple, Watercolor and Abstract Tiles reconstructions
- Note 5 ARM64 Droplet/Gyro and Sparkling Bubbles integration
- Transparent screenshot-backed lockscreen composition
- Fold Cover/Main caches, dual touch boxes and per-panel effect/doodle routing
- Bounded native lifecycle and context-loss handling

Geometric Mosaic is ARM32-only. Tabs Blind and Ink in Water are still hidden
WIP effects.

### Install

Use `LLE-arm64-debug.apk` for ARM64-only devices. Use
`LLE-armeabi-v7a-debug.apk` only when the device supports 32-bit ARM apps.
Install with `adb install -r <apk>`, launch L.L.E., enable its accessibility
service, capture the lockscreen background and configure the touch box.

This is an Early Alpha. ARM64 reconstructions target the recovered Samsung
behavior but are not guaranteed pixel-identical on every GPU, refresh rate or
One UI release. Fold support remains device-sensitive.

### Important firmware notice

These builds contain proprietary Samsung firmware-derived components. This
release does not grant a license to those components; redistributors are
responsible for establishing the necessary rights separately.

See the repository README for the effect matrix, complete setup, Fold workflow,
troubleshooting and source-build instructions.
```
