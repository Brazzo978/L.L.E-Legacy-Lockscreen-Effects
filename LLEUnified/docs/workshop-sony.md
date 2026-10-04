# Sony workshop parameters

The workshop reads one immutable preference snapshot for Xperia Z1 Blinds (35)
and Revolving Glass (36) before constructing runtime scene/render state.
`EffectWorkshopSonyParameters` is the descriptor and range authority.

Blinds exposes 18 controls: strip count (5–40), normalized affected window,
fold amplitude, horizontal/camera rotation and camera depth, shade strength,
shadow length/threshold/alpha, seam width/threshold/opacity, strip opacity,
spring stiffness/damping, hint press duration and sound volume. Every changed
strip count creates its own alpha array and drives all band loops and sound-band
selection. The analytic runtime spring in `XperiaBlindsDynamics` retains the
stock clamp at 50 ms and permits damping only 0.05–0.99, avoiding the zero
frequency denominator at critical damping. Unlock strip fade, global exit fade
and the 200 ms completion constant remain fixed.

Revolving Glass exposes 45 controls: card scale/position/depth/corners, ordinary
drag sensitivity/speed/slop/maximum angle and initial rotation, cancel speed and
attenuation, entry/hint motion, final tile scale, perspective, face/edge lighting,
border shape/width/strength, frosted blur, separate face/edge tints, glint
frequency/rotation/power, edge/tile/underlay opacity and sound volume.
`RevolvingGlassOptics` generates the actual shaders passed to program creation.
With custom settings disabled it returns the original shader strings unchanged.
The geometry takes its original constants directly in that mode. Configured
card bounds and depth give maximum rotated z below 1.168; camera distance starts
at 2, keeping the perspective denominator positive. The final unlock fast/slow
steps, tick, tail, exit duration and 1200 ms underlay hold remain fixed.

Neither effect contains Fold Morph or a variant switch. Assets and independent
primary/Last screen texture handling remain unchanged. These effects consume no
random sequence in the changed geometry/motion paths.

Run focused verification after an ARM64 Java build exists:

```powershell
powershell -ExecutionPolicy Bypass -File .\LLEUnified\ports\sony-workshop\tests\run-host-tests.ps1
```

The runner recompiles current Sony source and all descriptor providers against
Android 35, then runs the existing Blinds and Glass scene tests and the new
`SonyWorkshopTest`. That test checks stock spring/wave bit identity, bounded
strip mapping and spring extrema, actual custom scene behavior and unchanged
handoff hold, plus shader substitutions using the renderer's real shader source.
It does not create a GLES context. Runtime shader compilation and visual QA must
still be checked on a device; host Java compilation and text checks do not prove
GPU rendering.
