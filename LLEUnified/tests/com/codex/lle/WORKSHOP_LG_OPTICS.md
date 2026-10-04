# LG optics workshop coverage

Each View takes an immutable preference snapshot during construction. Parameter
changes are applied by replacing the effect instance. Disabled snapshots return
the recovered defaults. Terminal unlock, underlay hold and overlay handoff
clocks are unchanged; dimensions are bounded continuous inputs, not resource or
mesh topology switches.

| Effect | Controls | Actual consumers |
|---|---:|---|
| White Hole 37 | 7 | Gesture radius/threshold, CPU warp and RuntimeShader absorption/edge strengths, band width, corona geometry/rotation |
| Soda 38 | 27 | Three bounded emission counts, reveal/glow/cutout/halo geometry, center cycle/delay, rising hold and derived cycle, family sizes/alpha, angle/spread, speed ranges, flight distance, cancel drift and unlock radial impulse |
| Dewdrop 39 | 6 | Gesture radius/threshold; Snell mapping in RuntimeShader and CPU fallback; ellipse aspect/height cap; archival overlay diameter |
| Hula Hoop 42 V1 | 26 | Radius/threshold, layer scale, pivot and trail, four independent trail factors and angles, rotation periods, intro/overshoot, reflection size/alpha/rotation, outer ring geometry/rotation |
| Hula Hoop 42 V2 | 15 | Radius/stride, stretch threshold/delay/cap, actual spring-damping solver and Hermite shape, colored ring/pivot, rotation, angular offsets, alpha and stretch variance |

Hula's union schema stores all 41 controls; the UI filters `v1_` / `v2_` keys
according to the existing revision preference. The inactive revision's settings
are retained, without advertising inactive controls.

Soda center/rising/column emission limits are 16 per texture, 14 per texture and
20 per column. They fill the existing dynamic particle list, for at most 230
particles including the ten fixed large disks/rings. Archived lifetime literals
that are subsequently replaced by distance/speed calculations are not exposed:
the rising-cycle control modifies the calculated cycle actually consumed.
Soda textures retain their original non-rotating orientation; the angular
control changes emission location instead.

White Hole and Dewdrop fallback band counts, Hula's 100-segment topology,
texture resources and final lifecycle clocks remain fixed. No palette picker
or per-channel coloring was added. Shader uniform names were extended locally
for actual optical coefficients; no native ABI is involved.

Portable checks: `LgWhiteHoleWarpTest`, `LgHulaHoopSceneTest`,
`LgHulaHoopFluidicSceneTest`, and `EffectWorkshopLgOpticsTest`, compiled together
with EffectWorkshopConfig and its parameter providers. The custom suite uses
the same pure optical functions and SoftBody implementation consumed by the
Views. It checks custom Snell/ellipse/overlay output, particle cycle/size/alpha,
Hula geometry and mesh response, finite geometry at parameter corners and
unchanged unlock/handoff clocks. Android-focused javac also checks the modified
Views against android-35 and the previously built app classes.

The host tests do not exercise RuntimeShader compilation on a device, Canvas
compositing, asset appearance or physical touch/sensor input. Those require
device validation.
