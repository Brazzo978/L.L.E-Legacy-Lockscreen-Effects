# Xperia Z1 Blinds staged replacement manifest

| File | Role | Origin / status |
| --- | --- | --- |
| `XperiaBlindsEffectView.java` | Drop-in replacement, package `com.codex.lle` | Clean-room Canvas implementation from the audited normal donor behaviour. |
| `XperiaBlindsEffectViewTest.java` | Host-only scalar regression checks | New staged test; validates donor constants, affected-band bounds, band mapping, wave equation and spring stability. |
| `AUDIT.md` | Oracle evidence | Records APK/resource hashes, renderer formulas, lifecycle and limits. |

No resource/shader is staged: the donor's normal visual path is Canvas/Camera only. Its raw audio is documented in `AUDIT.md`; it is not copied because this replacement has no audio API and LLE routes lock audio through the central sound layer.

## Integration

1. Review/copy `XperiaBlindsEffectView.java` over `src/com/codex/lle/XperiaBlindsEffectView.java`.
2. Review/copy `XperiaBlindsEffectViewTest.java` over `tests/com/codex/lle/XperiaBlindsEffectViewTest.java`.
3. Compile the normal ARM64 tester. Do not modify the picker, prefs, service wiring, lock sounds, or assets as part of this staged port.
4. On-device, supply a screen-sized opaque synthetic background, exercise press/move/cancel/unlock twice, and compare: five affected bands around Y, 3-degree folds, upper-edge shadow/seams, and the 40–200 ms exit fade.

## Difference from the WIP baseline

- Keeps **17** bands and an exact 5-band affected window, rather than rendering a simplified local fold approximation.
- Ports donor colour-filter, top-edge gradient shadow, individual seam, and staggered per-band exit fade behaviour.
- Replaces centre-crop background preparation with whole-image mapping to eliminate source zoom/crop.
- Preserves LLE renderer/readiness/affordance/destroy interfaces and avoids touching the shared working tree.
