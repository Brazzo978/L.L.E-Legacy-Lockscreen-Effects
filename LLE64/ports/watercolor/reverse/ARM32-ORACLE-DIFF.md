# WaterColor ARM32 oracle vs LLE64 ARM64

> **Correzione successiva:** questo audit ha invertito l'associazione dei due
> comandi JNI. La catena vptr/event-dispatch ricontrollata byte per byte prova
> `showUnlock (type 2) -> a124 -> ab0/vector B` e
> `showAffordance (type 1) -> action 1/10/1 -> a11c/cf1 pending reset`.
> Usare come riferimento aggiornato `AUDIT-CURRENT-VS-ARM32.md` e la sezione
> “Macchina implementabile showUnlock / ab0” di `PHYSICS.md`. Le sezioni sotto
> che attribuiscono pending reset a showUnlock e B all'affordance sono quindi
> superseded e non devono guidare una patch runtime.

Audit indipendente del comportamento attivo di `libsecveWaterColor.so` ARM32 e
`libsecveSrkCommon.so` rispetto a
`ports/watercolor/native/watercolor_arm64.c`.

## Verdetto

Il port ARM64 riproduce gia molto bene il profilo visivo normale: asset, scale,
costanti cromatiche, shader brush/advect/mix, blending del radial e quasi tutta
la fisica della coda primaria coincidono con l'oracolo. Non e pero ancora 1:1
nel lifecycle. Le differenze che contano di piu sono:

1. `showUnlock` usa uno stato speciale di 30 frame che nell'ARM32 non esiste;
2. inizializzazione e feedback della density non riproducono i due pass di seed
   dell'oracolo;
3. manca la seconda queue usata dal path nativo affordance;
4. a queue vuota ARM64 continua radial/advect, mentre ARM32 conserva l'ultimo
   stato e passa direttamente al mix.

Le prime due vanno corrette prima di dichiarare la fedelta 1:1. Il feedback
same-texture dell'ARM32 non va copiato letteralmente: e comportamento GLES
indefinito. Va riprodotto in modo deterministico con il ping-pong gia presente.

## Campioni e affidabilita dell'oracolo

| Campione | SHA-256 / ruolo |
| --- | --- |
| `charging-touch-test-apk/native-libs/lib/armeabi-v7a/libsecveWaterColor.so` | `2B00D2590A9C92BFE5461C8890CA1E4F5D8D0A8196B0BA67C9CDB2B35895C2EB`; coincide con la reference ARM32 |
| `charging-touch-test-apk/native-libs/lib/armeabi-v7a/libsecveSrkCommon.so` | `5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36`; common stock |
| common patched per overlay trasparente | `6C592BD67D98FCA21508F601247902F8480788FC716D4634D0EC0C65F2D5DAC4`; modifica soltanto l'output shader finale |

La patch di trasparenza e documentata da
`charging-touch-test-apk/vendor/native-patches/patch-watercolor-transparent.ps1`.
Non cambia fisica, code, seeding o uniform; sostituisce in-place due stringhe
fragment shader in `libsecveSrkCommon.so`.

## Profilo realmente attivo

La catena ricostruita non e una scelta per somiglianza fra stringhe shader:

- `createScene` a `0x11c74` alloca la scene WaterColor; il costruttore scene e
  a `0xeb90`;
- l'init scene a `0xb198` crea la pipeline e chiama il costruttore `0x5c30`
  con profilo `(1, 3)` a `0xb1ac`;
- il costruttore istanzia `SPDrawRadialWaterBrush`,
  `SPDrawBGAdvectWaterBrush` e `SPDrawMixWaterBrush`;
- i factory shader attivi sono rispettivamente `0x48568`, `0x3dcb4` e
  `0x405d4` in `libsecveSrkCommon.so`.

Quindi l'oracolo attivo e il profilo **Water brush + BG advect + alternate
Water mix**. La variante mix che eleva l'alpha a potenza non e quella usata da
questa pipeline; lo script la patcha solo difensivamente.

## Diff classificato

### P0/P1 - `showUnlock`: semantica sbagliata

**ARM32 oracle**

- scene `showUnlock` (`0xa11c`) chiama `0x5aac`;
- `0x5aac` imposta soltanto il byte pending `pipeline+0xcf1`;
- al successivo update `0x3a68` consuma il pending, chiama reset `0x3448` e lo
  azzera;
- il reset svuota le due queue e ripristina lo stato gesture;
- non esiste un contatore di 30 frame e non assegna il timestep `0.9`.

**ARM64 current**

- `Native_showUnlock` assegna `unlock_frames = 30`;
- `render_radial` forza `uTimeStep = 0.9` finche il contatore e attivo;
- le queue non sono resettate con la stessa transizione pending-next-update.

Il `0.9` appartiene allo stato speciale `ab0` dell'affordance nativa, non
all'unlock. E una divergenza visibile e puo anche prolungare inutilmente il
render loop.

**Raccomandazione concreta:** sostituire `unlock_frames` con
`pending_unlock_reset`. `showUnlock` deve solo impostare il flag; all'inizio del
successivo update vanno svuotate primary/secondary queue, azzerati gesture e
stato affordance e poi consumato il flag. Nessun override del timestep.

Severita: **P0 per semantica API/unlock**, **P1 per sola resa del gesto normale**.

### P1 - seeding e feedback density non equivalenti

**ARM32 oracle**

Il reset/seeding a `0x4abc`:

1. pulisce radial FBO e density FBO;
2. configura noise + radial;
3. esegue **due draw advect consecutivi** verso il density FBO;
4. imposta `cd4 = 1`.

Nel render ordinario `0x3140` il texture ID passato come `uTexMap` e anche la
texture allegata al FBO di destinazione. Il common draw `0x3a4c4` lega:

- unit 0: density/`uTexMap`;
- unit 1: noise;
- unit 2: radial;
- unit 3: original background.

Questo e un feedback read/write sulla stessa texture. Era il comportamento
effettivo del dispositivo ARM32/Mali, ma la specifica GLES non ne garantisce il
risultato.

**ARM64 current**

- usa correttamente due density texture ping-pong;
- al primo frame sostituisce density con background e usa `uDensityReady = 0`;
- esegue un solo pass prima di rendere visibile il risultato;
- primo pass e orientamento di `uOriginal` non equivalgono ai due seed oracle.

**Raccomandazione concreta:** conservare il ping-pong, ma durante init/reset
eseguire due pass advect espliciti A->B e B->A con radial pulito e le stesse UV
dell'ARM32. Non presentare il mix prima che entrambi siano completati. Dopo il
seed fare esattamente un pass ping-pong per fixed update. Confrontare l'output
contro una cattura ARM32, senza reintrodurre il feedback indefinito.

Severita: **P1**, soprattutto per colore/struttura nei primi frame e memoria
della scia.

### P1 - secondary queue dell'affordance nativa assente

**ARM32 oracle**

Quando `ab0 != 0`, la primary queue non e vuota e la secondary e vuota, update
`0x3a68` costruisce esattamente quattro copie:

| N primary | indici copiati |
| --- | --- |
| `N >= 4` | `0, N-1, N-2, N-3` |
| `N = 3` | `0, 2, 1, 0` |
| `N = 2` | `0, 1, 0, 1` |
| `N = 1` | `0, 0, 0, 0` |

La secondary:

- viene aggiornata prima/disegnata prima della primary;
- cresce `size *= 1.1` ogni frame;
- usa alpha fisso `0.5`;
- usa timestep globale `0.8`;
- non rimuove singoli elementi durante l'update.

Nello stesso stato la primary usa timestep `0.9`.

**ARM64 current** ha una sola array `stamps[MAX_STAMPS]` e
`Native_showAffordance` inserisce un solo stamp normale.

Nota importante: nella shell Java WaterColor esaminata,
`WaterColorEffect.showAffordanceEffect` invia dopo il delay un `onTouch(DOWN)`
al centro; in quel percorso la JNI `showAffordance` e normalmente dormiente.
La mancanza resta pero reale per l'API nativa e per eventuali shell alternative.

**Raccomandazione concreta:** introdurre uno stato `affordance_special` e una
secondary queue di quattro copie con le regole sopra. Non riusare il contatore
unlock. Conservare il normale DOWN ritardato della shell Java cosi com'e.

Severita: **P1 se il path JNI viene usato**, **P2 nell'attuale shell Java**.

### P1 - comportamento dell'ultimo frame / tail diverso

**ARM32 oracle:** nel draw a `0x70e0`, dopo l'update, se primary e secondary
sono entrambe vuote salta il pass radial+advect `0x3140` e arriva al final mix
con lo stato GPU precedente.

**ARM64 current:** chiama sempre, nell'ordine, `update_stamps`, `render_radial`,
`render_advection`, `render_mix`. Quando l'ultimo stamp e appena scaduto pulisce
quindi il radial field ed esegue un altro advect. Il tail finale non puo essere
pixel-identico.

**Raccomandazione concreta:** dopo update, se entrambe le queue sono vuote,
saltare radial e advect e fare solo il final mix dello stato conservato, quindi
restituire inactive come l'oracolo. Verificare il frame terminale sia su ARM32
stock sia su overlay patched, perche la trasparenza rende questa differenza piu
facile da vedere.

Severita: **P1**.

### P2/P1 - `setParameters` e un no-op

Il setter ARM32 a `0x2b6c` implementa:

| key | effetto oracle |
| --- | --- |
| 0 | ricalcolo brush size da `height * 0.175 * value` |
| 1 | conserva value e `int(value * 20)` |
| 2 | brightness |
| 3 | saturation |
| 4 | radial vector scalar |
| 5 | noise vector scalar |

Il JNI ARM64 ignora arrays e valori. Le inizializzazioni correnti coincidono con
i default, percio questo non altera la demo normale, ma rompe la superficie API.

**Raccomandazione concreta:** implementare le sei key, validare lunghezze e
clampare solo dove l'ARM32 clampa. Non trasformare nuovamente i valori 4/5 se
sono gia gli scalari effettivi memorizzati nel port.

Severita: **P2 nella shell corrente**, **P1 se picker/config invoca i setter**.

### P2 - cap fisso di 192 stamp

L'ARM32 usa vector dinamici; ARM64 elimina il piu vecchio stamp quando raggiunge
192. Non incide sui gesti brevi, ma un drag lungo o input ad alta frequenza puo
cambiare la doppia accelerazione alpha degli elementi piu vecchi.

**Raccomandazione concreta:** usare crescita dinamica o almeno misurare il
massimo oracle in una gesture stress identica. Non aumentare il cap alla cieca.

Severita: **P2**.

## Parti gia conformi all'oracolo

### Asset

I cinque asset confrontati ARM32 vs LLE64 sono byte-identici:

| asset | SHA-256 |
| --- | --- |
| `mask1.png` | `F0B23FB55C80839616189FB75754A139CF9F09683AC12952548599EED4A3FE1D` |
| `mask2.png` | `20803E2C8867284DCE59EE0B7158BE860C4F2BED965EF41AA477863BE26ABAE5` |
| `mask3.png` | `D527A9FEB90173A0ADDE3DF1E1CE0299E781FFAC3FD28C968A96FB57A687B8D5` |
| noise | `01283D870B1D483AF96F99A3343A3D4E459AFE2BF568DBE5C61541F20A1CB642` |
| tube | `BE1C3AFB734D4AFE04CDBED923F882BC4DA360008A918609F797DC1A447F90F2` |

### Costanti e shader

- scale radial FBO `0.025`, density `0.60`, brush `0.8`;
- brush size portrait `0.35`, square/landscape path `0.196875`;
- soglia drag `0.025 * width`, spacing `0.05 * width`;
- move random `0.55 + rand * 0.25`, decay `0.025`, minimo `0.5`, recovery
  `0.02` per fixed update;
- saturation `1.2`, red `1.3`, green/blue `0.4`, brightness `1.35`;
- noise effettivo `3.4 * 125 = 425`;
- radial effettivo `3.6 * 18.525 = 66.69`.

Lo shader advect ARM64 conserva la formula oracle, incluso i fattori
`0.0175 * 0.006` e il mix verso original a `0.03` quando il radial alpha e
attivo. Lo shader mix corrente usa il profilo Water alternativo attivo e
produce il contributo premoltiplicato richiesto dalla patch trasparente:
`vec4(density.rgb * alpha, alpha)`.

La patch finale oracle attiva sostituisce:

```glsl
gl_FragColor = mix(TexColor, DensityColor, AlphaColor);
```

con l'equivalente di:

```glsl
gl_FragColor = vec4(DensityColor.rgb, 1.0) * AlphaColor;
```

Il port e quindi corretto nel non far passare lo screenshot fullscreen.

### Radial brush e blending

Il fragment attivo ARM32 e la variante tube: mask, tube, direzione radial
normalizzata, rapporto schermo, `uTimeStep * TubeColor.r` nel canale B e
`MaskColor.a * clamp(1-uAlpha,0,1)` in alpha. Il port replica la formula; il
guard sulla direzione di lunghezza zero e una protezione innocua.

`SPDrawRadialWaterBrush::drawRender` a `0x459d0` imposta esplicitamente:

```text
glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
                    GL_ONE,       GL_ONE_MINUS_SRC_ALPHA)
```

esattamente come ARM64. Non e una divergenza.

### Primary queue e touch normale

L'evento oracle e di 32 byte: base size `+0x00`, baseline `+0x04`, current size
`+0x08`, alpha `+0x0c`, x `+0x10`, y GL `+0x14`, fixed-mask `+0x18`, mask index
`+0x1c`.

Il port replica:

- crescita size `1.075`, `1.025`, `1.005`, `1.0045` alle quattro fasce;
- alpha `+0.025` oltre `2.8 * base`;
- secondo `+0.025` per tutti gli eventi tranne i 20 piu nuovi;
- rimozione a `alpha >= 1.06`;
- timestep `clamp((size-baseline)*0.01, 0.1, 1.0)`;
- conversione Y a origine bottom-left;
- MOVE count `ceil(distance / (0.05*width))`, clamp `2..101`;
- loop `i=1; i<count`, quindi endpoint volutamente escluso.

## Artefatti visuali disponibili

Gli artefatti trovati sono:

- `unlock-effects-test/build/s23-watercolor-tail/s23-watercolor-tail.mp4`:
  una sola immagine 1080x2316, utile per trasparenza/tail statico;
- `unlock-effects-test/build/s5-watercolor-tail/run1-tail.mp4`:
  una sola immagine 1080x1920, praticamente nera;
- `unlock-effects-test/build/s5-watercolor-tail/00-awake.png` e `bench.png`:
  capture di stato/bench.

Sono sufficienti come regression check per screenshot leak e frame terminale,
ma **non** come golden dinamico 1:1: non documentano la stessa traiettoria touch
fotogramma per fotogramma su ARM32 e ARM64.

## Piano di chiusura fidelity

Ordine consigliato:

1. correggere pending reset di `showUnlock` e rimuovere il falso override
   `0.9`;
2. rendere il seed density esplicitamente a due pass ping-pong;
3. allineare il branch a queue vuota/tail;
4. aggiungere la secondary queue affordance senza toccare il normale DOWN Java;
5. implementare `setParameters` e rimuovere o giustificare il cap 192;
6. registrare due run a 60 Hz con medesimo sfondo e script touch: ARM32 oracle
   patched e ARM64; confrontare frame 0-5, primo cambio fascia 2.3x, soglia 2.8x,
   ultimo stamp e frame terminale.

Il passaggio 6 e indispensabile per quantificare il residuo causato dal feedback
GLES storico. Il target realistico e equivalenza visiva deterministica, non la
riproduzione letterale di un read/write texture indefinito.
