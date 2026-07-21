# LSE unlock-effect identity and coverage audit

Date: 2026-07-19

## Scope

This audit covers the classic unlock renderers found in the local LSE material. Seasonal/festival effects and the charging companion are separate feature families and are not counted here.

There are two unrelated numeric namespaces in the Samsung/LSE sources:

- the lockscreen **setting ID**, used by the old lockscreen picker;
- the `EffectView` **renderer ID**, used by LSE's `secvisualeffect` host.

For example, Stone Skipping is lockscreen setting ID 7, while LSE renderer ID 7 means Brilliant Ring. The numbers must not be compared without naming their namespace.

## Findings

### Stone Skipping

Stone Skipping is Samsung's `MassRippleUnlockTwin`. The recovered firmware boundary now supports a **Galaxy S5** origin: Galaxy S4 `I9505XXUFNC4` (internal build 2014-03-08) is negative, while launch-branch Galaxy S5 `G900AUCU1ANCE` (2014-03-14) contains `MassRippleUnlockTwin`, `MassRippleImageView`, `MassTensionUnlockView`, and the public `simple_ripple = "Stone skipping"` resource. Note II releases through Android 4.3 are negative; its first KitKat build `N7100XXUFND4` (2014-04-18) is positive, making that copy a later backport. The first-generation Galaxy Tab S SM-T705 ANF8 resources are later still. See `STONE_SKIPPING_ORIGIN_AUDIT_2026-07-20.md` for the complete binary chronology.

The exact LSE smali copy is byte-identical to the Galaxy Note 4 SM-N910F BOB4 stock class captured from the connected device. The same implementation also appears in the later Galaxy Note 3 SM-N9008V DQD2 firmware:

- LSE copy: `unlock-effects-test/demo-apk/smali_s4_mass/com/android/keyguard/sec/MassRippleUnlockTwin.smali`
- Note 4 BOB4 copy: `unlock-effects-test/Note 4/N910F_device_lab/BOB4_stock_before_festival/priv-app/SystemUI/analysis/smali/com/android/keyguard/sec/MassRippleUnlockTwin.smali`
- later Note 3 copy: `unlock-effects-test/Note 4/N9008V_DQD2_extracted/SystemUI_smali/com/android/keyguard/sec/MassRippleUnlockTwin.smali`
- SHA-256 for all three: `B25660E918C45FDD195DDA229E7D1CEE007A3DAA0491D1235E709E3E125568D1`

The archived GalaxyLockscreenEffects picker names lockscreen setting ID 7 `Stone Skipping` in `unlock-effects-test/Note 4/FESTIVAL_PORT_ARCHIVE/04_BUILD_SOURCES/GalaxyLockscreenEffects/app/src/com/xpe/app/galaxy/lockscreen/effect/MainActivity.java:32-37`.

The renderer is small and Java-owned: six reusable ripple slots, white stroked circles, a 1300 ms decelerate-quadratic scale/fade, a second circle after 400 ms, and at most three moving circles. Its obsolete `DVFSHelper` calls are performance hints, not part of the image.

The LLE port preserves the visible and audible inputs:

| Stock property | Port value |
|---|---:|
| Main circle diameter | 290 dp |
| Screen-on affordance diameter | 224 dp |
| Stroke sequence | 49 / 26.6 / 37 / 30 dp |
| Animation duration | 1300 ms |
| Second ring delay | 400 ms |
| Movement ratio step | 0.45 |
| Maximum moving rings | 3 |
| Unlock delay | 901 ms stock; LLE shared path uses its matching 900 ms default |

The physical Note 4 also resolved an audio ambiguity. The stock class prefers `/system/media/audio/ui` over its packaged fallback. `stone_skipping_down.ogg` is identical in both locations; `stone_skipping_up.ogg` now uses the actual BOB4 runtime variant (48 kHz mono, SHA-256 `E40474452A9033B588ECB9801BDD34168B0D5CEA51F4CB31BDFE3F9CF00445FB`). The different LSE/SystemUI fallback encoding is preserved in the Stone Skipping reference archive.

### Brilliant Cut

The local evidence does not support a Tab S2 origin. Brilliant Cut is already present in the SM-T705 ANF8 firmware, which is the first-generation Galaxy Tab S 8.4:

- public label `Brilliant cut`: `unlock-effects-test/extracted/tab_t705_anf8_system_files/SecSettings_resources.txt:30695`
- `brilliantcut` enum: `unlock-effects-test/extracted/tab_t705_anf8_system_files/Keyguard_smali/com/android/keyguard/sec/KeyguardEffectViewMain$Background.smali:124`

It later appears in the recovered Note 4 material as a separate native renderer. It is not Geometric Mosaic and is not currently ported by LLE.

### RippleInk versus Ink in Water

The LSE label `Ink in water / Ripple ink` merges two names that Samsung kept separate:

- **RippleInk** (LSE renderer ID 8) is the normal water/refraction renderer using `libsecveRippleInk.so`.
- **Indigo Diffusion** (LSE renderer ID 9) is the effect publicly shown as **Ink in water** / `montblanc` on the Note 4, using `libsecveIndigoDiffusion.so`.

They share a renderer family and JNI shape, but they are different binaries and Indigo adds its coloured diffusion treatment. In LLE, S3 Water Ripple covers the ordinary RippleInk visual family, but it is not a literal port of that later LSE binary. Indigo Diffusion is now available as `N2 Ink in Water`; the historical label follows the Note II Ripple Ink lineage, while the implementation uses the later Indigo engine.

### Note 4 renderer binary identities

The recovered Note 4 BOB4 libraries are distinct:

| Renderer | Size | SHA-256 |
|---|---:|---|
| Brilliant Cut | 226,520 bytes | `46B7580078F373CD5129704B8294AD1B630665F27E6877A8ECB30A41BDF039C7` |
| Brilliant Ring | 62,680 bytes | `17F059922AFB2B15103EDAF817C7663890F99CDE9C153B55AC3E0CBAD27E3A79` |
| RippleInk | 79,184 bytes | `88991DE86A4BDE8E91CEE902F81A28D4F7794FDC70F774699748010C15A5CEBB` |
| Indigo Diffusion | 75,096 bytes | `D73EEB5479FFA585DE491C6FDCD32E36DB2E1AB1C80DD791AFE15780B30F10A4` |

## Classic LSE coverage after Stone Skipping

| LSE renderer/family | LLE ARM32 | LLE ARM64 | Audit note |
|---|---|---|---|
| Lens Flare | Available | Available | Covered |
| S3 Water Ripple | Available | Available | Covered |
| Popping Colours | Available | Available | Covered |
| Tab S Blind | Available | Available | Covered |
| Watercolor | Available | Available | Covered |
| RippleInk | Family covered by S3 Water Ripple | Family covered by S3 Water Ripple | Specific LSE renderer not literally ported |
| Indigo Diffusion / Ink in Water | Available | Available | Covered as `N2 Ink in Water` |
| Abstract Tiles | Available | Beta | Covered |
| Geometric Mosaic | Available | Beta | Covered |
| Brilliant Cut | Not available | Not available | Distinct remaining effect |
| Brilliant Ring | Not available | Not available | Distinct remaining effect |
| Colored Droplet | Available | Available | Covered |
| Sparkling Bubbles | Available | Available | Covered |
| S5 Stone Skipping / Mass Ripple | Available | Available | Added by this change |

## Coverage conclusion

Stone Skipping does **not** make LLE a literal 100% port of every classic renderer present in LSE.

- By user-visible visual family, the ordinary RippleInk can reasonably be counted as covered by Water Ripple.
- Two genuinely distinct effects still remain: **Brilliant Cut** and **Brilliant Ring**.
- If the goal is exact renderer-for-renderer parity rather than visual-family parity, the later LSE **RippleInk** renderer is a fourth remaining port.

The existing preview filename `preview_unlock_stoneskipping_s5` now agrees with the independently verified firmware provenance.

## Verification performed

- The shared build completed for both `armeabi-v7a` and `arm64-v8a`; both APKs passed v1, v2, and v3 signature verification.
- The final ARM64 APK was installed as an update on the connected Galaxy S23 Ultra (`SM-S918B`, package `com.codex.lle64`) without clearing app data.
- Accessibility remained bound and reported no crashed service.
- The selected renderer survived reinstall as effect type 13, reached the already-attached readiness state, and began a real lockscreen gesture with `syncMs=2` and `beginMs=2` in the captured run.
- A 400 px non-unlock swipe rendered the stock first, delayed twin, and moving white rings over the untouched lockscreen wallpaper; no screenshot or opaque background was introduced.
- ARM32 received build/signature coverage in this round. It was not installed on the S23, to avoid mixing the 32-bit and 64-bit test packages on the same device.
- Stock comparison was captured from a physical Galaxy Note 4 SM-N910F running BOB4 with `lockscreen_ripple_effect=7`. The screen-on twin, swipe propagation, transparency, stroke alternation, and 1300 ms lifetime match the recovered implementation. Captures and both audio encodings are archived under `ports/stone-skipping/reference/note4-n910f-bob4-20260719`.
