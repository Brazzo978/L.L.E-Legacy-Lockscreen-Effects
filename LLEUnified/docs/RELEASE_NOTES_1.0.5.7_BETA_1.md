# L.L.E 1.0.5.7 Beta 1

This diagnostic beta restores the full app-scoped logcat section in user-generated debug
reports. It is intended to diagnose effect initialization and renderer failures that could not
be identified from the severity-only summary shipped in 1.0.5.6.

## Diagnostic report change

- The report includes up to 2,000 logcat lines selected by the L.L.E app UID.
- Android versions without `logcat --uid` support fall back to the current L.L.E process ID.
- The existing 512 KiB report cap remains in place.
- Reports now include an explicit warning that app logs may contain notification or
  accessibility text and should be reviewed before sharing.

## Version

- Version: `1.0.5.7.B1` (`versionCode 33`).
- The Companion update marker remains on stable `1.0.5.6`; this beta is not advertised as a
  stable update.
