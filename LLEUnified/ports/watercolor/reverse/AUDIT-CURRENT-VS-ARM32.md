# WaterColor: audit current ARM64 vs ARM32 stock

Data audit: 2026-07-14. Questo documento confronta il runtime corrente con i
binari ARM32, senza usare come prova conclusiva i report precedenti e senza
modificare `watercolor_arm64.c`.

## Campioni e metodo

| Artefatto | SHA-256 |
|---|---|
| `native/watercolor_arm64.c` (904 righe) | `78C6601C075108B877C5563364A3066B6A05A49A373BBBA80F7AE67DC129ECD8` |
| `libsecveWaterColor.so` ARM32 | `2B00D2590A9C92BFE5461C8890CA1E4F5D8D0A8196B0BA67C9CDB2B35895C2EB` |
| `libsecveSrkCommon.so` ARM32 | `5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36` |

Le linee C citate valgono per l'hash sopra. Le conclusioni sono state
ricontrollate con `llvm-objdump` e `llvm-readelf` dell'NDK r27d. `[C]` indica
un fatto chiuso da istruzioni/offset; `[I]` un'interpretazione visiva.

Severita visiva:

- **P0**: semantica principale errata o divergenza immediata molto visibile;
- **P1**: divergenza forte in gesti lunghi, unlock o hint;
- **P2**: differenza cumulativa o edge case percepibile;
- **P3**: differenza numerica/architetturale da chiudere per il vero 1:1;
- **OK**: comportamento equivalente nel percorso osservato.

## Verdetto

Il percorso dito `DOWN -> MOVE -> UP` e ora vicino allo stock: coordinate,
soglia, numero di campioni, endpoint escluso, jitter, crescita, alpha ed expiry
sono sostanzialmente corretti. Non e ancora 1:1. I delta maggiori sono:

1. **P0 unlock:** lo stock entra nello stato `ab0`, crea la vector B da quattro
   copie e mantiene il path speciale; current usa solo un timer di 30 frame e
   non ha B.
2. **P1 storage:** current elimina il piu vecchio evento a 192; lo stock usa
   vector dinamica. Questo cambia anche aging e snapshot unlock.
3. **P1 affordance/API speciali:** la JNI stock non equivale a un singolo
   stamp; current non implementa la sequenza scene ne le action 7/9/10/11.
4. **P2 MOVE:** current clampa `move_scale` a `0.5` dopo la sottrazione; stock
   non clampa. Ordine di `sqrt`, divisioni e moltiplicazioni non e bit-identico.

## Catena JNI risolta: unlock e affordance non sono intercambiabili

Questa sezione corregge esplicitamente l'attribuzione opposta presente in
`ARM32-ORACLE-DIFF.md` e nella vecchia etichetta di `PHYSICS.md`.

La prova completa e:

1. Il costruttore scene carica a `0xef4c` il literal `0xfb98 = 0x4d20`.
   L'`add r8,pc,r9` a `0xef58` usa PC `0xef60`, quindi `r8=0x13c80`; a
   `0xef60..0xef64` scrive la vptr effettiva `0x13c88`.
2. La vtable da `0x13c88` contiene `+0x04 = 0xa11c` e
   `+0x0c = 0xa124`.
3. `Native_showAffordance` crea event type `1` a `0x10a78..0x10a98`;
   `Native_showUnlock` crea type `2` a `0x10f1c..0x10f40`.
4. Il dispatcher a `0x117a4` fa `pc + type*4`, con base effettiva
   `0x117ac`: type 1 va a `0x117b0 -> 0x11bc4`; type 2 va a
   `0x117b4 -> 0x11b60`.
5. Il case type 2 a `0x11b60..0x11b6c` chiama vtable `+0x0c`, dunque
   `0xa124 -> 0x4f38`, che scrive `pipeline+0xab0 = 1`.
6. Il case type 1 a `0x11bc4..0x11c44` chiama in ordine gli slot
   `+0x24`, `+0x30`, `+0x3c`, `+0x04`; l'ultimo e
   `0xa11c -> 0x5aac`, che pone il pending reset `+0xcf1`.

Conclusione `[C]`: **showUnlock -> `ab0`/vector B**; **showAffordance ->
sequenza action 1/10/1 e pending reset finale**. Implementare il contrario
porterebbe nel runtime il comportamento della API sbagliata.

## Struttura evento e code

| Area | Current | ARM32 stock / prova | Esito | Patch precisa |
|---|---|---|---|---|
| Layout stamp, C 41-50 | Otto campi da 4 byte: initial, baseline, current, alpha, x, y, mask, path | `0x3668`, stride 32: `+00/+04/+08/+0c/+10/+14/+18/+1c` con gli stessi significati | **OK**; `tube_path` e il forced-mask stock | Rinominare eventualmente `tube_path` in `forced_mask`, senza cambiare layout |
| Primary A, C 73-74 | Array fisso + count | Vector `+0xac0/+0xac4/+0xac8`; tutti gli input entrano qui (`0x3668`, `0x4f44`) | **P1** | Sostituire con storage dinamico; non espellere eventi vivi |
| Cap 192, C 16 e 451-455 | A piena fa `memmove`, scarta A0 | La vector stock rialloca/cresce; nessun cap 192 nel path | **P1** | `Stamp *a`, `count`, `capacity`; crescita geometrica checked; reserve iniziale >=256 |
| Secondary B | Assente | Vector `+0xacc/+0xad0/+0xad4`, costruita dal ramo `0x4138..0x4934` | **P0 unlock** | Aggiungere B separata; quattro elementi, reset insieme ad A |

Il cap non e solo un dettaglio di memoria: stock aggiunge fino a 100 stamp in
una sola MOVE e l'aging dipende da `count-20`. Eliminare A0 anticipa la perdita
della scia e cambia gli elementi campionati dall'unlock.

## Input ordinario, confronto riga per riga

### Coordinate e dispatch

| Current C | Current | Stock / prova | Esito |
|---|---|---|---|
| 780-783 | `x` float e una sola conversione `height-y`; base calcolata prima del branch | I thunk `0xa12c/0xa17c/0xa1cc` scalano le coordinate scene; `0x5514` salva y GL come `height-y` negli eventi | **OK** |
| 784, 789, 792 | action 0, 1, 2 esplicite | `0x5574..0x5628` smista esattamente 2,0,1 e poi 9,7,10,11 | **OK** per 0/1/2 |
| 819-821 | ogni altra action spegne `gesture_active` | stock ignora le sconosciute e ha branch dedicati 7/9/10/11 | **P2/P1 hint** |

### Action 0 / DOWN

| Current C | Current | Stock `0x5584..0x5604` | Esito / severita | Patch precisa |
|---|---|---|---|---|
| 785 | `gesture_active=1` | `+0xabc=1` pointer down | **OK** | Nessuna |
| 786-787 | salva last x/y | salva x/y sia come last sia come current con store di quattro float | **OK** per il path usato | Se serve ABI completa, mantenere anche current separato |
| 788 | inserisce subito base, forced=0 | se `cf2==0` e A non vuota pone `cf3=1` e non inserisce; altrimenti chiama `0x3668` con base/forced0 | **P2** | Aggiungere i due flag deferred; non inserire nel caso di deferral |
| 788 -> 436 | un `rand()` per mask | `0x3668` chiama `rand()` una volta quando forced=0 | **OK** | Nessuna |

Lo stock ha inoltre un gate prima del dispatch quando `ab0` e attivo
(`0x5538..0x5570`); current accetta subito nuovi tocchi. Va modellato insieme
allo stato unlock, non con un `gesture_active=0` isolato.

### Action 2 / MOVE e resampling

| Current C | Current | Stock `0x4f44`, `0x5684..0x56c0` | Esito / severita | Patch precisa |
|---|---|---|---|---|
| 793 | richiede gesture attiva | richiede `pointerDown!=0` e `cf3==0` | **P2**: manca il gate deferred | Rifiutare MOVE mentre `deferred_down` e attivo |
| 794-796 | dx/dy float, `sqrtf(dx*dx+dy*dy)` | somma float, conversione a double, `vsqrt.f64`, ritorno float (`0x4f88..0x4fa8`) | **P3** | `float d2=dx*dx+dy*dy; float d=(float)sqrt((double)d2);` |
| 797-799 | rifiuta se `distance < width*scale*.025` | confronto `width * cc8 * .025 > distance` (`0x4f6c..0x4fb8`) | **OK** salvo NaN/tie | Conservare il confronto stock se si vuole bit parity |
| 800-803 | `ceilf(distance/(width*.05))`, clamp 2..101 | contatore equivalente `clamp(ceil(D/(width*.05)),2,101)` (`0x4fbc..0x50bc`) | **OK** | Nessuna |
| 806 | loop `i=1; i<count` | loop parte da 1 e si ferma prima di count; max 100 | **OK** | Nessuna |
| 807, 814 | calcola ogni volta `t=i/count`, poi `last+dx*t` | divide dx/dy una volta per count (`0x5094..0x50bc`), poi `last+i*step` (`0x5128..0x512c`) | **P3** | Calcolare `step_x=dx/count`, `step_y=dy/count` prima del loop |
| 808-812 | se scale>.5 sottrae .025, poi clampa a .5 | stock sottrae `.025` se `cc8>.5` (`0x50f4..0x511c`) e **non clampa dopo** | **P2** cumulativa | Eliminare le righe 810-812; consentire il piccolo undershoot |
| 814-815 | base*scale*(.55+unit*.25), forced mask0 | stessa formula e un `rand()` per stamp (`0x5124..0x5154`); inserisce forced=1/index0 | **OK** visivo; **P3** bit order | Usare l'ordine float ARM32 mostrato nel pseudocodice sotto |
| 817-818 | aggiorna last solo dopo successo | handler salva target solo se `0x4f44` ritorna 1 (`0x56a4..0x56bc`) | **OK** | Nessuna |

Pseudocodice patch esatto per il corpo MOVE, nel sistema Y corrente:

```c
float dx = fx - last_x;
float dy = fy - last_y;
float d2 = dx * dx + dy * dy;
float distance = (float)sqrt((double)d2);
if (width * move_scale * 0.025f > distance) return;

int count = (int)ceilf(distance / (width * 0.05f));
if (count < 2) count = 2;
if (count > 101) count = 101;
float step_x = dx / (float)count;
float step_y = dy / (float)count;
for (int i = 1; i < count; ++i) {
    if (move_scale > 0.5f) move_scale -= 0.025f; /* no clamp */
    float jitter = 0.55f + (float)rand() * (0.25f / 2147483648.0f);
    push_a(last_x + (float)i * step_x,
           last_y + (float)i * step_y,
           base * move_scale * jitter,
           1, 0);
}
last_x = fx;
last_y = fy;
```

Il piccolo undershoot e reale: l'update recupera `+0.02`; uno stato come
`0.52` passa a `0.54`, poi due campioni producono `0.515` e `0.490`. Al tick
successivo torna `0.510`. Clamparlo a `0.5` modifica tutte le size/RNG
successive del gesto.

### Action 1 / UP

| Current C | Current | Stock `0x59a8..0x59f4` | Esito | Patch precisa |
|---|---|---|---|---|
| 790 | disattiva gesture | azzera `pointerDown` e `cf3` | **OK** per down; **P2** manca cf3 | Azzera entrambi i flag |
| 791 | inserisce `base*move_scale`, forced0 | esattamente `cbc*cc8`, current x/y GL, forced0 via `0x3668` | **OK** | Nessuna |
| 791 -> 436 | un rand per mask | un rand in `0x3668` | **OK** | Nessuna |

## RNG

| Area | Current | Stock / prova | Esito | Patch/test |
|---|---|---|---|---|
| Seed, C 671-673 | `srand((unsigned)time(NULL))` | ctor scene `0xecbc` chiama `time`, `0xecc0` chiama `srand` (reloc GOT confermate) | **OK** semantico | Esporre seed test-only per golden |
| Mask, C 436-440 | `(double)rand()*(2.99/2^31)`, int | stessa sequenza double in `0x3668` | **OK** | Il clamp C e difensivo e non cambia output valido |
| MOVE, C 443-445 | rand -> double /2^31 -> float -> formula | ARM32 converte rand a float e usa `.55 + rand*(.25/2^31)` | **P3** | Usare solo operazioni float nello stesso ordine |
| Call count | DOWN/UP 1; MOVE 1 per campione forced | identico nel percorso 0/2/1 | **OK** | Golden: loggare output per seed fisso |

La compatibilita della sequenza `rand()` tra la vecchia bionic ARM32 e la
bionic ARM64 non va presunta. Per il 1:1 deterministico serve un test con seed
fisso; se diverge, incorporare il generatore legacy locale e non usare il
`rand()` globale del processo.

## Update, crescita, alpha ed expiry

| Current C | Current | Stock update `0x3a68` | Esito | Patch |
|---|---|---|---|---|
| 470 | se scale<1 aggiunge .02, senza clamp | stessa add `.02`, senza clamp | **OK** | Nessuna |
| 474-481 | moltiplicatori 1.075 / 1.025 / 1.005 / 1.0045 alle soglie 2.3/2.6/2.8 | branch e literal in `0x3eb4` e blocco A | **OK** | Conservare float e ordine branch |
| 482-484 | alpha +.025 se current > 2.8*initial | stesso confronto e incremento | **OK** | Nessuna |
| 485-488 | ulteriore +.025 per indici prima degli ultimi 20 | stessa regola basata sul count pre-compattazione | **OK** finche non interviene cap192 | Rimuovere cap |
| 489-493 | conserva solo alpha<1.06 e compatta | stock rimuove a alpha>=1.06 e compatta/memmove | **OK** semantico | Nessuna |
| 494 | decrementa timer unlock | lo stock non ha questo timer nel path JNI unlock | **P0** | Rimuovere timer; usare flag `ab0` persistente/reset stock |

## Vector B, unlock e ordine draw

Quando `ab0!=0`, A non vuota e B vuota, `0x4138..0x4934` crea **sempre
quattro** copie:

| `N=A.count` | B stock |
|---:|---|
| `N>=4` | `[A0, A(N-1), A(N-2), A(N-3)]` |
| `N=3` | `[A0, A2, A1, A0]` |
| `N=2` | `[A0, A1, A0, A1]` |
| `N=1` | `[A0, A0, A0, A0]` |

| Area | Current | Stock / prova | Severita | Patch precisa |
|---|---|---|---|---|
| `Native_showUnlock`, C 825-831 | timer=30 e gesture off | type2 -> slot +0xc -> `a124 -> 4f38`: pone solo `ab0=1`; il ramo usa il countdown `ab4` già inizializzato dal reset | **P0** | Porre solo `unlock_special=1`; mantenere `ab4/cb8` come stato persistente separato e non azzerare down nel callback |
| Costruzione B | assente | mapping sopra, una sola volta mentre B non vuota | **P0** | Dopo i flag e prima degli update, costruire B se `unlock_special && A.count && !B.count` |
| Update B | assente | B prima di A; ogni tick `size*=1.1f`, `alpha=.5f`; nessun expiry individuale osservato | **P0/P1** | Loop B dedicato, non riusare update A |
| Draw B | assente | `0x3140`: B prima di A; timestep globale `.8`, nessuna selezione mask nel loop B | **P1** | Render B prima di A, preservando lo stato mask stock o rendendolo esplicito dopo capture |
| Draw A unlock, C 561 | `.9` solo per 30 frame | quando `ab0`, A usa `.9`; non e legato a 30 frame | **P0/P1** | `if (unlock_special) time_step=.9f` |
| Return active, C 771 | A/gesture/timer | il port deve continuare a tickare B e la macchina ab0; il predicato pipeline stock `0x2ad0` non è direttamente la API scheduling della shell LLE64 | **P1** | Per la shell corrente includere almeno A, B, gesture/eventi pending e `ab0` |

Nel ramo special update stock compare `ab4`, countdown inizializzato a 30 dal
reset. A ogni tick viene decrementato; quando il valore letto è `<=0`, il ramo
sottrae `.06` da **`cb8`**, il gate input unlock, non dallo stroke scalar
`cc8`. Con i default occorrono 30 tick di hold e poi 17 sottrazioni per portare
`cb8` da 1 a un valore non positivo. `ab0` non viene però spento allo scadere:
resta attivo finché un reset lo azzera. Il primo DOWN con `cb8<=0` provoca quel
reset e viene consumato senza stamp. Il countdown float `cd0=30` di action 9/7
è un altro campo indipendente. Va tradotto il branch `0x3e64..0x4934`, non
ridotto a `unlock_frames--`.

## Affordance e action speciali

| Current | Stock `0x5514` / common | Severita | Patch precisa |
|---|---|---|---|
| `showAffordance`, C 834-839, aggiunge uno stamp normale nel punto | event type1 esegue slot `+0x24` (action1), `+0x30` (action10), `+0x3c` (action1), poi `+0x04` (`cf1` pending reset) | **P1 API/hint** | Implementare la sequenza di stati/eventi; non sostituirla con `add_stamp` |
| action9 assente | salva posizione, countdown30 e abilita stato speciale (`0x56d4..0x56f8`) | **P1 hint** | Stato hover start dedicato |
| action10 assente | disabilita lo stato (`0x562c..0x5634`) | **P1 hint** | Hover stop dedicato |
| action7 assente | resampling oltre `width*.015`, jitter `.15..0.30`, forced mask0 (`0x56fc..0x599c`) | **P1 hint** | Funzione separata dal MOVE normale |
| action11 assente | inserisce forced mask, size `2*base` (`0x5638..0x5680`) | **P2** | Branch esplicito |

Il nome UI preciso delle action 7/9/10/11 e inferito; le operazioni e i codici
sono confermati. Il patch deve quindi conservare i codici, anche se si scelgono
nomi interni neutrali.

## Ordine update/draw e stato vuoto

| Area | Current | Stock / prova | Esito / severita | Patch |
|---|---|---|---|---|
| Ordine CPU/GPU, C 766-769 | update -> radial -> advect -> mix | scene `0xa5bc` chiama common `onUpdateScene` poi `onDrawScene`; common `0x49584` usa vtable +.14 e `0x4961c` +.18; pipeline `0x3a68` prima di `0x70e0/0x3140` | **OK** | Nessuna |
| Primo frame stamp | update avviene prima del primo render | identico | **OK** | Testare esplicitamente frame0 |
| Ordine code | solo A | update B->A e draw B->A | **P0/P1 unlock** | Due loop nello stesso ordine |
| Queue vuote | current pulisce radial e advecta comunque | `0x70e0` salta `0x3140` se A e B sono entrambe vuote, poi conserva il final mix | **P1 tail** | Dopo update: se A e B vuote, saltare radial+advect e fare solo mix |
| Input scheduling | JNI current muta stato direttamente | common stock accoda eventi; `Native_draw` li consuma prima di scene update/draw (`0x1134c..`) | **P2 safety/P3 visual** | Se la shell non serializza gia, introdurre event queue FIFO consumata all'inizio di draw |

## Patch plan ordinato

### Fase 0 - evitare la patch semanticamente invertita

1. Trattare questo documento come autorita per il mapping JNI.
2. Correggere le note precedenti: `showUnlock -> ab0/B`,
   `showAffordance -> sequenza + pending reset`.
3. Aggiungere un test di dispatch che logghi type, slot e flag risultante.

### Fase 1 - fedelta visiva ad alto impatto

1. Rendere A dinamica e rimuovere drop-oldest a 192.
2. Aggiungere B da quattro elementi con mapping esatto.
3. Implementare l'intero stato unlock `ab0`: countdown, B update, draw B prima
   di A, timestep `.8/.9`, scalar decrement e reset/lifecycle.
4. Correggere il branch a queue vuote.

### Fase 2 - percorso dito bit-close

1. Rimuovere il post-clamp a `.5`.
2. Usare sqrt double dopo la somma float.
3. Dividere dx/dy una volta e moltiplicare per `i`.
4. Riprodurre la formula jitter in float e validare la sequenza RNG legacy.
5. Aggiungere i flag deferred `cf2/cf3` e il gate input durante unlock.

### Fase 3 - affordance e superficie API

1. Sostituire lo stamp diretto di `showAffordance` con la sequenza stock.
2. Implementare action 7/9/10/11 con stati separati.
3. Se necessario per threading, accodare tutti gli eventi e consumarli prima
   dell'update nello stesso ordine del common ARM32.

## Golden minimi

Con seed, size e frame clock a 60 Hz fissati:

1. DOWN su A vuota e DOWN con A non vuota (deferred path).
2. MOVE appena sotto/sopra soglia; distanza che produce count 2, 3 e 101.
3. Gesto abbastanza lungo da superare 192 eventi.
4. Sequenza che porta scale vicino a `.5` e dimostra l'undershoot.
5. UP: size e mask/RNG call count.
6. Transizioni growth 2.3x, 2.6x, 2.8x; alpha e rimozione a 1.06.
7. Unlock con A count 1,2,3,4 e >4; verificare contenuto B e ordine draw.
8. Affordance type1; verificare chiamate action1/10/1 e pending reset.
9. Ultimo frame: A/B diventano vuote e radial/advect vengono saltati.

Un confronto solo dello screenshot finale non basta: servono dump per frame
di A/B (`size`, `alpha`, coordinate, mask), `move_scale`, RNG call index,
flag speciali e hash dei tre FBO/output.

## Conclusione operativa

La fisica primaria corrente e abbastanza vicina da spiegare il buon risultato
visivo gia osservato, ma la fedelta dichiarabile e circa **alta sul drag
ordinario, non ancora 1:1 sul lifecycle**. Il rischio maggiore non e nei
moltiplicatori A: e nell'aver compresso due API stock diverse in un timer e un
singolo stamp. Il prossimo intervento deve partire da storage A/B e mapping JNI
provato sopra; solo dopo conviene rifinire i delta float/RNG.
