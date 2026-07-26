# L.L.E 1.0.4.2

L.L.E 1.0.4.2 is a stable runtime-compatibility and support update for ARM64.

## Runtime surface protection

Unlock effects and charging doodles are now suspended while any of these
lockscreen-adjacent surfaces are active:

- Samsung Edge panels
- Samsung side-gesture pad
- Samsung Camera
- WhatsApp, Telegram, Messenger, and other common VoIP/caller apps
- Samsung Clock and alarm screens
- Samsung Reminder
- Waze and other common navigation apps
- Samsung, AOSP, and Google dialer/call surfaces

This prevents an L.L.E overlay from drawing over camera shortcuts, incoming
calls, alarms, reminders, navigation alerts, Edge panels, and gesture surfaces.
The selected effect becomes available again as soon as the lockscreen regains
focus.

## Debug reports

The new **Create debug report** button under **Setup & permissions** generates
a text-only support report and opens Android's share sheet. It includes the
current L.L.E configuration, runtime state, device/build information, memory
status, and recent logs from every process belonging to L.L.E, including a
previous process after a renderer crash while the Android log buffer still
retains it. Wallpapers and images are never included, and path-like preference
values are redacted.

## Builds

- `LLE64-1.0.4.2-64-bit.apk` — recommended ARM64 build.
- ARM32 is not rebuilt for this release. The frozen historical
  `LLE-1.0.4.1-32-bit.apk` remains available from the previous release.
