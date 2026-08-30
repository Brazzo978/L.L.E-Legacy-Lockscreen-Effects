# L.L.E 1.0.6.1

L.L.E 1.0.6.1 adds the configurable Random unlock mode and completes the final
Pixelate fidelity work from the 1.0.6 restoration release.

## Random unlock mode

- Added **Random** above the effect list, with a dedicated **EDIT POOL** mode.
- The initial pool includes every compatible low-cost effect available to the
  current build and wallpaper mode.
- Whole effect cards toggle pool membership; selected cards are highlighted and
  ordinary browsing remains uncluttered when pool editing is closed.
- Resource-heavy renderers remain available as explicit opt-ins. Enabling one
  requires two consecutive warnings; removing it remains immediate. The three
  Good Lock variants are included in this protected group.
- Random uses a shuffle bag: it exhausts the selected pool before refilling it
  and avoids an immediate repeat whenever at least two candidates are available.
- The selected renderer remains stable throughout one lock cycle, including AOD,
  Quick Settings, rotation and renderer recreation. The next renderer is chosen
  only after a confirmed unlock and is preloaded while the device is unlocked.
- Empty pools and renderer failures use S3 None for the affected cycle, then
  resume the selected Random pool after the next completed unlock.

## Refresh and Pixelate fidelity

- High frame rate mode now defaults to enabled for every renderer that supports
  it. An explicit user choice to disable HFR remains preserved.
- G2 Pixelate starts the SystemUI unlock handoff at its recovered 400 ms visual
  completion boundary while retaining the Last screen underlay through 1000 ms.
  This keeps the transition covered without delaying the unlock command.
- Restored G2 Pixelate's original `mosaic_touchdown`, `mosaic_unlock` and
  `mosaic_lock` audio from the authorized archive, mapped independently to touch,
  unlock and screen-lock events.

## Reliability

- Random preference changes rebuild only the selected renderer and retain the
  outgoing animation tail before preparing the next cycle.
- Rapid relocking commits a pending Random draw at screen-off so the previous
  candidate is not repeated while the normal post-unlock preload is still queued.
- Random renderer failure remains isolated to one cycle and does not replace the
  user's fixed-effect preference or corrupt the selected pool.

## Scope

- Version: `1.0.6.1` (`versionCode 43`).
- Package: `com.codex.lle64`.
- ABI: `arm64-v8a` only.
- The production APK is Samsung-free and does not ship archived applications or
  the original Pixelate APK; only the three authorized audio resources are
  included in the app-owned restoration.
- Recommended APK: `LLE64-1.0.6.1-64-bit.apk`.
