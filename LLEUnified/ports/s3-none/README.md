# S3 None / Circle Unlock reconstruction

## Primary evidence

The renderer is based on the locally preserved Galaxy S3 Neo implementation:

- `KeyguardEffectViewNone.smali`
- `CircleUnlockEffect.smali`
- `CircleUnlockCircle.smali`
- `keyguard_none_arrow.png`
- `keyguard_none_lock_01.png` through `keyguard_none_lock_30.png`

The clear Galaxy S6 Java form of the same Samsung Circle Unlock family was used
only to corroborate animator/listener semantics where the S3 odex decompile had
unresolved synthetic accesses. Product attribution, host dimensions and naming
come from the S3 Neo keyguard.

## Recovered contract

- Reference short side: 1080 px.
- Maximum circle diameter: 576 px.
- Arrow box: 180 px.
- Lock sequence box: 120 px, 30 frames.
- Outer/inner strokes: 4/6 reference pixels.
- Enter: 666 ms, quintic ease-out.
- Incomplete release: 333 ms, quintic ease-out.
- Arrow pulse: alternating 500 ms linear half-cycles, hidden after drag 0.4.
- Affordance: 666 ms enter; exit begins 200 ms before enter completes and lasts
  700 ms with quintic ease-in.
- Drag fill begins outside the minimum radius and reaches 1 at the maximum radius.
- The padlock keyhole is a downward-pointing bulb-and-stem union; the overlapping
  pieces must be combined before cutting the transparent silhouette from the body.
- `white_lockscreen_wallpaper=1` selects `#444444`; otherwise the scene is white.
- Accepted unlock calls the Samsung `unlock()` path, which cancels the circle
  animators rather than inventing an additional terminal flourish.

## L.L.E implementation

`NoneCircleUnlockEffectView` is one transparent Canvas view. It does not request
or retain a colormap, bitmap, GL surface, FBO, native library or effect-local
audio engine. Arrow and padlock imagery are procedurally reconstructed so no
XLocker raster assets are shipped.

Internal effect ID 31 remains stable. The picker label is **S3 None**, and the
tester places it first and permits it in no-colormap mode.
