# Effects workshop

The Workshop card is at the bottom of the Effects settings page, after the
effect list and background controls. It opens an advanced editor for the currently selected
effect. It resolves the pending selection at click time, so switching effects
and immediately opening the workshop shows the correct schema.

Each row has numeric input, a slider, its original value and range, and a
**Reset default** icon (↺) beside the info button.
Rounded white parameter cards match the app's pale background and teal palette.
A compact editable value badge sits between minus/plus buttons, with the unit
alongside the value. Range endpoints, default and step share a compact footer.
Card spacing, typography and the editor header use a slimmer layout while
interactive controls retain 48 dp touch targets. Sliders use a teal track,
ring thumb and tick marks for small grids. Decimal arithmetic prevents binary
rounding artifacts; slider endpoints remain reachable for partial final steps.
Minus/plus change the current value by one schema step, preserving finer manual
input. Slider steps give throttled haptic feedback respecting system settings.
All labels, groups, units and help text are English. Each parameter has an info
button that opens a floating, scrollable explanation with its default and range.
Editing numeric input, a slider or an individual reset enables customization.
Opening the editor, Reset all and explicit disable do not automatically enable it.
The Apply button explicitly says Apply custom settings or Use original settings.
The editor keeps its actions in a fixed footer below the scrollable parameters:
a full-width primary Apply button, followed by spaced Cancel and Reset all
buttons. Rounded buttons provide at least 48 dp touch targets and ripple feedback.
Apply validates all visible rows and saves one versioned preference snapshot per
effect. Cancel and Back discard the draft. Reset all restores draft defaults and
disables customization; it only takes effect after Apply. Disabling custom
settings preserves saved values while restoring the original renderer behavior.
Slider motion does not write preferences. Decimal input accepts dots or commas.

Hula Hoop shows only V1 or V2 parameters for the selected variant. Light Particle
shows only the geometry parameters for its selected revision. Hidden settings
remain saved when the active variant is edited. Existing asset, palette,
background, HFR and input mode selectors retain their usual UI.

## Coverage

All 39 selectable app-owned effect IDs in the Samsung-free ARM64 product have
workshop schemas. Reserved/legacy vendor IDs 1, 4, 5, 6, 9 and 21 are excluded.
Samsung and Good Lock were implemented first, LG second, and Sony last.

| ID | Effect | Controls |
| ---: | --- | ---: |
| 0 | S4 Lens Flare | 29 |
| 2 | S5 Popping Colours | 20 |
| 3 | N3 Watercolor | 10 |
| 7 | N4 Abstract Tiles | 14 |
| 8 | N4 Geometric Mosaic | 61 |
| 10 | S3 Water Ripple | 8 |
| 11 | Tab S Blind | 14 |
| 12 | N2 Ink in Water | 15 |
| 13 | S5 Stone Skipping | 16 |
| 14 | S5 Brilliant Ring | 24 |
| 15 | Tab S Brilliant Cut | 16 |
| 16 | Seasonal Spring | 8 |
| 17 | Seasonal Summer | 8 |
| 18 | Seasonal Autumn | 8 |
| 19 | Seasonal Winter | 8 |
| 20 | Seasonal | 8 |
| 22 | N5 Sparkling Bubbles | 48 |
| 23 | N5 Colored Droplet | 12 |
| 24 | N5 Colored Droplet + Gyro | 14 |
| 25 | Mass Tension | 9 |
| 26 | S6 Water Droplet | 16 |
| 27 | N3 Ripple Ink | 16 |
| 28 | Good Lock Popping Color | 8 |
| 29 | Good Lock Rectangle Traveller | 9 |
| 30 | Good Lock Bouncing Color | 10 |
| 31 | S3 None | 13 |
| 32 | G2 Pixelate | 9 |
| 33 | G2 Particle | 24 |
| 34 | G2 Crystal | 20 |
| 35 | Xperia Z1 Blinds | 18 |
| 36 | Revolving Glass | 45 |
| 37 | G1 White Hole | 7 |
| 38 | G2 Soda | 27 |
| 39 | G1 Dewdrop | 6 |
| 40 | G2 Light Particle | 38 |
| 41 | G2 Vector | 10 |
| 42 | Hula Hoop | 41 |
| 43 | Circle Mosaic | 10 |
| 44 | Emoji Trail | 28 |

The controls connect to actual drawing, geometry, emission, simulation, shaders
or audio paths. Native effects receive validated per-instance settings through
JNI on their existing render/worker lifecycle. Colour Droplet without gyro
omits inactive tilt controls while packing the original tilt coefficients into
the common native layout. Watercolor's formerly empty setter now consumes the
exposed settings.

Each renderer captures an immutable snapshot during construction. Applying a
snapshot to the selected effect recreates the renderer through the service's
existing cancel/preload/park path. Other effects read their settings when
subsequently selected, including random-pool changes. Seasonal entries retain
independent configurations; the seasonal doodle partner uses its existing
behavior.

The preload path also compares the effective workshop snapshot against the
renderer snapshot, recovering from a missed preference callback. It defers
replacement during a gesture, PIN handoff or blocked system surface. Disabled
draft changes keep the original effective signature. Preference snapshots are
cached per SharedPreferences instance to avoid parsing on every visibility pass.
Runtime reports show whether customization is enabled and the current renderer
matches the saved configuration. The editor identifies a paused app or a
disconnected accessibility service before claiming visible changes.

## Bounds and compatibility

Descriptors reject invalid defaults, clamp finite values and round integer
controls. Corrupt fields use the original value; corrupt or future-format
snapshots fall back to original settings. Native setters also validate/clamp
their input. Configuration preserves final unlock, underlay hold and handoff
durations. Reversible hint, emission and animation phases are exposed where
independent of those final transitions.

Variable particle and cell counts allocate matching buffers and retain bounded
capacities. G2 Particle handles all emission counts at zero without accessing an
empty vertex buffer. Custom attraction and escape integration are bounded to
avoid runaway positions at high settings. Good Lock caps custom emission at
160 particles per event and 2048 live particles; seasonal particles at 256.
Lens hexagon counts are limited to 32. Coupled thresholds, lifetime phases,
inverse denominators and minimum geometry sizes retain valid relationships.

Fixed resource topology, mesh connectivity, solver grids, native memory layout
and scheduler constants are not editable. Named presets and import/export of
applied settings are not implemented in this menu. A generated parameter
catalog provides CSV, JSON and a searchable HTML list for inspection.

## Build and validation

`WorkshopSliderScaleTest` checks decimal increments, accumulated drift, negative
ranges, partial final steps, manual precision and sampled monotonic round trips
for all 705 controls (160,188 assertions).

The requested build is **Tester 1.0.7RC1**, versionCode **53**, package
`com.codex.lle64.test`, ABI `arm64-v8a`. Build with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-arm64.ps1 -Tester
```

The Tester builder sets this version in its generated manifest. The canonical
product manifest and stable package version are unchanged. GitHub tag
`v1.0.7RC1` is published as a Tester prerelease, explicitly excluded from Latest.
Neither Companion feed is updated: `LLE_VERSION.txt` continues to advertise the
stable `1.0.6.7`. Companion ignores the separate Tester package.

Validation includes the complete ARM64 Tester build, APK signature and manifest
checks, schema persistence/bounds tests, actual custom scene tests and existing
timing regressions. Disabled baseline checks compare original particle and
geometry output against configured renderers, including byte/bit identity where
deterministic. Native host suites cover fluid setters, numerical limits,
instance isolation and reset behavior; NDK syntax checks cover changed sources.
LG tests exercise custom mesh and family counts, buffer capacities, optical
math, Hula geometry and its actual soft-body solver. Sony tests exercise actual
fold/spring math and glass scene/shader configuration.

Host tests and APK compilation do not verify device GLSL/RuntimeShader
compilation, asset appearance, audio, touch response or physical lock/unlock.
These require the corresponding device flow. On the Tester, verify Apply,
Cancel, individual Reset, Reset all, variant changes and persistence; then try
each effect with original settings and noticeable custom values, including
random changes, sound and unlock completion.
