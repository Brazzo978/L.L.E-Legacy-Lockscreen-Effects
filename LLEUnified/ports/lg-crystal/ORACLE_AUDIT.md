# G2 Crystal oracle audit

Oracle package:

- APK: `com.optimusdev.crystal-v1.1.apk`
- Package: `com.optimusdev.crystal`
- Version: `1.1` (`versionCode 110`)
- Minimum/target SDK: 14/21
- SHA-256: `DC4DB36AE7496C5ADD1D7818646A34664FAC2D2CE9B68669562FE377202022BD`

The archived APK was inspected with JADX 1.5.6. L.L.E. reimplements the renderer
for modern ARM64 Android; it does not embed, launch, or depend on the donor APK.
The four artwork layers and two sound files are the authorized archived originals
and are stored as normal L.L.E. resources.

## Recovered renderer contract

- The crystal is a ten-sector 3D mesh, not a procedural full-screen circle.
- Five mesh groups are rendered: upper girdle (30 vertices), upper bezel (30),
  lower bezel (five four-vertex strips), star (15) and table (five).
- The sampled last-screen texture is lit by two directional lights. The original
  constants are ambient 0.2, diffuse 0.4, specular 1.0 and shininess 80.
- The mesh shader derives the refracted texture coordinate from facet depth and
  normal, then rotates the sampling frame as the crystal grows.
- The main crystal artwork is rendered on the same mesh. Shadow, lighting 1 and
  lighting 2 are separate alpha-blended passes positioned around the touch point.
- Draw-time rotation is `-90 * radius / viewportWidth` degrees.

## Recovered gesture contract

- Touch-down radius: 50 px.
- Drag mapping: `50 + distance * (201 - 50) / 201`.
- Incomplete release: 300 ms with the stock accelerate interpolation.
- Completed release: 400 ms to `1.3 * hypot(width, height)`, with alpha fading
  from 1 to 0 across the same interval.
- Unlock audio begins when the completed release starts.
- Reset must restore radius, phase, opacity and multi-gesture readiness.

## L.L.E. adaptation

The original renderer accepted two captured images: a composed lockscreen image
and a live/root screenshot. L.L.E. keeps the real lockscreen visible behind its
transparent GL surface and supplies the last-unlocked cache only to the crystal
sampling pass. This preserves the same visible division without copying the
obsolete root screenshot mechanism.

The implementation keeps one full-size GL texture plus the four small OEM artwork
textures. Texture ownership, generation checks, pause/recreate handling and the
readiness gate remain owned by `CrystalPrismBetaEffectView`.
