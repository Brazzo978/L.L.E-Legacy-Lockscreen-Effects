# Ripple and Watercolor open research notes - 2026-07-05

This file records the current conclusions for future work. Do not treat Ripple
or Watercolor as complete ports yet.

## Current status

- N5 Colored Droplet: visually close and usable. Remaining known weak point is
  the lockscreen screenshot timing/cache path; if the screenshot is late or
  stale the refraction/color can be wrong.
- N5 Sparkling Bubbles: visually close and usable after native transparency and
  background draw fixes.
- N4/N3 Watercolor: incomplete. The active renderer is a safe WIP, not an exact
  Samsung port.
- S3 Ripple: incomplete. It is app-owned and overlay-safe, but still does not
  reproduce the original fullscreen refraction behavior exactly.
- S4 Ripple native/RippleInk: useful as a reference/test bed only. It is not a
  good final overlay solution in its direct native form.

## Why Note5 effects worked better

Colour Droplet and Sparkling Bubbles have a local effect mask in the original
native path:

- Droplet has density/edge fields around the droplet.
- Sparkling has particle alpha and mask texture alpha.

That means the app can keep Android overlay transparency simple:

- outside the local effect: alpha 0 or discard
- inside the local effect: use the lockscreen screenshot as a texture for color
  and refraction

Ripple is different. Samsung Ripple/S3/S4 treats the whole lockscreen as a
water surface. In the stock keyguard this is correct because the renderer
reconstructs the full screen.

## S4 RippleInk native findings

Relevant paths:

- `extracted/s4_system_files/lib/libsecveRippleInk.so`
- `extracted/secvisualeffect_hybrid_smali/com/samsung/android/visualeffect/lock/rippleink/`
- `_decompiled_old_native32/sources/com/samsung/android/visualeffect/lock/common/GLTextureViewRendererForRippleType.java`
- `_decompiled_old_native32/sources/com/samsung/android/visualeffect/lock/rippleink/RippleInkRenderer.java`

Wrapper behavior:

- Samsung effect id is `0x08` (`RIPPLE_INK`).
- `RippleInkData` contains `windowWidth`, `windowHeight`, and
  `reflectionBitmap`.
- The keyguard wrapper passes drawable `reflectionmap` as the environment /
  water reflection bitmap.
- `handleCustomEvent(0, {"Bitmap": bitmap})` loads the lockscreen screenshot as
  the background texture.
- `handleCustomEvent(1, {"StartDelay", "Rect"})` triggers the center affordance.

Native shader behavior:

```glsl
waterColor = texture2D(sWaterTexture, vWaterTextureCoord);
bgColor = texture2D(sBGTexture, vBGTexture1Coord);
t = clamp(abs(vHeights), 0.0, 1.13);
specular = clamp(specularRatio * pow(NdotHV, exponent), 1.0, 5.5);
rippleRGB = t * specular * waterColor.rgb * samsungLight + bgColor.rgb;
gl_FragColor = vec4(rippleRGB, 1.0);
```

Important implication: the original final color is fullscreen and opaque. It is
not an overlay particle/glyph. It redraws the lockscreen.

## Patch attempts and results

The native S4 test was added as a separate effect so the S3 primary path is not
replaced.

Attempts:

- Remove `bgColor.rgb` from the shader and output only water/specular:
  the ripple becomes too dark because the lockscreen refraction contribution is
  gone.
- Keep `+ bgColor.rgb` but set alpha from `t`:
  the screenshot becomes a bright/washed veil over the whole lockscreen, because
  the renderer still writes screenshot color over a wide surface.
- Change `+ bgColor.rgb` to `* bgColor.rgb`:
  the fullscreen veil is reduced, but the ripple becomes dark again because the
  screenshot often has low luminance and multiplies down the water/specular
  light.
- Simple alpha such as `min(t, .4)` is too crude:
  residual low wave energy over the large mesh can still create a fullscreen
  haze, while stricter alpha makes the visible ripple disappear.

Conclusion: patching one or two GLSL operations inside `libsecveRippleInk.so`
is not enough for a faithful transparent overlay. The missing piece is a
stable local mask.

## Why Indigo/S5 Ripple variants do not solve it

RippleInk, S5 RippleInk, Indigo Diffusion, and Ripple Ink variants appear to use
the same fullscreen-water model. Indigo changes effect mode/color behavior, but
does not provide a local mask like Droplet or Sparkling.

Practical conclusion: searching for a newer RippleInk native with a built-in
mask is unlikely to be the fastest path.

## Correct overlay compositing target

The overlay renderer should not draw the raw screenshot as a layer. It should
draw only the local difference caused by the wave.

Target model:

```glsl
mask = wave energy or normal/gradient strength;
if (mask is almost zero) discard;

base = sample screenshot at normal coordinate;
refracted = sample screenshot at refracted coordinate;
highlight = Samsung water/reflection/specular contribution;

delta = refracted - base;
out.rgb = delta + highlight;
out.a = mask;
```

The exact implementation may premultiply RGB by alpha depending on the GL blend
state. The key is that pixels outside the wave must output alpha 0, not the
current screenshot.

Possible mask sources:

- `t = abs(vHeights)` with a high threshold and smoothstep
- `length(vNormal.xy)` / slope strength, likely better than raw height
- combined height + slope + clip region around the active touch/hint
- dirty/scissor rect to reduce the maximum area that can haze

## Recommended future strategies

### 1. Improve current S3 app-owned renderer first

This is the lowest-risk useful next step.

Use S4/RippleInk data to improve `S3RippleMeshEffectView`:

- mesh/detail profile around `104x104` detail / `100x100` rendered inner mesh
- damping/reduction `0.94`
- `refractiveIndex = 0.93`
- `reflectionRatio = 0.13`
- `fresnel/specular/exponent = 0.1 / 0.5 / 20`
- drag threshold around `150px`
- down/move/up/hint impulse behavior from `GLTextureViewRendererForRippleType`
- alpha mask from slope/normal strength, not just raw height
- dual screenshot sampling: base sample and refracted sample

Goal: make the current primary ripple better without risking the rest of the
effect picker.

### 2. Build a separate S4RippleMeshEffectView

This is the most correct long-term path.

Use `S3RippleMeshEffectView` as the structural base:

- transparent `TextureView`
- app-owned GL thread
- cached screenshot as `sBGTexture`
- `s3_reflectionmap.jpg` / `reflectionmap` as `sWaterTexture`
- same unlock gesture lifecycle and hint hook
- no opaque background draw

Then copy S4/RippleInk behavior:

- S4 touch mapping/orientation math
- S4 mesh/projection constants
- S4 ripple/move behavior where feasible
- Samsung shader formulas before final compositing

Do not replace S3 with this. Add it as a separate effect until it is proven.

### 3. Keep S4 native wrapper only as reference

The direct native path is useful to compare:

- timing
- affordance behavior
- touch mapping
- reflection map use
- relative light/specular look

It is not recommended as production output unless a much more invasive native
patch is done. Binary GLSL string length makes complex shader changes fragile.

### 4. Watercolor remains a separate exact-port project

Watercolor is also incomplete, but for a different reason:

- Direct Samsung wrapper/full GL host broke or blackened the overlay flow.
- The WIP safe renderer is not visually exact.
- The future exact path is app-owned GLES/FBO, not a direct full-screen native
  wrapper.
- Required future work remains reverse of `libsecveWaterColor.so` constructor,
  vtable, pass ordering, shader constants, FBO lifecycle, and clear/empty logic.

## Do not repeat

- Do not present `S4RippleEffectView` direct native wrapper as the final ripple
  solution.
- Do not route Watercolor back to direct Samsung wrapper or direct GLSurfaceView
  overlay.
- Do not use the lockscreen screenshot as an opaque fullscreen fallback layer.
- Do not judge Ripple screenshot support only by color brightness; the real
  missing feature is a local mask/delta compositing model.
- Do not merge a future S4/S5 Ripple experiment over the S3 primary slot until
  it is tested side by side.

## Suggested future test order

1. Restore/keep the last stable tested app state.
2. Add one experimental picker slot only.
3. Implement dual-sample local refraction in app-owned S3 mesh.
4. Tune mask from slope/normal strength with a debug view:
   - raw height
   - slope/normal alpha
   - refracted screenshot only
   - final premultiplied output
5. If S3 improves, fork the renderer into `S4RippleMeshEffectView` and import
   S4-specific parameters/touch mapping.
6. Only after that revisit native `libsecveRippleInk.so` patching as a reference
   or validation tool.
