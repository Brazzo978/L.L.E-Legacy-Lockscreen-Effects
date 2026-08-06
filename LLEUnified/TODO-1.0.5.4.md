# L.L.E. 1.0.5.4 TODO

Working baseline: `1.0.5.3` (`versionCode 29`), ARM64-only production build.

This release is focused on display-profile correctness and lockscreen safety.
Do not mix unrelated effect-fidelity changes into these fixes. Preserve System
and Media audio routing, transparent rendering, screenshot/direct-wallpaper
behavior, hint lifecycle and unlock readiness.

## Release gates

- [ ] Tablet orientation profiles and two independent colormaps.
- [ ] Modern Android delayed/unrecognized-touch compatibility issue.
- [ ] Failed PIN/unlock handoff that leaves the user on the normal lockscreen.
- [ ] White, missing, stale or corrupt effect colormap handling.
- [x] Notification-shade/quick-panel detection with safe touch release
  implemented and validated on Galaxy S23/One UI; affected-device and
  additional-OEM validation remains tracked below.
- [ ] Debug reports never include notification text or other accessibility UI
  content.
- [ ] ARM64 tester build passes the full regression matrix.
- [ ] Version bump, signing, tag, push and publication only with explicit user
  authorization.

## 1. Tablet mode: portrait and landscape colormaps

### Goal

Add an explicit Tablet mode alongside the existing Fold mode. Tablets may use
different lockscreen wallpapers in portrait and landscape, so one shared
`single` colormap is not sufficient.

### Intended profile routing

- normal phone: `single`;
- Fold mode: `cover` or `main`, using the existing panel detection;
- Tablet mode: `tablet_portrait` or `tablet_landscape`, selected from the
  active display orientation;
- Fold mode takes precedence over Tablet mode if both capabilities are
  detected. The settings UI must avoid an ambiguous active configuration.

### Work

- [ ] Add Tablet mode detection/toggle next to Fold mode in setup and advanced
  settings.
- [ ] Extend the display-profile resolver without breaking the existing
  `single`, `cover` and `main` preference/cache schema.
- [ ] Keep separate automatic captures, imported wallpapers, timestamps,
  refresh tokens, dimensions and memory-cache identity for portrait and
  landscape.
- [ ] Switch profiles on an actual orientation/display change and invalidate
  only the renderer source that belongs to the old orientation.
- [ ] Load the correct cached colormap before the first visible effect frame
  when possible.
- [ ] Provide independent capture/import/preview status for both tablet
  orientations in the wizard and control UI.
- [ ] Preserve existing phone data as `single`; do not duplicate or erase it
  during migration.
- [ ] Keep touch-box behavior unchanged unless physical tablet validation shows
  that orientation-specific touch boxes are also required.
- [ ] Include active mode, resolved orientation profile, bitmap dimensions and
  source path type (automatic/imported, never the private path itself) in the
  debug report.

### Acceptance

- Portrait and landscape can display different lockscreen wallpapers and each
  effect samples the matching colormap after rotation.
- Repeated rotation never briefly reuses the opposite-orientation map and never
  falls back to white.
- Phone `single` and Fold `cover/main` behavior remains unchanged.
- Imported and automatic sources both work in each tablet orientation.

## 2. Modern Android touch warning and missed drag input

### Reported symptom

Some devices show:

> Isn't optimized for the latest version of Android. Screen touches may be
> delayed or not recognized.

On the reported Motorola device, L.L.E. appears to receive touch down/release
but not a reliable continuous drag. The source manifest already declares
`targetSdkVersion 35`; do not assume that a target-SDK bump is the fix.

The validated 120-second cold-boot safety remains the default mitigation. Its
debug bypass must remain opt-in and prominently warned about, but the safety
window is not a substitute for fixing runtime touch delivery.

### Work

- [ ] Reproduce with a fresh debug report and touch-event trace from an affected
  device.
- [ ] Verify the packaged APK's actual min/target SDK and compatibility flags,
  not only the source manifest.
- [ ] Identify whether the warning originates from Android compatibility,
  accessibility, overlay/window type, restricted settings or vendor input
  policy.
- [ ] Compare DOWN/MOVE/UP delivery at the accessibility event source, touch-box
  window and renderer dispatch boundaries.
- [ ] Fix the smallest confirmed cause; do not broaden overlay privileges or
  weaken the boot safety.
- [ ] Add diagnostic counters for raw touch events, forwarded renderer events,
  gesture distance and dispatch cancellation.
- [ ] Document the safety behavior in the README and wizard with a conspicuous
  warning for the debug boot-bypass option.

### Acceptance

- Affected modern-Android device receives a continuous drag and unlocks with
  one gesture.
- No compatibility warning attributable to L.L.E. remains after clean install
  and permission setup.
- Samsung tester retains normal touch latency, hints, PIN handoff and the
  120-second default boot guard.
- Tiny/default touch-box behavior and user-expanded wizard configuration both
  remain usable.

## 3. Unlock handoff occasionally requires a second swipe

### Reported symptom

The effect reaches its completed unlock animation and L.L.E. removes the touch
box, but SystemUI stays on the normal lockscreen. The user must then perform a
second stock swipe to reach PIN entry or unlock.

### Work

- [ ] Trace renderer completion, touch-box removal, synthetic SystemUI swipe,
  `dispatchGesture` callback, PIN-surface detection and keyguard state as one
  handoff transaction.
- [ ] Do not treat `dispatchGesture` acceptance alone as proof that SystemUI
  opened PIN entry or dismissed keyguard.
- [ ] After the expected transition delay, verify one of these terminal states:
  PIN/security surface visible, keyguard dismissed, or device unlocked.
- [ ] If the device is still locked on the ordinary lockscreen, perform one
  bounded retry using the current display/touch geometry.
- [ ] Never retry after PIN entry, notification shade, global actions, a
  blacklisted surface or launcher becomes visible.
- [ ] If the bounded retry also fails, restore a safe usable touch path instead
  of leaving L.L.E. hidden with no listener.
- [ ] Record attempt count, callback result, keyguard state and observed
  terminal surface in the debug report.

### Acceptance

- One completed L.L.E. swipe consistently reaches PIN entry or unlock.
- No double swipe is injected after a successful first handoff.
- No gesture is injected into PIN/password UI, launcher, notification shade or
  another protected surface.
- Touch box and effect are removed only when handoff ownership has safely moved
  to SystemUI, or are safely re-armed after a confirmed failure.

## 4. White, missing or corrupt colormap

### Confirmed cause

Popping Colours can be marked render-ready before its real colormap is loaded.
Its ARM64 renderer creates an explicit white fallback, and a render-size change
can discard a previously valid bitmap and return to that fallback. The attached
issue report contained a valid persisted PNG and no GL/EGL crash, which is
consistent with this race rather than a missing screenshot.

### Work

- [ ] Never replace a valid colormap with a fabricated white bitmap during
  layout, inset, navigation-mode, surface or orientation changes.
- [ ] Keep the last valid source available for sampling until a validated
  replacement is installed. Popping Colours already supports scaled sampling,
  so a size mismatch must request refresh without destroying the usable map.
- [ ] If no valid map has ever loaded, suppress/queue color-dependent visuals
  instead of emitting white particles. Touch and unlock safety must remain
  functional.
- [ ] Validate decoded dimensions, bitmap state and profile/orientation before
  applying a cache entry.
- [ ] Persist screenshots atomically so a killed process cannot leave a partial
  PNG as the active cache.
- [ ] On decode failure, quarantine/replace only the invalid entry and recapture
  it; preserve valid maps for other effects and display profiles.
- [ ] Reapply an in-memory or persisted valid cache if a renderer loses its
  source during recreation.
- [ ] Audit only active ARM64 screenshot/color-map renderers for the same white
  fallback or destructive size-mismatch pattern. Do not touch/build ARM32.
- [ ] Add debug fields for renderer source state, cache validation result,
  bitmap dimensions, expected profile dimensions and last replacement reason.

### Acceptance

- Immediate first touch, repeated screen off/on, renderer recreation, nav-mode
  change and rotation never produce a white effect/colormap.
- A deliberately truncated or invalid cache is rejected and recaptured without
  crashing or displaying corrupt pixels.
- A valid but temporarily size-mismatched bitmap remains usable until its
  replacement is ready.
- Screenshot and imported/direct-wallpaper modes preserve correct transparency,
  crop/alignment and hint behavior.

## 5. Notification shade and quick panel can remain covered by L.L.E.

### Reported symptom

On issue 27, opening the combined notification shade/quick panel from the
lockscreen does not suspend L.L.E. The visual effect remains above SystemUI and
the configured touch-listener regions can intercept tile and notification
interaction. Swiping inside an L.L.E. touch region can close the panel instead
of operating it.

The supplied report is from L.L.E. `1.0.5.3` (`versionCode 29`), a Samsung
Galaxy A16 (`SM-A166B`), Android 16/API 36 and One UI 8.5 in Turkish, with Mass
Tension selected. SystemUI emits a generic `android.widget.FrameLayout` window
event whose localized pane text identifies the notification shade in Turkish,
but every L.L.E. visibility record keeps `notificationShade=false`.

### Implementation status

- [x] First implementation completed for `1.0.5.4`.
- [x] Galaxy S23/Android 16: open, interaction suppression, close and one-time
  restoration validated with the ARM64 tester.
- [x] PIN transition, Google Assistant, WhatsApp, Telegram and Samsung global
  actions regression checks passed on the S23.
- [x] OEM diagnostics added: device/SystemUI version, bounded window metadata
  and visible resource-ID/class signatures, without accessibility text.
- [ ] Validate the fix on the affected Galaxy A16/One UI 8.5 report.
- [ ] Validate or adapt the structural signature on non-Samsung SystemUI from
  privacy-safe debug reports.

### Confirmed weaknesses in the current detector

- The direct event detector accepts `com.android.systemui`/AOD and then depends
  on a short list of strong class/resource fragments or English/Italian text.
  A generic class plus Turkish, Hungarian, Spanish or another localized pane
  title can therefore be missed.
- `containsKeyword` currently uses locale-dependent lowercasing; use
  `Locale.ROOT` for identifier and fallback-text normalization.
- The bounded node scan can recognize locale-independent resource-ID fragments,
  but deep traversal is currently enabled only after a PIN/shade state has
  already been suspected or latched. It cannot reliably bootstrap detection of
  an initially unknown localized shade.
- A single exact node path, resource ID, translated phrase, window title or
  Samsung model is not an acceptable fix. SystemUI structure changes across
  Android, One UI, devices, layouts and combined/separate panel modes.
- Android exposes window and accessibility transitions but no public universal
  `isQuickPanelExpanded()` API. The solution must combine the best available
  signals and fail safely when classification is uncertain. Google Play policy
  compatibility is explicitly not a requirement for L.L.E.

### Required safety architecture

- [x] Keep the visual renderer and the touch-listener overlay independently
  controllable. The visual renderer must remain non-touchable.
- [x] On a plausible SystemUI pane/window transition while interactive and
  keyguard-locked, suspend/remove the touch listener immediately before doing
  any heavier classification. Ambiguity must never leave the user trapped
  behind an L.L.E. input surface.
- [x] Use an event-driven, bounded, multi-signal detector. Candidate signals
  include active/focused SystemUI window identity, `TYPE_WINDOW_STATE_CHANGED`,
  `TYPE_WINDOWS_CHANGED` change flags, locale-independent view-ID fragments,
  a bounded structural scan for quick-settings controls, dynamically resolved
  SystemUI accessibility strings, and localized text only as fallback.
- [x] Do not require the shade to be already latched before the first bounded
  node scan. Rate-limit initial scans to relevant SystemUI transitions rather
  than scanning every content-change event.
- [x] Treat "probable shade" and "confirmed shade" separately: a probable
  result is sufficient to release the touch box; require stronger evidence to
  change longer-lived visual state.
- [x] Hide/remove effect and touch regions on confirmed shade, cancel active
  L.L.E. gestures and pending PIN/unlock dispatch, and prevent reattachment
  while the panel remains present.
- [x] Re-enable only after bounded negative confirmation plus a short debounce.
  Do not flicker/re-arm during panel animation or notification-content churn.
- [x] Preserve independent suppression for PIN/password/pattern, global
  actions, AOD, calls, blacklisted packages and the 120-second boot guard.
- [ ] The default build should continue working for the existing non-root user
  base, but research may evaluate hidden/reflected SystemUI APIs, Shizuku,
  privileged shell access or root as optional alternatives. Document their
  compatibility, maintenance and installation costs; do not silently make one
  mandatory.
- [ ] Avoid continuous polling, screenshots/image classification or input
  interception that keeps touchscreen events away from SystemUI unless research
  proves that a narrowly scoped alternative is safer than the current overlay.

### Diagnostic privacy requirement

Current `event detail` logging writes `AccessibilityEvent.getText()` and the
content description to logcat. The shareable debug report then captures up to
2,000 lines for the app UID, which has exposed real notification text, contact
names and message content in public issue attachments.

- [x] Stop logging raw accessibility text/content descriptions. Record only
  non-sensitive metadata needed for classification: event type, package,
  generic class, window ID/change flags, boolean signal matches, detector score,
  state transition reason and bounded timing.
- [ ] Sanitize captured logcat as defense in depth so legacy/stale sensitive log
  lines cannot enter a newly generated report.
- [ ] Review preference and internal-file output for identifiers in addition to
  the existing URI/path/token redaction.
- [ ] Add a clear report header stating that notification and UI content were
  not collected.

### Research questions before implementation

- [ ] Rank both public Android signals and viable non-public/OEM-specific
  alternatives for shade open/close detection across API 31-36, AOSP SystemUI
  and Samsung One UI. Separate documented behavior from reverse-engineered or
  device-specific behavior.
- [ ] Determine whether window change flags, layer/bounds/title changes or
  event-source/window IDs can distinguish keyguard, shade and quick settings
  without localized text.
- [ ] Evaluate dynamic lookup of SystemUI's localized accessibility resources
  only as an optional signal; document behavior when private resource names are
  changed or absent.
- [ ] Define a small structural signature for shade/QS that does not depend on
  a full node path, for example brightness/range controls plus multiple
  clickable or toggle controls. Quantify false positives on lockscreen media,
  PIN entry, AOD and global actions.
- [ ] Specify a detector score/state machine, scan budget, debounce and fail-open
  timeout before changing production behavior.

### Acceptance

- Opening combined or separate notification shade/quick settings on the
  lockscreen releases all L.L.E. touch regions before the first attempted tile
  or notification interaction.
- Notifications, brightness, media controls and quick-setting tiles work while
  the panel is open. No L.L.E. gesture or synthetic PIN swipe is dispatched.
- Closing the panel restores the selected effect and touch region once, without
  flicker, stale overlays or requiring screen off/on.
- Behavior does not depend on device language. Validate at minimum English,
  Italian and Turkish; use the observed Hungarian and Spanish pane labels as
  additional regression fixtures.
- Normal lockscreen, biometric unlock, PIN/password/pattern, AOD, global
  actions, launcher and blacklist flows do not falsely latch shade state.
- Detection is event-driven and bounded; idle battery behavior and lockscreen
  frame performance remain unchanged.
- Generated debug reports contain no raw accessibility text, notification
  content, contact names, message bodies or private file paths.

## Recommended implementation order

1. Sanitize debug telemetry, then implement and validate the universal
   notification-shade safe-touch release because it is a user-lockout/privacy
   risk confirmed by the issue 27 report.
2. Colormap lifetime/validation fix, because it is confirmed in current code
   and provides the safe cache foundation for Tablet mode.
3. Unlock-handoff terminal-state verification and bounded retry.
4. Modern Android/Motorola touch-delivery diagnosis and fix.
5. Tablet portrait/landscape profiles and UI.
6. Full regression, version bump and release preparation.

## Validation matrix

- S23 ARM64 tester: first and repeated lock/unlock, PIN and no-PIN flows.
- Affected Galaxy A16/One UI 8.5: combined shade/quick panel open, interact,
  close and effect restore in Turkish.
- S23 Samsung regression: separate/combined panel where available, shade pulled
  partially and fully, brightness/tile/notification interaction, English and
  Italian locale fixtures.
- Offline detector fixtures for the observed Turkish, Hungarian and Spanish
  SystemUI pane events, without retaining notification bodies in the fixture.
- Affected Motorola/modern-Android device: warning, DOWN/MOVE/UP trace and drag.
- Landscape-capable Samsung tablet: portrait/landscape automatic captures,
  imported wallpapers, rotation and navigation modes.
- Fold regression where available: cover/main profile isolation.
- Every active screenshot/color-map effect: hint, tap, drag, release, unlock,
  screen off/on, service recreation and cache reload.
- System and Media audio routes.
- Doodle off/on and runtime blacklist transitions.
- Default 120-second cold-boot guard and explicit debug bypass.

Normal local validation command after reading `BUILD_TEST_RELEASE.md`:

```powershell
.\build-arm64.ps1 -Tester
```
