# Geometric Mosaic ARM64: literal shader and binding audit

Audit target: `src/com/codex/lle/GeometricMosaicGlesPipeline.java`, 1,099-line
snapshot with SHA-256
`0E4D3F48D07976AF12A744EC449AD2DB0AF09A3DED86CDF2D471917C1648C684`.

ARM32 authority:

- stock `vendor/original-native/libsecveGeometricMosaic.so`, SHA-256
  `A16F926D14396E2C78E50AE48089860BD9B5156FB77ECC99A3E4E7694FE06DD8`;
- literal NUL-terminated GLSL at raw file offsets `0x18c48..0x1b35b`;
- current ARM32 transparent build artifact
  `build/armeabi-v7a/patched-geometric-mosaic/libsecveGeometricMosaic.so`,
  SHA-256
  `CB96C86E71878AB370F7213492290472C1AF6C4799302E0FA164FAB555529817`;
- constructor/binding decompile `GHIDRA-VTABLE-FORCED.txt`, especially
  `FUN_00027014`.

This audit deliberately excludes the already-corrected mask topology, touch
scale, record physics and circle mesh placement. It asks only whether the Java
shader sources, vertex layouts and program bindings reproduce the literal
ARM32 render programs. “Equivalent” means the difference cannot change valid
finite output. “Precision-sensitive” means the formula is mathematically the
same but can quantize or round at a different point on an ES 2.0 GPU.

## Result and patch priority

No remaining shader error explains the old grossly oversized touch footprint.
The six-layer color chain, sampler routing, orientation formulas and LLE
transparent square-root boundary are correct. The remaining ordinary-path
shader gap is concentrated in precision and literal program layout:

1. **P1 — restore the ARM32 circle varying precision.** Java interpolates
   position/center as `highp` and colors as `lowp`; ARM32 uses `mediump` for all
   five circle varyings. This can move a band edge and slightly quantize the
   8-bit palette.
2. **P1 — split `UV` and `UVhighp` in the shared fullscreen vertex shader.**
   ARM32 samples mask and circle textures with mediump `UV`, but uses highp
   `UVhighp` for the block-shift/color-origin coordinates. Java currently uses
   one highp `UV` for both roles.
3. **P2 — make the blur shader literal.** Java uses highp UV/texel coordinates,
   reverses the nested sample-loop order, and shortens the expression. The
   sample set is the same, but mediump accumulation and the `grayScale < 0.2`
   boundary can differ by a small amount.
4. **P2 — remove explicit highp from the final block-size uniforms and restore
   the transparent branch.** The former is precision-sensitive; the latter is
   finite-output-equivalent but removes unnecessary texture work and the
   theoretical `NaN * 0` outside-mask path.
5. **P3 — optionally restore the vec3 fullscreen position plus identity MVP.**
   Java's vec2 clip quad is output-equivalent only because this reconstruction
   intentionally uses an identity clip-space transform. It is not a likely
   source of the remaining visible difference.

The safest implementation is to copy the literal ARM32 sources, changing only
the final opaque tail to the already-verified LLE transparent tail. Renaming
attributes/uniforms is not itself a visual bug, but retaining the original
names makes future binary diffs mechanical.

## Pass-by-pass summary

| Pass | Literal ARM32 interface and precision | Java state | Classification | Expected visual impact |
|---|---|---|---|---|
| Mask VS/FS | default `mediump`; `aPos:vec2`, `aTex:float`, `alpha:float`; declared unused MVP | Same types, precision and bindings; statement order swapped | Equivalent | None |
| Circle3 VS/FS | default `mediump`; `aPosition`, `aCenter`, `aColorA/B/C`; all varyings mediump | `aPos`; position/center highp, colors lowp | Precision-sensitive | One-pixel band-edge changes and small palette shifts |
| Circle2 VS/FS | same, with A/B only | Java compiles the five-attribute VS and lets C optimize out; highp/lowp split remains | Precision-sensitive; inactive C is equivalent | Same as Circle3; no impact from optimized-out C |
| Color-origin VS | shared ARM32 fullscreen VS: `aPos:vec3`, `aTex:vec2`, MVP; `UV`/`UVNorm` mediump and `UVhighp` highp | `aPos:vec2`, no MVP, UV and UVNorm highp | Identity-position output equivalent; UV precision-sensitive | Small sample-coordinate change |
| Color-origin FS | default mediump; x-outer/y-inner 121-sample loop; mediump texel uniform | same 121 offsets, y-outer/x-inner; highp texel/UV | Algebraically same sample set, not floating-point literal | Small blur/luma-threshold change |
| Final portrait FS | mediump `UV`, highp `UVhighp`; literal portrait circle coordinate | one highp UV; uniform branch selects portrait | Formula equivalent, precision-sensitive | Mask/circle edge and block-boundary quantization |
| Final landscape FS | same, literal swapped circle coordinate | same shader, uniform branch selects landscape | Formula equivalent, precision-sensitive | Same |
| Transparent tail | branch; `m=clamp(1-alpha)`, `a=sqrt(m)`, `vec4(c6*a,a)`, else zero | same active-pixel math; chain evaluated outside mask, then multiplied by zero | Equivalent for finite inputs | None normally; extra work outside mask |

## 1. Mask pass

### Literal source and interface

The ARM32 strings at `0x18c48` and `0x18d0c` declare:

```glsl
precision mediump float;
uniform mat4 uModelViewProjectionMatrix;
attribute vec2 aPos;
attribute float aTex;
varying float alpha;
// VS: gl_Position=vec4(aPos,0,1); alpha=aTex
// FS: gl_FragColor=vec4(1,1,1,alpha)
```

The MVP is declared but never read. The Java shader declares the same symbols
and types and intentionally also leaves MVP inactive. Java interleaves
`x,y,alpha` as three floats with stride 12 and binds `aPos` at float 0 and
`aTex` at float 2. The ARM32 renderer may store these in separate VBOs, but the
attribute values presented to GLSL are identical.

### Divergence

Java assigns `alpha` before `gl_Position`; ARM32 does the reverse. These writes
are independent and the difference is exactly output-equivalent. No patch is
needed.

The straight-alpha blend binding is also correct for the literal shader:
over a transparent mask target, RGB stores the scalar source alpha, which the
final shader later samples through `uMask.x`. The destination alpha value is
not used by the final pass.

## 2. Circle3 pass

### Literal source, expressions and bindings

ARM32 strings `0x18f10` and `0x191f4` use default mediump for every floating
declaration:

```text
attributes: aPosition vec2, aCenter vec2, aColorA/B/C vec3
varyings:   UV vec2, vCenter vec2, vColorA/B/C vec3
uniforms:   uRadiusA/B/C, uAlphaA/B/C, uSquareRatio (all float)
```

The exact distance construction is:

```glsl
float sqdistX=(vCenter.x-UV.x)*(vCenter.x-UV.x);
float sqdistY=(vCenter.y-UV.y)*(vCenter.y-UV.y)*uSquareRatio;
float distance=sqrt(sqdistX+sqdistY);
```

It then tests C, B, A in that order and discards outside A.

Java binds semantically correct buffers: two geometry components plus two
center components at stride 16, and one independent RGB buffer per currently
permuted ring. It binds the matching radius/alpha with the same A/B/C suffix,
so color-buffer permutation and uniform permutation remain coupled.

### Divergences

- Java renames `aPosition` to `aPos`. This is internally consistent and has no
  visual effect.
- Java has no `precision mediump float;` in the circle vertex shader. Its
  position and center varyings are explicitly `highp`; colors are explicitly
  `lowp`. ARM32 declares all of them mediump.
- Java forms `d=position-center` and squares it. ARM32 forms
  `center-position`. Squaring removes the sign, so this is algebraically
  equivalent for finite values. It does not need a formula change.
- Java's vector color operations are absent here; it emits the same selected
  `vec4(color,alpha)` and uses the same strict `<` boundaries.

The precision difference is not merely cosmetic. ARM32 quantizes the two
interpolated coordinates separately as mediump before subtraction. Java
interpolates them at highp and converts the difference to the default-mediump
local `d`. The quantization point therefore differs. Java also quantizes
constant palette varyings to lowp rather than mediump. Neither should cause a
large shape mismatch, but both can affect the final few percent.

### Safe patch

Use the literal ARM32 Circle3 VS/FS, including `precision mediump float;`, the
original names and default-mediump varyings. Keep the current Java VBO values
and ring permutation. This is a source-only correction with no physics or mesh
change.

## 3. Circle2 pass

ARM32 strings `0x190a4` and `0x194c0` are the two-band equivalent:

```text
attributes/varyings: position, center, color A/B (all mediump)
uniforms: uRadiusA/B, uAlphaA/B, uSquareRatio
tests: B first, then A, else discard
```

Java compiles the common five-attribute circle VS with the two-band fragment
shader. Link-time dead-code elimination removes C, and Java already tolerates
the resulting attribute location `-1`. That difference is output-equivalent.
The same highp-position/lowp-color divergence from Circle3 applies.

Safe patch: use the literal two-band vertex shader rather than relying on C to
optimize out, and use the literal fragment shader. The current A/B buffer and
uniform bindings are already correct.

## 4. Color-origin blur/prepass

### Literal vertex and fragment programs

The ARM32 prepass uses the shared fullscreen vertex string at `0x18d78`:

```text
precision: default mediump
uniform:   uModelViewProjectionMatrix mat4
attrs:     aPos vec3, aTex vec2
varyings:  UV vec2, UVhighp highp vec2, UVNorm vec2
position:  MVP * vec4(aPos,1)
UV/UVhighp derived from gl_Position; UVNorm=aTex
```

Its fragment string at `0x196fc` declares default mediump `UVNorm`,
`uTextureOrigin`, and `uTextureOriginTexelSize`. It uses `distance=10`, loops
`i` (X) outside and `j` (Y) inside in steps of two, and divides by
`vec3((distance+1)*(distance+1))`. The luma expression and dark lift are
literal:

```glsl
float grayScale=0.299*avgColor.r+0.587*avgColor.g+0.114*avgColor.b;
if(grayScale<0.2) avgColor+=0.3;
```

### Java divergences

- Java uses a clip-space vec2 position and no MVP. With its explicit clip quad
  and identity transform, `gl_Position.xy`, derived UV and raster coverage are
  equivalent. This would stop being equivalent if the port later introduced a
  non-identity scene transform.
- Java makes `UVNorm` and the texel-size uniform highp. ARM32 keeps both at
  default mediump.
- Java loops Y outside and X inside. It visits the same 121 offsets, so the
  mathematical average is identical; floating addition is not associative,
  so mediump accumulation can differ in the low bits.
- Java computes the shifted coordinate as one vector expression rather than
  ARM32's scalar `xCoord`, scalar `yCoord`, then `vec2`. The sample set is
  algebraically identical, but the highp/mediump conversion points differ.
- `dot(c,vec3(...))` is algebraically equivalent to the literal three scalar
  products and additions, but a compiler may lower it with a different
  rounding/fusion sequence.
- `/=121.0` equals division by `(10+1)^2`. Uniform/attribute renaming is
  internally consistent and has no output effect.

These differences matter most for pixels whose luma is very close to `0.2`:
a rounding change can decide whether `+0.3` is applied to an entire low-res
block.

### Safe patch

Copy the literal fullscreen VS and prepass FS. Provide a vec3 position
(`z=0`) and set the MVP to identity. Bind the existing background texture to
literal `uTextureOrigin` and the existing source-texel reciprocals to
`uTextureOriginTexelSize`. This preserves the same 121 samples while matching
the original precision and accumulation order.

Separately, source-texel reciprocals must describe the uploaded bitmap, not
blindly the surface; that host-side issue is documented in
`JAVA-DIVERGENCE-AUDIT.md` and is outside this literal shader audit.

## 5. Final portrait pass

### Literal interface

The portrait fragment at `0x199d4` consumes:

```text
varyings: UV mediump, UVhighp highp, UVNorm mediump
samplers: uBackground, uTextureCircles, uTextureAnotherCircles, uMask,
          uTextureOrigin, uTextureColorOrigin
uniforms: uBlockSizeWidthNormalize, uBlockSizeHeightNormalize (mediump float),
          uVerticalFlip (unused int)
```

The portrait circle coordinate is exactly
`vec2(UV.x*2.0, UV.y*2.0*1.05)`. Mask and circle sampling therefore use
mediump `UV`; the shift, `coordL/R/RInv`, and direct mosaic-C sample originate
from highp `UVhighp`.

### Blend-function equivalence

Java shortens several helpers:

- vector `min(a+b,1)` equals ARM32's scalar-per-channel `linearDodge`;
- the vector soft-light expression is the same scalar expression applied
  componentwise and in the same lexical order;
- ternary overlay equals ARM32's scalar `if/return` for finite values;
- vector `max(c3,colorMosaicC)` equals the scalar-per-channel `lighten`;
- omitted `exclusion`, `substract`, and portrait-only `overlayOpt` helpers are
  dead in ARM32 and cannot affect output.

These are algebraically equivalent. They should not be used to tune the last
10 percent by eye.

### Real divergences

- Java exposes only one highp `UV`. Consequently mask and circle textures are
  sampled at highp coordinates instead of the literal mediump coordinates.
- Java explicitly marks the two block-size uniforms highp; ARM32 leaves them
  mediump. Values `1/12` and `1/21` and the row shift can therefore quantize
  differently.
- Java selects orientation with a uniform ternary. ARM32 selects one of two
  fragment programs during construction. Because `uLandscape` is uniform and
  only 0/1, the portrait result is mathematically identical; this is not a
  per-fragment orientation decision.
- Java evaluates all texture reads outside the mask and later multiplies the
  result by zero. Patched ARM32 enters the color chain only when
  `alpha<1.0`, otherwise it writes transparent zero. Valid texture values and
  nonzero block dimensions make the outputs equivalent, but the branch is
  more literal and avoids unnecessary reads.
- Java changes texture-read order. Texture reads have no side effects, so this
  is output-equivalent; implicit derivatives depend on coordinate expressions,
  not statement order.

### Safe patch

Use the literal shared fullscreen VS and portrait fragment body. Remove the
unused opaque-background sampling from behavior only through the verified tail;
it is safe to retain the now-inactive declarations. Preserve separate
mediump `UV` and highp `UVhighp`, default-mediump block uniforms, and the
`if(alpha<1.0) ... else vec4(0.0)` structure.

## 6. Final landscape pass

The landscape string at `0x1a714` is the portrait body with the exact circle
coordinate swap:

```glsl
vec2(UV.y*2.0, UV.x*2.0*1.05)
```

The block shift still uses `UVhighp.y`; it is **not** transposed. Java does
this correctly: only `circleUV` changes under `uLandscape`. All portrait
precision and branch findings apply.

The stock portrait shader contains an extra unused `overlayOpt`; the landscape
shader does not. This source-length difference is dead code, not an orientation
color difference.

Safe patch: either compile the two literal orientation fragments as ARM32 does,
or keep the uniform selection after first restoring the literal UV precision.
Two programs are preferable for mechanical 1:1 auditing; the current ternary
is mathematically sound.

## 7. Transparent patched tail verification

The build patch replaces the stock opaque tail in both orientation strings
with exactly:

```glsl
float m=clamp(1.0-alpha,0.0,1.0);
float a=sqrt(m);
gl_FragColor=vec4(c6*a,a);
} else gl_FragColor=vec4(0.0);
```

The Java active-pixel tail is the same formula. `renderFinal()` clears the
default target transparent and uses:

```text
GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA
```

Let mask coverage be `m` and `a=sqrt(m)`. Over transparent, fixed-function
blending stores RGB `(c6*a)*a=c6*m` and alpha `a*a=m`. Therefore the Java
shader/blend pair matches the patched ARM32 transparent boundary. Replacing
the blend source factor with `GL_ONE`, or emitting `vec4(c6*m,m)` while keeping
straight-alpha blending, would be non-equivalent and too dim/too wide in the
transition.

The only tail divergence is control flow: Java omits the outside-mask branch.
For finite `c6`, `vec4(c6*0,0)` equals `vec4(0)`. Restoring the branch is safe,
more literal, cheaper outside the active patch, and robust against an invalid
texture producing NaN.

## 8. Concrete source-only patch recipe

The changes below do not alter touch physics, target sizes, mesh data or
sampler routing:

1. Create literal Circle3 and Circle2 vertex strings, both beginning with
   `precision mediump float;`, using the ARM32 attribute/varying declarations.
2. Replace both circle fragment strings with the literal mediump sources. Keep
   current ring buffer/uniform permutation unchanged.
3. Replace `TEXTURE_VERTEX_SHADER` with the literal fullscreen vertex source;
   expand the Java fullscreen position from XY to XYZ (`z=0`) and bind an
   identity `uModelViewProjectionMatrix` for prepass and final.
4. Replace the blur fragment with the literal source and literal names; retain
   the current source texture and texel values at the binding boundary.
5. Use separate portrait and landscape final fragments copied from ARM32, then
   apply only the verified transparent tail. At minimum, reintroduce mediump
   `UV`, highp `UVhighp`, default-mediump block uniforms and the outside-mask
   branch.
6. Keep standard source-alpha blending and transparent clears unchanged.

If minimal churn is preferred, steps 1, 2 and the UV split in step 5 are the
highest-value changes. Renaming symbols, restoring dead helper functions and
adding the unused `uVerticalFlip` cannot improve valid output.

## 9. Validation gate for the remaining shader delta

After the literal patch, compare ARM32 and ARM64 with the same cached image and
gesture at fixed normalized timestamps. The most diagnostic crops are:

- a Circle3 and Circle2 band boundary, to expose mediump/highp interpolation;
- a low-luma block near the `grayScale=0.2` prepass threshold;
- a staggered row boundary, to expose block-size/UV precision;
- a zero-mask region, which must remain exactly RGBA zero after final blending.

Any larger residual after these source corrections is not in the recovered
GLSL math. It should be sought in the still-open external texture filter
defaults, animator interpolation, screenshot sampling/crop, or the special
affordance field rather than by modifying the six-layer color functions.
