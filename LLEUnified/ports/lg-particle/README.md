# LG G2 Particle restoration

## Source and authorization

Source: archived XLocker package `com.optimusdev.particle-v1.1.apk`, supplied by
Erik (XLocker/OptimusDev) with authorization to use the archived effect material
in L.L.E.

- APK SHA-256: `34E405C182C9C1FBF762BC88079A68157AB9AA5D5BAAA8448167ADC4B6117D9F`
- Original implementation: Java + OpenGL ES 2.0, without native libraries.
- L.L.E integration: existing effect ID 33, tester-only ARM64, dedicated pre-lock underlay.

## Recovered behaviour

The donor uses 64 point-sprite emitters split across four radial families. Point
sizes are selected from 3/7/11 dp and 4/8/12 dp groups, particles respawn on
600–1600 ms cycles and converge on independently jittered radii. Both cancel and
unlock use 400 ms accelerated transitions. L.L.E batches a bounded 1024-point
representation in one GLES draw call and retains the opened pre-lock frame for
550 ms after the recovered expansion.

## Imported assets

| Original | L.L.E resource | SHA-256 |
| --- | --- | --- |
| `particle_hole.png` | `lg_particle_hole.png` | `0B4D27E0483D30012A204B558E6D34F0204CD3A884DF46D5F9DB2E67CCB59E17` |
| `particle_white_normal.png` | `lg_particle_white_normal.png` | `55D6B8FFC2899285B48AA20034C28867F3125BDD2FA28BA068F56C8C28A96152` |
| `particle_lock.ogg` | `lg_particle_lock.ogg` | `31ED5DB64A5E4BC6E4677D71FE979001FE5E45818AD6F3487CD03325579A333D` |
| `particle_unlock.ogg` | `lg_particle_unlock.ogg` | `22AC45BAFEB65653653BE1FE0603E904FE159CC476B3C27C6461BCB4A8D6B5BB` |
