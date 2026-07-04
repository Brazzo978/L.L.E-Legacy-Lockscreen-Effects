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

N5 usa la cache solo come warm start: l'effetto puo partire subito, ma la cache
viene marcata stale e refreshata appena la lockscreen e stabile. Questo e
necessario perche l'interno della goccia e opaco come nel native Samsung; se
`uBG` e vecchia, dentro la goccia si vede una lockscreen vecchia.

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
