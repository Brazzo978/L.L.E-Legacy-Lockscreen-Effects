# Geometric Mosaic: visual reference audit

Date: 2026-07-16
Scope: original/legacy visual references only. This document does not treat the current ARM64 port as ground truth.

## Confidence legend

- **Observed**: directly visible in a preserved image or reproduced by the installed ARM32 native renderer.
- **Confirmed by native reverse**: timing/geometry read from the original ARM32 implementation, not estimated from a screenshot.
- **Inference**: plausible interpretation which still needs side-by-side video validation on original hardware.

## Sources and limitations

1. **Local legacy picker preview (strongest preserved visual frame)**
   `res/drawable-nodpi/preview_unlock_n4_geometric_mosaic_lle.png`, 1440 x 1440. This is the only local image that clearly shows the finished circle + triangular-mosaic composite.
2. **Local polygon wallpaper**
   `../unlock-effects-test/demo-apk/res/drawable-nodpi/s4_wp_geometric.jpg`, 1080 x 1920. It is useful for separating the base polygon image from the effect-generated cell/circle layers.
3. **Reddit recording**
   [Who still remembers this phone?](https://www.reddit.com/r/samsunggalaxy/comments/1l0gud6/who_still_remembers_this_phone/). Reddit identifies the device as Note 4 in the thread and one commenter explicitly identifies the shown unlock effect as Geometric Mosaic. The page currently returns "Sorry, something went wrong when loading this video"; Reddit JSON/CDN retrieval was also blocked by network security. Therefore no timeline measurements were taken from this video.
4. **Third-party A5 static reproduction (secondary evidence only)**
   [A5 Geometric Mosaic Locker](https://apkpure.net/a5-geometric-mosaic-locker/com.galaxytheme.geometricmosaic). Its store description claims an A5-identical effect and its APK contains the same legacy-named native components, but it is not a primary Samsung source. Its screenshot is used only to corroborate scale and placement, never timing.
5. **Installed ARM32 LLE renderer on S23**
   The native renderer loaded successfully at 1440 x 3088 (`libsecveGeometricMosaic.so`, effect 8, no crash). A timed `screencap` attempt was invalid for visual comparison: the first varying frames captured the AOD-to-lockscreen brightness transition, while the following set was black/unchanged because the device returned to doze/protected composition. These frames must not be used as visual ground truth. Accessibility was restored byte-for-byte to Bitwarden + `com.codex.lle.arm64dev` (`Ordinal ExactMatch=True`).

## Static composition: reliable observations

### Local interaction footprint

**Observed in the 1440 x 1440 picker preview:**

- The strongly affected local region is approximately `x=240..1080`, `y=260..1145`: about **58% of frame width** by **61% of frame height**. The edges are soft/irregular because the reveal is a coarse mask, not a hard rectangle.
- The visual center is slightly above the square frame center. It is a localized patch at touch time, not a permanent full-screen sheet.
- The secondary A5 reproduction corroborates this: its active patch occupies roughly half the 480 px screen width and about one third of its 800 px height, centered in the middle-lower lockscreen area. Treat those numbers as approximate because its screenshot uses a different aspect ratio and a third-party host.

### Circles

**Observed:**

- Circles are laid out on two interleaved/staggered lattices. They are not a dense uniform grid of independent dots.
- In the 1440 px preview, the most legible large rings are about **200-210 px diameter** (approximately 14% of frame width), with horizontal center pitch near **240 px** (approximately 16.7% of width).
- The inner disc is about **40% of the outer radius** in the clearest rings. The result reads as a small colored core plus one or two wider translucent annular bands.
- Several circles are intentionally clipped at the local mask boundary. Partial circles along the left/right/top/bottom edges are expected and should not be "fixed" by shrinking the lattice into the mask.
- Circle opacity is moderate and heavily composited with the wallpaper/mosaic. Opaque neon discs or strong white outlines are not faithful.

**Confirmed by native reverse:**

- Lattice A centers are generated from five x positions by six y positions; lattice B from four x positions by seven y positions. The two grids are offset by half a cell.
- The final pass samples the circle render targets with repeated coordinates, so the apparent on-screen pattern is the composition of the two staggered meshes and the mask. A single analytic screen-space circle array cannot match all clipped/repeated placements.

### Triangles / mosaic cells

**Observed:**

- The reveal is dominated by large triangular facets, not blur blobs and not rectangular pixelation.
- Triangle edges remain straight and comparatively crisp even though their colors are softly blended. The triangle field is visually tied to the underlying wallpaper colors.
- Cell brightness varies locally; there are dark teal facets beside cyan/blue/green facets. The effect is not a uniform brightness lift.
- The strongest triangular pattern is inside the touch patch, but subtle tinted geometry may extend under translucent circle edges.

**Confirmed by native reverse:**

- The final mosaic quantization is based on a portrait **12 x 21** block grid (21 x 12 in landscape), with a row-dependent horizontal shift. This is why rows form alternating diagonal triangles instead of a rectangular checkerboard.

### Color and transparency

**Observed:**

- On the teal reference wallpaper, the dominant output remains teal/cyan/blue. Small regions introduce green, pale cyan, gray and occasional purple; saturated palette colors are strongly moderated by the blend chain.
- Lockscreen content remains readable over/around the effect in the third-party static reproduction. There is no evidence for an opaque black backing layer.
- Outside the local interaction mask the effect must be transparent in the LLE overlay. Passing the full screenshot at uniform alpha is visibly wrong even when the colors inside the patch are plausible.
- Inside the patch the wallpaper is refracted/recolored through mosaic facets and circle layers; it should not look like a flat translucent white blur.

## Motion and chronology

No externally recovered video was playable, so the following timings are **confirmed by native reverse, not measured visually**.

### Tap / initial pulse

- A new touch record starts at radius **0.3**, grows to **0.8 in 150 ms**, then contracts toward **0.3 over 600 ms**.
- The first clearly visible local patch should therefore appear quickly, grow for roughly the first 150 ms, then soften/contract. A renderer that is still completely static at 150-300 ms is not faithful.
- Five independent ring tracks run with different spans: approximately **1.2 s**, **2.4 s**, **3.0 s** and **3.6 s**, including one track delayed by **600 ms**. Their radii are sorted before the two circle passes. Consequently rings must cross/reorder smoothly; all rings expanding in lockstep is incorrect.

### Drag

- New touch records are emitted once movement exceeds about **0.0085 of normalized screen extent**. The drag should leave a sequence of overlapping local patches/rings, not move a single fixed spotlight.
- Each sample follows the same short grow-then-contract envelope. The trail is therefore denser near the finger and decays behind it.
- The direction must follow the finger in screen coordinates. Any mirrored/inverted path is incorrect.

### Unlock

- On unlock, each active touch radius expands from its current value to **5.0 in 450 ms**.
- Scene alpha fades from **1.0 to 0.0 over 600 ms**.
- Therefore near/full-screen mosaic coverage during unlock is **intentional**, provided it expands and disappears during the same approximately 600 ms interval. A full-screen field that persists, pops on instantly, or fades only after expansion is not faithful.
- This is the most important interpretation for the ARM64 comparison: local confinement applies to tap/drag; unlock deliberately turns the local patch into a global transition.

## What a faithful ARM64 frame sequence should show

1. **Idle:** no generated mosaic sheet; unchanged screenshot outside all masks.
2. **~40-100 ms after tap:** small, localized coarse triangular reveal around the touch; circles may still be subtle.
3. **~150-300 ms:** patch near maximum local size; staggered clipped rings and triangular cell variation visible together.
4. **~500-800 ms:** local mask contracts/fades while slower ring tracks continue; not a frozen frame and not a global brightness-only transition.
5. **Drag:** overlapping discrete patches following the actual path, with older samples visibly weaker.
6. **Unlock 0-450 ms:** rapid expansion toward near/full-screen coverage while alpha is already falling.
7. **Unlock by ~600 ms:** composite gone; no residual opaque screenshot, black region, or stuck circle texture.

## Open visual questions for original-device side-by-side

- Exact easing curves (linear vs framework interpolator) are not provable from the unavailable video.
- Exact random color assignment per circle/cell varies by run; compare palette distribution and blend strength rather than exact cell-to-cell color identity.
- The legacy picker preview cannot prove z-order relative to every SystemUI element.
- The special idle/unlock-affordance behavior around the native `-0.8` scene value remains unverified visually.
- A real original-device 60 fps recording is still needed to score ring ordering, drag sample density and the final 450-600 ms unlock fade frame by frame.

## Rejected evidence

- `C:\Users\Admin\AppData\Local\Temp\gm-original-frames2\tap-*.png`: mixed with the S23 AOD-to-lockscreen brightness transition; not valid for effect geometry.
- `C:\Users\Admin\AppData\Local\Temp\gm-original-frames3\*.png`: black/unchanged protected/doze captures; no effect evidence.
- Current ARM64 screenshots: implementation-under-test, never a reference for its own fidelity.
