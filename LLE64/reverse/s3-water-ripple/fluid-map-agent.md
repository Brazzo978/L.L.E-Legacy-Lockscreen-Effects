# `libWaterRipple.so` ARM32 — mappa `Fluid` read-only

Target Ghidra: `libWaterRipple.so`, image base `0x10000`. Analisi eseguita senza rinominare, commentare, tipizzare o salvare il database; il disassembly e stato richiesto in `dry_run`.

## Risultato operativo

- **[CONFIRMED @ `0x164f8`, `0x16c58`]** `Fluid::Update` inlinea i kernel CPU di advezione velocita, divergenza, Jacobi e sottrazione gradiente. Tra i sei target, l'unica chiamata diretta interna e `Update -> AdvectDensity` (`bl 0x14118` a `0x16c58`).
- **[CONFIRMED @ `0x14118`]** `AdvectDensity` e un pass GLES, non un loop CPU: imposta programma/uniform/texture/VBO, disegna un `GL_TRIANGLE_STRIP` da quattro vertici e ripulisce i binding.
- **[CONFIRMED @ `0x14400`, `0x14880`, `0x14be4`, `0x17b78`]** `Jacobi`, `SubtractGradient`, `ComputeDivergence` e `AdvectVelocity` sono kernel CPU row-major; il loop esterno e `y`, quello interno e `x`.
- **[CONFIRMED @ `0x1689c`, `0x176f8`, `0x17a98`]** velocita e pressione usano coppie ping-pong di `Texture<T>` e vengono scambiate copiando tre word `{data,width,height}`.

## ABI e layout recuperato

- **[CONFIRMED @ `0x14418-0x14470`, `0x1489c-0x14910`, `0x14c14-0x14c8c`, `0x17ba0-0x17bf4`]** `Texture<T>` passato by-value e una tripletta ARM32 di 12 byte: `{T *data; int width; int height;}`. I kernel copiano tre word contigue e indicizzano `data[(y * width) + x]`; `vec2` occupa 8 byte, `float` 4 byte.
- **[CONFIRMED @ `0x164f8-0x16584`, `0x1689c-0x168f0`]** texture velocita ping-pong persistenti: A a `Fluid +0x63c/+0x640/+0x644`, B a `+0x648/+0x64c/+0x650`.
- **[CONFIRMED @ `0x170e4-0x1711c`, `0x171e8-0x17228`, `0x176f8-0x1771c`]** pressione ping-pong: A a `Fluid +0x66c/+0x670/+0x674`, B a `+0x678/+0x67c/+0x680`.
- **[CONFIRMED @ `0x16d1c-0x16dc8`]** divergenza: `Texture<float>` a `Fluid +0x684/+0x688/+0x68c`.
- **[CONFIRMED @ `0x14424-0x14494`, `0x16d04-0x16df8`]** parametri comuni: `+0x94 = cellSize`; `+0x98/+0x9c = dimensioni/scale logiche X/Y`; `+0xa0/+0xa4 = dimensioni normalizzanti X/Y`; `+0xbc = numero iterazioni Jacobi`; `+0xc0 = dt`; `+0xc4 = coefficiente gradiente`; `+0xc8 = dissipazione velocita`; `+0xcc = dissipazione densita`.
- **[PROBABLE @ `0x16c2c-0x16c58`]** `Surface_` GLES e un aggregato da 16 byte e le superfici densita sono conservate in coppie da quattro word, ma il significato esatto di ogni word (FBO/texture/dimensioni) resta non tipizzato.

## `Fluid::Update(int)` — `0x164f8`

### Ordine dei pass

1. **[CONFIRMED @ `0x164f8-0x16898`]** alterna il flag `Fluid+0x100`, copia le due texture velocita in scratch e inlinea `AdvectVelocity`: per ogni `y`, poi `x`, legge la velocita A, calcola il backtrace, campiona linearmente A, applica impulso drag opzionale e dissipazione `Fluid+0xc8`, scrivendo B.
2. **[CONFIRMED @ `0x1689c-0x168f0`]** scambia le triplette velocita A/B.
3. **[CONFIRMED @ `0x168f8-0x16ce0`, `0x17ab4-0x17b54`]** decide se eseguire la proiezione in base a modalita/tocco e margine. I margini osservati sono 60, 12 o 10 celle; in alcuni rami aggiorna `Fluid+0xdc/+0xe0` e `Fluid+0xfc`. Se il punto non e nell'interno ammesso salta a `0x1692c`.
4. **[CONFIRMED @ `0x16ce4-0x170d0`]** nel ramo interno genera un jitter con due `rand()` (`0x16ce4`, `0x16cec`), poi inlinea `ComputeDivergence` su velocita corrente -> divergenza. La formula FP finale e a `0x1704c-0x17064`; il loop interno torna a `0x16ef8` da `0x170a4`, quello esterno torna a `0x16ea8` da `0x170d0`.
5. **[CONFIRMED @ `0x170d4-0x171d0`]** azzera il buffer pressione A, inclusi percorsi scalar e vettoriale/allineato.
6. **[CONFIRMED @ `0x171d4-0x17720`]** esegue `Fluid+0xbc` iterazioni Jacobi. La costante `0.25f` e caricata a `0x171e4`; ogni iterazione scambia le triplette pressione a `0x176f8-0x1771c` e torna al corpo a `0x1725c` se il contatore e minore di `Fluid+0xbc`.
7. **[CONFIRMED @ `0x17724-0x17a88`]** inlinea `SubtractGradient`, usando la pressione finale e la velocita corrente per scrivere l'altra texture velocita.
8. **[CONFIRMED @ `0x17a8c-0x17ab0`]** scambia di nuovo le texture velocita e salta al pass comune a `0x1692c`.
9. **[CONFIRMED @ `0x1692c-0x16bb4`]** converte ogni componente velocita da float clampato `[-127,127]` a quattro byte RGBA usando `fract`; alloca `width*height*4`, carica il texture buffer GLES e libera il temporaneo.
10. **[CONFIRMED @ `0x16bb8-0x16c58`]** aggiorna viewport, chiama `AdvectDensity @ 0x14118`, quindi scambia le due `Surface_` densita a `0x16c5c-0x16c74`.

### Formula inline di advezione velocita

- **[CONFIRMED @ `0x16760-0x167dc`]** per cella `(x,y)`: `back = ((x,y) - dt * velocityA[x,y] + (0.5,0.5)) * (1/normX,1/normY)`; il campione e `texture2DLinear(velocityA, back)`.
- **[CONFIRMED @ `0x16820-0x16864`]** se il contatore impulso `Fluid+0xf8 > 0` e la distanza quadrata dal punto scalato e `< 625.0f`, aggiunge `(activeCount - 1.5f) * (DX,-DY)`.
- **[CONFIRMED @ `0x16730-0x16870`]** l'ultimo passo moltiplica entrambe le componenti per `Fluid+0xc8`; output contiguo, stride riga `outWidth*8`.

### Pseudocodice portabile

```cpp
advectVelocity(velA, velA, velB, velocityDissipation);
swap(velA, velB);

if (touchInsideSelectedMargin()) {
    computeDivergence(velA, divergence, jitteredImpulse);
    clear(pressureA, 0.0f);
    for (int i = 0; i < jacobiIterations; ++i) {
        jacobi(pressureA, divergence, pressureB);
        swap(pressureA, pressureB);
    }
    subtractGradient(velA, pressureA, velB);
    swap(velA, velB);
}

uploadPackedVelocityRGBA(velA);
advectDensity(...);
swap(densitySurfaceA, densitySurfaceB);
```

- **[PROBABLE @ `0x168f8-0x16ce0`]** il nome semantico del gate e `touchInsideSelectedMargin`; assembly e decompile confermano confronti e valori, ma i globali `POSITION_X/Y`, `DX/DY` non sono tipizzati nel database.
- **[UNRESOLVED @ `0x164f8`]** il parametro `int` di `Update` non compare con un uso affidabile nel decompilato; non va eliminato dall'ABI finche non viene verificato il chiamante esterno.

## `AdvectDensity` — `0x14118`

- **[CONFIRMED @ `0x1412c-0x141c0`]** usa il programma a `Fluid+0x74`; uniform iniziali: dimensioni logiche da `+0x98/+0x9c`, poi `dt*0.9/float(+0xa0,+0xa4)` usando `Fluid+0xc0`. La costante e una `double 0.9` caricata a `0x14190` e moltiplicata a `0x1419c`.
- **[CONFIRMED @ `0x141c4-0x14268`]** imposta ulteriori uniform da argomenti/`Fluid+0xe4`, posizione `(POSITION_X, height-POSITION_Y)`, `Fluid+0xf8`, e un flag `1`.
- **[CONFIRMED @ `0x1426c-0x14350`]** binda FBO e due texture, carica un VBO da 32 byte, abilita due attributi `vec2` (`GL_FLOAT`, stride 8, offset 0/4) e disegna `glDrawArrays(5,0,4)` = `GL_TRIANGLE_STRIP`.
- **[CONFIRMED @ `0x14354-0x143b4`]** ripulisce texture unit 3,2,1,0, FBO e programma.
- **[CONFIRMED @ `0x16c58`]** chiamante interno: `Fluid::Update`. Gli xref di entry mostrano anche esposizione esterna, ma non altri `bl` interni.
- **[UNRESOLVED @ `0x14140`, `0x14178`, `0x141c4`, `0x141dc`, `0x141f4`, `0x1423c`, `0x14254`]** i nomi delle uniform sono puntatori a stringhe non tipizzati (`UNK_...`); la semantica sopra deriva dai valori passati, non dai nomi.

## `AdvectVelocity` — `0x17b78`

- **[CONFIRMED @ `0x17ba0-0x17bf4`]** ABI: `Texture<vec2> velocity`, `Texture<vec2> source`, `Texture<vec2> output`, `float dissipation`; le tre triplette sono copiate rispettivamente negli scratch `Fluid+0x1fc`, `+0x208`, `+0x18c`, dissipazione in `+0x228`.
- **[CONFIRMED @ `0x17c04-0x17c20`]** pre-calcola `(1/normX,1/normY)` e `(logicalX/normX,logicalY/normY)`.
- **[CONFIRMED @ `0x17cb4-0x17ec0`]** loop signed: esce se `outHeight <= 0`; per ogni riga esce/skippa se `outWidth <= 0`; ordine `y` esterno, `x` interno, stride output `outWidth*8`.
- **[CONFIRMED @ `0x17d88-0x17e00`]** backtrace identico all'inline: `(cell - dt*velocity + 0.5) * invNorm`, poi `texture2DLinear(source, uv)` (`bl 0x16328` a `0x17e00`).
- **[CONFIRMED @ `0x17e40-0x17e88`]** impulso opzionale entro raggio quadrato `625.0f` (literal `0x441c4000` a `0x17ed0`), fattore `activeCount-1.5f`, direzione `(DX,-DY)`.
- **[CONFIRMED @ `0x17d54-0x17d70`]** output finale = campione/impulso moltiplicato per dissipazione; helper `vec2*float @ 0x1837c`.
- **[PROBABLE @ `0x17d90`]** il primo input viene indicizzato con lo stride di output, non con il proprio `width`; il codice presume texture della stessa dimensione. Il port deve mantenere questo pre-requisito o scegliere esplicitamente uno stride.

## `Jacobi` — `0x14400`

- **[CONFIRMED @ `0x14418-0x14470`]** ABI: `Texture<float> x`, `Texture<float> b`, `Texture<float> out`; `out` e negli argomenti 8..10, `x` in 2..4, `b` in 5..7.
- **[CONFIRMED @ `0x14478-0x14494`]** coefficienti: `alpha = -(cellSize*cellSize)` (`vnmul` a `0x14478`), `beta = 0.25f` (`0x3e800000` a `0x1447c`), piu inversi di `+0xa0/+0xa4` memorizzati ma non usati nel ramo CPU osservato.
- **[CONFIRMED @ `0x14498-0x144b0`]** se `out.data == null` ritorna; altrimenti `memset(out.data,0,out.width*out.height*4)`.
- **[CONFIRMED @ `0x144cc-0x14864`]** calcola soltanto celle interne: `y=1..out.height-2`, `x=1..out.width-2`; bordi restano zero dal `memset`. Il loop esterno torna a `0x14524`, quello interno scalare a `0x14624`; il percorso NEON elabora quattro `x` per volta a `0x14554-0x145bc`.
- **[CONFIRMED @ `0x147d8-0x14814`]** formula scalar: `out = (xTop + xBottom + xRight + xLeft + alpha*bCenter) * 0.25f`, con clamp delle coordinate contro le dimensioni delle rispettive texture.
- **[CONFIRMED @ `0x14584-0x145b8`]** il percorso NEON conserva la stessa associazione matematica a blocchi di quattro float; per parity bit-level il port deve considerare che l'ordine FP vettoriale puo differire da una semplice espressione C scalar.
- **[CONFIRMED @ `0x144b0`]** unico callee non intrinseco: wrapper `memset @ 0x12e94`; nessun chiamante `bl` interno nominale, perche `Update` contiene una copia inline.

## `SubtractGradient` — `0x14880`

- **[CONFIRMED @ `0x1489c-0x14910`]** ABI: `Texture<vec2> velocity`, `Texture<float> pressure`, `Texture<vec2> out`; scratch rispettivamente `Fluid+0x408`, `+0x414`, `+0x398`.
- **[CONFIRMED @ `0x14924-0x14bd8`]** se `out.data != null` e `out.height > 0`, loop `y` esterno e `x` interno, entrambi da zero a dimensione output esclusiva; output stride `out.width*8`.
- **[CONFIRMED @ `0x149c4-0x14b40`]** campiona/clampa `pressure(x+1,y)`, `pressure(x-1,y)`, `pressure(x,y+1)`, `pressure(x,y-1)` e la velocita centrale. I confronti sono signed (`cmp` con rami `blt/ble/bge`).
- **[CONFIRMED @ `0x14b40-0x14ba4`]** formula: `grad = vec2(pRight-pLeft, pTop-pBottom)`; `out = velocityCenter - (Fluid+0xc4)*grad`. Le chiamate helper sono `Vec2 @ 0x181c4`, `vec2*float @ 0x1837c`, `vec2-vec2 @ 0x18314`.
- **[PROBABLE @ `0x14894`, `0x14b78`]** l'orientamento nominale top/bottom dipende dalla convenzione Y del chiamante; l'ordine numerico certo e `sample(y+1) - sample(y-1)`.
- **[CONFIRMED @ `0x14880`]** nessun chiamante `bl` interno nominale; copia inline in `Update @ 0x17724`.

## `ComputeDivergence` — `0x14be4`

- **[CONFIRMED @ `0x14c14-0x14c8c`]** ABI: `Texture<vec2> velocity`, `Texture<float> out`, `Vector_ impulsePosition`; input scratch `Fluid+0x4a0`, output `+0x430`, posizione/raggio a `+0x4c8/+0x4cc`.
- **[CONFIRMED @ `0x14cb4-0x14ce0`]** pre-calcola scale coordinate e `0.5f/cellSize`; il `0.5f` e materializzato a `0x14c5c`, il risultato viene salvato nello scratch corrispondente a `Fluid+0x4bc`.
- **[CONFIRMED @ `0x14ce8-0x14f50`]** loop signed `y` esterno, `x` interno; tutte le quattro coordinate vicine sono clampate ai bordi della texture input.
- **[CONFIRMED @ `0x14ecc-0x14ee4`]** formula esatta: `div = ((vTop.y + (vRight.x - vLeft.x)) - vBottom.y) * (0.5f/cellSize)`; l'ordine FP assembly e `right-left`, poi `top+delta`, poi `-bottom`, poi moltiplicazione.
- **[CONFIRMED @ `0x14ee8-0x14f20`]** calcola distanza quadrata cella/impulso; se `< radius*radius`, sottrae `Fluid+0xdc` dalla divergenza. Il confronto FP usa `vcmpe`/`vldrmi`/`vsubmi`.
- **[CONFIRMED @ `0x14be4`]** nessun chiamante `bl` interno nominale; copia inline in `Update @ 0x16ce4-0x170d0`.

## Caller/callee e limiti

- **[CONFIRMED @ `0x16c58`]** relazione diretta recuperata: `Update -> AdvectDensity`.
- **[CONFIRMED @ `0x164f8-0x17b74`]** `Update` chiama helper matematici (`0x181c4`, `0x182f0`, `0x18338`, `0x1835c`, `0x1837c`, `0x18668`), sampler lineare `0x16328`, alloc/free/GL wrapper, ma non chiama gli entry point nominali `0x14400/0x14880/0x14be4/0x17b78`.
- **[CONFIRMED @ `0x14118`, `0x14400`, `0x14880`, `0x14be4`, `0x164f8`, `0x17b78`]** gli xref Ghidra degli entry point mostrano esposizione `EXTERNAL`; il database non fornisce un chiamante interno aggiuntivo.
- **[UNRESOLVED @ `0x12d74-0x12f48`]** molti wrapper import/PLT hanno nomi generici `func_...`; le associazioni GLES sono solide dai token GL e dalla sequenza, ma non sono state rinominate nel database per rispettare la modalita read-only.

## Implicazioni per il port AArch64

- **[CONFIRMED @ `0x14400`, `0x14880`, `0x14be4`, `0x17b78`]** usare `int32_t` per dimensioni/indici e controlli signed; non convertire i loop in `size_t` senza preservare i rami `<=0`.
- **[CONFIRMED @ `0x14498`, `0x170d4`]** preservare gli azzeramenti espliciti prima di Jacobi: i bordi zero fanno parte del risultato.
- **[CONFIRMED @ `0x176f8-0x17720`]** preservare lo swap pressione dopo ogni iterazione, inclusa l'ultima: `SubtractGradient` legge la tripletta diventata corrente.
- **[CONFIRMED @ `0x1689c`, `0x17a98`]** preservare entrambi gli swap velocita: uno subito dopo advezione, uno dopo proiezione.
- **[PROBABLE @ `0x14554-0x145bc`]** per confronto frame/hash, disabilitare FP contraction/fast-math finche il percorso AArch64 non e validato contro l'ordine ARM/NEON.
