# Abstract Tiles ARM64 - transcript completo del port Line

Data di consolidamento: 2026-07-16. Questo documento descrive il livello
`Line` del port ARM64, la sua relazione con Tile e Scatter, le prove ricavate
dal binario ARM32 di riferimento, le differenze imposte dall'host L.L.E. e la
procedura da usare per un futuro confronto affiancato con un dispositivo
originale.

## Stato dichiarato

- La geometria Line, le 22 tabelle portrait/landscape, i delta firmati, le
  threshold, l'ordine dei triangoli, la curva e la durata sono verificati contro
  il disassemblato.
- La Line viene presentata a circa 60 fps e usa tempo monotono trascorso, quindi
  la durata non cambia fra pannelli 60 e 120 Hz.
- Il picker non usa più la dicitura Alpha. Abstract Tiles ARM64 è Beta.
- La Line è ON per impostazione predefinita. Un toggle ARM64 permette una vera
  modalità Line OFF senza duplicare effetto, APK o trunk applicativo.
- Non viene dichiarata parità visiva assoluta finché non esiste una registrazione
  frame-aligned dello stesso gesto sul dispositivo originale.

## Materiale di prova preservato

Input di reverse:

- `vendor/original-native/libsecveAbstractTile.so`
- SHA-256:
  `F8E8BDF48D069F76AF9923D68474A7047C621DD763D3E6D96C4F940025643840`
- immagine mask Line `56 x 62`:
  `res/drawable-nodpi/special_abstracttile_linemask.png`
- SHA-256 mask:
  `523B2345EF2DFDC11D6DEDAF4B9EB818F1E0CB96D4A95E18BA3BC527C36B699B`

Output Ghidra conservati:

- `research-agent/GHIDRA-LINE-MOTION-20260716.txt`: decompilazione delle
  funzioni Line, builder e handler;
- `research-agent/GHIDRA-LINE-UNLOCK-HANDLER-20260716.txt`: wrapper unlock
  forzato a funzione;
- `research-agent/GHIDRA-LINE-RAW-INSTRUCTIONS-20260716.txt`: istruzioni ARM
  non semplificate dal decompiler;
- `research-agent/GHIDRA-LINE-SCALAR-REFS-20260716.txt`: riferimenti agli
  offset `+0x5E0/+0x5EC`.

Gli script headless aggiunti per rendere ripetibile la verifica sono:

- `research-agent/scripts/DumpFunctionInstructions.java`;
- `research-agent/scripts/FindScalarInstructionContext.java`.

Ghidra usa image base `0x10000` per questa importazione. Di conseguenza, per
esempio, `FUN_0002169C` corrisponde all'offset ELF/objdump `0x1169C`.

## Catena esatta dell'unlock

Il wrapper originale è `FUN_0002169C` (offset ELF `0x1169C`). La sequenza
osservata è:

1. chiama la routine Tile unlock con i parametri recuperati
   `FUN_00020BD4(scene, 1, 2, 0, 0.8, 20, 0.9)`;
2. calcola il target Line `scene + 0x5EC`;
3. scrive `0.0` nel target;
4. registra un animatore float con start `0.0`, end `1.0`, start time `now` e
   end time `now + 0.4`;
5. abilita il flag di aggiornamento Line `scene + 0x12C = 1`.

Il literal `0.4f` si trova all'offset ELF `0x11AE0` ed è
`0x3ECCCCCD`. L'updater cosine è `FUN_00013F64`; il literal pi è
`0x40490FDB`.

L'animatore produce:

```text
t = clamp((now - startTime) / 0.4, 0, 1)
p = 0.5 * (1 - cos(pi * t))
```

Quindi sono provati, non stimati:

- verso globale `0 -> 1`;
- durata `400 ms`;
- interpolazione coseno ease-in/ease-out;
- indipendenza dalla durata Tile unlock di `900 ms`.

Non bisogna invertire `p`, sostituire la curva o scalarla in base al refresh
rate. L'avanzamento deve dipendere dal tempo, non dal numero di frame.

## Record Line e funzione di aggiornamento

Ogni corner usa un record logico di 24 byte:

```text
float startX
float startY
float deltaX
float deltaY
float threshold
float vertexIndex
```

`FUN_00013B10` legge `p = clamp(*(scene + 0x5EC), 0, 1)` e applica due rami.

Per `p < threshold`:

```text
position = start
backgroundUv = start - p * delta
```

Per `p >= threshold`:

```text
position = start + (p - threshold) * delta
backgroundUv = valore persistente precedente
```

Conseguenze pratiche:

- threshold `0`: il corner muove subito la geometria lungo `+delta`; la UV del
  background resta al valore iniziale;
- threshold `1`: il corner resta geometricamente fermo e fa scorrere il sample
  del background lungo `-delta` per quasi tutta l'animazione;
- il port assoluto usa `uvProgress = min(p, threshold)` e
  `positionProgress = max(p - threshold, 0)`;
- all'esatto endpoint `p=1`, il binario conserva statefully l'ultima UV del
  frame precedente, mentre il port calcola l'endpoint matematico. A 60 fps la
  differenza è molto piccola e dipende dal momento dell'ultimo frame OEM.

Questa differenza endpoint non autorizza a cambiare segno o curva: è il solo
residuo matematico noto della ricostruzione stateless.

## Ordine di emissione e coordinate

Per ogni quad vengono emessi esattamente sei vertici:

```text
(A, C, D), (A, B, C)
```

La coordinata V dell'atlas dopo il flip è:

```text
{0, 1, 1, 0, 0, 1}
```

Il flip Y del builder originale viene applicato alle UV atlas e background,
non alle posizioni Line o ai delta. Il port mantiene questa separazione:

```text
position.y = startY + positionProgress * deltaY
backgroundV = cropY + (1 - 2*cropY) * (1 - uvY) / 2
```

Invertire il delta Y o applicare un secondo flip alle posizioni produrrebbe una
direzione contraria al binario.

## Tabelle portrait verificate

Formato: `atlasX | A/B/C/D | delta per A/B/C/D | threshold mask`.
I bit mask sono `A=1`, `B=2`, `C=4`, `D=8`.

| # | atlasX | A/B/C/D | delta from->to | mask |
|---:|---:|---|---|---:|
| 1 | 26 | 171/16/19/173 | tutti 171->16 | `0x0` |
| 2 | 18 | 103/267/268/101 | A 103->301, B 103->301, C 101->268, D 103->301 | `0x6` |
| 3 | 46 | 441/290/288/443 | tutti 290->441 | `0x9` |
| 4 | 2 | 952/918/881/951 | tutti 881->951 | `0x0` |
| 5 | 6 | 962/856/857/961 | tutti 857->961 | `0x0` |
| 6 | 14 | 603/374/372/605 | tutti 374->603 | `0x0` |
| 7 | 34 | 531/638/636/533 | tutti 531->638 | `0x6` |
| 8 | 22 | 243/309/310/245 | tutti 310->245 | `0x0` |
| 9 | 30 | 747/854/852/749 | tutti 854->747 | `0x0` |
| 10 | 38 | 909/794/792/911 | tutti 792->911 | `0x9` |
| 11 | 42 | 773/576/578/771 | tutti 773->576 | `0x0` |

La sequenza atlas X è quindi:

```text
26, 18, 46, 2, 6, 14, 34, 22, 30, 38, 42
```

Ogni X è normalizzata dividendo per `56`.

## Tabelle landscape verificate

| # | atlasX | A/B/C/D | delta from->to | mask |
|---:|---:|---|---|---:|
| 1 | 26 | 10/216/218/13 | tutti 13->218 | `0x0` |
| 2 | 18 | 4/236/235/6 | tutti 4->236 | `0x6` |
| 3 | 46 | 880/732/679/883 | tutti 880->732 | `0x6` |
| 4 | 2 | 481/423/364/373 | tutti 364->373 | `0x0` |
| 5 | 6 | 211/208/94/103 | tutti 94->103 | `0x0` |
| 6 | 14 | 34/238/237/37 | tutti 237->37 | `0x0` |
| 7 | 34 | 28/202/205/31 | tutti 28->202 | `0x6` |
| 8 | 22 | 16/80/79/19 | tutti 80->16 | `0x0` |
| 9 | 30 | 886/544/547/889 | tutti 544->886 | `0x0` |
| 10 | 38 | 892/640/643/895 | tutti 892->640 | `0x6` |
| 11 | 42 | 910/682/685/913 | tutti 682->910 | `0x0` |

Le 22 righe sono state confrontate con gli offset del builder
`FUN_0001CC08`; non sono stati trovati mismatch in source index, segno del
delta, threshold o flip.

## Shader e stato GLES

Il pass Line usa tre input per vertice:

- posizione clip-space;
- UV della mask Line;
- UV del background catturato.

Il fragment shader:

1. campiona la mask;
2. scarta il frammento se `mask.a == 0`;
3. campiona il background alle UV Line;
4. usa `mask.a` come alpha premoltiplicato.

Il pass usa blending:

```text
GL_ONE, GL_ONE_MINUS_SRC_ALPHA
```

Ordine del frame:

1. Tile;
2. Line;
3. Scatter additivo.

Risorse Line ON:

- programma vertex/fragment Line;
- VBO Line;
- texture mask `56 x 62`;
- 11 quad, 66 vertici, 7 float per vertice.

## Differenza host: wallpaper pulito contro screenshot completo

Il renderer di riferimento compone Line sopra un Background coerente con la
propria scena. L.L.E. dispone invece di una cattura completa del lockscreen, che
può contenere clock, stato, notifiche e meteo. Quando i grandi slab Line si
muovono, quei pixel UI si muovono insieme al background catturato.

Questa è una differenza di input/compositing, non un errore nelle tabelle Line.
Per la modalità fedele ARM64 la traslazione viene accettata. Il solo adattamento
necessario è il gate a `p=0`: sul renderer trasparente L.L.E., disegnare una
seconda copia statica dello screenshot renderebbe visibili duplicazioni prima
dell'unlock.

## Correzione del movimento percepito

Prima del pass 2026-07-16, `AbstractTilesArm64EffectView` richiedeva un frame ogni
`33 ms`. Una Line di `400 ms` aveva quindi solo circa 12 campioni visibili.
Geometria e curve erano corrette, ma il moto appariva a scatti e diverso dal
legacy.

Il cadence è ora `16 ms`, cioè circa 60 richieste al secondo. La simulazione non
somma step fissi: misura `System.nanoTime()` e passa tutto l'elapsed al core.
Quindi:

- pannello 60 Hz: un draw circa per refresh;
- pannello 120 Hz: la Line resta circa 60 fps, senza diventare due volte più
  veloce;
- frame saltato: il prossimo step recupera l'intero tempo trascorso;
- durata fisica Line: sempre 400 ms.

Questa correzione modifica il campionamento visivo, non la fisica recuperata.

## Modalità Line ON/OFF

È mantenuto un solo effect ID: `EFFECT_S4_ABSTRACT_TILES = 7`.

Preferenza ARM64:

```text
abstract_tiles_line_enabled = true
```

Line ON, default:

- inizializza programma/VBO Line;
- decodifica e carica la mask;
- costruisce 66 vertici ogni frame attivo;
- esegue il pass fra Tile e Scatter.

Line OFF:

- non compila lo shader Line;
- non crea il VBO Line;
- non decodifica/carica la mask;
- non costruisce i vertici Line;
- non invia alcun draw Line.

Il cambio toggle distrugge e ricrea il renderer attivo perché cambia il grafo di
risorse GLES. La versione bridge JNI è stata portata da `1` a `2` per impedire
l'accoppiamento accidentale con una `.so` precedente.

ARM32 conserva il proprio comportamento tiles-only e non mostra il toggle.

## Stabilità e falso restart del motore

Il probe `tests/hint_restart_probe_20260716` ha mostrato due hint nella stessa
sessione wake a causa dell'alternanza temporanea SystemUI -> AOD -> SystemUI.
PID e SurfaceView erano invariati. Non era un crash o un restart EGL:
`nativeAffordance`, correttamente, resetta la scena quando riceve un nuovo hint.

La correzione mantiene `unlockAffordanceShownThisWake` fino a un vero confine di
sessione invece di cancellarlo sul transitorio `showFx=false`.

Sono stati inoltre chiusi due rischi reali:

- una view Abstract Tiles inerte non può più chiamare `nativeDestroyGpu` e
  distruggere lo stato JNI globale della view owner;
- se la cattura background arriva mentre il `GLSurfaceView` è paused/detached,
  il bitmap viene staged lato CPU e caricato nel nuovo contesto da
  `onSurfaceChanged`, invece di essere accodato a un GLThread in uscita.

## Verifica sul build finale ARM64

Il build finale è stato reinstallato sul dispositivo S23 di test e sottoposto al
run `abstract_tiles_20260716_130436_455`. Il gesto dura 420 ms; le cinque catture
coprono circa unlock +20/+80/+160/+240/+400 ms. Il PID è rimasto esattamente
`18485` prima e dopo, `process_survived=true` e il conteggio crash/GLES è zero.
Il test non considera più valido un processo che crasha e viene respawnato.

La lista dei servizi accessibilità è rimasta invariata: Bitwarden più il
companion ARM64 L.L.E. Nessuna impostazione PIN/sicurezza è stata modificata.

## Checklist per il confronto 1:1 sul dispositivo originale

Preparazione:

1. usare lo stesso wallpaper/cattura, senza cambio automatico palette;
2. usare stesso orientamento e rapporto del display;
3. disabilitare animazioni di sistema non necessarie;
4. registrare entrambi i device a 120 o 240 fps con la stessa camera;
5. usare una guida fisica per stessa origine, distanza e durata dello swipe;
6. testare Line ON; ripetere Line OFF come controllo Tile/Scatter.

Frame da estrarre rispetto al comando unlock:

```text
0, 40, 80, 120, 160, 200, 240, 280, 320, 360, 400, 500, 900 ms
```

Per ogni frame confrontare:

- posizione dei bordi degli 11 slab;
- direzione dei corner threshold 0;
- scorrimento interno del background sui corner threshold 1;
- onset e arresto della Line;
- continuità del moto a 60 fps;
- posizione finale a 400 ms;
- indipendenza rispetto ai Tile che continuano fino a 900 ms;
- luminosità Tile e Scatter, separatamente dalla Line;
- eventuale spostamento della UI contenuta nello screenshot L.L.E.;
- alpha dell'overlay e assenza di rettangoli neri;
- presenza di un solo hint/reset per wake.

Misure consigliate:

- errore posizione in pixel per ogni slab ai tempi sopra;
- differenza temporale del primo movimento Line;
- differenza temporale del 50% del percorso (`p=0.5`, circa 200 ms);
- numero di frame duplicati o saltati;
- differenza RGB/alpha solo dentro la mask Line;
- PID e generazione EGL prima/dopo due cicli sleep/wake.

Non correggere la Line in base a una singola foto finale. Una foto può confermare
geometria e mask, ma non curva, cadence, direzione temporale o threshold.

## Matrice di confidenza

| Area | Confidenza | Motivo |
|---|---|---|
| durata 400 ms | binario-esatta | literal e record animatore verificati |
| curva coseno 0->1 | binario-esatta | updater e literal pi verificati |
| 22 tabelle | binario-esatta | builder portrait/landscape ricontrollato |
| segno delta e threshold | binario-esatta | record e branch `FUN_13B10` verificati |
| emissione A,C,D,A,B,C | binario-esatta | builder quad verificato |
| atlas/mask | binario-esatta | coordinate e asset hash preservati |
| endpoint UV | quasi esatta | port stateless contro ultimo sample OEM |
| gate a p=0 | adattamento host | necessario per overlay trasparente |
| screenshot con UI | adattamento host | sorgente differente dal Background pulito |
| cadence ~60 fps | policy fedele | corregge il precedente host a 30 fps |
| parità visiva globale | da validare | manca confronto originale frame-aligned |

## Artifact build dopo il consolidamento

- ARM64 companion: `build/arm64-v8a-dev/LLE-arm64-dev.apk`
- SHA-256 APK:
  `F1EE41032EA909B3B49A7731AB3CAD012C11975C9016EA1E7B32C69D61F44D20`
- `.so` Abstract Tiles contenuta nel build ARM64:
  `D97520E586449F2331DBFDCB9888A4BDBA0DEAA8DD57B710C709CE8CC1FCFB7C`
- ARM32: `build/armeabi-v7a/LLE-armeabi-v7a-debug.apk`
- SHA-256 APK:
  `6BA78024348E93B1E9EC5C0AF51AD63ABFFB2EA2176256CB16DEE0889F65B69E`

Questi hash vanno aggiornati se il codice cambia dopo il presente transcript.
