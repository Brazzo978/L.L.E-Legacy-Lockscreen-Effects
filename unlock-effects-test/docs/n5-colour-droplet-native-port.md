# Note5 Colour Droplet native port

Questo documento descrive come e stato portato l'effetto Samsung Note5
`ColourDroplet` dentro l'overlay dell'app.

## Sorgenti originali

- Native renderer: `extracted/note5_aoj4_system_files/lib/libColourDropletEffect.so`
- Java framework Samsung: `extracted/secvisualeffect_hybrid_dex/classes.dex`
- Smali di riferimento:
  - `extracted/note5_aoj4_secvisualeffect_smali/com/samsung/android/visualeffect/lock/colourdroplet/`
  - `extracted/s5_systemui_smali/com/android/keyguard/sec/effect/KeyguardEffectViewColourDroplet.smali`
- Texture originali:
  - `normal_low_z_256.png`
  - `edge_density_720.png`
- Suoni originali:
  - `ve_colourdroplet_tap.ogg`
  - `ve_colourdroplet_lock.ogg`
  - `ve_colourdroplet_unlock.ogg`

Nel progetto l'app usa la versione ARM32:

`charging-touch-test-apk/native-libs/lib/armeabi-v7a/libColourDropletEffect.so`

## Entry point Samsung

Il renderer non viene ricreato in Java. L'app istanzia il vero
`com.samsung.android.visualeffect.EffectView` tramite reflection e seleziona
l'effetto Samsung `0x11`, cioe `ColourDropletEffect_TV`.

Il wrapper locale e:

`charging-touch-test-apk/src/com/codex/lle/ColourDropletEffectView.java`

I dati passati a `EffectDataObj.colorDroplet` sono:

- `windowWidth`
- `windowHeight`
- `resNormal`
- `resEdgeDensity`

Dopo `setEffect(0x11)`, viene chiamato `init(data)`.

## Comandi runtime

Il comportamento e allineato al flusso Samsung:

- `handleCustomEvent(0, map)` aggiorna il background.
  - chiavi: `"Bitmap"` e `"Mode"`
  - lo screenshot lockscreen diventa la texture shader `uBG`
- `handleCustomEvent(1, map)` mostra l'hint/affordance.
  - chiavi: `"Rect"` e `"StartDelay"`
  - nella classe Samsung `SPhysicsEffect_TV` lo `StartDelay` viene passato ma
    il renderer usa la propria logica interna di delay.
- `handleCustomEvent(2, map)` invia l'evento di unlock.
- `handleCustomEvent(3, map)` invia screen off.
- `handleCustomEvent(4, map)` invia screen on.
- `handleTouchEvent(event, effectView)` inoltra i touch al native.

## Overlay trasparente

Il renderer Samsung originale e pensato per SystemUI: disegna un frame intero
opaco contenente sia lockscreen sia effetto. Nell'app invece il renderer vive
in un overlay Android sopra la lockscreen reale, quindi il buffer deve poter
essere trasparente.

Il child Samsung e un `TextureView`; per questo non si puo usare
`setBackgroundColor()`. Su `TextureView` puo causare
`UnsupportedOperationException` e non risolve il problema dell'opacita GL.

La soluzione Java e:

```java
if (view instanceof TextureView) {
    ((TextureView) view).setOpaque(false);
    return;
}
```

Questo rende il buffer compositabile con alpha sopra la lockscreen.

## Patch shader native

Il fragment shader originale Note5 sta dentro `libColourDropletEffect.so`,
offset file `0x5c714`. La stringa originale e lunga circa 10 KB.

Lo shader originale fa:

- fuori dalla goccia: campiona `uBG` e scrive RGB dello sfondo con alpha `1.0`
- dentro la goccia: calcola density, normal, refraction e colore usando
  `uDensity` e `uColorNDirection`, poi scrive alpha `1.0`

In SystemUI questo e corretto, perche il pass finale ricostruisce tutta la
schermata. In overlay invece coprirebbe o scurirebbe tutta la lockscreen.

La patch mantiene il calcolo Samsung dentro la goccia, ma cambia solo il
compositing finale:

- fuori dalla goccia: `gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0)`
- ombra esterna: nero con alpha equivalente al darkening originale
- dentro la goccia: colore/refraction Samsung originale con alpha `1.0`

Questa scelta evita di blendare parzialmente l'interno della goccia, cosa che
lo rendeva piu scuro del Note5 originale.

## Screenshot background

Gli effetti S3, S5 e N5 usano una bitmap lockscreen catturata dopo il wake.
Per N5 la bitmap e passata al native come `uBG`; serve alla rifrazione e al
colore interno della goccia.

La cache e caricata da:

`OverlayPrefs.touchBoxScreenshotFile(context)`

La UI contiene il pulsante `View colormap screenshot`, utile per controllare
se la bitmap usata dal native e luminosa, aggiornata e allineata.

N5 usa la cache come sorgente stabile. Il refresh automatico a ogni wake e stato
evitato perche su device reali il costo degli screenshot periodici e troppo
alto; quando serve aggiornare `uBG`, si usa il refresh manuale/debug della
colormap.

Quando una cattura N5 e necessaria o gia in volo, l'hint dell'effetto resta
pendente fino a quando la bitmap e pronta. Questo evita che lo screenshot
includa l'hint stesso e poi lo trasformi in una zona bianca dentro la goccia.

Il `TextureView` Samsung puo distruggere e ricreare il contesto native quando
l'overlay viene rimosso e riattaccato. Il wrapper invalida quindi il marker
`lastSentBackgroundBitmap` su attach/detach, cosi la bitmap cached viene
rimandata al nuovo renderer anche se il contenuto non e cambiato.

## Asset aggiunti al package

- `charging-touch-test-apk/res/drawable-nodpi/n5_colour_droplet_normal.png`
- `charging-touch-test-apk/res/drawable-nodpi/n5_colour_droplet_edge_density.png`
- `charging-touch-test-apk/res/raw/ve_colourdroplet_tap.ogg`
- `charging-touch-test-apk/res/raw/ve_colourdroplet_lock.ogg`
- `charging-touch-test-apk/res/raw/ve_colourdroplet_unlock.ogg`
- `charging-touch-test-apk/native-libs/lib/armeabi-v7a/libColourDropletEffect.so`

## Build e test rapidi

Build:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\charging-touch-test-apk\build.ps1
```

Install:

```powershell
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
& $adb install -r -d .\charging-touch-test-apk\build\LLE-debug.apk
```

Log utili:

```powershell
& $adb logcat -d -v threadtime | Select-String -Pattern 'ChargingColourDroplet|ColourDropletRenderer_TV|S-Physics|JniColourDroplet|Error compiling shader|AndroidRuntime'
```

## Gap noto

Il port usa il native Note5 reale e l'effetto e funzionante su overlay
trasparente. La differenza residua rispetto a SystemUI e il layer sorgente:
Samsung passa il wallpaper tramite `getCurrentWallpaper`, mentre l'app passa
uno screenshot lockscreen per poter lavorare fuori da SystemUI. Per questo la
rifrazione puo includere clock/testi se sono presenti nello screenshot.

Il wrapper audio replica i parametri SystemUI originali: `SoundPool` da 10
stream e riproduzione subordinata a `lockscreen_sounds_enabled` e al volume
dello stream di sistema. L'handoff verso il PIN conserva i 400 ms Samsung
includendo i 60 ms necessari a rimuovere l'overlay prima del gesto di
accessibilita.

## Variante accelerometro

Il native conserva una funzione non collegata dal renderer TV Samsung:
`SPColourDropletApp::onEventSensor`. La funzione converte l'inclinazione nei
campi di accelerazione SPH con fattori `-x * 0.01` e `-y * 0.015`; il flag
interno che abilita questo calcolo e gia attivo nel costruttore originale.

Il picker espone il bridge come effetto separato `N5 Colored Droplet + Gyro`,
mentre `N5 Colored Droplet` non registra alcun sensore. La variante usa un bridge
riflessivo verso `JniColourDropletRenderer.onSensorEvent()`. Registra
l'accelerometro con `SENSOR_DELAY_GAME`, applica clamp `[-10, 10]` e rotazione
display identici a `SPhysicsRenderer_TV`, e si disiscrive allo screen-off. Il
dex Samsung e la libreria nativa non vengono modificati.

## Stabilita 2026-07-06

Come Sparkling Bubbles, Colour Droplet usa `SPhysicsEffect_TV` e puo cadere se il
teardown native entra in `PhysicsEngineJNI::DeInit_JNI` mentre il `GLTextureView`
si sta chiudendo. Il wrapper ora tiene l'overlay native physics montato nei
passaggi transitori e usa il comando Samsung `ForceDirty` dopo reset/clear per
portare il renderer in dirty mode. Inoltre `warmUp()` prepara solo la bitmap
background e non manda piu `SCREEN_ON`: il motore viene riattivato solo su hint o
touch reale. Test ADB: `colour droplet force-dirty sent`, nessun crash buffer
fresco nel cambio immediato.

La correzione definitiva non e un retain temporizzato: nel cambio effetto il
wrapper spegne ordinatamente le `GLTextureView` Samsung chiamando prima
`onPause()` e `surfaceDestroyed()`, poi invoca `removeEffect()`. Il vecchio crash
era causato dal `GLThread` Samsung che leggeva una weak reference null durante lo
shutdown; tenendo vivo il view object fino alla pausa/surface destroy, il destroy
reale diventa stabile. Nei passaggi transitori lock/AOD/PIN invece il renderer
resta montato invisibile e viene solo portato in `SCREEN_OFF`/`ForceDirty`, cosi
il normale uso lockscreen non paga ricostruzioni continue. Dopo il destroy di un
N5 il servizio richiede una GC ritardata per liberare rapidamente le bitmap
temporanee create durante gli switch manuali/debug.
