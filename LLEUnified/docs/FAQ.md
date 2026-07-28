# L.L.E. FAQ

## I can see the effect, but I cannot hear its sound

L.L.E. respects Android's lockscreen sound policy. If the system lock/unlock
sound is disabled, L.L.E. does not bypass it and effect audio is muted too.

On Samsung devices:

1. Open **Settings**.
2. Open **Sounds and vibration**.
3. Open **System sound**.
4. Turn on **Screen lock/unlock**.
5. Make sure **System sound volume** is above zero.
6. Use the normal **Sound** profile, not Silent or Vibrate.
7. In L.L.E., keep **Effect sounds** enabled.

After changing the setting, lock and wake the device once before testing the
effect again.

On other manufacturers, look for the equivalent **Screen locking sound**,
**Lock/unlock sound**, or **System sounds** setting. The wording and location
can differ by vendor.

The L.L.E. **Effect sounds** switch controls touch and unlock-effect audio.
**Lock effect sound** controls the separate sound played when the screen is
locked. Both still follow the device's system lockscreen-sound policy.

If the effect is still silent, use **Create debug report** in L.L.E. and attach
the generated text file to the bug report. Starting with version 1.0.4.4, the
report records the ringer mode, System volume, lockscreen-sound switch, L.L.E.
audio switches, and the reason playback was allowed or suppressed.

## The effect shows the previous or a mismatched lockscreen wallpaper

On Samsung devices, rerun the L.L.E. setup wizard and review **Samsung wallpaper
compatibility**. Disable both **Dynamic Lock Screen** and Dark mode wallpaper
dimming when they are flagged.

Dynamic Lock Screen can replace its protected wallpaper after every lock.
L.L.E. cannot capture that new image while the display is off, and delaying
every unlock long enough to recapture it would make the effect unreliable.
Automatic screenshot mode therefore still requires Dynamic Lock Screen to be
off. After disabling it, use **Force recapture now**, then lock, wait for the
lockscreen to settle, and unlock once.
