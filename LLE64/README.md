# L.L.E. 64

Baseline ARM64 separata di **Legacy Lockscreen Effects**, package `com.codex.lle`.

## Stato iniziale

- S4 Lens Flare: attivo, renderer Java/Canvas app-owned.
- S5 Popping Colours: attivo, renderer Samsung dex senza dipendenze `.so` legacy.
- Water Ripple, Watercolor, Abstract Tiles e Geometric Mosaic: disattivati finché il port AArch64 non è verificato.
- N5 Colour Droplet e Sparkling Bubbles: candidati AArch64 originali conservati in `reference/arm64-candidates`, non ancora inclusi nell'APK.
- Tutte le librerie ARMv7 sono conservate intatte in `reference/arm32-original` e non partecipano alla build.

L'APK contiene una sola libreria nativa, `lib/arm64-v8a/liblle64marker.so`. Il marker viene caricato dal servizio di accessibilità e rende esplicito/fail-fast il vincolo ARM64.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Output: `build\LLE64-debug.apk`.

La build fallisce se trova una libreria `armeabi-v7a`/x86 o una voce nativa diversa dal marker AArch64.

## Dispositivo verificato

Galaxy Z Fold7 `SM-F966B`, Android 16/API 36: `arm64-v8a`, `zygote64`, nessuna ABI a 32 bit.
