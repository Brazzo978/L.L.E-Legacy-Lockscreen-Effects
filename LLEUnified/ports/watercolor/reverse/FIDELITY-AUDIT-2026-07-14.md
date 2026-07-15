# WaterColor ARM32 -> LLE64 rendering fidelity audit (2026-07-14)

Scope: fresh comparison of `native/watercolor_arm64.c` against
`libsecveWaterColor.so` and `libsecveSrkCommon.so`.  The ARM32 instructions,
shader strings and texture data were checked directly; this file does not rely
on conclusions from the older rendering report.  No runtime source was changed.

## Priority result

| Priority | Area | Current LLE64 | Stock ARM32 evidence | Visual impact | Concrete patch |
|---|---|---|---|---|---|
| P0 | Noise/velocity texture | Uploads the grayscale `watercolor_noise.jpg` directly as RGBA and samples `.xy`. Since R=G for every source pixel, the noise velocity is effectively diagonal, not a 2-D field. | WaterColor `0x6608..0x6d78` does not upload the JPEG directly. It vertically reads the grayscale source, computes a two-component finite-difference gradient, aspect-corrects one component by `0.5625`, encodes `(gradient + 0.5) * 255` into RGB (`B=0`), then uploads a new `GL_RGB` texture of `(sourceWidth+2) x (sourceHeight+2)`. The normalization literal at `0x6b04` is `1/765`; output scale at `0x6b08` is `255`. | Major. Direct grayscale makes `noiseVelocity.xy - 0.5` have equal X/Y components and can turn the brush into a generic diagonal blur instead of irregular watercolor flow. | Preprocess Noise once on load exactly as stock; bind the generated RGB gradient texture as `uVelocity`. Do not bind the raw JPEG. See pseudocode below. |
| P0 | Density seed | Uses a non-stock `uDensityReady` branch on the first advection pass. | WaterColor `0x4abc..0x4bd0`: clears radial and density, then invokes BGAdvect **twice** on the density FBO with radial alpha zero. `0x7304..0x7308` calls this seed before normal rendering. | Major at stroke attack: the first visible stamp starts from a different source/coordinate path in current. | Add an explicit two-pass seed before any visible stamp and after background/context changes. Delete `uDensityReady` from the advect shader and always execute the stock sampling path. |
| P1 | Density feedback topology | Uses two density textures/FBOs and alternates read/write (defined ping-pong). | WaterColor creates only one density FBO/texture (`0x4c44..0x4d00`, fields `+0xa78/+0xa7c`). Every frame binds that FBO, also binds the attached `+0xa7c` texture as `uTexMap`, and draws (`0x33fc..0x3424`). This is same-texture feedback, formally undefined in GLES2. | Potentially major: a correct ping-pong solver is not guaranteed to match the tile/cache feedback observed on the original GPU. | For command-level parity, provide an A/B stock path with one density texture sampled while attached. For deterministic/stable mode retain ping-pong, but label it an intentional emulation rather than 1:1. Seed both ping-pong sides in sequence if stable mode is kept. |
| P1 | Empty-queue tail | Calls `update_stamps`, then always clears radial, advects and writes a fully transparent final frame when count becomes zero. | WaterColor draw branch `0x7110..0x716c` calls radial+advect only when either event vector is non-empty; it still performs final Mix when both are empty. Therefore the first empty frame reuses the previous radial/density textures. | Changes the last fade frame and may create an early disappearance/pop versus the stock retained tail. | When the last event expires, skip radial clear/advection once and run Mix from the previous textures; only clear the transparent surface in the lifecycle step after stock would stop requesting frames. |
| P2 | Radial size precision | Sends float `uSize` directly to centered quad math. | `SPDrawRadialWaterBrush::setSize @ 0x47948` converts both floats to signed integers before rebuilding geometry. | Small edge/coverage difference, magnified slightly by the 2.5% radial FBO. | Use `float sx=(float)(int)stamp->size;` (and same for Y) before the radial quad calculation. |
| P2 | Managed texture wrap | Uploads Mask/Tube/background with `GL_CLAMP_TO_EDGE`. | `SPTextureManager::initializeTextureProperty @ 0x2f1e0` initializes wrap to `0x8370` (`GL_MIRRORED_REPEAT`), filter `0x2601` (`GL_LINEAR`), format RGBA, type UBYTE. WaterColor does not import/call a wrap override. The generated Noise and both FBO textures are explicitly CLAMP/LINEAR. | Usually limited to filtered edges or out-of-range normalized background coordinates. | Use MIRRORED_REPEAT for Mask/Tube/background textures; retain CLAMP_TO_EDGE for generated Noise, radial and density targets. |
| P2 | Background UV normalization | Uses one fixed full-screen UV set; only its advect VS applies a Y flip. | Both BGAdvect and Mix carry independent `aTexUV` and `aTexUVBG`. `setBGTexture @ common 0x3a724/0x3e888` calls `normalizeTextureCoord` on the background UV vector using texture and surface dimensions. | No difference when screenshot and surface aspect are identical; crop/rotation/resolution mismatch otherwise samples a different background pixel and breaks transparent base cancellation. | Carry a separate background UV scale/offset (or second attribute) computed with the stock normalization; do not assume density UV equals background UV after resize/rotation. |
| P3 | Exact radial source | Uses a semantically rewritten shader and guards `length <= 0.0001` by returning zero radial. | Selected source is VS `common 0x51508` (336 bytes) and FS `0x5165c` (1050 bytes); stock calls `normalize(direction) * 0.1` unconditionally and uses separate `uXRatio/uYRatio`. | One center sample can differ; source is not byte-for-byte parity even where output normally matches. | Compile the literal stock VS/FS and use the stock uniform names. The center normalization remains implementation-defined, which is itself stock behavior. |
| P3 | Exact advection source | Adds `uDensityReady`, renames uniforms and rewrites the conditional expression. | Selected VS is `0x4fbe4` (412 bytes), FS `0x4fd84` (1056 bytes). | After seeding the current seeded branch is algebraically close, but compiler precision/instruction selection need not match. | After explicit seed, compile the literal stock shaders and bind the stock names (`uTexMap`, `uVelocity`, `uRadial`, `uOriginal`). |
| Boundary | Final composition | Does not sample/draw the screenshot in Mix; writes premultiplied `vec4(density.rgb*A, A)` to a transparent surface. | Stock Mix VS/FS are `0x507e4`/`0x50958`; stock samples `uTexMap` (background), computes `mix(background,density,A)` and writes an opaque full-screen result. Mix disables GL_BLEND at `common 0x3e664..0x3e66c`. | Deliberate and required in LLE64: copying the screenshot fullscreen would repaint/blacken SystemUI. If the live screen equals the captured background, source-over of premultiplied local color is algebraically equivalent in RGB. | Keep this as the single documented platform boundary. Clear the window to `(0,0,0,0)`, disable GL blend for the direct final write, and output premultiplied RGB. Do not “restore” the opaque stock shader in the overlay. |

## Items verified equivalent

| Area | Stock | Current result |
|---|---|---|
| Radial alignment | Constructor defaults to 0, but WaterColor `0x71f8..0x7200` explicitly calls `setRectAlign(1)`. The center branch at common `0x45da8..0x45e58` builds `(cx +/- width/2, cy +/- height/2)`. | Centered geometry is correct. Do **not** change it to corner anchoring. |
| Radial UVs | Common `createTextureUV @ 0x4796c`: `(0,1),(1,1),(0,0),(1,0)` in triangle-strip vertex order. | The six-triangle `kBrushQuad` plus Y-flipped local UV gives the same interpolation. |
| Radial aspect ratio | Portrait `(1,W/H)`, landscape `(H/W,1)`, square `(1,1)`. | `min/W, min/H` is equivalent. |
| Radial blend | `glEnable(GL_BLEND)` and `glBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)` at common `0x45b10..0x45b28`; common renderer also enables blend at `0x2b124`. | Current corrected separate blend is exact. |
| Radial clear | `(0.5,0.5,0,0)`, color+depth clear at WaterColor `0x3194..0x31ac`. | Color result matches; current omits depth clear, but the FBO has no attached depth buffer. |
| Radial texture channels | FS reads `Mask.a` and `Tube.r`. | ARGB-to-RGBA upload and channel selection are correct. |
| Radial texture units | Mask first, Tube second (units 0 and 1). | Units 0/1 match. |
| Advect formula/constants | Stock displacement multiplier is `0.0175 * 0.006`, recovery mix is `0.03`; setter literals are `125.0` and `18.525`, so scene values 3.4/3.6 upload 425.0/66.69. | Seeded current formula and constants match, apart from raw Noise and `uDensityReady`. |
| Advect units | `uTexMap=0`, `uVelocity=1`, `uRadial=2`, `uOriginal=3` at common `0x3a560..0x3a5fc`. | Same semantic bindings and units. |
| Advect state | Explicit `glDisable(GL_BLEND)` at common `0x3a4d4..0x3a4dc`. | Blend-disabled pass matches. Explicit current depth disable is extra but normally inert. |
| Mix color math | Brightness, weighted magnitude and per-channel saturation formula from FS `0x50958`; scene values are saturation 1.2, RGB weights 1.3/0.4/0.4, brightness 1.35. | Color shaping matches before the intentional transparent-boundary rewrite. |
| Mix state | Stock disables blend at common `0x3e664..0x3e66c`; the following `glBlendFunc(SRC_ALPHA,ONE_MINUS_SRC_ALPHA)` only changes dormant state. | Current disables blend and directly writes premultiplied pixels, which is correct for Android composition. |
| FBO storage | Both stock targets use unsized `GL_RGBA`, `GL_UNSIGNED_BYTE`, LINEAR min/mag and CLAMP_TO_EDGE at WaterColor `0x4990..0x4a48`. | Current format/filter/wrap matches. Stock additionally generates an unused/unattached renderbuffer and does not check completeness; neither changes color output. |
| Nominal FBO scales | Stock constructor chooses radial/density scale by quality mode: `(0.2,1.0)`, `(0.2,0.7)`, `(0.025,0.7)`, or `(0.025,0.6)`. | Current fixed `(0.025,0.6)` is exact only for stock mode 3. If the reference device selected another quality mode this is a real resolution difference. |

## Exact Noise conversion required by P0

Equivalent CPU pseudocode, with the same signs observed in WaterColor `0x6608`:

```c
int gw = src_w + 2, gh = src_h + 2;
float *height = calloc(gw * gh, sizeof(float));
vec2 *gradient = calloc(gw * gh, sizeof(vec2));

// Stock reads source rows bottom-to-top into the upper-left gw-stride field.
// The two extra columns/rows stay zero; the source is not shifted by +1.
for (int y = 0; y < src_h; ++y)
    for (int x = 0; x < src_w; ++x) {
        RGB c = src[(src_h - 1 - y) * src_w + x];
        height[y * gw + x] =
            (c.r + c.g + c.b) * (1.0f / 765.0f);
    }

for (int y = 1; y < src_h - 1; ++y)
    for (int x = 1; x < src_w - 1; ++x) {
        float gx = height[(y + 1) * gw + x] - height[(y - 1) * gw + x];
        float gy = height[y * gw + (x - 1)] - height[y * gw + (x + 1)];
        if (screen_w < screen_h) gy *= 0.5625f;
        else if (screen_h < screen_w) gx *= 0.5625f;
        gradient[y * gw + x] = (vec2){gx, gy};
    }

for (int i = 0; i < gw * gh; ++i) {
    rgb[i].r = (uint8_t)((gradient[i].x + 0.5f) * 255.0f);
    rgb[i].g = (uint8_t)((gradient[i].y + 0.5f) * 255.0f);
    rgb[i].b = 0;
}
// GL_RGB/UNSIGNED_BYTE, LINEAR, CLAMP_TO_EDGE.
```

The generated texture must be rebuilt after an orientation/aspect change,
because stock applies the `0.5625` correction using the current surface
orientation.

## Recommended application order

1. Implement the stock Noise gradient conversion; this is the strongest
   remaining explanation for “transparent blur but no watercolor shape”.
2. Replace `uDensityReady` with the explicit two-draw seed.
3. A/B test stock single-texture feedback against deterministic ping-pong on
   the target 64-bit phone; record GPU/driver because stock behavior is
   undefined by GLES2.
4. Reproduce the empty-queue tail branch.
5. Only then spend time on float-to-int radial size, managed wrap and literal
   shader-source parity.
