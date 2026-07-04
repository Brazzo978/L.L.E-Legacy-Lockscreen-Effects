# Lock audio assets

Current lock audio assets bundled in `charging-touch-test-apk/res/raw`:

| Effect | Raw asset | Source dump |
| --- | --- | --- |
| S4 Lens Flare | `lens_flare_lock.ogg` | Note5 AOJ4 `media/audio/ui/lens_flare_lock.ogg` |
| S5 Popping Colours | `particle_lock.ogg` | S5 `media/audio/ui/ve_poppingcolours_lock.ogg` |
| S3 Ripple | `s3_lock.ogg` | S3 `media/audio/ui/Lock.ogg` |
| N4 Watercolor | `ve_watercolour_lock.ogg` | S5 `media/audio/ui/ve_watercolour_lock.ogg` |
| N5 Colored Droplet | `ve_colourdroplet_lock.ogg` | Note5 lockscreen effect audio |
| N5 Sparkling Bubbles | `ve_sparklingbubbles_lock.ogg` | Note5 AOJ4 `media/audio/ui/ve_sparklingbubbles_lock.ogg` |

These files are bundled as original lock sounds for the active/reversed effect set.
The current unlock renderer does not force-play them during gesture start; they are
available for a future exact lock/unlock audio behavior pass.
