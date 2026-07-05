package com.codex.lle;

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
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.media.AudioManager;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class ChargingAccessibilityService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "ChargingA11y";
    private static final String ACTION_BENCHMARK_TOUCH =
            "com.codex.lle.BENCHMARK_TOUCH";
    private static final int UNLOCK_TRIGGER_DISTANCE_DP = 120;
    private static final long PIN_ENTRY_DELAY_LENS_FLARE_MS = 400L;
    private static final long PIN_ENTRY_DELAY_POPPING_COLOURS_MS = 200L;
    private static final long PIN_ENTRY_DELAY_WATERCOLOUR_MS = 250L;
    private static final long PIN_ENTRY_DELAY_COLOUR_DROPLET_MS = 260L;
    private static final long PIN_ENTRY_DELAY_SPARKLING_BUBBLES_MS = 260L;
    private static final long PIN_ENTRY_SWIPE_START_DELAY_MS = 60L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_MS = 900L;
    private static final long PIN_ENTRY_SWIPE_DURATION_MS = 260L;
    private static final long UNLOCK_AFFORDANCE_DELAY_MS = 500L;
    private static final long DEBUG_LOOP_STEP_DELAY_MS = 120L;
    private static final long DEBUG_LOOP_RESTART_DELAY_MS = 620L;
    private static final long SCREEN_ON_REFRESH_FAST_MS = 35L;
    private static final long SCREEN_ON_REFRESH_SETTLE_MS = 140L;
    private static final long LOCKSCREEN_SESSION_FAST_POLL_MS = 10L;
    private static final long LOCKSCREEN_SESSION_CONTENT_POLL_MS = 40L;
    private static final long SCREEN_OFF_PREARM_FAST_MS = 80L;
    private static final long SCREEN_OFF_PREARM_SETTLE_MS = 180L;
    private static final long SCREEN_OFF_PREARM_LATE_MS = 420L;
    private static final long SCREEN_OFF_PREARM_FINAL_MS = 900L;
    private static final long SCREEN_OFF_PREARM_DOZE_MS = 2200L;
    private static final long SCREEN_OFF_PREARM_SUSPEND_MS = 5200L;
    private static final long BLOCKED_SURFACE_CLEAR_GRACE_MS = 120L;
    private static final long TOUCH_BOX_SCREENSHOT_DELAY_MS = 2000L;
    private static final long TOUCH_BOX_SCREENSHOT_OVERLAY_CLEAR_MS = 180L;
    private static final long UNLOCK_EFFECT_SCREENSHOT_RETRY_MS = 90L;
    private static final long UNLOCK_EFFECT_SCREENSHOT_MIN_SCREEN_ON_MS = 360L;
    private static final long S3_RIPPLE_SCREENSHOT_MIN_SCREEN_ON_MS = 1400L;
    private static final long S5_POPPING_SCREENSHOT_MIN_SCREEN_ON_MS = 700L;
    private static final long COLOUR_DROPLET_SCREENSHOT_MIN_SCREEN_ON_MS = 700L;
    private static final long SPARKLING_BUBBLES_SCREENSHOT_MIN_SCREEN_ON_MS = 700L;
    private static final long S3_RIPPLE_SURFACE_REATTACH_MIN_MS = 280L;
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
    private static final String[] CALL_SURFACE_PACKAGES = {
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.app.telephonyui",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.android.phone",
            "com.sec.phone"
    };
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
    private final Runnable touchBoxScreenshotDelayRunnable = new Runnable() {
        @Override
        public void run() {
            runTouchBoxScreenshotDelay();
        }
    };
    private final Runnable touchBoxScreenshotCaptureRunnable = new Runnable() {
        @Override
        public void run() {
            runTouchBoxScreenshotCapture();
        }
    };
    private final Runnable unlockEffectBackgroundRetryRunnable = new Runnable() {
        @Override
        public void run() {
            refreshUnlockEffectBackgroundSourceIfNeeded("background_retry");
        }
    };
    private WindowManager windowManager;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    private AudioManager audioManager;
    private SharedPreferences prefs;
    private SeasonalDoodleView overlayView;
    private TouchDebugView touchDebugView;
    private WindowManager.LayoutParams touchDebugParams;
    private boolean touchDebugTouchable;
    private UnlockEffectRenderer unlockEffectRenderer;
    private View unlockEffectView;
    private int unlockEffectRendererType = -1;
    private boolean unlockEffectOverlayAttached;
    private boolean doodleOverlayAttached;
    private float unlockEffectAnchorX;
    private float unlockEffectAnchorY;
    private boolean debugLensLoopScheduled;
    private boolean debugLensLoopGestureActive;
    private int debugLensLoopFrame;
    private final Set<String> homePackages = new HashSet<String>();
    private final Set<String> callPackages = new HashSet<String>();
    private String lastWindowPackage;
    private boolean charging;
    private int batteryPercent;
    private boolean pinEntryPending;
    private boolean pinEntryRequested;
    private boolean pinEntrySurfaceSeen;
    private boolean pinEntrySurfaceVisible;
    private boolean notificationShadeVisible;
    private boolean unlockTouchCachedWhileScreenOff;
    private boolean unlockAffordancePending;
    private boolean unlockAffordanceShownThisWake;
    private boolean lastInteractive;
    private boolean unlockFxVisible;
    private boolean lockscreenSessionPolling;
    private long nextContentAwarePollAt;
    private long pinEntryLastSeenAt;
    private long notificationShadeLastSeenAt;
    private long lastScreenOnAt;
    private long lastScreenOffAt;
    private long unlockEffectBackgroundCapturedAt;
    private long lastS3SurfaceReattachAt;
    private int unlockEffectBackgroundEffect = -1;
    private boolean colorScreenshotInFlight;
    private boolean colorScreenshotAttemptedThisSession;
    private boolean skipCachedEffectBackgroundLoad;
    private boolean rootTouchBenchmarkRunning;
    private boolean touchBoxScreenshotScheduled;
    private boolean touchBoxScreenshotInFlight;

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
                lastInteractive = false;
                lastScreenOffAt = SystemClock.uptimeMillis();
                Log.i(TAG, "screen off broadcast interactive="
                        + (powerManager == null || powerManager.isInteractive())
                        + " locked=" + isLockscreenLocked(false));
                unlockAffordancePending = false;
                unlockAffordanceShownThisWake = false;
                lastScreenOnAt = 0L;
                stopLockscreenSessionPolling();
                clearBlockedSurfaceState();
                resetUnlockEffectBackgroundSession(true);
                handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
                cacheUnlockTouchForScreenOff();
                scheduleScreenOffPrearm();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                unlockAffordancePending = false;
                unlockAffordanceShownThisWake = false;
                stopLockscreenSessionPolling();
                handler.removeCallbacks(screenOffPrearmRunnable);
                handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
                clearBlockedSurfaceState();
                unlockTouchCachedWhileScreenOff = false;
                resetUnlockEffectBackgroundSession(true);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                lastInteractive = true;
                lastScreenOnAt = SystemClock.uptimeMillis();
                Log.i(TAG, "screen on broadcast cached=" + unlockTouchCachedWhileScreenOff
                        + " interactive=" + (powerManager == null || powerManager.isInteractive())
                        + " locked=" + isLockscreenLocked(false)
                        + " sinceScreenOffMs=" + elapsedSinceScreenOff());
                unlockAffordancePending = true;
                handler.removeCallbacks(screenOffPrearmRunnable);
                if (unlockTouchCachedWhileScreenOff) {
                    notificationShadeVisible = false;
                    notificationShadeLastSeenAt = 0L;
                    syncUnlockEffectOverlay();
                    if (unlockEffectRenderer != null) {
                        unlockEffectRenderer.warmUp();
                    }
                    syncTouchDebugOverlay(true, true);
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

    private final BroadcastReceiver benchmarkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_BENCHMARK_TOUCH.equals(intent.getAction())) {
                startRootTouchBenchmark();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "connected");
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        lastInteractive = powerManager == null || powerManager.isInteractive();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        prefs = OverlayPrefs.get(this);
        OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this);
        applyPerfDefaultsOnce();
        ensureInternalTouchAreaEnabled();
        prefs.registerOnSharedPreferenceChangeListener(this);
        loadHomePackages();
        loadCallPackages();
        configurePassiveService();
        refreshChargingState();
        ensureDoodleLoaded();
        preloadUnlockEffectRenderer();
        registerScreenReceiver();
        if (powerManager != null && !powerManager.isInteractive()) {
            scheduleScreenOffPrearm();
        }
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
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (event != null && isCallPackage(event.getPackageName())) {
            unlockAffordancePending = false;
            unlockTouchCachedWhileScreenOff = false;
            stopDebugLensLoop();
            removeDoodleOverlay();
            removeUnlockEffectOverlay();
            removeTouchDebugOverlay();
            evaluateVisibility("event:" + eventTypeName(event) + ":call_surface", false);
            handler.removeCallbacks(screenOnRefreshRunnable);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_FAST_MS);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_SETTLE_MS);
            return;
        }
        boolean pinEntryEvent = isPinEntryEvent(event);
        boolean keyboardPinEntryEvent = isKeyboardPinEntryEvent(event);
        if (interactive && (pinEntryEvent || keyboardPinEntryEvent)) {
            boolean wasPinEntryRequested = pinEntryRequested;
            pinEntryPending = false;
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
        } else if (interactive && isNotificationShadeEvent(event)) {
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
        if (OverlayPrefs.MASTER_ENABLED.equals(key) && !OverlayPrefs.masterEnabled(this)) {
            stopAllRuntimeSurfaces();
        }
        if (OverlayPrefs.POPPING_COLOR_REFRESH_TOKEN.equals(key)) {
            invalidateUnlockEffectBackgroundSource();
        }
        if (OverlayPrefs.SHOW_DOODLE.equals(key) && !OverlayPrefs.showDoodle(this)) {
            destroyDoodleOverlay();
        } else if (OverlayPrefs.SHOW_DOODLE.equals(key)) {
            ensureDoodleLoaded();
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
        if ((OverlayPrefs.DEBUG_TOUCH_TRANSPARENT.equals(key)
                || OverlayPrefs.DEBUG_TOUCH_STANDBY.equals(key))
                && touchDebugView != null) {
            touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
            syncTouchDebugOverlay();
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
        if (OverlayPrefs.TOUCH_BOX_CAPTURE_REQUEST_ID.equals(key)) {
            touchBoxScreenshotScheduled = false;
            handler.removeCallbacks(touchBoxScreenshotDelayRunnable);
            handler.removeCallbacks(touchBoxScreenshotCaptureRunnable);
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
        try {
            unregisterReceiver(benchmarkReceiver);
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

        IntentFilter benchmarkFilter = new IntentFilter();
        benchmarkFilter.addAction(ACTION_BENCHMARK_TOUCH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(benchmarkReceiver, benchmarkFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(benchmarkReceiver, benchmarkFilter);
        }
    }

    private void startRootTouchBenchmark() {
        if (prefs == null
                || !prefs.getBoolean(OverlayPrefs.ROOT_DEBUG_ENABLED, false)
                || !prefs.getBoolean(OverlayPrefs.ROOT_TOUCH_CAPTURE_TEST_ENABLED, false)) {
            Log.i(TAG, "root touch benchmark ignored; root debug/touch test disabled");
            return;
        }
        if (rootTouchBenchmarkRunning) {
            Log.i(TAG, "root touch benchmark already running");
            return;
        }
        rootTouchBenchmarkRunning = true;
        Log.i(TAG, "root touch benchmark start durationMs=8000");
        new Thread(new Runnable() {
            @Override
            public void run() {
                RootDebugTools.Result result =
                        RootDebugTools.captureTouchEvents(
                                ChargingAccessibilityService.this,
                                8000);
                rootTouchBenchmarkRunning = false;
                Log.i(TAG, "root touch benchmark done success=" + result.success
                        + " message=" + result.message);
            }
        }, "LLE-root-touch-benchmark").start();
    }

    private void scheduleScreenOnRefreshes() {
        handler.removeCallbacks(screenOnRefreshRunnable);
        handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_FAST_MS);
        handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_SETTLE_MS);
    }

    private void handleInteractiveLockscreenWake(String reason, boolean locked) {
        if (!locked || !OverlayPrefs.unlockEffectEnabled(this)) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        boolean cached = unlockTouchCachedWhileScreenOff;
        lastScreenOnAt = now;
        unlockAffordancePending = true;
        handler.removeCallbacks(screenOffPrearmRunnable);
        notificationShadeVisible = false;
        notificationShadeLastSeenAt = 0L;
        if (cached) {
            syncUnlockEffectOverlay();
            if (unlockEffectRenderer != null) {
                unlockEffectRenderer.warmUp();
            }
            syncTouchDebugOverlay(true, true);
        }
        unlockTouchCachedWhileScreenOff = false;
        scheduleScreenOnRefreshes();
        startLockscreenSessionPolling();
        Log.i(TAG, "interactive lockscreen wake detected reason=" + reason
                + " cached=" + cached
                + " locked=" + locked);
    }

    private void scheduleScreenOffPrearm() {
        handler.removeCallbacks(screenOffPrearmRunnable);
        handler.post(screenOffPrearmRunnable);
        handler.postDelayed(screenOffPrearmRunnable, SCREEN_OFF_PREARM_FAST_MS);
        handler.postDelayed(screenOffPrearmRunnable, SCREEN_OFF_PREARM_SETTLE_MS);
        handler.postDelayed(screenOffPrearmRunnable, SCREEN_OFF_PREARM_LATE_MS);
        handler.postDelayed(screenOffPrearmRunnable, SCREEN_OFF_PREARM_FINAL_MS);
        handler.postDelayed(screenOffPrearmRunnable, SCREEN_OFF_PREARM_DOZE_MS);
        handler.postDelayed(screenOffPrearmRunnable, SCREEN_OFF_PREARM_SUSPEND_MS);
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
        if (!OverlayPrefs.masterEnabled(this) || !interactive || !locked) {
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
        long startedAt = SystemClock.uptimeMillis();
        unlockTouchCachedWhileScreenOff = shouldPrearmUnlockEffectForScreenOff();
        unlockAffordanceShownThisWake = false;
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.resetEffect();
        }
        if (unlockTouchCachedWhileScreenOff) {
            if (shouldKeepUnlockEffectOverlayDuringScreenOff()) {
                syncUnlockEffectOverlay();
                if (unlockEffectRenderer != null) {
                    unlockEffectRenderer.warmUp();
                }
            } else {
                removeUnlockEffectOverlay();
            }
            syncTouchDebugOverlay(true, false);
            Log.i(TAG, "unlock effect and touch box cached for screen off"
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt)
                    + " overlayAttached=" + unlockEffectOverlayAttached
                    + " touchBox=" + (touchDebugView != null)
                    + " displayState=" + displayStateName(currentDisplayState()));
        }
    }

    private void prearmUnlockTouchForScreenOff() {
        long startedAt = SystemClock.uptimeMillis();
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (interactive || !shouldPrearmUnlockEffectForScreenOff()) {
            return;
        }
        if (shouldKeepUnlockEffectOverlayDuringScreenOff()) {
            syncUnlockEffectOverlay();
            if (unlockEffectRenderer != null) {
                unlockEffectRenderer.warmUp();
            }
        } else {
            removeUnlockEffectOverlay();
        }
        syncTouchDebugOverlay(true, false);
        unlockTouchCachedWhileScreenOff = touchDebugView != null;
        unlockAffordanceShownThisWake = false;
        if (unlockTouchCachedWhileScreenOff) {
            Log.i(TAG, "unlock touch box prearmed for screen off"
                    + " sinceScreenOffMs=" + elapsedSinceScreenOff()
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt)
                    + " overlayAttached=" + unlockEffectOverlayAttached
                    + " touchBox=" + (touchDebugView != null)
                    + " displayState=" + displayStateName(currentDisplayState()));
        }
    }

    private boolean shouldPrearmUnlockEffectForScreenOff() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = isLockscreenLocked(false);
        boolean screenOffOrLocked = !interactive || locked;
        boolean showDoodle = isDoodleVisible(false, true, false, false);
        return screenOffOrLocked
                && OverlayPrefs.masterEnabled(this)
                && !showDoodle
                && !isCallSurfaceActive()
                && OverlayPrefs.unlockEffectEnabled(this)
                && OverlayPrefs.debugTouchArea(this);
    }

    private boolean shouldKeepUnlockEffectOverlayDuringScreenOff() {
        return unlockEffectRendererType != OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES;
    }

    private void clearBlockedSurfaceState() {
        pinEntryPending = false;
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
        boolean callSurface = isCallSurfaceActive();
        int displayState = currentDisplayState();
        boolean displayOn = displayState == Display.STATE_UNKNOWN || displayState == Display.STATE_ON;
        if (!OverlayPrefs.masterEnabled(this)) {
            stopAllRuntimeSurfaces();
            if (shouldLogVisibility(reason)) {
                Log.i(TAG, "visibility reason=" + reason
                        + " master=false"
                        + " charging=" + charging
                        + " interactive=" + interactive
                        + " locked=" + locked
                        + " pkg=" + lastWindowPackage);
            }
            return;
        }
        if (!displayOn) {
            unlockAffordancePending = false;
            unlockAffordanceShownThisWake = false;
            unlockFxVisible = false;
        }
        if (interactive) {
            if (!lastInteractive) {
                lastInteractive = true;
                handleInteractiveLockscreenWake(reason, locked);
            } else if (locked && unlockTouchCachedWhileScreenOff && !unlockAffordancePending) {
                handleInteractiveLockscreenWake(reason, locked);
            }
        } else {
            lastInteractive = false;
        }
        if (!interactive && unlockTouchCachedWhileScreenOff) {
            if (callSurface) {
                removeDoodleOverlay();
                removeUnlockEffectOverlay();
                removeTouchDebugOverlay();
                unlockTouchCachedWhileScreenOff = false;
                Log.i(TAG, "visibility reason=" + reason
                        + " showDoodle=false showFx=false"
                        + " charging=" + charging
                        + " interactive=false"
                        + " locked=" + locked
                        + " callSurface=true"
                        + " pkg=" + lastWindowPackage);
                return;
            }
            boolean showDoodle = isDoodleVisible(false, locked, false, false);
            if (showDoodle) {
                syncDoodleOverlay();
                removeUnlockEffectOverlay();
                removeTouchDebugOverlay();
                unlockTouchCachedWhileScreenOff = false;
            } else {
                removeDoodleOverlay();
                if (!shouldKeepUnlockEffectOverlayDuringScreenOff()) {
                    removeUnlockEffectOverlay();
                }
                syncTouchDebugOverlay(true, false);
            }
            Log.i(TAG, "visibility reason=" + reason
                    + " showDoodle=" + showDoodle
                    + " showFx=cached"
                    + " charging=" + charging
                    + " interactive=false"
                    + " locked=" + locked
                    + " pinEntryPending=" + pinEntryPending
                    + " pinEntryRequested=" + pinEntryRequested
                    + " pinEntrySurface=" + pinEntrySurfaceVisible
                    + " notificationShade=" + notificationShadeVisible
                    + " callSurface=false"
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
            unlockAffordancePending = false;
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

        boolean pinEntryActive = pinEntryPending || pinEntryRequested || pinEntrySurfaceVisible;
        boolean blockedSurfaceActive = pinEntryActive || notificationShadeVisible || callSurface;
        boolean touchBoxCapturePending = isTouchBoxScreenshotPending();
        boolean hideOverlaysForTouchBoxCapture = touchBoxCapturePending && interactive && locked;
        syncTouchBoxScreenshotCapture(reason, interactive, locked, blockedSurfaceActive);
        boolean aodSurface = AOD_PACKAGE.equals(lastWindowPackage);

        boolean showDoodle = !hideOverlaysForTouchBoxCapture
                && isDoodleVisible(interactive, locked, home, blockedSurfaceActive);
        boolean showFx = interactive
                && displayOn
                && locked
                && !aodSurface
                && !hideOverlaysForTouchBoxCapture
                && !blockedSurfaceActive
                && !showDoodle
                && OverlayPrefs.unlockEffectEnabled(this);

        if (showDoodle) {
            syncDoodleOverlay();
        } else {
            removeDoodleOverlay();
        }

        if (showFx) {
            if (!unlockFxVisible) {
                unlockFxVisible = true;
                unlockAffordanceShownThisWake = false;
            }
            if (!unlockAffordancePending && !unlockAffordanceShownThisWake) {
                unlockAffordancePending = true;
                Log.i(TAG, "unlock affordance armed from visible lockscreen reason=" + reason
                        + " cached=" + unlockTouchCachedWhileScreenOff);
            }
            refreshUnlockEffectBackgroundSourceIfNeeded("showFx:" + reason);
            if (unlockEffectOverlayWaitingForBackground("showFx:" + reason)) {
                syncTouchDebugOverlay(true, true);
                syncDebugLensLoop();
            } else {
                syncUnlockEffectOverlay();
                showPendingUnlockAffordance(reason);
                syncTouchDebugOverlay(true, true);
                syncDebugLensLoop();
            }
        } else {
            unlockFxVisible = false;
            stopDebugLensLoop();
            if (!pinEntryPending && !pinEntryRequested && !pinEntrySurfaceVisible) {
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
                    + " pinEntryPending=" + pinEntryPending
                    + " pinEntryRequested=" + pinEntryRequested
                    + " pinEntrySurface=" + pinEntrySurfaceVisible
                    + " notificationShade=" + notificationShadeVisible
                    + " callSurface=" + callSurface
                    + " touchBoxCapture=" + touchBoxCapturePending
                    + " home=" + home
                    + " pkg=" + lastWindowPackage);
        }
    }

    private boolean shouldLogVisibility(String reason) {
        return reason == null || !reason.startsWith("lockscreen_poll");
    }

    private void syncDoodleOverlay() {
        if (!OverlayPrefs.masterEnabled(this) || !OverlayPrefs.showDoodle(this)) {
            removeDoodleOverlay();
            return;
        }
        ensureDoodleLoaded();
        if (overlayView == null) {
            return;
        }
        if (doodleOverlayAttached) {
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
        params.setTitle("LLEDoodleOverlay");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        applyOverlayPrefs();
        windowManager.addView(overlayView, params);
        doodleOverlayAttached = true;
        Log.i(TAG, "doodle overlay shown");
    }

    private void ensureDoodleLoaded() {
        if (overlayView != null
                || !OverlayPrefs.masterEnabled(this)
                || !OverlayPrefs.showDoodle(this)) {
            return;
        }
        overlayView = new SeasonalDoodleView(this);
        applyOverlayPrefs();
        Log.i(TAG, "doodle view preloaded");
    }

    private void syncUnlockEffectOverlay() {
        long startedAt = SystemClock.uptimeMillis();
        preloadUnlockEffectRenderer();
        if (unlockEffectOverlayAttached && unlockEffectView != null
                && shouldReattachUnlockEffectSurfaceForWarmup()) {
            reattachUnlockEffectOverlay("surface_not_ready");
        }
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
        params.setTitle("LLEUnlockEffect");
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
                : unlockEffectRenderer.effectName())
                + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
    }

    private void preloadUnlockEffectRenderer() {
        long startedAt = SystemClock.uptimeMillis();
        int effect = OverlayPrefs.unlockEffect(this);
        if (unlockEffectRenderer != null && unlockEffectRendererType == effect) {
            return;
        }
        destroyUnlockEffectOverlay();
        unlockEffectRendererType = effect;
        if (effect == OverlayPrefs.EFFECT_S4_LENS_FLARE) {
            unlockEffectRenderer = new LensFlareEffectView(this);
        } else if (effect == OverlayPrefs.EFFECT_S3_RIPPLE) {
            unlockEffectRenderer = new S3RippleMeshEffectView(this);
        } else if (effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS) {
            unlockEffectRenderer = new PoppingColoursEffectView(this);
        } else if (effect == OverlayPrefs.EFFECT_WATERCOLOUR) {
            unlockEffectRenderer = new WatercolorEffectView(this);
        } else if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET) {
            unlockEffectRenderer = new ColourDropletEffectView(this);
        } else if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
            unlockEffectRenderer = new SparklingBubblesEffectView(this);
        } else {
            unlockEffectRenderer = null;
            unlockEffectView = null;
            Log.i(TAG, "unlock effect slot has no renderer type=" + effect);
            return;
        }
        unlockEffectView = unlockEffectRenderer.asView();
        loadCachedUnlockEffectBackgroundSourceIfNeeded(effect);
        Log.i(TAG, "unlock effect renderer preloaded type=" + effect
                + " name=" + unlockEffectRenderer.effectName()
                + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
    }

    private boolean shouldReattachUnlockEffectSurfaceForWarmup() {
        if (!(unlockEffectRenderer instanceof S3RippleMeshEffectView)) {
            return false;
        }
        if (((S3RippleMeshEffectView) unlockEffectRenderer).isGlReadyForFrame()) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        return now - lastS3SurfaceReattachAt >= S3_RIPPLE_SURFACE_REATTACH_MIN_MS;
    }

    private void reattachUnlockEffectOverlay(String reason) {
        if (!unlockEffectOverlayAttached || unlockEffectView == null) {
            return;
        }
        lastS3SurfaceReattachAt = SystemClock.uptimeMillis();
        try {
            windowManager.removeView(unlockEffectView);
        } catch (RuntimeException ignored) {
            // Display state transitions can remove the accessibility window first.
        }
        unlockEffectOverlayAttached = false;
        Log.i(TAG, "unlock effect overlay reattaching reason=" + reason
                + " type=" + unlockEffectRendererType);
    }

    private void showPendingUnlockAffordance(String reason) {
        if (!unlockAffordancePending) {
            return;
        }
        if (unlockEffectRenderer == null
                || unlockEffectView == null
                || !unlockEffectOverlayAttached
                || OverlayPrefs.debugLensLoop(this)) {
            return;
        }
        if (unlockAffordanceWaitingForBackground(reason)) {
            return;
        }
        Rect rect = unlockEffectVisibleRect();
        unlockEffectRenderer.showUnlockAffordance(rect, UNLOCK_AFFORDANCE_DELAY_MS);
        unlockAffordancePending = false;
        unlockAffordanceShownThisWake = true;
        Log.i(TAG, "unlock affordance scheduled reason=" + reason
                + " delayMs=" + UNLOCK_AFFORDANCE_DELAY_MS
                + " rect=" + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom
                + " effect=" + OverlayPrefs.unlockEffect(this));
    }

    private Rect unlockEffectVisibleRect() {
        int width = unlockEffectView == null ? 0 : unlockEffectView.getWidth();
        int height = unlockEffectView == null ? 0 : unlockEffectView.getHeight();
        if (width <= 0 || height <= 0) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            width = Math.max(dp(48), metrics.widthPixels);
            height = Math.max(dp(48), metrics.heightPixels);
        }
        return new Rect(0, 0, width, height);
    }

    private boolean unlockAffordanceWaitingForBackground(String reason) {
        if (!effectUsesScreenshotBackground(unlockEffectRendererType)
                || !(unlockEffectRenderer instanceof BackgroundSourceRenderer)) {
            return false;
        }
        BackgroundSourceRenderer backgroundRenderer =
                (BackgroundSourceRenderer) unlockEffectRenderer;
        if (unlockEffectRendererType == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || unlockEffectRendererType == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
            boolean hasUsableBackground = backgroundRenderer.hasBackgroundSourceBitmap();
            if (unlockEffectRendererType == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
                hasUsableBackground =
                        hasUsableBackground && currentUnlockEffectHasFreshBackground(
                                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES);
            }
            if (colorScreenshotInFlight) {
                Log.i(TAG, "native N5 affordance waiting for screenshot reason="
                        + reason
                        + " effect=" + unlockEffectRendererType);
                return true;
            }
            if (!hasUsableBackground) {
                refreshUnlockEffectBackgroundSourceIfNeeded("affordance:" + reason);
                Log.i(TAG, "native N5 affordance waiting for background reason="
                        + reason
                        + " effect=" + unlockEffectRendererType
                        + " attempted=" + colorScreenshotAttemptedThisSession);
                return true;
            }
            return false;
        }
        if (unlockEffectRendererType == OverlayPrefs.EFFECT_S3_RIPPLE
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S5_POPPING_COLOURS) {
            if (!backgroundRenderer.hasBackgroundSourceBitmap()) {
                refreshUnlockEffectBackgroundSourceIfNeeded("affordance:" + reason);
            }
            // These renderers have a warmed fallback; keep the wake hint responsive while
            // the lockscreen screenshot arrives in parallel.
            return false;
        }
        if (backgroundRenderer.hasBackgroundSourceBitmap()) {
            return false;
        }
        if (!colorScreenshotAttemptedThisSession || colorScreenshotInFlight) {
            refreshUnlockEffectBackgroundSourceIfNeeded("affordance:" + reason);
            Log.i(TAG, "unlock affordance waiting for background reason=" + reason
                    + " inFlight=" + colorScreenshotInFlight
                    + " attempted=" + colorScreenshotAttemptedThisSession);
            return true;
        }
        return false;
    }

    private boolean unlockEffectOverlayWaitingForBackground(String reason) {
        int effect = OverlayPrefs.unlockEffect(this);
        if (effect != OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || !(unlockEffectRenderer instanceof BackgroundSourceRenderer)) {
            return false;
        }
        if (currentUnlockEffectHasFreshBackground(effect)) {
            return false;
        }
        refreshUnlockEffectBackgroundSourceIfNeeded(reason);
        Log.i(TAG, "sparkling overlay waiting for background reason=" + reason
                + " inFlight=" + colorScreenshotInFlight
                + " attempted=" + colorScreenshotAttemptedThisSession);
        return true;
    }

    private void refreshUnlockEffectBackgroundSourceIfNeeded(final String reason) {
        int effect = OverlayPrefs.unlockEffect(this);
        if (!effectUsesScreenshotBackground(effect)
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || colorScreenshotInFlight) {
            return;
        }
        preloadUnlockEffectRenderer();
        if (!(unlockEffectRenderer instanceof BackgroundSourceRenderer)) {
            return;
        }
        BackgroundSourceRenderer backgroundRenderer =
                (BackgroundSourceRenderer) unlockEffectRenderer;
        boolean hasBackground = backgroundRenderer.hasBackgroundSourceBitmap();
        if (colorScreenshotAttemptedThisSession
                && hasBackground
                && (effect != OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || currentUnlockEffectHasFreshBackground(effect))) {
            return;
        }
        if (!shouldRefreshUnlockEffectBackground(effect, hasBackground)) {
            return;
        }
        if (!canCaptureUnlockEffectBackground()) {
            scheduleUnlockEffectBackgroundRetry(reason);
            Log.i(TAG, "unlock effect background capture waiting reason=" + reason
                    + " interactive=" + (powerManager == null || powerManager.isInteractive())
                    + " locked=" + isLockscreenLocked(false)
                    + " displayState=" + displayStateName(currentDisplayState())
                    + " sinceScreenOnMs=" + elapsedSinceScreenOn()
                    + " pkg=" + lastWindowPackage);
            return;
        }
        final int captureEffect = effect;
        colorScreenshotAttemptedThisSession = true;
        colorScreenshotInFlight = true;
        if ((captureEffect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || captureEffect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES)
                && unlockEffectRenderer != null) {
            unlockEffectRenderer.resetEffect();
        }
        Log.i(TAG, "unlock effect background screenshot requested reason=" + reason
                + " sinceScreenOnMs=" + elapsedSinceScreenOn()
                + " displayState=" + displayStateName(currentDisplayState())
                + " effect=" + captureEffect
                + " pkg=" + lastWindowPackage);
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    colorScreenshotInFlight = false;
                    Bitmap bitmap = bitmapFromScreenshot(screenshotResult);
                    if (bitmap == null) {
                        Log.i(TAG, "unlock effect background screenshot empty reason=" + reason);
                        if (shouldRetryUnlockEffectBackgroundCapture(captureEffect)) {
                            colorScreenshotAttemptedThisSession = false;
                            scheduleUnlockEffectBackgroundRetry("empty:" + reason);
                        }
                        showPendingUnlockAffordance("background_empty:" + reason);
                        return;
                    }
                    if (OverlayPrefs.unlockEffect(ChargingAccessibilityService.this)
                            != captureEffect || !canCaptureUnlockEffectBackground()) {
                        Log.i(TAG, "unlock effect background screenshot discarded reason="
                                + reason
                                + " effect=" + captureEffect
                                + " currentEffect="
                                + OverlayPrefs.unlockEffect(ChargingAccessibilityService.this)
                                + " pinEntryPending=" + pinEntryPending
                                + " pinEntryRequested=" + pinEntryRequested
                                + " pinEntrySurface=" + pinEntrySurfaceVisible
                                + " notificationShade=" + notificationShadeVisible
                                + " pkg=" + lastWindowPackage);
                        bitmap.recycle();
                        if (shouldRetryUnlockEffectBackgroundCapture(captureEffect)) {
                            colorScreenshotAttemptedThisSession = false;
                            scheduleUnlockEffectBackgroundRetry("discarded:" + reason);
                        }
                        return;
                    }
                    long now = SystemClock.uptimeMillis();
                    persistTouchBoxScreenshot(bitmap, "effect_background");
                    applyUnlockEffectBackgroundSource(bitmap, "accessibility_screenshot");
                    unlockEffectBackgroundCapturedAt = now;
                    unlockEffectBackgroundEffect = captureEffect;
                    skipCachedEffectBackgroundLoad = false;
                    Log.i(TAG, "unlock effect background screenshot applied reason=" + reason
                            + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                            + " displayState=" + displayStateName(currentDisplayState())
                            + " effect=" + captureEffect
                            + " pkg=" + lastWindowPackage);
                    bitmap.recycle();
                    if (captureEffect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
                        syncUnlockEffectOverlay();
                    }
                    showPendingUnlockAffordance("background:" + reason);
                }

                @Override
                public void onFailure(int errorCode) {
                    colorScreenshotInFlight = false;
                    Log.i(TAG, "unlock effect background screenshot failed code=" + errorCode
                            + " reason=" + reason);
                    if (shouldRetryUnlockEffectBackgroundCapture(captureEffect)) {
                        colorScreenshotAttemptedThisSession = false;
                        scheduleUnlockEffectBackgroundRetry("failed:" + reason);
                    }
                    showPendingUnlockAffordance("background_failed:" + reason);
                }
            });
        } catch (Throwable t) {
            colorScreenshotInFlight = false;
            Log.d(TAG, "unlock effect background screenshot request failed reason=" + reason, t);
            if (shouldRetryUnlockEffectBackgroundCapture(captureEffect)) {
                colorScreenshotAttemptedThisSession = false;
                scheduleUnlockEffectBackgroundRetry("exception:" + reason);
            }
            showPendingUnlockAffordance("background_request_failed:" + reason);
        }
    }

    private boolean shouldRefreshUnlockEffectBackground(int effect, boolean hasBackground) {
        if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
            return !hasBackground
                    || unlockEffectBackgroundEffect != effect
                    || (lastScreenOnAt > 0L && unlockEffectBackgroundCapturedAt < lastScreenOnAt);
        }
        if (!hasBackground || unlockEffectBackgroundEffect != effect) {
            return true;
        }
        return false;
    }

    private boolean canCaptureUnlockEffectBackground() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (!interactive || !isLockscreenLocked(false)) {
            return false;
        }
        if (pinEntryPending || pinEntryRequested || pinEntrySurfaceVisible
                || notificationShadeVisible || isCallSurfaceActive()) {
            return false;
        }
        if (AOD_PACKAGE.equals(lastWindowPackage)) {
            return false;
        }
        int displayState = currentDisplayState();
        if (displayState != Display.STATE_UNKNOWN && displayState != Display.STATE_ON) {
            return false;
        }
        long sinceScreenOn = elapsedSinceScreenOn();
        long minScreenOnMs = unlockEffectScreenshotMinScreenOnMs(
                OverlayPrefs.unlockEffect(this));
        return sinceScreenOn < 0L
                || sinceScreenOn >= minScreenOnMs;
    }

    private boolean shouldRetryUnlockEffectBackgroundCapture(int effect) {
        return effect == OverlayPrefs.EFFECT_S3_RIPPLE
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES;
    }

    private void scheduleUnlockEffectBackgroundRetry(String reason) {
        int effect = OverlayPrefs.unlockEffect(this);
        boolean hasUsableBackground = effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                ? currentUnlockEffectHasFreshBackground(effect)
                : currentUnlockEffectHasBackground(effect);
        if (colorScreenshotInFlight
                || (colorScreenshotAttemptedThisSession && hasUsableBackground)) {
            return;
        }
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        long sinceScreenOn = elapsedSinceScreenOn();
        long delayMs = UNLOCK_EFFECT_SCREENSHOT_RETRY_MS;
        long minScreenOnMs = unlockEffectScreenshotMinScreenOnMs(effect);
        if (sinceScreenOn >= 0L && sinceScreenOn < minScreenOnMs) {
            delayMs = Math.max(delayMs, minScreenOnMs - sinceScreenOn);
        }
        handler.postDelayed(unlockEffectBackgroundRetryRunnable, delayMs);
        Log.i(TAG, "unlock effect background retry scheduled reason=" + reason
                + " delayMs=" + delayMs);
    }

    private long unlockEffectScreenshotMinScreenOnMs(int effect) {
        if (effect == OverlayPrefs.EFFECT_S3_RIPPLE) {
            return S3_RIPPLE_SCREENSHOT_MIN_SCREEN_ON_MS;
        }
        if (effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS) {
            return S5_POPPING_SCREENSHOT_MIN_SCREEN_ON_MS;
        }
        if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET) {
            return COLOUR_DROPLET_SCREENSHOT_MIN_SCREEN_ON_MS;
        }
        if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
            return SPARKLING_BUBBLES_SCREENSHOT_MIN_SCREEN_ON_MS;
        }
        return UNLOCK_EFFECT_SCREENSHOT_MIN_SCREEN_ON_MS;
    }

    private boolean currentUnlockEffectHasBackground(int effect) {
        return unlockEffectRendererType == effect
                && unlockEffectRenderer instanceof BackgroundSourceRenderer
                && ((BackgroundSourceRenderer) unlockEffectRenderer).hasBackgroundSourceBitmap();
    }

    private boolean currentUnlockEffectHasFreshBackground(int effect) {
        return currentUnlockEffectHasBackground(effect)
                && unlockEffectBackgroundEffect == effect
                && (lastScreenOnAt <= 0L || unlockEffectBackgroundCapturedAt >= lastScreenOnAt);
    }

    private int currentDisplayState() {
        if (windowManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH) {
            return Display.STATE_UNKNOWN;
        }
        try {
            Display display = windowManager.getDefaultDisplay();
            return display == null ? Display.STATE_UNKNOWN : display.getState();
        } catch (Throwable t) {
            return Display.STATE_UNKNOWN;
        }
    }

    private long elapsedSinceScreenOn() {
        if (lastScreenOnAt <= 0L) {
            return -1L;
        }
        return SystemClock.uptimeMillis() - lastScreenOnAt;
    }

    private long elapsedSinceScreenOff() {
        if (lastScreenOffAt <= 0L) {
            return -1L;
        }
        return SystemClock.uptimeMillis() - lastScreenOffAt;
    }

    private String displayStateName(int state) {
        switch (state) {
            case Display.STATE_OFF:
                return "OFF";
            case Display.STATE_ON:
                return "ON";
            case Display.STATE_DOZE:
                return "DOZE";
            case Display.STATE_DOZE_SUSPEND:
                return "DOZE_SUSPEND";
            case Display.STATE_UNKNOWN:
            default:
                return "UNKNOWN";
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
        skipCachedEffectBackgroundLoad = true;
        unlockEffectBackgroundCapturedAt = 0L;
        unlockEffectBackgroundEffect = -1;
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        preloadUnlockEffectRenderer();
        if (unlockEffectRenderer instanceof BackgroundSourceRenderer) {
            ((BackgroundSourceRenderer) unlockEffectRenderer).clearBackgroundSourceBitmap();
        }
        Log.i(TAG, "unlock effect background map refresh requested");
    }

    private void loadCachedUnlockEffectBackgroundSourceIfNeeded(int effect) {
        if (skipCachedEffectBackgroundLoad
                || !effectUsesCachedScreenshotBackground(effect)
                || !(unlockEffectRenderer instanceof BackgroundSourceRenderer)) {
            return;
        }
        BackgroundSourceRenderer backgroundRenderer =
                (BackgroundSourceRenderer) unlockEffectRenderer;
        if (backgroundRenderer.hasBackgroundSourceBitmap()) {
            return;
        }
        File file = OverlayPrefs.touchBoxScreenshotFile(this);
        if (!file.exists() || file.length() <= 0L) {
            return;
        }
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            backgroundRenderer.setBackgroundSourceBitmap(bitmap, "cached_effect_background");
            unlockEffectBackgroundCapturedAt = SystemClock.uptimeMillis();
            unlockEffectBackgroundEffect = effect;
            colorScreenshotAttemptedThisSession = true;
            Log.i(TAG, "unlock effect background cache loaded size="
                    + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + " effect=" + effect);
        } catch (Throwable t) {
            Log.d(TAG, "unlock effect background cache load failed", t);
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void resetUnlockEffectBackgroundSession(boolean preserveCachedBackground) {
        colorScreenshotAttemptedThisSession = false;
        if (preserveCachedBackground
                && effectUsesCachedScreenshotBackground(unlockEffectBackgroundEffect)
                && unlockEffectRenderer instanceof BackgroundSourceRenderer
                && ((BackgroundSourceRenderer) unlockEffectRenderer).hasBackgroundSourceBitmap()) {
            return;
        }
        unlockEffectBackgroundCapturedAt = 0L;
        unlockEffectBackgroundEffect = -1;
    }

    private boolean effectUsesCachedScreenshotBackground(int effect) {
        return effect == OverlayPrefs.EFFECT_S3_RIPPLE
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET;
    }

    private void syncTouchBoxScreenshotCapture(String reason, boolean interactive,
            boolean locked, boolean blockedSurfaceActive) {
        if (!isTouchBoxScreenshotPending()) {
            touchBoxScreenshotScheduled = false;
            handler.removeCallbacks(touchBoxScreenshotDelayRunnable);
            if (!touchBoxScreenshotInFlight) {
                handler.removeCallbacks(touchBoxScreenshotCaptureRunnable);
            }
            return;
        }
        if (touchBoxScreenshotInFlight) {
            return;
        }
        if (!interactive || !locked || blockedSurfaceActive) {
            if (touchBoxScreenshotScheduled) {
                touchBoxScreenshotScheduled = false;
                handler.removeCallbacks(touchBoxScreenshotDelayRunnable);
            }
            markTouchBoxCaptureWaitingForLockscreen();
            return;
        }
        if (touchBoxScreenshotScheduled) {
            return;
        }
        touchBoxScreenshotScheduled = true;
        OverlayPrefs.get(this).edit()
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                        OverlayPrefs.TOUCH_BOX_CAPTURE_WAITING_LOCKSCREEN)
                .remove(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR)
                .apply();
        handler.postDelayed(touchBoxScreenshotDelayRunnable, TOUCH_BOX_SCREENSHOT_DELAY_MS);
        Log.i(TAG, "touch box screenshot scheduled reason=" + reason
                + " delayMs=" + TOUCH_BOX_SCREENSHOT_DELAY_MS);
    }

    private void runTouchBoxScreenshotDelay() {
        touchBoxScreenshotScheduled = false;
        if (!isTouchBoxScreenshotPending()) {
            return;
        }
        if (!canCaptureTouchBoxScreenshot()) {
            markTouchBoxCaptureWaitingForLockscreen();
            evaluateVisibility("touch_box_capture_not_ready", false);
            return;
        }
        touchBoxScreenshotInFlight = true;
        OverlayPrefs.get(this).edit()
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                        OverlayPrefs.TOUCH_BOX_CAPTURE_CAPTURING)
                .remove(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR)
                .apply();
        removeDoodleOverlay();
        removeUnlockEffectOverlay();
        removeTouchDebugOverlay();
        handler.postDelayed(touchBoxScreenshotCaptureRunnable,
                TOUCH_BOX_SCREENSHOT_OVERLAY_CLEAR_MS);
        Log.i(TAG, "touch box screenshot capture armed");
    }

    private void runTouchBoxScreenshotCapture() {
        if (!isTouchBoxScreenshotPending()) {
            touchBoxScreenshotInFlight = false;
            return;
        }
        if (!canCaptureTouchBoxScreenshot()) {
            touchBoxScreenshotInFlight = false;
            markTouchBoxCaptureWaitingForLockscreen();
            evaluateVisibility("touch_box_capture_cancelled", false);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            touchBoxScreenshotInFlight = false;
            failTouchBoxScreenshotCapture("Screenshot requires Android 11+");
            return;
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    touchBoxScreenshotInFlight = false;
                    Bitmap bitmap = bitmapFromScreenshot(screenshotResult);
                    if (bitmap == null) {
                        failTouchBoxScreenshotCapture("Screenshot empty");
                        return;
                    }
                    if (persistTouchBoxScreenshot(bitmap, "wizard")) {
                        Log.i(TAG, "touch box screenshot capture ready");
                    } else {
                        failTouchBoxScreenshotCapture("Screenshot save failed");
                    }
                    bitmap.recycle();
                    evaluateVisibility("touch_box_capture_done", false);
                }

                @Override
                public void onFailure(int errorCode) {
                    touchBoxScreenshotInFlight = false;
                    failTouchBoxScreenshotCapture("Screenshot failed code=" + errorCode);
                    evaluateVisibility("touch_box_capture_failed", false);
                }
            });
        } catch (Throwable t) {
            touchBoxScreenshotInFlight = false;
            Log.d(TAG, "touch box screenshot request failed", t);
            failTouchBoxScreenshotCapture("Screenshot request failed");
            evaluateVisibility("touch_box_capture_exception", false);
        }
    }

    private boolean canCaptureTouchBoxScreenshot() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        return interactive
                && isLockscreenLocked(false)
                && !pinEntryPending
                && !pinEntryRequested
                && !pinEntrySurfaceVisible
                && !notificationShadeVisible;
    }

    private boolean isTouchBoxScreenshotPending() {
        if (prefs == null) {
            return false;
        }
        int state = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                OverlayPrefs.TOUCH_BOX_CAPTURE_IDLE);
        if (state != OverlayPrefs.TOUCH_BOX_CAPTURE_REQUESTED
                && state != OverlayPrefs.TOUCH_BOX_CAPTURE_WAITING_LOCKSCREEN
                && state != OverlayPrefs.TOUCH_BOX_CAPTURE_CAPTURING) {
            return false;
        }
        int requestId = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_REQUEST_ID, 0);
        int resultId = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_RESULT_ID, 0);
        return requestId > 0 && requestId != resultId;
    }

    private int pendingTouchBoxScreenshotRequestId() {
        if (!isTouchBoxScreenshotPending()) {
            return 0;
        }
        return prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_REQUEST_ID, 0);
    }

    private void markTouchBoxCaptureWaitingForLockscreen() {
        if (!isTouchBoxScreenshotPending()) {
            return;
        }
        int state = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                OverlayPrefs.TOUCH_BOX_CAPTURE_IDLE);
        if (state == OverlayPrefs.TOUCH_BOX_CAPTURE_WAITING_LOCKSCREEN) {
            return;
        }
        OverlayPrefs.get(this).edit()
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                        OverlayPrefs.TOUCH_BOX_CAPTURE_WAITING_LOCKSCREEN)
                .apply();
    }

    private void failTouchBoxScreenshotCapture(String message) {
        OverlayPrefs.get(this).edit()
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                        OverlayPrefs.TOUCH_BOX_CAPTURE_FAILED)
                .putString(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR, message)
                .apply();
        Log.i(TAG, "touch box screenshot capture failed: " + message);
    }

    private boolean persistTouchBoxScreenshot(Bitmap bitmap, String sourceName) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        File file = OverlayPrefs.touchBoxScreenshotFile(this);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return false;
            }
            output.flush();
            int requestId = pendingTouchBoxScreenshotRequestId();
            SharedPreferences.Editor editor = OverlayPrefs.get(this).edit()
                    .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                            OverlayPrefs.TOUCH_BOX_CAPTURE_READY)
                    .remove(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR);
            if (requestId > 0) {
                editor.putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_RESULT_ID, requestId);
            }
            editor.apply();
            Log.i(TAG, "touch box screenshot saved source=" + sourceName
                    + " path=" + file.getAbsolutePath()
                    + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
            return true;
        } catch (Throwable t) {
            Log.d(TAG, "touch box screenshot save failed", t);
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                    // Best effort close for the cached wizard screenshot.
                }
            }
        }
    }

    private boolean effectUsesScreenshotBackground(int effect) {
        return effect == OverlayPrefs.EFFECT_S3_RIPPLE
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES;
    }

    private void syncTouchDebugOverlay() {
        syncTouchDebugOverlay(isFxSurfaceActive(false), true);
    }

    private void syncTouchDebugOverlay(boolean active) {
        syncTouchDebugOverlay(active, true);
    }

    private void syncTouchDebugOverlay(boolean mounted, boolean touchable) {
        long startedAt = SystemClock.uptimeMillis();
        if (!OverlayPrefs.debugTouchArea(this) || !mounted) {
            removeTouchDebugOverlay();
            return;
        }
        Rect box = resolveTouchBox();
        boolean standbyEnabled = OverlayPrefs.debugTouchStandby(this);
        boolean standbyTouchable = touchable || standbyEnabled;
        // Let an early wake touch try the same readiness gate used by normal gestures.
        boolean listening = touchable || standbyEnabled;
        if (touchDebugView != null) {
            touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
            touchDebugView.setListeningEnabled(listening);
            updateTouchDebugLayout(box, standbyTouchable);
            return;
        }
        touchDebugView = new TouchDebugView(this);
        touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
        touchDebugView.setListeningEnabled(listening);
        touchDebugView.setTouchTriggerListener(new TouchDebugView.TouchTriggerListener() {
            @Override
            public boolean onTouchStarted(float screenX, float screenY) {
                return beginUnlockEffectGesture(screenX, screenY);
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
                touchListenBoxFlags(standbyTouchable),
                PixelFormat.TRANSLUCENT);
        touchDebugTouchable = standbyTouchable;
        touchDebugParams.gravity = Gravity.TOP | Gravity.START;
        touchDebugParams.x = box.left;
        touchDebugParams.y = box.top;
        touchDebugParams.setTitle("LLETouchListenBox");
        windowManager.addView(touchDebugView, touchDebugParams);
        Log.i(TAG, "touch listen box shown left=" + box.left
                + " top=" + box.top
                + " right=" + box.right
                + " bottom=" + box.bottom
                + " touchable=" + standbyTouchable + " listening=" + listening
                + " active=" + touchable
                + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
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
        destroyDoodleOverlay();
        destroyUnlockEffectOverlay();
        removeTouchDebugOverlay();
    }

    private void stopAllRuntimeSurfaces() {
        handler.removeCallbacks(screenOnRefreshRunnable);
        handler.removeCallbacks(screenOffPrearmRunnable);
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        stopLockscreenSessionPolling();
        stopDebugLensLoop();
        unlockTouchCachedWhileScreenOff = false;
        unlockAffordancePending = false;
        unlockAffordanceShownThisWake = false;
        unlockFxVisible = false;
        colorScreenshotInFlight = false;
        destroyDoodleOverlay();
        destroyUnlockEffectOverlay();
        removeTouchDebugOverlay();
    }

    private void removeDoodleOverlay() {
        if (doodleOverlayAttached && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
                // The service can be torn down after the window was already removed.
            }
            doodleOverlayAttached = false;
        }
    }

    private void destroyDoodleOverlay() {
        removeDoodleOverlay();
        overlayView = null;
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
            unlockEffectBackgroundCapturedAt = 0L;
            unlockEffectBackgroundEffect = -1;
            handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
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
        if (!OverlayPrefs.masterEnabled(this)) {
            return false;
        }
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = isLockscreenLocked(contentAware);
        boolean home = interactive && !locked && isHomePackage(lastWindowPackage);
        boolean pinEntryActive = pinEntryPending || pinEntryRequested || pinEntrySurfaceVisible;
        boolean blockedSurfaceActive = pinEntryActive
                || notificationShadeVisible
                || isCallSurfaceActive();
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
        if (!OverlayPrefs.masterEnabled(this) || blockedSurfaceActive) {
            return false;
        }
        boolean showDoodleForSurface = (!interactive && OverlayPrefs.showAod(this))
                || (interactive && locked && OverlayPrefs.showLock(this))
                || (home && OverlayPrefs.showHome(this));
        return charging && OverlayPrefs.showDoodle(this) && showDoodleForSurface;
    }

    private boolean beginUnlockEffectGesture(float screenX, float screenY) {
        long startedAt = SystemClock.uptimeMillis();
        if (!OverlayPrefs.masterEnabled(this)) {
            Log.i(TAG, "unlock effect gesture ignored master=false");
            return false;
        }
        if (!isUnlockEffectGestureReady()) {
            Log.i(TAG, "unlock effect gesture ignored ready=false"
                    + " interactive=" + (powerManager == null || powerManager.isInteractive())
                    + " locked=" + isLockscreenLocked(false)
                    + " displayState=" + displayStateName(currentDisplayState())
                    + " pkg=" + lastWindowPackage);
            return false;
        }
        if (pinEntryPending || pinEntryRequested || pinEntrySurfaceVisible
                || notificationShadeVisible || isCallSurfaceActive()) {
            Log.i(TAG, "unlock effect gesture blocked by content surface");
            evaluateVisibility("gesture_blocked_surface");
            return false;
        }
        handler.removeCallbacks(pinEntryRunnable);
        handler.removeCallbacks(pinEntrySwipeRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        stopDebugLensLoop();
        unlockAffordancePending = false;
        unlockEffectAnchorX = screenX;
        unlockEffectAnchorY = screenY;
        syncUnlockEffectOverlay();
        long syncedAt = SystemClock.uptimeMillis();
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.beginGesture(unlockEffectAnchorX, unlockEffectAnchorY);
        }
        Log.i(TAG, "unlock effect gesture begin touch="
                + Math.round(screenX) + "," + Math.round(screenY)
                + " anchor=" + Math.round(unlockEffectAnchorX)
                + "," + Math.round(unlockEffectAnchorY)
                + " type=" + OverlayPrefs.unlockEffect(this)
                + " syncMs=" + (syncedAt - startedAt)
                + " beginMs=" + (SystemClock.uptimeMillis() - syncedAt));
        return true;
    }

    private boolean isUnlockEffectGestureReady() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (!interactive || !isLockscreenLocked(false)) {
            return false;
        }
        int displayState = currentDisplayState();
        return displayState == Display.STATE_UNKNOWN || displayState == Display.STATE_ON;
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
        if (!pinEntryPending) {
            handler.removeCallbacks(pinEntryRunnable);
            handler.removeCallbacks(pinEntrySwipeRunnable);
            handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        }
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.cancelGesture();
        }
        Log.i(TAG, "unlock effect gesture cancelled");
    }

    private void schedulePinEntry() {
        long delayMs = pinEntryDelayMs();
        pinEntryPending = true;
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
        if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET) {
            return PIN_ENTRY_DELAY_COLOUR_DROPLET_MS;
        }
        if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
            return PIN_ENTRY_DELAY_SPARKLING_BUBBLES_MS;
        }
        return PIN_ENTRY_DELAY_LENS_FLARE_MS;
    }

    private void openPinEntry() {
        pinEntryPending = false;
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

    private void loadCallPackages() {
        callPackages.clear();
        for (int i = 0; i < CALL_SURFACE_PACKAGES.length; i++) {
            callPackages.add(CALL_SURFACE_PACKAGES[i]);
        }
    }

    private boolean isCallPackage(CharSequence packageName) {
        return packageName != null && callPackages.contains(packageName.toString());
    }

    private boolean isCallSurfaceActive() {
        return isCallPackage(lastWindowPackage) || isCallAudioActive();
    }

    private boolean isCallAudioActive() {
        if (audioManager == null) {
            return false;
        }
        int mode = audioManager.getMode();
        return mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL;
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
