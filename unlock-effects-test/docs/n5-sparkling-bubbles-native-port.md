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
shader nativa iniziale, perche non c'e lo stesso fullscreen opaco evidente del
droplet.

La trasparenza Android e gestita sul child Samsung:

```java
if (view instanceof TextureView) {
    ((TextureView) view).setOpaque(false);
    return;
}
```

Se a test visuale l'effetto dovesse comunque scurire l'intero schermo, il punto
successivo da verificare e il clear/compositing GL native, non la mask delle
bolle.

## Screenshot background

Sparkling Bubbles e registrato tra gli effetti che usano screenshot cached:

- cache: `OverlayPrefs.touchBoxScreenshotFile(context)`
- refresh manuale: pulsante `Refresh effect background map`
- debug: pulsante `View colormap screenshot`

Come per Colour Droplet, l'hint viene tenuto in attesa se manca la bitmap
background o se una cattura e in volo. Questo evita che lo screenshot catturi
l'hint stesso e lo trasformi in artefatti nella colormap.

Il wrapper invalida `lastSentBackgroundBitmap` su attach/detach, cosi la bitmap
cached viene rimandata al renderer quando il `TextureView` Samsung ricrea il
contesto native.

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
- hint inviato e native `affordanceEffect(EVENT_AFFORDANCE)` chiamato

## Gap noto

Il port e agganciato e il native parte. La verifica visuale manuale resta
necessaria per confermare che il compositing sia perfettamente trasparente e
che le bolle siano colorate come sul Note5. Se il rendering dovesse apparire
troppo scuro, la prima ipotesi e il background screenshot o il clear GL native;
la mask `BlurMask` invece e quella originale Note5.
