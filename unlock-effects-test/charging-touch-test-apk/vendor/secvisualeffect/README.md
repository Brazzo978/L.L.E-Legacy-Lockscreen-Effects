# Samsung visual-effect dex

`classes.dex` is based on `extracted/secvisualeffect_hybrid_dex/classes.dex` and keeps
the Samsung renderers that are compatible with LLE's transparent accessibility overlay.

Local Popping Colours fidelity patch:

- `ParticleSpaceEffect.handleTouchEvent`: keep 15 dots on `ACTION_DOWN`, use the S5
  value of 3 dots on `ACTION_MOVE`.
- `ParticleSpaceEffect$4.run`: use the S5 unlock-affordance value of 50 dots.

The original S5 reference is
`extracted/s5_secvisualeffect_smali/com/samsung/android/visualeffect/lock/particle`.
The hybrid wrapper is retained instead of copying the full S5 `ParticleSpaceEffect`
because the latter owns and draws an opaque wallpaper `ImageView`; LLE must use the
bitmap only as an invisible colour map over the real lockscreen.

Patched dex SHA-256:
`954337D39687E982D7647CB84F0589186B0B7637733D15BBEC816142AD239E41`
