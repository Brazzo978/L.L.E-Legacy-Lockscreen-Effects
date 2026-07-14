# WaterColor ARM32: contratto Java/JNI e lifecycle per il port ARM64

Data analisi: 2026-07-14  
Effetto Samsung: `WaterColor`, effect id `5`  
Scopo: ricostruire l'API e il lifecycle osservabili dell'implementazione ARM32 e proporre il confine corretto per il port ARM64. Questo documento non modifica il runtime LLE64 e non sostituisce l'analisi matematica/render in `PHYSICS.md`.

## Verdetto operativo

L'effetto originale non è una libreria JNI autonoma. La catena reale è:

```text
KeyguardEffectViewWaterColor
  -> EffectView.setEffect(5)
  -> InnerViewManager
  -> WaterColorEffect (LockBGEffect / GLTextureView)
  -> WaterColorRenderer (GLTextureViewRenderer)
  -> com.samsung.android.visualeffect.lock.common.Native
  -> libsecveSrkCommon.so (JNI, texture map, dlopen/dlsym)
  -> libsecveWaterColor.so:createScene (scene/pipeline specifico)
```

Per ARM64 conviene conservare il contratto esterno Samsung — effect id 5, custom command 0..3, semantica touch, `draw() == true` finché attivo — ma non replicare il loader globale e fragile di `libsecveSrkCommon.so`. Il port dovrebbe essere una libreria app-owned ARM64 con handle per istanza, ownership GL esplicita e ricostruzione idempotente al cambio di `Surface`/contesto.

Tre risultati sono particolarmente importanti per la fedeltà:

1. Il comando affordance di WaterColor non chiama `Native.showAffordance`: dopo il delay sintetizza un vero `ACTION_DOWN` al centro del `Rect`.
2. Il renderer Samsung accetta touch solo dopo il primo draw e interpreta il booleano di `draw()` come “servono altri frame”; `false` rimette la view in render-on-demand.
3. Nel wrapper Java originale la seconda riproduzione del tap lungo avviene su `UP/CANCEL/OUTSIDE` dopo 411 ms, non sul primo `MOVE`. Audio e sensori non appartengono alla libreria JNI WaterColor.

## Provenienza e livello di certezza

Le evidenze usate sono:

- smali Samsung estratto in `unlock-effects-test/extracted/secvisualeffect_smali` e `unlock-effects-test/extracted/s4_systemui_smali`;
- ELF ARM32 canonici in `LLE64/reference/arm32-original/native-libs/armeabi-v7a`;
- disassemblato/decompilato selettivo in `GHIDRA-SELECTED-DECOMPILE.txt` e `GHIDRA-PIPELINE-DECOMPILE.txt`;
- reverse precedente in `unlock-effects-test/docs/watercolor-native-reverse-2026-07-11.md`;
- confronto non invasivo con `LLE64/src/com/codex/lle/WatercolorNativeEffectView.java`.

Nel seguito:

- **Certo**: firma/metodo, costante o branch direttamente leggibile nello smali/ELF.
- **Molto probabile**: ruolo ricostruito da call graph, vtable e accessi ai campi.
- **Proposto**: API ARM64 consigliata; non è una firma Samsung originale.

Gli indirizzi native sono VMA ELF/raw senza il bias Ghidra `+0x10000`; per esempio `createScene` è raw `0x11c74`, Ghidra `0x21c74`.

## 1. Contratto Java pubblico e costruzione dell'effetto

### 1.1 `EffectView`

`com.samsung.android.visualeffect.EffectView` è il facade usato da SystemUI. Le operazioni pubbliche rilevanti delegano all'istanza `IEffectView` interna:

| Operazione | Evidenza smali | Comportamento |
|---|---|---|
| `setEffect(int)` | `EffectView.smali:427-490` | chiede a `InnerViewManager` una view, la aggiunge e memorizza l'effect type |
| `init(EffectDataObj)` | `EffectView.smali:267-295` | delega a `IEffectView.init` |
| `reInit(EffectDataObj)` | `EffectView.smali:338-380` | delega a `IEffectView.reInit` |
| `handleTouchEvent(MotionEvent)` | `EffectView.smali:236-265` | delega alla view effetto |
| `handleCustomEvent(int, HashMap)` | `EffectView.smali:184-234` | converte parametri null in una map vuota e delega |
| `clearScreen()` | `EffectView.smali:108-135` | delega a `clearScreen` |
| `removeEffect()` | `EffectView.smali:382-396` | fa solo `removeAllViews()` e pone `mView=null` |

In `InnerViewManager.smali:89-96`, il case effect id `5` costruisce `WaterColorEffect`. `EffectDataObj.setEffect(5)` non configura campi specifici: per WaterColor `init` e `reInit` sono no-op ereditati da `LockBGEffect`.

### 1.2 `WaterColorEffect`

Il costruttore di `lock/watercolor/WaterColorEffect.smali:7-30`:

- estende `LockBGEffect`;
- conserva il `Context`;
- chiama `setEffectRenderer(5)`.

`RenderManaer.smali:49-60` associa l'id renderer `5` a `WaterColorRenderer`. Quest'ultimo estende `GLTextureViewRenderer` e imposta `mLibName="libsecveWaterColor.so"` (`WaterColorRenderer.smali:7-34`). Esistono classi GLSurface più vecchie che indicano `/system/lib/libsecveWaterColor.so`, ma il percorso attivo analizzato è `GLTextureView` + `WaterColorRenderer`.

### 1.3 Custom commands compatibili

`LockBGEffect.handleCustomEvent` mette il lavoro sulla GL thread. Il dispatch esatto è in `LockBGEffect$3.smali`:

| Command | Chiavi/valori | Azione originale |
|---:|---|---|
| `0` | `"Bitmap"` -> `Bitmap` | `setBGBitmap` |
| `1` | `"StartDelay"` -> `Long`, `"Rect"` -> `Rect` | `showAffordanceEffect` |
| `2` | nessun parametro | `showUnlockEffect` |
| `3` | `"Nums"` -> `int[]`, `"Values"` -> `float[]` | `setParameters` |

WaterColor sovrascrive il comportamento generico del comando `1`. `WaterColorEffect.showAffordanceEffect` posta un runnable dopo `StartDelay`; `WaterColorEffect$1.smali:47-110` invoca:

```text
renderer.handleTouchEvent(ACTION_DOWN, Rect.centerX(), Rect.centerY())
```

Quindi, nel port, command `1` deve restare un delayed touch-down al centro del rettangolo. Chiamare direttamente una ipotetica `nativeShowAffordance` cambierebbe il percorso native e non sarebbe 1:1.

### 1.4 Background

`LockBGEffect.setBGBitmap` (`LockBGEffect.smali:220-288`) estrae l'intera bitmap con `Bitmap.getPixels` in un `int[]`, passa pixel, larghezza e altezza al renderer, quindi richiede un frame. Nel renderer i dati restano pending fino a `onDrawFrame`, dove vengono caricati con nome logico esatto `"bg"`.

La pipeline stock tratta il background come immagine ARGB/RGBA piena. La trasparenza locale usata da LLE per mostrare lo sfondo solo dentro la pittura è una scelta di compositing del port e non cambia questo contratto di upload.

## 2. Contratto JNI e loader ARM32

### 2.1 Classe `Native`

`com.samsung.android.visualeffect.lock.common.Native` possiede un solo campo per istanza:

```java
private long mEffectId;
```

Nel suo `<clinit>` (`Native.smali:11-27`) carica, in quest'ordine:

```java
System.loadLibrary("stlport");
System.loadLibrary("secveSrkCommon");
```

Le firme JNI esatte dichiarate dallo smali sono:

```java
static native void pauseAnimation();
static native void resumeAnimation();

native String[] loadEffect(String libraryPath);
native void init(int width, int height, boolean reinit);
native boolean draw();
native void onTouch(int x, int y, int action);
native void clear();
native void showAffordance(int x, int y);
native void showUnlock();
native void setParameters(int[] nums, float[] values);
native void loadTexture(String name, int[] pixels, int width, int height);
native void loadModel(String name, byte[] data);
native void destroy();
```

Non risultano chiamanti smali di `pauseAnimation()` o `resumeAnimation()`. Non vanno considerati driver obbligatori del lifecycle WaterColor.

### 2.2 Dove risiede realmente JNI

`libsecveWaterColor.so` non esporta il set di metodi Java. Esporta sostanzialmente `createScene` a `0x11c74`; le entry JNI sono in `libsecveSrkCommon.so`:

| JNI `Native` | VMA ARM32 in common |
|---|---:|
| `init` | `0xf5e8` |
| `destroy` | `0xff78` |
| `pauseAnimation` | `0x10400` |
| `resumeAnimation` | `0x10494` |
| `clear` | `0x1052c` |
| `showAffordance` | `0x109c4` |
| `showUnlock` | `0x10eac` |
| `draw` | `0x1134c` |
| `onTouch` | `0x11fc0` |
| `setParameters` | `0x14158` |
| `loadEffect` | `0x15278` |
| `loadTexture` | `0x18140` |
| `loadModel` | `0x19f5c` |

`libsecveSrkCommon.so` contiene `dlopen`, `dlsym`, la stringa `"createScene"`, `gCreateScene` e `gTextureMap`. `Native.loadEffect(path)` è quindi il confine dinamico: apre la libreria effetto, risolve `createScene`, costruisce la scene e restituisce a Java l'elenco dei resource name da caricare.

La conseguenza è netta: copiare soltanto una `libstlport.so` ARM64 dal firmware Note 5 non rende eseguibile l'effetto. Servirebbe almeno una coppia ABI-compatible ARM64 `secveSrkCommon` + `secveWaterColor`, che non è stata trovata; inoltre resterebbero dipendenze e assunzioni Samsung private. Per il port conviene ricostruire entrambi i ruoli in una singola libreria ARM64 app-owned.

### 2.3 Identità e dipendenze degli ELF canonici

| File | Dimensione | SHA-256 | Nota |
|---|---:|---|---|
| `libsecveWaterColor.so` | 79,060 B | `2B00D2590A9C92BFE5461C8890CA1E4F5D8D0A8196B0BA67C9CDB2B35895C2EB` | ELF32 ARM, scene specifica |
| `libsecveSrkCommon.so` | 341,296 B | `5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36` | ELF32 ARM, JNI/loader/texture map |
| `libstlport.so` | 214,352 B | `B7B845F6E446E87878152D25D6DDE9657B5260B9DC47A540C87D2F6A67A97E09` | runtime C++ ARM32 |

`libsecveWaterColor.so` dichiara tra i `DT_NEEDED`: `libEGL`, `libGLESv2`, `libandroid`, `libc`, `libdl`, `libjnigraphics`, `liblog`, `libm`, `libsecveSrkCommon`, `libstdc++`, `libstlport`. La common dipende anch'essa da `libstlport`.

## 3. Texture/resource contract

Dopo `loadEffect`, `GLTextureViewRenderer.loadSpecialTexture` crea un package context per:

```text
com.samsung.android.visualeffect.res
```

Risolve ogni nome come drawable, decodifica senza scaling intenzionale, estrae gli ARGB in `int[]` e chiama `Native.loadTexture(name, pixels, width, height)`. I nomi incorporati dalla libreria WaterColor sono:

```text
watercolor_mask1
watercolor_mask2
watercolor_mask3
watercolor_noise
waterbrush_tube
```

Nel native compaiono anche le chiavi logiche `Mask1`, `Mask2`, `Mask3`, `Noise`, `Tube` e `bg`. Il port deve mantenere identità, dimensioni e orientamento dei campioni; rinominarli è sicuro solo se anche il mapping native viene aggiornato in modo univoco.

Asset originali correnti di riferimento in LLE64:

| Asset | Dimensioni | Byte | SHA-256 |
|---|---:|---:|---|
| `waterbrush_tube.png` | 480x480 | 12,799 | `BE1C3AFB734D4AFE04CDBED923F882BC4DA360008A918609F797DC1A447F90F2` |
| `watercolor_mask1.png` | 641x655 | 127,409 | `F0B23FB55C80839616189FB75754A139CF9F09683AC12952548599EED4A3FE1D` |
| `watercolor_mask2.png` | 675x733 | 119,903 | `20803E2C8867284DCE59EE0B7158BE860C4F2BED965EF41AA477863BE26ABAE5` |
| `watercolor_mask3.png` | 803x793 | 119,045 | `D527A9FEB90173A0ADDE3DF1E1CE0299E781FFAC3FD28C968A96FB57A687B8D5` |
| `watercolor_noise.jpg` | 360x640 | 20,362 | `01283D870B1D483AF96F99A3343A3D4E459AFE2BF568DBE5C61541F20A1CB642` |

## 4. Lifecycle GL originale

### 4.1 Creazione e prima inizializzazione

`LockBGEffect.setEffectRenderer(5)`:

1. imposta EGL client version 2;
2. ottiene `WaterColorRenderer` da `RenderManaer`;
3. lo assegna a `GLTextureView.setRenderer`, che crea e avvia la GL thread.

`GLTextureView` si registra come proprio `SurfaceTextureListener` e nello stock imposta `setOpaque(true)`. Quando la texture diventa disponibile, chiama in sequenza `surfaceCreated` e `surfaceChanged` sulla GL thread.

`GLTextureViewRenderer.onSurfaceCreated`:

1. legge le real display metrics;
2. mette `mIsNeedToReinit=true`;
3. legge `ApplicationInfo.nativeLibraryDir`;
4. trasforma `mLibName` concatenando `nativeLibraryDir + "/" + mLibName`.

Al primo `onDrawFrame`, se `mIsNeedToReinit`:

1. `Native.loadEffect(mLibName)`;
2. carica le cinque texture speciali restituite dalla scene;
3. azzera il frame counter;
4. carica l'eventuale background pending come `"bg"`;
5. chiama `Native.init(mWidth, mHeight, true)`;
6. consuma l'eventuale affordance generica pending;
7. chiama `Native.draw()`;
8. dopo il primo draw pone `isRendered=true`.

Il resize (`onSurfaceChanged`) scarta dimensioni non positive, aggiorna width/height, marca reinit se cambiate e passa in continuous rendering. Esiste anche una guardia difensiva basata su orientamento/epsilon; non è una firma ABI e può essere sostituita da una validazione moderna purché le dimensioni effettive della surface siano coerenti.

### 4.2 Loop di rendering

Il valore di ritorno di `Native.draw()` controlla lo scheduler Java:

- `true`: l'effetto è ancora attivo e il renderer può continuare a produrre frame;
- `false`: `GLTextureViewRenderer` imposta render mode `RENDERMODE_WHEN_DIRTY`.

Qualsiasi touch accettato, `clear`, background, resize, unlock o affordance rimette la view in continuous mode. Questo è il contratto da preservare: non serve un loop Java sempre acceso quando la simulazione è vuota.

La simulazione originale è frame-stepped intorno a 60 Hz. Su pannelli 120 Hz il port deve separare tick logico e presentazione: accumulator/fixed timestep da 16,666,667 ns, con al massimo rendering/interpolazione aggiuntiva. Legare un update completo a ogni vsync raddoppia velocità, diffusione e decadimento; forzare il display a 60 Hz non è necessario e rompe la compatibilità dinamica 60/120 Hz.

### 4.3 Touch readiness e mapping azioni

`GLTextureViewRenderer.handleTouchEvent` ignora il touch finché `isRendered` è false. Quando pronto usa coordinate raw e mappa Android action verso i codici passati a `Native.onTouch(x, y, code)`:

| Android action esterna | Codice common/scene | Significato host |
|---:|---:|---|
| `0` | `0` | down |
| `1` | `1` | up |
| `2` | `2` | move |
| `9` | `3` | hover/pattern enter trasformato |
| `10` | `4` | hover/pattern exit trasformato |
| `7` | `5` | hover/pattern move trasformato |

Le action `3/4/5/6/8` non invocano il native nel renderer generale, ma fanno comunque ripartire la modalità continuous.

Dentro la scene WaterColor i thunk ricostruiti trasformano nuovamente questi codici nel protocollo `SPISceneComponent`. Gli indirizzi raw sono:

| Scene thunk | Scene event emesso |
|---:|---:|
| `0xa12c` | `0` |
| `0xa17c` | `1` |
| `0xa1cc` | `2` |
| `0xa21c` | `9` |
| `0xa26c` | `10` |
| `0xa2bc` | `7` |

I thunk moltiplicano input normalizzati per width/height memorizzati, poi chiamano `SPISceneComponent::onTouchEvent`; l'handler WaterColor è raw `0x5514`. Il doppio mapping non va semplificato “a intuito”: l'adapter ARM64 deve ricevere azioni Android in modo esplicito e tradurle una sola volta nel protocollo interno equivalente.

### 4.4 Clear, unlock e parametri

- `clearScreen` viene accettato dal renderer solo dopo `isRendered`; invoca `Native.clear()` e riattiva il loop.
- `showUnlockEffect` è anch'esso gated da `isRendered`; chiama `Native.showUnlock()`.
- `setParameters(int[], float[])` delega direttamente al native.
- lo show-affordance generico del base renderer esiste, ma WaterColor command `1` non lo usa: sintetizza il down ritardato descritto sopra.

Nel native scene, `0xa11c` imposta il flag pipeline `+0xcf1` tramite `0x5aac`, percorso coerente con reset/show-unlock; `0xa124` imposta `+0xab0` tramite `0x4f38`, percorso affordance/speciale. `0xa09c` è invece solo il wrapper viewport e non un distruttore.

### 4.5 Distruzione, detach e bug storico di reattach

`GLTextureView.onDetachedFromWindow` chiama `requestExitAndWait()` e marca la view detached. La GL thread, quando osserva `mShouldExit`, invoca `renderer.onDestroy()` prima del teardown EGL. `GLTextureViewRenderer.onDestroy`, se `isRendered`, chiama `Native.destroy()`.

`onAttachedToWindow`, se la stessa view era detached, crea una nuova GL thread riutilizzando lo stesso renderer e il render mode precedente. Questo espone un bug nel codice stock:

```text
primo attach:
  mLibName = "libsecveWaterColor.so"
  -> "/data/app/.../lib/arm/libsecveWaterColor.so"

reattach della stessa istanza:
  mLibName è già assoluto
  -> "/data/app/.../lib/arm//data/app/.../lib/arm/libsecveWaterColor.so"
```

Inoltre lo stesso oggetto renderer conserva stato Java dopo che la scene native e il contesto GL sono stati distrutti. `EffectView.removeEffect()` non chiama esplicitamente `destroy`: rimuove le child view; la distruzione dipende quindi dal detach/uscita della GL thread.

La politica sicura ARM64 è una delle seguenti:

- ricreare interamente view/renderer/handle native dopo detach; oppure
- implementare `surfaceLost`/`surfaceCreated` idempotenti, con path immutabile e ricostruzione completa delle risorse GL.

Riutilizzare handle o texture/FBO GL del vecchio contesto non è corretto.

## 5. Lifecycle native scene/pipeline

Il costruttore esportato `createScene` a raw `0x11c74` alloca `0xdc` byte e chiama il costruttore scene `0xeb90`; la vtable scene è intorno a `0x13c88`.

La catena init scene `0xcaac -> 0xb168`:

1. alloca il componente/pipeline WaterColor da `0xcf8` byte;
2. chiama il costruttore raw `0x5c30` con configurazione equivalente a `(true, 3)`;
3. salva il componente in `scene + 0xd8`;
4. prosegue verso l'init `SPISceneComponent` comune.

La vtable del componente a `0x13b98` consente questa mappa:

| Raw | Ruolo ricostruito |
|---:|---|
| `0x9a00` / `0x9d2c` | distruttore / deleting destructor del componente |
| `0x2b2c` | init/size: memorizza w/h, inizializza background, imposta flag ready |
| `0x2ab0` | resize/dirty: aggiorna w/h e marca rebuild se già inizializzato |
| `0x3a68` | update CPU/simulazione |
| `0x70e0` | draw |
| `0x4d0c` | dispatcher comando custom |
| `0x2b6c` | parametri |
| `0x5514` | touch |
| `0x3658` circa | clear/reset thunk verso `0x3448` |
| `0x2ad0` | stato active/empty, ruolo inferito dal call graph |

Il wrapper scene raw `0xa5bc` aggrega `onUpdateScene`, `onDrawScene` e `isEmpty`. È la base native del booleano restituito a Java: continua finché la pipeline ha lavoro, altrimenti diventa idle. Il reload texture/background passa dal wrapper raw `0xab08` e da callback del componente. I teardown corposi della scene sono raw `0xd770` e `0xdbcc`; non vanno confusi con il wrapper viewport `0xa09c`.

Il componente usa le classi Samsung `SPDrawRadialWaterBrush`, `SPDrawBGAdvectWaterBrush`, `SPDrawMixWaterBrush` e `SPDrawBackground`, con circa 60 import della famiglia SPhysics. I dettagli di shader, coefficienti, ping-pong target e fisica sono documentati separatamente in `PHYSICS.md` e nel reverse del 2026-07-11.

## 6. Integrazione SystemUI/lockscreen

`com.android.keyguard.sec.KeyguardEffectViewWaterColor` estende `EffectView` e implementa `KeyguardEffectViewBase`. Il costruttore configura:

- effect id `5`;
- due sound id;
- soglia long press `0x19b`, cioè 411 ms;
- tempo rilascio audio unlock 2000 ms;
- stato `isUnlocked=false`.

Il lifecycle lockscreen osservato è:

| Evento SystemUI | Azione effetto |
|---|---|
| `update()` / `setContextualWallpaper()` | command `0` con wallpaper corrente |
| `show()` | prepara suoni, `clearScreen()`, `isUnlocked=false` |
| `reset()` | `isUnlocked=false`, `clearScreen()` |
| `screenTurnedOff()` | clear e rilascio degli eventuali DVFS lock |
| `screenTurnedOn()` | `isUnlocked=false`, clear ed eventuali DVFS lock |
| `showUnlockAffordance()` | command `1` con `StartDelay` e `Rect` |
| `handleUnlock()` | command `2`, `isUnlocked=true`, suono unlock |
| `cleanUp()` | stop/release SoundPool, clear posticipato di 400 ms, rilascio DVFS |

`getUnlockDelay()` restituisce 250 ms. `cleanUp()` non chiama `removeEffect()`: il rilascio native resta legato al lifecycle view/GL thread.

### 6.1 Touch ordinario e pattern path

Se `isUnlocked` è true, il touch viene assorbito. Altrimenti:

- `DOWN`: memorizza il tempo, assicura il SoundPool e riproduce il tap;
- `MOVE`: inoltra soltanto il movimento;
- `UP`, `CANCEL` o `OUTSIDE`: se sono passati più di 411 ms dal down, riproduce di nuovo il tap, poi inoltra.

Nel percorso pattern, SystemUI trasforma le azioni prima di inoltrarle:

- ordinary down -> action `9`;
- move -> action `7`;
- up/cancel/outside -> action `10`.

Questo spiega i thunk scene dedicati 9/10/7 e deve essere conservato se LLE espone lo stesso percorso di interazione.

### 6.2 Audio

L'audio è interamente Java/SystemUI, non JNI:

- controlla `Settings.System.lockscreen_sounds_enabled` per user `-2`;
- crea, dopo boot completo, un `SoundPool` con max streams `10`;
- usa `AudioAttributes` usage `13` e content type `4` (sonification);
- carica `watercolor_tap` e `watercolor_unlock`;
- riproduce volume L/R `1.0`, priority `0`, loop `0`, rate `1.0`;
- rilascia il pool 2000 ms dopo unlock.

Per fedeltà temporale, il secondo tap lungo deve essere emesso sul terminal event dopo 411 ms. Il wrapper LLE64 attuale lo emette sul primo `MOVE` oltre soglia e usa max streams `3`: è una divergenza host, non della fisica o del JNI. Può essere corretta in una fase successiva senza cambiare il port native.

### 6.3 Sensori

Non esiste una dipendenza WaterColor da gyro/accelerometro:

- nessuna registrazione `SensorManager` nello smali host WaterColor;
- nessuna firma sensor nella classe Java `Native`;
- l'hover pubblico della view keyguard restituisce false/no-op.

Le capability sensor eventualmente presenti nel framework SPhysics comune non sono collegate da questo effetto. L'API ARM64 WaterColor non deve inventare un canale sensori.

## 7. Mappa API proposta per ARM64

Questa è una proposta, non una ricostruzione letterale delle firme Samsung. L'obiettivo è mantenere compatibilità comportamentale eliminando globali, `dlopen` privato e lifetime ambiguo.

```java
final class WatercolorNative64 {
    static native long nativeCreate();
    static native void nativeDestroy(long handle);

    static native boolean nativeOnSurfaceCreated(long handle);
    static native void nativeOnSurfaceDestroyed(long handle);
    static native boolean nativeResize(long handle, int width, int height);

    static native boolean nativeLoadTexture(
            long handle, String name, Bitmap bitmap);
    static native boolean nativeSetBackground(
            long handle, Bitmap bitmap);

    // true = la pipeline richiede un altro frame
    static native boolean nativeDraw(long handle, long frameTimeNanos);
    static native void nativeTouch(
            long handle, int androidAction, float rawX, float rawY);

    static native void nativeClear(long handle);
    static native void nativeShowUnlock(long handle);
    static native void nativeSetParameters(
            long handle, int[] nums, float[] values);
    static native boolean nativeIsReady(long handle);
}
```

`nativeShowAffordance` può esistere come hook diagnostico/generico, ma l'adapter compatibility WaterColor command `1` deve continuare a schedulare `nativeTouch(ACTION_DOWN, centerX, centerY)`.

### 7.1 Mapping vecchio -> nuovo

| Samsung ARM32 | Port ARM64 proposto |
|---|---|
| `loadEffect(path)` + `createScene` | `nativeCreate()` + `nativeOnSurfaceCreated()`; asset bundled nell'app |
| `init(w,h,true)` | `nativeResize` e rebuild context-aware |
| `loadTexture(asset,...)` | `nativeLoadTexture(handle,name,Bitmap)` |
| `loadTexture("bg",...)` | `nativeSetBackground(handle,Bitmap)` |
| `draw()` | `nativeDraw(handle,frameTimeNanos)` con identica semantica active/idle |
| `onTouch(x,y,code)` | `nativeTouch(handle,androidAction,x,y)`; mapping centralizzato |
| `clear()` | `nativeClear(handle)` |
| `showUnlock()` | `nativeShowUnlock(handle)` |
| `setParameters(...)` | `nativeSetParameters(...)` |
| `destroy()` | `nativeOnSurfaceDestroyed` + `nativeDestroy`, entrambi idempotenti |
| `pause/resumeAnimation()` | scheduler/lifecycle Java; nessun globale necessario |

L'adapter Java sopra il native deve mantenere command `0..3` e chiavi originali. Questo permette di sostituire il backend senza cambiare il picker o l'integrazione lockscreen.

### 7.2 State machine raccomandata

```text
NEW
  -> SURFACE_BOUND
  -> ASSETS_LOADED
  -> SIZED_READY
  -> ACTIVE <-> IDLE
  -> SURFACE_LOST
  -> SURFACE_BOUND ...
  -> DESTROYED
```

Invarianti:

1. Tutte le operazioni che creano/usano/distruggono oggetti GL avvengono sulla GL thread proprietaria.
2. L'handle è per istanza; niente `gCreateScene`, `gTextureMap` o altre ownership globali condivise tra effetti.
3. `nativeDestroy(0)`, doppio destroy e `nativeOnSurfaceDestroyed` ripetuto sono no-op sicuri.
4. Dopo context loss si ricreano programmi, FBO, texture e buffer, quindi si ricaricano le cinque texture originali e il background. Nessun GL id sopravvive al contesto.
5. Path e nomi di libreria sono immutabili. Idealmente non esiste alcun `dlopen`: JNI è già nel `.so` ARM64 dell'app.
6. Background e resize possono essere messi pending finché la surface è pronta. Il touch ordinario va ignorato o accodato con policy deterministica; per equivalenza stock, ignorarlo prima del primo draw è la scelta più fedele.
7. La pipeline accetta touch solo quando dimensioni surface, viewport e background usano lo stesso spazio coordinate.
8. `nativeDraw == false` porta il renderer in when-dirty; un nuovo evento valido lo riattiva.
9. La simulazione usa tick fisso 60 Hz indipendente dal refresh panel. Al ritorno 120 -> 60 Hz non deve cambiare velocità o lifetime della pittura.
10. Audio rimane Java-owned; nessuna API sensori è necessaria.

## 8. Punti di compatibilità da non perdere nel port

- **Coordinate:** l'host stock usa `MotionEvent.getRawX/getRawY`. Se LLE usa coordinate locali, deve convertirle esplicitamente nello stesso spazio del viewport/background.
- **Directionality:** verificare su tracce diagonali e agli estremi; il doppio mapping Samsung non giustifica inversioni X/Y nel port.
- **Cadence:** update fisico a 60 Hz su display 60 e 120 Hz; non uno step per vsync.
- **Affordance:** delayed real down al centro del `Rect`; delay originario fornito da SystemUI. LLE oggi applica un minimo di 1000 ms per una race moderna AOD/compositor: è una mitigazione host documentata, non una costante dell'algoritmo originale.
- **First frame gate:** background/assets/size pronti prima del touch.
- **Idle:** rispettare il booleano `draw`; niente render loop permanente.
- **Context loss:** reupload totale, nessun riuso di risorse GL stale.
- **Detach:** ricreare l'intera istanza o usare un lifecycle idempotente; mai concatenare nuovamente un path già assoluto.
- **Transparency:** lo stock finale è opaco; il compositing locale trasparente richiesto da LLE è un adattamento intenzionale, da validare separatamente dalla fedeltà della simulazione.
- **Audio long hold:** secondo tap al rilascio/cancel/outside >411 ms, non durante move.
- **No gyro:** non aggiungere drift sensor-driven a WaterColor.

## 9. Checklist di accettazione 1:1 lifecycle/JNI

- [ ] effect id `5` costruisce una nuova istanza WaterColor indipendente.
- [ ] command `0` sostituisce il background e rende dirty la surface.
- [ ] command `1` rispetta il delay e genera down al centro del rect.
- [ ] command `2` attiva show-unlock e suono Java senza corrompere lo stato.
- [ ] command `3` conserva cardinalità e ordine di `Nums`/`Values`.
- [ ] touch prima del primo frame non entra nella pipeline.
- [ ] azioni ordinary 0/1/2 e pattern 9/10/7 producono percorsi equivalenti.
- [ ] `clear`, screen off/on e reset sono ripetibili senza crash.
- [ ] `draw=false` ferma gli update; il successivo input valido li riattiva.
- [ ] resize e cambio 60/120 Hz non cambiano velocità fisica.
- [ ] surface destroy/recreate ricostruisce tutte le risorse e conserva/reinvia background.
- [ ] detach/reattach ripetuto non duplica path e non usa handle stale.
- [ ] distruzione durante idle, durante animazione e prima del first draw è idempotente.
- [ ] nessun global state impedisce due istanze sequenziali o un rapido switch effetto.
- [ ] asset names/dimensioni/hash corrispondono ai riferimenti canonici.
- [ ] audio tap lungo avviene al terminal event dopo 411 ms.
- [ ] assenza di input sensori confermata nel comportamento finale.

## 10. Questioni residue per la fase di implementazione

1. Definire la policy esatta per eventi arrivati mentre il context è perso: stock li ignora di fatto prima del first draw; accodare solo background e affordance è la scelta più prudente.
2. Separare test di fedeltà algoritmo da test di compositing LLE: l'alpha locale intenzionale non deve mascherare errori di diffusione, lifetime o mapping coordinate.
3. Tracciare, con capture frame-to-frame, la transizione di `isEmpty` per rendere identica la soglia di ritorno `draw=false`.
4. Decidere se mantenere il workaround host affordance minimo 1000 ms come profilo “modern lockscreen” e offrire il delay Samsung letterale nei test reference.
5. Correggere l'audio long-hold nel wrapper Java in una patch separata dal native port, così da isolare regressioni.

La conclusione è che il port ARM64 non richiede di emulare l'intero framework Samsung: richiede un backend per-instance che riproduca scene/pipeline e una piccola compatibility layer Java fedele ai command, al gating del primo frame e al lifecycle GL. Il maggiore rischio di crash non è `libstlport` in sé, ma l'ownership implicita del vecchio loader e il riuso dello stesso renderer dopo la distruzione del contesto.
