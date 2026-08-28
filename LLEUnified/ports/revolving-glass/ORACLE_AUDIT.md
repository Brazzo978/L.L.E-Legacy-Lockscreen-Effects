# Revolving Glass oracle audit

Source inspected: archived `com.othlocks.oneplus.glass-v1.1.apk`, SHA-256
`27F4111DAE4AFF7E3D5FACB2B279AF470CB3256D33B6E4493B9081BC30376292`.

## Verified rendering contract

- The donor renders a thin five-group MilkShape box: translucent sides, gray back, and three
  vertically stacked front groups.
- At first touch it snapshots the composed lockscreen, slices it into three equal horizontal
  bands, and uploads the bands to the three front groups.
- A separate wallpaper view remains full-screen behind the rotating box. L.L.E maps this role to
  the independent `Last screen` cache, while its normal lockscreen cache is mapped only to the
  rotating front bands.
- The front uses the complete source rectangle. There is no center crop or gesture-driven source
  zoom.
- The clean-room port encodes the verified primitive geometry directly. It does not package the
  donor model, APK, Java/native code, or its ARM32 libraries.

## Verified interaction constants

- Initial angle: `(touchX - width / 2) * 0.02` degrees.
- Initial sink: 1.8 degrees every 10 ms.
- Drag starts after 20 px and uses `0.44 * (x - downX)` degrees.
- Unlock completes at plus or minus 180 degrees, stepping 6 degrees every 12 ms for the first
  seven eighths and 4 degrees every 12 ms for the tail.
- Cancel alternates around zero every 10 ms with amplitude-dependent speeds and attenuation.
- The unlock completion callback follows the donor unlock sound by 200 ms. L.L.E hides the
  rotating tile 100 ms after its turn completes, requests PIN entry at 660 ms, and retains only
  the independent `Last screen` underlay through 1000 ms to cover the SystemUI state handoff.

## Packaged donor media

The project authorization covers the two original short sounds copied as dedicated resources:

- `revolving_glass_lock.ogg`: SHA-256
  `6B98F1FB43D72813F7C57A57B255D3DE51ED78311901E2FF2636F6F75911D7D4`
- `revolving_glass_unlock.ogg`: SHA-256
  `D386103AD3269B1087E357541DE34667A8F965D55EA52A647C0ED7ECFD91DBCF`
