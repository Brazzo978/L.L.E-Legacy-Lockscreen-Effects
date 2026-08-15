# XLocker research note (2026-08-15)

## Release decision

Do not import XLocker code, native libraries, audio, or artwork into L.L.E.
The themes were distributed as proprietary freeware, and the only official
repository found does not provide a license granting reuse or redistribution.

XLocker may be used only as a secondary, private visual oracle. Samsung firmware
and recovered Samsung implementations remain the authoritative sources for
effect behaviour, timing, and release assets.

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

The one useful future candidate is **S5 Circle**. It should not be recovered from
an XLocker package: the local Samsung firmware material already contains
`CircleUnlockEffect.java`, `CircleUnlockCircle.java`, and the corresponding
`KeyguardEffectViewNone.java` integration. That source indicates a Canvas effect
without a colormap, with approximately 666 ms enter and 333 ms exit timing.

## Backlog

- Consider a clean S5 Circle port from the local Samsung firmware sources.
- Treat LG/Sony XLocker themes only as discovery leads; obtain their behaviour
  from the corresponding OEM firmware before implementing anything.
- Do not add XLocker binaries or extracted resources to release artifacts.
