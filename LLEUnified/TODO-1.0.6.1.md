# L.L.E 1.0.6.1 TODO

Updated: 2026-08-30

## Random effect mode with selectable pool

- [x] Add a **Random** picker entry above all effects and an explicit **EDIT POOL**
  mode where whole cards toggle membership. The initial pool contains every eligible
  low-cost effect. Heavy renderers stay opt-in and require two consecutive resource-use
  confirmations before they can join the pool; removing them remains immediate.
- [x] Resolve one compatible effect once per lock cycle and keep it latched until
  that cycle ends. QS, AOD, rotation and renderer recreation must not reroll it.
- [x] Filter the pool through `EffectAvailability`, build/ABI support and current
  no-colormap compatibility before choosing.
- [x] Preserve effective app-owned IDs when storing and resolving the pool. The
  remaining legacy Ink in Water alias already shares its effective numeric ID.
- [x] If the filtered pool is empty or a selected renderer fails, use S3 None for
  that cycle and resume the shuffle after the next completed unlock.
- [x] Avoid immediate repetition when at least two compatible effects remain, and
  exhaust the shuffle bag before refilling it.
- [x] Advance only after `ACTION_USER_PRESENT`, preserve the outgoing animation tail,
  then preload and park the next renderer while the device is unlocked. Stable unlocked
  keyguard state is the secondary confirmation when Samsung omits the broadcast; a rapid
  relock commits the pending draw at `SCREEN_OFF` instead of repeating the old candidate.
- [x] Log only selected effect ID, candidate count and fallback reason; do not
  expose package names, wallpaper content or user input.
