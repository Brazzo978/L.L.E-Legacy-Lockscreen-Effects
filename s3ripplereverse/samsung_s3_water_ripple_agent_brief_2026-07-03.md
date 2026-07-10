# Samsung S3 Water Ripple Agent Brief - 2026-07-03

## Goal
Port the original Samsung Galaxy S3 Water Ripple / `CircleUnlockRippleRenderer`
into `com.codex.lle` as faithfully as possible while keeping the
lockscreen visible through a transparent `TYPE_ACCESSIBILITY_OVERLAY`.

## Important Constraint
The original Samsung renderer is a fullscreen opaque OpenGL renderer. It cannot
be reused directly in the accessibility overlay because it can repaint or blacken
the lockscreen. The target app needs an app-owned transparent renderer that
reproduces the original simulation, mesh, projection, and shader behavior while
emitting only a local overlay contribution.

## Confirmed Samsung Pipeline
- Main Java/smali classes: `CircleUnlockRippleRenderer`, `RippleUnlockView`,
  `JniWaterRippleRender`.
- Native libraries: `libWaterRipple.so`, `libWaterRipple2.so`.
- Rendering path: OpenGL mesh, height/velocity simulation, background texture,
  reflection map, refraction, reflection/specular, not a Canvas bitmap ripple.
- Required texture asset: `reflectionmap.jpg` / `s3_reflectionmap.jpg`.
- Detail grid: `104x104`.
- Draw mesh: `100x100` vertices over a `50x50` world plane.
- Normal damping: `0.94`.
- Wave velocity coefficient: `0.5`.
- Relaxation / extra Laplacian: `0.068`.
- Injection radius: `3`.
- Height clamp: `-100..100`.
- Drag threshold: `150 px`.
- Portrait intensity: `0.5`.
- Landscape intensity: `0.35`.
- Refractive index: `0.93`.
- Reflection ratio: `0.13`.
- Fresnel/specular/exponent: `0.1 / 0.5 / 20`.

## Coordinate Mapping
Java side:

```text
glX = (rawX - screenW / 2 - XAdjust) * XRatio / screenW
glY = (rawY - screenH / 2) * YRatio / screenH
```

Original Java calls the native ripple as:

```text
ripple(glY, glX, intensity, true)
```

Native cell mapping:

```text
cellX = (mx / meshW + 0.5) * detailW
cellY = (my / meshH + 0.5) * detailH
```

## Useful External Research Pointers
- XDA XXLSJ ink/ripple guide:
  `https://xdaforums.com/t/rude-guide-xxlsj-how-to-add-ink-effect-on-jb-samsung-firmware-new-feature-added.2034565/`
- XDA cloudy overlay / ripple lockscreen guide:
  `https://xdaforums.com/t/mod-guide-remove-cloudy-overlay-and-improve-ripple-lockscreen-all-samsung-devices.2471373/`
- 4PDA Samsung framework editing thread:
  `https://4pda.to/forum/index.php?showtopic=251071&st=1100`
- MATCL Korean ink-effect guide:
  `https://www.matcl.com/tip/698987`
- SGH-I317 system dump listing `libWaterRipple.so`:
  `https://gist.github.com/archon810/3686883`
- Samsung Open Source:
  `https://opensource.samsung.com/`
- `esteewhy/whater` is only a generic CPU bitmap ripple reference, not a
  Samsung-equivalent renderer:
  `https://github.com/esteewhy/whater`

## Current Bug Hypothesis
The remaining black halo and wrong UI distortion are likely not from the ripple
simulation anymore. They come from translating Samsung's fullscreen `alpha=1`
fragment output into a transparent overlay:

- If the shader writes a refracted screenshot sample as source color, Android
  alpha-blends a displaced/stale screenshot over the real lockscreen.
- If alpha is derived from raw height/slope instead of actual visible delta,
  low-energy wave tails tint a broad region.
- If the background texture is stale or coordinate-mismatched, that tint becomes
  a dark halo or ghosted UI.

## 2026-07-03 Patch Direction
Keep the app-owned GLES2 mesh renderer. Do not return to the old Canvas path or
native fullscreen wrapper.

Transparent overlay adaptation should:

- Sample a non-refracted base pixel.
- Sample the refracted background pixel separately.
- Compute a signed delta:

```text
delta = refractedBackground + waterReflectionSpecularTerm - baseBackground
```

- Derive overlay alpha from local visible delta energy, not only from raw height.
- Keep premultiplied alpha output for Android composition:

```glsl
gl_FragColor = vec4(src.rgb * alpha, alpha);
```

- Use normal S3 specular clamp `1.0..4.5`; `5.5` belongs to gravity-style path.

## Test Notes
Latest user visual test before this note:

- Animation shape is much better and almost identical to the original.
- Remaining issues are black halo in/around ripple and imperfect UI distortion.
