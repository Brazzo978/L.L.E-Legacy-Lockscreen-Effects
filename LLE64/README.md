# L.L.E. 64

Baseline ARM64 separata di **Legacy Lockscreen Effects**, package `com.codex.lle`.

## Stato iniziale

- S4 Lens Flare: attivo, renderer Java/Canvas app-owned.
- S5 Popping Colours: attivo, renderer Samsung dex senza dipendenze `.so` legacy.
- Water Ripple, Watercolor, Abstract Tiles e Geometric Mosaic: disattivati finché il port AArch64 non è verificato.
- N5 Colour Droplet e Sparkling Bubbles: candidati AArch64 originali conservati in `reference/arm64-candidates`; entrambi superano `JNI_OnLoad`/`RegisterNatives` in una build isolata, ma restano fuori dall'APK stabile finche non e disponibile un runtime STLport AArch64 autentico.
- Tutte le librerie ARMv7 sono conservate intatte in `reference/arm32-original` e non partecipano alla build.

L'APK contiene una sola libreria nativa, `lib/arm64-v8a/liblle64marker.so`. Il marker viene caricato dal servizio di accessibilità e rende esplicito/fail-fast il vincolo ARM64.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Output: `build\LLE64-debug.apk`.

La build stabile fallisce se trova una libreria `armeabi-v7a`/x86 o una voce nativa diversa dal marker AArch64.

Sono disponibili due build sperimentali riproducibili e separate:

```powershell
# Solo test JNI delle librerie Note 5; contiene uno shim STLport non distribuibile.
powershell -ExecutionPolicy Bypass -File .\build.ps1 -IncludeNote5Probe

# Solo test ART dei metodi ARM64 initWaters/ripple/move.
powershell -ExecutionPolicy Bypass -File .\build.ps1 -IncludeRippleCoreProbe
```

Il port Water Ripple in `ports/water-ripple` ha superato sia il test nativo AArch64
sia il test JNI dentro ART. Non e ancora selezionabile: rendering GLES e texture
devono essere completati e verificati prima dell'integrazione.

## Dispositivo verificato

Galaxy Z Fold7 `SM-F966B`, Android 16/API 36: `arm64-v8a`, `zygote64`, nessuna ABI a 32 bit.
