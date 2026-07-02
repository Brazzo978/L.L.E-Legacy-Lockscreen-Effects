package com.codex.chargingtouchtest;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class ChargingAccessibilityService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "ChargingA11y";
    private static final int UNLOCK_TRIGGER_DISTANCE_DP = 120;
    private static final long PIN_ENTRY_DELAY_LENS_FLARE_MS = 400L;
    private static final long PIN_ENTRY_DELAY_POPPING_COLOURS_MS = 200L;
    private static final long PIN_ENTRY_DELAY_WATERCOLOUR_MS = 250L;
    private static final long PIN_ENTRY_DELAY_COLOUR_DROPLET_MS = 250L;
    private static final long PIN_ENTRY_DELAY_SPARKLING_BUBBLES_MS = 300L;
    private static final long PIN_ENTRY_SWIPE_START_DELAY_MS = 60L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_MS = 900L;
    private static final long PIN_ENTRY_SWIPE_DURATION_MS = 260L;
    private static final long DEBUG_LOOP_STEP_DELAY_MS = 120L;
    private static final long DEBUG_LOOP_RESTART_DELAY_MS = 620L;
    private static final long SCREEN_ON_REFRESH_FAST_MS = 35L;
    private static final long SCREEN_ON_REFRESH_SETTLE_MS = 140L;
    private static final long LOCKSCREEN_SESSION_FAST_POLL_MS = 10L;
    private static final long LOCKSCREEN_SESSION_CONTENT_POLL_MS = 40L;
    private static final long SCREEN_OFF_PREARM_FAST_MS = 80L;
    private static final long SCREEN_OFF_PREARM_SETTLE_MS = 180L;
    private static final long BLOCKED_SURFACE_CLEAR_GRACE_MS = 120L;
    private static final int TOUCH_LISTEN_BOX_BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    private static final int PIN_ENTRY_NODE_SCAN_DEPTH = 10;
    private static final int PIN_ENTRY_NODE_SCAN_CHILD_LIMIT = 80;
    private static final int BLOCKED_SURFACE_PIN_ENTRY = 1;
    private static final int BLOCKED_SURFACE_NOTIFICATION_SHADE = 1 << 1;
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String AOD_PACKAGE = "com.samsung.android.app.aodservice";
    private static final String SAMSUNG_KEYBOARD_PACKAGE = "com.samsung.android.honeyboard";
    private static final String GOOGLE_KEYBOARD_PACKAGE = "com.google.android.inputmethod.latin";
    private static final String AOSP_KEYBOARD_PACKAGE = "com.android.inputmethod.latin";
    private static final String[] PIN_ENTRY_STRONG_KEYWORDS = {
            "bouncer",
            "keyguardsecurity",
            "keyguard_security",
            "keyguardpin",
            "keyguard_pin",
            "keyguardpassword",
            "keyguard_password",
            "numpad",
            "passwordentry",
            "password_entry",
            "pinentry",
            "pin_entry",
            "pinview",
            "pin_view",
            "lockpattern",
            "lock_pattern",
            "sim_pin",
            "sim_puk"
    };
    private static final String[] PIN_ENTRY_TEXT_KEYWORDS = {
            "enter pin",
            "enter your pin",
            "inserisci pin",
            "inserisci il pin",
            "immetti pin",
            "usa il pin",
            "pin richiesto",
            "enter password",
            "inserisci password",
            "draw pattern",
            "traccia il segno",
            "traccia segno",
            "area sequenza",
            "sequenza di sblocco"
    };
    private static final String[] NOTIFICATION_SHADE_STRONG_KEYWORDS = {
            "notification_shade",
            "status_bar_expanded",
            "quick_qs_panel",
            "qs_panel",
            "qs_tile",
            "qs_detail",
            "quick_settings_panel",
            "quick_panel",
            "brightness_slider",
            "brightness_mirror",
            "sec_brightness"
    };
    private static final String[] NOTIFICATION_SHADE_TEXT_KEYWORDS = {
            "area notifiche",
            "notification shade",
            "quick settings",
            "impostazioni rapide",
            "pannello rapido"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    };
    private final Runnable pinEntryRunnable = new Runnable() {
        @Override
        public void run() {
            openPinEntry();
        }
    };
    private final Runnable pinEntrySwipeRunnable = new Runnable() {
        @Override
        public void run() {
            runPinEntrySwipe();
        }
    };
    private final Runnable pinEntryEffectCleanupRunnable = new Runnable() {
        @Override
        public void run() {
            removeUnlockEffectOverlay();
        }
    };
    private final Runnable debugLensLoopRunnable = new Runnable() {
        @Override
        public void run() {
            runDebugLensLoopFrame();
        }
    };
    private final Runnable screenOnRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            evaluateVisibility("screen_on_refresh");
        }
    };
    private final Runnable lockscreenSessionPollRunnable = new Runnable() {
        @Override
        public void run() {
            runLockscreenSessionPoll();
        }
    };
    private final Runnable screenOffPrearmRunnable = new Runnable() {
        @Override
        public void run() {
            prearmUnlockTouchForScreenOff();
        }
    };
    private WindowManager windowManager;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    private SharedPreferences prefs;
    private SeasonalDoodleView overlayView;
    private TouchDebugView touchDebugView;
    private WindowManager.LayoutParams touchDebugParams;
    private boolean touchDebugTouchable;
    private UnlockEffectRenderer unlockEffectRenderer;
    private View unlockEffectView;
    private int unlockEffectRendererType = -1;
    private boolean unlockEffectOverlayAttached;
    private float unlockEffectAnchorX;
    private float unlockEffectAnchorY;
    private boolean debugLensLoopScheduled;
    private boolean debugLensLoopGestureActive;
    private int debugLensLoopFrame;
    private final Set<String> homePackages = new HashSet<String>();
    private String lastWindowPackage;
    private boolean charging;
    private int batteryPercent;
    private boolean pinEntryRequested;
    private boolean pinEntrySurfaceSeen;
    private boolean pinEntrySurfaceVisible;
    private boolean notificationShadeVisible;
    private boolean unlockTouchCachedWhileScreenOff;
    private boolean lockscreenSessionPolling;
    private long nextContentAwarePollAt;
    private long pinEntryLastSeenAt;
    private long notificationShadeLastSeenAt;
    private boolean colorScreenshotInFlight;
    private boolean colorScreenshotAttemptedThisSession;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? "null" : intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                updateChargingState(intent);
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)
                    || Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                refreshChargingState();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.i(TAG, "screen off broadcast interactive="
                        + (powerManager == null || powerManager.isInteractive())
                        + " locked=" + isLockscreenLocked(false));
                stopLockscreenSessionPolling();
                clearBlockedSurfaceState();
                colorScreenshotAttemptedThisSession = false;
                cacheUnlockTouchForScreenOff();
                scheduleScreenOffPrearm();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                stopLockscreenSessionPolling();
                handler.removeCallbacks(screenOffPrearmRunnable);
                clearBlockedSurfaceState();
                unlockTouchCachedWhileScreenOff = false;
                colorScreenshotAttemptedThisSession = false;
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.i(TAG, "screen on broadcast cached=" + unlockTouchCachedWhileScreenOff
                        + " interactive=" + (powerManager == null || powerManager.isInteractive())
                        + " locked=" + isLockscreenLocked(false));
                handler.removeCallbacks(screenOffPrearmRunnable);
                if (unlockTouchCachedWhileScreenOff) {
                    notificationShadeVisible = false;
                    notificationShadeLastSeenAt = 0L;
                }
                evaluateVisibility("broadcast:" + action + ":fast", false);
                unlockTouchCachedWhileScreenOff = false;
                scheduleScreenOnRefreshes();
                startLockscreenSessionPolling();
                return;
            }
            evaluateVisibility("broadcast:" + action);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "connected");
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        prefs = OverlayPrefs.get(this);
        OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this);
        applyPerfDefaultsOnce();
        ensureInternalTouchAreaEnabled();
        prefs.registerOnSharedPreferenceChangeListener(this);
        loadHomePackages();
        configurePassiveService();
        refreshChargingState();
        preloadUnlockEffectRenderer();
        registerScreenReceiver();
        evaluateVisibility("connected");
    }

    private void applyPerfDefaultsOnce() {
        if (prefs == null || prefs.getBoolean(OverlayPrefs.PERF_DEFAULTS_APPLIED, false)) {
            return;
        }
        prefs.edit()
                .putBoolean(OverlayPrefs.DEBUG_LENS_LOOP, false)
                .putBoolean(OverlayPrefs.PERF_DEFAULTS_APPLIED, true)
                .apply();
    }

    private void ensureInternalTouchAreaEnabled() {
        if (prefs != null && !prefs.getBoolean(OverlayPrefs.DEBUG_TOUCH_AREA, true)) {
            prefs.edit().putBoolean(OverlayPrefs.DEBUG_TOUCH_AREA, true).apply();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            lastWindowPackage = event.getPackageName().toString();
        }
        logSystemUiEvent(event);
        boolean pinEntryEvent = isPinEntryEvent(event);
        boolean keyboardPinEntryEvent = isKeyboardPinEntryEvent(event);
        if (pinEntryEvent || keyboardPinEntryEvent) {
            boolean wasPinEntryRequested = pinEntryRequested;
            pinEntryRequested = true;
            pinEntrySurfaceSeen = true;
            pinEntrySurfaceVisible = true;
            pinEntryLastSeenAt = SystemClock.uptimeMillis();
            if (keyboardPinEntryEvent && !pinEntryEvent) {
                Log.i(TAG, "pin entry keyboard surface visible pkg="
                        + event.getPackageName());
            }
            removeTouchDebugOverlay();
            if (!wasPinEntryRequested) {
                scheduleUnlockEffectCleanup();
            }
            evaluateVisibility("event:" + eventTypeName(event) + ":pin_fast", false);
            handler.removeCallbacks(screenOnRefreshRunnable);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_FAST_MS);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_SETTLE_MS);
            return;
        } else if ((powerManager == null || powerManager.isInteractive())
                && isNotificationShadeEvent(event)) {
            notificationShadeVisible = true;
            notificationShadeLastSeenAt = SystemClock.uptimeMillis();
            removeTouchDebugOverlay();
            removeUnlockEffectOverlay();
            evaluateVisibility("event:" + eventTypeName(event) + ":notification_shade_fast", false);
            handler.removeCallbacks(screenOnRefreshRunnable);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_FAST_MS);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_SETTLE_MS);
            return;
        }
        evaluateVisibility("event:" + eventTypeName(event));
    }

    @Override
    public void onMotionEvent(MotionEvent event) {
        // Disabled for now: this phase is only about a visible, non-interactive lockscreen layer.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (OverlayPrefs.POPPING_COLOR_REFRESH_TOKEN.equals(key)) {
            invalidateUnlockEffectBackgroundSource();
        }
        if ((OverlayPrefs.SEASON_MODE.equals(key)
                || OverlayPrefs.POSITION_OFFSET_X.equals(key)
                || OverlayPrefs.POSITION_OFFSET_Y.equals(key)
                || OverlayPrefs.DEBUG_ROLLING_CHARGE.equals(key)
                || OverlayPrefs.SHOW_DOODLE.equals(key))
                && overlayView != null) {
            applyOverlayPrefs();
        }
        if (OverlayPrefs.DEBUG_TOUCH_AREA.equals(key)) {
            ensureInternalTouchAreaEnabled();
            if (touchDebugView != null) {
                syncTouchDebugOverlay();
            }
        }
        if (OverlayPrefs.DEBUG_TOUCH_TRANSPARENT.equals(key) && touchDebugView != null) {
            touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
        }
        if (OverlayPrefs.DEBUG_LENS_LOOP.equals(key)) {
            if (OverlayPrefs.debugLensLoop(this)) {
                syncDebugLensLoop();
            } else {
                stopDebugLensLoop();
            }
        }
        if ((OverlayPrefs.TOUCH_BOX_CONFIGURED.equals(key)
                || OverlayPrefs.TOUCH_BOX_LEFT.equals(key)
                || OverlayPrefs.TOUCH_BOX_TOP.equals(key)
                || OverlayPrefs.TOUCH_BOX_RIGHT.equals(key)
                || OverlayPrefs.TOUCH_BOX_BOTTOM.equals(key))
                && touchDebugView != null) {
            syncTouchDebugOverlay();
        }
        if (OverlayPrefs.UNLOCK_EFFECT.equals(key) && unlockEffectRenderer != null) {
            destroyUnlockEffectOverlay();
        }
        evaluateVisibility("prefs:" + key);
    }

    private void cleanup() {
        handler.removeCallbacksAndMessages(null);
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }
        try {
            unregisterReceiver(screenReceiver);
        } catch (RuntimeException ignored) {
            // Receiver may not be registered if the service never fully connected.
        }
        removeOverlay();
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(screenReceiver, filter);
    }

    private void scheduleScreenOnRefreshes() {
        handler.removeCallbacks(screenOnRefreshRunnable);
        handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_FAST_MS);
        handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_SETTLE_MS);
    }

    private void scheduleScreenOffPrearm() {
        handler.removeCallbacks(screenOffPrearmRunnable);
        handler.post(screenOffPrearmRunnable);
        handler.postDelayed(screenOffPrearmRunnable, SCREEN_OFF_PREARM_FAST_MS);
        handler.postDelayed(screenOffPrearmRunnable, SCREEN_OFF_PREARM_SETTLE_MS);
    }

    private void startLockscreenSessionPolling() {
        if (lockscreenSessionPolling) {
            return;
        }
        lockscreenSessionPolling = true;
        nextContentAwarePollAt = 0L;
        handler.removeCallbacks(lockscreenSessionPollRunnable);
        handler.post(lockscreenSessionPollRunnable);
    }

    private void stopLockscreenSessionPolling() {
        lockscreenSessionPolling = false;
        handler.removeCallbacks(lockscreenSessionPollRunnable);
    }

    private void runLockscreenSessionPoll() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = isLockscreenLocked(false);
        if (!interactive || !locked) {
            stopLockscreenSessionPolling();
            return;
        }

        long now = SystemClock.uptimeMillis();
        boolean contentAware = now >= nextContentAwarePollAt;
        if (contentAware) {
            nextContentAwarePollAt = now + LOCKSCREEN_SESSION_CONTENT_POLL_MS;
        }
        evaluateVisibility(contentAware ? "lockscreen_poll_content" : "lockscreen_poll_fast",
                contentAware);
        handler.postDelayed(lockscreenSessionPollRunnable, LOCKSCREEN_SESSION_FAST_POLL_MS);
    }

    private void cacheUnlockTouchForScreenOff() {
        unlockTouchCachedWhileScreenOff = touchDebugView != null
                && OverlayPrefs.unlockEffectEnabled(this)
                && OverlayPrefs.debugTouchArea(this);
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.resetEffect();
        }
        if (unlockTouchCachedWhileScreenOff) {
            syncTouchDebugOverlay(true, false);
            Log.i(TAG, "unlock touch box cached for screen off");
        }
    }

    private void prearmUnlockTouchForScreenOff() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (interactive || !isLockscreenLocked(false)) {
            return;
        }
        boolean showDoodle = isDoodleVisible(false, true, false, false);
        if (showDoodle
                || !OverlayPrefs.unlockEffectEnabled(this)
                || !OverlayPrefs.debugTouchArea(this)) {
            return;
        }
        syncUnlockEffectOverlay();
        syncTouchDebugOverlay(true, false);
        unlockTouchCachedWhileScreenOff = touchDebugView != null;
        if (unlockTouchCachedWhileScreenOff) {
            Log.i(TAG, "unlock touch box prearmed for screen off");
        }
    }

    private void clearBlockedSurfaceState() {
        pinEntryRequested = false;
        pinEntrySurfaceSeen = false;
        pinEntrySurfaceVisible = false;
        notificationShadeVisible = false;
        pinEntryLastSeenAt = 0L;
        notificationShadeLastSeenAt = 0L;
    }

    private void configurePassiveService() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        setServiceInfo(info);
    }

    private void evaluateVisibility(String reason) {
        evaluateVisibility(reason, true);
    }

    private void evaluateVisibility(String reason, boolean contentAware) {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = isLockscreenLocked(contentAware);
        boolean home = interactive && !locked && isHomePackage(lastWindowPackage);
        if (!interactive && unlockTouchCachedWhileScreenOff) {
            boolean showDoodle = isDoodleVisible(false, locked, false, false);
            if (showDoodle) {
                syncDoodleOverlay();
                removeUnlockEffectOverlay();
                removeTouchDebugOverlay();
                unlockTouchCachedWhileScreenOff = false;
            } else {
                removeDoodleOverlay();
                syncTouchDebugOverlay(true, false);
            }
            Log.i(TAG, "visibility reason=" + reason
                    + " showDoodle=" + showDoodle
                    + " showFx=cached"
                    + " charging=" + charging
                    + " interactive=false"
                    + " locked=" + locked
                    + " pinEntryRequested=" + pinEntryRequested
                    + " pinEntrySurface=" + pinEntrySurfaceVisible
                    + " notificationShade=" + notificationShadeVisible
                    + " home=false"
                    + " pkg=" + lastWindowPackage);
            return;
        }
        if (contentAware) {
            long now = SystemClock.uptimeMillis();
            boolean wasPinEntrySurfaceVisible = pinEntrySurfaceVisible;
            boolean wasNotificationShadeVisible = notificationShadeVisible;
            int blockedSurfaces = detectContentBlockedSurfaces();
            boolean detectedPinEntry = (blockedSurfaces & BLOCKED_SURFACE_PIN_ENTRY) != 0;
            boolean detectedNotificationShade =
                    (blockedSurfaces & BLOCKED_SURFACE_NOTIFICATION_SHADE) != 0;
            if (detectedPinEntry) {
                pinEntryLastSeenAt = now;
            }
            if (detectedNotificationShade) {
                notificationShadeLastSeenAt = now;
            }
            pinEntrySurfaceVisible = detectedPinEntry;
            notificationShadeVisible = detectedNotificationShade
                    || (wasNotificationShadeVisible
                    && now - notificationShadeLastSeenAt < BLOCKED_SURFACE_CLEAR_GRACE_MS);
            if (pinEntrySurfaceVisible != wasPinEntrySurfaceVisible) {
                Log.i(TAG, "pin entry surface visible=" + pinEntrySurfaceVisible);
            }
            if (notificationShadeVisible != wasNotificationShadeVisible) {
                Log.i(TAG, "notification shade visible=" + notificationShadeVisible);
            }
            if (pinEntrySurfaceVisible) {
                pinEntrySurfaceSeen = true;
            }
        }
        if (!interactive || !locked) {
            clearBlockedSurfaceState();
        } else if (pinEntryRequested && pinEntrySurfaceSeen && !pinEntrySurfaceVisible) {
            long elapsedSincePinSeen = pinEntryLastSeenAt <= 0L
                    ? Long.MAX_VALUE
                    : SystemClock.uptimeMillis() - pinEntryLastSeenAt;
            if (elapsedSincePinSeen >= BLOCKED_SURFACE_CLEAR_GRACE_MS) {
                pinEntryRequested = false;
                pinEntrySurfaceSeen = false;
                pinEntryLastSeenAt = 0L;
                Log.i(TAG, "pin entry surface cleared; lockscreen controls re-enabled");
            }
        }

        boolean pinEntryActive = pinEntryRequested || pinEntrySurfaceVisible;
        boolean blockedSurfaceActive = pinEntryActive || notificationShadeVisible;
        boolean showDoodle = isDoodleVisible(interactive, locked, home, blockedSurfaceActive);
        boolean showFx = interactive
                && locked
                && !blockedSurfaceActive
                && !showDoodle
                && OverlayPrefs.unlockEffectEnabled(this);

        if (showDoodle) {
            syncDoodleOverlay();
        } else {
            removeDoodleOverlay();
        }

        if (showFx) {
            refreshUnlockEffectBackgroundSourceIfNeeded("showFx:" + reason);
            syncUnlockEffectOverlay();
            syncTouchDebugOverlay(true, true);
            syncDebugLensLoop();
        } else {
            stopDebugLensLoop();
            if (!pinEntryRequested && !pinEntrySurfaceVisible) {
                removeUnlockEffectOverlay();
            }
            removeTouchDebugOverlay();
        }

        if (interactive && locked) {
            startLockscreenSessionPolling();
        } else {
            stopLockscreenSessionPolling();
        }

        if (shouldLogVisibility(reason)) {
            Log.i(TAG, "visibility reason=" + reason
                    + " showDoodle=" + showDoodle
                    + " showFx=" + showFx
                    + " charging=" + charging
                    + " interactive=" + interactive
                    + " locked=" + locked
                    + " pinEntryRequested=" + pinEntryRequested
                    + " pinEntrySurface=" + pinEntrySurfaceVisible
                    + " notificationShade=" + notificationShadeVisible
                    + " home=" + home
                    + " pkg=" + lastWindowPackage);
        }
    }

    private boolean shouldLogVisibility(String reason) {
        return reason == null || !reason.startsWith("lockscreen_poll");
    }

    private void syncDoodleOverlay() {
        if (!OverlayPrefs.showDoodle(this)) {
            removeDoodleOverlay();
            return;
        }
        if (overlayView != null) {
            applyOverlayPrefs();
            return;
        }

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("ChargingAccessibilityOverlay");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        overlayView = new SeasonalDoodleView(this);
        applyOverlayPrefs();
        windowManager.addView(overlayView, params);
        Log.i(TAG, "doodle overlay shown");
    }

    private void syncUnlockEffectOverlay() {
        preloadUnlockEffectRenderer();
        if (unlockEffectOverlayAttached || unlockEffectView == null) {
            return;
        }
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("ChargingUnlockEffect");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        windowManager.addView(unlockEffectView, params);
        unlockEffectOverlayAttached = true;
        unlockEffectView.post(new Runnable() {
            @Override
            public void run() {
                if (unlockEffectRenderer != null) {
                    unlockEffectRenderer.warmUp();
                }
            }
        });
        Log.i(TAG, "unlock effect overlay shown type=" + unlockEffectRendererType
                + " name=" + (unlockEffectRenderer == null
                ? "none"
                : unlockEffectRenderer.effectName()));
    }

    private void preloadUnlockEffectRenderer() {
        int effect = OverlayPrefs.unlockEffect(this);
        if (unlockEffectRenderer != null && unlockEffectRendererType == effect) {
            return;
        }
        destroyUnlockEffectOverlay();
        unlockEffectRendererType = effect;
        if (effect == OverlayPrefs.EFFECT_S4_LENS_FLARE) {
            unlockEffectRenderer = new LensFlareEffectView(this);
        } else if (effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS) {
            unlockEffectRenderer = new PoppingColoursEffectView(this);
        } else if (effect == OverlayPrefs.EFFECT_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_SPARKLING_BUBBLES) {
            unlockEffectRenderer = new SamsungNativeEffectView(this, effect);
        } else {
            unlockEffectRenderer = null;
            unlockEffectView = null;
            Log.i(TAG, "unlock effect slot has no renderer type=" + effect);
            return;
        }
        unlockEffectView = unlockEffectRenderer.asView();
        Log.i(TAG, "unlock effect renderer preloaded type=" + effect
                + " name=" + unlockEffectRenderer.effectName());
    }

    private void refreshUnlockEffectBackgroundSourceIfNeeded(final String reason) {
        int effect = OverlayPrefs.unlockEffect(this);
        if (!effectUsesScreenshotBackground(effect)
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || colorScreenshotInFlight
                || colorScreenshotAttemptedThisSession) {
            return;
        }
        preloadUnlockEffectRenderer();
        if (!(unlockEffectRenderer instanceof BackgroundSourceRenderer)) {
            return;
        }
        BackgroundSourceRenderer backgroundRenderer =
                (BackgroundSourceRenderer) unlockEffectRenderer;
        if (backgroundRenderer.hasBackgroundSourceBitmap()) {
            return;
        }
        colorScreenshotAttemptedThisSession = true;
        colorScreenshotInFlight = true;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    colorScreenshotInFlight = false;
                    Bitmap bitmap = bitmapFromScreenshot(screenshotResult);
                    if (bitmap == null) {
                        Log.i(TAG, "unlock effect background screenshot empty reason=" + reason);
                        return;
                    }
                    applyUnlockEffectBackgroundSource(bitmap, "accessibility_screenshot");
                    bitmap.recycle();
                }

                @Override
                public void onFailure(int errorCode) {
                    colorScreenshotInFlight = false;
                    Log.i(TAG, "unlock effect background screenshot failed code=" + errorCode
                            + " reason=" + reason);
                }
            });
        } catch (Throwable t) {
            colorScreenshotInFlight = false;
            Log.d(TAG, "unlock effect background screenshot request failed reason=" + reason, t);
        }
    }

    private Bitmap bitmapFromScreenshot(ScreenshotResult screenshotResult) {
        if (screenshotResult == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
        if (buffer == null) {
            return null;
        }
        try {
            Bitmap wrapped = Bitmap.wrapHardwareBuffer(
                    buffer,
                    screenshotResult.getColorSpace());
            if (wrapped == null || wrapped.isRecycled()) {
                return null;
            }
            return wrapped.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable t) {
            Log.d(TAG, "screenshot bitmap conversion failed", t);
            return null;
        } finally {
            buffer.close();
        }
    }

    private void applyUnlockEffectBackgroundSource(Bitmap bitmap, String sourceName) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        if (unlockEffectRenderer instanceof BackgroundSourceRenderer) {
            ((BackgroundSourceRenderer) unlockEffectRenderer)
                    .setBackgroundSourceBitmap(bitmap, sourceName);
        }
    }

    private void invalidateUnlockEffectBackgroundSource() {
        colorScreenshotInFlight = false;
        colorScreenshotAttemptedThisSession = false;
        preloadUnlockEffectRenderer();
        if (unlockEffectRenderer instanceof BackgroundSourceRenderer) {
            ((BackgroundSourceRenderer) unlockEffectRenderer).clearBackgroundSourceBitmap();
        }
        Log.i(TAG, "unlock effect background map refresh requested");
    }

    private boolean effectUsesScreenshotBackground(int effect) {
        return effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS;
    }

    private void syncTouchDebugOverlay() {
        syncTouchDebugOverlay(isFxSurfaceActive(false), true);
    }

    private void syncTouchDebugOverlay(boolean active) {
        syncTouchDebugOverlay(active, true);
    }

    private void syncTouchDebugOverlay(boolean mounted, boolean touchable) {
        if (!OverlayPrefs.debugTouchArea(this) || !mounted) {
            removeTouchDebugOverlay();
            return;
        }
        Rect box = resolveTouchBox();
        if (touchDebugView != null) {
            touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
            touchDebugView.setListeningEnabled(touchable);
            updateTouchDebugLayout(box, touchable);
            return;
        }
        touchDebugView = new TouchDebugView(this);
        touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
        touchDebugView.setListeningEnabled(touchable);
        touchDebugView.setTouchTriggerListener(new TouchDebugView.TouchTriggerListener() {
            @Override
            public void onTouchStarted(float screenX, float screenY) {
                beginUnlockEffectGesture(screenX, screenY);
            }

            @Override
            public void onTouchMoved(float screenX, float screenY,
                    float deltaX, float deltaY, float distance) {
                updateUnlockEffectGesture(screenX, screenY);
            }

            @Override
            public void onTouchEnded(float screenX, float screenY,
                    float deltaX, float deltaY, float distance) {
                finishUnlockEffectGesture(screenX, screenY, distance);
            }

            @Override
            public void onTouchCancelled() {
                cancelUnlockEffectGesture();
            }
        });

        touchDebugParams = new WindowManager.LayoutParams(
                box.width(),
                box.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                touchListenBoxFlags(touchable),
                PixelFormat.TRANSLUCENT);
        touchDebugTouchable = touchable;
        touchDebugParams.gravity = Gravity.TOP | Gravity.START;
        touchDebugParams.x = box.left;
        touchDebugParams.y = box.top;
        touchDebugParams.setTitle("ChargingTouchListenBox");
        windowManager.addView(touchDebugView, touchDebugParams);
        Log.i(TAG, "touch listen box shown left=" + box.left
                + " top=" + box.top
                + " right=" + box.right
                + " bottom=" + box.bottom
                + " touchable=" + touchable);
    }

    private void applyOverlayPrefs() {
        if (overlayView == null) {
            return;
        }
        overlayView.setSeasonMode(OverlayPrefs.seasonMode(this));
        overlayView.setPositionOffset(
                OverlayPrefs.positionOffsetX(this),
                OverlayPrefs.positionOffsetY(this));
        overlayView.setBatteryPercent(batteryPercent);
        overlayView.setDebugRollingCharge(OverlayPrefs.debugRollingCharge(this));
    }

    private void removeOverlay() {
        removeDoodleOverlay();
        destroyUnlockEffectOverlay();
        removeTouchDebugOverlay();
    }

    private void removeDoodleOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
                // The service can be torn down after the window was already removed.
            }
            overlayView = null;
        }
    }

    private void removeUnlockEffectOverlay() {
        stopDebugLensLoop();
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.resetEffect();
        }
        if (unlockEffectOverlayAttached && unlockEffectView != null) {
            try {
                windowManager.removeView(unlockEffectView);
            } catch (RuntimeException ignored) {
                // The service can be torn down after the effect window was already removed.
            }
            unlockEffectOverlayAttached = false;
        }
    }

    private void destroyUnlockEffectOverlay() {
        removeUnlockEffectOverlay();
        if (effectUsesScreenshotBackground(unlockEffectRendererType)) {
            colorScreenshotInFlight = false;
            colorScreenshotAttemptedThisSession = false;
        }
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.destroy();
            unlockEffectRenderer = null;
        }
        unlockEffectView = null;
        unlockEffectRendererType = -1;
    }

    private void removeTouchDebugOverlay() {
        if (touchDebugView != null) {
            try {
                windowManager.removeView(touchDebugView);
            } catch (RuntimeException ignored) {
                // The service can be torn down after the debug window was already removed.
            }
            touchDebugView = null;
            touchDebugParams = null;
            touchDebugTouchable = false;
        }
    }

    private void updateTouchDebugLayout(Rect box, boolean touchable) {
        if (touchDebugView == null || touchDebugParams == null) {
            return;
        }
        int flags = touchListenBoxFlags(touchable);
        boolean changed = touchDebugParams.x != box.left
                || touchDebugParams.y != box.top
                || touchDebugParams.width != box.width()
                || touchDebugParams.height != box.height()
                || touchDebugParams.flags != flags
                || touchDebugTouchable != touchable;
        if (!changed) {
            return;
        }
        touchDebugParams.x = box.left;
        touchDebugParams.y = box.top;
        touchDebugParams.width = box.width();
        touchDebugParams.height = box.height();
        touchDebugParams.flags = flags;
        touchDebugTouchable = touchable;
        windowManager.updateViewLayout(touchDebugView, touchDebugParams);
        Log.i(TAG, "touch listen box updated left=" + box.left
                + " top=" + box.top
                + " right=" + box.right
                + " bottom=" + box.bottom
                + " touchable=" + touchable);
    }

    private int touchListenBoxFlags(boolean touchable) {
        int flags = TOUCH_LISTEN_BOX_BASE_FLAGS;
        if (!touchable) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        return flags;
    }

    private Rect resolveTouchBox() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = Math.max(dp(48), metrics.widthPixels);
        int screenHeight = Math.max(dp(48), metrics.heightPixels);

        int left;
        int top;
        int right;
        int bottom;
        if (OverlayPrefs.touchBoxConfigured(this)) {
            left = OverlayPrefs.touchBoxLeft(this);
            top = OverlayPrefs.touchBoxTop(this);
            right = OverlayPrefs.touchBoxRight(this);
            bottom = OverlayPrefs.touchBoxBottom(this);
        } else {
            left = OverlayPrefs.DEFAULT_TOUCH_BOX_LEFT;
            top = OverlayPrefs.DEFAULT_TOUCH_BOX_TOP;
            right = OverlayPrefs.DEFAULT_TOUCH_BOX_RIGHT;
            bottom = OverlayPrefs.DEFAULT_TOUCH_BOX_BOTTOM;
        }

        int minSize = dp(48);
        left = clamp(left, 0, screenWidth - minSize);
        top = clamp(top, 0, screenHeight - minSize);
        right = clamp(right, left + minSize, screenWidth);
        bottom = clamp(bottom, top + minSize, screenHeight);
        return new Rect(left, top, right, bottom);
    }

    private void syncDebugLensLoop() {
        if (!OverlayPrefs.debugLensLoop(this)) {
            stopDebugLensLoop();
            return;
        }
        if (!debugLensLoopScheduled && !debugLensLoopGestureActive) {
            scheduleDebugLensLoop(0L);
        }
    }

    private void scheduleDebugLensLoop(long delayMs) {
        if (!OverlayPrefs.debugLensLoop(this)
                || unlockEffectRenderer == null
                || !isFxSurfaceActive()) {
            return;
        }
        if (debugLensLoopScheduled) {
            return;
        }
        debugLensLoopScheduled = true;
        handler.postDelayed(debugLensLoopRunnable, delayMs);
    }

    private void runDebugLensLoopFrame() {
        debugLensLoopScheduled = false;
        if (!OverlayPrefs.debugLensLoop(this)
                || unlockEffectRenderer == null
                || !isFxSurfaceActive()) {
            stopDebugLensLoop();
            return;
        }

        Rect box = resolveTouchBox();
        float centerX = box.exactCenterX();
        float centerY = box.exactCenterY();
        float insetX = Math.max(dp(12), box.width() * 0.18f);
        float insetY = Math.max(dp(12), box.height() * 0.18f);
        float left = box.left + insetX;
        float right = box.right - insetX;
        float top = box.top + insetY;
        float bottom = box.bottom - insetY;
        if (left > right) {
            left = centerX;
            right = centerX;
        }
        if (top > bottom) {
            top = centerY;
            bottom = centerY;
        }

        float x = centerX;
        float y = centerY;
        switch (debugLensLoopFrame) {
            case 0:
                unlockEffectRenderer.beginGesture(centerX, centerY);
                debugLensLoopGestureActive = true;
                Log.i(TAG, "unlock effect debug loop begin box="
                        + box.left + "," + box.top + "," + box.right + "," + box.bottom
                        + " center=" + Math.round(centerX) + "," + Math.round(centerY));
                debugLensLoopFrame = 1;
                scheduleDebugLensLoop(DEBUG_LOOP_STEP_DELAY_MS);
                return;
            case 1:
                x = right;
                y = centerY;
                break;
            case 2:
                x = right;
                y = bottom;
                break;
            case 3:
                x = centerX;
                y = top;
                break;
            case 4:
                x = left;
                y = bottom;
                break;
            case 5:
                x = centerX;
                y = centerY;
                break;
            default:
                unlockEffectRenderer.finishGesture(false);
                debugLensLoopGestureActive = false;
                debugLensLoopFrame = 0;
                Log.i(TAG, "unlock effect debug loop end");
                scheduleDebugLensLoop(DEBUG_LOOP_RESTART_DELAY_MS);
                return;
        }

        unlockEffectRenderer.updateGesture(x, y);
        Log.i(TAG, "unlock effect debug loop frame=" + debugLensLoopFrame
                + " point=" + Math.round(x) + "," + Math.round(y));
        debugLensLoopFrame++;
        scheduleDebugLensLoop(DEBUG_LOOP_STEP_DELAY_MS);
    }

    private void stopDebugLensLoop() {
        handler.removeCallbacks(debugLensLoopRunnable);
        debugLensLoopScheduled = false;
        debugLensLoopFrame = 0;
        if (debugLensLoopGestureActive && unlockEffectRenderer != null) {
            unlockEffectRenderer.cancelGesture();
        }
        debugLensLoopGestureActive = false;
    }

    private boolean isFxSurfaceActive() {
        return isFxSurfaceActive(true);
    }

    private boolean isFxSurfaceActive(boolean contentAware) {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = isLockscreenLocked(contentAware);
        boolean home = interactive && !locked && isHomePackage(lastWindowPackage);
        boolean pinEntryActive = pinEntryRequested || pinEntrySurfaceVisible;
        boolean blockedSurfaceActive = pinEntryActive || notificationShadeVisible;
        boolean showDoodle = isDoodleVisible(interactive, locked, home, blockedSurfaceActive);
        return interactive
                && locked
                && !blockedSurfaceActive
                && !showDoodle
                && OverlayPrefs.unlockEffectEnabled(this);
    }

    private boolean isLockscreenLocked(boolean contentAware) {
        if (keyguardManager == null) {
            return false;
        }
        if (keyguardManager.isKeyguardLocked()) {
            return true;
        }
        return !contentAware && (unlockTouchCachedWhileScreenOff || keyguardManager.isDeviceLocked());
    }

    private boolean isDoodleVisible(boolean interactive, boolean locked, boolean home,
            boolean blockedSurfaceActive) {
        boolean showDoodleForSurface = (!interactive && OverlayPrefs.showAod(this))
                || (interactive && locked && !blockedSurfaceActive && OverlayPrefs.showLock(this))
                || (home && OverlayPrefs.showHome(this));
        return charging && OverlayPrefs.showDoodle(this) && showDoodleForSurface;
    }

    private void beginUnlockEffectGesture(float screenX, float screenY) {
        if (pinEntryRequested || pinEntrySurfaceVisible || notificationShadeVisible) {
            Log.i(TAG, "unlock effect gesture blocked by content surface");
            evaluateVisibility("gesture_blocked_surface");
            return;
        }
        handler.removeCallbacks(pinEntryRunnable);
        handler.removeCallbacks(pinEntrySwipeRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        stopDebugLensLoop();
        unlockEffectAnchorX = screenX;
        unlockEffectAnchorY = screenY;
        syncUnlockEffectOverlay();
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.beginGesture(unlockEffectAnchorX, unlockEffectAnchorY);
        }
        Log.i(TAG, "unlock effect gesture begin touch="
                + Math.round(screenX) + "," + Math.round(screenY)
                + " anchor=" + Math.round(unlockEffectAnchorX)
                + "," + Math.round(unlockEffectAnchorY)
                + " type=" + OverlayPrefs.unlockEffect(this));
    }

    private void updateUnlockEffectGesture(float screenX, float screenY) {
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.updateGesture(screenX, screenY);
        }
    }

    private void finishUnlockEffectGesture(float screenX, float screenY, float distance) {
        boolean unlockTriggered = distance >= dp(UNLOCK_TRIGGER_DISTANCE_DP);
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.updateGesture(screenX, screenY);
            unlockEffectRenderer.finishGesture(unlockTriggered);
        }
        if (unlockTriggered) {
            schedulePinEntry();
        }
        Log.i(TAG, "unlock effect gesture end effect="
                + Math.round(screenX) + "," + Math.round(screenY)
                + " distance=" + Math.round(distance)
                + " threshold=" + dp(UNLOCK_TRIGGER_DISTANCE_DP)
                + " unlockTriggered=" + unlockTriggered);
    }

    private void cancelUnlockEffectGesture() {
        handler.removeCallbacks(pinEntryRunnable);
        handler.removeCallbacks(pinEntrySwipeRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.cancelGesture();
        }
        Log.i(TAG, "unlock effect gesture cancelled");
    }

    private void schedulePinEntry() {
        long delayMs = pinEntryDelayMs();
        handler.removeCallbacks(pinEntryRunnable);
        handler.postDelayed(pinEntryRunnable, delayMs);
        Log.i(TAG, "pin entry scheduled delayMs=" + delayMs
                + " effect=" + OverlayPrefs.unlockEffect(this));
    }

    private long pinEntryDelayMs() {
        int effect = OverlayPrefs.unlockEffect(this);
        if (effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS) {
            return PIN_ENTRY_DELAY_POPPING_COLOURS_MS;
        }
        if (effect == OverlayPrefs.EFFECT_WATERCOLOUR) {
            return PIN_ENTRY_DELAY_WATERCOLOUR_MS;
        }
        if (effect == OverlayPrefs.EFFECT_COLOUR_DROPLET) {
            return PIN_ENTRY_DELAY_COLOUR_DROPLET_MS;
        }
        if (effect == OverlayPrefs.EFFECT_SPARKLING_BUBBLES) {
            return PIN_ENTRY_DELAY_SPARKLING_BUBBLES_MS;
        }
        return PIN_ENTRY_DELAY_LENS_FLARE_MS;
    }

    private void openPinEntry() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        if (!interactive) {
            Log.i(TAG, "pin entry skipped interactive=false locked=" + locked);
            clearBlockedSurfaceState();
            return;
        }
        if (!locked) {
            Log.w(TAG, "pin entry swipe continuing while keyguard reports locked=false");
        }

        pinEntryRequested = true;
        removeTouchDebugOverlay();
        scheduleUnlockEffectCleanup();
        handler.removeCallbacks(pinEntrySwipeRunnable);
        handler.postDelayed(pinEntrySwipeRunnable, PIN_ENTRY_SWIPE_START_DELAY_MS);
        Log.i(TAG, "pin entry swipe queued delayMs=" + PIN_ENTRY_SWIPE_START_DELAY_MS);
        evaluateVisibility("pin_entry_requested");
    }

    private void runPinEntrySwipe() {
        boolean accepted = performPinEntrySwipe();
        if (!accepted) {
            clearBlockedSurfaceState();
            evaluateVisibility("pin_entry_swipe_rejected");
        }
    }

    private void scheduleUnlockEffectCleanup() {
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        handler.postDelayed(pinEntryEffectCleanupRunnable, PIN_ENTRY_EFFECT_CLEANUP_DELAY_MS);
        Log.i(TAG, "unlock effect cleanup scheduled delayMs="
                + PIN_ENTRY_EFFECT_CLEANUP_DELAY_MS);
    }

    private boolean performPinEntrySwipe() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "pin entry swipe unavailable below Android N");
            return false;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float x = metrics.widthPixels / 2f;
        float startY = metrics.heightPixels * 0.82f;
        float endY = metrics.heightPixels * 0.28f;

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, PIN_ENTRY_SWIPE_DURATION_MS);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.i(TAG, "pin entry swipe completed");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, "pin entry swipe cancelled");
            }
        }, handler);
        Log.i(TAG, "pin entry swipe dispatched accepted=" + accepted);
        return accepted;
    }

    private void loadHomePackages() {
        homePackages.clear();
        homePackages.add("com.sec.android.app.launcher");
        homePackages.add("com.android.launcher");
        homePackages.add("com.google.android.apps.nexuslauncher");
        homePackages.add("app.lawnchair");
        homePackages.add("com.microsoft.launcher");
    }

    private boolean isHomePackage(String packageName) {
        return packageName != null && homePackages.contains(packageName);
    }

    private int detectContentBlockedSurfaces() {
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (RuntimeException e) {
            Log.w(TAG, "content window scan failed", e);
            return 0;
        }
        if (windows == null || windows.isEmpty()) {
            return 0;
        }
        int blockedSurfaces = 0;
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window == null) {
                continue;
            }
            if (!isActiveOrFocusedWindow(window)) {
                continue;
            }
            CharSequence title = windowTitle(window);
            if (containsStrongPinEntryKeyword(title)) {
                blockedSurfaces |= BLOCKED_SURFACE_PIN_ENTRY;
            }
            if (containsStrongNotificationShadeKeyword(title)
                    || containsNotificationShadeTextKeyword(title)) {
                blockedSurfaces |= BLOCKED_SURFACE_NOTIFICATION_SHADE;
            }

            AccessibilityNodeInfo root = null;
            try {
                root = window.getRoot();
                if (root != null && pinEntryRequested
                        && isKeyboardPackage(root.getPackageName())) {
                    blockedSurfaces |= BLOCKED_SURFACE_PIN_ENTRY;
                    continue;
                }
                if (root == null || !isSystemKeyguardNode(root)) {
                    continue;
                }
                if (containsPinEntryNode(root, 0)) {
                    blockedSurfaces |= BLOCKED_SURFACE_PIN_ENTRY;
                }
                if (containsNotificationShadeNode(root, 0)) {
                    blockedSurfaces |= BLOCKED_SURFACE_NOTIFICATION_SHADE;
                }
                if ((blockedSurfaces & BLOCKED_SURFACE_PIN_ENTRY) != 0
                        && (blockedSurfaces & BLOCKED_SURFACE_NOTIFICATION_SHADE) != 0) {
                    return blockedSurfaces;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "content node scan failed", e);
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
        }
        return blockedSurfaces;
    }

    private boolean isActiveOrFocusedWindow(AccessibilityWindowInfo window) {
        return window != null && (window.isActive() || window.isFocused());
    }

    private boolean containsPinEntryNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > PIN_ENTRY_NODE_SCAN_DEPTH) {
            return false;
        }
        if (nodeMatchesPinEntry(node)) {
            return true;
        }
        int childCount = Math.min(node.getChildCount(), PIN_ENTRY_NODE_SCAN_CHILD_LIMIT);
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (containsPinEntryNode(child, depth + 1)) {
                    return true;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "pin entry child scan failed", e);
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private boolean containsNotificationShadeNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > PIN_ENTRY_NODE_SCAN_DEPTH) {
            return false;
        }
        if (nodeMatchesNotificationShade(node)) {
            return true;
        }
        int childCount = Math.min(node.getChildCount(), PIN_ENTRY_NODE_SCAN_CHILD_LIMIT);
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (containsNotificationShadeNode(child, depth + 1)) {
                    return true;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "notification shade child scan failed", e);
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private boolean nodeMatchesPinEntry(AccessibilityNodeInfo node) {
        return containsStrongPinEntryKeyword(node.getViewIdResourceName())
                || containsStrongPinEntryKeyword(node.getClassName())
                || containsPinEntryTextKeyword(node.getText())
                || containsPinEntryTextKeyword(node.getContentDescription());
    }

    private boolean nodeMatchesNotificationShade(AccessibilityNodeInfo node) {
        return containsStrongNotificationShadeKeyword(node.getViewIdResourceName())
                || containsStrongNotificationShadeKeyword(node.getClassName())
                || containsNotificationShadeTextKeyword(node.getText())
                || containsNotificationShadeTextKeyword(node.getContentDescription());
    }

    private boolean isSystemKeyguardNode(AccessibilityNodeInfo node) {
        if (node.getPackageName() == null) {
            return true;
        }
        return isSystemKeyguardPackage(node.getPackageName());
    }

    private boolean isSystemKeyguardPackage(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.toString();
        return SYSTEM_UI_PACKAGE.equals(value) || AOD_PACKAGE.equals(value);
    }

    private boolean containsStrongPinEntryKeyword(CharSequence value) {
        return containsKeyword(value, PIN_ENTRY_STRONG_KEYWORDS);
    }

    private boolean containsPinEntryTextKeyword(CharSequence value) {
        return containsKeyword(value, PIN_ENTRY_TEXT_KEYWORDS);
    }

    private boolean containsStrongNotificationShadeKeyword(CharSequence value) {
        return containsKeyword(value, NOTIFICATION_SHADE_STRONG_KEYWORDS);
    }

    private boolean containsNotificationShadeTextKeyword(CharSequence value) {
        return containsKeyword(value, NOTIFICATION_SHADE_TEXT_KEYWORDS);
    }

    private boolean containsNotificationShadeTextKeyword(List<CharSequence> values) {
        if (values == null) {
            return false;
        }
        for (int i = 0; i < values.size(); i++) {
            if (containsNotificationShadeTextKeyword(values.get(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isKeyboardPinEntryEvent(AccessibilityEvent event) {
        if (event == null || (!pinEntryRequested && !pinEntrySurfaceSeen)) {
            return false;
        }
        if (powerManager != null && !powerManager.isInteractive()) {
            return false;
        }
        return isLockscreenLocked(false) && isKeyboardPackage(event.getPackageName());
    }

    private boolean isKeyboardPackage(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.toString();
        return SAMSUNG_KEYBOARD_PACKAGE.equals(value)
                || GOOGLE_KEYBOARD_PACKAGE.equals(value)
                || AOSP_KEYBOARD_PACKAGE.equals(value);
    }

    private boolean containsPinEntryTextKeyword(List<CharSequence> values) {
        if (values == null) {
            return false;
        }
        for (int i = 0; i < values.size(); i++) {
            if (containsPinEntryTextKeyword(values.get(i))) {
                return true;
            }
        }
        return false;
    }

    private CharSequence windowTitle(AccessibilityWindowInfo window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null;
        }
        return window.getTitle();
    }

    private boolean containsKeyword(CharSequence value, String[] keywords) {
        if (value == null) {
            return false;
        }
        String normalized = value.toString().toLowerCase();
        for (int i = 0; i < keywords.length; i++) {
            if (normalized.contains(keywords[i])) {
                return true;
            }
        }
        return false;
    }

    private void refreshChargingState() {
        updateChargingState(registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
    }

    private void updateChargingState(Intent batteryIntent) {
        boolean wasCharging = charging;
        charging = isBatteryCharging(batteryIntent);
        batteryPercent = extractBatteryPercent(batteryIntent, batteryPercent);
        if (overlayView != null) {
            overlayView.setBatteryPercent(batteryPercent);
        }
        if (!wasCharging && charging && overlayView != null && OverlayPrefs.debugRollingCharge(this)) {
            overlayView.resetChargeCycle();
        }
    }

    private int extractBatteryPercent(Intent batteryIntent, int fallback) {
        if (batteryIntent == null) {
            return fallback;
        }
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            return fallback;
        }
        return Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
    }

    private boolean isBatteryCharging(Intent batteryIntent) {
        if (batteryIntent == null) {
            return false;
        }
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
                || plugged != 0;
    }

    private String eventTypeName(AccessibilityEvent event) {
        if (event == null) {
            return "null";
        }
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                return "window_state";
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
                return "windows";
            default:
                return String.valueOf(event.getEventType());
        }
    }

    private void logSystemUiEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }
        if (!isSystemKeyguardPackage(event.getPackageName())) {
            return;
        }
        int type = event.getEventType();
        if (!pinEntryRequested
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }
        CharSequence className = event.getClassName();
        CharSequence contentDescription = event.getContentDescription();
        Log.i(TAG, "event detail type=" + eventTypeName(event)
                + " class=" + (className == null ? "-" : className)
                + " text=" + event.getText()
                + " desc=" + (contentDescription == null ? "-" : contentDescription)
                + " pinEntryRequested=" + pinEntryRequested);
    }

    private boolean isPinEntryEvent(AccessibilityEvent event) {
        if (event == null || !isSystemKeyguardPackage(event.getPackageName())) {
            return false;
        }
        return containsStrongPinEntryKeyword(event.getClassName())
                || containsPinEntryTextKeyword(event.getText())
                || containsPinEntryTextKeyword(event.getContentDescription());
    }

    private boolean isNotificationShadeEvent(AccessibilityEvent event) {
        if (event == null || !isSystemKeyguardPackage(event.getPackageName())) {
            return false;
        }
        return containsStrongNotificationShadeKeyword(event.getClassName())
                || containsNotificationShadeTextKeyword(event.getText())
                || containsNotificationShadeTextKeyword(event.getContentDescription());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

