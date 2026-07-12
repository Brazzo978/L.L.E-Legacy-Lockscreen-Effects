# N4 Abstract Tiles native audit

Date: 2026-07-10

## Provenance

- LLE uses Samsung's original `libsecveAbstractTile.so` from the Note 4 SM-N910F BOB4 firmware (`LRX22C.N910FXXU1BOB4`, Android 5.0.1).
- Original SHA-256: `F8E8BDF48D069F76AF9923D68474A7047C621DD763D3E6D96C4F940025643840`.
- LLE staged patched SHA-256: `B33F8421CC4FF0B442761AAA8E92EE0471BFF26AA9ED0D42A201403151185C4B`.
- `build.ps1` regenerates the staged library from the verified original on every build through `vendor/native-patches/patch-abstract-tile-transparent.ps1`; it no longer relies on accumulating edits in the packaged source copy.
- The native file has four intentional transparent-overlay translations:
  - the fullscreen Samsung wallpaper pass (`Background::renderFrame`) is skipped;
  - the final additive tile shader premultiplies `(wallpaper RGB + bri)` by Samsung's native tile alpha;
  - the additive scatter shader writes transparent alpha instead of opaque alpha.
  - the line pass that writes absolute wallpaper RGB is discarded because it depends on the omitted opaque wallpaper pass.

The tiles, shader math, timings and native state machine remain Samsung's implementation. Skipping the opaque wallpaper and dependent line passes is required so LLE can composite over the real lockscreen instead of repainting it.

## Regressions found and corrected

- Restored the exact Samsung line-mask texture (SHA-256 `523B2345EF2DFDC11D6DEDAF4B9EB818F1E0CB96D4A95E18BA3BC527C36B699B`).
- The stock line pass cannot be composited directly as a transparent overlay: its shader writes absolute wallpaper RGB and relies on Samsung's opaque fullscreen wallpaper pass underneath. Without that opaque pass it exposes large background-coloured strips across the lockscreen. LLE keeps the authentic asset but discards this incompatible pass in the native shader; the original tile animation remains active.
- Restored full-strength screenshot colour sampling. The previous `0.25` RGB gain was not present in Samsung and made the effect unnaturally dark/weak.
- Restored `showUnlockAffordance`: custom event `1` with Samsung's `StartDelay` and `Rect` parameters. LLE schedules it 500 ms after screen-on.
- Removed idle parking/reset while the lockscreen is visible. The original native renderer finishes its animation and switches itself to dirty/on-demand rendering, so keeping the transparent Surface attached avoids a cold first touch without continuously rendering.
- Added a transparent final-frame clear when the native renderer enters dirty/on-demand mode. Samsung's opaque wallpaper pass normally covered the last animation buffer; without it, a partial swipe could leave several finished tiles frozen until the next render.
- Restored the original drag-sound behaviour: it starts as a loop after a 411 ms held MOVE and fades every 10 ms (`0.039` on release, `0.059` on unlock). Playback follows the system lockscreen-sound setting and system-stream volume.
- Removed the unnecessary per-pixel colour-matrix pass and added a fast exact-size bitmap copy path.
- Shared the in-memory lockscreen colour-map cache between the effects that consume the same captured image.
- 2026-07-12 compositing correction: Ghidra mapping confirmed draw order `Background -> Scatter -> Line -> Tile`. The final Tile renderer explicitly uses `glBlendFunc(GL_ONE, GL_ONE)`, so its old straight RGB was invalid on Android's premultiplied transparent `TextureView` and caused the bright/glowing result. Only that shader now writes `(texture.rgb + bri) * alpha`; native geometry, `bri`, alpha, additive overlap, timing and blend order remain original.
- 2026-07-12 runtime correction: a new renderer no longer invalidates and uploads the same `1080x2316` background again on attach; duplicate affordance commands inside 2.5 seconds are suppressed; the affordance dispatch runs off the main thread before Samsung queues its GL work; repeated hidden-overlay polling logs only on the actual state transition.
- 2026-07-12 stock S4 comparison: the live GT-I9505 renderer measured `29.94-32.98 fps`; its hint ran for `1.721 s` and a gesture for `1.412 s` from DOWN to dirty. The simulation is frame-stepped, so LLE now paces only `AbstractTileRenderer.onDrawFrame()` to `33.333 ms`. This preserves stock timing on 60/90/120 Hz displays without changing the display refresh rate or pacing other Samsung effects.

## Phone verification

Tested on the connected device with effect 7 selected:

- warm screen-on resumes the native Surface before the hint;
- exactly one hint command is accepted per wake;
- native hint starts after the requested delay, renders for about 1.7 s, then enters `dirty mode` naturally;
- no service-side reset interrupts the hint;
- first touch background sync: `0 ms`;
- first touch native begin: `18 ms` in the measured ADB run;
- held drag sound starts after the original long-press threshold;
- no Java/native crash was recorded.

2026-07-12 follow-up verification on SM-S918B:

- one background copy/upload instead of two;
- one affordance command and the later screen-on duplicate was rejected;
- no LLE `Skipped frames` report during the wake hint after off-main dispatch;
- a complete swipe reached unlock/clear/dirty normally and produced no Java, EGL or native crash;
- the installed APK contains exactly one premultiplied tile shader and the expected transparent Background NOP. Visual S4 parity and the former faint-X residue still require user/device comparison.

## Current status: not 100% complete

The serious corruption, renderer reset, frozen final-frame, duplicate-startup, incorrect 120 Hz simulation speed and known overbright compositing paths are fixed. The earlier build could leave a faint X made from semi-visible triangles after some partial swipes; the new premultiplied tile shader may also correct that additive residue, but this still needs phone/S4 visual validation before Abstract Tiles is called perfect 1:1 parity.

## Fidelity boundary

The active tile animation and assets are the original Note 4 implementation. The visual architecture difference is transparent composition: Samsung SystemUI rendered its own wallpaper as an opaque fullscreen base, while LLE omits that pass and the dependent absolute-RGB line pass, then draws the native animated tiles over the already visible lockscreen.
