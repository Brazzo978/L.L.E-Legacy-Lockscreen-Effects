# Water Ripple ARM64 — integrazione lifecycle Java/JNI

Data: 2026-07-14. Fonte canonica: `LLE64/reverse/s3-water-ripple/lifecycle-map-agent.md`.

Questa integrazione è collegata al picker LLE64 come `S3 Water Ripple (Early Alpha)`. Non modifica il vecchio progetto 32-bit e non richiede API Samsung private.

## File aggiunti

- `LLE64/src/com/codex/lle/S3Arm64RippleEffectView.java`
  - `GLSurfaceView` app-owned;
  - mesh/simulazione normale S3;
  - lifecycle EGL, resize, input, background e cleanup;
  - implementa `UnlockEffectRenderer` e `BackgroundSourceRenderer` ed è selezionata dal factory per l'effetto S3 ARM64.
- `LLE64/src/com/codex/lle/S3RippleLifecycleNative.java`
  - boundary JNI dedicato per pipeline GLES, upload bitmap e teardown.
- `LLE64/ports/water-ripple/native/water_ripple_jni_lifecycle.c`
  - collega Java a `ripple_gles_pipeline.h`;
  - mantiene un solo `LleRippleGles` statico, coerente con il singleton ARM32;
  - propaga errori nativi e GL a Java.

`build.ps1` compila e verifica la libreria completa AArch64, controlla gli export JNI e la inserisce nell'APK.

## Stato implementato

### Thread e input

- Tutti i cambi di stato simulazione, `ripple()`, `move()`, upload, render e delete GL sono eseguiti nel render thread tramite `queueEvent()`.
- Ogni input conserva `downTime` ed `eventTime` da `SystemClock.uptimeMillis()`; il thread GL scarta eventi temporalmente precedenti all'ultimo elaborato.
- Sequenza normale S3 conservata:
  - down: impulso `4 * intensity`;
  - move: distanza intera accumulata e tre impulsi `3 * intensity` a `0/+20/+40 ms` quando `>150 px`;
  - up tenuto `>600 ms`: impulso `4 * intensity`;
  - cancel: chiude il gesto senza nuovo impulso.
- Coordinate conservate come nell'originale: conversione screen→mesh con ratio portrait `30/46`, landscape `45/25`, chiamata native con asse scambiato `ripple(glY, glX, ...)`.
- Multi-touch `>1` viene soppresso; al ritorno a un dito l'origine incrementale viene riallineata senza un nuovo down/ripple.
- `ACTION_UP` usa direttamente le coordinate terminali senza un MOVE sintetico precedente.
- I suoni originali down/up seguono timing Samsung, toggle/orari LLE e setting lockscreen di sistema; l'affordance resta silenzioso.
- Nessun `onTouch()` ink viene chiamato nella modalità normale, coerente col caller Samsung.

### Simulazione e frame order

- Detail grid `104x104`, surface mesh `100x100`, mesh logica `50x50`.
- Damping `0.94`, wave coefficient `0.5`.
- Intensità portrait/landscape `0.5/0.35`.
- Ottica normale: refractive `0.93`, reflection `0.13`, alpha `1/1`, fresnel/specular/exponent `0.1/0.5/20`.
- Primo frame senza `move()`, poi draw→move come nello smali originale; la fisica resta a 60 Hz anche su pannelli 120/144 Hz.
- Bounds letterali S3: portrait `3,21,101,83`, landscape `21,3,83,101`.
- Packing `gpuHeights` trasposto e triplette dei tre vicini copiati nell'ordine Samsung.
- Passaggio a `RENDERMODE_WHEN_DIRTY` solo quando `move()` segnala vuoto e non c'è touch; un nuovo input riattiva il render continuo.

### Lifecycle EGL

- Ogni `onSurfaceCreated()` incrementa una `contextGeneration`.
- Alla ricreazione context chiama `nativeAbandonGpu()`: azzera nomi di un contesto perso senza fare `glDelete*` nel nuovo context.
- `onSurfaceChanged()` confronta sia width sia height.
- Resize nello stesso context esegue destroy completo e una sola init; non replica la doppia init/leak dello smali.
- Init, texture upload e render sono rifiutati quando il bridge completo non è presente: una build core-only resta trasparente invece di lanciare `UnsatisfiedLinkError` in loop.
- Matrice view/projection/translate riproduce l'helper Samsung, inclusa la sua formula perspective non standard.

### Bitmap ownership

- La UI crea una copia software `ARGB_8888` e center-crop della screenshot alla dimensione view.
- Il bitmap candidato resta posseduto dalla view finché il comando GL non lo carica, lo sostituisce oppure lo scarta.
- Il JNI accetta solo `ANDROID_BITMAP_FORMAT_RGBA_8888` software.
- Sequenza nativa atomica:

```text
AndroidBitmap_getInfo
AndroidBitmap_lockPixels
eventuale copia row-by-row se stride != width*4
glTexImage2D sincrono
AndroidBitmap_unlockPixels
```

- Nessun pointer viene conservato dopo unlock.
- Un seriale elimina background update obsoleti; la sostituzione avviene sul solo thread GL.
- Se il nuovo upload fallisce, il renderer tenta di ricaricare il bitmap precedente.
- Reflection map `s3_reflectionmap` viene normalizzata `ARGB_8888` e resta di proprietà della view fino a pause/destroy.

### Pause/destroy

- `onPause()`/detach mettono in coda il destroy nel context corrente, con attesa propria limitata a 350 ms, quindi chiamano `GLSurfaceView.onPause()`.
- `destroy()` impedisce nuovi comandi, annulla affordance pendenti, esegue cleanup GL bounded, ricicla tutti i bitmap posseduti e libera il gate singleton.
- Gli ID sono azzerati dal `LleRippleGles` destroy/abandon idempotente.
- Non ci sono handler Samsung, SContext, DVFS, hidden settings o accesso ai vecchi path wallpaper.

## Verifiche eseguite

### Java

Compilazione diretta dei nuovi file contro Android 35, Java 8 e le classi LLE64 già generate:

```text
javac -source 1.8 -target 1.8 ...
PASS: S3Arm64RippleEffectView + S3RippleLifecycleNative
```

Sono presenti soltanto i tre warning standard del JDK moderno per source/target 8 obsoleti; nessun errore Java.

### JNI AArch64

`water_ripple_jni_lifecycle.c`:

```text
aarch64-linux-android23-clang -std=c11 -O2 -fPIC \
  -Wall -Wextra -Werror -c water_ripple_jni_lifecycle.c
PASS
```

Link temporaneo completo con core + JNI core + shader/pipeline + lifecycle:

```text
Machine: AArch64
Type: DYN
SONAME: libWaterRipple.so
NEEDED: libandroid.so, liblog.so, libGLESv2.so, libm.so, libdl.so, libc.so
```

Gli export JNI core `initWaters/move/ripple` e tutti gli export `S3RippleLifecycleNative` risultano presenti. Questo test non ha modificato il packaging.

Artefatto di link verificato (non pacchettizzato):

```text
path:   LLE64/ports/water-ripple/build/lifecycle/libWaterRipple.so
size:   47752 bytes
SHA256: 82DA321694CDAD57C703CA8EB1700C0FBCF1497FEF09E2A7E7C1477C3AAC81A6
```

Export JNI verificati:

```text
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_initWaters
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_move
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_ripple
Java_com_codex_lle_S3RippleLifecycleNative_nativeAbandonGpu
Java_com_codex_lle_S3RippleLifecycleNative_nativeBridgeVersion
Java_com_codex_lle_S3RippleLifecycleNative_nativeDestroyGpu
Java_com_codex_lle_S3RippleLifecycleNative_nativeFreeTexture
Java_com_codex_lle_S3RippleLifecycleNative_nativeGetLastError
Java_com_codex_lle_S3RippleLifecycleNative_nativeInitGpu
Java_com_codex_lle_S3RippleLifecycleNative_nativeRenderNormal
Java_com_codex_lle_S3RippleLifecycleNative_nativeUploadBitmap
```

La pipeline C e la view lifecycle sono ora eseguite sul Fold7 nella build Early Alpha. Init GPU, background reale, teardown e ricreazione durante le transizioni Gyro/Ripple sono passati senza restart del processo o voci nel crash buffer.

## Gap residui dopo l'abilitazione Early Alpha

1. **Compositing**: la maschera locale premoltiplicata è verificata e trasparente, ma non è l'alpha fullscreen Samsung. Un compositing adattivo più esatto richiede un esperimento separato.
2. **Background**: la view usa la screenshot accessibility center-crop; il wallpaper S3 originale e l'adattamento Fold sono esplicitamente rinviati.
3. **Stress lungo**: build/install, background reale e cicli Gyro/Ripple sono verificati; restano utili cicli screen off/on e context recreation prolungati per misurare memoria/stall driver.
4. **Pixel/channel test**: validare con un pattern RGBA noto che `ARGB_8888`/`ANDROID_BITMAP_FORMAT_RGBA_8888` e il sampling shader mantengano i canali sul device target.
5. **Memory peak background**: normalizzazione + crop può tenere temporaneamente due copie della screenshot. È corretta per ownership ma va misurata su risoluzioni Fold aperto/chiuso.
6. **Driver stall**: l'attesa esplicita del comando cleanup è limitata a 350 ms; `GLSurfaceView.onPause()` è framework code e può comunque attendere un GL thread bloccato nel driver.
7. **Solo normal mode**: ink e gravity non sono esposti da questa view. Gravity resta bloccato anche dai tre asset framework Samsung originali mancanti.
8. **Singleton**: la view impedisce due istanze nello stesso processo, coerente con ARM32. Un futuro port multi-instance richiede anche core/simulazione e GLES per-handle.
9. **Suoni**: i file S3 down/up sono attivi; il caricamento `SoundPool` è asincrono, quindi il primissimo tocco immediato dopo la costruzione può essere silenzioso.

## Verifica corrente

La build Early Alpha corrente ha superato:

- `.so` ARM64 completa e export verificati da `readelf`;
- test core, shader statico, cinque programmi GLES2 e overlay `glReadPixels` sul Fold7;
- APK installata con hash locale/device identico;
- cinque transizioni Gyro/Ripple con PID invariato e crash buffer vuoto;
- output trasparente e comportamento base già confermati visivamente dall'utente.

Il confronto visivo finale dei nuovi trail/audio/multi-touch resta manuale sul lockscreen.
