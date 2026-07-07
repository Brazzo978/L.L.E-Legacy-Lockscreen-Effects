# Effect Background Screenshot Workflow

This document describes the shared screenshot workflow used by LLE lockscreen
effects that need a lockscreen color/background map.

## Core Rule

The screenshot is owned by `ChargingAccessibilityService`, not by individual
effects.

All screenshot-backed effects consume the same base file:

```text
files/unlock_effect_background.png
```

Each effect may crop, resize, gain-adjust, upload, or otherwise transform that
base map for its own renderer, but it must not own the recapture policy.

## Block Diagram

```mermaid
flowchart TD
    A["Settings / ADB / auto timer requests recapture"] --> B["OverlayPrefs effect_background_refresh_token"]
    B --> C["ChargingAccessibilityService observes pending token"]
    C --> D{"Valid lockscreen capture window?"}
    D -->|"No: screen off, AOD, PIN, call, notification shade, app UI, FX overlay, gesture active"| E["Schedule throttled retry"]
    E --> C
    D -->|"Yes"| F["takeScreenshot(Display.DEFAULT_DISPLAY)"]
    F --> G{"Screenshot valid?"}
    G -->|"No: black/flat, AOD, overlay visible, stale generation, capture error"| E
    G -->|"Yes"| H["Apply bitmap to active BackgroundSourceRenderer if present"]
    H --> I["Async PNG save to temp file"]
    I --> J["Atomic swap into unlock_effect_background.png"]
    J --> K["Mark refresh token handled for all screenshot-backed effects"]
    K --> L["Keep old map until validated replacement succeeds"]

    M["Effect selected / warmed / shown"] --> N{"Needs screenshot map?"}
    N -->|"No"| O["Render normally"]
    N -->|"Yes"| P{"Shared map in RAM/file?"}
    P -->|"Yes"| Q["Service passes cached_effect_background to renderer"]
    P -->|"No"| R["Service requests capture at next valid lockscreen window"]
    R --> C
    Q --> S["Effect performs local processing only"]
    S --> T["Crop / resize / gain / native upload / GL texture as needed"]

    U["Legacy per-effect files unlock_effect_background_<id>.png"] --> V["One-time migration only"]
    V --> J
```

## Operational Notes

- `touch_box_lockscreen.png` is only for the touch-box wizard and must not be
  used as the effect colormap.
- `unlock_effect_background.png` is shared by all screenshot-backed effects.
- The service keeps the previous valid map until a new screenshot is validated
  and atomically swapped.
- Capture is blocked while an unlock effect overlay, gesture, PIN entry,
  notification shade, call UI, AOD, or the LLE settings UI is visible.
- Retry is throttled so lockscreen polling cannot spam screenshots every 10 ms.
- The hard-wake refresh activity only arms the service. The service still owns
  validation, capture, save, and sleep/lock cleanup.
- Legacy files such as `unlock_effect_background_2.png` are migration sources
  only. They are copied into the shared file and are not part of runtime policy.

## Main Code Paths

- `OverlayPrefs.effectBackgroundFile(...)`
  returns the shared `unlock_effect_background.png`.
- `ChargingAccessibilityService.refreshUnlockEffectBackgroundSourceIfNeeded(...)`
  decides whether capture is needed.
- `ChargingAccessibilityService.canCaptureUnlockEffectBackground()`
  gates capture to a clean lockscreen window.
- `ChargingAccessibilityService.isValidUnlockEffectBackgroundScreenshot(...)`
  rejects bad frames.
- `ChargingAccessibilityService.persistEffectBackgroundScreenshotAsync(...)`
  writes and swaps the shared PNG.
- `BackgroundSourceRenderer.setBackgroundSourceBitmap(...)`
  is the consumption point for effects.
