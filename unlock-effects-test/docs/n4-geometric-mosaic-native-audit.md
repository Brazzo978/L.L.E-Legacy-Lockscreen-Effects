# N4 Geometric Mosaic native audit

Date: 2026-07-12

## Provenance

- Canonical native library: Note 4 BOB4 `libsecveGeometricMosaic.so`.
- SHA-256: `A16F926D14396E2C78E50AE48089860BD9B5156FB77ECC99A3E4E7694FE06DD8`.
- The live S4 GT-I9505 library is byte-identical to the canonical Note 4 file.
- `build.ps1` regenerates LLE's transparent version from that verified original through `vendor/native-patches/patch-geometric-mosaic-transparent.ps1`.

## Stock S4 reference

The S4 firmware contains and registers `secvisualeffect` and both Geometric Mosaic native libraries even though its effect picker does not expose the effect. A separate Android 5 harness loads the system shared library and effect ID 1 without bundling a Samsung dex or native library.

- Renderer source confirmed in log: `/system/lib/libsecveGeometricMosaic.so`.
- Standalone harness gesture: `55.72-59.70 fps`; this is not the keyguard cadence.
- Real SystemUI lockscreen selected through `lockscreen_ripple_effect=12`: approximately `30.18-35.79 fps`, with a controlled gesture ending at `32.10 fps`.
- DOWN to dirty: about `1.18 s`.
- UP to dirty: about `0.77 s`.
- Tail capture: clean, with no finished mosaic residue.

LLE therefore paces only `GeometricMosaicRenderer.onDrawFrame()` to `33.333 ms`; the physical display may remain at 60, 90 or 120 Hz. Abstract Tiles retains its separate measured 30 Hz cadence. The real SystemUI path is authoritative over the standalone harness because it includes Samsung's keyguard scheduling and composition.

## Transparent compositing translation

Stock uses straight-alpha blending (`GL_SRC_ALPHA`, `GL_ONE_MINUS_SRC_ALPHA`) and mixes the mosaic over an opaque wallpaper in its final portrait and landscape shaders. LLE must leave the real lockscreen visible beneath a transparent surface.

Let `m = 1 - alpha` be Samsung's intended mosaic coverage. Writing `vec4(c6, m)` into a transparent target produces framebuffer RGB `m*c6` but alpha `m*m`, causing excessive background visibility and halos. LLE instead writes:

```glsl
float m = clamp(1.0 - alpha, 0.0, 1.0);
float a = sqrt(m);
gl_FragColor = vec4(c6 * a, a);
```

After Samsung's unchanged fixed-function blend this stores exactly premultiplied `RGB=m*c6, A=m`, so SurfaceFlinger produces the same final equation as stock: `background*(1-m) + mosaic*m`.

Only the two final shader tails differ. Native ARM code, draw order, masks, blur preparation pass, geometry, colour math and state machine remain original.
