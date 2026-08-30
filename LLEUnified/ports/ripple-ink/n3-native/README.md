# Note 3 ENB4 velocity worker

This is an app-owned ARM64 implementation of the recovered velocity-only
portion of `libRippleInkEffect.so` from N9005VJUENB4. It does not package, load
or execute Samsung code. The source-side density/AddInk/OpenGL owner remains
outside this module.

## ABI

`N3RippleInkWorkerNative.nativeCreate(velocityWidth, velocityHeight,
screenWidth, screenHeight)` creates an immutable geometry handle. The velocity
grid must be `screen / 12` and is normally `90x160` on a `1080x1920` surface.
Coordinates passed to `nativeStep` are MotionEvent pixel coordinates with a
top-left origin; the native worker converts Y once to the bottom-origin worker
space.

Each `nativeStep` returns RGBA8 `(vx-hi, vx-lo, vy-hi, vy-lo)` for the completed
previous worker. Only after encoding it does the module launch the current
worker. The GLES owner must therefore do this order at its 60 Hz logical tick:

```text
nativeStep -> upload returned N-1 velocity -> AdvectDensity -> optional AddInk
```

The new velocity becomes visible on the next `nativeStep`. `mode` is the ENB4
state (`-1`, `0`, `1`, `2`); profile fields must be selected by the existing
touch/onDraw owner and forwarded untouched.

The worker retains ENB4's one-step gate state: an out-of-60px mode-0 projection
leaves a 12px fallback and mode-1 leaves 10px. The following mode-2 direct
`Perturb` consumes that retained fallback before restoring the normal 60px gate
for its own projection branch.

## Random stream

The ENB4 import table contains `lrand48` but no `srand48` or `seed48`.
Android builds intentionally call Bionic's process-global `lrand48`, preserving
the default POSIX state `0x1234abcd330e` when the process has not otherwise
consumed that stream. The worker consumes exactly two values only after the
strict 60px projection gate succeeds. The host harness emulates the same
48-bit state; its first values are `851401618, 1804928587, 758783491,
959030623, 684387517`.

`reset` and `destroy` join a pending worker. `reset` clears local velocity and
pressure surfaces but deliberately never reseeds the process-global random
stream, matching ENB4's absence of a seed call.
