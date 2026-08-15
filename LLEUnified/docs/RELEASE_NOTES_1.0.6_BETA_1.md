# L.L.E 1.0.6 Beta 1

This co-installable ARM64 tester begins the clean-room research cycle discovered
through the historical XLocker catalog. It does not ship XLocker program code,
native libraries, audio, models, shaders, or extracted artwork.

## Experimental effects

- S5 Circle (None), reconstructed from local Samsung OEM material.
- LG G2 Pixelate, an original app-owned GLES interpretation.
- LG G2 Particle, an original app-owned GLES particle-ring interpretation.
- LG G2 Crystal, a procedural refractive interpretation pending an OEM oracle.
- Xperia Z1 Blinds, an original app-owned Canvas strip renderer.
- Revolving Glass, a simplified procedural GLES interpretation which rotates the
  cached lockscreen image rather than live SystemUI clock and widget layers.

S5 Particle maps to L.L.E's existing Samsung-sourced Popping Colours renderer.
The six new entries are tester-only and unavailable in the production 1.0.5.7
package. Pixelate and Crystal expose the per-effect high-frame-rate and creative
speed controls; naturally time-based renderers follow the panel cadence directly.

## Package

- Version: `1.0.6.B1` (`versionCode 40`).
- Package: `com.codex.lle64.test`.
- ABI: `arm64-v8a` only.
- Samsung-free; no legacy vendor or XLocker binary payload.
