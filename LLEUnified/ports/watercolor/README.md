# N3 Watercolor ARM64 port

This directory contains the clean-room AArch64 reconstruction of Samsung's
classic ARM32 Watercolor renderer.

Current architecture:

- the original Samsung `EffectView`/`WaterColorEffect`/`GLTextureView` Java
  contract remains the host;
- `libsecveSrkCommon.so` is rebuilt for AArch64 with only the JNI and GLES/FBO
  subset required by Watercolor;
- `libsecveWaterColor.so` is an ABI sentinel because the reconstructed common
  bridge owns the Watercolor scene directly;
- original ARM32 binaries remain immutable under `reference/arm32-original`;
- final output is premultiplied local brush alpha for composition above modern
  SystemUI. All recovered upstream brush, advection and colour math remains in
  the port.

Reverse reports and reproducible Ghidra helpers live under `reverse/`.
