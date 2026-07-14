# Note 5 firmware audit for the ARM64 effect blobs

## Conclusion

`N920GUBU2AOJ4_N920GUWA2AOJ4_N920GUBU2AOJ3_HOME.tar.md5` is the exact and preferred firmware for this work. Its `system.img` contains the AArch64 `libstlport.so` needed by the two effect blobs, and the Colour Droplet and Sparkling Bubbles libraries extracted from that same image are byte-for-byte identical to the candidates already under `LLE64/reference/arm64-candidates/note5-aoj4`.

The BQAB and CVG1 firmware files are valid later Note 5 generations, but they are not ABI provenance matches for the AOJ4 blobs and their system images do not contain `lib64/libstlport.so`.

## Firmware comparison

| Archive | Model/build from `build.prop` | Android | System contents relevant here | Suitability |
| --- | --- | --- | --- | --- |
| `N920GUBU2AOJ4_N920GUWA2AOJ4_N920GUBU2AOJ3_HOME.tar.md5` | SM-N920G, `N920GUBU2AOJ4`, fingerprint `samsung/noblelteub/noblelte:5.1.1/LMY47X/N920GUBU2AOJ4:user/release-keys`, changelist 5962091 | 5.1.1 / API 22 | ARM64 STLport plus both exact effect blobs | **Exact source; use this one** |
| `N920CXXS3BQAB_N920COLB3BPJ1_N920CXXU3BQA1_HOME.tar.md5` | SM-N920C, `N920CXXS3BQAB`, fingerprint `samsung/nobleltejv/noblelte:6.0.1/MMB29K/N920CXXS3BQAB:user/release-keys`, changelist 8848699 | 6.0.1 / API 23 | No `lib64/libstlport.so`; no matching effect blobs at the standard paths | Later/different model build; not preferred |
| `AP_N920GDDU5CVG1_CL11762721_QB54496127_REV00_user_low_ship_meta.tar.md5` | SM-N920G, `N920GDDU5CVG1`, fingerprint `samsung/nobleltedd/noblelte:7.0/NRD90M/N920GDDU5CVG1:user/release-keys`, changelist 11762721 | 7.0 / API 24 | No `lib64/libstlport.so`; no matching effect blobs at the standard paths | Much later AP-only build; not preferred |

The AOJ4 HOME archive contains `sboot.bin`, `cm.bin`, `boot.img`, `recovery.img`, `system.img`, `cache.img`, `hidden.img`, and `modem.bin`. BQAB contains the same broad HOME set. CVG1 is an AP package containing `boot.img`, `recovery.img`, `system.img`, `userdata.img`, and FOTA metadata.

## Authentic AOJ4 ARM64 STLport

Source inside the sparse ext4 image:

```text
system.img:/lib64/libstlport.so
```

Extracted read-only audit copy:

```text
/tmp/lle64-firmware-audit/aoj4/extracted/libstlport.so
\\wsl$\Debian\tmp\lle64-firmware-audit\aoj4\extracted\libstlport.so
```

Properties:

- Size: 456,280 bytes
- SHA-256: `821b11d1ea2e1853d0de0f547f9fe224100aaa53a500f69441765bb089615cca`
- Format: ELF64, little-endian, AArch64, shared object, stripped
- SONAME: `libstlport.so`
- `DT_NEEDED`: `libc.so`, `libm.so`, `libstdc++.so`
- Binding: `BIND_NOW` / `DF_1_NOW`

All five imports previously identified in the Samsung effects are exported:

| Symbol | ELF type | Size |
| --- | --- | ---: |
| `_ZNSt12__node_alloc11_M_allocateERm` | FUNC | 4 |
| `_ZNSt12__node_alloc13_M_deallocateEPvm` | FUNC | 4 |
| `_ZNSt8ios_base16_M_throw_failureEv` | FUNC | 32 |
| `_ZSt24__stl_throw_length_errorPKc` | FUNC | 16 |
| `_ZSt4cout` | OBJECT | 152 |

This is materially safer than the probe shim: `_ZSt4cout` is a real initialized STLport object, and allocator/exception support comes from the exact runtime shipped alongside the effects.

## Exact blob provenance

The libraries extracted from the AOJ4 `system.img` are:

| File | Size | SHA-256 | Comparison with LLE64 candidate |
| --- | ---: | --- | --- |
| `/lib64/libColourDropletEffect.so` | 517,896 | `634dc703ff9288a4961b3e636b83dd89ddbf86df6087d624dc19b4231e6c010c` | Identical |
| `/lib64/libSparklingBubblesEffect.so` | 435,976 | `f96e287cd20b411a863d07d012631fa61761fc35aec50d4b4a4b454577b2c944` | Identical |

This hash identity is the decisive reason to use the AOJ4 STLport instead of borrowing one from BQAB/CVG1 or rebuilding AOSP first.

## Method and scope

The original `.tar.md5` files were not modified. Each `system.img` was extracted to `/tmp/lle64-firmware-audit`, converted from Android sparse format to a temporary raw ext4 image, and inspected with `debugfs`. The absence checks covered both `/lib64/libstlport.so` and `/vendor/lib64/libstlport.so`, plus the standard `/lib64` paths for the two effects.

This audit establishes source provenance and static ABI compatibility. It does not by itself prove that Android 16's linker namespace will accept all transitive dependencies or that complete effect construction/render/destruction is safe; those remain runtime gates on the Fold.
