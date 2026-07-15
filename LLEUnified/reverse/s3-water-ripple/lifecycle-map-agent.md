# S3 `libWaterRipple.so` ARM32 — JNI, lifecycle e ownership per il port ARM64

Data: 2026-07-14. Analisi **sola lettura** di `libWaterRipple.so` in Ghidra e dello smali originale S3. Il database Ghidra non è stato modificato né salvato; nessun file dell'app o del port è stato cambiato.

Convenzione indirizzi: Ghidra usa image base `0x10000`, quindi `indirizzo Ghidra = ELF st_value + 0x10000`.

## Livelli di certezza

- **CONFIRMED**: verificato in assembly/P-code e/o nel caller smali esatto.
- **PROBABLE**: ricostruzione coerente con ABI e flusso, ma un dettaglio non è dimostrabile senza un'ulteriore verifica mirata.
- **UNRESOLVED**: il materiale disponibile non basta per una replica esatta.

## Risultato operativo

- **CONFIRMED**: l'API nativa non crea istanze. Esistono un singleton `Fluid` globale, bitmap globali e uno state machine touch globale. Più renderer nello stesso processo condividono tutto.
- **CONFIRMED**: la maggior parte delle operazioni GL avviene nel thread `GLSurfaceView.Renderer`, ma touch, impulsi, copia dei riferimenti bitmap e parte del reset avvengono nel thread UI senza sincronizzazione.
- **CONFIRMED**: `transfer*Bitmap()` conserva un puntatore ottenuto da `AndroidBitmap_lockPixels()` **dopo** `AndroidBitmap_unlockPixels()`. Questo viola il contratto NDK e non deve essere replicato nel port.
- **CONFIRMED**: gli upload forzano `GL_RGBA/GL_UNSIGNED_BYTE`, ignorano `stride`, `format` e `flags` di `AndroidBitmapInfo`.
- **CONFIRMED**: `onInitSetting()`/`onInitGPU*()` sono richiamati due volte nello stesso `onSurfaceChanged()` originale. Il secondo giro perde oggetti GL; non è un requisito visivo e nel port deve diventare idempotente.
- **CONFIRMED**: il cleanup Java non libera programmi, FBO, VBO, texture e buffer CPU nativi. `onFreeWaterTextures()` e `onFreeGravityTextures()` non sono chiamate dallo smali fornito.
- **CONFIRMED**: `FreeGravityTextures()` elimina soltanto due delle tre texture gravity; la terza resta allocata.
- **UNRESOLVED**: per il gravity esatto mancano i tre drawable Samsung originali referenziati tramite ID framework. Il ripple normale richiede invece solo background dinamico e reflection map, già identificata.

## Superficie JNI completa

**CONFIRMED**, dichiarazioni in `JniWaterRippleRender.smali:30-87`; caricamento statico `System.loadLibrary("WaterRipple")` in `JniWaterRippleRender.smali:7-18`. Non è presente registrazione dinamica/JNI `JNI_OnLoad`: gli export usano il nome JNI statico.

```text
clearInkValue()V
getClearInkValue()I
initWaters([F[SIIIII)V
move([F[FIIIIIIZFF)I
onDraw([F[F[SIII[FIIIIFFFFFFFFFF)V
onDrawGravity([F[F[SIII[FIIIIFFFFFFFFFFIFFFFFZF)V
onFreeBGTextures()V
onFreeGravityTextures()V
onFreeWaterTextures()V
onInitGPU()V
onInitGPUGravity()V
onInitSetting(IIZ)V
onLoadBGTextures()V
onLoadGravityTextures()V
onLoadWaterTextures()V
onTouch(IIIF)V
ripple([FIIIIFFF)V
transferBGBitmap(Bitmap)V
transferGravityBitmap(Bitmap,Bitmap,Bitmap)V
transferWaterBitmap(Bitmap)V
```

### Indirizzi dei wrapper

| JNI | ELF | Ghidra | Destinazione/effetto |
|---|---:|---:|---|
| `onInitSetting` | `0x9da0` | `0x19da0` | `Fluid::InitializeSetting @ 0x130c4` |
| `onInitGPU` | `0x9e08` | `0x19e08` | `Fluid::InitializeGPU @ 0x159c8` |
| `onInitGPUGravity` | `0x9e28` | `0x19e28` | `Fluid::InitializeGPUGravity @ 0x13208` |
| `onLoadBGTextures` | `0x9e38` | `0x19e38` | `Fluid::LoadBGTextures @ 0x13320` |
| `onLoadWaterTextures` | `0x9e84` | `0x19e84` | `Fluid::LoadWaterTextures @ 0x133e4` |
| `onLoadGravityTextures` | `0x9ed0` | `0x19ed0` | `Fluid::LoadGravityTextures @ 0x134a8` |
| `onFreeBGTextures` | `0x9f80` | `0x19f80` | `Fluid::FreeBGTextures @ 0x13688` |
| `onFreeWaterTextures` | `0x9f90` | `0x19f90` | `Fluid::FreeWaterTextures @ 0x13694` |
| `onFreeGravityTextures` | `0x9fa0` | `0x19fa0` | `Fluid::FreeGravityTextures @ 0x136a0` |
| `onDraw` | `0x9fb0` | `0x19fb0` | render normale |
| `onDrawGravity` | `0xa6b8` | `0x1a6b8` | render gravity |
| `onTouch` | `0xa8d0` | `0x1a8d0` | state machine ink/stylus |
| `getClearInkValue` | `0xadf0` | `0x1adf0` | legge `Fluid+0xf0` |
| `clearInkValue` | `0xae0c` | `0x1ae0c` | reset ink/touch |
| `transferBGBitmap` | `0xae54` | `0x1ae54` | cattura info/puntatore BG |
| `transferWaterBitmap` | `0xaef4` | `0x1aef4` | cattura info/puntatore reflection |
| `transferGravityBitmap` | `0xaf94` | `0x1af94` | cattura tre texture gravity |
| `initWaters` | `0xb778` | `0x1b778` | inizializza mesh/indici |
| `move` | `0xbc04` | `0x1bc04` | step simulazione |
| `ripple` | `0xbfe4` | `0x1bfe4` | inietta impulso nell'array velocity |

Gli indirizzi C++ della tabella sono Ghidra. Per ottenere l'ELF si sottrae `0x10000`.

## Singleton e stato globale

**CONFIRMED**: `fluid` è un simbolo globale da 1680 byte a ELF `0x120dc`, Ghidra `0x220dc`. Non esiste handle JNI, factory o destroy esplicito.

L'inizializzatore `.init_array` chiama `_INIT_0 @ ELF 0xb628 / Ghidra 0x1b628`: azzera i sotto-oggetti/puntatori, installa lo stato iniziale e registra un distruttore di fine processo con `__aeabi_atexit`. `.fini_array` punta a ELF `0x3068 / Ghidra 0x13068`. Quindi la durata effettiva è il processo, non la view.

### Globali rilevanti

| Stato | ELF | Ghidra | Note |
|---|---:|---:|---|
| `bWithInk` | `0x120b0` | `0x220b0` | selezione processo-globale |
| BG pixels | `0x12068` | `0x22068` | puntatore conservato dopo unlock |
| water pixels | `0x120d8` | `0x220d8` | reflection map |
| gravity pixels | `0x127bc` | `0x227bc` | texture gravity |
| caustics 1 pixels | `0x12770` | `0x22770` | texture gravity |
| caustics 2 pixels | `0x120d4` | `0x220d4` | texture gravity |
| BG `AndroidBitmapInfo` | `0x1277c` | `0x2277c` | width/height/stride/format/flags |
| water `AndroidBitmapInfo` | `0x120b4` | `0x220b4` | idem |
| gravity `AndroidBitmapInfo` | `0x12030` | `0x22030` | idem |
| caustics 1 info | `0x1279c` | `0x2279c` | idem |
| caustics 2 info | `0x12054` | `0x22054` | idem |

Touch globale:

| Stato | ELF | Valore iniziale osservato |
|---|---:|---:|
| `state` | `0x12078` | BSS `0` |
| `step` | `0x12798` | BSS `0` |
| `POSITION_X/Y` | `0x1207c/0x12080` | BSS `0` |
| `drag_step` | `0x12090` | BSS `0` |
| previous X/Y | `0x12098/0x120d0` | BSS `0` |
| drag start X/Y | `0x1209c/0x120a0` | BSS `0` |
| drag end X/Y | `0x12050/0x12778` | BSS `0` |
| `TouchPressure` | `0x1200c` | `1.0f` |
| `isMovingEvent` | `0x12004` | `-1` |
| `sim_step` | `.data` | `10` |
| `acc_step` | `.data` | `5` |
| `Allegro_threshold` | `.data` | `10` |
| `Andante_threshold` | `.data` | `2` |
| `max_step` | `.data` | `12` |

**Conseguenza per il port**: per conservare l'ABI legacy si può mantenere un singleton esplicito, ma l'adapter deve impedire due view attive contemporaneamente. Una nuova API ARM64 migliore può usare un handle per renderer; in quel caso la separazione deve includere anche bitmap e touch state, non soltanto `Fluid`.

## Inizializzazione nativa

### `onInitSetting(IIZ)`

**CONFIRMED @ JNI Ghidra `0x19da0`, `Fluid::InitializeSetting @ 0x130c4`**:

- salva `withInk` in `bWithInk`;
- salva width/height in `Fluid+0x98/+0x9c`;
- imposta i divisori RGB `Fluid+0x170/+0x174/+0x178` a `1.0f`;
- chiama sempre `glGenBuffers(1, Fluid+0x17c)`;
- se `withInk=false`, non configura il solver ink;
- se `withInk=true`, sceglie la density map `512x256` o `256x512` secondo l'orientamento, salva le dimensioni in `+0xa8/+0xac`, usa `screenWidth/12` e `screenHeight/12` in `+0xa0/+0xa4`, e imposta i parametri Fluid osservati: cell size `2.5`, raggio/threshold `200`, 5 iterazioni Jacobi, `dt=0.25`, gradient scale `0.2`, dissipazioni `0.9` e `0.92`.

Non valida dimensioni, non controlla errori GL e non elimina il VBO precedente. Chiamate ripetute perdono il buffer precedente.

### `onInitGPU()`

**CONFIRMED @ JNI `0x19e08`, `Fluid::InitializeGPU(bool) @ 0x159c8`**:

- `withInk=false`: crea soltanto il programma shader normale e lo salva in `Fluid+0x70`;
- `withInk=true`: crea programmi render/density/velocity, superfici/FBO e sette buffer CPU del solver;
- prima di ogni nuova `malloc` dei sette buffer CPU libera il puntatore precedente se non nullo;
- non elimina invece programmi, texture, FBO, superfici e buffer GL già esistenti prima di ricrearli;
- configura blend, viewport/FBO e clear senza controlli di errore.

Un fallimento di compilazione/link shader restituisce `0`, ma il caller non controlla il programma. Anche le allocazioni CPU non sono validate prima dell'uso.

### `onInitGPUGravity()`

**CONFIRMED @ JNI `0x19e28`, `Fluid::InitializeGPUGravity @ 0x13208`**:

- crea il programma gravity in `Fluid+0x70`;
- risolve e conserva dieci location sampler/uniform in `Fluid+0x12c..+0x154`;
- non alloca le superfici del solver Fluid normale;
- non controlla compilazione/link/location/errori GL.

### Vincolo thread/context

Tutte e tre le funzioni invocano GL e richiedono un contesto corrente. Nello smali originale sono chiamate da `onSurfaceChanged()`, quindi dal thread GL. Nel port non devono essere richiamate direttamente dal thread UI.

## Bitmap: trasferimento, formato e lifetime

### `transferBGBitmap()` e `transferWaterBitmap()`

**CONFIRMED @ Ghidra `0x1ae54`, `0x1aef4`**:

1. `AndroidBitmap_getInfo(env, bitmap, &globalInfo)`;
2. `AndroidBitmap_lockPixels(env, bitmap, &globalPixels)`;
3. `AndroidBitmap_unlockPixels(env, bitmap)` immediato;
4. ritorno `void`.

Ogni errore scrive un log Android priority 6 e ritorna. Nessun errore raggiunge Java e nessuno stato precedente viene azzerato.

### `transferGravityBitmap()`

**CONFIRMED @ Ghidra `0x1af94`**: ripete in sequenza getInfo/lock/unlock per gravity, caustics 1 e caustics 2. Si ferma al primo errore. Se il secondo o terzo bitmap fallisce, le globali precedenti possono restare valide/stale e quelle successive non vengono aggiornate. Non c'è rollback atomico.

### Problema di ownership

**CONFIRMED**: il puntatore ottenuto da `lockPixels()` è utilizzato più tardi da `onLoad*Textures()`, quando il bitmap è già stato sbloccato. Tenere vivo l'oggetto Java, come fa il renderer originale, non estende il contratto del puntatore dopo `unlockPixels()`.

Il port ARM64 deve scegliere una delle due strategie:

1. eseguire lock, copia/upload GL e unlock nello stesso comando sul thread GL; oppure
2. mentre il bitmap è locked, copiare le righe in uno staging buffer nativo posseduto dall'istanza, poi sbloccare e caricare dallo staging buffer.

La seconda strategia è più semplice per mantenere decode/UI separato dal renderer GL.

### Formato e stride

**CONFIRMED @ `Fluid::LoadBGTextures 0x13320`, `LoadWaterTextures 0x133e4`, `LoadGravityTextures 0x134a8`**: tutti gli upload usano:

```c
glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
             info.width, info.height, 0,
             GL_RGBA, GL_UNSIGNED_BYTE, pixels);
```

Impostano `GL_CLAMP_TO_EDGE` su S/T e `GL_LINEAR` su min/mag. Usano soltanto `width`, `height` e `pixels`: `stride`, `format` e `flags` sono ignorati; non è impostato `glPixelStorei()`.

**Vincolo exact-port**:

- accettare solo un bitmap software `ARGB_8888` noto e verificare `stride == width*4`, oppure normalizzare ogni riga nello staging buffer;
- definire e testare esplicitamente l'ordine canali atteso prima dell'upload `RGBA`; non assumere che ogni config moderna abbia lo stesso layout byte del percorso Samsung;
- rifiutare/convertire `RGB_565`, `RGBA_F16`, `ALPHA_8` e altre config;
- non affidarsi a `lockPixels()` di un hardware bitmap: creare una copia software `ARGB_8888`/buffer equivalente;
- mantenere il bitmap sorgente soltanto per lifecycle Java, non come garanzia di validità del vecchio puntatore.

**PROBABLE**: l'originale funzionava perché i drawable/background Samsung entravano come bitmap software `ARGB_8888`, tightly packed, sul dispositivo little-endian. La corrispondenza canali va comunque validata con un pattern RGBA nel port, perché l'assembly non descrive la config Java effettiva di ogni sorgente.

## Upload e free delle texture

### Texture ID in `Fluid`

- **CONFIRMED** BG: `Fluid+0x104`.
- **CONFIRMED** water/reflection: `Fluid+0x108`.
- **CONFIRMED** gravity: `Fluid+0x120`.
- **CONFIRMED** caustics 1: `Fluid+0x124`.
- **CONFIRMED** caustics 2: `Fluid+0x128`.

`onLoad*Textures()` genera e carica gli oggetti nel contesto GL corrente. Non verifica che transfer sia riuscito, non controlla puntatori/dimensioni, non controlla `glGetError()`.

### Free

- **CONFIRMED @ `0x13688`** `FreeBGTextures()` elimina `Fluid+0x104`.
- **CONFIRMED @ `0x13694`** `FreeWaterTextures()` elimina `Fluid+0x108`.
- **CONFIRMED @ `0x136a0`** `FreeGravityTextures()` elimina `Fluid+0x120` e `Fluid+0x124`, ma non `Fluid+0x128`.

Gli ID non vengono riportati a zero. Una seconda free può quindi provare a cancellare nuovamente lo stesso nome GL; una load ripetuta sovrascrive l'ID e perde quello precedente.

Nel port ogni risorsa deve avere `{contextGeneration, id}` e:

- essere creata e distrutta soltanto nel thread/context GL proprietario;
- azzerare l'ID dopo la delete;
- eliminare tutte e tre le texture gravity;
- trattare la perdita del contesto come invalidazione automatica, senza chiamare delete sui nomi di un contesto morto;
- rendere load/reload transazionale: non sostituire la texture corrente finché staging e upload della nuova non sono riusciti.

## `onTouch(IIIF)` e ink state machine

**CONFIRMED @ Ghidra `0x1a8d0`**: i parametri sono coordinate pixel grezze, action `MotionEvent` e pressure.

Flusso osservato:

- `ACTION_DOWN (0)`: azzera `count`, aggiorna pressure/coordinate, porta `Fluid+0xf0` a `1`, inizializza i punti drag e passa dallo stato 0 allo stato 1;
- stato 1 + move: misura la distanza; finché `step < max_step` e il movimento è `<= Andante_threshold (2)` resta nello stato; altrimenti entra nello stato 2 e azzera `drag_step`;
- stato 2: classifica `isMovingEvent` in 2 per distanza `>= Allegro_threshold (10)`, 1 per distanza `> 2`, altrimenti 0;
- `ACTION_UP (1)`: in certi rami ritorna allo stato 1 se `step < 12` e `drag_step < 10`; altrimenti resetta stato/contatori;
- `ACTION_CANCEL (3)`: azzera `Fluid+0xf0`, stato e step;
- alla fine aggiorna sempre la posizione precedente.

**PROBABLE**: `TouchPressure` è posto a zero per pressure non positiva/non valida, altrimenti a `pressure*pressure + 0.2`. La sequenza VFP del predicato è ambigua nel decompilato e richiede una verifica assembly mirata se si vuole replicare anche NaN/edge case bit-identici.

**CONFIRMED**, caller `CircleUnlockRippleRenderer.smali:6774+`: Java chiama `onTouch` soltanto nelle modalità `RIPPLE_WITH_INK`/`RIPPLE_LIGHT_WITH_INK`. Per un evento con source `0x4002` inoltra gli action normali; per up/cancel forza action nativo 1. `cleanUp()` sintetizza `onTouch(lastX,lastY,1,1.0)` se manca lo stylus-up.

Il ripple normale/light senza ink non usa `onTouch`: l'onda è iniettata da `ripple()`.

### Clear/get ink

- **CONFIRMED @ `getClearInkValue 0x1adf0`**: restituisce `(int)Fluid+0xf0`. Non è stato trovato un caller Java oltre alla dichiarazione (`rg` sullo smali fornito).
- **CONFIRMED @ `clearInkValue 0x1ae0c`**: imposta `Fluid+0xf0=0`, `Fluid+0xcc=0`, `Fluid+0xf8=0`, `state=0`, `step=0`.

Il clear è richiamato da `onSurfaceChanged()` per modalità ink e da `clearAllEffect()`. Non usa mutex e può correre contro update/render.

## Sequenza lifecycle Java/GL originale

### Costruzione view/renderer

**CONFIRMED**, `RippleUnlockView.smali:36-255`:

1. legge le real display metrics;
2. costruisce `CircleUnlockRippleRenderer(context, view, width, height)`;
3. richiede EGL client version 2;
4. sceglie config RGBA `8/8/8/8`, depth 16, stencil 0;
5. registra il renderer;
6. parte in `RENDERMODE_WHEN_DIRTY (0)`;
7. usa holder format `3` (`PixelFormat.TRANSLUCENT`).

**CONFIRMED**, renderer `CircleUnlockRippleRenderer.smali:445-1590`: inizializza campi e configurazione modello, chiama `initWaters()`, quindi `setBackground(true)`. Quest'ultimo decode/carica i riferimenti bitmap e chiama i tre `transfer*Bitmap()` dal thread UI.

`initWaters()` (`CircleUnlockRippleRenderer.smali:2252-2397`) alloca mesh/simulation arrays Java, invoca il JNI `initWaters`, poi azzera/inizializza gli array.

### Creazione superficie

**CONFIRMED**, `CircleUnlockRippleRenderer.smali:10382-10412`, thread GL:

```text
onSurfaceCreated:
    loadBitmapIfBitmapNull()
    onLoadBGTextures()
    onLoadWaterTextures()
    previousWidth = reset
```

Il callback carica quindi BG e water texture prima della successiva inizializzazione shader/buffer in `onSurfaceChanged()`. È legale perché il singleton nativo esiste già dal load della libreria, ma presuppone che i transfer UI abbiano prodotto puntatori ancora leggibili.

### Cambio superficie/orientamento

**CONFIRMED**, `CircleUnlockRippleRenderer.smali:9678-10380`, thread GL:

- aggiorna flag e dimensioni/matrici;
- ritorna presto se la **width** non è cambiata, ignorando un cambio di sola height;
- determina landscape con `width > height`;
- riusa min/max delle dimensioni originarie della window per `mScreenWidth/mScreenHeight`;
- chiama init specifico della modalità.

**CONFIRMED, anomalia importante**: ogni ramo di init è duplicato nello stesso callback (direttive source `.line 913-942`, circa righe fisiche smali `10113-10377`):

- `RIPPLE_LIGHT`: `onInitSetting(width,height,false)` + `onInitGPU()`, due volte;
- modalità ink: `onInitSetting(width,height,true)` + `onInitGPU()`, due volte, con clear ink nel flusso;
- gravity: `onInitSetting(width,height,true)` + `onInitGPUGravity()` + `onLoadGravityTextures()`, due volte.

La duplicazione crea VBO/programmi/texture non più raggiungibili. Per il port: conservare valori e ordine funzionale di init, ma eseguire una sola inizializzazione per `contextGeneration`; una seconda richiesta identica deve essere no-op o rebuild con cleanup completo.

### Selezione modalità/show

**CONFIRMED**, renderer `show()` circa `10988-11055` e view `show()` circa `736-779`: la modalità/versione viene salvata, si impostano flag/sound e si chiama `requestRender()`.

`Def.MODE` nasce come `RIPPLE_WITH_INK`. Per ottenere il ramo GL corretto, `setRippleVersion/show` deve avvenire prima dell'`onSurfaceChanged()` utile. `onDrawFrame()` ha un fallback che imposta la versione al frame zero quando non è ancora in show, ma arriva troppo tardi per correggere un init superficie già eseguito.

### Draw/move/render mode

**CONFIRMED**, `CircleUnlockRippleRenderer.smali:7940+`:

- `onDrawFrame()` esegue il draw;
- chiama `move()` soltanto quando `drawCount > 0`, quindi il primo frame non avanza la simulazione;
- incrementa `drawCount` fino al limite 2;
- `move()` può riportare il view a `RENDERMODE_WHEN_DIRTY` quando le tre misure di attività sono vuote e non c'è touch, con logica diversa secondo la modalità.

**CONFIRMED**, `ripple(FFFZ)` in `CircleUnlockRippleRenderer.smali:3742-3853`: prima imposta `RENDERMODE_CONTINUOUSLY (1)`, poi chiama il JNI `ripple()` e gestisce il vecchio DVFS boost.

**CONFIRMED**, `mouseMove()` in `CircleUnlockRippleRenderer.smali:6774+`:

- gira normalmente nel thread UI;
- rifiuta multi-touch con più di un pointer;
- su down inietta quattro impulsi;
- su move, dopo distanza accumulata >150 px, inietta tre impulsi;
- su up, se la pressione è durata >600 ms, inietta quattro impulsi;
- usa coordinate raw/display.

Questo significa che `ripple()` modifica l'array velocity Java dal thread UI mentre `move()`/draw può usarlo dal thread GL.

### Background dinamico

**CONFIRMED**, `setBackground(Z)` in `CircleUnlockRippleRenderer.smali:3854+` e `transferBitmapToJni(Z)` a `5789+`:

- UI sostituisce/trasferisce il bitmap BG;
- aggiunge un `Boolean` a una `ArrayList` condivisa;
- nel frame GL, se la coda non è vuota, chiama `onFreeBGTextures()`, `onLoadBGTextures()`, poi rimuove indice 0.

L'`ArrayList` non è sincronizzata. Inoltre il vecchio puntatore bitmap globale può essere sostituito dal thread UI mentre il thread GL lo usa per upload.

### Cleanup, detach, pause/resume

**CONFIRMED**, view `cleanUp()` (`RippleUnlockView.smali:334-352`) delega al renderer `cleanUp()` (`CircleUnlockRippleRenderer.smali:6109-6263`):

- rilascia i suoni e alcuni flag;
- sintetizza eventualmente stylus-up;
- termina il vecchio DVFS;
- posta un runnable ritardato di 300 ms sul parent/UI che chiama `clearAllEffect()`;
- deregistra SContext.

`clearAllEffect()` (`CircleUnlockRippleRenderer.smali:6265+`) azzera array Java e clear ink/gravity. Il runnable UI può scrivere gli stessi array mentre il thread GL sta eseguendo `move()`/draw.

Non avvengono:

- `GLSurfaceView.onPause()` o stop esplicito del thread GL;
- free programmi/FBO/VBO;
- free water/gravity texture;
- free dei buffer CPU del singleton;
- recycle bitmap nel percorso normale;
- destroy nativo.

**CONFIRMED**:

- `reset()` view (`RippleUnlockView.smali:670-698`) chiama clear + requestRender; renderer `reset()` (`CircleUnlockRippleRenderer.smali:10532+`) modifica flag/sensori, non libera GL;
- `onDetachedFromWindow()` (`RippleUnlockView.smali:607-628`) chiama soltanto `renderer.destroyed()`; `destroyed()` (`CircleUnlockRippleRenderer.smali:6483+`) chiude handler/DVFS, non il renderer nativo;
- `onPause()` e `onResume()` (`RippleUnlockView.smali:630-644`) sono vuoti e non invocano neppure il superclass;
- `onWindowVisibilityChanged()` (`RippleUnlockView.smali:646+`) inoltra al superclass solo il caso visible e ignora gli altri;
- `recycleBitmap()` (`CircleUnlockRippleRenderer.smali:3599+`) esiste ma non è stato trovato nel lifecycle effettivo.

### Orientamento

La view inoltra un cambiamento orientamento al renderer solo nel percorso tablet/keyguard locked e quando il valore effettivo differisce. Il renderer forza rendering continuo e attende surface change o un timeout di frame prima di togliere il flag. Indipendentemente da questo listener, il sistema può chiamare `onSurfaceChanged()`.

Rischi exact-port:

- il controllo originale solo-width non basta con resize, split-screen, rotation/inset moderni;
- un EGL context può essere ricreato con la stessa width: l'early return originale lascerebbe ID GL di un contesto morto;
- matrice, viewport, mesh e solver devono dipendere da `{contextGeneration,width,height,orientation}`, non dalla sola width.

## Diagramma dei thread e ownership

```text
UI thread
  constructor/decode -> transfer*Bitmap (lock, salva ptr, unlock)
  touch -> ripple()/onTouch()
  cleanup delayed -> clearAllEffect()/clearInkValue()
  background update -> cambia bitmap + enqueue Boolean
                         |
                         | stato/array/globali senza lock
                         v
GL thread
  onSurfaceCreated -> load BG/water textures
  onSurfaceChanged -> init setting/GPU/gravity
  onDrawFrame -> draw -> move
                -> free/reload BG dalla coda

Process lifetime
  singleton Fluid + bitmap globals + touch globals
```

**CONFIRMED**: non ci sono lock JNI o Java nei percorsi analizzati. Le race principali sono:

1. `ripple()` UI contro `move()` GL sull'array velocity;
2. delayed clear UI contro move/draw GL sugli array;
3. transfer bitmap UI contro load texture GL sul puntatore globale;
4. coda `ArrayList` UI/GL non sincronizzata;
5. due renderer contro singleton/process globals;
6. clear/onTouch contro Fluid update/render.

Per il port, tutti gli impulsi, clear, upload/reload e create/delete devono essere messaggi verso un unico thread GL/simulazione. Se il solver CPU resta fuori da quel thread, array e stato devono avere ownership o lock espliciti; la semplice `volatile` non rende atomico un frame.

## Risorse necessarie e stato del workspace

### Ripple normale/light

**CONFIRMED**: richiede:

- background corrente/dinamico;
- reflection map water originale.

Nel workspace è presente `LLE64/app/src/main/res/drawable-nodpi/s3_reflectionmap.jpg`. Sono presenti anche wallpaper S3 di fallback e i suoni ripple. Gli shader sono stringhe incorporate nella `.so` e già mappate nel report render; non esiste un ulteriore depth/water asset necessario per la modalità normale.

### Gravity

**UNRESOLVED**: lo smali originale carica tre drawable del framework Samsung tramite ID numerici:

```text
0x1080125  gravity bitmap
0x1080271  caustics bitmap 1
0x1080272  caustics bitmap 2
```

Nel workspace non è presente il corrispondente `framework-res.apk/public.xml` stock S3 che permetta di risolvere nomi e contenuto esatti. Su Android moderno questi ID framework non sono ABI: possono risolvere un'altra risorsa o fallire. Per un gravity esatto bisogna estrarre i tre asset dal firmware S3/framework Samsung compatibile e inserirli come risorse dell'app con nomi propri. Non vanno sostituiti con texture approssimative.

### API Samsung/legacy

**CONFIRMED** nello smali, ma non portabili direttamente su Android moderno:

- `SContextManager` e feature level Samsung;
- sensorhub/SContext gravity;
- `DVFSHelper`;
- hidden `SystemProperties`;
- vecchi path/impostazioni wallpaper accessibili al processo system/keyguard.

Per il ripple normale queste parti non sono necessarie al renderer. Per gravity, la mappatura sensore verso `SensorManager` standard è possibile, ma valori, filtraggio e coordinate devono essere misurati contro l'originale prima di definirla exact.

## Error paths originali

| Operazione | Comportamento originale | Port richiesto |
|---|---|---|
| `System.loadLibrary` fallisce | eccezione in `<clinit>` | capability check e fallback UI esplicito |
| `AndroidBitmap_getInfo/lockPixels` fallisce | log priority 6, `void`, stato eventualmente stale | errore restituito, stato transazionale |
| formato/stride non compatibile | nessun controllo | validazione/conversione |
| shader compile/link fallisce | program `0`, caller continua | propagare log e fallire init |
| `malloc` fallisce | nessun controllo osservato | rollback/errore, niente dereference |
| `glGen*`/`glTexImage2D` fallisce | nessun check | controllo GL per init/upload |
| init ripetuto | leak GL | idempotenza per context generation |
| context loss | lifecycle non gestito in modo robusto | invalidazione + rebuild completo |
| gravity bitmap parziale | globali miste/stale | preparazione atomica dei tre asset |
| free gravity | perde terza texture, ID non azzerati | delete completa + zero ID |

## Sequenza ARM64 raccomandata, semanticamente fedele

Questa sequenza conserva comportamento e ordine osservabili senza replicare i bug di ownership:

1. **UI/background acquisition**: ottenere background e asset; convertire/copiare in staging RGBA8 con stride noto. Non conservare pointer AndroidBitmap sbloccati.
2. **Create renderer generation sul GL thread**: assegnare un nuovo `contextGeneration`; creare o resettare lo stato dell'istanza.
3. **Surface created**: caricare BG e reflection texture dallo staging posseduto. Per gravity caricare il set completo solo se tutti e tre gli asset sono validi.
4. **Surface sized**: impostare width, height, orientation e matrice; inizializzare una sola volta programmi/VBO/solver per quella generation e modalità.
5. **Frame loop**: draw; dal secondo frame applicare move come l'originale; commutare continuous/when-dirty con gli stessi segnali di attività.
6. **Input**: trasformare ogni touch in comando ordinato sul thread simulazione/GL; mantenere le molteplicità originali degli impulsi down/move/up.
7. **Background reload**: preparare nuova texture, verificare upload, poi swap dell'ID e delete della precedente nello stesso thread/context.
8. **Resize/orientation**: rebuild dei soli oggetti size-dependent quando width **o** height cambia; rebuild completo a ogni nuova generation EGL anche con size uguale.
9. **Pause/detach**: fermare input/frame, drenare la queue, eliminare oggetti GL nel contesto valido; liberare staging e buffer CPU dell'istanza. Se il contesto è già perso, scartare gli ID senza delete.

## Checklist minima prima di integrare il port

- [ ] Nessun pointer `AndroidBitmap` usato dopo unlock.
- [ ] Formato software e stride validati/copiati riga per riga.
- [ ] Upload e delete eseguiti sul GL thread con context generation.
- [ ] Init ripetibile senza leak e rebuild corretto su context loss.
- [ ] Singleton impedisce due renderer oppure stato convertito integralmente per-instance.
- [ ] Touch/ripple/clear serializzati rispetto a move/draw.
- [ ] Background reload transazionale, senza `ArrayList` condivisa non sincronizzata.
- [ ] Tutte e tre le texture gravity eliminate.
- [ ] Shader/malloc/GL error propagati al Java layer.
- [ ] Modalità impostata prima dell'init superficie.
- [ ] Dimensioni confrontate su width e height, non solo width.
- [ ] Gravity non dichiarato exact finché i tre asset Samsung non sono recuperati.

## Questioni ancora aperte

- **UNRESOLVED**: nomi e contenuto esatti dei tre drawable gravity Samsung; serve il framework-res stock S3 corrispondente.
- **UNRESOLVED**: mapping bit-identico del SContext gravity verso sensori Android standard.
- **PROBABLE**: edge case del predicato VFP pressure/NaN in `onTouch`; non blocca il ripple normale.
- **UNRESOLVED**: comportamento desiderato con due lockscreen renderer simultanei. L'originale non lo supporta in sicurezza; il port deve imporre esclusione oppure progettare istanze vere.

## Riferimenti incrociati

- `LLE64/reverse/s3-water-ripple/render-map-agent.md`: shader, draw e binding GLES esatti.
- `LLE64/reverse/s3-water-ripple/fluid-map-agent.md`: layout solver, update CPU/GPU e ownership dei buffer.
- `LLE64/reverse/s3-water-ripple/BENCHMARK.md`: baseline e criteri di confronto.
- `unlock-effects-test/demo-apk/smali_s3_ripple/.../JniWaterRippleRender.smali`: ABI JNI.
- `unlock-effects-test/demo-apk/smali_s3_ripple/.../CircleUnlockRippleRenderer.smali`: sequenza renderer/input/lifecycle.
- `unlock-effects-test/demo-apk/smali_s3_ripple/.../RippleUnlockView.smali`: lifecycle view/GLSurfaceView.
