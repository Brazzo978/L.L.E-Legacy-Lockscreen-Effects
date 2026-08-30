# L.L.E 1.0.5.7 Beta 2

This diagnostic beta improves log collection on Android builds whose `logcat` command does not
support the `--uid` selector.

## Diagnostic report change

- The preferred path remains `logcat --uid=<LLE uid>`.
- When that selector is unavailable, L.L.E reads logcat with the UID field enabled and locally
  retains only entries belonging to the L.L.E UID. This preserves entries from earlier L.L.E
  process IDs, including renderer crashes followed by a process restart.
- The current-process PID selector is retained only as a final compatibility fallback.
- The filtered report remains capped at 512 KiB; the temporary UID-formatted input is capped at
  4 MiB and is never written to the report unfiltered.

## Version

- Version: `1.0.5.7.B2` (`versionCode 34`).
- The Companion update marker remains on stable `1.0.5.6`.
