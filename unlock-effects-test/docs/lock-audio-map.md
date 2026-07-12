# Lock audio assets

Current lock audio assets bundled in `charging-touch-test-apk/res/raw`:

| Effect | Raw asset | Source dump |
| --- | --- | --- |
| S4 Lens Flare | `lens_flare_lock.ogg` | Note5 AOJ4 `media/audio/ui/lens_flare_lock.ogg` |
| S5 Popping Colours | `particle_lock.ogg` | S5 `media/audio/ui/ve_poppingcolours_lock.ogg` |
| S3 Ripple | `s3_lock.ogg` | S3 `media/audio/ui/Lock.ogg` |
| N3 Watercolor | `ve_watercolour_lock.ogg` | S5 `media/audio/ui/ve_watercolour_lock.ogg` |
| N5 Colored Droplet | `ve_colourdroplet_lock.ogg` | Note5 lockscreen effect audio |
| N5 Sparkling Bubbles | `ve_sparklingbubbles_lock.ogg` | Note5 AOJ4 `media/audio/ui/ve_sparklingbubbles_lock.ogg` |
| N4 Abstract Tiles | `abstracttile_lock.ogg` | Note4 N9005 SystemUI `media/audio/ui/abstracttile_lock.ogg` |
| N4 Geometric Mosaic | `geometricmosaic_lock.ogg` | Note4 N9005 SystemUI `media/audio/ui/GeometricMosaic_lock.ogg` |
| Seasonal doodle/partner Spring | `spring_lock.ogg` | Note4 SystemUI `res/raw/spring_lock.ogg` |
| Seasonal doodle/partner Summer | `summer_lock.ogg` | Note4 SystemUI `res/raw/summer_lock.ogg` |
| Seasonal doodle/partner Autumn | `autumn_lock.ogg` | Note4 SystemUI `res/raw/autumn_lock.ogg` |
| Seasonal doodle/partner Winter | `winter_lock.ogg` | Note4 SystemUI `res/raw/winter_lock.ogg` |

`LockSoundPlayer` plays the matching lock sound on `ACTION_SCREEN_OFF`, respecting
Android `lockscreen_sounds_enabled`. Charging doodle mode uses the seasonal lock
sound; otherwise LLE uses the currently selected lockscreen effect.

## Interaction audio verification (2026-07-12)

- N4 Geometric Mosaic uses Samsung's `brilliantcut_tap`, `brilliantcut_drag` and `brilliantcut_unlock`; it must not reuse Abstract Tiles interaction audio. The build stages the byte-identical live S4 assets from `demo-apk/res/raw`.
- N3 Watercolor uses the 24,881-byte S4/Note3/Note4/S5 `ve_watercolour_tap` (`SHA-256 D42EE22D...`) and replays it once on the first MOVE after 411 ms, matching Samsung. The previous 64,083-byte Tab T705 variant is no longer packaged.
- Abstract Tiles, Lens Flare, S3 Ripple, N5 Colour Droplet, N5 Sparkling Bubbles and all seasonal interaction assets match their selected stock sources.
- Popping Colours intentionally mixes generations only where verified: current tap/drag are the S4 raw pair, while unlock/lock are the newer S5 `Particle_Unlock` / `ve_poppingcolours_lock`. The user confirmed the S5 drag sounds different from S4; its exact S5 raw asset still needs acquisition before replacing the current drag.
