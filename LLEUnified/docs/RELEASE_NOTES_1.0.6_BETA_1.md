# L.L.E 1.0.6 Beta 1

This co-installable ARM64 tester begins the legacy restoration cycle recovered
through the historical XLocker archive. Erik, the original XLocker/OptimusDev
author, supplied the archived packages and authorized their use for L.L.E.

The beta includes the original White Hole and Soda artwork/audio required for an
authentic restoration. The renderers are adapted to L.L.E's modern accessibility
overlay and pre-lock capture pipeline; no XLocker host application or native
library is bundled.

## Restoration beta

- S3 None, reconstructed from the Galaxy S3 Neo Circle Unlock implementation.
- LG G1 White Hole, restored with its original animated corona and sounds.
- LG Soda, restored with its original particle artwork and sounds.

## Experimental effects

- LG G2 Pixelate (WIP), an original app-owned GLES interpretation.
- LG G2 Particle (WIP), an original app-owned GLES particle-ring interpretation.
- LG G2 Crystal (WIP), a procedural refractive interpretation pending an OEM oracle.
- Xperia Z1 Blinds (WIP), an original app-owned Canvas strip renderer.
- Revolving Glass (WIP), a simplified procedural GLES interpretation which rotates the
  cached lockscreen image rather than live SystemUI clock and widget layers.

S5 Particle maps to L.L.E's existing Samsung-sourced Popping Colours renderer.
The restoration and WIP entries are tester-only and unavailable in the production
1.0.5.7 package. Pixelate and Crystal expose the per-effect high-frame-rate and
creative speed controls; naturally time-based renderers follow the panel cadence
directly.

## Package

- Version: `1.0.6.B1` (`versionCode 40`).
- Package: `com.codex.lle64.test`.
- ABI: `arm64-v8a` only.
- Samsung-free; no legacy vendor or XLocker native binary payload.
