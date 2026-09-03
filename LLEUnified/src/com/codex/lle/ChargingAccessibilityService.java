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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.util.SparseArray;
import android.view.Display;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ChargingAccessibilityService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile ChargingAccessibilityService activeService;
    private static final String TAG = "ChargingA11y";
    private static final String ACTION_DEBUG_UNLOCK_EFFECT_PROFILE =
            "com.codex.lle.DEBUG_UNLOCK_EFFECT_PROFILE";
    private static final String ACTION_DEBUG_UNLOCK_EFFECT_DEMO_GESTURE =
            "com.codex.lle.DEBUG_UNLOCK_EFFECT_DEMO_GESTURE";
    private static final String ACTION_DEBUG_UNLOCK_EFFECT_BENCHMARK =
            "com.codex.lle.DEBUG_UNLOCK_EFFECT_BENCHMARK";
    private static final String ACTION_DEBUG_SET_LENS_FLARE_GLES_RENDERER =
            "com.codex.lle.DEBUG_SET_LENS_FLARE_GLES_RENDERER";
    private static final String ACTION_DEBUG_SET_LENS_FLARE_MODE =
            "com.codex.lle.DEBUG_SET_LENS_FLARE_MODE";
    private static final String ACTION_DEBUG_SET_DOODLE_SEASON =
            "com.codex.lle.DEBUG_SET_DOODLE_SEASON";
    private static final String ACTION_DEBUG_CAPTURE_GEOMETRIC_HINT =
            "com.codex.lle.DEBUG_CAPTURE_GEOMETRIC_HINT";
    private static final String ACTION_DEBUG_CAPTURE_ABSTRACT_TILES =
            "com.codex.lle.DEBUG_CAPTURE_ABSTRACT_TILES";
    private static final String TESTER_UNDERLAY_PROBE_FILE =
            "tester_lg_underlay_probe.argb8888";
    private static final String TESTER_UNDERLAY_PROBE_STATUS =
            "tester_lg_underlay_probe_status";
    static final String ACTION_EFFECT_BACKGROUND_REFRESH =
            "com.codex.lle.EFFECT_BACKGROUND_REFRESH";
    private static final int UNLOCK_TRIGGER_DISTANCE_DP = 120;
    private static final long PIN_ENTRY_DELAY_LENS_FLARE_MS = 400L;
    private static final long PIN_ENTRY_DELAY_POPPING_COLOURS_MS = 300L;
    private static final long PIN_ENTRY_DELAY_WATERCOLOUR_MS = 250L;
    private static final long PIN_ENTRY_DELAY_BRILLIANT_RING_MS = 250L;
    // Empirical release-to-handoff timings. The real window alpha is changed only
    // after these selected effect tails have had time to present.
    private static final long PIN_ENTRY_DELAY_POPPING_COLOURS_TAIL_MS = 325L;
    private static final long PIN_ENTRY_DELAY_BRILLIANT_CUT_TAIL_MS = 415L;
    private static final long PIN_ENTRY_DELAY_MASS_TENSION_TAIL_MS = 510L;
    // Geometric Mosaic must dispatch when its 400 ms expansion reaches full coverage. Its fade is
    // deliberately not part of the handoff delay: the window is neutralized at full coverage.
    private static final long PIN_ENTRY_DELAY_GEOMETRIC_MOSAIC_TAIL_MS =
            GeometricMosaicGlesPipeline.unlockHandoffDelayMs();
    private static final long PIN_ENTRY_DELAY_WATERCOLOUR_TAIL_MS = 800L;
    private static final long PIN_ENTRY_DELAY_ABSTRACT_TILES_TAIL_MS = 925L;
    private static final long PIN_ENTRY_DELAY_BRILLIANT_RING_TAIL_MS = 930L;
    private static final long PIN_ENTRY_DELAY_LENS_FLARE_TAIL_MS = 600L;
    private static final long PIN_ENTRY_DELAY_S3_NONE_TAIL_MS = 375L;
    // Pixelate reaches full coverage at 400 ms. Start the SystemUI handoff there while
    // its scene independently keeps the captured underlay alive through 1000 ms.
    private static final long PIN_ENTRY_DELAY_LG_G2_PIXELATE_TAIL_MS =
            LgPixelateScene.UNLOCK_MS;
    private static final long PIN_ENTRY_DELAY_LG_G2_PARTICLE_TAIL_MS = 440L;
    private static final long PIN_ENTRY_DELAY_LG_G2_CRYSTAL_TAIL_MS = 500L;
    private static final long PIN_ENTRY_DELAY_LG_G1_WHITE_HOLE_TAIL_MS =
            LgWhiteHoleEffectView.COMPLETE_MS;
    private static final long PIN_ENTRY_DELAY_LG_SODA_TAIL_MS = LgSodaEffectView.COMPLETE_MS;
    private static final long PIN_ENTRY_DELAY_LG_G1_DEWDROP_TAIL_MS =
            LgDewdropEffectView.COMPLETE_MS;
    // The shared swipe stage adds 60 ms, matching the donor's 500 ms unlock clock.
    private static final long PIN_ENTRY_DELAY_LG_G2_LIGHT_PARTICLE_TAIL_MS = 440L;
    // Vector opens for 400 ms, then its renderer holds Last Screen for another 550 ms.
    private static final long PIN_ENTRY_DELAY_LG_G2_VECTOR_TAIL_MS = LgVectorScene.UNLOCK_MS;
    private static final long PIN_ENTRY_DELAY_XPERIA_Z1_BLINDS_TAIL_MS = 340L;
    private static final long PIN_ENTRY_DELAY_REVOLVING_GLASS_TAIL_MS = 660L;
    // Samsung exposes a 400 ms unlock delay. The shared dispatch stage below adds 60 ms.
    private static final long PIN_ENTRY_DELAY_COLOUR_DROPLET_MS = 340L;
    // Samsung exposes a 400 ms unlock delay. The shared dispatch stage below adds 60 ms.
    private static final long PIN_ENTRY_DELAY_SPARKLING_BUBBLES_MS = 875L;
    private static final long PIN_ENTRY_DELAY_S6_WATER_DROPLET_MS = 340L;
    // Samsung exposes 500 ms; LLE's shared dispatch stage below adds 60 ms.
    private static final long PIN_ENTRY_DELAY_MASS_TENSION_MS = 440L;
    private static final long PIN_ENTRY_DELAY_SEASONAL_UNLOCK_MS = 300L;
    private static final long SEASONAL_UNLOCK_SURFACE_HOLD_MS = 900L;
    private static final float WARM_PARK_ALPHA = 0.01f;
    private static final float CANVAS_WARM_PARK_ALPHA = 1f;
    private static final long PIN_ENTRY_SWIPE_START_DELAY_MS = 60L;
    private static final long PIN_ENTRY_SWIPE_START_DELAY_CONSERVATIVE_MS = 140L;
    private static final long PIN_ENTRY_HANDOFF_VERIFY_DELAY_MS = 360L;
    private static final long PIN_ENTRY_HANDOFF_CANCEL_VERIFY_DELAY_MS = 180L;
    private static final long PIN_ENTRY_HANDOFF_SCAN_RECHECK_MS = 120L;
    private static final long PIN_ENTRY_HANDOFF_SCAN_WAIT_MAX_MS = 720L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_DEFAULT_MS = 900L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_LOCKBG_MS = 300L;
    // Pin entry opens after 340 ms and its shared dispatch starts 60 ms later.
    // Hide the droplet surface at that same 400 ms stock wrapper boundary so
    // native tail/release motion never becomes visible after fullscreen coverage.
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_COLOUR_DROPLET_MS = 60L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_SPARKLING_BUBBLES_MS = 650L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_S6_WATER_DROPLET_MS = 340L;
    private static final long LOCKBG_IDLE_HIDE_DELAY_MS = 700L;
    private static final long PIN_ENTRY_SWIPE_DURATION_MS = 260L;
    private static final long BOOT_SAFETY_WINDOW_MS = 120_000L;
    private static final long UNLOCK_AFFORDANCE_DELAY_MS = 500L;
    private static final long ACTIVE_EFFECT_PROFILE_SAMPLE_DELAY_MS = 220L;
    private static final long TESTER_UNDERLAY_PROBE_ARM_WINDOW_MS = 30_000L;
    private static final long TESTER_UNDERLAY_PROBE_POLL_MS = 250L;
    private static final long DEBUG_LOOP_STEP_DELAY_MS = 120L;
    private static final long DEBUG_LOOP_RESTART_DELAY_MS = 620L;
    private static final long SCREEN_ON_REFRESH_FAST_MS = 35L;
    private static final long SCREEN_ON_REFRESH_SETTLE_MS = 140L;
    private static final long LOCKSCREEN_SESSION_FAST_WINDOW_MS = 1200L;
    private static final long LOCKSCREEN_SESSION_FAST_POLL_MS = 10L;
    private static final long LOCKSCREEN_SESSION_STABLE_POLL_MS = 25L;
    private static final long LOCKSCREEN_SESSION_INITIAL_CONTENT_DELAY_MS = 20L;
    private static final long LOCKSCREEN_SESSION_CONTENT_POLL_MS = 60L;
    private static final long LOCKSCREEN_SESSION_STABLE_CONTENT_POLL_MS = 80L;
    private static final long WINDOW_CONTENT_EVENT_MIN_INTERVAL_MS = 32L;
    private static final long DISPLAY_CANDIDATE_WAKE_COALESCE_MS = 32L;
    private static final long LOCKSCREEN_EXIT_FAST_POLL_MS = 20L;
    private static final long LOCKSCREEN_EXIT_FOLLOWUP_MS = 1600L;
    private static final long LOCKSCREEN_EXIT_FOLLOWUP_ARM_WINDOW_MS = 700L;
    private static final long LOCK_SOUND_THROTTLE_MS = 1200L;
    private static final long LOCK_SOUND_UNLOCK_CONFIRM_MS = 600L;
    private static final long RANDOM_UNLOCK_NEXT_PRELOAD_DELAY_MS = 1300L;
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
    private static final long PIN_ENTRY_PARTIAL_CLEAR_STABLE_MS = 800L;
    private static final long NOTIFICATION_SHADE_CLEAR_STABLE_MS = 250L;
    private static final long BLOCKED_SURFACE_SCAN_MIN_INTERVAL_MS = 60L;
    private static final long NOTIFICATION_SHADE_SCAN_MIN_INTERVAL_MS = 120L;
    private static final long NOTIFICATION_SHADE_STRUCTURAL_LOG_INTERVAL_MS = 750L;
    private static final long BLOCKED_SURFACE_SCAN_DIAGNOSTIC_INTERVAL_MS = 500L;
    private static final long NOTIFICATION_SHADE_OEM_DIAGNOSTIC_INTERVAL_MS = 5000L;
    private static final long NOTIFICATION_SHADE_PROBE_RECHECK_MS = 500L;
    private static final long NOTIFICATION_SHADE_SCREEN_OFF_GUARD_MS = 1000L;
    private static final int NOTIFICATION_SHADE_OEM_DIAGNOSTIC_MAX_NODES = 48;
    private static final int NOTIFICATION_SHADE_OEM_DIAGNOSTIC_MAX_CHARS = 2400;
    private static final int NOTIFICATION_SHADE_OEM_LOG_CHUNK_CHARS = 1800;
    private static final int NOTIFICATION_SHADE_CLEAR_SUCCESS_COUNT = 2;
    private static final int BLOCKED_SURFACE_SCAN_MAX_WINDOWS_PER_DISPLAY = 4;
    private static final int BLOCKED_SURFACE_SCAN_MAX_NODES = 320;
    private static final long BLOCKED_SURFACE_SCAN_MAX_ELAPSED_MS = 12L;
    private static final long NOTIFICATION_SHADE_EXTENDED_SCAN_MAX_ELAPSED_MS = 160L;
    private static final long GLOBAL_ACTIONS_EVENT_CLEAR_GRACE_MS = 250L;
    private static final long GLOBAL_ACTIONS_FALLBACK_CLEAR_MS = 9000L;
    private static final long RUNTIME_BLOCK_WINDOW_RECHECK_MS = 450L;
    private static final long RUNTIME_BLOCK_WINDOW_CLEAR_GRACE_MS = 700L;
    private static final long TOUCH_BOX_SCREENSHOT_DELAY_MS = 2000L;
    private static final long TOUCH_BOX_SCREENSHOT_OVERLAY_CLEAR_MS = 180L;
    private static final long UNLOCK_EFFECT_SCREENSHOT_RETRY_MS = 90L;
    private static final long UNLOCK_EFFECT_SCREENSHOT_WAIT_LOG_INTERVAL_MS = 1000L;
    private static final int UNLOCK_EFFECT_SCREENSHOT_MAX_ATTEMPTS = 2;
    private static final float PROFILE_SCREENSHOT_MIN_TARGET_SCALE = 0.90f;
    // The persisted colormap is shared by every screenshot-backed effect, so its
    // capture quality must not depend on whichever renderer happens to be selected.
    private static final long SHARED_COLORMAP_CAPTURE_MIN_SCREEN_ON_MS = 1400L;
    private static final long EFFECT_BACKGROUND_WAKE_TIMEOUT_MS = 7000L;
    private static final long EFFECT_BACKGROUND_WAKE_LOCK_DELAY_MS = 260L;
    private static final int TOUCH_LISTEN_BOX_BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    private static final int PIN_ENTRY_NODE_SCAN_DEPTH = 10;
    private static final int PIN_ENTRY_NODE_SCAN_CHILD_LIMIT = 80;
    private static final int BLOCKED_SURFACE_PIN_ENTRY = 1;
    private static final int BLOCKED_SURFACE_NOTIFICATION_SHADE = 1 << 1;
    private static final int BLOCKED_SURFACE_GLOBAL_ACTIONS = 1 << 2;
    private static final int BLOCKED_SURFACE_SCAN_UNKNOWN = 0;
    private static final int BLOCKED_SURFACE_SCAN_SUCCESS = 1;
    private static final int BLOCKED_SURFACE_SCAN_PARTIAL = 2;
    private static final String ACTION_CLOSE_SYSTEM_DIALOGS =
            "android.intent.action.CLOSE_SYSTEM_DIALOGS";
    private static final String SYSTEM_DIALOG_REASON_KEY = "reason";
    private static final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";
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
    private static final String[] RUNTIME_SURFACE_BLACKLIST_PACKAGES = {
            // Samsung lockscreen-adjacent surfaces.
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.sidegesturepad",
            "com.sec.android.app.camera",
            "com.sec.android.app.clockpackage",
            "com.samsung.android.app.notes",
            "com.samsung.android.app.reminder",
            "com.samsung.android.calendar",
            "com.samsung.android.emergency",
            "com.sec.android.emergencylauncher",
            "com.google.android.googlequicksearchbox",

            // Messaging and VoIP surfaces that can present a caller over keyguard.
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.thunderdog.challegram",
            "com.facebook.orca",
            "com.facebook.katana",
            "com.instagram.android",
            "com.discord",
            "com.truecaller",
            "org.thoughtcrime.securesms",
            "com.viber.voip",
            "com.skype.raider",
            "com.microsoft.teams",
            "com.google.android.apps.tachyon",
            "com.google.android.apps.meetings",
            "us.zoom.videomeetings",
            "jp.naver.line.android",
            "com.tencent.mm",
            "com.snapchat.android",

            // Navigation surfaces that can wake or draw over the lockscreen.
            "com.waze",
            "com.google.android.apps.maps",
            "com.sygic.aura",
            "com.tomtom.gplay.navapp",
            "com.here.app.maps"
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
            "expanded_qs_scroll_view",
            "brightness_slider",
            "brightness_bar_container",
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

    private static final class BlockedSurfaceScanResult {
        int surfaces;
        int quality = BLOCKED_SURFACE_SCAN_UNKNOWN;
        int windowsVisited;
        int systemUiRootsScanned;
        int nodesVisited;
        boolean budgetExhausted;
        boolean deepScanPerformed;
        String shadeMatch;
        String windowSignature;
        String nodeSignature;
    }

    private static final class BlockedSurfaceNodeScanBudget {
        long deadlineAt;
        int remainingNodes = BLOCKED_SURFACE_SCAN_MAX_NODES;
        int nodesVisited;
        boolean exhausted;
        String shadeMatch;
        boolean visibleKeyguardRootSeen;
        boolean notificationShadeCandidateSeen;
        boolean definitiveNotificationShadeStructureSeen;
        String notificationShadeCandidateMatch;
        final LinkedHashSet<String> diagnosticNodes = new LinkedHashSet<String>();
        int diagnosticChars;
        final long maxElapsedMs;

        BlockedSurfaceNodeScanBudget(long maxElapsedMs) {
            this.maxElapsedMs = maxElapsedMs;
        }

        boolean tryVisit() {
            long now = SystemClock.uptimeMillis();
            if (deadlineAt <= 0L) {
                deadlineAt = now + maxElapsedMs;
            }
            if (remainingNodes <= 0 || now > deadlineAt) {
                exhausted = true;
                return false;
            }
            remainingNodes--;
            nodesVisited++;
            return true;
        }

        void recordDiagnosticNode(int depth, AccessibilityNodeInfo node) {
            if (node == null
                    || diagnosticNodes.size() >= NOTIFICATION_SHADE_OEM_DIAGNOSTIC_MAX_NODES
                    || diagnosticChars >= NOTIFICATION_SHADE_OEM_DIAGNOSTIC_MAX_CHARS
                    || !node.isVisibleToUser()) {
                return;
            }
            CharSequence viewId = node.getViewIdResourceName();
            CharSequence className = node.getClassName();
            if (viewId == null && className == null) {
                return;
            }
            String entry = "d" + depth
                    + ":id=" + (viewId == null ? "-" : viewId)
                    + ",class=" + (className == null ? "-" : className);
            if (diagnosticChars + entry.length()
                    > NOTIFICATION_SHADE_OEM_DIAGNOSTIC_MAX_CHARS) {
                return;
            }
            if (diagnosticNodes.add(entry)) {
                diagnosticChars += entry.length();
            }
        }

        String diagnosticSignature() {
            if (diagnosticNodes.isEmpty()) {
                return "<none>";
            }
            StringBuilder signature = new StringBuilder(diagnosticChars + 32);
            for (String entry : diagnosticNodes) {
                if (signature.length() > 0) {
                    signature.append(" | ");
                }
                signature.append(entry);
            }
            return signature.toString();
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService blockedSurfaceScanExecutor =
            Executors.newSingleThreadExecutor();
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
    private final Runnable pinEntryHandoffVerifyRunnable = new Runnable() {
        @Override
        public void run() {
            verifyPinEntryHandoff();
        }
    };
    private final Runnable pinEntryEffectCleanupRunnable = new Runnable() {
        @Override
        public void run() {
            cleanupUnlockEffectAfterPinDelay();
        }
    };
    private final Runnable bootSafetyReleaseRunnable = new Runnable() {
        @Override
        public void run() {
            startRuntimeAfterBootSafety("timeout");
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
            if ((unlockEffectRendererType == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                    || unlockEffectRendererType == OverlayPrefs.EFFECT_N4_INK_IN_WATER)
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
            } else if (unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_RING
                    && unlockEffectRenderer instanceof BrilliantRingEffectView) {
                if (!((BrilliantRingEffectView) unlockEffectRenderer).isReady()) {
                    fallBackFromFailedBrilliantRingRenderer("async_gl_init_or_render");
                    return;
                }
            } else if (unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_CUT
                    && unlockEffectRenderer instanceof BrilliantCutEffectView) {
                if (!((BrilliantCutEffectView) unlockEffectRenderer).isReady()) {
                    fallBackFromFailedBrilliantCutRenderer("async_gl_init_or_render");
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
    private final Runnable runtimeBlockWindowValidationRunnable = new Runnable() {
        @Override
        public void run() {
            validateActiveRuntimeBlockWindow();
        }
    };
    private final Runnable notificationShadeProbeRecheckRunnable = new Runnable() {
        @Override
        public void run() {
            recheckUnconfirmedNotificationShadeProbe();
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
    private final Runnable unlockEffectReadinessFailureRunnable = new Runnable() {
        @Override
        public void run() {
            handleUnlockEffectReadinessFailure();
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
    private final Runnable randomUnlockAdvanceRunnable = new Runnable() {
        @Override
        public void run() {
            randomUnlockAdvancePending = false;
            String reason = randomUnlockAdvanceReason;
            randomUnlockAdvanceReason = "";
            advanceAndPreloadRandomUnlockEffect(
                    reason.isEmpty() ? "confirmed_unlock" : reason);
        }
    };
    private final Runnable randomUnlockPrefsRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshRandomUnlockEffectAfterPreferenceChange();
        }
    };
    private final Runnable testerUnderlayProbeRunnable = new Runnable() {
        @Override
        public void run() {
            runTesterUnderlayProbe();
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
    private boolean bootSafetyHolding;
    private boolean runtimeStartedAfterBootSafety;
    private boolean resolvedTouchBoxesDirty = true;
    private UnlockEffectRenderer unlockEffectRenderer;
    private UnlockEffectRenderer unlockAffordanceDeliveredRenderer;
    private View unlockEffectView;
    private WindowManager.LayoutParams unlockEffectWindowParams;
    private SeasonalUnlockEffectView seasonalUnlockPartnerRenderer;
    private View seasonalUnlockPartnerView;
    private int unlockEffectRendererType = -1;
    private boolean unlockEffectOverlayAttached;
    private boolean unlockEffectOverlayParked;
    private boolean unlockEffectWindowNeutralizedForHandoff;
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
    private final Set<String> runtimeSurfaceBlacklistPackages = new HashSet<String>();
    private String lastWindowPackage;
    private boolean charging;
    private int batteryPercent;
    private boolean pinEntryPending;
    private boolean pinEntryRequested;
    private boolean pinEntrySurfaceSeen;
    private boolean pinEntrySurfaceVisible;
    private boolean notificationShadeVisible;
    private boolean notificationShadeSuspected;
    private boolean notificationShadeProbePending;
    private boolean notificationShadeOemPositiveLoggedForCurrentVisibility;
    private boolean globalActionsVisible;
    private boolean unlockTouchCachedWhileScreenOff;
    private boolean unlockAffordancePending;
    private boolean unlockAffordanceShownThisWake;
    private boolean unlockAffordanceDispatchQueued;
    private boolean lastInteractive;
    private boolean interactiveSessionWasUnlocked;
    private boolean randomUnlockAdvancePending;
    private String randomUnlockAdvanceReason = "";
    private boolean unlockFxVisible;
    private boolean unlockEffectGestureActive;
    private boolean lockCycleSafetyBypassActive;
    private boolean bufferedReadinessGestureActive;
    private boolean readinessFallbackGestureActive;
    private boolean bufferedReadinessHasMove;
    private boolean bufferedReadinessHasTerminal;
    private boolean bufferedReadinessTerminalCancel;
    private boolean seasonalUnlockPartnerGestureActive;
    private boolean suppressUnlockFxAfterDoodleDisconnect;
    private boolean suppressUnlockEffectPreferenceCallback;
    private boolean lockscreenSessionPolling;
    private boolean blockedSurfaceScanInFlight;
    private String activeRuntimeBlockPackage;
    private int activeRuntimeBlockWindowId = -1;
    private long activeRuntimeBlockLastSeenAt;
    private long lockscreenSessionPollingStartedAt;
    private long nextContentAwarePollAt;
    private long lastWindowContentVisibilityAt;
    private long lastDisplayCandidateWakeRefreshAt;
    private long lastActiveDisplayResolveAt;
    private long pinEntryLastSeenAt;
    private long notificationShadeLastSeenAt;
    private long notificationShadeLastClearScanAt;
    private long notificationShadeSuspectedAt;
    private boolean notificationShadeNeedsExtendedScan;
    private long lastNotificationShadeStructuralLogAt;
    private long lastBlockedSurfaceScanDiagnosticAt;
    private long lastBlockedSurfaceScanRequestedAt;
    private long lastNotificationShadeOemDiagnosticAt;
    private long lastNotificationShadeDiagnosticCapturedAt;
    private int lastNotificationShadeDiagnosticQuality;
    private int lastNotificationShadeDiagnosticWindows;
    private int lastNotificationShadeDiagnosticRoots;
    private int lastNotificationShadeDiagnosticNodes;
    private boolean lastNotificationShadeDiagnosticMatched;
    private boolean lastNotificationShadeDiagnosticExhausted;
    private String lastNotificationShadeDiagnosticReason = "<none>";
    private String lastNotificationShadeWindowSignature = "<none>";
    private String lastNotificationShadeNodeSignature = "<none>";
    private String lastConfirmedNotificationShadeWindowSignature = "<none>";
    private String lastConfirmedNotificationShadeNodeSignature = "<none>";
    private long globalActionsShownAt;
    private long lastScreenOnAt;
    private long lastScreenOffAt;
    private boolean screenOffTransitionPending;
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
    private long pinEntryHandoffLastObservedAt;
    private long pinEntryHandoffAttemptDispatchedAt;
    private long pinEntryHandoffLastSafeScanAt;
    private long pinEntryHandoffPreparedAt;
    private long pinEntryHandoffSwipeQueuedAt;
    private long pinEntryHandoffTouchRemovalElapsedMs = -1L;
    private long pinEntryHandoffWindowAlphaElapsedMs = -1L;
    private long testerUnderlayProbeDeadlineAt;
    private boolean testerUnderlayProbeSawLockedScreen;
    private int testerUnderlayProbeLastWindowCount = -1;
    private int testerUnderlayProbeLastCandidateCount = -1;
    private int pinEntryHandoffTouchWindowsBefore = -1;
    private int pinEntryHandoffTouchWindowsAfter = -1;
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
    private boolean lgPreLockUnderlayCaptureInFlight;
    private int lgPreLockUnderlayCaptureGeneration;
    private boolean colorScreenshotAttemptedThisSession;
    private boolean unlockEffectBackgroundCaptureSucceededThisSession;
    private boolean skipCachedEffectBackgroundLoad;
    private boolean unlockEffectBenchmarkRunning;
    private boolean touchBoxScreenshotScheduled;
    private boolean touchBoxScreenshotInFlight;
    private boolean touchBoxScreenshotCallbackPending;
    private volatile boolean serviceAlive;
    private volatile int serviceLifecycleGeneration;
    private boolean unlockEffectReadinessFallbackScheduled;
    private int touchBoxScreenshotInFlightRequestId;
    private int[] unlockEffectBenchmarkEffects;
    private int unlockEffectBenchmarkIndex;
    private int unlockEffectBenchmarkOriginalEffect;
    private int candidateWakeGeneration;
    private int unlockAffordanceDispatchGeneration;
    private int lockscreenSessionGeneration;
    private int blockedSurfaceScanRequestGeneration;
    private int blockedSurfaceScanInFlightRequestId;
    private int notificationShadeClearSuccessCount;
    private int inputSafetyGeneration;
    private int testerUnderlayProbeGeneration;
    private int scheduledPinEntrySafetyGeneration;
    private int queuedPinSwipeSafetyGeneration;
    private int pinEntryHandoffGeneration;
    private int pinEntryHandoffSafetyGeneration;
    private int pinEntryHandoffAttempt;
    private int pinEntryHandoffVerifyGeneration;
    private int pinEntryHandoffVerifyAttempt;
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
    private String pinEntryHandoffLastCallback = "none";
    private String pinEntryHandoffLastTerminal = "none";
    private String pinEntryHandoffLastOutcome = "none";
    private String pinEntryHandoffTouchRemovalMode = "none";
    private String pinEntryHandoffTouchRemovalResult = "none";
    private String pinEntryHandoffWindowAlphaResult = "none";
    private boolean pinEntryHandoffActive;
    private boolean pinEntryHandoffFailOpen;
    private boolean pinEntryHandoffLastInteractive;
    private boolean pinEntryHandoffLastKeyguardLocked;
    private boolean pinEntryHandoffLastDeviceLocked;
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

    private final Runnable globalActionsFallbackClearRunnable = new Runnable() {
        @Override
        public void run() {
            if (!globalActionsVisible) {
                return;
            }
            globalActionsVisible = false;
            globalActionsShownAt = 0L;
            Log.i(TAG, "global actions suppression cleared reason=fallback_timeout");
            evaluateVisibility("global_actions:fallback_timeout", false);
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (holdRuntimeForBootSafety("broadcast")) {
                return;
            }
            String action = intent == null ? "null" : intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                updateChargingState(intent);
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)
                    || Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                refreshChargingState();
                scheduleCandidateWakeRefreshes("broadcast:" + action);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                advancePendingRandomUnlockEffectForScreenOff();
                if (!captureLgPreLockUnderlayIfNeeded()) {
                    captureTesterPreLockFrame();
                }
                cancelUnlockAffordanceDispatch(false, "screen_off");
                clearGlobalActionsSuppression("screen_off", false);
                clearActiveRuntimeBlock("screen_off");
                playLockSoundForScreenOff();
                handler.removeCallbacks(timeWindowRefreshRunnable);
                interactiveSessionWasUnlocked = false;
                lastInteractive = false;
                lastScreenOffAt = SystemClock.uptimeMillis();
                screenOffTransitionPending = true;
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
                // PowerManager can still report interactive=true for a short time after this
                // broadcast. Falling through to evaluateVisibility() would misclassify that
                // stale state as a new lockscreen wake and reopen shade probing mid-transition.
                return;
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                clearLockCycleSafetyBypass("user_present");
                screenOffTransitionPending = false;
                cancelUnlockAffordanceDispatch(false, "user_present");
                clearGlobalActionsSuppression("user_present", false);
                clearActiveRuntimeBlock("user_present");
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
                scheduleRandomUnlockAdvance("user_present");
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                screenOffTransitionPending = false;
                clearGlobalActionsSuppression("screen_on", false);
                boolean duplicateScreenOn = lastInteractive && lastScreenOnAt > 0L;
                lastInteractive = true;
                if (!duplicateScreenOn) {
                    clearLockCycleSafetyBypass("new_screen_on");
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
                cancelUnlockAffordanceDispatch(false, "screen_on_rearm");
                Log.i(TAG, "screen on broadcast cached=" + unlockTouchCachedWhileScreenOff
                        + " interactive=" + (powerManager == null || powerManager.isInteractive())
                        + " locked=" + isLockscreenLocked(false)
                        + " sinceScreenOffMs=" + elapsedSinceScreenOff());
                if (BuildFlavor.TESTER
                        && testerUnderlayProbeDeadlineAt > SystemClock.uptimeMillis()) {
                    handler.removeCallbacks(testerUnderlayProbeRunnable);
                    handler.post(testerUnderlayProbeRunnable);
                }
                unlockAffordancePending = true;
                boolean backgroundCaptureOwnsWake =
                        shouldSuppressWakeSurfacesForBackgroundCapture();
                if (backgroundCaptureOwnsWake) {
                    detachRuntimeSurfacesForBackgroundCapture("screen_on");
                } else if (unlockTouchCachedWhileScreenOff) {
                    restoreUnlockEffectOverlayAfterScreenOff();
                    syncTouchDebugOverlay(true, true);
                }
                evaluateVisibility("broadcast:" + action + ":fast", false);
                refreshTouchDebugInputAfterScreenOn();
                unlockTouchCachedWhileScreenOff = false;
                scheduleCandidateWakeRefreshes("broadcast:" + action);
                startLockscreenSessionPolling();
                return;
            } else if (ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String closeReason = intent == null
                        ? null : intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                Log.i(TAG, "close system dialogs reason=" + closeReason);
                if (SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS.equals(closeReason)) {
                    showGlobalActionsSuppression();
                    return;
                }
                if (globalActionsVisible) {
                    clearGlobalActionsSuppression(
                            "close_system_dialogs:" + closeReason, true);
                    return;
                }
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
            if (holdRuntimeForBootSafety("debug_broadcast")) {
                return;
            }
            if (ACTION_DEBUG_UNLOCK_EFFECT_PROFILE.equals(intent.getAction())) {
                profileDebugUnlockEffect(intent.getIntExtra("effect",
                        OverlayPrefs.unlockEffect(ChargingAccessibilityService.this)));
            } else if (ACTION_DEBUG_UNLOCK_EFFECT_DEMO_GESTURE.equals(intent.getAction())) {
                runDebugUnlockEffectDemoGesture();
            } else if (ACTION_DEBUG_UNLOCK_EFFECT_BENCHMARK.equals(intent.getAction())) {
                startUnlockEffectBenchmark();
            } else if (ACTION_DEBUG_SET_LENS_FLARE_GLES_RENDERER.equals(intent.getAction())) {
                boolean enabled = intent.getBooleanExtra("enabled", false);
                OverlayPrefs.get(ChargingAccessibilityService.this).edit()
                        .putBoolean(OverlayPrefs.LENS_FLARE_GLES_RENDERER, false)
                        .apply();
                Log.w(TAG, "ignored retired Lens Flare GLES request enabled=" + enabled
                        + "; Canvas/HWUI is mandatory");
            } else if (ACTION_DEBUG_SET_LENS_FLARE_MODE.equals(intent.getAction())) {
                String requested = OverlayPrefs.normalizeLensFlareMode(
                        intent.getStringExtra("mode"));
                OverlayPrefs.get(ChargingAccessibilityService.this).edit()
                        .putString(OverlayPrefs.LENS_FLARE_MODE, requested)
                        .apply();
                Log.i(TAG, "debug Lens Flare mode requested=" + requested);
            } else if (ACTION_DEBUG_SET_DOODLE_SEASON.equals(intent.getAction())) {
                int season = intent.getIntExtra(
                        "season", SeasonalDoodleView.SEASON_AUTO);
                if (season < SeasonalDoodleView.SEASON_AUTO
                        || season > SeasonalDoodleView.SEASON_WINTER) {
                    Log.i(TAG, "debug doodle season ignored value=" + season);
                    return;
                }
                OverlayPrefs.get(ChargingAccessibilityService.this).edit()
                        .putInt(OverlayPrefs.SEASON_MODE, season)
                        .apply();
                Log.i(TAG, "debug doodle season selected value=" + season);
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

    private final Runnable unlockAffordanceDispatchRunnable = new Runnable() {
        @Override
        public void run() {
            unlockAffordanceDispatchQueued = false;
            if (!unlockAffordancePending || unlockAffordanceShownThisWake) {
                return;
            }
            if (suppressUnlockAffordanceForActiveDoodle("dispatch")) {
                return;
            }
            if (!canDispatchUnlockAffordanceNow()) {
                Log.i(TAG, "unlock affordance dispatch deferred effect="
                        + OverlayPrefs.unlockEffect(ChargingAccessibilityService.this)
                        + " attached=" + unlockEffectOverlayAttached
                        + " parked=" + unlockEffectOverlayParked
                        + " visible=" + unlockFxVisible
                        + " ready=" + isUnlockEffectFirstFrameReady());
                return;
            }
            if (unlockAffordanceWaitingForBackground("dispatch")) {
                return;
            }
            Rect rect = unlockEffectVisibleRect();
            unlockEffectRenderer.showUnlockAffordance(rect, 0L);
            unlockAffordanceDeliveredRenderer = unlockEffectRenderer;
            unlockAffordancePending = false;
            unlockAffordanceShownThisWake = true;
            Log.i(TAG, "unlock affordance dispatched generation="
                    + unlockAffordanceDispatchGeneration
                    + " rect=" + rect.left + "," + rect.top + ","
                    + rect.right + "," + rect.bottom
                    + " effect=" + OverlayPrefs.unlockEffect(ChargingAccessibilityService.this));
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = this;
        serviceAlive = true;
        serviceLifecycleGeneration++;
        bootSafetyHolding = false;
        runtimeStartedAfterBootSafety = false;
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
        OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this, activeTouchBoxProfile());
        OverlayPrefs.migrateExperimentalNativeRefreshPrefsIfNeeded(this);
        applyPerfDefaultsOnce();
        ensureInternalTouchAreaEnabled();
        prefs.registerOnSharedPreferenceChangeListener(this);
        loadHomePackages();
        loadCallPackages();
        loadRuntimeSurfaceBlacklistPackages();
        configurePassiveService();
        logNotificationShadeDiagnosticEnvironment();
        refreshChargingState();
        registerScreenReceiver();
        registerDisplayListener();
        startRuntimeAfterBootSafety("connected");
    }

    private long bootSafetyRemainingMs() {
        if (OverlayPrefs.debugBypassBootSafety(this)) {
            return 0L;
        }
        return Math.max(0L, BOOT_SAFETY_WINDOW_MS - SystemClock.elapsedRealtime());
    }

    private boolean holdRuntimeForBootSafety(String reason) {
        long remainingMs = bootSafetyRemainingMs();
        if (remainingMs <= 0L) {
            bootSafetyHolding = false;
            return false;
        }
        if (!bootSafetyHolding || runtimeStartedAfterBootSafety) {
            bootSafetyHolding = true;
            runtimeStartedAfterBootSafety = false;
            stopAllRuntimeSurfaces();
            cancelEffectBackgroundRefreshAlarm();
            Log.w(TAG, "boot safety active reason=" + reason
                    + " remainingMs=" + remainingMs);
        }
        handler.removeCallbacks(bootSafetyReleaseRunnable);
        handler.postDelayed(bootSafetyReleaseRunnable, remainingMs + 50L);
        return true;
    }

    private void startRuntimeAfterBootSafety(String reason) {
        if (!serviceAlive || holdRuntimeForBootSafety(reason)) {
            return;
        }
        handler.removeCallbacks(bootSafetyReleaseRunnable);
        if (runtimeStartedAfterBootSafety) {
            return;
        }
        runtimeStartedAfterBootSafety = true;
        ensureDoodleLoaded();
        scheduleTimeWindowRefresh();
        preloadAndAttachSelectedUnlockEffectParked("boot_safety:" + reason);
        if (powerManager != null && !powerManager.isInteractive()) {
            scheduleScreenOffPrearm();
        }
        scheduleEffectBackgroundRefreshAlarm("boot_safety:" + reason);
        Log.i(TAG, "boot safety released reason=" + reason
                + " elapsedRealtimeMs=" + SystemClock.elapsedRealtime());
        evaluateVisibility("boot_safety:" + reason);
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
        if (holdRuntimeForBootSafety("accessibility_event")) {
            return;
        }
        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }
        boolean blockedPackage = isRuntimeSurfaceBlockPackage(event.getPackageName());
        if (blockedPackage) {
            noteActiveRuntimeBlock(event);
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
        if (interactive && isGlobalActionsEvent(event)) {
            showGlobalActionsSuppression();
            return;
        }
        if (globalActionsVisible && isGlobalActionsDismissEvent(event)) {
            // Samsung emits generic FrameLayout window-state events while the power menu is
            // still open. Let the structural window scan prove that Phone options disappeared
            // instead of clearing suppression from one ambiguous event.
            requestContentBlockedSurfaceScan(
                    "event:" + eventTypeName(event) + ":global_actions_dismiss_probe");
        }
        noteExternalLockscreenSurface(event, interactive);
        if (blockedPackage) {
            unlockAffordancePending = false;
            unlockTouchCachedWhileScreenOff = false;
            hideRuntimeSurfacesForBlockedPackage("event:" + eventTypeName(event));
            evaluateVisibility("event:" + eventTypeName(event) + ":blocked_package", false);
            handler.removeCallbacks(screenOnRefreshRunnable);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_FAST_MS);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_SETTLE_MS);
            return;
        }
        boolean pinEntryEvent = isPinEntryEvent(event);
        boolean keyboardPinEntryEvent = isKeyboardPinEntryEvent(event);
        if (interactive && (pinEntryEvent || keyboardPinEntryEvent)) {
            boolean wasPinEntryRequested = pinEntryRequested;
            boolean completingActiveHandoff = pinEntryHandoffActive;
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
            if (completingActiveHandoff) {
                // A current bouncer/keyboard event is stronger evidence than a later
                // bounded scan on slow SystemUI hardware. Complete immediately so a
                // partial negative scan cannot re-arm L.L.E over the PIN surface.
                finishSuccessfulPinEntryHandoff("pin_surface");
            }
            removeTouchDebugOverlay();
            if (!wasPinEntryRequested && !completingActiveHandoff) {
                scheduleUnlockEffectCleanup();
            }
            evaluateVisibility("event:" + eventTypeName(event) + ":pin_fast", false);
            handler.removeCallbacks(screenOnRefreshRunnable);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_FAST_MS);
            handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_SETTLE_MS);
            return;
        } else if (interactive && isNotificationShadeEvent(event)) {
            if (OverlayPrefs.debugLegacyQuickPanelDetection(this)) {
                // Compatibility path: this is the 1.0.5.3 behavior. Event metadata is
                // authoritative and hides the runtime surfaces immediately.
                notificationShadeVisible = true;
                notificationShadeLastSeenAt = SystemClock.uptimeMillis();
                removeTouchDebugOverlay();
                removeUnlockEffectOverlay();
                evaluateVisibility("event:" + eventTypeName(event)
                        + ":notification_shade_fast", false);
                handler.removeCallbacks(screenOnRefreshRunnable);
                handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_FAST_MS);
                handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_SETTLE_MS);
                return;
            } else if (isNotificationShadeProbeSuppressedForScreenOffTransition()) {
                Log.i(TAG, "notification shade hint ignored during screen-off transition"
                        + " type=" + eventTypeName(event)
                        + " sinceScreenOffMs=" + elapsedSinceScreenOff());
            } else {
                // Some Samsung bouncer transitions briefly expose a SystemUI event whose
                // accessible label looks like the notification shade. Treat event metadata
                // as an immediate safety probe only; the bounded window-tree scan is the
                // authority that confirms the shade and avoids a false positive on PIN entry.
                String shadeEventReason = "event:" + eventTypeName(event) + ":shade_hint";
                armNotificationShadeProbe(shadeEventReason, true);
                requestContentBlockedSurfaceScan(shadeEventReason);
                evaluateVisibility(shadeEventReason, false);
                handler.removeCallbacks(screenOnRefreshRunnable);
                handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_FAST_MS);
                handler.postDelayed(screenOnRefreshRunnable, SCREEN_ON_REFRESH_SETTLE_MS);
                return;
            }
        }
        String eventReason = "event:" + eventTypeName(event);
        if (interactive && isLockscreenLocked(false)
                && shouldProbeNotificationShade(event)) {
            boolean neutralizeTouch = eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            armNotificationShadeProbe(eventReason, neutralizeTouch);
            requestContentBlockedSurfaceScan(eventReason + ":shade_probe");
        }
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
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (activeService == this) {
            activeService = null;
        }
        cleanup();
        blockedSurfaceScanExecutor.shutdownNow();
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    static String debugRuntimeSnapshot() {
        ChargingAccessibilityService service = activeService;
        if (service == null) {
            return "service_connected=false\n";
        }
        StringBuilder snapshot = new StringBuilder();
        snapshot.append("service_connected=").append(service.serviceAlive).append('\n');
        snapshot.append("service_generation=")
                .append(service.serviceLifecycleGeneration).append('\n');
        snapshot.append("boot_safety_holding=")
                .append(service.bootSafetyHolding).append('\n');
        snapshot.append("boot_safety_remaining_ms=")
                .append(service.bootSafetyRemainingMs()).append('\n');
        snapshot.append("boot_safety_debug_bypass=")
                .append(OverlayPrefs.debugBypassBootSafety(service)).append('\n');
        snapshot.append("last_window_package=")
                .append(service.lastWindowPackage == null
                        ? "<none>" : service.lastWindowPackage)
                .append('\n');
        snapshot.append("active_runtime_block_package=")
                .append(service.activeRuntimeBlockPackage == null
                        ? "<none>" : service.activeRuntimeBlockPackage)
                .append('\n');
        snapshot.append("active_runtime_block_window_id=")
                .append(service.activeRuntimeBlockWindowId).append('\n');
        snapshot.append("charging=").append(service.charging).append('\n');
        snapshot.append("service_battery_percent=")
                .append(service.batteryPercent).append('\n');
        snapshot.append("display_profile=")
                .append(service.activeDisplayProfile).append('\n');
        snapshot.append("display_profile_mode=")
                .append(FoldDisplayTarget.modeLabel(service)).append('\n');
        snapshot.append("display_dimensions=")
                .append(service.activeDisplayWidth).append('x')
                .append(service.activeDisplayHeight).append('\n');
        Display snapshotDisplay = service.displayManager == null
                ? null : service.displayManager.getDisplay(service.activeDisplayId);
        int maxRefreshMilliHz = 0;
        int supportedModeCount = 0;
        if (snapshotDisplay != null) {
            try {
                Display.Mode[] modes = snapshotDisplay.getSupportedModes();
                supportedModeCount = modes == null ? 0 : modes.length;
                if (modes != null) {
                    for (Display.Mode mode : modes) {
                        if (mode != null
                                && !Float.isNaN(mode.getRefreshRate())
                                && !Float.isInfinite(mode.getRefreshRate())) {
                            maxRefreshMilliHz = Math.max(maxRefreshMilliHz,
                                    Math.round(mode.getRefreshRate() * 1000f));
                        }
                    }
                }
            } catch (Throwable ignored) {
                supportedModeCount = -1;
            }
        }
        snapshot.append("active_display_id=").append(service.activeDisplayId).append('\n');
        snapshot.append("active_display_rotation=")
                .append(snapshotDisplay == null ? -1 : snapshotDisplay.getRotation()).append('\n');
        snapshot.append("active_display_state=")
                .append(snapshotDisplay == null ? -1 : snapshotDisplay.getState()).append('\n');
        snapshot.append("active_display_current_refresh_millihz=")
                .append(snapshotDisplay == null ? 0
                        : Math.round(snapshotDisplay.getRefreshRate() * 1000f)).append('\n');
        snapshot.append("active_display_max_refresh_millihz=")
                .append(maxRefreshMilliHz).append('\n');
        snapshot.append("active_display_supported_mode_count=")
                .append(supportedModeCount).append('\n');
        int snapshotEffect = OverlayPrefs.unlockEffect(service);
        snapshot.append("background_source_type=")
                .append(OverlayPrefs.importedEffectBackgroundEnabled(
                        service, snapshotEffect, service.activeDisplayProfile)
                        ? "imported" : "automatic")
                .append('\n');
        Bitmap snapshotBackground = service.cachedUnlockEffectBackgroundBitmap;
        snapshot.append("background_bitmap_dimensions=")
                .append(snapshotBackground == null || snapshotBackground.isRecycled()
                        ? "unavailable"
                        : snapshotBackground.getWidth() + "x" + snapshotBackground.getHeight())
                .append('\n');
        snapshot.append("background_cache_bitmap_config=")
                .append(bitmapConfigToken(snapshotBackground)).append('\n');
        snapshot.append("background_cache_bitmap_allocation_bytes=")
                .append(bitmapAllocationBytes(snapshotBackground)).append('\n');
        boolean rendererBorrowsCache = snapshotBackground != null
                && !snapshotBackground.isRecycled()
                && service.unlockEffectRenderer instanceof BackgroundSourceRenderer
                && ((BackgroundSourceRenderer) service.unlockEffectRenderer)
                        .isUsingBackgroundSourceBitmap(snapshotBackground);
        snapshot.append("background_renderer_borrows_cache=")
                .append(rendererBorrowsCache).append('\n');
        boolean rawRenderer = service.unlockEffectRenderer
                instanceof RawArgb8888BackgroundRenderer;
        boolean rawSourceAccepted = rawRenderer
                && ((RawArgb8888BackgroundRenderer) service.unlockEffectRenderer)
                        .hasRawArgb8888BackgroundSource();
        snapshot.append("background_delivery_path=")
                .append(OverlayPrefs.testerNoColormapModeEnabled(service) ? "disabled"
                        : rawSourceAccepted ? "raw_direct"
                        : service.unlockEffectRenderer instanceof BackgroundSourceRenderer
                                && ((BackgroundSourceRenderer) service.unlockEffectRenderer)
                                        .hasBackgroundSourceBitmap()
                                ? "bitmap" : "pending_or_missing")
                .append('\n');
        snapshot.append("background_raw_renderer_capable=")
                .append(rawRenderer).append('\n');
        snapshot.append("background_raw_source_accepted=")
                .append(rawSourceAccepted).append('\n');
        snapshot.append("background_capture_generation=")
                .append(service.unlockEffectBackgroundGeneration).append('\n');
        snapshot.append("background_capture_attempts=")
                .append(service.unlockEffectBackgroundCaptureAttempts).append('\n');
        snapshot.append("background_capture_attempted_this_session=")
                .append(service.colorScreenshotAttemptedThisSession).append('\n');
        snapshot.append("background_capture_succeeded_this_session=")
                .append(service.unlockEffectBackgroundCaptureSucceededThisSession).append('\n');
        snapshot.append("background_captured_age_ms=")
                .append(ageSince(service.unlockEffectBackgroundCapturedAt)).append('\n');
        snapshot.append("background_effect=")
                .append(service.unlockEffectBackgroundEffect).append('\n');
        snapshot.append("background_cached_effect=")
                .append(service.cachedUnlockEffectBackgroundEffect).append('\n');
        snapshot.append("background_cached_profile=")
                .append(service.cachedUnlockEffectBackgroundProfile.length() == 0
                        ? "none" : service.cachedUnlockEffectBackgroundProfile).append('\n');
        snapshot.append("effect_uses_colormap_current=")
                .append(service.effectUsesScreenshotBackground(snapshotEffect)).append('\n');
        snapshot.append("effect_supports_no_colormap=")
                .append(OverlayPrefs.supportsTesterNoColormapMode(snapshotEffect)).append('\n');
        if (service.unlockEffectRenderer instanceof S3Arm64RippleEffectView) {
            snapshot.append(((S3Arm64RippleEffectView) service.unlockEffectRenderer)
                    .backgroundMappingDebugSnapshot());
        } else if (service.unlockEffectRenderer instanceof ColourDropletAppOwnedEffectView) {
            snapshot.append(((ColourDropletAppOwnedEffectView) service.unlockEffectRenderer)
                    .backgroundMemoryDebugSnapshot());
        } else if (service.unlockEffectRenderer
                instanceof SparklingBubblesAppOwnedEffectView) {
            snapshot.append(((SparklingBubblesAppOwnedEffectView) service.unlockEffectRenderer)
                    .backgroundMemoryDebugSnapshot());
        } else if (service.unlockEffectRenderer
                instanceof S6WaterDropletAppOwnedEffectView) {
            snapshot.append(((S6WaterDropletAppOwnedEffectView) service.unlockEffectRenderer)
                    .backgroundMemoryDebugSnapshot());
        }
        snapshot.append("doodle_attached=")
                .append(service.doodleOverlayAttached).append('\n');
        snapshot.append("doodle_parked=")
                .append(service.doodleOverlayParked).append('\n');
        snapshot.append("effect_attached=")
                .append(service.unlockEffectOverlayAttached).append('\n');
        snapshot.append("effect_parked=")
                .append(service.unlockEffectOverlayParked).append('\n');
        snapshot.append("effect_visible=")
                .append(service.unlockFxVisible).append('\n');
        snapshot.append("affordance_pending=")
                .append(service.unlockAffordancePending).append('\n');
        snapshot.append("affordance_shown_this_wake=")
                .append(service.unlockAffordanceShownThisWake).append('\n');
        snapshot.append("affordance_dispatch_queued=")
                .append(service.unlockAffordanceDispatchQueued).append('\n');
        snapshot.append("affordance_dispatch_generation=")
                .append(service.unlockAffordanceDispatchGeneration).append('\n');
        snapshot.append("effect_renderer_type=")
                .append(service.unlockEffectRendererType).append('\n');
        snapshot.append("effect_renderer_class=")
                .append(service.unlockEffectRenderer == null ? "none"
                        : service.unlockEffectRenderer.getClass().getSimpleName()).append('\n');
        snapshot.append("effect_renderer_display_dimensions=")
                .append(service.unlockEffectRendererDisplayWidth).append('x')
                .append(service.unlockEffectRendererDisplayHeight).append('\n');
        snapshot.append("effect_renderer_recreate_pending=")
                .append(service.unlockEffectRendererNeedsRecreate).append('\n');
        snapshot.append("effect_renderer_recreate_reason=")
                .append(diagnosticToken(service.unlockEffectRendererRecreateReason)).append('\n');
        snapshot.append("effect_readiness_state=")
                .append(service.unlockEffectReadinessState).append('\n');
        snapshot.append("effect_readiness_detail=")
                .append(diagnosticToken(service.unlockEffectReadinessDetail)).append('\n');
        snapshot.append("effect_hfr_enabled=")
                .append(OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(
                        service, snapshotEffect)).append('\n');
        snapshot.append("effect_hfr_speed_tenths=")
                .append(OverlayPrefs.experimentalNativeRefreshPhysicsSpeedTenths(
                        service, snapshotEffect)).append('\n');
        snapshot.append("effect_lens_flare_mode=")
                .append(OverlayPrefs.lensFlareMode(service)).append('\n');
        snapshot.append("effect_lens_flare_renderer=")
                .append("canvas").append('\n');
        snapshot.append("effect_gesture_active=")
                .append(service.unlockEffectGestureActive).append('\n');
        snapshot.append("effect_window_not_touchable=")
                .append(service.unlockEffectWindowParams == null
                        || (service.unlockEffectWindowParams.flags
                                & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0)
                .append('\n');
        snapshot.append("effect_window_alpha_milli=")
                .append(service.unlockEffectWindowParams == null ? -1
                        : Math.round(service.unlockEffectWindowParams.alpha * 1000f))
                .append('\n');
        snapshot.append("effect_window_neutralized_for_handoff=")
                .append(service.unlockEffectWindowNeutralizedForHandoff).append('\n');
        int touchWindowCount = (service.touchDebugView == null ? 0 : 1)
                + service.additionalTouchDebugViews.size();
        snapshot.append("touch_window_count=").append(touchWindowCount).append('\n');
        snapshot.append("touch_primary_attached=")
                .append(service.touchDebugView != null
                        && service.touchDebugView.isAttachedToWindow()).append('\n');
        snapshot.append("touch_requested_touchable=")
                .append(service.touchDebugTouchable).append('\n');
        snapshot.append("touch_params_not_touchable=")
                .append(service.touchDebugParams == null
                        || (service.touchDebugParams.flags
                                & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0)
                .append('\n');
        snapshot.append("touch_additional_window_count=")
                .append(service.additionalTouchDebugViews.size()).append('\n');
        snapshot.append("touch_resolved_region_count=")
                .append(service.resolvedTouchBoxesCache.size()).append('\n');
        snapshot.append("touch_resolved_profile=")
                .append(service.resolvedTouchBoxesProfile.length() == 0
                        ? "none" : service.resolvedTouchBoxesProfile).append('\n');
        snapshot.append("touch_resolved_dimensions=")
                .append(service.resolvedTouchBoxesWidth).append('x')
                .append(service.resolvedTouchBoxesHeight).append('\n');
        snapshot.append("touch_cached_while_screen_off=")
                .append(service.unlockTouchCachedWhileScreenOff).append('\n');
        snapshot.append("touch_box_capture_scheduled=")
                .append(service.touchBoxScreenshotScheduled).append('\n');
        snapshot.append("touch_box_capture_in_flight=")
                .append(service.touchBoxScreenshotInFlight).append('\n');
        snapshot.append("touch_box_capture_callback_pending=")
                .append(service.touchBoxScreenshotCallbackPending).append('\n');
        snapshot.append("buffered_readiness_gesture_active=")
                .append(service.bufferedReadinessGestureActive).append('\n');
        snapshot.append("lockscreen_session_polling=")
                .append(service.lockscreenSessionPolling).append('\n');
        snapshot.append("blocked_surface_scan_in_flight=")
                .append(service.blockedSurfaceScanInFlight).append('\n');
        snapshot.append("lock_cycle_safety_bypass_active=")
                .append(service.lockCycleSafetyBypassActive).append('\n');
        snapshot.append("three_finger_safety_bypass_enabled=")
                .append(OverlayPrefs.threeFingerSafetyBypassEnabled(service)).append('\n');
        snapshot.append("pin_entry_pending=")
                .append(service.pinEntryPending).append('\n');
        snapshot.append("pin_entry_surface_visible=")
                .append(service.pinEntrySurfaceVisible).append('\n');
        snapshot.append("pin_entry_handoff_active=")
                .append(service.pinEntryHandoffActive).append('\n');
        snapshot.append("pin_entry_handoff_attempt=")
                .append(service.pinEntryHandoffAttempt).append('\n');
        snapshot.append("pin_entry_handoff_callback=")
                .append(service.pinEntryHandoffLastCallback).append('\n');
        snapshot.append("pin_entry_handoff_terminal=")
                .append(service.pinEntryHandoffLastTerminal).append('\n');
        snapshot.append("pin_entry_handoff_outcome=")
                .append(service.pinEntryHandoffLastOutcome).append('\n');
        snapshot.append("pin_entry_handoff_observed_age_ms=")
                .append(service.pinEntryHandoffLastObservedAt <= 0L
                        ? -1L
                        : SystemClock.uptimeMillis()
                                - service.pinEntryHandoffLastObservedAt)
                .append('\n');
        snapshot.append("pin_entry_handoff_interactive=")
                .append(service.pinEntryHandoffLastInteractive).append('\n');
        snapshot.append("pin_entry_handoff_keyguard_locked=")
                .append(service.pinEntryHandoffLastKeyguardLocked).append('\n');
        snapshot.append("pin_entry_handoff_device_locked=")
                .append(service.pinEntryHandoffLastDeviceLocked).append('\n');
        snapshot.append("pin_entry_handoff_touch_windows_before=")
                .append(service.pinEntryHandoffTouchWindowsBefore).append('\n');
        snapshot.append("pin_entry_handoff_touch_windows_after=")
                .append(service.pinEntryHandoffTouchWindowsAfter).append('\n');
        snapshot.append("pin_entry_handoff_touch_removal_mode=")
                .append(service.pinEntryHandoffTouchRemovalMode).append('\n');
        snapshot.append("pin_entry_handoff_touch_removal_result=")
                .append(service.pinEntryHandoffTouchRemovalResult).append('\n');
        snapshot.append("pin_entry_handoff_touch_removal_elapsed_ms=")
                .append(service.pinEntryHandoffTouchRemovalElapsedMs).append('\n');
        snapshot.append("pin_entry_handoff_window_alpha_result=")
                .append(service.pinEntryHandoffWindowAlphaResult).append('\n');
        snapshot.append("pin_entry_handoff_window_alpha_elapsed_ms=")
                .append(service.pinEntryHandoffWindowAlphaElapsedMs).append('\n');
        snapshot.append("pin_entry_handoff_prepare_age_ms=")
                .append(service.pinEntryHandoffPreparedAt <= 0L
                        ? -1L : SystemClock.uptimeMillis()
                                - service.pinEntryHandoffPreparedAt)
                .append('\n');
        snapshot.append("pin_entry_handoff_swipe_queue_age_ms=")
                .append(service.pinEntryHandoffSwipeQueuedAt <= 0L
                        ? -1L : SystemClock.uptimeMillis()
                                - service.pinEntryHandoffSwipeQueuedAt)
                .append('\n');
        snapshot.append("pin_entry_handoff_dispatch_age_ms=")
                .append(service.pinEntryHandoffAttemptDispatchedAt <= 0L
                        ? -1L : SystemClock.uptimeMillis()
                                - service.pinEntryHandoffAttemptDispatchedAt)
                .append('\n');
        snapshot.append("pin_entry_handoff_fail_open=")
                .append(service.pinEntryHandoffFailOpen).append('\n');
        snapshot.append("notification_shade_visible=")
                .append(service.notificationShadeVisible).append('\n');
        snapshot.append("notification_shade_diagnostic_age_ms=")
                .append(service.lastNotificationShadeDiagnosticCapturedAt <= 0L
                        ? -1L
                        : SystemClock.uptimeMillis()
                                - service.lastNotificationShadeDiagnosticCapturedAt)
                .append('\n');
        snapshot.append("notification_shade_diagnostic_reason=")
                .append(service.lastNotificationShadeDiagnosticReason).append('\n');
        snapshot.append("notification_shade_diagnostic_matched=")
                .append(service.lastNotificationShadeDiagnosticMatched).append('\n');
        snapshot.append("notification_shade_diagnostic_quality=")
                .append(service.lastNotificationShadeDiagnosticQuality).append('\n');
        snapshot.append("notification_shade_diagnostic_windows=")
                .append(service.lastNotificationShadeDiagnosticWindows).append('\n');
        snapshot.append("notification_shade_diagnostic_roots=")
                .append(service.lastNotificationShadeDiagnosticRoots).append('\n');
        snapshot.append("notification_shade_diagnostic_nodes=")
                .append(service.lastNotificationShadeDiagnosticNodes).append('\n');
        snapshot.append("notification_shade_diagnostic_exhausted=")
                .append(service.lastNotificationShadeDiagnosticExhausted).append('\n');
        snapshot.append("notification_shade_window_signature=")
                .append(service.lastNotificationShadeWindowSignature).append('\n');
        snapshot.append("notification_shade_visible_node_signature=")
                .append(service.lastNotificationShadeNodeSignature).append('\n');
        snapshot.append("notification_shade_confirmed_window_signature=")
                .append(service.lastConfirmedNotificationShadeWindowSignature).append('\n');
        snapshot.append("notification_shade_confirmed_node_signature=")
                .append(service.lastConfirmedNotificationShadeNodeSignature).append('\n');
        snapshot.append("global_actions_visible=")
                .append(service.globalActionsVisible).append('\n');
        snapshot.append("global_actions_age_ms=")
                .append(service.globalActionsShownAt <= 0L
                        ? -1L
                        : SystemClock.uptimeMillis() - service.globalActionsShownAt)
                .append('\n');
        snapshot.append("custom_blacklist_packages=")
                .append(new java.util.TreeSet<String>(
                        OverlayPrefs.userRuntimeBlacklistPackages(service)))
                .append('\n');
        snapshot.append("background_capture_active=")
                .append(service.colorScreenshotInFlight).append('\n');
        return snapshot.toString();
    }

    static boolean scheduleTesterUnderlayProbe(long delayMs) {
        ChargingAccessibilityService service = activeService;
        if (!BuildFlavor.TESTER
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || service == null || !service.serviceAlive) {
            return false;
        }
        service.testerUnderlayProbeGeneration++;
        service.testerUnderlayProbeDeadlineAt = SystemClock.uptimeMillis()
                + TESTER_UNDERLAY_PROBE_ARM_WINDOW_MS;
        service.testerUnderlayProbeSawLockedScreen = false;
        service.testerUnderlayProbeLastWindowCount = -1;
        service.testerUnderlayProbeLastCandidateCount = -1;
        service.handler.removeCallbacks(service.testerUnderlayProbeRunnable);
        File previous = testerUnderlayProbeFile(service);
        if (previous.exists() && !previous.delete()) {
            Log.d(TAG, "tester underlay probe could not clear previous private result");
        }
        service.setTesterUnderlayProbeStatus(
                "armed for 30 seconds | waiting for an awake lockscreen");
        Log.i(TAG, "tester underlay probe armed windowMs="
                + TESTER_UNDERLAY_PROBE_ARM_WINDOW_MS);
        service.handler.postDelayed(service.testerUnderlayProbeRunnable,
                Math.min(250L, Math.max(0L, delayMs)));
        return true;
    }

    static File testerUnderlayProbeFile(Context context) {
        return new File(context.getFilesDir(), TESTER_UNDERLAY_PROBE_FILE);
    }

    static void reloadLgLastScreenCache() {
        final ChargingAccessibilityService service = activeService;
        if (service == null || !service.serviceAlive) {
            return;
        }
        service.handler.post(new Runnable() {
            @Override
            public void run() {
                int effect = OverlayPrefs.unlockEffect(service);
                if (service.effectNeedsLgPreLockUnderlay(effect)) {
                    service.invalidateLoadedLgPreLockUnderlay(effect);
                }
            }
        });
    }

    private boolean captureLgPreLockUnderlayIfNeeded() {
        final int effect = OverlayPrefs.unlockEffect(this);
        final long now = SystemClock.uptimeMillis();
        if (!effectNeedsLgPreLockUnderlay(effect)
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || lgPreLockUnderlayCaptureInFlight
                || !interactiveSessionWasUnlocked) {
            return false;
        }
        final int captureGeneration = ++lgPreLockUnderlayCaptureGeneration;
        final int lifecycleGeneration = serviceLifecycleGeneration;
        final int captureDisplayId = activeDisplayId == Display.INVALID_DISPLAY
                ? Display.DEFAULT_DISPLAY : activeDisplayId;
        final String captureProfile = FoldDisplayTarget.normalizeProfile(activeDisplayProfile);
        final int captureWidth = activeDisplayWidth > 0
                ? activeDisplayWidth : Math.max(1, activeDisplayMetrics().widthPixels);
        final int captureHeight = activeDisplayHeight > 0
                ? activeDisplayHeight : Math.max(1, activeDisplayMetrics().heightPixels);
        final long requestedAt = SystemClock.uptimeMillis();
        final boolean mirrorTesterProbe = BuildFlavor.TESTER
                && testerUnderlayProbeDeadlineAt > now;
        if (mirrorTesterProbe) {
            testerUnderlayProbeDeadlineAt = 0L;
            handler.removeCallbacks(testerUnderlayProbeRunnable);
            setTesterUnderlayProbeStatus("capturing production pre-lock underlay");
        }
        lgPreLockUnderlayCaptureInFlight = true;
        try {
            takeScreenshot(captureDisplayId, mainExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    final Bitmap bitmap = bitmapFromScreenshot(screenshotResult);
                    if (bitmap == null) {
                        finishLgPreLockUnderlayCapture(captureGeneration);
                        if (mirrorTesterProbe) {
                            setTesterUnderlayProbeStatus(
                                    "pre-lock underlay capture returned an empty bitmap");
                        }
                        return;
                    }
                    if (!serviceAlive
                            || lifecycleGeneration != serviceLifecycleGeneration
                            || captureGeneration != lgPreLockUnderlayCaptureGeneration
                            || captureDisplayId != (activeDisplayId == Display.INVALID_DISPLAY
                                    ? Display.DEFAULT_DISPLAY : activeDisplayId)
                            || !captureProfile.equals(activeDisplayProfile)
                            || captureWidth != activeDisplayWidth
                            || captureHeight != activeDisplayHeight
                            || bitmap.getWidth() != captureWidth
                            || bitmap.getHeight() != captureHeight) {
                        Log.i(TAG, "LG pre-lock underlay discarded: display geometry changed"
                                + " requested=" + captureDisplayId + "/" + captureProfile
                                + "/" + captureWidth + "x" + captureHeight
                                + " current=" + activeDisplayId + "/" + activeDisplayProfile
                                + "/" + activeDisplayWidth + "x" + activeDisplayHeight
                                + " bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                        bitmap.recycle();
                        finishLgPreLockUnderlayCapture(captureGeneration);
                        return;
                    }
                    final int width = captureWidth;
                    final int height = captureHeight;
                    final long callbackMs = Math.max(0L,
                            SystemClock.uptimeMillis() - requestedAt);
                    try {
                        ioExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                File target = OverlayPrefs.lgPreLockUnderlayFile(
                                        ChargingAccessibilityService.this,
                                        captureProfile, captureDisplayId, width, height);
                                File temp = new File(
                                        target.getParentFile(), target.getName() + ".tmp");
                                boolean saved = Argb8888BitmapStore.write(temp, bitmap);
                                if (saved) {
                                    saved = swapEffectBackgroundCacheFile(temp, target);
                                }
                                boolean testerSaved = !mirrorTesterProbe
                                        || Argb8888BitmapStore.write(
                                                testerUnderlayProbeFile(
                                                        ChargingAccessibilityService.this),
                                                bitmap);
                                bitmap.recycle();
                                if (!saved && temp.exists()) {
                                    //noinspection ResultOfMethodCallIgnored
                                    temp.delete();
                                }
                                final boolean committed = saved;
                                final boolean probeCommitted = testerSaved;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        finishLgPreLockUnderlayCapture(captureGeneration);
                                        if (!serviceAlive
                                                || lifecycleGeneration
                                                != serviceLifecycleGeneration
                                                || !committed) {
                                            if (mirrorTesterProbe) {
                                                setTesterUnderlayProbeStatus(
                                                        "pre-lock underlay private save failed");
                                            }
                                            return;
                                        }
                                        int generation = OverlayPrefs
                                                .markLgPreLockUnderlayCaptured(
                                                        ChargingAccessibilityService.this,
                                                        captureProfile, captureDisplayId,
                                                        width, height,
                                                        System.currentTimeMillis());
                                        invalidateLoadedLgPreLockUnderlay(effect);
                                        if (mirrorTesterProbe) {
                                            setTesterUnderlayProbeStatus(probeCommitted
                                                    ? "ready | production pre-lock underlay "
                                                            + width + " x " + height
                                                            + " | callbackMs=" + callbackMs
                                                            + " | generation=" + generation
                                                    : "production underlay saved; tester mirror failed");
                                        }
                                        Log.i(TAG, "LG pre-lock underlay ready dimensions="
                                                + width + "x" + height
                                                + " displayId=" + captureDisplayId
                                                + " profile=" + captureProfile
                                                + " generation=" + generation
                                                + " callbackMs=" + callbackMs);
                                    }
                                });
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        bitmap.recycle();
                        finishLgPreLockUnderlayCapture(captureGeneration);
                        if (mirrorTesterProbe) {
                            setTesterUnderlayProbeStatus(
                                    "pre-lock underlay save executor unavailable");
                        }
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    finishLgPreLockUnderlayCapture(captureGeneration);
                    long callbackMs = Math.max(0L,
                            SystemClock.uptimeMillis() - requestedAt);
                    if (mirrorTesterProbe) {
                        setTesterUnderlayProbeStatus("pre-lock underlay capture failed | code="
                                + errorCode + " | callbackMs=" + callbackMs);
                    }
                    Log.i(TAG, "LG pre-lock underlay failed code=" + errorCode
                            + " callbackMs=" + callbackMs);
                }
            });
            return true;
        } catch (Throwable t) {
            finishLgPreLockUnderlayCapture(captureGeneration);
            if (mirrorTesterProbe) {
                setTesterUnderlayProbeStatus("pre-lock underlay capture request failed");
            }
            Log.i(TAG, "LG pre-lock underlay request failed");
            return false;
        }
    }

    private void finishLgPreLockUnderlayCapture(int generation) {
        if (generation == lgPreLockUnderlayCaptureGeneration) {
            lgPreLockUnderlayCaptureInFlight = false;
        }
    }

    private void invalidateLoadedLgPreLockUnderlay(int effect) {
        if (unlockEffectRendererType != effect
                || !(unlockEffectRenderer instanceof BackgroundSourceRenderer)) {
            return;
        }
        if (unlockEffectRenderer instanceof SecondaryBackgroundSourceRenderer
                && OverlayPrefs.usesLgPreLockUnderlayAsSecondary(effect)) {
            ((SecondaryBackgroundSourceRenderer) unlockEffectRenderer)
                    .clearSecondaryBackgroundSourceBitmap();
        } else {
            ((BackgroundSourceRenderer) unlockEffectRenderer).clearBackgroundSourceBitmap();
            if (unlockEffectBackgroundEffect == effect) {
                unlockEffectBackgroundEffect = -1;
                unlockEffectBackgroundCapturedAt = 0L;
            }
        }
        // The file has already been atomically committed when this method runs. Reload it now;
        // otherwise the parked renderer keeps an empty texture until it is recreated, and the
        // next full lock cycle can neither draw nor leave the readiness gesture buffer.
        loadCachedUnlockEffectBackgroundSourceIfNeeded(effect);
    }

    private void captureTesterPreLockFrame() {
        if (!BuildFlavor.TESTER
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || testerUnderlayProbeDeadlineAt <= SystemClock.uptimeMillis()) {
            return;
        }
        testerUnderlayProbeDeadlineAt = 0L;
        handler.removeCallbacks(testerUnderlayProbeRunnable);
        final int probeGeneration = testerUnderlayProbeGeneration;
        final int lifecycleGeneration = serviceLifecycleGeneration;
        final long requestedAt = SystemClock.uptimeMillis();
        setTesterUnderlayProbeStatus("capturing display at screen-off transition");
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    final Bitmap bitmap = bitmapFromScreenshot(screenshotResult);
                    if (bitmap == null) {
                        setTesterUnderlayProbeStatus(
                                "screen-off display capture returned an empty bitmap");
                        return;
                    }
                    if (!serviceAlive
                            || lifecycleGeneration != serviceLifecycleGeneration
                            || probeGeneration != testerUnderlayProbeGeneration) {
                        bitmap.recycle();
                        return;
                    }
                    final long callbackMs = Math.max(0L,
                            SystemClock.uptimeMillis() - requestedAt);
                    try {
                        ioExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                boolean saved = Argb8888BitmapStore.write(
                                        testerUnderlayProbeFile(
                                                ChargingAccessibilityService.this), bitmap);
                                int width = bitmap.getWidth();
                                int height = bitmap.getHeight();
                                bitmap.recycle();
                                if (!serviceAlive
                                        || lifecycleGeneration != serviceLifecycleGeneration
                                        || probeGeneration != testerUnderlayProbeGeneration) {
                                    return;
                                }
                                setTesterUnderlayProbeStatus(saved
                                        ? "ready | screen-off display " + width + " x " + height
                                                + " | callbackMs=" + callbackMs
                                        : "screen-off private ARGB8888 save failed");
                                Log.i(TAG, saved
                                        ? "tester pre-lock display probe ready dimensions="
                                                + width + "x" + height
                                                + " callbackMs=" + callbackMs
                                        : "tester pre-lock display probe private save failed");
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        bitmap.recycle();
                        setTesterUnderlayProbeStatus(
                                "screen-off private save executor unavailable");
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    if (serviceAlive
                            && lifecycleGeneration == serviceLifecycleGeneration
                            && probeGeneration == testerUnderlayProbeGeneration) {
                        long callbackMs = Math.max(0L,
                                SystemClock.uptimeMillis() - requestedAt);
                        setTesterUnderlayProbeStatus("screen-off display capture failed | code="
                                + errorCode + " | callbackMs=" + callbackMs);
                        Log.i(TAG, "tester pre-lock display probe failed code="
                                + errorCode + " callbackMs=" + callbackMs);
                    }
                }
            });
        } catch (Throwable t) {
            setTesterUnderlayProbeStatus("screen-off display capture request failed");
            Log.i(TAG, "tester pre-lock display probe request failed");
        }
    }

    static String testerUnderlayProbeStatus(Context context) {
        return OverlayPrefs.get(context).getString(
                TESTER_UNDERLAY_PROBE_STATUS, "not run yet");
    }

    private void setTesterUnderlayProbeStatus(String status) {
        OverlayPrefs.get(this).edit()
                .putString(TESTER_UNDERLAY_PROBE_STATUS,
                        status == null ? "unknown" : status)
                .apply();
    }

    private void runTesterUnderlayProbe() {
        if (!BuildFlavor.TESTER
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || !serviceAlive) {
            setTesterUnderlayProbeStatus("unsupported or service unavailable");
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now >= testerUnderlayProbeDeadlineAt) {
            if (testerUnderlayProbeSawLockedScreen) {
                setTesterUnderlayProbeStatus(
                        "timed out | locked screen seen; no eligible application window"
                                + " | windows=" + testerUnderlayProbeLastWindowCount
                                + " | candidates=" + testerUnderlayProbeLastCandidateCount);
                Log.i(TAG, "tester underlay probe timed out without application window"
                        + " windows=" + testerUnderlayProbeLastWindowCount
                        + " candidates=" + testerUnderlayProbeLastCandidateCount);
            } else {
                setTesterUnderlayProbeStatus(
                        "timed out | no locked screen appeared within 30 seconds");
                Log.i(TAG, "tester underlay probe timed out before locked screen");
            }
            return;
        }
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (!isLockscreenLocked(false)) {
            setTesterUnderlayProbeStatus(
                    "armed | waiting for keyguard locked | interactive=" + interactive);
            handler.postDelayed(testerUnderlayProbeRunnable,
                    TESTER_UNDERLAY_PROBE_POLL_MS);
            return;
        }
        testerUnderlayProbeSawLockedScreen = true;

        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (Throwable t) {
            setTesterUnderlayProbeStatus("window enumeration failed");
            return;
        }
        int totalWindows = windows == null ? 0 : windows.size();
        testerUnderlayProbeLastWindowCount = totalWindows;
        AccessibilityWindowInfo chosen = null;
        int candidates = 0;
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window == null
                        || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }
                AccessibilityNodeInfo root = null;
                CharSequence packageName = null;
                try {
                    root = window.getRoot();
                    packageName = root == null ? null : root.getPackageName();
                } catch (Throwable ignored) {
                    // An inaccessible window is not a usable screenshot candidate.
                } finally {
                    if (root != null) {
                        try {
                            root.recycle();
                        } catch (Throwable ignored) {
                        }
                    }
                }
                if (packageName == null) {
                    continue;
                }
                String packageToken = packageName.toString();
                if (getPackageName().equals(packageToken)
                        || "com.android.systemui".equals(packageToken)) {
                    continue;
                }
                candidates++;
                if (chosen == null || window.getLayer() > chosen.getLayer()) {
                    chosen = window;
                }
            }
        }
        testerUnderlayProbeLastCandidateCount = candidates;
        if (chosen == null) {
            setTesterUnderlayProbeStatus("locked screen found; waiting for app window"
                    + " | interactive=" + interactive
                    + " | windows=" + totalWindows + " | candidates=" + candidates);
            handler.postDelayed(testerUnderlayProbeRunnable,
                    TESTER_UNDERLAY_PROBE_POLL_MS);
            return;
        }

        testerUnderlayProbeDeadlineAt = 0L;

        final int probeGeneration = testerUnderlayProbeGeneration;
        final int lifecycleGeneration = serviceLifecycleGeneration;
        final int windowId = chosen.getId();
        final int layer = chosen.getLayer();
        final boolean active = chosen.isActive();
        final boolean focused = chosen.isFocused();
        final int windowCount = totalWindows;
        final int candidateCount = candidates;
        setTesterUnderlayProbeStatus("capturing application window | windows="
                + windowCount + " | candidates=" + candidateCount);
        try {
            takeScreenshotOfWindow(windowId, mainExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    final Bitmap bitmap = bitmapFromScreenshot(screenshotResult);
                    if (bitmap == null) {
                        setTesterUnderlayProbeStatus("capture returned an empty bitmap");
                        return;
                    }
                    if (!serviceAlive
                            || lifecycleGeneration != serviceLifecycleGeneration
                            || probeGeneration != testerUnderlayProbeGeneration) {
                        bitmap.recycle();
                        return;
                    }
                    setTesterUnderlayProbeStatus("saving private ARGB8888 result");
                    try {
                        ioExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                boolean saved = Argb8888BitmapStore.write(
                                        testerUnderlayProbeFile(
                                                ChargingAccessibilityService.this), bitmap);
                                int width = bitmap.getWidth();
                                int height = bitmap.getHeight();
                                bitmap.recycle();
                                if (!serviceAlive
                                        || lifecycleGeneration != serviceLifecycleGeneration
                                        || probeGeneration != testerUnderlayProbeGeneration) {
                                    return;
                                }
                                String status = saved
                                        ? "ready | source window " + width + " x " + height
                                                + " | layer=" + layer
                                                + " | active=" + active
                                                + " | focused=" + focused
                                                + " | windows=" + windowCount
                                                + " | candidates=" + candidateCount
                                        : "private ARGB8888 save failed";
                                setTesterUnderlayProbeStatus(status);
                                Log.i(TAG, saved
                                        ? "tester underlay probe ready dimensions="
                                                + width + "x" + height
                                                + " windows=" + windowCount
                                                + " candidates=" + candidateCount
                                        : "tester underlay probe private save failed");
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        bitmap.recycle();
                        setTesterUnderlayProbeStatus("private save executor unavailable");
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    if (serviceAlive
                            && lifecycleGeneration == serviceLifecycleGeneration
                            && probeGeneration == testerUnderlayProbeGeneration) {
                        setTesterUnderlayProbeStatus("window screenshot failed | code="
                                + errorCode + " | candidates=" + candidateCount);
                        Log.i(TAG, "tester underlay probe screenshot failed code="
                                + errorCode + " candidates=" + candidateCount);
                    }
                }
            });
        } catch (Throwable t) {
            setTesterUnderlayProbeStatus("window screenshot request failed");
            Log.i(TAG, "tester underlay probe request failed");
        }
    }

    private static long ageSince(long timestamp) {
        return timestamp <= 0L ? -1L : Math.max(0L, SystemClock.uptimeMillis() - timestamp);
    }

    private static String bitmapConfigToken(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getConfig() == null) {
            return "unavailable";
        }
        return diagnosticToken(bitmap.getConfig().name().toLowerCase(java.util.Locale.US));
    }

    private static long bitmapAllocationBytes(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 0L;
        }
        try {
            return bitmap.getAllocationByteCount();
        } catch (RuntimeException ignored) {
            return (long) bitmap.getRowBytes() * bitmap.getHeight();
        }
    }

    private static String diagnosticToken(String value) {
        if (value == null || value.length() == 0) {
            return "none";
        }
        StringBuilder token = new StringBuilder(Math.min(96, value.length()));
        for (int index = 0; index < value.length() && token.length() < 96; index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)
                    || character == '_' || character == '-' || character == '.'
                    || character == ':' || character == '<' || character == '>') {
                token.append(character);
            } else {
                token.append('_');
            }
        }
        return token.toString();
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
        if (OverlayPrefs.DEBUG_BYPASS_BOOT_SAFETY.equals(key)) {
            if (!holdRuntimeForBootSafety("prefs:bypass")) {
                startRuntimeAfterBootSafety("prefs:bypass");
            }
            return;
        }
        if (OverlayPrefs.DEBUG_LEGACY_QUICK_PANEL_DETECTION.equals(key)) {
            blockedSurfaceScanInFlightRequestId = ++blockedSurfaceScanRequestGeneration;
            blockedSurfaceScanInFlight = false;
            clearBlockedSurfaceState();
            Log.i(TAG, "quick panel detector="
                    + (OverlayPrefs.debugLegacyQuickPanelDetection(this)
                    ? "legacy_1.0.5.3" : "structural"));
            evaluateVisibility("prefs:quick_panel_detector", false);
            return;
        }
        if (holdRuntimeForBootSafety("prefs:" + key)) {
            return;
        }
        if (OverlayPrefs.UNLOCK_EFFECT_RANDOM_ENABLED.equals(key)
                || OverlayPrefs.UNLOCK_EFFECT_RANDOM_POOL.equals(key)) {
            handler.removeCallbacks(randomUnlockPrefsRefreshRunnable);
            handler.postDelayed(randomUnlockPrefsRefreshRunnable, 32L);
            return;
        }
        if (OverlayPrefs.UNLOCK_EFFECT_RANDOM_CURRENT.equals(key)
                || OverlayPrefs.UNLOCK_EFFECT_RANDOM_REMAINING.equals(key)) {
            // Candidate changes are owned by the coalesced preference refresh or the
            // post-unlock advance path; avoid rebuilding twice for one shuffle draw.
            return;
        }
        if (OverlayPrefs.TESTER_NO_COLORMAP_MODE.equals(key)) {
            boolean enabled = OverlayPrefs.testerNoColormapModeEnabled(this);
            int selectedEffect = OverlayPrefs.rawUnlockEffect(this);
            if (enabled && !OverlayPrefs.supportsTesterNoColormapMode(selectedEffect)) {
                selectedEffect = OverlayPrefs.EFFECT_MASS_TENSION;
                setUnlockEffectPreferenceInternally(selectedEffect);
            }
            unlockEffectBackgroundGeneration++;
            colorScreenshotInFlight = false;
            handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
            clearCachedUnlockEffectBackgroundBitmap();
            if (enabled) {
                disableHardEffectBackgroundRecapture("tester_no_colormap_mode");
            }
            cancelUnlockAffordanceDispatch(false, "prefs:tester_no_colormap_mode");
            if (unlockEffectRenderer != null) {
                destroyUnlockEffectOverlay();
            }
            preloadAndAttachSelectedUnlockEffectParked("prefs:tester_no_colormap_mode");
            evaluateVisibility("prefs:tester_no_colormap_mode", false);
            Log.i(TAG, "tester no-colormap mode=" + enabled
                    + " selectedEffect=" + selectedEffect);
            return;
        }
        if (OverlayPrefs.RIPPLE_INK_PALETTE.equals(key)) {
            applyRippleInkPalettePreference();
            return;
        }
        if (OverlayPrefs.LENS_FLARE_GLES_RENDERER.equals(key)) {
            boolean staleEnabled = OverlayPrefs.get(this).getBoolean(key, false);
            if (staleEnabled) {
                OverlayPrefs.get(this).edit().putBoolean(key, false).apply();
            }
            Log.w(TAG, "ignored retired Lens Flare GLES preference enabled=" + staleEnabled
                    + "; Canvas/HWUI remains active");
            return;
        }
        if (OverlayPrefs.LENS_FLARE_MODE.equals(key)) {
            if (OverlayPrefs.unlockEffect(this) != OverlayPrefs.EFFECT_S4_LENS_FLARE) {
                return;
            }
            String reason = "prefs:lens_flare_mode";
            cancelUnlockAffordanceDispatch(false, reason);
            if (unlockEffectRenderer != null) {
                destroyUnlockEffectOverlay();
            }
            preloadAndAttachSelectedUnlockEffectParked(reason);
            evaluateVisibility(reason, false);
            Log.i(TAG, "Lens Flare Canvas mode=" + OverlayPrefs.lensFlareMode(this));
            return;
        }
        if (OverlayPrefs.isExperimentalNativeRefreshPhysicsPreferenceKey(key)) {
            int changedEffect =
                    OverlayPrefs.experimentalNativeRefreshPhysicsEffectFromPreferenceKey(key);
            int selectedEffect = OverlayPrefs.unlockEffect(this);
            boolean speedChanged =
                    OverlayPrefs.isExperimentalNativeRefreshPhysicsSpeedTenthsPreferenceKey(key);
            boolean nativeRefreshEnabled = OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(
                    this, changedEffect);
            float multiplier = OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(
                    this, changedEffect);
            Log.i(TAG, "native refresh physics preference changed effect=" + changedEffect
                    + " selectedEffect=" + selectedEffect
                    + " keyType=" + (speedChanged ? "speed" : "enabled")
                    + " enabled=" + nativeRefreshEnabled
                    + " speedMultiplier=" + multiplier);
            if (changedEffect != selectedEffect) {
                return;
            }
            if (changedEffect == OverlayPrefs.EFFECT_RIPPLE_INK
                    && unlockEffectRenderer instanceof RippleInkPortEffectView) {
                // Hybrid HFR changes only the mesh presentation clock.  Keep the retained Ink
                // density/FBO and apply it live instead of rebuilding the production overlay.
                ((RippleInkPortEffectView) unlockEffectRenderer).setHighFrameRateEnabled(
                        nativeRefreshEnabled);
                evaluateVisibility("prefs:ripple_ink_hybrid_hfr", false);
                return;
            }
            cancelUnlockAffordanceDispatch(false, "prefs:native_refresh_physics_per_effect");
            if (unlockEffectRenderer != null) {
                destroyUnlockEffectOverlay();
            }
            preloadAndAttachSelectedUnlockEffectParked(
                    "prefs:native_refresh_physics_per_effect");
            evaluateVisibility("prefs:native_refresh_physics_per_effect", false);
            return;
        }
        if (OverlayPrefs.MASTER_ENABLED.equals(key) && !OverlayPrefs.masterEnabled(this)) {
            stopAllRuntimeSurfaces();
            disableHardEffectBackgroundRecapture("master_disabled");
        }
        if (OverlayPrefs.USER_RUNTIME_BLACKLIST_PACKAGES.equals(key)) {
            loadRuntimeSurfaceBlacklistPackages();
            if (isRuntimeSurfaceBlocked()) {
                hideRuntimeSurfacesForBlockedPackage("prefs:custom_blacklist");
            }
            evaluateVisibility("prefs:custom_blacklist", false);
            return;
        }
        if (OverlayPrefs.LLE_AUDIO_ROUTE_MEDIA.equals(key)) {
            if (lockSoundPlayer != null) {
                lockSoundPlayer.release();
            }
            lockSoundPlayer = new LockSoundPlayer(this);
            destroySeasonalUnlockPartnerOverlay();
            destroyUnlockEffectOverlay();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    preloadAndAttachSelectedUnlockEffectParked("prefs:audio_route");
                    evaluateVisibility("prefs:audio_route", false);
                }
            }, 100L);
            Log.i(TAG, "audio route changed to " + EffectAudio.routeLabel(this));
            return;
        }
        if (OverlayPrefs.FOLD_MODE.equals(key)
                || OverlayPrefs.TABLET_MODE.equals(key)) {
            refreshActiveDisplayTarget(OverlayPrefs.FOLD_MODE.equals(key)
                    ? "prefs_fold_mode" : "prefs_tablet_mode");
            OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this, activeTouchBoxProfile());
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
            if (isUnlockEffectEnabledForActivePanel()) {
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
            if (!isUnlockEffectEnabledForActivePanel()) {
                unloadUnlockEffects("routing:prefs:" + key);
                disableHardEffectBackgroundRecapture("effect_routing_disabled");
            } else {
                preloadUnlockEffectRenderer();
            }
        }
        if ((OverlayPrefs.SEASON_MODE.equals(key)
                || OverlayPrefs.POSITION_OFFSET_X.equals(key)
                || OverlayPrefs.POSITION_OFFSET_Y.equals(key)
                || OverlayPrefs.DOODLE_SIZE_PERCENT.equals(key)
                || OverlayPrefs.DOODLE_AOD_ENABLED.equals(key)
                || OverlayPrefs.DOODLE_AOD_BRIGHTNESS_PERCENT.equals(key)
                || OverlayPrefs.DOODLE_AOD_OPACITY_PERCENT.equals(key)
                || OverlayPrefs.DOODLE_OPACITY_PERCENT.equals(key)
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
        if (OverlayPrefs.THREE_FINGER_SAFETY_BYPASS_ENABLED.equals(key)
                && touchDebugView != null) {
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
            cancelUnlockAffordanceDispatch(false, "effect_changed");
            unlockAffordanceShownThisWake = false;
            unlockAffordancePending = isLockscreenLocked(false)
                    && (powerManager == null || powerManager.isInteractive());
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

    /**
     * Apply palette changes without tearing down the production GL context.
     */
    private void applyRippleInkPalettePreference() {
        if (!EffectAvailability.isAvailable(this, OverlayPrefs.EFFECT_RIPPLE_INK)
                || OverlayPrefs.unlockEffect(this) != OverlayPrefs.EFFECT_RIPPLE_INK) {
            return;
        }
        if (unlockEffectRenderer instanceof RippleInkPortEffectView) {
            ((RippleInkPortEffectView) unlockEffectRenderer).setInkPaletteSlot(
                    OverlayPrefs.rippleInkPalette(this));
        } else if (unlockEffectRenderer != null) {
            // A future production implementation that is recreated rather than live-updated
            // still receives the stored slot through its constructor/factory path.
            destroyUnlockEffectOverlay();
            preloadAndAttachSelectedUnlockEffectParked("prefs:ripple_ink_palette");
        }
        evaluateVisibility("prefs:ripple_ink_palette", false);
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
        serviceAlive = false;
        serviceLifecycleGeneration++;
        bootSafetyHolding = false;
        runtimeStartedAfterBootSafety = false;
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
        IntentFilter filter = createScreenReceiverFilter(true);
        try {
            registerScreenReceiverInternal(filter);
        } catch (SecurityException e) {
            // Some vendor builds treat CLOSE_SYSTEM_DIALOGS as a non-system action.
            // Keep the accessibility service alive even if that optional power-menu
            // signal is unavailable.
            Log.w(TAG, "global actions broadcast unavailable; using base receiver", e);
            registerScreenReceiverInternal(createScreenReceiverFilter(false));
        }

        IntentFilter benchmarkFilter = new IntentFilter();
        benchmarkFilter.addAction(ACTION_DEBUG_UNLOCK_EFFECT_PROFILE);
        benchmarkFilter.addAction(ACTION_DEBUG_UNLOCK_EFFECT_DEMO_GESTURE);
        benchmarkFilter.addAction(ACTION_DEBUG_UNLOCK_EFFECT_BENCHMARK);
        benchmarkFilter.addAction(ACTION_DEBUG_SET_LENS_FLARE_GLES_RENDERER);
        benchmarkFilter.addAction(ACTION_DEBUG_SET_LENS_FLARE_MODE);
        benchmarkFilter.addAction(ACTION_DEBUG_SET_DOODLE_SEASON);
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

    private IntentFilter createScreenReceiverFilter(boolean includeGlobalActions) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (includeGlobalActions) {
            filter.addAction(ACTION_CLOSE_SYSTEM_DIALOGS);
        }
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        return filter;
    }

    private void registerScreenReceiverInternal(IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
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
                || !target.cacheProfile.equals(activeDisplayProfile)
                || target.width != activeDisplayWidth
                || target.height != activeDisplayHeight;
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
        OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this, activeTouchBoxProfile());
        invalidateResolvedTouchBoxes();
        unlockEffectBackgroundGeneration++;
        // A Fold transition or rotation can preserve the logical display id while changing
        // its buffer geometry. Invalidate any screenshot callback captured for the old shape.
        lgPreLockUnderlayCaptureGeneration++;
        lgPreLockUnderlayCaptureInFlight = false;
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
            preloadUnlockEffectRenderer();
            scheduleEffectBackgroundRefreshAlarm("display_target");
            evaluateVisibility("display_target:" + reason, false);
        }
        return true;
    }

    private Context rendererContext() {
        return activeDisplayContext == null ? this : activeDisplayContext;
    }

    private String activeTouchBoxProfile() {
        return activeDisplayProfile;
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

    private int setUnlockEffectFallbackInternally(int failedEffect, String reason) {
        if (OverlayPrefs.randomUnlockEffectEnabled(this)) {
            OverlayPrefs.useRandomUnlockEffectFallback(this);
            Log.e(TAG, "random renderer failed; using S3 None for this cycle"
                    + " failedType=" + failedEffect
                    + " reason=" + reason);
            return OverlayPrefs.EFFECT_S3_NONE;
        }
        setUnlockEffectPreferenceInternally(OverlayPrefs.EFFECT_S4_LENS_FLARE);
        Log.e(TAG, "renderer failed; using Lens Flare"
                + " failedType=" + failedEffect
                + " reason=" + reason);
        return OverlayPrefs.EFFECT_S4_LENS_FLARE;
    }

    private UnlockEffectRenderer createUnlockEffectFallbackRenderer(int effect) {
        return effect == OverlayPrefs.EFFECT_S3_NONE
                ? new NoneCircleUnlockEffectView(rendererContext())
                : new LensFlareEffectView(rendererContext());
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
        // Keep tester/debug selection aligned with the actual flavor/ABI availability table.
        return EffectAvailability.isAvailable(effect);
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
        notificationShadeOemPositiveLoggedForCurrentVisibility = false;
        notificationShadeLastSeenAt = 0L;
        boolean backgroundCaptureOwnsWake =
                shouldSuppressWakeSurfacesForBackgroundCapture();
        if (backgroundCaptureOwnsWake) {
            detachRuntimeSurfacesForBackgroundCapture("interactive_wake");
        } else if (cached) {
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
        lockscreenSessionGeneration++;
        lockscreenSessionPollingStartedAt = SystemClock.uptimeMillis();
        nextContentAwarePollAt = lockscreenSessionPollingStartedAt
                + LOCKSCREEN_SESSION_INITIAL_CONTENT_DELAY_MS;
        handler.removeCallbacks(lockscreenSessionPollRunnable);
        handler.post(lockscreenSessionPollRunnable);
    }

    private void stopLockscreenSessionPolling() {
        lockscreenSessionPolling = false;
        lockscreenSessionGeneration++;
        blockedSurfaceScanInFlight = false;
        blockedSurfaceScanInFlightRequestId = ++blockedSurfaceScanRequestGeneration;
        lastBlockedSurfaceScanRequestedAt = 0L;
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
        int effect = OverlayPrefs.unlockEffect(this);
        return screenOffOrLocked
                && OverlayPrefs.masterEnabled(this)
                && !isRuntimeSurfaceBlocked()
                && isUnlockEffectAllowedNowForActivePanel()
                && (!effectUsesCachedScreenshotBackground(effect)
                || hasUnlockEffectBackgroundSource(effect));
    }

    private boolean shouldKeepNativePhysicsOverlayAttachedDuringHide(int effect) {
        // Screenshot-backed effects benefit from keeping their attached
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
                && !isRuntimeSurfaceBlockPackage(value)
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
                || effect == OverlayPrefs.EFFECT_N4_INK_IN_WATER
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || (!EffectAvailability.is64BitProcess()
                && (effect == OverlayPrefs.EFFECT_BRILLIANT_RING
                || effect == OverlayPrefs.EFFECT_BRILLIANT_CUT));
    }

    private boolean isRecreatableNativeEffect(int effect) {
        return isSamsungLockBgEffect(effect)
                || effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP
                || effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET
                || effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED;
    }

    private void markNativeRendererStaleForDisplaySize() {
        int effect = unlockEffectRendererType;
        boolean arm64AbstractTiles = EffectAvailability.is64BitProcess()
                && effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES;
        boolean arm32BrilliantRing = !EffectAvailability.is64BitProcess()
                && effect == OverlayPrefs.EFFECT_BRILLIANT_RING;
        boolean arm32BrilliantCut = !EffectAvailability.is64BitProcess()
                && effect == OverlayPrefs.EFFECT_BRILLIANT_CUT;
        if (!arm64AbstractTiles
                && !arm32BrilliantRing
                && !arm32BrilliantCut
                && effect != OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                && effect != OverlayPrefs.EFFECT_N4_INK_IN_WATER
                && effect != OverlayPrefs.EFFECT_WATERCOLOUR
                && effect != OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                && effect != OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP
                && effect != OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP
                && effect != OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                && effect != OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                && effect != OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP
                && effect != OverlayPrefs.EFFECT_S6_WATER_DROPLET
                && effect != OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED) {
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
        cancelPinEntryHandoff("blocked_surface_state_cleared");
        pinEntryPending = false;
        pinEntryRequested = false;
        pinEntrySurfaceSeen = false;
        pinEntrySurfaceVisible = false;
        notificationShadeVisible = false;
        notificationShadeSuspected = false;
        notificationShadeProbePending = false;
        notificationShadeSuspectedAt = 0L;
        handler.removeCallbacks(notificationShadeProbeRecheckRunnable);
        notificationShadeOemPositiveLoggedForCurrentVisibility = false;
        pinEntryLastSeenAt = 0L;
        notificationShadeLastSeenAt = 0L;
        notificationShadeLastClearScanAt = 0L;
        notificationShadeClearSuccessCount = 0;
        notificationShadeNeedsExtendedScan = false;
        clearPinEntryTrace();
    }

    private boolean isNotificationShadeInputBlocked() {
        return notificationShadeSuspected || notificationShadeVisible;
    }

    private void armNotificationShadeProbe(String reason, boolean neutralizeTouch) {
        notificationShadeProbePending = true;
        if (neutralizeTouch) {
            // Slow SystemUI implementations may not fit the normal bounded tree scan.
            // Give the first authoritative shade probe enough time to either confirm the
            // panel or clear the safety latch instead of waiting for a lucky cached tree.
            notificationShadeNeedsExtendedScan = true;
        }
        if (!neutralizeTouch || notificationShadeVisible || notificationShadeSuspected) {
            return;
        }
        notificationShadeSuspected = true;
        notificationShadeSuspectedAt = SystemClock.uptimeMillis();
        handler.removeCallbacks(notificationShadeProbeRecheckRunnable);
        handler.postDelayed(notificationShadeProbeRecheckRunnable,
                NOTIFICATION_SHADE_PROBE_RECHECK_MS);
        setTouchDebugSafetyBlocked(true);
        Log.i(TAG, "notification shade probe armed reason=" + reason);
    }

    private void recheckUnconfirmedNotificationShadeProbe() {
        if (!notificationShadeSuspected || notificationShadeVisible) {
            return;
        }
        if (blockedSurfaceScanInFlight) {
            handler.postDelayed(notificationShadeProbeRecheckRunnable,
                    NOTIFICATION_SHADE_SCAN_MIN_INTERVAL_MS);
            return;
        }
        notificationShadeProbePending = true;
        requestContentBlockedSurfaceScan("notification_shade_probe_recheck");
        handler.postDelayed(notificationShadeProbeRecheckRunnable,
                NOTIFICATION_SHADE_PROBE_RECHECK_MS);
    }

    private void confirmNotificationShade(String reason) {
        long now = SystemClock.uptimeMillis();
        boolean changed = !notificationShadeVisible;
        notificationShadeVisible = true;
        notificationShadeSuspected = false;
        notificationShadeProbePending = false;
        notificationShadeSuspectedAt = 0L;
        handler.removeCallbacks(notificationShadeProbeRecheckRunnable);
        notificationShadeLastSeenAt = now;
        notificationShadeLastClearScanAt = 0L;
        notificationShadeClearSuccessCount = 0;
        notificationShadeNeedsExtendedScan = false;
        if (changed) {
            invalidatePendingInputForNotificationShade();
            Log.i(TAG, "notification shade confirmed reason=" + reason);
        }
        removeTouchDebugOverlay();
        removeUnlockEffectOverlay();
    }

    private void invalidatePendingInputForNotificationShade() {
        inputSafetyGeneration++;
        handler.removeCallbacks(pinEntryRunnable);
        handler.removeCallbacks(pinEntrySwipeRunnable);
        cancelPinEntryHandoff("notification_shade");
        pinEntryPending = false;
        pinEntryRequested = false;
        pinEntrySurfaceSeen = false;
        pinEntrySurfaceVisible = false;
        clearPinEntryTrace();
        cancelBufferedReadinessGesture("notification_shade", false);
    }

    private void setTouchDebugSafetyBlocked(boolean blocked) {
        if (touchDebugView == null || touchDebugParams == null) {
            return;
        }
        for (int i = 0; i < touchDebugWindowCount(); i++) {
            touchDebugViewAt(i).setListeningEnabled(!blocked);
        }
        updateTouchDebugLayouts(resolveTouchBoxes(), !blocked);
    }

    private void showGlobalActionsSuppression() {
        boolean wasVisible = globalActionsVisible;
        globalActionsVisible = true;
        globalActionsShownAt = SystemClock.uptimeMillis();
        handler.removeCallbacks(globalActionsFallbackClearRunnable);
        handler.postDelayed(
                globalActionsFallbackClearRunnable,
                GLOBAL_ACTIONS_FALLBACK_CLEAR_MS);
        if (!wasVisible) {
            unlockAffordancePending = false;
            unlockTouchCachedWhileScreenOff = false;
            hideRuntimeSurfacesForBlockedPackage("global_actions");
            Log.i(TAG, "global actions visible=true");
            evaluateVisibility("global_actions:shown", false);
        }
    }

    private void clearGlobalActionsSuppression(String reason, boolean evaluate) {
        handler.removeCallbacks(globalActionsFallbackClearRunnable);
        if (!globalActionsVisible) {
            globalActionsShownAt = 0L;
            return;
        }
        globalActionsVisible = false;
        globalActionsShownAt = 0L;
        Log.i(TAG, "global actions suppression cleared reason=" + reason);
        if (evaluate) {
            evaluateVisibility("global_actions:cleared:" + reason, false);
        }
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
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
    }

    private void logNotificationShadeDiagnosticEnvironment() {
        AccessibilityServiceInfo serviceInfo = getServiceInfo();
        Log.i(TAG, "shade diagnostic environment"
                + " manufacturer=" + Build.MANUFACTURER
                + " brand=" + Build.BRAND
                + " model=" + Build.MODEL
                + " device=" + Build.DEVICE
                + " product=" + Build.PRODUCT
                + " sdk=" + Build.VERSION.SDK_INT
                + " release=" + Build.VERSION.RELEASE
                + " securityPatch=" + Build.VERSION.SECURITY_PATCH
                + " display=" + Build.DISPLAY
                + " fingerprint=" + Build.FINGERPRINT
                + " locale=" + Locale.getDefault().toLanguageTag()
                + " systemUi=" + packageVersionSummary(SYSTEM_UI_PACKAGE)
                + " serviceFlags=" + (serviceInfo == null ? -1 : serviceInfo.flags));
    }

    private String packageVersionSummary(String packageName) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            return packageName + "/"
                    + (info.versionName == null ? "unknown" : info.versionName)
                    + "(" + versionCode + ")";
        } catch (PackageManager.NameNotFoundException error) {
            return packageName + "/unavailable";
        } catch (RuntimeException error) {
            return packageName + "/error:" + error.getClass().getSimpleName();
        }
    }

    private void evaluateVisibility(String reason) {
        evaluateVisibility(reason, true);
    }

    private void evaluateVisibility(String reason, boolean contentAware) {
        if (holdRuntimeForBootSafety("visibility:" + reason)) {
            return;
        }
        if (!runtimeStartedAfterBootSafety) {
            startRuntimeAfterBootSafety("visibility:" + reason);
            return;
        }
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = isLockscreenLocked(contentAware);
        if (!locked && activeRuntimeBlockPackage != null) {
            clearActiveRuntimeBlock("keyguard_unlocked");
        }
        long sinceScreenOnMs = elapsedSinceScreenOn();
        if (!interactiveSessionWasUnlocked
                && interactive
                && !locked
                && sinceScreenOnMs >= LOCK_SOUND_UNLOCK_CONFIRM_MS) {
            interactiveSessionWasUnlocked = true;
            Log.i(TAG, "lock sound armed reason=stable_unlocked_session"
                    + " sinceScreenOnMs=" + sinceScreenOnMs);
            scheduleRandomUnlockAdvance("stable_unlocked_session");
        }
        boolean home = interactive && !locked && isHomePackage(lastWindowPackage);
        boolean blockedPackageSurface = isRuntimeSurfaceBlocked();
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
        if (blockedPackageSurface) {
            unlockAffordancePending = false;
            unlockTouchCachedWhileScreenOff = false;
            hideRuntimeSurfacesForBlockedPackage(reason);
            if (shouldLogVisibility(reason)) {
                Log.i(TAG, "visibility reason=" + reason
                        + " showDoodle=false showFx=false"
                        + " blockedPackageSurface=true"
                        + " charging=" + charging
                        + " interactive=" + interactive
                        + " locked=" + locked
                        + " pkg=" + lastWindowPackage);
            }
            return;
        }
        if (shouldShowFrozenDoodleAod(interactive, locked, displayState)) {
            cancelUnlockAffordanceDispatch(false, "frozen_doodle_aod");
            unlockAffordancePending = false;
            unlockFxVisible = false;
            ensureDoodleLoaded();
            if (!doodleOverlayAttached) {
                syncDoodleOverlay();
            }
            if (overlayView != null && doodleOverlayAttached) {
                overlayView.setWarmParked(true);
                overlayView.setAodFrozen(true);
                overlayView.setKeepScreenOn(false);
                overlayView.setAodBrightness(
                        OverlayPrefs.doodleAodBrightnessPercent(this) / 100f);
                overlayView.setAlpha(
                        OverlayPrefs.doodleAodOpacityPercent(this) / 100f);
                overlayView.setVisibility(View.VISIBLE);
                doodleOverlayParked = true;
            }
            parkUnlockEffectOverlayForScreenOff();
            destroySeasonalUnlockPartnerOverlay();
            removeTouchDebugOverlay();
            Log.i(TAG, "visibility reason=" + reason
                    + " showDoodle=frozen_aod showFx=false"
                    + " charging=" + charging
                    + " interactive=" + interactive
                    + " locked=" + locked
                    + " displayState=" + displayState);
            return;
        }
        if (overlayView != null) {
            overlayView.setAodFrozen(false);
            overlayView.setKeepScreenOn(false);
            overlayView.setAodBrightness(1f);
        }        if (interactive) {
            if (!lastInteractive) {
                lastInteractive = true;
                handleInteractiveLockscreenWake(reason, locked);
            } else if (locked && unlockTouchCachedWhileScreenOff && !unlockAffordancePending) {
                handleInteractiveLockscreenWake(reason, locked);
            }
        } else {
            lastInteractive = false;
        }
        if (!interactive && locked && !unlockTouchCachedWhileScreenOff) {
            // On rapid unlock -> power-off -> wake cycles Samsung can publish the display-off
            // state before ACTION_SCREEN_OFF reaches this service. Prearm from that stronger
            // state immediately so the accessibility input handle already exists at wake.
            cacheUnlockTouchForScreenOff();
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
        if (contentAware && interactive && locked) {
            requestContentBlockedSurfaceScan(reason);
        }        if (!interactive || !locked) {
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
                pinEntrySurfaceVisible, notificationShadeVisible, blockedPackageSurface);
        boolean blockedSurfaceActive =
                pinEntryActive || notificationShadeSuspected
                        || notificationShadeVisible || blockedPackageSurface;
        boolean touchBoxCapturePending = isTouchBoxScreenshotPending();
        boolean hideOverlaysForTouchBoxCapture = touchBoxCapturePending && interactive && locked;
        boolean aodSurface = isActualAodSurface(interactive, displayOn);
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
        boolean backgroundPreflightNeeded = shouldRunUnlockEffectBackgroundPreflight(
                interactive,
                displayOn,
                locked,
                aodSurface,
                hideOverlaysForTouchBoxCapture,
                false,
                blockedSurfaceActive);
        boolean hideOverlaysForBackgroundCapture =
                unlockEffectAllowedForActivePanel
                        && (OverlayPrefs.effectBackgroundWakeCaptureActive(this)
                        || colorScreenshotInFlight
                        || backgroundBootstrapNeeded
                        || backgroundPreflightNeeded) && interactive && locked;
        syncTouchBoxScreenshotCapture(reason, interactive, locked, blockedSurfaceActive);

        if (hideOverlaysForBackgroundCapture) {
            // A few native renderers normally stay attached at alpha 0 while parked.
            // Screenshot capture needs every LLE window to be truly detached and absent
            // from a presented frame before AccessibilityService.takeScreenshot().
            detachRuntimeSurfacesForBackgroundCapture("visibility:" + reason);
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
        boolean showDoodle = !lockCycleSafetyBypassActive
                && runtimeSurfaceAllowed && isChargingDoodleModeEnabled();
        boolean showFx = !lockCycleSafetyBypassActive
                && runtimeSurfaceAllowed
                && unlockEffectAllowedForActivePanel;

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
                    + " globalActions=" + globalActionsVisible
                    + " blockedPackageSurface=" + blockedPackageSurface
                    + " touchBoxCapture=" + touchBoxCapturePending
                    + " home=" + home
                    + " pkg=" + lastWindowPackage);
        }
    }

    private boolean shouldLogVisibility(String reason) {
        return reason == null
                || (!reason.startsWith("lockscreen_poll")
                && !reason.startsWith("lockscreen_exit_poll")
                && !reason.startsWith("async_surface_scan"));
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
            overlayView.setAlpha(OverlayPrefs.doodleOpacityPercent(this) / 100f);
            overlayView.setVisibility(View.VISIBLE);
            if (doodleOverlayParked) {
                doodleOverlayParked = false;
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
        overlayView.setAlpha(OverlayPrefs.doodleOpacityPercent(this) / 100f);
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
        boolean displayOn =
                displayState == Display.STATE_UNKNOWN || displayState == Display.STATE_ON;
        return !interactive
                || !displayOn
                || isActualAodSurface(interactive, displayOn);
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
        unlockEffectWindowParams = params;
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
            unlockEffectWindowParams = null;
            unlockEffectWindowNeutralizedForHandoff = false;
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
        bringDoodleOverlayAboveUnlockEffect();
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

    private void bringDoodleOverlayAboveUnlockEffect() {
        if (!unlockEffectOverlayAttached
                || !doodleOverlayAttached
                || overlayView == null) {
            return;
        }
        boolean wasParked = doodleOverlayParked;
        removeDoodleOverlay();
        if (wasParked) {
            parkDoodleOverlayForWarmth("z_order_above_unlock_effect");
        } else {
            syncDoodleOverlay();
        }
        Log.i(TAG, "doodle overlay raised above unlock effect"
                + " parked=" + wasParked
                + " effect=" + unlockEffectRendererType);
    }

    private void refreshRandomUnlockEffectAfterPreferenceChange() {
        if (!serviceAlive || !runtimeStartedAfterBootSafety) {
            return;
        }
        if (!OverlayPrefs.randomUnlockEffectEnabled(this)) {
            handler.removeCallbacks(randomUnlockAdvanceRunnable);
            randomUnlockAdvancePending = false;
            randomUnlockAdvanceReason = "";
        }
        int resolvedEffect = OverlayPrefs.unlockEffect(this);
        cancelUnlockAffordanceDispatch(false, "random_preferences");
        unlockAffordanceShownThisWake = false;
        unlockAffordancePending = isLockscreenLocked(false)
                && (powerManager == null || powerManager.isInteractive());
        cancelBufferedReadinessGesture("random_preferences", false);
        if (unlockEffectRenderer != null && unlockEffectRendererType != resolvedEffect) {
            destroyUnlockEffectOverlay();
        }
        preloadAndAttachSelectedUnlockEffectParked("random_preferences");
        evaluateVisibility("prefs:random_unlock", false);
        Log.i(TAG, "random preferences applied enabled="
                + OverlayPrefs.randomUnlockEffectEnabled(this)
                + " resolved=" + resolvedEffect
                + " pool=" + OverlayPrefs.randomUnlockEffectPool(this).size());
    }

    private void scheduleRandomUnlockAdvance(String reason) {
        if (!OverlayPrefs.randomUnlockEffectEnabled(this)
                || randomUnlockAdvancePending) {
            return;
        }
        randomUnlockAdvancePending = true;
        randomUnlockAdvanceReason = reason == null ? "confirmed_unlock" : reason;
        handler.postDelayed(randomUnlockAdvanceRunnable,
                RANDOM_UNLOCK_NEXT_PRELOAD_DELAY_MS);
        Log.i(TAG, "random advance scheduled reason=" + randomUnlockAdvanceReason
                + " delayMs=" + RANDOM_UNLOCK_NEXT_PRELOAD_DELAY_MS
                + " current=" + OverlayPrefs.unlockEffect(this));
    }

    private void advancePendingRandomUnlockEffectForScreenOff() {
        if (!randomUnlockAdvancePending) {
            return;
        }
        handler.removeCallbacks(randomUnlockAdvanceRunnable);
        randomUnlockAdvancePending = false;
        String scheduledReason = randomUnlockAdvanceReason;
        randomUnlockAdvanceReason = "";
        if (!OverlayPrefs.randomUnlockEffectEnabled(this)) {
            return;
        }
        int previous = OverlayPrefs.unlockEffect(this);
        int next = OverlayPrefs.advanceRandomUnlockEffect(this);
        if (unlockEffectRenderer != null && unlockEffectRendererType != next) {
            destroyUnlockEffectOverlay();
        }
        scheduleEffectBackgroundRefreshAlarm("random_next:rapid_relock");
        Log.i(TAG, "random effect advanced reason=rapid_relock"
                + " scheduledFrom=" + scheduledReason
                + " previous=" + previous
                + " next=" + next
                + " pool=" + OverlayPrefs.randomUnlockEffectPool(this).size());
    }

    private void advanceAndPreloadRandomUnlockEffect(String reason) {
        if (!serviceAlive || !OverlayPrefs.randomUnlockEffectEnabled(this)) {
            return;
        }
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (!interactive || isLockscreenLocked(false)) {
            Log.i(TAG, "random advance skipped reason=" + reason
                    + " interactive=" + interactive
                    + " locked=" + isLockscreenLocked(false));
            return;
        }
        int previous = OverlayPrefs.unlockEffect(this);
        int next = OverlayPrefs.advanceRandomUnlockEffect(this);
        if (unlockEffectRenderer != null && unlockEffectRendererType != next) {
            destroyUnlockEffectOverlay();
        }
        preloadAndAttachSelectedUnlockEffectParked("random_next:" + reason);
        scheduleEffectBackgroundRefreshAlarm("random_next:" + reason);
        Log.i(TAG, "random effect advanced reason=" + reason
                + " previous=" + previous
                + " next=" + next
                + " pool=" + OverlayPrefs.randomUnlockEffectPool(this).size());
    }

    private void preloadAndAttachSelectedUnlockEffectParked(String reason) {
        if (!OverlayPrefs.masterEnabled(this)
                || !isUnlockEffectEnabledForActivePanel()) {
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
        if (unlockEffectRendererType == OverlayPrefs.EFFECT_RIPPLE_INK
                && unlockEffectRenderer instanceof RippleInkPortEffectView
                && unlockEffectReadinessState >= UnlockEffectReadiness.STATE_FIRST_FRAME_READY
                && !((RippleInkPortEffectView) unlockEffectRenderer).isProductionReady()) {
            unlockEffectReadinessState = UnlockEffectReadiness.STATE_FAILED;
            unlockEffectReadinessDetail = "ripple_ink_production_gate_not_ready";
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
                showPendingUnlockAffordance("readiness_ready");
            } else if (unlockEffectOverlayParked && unlockEffectView != null) {
                // A zero-alpha HWUI root can be culled before Lens Flare builds its first
                // display list. The parked prewarm uses WARM_PARK_ALPHA only until the renderer
                // acknowledges its first transparent frame, then becomes fully invisible.
                unlockEffectView.setAlpha(0f);
            }
        } else if (unlockEffectReadinessState == UnlockEffectReadiness.STATE_FAILED
                && unlockEffectRendererType != OverlayPrefs.EFFECT_S4_LENS_FLARE
                && !unlockEffectReadinessFallbackScheduled) {
            unlockEffectReadinessFallbackScheduled = true;
            handler.post(unlockEffectReadinessFailureRunnable);
        }
    }

    private void handleUnlockEffectReadinessFailure() {
        unlockEffectReadinessFallbackScheduled = false;
        if (!serviceAlive
                || unlockEffectReadinessState != UnlockEffectReadiness.STATE_FAILED
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S4_LENS_FLARE) {
            return;
        }
        int failedEffect = unlockEffectRendererType;
        Log.e(TAG, "renderer readiness failed type="
                + failedEffect + " detail=" + unlockEffectReadinessDetail);
        destroyUnlockEffectOverlay();
        setUnlockEffectFallbackInternally(failedEffect, "readiness_failed");
        preloadAndAttachSelectedUnlockEffectParked("readiness_failed");
        evaluateVisibility("renderer_readiness_failed", false);
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
        long startedAt = SystemClock.uptimeMillis();
        int effect = OverlayPrefs.unlockEffect(this);
        boolean rearmAffordanceForReplacement = false;
        if (!EffectAvailability.isAvailable(this, effect)) {
            Log.e(TAG, "selected effect is unavailable in build flavor "
                    + EffectAvailability.buildFlavorLabel()
                    + "; applying safe fallback type=" + effect);
            effect = setUnlockEffectFallbackInternally(effect, "unavailable_in_build");
        }
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
                        + " blockedPackageSurface=" + isRuntimeSurfaceBlocked());
                return;
            }
            Log.i(TAG, "native lockbg renderer recreating reason="
                    + unlockEffectRendererRecreateReason
                    + " type=" + effect);
            rearmAffordanceForReplacement = unlockAffordanceShownThisWake
                    && unlockAffordanceDeliveredRenderer == unlockEffectRenderer;
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
                            new S3Arm64RippleEffectView(rendererContext(), false,
                                    OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(
                                            this, effect));
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException("Water Ripple ARM64 renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                } else {
                    unlockEffectRenderer = new S3NativeRippleEffectView(rendererContext());
                }
            } else if (effect == OverlayPrefs.EFFECT_N4_INK_IN_WATER) {
                if (EffectAvailability.is64BitProcess()) {
                    S3Arm64RippleEffectView renderer =
                            new S3Arm64RippleEffectView(rendererContext(), true, false);
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException("Ink in Water ARM64 renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                } else {
                    unlockEffectRenderer =
                            SamsungLockBgEffectView.indigoDiffusion(rendererContext());
                }
            } else if (effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES) {
                if (EffectAvailability.is64BitProcess()) {
                    AbstractTilesArm64EffectView renderer =
                            new AbstractTilesArm64EffectView(rendererContext(),
                                    OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(
                                            this, effect));
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
                            new GeometricMosaicArm64EffectView(rendererContext(),
                                    OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(
                                            this, effect));
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
                if (EffectAvailability.is64BitProcess()) {
                    unlockEffectRenderer = new PoppingColoursArm64EffectView(rendererContext(),
                            OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect),
                            OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(
                                    this, effect));
                } else {
                    unlockEffectRenderer = new PoppingColoursEffectView(rendererContext());
                }
            } else if (effect == OverlayPrefs.EFFECT_TABS_BLIND) {
                if (EffectAvailability.is64BitProcess()) {
                    BlindArm64EffectView renderer =
                            new BlindArm64EffectView(rendererContext());
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Tab S Blind ARM64 renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                } else {
                    BlindDexEffectView renderer =
                            new BlindDexEffectView(rendererContext());
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Tab S Blind DEX renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                }
            } else if (effect == OverlayPrefs.EFFECT_STONE_SKIPPING) {
                unlockEffectRenderer = new StoneSkippingEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_MASS_TENSION) {
                unlockEffectRenderer = new MassTensionEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_S3_NONE) {
                unlockEffectRenderer = new NoneCircleUnlockEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS) {
                unlockEffectRenderer = new XperiaBlindsEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_LG_G2_PIXELATE) {
                unlockEffectRenderer = new LgPixelateEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_LG_G2_PARTICLE) {
                unlockEffectRenderer = new G2ParticleEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_LG_G2_CRYSTAL) {
                unlockEffectRenderer = new CrystalPrismBetaEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_REVOLVING_GLASS) {
                unlockEffectRenderer = new RevolvingGlassEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_LG_G1_WHITE_HOLE) {
                unlockEffectRenderer = new LgWhiteHoleEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_LG_SODA) {
                unlockEffectRenderer = new LgSodaEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_LG_G1_DEWDROP) {
                unlockEffectRenderer = new LgDewdropEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE) {
                unlockEffectRenderer = new LgLightParticleEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_LG_G2_VECTOR) {
                unlockEffectRenderer = new LgVectorEffectView(rendererContext());
            } else if (effect == OverlayPrefs.EFFECT_RIPPLE_INK) {
                unlockEffectRenderer = new RippleInkPortEffectView(
                        rendererContext(),
                        OverlayPrefs.rippleInkPalette(this),
                        OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect));
            } else if (effect == OverlayPrefs.EFFECT_GOOD_LOCK_POPPING) {
                unlockEffectRenderer = new GoodLockParticleEffectView(rendererContext(),
                        GoodLockParticleEffectView.Variant.POPPING,
                        OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect),
                        OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(
                                this, effect));
            } else if (effect == OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE) {
                unlockEffectRenderer = new GoodLockParticleEffectView(rendererContext(),
                        GoodLockParticleEffectView.Variant.RECTANGLE,
                        OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect),
                        OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(
                                this, effect));
            } else if (effect == OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING) {
                unlockEffectRenderer = new GoodLockParticleEffectView(rendererContext(),
                        GoodLockParticleEffectView.Variant.BOUNCING,
                        OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect),
                        OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(
                                this, effect));
            } else if (effect == OverlayPrefs.EFFECT_BRILLIANT_RING) {
                if (EffectAvailability.is64BitProcess()) {
                    unlockEffectRenderer = new BrilliantRingEffectView(rendererContext(),
                            OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect));
                } else {
                    unlockEffectRenderer =
                            SamsungLockBgEffectView.brilliantRing(rendererContext());
                }
            } else if (effect == OverlayPrefs.EFFECT_BRILLIANT_CUT) {
                if (EffectAvailability.is64BitProcess()) {
                    unlockEffectRenderer = new BrilliantCutEffectView(rendererContext(),
                            OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect));
                } else {
                    unlockEffectRenderer =
                            SamsungLockBgEffectView.brilliantCut(rendererContext());
                }
            } else if (OverlayPrefs.isSeasonalUnlockEffect(effect)) {
                unlockEffectRenderer = new SeasonalUnlockEffectView(
                        rendererContext(),
                        OverlayPrefs.seasonForUnlockEffect(effect),
                        false);
            } else if (effect == OverlayPrefs.EFFECT_WATERCOLOUR) {
                WatercolorArm64EffectView renderer =
                        new WatercolorArm64EffectView(rendererContext(),
                                OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(
                                        this, effect));
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException("Watercolor native renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else if (effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET) {
                if (!EffectAvailability.is64BitProcess()) {
                    throw new IllegalStateException(
                            "S6 Water Droplet requires the ARM64 product");
                }
                S6WaterDropletEffectView renderer =
                        new S6WaterDropletEffectView(rendererContext());
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException(
                            "S6 Water Droplet ARM64 renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else if (effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED) {
                if (!EffectAvailability.is64BitProcess()) {
                    throw new IllegalStateException(
                            "App-owned S6 Water Droplet requires ARM64");
                }
                S6WaterDropletAppOwnedEffectView renderer =
                        new S6WaterDropletAppOwnedEffectView(rendererContext(),
                                OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect),
                                OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(
                                        this, effect));
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException(
                            "App-owned S6 Water Droplet renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET) {
                if (EffectAvailability.is64BitProcess()) {
                    ColourDropletArm64EffectView renderer =
                            new ColourDropletArm64EffectView(rendererContext(), false);
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Colour Droplet ARM64 renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                } else {
                    ColourDropletEffectView renderer =
                            new ColourDropletEffectView(rendererContext(), false);
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Colour Droplet native renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                }
            } else if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO) {
                if (EffectAvailability.is64BitProcess()) {
                    ColourDropletArm64EffectView renderer =
                            new ColourDropletArm64EffectView(rendererContext(), true);
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Colour Droplet + Gyro ARM64 renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                } else {
                    ColourDropletEffectView renderer =
                            new ColourDropletEffectView(rendererContext(), true);
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Colour Droplet + Gyro native renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                }
            } else if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP) {
                if (!EffectAvailability.is64BitProcess()) {
                    throw new IllegalStateException(
                            "App-owned Colored Droplet requires ARM64");
                }
                ColourDropletAppOwnedEffectView renderer =
                        new ColourDropletAppOwnedEffectView(rendererContext(), false,
                                OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect),
                                OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(
                                        this, effect));
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException(
                            "App-owned Colored Droplet renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else if (effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP) {
                if (!EffectAvailability.is64BitProcess()) {
                    throw new IllegalStateException(
                            "App-owned Colored Droplet (Gyro) requires ARM64");
                }
                ColourDropletAppOwnedEffectView renderer =
                        new ColourDropletAppOwnedEffectView(rendererContext(), true,
                                OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect),
                                OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(
                                        this, effect));
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException(
                            "App-owned Colored Droplet (Gyro) renderer unavailable");
                }
                unlockEffectRenderer = renderer;
            } else if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES) {
                if (EffectAvailability.is64BitProcess()) {
                    SparklingBubblesArm64EffectView renderer =
                            new SparklingBubblesArm64EffectView(rendererContext());
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Sparkling Bubbles ARM64 renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                } else {
                    SparklingBubblesEffectView renderer =
                            new SparklingBubblesEffectView(rendererContext());
                    if (!renderer.isReady()) {
                        renderer.destroy();
                        throw new IllegalStateException(
                                "Sparkling Bubbles native renderer unavailable");
                    }
                    unlockEffectRenderer = renderer;
                }
            } else if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP) {
                if (!EffectAvailability.is64BitProcess()) {
                    throw new IllegalStateException(
                            "App-owned Sparkling Bubbles requires ARM64");
                }
                SparklingBubblesAppOwnedEffectView renderer =
                        new SparklingBubblesAppOwnedEffectView(rendererContext(),
                                OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect),
                                OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(
                                        this, effect));
                if (!renderer.isReady()) {
                    renderer.destroy();
                    throw new IllegalStateException(
                            "App-owned Sparkling Bubbles renderer unavailable");
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
            effect = setUnlockEffectFallbackInternally(failedEffect, "renderer_preload");
            unlockEffectRendererType = effect;
            try {
                unlockEffectRenderer = createUnlockEffectFallbackRenderer(effect);
                Log.w(TAG, "native renderer fallback ready failedType="
                        + failedEffect + " fallbackType=" + effect);
            } catch (Throwable fallbackError) {
                Log.e(TAG, "unlock effect fallback failed", fallbackError);
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
            if (effect == OverlayPrefs.EFFECT_S4_LENS_FLARE) {
                return;
            }
            int failedEffect = effect;
            effect = setUnlockEffectFallbackInternally(failedEffect, "renderer_view");
            unlockEffectRendererType = effect;
            try {
                unlockEffectRenderer = createUnlockEffectFallbackRenderer(effect);
                unlockEffectView = unlockEffectRenderer.asView();
                Log.w(TAG, "renderer view fallback ready failedType="
                        + failedEffect + " fallbackType=" + effect);
            } catch (Throwable fallbackError) {
                Log.e(TAG, "unlock effect view fallback failed", fallbackError);
                if (unlockEffectRenderer != null) {
                    try {
                        unlockEffectRenderer.destroy();
                    } catch (Throwable ignored) {
                    }
                }
                unlockEffectRenderer = null;
                unlockEffectView = null;
                unlockEffectRendererType = -1;
                return;
            }
        }
        if (rearmAffordanceForReplacement) {
            unlockAffordanceShownThisWake = false;
            unlockAffordancePending = true;
            Log.i(TAG, "unlock affordance rearmed for replacement renderer type=" + effect);
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
        installTesterSyntheticColormapIfNeeded(effect);
        loadCachedUnlockEffectBackgroundSourceIfNeeded(effect);
        long cacheMs = SystemClock.uptimeMillis() - cacheStartedAt;
        if (OverlayPrefs.supportsExperimentalNativeRefreshPhysics(effect)) {
            Log.i(TAG, "native refresh physics selected mode="
                    + (OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this, effect)
                    ? "display_refresh_experimental" : "legacy_60hz")
                    + " speedMultiplier="
                    + OverlayPrefs.experimentalNativeRefreshPhysicsSpeedMultiplier(this, effect)
                    + " effect=" + effect);
        }
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
        boolean ripple = (unlockEffectRendererType == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || unlockEffectRendererType == OverlayPrefs.EFFECT_N4_INK_IN_WATER)
                && unlockEffectRenderer instanceof S3Arm64RippleEffectView;
        boolean abstractTiles =
                unlockEffectRendererType == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                && unlockEffectRenderer instanceof AbstractTilesArm64EffectView;
        boolean geometricMosaic =
                unlockEffectRendererType == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                && unlockEffectRenderer instanceof GeometricMosaicArm64EffectView;
        boolean brilliantRing =
                unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_RING
                && unlockEffectRenderer instanceof BrilliantRingEffectView;
        boolean brilliantCut =
                unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_CUT
                && unlockEffectRenderer instanceof BrilliantCutEffectView;
        if (ripple || abstractTiles || geometricMosaic || brilliantRing || brilliantCut) {
            handler.postDelayed(rippleRendererReadinessRunnable, 250L);
        }
    }

    private void fallBackFromFailedRippleRenderer(String reason) {
        if (unlockEffectRendererType != OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                && unlockEffectRendererType != OverlayPrefs.EFFECT_N4_INK_IN_WATER) {
            return;
        }
        Log.e(TAG, OverlayPrefs.effectLabel(unlockEffectRendererType)
                + " failed; applying safe fallback reason=" + reason);
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        int failedEffect = unlockEffectRendererType;
        destroyUnlockEffectOverlay();
        setUnlockEffectFallbackInternally(failedEffect, reason);
        preloadUnlockEffectRenderer();
        evaluateVisibility("ripple_renderer_failed", false);
    }

    private void fallBackFromFailedAbstractTilesRenderer(String reason) {
        if (unlockEffectRendererType != OverlayPrefs.EFFECT_S4_ABSTRACT_TILES) {
            return;
        }
        Log.e(TAG, "Abstract Tiles ARM64 failed; applying safe fallback reason="
                + reason);
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        destroyUnlockEffectOverlay();
        setUnlockEffectFallbackInternally(OverlayPrefs.EFFECT_S4_ABSTRACT_TILES, reason);
        preloadUnlockEffectRenderer();
        evaluateVisibility("abstract_tiles_renderer_failed", false);
    }

    private void fallBackFromFailedGeometricMosaicRenderer(String reason) {
        if (unlockEffectRendererType != OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC) {
            return;
        }
        Log.e(TAG, "Geometric Mosaic ARM64 failed; applying safe fallback reason="
                + reason);
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        destroyUnlockEffectOverlay();
        setUnlockEffectFallbackInternally(OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC, reason);
        preloadUnlockEffectRenderer();
        evaluateVisibility("geometric_mosaic_renderer_failed", false);
    }

    private void fallBackFromFailedBrilliantRingRenderer(String reason) {
        if (unlockEffectRendererType != OverlayPrefs.EFFECT_BRILLIANT_RING
                || !(unlockEffectRenderer instanceof BrilliantRingEffectView)) {
            return;
        }
        Log.e(TAG, "Brilliant Ring ARM64 failed; applying safe fallback reason="
                + reason);
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        destroyUnlockEffectOverlay();
        setUnlockEffectFallbackInternally(OverlayPrefs.EFFECT_BRILLIANT_RING, reason);
        preloadUnlockEffectRenderer();
        evaluateVisibility("brilliant_ring_renderer_failed", false);
    }

    private void fallBackFromFailedBrilliantCutRenderer(String reason) {
        if (unlockEffectRendererType != OverlayPrefs.EFFECT_BRILLIANT_CUT
                || !(unlockEffectRenderer instanceof BrilliantCutEffectView)) {
            return;
        }
        Log.e(TAG, "Brilliant Cut ARM64 failed; applying safe fallback reason="
                + reason);
        handler.removeCallbacks(rippleRendererReadinessRunnable);
        destroyUnlockEffectOverlay();
        setUnlockEffectFallbackInternally(OverlayPrefs.EFFECT_BRILLIANT_CUT, reason);
        preloadUnlockEffectRenderer();
        evaluateVisibility("brilliant_cut_renderer_failed", false);
    }

    private boolean canRecreateStaleLockBgRenderer() {
        return !unlockEffectGestureActive
                && !pinEntryPending
                && !pinEntryRequested
                && !pinEntrySurfaceVisible
                && !notificationShadeVisible
                && !isRuntimeSurfaceBlocked();
    }

    private void showPendingUnlockAffordance(String reason) {
        if (!unlockAffordancePending || unlockAffordanceDispatchQueued) {
            return;
        }
        if (unlockAffordanceShownThisWake) {
            unlockAffordancePending = false;
            Log.i(TAG, "unlock affordance duplicate skipped reason=" + reason
                    + " effect=" + OverlayPrefs.unlockEffect(this));
            return;
        }
        if (suppressUnlockAffordanceForActiveDoodle(reason)) {
            return;
        }
        int effect = OverlayPrefs.unlockEffect(this);
        if (!supportsUnlockAffordance(effect)) {
            unlockAffordancePending = false;
            unlockAffordanceShownThisWake = true;
            unlockAffordanceDeliveredRenderer = null;
            Log.i(TAG, "unlock affordance unsupported reason=" + reason
                    + " effect=" + effect);
            return;
        }
        if (!canDispatchUnlockAffordanceNow()) {
            return;
        }
        if (unlockAffordanceWaitingForBackground(reason)) {
            return;
        }
        unlockAffordanceDispatchQueued = true;
        unlockAffordanceDispatchGeneration++;
        handler.removeCallbacks(unlockAffordanceDispatchRunnable);
        handler.postDelayed(unlockAffordanceDispatchRunnable, UNLOCK_AFFORDANCE_DELAY_MS);
        Log.i(TAG, "unlock affordance queued reason=" + reason
                + " generation=" + unlockAffordanceDispatchGeneration
                + " delayMs=" + UNLOCK_AFFORDANCE_DELAY_MS
                + " effect=" + effect);
    }

    private boolean supportsUnlockAffordance(int effect) {
        return effect != OverlayPrefs.EFFECT_MASS_TENSION
                && !OverlayPrefs.isSeasonalUnlockEffect(effect);
    }

    private boolean canDispatchUnlockAffordanceNow() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        return interactive
                && isLockscreenLocked(false)
                && !isRuntimeSurfaceBlocked()
                && !unlockEffectGestureActive
                && unlockFxVisible
                && unlockEffectRenderer != null
                && unlockEffectView != null
                && unlockEffectOverlayAttached
                && !unlockEffectOverlayParked
                && unlockEffectView.getVisibility() == View.VISIBLE
                && unlockEffectView.getAlpha() >= 0.99f
                && isUnlockEffectFirstFrameReady()
                && !OverlayPrefs.debugLensLoop(this);
    }

    private boolean suppressUnlockAffordanceForActiveDoodle(String reason) {
        if (!isChargingDoodleModeEnabled()) {
            return false;
        }
        unlockAffordancePending = false;
        unlockAffordanceShownThisWake = true;
        unlockAffordanceDeliveredRenderer = null;
        Log.i(TAG, "unlock affordance suppressed for active doodle reason=" + reason);
        return true;
    }

    private void cancelUnlockAffordanceDispatch(boolean rearm, String reason) {
        boolean wasQueued = unlockAffordanceDispatchQueued;
        handler.removeCallbacks(unlockAffordanceDispatchRunnable);
        unlockAffordanceDispatchQueued = false;
        unlockAffordanceDispatchGeneration++;
        if (rearm && !unlockAffordanceShownThisWake) {
            unlockAffordancePending = true;
        }
        if (wasQueued) {
            Log.i(TAG, "unlock affordance queue cancelled reason=" + reason
                    + " rearm=" + rearm
                    + " generation=" + unlockAffordanceDispatchGeneration);
        }
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
                || unlockEffectRendererType == OverlayPrefs.EFFECT_N4_INK_IN_WATER
                || unlockEffectRendererType == OverlayPrefs.EFFECT_WATERCOLOUR
                || OverlayPrefs.isColourDropletEffect(unlockEffectRendererType)
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                || unlockEffectRendererType == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || unlockEffectRendererType == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP
                || unlockEffectRendererType == OverlayPrefs.EFFECT_S6_WATER_DROPLET
                || unlockEffectRendererType
                        == OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED
                || unlockEffectRendererType == OverlayPrefs.EFFECT_TABS_BLIND
                || unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_RING
                || unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_CUT) {
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
        if (effectUsesLgPreLockUnderlay(effect)
                && !OverlayPrefs.effectBackgroundWakeCaptureActive(this)) {
            loadCachedUnlockEffectBackgroundSourceIfNeeded(effect);
            return;
        }
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
        boolean lgSharedCacheRequest = effectUsesLgPreLockUnderlay(effect)
                && OverlayPrefs.effectBackgroundWakeCaptureActive(this);
        if (unlockEffectBackgroundCaptureSucceededThisSession && hasBackground
                && !lgSharedCacheRequest) {
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
                        + " pinEntryPending=" + pinEntryPending
                        + " pinEntryRequested=" + pinEntryRequested
                        + " pinEntrySurface=" + pinEntrySurfaceVisible
                        + " notificationShade=" + notificationShadeVisible
                        + " blockedSurface=" + isRuntimeSurfaceBlocked()
                        + " effectGesture=" + unlockEffectGestureActive
                        + " effectAttached=" + unlockEffectOverlayAttached
                        + " effectParked=" + unlockEffectOverlayParked
                        + " lensLoopGesture=" + debugLensLoopGestureActive
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
                || captureEffect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || captureEffect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP
                || captureEffect == OverlayPrefs.EFFECT_S6_WATER_DROPLET
                || captureEffect == OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED)
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
                    boolean lgSharedCacheOnly = effectUsesLgPreLockUnderlay(captureEffect);
                    if (!lgSharedCacheOnly) {
                        applyUnlockEffectBackgroundSource(bitmap, "accessibility_screenshot");
                    }
                    persistEffectBackgroundScreenshotAsync(
                            bitmap, captureGeneration, captureEffect, captureProfile);
                    unlockEffectBackgroundCapturedAt = now;
                    unlockEffectBackgroundEffect = captureEffect;
                    unlockEffectBackgroundNextAttemptAt = 0L;
                    unlockEffectBackgroundCaptureSucceededThisSession = true;
                    skipCachedEffectBackgroundLoad = false;
                    Log.i(TAG, "unlock effect background screenshot "
                            + (lgSharedCacheOnly ? "saved for shared fallback" : "applied")
                            + " reason=" + reason
                            + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                            + " displayState=" + displayStateName(currentDisplayState())
                            + " displayId=" + captureDisplayId
                            + " profile=" + captureProfile
                            + " effect=" + captureEffect
                            + " pkg=" + lastWindowPackage);
                    bitmap.recycle();
                    completeForcedEffectBackgroundRefresh("applied");
                    forcedEffectBackgroundOverlayClearStartedAt = 0L;
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

    private boolean shouldSuppressWakeSurfacesForBackgroundCapture() {
        if (!isUnlockEffectAllowedNowForActivePanel()) {
            return false;
        }
        int effect = OverlayPrefs.unlockEffect(this);
        if (!effectUsesCachedScreenshotBackground(effect)
                || usesImportedEffectBackground(effect, activeDisplayProfile)) {
            return false;
        }
        return colorScreenshotInFlight
                || hasPendingUnlockEffectBackgroundRefresh(effect, "wake_gate");
    }

    private boolean hasPendingUnlockEffectBackgroundRefresh(int effect, String reason) {
        if (!effectUsesCachedScreenshotBackground(effect)
                || usesImportedEffectBackground(effect, activeDisplayProfile)) {
            return false;
        }
        if (effectUsesLgPreLockUnderlay(effect)) {
            // LG renderers consume Last screen, but an explicit request still maintains the
            // independent lockscreen cache used when the user switches to another effect.
            return OverlayPrefs.effectBackgroundWakeCaptureActive(this);
        }
        return shouldRefreshUnlockEffectBackground(
                effect, hasUnlockEffectBackgroundSource(effect), reason);
    }

    private void detachRuntimeSurfacesForBackgroundCapture(String reason) {
        boolean firstDetach = forcedEffectBackgroundOverlayClearStartedAt <= 0L;
        boolean hadAttachedSurface = doodleOverlayAttached
                || seasonalUnlockPartnerOverlayAttached
                || unlockEffectOverlayAttached
                || unlockEffectOverlayParked
                || touchDebugView != null;
        if (forcedEffectBackgroundOverlayClearStartedAt <= 0L) {
            forcedEffectBackgroundOverlayClearStartedAt = SystemClock.uptimeMillis();
        }
        removeDoodleOverlay();
        destroySeasonalUnlockPartnerOverlay();
        removeUnlockEffectOverlay(true);
        removeTouchDebugOverlay();
        if (firstDetach || hadAttachedSurface) {
            Log.i(TAG, "runtime surfaces detached for background capture reason=" + reason);
        }
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
        return hasPendingUnlockEffectBackgroundRefresh(effect, "preflight");
    }

    private boolean shouldRefreshUnlockEffectBackground(int effect, boolean hasBackground,
            String reason) {
        if (effectUsesLgPreLockUnderlay(effect)) {
            return OverlayPrefs.effectBackgroundWakeCaptureActive(this);
        }
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
        int effect = OverlayPrefs.unlockEffect(this);
        if ((effectUsesLgPreLockUnderlay(effect)
                && !OverlayPrefs.effectBackgroundWakeCaptureActive(this))
                || usesImportedEffectBackground(effect, activeDisplayProfile)) {
            return false;
        }
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (!interactive || !isLockscreenLocked(false)) {
            return false;
        }
        if (pinEntryPending || pinEntryRequested || pinEntrySurfaceVisible
                || notificationShadeVisible || isRuntimeSurfaceBlocked()) {
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
        return sinceScreenOn < 0L
                || sinceScreenOn >= SHARED_COLORMAP_CAPTURE_MIN_SCREEN_ON_MS;
    }

    private boolean shouldRetryUnlockEffectBackgroundCapture(int effect) {
        return !usesImportedEffectBackground(effect, activeDisplayProfile)
                && effectUsesScreenshotBackground(effect)
                && hasPendingUnlockEffectBackgroundRefresh(effect, "capture_retry")
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
        if (!automaticProfileScreenshotResolutionMatches(
                profile, bitmap.getWidth(), bitmap.getHeight(),
                activeDisplayWidth, activeDisplayHeight)) {
            Log.i(TAG, "background screenshot rejected: low profile resolution reason="
                    + reason
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
        if (!hasPendingUnlockEffectBackgroundRefresh(effect, "retry_gate:" + reason)) {
            // Normal wakes keep an existing cache. Only a truly missing first-run cache,
            // a profile-specific refresh token, an explicit one-shot request or a due
            // scheduled refresh receives retries.
            handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
            unlockEffectBackgroundNextAttemptAt = Long.MAX_VALUE;
            return;
        }
        long now = SystemClock.uptimeMillis();
        long sinceScreenOn = elapsedSinceScreenOn();
        long delayMs = UNLOCK_EFFECT_SCREENSHOT_RETRY_MS;
        if (sinceScreenOn >= 0L
                && sinceScreenOn < SHARED_COLORMAP_CAPTURE_MIN_SCREEN_ON_MS) {
            delayMs = Math.max(delayMs,
                    SHARED_COLORMAP_CAPTURE_MIN_SCREEN_ON_MS - sinceScreenOn);
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
        if (!OverlayPrefs.masterEnabled(this)
                || !isUnlockEffectEnabledForActivePanel()
                || !OverlayPrefs.effectBackgroundAutoRefreshEnabled(this)
                || !OverlayPrefs.effectBackgroundForceRecapture(this)) {
            cancelEffectBackgroundRefreshAlarm();
            return;
        }
        int effect = OverlayPrefs.unlockEffect(this);
        if (effectUsesLgPreLockUnderlay(effect)
                || !effectUsesCachedScreenshotBackground(effect)) {
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

    private void disableHardEffectBackgroundRecapture(String reason) {
        handler.removeCallbacks(unlockEffectBackgroundRetryRunnable);
        handler.removeCallbacks(forcedEffectBackgroundTimeoutRunnable);
        handler.removeCallbacks(forcedEffectBackgroundSleepRunnable);
        unlockEffectBackgroundNextAttemptAt = 0L;
        cancelEffectBackgroundRefreshAlarm();
        OverlayPrefs.get(this).edit()
                .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE, false)
                .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK, false)
                .apply();
        Log.i(TAG, "effect background hard recapture disarmed reason=" + reason);
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
        if (unlockEffectRenderer instanceof RawArgb8888BackgroundRenderer) {
            // Wait for the persisted raw file. Passing this transient screenshot would
            // recreate a full-screen Java Bitmap in the direct GLES path.
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
        if (effectUsesLgPreLockUnderlay(effect)) {
            return false;
        }
        if (!OverlayPrefs.importedEffectBackgroundEnabled(this, effect, profile)) {
            return false;
        }
        File imported = OverlayPrefs.importedEffectBackgroundFile(this, effect, profile);
        if (ManualEffectBackground.isUsable(imported)) {
            return true;
        }
        Log.w(TAG, "imported effect wallpaper invalid; restoring automatic capture"
                + " effect=" + effect
                + " profile=" + FoldDisplayTarget.normalizeProfile(profile)
                + " file=" + imported);
        OverlayPrefs.useAutomaticEffectBackground(this, effect, profile);
        return false;
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
        if (applyRawArgb8888BackgroundSourceIfSupported(
                file, effect, "imported_effect_background")) {
            unlockEffectBackgroundCapturedAt = SystemClock.uptimeMillis();
            unlockEffectBackgroundEffect = effect;
            colorScreenshotAttemptedThisSession = true;
            unlockEffectBackgroundCaptureSucceededThisSession = true;
            skipCachedEffectBackgroundLoad = false;
            Argb8888BitmapStore.Info info = Argb8888BitmapStore.inspect(file);
            Log.i(TAG, "imported effect wallpaper mapped directly size="
                    + (info == null ? "unknown" : info.width + "x" + info.height)
                    + " effect=" + effect
                    + " profile=" + activeDisplayProfile
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
            return true;
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
        if (!effectUsesCachedScreenshotBackground(effect)
                || !(unlockEffectRenderer instanceof BackgroundSourceRenderer)) {
            return;
        }
        loadSecondaryPreLockUnderlaySourceIfNeeded(effect, startedAt);
        if (skipCachedEffectBackgroundLoad) {
            return;
        }
        if (effectUsesLgPreLockUnderlay(effect)) {
            loadLgPreLockUnderlaySource(effect, startedAt);
            return;
        }
        BackgroundSourceRenderer backgroundRenderer =
                (BackgroundSourceRenderer) unlockEffectRenderer;
        if (usesImportedEffectBackground(effect, activeDisplayProfile)) {
            if (backgroundRenderer.hasBackgroundSourceBitmap()) {
                return;
            }
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
        if (applyRawArgb8888BackgroundSourceIfSupported(
                file, effect, BackgroundSourceRenderer.SHARED_CACHE_SOURCE)) {
            Argb8888BitmapStore.Info info = Argb8888BitmapStore.inspect(file);
            unlockEffectBackgroundCapturedAt = SystemClock.uptimeMillis();
            unlockEffectBackgroundEffect = effect;
            colorScreenshotAttemptedThisSession = true;
            Log.i(TAG, "unlock effect background cache mapped directly size="
                    + (info == null ? "unknown" : info.width + "x" + info.height)
                    + " effect=" + effect
                    + " profile=" + activeDisplayProfile
                    + " displayId=" + activeDisplayId
                    + " fileKb=" + Math.max(1L, file.length() / 1024L)
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
            return;
        }
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
                bitmap = Argb8888BitmapStore.decode(file);
                if (bitmap != null && !bitmap.isRecycled()) {
                    replaceCachedUnlockEffectBackgroundBitmap(
                            bitmap, effect, activeDisplayProfile, fileLength, fileModified,
                            file.getAbsolutePath());
                } else if (Argb8888BitmapStore.isRaw(file)) {
                    Log.w(TAG, "corrupt ARGB8888 colormap rejected profile="
                            + activeDisplayProfile + " effect=" + effect);
                    file.delete();
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

    private LgLastScreenCache.Target activeLgLastScreenTarget() {
        int width = activeDisplayWidth > 0
                ? activeDisplayWidth : Math.max(1, activeDisplayMetrics().widthPixels);
        int height = activeDisplayHeight > 0
                ? activeDisplayHeight : Math.max(1, activeDisplayMetrics().heightPixels);
        int displayId = activeDisplayId == Display.INVALID_DISPLAY
                ? Display.DEFAULT_DISPLAY : activeDisplayId;
        String profile = FoldDisplayTarget.normalizeProfile(activeDisplayProfile);
        return new LgLastScreenCache.Target(profile, displayId, width, height,
                OverlayPrefs.lgPreLockUnderlayFile(
                        this, profile, displayId, width, height));
    }

    private LgLastScreenCache.ResolvedSource resolveLgLastScreenSource(int effect) {
        return LgLastScreenCache.resolve(this, effect, activeLgLastScreenTarget());
    }

    private void loadSecondaryPreLockUnderlaySourceIfNeeded(int effect, long startedAt) {
        if (!OverlayPrefs.usesLgPreLockUnderlayAsSecondary(effect)
                || unlockEffectRendererType != effect
                || !(unlockEffectRenderer instanceof SecondaryBackgroundSourceRenderer)) {
            return;
        }
        SecondaryBackgroundSourceRenderer renderer =
                (SecondaryBackgroundSourceRenderer) unlockEffectRenderer;
        if (renderer.hasSecondaryBackgroundSourceBitmap()) {
            return;
        }
        LgLastScreenCache.Target target = activeLgLastScreenTarget();
        LgLastScreenCache.ResolvedSource source = resolveLgLastScreenSource(effect);
        if (source == null) {
            Log.i(TAG, "secondary Last screen unavailable effect=" + effect + " profile="
                    + target.profile + " displayId=" + target.displayId
                    + " target=" + target.width + "x" + target.height);
            return;
        }
        Bitmap bitmap = Argb8888BitmapStore.decode(source.file);
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        try {
            renderer.setSecondaryBackgroundSourceBitmap(
                    bitmap, source.fallback
                            ? BackgroundSourceRenderer.LG_PRELOCK_FALLBACK_SOURCE
                            : BackgroundSourceRenderer.LG_PRELOCK_UNDERLAY_SOURCE);
            Log.i(TAG, "secondary Last screen loaded size="
                    + source.info.width + "x" + source.info.height
                    + " displayId=" + target.displayId
                    + " profile=" + target.profile
                    + " fallback=" + source.fallback
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
        } finally {
            bitmap.recycle();
        }
    }

    private void loadLgPreLockUnderlaySource(int effect, long startedAt) {
        BackgroundSourceRenderer backgroundRenderer =
                (BackgroundSourceRenderer) unlockEffectRenderer;
        if (unlockEffectBackgroundEffect == effect
                && backgroundRenderer.hasBackgroundSourceBitmap()) {
            return;
        }
        LgLastScreenCache.Target target = activeLgLastScreenTarget();
        LgLastScreenCache.ResolvedSource source = resolveLgLastScreenSource(effect);
        if (source == null) {
            Log.i(TAG, "LG Last screen and lockscreen fallback unavailable effect=" + effect
                    + " profile=" + target.profile
                    + " displayId=" + target.displayId
                    + " target=" + target.width + "x" + target.height);
            return;
        }
        String sourceName = source.fallback
                ? BackgroundSourceRenderer.LG_PRELOCK_FALLBACK_SOURCE
                : BackgroundSourceRenderer.LG_PRELOCK_UNDERLAY_SOURCE;
        if (applyRawArgb8888BackgroundSourceIfSupported(
                source.file, effect, sourceName)) {
            unlockEffectBackgroundCapturedAt = SystemClock.uptimeMillis();
            unlockEffectBackgroundEffect = effect;
            colorScreenshotAttemptedThisSession = true;
            unlockEffectBackgroundCaptureSucceededThisSession = true;
            skipCachedEffectBackgroundLoad = false;
            Log.i(TAG, "LG pre-lock underlay mapped size="
                    + source.info.width + "x" + source.info.height
                    + " effect=" + effect
                    + " displayId=" + target.displayId
                    + " profile=" + target.profile
                    + " fallback=" + source.fallback
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
            return;
        }
        Bitmap bitmap = Argb8888BitmapStore.decode(source.file);
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        try {
            backgroundRenderer.setBackgroundSourceBitmap(
                    bitmap, sourceName);
            unlockEffectBackgroundCapturedAt = SystemClock.uptimeMillis();
            unlockEffectBackgroundEffect = effect;
            colorScreenshotAttemptedThisSession = true;
            unlockEffectBackgroundCaptureSucceededThisSession = true;
            skipCachedEffectBackgroundLoad = false;
            Log.i(TAG, "LG pre-lock underlay loaded size="
                    + source.info.width + "x" + source.info.height
                    + " effect=" + effect
                    + " displayId=" + target.displayId
                    + " profile=" + target.profile
                    + " fallback=" + source.fallback
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
        } finally {
            bitmap.recycle();
        }
    }

    private boolean applyRawArgb8888BackgroundSourceIfSupported(
            File file, int effect, String sourceName) {
        if (unlockEffectRendererType != effect
                || !(unlockEffectRenderer instanceof RawArgb8888BackgroundRenderer)
                || !Argb8888BitmapStore.isRaw(file)) {
            return false;
        }
        RawArgb8888BackgroundRenderer renderer =
                (RawArgb8888BackgroundRenderer) unlockEffectRenderer;
        renderer.setRawArgb8888BackgroundSource(file, sourceName);
        return renderer.hasRawArgb8888BackgroundSource();
    }

    private boolean hasUsableEffectBackgroundCache(int effect) {
        if (effectUsesLgPreLockUnderlay(effect)) {
            return resolveLgLastScreenSource(effect) != null;
        }
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
        File legacyProfilePng = OverlayPrefs.legacyPngEffectBackgroundFile(
                this, activeDisplayProfile);
        if (isUsableEffectBackgroundCacheFileForActiveProfile(legacyProfilePng)
                && copyEffectBackgroundCacheFile(legacyProfilePng, profileFile)) {
            Log.i(TAG, "effect background PNG migrated to ARGB8888 profile="
                    + activeDisplayProfile);
            return profileFile;
        }
        if (FoldDisplayTarget.PROFILE_COVER.equals(activeDisplayProfile)
                || FoldDisplayTarget.PROFILE_MAIN.equals(activeDisplayProfile)) {
            File oldShared = OverlayPrefs.effectBackgroundFile(this, effect);
            if (isUsableEffectBackgroundCacheFileForActiveProfile(oldShared)
                    && copyEffectBackgroundCacheFile(oldShared, profileFile)) {
                Log.i(TAG, "effect background single cache migrated profile="
                        + activeDisplayProfile);
                return profileFile;
            }
            File oldSharedPng = OverlayPrefs.legacyPngEffectBackgroundFile(
                    this, FoldDisplayTarget.PROFILE_SINGLE);
            if (isUsableEffectBackgroundCacheFileForActiveProfile(oldSharedPng)
                    && copyEffectBackgroundCacheFile(oldSharedPng, profileFile)) {
                Log.i(TAG, "effect background single PNG migrated profile="
                        + activeDisplayProfile);
                return profileFile;
            }
        }
        if (FoldDisplayTarget.PROFILE_TABLET_PORTRAIT.equals(activeDisplayProfile)
                || FoldDisplayTarget.PROFILE_TABLET_LANDSCAPE.equals(activeDisplayProfile)) {
            // A pre-tablet-mode single cache has no trustworthy orientation identity.
            // Keep it intact for phone mode, but require a clean capture for each tablet profile.
            return null;
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
                OverlayPrefs.EFFECT_N4_INK_IN_WATER,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_TABS_BLIND,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP,
                OverlayPrefs.EFFECT_S6_WATER_DROPLET,
                OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC,
                OverlayPrefs.EFFECT_BRILLIANT_RING,
                OverlayPrefs.EFFECT_BRILLIANT_CUT,
                OverlayPrefs.EFFECT_RIPPLE_INK,
                OverlayPrefs.EFFECT_GOOD_LOCK_POPPING,
                OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE,
                OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING,
                OverlayPrefs.EFFECT_LG_G2_PIXELATE,
                OverlayPrefs.EFFECT_LG_G2_PARTICLE,
                OverlayPrefs.EFFECT_LG_G2_CRYSTAL,
                OverlayPrefs.EFFECT_LG_G2_VECTOR,
                OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS,
                OverlayPrefs.EFFECT_REVOLVING_GLASS
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
                OverlayPrefs.EFFECT_N4_INK_IN_WATER,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_TABS_BLIND,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP,
                OverlayPrefs.EFFECT_S6_WATER_DROPLET,
                OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC,
                OverlayPrefs.EFFECT_BRILLIANT_RING,
                OverlayPrefs.EFFECT_BRILLIANT_CUT,
                OverlayPrefs.EFFECT_RIPPLE_INK,
                OverlayPrefs.EFFECT_GOOD_LOCK_POPPING,
                OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE,
                OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING,
                OverlayPrefs.EFFECT_LG_G2_PIXELATE,
                OverlayPrefs.EFFECT_LG_G2_PARTICLE,
                OverlayPrefs.EFFECT_LG_G2_CRYSTAL,
                OverlayPrefs.EFFECT_LG_G2_VECTOR,
                OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS,
                OverlayPrefs.EFFECT_REVOLVING_GLASS
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
        try {
            return Argb8888BitmapStore.migrate(source, target);
        } catch (Throwable t) {
            Log.d(TAG, "effect background legacy migration failed", t);
            return false;
        }
    }

    private boolean isUsableEffectBackgroundCacheFile(File file) {
        return file != null && file.exists() && file.length() > 0L;
    }

    private boolean isUsableEffectBackgroundCacheFileForActiveProfile(File file) {
        if (!isUsableEffectBackgroundCacheFile(file)) {
            return false;
        }
        try {
            Argb8888BitmapStore.Info bounds = Argb8888BitmapStore.inspect(file);
            if (bounds == null || bounds.width < 100 || bounds.height < 100) {
                return false;
            }
            if (FoldDisplayTarget.PROFILE_SINGLE.equals(activeDisplayProfile)) {
                return true;
            }
            return FoldDisplayTarget.bitmapMatches(
                    activeDisplayProfile,
                    bounds.width,
                    bounds.height,
                    activeDisplayWidth,
                    activeDisplayHeight)
                    && automaticProfileScreenshotResolutionMatches(
                            activeDisplayProfile,
                            bounds.width,
                            bounds.height,
                            activeDisplayWidth,
                            activeDisplayHeight);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean automaticProfileScreenshotResolutionMatches(
            String profile, int bitmapWidth, int bitmapHeight,
            int targetWidth, int targetHeight) {
        if (FoldDisplayTarget.PROFILE_SINGLE.equals(
                FoldDisplayTarget.normalizeProfile(profile))) {
            return true;
        }
        if (targetWidth <= 0 || targetHeight <= 0) {
            return false;
        }
        int minimumWidth = Math.max(
                100, (int) Math.ceil(targetWidth * PROFILE_SCREENSHOT_MIN_TARGET_SCALE));
        int minimumHeight = Math.max(
                100, (int) Math.ceil(targetHeight * PROFILE_SCREENSHOT_MIN_TARGET_SCALE));
        return bitmapWidth >= minimumWidth && bitmapHeight >= minimumHeight;
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

    private boolean effectUsesLgPreLockUnderlay(int effect) {
        return OverlayPrefs.usesLgPreLockUnderlay(effect);
    }

    private boolean effectNeedsLgPreLockUnderlay(int effect) {
        return OverlayPrefs.needsLgPreLockUnderlay(effect);
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
        if (!pendingProfile.equals(activeTouchBoxProfile())) {
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
        if (!pendingTouchBoxScreenshotProfile().equals(activeTouchBoxProfile())) {
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
        Log.i(TAG, "touch box screenshot capture armed profile=" + activeTouchBoxProfile());
    }

    private void runTouchBoxScreenshotCapture() {
        final int captureRequestId = touchBoxScreenshotInFlightRequestId;
        final int lifecycleGeneration = serviceLifecycleGeneration;
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
        if (!pendingTouchBoxScreenshotProfile().equals(activeTouchBoxProfile())) {
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
        final String captureProfile = activeTouchBoxProfile();
        try {
            touchBoxScreenshotCallbackPending = true;
            takeScreenshot(captureDisplayId, mainExecutor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    finishTouchBoxScreenshotAttempt(captureRequestId);
                    Bitmap bitmap = bitmapFromScreenshot(screenshotResult);
                    if (bitmap == null) {
                        if (!serviceAlive
                                || lifecycleGeneration != serviceLifecycleGeneration) {
                            return;
                        }
                        failTouchBoxScreenshotCapture(captureRequestId, "Screenshot empty");
                        return;
                    }
                    if (!serviceAlive
                            || lifecycleGeneration != serviceLifecycleGeneration) {
                        bitmap.recycle();
                        return;
                    }
                    if (captureDisplayId != activeDisplayId
                            || !captureProfile.equals(activeTouchBoxProfile())
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
                    if (!serviceAlive
                            || lifecycleGeneration != serviceLifecycleGeneration) {
                        return;
                    }
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
            return activeTouchBoxProfile();
        }
        return FoldDisplayTarget.normalizeProfile(prefs.getString(
                OverlayPrefs.TOUCH_BOX_CAPTURE_PROFILE, activeTouchBoxProfile()));
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
        final int lifecycleGeneration = serviceLifecycleGeneration;
        try {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (!serviceAlive
                            || lifecycleGeneration != serviceLifecycleGeneration) {
                        copy.recycle();
                        return;
                    }
                    final File file = OverlayPrefs.effectBackgroundFile(
                            ChargingAccessibilityService.this, effect, capturedProfile);
                    final File temp = new File(file.getParentFile(), file.getName() + ".tmp");
                    boolean saved = Argb8888BitmapStore.write(temp, copy);
                    if (saved) {
                        saved = swapEffectBackgroundCacheFile(temp, file);
                    }
                    if (!serviceAlive
                            || lifecycleGeneration != serviceLifecycleGeneration) {
                        if (temp.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            temp.delete();
                        }
                        copy.recycle();
                        return;
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
                            if (!serviceAlive
                                    || lifecycleGeneration != serviceLifecycleGeneration) {
                                if (!copy.isRecycled()) {
                                    copy.recycle();
                                }
                                return;
                            }
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
                            if (effectUsesLgPreLockUnderlay(effect)) {
                                // Prefer the independent Last screen. If it is unavailable,
                                // load this exact-profile lockscreen cache as a safe temporary
                                // source; the next successful screen-off capture replaces it.
                                copy.recycle();
                                if (generation == unlockEffectBackgroundGeneration
                                        && capturedProfile.equals(activeDisplayProfile)) {
                                    loadCachedUnlockEffectBackgroundSourceIfNeeded(effect);
                                }
                                scheduleEffectBackgroundRefreshAlarm("shared_cache_saved_for_lg");
                                return;
                            }
                            if (generation != unlockEffectBackgroundGeneration
                                    || !capturedProfile.equals(activeDisplayProfile)) {
                                copy.recycle();
                                return;
                            }
                            boolean directRaw = unlockEffectRendererType == effect
                                    && capturedProfile.equals(activeDisplayProfile)
                                    && applyRawArgb8888BackgroundSourceIfSupported(
                                            file,
                                            effect,
                                            BackgroundSourceRenderer.SHARED_CACHE_SOURCE);
                            if (directRaw) {
                                if (!copy.isRecycled()) {
                                    copy.recycle();
                                }
                            } else {
                                replaceCachedUnlockEffectBackgroundBitmap(
                                        copy,
                                        effect,
                                        capturedProfile,
                                        file.length(),
                                        file.lastModified(),
                                        file.getAbsolutePath());
                                if (unlockEffectRendererType == effect
                                        && capturedProfile.equals(activeDisplayProfile)
                                        && unlockEffectRenderer
                                        instanceof BackgroundSourceRenderer) {
                                    ((BackgroundSourceRenderer) unlockEffectRenderer)
                                            .setBackgroundSourceBitmap(copy,
                                                    BackgroundSourceRenderer.SHARED_CACHE_SOURCE);
                                }
                            }
                            if (OverlayPrefs.usesLgPreLockUnderlayAsSecondary(effect)
                                    && capturedProfile.equals(activeDisplayProfile)) {
                                loadSecondaryPreLockUnderlaySourceIfNeeded(
                                        effect, SystemClock.uptimeMillis());
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
                OverlayPrefs.EFFECT_N4_INK_IN_WATER,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_TABS_BLIND,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP,
                OverlayPrefs.EFFECT_S6_WATER_DROPLET,
                OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC,
                OverlayPrefs.EFFECT_BRILLIANT_RING,
                OverlayPrefs.EFFECT_BRILLIANT_CUT,
                OverlayPrefs.EFFECT_RIPPLE_INK,
                OverlayPrefs.EFFECT_GOOD_LOCK_POPPING,
                OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE,
                OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING,
                OverlayPrefs.EFFECT_LG_G2_PIXELATE,
                OverlayPrefs.EFFECT_LG_G2_PARTICLE,
                OverlayPrefs.EFFECT_LG_G2_CRYSTAL,
                OverlayPrefs.EFFECT_LG_G2_VECTOR,
                OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS,
                OverlayPrefs.EFFECT_REVOLVING_GLASS
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
        if (effectUsesLgPreLockUnderlay(effect)) {
            return true;
        }
        // These scenes need both their regular lockscreen capture and independent Last Screen
        // underlay, even when the tester's generic no-colormap switch is enabled.
        if (effect == OverlayPrefs.EFFECT_LG_G2_PIXELATE
                || effect == OverlayPrefs.EFFECT_LG_G2_VECTOR) {
            return true;
        }
        if (OverlayPrefs.testerNoColormapModeEnabled(this)) {
            return false;
        }
        return effect == OverlayPrefs.EFFECT_S4_LENS_FLARE
                || effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || effect == OverlayPrefs.EFFECT_N4_INK_IN_WATER
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_TABS_BLIND
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP
                || effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET
                || effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED
                || effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                || effect == OverlayPrefs.EFFECT_BRILLIANT_RING
                || effect == OverlayPrefs.EFFECT_BRILLIANT_CUT
                || effect == OverlayPrefs.EFFECT_RIPPLE_INK
                || effect == OverlayPrefs.EFFECT_GOOD_LOCK_POPPING
                || effect == OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE
                || effect == OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING
                || effect == OverlayPrefs.EFFECT_LG_G2_PIXELATE
                || effect == OverlayPrefs.EFFECT_LG_G2_PARTICLE
                || effect == OverlayPrefs.EFFECT_LG_G2_CRYSTAL
                || effect == OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS
                || effect == OverlayPrefs.EFFECT_REVOLVING_GLASS;
    }

    /**
     * Supplies only the two colour-sampling renderers with a tiny deterministic palette. It is
     * never persisted and is deliberately unrelated to the user's lockscreen wallpaper.
     */
    private void installTesterSyntheticColormapIfNeeded(int effect) {
        if (!OverlayPrefs.testerNoColormapModeEnabled(this)
                || !OverlayPrefs.usesTesterSyntheticColormap(effect)
                || !(unlockEffectRenderer instanceof BackgroundSourceRenderer)) {
            return;
        }
        final int size = 16;
        Bitmap palette = null;
        try {
            palette = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int red = 64 + (x * 9 + y * 3) % 160;
                    int green = 72 + (x * 4 + y * 11) % 152;
                    int blue = 88 + (x * 13 + y * 5) % 144;
                    pixels[y * size + x] = 0xFF000000
                            | red << 16 | green << 8 | blue;
                }
            }
            palette.setPixels(pixels, 0, size, 0, 0, size, size);
            ((BackgroundSourceRenderer) unlockEffectRenderer).setBackgroundSourceBitmap(
                    palette, BackgroundSourceRenderer.TESTER_SYNTHETIC_SOURCE);
            Log.i(TAG, "tester synthetic palette installed effect=" + effect
                    + " size=" + size + "x" + size);
        } catch (Throwable error) {
            Log.w(TAG, "tester synthetic palette unavailable effect=" + effect, error);
        } finally {
            if (palette != null && !palette.isRecycled()) {
                palette.recycle();
            }
        }
    }

    private void syncTouchDebugOverlay() {
        syncTouchDebugOverlay(isFxSurfaceActive(false), true);
    }

    private void syncTouchDebugOverlay(boolean active) {
        syncTouchDebugOverlay(active, true);
    }

    private void syncTouchDebugOverlay(boolean mounted, boolean touchable) {
        long startedAt = SystemClock.uptimeMillis();
        if (lockCycleSafetyBypassActive) {
            removeTouchDebugOverlay();
            return;
        }
        if (holdRuntimeForBootSafety("touch_overlay")) {
            removeTouchDebugOverlay();
            return;
        }
        if (!OverlayPrefs.debugTouchArea(this) || !mounted) {
            removeTouchDebugOverlay();
            return;
        }
        List<Rect> boxes = resolveTouchBoxes();
        boolean standbyEnabled = OverlayPrefs.debugTouchStandby(this);
        boolean standbyTouchable = !isNotificationShadeInputBlocked()
                && (touchable || standbyEnabled);
        // Let an early wake touch try the same readiness gate used by normal gestures.
        boolean listening = standbyTouchable;
        if (touchDebugView != null) {
            if (touchDebugWindowCount() == boxes.size()) {
                for (int i = 0; i < boxes.size(); i++) {
                    TouchDebugView view = touchDebugViewAt(i);
                    view.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
                    view.setListeningEnabled(listening);
                    view.setSafetyBypassEnabled(
                            OverlayPrefs.threeFingerSafetyBypassEnabled(this));
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
                } else if (unlockEffectRenderer instanceof BrilliantRingEffectView) {
                    ((BrilliantRingEffectView) unlockEffectRenderer).realignGesture(
                            screenX, screenY);
                } else if (unlockEffectRenderer instanceof BrilliantCutEffectView) {
                    ((BrilliantCutEffectView) unlockEffectRenderer).realignGesture(
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

            @Override
            public void onSafetyBypassRequested() {
                activateLockCycleSafetyBypass();
            }
        };

        touchDebugTouchable = standbyTouchable;
        for (int i = 0; i < boxes.size(); i++) {
            Rect area = boxes.get(i);
            TouchDebugView view = new TouchDebugView(rendererContext());
            view.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
            view.setListeningEnabled(listening);
            view.setSafetyBypassEnabled(
                    OverlayPrefs.threeFingerSafetyBypassEnabled(this));
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
        cancelUnlockAffordanceDispatch(false, "stop_all");
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

    private void hideRuntimeSurfacesForBlockedPackage(String reason) {
        boolean hadVisibleRuntimeState = unlockEffectGestureActive
                || unlockFxVisible
                || doodleOverlayAttached
                || seasonalUnlockPartnerOverlayAttached
                || touchDebugView != null
                || (unlockEffectOverlayAttached && !unlockEffectOverlayParked);
        stopDebugLensLoop();
        cancelSeasonalUnlockPartnerGesture();
        unlockEffectGestureActive = false;
        unlockFxVisible = false;
        pinEntryPending = false;
        removeDoodleOverlay();
        destroySeasonalUnlockPartnerOverlay();
        parkUnlockEffectOverlayForBlockedPackage();
        removeTouchDebugOverlay();
        if (hadVisibleRuntimeState) {
            Log.i(TAG, "runtime surfaces parked for blocked package reason=" + reason);
        }
    }

    private void parkUnlockEffectOverlayForBlockedPackage() {
        cancelUnlockAffordanceDispatch(false, "blocked_package");
        if (!unlockEffectOverlayAttached || unlockEffectView == null) {
            return;
        }
        if (unlockEffectOverlayParked && unlockEffectView.getAlpha() == 0f) {
            return;
        }
        unlockEffectView.setAlpha(0f);
        unlockEffectView.setVisibility(View.VISIBLE);
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.cancelGesture();
            parkNativePhysicsRendererState(unlockEffectRenderer);
        }
        unlockEffectOverlayParked = true;
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
        cancelUnlockAffordanceDispatch(false, "unload:" + reason);
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
                // Make the already-composited frame invisible before queuing the
                // native reset. Otherwise a fast GL thread can expose the reset
                // or release-tail frame during the final unlock transition.
                hideUnlockEffectView(unlockEffectView);
                parkNativePhysicsRendererState(unlockEffectRenderer);
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
            unlockEffectWindowParams = null;
            unlockEffectWindowNeutralizedForHandoff = false;
            refreshUnlockEffectReadiness("detached");
        }
        // Parked is meaningful only while the renderer window is still attached at
        // zero alpha. A display-profile handoff can already remove that window before
        // this cleanup runs; keeping the old parked flag then blocks screenshot capture
        // forever even though no L.L.E surface remains on the new panel.
        if (!unlockEffectOverlayAttached) {
            unlockEffectOverlayParked = false;
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
            if (unlockAffordanceDeliveredRenderer == unlockEffectRenderer) {
                unlockAffordanceDeliveredRenderer = null;
            }
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
        unlockEffectWindowParams = null;
        unlockEffectRendererType = -1;
        unlockEffectOverlayAddRetryAt = 0L;
        unlockEffectOverlayParked = false;
        unlockEffectWindowNeutralizedForHandoff = false;
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
        if (renderer instanceof Note5NativeEffectView) {
            ((Note5NativeEffectView) renderer).parkForReuse();
        } else if (renderer instanceof S6WaterDropletEffectView) {
            ((S6WaterDropletEffectView) renderer).parkForReuse();
        } else if (renderer instanceof S6WaterDropletAppOwnedEffectView) {
            ((S6WaterDropletAppOwnedEffectView) renderer).parkForReuse();
        } else if (renderer instanceof ColourDropletEffectView) {
            ((ColourDropletEffectView) renderer).parkForReuse();
        } else if (renderer instanceof SparklingBubblesEffectView) {
            ((SparklingBubblesEffectView) renderer).parkForReuse();
        } else if (renderer instanceof SparklingBubblesAppOwnedEffectView) {
            ((SparklingBubblesAppOwnedEffectView) renderer).parkForReuse();
        } else if (renderer instanceof ColourDropletAppOwnedEffectView) {
            ((ColourDropletAppOwnedEffectView) renderer).parkForReuse();
        } else if (renderer instanceof G2ParticleEffectView) {
            ((G2ParticleEffectView) renderer).parkForReuse();
        } else if (renderer != null) {
            renderer.resetEffect();
        }
    }

    private void resumeNativePhysicsRendererState(UnlockEffectRenderer renderer) {
        if (renderer instanceof Note5NativeEffectView) {
            ((Note5NativeEffectView) renderer).resumeForReuse();
        } else if (renderer instanceof S6WaterDropletEffectView) {
            ((S6WaterDropletEffectView) renderer).resumeForReuse();
        } else if (renderer instanceof S6WaterDropletAppOwnedEffectView) {
            ((S6WaterDropletAppOwnedEffectView) renderer).resumeForReuse();
        } else if (renderer instanceof ColourDropletEffectView) {
            ((ColourDropletEffectView) renderer).resumeForReuse();
        } else if (renderer instanceof SparklingBubblesEffectView) {
            ((SparklingBubblesEffectView) renderer).resumeForReuse();
        } else if (renderer instanceof SparklingBubblesAppOwnedEffectView) {
            ((SparklingBubblesAppOwnedEffectView) renderer).resumeForReuse();
        } else if (renderer instanceof ColourDropletAppOwnedEffectView) {
            ((ColourDropletAppOwnedEffectView) renderer).resumeForReuse();
        } else if (renderer instanceof G2ParticleEffectView) {
            ((G2ParticleEffectView) renderer).resumeForReuse();
        }
    }

    private void parkUnlockEffectOverlayForScreenOff() {
        cancelUnlockAffordanceDispatch(false, "screen_off_park");
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
        if (unlockEffectRenderer != null) {
            unlockEffectRenderer.resetEffect();
        }
        syncUnlockEffectOverlay(true);
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
        boolean windowWasNeutralized = unlockEffectWindowNeutralizedForHandoff;
        restoreUnlockEffectWindowAfterHandoff("show");
        boolean wasParked = unlockEffectOverlayParked
                || view.getVisibility() != View.VISIBLE
                || view.getAlpha() < 1f
                || windowWasNeutralized;
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

    private void neutralizeUnlockEffectWindowForHandoff(String reason) {
        // Android started blocking untrusted pass-through touches in Android 12.
        // Older releases do not benefit from the extra WindowManager transaction,
        // so preserve their established handoff path byte-for-byte at runtime.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            pinEntryHandoffWindowAlphaResult = "not_required_pre_s";
            pinEntryHandoffWindowAlphaElapsedMs = 0L;
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        boolean hadWindow = unlockEffectOverlayAttached
                && unlockEffectView != null && unlockEffectWindowParams != null;
        boolean retainLgPreLockFrame = retainsLgPreLockFrameDuringHandoff();
        float targetAlpha = retainLgPreLockFrame ? 1f : 0f;
        if (retainLgPreLockFrame) {
            // TYPE_ACCESSIBILITY_OVERLAY is trusted by InputDispatcher. Keep the dedicated
            // visual window fully opaque and permanently outside hit testing; only the
            // separate touch-listen windows are neutralized during the LG handoff.
            int requiredFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            boolean needsUpdate = hadWindow
                    && (Math.abs(unlockEffectWindowParams.alpha - 1f) >= 0.001f
                    || (unlockEffectWindowParams.flags & requiredFlags) != requiredFlags);
            if (needsUpdate) {
                float previousAlpha = unlockEffectWindowParams.alpha;
                int previousFlags = unlockEffectWindowParams.flags;
                unlockEffectWindowParams.alpha = 1f;
                unlockEffectWindowParams.flags |= requiredFlags;
                try {
                    windowManager.updateViewLayout(unlockEffectView, unlockEffectWindowParams);
                } catch (RuntimeException e) {
                    unlockEffectWindowParams.alpha = previousAlpha;
                    unlockEffectWindowParams.flags = previousFlags;
                    Log.w(TAG, "LG visual overlay state update failed reason=" + reason, e);
                }
            }
            unlockEffectWindowNeutralizedForHandoff = false;
        } else {
            setUnlockEffectWindowAlpha(targetAlpha, true, reason);
        }
        pinEntryHandoffWindowAlphaElapsedMs = Math.max(
                0L, SystemClock.uptimeMillis() - startedAt);
        if (!hadWindow) {
            pinEntryHandoffWindowAlphaResult = "no_effect_window";
        } else if (unlockEffectWindowParams != null
                && Math.abs(unlockEffectWindowParams.alpha - targetAlpha) < 0.001f
                && (!retainLgPreLockFrame || (unlockEffectWindowParams.flags
                & (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
                == (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))) {
            pinEntryHandoffWindowAlphaResult = retainLgPreLockFrame
                    ? "lg_visual_opaque_not_touchable" : "request_accepted";
        } else {
            pinEntryHandoffWindowAlphaResult = "failed";
        }
        Log.i(TAG, "pin entry effect window prepared result="
                + pinEntryHandoffWindowAlphaResult
                + " elapsedMs=" + pinEntryHandoffWindowAlphaElapsedMs
                + " attached=" + unlockEffectOverlayAttached
                + " type=" + unlockEffectRendererType);
    }

    private boolean retainsLgPreLockFrameDuringHandoff() {
        // White Hole is the first recovered LG effect with this two-phase handoff.
        // Future effects such as Soda can opt in here without changing Samsung effects.
        return unlockEffectRendererType == OverlayPrefs.EFFECT_LG_G1_WHITE_HOLE
                || unlockEffectRendererType == OverlayPrefs.EFFECT_LG_SODA
                || unlockEffectRendererType == OverlayPrefs.EFFECT_LG_G1_DEWDROP
                || unlockEffectRendererType == OverlayPrefs.EFFECT_LG_G2_PARTICLE
                || unlockEffectRendererType == OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE
                || unlockEffectRendererType == OverlayPrefs.EFFECT_LG_G2_PIXELATE
                || unlockEffectRendererType == OverlayPrefs.EFFECT_LG_G2_VECTOR
                || unlockEffectRendererType == OverlayPrefs.EFFECT_REVOLVING_GLASS;
    }

    private void restoreUnlockEffectWindowAfterHandoff(String reason) {
        if (!unlockEffectWindowNeutralizedForHandoff) {
            return;
        }
        setUnlockEffectWindowAlpha(1f, false, reason);
    }

    private void setUnlockEffectWindowAlpha(
            float alpha, boolean neutralized, String reason) {
        if (!unlockEffectOverlayAttached
                || unlockEffectView == null
                || unlockEffectWindowParams == null) {
            return;
        }
        float targetAlpha = Math.max(0f, Math.min(1f, alpha));
        float previousAlpha = unlockEffectWindowParams.alpha;
        if (Math.abs(previousAlpha - targetAlpha) < 0.001f) {
            unlockEffectWindowNeutralizedForHandoff = neutralized;
            return;
        }
        unlockEffectWindowParams.alpha = targetAlpha;
        try {
            windowManager.updateViewLayout(unlockEffectView, unlockEffectWindowParams);
            unlockEffectWindowNeutralizedForHandoff = neutralized;
            Log.i(TAG, "unlock effect window alpha=" + targetAlpha
                    + " reason=" + reason);
        } catch (RuntimeException e) {
            unlockEffectWindowParams.alpha = previousAlpha;
            Log.w(TAG, "unlock effect window alpha update failed reason=" + reason, e);
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

    /**
     * Synchronously removes only the input-owning touch windows at the PIN handoff boundary.
     * Normal QS/AOD/screen-off removals intentionally keep using removeView() so they never
     * block a visibility pass. Here ACTION_UP has already completed, and dispatching a SystemUI
     * gesture while an old InputWindow removal is still queued can wedge slow OEM dispatchers.
     */
    private boolean removeTouchDebugOverlayImmediatelyForHandoff(String reason) {
        long startedAt = SystemClock.uptimeMillis();
        int before = touchDebugWindowCount();
        boolean primaryPreparation = "pin_entry_requested".equals(reason);
        boolean removalSucceeded = true;
        if (primaryPreparation) {
            pinEntryHandoffTouchWindowsBefore = before;
            pinEntryHandoffTouchRemovalMode = "immediate";
        }
        if (touchDebugView != null) {
            try {
                windowManager.removeViewImmediate(touchDebugView);
            } catch (RuntimeException e) {
                removalSucceeded = false;
                Log.w(TAG, "immediate primary touch window removal failed reason="
                        + reason, e);
            }
            touchDebugView = null;
            touchDebugParams = null;
            touchDebugTouchable = false;
        }
        for (TouchDebugView view : additionalTouchDebugViews) {
            try {
                windowManager.removeViewImmediate(view);
            } catch (RuntimeException e) {
                removalSucceeded = false;
                Log.w(TAG, "immediate additional touch window removal failed reason="
                        + reason, e);
            }
        }
        additionalTouchDebugViews.clear();
        additionalTouchDebugParams.clear();
        int after = touchDebugWindowCount();
        long elapsedMs = Math.max(0L, SystemClock.uptimeMillis() - startedAt);
        if (primaryPreparation) {
            pinEntryHandoffTouchWindowsAfter = after;
            pinEntryHandoffTouchRemovalElapsedMs = elapsedMs;
            pinEntryHandoffTouchRemovalResult = removalSucceeded
                    ? "request_completed" : "exception";
        }
        Log.i(TAG, "pin entry touch windows removed mode=immediate"
                + " reason=" + reason
                + " before=" + before
                + " after=" + after
                + " result=" + (removalSucceeded ? "request_completed" : "exception")
                + " elapsedMs=" + elapsedMs);
        return removalSucceeded;
    }

    /**
     * First phase of the LG handoff: publish NOT_TOUCHABLE while the input windows are still
     * attached. Removing them on the following frame gives WindowManager/InputDispatcher a
     * safe intermediate state instead of racing removal directly against injected ACTION_DOWN.
     */
    private boolean neutralizeTouchDebugOverlayForLgHandoff(String reason) {
        long startedAt = SystemClock.uptimeMillis();
        int before = touchDebugWindowCount();
        boolean succeeded = true;
        pinEntryHandoffTouchWindowsBefore = before;
        pinEntryHandoffTouchRemovalMode = "lg_neutralize_frame_remove_frame";
        for (int i = 0; i < before; i++) {
            TouchDebugView view = touchDebugViewAt(i);
            WindowManager.LayoutParams params = touchDebugParamsAt(i);
            if (view == null || params == null) {
                continue;
            }
            int previousFlags = params.flags;
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            try {
                windowManager.updateViewLayout(view, params);
            } catch (RuntimeException e) {
                params.flags = previousFlags;
                succeeded = false;
                Log.w(TAG, "LG touch window neutralization failed index=" + i
                        + " reason=" + reason, e);
            }
        }
        touchDebugTouchable = false;
        pinEntryHandoffTouchWindowsAfter = touchDebugWindowCount();
        pinEntryHandoffTouchRemovalElapsedMs = Math.max(
                0L, SystemClock.uptimeMillis() - startedAt);
        pinEntryHandoffTouchRemovalResult = succeeded
                ? "neutralized_pending_frame_removal" : "neutralize_exception";
        Log.i(TAG, "LG pin entry touch windows neutralized"
                + " before=" + before
                + " attached=" + pinEntryHandoffTouchWindowsAfter
                + " result=" + pinEntryHandoffTouchRemovalResult
                + " elapsedMs=" + pinEntryHandoffTouchRemovalElapsedMs);
        return succeeded;
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
        updateTouchDebugLayouts(boxes, touchable, false);
    }

    private void updateTouchDebugLayouts(
            List<Rect> boxes, boolean touchable, boolean forceRelayout) {
        if (touchDebugView == null || touchDebugParams == null
                || touchDebugWindowCount() != boxes.size()) {
            return;
        }
        int flags = touchListenBoxFlags(touchable);
        for (int i = 0; i < boxes.size(); i++) {
            Rect box = boxes.get(i);
            TouchDebugView view = touchDebugViewAt(i);
            WindowManager.LayoutParams params = touchDebugParamsAt(i);
            boolean changed = forceRelayout
                    || params.x != box.left || params.y != box.top
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

    private void refreshTouchDebugInputAfterScreenOn() {
        if (touchDebugView == null) {
            return;
        }
        // Some Samsung builds make accessibility-overlay input handles NOT_TOUCHABLE
        // while the display is off without changing the app-owned LayoutParams. A normal
        // sync then sees identical flags and skips updateViewLayout(), leaving the stale
        // InputWindowHandle in place after wake. Force one relayout once SCREEN_ON is
        // delivered so the configured regions become touchable again; keep the current
        // desired touchability so shade/PIN suppression remains authoritative.
        updateTouchDebugLayouts(resolveTouchBoxes(), touchDebugTouchable, true);
        Log.i(TAG, "touch listen input refreshed after screen on"
                + " touchable=" + touchDebugTouchable
                + " areas=" + touchDebugWindowCount());
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
                && activeTouchBoxProfile().equals(resolvedTouchBoxesProfile)) {
            return resolvedTouchBoxesCache;
        }
        int minSize = dp(48);
        resolvedTouchBoxesCache.clear();
        List<Rect> saved = OverlayPrefs.touchBoxRegions(
                rendererContext(), activeTouchBoxProfile());
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
        resolvedTouchBoxesProfile = activeTouchBoxProfile();
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

    private void runDebugUnlockEffectDemoGesture() {
        if (unlockEffectRenderer == null || !isFxSurfaceActive()) {
            Log.i(TAG, "debug demo gesture ignored renderer="
                    + (unlockEffectRenderer == null ? "null"
                            : unlockEffectRenderer.getClass().getSimpleName())
                    + " surfaceActive=" + isFxSurfaceActive());
            return;
        }

        // Keep renderer A/B captures deterministic. A queued lockscreen affordance can
        // otherwise start between renderer construction and this synthetic gesture,
        // consuming the Lens Flare random sequence at a device-dependent time.
        cancelUnlockAffordanceDispatch(false, "debug_demo_gesture");
        unlockAffordancePending = false;
        unlockAffordanceShownThisWake = true;
        unlockAffordanceDeliveredRenderer = null;
        final UnlockEffectRenderer renderer = unlockEffectRenderer;
        Rect box = resolveTouchBox();
        float span = Math.min(box.width(), box.height()) * 0.72f;
        final float startX = box.exactCenterX() - span * 0.5f;
        final float startY = box.exactCenterY() + span * 0.5f;
        final float endX = box.exactCenterX() + span * 0.5f;
        final float endY = box.exactCenterY() - span * 0.5f;
        renderer.beginGesture(startX, startY);
        Log.i(TAG, "debug demo gesture begin effect=" + unlockEffectRendererType
                + " box=" + box.left + "," + box.top + ","
                + box.right + "," + box.bottom
                + " from=" + Math.round(startX) + "," + Math.round(startY)
                + " to=" + Math.round(endX) + "," + Math.round(endY));

        final android.animation.ValueAnimator animator =
                android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1_250L);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            private long lastMoveAt;

            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                if (unlockEffectRenderer != renderer) {
                    animation.cancel();
                    return;
                }
                float fraction = animation.getAnimatedFraction();
                long now = SystemClock.uptimeMillis();
                // The S23 runs at 120 Hz, but Samsung's original touch paths are
                // happiest near 60 MOVE events/s. Throttle without quantizing the path.
                if (fraction < 1f && now - lastMoveAt < 16L) {
                    return;
                }
                lastMoveAt = now;
                renderer.updateGesture(
                        startX + (endX - startX) * fraction,
                        startY + (endY - startY) * fraction);
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (cancelled || unlockEffectRenderer != renderer) {
                    return;
                }
                // Renderer-only demo: never request PIN entry or unlock the device.
                renderer.finishGesture(false);
                Log.i(TAG, "debug demo gesture end effect=" + unlockEffectRendererType);
            }
        });
        animator.start();
    }

    private boolean isFxSurfaceActive() {
        return isFxSurfaceActive(true);
    }

    private boolean isFxSurfaceActive(boolean contentAware) {
        if (!OverlayPrefs.masterEnabled(this) || lockCycleSafetyBypassActive) {
            return false;
        }
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = isLockscreenLocked(contentAware);
        boolean home = interactive && !locked && isHomePackage(lastWindowPackage);
        boolean pinEntryActive = pinEntryPending || pinEntryRequested || pinEntrySurfaceVisible;
        boolean blockedSurfaceActive = pinEntryActive
                || notificationShadeVisible
                || isRuntimeSurfaceBlocked();
        int displayState = currentDisplayState();
        boolean displayOn = displayState == Display.STATE_UNKNOWN || displayState == Display.STATE_ON;
        boolean aodSurface = isActualAodSurface(interactive, displayOn);
        return isSharedRuntimeSurfaceAllowed(
                interactive,
                displayOn,
                locked,
                aodSurface,
                false,
                false,
                blockedSurfaceActive)
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

    private boolean shouldShowFrozenDoodleAod(
            boolean interactive, boolean locked, int displayState) {
        boolean aodDisplayState = !interactive
                || displayState == Display.STATE_DOZE
                || displayState == Display.STATE_DOZE_SUSPEND;
        return OverlayPrefs.doodleAodEnabled(this)
                && charging
                && locked
                && aodDisplayState
                && isChargingDoodleModeEnabled();
    }

    private boolean isDoodleVisible(boolean interactive, boolean locked, boolean home,
            boolean blockedSurfaceActive) {
        int displayState = currentDisplayState();
        boolean displayOn = displayState == Display.STATE_UNKNOWN || displayState == Display.STATE_ON;
        boolean aodSurface = isActualAodSurface(interactive, displayOn);
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

    private boolean isActualAodSurface(boolean interactive, boolean displayOn) {
        // During a normal Samsung wake the reported foreground package can briefly
        // remain AOD even though the interactive lockscreen is already on-screen.
        // Treat the package as an AOD blocker only while the display is genuinely
        // non-interactive/off; otherwise parking the overlay causes a visible flash.
        return AOD_PACKAGE.equals(lastWindowPackage) && (!interactive || !displayOn);
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
                && OverlayPrefs.isImplementedEffect(this, OverlayPrefs.unlockEffect(this))
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
                || isRuntimeSurfaceBlocked()
                || notificationShadeVisible) {
            return;
        }
        if (isChargingDoodleModeEnabled()) {
            if (OverlayPrefs.doodleLockSoundAllowedNow(this)) {
                lastLockSoundPlayedAt = now;
                lockSoundPlayer.playSeasonalLock(OverlayPrefs.seasonMode(this));
                return;
            }
        }
        if (isUnlockEffectEnabledForActivePanel()
                && OverlayPrefs.isImplementedEffect(this, OverlayPrefs.unlockEffect(this))
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
        if (lockCycleSafetyBypassActive) {
            Log.i(TAG, "unlock effect gesture ignored lock-cycle safety bypass=true");
            return false;
        }
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
                || isNotificationShadeInputBlocked() || isRuntimeSurfaceBlocked()) {
            Log.i(TAG, "unlock effect gesture blocked by content surface");
            evaluateVisibility("gesture_blocked_surface");
            return false;
        }
        // A visible native affordance owns the same physics scene as the real gesture.
        // Re-parking an already attached renderer here sends clear + screen-off and can
        // race the following ACTION_DOWN on the GL thread, leaving only the touch sound.
        // Keep the live scene running; only take the parked preload path when the surface
        // genuinely still has to be created or attached.
        if (unlockEffectRenderer == null
                || unlockEffectView == null
                || !unlockEffectOverlayAttached
                || unlockEffectRendererType != effect) {
            preloadAndAttachSelectedUnlockEffectParked("gesture_down");
        }
        refreshUnlockEffectReadiness("gesture_down");
        if (!isUnlockEffectFirstFrameReady()) {
            return bufferUnlockEffectGestureDown(screenX, screenY, effect);
        }
        return beginReadyUnlockEffectGesture(screenX, screenY, effect, startedAt);
    }

    private boolean beginReadyUnlockEffectGesture(float screenX, float screenY,
            int effect, long startedAt) {
        cancelUnlockAffordanceDispatch(false, "gesture_down");
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
        if (unlockTriggered && isUnlockEffectGestureReady()
                && !isNotificationShadeInputBlocked()) {
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
        if (lockCycleSafetyBypassActive) {
            Log.i(TAG, "seasonal unlock partner ignored lock-cycle safety bypass=true");
            return false;
        }
        if (!isSeasonalUnlockPartnerModeEnabled() || !isUnlockEffectGestureReady()) {
            Log.i(TAG, "seasonal unlock partner gesture ignored ready=false"
                    + " mode=" + isSeasonalUnlockPartnerModeEnabled()
                    + " charging=" + charging
                    + " locked=" + isLockscreenLocked(false));
            return false;
        }
        if (pinEntryPending || pinEntryRequested || pinEntrySurfaceVisible
                || isNotificationShadeInputBlocked() || isRuntimeSurfaceBlocked()) {
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
        } else if (unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_RING
                && unlockEffectRenderer instanceof BrilliantRingEffectView) {
            ((BrilliantRingEffectView) unlockEffectRenderer).finishGestureAt(
                    screenX, screenY, unlockTriggered);
        } else if (unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_RING
                && unlockEffectRenderer instanceof SamsungLockBgEffectView) {
            ((SamsungLockBgEffectView) unlockEffectRenderer).finishGestureAt(
                    screenX, screenY, unlockTriggered);
        } else if (unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_CUT
                && unlockEffectRenderer instanceof BrilliantCutEffectView) {
            ((BrilliantCutEffectView) unlockEffectRenderer).finishGestureAt(
                    screenX, screenY, unlockTriggered);
        } else if (unlockEffectRendererType == OverlayPrefs.EFFECT_BRILLIANT_CUT
                && unlockEffectRenderer instanceof SamsungLockBgEffectView) {
            ((SamsungLockBgEffectView) unlockEffectRenderer).finishGestureAt(
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

    private void activateLockCycleSafetyBypass() {
        if (lockCycleSafetyBypassActive
                || !OverlayPrefs.threeFingerSafetyBypassEnabled(this)) {
            return;
        }
        lockCycleSafetyBypassActive = true;
        cancelUnlockAffordanceDispatch(false, "three_finger_safety_bypass");
        unlockAffordancePending = false;
        unlockAffordanceShownThisWake = true;
        unlockAffordanceDeliveredRenderer = null;
        cancelBufferedReadinessGesture("three_finger_safety_bypass", false);
        if (seasonalUnlockPartnerGestureActive) {
            cancelSeasonalUnlockPartnerGesture();
        }
        cancelUnlockEffectGesture();
        clearActiveUnlockEffectProfile();
        unlockFxVisible = false;
        removeTouchDebugOverlay();
        removeUnlockEffectOverlay();
        destroySeasonalUnlockPartnerOverlay();
        Toast.makeText(this,
                "L.L.E disabled until the next lock cycle",
                Toast.LENGTH_LONG).show();
        Log.w(TAG, "lock-cycle safety bypass activated gesture=three_finger_swipe");
    }

    private void clearLockCycleSafetyBypass(String reason) {
        if (!lockCycleSafetyBypassActive) {
            pinEntryHandoffFailOpen = false;
            return;
        }
        lockCycleSafetyBypassActive = false;
        pinEntryHandoffFailOpen = false;
        Log.i(TAG, "lock-cycle safety bypass cleared reason=" + reason);
    }

    private void schedulePinEntry() {
        schedulePinEntry(pinEntryDelayMs(), OverlayPrefs.effectLabel(OverlayPrefs.unlockEffect(this)));
    }

    private void schedulePinEntry(long delayMs, String source) {
        if (pinEntryPending || pinEntryRequested || pinEntryHandoffActive) {
            Log.i(TAG, "pin entry duplicate schedule ignored"
                    + " pending=" + pinEntryPending
                    + " requested=" + pinEntryRequested
                    + " handoffActive=" + pinEntryHandoffActive
                    + " source=" + source);
            return;
        }
        if (isNotificationShadeInputBlocked()) {
            Log.i(TAG, "pin entry not scheduled; notification shade input blocked");
            return;
        }
        startPinEntryTrace(delayMs);
        beginPinEntryHandoff();
        pinEntryPending = true;
        scheduledPinEntrySafetyGeneration = inputSafetyGeneration;
        handler.removeCallbacks(pinEntryRunnable);
        handler.postDelayed(pinEntryRunnable, delayMs);
        Log.i(TAG, "pin entry scheduled delayMs=" + delayMs
                + " source=" + source
                + " effect=" + OverlayPrefs.unlockEffect(this));
    }

    private long pinEntryDelayMs() {
        int effect = OverlayPrefs.unlockEffect(this);
        long tailDelayMs = tailCompletePinEntryDelayMs(effect);
        if (tailDelayMs >= 0L) {
            return tailDelayMs;
        }
        if (effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS) {
            return PIN_ENTRY_DELAY_POPPING_COLOURS_MS;
        }
        if (effect == OverlayPrefs.EFFECT_WATERCOLOUR) {
            return PIN_ENTRY_DELAY_WATERCOLOUR_MS;
        }
        if (effect == OverlayPrefs.EFFECT_BRILLIANT_RING
                || effect == OverlayPrefs.EFFECT_BRILLIANT_CUT) {
            return PIN_ENTRY_DELAY_BRILLIANT_RING_MS;
        }
        if (OverlayPrefs.isColourDropletEffect(effect)) {
            return PIN_ENTRY_DELAY_COLOUR_DROPLET_MS;
        }
        if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP) {
            return PIN_ENTRY_DELAY_SPARKLING_BUBBLES_MS;
        }
        if (effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET
                || effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED) {
            return PIN_ENTRY_DELAY_S6_WATER_DROPLET_MS;
        }
        if (effect == OverlayPrefs.EFFECT_MASS_TENSION) {
            return PIN_ENTRY_DELAY_MASS_TENSION_MS;
        }
        return PIN_ENTRY_DELAY_LENS_FLARE_MS;
    }

    /** Returns -1 for effects which retain their established recovered wrapper delay. */
    private static long tailCompletePinEntryDelayMs(int effect) {
        switch (effect) {
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                return PIN_ENTRY_DELAY_POPPING_COLOURS_TAIL_MS;
            case OverlayPrefs.EFFECT_BRILLIANT_CUT:
                return PIN_ENTRY_DELAY_BRILLIANT_CUT_TAIL_MS;
            case OverlayPrefs.EFFECT_MASS_TENSION:
                return PIN_ENTRY_DELAY_MASS_TENSION_TAIL_MS;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                return PIN_ENTRY_DELAY_GEOMETRIC_MOSAIC_TAIL_MS;
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                return PIN_ENTRY_DELAY_WATERCOLOUR_TAIL_MS;
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                return PIN_ENTRY_DELAY_ABSTRACT_TILES_TAIL_MS;
            case OverlayPrefs.EFFECT_BRILLIANT_RING:
                return PIN_ENTRY_DELAY_BRILLIANT_RING_TAIL_MS;
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                return PIN_ENTRY_DELAY_LENS_FLARE_TAIL_MS;
            case OverlayPrefs.EFFECT_S3_NONE:
                return PIN_ENTRY_DELAY_S3_NONE_TAIL_MS;
            case OverlayPrefs.EFFECT_LG_G2_PIXELATE:
                return PIN_ENTRY_DELAY_LG_G2_PIXELATE_TAIL_MS;
            case OverlayPrefs.EFFECT_LG_G2_PARTICLE:
                return PIN_ENTRY_DELAY_LG_G2_PARTICLE_TAIL_MS;
            case OverlayPrefs.EFFECT_LG_G2_CRYSTAL:
                return PIN_ENTRY_DELAY_LG_G2_CRYSTAL_TAIL_MS;
            case OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS:
                return PIN_ENTRY_DELAY_XPERIA_Z1_BLINDS_TAIL_MS;
            case OverlayPrefs.EFFECT_REVOLVING_GLASS:
                return PIN_ENTRY_DELAY_REVOLVING_GLASS_TAIL_MS;
            case OverlayPrefs.EFFECT_LG_G1_WHITE_HOLE:
                return PIN_ENTRY_DELAY_LG_G1_WHITE_HOLE_TAIL_MS;
            case OverlayPrefs.EFFECT_LG_SODA:
                return PIN_ENTRY_DELAY_LG_SODA_TAIL_MS;
            case OverlayPrefs.EFFECT_LG_G1_DEWDROP:
                return PIN_ENTRY_DELAY_LG_G1_DEWDROP_TAIL_MS;
            case OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE:
                return PIN_ENTRY_DELAY_LG_G2_LIGHT_PARTICLE_TAIL_MS;
            case OverlayPrefs.EFFECT_LG_G2_VECTOR:
                return PIN_ENTRY_DELAY_LG_G2_VECTOR_TAIL_MS;
            default:
                return -1L;
        }
    }

    private void openPinEntry() {
        if (scheduledPinEntrySafetyGeneration != inputSafetyGeneration
                || pinEntryHandoffSafetyGeneration != inputSafetyGeneration
                || isNotificationShadeInputBlocked()) {
            pinEntryPending = false;
            cancelPinEntryHandoff("input_safety_generation");
            clearPinEntryTrace();
            Log.i(TAG, "pin entry dropped by input safety generation");
            return;
        }
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
        String dispatchBlock = pinEntrySwipeDispatchBlockReason(false);
        if (dispatchBlock != null) {
            Log.i(TAG, "pin entry swipe not queued reason=" + dispatchBlock
                    + " locked=" + locked);
            finishBlockedPinEntryHandoff(dispatchBlock);
            return;
        }

        pinEntryRequested = true;
        if (retainsLgPreLockFrameDuringHandoff()) {
            if (!neutralizeTouchDebugOverlayForLgHandoff("pin_entry_requested")) {
                recoverTouchPathAfterPinEntryHandoffFailure("touch_window_neutralize_exception");
                return;
            }
            neutralizeUnlockEffectWindowForHandoff("pin_entry_requested");
            pinEntryHandoffPreparedAt = SystemClock.uptimeMillis();
            scheduleUnlockEffectCleanup();
            queueLgPinEntrySwipeAfterInputSettle(pinEntryHandoffPreparedAt);
            evaluateVisibility("pin_entry_requested");
            return;
        }
        if (!removeTouchDebugOverlayImmediatelyForHandoff("pin_entry_requested")) {
            recoverTouchPathAfterPinEntryHandoffFailure("touch_window_remove_exception");
            return;
        }
        // Full coverage is the Mosaic handoff boundary too. Use the normal Android 12+ window
        // neutralization so its internal fade is never presented over the PIN transition.
        // Some OEM builds lose the trusted-overlay classification for a renderer-backed
        // accessibility window during the synthetic unlock handoff. View.setAlpha(0) is
        // not sufficient for InputDispatcher: the pass-through exemption is based on the
        // WindowManager alpha.
        neutralizeUnlockEffectWindowForHandoff("pin_entry_requested");
        pinEntryHandoffPreparedAt = SystemClock.uptimeMillis();
        scheduleUnlockEffectCleanup();
        handler.removeCallbacks(pinEntrySwipeRunnable);
        queuedPinSwipeSafetyGeneration = inputSafetyGeneration;
        long swipeStartDelayMs = pinEntrySwipeStartDelayMs();
        pinEntryHandoffSwipeQueuedAt = SystemClock.uptimeMillis();
        handler.postDelayed(pinEntrySwipeRunnable, swipeStartDelayMs);
        Log.i(TAG, "pin entry swipe queued delayMs=" + swipeStartDelayMs
                + " sinceReleaseMs=" + sincePinEntryRelease(pinEntryHandoffSwipeQueuedAt)
                + " touchWindowsBefore=" + pinEntryHandoffTouchWindowsBefore
                + " touchWindowsAfter=" + pinEntryHandoffTouchWindowsAfter
                + " touchRemovalMs=" + pinEntryHandoffTouchRemovalElapsedMs
                + " effectAlpha=" + pinEntryHandoffWindowAlphaResult
                + " effectAlphaMs=" + pinEntryHandoffWindowAlphaElapsedMs);
        evaluateVisibility("pin_entry_requested");
    }

    private void queueLgPinEntrySwipeAfterInputSettle(final long handoffStartedAt) {
        final int handoffGeneration = pinEntryHandoffGeneration;
        final int safetyGeneration = inputSafetyGeneration;
        handler.removeCallbacks(pinEntrySwipeRunnable);
        Choreographer.getInstance().postFrameCallback(
                new Choreographer.FrameCallback() {
                    @Override
                    public void doFrame(long frameTimeNanos) {
                        if (!isLgHandoffGenerationCurrent(
                                handoffGeneration, safetyGeneration)) {
                            return;
                        }
                        long removeStartedAt = SystemClock.uptimeMillis();
                        boolean removed = removeTouchDebugOverlayImmediatelyForHandoff(
                                "lg_first_frame");
                        pinEntryHandoffTouchWindowsAfter = touchDebugWindowCount();
                        pinEntryHandoffTouchRemovalElapsedMs = Math.max(
                                0L, SystemClock.uptimeMillis() - removeStartedAt);
                        pinEntryHandoffTouchRemovalResult = removed
                                ? "neutralized_then_removed" : "frame_remove_exception";
                        if (!removed) {
                            recoverTouchPathAfterPinEntryHandoffFailure(
                                    "touch_window_frame_remove_exception");
                            return;
                        }
                        Choreographer.getInstance().postFrameCallback(
                                new Choreographer.FrameCallback() {
                                    @Override
                                    public void doFrame(long secondFrameTimeNanos) {
                                        if (!isLgHandoffGenerationCurrent(
                                                handoffGeneration, safetyGeneration)) {
                                            return;
                                        }
                                        long elapsed = Math.max(0L,
                                                SystemClock.uptimeMillis()
                                                        - handoffStartedAt);
                                        long remaining = Math.max(0L,
                                                PIN_ENTRY_SWIPE_START_DELAY_MS - elapsed);
                                        queuedPinSwipeSafetyGeneration = inputSafetyGeneration;
                                        pinEntryHandoffSwipeQueuedAt =
                                                SystemClock.uptimeMillis();
                                        handler.postDelayed(pinEntrySwipeRunnable, remaining);
                                        Log.i(TAG, "LG pin entry swipe queued after input settle"
                                                + " remainingMs=" + remaining
                                                + " elapsedMs=" + elapsed
                                                + " touchWindowsBefore="
                                                + pinEntryHandoffTouchWindowsBefore
                                                + " touchWindowsAfter="
                                                + pinEntryHandoffTouchWindowsAfter
                                                + " visual="
                                                + pinEntryHandoffWindowAlphaResult);
                                    }
                                });
                    }
                });
    }

    private boolean isLgHandoffGenerationCurrent(
            int handoffGeneration, int safetyGeneration) {
        return pinEntryRequested
                && pinEntryHandoffActive
                && handoffGeneration == pinEntryHandoffGeneration
                && safetyGeneration == inputSafetyGeneration
                && pinEntryHandoffSafetyGeneration == inputSafetyGeneration;
    }

    private long pinEntrySwipeStartDelayMs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && OverlayPrefs.debugConservativeUnlockHandoff(this)) {
            return PIN_ENTRY_SWIPE_START_DELAY_CONSERVATIVE_MS;
        }
        return PIN_ENTRY_SWIPE_START_DELAY_MS;
    }

    private void runPinEntrySwipe() {
        if (queuedPinSwipeSafetyGeneration != inputSafetyGeneration
                || pinEntryHandoffSafetyGeneration != inputSafetyGeneration
                || isNotificationShadeInputBlocked()) {
            Log.i(TAG, "pin entry swipe dropped by input safety generation");
            finishBlockedPinEntryHandoff("input_safety_generation");
            return;
        }
        int attempt = pinEntryHandoffAttempt + 1;
        String dispatchBlock = pinEntrySwipeDispatchBlockReason(attempt > 1);
        if (dispatchBlock != null) {
            Log.i(TAG, "pin entry swipe blocked attempt=" + attempt
                    + " reason=" + dispatchBlock);
            finishBlockedPinEntryHandoff(dispatchBlock);
            return;
        }
        pinEntryHandoffAttempt = attempt;
        performPinEntrySwipe(pinEntryHandoffGeneration, attempt);
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
        if (effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP) {
            return PIN_ENTRY_EFFECT_CLEANUP_DELAY_SPARKLING_BUBBLES_MS;
        }
        if (effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET
                || effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED) {
            return PIN_ENTRY_EFFECT_CLEANUP_DELAY_S6_WATER_DROPLET_MS;
        }
        return PIN_ENTRY_EFFECT_CLEANUP_DELAY_DEFAULT_MS;
    }

    private boolean performPinEntrySwipe(final int handoffGeneration, final int attempt) {
        if (queuedPinSwipeSafetyGeneration != inputSafetyGeneration
                || !pinEntryHandoffActive
                || handoffGeneration != pinEntryHandoffGeneration
                || attempt != pinEntryHandoffAttempt
                || isNotificationShadeInputBlocked()) {
            Log.i(TAG, "pin entry swipe blocked before dispatch");
            finishBlockedPinEntryHandoff("pre_dispatch_safety");
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "pin entry swipe unavailable below Android N");
            pinEntryHandoffLastCallback = "unavailable";
            recoverTouchPathAfterPinEntryHandoffFailure("gesture_api_unavailable");
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
        pinEntryHandoffAttemptDispatchedAt = SystemClock.uptimeMillis();
        pinEntryHandoffLastSafeScanAt = 0L;
        pinEntryTraceDispatchAt = pinEntryHandoffAttemptDispatchedAt;
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                long now = SystemClock.uptimeMillis();
                notePinEntryHandoffCallback(
                        handoffGeneration, attempt, "completed",
                        PIN_ENTRY_HANDOFF_VERIFY_DELAY_MS);
                Log.i(TAG, "pin entry swipe completed attempt=" + attempt
                        + " sinceReleaseMs=" + sincePinEntryRelease(now)
                        + " sinceDispatchMs=" + sincePinEntryDispatch(now));
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                long now = SystemClock.uptimeMillis();
                notePinEntryHandoffCallback(
                        handoffGeneration, attempt, "cancelled",
                        PIN_ENTRY_HANDOFF_CANCEL_VERIFY_DELAY_MS);
                Log.w(TAG, "pin entry swipe cancelled attempt=" + attempt
                        + " sinceReleaseMs=" + sincePinEntryRelease(now)
                        + " sinceDispatchMs=" + sincePinEntryDispatch(now));
            }
        }, handler);
        pinEntryHandoffLastCallback = accepted ? "accepted" : "rejected";
        schedulePinEntryHandoffVerification(
                handoffGeneration,
                attempt,
                accepted
                        ? PIN_ENTRY_SWIPE_DURATION_MS + PIN_ENTRY_HANDOFF_VERIFY_DELAY_MS
                        : PIN_ENTRY_HANDOFF_CANCEL_VERIFY_DELAY_MS,
                accepted ? "callback_watchdog" : "dispatch_rejected");
        Log.i(TAG, "pin entry swipe dispatched attempt=" + attempt
                + " accepted=" + accepted
                + " sinceReleaseMs=" + sincePinEntryRelease(pinEntryTraceDispatchAt)
                + " sinceOpenMs=" + sincePinEntryOpen(pinEntryTraceDispatchAt));
        return accepted;
    }

    private void beginPinEntryHandoff() {
        handler.removeCallbacks(pinEntryHandoffVerifyRunnable);
        pinEntryHandoffGeneration++;
        pinEntryHandoffSafetyGeneration = inputSafetyGeneration;
        pinEntryHandoffAttempt = 0;
        pinEntryHandoffVerifyGeneration = 0;
        pinEntryHandoffVerifyAttempt = 0;
        pinEntryHandoffAttemptDispatchedAt = 0L;
        pinEntryHandoffLastSafeScanAt = 0L;
        pinEntryHandoffPreparedAt = 0L;
        pinEntryHandoffSwipeQueuedAt = 0L;
        pinEntryHandoffTouchRemovalElapsedMs = -1L;
        pinEntryHandoffWindowAlphaElapsedMs = -1L;
        pinEntryHandoffTouchWindowsBefore = -1;
        pinEntryHandoffTouchWindowsAfter = -1;
        pinEntryHandoffLastCallback = "none";
        pinEntryHandoffLastTerminal = "pending";
        pinEntryHandoffLastOutcome = "pending";
        pinEntryHandoffTouchRemovalMode = "pending";
        pinEntryHandoffTouchRemovalResult = "pending";
        pinEntryHandoffWindowAlphaResult = "pending";
        pinEntryHandoffFailOpen = false;
        pinEntryHandoffActive = true;
    }

    private void cancelPinEntryHandoff(String reason) {
        handler.removeCallbacks(pinEntryHandoffVerifyRunnable);
        if (!pinEntryHandoffActive) {
            return;
        }
        pinEntryHandoffActive = false;
        pinEntryHandoffGeneration++;
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean keyguardLocked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        boolean deviceLocked = keyguardManager != null && keyguardManager.isDeviceLocked();
        pinEntryHandoffLastObservedAt = SystemClock.uptimeMillis();
        pinEntryHandoffLastInteractive = interactive;
        pinEntryHandoffLastKeyguardLocked = keyguardLocked;
        pinEntryHandoffLastDeviceLocked = deviceLocked;
        if (interactive && keyguardManager != null && !keyguardLocked) {
            pinEntryHandoffLastTerminal = "keyguard_dismissed";
            pinEntryHandoffLastOutcome = "success:keyguard_dismissed";
        } else {
            pinEntryHandoffLastOutcome = "cancelled:" + reason;
        }
        Log.i(TAG, "pin entry handoff cancelled reason=" + reason
                + " attempt=" + pinEntryHandoffAttempt);
    }

    private void notePinEntryHandoffCallback(
            int handoffGeneration, int attempt, String result, long verifyDelayMs) {
        if (!pinEntryHandoffActive
                || handoffGeneration != pinEntryHandoffGeneration
                || attempt != pinEntryHandoffAttempt) {
            return;
        }
        pinEntryHandoffLastCallback = result;
        requestContentBlockedSurfaceScan("pin_handoff_callback:" + result);
        schedulePinEntryHandoffVerification(
                handoffGeneration, attempt, verifyDelayMs, "callback:" + result);
    }

    private void schedulePinEntryHandoffVerification(
            int handoffGeneration, int attempt, long delayMs, String reason) {
        if (!pinEntryHandoffActive
                || handoffGeneration != pinEntryHandoffGeneration
                || attempt != pinEntryHandoffAttempt) {
            return;
        }
        pinEntryHandoffVerifyGeneration = handoffGeneration;
        pinEntryHandoffVerifyAttempt = attempt;
        handler.removeCallbacks(pinEntryHandoffVerifyRunnable);
        handler.postDelayed(pinEntryHandoffVerifyRunnable, Math.max(0L, delayMs));
        Log.i(TAG, "pin entry handoff verification scheduled attempt=" + attempt
                + " delayMs=" + delayMs
                + " reason=" + reason);
    }

    private void verifyPinEntryHandoff() {
        int handoffGeneration = pinEntryHandoffVerifyGeneration;
        int attempt = pinEntryHandoffVerifyAttempt;
        if (!pinEntryHandoffActive
                || handoffGeneration != pinEntryHandoffGeneration
                || attempt != pinEntryHandoffAttempt) {
            return;
        }

        String terminal = observePinEntryHandoffTerminal();
        Log.i(TAG, "pin entry handoff observed attempt=" + attempt
                + " callback=" + pinEntryHandoffLastCallback
                + " terminal=" + terminal
                + " interactive=" + pinEntryHandoffLastInteractive
                + " keyguardLocked=" + pinEntryHandoffLastKeyguardLocked
                + " deviceLocked=" + pinEntryHandoffLastDeviceLocked);
        if ("pin_surface".equals(terminal) || "keyguard_dismissed".equals(terminal)) {
            finishSuccessfulPinEntryHandoff(terminal);
            return;
        }
        if (!"ordinary_lockscreen".equals(terminal)) {
            finishBlockedPinEntryHandoff(terminal);
            return;
        }
        if (attempt >= 2) {
            recoverTouchPathAfterPinEntryHandoffFailure("retry_still_locked");
            return;
        }

        long now = SystemClock.uptimeMillis();
        boolean safeScanObserved = pinEntryHandoffAttemptDispatchedAt > 0L
                && pinEntryHandoffLastSafeScanAt >= pinEntryHandoffAttemptDispatchedAt;
        if (!safeScanObserved) {
            long sinceDispatchMs = pinEntryHandoffAttemptDispatchedAt <= 0L
                    ? Long.MAX_VALUE
                    : now - pinEntryHandoffAttemptDispatchedAt;
            if (sinceDispatchMs < PIN_ENTRY_HANDOFF_SCAN_WAIT_MAX_MS) {
                requestContentBlockedSurfaceScan("pin_handoff_safe_retry_probe");
                schedulePinEntryHandoffVerification(
                        handoffGeneration,
                        attempt,
                        PIN_ENTRY_HANDOFF_SCAN_RECHECK_MS,
                        "await_safe_surface_scan");
                return;
            }
            recoverTouchPathAfterPinEntryHandoffFailure("safe_scan_unconfirmed");
            return;
        }

        String retryBlock = pinEntrySwipeDispatchBlockReason(true);
        if (retryBlock != null) {
            finishBlockedPinEntryHandoff(retryBlock);
            return;
        }
        pinEntryHandoffLastCallback = "retry_queued";
        Log.i(TAG, "pin entry handoff retrying after verified ordinary lockscreen");
        runPinEntrySwipe();
    }

    private String observePinEntryHandoffTerminal() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean keyguardLocked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        boolean deviceLocked = keyguardManager != null && keyguardManager.isDeviceLocked();
        pinEntryHandoffLastObservedAt = SystemClock.uptimeMillis();
        pinEntryHandoffLastInteractive = interactive;
        pinEntryHandoffLastKeyguardLocked = keyguardLocked;
        pinEntryHandoffLastDeviceLocked = deviceLocked;

        String terminal;
        if (!interactive) {
            terminal = "not_interactive";
        } else if (keyguardManager == null) {
            terminal = "keyguard_state_unavailable";
        } else if (!keyguardLocked) {
            terminal = "keyguard_dismissed";
        } else if (isCurrentPinEntrySurfaceForHandoff()) {
            terminal = "pin_surface";
        } else if (notificationShadeVisible || notificationShadeSuspected) {
            terminal = "notification_shade";
        } else if (globalActionsVisible) {
            terminal = "global_actions";
        } else if (activeRuntimeBlockPackage != null
                || isRuntimeSurfaceBlockPackage(lastWindowPackage)
                || isCallAudioActive()) {
            terminal = "runtime_block";
        } else if (isHomePackage(lastWindowPackage)) {
            terminal = "launcher";
        } else if (SYSTEM_UI_PACKAGE.equals(lastWindowPackage)) {
            terminal = "ordinary_lockscreen";
        } else {
            terminal = "external_or_unknown_surface";
        }
        pinEntryHandoffLastTerminal = terminal;
        return terminal;
    }

    private String pinEntrySwipeDispatchBlockReason(boolean retry) {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean keyguardLocked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        if (!interactive) {
            return "not_interactive";
        }
        if (!keyguardLocked) {
            return "keyguard_dismissed";
        }
        if (isCurrentPinEntrySurfaceForHandoff()) {
            return "pin_surface";
        }
        if (notificationShadeVisible || notificationShadeSuspected) {
            return "notification_shade";
        }
        if (globalActionsVisible) {
            return "global_actions";
        }
        if (activeRuntimeBlockPackage != null
                || isRuntimeSurfaceBlockPackage(lastWindowPackage)
                || isCallAudioActive()) {
            return "runtime_block";
        }
        if (isHomePackage(lastWindowPackage)) {
            return "launcher";
        }
        if (retry && !SYSTEM_UI_PACKAGE.equals(lastWindowPackage)) {
            return "non_ordinary_retry_surface";
        }
        return null;
    }

    private void finishSuccessfulPinEntryHandoff(String terminal) {
        handler.removeCallbacks(pinEntryHandoffVerifyRunnable);
        pinEntryHandoffActive = false;
        pinEntryHandoffLastTerminal = terminal;
        pinEntryHandoffLastOutcome = "success:" + terminal;
        Log.i(TAG, "pin entry handoff complete terminal=" + terminal
                + " attempts=" + pinEntryHandoffAttempt
                + " callback=" + pinEntryHandoffLastCallback);
        if ("pin_surface".equals(terminal)) {
            pinEntryPending = false;
            pinEntryRequested = true;
            removeTouchDebugOverlay();
            if (pinEntryHandoffAttempt == 0) {
                scheduleUnlockEffectCleanup();
            }
        } else if ("keyguard_dismissed".equals(terminal)) {
            pinEntryPending = false;
            pinEntryRequested = false;
            clearPinEntryTrace();
            evaluateVisibility("pin_entry_handoff:keyguard_dismissed", false);
        }
    }

    private boolean isCurrentPinEntrySurfaceForHandoff() {
        // pinEntryLastSeenAt is historical evidence and intentionally not sufficient:
        // the bouncer may have appeared after dispatch and already returned to the
        // ordinary lockscreen before verification. The live surface state is cleared by
        // the bounded scan; a foreground keyboard is independently authoritative.
        return pinEntrySurfaceVisible || isKeyboardPackage(lastWindowPackage);
    }

    private void finishBlockedPinEntryHandoff(String terminal) {
        if ("keyguard_dismissed".equals(terminal) || "pin_surface".equals(terminal)) {
            finishSuccessfulPinEntryHandoff(terminal);
            return;
        }
        handler.removeCallbacks(pinEntryHandoffVerifyRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        pinEntryHandoffActive = false;
        pinEntryHandoffLastTerminal = terminal;
        pinEntryHandoffLastOutcome = "blocked:" + terminal;
        pinEntryPending = false;
        pinEntryRequested = false;
        clearPinEntryTrace();
        removeTouchDebugOverlay();
        Log.i(TAG, "pin entry handoff stopped terminal=" + terminal
                + " attempts=" + pinEntryHandoffAttempt
                + " callback=" + pinEntryHandoffLastCallback);
        evaluateVisibility("pin_entry_handoff:blocked:" + terminal, false);
    }

    private void recoverTouchPathAfterPinEntryHandoffFailure(String reason) {
        handler.removeCallbacks(pinEntryHandoffVerifyRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        pinEntryHandoffActive = false;
        pinEntryHandoffLastTerminal = "ordinary_lockscreen";
        pinEntryHandoffLastOutcome = "failed_rearmed:" + reason;
        pinEntryPending = false;
        pinEntryRequested = false;
        pinEntrySurfaceSeen = false;
        pinEntrySurfaceVisible = false;
        pinEntryLastSeenAt = 0L;
        clearPinEntryTrace();
        // Fail open for this lock cycle. Re-attaching the touch box after a failed handoff
        // (including an unverified bounded scan on slow hardware) can keep an OEM
        // InputDispatcher in the exact state that makes the lockscreen unusable until reboot.
        // With every LLE input window gone, the user can always perform the stock unlock
        // gesture; the next real lock cycle clears this latch.
        pinEntryHandoffFailOpen = true;
        lockCycleSafetyBypassActive = true;
        unlockFxVisible = false;
        removeTouchDebugOverlayImmediatelyForHandoff("handoff_failure:" + reason);
        removeUnlockEffectOverlay();
        destroySeasonalUnlockPartnerOverlay();
        Log.w(TAG, "pin entry handoff failed; stock lockscreen fail-open reason=" + reason
                + " attempts=" + pinEntryHandoffAttempt
                + " callback=" + pinEntryHandoffLastCallback);
        evaluateVisibility("pin_entry_handoff:fail_open:" + reason, true);
    }

    private void notePinEntryHandoffSurfaceScan(
            long requestedAt, int quality, int surfaces) {
        if (!pinEntryHandoffActive
                || quality != BLOCKED_SURFACE_SCAN_SUCCESS
                || requestedAt < pinEntryHandoffAttemptDispatchedAt) {
            return;
        }
        if (surfaces == 0) {
            pinEntryHandoffLastSafeScanAt = SystemClock.uptimeMillis();
        } else {
            pinEntryHandoffLastSafeScanAt = 0L;
        }
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

    private void loadRuntimeSurfaceBlacklistPackages() {
        runtimeSurfaceBlacklistPackages.clear();
        for (int i = 0; i < RUNTIME_SURFACE_BLACKLIST_PACKAGES.length; i++) {
            runtimeSurfaceBlacklistPackages.add(RUNTIME_SURFACE_BLACKLIST_PACKAGES[i]);
        }
        runtimeSurfaceBlacklistPackages.addAll(
                OverlayPrefs.userRuntimeBlacklistPackages(this));
    }

    static boolean isBuiltInRuntimeBlacklistPackage(String packageName) {
        String normalized = OverlayPrefs.normalizePackageName(packageName);
        for (int i = 0; i < RUNTIME_SURFACE_BLACKLIST_PACKAGES.length; i++) {
            if (RUNTIME_SURFACE_BLACKLIST_PACKAGES[i].equals(normalized)) {
                return true;
            }
        }
        for (int i = 0; i < CALL_SURFACE_PACKAGES.length; i++) {
            if (CALL_SURFACE_PACKAGES[i].equals(normalized)) {
                return true;
            }
        }
        return false;
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

    private boolean isRuntimeSurfaceBlockPackage(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.toString().toLowerCase();
        return runtimeSurfaceBlacklistPackages.contains(value) || isCallPackage(value);
    }

    private void noteActiveRuntimeBlock(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }
        String packageName = OverlayPrefs.normalizePackageName(
                event.getPackageName().toString());
        if (packageName.isEmpty()) {
            return;
        }
        int windowId = event.getWindowId();
        boolean changed = !packageName.equals(activeRuntimeBlockPackage)
                || windowId != activeRuntimeBlockWindowId;
        activeRuntimeBlockPackage = packageName;
        activeRuntimeBlockWindowId = windowId;
        activeRuntimeBlockLastSeenAt = SystemClock.uptimeMillis();
        handler.removeCallbacks(runtimeBlockWindowValidationRunnable);
        handler.postDelayed(
                runtimeBlockWindowValidationRunnable, RUNTIME_BLOCK_WINDOW_RECHECK_MS);
        hideRuntimeSurfacesForBlockedPackage("event_latch:" + packageName);
        if (changed) {
            Log.i(TAG, "runtime block latched pkg=" + packageName
                    + " windowId=" + windowId);
        }
    }

    private void validateActiveRuntimeBlockWindow() {
        if (activeRuntimeBlockPackage == null) {
            return;
        }
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (!interactive || !isLockscreenLocked(false)) {
            clearActiveRuntimeBlock(interactive ? "keyguard_unlocked" : "not_interactive");
            evaluateVisibility("runtime_block:state_clear", false);
            return;
        }
        long now = SystemClock.uptimeMillis();
        RuntimeSurfaceBlockState.ActiveWindowState windowState =
                activeRuntimeBlockWindowState();
        if (windowState != RuntimeSurfaceBlockState.ActiveWindowState.ABSENT) {
            activeRuntimeBlockLastSeenAt = now;
            handler.postDelayed(
                    runtimeBlockWindowValidationRunnable, RUNTIME_BLOCK_WINDOW_RECHECK_MS);
            return;
        }
        long missingForMs = now - activeRuntimeBlockLastSeenAt;
        if (missingForMs < RUNTIME_BLOCK_WINDOW_CLEAR_GRACE_MS) {
            handler.postDelayed(runtimeBlockWindowValidationRunnable,
                    RUNTIME_BLOCK_WINDOW_CLEAR_GRACE_MS - missingForMs);
            return;
        }
        clearActiveRuntimeBlock("window_gone", windowState);
        evaluateVisibility("runtime_block:window_gone", true);
    }

    private RuntimeSurfaceBlockState.ActiveWindowState activeRuntimeBlockWindowState() {
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (RuntimeException e) {
            Log.w(TAG, "runtime block window scan failed", e);
            return RuntimeSurfaceBlockState.ActiveWindowState.UNKNOWN;
        }
        if (windows == null) {
            return RuntimeSurfaceBlockState.ActiveWindowState.UNKNOWN;
        }
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window == null || !isActiveOrFocusedWindow(window)) {
                continue;
            }
            boolean sameWindow = activeRuntimeBlockWindowId >= 0
                    && window.getId() == activeRuntimeBlockWindowId;
            AccessibilityNodeInfo root = null;
            try {
                root = window.getRoot();
                String packageName = root == null || root.getPackageName() == null
                        ? ""
                        : OverlayPrefs.normalizePackageName(
                                root.getPackageName().toString());
                if (activeRuntimeBlockPackage.equals(packageName)
                        || (sameWindow && isRuntimeSurfaceBlockPackage(packageName))) {
                    return RuntimeSurfaceBlockState.ActiveWindowState.PRESENT;
                }
                if (root == null) {
                    // A root-less active/focused window cannot disprove that an OEM
                    // transient is still present, even when its previous ID vanished.
                    return RuntimeSurfaceBlockState.ActiveWindowState.UNKNOWN;
                }
            } catch (RuntimeException e) {
                return RuntimeSurfaceBlockState.ActiveWindowState.UNKNOWN;
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
        }
        return RuntimeSurfaceBlockState.ActiveWindowState.ABSENT;
    }

    private void clearActiveRuntimeBlock(String reason) {
        clearActiveRuntimeBlock(reason, RuntimeSurfaceBlockState.ActiveWindowState.UNKNOWN);
    }

    private void clearActiveRuntimeBlock(String reason,
            RuntimeSurfaceBlockState.ActiveWindowState windowState) {
        handler.removeCallbacks(runtimeBlockWindowValidationRunnable);
        if (activeRuntimeBlockPackage == null) {
            activeRuntimeBlockWindowId = -1;
            activeRuntimeBlockLastSeenAt = 0L;
            return;
        }
        Log.i(TAG, "runtime block cleared reason=" + reason
                + " pkg=" + activeRuntimeBlockPackage
                + " windowId=" + activeRuntimeBlockWindowId);
        String clearedPackage = activeRuntimeBlockPackage;
        activeRuntimeBlockPackage = null;
        activeRuntimeBlockWindowId = -1;
        activeRuntimeBlockLastSeenAt = 0L;
        String normalizedLastWindowPackage = lastWindowPackage == null
                ? null : OverlayPrefs.normalizePackageName(lastWindowPackage);
        if (RuntimeSurfaceBlockState.shouldClearStaleLastWindowPackage(
                clearedPackage, normalizedLastWindowPackage, windowState)) {
            lastWindowPackage = null;
            Log.i(TAG, "runtime block retired stale event package pkg=" + clearedPackage);
        }
    }

    private boolean isRuntimeSurfaceBlocked() {
        return globalActionsVisible
                || activeRuntimeBlockPackage != null
                || isRuntimeSurfaceBlockPackage(lastWindowPackage)
                || isCallAudioActive();
    }

    private boolean isCallAudioActive() {
        if (audioManager == null) {
            return false;
        }
        int mode = audioManager.getMode();
        return mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL;
    }

    private void requestContentBlockedSurfaceScan(final String reason) {
        if (!serviceAlive || !lockscreenSessionPolling || blockedSurfaceScanInFlight) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long minimumIntervalMs = notificationShadeVisible
                && !notificationShadeProbePending
                ? NOTIFICATION_SHADE_SCAN_MIN_INTERVAL_MS
                : BLOCKED_SURFACE_SCAN_MIN_INTERVAL_MS;
        if (lastBlockedSurfaceScanRequestedAt > 0L
                && now - lastBlockedSurfaceScanRequestedAt < minimumIntervalMs) {
            return;
        }
        lastBlockedSurfaceScanRequestedAt = now;
        blockedSurfaceScanInFlight = true;
        final int lifecycleGeneration = serviceLifecycleGeneration;
        final int sessionGeneration = lockscreenSessionGeneration;
        final int requestId = ++blockedSurfaceScanRequestGeneration;
        blockedSurfaceScanInFlightRequestId = requestId;
        final long requestedAt = SystemClock.uptimeMillis();
        final boolean pinEntryRequestedSnapshot = pinEntryRequested;
        // The legacy toggle controls only Quick Panel routing. Unlock handoff always
        // needs the bounded detector's quality result before a second gesture is safe.
        final boolean legacyQuickPanelDetection =
                OverlayPrefs.debugLegacyQuickPanelDetection(this)
                        && !pinEntryHandoffActive
                        && !pinEntryRequested
                        && !pinEntrySurfaceSeen
                        && !pinEntrySurfaceVisible;
        final boolean deepNodeScanNeeded = pinEntryPending
                || pinEntryRequestedSnapshot
                || pinEntrySurfaceSeen
                || pinEntrySurfaceVisible
                || globalActionsVisible
                || notificationShadeVisible
                || (!legacyQuickPanelDetection
                && (notificationShadeSuspected || notificationShadeProbePending));
        final boolean extendedShadeScan = !legacyQuickPanelDetection
                && (notificationShadeVisible
                || notificationShadeSuspected)
                && notificationShadeNeedsExtendedScan;
        if (deepNodeScanNeeded) {
            notificationShadeProbePending = false;
        }
        try {
            blockedSurfaceScanExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final BlockedSurfaceScanResult result = legacyQuickPanelDetection
                            ? detectLegacyContentBlockedSurfaces(
                                    deepNodeScanNeeded, pinEntryRequestedSnapshot)
                            : detectContentBlockedSurfaces(
                                    deepNodeScanNeeded, pinEntryRequestedSnapshot,
                                    extendedShadeScan);
                    final long elapsedMs = SystemClock.uptimeMillis() - requestedAt;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (requestId != blockedSurfaceScanInFlightRequestId) {
                                return;
                            }
                            blockedSurfaceScanInFlight = false;
                            if (!serviceAlive
                                    || lifecycleGeneration != serviceLifecycleGeneration
                                    || sessionGeneration != lockscreenSessionGeneration
                                    || !lockscreenSessionPolling) {
                                return;
                            }
                            if (legacyQuickPanelDetection) {
                                applyLegacyContentBlockedSurfaceScanResult(
                                        result.surfaces, reason, requestedAt, elapsedMs);
                            } else {
                                applyContentBlockedSurfaceScanResult(
                                        result, reason, requestedAt, elapsedMs);
                            }
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            blockedSurfaceScanInFlight = false;
            Log.w(TAG, "blocked surface scan rejected", e);
        }
    }

    private void applyLegacyContentBlockedSurfaceScanResult(
            int surfaces, String reason, long requestedAt, long elapsedMs) {
        long now = SystemClock.uptimeMillis();
        boolean wasPinEntryVisible = pinEntrySurfaceVisible;
        boolean wasNotificationShadeVisible = notificationShadeVisible;
        boolean pinObservedSinceRequest = pinEntryLastSeenAt >= requestedAt
                && pinEntryLastSeenAt > 0L;
        boolean shadeObservedSinceRequest = notificationShadeLastSeenAt >= requestedAt
                && notificationShadeLastSeenAt > 0L;
        boolean detectedPinEntry = (surfaces & BLOCKED_SURFACE_PIN_ENTRY) != 0
                || (pinObservedSinceRequest && wasPinEntryVisible);
        boolean detectedNotificationShade =
                (surfaces & BLOCKED_SURFACE_NOTIFICATION_SHADE) != 0
                        || (shadeObservedSinceRequest && wasNotificationShadeVisible);
        boolean detectedGlobalActions =
                (surfaces & BLOCKED_SURFACE_GLOBAL_ACTIONS) != 0;
        if (detectedGlobalActions) {
            showGlobalActionsSuppression();
        } else if (globalActionsVisible) {
            clearGlobalActionsSuppression("legacy_structural_scan", false);
        }
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
        if (pinEntrySurfaceVisible != wasPinEntryVisible) {
            Log.i(TAG, "pin entry surface visible=" + pinEntrySurfaceVisible);
        }
        if (notificationShadeVisible != wasNotificationShadeVisible) {
            Log.i(TAG, "notification shade visible=" + notificationShadeVisible
                    + " detector=legacy_1.0.5.3");
        }
        if (pinEntrySurfaceVisible) {
            pinEntrySurfaceSeen = true;
        }
        if (elapsedMs > LOCKSCREEN_SESSION_STABLE_CONTENT_POLL_MS) {
            Log.w(TAG, "blocked surface async scan slow elapsedMs=" + elapsedMs
                    + " detector=legacy_1.0.5.3");
        }
        evaluateVisibility("async_surface_scan:" + reason, false);
    }

    private void applyContentBlockedSurfaceScanResult(
            BlockedSurfaceScanResult result, String reason,
            long requestedAt, long elapsedMs) {
        long now = SystemClock.uptimeMillis();
        notePinEntryHandoffSurfaceScan(requestedAt, result.quality, result.surfaces);
        boolean wasPinEntryVisible = pinEntrySurfaceVisible;
        boolean wasNotificationShadeVisible = notificationShadeVisible;
        boolean pinObservedSinceRequest = pinEntryLastSeenAt >= requestedAt
                && pinEntryLastSeenAt > 0L;
        boolean shadeObservedSinceRequest = notificationShadeLastSeenAt >= requestedAt
                && notificationShadeLastSeenAt > 0L;
        boolean detectedPinEntry = (result.surfaces & BLOCKED_SURFACE_PIN_ENTRY) != 0
                || (pinObservedSinceRequest && wasPinEntryVisible);
        boolean detectedNotificationShade =
                (result.surfaces & BLOCKED_SURFACE_NOTIFICATION_SHADE) != 0
                        || (shadeObservedSinceRequest && wasNotificationShadeVisible);
        boolean structuralShadeMatch =
                (result.surfaces & BLOCKED_SURFACE_NOTIFICATION_SHADE) != 0;
        boolean detectedGlobalActions =
                (result.surfaces & BLOCKED_SURFACE_GLOBAL_ACTIONS) != 0;
        if (detectedGlobalActions) {
            showGlobalActionsSuppression();
        } else if (globalActionsVisible
                && result.quality == BLOCKED_SURFACE_SCAN_SUCCESS
                && requestedAt >= globalActionsShownAt) {
            clearGlobalActionsSuppression("verified_structural_scan", false);
        }
        boolean shadeDiagnosticRelevant = result.deepScanPerformed
                && (structuralShadeMatch
                || notificationShadeSuspected
                || notificationShadeVisible
                || (reason != null && reason.contains("shade")));
        if (shadeDiagnosticRelevant) {
            captureNotificationShadeOemDiagnostic(
                    result, reason, structuralShadeMatch, now);
        }
        if (result.deepScanPerformed
                && now - lastBlockedSurfaceScanDiagnosticAt
                        >= BLOCKED_SURFACE_SCAN_DIAGNOSTIC_INTERVAL_MS) {
            lastBlockedSurfaceScanDiagnosticAt = now;
            Log.i(TAG, "blocked surface scan result"
                    + " surfaces=" + result.surfaces
                    + " quality=" + result.quality
                    + " windows=" + result.windowsVisited
                    + " roots=" + result.systemUiRootsScanned
                    + " nodes=" + result.nodesVisited
                    + " exhausted=" + result.budgetExhausted
                    + " shadeMatch=" + (result.shadeMatch == null
                            ? "-" : result.shadeMatch));
        }
        if ((result.surfaces & BLOCKED_SURFACE_NOTIFICATION_SHADE) != 0
                && now - lastNotificationShadeStructuralLogAt
                        >= NOTIFICATION_SHADE_STRUCTURAL_LOG_INTERVAL_MS) {
            lastNotificationShadeStructuralLogAt = now;
            Log.i(TAG, "notification shade structural match"
                    + " quality=" + result.quality
                    + " windows=" + result.windowsVisited
                    + " roots=" + result.systemUiRootsScanned
                    + " nodes=" + result.nodesVisited
                    + " exhausted=" + result.budgetExhausted
                    + " shadeMatch=" + (result.shadeMatch == null
                            ? "-" : result.shadeMatch));
        }
        if (detectedPinEntry) {
            pinEntryLastSeenAt = now;
            pinEntrySurfaceVisible = true;
        } else if (result.quality == BLOCKED_SURFACE_SCAN_SUCCESS) {
            pinEntrySurfaceVisible = false;
        } else if (wasPinEntryVisible
                && result.quality == BLOCKED_SURFACE_SCAN_PARTIAL) {
            // A 12 ms scan frequently exhausts on low-end tablets while the bouncer is
            // already visible. Never let one partial negative flicker the touch box back
            // over PIN. After the PIN events have genuinely stopped, repeated partial
            // negatives may clear the latch after a conservative stable interval.
            boolean pinEvidenceStale = pinEntryLastSeenAt > 0L
                    && requestedAt >= pinEntryLastSeenAt
                    && now - pinEntryLastSeenAt >= PIN_ENTRY_PARTIAL_CLEAR_STABLE_MS;
            pinEntrySurfaceVisible = !pinEvidenceStale;
            if (pinEvidenceStale) {
                Log.i(TAG, "pin entry surface cleared after stable partial negatives");
            }
        } else {
            pinEntrySurfaceVisible = wasPinEntryVisible;
        }
        if (detectedNotificationShade) {
            confirmNotificationShade("scan:" + reason);
        } else if (result.quality == BLOCKED_SURFACE_SCAN_SUCCESS) {
            if (wasNotificationShadeVisible) {
                long sincePositiveMs = notificationShadeLastSeenAt <= 0L
                        ? Long.MAX_VALUE : now - notificationShadeLastSeenAt;
                if (sincePositiveMs >= BLOCKED_SURFACE_CLEAR_GRACE_MS
                        && (notificationShadeLastClearScanAt <= 0L
                        || now - notificationShadeLastClearScanAt
                                >= BLOCKED_SURFACE_CLEAR_GRACE_MS)) {
                    notificationShadeLastClearScanAt = now;
                    notificationShadeClearSuccessCount++;
                }
                if (notificationShadeClearSuccessCount
                        >= NOTIFICATION_SHADE_CLEAR_SUCCESS_COUNT
                        && sincePositiveMs >= NOTIFICATION_SHADE_CLEAR_STABLE_MS) {
                    notificationShadeVisible = false;
                    notificationShadeSuspected = false;
                    notificationShadeOemPositiveLoggedForCurrentVisibility = false;
                    notificationShadeLastSeenAt = 0L;
                    notificationShadeLastClearScanAt = 0L;
                    notificationShadeClearSuccessCount = 0;
                    notificationShadeNeedsExtendedScan = false;
                    Log.i(TAG, "notification shade cleared by verified scans");
                }
            } else if (notificationShadeSuspected
                    && requestedAt >= notificationShadeSuspectedAt) {
                notificationShadeSuspected = false;
                notificationShadeSuspectedAt = 0L;
                handler.removeCallbacks(notificationShadeProbeRecheckRunnable);
                notificationShadeLastClearScanAt = 0L;
                notificationShadeClearSuccessCount = 0;
                notificationShadeNeedsExtendedScan = false;
                Log.i(TAG, "notification shade probe cleared by structural scan");
            } else if (notificationShadeSuspected) {
                Log.i(TAG, "notification shade probe kept after stale scan"
                        + " requestedAt=" + requestedAt
                        + " suspectedAt=" + notificationShadeSuspectedAt);
            }
        } else if (wasNotificationShadeVisible
                && result.quality == BLOCKED_SURFACE_SCAN_PARTIAL
                && !detectedNotificationShade) {
            if (!notificationShadeNeedsExtendedScan) {
                Log.i(TAG, "notification shade partial negative; extended clear scan armed"
                        + " nodes=" + result.nodesVisited
                        + " exhausted=" + result.budgetExhausted);
            }
            notificationShadeNeedsExtendedScan = true;
        }
        if (notificationShadeSuspected && !notificationShadeVisible) {
            handler.removeCallbacks(notificationShadeProbeRecheckRunnable);
            handler.postDelayed(notificationShadeProbeRecheckRunnable,
                    NOTIFICATION_SHADE_PROBE_RECHECK_MS);
        }
        if (pinEntrySurfaceVisible != wasPinEntryVisible) {
            Log.i(TAG, "pin entry surface visible=" + pinEntrySurfaceVisible);
        }
        if (notificationShadeVisible != wasNotificationShadeVisible) {
            Log.i(TAG, "notification shade visible=" + notificationShadeVisible);
        }
        if (pinEntrySurfaceVisible) {
            pinEntrySurfaceSeen = true;
        }
        if (elapsedMs > LOCKSCREEN_SESSION_STABLE_CONTENT_POLL_MS) {
            Log.w(TAG, "blocked surface async scan slow elapsedMs=" + elapsedMs
                    + " quality=" + result.quality
                    + " windows=" + result.windowsVisited
                    + " roots=" + result.systemUiRootsScanned
                    + " nodes=" + result.nodesVisited
                    + " exhausted=" + result.budgetExhausted);
        }
        evaluateVisibility("async_surface_scan:" + reason, false);
    }

    private void captureNotificationShadeOemDiagnostic(
            BlockedSurfaceScanResult result, String reason,
            boolean structuralShadeMatch, long now) {
        String windows = nonEmptyShadeDiagnostic(result.windowSignature);
        String nodes = nonEmptyShadeDiagnostic(result.nodeSignature);
        boolean firstStructuralMatch = structuralShadeMatch
                && !notificationShadeOemPositiveLoggedForCurrentVisibility;
        if (structuralShadeMatch) {
            notificationShadeOemPositiveLoggedForCurrentVisibility = true;
        }
        lastNotificationShadeDiagnosticCapturedAt = now;
        lastNotificationShadeDiagnosticQuality = result.quality;
        lastNotificationShadeDiagnosticWindows = result.windowsVisited;
        lastNotificationShadeDiagnosticRoots = result.systemUiRootsScanned;
        lastNotificationShadeDiagnosticNodes = result.nodesVisited;
        lastNotificationShadeDiagnosticMatched = structuralShadeMatch;
        lastNotificationShadeDiagnosticExhausted = result.budgetExhausted;
        lastNotificationShadeDiagnosticReason = reason == null ? "<none>" : reason;
        lastNotificationShadeWindowSignature = windows;
        lastNotificationShadeNodeSignature = nodes;
        if (structuralShadeMatch) {
            lastConfirmedNotificationShadeWindowSignature = windows;
            lastConfirmedNotificationShadeNodeSignature = nodes;
        }

        if (!firstStructuralMatch
                && now - lastNotificationShadeOemDiagnosticAt
                        < NOTIFICATION_SHADE_OEM_DIAGNOSTIC_INTERVAL_MS) {
            return;
        }
        lastNotificationShadeOemDiagnosticAt = now;
        Log.i(TAG, "shade OEM diagnostic"
                + " reason=" + lastNotificationShadeDiagnosticReason
                + " matched=" + structuralShadeMatch
                + " quality=" + result.quality
                + " windows=" + result.windowsVisited
                + " roots=" + result.systemUiRootsScanned
                + " nodes=" + result.nodesVisited
                + " exhausted=" + result.budgetExhausted
                + " shadeMatch=" + (result.shadeMatch == null
                        ? "-" : result.shadeMatch));
        logNotificationShadeDiagnosticChunks("windows", windows);
        logNotificationShadeDiagnosticChunks("visibleNodes", nodes);
    }

    private String nonEmptyShadeDiagnostic(String value) {
        return value == null || value.length() == 0 ? "<none>" : value;
    }

    private void logNotificationShadeDiagnosticChunks(String label, String value) {
        String safeValue = nonEmptyShadeDiagnostic(value);
        ArrayList<String> chunks = new ArrayList<String>();
        int start = 0;
        while (start < safeValue.length()) {
            int end = Math.min(safeValue.length(),
                    start + NOTIFICATION_SHADE_OEM_LOG_CHUNK_CHARS);
            if (end < safeValue.length()) {
                int delimiter = safeValue.lastIndexOf(" | ", end);
                if (delimiter > start) {
                    end = delimiter;
                }
            }
            chunks.add(safeValue.substring(start, end));
            start = end;
            if (safeValue.startsWith(" | ", start)) {
                start += 3;
            }
        }
        int chunkCount = chunks.size();
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            Log.i(TAG, "shade OEM " + label + "["
                    + (chunk + 1) + "/" + chunkCount + "]="
                    + chunks.get(chunk));
        }
    }

    private BlockedSurfaceScanResult detectLegacyContentBlockedSurfaces(
            boolean deepNodeScanNeeded, boolean pinEntryRequestedSnapshot) {
        BlockedSurfaceScanResult result = new BlockedSurfaceScanResult();
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (RuntimeException e) {
            Log.w(TAG, "legacy content window scan failed", e);
            return result;
        }
        if (windows == null || windows.isEmpty()) {
            return result;
        }
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window == null || !isActiveOrFocusedWindow(window)) {
                continue;
            }
            CharSequence title = windowTitle(window);
            if (containsStrongPinEntryKeyword(title)) {
                result.surfaces |= BLOCKED_SURFACE_PIN_ENTRY;
            }
            if (containsStrongNotificationShadeKeyword(title)
                    || containsNotificationShadeTextKeyword(title)) {
                result.surfaces |= BLOCKED_SURFACE_NOTIFICATION_SHADE;
            }
            if (containsStrongGlobalActionsKeyword(title)) {
                result.surfaces |= BLOCKED_SURFACE_GLOBAL_ACTIONS;
            }
            if (result.surfaces != 0) {
                return result;
            }
            if (!deepNodeScanNeeded) {
                continue;
            }

            AccessibilityNodeInfo root = null;
            try {
                root = window.getRoot();
                if (root != null && pinEntryRequestedSnapshot
                        && isKeyboardPackage(root.getPackageName())) {
                    result.surfaces |= BLOCKED_SURFACE_PIN_ENTRY;
                    continue;
                }
                if (root == null || !isSystemKeyguardNode(root)) {
                    continue;
                }
                result.surfaces |= detectLegacyBlockedSurfaceNodes(root, 0);
                if ((result.surfaces & BLOCKED_SURFACE_PIN_ENTRY) != 0
                        && (result.surfaces & BLOCKED_SURFACE_NOTIFICATION_SHADE) != 0) {
                    return result;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "legacy content node scan failed", e);
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
        }
        return result;
    }

    private int detectLegacyBlockedSurfaceNodes(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > PIN_ENTRY_NODE_SCAN_DEPTH) {
            return 0;
        }
        int blockedSurfaces = 0;
        if (nodeMatchesPinEntry(node)) {
            blockedSurfaces |= BLOCKED_SURFACE_PIN_ENTRY;
        }
        if (nodeMatchesLegacyNotificationShade(node)) {
            blockedSurfaces |= BLOCKED_SURFACE_NOTIFICATION_SHADE;
        }
        if (nodeMatchesGlobalActions(node)) {
            blockedSurfaces |= BLOCKED_SURFACE_GLOBAL_ACTIONS;
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
                blockedSurfaces |= detectLegacyBlockedSurfaceNodes(child, depth + 1);
                if (blockedSurfaces == (BLOCKED_SURFACE_PIN_ENTRY
                        | BLOCKED_SURFACE_NOTIFICATION_SHADE
                        | BLOCKED_SURFACE_GLOBAL_ACTIONS)) {
                    return blockedSurfaces;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "legacy blocked surface child scan failed", e);
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return blockedSurfaces;
    }

    private boolean nodeMatchesLegacyNotificationShade(AccessibilityNodeInfo node) {
        return containsStrongNotificationShadeKeyword(node.getViewIdResourceName())
                || containsStrongNotificationShadeKeyword(node.getClassName())
                || containsNotificationShadeTextKeyword(node.getText())
                || containsNotificationShadeTextKeyword(node.getContentDescription());
    }

    private BlockedSurfaceScanResult detectContentBlockedSurfaces(
            boolean deepNodeScanNeeded, boolean pinEntryRequestedSnapshot,
            boolean extendedShadeScan) {
        BlockedSurfaceScanResult result = new BlockedSurfaceScanResult();
        result.deepScanPerformed = deepNodeScanNeeded;
        List<AccessibilityWindowInfo> windows;
        try {
            windows = collectBlockedSurfaceScanWindows();
        } catch (RuntimeException e) {
            Log.w(TAG, "content window scan failed", e);
            return result;
        }
        if (windows == null || windows.isEmpty()) {
            return result;
        }
        BlockedSurfaceNodeScanBudget budget = deepNodeScanNeeded
                ? new BlockedSurfaceNodeScanBudget(extendedShadeScan
                        ? NOTIFICATION_SHADE_EXTENDED_SCAN_MAX_ELAPSED_MS
                        : BLOCKED_SURFACE_SCAN_MAX_ELAPSED_MS) : null;
        StringBuilder windowSignature = deepNodeScanNeeded
                ? new StringBuilder(512) : null;
        boolean scanError = false;
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window == null) {
                continue;
            }
            result.windowsVisited++;
            CharSequence title = windowTitle(window);
            if (containsStrongPinEntryKeyword(title)) {
                result.surfaces |= BLOCKED_SURFACE_PIN_ENTRY;
            }
            if (containsStrongNotificationShadeKeyword(title)
                    || containsNotificationShadeTextKeyword(title)) {
                result.surfaces |= BLOCKED_SURFACE_NOTIFICATION_SHADE;
            }
            if (containsStrongGlobalActionsKeyword(title)) {
                result.surfaces |= BLOCKED_SURFACE_GLOBAL_ACTIONS;
            }
            if (!deepNodeScanNeeded) {
                continue;
            }

            AccessibilityNodeInfo root = null;
            try {
                root = window.getRoot();
                appendNotificationShadeWindowDiagnostic(
                        windowSignature, window, title, root);
                if (root != null && pinEntryRequestedSnapshot
                        && isKeyboardPackage(root.getPackageName())) {
                    result.surfaces |= BLOCKED_SURFACE_PIN_ENTRY;
                    continue;
                }
                if (root == null || !isSystemKeyguardNode(root)) {
                    continue;
                }
                result.systemUiRootsScanned++;
                result.surfaces |= detectBlockedSurfaceNodes(root, budget);
            } catch (RuntimeException e) {
                scanError = true;
                Log.w(TAG, "content node scan failed", e);
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            if (budget != null && budget.exhausted) {
                break;
            }
        }
        if (budget != null) {
            result.nodesVisited = budget.nodesVisited;
            result.budgetExhausted = budget.exhausted;
            result.shadeMatch = budget.shadeMatch;
            result.nodeSignature = budget.diagnosticSignature();
            result.windowSignature = windowSignature == null
                    || windowSignature.length() == 0
                    ? "<none>" : windowSignature.toString();
        }
        if ((result.surfaces & BLOCKED_SURFACE_NOTIFICATION_SHADE) != 0) {
            result.quality = scanError || result.budgetExhausted
                    ? BLOCKED_SURFACE_SCAN_PARTIAL : BLOCKED_SURFACE_SCAN_SUCCESS;
        } else if (deepNodeScanNeeded && result.systemUiRootsScanned > 0) {
            result.quality = scanError || result.budgetExhausted
                    ? BLOCKED_SURFACE_SCAN_PARTIAL : BLOCKED_SURFACE_SCAN_SUCCESS;
        }
        return result;
    }

    private void appendNotificationShadeWindowDiagnostic(
            StringBuilder signature, AccessibilityWindowInfo window,
            CharSequence title, AccessibilityNodeInfo root) {
        if (signature == null || window == null
                || signature.length() >= NOTIFICATION_SHADE_OEM_DIAGNOSTIC_MAX_CHARS) {
            return;
        }
        if (signature.length() > 0) {
            signature.append(" | ");
        }
        String titleSignal;
        if (containsStrongNotificationShadeKeyword(title)
                || containsNotificationShadeTextKeyword(title)) {
            titleSignal = "shade";
        } else if (containsStrongPinEntryKeyword(title)) {
            titleSignal = "pin";
        } else {
            titleSignal = title == null ? "none" : "other";
        }
        boolean systemRoot = root != null && isSystemKeyguardNode(root);
        signature.append("id=").append(window.getId())
                .append(",display=")
                .append(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? window.getDisplayId() : 0)
                .append(",type=").append(window.getType())
                .append(",layer=").append(window.getLayer())
                .append(",active=").append(window.isActive())
                .append(",focused=").append(window.isFocused())
                .append(",titleSignal=").append(titleSignal)
                .append(",rootPackage=")
                .append(root == null ? "-"
                        : (systemRoot && root.getPackageName() != null
                                ? root.getPackageName() : "<non-system>"))
                .append(",rootClass=")
                .append(!systemRoot || root.getClassName() == null
                        ? "-" : root.getClassName());
    }

    private List<AccessibilityWindowInfo> collectBlockedSurfaceScanWindows() {
        ArrayList<AccessibilityWindowInfo> result =
                new ArrayList<AccessibilityWindowInfo>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SparseArray<List<AccessibilityWindowInfo>> windowsByDisplay =
                    getWindowsOnAllDisplays();
            if (windowsByDisplay == null) {
                return result;
            }
            for (int displayIndex = 0; displayIndex < windowsByDisplay.size(); displayIndex++) {
                List<AccessibilityWindowInfo> displayWindows =
                        windowsByDisplay.valueAt(displayIndex);
                if (displayWindows == null) {
                    continue;
                }
                int addedForDisplay = 0;
                // Modal SystemUI windows are not consistently first in Samsung's list.
                // Prefer active/focused candidates, then fill the bounded scan with the rest.
                for (int pass = 0; pass < 2
                        && addedForDisplay < BLOCKED_SURFACE_SCAN_MAX_WINDOWS_PER_DISPLAY;
                        pass++) {
                    for (int i = 0; i < displayWindows.size()
                            && addedForDisplay
                                    < BLOCKED_SURFACE_SCAN_MAX_WINDOWS_PER_DISPLAY; i++) {
                        AccessibilityWindowInfo window = displayWindows.get(i);
                        boolean priority = isActiveOrFocusedWindow(window);
                        if (!isBlockedSurfaceScanCandidateWindow(window)
                                || (pass == 0) != priority) {
                            continue;
                        }
                        result.add(window);
                        addedForDisplay++;
                    }
                }
            }
            return result;
        }
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) {
            return result;
        }
        for (int pass = 0; pass < 2
                && result.size() < BLOCKED_SURFACE_SCAN_MAX_WINDOWS_PER_DISPLAY; pass++) {
            for (int i = 0; i < windows.size()
                    && result.size() < BLOCKED_SURFACE_SCAN_MAX_WINDOWS_PER_DISPLAY; i++) {
                AccessibilityWindowInfo window = windows.get(i);
                boolean priority = isActiveOrFocusedWindow(window);
                if (isBlockedSurfaceScanCandidateWindow(window)
                        && (pass == 0) == priority) {
                    result.add(window);
                }
            }
        }
        return result;
    }

    private boolean isBlockedSurfaceScanCandidateWindow(AccessibilityWindowInfo window) {
        return window != null
                && window.getType() != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY;
    }

    private boolean isActiveOrFocusedWindow(AccessibilityWindowInfo window) {
        return window != null && (window.isActive() || window.isFocused());
    }

    private int detectBlockedSurfaceNodes(
            AccessibilityNodeInfo root, BlockedSurfaceNodeScanBudget budget) {
        if (root == null || budget == null) {
            return 0;
        }
        ArrayList<AccessibilityNodeInfo> queue = new ArrayList<AccessibilityNodeInfo>();
        ArrayList<Integer> depths = new ArrayList<Integer>();
        queue.add(root);
        depths.add(Integer.valueOf(0));
        int blockedSurfaces = 0;
        int queueIndex = 0;
        while (queueIndex < queue.size() && budget.tryVisit()) {
            AccessibilityNodeInfo node = queue.get(queueIndex);
            int depth = depths.get(queueIndex).intValue();
            queueIndex++;
            boolean recycleNode = node != root;
            try {
                budget.recordDiagnosticNode(depth, node);
                if (nodeMatchesPinEntry(node)) {
                    blockedSurfaces |= BLOCKED_SURFACE_PIN_ENTRY;
                }
                if (nodeMatchesGlobalActions(node)) {
                    blockedSurfaces |= BLOCKED_SURFACE_GLOBAL_ACTIONS;
                }
                if (isVisibleKeyguardRootNode(node)) {
                    budget.visibleKeyguardRootSeen = true;
                }
                boolean definitiveNotificationShadeNode =
                        isDefinitiveNotificationShadeStructuralNode(node);
                boolean notificationShadeNode = nodeMatchesNotificationShade(node)
                        || definitiveNotificationShadeNode;
                if (notificationShadeNode) {
                    budget.notificationShadeCandidateSeen = true;
                    String match = notificationShadeNodeMatch(node);
                    if (budget.notificationShadeCandidateMatch == null) {
                        budget.notificationShadeCandidateMatch = match;
                    }
                    if (definitiveNotificationShadeNode) {
                        budget.definitiveNotificationShadeStructureSeen = true;
                        // Prefer the active container in diagnostics over a dormant child.
                        budget.notificationShadeCandidateMatch = match;
                    }
                }
                if (depth >= PIN_ENTRY_NODE_SCAN_DEPTH) {
                    continue;
                }
                int childCount = Math.min(
                        node.getChildCount(), PIN_ENTRY_NODE_SCAN_CHILD_LIMIT);
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        queue.add(child);
                        depths.add(Integer.valueOf(depth + 1));
                    }
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "blocked surface child scan failed", e);
            } finally {
                if (recycleNode) {
                    node.recycle();
                }
            }
        }
        for (int i = queueIndex; i < queue.size(); i++) {
            AccessibilityNodeInfo queuedNode = queue.get(i);
            if (queuedNode != null && queuedNode != root) {
                queuedNode.recycle();
            }
        }
        if (budget.notificationShadeCandidateSeen
                && (!budget.visibleKeyguardRootSeen
                || budget.definitiveNotificationShadeStructureSeen)) {
            blockedSurfaces |= BLOCKED_SURFACE_NOTIFICATION_SHADE;
            budget.shadeMatch = budget.notificationShadeCandidateMatch;
        }
        return blockedSurfaces;
    }

    private boolean nodeMatchesPinEntry(AccessibilityNodeInfo node) {
        return containsStrongPinEntryKeyword(node.getViewIdResourceName())
                || containsStrongPinEntryKeyword(node.getClassName())
                || containsPinEntryTextKeyword(node.getText())
                || containsPinEntryTextKeyword(node.getContentDescription());
    }

    private boolean nodeMatchesGlobalActions(AccessibilityNodeInfo node) {
        return node != null && node.isVisibleToUser()
                && (containsStrongGlobalActionsKeyword(node.getViewIdResourceName())
                || containsStrongGlobalActionsKeyword(node.getClassName()));
    }

    private boolean nodeMatchesNotificationShade(AccessibilityNodeInfo node) {
        return node.isVisibleToUser()
                && (containsStrongNotificationShadeKeyword(node.getViewIdResourceName())
                || containsStrongNotificationShadeKeyword(node.getClassName()));
    }

    private boolean isVisibleKeyguardRootNode(AccessibilityNodeInfo node) {
        return node != null && node.isVisibleToUser()
                && containsIdentifierFragment(
                        node.getViewIdResourceName(), "keyguard_root_view");
    }

    private boolean isDefinitiveNotificationShadeStructuralNode(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) {
            return false;
        }
        CharSequence viewId = node.getViewIdResourceName();
        return containsIdentifierFragment(viewId, "sec_quick_panel_compose_root");
    }

    private String notificationShadeNodeMatch(AccessibilityNodeInfo node) {
        CharSequence viewId = node.getViewIdResourceName();
        CharSequence className = node.getClassName();
        return viewId != null
                ? viewId.toString()
                : (className == null ? "unknown" : className.toString());
    }

    private boolean containsIdentifierFragment(CharSequence value, String fragment) {
        return value != null && fragment != null
                && value.toString().toLowerCase(Locale.ROOT).contains(fragment);
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

    private boolean containsStrongGlobalActionsKeyword(CharSequence value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toString().toLowerCase(Locale.ROOT);
        return normalized.contains("globalactions")
                || normalized.contains("global_actions")
                || normalized.contains("sec_global_actions")
                || normalized.contains("actionsdialog")
                || normalized.contains("phone options");
    }

    private boolean containsStrongGlobalActionsKeyword(List<CharSequence> values) {
        if (values == null) {
            return false;
        }
        for (int i = 0; i < values.size(); i++) {
            if (containsStrongGlobalActionsKeyword(values.get(i))) {
                return true;
            }
        }
        return false;
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
        String normalized = value.toString().toLowerCase(Locale.ROOT);
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
                suppressUnlockFxAfterDoodleDisconnect = false;
                destroySeasonalUnlockPartnerOverlay();
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
        if (isChargingDoodleModeEnabled() && isSeasonalUnlockPartnerModeEnabled()) {
            ensureSeasonalUnlockPartnerLoaded();
        }
        if (isUnlockEffectEnabledForActivePanel()) {
            preloadUnlockEffectRenderer();
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
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                return "window_content";
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
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    || !pinEntryRequested) {
                return;
            }
        }
        CharSequence className = event.getClassName();
        int windowChanges = type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? event.getWindowChanges() : 0;
        Log.i(TAG, "event detail type=" + eventTypeName(event)
                + " class=" + (className == null ? "-" : className)
                + " windowId=" + event.getWindowId()
                + " contentChanges=" + event.getContentChangeTypes()
                + " windowChanges=" + windowChanges
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

    private boolean shouldProbeNotificationShade(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !SYSTEM_UI_PACKAGE.equals(event.getPackageName().toString())) {
            return false;
        }
        int type = event.getEventType();
        if (isNotificationShadeProbeSuppressedForScreenOffTransition()) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                Log.i(TAG, "generic notification shade probe ignored during screen-off"
                        + " transition type=" + eventTypeName(event)
                        + " sinceScreenOffMs=" + elapsedSinceScreenOff());
            }
            return false;
        }
        return type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
    }

    private boolean isNotificationShadeProbeSuppressedForScreenOffTransition() {
        long sinceScreenOffMs = elapsedSinceScreenOff();
        return screenOffTransitionPending
                && sinceScreenOffMs >= 0L
                && sinceScreenOffMs < NOTIFICATION_SHADE_SCREEN_OFF_GUARD_MS;
    }

    private boolean isGlobalActionsEvent(AccessibilityEvent event) {
        if (event == null || !SYSTEM_UI_PACKAGE.equals(
                event.getPackageName() == null
                        ? null : event.getPackageName().toString())) {
            return false;
        }
        return containsStrongGlobalActionsKeyword(event.getClassName())
                || containsStrongGlobalActionsKeyword(event.getText())
                || containsStrongGlobalActionsKeyword(event.getContentDescription())
                || eventSourceContainsGlobalActions(event);
    }

    private boolean eventSourceContainsGlobalActions(AccessibilityEvent event) {
        AccessibilityNodeInfo source = null;
        try {
            source = event == null ? null : event.getSource();
            return source != null
                    && nodeTreeContainsGlobalActions(source, 0, new int[]{48});
        } catch (RuntimeException e) {
            Log.w(TAG, "global actions event-source scan failed", e);
            return false;
        } finally {
            if (source != null) {
                source.recycle();
            }
        }
    }

    private boolean nodeTreeContainsGlobalActions(
            AccessibilityNodeInfo node, int depth, int[] remaining) {
        if (node == null || remaining == null || remaining[0] <= 0 || depth > 6) {
            return false;
        }
        remaining[0]--;
        if (nodeMatchesGlobalActions(node)) {
            return true;
        }
        int childCount = Math.min(node.getChildCount(), PIN_ENTRY_NODE_SCAN_CHILD_LIMIT);
        for (int i = 0; i < childCount && remaining[0] > 0; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (child != null
                        && nodeTreeContainsGlobalActions(child, depth + 1, remaining)) {
                    return true;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "global actions child scan failed", e);
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private boolean isGlobalActionsDismissEvent(AccessibilityEvent event) {
        if (event == null
                || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || !isSystemKeyguardPackage(event.getPackageName())
                || isGlobalActionsEvent(event)) {
            return false;
        }
        return globalActionsShownAt > 0L
                && SystemClock.uptimeMillis() - globalActionsShownAt
                >= GLOBAL_ACTIONS_EVENT_CLEAR_GRACE_MS;
    }

    private int dp(int value) {
        return Math.round(value * activeDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
