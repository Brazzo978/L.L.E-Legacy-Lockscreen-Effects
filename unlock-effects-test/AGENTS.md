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
- Default debug toggles: unlock effect on, touch area on, transparent touch area on, doodle can be toggled off with `show_doodle`.
- `debug_lens_loop` is no longer exposed in the UI and `OverlayPrefs.debugLensLoop()` returns false.
- Charging doodles and unlock effects are intentionally separate:
  - `show_lock`, `show_aod`, `show_home`, `show_doodle`, charging state, season, and battery percent affect only the charging doodle.
  - `unlock_effect_enabled`, `unlock_effect`, `debug_touch_area`, `debug_touch_transparent`, and the touch box affect only unlock FX input/rendering.
  - Runtime priority: if the charging doodle is actually visible on the current surface, unlock FX/listen box are suppressed. Example: phone charging + lockscreen doodle enabled means no unlock effect until the doodle is hidden/off/not charging.
- PIN/bouncer entry is a separate blocked surface for both doodle and unlock FX. The touch APK now enables `canRetrieveWindowContent`, `flagRetrieveInteractiveWindows`, and `flagReportViewIds` so `ChargingAccessibilityService` can detect Samsung/SystemUI bouncer windows/nodes (`Bouncer`, `keyguard_pin`, `keyguard_password`, pattern/sequence text, etc.). Bouncer detection must consider only active/focused Accessibility windows; hidden/non-focused Bouncer windows can remain present in Samsung window state after returning to the base lockscreen and must not keep unlock FX disabled.
- On `ACTION_SCREEN_ON`, the service first runs a legacy/fast visibility pass without scanning Accessibility windows, so unlock FX/listen box can mount immediately from `interactive + locked`/`isDeviceLocked`. It then starts the lockscreen polling loop and still schedules content-aware refreshes at 35/140 ms as extra early correction points.
- To reduce first-touch latency, the touch listen box is cached across screen-off when it was already active on the lockscreen. If it was not active yet, `ACTION_SCREEN_OFF` schedules pre-arm attempts at 80/180 ms; when the device is locked, no doodle/AOD priority is active, and unlock FX is enabled, the touch box is attached while the screen is still off so the next wake does not wait for `WindowManager.addView`.
- While the phone is in the interactive lockscreen session, `ChargingAccessibilityService` runs a low-latency polling loop: fast visibility pass every 10 ms, content-aware Accessibility scan about every 40 ms. The fast pass keeps the touch box immediate; the content-aware pass corrects PIN/pattern/bouncer and notification shade state, including returning from PIN back to the base lockscreen. The loop stops on screen-off/user-present/unlocked state; do not turn it into global always-on polling.
- Notification shade handling has an event-side fast guard too: SystemUI events containing specific shade/quick-settings text such as `Area notifiche` mark `notificationShadeVisible=true`, remove the touch box/effect immediately, and then let the delayed content-aware scan confirm/clear the state. This avoids starting unlock FX during the brief window before the full scan catches up.
- Unlock effect picker is stored in `unlock_effect`: `0` = S4 lens flare, `1` = S3 ripple slot. Until S3 is ported, S4 lens flare is the only implemented renderer; selecting the S3 slot must not silently show S4 as if it were S3.

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
  - PIN handoff starts about `400ms` after a completed swipe, then waits another `60ms` after removing the touchable box before dispatching the synthetic swipe. The trigger threshold is back to `120dp` to avoid firing too early. The lens flare view is kept alive for another `900ms` so the unlock sound/animation is not cut by `SoundPool.release()`.
- Fidelity audit notes:
  - `Y_OFFSET`, `MAX_ALPHA_DISTANCE`, and `TAP_AREA_RADIUS` are scaled with `min(widthPixels,heightPixels) / 1080`, matching Samsung `lensFlareinit`.
  - Drag hexagon assets follow Samsung order: blue, orange, blue, orange, green, green.
  - Tap hexagon assets follow Samsung modulo order: blue, orange, green, with persistent per-hex random rotations.
  - Not ported because they are outside the current lockscreen swipe test path: Samsung hover/stylus affordance animation and Samsung internal Activity/wallpaper wrapper.

## S3 Ripple Status

- The original S3 ripple from the demo/APK is the old keyguard policy OpenGL/native effect, not a modern `com.samsung.android.visualeffect.EffectView` wrapper.
- Main original paths:
  - `extracted/s3_android_policy_smali/com/android/internal/policy/impl/keyguard/sec/RippleUnlockView.smali`
  - `extracted/s3_android_policy_smali/com/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer.smali`
  - `extracted/s3_android_policy_smali/com/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender.smali`
  - `extracted/s3_android_policy_deodex_smali/com/android/internal/policy/impl/keyguard/sec/KeyguardEffectView.smali`
- Demo integration paths:
  - `demo-apk/src/com/codex/s4unlockfx/OriginalSamsungEffectHost.java` registers "S3 ripple original" as `com.android.internal.policy.impl.keyguard.sec.RippleUnlockView`.
  - `demo-apk/src/com/codex/s4unlockfx/SystemUiLegacyEffectView.java` reflects `show`, `handleTouchEvent(View, MotionEvent)`, `handleUnlock`, `cleanUp`, and `reset`.
- Relevant demo assets/resources:
  - `demo-apk/res/drawable-nodpi/s3_reflectionmap.jpg`
  - `demo-apk/res/drawable-nodpi/s3_keyguard_default_wallpaper.jpg`
  - `demo-apk/res/drawable-nodpi/s3_default_wallpaper.jpg`
  - `demo-apk/res/drawable-nodpi/keyguard_default_wallpaper.jpg`
  - `demo-apk/res/raw/s3_ripple_down.ogg`
  - `demo-apk/res/raw/s3_ripple_up.ogg`
  - `demo-apk/res/raw/s3_gravity_effect.ogg`
  - `demo-apk/res/values/s3_ripple.xml`
  - native library reference: `extracted/s3_system_files/lib/libWaterRipple.so`
- Direct wrapper risk is high for the touch APK because the real S3 effect is `GLSurfaceView` plus `libWaterRipple.so`, likely 32-bit/old Samsung-framework dependent, and the S4 direct Samsung wrapper already rendered incorrectly in `TYPE_ACCESSIBILITY_OVERLAY`.
- Recommended production path: reverse-port S3 ripple into an app-owned renderer, likely Canvas or a controlled GL view, using the S3 reflection/wallpaper/ripple audio assets and the original smali/JNI behavior as the reference.
