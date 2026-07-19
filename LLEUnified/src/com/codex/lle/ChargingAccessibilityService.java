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
import java.util.ArrayList;
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
    private static final String ACTION_DEBUG_CAPTURE_GEOMETRIC_HINT =
            "com.codex.lle.DEBUG_CAPTURE_GEOMETRIC_HINT";
    private static final String ACTION_DEBUG_CAPTURE_ABSTRACT_TILES =
            "com.codex.lle.DEBUG_CAPTURE_ABSTRACT_TILES";
    static final String ACTION_EFFECT_BACKGROUND_REFRESH =
            "com.codex.lle.EFFECT_BACKGROUND_REFRESH";
    private static final int UNLOCK_TRIGGER_DISTANCE_DP = 120;
    private static final long PIN_ENTRY_DELAY_LENS_FLARE_MS = 400L;
    private static final long PIN_ENTRY_DELAY_POPPING_COLOURS_MS = 300L;
    private static final long PIN_ENTRY_DELAY_WATERCOLOUR_MS = 250L;
    // Samsung exposes a 400 ms unlock delay. The shared dispatch stage below adds 60 ms.
    private static final long PIN_ENTRY_DELAY_COLOUR_DROPLET_MS = 340L;
    // Samsung exposes a 400 ms unlock delay. The shared dispatch stage below adds 60 ms.
    private static final long PIN_ENTRY_DELAY_SPARKLING_BUBBLES_MS = 340L;
    private static final long PIN_ENTRY_DELAY_SEASONAL_UNLOCK_MS = 300L;
    private static final long SEASONAL_UNLOCK_SURFACE_HOLD_MS = 900L;
    private static final float WARM_PARK_ALPHA = 0.01f;
    private static final float CANVAS_WARM_PARK_ALPHA = 1f;
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
    private static final long LOCKSCREEN_SESSION_FAST_WINDOW_MS = 1200L;
    private static final long LOCKSCREEN_SESSION_FAST_POLL_MS = 16L;
    private static final long LOCKSCREEN_SESSION_STABLE_POLL_MS = 50L;
    private static final long LOCKSCREEN_SESSION_CONTENT_POLL_MS = 80L;
    private static final long LOCKSCREEN_SESSION_STABLE_CONTENT_POLL_MS = 200L;
    private static final long WINDOW_CONTENT_EVENT_MIN_INTERVAL_MS = 32L;
    private static final long DISPLAY_CANDIDATE_WAKE_COALESCE_MS = 32L;
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
    private static final long READINESS_GESTURE_TIMEOUT_MS = 650L;
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
    private final Runnable rippleRendererReadinessRunnable = new Runnable() {
        @Override
        public void run() {
            if (unlockEffectRendererType == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                    && unlockEffectRenderer instanceof S3Arm64RippleEffectView) {
                if (!((S3Arm64RippleEffectView) unlockEffectRenderer).isReady()) {
                    fallBackFromFailedRippleRenderer("async_gl_init_or_render");
                    return;
                }
            } else if (unlockEffectRendererType == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                    && unlockEffectRenderer instanceof AbstractTilesArm64EffectView) {
                if (!((AbstractTilesArm64EffectView) unlockEffectRenderer).isReady()) {
                    fallBackFromFailedAbstractTilesRenderer("async_gl_init_or_render");
                    return;
                }
            } else if (unlockEffectRendererType == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                    && unlockEffectRenderer instanceof GeometricMosaicArm64EffectView) {
                if (!((GeometricMosaicArm64EffectView) unlockEffectRenderer).isReady()) {
                    fallBackFromFailedGeometricMosaicRenderer("async_gl_init_or_render");
                    return;
                }
            } else {
                return;
            }
            handler.postDelayed(this, 1000L);
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
    private final Runnable unlockEffectReadinessChangedRunnable = new Runnable() {
        @Override
        public void run() {
            refreshUnlockEffectReadiness("listener");
        }
    };
    private final Runnable bufferedReadinessGestureTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            handleBufferedReadinessGestureTimeout();
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
    private final Runnable importedEffectBackgroundReloadRunnable = new Runnable() {
        @Override
        public void run() {
            String reason = pendingImportedEffectBackgroundReloadReason;
            pendingImportedEffectBackgroundReloadReason = null;
            reloadSelectedEffectBackgroundSource(reason == null
                    ? "prefs:imported_background" : reason);
        }
    };
    private final Runnable forcedEffectBackgroundTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            completeForcedEffectBackgroundRefresh("timeout");
        }
    };
    private final Runnable timeWindowRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            evaluateVisibility("time_window_boundary");
            scheduleTimeWindowRefresh();
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
    private final Runnable activeDisplayRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            boolean switched = refreshActiveDisplayTarget("display_callback");
            if (!switched) {
                markNativeRendererStaleForDisplaySize();
            }
            scheduleCandidateWakeRefreshes("display_changed");
        }
    };
    private final Runnable unlockEffectBenchmarkStepRunnable = new Runnable() {
        @Override
        public void run() {
            runUnlockEffectBenchmarkStep();
        }
    };
    private WindowManager windowManager;
    private String pendingImportedEffectBackgroundReloadReason;
    private Context activeDisplayContext;
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
    private final ArrayList<TouchDebugView> additionalTouchDebugViews =
            new ArrayList<TouchDebugView>();
    private final ArrayList<WindowManager.LayoutParams> additionalTouchDebugParams =
            new ArrayList<WindowManager.LayoutParams>();
    private final ArrayList<Rect> resolvedTouchBoxesCache = new ArrayList<Rect>();
    private boolean touchDebugTouchable;
    private boolean resolvedTouchBoxesDirty = true;
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
    private boolean bufferedReadinessGestureActive;
    private boolean readinessFallbackGestureActive;
    private boolean bufferedReadinessHasMove;
    private boolean bufferedReadinessHasTerminal;
    private boolean bufferedReadinessTerminalCancel;
    private boolean seasonalUnlockPartnerGestureActive;
    private boolean suppressUnlockFxAfterDoodleDisconnect;
    private boolean suppressUnlockEffectPreferenceCallback;
    private boolean lockscreenSessionPolling;
    private long lockscreenSessionPollingStartedAt;
    private long nextContentAwarePollAt;
    private long lastWindowContentVisibilityAt;
    private long lastDisplayCandidateWakeRefreshAt;
    private long lastActiveDisplayResolveAt;
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
    private long unlockEffectOverlayAddRetryAt;
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
    private int unlockEffectReadinessState = UnlockEffectReadiness.STATE_DETACHED;
    private int bufferedReadinessEffect = -1;
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
    private boolean touchBoxScreenshotCallbackPending;
    private int touchBoxScreenshotInFlightRequestId;
    private int[] unlockEffectBenchmarkEffects;
    private int unlockEffectBenchmarkIndex;
    private int unlockEffectBenchmarkOriginalEffect;
    private int candidateWakeGeneration;
    private int lastScreenOffPrearmDisplayState = Integer.MIN_VALUE;
    private int unlockEffectRendererDisplayWidth;
    private int unlockEffectRendererDisplayHeight;
    private int activeDisplayId = Display.INVALID_DISPLAY;
    private int activeDisplayWidth;
    private int activeDisplayHeight;
    private int resolvedTouchBoxesWidth = -1;
    private int resolvedTouchBoxesHeight = -1;
    private String activeDisplayProfile = FoldDisplayTarget.PROFILE_SINGLE;
    private String resolvedTouchBoxesProfile = "";
    private String cachedUnlockEffectBackgroundProfile = "";
    private String cachedUnlockEffectBackgroundFilePath = "";
    private String unlockEffectReadinessDetail = "detached";
    private float bufferedReadinessDownX;
    private float bufferedReadinessDownY;
    private float bufferedReadinessMoveX;
    private float bufferedReadinessMoveY;
    private float bufferedReadinessMoveDistance;
    private float bufferedReadinessTerminalX;
    private float bufferedReadinessTerminalY;
    private float bufferedReadinessTerminalDistance;
    private StringBuilder unlockEffectBenchmarkCsv;

    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    scheduleActiveDisplayRefresh();
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    scheduleActiveDisplayRefresh();
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    scheduleActiveDisplayRefresh();
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
                handler.removeCallbacks(timeWindowRefreshRunnable);
                interactiveSessionWasUnlocked = false;
                lastInteractive = false;
                lastScreenOffAt = SystemClock.uptimeMillis();
                lastScreenOffPrearmDisplayState = Integer.MIN_VALUE;
                seasonalUnlockSurfaceHoldUntil = 0L;
                suppressUnlockFxAfterDoodleDisconnect = false;
                handler.removeCallbacks(forcedEffectBackgroundTimeoutRunnable);
                handler.removeCallbacks(forcedEffectBackgroundSleepRunnable);
                Log.i(TAG, "screen off broadcast interactive="
                        + (powerManager == null || powerManager.isInteractive())
                        + " locked=" + isLockscreenLocked(false));
                unlockAffordancePending = false;
                unlockAffordanceShownThisWake = false;
                handler.removeCallbacks(unlockEffectIdleHideRunnable);
                lastScreenOnAt = 0L;
                stopLockscreenSessionPolling();
                clearBlockedSurfaceState();
                resetUnlockEffectBackgroundSession(true);
                handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
                cacheUnlockTouchForScreenOff();
                scheduleScreenOffPrearm();
                scheduleEffectBackgroundRefreshAlarm("screen_off");
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                handler.removeCallbacks(timeWindowRefreshRunnable);
                interactiveSessionWasUnlocked = true;
                unlockAffordancePending = false;
                unlockAffordanceShownThisWake = false;
                handler.removeCallbacks(unlockEffectIdleHideRunnable);
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
                scheduleTimeWindowRefresh();
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
            } else if (ACTION_DEBUG_CAPTURE_GEOMETRIC_HINT.equals(intent.getAction())) {
                long phaseMs = Math.max(0L, Math.min(2_400L,
                        intent.getLongExtra("phase_ms", 800L)));
                if (unlockEffectRendererType != OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                        || !(unlockEffectRenderer instanceof DebugFrameCaptureRenderer)) {
                    Log.i(TAG, "debug geometric hint capture ignored phaseMs=" + phaseMs
                            + " rendererType=" + unlockEffectRendererType
                            + " renderer=" + (unlockEffectRenderer == null ? "null"
                                    : unlockEffectRenderer.getClass().getSimpleName()));
                } else {
                    Log.i(TAG, "debug geometric hint capture requested phaseMs=" + phaseMs
                            + " renderer=" + unlockEffectRenderer.getClass().getSimpleName());
                    ((DebugFrameCaptureRenderer) unlockEffectRenderer)
                            .captureDebugAffordanceFrame(phaseMs);
                }
            } else if (ACTION_DEBUG_CAPTURE_ABSTRACT_TILES.equals(intent.getAction())) {
                long phaseMs = Math.max(0L, Math.min(2_400L,
                        intent.getLongExtra("phase_ms", 800L)));
                String sequence = intent.getStringExtra("sequence");
                if (sequence == null) {
                    sequence = "hint";
                }
                sequence = sequence.trim().toLowerCase(java.util.Locale.US);
                if (!"hint".equals(sequence)
                        && !"touch".equals(sequence)
                        && !"unlock".equals(sequence)
                        && !"unlock-series".equals(sequence)) {
                    Log.i(TAG, "debug Abstract Tiles capture ignored; unknown sequence="
                            + sequence);
                } else if (unlockEffectRendererType
                        != OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                        || !(unlockEffectRenderer
                                instanceof DebugAbstractTilesCaptureRenderer)) {
                    Log.i(TAG, "debug Abstract Tiles capture ignored sequence=" + sequence
                            + " phaseMs=" + phaseMs
                            + " rendererType=" + unlockEffectRendererType
                            + " renderer=" + (unlockEffectRenderer == null ? "null"
                                    : unlockEffectRenderer.getClass().getSimpleName()));
                } else {
                    Log.i(TAG, "debug Abstract Tiles capture requested sequence=" + sequence
                            + " phaseMs=" + phaseMs
                            + " renderer="
                            + unlockEffectRenderer.getClass().getSimpleName());
                    ((DebugAbstractTilesCaptureRenderer) unlockEffectRenderer)
                            .captureDebugAbstractTilesFrame(sequence, phaseMs);
                }
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "connected abi=" + Lle64Abi.verify());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        refreshActiveDisplayTarget("connected");
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
        OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this, activeDisplayProfile);
        applyPerfDefaultsOnce();
        ensureInternalTouchAreaEnabled();
        prefs.registerOnSharedPreferenceChangeListener(this);
        loadHomePackages();
        loadCallPackages();
        configurePassiveService();
        refreshChargingState();
        ensureDoodleLoaded();
        scheduleTimeWindowRefresh();
        if (!isChargingDoodleModeEnabled()) {
            preloadAndAttachSelectedUnlockEffectParked("connected");
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
        if (event == null) {
            return;
        }
        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            long now = SystemClock.uptimeMillis();
            if (now - lastWindowContentVisibilityAt < WINDOW_CONTENT_EVENT_MIN_INTERVAL_MS) {
                return;
            }
            lastWindowContentVisibilityAt = now;
        }
        refreshActiveDisplayTarget("accessibility_event");
        if (event.getPackageName() != null) {
            lastWindowPackage = event.getPackageName().toString();
        }
        logSystemUiEvent(event);
        boolean interactive = powerManager == null || powerManager.isInteractive();
        noteExternalLockscreenSurface(event, interactive);
        if (isCallPackage(event.getPackageName())) {
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
        String eventReason = "event:" + eventTypeName(event);
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Pin entry, notification shade and call surfaces were handled above. Generic
            // content churn only needs a cheap state pass; the session poll performs the
            // bounded node scan separately.
            evaluateVisibility(eventReason, false);
            return;
        }
        evaluateVisibility(eventReason);
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
        if (suppressUnlockEffectPreferenceCallback
                && OverlayPrefs.UNLOCK_EFFECT.equals(key)) {
            // Internal profiler/fallback transitions already own the renderer lifecycle.
            // Re-entering the normal preference path here can attach a SurfaceView and
            // remove it again before ViewRoot finishes dispatchAttachedToWindow().
            return;
        }
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
        if (OverlayPrefs.FOLD_MODE.equals(key)) {
            refreshActiveDisplayTarget("prefs_fold_mode");
            OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this, activeDisplayProfile);
        }
        if (OverlayPrefs.EFFECT_BACKGROUND_REFRESH_TOKEN.equals(key)
                || OverlayPrefs.POPPING_COLOR_REFRESH_TOKEN.equals(key)) {
            invalidateUnlockEffectBackgroundSource();
        }
        if (OverlayPrefs.isImportedEffectBackgroundPreferenceKey(key)) {
            int selectedEffect = OverlayPrefs.unlockEffect(this);
            if (OverlayPrefs.isImportedEffectBackgroundPreferenceKeyFor(
                    key, selectedEffect, activeDisplayProfile)) {
                // One all-effects import changes several preference keys in one transaction.
                // Filter to the active effect/profile and coalesce its path+mode callbacks.
                pendingImportedEffectBackgroundReloadReason = "prefs:" + key;
                handler.removeCallbacks(importedEffectBackgroundReloadRunnable);
                handler.postDelayed(importedEffectBackgroundReloadRunnable, 32L);
            }
            // Imported metadata and modes are renderer-source bookkeeping. The selected
            // path/mode reload above is the only visible work this batch needs.
            return;
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
                || OverlayPrefs.SEASONAL_UNLOCK_PARTNER.equals(key)) {
            if (isChargingDoodleModeEnabled()) {
                unloadUnlockEffects("doodle:prefs:" + key);
            } else {
                preloadUnlockEffectRenderer();
            }
        }
        if (OverlayPrefs.isFoldPanelRoutingKey(key)) {
            if (isChargingDoodleModeEnabled()) {
                ensureDoodleLoaded();
            } else {
                destroySeasonalUnlockPartnerOverlay();
                destroyDoodleOverlay();
            }
        }
        if (OverlayPrefs.UNLOCK_EFFECT_ENABLED.equals(key)
                || OverlayPrefs.isFoldPanelRoutingKey(key)) {
            if (!isUnlockEffectEnabledForActivePanel() || isChargingDoodleModeEnabled()) {
                unloadUnlockEffects("routing:prefs:" + key);
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
        if (OverlayPrefs.isTouchBoxPreferenceKey(key)
                && touchDebugView != null) {
            invalidateResolvedTouchBoxes();
            syncTouchDebugOverlay();
        } else if (OverlayPrefs.isTouchBoxPreferenceKey(key)) {
            invalidateResolvedTouchBoxes();
        }
        if (OverlayPrefs.TOUCH_BOX_CAPTURE_REQUEST_ID.equals(key)) {
            touchBoxScreenshotScheduled = false;
            handler.removeCallbacks(touchBoxScreenshotDelayRunnable);
            handler.removeCallbacks(touchBoxScreenshotCaptureRunnable);
            if (!touchBoxScreenshotCallbackPending) {
                touchBoxScreenshotInFlight = false;
                touchBoxScreenshotInFlightRequestId = 0;
            }
        }
        if (OverlayPrefs.UNLOCK_EFFECT.equals(key)) {
            cancelBufferedReadinessGesture("effect_changed", false);
            if (unlockEffectRenderer != null) {
                destroyUnlockEffectOverlay();
            }
            preloadAndAttachSelectedUnlockEffectParked("prefs:unlock_effect");
        }
        if (OverlayPrefs.ABSTRACT_TILES_LINE_ENABLED.equals(key)
                && unlockEffectRenderer != null
                && OverlayPrefs.unlockEffect(this) == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES) {
            // Line ON/OFF changes the GLES resource graph, so rebuild the one renderer
            // instead of leaving a half-configured mask/program set in the current context.
            destroyUnlockEffectOverlay();
        }
        scheduleTimeWindowRefresh();
        evaluateVisibility("prefs:" + key);
    }

    private void scheduleTimeWindowRefresh() {
        handler.removeCallbacks(timeWindowRefreshRunnable);
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (!interactive
                || !isLockscreenLocked(false)
                || !OverlayPrefs.hasRuntimeSurfaceTimeWindow(this)) {
            return;
        }
        long now = System.currentTimeMillis();
        long delay = 60_000L - (now % 60_000L) + 50L;
        handler.postDelayed(timeWindowRefreshRunnable, delay);
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
        benchmarkFilter.addAction(ACTION_DEBUG_CAPTURE_GEOMETRIC_HINT);
        benchmarkFilter.addAction(ACTION_DEBUG_CAPTURE_ABSTRACT_TILES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(benchmarkReceiver, benchmarkFilter,
                    android.Manifest.permission.DUMP, null, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(benchmarkReceiver, benchmarkFilter,
                    android.Manifest.permission.DUMP, null);
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

    private void scheduleActiveDisplayRefresh() {
        handler.removeCallbacks(activeDisplayRefreshRunnable);
        handler.postDelayed(activeDisplayRefreshRunnable, 64L);
    }

    private boolean refreshActiveDisplayTarget(String reason) {
        if (displayManager == null) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        if ("accessibility_event".equals(reason)
                && now - lastActiveDisplayResolveAt < 200L) {
            return false;
        }
        lastActiveDisplayResolveAt = now;
        FoldDisplayTarget target = FoldDisplayTarget.resolve(
                this, displayManager, activeDisplayId);
        if (target.display == null) {
            return false;
        }
        boolean changed = target.displayId != activeDisplayId
                || !target.cacheProfile.equals(activeDisplayProfile);
        if (!changed && windowManager != null && activeDisplayContext != null) {
            activeDisplayWidth = target.width;
            activeDisplayHeight = target.height;
            return false;
        }

        int previousDisplayId = activeDisplayId;
        String previousProfile = activeDisplayProfile;
        if (previousDisplayId != Display.INVALID_DISPLAY) {
            stopDebugLensLoop();
            cancelSeasonalUnlockPartnerGesture();
            destroyUnlockEffectOverlay();
            destroySeasonalUnlockPartnerOverlay();
            destroyDoodleOverlay();
            removeTouchDebugOverlay();
            clearCachedUnlockEffectBackgroundBitmap();
        }

        Context displayContext = this;
        try {
            if (target.multiPanel && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                displayContext = createWindowContext(
                        target.display,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        null);
            } else if (target.displayId != Display.DEFAULT_DISPLAY) {
                displayContext = createDisplayContext(target.display);
            }
        } catch (Throwable t) {
            Log.w(TAG, "display context creation failed id=" + target.displayId, t);
            displayContext = this;
        }
        WindowManager targetWindowManager =
                (WindowManager) displayContext.getSystemService(WINDOW_SERVICE);
        if (targetWindowManager == null) {
            Log.w(TAG, "display window manager unavailable id=" + target.displayId);
            return false;
        }

        activeDisplayContext = displayContext;
        windowManager = targetWindowManager;
        activeDisplayId = target.displayId;
        activeDisplayWidth = target.width;
        activeDisplayHeight = target.height;
        activeDisplayProfile = target.cacheProfile;
        invalidateResolvedTouchBoxes();
        unlockEffectBackgroundGeneration++;
        colorScreenshotInFlight = false;
        colorScreenshotAttemptedThisSession = false;
        unlockEffectBackgroundCaptureSucceededThisSession = false;
        unlockEffectBackgroundCapturedAt = 0L;
        unlockEffectBackgroundEffect = -1;
        unlockEffectBackgroundNextAttemptAt = 0L;
        unlockEffectBackgroundCaptureAttempts = 0;
        skipCachedEffectBackgroundLoad = false;
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);

        Log.i(TAG, "active display target reason=" + reason
                + " id=" + previousDisplayId + "->" + activeDisplayId
                + " profile=" + previousProfile + "->" + activeDisplayProfile
                + " physical=" + activeDisplayWidth + "x" + activeDisplayHeight);

        if (previousDisplayId != Display.INVALID_DISPLAY) {
            ensureDoodleLoaded();
            if (!isChargingDoodleModeEnabled()) {
                preloadUnlockEffectRenderer();
            }
            scheduleEffectBackgroundRefreshAlarm("display_target");
            evaluateVisibility("display_target:" + reason, false);
        }
        return true;
    }

    private Context rendererContext() {
        return activeDisplayContext == null ? this : activeDisplayContext;
    }

    private DisplayMetrics activeDisplayMetrics() {
        return rendererContext().getResources().getDisplayMetrics();
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
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS
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
        // Publish diagnostics while the last benchmark renderer is still the selected
        // effect, then change the selection without re-entering the preference lifecycle.
        // The explicit destroy/evaluate pair below performs exactly one transition back.
        OverlayPrefs.get(this).edit()
                .putBoolean(OverlayPrefs.EFFECT_PROFILE_RUNNING, false)
                .putString(OverlayPrefs.EFFECT_PROFILE_DIAGNOSTIC_SUMMARY, status)
                .putString(OverlayPrefs.EFFECT_PROFILE_LAST_CSV, file.getAbsolutePath())
                .apply();
        setUnlockEffectPreferenceInternally(unlockEffectBenchmarkOriginalEffect);
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

        setUnlockEffectPreferenceInternally(effect);
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

    private void setUnlockEffectPreferenceInternally(int effect) {
        boolean wasSuppressed = suppressUnlockEffectPreferenceCallback;
        suppressUnlockEffectPreferenceCallback = true;
        try {
            OverlayPrefs.get(this).edit()
                    .putInt(OverlayPrefs.UNLOCK_EFFECT, effect)
                    .apply();
        } finally {
            suppressUnlockEffectPreferenceCallback = wasSuppressed;
        }
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
                || effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                || effect == OverlayPrefs.EFFECT_TABS_BLIND;
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
        if ("display_changed".equals(reason)) {
            long now = SystemClock.uptimeMillis();
            if (now - lastDisplayCandidateWakeRefreshAt
                    < DISPLAY_CANDIDATE_WAKE_COALESCE_MS) {
                return;
            }
            lastDisplayCandidateWakeRefreshAt = now;
        }
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
        if (locked) {
            scheduleTimeWindowRefresh();
        }
        if (!locked || !isUnlockEffectAllowedNowForActivePanel()) {
            return;
        }
        holdHotWakeLock("interactive_wake:" + reason);
        long now = SystemClock.uptimeMillis();
        boolean cached = unlockTouchCachedWhileScreenOff;
        lastScreenOnAt = now;
        unlockAffordancePending = true;
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
        lockscreenSessionPollingStartedAt = SystemClock.uptimeMillis();
        nextContentAwarePollAt = 0L;
        handler.removeCallbacks(lockscreenSessionPollRunnable);
        handler.post(lockscreenSessionPollRunnable);
    }

    private void stopLockscreenSessionPolling() {
        lockscreenSessionPolling = false;
        lockscreenSessionPollingStartedAt = 0L;
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
        long sessionAgeMs = lockscreenSessionPollingStartedAt <= 0L
                ? 0L
                : now - lockscreenSessionPollingStartedAt;
        boolean fastWindow = sessionAgeMs < LOCKSCREEN_SESSION_FAST_WINDOW_MS;
        long contentPollMs = fastWindow
                ? LOCKSCREEN_SESSION_CONTENT_POLL_MS
                : LOCKSCREEN_SESSION_STABLE_CONTENT_POLL_MS;
        boolean contentAware = now >= nextContentAwarePollAt;
        if (contentAware) {
            nextContentAwarePollAt = now + contentPollMs;
        }
        evaluateVisibility(contentAware ? "lockscreen_poll_content" : "lockscreen_poll_fast",
                contentAware);
        handler.postDelayed(lockscreenSessionPollRunnable, fastWindow
                ? LOCKSCREEN_SESSION_FAST_POLL_MS
                : LOCKSCREEN_SESSION_STABLE_POLL_MS);
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
        if (unlockEffectRenderer != null && unlockEffectOverlayAttached) {
            unlockEffectRenderer.resetEffect();
        }
        if (unlockTouchCachedWhileScreenOff) {
            syncUnlockEffectOverlay(false);
            if (unlockEffectRenderer != null) {
                unlockEffectRenderer.warmUp();
            }
            parkUnlockEffectOverlayForScreenOff();
            syncTouchDebugOverlay(true, false);
            unlockTouchCachedWhileScreenOff = unlockEffectOverlayAttached;
            lastScreenOffPrearmDisplayState = currentDisplayState();
            Log.i(TAG, "unlock effect and touch box cached for screen off"
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt)
                    + " overlayAttached=" + unlockEffectOverlayAttached
                    + " touchBox=" + (touchDebugView != null)
                    + " displayState=" + displayStateName(currentDisplayState()));
        } else {
            int effect = OverlayPrefs.unlockEffect(this);
            if (effectUsesCachedScreenshotBackground(effect)
                    && !hasUnlockEffectBackgroundSource(effect)) {
                // A fresh install has no per-package screenshot cache. Keeping an empty
                // TextureView attached while the screen is off makes canCapture... reject
                // the first lockscreen screenshot forever, leaving native bgReady at 0.
                removeUnlockEffectOverlay(true);
                removeTouchDebugOverlay();
                Log.i(TAG, "screen-off prearm deferred for missing effect background"
                        + " effect=" + effect);
            }
        }
    }

    private void prearmUnlockTouchForScreenOff() {
        long startedAt = SystemClock.uptimeMillis();
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (interactive) {
            // Do not cancel the tail of the screen-off warm sequence at SCREEN_ON. Renderers
            // that are still creating EGL/native resources get bounded emergency warm frames;
            // a ready renderer makes this branch a cheap no-op.
            if (unlockEffectOverlayAttached && !isUnlockEffectFirstFrameReady()) {
                if (unlockEffectRenderer != null) {
                    unlockEffectRenderer.warmUp();
                }
                refreshUnlockEffectReadiness("screen_on_emergency_warm");
            }
            return;
        }
        if (!shouldPrearmUnlockEffectForScreenOff()) {
            return;
        }
        int displayState = currentDisplayState();
        if (displayState == lastScreenOffPrearmDisplayState
                && unlockEffectOverlayAttached
                && touchDebugView != null) {
            return;
        }
        lastScreenOffPrearmDisplayState = displayState;
        holdHotWakeLock("screen_off_prearm");
        syncUnlockEffectOverlay(false);
        scheduleUnlockEffectWarmBurst("screen_off_prearm");
        parkUnlockEffectOverlayForScreenOff();
        syncTouchDebugOverlay(true, false);
        // Effect readiness is independent from the optional touch listener. The touch box
        // remains conditional inside syncTouchDebugOverlay().
        unlockTouchCachedWhileScreenOff = unlockEffectOverlayAttached;
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
        int effect = OverlayPrefs.unlockEffect(this);
        return screenOffOrLocked
                && OverlayPrefs.masterEnabled(this)
                && !showDoodle
                && !isCallSurfaceActive()
                && isUnlockEffectAllowedNowForActivePanel()
                && (!effectUsesCachedScreenshotBackground(effect)
                || hasUnlockEffectBackgroundSource(effect));
    }

    private boolean shouldKeepNativePhysicsOverlayAttachedDuringHide(int effect) {
        // All ten selectable screenshot-backed effects benefit from keeping their attached
        // renderer parked. Mandatory detach paths pass destroyingRenderer=true (capture,
        // calls, explicit destroy) and therefore still bypass this keep-warm policy.
        return effectUsesScreenshotBackground(effect);
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
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR;
    }

    private boolean isRecreatableNativeEffect(int effect) {
        return isSamsungLockBgEffect(effect)
                || effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES;
    }

    private void markNativeRendererStaleForDisplaySize() {
        int effect = unlockEffectRendererType;
        boolean arm64AbstractTiles = EffectAvailability.is64BitProcess()
                && effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES;
        if (!arm64AbstractTiles
                && effect != OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                && effect != OverlayPrefs.EFFECT_WATERCOLOUR
                && effect != OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                && effect != OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                && effect != OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
            return;
        }
        DisplayMetrics metrics = activeDisplayMetrics();
        int width = Math.max(1, metrics.widthPixels);
        int height = Math.max(1, metrics.heightPixels);
        if (unlockEffectRendererDisplayWidth <= 0 || unlockEffectRendererDisplayHeight <= 0) {
            unlockEffectRendererDisplayWidth = width;
            unlockEffectRendererDisplayHeight = height;
            return;
        }
        if (width == unlockEffectRendererDisplayWidth
                && height == unlockEffectRendererDisplayHeight) {
            return;
        }
        unlockEffectRendererNeedsRecreate = true;
        unlockEffectRendererRecreateReason = "display_size:"
                + unlockEffectRendererDisplayWidth + "x" + unlockEffectRendererDisplayHeight
                + "->" + width + "x" + height;
        Log.i(TAG, "native renderer marked stale reason="
                + unlockEffectRendererRecreateReason + " type=" + effect);
    }

    private boolean shouldParkUnlockEffectOverlayWhenIdle() {
        // The patched LockBG renderer has no fullscreen background pass and naturally
        // switches itself to RENDERMODE_WHEN_DIRTY. Keeping its Surface visible while the
        // lockscreen is active avoids first-touch cold starts and matches Samsung SystemUI.
        return false;
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
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = WINDOW_CONTENT_EVENT_MIN_INTERVAL_MS;
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
                parkUnlockEffectOverlayForScreenOff();
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
        boolean unlockEffectAllowedForActivePanel =
                isUnlockEffectAllowedNowForActivePanel();
        if (!unlockEffectAllowedForActivePanel
                && OverlayPrefs.effectBackgroundWakeCaptureActive(this)) {
            completeForcedEffectBackgroundRefresh("panel_effect_disabled");
        }
        int selectedUnlockEffect = OverlayPrefs.unlockEffect(this);
        boolean backgroundBootstrapNeeded = unlockEffectAllowedForActivePanel
                && interactive
                && locked
                && effectUsesCachedScreenshotBackground(selectedUnlockEffect)
                && !hasUnlockEffectBackgroundSource(selectedUnlockEffect);
        boolean hideOverlaysForBackgroundCapture =
                unlockEffectAllowedForActivePanel
                        && (OverlayPrefs.effectBackgroundWakeCaptureActive(this)
                        || colorScreenshotInFlight
                        || backgroundBootstrapNeeded) && interactive && locked;
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
                && unlockEffectAllowedForActivePanel;

        if (!showDoodle
                && shouldRunUnlockEffectBackgroundPreflight(interactive, displayOn, locked,
                aodSurface, hideOverlaysForTouchBoxCapture, hideOverlaysForBackgroundCapture,
                blockedSurfaceActive)) {
            refreshUnlockEffectBackgroundSourceIfNeeded("service_background:" + reason);
            // A capture can start inside this same visibility pass, after showFx was
            // computed. Do not remount the renderer with the stale cache before the
            // asynchronous screenshot callback has validated and applied the new frame.
            if (colorScreenshotInFlight) {
                showFx = false;
                hideOverlaysForBackgroundCapture = true;
            }
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
                /* AOD/SystemUI can alternate as the reported foreground package while the
                 * same lockscreen wake is still active. That transient visibility edge must
                 * not start a second affordance: nativeAffordance intentionally resets the
                 * entire effect scene. Screen-off/wake boundaries own this session flag. */
            }
            boolean parkFxIdle = shouldParkUnlockEffectOverlayWhenIdle();
            if (!unlockAffordancePending && !unlockAffordanceShownThisWake) {
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
                    + " panelEffect=" + isUnlockEffectEnabledForActivePanel()
                    + " panelDoodle="
                    + OverlayPrefs.foldPanelDoodleEnabled(this, activeDisplayProfile)
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
        return reason == null
                || (!reason.startsWith("lockscreen_poll")
                && !reason.startsWith("lockscreen_exit_poll"));
    }

    private void syncDoodleOverlay() {
        if (!isChargingDoodleModeEnabled()) {
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
                || !isDoodleAllowedNowForActivePanel()
                || !charging) {
            return;
        }
        overlayView = new SeasonalDoodleView(rendererContext());
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
        seasonalUnlockPartnerRenderer = new SeasonalUnlockEffectView(rendererContext());
        seasonalUnlockPartnerRenderer.setSeasonMode(OverlayPrefs.seasonMode(this));
        seasonalUnlockPartnerView = seasonalUnlockPartnerRenderer.asView();
        Log.i(TAG, "seasonal unlock partner preloaded");
    }

    private void syncUnlockEffectOverlay() {
        syncUnlockEffectOverlay(true);
    }

    private void syncUnlockEffectOverlay(final boolean visible) {
        long startedAt = SystemClock.uptimeMillis();
        if (!unlockEffectOverlayAttached
                && unlockEffectView != null
                && startedAt < unlockEffectOverlayAddRetryAt) {
            return;
        }
        preloadUnlockEffectRenderer();
        refreshUnlockEffectReadiness("sync");
        final boolean exposeReadySurface = visible && isUnlockEffectFirstFrameReady();
        if (unlockEffectOverlayAttached || unlockEffectView == null) {
            if (unlockEffectOverlayAttached && unlockEffectView != null) {
                if (exposeReadySurface) {
                    showUnlockEffectView(unlockEffectView);
                } else {
                    parkUnlockEffectOverlayForScreenOff();
                }
            }
            return;
        }
        if (exposeReadySurface) {
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
                refreshUnlockEffectReadiness("already_attached");
                if (visible && isUnlockEffectFirstFrameReady()) {
                    showUnlockEffectView(unlockEffectView);
                } else {
                    parkUnlockEffectOverlayForScreenOff();
                }
                Log.w(TAG, "unlock effect overlay already attached type="
                        + unlockEffectRendererType);
                return;
            }
            Log.e(TAG, "unlock effect overlay addView failed type=" + unlockEffectRendererType, e);
            unlockEffectOverlayAttached = false;
            // Accessibility's overlay token can be transiently unavailable while the
            // service is rebinding after an APK update or during a display transition.
            // Keep the expensive native renderer and its decoded/GL state in RAM; the
            // lockscreen poll will retry after a short backoff instead of constructing
            // and destroying a new renderer every 10 ms.
            unlockEffectOverlayAddRetryAt = SystemClock.uptimeMillis() + 250L;
            return;
        }
        unlockEffectOverlayAddRetryAt = 0L;
        unlockEffectOverlayAttached = true;
        refreshUnlockEffectReadiness("attached");
        unlockEffectView.post(new Runnable() {
            @Override
            public void run() {
                if (unlockEffectRenderer != null) {
                    unlockEffectRenderer.warmUp();
                }
                refreshUnlockEffectReadiness("attached_post");
                if (visible && isUnlockEffectFirstFrameReady()) {
                    showUnlockEffectView(unlockEffectView);
                }
                scheduleRippleRendererReadinessCheck();
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

    private void preloadAndAttachSelectedUnlockEffectParked(String reason) {
        if (!OverlayPrefs.masterEnabled(this)
                || !isUnlockEffectEnabledForActivePanel()
                || isChargingDoodleModeEnabled()) {
            return;
        }
        preloadUnlockEffectRenderer();
        int effect = OverlayPrefs.unlockEffect(this);
        if (unlockEffectRenderer == null || unlockEffectRendererType != effect) {
            return;
        }
        // Attaching an empty screenshot-backed renderer would make the accessibility
        // window contaminate/block the bootstrap capture. Wait for a real cache instead.
        if (effectUsesCachedScreenshotBackground(effect)
                && !hasUnlockEffectBackgroundSource(effect)) {
            Log.i(TAG, "unlock effect parked attach deferred; background missing reason="
                    + reason + " type=" + effect);
            return;
        }
        syncUnlockEffectOverlay(false);
        refreshUnlockEffectReadiness("parked:" + reason);
        Log.i(TAG, "unlock effect preloaded parked reason=" + reason
                + " type=" + effect
                + " attached=" + unlockEffectOverlayAttached
                + " readiness=" + unlockEffectReadinessState
                + " detail=" + unlockEffectReadinessDetail);
    }

    private void registerUnlockEffectReadinessListener() {
        unlockEffectReadinessState = UnlockEffectReadiness.STATE_CONSTRUCTED;
        unlockEffectReadinessDetail = "constructed";
        if (!(unlockEffectRenderer instanceof UnlockEffectReadiness)) {
            return;
        }
        final UnlockEffectRenderer expectedRenderer = unlockEffectRenderer;
        ((UnlockEffectReadiness) unlockEffectRenderer).setReadinessListener(
                new UnlockEffectReadiness.ReadinessListener() {
                    @Override
                    public void onReadinessChanged() {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (unlockEffectRenderer == expectedRenderer) {
                                    handler.removeCallbacks(
                                            unlockEffectReadinessChangedRunnable);
                                    handler.post(unlockEffectReadinessChangedRunnable);
                                }
                            }
                        });
                    }
                });
        refreshUnlockEffectReadiness("registered");
    }

    private void refreshUnlockEffectReadiness(String reason) {
        int previous = unlockEffectReadinessState;
        String previousDetail = unlockEffectReadinessDetail;
        if (unlockEffectRenderer == null) {
            unlockEffectReadinessState = UnlockEffectReadiness.STATE_DETACHED;
            unlockEffectReadinessDetail = "no_renderer";
        } else if (unlockEffectRenderer instanceof UnlockEffectReadiness) {
            UnlockEffectReadiness readiness = (UnlockEffectReadiness) unlockEffectRenderer;
            try {
                unlockEffectReadinessState = readiness.getReadinessState();
                unlockEffectReadinessDetail = readiness.getReadinessDetail();
            } catch (Throwable t) {
                unlockEffectReadinessState = UnlockEffectReadiness.STATE_FAILED;
                unlockEffectReadinessDetail = "query_failed:" + t.getClass().getSimpleName();
            }
        } else {
            unlockEffectReadinessState = unlockEffectOverlayAttached
                    ? UnlockEffectReadiness.STATE_FIRST_FRAME_READY
                    : UnlockEffectReadiness.STATE_CONSTRUCTED;
            unlockEffectReadinessDetail = unlockEffectOverlayAttached
                    ? "legacy_attached" : "legacy_constructed";
        }
        if (unlockEffectReadinessDetail == null) {
            unlockEffectReadinessDetail = "";
        }
        if (previous != unlockEffectReadinessState
                || !unlockEffectReadinessDetail.equals(previousDetail)) {
            Log.i(TAG, "unlock effect readiness reason=" + reason
                    + " type=" + unlockEffectRendererType
                    + " state=" + previous + "->" + unlockEffectReadinessState
                    + " detail=" + unlockEffectReadinessDetail);
        }
        if (isUnlockEffectFirstFrameReady()) {
            replayBufferedReadinessGesture();
            if (unlockFxVisible && isUnlockEffectGestureReady()
                    && !unlockEffectGestureActive) {
                showUnlockEffectView(unlockEffectView);
            } else if (unlockEffectOverlayParked && unlockEffectView != null) {
                // A zero-alpha HWUI root can be culled before Lens Flare builds its first
                // display list. The parked prewarm uses WARM_PARK_ALPHA only until the renderer
                // acknowledges its first transparent frame, then becomes fully invisible.
                unlockEffectView.setAlpha(0f);
            }
        }
    }

    private boolean isUnlockEffectFirstFrameReady() {
        return unlockEffectRenderer != null
                && unlockEffectRendererType == OverlayPrefs.unlockEffect(this)
                && unlockEffectOverlayAttached
                && unlockEffectReadinessState
                >= UnlockEffectReadiness.STATE_FIRST_FRAME_READY;
    }

    private void preloadUnlockEffectRenderer() {
        if (!isUnlockEffectEnabledForActivePanel()) {
            unloadUnlockEffects("panel_effect_disabled:" + activeDisplayProfile);
            return;
        }
        if (isChargingDoodleModeEnabled()) {
            unloadUnlockEffects("doodle:preload_blocked");
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        int effect = OverlayPrefs.unlockEffect(this);
        if (unlockEffectRenderer != null && unlockEffectRendererType == effect) {
            if (!unlockEffectRendererNeedsRecreate || !isRecreatableNativeEffect(effect)) {
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
                unlockEffectRenderer = new LensFlareEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE) {
                if (EffectAvailability.is64BitProcess()) {
                    S3Arm64RippleEffectView renderer =
                            new S3Arm64RippleEffectView(rendererContext());
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException("Water Ripple ARM64 renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                } else {
                    unlockEffectRenderer = new S3NativeRippleEffectView(rendererContext());
                }
            } else if (effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES) {
                if (EffectAvailability.is64BitProcess()) {
                    AbstractTilesArm64EffectView renderer =
                            new AbstractTilesArm64EffectView(rendererContext());
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Abstract Tiles ARM64 renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                } else {
                    unlockEffectRenderer =
                            SamsungLockBgEffectView.abstractTiles(rendererContext());
                }
            } else if (effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC) {
                if (EffectAvailability.is64BitProcess()) {
                    GeometricMosaicArm64EffectView renderer =
                            new GeometricMosaicArm64EffectView(rendererContext());
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Geometric Mosaic ARM64 renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                } else {
                    unlockEffectRenderer =
                            SamsungLockBgEffectView.geometricMosaic(rendererContext());
                }
            } else if (effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS) {
                unlockEffectRenderer = new PoppingColoursEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_TABS_BLIND) {
                BlindDexEffectView renderer = new BlindDexEffectView(rendererContext());
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException("Tab S Blind DEX renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else if (effect == OverlayPrefs.EFFECT_WATERCOLOUR) {
                WatercolorNativeEffectView renderer =
                        new WatercolorNativeEffectView(rendererContext());
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException("Watercolor native renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET) {
                ColourDropletEffectView renderer =
                        new ColourDropletEffectView(rendererContext(), false);
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException("Colour Droplet native renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO) {
                ColourDropletEffectView renderer =
                        new ColourDropletEffectView(rendererContext(), true);
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException("Colour Droplet + Gyro native renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
                SparklingBubblesEffectView renderer =
                        new SparklingBubblesEffectView(rendererContext());
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException("Sparkling Bubbles native renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else {
                unlockEffectRenderer = null;
            }
        } catch (Throwable t) {
            Log.e(TAG, "unlock effect renderer preload failed type=" + effect, t);
            if (unlockEffectRenderer != null) {
                try {
                    unlockEffectRenderer.destroy();
                } catch (Throwable destroyError) {
                    Log.d(TAG, "failed renderer cleanup ignored", destroyError);
                }
            }
            unlockEffectRenderer = null;
            unlockEffectView = null;
            unlockEffectRendererType = -1;
            unlockEffectRendererNeedsRecreate = false;
            unlockEffectRendererRecreateReason = "";
            if (effect == OverlayPrefs.EFFECT_S4_LENS_FLARE) {
                return;
            }
            int failedEffect = effect;
            effect = OverlayPrefs.EFFECT_S4_LENS_FLARE;
            setUnlockEffectPreferenceInternally(effect);
            unlockEffectRendererType = effect;
            try {
                unlockEffectRenderer = new LensFlareEffectView(rendererContext());
                Log.w(TAG, "native renderer fell back to Lens Flare failedType="
                        + failedEffect);
            } catch (Throwable fallbackError) {
                Log.e(TAG, "Lens Flare fallback failed", fallbackError);
                unlockEffectRenderer = null;
                unlockEffectRendererType = -1;
                return;
            }
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
        registerUnlockEffectReadinessListener();
        if (isRecreatableNativeEffect(effect)) {
            DisplayMetrics metrics = activeDisplayMetrics();
            unlockEffectRendererDisplayWidth = Math.max(1, metrics.widthPixels);
            unlockEffectRendererDisplayHeight = Math.max(1, metrics.heightPixels);
        } else {
            unlockEffectRendererDisplayWidth = 0;
            unlockEffectRendererDisplayHeight = 0;
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

    private void scheduleRippleRendererReadinessCheck() {
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        boolean ripple = unlockEffectRendererType == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                && unlockEffectRenderer instanceof S3Arm64RippleEffectView;
        boolean abstractTiles =
                unlockEffectRendererType == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                && unlockEffectRenderer instanceof AbstractTilesArm64EffectView;
        boolean geometricMosaic =
                unlockEffectRendererType == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                && unlockEffectRenderer instanceof GeometricMosaicArm64EffectView;
        if (ripple || abstractTiles || geometricMosaic) {
            handler.postDelayed(rippleRendererReadinessRunnable, 250L);
        }
    }

    private void fallBackFromFailedRippleRenderer(String reason) {
        if (unlockEffectRendererType != OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE) {
            return;
        }
        Log.e(TAG, "Water Ripple failed; falling back to Lens Flare reason="
                + reason);
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        destroyUnlockEffectOverlay();
        setUnlockEffectPreferenceInternally(OverlayPrefs.EFFECT_S4_LENS_FLARE);
        preloadUnlockEffectRenderer();
        evaluateVisibility("ripple_renderer_failed", false);
    }

    private void fallBackFromFailedAbstractTilesRenderer(String reason) {
        if (unlockEffectRendererType != OverlayPrefs.EFFECT_S4_ABSTRACT_TILES) {
            return;
        }
        Log.e(TAG, "Abstract Tiles ARM64 failed; falling back to Lens Flare reason="
                + reason);
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        destroyUnlockEffectOverlay();
        setUnlockEffectPreferenceInternally(OverlayPrefs.EFFECT_S4_LENS_FLARE);
        preloadUnlockEffectRenderer();
        evaluateVisibility("abstract_tiles_renderer_failed", false);
    }

    private void fallBackFromFailedGeometricMosaicRenderer(String reason) {
        if (unlockEffectRendererType != OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC) {
            return;
        }
        Log.e(TAG, "Geometric Mosaic ARM64 failed; falling back to Lens Flare reason="
                + reason);
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        destroyUnlockEffectOverlay();
        setUnlockEffectPreferenceInternally(OverlayPrefs.EFFECT_S4_LENS_FLARE);
        preloadUnlockEffectRenderer();
        evaluateVisibility("geometric_mosaic_renderer_failed", false);
    }

    private boolean canRecreateStaleLockBgRenderer() {
        return !unlockEffectGestureActive
                && !pinEntryPending
                && !pinEntryRequested
                && !pinEntrySurfaceVisible
                && !notificationShadeVisible
                && !isCallSurfaceActive();
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
            DisplayMetrics metrics = activeDisplayMetrics();
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
        if (unlockEffectRendererType == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || unlockEffectRendererType == OverlayPrefs.EFFECT_WATERCOLOUR
                || OverlayPrefs.isColourDropletEffect(unlockEffectRendererType)
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                || unlockEffectRendererType == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || unlockEffectRendererType == OverlayPrefs.EFFECT_TABS_BLIND) {
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
        if (!isUnlockEffectAllowedNowForActivePanel()) {
            return;
        }
        int effect = OverlayPrefs.unlockEffect(this);
        if (usesImportedEffectBackground(effect, activeDisplayProfile)) {
            if (OverlayPrefs.effectBackgroundWakeCaptureActive(this)) {
                completeForcedEffectBackgroundRefresh("imported_source_active");
            }
            loadCachedUnlockEffectBackgroundSourceIfNeeded(effect);
            return;
        }
        if (!effectUsesScreenshotBackground(effect)
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || colorScreenshotInFlight) {
            return;
        }
        boolean hasBackground = hasUnlockEffectBackgroundSource(effect);
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
        final int captureDisplayId = activeDisplayId == Display.INVALID_DISPLAY
                ? Display.DEFAULT_DISPLAY : activeDisplayId;
        final String captureProfile = activeDisplayProfile;
        colorScreenshotAttemptedThisSession = true;
        colorScreenshotInFlight = true;
        unlockEffectBackgroundCaptureAttempts++;
        if ((OverlayPrefs.isColourDropletEffect(captureEffect)
                || captureEffect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES)
                && unlockEffectRenderer != null) {
            unlockEffectRenderer.resetEffect();
        }
        Log.i(TAG, "unlock effect background screenshot requested reason=" + reason
                + " sinceScreenOnMs=" + elapsedSinceScreenOn()
                + " displayState=" + displayStateName(currentDisplayState())
                + " displayId=" + captureDisplayId
                + " profile=" + captureProfile
                + " effect=" + captureEffect
                + " pkg=" + lastWindowPackage);
        try {
            takeScreenshot(captureDisplayId, mainExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    Bitmap bitmap = bitmapFromScreenshot(screenshotResult);
                    if (!finishUnlockEffectBackgroundCapture(captureGeneration)) {
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        Log.i(TAG, "stale unlock effect background callback ignored generation="
                                + captureGeneration + "/" + unlockEffectBackgroundGeneration);
                        return;
                    }
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
                            || captureDisplayId != activeDisplayId
                            || !captureProfile.equals(activeDisplayProfile)
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
                                + " gesture=" + unlockEffectGestureActive
                                + " fxAttached=" + unlockEffectOverlayAttached
                                + " fxParked=" + unlockEffectOverlayParked
                                + " interactive="
                                + (powerManager == null || powerManager.isInteractive())
                                + " locked=" + isLockscreenLocked(false)
                                + " displayState=" + displayStateName(currentDisplayState())
                                + " pkg=" + lastWindowPackage);
                        bitmap.recycle();
                        if (!retryUnlockEffectBackgroundCapture(
                                captureEffect, "discarded:" + reason)) {
                            completeForcedEffectBackgroundRefresh("discarded");
                        }
                        return;
                    }
                    long now = SystemClock.uptimeMillis();
                    if (!isValidUnlockEffectBackgroundScreenshot(
                            bitmap, captureEffect, captureProfile, reason)) {
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
                    persistEffectBackgroundScreenshotAsync(
                            bitmap, captureGeneration, captureEffect, captureProfile);
                    unlockEffectBackgroundCapturedAt = now;
                    unlockEffectBackgroundEffect = captureEffect;
                    unlockEffectBackgroundNextAttemptAt = 0L;
                    unlockEffectBackgroundCaptureSucceededThisSession = true;
                    skipCachedEffectBackgroundLoad = false;
                    Log.i(TAG, "unlock effect background screenshot applied reason=" + reason
                            + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                            + " displayState=" + displayStateName(currentDisplayState())
                            + " displayId=" + captureDisplayId
                            + " profile=" + captureProfile
                            + " effect=" + captureEffect
                            + " pkg=" + lastWindowPackage);
                    bitmap.recycle();
                    completeForcedEffectBackgroundRefresh("applied");
                    showPendingUnlockAffordance("background:" + reason);
                }

                @Override
                public void onFailure(int errorCode) {
                    if (!finishUnlockEffectBackgroundCapture(captureGeneration)) {
                        Log.i(TAG, "stale unlock effect background failure ignored generation="
                                + captureGeneration + "/" + unlockEffectBackgroundGeneration);
                        return;
                    }
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
            finishUnlockEffectBackgroundCapture(captureGeneration);
            Log.d(TAG, "unlock effect background screenshot request failed reason=" + reason, t);
            if (!retryUnlockEffectBackgroundCapture(
                    captureEffect, "exception:" + reason)) {
                completeForcedEffectBackgroundRefresh("exception");
            }
            showPendingUnlockAffordance("background_request_failed:" + reason);
        }
    }

    private boolean finishUnlockEffectBackgroundCapture(int generation) {
        if (generation != unlockEffectBackgroundGeneration) {
            return false;
        }
        colorScreenshotInFlight = false;
        return true;
    }

    private boolean shouldRunUnlockEffectBackgroundPreflight(boolean interactive,
            boolean displayOn, boolean locked, boolean aodSurface,
            boolean hideOverlaysForTouchBoxCapture, boolean hideOverlaysForBackgroundCapture,
            boolean blockedSurfaceActive) {
        if (!interactive || !displayOn || !locked || aodSurface
                || hideOverlaysForTouchBoxCapture || hideOverlaysForBackgroundCapture
                || blockedSurfaceActive || !isUnlockEffectAllowedNowForActivePanel()) {
            return false;
        }
        int effect = OverlayPrefs.unlockEffect(this);
        if (!effectUsesCachedScreenshotBackground(effect)) {
            return false;
        }
        if (usesImportedEffectBackground(effect, activeDisplayProfile)) {
            return false;
        }
        if (OverlayPrefs.effectBackgroundRefreshToken(this)
                != OverlayPrefs.effectBackgroundHandledRefreshToken(
                        this, effect, activeDisplayProfile)) {
            return true;
        }
        if (!hasUsableEffectBackgroundCache(effect)) {
            return true;
        }
        return OverlayPrefs.effectBackgroundAutoRefreshEnabled(this);
    }

    private boolean shouldRefreshUnlockEffectBackground(int effect, boolean hasBackground,
            String reason) {
        if (usesImportedEffectBackground(effect, activeDisplayProfile)) {
            return false;
        }
        if (OverlayPrefs.effectBackgroundWakeCaptureActive(this)
                && effectUsesCachedScreenshotBackground(effect)) {
            return true;
        }
        if (effectUsesCachedScreenshotBackground(effect)
                && OverlayPrefs.effectBackgroundRefreshToken(this)
                != OverlayPrefs.effectBackgroundHandledRefreshToken(
                        this, effect, activeDisplayProfile)) {
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
            long lastCapturedAt = OverlayPrefs.effectBackgroundLastCapturedAt(
                    this, effect, activeDisplayProfile);
            if (lastCapturedAt <= 0L) {
                File file = OverlayPrefs.effectBackgroundFile(
                        this, effect, activeDisplayProfile);
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
        if (usesImportedEffectBackground(
                OverlayPrefs.unlockEffect(this), activeDisplayProfile)) {
            return false;
        }
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
        return !usesImportedEffectBackground(effect, activeDisplayProfile)
                && effectUsesScreenshotBackground(effect)
                && (OverlayPrefs.effectBackgroundWakeCaptureActive(this)
                || !hasUnlockEffectBackgroundSource(effect))
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
            String profile, String reason) {
        if (bitmap == null || bitmap.isRecycled()
                || bitmap.getWidth() < 100 || bitmap.getHeight() < 100) {
            return false;
        }
        if (!FoldDisplayTarget.bitmapMatches(profile, bitmap.getWidth(), bitmap.getHeight(),
                activeDisplayWidth, activeDisplayHeight)) {
            Log.i(TAG, "background screenshot rejected: panel mismatch reason=" + reason
                    + " profile=" + profile
                    + " bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + " target=" + activeDisplayWidth + "x" + activeDisplayHeight);
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
        boolean missingRequiredBackground = effectUsesCachedScreenshotBackground(effect)
                && !hasUnlockEffectBackgroundSource(effect);
        if (!OverlayPrefs.effectBackgroundWakeCaptureActive(this)
                && !missingRequiredBackground) {
            // Normal wakes keep an existing cache. Only a truly missing first-run cache,
            // an explicit one-shot request or the scheduled refresh receives retries.
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
        if (usesImportedEffectBackground(effect, activeDisplayProfile)) {
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
        long last = OverlayPrefs.effectBackgroundLastCapturedAt(
                this, effect, activeDisplayProfile);
        File file = OverlayPrefs.effectBackgroundFile(this, effect, activeDisplayProfile);
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
        if (effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE) {
            minScreenOnMs = S3_RIPPLE_SCREENSHOT_MIN_SCREEN_ON_MS;
        } else if (effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS) {
            minScreenOnMs = S5_POPPING_SCREENSHOT_MIN_SCREEN_ON_MS;
        } else if (effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC) {
            minScreenOnMs = S4_LOCKBG_SCREENSHOT_MIN_SCREEN_ON_MS;
        } else if (OverlayPrefs.isColourDropletEffect(effect)) {
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH) {
            return Display.STATE_UNKNOWN;
        }
        try {
            Display display = displayManager == null ? null
                    : displayManager.getDisplay(activeDisplayId == Display.INVALID_DISPLAY
                    ? Display.DEFAULT_DISPLAY : activeDisplayId);
            if (display == null && windowManager != null) {
                display = windowManager.getDefaultDisplay();
            }
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

    private void reloadSelectedEffectBackgroundSource(String reason) {
        unlockEffectBackgroundGeneration++;
        colorScreenshotInFlight = false;
        colorScreenshotAttemptedThisSession = false;
        unlockEffectBackgroundCaptureSucceededThisSession = false;
        unlockEffectBackgroundNextAttemptAt = 0L;
        unlockEffectBackgroundLastWaitLogAt = 0L;
        unlockEffectBackgroundCaptureAttempts = 0;
        skipCachedEffectBackgroundLoad = false;
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        clearCachedUnlockEffectBackgroundBitmap();
        if (unlockEffectRenderer instanceof BackgroundSourceRenderer) {
            ((BackgroundSourceRenderer) unlockEffectRenderer).clearBackgroundSourceBitmap();
        }
        unlockEffectBackgroundCapturedAt = 0L;
        unlockEffectBackgroundEffect = -1;
        // Recreate the renderer as well as its source so a DEX/native host cannot retain
        // texture state from the previous AUTO/IMPORTED selection.
        destroyUnlockEffectOverlay();
        preloadAndAttachSelectedUnlockEffectParked("background_source_reload");
        int effect = OverlayPrefs.unlockEffect(this);
        if (usesImportedEffectBackground(effect, activeDisplayProfile)) {
            OverlayPrefs.get(this).edit()
                    .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE, false)
                    .putBoolean(
                            OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK, false)
                    .apply();
            handler.removeCallbacks(forcedEffectBackgroundTimeoutRunnable);
            handler.removeCallbacks(forcedEffectBackgroundSleepRunnable);
        }
        scheduleEffectBackgroundRefreshAlarm(reason);
        Log.i(TAG, "unlock effect background source reloaded reason=" + reason
                + " effect=" + effect
                + " profile=" + activeDisplayProfile
                + " imported="
                + usesImportedEffectBackground(effect, activeDisplayProfile));
    }

    private boolean usesImportedEffectBackground(int effect, String profile) {
        return OverlayPrefs.importedEffectBackgroundEnabled(this, effect, profile);
    }

    private boolean loadImportedEffectBackgroundSource(
            int effect, BackgroundSourceRenderer backgroundRenderer, long startedAt) {
        File file = OverlayPrefs.importedEffectBackgroundFile(
                this, effect, activeDisplayProfile);
        if (!ManualEffectBackground.isUsable(file)) {
            Log.w(TAG, "imported effect wallpaper unavailable; automatic capture remains paused"
                    + " effect=" + effect
                    + " profile=" + activeDisplayProfile
                    + " file=" + file);
            colorScreenshotAttemptedThisSession = true;
            return false;
        }
        int width = activeDisplayWidth > 0
                ? activeDisplayWidth : Math.max(1, activeDisplayMetrics().widthPixels);
        int height = activeDisplayHeight > 0
                ? activeDisplayHeight : Math.max(1, activeDisplayMetrics().heightPixels);
        long fileLength = file.length();
        long fileModified = file.lastModified();
        String filePath = file.getAbsolutePath();
        boolean memoryCacheHit = hasCachedUnlockEffectBackground(
                effect, activeDisplayProfile, fileLength, fileModified, filePath);
        Bitmap bitmap = memoryCacheHit ? cachedUnlockEffectBackgroundBitmap
                : ManualEffectBackground.decodeCenterCrop(file, width, height);
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w(TAG, "imported effect wallpaper decode failed; automatic capture remains paused"
                    + " effect=" + effect
                    + " profile=" + activeDisplayProfile
                    + " file=" + filePath);
            colorScreenshotAttemptedThisSession = true;
            return false;
        }
        if (!memoryCacheHit) {
            replaceCachedUnlockEffectBackgroundBitmap(bitmap, effect,
                    activeDisplayProfile, fileLength, fileModified, filePath);
        } else {
            cachedUnlockEffectBackgroundEffect = effect;
        }
        backgroundRenderer.setBackgroundSourceBitmap(
                bitmap, BackgroundSourceRenderer.SHARED_CACHE_SOURCE);
        unlockEffectBackgroundCapturedAt = SystemClock.uptimeMillis();
        unlockEffectBackgroundEffect = effect;
        colorScreenshotAttemptedThisSession = true;
        unlockEffectBackgroundCaptureSucceededThisSession = true;
        skipCachedEffectBackgroundLoad = false;
        Log.i(TAG, "imported effect wallpaper loaded size="
                + bitmap.getWidth() + "x" + bitmap.getHeight()
                + " original="
                + OverlayPrefs.importedEffectBackgroundWidth(
                        this, effect, activeDisplayProfile)
                + "x"
                + OverlayPrefs.importedEffectBackgroundHeight(
                        this, effect, activeDisplayProfile)
                + " effect=" + effect
                + " profile=" + activeDisplayProfile
                + " memoryCache=" + memoryCacheHit
                + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
        return true;
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
        if (usesImportedEffectBackground(effect, activeDisplayProfile)) {
            loadImportedEffectBackgroundSource(effect, backgroundRenderer, startedAt);
            return;
        }
        if (backgroundRenderer.hasBackgroundSourceBitmap()) {
            return;
        }
        File file = findBestEffectBackgroundCacheFile(effect);
        if (file == null) {
            return;
        }
        repairSharedEffectBackgroundMetadataIfNeeded(effect, file);
        Bitmap bitmap = null;
        try {
            long fileLength = file.length();
            long fileModified = file.lastModified();
            boolean memoryCacheHit = hasCachedUnlockEffectBackground(
                    effect, activeDisplayProfile, fileLength, fileModified,
                    file.getAbsolutePath());
            long decodeStartedAt = SystemClock.uptimeMillis();
            if (memoryCacheHit) {
                bitmap = cachedUnlockEffectBackgroundBitmap;
                cachedUnlockEffectBackgroundEffect = effect;
            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (bitmap != null && !bitmap.isRecycled()) {
                    replaceCachedUnlockEffectBackgroundBitmap(
                            bitmap, effect, activeDisplayProfile, fileLength, fileModified,
                            file.getAbsolutePath());
                }
            }
            long decodeMs = SystemClock.uptimeMillis() - decodeStartedAt;
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            long applyStartedAt = SystemClock.uptimeMillis();
            backgroundRenderer.setBackgroundSourceBitmap(
                    bitmap, BackgroundSourceRenderer.SHARED_CACHE_SOURCE);
            long applyMs = SystemClock.uptimeMillis() - applyStartedAt;
            unlockEffectBackgroundCapturedAt = SystemClock.uptimeMillis();
            unlockEffectBackgroundEffect = effect;
            colorScreenshotAttemptedThisSession = true;
            Log.i(TAG, "unlock effect background cache loaded size="
                    + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + " effect=" + effect
                    + " profile=" + activeDisplayProfile
                    + " displayId=" + activeDisplayId
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
        if (usesImportedEffectBackground(effect, activeDisplayProfile)) {
            return ManualEffectBackground.isUsable(
                    OverlayPrefs.importedEffectBackgroundFile(
                            this, effect, activeDisplayProfile));
        }
        return findBestEffectBackgroundCacheFile(effect) != null;
    }

    private File findBestEffectBackgroundCacheFile(int effect) {
        File profileFile = OverlayPrefs.effectBackgroundFile(
                this, effect, activeDisplayProfile);
        if (isUsableEffectBackgroundCacheFileForActiveProfile(profileFile)) {
            return profileFile;
        }
        if (!FoldDisplayTarget.PROFILE_SINGLE.equals(activeDisplayProfile)) {
            File oldShared = OverlayPrefs.effectBackgroundFile(this, effect);
            if (isUsableEffectBackgroundCacheFileForActiveProfile(oldShared)
                    && copyEffectBackgroundCacheFile(oldShared, profileFile)) {
                Log.i(TAG, "effect background single cache migrated profile="
                        + activeDisplayProfile);
                return profileFile;
            }
        }
        File legacy = findLatestLegacyEffectBackgroundCacheFile();
        if (legacy != null && copyEffectBackgroundCacheFile(legacy, profileFile)) {
            Log.i(TAG, "effect background legacy cache migrated profile="
                    + activeDisplayProfile);
            return profileFile;
        }
        return null;
    }

    private void repairSharedEffectBackgroundMetadataIfNeeded(int effect, File file) {
        File shared = OverlayPrefs.effectBackgroundFile(this, effect, activeDisplayProfile);
        int refreshToken = OverlayPrefs.effectBackgroundRefreshToken(this);
        if (file == null || !file.equals(shared) || refreshToken <= 0
                || OverlayPrefs.effectBackgroundHandledRefreshToken(
                        this, effect, activeDisplayProfile)
                == refreshToken) {
            return;
        }
        int[] effects = {
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_TABS_BLIND,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
        };
        for (int candidate : effects) {
            if (candidate == effect
                    || OverlayPrefs.effectBackgroundHandledRefreshToken(
                            this, candidate, activeDisplayProfile)
                    != refreshToken
                    || OverlayPrefs.effectBackgroundLastCapturedAt(
                            this, candidate, activeDisplayProfile) <= 0L) {
                continue;
            }
            OverlayPrefs.saveEffectBackgroundLastCapturedAt(
                    this, effect, activeDisplayProfile, Math.max(1L, file.lastModified()));
            OverlayPrefs.saveEffectBackgroundHandledRefreshToken(
                    this, effect, activeDisplayProfile, refreshToken);
            Log.i(TAG, "shared effect background metadata repaired effect=" + effect
                    + " profile=" + activeDisplayProfile + " token=" + refreshToken);
            return;
        }
    }

    private File findLatestLegacyEffectBackgroundCacheFile() {
        File best = null;
        int[] effects = {
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_TABS_BLIND,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
        };
        for (int candidate : effects) {
            File file = OverlayPrefs.legacyEffectBackgroundFile(this, candidate);
            if (!isUsableEffectBackgroundCacheFileForActiveProfile(file)) {
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

    private boolean isUsableEffectBackgroundCacheFileForActiveProfile(File file) {
        if (!isUsableEffectBackgroundCacheFile(file)) {
            return false;
        }
        if (FoldDisplayTarget.PROFILE_SINGLE.equals(activeDisplayProfile)) {
            return true;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            return FoldDisplayTarget.bitmapMatches(
                    activeDisplayProfile,
                    bounds.outWidth,
                    bounds.outHeight,
                    activeDisplayWidth,
                    activeDisplayHeight);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hasCachedUnlockEffectBackground(int effect, String profile, long fileLength,
            long fileModified, String filePath) {
        return cachedUnlockEffectBackgroundBitmap != null
                && !cachedUnlockEffectBackgroundBitmap.isRecycled()
                && cachedUnlockEffectBackgroundEffect == effect
                && FoldDisplayTarget.normalizeProfile(profile)
                .equals(cachedUnlockEffectBackgroundProfile)
                && cachedUnlockEffectBackgroundFileLength == fileLength
                && cachedUnlockEffectBackgroundFileModified == fileModified
                && (filePath == null ? "" : filePath)
                .equals(cachedUnlockEffectBackgroundFilePath);
    }

    private void replaceCachedUnlockEffectBackgroundBitmap(
            Bitmap bitmap,
            int effect,
            String profile,
            long fileLength,
            long fileModified,
            String filePath) {
        if (bitmap == cachedUnlockEffectBackgroundBitmap) {
            cachedUnlockEffectBackgroundEffect = effect;
            cachedUnlockEffectBackgroundProfile =
                    FoldDisplayTarget.normalizeProfile(profile);
            cachedUnlockEffectBackgroundFileLength = fileLength;
            cachedUnlockEffectBackgroundFileModified = fileModified;
            cachedUnlockEffectBackgroundFilePath = filePath == null ? "" : filePath;
            return;
        }
        Bitmap previous = cachedUnlockEffectBackgroundBitmap;
        cachedUnlockEffectBackgroundBitmap = bitmap;
        cachedUnlockEffectBackgroundEffect = effect;
        cachedUnlockEffectBackgroundProfile = FoldDisplayTarget.normalizeProfile(profile);
        cachedUnlockEffectBackgroundFileLength = fileLength;
        cachedUnlockEffectBackgroundFileModified = fileModified;
        cachedUnlockEffectBackgroundFilePath = filePath == null ? "" : filePath;
        if (previous != null && previous != bitmap) {
            if (unlockEffectRenderer instanceof BackgroundSourceRenderer
                    && ((BackgroundSourceRenderer) unlockEffectRenderer)
                    .isUsingBackgroundSourceBitmap(previous)) {
                ((BackgroundSourceRenderer) unlockEffectRenderer)
                        .setBackgroundSourceBitmap(
                                bitmap, BackgroundSourceRenderer.SHARED_CACHE_SOURCE);
            }
            if (!previous.isRecycled()) {
                previous.recycle();
            }
        }
    }

    private void clearCachedUnlockEffectBackgroundBitmap() {
        if (cachedUnlockEffectBackgroundBitmap != null
                && unlockEffectRenderer instanceof BackgroundSourceRenderer
                && ((BackgroundSourceRenderer) unlockEffectRenderer)
                .isUsingBackgroundSourceBitmap(cachedUnlockEffectBackgroundBitmap)) {
            ((BackgroundSourceRenderer) unlockEffectRenderer).clearBackgroundSourceBitmap();
        }
        if (cachedUnlockEffectBackgroundBitmap != null
                && !cachedUnlockEffectBackgroundBitmap.isRecycled()) {
            cachedUnlockEffectBackgroundBitmap.recycle();
        }
        cachedUnlockEffectBackgroundBitmap = null;
        cachedUnlockEffectBackgroundEffect = -1;
        cachedUnlockEffectBackgroundProfile = "";
        cachedUnlockEffectBackgroundFileLength = 0L;
        cachedUnlockEffectBackgroundFileModified = 0L;
        cachedUnlockEffectBackgroundFilePath = "";
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
                    activeDisplayProfile, file.length(), file.lastModified(),
                    file.getAbsolutePath());
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

    private boolean hasUnlockEffectBackgroundSource(int effect) {
        if (unlockEffectRendererType == effect
                && unlockEffectRenderer instanceof BackgroundSourceRenderer
                && ((BackgroundSourceRenderer) unlockEffectRenderer)
                .hasBackgroundSourceBitmap()) {
            return true;
        }
        return hasUsableEffectBackgroundCache(effect);
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
        String pendingProfile = pendingTouchBoxScreenshotProfile();
        if (!pendingProfile.equals(activeDisplayProfile)) {
            if (touchBoxScreenshotScheduled) {
                touchBoxScreenshotScheduled = false;
                handler.removeCallbacks(touchBoxScreenshotDelayRunnable);
            }
            markTouchBoxCaptureWaitingForLockscreen();
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
                + " profile=" + pendingProfile
                + " delayMs=" + TOUCH_BOX_SCREENSHOT_DELAY_MS);
    }

    private void runTouchBoxScreenshotDelay() {
        touchBoxScreenshotScheduled = false;
        if (!isTouchBoxScreenshotPending()) {
            return;
        }
        if (!pendingTouchBoxScreenshotProfile().equals(activeDisplayProfile)) {
            markTouchBoxCaptureWaitingForLockscreen();
            evaluateVisibility("touch_box_capture_wrong_panel", false);
            return;
        }
        if (!canCaptureTouchBoxScreenshot()) {
            markTouchBoxCaptureWaitingForLockscreen();
            evaluateVisibility("touch_box_capture_not_ready", false);
            return;
        }
        touchBoxScreenshotInFlight = true;
        touchBoxScreenshotInFlightRequestId = pendingTouchBoxScreenshotRequestId();
        if (touchBoxScreenshotInFlightRequestId <= 0) {
            touchBoxScreenshotInFlight = false;
            return;
        }
        OverlayPrefs.get(this).edit()
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                        OverlayPrefs.TOUCH_BOX_CAPTURE_CAPTURING)
                .remove(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR)
                .apply();
        removeDoodleOverlay();
        // A parked Samsung Surface/TextureView can still leave a compositor hole in an
        // accessibility screenshot even at alpha zero. Destroy the renderer window for
        // the wizard capture so the cached editor image cannot contain black/duplicated
        // surface tiles; the selected effect is reconstructed after capture.
        destroyUnlockEffectOverlay();
        removeTouchDebugOverlay();
        handler.postDelayed(touchBoxScreenshotCaptureRunnable,
                TOUCH_BOX_SCREENSHOT_OVERLAY_CLEAR_MS);
        Log.i(TAG, "touch box screenshot capture armed profile=" + activeDisplayProfile);
    }

    private void runTouchBoxScreenshotCapture() {
        final int captureRequestId = touchBoxScreenshotInFlightRequestId;
        if (!touchBoxCaptureMatches(captureRequestId, pendingTouchBoxScreenshotProfile())) {
            finishTouchBoxScreenshotAttempt(captureRequestId);
            return;
        }
        if (!canCaptureTouchBoxScreenshot()) {
            finishTouchBoxScreenshotAttempt(captureRequestId);
            markTouchBoxCaptureWaitingForLockscreen();
            evaluateVisibility("touch_box_capture_cancelled", false);
            return;
        }
        if (!pendingTouchBoxScreenshotProfile().equals(activeDisplayProfile)) {
            finishTouchBoxScreenshotAttempt(captureRequestId);
            markTouchBoxCaptureWaitingForLockscreen();
            evaluateVisibility("touch_box_capture_panel_changed", false);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            finishTouchBoxScreenshotAttempt(captureRequestId);
            failTouchBoxScreenshotCapture(captureRequestId,
                    "Screenshot requires Android 11+");
            return;
        }
        final int captureDisplayId = activeDisplayId == Display.INVALID_DISPLAY
                ? Display.DEFAULT_DISPLAY : activeDisplayId;
        final String captureProfile = activeDisplayProfile;
        try {
            touchBoxScreenshotCallbackPending = true;
            takeScreenshot(captureDisplayId, mainExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    finishTouchBoxScreenshotAttempt(captureRequestId);
                    Bitmap bitmap = bitmapFromScreenshot(screenshotResult);
                    if (bitmap == null) {
                        failTouchBoxScreenshotCapture(captureRequestId, "Screenshot empty");
                        return;
                    }
                    if (captureDisplayId != activeDisplayId
                            || !captureProfile.equals(activeDisplayProfile)
                            || !touchBoxCaptureMatches(captureRequestId, captureProfile)) {
                        bitmap.recycle();
                        evaluateVisibility("touch_box_capture_display_changed", false);
                        return;
                    }
                    if (persistTouchBoxScreenshot(bitmap, "wizard", captureProfile,
                            captureRequestId)) {
                        Log.i(TAG, "touch box screenshot capture ready profile="
                                + captureProfile);
                    } else {
                        failTouchBoxScreenshotCapture(captureRequestId,
                                "Screenshot save failed");
                    }
                    bitmap.recycle();
                    evaluateVisibility("touch_box_capture_done", false);
                }

                @Override
                public void onFailure(int errorCode) {
                    finishTouchBoxScreenshotAttempt(captureRequestId);
                    failTouchBoxScreenshotCapture(captureRequestId,
                            "Screenshot failed code=" + errorCode);
                    evaluateVisibility("touch_box_capture_failed", false);
                }
            });
        } catch (Throwable t) {
            finishTouchBoxScreenshotAttempt(captureRequestId);
            Log.d(TAG, "touch box screenshot request failed", t);
            failTouchBoxScreenshotCapture(captureRequestId, "Screenshot request failed");
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

    private String pendingTouchBoxScreenshotProfile() {
        if (prefs == null) {
            return activeDisplayProfile;
        }
        return FoldDisplayTarget.normalizeProfile(prefs.getString(
                OverlayPrefs.TOUCH_BOX_CAPTURE_PROFILE, activeDisplayProfile));
    }

    private boolean touchBoxCaptureMatches(int requestId, String profile) {
        return requestId > 0
                && isTouchBoxScreenshotPending()
                && requestId == pendingTouchBoxScreenshotRequestId()
                && FoldDisplayTarget.normalizeProfile(profile)
                .equals(pendingTouchBoxScreenshotProfile());
    }

    private void finishTouchBoxScreenshotAttempt(int requestId) {
        if (requestId != touchBoxScreenshotInFlightRequestId) {
            return;
        }
        touchBoxScreenshotInFlight = false;
        touchBoxScreenshotCallbackPending = false;
        touchBoxScreenshotInFlightRequestId = 0;
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

    private void failTouchBoxScreenshotCapture(int requestId, String message) {
        if (!touchBoxCaptureMatches(requestId, pendingTouchBoxScreenshotProfile())) {
            return;
        }
        OverlayPrefs.get(this).edit()
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                        OverlayPrefs.TOUCH_BOX_CAPTURE_FAILED)
                .putString(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR, message)
                .apply();
        Log.i(TAG, "touch box screenshot capture failed: " + message);
    }

    private boolean persistTouchBoxScreenshot(Bitmap bitmap, String sourceName,
            String profile, int requestId) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        if (!touchBoxCaptureMatches(requestId, profile)) {
            return false;
        }
        return writeTouchBoxScreenshotFile(bitmap, sourceName, profile, requestId);
    }

    private boolean writeTouchBoxScreenshotFile(Bitmap bitmap, String sourceName,
            String profile, int requestId) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        File file = OverlayPrefs.touchBoxScreenshotFile(this, profile);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return false;
            }
            output.flush();
            SharedPreferences.Editor editor = OverlayPrefs.get(this).edit()
                    .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                            OverlayPrefs.TOUCH_BOX_CAPTURE_READY)
                    .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_RESULT_ID, requestId)
                    .remove(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR);
            editor.apply();
            Log.i(TAG, "touch box screenshot saved source=" + sourceName
                    + " profile=" + FoldDisplayTarget.normalizeProfile(profile)
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
            final int effect, String profile) {
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
        final String capturedProfile = FoldDisplayTarget.normalizeProfile(profile);
        final int capturedRefreshToken = OverlayPrefs.effectBackgroundRefreshToken(this);
        try {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final File file = OverlayPrefs.effectBackgroundFile(
                            ChargingAccessibilityService.this, effect, capturedProfile);
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
                            if (copy.isRecycled()) {
                                return;
                            }
                            if (!file.exists()) {
                                copy.recycle();
                                return;
                            }
                            markSharedEffectBackgroundCacheCurrent(
                                    capturedProfile,
                                    System.currentTimeMillis(),
                                    capturedRefreshToken);
                            if (generation != unlockEffectBackgroundGeneration
                                    || !capturedProfile.equals(activeDisplayProfile)) {
                                copy.recycle();
                                return;
                            }
                            replaceCachedUnlockEffectBackgroundBitmap(
                                    copy,
                                    effect,
                                    capturedProfile,
                                    file.length(),
                                    file.lastModified(),
                                    file.getAbsolutePath());
                            if (unlockEffectRendererType == effect
                                    && capturedProfile.equals(activeDisplayProfile)
                                    && unlockEffectRenderer instanceof BackgroundSourceRenderer) {
                                ((BackgroundSourceRenderer) unlockEffectRenderer)
                                        .setBackgroundSourceBitmap(copy,
                                                BackgroundSourceRenderer.SHARED_CACHE_SOURCE);
                            }
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

    private void markSharedEffectBackgroundCacheCurrent(String profile, long timestamp,
            int refreshToken) {
        int[] effects = {
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_TABS_BLIND,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
        };
        SharedPreferences.Editor editor = OverlayPrefs.get(this).edit();
        for (int effect : effects) {
            editor.putLong(
                    OverlayPrefs.effectBackgroundLastCaptureKey(effect, profile),
                    Math.max(0L, timestamp));
            editor.putInt(
                    OverlayPrefs.effectBackgroundHandledRefreshTokenKey(effect, profile),
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
                || effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_TABS_BLIND
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
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
        List<Rect> boxes = resolveTouchBoxes();
        boolean standbyEnabled = OverlayPrefs.debugTouchStandby(this);
        boolean standbyTouchable = touchable || standbyEnabled;
        // Let an early wake touch try the same readiness gate used by normal gestures.
        boolean listening = touchable || standbyEnabled;
        if (touchDebugView != null) {
            if (touchDebugWindowCount() == boxes.size()) {
                for (int i = 0; i < boxes.size(); i++) {
                    TouchDebugView view = touchDebugViewAt(i);
                    view.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
                    view.setListeningEnabled(listening);
                }
                updateTouchDebugLayouts(boxes, standbyTouchable);
                return;
            }
            removeTouchDebugOverlay();
        }
        TouchDebugView.TouchTriggerListener triggerListener =
                new TouchDebugView.TouchTriggerListener() {
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
                updateUnlockEffectGesture(screenX, screenY, distance);
            }

            @Override
            public void onTouchRealigned(float screenX, float screenY) {
                if (seasonalUnlockPartnerGestureActive) {
                    return;
                }
                if (bufferedReadinessGestureActive) {
                    bufferedReadinessHasMove = true;
                    bufferedReadinessMoveX = screenX;
                    bufferedReadinessMoveY = screenY;
                    return;
                }
                if (readinessFallbackGestureActive) {
                    return;
                }
                if (unlockEffectRenderer instanceof S3Arm64RippleEffectView) {
                    ((S3Arm64RippleEffectView) unlockEffectRenderer).realignGesture(
                            screenX, screenY);
                } else if (unlockEffectRenderer instanceof AbstractTilesArm64EffectView) {
                    ((AbstractTilesArm64EffectView) unlockEffectRenderer).realignGesture(
                            screenX, screenY);
                } else if (unlockEffectRenderer instanceof GeometricMosaicArm64EffectView) {
                    ((GeometricMosaicArm64EffectView) unlockEffectRenderer).realignGesture(
                            screenX, screenY);
                }
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
        };

        touchDebugTouchable = standbyTouchable;
        for (int i = 0; i < boxes.size(); i++) {
            Rect area = boxes.get(i);
            TouchDebugView view = new TouchDebugView(rendererContext());
            view.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
            view.setListeningEnabled(listening);
            view.setTouchTriggerListener(triggerListener);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    area.width(), area.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    touchListenBoxFlags(standbyTouchable), PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = area.left;
            params.y = area.top;
            params.setTitle("LLETouchListenArea" + (i + 1));
            try {
                windowManager.addView(view, params);
            } catch (RuntimeException e) {
                Log.e(TAG, "touch listen area addView failed index=" + i, e);
                removeTouchDebugOverlay();
                return;
            }
            if (i == 0) {
                touchDebugView = view;
                touchDebugParams = params;
            } else {
                additionalTouchDebugViews.add(view);
                additionalTouchDebugParams.add(params);
            }
        }
        Log.i(TAG, "touch listen region shown areas=" + boxes.size()
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
        destroyDoodleOverlay();
        destroySeasonalUnlockPartnerOverlay();
        destroyUnlockEffectOverlay();
        clearCachedUnlockEffectBackgroundBitmap();
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

    private void unloadUnlockEffects(String reason) {
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
        destroyUnlockEffectOverlay();
        clearCachedUnlockEffectBackgroundBitmap();
        removeTouchDebugOverlay();
        Log.i(TAG, "unlock effects unloaded reason=" + reason);
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
        boolean removed = false;
        int removedType = unlockEffectRendererType;
        if (!destroyingRenderer
                && unlockEffectOverlayAttached
                && shouldKeepNativePhysicsOverlayAttachedDuringHide(removedType)) {
            if (!unlockEffectOverlayParked) {
                parkNativePhysicsRendererState(unlockEffectRenderer);
                hideUnlockEffectView(unlockEffectView);
                Log.i(TAG, "native physics overlay kept attached while hidden type="
                        + removedType);
            }
            return;
        }
        if (unlockEffectRenderer != null && unlockEffectOverlayAttached) {
            unlockEffectRenderer.resetEffect();
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
            refreshUnlockEffectReadiness("detached");
        }
        if (removed && !destroyingRenderer && isSamsungLockBgEffect(removedType)) {
            markUnlockEffectRendererStale("overlay_detached");
        }
    }

    private void destroyUnlockEffectOverlay() {
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        handler.removeCallbacks(unlockEffectReadinessChangedRunnable);
        cancelBufferedReadinessGesture("renderer_destroy", false);
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
            if (unlockEffectRenderer instanceof UnlockEffectReadiness) {
                try {
                    ((UnlockEffectReadiness) unlockEffectRenderer)
                            .setReadinessListener(null);
                } catch (Throwable t) {
                    Log.d(TAG, "readiness listener detach ignored", t);
                }
            }
            unlockEffectRenderer.destroy();
            unlockEffectRenderer = null;
            if (shouldKeepNativePhysicsOverlayAttachedDuringHide(destroyedType)) {
                scheduleNativePhysicsPostDestroyGc(destroyedType);
            }
        }
        unlockEffectView = null;
        unlockEffectRendererType = -1;
        unlockEffectOverlayAddRetryAt = 0L;
        unlockEffectOverlayParked = false;
        unlockEffectGestureActive = false;
        unlockEffectRendererNeedsRecreate = false;
        unlockEffectRendererRecreateReason = "";
        unlockEffectRendererDisplayWidth = 0;
        unlockEffectRendererDisplayHeight = 0;
        unlockEffectReadinessState = UnlockEffectReadiness.STATE_DETACHED;
        unlockEffectReadinessDetail = "detached";
    }

    private void scheduleNativePhysicsPostDestroyGc(final int effect) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (unlockEffectRenderer != null
                        || (powerManager != null && powerManager.isInteractive())) {
                    return;
                }
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
        float parkedAlpha = WARM_PARK_ALPHA;
        if (unlockEffectRenderer instanceof LensFlareEffectView) {
            // Lens Flare primes its HWUI display list with a deliberately near-transparent
            // frame (1/255 per asset). Multiplying that by WARM_PARK_ALPHA lets HWUI cull
            // the child layer before onDraw(), so keep the root compositable until the
            // renderer acknowledges FIRST_FRAME_READY. The listener immediately parks it
            // at real zero alpha after that one warm frame.
            parkedAlpha = CANVAS_WARM_PARK_ALPHA;
        }
        view.setAlpha(isUnlockEffectFirstFrameReady() ? 0f : parkedAlpha);
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
        if (!wasParked) {
            return;
        }
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
        for (TouchDebugView view : additionalTouchDebugViews) {
            try {
                windowManager.removeView(view);
            } catch (RuntimeException ignored) {
            }
        }
        additionalTouchDebugViews.clear();
        additionalTouchDebugParams.clear();
    }

    private int touchDebugWindowCount() {
        return touchDebugView == null ? 0 : 1 + additionalTouchDebugViews.size();
    }

    private TouchDebugView touchDebugViewAt(int index) {
        return index == 0 ? touchDebugView : additionalTouchDebugViews.get(index - 1);
    }

    private WindowManager.LayoutParams touchDebugParamsAt(int index) {
        return index == 0 ? touchDebugParams : additionalTouchDebugParams.get(index - 1);
    }

    private void updateTouchDebugLayouts(List<Rect> boxes, boolean touchable) {
        if (touchDebugView == null || touchDebugParams == null
                || touchDebugWindowCount() != boxes.size()) {
            return;
        }
        int flags = touchListenBoxFlags(touchable);
        for (int i = 0; i < boxes.size(); i++) {
            Rect box = boxes.get(i);
            TouchDebugView view = touchDebugViewAt(i);
            WindowManager.LayoutParams params = touchDebugParamsAt(i);
            boolean changed = params.x != box.left || params.y != box.top
                    || params.width != box.width() || params.height != box.height()
                    || params.flags != flags || touchDebugTouchable != touchable;
            if (!changed) {
                continue;
            }
            params.x = box.left;
            params.y = box.top;
            params.width = box.width();
            params.height = box.height();
            params.flags = flags;
            try {
                windowManager.updateViewLayout(view, params);
            } catch (RuntimeException e) {
                Log.e(TAG, "touch listen area update failed index=" + i, e);
                removeTouchDebugOverlay();
                return;
            }
        }
        touchDebugTouchable = touchable;
    }

    private int touchListenBoxFlags(boolean touchable) {
        int flags = TOUCH_LISTEN_BOX_BASE_FLAGS;
        if (!touchable) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        return flags;
    }

    private List<Rect> resolveTouchBoxes() {
        DisplayMetrics metrics = activeDisplayMetrics();
        int screenWidth = Math.max(dp(48), metrics.widthPixels);
        int screenHeight = Math.max(dp(48), metrics.heightPixels);
        if (!resolvedTouchBoxesDirty
                && resolvedTouchBoxesWidth == screenWidth
                && resolvedTouchBoxesHeight == screenHeight
                && activeDisplayProfile.equals(resolvedTouchBoxesProfile)) {
            return resolvedTouchBoxesCache;
        }
        int minSize = dp(48);
        resolvedTouchBoxesCache.clear();
        List<Rect> saved = OverlayPrefs.touchBoxRegions(
                rendererContext(), activeDisplayProfile);
        for (Rect source : saved) {
            int left = clamp(source.left, 0, screenWidth - minSize);
            int top = clamp(source.top, 0, screenHeight - minSize);
            int right = clamp(source.right, left + minSize, screenWidth);
            int bottom = clamp(source.bottom, top + minSize, screenHeight);
            resolvedTouchBoxesCache.add(new Rect(left, top, right, bottom));
        }
        if (resolvedTouchBoxesCache.isEmpty()) {
            resolvedTouchBoxesCache.add(new Rect(OverlayPrefs.DEFAULT_TOUCH_BOX_LEFT,
                    OverlayPrefs.DEFAULT_TOUCH_BOX_TOP,
                    Math.min(screenWidth, OverlayPrefs.DEFAULT_TOUCH_BOX_RIGHT),
                    Math.min(screenHeight, OverlayPrefs.DEFAULT_TOUCH_BOX_BOTTOM)));
        }
        resolvedTouchBoxesWidth = screenWidth;
        resolvedTouchBoxesHeight = screenHeight;
        resolvedTouchBoxesProfile = activeDisplayProfile;
        resolvedTouchBoxesDirty = false;
        return resolvedTouchBoxesCache;
    }

    private void invalidateResolvedTouchBoxes() {
        resolvedTouchBoxesDirty = true;
    }

    private Rect resolveTouchBox() {
        return touchBoxBounds(resolveTouchBoxes());
    }

    private Rect touchBoxBounds(List<Rect> boxes) {
        Rect bounds = new Rect(boxes.get(0));
        for (int i = 1; i < boxes.size(); i++) {
            bounds.union(boxes.get(i));
        }
        return bounds;
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
                && isUnlockEffectAllowedNowForActivePanel();
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
                && isDoodleAllowedNowForActivePanel();
    }

    private boolean isDoodleAllowedNowForActivePanel() {
        return OverlayPrefs.doodleAllowedNow(this)
                && OverlayPrefs.foldPanelDoodleEnabled(this, activeDisplayProfile);
    }

    private boolean isUnlockEffectEnabledForActivePanel() {
        return OverlayPrefs.unlockEffectEnabled(this)
                && OverlayPrefs.foldPanelUnlockEffectEnabled(this, activeDisplayProfile);
    }

    private boolean isUnlockEffectAllowedNowForActivePanel() {
        return isUnlockEffectEnabledForActivePanel()
                && OverlayPrefs.isImplementedEffect(OverlayPrefs.unlockEffect(this))
                && OverlayPrefs.timeWindowAllows(this,
                OverlayPrefs.UNLOCK_EFFECT_TIME_ENABLED,
                OverlayPrefs.UNLOCK_EFFECT_TIME_START,
                OverlayPrefs.UNLOCK_EFFECT_TIME_END);
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
                || isCallSurfaceActive()
                || notificationShadeVisible) {
            return;
        }
        if (isChargingDoodleModeEnabled()) {
            if (!OverlayPrefs.doodleLockSoundAllowedNow(this)) {
                return;
            }
            lastLockSoundPlayedAt = now;
            lockSoundPlayer.playSeasonalLock(OverlayPrefs.seasonMode(this));
            return;
        }
        if (isUnlockEffectEnabledForActivePanel()
                && OverlayPrefs.isImplementedEffect(OverlayPrefs.unlockEffect(this))
                && OverlayPrefs.lockscreenLockSoundAllowedNow(this)) {
            lastLockSoundPlayedAt = now;
            lockSoundPlayer.playEffectLock(OverlayPrefs.unlockEffect(this));
        }
    }

    private boolean isSeasonalUnlockPartnerModeEnabled() {
        return isChargingDoodleModeEnabled()
                && OverlayPrefs.seasonalUnlockPartnerAllowedNow(this);
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
        if (!OverlayPrefs.get(this).getBoolean(
                OverlayPrefs.EFFECT_PROFILE_SAMPLE_PENDING, false)) {
            clearActiveUnlockEffectProfile();
            return;
        }
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
                .putBoolean(OverlayPrefs.EFFECT_PROFILE_SAMPLE_PENDING, false)
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
        preloadAndAttachSelectedUnlockEffectParked("gesture_down");
        refreshUnlockEffectReadiness("gesture_down");
        if (!isUnlockEffectFirstFrameReady()) {
            return bufferUnlockEffectGestureDown(screenX, screenY, effect);
        }
        return beginReadyUnlockEffectGesture(screenX, screenY, effect, startedAt);
    }

    private boolean beginReadyUnlockEffectGesture(float screenX, float screenY,
            int effect, long startedAt) {
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

    private boolean bufferUnlockEffectGestureDown(float screenX, float screenY, int effect) {
        cancelBufferedReadinessGesture("superseded_down", false);
        bufferedReadinessGestureActive = true;
        readinessFallbackGestureActive = false;
        bufferedReadinessEffect = effect;
        bufferedReadinessDownX = screenX;
        bufferedReadinessDownY = screenY;
        bufferedReadinessHasMove = false;
        bufferedReadinessHasTerminal = false;
        bufferedReadinessTerminalCancel = false;
        handler.removeCallbacks(bufferedReadinessGestureTimeoutRunnable);
        handler.postDelayed(bufferedReadinessGestureTimeoutRunnable,
                READINESS_GESTURE_TIMEOUT_MS);
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.warmUp();
        }
        scheduleUnlockEffectWarmBurst("readiness_buffer_down");
        Log.i(TAG, "unlock effect DOWN buffered type=" + effect
                + " readiness=" + unlockEffectReadinessState
                + " detail=" + unlockEffectReadinessDetail
                + " timeoutMs=" + READINESS_GESTURE_TIMEOUT_MS);
        return true;
    }

    private void replayBufferedReadinessGesture() {
        if (!bufferedReadinessGestureActive || !isUnlockEffectFirstFrameReady()) {
            return;
        }
        int effect = bufferedReadinessEffect;
        float downX = bufferedReadinessDownX;
        float downY = bufferedReadinessDownY;
        boolean hasMove = bufferedReadinessHasMove;
        float moveX = bufferedReadinessMoveX;
        float moveY = bufferedReadinessMoveY;
        boolean hasTerminal = bufferedReadinessHasTerminal;
        boolean cancel = bufferedReadinessTerminalCancel;
        float terminalX = bufferedReadinessTerminalX;
        float terminalY = bufferedReadinessTerminalY;
        float terminalDistance = bufferedReadinessTerminalDistance;
        clearBufferedReadinessGestureState();
        if (effect != OverlayPrefs.unlockEffect(this) || !isUnlockEffectGestureReady()) {
            Log.i(TAG, "buffered unlock gesture dropped before replay type=" + effect
                    + " selected=" + OverlayPrefs.unlockEffect(this)
                    + " environmentReady=" + isUnlockEffectGestureReady());
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        if (!beginReadyUnlockEffectGesture(downX, downY, effect, startedAt)) {
            return;
        }
        if (hasMove) {
            updateUnlockEffectGesture(moveX, moveY);
        }
        if (hasTerminal) {
            if (cancel) {
                cancelUnlockEffectGesture();
            } else {
                finishUnlockEffectGesture(terminalX, terminalY, terminalDistance);
            }
        }
        Log.i(TAG, "buffered unlock gesture replayed type=" + effect
                + " move=" + hasMove + " terminal=" + hasTerminal
                + " cancel=" + cancel);
    }

    private void handleBufferedReadinessGestureTimeout() {
        if (!bufferedReadinessGestureActive) {
            return;
        }
        boolean hasTerminal = bufferedReadinessHasTerminal;
        boolean cancel = bufferedReadinessTerminalCancel;
        float terminalDistance = bufferedReadinessTerminalDistance;
        int effect = bufferedReadinessEffect;
        clearBufferedReadinessGestureState();
        if (hasTerminal) {
            if (!cancel) {
                completeReadinessFallbackGesture(terminalDistance,
                        "timeout_received_up");
            }
        } else {
            readinessFallbackGestureActive = true;
        }
        Log.w(TAG, "unlock effect readiness gesture timeout type=" + effect
                + " state=" + unlockEffectReadinessState
                + " detail=" + unlockEffectReadinessDetail
                + " terminal=" + hasTerminal + " cancel=" + cancel
                + " fallbackActive=" + readinessFallbackGestureActive);
    }

    private void completeReadinessFallbackGesture(float distance, String reason) {
        boolean unlockTriggered = distance >= dp(UNLOCK_TRIGGER_DISTANCE_DP);
        if (unlockTriggered && isUnlockEffectGestureReady()) {
            // This is based solely on the received UP and its measured distance. No synthetic
            // gesture or unlock is generated when the renderer misses its readiness deadline.
            schedulePinEntry();
        }
        Log.i(TAG, "readiness fallback gesture complete reason=" + reason
                + " distance=" + Math.round(distance)
                + " unlockTriggered=" + unlockTriggered
                + " environmentReady=" + isUnlockEffectGestureReady());
    }

    private void cancelBufferedReadinessGesture(String reason,
            boolean preserveReceivedUnlock) {
        if (!bufferedReadinessGestureActive && !readinessFallbackGestureActive) {
            return;
        }
        boolean receivedUp = bufferedReadinessGestureActive
                && bufferedReadinessHasTerminal && !bufferedReadinessTerminalCancel;
        float receivedDistance = bufferedReadinessTerminalDistance;
        clearBufferedReadinessGestureState();
        readinessFallbackGestureActive = false;
        if (preserveReceivedUnlock && receivedUp) {
            completeReadinessFallbackGesture(receivedDistance,
                    "cancel_preserved:" + reason);
        }
        Log.i(TAG, "buffered readiness gesture cancelled reason=" + reason
                + " receivedUp=" + receivedUp
                + " preserve=" + preserveReceivedUnlock);
    }

    private void clearBufferedReadinessGestureState() {
        handler.removeCallbacks(bufferedReadinessGestureTimeoutRunnable);
        bufferedReadinessGestureActive = false;
        bufferedReadinessEffect = -1;
        bufferedReadinessHasMove = false;
        bufferedReadinessHasTerminal = false;
        bufferedReadinessTerminalCancel = false;
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
        updateUnlockEffectGesture(screenX, screenY, 0f);
    }

    private void updateUnlockEffectGesture(float screenX, float screenY, float distance) {
        if (bufferedReadinessGestureActive) {
            bufferedReadinessHasMove = true;
            bufferedReadinessMoveX = screenX;
            bufferedReadinessMoveY = screenY;
            bufferedReadinessMoveDistance = distance;
            return;
        }
        if (readinessFallbackGestureActive) {
            return;
        }
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.updateGesture(screenX, screenY);
        }
    }

    private void finishUnlockEffectGesture(float screenX, float screenY, float distance) {
        if (bufferedReadinessGestureActive) {
            bufferedReadinessHasTerminal = true;
            bufferedReadinessTerminalCancel = false;
            bufferedReadinessTerminalX = screenX;
            bufferedReadinessTerminalY = screenY;
            bufferedReadinessTerminalDistance = distance;
            return;
        }
        if (readinessFallbackGestureActive) {
            readinessFallbackGestureActive = false;
            completeReadinessFallbackGesture(distance, "up_after_timeout");
            return;
        }
        boolean unlockTriggered = distance >= dp(UNLOCK_TRIGGER_DISTANCE_DP);
        if (unlockEffectRenderer instanceof S3Arm64RippleEffectView) {
            ((S3Arm64RippleEffectView) unlockEffectRenderer).finishGestureAt(
                    screenX, screenY, unlockTriggered);
        } else if (unlockEffectRenderer != null) {
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
        if (bufferedReadinessGestureActive) {
            bufferedReadinessHasTerminal = true;
            bufferedReadinessTerminalCancel = true;
            return;
        }
        if (readinessFallbackGestureActive) {
            readinessFallbackGestureActive = false;
            Log.i(TAG, "readiness fallback gesture cancelled by received CANCEL");
            return;
        }
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
        if (OverlayPrefs.isColourDropletEffect(effect)) {
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
        if (OverlayPrefs.isColourDropletEffect(effect)) {
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

        DisplayMetrics metrics = activeDisplayMetrics();
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
                blockedSurfaces |= detectBlockedSurfaceNodes(root, 0);
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

    private int detectBlockedSurfaceNodes(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > PIN_ENTRY_NODE_SCAN_DEPTH) {
            return 0;
        }
        int blockedSurfaces = 0;
        if (nodeMatchesPinEntry(node)) {
            blockedSurfaces |= BLOCKED_SURFACE_PIN_ENTRY;
        }
        if (nodeMatchesNotificationShade(node)) {
            blockedSurfaces |= BLOCKED_SURFACE_NOTIFICATION_SHADE;
        }
        if (blockedSurfaces == (BLOCKED_SURFACE_PIN_ENTRY
                | BLOCKED_SURFACE_NOTIFICATION_SHADE)) {
            return blockedSurfaces;
        }
        int childCount = Math.min(node.getChildCount(), PIN_ENTRY_NODE_SCAN_CHILD_LIMIT);
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                blockedSurfaces |= detectBlockedSurfaceNodes(child, depth + 1);
                if (blockedSurfaces == (BLOCKED_SURFACE_PIN_ENTRY
                        | BLOCKED_SURFACE_NOTIFICATION_SHADE)) {
                    return blockedSurfaces;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "blocked surface child scan failed", e);
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return blockedSurfaces;
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
            unloadUnlockEffects("doodle:charging");
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
        return Math.round(value * activeDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
