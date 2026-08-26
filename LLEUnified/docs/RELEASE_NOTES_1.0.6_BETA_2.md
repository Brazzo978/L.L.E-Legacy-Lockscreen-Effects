# L.L.E 1.0.6 Beta 2

This co-installable ARM64 tester is the Lens Flare fidelity and reliability patch
for the 1.0.6 restoration beta.

## Lens Flare fidelity

- Lightning now uses the archived fourth Lens Flare texture family and the same
  recovered Samsung choreography as Original, Blue Ring and Blood. The unrelated
  procedural bolt generator has been removed.
- Blue Ring and Blood retain Erik's original variant artwork, with the source APK's
  xhdpi/xxhdpi density relationship restored. This fixes the oversized rings,
  particles and drag trail without changing the calibrated Original asset scale.
- The recovered seven-hexagon touch burst, `0.3..1.1` random scale range and drag
  hexagon size offset are shared by the Canvas and GLES renderers.
- Lightning has its own picker preview and no longer forces the experimental GLES
  renderer.

## Lens Flare reliability

- Canvas/HWUI is now the default stable renderer for every Lens Flare variant.
- The old default-true GLES preference is retired on upgrade, preventing an existing
  installation from carrying the failure-prone renderer into this beta.
- GLES remains available as an explicit diagnostic A/B option and still switches to
  Canvas automatically after a reported GL initialization, resize or draw failure.

## Restoration beta

- S3 None.
- G1 White Hole.
- G2 Soda.
- G1 Dewdrop.
- G2 Particle.
- G2 Light Particle.
- G2 Crystal.

### G2 Crystal fidelity

- Replaced the temporary procedural circle with the recovered ten-sector crystal
  mesh, per-facet normals, two-light specular shader and refracted last-screen
  sampling used by the archived effect.
- Restored the original main, shadow and dual lighting artwork as separate passes
  around the touch point.
- Restored the 50/201 px drag geometry, 300 ms incomplete-release retraction and
  400 ms completed-release expansion, including the original unlock sound.
- Kept the modern L.L.E. last-screen cache and transparent lockscreen carrier;
  the obsolete root screenshot mechanism and donor APK code are not shipped.

The remaining clean-room tests stay visibly labelled WIP in the picker.

## Physical-device validation

- Samsung Galaxy S23 Ultra (`SM-S918B`), Android 16 / API 36.
- Canvas renderer: 20/20 verified sleep/wake/reattach cycles while rotating
  Original, Blue Ring, Blood and Lightning. Every cycle reached both debug gesture
  begin and end; no ignored gesture, renderer fallback, OOM, process restart or
  fatal exception was observed.
- GLES diagnostic A/B: 4/4 verified sleep/wake/reattach cycles across Original
  and Lightning, with the EGL surface, textures and first frame reaching ready on
  every cycle and no automatic fallback.
- Regression smoke: LG G1 White Hole and LG Soda each completed a debug gesture on
  the same locked device without fallback or process restart.
- `gfxinfo` over the stress session reported 8,525 rendered frames, 2.89% janky
  frames, a 9 ms 90th percentile and no slow bitmap uploads.

## Package

- Version: `1.0.6.B2` (`versionCode 41`).
- Package: `com.codex.lle64.test`.
- ABI: `arm64-v8a` only.
- Samsung-free; no legacy vendor native binary payload.
