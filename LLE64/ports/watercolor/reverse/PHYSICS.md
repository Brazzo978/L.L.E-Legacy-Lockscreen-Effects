# Watercolor ARM32: reverse della simulazione e proposta ARM64

Data: 2026-07-14

## Scopo e metodo

Questo documento descrive la parte di simulazione dell'effetto Samsung Watercolor ARM32 e propone un port ARM64 fedele. Non contiene modifiche al codice di LLE64.

Per shader, texture units, UV e compositing finali fa fede `RENDERING.md`, che ha completato l'estrazione del profilo classic `(true, 3)` dopo questo studio della fisica.

Legenda:

- **[C] Confermato**: visibile direttamente in disassembly, dati ELF, shader o smali.
- **[I] Inferito**: nome/ruolo ricostruito dal flusso e dagli argomenti, ma privo di simbolo esportato.
- **[U] Da verificare**: comportamento non ancora dimostrato abbastanza per un port 1:1.

Gli indirizzi sono VMA ELF del file ARM32 originale, senza l'eventuale base `0x10000` mostrata da vecchi appunti. Analisi eseguita con `llvm-readelf`/`llvm-objdump` NDK e confronto con gli artefatti Capstone già presenti nel workspace.

## Campione canonico e fonti

| File | SHA-256 | Ruolo |
|---|---|---|
| `LLE64/reference/arm32-original/native-libs/armeabi-v7a/libsecveWaterColor.so` | `2B00D2590A9C92BFE5461C8890CA1E4F5D8D0A8196B0BA67C9CDB2B35895C2EB` | implementazione effetto |
| `LLE64/reference/arm32-original/native-libs/armeabi-v7a/libsecveSrkCommon.so` | `5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36` | scene/input/render framework condiviso |

Fonti secondarie confrontate:

- `unlock-effects-test/docs/watercolor-native-reverse-2026-07-11.md`
- `watercolor reverse.txt`
- `tmp_watercolor_disasm.txt`
- `tmp_watercolor_keyblocks.txt`
- `tmp_watercolor_allplt.txt`
- `tmp_watercolor_relocs.txt`
- smali di `unlock-effects-test/charging-touch-test-apk`

Il campione è ELF32 ARMv7, 79.060 byte; `.text` è a `0x2900..0x11c90`. Le copie S4 e Note 4 finora confrontate sono byte-identiche. La variante S5 è più piccola, ma conserva la stessa famiglia di shader. **[C]**

Dipendenze ELF rilevanti: `libsecveSrkCommon.so`, `libstdc++.so`, `libstlport.so`, GLES2 ed EGL. Queste sono dipendenze ABI dell'originale, non una prescrizione per il port: la riscrittura ARM64 non deve importare STLport né copiare layout C++ tra ABI. **[C]**

## Risultato principale

Watercolor non è una simulazione di particelle CPU. La CPU costruisce e aggiorna due code di timbri radiali; la propagazione visiva avviene in GLES2 tramite due FBO/texture, un campo radiale/velocità, una texture di rumore e un pass di advezione del fondo. **[C]**

Flusso per tick/frame attivo:

1. aggiorna dimensione, fase e vita dei `BrushEvent`;
2. rasterizza i timbri nel FBO radiale, dopo averlo azzerato a `(0.5, 0.5, 0, 0)`;
3. usa radiale + `Noise` per advectare la densità/immagine nel secondo FBO;
4. miscela densità e colore nel framebuffer finale.

L'irregolarità spaziale continua è texture-driven; `rand()` seleziona la maschera del timbro e ne jittera la dimensione durante l'interpolazione. **[C]**

## Oggetti e punti d'ingresso

### Scene

| VMA | Evidenza | Interpretazione |
|---:|---|---|
| `0x11c74` | alloca `0xdc`, chiama `0xeb90` | `createScene` **[C]** |
| `0xeb90` | inizializza scene e `scene+0xd8`; chiama `time`/`srand` | costruttore scene **[C]** |
| `0xecbc` / `0xecc0` | PLT `time(NULL)` / `srand(seed)` | seed RNG per istanza **[C]** |
| `0xcaac -> 0xb168` | init; alloca `0xcf8`, chiama `0x5c30(this,1,3)` | crea pipeline Watercolor **[C]** |
| `0xa0fc` | inoltra il cambio contesto a `0x9d48` | context-lost/reinit **[I]** |
| `0xa11c -> 0x5aac` | pone `pipeline+0xcf1=1` | pending reset; raggiunto dal case common `showAffordance` type 1 **[C]** |
| `0xa124 -> 0x4f38` | pone `pipeline+0xab0=1` | modalità speciale di `showUnlock` type 2 **[C]** |

La vtable scene è a `0x13c88`. I thunk touch a `0xa12c`, `0xa17c`, `0xa1cc`, `0xa21c`, `0xa26c`, `0xa2bc` inoltrano rispettivamente i codici nativi `0,1,2,9,10,7`. **[C]**

### Pipeline

Il costruttore `0x5c30` scrive la vptr `0x13b98`; l'oggetto misura `0xcf8` byte. **[C]**

| VMA | Ruolo ricostruito | Confidenza |
|---:|---|---|
| `0x9a00` / `0x9d2c` | distruttore / deleting destructor | [C] |
| `0x2b2c` | resize/init superficie; salva width e height | [I] |
| `0x2b6c` | setter parametro, chiavi `0..5` | [C] |
| `0x3a68` | update CPU di code e stati | [C] |
| `0x70e0` | inizializzazione risorse e draw pipeline | [C] |
| `0x5514` | handler degli action code e coordinate | [C] |
| `0x365c` | ritorna `+0xcf5` | [C] |
| `0x2ad0` | stato active/empty derivato da code e flag | [I] |
| `0x9d48` | reset dopo perdita contesto | [I] |

`0xa5bc` nel wrapper scene chiama `SPISceneComponent::onUpdateScene`, `onDrawScene` e `isEmpty`; l'update core viene quindi eseguito una volta per invocazione di `draw()`, senza un `deltaTime` moltiplicato nei calcoli recuperati. **[C]**

## Layout dati della pipeline

Offset relativi all'oggetto allocato da `0x5c30`:

| Offset | Tipo/uso | Evidenza |
|---:|---|---|
| `+0x24`, `+0x28` | `int surfaceWidth, surfaceHeight` | [C] |
| `+0x2c` | renderer `SPDrawRadialWaterBrush` embedded | [I] |
| `+0x1a4` | renderer `SPDrawMixWaterBrush` embedded | [I] |
| `+0x564` | renderer `SPDrawBGAdvectWaterBrush` embedded | [I] |
| circa `+0x91c` | renderer background embedded | [I] |
| `+0xa6c/+0xa70/+0xa74` | FBO/texture/renderbuffer radiale | [C] |
| `+0xa78/+0xa7c/+0xa80` | FBO/texture/renderbuffer densità | [C] |
| `+0xa84` | texture id `Noise` | [C] |
| `+0xaa0..+0xaac` | quattro float riutilizzati: scale FBO durante init, poi last/current touch xy | [C] |
| `+0xab0` | flag modalità speciale unlock | [C] |
| `+0xab4` | countdown/parametro intero, default `30` | [C] |
| `+0xab8` | parametro float, default `1.5` | [C] |
| `+0xabc` | pointer-down | [C] |
| `+0xac0/+0xac4/+0xac8` | vector A begin/end/capacity | [C] |
| `+0xacc/+0xad0/+0xad4` | vector B begin/end/capacity | [C] |
| `+0xcbc` | dimensione base pennello | [C] |
| `+0xcc0/+0xcc4/+0xcc8` | `2.0 / 0.8 / 1.0`; `+cc8` evolve nello stroke | [C] |
| `+0xccc` | hover/affordance attivo | [I] |
| `+0xcd8/+0xcdc` | `0.8 / 1.35` con ctor `(1,3)` | [C] |
| `+0xce0..+0xcec` | `1.2 / 1.3 / 0.4 / 0.4` | [C] |
| `+0xcf0..+0xcf6` | flag init/reset/queue/context | [C], semantica [I] |

### Riutilizzo intenzionale di `+0xaa0..+0xaac`

Il costruttore pone `+0xaa8=0.025` e `+0xaac=0.6`; `0x4c44` li legge come scale iniziali dei due FBO. Al primo DOWN, una store NEON di quattro float a partire da `+0xaa0` sovrascrive anche quei due valori con le coordinate last/current. Non devono quindi essere modellati come campi persistenti indipendenti. Nel port conviene separare chiaramente `initialFboScale` da `touchState`, mantenendo però lo stesso ordine di lifecycle. **[C]**

### `BrushEvent`, stride `0x20`

Ricostruito dalla routine di inserimento `0x3668` e dall'iterazione delle due vector:

```c
struct BrushEvent32 {
    float size0;        // +0x00
    float size1;        // +0x04, baseline iniziale; non viene aggiornata per tick
    float currentSize;  // +0x08, inizialmente uguale
    float phase;        // +0x0c, inizialmente 0
    float x;            // +0x10
    float yGL;          // +0x14
    uint8_t forcedMask; // +0x18
    uint8_t pad[3];
    uint32_t maskIndex; // +0x1c, 0..2
};
```

I vector hanno stride esatto `0x20`. Il port ARM64 deve usare una struct di proprietà dell'app con tipi a larghezza fissa; non deve serializzare/copiare `std::vector` o la struct ARM32. **[C]**

## Input e coordinate

I thunk scene leggono due float normalizzati, li moltiplicano per `scene+0x20/+0x24`, li troncano a interi signed e inoltrano l'action code al common framework. `0x5514` riceve quindi action in `r1`, `x` in `r2`, `y` in `r3`. Nei `BrushEvent`:

```text
xBrush = xAndroid
yBrush = surfaceHeight - yAndroid
```

La conversione Y è una prova diretta e va mantenuta una sola volta nel port. **[C]**

Comportamenti recuperati:

- `0` DOWN: pone `pointerDown`, inizializza last/current point e inserisce il primo evento, salvo differimento indicato dai flag di coda. **[C]**
- `2` MOVE: valido solo mentre down; `0x4f44` ignora distanze inferiori a `surfaceWidth * cc8 * 0.025`, poi usa un contatore massimo di 101 e genera al massimo 100 timbri, perché l'estremo corrente non viene inserito. **[C]**
- `1` UP: libera down/deferred e inserisce il timbro terminale. **[C]**
- `9`: inizializza posizione, countdown `30`, attiva il percorso hover/affordance. **[C]**, nome [I]
- `10`: disattiva quel percorso. **[C]**, nome [I]
- `7`: interpola movimento speciale oltre `surfaceWidth * 0.015`, con lo stesso contatore massimo 101 e al massimo 100 timbri effettivi. **[C]**, nome [I]
- `11`: inserisce un evento a maschera forzata. **[C]**, origine Java [U]

Nel MOVE normale il contatore è equivalente a `clamp(ceil(distance / (width * 0.05)), 2, 101)`. Il loop inserisce `i=1..count-1`, quindi non inserisce il punto finale: al termine quel punto diventa il nuovo `lastPoint`. Prima di calcolare ogni size, `cc8` scende di `0.025` quando il valore pre-sottrazione è maggiore di `0.5`; non esiste un clamp successivo e, dopo il recovery `+0.02`, può quindi sottopassare leggermente `0.5`. La size è `base * cc8 * random[0.55,0.80)`. Il percorso speciale usa gli anchor `0.05` e `0.025`. Per la prima implementazione fedele è preferibile tradurre i branch di `0x4f44` e `0x5514` quasi letteralmente, inclusi cast, estremo escluso e contatore massimo 101, invece di sostituirli con un resampling geometrico generico. **[C]**

## RNG

La scene esegue `srand(time(NULL))` nel costruttore (`0xeb90`, call a `0xecbc/0xecc0`). Le routine usano il `rand()` libc globale. **[C]**

Usi recuperati:

1. In `0x3668`, se `forcedMask == 0`:

   ```text
   maskIndex = uint32(rand() * (2.99 / 2^31))
   ```

   Risultato: `0`, `1` o `2`. Se `forcedMask != 0`, l'indice è `0`. **[C]**

2. Nell'interpolazione MOVE (`0x4f44`, call intorno a `0x5130`):

   ```text
   sizeJitter = 0.55 + rand() * (0.25 / 2^31)
   ```

   Intervallo `[0.55, 0.80)`. **[C]**

3. Nel percorso hover/affordance (`0x5514`, branch intorno a `0x58d4/0x5948`):

   ```text
   sizeJitter = 0.15 + rand() * (0.15 / 2^31)
   ```

   Intervallo `[0.15, 0.30)`. **[C]**

Per fedeltà visiva e test ripetibili, il port dovrebbe introdurre un PRNG locale con seed configurabile ma offrire una modalità legacy che riproduca la sequenza/scaling di `rand()` a 31 bit. Usare il PRNG di sistema ARM64 cambierebbe sequenza e distribuzione tra libc diverse. Il confronto 1:1 deve fissare il seed. **[I]**

## Update loop CPU (`0x3a68`)

L'update è frame-stepped: nel core analizzato non esiste un `dt`. Prima gestisce reset/flag, poi aggiorna e rimuove eventi dalle due code. **[C]**

- Se `+0xcf1` è posto, chiama `0x3448` e lo azzera.
- Ripristina gli scalar shader `noise=3.4` e `radial=3.6`.
- Aggiorna countdown e genera eventi del percorso speciale quando attivo.
- Per la vector secondaria, `currentSize *= 1.1` e `phase` viene portata a `0.5` nel ramo osservato.
- Per la vector attiva, la crescita di `currentSize` è piecewise rispetto alla base:

| Regione | Moltiplicatore per tick |
|---|---:|
| sotto `2.3 * base` | `1.075` |
| `2.3..2.6 * base` | `1.025` |
| ramo fino alla soglia `2.8 * base` | `1.005` |
| oltre `2.8 * base` | `1.0045` |

La fase/alpha usa incrementi di `0.025` nei rami attivi; compaiono inoltre gli anchor `0.35`, `0.02`, `1.06` e `0.06` nelle condizioni di transizione/rimozione. Gli eventi terminati vengono rimossi con compattazione della vector (`memmove`) e lo stato idle viene aggiornato. **[C]** per costanti e operazioni; la denominazione delle fasi è [I].

Costanti float direttamente recuperate dal blocco letterali usato a `0x3eb4` e seguenti:

```text
0.35, 0.02, 1.1, 2.3, 2.6, 2.8,
1.0045, 1.005, 1.025, 1.075, 0.025, 1.06, 0.06
```

Per il port 1:1 bisogna conservare aritmetica `float32`, ordine delle operazioni e confronti. Una riscrittura con `double`, `pow` o curve temporali continue produce divergenza accumulata. **[I]**

### Ruolo esatto delle due vector

La prima vector (`+0xac0/+0xac4/+0xac8`, qui **A**) è la coda normale. Tutti gli inserimenti da touch finiscono in A:

- `0x3668` inserisce DOWN, UP, eventi differiti e gli eventi speciali che passano dal helper;
- `0x4f44` inserisce direttamente gli eventi interpolati del MOVE;
- nessuna routine di input inserisce direttamente nella vector B.

La seconda vector (`+0xacc/+0xad0/+0xad4`, qui **B**) è una snapshot speciale. Quando `+0xab0 != 0`, A è non vuota e B è vuota, il ramo `0x4138..0x4954` costruisce sempre quattro copie in B, senza rimuoverle da A:

```text
count >= 4: [A0, A(count-1), A(count-2), A(count-3)]
count == 3: [A0, A2, A1, A0]
count == 2: [A0, A1, A0, A1]
count == 1: [A0, A0, A0, A0]
```

B viene popolata una sola volta finché resta non vuota. A ogni update, ogni elemento di B esegue `currentSize *= 1.1` e riceve `alpha=0.5`; non è stato trovato un ramo di expiry individuale per B. `0x3448`, `0x6e64` e i reset di lifecycle azzerano l'end di entrambe le vector. **[C]**

L'update elabora prima B e poi A. A conserva la baseline `size1` a `+0x04`: soltanto `currentSize` a `+0x08` cresce. A applica l'expiry `alpha >= 1.06`, compatta gli elementi successivi e aggiorna l'end. **[C]**

### Macchina implementabile `showUnlock` / `ab0`

Il mapping JNI è stato chiuso dalla catena completa e non dai nomi inferiti dei
thunk. Il costruttore scene scrive la vptr effettiva `0x13c88` (`0xef4c..0xef64`);
lo slot `+0x0c` è quindi `0xa124`. `Native_showUnlock` accoda type 2
(`0x10f1c..0x10f40` in common); il dispatcher type 2 va da `0x117b4` a
`0x11b60`, che chiama lo slot `+0x0c`. La destinazione finale è
`0xa124 -> 0x4f38`, cioè `ab0=1`. **[C]**

Semantica per tick:

1. `showUnlock` pone soltanto `ab0=1`; non svuota A, non crea B nel callback e
   non inizializza un timer autonomo.
2. Il reset `0x3448` ha già inizializzato `ab4=30` e `cb8=1.0`. Il ramo
   `ab0` dell'update (`0x3e64`) legge `ab4`, lo decrementa ogni tick e, quando
   il valore letto è `<=0`, sottrae `0.06` da **`cb8`**. `cb8` non è lo
   stroke scalar `cc8`; `cc8` continua il proprio recovery `+0.02` normale.
3. Nello stesso update, se A è non vuota e B è vuota, crea B con le quattro
   copie elencate sopra. B non viene ricreata finché resta non vuota.
4. Aggiorna prima B (`size*=1.1`, `alpha=0.5`) e poi A con crescita/expiry
   ordinari.
5. Il draw `0x3140` rasterizza prima B con timestep globale `cc4=0.8`, poi A;
   per A usa `0.9` finché `ab0!=0`, non soltanto per 30 frame.
6. `ab0` non viene azzerato automaticamente allo scadere di `ab4`. Il search
   completo delle store mostra clear in `0x3448`, init/lifecycle
   `0x5eec`/`0x6e9c`, non nel ramo update. B non ha expiry individuale ed è
   svuotata dagli stessi reset.
7. Finché `ab0!=0 && cb8>0`, l'handler `0x5514` rifiuta il touch. Quando
   `cb8<=0`, un nuovo action 0 chiama `0x3448`: questo azzera `ab0`, A, B e
   stato gesto, poi il DOWN corrente termina senza creare uno stamp. Le altre
   action restano ignorate finché non arriva quel DOWN/reset.

Con i default, i primi 30 update portano `ab4` da 30 a 0 senza cambiare
`cb8`; dal tick successivo `cb8` perde `0.06` per update e diventa non positivo
dopo 17 sottrazioni. Il flag resta comunque posto fino al reset esplicito
(tipicamente il primo DOWN successivo, se la scene esiste ancora). **[C]**

Esiste un secondo countdown 30 indipendente: action 9 scrive il float
`cd0=30` a `0x56d4..0x56f4`; action 7 lo ripristina a 30 a
`0x5994..0x59a0`; il ramo `ccc` a `0x3ac0..0x3b40` lo decrementa e genera lo
stamp periodico quando scade. Questo è il timer hover/azione speciale e non va
fuso con `ab4`. **[C]**

Per il port, lo stato minimo è quindi:

```c
bool unlock_special;       /* ab0 */
int unlock_hold_ticks;     /* ab4, default 30 */
float unlock_input_gate;   /* cb8, default 1.0 */
StampVector primary;       /* A */
StampVector secondary;     /* B */
```

Il booleano restituito dal runtime LLE64 deve restare vero mentre A o B sono
non vuote o mentre esiste uno stato/evento che richiede un altro tick. Per la
macchina unlock significa almeno `A.count || B.count || unlock_special`; se si
vuole replicare anche il common originale, il render scheduling va separato dal
predicato pipeline `0x2ad0`, perché quello considera A/deferred/cf5 e non è una
specifica sufficiente per la shell Java del port. La regola importante per il
draw è: dopo update, se A e B sono entrambe vuote, stock salta radial+advect e
conserva soltanto il final mix; non pulire il radial come fa il current. **[C]**

### Semantica implementabile `showAffordance`

`Native_showAffordance` accoda type 1 (`0x10a78..0x10a98`). Il case type 1
`0x11bc4..0x11c44` esegue sullo stesso singolo evento common:

```text
scene slot +0x24 -> action 1
scene slot +0x30 -> action 10
scene slot +0x3c -> action 1 (seconda famiglia di callback)
scene slot +0x04 -> a11c -> cf1 = 1
```

All'inizio dell'update successivo `0x3a68` vede `cf1`, chiama `0x3448`, azzera
`cf1` e prosegue nello stesso update dallo stato resettato (`0x3e54..0x3e60`).
Quindi la JNI affordance corrente non deve fare `add_stamp(center, base)`: deve
riprodurre la sequenza action/stop e porre un `pending_reset`, consumato nel
tick successivo. Se la shell Java desidera anche un hint visuale al centro,
quello è un percorso Java/onTouch separato e non va attribuito alla semantica
di questa JNI. **[C]**

## Reset e parametri (`0x3448`, `0x2b6c`)

Valori di reset confermati:

| Parametro | Valore |
|---|---:|
| noise vector scalar | `3.4` |
| radial vector scalar | `3.6` |
| `cc0 / cc4 / cc8` | `2.0 / 0.8 / 1.0` |
| countdown | `30` |
| parametro float | `1.5` |
| saturation | `1.2` |
| red saturation | `1.3` |
| green/blue saturation | `0.4 / 0.4` |
| brightness | `1.35` |
| timestep speciale | `0.9` |

Dimensione base del pennello `+0xcbc`:

- superficie non quadrata: `0.35 * 0.8 * min(width,height) = 0.28 * min(width,height)`;
- superficie quadrata: `0.0984374955297 * 2 * width * 0.8`, cioè circa `0.1575 * width`.

Il radial renderer riceve anche l'aspect ratio in base all'orientamento. **[C]**

Setter `0x2b6c`:

| Chiave | Effetto |
|---:|---|
| `0` | ricalcola la size (`height * 0.175 * value`) |
| `1` | salva value e countdown `int(value * 20)` |
| `2` | brightness |
| `3` | saturation |
| `4` | radial vector scalar |
| `5` | noise vector scalar |

## Pipeline GLES2

### Creazione e seed

`0x70e0` carica `bg`, `Noise`, `Tube`, `Mask1`, `Mask2`, `Mask3` e inizializza renderer/FBO se `+0xcf5` è falso. `0x4c44` crea texture RGBA con clamp-to-edge e filtro lineare. Le scale iniziali osservate sono `0.025` per il campo radiale e `0.6` per la densità. **[C]**

`0x4abc` pulisce i due FBO e chiama due volte `SPDrawBGAdvectWaterBrush.draw()` per inizializzare la densità. **[C]**

### Pass per frame (`0x3140`, chiamata da `0x70e0`)

1. imposta viewport alla risoluzione radiale;
2. binda FBO `+0xa6c` e pulisce a `(0.5,0.5,0,0)`;
3. disegna prima B e poi A;
4. imposta viewport alla risoluzione densità e binda FBO `+0xa78`;
5. esegue `SPDrawBGAdvectWaterBrush` con `Noise (+0xa84)`, radial texture `(+0xa70)` e density texture/FBO `(+0xa7c)`;
6. torna al framebuffer di default e al viewport pieno;
7. esegue `SPDrawMixWaterBrush` con radial+density.

Il dettaglio del pass radiale è significativo:

- B: posizione, `currentSize`, `alpha`; `uTimeStep` è sempre il campo globale `+0xcc4 = 0.8`. Il loop B non chiama `setMaskTexture`, quindi usa lo stato maschera già presente nel renderer. **[C]**
- A: seleziona `Mask1/2/3` con `maskIndex`, poi posizione, `currentSize` e `alpha`. In modalità speciale `uTimeStep=0.9`; altrimenti: **[C]**

  ```text
  uTimeStep = clamp((currentSize - size1) * 0.01, 0.1, 1.0)
  ```

`size1` è la baseline iniziale e non la size del tick precedente. I renderer lavorano con blending disabilitato: gli elementi successivi sovrascrivono le aree sovrapposte. **[C]**

L'ordine complessivo stock è `update 0x3a68` e poi `draw 0x70e0/0x3140`; quindi un evento appena inserito cresce una volta prima del suo primo rendering. **[C]**

Non è stato osservato ping-pong esplicito tra due density FBO nel layout principale: l'implementazione GLES originale può contare su una specifica strategia interna del renderer/texture. Questo punto va verificato su GPU target con capture, perché leggere e scrivere la stessa texture nello stesso draw sarebbe undefined in GLES2. **[U]**

### Equazione di advezione recuperata

Forma shader classic verificata nel binario. I setter common trasformano i valori nominali `3.4/3.6` negli uniform effettivi `425.0/66.69`:

```glsl
vec4 alpha = texture2D(uRadial, uv);
vec4 noise = texture2D(uVelocity, uv);
vec2 densityUV = uv + (
    (alpha.xy - 0.5) * 10.0 * 66.69
    + (noise.xy - 0.5) * uNoiseVectorScalar
    + (noise.xy - 0.5) * 425.0
) * alpha.b * 0.0175 * 0.006;
```

Il profilo classic non usa lo shader generico con `pow(alpha, 4.0)`. Dopo brightness e saturation, il mix stock è:

```glsl
gl_FragColor = mix(background, density, alpha.a);
```

L'adattamento trasparente LLE64 emette invece `vec4(density.rgb * alpha.a, alpha.a)` senza blending finale, matematicamente equivalente quando il background catturato coincide con quello live. Saturazione per canale e brightness usano i valori descritti sopra. **[C]** per equazioni/costanti.

## Proposta ARM64 fedele

### Confine del port

Riscrivere la pipeline in C++ ARM64/GLES2 mantenendo asset, shader, risoluzioni interne, code e ordine dei pass. Non tentare di caricare `libsecveWaterColor.so` ARM32 in un processo ARM64 e non portare `libstlport.so`: Android non consente il mix di ABI nello stesso processo e i layout STL non sono stabili. **[C]**

### Moduli suggeriti

```text
WatercolorScene64
  -> TouchDecoder       (action code e singolo flip Y)
  -> BrushQueue         (struct fissa da 32 byte logici, max 100 insert per MOVE)
  -> LegacyRng31        (seed esplicito, scaling originale)
  -> WatercolorTick60   (traduzione branch-for-branch di 0x3a68)
  -> RadialPass         (FBO 0.025x)
  -> AdvectPass         (FBO 0.6x + Noise)
  -> MixPass            (viewport finale)
```

### Timing 60/120 Hz

L'originale aggiorna una volta per `draw()` e tutte le costanti sono per-frame. Su un display a 120 Hz, chiamare l'update due volte raddoppia crescita e decadimento. Per rendere identico il risultato sia a 60 sia a 120 Hz, usare un accumulatore a tick fisso di `1/60 s`:

```text
accumulator += clamp(realDelta, 0, 0.1)
while accumulator >= 1/60 and catchUp < 4:
    updateLegacyTick()
    accumulator -= 1/60
renderCurrentState()
```

Gli input possono essere accodati con timestamp e consumati al tick; non vanno duplicati a 120 Hz. Il render può avvenire a ogni vsync. Se il riferimento Samsung viene provato su un device originale a frequenza diversa, la frequenza legacy dovrà essere misurata e resa configurabile, ma `60 Hz` è l'ipotesi operativa più forte. **[I]**

### Ordine di implementazione

1. Portare asset e shader senza modifiche semantiche.
2. Implementare struct/code e RNG deterministico.
3. Tradurre `0x5514`, `0x4f44`, `0x3668`, `0x3a68` conservando `float32`, cast e ordine branch.
4. Ricostruire FBO e pass nell'ordine di `0x3140/0x70e0`.
5. Aggiungere scheduler fisso 60 Hz e render indipendente.
6. Solo dopo il match, applicare la composizione trasparente richiesta da LLE64 al pass finale; non cambiare radial/advection.

### Audit di `watercolor_arm64.c` dopo il test device

Il test device 2026-07-14 mostra coordinate e trasparenza corrette, ma soltanto blur senza pennellata/scia. Il confronto diretto identifica questi delta nella versione corrente, in ordine di impatto probabile:

| Area corrente | Delta rispetto ad ARM32 | Correzione concreta |
|---|---|---|
| `kMoveSizeMin=0.15`, `kMoveSizeRange=0.15` | questi valori appartengono al percorso action 7, non al MOVE touch | MOVE: `0.55` e `0.25`; action 7 separato: `0.15` e `0.15` |
| `previous_size = stamp.size` a ogni update | `+0x04` stock è baseline immutabile | rinominare in `size1`/`baseline_size` e non aggiornarla |
| `uTimeStep=(size-previous_size)*0.01` | stock usa crescita totale dalla baseline | `clamp((size-baseline_size)*0.01, 0.1, 1.0)` |
| threshold MOVE fisso `width*0.05` | stock usa `width*strokeScale*0.025` | aggiungere `strokeScale`, iniziale `1.0`, minimo `0.5` |
| spacing `floor(distance/(width*0.025))`, endpoint incluso | stock usa contatore `ceil(distance/(width*0.05))`, minimo 2, massimo 101, endpoint escluso | loop `for (i=1; i<count; ++i)`, poi salvare target come nuovo last |
| un solo array `stamps` | manca la vector B speciale | aggiungere snapshot B da quattro elementi e renderizzarla prima di A |
| cap `MAX_STAMPS=192` con drop del più vecchio | vector stock cresce dinamicamente; una singola callback può aggiungere 100 eventi | usare storage dinamico/reserve e non eliminare eventi vivi per overflow |
| action `2/5/7` unite | stock usa 2 per il drag; 7 è un percorso hover distinto; 5 non passa da questo branch | separare gli action code |
| UP usa sempre `base` | stock usa `base * strokeScale` | applicare la scale corrente su UP |

Le size correnti del MOVE sono particolarmente distruttive con FBO radiale a `0.025x`: su short side 1080, `base=302.4`; il runtime produce circa `45..91 px`, cioè solo `1.1..2.3` texel radiali, mentre lo stock produce inizialmente circa `166..242 px`, cioè `4.2..6.0` texel. Questo è coerente con il blur trasparente senza una maschera di pennellata leggibile. **[I]** supportato da costanti [C].

Pseudocodice minimo per il MOVE normale:

```c
/* Versione adatta al runtime corrente, che conserva già Y bottom-origin. */
float brushY = height - yAndroid;
float distance = hypotf(x - lastX, brushY - lastYBrush);
if (distance < width * strokeScale * 0.025f) return;

int count = (int)ceilf(distance / (width * 0.05f));
if (count < 2) count = 2;
if (count > 101) count = 101;

float dx = (x - lastX) / (float)count;
float dy = (brushY - lastYBrush) / (float)count;
for (int i = 1; i < count; ++i) {
    if (strokeScale > 0.5f) strokeScale -= 0.025f;
    float r = legacyRand31();
    float size = base * strokeScale * (0.55f + r * 0.25f);
    pushA(lastX + i * dx, lastYBrush + i * dy, size,
          /*forcedMask=*/true, /*maskIndex=*/0);
}
lastX = x;
lastYBrush = brushY;
```

Nel tick, prima delle vector, stock recupera `strokeScale` verso `1.0` con `+0.02` quando è inferiore a 1. La sequenza corretta è: recupero scale e gestione flag; eventuale costruzione B; update B; update/expiry A; render B; render A; advection; mix. **[C]**

## Piano di verifica 1:1

### Golden deterministici

Fissare seed RNG, risoluzione e sequenza input. Casi minimi:

- tap centrale;
- linea orizzontale lenta e veloce;
- diagonale con 3 cambi direzione;
- stroke fino al contatore limite 101, cioè 100 stamp effettivi;
- DOWN/UP senza MOVE;
- percorsi action `7/9/10/11`;
- cambio 60 -> 120 -> 60 Hz con identici timestamp input.

Per ogni tick salvare:

- conteggio e contenuto delle due code;
- stato RNG;
- hash/immagine FBO radiale;
- hash/immagine FBO densità;
- frame mix finale.

Metriche suggerite: confronto esatto per eventi e parametri CPU; MAE/SSIM per i tre output GPU, oltre a diff heatmap. Prima isolare il radial pass, poi advection, infine mix. Il solo confronto dello screenshot finale può nascondere errori compensati tra pass.

### Verifiche ancora aperte

- **[U]** Determinare con capture GLES se l'advection usa texture feedback interno/ping-pong non evidente dal layout.
- **[U]** Estrarre shader completi con precision qualifier e uniform order, non solo le equazioni chiave.
- **[U]** Dare nomi definitivi alle action `7/9/10/11` seguendo il common framework/Java.
- **[U]** Ricostruire branch per branch le condizioni di rimozione che usano `0.35`, `0.02`, `1.06`, `0.06`.
- **[U]** Verificare se il device Samsung di riferimento forza davvero il render loop a 60 Hz.
- **[U]** Misurare differenze dovute a precisione shader/driver tra GLES2 ARM32 e GPU moderna.

## Conclusione

Il port ARM64 non richiede la ricostruzione di un solver fisico sconosciuto: richiede la replica esatta di un piccolo state machine CPU, due code di `BrushEvent`, tre usi del RNG e una pipeline GLES2 a campi/texture. Il rischio maggiore per la fedeltà non è l'ABI, ma l'ordine numerico dei tick, l'interpolazione input, il feedback del pass di advezione e la precisione shader. Separare tick fisso a 60 Hz e composizione finale consentirà di mantenere comportamento identico su pannelli 60 e 120 Hz senza contaminare la dinamica originale.
