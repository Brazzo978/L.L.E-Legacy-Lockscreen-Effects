# S3 Ripple Native Extraction - Agent 2 - 2026-07-03

Target library:

`C:\Users\Admin\Documents\New project\unlock-effects-test\extracted\s3_system_files\lib\libWaterRipple.so`

Ghidra loads ELF symbols at `0x10000 + ELF symbol value`, so both addresses are listed where useful.

## Constant Table

| Name / Meaning | Exact value / formula | Address / function | Usage |
| --- | --- | --- | --- |
| `refractiveIndex` | `0.93f` default | Java smali `CircleUnlockRippleRenderer`, constructor field init; passed to native `onDraw`, then `Ripple_Render` | GLSL uniform `uRefractiveIndex`; scales height-derived refraction offset. |
| `reflectionRatio` | `0.13f` default | Java smali constructor; passed as `param_14` to `Fluid::Ripple_Render` | Blends water/reflection contribution. Native sets `alphaRatio1 = alphaRatio1Param * reflectionRatio`, `alphaRatio2 = alphaRatio2Param * (1 - reflectionRatio)`. |
| `reflectionRatio` override | `0.2f` | Java smali `setModeleConfiguration()` | Device/profile-specific override. |
| `alphaRatio1` | Java default appears `0.0f`; native uses `alphaRatio1 * reflectionRatio` | `Fluid::Ripple_Render`, Ghidra `0x136c4`, ELF `0x36c4` | GLSL `alphaRatio1` multiplier for water/reflection RGB. |
| `alphaRatio2` | Java default appears `0.0f`; native uses `alphaRatio2 * (1 - reflectionRatio)` | `Fluid::Ripple_Render`, `0x136c4` | GLSL `alphaRatio2`; used mainly gravity shader path and possibly background/water blend variants. |
| `fresnelRatio` | `0.1f` touch default | Java smali constructor; native uniform set in `Ripple_Render` | GLSL: `fresnelRatio * clamp((NdotL - 0.99), 0.0, 0.3)`. |
| `specularRatio` | `0.5f` touch default | Java smali constructor; native uniform set in `Ripple_Render` | GLSL: `specular = clamp(specularRatio * pow(NdotHV, exponent), 1.0, 4.5)`. |
| `specularRatio` override | `1.5f` | Java smali `setModeleConfiguration()` profile branch | Stronger model-specific highlight. |
| `exponent` | `20.0f` touch default | Java smali constructor; native uniform set in `Ripple_Render` | GLSL specular exponent. |
| `exponent` override | `40.0f` | Java smali `setModeleConfiguration()` profile branch | Sharper model-specific specular. |
| Specular clamp | normal `1.0..4.5`; gravity `1.0..5.5` | GLSL strings around file offsets `0xc8a2`, `0xd4dd` | Prevents specular from dropping below `1`, giving the ripple persistent brightness. |
| Light vector | `vec3(5.0, -5.0, 1.0)` | GLSL fragment shader | Used for `NdotL`. |
| Half vector input | `vec4(5.0, -5.0, 1.0, 1.0)` | GLSL vertex shader | Used for `vHalfVec`. |
| Normal Z / height-to-normal scale | `normalize(vec3(n.x, n.y, 0.6))` | GLSL vertex shader offsets around `0xe9fd`, `0xefed` | Height gradient becomes normal with fixed Z `0.6`; lower Z means stronger apparent slope. |
| CPU wave damping | `0.94f` main | Java field `mReductionRate`; JNI `move()` compares damping to `0.94` | Main decay multiplier in `move()`. |
| CPU sub damping | `0.99f` | Java field `mReductionRateSub` | Secondary reduction rate. |
| CPU wave Laplacian coefficient | Passed as first float to `move()`, usually from Java; `move()` also applies extra `0.068` if damping is `0.94`, otherwise `0.018` | JNI `move`, Ghidra `0x1bc04`, ELF `0xbc04` | `next = (next + laplacian * coeff) * damping`, then second Laplacian pass with `0.068` or `0.018`. |
| Height clamp | `[-100.0f, 100.0f]` | JNI `move`, `0x1bc04` | Prevents height blow-up. |
| Ripple injection radius | `3.0f` cone radius, bounds padded by `5` cells | JNI `ripple`, Ghidra `0x1bfe4`, ELF `0xbfe4` | Adds `(3 - distance) * intensity` into height buffer. |
| Touch pressure formula | `pressure > 0 ? pressure^2 + 0.2 : 0.0` | JNI `onTouch`, Ghidra `0x1a8d0`, ELF `0xa8d0` | Used to scale touch ripple injection. |
| Touch injection strength | `TouchPressure * 40.0f` and `TouchPressure * 45.0f` in touch state | `onDraw`, Ghidra `0x19fb0`, ELF `0x9fb0` | Sets native fluid amplitude/radius-like fields during first touch. |
| Drag injection strengths | slow `25.0`, medium `20.0`, fast `30.0` style values in `onDraw` state machine | `onDraw`, `0x19fb0` | Different movement states inject different wave/velocity parameters. |
| `AddInk` radius | `100.0f` initial setting | `Fluid::InitializeSetting`, Ghidra `0x130c4`, field `+0xb4 = 0x43480000` | Passed to `AddInk`; GLSL uniform `Radius`. |
| `AddInk` impulse density | `2.0f` initial setting | `InitializeSetting`, field `+0xb0 = 0x40000000` | GLSL uniform `ImpulseDensity`. |
| `AddInk` shader formula | Line mode: `x += ImpulseDensity * exp(-d*d/(0.8*Radius*Radius))`; point mode: `x += ImpulseDensity/(1.0+d)` | GLSL string offsets around `0xcda2`, `0xce31`; native `AddInk 0x14f60` | Exact density injection. |
| `AddVelocity` formula | Direction is normalized drag vector; length is drag distance; per-pixel shader writes velocity along path | `Fluid::AddVelocity`, Ghidra `0x15398`, ELF `0x5398` | Adds motion velocity into fluid texture. |
| Divergence scale | `0.5 / cellSize` | `ComputeDivergence`, Ghidra `0x14be4`; field `+0x4bc` | Formula: `((top.y + right.x - left.x) - bottom.y) * 0.5 / cellSize`. |
| Fluid cell size | `2.5f` | `InitializeSetting`, field `+0x94 = 0x40200000` | Used by divergence/pressure solve. |
| Ink clear/composite color transform | `ink_color = (1.5 - clearInk) / rgb - 1.0` | `Ripple_Render`, `0x136c4`, ink path | Used by shader output divisor `1.0 + w * ink_color`. |
| Final alpha | `1.0` in original renderer | GLSL normal/gravity fragments | Original is full-screen opaque composition, not transparent overlay. |

## Final Visual Compositing Pseudocode

```glsl
// Vertex stage
height = sampled_or_attribute_height;
n = height_gradient_from_neighbors;
normal = normalize(vec3(n.x, n.y, 0.6));

waterUV = refracted_uv_from_position_height_and_uRefractiveIndex;
vHalfVec = normalize(view_vector + light_half_vector);

// Fragment stage, normal ripple path
waterColor = texture2D(sWaterTexture, waterUV);
bgColor = texture2D(sBGTexture, bgUV);
density = texture2D(Density, gl_FragCoord.xy * Scale);

NdotHV = max(dot(vNormal, vHalfVec), 0.0);
specular = clamp(specularRatio * pow(NdotHV, exponent), 1.0, 4.5);

NdotL = max(dot(vNormal, vec3(5.0, -5.0, 1.0)), 0.0);
fresnel = fresnelRatio * clamp(NdotL - 0.99, 0.0, 0.3);

rippleRGB =
    density_or_height_term
  * specular
  * waterColor.rgb
  * (alphaRatio1 + fresnel)
  + bgColor.rgb;

// Ink mode divides by color-clearing term.
outRGB = rippleRGB / (1.0 + densityWeight * ink_color);
outA = 1.0;
```

## CPU Wave Core

```c
// ripple()
for cell in radius 3 around touch:
    d = distance(cell, touchCell)
    impulse = 3.0 - d
    if impulse > 0:
        height[cell] += impulse * strength

// move()
lap = up - center * 4.0 + left + right + down
velocityOrNext = (velocityOrNext + lap * waveCoeff) * damping

height = clamp(height + velocityOrNext, -100.0, 100.0)

extraCoeff = damping == 0.94f ? 0.068f : 0.018f
height += laplacian(height) * extraCoeff
```

## Transparent Overlay Translation Notes

Original Samsung output is opaque:

```glsl
gl_FragColor = vec4(rippleRGB, 1.0);
```

The original `bgColor` is sampled from the lockscreen wallpaper/background texture. For the accessibility overlay, do not draw that `bgColor` as an opaque layer.

Translate it as a delta effect:

```c
base = liveLockscreenPixel;          // actual screen underneath, conceptual
samsungComposite = originalFormula(bgColor = base);
delta = samsungComposite - base;

waveEnergy = abs(height) + length(heightGradient);
alpha = saturate(waveEnergy * overlayScale);
alpha *= localMaskFromRippleRadiusOrDensity;

overlayRGB = base + delta / max(alpha, epsilon);
drawPremultiplied(overlayRGB, alpha);
```

Practical port rule: use the native formulas for height, normal, specular, Fresnel, and water/reflection sampling, but replace original `bgColor.rgb` with transparent compositing. Alpha should come from local wave energy/gradient/density, not from Samsung's fragment alpha, because Samsung's fragment alpha is always `1.0`.

## Immediate Porting Implications

- The current WIP should not keep lowering physics strength to solve visibility. Physics should move toward the exact native values, while overlay alpha/visibility should be handled separately.
- Key native values to apply next are injection radius `3.0`, height clamp `-100..100`, damping `0.94`, wave coefficient `0.5`, extra smoothing/Laplacian coefficient `0.068`, normal Z `0.6`, specular clamp `1.0..4.5`, and Fresnel/specular/exponent `0.1 / 0.5 / 20.0`.
- Samsung's renderer is full-screen opaque by design. Our faithful port must reproduce the math but convert output to transparent local deltas over the real lockscreen.
