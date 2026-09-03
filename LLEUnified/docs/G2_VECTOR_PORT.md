# G2 Vector - first tester port (2026-09-03)

The initial port used the 1.0.6.2 tester. The user subsequently approved the
effect on the S23 and its LLE-style picker icon; it is now included in the
1.0.6.3 development/tester build. No public release is implied by this note.

## Donor and implementation

- User-supplied `G2+Vector+Locker_1.1_APKPure.apk`, package
  `com.optimusdev.vector`, version 1.1 / 110, min SDK 14 / target 21.
- SHA-256: `4af254a810076ec8a86087eca8e92cd5a17047051bd977982f5f031b3bd411c5`.
- No native ELF in the donor. Recovered Java renderer:
  `com.optimusdev.vector.a.b.l`; geometry classes `a/b/c/e/f/h/k`.
- Port is Java source in LLE (`LgVectorScene`, `LgVectorEffectView`), using HWUI
  Canvas primitives and the two original PNGs, not an embedded APK, donor DEX,
  XLocker app, or new vendor EGL surface.
- Resources imported byte-for-byte: `vector_line.png`, `vector_tab_line.png`,
  `vector_lock.ogg`, `vector_touchdown.ogg`, `vector_unlock.ogg` and donor icon.
  The icon is a first-test placeholder, not a new matching icon-set design.
  No animated preview is claimed; long-press uses the static icon.

Extracted APK and JADX output are local ignored artifacts in `build/vector-donor`
and `build/vector-jadx`; they are not packaged or committed.

## Recovered values and translation

- Minimum radius 44 dp; boundary radius 113.32999 dp (donor resources).
- Touch-only pulse: 680 ms accelerate/decelerate, radius `1.3 * min * progress`;
  4.666667 dp bands, cutoff `min + 3.5 * bandWidth`.
- Twelve white tap spokes, 30 degree separation, stagger `0.3 + index/60`,
  normalized ramp over 0.5; radius `(1.3 + .7 * ramp) * min`, cutoff `1.8 * min`.
- Drag activates after half the minimum radius; distance is measured from DOWN
  and remains reversible. Outer radius at the first midpoint is `.9266 * boundary`.
- The two 201-point arc strips start at 44 and 150 degrees in the donor's
  `sin(theta),cos(theta)` coordinate convention. BOTH update methods enumerate
  decreasing angles; do not infer the white arc direction from its initializer,
  whose geometry is replaced by `b.c(inner,outer)` before drawing.
- Exactly 11 distance-driven points, not an emitter or a looping animation.
  `progress = 7.35 * normalize(0, boundary/2, innerRadius)`. The shared donor
  `k.a(lo,hi,value)` is a NORMALIZER, not a clamp-to-value.
- Four original randomly selected palettes; white-to-colour particle transition,
  sizes 6.67 dp for the first six, 3.33..26.67 dp times 1.2..2 for the last five,
  expanding central cutout after normalized lifetime .5, final fade .9..1.
- `vector_main_fs` mixes lock image and palette band colour 50/50; resulting
  band alpha is `.5 + .5 * (1 - .7 * normalize(0,boundary,drag))`.
- The donor `Q.f` is primary image SCALE, not background ALPHA. Its subtle
  `1 + .5 * normalize(boundary/4, diagonal, drag)` is retained only inside the
  tinted annulus. Last Screen is never scaled by the gesture.
- Cancellation 300 ms accelerate; unlock 400 ms accelerate. A tap/hint completes
  its own 680 ms clock without waiting for another touch. Reset/detach/destroy
  cancel pending hint callbacks, and terminal input cannot resurrect the scene.

## Required LLE adaptations

The donor replaces its full lockscreen plane and exposes the app underneath through
transparent pixels. LLE is an overlay and cannot do that directly:

1. Primary input remains the regular lockscreen capture, sampled only in the tint band.
2. Secondary input is independently resolved Last Screen, including the existing
   per-display/profile/dimension fallback. It fills the opening beneath that band.
3. The exterior stays transparent, preserving live SystemUI rather than freezing its
   clock and icons or applying the captured app over the full display on DOWN.
4. At the end of the 400 ms unlock, retain an opaque, fixed Last Screen for 550 ms
   without fade. The existing LG non-touchable visual handoff lets SystemUI progress.
5. Bitmap copies remain owned by the renderer. Released HWUI display-list references
   are allowed to retire naturally rather than recycling a bitmap in RenderThread use.

Available as appended effect ID 41 (previous IDs unchanged), ARM64 only. Random,
two-source cache controls, readiness, imported/automatic primary cache and lock
sound use the existing integration paths. No new HFR/speed switch is introduced.

## Verification

Host command from `LLEUnified`:

```powershell
javac -encoding UTF-8 -source 8 -target 8 -d build/vector-host-tests `
  src/com/codex/lle/LgVectorScene.java tests/com/codex/lle/LgVectorSceneTest.java `
  src/com/codex/lle/RuntimeSurfaceBlockState.java tests/com/codex/lle/RuntimeSurfaceBlockStateTest.java
java -cp build/vector-host-tests com.codex.lle.LgVectorSceneTest
java -cp build/vector-host-tests com.codex.lle.RuntimeSurfaceBlockStateTest
```

The scene tests cover donor radius transitions, 100 consecutive completion cycles,
stationary tap/hint expiry, reverse drag, late UP after reset, non-finite inputs,
edge-touch full coverage and precise 550 ms hold. They do not establish on-device
visual parity, HWUI behaviour, or frame-rate performance.

Device acceptance still required: tap and release, long hold, slow reverse drag,
fast unlock, repeated screen-off/wake, random selection, and both Fold profiles.
Compare small arcs/particles and band colours with the original before release.

Checks completed in this pass:

- Scene host suite: PASS (522 assertions); runtime-block decision suite: PASS.
- `build-arm64.ps1 -Tester`: PASS; no ARM32 build. Package `com.codex.lle64.test`,
  ARM64 only, versionCode 44 / versionName 1.0.6.2. Signature verification passed.
- All three effect sounds and both line textures match donor SHA-256 byte-for-byte.
- Installed with `adb install -r` successfully on S23 Ultra `RFCW30S277B` (SM-S918B).
  The phone was locked and its stable Accessibility service still active: the new
  renderer was NOT exercised on device in this pass. Stable app data was untouched.
- Tester artifact: `build/beta/LLE64-G2-Vector-cocktailbar-tester-20260903.apk`,
  35,139,983 bytes; SHA-256
  `a198568ff49ca502dbf020e881a96822586e44463d38f67b939511d8df3316e3`.

## Secondary Cocktail Bar correction

Issue #35's A51 1.0.5.7 log shows `window_gone` immediately followed by
`blockedPackageSurface=true`, with the old Cocktail Bar package still cached.
The service now retires that same cached package only after the active/focused
window scan confirms ABSENT and the existing grace has elapsed. PRESENT/UNKNOWN
still block; camera/Edge Panel/call safeguards are not removed.

Host tests verify that stale-package decision, not the Android window enumeration
or the A51 symptom end-to-end. On A51, confirm recovery after the handle disappears
and suppression while the actual Edge Panel is open. A transient live panel event
can still temporarily suppress LLE; this patch addresses the stale tail only.
