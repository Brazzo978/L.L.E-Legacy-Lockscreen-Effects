# Stone Skipping firmware-origin audit

Date: 2026-07-20

## Conclusion

The oldest verified official firmware in the recovered corpus that contains Samsung's Stone Skipping renderer is the Galaxy S5 launch-branch build `G900AUCU1ANCE`, internally compiled on 2014-03-14. LLE therefore classifies the effect as **S5 Stone Skipping**.

The renderer is `MassRippleUnlockTwin`, backed by `MassRippleImageView`. Its lockscreen resource name is `simple_ripple`, with the public label `Stone skipping`.

## Binary boundary

| Device and firmware | Android | Internal build date | `MassRippleUnlockTwin` | Relevant finding |
|---|---:|---:|:---:|---|
| Note II `N7100UBALI9` | 4.1.1 | 2012-09-22 | No | Stock `CircleUnlockRippleRenderer` only |
| Note II `N7100UBDMA3` | 4.1.2 | 2013-01-25 | No | Adds the `inkeffect` block; this is Ripple Ink, not Stone Skipping |
| Note II `N7100UBUEMK4` | 4.3 | 2013-11-26 | No | Ripple, Ink, and Watercolor families present |
| Galaxy S4 `I9505XXUFNC4` | 4.4.2 | 2014-03-08 | No | No Mass Ripple classes or `simple_ripple` resource |
| **Galaxy S5 `G900AUCU1ANCE`** | **4.4.2** | **2014-03-14** | **Yes** | Full Mass Ripple classes and `simple_ripple = "Stone skipping"` |
| Note II `N7100XXUFND4` | 4.4.2 | 2014-04-18 | Yes | First positive Note II build; later KitKat backport |

Additional negative checks:

- Galaxy S3 `I9300XXUGOL2` contains the Ripple/Ink/Watercolor families but no Mass Ripple class or Stone Skipping resource.
- Galaxy Note 3 `N9008VZMUCNK1` Android 4.4.2 contains no Mass Ripple or Mass Tension class. The later Note 3 Lollipop `DQD2` copy is not origin evidence.

The audit establishes the earliest verified production boundary in the available firmware set. It does not claim that no unreleased Samsung engineering build ever contained an earlier prototype.

## Preserved evidence

- `unlock-effects-test/S3/firmware-origin-analysis/I9300_GOL2`
- `unlock-effects-test/S4/firmware-origin-analysis/I9505_FNC4`
- `unlock-effects-test/S5/firmware-origin-analysis/G900A_ANCE`
- `unlock-effects-test/note2/firmware-origin-analysis/N7100_UBALI9`
- `unlock-effects-test/note2/firmware-origin-analysis/N7100_UBDMA3`
- `unlock-effects-test/note2/firmware-origin-analysis/N7100_UBUEMK4`
- `unlock-effects-test/note2/firmware-origin-analysis/N7100_XXUFND4`

The analysis directories preserve extracted system images and selected `odex`/resource/build-property evidence. Source AP payloads remain in their device folders, and the original SamFW downloads were also retained. No source archive was deleted.

## LLE UI decision

The stable preference ID remains `OverlayPrefs.EFFECT_STONE_SKIPPING = 13`. Only its historical classification and picker placement change: the UI label is `S5 Stone Skipping`, positioned directly after `S5 Popping Colours`. This preserves all existing user selections and both ABI implementations.
