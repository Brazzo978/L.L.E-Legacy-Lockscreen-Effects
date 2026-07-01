# Agent Notes

## Repository and branches
- Git root: `C:\Users\Admin\Documents\New project`.
- GitHub: `https://github.com/Brazzo978/unlock-effects-test` private repo.
- Default branch `main` preserves the stable charging doodle app.
- Active work branch: `codex/charging-touch-advanced`.
- Stable validated tag: `charging-lock-stable-perfect-2026-06-30`.
- Touch baseline tag: `charging-touch-advanced-baseline-2026-06-30`.

## Stable app: do not break
- Path: `unlock-effects-test\charging-lock-test-apk`.
- Package: `com.codex.charginglocktest`.
- User validated this APK with "FUNZIONA PERFETTA".
- Main implementation path: `ChargingAccessibilityService` using `TYPE_ACCESSIBILITY_OVERLAY`.
- The doodle fullscreen overlay is `FLAG_NOT_TOUCHABLE`, so unlock/touch is not blocked.
- Defaults: lockscreen on, AOD off, home off.
- Keep this app as the known-good baseline unless the user explicitly asks to modify it.

## Advanced touch test app
- Path: `unlock-effects-test\charging-touch-test-apk`.
- Package: `com.codex.chargingtouchtest`.
- Current APK: `unlock-effects-test\charging-touch-test-apk\build\ChargingTouchTest-debug.apk`.
- It is the experimental branch for touch listening and unlock FX.
- Current features:
  - Touch box calibration from app UI via `TouchBoxSetupActivity`.
  - Transparent calibrated touch window using `TouchDebugView`.
  - Optional `Charging doodle overlay` toggle to hide doodles during FX testing.
  - S4 raw sounds copied into `res/raw`: `lens_flare_tap.ogg`, `lens_flare_unlock.ogg`.
  - Current active lens flare path is the original Samsung S4 visual effect dex loaded by `LensFlareEffectView`.
  - Current gesture flow: effect starts on `ACTION_DOWN`, follows `MOVE`, opens PIN only after swipe distance threshold.
  - PIN opening is attempted with Accessibility `dispatchGesture`; service XML includes `android:canPerformGestures="true"`.
- Important separation: charging doodles and unlock FX are separate systems.
  Doodles remain gated by real charging state; unlock/touch FX must work on the lockscreen even when not charging.

## Build and install
- Build stable:
  `powershell -ExecutionPolicy Bypass -File .\unlock-effects-test\charging-lock-test-apk\build.ps1`
- Build touch:
  `powershell -ExecutionPolicy Bypass -File .\unlock-effects-test\charging-touch-test-apk\build.ps1`
- Install touch:
  `adb install -r .\unlock-effects-test\charging-touch-test-apk\build\ChargingTouchTest-debug.apk`
- Open touch settings:
  `adb shell am start -n com.codex.chargingtouchtest/.ControlActivity`
- Logs:
  `adb logcat -s ChargingA11y ChargingTouchDebug ChargingOverlay ChargingLockTest ChargingTouchTest`

## Critical next objective: true S4 Lens Flare
- User explicitly requested exact S4 lens flare, not an approximate/fake effect.
- Do not present a visual approximation as the real port.
- First choice: locate and port the original S4 implementation/assets 1:1.
- If direct port is not possible, reverse engineer behavior fully and reimplement identically in a flexible reusable system.
- Confirmed original Samsung Lens Flare implementation in
  `unlock-effects-test\extracted\secvisualeffect_hybrid_dex\classes.dex`
  and smali under
  `unlock-effects-test\extracted\secvisualeffect_hybrid_smali\com\samsung\android\visualeffect\lock\lensflare`.
- `InnerViewManager.getInstance(context, 11)` returns
  `com.samsung.android.visualeffect.lock.lensflare.LensFlareEffect`.
- Lens Flare is driven by `com.samsung.android.visualeffect.EffectView`:
  `setEffect(11)`, `init(EffectDataObj)`, `handleTouchEvent(MotionEvent, View)`,
  and `handleCustomEvent`.
- Startup commands for S4 Lens Flare are `handleCustomEvent(3, {"manualInit": true})`
  then `handleCustomEvent(3, {"show": true})`.
- Unlock animation command is `handleCustomEvent(2, new HashMap())`.
- Gesture behavior inside Samsung code:
  `ACTION_DOWN` calls `showLight(rawX, rawY)`,
  `ACTION_MOVE` calls `move(rawX, rawY)`,
  `ACTION_UP`/`ACTION_CANCEL` calls `hide()`.
- Required original texture resources copied from `demo-apk` into touch app:
  `keyguard_flare_light_00040`, `keyguard_flare_ring`, `keyguard_flare_particle`,
  `keyguard_flare_long`, `keyguard_flare_rainbow`, `keyguard_flare_hoverlight`,
  `keyguard_flare_vignetting`, `keyguard_flare_hexagon_blue`,
  `keyguard_flare_hexagon_green`, `keyguard_flare_hexagon_orange`.
- Touch app now builds `classes2.dex` from the Samsung visual effect dex and
  `LensFlareEffectView` is a reflection wrapper around the original Samsung effect.
- The old Canvas fake Lens Flare was removed from the active touch flow.
- LensFlareEffectView must initialize Samsung's effect after the accessibility
  overlay has real layout dimensions, then send `manualInit` and `show`.
- The Samsung effect must receive an accepted `ACTION_DOWN` before `MOVE` or `UP`;
  otherwise its original `move()` path can hit null animator state.
- For the touch listen box, do not rely on `MotionEvent.getRawX/Y()` from the
  small accessibility overlay. `TouchDebugView` computes screen coordinates as
  `getLocationOnScreen() + event.getX/Y()` and forwards those to the S4 effect,
  because the original Samsung code consumes `getRawX/Y()` internally.
- Current coordinate test mode binds the S4 lens flare gesture start to the
  center of the configured touch box. MOVE/UP are forwarded as
  `boxCenter + swipeDelta`, so the effect can be checked independently from the
  exact finger-down point inside the box.
