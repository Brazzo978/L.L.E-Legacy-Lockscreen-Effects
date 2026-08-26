# XLocker research note (2026-08-15)

## Updated release decision

The initial clean-room restriction below was superseded after direct contact with
Erik, the original XLocker/OptimusDev author. He supplied the archived effect APKs
and authorized their use for the L.L.E legacy-restoration project. The project may
therefore use the original effect code, artwork, shaders and audio when required
for an authentic restoration.

Archive-derived material must remain identifiable by effect and provenance. L.L.E
adapts the renderers to its own overlay/capture lifecycle and does not bundle the
obsolete XLocker host application. Samsung firmware remains the preferred source
for Samsung effects such as S3 None.

## Primary source checked

- Repository: <https://github.com/XLocker/SampleTheme>
- Checked revision: `68a5a3564e1e5daa4665213f8cf729cfd2a51cfe`
- Package family: `com.xlocker.host` / X-Locker Team
- Contents: SDK and sample-theme contract only; no legacy effect renderers
- Licensing: no `LICENSE`, `COPYING`, or `NOTICE` file was present

The sample documents the theme lifecycle and host integration, but it is not a
permissively licensed renderer source.

## Catalog comparison

The historical Samsung-themed catalog overlaps almost entirely with effects
already reconstructed from better Samsung sources: Geometric Mosaic, Water
Droplet, Abstract Tiles, Particle/Popping Colours, Watercolor, Ripple, and Lens
Flare.

The useful Samsung candidate is **S3 None** (called Circle by the XLocker
theme). It should not be recovered from an XLocker package: the local Samsung
firmware material already contains
`CircleUnlockEffect.java`, `CircleUnlockCircle.java`, and the corresponding
`KeyguardEffectViewNone.java` integration. That source indicates a Canvas effect
without a colormap, with approximately 666 ms enter and 333 ms exit timing.

## Backlog at time of initial research

- Use the clean S3 None port from the local S3 Neo firmware sources.
- Restore LG themes from the authorized archive when it provides the complete
  effect implementation; consult LG firmware when archive material is incomplete.
- Keep archive assets scoped to their corresponding restored effect.
