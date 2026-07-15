# ARM32 vs ARM64 runtime comparison (S23 Ultra, 2026-07-15)

## Test device and method

The same Samsung Galaxy S23 Ultra (`SM-S918B`, Android 16) was used for both
products. The device reports `arm64-v8a, armeabi-v7a, armeabi`, so it can run
both APKs natively. Physical resolution was `1440x3088`; the panel supports
60/96/120 Hz and was in the 60 Hz mode reported by DisplayManager during the
initial device audit.

Each final APK was installed with `adb install -r`, preserving the same LLE
preferences and screenshot cache. Every effect sample used a fresh app process
and the same state transition:

1. save the selected effect;
2. reinstall the same ABI to start a clean process;
3. wake the locked screen;
4. return it to sleep/doze;
5. wait four seconds for LLE's screen-off prearm;
6. collect `dumpsys meminfo` and process thread state.

This measures a resident, prearmed lockscreen renderer rather than a Java-only
preload. No app data was cleared between ABIs. Results are snapshots and should
not be compared to a different device resolution.

## Resident memory

| Effect | ARM32 PSS | ARM64 PSS | ARM64 difference | ARM32 Graphics | ARM64 Graphics |
|---|---:|---:|---:|---:|---:|
| S4 Lens Flare | 246.7 MiB | 200.2 MiB | -46.5 MiB | 135.0 MiB | 135.2 MiB |
| S5 Popping Colours | 152.2 MiB | 98.2 MiB | -54.0 MiB | 64.9 MiB | 64.6 MiB |
| N3 Watercolor | 366.8 MiB | 264.6 MiB | -102.2 MiB | 184.4 MiB | 151.2 MiB |
| N5 Colored Droplet | 343.9 MiB | 298.6 MiB | -45.3 MiB | 236.5 MiB | 236.4 MiB |
| N5 Sparkling Bubbles | 399.0 MiB | 354.3 MiB | -44.7 MiB | 239.9 MiB | 240.1 MiB |
| N5 Colored Droplet + Gyro | 334.2 MiB | 288.5 MiB | -45.7 MiB | 236.1 MiB | 236.7 MiB |
| S3 Water Ripple | 241.5 MiB | 232.2 MiB | -9.3 MiB | 129.6 MiB | 165.4 MiB |

ARM64 had lower total PSS in every same-device sample. Most Samsung effects
used essentially the same Graphics allocation on both ABIs, while ARM32 also
carried roughly 13 MiB of swap PSS versus less than 1 MiB for ARM64.

The two meaningful Graphics differences were:

- ARM64 Watercolor used about 33.2 MiB less Graphics;
- the app-owned ARM64 Ripple used about 35.8 MiB more Graphics than the
  original ARM32 Ripple.

The Ripple result reinforces the planned static VBO/IBO and buffer-lifetime
work. It is the only common effect where the ARM64 PSS advantage was small.

## Warm switch preload latency

The diagnostic switch sequence used the same effect order on both ABIs and a
warm screenshot cache. These times measure renderer preload, not the subsequent
lockscreen animation.

| Effect | ARM32 preload | ARM64 preload |
|---|---:|---:|
| S4 Lens Flare | 71 ms | 43 ms |
| S5 Popping Colours | 23 ms | 19 ms |
| N3 Watercolor | 20 ms | 26 ms |
| N5 Colored Droplet | 91 ms | 73 ms |
| N5 Sparkling Bubbles | 136 ms | 120 ms |
| N5 Colored Droplet + Gyro | 65 ms | 53 ms |
| S3 Water Ripple | 87 ms | 38 ms |

ARM64 was faster in six of seven warm preload samples; Watercolor ARM32 was
6 ms faster in this run.

## Threads and stability

Lens Flare and Popping Colours used no GL thread. With the render Surface
mounted, the Samsung/native GL effects consistently exposed six `GLThread`
entries on both ABIs. A Java-only preload sometimes showed one thread, which is
why thread counts must be taken only after the same prearm transition.

Both APKs completed all common-effect samples without a fatal signal, ANR,
recycled-bitmap failure or bounded lifecycle timeout. After the comparison the
phone was restored to the ARM64 APK with S4 Lens Flare selected.

## ARM32 bootstrap issue found by the test

The first unified ARM32 attempt crashed before renderer creation because
`Lle64Abi` unconditionally loaded `liblle64marker.so`. That marker is correctly
present only in the ARM64 APK. Runtime ABI detection now uses
`Process.is64Bit()`; ARM64 still loads and validates the native marker, while
the `armeabi-v7a` process does not request it. The fixed ARM32 service rebound
with `connected abi=armeabi-v7a` and cleared Android's crashed-service state.
