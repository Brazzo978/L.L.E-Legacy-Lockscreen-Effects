# S3 Water Ripple ARM64 — specifica completa di fedeltà

- Data di congelamento: 2026-07-14
- Baseline sorgente: commit `e0ad7bd` (`feat: ship S3 Water Ripple ARM64 early alpha`)
- Package host: `com.codex.lle`
- Picker ID: `10` (`EFFECT_S3_RIPPLE_NATIVE`)
- Nome UI: `S3 Water Ripple (Early Alpha)`

Questo documento è la specifica canonica dell'implementazione Water Ripple corrente. Registra logica, fisica, ordine numerico, mesh, input, rendering, compositing, lifecycle, asset, packaging, prove e divergenze. Va aggiornato insieme al codice se uno di questi comportamenti cambia.

## 1. Livelli di fedeltà

Le affermazioni usano tre categorie:

- **SAMSUNG-EXACT**: valore, formula, ordine o comportamento recuperato dall'ARM32/smali originale e mantenuto letteralmente.
- **EQUIVALENTE ARM64**: cambia solo la gestione sicura di errori, thread, ownership o context loss; il risultato valido atteso non cambia.
- **COMPATIBILITÀ LLE**: divergenza intenzionale necessaria per disegnare sopra la SystemUI reale senza coprirla.

Stato sintetico:

- core altezza/velocità, mesh, shear, coordinate, input, timing nominale, shader e ottica normal: alta confidenza **SAMSUNG-EXACT**;
- JNI/lifecycle/cleanup: **EQUIVALENTE ARM64**;
- alpha locale e background screenshot: **COMPATIBILITÀ LLE**;
- parità raster bit-per-bit fra la GPU S3 e la GPU Fold: non dichiarata.

## 2. Fonti canoniche

### Binario e smali originali

- `LLE64/reference/arm32-original/vendor/original-native/libWaterRipple.so`
  - ARM32, 70.896 byte;
  - SHA-256 `96088C44C40C0E1DF52D32B9D9B47506B6B2A1B9E8C2D9BE55FCEA5366CF48A3`.
- `unlock-effects-test/demo-apk/smali_s3_ripple/com/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer.smali`
- `unlock-effects-test/demo-apk/smali_s3_ripple/com/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender.smali`

### Report reverse read-only

- `LLE64/reverse/s3-water-ripple/fluid-map-agent.md`: `initWaters`, `ripple`, `move`, loop e ordine floating point.
- `LLE64/reverse/s3-water-ripple/render-map-agent.md`: upload, VBO/IBO, draw e uniform.
- `LLE64/reverse/s3-water-ripple/shader-map-agent.md`: blob GLSL e formule shader.
- `LLE64/reverse/s3-water-ripple/lifecycle-map-agent.md`: JNI, Java caller, touch, audio, ownership e cleanup.
- `LLE64/reverse/s3-water-ripple/BENCHMARK.md`: prove ARM32/ARM64 iniziali.

I database Ghidra sono stati usati in sola lettura. Il bulk mesh ambiguo nel decompilato è stato risolto verificando assembly/P-code e facendo eseguire direttamente la routine ARM32: la piccola shear descritta sotto è reale.

## 3. Mappa dei file implementativi

### Host Java

- `LLE64/src/com/codex/lle/S3Arm64RippleEffectView.java`
  - `GLSurfaceView` trasparente;
  - state machine input;
  - clock fisico;
  - packing height→GPU;
  - matrice MVP;
  - background/reflection map;
  - audio, affordance, pause e destroy.
- `LLE64/src/com/codex/lle/S3RippleLifecycleNative.java`
  - caricamento `libWaterRipple.so`;
  - controllo bridge ABI v2;
  - firme JNI app-owned.
- `LLE64/src/com/codex/lle/TouchDebugView.java`
  - selezione pointer attivo;
  - soppressione e riallineamento multi-touch.
- `LLE64/src/com/codex/lle/ChargingAccessibilityService.java`
  - factory/picker;
  - screenshot background;
  - lifecycle overlay e fallback;
  - terminal UP alle coordinate reali.

### Core e GLES nativi

- `ripple_core.c/.h`: mesh, indici, iniezione, solver altezza/velocità.
- `water_ripple_jni_core.c`: ABI JNI Samsung `initWaters/move/ripple`.
- `ripple_gles_shaders.c/.h`: shader Samsung recuperati byte per byte.
- `ripple_gles_pipeline.c/.h`: programmi, buffer, texture, FBO e draw.
- `ripple_gles_overlay_shader.c/.h`: sola variante fragment trasparente LLE.
- `ripple_gles_overlay.c/.h`: selezione esplicita Samsung-exact/LLE transparent.
- `water_ripple_jni_lifecycle.c`: singleton GLES JNI v2 e upload bitmap.

### Test

- `ripple_core_test.c`: mesh golden, inject/move e hash del campo.
- `ripple_gles_static_test.c`: costanti shader e quad `GL_SHORT`.
- `ripple_gles_device_test.c`: EGL/GLES2 reale e cinque programmi.
- `ripple_gles_overlay_device_test.c`: framebuffer RGBA8 e `glReadPixels`.

## 4. Dimensioni, array e memoria logica

Valori attivi normal mode:

```text
DETAIL_WIDTH       = 104
DETAIL_HEIGHT      = 104
SURFACE_WIDTH      = 100
SURFACE_HEIGHT     = 100
MESH_WIDTH         = 50
MESH_HEIGHT        = 50
vertex count       = 10.000
index count        = 99 * 99 * 6 = 58.806
physics cells      = 104 * 104 = 10.816
```

Array Java:

- `vertices`: 30.000 `float`, tre componenti per vertice;
- `gpuHeights`: 30.000 `float`, tre altezze/vicini per vertice;
- `indices`: 58.806 `short`;
- `heights`: 10.816 `float`;
- `velocity`: 10.816 `float`.

Il core e la pipeline mantengono semantica singleton, come l'originale ARM32. `NATIVE_OWNER` impedisce due view simultanee nello stesso processo.

## 5. Inizializzazione mesh e shear NEON storica

### 5.1 Bulk ARM32, vertici divisibili per quattro

Per indice lineare `v`:

```text
rowFraction = float(v) / surfaceWidth
row         = trunc(rowFraction)
column      = v - row * surfaceWidth

x = rowFraction * (meshHeight / (surfaceHeight - 1)) - meshHeight / 2
y = -(column * (meshWidth / (surfaceWidth - 1)) - meshWidth / 2)
z = 0
```

Il punto importante è `rowFraction` non troncato nel calcolo di `x`. Ogni colonna aggiunge una piccola componente X: non è un errore del port, ma la shear prodotta dal bulk NEON Samsung. Sul profilo 100×100 arriva a circa `0.5` unità logiche, circa 11 px su 1080 px.

Il calcolo ARM32 usa reciprocal estimate/refinement NEON; ARM64 C può differire di pochi ULP. I golden usano tolleranza assoluta `0.00005`, abbastanza stretta da impedire la rimozione della shear.

Golden eseguiti dall'ARM32:

```text
v=0     (-25.00000000,  25.00000000)
v=1     (-24.99494934,  24.49494934)
v=99    (-24.50000000, -24.99999619)
v=100   (-24.49494934,  25.00000000)
v=5000  (  0.25253487,  25.00000000)
v=9999  ( 25.50001907, -24.99999619)
```

I profili Samsung supportati 100×100 e 70×70 hanno un numero di vertici divisibile per quattro, quindi usano integralmente questo bulk.

### 5.2 Tail scalare diagnostico

Per un eventuale tail non divisibile per quattro:

```text
row    = v / surfaceWidth       // intero
column = v % surfaceWidth
x = row * rowStepX - halfX
y = -(column * columnStepY - halfY)
```

La differenza bulk/tail è mantenuta perché esiste nell'originale, anche se non entra nei profili reali correnti.

### 5.3 Indici

Loop, ordine e stride:

```text
for x = 1 .. surfaceHeight-1
  for y = 1 .. surfaceWidth-1
    bottomRight = x * surfaceHeight + y
    topLeft     = bottomRight - surfaceHeight - 1
    topRight    = bottomRight - surfaceHeight
    bottomLeft  = bottomRight - 1

    triangle 1 = topLeft, topRight, bottomRight
    triangle 2 = topLeft, bottomRight, bottomLeft
```

Lo stride è intenzionalmente `surfaceHeight`. I profili reali sono quadrati.

## 6. Coordinate touch e basis trasposta

Le coordinate ricevute dal service sono screen-space. La view sottrae `getLocationOnScreen()` e lavora in coordinate locali Android, X verso destra e Y verso il basso.

Rapporti:

```text
portrait:  xRatio=30, yRatio=46, intensity=0.50
landscape: xRatio=45, yRatio=25, intensity=0.35
```

Mapping:

```text
glX = (localX - surfacePixelWidth/2) * xRatio / surfacePixelWidth
glY = (localY - surfacePixelHeight/2) * yRatio / surfacePixelHeight
```

Il caller Samsung invoca poi:

```text
ripple(glY, glX, strength)
```

L'ordine scambiato è obbligatorio. La mesh, il mapping native e il packing height sono una basis trasposta coerente; “normalizzare” uno solo dei tre produce direzione ruotata/opposta.

## 7. Iniezione del ripple nel campo velocità

Con coordinate native `meshX/meshY`:

```text
cellX = (meshX / meshWidth  + 0.5) * detailWidth
cellY = (meshY / meshHeight + 0.5) * detailHeight

xBegin = cellX < 5 ? 2 : floor(cellX - 3)
yBegin = cellY < 5 ? 2 : floor(cellY - 3)
xEnd   = cellX < detailWidth  - 5 ? floor(cellX + 4) : detailWidth  - 1
yEnd   = cellY < detailHeight - 5 ? floor(cellY + 4) : detailHeight - 1
```

I limiti finali sono esclusivi. Per ogni cella:

```text
dx       = cellX - x
dy       = cellY - y
distance = sqrt(dx*dx + dy*dy)
impulse  = 3 - distance

if impulse > 0:
    velocity[y * detailWidth + x] += impulse * strength
```

Il raggio effettivo è 3 celle e l'iniezione modifica solo `velocity`.

Golden centrale con `strength=2.0`:

```text
center velocity = 6.0
right velocity  = 4.0
down velocity   = 4.0
```

## 8. Solver fisico `move`

### 8.1 Laplaciano e ordine aritmetico

Per indice `i` e stride `detailWidth`:

```text
L = height[i-stride] - 4*height[i]
L = L + height[i-1]
L = L + height[i+1]
L = L + height[i+stride]
```

Questa sequenza di addizioni è mantenuta deliberatamente; non va riscritta come somma vettoriale o con `fast-math`.

### 8.2 Bounds S3 letterali

```text
portrait:  x=[3,101),  y=[21,83)
landscape: x=[21,83),  y=[3,101)
```

Il core clampa inoltre begin ad almeno 1 ed end al massimo `detail-1`. Questi bounds sono S3 letterali, non derivati dal rapporto del Fold.

### 8.3 Passo 1 — velocità

Loop **X-major/Y-minor**:

```text
for x
  for y
    i = y * detailWidth + x
    nextVelocity = (velocity[i] + laplacian(height,i) * 0.5) * 0.94
    velocity[i] = nextVelocity
```

Se `checkEmpty=true`, il campo non è vuoto appena una velocità soddisfa:

```text
nextVelocity > 0.01 || nextVelocity < -0.01
```

### 8.4 Passo 2 — altezza

Stesso ordine X-major/Y-minor:

```text
height[i] = clamp(height[i] + velocity[i], -100, 100)
```

### 8.5 Passo 3 — smoothing in-place

Per damping esattamente `0.94f`:

```text
height[i] = height[i] + laplacian(height,i) * 0.068
```

Per gli altri damping l'originale sceglie `0.018`. Il passaggio è **in-place** e X-major/Y-minor: le celle successive osservano valori già aggiornati. Non usare un buffer temporaneo.

### 8.6 Stato idle

Il native restituisce “empty” se nessuna velocità supera `±0.01`. Java entra in idle solo se il native è empty **e** `glTouched=false`. Dopo almeno due draw passa a `RENDERMODE_WHEN_DIRTY`; un nuovo input torna a rendering continuo.

Hash golden dopo inject/move di test:

```text
59890e7812c02590
centerVelocity=2.50805116
centerHeight=7.21869993
```

## 9. Packing del campo per lo shader

La GPU riceve tre float per vertice. Per `i` riga surface e `j` colonna surface:

```text
target = (SURFACE_HEIGHT * j + i) * 3

gpu[target+0] = heights[(j+2) * DETAIL_WIDTH + (i+2)]
gpu[target+1] = heights[(j+2) * DETAIL_WIDTH + (i+1)]
gpu[target+2] = heights[(j+1) * DETAIL_WIDTH + (i+2)]
```

Anche questo packing è trasposto. Le tre componenti diventano altezza corrente e due vicini usati dal normal vertex shader per costruire la normale.

## 10. Clock, frame order e refresh rate

Samsung eseguiva un `move()` per frame su display nominale 60 Hz. LLE rende alla frequenza pannello ma fa avanzare la fisica con un accumulatore razionale a 60 Hz:

```text
accumulatorUnits += elapsedNanoseconds * 60
steps = accumulatorUnits / 1.000.000.000
accumulatorUnits -= steps * 1.000.000.000
```

Proprietà:

- il primo `advance()` inizializza il timestamp e restituisce 0;
- massimo 4 step fisici recuperati per frame;
- delta frame limitato a circa 66,666667 ms;
- accumulatore massimo `4 * 1.000.000.000` unità;
- reset su surface/init, idle→touch, reset, release e quando il campo torna idle.

Ordine di ogni frame valido:

```text
1. clear RGBA/depth trasparente
2. draw usando gpuHeights correnti
3. calcolo numero di step 60 Hz
4. zero o più move()
5. fillGpuHeights() dopo ogni move
```

Il primo draw non esegue `move()`, come nello smali. A 120 Hz normalmente un frame fa 0 step e il successivo 1; a 60 Hz normalmente ogni frame fa 1.

Verifiche clock già eseguite:

```text
10 secondi -> 600 step a 30/48/60/80/90/120/144 Hz
sequenza 120→60→120→30 -> 1200 step esatti sul periodo provato
```

Il limite di quattro catch-up step durante jank è una scelta **EQUIVALENTE ARM64/stabilità**, non un comportamento misurato sull'S3.

## 11. State machine gesture

### ACTION_DOWN

```text
glTouched = true
activeDownTime = downTime
previousTouch = currentTouch
rippleDistance = 0
inject(strength = 4 * intensity)
play s3_ripple_down
```

Strength risultante:

```text
portrait  = 2.00
landscape = 1.40
```

### ACTION_MOVE

Per ogni segmento:

```text
dx = x - previousX
dy = y - previousY
rippleDistance += int(sqrt(dx*dx + dy*dy))
previousTouch = currentTouch
```

La soglia è **strettamente** `>150 px`. Quando viene superata:

```text
rippleDistance = 0
strength = 3 * intensity
inject subito
inject stessa posizione/strength dopo 20 ms
inject stessa posizione/strength dopo 40 ms
play s3_ripple_up una sola volta per evento soglia
```

Strength:

```text
portrait  = 1.50 per impulso
landscape = 1.05 per impulso
```

I due callback ritardati hanno una generation. Reset e destroy incrementano la generation, rimuovono i callback UI e fanno scartare anche eventuali comandi già accodati al GL thread.

### ACTION_UP

L'UP usa direttamente le coordinate finali reali; il service non invia un MOVE sintetico prima dell'UP per S3.

Se la durata è **strettamente** `>600 ms` e `glTouched=true`:

```text
inject(strength = 4 * intensity)
play s3_ripple_down
```

Poi azzera touch, down time e distanza. Il boolean `completed/unlockTriggered` dell'host non modifica la fisica S3.

Decisione unlock host, separata dall'effetto:

```text
distance = hypot(currentScreenX-startScreenX, currentScreenY-startScreenY)
unlockTriggered = distance >= 120 dp
delay apertura PIN per S3 = 400 ms (ramo default host)
delay inizio swipe PIN     = 60 ms
durata swipe PIN           = 260 ms
cleanup/park effetto       = 900 ms
```

La soglia host usa la distanza diretta dal punto iniziale; la soglia ripple da 150 px usa invece la somma intera dei segmenti MOVE. Sono due contatori indipendenti.

### ACTION_CANCEL

Chiude lo stato touch e azzera distanza/down time senza nuovo impulso e senza suono.

### Multi-touch

Comportamento originale preservato:

- finché `pointerCount>1`, gli eventi sono soppressi;
- la gesture non riceve un CANCEL e non genera un nuovo DOWN;
- quando resta un dito viene selezionato il vero pointer rimasto;
- `previousTouch` viene riallineato e `rippleDistance=0`;
- l'onda già esistente continua a decadere naturalmente;
- il prossimo movimento riparte senza salto e senza impulso artificiale.

Se durante due dita l'effetto sembra “chiudersi”, è solo il decadimento naturale dovuto all'assenza di nuove iniezioni; l'overlay/gesture non viene distrutto.

### Affordance screen-on

L'affordance inietta al centro `4 * intensity`, senza suono. Viene cancellato su primo touch, reset e destroy. Una generation volatile impedisce che un comando centro già accodato venga eseguito dopo il touch reale.

## 12. Audio

`SoundPool`:

```text
maxStreams = 10
usage      = USAGE_ASSISTANCE_SONIFICATION
content    = CONTENT_TYPE_SONIFICATION
volume     = 1.0 / 1.0
loop       = 0
rate       = 1.0
```

Gate audio:

- toggle suono effetto LLE;
- finestra oraria suono effetto LLE;
- `Settings.System[lockscreen_sounds_enabled]`.

Il caricamento SoundPool è asincrono: un tocco immediato dopo la costruzione può essere silenzioso. Il pool viene rilasciato in `destroy()`.

Dettaglio originale non replicato: `playDragSound()` sottrae 1 allo stream ID restituito, costruisce un oggetto `Thread` ma chiama `run()` sincronicamente, quindi tenta un fade in 5 step da 10 ms (`1.0→0.8→0.6→0.4→0.2→0`) bloccando il thread UI per circa 50 ms e probabilmente agendo sullo stream sbagliato. LLE conserva asset, istante e volume iniziale ma usa un one-shot SoundPool stabile. È una divergenza audio/lifecycle intenzionale, non una differenza della fisica.

Asset originali:

```text
s3_ripple_down.ogg
size    11.571
SHA-256 AD1667363A2E6E753EA002FC5987FA63EB8E07A6853648BBA9C8835307B46107

s3_ripple_up.ogg
size    20.394
SHA-256 E40474452A9033B588ECB9801BDD34168B0D5CEA51F4CB31BDFE3F9CF00445FB
```

## 13. Matrice e proiezione

View matrix:

```text
eye    = (0,0,1)
center = (0,0,0)
up     = (0,1,0)
```

Projection helper Samsung, intenzionalmente non standard:

```text
angle  = 45.0
aspect = pixelWidth / pixelHeight
near   = 0.1
far    = 500.0

f = tan(0.5 * (PI - angle))
range = near - far

m[0]  = f / aspect
m[5]  = f
m[10] = far / range
m[11] = -1
m[14] = near * far / range
```

Translate Z:

```text
portrait  -43.05
landscape -23.8
```

Adattamento mesh inviato agli uniform:

```text
bitmapRatio = max(pixelWidth,pixelHeight) / min(pixelWidth,pixelHeight)

portrait:
  renderMeshWidth  = int(50 / bitmapRatio), minimo 1
  renderMeshHeight = 50

landscape:
  renderMeshWidth  = 50
  renderMeshHeight = int(50 * bitmapRatio), minimo 1
```

Gli uniform detail passati al normal vertex shader sono `52,52` (`104/2`), coerenti col caller ricostruito.

## 14. Shader normal Samsung

Gli shader Samsung-exact non sono stati modificati per ottenere la trasparenza. Hash sorgenti congelati:

```text
ripple_gles_shaders.c
SHA-256 D0F895892896502C0E00DB490AEE1A9B41B1D06A75389E6E50D6FB871DDD50CB

ripple_gles_shaders.h
SHA-256 A20291352AF4EB56B091BC1FBE25E2641276211A0F41498FAE638BB6CD84A2EB
```

Sono presenti otto sorgenti uniche:

- quad vertex;
- AdvectDensity fragment;
- AddInk fragment;
- normal vertex;
- normal fragment;
- ink fragment;
- gravity vertex;
- gravity fragment.

Nel binario ARM32 i blob fisici sono nove perché il normal vertex è duplicato byte-identico.

### Normal vertex essenziale

```glsl
vec3 v = vec3(pos.x, pos.y, heights.x * 0.25);
vec2 n = (vec2(heights.x) - heights.yz) * 0.25;
float nz = sqrt(dot(n,n) + 1.0);
n /= nz;

vec3 d = normalize(vec3(v.x, v.y, v.z + 30.0));
float t = dot(d, vec3(n.x,n.y,1.0)) * (uRefractiveIndex - 1.0);
d.xy += n * t;

r0 = (30.9 - v.z) / d.z;
u0 = (d.x*r0 + v.x) / maxX * 0.25 + 0.5;
v0 = (d.y*r0 + v.y) / maxY * -0.25 + 0.5;
```

Reflection coordinates:

```glsl
uxx = n.x*0.5 + 0.5 + pos.y/uMESH_SIZE_WIDTH*0.25;
vxx = n.y*0.5 + 0.5 + pos.x/uMESH_SIZE_HEIGHT*0.25;
```

Normal e height:

```glsl
vNormal  = normalize(vec3(n.x,n.y,0.6));
vHeights = aHeights.x;
```

### Parametri ottici normal

```text
refractiveIndex = 0.93
reflectionRatio = 0.13
alphaRatio1     = 1.0
alphaRatio2     = 1.0
fresnelRatio    = 0.1
specularRatio   = 0.5
exponent        = 20.0
```

La pipeline invia:

```text
alphaRatio1 uniform = 1.0 * 0.13 = 0.13
alphaRatio2 uniform = 1.0 * (1 - 0.13) = 0.87
```

Il normal fragment usa `alphaRatio1`; `alphaRatio2` serve alle altre varianti.

### Normal fragment Samsung-exact

```glsl
NdotHV  = max(dot(vNormal,vHalfVec),0.0);
t       = clamp(abs(vHeights),0.0,1.13);
specular = clamp(0.5 * pow(NdotHV,20.0),1.0,5.5);
NdotL   = max(dot(vNormal,vec3(5.0,-5.0,1.0)),0.0);

rippleRGB = t * specular * waterColor.rgb
           * (0.13 + 0.1 * clamp(NdotL-0.99,0.0,0.3))
           + bgColor.rgb;

gl_FragColor = vec4(rippleRGB,1.0);
```

Texture unit normal:

```text
0 = background
1 = water/reflection map
```

Geometry draw:

```text
positions  vec3 GL_FLOAT, GL_DYNAMIC_DRAW
heights    vec3 GL_FLOAT, GL_DYNAMIC_DRAW
indices    GL_UNSIGNED_SHORT, GL_DYNAMIC_DRAW
primitive  GL_TRIANGLES
```

## 15. Reflection map

```text
LLE64/res/drawable-nodpi/s3_reflectionmap.jpg
size    32.835
SHA-256 061CE08AC983BC1DAF0C7E169AB55C51BC5BCB212DFD2404518428091B799130
```

Viene decodificata con `inScaled=false`, normalizzata ad `ARGB_8888`, caricata sincronicamente in texture unit 1 e mantenuta dalla view fino al cleanup.

## 16. Compositing trasparente LLE

### Motivo della divergenza

Il fragment Samsung scrive alpha `1.0` e ricostruisce il wallpaper fullscreen. In un accessibility overlay questo coprirebbe la lockscreen reale. LLE conserva intatti gli shader originali e seleziona esplicitamente un secondo fragment compatibilità.

Modalità:

```text
LLE_RIPPLE_NORMAL_COMPOSITE_SAMSUNG_EXACT       = 0
LLE_RIPPLE_NORMAL_COMPOSITE_TRANSPARENT_DELTA   = 1  // attiva nell'app
```

### Maschera e output

Default:

```text
maskLow  = 0.035
maskHigh = 0.180
opacity  = 1.0
```

Formula:

```glsl
slopeStrength = length(vNormal.xy);
mask = smoothstep(0.035,0.180,slopeStrength);
samsungResult = clamp(rippleRGB,0.0,1.0);
out = vec4(mask * samsungResult, mask);
```

È output premoltiplicato (`RGB<=A`). Il framebuffer viene prima pulito a `(0,0,0,0)`. Il draw disabilita `GL_BLEND`; la composizione source-over avviene successivamente in Android/SurfaceFlinger.

Con background cache uguale al contenuto live, il risultato percepito è approssimativamente:

```text
liveBackground + mask * (SamsungResult - liveBackground)
```

Questa attenuazione è intenzionale nella Early Alpha. Un compositing adattivo che ricostruisca più esattamente `SamsungResult` è rimandato perché richiede un secondo sample background non rifratto e test A/B contro ghosting da cache stale.

Test EGL 64×64 sul Fold:

```text
flatNonzero=0
impulseNonzero=154
centerNonzero=154
borderNonzero=0
rgbOverAlpha=0
maxAlpha=255
```

## 17. Background LLE

Samsung usa il wallpaper. LLE usa una screenshot accessibility/SystemUI reale come color map.

Pipeline:

1. attende almeno 1.400 ms dopo screen-on per il candidato Ripple;
2. recupera/cachea screenshot per effetto;
3. copia software `ARGB_8888` non mutable;
4. center-crop al rapporto della surface;
5. scala con filtro+dither;
6. assegna un seriale ownership;
7. carica sul GL thread;
8. scarta comandi con seriale obsoleto;
9. se il nuovo upload fallisce, tenta di ricaricare il bitmap precedente.

Il bitmap è posseduto dalla view fino a sostituzione/clear/destroy. Fold aperto/chiuso e sorgente wallpaper originale sono esplicitamente fuori dalla fase corrente.

Timing host collegati al Ripple:

```text
minimo screen-on prima screenshot Ripple = 1400 ms
retry minimo screenshot                 = 90 ms
tentativi screenshot                    = 2
rate-limit log attesa screenshot        = 1000 ms
affordance screen-on                    = +500 ms
cleanup effetto dopo apertura PIN       = 900 ms (default host)
readiness check iniziale                 = +250 ms
readiness check successivi               = ogni 1000 ms
```

Il renderer Ripple resta allegato quando l'overlay nativo viene parcheggiato/nascosto e viene ricreato se cambiano le dimensioni display. Il background cache è separato per effect ID.

## 18. Upload bitmap JNI

Requisiti:

- software bitmap;
- `ANDROID_BITMAP_FORMAT_RGBA_8888`;
- width/height non zero e rappresentabili da `int`;
- stride almeno `width*4`.

Sequenza:

```text
AndroidBitmap_getInfo
AndroidBitmap_lockPixels
se stride != width*4: copia row-by-row in buffer tight
glTexImage2D sincrono
free buffer temporaneo
AndroidBitmap_unlockPixels
```

Nessun pointer Java viene conservato dopo unlock. Texture RGBA8: clamp-to-edge, min/mag `GL_LINEAR`.

## 19. Lifecycle EGL e thread

Tutte le operazioni su simulazione, buffer e GLES vengono serializzate sul render thread tramite `queueEvent()`.

Configurazione surface:

```text
GLES context version       = 2
EGL color                  = RGBA 8/8/8/8
EGL depth                  = 16
EGL stencil                = 0
preserve context on pause  = false
Z order                    = on top
holder format              = TRANSLUCENT
default render mode        = WHEN_DIRTY
clear color                = 0,0,0,0
```

### Context creation

- `contextGeneration++`;
- clear trasparente;
- `nativeAbandonGpu()` per azzerare i nomi del context perso senza chiamare `glDelete*` nel context nuovo;
- `initWaters()`;
- decode/ownership reflection map.

### Surface change

- confronta generation, width e height;
- stesso context + resize: `nativeDestroyGpu()` completo, poi singola init;
- context nuovo: vecchi nomi già abbandonati;
- init programmi/buffer;
- rimappa e carica background/reflection.

### Pause e destroy

- cleanup accodato nel context corrente;
- attesa propria massima 350 ms;
- poi `GLSurfaceView.onPause()`;
- destroy finale cancella affordance e trail, ricicla bitmap, rilascia SoundPool e libera il singleton.

`destroy()` e `abandon()` sono idempotenti. In caso di timeout driver, l'host prosegue senza usare nuovamente lo stato distrutto.

### Readiness/fallback

La view è ready solo se:

```text
ownsNativeSlot
&& !destroyed
&& bridgeVersion == 2
&& !initializationFailed
```

Il service controlla periodicamente readiness. Se init/render fallisce:

- distrugge l'overlay Ripple;
- salva Lens Flare come fallback;
- ricrea la superficie;
- registra motivo e errore native.

## 20. Pipeline GLES disponibile ma non esposta

La libreria contiene e testa cinque programmi:

- normal;
- ink;
- AdvectDensity;
- AddInk;
- gravity.

La view Early Alpha chiama esclusivamente normal. Ink/gravity non sono selezionabili e non devono essere descritti come completi lato state machine Java. Gravity richiede inoltre asset/modalità originali non inclusi nel target corrente.

Configurazione legacy salvata in `res/values/s3_ripple.xml`:

```text
s3_config_is_jbp_upgrade           = false
s3_config_is_water_ink_enabled     = false
s3_config_is_water_ink_lcd         = true
s3_restrict_cpu_clock_ripple       = false
s3_restrict_gpu_freq_ripple        = false
s3_cpu_clock_index_for_ripple      = 1574400
s3_gpu_freq_index_for_ripple       = 389000000
```

La view ARM64 normal corrente non legge questi valori: sono conservati come configurazione/reference Samsung e non implicano DVFS o modalità ink attive.

Il quad fluid è esattamente 32 byte:

```text
{position.xy, uv.xy} * 4 vertici
tipo GL_SHORT
stride 8
position offset 0
uv offset 4
primitive GL_TRIANGLE_STRIP
```

## 21. Differenze lifecycle intenzionali non visive

Rispetto all'ARM32:

- compile/link shader fallito è sempre errore; non replica il bug “info log vuoto = successo”;
- shader object eliminati dopo link invece di essere persi;
- FBO incompleto fallisce e viene distrutto invece di proseguire;
- ricaricare una texture elimina prima il vecchio nome;
- errori GL preesistenti vengono drenati (massimo 16 letture) prima dell'operazione;
- init costruisce i cinque programmi in un passaggio;
- resize/context loss non replica leak o doppie init;
- bitmap stride e ownership sono validate esplicitamente.

Queste differenze sono classificate **EQUIVALENTE ARM64** e non cambiano la matematica di un frame valido.

## 22. Build e ABI

Compilazione native normal:

```text
NDK r27d
target aarch64-linux-android23
-std=c11
-O2
-fno-fast-math
-ffp-contract=off
-shared -fPIC
-Wall -Wextra -Werror
-Wl,--no-undefined
-Wl,-soname,libWaterRipple.so
```

Dipendenze dinamiche:

```text
libGLESv2.so
libjnigraphics.so
liblog.so
libm.so
libdl.so
libc.so
```

Nessuna dipendenza STLport/libstdc++ legacy.

Export obbligatori:

```text
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_initWaters
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_move
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_ripple
Java_com_codex_lle_S3RippleLifecycleNative_nativeBridgeVersion
Java_com_codex_lle_S3RippleLifecycleNative_nativeInitGpu
Java_com_codex_lle_S3RippleLifecycleNative_nativeAbandonGpu
Java_com_codex_lle_S3RippleLifecycleNative_nativeDestroyGpu
Java_com_codex_lle_S3RippleLifecycleNative_nativeUploadBitmap
Java_com_codex_lle_S3RippleLifecycleNative_nativeFreeTexture
Java_com_codex_lle_S3RippleLifecycleNative_nativeRenderNormal
Java_com_codex_lle_S3RippleLifecycleNative_nativeGetLastError
```

Snapshot artefatti installati al congelamento:

```text
libWaterRipple.so
size    55.232
SHA-256 E97948C72F48849776847EF4862EC75FEA4033736FC8E9DFCF157A202C2B37C7

LLE64-debug.apk
size    14.153.828
SHA-256 966001872D9852D1C72314D4923F939E8E4300A58400EBCC824A451821D3C55C
```

L'hash APK può cambiare ricompilando/firmando; l'hash `.so` è il riferimento deterministico per il codice native congelato.

## 23. Verifiche eseguite

Device: Samsung SM-F966B/Fold7, ABI `arm64-v8a`.

### Core

```text
PASS hash=59890e7812c02590 centerVelocity=2.50805116 centerHeight=7.21869993
```

Include golden shear ai vertici `0,1,99,100,5000,9999`.

### Shader statico

```text
PASS Water Ripple GLSL constants and GL_SHORT quad
```

### EGL/GLES2 reale

```text
PASS Water Ripple GLES2 programs=3,6,9,12,15 gravityDeadWaterBrightness=-1
```

`gravityDeadWaterBrightness=-1` conferma che il driver elimina l'uniform gravity dichiarato ma inutilizzato.

### Overlay trasparente

```text
PASS overlay formula=vec4(mask*SamsungResult,mask)
defaults=0.035/0.180/1.000
flatNonzero=0 impulseNonzero=154 centerNonzero=154
borderNonzero=0 rgbOverAlpha=0 maxAlpha=255
```

### Packaging/runtime

- APK ARM64-only firmata e verificata v1/v2/v3;
- hash APK locale e `/data/app/.../base.apk` identico;
- effetto ID 10 inizializzato a 1080×2520;
- cinque transizioni Gyro↔Ripple sulla build finale;
- PID invariato `30942` durante lo stress;
- crash buffer vuoto;
- funzionamento visivo base, gyro e Ripple confermato dall'utente.

## 24. Divergenze e lavoro esplicitamente rinviato

### Necessarie oggi

1. alpha locale premoltiplicato invece di alpha fullscreen 1.0;
2. screenshot SystemUI center-crop invece del wallpaper S3 originale;
3. limite massimo di quattro catch-up step dopo jank.

### Differenze host/sicurezza note

1. tutti gli input e il solver sono serializzati sul GL thread, mentre l'originale modificava `velocity` dal thread UI in concorrenza col draw;
2. non viene replicato il fade drag SoundPool buggato/bloccante descritto nella sezione audio;
3. l'originale può rifiutare un touch arrivato quando `drawCount==0`; LLE accoda il touch appena mesh/lifecycle sono ready per non perdere il primo contatto durante il wake;
4. DVFS Samsung, SContext, hover/S Pen e hidden settings non vengono usati;
5. cleanup/context loss sono bounded e idempotenti invece di replicare delay, leak e race originali.

### Rinviate per decisione di progetto

1. compositing adattivo più vicino a Samsung senza attenuazione della delta;
2. wallpaper/source originale;
3. comportamento Fold aperto/chiuso e layout specifici;
4. ink mode;
5. gravity mode e relativi asset;
6. confronto framebuffer pixel/hash contro hardware S3 originale;
7. tuning finale degli occasionali lag/slow con hint.

Questi punti non vanno introdotti silenziosamente nel port normal: richiedono una fase e test A/B separati.

## 25. Regole anti-regressione

Prima di cambiare il Ripple:

1. non rimuovere o “correggere” la shear mesh;
2. non scambiare/normalizzare `glY,glX` o il packing trasposto;
3. non cambiare loop X-major/Y-minor;
4. non rendere il terzo pass out-of-place;
5. non abilitare `fast-math` o contraction FMA;
6. non legare `move()` al refresh fisico del pannello;
7. non inviare un MOVE sintetico prima di UP;
8. non trasformare il ritorno multi-touch in un nuovo DOWN;
9. non modificare gli shader Samsung-exact per risolvere l'alpha LLE;
10. non disegnare il background come layer opaco;
11. non eliminare generation/serial guard di affordance, trail e bitmap;
12. non fare `glDelete*` di nomi appartenenti a un context perso.

Gate minimo dopo ogni modifica:

```text
git diff --check
ripple_core_test sul device
ripple_gles_static_test sul device
ripple_gles_device_test sul device
ripple_gles_overlay_device_test sul device
build.ps1 completa
hash APK locale/device
stress Gyro↔Ripple
crash buffer vuoto
controllo manuale down/drag/long-up/audio/multi-touch
```

## 26. Confidenza finale

Il port non viene definito “100% pixel-perfect” perché background, alpha e GPU differiscono necessariamente. È però una ricostruzione ad alta confidenza della logica S3 normal:

- formule e ordine del solver conservati;
- mesh e anomalia NEON conservate;
- coordinate e basis trasposta conservate;
- clock nominale e frame order conservati;
- gesture, trail, long-up e multi-touch conservati; asset/timing audio conservati salvo il fade buggato dichiarato;
- shader/ottica normal conservati;
- differenze LLE isolate, nominate e testate.

Questa è la baseline da non alterare durante il reverse Watercolor.
