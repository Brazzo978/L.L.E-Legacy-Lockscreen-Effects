# Workshop tuning

Effect 23 exposes 12 controls; effect 24 (accelerometer variant) exposes 14.
The native layout below has 14 entries for both. Java supplies the original
coefficients at indices 7 and 8 for effect 23, without exposing inactive tilt
controls. Physics scales multiply the recovered phone/tablet and growth-phase
coefficients; these are model coefficients, not physical SI units. Radius
scales the grown SPH smoothing radius, rather than screen/grid/FBO resolution.

Each CPU simulation owns its tuning struct. JNI accepts exactly the following float array; Java packs the same order in EffectWorkshopDropsParameters. C clamps finite values, falls back per field for NaN/Inf, rounds counts, and orders each min/max pair. NULL/wrong-length core input restores defaults. Configuration survives reset/resize/EGL loss. Defaults keep recovered behavior; no resource topology or unlock timing changes. Settings affect new particles and growth/physics/optical export, and must be installed before first input/resize/step.

| Index | Key | Default | Range |
|---|---|---|---|
| 0 | `radius_scale` | 1 | 0.5..1.5 |
| 1 | `density_scale` | 1 | 0.5..1.5 |
| 2 | `viscosity_scale` | 1 | 0..2 |
| 3 | `pressure_scale` | 1 | 0..1.5 |
| 4 | `near_pressure_scale` | 1 | 0..1.5 |
| 5 | `growth_scale` | 1 | 0.5..1.5 |
| 6 | `edge_bounce` | 0.15 | 0..0.6 |
| 7 | `tilt_x_scale` | 1 | 0..2 |
| 8 | `tilt_y_scale` | 1 | 0..2 |
| 9 | `refraction_scale` | 1 | 0..2 |
| 10 | `shadow_width` | 0.6 | 0..1.2 |
| 11 | `saturation` | 1.3 | 0..2 |
| 12 | `brightness` | 1.3 | 0.5..2 |
| 13 | `minimum_value` | 0.15 | 0..0.5 |
