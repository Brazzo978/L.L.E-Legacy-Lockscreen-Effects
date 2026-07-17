# Geometric Mosaic mask-distance scale audit

Date: 2026-07-16

## Result

The two Geometric Mosaic fields at scene offsets `+0xdc` and `+0xe0` are not
free tuning parameters. They are aspect-ratio correction factors inherited from
the common scene base and initialized from the active render width `W` and
height `H`:

```text
scaleX = max(1.0, W / H)
scaleY = max(1.0, H / W)
```

Equivalently:

```text
portrait (W < H):  scaleX = 1.0, scaleY = H / W
landscape (W >= H): scaleX = W / H, scaleY = 1.0
```

For valid positive Android surface dimensions this result is exact, with high
confidence backed by both the common JNI instruction stream and the effect
instruction stream. No visual fitting was used.

The current ARM64 reconstruction must recompute these values whenever the
surface dimensions change. A neutral `1/1` pair is wrong on a non-square
display. The previously considered portrait pair `W/H, 1` is also wrong: it
makes the effect use the long side as its radius reference, enlarging the touch
footprint by `H/W`.

## Binaries and address convention

The evidence comes from the exact local ARM32 pair used by the legacy engine:

| Binary | SHA-256 | Relevant role |
|---|---|---|
| `native-libs/lib/armeabi-v7a/libsecveSrkCommon.so` | `5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36` | JNI input normalization and base-scene initialization |
| `vendor/original-native/libsecveGeometricMosaic.so` | `A16F926D14396E2C78E50AE48089860BD9B5156FB77ECC99A3E4E7694FE06DD8` | Geometric scene initialization and mask evaluator |

`libsecveGeometricMosaic.so` has a direct `DT_NEEDED` dependency on
`libsecveSrkCommon.so`, so the shared object layout and initialization path are
not inferred from an unrelated effect.

The existing Ghidra projects were imported with a `+0x10000` image-base offset.
This document therefore reports both forms:

```text
Ghidra VA = ELF/raw VA + 0x10000
```

"Raw" below means the virtual address found in the ELF and used by Capstone,
not a byte offset into the file.

## Exact common-JNI initialization

The exported function is:

```text
Java_com_samsung_android_visualeffect_lock_common_Native_init
raw ELF VA: 0x0000f5e8
Ghidra VA:   0x0001f5e8
symbol size: 2448 bytes
```

Its arguments include the requested surface width and height. The relevant
decompile is preserved in
`ports/abstract-tiles/research-agent/GHIDRA-COMMON-JNI-DECOMPILE.txt`.
Immediately before the scale calculation it establishes:

```text
scene +0x20 = W as integer
scene +0x24 = H as integer
scene +0x28 = float(W)
scene +0x2c = float(H)
scene +0x30 = 1/W
scene +0x34 = 1/H
scene +0x38 = W/H
```

The decisive raw instructions are:

```text
0x0000f92c  vdiv.f32    s5, s17, s18       ; s5 = W/H
0x0000f93c  vcmpe.f32   s5, s16            ; compare W/H with 1.0
0x0000f940  vstr        s5, [r4,#0x38]     ; base aspect = W/H
0x0000f948  vdivmi.f32  s17, s18, s17      ; portrait: s17 = H/W
0x0000f94c  vmovmi      r2, s16            ; portrait: r2 = 1.0
0x0000f950  movlt       r7, #0             ; portrait flag
0x0000f954  movge       r7, #1             ; landscape/square flag
0x0000f958  vmovpl      r2, s5             ; landscape: r2 = W/H
0x0000f960  str         r2, [r4,#0x3c]     ; base scaleX
0x0000f964  vmovpl.f32  s17, s16           ; landscape: s17 = 1.0
0x0000f968  vstr        s17,[r4,#0x40]     ; base scaleY
```

Here `r4` is the scene pointer, `s17=float(W)`, `s18=float(H)`, and
`s16=1.0`. The conditional VFP instructions make the two orientation branches
unambiguous. The Ghidra decompiler's `extraout_r2` temporary is only a register
recovery artifact; the raw `vmovmi`/`vmovpl` instructions above define `r2` on
both branches.

After these stores, `Native_init` invokes the scene initialization through its
vtable:

```text
0x0000f96c  load vfunc +0x60 and call it
0x0000f984  portrait:  load vfunc +0x14
0x0000f988  landscape: load vfunc +0x18
0x0000f98c  call selected orientation initializer
```

For the Geometric scene, vtable `+0x14` is raw `0x17014`. Vtable `+0x18` is
raw `0x01c54`, a trampoline which invokes `+0x14`, so both orientations reach
the same Geometric initializer after the common fields have been computed.

## Exact Geometric copy into `+0xdc/+0xe0`

`createScene` is exported at raw `0x157dc` / Ghidra `0x257dc`. It allocates a
`0x978`-byte scene and calls the base constructor at raw `0x15040` / Ghidra
`0x25040`. That constructor initially clears `+0x3c`, `+0x40`, `+0xdc`, and
`+0xe0`; those zeroes are constructor defaults, not the runtime distance
scales. `Native_init` overwrites the base pair before dispatching the virtual
initializer.

The Geometric orientation initializer is raw `0x17014` / Ghidra `0x27014`.
Near the end of its setup, the exact copy is:

```text
0x000174f4  ldr  lr, [r8,#0x3c]    ; common/base scaleX
0x0001752c  str  lr, [r8,#0xdc]    ; Geometric mask scaleX
0x0001754c  ldr  fp, [r8,#0x40]    ; common/base scaleY
0x00017554  str  fp, [r8,#0xe0]    ; Geometric mask scaleY
```

The corresponding Ghidra addresses are `0x274f4`, `0x2752c`, `0x2754c`, and
`0x27554`.

This closes the complete chain:

```text
Native_init(W,H)
  -> base +0x3c/+0x40 = short-side aspect correction
  -> virtual Geometric initializer
  -> Geometric +0xdc/+0xe0 = base +0x3c/+0x40
  -> mask distance evaluation
```

## Use in the mask evaluator

The evaluator at raw `0x06084` / Ghidra `0x16084` computes, for each active
touch record:

```text
dx = (sampleClipX - touchClipX) * scene[+0xdc]
dy = (sampleClipY - touchClipY) * scene[+0xe0]
distance = sqrt(dx*dx + dy*dy)
coverage = 1.0 - distance/radius
```

The input convention is also code-backed:

1. Common `Native_onTouch` (raw `0x11fc0`, Ghidra `0x21fc0`) converts pixels to
   `u=x/W`, `v=(H-y)/H`.
2. Geometric touch handling (raw `0x0a54c`, Ghidra `0x1a54c`) stores
   `touchClipX=2u-1`, `touchClipY=2v-1`.

For portrait, a pixel displacement therefore becomes:

```text
dx = (2*deltaPixelsX/W) * 1
dy = (2*deltaPixelsY/H) * (H/W) = 2*deltaPixelsY/W
```

For landscape the same derivation uses `H`, the short side, for both axes.
Consequently a Geometric radius `R` means a physical pixel radius of:

```text
pixelRadius = R * min(W,H) / 2
```

This is the semantic meaning of `+0xdc/+0xe0`: make the radial mask circular in
physical pixels and define its size relative to the short side.

## Concrete S23 Ultra example

For the observed `1440 x 3088` surface:

```text
scaleX = 1.0
scaleY = 3088/1440 = 2.1444444...
```

At the initial native radius `0.3`, the exact physical radius is `216 px` and
the diameter is `432 px` on both axes.

Using the reversed pair `scaleX=W/H=0.466321..., scaleY=1` instead produces a
physical radius of about `463 px` and diameter of about `926 px`. It is still
roughly circular, but it is `2.1444x` too large. This matches the reported
"touch covers almost the whole screen" symptom and explains it without visual
tuning.

Using neutral `1/1` produces a `432 x 926 px` ellipse at radius `0.3`, which is
also not faithful.

## Port requirement

For every `initialize`/size-changing `resize`, the ARM64 pipeline should apply:

```java
float aspect = width / (float) height;
maskScaleX = Math.max(1.0f, aspect);
maskScaleY = Math.max(1.0f, 1.0f / aspect);
```

An equivalent branch that directly computes `height/(float)width` in portrait
avoids the reciprocal and matches the original operation order more closely.
The scale must use the actual render surface dimensions for the currently
active display; cached screenshot dimensions must not substitute for a resized
surface.

The scale formula itself has no remaining reverse-engineering gap. Any residual
footprint mismatch after applying it must come from radius timing/record state,
mask topology, or the host's coordinate mapping, not from `+0xdc/+0xe0`.
