package com.codex.chargingtouchtest;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
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

    private WindowManager windowManager;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    private SharedPreferences prefs;
    private SeasonalDoodleView overlayView;
    private TouchDebugView touchDebugView;
    private final Set<String> homePackages = new HashSet<String>();
    private String lastWindowPackage;
    private boolean charging;
    private int batteryPercent;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? "null" : intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                updateChargingState(intent);
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)
                    || Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                refreshChargingState();
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
        prefs.registerOnSharedPreferenceChangeListener(this);
        loadHomePackages();
        configurePassiveService();
        refreshChargingState();
        registerScreenReceiver();
        evaluateVisibility("connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            lastWindowPackage = event.getPackageName().toString();
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
                || OverlayPrefs.DEBUG_ROLLING_CHARGE.equals(key))
                && overlayView != null) {
            applyOverlayPrefs();
        }
        if (OverlayPrefs.DEBUG_TOUCH_AREA.equals(key) && overlayView != null) {
            syncTouchDebugOverlay();
        }
        if (OverlayPrefs.DEBUG_TOUCH_TRANSPARENT.equals(key) && touchDebugView != null) {
            touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
        }
        evaluateVisibility("prefs:" + key);
    }

    private void cleanup() {
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

        boolean showForSurface = (!interactive && OverlayPrefs.showAod(this))
                || (interactive && locked && OverlayPrefs.showLock(this))
                || (home && OverlayPrefs.showHome(this));
        boolean show = charging && showForSurface;

        if (show) {
            showOverlay();
        } else {
            removeOverlay();
        }

        Log.i(TAG, "visibility reason=" + reason
                + " show=" + show
                + " charging=" + charging
                + " interactive=" + interactive
                + " locked=" + locked
                + " home=" + home
                + " pkg=" + lastWindowPackage);
    }

    private void showOverlay() {
        if (overlayView != null) {
            applyOverlayPrefs();
            syncTouchDebugOverlay();
            return;
        }
        overlayView = new SeasonalDoodleView(this);
        applyOverlayPrefs();

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
        windowManager.addView(overlayView, params);
        syncTouchDebugOverlay();
        Log.i(TAG, "accessibility overlay shown");
    }

    private void syncTouchDebugOverlay() {
        if (!OverlayPrefs.debugTouchArea(this)) {
            removeTouchDebugOverlay();
            return;
        }
        if (touchDebugView != null) {
            touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));
            return;
        }
        touchDebugView = new TouchDebugView(this);
        touchDebugView.setTransparentMode(OverlayPrefs.debugTouchTransparent(this));

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(230),
                dp(150),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(72);
        params.setTitle("ChargingTouchDebugArea");
        windowManager.addView(touchDebugView, params);
        Log.i(TAG, "touch debug overlay shown");
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
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
                // The service can be torn down after the window was already removed.
            }
            overlayView = null;
        }
        removeTouchDebugOverlay();
    }

    private void removeTouchDebugOverlay() {
        if (touchDebugView != null) {
            try {
                windowManager.removeView(touchDebugView);
            } catch (RuntimeException ignored) {
                // The service can be torn down after the debug window was already removed.
            }
            touchDebugView = null;
        }
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

