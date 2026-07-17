# Geometric Mosaic ARM32: exact render and interaction specification

Status: reverse-engineering specification for the ARM64 reconstruction. This
document describes the original ARM32 engine, except where the transparent LLE
tail is explicitly identified. It is based on the original binary with SHA-256
`A16F926D14396E2C78E50AE48089860BD9B5156FB77ECC99A3E4E7694FE06DD8`, the
Ghidra dumps in this directory, and direct ARM instruction/rodata inspection.

Confidence labels used below:

- **CERTAIN**: literal shader, instruction, constant, field access, or call
  argument recovered from the binary.
- **STRONG INFERENCE**: the wrapper implementation is external, but the value
  is forced by the recovered data flow or output representation.
- **OPEN**: cannot be proved from this `.so` alone and must be validated against
  the original device or the external `vi` renderer implementation.

## 1. Non-negotiable architecture

Geometric Mosaic is not one analytical fragment shader. It is a six-texture,
four-stage composition:

1. Build a low-resolution color-origin texture from the screenshot, once.
2. Every frame, generate a faceted touch mask in a quarter-resolution FBO.
3. Every active frame, render two independent staggered circle lattices into
   two repeat-wrapped FBOs: one with three animated bands, one with two.
4. Composite the screenshot, random alpha mosaic, low-resolution color origin,
   both circle textures, and the touch mask in the final shader.

Replacing stages 2 and 3 with distance functions in the final shader is not
equivalent. It loses the original four-triangle mask quantization, the repeated
off-screen circle lattices, the per-band color-buffer permutation, and the
alpha accumulation performed while rendering the intermediate FBOs.

## 2. Scene resources and exact sampler mapping

The mapping below is **CERTAIN**. It was recovered from the final renderer's
actual `addTexture` calls in ARM code at raw VAs `0x183b8..0x185a0`, not from
the order of declarations in the GLSL source.

| Final sampler | Scene slot / byte offset | Exact source |
|---|---:|---|
| `uBackground` | `scene+0x094` | Source screenshot/background texture |
| `uTextureOrigin` | slot `0x8e`, `scene+0x238` | 12x21 or 21x12 random one-byte alpha texture |
| `uTextureColorOrigin` | slot `0x90`, `scene+0x240` | Low-resolution screenshot color prepass FBO |
| `uTextureCircles` | slot `0x91`, `scene+0x244` | Three-band circle FBO |
| `uTextureAnotherCircles` | slot `0x92`, `scene+0x248` | Two-band circle FBO |
| `uMask` | slot `0x8f`, `scene+0x23c` | Quarter-screen faceted touch-mask FBO |

Critical correction: `uMask` is **not** the 12x21 render target. The 12x21
render target is `uTextureColorOrigin`; the separate 12x21 byte texture is
`uTextureOrigin` and is sampled through `.a`.

### 2.1 Sizes, filter, and wrap

Let `W` and `H` be the current render width and height, `S=min(W,H)`, and
`L=max(W,H)`. Integer divisions below truncate exactly as in the ARM code.

| Resource | Size | Explicit GL state after construction | Use |
|---|---|---|---|
| source / slot at `+0x094` | screenshot size | inherited | screenshot and prepass source |
| random alpha / `0x8e` | portrait `12x21`; landscape `21x12` | constructor flags only | per-block grayscale overlay values |
| mask RT / `0x8f` | `(W >> 2) x (H >> 2)` | `WRAP_S=T=GL_CLAMP_TO_EDGE` | faceted touch coverage |
| color-origin RT / `0x90` | portrait `12x21`; landscape `21x12` | `MIN=MAG=GL_NEAREST`, `WRAP_S=T=GL_CLAMP_TO_EDGE` | blurred/brightened block colors |
| circle3 RT / `0x91` | `((S/12)*18)>>2` by `((L/21)*30)>>2` | `WRAP_S=T=GL_REPEAT` | three circle bands |
| circle2 RT / `0x92` | same as `0x91` | `WRAP_S=T=GL_REPEAT` | two circle bands |

The explicit `glTexParameteri` calls are **CERTAIN**. The `vi` wrapper's
constructor-default min/mag filtering for `0x8f`, `0x91`, `0x92`, and `0x8e`
is **OPEN** because that code is external. Visual intent strongly suggests
linear filtering for the render targets. Do not invent a filter for parity:
capture it with GL interception on ARM32 or recover the `vi` implementation.

The random texture contains exactly one byte per texel and the final shader
reads its `.a`. Therefore its upload format must expose the byte as alpha;
`GL_ALPHA` is a **STRONG INFERENCE** (using `GL_LUMINANCE` would make `.a`
constant 1 and destroy the recovered data path). The constructor's format enum
is the literal value `1`.

The square-aspect uniform used by both circle shaders is:

```text
uSquareRatio = (((L / 21) * 10.0) / ((S / 12) * 6.0))^2
```

The final block sizes are **CERTAIN**:

```text
portrait:  uBlockSizeWidthNormalize  = 1/12
           uBlockSizeHeightNormalize = 1/21
landscape: uBlockSizeWidthNormalize  = 1/21
           uBlockSizeHeightNormalize = 1/12
```

## 3. Random sources and exact palette

The engine calls `srand(time(NULL))` once during construction. `FUN_257f8`
consumes the first 75 `rand()` results to make circle colors. Construction then
consumes 252 more results, writing the low byte of each result sequentially to
the 12x21/21x12 alpha texture. The image is deliberately non-deterministic
between engine instances but stable for the lifetime of one instance.

The circle palette is the following exact 8-bit palette, converted to floats by
division by 255:

| Index | RGB | Hex |
|---:|---:|---:|
| 0 | 132, 112, 255 | `#8470FF` |
| 1 | 173, 255, 47 | `#ADFF2F` |
| 2 | 255, 215, 0 | `#FFD700` |
| 3 | 205, 92, 92 | `#CD5C5C` |
| 4 | 205, 182, 193 | `#CDB6C1` |
| 5 | 131, 11, 255 | `#830BFF` |
| 6 | 67, 205, 128 | `#43CD80` |
| 7 | 255, 192, 192 | `#FFC0C0` |
| 8 | 205, 133, 63 | `#CD853F` |
| 9 | 255, 48, 48 | `#FF3030` |

Each of the three first-lattice color attributes and each of the two
second-lattice color attributes is generated independently. It is not 30 or 28
fully independent choices: each buffer uses 15 independent palette choices and
places them in a seamless repeated pattern.

For one first-lattice attribute, draw independent colors `R0..R14`; its six
rows of five cells are:

```text
R6   R4   R5   R6   R4
R0   R7   R11  R0   R7
R1   R8   R12  R1   R8
R2   R9   R13  R2   R9
R3   R10  R14  R3   R10
R6   R4   R5   R6   R4
```

For one second-lattice attribute, draw a new independent `R0..R14`; its seven
rows of four cells are:

```text
R4  R1   R0   R4
R2  R6   R5   R2
R3  R9   R10  R3
R7  R11  R12  R7
R8  R13  R14  R8
R4  R1   R0   R4
R2  R6   R5   R2
```

This pattern and the total of 75 palette draws are **CERTAIN** from
`FUN_257f8`.

## 4. Exact circle meshes

`FUN_257f8(scene, 6, 10)` builds two clip-space quad lattices. Define:

```text
stepX = 2/6  = 0.3333333333
stepY = 2/10 = 0.2
halfW = stepX * 1.25 = 0.4166666667
halfH = stepY * 1.25 = 0.25
```

For every cell, emit four positions in this exact order and four copies of its
center:

```text
(cx-halfW, cy+halfH)
(cx-halfW, cy-halfH)
(cx+halfW, cy+halfH)
(cx+halfW, cy-halfH)
```

Use indices `0,1,2, 2,1,3` per quad.

### 4.1 First lattice: three-band shader

Thirty cells, in row-major order:

```text
cx = -1.3333333, -0.6666667, 0, 0.6666667, 1.3333333
cy = -1.0, -0.6, -0.2, 0.2, 0.6, 1.0
```

The centers outside `[-1,1]` and the oversized quads are intentional. The
texture is rendered once then repeat-wrapped and sampled at approximately 2x.

### 4.2 Second lattice: two-band shader

Twenty-eight cells, in row-major order:

```text
cx = -1.0, -0.3333333, 0.3333333, 1.0
cy = -1.2, -0.8, -0.4, 0, 0.4, 0.8, 1.2
```

This is the staggered companion lattice. Reusing the first lattice with an
offset is not byte-for-byte equivalent.

## 5. Touch-mask mesh and CPU update

The mask is a `12x21` portrait grid (`21x12` landscape), but it is rendered to
the quarter-screen `0x8f` FBO. Every grid cell is split into **four triangles**
around its center, not into two triangles and not evaluated per final pixel.

For a cell with boundaries `xL,xR,yT,yB` and center `C`, emit:

```text
T_left   = [(xL,yT), C, (xL,yB)]
T_bottom = [(xL,yB), C, (xR,yB)]
T_right  = [(xR,yB), C, (xR,yT)]
T_top    = [(xR,yT), C, (xL,yT)]
```

The CPU evaluates the radial field at the four side midpoints `L,R,B,T`.
For active touch record `j`, at any midpoint `P`:

```text
dx = (P.x - record[j].x) * scene.scaleX   // scene+0xdc
dy = (P.y - record[j].y) * scene.scaleY   // scene+0xe0
sample_j(P) = 1 - sqrt(dx*dx + dy*dy) / record[j].radius
v(P) = max(0, sample_0(P), ..., sample_99(P))
```

The function scans all 100 records and ignores records whose active byte is
zero. `scene.scaleX/Y` is copied from the common engine's aspect fields at
base-scene offsets `+0x3c/+0x40`. `Native_init` in the matching ARM32
`libsecveSrkCommon.so` computes them exactly as:

```text
scene.scaleX = max(W/H, 1)
scene.scaleY = max(H/W, 1)
```

Therefore portrait uses `(1, H/W)` and landscape uses `(W/H, 1)`. This is
**CERTAIN** from common-engine raw VAs `0xf92c..0xf968` and the Geometric
Mosaic copy at raw VAs `0x1752c` and `0x17554` (Ghidra `+0x10000`). The
earlier scene-constructor stores at raw `0x153f8/0x15400` only zero-initialize
the derived fields and are not their final runtime values.

Let the resulting midpoint values be `L,R,B,T`. The per-triangle scalar sent
to the `aTex` attribute is not simply the midpoint value:

```text
A_left   = min(2*L, B+T) * scene.globalAlpha
A_bottom = min(2*B, L+R) * scene.globalAlpha
A_right  = min(2*R, B+T) * scene.globalAlpha
A_top    = min(2*T, L+R) * scene.globalAlpha
```

Each `A_*` is repeated three times, once for every vertex of the corresponding
triangle. This creates the original angular/faceted mask. The mask vertex and
fragment shaders are exactly:

```glsl
precision mediump float;
uniform mat4 uModelViewProjectionMatrix;
attribute vec2 aPos;
attribute float aTex;
varying float alpha;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    alpha = aTex;
}

precision mediump float;
varying float alpha;
void main() {
    gl_FragColor = vec4(1.0, 1.0, 1.0, alpha);
}
```

With standard source-alpha blending over a transparent clear, the stored mask
RGB becomes the intended scalar coverage sampled later by `uMask.x`.

### 5.1 Special `scene+0xe4` radial field

When the special expansion flag is active, `FUN_16084` seeds each side value
before scanning touch records. For side point `P`, center `Q` held in the
engine's two globals, `r=scene+0xf8`, and the same scale factors:

```text
d = length(((P.x-Q.x)*scaleX, (P.y-Q.y)*scaleY))
K = 10.5 / (r + 1.8)^3
special(P) = K * (0.5 - abs(r - d))
```

Active touch records can raise this value through the normal maximum scan. The
formula and constants are **CERTAIN**; the semantic name and lifecycle of the
two global center coordinates remain **OPEN**.

## 6. Touch records and interaction timeline

There are exactly 100 fixed-size records, each `0x24` bytes, plus a free-index
stack initially containing `0..99`. Allocation pops from the end; reclamation
pushes the index back.

| Record offset | Meaning | Exact initialization on a new sample |
|---:|---|---|
| `+0x00` | active byte | `1` |
| `+0x04` | clip-space X | `2*u - 1` |
| `+0x08` | clip-space Y | `2*v - 1` |
| `+0x0c` | animated radius | reset to `0`, animator starts at `0.3` |
| `+0x10` | initial radius key | `0.3` |
| `+0x14` | peak radius key | `0.8` |
| `+0x18` | growth expiry | `now + 0.15 s` |
| `+0x1c` | cleanup time | `0` initially |
| `+0x20` | retreat/cleanup-scheduled byte | `0` initially |

`FUN_1a54c` itself performs no Y inversion: it stores `2*v-1`. The coordinate
convention of the caller is outside this function. An Android top-origin touch
must be transformed in the same place as the ARM32 caller; adding an arbitrary
inversion inside the renderer will reverse the effect.

For non-forced samples, a point is rejected when its clip-space Euclidean
distance from the latest point is below exactly `0.017`. In `[0,1]` normalized
coordinates this is `0.0085`. A point is also rejected when no free record is
available.

New-point radius animation:

```text
0.3 -> 0.8, start now, end now+0.15 s
```

When old trail records expire, records other than the current record animate
their current radius back to `0.3` over `0.6 s`; the current/last record
animates its current radius to `0.0` over `0.6 s`. Both are then reclaimed when
their `cleanupTime` passes. This distinction is visible in `FUN_1b580` and must
not be collapsed to one decay rule.

The animation records carry flag `0x0100`. Key values and absolute times are
**CERTAIN**. The human-readable interpolator name is **OPEN** because the
generic animator is external; observed behavior is consistent with linear
interpolation.

All times are engine-clock seconds. A reconstruction must calculate state from
timestamps, never from frame counts, so the same values work at 60, 120, and
144 Hz.

### 6.1 Unlock/close expansion

`FUN_1dac8` operates on the current active record:

```text
record.radius: current -> 5.0, now -> now+0.45 s
scene.globalAlpha: current -> 0.0, now -> now+0.60 s
record.cleanupScheduled = 1
record.cleanupTime = now + 2.0 s
```

Existing animators targeting those values are cancelled first. Therefore the
visual close is a rapidly expanding touch field combined with a slower global
fade, not a fixed-radius opacity fade.

## 7. Five ring records and band ordering

The five ring records begin at `scene+0x93c`, stride `0x1c`:

```text
+0x00 active byte
+0x04 alpha
+0x08 radius
+0x0c next/end time
+0x10 color-buffer handle/pointer
```

Let `B=scene+0xf0=0.4166666667` (`(2/6)*1.25`). On a fresh trigger the exact
radius keyframes are:

| Ring | Shader group | From | To | Start | End |
|---:|---|---:|---:|---:|---:|
| 0 | circle3 | `0.6B` | `B` | `now` | `now+1.2` |
| 1 | circle3 | `0.2B` | `B` | `now` | `now+2.4` |
| 2 | circle3 | `0` | `B` | `now+0.6` | `now+3.6` |
| 3 | circle2 | `0.6B` | `B` | `now` | `now+1.2` |
| 4 | circle2 | `0` | `B` | `now` | `now+3.0` |

Every frame, each ring alpha is computed exactly as:

```text
alpha = clamp(1.5 - (3*radius)/(2*B), 0, 1)
```

The first touch of a new inactive stroke resets and activates all five rings.
Additional points in the same active stroke do not restart the whole set.

### 7.1 Three-band permutation

The three-band fragment shader tests `C`, then `B`, then `A`, so the normal
timeline binds outer/middle/inner as `A/B/C`. The binary also performs cyclic
permutations of the color buffers and their matching radius/alpha uniforms.
The exact recovered branch mapping is:

```text
if r2 < r1 and r1 < r0:       A=ring0, B=ring1, C=ring2
else if r2 < r1 and r0 < r2:  A=ring1, B=ring2, C=ring0
else:                          A=ring2, B=ring0, C=ring1
```

On the normal keyframes `r0 >= r1 >= r2`, the first mapping is used. When all
radii are equal, the permutation is visually immaterial. This is the literal
branch structure, not a generic sort reconstructed from intent.

### 7.2 Two-band ordering

The two-band group performs a conventional descending-radius bind:

```text
if r3 <= r4: A=ring4, B=ring3
else:        A=ring3, B=ring4
```

The color attribute buffers are swapped together with radius and alpha. Moving
only the uniforms produces incorrect colored bands.

## 8. Shader passes

### 8.1 Color-origin prepass (`0x90`)

This pass runs once after resource construction. It samples the source
screenshot (`uTextureOrigin` in this prepass program, not the final sampler of
the same conceptual name) into the 12x21/21x12 `0x90` target.

For `i,j = -10,-8,...,8,10`, it accumulates 121 samples:

```text
avg = sum(texture(source, UVNorm + (i,j)*sourceTexelSize).rgb) / 121
gray = 0.299*avg.r + 0.587*avg.g + 0.114*avg.b
if gray < 0.2: avg += 0.3
output = vec4(avg, 1)
```

The target is nearest-filtered and clamp-wrapped. Skipping this prepass or
feeding its output to `uTextureOrigin` instead of `uTextureColorOrigin` changes
both brightness and distortion.

### 8.2 Circle3 pass (`0x91`)

Inputs per vertex: `aPosition`, `aCenter`, `aColorA/B/C`. Fragment distance:

```text
d = sqrt((center.x-uv.x)^2 + (center.y-uv.y)^2*uSquareRatio)
if d < radiusC: colorC, alphaC
else if d < radiusB: colorB, alphaB
else if d < radiusA: colorA, alphaA
else discard
```

### 8.3 Circle2 pass (`0x92`)

The same distance formula, with two bands:

```text
if d < radiusB: colorB, alphaB
else if d < radiusA: colorA, alphaA
else discard
```

### 8.4 Final pass

The final vertex shader derives:

```text
UV      = (clipPosition.xy + 1) * 0.5
UVhighp = UV
UVNorm  = aTex
```

The final fragment logic is:

```text
backgroundAlpha = 1 - texture(uMask, UV).x

portrait circleUV  = (UV.x*2, UV.y*2*1.05)
landscape circleUV = (UV.y*2, UV.x*2*1.05)

shift = blockWidth * mod(UVhighp.y, blockHeight) / blockHeight
coordL    = (UVhighp.x + shift, UVhighp.y)
coordR    = (UVhighp.x - shift, UVhighp.y)
coordRInv = (UVhighp.x - shift, 1-UVhighp.y)

colorGrayB    = texture(uTextureOrigin, coordL).a
colorGrayC    = texture(uTextureOrigin, coordRInv).a
colorLayerA   = texture(uTextureColorOrigin, coordL).rgb
colorLayerB   = texture(uTextureColorOrigin, coordR).rgb
colorMosaicC  = texture(uTextureColorOrigin, UVhighp).rgb
circles       = texture(uTextureCircles, circleUV).rgb
otherCircles  = texture(uTextureAnotherCircles, circleUV).rgb

c1 = mix(colorLayerA, linearDodge(colorLayerA, circles), 0.75)
c2 = mix(c1, softLight(c1, otherCircles), 0.40)
c3 = overlay(c2, colorLayerB)
c4 = mix(c3, lighten(c3, colorMosaicC), 0.75)
c5 = mix(c4, overlay(c4, colorGrayB), 0.50)
c6 = mix(c5, overlay(c5, colorGrayC), 0.50)
```

The stock opaque tail is:

```text
backgroundAlpha * background + (1-backgroundAlpha) * vec4(c6,1)
```

The LLE ARM32 transparent patch replaces it in both orientation shaders with:

```glsl
float m = clamp(1.0-backgroundAlpha, 0.0, 1.0);
float a = sqrt(m);
gl_FragColor = vec4(c6*a, a);
// outside the mask: vec4(0.0)
```

## 9. Blend state and why the square root is required

The engine calls `Renderer::setBlending(true)` before these passes. The exact
wrapper implementation is external, but the patch, shader outputs, and opaque
ARM32 result jointly establish standard straight-alpha blending as a **STRONG
INFERENCE**:

```text
glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
```

Intermediate FBOs must be cleared transparent. Both mask and circle shaders
emit non-premultiplied RGB plus an alpha, so standard source-alpha blending
stores the intended alpha-weighted RGB.

For the transparent final pass, let intended mask coverage be `m`. The patched
shader emits `RGB=c6*sqrt(m), A=sqrt(m)`. Standard blending over transparent
stores `RGB=c6*m, A=m`. Using `GL_ONE` for the source factor would instead store
the square-root coverage and visibly over-expand/brighten the effect.

## 10. Exact frame order

Initialization:

1. Seed the RNG with `time(NULL)`.
2. Build both circle meshes and five patterned color buffers (75 palette draws).
3. Create all four render targets.
4. Fill and upload the 252-byte `GL_ALPHA` random mosaic texture.
5. Render the source screenshot through the blur/brighten shader into `0x90`.

Every frame:

1. Enable renderer blending.
2. Advance/retire touch records (`FUN_1b580`).
3. Recompute the four-side values and 12 alpha vertices per mask cell
   (`FUN_16084`), update the VBO.
4. Clear and render the mask mesh to quarter-screen target `0x8f`.
5. Advance the five ring animations and recompute their alphas
   (`FUN_1c8d4`).
6. Permute matching color VBOs/radii/alphas for the three- and two-band
   shaders.
7. While a touch field or special expansion is active, clear and render
   circle3 to `0x91`, then circle2 to `0x92`.
8. Render the final six-texture composition to the default target.

## 11. Known open points requiring an original-device capture

These points must not be silently guessed in a claimed 1:1 port:

1. The `vi` wrapper's constructor-default min/mag filtering for `0x8f`,
   `0x91`, `0x92`, and `0x8e`.
2. The formal name/easing curve represented by animator flag `0x0100`; all
   keyframe values and timestamps are already exact.
3. The external/global center used by the special `scene+0xe4` field and its
   exact lifecycle relative to keyguard hint events.
4. Any GL state retained by the external renderer across `renderFrame` calls
   beyond the explicit state listed here.

Everything else needed for the ordinary touch trail, circle layout, timing,
palette, sampler routing, low-resolution color prepass, final blend chain, and
transparent LLE output is specified above.

## 12. Evidence index

- `FUN_27014`: target construction, texture state, RNG texture, shaders,
  renderer bindings, one-time color prepass.
- `FUN_257f8`: staggered meshes, palette patterns, five ring records.
- `FUN_232ec`: four-triangle-per-cell mask geometry.
- `FUN_1a54c`: record allocation, clip conversion, threshold, growth keys.
- `FUN_1b580`: old/current record retreat and reclamation.
- `FUN_16084`: exact midpoint radial field and triangle smoothing.
- `FUN_1c8d4`: ring radius scheduling and alpha formula.
- `FUN_1d394`: per-frame pass order and ring-buffer permutations.
- `FUN_1dac8`: unlock expansion and global fade.
- raw rodata `0x18c48..0x1ad00`: exact shader strings.
- raw ARM `0x183b8..0x185a0`: authoritative six-sampler object mapping.
