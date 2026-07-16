# Samsung Abstract Tiles: ARM32 reverse and ARM64 port specification

Status: reverse-engineering baseline complete
Target: LLE Unified, `arm64-v8a`
Effect: Samsung Abstract Tiles / `libsecveAbstractTile.so`
Last verified: 2026-07-16

The exhaustive Line-layer reverse, including raw Ghidra evidence, all 22
portrait/landscape definitions, host adaptations and the original-device
comparison protocol, is preserved in
[`ABSTRACT_TILES_LINE_PORT_TRANSCRIPT_2026-07-16.md`](ABSTRACT_TILES_LINE_PORT_TRANSCRIPT_2026-07-16.md).

This document is the implementation contract for the app-owned ARM64 reconstruction. It records the behavior recovered from the original ARM32 engine and explicitly separates OEM behavior from the changes required by LLE's transparent accessibility overlay. Values marked as OEM below should not be tuned by eye without first proving that the reverse is wrong.

## 1. Sources and confidence

### Primary native oracle

- File: `LLEUnified/vendor/original-native/libsecveAbstractTile.so`
- Size: 115,932 bytes
- SHA-256: `F8E8BDF48D069F76AF9923D68474A7047C621DD763D3E6D96C4F940025643840`
- Format: ELF32 ARM
- Dependencies include `libsecveSrkCommon.so` and `libstlport.so`.
- The copies extracted from the S4 and Note 4 firmware used during this reverse are byte-identical.

The common JNI/event behavior was cross-checked against `libsecveSrkCommon.so` and the Samsung `EffectView`/Abstract Tiles smali. The original binary is the source of truth for geometry, animation, timing and render order. The app-owned ARM64 port is not expected to reproduce Samsung's private C++ ABI, object layout or `stlport` dependency.

Useful reverse facts:

- `createScene`: raw VA `0x19E20`, Ghidra image address `0x29E20`
- scene allocation: `0x5FC` bytes
- vtable: raw VA `0x1CD28`, file offset `0x1BD28`

Recovered scene vtable:

| Slot | Behavior | Raw VA | Ghidra address |
|---:|---|---:|---:|
| 0 | viewport | `0x3A1C` | `0x13A1C` |
| 1 | clear/reset | `0x15740` | `0x25740` |
| 2 | unlock affordance | `0x9EE0` | `0x19EE0` |
| 3 | unlock animation | `0x1169C` | `0x2169C` |
| 4 | resize | `0x1560C` | `0x2560C` |
| 5 | portrait initialization | `0x186C0` | `0x286C0` |
| 6 | landscape initialization | `0x186F8` | `0x286F8` |
| 7 | draw/update | `0x13214` | `0x23214` |
| 8 | touch DOWN | `0x1AF7C` | `0x2AF7C` |
| 9 | touch UP | `0x79FC` | `0x179FC` |
| 10 | touch MOVE | `0x3A7C` | `0x13A7C` |
| 11-21 | no-op | `0x1E60` | - |

Large Ghidra project databases and bulk decompiler dumps are investigation artifacts, not required inputs to the port and should not be added to production commits. This specification and the original vendor binary are sufficient for implementation review.

## 2. Runtime pipeline

The logical OEM pipeline is:

1. Java/common JNI queues lifecycle, resize and touch events.
2. At the beginning of a draw, the common layer consumes queued events.
3. The scene updates active triangle transforms, touch response, ray response, reset interpolation and scatter animations using absolute monotonic time.
4. Per-triangle values are expanded to per-vertex arrays.
5. The stock renderer draws Background, Tile, Line and Scatter.
6. Drawing continues while the native animator manager has active records, then the Java wrapper can return to `RENDERMODE_WHEN_DIRTY`.

Within each frame, the recovered update order is:

1. update triangle transform records;
2. update touch-proximity alpha;
3. update the eight ray paths;
4. apply optional reset interpolation;
5. update held/affordance scatter;
6. expand per-triangle values to render vertices.

Order matters. In particular, do not evaluate scatter first and do not replace the animator manager with frame-counted state.

## 3. Geometry

### 3.1 Orientation constants and counts

| Orientation | Nominal columns | Nominal rows | Inclusive lattice | Triangles | Vertices |
|---|---:|---:|---:|---:|---:|
| Portrait | 5 | 13 | `(5 + 1) * (13 + 1) = 84` | 336 | 1008 |
| Landscape | 8 | 8 | `(8 + 1) * (8 + 1) = 81` | 324 | 972 |

The loops are inclusive: `i = 0..cols`, `j = 0..rows`. Each lattice coordinate produces two interlaced diamonds. Each diamond is split into two triangles, therefore every lattice coordinate emits four triangles or twelve `vec2` vertices. Earlier estimates based on one diamond per coordinate are incorrect.

The nominal visible-screen coverage is approximately 260 portrait or 256 landscape triangles; the remaining geometry is deliberate overscan.

### 3.2 Exact mesh construction

Let:

```text
wx = 1 / cols
hy = 1 / rows

xL = 2*i*wx - 1
xC = xL + wx
xR = xL + 2*wx

yT = 1 + hy - 2*j*hy
yC = 1      - 2*j*hy
yB = 1 - hy - 2*j*hy
yN = yB - hy
```

Emit the following four triangles in this order:

```text
# first diamond
(xL,      yT), (xC, yC), (xL,      yB)
(xR,      yB), (xC, yC), (xR,      yT)

# second, interlaced diamond
(xL - wx, yC), (xL, yB), (xL - wx, yN)
(xL + wx, yN), (xL, yB), (xL + wx, yC)
```

The draw primitive is `GL_TRIANGLES` (`mode = 4`). There is no index buffer requirement in the OEM layout: vertices are expanded.

### 3.3 Triangle records and deformation

The OEM runtime record is 44 bytes per triangle. The port does not need to copy the ABI, but it does need equivalent state:

- three original vertices;
- one randomly selected pivot vertex `P`;
- the other two vertices `A` and `B`;
- precomputed deltas `dA = s * (A - P)` and `dB = s * (B - P)`;
- absolute animation start and end times;
- prior progress so the current frame applies `(progress - previousProgress) * delta`;
- per-triangle alpha/brightness/scatter channels and active flags.

At animation completion, restore the original geometry exactly. Do not accumulate floating-point drift between gestures.

Normal pop behavior:

- deformation scale `s`: randomly `0.5` or `1.0`, resulting in approximately `1.5x` or `2.0x` expansion about the pivot;
- duration: `0.4 s`;
- stagger: `0.02 s` between triangles actually activated;
- brightness target: approximately `[-0.375, +0.375]`;
- base seam/alpha contribution: approximately `0.3`.

Unlock behavior:

- deformation scale `s`: randomly `1.0` or `2.0`, resulting in approximately `2x` or `3x` expansion;
- duration: `0.9 s`;
- stagger: zero;
- probability: `0.8` inside the activation region, `0.016` outside it;
- squared distance cutoff: `20 * 0.1406 = 2.812`, measured in `[0,1]`
  normalized coordinates rather than `[-1,1]` clip coordinates.

Recovered cleanup constants are `0.08 s` and `0.3 s`. Release does not immediately reset the entire mesh: delayed animations that have not started are removed, while animations already in progress are allowed to complete and restore their own geometry.

### 3.4 Physical radius and proximity

The mesh radius is:

```text
r = hy * sqrt(abs((2*wx - hy) / (2*wx + hy)))
```

The engine also retains `r*r`, `2*r` and `35*r`.

The extended ray-candidate threshold depends on display aspect ratio:

```text
extendedRadiusSquared = (screenWidth / screenHeight < 0.82)
    ? 3.25 * r*r
    : 1.25 * r*r
```

All geometry and distances above are in normalized/clip space, not pixels.

## 4. Background UV mapping

The OEM renderer samples the screenshot as a center-cropped color source named logically `"bg"`.

```text
sx = screenWidth  / textureWidth
sy = screenHeight / textureHeight

cropX = sy > sx  ? abs(sx/sy - 1)/2 : 0
cropY = sy <= sx ? abs(sy/sx - 1)/2 : 0

u = cropX + (1 - 2*cropX) * (1 + xClip)/2
v = cropY + (1 - 2*cropY) * (1 - yClip)/2
```

Use the actual cached screenshot dimensions. Do not assume that the background texture has the same aspect ratio or dimensions as the current effect surface. Fold screen switching is an LLE host concern; each screen's active cached screenshot must enter this same mapping independently.

## 5. Touch coordinate system and gesture state

### 5.1 JNI normalization

The common native layer converts raw screen coordinates as follows:

```text
nx = rawX / width
ny = (height - rawY) / height

xClip = 2*nx - 1
yClip = 2*ny - 1
```

`yClip` is already GL-up. Do not invert Y again in Java, JNI or the core.

The Samsung Java host uses `MotionEvent.getActionMasked()` and `getRawX()/getRawY()`, with float-to-int truncation before the native call. The accompanying `View` argument is ignored by the Abstract Tiles native scene.

Only DOWN, UP and MOVE are routed by the OEM host. `POINTER_DOWN`, `POINTER_UP`, `CANCEL` and `OUTSIDE` do not invoke a scene handler. Hover event slots are inherited no-ops. LLE may map CANCEL to UP as a safety adaptation, but that is not original behavior.

### 5.2 Aspect-corrected distances

To make the normalized touch distance visually isotropic:

```text
portrait:  scaleX = 1,                  scaleY = height/width
landscape: scaleX = width/height,       scaleY = 1

d2 = ((x1 - x2) * scaleX)^2 + ((y1 - y2) * scaleY)^2
```

MOVE advances the accepted trail point only if:

```text
normalized d2(oldAccepted, currentTouch) > 0.25 * hy*hy
clip-space d2(oldAccepted, currentTouch) > hy*hy
```

The live center and MOVE flag still update on every MOVE while the gesture is
active. The handler does not directly launch a pop batch or rebuild rays. The
threshold only advances the accepted trail point; the `0.16 s` scheduler uses
the live center.

### 5.3 Repeated batches while held

The batch cooldown is exactly `0.16 s`. While the primary touch remains held, the batch routine runs again after each cooldown even if the finger is stationary, giving approximately `6.25` activation batches per second. Each batch skips triangles that already have active records.

For the normal batch:

- near squared-distance threshold: `0.1406`;
- activation probability near: `0.8`;
- activation probability far: `0.016`;
- stagger: `0.02 s` per triangle actually activated, not per triangle merely visited.

## 6. Rays and randomization

The scene constructor seeds C `rand()` with `time(NULL)`. The effect is intentionally not deterministic between scene constructions.

There are eight ray paths. For ray `k`:

```text
theta[k] = k * (pi/4) + rand() * ((pi/4) / RAND_MAX)
theta is wrapped into [0, 2*pi)
```

Recovered constants:

- `pi/4 = 0.7853982`
- `2*pi = 6.2831855`
- trigonometric lookup scale `1024/(2*pi) = 162.974655`
- ray reach `10 * sqrt(wx*wx + hy*hy)`
- traversal aspect-distance-squared stop threshold `0.8`

Random pivot and permutation selection use independent 1024-entry precomputed
tables. They are filled from Bionic `rand()` starting at seed 1: all float values
first, then all unsigned values. Both cursors pre-increment and wrap. Before
each Tile permutation, the unsigned cursor resets to zero; every slot swaps with
`nextUIntLUT() % triangleCount`. Scene ray generation then uses `srand(time)`.

## 7. Scatter scheduler and affordance

The recovered scheduler can be represented as:

```text
scatter(center, radius, multiplier, riseDuration)
```

For an idle triangle at aspect-corrected centroid distance `d`:

- it is eligible only when `d <= radius`;
- it is also required that `2*r/d <= 1`;
- start delay is `d * multiplier - 0.1`;
- white amplitude is randomized;
- alpha seed is `0.001`;
- rise is `0 -> amplitude` over `riseDuration`;
- fall is `amplitude -> 0` over `riseDuration + 0.2`, starting after the rise.

Normal held scatter uses `(radius=0.6, multiplier=0.5, rise=0.5)`. Its maximum tail is approximately:

```text
0.2 delay + 0.5 rise + 0.7 fall = 1.4 s
```

Affordance scatter uses `(radius=2.0, multiplier=0.5, rise=0.3)`. Its maximum tail is approximately:

```text
0.9 delay + 0.3 rise + 0.5 fall = 1.7 s
```

These totals match the measured OEM tails of about `1.412 s` and `1.721 s`.

Before clip-space conversion, the affordance center is shifted upward by `hy/2`. The affordance entry point first performs a complete clear/reset and clock reset, then schedules its scatter. Do not layer a hint onto stale touch records.

## 8. Seam/line pass

The OEM engine does not outline the whole grid. It draws exactly eleven selected seam strips per orientation. Each strip is a quad rendered as two triangles `(A,C,D)` and `(A,B,C)`.

The line-mask atlas has width 56 pixels. The U columns, in strip order, are:

```text
26, 18, 46, 2, 6, 14, 34, 22, 30, 38, 42
```

Normalize each with `u = pixelX / 56`; V is `0` or `1`.

Portrait vertex tuples `(A,B,C,D)`:

```text
26: (171,  16,  19, 173)
18: (103, 267, 268, 101)
46: (441, 290, 288, 443)
 2: (952, 918, 881, 951)
 6: (962, 856, 857, 961)
14: (603, 374, 372, 605)
34: (531, 638, 636, 533)
22: (243, 309, 310, 245)
30: (747, 854, 852, 749)
38: (909, 794, 792, 911)
42: (773, 576, 578, 771)
```

Landscape vertex tuples `(A,B,C,D)`:

```text
26: ( 10, 216, 218,  13)
18: (  4, 236, 235,   6)
46: (880, 732, 679, 883)
 2: (481, 423, 364, 373)
 6: (211, 208,  94, 103)
14: ( 34, 238, 237,  37)
34: ( 28, 202, 205,  31)
22: ( 16,  80,  79,  19)
30: (886, 544, 547, 889)
38: (892, 640, 643, 895)
42: (910, 682, 685, 913)
```

The sole Abstract Tiles special resource is:

- LLE path: `res/drawable-nodpi/special_abstracttile_linemask.png`
- dimensions: `56 x 62`, ARGB
- size: 610 bytes
- SHA-256: `523B2345EF2DFDC11D6DEDAF4B9EB818F1E0CB96D4A95E18BA3BC527C36B699B`
- sampling: linear filtering, clamp-to-edge

Keep it in `drawable-nodpi`; Android density scaling changes the atlas columns and breaks the hardcoded U coordinates.

## 9. Shaders and draw state

### 9.1 Stock shader contracts

The tile vertex stage consumes:

- `aPos: vec2`
- `aTex: vec2`
- `aAlpha: float`
- `aBri: float`

It forwards UV, alpha and brightness and writes the clip-space position directly. The stock fragment result is equivalent to:

```glsl
vec4(texture2D(uTextureOrigin, uv).rgb + brightness, alpha)
```

The scatter vertex stage sums its three scalar alpha channels:

```text
alpha = aAlpha + aAlphaRandom + aAlphaScatter
```

The stock scatter fragment result is white:

```glsl
vec4(alpha, alpha, alpha, 1.0)
```

The line vertex stage consumes position, line-atlas UV and background UV. The stock line fragment samples the line mask, discards zero-alpha texels, and outputs background RGB with the mask alpha.

Unlock drives a separate cosine scalar from 0 to 1 over `0.4 s`. For each Line
vertex, progress below its recovered binary threshold keeps position fixed and
scrolls background UV by `-progress * delta`; at/above the threshold position is
`start + (progress - threshold) * delta`, while background UV retains the value
reached at the threshold. Line uses full `mask.a`, not Tile alpha. On LLE's
transparent surface the pass is gated to `unlockProgress > 0`: at progress zero
the OEM quads are visually neutral only because they exactly cover its opaque
Background, whereas drawing them over the live lockscreen exposes stale UI
pixels.

Host note: the unified host currently provides a complete lockscreen capture
from `AccessibilityService.takeScreenshot()`, not a wallpaper-only texture.
Moving the exact eleven Line slabs therefore also displaces clock, weather and
status pixels. That displacement is accepted for ARM64 fidelity and the recovered
Line pass is enabled during unlock. At progress zero it remains gated to avoid a
second, static copy of those pixels on LLE's transparent overlay. ARM32 still has
its older discard patch and must be validated separately before changing it.

### 9.2 Stock draw order and state

Recovered stock sequence:

1. disable depth testing;
2. disable blending;
3. draw opaque Background;
4. enable blending;
5. draw Tile;
6. draw Line;
7. set `glBlendFunc(GL_ONE, GL_ONE)`;
8. draw Scatter.

Only Scatter is deliberately additive. Do not render Tile or every line additively just because the last explicit blend function in the routine is `ONE, ONE`.

Recovered array groupings, useful when comparing decompiler output:

| Pass | Scene offsets |
|---|---|
| Scatter | position `+0x160`, alpha `+0x178`, random alpha `+0x558`, scatter alpha `+0x57C` |
| Tile | position `+0x2B8`, alpha `+0x2D0`, UV `+0x2E8`, brightness `+0x59C` |
| Line | position `+0x410`, line UV `+0x5C8`, background UV `+0x5D4` |

Renderer members map as `+0x538 = Scatter`, `+0x53C = Tile`, `+0x540 = Line`.

## 10. Timing model

The native engine uses `clock_gettime` and stores absolute start/end times in seconds. All animation progress is elapsed-time based and independent of display refresh rate.

Consequences for the port:

- never implement physics as a fixed number of frames;
- never multiply speeds by `refreshRate / 60`;
- a 60 Hz and 120/144 Hz display must show the same duration and trajectory;
- a Java-side `33 ms` render-request cadence is acceptable as an optional LLE performance cap, provided every native update uses real elapsed time;
- long or irregular frames advance animations by elapsed time rather than slowing the effect.

The stock DEX does not contain the external `33 ms` sleep found in the existing LLE ARM32 integration. That cap is a measured app-level parity/performance patch, not native Abstract Tiles physics.

## 11. Lifecycle and event semantics

### Clear/reset

The common layer forces all held-pointer flags false and invokes UP handlers before the Abstract Tiles clear routine. The scene then rebuilds/zeros state. Clear must leave no active gesture, delayed pop, brightness, ray or scatter record.

### Resize/orientation

Resize chooses the portrait or landscape topology and rebuilds the mesh and dependent buffers. UVs and the eleven seam strips must be rebuilt for the new orientation. Do not scale a portrait vertex buffer into landscape.

### Affordance

The common path performs a full clear and clock reset before invoking the scene affordance behavior. The Abstract Tiles `setParameters` path is effectively ignored.

### Unlock

Unlock is invoked directly. The scene internally performs its UP/release behavior before scheduling the stronger unlock deformation.

### Release

UP stops creation of new held batches, drops delayed records that have not begun and permits already-started records to complete and restore. An immediate `resetAllVertices()` on UP is visibly wrong.

### Rendering lifetime

The Abstract Tiles draw slot returns zero. The common manager, not that return value alone, keeps rendering while animator records are active and falls back to when-dirty rendering after the final tail completes.

LLE-specific `nativeRealign`, CANCEL-as-UP and fold surface/cache repair are safe hardening hooks, but they must be documented as host adaptations and must not alter normal DOWN/MOVE/UP math.

## 12. Audio parity

Audio is managed by the Java host, not the visual core:

- play tap on DOWN;
- begin drag behavior after `411 ms`;
- volume fade tick: `10 ms`;
- release fade decrement: `0.039` per tick;
- unlock fade decrement: `0.059` per tick;
- release SoundPool resources `2000 ms` after unlock;
- visual unlock animation has zero delay;
- honor `lockscreen_sounds_enabled`.

Use LLE's modern SoundPool lifecycle while retaining these timings and the original raw assets already present in the unified resources.

## 13. Required transparent-overlay adaptation

Samsung rendered Abstract Tiles as an opaque full-screen scene with its own Background pass. LLE renders over the real lockscreen through a translucent accessibility surface. Copying the stock framebuffer behavior literally produces a black/opaque fullscreen rectangle or a duplicated screenshot. The following differences are required boundary adaptations, not artistic deviations.

### 13.1 Framebuffer and background

- Request an RGBA translucent EGL configuration and transparent surface format.
- Clear to `(0,0,0,0)`.
- Do not draw the OEM Background pass.
- Keep the cached screenshot only as the Tile and Line color source.
- Preserve the OEM pass order among visible effect passes: Tile, Line, Scatter.

### 13.2 Tile and line composition

The Android compositor expects premultiplied color. Tile output must therefore contribute:

```text
rgb = clamp(sampledBackground.rgb + brightness, 0, 1) * alpha
a   = alpha
```

Line output must premultiply sampled background RGB by mask alpha in the same manner. Use one consistent premultiplied path: either write premultiplied output directly to a clear target or use `GL_ONE, GL_ONE_MINUS_SRC_ALPHA` without multiplying twice.

This preserves the OEM appearance inside affected tiles while leaving the rest of the lockscreen genuinely transparent.

### 13.3 Scatter composition

The OEM scatter shader writes alpha `1.0` because its destination is already opaque. On LLE that creates opaque white triangles. Preserve its additive white RGB behavior but do not raise the surface coverage alpha solely for Scatter. The current LLE composition contract uses an additive RGB contribution with zero surface alpha for this pass. Validate this on the actual SurfaceFlinger/device path; if a device clamps zero-alpha RGB, composite Scatter into the alpha-bearing local Tile region rather than reverting to fullscreen opacity.

### 13.4 App-owned runtime

- JNI entry points and native object ownership belong to LLE.
- Do not depend on Samsung's private `libsecveSrkCommon.so`, ARM32 `stlport`, mangled C++ ABI or original factory/vtable.
- Load the ARM64 engine lazily only when Abstract Tiles is selected.
- Release GL resources on the GL thread and make repeated attach/detach idempotent.
- Fold caches, per-display screenshots and per-display enablement are host integration, not engine physics.

## 14. Suggested ARM64 module boundary

A maintainable implementation can be split into three layers:

```text
abstract_tiles_core.c
  deterministic mesh construction
  touch state, batches, rays, scatter and absolute-time animators
  no JNI and no GLES calls

abstract_tiles_gles.c
  shader/program creation
  background and line-mask textures
  dynamic vertex-array upload
  transparent Tile -> Line -> Scatter render path

abstract_tiles_jni.c
  Java handle lifecycle and synchronization
  resize/background/touch/clear/affordance/unlock entry points
  elapsed-time handoff and error reporting
```

The core should expose debug snapshots for tests: orientation, vertex/triangle counts, current normalized touch, active animator count and current elapsed time. Do not expose Ghidra-derived object offsets as public API.

## 15. Fidelity acceptance checklist

### Geometry

- [ ] Portrait produces exactly 1008 vertices / 336 triangles.
- [ ] Landscape produces exactly 972 vertices / 324 triangles.
- [ ] Both interlaced diamonds are emitted at every inclusive lattice point.
- [ ] Geometry is restored exactly after each completed animation.
- [ ] Resize rebuilds the correct orientation rather than scaling the old mesh.

### Touch and timing

- [ ] Raw Y is converted to GL-up exactly once.
- [ ] Distance tests use aspect-corrected normalized coordinates, not pixels.
- [ ] MOVE threshold is normalized `0.25 * hy^2` (clip-space `hy^2`) while the live center still follows continuously.
- [ ] Held touch repeats batches every `0.16 s`, including when stationary.
- [ ] Animation progress uses real elapsed seconds at 60, 120 and 144 Hz.
- [ ] UP does not immediately snap all geometry back.
- [ ] Touch tail is about 1.4 s and affordance tail about 1.7 s.

### Appearance

- [ ] Normal pop uses `s = 0.5/1.0`, duration `0.4 s`, stagger `0.02 s`.
- [ ] Unlock uses `s = 1.0/2.0`, duration `0.9 s`, no stagger.
- [ ] Near/far probability is `0.8/0.016`.
- [ ] Eight randomized rays are present and vary between scene constructions.
- [ ] The 48-entry ARM64 ray guard is never reached before the recovered `d^2 >= 0.8` stop.
- [ ] Exactly eleven seam strips are drawn, using the orientation-specific tuples.
- [ ] The 56x62 line mask is loaded without density scaling.
- [ ] Background sampling is center-cropped with the recovered UV formula.

### Render/composition

- [ ] LLE clears RGBA to transparent and skips only the OEM Background pass.
- [ ] Visible pass order remains Tile, Line, Scatter.
- [ ] Only Scatter is additive.
- [ ] Tile and Line RGB are correctly premultiplied for the Android translucent surface.
- [ ] Scatter does not create opaque white triangles.
- [ ] Unaffected pixels remain `(0,0,0,0)`; there is no fullscreen screenshot duplication.

### Lifecycle

- [ ] Affordance performs a full clear before scheduling.
- [ ] Unlock first releases the held gesture.
- [ ] Clear removes all active and delayed state.
- [ ] Surface recreation and repeated effect selection do not leak programs, textures or native handles.
- [ ] Missing/stale screenshot state fails transparent and recovers when a valid cache arrives.

## 16. Known OEM-to-LLE divergences

| Area | OEM ARM32 | LLE ARM64 requirement |
|---|---|---|
| Scene background | Opaque screenshot Background pass | Skip pass; real lockscreen stays visible |
| Surface clear | Opaque scene | RGBA transparent clear |
| Tile/Line RGB | Written into an already opaque target | Premultiply for Android translucent composition |
| Line input | Wallpaper-only texture over identical opaque Background | Disabled until the host can provide wallpaper without lockscreen UI |
| Scatter alpha | White RGB with alpha 1 | Additive local RGB without making white opaque tiles |
| Runtime | Samsung common library, C++/stlport | App-owned C/JNI/GLES implementation |
| Frame scheduling | Animator manager; elapsed-time physics | Same elapsed-time physics; ~60 fps presentation request independent of 60/120 Hz panel mode |
| CANCEL | Not routed to scene | May be mapped to UP for safety |
| Realignment | No app-specific hook | Allowed to recover overlay/screenshot coordinate changes |
| Fold support | Not present in original engine | Host chooses active display/cache; core math unchanged |
| Random testability | `srand(time(NULL))` | Same in production; optional fixed debug seed |

These are the only intended behavioral divergences. Gesture geometry, probabilities, timings, random path count, mesh topology, seam selection and shader math should remain OEM-derived.

## 17. Review blockers

Do not call the port fidelity-ready if any of the following is true:

1. the mesh uses 1008/972 *triangles* rather than vertices, or emits only one diamond per lattice point;
2. every tile edge is outlined instead of the eleven OEM seam strips;
3. touch motion is mirrored because Y is inverted twice;
4. animation speed changes with refresh rate;
5. batches stop when a held finger is stationary;
6. UP globally resets vertices immediately;
7. the line mask is density-scaled or sampled with the wrong atlas columns;
8. Tile, Line and Scatter are all rendered with additive blending;
9. the engine draws the screenshot fullscreen on LLE's transparent overlay;
10. a missing background texture leaves a black or opaque surface;
11. affordance is scheduled on top of stale touch state;
12. production randomness is replaced by a fixed path.

Passing this document's static checklist should be followed by device comparison against the ARM32 original using the same screenshot and gesture traces. Visual tuning is appropriate only after the exact topology, event semantics, elapsed-time model and transparent composition boundary have been verified.
