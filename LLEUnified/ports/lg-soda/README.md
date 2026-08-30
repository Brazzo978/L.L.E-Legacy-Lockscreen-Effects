# LG Soda restoration

## Source and authorization

The restoration source is the archived XLocker theme package
`com.optimusdev.soda-v1.1.apk`, supplied by Erik (XLocker/OptimusDev) with
authorization to use the archived effect material in L.L.E.

- Source APK SHA-256: `2906BCEFF3EB164D841B6F91FB2FDF3B35EC067D0109191D3C544001EE4F2CF1`
- Original implementation: Java + OpenGL ES 2.0; no native `.so` payload.
- L.L.E integration: dedicated pre-lock underlay, tester-only ARM64 effect.

## Imported effect assets

The 14 `sodaparticle_*` sprites are imported under the `lg_soda_*` resource
prefix. Their original dimensions and pixels are retained; the Android-era
indexed PNG encoding is normalized to RGBA for reliable decoding on current
devices. Original source hashes:

| Original asset | SHA-256 |
| --- | --- |
| `sodaparticle_big_01.png` | `6E8ED018BA6F6225838A4586300944A5307877053AAB9783FED7314213499BE3` |
| `sodaparticle_big_02.png` | `E49DE1C52CFE7DD835948B7B09A0AFB33FBBB66D5B0BB115D52E4FED63AAEC55` |
| `sodaparticle_big_03.png` | `A5F169EEFBDF7F66BE1FE7107ACEFB3C99363030573ED29D681C03278839B161` |
| `sodaparticle_big_06.png` | `C2BF0A489A960120914C91951C889D73680096A0A5F72812A23D4274055DC98A` |
| `sodaparticle_big_11.png` | `1666285554C148F39754DFC2262EEF12AA928772A1CF0D6B71DDB29DB5077064` |
| `sodaparticle_big_12.png` | `F1334AC35B4F43829715D1D8014ED8ACFD04C254E1AB80ECDEDFEDFF56EE5D81` |
| `sodaparticle_big_14.png` | `B73F2CAF58AF449F1FCEF10F929502E20468145A6029E7B4C23A35DE568B2DBD` |
| `sodaparticle_big_16.png` | `DB9EC1A4B25DA7265A368B22E051FAC2DF42AA7DA4B47D05D05B2A1BA65AAAD6` |
| `sodaparticle_small_01.png` | `DF638A6D28D324CAB07F7AE2B8C0128D80D6701F2AC64B435CA107293109CFD3` |
| `sodaparticle_small_02.png` | `5205747DB65993C009B2488B968DBB0925F81FC01115A6678471C67AB9FC04A5` |
| `sodaparticle_small_03.png` | `6A1A80B1535BEB92771516958D8FA5BA798EFF576A04D481957A05042607B990` |
| `sodaparticle_small_04.png` | `9700271EAB460BBF0FC77BD7E08CBD43E4CE5E72469AE181D81319E26D151C7D` |
| `sodaparticle_small_05.png` | `C3266AD5EC2FA6FFDF0E226A26A7953082584061EF8996EAF8C87B0AB4820AA9` |
| `sodaparticle_small_06.png` | `6A508FF87280E498CBAA49233ECE14C30D36B57A59BCDD8469C8B7D38B510D59` |

Original audio is imported as `lg_soda_lock.ogg`, `lg_soda_touchdown.ogg`, and
`lg_soda_unlock.ogg`:

| Original asset | SHA-256 |
| --- | --- |
| `soda_lock.ogg` | `3AE6A892627B4A98A9C1648B7450DAF2BDC139B32EF75F424474BA346BD2AECB` |
| `soda_touchdown.ogg` | `AF23E0267364AD57B7326DF90553245933C6C4392FAF80814D2D6B40BFD7184D` |
| `soda_unlock.ogg` | `DD066BED5768145F5F0C44D08E2966E6D94BB14332C738E80B0A6FE53196D1AB` |
