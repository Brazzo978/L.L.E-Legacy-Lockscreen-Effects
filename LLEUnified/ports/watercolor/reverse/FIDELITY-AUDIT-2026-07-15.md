# Watercolor residual blur/distortion audit (2026-07-15)

## Scope and status

This pass was started after the ARM64 application became stable but retained a
small visible difference from the Samsung ARM32 effect, especially in the
blur/distortion inside the Watercolor stroke. It compares the active ARM64
renderer directly with the selected shader strings and renderer functions in
the original `libsecveSrkCommon.so` and `libsecveWaterColor.so`.

The stable renderer remains the default. A literal legacy-feedback build is
available only as a controlled A/B diagnostic and must not be shipped without
device evidence because it deliberately invokes undefined GLES2 behavior.

## Closed findings

### Advection precision was not source-equivalent

The selected stock advection fragment shader at common raw VMA `0x4fd84`
(Ghidra `0x5fd84`) declares:

```glsl
varying highp vec2 vTexUV;
...
highp vec4 NoiseVelocity = texture2D(uVelocity, vTexUVBG);
```

The previous ARM64 shader used the fragment default `mediump` for both. It
also evaluated the velocity/density samples outside the stock
`AlphaColor.a != 0.0` branch. The math was algebraically equivalent, but the
precision and generated driver program were not. The candidate renderer now:

- uses `highp` for the density/effect UV varying on both shader stages;
- uses `highp` for the sampled velocity vector;
- restores the stock conditional sampling structure;
- retains `mediump` for the background UV and final `densityUV`, as stock does.

This is the strongest deterministic explanation found for a slightly softer
or more quantized distortion on the 60%-resolution density target.

### Final colour shaping used an algebraic rewrite

The stock mix shader at raw VMA `0x50958` expands the weighted magnitude and
updates R, G and B separately. The previous port used a GLSL `dot()` rewrite.
The candidate restores the stock operation order while keeping LLE64's
required premultiplied transparent output boundary.

### Managed texture wrap differed

Samsung's `SPTextureManager` defaults managed Mask, Tube and background
textures to `GL_MIRRORED_REPEAT`. The port used `GL_CLAMP_TO_EDGE` for those
uploads. Candidate builds now use mirrored repeat for managed ARGB textures;
generated Noise plus radial/density FBO textures remain linear/clamped exactly
as stock. The expected effect is limited to filtered edges, but this removes a
real command-level delta.

### Background UV normalization is neutral in the current geometry

`SPTextureManager::normalizeTextureCoord` at raw VMA `0x2dfc0` was decompiled
again together with both Water brush renderers. For equal screenshot/surface
aspect ratios it produces the canonical four coordinates
`(0,1), (1,1), (0,0), (1,0)`. Combined with the selected advection vertex
shader's Y transform, the current fullscreen quad is equivalent. Therefore
background UV normalization is not the cause of the observed residual on the
present 1080x2520 path. Different Fold/display geometries remain out of scope
for this pass.

## Remaining irreducible candidate: density feedback topology

WaterColor creates one density texture/FBO. In the active draw at WaterColor
raw `0x3140..0x3433`, the same density texture is bound for sampling while it
is attached to the draw framebuffer. Seed at raw `0x4abc` performs the same
draw twice. This is formally undefined framebuffer feedback in GLES2.

The stable ARM64 renderer uses two RGBA8 targets and deterministic ping-pong,
which models previous-frame recurrence. It cannot guarantee the tile/cache
artifact produced by a particular legacy Mali/Adreno driver. For diagnosis,
`build.ps1 -WatercolorFeedbackMode StockFeedback` compiles the literal
same-texture topology into a separately named APK. The default
`-WatercolorFeedbackMode Stable` continues to compile ping-pong.

The A/B changes only density topology; both variants include the precision,
branch, mix-order and texture-wrap corrections above. Runtime logcat identifies
them as `stable-ping-pong` or `stock-same-texture-ab`.

## A/B acceptance protocol

Use the same screenshot, display mode and scripted slow diagonal stroke for
both builds. Compare:

1. the first two visible frames after DOWN;
2. edge sharpness and local bending during a slow MOVE;
3. persistence/relaxation after UP;
4. shader/FBO errors, black output, corruption and crash logs.

If literal feedback is only different, unstable or device-run dependent, keep
stable ping-pong. If it is consistently closer to the ARM32 reference, use the
capture to derive a deterministic approximation; do not promote the undefined
path itself solely on visual preference.

## Device validation performed

Both final candidates were compiled, signed, installed in turn and profiled on
the Galaxy Z Fold7 `SM-F966B` (Android 16, arm64-only, Adreno shader compiler
`E031.47.18.50`). The internal effect profiler forced effect id 3 without
requiring a picker change.

| Mode | APK SHA-256 | Common library SHA-256 | Runtime result |
|---|---|---|---|
| Stable ping-pong | `B754960F567FA0F4872054ED199E1104D654EDD5131290FBF3B713B6754F1202` | `6974BE907684897F31F8B14EB560D80D5EC1648631044395CD73B33A354D9126` | shader link, all assets, two-pass seed and first frame succeeded |
| Stock same-texture A/B | `4DE1B32C11BB13D6473D00644198B0217FDFB8C9971027352ACF0A5B8C5017A0` | `7889C23E9DAF0C80F2142DC54FFE5A3CEE294AFD9171176F50708F09476A36A1` | shader link, same-texture seed and first frame succeeded |

Both reported radial `27x63`, density `648x1512`, generated velocity
`362x642`, and no Java/native crash, shader error or GL error in the sampled
run. The stable APK was reinstalled after the A/B run and is the final device
state. The accessibility service is enabled/bound and Watercolor remains the
selected effect. A subsequent real-use visual check judged this corrected
stable build materially closer to the ARM32 reference. The post-use audit
found the process alive, no Java/native crash, ANR, EGL/GL error or failed
framebuffer, and no crash exit record; recorded exits were package replacement
or an intentional force-stop during development.

## Readiness verdict

Native compilation, APK packaging, runtime shader/FBO validation and the
target-device visual check pass. Watercolor is accepted at the current
early-alpha fidelity target with deterministic ping-pong as the shipping
implementation. The literal same-texture build remains diagnostic only: its
GLES2 feedback is undefined, so successful execution on this device does not
justify declaring it stable. Exact frame identity with a legacy GPU remains
outside what can be guaranteed, but this audit found no further deterministic
shader, texture-state, geometry, cadence, touch or lifecycle delta responsible
for the previously reported blur/distortion difference.
