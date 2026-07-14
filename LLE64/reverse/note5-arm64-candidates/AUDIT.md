# Note 5 AOJ4 AArch64 candidate audit

Scope: static, read-only audit of the two binaries under
`reference/arm64-candidates/note5-aoj4`. No binary, app source, port, package,
or Ghidra project was modified. Evidence was collected with the NDK r27d
`llvm-readelf`/`llvm-objdump`, Android `dexdump`, and string/relocation tables.

## Binary identity

| Library | Size | SHA-256 | ELF |
|---|---:|---|---|
| `libColourDropletEffect.so` | 517,896 | `634DC703FF9288A4961B3E636B83DD89DDBF86DF6087D624DC19B4231E6C010C` | ELF64, AArch64, DYN |
| `libSparklingBubblesEffect.so` | 435,976 | `F96E287CD20B411A863D07D012631FA61761FC35AEC50D4B4A4B454577B2C944` | ELF64, AArch64, DYN |

Their SONAMEs match their filenames. Both export `JNI_OnLoad`; neither exports
static `Java_*` entry points.

## `DT_NEEDED`

The dependency list is identical in both candidates:

- `libEGL.so`
- `libGLESv2.so`
- `libandroid.so`
- `libc.so`
- `libjnigraphics.so`
- `liblog.so`
- `libm.so`
- `libstdc++.so`
- `libstlport.so`
- `libz.so`

The Android/GL dependencies are ordinary platform libraries for this vintage
of binary. The blocking dependency is `libstlport.so`; `libstdc++.so` must also
be verified on the target system because these libraries name it directly.

## Dynamic JNI registration

Disassembly of both `JNI_OnLoad` functions confirms:

- request/return of `JNI_VERSION_1_6` (`0x00010006`);
- `FindClass` of one effect-specific Samsung renderer class;
- `RegisterNatives(..., count = 13)` using the 13-entry relocation-backed
  `JNINativeMethod` table in `.data`.

Class targets:

- Colour Droplet:
  `com/samsung/android/visualeffect/lock/colourdroplet/JniColourDropletRenderer`
- Sparkling Bubbles:
  `com/samsung/android/visualeffect/lock/sparklingbubbles/JniSparklingBubblesRenderer`

Both register the same method surface:

| Registered name | JNI signature |
|---|---|
| `native_Init_JNI` | `()J` |
| `native_DeInit_JNI` | `(J)V` |
| `native_Init_PhysicsEngine` | `(JIIII)V` |
| `native_onSurfaceChangedEvent` | `(JII)V` |
| `native_Draw_PhysicsEngine` | `(J)V` |
| `native_onTouchEvent` | `(JIII[I[I)V` |
| `native_onSensorEvent` | `(JIFFF)V` |
| `native_onKeyEvent` | `(JI)V` |
| `native_SetTexture` | `(JLjava/lang/String;Landroid/graphics/Bitmap;)V` |
| `native_SetTextureColor` | `(JLjava/lang/String;Landroid/graphics/Bitmap;)V` |
| `native_onCustomEvent` | `(JIF)V` |
| `native_onCustomEventVec` | `(JIFFF)V` |
| `native_isEmpty` | `(J)I` |

The function-pointer relocations map all 13 entries to the corresponding
`PhysicsEngineJNI` implementations. The two candidates have the same JNI ABI,
but each registers only against its own class name.

## Match against the existing Samsung dex

`vendor/secvisualeffect/classes.dex` contains both exact class descriptors.
`dexdump` confirms all 13 private static native declarations and signatures
match the native registration tables exactly. Their static initializers also
load the expected SONAME stem:

- `JniColourDropletRenderer` calls
  `System.loadLibrary("ColourDropletEffect")`;
- `JniSparklingBubblesRenderer` calls
  `System.loadLibrary("SparklingBubblesEffect")`.

The current LLE64 build copies that dex into the APK as `classes2.dex`.
Therefore **the Java/JNI class and signature layer is already present and
compatible with these candidates**. The app-owned effect views reach the same
Samsung effect framework through `EffectView`; no replacement Java JNI bridge
is indicated by this audit.

## Unresolved C++/STL symbols

Besides platform C/GL/JNI symbols and the usual allocation/runtime entry
points, both candidates import the same five STLport-specific ABI symbols:

| Raw symbol | Demangled role |
|---|---|
| `_ZNSt12__node_alloc11_M_allocateERm` | `std::__node_alloc::_M_allocate(unsigned long&)` |
| `_ZNSt12__node_alloc13_M_deallocateEPvm` | `std::__node_alloc::_M_deallocate(void*, unsigned long)` |
| `_ZNSt8ios_base16_M_throw_failureEv` | `std::ios_base::_M_throw_failure()` |
| `_ZSt24__stl_throw_length_errorPKc` | `std::__stl_throw_length_error(char const*)` |
| `_ZSt4cout` | `std::cout` object |

The remaining C++ runtime imports are `operator new/new[]`,
`operator delete/delete[]`, `__cxa_atexit`, and `__cxa_finalize`; these are the
kind of symbols historically supplied through the named `libstdc++.so` runtime.

The repository contains only two `libstlport.so` copies, and both are ELF32
`EM_ARM` (one under the LLE64 ARM32 reference tree and one in the older touch
app). No ELF64/AArch64 STLport was found. The ARM32 STLport confirms the same
legacy ABI families, with the expected 32-bit `unsigned int` mangling for
`__node_alloc`; it cannot be loaded into this 64-bit process.

## Shim feasibility and blocker

A narrow shim is superficially small by symbol count, but it is **not currently
a safe packaging solution**:

- the two node-allocation functions and two throw helpers could plausibly be
  recreated after their exact STLport semantics and exception ABI are fixed;
- `_ZSt4cout` is an exported C++ object, not a plain function. Its size, layout,
  initialization, vtables/facets, and lifetime must match the old AArch64
  STLport ABI. Aliasing it to libc++ or a modern GNU `std::cout` is not ABI-safe;
- the direct `DT_NEEDED` on `libstlport.so` must still be satisfied before JNI
  loading can begin, and `libstdc++.so` availability/exports must be checked on
  the actual target image.

Thus the Java/dex side is **CONFIRMED compatible**, while native runtime loading
is **BLOCKED** by the absent AArch64 STLport runtime (plus target verification of
the directly named `libstdc++.so`). The realistic next evidence step is to
obtain the matching Note 5 AArch64 `libstlport.so` from the same firmware, or
prove by call-site analysis/runtime tracing that `std::cout` is unreachable and
then design/test a tightly scoped compatibility library. This audit does not
justify packaging either candidate yet.
