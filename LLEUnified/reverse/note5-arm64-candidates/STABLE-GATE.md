# Gate di stabilizzazione Note 5 ARM64 in LLE64

Data audit: 2026-07-14. Audit statico read-only del working tree successivo a
`c9122ed`; nessun file applicativo o binario e stato modificato da questo audit.

## Verdetto

Il plumbing per rendere selezionabili **N5 Colored Droplet** e
**N5 Sparkling Bubbles** e completo nel working tree: le tre librerie
ARM64 vengono portate verso la build stabile, il dex Samsung bounded viene usato
anche fuori dal probe, i due id sono accettati dalle preferenze, compaiono nel
picker e il servizio costruisce i wrapper corretti.

Il primo stato auditato era sufficiente soltanto per una candidate
snapshot-backed: i binari AOJ4 originali conservavano il compositing fullscreen
opaco di SystemUI. La patch staged descritta nella sezione successiva chiude ora
staticamente questo gap senza alterare i riferimenti firmware. La verifica
dinamica/visiva sul Fold7, il fallback esplicito se il native non parte e il
lifecycle reale del servizio sono stati poi provati nella build installata.
Restano come hardening P1 un soak piu lungo, fold/unfold reale e la matrice
completa di switch/reboot.

## Aggiornamento implementazione trasparenza ARM64

La limitazione di compositing descritta sopra e stata corretta staticamente nel
working tree dopo l'audit iniziale. La build non modifica mai i file AOJ4 in
`reference/`: li copia in staging e invoca
`vendor/native-patches/patch-note5-arm64-transparency.ps1` soltanto sulle copie
in `build/native/lib/arm64-v8a`.

Lo script applica e verifica:

- hash SHA-256 di tutti e tre gli input AOJ4 e della sorgente ARM32 patched;
- estrazione del GLSL ARM32 a `0x5c714`, lunghezza `2785`, SHA-256 blob
  `D4DD042CA07D1D68595DB0F7B67576ABF0EE61CD404245A5B96D20256BA9698F`;
- inserimento dello stesso GLSL trasparente nello slot ARM64 Droplet a
  `0x65268`, con capacita `10952`, terminatore NUL e padding residuo a zero;
- Bubbles `0x531c4`: word `0x1e2e1003` (`fmov s3,#1.0`) sostituita da
  `0x1e2703e3` (`fmov s3,wzr`);
- Bubbles call background a `0x57144`, `0x5714c`, `0x571c0`, `0x571c8`:
  word originali `0x97ff16d7`, `0x97ff1821`, `0x97ff16b8`, `0x97ff1802`
  sostituite singolarmente da `0xd503201f` (`nop`);
- conservazione delle stringhe shader `uBGTexMap`, `uMaskMap` e
  `PointerAlpha`, quindi upload/sampling colore delle bolle ancora presenti;
- architettura, SONAME e `DT_NEEDED libstlport.so` tramite `llvm-readelf`;
- opcode finali tramite `llvm-objdump` e GLSL tramite `llvm-strings`.

Hash deterministici staged finali:

| File | SHA-256 staged patched |
|---|---|
| `libColourDropletEffect.so` | `38FFB25ADAA178D96B981C3EC0D616EC86B2F73EC5EBDDE8437E02D610D19EE4` |
| `libSparklingBubblesEffect.so` | `B96EC92493477AF9F9958A8B7A6466BB4EDD5195145D47F339BB68A9C8552FC0` |
| `libstlport.so` | `821B11D1EA2E1853D0DE0F547F9FE224100AAA53A500F69441765BB089615CCA` |

Due build complete consecutive sono riuscite. Entrambe hanno riprodotto gli
stessi hash native sopra e lo stesso dex lifecycle bounded:

`BCD412CBD6788F9C8635DCC97EED82A54E3489CB434AC1C5528E4B16389A626A`

L'APK risultante e firmato e contiene esattamente marker, Droplet, Bubbles e
STLport sotto `lib/arm64-v8a`.

Aggiornamento runtime: l'APK e stato installato sul Fold7 con
`primaryCpuAbi=arm64-v8a`. Entrambi gli effetti hanno superato init JNI/GLES,
gesture reali, park/resume e compositing trasparente sopra la SystemUI viva.
La verifica visiva dell'utente ha confermato che lo screenshot viene campionato
soltanto dentro l'effetto/particelle. Nei cicli Droplet non sono comparsi crash,
errori EGL/JNI o timeout GL nel PID LLE.

## Stato del working tree auditato

Le modifiche applicative in corso sono corrette nella direzione:

- `build.ps1` ricostruisce sempre `classes-note5-bounded.dex`, non soltanto con
  `-IncludeNote5Probe`;
- la build normale copia `libColourDropletEffect.so`,
  `libSparklingBubblesEffect.so` e `libstlport.so` sotto
  `lib/arm64-v8a`, oltre al marker;
- `OverlayPrefs.isImplementedEffect()` accetta gli id `4` e `5`, quindi
  `unlockEffect()` non li migra piu silenziosamente a Lens Flare;
- `ControlActivity` mostra entrambe le opzioni e riusa icone/preview gia
  presenti;
- `ChargingAccessibilityService.preloadUnlockEffectRenderer()` costruisce
  `ColourDropletEffectView(this, false)` e `SparklingBubblesEffectView(this)`;
- il service valida `isReady()`, distrugge un'istanza parziale e migra una sola
  volta a Lens Flare se JNI/native init non e disponibile;
- il display listener invalida e ricrea i renderer N5 quando cambiano le
  dimensioni del display;
- cache screenshot, attesa dell'affordance, tempi PIN, park/resume e cleanup
  contengono gia i rami N5 necessari.

La variante `Colored Droplet + Gyro` (id `9`) ha ora superato il gate statico
separato. Riusa la stessa libreria ARM64 patched di Droplet e lo stesso wrapper
della precedente app ARM32; le sole differenze del wrapper LLE64 sono il gate
`isReady()` e il teardown GL bounded gia validato per Droplet normale. Il
bridge riflessivo raggiunge `mView -> mIRenderer -> mIJniRenderer` e invoca
`onSensorEvent(int,float,float,float)`, metodo presente sia nel dex Samsung sia
nella tabella JNI ARM64 autentica con firma native `(JIFFF)V`.

Il sensore e opt-in: viene registrato soltanto dopo `CMD_SCREEN_ON` e viene
sempre rimosso su park, screen-off, detach e destroy. Il sample rate resta
`SENSOR_DELAY_GAME`, come nella precedente app, mentre lo smali SystemUI
originale usava `SENSOR_DELAY_UI`; questo aumenta frequenza e consumo durante
la sola fase lockscreen attiva. Il picker, la persistenza, il fallback a Lens
Flare e la ricreazione su cambio dimensioni includono ora l'id `9`.

Il gate dinamico e passato sul Fold7: `gyro JNI bridge ready`, 4 registrazioni,
4 unregister corrispondenti e 17 checkpoint di campioni XYZ variabili. La
verifica visiva dell'utente conferma la risposta gravity; nel PID LLE non sono
comparsi crash o errori JNI/EGL/GL. Piccoli lag e rallentamenti dell'hint restano
un item di tuning P1.

## Gate P0: requisiti prima di chiamarli stabili

### 1. Packaging ARM64 e load order

Input autentici confermati:

| File | SHA-256 input AOJ4 |
|---|---|
| `libColourDropletEffect.so` | `634DC703FF9288A4961B3E636B83DD89DDBF86DF6087D624DC19B4231E6C010C` |
| `libSparklingBubblesEffect.so` | `F96E287CD20B411A863D07D012631FA61761FC35AEC50D4B4A4B454577B2C944` |
| `libstlport.so` | `821B11D1EA2E1853D0DE0F547F9FE224100AAA53A500F69441765BB089615CCA` |

Le due librerie effetto hanno `DT_NEEDED` diretto su `libstlport.so` e
`libstdc++.so`. Non serve caricare manualmente STLport da Java: il linker
Android risolve e carica prima le dipendenze `DT_NEEDED` dalla stessa directory
nativa, come gia provato sul Fold7. Un `System.loadLibrary("stlport")` esplicito
aggiungerebbe ordine applicativo non necessario e non risolverebbe eventuali
incompatibilita di simboli.

Il manifest ha correttamente `android:extractNativeLibs="true"`. Tutti e tre
gli ELF hanno segmenti `PT_LOAD` allineati a `0x10000`, quindi non introducono
il comune problema di page-size a 16 KiB. L'APK resta intenzionalmente
ARM64-only.

Implementato in `build.ps1` per il gate finale:

1. controllo fail-fast dei tre SHA-256 input, non soltanto esistenza del file;
2. `llvm-readelf -h/-d` per verificare AArch64, SONAME e dipendenze attese;
3. controllo SHA-256 anche sui binari patched/staged finali;
4. mantenere gli originali in `reference/` immutati e applicare eventuali patch
   soltanto alle copie sotto `build/native/lib/arm64-v8a`;
5. continuare a confrontare esattamente le entry native dell'APK, come fa gia
   lo script.

### 2. Dex lifecycle bounded anche nella build stabile

Questa correzione e obbligatoria. Il dex Samsung originale attende senza
timeout in `GLThread.onPause()` e `requestExitAndWait()` e puo bloccare il main
thread indefinitamente. Il working tree ora usa correttamente
`patch-note5-lifecycle.ps1` per ogni build: questa modifica non deve essere
rimossa durante la promozione.

Il dex prodotto dal probe validato aveva SHA-256
`B05638F3ADCAAB6664C68CC50A36F5E6AA6E97E53AA8A485972CF2DAC8620E42`.
La build deve continuare a verificare la presenza delle chiamate bounded e il
log di timeout, non limitarsi ad assemblare il dex.

I wrapper ora eseguono un solo exit tramite `removeEffect()`/detach; non va
reintrodotto `SamsungGlTextureShutdown.shutdown()`, che duplicava il teardown.

### 3. Compositing: snapshot opaco contro overlay trasparente

Le librerie ARM64 candidate sono byte-identiche al firmware e **non contengono
le patch trasparenza gia usate sulle equivalenti ARM32**.

Con lo stato attuale:

- `TextureView.setOpaque(false)` rende il buffer capace di alpha, ma non cambia
  un fragment/clear che continua a scrivere alpha `1.0`;
- il service passa correttamente lo screenshot con
  `handleCustomEvent(0, {"Bitmap", "Mode"})`;
- il nero puo quindi essere sostituito dallo screenshot, ma il native ridisegna
  ancora un frame fullscreen: clock, notifiche o transizioni possono apparire
  congelati/duplicati rispetto alla SystemUI viva sottostante.

Se questo comportamento e accettato esplicitamente, si puo chiamare la prima
build **stable candidate snapshot-backed**. Per la stabilita visuale vera in un
accessibility overlay servono patch ARM64 riproducibili:

#### Colour Droplet

Nel file AOJ4 ARM64 il fragment GLSL principale inizia al file offset
`0x65268` ed e lungo `10952` byte prima del terminatore NUL. Conserva i rami
Samsung fullscreen con alpha `1.0`.

La traduzione corretta e gia definita e provata nella build ARM32:

- fuori dalla goccia: `vec4(0,0,0,0)`;
- ombra esterna: nero con solo alpha locale;
- dentro la goccia: rifrazione/colore Samsung invariati, alpha `1.0`.

Il GLSL e indipendente dall'ABI e puo essere inserito nella stringa ARM64,
azzerando lo spazio residuo, tramite uno script con hash input obbligatorio e
verifica della stringa risultante. Dopo la patch vanno controllati compile/link
shader sul device e assenza di `Error compiling shader`.

#### Sparkling Bubbles

Il particle shader e gia ad alpha locale (`uMaskMap.a * vPointAlpha`); i due
problemi sono clear opaco e draw del background fullscreen. Gli anchor ARM64
AOJ4 confermati sono:

- `SPISceneComponent::clearGLBuffer()` a `0x531bc`; istruzione alpha a
  file/VA `0x531c4`, da `fmov s3,#1.0` a zero;
- `SPSparklingBubblesApp::drawApp()` a `0x5710c`;
- chiamate background scale/draw a `0x57144`, `0x5714c`, `0x571c0` e
  `0x571c8`, da neutralizzare con NOP AArch64 dopo verifica degli opcode.

Non va eliminato il caricamento di `PortraitBG`/`LandscapeBG`: la texture deve
restare disponibile come `uBGTexMap` per colorare le bolle. Non va nemmeno
patchato `SPDrawBackground::drawRender()` con un return globale, perche il
renderer comune puo lasciare mesh/stato GL incoerente.

Ogni patch deve verificare hash input, byte originali a ciascun anchor,
disassembly finale e hash output. Mai modificare in-place le copie firmware in
`reference/`.

### 4. Fallback se JNI/renderer non e disponibile

Entrambi i wrapper catturano `Throwable` nel costruttore, impostano
`ready=false` e ritornano comunque un oggetto valido. Il service considera
quell'oggetto un renderer caricato; il touch/PIN continua, ma l'effetto resta
silenziosamente vuoto.

Per una build stabile occorre esporre uno stato `isReady()` (o far fallire il
costruttore in modo controllato), validarlo subito in
`preloadUnlockEffectRenderer()` e applicare una sola politica deterministica:

- distruggere l'istanza parziale;
- registrare il motivo (`UnsatisfiedLinkError`, class/JNI/init GL);
- migrare la selezione a Lens Flare oppure mostrare in UI uno stato
  "renderer non disponibile";
- evitare retry di costruzione ogni 10 ms durante il polling lockscreen.

### 5. Persistenza e selezione

Gli id storici `4` e `5` sono stabili e non vanno rinumerati. La modifica
`unlockEffect() -> isImplementedEffect()` e corretta. Il listener del service
distrugge il renderer attivo su `UNLOCK_EFFECT` e il successivo visibility pass
costruisce quello nuovo.

Test obbligatorio: selezionare ciascun N5 dalla UI, attendere i 2 s di apply,
forzare stop/start del processo e riavviare il telefono; la preferenza deve
restare invariata e non tornare a Lens Flare.

## Gate P1: rischi da chiudere o documentare

### Bitmap background e queue GL

`SPhysicsEffect_TV.changeBackground()` accoda un `Runnable` sulla GL thread
quando il renderer ha gia disegnato. I wrapper riciclano la bitmap precedente
immediatamente durante una sostituzione/clear. In un refresh ravvicinato il
Runnable puo quindi osservare una bitmap riciclata.

La soluzione robusta e conservare le bitmap ritirate fino a conferma della GL
queue o, almeno, fino a un punto lifecycle sicuro (`reset`/detach/destroy), con
un limite stretto per non raddoppiare stabilmente la memoria. Il normale flusso
di cache rende la race rara, ma uno stress di recapture/switch deve provarla.

### Fold/unfold e cambio dimensioni

`windowWidth`/`windowHeight` vengono fissati nell'`EffectDataObj` durante il
costruttore. I wrapper N5 non implementano `onSizeChanged()` e il display
listener del service rivaluta solo la visibilita; non ricrea il renderer.
Sul Fold7 il passaggio display interno/esterno puo quindi lasciare simulazione,
texture e background con dimensioni discordanti.

Per stabilita su foldable, rilevare una variazione reale `width x height`,
invalidare la bitmap e ricreare il renderer N5 fuori da un gesto attivo. Come
minimo questo scenario deve essere incluso nello stress test.

### Cache mancante o screenshot negato

Per N5 l'affordance aspetta un background reale. Se non esiste cache e la
capability screenshot non e disponibile (per esempio servizio non riabilitato
dopo upgrade), il risultato puo essere nessun hint e fallback opaco. La UI deve
mostrare chiaramente "cache vuota" e offrire recapture; il runtime dovrebbe
degradare a Lens Flare o mantenere l'effetto disabilitato, non presentare una
schermata nera come successo silenzioso.

### Distribuzione e versioning

Le tre librerie derivano da firmware Samsung e restano proprietarie. La
promozione e tecnicamente valida per APK privato/test locale; una distribuzione
pubblica richiede una decisione separata sui diritti di redistribuzione.

Aggiornare inoltre:

- `AndroidManifest.xml` versionCode/versionName;
- `README.md`, che al momento dichiara ancora marker-only e N5 esclusi;
- `reference/arm64-candidates/note5-aoj4/AUDIT.md`, che contiene ancora il
  vecchio blocker "STLport ARM64 assente", ormai superato da
  `FIRMWARE-AUDIT.md`.

## Matrice di test prima del commit stabile

1. Build normale senza flag: quattro entry ARM64 esatte (marker + 3 N5), zero
   ARMv7/x86; bounded dex presente.
2. Installazione `adb install -r`; `dumpsys package` deve indicare
   `primaryCpuAbi=arm64-v8a`.
3. Primo avvio senza cache: cattura valida, background inviato, hint e gesto
   visibili per entrambi gli effetti.
4. Cache esistente e recapture manuale: nessun nero, duplicazione, bitmap
   riciclata o dead GL queue.
5. Almeno 20 screen-off/on per effetto e 20 switch alternati
   Droplet/Bubbles/Lens; nessun `AndroidRuntime`, fatal signal, ANR o timeout
   `LLE64-GLThread`.
6. Swipe completo e cancel per entrambi; PIN handoff, touch, reset, screen-on,
   screen-off e destroy presenti nei log.
7. Fold/unfold e cambio risoluzione con effetto parcheggiato e attivo.
8. Force-stop/restart e reboot: selezione persistente e cache riutilizzata.
9. Processo idle con renderer parked: CPU prossima a zero e memoria/thread GL
   senza crescita dopo i cicli.
10. Se si applicano le patch trasparenza: screenshot comparativo con SystemUI
    viva; fuori dall'effetto il pixel deve provenire dalla lockscreen reale, non
    dal frame cached del native.

## Criterio di promozione

- **Stable candidate snapshot-backed**: plumbing corrente + bounded dex +
  hash guard + fallback init + matrice device senza crash. Accetta
  esplicitamente che il native ridisegni lo screenshot fullscreen.
- **Stable trasparente**: tutti i punti sopra piu patch ARM64 Droplet/Bubbles e
  verifica visuale/alpha. Questa e la variante coerente con il comportamento
  desiderato dell'accessibility overlay.
