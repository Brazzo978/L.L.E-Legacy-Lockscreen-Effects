# L.L.E 1.0.7.0 — Effects Workshop and dark mode

This stable release brings the Effects Workshop and the RC2 appearance controls
to the main L.L.E application.

## Effects Workshop

- Open **Effects → Effects workshop → Open advanced workshop** at the bottom
  of the Effects page. The editor shows the selected effect's controls.
- Explore **705 controls across 39 selectable effects**, including Samsung,
  Good Lock, LG, Sony, Seasonal and Emoji Trail. Controls cover the appearance,
  geometry, particles, motion, fluid simulation, shaders and audio supported by
  each renderer.
- Use compact sliders with decimal steps, light haptic feedback and minus/plus
  buttons, or enter a value directly.
- Each parameter has an English **ⓘ** explanation and an individual **↺** reset.
  **Reset all** restores the draft defaults; **Apply** saves and **Cancel**
  discards the draft. The action buttons remain outside the scrolling controls.
- Disable custom settings to restore the original effect while retaining saved
  values. Final unlock and handoff timing stays unchanged.
- Renderer configuration checks recover from missed preference notifications
  and defer applying settings until an active gesture or protected transition
  has finished.

## Appearance

- **Auto** follows the Android system theme by default. In **ADV → Appearance**,
  the compact **Auto / moon / sun** selector can force Dark or Light.
- Dark surfaces, aqua accents and readable controls extend to the Workshop,
  dialogs, setup wizard, app picker, touch area editor and wallpaper editor.
- A circular reveal animates changes between light and dark. It respects the
  Android animation setting and retains the current page and scroll position.
- Workshop drafts and unsaved touch area edits survive theme recreation.
- App appearance does not recolor effect artwork, wallpapers or calibration
  screenshots, or change effect settings.

## Installation and Companion

- **Version:** `1.0.7.0` (`versionCode 55`).
- **Stable package:** `com.codex.lle64`; **ABI:** `arm64-v8a`.
- **Minimum Android:** Android 6.0 / API 23.
- Download **LLE64-1.0.7.0-64-bit.apk** and verify it against **SHA256SUMS.txt**.
- Updates the compatible official stable installation using the established
  signing lineage. The separate Tester installation remains independent.
- This is a normal stable release and becomes **Latest**. Both current and
  older Companion update feeds advertise it after the release is public.
  Existing Companion installations can offer this L.L.E update without a new
  Companion binary.

See [Workshop documentation](EFFECTS_WORKSHOP.md) and
[app appearance documentation](APP_APPEARANCE.md) for behavior and validation
boundaries. Device behavior can vary with the Android version, manufacturer
and GPU; this release does not claim exhaustive physical testing of every
parameter combination.
