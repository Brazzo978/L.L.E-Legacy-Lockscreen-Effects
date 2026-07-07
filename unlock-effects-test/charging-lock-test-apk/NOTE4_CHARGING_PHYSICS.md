# Note 4 seasonal charging physics

Reference source: Samsung `SystemUI` decompile, classes:

- `ChargingSpringView`
- `ChargingSummerView`
- `ChargingAutumnView`
- `ChargingView` (winter)
- `ParticleStraightLine`

The stock coordinate system is `360 x 640 dp`. The original code multiplies every
coordinate by display density (`coefficientX`). The PNGs in this port come from
`drawable-xxxhdpi`, so bitmap pixels should be converted back to stock dp with a
`3.0` asset scale.

## Shared battery levels

Spring, summer and autumn use five visible charging frames:

| Battery level | Frame index |
| --- | --- |
| `0..25` | `0` |
| `26..50` | `1` |
| `51..75` | `2` |
| `76..99` | `3` |
| `100` | `4` |

Winter has a separate base image and only four charging overlays:

| Battery level | Winter overlay |
| --- | --- |
| `0..25` | none, base only |
| `26..50` | `winter_charging_25p` |
| `51..75` | `winter_charging_50p` |
| `76..99` | `winter_charging_75p` |
| `100` | `winter_charging_100p` |

## Shared particle animator

`ParticleStraightLine` uses:

- translate X/Y: start to end, `1600 ms`, repeat restart
- alpha: `1.0 -> 0.0`, `1600 ms`, repeat restart, linear interpolator
- scale X/Y: current scale to random target, `1600 ms`, repeat restart
- rotation when enabled: `0 -> 359`, repeat restart
- start delay: particle index `* 500 ms`

Default Android `ObjectAnimator` interpolator applies to translate, scale and
rotation unless the stock class explicitly sets another interpolator.

## Spring

Media used by charging:

- frames: `spring_charging_25p`, `spring_charging_50p`,
  `spring_charging_75p`, `spring_charging_99p`, `spring_charging_100p`
- particles by slot:
  `spring_particle_01 x6`, `spring_particle_02 x2`,
  `spring_particle_03 x4`, `spring_particle_04 x1`

Media not used by spring charging: `flower_01..03`.

Frame motion:

- frame x: `118`
- frame y: `300 -> 266`
- y duration: `3300 ms`, repeat reverse
- rotation: `0 -> 359`, `20010 ms`, repeat restart

Particles:

- count: 13 normally, 12 at full charge (slot 12 is skipped)
- non-full start: random x in 30%-70% of stock width, y `888`
- non-full end: x `170`, y `334`
- full start: x `170`, y `350`
- full end: random point around x `170`, y `350`, radius `113`
- rotation: enabled for all spring particle slots, `1600 ms`
- scale targets:
  - slots `0..5`: `0.5 + 0.3 * random`
  - slots `6..7`: `0.7 + 0.3 * random`
  - slots `8..11`: `0.4 + 0.2 * random`
  - slot `12`: `0.5 + 0.7 * random`

Z order: particles below charging frame.

## Summer

Media used by charging:

- frames: `summer_charging_01..05`
- particles by slot:
  `summer_particle_01 x8`, `summer_particle_02 x1`,
  `summer_particle_03 x1`

Media not used by summer charging: `unlock_summer_*`.

Frame motion:

- frame x: `118`
- frame y: `300 -> 266`
- y duration: `3300 ms`, repeat reverse
- rotation: `0 -> 359`, `20010 ms`, repeat restart

Particles:

- count: 10 normally
- full charge: loop returns at slot 8, so only slots `0..7` are visible
- non-full start: x `170`, y `888`
- non-full end: random x in 30%-70% of stock width, y `334`
- full start: x `170`, y `350`
- full end: random point around x `170`, y `350`, radius `113`, but each
  axis uses `90 - random(180)` degrees
- rotation: enabled for all summer particle slots, `1600 ms`
- scale targets:
  - slots `0..7`: `0.5 + 0.5 * random`
  - slot `8`: `0.8 + 0.2 * random`
  - slot `9`: `1.0 + 0.2 * random`

Z order: particles below charging frame.

## Autumn

Media used by charging:

- frames: `autumn_charging_01..05`
- full-charge circle: `autumn_charging_circle x4`
- particles by slot:
  `autumn_particle_01 x3`, `autumn_particle_02 x1`,
  `autumn_particle_03 x1`, `autumn_particle_04 x1`

Media not used by autumn charging: `leaf_01..04`, `unlock_autumn_*`.

Frame motion:

- frame x: `114`
- frame y: `300 -> 266`
- y duration: `3300 ms`, repeat reverse
- rotation: `-10 -> 10`, `6670 ms`, repeat reverse

Particles:

- count: 6 normally
- full charge: no regular particles; only the four circles are animated
- non-full start: x `170`, y `888`
- non-full end: random x in 30%-70% of stock width, y `334`
- rotation: enabled for all autumn particle slots, `1600 ms`
- scale targets:
  - slots `0..2`: `0.6 + 0.4 * random`
  - slots `3..5`: `0.8 + 0.2 * random`

Full-charge circles:

- image: `autumn_charging_circle`
- count: 4
- x: `100`
- y: `264`
- start delay: circle index `* 1500 ms`
- alpha: `1.0 -> 0.0`, `4000 ms`, repeat restart,
  `AccelerateInterpolator`
- scale X/Y: `1.0 -> 2.2`, `4000 ms`, repeat restart

Z order: particles, circles, charging frame. The decompiled class has a
`initViewRotateBase()` method, but `initView()` does not call it, so the winter
base is not part of the stock autumn charging effect.

## Winter

Media used by charging:

- base: `winter_charging_base`
- frames: `winter_charging_25p`, `winter_charging_50p`,
  `winter_charging_75p`, `winter_charging_100p`
- particles weighted by random slot:
  `winter_particle_01 x3`, `winter_particle_02 x3`,
  `winter_particle_03 x5`, `winter_particle_04 x1`

Frame/base motion:

- x: `121`
- base y: `300 -> 267`
- frame y: `300 -> 266`
- y duration: `3300 ms`, repeat reverse
- rotation: `0 -> 359`, `20010 ms`, repeat restart

Particles:

- count: 12
- stock init chooses `random(12)` for each particle view, then resolves through
  the weighted slot list above
- non-full start: x `170`, y `888`
- non-full end: random x in 30%-70% of stock width, y `334`
- full start: x `170`, y `350`
- full end: random point around x `170`, y `350`, radius `113`
- rotation: enabled for weighted slots `0..10`, disabled for slot `11`
- rotation duration: `1000 ms`
- scale targets:
  - slots `0..2`: `0.6 + 0.8 * random`
  - slots `3..5`: `0.5 + 0.7 * random`
  - slots `6..10`: `1.0 + 1.6 * random`
  - slot `11`: `1.0`

Z order: particles, base, charging frame.

## Corrections for this port

- Use `3.0` as the asset scale for the extracted `drawable-xxxhdpi` PNGs.
- Remove the custom charging text label from the seasonal doodle view; stock
  charging effects only draw the seasonal sprites.
- Do not load or map unlock/touch media into charging physics.
- Do not include `flower_01..03` or `leaf_01..04` in the charging sprite arrays;
  those assets are present in the APK, but not referenced by the charging view
  classes.
- Keep the autumn circle layer below the autumn charging frame.
- Keep autumn without `winter_charging_base`; the method exists in the
  decompiled class, but it is not called for autumn.
- Generate particle endpoints and target scales once per season/full-state
  change, then reuse those values while drawing. This mirrors the stock
  `animationViewParticle()` behavior: values are random, but always inside the
  same Samsung ranges and not recomputed every frame.
- Winter should preserve Samsung's weighted random sprite selection: each
  particle view chooses `random(12)` over the stock weighted slot list
  (`01 x3`, `02 x3`, `03 x5`, `04 x1`). Those sprite slots stay stable while
  winter regenerates endpoints/scales for full/non-full transitions. Slot `11`
  is the only non-rotating winter particle.
