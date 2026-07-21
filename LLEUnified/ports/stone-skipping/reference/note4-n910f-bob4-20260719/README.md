# Stone Skipping stock reference: Galaxy Note 4 BOB4

Captured over ADB on 2026-07-19 without changing device settings or files.

## Device

- Model: `SM-N910F`
- Device: `trlte`
- Build: `LRX22C.N910FXXU1BOB4`
- Android: `5.0.1`
- Display: `1440x2560`, density `640`
- Active lockscreen setting: `lockscreen_ripple_effect=7` (`Stone Skipping`)

The device's stock `MassRippleUnlockTwin` smali is SHA-256
`B25660E918C45FDD195DDA229E7D1CEE007A3DAA0491D1235E709E3E125568D1`,
identical to the class bundled by LSE.

## Captures

- `captures/screen-on-affordance.mp4`: display wake and the stock concentric twin hint.
- `captures/controlled-swipe.mp4`: ADB swipe from `(200,2000)` to `(1200,1000)` over 1600 ms.
- Matching contact sheets provide a quick 200/250 ms frame overview.

The captures confirm:

- transparent composition over the untouched lockscreen;
- immediate first screen-on ring and the second ring after about 400 ms;
- 1300 ms decelerate-quadratic scale/fade;
- moving rings triggered at the recovered 0.45 distance-ratio increments;
- alternating moving-ring stroke weights.

## Runtime audio

The stock class checks `/system/media/audio/ui` before its packaged fallback.
This Note 4 therefore plays the following system files:

| File | Format | Duration | SHA-256 |
|---|---|---:|---|
| `simple_ripple_down_stock-44100.ogg` | 44.1 kHz, stereo | 0.68050 s | `AD1667363A2E6E753EA002FC5987FA63EB8E07A6853648BBA9C8835307B46107` |
| `simple_ripple_up_stock-48000.ogg` | 48 kHz, mono | 1.01125 s | `E40474452A9033B588ECB9801BDD34168B0D5CEA51F4CB31BDFE3F9CF00445FB` |

`simple_ripple_up_lse-fallback-44100.ogg` preserves LSE/SystemUI's fallback
encoding (44.1 kHz mono, 1.01127 s, SHA-256
`A461504E5637CFDD5E162A9615E1FC546AB794B6984563403F18673B8395B3B5`).
The port uses the real BOB4 runtime variant.
