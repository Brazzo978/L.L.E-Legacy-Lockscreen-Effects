# Note5 Sparkling Bubbles native port

Questo documento descrive il primo port dell'effetto Samsung Note5
`SparklingBubbles` dentro l'overlay dell'app.

## Sorgenti originali

- Native renderer: `extracted/note5_aoj4_system_files/lib/libSparklingBubblesEffect.so`
- Java framework Samsung: `extracted/secvisualeffect_hybrid_dex/classes.dex`
- Smali framework:
  - `extracted/note5_aoj4_secvisualeffect_smali/com/samsung/android/visualeffect/lock/sparklingbubbles/`
  - `extracted/note5_aoj4_secvisualeffect_smali/com/samsung/android/visualeffect/lock/common/SPhysicsEffect_TV.smali`
- Wrapper SystemUI di riferimento:
  - `extracted/s5_systemui_smali/com/android/keyguard/sec/effect/KeyguardEffectViewSparklingBubbles.smali`
- Asset:
  - `blur_mask.png`, estratto da `extracted/note5_aoj4_more/priv-app/SystemUI/SystemUI.apk`
  - path APK originale: `res/drawable-nodpi-v4/blur_mask.png`
- Suoni:
  - `ve_sparklingbubbles_tap.ogg`
  - `ve_sparklingbubbles_drag.ogg`
  - `ve_sparklingbubbles_lock.ogg`
  - `ve_sparklingbubbles_unlock.ogg`

Nel progetto l'app usa la versione ARM32:

`charging-touch-test-apk/native-libs/lib/armeabi-v7a/libSparklingBubblesEffect.so`

## Entry point Samsung

L'effetto usa il vero `com.samsung.android.visualeffect.EffectView`, istanziato
via reflection dal wrapper locale:

`charging-touch-test-apk/src/com/codex/lle/SparklingBubblesEffectView.java`

L'ID framework Samsung usato e `0x0f`, cioe `SPARKLING_BUBBLES_TV`.
L'ID `0x0e` e il ramo GL/SufaceView; per overlay trasparente si usa il ramo TV,
come per `ColourDroplet` (`0x11`).

I dati passati a `EffectDataObj.sparklingBubblesData` sono:

- `windowWidth`
- `windowHeight`
- `resBmp`, cioe `R.drawable.n5_sparkling_bubbles_blur_mask`

Il nome della data class Samsung contiene un typo originale:

`com.samsung.android.visualeffect.lock.data.SparklingBullesData`

## Comandi runtime

Il comportamento segue `SPhysicsEffect_TV`:

- `handleCustomEvent(0, map)` aggiorna il background.
  - chiavi: `"Bitmap"` e `"Mode"`
  - il native usa questa bitmap come texture `PortraitBG` / `LandscapeBG`
- `handleCustomEvent(1, map)` mostra l'hint/affordance.
  - chiavi: `"Rect"` e `"StartDelay"`
  - il common handler usa il centro del `Rect`
- `handleCustomEvent(2, map)` invia unlock.
- `handleCustomEvent(3, map)` invia screen off.
- `handleCustomEvent(4, map)` invia screen on.
- `handleTouchEvent(event, effectView)` inoltra i touch al native.

Lo shader native campiona:

- `uMaskMap`, caricato dalla texture `"BlurMask"`
- `uBGTexMap`, caricato dalla bitmap background

Quindi Sparkling Bubbles usa la colormap/screenshot come Colour Droplet, anche
se l'effetto visibile e fatto da particelle alpha.

## Trasparenza

Lo shader principale delle bolle scrive alpha da:

`texture2D(uMaskMap, gl_PointCoord.xy).a * vPointAlpha`

e fa `discard` quando l'alpha e sotto `0.005`. Non e stata applicata una patch
shader nativa alle particelle: le bolle originali hanno gia alpha corretta.

La trasparenza Android e gestita sul child Samsung:

```java
if (view instanceof TextureView) {
    ((TextureView) view).setOpaque(false);
    return;
}
```

Il nero fullscreen era causato dal clear GL native, non dalla `TextureView`.
`SPhysics::SPISceneComponent::clearGLBuffer()` puliva il framebuffer con alpha
1.0. La patch rende il clear trasparente:

```text
0x48084: fe 35 a0 e3 -> 00 30 a0 e3
```

Significato:

```text
mov r3, #1065353216  ; float 1.0 alpha
mov r3, #0           ; float 0.0 alpha
```

In piu `SPSparklingBubblesApp::drawApp()` disegnava il background Samsung
fullscreen tramite il renderer a `[this + 0x28]`. Quelle draw-call sono state
sostituite con NOP ARM (`00 f0 20 e3`), lasciando intatto il caricamento della
texture `PortraitBG` / `LandscapeBG` usata dallo shader delle bolle:

```text
0x4bba8: e5940028 -> e320f000
0x4bbac: ee111a90 -> e320f000
0x4bbb0: ebff334f -> e320f000
0x4bbb4: e5940028 -> e320f000
0x4bbb8: ebff3350 -> e320f000

0x4bc3c: e5940028 -> e320f000
0x4bc40: ee101a90 -> e320f000
0x4bc44: ebff332a -> e320f000
0x4bc48: e5940028 -> e320f000
0x4bc4c: ebff332b -> e320f000
```

Nota importante: non patchare `SPDrawBackground::drawRender()` con `bx lr`.
Quella prova manda in crash il GLThread, perche `SPIRenderer::draw()` continua
e prova a disegnare una mesh non inizializzata:

```text
SPhysics::SPMesh::getNumOfVertex()
SPhysics::SPIRenderer::runMeshDraw()
SPhysics::SPIRenderer::draw()
SPhysics::SPSparklingBubblesApp::drawApp()
```

## Screenshot background

Sparkling Bubbles e registrato tra gli effetti che usano screenshot cached:

- cache: `OverlayPrefs.touchBoxScreenshotFile(context)`
- refresh manuale: pulsante `Refresh effect background map`
- debug: pulsante `View colormap screenshot`

Sparkling Bubbles riusa il cached screenshot come S3/S5/Colour Droplet, senza
forzare una nuova cattura a ogni wake. Il refresh continuo non reggeva bene sul
device e poteva catturare l'hint stesso.

Il wrapper invalida `lastSentBackgroundBitmap` su attach/detach, cosi la bitmap
cached viene rimandata al renderer quando il `TextureView` Samsung ricrea il
contesto native.

Il wrapper non passa piu una bitmap fullscreen "vera" da visualizzare: crea una
color map dal cached screenshot e la invia tramite il path Samsung standard
`handleCustomEvent(0, { Bitmap, Mode })`. La lib la usa come `uBGTexMap`.

La color map viene ora generata con un center-crop diretto dello screenshot alla
dimensione render. Il vecchio passaggio sperimentale a 48 px e il successivo
upscale sono stati rimossi, quindi il renderer Samsung riceve nuovamente una
mappa colore con dettaglio pieno.

Il wrapper audio replica inoltre i parametri SystemUI originali: `SoundPool` da
10 stream, soglie drag di 1100 ms/120 px e fade ogni 10 ms con decremento 0.039
al rilascio e 0.059 allo sblocco. L'handoff verso il PIN conserva i 400 ms
Samsung includendo i 60 ms necessari a rimuovere l'overlay prima del gesto di
accessibilita.

## Asset aggiunti al package

- `charging-touch-test-apk/res/drawable-nodpi/n5_sparkling_bubbles_blur_mask.png`
- `charging-touch-test-apk/res/raw/ve_sparklingbubbles_tap.ogg`
- `charging-touch-test-apk/res/raw/ve_sparklingbubbles_drag.ogg`
- `charging-touch-test-apk/res/raw/ve_sparklingbubbles_lock.ogg`
- `charging-touch-test-apk/res/raw/ve_sparklingbubbles_unlock.ogg`
- `charging-touch-test-apk/native-libs/lib/armeabi-v7a/libSparklingBubblesEffect.so`

## Test eseguiti

Build:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\charging-touch-test-apk\build.ps1
```

Install sul device `RFCW30S277B`:

```powershell
& $adb install -r -d .\charging-touch-test-apk\build\LLE-debug.apk
```

Log confermati:

- `libSparklingBubblesEffect.so` caricata correttamente dal `classes2.dex`
- `JniSparklingBubblesRenderer` registrato via `JNI_OnLoad`
- `SparklingBubblesEffect_TV` e `SparklingBubblesRenderer_TV` istanziati
- `BlurMask` passato al native tramite `setResourcesBitmap1()`
- background cached applicato tramite `changeBackground Mode = 0`
- `sparkling bubbles background sent source=cached_effect_background_colour_map`
- hint inviato e native `affordanceEffect(EVENT_AFFORDANCE)` chiamato

## Stato visuale

Build installata e testata su `RFCW30S277B`: overlay trasparente, niente
fullscreen nero, effetto Sparkling Bubbles visibile sopra la lockscreen e color
map cached agganciata. La verifica visuale dell'utente e "quasi perfetto".

## Stabilita 2026-07-06

I crash residui osservati non venivano dal touch/render, ma dal teardown native:
`libSparklingBubblesEffect.so -> PhysicsEngineJNI::DeInit_JNI` durante
`SPhysicsRenderer_TV.onDestroy()`. La soluzione attiva evita il teardown nei
passaggi transitori lock/AOD/PIN tenendo l'overlay native physics montato e
mandando il comando originale Samsung `handleCustomEvent(99, {"CustomEvent":
"ForceDirty"})` dopo reset/clear. In idle il processo e stato campionato a
`0.0%` CPU con l'effetto caldo; resta un costo memoria/graphics, ma e preferibile
al crash da DeInit.

La correzione finale non usa piu una cache doppia di sessione. Sul cambio
effetto il wrapper spegne ordinatamente le `GLTextureView` Samsung chiamando
`onPause()` e `surfaceDestroyed()` prima di `removeEffect()`. Il crash era dovuto
al `GLThread` Samsung che durante lo shutdown poteva leggere una weak reference
null; con questa sequenza il destroy reale regge e non serve tenere Droplet e
Sparkling entrambi in RAM. Nei passaggi transitori lock/AOD/PIN Sparkling resta
montato invisibile e viene solo mandato in `SCREEN_OFF`/`ForceDirty`, cosi non si
ricostruisce nel normale ciclo lockscreen. Dopo il destroy di un N5 il servizio
richiede una GC ritardata per liberare rapidamente le bitmap temporanee create
durante gli switch manuali/debug. Non patchare `native_DeInit_JNI` nel dex vendor
salvo nuovo test mirato: ridurrebbe i crash al costo di leak nativi a ogni cambio
effetto.
