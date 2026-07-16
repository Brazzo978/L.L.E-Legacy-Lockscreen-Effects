# Abstract Tiles deep fidelity findings (2026-07-15)

This record preserves the high-confidence ARM32 binary findings recovered after
the ARM64 Alpha implementation was frozen for L.L.E. 1.0.1 Beta 1. It was
updated during the 2026-07-16 fidelity pass to record the behavior now mirrored
by the app-owned ARM64 engine. Visual parity still requires an LSE device replay.

## Confidence and scope

The animation formulas, constants, record layouts, channel offsets and timing
below were recovered directly from the ARM binary and are high confidence.
The only explicit port decisions left open are refresh-rate normalization for a
stock per-draw increment and the inferred reason for the second candidate in a
ray path.

## Animator records and interpolation

The float animator record is 24 bytes:

```text
float *target
float start
float end
float startAbsoluteTime
float endAbsoluteTime
flags (low half includes 0x0100)
```

The helper accumulates delay on the current time.

- Geometry animator (`FUN_1A18C`, updater `0x2794`) uses ease-out cubic:
  `E(t) = 1 + (t - 1)^3 = 1 - (1 - t)^3`.
- Brightness, alpha, scatter and ray animator (`FUN_1934C`, updater `0x2CB8`)
  is linear.
- The 20-byte boolean animator (`FUN_1823C`) keeps the start byte during the
  interval and assigns the end byte at completion.
- Cosine interpolation `0.5 * (1 - cos(pi * t))` belongs to a separate pool.
  Unlock uses it only for scene scalar `+0x5EC`, from 0 to 1 over 0.4 seconds;
  it is not the per-tile geometry curve.

## Pop gesture (`FUN_200F8`)

The stock Tile position and UV records are permuted together after mesh/Line
construction and on every clear/rebuild. `FUN_16BE8` resets the unsigned LUT
cursor to zero, then for every Tile slot `i` swaps it with
`nextUIntLUT() % triangleCount`. This is not descending Fisher-Yates. Pop and
unlock scan this permuted Tile order; Scatter and Line retain canonical geometry.

For every accepted tile:

1. Set the record active.
2. Reset progress, previous progress and brightness to 0.
3. Set tile alpha to 0.3.
4. Select pivot with `uintLUT % 3`.
5. Select factor 0.5 or 1.
6. Animate geometry 0 to 1 with ease-out cubic over 0.4 seconds at the current
   stagger delay.
7. Calculate brightness `B = floatLUT * 0.75 - 0.375`; animate linearly from 0
   to `B` over 0.4 seconds with the same delay.
8. Animate tile alpha linearly from 0.3 to 0 over 0.2 seconds after a 0.2-second
   hold (`delay = stagger + 0.2`).
9. Increase stagger by 0.02 seconds only when a tile was actually activated.

The next batch is eligible after 0.16 seconds.

Pop and unlock probability distances are measured after converting clip
coordinates to normalized `[0,1]` coordinates. Using their recovered constants
directly in `[-1,1]` clip space makes the effective radius half as large.

Geometry update uses delta progress, not absolute reconstruction:

```text
delta = progress - previousProgress
A += delta * dA
B += delta * dB
copy alpha and brightness to all three vertices
previousProgress = progress
```

Cleanup subtracts the exact accumulated geometry delta and clears the record
and its arrays.

## Unlock

`FUN_20BD4` uses per-tile ease-out cubic geometry from 0 to 1 over 0.9 seconds.
Brightness is linear from 0 to a random value in `[-0.375, 0.375]` over 0.9
seconds. Tile alpha starts at 0.3 but has no per-tile alpha animator. Each tile
record is cleaned at completion. Scene scalar `+0x5EC` independently uses the
cosine curve from 0 to 1 over 0.4 seconds.

## Three independent scatter alpha channels

The stock vertex shader adds three alpha channels. They never contain the
screenshot and are not the tile alpha:

- `+0x178`: proximity alpha
- `+0x558`: radial/random alpha
- `+0x57C`: ray alpha

The fragment shader outputs white. Scatter blending is additive
`GL_ONE, GL_ONE`.

### Proximity (`FUN_18B6C`)

Let `d` be the aspect-corrected distance from tile centroid to touch, `r` the
physical touch radius and `q = r / d`.

A tile is near when `q > 1` or any triangle edge intersects the touch circle
with radius squared `r^2`.

```text
cap = 0.045 * pow(clamp(q, 0, 1) + 1.5, 2.5)
```

While touch is stationary, ordinal-even tiles rise/fall at `3 * dt` and odd
tiles at `2.1 * dt`. After MOVE flag `+0x555`, rise is `+0.3` per draw; fall is
`4 * dt` for even and `2.8 * dt` for odd tiles.

The `+0.3` stock increment is refresh-dependent. A faithful modern port must
either cap/fixed-step at the stock cadence or convert it to an equivalent time
rate (approximately `+9 * dt`) so 60 and 120 Hz panels remain compatible. The
time conversion is a port policy, not a recovered stock instruction.

### Radial/random (`FUN_19C88`)

For an inactive tile scalar:

```text
qDiameter = (2 * physicalRadius) / d
eligible when qDiameter <= 1 and d <= radius
random gate: nextUIntLUT() > 0x03ffffff  (31/32 probability)
A = 0.12 * nextFloatLUT() / pow(qDiameter, 0.7)
delay = d * delayMultiplier - 0.1
```

Set the scalar to 0.001, animate linearly from 0 to `A` over
`[delay, delay + rise]`, then from `A` to 0 over
`[delay + rise, delay + 2 * rise + 0.2]`. The values are triplicated into
channel `+0x558`.

Recovered mode parameters:

| Mode | Radius | Delay multiplier | Rise |
|---|---:|---:|---:|
| Held | 0.6 | 0.5 | 0.5 |
| Affordance | 2.0 | 0.5 | 0.3 |

Held scheduling occurs every draw and skips non-zero scalars; it is not a
0.16-second batch. The old ARM64 Alpha approximation
`0.001 + random * 0.999` must be replaced by the recovered formula.

### Ray paths (`FUN_2A710`, `FUN_1F078`)

Build eight paths. Initial angle for path `k` is:

```text
theta = k * pi / 4 + random * (pi / 4)
```

Candidate selection tests the ray segment against a circle at each canonical
triangle centroid, using the extended radius squared at `+0x118`; it does not
expand the complete triangle outline. The second-nearest hit is appended because
the nearest is normally the current tile. Stop once aspect-corrected distance
squared reaches 0.8; the tile crossing that threshold remains in the path. After
the third element, deviations alternate between:

- wide: `theta - pi/2 + random * pi`
- narrow: `theta - pi/8 + random * pi/4`

Reach is `10 * sqrt(wx^2 + hy^2)`.

For path index `j`, with aspect-corrected squared distance `d2` from the
original touch:

```text
A = (0.21 + 0.2 * floatLUT) * pow(1.2 - d2, 1.3)
rise:  now + j*0.1       -> +0.1 seconds
decay: now + (j+1)*0.1   -> +0.1 seconds
```

Values are triplicated into channel `+0x57C`. Flag `+0x598` is set when a ray
is scheduled and is reset only when `FUN_2A710` rebuilds the paths, not on UP.

## Random and trigonometric lookup tables

- Float LUT: 1024 values, `float(rand() & 0x7fffffff) * 2^-31`.
- Unsigned LUT: 1024 values, `rand() & 0x7fffffff`.
- The float and unsigned cursors are independent, pre-incremented and wrap at
  1024.
- Trigonometric LUT entry `i` stores
  `{sin(i * 2*pi/1024), cos(i * 2*pi/1024)}`.

Static initialization uses Bionic's default `rand()` sequence (seed 1), so the
tables begin from the well-known values `1804289383, 846930886, ...`. The port
seeds this explicitly before filling the float table and then the unsigned table.
The scene subsequently calls `srand(time)`. Ray-angle multiplication retains the
single-rounding ARM constants `0x2fc90fdb` (`pi/4 / RAND_MAX`) and `0x30c90fdb`
(`pi / RAND_MAX`).

## Touch ordering recovered in the final pass

- DOWN cancels old ray tails, builds eight paths with C `rand()`, schedules the
  initial radial group, schedules the pop group, then schedules ray alpha.
- MOVE updates the live center and MOVE flag but does not directly create pop or
  ray groups. It advances the accepted trail point only after the normalized
  threshold; in clip space that threshold is `hy^2`.
- Held pop batches alone run every `0.16 s` and use the live touch center.
- UP clears the MOVE flag and removes only delayed pop records whose start is in
  the future.
- The remaining release gate is a same-gesture visual differential against LSE,
  especially Scatter intensity and transparent Line composition.

The Line arrays are persistent in the ARM32 scene. When progress reaches a
corner's threshold, `FUN_13B10` stops writing its background UV, so the UV keeps
the final displaced value. A reconstruction from canonical geometry must use
`uv = start - min(progress, threshold) * delta`; rebuilding it as `start` in the
post-threshold branch produces incorrect screenshot content at unlock endpoint.
Because a static stock Line is neutral only over the engine's identical opaque
Background, the recovered pass is retained but disabled in shipping builds until
the host can supply a wallpaper-only texture.

Device comparison established a harder boundary: LSE's OEM host samples a clean
gallery/wallpaper image and renders its demo UI separately, while LLE's only
available Fold-aware source is the complete accessibility screenshot. Exact Line
slabs consequently move lockscreen clock/status/weather pixels and look corrupt
even with correct geometry. The S23 LSE install also lacks the external legacy
resource package that supplied the line mask, so it cannot provide a visual Line
reference. The shipping ARM32 pass was already disabled for this reason; ARM64
now preserves the exact dormant implementation but also ships Line off pending a
wallpaper-only host source.

The ARM64 safety cap of 48 entries per ray has no known visual effect because
the recovered `d^2 >= 0.8` stop normally terminates first; the OEM vector itself
is dynamically sized. MOVE proximity's stock `+0.3` per draw remains the one
intentional physics normalization (`9 * dt`) for consistent 60/120 Hz behavior.

## 2026-07-16 build and device validation

- ARM64 companion APK: `build/arm64-v8a-dev/LLE-arm64-dev.apk`, SHA-256
  `68B3B9FC87328DB79838EAF90C955F7C3AC8A31660E7A80E9F8772193E43C9C7`.
- ARM32 APK: `build/armeabi-v7a/LLE-armeabi-v7a-debug.apk`, SHA-256
  `C14294BC159DE622970FA7E066ED7C2FE1F9C881954ADE57636AA2A50F5EAB06`.
- S23 Ultra smoke run `abstract_tiles_20260716_121618_898`: process survived,
  six post-gesture captures completed and crash/GLES finding count was zero.
- The installed ARM64 companion remained co-installable with the ARM32 daily
  package, and the enabled accessibility list remained Bitwarden plus the ARM64
  companion service.
- A valid same-gesture LSE capture was not produced while the secure keyguard
  was locked/dozing. Earlier selector captures in the test folder are not valid
  Abstract Tiles reference frames. Do not declare visual parity from them.
