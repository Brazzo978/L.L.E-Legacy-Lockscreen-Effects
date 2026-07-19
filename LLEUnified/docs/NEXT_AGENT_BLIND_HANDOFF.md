# L.L.E. Blind — handoff operativo per il prossimo agente

Data: 2026-07-17. Repository attivo: `D:\New project\LLEUnified`, branch
`codex/lle-unified`, HEAD osservato `297d65b`. Leggere prima `AGENTS.md`.

## Aggiornamento operativo autoritativo - 2026-07-17 (S23)

Questa sezione descrive lo stato reale corrente e sostituisce, per le decisioni operative, lo stato iniziale conservato piu' sotto come cronologia. Il repository attivo e' `E:\New project\LLEUnified`; HEAD osservato `621c44d`, con Blind integrato in entrambe le ABI. Non dichiarare il port "1:1 certificato": ARM32 ha una baseline funzionale confermata dall'utente e ARM64 ha superato preload/background nel processo `arm64-v8a`; il test visuale live ARM64, l'import manuale end-to-end e il confronto frame-aligned con un oracle stock affidabile restano da chiudere.

### Reverse engineering e provenienza

- Progetto Ghidra: `E:\New project\LLEUnified\ports\blind\reverse\ghidra-project\LLE_Blind.gpr`.
- Programma analizzato con Ghidra 12.1.2, linguaggio Dalvik `DEX_KitKat`: `/secvisualeffect-tabs-t705-optimized.dex`.
- DEX estratto dall'ODEX del firmware stock Tab S T705 ANF8: `ports\blind\reverse\secvisualeffect-tabs-t705-optimized.dex`, 148524 byte, SHA-256 `9C6CDA5DB6650D1C04E749D1EDF97EF207D65931476771C515DC53FD00493A99`.
- Il package stock antico e' `com.sec.android.visualeffect.blind`; il port LSE usa `com.samsung.android.visualeffect.lock.blind`. API shell e package non sono byte-identici allo stock, anche se le formule visibili centrali corrispondono. Non usare quindi "bitwise stock" come claim.

### Identita' del package Blind importato da LSE

- Oracle LSE: `ports\blind\reference\lse-arm32\classes2.dex`, 297748 byte, SHA-256 `7769FD7558858D7C26A859B0BAF729DD94F6BDA594B19DA03946B20BF9623099`.
- Base vendor: `vendor\secvisualeffect\classes.dex`, 297940 byte, SHA-256 `206265D2719C5223E57412871B2B778DC56A088300B52B1FEDEB548BFB7EEDB0`.
- Il confronto iniziale sul solo package `com/samsung/android/visualeffect/lock/blind` era 14/14 byte-identico a LSE. Dal 2026-07-18 il build applica intenzionalmente una patch trasparenza al solo `BlindEffect.smali`: gli altri 13 file restano identici; `clearEffect()` azzera l'alpha dei 40+25 listelli e `setScale()` pubblica alpha 1/0 nello stesso update che cambia la scala. Non descrivere quindi il package finale come byte-identico a LSE.
- Sia il `classes2.dex` ARM32 finale sia quello ARM64 finale contengono tutte le 14 definizioni Blind.

### Asset stock T705

Le sorgenti di riferimento sono in `ports\blind\reference\tabs-t705-anf8\keyguard-assets`; le copie attive in `res` devono mantenere questi valori:

| Asset | Dimensioni / byte | SHA-256 |
| --- | ---: | --- |
| `keyguard_blind_light.png` | 160x159, 9269 byte | `FF4CF8A9F7FC567B2882692C40D58ACFE9E8131F4BC7621AE8BF1AF2D5F65E73` |
| `blind_touch.ogg` | 73119 byte | `C7CF71C9F5DFE884316A56D0D13A607A05EE25825DA10F1C5C529AA836FBD795` |
| `blind_unlock.ogg` | 67390 byte | `EB1B8615AE5C0C740EB4710B2B31713DAB7AAB9202ED307E13E36AA3CD7F2907` |
| `blind_lock.ogg` | 29724 byte | `60303626E858B2E7F83493C1372BE7AEB8F7B56DD8C0B8548DC96A064AB44AAD` |

I suoni touch/unlock della demo LSE sono byte-identici agli stock. `blind_lock.ogg` e' conservato, ma lo stock `KeyguardEffectViewBlind.playLockSound()` e' un no-op e il renderer touch non lo usa. La light della demo LSE era un gradiente sintetico full-screen; il wrapper attivo usa correttamente il PNG stock. Per questo la light della vecchia demo LSE non e' un oracle visuale esatto finche' la demo non viene aggiornata.

### Wrapper, compatibilita' e ownership bitmap

- `src\com\codex\lle\BlindDexEffectView.java` implementa `UnlockEffectRenderer` e `BackgroundSourceRenderer` per l'effetto id 10.
- L'ordine di init e' quello richiesto: creazione `EffectView`, `data.setEffect(10)`, `setEffect(10)`, command 0 con background/light, quindi `init`.
- Il wrapper inoltra coordinate raw, hint, reset/destroy e suoni stock. Dal 2026-07-18 non rende piu' opaco l'intero layer Samsung al primo `DOWN`: raccoglie i 65 `Blind` (40 landscape + 25 portrait) e, a ogni pre-draw, lascia alpha 0 sui listelli con scala neutra `1.0`, mostrando solo quelli che il DEX sta deformando. Il root resta alpha 0 in idle e viene nascosto dopo il release.
- Dopo che il solo pre-draw wrapper si e' dimostrato insufficiente sul test visuale, `vendor\secvisualeffect\patch-note5-lifecycle.ps1` applica anche la correzione dentro il DEX. Sono aggiunti esattamente tre call-site `Blind.setAlpha`: reset landscape, reset portrait e alpha atomico in `setScale()`. Gli hash bounded obbligatori sono `9CE97D2B157A188E71EA9780B35AF72A2FB83AD1D72FD52F9A47EE3329D02AC1` (package ARM32) e `339A2546735F4E6A76D9BBE5BC8AD413E607F57F0347D0EBA65D4A7C7D014734` (package ARM64). Il DEX vendor sorgente non viene modificato.
- Compat interpolators presenti: `CubicEaseIn`, `CubicEaseOut`, `QuadEaseIn` e `QuintEaseOut`. `QuadEaseIn` e `QuintEaseOut` sono obbligatori gia' durante `setAnimator`, non opzionali. Entrambi i DEX principali finali espongono tutte e quattro le classi, verificato con `baksmali list classes`.
- `sendBackgroundBitmap` non passa piu' il master condiviso/retained al DEX Samsung. Crea una copia `ARGB_8888`, invia quella al command 0 e la ricicla nel `finally` se il DEX non l'ha gia' consumata. Questo isola i recycle interni di Blind dal cache master ed evita sia use-after-recycle sia leak quando il DEX crea copie scalate.
- Il `classes.dex` principale e' identico nei due build correnti: 551288 byte, SHA-256 `CD05DE414A062268C990EA1F297B98ECEB4C7678B6C38C4BD5FC6AB180DAA35B`.

### Stato test ARM32 e ARM64

- ARM32: preload/background e test visuale live riusciti sul Samsung S23 Ultra SM-S918B, confermati dall'utente. E' la baseline funzionale corrente, non ancora una certificazione 1:1 frame-aligned.
- ARM64: gate statico superato sul package Blind e sulla dependency closure (`EffectView`, `InnerViewManager`, `EffectDataObj`, interfacce e `ImageViewBlended`): nessun metodo `native`, JNI, `System.loadLibrary`, `Runtime.load`, literal `.so`, controllo ABI, GL, RenderScript o DVFS. L'id 10 costruisce solo Blind e non esiste una libreria `.so` Blind; le native library nell'APK appartengono ad altri effetti.
- Lo slot Blind e' ora abilitato anche dal gate ARM64 in `EffectAvailability`. Il build ARM64 e' stato installato sullo S23 e il profilo diagnostico ha caricato il DEX nel processo `arm64-v8a`, inviato il background 1440x3088, inizializzato la light 160x159 e completato preload/attach senza fallback, JNI/linker error o classi mancanti. Restano da validare picker e gesto visuale live nella UI lockscreen.
- Backup recuperabile dell'APK ARM64 installato prima di Blind: `ports\blind\tests\baseline-installed-arm64-before-blind-20260717.apk`, 14235992 byte, SHA-256 `CB713BF994DA8C23E0897492172F14BC6E752B8E1803881F9B65187399B298BE`.

### Background manuale EXTRA / Beta

- `src\com\codex\lle\ManualEffectBackground.java` e la voce ControlActivity `EXTRA / Beta - Manual renderer background` forniscono una sorgente full-frame sperimentale per gli effetti basati su screenshot.
- L'utente importa il wallpaper esatto per effetto e profilo display attivo; il file viene copiato nello storage privato con versione immutabile. `Reset to Auto` cambia la preferenza ma lascia i file recuperabili: non cancella nulla.
- L'immagine viene center-croppata al display attivo. Il bitmap resta full-frame come sorgente interna del DEX, ma il wrapper Blind applica una maschera dinamica ai listelli: a schermo compaiono solo le porzioni che Samsung sta deformando. Un wallpaper importato pulito evita comunque che clock/notifiche finiscano dentro i listelli attivi.
- La cattura screenshot automatica viene sospesa solo mentre e' attiva la modalita' Imported. Il percorso UI/runtime di import deve ancora essere provato end-to-end sia ARM32 sia ARM64.

### Manifest dei build finali correnti

| Target | Artefatto | Byte | SHA-256 |
| --- | --- | ---: | --- |
| ARM32 APK | `build\armeabi-v7a\LLE-armeabi-v7a-debug.apk` | 14633621 | `0DFB440334EBE3D0AEBFA539CB1847213B47C418ADEBA93733BD06C08A27B66D` |
| ARM32 `classes2.dex` | dentro l'APK | 298548 | `C556329950C4AE5F1E088018A9DC7A7A936F8E1C76359C39D4D3B19C200CDB0A` |
| ARM64 APK | `build\arm64-v8a\LLE64-arm64-v8a.apk` | 14400066 | `C907DEB97DF2A13D5BF9BED608E322B6C272D474346A749245D0DEE48A89C249` |
| ARM64 `classes2.dex` | dentro l'APK | 298324 | `339A2546735F4E6A76D9BBE5BC8AD413E607F57F0347D0EBA65D4A7C7D014734` |

Build ricompilati e reinstallati sul solo S23 il `2026-07-18`. I due `classes2.dex` hanno hash complessivi diversi per patch package/resource esterne a Blind; entrambi includono la medesima patch logica interaction-only sui listelli.

### Aggiornamento Fold e Direct wallpaper - 2026-07-18

- L'utente ha completato anche il test visuale ARM64 live sullo S23: Blind e la
  maschera interaction-only funzionano correttamente. Il vecchio gate visuale
  ARM64 ai punti successivi resta come cronologia, ma non e' piu' aperto.
- Controprova runtime avviata sul solo Fold `SM-F966B` / `q7q`, seriale
  `RFCY70WM0JA`, ABI `arm64-v8a`. Non toccare il Note 4 `44d857ce`.
- Sulla cover chiusa il target risolto e' `cover`, `1080x2520`; il profilo debug
  Blind ha costruito 65 listelli, inviato background e light stock e terminato
  `status=ok`, senza fallback o errore di verifica. Il pannello interno rilevato
  e' `main`, `1968x2184`, ma la controprova live interna attende l'apertura fisica.
- Il controllo `Renderer wallpaper` e' ora prima della lista effetti. La sezione
  `EXTRA / Beta - Direct wallpaper` offre sorgenti indipendenti Cover/Main anche
  se uno dei pannelli e' inattivo, usando le dimensioni reali di ciascun display.
- In `DIRECT` il file scelto viene copiato nello storage privato, center-croppato
  alla risoluzione del pannello e passato direttamente al
  `BackgroundSourceRenderer`; la cattura della lockscreen resta sospesa. Tornare
  ad `AUTO` riattiva lo screenshot senza eliminare la copia importata.
- Build condivisi riusciti dopo la nuova UI: ARM32 APK SHA-256
  `FAEA352333D4001DED7041432996EFF5CE8CB067E64D773A4157EE76D719EC26`;
  ARM64 APK SHA-256
  `0745FAA94D9BBE2FB4EE4D48875D32C60B76617D0681331E77ABCD07F9AC4B7D`.
  Il secondo e' installato sul solo Fold. Accessibility e' rimasta abilitata
  esclusivamente per `com.codex.lle64` su quel device.
- Il resolver `ACTION_OPEN_DOCUMENT image/*` del Fold trova correttamente Google
  DocumentsUI. La selezione end-to-end non e' stata automatizzata per non scegliere
  un file dell'utente: la UI attende sblocco tramite segno e scelta manuale.

### Gate ancora aperti prima del claim 1:1

1. Eseguire il test visuale ARM64 completo: picker, touch/release/unlock e ritorno idle. Preload/background/attach runtime sono gia' passati.
2. Provare l'import manuale end-to-end su entrambe le ABI, inclusi persistenza, center-crop e `Reset to Auto` senza cancellazione.
3. Acquisire un oracle stock Tab S affidabile e fare confronto frame-aligned di geometria, alpha, timing, luce, compositing PorterDuff e audio.
4. Verificare visivamente il compositing moderno interaction-only: nessun flash full-screen al primo `DOWN`, listelli neutri trasparenti e soli listelli deformati visibili durante down/move/up.

Fino alla chiusura di questi gate, descrivere Blind come **basata sul DEX LSE e sugli asset stock T705**, con ARM32 funzionale confermata e ARM64 compatibile e validata in preload/runtime, ma non ancora confermata visivamente nella UI live.

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

## Stato iniziale storico (superato dall'aggiornamento operativo)

La sezione seguente e' mantenuta senza cancellazioni per ricostruire il punto di partenza. Non usarla come fotografia del build corrente.

- Slot L.L.E. iniziale: effect id `11`; l'etichetta pubblica corrente e' `Tab S Blind`.
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

## Stato app e setup wizard (2026-07-18)

La schermata Lockscreen condivisa ARM32/ARM64 usa ora questo ordine:

1. `Unlock effect` e relativi toggle/suoni;
2. lista `Effects` per l'ABI corrente;
3. `Setup & permissions`, routing Fold quando applicabile e controlli della
   sorgente wallpaper/screenshot.

Il first-launch wizard e' interamente in inglese e contiene quattro step:
Accessibility, battery exemption, wallpaper source e feature selection. Le
quattro scelte finali impostano rispettivamente:

1. charging doodle soltanto;
2. lockscreen effect soltanto;
3. charging doodle + lockscreen effect;
4. charging doodle + lockscreen effect + charging companion effect.

La scelta finale scrive `MASTER_ENABLED=true` e la combinazione richiesta di
`SHOW_DOODLE`, `UNLOCK_EFFECT_ENABLED` e `SEASONAL_UNLOCK_PARTNER`. Il lancio
`sourceOnly` cambia soltanto la sorgente wallpaper e non tocca queste feature.

Build condivise verificate il 2026-07-18:

```text
ARM32 APK  F68A37BF16BFBBADC13B858609CB02173C37D52B0F2EA6BBD788563FF248B82C
ARM64 APK  854A1B8E09488B270A4E578B03F76EFD81027070DF60EE76CDEE9A60019EFED4
ARM32 DEX  9CE97D2B157A188E71EA9780B35AF72A2FB83AD1D72FD52F9A47EE3329D02AC1
ARM64 DEX  339A2546735F4E6A76D9BBE5BC8AD413E607F57F0347D0EBA65D4A7C7D014734
```

Solo l'APK ARM64 e' stato installato durante questo test sull'S23
`RFCW30S277B`. L'utente ha disabilitato il servizio ARM32 sul telefono per
evitare conflitti; lo stato Accessibility verificato e' Bitwarden +
`com.codex.lle64/com.codex.lle.ChargingAccessibilityService`. Il Note 4
`44d857ce` e' connesso come oracle e non e' stato toccato. Il wizard ARM64 e'
stato lasciato intenzionalmente allo step wallpaper (`3 of 4`) affinche'
l'utente scelga la sorgente senza perdere il wallpaper preciso gia' importato.

## Primo messaggio operativo storico (non piu' applicabile)

> Non implementare ancora ARM64. Leggi `AGENTS.md` e questo handoff; acquisisci
> Blind da LSE ARM32, diffalo col DEX attivo e recupera dal firmware T705 asset e
> wiring SystemUI. Poi integra il DEX originale come WIP nella sola build ARM32,
> senza tuning a occhio e senza alterare gli altri effetti. Solo dopo un baseline
> stabile affronta compositing e validazione ARM64.
