# G2 Pixelate oracle audit

## Scope and provenance

This audit covers the authorized OptimusDev/XLocker donor APK
`C:\Users\Manu\Downloads\com.optimusdev.pixelate-v1.1.apk`.

- Package/version: `com.optimusdev.pixelate` 1.1.
- APK SHA-256: `564183A65804EF26ACE815AE2C4F50BF17B3EC8D93D06389FFD741CD51BC18C2`.
- Inspection: JADX 1.5.5, output retained outside the repository at
  `C:\Users\Manu\AppData\Local\Temp\lle-pixelate-jadx-20260827`.
- Directly extracted donor shaders: `res/raw/pixelate_vs.glsl` and
  `res/raw/pixelate_fs.glsl`.

The L.L.E. implementation must remain clean-room: do not embed donor Java, GLSL, binary,
or APK code. The three donor audio files are separately authorized material, but importing
them and central sound mapping are outside this tranche.

## Recovered mesh contract

The current L.L.E. WIP (`LgPixelateEffectView`) is not geometrically faithful: it draws a
circular `floor()` pixel field. The donor draws a full-screen `GL_TRIANGLES` mesh.

- Base resolution is `100`; donor creates `hResolution = 100 + 2` rows and
  `wResolution = int(100 * shortSide / longSide) + 2` columns (orientation chooses which
  side is short).
- Every rectangular cell is two triangles, six vertices. Standard UVs map the full source.
- `aTexCoord2` is flat within each triangle. Triangle 1 samples
  `(0.6*u0 + 0.4*u1, 0.6*v0 + 0.4*v1)` and triangle 2 samples
  `(0.6*u1 + 0.4*u0, 0.6*v0 + 0.4*v1)`.
- The fragment shader selects the flat triangular UV only while drag is non-zero;
  otherwise it samples ordinary UV. Its final alpha is `aUserAttrib * uAlpha`.
- The mesh scale is `1 + 5 * clamp01(drag / threshold)`; it is therefore 1..6.

The direct donor source is `a/b/c.java`: `createMesh()` builds the rows/columns and three
vertex streams; `g()` calculates scale, opacity and inverse texture transform. The extracted
fragment shader independently confirms the `uTouch`, `aTexCoord2`, `aUserAttrib` contract.

## Recovered gesture and timing contract

- Down stores the origin; move computes `hypot(current - down)`. The origin never follows
  the finger, and dragging back reduces the effect.
- Donor threshold is supplied by its lockscreen host. L.L.E.'s existing completion threshold
  is 120 dp, so the faithful adaptation passes `120 * density` pixels to the scene.
- `uAlpha` is 1 through `1.5 * threshold`, then decreases linearly to 0 at the display
  diagonal.
- Cancel uses `AccelerateInterpolator` (`t*t`) to retract from current drag to zero in
  300 ms, followed by a 350 ms linear alpha fade.
- Completed unlock accelerates from current drag to screen diagonal in 400 ms.
- The CPU user-mask is radial in the transformed model. For an intersecting first triangle,
  all three vertices receive `distanceSquared / radiusSquared`; for an intersecting second
  triangle all receive `0.5`; non-intersecting triangles receive `1`.

These facts were read from donor `a/b/d.java` (touch and 300/400/350-ms transitions) and
`a/b/c.java` (mask and mesh/shader parameters), not inferred from a preview image.

## Required L.L.E. two-source composition

The source division is explicit and must not be collapsed into one global screenshot:

| Layer | L.L.E. source | Draw scope |
| --- | --- | --- |
| Fixed background/home-underlay | Last screen cache | Full screen, beneath the mesh |
| Effect pixels/triangles | Lock-screen cache | Mosaic mesh only; never a normal full-screen replacement |

`RevolvingGlassEffectView` supplies the now-established local model: primary source is the
lock-screen tile and `SecondaryBackgroundSourceRenderer` carries the independent Last screen.
Pixelate should implement that interface, retain its primary raw-ARGB upload support, accept a
secondary bitmap, and draw the secondary texture first. The lock cache should be sampled only
by the triangular mosaic pass. Do not use the legacy donor's ordinary full-screen lock-image
fallback because it violates the L.L.E. source contract above.

## 2026-08-27 bounded release change

Only `LgPixelateScene.java` and its host test were updated in the release worktree:

- Scene exposes recovered drag, mesh-scale, alpha, accelerated cancel/unlock and finite
  affordance states.
- Compatibility aliases `radiusPx` and `pixelSizePx` remain temporarily so the unreworked
  circular WIP renderer compiles. They are not evidence that a circle is donor-faithful.
- `LgPixelateEffectView.java` was deliberately left untouched when the 1.0.6 release freeze
  began. It still needs the mesh/two-texture renderer described above.

## Open items before visual acceptance

1. Integrate `SecondaryBackgroundSourceRenderer` for Pixelate in the service policy without
   changing the established cache lifetime of other LG effects.
2. Replace the circular fragment shader with prebuilt triangle position/standard-UV/flat-UV
   buffers and per-frame reusable user-mask buffer. Apply center-crop and raw-BGRA correction
   to both UV streams.
3. Confirm on device that Last screen is fully fixed beneath the effect and no unpixelated
   lock cache appears full-screen at down/cancel fade.
4. Add/import the authorized `mosaic_touchdown` and `mosaic_unlock` only with the separate
   sound mapping decision; donor also contains `mosaic_lock`.
5. Capture an ordinary-touch donor/device comparison for final geometric orientation and
   model-space mask alignment; static shader/source evidence fixes the formulas but not a
   modern S23 coordinate-orientation capture.
