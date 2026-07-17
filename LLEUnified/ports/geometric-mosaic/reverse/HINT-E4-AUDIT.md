# Geometric Mosaic ARM32 special hint (`scene+0xe4`) audit

Date: 2026-07-16

## Result

The `scene+0xe4` path is the stock Geometric Mosaic unlock-affordance/hint.
It is **not** an ordinary touch sample. The public affordance command clears the
scene, then starts a two-second expanding radial band centered at the requested
rectangle center. The exact special state is:

```text
scene+0xe4  special field active (byte)
scene+0xf8  special radius (float)
Qx/Qy       process globals at raw 0x1d22c/0x1d230
             (Ghidra 0x2d22c/0x2d230)
```

For activation time `T`:

```text
active(t) = true  for T <= t <= T+2.0
            false for t > T+2.0

r(t) = -0.8 + (3.0 - (-0.8)) * clamp((t-T)/2.0, 0, 1)
     = -0.8 + 1.9*(t-T), while T <= t < T+2.0
```

The float interpolation is literally linear. The active flag is a timed step:
it remains `1` through the end timestamp and becomes `0` on the first animator
update whose time is greater than `T+2.0`.

The special center comes from Android pixel coordinates `(x,y)` as:

```text
u  = x / W
v  = (H-y) / H
Qx = u - 0.5 = x/W - 0.5
Qy = v - 0.5 = 0.5 - y/H
```

This half-clip convention is counterintuitive but certain. Relative to the
ordinary Geometric touch coordinates, it is exactly:

```text
ordinaryX = 2*u - 1       specialQx = ordinaryX/2
ordinaryY = 2*v - 1       specialQy = ordinaryY/2
```

The normal lock-screen caller supplies the center of the requested `Rect`, so a
full-screen affordance is centered at `(0,0)` in either convention.

## Binaries and address convention

The exact pair audited here is:

| Binary | SHA-256 |
|---|---|
| `vendor/original-native/libsecveGeometricMosaic.so` | `A16F926D14396E2C78E50AE48089860BD9B5156FB77ECC99A3E4E7694FE06DD8` |
| `reference/arm32-original/native-libs/armeabi-v7a/libsecveSrkCommon.so` | `5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36` |

The existing Ghidra projects use a `+0x10000` image base:

```text
Ghidra VA = ELF/raw VA + 0x10000
```

The key functions are:

| Role | Raw ELF VA | Ghidra VA |
|---|---:|---:|
| common JNI `Native_showAffordance` | `0x109c4` | `0x209c4` |
| common event processing / `Native_draw` | `0x1134c` | `0x2134c` |
| Geometric clear/reset, vtable `+0x04` | `0x051b0` | `0x151b0` |
| special affordance trigger, vtable `+0x08` | `0x0eb4c` | `0x1eb4c` |
| ring/stagger trigger | `0x0ad40` | `0x1ad40` |
| ordinary primary DOWN, vtable `+0x20` | `0x0b550` | `0x1b550` |
| mask evaluator | `0x06084` | `0x16084` |
| per-frame Geometric render | `0x0d394` | `0x1d394` |
| byte animator cancel/update/is-active | `0x01e34` / `0x02f18` / `0x01fd8` | `0x11e34` / `0x12f18` / `0x11fd8` |
| float animator cancel/update/is-active | `0x0214c` / `0x02a78` / `0x022f0` | `0x1214c` / `0x12a78` / `0x122f0` |

The Geometric scene vtable is at raw `0x1cd38` (Ghidra `0x2cd38`). Its
relevant raw entries are:

```text
+0x04 = 0x051b0  clear/reset
+0x08 = 0x0eb4c  affordance
+0x0c = 0x0dac8  unlock
+0x1c = 0x0d394  render
+0x20 = 0x0b550  primary DOWN
+0x24 = 0x0333c  primary UP
+0x30/+0x3c      secondary/tertiary UP paths inherited by the common event flow
```

## Public command and caller chain

The framework smali establishes the complete public call chain:

1. `LockBGEffect.handleCustomEvent(1, params)` reads exact keys
   `"StartDelay"` (`Long`) and `"Rect"` (`Rect`) in
   `LockBGEffect$3.smali`.
2. `LockBGEffect.showAffordanceEffect` waits `StartDelay`, calculates the integer
   rectangle center, and its runnable calls
   `GLTextureViewRenderer.showUnlockAffordance(centerX, centerY)`.
3. The renderer stores those two pixels, sets `isAffordanceOccur=true`, and
   switches the GL view to continuous rendering.
4. On the next `onDrawFrame`, the renderer calls native
   `showAffordance(x,y)`, clears its pending Java flag, then calls native
   `draw()` in the same frame.

The relevant preserved files are:

```text
build/abstract-tiles-research-artifacts/smali/com/samsung/android/
  visualeffect/lock/common/LockBGEffect.smali
  visualeffect/lock/common/LockBGEffect$2.smali
  visualeffect/lock/common/LockBGEffect$3.smali
  visualeffect/lock/common/GLTextureViewRenderer.smali
  visualeffect/lock/common/Native.smali
```

`Native_showAffordance` does not invoke the scene immediately. At Ghidra
`0x20a28..0x20a60` (raw `0x10a28..0x10a60`) it reads `W/H` from the common
scene, computes `x/W` and `(H-y)/H`, and appends a 0x24-byte event with type `1`
at event offset `+0x20` (`0x20a68..0x20a9c`).

During `Native_draw`, common event case `1` is the branch at Ghidra
`0x21bc4..0x21cbc` (raw `0x11bc4..0x11cbc`). In exact order it:

```text
primary/secondary/tertiary held flags = false
call scene vtable +0x24 with last primary point
call scene vtable +0x30 with last secondary point
call scene vtable +0x3c with last tertiary point
call scene vtable +0x04                // complete clear/reset
reset the common engine clock origin
call scene vtable +0x08(event.xy)      // Geometric special trigger
```

Therefore the stock public affordance never layers onto stale touch records.
The scene-level `+0x08` method can technically be called directly, but that is
not the Java/API behavior that LLE needs to reproduce.

## Exact scene trigger

The trigger at raw `0x0eb4c` / Ghidra `0x1eb4c` performs:

```c
void geometricAffordance(Scene *s, Event *event) {
    cancelByteAnimationsTargeting(&s->specialActive);  // +0xe4
    cancelFloatAnimationsTargeting(&s->specialRadius); // +0xf8

    s->specialActive = 1;
    float T = engineClockSeconds();

    // Timed byte record. The byte animator writes 1 during [T,T+2]
    // and writes 0 on the first update after T+2.
    animateTimedByte(&s->specialActive,
                     afterValue = 0, intervalValue = 1,
                     start = T, end = T + 2.0,
                     repeat = false, active = true);

    // Literal linear float animator.
    animateFloat(&s->specialRadius,
                 from = -0.8, to = 3.0,
                 start = T, end = T + 2.0,
                 repeat = false, active = true);

    Qx = event->x - 0.5;
    Qy = event->y - 0.5;

    startFiveRingsIfStrokeInactive(s); // FUN_1ad40
}
```

The decisive trigger instructions are:

```text
Ghidra 0x1efc8  strb 1,[scene,#0xe4]
Ghidra 0x1f008  load 2.0
Ghidra 0x1f294  store -0.8 in float animation record
Ghidra 0x1f2a0  store 3.0 in float animation record
Ghidra 0x1f2b0  end = T+2.0
Ghidra 0x1f4c8  Qy = event.y-0.5
Ghidra 0x1f4cc  Qx = event.x-0.5
Ghidra 0x1f4d0  store Qy to global +4
Ghidra 0x1f4d4  store Qx to global +0
Ghidra 0x1f4d8  call FUN_1ad40
```

The corresponding raw addresses are exactly `0x10000` lower.

The trigger itself constructs the float animation with a literal `-0.8` start,
but does not separately store `-0.8` to `scene+0xf8` before returning. The
public case-1 path has just run the clear method, which did store `-0.8` there.
On later frames the float animator writes the linear value. This distinction
only matters to a non-stock direct re-entry that skips clear.

## Animator semantics: no easing ambiguity remains

The previous pipeline audit left the interpolator open. It is closed for this
field by the concrete animator vtable and update routines.

The float record used for `+0xf8` is:

```text
+0x00 target float pointer
+0x04 from = -0.8
+0x08 to   = 3.0
+0x0c start time T
+0x10 end time T+2.0
+0x14 repeat byte 0 / active byte 1
```

`FUN_12a78` computes exactly:

```text
target = from + ((now-start)/(end-start))*(to-from), while now < end
target = to,                                      when now >= end
```

and clears the record's active byte when `now >= end` and repeat is false.

The timed-byte record for `+0xe4` is 0x14 bytes:

```text
+0x00 target byte pointer
+0x04 value after interval = 0
+0x05 value during interval = 1
+0x08 start time T
+0x0c end time T+2.0
+0x10 repeat byte 0 / active byte 1
```

`FUN_12f18` writes the interval byte while `start <= now <= end`, then writes
the after byte and retires the record when `now > end`. This proves the exact
two-second flag lifecycle rather than inferring it from a capture.

## Center globals and special mask field

The trigger resolves its PC-relative global base to Ghidra `0x2d22c`, raw
`0x1d22c`, in `.bss`. The stores are:

```text
raw 0x0f4d4 -> [0x1d22c] = Qx
raw 0x0f4d0 -> [0x1d230] = Qy
```

The mask evaluator resolves the same base at Ghidra `0x160cc` and loads it at
Ghidra `0x160d8/0x160e0` (raw `0x060d8/0x060e0`).

For each of the four side-midpoint samples `P` of a mask cell, with the exact
aspect correction from `scene+0xdc/+0xe0`:

```text
dx = (P.x-Qx) * scaleX
dy = (P.y-Qy) * scaleY
d  = sqrt(dx*dx + dy*dy)

K = 10.5 / (r+1.8)^3
special(P) = K * (0.5 - abs(r-d))
```

This is an expanding annular/band field, not the filled-disc coverage used by
ordinary touch records. At `r=-0.8` the value is negative everywhere. At the
center it first becomes positive after about `0.1578947 s`, when `r` crosses
`-0.5`; this short delayed appearance follows directly from the formula and
linear radius.

The evaluator initializes each side to zero when `+0xe4` is false. When the
special flag is true it replaces that seed with the signed special value. It
then scans all ordinary records and takes the larger coverage. If it encounters
an active ordinary record while the special seed is negative, it first resets
that seed to `0`. With no ordinary records, negative values can survive into
the CPU vertex-alpha buffer; normalized framebuffer output clips them. A
literal reconstruction should preserve this signed intermediate rather than
turning the special field into `max(0,special)` prematurely.

After the four side values have been combined with the already recovered
four-triangle mask equations, `scene+0x114` global alpha is applied normally.

## Interaction with ordinary touch records and rings

There are two distinct cases.

### Stock public affordance

The common type-1 event performs the full clear before vtable `+0x08`:

- all 100 ordinary records become inactive and the free stack is rebuilt;
- any old current-record index is visually irrelevant because no record is
  active;
- all property animations are cleared;
- `+0xf8=-0.8`, `+0xe4=0`, `+0x114=1.0`, `+0x131=0`;
- the five ring records are reset active with radius zero.

The special trigger then enables `+0xe4` and calls `FUN_1ad40`. Because
`+0x131` is zero after clear, `FUN_1ad40` schedules the normal five recovered
ring timelines and sets `+0x131=1`. It does **not** allocate an ordinary touch
record.

### A real touch arrives during the two-second hint

Primary DOWN reaches raw `0x0b550` / Ghidra `0x1b550`:

```text
FUN_1ad40(scene)
scene+0x132 = 1
FUN_1a54c(scene,event)  // allocate ordinary record
scene+0x133 = 0
```

It does not clear `+0xe4`. Since the hint already set `+0x131=1`, the ring
helper does not restart all five rings. During the overlap, each mask side is:

```text
max(special radial seed, every active ordinary-record coverage)
```

When the special flag expires, an ordinary record can keep the mask and ring
rendering alive through the ordinary record lifecycle. Without an overlapping
ordinary record, the render path sees both `maskActive==0` and `+0xe4==0`,
cancels the unfinished ring animations, resets the ring-active latch, and the
scene becomes idle.

## Reset, render gating, and `isEmpty`

Geometric clear at raw `0x051b0` / Ghidra `0x151b0` begins by clearing the
scene animation manager, then explicitly writes:

```text
scene+0xf8 = -0.8
scene+0xe4 = 0
scene+0x114 = 1.0
scene+0x131 = 0
scene+0xec = 0
```

It also clears the dynamic mask buffer, resets all five ring radii, deactivates
all touch records, and reconstructs the 0..99 free-index stack. Therefore
`clear`, a repeated public hint, and public case-1 affordance all terminate the
previous special field deterministically.

`FUN_16084` returns whether at least one ordinary touch record remains
allocated (the free stack contains fewer than 100 indices). Special-only
rendering does not make that return value true. `FUN_1d394` stores it in
`scene+0x130` and renders the circle targets while either `+0x130` or `+0xe4`
is true.

Common `Native_draw` runs the animation-manager update first (Ghidra
`0x2175c..0x21768`), processes queued events, then calls scene render through
vtable `+0x1c`. Its returned keep-drawing value is:

```text
sceneRenderReturn | animationManagerHasActiveRecords
```

at Ghidra `0x21888..0x218b0` (raw `0x11888..0x118b0`). Thus the timed byte and
float records keep a special-only hint in continuous mode for the full two
seconds even though there is no ordinary record. On the first update after the
deadline, `+0xe4` becomes zero; the render path cancels the otherwise longer
ring tails when no ordinary field exists, and native `draw()` can return false
so Java switches back to dirty mode.

## Concrete ARM64 implementation guidance

Replace the current affordance-as-touch behavior with a separate state. For
normalized Android top-origin inputs `x,y` and animation timestamp `now`:

```java
void addAffordance(float x, float y, double now) {
    resetEffectStateExactlyAsClear();

    specialCenterX = x - 0.5f;
    specialCenterY = 0.5f - y;
    specialStartSeconds = now;
    specialActive = true;
    specialRadius = -0.8f;

    // Clear left the stroke/ring latch inactive, so this starts the five stock
    // timelines once. Do not allocate a TouchRecord and do not set currentTouch.
    ringStartSeconds = now;
    ringsActive = true;
}
```

Before mask generation each frame:

```java
double age = now - specialStartSeconds;
if (specialActive) {
    specialRadius = -0.8f + 3.8f
            * clamp((float) (age / 2.0), 0.0f, 1.0f);
    if (age > 2.0) {
        specialActive = false;
    }
}
```

Use `age > 2.0`, not `>=`, to match the byte animator's inclusive end-time
test. Seed each side-midpoint coverage with the signed formula above while
active, then run the ordinary-record max scan. Continue requesting frames
while the special state is active. If there is no ordinary record when the
flag expires, perform the same ring-latch cleanup as the ARM32 render path.

Do not:

- route affordance through `addTouch()`;
- use the ordinary full clip center `2*x-1, 1-2*y` for this path;
- use a filled-disc `1-distance/radius` field for the hint;
- restart the five rings if a real touch arrives while the hint's stroke latch
  is already active;
- allow a repeated public affordance to layer over old records instead of
  applying the stock clear-first contract.

## Confidence

| Finding | Confidence | Evidence |
|---|---|---|
| Public command is cmd `1`, delayed rectangle center | Certain | framework smali call chain |
| Common event clears all three touch channels and scene before trigger | Certain | common `Native_draw` raw `0x11bc4..0x11cbc` |
| Pixel normalization and inverted Y | Certain | common JNI raw `0x10a28..0x10a60` |
| `Q=(u-0.5,v-0.5)` and BSS addresses | Certain | Geometric raw stores `0x0f4d0/0x0f4d4`, evaluator loads `0x060d8/0x060e0` |
| `r: -0.8 -> 3.0` over 2.0 s | Certain | trigger record plus float animator `FUN_12a78` |
| Linear interpolation | Certain | float animator instruction/decompile body |
| Active through end, false after 2.0 s | Certain | timed-byte record plus `FUN_12f18` |
| Signed radial formula and max with ordinary records | Certain | `FUN_16084` |
| Trigger starts rings but allocates no touch record | Certain | terminal call to `FUN_1ad40`, no call to `FUN_1a54c` |
| Special-only continuous-render and idle behavior | Certain | `FUN_1d394` plus common draw return OR |

There is no remaining reverse-engineering gap in the `scene+0xe4/+0xf8`
affordance physics or lifecycle. Visual differences after implementing this
spec should be sought in GL precision/filtering or host timing, not by tuning
the hint radius, center, or duration.
