# Optional root debug plan

Root support is opt-in and diagnostic only. It must not replace the normal
accessibility/media-projection path unless a later test proves it is better on
the target device.

## App-side test tools

The `Root debug` section in the lockscreen tab is disabled by default. It adds:

| Tool | Behavior |
| --- | --- |
| `Root: check su` | Runs `su -c id` and checks for `uid=0`. |
| `Root: benchmark touch 8s` | Runs an 8 second `getevent -lt` capture, filters the Samsung touchscreen device, and saves `root_touch_events.txt`. |
| `Root: write debug report` | Runs read-only `dumpsys`/`getprop`/`appops` diagnostics and saves `root_debug_report.txt`. |
| `Root: write keepalive plan` | Writes reversible commands to `root_keepalive_plan.txt`; it does not apply them. |

Safety constraints:

- No remount.
- No `/system` writes.
- No Magisk module install.
- No SystemUI integration.
- No touch injection.
- No automatic battery/appops mutation.
- All command output has size limits and timeouts.

## ADB commands for later profiling

```sh
adb shell su -c id
adb shell su -c 'timeout 5 getevent -lt'
adb shell su -c 'dumpsys input'
adb shell su -c 'dumpsys window'
adb shell su -c 'dumpsys power'
adb shell su -c 'dumpsys SurfaceFlinger --latency'
adb shell su -c 'dumpsys gfxinfo com.codex.lle framestats'
adb shell su -c 'cmd appops get com.codex.lle'
```

Root touch benchmark can also be launched from ADB:

```sh
adb shell am broadcast -a com.codex.lle.BENCHMARK_TOUCH
adb exec-out run-as com.codex.lle cat files/root_touch_events.txt > root_touch_events.txt
```

The broadcast is ignored unless `Enable root debug tools` and
`Root touch capture test` are both enabled in the app.

Measured on SM-S918B / Android 16:

| Input path | Result |
| --- | --- |
| Root `getevent -lt` | Works and identifies `sec_touchscreen` as `/dev/input/event10`; captured hundreds of raw touch events. |
| Existing overlay touch box | Also captured the same physical touches immediately; gesture begin stayed around 0-3 ms after DOWN. |

Conclusion: root input is useful as a diagnostic trace, but it does not currently
beat the existing overlay touch box enough to justify a live root input path.
Keeping the live service on the non-root touch box avoids a persistent root
reader, raw event parsing, coordinate mapping, and extra failure modes.

The root keepalive item stays as a future-only option: it writes a reversible
plan for battery/optimization experiments, but the app does not apply those
commands automatically. A short SM-S918B wake test did not prove a clear
keepalive win; the useful improvement was keeping the existing standby touch
box listening and relying on the normal lockscreen readiness gate.

Files written by the app-side buttons can be pulled from the debuggable app
private directory:

```sh
adb shell run-as com.codex.lle ls files
adb exec-out run-as com.codex.lle cat files/root_touch_events.txt > root_touch_events.txt
adb exec-out run-as com.codex.lle cat files/root_debug_report.txt > root_debug_report.txt
adb exec-out run-as com.codex.lle cat files/root_keepalive_plan.txt > root_keepalive_plan.txt
```

## Optional keepalive experiment

These are the only keepalive commands proposed for a root test. They are scoped
to `com.codex.lle` and reversible:

```sh
adb shell su -c 'dumpsys deviceidle whitelist +com.codex.lle'
adb shell su -c 'cmd appops set com.codex.lle RUN_ANY_IN_BACKGROUND allow'
adb shell su -c 'cmd appops set com.codex.lle RUN_IN_BACKGROUND allow'
adb shell su -c 'cmd appops set com.codex.lle WAKE_LOCK allow'
adb shell su -c 'am set-inactive com.codex.lle false'
```

Revert:

```sh
adb shell su -c 'dumpsys deviceidle whitelist -com.codex.lle'
adb shell su -c 'cmd appops set com.codex.lle RUN_ANY_IN_BACKGROUND default'
adb shell su -c 'cmd appops set com.codex.lle RUN_IN_BACKGROUND default'
adb shell su -c 'cmd appops set com.codex.lle WAKE_LOCK default'
```

If this proves useful, the next safe step is to expose an explicit in-app
"apply/revert keepalive" pair behind the root debug toggle, never as a silent
startup action.
