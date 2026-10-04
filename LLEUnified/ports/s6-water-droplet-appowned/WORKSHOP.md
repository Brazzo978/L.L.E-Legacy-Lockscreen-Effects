# Workshop tuning

Physics scales retain the recovered phone/tablet and growth-phase branches.
Radius controls the grown SPH smoothing radius; density/pressure/viscosity are
internal model coefficients. Tilt acts on the existing accelerometer path.
Optics use existing shader uniforms, including shadow, refraction, density
threshold and edge width. Saturation/brightness controls are absent because the
stock S6 composite has no such uniforms; adding those requires a shader change.

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
| 10 | `density_threshold` | 0.5 | 0.25..0.8 |
| 11 | `edge_offset` | 0.075 | 0..0.15 |
| 12 | `shadow_offset` | 0.15 | 0..0.3 |
| 13 | `shadow_range` | 12 | 0..24 |
| 14 | `refraction_eta` | 0.75001875 | 0.5..1 |
| 15 | `refraction_amplitude` | 0.075 | 0..0.15 |
