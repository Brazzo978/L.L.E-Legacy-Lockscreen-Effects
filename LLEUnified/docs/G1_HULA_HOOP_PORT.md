# LG Hula Hoop V1/V2 port

## V1 and V2 selector

The catalog exposes one `Hula Hoop` card with V1 and V2 choices. V1 remains the default and keeps
the source-backed E975/G3 Color Layered restoration described below. V2 is no longer inferred from
the stock recording: it is a Canvas translation of `FluidicRenderer` and `FluidicCircleObject`
recovered from the LG G4 H81510E Android 5.1 SystemUI. It omits V1's ambient reflections and idle
hint because neither belongs to the G4 Fluidic renderer.

The original V1/G3 sound table maps effect type 5 to `circle_lock.ogg`; the earlier generic
`lg_lock.ogg` placeholder was incorrect. V1 retains that lock sound and its four random
`circle_unlock1-4.ogg` samples. V2 uses the distinct G4 originals `hulahoops_lock.ogg`,
`hulahoops_touchdown.ogg` and `hulahoops_unlock.ogg`.

## Donor identity

The authorized LG E975T10C firmware exposes the effect as `Hula-hoops` in
`LockScreenSettings.apk` and as effect type `5`, `EFFECT_TYPE_COLORLAYERED`, in
`com.lge.lockscreen` 3.0.26013. Effect type 5 inflates
`lockscreen_drag_layer_color_layered` and is implemented by:

- `LockDragLayerColorLayered`
- `ColorLayeredCircleEffect`
- `ColorLayeredBackgroundEffect`
- `ColorLayeredCircleSound`

The Color Layered classes use Canvas, drawables and ObjectAnimator. They contain no JNI method
or native-library load, so L.L.E. translates the behavior into its app-owned Canvas lifecycle
instead of packaging or loading the donor APK/ODEX.

## Restored stock behavior

- Four original translucent layer textures, with 0/90/180/270-degree offsets.
- Per-layer finger-transition factors 1.3/1.2/1.1/0.9.
- Stock 50.2 dp minimum radius and 128 dp drag threshold.
- 700–2500 ms rotation range (fast at the minimum radius, easing slower as it grows) and
  300 ms cancel/return animation.
- Exact `LgeDrawableHolder` transform order: holder translation, radius scale, bitmap centering,
  then rotation around the radius-dependent internal pivot. This keeps all four layers orbiting
  the opening instead of appearing attached outside it.
- Stock layer order 0/1/2/3, common pivot aimed from the original touch point, five-degree pivot
  steering per nominal frame, velocity-gated display-normalized trail and the 160 ms delayed
  tension return toward the fixed opening.
- Linear 600 ms unlock expansion.
- Two-stage ping: 500 ms start delay, 630 ms expansion and 230 ms second-circle delay.
- Original inner ring and three-line outer ring; the outer ring remains at the stock 128 dp
  maximum radius instead of being artificially reduced at touch-down.
- The donor's duplicate unlock icon and ping discs are retained as research assets but
  intentionally not rendered by L.L.E.: the live OEM lockscreen already owns its affordance.
- The accessibility hint reuses the rotating color corona and three-line outer ring, but keeps
  the center transparent and omits Last Screen, the duplicate lock icon and ambient reflections.
- Five stock ambient reflection discs are drawn directly over the live lockscreen without
  repainting its cached bitmap. Their random constrained positions, 0.65-3.0 scale range,
  0.10-0.20 alpha range, forced 3.0-scale member and 2000-4999 ms shared rotation cycle come from
  `ColorLayeredBackgroundEffect`.
- Random selection among the four original `circle_unlock*.ogg` sounds. Touch-down and
  touch-release remain silent, matching `ColorLayeredCircleSound`.
- The generic LG `Lock.ogg` from the same firmware is used for the lock event.

## Restored G4 Fluidic V2 behavior

- The opening remains anchored at ACTION_DOWN. Dragging changes a 100-segment lopsided Hermite
  mesh; it does not translate a circular reveal with the finger.
- Three independent soft bodies reproduce the hole, cyan ring and magenta ring. Their spring uses
  the donor's `KS=0.01`, `KD=0.03` and nominal 16.666 ms integration step.
- Stock 50.199982 dp minimum radius, 15 dp outer-ring stride, 0.2 px/ms stretch threshold,
  five-frame stretch delay and maximum 2:1 outer/inner stretch ratio.
- Slow radial motion returns to a circular body. Fast outward motion stretches the opening toward
  the drag direction while the radius lags behind the drag distance.
- Cyan and magenta begin at randomized stock angles and rotate at randomized signed speeds of
  0.18-0.36 degrees/ms. Their stretch-angle offsets use the original -8 to +8 degree range.
- The original stencil composition is expressed as Canvas path intersections: one-body regions use
  blue/cyan/magenta, two-body overlaps use `COLOR6` purple, and the shared three-body center remains
  transparent so Last Screen is visible.
- Cancel disables soft-body interpolation, closes linearly in 250 ms and hides the two colored
  rings after 80 percent. Unlock expands linearly for 250 ms using the donor's bounce and diagonal
  maximum-radius formulas.
- Original G4 touchdown, unlock and lock sounds are selected only for V2; V1 audio is unchanged.

## L.L.E. source routing

Hula Hoop implements both `BackgroundSourceRenderer` and
`SecondaryBackgroundSourceRenderer`:

1. The ordinary effect-background cache remains available for readiness/fallback, but is not
   repainted fullscreen over the live lockscreen.
2. The independent LG Last Screen cache is revealed through the V1 circle or V2 deformable hole,
   both anchored at the initial touch. Pixels outside the affected paths remain the live lockscreen.
3. V1 finishes its stock 600 ms expansion; V2 finishes its stock 250 ms expansion. Last Screen
   then fills the renderer and remains for another 550 ms without a fade to protect the SystemUI
   handoff.

The effect is ARM64-only, eligible for Random by default, and participates in the existing Fold
profile cache selection and fallback logic.

## Verification

- `LgHulaHoopSceneTest`: 222 host assertions.
- `LgHulaHoopFluidicSceneTest`: 24 host assertions.
- ARM64 tester resource compilation, Java compilation, D8, APK assembly and signing pass.
- APK signature schemes v1, v2 and v3 verify.
- Compiled APK contains only `lib/arm64-v8a` native entries.
- Compiled APK contains the three distinct G4 V2 Hula Hoop audio resources.
