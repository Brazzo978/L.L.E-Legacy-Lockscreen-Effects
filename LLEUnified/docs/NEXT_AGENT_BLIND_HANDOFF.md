# L.L.E. Blind — handoff operativo per il prossimo agente

Data: 2026-07-17. Repository attivo: `D:\New project\LLEUnified`, branch
`codex/lle-unified`, HEAD osservato `297d65b`. Leggere prima `AGENTS.md`.

## Obiettivo e ordine obbligatorio

Integrare **Tab S Blind** in L.L.E. con fedeltà massima, in questo ordine:

1. rendere selezionabile e validare l'implementazione DEX originale nella build
   ARM32;
2. acquisire un oracle visivo/temporale affidabile e correggere host/compositing;
3. solo dopo abilitarla e validarla nelle build successive, ARM64 inclusa;
4. non dichiarare 1:1 senza confronto frame-aligned con un riferimento stock.

Il punto più importante: **Blind non è, per quanto verificato, un motore native
`.so`**. `EffectView(10)` istanzia `BlindEffect`, un renderer Java/View contenuto
nel DEX. “Prima sulla nativa 32 bit” significa prima nella variante L.L.E. ARM32
usando il DEX legacy come oracle, non tradurre C/ARM assembly. Se il DEX funziona
anche nel processo ARM64, non inventare un port GLES/native non necessario.

## Stato attuale: cosa esiste e cosa manca

- Slot L.L.E.: `OverlayPrefs.EFFECT_TABS_BLIND_WIP = 11`, label `TabS Blind (WIP)`.
- È nascosto: `EffectAvailability.isAvailable()` restituisce `false` per lo slot.
- Non esiste ancora un branch renderer in `ChargingAccessibilityService`.
- Non esiste uno wrapper Blind nel tree attivo.
- Il DEX che contiene Blind è **già impacchettato come `classes2.dex` in entrambe
  le build** tramite `vendor/secvisualeffect/classes.dex` e
  `patch-note5-lifecycle.ps1`.
- Non serve aggiungere una libreria ABI per il primo probe.
- Il tree è molto dirty per lavori validi su altri effetti: non fare reset,
  checkout distruttivi, pulizie globali o riscritture meccaniche.

## Oracle e sorgenti, in ordine di autorità

1. **Firmware stock Tab S T705** — oracle finale:
   `D:\New project\unlock-effects-test\tabs\AP_T705XXU1ANF8_2085145_REV00_user_low_ship.tar.md5`
   (2,235,668,562 byte; contiene `boot.img`, `recovery.img`, `system.img`).
   Estrarre da `system.img` almeno `secvisualeffect.jar`, SystemUI/keyguard,
   `secvisualeffect-res.apk` e asset Blind. Il tool locale disponibile è
   `D:\New project\unlock-effects-test\tools\simg2img_min.py`.
2. **L.S.E. ARM32** — primo oracle eseguibile, ma mai validato visivamente
   dall'utente per Blind:
   `D:\New project\unlock-effects-test\demo-apk\build\installed-lse-base.apk`;
   package `com.codex.s4unlockfx`, version `0.1`, ABI `armeabi-v7a`, SHA-256
   `AAF7C701FD4AE7C6E5D1D9C8F4BEB4F22CBC5AABD4000F17BAE1F6AE8400DBF1`.
   È già installato sullo S23. Launcher:
   `com.codex.s4unlockfx.EffectSelectorActivity`. Blind è il quarto elemento
   della lista LSE (`mode_index=3`, Samsung effect ID `10`).
3. **DEX LSE decompilato**, riferimento read-only:
   `D:\New project\unlock-effects-test\_decompiled_old_native32\sources\com\samsung\android\visualeffect\lock\blind\`
   e host:
   `...\sources\com\codex\s4unlockfx\OriginalSamsungEffectHost.java`.
   Il `classes2.dex` di LSE ha SHA-256
   `7769FD7558858D7C26A859B0BAF729DD94F6BDA594B19DA03946B20BF9623099`.
4. **DEX attivo L.L.E.**:
   `vendor/secvisualeffect/classes.dex`, SHA-256
   `206265D2719C5223E57412871B2B778DC56A088300B52B1FEDEB548BFB7EEDB0`.
   Lo smali generato da una build ARM32 è in
   `build/armeabi-v7a/smali_secvisualeffect_lle/.../lock/blind/`; è output di
   build, non va modificato direttamente. Se serve una patch DEX, renderla
   deterministica in `vendor/secvisualeffect/patch-note5-lifecycle.ps1`, con
   hash/needle/verifica, lasciando immutato il DEX sorgente.

I due DEX hanno hash diversi: non assumere che LSE e il DEX attivo siano
identici. Fare diff del solo package Blind e delle dipendenze prima di attribuire
una divergenza visiva all'host.

## Contratto DEX già verificato

Catena: `EffectView.setEffect(10)` -> `InnerViewManager.getInstance(...,10)` ->
`new BlindEffect(context)`. Nessun JNI/native load è visibile in questa catena.

Ordine di inizializzazione necessario:

1. creare `EffectView`;
2. `setEffect(10)`;
3. `handleCustomEvent(0, {"background": Bitmap, "light": Bitmap})`;
4. solo allora `init(EffectDataObj(effect=10))`.

Blind usa i bitmap già in `init()`: invertire 3 e 4 può causare NPE o scena vuota.
I nomi delle chiavi sono esatti e diversi dai LockBG (`Bitmap`) già ospitati da
`SamsungLockBgEffectView`.

Eventi:

- touch: inoltrare `MotionEvent` con coordinate raw coerenti con lo schermo;
- hint: `cmd=1`, chiavi `StartDelay: Long`, `Rect: Rect`;
- unlock: `cmd=2`, chiave `unlock`; nel DEX LSE decompilato `unlockEffect()` è
  vuoto, quindi non inventare comportamento finché firmware/SystemUI stock non
  chiariscono il lifecycle;
- durata unlock: `cmd=2`, chiave `unlockDelay: Long`;
- lifecycle: `cmd=3` con `onConfigurationChanged`, `show` o `destroy`;
- reset: `clearScreen()`.

`BlindMaskEffect` esiste (10 maschere, 400 ms, `CubicEaseIn`, start delay
scaglionato), ma nel `BlindEffect` ispezionato il campo non viene costruito e
l'unlock è no-op. Trattarlo come codice dormiente finché l'oracle stock non
dimostra il contrario.

## Fisica/visuale recuperata dal DEX

- Wallpaper center-crop in due bitmap: landscape `longWidth x shortWidth` e
  portrait `shortWidth x longWidth`.
- Landscape: 40 strip verticali; portrait: 25. Ogni strip è un `ImageView` con
  la propria fetta del bitmap e pivot/posizione Android standard.
- Light: bitmap scalato a un quadrato `stageWidth/2`, disegnato da
  `ImageViewBlended` con Porter-Duff `ADD`.
- DOWN: `animationValue` 0.3 -> 1, `QuintEaseOut`, 200 ms; alpha light =
  `animationValue * 0.15`.
- Tracking: `ValueAnimator` lineare infinito da 3,600,000 ms usato come clock;
  per ogni frame `point += (current-point)*0.17` su X/Y e sul secondo fronte X.
- Influenza strip:
  `d=max(0,(stageWidth/(landscape?8:5)-abs(stripMidX-pointX))/1000)`.
- Scala: landscape `1 + animationValue*d`; portrait
  `1 + animationValue*d*0.625`.
- Brightness RGB offset: `animationValue*d*200` tramite `ColorMatrix`.
- MOVE aggiorna `currentX/currentY`; la direzione orizzontale imposta `isRight`.
- UP/CANCEL: 1 -> 0, `QuintEaseOut`, 1000 ms; i due fronti X vengono spinti in
  direzioni opposte di `(1-animationValue)*50` per callback.
- Hint: centro del `Rect`; DOWN dopo `StartDelay`, UP 100 ms dopo.
- Clear: annulla animatori, scala strip a 1, rimuove filtri, alpha light a 0.

Non convertire il coefficiente `0.17` in una costante “per secondo” prima di
misurare la cadence stock: è frame-coupled. Su 120 Hz potrebbe accelerare come
già successo con altri effetti; prima acquisire LSE/stock a 60/120 Hz, poi
normalizzare solo se i dati lo richiedono.

Interpolatori legacy usati: `QuintEaseOut`, `QuadEaseIn`, `CubicEaseIn` sotto
`android.view.animation.interpolator`. Se mancano sul sistema moderno, sostituire
solo dopo aver confermato il `ClassNotFoundException`, con curve esatte:
`1-(1-t)^5`, `t^2`, `t^3`.

## Rischio principale: compositing, non ABI

Stock Blind trasforma il **wallpaper dietro la UI**. L.L.E. normalmente possiede
una cattura completa del lockscreen: passarla integralmente alle strip può
congelare, duplicare o deformare clock/notifiche. Inoltre a riposo le strip
ricostruiscono un frame opaco completo. LSE, essendo un host fullscreen, non è
un riferimento sufficiente per la trasparenza dell'overlay Accessibility.

Quindi separare sempre due problemi:

1. fedeltà della fisica DEX (prima baseline ARM32 fullscreen/LSE);
2. composizione L.L.E. (wallpaper pulito, maschera/delta trasparente o altra
   soluzione provata). Non “risolvere” il secondo alterando fisica, numero di
   strip o curve.

Il `light` generato da LSE in `createBlindLightBitmap()` è un gradiente sintetico
di compatibilità, **non è ancora verificato come asset stock**. Recuperare prima
l'asset/configurazione Tab S da firmware/SystemUI; non fare tuning 1:1 sul
gradiente LSE.

## Piano di lavoro minimo

### A. Baseline ARM32

1. Acquisire video LSE Blind: hint, tap fermo, drag lento/rapido a sinistra e
   destra, release senza unlock, unlock; almeno 60 fps e logcat `BlindEffect`.
2. Verificare/diffare Blind nel DEX LSE, DEX L.L.E. e firmware Tab S.
3. Creare preferibilmente uno wrapper dedicato `BlindDexEffectView` che implementi
   `UnlockEffectRenderer` + `BackgroundSourceRenderer`; non forzare Blind dentro
   `SamsungLockBgEffectView`, che assume chiave `Bitmap`, audio Abstract/Geometric
   e lifecycle LockBG native.
4. Collegare slot 11 in `ChargingAccessibilityService`, picker e
   `EffectAvailability`; inizialmente disponibile **solo nel processo ARM32**.
5. Inviare `background/light` prima di `init`, inoltrare touch/hint/reset/destroy,
   mantenere fallback sicuro a Lens Flare su ogni errore.
6. Tenere la feature WIP/Beta finché compositing e oracle stock non sono chiusi.

### B. Gate ARM32

- Nessun crash/NPE/ANR per 20 cicli wake-touch-release e 20 unlock.
- PID stabile; nessun respawn scambiato per successo.
- Hint una volta per wake, nessun input invertito, entrambe le direzioni corrette.
- Nessun frame nero, screenshot stantio permanente o UI deformata a riposo.
- Misurare tempi reali DOWN/UP, cadence e RAM/CPU; catture a 0/50/100/200/400/
  700/1000 ms.
- Confronto frame-aligned con LSE e poi Tab S stock; annotare ciò che è oracle e
  ciò che è adattamento host.

### C. ARM64 e build successive

Blind è DEX/Canvas: provare prima lo **stesso wrapper e lo stesso DEX** nel
companion ARM64. Se funziona, il “port ARM64” è solo validazione/integration e
non una riscrittura. Ricostruire solo il minimo incompatibile e documentare ogni
divergenza. Abilitare ARM64 in `EffectAvailability` solo dopo lo stesso gate.

Ogni modifica a Java/resource/picker/lifecycle condiviso richiede entrambe:

```powershell
.\build-arm32.ps1
.\build-arm64.ps1
```

APK: `build\armeabi-v7a\LLE-armeabi-v7a-debug.apk` e
`build\arm64-v8a\LLE64-arm64-v8a.apk`.

## Dispositivi e stato da preservare

Al momento della consegna è connesso solo S23 Ultra:

- seriale `RFCW30S277B`, modello `SM-S918B`;
- `com.codex.lle` installata come `armeabi-v7a`;
- target ARM64 permanente: `com.codex.lle64`, launcher `LLE64`;
- il vecchio `com.codex.lle.arm64dev` può essere ancora installato soltanto
  durante la migrazione e non deve essere usato come identità futura;
- `com.codex.s4unlockfx` LSE ARM32 installata;
- Note 4 ARM32/root è un oracle disponibile quando l'utente lo ricollega, ma non
  ospita necessariamente Blind stock; il riferimento stock corretto è Tab S.

Non modificare risoluzione, refresh, PIN/sicurezza o servizi non coinvolti.
Stato Accessibility da preservare esattamente:

```text
com.codex.lle/com.codex.lle.ChargingAccessibilityService:com.x8bit.bitwarden/com.x8bit.bitwarden.Accessibility.AccessibilityService:com.codex.lle64/com.codex.lle.ChargingAccessibilityService
accessibility_enabled=1
```

## Regole di consegna

- Codice solo in `LLEUnified`; `LLE64` e `unlock-effects-test/charging-touch-test-apk`
  sono riferimenti congelati.
- Non modificare output `build/` come sorgente e non editare il DEX a mano.
- Non toccare effetti funzionanti per accomodare Blind.
- Aggiungere `ports/blind/` con `reference/`, `reverse/`, `tests/results/` e un
  transcript che separi fatti DEX, osservazioni visive e adattamenti L.L.E.
- Non fidarsi del solo screenshot: Blind è movimento, easing e compositing.
- Prima di dichiarare completato: build doppia, `git diff --check`, test su device,
  hash APK/DEX, log PID/crash/GLES/ANR e confronto visivo dell'utente.

## Primo messaggio operativo consigliato al prossimo agente

> Non implementare ancora ARM64. Leggi `AGENTS.md` e questo handoff; acquisisci
> Blind da LSE ARM32, diffalo col DEX attivo e recupera dal firmware T705 asset e
> wiring SystemUI. Poi integra il DEX originale come WIP nella sola build ARM32,
> senza tuning a occhio e senza alterare gli altri effetti. Solo dopo un baseline
> stabile affronta compositing e validazione ARM64.
