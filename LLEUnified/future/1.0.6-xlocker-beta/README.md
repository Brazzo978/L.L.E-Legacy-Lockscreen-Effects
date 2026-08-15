# LLE 1.0.6 XLocker research beta

## Release boundary

This directory is a future-work specification for an experimental `1.0.6-xlocker-beta` train. It is **not part of LLE 1.0.5.7**. Version 1.0.5.7 must not gain any of the experimental renderers, resources, audio, native binaries, models, or IDs described below.

The XLocker APKs are a secondary behavioural oracle only. They are not a source from which implementation code or assets may be copied. Samsung firmware remains the primary oracle for Samsung effects.

## Provenance and clean-room rules

- Public reference repository: <https://github.com/XLocker/SampleTheme>, audited at commit `68a5a3564e1e5daa4665213f8cf729cfd2a51cfe`.
- No `LICENSE`, `COPYING`, or `NOTICE` file was found in that repository, and the researched APKs provide no reuse licence. Absence of a licence means no permission to copy or redistribute their code or content.
- XLocker DEX, shader text, images, textures, audio, native libraries, models, and extracted resources remain research-only and must not enter LLE source, build inputs, APKs, or release archives.
- `z1-blinds.apk` contains shader text bearing Sony/SCE copyright and confidentiality notices. It must not be copied.
- `revolving-glass.apk` contains proprietary ARM32-only native libraries and a model. LLE remains ARM64-only: do not bundle, translate, or build those libraries.
- A future implementation must be independently authored from documented behaviour and independently licensed/OEM-authoritative material. Keep a provenance ledger for every resource.
- Do not describe IDs 32–36 as authentic, exact, stock, or 1:1 until they have been compared against an independent OEM oracle. ID 36 can only be presented as a clean-room, inspired beta unless an independent specification becomes available.

### Existing Lens Flare provenance issue

The currently tracked `keyguard_bluering_*` and `keyguard_blood_*` assets were found byte-identical to the corresponding XLocker `s4-lensflare.apk` variants. Their independent redistribution authority is not documented. Before 1.0.6, either document an independent OEM/licensed source, replace them with procedural app-owned artwork, or hide/remove those modes. This finding is specific to those Blue Ring/Blood assets; it is not permission to import any additional XLocker material.

## APK inventory and stable ID map

The six proposed IDs are reserved as follows. Keep them stable and set `EFFECT_COUNT` to `37` only when the implementation train actually begins.

| Research APK | SHA-256 | Proposed ID and constant | Relationship to LLE | Clean-room disposition |
|---|---|---|---|---|
| `s5-circle.apk` | `A8EBCA46E566289CE0F0CF1B518A508EF9F8F54A8F8D4557BE3783E2A83A41B5` | 31 — `EFFECT_S5_NONE` | New effect; UI label: **S5 Circle (None)** | Feasible OEM-backed Canvas implementation from the independently held Samsung firmware sources listed below. No colormap. |
| `g2-pixelate.apk` | `564183A65804EF26ACE815AE2C4F50BF17B3EC8D93D06389FFD741CD51BC18C2` | 32 — `EFFECT_LG_G2_PIXELATE` | New experimental effect | Feasible clean app-owned GLES renderer; colormap required. Do not copy XLocker shaders or assets. |
| `g2-particle.apk` | `34E405C182C9C1FBF762BC88079A68157AB9AA5D5BAAA8448167ADC4B6117D9F` | 33 — `EFFECT_LG_G2_PARTICLE` | New experimental effect; distinct from Popping Colours | Feasible clean app-owned GLES particles/hole renderer; colormap required for faithful colour sampling. A synthetic map is test-only and non-faithful. |
| `g2-crystal.apk` | `DC4DB36AE7496C5ADD1D7818646A34664FAC2D2CE9B68669562FE377202022BD` | 34 — `EFFECT_LG_G2_CRYSTAL` | New experimental effect | Procedural app-owned GLES inspired beta; colormap required for refraction. Do not copy the four XLocker textures. |
| `z1-blinds.apk` | `74725EA7205B12BBFFA147A5E9508DDB359365AA03A51625597757A1B40488B3` | 35 — `EFFECT_XPERIA_Z1_BLINDS` | New effect; distinct from ID 11 Tab S Blind | Feasible clean app-owned Canvas strip renderer; screenshot/colormap required. Do not copy Sony/XLocker shaders or assets. |
| `revolving-glass.apk` | `27F4111DAE4AFF7E3D5FACB2B279AF470CB3256D33B6E4493B9081BC30376292` | 36 — `EFFECT_REVOLVING_GLASS` | New experimental effect | Original port is blocked. Only a clearly labelled procedural clean-room/inspired renderer is eligible; colormap required. |

Three other researched APKs do not receive new effect IDs:

| Research APK | SHA-256 | Mapping |
|---|---|---|
| `s4-lensflare.apk` | `8AF4CC27AE055973058FE2A697701F8E0069546A6CC52EE27F74D6DB7B528A3F` | Existing ID 0, S4 Lens Flare; variants remain modes, not new effects. |
| `s5-particle.apk` | `A7EB49251361192BBCB920C6DBA7352824319E6585F40D70C6A0EFD40275E7FC` | Existing ID 2, Popping Colours/ParticleSpace. Current Samsung oracle wins on behavioural conflicts. |
| XLocker host APK | `F777ECBAA7920F838AA5152D8B1CA4AF0663BD7B1686AB34693560C7642B3228` | Host only; no effect ID. |

### ID 31 OEM evidence

The clean implementation authority is the independently held Samsung firmware material, not XLocker:

- `F:\New project\firmware-research\s6e-g925f-qpb2\jadx-secvisualeffect\sources\com\samsung\android\visualeffect\lock\circleunlock\CircleUnlockEffect.java`
- `F:\New project\firmware-research\s6e-g925f-qpb2\jadx-secvisualeffect\sources\com\samsung\android\visualeffect\lock\circleunlock\CircleUnlockCircle.java`
- Samsung SystemUI `KeyguardEffectViewNone.java`

Observed timing contract: approximately 666 ms enter and 333 ms exit. It should use natural Canvas/`ValueAnimator` cadence, with no HFR speed multiplier.

### ID 36 native blocker

The researched APK uses `lib/armeabi/libmodel_jni.so`, `lib/armeabi/libms4d_jni.so`, and `assets/model/lock_screen_model.ms3d` plus proprietary textures. The libraries are ELF32 ARM (`e_machine = 40`) and are incompatible with the ARM64-only production contract. They are also unlicensed proprietary implementation material. A future renderer must therefore be independently procedural and use display-vsync scheduling (`Choreographer`/`postOnAnimation`), never the APK's 4 ms loop.

## Audio status

- No audio may be copied from an XLocker APK.
- IDs 32–36 must remain silent for effect-specific touch, lock, and unlock sounds until an independent OEM/licensed source or original app-owned replacement exists. Do not attach a merely similar Samsung sound.
- ID 31 has independently held Samsung OEM candidates:
  - lock: `F:\New project\firmware-research\sm-g318h-amo-qk2\extracted\audio-ui\ui\Lock_none_effect.ogg`, SHA-256 `9CE26FCA4D300110D84EABCDCEE574D45F1046347F9C27A4EE914269F96D121B`;
  - unlock candidate SHA-256: `4091664CE911B74B03F03A0EF5706D60AA9448E996FD0C27C267606980B59F25` (confirm its exact source path and oracle match before integration).
- Preserve both System and Media routing through `EffectAudio`/`LockSoundPlayer`, including asynchronous preload/readiness behaviour.
- XLocker's Lens Flare mode 3/Lightning sound differs from current LLE and is not an import source. For S5 Particle, the current Samsung oracle remains authoritative even where hashes or behaviour appear to match.

## Integration contract

### Renderer interfaces

| ID | Required contracts |
|---|---|
| 31 | `UnlockEffectRenderer`, `UnlockEffectReadiness`; no background-source contract. |
| 32–34 | `UnlockEffectRenderer`, `BackgroundSourceRenderer`, `RawArgb8888BackgroundRenderer`, `UnlockEffectReadiness`. |
| 35 | `UnlockEffectRenderer`, `BackgroundSourceRenderer`, `UnlockEffectReadiness`; Canvas consumes a `Bitmap`. |
| 36 | Same GLES contracts as IDs 32–34, with direct raw ARGB8888 strongly preferred. |

### Background and lifecycle

- Only ID 31 is genuinely no-colormap. IDs 32–36 are screenshot-backed effects.
- Add IDs 32–36 to the central screenshot-background capability, raw colormap metadata/migration handling where applicable, and phone/fold/tablet/orientation profile selection. Avoid duplicating hard-coded effect arrays across the service and UI; use one capability predicate.
- Validate capture dimensions, crop, orientation, CRC, and dim state before upload. Do not stretch a portrait map into landscape or reuse the wrong fold/tablet profile.
- Keep at most one full-size wallpaper texture per renderer where practical, and release it on park/destroy.
- A missing or corrupt map must produce the existing safe fallback, never a blank overlay or touch-blocking dead state.
- Preserve readiness, hint lifecycle, unlock tail, screenshot/direct-wallpaper behaviour, transparency, QS/power-menu parking, recreation after rotation/profile change, and deterministic cleanup.

### Timing and HFR

- The researched LG renderers use real elapsed-millisecond animations (roughly 300–450 ms) and on-demand rendering. Xperia Blinds is also time-based.
- HFR may increase presentation cadence but must not multiply physics or shorten animation duration. Test at 60, 90, 120, and 144 Hz using elapsed time.
- Any speed slider is an explicitly creative modification and must be labelled separately from oracle-fidelity mode.
- ID 31 needs no HFR control. ID 36 must be vsync-driven rather than emulating XLocker's busy 4 ms scheduling.

### UI, availability, and fallback

- Put these entries in a dedicated **XLocker research beta** section with explicit `clean-room` or `inspired beta` badges.
- Gate all six through tester/beta availability until separately validated. A persisted effect that becomes unavailable must resolve to the normal safe fallback.
- Label ID 31 **S5 Circle (None)**. Do not call IDs 32–36 stock/authentic/1:1 without an independent OEM oracle.
- Ensure no-colormap mode exposes ID 31 but greys out IDs 32–36.

## Required verification before any beta build

1. Add deterministic scene tests at 60/90/120/144 Hz, including long press, stationary release, unlock-tail completion, pause/resume, park/recreate, destroy, and readiness.
2. Check for per-frame Java/native allocations and verify texture ownership across repeated lock/unlock and orientation/profile changes.
3. Run phone, fold, and tablet portrait/landscape screenshot tests, including missing/corrupt colormap fallback and no-colormap mode.
4. Regress System and Media audio routing and first-play readiness for ID 31; verify IDs 32–36 remain intentionally silent.
5. Scan the produced APK for XLocker package names, byte-identical resources, audio, shader text, native libraries, and model files. The expected result is none.
6. Build and test ARM64 only after implementation work begins. Do not touch or build ARM32.
7. Record every oracle, authored resource, hash, and licence decision in the provenance ledger before distribution.

Until every applicable gate passes, this document is a research backlog only and none of IDs 31–36 belongs in the 1.0.5.7 release.
