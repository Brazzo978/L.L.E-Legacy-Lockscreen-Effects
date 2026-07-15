# Watercolor ARM64 implementation log

Date: 2026-07-15

## Scope

LLE64 keeps Samsung's original Java shell (`EffectView`, effect id 5,
`WaterColorEffect`, `WaterColorRenderer`) but replaces the ARM32 native engine
with an app-owned AArch64 reconstruction.

Packaged ARM64 libraries:

- `libsecveSrkCommon.so`: JNI, state machine and GLES2 renderer;
- `libsecveWaterColor.so`: ABI/SONAME sentinel exporting `createScene`;
- `libstlport.so`: retained because Samsung's Java `Native` class loads it
  before the reconstructed common bridge.

No ARM32 object, STL layout or function pointer crosses the ABI boundary.

## Recovered simulation

- CPU events use the recovered logical 32-byte record: initial size, immutable
  baseline size, current size, alpha/phase, x/y, forced-mask flag and mask
  index. Primary storage grows dynamically like the stock `std::vector`; no
  live stamp is dropped at an arbitrary capacity.
- Java raw top-left touch Y is converted once to Watercolor's bottom-left
  brush coordinate. The radial vertex path converts it directly to GL clip
  space, preserving the original radial-vector sign.
- Base brush diameter is `0.8 * 0.35 * min(width,height)` on a non-square
  display and approximately `0.1575 * side` on a square display.
- MOVE acceptance threshold is `width * strokeScale * 0.025`. Segment count is
  `clamp(ceil(distance / (width * 0.05)), 2, 101)` and the loop emits
  `i=1..count-1`, deliberately excluding the current endpoint.
- DOWN/UP select one of three masks through legacy `rand()` scaling.
  Interpolated MOVE stamps force mask 0 and use
  `base * strokeScale * [0.55,0.80)`. `strokeScale` recovers by `+0.02` per
  60 Hz update and falls by `0.025` for every accepted MOVE stamp.
- Size growth is branch-for-branch: `1.075`, `1.025`, `1.005`, `1.0045` at
  the recovered `2.3/2.6/2.8` boundaries. Alpha advances by `0.025` beyond the
  last size boundary and receives the recovered extra aging pass outside the
  newest twenty events; expiry is at `1.06`.
- Normal radial timestep is
  `clamp((size - baselineSize) * 0.01, 0.1, 1.0)`. The baseline is immutable,
  not the size from the previous frame.
- The Samsung DEX renderer is paced at 60 Hz, keeping the frame-stepped state
  consistent on 60/80/120 Hz panels.

`showUnlock` activates the persistent stock special state. It snapshots the
primary queue into exactly four secondary events, updates them first with
`size *= 1.1` and alpha `0.5`, then draws them before the primary queue with
timestep `0.8`; primary stamps use timestep `0.9` in this state. The stock
30-tick input gate and subsequent `-0.06` gate decay remain separate from the
queues. Native `showAffordance` follows the recovered pending-reset path; the
WaterColor Java shell's normal hint remains its delayed synthetic DOWN.

## GLES2 pipeline

1. Clear an RGBA8 radial FBO at 2.5% resolution to `(0.5,0.5,0,0)`.
2. Draw Tube/mask brush quads in secondary/primary queue order using
   `glBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE,
   ONE_MINUS_SRC_ALPHA)`.
3. Advect a 60%-resolution density field from radial + generated velocity +
   prior density.
4. Apply classic brightness/saturation and write local premultiplied color to
   the transparent default framebuffer.

Samsung does not upload `watercolor_noise.jpg` directly. It flips source rows,
converts `(R+G+B)/765` to a height field, computes signed finite differences
for X/Y, applies the orientation-dependent `0.5625` correction and encodes
`(gradient+0.5)*255` into a generated `GL_RGB` velocity texture sized
`(sourceWidth+2) x (sourceHeight+2)`. Sampling the raw grayscale image makes
velocity X equal velocity Y and collapses the effect into diagonal blur.

Before the first visible stamp and after background/context invalidation, the
port clears the radial/density targets and performs the two recovered advect
seed draws. Once both queues become empty, radial clear and advection are
skipped for the terminal frame and the previous state is mixed, matching the
stock tail branch.

The original samples and renders the same density texture in one draw, which
is undefined framebuffer feedback in GLES2. The ARM64 port uses two density
textures/FBOs and ping-pongs them. This preserves the intended previous-frame
recurrence deterministically on modern Adreno/Mali drivers.

Classic shader constants:

- noise vector uniform: `3.4 * 125 = 425.0`;
- radial vector uniform: `3.6 * 18.525 = 66.69`;
- displacement tail: `radial.b * 0.0175 * 0.006`;
- relaxation to original: `0.03`;
- brightness/saturation/R/G/B: `1.35 / 1.2 / 1.3 / 0.4 / 0.4`.

The stock final shader writes `mix(background,density,alpha)` to an opaque
SystemUI target. LLE64 writes `(density.rgb * alpha, alpha)` with final
blending disabled. Android supplies `background * (1-alpha)` from the live
lockscreen, leaving pixels outside the brush truly transparent.

Android bitmap/noise uploads and FBO textures have different row origins. The
ARM64 shaders keep radial/density UV bottom-origin, flip Android ARGB inputs
once in the advect pass and sample radial/density directly in the final pass.
This is the port equivalent of Samsung's dual UV arrays.

All attribute and uniform locations are cached once after program link. The
stamp hot path performs no `glGetUniformLocation` calls, removing hundreds of
redundant driver lookups on long strokes without changing shader inputs.

The residual blur/distortion audit restored the selected ARM32 shader's
`highp` density UV/velocity qualifiers, conditional advection sampling,
expanded mix operation order and managed `GL_MIRRORED_REPEAT` state. The
evidence and controlled density-topology A/B protocol are recorded in
`reverse/FIDELITY-AUDIT-2026-07-15.md`.

## Java and DEX integration

- `OverlayPrefs.isImplementedEffect()` accepts N3 Watercolor and
  `ControlActivity` exposes `N3 Watercolor (Early Alpha)` in the ARM64 picker.
- `ChargingAccessibilityService` constructs `WatercolorNativeEffectView`,
  waits for the shared screenshot background, recreates it on display-size
  changes and falls back to Lens Flare if native construction fails.
- Watercolor instances are destroyed and reconstructed after detach; a stale
  Samsung `GLTextureViewRenderer` is never reattached.
- EGL config is patched from RGB888/alpha 0 to RGBA8888/alpha 8.
- `WaterColorRenderer.onDrawFrame` calls the app's 60 Hz pacer.
- Special textures resolve from package `com.codex.lle` and retain original
  unscaled dimensions.
- Audio matches stock ordering: tap on DOWN, optional second tap on release
  after more than 411 ms, unlock sound on completed gesture.

## Verification performed

- Both reconstructed libraries compile with strict C11 flags, no fast math,
  `-Wall -Wextra -Werror` and `--no-undefined`.
- Build checks AArch64 ELF headers, SONAMEs, dependencies and every JNI export.
- APK packaging rejects any ABI other than `arm64-v8a` and verifies v1/v2/v3
  signing.
- Device: Galaxy Z Fold7 `SM-F966B`, Android 16, arm64-only.
- Runtime: all five assets and the screenshot upload at expected sizes; the
  raw `360x640` noise becomes a `362x642` velocity texture. At `1080x2520`,
  radial FBO is `27x63` and each density FBO is `648x1512`.
- Real lockscreen DOWN/MOVE/UP, completed and cancelled gestures, delayed
  affordance, destroy/recreate and shader compilation ran without Java/native
  crash.
- Observed renderer cadence is approximately 59.4-60.0 fps while the physical
  panel switches dynamically between 60 and 120 Hz.
- Post-fidelity process statistics were 5 ms median / 9 ms p95 with 2 ms
  median GPU time during the sampled session.
- The 2026-07-15 residual-fidelity candidates both linked on Adreno
  `E031.47.18.50`, loaded all assets, created `27x63` / `648x1512` targets and
  completed their respective two-pass seed plus first frame without a GL,
  native or Java failure. Stable ping-pong was reinstalled after the A/B.

## Current fidelity boundary

This remains a clean-room reconstruction. The largest intentional difference
is deterministic density ping-pong in place of original undefined
single-texture feedback; literal feedback would make output driver-dependent
and can corrupt rendering on modern GPUs. A separately named, compile-time A/B
build can reproduce the literal command topology for controlled captures, but
never replaces stable ping-pong automatically. Remaining fidelity work is
limited to paired frame captures, exact legacy RNG sequences and optional
parameter/action paths. The transparent boundary can only match stock exactly
while the cached screenshot remains aligned with live SystemUI.
