# L.L.E. Unified

Canonical source tree for **Legacy Lockscreen Effects**, package
`com.codex.lle`. Java logic, resources, preferences, accessibility lifecycle,
background capture and UI live in this directory only. ARM32 and ARM64 differ
only in their native engines, Samsung bytecode staging and runtime effect
availability.

The frozen pre-unification implementations remain in:

- `../unlock-effects-test/charging-touch-test-apk` (ARM32 reference);
- `../LLE64` (ARM64 reference).

Do not develop new application logic in those reference trees.

## Builds

Build both targets from the same source:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Build one target:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Target Arm32
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Target Arm64
```

Outputs:

- `build/armeabi-v7a/LLE-armeabi-v7a-debug.apk`
- `build/arm64-v8a/LLE-arm64-debug.apk`

Both APKs use the same package, version, resources and signing identity. They
are alternative installations, not two applications that can coexist.

## Effect policy

- Architecture-independent effects are available in both builds.
- On ARM32, Samsung legacy `.so` engines are selected where available.
- On ARM64, only the completed AArch64 engines and app-owned renderers are
  exposed.
- An unavailable saved selection is migrated safely to S4 Lens Flare.
- Native libraries are loaded lazily by the selected renderer; incompatible
  libraries must never be touched during normal application startup.

The runtime decision is based on the bitness of the current process, not only
on the ABIs supported by the device.

## Fold displays

Fold dual-panel behavior, cache separation and device validation are recorded
in [`FOLD-DISPLAY-PORT.md`](FOLD-DISPLAY-PORT.md).

## Provenance

The pre-unification state is preserved in Git tag
`lle-pre-unification-2026-07-15`. Samsung firmware libraries remain proprietary
and are intended for private/local compatibility testing unless redistribution
rights are established separately.
