# Workshop tuning

Particle sizes and emission radii are separate controls, in pixels relative to
the recovered 1440-pixel short-side reference. Lifetimes and growth ranges are
in seconds; lifetime conversion still uses the recovered 60-Hz tick clock.
Counts affect initialized particles only; allocation/export/draw capacity stays
25 groups by 1100 slots. Small/medium/large shares retain the recovered
80/15/5-percent branch boundaries, including its inclusive boundary quirk.
No gravity/SPH/tilt controls are exposed: this renderer uses radial motion and
edge acceleration, rather than the droplet fluid solver.

Each CPU simulation owns its tuning struct. JNI accepts exactly the following float array; Java packs the same order in EffectWorkshopDropsParameters. C clamps finite values, falls back per field for NaN/Inf, rounds counts, and orders each min/max pair. NULL/wrong-length core input restores defaults. Configuration survives reset/resize/EGL loss. Defaults keep recovered behavior; no resource topology or unlock timing changes. Settings affect new particles and growth/physics/optical export, and must be installed before first input/resize/step.

| Index | Key | Default | Range |
|---|---|---|---|
| 0 | `press_count` | 1100 | 1..1100 |
| 1 | `hint_count` | 1100 | 1..1100 |
| 2 | `press_small_radius_min` | 40 | 0..400 |
| 3 | `press_small_radius_max` | 250 | 0..400 |
| 4 | `press_medium_radius_min` | 10 | 0..400 |
| 5 | `press_medium_radius_max` | 220 | 0..400 |
| 6 | `press_large_radius_min` | 10 | 0..400 |
| 7 | `press_large_radius_max` | 240 | 0..400 |
| 8 | `hint_small_radius_min` | 30 | 0..400 |
| 9 | `hint_small_radius_max` | 250 | 0..400 |
| 10 | `hint_medium_radius_min` | 10 | 0..400 |
| 11 | `hint_medium_radius_max` | 220 | 0..400 |
| 12 | `hint_large_radius_min` | 10 | 0..400 |
| 13 | `hint_large_radius_max` | 200 | 0..400 |
| 14 | `small_size_min` | 4 | 1..80 |
| 15 | `small_size_max` | 10 | 1..80 |
| 16 | `medium_size_min` | 12 | 1..80 |
| 17 | `medium_size_max` | 18 | 1..80 |
| 18 | `medium_target_size_min` | 12 | 1..80 |
| 19 | `medium_target_size_max` | 25 | 1..80 |
| 20 | `large_size_min` | 24 | 1..80 |
| 21 | `large_size_max` | 28 | 1..80 |
| 22 | `large_target_size_min` | 28 | 1..80 |
| 23 | `large_target_size_max` | 43 | 1..80 |
| 24 | `press_lifetime_min` | 2.2 | 0.5..5 |
| 25 | `press_lifetime_max` | 2.5 | 0.5..5 |
| 26 | `hint_small_lifetime_min` | 2 | 0.5..5 |
| 27 | `hint_small_lifetime_max` | 2.5 | 0.5..5 |
| 28 | `hint_medium_lifetime_min` | 1.8 | 0.5..5 |
| 29 | `hint_medium_lifetime_max` | 2.3 | 0.5..5 |
| 30 | `hint_large_lifetime_min` | 1.6 | 0.5..5 |
| 31 | `hint_large_lifetime_max` | 2.1 | 0.5..5 |
| 32 | `medium_growth_min` | 0.3 | 0.1..2 |
| 33 | `medium_growth_max` | 0.5 | 0.1..2 |
| 34 | `large_growth_min` | 0.3 | 0.1..2 |
| 35 | `large_growth_max` | 0.7 | 0.1..2 |
| 36 | `press_alpha_min` | 0.35 | 0..1 |
| 37 | `press_alpha_max` | 0.6 | 0..1 |
| 38 | `press_large_alpha_min` | 0.25 | 0..1 |
| 39 | `press_large_alpha_max` | 0.6 | 0..1 |
| 40 | `hint_alpha_min` | 0.5 | 0..1 |
| 41 | `hint_alpha_max` | 0.6 | 0..1 |
| 42 | `speed_min` | 0.01 | 0..0.08 |
| 43 | `speed_max` | 0.03 | 0..0.08 |
| 44 | `press_acceleration` | 0.6 | 0..2 |
| 45 | `hint_acceleration` | 1 | 0..2 |
| 46 | `edge_band_min` | 0.03 | 0.01..0.3 |
| 47 | `edge_band_max` | 0.12 | 0.01..0.3 |
