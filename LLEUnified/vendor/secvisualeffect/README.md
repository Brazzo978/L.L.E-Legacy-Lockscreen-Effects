# Samsung visual-effect dex

`classes.dex` is based on `extracted/secvisualeffect_hybrid_dex/classes.dex` and keeps
the Samsung renderers that are compatible with LLE's transparent accessibility overlay.

Local Popping Colours fidelity patch:

- `ParticleSpaceEffect.handleTouchEvent`: keep 15 dots on `ACTION_DOWN`, use the S5
  value of 3 dots on `ACTION_MOVE`.
- `ParticleSpaceEffect$4.run`: use the S5 unlock-affordance value of 50 dots.
- `ParticleEffect`: request Canvas redraws every 16 ms instead of every 2 ms. Particle
  motion, lifetime and alpha are frame-counted, so this preserves the intended 60-step
  cadence on 120/144 Hz panels without changing the display refresh rate.

The original S5 reference is
`extracted/s5_secvisualeffect_smali/com/samsung/android/visualeffect/lock/particle`.
The hybrid wrapper is retained instead of copying the full S5 `ParticleSpaceEffect`
because the latter owns and draws an opaque wallpaper `ImageView`; LLE must use the
bitmap only as an invisible colour map over the real lockscreen.

Transparent LockBG lifecycle patches:

- clear the GLES colour buffer to transparent before every native LockBG frame;
- when the native animation reports completion, clear once more before switching to
  `RENDERMODE_WHEN_DIRTY`. Samsung's opaque wallpaper pass used to overwrite that
  final frame; without the extra clear, released Abstract Tiles could remain frozen
  on LLE's transparent Surface until the next gesture.

Patched dex SHA-256:
`206265D2719C5223E57412871B2B778DC56A088300B52B1FEDEB548BFB7EEDB0`
