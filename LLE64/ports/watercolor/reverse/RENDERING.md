# Samsung WaterColor rendering reverse (ARM32 reference)

Date: 2026-07-14  
Scope: rendering, shaders, resources and composition of the original ARM32 WaterColor effect.  
Status: static reverse complete enough to implement the classic WaterColor profile on ARM64. This document does not modify or endorse the current runtime implementation.

## 1. Result in one paragraph

The classic scene is a three-pass GLES2 pipeline. It first stamps a very low-resolution RGBA radial field, then advects a 60%-resolution color/density texture using that radial field plus the noise texture, and finally mixes the cached lockscreen background with the advected density through the radial alpha. The exact classic constructor is `WaterColorComponent(true, 3)`. That profile does **not** select the generic WaterColor mix shader containing `pow(alpha, 4.0) * 0.95` or `uAlphaRatio`; it selects the alternate fragment shader at common-library VA `0x50958`, whose final operation is simply `mix(background, density, alpha)`. The stock Samsung target is opaque. For LLE64's transparent Android overlay, the same visible color is reproduced by emitting premultiplied `(density.rgb * alpha, alpha)` over the live lockscreen, provided the sampled background and the lockscreen below are identical.

## 2. Provenance and hashes

All addresses below are ELF virtual addresses relative to each library image, not process addresses after ASLR. Ghidra imports use image base `0x10000`; for example Ghidra `0x170e0` corresponds to WaterColor ELF VA `0x70e0`.

### Native reference

| File | Size | SHA-256 |
|---|---:|---|
| `reference/arm32-original/native-libs/armeabi-v7a/libsecveWaterColor.so` | 79,060 | `2B00D2590A9C92BFE5461C8890CA1E4F5D8D0A8196B0BA67C9CDB2B35895C2EB` |
| `reference/arm32-original/native-libs/armeabi-v7a/libsecveSrkCommon.so` | 341,296 | `5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36` |

The classic S4 copy and the Note 4 BOB4 copy used for this reverse are byte-identical. `libsecveWaterColor.so` exports `createScene` at `0x11c74`; the scene constructor is at `0xeb90`.

### Original resource containers

| Container | SHA-256 |
|---|---|
| `unlock-effects-test/extracted/s4_system_files/app/secvisualeffect-res/secvisualeffect-res.apk` | `0F69DA468B7CE9BA75E8B1F6402C776A4CC4CEC5F70347E5496EF3275E56D9AC` |
| Note 4 BOB4 `secvisualeffect-res.apk` | `DBC2996BFC603E2C2A0DD62B90CD5514CCD054470282CAA8B4C28934F4A59686` |

The following entries extracted from both APKs are byte-identical to the copies already under `LLE64/res/drawable-nodpi`:

| Logical resource | Dimensions / format | Bytes | SHA-256 |
|---|---|---:|---|
| `waterbrush_tube.png` | 480 x 480, ARGB | 12,799 | `BE1C3AFB734D4AFE04CDBED923F882BC4DA360008A918609F797DC1A447F90F2` |
| `watercolor_mask1.png` | 641 x 655, ARGB | 127,409 | `F0B23FB55C80839616189FB75754A139CF9F09683AC12952548599EED4A3FE1D` |
| `watercolor_mask2.png` | 675 x 733, ARGB | 119,903 | `20803E2C8867284DCE59EE0B7158BE860C4F2BED965EF41AA477863BE26ABAE5` |
| `watercolor_mask3.png` | 803 x 793, ARGB | 119,045 | `D527A9FEB90173A0ADDE3DF1E1CE0299E781FFAC3FD28C968A96FB57A687B8D5` |
| `watercolor_noise.jpg` | 360 x 640, RGB | 20,362 | `01283D870B1D483AF96F99A3343A3D4E459AFE2BF568DBE5C61541F20A1CB642` |

`GLTextureViewRenderer.loadSpecialTexture` requests unscaled drawables from package `com.samsung.android.visualeffect.res` and passes the decoded ARGB integer pixels to native code. There is no Android resource scaling step to reproduce. WaterColor's native aliases are:

| WaterColor rodata VA | Internal name | Android resource name |
|---:|---|---|
| `0x11ee0` | `Mask1` | `watercolor_mask1` |
| `0x11ee8` | `Mask2` | `watercolor_mask2` |
| `0x11ef0` | `Mask3` | `watercolor_mask3` |
| `0x11ef8` | `bg` | live screenshot/background texture |
| `0x11efc` | `Tube` | `waterbrush_tube` |
| `0x11fa4` | `Noise` | `watercolor_noise` |

The external resource strings begin around WaterColor `0x12050` and include `watercolor_mask1`, `waterbrush_tube`, `watercolor_noise`, `watercolor_mask3` and `watercolor_mask2`.

## 3. Object and call-path anchors

The scene initializer thunk at WaterColor `0xcaac` reaches `0xb168`. The latter allocates `0xcf8` bytes and calls the component constructor at `0x5c30` with the exact arguments `(true, 3)`. This constructor profile is the reason the alternate shader family, rather than the first generic strings found in `libsecveSrkCommon.so`, is canonical here.

Useful WaterColor functions:

| VA | Meaning |
|---:|---|
| `0x3448` | clear/reset component state and renderer parameters |
| `0x3140` | radial pass and density/advection pass |
| `0x3668` | enqueue one brush stamp record |
| `0x4990` | allocate the two off-screen framebuffer triplets |
| `0x4abc` | reset/prime off-screen targets |
| `0x5514` | touch/action handler |
| `0x5c30` | WaterColor component constructor |
| `0x70e0` | component draw path and texture wiring |
| `0xa5bc` | scene update/draw/is-empty wrapper |
| `0xb168` | create/initialize the `0xcf8`-byte component |
| `0xeb90` | scene constructor |
| `0x11c74` | exported `createScene` |

The component vtable begins in WaterColor `.data.rel.ro` at `0x13b98`. Relevant slots are `+0x08 -> 0x2b2c` (init/size), `+0x0c -> 0x2ab0` (resize/dirty), `+0x14 -> 0x3a68` (update), `+0x18 -> 0x70e0` (draw), `+0x1c -> 0x4d0c` (command), `+0x20 -> 0x2b6c` (parameters), `+0x24 -> 0x5514` (touch), `+0x38 -> 0x3658 -> 0x3448` (clear/reset) and `+0x3c -> 0x2ad0` (is-empty). Component destructors are `0x9a00`/`0x9d2c`. The scene vtable begins at `0x13c88`; the substantial scene teardown functions are `0xd770`/`0xdbcc`.

Key common-library renderer symbols:

| Common VA | Function |
|---:|---|
| `0x3a410` / `0x3a4c4` | `SPDrawBGAdvectWaterBrush` ctor / draw |
| `0x3dcb4` / `0x3de44` | BG advect create-shader / init |
| `0x3def8` / `0x3df10` | BG scalar setters |
| `0x3e5d0` / `0x3e654` | `SPDrawMixWaterBrush` ctor / draw |
| `0x405d4` / `0x40794` | mix create-shader / init |
| `0x459d0` | radial renderer draw |
| `0x48568` / `0x485fc` | radial create-shader / init |
| `0x48698` | radial time-step setter |
| `0x486a0`/`0x486a8`/`0x486b0` | radial graph/mask texture wiring |
| `0x486d0` / `0x486d8` | radial alpha / aspect ratio setters |
| `0x2f72c` / `0x2f800` / `0x30f1c` | background draw / texture / resize |

## 4. Render targets

`WaterColor +0x4990` creates RGBA8 color textures with `GL_RGBA`, `GL_UNSIGNED_BYTE`, `GL_LINEAR` min/mag filtering and `GL_CLAMP_TO_EDGE`. Each is attached at `GL_COLOR_ATTACHMENT0`.

| Component offsets | Purpose | Size |
|---|---|---|
| `a6c` FBO, `a70` texture, `a74` renderbuffer | radial direction/tube/alpha field | `floor(width * 0.025)` x `floor(height * 0.025)` |
| `a78` FBO, `a7c` texture, `a80` renderbuffer | advected color/density | `floor(width * 0.6)` x `floor(height * 0.6)` |

The code generates and binds the renderbuffer objects, but this reverse did not observe a matching storage allocation or attachment call. Color rendering therefore depends only on the RGBA texture attachment; do not invent a depth dependency in the ARM64 port.

The radial target is cleared to `(0.5, 0.5, 0.0, 0.0)`, not transparent black. This is essential: radial R/G encode a signed 2-D vector with zero centered at 0.5. The clear mask is `0x4100` (`GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT`), although the observed FBO has no useful depth attachment.

### Density feedback caveat

The ARM32 path binds `a78`, whose color attachment is `a7c`, and at the same time supplies `a7c` to the advect shader as `uTexMap`. Sampling a texture while rendering into the same texture is framebuffer feedback and is undefined in GLES. The original appears to rely on behavior of its contemporary Samsung/Mali driver, most likely reading the previous contents.

For deterministic ARM64 behavior, allocate two 60%-resolution density textures and ping-pong them: sample previous density, render next density, then swap. This is a deliberate compatibility repair for undefined behavior, not a change to the intended recurrence. Keep an optional one-texture diagnostic path only for comparison on the legacy device.

## 5. Exact frame order

The active draw path is WaterColor `0x70e0`, with the off-screen work in `0x3140`:

1. Set the viewport to the 2.5%-resolution radial target.
2. Bind FBO `a6c` and clear it to `(0.5, 0.5, 0, 0)`.
3. Draw all queued radial brush quads into texture `a70`.
4. Set the viewport to the 60%-resolution density target.
5. Bind FBO `a78` and wire advect inputs: previous density `a7c`, noise `a84`, radial field `a70`, original background `bg`.
6. Draw the full-screen advect quad into the density target.
7. Restore the full display viewport and bind framebuffer 0.
8. Wire mix alpha=`a70`, density=`a7c`, background=`bg` and draw the full-screen mix quad.

The reset/prime path at WaterColor `0x4abc` clears both off-screen targets, executes the background advect renderer twice into the density target, restores the default viewport and updates the component state. With radial alpha initially zero, the advect shader copies `uOriginal`, so the density texture becomes an opaque background before the first visible stroke.

On the initial dirty flag (`component+0xcf4`), `0x70e0` also calls `SPDrawBackground` once with texture `bg`; this is an opaque stock bootstrap. It must not be reproduced as a fullscreen draw into LLE64's transparent overlay.

## 6. Shader selection evidence

`libsecveSrkCommon.so` contains multiple WaterColor shader strings. The call sites, PC-relative literals and exact string-copy lengths identify the family used by `(true, 3)`:

| Stage | Vertex shader VA / copied bytes | Fragment shader VA / copied bytes | Selector |
|---|---|---|---|
| BG advect | `0x4fbe4` / `0x19d` (412-char source) | `0x4fd84` / `0x421` (1056-char source) | `createRectTextureShader @ 0x3dcb4` |
| final mix | `0x507e4` / `0x171` (368-char source) | `0x50958` / `0x45f` (1118-char source) | `createRectTextureShader @ 0x405d4` |
| radial tube | `0x51508` / `0x151` (336-char source) | `0x5165c` / `0x41b` (1050-char source) | `createRectTextureShader @ 0x48568` |

The generic mix fragment shader around `0x50348`, containing `pow(alpha, 4.0) * 0.95` and `uAlphaRatio`, exists but is **not selected** in the classic call path. Any implementation using that formula is a different profile, not a 1:1 port of this scene.

### 6.1 BG advect shader

The selected vertex shader flips Y independently for both texture-coordinate sets:

```glsl
vTexUV   = vec2(aTexUV.x,   1.0 - aTexUV.y);
vTexUVBG = vec2(aTexUVBG.x, 1.0 - aTexUVBG.y);
```

Equivalent selected fragment logic at common `0x4fd84`:

```glsl
vec4 radial = texture2D(uRadial, vTexUV);
vec4 color;
if (radial.a != 0.0) {
    vec4 velocity = texture2D(uVelocity, vTexUVBG);
    vec2 densityUV = vTexUV
        + ((radial.xy - 0.5) * 10.0 * uRadialVectorScalar
        +  (velocity.xy - 0.5) * uNoiseVectorScalar)
        * radial.b * 0.0175 * 0.006;
    color = mix(texture2D(uTexMap, densityUV),
                texture2D(uOriginal, vTexUVBG), 0.03);
} else {
    color = texture2D(uOriginal, vTexUVBG);
}
gl_FragColor = color;
```

Important details:

- The branch tests exact non-zero radial alpha.
- Noise affects the sample only inside the radial mask.
- The recurrence relaxes toward the original background by 3% per simulation step.
- `radial.b`, sourced from the Tube texture, scales both radial and noise displacement.
- Do not algebraically remove the literal factors if bit-level comparison is desired; preserve operation order and mediump behavior first.

### 6.2 Final mix shader

Equivalent selected fragment logic at common `0x50958`:

```glsl
vec4 background = texture2D(uTexMap, vTexUVBG);
vec4 density = texture2D(uDensity, vTexUV) * uBrightness;

float p = sqrt(density.r * density.r * uRedSaturation
             + density.g * density.g * uGreenSaturation
             + density.b * density.b * uBlueSaturation);

density.r = p + (density.r - p) * uSaturation;
density.g = p + (density.g - p) * uSaturation;
density.b = p + (density.b - p) * uSaturation;

float alpha = texture2D(uAlpha, vTexUV).a;
gl_FragColor = mix(background, density, alpha);
```

The selected mix VS is a dual-UV fullscreen shader, but unlike BG advect it does **not** flip Y:

```glsl
vTexUV   = aTexUV.st;
vTexUVBG = aTexUVBG.st;
```

Do not reuse the advect VS for this pass. Both primed density and background are opaque, so the stock final alpha is 1 even where the brush alpha is zero.

### 6.3 Radial tube shader

Equivalent selected fragment logic at common `0x5165c`:

```glsl
vec4 mask = texture2D(uMask, vTexUV);
vec4 tube = texture2D(uTube, vTexUV);

vec2 direction = center - vPosition;
vec2 radial = normalize(direction) * 0.1;
radial.x *= uXRatio;
radial.y *= uYRatio;
radial += 0.5;

gl_FragColor = vec4(radial.xy,
                    uTimeStep * tube.r,
                    mask.a * clamp(1.0 - uAlpha, 0.0, 1.0));
```

`uPow` and `uMultiply` are declared by this source family but unused in the selected fragment expression. Keep them out of the ARM64 shader unless uniform-layout compatibility with an existing wrapper requires the names.

Every queued radial stamp uses Tube. It is not a MOVE-only modifier: the touch handler at WaterColor `0x5514` explicitly enqueues stamps for Android action 0 (DOWN) and action 1 (UP), and the hover/interpolated path at action 7 enqueues Tube-backed stamps as well. ACTION_MOVE (2) updates the accepted path point; its generated/consumed stamps still go through the same radial renderer. The three mask textures are selected per stamp type, but Tube remains the graph texture for all of them.

## 7. Texture units and uniform values

### BG advect draw (`common +0x3a4c4`)

| Unit / object field | Uniform | Scene input |
|---|---|---|
| unit 0 / `+0x394` | `uTexMap` | prior density, WaterColor `a7c` |
| unit 1 / `+0x398` | `uVelocity` | `Noise`, WaterColor `a84` |
| unit 2 / `+0x39c` | `uRadial` | radial target, WaterColor `a70` |
| unit 3 / `+0x3a0` | `uOriginal` | cached screenshot texture `bg` |
| field `+0x3b0` | `uNoiseVectorScalar` | effective 425.0 |
| field `+0x3b4` | `uRadialVectorScalar` | effective 66.69 |

The scene supplies nominal values 3.4 and 3.6, but the common setters scale them before upload:

- `setNoiseVectorScalar`: `3.4 * 125.0 = 425.0` (`common +0x3df0c`).
- `setRadialVectorScalar`: `3.6 * 18.525 = 66.69` (`common +0x3df24`).

Uploading 3.4/3.6 directly is therefore not faithful.

### Mix draw (`common +0x3e654`)

| Unit / object field | Uniform | Scene input |
|---|---|---|
| unit 0 / `+0x394` | `uTexMap` | cached background `bg` |
| unit 1 / `+0x39c` | `uDensity` | advected density `a7c` |
| unit 2 / `+0x398` | `uAlpha` | radial alpha `a70` |
| uniform slots 6..10 | saturation, brightness, R/G/B weights | profile 3 constants below |

The classic `(true, 3)` profile sets:

| Parameter | Value |
|---|---:|
| brightness | `1.35` |
| saturation | `1.2` |
| red saturation weight | `1.3` |
| green saturation weight | `0.4` |
| blue saturation weight | `0.4` |
| radial time step | `0.9` |

The constructor stores the color controls around component `+0xcdc..+0xcec`, and reset `0x3448` passes them into `SPDrawMixWaterBrush`. The radial aspect ratios are normalized for the current orientation so the shorter axis is the reference; known sizing constants near WaterColor `0x364c`, `0x3650` and `0x3654` are `0.175`, `0.35` and approximately `0.0984375`.

## 8. Geometry and GL state

All three rendering stages use four-vertex quads.

- BG advect and mix call `glDrawElements(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_SHORT, indices)` at common `0x3a68c` and `0x3e83c`.
- Their vertex input is separate vec3 arrays for position, effect UV and background UV, with 12-byte stride per array.
- Radial and background rendering use the generic `SPIRenderer` path, option `GL_TRIANGLE_STRIP`, four unsigned-short indices.
- The simple background fragment shader around common `0x4c918` is `texture2D(uTexMap, vTexUV) * uColor`.

Blend behavior is easy to misread because state is split across the generic renderer and the WaterColor draw objects:

| Pass | Observed state | Consequence |
|---|---|---|
| radial stamp | generic `SPIRenderer::draw @ 0x2b0f8` disables only `GL_DEPTH_TEST` at `0x2b120`, then enables `GL_BLEND` at `0x2b124` and calls `glBlendFuncSeparate` at `0x2b134` | radial quads accumulate; transparent mask texels preserve earlier stamps |
| radial virtual draw | `SPDrawRadialWaterBrush::drawRender @ 0x459d0` again enables blend at `0x45b10` and programs `glBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)` at `0x45b28` immediately before the indexed draw | RGB uses source-over and alpha uses union/source-over accumulation |
| BG advect | explicitly calls `glDisable(GL_BLEND)` at `0x3a4d4..0x3a4dc` | fullscreen advect directly replaces the density target |
| final mix | explicitly calls `glDisable(GL_BLEND)` at `0x3e664..0x3e66c`; the following `glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)` only updates dormant state | stock and patched final shaders directly replace the current output buffer |

For one radial fragment `S` over the existing radial target `D`, the exact separate factors give:

```text
RGBout = S.rgb * S.a + D.rgb * (1 - S.a)
Aout   = S.a         + D.a   * (1 - S.a)
```

This is not additive color blending, but it does accumulate the mask coverage and preserve the trail. Disabling blend is not equivalent: because the radial shader still writes RG/B where `Mask.a == 0`, a later quad would erase earlier alpha and field data across its transparent texels.

## 9. Stock compositing versus LLE64 overlay compositing

Let:

- `B` be the cached lockscreen background sampled by `uTexMap`.
- `D` be the brightness/saturation-adjusted advected density.
- `A` be radial texture alpha.

The selected Samsung shader produces:

```text
C_stock = B * (1 - A) + D * A
alpha_stock = 1
```

Samsung could draw this fullscreen because its original effect owned the lockscreen rendering surface. LLE64 must leave the real lockscreen visible outside the local effect. If the Android layer underneath already displays the same `B`, the exact premultiplied overlay is:

```text
overlay.rgb = D * A
overlay.a   = A
compositor result = D * A + B * (1 - A) = C_stock
```

This identity is the correct transparent translation. It is exact only when the live pixels below match the screenshot texture `B` coordinate-for-coordinate and use compatible color space/transfer behavior. A stale screenshot, a changed notification/AOD state, a crop mismatch or a different wallpaper transform breaks cancellation and produces halos or color jumps.

### Conditional double-alpha trap

The transparent patch strategy changes the shader to the equivalent of:

```glsl
gl_FragColor = vec4(density.rgb, 1.0) * alpha;
```

That output is already premultiplied. The original `SPDrawMixWaterBrush::draw @ 0x3e654` disables blending before the quad, so a shader-only premultiplied patch is a direct write and does **not** double-apply alpha. A double-alpha error occurs only if a replacement renderer enables `GL_BLEND` with `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` while drawing that premultiplied output:

```text
stored RGB ~= D * A^2
stored A   ~= A^2
```

For a faithful ARM64 renderer, choose one consistent final-boundary strategy:

1. Preferred: shader outputs `vec4(D * A, A)` and blending is disabled for the final quad.
2. Equivalent premultiplied blend: shader outputs `vec4(D * A, A)` and uses `glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)` on a transparent target.
3. Straight shader alternative: output `vec4(D, A)` and use `glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`.

Do not render `B` fullscreen into the transparent overlay. `B` is needed as a simulation/color-map input, not as visible overlay content.

## 10. ARM64 implementation blueprint

1. Reuse the five verified original assets byte-for-byte and decode them without density scaling.
2. Use an app-owned GLES2 renderer; no Samsung ARM32 object layout or STL ABI should cross into this implementation.
3. Create one 2.5%-resolution RGBA8 radial texture/FBO and two 60%-resolution RGBA8 density texture/FBO pairs for deterministic ping-pong.
4. Use `GL_LINEAR` and `GL_CLAMP_TO_EDGE` on these targets, matching the ARM32 setup.
5. On reset, clear radial to `(0.5,0.5,0,0)`, clear density targets, and prime the density state from the current background.
6. For each simulation tick, clear radial and draw queued four-vertex Tube/mask quads in original queue order with `glEnable(GL_BLEND)` and `glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`.
7. Run the exact selected advect formula with effective scalar uniforms 425.0 and 66.69; read previous density and write next density.
8. Run the exact selected brightness/saturation formula with profile values 1.35, 1.2, 1.3, 0.4, 0.4.
9. Translate only the final boundary to premultiplied transparent output `(D*A, A)`; never draw the screenshot/background fullscreen in the overlay.
10. Advance simulation at the stock cadence, effectively 60 Hz. On 120/144 Hz panels, render/present as needed but do not execute two or more WaterColor updates per stock tick. On a 60 Hz power-saving transition, the same simulation clock must continue without a speed change.
11. Refresh or invalidate the background texture when the underlying lockscreen geometry/content changes, and suppress visible output until the sample is aligned.

The fixed-step point is important because the advection has a hardcoded 3% relaxation per update and multiple other quantities are frame-stepped. Driving the same shader at 120 Hz doubles the apparent diffusion/relaxation speed even if touch coordinates are correct.

## 11. Fidelity validation checklist

- Verify all resource hashes above in the packaged ARM64 APK.
- Confirm radial clear bytes correspond to approximately `(128,128,0,0)` in RGBA8.
- Capture one radial target and check R/G are centered on 0.5, B follows `0.9 * Tube.r`, and A follows mask alpha/fade.
- Confirm DOWN and UP endpoints contain Tube-backed stamps; do not restrict Tube to MOVE.
- Confirm radial stamps use the exact separate source-over factors: overlapping alpha accumulates and zero-alpha mask texels leave earlier stamps unchanged.
- Confirm selected classic mix has no `pow`, `0.95`, or `uAlphaRatio` use.
- Confirm noise/radial uniforms arriving at the shader are 425.0 and 66.69, not 3.4 and 3.6.
- Compare 60-frame sequences at fixed input points, not only still frames; density advection is temporal.
- Test portrait and landscape Y flips and brush aspect normalization.
- Test at display modes 60, 120 and 144 Hz while keeping the simulation at 60 updates/s.
- Clear the Android output layer transparent and inspect GPU capture: outside `A`, RGBA must be zero.
- On a transparent-buffer test, ensure half alpha stores approximately `D*0.5` and alpha `0.5`, never `D*0.25`/`0.25`.
- Compare the legacy single-texture feedback result against the ARM64 ping-pong result on the reference device; document any driver-specific divergence.
- Validate screenshot alignment after AOD exit, notification changes, rotation and resolution/power-mode changes.

## 12. Confidence and remaining unknowns

High confidence: classic constructor profile, exact selected shader strings and copy lengths, FBO dimensions/formats, texture-unit mapping, pass order, resource hashes, core profile constants, final stock formula and blend calls.

Medium confidence: the precise legacy GPU result of the illegal density read/write feedback. The intended recurrence is clear, but GLES does not define what a modern driver must return. Ping-pong is the safest faithful interpretation.

The transparent final boundary is necessarily an LLE64 adaptation. It is mathematically identical to the stock opaque mix when the live layer below equals the cached background; it cannot be universally identical if SystemUI changes after capture. This constraint should be treated as part of renderer lifecycle design, not hidden by drawing the screenshot fullscreen.
