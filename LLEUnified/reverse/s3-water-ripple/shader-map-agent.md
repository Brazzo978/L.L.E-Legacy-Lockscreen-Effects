# S3 `libWaterRipple.so` — shader e compositing esatti

Data: 2026-07-14. Analisi **sola lettura** del programma Ghidra `libWaterRipple.so`; nessuna modifica, rinomina o salvataggio del database. ELF analizzato: SHA-256 `96088C44C40C0E1DF52D32B9D9B47506B6B2A1B9E8C2D9BE55FCEA5366CF48A3`.

Gli indirizzi Ghidra hanno image base `0x10000`; quindi, per questo ELF, `Ghidra = ELF st_value + 0x10000`. Le stringhe shader sono in `.rodata` e l'indirizzo ELF coincide con il file offset.

## Livelli di certezza

- **CONFIRMED**: testo presente letteralmente nell'ELF e/o flusso verificato contro assembly ARM32.
- **PROBABLE**: semantica coerente ma non completamente osservabile (per esempio stato GLES ereditato dall'host).
- **UNRESOLVED**: non ricostruibile senza una cattura runtime o un secondo binario.

## Mappa completa dei programmi

**CONFIRMED** — `Fluid::InitializeGPU(bool)` a Ghidra `0x159c8`, ELF `0x59c8`:

| Modalità | Vertex shader | Fragment shader | Programma in `Fluid` |
|---|---:|---:|---:|
| Ripple normale, `bWithInk=false` | G `0x1eae8` / ELF `0xeae8` | G `0x1d250` / ELF `0xd250` | `+0x70` |
| Ripple con ink, `bWithInk=true` | G `0x1e4f8` / ELF `0xe4f8` | G `0x1c600` / ELF `0xc600` | `+0x70` |
| Advect density | G `0x1c570` / ELF `0xc570` | G `0x1ce90` / ELF `0xce90` | `+0x74` |
| Add ink | G `0x1c570` / ELF `0xc570` | G `0x1ca90` / ELF `0xca90` | `+0x88` |

**CONFIRMED** — i due vertex shader normali a ELF `0xe4f8` e `0xeae8` sono duplicati byte-per-byte (1518 byte); la duplicazione serve solo a costruire due programmi con fragment diversi.

**CONFIRMED** — `Fluid::InitializeGPUGravity()` a Ghidra `0x13208`, ELF `0x3208`:

| Modalità | Vertex shader | Fragment shader | Programma in `Fluid` |
|---|---:|---:|---:|
| Gravity/caustics | G `0x1d618` / ELF `0xd618` | G `0x1f0d8` / ELF `0xf0d8` | `+0x70` |

Non esistono shader separati per `AdvectVelocity`, `Jacobi`, `ComputeDivergence` o `SubtractGradient`: nel binario osservato questi pass sono CPU. Il solo pass di advezione GLES è `AdvectDensity`.

## Sorgenti GLSL estratti

I blocchi seguenti sono trascrizioni token-per-token delle stringhe NUL-terminate nell'ELF. Sono stati normalizzati soltanto gli spazi finali delle righe nel Markdown; formule, identificatori, costanti e refusi originali sono conservati.

### Quad vertex — ELF `0xc570`, Ghidra `0x1c570`

```glsl
attribute vec4 vertex;
attribute vec2 texCoord;
varying vec2 v_texCoord;
void main()
{
    gl_Position = vertex;
    v_texCoord = texCoord;
}
```

### AdvectDensity fragment — ELF `0xce90`, Ghidra `0x1ce90`

```glsl
precision mediump float;
uniform sampler2D VelocityTexture;
uniform sampler2D SourceTexture;
uniform vec2 TimeStep;
uniform float BackwardStepSize;
uniform float Dissipation;
uniform vec2  Scale;
uniform vec2  center;
uniform int   drag;
varying vec2 v_texCoord;
vec2 encode(float v)
{
	const vec2 mask = vec2(0.00392157, 1.0);
	v = clamp(v, -127.0, 127.0);
	vec2 p = fract(v*mask);
	p.x -= p.y/255.0;
	return p;
}
void main()
{
    vec2 fragCoord = gl_FragCoord.xy;
    vec4 buf = texture2D(VelocityTexture, v_texCoord);
    vec2 u;
    u.x = 255.0*buf.x + buf.y - 127.0;
    u.y = 255.0*buf.z + buf.w - 127.0;
    float back_step = BackwardStepSize;
	 if(drag<2){
        float d = distance(center, Scale*v_texCoord);
       if( d < 80.0 ) back_step = 0.0075*d;
	 }
    vec2 coord = v_texCoord - back_step * TimeStep * u;
    buf = texture2D(SourceTexture, coord);
    float value = 255.0*buf.x + buf.y;
    gl_FragColor.xy = encode(Dissipation*value);
}
```

`fragCoord` è intenzionalmente inutilizzato. `VelocityTexture` non viene cercato con `glGetUniformLocation`: conserva il valore sampler predefinito `0`; `SourceTexture` viene impostato a `1`.

### AddInk fragment — ELF `0xca90`, Ghidra `0x1ca90`

```glsl
precision mediump float;
uniform sampler2D Source;
uniform vec2 Scale;
uniform vec2  current;
uniform vec2  previous;
uniform vec2  normal;
uniform int   mode;
uniform float len;
uniform float ImpulseDensity;
uniform float Radius;
varying vec2 v_texCoord;
vec2 encode(float v)
{
    v = clamp(v, -127.0, 127.0);
    vec2 p;
    p.x = fract(v/255.0);
    p.y = fract(v);
    p.x -= p.y/255.0;
	 return p;
}
void main()
{
    vec4 buf = texture2D(Source, v_texCoord);
    float x = 255.0*buf.x + buf.y;
    vec2  p = Scale*v_texCoord;
    if(mode==2)
    {
       vec2 vector = p - previous;
       float dot_val = dot( normal, vector );
       if( dot_val > 0.0 && dot_val < len )
       {
         vec2 projected = previous + dot_val*normal;
					float d = distance(projected, p);
        if( d < Radius ) x += ImpulseDensity*exp(-d*d/(0.8*Radius*Radius));
       }
    }
    else
    {
        float d = distance(current, p);
        if( d < Radius ) x += ImpulseDensity/(1.0+d);
    }
    gl_FragColor.xy = encode(x);
}
```

### Normal/ink vertex — ELF `0xe4f8` e `0xeae8`, Ghidra `0x1e4f8` e `0x1eae8`

```glsl
uniform mat4 uMVPMatrix;
attribute vec4 aPosition;
attribute vec4 aHeights;
varying vec2 vWaterTextureCoord;
varying vec2 vBGTexture1Coord;
varying vec3 vNormal;
varying vec3 vHalfVec;
varying float vHeights;
uniform float uMESH_SIZE_WIDTH;
 uniform float uMESH_SIZE_HEIGHT;
 uniform float uNUM_DETAILS_WIDTH;
 uniform float uNUM_DETAILS_HEIGHT;
 uniform float uRefractiveIndex;
 void main() {
  float maxX = uMESH_SIZE_WIDTH / 2.0;
  float maxY = uMESH_SIZE_HEIGHT / 2.0;
  float rimo = uRefractiveIndex - 1.0;
   vec4 pos = aPosition;
  vec4 heights = aHeights;
  float len = heights.x;
  vec3 v = vec3( pos.x, pos.y, len * 0.25 );
  vec2 n = ( vec2( len ) - heights.yz ) * 0.25;
  float nz = sqrt(dot( n, n ) + 1.0 );
  n = n / nz;
  vec3 d = vec3( v.x, v.y, v.z + 30.0 );
   len = sqrt( dot( d, d ) );
  d = d / len;
  float t = dot( d, vec3( n.x, n.y, 1.0 ) ) * rimo;
  d.x += n.x * t;
  d.y += n.y * t;
  float r0, u0, v0;
  r0 = ( 30.9 - v.z ) / d.z;
  u0 = ( d.x * r0 + v.x ) / maxX * 0.25 + 0.5;
  v0 = ( d.y * r0 + v.y ) / maxY * -0.25 + 0.5;
  float uxx = n.x * 0.5 + 0.5 + pos.y / uMESH_SIZE_WIDTH * 0.25;
  float vxx = n.y * 0.5 + 0.5 + pos.x / uMESH_SIZE_HEIGHT * 0.25;
  vWaterTextureCoord = vec2( uxx, vxx );
  vBGTexture1Coord = vec2( u0, v0 );
  vNormal = normalize(vec3(n.x, n.y, 0.6));
  vHalfVec = normalize(normalize(vec3(0.0, 0.0, 1.0) - (uMVPMatrix * pos).xyz) + (uMVPMatrix * vec4(5.0, -5.0, 1.0, 1.0)).xyz) ;
  vHeights = aHeights.x;
  gl_Position = uMVPMatrix*pos;
}
```

`uNUM_DETAILS_WIDTH` e `uNUM_DETAILS_HEIGHT` sono dichiarate e impostate dal C++, ma non usate nel vertex shader.

### Normal fragment — ELF `0xd250`, Ghidra `0x1d250`

```glsl
precision mediump float;
varying vec2 vWaterTextureCoord;
varying vec2 vBGTexture1Coord;
varying vec3 vNormal;
varying vec3 vHalfVec;
varying float vHeights;
uniform sampler2D sWaterTexture;
uniform sampler2D sBGTexture;
uniform float alphaRatio1;
uniform float fresnelRatio;
uniform float specularRatio;
uniform float exponent;
uniform float viewportHeight;
void main() {
  vec4  waterColor = texture2D(sWaterTexture, vec2(vWaterTextureCoord.s, vWaterTextureCoord.t));
  vec4  bgColor = texture2D(sBGTexture, vec2(vBGTexture1Coord.s, vBGTexture1Coord.t));
  float NdotHV = max(dot(vNormal, vHalfVec),0.0);
  float t = clamp(abs(vHeights), 0.0, 1.13);
  float specular =  clamp(specularRatio * pow(NdotHV, exponent), 1.0, 5.5) ;
  float NdotL = max(dot(vNormal, vec3(5.0,-5.0, 1.0)),0.0);
  vec3  rippleRGB = t * specular* waterColor.rgb * ( alphaRatio1 +  fresnelRatio * clamp((NdotL - 0.99),0.0, 0.3)) + bgColor.rgb ;
  gl_FragColor = vec4(rippleRGB , 1.0);
}
```

`viewportHeight` è dichiarata/impostata ma inutilizzata. `alphaRatio2` viene cercata dal renderer, ma non è dichiarata in questo shader: la location è `-1` e il relativo `glUniform1f` non produce effetto.

### Ink-composited fragment — ELF `0xc600`, Ghidra `0x1c600`

```glsl
precision mediump float;
uniform sampler2D Density;
varying vec2 vWaterTextureCoord;
varying vec2 vBGTexture1Coord;
varying vec3 vNormal;
varying vec3 vHalfVec;
varying float vHeights;
uniform sampler2D sWaterTexture;
uniform sampler2D sBGTexture;
uniform float alphaRatio1;
uniform vec2 Scale;
uniform float intensity;
uniform vec3 ink_color;
uniform float fresnelRatio;
uniform float specularRatio;
uniform float exponent;
void main() {
  vec4  waterColor = texture2D(sWaterTexture, vec2(vWaterTextureCoord.s, vWaterTextureCoord.t));
  vec4  bgColor = texture2D(sBGTexture, vec2(vBGTexture1Coord.s, vBGTexture1Coord.t));
  float NdotHV = max(dot(vNormal, vHalfVec),0.0);
  float specular = clamp(specularRatio * pow(NdotHV, exponent), 1.0, 4.5) ;
  float NdotL = max(dot(vNormal, vec3(5.0,-5.0, 1.0)),0.0);
  float t = clamp(abs(vHeights), 0.0, 1.13);
  vec3  rippleRGB = t * specular* waterColor.rgb * ( alphaRatio1 +  fresnelRatio * clamp((NdotL - 0.99),0.0, 0.3)) + bgColor.rgb ;
  vec4  buf = texture2D(Density, gl_FragCoord.xy * Scale );
  float d = 255.0*buf.x + buf.y;
  float w = intensity*d;
  gl_FragColor = vec4(rippleRGB / (1.0+w*ink_color), 1.0);
}
```

La differenza ottica rispetto al fragment normale è reale: limite speculare massimo `4.5` anziché `5.5`, più la divisione RGB per `(1 + w*ink_color)`.

### Gravity vertex — ELF `0xd618`, Ghidra `0x1d618`

```glsl
attribute vec4 aPosition;
attribute vec4 aHeights;
attribute vec2 aTextureU;
varying vec2  vTexture0Coord;
varying vec2  vTexture1Coord;
varying vec2  vTextureBackCoord;
varying vec2  vTextureCausticsCoord;
varying float vHeights;
varying vec3  vLightDir;
varying vec3  vHoverI;
varying vec3  vNormalI;
varying vec3  vNormal;
varying vec2  vTextureU;
uniform mat4 uMVPMatrix;
uniform mat4 uMVMatrix;
uniform float uMESH_SIZE_WIDTH;
 uniform float uMESH_SIZE_HEIGHT;
 uniform float uNUM_DETAILS_WIDTH;
 uniform float uNUM_DETAILS_HEIGHT;
 uniform float uRefractiveIndex;
 uniform float uHoverX;
 uniform float uHoverY;
 uniform float uTexMove;
uniform int uGravityDirection;
varying vec3 vNormalL;
varying vec3 vNormalHV;
varying float vTexCoordU;
void main() {
	float maxX = uMESH_SIZE_WIDTH / 2.0;
	float maxY = uMESH_SIZE_HEIGHT / 2.0;
	float rimo = uRefractiveIndex - 1.1;
    vec4 pos = aPosition;
	vec4 heights = aHeights;
	float len = heights.x;
	vec3 v = vec3( pos.x, pos.y, len * 0.25 );
	vec2 n = ( vec2( len ) - heights.yz ) * 0.25;
	float nz = sqrt(dot( n, n ) + 1.0 );
	n = n / nz;
	vec3 d = vec3( v.x, v.y, v.z + 30.0 );
    len = sqrt( dot( d, d ) );
	d = d / len;
   vec3 d2  = d;	float t = dot( d, vec3( n.x, n.y, 1.0 ) ) * rimo;
	d.x += n.x * t;
	d.y += n.y * t;
	float r0, u0, v0;
	r0 = ( 30.9 - v.z ) / d.z;
	u0 = ( d.x * r0 + v.x ) / maxX * 0.25 + 0.5;
	v0 = ( d.y * r0 + v.y ) / maxY * -0.25 + 0.5;
	float uxx = n.x * 0.5 + 0.5 + pos.y / uMESH_SIZE_WIDTH * 0.25;
	float vxx = n.y * 0.5 + 0.5 + pos.x / uMESH_SIZE_HEIGHT * 0.25;
	vTexture0Coord = vec2( uxx, vxx );
	vTexture1Coord = vec2( u0, v0 );
	d2.x += n.x * (dot( d2, vec3( n.x, n.y, 1.0 ) ) * (uRefractiveIndex - 0.3));
	d2.y += n.y * (dot( d2, vec3( n.x, n.y, 1.0 ) ) * (uRefractiveIndex - 0.3));
   float causticsRefractx =  ( d2.x * r0 + v.x ) / maxX * 0.25 + 0.5;
   float causticsRefracty =  ( d2.y * r0 + v.y ) / maxY * 0.25 + 0.5;
   vTextureCausticsCoord = vec2(causticsRefractx, causticsRefracty);	vec3 v3 = vec3( pos.x, pos.y, heights.x * 0.001 );   vec3 d3  = vec3( v3.x, v3.y,  v3.z + 30.0 );	float r3 = ( 30.9 - v3.z ) / d3.z;
   float Backx =  (( d3.x * r3 + v3.x ) / maxX * 0.25) + 0.5;
   float Backy =  (( d3.y * r3 + v3.y ) / maxY * 0.25) + 0.5;
	vTextureBackCoord = vec2(Backx, Backy);	vHoverI = normalize((uMVMatrix * (vec4(uHoverX, uHoverY, 12.0,1.0) - aPosition)).xyz);	vLightDir = normalize(vec3(70.0, 120.0, 1.0) - (uMVMatrix * aPosition).xyz);
	vNormalI = normalize(vec3(0.0, 0.0, 1.0) - (uMVMatrix * aPosition).xyz);	vNormal = cross(vec3(1.0, 0.0, aHeights.x - aHeights.z), vec3(0.0, 1.0, aHeights.y - aHeights.x));	vHeights = aHeights.x;
	vTextureU = aTextureU;
	vNormalL = vLightDir;
	vNormalHV = normalize(vNormalL + vNormalI);
	if(uGravityDirection == 0){   vTexCoordU = (1.0 -  vTextureBackCoord.x ) + uTexMove  ;
	}
    else{	vTexCoordU = vTextureBackCoord.x  + uTexMove  ;
	}
	gl_Position = uMVPMatrix * pos;
}
```

### Gravity fragment — ELF `0xf0d8`, Ghidra `0x1f0d8`

```glsl
precision mediump float;
varying float vHeights;
varying vec2  vTexture0Coord;
varying vec2  vTexture1Coord;
varying vec2  vTextureBackCoord;
varying vec2  vTextureCausticsCoord;
varying vec3  vLightDir;
varying vec3  vHoverI;
varying vec3  vNormalI;
varying vec3  vNormal;
varying vec2  vTextureU;
uniform sampler2D sWaterTexture;
uniform sampler2D sBGTexture;
uniform sampler2D gravityTexture;
uniform sampler2D causticTexture;
uniform sampler2D causticTexture2;
uniform float alphaRatio1;
uniform float alphaRatio2;
uniform float fresnelRatio;
uniform float specularRatio;
uniform float exponent;
uniform float uPercent;
uniform float uCausticTimeMix;
uniform float uCausticTimeRatio;
uniform float uCausticTimeRatio2;
uniform float uReferencePoint;
uniform float uCausitcAni;
uniform float uWaterbrightness;
varying vec3 vNormalL;
varying vec3 vNormalHV;
varying float vTexCoordU;
void main() {
float HeightAlphaMap = 0.0;
vec2 texHAMMap = vec2(vTexCoordU,  vTextureBackCoord.t );
	HeightAlphaMap += texture2D(gravityTexture, texHAMMap + vec2(0.0, 0.00208333333333333333333333333333*2.0 )).r;
	HeightAlphaMap += texture2D(gravityTexture, texHAMMap - vec2(0.0, 0.00208333333333333333333333333333*2.0 )).r;
	HeightAlphaMap += texture2D(gravityTexture, texHAMMap + vec2(0.0037037037037037037037037037037*2.0 ,0.0) ).r;
	HeightAlphaMap += texture2D(gravityTexture, texHAMMap - vec2(0.0037037037037037037037037037037*2.0 ,0.0) ).r;
HeightAlphaMap *= 0.25 ;
vec3  rippleRGB;
 vec4  texBackground = texture2D(sBGTexture, vec2(vTextureBackCoord.s, 1.0 - vTextureBackCoord.t));
float tex2Point = HeightAlphaMap * uReferencePoint  + 20.0;
float HeightReferencePoint = vHeights + ((uReferencePoint - 40.0 ) * 0.2 ) + 46.0 ;if( tex2Point <= HeightReferencePoint){
	vec3 N = normalize(vNormal);
	vec4  tex0 = texture2D(sWaterTexture, vec2(vTexture0Coord.s, vTexture0Coord.t));
	vec4  tex1 = texture2D(sBGTexture, vec2(vTexture1Coord.s, vTexture1Coord.t));
	float hoverSpecular = 0.0;
	vec4  texCaustic = texture2D(causticTexture, vec2(vTextureCausticsCoord.s, vTextureCausticsCoord.t));
	vec4  texCaustic2 = texture2D(causticTexture2, vec2(vTextureCausticsCoord.s, vTextureCausticsCoord.t));
	float NdotHV = max(dot(N, vNormalHV),0.0);
	float specular = specularRatio * pow(NdotHV, exponent);
	if(uPercent > 0.0) {
        NdotHV = max(dot(N, vHoverI), 0.0);
		hoverSpecular = pow(NdotHV, max((uPercent), 60.0));
	}
float NdotL;
if(uPercent > 0.1) {
  NdotL = max(dot(N, vec3(-5.0, -5.0, 1.0)) + dot(N, vec3(5.0, 5.0, 1.0)) , 0.0);
} else {
  NdotL = max(dot(N, vec3(5.0, 5.0, 1.0)), 0.0);
}
  float dirtyAlpha = clamp(abs(vHeights), 0.0, 1.13);
    float causticRatio =  min(abs(vHeights), 0.5) * 2.0;
    vec3 causticMix = mix( texCaustic.rgb , texCaustic2.rgb, uCausticTimeMix);
	float CausticsColorSum = 1.4 - (  clamp(abs(vHeights * 1.5) , 0.2 ,  1.2 ) )  ;
	float CausticsResult = (pow((CausticsColorSum - 0.2) ,3.0) * 0.2 ) + 0.9;
	vec3 baseRippleRGB = vec3(specular) + vec3(max(hoverSpecular * uPercent * 0.9, 0.0))  + dirtyAlpha * tex0.rgb * (alphaRatio1  +  (fresnelRatio + uPercent ) * clamp((NdotL - 0.99),0.0, 0.3)) + tex1.rgb * alphaRatio2;
   rippleRGB = baseRippleRGB + ( causticMix * uCausticTimeRatio * causticRatio ) + ( pow(CausticsResult, 5.0) * tex1.rgb * abs(uCausticTimeRatio2) * 0.7 );
	if(tex2Point <= HeightReferencePoint && tex2Point >= HeightReferencePoint - 1.0 ){
		float ratioMix =  tex2Point - (HeightReferencePoint - 1.0);
        rippleRGB = mix(rippleRGB * (0.6 + (0.4 * ( 1.0 - ratioMix))), texBackground.rgb* (0.6 + (0.4 * ( ratioMix))), ratioMix );
	}
 }
 else{
    rippleRGB = texBackground.rgb;
 }
  gl_FragColor = vec4(rippleRGB,1.0);
}
```

Refusi originali conservati: `uCausitcAni` e `uWaterbrightness`. Entrambi sono inutilizzati nel testo del fragment; `uWaterbrightness` viene comunque cercato/impostato, quindi la location attesa è `-1`. `uPercent` non ha una stringa separata usata da `glGetUniformLocation` nel binario e rimane al default GLES `0.0`; di conseguenza il ramo hover `uPercent > 0` non si attiva nel percorso osservato. `uMVMatrix`, `uHoverX`, `uHoverY` e `aTextureU` non sono cercati/forniti dal renderer; i relativi risultati non contribuiscono al percorso `uPercent == 0`, salvo varying inutilizzati.

## Rendering normale esatto

**CONFIRMED** — `Fluid::Ripple_Render` Ghidra `0x136c4`, ELF `0x36c4`; la parte dopo le chiamate `glBindTexture` è stata verificata in assembly perché il decompilatore la marca erroneamente unreachable.

1. `glViewport(0,0,width@+0x98,height@+0x9c)`; `glUseProgram(program@+0x70)`.
2. `glUniform1f` per `uMESH_SIZE_WIDTH`, `uMESH_SIZE_HEIGHT`, `uNUM_DETAILS_WIDTH`, `uNUM_DETAILS_HEIGHT`, `uRefractiveIndex`.
3. Cerca `aPosition`, `aHeights`, `uMVPMatrix`; matrice con `glUniformMatrix4fv(...,1,GL_FALSE,mvp)`.
4. Ogni frame aggiorna VBO `+0x17c` (`vertexCount*4`), VBO `+0x180` (`heightCount*4`) e IBO `+0x184` (`indexCount*2`) con `GL_DYNAMIC_DRAW`.
5. `aHeights` e `aPosition`: `size=3`, `GL_FLOAT`, normalized false, stride/pointer 0; entrambi abilitati.
6. Se `bWithInk`: `Scale=(1/width,1/height)`, `ink_color=((1.5-clearInk@+0xe8)/rgb@(+0x170,+0x174,+0x178))-1`, `intensity=(+0xf4)*(+0xf0)`; unità 2, texture density `+0x24`, `Density=2`.
7. `alphaRatio1=inputAlpha1*reflectionRatio`; `alphaRatio2=inputAlpha2*(1-reflectionRatio)`; poi `fresnelRatio`, `specularRatio`, `exponent`, `viewportHeight=float(height)`.
8. Unità 0: BG `+0x104`, `sBGTexture=0`; unità 1: water `+0x108`, `sWaterTexture=1`.
9. Binda IBO e `glDrawElements(GL_TRIANGLES,indexCount,GL_UNSIGNED_SHORT,0)`.
10. Disabilita `aPosition`/`aHeights`; unbind texture nelle unità 3,2,1,0 e lascia attiva unità 0.

**CONFIRMED** — non vi è alcun `glClear` in `Ripple_Render`. Il framebuffer conserva quindi ciò che l'host/default surface non pulisce prima del draw.

## Rendering gravity esatto

**CONFIRMED** — `Fluid::Ripple_Gravity_Render` Ghidra `0x13c08`, ELF `0x3c08`.

- Setup viewport, programma, uniform base, VBO/IBO, attributi, ottica, BG unità 0 e water unità 1 sono uguali al renderer normale.
- Non esiste il ramo density/ink.
- Uniform location pre-cache da `InitializeGPUGravity`: `gravityTexture@+0x12c`, `causticTexture@+0x130`, `causticTexture2@+0x134`, `uCausticTimeRatio@+0x13c`, `uCausticTimeRatio2@+0x140`, `uCausticTimeMix@+0x144`, `uReferencePoint@+0x148`, `uTexMove@+0x14c`, `uGravityDirection@+0x150`, `uWaterbrightness@+0x154`.
- Unità 2: gravity texture `+0x120`; unità 3: caustic1 `+0x124`; unità 4: caustic2 `+0x128`.
- Draw identico con `GL_TRIANGLES`/`GL_UNSIGNED_SHORT`; cleanup unità 4,3,2,1,0.

## AdvectDensity e AddInk: GL e quad

**CONFIRMED / correzione a `fluid-map-agent.md`** — `AdvectDensity` è Ghidra `0x14118` / ELF `0x4118`, `AddInk` è Ghidra `0x14f60` / ELF `0x4f60`. Il quad offscreen **non** usa `GL_FLOAT`: assembly `AdvectDensity` Ghidra `0x14304..0x14338` / ELF `0x4304..0x4338` passa `0x1402 = GL_SHORT` a entrambi i `glVertexAttribPointer`.

I 32 byte a Ghidra `0x1c548` / ELF `0xc548` rappresentano quattro record interleaved, ognuno `{short posX,posY, short texU,texV}`:

```text
(-1,-1, 0,0)
( 1,-1, 1,0)
(-1, 1, 0,1)
( 1, 1, 1,1)
```

- Attributi fissati in link: location `0 = vertex`, `1 = texCoord` (`secCreateShaderProgram`, ELF `0x80c4..0x80ec`).
- VBO `+0x90`, 32 byte, `GL_DYNAMIC_DRAW`; location 0 e 1 sono `size=2`, `GL_SHORT`, normalized false, stride `8`, offset `0` e `4`.
- Draw `glDrawArrays(GL_TRIANGLE_STRIP,0,4)`.
- `AdvectDensity` binda output FBO, unità 0 velocity, unità 1 source density; `VelocityTexture` resta sampler 0 e `SourceTexture=1`.
- `AddInk` binda output FBO, unità 0 source; `Source=0`.
- Entrambi disabilitano l'attributo 0 e unbindano le unità 3,2,1,0, poi FBO 0 e `glDisable(GL_BLEND)`.

## Alpha, blend e compositing

**CONFIRMED**:

- Tutti e tre i fragment finali (`normal`, `ink`, `gravity`) scrivono alpha letterale `1.0`.
- Il fragment normal/ink ricostruisce un colore fullscreen: `BG rifratto + contributo acqua/speculare`; non produce un delta e non produce trasparenza locale.
- Gravity usa o `texBackground.rgb` oppure la composizione water/refraction/caustics; anch'esso è fullscreen opaco.
- Il solo ramo `bWithInk=true` di `InitializeGPU` chiama `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` a ELF `0x60c0..0x60c8` / Ghidra `0x160c0..0x160c8` (argomenti `0x302,0x303`); il ramo normale ritorna prima. Il binario non importa né chiama `glEnable(GL_BLEND)`. `AdvectDensity` e `AddInk` chiamano esplicitamente `glDisable(GL_BLEND)` a fine pass.

**PROBABLE**: sul renderer Samsung originale il draw finale è effettivamente non blended/opaco, coerente con alpha `1.0` e con l'assenza di `glEnable(GL_BLEND)`. Lo stato potrebbe in teoria essere abilitato dall'host EGL/Java, ma non esiste alcuna chiamata `glEnable` nello smali `CircleUnlockRippleRenderer`; anche in quel caso alpha `1.0` rende identico il colore finale.

Conseguenza per LLE64: una traduzione a overlay trasparente richiede necessariamente una modifica intenzionale del compositing (alpha locale/premoltiplicato). Non può essere chiamata output Samsung byte-identico; la matematica RGB sopra, invece, può e deve restare esatta.

## Upload texture

**CONFIRMED** — `LoadBGTextures` G `0x13320`/ELF `0x3320`, `LoadWaterTextures` G `0x133e4`/ELF `0x33e4`, `LoadGravityTextures` G `0x134a8`/ELF `0x34a8`:

- `glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels)`.
- `WRAP_S/T = GL_CLAMP_TO_EDGE`; `MAG/MIN_FILTER = GL_LINEAR` tramite `glTexParameterf`.
- Gravity carica tre texture identiche per formato/settings agli offset `+0x120/+0x124/+0x128`.

Il codice ignora stride/format di `AndroidBitmapInfo` e usa il puntatore dopo `AndroidBitmap_unlockPixels`; questo resta un bug/rischio del binario originale, non va copiato nel port ARM64.

## Compile/link e fallback/error paths

**CONFIRMED** — `secloadShaderScript` G `0x17ee8`/ELF `0x7ee8`:

1. `glCreateShader(type)`; se ritorna 0, ritorna 0.
2. `glShaderSource(...,1,&source,NULL)`, `glCompileShader`.
3. `GL_COMPILE_STATUS`; su successo ritorna shader id.
4. Su errore legge `GL_INFO_LOG_LENGTH`. Se è nonzero, alloca il buffer, logga `"Could not compile shader %d:\n%s\n"`, elimina lo shader e ritorna 0 (anche un fallimento di `malloc` porta a delete/0).
5. **Bug storico confermato dall'assembly ELF `0x7f60..0x7f68`**: se compile fallisce ma `GL_INFO_LOG_LENGTH==0`, ritorna comunque lo shader id nonzero senza eliminarlo. Un port fedele che vuole però un fail-fast robusto dovrebbe correggere intenzionalmente questo caso e documentare la divergenza.

**CONFIRMED** — `secCreateShaderProgram` G `0x17fcc`/ELF `0x7fcc`:

1. Compila vertex e fragment; qualunque 0 fa ritornare 0.
2. `glCreateProgram`; 0 fa ritornare 0.
3. Attacca entrambi; dopo ciascun attach drena `glGetError()` e logga `"after glAttachShader() glError (0x%x)"`.
4. `glBindAttribLocation(program,0,"vertex")`, location 1 `"texCoord"`; poi link.
5. Se `GL_LINK_STATUS==1`, ritorna il program id.
6. Su errore prende il program log (se disponibile), logga `"Could not link program:\n%s\n"`, elimina il programma e ritorna 0.

**CONFIRMED**: i caller non verificano se il programma ritornato è 0 prima di conservarlo/usarlo. Non esiste shader alternativo/fallback. Inoltre gli shader compilati non vengono eliminati/detached dopo il link; è un leak storico da non riprodurre.

## UNRESOLVED

- **UNRESOLVED**: risultato di compilazione dei sorgenti gravity su ogni driver GLES moderno. Il codice è GLSL ES 1.00 valido sul target originale, ma solo una prova runtime sul Fold7 può confermare eventuali differenze/ottimizzazioni del driver corrente.
- **UNRESOLVED**: precisione visiva bit-identica del `mediump` fragment tra GPU Mali/Adreno storica e quella moderna; le formule sono esatte, la precisione hardware non è forzabile dal sorgente oltre al qualifier originale.
- **UNRESOLVED**: valore runtime di `glGetUniformLocation` per dichiarazioni eliminate dal compilatore. Per semantica GLES è normalmente `-1`; va loggato nel probe ARM64 se serve certificazione del driver specifico.
