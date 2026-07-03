# S3 Ripple Java/Smali Parameters - 2026-07-03

Source: pasted reverse report from the real S3 `libWaterRipple.so` / smali analysis.

Aliases:

- `CRR` = `unlock-effects-test\extracted\s3_android_policy_deodex_smali\com\android\internal\policy\impl\keyguard\sec\CircleUnlockRippleRenderer.smali`
- `RUV` = `unlock-effects-test\extracted\s3_android_policy_deodex_smali\com\android\internal\policy\impl\keyguard\sec\RippleUnlockView.smali`
- `JNI` = `unlock-effects-test\extracted\s3_android_policy_smali\com\android\internal\policy\impl\keyguard\sec\JniWaterRippleRender.smali`

## Parameter Table

| Parameter | Exact value | File/line | Usage context |
| --- | --- | --- | --- |
| Renderer sound resources | down `0x1100001`, up `0x110003c` | `RUV:188-194` | `RippleUnlockView` sets ripple sound resource IDs on renderer |
| GLSurfaceView render mode initial | `0` / `RENDERMODE_WHEN_DIRTY` | `RUV:220-225` | renderer installed, render-on-demand initially |
| Touch delegate | `handleTouchEvent -> renderer.mouseMove(view,event)` | `RUV:420-456` | all touch behavior lives in `CircleUnlockRippleRenderer.mouseMove` |
| JNI native ripple API | `ripple([FIIIIFFF)V` | `JNI:82` | velocity, mesh sizes, detail counts, x/y/intensity |
| JNI native move API | `move([F[FIIIIIIZFF)I` | `JNI:43` | velocity/height simulation update |
| JNI native draw API | `onDraw([F[F[SIII[FIIIIFFFFFFFFFF)V` | `JNI:46` | normal ripple draw with optical params |
| Base wave velocity | `0.5f` | `CRR:603-606` | normal wave velocity / native move velocity |
| Reduction/damping normal | `0.94f` | `CRR:611-614` | `REDUCTION_RATE_NORMAL` |
| Reduction/damping rain | `0.96f` | `CRR:616-619` | `REDUCTION_RATE_RAIN` |
| Reduction/damping wave | `0.94f` | `CRR:621-624` | `REDUCTION_RATE_WAVE` |
| Reduction/damping wave2 | `1.0f` | `CRR:626-627` | `REDUCTION_RATE_WAVE2` |
| Active damping default | `mReductionRate = 0.94f` | `CRR:731-734` | passed into normal `JniWaterRippleRender.move` |
| Sub damping default | `mReductionRateSub = 0.99f` | `CRR:736-739` | secondary wave state |
| Bottom wave velocity | `0.3f` | `CRR:1098-1101` | gravity/bottom-wave move velocity |
| Bottom wave damping | `0.94f` | `CRR:1108-1111` | gravity/bottom-wave reduction |
| Touch Fresnel | `0.1f` | `CRR:632-650` | initial `mFresnelRatio` |
| Touch specular | `0.5f` | `CRR:637-655` | initial `mSpecularRatio` |
| Touch exponent | `20.0f` | `CRR:642-660` | initial `mExponentRatio` |
| Hover Fresnel max | `1.0f` | `CRR:677-678` | hover clamp |
| Hover specular max | `10.0f` | `CRR:680-683` | hover clamp |
| Hover exponent max | `20.0f` | `CRR:685-688` | hover clamp |
| Hover intensity | `0.025f` | `CRR:690-713` | hover ripple intensity |
| Hover increments | Fresnel `0.01f`, specular `0.1f`, exponent `0.1f` | `CRR:695-708` | added each hover move until clamped |
| Hover wait time | `0x640 = 1600 ms` | `CRR:723-726`, `CRR:7403-7417` | hover ripple enabled only after wait since last touch ripple |
| Refractive index | `0.93f` | `CRR:751-754` | passed to native draw |
| Reflection ratio default | `0.13f` | `CRR:756-759` | passed to native draw |
| Alpha ratios | `alphaRatio1 = 1.0f`, `alphaRatio2 = 1.0f` | `CRR:761-765` | passed to native draw |
| Light height | `1.5f` | `CRR:746-749` | lighting/water optical setup |
| Default portrait translate Z | `-44.0f` constructor default; model config uses `-43.05f` | `CRR:847-855`, `CRR:4680-4688` | projection/world translate |
| Default landscape translate Z | `-23.0f` constructor default; common model config uses `-23.8f` | `CRR:852-855`, `CRR:4685-4688` | projection/world translate |
| FOV | `45.0f` | `CRR:9932-9946` | custom `perspectiveM(proj, 45, ratio, 0.1, 500)` |
| Near/far planes | near `0.1f`, far `500.0f` | `CRR:9932-9946` | projection setup |
| View matrix | eye `(0,0,1)`, center `(0,0,0)`, up `(0,1,0)` | `CRR:9905-9929` | `Matrix.setLookAtM` |
| Perspective formula | `f = tan(0.5 * (PI - angle))`, matrix slots set manually | `CRR:3341-3465` | custom projection helper |
| Active screen size | landscape: width=max(window), height=min(window); portrait reversed | `CRR:9840-9885`, `CRR:10182-10223` | used for aspect and touch normalization |
| 720x1280 / 1280x720 grid | `NUM_DETAILS_WIDTH=104`, `NUM_DETAILS_HEIGHT=104`, mesh `50x50`, surface `100x100`, `VCOUNT=10000` | `CRR:4596-4656` | model config for 720p |
| 540x960 / 960x540 grid | same as 720p: `104`, mesh `50`, surface `100`, `VCOUNT=10000` | `CRR:4741-4802` | qHD branch |
| 800x1280 / 1280x800 grid | same as 720p: `104`, mesh `50`, surface `100`, `VCOUNT=10000` | `CRR:4876-4937` | 800x1280 branch |
| 480x800 / 800x480 grid | `NUM_DETAILS=74`, mesh `50x50`, surface `70x70`, `VCOUNT=4900` | `CRR:5013-5078` | WVGA branch |
| 1600x2560 / 2560x1600 grid | `NUM_DETAILS=74`, mesh `50x50`, surface `70x70`, `VCOUNT=4900` | `CRR:5154-5219` | tablet/high-res branch |
| Fallback grid | `NUM_DETAILS=104`, mesh `50x50`, surface `100x100`, `VCOUNT=10000` | `CRR:5326-5360` | default branch |
| Max buffer size | `NUM_DETAILS_WIDTH * NUM_DETAILS_HEIGHT` | `CRR:4728-4736` | height/velocity buffer length |
| Native init mesh args | `initWaters(vertices, indices, VCOUNT, MESH_SIZE_HEIGHT, MESH_SIZE_WIDTH, SURFACE_DETAILS_HEIGHT, SURFACE_DETAILS_WIDTH)` | `CRR:2342-2357` | mesh/index setup |
| Landscape intensity common | `0.35f` | `CRR:4658-4666`, `CRR:5362-5370` | assigned to `intensityForRipple` on landscape |
| Portrait intensity common | `0.5f` | `CRR:4663-4666`, `CRR:5367-5370` | assigned to `intensityForRipple` on portrait |
| 1600x2560 intensity | landscape `0.2f`, portrait `0.35f` | `CRR:5221-5229` | high-res/tablet branch |
| 720/qHD span | landscape `x=3,y=21`, portrait `x=21,y=3` | `CRR:4690-4700`, `CRR:4836-4846` | passed into native move imax/jmax |
| 480x800 span | landscape `x=2,y=14`, portrait `x=14,y=2` | `CRR:5112-5130` | smaller grid branch |
| X/Y ratios 720/qHD | landscape `45/25`, portrait `30/46` | `CRR:4702-4720`, `CRR:4848-4866` | screen touch to GL coordinate conversion |
| X/Y ratios 800x1280 | landscape `48/27`, portrait `30/46` | `CRR:4985-5003` | touch normalization |
| X/Y ratios 480x800 | landscape `45/25`, portrait `28/45` | `CRR:5132-5150` | touch normalization |
| X/Y adjust | `0.0f` | `CRR:4722-4726`, `CRR:5426-5430` | subtract before ratio scaling |
| Portrait touch mapping | `glX=(rawX-screenW/2-XAdjust)*XRatio/screenW`; `glY=-((screenH-rawY)-screenH/2)*YRatio/screenH` | `CRR:7532-7603` | maps `MotionEvent` raw coords to ripple coords |
| Landscape touch mapping | same shape with landscape ratios | `CRR:7044-7114` | maps raw coords to ripple coords |
| ACTION_DOWN behavior | disable hover, mark touched, store downX/downY, reset distance/time, ripple at `intensityForRipple*4`, play down sound | `CRR:7189-7352` | first touch burst |
| ACTION_MOVE threshold | accumulate Euclidean distance; threshold default `150.0` | `CRR:7642-7731`, `CRR:914-917` | drag ripple gate |
| ACTION_MOVE ripple | when threshold crossed: reset distance, ripple at `intensityForRipple*3`, play drag/up sound id `1`, schedule long press check after `20 ms` with intensity `3.0f` | `CRR:7733-7776` | drag trail ripple |
| ACTION_UP behavior | if press duration `>600 ms`, ripple at `intensityForRipple*4`, play down sound id `0` | `CRR:7793-7846` | long press release burst |
| ACTION_CANCEL behavior | clears `mouseInputCount` | `CRR:7850-7866` | cancel cleanup |
| Multitouch behavior | pointer count `>1` sets `mouseInputCount=2` and returns false | `CRR:6996-7013` | ignores multitouch |
| Ripple call side effect | touch ripple stores `mPreviousRippleTime = uptimeMillis()` | `CRR:3742-3758` | suppresses hover until wait expires |
| Native ripple args | `ripple(velocity, MESH_SIZE_WIDTH, MESH_SIZE_HEIGHT, NUM_DETAILS_WIDTH, NUM_DETAILS_HEIGHT, mx, my, intensity)` | `CRR:3813-3830` | actual native disturbance insertion |
| Native move args normal | `move(velocity, heights, xSpan, ySpan, imax, jmax, NUM_DETAILS_WIDTH, NUM_DETAILS_HEIGHT, true, mReductionRate, 0.5f)` | `CRR:2680-2704` | per-frame wave update |
| Native draw optical args | includes `refractiveIndex`, `reflectionRatio`, `alphaRatio1/2`, `mFresnelRatio`, `mSpecularRatio`, `mExponentRatio` | `CRR:8412-8500` | final renderer params |
| SoundPool config | max streams `10`, stream type `1`, src quality `0` | `CRR:5624-5671` | normal sound pool |
| Sound IDs | `SOUND_ID_DOWN=0`, `SOUND_ID_UP=1`, gravity `0` | `CRR:957-970` | indexes into sounds array |
| Sound defaults | `soundNum=5`, `soundTime=10` | `CRR:947-955` | drag sound timing/thread state |
| Normal sound play args | left `1.0`, right `1.0`, priority `0`, loop `0`, rate `1.0` | `CRR:3523-3554` | `playSound` |
| Gravity sound resource | `0x1100004` | `CRR:5770-5778` | gravity sound load |

## Key Normal-Phone Values To Port First

- Grid: `104x104`
- Mesh: `50x50`
- Surface: `100x100`
- Damping: `0.94`
- Wave velocity: `0.5`
- Default drag threshold: `150 px`
- Down/up ripple intensity multiplier: `4x`
- Move multiplier: `3x`
- Projection: FOV `45`, near/far `0.1/500`
- Portrait Z: `-43.05`
- Landscape Z: usually `-23.8`

## Important Porting Notes

- `ripple()` inserts energy into `velocity`, not directly into `height`.
- Native `move()` receives `velocity`, `heights`, update spans, detail dimensions, `checkEmpty=true`, damping, and wave velocity.
- The exact visual still depends on native draw/compositing (`onDraw`) constants and shader-like behavior, so this table should be combined with the native render extraction before claiming exact parity.
