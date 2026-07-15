# Runtime and Fold optimization pass (2026-07-15)

## Scope and invariants

This pass optimized the unified `com.codex.lle` application without changing
the established effect physics, shader constants, touch sampling, sensor rate
or 60/120 Hz pacing. Shared Java and resource changes were built for both
`armeabi-v7a` and `arm64-v8a`; runtime measurements were taken on the ARM64-only
Galaxy Z Fold7 (`SM-F966B`).

The visual rule remains strict: an effect background may be shared only when
its bitmap is an exact-size, immutable `ARGB_8888` cache for the active panel.
Renderers that transform the image or whose native lifetime is not proven keep
an owned copy.

## Fold wizard and capture state

The Fold touch-box wizard is now a two-panel dashboard rather than a single
editor with ambiguous state. It shows Cover and Main thumbnails, dimensions,
saved regions and per-panel actions. Saving a panel returns to the dashboard;
cancelling an armed capture atomically completes the pending request so the
accessibility service cannot remain stuck waiting for a screenshot.

The dedicated wizard images remain:

- `touch_box_lockscreen_cover.png`;
- `touch_box_lockscreen_main.png`.

When one is absent, the wizard can reuse that panel's already valid effect
background (`unlock_effect_background_cover.png` or
`unlock_effect_background_main.png`). This fixes the false `Capture needed`
state reported for Main even though its `1968x2184` screenshot already existed.
The overview explicitly labels this case as `Effect screenshot reused`.

The control screen also exposes both panel screenshots in one viewer. A missing
slot is rendered as an explicit state rather than silently replacing the other
panel.

## Implemented runtime changes

### Bitmap ownership and cache

- The in-memory background cache is shared per active Fold profile instead of
  being redundantly decoded for every effect. The persisted file was already
  per panel, so effect-keying the RAM copy had no benefit.
- Lens Flare, Watercolor, Popping Colours, Colour Droplet and compatible
  Samsung lock-background renderers borrow an exact shared cache bitmap.
- Every renderer tracks whether it owns its bitmap. A borrowed bitmap is never
  recycled by the renderer; a cropped, fallback or live-screenshot copy is.
- Cache replacement first rebinds a renderer that is borrowing the previous
  bitmap, then recycles the old cache. Teardown destroys the renderer before
  clearing the cache.
- Async capture callbacks are generation-checked. Stale success/failure cannot
  clear the current in-flight state or install a bitmap for the wrong panel.
- Sparkling Bubbles and both S3 Ripple implementations retain their defensive
  copies because their transform/native lifetime has not been proven safe for
  borrowing.

### Accessibility and touch hot paths

- Runtime accessibility events are limited to window state, windows changed
  and window content changed, with a `32 ms` notification timeout.
- Content events are throttled before expensive visibility work.
- PIN-entry and notification-shade detection share one bounded UI-tree walk.
- Fold panel resolution bypasses an accessibility-window scan when exactly one
  internal panel is active.
- Resolved touch regions are cached by panel profile and dimensions.
- The transparent touch listener no longer redraws its full transparent layer
  on every `DOWN/MOVE/UP`; invalidation remains enabled only for visible debug
  boxes.
- Stable visibility polls no longer repeat `setVisibility`, alpha and renderer
  `resume()` calls, and fast/exit polling messages are suppressed from Logcat.
- Automatic first-gesture profiling no longer calls `Debug.getMemoryInfo()`.
  Runtime memory sampling now runs only after the explicit `Sample next run`
  action.

### Lifecycle and sensor cleanup

- Delayed post-destroy GC is skipped if a new renderer already exists or the
  display has become interactive, preventing an old Fold teardown from
  stalling a newly created renderer.
- Screenshot request IDs protect the touch-capture callback from fold/unfold
  races and a cancelled wizard request.
- Both build pipelines now use the same bounded Samsung GL lifecycle DEX:
  `onPause()` and `requestExitAndWait()` stop waiting after 2000 ms. ARM32 then
  adds only its Abstract Tiles and Geometric Mosaic pacing overrides; the
  RGBA/Watercolor patches are not applied twice.
- Popping Colours now calls Samsung `removeEffect()`, removes child views and
  clears reflection/native references during destroy.
- Colour Droplet Gyro caches its display instead of resolving `WindowManager`
  at every sensor event. Rotation is still read live, `SENSOR_DELAY_GAME` and
  the native values are unchanged. Per-second sensor formatting is debug-gated.

## Measurements

The values below are snapshots, not a laboratory benchmark; Android graphics
accounting is affected by buffer-pool warm-up and SurfaceFlinger retention.

| State | Total PSS | Graphics | Bitmap malloced |
|---|---:|---:|---:|
| Main, before this pass | ~170.2 MiB | ~111.7 MiB | ~36.7 MiB |
| Cover baseline before shared bitmap | ~135.1 MiB | ~77.3 MiB | ~32.2 MiB |
| Cover, fresh optimized Lens build | ~108.8 MiB | ~75.9 MiB | ~13.7 MiB |
| Cover, after five Popping/Lens cycles | ~151.5 MiB | ~106.7 MiB | ~13.7 MiB |

The most reliable result is bitmap memory: the exact shared cache removed
about `18.5 MiB` of duplicate Cover bitmap allocation. A Main `1968x2184`
ARGB copy is about `16.4 MiB`, so avoiding duplicate ownership is even more
valuable on the inner panel.

Five controlled Popping Colours -> Lens Flare switch cycles reached a graphics
plateau (`106.4`, `106.6`, `106.7 MiB` for the last three samples). Only one LLE
effect surface and the process RenderThread remained; no Samsung GLThread,
timeout, recycled-bitmap error or crash was observed.

The pre-pass frame sample was 582 frames, 1.72% legacy jank, p50 `5 ms`, p90
`6 ms`, p95 `7 ms`, p99 `46 ms`. A later event-path sample showed p95 `7 ms`
and p99 `40 ms`, but injected Cover gestures were not delivered by the Samsung
lockscreen in that run, so it is not claimed as a like-for-like improvement.

## Experiments deliberately rejected

The transparent touch window was tested with `A_8`, `RGB_565` and near-zero
window alpha. SurfaceFlinger still promoted the buffer to `RGBA_8888`; zero
alpha also removed touch hit-testing. The production window therefore remains
`PixelFormat.TRANSLUCENT`, alpha `1`. The four Cover touch buffers and four
effect buffers remain the dominant GPU allocation and cannot be reduced safely
through this format trick.

Recursive hardware-layer removal from Samsung wrapper views was not shipped.
It may remove redundant offscreen layers, but requires an effect-by-effect A/B
test of transparency and graphics memory. `TextureView.setOpaque(false)` must
remain intact.

## Follow-up optimization backlog

The largest remaining deterministic native hotspot is the ARM64 Ripple GL
path. It currently uploads roughly 120 KiB of vertices, 120 KiB of heights and
117 KiB of indices per draw, revalidates 58,806 indices and repeats uniform/
attribute lookups. The safe next experiment is static VBO/IBO storage, cached
locations and per-frame upload of dynamic heights only. It must be validated
against a golden visual/touch run at both 60 and 120 Hz before shipping.

Further Samsung optimizations should also stay in a separate phase:

- compile-only typed stubs to remove reflection boxing from gyro and MOVE
  forwarding;
- one-effect-at-a-time removal of redundant wrapper hardware layers;
- ARM32 stress testing on real 32-bit hardware, which the Fold7 cannot run;
- 20 physical fold cycles and 60/120 Hz gesture runs on both panels.

## Same-device ABI comparison

`ABI-COMPARISON-S23U-2026-07-15.md` records a normalized ARM32/ARM64 run on an
S23 Ultra that natively supports both ABIs. ARM64 had lower resident PSS for
all seven common effects. Graphics allocation was effectively equal for most
Samsung renderers; Watercolor ARM64 was about 33.2 MiB lower, while the
app-owned Ripple ARM64 was about 35.8 MiB higher than the original ARM32
renderer. Ripple buffer ownership therefore remains the first native-memory
optimization target.
