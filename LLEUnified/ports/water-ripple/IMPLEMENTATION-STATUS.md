# Water Ripple GLES AArch64 implementation status

Data: 2026-07-14. Fonte canonica: `reverse/s3-water-ripple/shader-map-agent.md`. La pipeline normal è ora integrata nell'APK ARM64 come effetto `S3 Water Ripple (Early Alpha)`; ink e gravity restano non esposti.

## Implementato

### CONFIRMED — sorgenti e programmi

- Nuovo `native/ripple_gles_shaders.c/.h` con gli otto sorgenti GLSL unici recuperati dall'ELF ARM32: quad, AdvectDensity, AddInk, normal vertex/fragment, ink fragment, gravity vertex/fragment. Nell'ELF i blob fisici sono nove perché il normal vertex compare due volte byte-identico.
- Conservate formule, costanti, qualifier `mediump`, alpha finale `1.0`, refusi/nome uniform originali e assenza di `#version` (GLSL ES 1.00).
- Cinque programmi GLES2: normal, ink, AdvectDensity, AddInk e gravity.
- `glBindAttribLocation(0,"vertex")` e location 1 `texCoord` prima del link per i programmi quad, come l'helper Samsung.
- Le uniform gravity aggiuntive vengono pre-cache nell'ordine originale; le uniform comuni vengono cercate durante il draw.

### CONFIRMED — pipeline GL

- Nuovo `native/ripple_gles_pipeline.c/.h` con stato C zero-initializable e API idempotenti `init`, `destroy` e `abandon` per context loss.
- Mesh finale: VBO position/height float, IBO `uint16_t`, upload `GL_DYNAMIC_DRAW`, attributi `vec3 GL_FLOAT`, `glDrawElements(GL_TRIANGLES,...,GL_UNSIGNED_SHORT)`.
- Ordine uniform/texture normal e ink: ottica Samsung, BG unità 0, water unità 1, density unità 2; cleanup unità 3→0.
- Gravity: BG/water unità 0/1, gravity/caustics unità 2/3/4; uniform time/reference/direction conservate e cleanup 4→0.
- AdvectDensity e AddInk: FBO offscreen, quad `GL_TRIANGLE_STRIP`, sorgenti e formule originali, cleanup FBO/program/blend coerente.
- Correzione applicata rispetto al vecchio report fluid: payload quad di 32 byte `{pos.xy,uv.xy}` in `GL_SHORT`, stride 8, offset 0/4. Non usa `GL_FLOAT`.
- Surface RGBA8 con clamp-to-edge/linear e clear trasparente; upload BG/water/gravity/caustics RGBA8 sincrono, senza conservare il puntatore dei pixel dopo la chiamata.
- Upload bitmap richiede memoria tightly packed RGBA8; sarà responsabilità del bridge copiare/normalizzare stride e formato mentre `AndroidBitmap` è locked.

### CONFIRMED — build e prove Fold7

Compilazione NDK r27d API 23:

```text
-std=c11 -O2 -fno-fast-math -ffp-contract=off -Wall -Wextra -Werror
```

Output:

```text
build/libWaterRippleGles64Wip.so
ELF64 AArch64
SONAME libWaterRippleGles64Wip.so
NEEDED libGLESv2.so, libdl.so, libc.so
SHA-256 7CAD47E145F7ECE65BEB5F8DE2B84BFC9D54F94F2090AF7F93DC2E75016484DA
```

Test statico AArch64 eseguito sul Fold7:

```text
PASS Water Ripple GLSL constants and GL_SHORT quad
```

Test EGL pbuffer GLES2 sul Fold7:

- compilazione/link di tutti i cinque programmi;
- creazione FBO RGBA8;
- upload delle cinque texture esterne;
- esecuzione AdvectDensity e AddInk;
- draw normal, ink e gravity senza `glGetError`;
- distruzione completa con contesto corrente.

Risultato:

```text
PASS Water Ripple GLES2 programs=3,6,9,12,15 gravityDeadWaterBrightness=-1
```

Il `-1` conferma sul driver del Fold7 che `uWaterbrightness`, dichiarata ma inutilizzata dal fragment originale, viene eliminata dal linker.

## Divergenze intenzionali non visive

### CONFIRMED

- Il loader ARM64 fallisce sempre quando compile/link fallisce; non replica il bug ARM32 “compile fallita + info-log length 0 = shader accettato”.
- Gli shader object vengono eliminati dopo il link; l'ARM32 li perde/leaka.
- Un FBO incompleto restituisce errore e viene distrutto; l'ARM32 logga e continua.
- Il renderbuffer vuoto creato dall'originale viene conservato nello struct e poi eliminato, anziché essere perso.
- Ricaricare una texture elimina prima il vecchio nome GL.
- `init` costruisce tutti i programmi in un passaggio; l'ARM32 costruisce solo il sottoinsieme scelto dalla modalità corrente.

Queste differenze riguardano failure/lifecycle e non cambiano shader, uniform, texture sampling o draw validi.

## Stato dell'integrazione e gap residui

- **INTEGRATO (normal)**: bridge JNI app-owned v2, upload/free texture, init/render/destroy/abandon e view Java sono inclusi in `libWaterRipple.so`/APK.
- **INTEGRATO (core)**: `initWaters/ripple/move`, shear NEON storico, clock fisso 60 Hz, bounds e gesture S3 sono attivi nella view Early Alpha.
- **NON ESPOSTO**: mapping frame-per-frame completo dello state machine ink/gravity verso AdvectDensity/AddInk e ping-pong surface. Le primitive GPU sono disponibili ma non fanno parte del target normal corrente.
- **UNRESOLVED**: confronto pixel/hash contro un framebuffer del dispositivo Samsung originale. Il test Fold7 certifica compilazione e assenza di errori, non parità raster bit-per-bit tra GPU differenti.
- **INTEGRATO CON DIVERGENZA DICHIARATA**: gli shader Samsung-exact mantengono alpha `1.0`; l'overlay Android usa una variante separata premoltiplicata con maschera locale, verificata sul Fold con `glReadPixels`.
- **UNRESOLVED**: texture/assets gravity reali e verifica visiva della modalità gravity sul Fold7. Il test usa texture RGBA sintetiche.
- **INTEGRATO**: orientation/resize, context recreation, `destroy()` e `abandon()` sono collegati dal lifecycle Java/JNI e sottoposti a stress Gyro/Ripple senza restart del processo.
