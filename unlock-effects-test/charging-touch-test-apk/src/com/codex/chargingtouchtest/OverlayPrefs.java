package com.codex.chargingtouchtest;

import android.content.Context;
import android.content.SharedPreferences;

final class OverlayPrefs {
    static final String PREFS = "overlay_prefs";
    static final String SHOW_LOCK = "show_lock";
    static final String SHOW_AOD = "show_aod";
    static final String SHOW_HOME = "show_home";
    static final String SHOW_DOODLE = "show_doodle";
    static final String SEASON_MODE = "season_mode";
    static final String POSITION_OFFSET_X = "position_offset_x";
    static final String POSITION_OFFSET_Y = "position_offset_y";
    static final String DEBUG_ROLLING_CHARGE = "debug_rolling_charge";
    static final String DEBUG_TOUCH_AREA = "debug_touch_area";
    static final String DEBUG_TOUCH_TRANSPARENT = "debug_touch_transparent";
    static final String DEBUG_LENS_LOOP = "debug_lens_loop";
    static final String PERF_DEFAULTS_APPLIED = "perf_defaults_20260701";
    static final String TOUCH_BOX_CONFIGURED = "touch_box_configured";
    static final String TOUCH_BOX_LEFT = "touch_box_left";
    static final String TOUCH_BOX_TOP = "touch_box_top";
    static final String TOUCH_BOX_RIGHT = "touch_box_right";
    static final String TOUCH_BOX_BOTTOM = "touch_box_bottom";
    static final int DEFAULT_TOUCH_BOX_LEFT = 60;
    static final int DEFAULT_TOUCH_BOX_TOP = 710;
    static final int DEFAULT_TOUCH_BOX_RIGHT = 1030;
    static final int DEFAULT_TOUCH_BOX_BOTTOM = 1900;
    static final int TOUCH_BOX_ROUNDING_PX = 10;
    static final int POSITION_OFFSET_MIN = -100;
    static final int POSITION_OFFSET_MAX = 100;

    private OverlayPrefs() {
    }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean showLock(Context context) {
        return get(context).getBoolean(SHOW_LOCK, true);
    }

    static boolean showAod(Context context) {
        return get(context).getBoolean(SHOW_AOD, false);
    }

    static boolean showHome(Context context) {
        return get(context).getBoolean(SHOW_HOME, false);
    }

    static boolean showDoodle(Context context) {
        return get(context).getBoolean(SHOW_DOODLE, true);
    }

    static int seasonMode(Context context) {
        return get(context).getInt(SEASON_MODE, SeasonalDoodleView.SEASON_AUTO);
    }

    static int positionOffsetX(Context context) {
        return clampPositionOffset(get(context).getInt(POSITION_OFFSET_X, 0));
    }

    static int positionOffsetY(Context context) {
        return clampPositionOffset(get(context).getInt(POSITION_OFFSET_Y, 0));
    }

    static boolean debugRollingCharge(Context context) {
        return get(context).getBoolean(DEBUG_ROLLING_CHARGE, false);
    }

    static boolean debugTouchArea(Context context) {
        return get(context).getBoolean(DEBUG_TOUCH_AREA, true);
    }

    static boolean debugTouchTransparent(Context context) {
        return get(context).getBoolean(DEBUG_TOUCH_TRANSPARENT, true);
    }

    static boolean debugLensLoop(Context context) {
        return get(context).getBoolean(DEBUG_LENS_LOOP, false);
    }

    static boolean touchBoxConfigured(Context context) {
        return get(context).getBoolean(TOUCH_BOX_CONFIGURED, false);
    }

    static int touchBoxLeft(Context context) {
        return roundTouchCoordinate(get(context).getInt(
                TOUCH_BOX_LEFT,
                DEFAULT_TOUCH_BOX_LEFT));
    }

    static int touchBoxTop(Context context) {
        return roundTouchCoordinate(get(context).getInt(
                TOUCH_BOX_TOP,
                DEFAULT_TOUCH_BOX_TOP));
    }

    static int touchBoxRight(Context context) {
        return roundTouchCoordinate(get(context).getInt(
                TOUCH_BOX_RIGHT,
                DEFAULT_TOUCH_BOX_RIGHT));
    }

    static int touchBoxBottom(Context context) {
        return roundTouchCoordinate(get(context).getInt(
                TOUCH_BOX_BOTTOM,
                DEFAULT_TOUCH_BOX_BOTTOM));
    }

    static void saveTouchBox(Context context, int left, int top, int right, int bottom) {
        get(context).edit()
                .putBoolean(TOUCH_BOX_CONFIGURED, true)
                .putInt(TOUCH_BOX_LEFT, roundTouchCoordinate(left))
                .putInt(TOUCH_BOX_TOP, roundTouchCoordinate(top))
                .putInt(TOUCH_BOX_RIGHT, roundTouchCoordinate(right))
                .putInt(TOUCH_BOX_BOTTOM, roundTouchCoordinate(bottom))
                .apply();
    }

    static void clearTouchBox(Context context) {
        get(context).edit()
                .putBoolean(TOUCH_BOX_CONFIGURED, false)
                .remove(TOUCH_BOX_LEFT)
                .remove(TOUCH_BOX_TOP)
                .remove(TOUCH_BOX_RIGHT)
                .remove(TOUCH_BOX_BOTTOM)
                .apply();
    }

    static int clampPositionOffset(int value) {
        return Math.max(POSITION_OFFSET_MIN, Math.min(POSITION_OFFSET_MAX, value));
    }

    static int roundTouchCoordinate(int value) {
        return Math.round(value / (float) TOUCH_BOX_ROUNDING_PX) * TOUCH_BOX_ROUNDING_PX;
    }
}

