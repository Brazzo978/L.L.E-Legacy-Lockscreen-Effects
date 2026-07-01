# Project Notes For Agents

## Charging Lock Test

- Path: `charging-lock-test-apk`
- Package: `com.codex.charginglocktest`
- This is the stable APK the user tested and called "FUNZIONA PERFETTA".
- Do not modify it while iterating on unlock effects. It uses `ChargingAccessibilityService` with a fullscreen `TYPE_ACCESSIBILITY_OVERLAY`, transparent and `FLAG_NOT_TOUCHABLE`, so it does not block unlock/touch.

## Charging Touch Test

- Path: `charging-touch-test-apk`
- Package: `com.codex.chargingtouchtest`
- This is the active experimental app for lockscreen touch input and S4 unlock effects.
- Charging doodles and unlock effects are separate concerns. Unlock FX must not require charging; charging remains relevant only to doodle visibility.
- The touch listen box is a small touchable `TYPE_ACCESSIBILITY_OVERLAY`; the doodle and FX overlays stay pass-through.
- The listen box is calibrated from the app with `TouchBoxSetupActivity` and stored in `overlay_prefs` as `touch_box_*`.
- Default listen box on SM-S918B 1080x2316: `left=0`, `top=730`, `right=1080`, `bottom=2100`. This replaced the earlier rounded `60,710,1030,1900` box by extending to screen edges, extending downward, and trimming 20 px from the top. Touch box coordinates are rounded to 10 px on save/read; old defaultish prefs migrate to the new default automatically.
- Default debug toggles: touch area on, transparent touch area on, doodle can be toggled off with `show_doodle`.
- `debug_lens_loop` is no longer exposed in the UI and `OverlayPrefs.debugLensLoop()` returns false.
- Unlock effect picker is stored in `unlock_effect`: `0` = S4 lens flare, `1` = S5 effect slot for the upcoming port. Until S5 is ported, S4 remains the only renderer.

## S4 Lens Flare Status

- Direct reflection into Samsung `com.samsung.android.visualeffect.EffectView` loaded but rendered incorrectly in the accessibility overlay: a fixed/bad frame stayed at the top-left.
- `demo-apk`/FX selector uses the same Samsung wrapper successfully in its Activity/wallpaper context, so the failure appears to be the accessibility overlay rendering context, not the touch coordinates.
- The original Samsung smali path is `extracted/secvisualeffect_hybrid_smali/com/samsung/android/visualeffect/lock/lensflare/LensFlareEffect.smali`.
- Important original behavior from smali:
  - `ACTION_DOWN` calls `showLight(rawX, rawY)`.
  - `ACTION_MOVE` calls `move(rawX, rawY)`.
  - `ACTION_UP/CANCEL` calls `hide()`.
  - finger `Y_OFFSET` is `-80` px.
  - key durations: show `6000ms`, tap `4000ms`, fade-out `500ms`, unlock `1200ms`.
- Current touch APK therefore uses a hardware-accelerated Canvas renderer in `LensFlareEffectView` with the original S4 bitmap assets and core timing constants. If exact Samsung parity is required later, continue reverse-porting the smali math into this Canvas class rather than returning to the broken accessibility-overlay wrapper.
- The Canvas renderer now separates the S4 phases instead of using the earlier generic burst:
  - `ACTION_DOWN`/`beginGesture` starts the tap animation immediately from the touched point.
  - The tap phase draws the 5 Samsung tap hexagons plus ring, particle, and long-light using the smali timing formulas and the original `4000ms` `QuintEaseOut` curve.
  - Drag draws the fog/light and distance-based drag hexagons while the finger moves.
  - Completed swipe starts the `1200ms` unlock rainbow along the start-to-current vector.
  - PIN handoff starts about `700ms` after a completed swipe-like movement. The trigger threshold is intentionally low (`8dp`) so short swipes still request PIN entry. The lens flare view is kept alive for another `900ms` so the unlock sound/animation is not cut by `SoundPool.release()`.
- Fidelity audit notes:
  - `Y_OFFSET`, `MAX_ALPHA_DISTANCE`, and `TAP_AREA_RADIUS` are scaled with `min(widthPixels,heightPixels) / 1080`, matching Samsung `lensFlareinit`.
  - Drag hexagon assets follow Samsung order: blue, orange, blue, orange, green, green.
  - Tap hexagon assets follow Samsung modulo order: blue, orange, green, with persistent per-hex random rotations.
  - Not ported because they are outside the current lockscreen swipe test path: Samsung hover/stylus affordance animation and Samsung internal Activity/wallpaper wrapper.
