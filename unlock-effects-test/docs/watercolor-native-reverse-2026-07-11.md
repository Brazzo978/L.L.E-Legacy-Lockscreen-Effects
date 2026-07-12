# Samsung N3 Watercolor native reverse - 2026-07-11

## Selected reference

The classic Watercolor effect is present in the S4 and Note 4 generations.
The following binaries are byte-identical (`79060` bytes, SHA-256
`2B00D2590A9C92BFE5461C8890CA1E4F5D8D0A8196B0BA67C9CDB2B35895C2EB`):

- `extracted/s4_system_files/lib/libsecveWaterColor.so`
- `extracted/note4_bob4_full/system/lib/libsecveWaterColor.so`
- `charging-touch-test-apk/native-libs/lib/armeabi-v7a/libsecveWaterColor.so`

S5 and one later Note 4 dump contain smaller `75364` byte revisions with
different hashes. They keep the same public Java contract and visual family,
but the S4/Note 4 BOB4 binary is the canonical reference for the classic port.

### S5 revision comparison

The smaller S5 binary is not evidence of a new visual algorithm:

- both Watercolor libraries import the same set of 60 `SPhysics` APIs;
- the radial-mask, tube, background-advection, density, and final-mix GLSL
  strings in the S4 and S5 `libsecveSrkCommon.so` files are byte-identical;
- the S5 build changes C++ runtime/toolchain dependencies from the older GNU
  stack to libc++/newer Android private symbols and moves `createScene` from
  `0x11c74` to `0xe8d4`;
- the S5 pair is smaller (`WaterColor 75364`, `SrkCommon 263872`) than the
  classic pair (`79060`, `341296`) because of that rebuild/relink, not because
  a different Watercolor shader was introduced.

Therefore use S4/Note 4 for the canonical reverse addresses and use S5 as a
secondary device reference for timing, lifecycle, and visual validation. There
is no benefit in replacing the app-owned port math with S5-specific math because
the recovered shader math is the same.

## Java/keyguard contract

- S4 SystemUI exposes `EFFECT_WATERCOLOR = 4` and constructs
  `KeyguardEffectViewWaterColor`.
- Samsung VisualEffect exposes Watercolor through effect id `5`.
- The renderer library name is `libsecveWaterColor.so`.
- Background texture name is `bg`.
- Original assets are `waterbrush_tube`, `watercolor_mask1/2/3`, and
  `watercolor_noise`.
- Touch codes are `0` down, `1` up, `2` move, with `9/10/7` used by the
  hover/pattern path.
- The Java renderer waits for three frames before accepting input.

## ELF anchors

- ARMv7 ELF32, `.text` VMA/file offset `0x2900`, size `0xf390`.
- `createScene`: `0x11c74`.
- scene constructor: `0xeb90`.
- scene vtable starts at `.data.rel.ro + 0x1b0`, VMA `0x13c88`.
- touch thunks: `0xa12c`, `0xa17c`, `0xa1cc`, `0xa21c`, `0xa26c`, `0xa2bc`.
  They forward codes `0`, `1`, `2`, `9`, `10`, `7` to the common scene touch
  handler after converting normalized coordinates to integer screen space.
- `0xa5bc` combines base update/draw/empty checks.

The old Ghidra notes that called `0x23c88` the vtable used an image base of
`0x10000`; the ELF VMA is `0x13c88`. Both labels describe the same bytes.

## Recovered render pipeline

The native scene owns these common-library components:

1. `SPDrawRadialWaterBrush`
2. `SPDrawBGAdvectWaterBrush`
3. `SPDrawMixWaterBrush`
4. `SPDrawBackground`

The scene allocates framebuffer/texture pairs, stamps radial velocity and alpha
from the mask/tube assets, advects the background with radial plus noise
velocity, then mixes the advected density with the original background.

Confirmed setter values in the native draw/update path:

- noise vector scalar: `3.4` (`0x4059999a`)
- radial vector scalar: `3.6` (`0x40666666`)
- active radial brush time step can be forced to `0.9`
- final alpha density is shaped with `pow(alpha, 4.0) * 0.95`

The background-advection shader computes:

```glsl
vec4 alpha = texture2D(uRadial, uv);
vec4 noise = texture2D(uVelocity, uv);
vec2 densityUV = uv + (
    (alpha.xy - 0.5) * 10.0 * uRadialVectorScalar
    + (noise.xy - 0.5) * uNoiseVectorScalar
) * alpha.b * 0.0021;
```

The final Watercolor mix shader computes:

```glsl
vec4 base = texture2D(uTexMap, uv);
vec4 alpha = texture2D(uAlpha, uv);
vec4 density = mix(
    texture2D(uDensity, uv),
    texture2D(uColorDensity, uv),
    alpha.a
) * uBrightness;

float p = sqrt(dot(density.rgb * density.rgb,
        vec3(uRedSaturation)));
density.rgb = p + (density.rgb - p) * uSaturation;
alpha.a = pow(alpha.a, 4.0) * 0.95;
gl_FragColor = mix(base, density, alpha.a);
gl_FragColor.a = uAlphaRatio;
```

The radial mask encodes direction in RG, time step in B, and brush mask in A.
The tube variant multiplies the time step by the red channel of
`waterbrush_tube`.

## Native overlay translation implemented 2026-07-12

The stock final pass redraws the complete opaque keyguard background. LLE must
not copy that final alpha behavior. The active implementation keeps Samsung's
original renderer and changes only its compatibility boundary:

- `WatercolorNativeEffectView` hosts `EffectView.setEffect(5)` and passes the
  live/cached lockscreen screenshot through command `0` / key `Bitmap`;
- the Samsung dex is rebuilt with EGL RGBA8888 instead of RGB888, and the
  nested `TextureView` is marked non-opaque before it is attached;
- the already retained LockBG dex patch clears the default framebuffer to
  transparent before each native draw and once more when animation becomes idle;
- `patch-watercolor-transparent.ps1` rewrites only the two final mix outputs in
  staged `libsecveSrkCommon.so`.

The primary final pass now emits the native density colour with
`alpha.a * uAlphaRatio`; the alternate pass emits density with its native
`AlphaColor`. Both RGB outputs are premultiplied. Outside the native Watercolor
alpha field the output is transparent, while Android composition supplies the
real SystemUI/background pixel. All upstream native FBOs and formulas remain
byte-identical.

This is the same compatibility boundary solved for native S3 Ripple, but
Watercolor already has a native local alpha field, so it should require less
heuristic masking than Ripple.

## Device verification

On SM-S918B the rebuilt APK remained ARM32 and logged successful construction,
background upload at `1080x2316`, native `onSurfaceCreated`, native
`onSurfaceChanged`, texture load, first draw, original center affordance, and
real touch DOWN/UP. An idle full-lockscreen capture confirmed that the overlay
no longer covers SystemUI. No runtime/JNI/EGL failure occurred in the successful
process, and LLE plus Bitwarden accessibility services remained enabled.

Watercolor originally reported `118-120 fps` on a 120 Hz panel and consumed its
frame-stepped wake hint far faster than stock. The current build overrides only
`WaterColorRenderer.onDrawFrame()` to pace the original renderer at 60 Hz; it
does not change display refresh or any native simulation formula. A clean S23
wake measured `57.65-58.82 fps`, a roughly two-second queued-hint lifecycle and
no LLE frame skip/crash. LLE still marks Watercolor stale on overlay detach and
reconstructs the complete Samsung effect before reuse, avoiding the earlier
doubled-library-path `dlopen` failure and stale framebuffer state.

The wake hint exposed a second, independent composition race. Command `1` used
Samsung's 500 ms center DOWN while the modern device could still be presenting
black/AOD; the native Watercolor pass had already received the bright cached
lockscreen bitmap, so local alpha revealed a yellow/orange patch over the dark
base. Watercolor still uses Samsung's original affordance command and geometry,
but its host clamps the delay to `1000 ms` so SystemUI can finish composing the
real lockscreen first.

The deleted Canvas renderer is not retained as fallback. If native Watercolor
cannot initialize, LLE leaves that renderer unavailable instead of substituting
an approximation.
