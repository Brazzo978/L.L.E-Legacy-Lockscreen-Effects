# L.L.E 1.0.6.1 TODO

Updated: 2026-08-28

## Random effect mode with selectable pool

- [ ] Add a **Random** picker entry and a configuration page containing a
  selectable pool of effects.
- [ ] Resolve one compatible effect once per lock cycle and keep it latched until
  that cycle ends. QS, AOD, rotation and renderer recreation must not reroll it.
- [ ] Filter the pool through `EffectAvailability`, build/ABI support and current
  no-colormap compatibility before choosing.
- [ ] Migrate legacy aliases to their effective app-owned IDs before storing or
  resolving the pool.
- [ ] If the filtered pool is empty, fall back to S3 None in no-colormap mode;
  otherwise use the normal safe fallback.
- [ ] Avoid immediate repetition when at least two compatible effects remain.
- [ ] Log only selected effect ID, candidate count and fallback reason; do not
  expose package names, wallpaper content or user input.
