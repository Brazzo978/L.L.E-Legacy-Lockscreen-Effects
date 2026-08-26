# Lens Flare variant audit — 2026-08-20

## Sources

- Erik's local `origin/pr-34`, commit `e9eb64d`.
- Archived `com.galaxytheme.lensflare` v1.1 APK supplied by Erik.
- Current app-owned Canvas/GLES renderers in L.L.E.

## Variant map

The archive uses one renderer and appends the selected integer to every resource
name. Its settings array establishes the exact mapping:

| Index | Style | L.L.E prefix |
| --- | --- | --- |
| 0 | Lens flare | `keyguard_flare_` |
| 1 | Blue rings | `keyguard_bluering_` |
| 2 | Red blood | `keyguard_blood_` |
| 3 | Lightning | `keyguard_lightning_` |

Lightning is not a separate bolt simulation. It uses the same light, ring,
particle, long, rainbow, hover-light and hexagon choreography as the other three
styles, with the index-3 texture family.

## Density correction

- The calibrated Original files are half-size copies of the archive's xxhdpi
  assets and are rendered with L.L.E's existing `inSampleSize=2` multiplier.
- Erik's Blue Ring PR files match the archive's xhdpi assets byte-for-byte. They
  therefore need a `0.75` render scale before the existing multiplier to preserve
  the original xhdpi-to-xxhdpi relationship.
- Erik's Blood PR files match the archive's xxhdpi assets. They need a `0.5`
  render scale before the existing multiplier.
- Lightning is imported from the xxhdpi index-3 family and uses the same `0.5`
  scale.

This fixes relative size without resampling or recolouring the source PNGs.

## Recovered shared choreography

- Seven touch hexagons.
- Initial touch-hexagon random scale `0.3..1.1`.
- Six drag hexagons with shuffled path targets and a `+0.2` scale offset.
- Show/tap/release/unlock durations remain `6000/4000/500/1200 ms`.
- Affordance fog remains `200 ms` in and `1100 ms` out.

## Reliability decision

Canvas/HWUI is the default renderer in Beta 2. The old default-true GLES
preference key is retired on upgrade. GLES remains an explicit A/B path and its
reported initialization/resize/draw failures continue to switch the preference
back to Canvas. Lightning no longer forces GLES, removing the previous permanent
retry path after a GL failure.

## S23 physical validation

Validated the Beta 2 tester on a Samsung Galaxy S23 Ultra (`SM-S918B`), Android
16 / API 36. The Canvas path completed 20/20 counted sleep/wake/reattach cycles
while rotating all four variants. A cycle counted only when both the renderer's
debug gesture begin and end were present in logcat. There were no ignored
gestures, readiness failures, fallbacks, OOMs, fatal exceptions or process-ID
changes. The optional GLES path separately completed 4/4 cycles across Original
and Lightning, reaching its first-frame-ready state each time without fallback.
