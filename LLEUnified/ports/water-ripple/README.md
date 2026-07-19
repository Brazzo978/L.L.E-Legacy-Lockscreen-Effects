# Water Ripple AArch64 port

Port app-owned dell'effetto S3 Water Ripple, integrato nell'APK ARM64 e selezionabile nel picker come `S3 Water Ripple (Early Alpha)`. Il target corrente è il comportamento S3 letterale; l'adattamento specifico ai display Fold resta una fase separata.

## Implementato e verificato

- Core JNI `initWaters`, `ripple` e `move`, inclusi ordine numerico, mapping trasposto e shear storico del bulk NEON ARM32.
- Nove blob GLSL Samsung (otto sorgenti uniche) e pipeline GLES2 normal/ink/fluid/gravity; la view integrata usa Water Ripple normal.
- Output Android premoltiplicato e trasparente fuori dall'onda, senza coprire la SystemUI.
- Simulazione a 60 Hz indipendente dal refresh 60/120/144 Hz, con ordine draw-before-move Samsung.
- Bounds solver S3 letterali: portrait `3,21,101,83`, landscape `21,3,83,101`.
- Gesture stock validate su GT-I9301I e SM-G900F: down `4x`, un solo impulso drag `3x` oltre 150 px stock, long-up oltre 600 ms `4x`. Sui pannelli oltre 1080 px la soglia ARM64 viene scalata sul lato corto per conservare la stessa densità relativa di eventi.
- Suoni originali `s3_ripple_down/up`, affordance silenzioso, input multi-touch soppresso e riallineato senza un secondo down artificiale.
- Bridge JNI lifecycle, screenshot/background ownership, context recreation e cleanup bounded.
- Build AArch64 senza STLport o altre runtime C++ legacy.

Test Fold7 superati:

```text
PASS hash=59890e7812c02590 centerVelocity=2.50805116 centerHeight=7.21869993
PASS Water Ripple GLSL constants and GL_SHORT quad
PASS Water Ripple GLES2 programs=3,6,9,12,15 gravityDeadWaterBrightness=-1
PASS overlay formula=vec4(mask*SamsungResult,mask) defaults=0.035/0.180/1.000 flatNonzero=0 impulseNonzero=154 centerNonzero=154 borderNonzero=0 rgbOverAlpha=0 maxAlpha=255
```

Artefatto integrato corrente:

```text
libWaterRipple.so: 55.232 byte
SHA-256: E97948C72F48849776847EF4862EC75FEA4033736FC8E9DFCF157A202C2B37C7
APK SHA-256: 966001872D9852D1C72314D4923F939E8E4300A58400EBCC824A451821D3C55C
```

La build installata è stata sottoposta a cinque transizioni Gyro/Ripple: PID invariato e crash buffer vuoto.

## Divergenze ancora dichiarate

- Samsung disegna il wallpaper fullscreen con alpha `1.0`; LLE deve invece sintetizzare alpha locale per convivere con la lockscreen reale.
- LLE usa una screenshot SystemUI center-crop come color map. Wallpaper originale e compositing adattivo vengono valutati separatamente, non in questa build.
- Il clock mantiene 60 Hz nominali ma limita a quattro step di recupero dopo jank, scelta moderna di stabilità.
- Ink e gravity non sono esposti dalla view Early Alpha.
- Parità raster bit-per-bit fra GPU S3 e Fold non è realisticamente garantibile; shader, costanti, mesh e state machine vengono però mantenuti quanto più fedeli possibile.

Dettagli:

- `FIDELITY-SPEC.md`: specifica canonica completa di logica, fisica, mesh, gesture, rendering, lifecycle, test e divergenze.
- `IMPLEMENTATION-STATUS.md`: shader e pipeline GLES.
- `IMPLEMENTATION-LIFECYCLE.md`: bridge JNI, view Java, input, ownership e cleanup.
- `IMPLEMENTATION-OVERLAY.md`: variante trasparente e test EGL/glReadPixels.
- `reverse/s3-water-ripple/`: mappe reverse canoniche.
