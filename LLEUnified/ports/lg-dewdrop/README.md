# LG G1 Dewdrop restoration

## Source and authorization

The restoration source is the archived XLocker theme package
`com.optimusdev.dewdrop-v1.1.apk`, supplied by Erik (XLocker/OptimusDev) with
authorization to use the archived effect material in L.L.E.

- Source APK SHA-256: `160736E5CA9ACC58F057F887229A7DC59A3177EAB691B8F01BAC20B3A486F9DE`
- Original implementation: Java + OpenGL ES 2.0; no native `.so` payload.
- L.L.E integration: dedicated pre-lock underlay, tester-only ARM64 effect.

## Recovered renderer behaviour

The donor uses a 50-sector by 70-ring ellipsoid mesh. Its vertex shader computes
the cap height as `b * sqrt(1 - r^2 / a^2)` and refracts the captured image with
an index of `3.0`. L.L.E evaluates that recovered radial equation in concentric
Canvas bands, preserving the reversible 44 dp to 113.33 dp drag mapping, the
300 ms cancel tween and the 400 ms unlock expansion. The original 720 x 720
optical overlay and its six-piece scale curve are retained.

## Imported assets

| Original asset | L.L.E resource | SHA-256 |
| --- | --- | --- |
| `dewdrop_hole.png` | `lg_dewdrop_hole.png` | `1A0966E23DABDBAF221E5B0B313A623E18C30CD077D56D633293E85D16753457` |
| `dewdrop_lock.ogg` | `lg_dewdrop_lock.ogg` | `536457D36EAC751C8B06A996DC0D4F281BD62527016019E9070806BE6685560E` |
| `dewdrop_touchdown.ogg` | `lg_dewdrop_touchdown.ogg` | `410E5A4ECC688B2DAB46A297F799336C6C3A9D13C119DDDD7C03AEB9F947E884` |
| `dewdrop_touchrelease.ogg` | `lg_dewdrop_touchrelease.ogg` | `D73C4EDE74F3453B815502F1AFBBEF98D31EDFD753D8B297D924C757871BEEB2` |
| `dewdrop_unlock.ogg` | `lg_dewdrop_unlock.ogg` | `60DFDF3C0434C6B7184F8B3BFDFE210152AF6D21FF0385DA389369821736B32F` |
