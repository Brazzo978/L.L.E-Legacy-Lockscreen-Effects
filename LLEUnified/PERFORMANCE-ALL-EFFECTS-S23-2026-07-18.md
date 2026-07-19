# ARM64 all-effects performance round — S23 Ultra — 2026-07-18

## Scope and conditions

- APK/package: `com.codex.lle64`, `1.0.2-beta.2`, ARM64 only.
- Device: Samsung `SM-S918B`, Android 16, `1440x3088`, active render rate 120 Hz.
- Power: USB charging, battery 75–76%, battery 33.2–34.3 °C.
- Thermal status stayed `0`; AP rose from 36.3 to 36.8 °C and skin from 34.5 to 35.4 °C.
- Accessibility: LLE64 and Bitwarden remained the only enabled services at the end; no binding or crashed service.
- Workload: four zero-distance taps at `(720,1600)`, 450 ms apart, followed by 950 ms of effect tail. Each timing window was about 3.2 s.
- CPU is aggregate process running time from `/proc/<pid>/task/*/stat`, with `CLK_TCK=100`. `100%` means one fully occupied core, not the whole 8-core SoC.
- GPU busy is the device-wide KGSL counter. It includes SystemUI/keyguard/composition and is useful for same-session comparisons, not as an app-only percentage.
- `dumpsys meminfo` ran only after each CPU/GPU timing window. Screen recording and screenshots were disabled.

All ten ARM64 effect engines exposed by `EffectAvailability` were exercised. Abstract Tiles was measured with `Line ON`; the `No lines` UI choice shares effect id 7 and the same renderer, with its Line pass disabled, so it was not a separate debug-profile target.

## CPU and GPU results

| Effect | Process CPU | Hottest effect thread | Global GPU busy | Notes |
|---|---:|---:|---:|---|
| S4 Lens Flare | 27.5% | RenderThread 15.8% | 26.7% | Canvas/HWUI path; includes lockscreen composition |
| S3 Water Ripple | 16.0% | GLThread 10.7% | 16.9% | App-owned GLES, fixed 60 Hz solver, 120 Hz presentation |
| S5 Popping Colours | 16.5% | RenderThread 8.7% | 18.2% | Samsung DEX renderer |
| Tab S Blind | 24.6–25.5% | RenderThread 12.5–12.9% | 30.9–34.8% | Two valid touch runs |
| N3 Watercolor | 16.5% | RenderThread 6.8% | 18.3% | ARM64 GLES port |
| N4 Abstract Tiles · Line ON | 10.8% | GLThread 5.3% | 12.1% | Direct SurfaceView/GLES |
| N4 Geometric Mosaic | 8.7% | GLThread 4.0% | 10.8% | Lightest measured active renderer |
| N5 Colored Droplet | 42.3–61.5% | GLThread 33.3–50.3% | 8.5–14.7% | CPU-heavy native simulation |
| N5 Colored Droplet + Gyro | 78.5–93.4% | GLThread 64.2–75.1% | 27.9–37.7% | Sensor/native path adds a large sustained cost |
| N5 Sparkling Bubbles | 107.2% | GLThread 89.7% | 23.9% | More than one core in aggregate; clear primary hotspot |

The outliers were repeated after recreating their renderers. Droplet, Gyro and Blind stayed in the same performance class. The first Bubbles run did not receive touch and was discarded; the reported run delivered all four touches.

## Load, touch and frame latency

`Init total` is the existing in-app diagnostic and includes its own memory snapshots, so it is not a pure renderer timing. `Touch sync` and `Begin` end when the command is queued to the renderer; they are not input-to-visible latency.

| Effect | Preload | Attach | Init total | Touch sync max | Begin max | Wrapper/HWUI frame result |
|---|---:|---:|---:|---:|---:|---|
| Lens Flare | 48 ms | 76 ms | 281 ms | 1 ms | 1 ms | 1.06% jank; CPU p95 9 ms; GPU p95 7 ms |
| Water Ripple | 71 ms | 8 ms | 133 ms | 2 ms | 0 ms | direct GL; see Perfetto below |
| Popping Colours | 84 ms | 32 ms | 182 ms | 2 ms | 0 ms | 0.81% jank; CPU p95 5 ms; GPU p95 2 ms |
| Blind | 167–253 ms | 7–29 ms | 269–299 ms | 1 ms | 1–2 ms | 1.29–1.59% jank; p95 9/8 ms |
| Watercolor | 81 ms | 35 ms | 183 ms | 1 ms | 1 ms | 3.42% legacy jank; p95 5/2 ms |
| Abstract Tiles | 187 ms | 18 ms | 405 ms | 1 ms | 1 ms | direct GL; `gfxinfo` is not representative |
| Geometric Mosaic | 77 ms | 6 ms | 139 ms | 2 ms | 3 ms | direct GL; `gfxinfo` is not representative |
| Colored Droplet | 98–119 ms | 21–45 ms | 192–281 ms | 1–2 ms | 2–4 ms | 0–0.49% jank; p95 5/3 ms |
| Droplet + Gyro | 86–97 ms | 14–26 ms | 155–191 ms | 1 ms | 3–4 ms | 1.47–1.55% jank; p95 9/9 ms |
| Sparkling Bubbles | 120 ms | 19 ms | 361 ms | 1 ms | 5 ms | 0.73% jank; p95 9/8 ms |

Water Ripple received a separate 7 s Perfetto system trace with 366 `onDrawFrame` calls:

- active frame window: 4.279 s;
- `onDrawFrame`: p50 4.482 ms, p95 5.263 ms, p99 6.559 ms, max 10.909 ms;
- frame-start interval: p50 8.328 ms, p95 9.155 ms, p99 11.683 ms;
- only 3 intervals of 362 (`0.83%`) exceeded 12.5 ms; one exceeded 20.83 ms during screen/lifecycle transition;
- `eglSwapBuffers`: p95 5.815 ms, p99 7.473 ms;
- GPU completion fence wait: p50 8.280 ms, p95 9.081 ms, p99 12.025 ms;
- LLE CPU inside the active frame window: 690.7 CPU-ms / 4279.4 wall-ms = 16.14% of one core; GLThread used 383.4 CPU-ms.

The current code has no allocation-free marker for input receipt → GL `handleTouch` → first presented modified frame. For Ripple, static timing gives an expected visible response of roughly 25–33 ms at 120 Hz because Samsung's faithful order draws first, advances the 60 Hz solver afterward, then presents the modified heights on a later frame. At 60 Hz the corresponding expected window is roughly 33–50 ms. A true measured end-to-end value needs three trace markers plus FrameTimeline correlation.

## RAM snapshots

These are warm production-style snapshots after interaction, in MiB. They are useful as observed upper bounds but are not a clean ranking: SurfaceFlinger, EGL and the app retain buffers/caches across renderer switches, and Android swap changed materially during the round. A laboratory 1:1 comparison requires a fresh process or reboot for every effect.

| Effect | PSS | RSS | Graphics | Native heap | Bitmap malloced |
|---|---:|---:|---:|---:|---:|
| Lens Flare | 95.3 | 140.0 | 23.8 | 29.4 | 67.1 |
| Water Ripple | 341.0 | 387.2 | 231.3 | 66.1 | 48.1 |
| Popping Colours | 320.3 | 367.5 | 217.3 | 44.7 | 62.1 |
| Blind | 420.7 | 489.3 | 239.1 | 105.9 | 113.7 |
| Watercolor | 324.2 | 373.0 | 214.4 | 44.2 | 27.2 |
| Abstract Tiles | 230.5 | 279.6 | 163.7 | 26.7 | 27.2 |
| Geometric Mosaic | 262.3 | 312.1 | 180.9 | 28.0 | 27.2 |
| Colored Droplet | 388.9 | 459.9 | 287.8 | 39.3 | 30.9 |
| Droplet + Gyro | 406.9 | 478.3 | 305.3 | 39.6 | 34.7 |
| Sparkling Bubbles | 422.5 | 502.8 | 282.2 | 102.6 | 68.5 |

Ripple's dedicated stable sample showed why it is expensive in graphics memory: four full-screen SurfaceView buffers at about 17.8 MiB each, plus the effect ViewRoot and touch-listener buffers. `GraphicBufferAllocator` accounted for about 130.8 MiB before textures and other GL allocations.

## Findings and priorities

1. **P0 — N5 frame pacing/power:** Sparkling Bubbles, Droplet + Gyro and Droplet are CPU-bound on a 120 Hz panel. These Note 5 engines were designed around 60 Hz hardware. The next controlled experiment should compare 60 vs 120 Hz and determine whether surface-level 60 Hz pacing halves work without changing native physics.
2. **P1 — Water Ripple data path:** visual frame pacing is already good, but each draw reacquires Java arrays and uploads static vertices/indices with dynamic heights. The current path transfers about 357,612 bytes per draw, roughly 42.9 MB/s at 120 Hz. Static VBO/IBO storage, cached shader locations and dynamic-height-only updates are the safest optimization candidates.
3. **P1 — touch allocation/coalescing:** Ripple allocates coordinate arrays and a `Runnable` for every touch event. High-rate MOVE streams can add GC pressure and queue stale moves even though the visible ripple threshold is now correct.
4. **P1 — RAM protocol:** add a release-like `profileable` diagnostic variant or a protected self-restart hook so each renderer can be measured from an identical fresh-process state. Do not compare the warm PSS column as if it were isolated ownership.
5. **P2 — exact latency instrumentation:** add sequence/timestamps at input receipt, GL touch handling and first modified draw; correlate the final marker with Perfetto FrameTimeline. Existing `syncMs`/`beginMs` values only prove that the Java forwarding path is fast.

No effect crashed, the process PID remained stable during the normal switch matrix, thermal throttling never engaged, and Lens Flare was restored as the selected effect at the end.

## Artifacts

- Reusable harness: `tools/profile-arm64-effects.ps1`
- Perfetto traces: `ports/water-ripple/reference/performance-s23-20260718/`
- Detailed Ripple comparison: `ports/water-ripple/reference/comparison-20260718/README.md`
