# L.L.E. session handoff — 2026-08-06

Canonical worktree:

`F:\lle-1.0.5.1-b1-worktree\LLEUnified`

Branch:

`codex/lle-1.0.5.1-b1`

Target release: `1.0.5.4`. The current tester manifest is intentionally still
`versionName 1.0.5.3`, `versionCode 29`; no release/tag was created.

## Current tester

- APK: `F:\lle-1.0.5.1-b1-worktree\LLEUnified\build\arm64-v8a-test\LLE64-arm64-v8a-tester.apk`
- SHA-256: `BB16EA4878B4DA10FEAEFA5ADADCF060E3156ED811F24B927320A01EACF542A4`
- Package: `com.codex.lle64.test`
- Samsung-free, ARM64-only.
- Installed on tablet `R5GL523YFHY` (SM-X236B); accessibility remained enabled.

## Completed implementation in this worktree

### Tablet profiles

- Explicit Tablet mode alongside Fold mode.
- Independent `tablet_portrait` and `tablet_landscape` colormap/cache routing.
- Independent portrait and landscape touch-box regions.
- Rotation/profile invalidation, setup/control UI and debug-report fields.
- Physically verified automatic portrait/landscape cache isolation at
  `1200x1920` and `1920x1200`.
- Still required: imported-source/previews, phone `single`, and Fold
  `cover/main` regression.

### Notification shade / quick panel

- Renderer and touch listener can be suppressed independently.
- Event-driven probable/confirmed shade state, bounded structural scan,
  debounced restore and privacy-safe diagnostics are present.
- Galaxy S23 validation had passed open/interact/close, PIN, Assistant,
  messaging apps and global-actions regression.
- Tablet testing exposed a false probable-shade latch during normal wake: the
  scan could take 327 ms and return `partial`; suspicion was only cleared on a
  full `success`, leaving the touch listener non-touchable indefinitely.
- Latest patch adds a 500 ms fail-open only for an unconfirmed suspicion.
  Confirmed shade state remains blocked. This latest patch built and was
  structurally checked, but needs repeated physical OFF/ON and real-QS testing.

### Tablet wake touch-listener failure

Reproduced with a user gesture definitely inside the configured portrait box:

- touch box: `(0,640)-(1200,1730)`;
- missed DOWN examples: `(639,1152)`, `(534,1278)`, `(578,1243)`;
- L.L.E. window was attached and visible, but `dumpsys input` showed
  `NOT_FOCUSABLE | NOT_TOUCHABLE | TRUSTED_OVERLAY`;
- InputDispatcher therefore sent the gestures to Samsung SystemUI.

Cause: Samsung can make the accessibility-overlay input handle non-touchable
while the display is OFF without changing the app-owned `LayoutParams`. On
SCREEN_ON, L.L.E. saw identical desired flags and skipped `updateViewLayout`,
so the stale input handle survived the wake.

Fix in `ChargingAccessibilityService`:

- force one touch-listener relayout after SCREEN_ON;
- preserve the current desired touchability so PIN/QS/global-actions safety
  remains authoritative;
- do not expand the configured touch box or add a full-screen input shield.

Structural validation after the fix showed the live handle as
`NOT_FOCUSABLE | TRUSTED_OVERLAY`, with the correct touchable region and no
`NOT_TOUCHABLE`. A subsequent manual run showed correct DOWN/MOVE/UP delivery,
but later rapid cycles exposed the separate false-QS-suspicion latch described
above. Revalidate both fixes together using the latest tester.

### Sparkling Bubbles

- Samsung's original renderer was temporarily tested and then removed from the
  build/worktree. The stock renderer also produces mostly white particles on
  this wallpaper, so the attempted hue/gamut compensation was reverted.
- `SparklingBubblesAppOwnedGlView` now generations asynchronous animation-stop
  requests. A stale idle stop from the hint can no longer stop rendering after
  a new touch/warm-up.
- No Samsung legacy ELF is included in the tester.

### S6 Water Droplet cadence

- Root cause: `nativeStep()` ran once per display vsync although the recovered
  Samsung simulation expects 60 Hz. Thus 90 Hz ran at 1.5x and 120 Hz at 2x.
- Rendering still follows display refresh; native simulation now uses a fixed
  60 Hz accumulator.
- Maximum two simulation steps per draw; stalls over 66 ms collapse to one
  step instead of replaying backlog.
- Clock resets across EGL recreation, resize, background replacement, reset,
  park, pause and restart. Idle/keep-alive count simulation ticks.
- Deterministic timing checks passed at 60/90/120 Hz and tester build passed.
- Still required: physical portrait/landscape and adaptive-refresh validation.

## Last validation

- `git diff --check`: passed (only expected LF/CRLF warnings).
- `build-arm64.ps1 -Tester`: passed.
- APK signature verification: v1/v2/v3 passed for the tester signing setup.
- APK contents verified Samsung-free and ARM64-only.
- Post-install accessibility service remained enabled.
- Latest automated OFF/ON check showed `LLETouchListenArea1` touchable with
  region `(0,640)-(1200,1730)`.
- Latest 500 ms unconfirmed-QS timeout has not yet had a full repeated manual
  validation session or real quick-panel regression on the tablet.

## QA evidence on F:

- `F:\LLE-QA\2026-08-06-tablet-sparkling-detach`
- `F:\LLE-QA\2026-08-06-tablet-early-touch`
- `F:\LLE-QA\2026-08-06-tablet-touchbox-miss-live`
- `F:\LLE-QA\2026-08-06-tablet-touchbox-relayout-validation`
- `F:\LLE-QA\2026-08-06-tablet-touchbox-probe-timeout-validation`

Do not commit QA logs or any original Samsung binaries. Never access or commit
keys, P12 files, passwords or `.keys`.

## Start here tomorrow

1. Read `TODO-1.0.5.4.md`, this handoff and `CURRENT_OPEN_WORK.md`.
2. Confirm the tablet still has the latest tester hash above installed.
3. Run at least 10 OFF/ON cycles, including fast wake touches inside the saved
   portrait box. Capture logcat and check every inside DOWN reaches
   `LLETouchListenArea1`.
4. Open and close the real quick panel repeatedly. Verify the box releases
   promptly while QS is open and restores after close; specifically confirm
   the 500 ms fail-open does not re-enable it over a genuinely open panel.
5. Select S6 Water Droplet and physically verify cadence in portrait,
   landscape and adaptive refresh.
6. Continue the remaining `TODO-1.0.5.4.md` release gates. Do not bump, sign a
   production APK, tag or publish without explicit authorization.
