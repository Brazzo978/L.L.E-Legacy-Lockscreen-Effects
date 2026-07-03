# Report reverse S3 Ripple (1:1) - Samsung

## Scope verificato
- `C:\Users\Admin\Documents\New project\unlock-effects-test\extracted\s3_android_policy_deodex_smali\com\android\internal\policy\impl\keyguard\sec\RippleUnlockView.smali`
- `C:\Users\Admin\Documents\New project\unlock-effects-test\extracted\s3_android_policy_deodex_smali\com\android\internal\policy\impl\keyguard\sec\CircleUnlockRippleRenderer.smali`
- `C:\Users\Admin\Documents\New project\unlock-effects-test\extracted\s3_android_policy_deodex_smali\com\android\internal\policy\impl\keyguard\sec\JniWaterRippleRender.smali`
- `C:\Users\Admin\Documents\New project\unlock-effects-test\demo-apk\src\com\codex\s4unlockfx\SystemUiLegacyEffectView.java`
- `C:\Users\Admin\Documents\New project\unlock-effects-test\demo-apk\src\com\codex\s4unlockfx\OriginalSamsungEffectHost.java`
- `C:\Users\Admin\Documents\New project\unlock-effects-test\demo-apk\res\values\s3_ripple.xml`
- Assets in demo-apk:
  - `res/drawable-nodpi/s3_reflectionmap.jpg`
  - `res/drawable-nodpi/s3_keyguard_default_wallpaper.jpg`
  - `res/drawable-nodpi/s3_default_wallpaper.jpg`
  - `res/raw/s3_ripple_down.ogg`
  - `res/raw/s3_ripple_up.ogg`
  - `res/raw/s3_gravity_effect.ogg`
- Native:
  - `unlock-effects-test/extracted/s3_system_files/lib/libWaterRipple.so`
  - `.../lib/libsamsungeffect.so` (se presente)

## 1) Classi Java/smali e lifecycle
### Coinvolte
- `RippleUnlockView`
- `CircleUnlockRippleRenderer`
- `JniWaterRippleRender`
- `CircleUnlockRippleRenderer$DVFSHandlerForRipple`
- `CircleUnlockRippleRenderer$SoundPoolThread`
- `CircleUnlockRippleRenderer$1..$4` (runnables)
- Host legacy:
  - `SystemUiLegacyEffectView`
  - `OriginalSamsungEffectHost`

### Lifecycle core
- Host
  - `getInstance(context)` → `show()` → touch forwarding via `handleTouchEvent/onHostTouchEvent`
  - `showUnlockAffordance(startDelay, rect)`
  - `reset()` / `cleanUp()` / `destroyed()` / detach
- Renderer
  - `onSurfaceCreated` → `loadBitmapIfBitmapNull` → `onLoadBGTextures`/`onLoadWaterTextures` → init JNI
  - `onSurfaceChanged` → update viewport/proiezione/matrix e init GPU
  - `onDrawFrame` loop continuo, con `move()` + draw (`onDraw` / `onDrawGravity`)
  - `show()`, `reset()`, `destroyed()`, `cleanUp()`

## 2) Touch mapping esatto
- Input raw da overlay Android: `MotionEvent.getRawX()` / `getRawY()`.
- Multitouch:
  - se `mouseInputCount > 1` e ritorna 1 dito: reset counter + reimposta `downX/downY`
  - se `pointerCount > 1`: `mouseInputCount = 2` e uscita da processing ripple

### ACTION_DOWN
- `isTouched=true`, `isOrientationChanged=false`
- `mouseInputCount=0`, `downX/downY` inizializzati, `rippleDistance=0`
- `prevPressTime = SystemClock.uptimeMillis()`
- `setHoverEnable(false)`
- clamp `mBottomWaveReductionRate` minimo `0.94`
- `onTouch(..., ACTION_DOWN, ...)`
- `ripple(..., intensity*4, true)`
- suono down

### ACTION_MOVE
- aggiorna distanza cumulata (`rippleDistance`) con distanza euclidea dal punto precedente
- su soglia `rippleDistance > 150.0`:
  - `ripple(..., intensity*3, true)`
  - suono drag
  - schedule `startLongPressCheck2(view,glY,glX,20ms,0.3f)`
  - `rippleDistance=0`

### ACTION_UP
- calcola hold `now - prevPressTime`
- se `> 600ms` emette `ripple(..., *4, true)`
- suono up
- reset `mouseInputCount=0`, `isTouched=false`

### ACTION_CANCEL
- reset immediato: `mouseInputCount=0`, `isTouched=false`, `setHoverEnable(false)`

### Hover
- `ACTION_HOVER_MOVE` con soglia temporale e graduale:
  - dopo `RIPPLE_WAIT_TIME = 1600ms`
  - `Fresnel/specular/exponent` salgono verso max con incrementi (`hoverPlus_*`)
  - `ripple(..., mHoverIntensity, false)`

### Coordinate GL
- Landscape:
  - `glX = (rawX - width/2 - XRatioAdjustLandscape) * XRatioForLandscape / width`
  - `glY = -((height - rawY) - height/2) * YRatioForLandscape / height`
- Portrait:
  - `glX = (rawX - width/2 - XRatioAdjustPortrait) * XRatioForPortrait / width`
  - `glY = -((height - rawY) - height/2) * YRatioForPortrait / height`

### Gravity lane
- in mode `RIPPLE_LIGHT_WITH_GRAVITY` viene valutata la zona x:
  - soglia sinistra/destra `0.3f` e `0.7f` sul rapporto posizione X/width

## 3) JNI / Native functions e parametri
`JniWaterRippleRender` dichiara `System.loadLibrary("WaterRipple")`.
- `onInitSetting(int, int, boolean)`
- `onInitGPU()`
- `onInitGPUGravity()`
- `onLoadBGTextures()`
- `onLoadWaterTextures()`
- `onLoadGravityTextures()`
- `onFreeBGTextures()` / `onFreeWaterTextures()` / `onFreeGravityTextures()`
- `onTouch(int, int, int, float)`
- `ripple([FIIIIFFF)V`
- `move([F[FIIIIIIZFF)I`
- `onDraw([F[F[SIII[FIIIIFFFFFFFFFF)V`
- `onDrawGravity([F[F[SIII[FIIIIFFFFFFFFFFIFFFFFZF)V`
- `transferBGBitmap(Bitmap)`
- `transferWaterBitmap(Bitmap)`
- `transferGravityBitmap(Bitmap, Bitmap, Bitmap)`
- `clearInkValue()`
- `getClearInkValue()`

## 4) Eventi unlock / clear / reset / pause / resume
- Unlock decision lato host:
  - `distance > 150dp OR dy < -120dp`
  - se superato: unlock callback + suono unlock
- Clear/Reset:
  - `reset()` (stato renderer/suoni/flags)
  - `clearInkValue()` e path clear effect
- Lifecycle UI:
  - `show()`, `showUnlockAffordance()`, `onDetachedFromWindow()`, `destroyed()`, `cleanUp()`
- Pause/Resume sono gestiti tramite detach/reattach e stati show/reset del host/render, non come API esplicita dedicata.

## 5) Timing audio + animazione
- Valori noti:
  - long hold up threshold: `600ms`
  - hover start delay: `1600ms`
  - long press check: `20ms` + soglia `0.3f`
  - drag trigger distance: `150.0f`
- Audio:
  - `soundNum = 5`
  - `soundTime = 10`
  - suoni: `s3_ripple_down.ogg`, `s3_ripple_up.ogg`, `s3_gravity_effect.ogg`

## 6) Pipeline grafica
- Textures/bitmaps in host: `bitmapBG`, `bitmapWater`, `bitmapGravity`, `bitmapCaustics`, `bitmapCaustics2`.
- Upload ordine tipico:
  - `transferBGBitmap(...)`
  - opzionale `transferWaterBitmap(...)`
  - gravity: `transferGravityBitmap(...)`
- Render path:
  - `onSurfaceChanged` imposta viewport, matrice `model/view/projection` e `world/view/proj/wvp`
  - `onInitSetting(w,h,isInkMode)`
  - `onInitGPU` o `onInitGPUGravity` in base mode
  - `onDrawFrame` dispatch su `onDraw` / `onDrawGravity`, poi `move`

## 7) Costanti numeriche importanti
- Riduzione:
  - `REDUCTION_RATE_NORMAL = 0.94`
  - `REDUCTION_RATE_RAIN = 0.96`
  - `REDUCTION_RATE_WAVE = 0.94`
  - `REDUCTION_RATE_WAVE2 = 0.99`
- Light:
  - `TOUCH_FRESENL = 0.1`
  - `TOUCH_SPECULAR = 0.5`
  - `TOUCH_EXPONENT = 20`
  - Hover max:
    - `HOVER_FRESENL_MAX = 1.0`
    - `HOVER_SPECULAR_RATIO_MAX = 10.0`
    - `HOVER_EXPONENT_RATIO_MAX = 20.0`
    - `HOVER_INTENSITY_MAX = 0.025`
  - hover step: `0.01`, `0.1`, `0.1`
- Fisica/parametri:
  - `mBottomWaveReductionRate` minimo `0.94`
  - `mWaveVelocity = 0.5`
  - `mWaveReduction = 0.94`
  - `mLightHeight = 1.5`
  - `refractiveIndex = 0.93`
  - `reflectionRatio = 0.13`
  - `intensityForLandscape = 0.5`
  - `intensityForPortrait = 1.0`
  - ratio/translate:
    - `XRatioForLandscape=45`, `YRatioForLandscape=25`
    - `XRatioForPortrait=25`, `YRatioForPortrait=46`
    - `translateZForLandscape=-23.0`, `translateZForPortrait=-44.0`
    - span landscape `16/16`, portrait `16/1`

## 8) Decompilazione Ghidra libWaterRipple.so
- Call-order osservato (entry principali, indirizzi stimati dal materiale già disponibile):
  - `onInitSetting` ~`0x9da0`
  - `onInitGPU` ~`0x9e08`
  - `onInitGPUGravity` ~`0x9e28`
  - `onLoadBGTextures` ~`0x9e38`
  - `onLoadWaterTextures` ~`0x9e84`
  - `onLoadGravityTextures` ~`0x9ed0`
  - `onFreeBGTextures` ~`0x9f70`
  - `onFreeWaterTextures` ~`0x9fbc`
  - `onFreeGravityTextures` ~`0x9fe0`
  - `onDraw` ~`0x9fb0`
  - `onDrawGravity` ~`0xa6b8`
  - `onTouch` ~`0xa8d0`
  - `move` ~`0xbc04`
  - `ripple` ~`0xbfe4`
- Funzioni di alto livello C++ inferite:
  1. init setting/textures
  2. draw frame
  3. touch/ripple input
  4. move integration
  5. texture free/reset

## 9) Conclusione pratica (ricostruzione 1:1)
Per una porting “1:1 app-owned” servono tre blocchi identici:
1. **state machine** e mapping input Android come smali originale
2. **pipeline grafica** (matrici, texture set, timing, suoni, unlock heuristics)
3. **kernel di ripple nativo** (moto dell’onda / shading / clearing / refraction/reflection)

Il terzo punto richiede decompilazione completa `libWaterRipple.so` con Ghidra/angolo C++: dai soli smali si ricostruisce solo la superficie Java/JNI e il comportamento osservabile, non il kernel interno bit-identico.
