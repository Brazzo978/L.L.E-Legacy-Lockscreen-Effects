package com.codex.lle;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
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
import android.hardware.display.DisplayManager;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ChargingAccessibilityService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "ChargingA11y";
    private static final String ACTION_BENCHMARK_TOUCH =
            "com.codex.lle.BENCHMARK_TOUCH";
    private static final String ACTION_DEBUG_UNLOCK_EFFECT_PROFILE =
            "com.codex.lle.DEBUG_UNLOCK_EFFECT_PROFILE";
    private static final String ACTION_DEBUG_UNLOCK_EFFECT_BENCHMARK =
            "com.codex.lle.DEBUG_UNLOCK_EFFECT_BENCHMARK";
    static final String ACTION_EFFECT_BACKGROUND_REFRESH =
            "com.codex.lle.EFFECT_BACKGROUND_REFRESH";
    private static final int UNLOCK_TRIGGER_DISTANCE_DP = 120;
    private static final long PIN_ENTRY_DELAY_LENS_FLARE_MS = 400L;
    private static final long PIN_ENTRY_DELAY_POPPING_COLOURS_MS = 300L;
    private static final long PIN_ENTRY_DELAY_WATERCOLOUR_MS = 250L;
    private static final long PIN_ENTRY_DELAY_COLOUR_DROPLET_MS = 60L;
    // Samsung exposes a 400 ms unlock delay. The shared dispatch stage below adds 60 ms.
    private static final long PIN_ENTRY_DELAY_SPARKLING_BUBBLES_MS = 340L;
    private static final long PIN_ENTRY_DELAY_SEASONAL_UNLOCK_MS = 300L;
    private static final long SEASONAL_UNLOCK_SURFACE_HOLD_MS = 900L;
    private static final float WARM_PARK_ALPHA = 0.01f;
    private static final long PIN_ENTRY_SWIPE_START_DELAY_MS = 60L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_DEFAULT_MS = 900L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_LOCKBG_MS = 300L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_COLOUR_DROPLET_MS = 340L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_SPARKLING_BUBBLES_MS = 650L;
    private static final long LOCKBG_IDLE_HIDE_DELAY_MS = 700L;
    private static final long PIN_ENTRY_SWIPE_DURATION_MS = 260L;
    private static final long UNLOCK_AFFORDANCE_DELAY_MS = 500L;
    private static final long ACTIVE_EFFECT_PROFILE_SAMPLE_DELAY_MS = 220L;
    private static final long DEBUG_LOOP_STEP_DELAY_MS = 120L;
    private static final long DEBUG_LOOP_RESTART_DELAY_MS = 620L;
    private static final long SCREEN_ON_REFRESH_FAST_MS = 35L;
    private static final long SCREEN_ON_REFRESH_SETTLE_MS = 140L;
    private static final long LOCKSCREEN_SESSION_FAST_POLL_MS = 10L;
    private static final long LOCKSCREEN_SESSION_CONTENT_POLL_MS = 40L;
    private static final long LOCKSCREEN_EXIT_FAST_POLL_MS = 20L;
    private static final long LOCKSCREEN_EXIT_FOLLOWUP_MS = 1600L;
    private static final long LOCKSCREEN_EXIT_FOLLOWUP_ARM_WINDOW_MS = 700L;
    private static final long LOCK_SOUND_THROTTLE_MS = 1200L;
    private static final long LOCK_SOUND_UNLOCK_CONFIRM_MS = 600L;
    private static final long SCREEN_OFF_PREARM_FAST_MS = 80L;
    private static final long SCREEN_OFF_PREARM_SETTLE_MS = 180L;
    private static final long SCREEN_OFF_PREARM_LATE_MS = 420L;
    private static final long SCREEN_OFF_PREARM_FINAL_MS = 900L;
    private static final long SCREEN_OFF_PREARM_DOZE_MS = 2200L;
    private static final long SCREEN_OFF_PREARM_SUSPEND_MS = 5200L;
    private static final long HOT_WAKE_LOCK_MS = 1600L;
    private static final long WARM_BURST_FAST_MS = 24L;
    private static final long WARM_BURST_SETTLE_MS = 72L;
    private static final long WARM_BURST_LATE_MS = 180L;
    private static final long BLOCKED_SURFACE_CLEAR_GRACE_MS = 120L;
    private static final long TOUCH_BOX_SCREENSHOT_DELAY_MS = 2000L;
    private static final long TOUCH_BOX_SCREENSHOT_OVERLAY_CLEAR_MS = 180L;
    private static final long UNLOCK_EFFECT_SCREENSHOT_RETRY_MS = 90L;
    private static final long UNLOCK_EFFECT_SCREENSHOT_WAIT_LOG_INTERVAL_MS = 1000L;
    private static final int UNLOCK_EFFECT_SCREENSHOT_MAX_ATTEMPTS = 2;
    private static final long UNLOCK_EFFECT_SCREENSHOT_MIN_SCREEN_ON_MS = 360L;
    private static final long EFFECT_BACKGROUND_WAKE_TIMEOUT_MS = 7000L;
    private static final long EFFECT_BACKGROUND_WAKE_CAPTURE_MIN_SCREEN_ON_MS = 500L;
    private static final long EFFECT_BACKGROUND_WAKE_LOCK_DELAY_MS = 260L;
    private static final long S3_RIPPLE_SCREENSHOT_MIN_SCREEN_ON_MS = 1400L;
    private static final long S5_POPPING_SCREENSHOT_MIN_SCREEN_ON_MS = 500L;
    private static final long S4_LOCKBG_SCREENSHOT_MIN_SCREEN_ON_MS = 700L;
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
    private static final String SAMSUNG_BIOMETRICS_PACKAGE =
            "com.samsung.android.biometrics.app.setting";
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
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
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
            cleanupUnlockEffectAfterPinDelay();
        }
    };
    private final Runnable unlockEffectIdleHideRunnable = new Runnable() {
        @Override
        public void run() {
            parkUnlockEffectOverlayForIdle();
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
            unlockEffectBackgroundNextAttemptAt = 0L;
            refreshUnlockEffectBackgroundSourceIfNeeded("background_retry");
        }
    };
    private final Runnable forcedEffectBackgroundTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            completeForcedEffectBackgroundRefresh("timeout");
        }
    };
    private final Runnable forcedEffectBackgroundSleepRunnable = new Runnable() {
        @Override
        public void run() {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
    };
    private final Runnable activeEffectProfileSampleRunnable = new Runnable() {
        @Override
        public void run() {
            sampleActiveUnlockEffectProfile();
        }
    };
    private final Runnable unlockEffectBenchmarkStepRunnable = new Runnable() {
        @Override
        public void run() {
            runUnlockEffectBenchmarkStep();
        }
    };
    private WindowManager windowManager;
    private DisplayManager displayManager;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock hotWakeLock;
    private AudioManager audioManager;
    private LockSoundPlayer lockSoundPlayer;
    private SharedPreferences prefs;
    private SeasonalDoodleView overlayView;
    private TouchDebugView touchDebugView;
    private WindowManager.LayoutParams touchDebugParams;
    private boolean touchDebugTouchable;
    private UnlockEffectRenderer unlockEffectRenderer;
    private View unlockEffectView;
    private SeasonalUnlockEffectView seasonalUnlockPartnerRenderer;
    private View seasonalUnlockPartnerView;
    private int unlockEffectRendererType = -1;
    private boolean unlockEffectOverlayAttached;
    private boolean unlockEffectOverlayParked;
    private boolean seasonalUnlockPartnerOverlayAttached;
    private boolean seasonalUnlockPartnerOverlayParked;
    private boolean doodleOverlayAttached;
    private boolean doodleOverlayParked;
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
    private boolean interactiveSessionWasUnlocked;
    private boolean unlockFxVisible;
    private boolean unlockEffectGestureActive;
    private boolean seasonalUnlockPartnerGestureActive;
    private boolean suppressUnlockFxAfterDoodleDisconnect;
    private boolean lockscreenSessionPolling;
    private long nextContentAwarePollAt;
    private long pinEntryLastSeenAt;
    private long notificationShadeLastSeenAt;
    private long lastScreenOnAt;
    private long lastScreenOffAt;
    private long lastLockSoundPlayedAt;
    private long lastLockscreenSessionSeenAt;
    private long lockscreenExitFollowupUntil;
    private long seasonalUnlockSurfaceHoldUntil;
    private long unlockEffectBackgroundCapturedAt;
    private long unlockEffectBackgroundNextAttemptAt;
    private long unlockEffectBackgroundLastWaitLogAt;
    private long forcedEffectBackgroundOverlayClearStartedAt;
    private long cachedUnlockEffectBackgroundFileModified;
    private long cachedUnlockEffectBackgroundFileLength;
    private int cachedUnlockEffectBackgroundEffect = -1;
    private long lastS3SurfaceReattachAt;
    private long activeEffectProfileStartedAt;
    private long activeEffectProfileSyncMs;
    private long activeEffectProfileBeginMs;
    private long pinEntryTraceGestureEndAt;
    private long pinEntryTraceOpenAt;
    private long pinEntryTraceDispatchAt;
    private int unlockEffectBackgroundEffect = -1;
    private int pinEntryTraceEffect = -1;
    private int activeEffectProfileEffect = -1;
    private int activeEffectProfileToken = -1;
    private int unlockEffectBackgroundGeneration;
    private int unlockEffectBackgroundCaptureAttempts;
    private int unlockEffectWarmBurstGeneration;
    private boolean pinEntryTraceActive;
    private Bitmap cachedUnlockEffectBackgroundBitmap;
    private RuntimeMemoryStats activeEffectProfileBefore;
    private boolean unlockEffectRendererNeedsRecreate;
    private String unlockEffectRendererRecreateReason = "";
    private boolean colorScreenshotInFlight;
    private boolean colorScreenshotAttemptedThisSession;
    private boolean unlockEffectBackgroundCaptureSucceededThisSession;
    private boolean skipCachedEffectBackgroundLoad;
    private boolean rootTouchBenchmarkRunning;
    private boolean unlockEffectBenchmarkRunning;
    private boolean touchBoxScreenshotScheduled;
    private boolean touchBoxScreenshotInFlight;
    private int[] unlockEffectBenchmarkEffects;
    private int unlockEffectBenchmarkIndex;
    private int unlockEffectBenchmarkOriginalEffect;
    private int candidateWakeGeneration;
    private StringBuilder unlockEffectBenchmarkCsv;

    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId != Display.DEFAULT_DISPLAY) {
                        return;
                    }
                    scheduleCandidateWakeRefreshes("display_changed");
                }
            };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? "null" : intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                updateChargingState(intent);
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)
                    || Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                refreshChargingState();
                scheduleCandidateWakeRefreshes("broadcast:" + action);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                playLockSoundForScreenOff();
                interactiveSessionWasUnlocked = false;
                lastInteractive = false;
                lastScreenOffAt = SystemClock.uptimeMillis();
                seasonalUnlockSurfaceHoldUntil = 0L;
                suppressUnlockFxAfterDoodleDisconnect = false;
                handler.removeCallbacks(forcedEffectBackgroundTimeoutRunnable);
                handler.removeCallbacks(forcedEffectBackgroundSleepRunnable);
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
                scheduleEffectBackgroundRefreshAlarm("screen_off");
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                interactiveSessionWasUnlocked = true;
                unlockAffordancePending = false;
                unlockAffordanceShownThisWake = false;
                seasonalUnlockSurfaceHoldUntil = 0L;
                suppressUnlockFxAfterDoodleDisconnect = false;
                stopLockscreenSessionPolling();
                handler.removeCallbacks(screenOffPrearmRunnable);
                handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
                clearBlockedSurfaceState();
                unlockTouchCachedWhileScreenOff = false;
                resetUnlockEffectBackgroundSession(true);
                OverlayPrefs.get(ChargingAccessibilityService.this).edit()
                        .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE, false)
                        .putBoolean(
                                OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK,
                                false)
                        .apply();
                scheduleEffectBackgroundRefreshAlarm("user_present");
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                boolean duplicateScreenOn = lastInteractive && lastScreenOnAt > 0L;
                lastInteractive = true;
                if (!duplicateScreenOn) {
                    lastScreenOnAt = SystemClock.uptimeMillis();
                    forcedEffectBackgroundOverlayClearStartedAt = 0L;
                } else {
                    Log.i(TAG, "duplicate screen on broadcast ignored for wake timing"
                            + " sinceScreenOnMs=" + elapsedSinceScreenOn());
                }
                // A transient notification/lift wake can briefly report an unlocked keyguard
                // while SystemUI is still settling. Only ACTION_USER_PRESENT (or service startup
                // while already unlocked) is strong enough evidence that this wake was unlocked.
                interactiveSessionWasUnlocked = false;
                if (OverlayPrefs.effectBackgroundWakeCaptureActive(
                        ChargingAccessibilityService.this)) {
                    handler.removeCallbacks(forcedEffectBackgroundTimeoutRunnable);
                    handler.postDelayed(forcedEffectBackgroundTimeoutRunnable,
                            EFFECT_BACKGROUND_WAKE_TIMEOUT_MS);
                }
                Log.i(TAG, "screen on broadcast cached=" + unlockTouchCachedWhileScreenOff
                        + " interactive=" + (powerManager == null || powerManager.isInteractive())
                        + " locked=" + isLockscreenLocked(false)
                        + " sinceScreenOffMs=" + elapsedSinceScreenOff());
                unlockAffordancePending = true;
                handler.removeCallbacks(screenOffPrearmRunnable);
                if (unlockTouchCachedWhileScreenOff) {
                    notificationShadeVisible = false;
                    notificationShadeLastSeenAt = 0L;
                    restoreUnlockEffectOverlayAfterScreenOff();
                    syncTouchDebugOverlay(true, true);
                }
                evaluateVisibility("broadcast:" + action + ":fast", false);
                unlockTouchCachedWhileScreenOff = false;
                scheduleCandidateWakeRefreshes("broadcast:" + action);
                startLockscreenSessionPolling();
                return;
            } else if (Intent.ACTION_DREAMING_STOPPED.equals(action)
                    || PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)
                    || PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action)) {
                scheduleCandidateWakeRefreshes("broadcast:" + action);
            }
            evaluateVisibility("broadcast:" + action);
        }
    };

    private final BroadcastReceiver benchmarkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (ACTION_BENCHMARK_TOUCH.equals(intent.getAction())) {
                startRootTouchBenchmark();
            } else if (ACTION_DEBUG_UNLOCK_EFFECT_PROFILE.equals(intent.getAction())) {
                profileDebugUnlockEffect(intent.getIntExtra("effect",
                        OverlayPrefs.unlockEffect(ChargingAccessibilityService.this)));
            } else if (ACTION_DEBUG_UNLOCK_EFFECT_BENCHMARK.equals(intent.getAction())) {
                startUnlockEffectBenchmark();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "connected");
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            hotWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LLE:HotWake");
            hotWakeLock.setReferenceCounted(false);
        }
        lastInteractive = powerManager == null || powerManager.isInteractive();
        interactiveSessionWasUnlocked = lastInteractive && !isLockscreenLocked(false);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        lockSoundPlayer = new LockSoundPlayer(this);
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
        if (!isChargingDoodleModeEnabled()) {
            preloadUnlockEffectRenderer();
        }
        registerScreenReceiver();
        registerDisplayListener();
        if (powerManager != null && !powerManager.isInteractive()) {
            scheduleScreenOffPrearm();
        }
        scheduleEffectBackgroundRefreshAlarm("connected");
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
        noteExternalLockscreenSurface(event, interactive);
        if (event != null && isCallPackage(event.getPackageName())) {
            unlockAffordancePending = false;
            unlockTouchCachedWhileScreenOff = false;
            hideRuntimeSurfacesForCall("event:" + eventTypeName(event));
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
            logPinEntrySurfaceTrace(
                    pinEntryLastSeenAt,
                    event,
                    keyboardPinEntryEvent && !pinEntryEvent);
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
        ioExecutor.shutdownNow();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null && (key.startsWith(OverlayPrefs.EFFECT_BACKGROUND_LAST_CAPTURE_PREFIX)
                || key.startsWith(
                OverlayPrefs.EFFECT_BACKGROUND_HANDLED_REFRESH_TOKEN_PREFIX))) {
            // Cache bookkeeping does not change any visible runtime state. The save path
            // schedules the next alarm itself, so avoid one full visibility pass per key.
            return;
        }
        if (OverlayPrefs.MASTER_ENABLED.equals(key) && !OverlayPrefs.masterEnabled(this)) {
            stopAllRuntimeSurfaces();
        }
        if (OverlayPrefs.EFFECT_BACKGROUND_REFRESH_TOKEN.equals(key)
                || OverlayPrefs.POPPING_COLOR_REFRESH_TOKEN.equals(key)) {
            invalidateUnlockEffectBackgroundSource();
        }
        if (OverlayPrefs.EFFECT_BACKGROUND_AUTO_REFRESH_ENABLED.equals(key)
                || OverlayPrefs.EFFECT_BACKGROUND_REFRESH_INTERVAL_HOURS.equals(key)
                || OverlayPrefs.EFFECT_BACKGROUND_SKIP_NIGHT.equals(key)
                || OverlayPrefs.EFFECT_BACKGROUND_FORCE_RECAPTURE.equals(key)) {
            scheduleEffectBackgroundRefreshAlarm("prefs:" + key);
        }
        if (OverlayPrefs.SHOW_DOODLE.equals(key) && !OverlayPrefs.showDoodle(this)) {
            destroyDoodleOverlay();
        } else if (OverlayPrefs.SHOW_DOODLE.equals(key)) {
            ensureDoodleLoaded();
        }
        if (OverlayPrefs.SHOW_DOODLE.equals(key)
                || OverlayPrefs.SHOW_LOCK.equals(key)
                || OverlayPrefs.SEASONAL_UNLOCK_PARTNER.equals(key)) {
            if (isChargingDoodleModeEnabled()) {
                unloadUnlockEffectsForDoodleMode("prefs:" + key);
            } else {
                preloadUnlockEffectRenderer();
            }
        }
        if ((OverlayPrefs.SEASON_MODE.equals(key)
                || OverlayPrefs.POSITION_OFFSET_X.equals(key)
                || OverlayPrefs.POSITION_OFFSET_Y.equals(key)
                || OverlayPrefs.DOODLE_SIZE_PERCENT.equals(key)
                || OverlayPrefs.DEBUG_ROLLING_CHARGE.equals(key)
                || OverlayPrefs.SHOW_DOODLE.equals(key))
                && overlayView != null) {
            applyOverlayPrefs();
        }
        if (OverlayPrefs.SEASON_MODE.equals(key) && seasonalUnlockPartnerRenderer != null) {
            seasonalUnlockPartnerRenderer.setSeasonMode(OverlayPrefs.seasonMode(this));
        }
        if (OverlayPrefs.SEASONAL_UNLOCK_PARTNER.equals(key)
                && !OverlayPrefs.seasonalUnlockPartner(this)) {
            destroySeasonalUnlockPartnerOverlay();
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
        cancelEffectBackgroundRefreshAlarm();
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
        unregisterDisplayListener();
        if (lockSoundPlayer != null) {
            lockSoundPlayer.release();
            lockSoundPlayer = null;
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
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        registerReceiver(screenReceiver, filter);

        IntentFilter benchmarkFilter = new IntentFilter();
        benchmarkFilter.addAction(ACTION_BENCHMARK_TOUCH);
        benchmarkFilter.addAction(ACTION_DEBUG_UNLOCK_EFFECT_PROFILE);
        benchmarkFilter.addAction(ACTION_DEBUG_UNLOCK_EFFECT_BENCHMARK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(benchmarkReceiver, benchmarkFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(benchmarkReceiver, benchmarkFilter);
        }
    }

    private void registerDisplayListener() {
        if (displayManager == null) {
            return;
        }
        try {
            displayManager.registerDisplayListener(displayListener, handler);
        } catch (RuntimeException e) {
            Log.w(TAG, "display listener registration failed", e);
        }
    }

    private void unregisterDisplayListener() {
        if (displayManager == null) {
            return;
        }
        try {
            displayManager.unregisterDisplayListener(displayListener);
        } catch (RuntimeException ignored) {
            // Service teardown can race registration state.
        }
    }

    private void profileDebugUnlockEffect(int effect) {
        if (!isKnownUnlockEffect(effect)) {
            Log.w(TAG, "debug unlock effect profile ignored type=" + effect);
            saveEffectProfileSummary("Profile ignored: unknown effect " + effect);
            return;
        }
        EffectProfileResult result = profileUnlockEffectForMetrics(effect);
        saveEffectProfileSummary(result.summary());
        Log.i(TAG, "debug unlock effect profile complete " + result.logLine());
    }

    private void startUnlockEffectBenchmark() {
        if (unlockEffectBenchmarkRunning) {
            saveEffectProfileSummary("Benchmark already running");
            return;
        }
        if (isChargingDoodleModeEnabled()) {
            saveEffectProfileSummary("Benchmark blocked: charging doodle mode unloads FX");
            return;
        }
        unlockEffectBenchmarkRunning = true;
        unlockEffectBenchmarkOriginalEffect = OverlayPrefs.unlockEffect(this);
        unlockEffectBenchmarkIndex = 0;
        unlockEffectBenchmarkEffects = new int[] {
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
        };
        unlockEffectBenchmarkCsv = new StringBuilder(RuntimeMemoryStats.csvHeader());
        OverlayPrefs.get(this).edit()
                .putBoolean(OverlayPrefs.EFFECT_PROFILE_RUNNING, true)
                .putString(OverlayPrefs.EFFECT_PROFILE_DIAGNOSTIC_SUMMARY,
                        "Benchmark running: 0/" + unlockEffectBenchmarkEffects.length)
                .apply();
        handler.removeCallbacks(unlockEffectBenchmarkStepRunnable);
        handler.post(unlockEffectBenchmarkStepRunnable);
    }

    private void runUnlockEffectBenchmarkStep() {
        if (!unlockEffectBenchmarkRunning || unlockEffectBenchmarkEffects == null) {
            return;
        }
        if (unlockEffectBenchmarkIndex >= unlockEffectBenchmarkEffects.length) {
            finishUnlockEffectBenchmark();
            return;
        }
        int effect = unlockEffectBenchmarkEffects[unlockEffectBenchmarkIndex];
        EffectProfileResult result = profileUnlockEffectForMetrics(effect);
        unlockEffectBenchmarkCsv.append(result.csvRow());
        unlockEffectBenchmarkIndex++;
        OverlayPrefs.get(this).edit()
                .putString(OverlayPrefs.EFFECT_PROFILE_DIAGNOSTIC_SUMMARY,
                        "Benchmark running: " + unlockEffectBenchmarkIndex
                                + "/" + unlockEffectBenchmarkEffects.length
                                + "\n" + result.summary())
                .apply();
        handler.postDelayed(unlockEffectBenchmarkStepRunnable, 260L);
    }

    private void finishUnlockEffectBenchmark() {
        File file = OverlayPrefs.effectBenchmarkFile(this);
        String status;
        if (writeTextFile(file, unlockEffectBenchmarkCsv == null
                ? ""
                : unlockEffectBenchmarkCsv.toString())) {
            status = "Benchmark complete: " + file.getAbsolutePath();
        } else {
            status = "Benchmark complete, CSV save failed";
        }
        unlockEffectBenchmarkRunning = false;
        unlockEffectBenchmarkEffects = null;
        unlockEffectBenchmarkCsv = null;
        OverlayPrefs.get(this).edit()
                .putInt(OverlayPrefs.UNLOCK_EFFECT, unlockEffectBenchmarkOriginalEffect)
                .putBoolean(OverlayPrefs.EFFECT_PROFILE_RUNNING, false)
                .putString(OverlayPrefs.EFFECT_PROFILE_DIAGNOSTIC_SUMMARY, status)
                .putString(OverlayPrefs.EFFECT_PROFILE_LAST_CSV, file.getAbsolutePath())
                .apply();
        destroyUnlockEffectOverlay();
        evaluateVisibility("effect_benchmark_complete", false);
        Log.i(TAG, status);
    }

    private EffectProfileResult profileUnlockEffectForMetrics(int effect) {
        long startedAt = SystemClock.uptimeMillis();
        RuntimeMemoryStats before = RuntimeMemoryStats.capture();
        if (isChargingDoodleModeEnabled()) {
            RuntimeMemoryStats after = RuntimeMemoryStats.capture();
            return new EffectProfileResult(
                    effect,
                    OverlayPrefs.effectLabel(effect),
                    0L,
                    0L,
                    0L,
                    SystemClock.uptimeMillis() - startedAt,
                    before,
                    after,
                    "blocked_charging_doodle");
        }

        OverlayPrefs.get(this).edit()
                .putInt(OverlayPrefs.UNLOCK_EFFECT, effect)
                .apply();
        destroyUnlockEffectOverlay();

        long preloadStartedAt = SystemClock.uptimeMillis();
        preloadUnlockEffectRenderer();
        long preloadMs = SystemClock.uptimeMillis() - preloadStartedAt;

        long attachStartedAt = SystemClock.uptimeMillis();
        syncUnlockEffectOverlay(false);
        long attachMs = SystemClock.uptimeMillis() - attachStartedAt;

        long warmStartedAt = SystemClock.uptimeMillis();
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.warmUp();
        }
        long warmMs = SystemClock.uptimeMillis() - warmStartedAt;

        RuntimeMemoryStats after = RuntimeMemoryStats.capture();
        String name = unlockEffectRenderer == null
                ? OverlayPrefs.effectLabel(effect)
                : unlockEffectRenderer.effectName();
        return new EffectProfileResult(
                effect,
                name,
                preloadMs,
                attachMs,
                warmMs,
                SystemClock.uptimeMillis() - startedAt,
                before,
                after,
                unlockEffectRenderer == null ? "no_renderer" : "ok");
    }

    private void saveEffectProfileSummary(String summary) {
        OverlayPrefs.get(this).edit()
                .putString(OverlayPrefs.EFFECT_PROFILE_DIAGNOSTIC_SUMMARY, summary)
                .putBoolean(OverlayPrefs.EFFECT_PROFILE_RUNNING, unlockEffectBenchmarkRunning)
                .apply();
    }

    private boolean writeTextFile(File file, String text) {
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(text.getBytes("UTF-8"));
            output.flush();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "text file write failed path=" + file, t);
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean isKnownUnlockEffect(int effect) {
        return effect == OverlayPrefs.EFFECT_S4_LENS_FLARE
                || effect == OverlayPrefs.EFFECT_S3_RIPPLE
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_S4_RIPPLE
                || effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC;
    }

    private static final class EffectProfileResult {
        final int effect;
        final String effectName;
        final long preloadMs;
        final long attachMs;
        final long warmMs;
        final long totalMs;
        final RuntimeMemoryStats before;
        final RuntimeMemoryStats after;
        final String status;

        EffectProfileResult(
                int effect,
                String effectName,
                long preloadMs,
                long attachMs,
                long warmMs,
                long totalMs,
                RuntimeMemoryStats before,
                RuntimeMemoryStats after,
                String status) {
            this.effect = effect;
            this.effectName = effectName;
            this.preloadMs = preloadMs;
            this.attachMs = attachMs;
            this.warmMs = warmMs;
            this.totalMs = totalMs;
            this.before = before;
            this.after = after;
            this.status = status;
        }

        String summary() {
            return effectName
                    + " (" + status + ")\n"
                    + "Preload " + preloadMs + " ms | Attach " + attachMs
                    + " ms | Warm " + warmMs + " ms | Total " + totalMs + " ms\n"
                    + "PSS " + RuntimeMemoryStats.formatMb(after.totalPssKb)
                    + " (delta " + signedMb(after.totalPssKb - before.totalPssKb) + ")\n"
                    + "Java " + RuntimeMemoryStats.formatMb(after.javaHeapKb)
                    + " | Native " + RuntimeMemoryStats.formatMb(after.nativeHeapKb)
                    + " | Graphics " + RuntimeMemoryStats.formatMb(after.graphicsKb)
                    + " | Native alloc " + RuntimeMemoryStats.formatMb(after.nativeAllocatedKb);
        }

        String logLine() {
            return "effect=" + effect
                    + " name=" + effectName
                    + " status=" + status
                    + " totalMs=" + totalMs
                    + " preloadMs=" + preloadMs
                    + " attachMs=" + attachMs
                    + " warmMs=" + warmMs
                    + " pssKb=" + after.totalPssKb
                    + " deltaPssKb=" + (after.totalPssKb - before.totalPssKb);
        }

        String csvRow() {
            return after.wallTimeMs + ","
                    + effect + ","
                    + csv(effectName) + ","
                    + preloadMs + ","
                    + attachMs + ","
                    + warmMs + ","
                    + totalMs + ","
                    + after.totalPssKb + ","
                    + after.privateDirtyKb + ","
                    + after.javaHeapKb + ","
                    + after.nativeHeapKb + ","
                    + after.graphicsKb + ","
                    + after.codeKb + ","
                    + after.stackKb + ","
                    + after.systemKb + ","
                    + after.swapKb + ","
                    + after.nativeAllocatedKb + ","
                    + after.javaUsedKb + ","
                    + after.javaTotalKb + ","
                    + after.javaMaxKb + ","
                    + csv(status) + "\n";
        }

        private static String signedMb(long kb) {
            return (kb >= 0L ? "+" : "-") + RuntimeMemoryStats.formatMb(Math.abs(kb));
        }

        private static String csv(String value) {
            if (value == null) {
                return "";
            }
            return "\"" + value.replace("\"", "\"\"") + "\"";
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

    private void scheduleCandidateWakeRefreshes(final String reason) {
        holdHotWakeLock(reason);
        final int generation = ++candidateWakeGeneration;
        evaluateVisibility(reason + ":0ms", false);
        scheduleCandidateWakeRefresh(reason, generation, 50L, false);
        scheduleCandidateWakeRefresh(reason, generation, 150L, true);
        scheduleCandidateWakeRefresh(reason, generation, 350L, true);
        scheduleCandidateWakeRefresh(reason, generation, 800L, true);
    }

    private void scheduleCandidateWakeRefresh(final String reason, final int generation,
            long delayMs, final boolean contentAware) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (generation != candidateWakeGeneration) {
                    return;
                }
                evaluateVisibility(reason + ":" + delayMs + "ms", contentAware);
            }
        }, delayMs);
    }

    private void handleInteractiveLockscreenWake(String reason, boolean locked) {
        if (!locked || !OverlayPrefs.unlockEffectEnabled(this)) {
            return;
        }
        holdHotWakeLock("interactive_wake:" + reason);
        long now = SystemClock.uptimeMillis();
        boolean cached = unlockTouchCachedWhileScreenOff;
        lastScreenOnAt = now;
        unlockAffordancePending = true;
        handler.removeCallbacks(screenOffPrearmRunnable);
        notificationShadeVisible = false;
        notificationShadeLastSeenAt = 0L;
        if (cached) {
            restoreUnlockEffectOverlayAfterScreenOff();
            syncTouchDebugOverlay(true, true);
        }
        unlockTouchCachedWhileScreenOff = false;
        scheduleScreenOnRefreshes();
        if (!cached) {
            scheduleUnlockEffectWarmBurst("interactive_wake:" + reason);
        }
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
        lockscreenExitFollowupUntil = 0L;
        handler.removeCallbacks(lockscreenSessionPollRunnable);
    }

    private void runLockscreenSessionPoll() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = isLockscreenLocked(false);
        if (!OverlayPrefs.masterEnabled(this) || !interactive) {
            stopLockscreenSessionPolling();
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (!locked) {
            if (!shouldContinueLockscreenExitFollowup(now)) {
                evaluateVisibility("lockscreen_exit_poll_final", false);
                stopLockscreenSessionPolling();
                return;
            }
            evaluateVisibility("lockscreen_exit_poll_fast", false);
            handler.postDelayed(lockscreenSessionPollRunnable, LOCKSCREEN_EXIT_FAST_POLL_MS);
            return;
        }

        lastLockscreenSessionSeenAt = now;
        lockscreenExitFollowupUntil = 0L;
        boolean contentAware = now >= nextContentAwarePollAt;
        if (contentAware) {
            nextContentAwarePollAt = now + LOCKSCREEN_SESSION_CONTENT_POLL_MS;
        }
        evaluateVisibility(contentAware ? "lockscreen_poll_content" : "lockscreen_poll_fast",
                contentAware);
        handler.postDelayed(lockscreenSessionPollRunnable, LOCKSCREEN_SESSION_FAST_POLL_MS);
    }

    private boolean shouldContinueLockscreenExitFollowup(long now) {
        if (lockscreenExitFollowupUntil <= 0L
                && lastLockscreenSessionSeenAt > 0L
                && now - lastLockscreenSessionSeenAt <= LOCKSCREEN_EXIT_FOLLOWUP_ARM_WINDOW_MS) {
            lockscreenExitFollowupUntil = now + LOCKSCREEN_EXIT_FOLLOWUP_MS;
        }
        return lockscreenExitFollowupUntil > now;
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
                syncUnlockEffectOverlay(false);
                if (unlockEffectRenderer != null) {
                    unlockEffectRenderer.warmUp();
                }
                parkUnlockEffectOverlayForScreenOff();
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
        holdHotWakeLock("screen_off_prearm");
        if (shouldKeepUnlockEffectOverlayDuringScreenOff()) {
            syncUnlockEffectOverlay(false);
            scheduleUnlockEffectWarmBurst("screen_off_prearm");
            parkUnlockEffectOverlayForScreenOff();
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
        boolean showDoodle = isDoodleEnabledForLockscreen(false);
        return screenOffOrLocked
                && OverlayPrefs.masterEnabled(this)
                && !showDoodle
                && !isCallSurfaceActive()
                && OverlayPrefs.unlockEffectEnabled(this)
                && OverlayPrefs.debugTouchArea(this);
    }

    private boolean shouldKeepUnlockEffectOverlayDuringScreenOff() {
        int selectedEffect = OverlayPrefs.unlockEffect(this);
        return selectedEffect != OverlayPrefs.EFFECT_S4_RIPPLE
                && unlockEffectRendererType != OverlayPrefs.EFFECT_S4_RIPPLE;
    }

    private boolean shouldKeepNativePhysicsOverlayAttachedDuringHide(int effect) {
        return effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES;
    }

    private void noteExternalLockscreenSurface(AccessibilityEvent event, boolean interactive) {
        if (event == null
                || event.getPackageName() == null
                || !interactive
                || !isLockscreenLocked(false)
                || !isExternalLockscreenSurfacePackage(event.getPackageName())) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }
        markUnlockEffectRendererStale("external_surface:" + event.getPackageName());
    }

    private boolean isExternalLockscreenSurfacePackage(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.toString();
        return !isSystemUiFamilyPackage(value)
                && !getPackageName().equals(value)
                && !isHomePackage(value)
                && !isCallPackage(value)
                && !isKeyboardPackage(value);
    }

    private boolean isSystemUiFamilyPackage(String packageName) {
        return packageName != null
                && (SYSTEM_UI_PACKAGE.equals(packageName)
                || packageName.startsWith(SYSTEM_UI_PACKAGE + ".")
                || AOD_PACKAGE.equals(packageName)
                || packageName.startsWith(AOD_PACKAGE + ".")
                || SAMSUNG_BIOMETRICS_PACKAGE.equals(packageName)
                || packageName.startsWith(SAMSUNG_BIOMETRICS_PACKAGE + "."));
    }

    private boolean isSamsungLockBgEffect(int effect) {
        return effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC;
    }

    private boolean shouldParkUnlockEffectOverlayWhenIdle() {
        return !unlockEffectGestureActive
                && (isSamsungLockBgEffect(OverlayPrefs.unlockEffect(this))
                || isSamsungLockBgEffect(unlockEffectRendererType));
    }

    private void markUnlockEffectRendererStale(String reason) {
        int effect = unlockEffectRendererType >= 0
                ? unlockEffectRendererType
                : OverlayPrefs.unlockEffect(this);
        if (!isSamsungLockBgEffect(effect)) {
            return;
        }
        boolean alreadyMarked = unlockEffectRendererNeedsRecreate;
        unlockEffectRendererNeedsRecreate = true;
        unlockEffectRendererRecreateReason = reason;
        if (!alreadyMarked) {
            Log.i(TAG, "native lockbg renderer marked stale reason=" + reason
                    + " type=" + effect
                    + " attached=" + unlockEffectOverlayAttached
                    + " parked=" + unlockEffectOverlayParked);
        }
    }

    private void clearBlockedSurfaceState() {
        pinEntryPending = false;
        pinEntryRequested = false;
        pinEntrySurfaceSeen = false;
        pinEntrySurfaceVisible = false;
        notificationShadeVisible = false;
        pinEntryLastSeenAt = 0L;
        notificationShadeLastSeenAt = 0L;
        clearPinEntryTrace();
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
        long sinceScreenOnMs = elapsedSinceScreenOn();
        if (!interactiveSessionWasUnlocked
                && interactive
                && !locked
                && sinceScreenOnMs >= LOCK_SOUND_UNLOCK_CONFIRM_MS) {
            interactiveSessionWasUnlocked = true;
            Log.i(TAG, "lock sound armed reason=stable_unlocked_session"
                    + " sinceScreenOnMs=" + sinceScreenOnMs);
        }
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
        if (callSurface) {
            unlockAffordancePending = false;
            unlockTouchCachedWhileScreenOff = false;
            hideRuntimeSurfacesForCall(reason);
            if (shouldLogVisibility(reason)) {
                Log.i(TAG, "visibility reason=" + reason
                        + " showDoodle=false showFx=false"
                        + " callSurface=true"
                        + " charging=" + charging
                        + " interactive=" + interactive
                        + " locked=" + locked
                        + " pkg=" + lastWindowPackage);
            }
            return;
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
            boolean showDoodle = isDoodleVisible(false, locked, false, false);
            if (showDoodle) {
                syncDoodleOverlay();
                removeUnlockEffectOverlay();
                removeTouchDebugOverlay();
                unlockTouchCachedWhileScreenOff = false;
            } else {
                parkDoodleOverlayForWarmth("screen_off_cached");
                if (!shouldKeepUnlockEffectOverlayDuringScreenOff()) {
                    removeUnlockEffectOverlay();
                } else {
                    parkUnlockEffectOverlayForScreenOff();
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
        boolean seasonalSurfaceHoldActive = isSeasonalUnlockSurfaceHoldActive(
                pinEntrySurfaceVisible, notificationShadeVisible, callSurface);
        boolean blockedSurfaceActive = pinEntryActive || notificationShadeVisible || callSurface;
        boolean touchBoxCapturePending = isTouchBoxScreenshotPending();
        boolean hideOverlaysForTouchBoxCapture = touchBoxCapturePending && interactive && locked;
        boolean hideOverlaysForBackgroundCapture =
                OverlayPrefs.effectBackgroundWakeCaptureActive(this) && interactive && locked;
        syncTouchBoxScreenshotCapture(reason, interactive, locked, blockedSurfaceActive);
        boolean aodSurface = AOD_PACKAGE.equals(lastWindowPackage);

        if (hideOverlaysForBackgroundCapture) {
            if (forcedEffectBackgroundOverlayClearStartedAt <= 0L) {
                forcedEffectBackgroundOverlayClearStartedAt = SystemClock.uptimeMillis();
            }
            if (doodleOverlayAttached) {
                removeDoodleOverlay();
            }
            if (unlockEffectOverlayAttached || unlockEffectOverlayParked) {
                // A few native renderers normally stay attached at alpha 0 while parked.
                // Screenshot capture needs the accessibility window to be truly detached.
                removeUnlockEffectOverlay(true);
            }
            if (touchDebugView != null) {
                removeTouchDebugOverlay();
            }
            long overlayClearAgeMs = SystemClock.uptimeMillis()
                    - forcedEffectBackgroundOverlayClearStartedAt;
            if (overlayClearAgeMs >= TOUCH_BOX_SCREENSHOT_OVERLAY_CLEAR_MS) {
                refreshUnlockEffectBackgroundSourceIfNeeded("forced_background:" + reason);
            } else {
                scheduleUnlockEffectBackgroundRetry("overlay_clear:" + reason);
            }
        }

        boolean runtimeSurfaceAllowed = isSharedRuntimeSurfaceAllowed(
                interactive,
                displayOn,
                locked,
                aodSurface,
                hideOverlaysForTouchBoxCapture,
                hideOverlaysForBackgroundCapture,
                blockedSurfaceActive);
        boolean showDoodle = runtimeSurfaceAllowed && isChargingDoodleModeEnabled();
        boolean showFx = runtimeSurfaceAllowed
                && !showDoodle
                && !suppressUnlockFxAfterDoodleDisconnect
                && OverlayPrefs.unlockEffectEnabled(this);

        if (!showDoodle
                && shouldRunUnlockEffectBackgroundPreflight(interactive, displayOn, locked,
                aodSurface, hideOverlaysForTouchBoxCapture, hideOverlaysForBackgroundCapture,
                blockedSurfaceActive)) {
            refreshUnlockEffectBackgroundSourceIfNeeded("service_background:" + reason);
        }

        if (showDoodle) {
            syncDoodleOverlay();
            if (isSeasonalUnlockPartnerModeEnabled()) {
                syncSeasonalUnlockPartnerOverlay();
                syncTouchDebugOverlay(true, true);
            } else {
                destroySeasonalUnlockPartnerOverlay();
            }
        } else if (seasonalSurfaceHoldActive
                && !hideOverlaysForTouchBoxCapture
                && !hideOverlaysForBackgroundCapture) {
            syncDoodleOverlay();
            syncSeasonalUnlockPartnerOverlay();
            removeTouchDebugOverlay();
        } else {
            if (!hideOverlaysForTouchBoxCapture
                    && !hideOverlaysForBackgroundCapture
                    && isChargingDoodleModeEnabled()) {
                parkDoodleOverlayForWarmth(reason);
            } else {
                removeDoodleOverlay();
            }
            if (!hideOverlaysForTouchBoxCapture
                    && !hideOverlaysForBackgroundCapture
                    && isSeasonalUnlockPartnerModeEnabled()) {
                parkSeasonalUnlockPartnerOverlayForWarmth(reason);
            } else {
                destroySeasonalUnlockPartnerOverlay();
            }
        }

        if (showFx) {
            if (!unlockFxVisible) {
                unlockFxVisible = true;
                unlockAffordanceShownThisWake = false;
            }
            boolean parkFxIdle = shouldParkUnlockEffectOverlayWhenIdle();
            if (!parkFxIdle && !unlockAffordancePending && !unlockAffordanceShownThisWake) {
                unlockAffordancePending = true;
                Log.i(TAG, "unlock affordance armed from visible lockscreen reason=" + reason
                        + " cached=" + unlockTouchCachedWhileScreenOff);
            }
            refreshUnlockEffectBackgroundSourceIfNeeded("showFx:" + reason);
            syncUnlockEffectOverlay(!parkFxIdle);
            if (parkFxIdle) {
                parkUnlockEffectOverlayForIdle();
            } else {
                showPendingUnlockAffordance(reason);
            }
            syncTouchDebugOverlay(true, true);
            syncDebugLensLoop();
        } else {
            unlockFxVisible = false;
            stopDebugLensLoop();
            if (!pinEntryPending && !pinEntryRequested && !pinEntrySurfaceVisible) {
                removeUnlockEffectOverlay();
            }
            if (!(showDoodle && isSeasonalUnlockPartnerModeEnabled())) {
                removeTouchDebugOverlay();
            }
        }

        if (interactive && locked) {
            lastLockscreenSessionSeenAt = SystemClock.uptimeMillis();
            lockscreenExitFollowupUntil = 0L;
            startLockscreenSessionPolling();
        } else if (interactive && shouldContinueLockscreenExitFollowup(SystemClock.uptimeMillis())) {
            startLockscreenSessionPolling();
        } else {
            stopLockscreenSessionPolling();
        }

        if (shouldLogVisibility(reason)) {
            Log.i(TAG, "visibility reason=" + reason
                    + " showDoodle=" + showDoodle
                    + " showFx=" + showFx
                    + " charging=" + charging
                    + " seasonalPartner=" + isSeasonalUnlockPartnerModeEnabled()
                    + " seasonalHold=" + seasonalSurfaceHoldActive
                    + " suppressFxAfterDoodle=" + suppressUnlockFxAfterDoodleDisconnect
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
            overlayView.setWarmParked(false);
            overlayView.setAlpha(1f);
            overlayView.setVisibility(View.VISIBLE);
            if (doodleOverlayParked) {
                doodleOverlayParked = false;
                overlayView.invalidate();
                overlayView.postInvalidateOnAnimation();
                Log.i(TAG, "doodle overlay resumed warm");
            }
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
        try {
            windowManager.addView(overlayView, params);
        } catch (RuntimeException e) {
            if (isAlreadyAddedWindowError(e)) {
                doodleOverlayAttached = true;
                Log.w(TAG, "doodle overlay already attached");
                return;
            }
            Log.e(TAG, "doodle overlay addView failed", e);
            destroyDoodleOverlay();
            return;
        }
        doodleOverlayAttached = true;
        doodleOverlayParked = false;
        overlayView.setWarmParked(false);
        overlayView.setAlpha(1f);
        overlayView.setVisibility(View.VISIBLE);
        Log.i(TAG, "doodle overlay shown");
    }

    private void parkDoodleOverlayForWarmth(String reason) {
        if (!isChargingDoodleModeEnabled()) {
            removeDoodleOverlay();
            return;
        }
        ensureDoodleLoaded();
        if (overlayView == null) {
            return;
        }
        if (!doodleOverlayAttached) {
            syncDoodleOverlay();
        }
        if (doodleOverlayAttached && overlayView != null) {
            overlayView.setWarmParked(true);
            overlayView.setAlpha(shouldHardHideParkedDoodle() ? 0f : WARM_PARK_ALPHA);
            overlayView.setVisibility(View.VISIBLE);
            overlayView.invalidate();
            if (!doodleOverlayParked) {
                doodleOverlayParked = true;
                Log.i(TAG, "doodle overlay parked warm reason=" + reason);
            }
        }
    }

    private boolean shouldHardHideParkedDoodle() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        int displayState = currentDisplayState();
        return !interactive
                || displayState == Display.STATE_DOZE
                || displayState == Display.STATE_DOZE_SUSPEND
                || AOD_PACKAGE.equals(lastWindowPackage);
    }

    private void ensureDoodleLoaded() {
        if (overlayView != null
                || !OverlayPrefs.masterEnabled(this)
                || !OverlayPrefs.showDoodle(this)
                || !charging) {
            return;
        }
        overlayView = new SeasonalDoodleView(this);
        applyOverlayPrefs();
        Log.i(TAG, "doodle view preloaded");
    }

    private void syncSeasonalUnlockPartnerOverlay() {
        syncSeasonalUnlockPartnerOverlay(true);
    }

    private void syncSeasonalUnlockPartnerOverlay(boolean visible) {
        if (!isSeasonalUnlockPartnerModeEnabled()) {
            destroySeasonalUnlockPartnerOverlay();
            return;
        }
        ensureSeasonalUnlockPartnerLoaded();
        if (seasonalUnlockPartnerView == null) {
            return;
        }
        seasonalUnlockPartnerView.setAlpha(visible ? 1f : 0f);
        seasonalUnlockPartnerView.setVisibility(View.VISIBLE);
        if (seasonalUnlockPartnerOverlayAttached) {
            if (visible && seasonalUnlockPartnerOverlayParked) {
                seasonalUnlockPartnerOverlayParked = false;
                Log.i(TAG, "seasonal unlock partner resumed warm");
            } else if (!visible) {
                seasonalUnlockPartnerOverlayParked = true;
            }
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
        params.setTitle("LLESeasonalUnlockPartner");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        try {
            windowManager.addView(seasonalUnlockPartnerView, params);
        } catch (RuntimeException e) {
            if (isAlreadyAddedWindowError(e)) {
                seasonalUnlockPartnerOverlayAttached = true;
                Log.w(TAG, "seasonal unlock partner already attached");
                return;
            }
            Log.e(TAG, "seasonal unlock partner addView failed", e);
            destroySeasonalUnlockPartnerOverlay();
            return;
        }
        seasonalUnlockPartnerOverlayAttached = true;
        seasonalUnlockPartnerOverlayParked = !visible;
        seasonalUnlockPartnerRenderer.warmUp();
        Log.i(TAG, "seasonal unlock partner " + (visible ? "shown" : "parked warm"));
    }

    private void parkSeasonalUnlockPartnerOverlayForWarmth(String reason) {
        if (!isSeasonalUnlockPartnerModeEnabled()) {
            destroySeasonalUnlockPartnerOverlay();
            return;
        }
        ensureSeasonalUnlockPartnerLoaded();
        if (seasonalUnlockPartnerView == null) {
            return;
        }
        if (!seasonalUnlockPartnerOverlayAttached) {
            syncSeasonalUnlockPartnerOverlay(false);
        }
        if (seasonalUnlockPartnerOverlayAttached && seasonalUnlockPartnerView != null) {
            if (seasonalUnlockPartnerRenderer != null) {
                seasonalUnlockPartnerRenderer.resetEffect();
                seasonalUnlockPartnerRenderer.warmUp();
            }
            seasonalUnlockPartnerView.setAlpha(0f);
            seasonalUnlockPartnerView.setVisibility(View.VISIBLE);
            seasonalUnlockPartnerGestureActive = false;
            if (!seasonalUnlockPartnerOverlayParked) {
                seasonalUnlockPartnerOverlayParked = true;
                Log.i(TAG, "seasonal unlock partner parked warm reason=" + reason);
            }
        }
    }

    private void ensureSeasonalUnlockPartnerLoaded() {
        if (seasonalUnlockPartnerRenderer != null || !isSeasonalUnlockPartnerModeEnabled()) {
            return;
        }
        seasonalUnlockPartnerRenderer = new SeasonalUnlockEffectView(this);
        seasonalUnlockPartnerRenderer.setSeasonMode(OverlayPrefs.seasonMode(this));
        seasonalUnlockPartnerView = seasonalUnlockPartnerRenderer.asView();
        Log.i(TAG, "seasonal unlock partner preloaded");
    }

    private void syncUnlockEffectOverlay() {
        syncUnlockEffectOverlay(true);
    }

    private void syncUnlockEffectOverlay(final boolean visible) {
        long startedAt = SystemClock.uptimeMillis();
        preloadUnlockEffectRenderer();
        if (unlockEffectOverlayAttached && unlockEffectView != null
                && shouldReattachUnlockEffectSurfaceForWarmup()) {
            reattachUnlockEffectOverlay("surface_not_ready");
        }
        if (unlockEffectOverlayAttached || unlockEffectView == null) {
            if (unlockEffectOverlayAttached && unlockEffectView != null) {
                if (visible) {
                    showUnlockEffectView(unlockEffectView);
                } else {
                    parkUnlockEffectOverlayForScreenOff();
                }
            }
            return;
        }
        if (visible) {
            showUnlockEffectView(unlockEffectView);
        } else {
            parkNativePhysicsRendererState(unlockEffectRenderer);
            hideUnlockEffectView(unlockEffectView);
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
        try {
            windowManager.addView(unlockEffectView, params);
        } catch (RuntimeException e) {
            if (isAlreadyAddedWindowError(e)) {
                unlockEffectOverlayAttached = true;
                if (visible) {
                    showUnlockEffectView(unlockEffectView);
                } else {
                    parkUnlockEffectOverlayForScreenOff();
                }
                Log.w(TAG, "unlock effect overlay already attached type="
                        + unlockEffectRendererType);
                return;
            }
            Log.e(TAG, "unlock effect overlay addView failed type=" + unlockEffectRendererType, e);
            UnlockEffectRenderer failedRenderer = unlockEffectRenderer;
            unlockEffectOverlayAttached = false;
            unlockEffectView = null;
            unlockEffectRenderer = null;
            unlockEffectRendererType = -1;
            if (failedRenderer != null) {
                try {
                    failedRenderer.destroy();
                } catch (Throwable destroyError) {
                    Log.d(TAG, "renderer cleanup ignored after addView failure",
                            destroyError);
                }
            }
            return;
        }
        unlockEffectOverlayAttached = true;
        unlockEffectView.post(new Runnable() {
            @Override
            public void run() {
                if (unlockEffectRenderer != null) {
                    unlockEffectRenderer.warmUp();
                }
                boolean interactive = powerManager == null || powerManager.isInteractive();
                if (!visible
                        && !unlockEffectGestureActive
                        && (!interactive || shouldParkUnlockEffectOverlayWhenIdle())) {
                    parkUnlockEffectOverlayForScreenOff();
                }
            }
        });
        Log.i(TAG, "unlock effect overlay shown type=" + unlockEffectRendererType
                + " name=" + (unlockEffectRenderer == null
                ? "none"
                : unlockEffectRenderer.effectName())
                + " visible=" + visible
                + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
    }

    private void preloadUnlockEffectRenderer() {
        if (isChargingDoodleModeEnabled()) {
            unloadUnlockEffectsForDoodleMode("preload_blocked");
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        int effect = OverlayPrefs.unlockEffect(this);
        if (unlockEffectRenderer != null && unlockEffectRendererType == effect) {
            if (!unlockEffectRendererNeedsRecreate || !isSamsungLockBgEffect(effect)) {
                return;
            }
            if (!canRecreateStaleLockBgRenderer()) {
                Log.i(TAG, "native lockbg renderer recreate delayed reason="
                        + unlockEffectRendererRecreateReason
                        + " type=" + effect
                        + " gesture=" + unlockEffectGestureActive
                        + " pinEntry=" + pinEntrySurfaceVisible
                        + " notificationShade=" + notificationShadeVisible
                        + " callSurface=" + isCallSurfaceActive());
                return;
            }
            Log.i(TAG, "native lockbg renderer recreating reason="
                    + unlockEffectRendererRecreateReason
                    + " type=" + effect);
        }
        destroyUnlockEffectOverlay();
        unlockEffectRendererType = effect;
        long constructStartedAt = SystemClock.uptimeMillis();
        try {
            if (effect == OverlayPrefs.EFFECT_S4_LENS_FLARE) {
                unlockEffectRenderer = new LensFlareEffectView(this);
            } else if (effect == OverlayPrefs.EFFECT_S3_RIPPLE) {
                unlockEffectRenderer = new S3RippleMeshEffectView(this);
            } else if (effect == OverlayPrefs.EFFECT_S4_RIPPLE) {
                unlockEffectRenderer = new S4RippleEffectView(this);
            } else if (effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES) {
                unlockEffectRenderer = SamsungLockBgEffectView.abstractTiles(this);
            } else if (effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC) {
                unlockEffectRenderer = SamsungLockBgEffectView.geometricMosaic(this);
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
            }
        } catch (Throwable t) {
            Log.e(TAG, "unlock effect renderer preload failed type=" + effect, t);
            unlockEffectRenderer = null;
            unlockEffectView = null;
            unlockEffectRendererType = -1;
            unlockEffectRendererNeedsRecreate = false;
            unlockEffectRendererRecreateReason = "";
            return;
        }
        if (unlockEffectRenderer == null) {
            unlockEffectRenderer = null;
            unlockEffectView = null;
            Log.i(TAG, "unlock effect slot has no renderer type=" + effect);
            return;
        }
        long constructMs = SystemClock.uptimeMillis() - constructStartedAt;
        try {
            unlockEffectView = unlockEffectRenderer.asView();
        } catch (Throwable t) {
            Log.e(TAG, "unlock effect renderer view failed type=" + effect, t);
            try {
                unlockEffectRenderer.destroy();
            } catch (Throwable destroyError) {
                Log.d(TAG, "renderer cleanup ignored after view failure", destroyError);
            }
            unlockEffectRenderer = null;
            unlockEffectView = null;
            unlockEffectRendererType = -1;
            unlockEffectRendererNeedsRecreate = false;
            unlockEffectRendererRecreateReason = "";
            return;
        }
        long cacheStartedAt = SystemClock.uptimeMillis();
        loadCachedUnlockEffectBackgroundSourceIfNeeded(effect);
        long cacheMs = SystemClock.uptimeMillis() - cacheStartedAt;
        Log.i(TAG, "unlock effect renderer preloaded type=" + effect
                + " name=" + unlockEffectRenderer.effectName()
                + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt)
                + " constructMs=" + constructMs
                + " cacheLoadMs=" + cacheMs);
        unlockEffectRendererNeedsRecreate = false;
        unlockEffectRendererRecreateReason = "";
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

    private boolean canRecreateStaleLockBgRenderer() {
        return !unlockEffectGestureActive
                && !pinEntryPending
                && !pinEntryRequested
                && !pinEntrySurfaceVisible
                && !notificationShadeVisible
                && !isCallSurfaceActive();
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
        if (unlockAffordanceShownThisWake) {
            unlockAffordancePending = false;
            Log.i(TAG, "unlock affordance duplicate skipped reason=" + reason
                    + " effect=" + OverlayPrefs.unlockEffect(this));
            return;
        }
        if (shouldParkUnlockEffectOverlayWhenIdle()) {
            unlockAffordancePending = false;
            unlockAffordanceShownThisWake = true;
            parkUnlockEffectOverlayForIdle();
            Log.i(TAG, "unlock affordance skipped for idle-hidden lockbg effect="
                    + OverlayPrefs.unlockEffect(this)
                    + " reason=" + reason);
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
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                || unlockEffectRendererType == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
            if (colorScreenshotInFlight) {
                Log.i(TAG, "native affordance waiting for screenshot reason="
                        + reason
                        + " effect=" + unlockEffectRendererType);
                return true;
            }
            if (!backgroundRenderer.hasBackgroundSourceBitmap()) {
                refreshUnlockEffectBackgroundSourceIfNeeded("affordance:" + reason);
                Log.i(TAG, "native affordance waiting for background reason="
                        + reason
                        + " effect=" + unlockEffectRendererType
                        + " attempted=" + colorScreenshotAttemptedThisSession);
                return true;
            }
            return false;
        }
        if (unlockEffectRendererType == OverlayPrefs.EFFECT_S4_LENS_FLARE
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S3_RIPPLE
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S4_RIPPLE
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

    private void refreshUnlockEffectBackgroundSourceIfNeeded(final String reason) {
        int effect = OverlayPrefs.unlockEffect(this);
        if (!effectUsesScreenshotBackground(effect)
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || colorScreenshotInFlight) {
            return;
        }
        BackgroundSourceRenderer backgroundRenderer = null;
        if (unlockEffectRendererType == effect
                && unlockEffectRenderer instanceof BackgroundSourceRenderer) {
            backgroundRenderer = (BackgroundSourceRenderer) unlockEffectRenderer;
        }
        boolean hasBackground = backgroundRenderer != null
                ? backgroundRenderer.hasBackgroundSourceBitmap()
                : hasUsableEffectBackgroundCache(effect);
        if (unlockEffectBackgroundCaptureSucceededThisSession && hasBackground) {
            return;
        }
        boolean shouldRefresh = shouldRefreshUnlockEffectBackground(effect, hasBackground, reason);
        if (colorScreenshotAttemptedThisSession
                && hasBackground
                && !shouldRefresh) {
            return;
        }
        if (!shouldRefresh) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (unlockEffectBackgroundNextAttemptAt > now) {
            return;
        }
        if (!canCaptureUnlockEffectBackground()) {
            scheduleUnlockEffectBackgroundRetry(reason);
            if (shouldLogUnlockEffectBackgroundWait()) {
                Log.i(TAG, "unlock effect background capture waiting reason=" + reason
                        + " forced=" + OverlayPrefs.effectBackgroundWakeCaptureActive(this)
                        + " interactive=" + (powerManager == null || powerManager.isInteractive())
                        + " locked=" + isLockscreenLocked(false)
                        + " displayState=" + displayStateName(currentDisplayState())
                        + " sinceScreenOnMs=" + elapsedSinceScreenOn()
                        + " pkg=" + lastWindowPackage);
            }
            return;
        }
        final int captureEffect = effect;
        final int captureGeneration = ++unlockEffectBackgroundGeneration;
        colorScreenshotAttemptedThisSession = true;
        colorScreenshotInFlight = true;
        unlockEffectBackgroundCaptureAttempts++;
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
                        if (!retryUnlockEffectBackgroundCapture(
                                captureEffect, "empty:" + reason)) {
                            completeForcedEffectBackgroundRefresh("empty");
                        }
                        showPendingUnlockAffordance("background_empty:" + reason);
                        return;
                    }
                    if (captureGeneration != unlockEffectBackgroundGeneration
                            || OverlayPrefs.unlockEffect(ChargingAccessibilityService.this)
                            != captureEffect || !canCaptureUnlockEffectBackground()) {
                        Log.i(TAG, "unlock effect background screenshot discarded reason="
                                + reason
                                + " generation=" + captureGeneration
                                + "/" + unlockEffectBackgroundGeneration
                                + " effect=" + captureEffect
                                + " currentEffect="
                                + OverlayPrefs.unlockEffect(ChargingAccessibilityService.this)
                                + " pinEntryPending=" + pinEntryPending
                                + " pinEntryRequested=" + pinEntryRequested
                                + " pinEntrySurface=" + pinEntrySurfaceVisible
                                + " notificationShade=" + notificationShadeVisible
                                + " pkg=" + lastWindowPackage);
                        bitmap.recycle();
                        if (!retryUnlockEffectBackgroundCapture(
                                captureEffect, "discarded:" + reason)) {
                            completeForcedEffectBackgroundRefresh("discarded");
                        }
                        return;
                    }
                    long now = SystemClock.uptimeMillis();
                    if (!isValidUnlockEffectBackgroundScreenshot(bitmap, captureEffect, reason)) {
                        Log.i(TAG, "unlock effect background screenshot rejected reason=" + reason
                                + " effect=" + captureEffect);
                        bitmap.recycle();
                        if (!retryUnlockEffectBackgroundCapture(
                                captureEffect, "rejected:" + reason)) {
                            completeForcedEffectBackgroundRefresh("rejected");
                        }
                        showPendingUnlockAffordance("background_rejected:" + reason);
                        return;
                    }
                    applyUnlockEffectBackgroundSource(bitmap, "accessibility_screenshot");
                    persistEffectBackgroundScreenshotAsync(bitmap, captureGeneration, captureEffect);
                    unlockEffectBackgroundCapturedAt = now;
                    unlockEffectBackgroundEffect = captureEffect;
                    unlockEffectBackgroundNextAttemptAt = 0L;
                    unlockEffectBackgroundCaptureSucceededThisSession = true;
                    skipCachedEffectBackgroundLoad = false;
                    Log.i(TAG, "unlock effect background screenshot applied reason=" + reason
                            + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                            + " displayState=" + displayStateName(currentDisplayState())
                            + " effect=" + captureEffect
                            + " pkg=" + lastWindowPackage);
                    bitmap.recycle();
                    completeForcedEffectBackgroundRefresh("applied");
                    showPendingUnlockAffordance("background:" + reason);
                }

                @Override
                public void onFailure(int errorCode) {
                    colorScreenshotInFlight = false;
                    Log.i(TAG, "unlock effect background screenshot failed code=" + errorCode
                            + " reason=" + reason);
                    if (!retryUnlockEffectBackgroundCapture(
                            captureEffect, "failed:" + reason)) {
                        completeForcedEffectBackgroundRefresh("failed");
                    }
                    showPendingUnlockAffordance("background_failed:" + reason);
                }
            });
        } catch (Throwable t) {
            colorScreenshotInFlight = false;
            Log.d(TAG, "unlock effect background screenshot request failed reason=" + reason, t);
            if (!retryUnlockEffectBackgroundCapture(
                    captureEffect, "exception:" + reason)) {
                completeForcedEffectBackgroundRefresh("exception");
            }
            showPendingUnlockAffordance("background_request_failed:" + reason);
        }
    }

    private boolean shouldRunUnlockEffectBackgroundPreflight(boolean interactive,
            boolean displayOn, boolean locked, boolean aodSurface,
            boolean hideOverlaysForTouchBoxCapture, boolean hideOverlaysForBackgroundCapture,
            boolean blockedSurfaceActive) {
        if (!interactive || !displayOn || !locked || aodSurface
                || hideOverlaysForTouchBoxCapture || hideOverlaysForBackgroundCapture
                || blockedSurfaceActive || !OverlayPrefs.unlockEffectEnabled(this)) {
            return false;
        }
        int effect = OverlayPrefs.unlockEffect(this);
        if (!effectUsesCachedScreenshotBackground(effect)) {
            return false;
        }
        if (OverlayPrefs.effectBackgroundRefreshToken(this)
                != OverlayPrefs.effectBackgroundHandledRefreshToken(this, effect)) {
            return true;
        }
        if (!hasUsableEffectBackgroundCache(effect)) {
            return true;
        }
        return OverlayPrefs.effectBackgroundAutoRefreshEnabled(this);
    }

    private boolean shouldRefreshUnlockEffectBackground(int effect, boolean hasBackground,
            String reason) {
        if (OverlayPrefs.effectBackgroundWakeCaptureActive(this)
                && effectUsesCachedScreenshotBackground(effect)) {
            return true;
        }
        if (effectUsesCachedScreenshotBackground(effect)
                && OverlayPrefs.effectBackgroundRefreshToken(this)
                != OverlayPrefs.effectBackgroundHandledRefreshToken(this, effect)) {
            return true;
        }
        if (!hasBackground || unlockEffectBackgroundEffect != effect) {
            return true;
        }
        if (effectUsesCachedScreenshotBackground(effect)
                && OverlayPrefs.effectBackgroundAutoRefreshEnabled(this)) {
            if (OverlayPrefs.effectBackgroundSkipNight(this) && isEffectBackgroundQuietHour()) {
                Log.i(TAG, "unlock effect background auto refresh skipped at night reason="
                        + reason);
                return false;
            }
            long lastCapturedAt = OverlayPrefs.effectBackgroundLastCapturedAt(this, effect);
            if (lastCapturedAt <= 0L) {
                File file = OverlayPrefs.effectBackgroundFile(this, effect);
                if (file.exists()) {
                    lastCapturedAt = file.lastModified();
                }
            }
            long intervalMs = OverlayPrefs.effectBackgroundRefreshIntervalHours(this)
                    * 60L * 60L * 1000L;
            return lastCapturedAt <= 0L
                    || System.currentTimeMillis() - lastCapturedAt >= intervalMs;
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
        if (unlockEffectGestureActive || unlockEffectOverlayAttached || unlockEffectOverlayParked
                || debugLensLoopGestureActive) {
            return false;
        }
        if (getPackageName().equals(lastWindowPackage)) {
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
        return effectUsesScreenshotBackground(effect)
                && OverlayPrefs.effectBackgroundWakeCaptureActive(this)
                && unlockEffectBackgroundCaptureAttempts
                < UNLOCK_EFFECT_SCREENSHOT_MAX_ATTEMPTS;
    }

    private boolean retryUnlockEffectBackgroundCapture(int effect, String reason) {
        if (!shouldRetryUnlockEffectBackgroundCapture(effect)) {
            return false;
        }
        colorScreenshotAttemptedThisSession = false;
        scheduleUnlockEffectBackgroundRetry(reason);
        return true;
    }

    private boolean isValidUnlockEffectBackgroundScreenshot(Bitmap bitmap, int effect,
            String reason) {
        if (bitmap == null || bitmap.isRecycled()
                || bitmap.getWidth() < 100 || bitmap.getHeight() < 100) {
            return false;
        }
        int displayState = currentDisplayState();
        if (AOD_PACKAGE.equals(lastWindowPackage)
                || (displayState != Display.STATE_UNKNOWN && displayState != Display.STATE_ON)) {
            Log.i(TAG, "background screenshot rejected: non-lockscreen display reason="
                    + reason
                    + " displayState=" + displayStateName(displayState)
                    + " pkg=" + lastWindowPackage);
            return false;
        }
        if (doodleOverlayAttached || unlockEffectOverlayAttached || unlockEffectOverlayParked) {
            Log.i(TAG, "background screenshot rejected: visual overlay attached reason="
                    + reason
                    + " doodle=" + doodleOverlayAttached
                    + " fx=" + unlockEffectOverlayAttached
                    + " parked=" + unlockEffectOverlayParked);
            return false;
        }
        int columns = 18;
        int rows = 32;
        long lumaSum = 0L;
        long lumaSqSum = 0L;
        int veryDark = 0;
        int valid = 0;
        for (int y = 0; y < rows; y++) {
            int py = Math.min(bitmap.getHeight() - 1,
                    Math.max(0, (int) ((y + 0.5f) * bitmap.getHeight() / rows)));
            for (int x = 0; x < columns; x++) {
                int px = Math.min(bitmap.getWidth() - 1,
                        Math.max(0, (int) ((x + 0.5f) * bitmap.getWidth() / columns)));
                int color = bitmap.getPixel(px, py);
                int red = (color >> 16) & 0xff;
                int green = (color >> 8) & 0xff;
                int blue = color & 0xff;
                int luma = (red * 299 + green * 587 + blue * 114) / 1000;
                lumaSum += luma;
                lumaSqSum += (long) luma * luma;
                if (luma < 8) {
                    veryDark++;
                }
                valid++;
            }
        }
        if (valid <= 0) {
            return false;
        }
        float average = lumaSum / (float) valid;
        float variance = lumaSqSum / (float) valid - average * average;
        float darkRatio = veryDark / (float) valid;
        if ((average < 10f && darkRatio > 0.92f)
                || (average < 24f && variance < 20f)) {
            Log.i(TAG, "background screenshot rejected: black/flat frame reason=" + reason
                    + " effect=" + effect
                    + " avg=" + average
                    + " variance=" + variance
                    + " darkRatio=" + darkRatio);
            return false;
        }
        return true;
    }

    private void scheduleUnlockEffectBackgroundRetry(String reason) {
        int effect = OverlayPrefs.unlockEffect(this);
        if (colorScreenshotInFlight) {
            return;
        }
        if (!OverlayPrefs.effectBackgroundWakeCaptureActive(this)) {
            // Normal wakes are cache-only. A missing/stale cache is refreshed only by the
            // explicit one-shot request (or the opt-in scheduled refresh receiver).
            handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
            unlockEffectBackgroundNextAttemptAt = Long.MAX_VALUE;
            return;
        }
        long now = SystemClock.uptimeMillis();
        long sinceScreenOn = elapsedSinceScreenOn();
        long delayMs = UNLOCK_EFFECT_SCREENSHOT_RETRY_MS;
        long minScreenOnMs = unlockEffectScreenshotMinScreenOnMs(effect);
        if (sinceScreenOn >= 0L && sinceScreenOn < minScreenOnMs) {
            delayMs = Math.max(delayMs, minScreenOnMs - sinceScreenOn);
        }
        if (forcedEffectBackgroundOverlayClearStartedAt > 0L) {
            long overlayClearRemainingMs = TOUCH_BOX_SCREENSHOT_OVERLAY_CLEAR_MS
                    - (now - forcedEffectBackgroundOverlayClearStartedAt);
            if (overlayClearRemainingMs > 0L) {
                delayMs = Math.max(delayMs, overlayClearRemainingMs);
            }
        }
        long dueAt = now + delayMs;
        if (unlockEffectBackgroundNextAttemptAt > now
                && unlockEffectBackgroundNextAttemptAt <= dueAt) {
            return;
        }
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        unlockEffectBackgroundNextAttemptAt = dueAt;
        handler.postDelayed(unlockEffectBackgroundRetryRunnable, delayMs);
        Log.i(TAG, "unlock effect background retry scheduled reason=" + reason
                + " delayMs=" + delayMs);
    }

    private boolean shouldLogUnlockEffectBackgroundWait() {
        long now = SystemClock.uptimeMillis();
        if (unlockEffectBackgroundLastWaitLogAt > 0L
                && now - unlockEffectBackgroundLastWaitLogAt
                < UNLOCK_EFFECT_SCREENSHOT_WAIT_LOG_INTERVAL_MS) {
            return false;
        }
        unlockEffectBackgroundLastWaitLogAt = now;
        return true;
    }

    private void scheduleEffectBackgroundRefreshAlarm(String reason) {
        if (!OverlayPrefs.effectBackgroundAutoRefreshEnabled(this)
                || !OverlayPrefs.effectBackgroundForceRecapture(this)) {
            cancelEffectBackgroundRefreshAlarm();
            return;
        }
        int effect = OverlayPrefs.unlockEffect(this);
        if (!effectUsesCachedScreenshotBackground(effect)) {
            cancelEffectBackgroundRefreshAlarm();
            return;
        }
        long triggerAt = nextEffectBackgroundRefreshAt(effect);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent intent = effectBackgroundRefreshPendingIntent(
                PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, intent);
        }
        Log.i(TAG, "effect background hard refresh alarm scheduled reason=" + reason
                + " effect=" + effect
                + " inMs=" + Math.max(0L, triggerAt - System.currentTimeMillis()));
    }

    private void cancelEffectBackgroundRefreshAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent intent = effectBackgroundRefreshPendingIntent(
                PendingIntent.FLAG_NO_CREATE);
        if (intent != null) {
            alarmManager.cancel(intent);
        }
    }

    private PendingIntent effectBackgroundRefreshPendingIntent(int flags) {
        Intent intent = new Intent(this, EffectBackgroundRefreshReceiver.class);
        intent.setAction(ACTION_EFFECT_BACKGROUND_REFRESH);
        int pendingFlags = flags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(this, 4187, intent, pendingFlags);
    }

    private long nextEffectBackgroundRefreshAt(int effect) {
        long now = System.currentTimeMillis();
        long last = OverlayPrefs.effectBackgroundLastCapturedAt(this, effect);
        File file = OverlayPrefs.effectBackgroundFile(this, effect);
        if (last <= 0L && file.exists()) {
            last = file.lastModified();
        }
        long intervalMs = OverlayPrefs.effectBackgroundRefreshIntervalHours(this)
                * 60L * 60L * 1000L;
        long dueAt = last <= 0L ? now + 30_000L : last + intervalMs;
        if (OverlayPrefs.effectBackgroundSkipNight(this)) {
            dueAt = moveOutOfQuietHours(dueAt);
        }
        return Math.max(now + 1000L, dueAt);
    }

    private long moveOutOfQuietHours(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (hour >= 23) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 7);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTimeInMillis();
        }
        if (hour < 7) {
            calendar.set(Calendar.HOUR_OF_DAY, 7);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTimeInMillis();
        }
        return timeMs;
    }

    private boolean isEffectBackgroundQuietHour() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return hour >= 23 || hour < 7;
    }

    private void completeForcedEffectBackgroundRefresh(String reason) {
        if (!OverlayPrefs.effectBackgroundWakeCaptureActive(this)) {
            return;
        }
        boolean shouldRelock = OverlayPrefs.effectBackgroundWakeCaptureShouldRelock(this);
        OverlayPrefs.get(this).edit()
                .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE, false)
                .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK, false)
                .apply();
        handler.removeCallbacks(forcedEffectBackgroundTimeoutRunnable);
        handler.removeCallbacks(forcedEffectBackgroundSleepRunnable);
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        unlockEffectBackgroundNextAttemptAt = 0L;
        forcedEffectBackgroundOverlayClearStartedAt = 0L;
        if (shouldRelock) {
            handler.postDelayed(forcedEffectBackgroundSleepRunnable,
                    EFFECT_BACKGROUND_WAKE_LOCK_DELAY_MS);
        }
        scheduleEffectBackgroundRefreshAlarm("forced_complete:" + reason);
        Log.i(TAG, "forced effect background refresh complete reason=" + reason
                + " relock=" + shouldRelock
                + " attempts=" + unlockEffectBackgroundCaptureAttempts);
    }

    private long unlockEffectScreenshotMinScreenOnMs(int effect) {
        long minScreenOnMs = UNLOCK_EFFECT_SCREENSHOT_MIN_SCREEN_ON_MS;
        if (effect == OverlayPrefs.EFFECT_S3_RIPPLE
                || effect == OverlayPrefs.EFFECT_S4_RIPPLE) {
            minScreenOnMs = S3_RIPPLE_SCREENSHOT_MIN_SCREEN_ON_MS;
        } else if (effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS) {
            minScreenOnMs = S5_POPPING_SCREENSHOT_MIN_SCREEN_ON_MS;
        } else if (effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC) {
            minScreenOnMs = S4_LOCKBG_SCREENSHOT_MIN_SCREEN_ON_MS;
        } else if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET) {
            minScreenOnMs = COLOUR_DROPLET_SCREENSHOT_MIN_SCREEN_ON_MS;
        } else if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
            minScreenOnMs = SPARKLING_BUBBLES_SCREENSHOT_MIN_SCREEN_ON_MS;
        }
        if (OverlayPrefs.effectBackgroundWakeCaptureActive(this)) {
            minScreenOnMs = Math.max(minScreenOnMs,
                    EFFECT_BACKGROUND_WAKE_CAPTURE_MIN_SCREEN_ON_MS);
        }
        return minScreenOnMs;
    }

    private boolean currentUnlockEffectHasBackground(int effect) {
        return unlockEffectRendererType == effect
                && unlockEffectRenderer instanceof BackgroundSourceRenderer
                && ((BackgroundSourceRenderer) unlockEffectRenderer).hasBackgroundSourceBitmap();
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
        unlockEffectBackgroundGeneration++;
        colorScreenshotInFlight = false;
        colorScreenshotAttemptedThisSession = false;
        unlockEffectBackgroundCaptureSucceededThisSession = false;
        unlockEffectBackgroundNextAttemptAt = 0L;
        unlockEffectBackgroundLastWaitLogAt = 0L;
        forcedEffectBackgroundOverlayClearStartedAt = 0L;
        unlockEffectBackgroundCaptureAttempts = 0;
        skipCachedEffectBackgroundLoad = false;
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        preloadUnlockEffectRenderer();
        Log.i(TAG, "unlock effect background recapture requested; old cache preserved");
    }

    private void loadCachedUnlockEffectBackgroundSourceIfNeeded(int effect) {
        long startedAt = SystemClock.uptimeMillis();
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
        File file = findBestEffectBackgroundCacheFile(effect);
        if (file == null) {
            return;
        }
        repairSharedEffectBackgroundMetadataIfNeeded(effect, file);
        boolean ownCache = file.equals(OverlayPrefs.effectBackgroundFile(this, effect));
        Bitmap bitmap = null;
        try {
            long fileLength = file.length();
            long fileModified = file.lastModified();
            boolean memoryCacheHit = hasCachedUnlockEffectBackground(
                    effect, fileLength, fileModified);
            long decodeStartedAt = SystemClock.uptimeMillis();
            if (memoryCacheHit) {
                bitmap = cachedUnlockEffectBackgroundBitmap;
            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (bitmap != null && !bitmap.isRecycled()) {
                    replaceCachedUnlockEffectBackgroundBitmap(
                            bitmap, effect, fileLength, fileModified);
                }
            }
            long decodeMs = SystemClock.uptimeMillis() - decodeStartedAt;
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            long applyStartedAt = SystemClock.uptimeMillis();
            backgroundRenderer.setBackgroundSourceBitmap(bitmap, "cached_effect_background");
            long applyMs = SystemClock.uptimeMillis() - applyStartedAt;
            unlockEffectBackgroundCapturedAt = SystemClock.uptimeMillis();
            unlockEffectBackgroundEffect = effect;
            colorScreenshotAttemptedThisSession = true;
            Log.i(TAG, "unlock effect background cache loaded size="
                    + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + " effect=" + effect
                    + " fileKb=" + Math.max(1L, fileLength / 1024L)
                    + " memoryCache=" + memoryCacheHit
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt)
                    + " decodeMs=" + decodeMs
                    + " applyMs=" + applyMs);
        } catch (Throwable t) {
            Log.d(TAG, "unlock effect background cache load failed", t);
        }
    }

    private boolean hasUsableEffectBackgroundCache(int effect) {
        return findBestEffectBackgroundCacheFile(effect) != null;
    }

    private File findBestEffectBackgroundCacheFile(int effect) {
        File shared = OverlayPrefs.effectBackgroundFile(this, effect);
        if (isUsableEffectBackgroundCacheFile(shared)) {
            return shared;
        }
        File legacy = findLatestLegacyEffectBackgroundCacheFile();
        if (legacy != null && copyEffectBackgroundCacheFile(legacy, shared)) {
            Log.i(TAG, "effect background legacy cache migrated to shared path");
            return shared;
        }
        return null;
    }

    private void repairSharedEffectBackgroundMetadataIfNeeded(int effect, File file) {
        File shared = OverlayPrefs.effectBackgroundFile(this, effect);
        int refreshToken = OverlayPrefs.effectBackgroundRefreshToken(this);
        if (file == null || !file.equals(shared) || refreshToken <= 0
                || OverlayPrefs.effectBackgroundHandledRefreshToken(this, effect)
                == refreshToken) {
            return;
        }
        int[] effects = {
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                OverlayPrefs.EFFECT_S3_RIPPLE,
                OverlayPrefs.EFFECT_S4_RIPPLE,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
        };
        for (int candidate : effects) {
            if (candidate == effect
                    || OverlayPrefs.effectBackgroundHandledRefreshToken(this, candidate)
                    != refreshToken
                    || OverlayPrefs.effectBackgroundLastCapturedAt(this, candidate) <= 0L) {
                continue;
            }
            OverlayPrefs.saveEffectBackgroundLastCapturedAt(
                    this, effect, Math.max(1L, file.lastModified()));
            OverlayPrefs.saveEffectBackgroundHandledRefreshToken(this, effect, refreshToken);
            Log.i(TAG, "shared effect background metadata repaired effect=" + effect
                    + " token=" + refreshToken);
            return;
        }
    }

    private File findLatestLegacyEffectBackgroundCacheFile() {
        File best = null;
        int[] effects = {
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                OverlayPrefs.EFFECT_S3_RIPPLE,
                OverlayPrefs.EFFECT_S4_RIPPLE,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
        };
        for (int candidate : effects) {
            File file = OverlayPrefs.legacyEffectBackgroundFile(this, candidate);
            if (!isUsableEffectBackgroundCacheFile(file)) {
                continue;
            }
            if (best == null || file.lastModified() > best.lastModified()) {
                best = file;
            }
        }
        return best;
    }

    private boolean copyEffectBackgroundCacheFile(File source, File target) {
        if (!isUsableEffectBackgroundCacheFile(source) || target == null) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        File temp = new File(parent, target.getName() + ".tmp");
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(temp);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            return swapEffectBackgroundCacheFile(temp, target);
        } catch (Throwable t) {
            Log.d(TAG, "effect background legacy migration failed", t);
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
            if (temp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    private boolean isUsableEffectBackgroundCacheFile(File file) {
        return file != null && file.exists() && file.length() > 0L;
    }

    private boolean hasCachedUnlockEffectBackground(int effect, long fileLength,
            long fileModified) {
        return cachedUnlockEffectBackgroundBitmap != null
                && !cachedUnlockEffectBackgroundBitmap.isRecycled()
                && cachedUnlockEffectBackgroundEffect == effect
                && cachedUnlockEffectBackgroundFileLength == fileLength
                && cachedUnlockEffectBackgroundFileModified == fileModified;
    }

    private void replaceCachedUnlockEffectBackgroundBitmap(
            Bitmap bitmap,
            int effect,
            long fileLength,
            long fileModified) {
        if (bitmap == cachedUnlockEffectBackgroundBitmap) {
            cachedUnlockEffectBackgroundEffect = effect;
            cachedUnlockEffectBackgroundFileLength = fileLength;
            cachedUnlockEffectBackgroundFileModified = fileModified;
            return;
        }
        clearCachedUnlockEffectBackgroundBitmap();
        cachedUnlockEffectBackgroundBitmap = bitmap;
        cachedUnlockEffectBackgroundEffect = effect;
        cachedUnlockEffectBackgroundFileLength = fileLength;
        cachedUnlockEffectBackgroundFileModified = fileModified;
    }

    private void clearCachedUnlockEffectBackgroundBitmap() {
        if (cachedUnlockEffectBackgroundBitmap != null
                && !cachedUnlockEffectBackgroundBitmap.isRecycled()) {
            cachedUnlockEffectBackgroundBitmap.recycle();
        }
        cachedUnlockEffectBackgroundBitmap = null;
        cachedUnlockEffectBackgroundEffect = -1;
        cachedUnlockEffectBackgroundFileLength = 0L;
        cachedUnlockEffectBackgroundFileModified = 0L;
    }

    private void cachePersistedUnlockEffectBackgroundBitmap(Bitmap source, File file,
            int effect) {
        if (source == null || source.isRecycled() || file == null || !file.exists()) {
            return;
        }
        try {
            Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
            if (copy == null || copy.isRecycled()) {
                return;
            }
            copy.prepareToDraw();
            replaceCachedUnlockEffectBackgroundBitmap(copy, effect,
                    file.length(), file.lastModified());
        } catch (Throwable t) {
            Log.d(TAG, "unlock effect memory background cache update failed", t);
        }
    }

    private void resetUnlockEffectBackgroundSession(boolean preserveCachedBackground) {
        colorScreenshotAttemptedThisSession = false;
        unlockEffectBackgroundCaptureSucceededThisSession = false;
        unlockEffectBackgroundNextAttemptAt = 0L;
        unlockEffectBackgroundLastWaitLogAt = 0L;
        forcedEffectBackgroundOverlayClearStartedAt = 0L;
        unlockEffectBackgroundCaptureAttempts = 0;
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
        return effectUsesScreenshotBackground(effect);
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
        return persistTouchBoxScreenshot(bitmap, sourceName, true);
    }

    private boolean persistTouchBoxScreenshot(Bitmap bitmap, String sourceName,
            boolean updateTouchBoxState) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        return writeTouchBoxScreenshotFile(bitmap, sourceName, updateTouchBoxState);
    }

    private boolean writeTouchBoxScreenshotFile(Bitmap bitmap, String sourceName,
            boolean updateTouchBoxState) {
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
            if (updateTouchBoxState) {
                int requestId = pendingTouchBoxScreenshotRequestId();
                SharedPreferences.Editor editor = OverlayPrefs.get(this).edit()
                        .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                                OverlayPrefs.TOUCH_BOX_CAPTURE_READY)
                        .remove(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR);
                if (requestId > 0) {
                    editor.putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_RESULT_ID, requestId);
                }
                editor.apply();
            }
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

    private void persistEffectBackgroundScreenshotAsync(Bitmap bitmap, final int generation,
            final int effect) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        final Bitmap copy;
        try {
            copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable t) {
            Log.d(TAG, "effect background async copy failed", t);
            return;
        }
        if (copy == null || copy.isRecycled()) {
            return;
        }
        try {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final File file = OverlayPrefs.effectBackgroundFile(
                            ChargingAccessibilityService.this, effect);
                    final File temp = new File(file.getParentFile(), file.getName() + ".tmp");
                    boolean saved = writeBitmapPngFile(copy, temp);
                    if (saved) {
                        saved = swapEffectBackgroundCacheFile(temp, file);
                    }
                    if (!saved) {
                        if (temp.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            temp.delete();
                        }
                        copy.recycle();
                        return;
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != unlockEffectBackgroundGeneration
                                    || copy.isRecycled()
                                    || !file.exists()) {
                                copy.recycle();
                                return;
                            }
                            replaceCachedUnlockEffectBackgroundBitmap(
                                    copy,
                                    effect,
                                    file.length(),
                                    file.lastModified());
                            markSharedEffectBackgroundCacheCurrent(
                                    System.currentTimeMillis(),
                                    OverlayPrefs.effectBackgroundRefreshToken(
                                            ChargingAccessibilityService.this));
                            scheduleEffectBackgroundRefreshAlarm("cache_saved");
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            copy.recycle();
            Log.d(TAG, "effect background async save rejected", e);
        }
    }

    private void markSharedEffectBackgroundCacheCurrent(long timestamp, int refreshToken) {
        int[] effects = {
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                OverlayPrefs.EFFECT_S3_RIPPLE,
                OverlayPrefs.EFFECT_S4_RIPPLE,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
        };
        SharedPreferences.Editor editor = OverlayPrefs.get(this).edit();
        for (int effect : effects) {
            editor.putLong(
                    OverlayPrefs.EFFECT_BACKGROUND_LAST_CAPTURE_PREFIX + effect,
                    Math.max(0L, timestamp));
            editor.putInt(
                    OverlayPrefs.EFFECT_BACKGROUND_HANDLED_REFRESH_TOKEN_PREFIX + effect,
                    Math.max(0, refreshToken));
        }
        editor.apply();
    }

    private boolean writeBitmapPngFile(Bitmap bitmap, File file) {
        if (bitmap == null || bitmap.isRecycled() || file == null) {
            return false;
        }
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return false;
            }
            output.flush();
            return true;
        } catch (Throwable t) {
            Log.d(TAG, "bitmap png save failed path=" + file.getAbsolutePath(), t);
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                    // Best effort close for background bitmap writes.
                }
            }
        }
    }

    private boolean swapEffectBackgroundCacheFile(File temp, File target) {
        if (temp == null || target == null || !temp.exists()) {
            return false;
        }
        if (temp.renameTo(target)) {
            return true;
        }
        return false;
    }

    private boolean effectUsesScreenshotBackground(int effect) {
        return effect == OverlayPrefs.EFFECT_S4_LENS_FLARE
                || effect == OverlayPrefs.EFFECT_S3_RIPPLE
                || effect == OverlayPrefs.EFFECT_S4_RIPPLE
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC;
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
                if (isSeasonalUnlockPartnerModeEnabled()) {
                    return beginSeasonalUnlockPartnerGesture(screenX, screenY);
                }
                return beginUnlockEffectGesture(screenX, screenY);
            }

            @Override
            public void onTouchMoved(float screenX, float screenY,
                    float deltaX, float deltaY, float distance) {
                if (seasonalUnlockPartnerGestureActive) {
                    updateSeasonalUnlockPartnerGesture(screenX, screenY);
                    return;
                }
                updateUnlockEffectGesture(screenX, screenY);
            }

            @Override
            public void onTouchEnded(float screenX, float screenY,
                    float deltaX, float deltaY, float distance) {
                if (seasonalUnlockPartnerGestureActive) {
                    finishSeasonalUnlockPartnerGesture(screenX, screenY, distance);
                    return;
                }
                finishUnlockEffectGesture(screenX, screenY, distance);
            }

            @Override
            public void onTouchCancelled() {
                if (seasonalUnlockPartnerGestureActive) {
                    cancelSeasonalUnlockPartnerGesture();
                    return;
                }
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
        try {
            windowManager.addView(touchDebugView, touchDebugParams);
        } catch (RuntimeException e) {
            if (isAlreadyAddedWindowError(e)) {
                Log.w(TAG, "touch listen box already attached");
            } else {
                Log.e(TAG, "touch listen box addView failed", e);
                touchDebugView = null;
                touchDebugParams = null;
                touchDebugTouchable = false;
                return;
            }
        }
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
        overlayView.setDoodleSizePercent(OverlayPrefs.doodleSizePercent(this));
        overlayView.setBatteryPercent(batteryPercent);
        overlayView.setDebugRollingCharge(OverlayPrefs.debugRollingCharge(this));
    }

    private void removeOverlay() {
        destroyDoodleOverlay();
        destroySeasonalUnlockPartnerOverlay();
        destroyUnlockEffectOverlay();
        removeTouchDebugOverlay();
    }

    private void stopAllRuntimeSurfaces() {
        releaseHotWakeLock();
        handler.removeCallbacks(screenOnRefreshRunnable);
        handler.removeCallbacks(screenOffPrearmRunnable);
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        stopLockscreenSessionPolling();
        stopDebugLensLoop();
        clearActiveUnlockEffectProfile();
        unlockTouchCachedWhileScreenOff = false;
        unlockAffordancePending = false;
        unlockAffordanceShownThisWake = false;
        unlockFxVisible = false;
        unlockEffectBackgroundGeneration++;
        colorScreenshotInFlight = false;
        unlockEffectBackgroundNextAttemptAt = 0L;
        clearCachedUnlockEffectBackgroundBitmap();
        destroyDoodleOverlay();
        destroySeasonalUnlockPartnerOverlay();
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
            doodleOverlayParked = false;
        }
    }

    private void destroyDoodleOverlay() {
        removeDoodleOverlay();
        overlayView = null;
    }

    private void hideRuntimeSurfacesForCall(String reason) {
        stopDebugLensLoop();
        cancelSeasonalUnlockPartnerGesture();
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.cancelGesture();
            unlockEffectRenderer.resetEffect();
        }
        unlockEffectGestureActive = false;
        unlockFxVisible = false;
        pinEntryPending = false;
        removeDoodleOverlay();
        destroySeasonalUnlockPartnerOverlay();
        removeUnlockEffectOverlay(true);
        removeTouchDebugOverlay();
        Log.i(TAG, "runtime surfaces hidden for call reason=" + reason);
    }

    private void removeSeasonalUnlockPartnerOverlay() {
        if (seasonalUnlockPartnerOverlayAttached && seasonalUnlockPartnerView != null) {
            try {
                windowManager.removeView(seasonalUnlockPartnerView);
            } catch (RuntimeException ignored) {
                // Display/service teardown can remove accessibility windows first.
            }
            seasonalUnlockPartnerOverlayAttached = false;
            seasonalUnlockPartnerOverlayParked = false;
        }
    }

    private void destroySeasonalUnlockPartnerOverlay() {
        removeSeasonalUnlockPartnerOverlay();
        seasonalUnlockPartnerGestureActive = false;
        seasonalUnlockPartnerOverlayParked = false;
        if (seasonalUnlockPartnerRenderer != null) {
            seasonalUnlockPartnerRenderer.destroy();
        }
        seasonalUnlockPartnerRenderer = null;
        seasonalUnlockPartnerView = null;
    }

    private void unloadUnlockEffectsForDoodleMode(String reason) {
        boolean hadRuntimeState = hasUnlockEffectRuntimeState();
        handler.removeCallbacks(pinEntryRunnable);
        handler.removeCallbacks(pinEntrySwipeRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        handler.removeCallbacks(unlockEffectIdleHideRunnable);
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        stopDebugLensLoop();
        clearActiveUnlockEffectProfile();
        pinEntryPending = false;
        unlockTouchCachedWhileScreenOff = false;
        unlockAffordancePending = false;
        unlockAffordanceShownThisWake = false;
        unlockFxVisible = false;
        unlockEffectGestureActive = false;
        unlockEffectBackgroundGeneration++;
        colorScreenshotInFlight = false;
        if (!hadRuntimeState) {
            return;
        }
        clearCachedUnlockEffectBackgroundBitmap();
        destroyUnlockEffectOverlay();
        removeTouchDebugOverlay();
        Log.i(TAG, "unlock effects unloaded for charging doodle mode reason=" + reason);
    }

    private boolean hasUnlockEffectRuntimeState() {
        return unlockEffectRenderer != null
                || unlockEffectView != null
                || unlockEffectOverlayAttached
                || unlockEffectOverlayParked
                || touchDebugView != null
                || touchDebugParams != null
                || unlockTouchCachedWhileScreenOff
                || unlockAffordancePending
                || unlockAffordanceShownThisWake
                || unlockFxVisible
                || unlockEffectGestureActive
                || pinEntryPending
                || debugLensLoopScheduled
                || colorScreenshotInFlight
                || cachedUnlockEffectBackgroundBitmap != null
                || unlockEffectBackgroundEffect >= 0;
    }

    private void removeUnlockEffectOverlay() {
        removeUnlockEffectOverlay(false);
    }

    private void removeUnlockEffectOverlay(boolean destroyingRenderer) {
        stopDebugLensLoop();
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.resetEffect();
        }
        boolean removed = false;
        int removedType = unlockEffectRendererType;
        if (!destroyingRenderer
                && unlockEffectOverlayAttached
                && shouldKeepNativePhysicsOverlayAttachedDuringHide(removedType)) {
            parkNativePhysicsRendererState(unlockEffectRenderer);
            hideUnlockEffectView(unlockEffectView);
            Log.i(TAG, "native physics overlay kept attached while hidden type=" + removedType);
            return;
        }
        if (unlockEffectOverlayAttached && unlockEffectView != null) {
            try {
                windowManager.removeView(unlockEffectView);
                removed = true;
            } catch (RuntimeException ignored) {
                // The service can be torn down after the effect window was already removed.
            }
            unlockEffectOverlayAttached = false;
            unlockEffectOverlayParked = false;
        }
        if (removed && !destroyingRenderer && isSamsungLockBgEffect(removedType)) {
            markUnlockEffectRendererStale("overlay_detached");
        }
    }

    private void destroyUnlockEffectOverlay() {
        int destroyedType = unlockEffectRendererType;
        boolean clearBackgroundSession = effectUsesScreenshotBackground(destroyedType);
        removeUnlockEffectOverlay(true);
        if (clearBackgroundSession) {
            unlockEffectBackgroundGeneration++;
            colorScreenshotInFlight = false;
            colorScreenshotAttemptedThisSession = false;
            unlockEffectBackgroundNextAttemptAt = 0L;
            unlockEffectBackgroundCapturedAt = 0L;
            unlockEffectBackgroundEffect = -1;
            handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        }
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.destroy();
            unlockEffectRenderer = null;
            if (shouldKeepNativePhysicsOverlayAttachedDuringHide(destroyedType)) {
                scheduleNativePhysicsPostDestroyGc(destroyedType);
            }
        }
        unlockEffectView = null;
        unlockEffectRendererType = -1;
        unlockEffectOverlayParked = false;
        unlockEffectGestureActive = false;
        unlockEffectRendererNeedsRecreate = false;
        unlockEffectRendererRecreateReason = "";
    }

    private void scheduleNativePhysicsPostDestroyGc(final int effect) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                System.gc();
                Log.i(TAG, "native physics post-destroy GC requested type=" + effect);
            }
        }, 250L);
    }

    private void parkNativePhysicsRendererState(UnlockEffectRenderer renderer) {
        if (renderer instanceof ColourDropletEffectView) {
            ((ColourDropletEffectView) renderer).parkForReuse();
        } else if (renderer instanceof SparklingBubblesEffectView) {
            ((SparklingBubblesEffectView) renderer).parkForReuse();
        } else if (renderer != null) {
            renderer.resetEffect();
        }
    }

    private void resumeNativePhysicsRendererState(UnlockEffectRenderer renderer) {
        if (renderer instanceof ColourDropletEffectView) {
            ((ColourDropletEffectView) renderer).resumeForReuse();
        } else if (renderer instanceof SparklingBubblesEffectView) {
            ((SparklingBubblesEffectView) renderer).resumeForReuse();
        }
    }

    private void parkUnlockEffectOverlayForScreenOff() {
        if (unlockEffectOverlayParked) {
            return;
        }
        parkNativePhysicsRendererState(unlockEffectRenderer);
        hideUnlockEffectView(unlockEffectView);
    }

    private void parkUnlockEffectOverlayForIdle() {
        if (!shouldParkUnlockEffectOverlayWhenIdle()) {
            return;
        }
        if (unlockEffectOverlayParked) {
            return;
        }
        parkNativePhysicsRendererState(unlockEffectRenderer);
        hideUnlockEffectView(unlockEffectView);
    }

    private void scheduleUnlockEffectIdleHide() {
        scheduleUnlockEffectIdleHide(LOCKBG_IDLE_HIDE_DELAY_MS);
    }

    private void scheduleUnlockEffectIdleHide(long delayMs) {
        handler.removeCallbacks(unlockEffectIdleHideRunnable);
        handler.postDelayed(unlockEffectIdleHideRunnable, Math.max(0L, delayMs));
    }

    private void restoreUnlockEffectOverlayAfterScreenOff() {
        holdHotWakeLock("restore_after_screen_off");
        syncUnlockEffectOverlay(true);
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.resetEffect();
        }
        scheduleUnlockEffectWarmBurst("restore_after_screen_off");
    }

    private void holdHotWakeLock(String reason) {
        if (hotWakeLock == null) {
            return;
        }
        try {
            hotWakeLock.acquire(HOT_WAKE_LOCK_MS);
            Log.i(TAG, "hot wake lock held reason=" + reason
                    + " timeoutMs=" + HOT_WAKE_LOCK_MS);
        } catch (RuntimeException e) {
            Log.d(TAG, "hot wake lock ignored reason=" + reason, e);
        }
    }

    private void releaseHotWakeLock() {
        if (hotWakeLock == null) {
            return;
        }
        try {
            if (hotWakeLock.isHeld()) {
                hotWakeLock.release();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void scheduleUnlockEffectWarmBurst(final String reason) {
        final int generation = ++unlockEffectWarmBurstGeneration;
        runUnlockEffectWarmFrame(reason + ":now");
        scheduleUnlockEffectWarmFrame(reason + ":fast", generation, WARM_BURST_FAST_MS);
        scheduleUnlockEffectWarmFrame(reason + ":settle", generation, WARM_BURST_SETTLE_MS);
        scheduleUnlockEffectWarmFrame(reason + ":late", generation, WARM_BURST_LATE_MS);
    }

    private void scheduleUnlockEffectWarmFrame(final String reason, final int generation,
            long delayMs) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (generation == unlockEffectWarmBurstGeneration) {
                    runUnlockEffectWarmFrame(reason);
                }
            }
        }, delayMs);
    }

    private void runUnlockEffectWarmFrame(String reason) {
        if (unlockEffectRenderer == null || unlockEffectView == null) {
            return;
        }
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = isLockscreenLocked(false);
        if (!locked && !unlockEffectOverlayAttached) {
            return;
        }
        try {
            unlockEffectRenderer.warmUp();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                unlockEffectView.postInvalidateOnAnimation();
            } else {
                unlockEffectView.invalidate();
            }
            Log.i(TAG, "unlock effect warm frame reason=" + reason
                    + " interactive=" + interactive
                    + " locked=" + locked
                    + " attached=" + unlockEffectOverlayAttached
                    + " parked=" + unlockEffectOverlayParked
                    + " type=" + unlockEffectRendererType);
        } catch (Throwable t) {
            Log.d(TAG, "unlock effect warm frame ignored reason=" + reason, t);
        }
    }

    private void hideUnlockEffectView(View view) {
        if (view == null) {
            return;
        }
        view.setAlpha(0f);
        // Keep the accessibility window and renderer Surface allocated while parked.
        // INVISIBLE destroys the Window Surface even though the Java renderer stays warm.
        view.setVisibility(View.VISIBLE);
        unlockEffectOverlayParked = true;
    }

    private void showUnlockEffectView(View view) {
        if (view == null) {
            return;
        }
        boolean wasParked = unlockEffectOverlayParked
                || view.getVisibility() != View.VISIBLE
                || view.getAlpha() < 1f;
        view.setVisibility(View.VISIBLE);
        view.setAlpha(1f);
        unlockEffectOverlayParked = false;
        resumeNativePhysicsRendererState(unlockEffectRenderer);
        if (wasParked) {
            if (unlockEffectRenderer != null) {
                unlockEffectRenderer.warmUp();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                view.postInvalidateOnAnimation();
            } else {
                view.invalidate();
            }
            Log.i(TAG, "unlock effect surface resumed warm type="
                    + unlockEffectRendererType);
        }
    }

    private boolean isAlreadyAddedWindowError(RuntimeException error) {
        return error instanceof IllegalStateException
                && error.getMessage() != null
                && error.getMessage().contains("already been added");
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
        try {
            windowManager.updateViewLayout(touchDebugView, touchDebugParams);
        } catch (RuntimeException e) {
            Log.e(TAG, "touch listen box updateViewLayout failed", e);
            removeTouchDebugOverlay();
            return;
        }
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
        int displayState = currentDisplayState();
        boolean displayOn = displayState == Display.STATE_UNKNOWN || displayState == Display.STATE_ON;
        boolean aodSurface = AOD_PACKAGE.equals(lastWindowPackage);
        boolean showDoodle = isSharedRuntimeSurfaceAllowed(
                interactive,
                displayOn,
                locked,
                aodSurface,
                false,
                false,
                blockedSurfaceActive)
                && isChargingDoodleModeEnabled();
        return isSharedRuntimeSurfaceAllowed(
                interactive,
                displayOn,
                locked,
                aodSurface,
                false,
                false,
                blockedSurfaceActive)
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
        int displayState = currentDisplayState();
        boolean displayOn = displayState == Display.STATE_UNKNOWN || displayState == Display.STATE_ON;
        boolean aodSurface = AOD_PACKAGE.equals(lastWindowPackage);
        return isSharedRuntimeSurfaceAllowed(
                interactive,
                displayOn,
                locked,
                aodSurface,
                false,
                false,
                blockedSurfaceActive)
                && isChargingDoodleModeEnabled();
    }

    private boolean isDoodleEnabledForLockscreen(boolean blockedSurfaceActive) {
        return !blockedSurfaceActive && isChargingDoodleModeEnabled();
    }

    private boolean isSharedRuntimeSurfaceAllowed(boolean interactive, boolean displayOn,
            boolean locked, boolean aodSurface, boolean hideOverlaysForTouchBoxCapture,
            boolean hideOverlaysForBackgroundCapture, boolean blockedSurfaceActive) {
        return OverlayPrefs.masterEnabled(this)
                && interactive
                && displayOn
                && locked
                && !aodSurface
                && !hideOverlaysForTouchBoxCapture
                && !hideOverlaysForBackgroundCapture
                && !blockedSurfaceActive;
    }

    private boolean isChargingDoodleModeEnabled() {
        return OverlayPrefs.masterEnabled(this)
                && charging
                && OverlayPrefs.showDoodle(this)
                && OverlayPrefs.showLock(this);
    }

    private void playLockSoundForScreenOff() {
        if (!interactiveSessionWasUnlocked) {
            Log.i(TAG, "lock sound suppressed reason=screen_was_never_unlocked"
                    + " sinceScreenOnMs=" + elapsedSinceScreenOn());
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastLockSoundPlayedAt < LOCK_SOUND_THROTTLE_MS) {
            return;
        }
        if (lockSoundPlayer == null
                || !OverlayPrefs.masterEnabled(this)
                || !OverlayPrefs.lockSoundEnabled(this)
                || isCallSurfaceActive()
                || notificationShadeVisible) {
            return;
        }
        lastLockSoundPlayedAt = now;
        if (isChargingDoodleModeEnabled()) {
            lockSoundPlayer.playSeasonalLock(OverlayPrefs.seasonMode(this));
            return;
        }
        if (OverlayPrefs.unlockEffectEnabled(this)) {
            lockSoundPlayer.playEffectLock(OverlayPrefs.unlockEffect(this));
        }
    }

    private boolean isSeasonalUnlockPartnerModeEnabled() {
        return isChargingDoodleModeEnabled()
                && OverlayPrefs.seasonalUnlockPartner(this);
    }

    private boolean isSeasonalUnlockSurfaceHoldActive(boolean pinSurfaceVisible,
            boolean shadeVisible, boolean callSurface) {
        if (seasonalUnlockSurfaceHoldUntil <= 0L
                || !isSeasonalUnlockPartnerModeEnabled()
                || pinSurfaceVisible
                || shadeVisible
                || callSurface) {
            if (pinSurfaceVisible || shadeVisible || callSurface) {
                seasonalUnlockSurfaceHoldUntil = 0L;
            }
            return false;
        }
        if (SystemClock.uptimeMillis() <= seasonalUnlockSurfaceHoldUntil) {
            return true;
        }
        seasonalUnlockSurfaceHoldUntil = 0L;
        return false;
    }

    private void armActiveUnlockEffectProfile(int effect, long startedAt) {
        int token = OverlayPrefs.effectProfileSampleToken(this);
        int sampledToken = OverlayPrefs.get(this).getInt(
                OverlayPrefs.effectProfileSampledTokenKey(effect),
                Integer.MIN_VALUE);
        if (sampledToken == token) {
            clearActiveUnlockEffectProfile();
            return;
        }
        activeEffectProfileEffect = effect;
        activeEffectProfileToken = token;
        activeEffectProfileStartedAt = startedAt;
        activeEffectProfileSyncMs = 0L;
        activeEffectProfileBeginMs = 0L;
        activeEffectProfileBefore = RuntimeMemoryStats.capture();
    }

    private void scheduleActiveUnlockEffectProfileSample() {
        if (activeEffectProfileBefore == null || activeEffectProfileEffect < 0) {
            return;
        }
        handler.removeCallbacks(activeEffectProfileSampleRunnable);
        handler.postDelayed(activeEffectProfileSampleRunnable,
                ACTIVE_EFFECT_PROFILE_SAMPLE_DELAY_MS);
    }

    private void sampleActiveUnlockEffectProfile() {
        if (activeEffectProfileBefore == null || activeEffectProfileEffect < 0) {
            return;
        }
        RuntimeMemoryStats after = RuntimeMemoryStats.capture();
        long now = SystemClock.uptimeMillis();
        String name = unlockEffectRenderer == null
                ? OverlayPrefs.effectLabel(activeEffectProfileEffect)
                : unlockEffectRenderer.effectName();
        int deltaPssKb = after.totalPssKb - activeEffectProfileBefore.totalPssKb;
        String summary = name
                + "\nSampled on unlock effect run"
                + "\nTouch sync " + activeEffectProfileSyncMs
                + " ms | Begin " + activeEffectProfileBeginMs
                + " ms | Sample +" + (now - activeEffectProfileStartedAt) + " ms"
                + "\nPSS " + RuntimeMemoryStats.formatMb(after.totalPssKb)
                + " (delta " + signedMb(deltaPssKb) + ")"
                + "\nJava " + RuntimeMemoryStats.formatMb(after.javaHeapKb)
                + " | Native " + RuntimeMemoryStats.formatMb(after.nativeHeapKb)
                + " | Graphics " + RuntimeMemoryStats.formatMb(after.graphicsKb)
                + "\nNative alloc " + RuntimeMemoryStats.formatMb(after.nativeAllocatedKb)
                + " | Runtime Java " + RuntimeMemoryStats.formatMb(after.javaUsedKb);
        OverlayPrefs.get(this).edit()
                .putString(OverlayPrefs.EFFECT_PROFILE_LAST_SUMMARY, summary)
                .putBoolean(OverlayPrefs.EFFECT_PROFILE_RUNNING, false)
                .putInt(OverlayPrefs.effectProfileSampledTokenKey(activeEffectProfileEffect),
                        activeEffectProfileToken)
                .apply();
        Log.i(TAG, "active effect profile sampled effect=" + activeEffectProfileEffect
                + " name=" + name
                + " pssKb=" + after.totalPssKb
                + " deltaPssKb=" + deltaPssKb
                + " syncMs=" + activeEffectProfileSyncMs
                + " beginMs=" + activeEffectProfileBeginMs);
        clearActiveUnlockEffectProfile();
    }

    private void clearActiveUnlockEffectProfile() {
        handler.removeCallbacks(activeEffectProfileSampleRunnable);
        activeEffectProfileBefore = null;
        activeEffectProfileEffect = -1;
        activeEffectProfileToken = -1;
        activeEffectProfileStartedAt = 0L;
        activeEffectProfileSyncMs = 0L;
        activeEffectProfileBeginMs = 0L;
    }

    private static String signedMb(long kb) {
        return (kb >= 0L ? "+" : "-") + RuntimeMemoryStats.formatMb(Math.abs(kb));
    }

    private boolean beginUnlockEffectGesture(float screenX, float screenY) {
        long startedAt = SystemClock.uptimeMillis();
        int effect = OverlayPrefs.unlockEffect(this);
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
        armActiveUnlockEffectProfile(effect, startedAt);
        handler.removeCallbacks(pinEntryRunnable);
        handler.removeCallbacks(pinEntrySwipeRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        handler.removeCallbacks(unlockEffectIdleHideRunnable);
        stopDebugLensLoop();
        unlockEffectGestureActive = true;
        unlockAffordancePending = false;
        unlockEffectAnchorX = screenX;
        unlockEffectAnchorY = screenY;
        syncUnlockEffectOverlay(true);
        long syncedAt = SystemClock.uptimeMillis();
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.beginGesture(unlockEffectAnchorX, unlockEffectAnchorY);
        }
        activeEffectProfileSyncMs = syncedAt - startedAt;
        activeEffectProfileBeginMs = SystemClock.uptimeMillis() - syncedAt;
        scheduleActiveUnlockEffectProfileSample();
        Log.i(TAG, "unlock effect gesture begin touch="
                + Math.round(screenX) + "," + Math.round(screenY)
                + " anchor=" + Math.round(unlockEffectAnchorX)
                + "," + Math.round(unlockEffectAnchorY)
                + " type=" + effect
                + " syncMs=" + activeEffectProfileSyncMs
                + " beginMs=" + activeEffectProfileBeginMs);
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

    private boolean beginSeasonalUnlockPartnerGesture(float screenX, float screenY) {
        if (!isSeasonalUnlockPartnerModeEnabled() || !isUnlockEffectGestureReady()) {
            Log.i(TAG, "seasonal unlock partner gesture ignored ready=false"
                    + " mode=" + isSeasonalUnlockPartnerModeEnabled()
                    + " charging=" + charging
                    + " locked=" + isLockscreenLocked(false));
            return false;
        }
        if (pinEntryPending || pinEntryRequested || pinEntrySurfaceVisible
                || notificationShadeVisible || isCallSurfaceActive()) {
            Log.i(TAG, "seasonal unlock partner blocked by content surface");
            evaluateVisibility("seasonal_partner_blocked_surface");
            return false;
        }
        ensureSeasonalUnlockPartnerLoaded();
        syncSeasonalUnlockPartnerOverlay();
        if (seasonalUnlockPartnerRenderer == null) {
            return false;
        }
        handler.removeCallbacks(pinEntryRunnable);
        handler.removeCallbacks(pinEntrySwipeRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        seasonalUnlockPartnerGestureActive = true;
        seasonalUnlockPartnerRenderer.beginGesture(screenX, screenY);
        Log.i(TAG, "seasonal unlock partner gesture begin touch="
                + Math.round(screenX) + "," + Math.round(screenY));
        return true;
    }

    private void updateSeasonalUnlockPartnerGesture(float screenX, float screenY) {
        if (seasonalUnlockPartnerRenderer != null) {
            seasonalUnlockPartnerRenderer.updateGesture(screenX, screenY);
        }
    }

    private void finishSeasonalUnlockPartnerGesture(float screenX, float screenY, float distance) {
        boolean unlockTriggered = distance >= dp(UNLOCK_TRIGGER_DISTANCE_DP);
        if (seasonalUnlockPartnerRenderer != null) {
            seasonalUnlockPartnerRenderer.updateGesture(screenX, screenY);
            seasonalUnlockPartnerRenderer.finishGesture(unlockTriggered);
        }
        seasonalUnlockPartnerGestureActive = false;
        if (unlockTriggered) {
            seasonalUnlockSurfaceHoldUntil =
                    SystemClock.uptimeMillis() + SEASONAL_UNLOCK_SURFACE_HOLD_MS;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    evaluateVisibility("seasonal_unlock_hold_expired");
                }
            }, SEASONAL_UNLOCK_SURFACE_HOLD_MS + 40L);
            schedulePinEntry(PIN_ENTRY_DELAY_SEASONAL_UNLOCK_MS, "seasonal_unlock_partner");
        } else {
            seasonalUnlockSurfaceHoldUntil = 0L;
        }
        Log.i(TAG, "seasonal unlock partner gesture end touch="
                + Math.round(screenX) + "," + Math.round(screenY)
                + " distance=" + Math.round(distance)
                + " threshold=" + dp(UNLOCK_TRIGGER_DISTANCE_DP)
                + " unlockTriggered=" + unlockTriggered);
    }

    private void cancelSeasonalUnlockPartnerGesture() {
        if (seasonalUnlockPartnerRenderer != null) {
            seasonalUnlockPartnerRenderer.cancelGesture();
        }
        seasonalUnlockPartnerGestureActive = false;
        seasonalUnlockSurfaceHoldUntil = 0L;
        Log.i(TAG, "seasonal unlock partner gesture cancelled");
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
        unlockEffectGestureActive = false;
        if (unlockTriggered) {
            schedulePinEntry();
        } else if (shouldParkUnlockEffectOverlayWhenIdle()) {
            scheduleUnlockEffectIdleHide();
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
        unlockEffectGestureActive = false;
        if (shouldParkUnlockEffectOverlayWhenIdle()) {
            scheduleUnlockEffectIdleHide();
        }
        Log.i(TAG, "unlock effect gesture cancelled");
    }

    private void schedulePinEntry() {
        schedulePinEntry(pinEntryDelayMs(), OverlayPrefs.effectLabel(OverlayPrefs.unlockEffect(this)));
    }

    private void schedulePinEntry(long delayMs, String source) {
        startPinEntryTrace(delayMs);
        pinEntryPending = true;
        handler.removeCallbacks(pinEntryRunnable);
        handler.postDelayed(pinEntryRunnable, delayMs);
        Log.i(TAG, "pin entry scheduled delayMs=" + delayMs
                + " source=" + source
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
        pinEntryTraceOpenAt = SystemClock.uptimeMillis();
        Log.i(TAG, "pin entry trace open"
                + " effect=" + pinEntryTraceEffect
                + " sinceReleaseMs=" + sincePinEntryRelease(pinEntryTraceOpenAt));
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
        Log.i(TAG, "pin entry swipe queued delayMs=" + PIN_ENTRY_SWIPE_START_DELAY_MS
                + " sinceReleaseMs=" + sincePinEntryRelease(SystemClock.uptimeMillis()));
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
        long delayMs = unlockEffectCleanupDelayMs();
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        handler.postDelayed(pinEntryEffectCleanupRunnable, delayMs);
        Log.i(TAG, "unlock effect cleanup scheduled delayMs="
                + delayMs
                + " effect=" + OverlayPrefs.unlockEffect(this));
    }

    private void cleanupUnlockEffectAfterPinDelay() {
        if (shouldParkUnlockEffectOverlayWhenIdle()) {
            parkUnlockEffectOverlayForIdle();
            Log.i(TAG, "unlock effect cleanup parked lockbg type="
                    + unlockEffectRendererType);
            return;
        }
        removeUnlockEffectOverlay();
    }

    private long unlockEffectCleanupDelayMs() {
        int effect = OverlayPrefs.unlockEffect(this);
        if (effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC) {
            return PIN_ENTRY_EFFECT_CLEANUP_DELAY_LOCKBG_MS;
        }
        if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET) {
            return PIN_ENTRY_EFFECT_CLEANUP_DELAY_COLOUR_DROPLET_MS;
        }
        if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
            return PIN_ENTRY_EFFECT_CLEANUP_DELAY_SPARKLING_BUBBLES_MS;
        }
        return PIN_ENTRY_EFFECT_CLEANUP_DELAY_DEFAULT_MS;
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
                long now = SystemClock.uptimeMillis();
                Log.i(TAG, "pin entry swipe completed"
                        + " sinceReleaseMs=" + sincePinEntryRelease(now)
                        + " sinceDispatchMs=" + sincePinEntryDispatch(now));
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                long now = SystemClock.uptimeMillis();
                Log.w(TAG, "pin entry swipe cancelled"
                        + " sinceReleaseMs=" + sincePinEntryRelease(now)
                        + " sinceDispatchMs=" + sincePinEntryDispatch(now));
            }
        }, handler);
        pinEntryTraceDispatchAt = SystemClock.uptimeMillis();
        Log.i(TAG, "pin entry swipe dispatched accepted=" + accepted
                + " sinceReleaseMs=" + sincePinEntryRelease(pinEntryTraceDispatchAt)
                + " sinceOpenMs=" + sincePinEntryOpen(pinEntryTraceDispatchAt));
        return accepted;
    }

    private void startPinEntryTrace(long scheduledDelayMs) {
        long now = SystemClock.uptimeMillis();
        pinEntryTraceActive = true;
        pinEntryTraceGestureEndAt = now;
        pinEntryTraceOpenAt = 0L;
        pinEntryTraceDispatchAt = 0L;
        pinEntryTraceEffect = OverlayPrefs.unlockEffect(this);
        Log.i(TAG, "pin entry trace start"
                + " effect=" + pinEntryTraceEffect
                + " scheduledDelayMs=" + scheduledDelayMs);
    }

    private void logPinEntrySurfaceTrace(
            long now,
            AccessibilityEvent event,
            boolean keyboardOnly) {
        if (!pinEntryTraceActive) {
            return;
        }
        Log.i(TAG, "pin entry trace surface"
                + " effect=" + pinEntryTraceEffect
                + " sinceReleaseMs=" + sincePinEntryRelease(now)
                + " sinceOpenMs=" + sincePinEntryOpen(now)
                + " sinceDispatchMs=" + sincePinEntryDispatch(now)
                + " event=" + eventTypeName(event)
                + " keyboardOnly=" + keyboardOnly
                + " pkg=" + (event == null ? "null" : event.getPackageName()));
        pinEntryTraceActive = false;
    }

    private long sincePinEntryRelease(long now) {
        return pinEntryTraceGestureEndAt <= 0L ? -1L : now - pinEntryTraceGestureEndAt;
    }

    private long sincePinEntryOpen(long now) {
        return pinEntryTraceOpenAt <= 0L ? -1L : now - pinEntryTraceOpenAt;
    }

    private long sincePinEntryDispatch(long now) {
        return pinEntryTraceDispatchAt <= 0L ? -1L : now - pinEntryTraceDispatchAt;
    }

    private void clearPinEntryTrace() {
        pinEntryTraceActive = false;
        pinEntryTraceGestureEndAt = 0L;
        pinEntryTraceOpenAt = 0L;
        pinEntryTraceDispatchAt = 0L;
        pinEntryTraceEffect = -1;
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
        if (packageName == null) {
            return false;
        }
        String value = packageName.toString().toLowerCase();
        return callPackages.contains(value)
                || value.contains(".incall")
                || value.contains("incallui")
                || value.contains(".dialer")
                || value.contains("dialer")
                || value.contains(".telecom")
                || value.contains("telephonyui")
                || value.equals("com.android.phone")
                || value.equals("com.sec.phone");
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
        if (!charging) {
            if (wasCharging) {
                suppressUnlockFxAfterDoodleDisconnect = true;
                destroySeasonalUnlockPartnerOverlay();
                removeTouchDebugOverlay();
            }
            if (overlayView != null) {
                destroyDoodleOverlay();
                Log.i(TAG, "doodle view unloaded; not charging");
            }
            return;
        }
        if (!wasCharging) {
            suppressUnlockFxAfterDoodleDisconnect = false;
            ensureDoodleLoaded();
        }
        if (isChargingDoodleModeEnabled()) {
            unloadUnlockEffectsForDoodleMode("charging");
            if (isSeasonalUnlockPartnerModeEnabled()) {
                ensureSeasonalUnlockPartnerLoaded();
            }
        }
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
