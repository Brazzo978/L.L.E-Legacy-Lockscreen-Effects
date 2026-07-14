# Water Ripple AArch64 port

This directory is an analysis/validation port and is deliberately not packaged in `LLE64-debug.apk` yet.

## Implemented and confirmed

- JNI `ripple` core: mesh-to-detail mapping, radius-3 cone and accumulation order.
- JNI `move` core: exact three-pass order, empty threshold, height clamp and in-place final Laplacian.
- JNI `initWaters`, `move` and `ripple` entry points in an isolated `libWaterRipple.so` WIP.
- AArch64 build with no legacy C++ runtime dependency.

Build and execute the deterministic core test on a connected ARM64 Android device:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-port.ps1 -RunOnDevice
```

Fold7 reference result (2026-07-14):

```text
PASS hash=59890e7812c02590 centerVelocity=2.50805116 centerHeight=7.21869993
```

The JNI core probe also passed inside ART on the Fold7 using the exact Samsung
class name and method signatures:

```text
PASS Water Ripple ARM64 initWaters/ripple/move through ART
```

Build that isolated APK with `LLE64\build.ps1 -IncludeRippleCoreProbe`. The
probe Activity is exported only in probe builds; the normal manifest keeps it
private.

The shared objects currently need only current Android platform libraries and do not depend on STLport. `libWaterRipple.so` is still an isolated core bridge and is not packaged by the app build: the remaining GPU/lifecycle JNI methods are deliberately absent. It is not a complete renderer; GLES pass order, shaders, textures and transparent delta composition remain gated by the reverse reports.
