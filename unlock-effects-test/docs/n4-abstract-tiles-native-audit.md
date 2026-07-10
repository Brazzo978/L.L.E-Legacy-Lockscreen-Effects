# N4 Abstract Tiles native audit

Date: 2026-07-10

## Provenance

- LLE uses Samsung's original `libsecveAbstractTile.so` from the Note 4 SM-N910F BOB4 firmware (`LRX22C.N910FXXU1BOB4`, Android 5.0.1).
- Original SHA-256: `F8E8BDF48D069F76AF9923D68474A7047C621DD763D3E6D96C4F940025643840`.
- LLE patched SHA-256: `191B5BF939B050AA1F9ECA1DF029B15901EBD040A2877E6C618E54371489421F`.
- The native file differs only by three intentional transparent-overlay patches:
  - the fullscreen Samsung wallpaper pass (`Background::renderFrame`) is skipped;
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

## Current status: not 100% complete

The serious corruption, renderer reset and frozen final-frame problems are fixed. A minor transparent-compositing artifact remains: after some partial swipes, a faint X made from a few semi-visible triangles can persist temporarily. The renderer remains alive and subsequent gestures start immediately, but this residue means Abstract Tiles must not yet be considered perfect 1:1 parity.

## Fidelity boundary

The active tile animation and assets are the original Note 4 implementation. The visual architecture difference is transparent composition: Samsung SystemUI rendered its own wallpaper as an opaque fullscreen base, while LLE omits that pass and the dependent absolute-RGB line pass, then draws the native animated tiles over the already visible lockscreen.
