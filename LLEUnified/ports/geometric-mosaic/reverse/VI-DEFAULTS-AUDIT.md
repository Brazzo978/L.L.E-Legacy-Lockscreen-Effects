# Geometric Mosaic: `vi` defaults, blend state, and animator audit

Status: static ARM32 recovery for the ARM64 reconstruction. No application
source was changed for this audit and no device/ADB evidence was used.

## Inputs and address convention

| Binary | Size | SHA-256 |
|---|---:|---|
| `vendor/original-native/libsecveGeometricMosaic.so` | 115,932 | `A16F926D14396E2C78E50AE48089860BD9B5156FB77ECC99A3E4E7694FE06DD8` |
| `native-libs/lib/armeabi-v7a/libsecveSrkCommon.so` | 341,296 | `5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36` |

Both existing Ghidra imports use an image base `0x10000` above the ELF/raw
virtual addresses:

```text
Ghidra VA = ELF/raw VA + 0x10000
```

Confidence labels:

- **CERTAIN**: literal ARM instruction, constructor argument, vtable entry, or
  GL constant recovered from the two binaries.
- **CONTEXT-DEFAULT**: not written by the recovered method; the value follows
  only if the GLES context is still in its specified initial state.

## Result

The previously open texture filters are now closed. The original resources
use these exact effective parameters:

| Geometric resource | Min filter | Mag filter | Wrap S/T | Confidence |
|---|---|---|---|---|
| mask RT `0x8f` | `GL_LINEAR` | `GL_LINEAR` | `GL_CLAMP_TO_EDGE` | **CERTAIN** |
| color-origin RT `0x90` | `GL_NEAREST` | `GL_NEAREST` | `GL_CLAMP_TO_EDGE` | **CERTAIN** |
| circle3 RT `0x91` | `GL_LINEAR` | `GL_LINEAR` | `GL_REPEAT` | **CERTAIN** |
| circle2 RT `0x92` | `GL_LINEAR` | `GL_LINEAR` | `GL_REPEAT` | **CERTAIN** |
| random-alpha texture `0x8e` | `GL_NEAREST` | `GL_NEAREST` | `GL_CLAMP_TO_EDGE` | **CERTAIN** |

`Renderer::setBlending(true)` is also exact: it enables `GL_BLEND` and calls
`glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)`. It does not call a blend
equation function or change any other render state.

The animation assumption also changes materially: packed value `0x0100` is
not the easing selector. Its two bytes are inactive-at-completion/repeat flag
`0` and active flag `1`. The easing curve comes from the concrete animation
manager to which the 24-byte record is submitted. Ordinary Geometric Mosaic
uses four different curves:

| Animation | Exact normalized curve `E(t)`, `t` clamped to `[0,1]` |
|---|---|
| new touch radius `0.3 -> 0.8` | `(1 - cos(pi*t)) / 2` (cosine ease-in-out) |
| old/current trail retreat over `0.6 s` | `t^3` (cubic ease-in) |
| five ring radii | `t` (linear) |
| unlock radius `current -> 5` | `t` (linear) |
| unlock scene alpha `current -> 0` | `sin(pi*t/2)` (sine ease-out) |

These formulas are **CERTAIN**, not visual guesses.

## 1. Texture constructor defaults

### 1.1 `vi::Texture`

The matching common engine exports both C1/C2 at raw `0x26800`, Ghidra
`0x36800`. Its relevant stored fields are established at raw
`0x2687c..0x268bc`:

```text
object+0x19 = constructor bool 1
object+0x1a = constructor bool 2       // wrap family enabled
object+0x1b = constructor bool 3       // false=repeat, true=mirrored repeat
object+0x1c = constructor bool 4       // false=nearest, true=linear
object+0x1d = constructor bool 5
object+0x30 = GL_TEXTURE_2D
object+0x34 = format selected by enum  // 1=ALPHA, 3=RGB, 4=RGBA
object+0x38 = false ? GL_UNSIGNED_BYTE : GL_FLOAT
```

`Texture::allocate` is raw `0x262c4`, Ghidra `0x362c4`. It converts those
booleans into literal GL values:

- raw `0x262f8..0x26320`: `object+0x1c == 0` selects `GL_NEAREST (0x2600)`;
  nonzero selects `GL_LINEAR (0x2601)`;
- raw `0x26328..0x26344`: `object+0x1a == 0` selects
  `GL_CLAMP_TO_EDGE (0x812f)`; otherwise `object+0x1b == 0` selects
  `GL_REPEAT (0x2901)`, and nonzero selects
  `GL_MIRRORED_REPEAT (0x8370)`;
- raw `0x2636c..0x263a8`: the same selected filter is written to both
  `GL_TEXTURE_MAG_FILTER (0x2800)` and `GL_TEXTURE_MIN_FILTER (0x2801)`, and
  the same wrap is written to S/T (`0x2802/0x2803`).

There is no mipmapped min-filter default in this path.

### 1.2 `vi::RenderableTexture`

The constructor used here is raw `0x1ea84`, Ghidra `0x2ea84`. At raw
`0x1eab4..0x1eadc` it forwards the four caller booleans to `Texture`, supplies
format enum `4` (`GL_RGBA`), and supplies the final false value that selects
`GL_UNSIGNED_BYTE`.

All four Geometric RT calls pass the same booleans:

```text
true, true, false, true, false
```

The literal stack setup and calls in Geometric Mosaic are:

- mask: raw `0x17610..0x17640`, Ghidra `0x27610..0x27640`;
- color origin: raw `0x1765c..0x1767c`, Ghidra `0x2765c..0x2767c`;
- circle3: raw `0x1771c..0x17768`, Ghidra `0x2771c..0x27768`;
- circle2: raw `0x1777c..0x177a4`, Ghidra `0x2777c..0x277a4`.

For every call, constructor bool 4 is true, so the inherited min/mag filter is
linear. Constructor bools 2/3 initially select repeat.

The Geometric constructor then makes only these overrides:

- mask raw `0x177b4..0x177e0` (`0x277b4..0x277e0` Ghidra): changes wrap to
  clamp, leaving the inherited linear filter untouched;
- color origin raw `0x177e4..0x1784c`: changes filter to nearest and wrap to
  clamp;
- circle3 raw `0x17850..0x17884`: writes repeat wrap and leaves linear;
- circle2 raw `0x17888..0x178bc`: writes repeat wrap and leaves linear.

Therefore the mask and circle filter defaults are no longer open points.

### 1.3 Random alpha texture `0x8e`

The direct call at Geometric raw `0x17a58..0x17a90`, Ghidra
`0x27a58..0x27a90`, is:

```text
Texture(buffer, gridWidth, gridHeight,
        false, false, false, false, false, 1, false)
```

The format enum `1` selects `GL_ALPHA`, the last false selects
`GL_UNSIGNED_BYTE`, constructor bool 4 false selects nearest for min and mag,
and wrap-family bool false selects clamp-to-edge. This proves the exact
one-byte nearest/clamp configuration.

## 2. Exact blend behavior

`vi::Renderer::setBlending(bool)` is raw `0x22fa8`, Ghidra `0x32fa8` in
`libsecveSrkCommon.so`.

The complete true path is only:

```text
0x22fc0  bl    glEnable
0x22fc4  movw  r0, #0x0302       // GL_SRC_ALPHA
0x22fc8  movw  r1, #0x0303       // GL_ONE_MINUS_SRC_ALPHA
0x22fd0  b     glBlendFunc
```

The false path loads `0x0be2` (`GL_BLEND`) and tail-calls `glDisable` at raw
`0x22fb8..0x22fbc`. The true path first calls `glEnable(GL_BLEND)`; the PLT
mapping proves the targets:

| Common PLT raw VA | Symbol |
|---:|---|
| `0xe268` | `glEnable` |
| `0xe280` | `glDisable` |
| `0xe298` | `glBlendFunc` |

The Geometric frame path calls `setBlending(true)` at raw `0xd3a8..0xd3ac`,
Ghidra `0x1d3a8..0x1d3ac`.

Neither analyzed ELF imports `glBlendEquation` or
`glBlendEquationSeparate`. `setBlending` also does not touch depth test,
depth mask, culling, scissor, or color mask. Consequently:

- blend enable and factors are **CERTAIN**;
- an equation of `GL_FUNC_ADD` is only **CONTEXT-DEFAULT**: it is the GLES
  initial value, but this method does not reassert it;
- the Java helper's current `glEnable(GL_BLEND)` plus
  `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` matches the literal
  method. No alternate/additive blend mode should be introduced.

## 3. Exact animator semantics

### 3.1 Record layout and `0x0100`

Each float animation entry is 24 bytes:

```text
+0x00  float *target
+0x04  start value
+0x08  end value
+0x0c  start time
+0x10  end time
+0x14  completion/continuation byte
+0x15  active byte
```

The scheduling sites write packed halfword `0x0100`, which on little-endian
ARM means `entry+0x14 = 0`, `entry+0x15 = 1`. Every recovered manager checks
`+0x15` before evaluating. At or after the end time it writes the exact end
value, then raw code equivalent to this appears in every manager:

```text
if (entry[0x14] == 0)
    entry[0x15] = 0;
```

For example this is raw `0x3938..0x3940` in the cosine manager and raw
`0x3e80..0x3e88` in the sine manager. Thus `0x0100` activates a one-shot
record; it does not name an interpolator.

### 3.2 Manager/vtable map

The concrete manager object selects the curve through its update vtable slot:

| Manager object raw/Ghidra | Object vptr raw/Ghidra | Update raw/Ghidra | Curve |
|---|---|---|---|
| `0x1d02c` / `0x2d02c` | `0x1ccd8` / `0x2ccd8` | `0x386c` / `0x1386c` | cosine ease-in-out |
| `0x1d01c` / `0x2d01c` | `0x1ccf8` / `0x2ccf8` | `0x2598` / `0x12598` | cubic ease-in |
| `0x1d058` / `0x2d058` | `0x1cc98` / `0x2cc98` | `0x2a78` / `0x12a78` | linear |
| `0x1d048` / `0x2d048` | `0x1ccb8` / `0x2ccb8` | `0x3dc4` / `0x13dc4` | sine ease-out |

Representative lazy construction proves the object-to-vtable assignments:

- cosine object: raw `0xa9c8..0xa9f0`;
- cubic object: raw `0xc43c..0xc468`;
- linear object: raw `0xd024..0xd04c`;
- sine-out object: raw `0xe45c..0xe484`.

The curve math is literal ARM VFP:

- cosine manager raw `0x38f0..0x3924` normalizes `t`, multiplies it by the
  float `pi` at raw `0x3b50` (`0x40490fdb`), calls `cosf` through PLT raw
  `0x1584`, and evaluates
  `(start+end)/2 + (start-end)*cos(pi*t)/2`;
- cubic manager raw `0x29d8..0x29f8` evaluates
  `start + (end-start)*t*t*t`;
- linear manager raw `0x2e98..0x2eb0` evaluates
  `start + (end-start)*t`;
- sine manager raw `0x3e40..0x3e68` multiplies by the float `pi/2` at raw
  `0x4080` (`0x3fc90fdb`), calls `sinf` through PLT raw `0x1560`, and evaluates
  `start + (end-start)*sin(pi*t/2)`.

### 3.3 Geometric animation-to-manager routing

The scheduling functions register their records with different objects:

- `FUN_1a54c` new-point growth uses object raw `0x1d02c` (references at raw
  `0xa6b8..0xa6d8`): cosine ease-in-out;
- `FUN_1b580` old/current retreat uses object raw `0x1d01c` (references at raw
  `0xb66c..0xb67c` and `0xb968..0xb978`): cubic ease-in;
- `FUN_1c8d4` ring radius keys use object raw `0x1d058` (references beginning
  raw `0xccd0..0xcce0`): linear;
- `FUN_1dac8` current-record radius-to-5 uses object raw `0x1d058`
  (references beginning raw `0xde5c..0xde6c`): linear;
- the same `FUN_1dac8` scene-alpha-to-zero record uses object raw `0x1d048`
  (references beginning raw `0xe0d8..0xe0e8`): sine ease-out.

## 4. Certain helper implications

The current helper already has the now-proven texture configuration:

```text
mask       linear + clamp
circle3/2  linear + repeat
color RT   nearest + clamp
noise      nearest + clamp
```

It also already uses the exact blend enable/factors. No filter or blend-factor
change is required.

The certain remaining helper changes are animation curves only:

```text
touch growth progress = 0.5 * (1 - cos(pi*t))
touch retreat progress = t*t*t
ring progress          = t
unlock radius progress = t
unlock alpha progress  = sin(pi*t/2)
```

Do not apply one easing globally: the original intentionally routes these
records through four manager specializations. In particular, changing rings
or unlock expansion away from linear would reduce fidelity even though the
touch growth and retreat are currently too linear.
