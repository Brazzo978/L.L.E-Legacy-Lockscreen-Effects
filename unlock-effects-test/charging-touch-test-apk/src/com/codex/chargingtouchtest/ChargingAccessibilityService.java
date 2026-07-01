package com.codex.chargingtouchtest;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

public class ChargingAccessibilityService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "ChargingA11y";
    private static final int UNLOCK_TRIGGER_DISTANCE_DP = 8;
    private static final long PIN_ENTRY_DELAY_MS = 700L;
    private static final long PIN_ENTRY_EFFECT_CLEANUP_DELAY_MS = 900L;
    private static final long PIN_ENTRY_SWIPE_DURATION_MS = 260L;
    private static final long DEBUG_LOOP_STEP_DELAY_MS = 120L;
    private static final long DEBUG_LOOP_RESTART_DELAY_MS = 620L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pinEntryRunnable = new Runnable() {
        @Override
        public void run() {
            openPinEntry();
        }
    };
    private final Runnable pinEntryEffectCleanupRunnable = new Runnable() {
        @Override
        public void run() {
            removeLensFlareOverlay();
        }
    };
    private final Runnable debugLensLoopRunnable = new Runnable() {
        @Override
        public void run() {
            runDebugLensLoopFrame();
        }
    };
    private WindowManager windowManager;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    private SharedPreferences prefs;
    private SeasonalDoodleView overlayView;
    private TouchDebugView touchDebugView;
    private WindowManager.LayoutParams touchDebugParams;
    private LensFlareEffectView lensFlareView;
    private float lensFlareAnchorX;
    private float lensFlareAnchorY;
    private boolean debugLensLoopScheduled;
    private boolean debugLensLoopGestureActive;
    private int debugLensLoopFrame;
    private final Set<String> homePackages = new HashSet<String>();
    private String lastWindowPackage;
    private boolean charging;
    private int batteryPercent;
    private boolean pinEntryRequested;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? "null" : intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                updateChargingState(intent);
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)
                    || Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                refreshChargingState();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)) {
                pinEntryRequested = false;
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
        prefs.registerOnSharedPreferenceChangeListener(this);
        loadHomePackages();
        configurePassiveService();
        refreshChargingState();
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            lastWindowPackage = event.getPackageName().toString();
        }
        if (isPinEntryEvent(event)) {
            boolean wasPinEntryRequested = pinEntryRequested;
            pinEntryRequested = true;
            removeTouchDebugOverlay();
            if (!wasPinEntryRequested) {
                scheduleLensFlareCleanup();
            }
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
        if ((OverlayPrefs.SEASON_MODE.equals(key)
                || OverlayPrefs.POSITION_OFFSET_X.equals(key)
                || OverlayPrefs.POSITION_OFFSET_Y.equals(key)
                || OverlayPrefs.DEBUG_ROLLING_CHARGE.equals(key)
                || OverlayPrefs.SHOW_DOODLE.equals(key))
                && overlayView != null) {
            applyOverlayPrefs();
        }
        if (OverlayPrefs.DEBUG_TOUCH_AREA.equals(key) && overlayView != null) {
            syncTouchDebugOverlay();
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
                && overlayView != null) {
            syncTouchDebugOverlay();
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

    private void configurePassiveService() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = 0;
        setServiceInfo(info);
    }

    private void evaluateVisibility(String reason) {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        boolean home = interactive && !locked && isHomePackage(lastWindowPackage);
        if (!interactive || !locked) {
            pinEntryRequested = false;
        }

        boolean showDoodleForSurface = (!interactive && OverlayPrefs.showAod(this))
                || (interactive && locked && !pinEntryRequested && OverlayPrefs.showLock(this))
                || (home && OverlayPrefs.showHome(this));
        boolean showDoodle = charging && showDoodleForSurface;
        boolean showFx = interactive && locked && !pinEntryRequested && OverlayPrefs.showLock(this);

        if (showDoodle) {
            syncDoodleOverlay();
        } else {
            removeDoodleOverlay();
        }

        if (showFx) {
            syncLensFlareOverlay();
            syncTouchDebugOverlay();
            syncDebugLensLoop();
        } else {
            stopDebugLensLoop();
            if (!pinEntryRequested) {
                removeLensFlareOverlay();
            }
            removeTouchDebugOverlay();
        }

        Log.i(TAG, "visibility reason=" + reason
                + " showDoodle=" + showDoodle
                + " showFx=" + showFx
                + " charging=" + charging
                + " interactive=" + interactive
                + " locked=" + locked
                + " pinEntryRequested=" + pinEntryRequested
                + " home=" + home
                + " pkg=" + lastWindowPackage);
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

    private void syncLensFlareOverlay() {
        if (lensFlareView != null) {
            return;
        }
        lensFlareView = new LensFlareEffectView(this);
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
        params.setTitle("ChargingLensFlareEffect");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        windowManager.addView(lensFlareView, params);
        Log.i(TAG, "lens flare overlay shown");
    }

    private void syncTouchDebugOverlay() {
        if (!OverlayPrefs.debugTouchArea(this)) {
            removeTouchDebugOverlay();
            return;
        }
        Rect box = resolveTouchBox();
        if (touchDebugView != null) {
            touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
            updateTouchDebugLayout(box);
            return;
        }
        touchDebugView = new TouchDebugView(this);
        touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
        touchDebugView.setTouchTriggerListener(new TouchDebugView.TouchTriggerListener() {
            @Override
            public void onTouchStarted(float screenX, float screenY) {
                beginLensFlareGesture(screenX, screenY);
            }

            @Override
            public void onTouchMoved(float screenX, float screenY,
                    float deltaX, float deltaY, float distance) {
                updateLensFlareGesture(screenX, screenY);
            }

            @Override
            public void onTouchEnded(float screenX, float screenY,
                    float deltaX, float deltaY, float distance) {
                finishLensFlareGesture(screenX, screenY, distance);
            }

            @Override
            public void onTouchCancelled() {
                cancelLensFlareGesture();
            }
        });

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        touchDebugParams = new WindowManager.LayoutParams(
                box.width(),
                box.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        touchDebugParams.gravity = Gravity.TOP | Gravity.START;
        touchDebugParams.x = box.left;
        touchDebugParams.y = box.top;
        touchDebugParams.setTitle("ChargingTouchListenBox");
        windowManager.addView(touchDebugView, touchDebugParams);
        Log.i(TAG, "touch listen box shown left=" + box.left
                + " top=" + box.top
                + " right=" + box.right
                + " bottom=" + box.bottom);
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
        removeLensFlareOverlay();
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

    private void removeLensFlareOverlay() {
        stopDebugLensLoop();
        if (lensFlareView != null) {
            try {
                windowManager.removeView(lensFlareView);
            } catch (RuntimeException ignored) {
                // The service can be torn down after the effect window was already removed.
            }
            lensFlareView = null;
        }
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
        }
    }

    private void updateTouchDebugLayout(Rect box) {
        if (touchDebugView == null || touchDebugParams == null) {
            return;
        }
        boolean changed = touchDebugParams.x != box.left
                || touchDebugParams.y != box.top
                || touchDebugParams.width != box.width()
                || touchDebugParams.height != box.height();
        if (!changed) {
            return;
        }
        touchDebugParams.x = box.left;
        touchDebugParams.y = box.top;
        touchDebugParams.width = box.width();
        touchDebugParams.height = box.height();
        windowManager.updateViewLayout(touchDebugView, touchDebugParams);
        Log.i(TAG, "touch listen box updated left=" + box.left
                + " top=" + box.top
                + " right=" + box.right
                + " bottom=" + box.bottom);
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
        if (!OverlayPrefs.debugLensLoop(this) || lensFlareView == null || !isFxSurfaceActive()) {
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
        if (!OverlayPrefs.debugLensLoop(this) || lensFlareView == null || !isFxSurfaceActive()) {
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
                lensFlareView.beginGesture(centerX, centerY);
                debugLensLoopGestureActive = true;
                Log.i(TAG, "lens flare debug loop begin box="
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
                lensFlareView.finishGesture(false);
                debugLensLoopGestureActive = false;
                debugLensLoopFrame = 0;
                Log.i(TAG, "lens flare debug loop end");
                scheduleDebugLensLoop(DEBUG_LOOP_RESTART_DELAY_MS);
                return;
        }

        lensFlareView.updateGesture(x, y);
        Log.i(TAG, "lens flare debug loop frame=" + debugLensLoopFrame
                + " point=" + Math.round(x) + "," + Math.round(y));
        debugLensLoopFrame++;
        scheduleDebugLensLoop(DEBUG_LOOP_STEP_DELAY_MS);
    }

    private void stopDebugLensLoop() {
        handler.removeCallbacks(debugLensLoopRunnable);
        debugLensLoopScheduled = false;
        debugLensLoopFrame = 0;
        if (debugLensLoopGestureActive && lensFlareView != null) {
            lensFlareView.cancelGesture();
        }
        debugLensLoopGestureActive = false;
    }

    private boolean isFxSurfaceActive() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        return interactive && locked && !pinEntryRequested && OverlayPrefs.showLock(this);
    }

    private void beginLensFlareGesture(float screenX, float screenY) {
        handler.removeCallbacks(pinEntryRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        stopDebugLensLoop();
        lensFlareAnchorX = screenX;
        lensFlareAnchorY = screenY;
        syncLensFlareOverlay();
        if (lensFlareView != null) {
            lensFlareView.beginGesture(lensFlareAnchorX, lensFlareAnchorY);
        }
        Log.i(TAG, "lens flare gesture begin touch="
                + Math.round(screenX) + "," + Math.round(screenY)
                + " anchor=" + Math.round(lensFlareAnchorX)
                + "," + Math.round(lensFlareAnchorY));
    }

    private void updateLensFlareGesture(float screenX, float screenY) {
        if (lensFlareView != null) {
            lensFlareView.updateGesture(screenX, screenY);
        }
    }

    private void finishLensFlareGesture(float screenX, float screenY, float distance) {
        boolean unlockTriggered = distance >= dp(UNLOCK_TRIGGER_DISTANCE_DP);
        if (lensFlareView != null) {
            lensFlareView.updateGesture(screenX, screenY);
            lensFlareView.finishGesture(unlockTriggered);
        }
        if (unlockTriggered) {
            schedulePinEntry();
        }
        Log.i(TAG, "lens flare gesture end effect="
                + Math.round(screenX) + "," + Math.round(screenY)
                + " distance=" + Math.round(distance)
                + " threshold=" + dp(UNLOCK_TRIGGER_DISTANCE_DP)
                + " unlockTriggered=" + unlockTriggered);
    }

    private void cancelLensFlareGesture() {
        handler.removeCallbacks(pinEntryRunnable);
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        if (lensFlareView != null) {
            lensFlareView.cancelGesture();
        }
        Log.i(TAG, "lens flare gesture cancelled");
    }

    private void schedulePinEntry() {
        handler.removeCallbacks(pinEntryRunnable);
        handler.postDelayed(pinEntryRunnable, PIN_ENTRY_DELAY_MS);
        Log.i(TAG, "pin entry scheduled delayMs=" + PIN_ENTRY_DELAY_MS);
    }

    private void openPinEntry() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        if (!interactive) {
            Log.i(TAG, "pin entry skipped interactive=false locked=" + locked);
            pinEntryRequested = false;
            return;
        }
        if (!locked) {
            Log.w(TAG, "pin entry swipe continuing while keyguard reports locked=false");
        }

        pinEntryRequested = true;
        removeTouchDebugOverlay();
        scheduleLensFlareCleanup();
        boolean accepted = performPinEntrySwipe();
        if (!accepted) {
            pinEntryRequested = false;
        }
        evaluateVisibility("pin_entry_requested");
    }

    private void scheduleLensFlareCleanup() {
        handler.removeCallbacks(pinEntryEffectCleanupRunnable);
        handler.postDelayed(pinEntryEffectCleanupRunnable, PIN_ENTRY_EFFECT_CLEANUP_DELAY_MS);
        Log.i(TAG, "lens flare cleanup scheduled delayMs="
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

    private boolean isPinEntryEvent(AccessibilityEvent event) {
        if (event == null || event.getClassName() == null) {
            return false;
        }
        String className = event.getClassName().toString().toLowerCase();
        return className.contains("pin")
                || className.contains("password")
                || className.contains("bouncer")
                || className.contains("numpad")
                || className.contains("keyguardsecurity");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

