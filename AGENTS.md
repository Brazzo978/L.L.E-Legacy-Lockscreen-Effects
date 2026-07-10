# Agent Notes

## Repository and branches
- Git root: `D:\New project`.
- GitHub: `https://github.com/Brazzo978/unlock-effects-test` private repo.
- Active work branch: `codex/charging-touch-advanced`.
- Stable validated tag: `charging-lock-stable-perfect-2026-06-30`.
- Touch baseline tag: `charging-touch-advanced-baseline-2026-06-30`.

## Legacy apps
- Old app modules must not be revived or targeted by PRs.
- Legacy charging-only app modules were removed from the active repo.
- Port useful code into LLE manually instead of merging/cherry-picking legacy app changes.

## LLE app
- Path: `unlock-effects-test\charging-touch-test-apk`.
- Package: `com.codex.lle`.
- Current APK: `unlock-effects-test\charging-touch-test-apk\build\LLE-debug.apk`.
- LLE means Legacy Lockscreen Effect; it is the experimental branch for touch listening and unlock FX.

## LSE app
- Path: `unlock-effects-test\demo-apk`.
- Package: `com.codex.s4unlockfx`.
- Launcher label: `L.S.E`.
- LSE means Legacy Samsung Effect; keep this demo/reference app alongside LLE.
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
- 2026-07-02 active-effect correction:
  - Current validated/reliable effects are S4 Lens Flare Canvas port and S5 Popping Colours.
  - Picker order requested by user: `S3 ripple WIP`, `S4 Lens Flare`, `S5 Popping Colours`, `N4 Watercolor WIP`.
  - Watercolor picker value `3` is an active WIP slot routed to `WatercolorEffectView`, an app-owned transparent renderer. It uses the original Watercolor mask/tube/noise assets and lockscreen screenshot only as a color map, but it is still not exact and must not be presented as faithful Samsung parity.
  - Watercolor must stay visually transparent outside the local brush marks; do not route it through a native/Surface full-screen renderer that can blacken or repaint the lockscreen.
  - S5 coloured droplets and S5 sparkling bubbles were removed from the active app after phone testing confirmed they are broken/blacken the lockscreen.
  - `SamsungNativeEffectView.java` was removed from the active source. Do not route Watercolor/droplet/bubbles back through the direct Samsung native wrapper.
- 2026-07-03 sync note:
  - GitHub had commits `e1437d0` / `175b9d2` adding an S4 screen-on center hint, but they were based on an older touch app state.
  - Do not merge those commits blindly over the local WIP. The useful S4-only hint logic was manually re-applied on top of the latest local app state.
  - The screen-on hint is a generic renderer hook: 500 ms after `ACTION_SCREEN_ON`, schedule `showUnlockAffordance` at the center of the effect overlay.
  - S4 Lens Flare handles it with the app-owned Canvas tap burst/fog. S5 Popping Colours handles it through Samsung `handleCustomEvent(1, {"StartDelay","Rect"})` after its background color map is ready. N4 Watercolor WIP currently keeps a no-op hint until the effect itself is faithful enough.
- 2026-07-03 S3 Ripple reverse update:
  - Treat S3 ripple as a separate app-owned Fluid/ripple renderer, not a Samsung native wrapper.
  - Original uses OpenGL mesh plus height/velocity arrays and native `Fluid` functions (`Ripple_Render`, `Update`, `Advect*`, `Jacobi`, `AddInk`, `AddVelocity`) to produce refraction/reflection/specular.
  - Saved reverse reports:
    - Java/smali-side parameters: `s3ripplereverse\s3_ripple_smali_params_2026-07-03.md`.
    - Native/GL extraction from agent 2: `s3ripplereverse\s3_ripple_native_extraction_agent2_2026-07-03.md`.
  - Required assets for touch APK: `s3_reflectionmap.jpg`, optional S3 wallpaper fallback/debug images, `s3_ripple_down.ogg`, `s3_ripple_up.ogg`, optional `s3_gravity_effect.ogg`.
  - Transparent overlay rule: output only local wave alpha/delta over the real lockscreen; never draw wallpaper as an opaque layer.
  - Native extraction confirms original fragment alpha is always `1.0`, so the touch APK must reproduce the math but synthesize alpha from local wave energy/gradient/density.
  - Current S3 implementation exists as `S3RippleEffectView.java`, routed to picker value `1`.
  - 2026-07-03 port pass applied exact known Samsung values: detail grid `104x104`, mesh `50x50`, damping `0.94`, wave coefficient `0.5`, second Laplacian coefficient `0.068`, injection radius `3`, drag threshold `150px`, portrait/landscape intensity `0.5/0.35`, `refractiveIndex=0.93`, `reflectionRatio=0.13`, `fresnel/specular/exponent=0.1/0.5/20`.
  - Native `JniWaterRippleRender_ripple @ 0x0000bfe4` maps `mx/my` to cells with `(mx / MESH_SIZE_WIDTH + 0.5) * NUM_DETAILS_WIDTH`; Java smali calls `ripple(glY, glX, intensity, true)`, and the app-owned renderer now follows that path.
  - 2026-07-03 5.5 crosscheck correction: because Samsung injects in a swapped native cell basis and then renders through OpenGL mesh/projection, the Canvas renderer must transpose the height field when drawing. Without that, the ripple can appear to move opposite/rotated relative to the finger.
  - 2026-07-03 brightness correction: normal S3 shader is additive over `bgColor.rgb`, not globally dark. The overlay renderer now encodes a positive delta from the sampled base toward `bg + water/specular`, instead of drawing a dark mixed replacement color.
  - Remaining non-Samsung part is only the required transparent-overlay translation: Samsung outputs `alpha=1.0` fullscreen, while the touch APK must synthesize local alpha from wave/density energy to avoid covering the lockscreen.
  - 2026-07-03 mesh renderer pass: picker value `1` now routes to `S3RippleMeshEffectView`, a transparent app-owned `TextureView`/GLES renderer. It keeps the confirmed CPU simulation but renders a Samsung-shaped `100x100` projected mesh with `aPosition + aHeights`, using the recovered normal S3 vertex/fragment shader math. `S3RippleEffectView` remains in source only as the older Canvas fallback/reference.
  - The mesh renderer requires a live accessibility screenshot/background texture before drawing. It intentionally avoids the fallback wallpaper for visible output because source-over base-cancel math only works if the sampled base matches the real lockscreen pixel.

## Build and install
- Build LLE:
  `powershell -ExecutionPolicy Bypass -File .\unlock-effects-test\charging-touch-test-apk\build.ps1`
- Install LLE:
  `adb install -r .\unlock-effects-test\charging-touch-test-apk\build\LLE-debug.apk`
- Open LLE settings:
  `adb shell am start -n com.codex.lle/.ControlActivity`
- Logs:
  `adb logcat -s ChargingA11y LLEDebug ChargingOverlay LLE LLEControl LleRootDebug`

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
- Current debug loop mode is controlled by `debug_lens_loop` and defaults on in
  the touch test app. When active on the lockscreen it repeatedly sends an
  automatic DOWN/MOVE/UP path inside the configured touch box, without opening
  PIN entry, to diagnose whether the original S4 renderer follows box
  coordinates or collapses elsewhere.
- 2026-07-01 finding: user confirmed the loop still leaves a fixed lens-flare
  frame at top-left, so the issue is not touch coordinate mapping. Current test
  patch forces software layer rendering for the Samsung effect wrapper and waits
  for internal `ImageViewBlended` children to have nonzero layout before sending
  synthetic touches. If this still fails, treat the bundled Samsung renderer as
  incompatible with this overlay/runtime and continue with a faithful
  reimplementation from the reversed smali timings/assets.
