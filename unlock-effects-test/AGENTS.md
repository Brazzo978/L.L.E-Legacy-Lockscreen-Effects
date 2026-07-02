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
- Default debug toggles: unlock effect on, internal touch area on, transparent touch area on, doodle can be toggled off with `show_doodle`.
- `ControlActivity` uses two top tabs: `Charging doodle` contains doodle/charging controls plus the doodle-only `Rolling battery percent` debug toggle; `Lockscreen effect` contains unlock effect picker plus touch box calibration/debug controls.
- The old `Touch debug area` UI toggle was removed because it duplicated `Unlock effect on lockscreen`; `ControlActivity` forces `debug_touch_area=true` so the hidden pref cannot silently disable unlock input. The visible lockscreen debug toggle is now inverted: `Show touch box` writes `debug_touch_transparent=false`; normal use leaves the touch box transparent.
- `debug_lens_loop` is no longer exposed in the UI and `OverlayPrefs.debugLensLoop()` returns false.
- Charging doodles and unlock effects are intentionally separate:
  - `show_lock`, `show_aod`, `show_home`, `show_doodle`, charging state, season, and battery percent affect only the charging doodle.
  - `unlock_effect_enabled`, `unlock_effect`, `debug_touch_area`, `debug_touch_transparent`, and the touch box affect only unlock FX input/rendering.
  - Runtime priority: if the charging doodle is actually visible on the current surface, unlock FX/listen box are suppressed. Example: phone charging + lockscreen doodle enabled means no unlock effect until the doodle is hidden/off/not charging.
- PIN/bouncer entry is a separate blocked surface for both doodle and unlock FX. The touch APK now enables `canRetrieveWindowContent`, `flagRetrieveInteractiveWindows`, and `flagReportViewIds` so `ChargingAccessibilityService` can detect Samsung/SystemUI bouncer windows/nodes (`Bouncer`, `keyguard_pin`, `keyguard_password`, pattern/sequence text, etc.). Bouncer detection must consider only active/focused Accessibility windows; hidden/non-focused Bouncer windows can remain present in Samsung window state after returning to the base lockscreen and must not keep unlock FX disabled.
- On `ACTION_SCREEN_ON`, the service first runs a legacy/fast visibility pass without scanning Accessibility windows, so unlock FX/listen box can mount immediately from `interactive + locked`/`isDeviceLocked`. It then starts the lockscreen polling loop and still schedules content-aware refreshes at 35/140 ms as extra early correction points.
- To reduce first-touch latency, the touch listen box is cached across screen-off when it was already active on the lockscreen. If it was not active yet, `ACTION_SCREEN_OFF` schedules pre-arm attempts at 80/180 ms; when the device is locked, no doodle/AOD priority is active, and unlock FX is enabled, the touch box is attached while the screen is still off so the next wake does not wait for `WindowManager.addView`.
- Pre-armed touch box state is mounted but not touchable: `ChargingAccessibilityService.syncTouchDebugOverlay(true, false)` keeps the calibrated overlay window and `TouchDebugView` ready with `FLAG_NOT_TOUCHABLE` and `listeningEnabled=false`. On lockscreen `SCREEN_ON`, `syncTouchDebugOverlay(true, true)` only updates flags/listening state, which is cheaper than creating the window at wake. Do not add screen-off polling or animation loops; the low-drain model is preloaded RAM + parked window + no redraws.
- Unlock effect renderers are intentionally kept in RAM after preload. `removeUnlockEffectOverlay()` detaches/resets the selected renderer window but does not destroy decoded/original effect resources; `destroyUnlockEffectOverlay()` is reserved for service cleanup/effect change. This avoids first-touch decode/sound-load latency.
- Latency tuning after 2026-07-02 testing:
  - `ACTION_SCREEN_OFF` schedules an immediate prearm plus 80/180 ms retries.
  - Notification shade events are ignored while non-interactive/AOD; otherwise Samsung lockscreen text `Area notifiche` can falsely keep `notificationShadeVisible=true` and block the fast wake path.
  - PIN/bouncer clear has a 120 ms grace window so transient content-scan misses do not briefly re-enable the touch box during PIN entry.
  - If a PIN request is active and Samsung Honeyboard/Gboard appears, treat it as PIN surface until it disappears.
  - `LensFlareEffectView` calls `Bitmap.prepareToDraw()` for all S4 lens flare bitmaps and requests an invisible warm-up draw when the overlay is attached, reducing the first visible frame delay after `ACTION_DOWN`.
- While the phone is in the interactive lockscreen session, `ChargingAccessibilityService` runs a low-latency polling loop: fast visibility pass every 10 ms, content-aware Accessibility scan about every 40 ms. The fast pass keeps the touch box immediate; the content-aware pass corrects PIN/pattern/bouncer and notification shade state, including returning from PIN back to the base lockscreen. The loop stops on screen-off/user-present/unlocked state; do not turn it into global always-on polling.
- Notification shade handling has an event-side fast guard too: SystemUI events containing specific shade/quick-settings text such as `Area notifiche` mark `notificationShadeVisible=true`, remove the touch box/effect immediately, and then let the delayed content-aware scan confirm/clear the state. This avoids starting unlock FX during the brief window before the full scan catches up.
- Unlock effect picker is stored in `unlock_effect`: `0` = S4 lens flare, `1` = S3 ripple slot, `2` = S5 popping colours, `3` = reserved/disabled Watercolor slot, `4` = S5 coloured droplets, `5` = S5 sparkling bubbles. S3 is still only a placeholder slot and must not silently show S4 as if it were S3.

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
  - S4 Lens Flare PIN handoff starts about `400ms` after a completed swipe, then waits another `60ms` after removing the touchable box before dispatching the synthetic swipe. The trigger threshold is back to `120dp` to avoid firing too early. The lens flare view is kept alive for another `900ms` so the unlock sound/animation is not cut by `SoundPool.release()`.
- Fidelity audit notes:
  - `Y_OFFSET`, `MAX_ALPHA_DISTANCE`, and `TAP_AREA_RADIUS` are scaled with `min(widthPixels,heightPixels) / 1080`, matching Samsung `lensFlareinit`.
  - Drag hexagon assets follow Samsung order: blue, orange, blue, orange, green, green.
  - Tap hexagon assets follow Samsung modulo order: blue, orange, green, with persistent per-hex random rotations.
- Not ported because they are outside the current lockscreen swipe test path: Samsung hover/stylus affordance animation and Samsung internal Activity/wallpaper wrapper.

## S5 Popping Colours Status

- S5 Popping Colours / Particle Space is implemented in the touch APK as effect picker value `2`.
- S5 Popping Colours uses its own quicker PIN handoff delay: `200ms` after completed swipe, then the shared `60ms` before the synthetic swipe.
- Main touch APK files:
  - `src/com/codex/chargingtouchtest/UnlockEffectRenderer.java`
  - `src/com/codex/chargingtouchtest/PoppingColoursEffectView.java`
  - generic mount/gesture flow in `ChargingAccessibilityService`
- The implementation uses the original Samsung visual-effect dex path already packaged as `classes2.dex` from `extracted/secvisualeffect_hybrid_dex/classes.dex`.
- Original Samsung identity:
  - effect id `3`
  - `com.samsung.android.visualeffect.EffectView.setEffect(3)`
  - `com.samsung.android.visualeffect.lock.particle.ParticleSpaceEffect`
  - `handleTouchEvent(MotionEvent, View)` reads `event.getRawX()/getRawY()`
  - `handleCustomEvent(0, {"BGBitmap": bitmap})` sets the wallpaper/color sampling bitmap
  - `handleCustomEvent(2, new HashMap())` triggers unlock
- Visual behavior from smali:
  - DOWN creates 15 dots.
  - MOVE creates dots from the wallpaper color under the raw touch point.
  - UP/CANCEL resets the internal current point to center.
  - Unlock accelerates the live dot pool and clears after Samsung's original short unlock animation.
- Assets copied from `demo-apk` into touch app:
  - `res/raw/particle_tap.ogg`
  - `res/raw/particle_drag.ogg`
  - `res/raw/particle_unlock.ogg`
- Current color-source test plan:
  - Primary/best method: `ChargingAccessibilityService.takeScreenshot()` on the lockscreen, converted from `ScreenshotResult` hardware buffer and sent as `BGBitmap`.
  - Screenshot capture is not continuous. The service captures only if the selected S5 renderer has no real color map in RAM and only once per lockscreen session; on success the map is reused until service restart/effect recreation.
  - If capture fails or is empty, the renderer keeps the solid white `BGBitmap` (`white_fallback`) and the service can retry on the next lockscreen session.
  - Manual refresh exists in the `Lockscreen effect` tab as `Refresh effect background map`. It bumps `popping_color_refresh_token`, clears the selected renderer's background map in RAM, and queues a new screenshot capture for the next valid lockscreen surface.
  - Do not re-add stock/guessed wallpaper fallbacks for now. If screenshot colors are blocked or wrong, the next planned fallback is a wallpaper picker/map supplied by the user.
- The S5 renderer keeps a center-cropped 1080x2316-ish `BGBitmap` in RAM and sends it to Samsung's effect, but does not display the wallpaper layer; the accessibility overlay should show only particles over the real lockscreen.
- Compatibility note: the Samsung dex expects `android.view.animation.interpolator.CubicEaseOut` during `ParticleSpaceEffect.setAnimator()`, but the test phone firmware did not provide it. The APK now includes app-side compatible classes:
  - `src/android/view/animation/interpolator/CubicEaseOut.java`
  - `src/android/view/animation/interpolator/CubicEaseIn.java`
- Accessibility service XML includes `android:canTakeScreenshot="true"` for the S5 color-source test. After installing a build with this service capability changed, Android may require disabling/re-enabling the accessibility service before screenshots are granted.
- 2026-07-02 verification:
  - Build succeeded.
  - Install succeeded.
  - Selecting S5 in the picker succeeded.
  - On lockscreen, logs showed `unlock effect overlay shown type=2 name=S5 popping colours`, touch listen box mounted, and Samsung `VisualEffectParticleEffect` accepted `BGBitmap : 1080 x 2316`.
  - ADB dropped offline during the synthetic swipe test, so physical/user validation of the visible particle trail and PIN handoff is still needed.

## Watercolor / Coloured Droplets / Sparkling Bubbles Prep

- 2026-07-02 native-effect direct-wrapper test result:
  - Watercolor broke the app/test flow when attempted through the original Samsung native wrapper.
  - Watercolor is not an easy direct 64-bit port with current dumps: all found `libsecveWaterColor.so` copies are under 32-bit `system/lib`, while no `lib64/libsecveWaterColor.so` was found.
  - User direction after this finding: use the highest-fidelity route, which means rebuilding the Watercolor renderer in the app instead of pretending the direct native wrapper is production-safe.
- 2026-07-02 faithful Watercolor port direction:
  - For maximum fidelity, do not implement Watercolor as a simple Canvas fake. The original is a GLES/FBO renderer, so the faithful app-owned path should be a custom OpenGL renderer with transparent overlay composition.
  - The original Java/smali layer is only a thin wrapper: `EffectView.setEffect(5)` creates `WaterColorEffect`, `WaterColorEffect` calls `setEffectRenderer(5)`, and `WaterColorRenderer` only sets `mLibName = "libsecveWaterColor.so"`.
  - Touch contract from `GLTextureViewRenderer`: renderer ignores touches until after the first 3 draw frames; `ACTION_DOWN -> Native.onTouch(x,y,0)`, `ACTION_UP -> onTouch(x,y,1)`, `ACTION_MOVE -> onTouch(x,y,2)`, pattern/hover path maps `9/10/7` to native `3/4/5`; unlock is `Native.showUnlock()`.
  - `handleCustomEvent(0, {"Bitmap": bitmap})` loads the background as texture name `bg`; `handleCustomEvent(1, {"StartDelay","Rect"})` is special for Watercolor and simulates an `ACTION_DOWN` at the rect center; `handleCustomEvent(2, ...)` triggers unlock.
  - Native strings/symbols reveal the actual pipeline: `SPDrawRadialWaterBrush`, `SPDrawBGAdvectWaterBrush`, `SPDrawMixWaterBrush`, FBO usage, `Mask1/2/3`, `Noise`, `waterbrush_tube`, `watercolor_mask1/2/3`, `watercolor_noise`.
  - Inference: touch writes radial density/velocity into offscreen textures using the mask/brush assets; then the background is advected/distorted with noise and radial vectors; final pass mixes the distorted background with brightness/saturation/RGB-saturation style parameters.
  - Assets already present and matching the native texture names: `waterbrush_tube.png` 480x480, `watercolor_mask1.png` 641x655, `watercolor_mask2.png` 675x733, `watercolor_mask3.png` 803x793, `watercolor_noise.jpg` 360x640, plus `bg.jpg` as fallback/test background.
  - Visible timing hints: renderer ready after 3 frames, Keyguard wrapper unlock delay about 250 ms, long-press tap repeat about 411 ms, cleanup about 400 ms, sound release about 2000 ms. Exact shader constants, decay, timestep, blend equations, and empty/stop criteria remain inside the 32-bit native library.
  - Do not use the earlier `WatercolorEffectView` reconstruction. It was an approximation, user rejected it, and it has been deleted from the touch app source.
  - Do not present the Samsung native wrapper as the solution either. The direct native path was already observed to break/cover the lockscreen in this overlay context.
  - Current source safety state: Watercolor picker value `3` is reserved but hidden from the UI, and `OverlayPrefs.unlockEffect()` maps old value `3` back to S5 Popping Colours until an exact app-owned renderer exists.
  - `ChargingAccessibilityService` no longer routes `EFFECT_WATERCOLOUR` into `SamsungNativeEffectView`, and `effectUsesScreenshotBackground()` currently remains true only for S5 Popping Colours.
  - Next valid implementation must be a new app-owned OpenGL renderer, likely `WatercolorExactEffectView`, built from the reversed native call-order/shaders/constants. Do not add Watercolor back to the picker until that renderer is mounted.
  - Ghidra/reverse status:
    - `watercolor reverse.txt` is the working reverse log at `C:\Users\Admin\Documents\New project\watercolor reverse.txt`.
    - `createScene` is at Ghidra VMA `0x21c74` / raw `0x11c74`.
    - The constructor/setup called by `createScene` is raw `0xeb90`, which corresponds to Ghidra VMA `0x1eb90`; reverse agents should not discard it as out-of-range.
    - Candidate vtable is raw `0x13c88` / Ghidra VMA `0x23c88`; remaining required work is mapping virtual methods to init/resize/touch/update/draw/isEmpty/clear/showUnlock/destructor.
    - Common shaders have been extracted from `libsecveSrkCommon.so` for `SPDrawRadialWaterBrush`, `SPDrawBGAdvectWaterBrush`, and `SPDrawMixWaterBrush`, but Watercolor-specific pass ordering and parameter values still need the `libsecveWaterColor.so` constructor/vtable reverse.
- Current easier native-test effects:
  - Picker value `4`: S5 coloured droplets, original Samsung effect id `16`.
  - Picker value `5`: S5 sparkling bubbles, original Samsung effect id `14`.
  - Both use `src/com/codex/chargingtouchtest/SamsungNativeEffectView.java` as a reflection wrapper around Samsung `EffectView`.
  - Both are SPhysics/`GLTextureView` effects, not the older `LockBGEffect` common renderer path.
- Packaging state:
  - `classes2.dex` remains copied from `extracted/secvisualeffect_hybrid_dex/classes.dex`.
  - 32-bit native libs still exist under `native-libs/lib/armeabi-v7a` from the first probe.
  - A brief test packaged Note5 arm64 libs under `native-libs/lib/arm64-v8a` (`libColourDropletEffect.so`, `libSparklingBubblesEffect.so`), but that pushed the app to `primaryCpuAbi=arm64-v8a` and broke the previously working S4 Lens Flare/S5 Popping path.
  - The arm64 directory was removed again; the installed recovery build reports `primaryCpuAbi=armeabi-v7a` and S4 Lens Flare works again.
  - No arm64 Watercolor/common `libsecveWaterColor.so`/`libsecveSrkCommon.so` was found in the available dumps.
- Overlay/black-screen mitigation:
  - The first native probe showed Droplets could animate but sometimes covered/blackened the lockscreen because Samsung's GL view was drawing a full opaque surface.
  - `SamsungNativeEffectView.configureTransparentSurfaces()` now forces nested `TextureView` instances to `setOpaque(false)` and still forces `SurfaceView` instances translucent if present.
  - Droplets/Bubbles use a transparent fallback bitmap and no accessibility screenshot background; `effectUsesScreenshotBackground()` currently returns true only for S5 Popping Colours.
  - If Droplets/Bubbles still cover the lockscreen after this patch, treat the direct native wrapper as unsuitable for production and reverse/reimplement the foreground visual layer instead.
- Verification state:
  - Local build succeeded and produced `charging-touch-test-apk/build/ChargingTouchTest-debug.apk`.
  - Recovery build was installed on SM-S918B and `dumpsys package com.codex.chargingtouchtest` reported `primaryCpuAbi=armeabi-v7a`.
  - S4 Lens Flare recovery log showed touch input, Canvas effect begin/finish, and accepted PIN-entry synthetic swipe again.
  - Do not reintroduce partial arm64 packaging in the main test APK unless it is isolated from the working S4/Popping path or all required native dependencies are available.

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
