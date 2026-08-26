# L.L.E 1.0.6 — LG G1/G2 clean-room roadmap

Date: 2026-08-15

## Source policy

- XLocker APKs are private behavioural oracles. Reimplement Java/native code for
  modern Android; archived legacy artwork/audio may be restored only where the
  project has explicit redistribution permission.
- Prefer original LG firmware implementations and resources as behavioural
  evidence. Reconstruct ARM64 renderers in app-owned code.
- Candidate firmware baselines are the international stock releases:
  - LG Optimus G E975, Android 4.1.x Jelly Bean (`V10` generation).
  - LG G2 D802, Android 4.2.2 Jelly Bean (`V10` generation).
- A full KDZ is useful because the relevant implementation may be split among
  SystemUI, LG keyguard/framework jars, resources, and vendor EGL libraries.
  No device flashing is required.

## Under-lockscreen source image

LG effects often appear to reveal or distort the launcher/application below the
lockscreen rather than a separately chosen lockscreen wallpaper. L.L.E should
use the following ordered strategy:

1. **Android 14+ experimental window capture.** Enumerate accessibility windows,
   reject L.L.E/SystemUI/secure windows, and call
   `AccessibilityService.takeScreenshotOfWindow(windowId, ...)` for the highest
   valid launcher/application window below the lockscreen.
2. **Last-unlocked underlay cache.** If Android does not expose the underlying
   window while locked, capture the last usable launcher/application surface at
   the lock transition and reuse it on the next wake. Never run continuous
   background capture.
3. **Current lockscreen colormap fallback.** Preserve the existing per-profile
   screenshot route when no safe underlay is available.
4. **No-colormap fallback.** Use S3 None/Mass Tension/Seasonal when capture is
   disabled or every source is invalid.

The underlay cache must be isolated by physical display, fold/tablet profile,
orientation, dimensions, generation and timestamp. A stale portrait image must
never be stretched onto a landscape or fold profile.

`FLAG_SHOW_WALLPAPER` can reveal the system wallpaper behind a translucent
window but does not provide pixels for a distortion shader. `WallpaperManager`
does not reliably expose the user's real wallpaper to an ordinary modern app.
MediaProjection is unsuitable because it requires user consent and modern
Android stops the projection when the device locks. Root/privileged
SurfaceControl capture remains a research-only last resort, not a production
dependency.

## Port order

### Phase 0 — capture probe

- Add tester-only diagnostics for accessibility window type/layer/bounds and
  capture result, without exporting package names or screen contents in the safe
  debug report.
- Validate launcher and foreground-app capture on S23, Fold, tablet and one
  non-Samsung Android 14+ device.
- Keep the existing SystemUI colormap path byte-for-byte selectable for A/B.

### Phase 1 — lightweight baseline

- **S3 None**: first picker item, no colormap, no GL/native allocation. Recovered
  from S3 Neo firmware rather than the XLocker Circle theme.

### Phase 2 onward — user-prioritized port queue

1. **G1 White Hole** — high interest; full-screen warp/feedback likely needs an
   exact source-layer contract.
2. **G1 Dewdrop** — high interest; a strong first underlay-capture oracle because
   local refraction geometry is easy to compare.
3. **G2 Particle** — high interest; transparent particle path plus sampled
   underlay colour. Keep motion and audio clocks independent.
4. **G2 Light Particle** — high interest; classify the firmware renderer before
   assuming the current particle architecture.
5. **G2 Soda** — high interest; recover feedback buffers and unlock chronology.
6. **G2 Crystal** — restored from the archived oracle as a ten-sector lit mesh
   with the four original overlay passes and exact release clocks.
7. **Z1 Blinds** — next active port; the expected two-shader lockscreen-source
   path still needs an APK oracle comparison.
8. **Revolving Glass** — third active experiment and first deliberate two-source
   carrier. Validate separate lockscreen and home/underlay captures without
   destabilizing the existing single-source LG effects.
9. **Z2 Particle** — maybe/final group; a sparkle-only partial port is not a
    useful restoration without the rest of the donor effect.
10. **G1 Ripple** — maybe/final group; it depends on both the captured source
    and moving/deformable state, so a partial visual clone is not useful.
11. **X10** — maybe/final group; low restoration value.

**G2 Pixelate** is deliberately parked at master priority 14. The donor appears
to composite distinct lockscreen and home/underlay captures, so the inspired beta
must not be promoted until the Revolving Glass experiment proves an explicit
two-source contract.

The configured Random-effect pool is a separate 1.0.6 feature and must resolve
one compatible renderer per lock cycle without rerolling on QS/AOD/rotation.
Detailed persistence and fallback requirements live in `TODO-1.0.6.md`.

## Acceptance gates

- Oracle comparison at stock cadence for tap, stationary hold, drag, incomplete
  release and completed unlock.
- Same wall-clock lifetime at 60/90/120/144 Hz; high-frame-rate mode remains
  opt-in until device profiling passes.
- Correct phone/fold/tablet crop in both orientations with no stale or stretched
  underlay.
- Zero renderer failures over 20 lock/unlock cycles and no retained bitmap/GL
  growth after park/recreate cycles.
- System and Media audio routing must continue through `EffectAudio`; do not use
  XLocker audio as a release asset.

## Deferred visual work

- Lens Flare Original, Blue Ring, Blood and Lightning require a separate Note 4
  oracle comparison. Preserve GL/Canvas A/B until all variants match.
