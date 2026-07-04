# Optional root debug plan

Root support is opt-in and diagnostic only. It must not replace the normal
accessibility/media-projection path unless a later test proves it is better on
the target device.

## App-side test tools

The `Root debug` section in the lockscreen tab is disabled by default. It adds:

| Tool | Behavior |
| --- | --- |
| `Root: check su` | Runs `su -c id` and checks for `uid=0`. |
| `Root: capture screenshot` | Runs `su -c screencap -p`, reads PNG bytes from stdout, and saves `root_screenshot.png` in app private files. |
| `Root: capture touch 3s` | Runs a 3 second `getevent -lt` capture and saves `root_touch_events.txt` in app private files. |
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
adb shell su -c 'screencap -p > /data/local/tmp/lle_root_screen.png'
adb pull /data/local/tmp/lle_root_screen.png
adb shell su -c 'timeout 5 getevent -lt'
adb shell su -c 'dumpsys input'
adb shell su -c 'dumpsys window'
adb shell su -c 'dumpsys power'
adb shell su -c 'dumpsys SurfaceFlinger --latency'
adb shell su -c 'dumpsys gfxinfo com.codex.lle framestats'
adb shell su -c 'cmd appops get com.codex.lle'
```

Files written by the app-side buttons can be pulled from the debuggable app
private directory:

```sh
adb shell run-as com.codex.lle ls files
adb exec-out run-as com.codex.lle cat files/root_screenshot.png > root_screenshot.png
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
