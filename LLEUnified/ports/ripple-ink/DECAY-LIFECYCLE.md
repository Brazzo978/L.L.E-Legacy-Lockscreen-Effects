# Ripple Ink density lifecycle

Lineage: Note 3 Ripple Ink, first verified in N9005 BMI7/ENB4 firmware. The
authoritative reverse oracle used for the recovered pipeline is
`libsecveRippleInk.so`, SHA-256
`88991DE86A4BDE8E91CEE902F81A28D4F7794FDC70F774699748010C15A5CEBB`.

The density pass is one ping-pong advection draw per logical update, before an
optional `AddInk` draw. `FUN_0001aa10` binds `Dissipation` from persistent
state `+0x12c`; the target Advect fragment at file offset `0x11450` writes
`encode(Dissipation * value)`. Advection consumes the stored value, then the
current profile writes the value for the next tick: reset is `.92`, press/mode
1/mode 2 prepare `.92`, and mode 0 prepares `.94` (therefore one tick later).
The qualifying native UP path writes `.9` at `0x1d51c` immediately, so a
release followed by DOWN still advects once at `.9`; the following press tick
uses `.92`. While released/inactive, `.9` remains persistent across every tail
advection; it is replaced only by an active profile or clear. `clearInkValue`
writes `0` at `0x1d624` (so the next advect clears the field). For HFR, the
host uses `pow(D60, q)`; `q == 1` returns the exact raw S4 value.

`TimeStep = (.25 * .9) / velocityGridDims`; the grid is screen/12. The shader
uses `coord = v_texCoord - back_step * TimeStep * u`, without a second `.25`.
`BackwardStepSize` is similarly stateful: reset begins at `1`; press draws
prepare `.1*n` for `n < 10` (including the initial `n=0`, radius-zero AddInk
draw), then prepare `1`.

The S4 touch gate is deliberately two-stage. State 1 injects only while its
draw counter is `< 10`; it is not frame-promoted to state 2. On an
`ACTION_MOVE`, state 1 remains only when `counter < 12 && eventDistance <= 2`.
Otherwise it writes state 2 and resets the motion counter, but does not write
the raw AddInk mode (so the initial value remains `-1`). A later state-2 MOVE
classifies strictly: `d > 10` is mode 2 (the only segment branch), `2 < d <=
10` is mode 1, and `d <= 2` is mode 0. In `FUN_1b128`, screen-space Y flips
once and the shader receives prior-drawn as `current` and latest-event as
`previous`; `Scale`, `Radius`, and `len` remain screen-pixel units even though
the framebuffer is density-sized.
The default `BackwardStepSize` is `1`; density textures are RGBA8,
`GL_LINEAR`, `GL_CLAMP_TO_EDGE`, preserving the stock bilinear numerical
spreading in addition to scalar dissipation.
The uploaded background and reflection textures likewise use `GL_LINEAR` with
`GL_CLAMP_TO_EDGE`; the earlier reflection-only `GL_REPEAT` branch was not in
the recovered renderer.

## Velocity worker boundary

The density pass consumes the **previous** worker result: upload its RGBA8
velocity texture, advect one density surface, optionally AddInk to the other
surface, then produce the velocity result for the following frame. The worker
does not add a second self-advection/drift pass and does not jitter the touch
position. Its deterministic projection uses clamp-to-edge neighbours,
`D=.2*(uE-uW+vN-vS)`, ten Jacobi passes in the stable Java-domain
normalization `Pout=.25*(PW+PE+PN+PS-6.25*D)`, then
`u'=u-.2*grad(P)`. Velocity packing clamps each component to `[-127,127]`,
biases it by `127`, and writes integer/fraction bytes so shader decode
`255*hi+lo-127` recovers the worker value to byte precision.

Every velocity feedback write is finite-checked and clamped to that same
representable interval before its next worker pass or texture upload. This is
a containment guard for host inputs/precision, not an additional noise or
advection term.

Pressure belongs to the touch record, not a noise source: use `0.2+p*p` for
positive native pressure and `0` otherwise where the mode-0 profile scales its
radius/velocity impulse. The renderer must forward the actual event pressure.

The overlay opacity stays `1`. A global post-UP alpha fade makes old and new
deposits disappear together and is not stock density behavior. There is no
post-UP cleanup timer and no density-zero liveness test in the recovered
renderer. Java calls native `onDrawFrame` and then `move`; once native water is
quiet and the finger is up, Java switches to `RENDERMODE_WHEN_DIRTY` without
clearing the density FBO. A later DOWN resumes continuous rendering from that
retained density. `clearScreen`/`clearAllEffect`, surface recreation and host
lifecycle teardown are the explicit cleanup boundaries.

## Production exact-background compositor

The stock S4 final fragment owns the full lockscreen and always writes alpha
`1`; it has no local-alpha mask. The earlier `DELTA_ONLY` attribution build
derived alpha against the refracted background sample. That sample is not the
pixel under the Android overlay, so source-over cancellation could clip colours,
and pure-refraction water disappeared because no slope alpha remained. The
production compositor replaces that diagnostic without changing the water
solver, density passes, retained fade, lifecycle or fixed 60 Hz cadence.

Let `W` be the clamped recovered water RGB (refracted background plus stock
reflection/specular), `c` one raw S4 palette component, and `w=intensity*d`.
The stock division is evaluated in an algebraically equivalent finite form:

```text
Istock = W                                      when w == 0
Istock = W*c / (c + w*(1.5-c))                 when w > 0
```

This is `W/(1+w*(1.5/c-1))` without uploading a reciprocal. Palette 5 has
`c.r == 0`, for which opaque stock changes from `W.r` at zero density to zero
at any positive density. That discontinuity would turn a retained one-low-byte
density texel into a permanent opaque red-channel halo in a transparent layer.
Only exact-zero palette components therefore adapt their stock ink delta with
the existing density coverage; nonzero components retain bit-equivalent stock
math:

```text
q = clamp(w, 0, 1)
I = W + q*(Istock-W)                       when c == 0
I = Istock                                 otherwise
```

This is continuous as `w` approaches zero and reaches exact stock at `w>=1`.
It does not alter the raw palette uniform or introduce an opacity/threshold
control. No infinity or `0*infinity` enters the shader.

The mesh vertex exports a second background coordinate `vBGScreenCoord`. It is
calculated from the saved pre-refraction direction with the same `r0` projection
as `vBGTexture1Coord`; therefore both coordinates are identical when the normal
is flat. This avoids guessing bitmap orientation from `gl_FragCoord`. Let `B`
be the background sampled at that nonrefracted coordinate. Water uses the
accepted S3 slope alpha. Ink density smoothly unions with that coverage so
transparent adaptation converges to recovered opaque stock colour without an
additional opacity control:

```text
waterA = smoothstep(.035, .180, length(vNormal.xy))
q      = clamp(w, 0, 1)
effectiveWaterA = waterA + (1-waterA)*q
```

The recovered ink change is applied with effective coverage and clamped once:

```text
T = clamp(B + effectiveWaterA*(W-B) + (I-W), 0, 1)
```

Before the final clamp this is the former transparent target interpolated
toward exact stock `I` by `q`, with residual
`T-I=(1-q)*(1-waterA)*(B-W)`. Zero density remains exact accepted S3 water;
`w>=1` reaches exact opaque S4 ink RGB for every palette and background.

For each channel, exact minimum source-over alpha is `(B-T)/B` when `T<B` and
`B>0`, or `(T-B)/(1-B)` when `T>B` and `B<1`. Guarding the divisions explicitly
handles exact black and white without an epsilon approximation. The maximum
channel need is `a`, and the final layer is:

```text
P = clamp(T - (1-a)*B, 0, a)
out = (P, a)
```

Compositing `(P,a)` over `B` reconstructs `T` exactly and preserves
`0 <= RGB <= A <= 1`. At zero density, `I==W` and `q==0`, so the target is
exactly the accepted S3 water composite. Background/reflection
sampling, density decode, palette selection, `GL_ONE/GL_ONE_MINUS_SRC_ALPHA`,
and every simulation/fade behavior otherwise remain unchanged.
