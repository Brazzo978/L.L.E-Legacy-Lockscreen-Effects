# S3 `libWaterRipple.so` render map (ARM32)

Data: 2026-07-14. Analisi **sola lettura** del programma Ghidra `libWaterRipple.so`; il database non e stato modificato/salvato. Gli indirizzi sono quelli Ghidra (ELF `st_value + 0x10000`). Il decompilato e stato verificato contro ARM assembly/P-code; questo e importante perche Ghidra elimina erroneamente vari blocchi dopo le chiamate PLT `glBindTexture`.

## Livelli di certezza

- **CONFIRMED**: visibile sia nel decompilato sia in assembly/P-code, oppure firma/caller esatto nello smali originale.
- **PROBABLE**: ricostruzione coerente da ABI/layout e loop, ma il decompilato NEON non conserva bene i nomi/dimensioni.
- **UNRESOLVED**: non determinabile senza ampliare l'analisi.

## Firme e wrapper JNI

**CONFIRMED** (smali `JniWaterRippleRender.smali`):

```text
onDraw([F[F[SIII[FIIIIFFFFFFFFFF)V
onDrawGravity([F[F[SIII[FIIIIFFFFFFFFFFIFFFFFZF)V
initWaters([F[SIIIII)V
transferWaterBitmap(Landroid/graphics/Bitmap;)V
onLoadWaterTextures()V
```

Ordine parametri Java per `onDraw` (caller `CircleUnlockRippleRenderer`):

```text
vertices, gpuHeights, indices,
vertices.length, gpuHeights.length, indices.length,
wvp,
MESH_SIZE_WIDTH / bitmapRatio (int), MESH_SIZE_HEIGHT,
NUM_DETAILS_WIDTH/2, NUM_DETAILS_HEIGHT/2,
refractiveIndex, reflectionRatio, alphaRatio1, alphaRatio2,
inkR, inkG, inkB,
mFresnelRatio, mSpecularRatio, mExponentRatio
```

`onDrawGravity` aggiunge: `0` (int), `causticsTimeRatio`, `causticsTimeRatio2`, `causticsTimeMix`, `ReferencePoint`, `TexMoveU`, `bGravityDirection`, `fWaterBrightness`.

**CONFIRMED** JNI array lifecycle (`onDraw @ 0x19fb0`, `onDrawGravity @ 0x1a6b8`): `GetFloatArrayElements` per vertices/heights/wvp (vtable `+0x2f4`), `GetShortArrayElements` per indices (`+0x2e8`), chiamata C++, quindi release con mode `0`: float `+0x314`, short `+0x308`. La firma C++ effettiva elimina i tre colori ink: sono consumati dal solo state machine JNI; il render riceve i sette float `refractive, reflection, alpha1, alpha2, fresnel, specular, exponent` e `bWithInk`.

## `Fluid::Ripple_Render @ 0x136c4`

### Ordine GLES esatto

**CONFIRMED**:

1. `glViewport(0,0,this->width@+0x98,this->height@+0x9c)`; `glUseProgram(this->program@+0x70)`.
2. `glUniform1f`: `uMESH_SIZE_WIDTH`, `uMESH_SIZE_HEIGHT`, `uNUM_DETAILS_WIDTH`, `uNUM_DETAILS_HEIGHT`, `uRefractiveIndex`.
3. Cerca attributi `aPosition`, `aHeights` e uniform `uMVPMatrix`; carica la matrice con `glUniformMatrix4fv(...,1,GL_FALSE,mvp)`.
4. Aggiorna ogni frame tre buffer con `GL_DYNAMIC_DRAW (0x88e4)`:
   - `+0x17c`, `GL_ARRAY_BUFFER`, `vertexCount*4`, `vertices`;
   - `+0x180`, `GL_ARRAY_BUFFER`, `heightCount*4`, `heights`;
   - `+0x184`, `GL_ELEMENT_ARRAY_BUFFER`, `indexCount*2`, `indices`.
5. Attributi: `aHeights <- +0x180`, `aPosition <- +0x17c`, entrambi `size=3`, `GL_FLOAT`, normalized false, stride/pointer 0; abilita entrambi.
6. Se `bWithInk=true`: uniform `Scale=(1/width,1/height)`; `ink_color=((1.5-clearInk@+0xe8)/rgb@(+0x170,+0x174,+0x178))-1`; `intensity=(+0xf4)*(+0xf0)`; unit 2 (`GL_TEXTURE2`) texture `+0x24`, sampler `Density=2`.
7. Uniform comuni: `alphaRatio1=inputAlpha1*reflectionRatio`; `alphaRatio2=inputAlpha2*(1-reflectionRatio)`; poi `fresnelRatio`, `specularRatio`, `exponent`, `viewportHeight=float(+0x9c)`.
8. Unit 0: texture `+0x104`, `sBGTexture=0`; unit 1: texture `+0x108`, `sWaterTexture=1`.
9. `glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,+0x184)`; `glDrawElements(GL_TRIANGLES,indexCount,GL_UNSIGNED_SHORT,0)`.
10. Disabilita i due attributi; unbind texture units nell'ordine 3,2,1,0 (lascia attiva unit 0).

Nota: il decompilato tronca falsamente i passi 8-10 per i warning “Possible PIC construction”. L'assembly ARM `0x13ad4..0x13bb4` li conferma.

### Nomi shader esatti

**CONFIRMED**, stringhe `0x1e24c..0x1e35c`:

```text
uMESH_SIZE_WIDTH, uMESH_SIZE_HEIGHT,
uNUM_DETAILS_WIDTH, uNUM_DETAILS_HEIGHT,
uRefractiveIndex, aPosition, aHeights, uMVPMatrix,
Scale, ink_color, intensity, Density,
alphaRatio1, alphaRatio2, fresnelRatio, specularRatio, exponent,
viewportHeight, sBGTexture, sWaterTexture
```

## `Fluid::Ripple_Gravity_Render @ 0x13c08`

**CONFIRMED**: passi viewport/program, uniform base, upload VBO/IBO, attributi, uniform ottiche e texture BG/water sono gli stessi. Non esegue il ramo `Scale/ink_color/intensity/Density` del renderer normale.

Dopo BG unit 0 e water unit 1 imposta location gia memorizzate nella struct:

| Offset | Nome | Valore render |
|---:|---|---|
| `+0x13c` | `uCausticTimeRatio` | arg float 1 |
| `+0x140` | `uCausticTimeRatio2` | arg float 2 |
| `+0x144` | `uCausticTimeMix` | arg float 3 |
| `+0x148` | `uReferencePoint` | arg float 4 |
| `+0x14c` | `uTexMove` | arg float 5 |
| `+0x150` | `uGravityDirection` | arg bool via `glUniform1i` |
| `+0x154` | `uWaterbrightness` | ultimo float |

Le location vengono inizializzate da `InitializeGPUGravity @ 0x13208`; sampler: `gravityTexture @ +0x12c`, `causticTexture @ +0x130`, `causticTexture2 @ +0x134`.

Texture aggiuntive **CONFIRMED**: unit 2 `texture +0x120 / gravityTexture=2`; unit 3 `+0x124 / causticTexture=3`; unit 4 `+0x128 / causticTexture2=4`. Poi draw identico e unbind units 4,3,2,1,0.

## Layout `Fluid` osservato

**CONFIRMED**:

| Offset | Ruolo |
|---:|---|
| `+0x24` | density/ink texture (normale, unit 2) |
| `+0x70` | programma GLSL corrente |
| `+0x98/+0x9c` | viewport width/height |
| `+0x104/+0x108` | BG/water textures |
| `+0x120/+0x124/+0x128` | gravity/caustic1/caustic2 textures |
| `+0x12c..+0x154` | sampler/uniform locations gravity (vedi sopra) |
| `+0x170/+0x174/+0x178` | ink RGB divisors |
| `+0x17c/+0x180/+0x184` | position VBO / height VBO / index IBO |

## Costanti esatte dello state machine `onDraw`

**CONFIRMED** (bit-pattern ARM): ogni frame ink imposta `+0xe8=0.7f`, `+0xf4=0.02f`, `+0xe4=1.0f`, `+0xc0=0.25f`, e usa `inkRGB = JavaRGB + 0.05f`.

- State 1: `+0xf8=-1`, `+0xc8=0.94`, `+0xcc=0.92`, `+0xb4=200`, `+0xe0=40`, `+0xb0=step*8`, `+0xdc=(step<acc_step?step*12:0)`, `+0xe4=step*0.1` durante inject.
- Moving 0: `+0xf8=0`, `+0xb4=100`, `+0xc8=0.8`, `+0xcc=0.94`, `+0xe0=20`, `+0xb0=pressure*40`, `+0xdc=pressure*45`.
- Moving 1: `+0xf8=1`, `+0xb4=150`, `+0xc8=0.94`, `+0xcc=0.92`, `+0xe0=20`, `+0xdc=10`, `+0xb0=25`.
- Moving 2: `+0xf8=2`, `+0xb4=40`, `+0xc8=0.96`, `+0xcc=0.92`, `+0xe0=4`, `+0xdc=20`, `+0xb0=30`.

## `initWaters @ 0x1b778`

**CONFIRMED**: firma `[F[SIIIII)V`; blocca `float[] vertices` e `short[] indices`, scrive triplette XYZ e 6 indici per quad, quindi rilascia entrambi con mode 0. Step: `dx = MESH_SIZE_WIDTH/(SURFACE_DETAILS_WIDTH-1)`, `dy = MESH_SIZE_HEIGHT/(SURFACE_DETAILS_HEIGHT-1)`; coordinate centrate, Z `0.0f`. Indici per quad in due triangoli: `topLeft, topRight, bottomRight, topLeft, bottomRight, bottomLeft` (ordine osservato nel loop scalar/NEON).

**PROBABLE**: perche i profili noti usano superfici quadrate, il codice usa `SURFACE_DETAILS_HEIGHT` anche come stride degli indici; va corretto/validato per griglie non quadrate in un port generico.

## Water bitmap / texture

`transferWaterBitmap @ 0x1aef4` **CONFIRMED**:

1. `AndroidBitmap_getInfo(env, bitmap, &bmp_water_Info@0x220b4)`;
2. `AndroidBitmap_lockPixels(env, bitmap, &bmp_water_Pixels@0x220d8)`;
3. su successo chiama subito `AndroidBitmap_unlockPixels`; errori log priority 6.

`onLoadWaterTextures @ 0x19e84` **CONFIRMED** passa a `Fluid::LoadWaterTextures @ 0x133e4` l'`AndroidBitmapInfo` globale per valore e il puntatore globale. `LoadWaterTextures`:

```text
glGenTextures(1, &this->waterTexture@+0x108)
glBindTexture(GL_TEXTURE_2D, +0x108)
glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels)
glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE) // 33071.0f
glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)   // 9729.0f
glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
```

**UNRESOLVED / rischio di porting**: il puntatore viene conservato e usato dopo `unlockPixels`; il contratto Android non garantisce che resti valido. In un port 64-bit copiare i pixel prima dell'unlock o caricare la texture mentre sono locked. Inoltre il native ignora `stride` e `format` e forza RGBA8: il chiamante deve fornire bitmap compatibile/tightly packed.

## Riepilogo implementativo

- **CONFIRMED**: il renderer e GLES 2.x, senza dipendenza ABI intrinseca da puntatori a 32 bit nella matematica/shader; la barriera al port e la riscrittura JNI/GL e il layout dati, non le formule.
- **CONFIRMED**: indice draw `GL_UNSIGNED_SHORT`; VBO dati `float`, quindi le dimensioni upload sono `count*4`, `count*4`, `count*2` indipendenti da arm32/arm64.
- **UNRESOLVED**: questo pass non ha validato l'intero sorgente GLSL gravity ne tutti i produttori dei campi `Fluid`; non inferire altri offset oltre a quelli elencati.
