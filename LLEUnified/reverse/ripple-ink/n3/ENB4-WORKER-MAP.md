# Note 3 ENB4 Ripple Ink: worker map

Oracle: `D:\tmp\enb4_selected\libRippleInkEffect.so`, SHA-256
`828229BC756C30E9F7F6F70C11985C0C6FC68F860AF7E8B2AF18BBCC579A0C8D`,
size 66,800 bytes. It is the N9005VJUENB4 Note 3 library. Its BMI7 ancestor is
`D:\tmp\bmi7_selected\libWaterRipple.so`, SHA-256
`2F938DEB25FA9C6D5CE981F8E99E733C9A376800D79D906C4C9C52DBDB49D2FD`;
that variant has no pthread worker and is not the full N3 implementation.

The later Note 4 oracle used by the Java port is `libsecveRippleInk.so`, SHA-256
`88991DE86A4BDE8E91CEE902F81A28D4F7794FDC70F774699748010C15A5CEBB`,
79,184 bytes. It has broadly related profiles but is not byte-identical to
ENB4; treat the ENB4 worker below as the source for Note 3 edge behavior.

## Relevant ENB4 symbols / offsets

| Routine | ELF virtual offset | Role |
| --- | ---: | --- |
| JNI `onDrawFrame` | `0x1b640` | calls `Fluid::Update`, then source injection |
| JNI `onTouch` | `0x1bd40` | state `0/1/2`, pressure and movement classification |
| `thread_update` | `0x170b0` | worker implementation |
| `thread_func` | `0x17cb0` | pthread entry wrapper |
| `AdvectVelocity` | `0x17cb8` | normalized self-advection |
| `AdvectDensity` | `0x17df0` | density advection |
| `Update` | `0x180d8` | join/upload/launch worker then density advection |
| `AddInk` | `0x187c0` | density source shader uniforms |
| `Inject` | `0x18ad0` | AddInk and density-surface swap |
| `AddVelocity` | `0x18c2c` | direct mode-2 velocity capsule |
| `SwapSurfaces` | `0x18d88` | ENB4 density surface exchange |

## Exact worker chronology

At logical tick N, `Update` first joins the N-1 pthread, uploads that completed
RGBA8 velocity surface, launches worker N, and advects density using N-1.
`onDrawFrame` then optionally calls `Inject`/`AddInk`. Thus the worker result
has exactly one tick of latency. The density advection and AddInk each swap the
five-word ENB4 density surface record, resulting in two density swaps when ink
is injected.

The worker order is direct `Perturb`/`AddVelocity` (only mode 2), normalized
bilinear velocity self-advection (`0.25` backtrace), optional local 25px
override, then projection. Projection is `D=.2*(uE-uW+vN-vS)`, ten Jacobi
iterations with `alpha=-6.25`, `inverseBeta=.25`, then gradient subtract at
`.2`. Velocity packs as RGBA8 high/fraction byte pairs, components limited to
`[-127,127]` and biased by `127`.

## Mode gates

The 60px main gate is strictly open (`x > margin`, never `>=`). The exact raw
branch at `0x17b38..0x17c24` has three cases:

- Mode 0: failing the 60px gate stores a 12px gate and uses projection override
  radius `4`, strength `40`.
- Mode 1: failing the 60px gate stores a 10px gate but retains the selected
  profile (normally radius `20`, strength `10`). Failing 10px performs no
  projection.
- Modes `2` and `-1`: retain the 60px gate.

Important ordering: the mode-2 direct `Perturb` happens before the worker resets
the stored gate to 60px. A preceding mode-0/mode-1 worker can therefore make
the next mode-2 direct capsule eligible in the 12..60px or 10..60px zone; its
own projection still uses the restored 60px gate.

## Input and random facts

`onTouch` derives valid pressure as `p*p + .2`; invalid/nonpositive pressure
is zero. Movement bins are strict `d <= 2` mode 0, `2 < d <= 10` mode 1 and
`d > 10` mode 2. State 1 emits with `mode=-1` for draw steps `<10`, updates its
back-step only after injection, and transitions to state 2 on a qualifying
MOVE.

The ENB4 import table contains `lrand48`; there is no `srand48` or `seed48`.
The worker calls it exactly twice after an admitted projection gate. Android
uses the Bionic process-global stream; an untouched POSIX stream begins from
48-bit state `0x1234abcd330e` and yields
`851401618, 1804928587, 758783491, 959030623, 684387517`.

## Implemented boundary

`ports/ripple-ink/n3-native` implements this worker independently as an ARM64
stateful JNI handle. It intentionally does not own the GLSL density pass; its
`nativeStep` returns the completed N-1 velocity byte surface and launches N.
The existing density owner must call it before upload/advection/AddInk. Host
tests cover lrand48, N-1 latency, strict 60/12/10 gates, mode-0 override,
mode-1 preservation, and fallback-to-mode-2 gate hand-off.
