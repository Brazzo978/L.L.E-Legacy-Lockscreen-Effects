# Geometric Mosaic ARM64 Java divergence audit

Audit target: `src/com/codex/lle/GeometricMosaicGlesPipeline.java`, 1,099-line
post-mask-rewrite snapshot written 2026-07-16 14:56:33.

Authority: `EXACT-PIPELINE-SPEC.md`, literal GLSL strings in
`vendor/original-native/libsecveGeometricMosaic.so`, and the Ghidra artifacts
in this directory. "Certain" below means that the ARM32 binary establishes a
different operation. "Open" means the external `vi` renderer or the Java-side
ARM32 caller is needed before either implementation can be called correct.

## Result

The normal tap/drag/unlock path is now structurally faithful enough for the
next device test. There is no known Java divergence that should block that
install. In particular, the rewrite has removed the large errors that were
capable of explaining the previous oversized, two-triangle result.

It is not yet honest to label the whole effect 1:1. The largest remaining
feature omission is the special `scene+0xe4` radial field, which is likely
relevant to the lock-screen affordance/hint path. The largest remaining
ordinary-render differences are GLSL precision/layout rather than the physics
or pass graph. The unknown `vi` texture defaults and animator easing also still
need an ARM32 GL trace or original-device capture.

## What now matches the recovered ARM32 engine

| Area | Java evidence | Audit result |
|---|---|---|
| Mask topology | lines 26-27, 438-479 | Exact 12 vertices per cell, four center-facing triangles, side-midpoint samples, and the four `min` formulas. |
| Mask scale | lines 126-129, 492-495 | Uses the aspect-normalized base-renderer multipliers recovered after the original spec was written: portrait `1,H/W`, landscape `W/H,1`. This is the correction expected to remove the old wide touch field. |
| Record pool | lines 63-64, 235-243, 264-268, 407-411 | Exact 100-slot free stack semantics: initialize `0..99`, pop from the end, reject when empty, push reclaimed indices. |
| Record radii | lines 376-403, 686-725 | New point `0.3 -> 0.8` in 0.15 s; old record retreats to 0.3, current record to 0, both over 0.6 s; current unlock expands to 5 in 0.45 s and survives for 2 s. |
| Unlock selection | lines 300-309 | Operates only on the current active record and returns if none is active, matching `FUN_1dac8`. |
| Ring timing | lines 39-48, 426-435 | All five radius keyframes and `clamp(1.5 - 3r/(2B))` match. Additional points in one held stroke do not restart them. |
| Ring binding | lines 516-535 | Uses the literal three-ring branch mapping and two-ring descending branch, with color buffers moved with their ring. |
| Circle meshes | lines 770-848 | Correct row-major centers, oversized quads, `0,1,2,2,1,3` vertex order, and separate 30/28-cell lattices. |
| Color lattice | lines 738-818 | Exact five independent 15-draw buffers and exact repeated first/second-lattice patterns; total palette consumption is 45+30 draws. |
| Circle aspect | lines 545-549 | Uses `(((L/21)*10)/((S/12)*6))^2`, not the slightly different ratio of the integer-rounded FBO dimensions. |
| Target sizes/routes | lines 136-169, 578-609 | Quarter-screen mask; 12x21/21x12 color/noise sources; recovered circle sizes; all six final samplers route to the correct source. |
| Required explicit texture state | lines 136-169, 654-664 | Color-origin is nearest/clamp, mask is clamp, circles are repeat. Defaults that the original did not explicitly set remain an open item below. |
| GL alpha texture | lines 160-169 | One-byte `GL_ALPHA`, consumed through `.a` in the final shader. |
| Prepass math | lines 578-587, 1056-1063 | 121 samples, exact luma weights, dark-color `+0.3`, and nearest 12x21/21x12 destination. |
| Final blend chain | lines 590-613, 1065-1098 | Correct sampler coordinates, six blend stages, transparent square-root tail, and `SRC_ALPHA, ONE_MINUS_SRC_ALPHA`. |
| Time basis | all animation methods | Absolute nanosecond-derived seconds, not frame counts; therefore 60/120/144 Hz does not change animation duration. |

## Remaining certain divergences

### D1. Special `scene+0xe4` field is absent

Severity: **high for hint/affordance fidelity; no blocker for ordinary tap/drag**
Confidence: **certain omission; trigger lifecycle still open**

`rawMaskCoverage` at lines 485-499 seeds coverage with zero and only scans touch
records. The ARM32 `FUN_16084` has another recovered seed when `scene+0xe4` is
active:

```text
d = length(((P.x-Q.x)*scaleX, (P.y-Q.y)*scaleY))
K = 10.5 / (r + 1.8)^3
special(P) = K * (0.5 - abs(r-d))
```

The active records then take a maximum over this seed. There are no Java fields
for the special flag, `Q`, or `r`. `addAffordance` at lines 293-297 instead emits
an ordinary touch sample and immediately ends the stroke.

Concrete next step: implement the formula in a separate state object, but do
not guess when it starts, how `Q` is supplied, or how `r` is animated. Those
three lifecycle facts remain external to this `.so`; recover them from the
original caller or an original-device event/GL trace first.

### D2. The GLSL precision/layout is not literal

Severity: **medium; potentially visible at band and block boundaries**
Confidence: **certain source divergence, low-to-medium pixel impact**

The exact ARM32 circle shaders declare `precision mediump float` and use
default-mediump `UV`, center, and color varyings. Java lines 1013-1048 instead
make position/center explicitly highp and color explicitly lowp. The palette is
already 8-bit and constant per quad, so the lowp color change is unlikely to be
visible; edge interpolation can still differ by a pixel.

More importantly, the ARM32 fullscreen vertex shader emits both:

```glsl
varying vec2 UV;            // mediump
varying highp vec2 UVhighp;
```

Mask and circle sampling use `UV`; the block-shift and color-origin coordinates
use `UVhighp`. Java lines 1050-1054 and 1065-1098 collapse both into one highp
`UV`. This can move quantized mask/circle edges or a staggered block boundary,
especially on a fragment implementation with the minimum ES2 mediump range.

Concrete fix: port the literal vertex/varying declarations and use `UVhighp`
only in the three block coordinates. Preserve the current transparent patched
tail. The original MVP path may remain identity if the Java quad is already in
clip space; do not introduce a transform merely to retain an unused uniform.

### D3. Java RNG is not libc `rand()`

Severity: **low visual impact, but not bit-for-bit stochastic parity**
Confidence: **certain**

Lines 107, 146-169, and 813-817 use `java.util.Random`. ARM32 calls
`srand(time(NULL))`, uses `rand()%10` for 75 palette selections, then writes the
low byte of 252 further `rand()` results. The Java implementation now consumes
the correct number of values in the correct order, but the generator,
`nextInt(10)` mapping, and `nextInt(256)` bytes differ.

This does not explain a stable size, timing, or topology mismatch: both engines
choose a new nondeterministic valid mosaic. It matters only for exact seeded
reproduction and tiny distribution differences. If deterministic cross-ABI
comparison is desired, implement the old device libc generator and its modulo/
low-byte operations behind the existing seed hook.

### D4. Prepass texel size is the surface size, not the uploaded source size

Severity: **medium only when bitmap and GL surface dimensions differ**
Confidence: **certain conditional divergence**

Lines 584-585 set `uTexel=(1/width,1/height)`. The recovered shader expects the
texel size of `uTextureOrigin`, i.e. the actual uploaded screenshot. They are
normally identical on the current S23 test. They can differ after a Fold
cover/main cache swap, scaling, cropping, or a capture taken at another
resolution.

Concrete fix: save `bitmap.getWidth()` and `bitmap.getHeight()` after a
successful upload and use their reciprocals in `renderBlur`. This change is
safe for equal-size screenshots and prevents the 21x21 blur footprint from
changing on mismatched Fold caches.

### D5. Forced input semantics are not represented

Severity: **low; event-specific**
Confidence: **certain API difference, uncertain current call-site impact**

`FUN_1a54c` has a forced parameter that bypasses the 0.017 clip-space movement
threshold. Java `addTouch` has no forced argument, and `addAffordance` calls the
ordinary filtered path at lines 293-297. Most affordances are accepted because
the method ends its previous stroke, but an affordance arriving while another
stroke is marked active can be rejected when close to the last point.

Concrete fix: use a private `addTouch(..., boolean forced)` implementation;
skip only the distance test when forced, while retaining the 100-slot capacity
test. Route the caller proven to correspond to the ARM32 forced entry point to
`true`.

### D6. Circle FBOs are redrawn unconditionally

Severity: **low, primarily CPU/GPU work**
Confidence: **certain frame-order difference, normally output-equivalent**

Lines 326-331 always clear and render both circle targets on every requested
frame. `FUN_1d394` does so only while an ordinary touch field or special
expansion is active. With all ring alphas at zero the Java clear/draw produces
the same transparent textures, so this is not a normal visual mismatch.

Concrete fix: have the touch-mask update expose the equivalent of
`scene+0x130` and render circle3/circle2 only when it or the future special flag
is active. This is an optimization/fidelity cleanup, not a prerequisite for
the next visual test.

### D7. Literal final-shader control flow was simplified

Severity: **very low**
Confidence: **certain source difference, output-equivalent for finite inputs**

The original shader evaluates the mosaic inside `if (backgroundAlpha < 1.0)`
and otherwise returns the background. The transparent patch changes those two
outputs to the square-root mosaic and transparent zero. Java lines 1077-1098
evaluate the chain everywhere and multiply it by zero outside the mask.

All current operations are finite for valid textures and nonzero block sizes,
so the result is equivalent. Restoring the branch would avoid unnecessary
texture reads outside the mask and remove the theoretical `NaN*0` portability
edge, but it should not be used to explain the reported 60% similarity.

## Probable or integration-level differences

### P1. Stroke-active proxy

Ring restart at lines 255 and 273-276 is keyed by `hasLastTouch`, which is an
Android gesture proxy. ARM32 keys the ring reset from native scene/input state.
For the present DOWN/MOVE/UP host mapping these agree. Multi-touch gating,
cancel/re-entry, or a synthetic event can expose a difference. Validate with an
event trace before changing it; repeated MOVE samples correctly do not restart
the rings.

### P2. Coordinate inversion belongs to the caller boundary

Line 268 stores Java top-origin input as `clipY=1-2*y`. `FUN_1a54c` itself
stores `2*v-1` without an inversion, but the native Java/caller convention is
outside that function. Current Android input is top-origin and device testing
has not reported vertical reversal after the recent corrections, so this is
not presently classified as a bug. It remains a boundary to verify against a
single known ARM32 touch location.

### P3. Linear interpolation is assumed

All Java radius/alpha transitions are linear. The ARM binary gives exact keys,
times, and animation flag `0x0100`; the human-readable easing implemented by
the external animator remains unknown. Device motion should be compared at
normalized times (for example 0, 25, 50, 75, 100%), not by frame number.

## Open GL state: do not tune blindly

The following are intentionally unresolved rather than confirmed bugs:

- Java sets linear min/mag on mask and both circle targets, and nearest on the
  random alpha texture. ARM32 explicitly proves mask clamp, circle repeat, and
  color-origin nearest/clamp, but the `vi` constructor defaults for the other
  min/mag states are external.
- Standard straight-alpha blending is a strong inference and Java applies it
  consistently at lines 471, 542, and 596. This is the only blend mode that
  pairs correctly with the recovered intermediate shaders and square-root
  transparent tail.
- Blend equation, scissor, depth, cull, and any renderer-retained state are not
  established by this `.so`. The dedicated Java GL context starts with the
  relevant ES defaults, so changing them without an ARM32 trace would add a
  guess rather than remove one.

Use GL interception on ARM32 to close these points. Do not adjust filters based
on one screenshot: filter changes can hide a scale/topology error while making
the actual implementation less faithful.

## Next-test gate

The current snapshot is suitable for an ARM64 device test of two separate
paths:

1. A short tap/drag that never triggers unlock, to judge the corrected local
   footprint, four-face boundary, and trail decay.
2. A normal unlock gesture, to judge the intentional radius-5 expansion and
   0.6-second global fade separately from touch size.

Do not use the hint/affordance animation as the acceptance test for the normal
path until D1 is implemented or its original lifecycle is disproved. A clean
normal-path result would raise confidence substantially, but the honest 1:1
label still requires the GLSL precision cleanup plus closure of the documented
open animator/filter state.
