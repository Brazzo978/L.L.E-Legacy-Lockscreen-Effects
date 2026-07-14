# L.L.E. 64

Baseline ARM64 separata di **Legacy Lockscreen Effects**, package `com.codex.lle`.

## Stato

- S4 Lens Flare: attivo, renderer Java/Canvas app-owned.
- S5 Popping Colours: attivo, renderer Samsung dex senza dipendenze `.so` legacy.
- N5 Colored Droplet e Sparkling Bubbles: attivi nella build stabile ARM64. Usano il dex Samsung con teardown bounded e le librerie AArch64/STLport originali AOJ4, patchate in staging per comporre soltanto le particelle sopra la SystemUI reale.
- N5 Colored Droplet + Gyro: attivo; riusa il renderer trasparente Droplet e inoltra l'accelerometro al bridge JNI soltanto mentre il lockscreen è attivo.
- Water Ripple: core JNI e pipeline GLES2 ARM64 verificati sul Fold7; integrazione lifecycle/JNI completa ancora in corso e non inclusa nell'APK stabile.
- Watercolor, Abstract Tiles e Geometric Mosaic: disattivati finché il port AArch64 non è verificato.
- Tutte le librerie ARMv7 restano intatte in `reference/arm32-original` e non partecipano alla build.

La build stabile contiene esclusivamente queste librerie sotto `lib/arm64-v8a`:

- `liblle64marker.so`
- `libColourDropletEffect.so`
- `libSparklingBubblesEffect.so`
- `libstlport.so`

Le copie firmware sotto `reference/` non vengono modificate. `build.ps1` ne verifica gli hash, le copia nella directory temporanea e applica le patch di trasparenza soltanto alle copie staged. La build verifica inoltre AArch64, SONAME, dipendenze, opcode/GLSL patched ed elenco esatto delle entry native dell'APK.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Output: `build\LLE64-debug.apk`.

Probe Water Ripple core isolato:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -IncludeRippleCoreProbe
```

`-IncludeNote5Probe` produce ancora un APK di diagnostica con la probe Activity esportata, ma usa gli stessi renderer N5 ARM64 e lo stesso dex bounded della build stabile.

## Verifica Fold7

Galaxy Z Fold7 `SM-F966B`, Android 16/API 36: `arm64-v8a`, `zygote64`, nessuna ABI a 32 bit.

Colored Droplet, Droplet + Gyro e Sparkling Bubbles hanno superato load JNI, EGL/GLES, input, reset, destroy, park/resume e verifica visiva trasparente sopra il lockscreen reale. Per Gyro sono stati osservati campioni accelerometro reali e quattro coppie register/unregister pulite. L'APK installato riporta `primaryCpuAbi=arm64-v8a`; durante i cicli schermo non sono stati osservati crash, errori EGL/JNI o timeout GL nel processo LLE.

## Distribuzione

Le tre librerie Note 5 derivano da firmware Samsung e sono proprietarie. La build è tecnicamente valida per test privato/locale; una distribuzione pubblica richiede una decisione separata sui diritti di redistribuzione.
