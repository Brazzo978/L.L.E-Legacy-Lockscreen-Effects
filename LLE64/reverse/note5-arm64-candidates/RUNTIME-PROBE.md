# Note 5 ARM64 runtime probe

Date: 2026-07-14

Target device:

- Samsung Galaxy Z Fold7 (`SM-F966B`)
- Android 16 / API 36
- `primaryCpuAbi=arm64-v8a`, no secondary ABI

## Scope

This is a deliberately isolated compatibility probe. It is not evidence that the
effects are ready for distribution or that their native renderer is safe to use.

The first probe APK packaged the original AArch64 Note 5 libraries plus an
explicitly ABI-incomplete `libstlport.so` shim. That historical result only
established dynamic loading and Java native registration. The shim must not be
included in a normal LLE64 build.

The exact N920G AOJ4 firmware was subsequently supplied and contains the matching
Samsung AArch64 `libstlport.so` (`SHA-256
821B11D1EA2E1853D0DE0F547F9FE224100AAA53A500F69441765BB089615CCA`). New
Note 5 probes use that authentic runtime; they remain test-only because the
firmware binary's redistribution terms are separate from LLE64.

Class-load probe APK SHA-256:

`2AFC0624FE6E5CDED1EB3F298422E1E6A3BD8C736F476A24A37F39DF49ECA5D6`

Renderer probe APK SHA-256:

`61AADB3AD6449FF7CF9835BB6B3A0F9BC6BDA49F68C420EAA5235411D0F30E92`

## Result

Both original Samsung libraries passed all of the following inside a real ART app
process:

1. dependency resolution and `dlopen` through `System.loadLibrary`;
2. execution of `JNI_OnLoad`;
3. lookup of the matching Samsung Java class from `classes2.dex`;
4. `RegisterNatives` for all methods declared by that class.

Observed logcat:

```text
I LLE64NativeProbe: PASS class-load/JNI_OnLoad/RegisterNatives com.samsung.android.visualeffect.lock.colourdroplet.JniColourDropletRenderer
I LLE64NativeProbe: PASS class-load/JNI_OnLoad/RegisterNatives com.samsung.android.visualeffect.lock.sparklingbubbles.JniSparklingBubblesRenderer
```

No `AndroidRuntime` crash was recorded during this class-loading test.

## Renderer result

The authentic AOJ4 STLport was then used to construct both Samsung effect
wrappers on the Fold7. Both probes created an AArch64 Adreno EGL/GLES context,
ran `native_Init_JNI`, initialized the native physics renderer, accepted the
unlock-affordance command, and processed a synthetic down/move/up gesture.

Observed checkpoints:

```text
I LLE64NativeProbe: PASS Colour Droplet wrapper/native renderer constructed
I LLE64NativeProbe: PASS Colour Droplet warmup/affordance queued size=1080x2520
I LLE64NativeProbe: PASS Colour Droplet touch sequence queued
D ColourDropletRenderer_GL: FPS = 105
I LLE64NativeProbe: PASS Colour Droplet survived init/GLES/touch/reset window

I LLE64NativeProbe: PASS Sparkling Bubbles wrapper/native renderer constructed
I LLE64NativeProbe: PASS Sparkling Bubbles warmup/affordance queued size=1080x2520
I LLE64NativeProbe: PASS Sparkling Bubbles touch sequence queued
D SparklingBubblesRenderer_TV: FPS = 158
I LLE64NativeProbe: PASS Sparkling Bubbles survived init/GLES/touch/reset window
```

No native crash or `AndroidRuntime` failure occurred. The probe host currently
shows an opaque black background because it does not yet composite the effect
surface over the lockscreen/background source. This is a Java/view integration
issue, not an ARM64 loader or JNI failure.

The first renderer probe exposed an unbounded legacy `GLTextureView` teardown.
The test-only Samsung dex is now rebuilt reproducibly with bounded 2000 ms
`onPause`/`requestExitAndWait` paths (patched dex SHA-256
`B05638F3ADCAAB6664C68CC50A36F5E6AA6E97E53AA8A485972CF2DAC8620E42`).
The wrappers also use only the detach-triggered exit instead of issuing a second
explicit shutdown.

On the repeat device test, both effects completed reset, screen-off, native
`DeInit_PhysicsEngineJNI`, detach, and wrapper destruction. Neither path used the
timeout fallback:

```text
I LLE64NativeProbe: PASS Colour Droplet renderer destroyed cleanly
I LLE64NativeProbe: PASS Sparkling Bubbles renderer destroyed cleanly
```

## What this does not prove

- Transparency/background composition is not wired into this isolated activity.
- A long-duration render/stress test has not been run.
- Context loss and repeated activity recreation are not yet stress-tested; one
  complete renderer destruction cycle per effect passed.
- The firmware libraries remain proprietary test inputs and are not included in
  the normal LLE64 APK.

The next integration step is to connect the working renderers to LLE64's
background composition and lockscreen lifecycle, then run repeated recreation and
context-loss tests. The exact Water Ripple port can continue independently
because the original Water Ripple library does not depend on STLport.
