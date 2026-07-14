# Water Ripple normal transparent overlay

Data: 2026-07-14. Target di prova: Samsung Fold7 `RFCY70WM0JA` (SM-F966B).

## Separazione dalla pipeline Samsung

I sorgenti GLSL Samsung-exact restano in `native/ripple_gles_shaders.c` e non sono
stati modificati. SHA-256 verificato dopo il lavoro:

```text
ripple_gles_shaders.c  D0F895892896502C0E00DB490AEE1A9B41B1D06A75389E6E50D6FB871DDD50CB
ripple_gles_shaders.h  A20291352AF4EB56B091BC1FBE25E2641276211A0F41498FAE638BB6CD84A2EB
```

`LLE_RIPPLE_NORMAL_COMPOSITE_SAMSUNG_EXACT` continua a chiamare direttamente
`lle_ripple_gles_render()`. La modalita Android e una variante separata in
`ripple_gles_overlay_shader.c` e viene scelta esplicitamente con
`LLE_RIPPLE_NORMAL_COMPOSITE_TRANSPARENT_DELTA`.

## Formula e default

La variante ripete il calcolo RGB normal Samsung, lo limita al range del
framebuffer RGBA8 e sintetizza una maschera soltanto dalla pendenza locale:

```glsl
float slopeStrength = length(vNormal.xy);
float mask = smoothstep(0.035, 0.180, slopeStrength) * 1.0;
vec3 samsungResult = clamp(rippleRGB, 0.0, 1.0);
gl_FragColor = vec4(mask * samsungResult, mask);
```

Default C: `mask_low=0.035`, `mask_high=0.180`, `opacity=1.000`. L'uscita e
premoltiplicata: un mesh piatto ha `mask=0`; per ogni pixel e canale vale
`RGB <= A`. Il draw disabilita `GL_BLEND`, perche scrive il layer
premoltiplicato appena pulito a trasparente; la composizione source-over avviene
poi nel sistema Android.

## Test reale EGL/glReadPixels

Compilazione e link con NDK r27d, API 23:

```text
-std=c11 -O2 -fno-fast-math -ffp-contract=off -Wall -Wextra -Werror
ELF64 AArch64, SONAME libWaterRipple.so
NEEDED libGLESv2.so, libjnigraphics.so, liblog.so, libm.so, libdl.so, libc.so
```

Il test `native/ripple_gles_overlay_device_test.c` crea un pbuffer RGBA8
64x64, carica texture note, disegna un mesh piatto, poi un singolo impulso al
centro di un mesh 9x9, e legge il framebuffer con `glReadPixels`.

Risultato Fold7:

```text
PASS overlay formula=vec4(mask*SamsungResult,mask) defaults=0.035/0.180/1.000 flatNonzero=0 impulseNonzero=154 centerNonzero=154 borderNonzero=0 rgbOverAlpha=0 maxAlpha=255
```

Quindi sul driver reale:

- il mesh piatto e RGBA `(0,0,0,0)` esatto in tutti i 4096 pixel;
- l'impulso ha alpha nonzero in 154 pixel localizzati al centro;
- la fascia esterna di quattro pixel resta alpha zero;
- nessun pixel viola `R<=A`, `G<=A` o `B<=A`.

Artefatti temporanei verificati (non sono packaging canonico):

```text
ripple_gles_overlay_device_test  49264 bytes
SHA-256 3ECC25B69DFC54DD2AE0586B10D6F57594038081899DA367E41AFC4BBEA09CB1

libWaterRipple.so  55064 bytes
SHA-256 A1F2E6340D78B55AC2ED070FB498A839DEF30332B95711771A0C707CB1DEE07A
```

## Lifecycle runtime

Il bridge JNI v2 inizializza, distrugge e abbandona insieme pipeline e overlay.
`nativeRenderNormal()` usa i default sopra e seleziona soltanto la variante
trasparente. `S3Arm64RippleEffectView` continua a pulire il framebuffer a RGBA
zero prima del draw e rende disponibile `isReady()` per il fallback del factory.

Le entry point GL pubbliche scartano inoltre gli errori GL gia presenti prima di
iniziare un'operazione. Il drain e limitato a 16 letture, cosi un errore generato
da codice esterno non viene attribuito falsamente a init/upload/render senza
introdurre un loop illimitato su driver difettosi. Il test induce volutamente
`GL_INVALID_ENUM` prima dell'upload e verifica che l'upload successivo passi.
