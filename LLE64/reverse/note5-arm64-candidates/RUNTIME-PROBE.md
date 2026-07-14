# Note 5 ARM64 runtime probe

Date: 2026-07-14

Target device:

- Samsung Galaxy Z Fold7 (`SM-F966B`)
- Android 16 / API 36
- `primaryCpuAbi=arm64-v8a`, no secondary ABI

## Scope

This is a deliberately isolated compatibility probe. It is not evidence that the
effects are ready for distribution or that their native renderer is safe to use.

The probe APK packages the original AArch64 Note 5 libraries plus an explicitly
ABI-incomplete `libstlport.so` shim. The shim only provides enough symbols to test
dynamic loading and Java native registration. It must not be included in a normal
LLE64 build.

Probe APK SHA-256:

`2AFC0624FE6E5CDED1EB3F298422E1E6A3BD8C736F476A24A37F39DF49ECA5D6`

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

## What this does not prove

- No renderer object was constructed.
- No EGL/GLES surface was created.
- No effect frame was rendered.
- The shim's fake storage for the imported `std::cout` object is not an ABI-safe
  implementation of the original STLport runtime.
- Heap allocation, C++ stream use, exception paths, and destructor behavior remain
  unsafe or unverified.

The correct next step for these two effects is to obtain or reproducibly build an
authentic compatible AArch64 STLport runtime, then repeat initialization and render
tests. The exact Water Ripple port can continue independently because the original
Water Ripple library does not depend on STLport.
