package com.codex.lle;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

final class OverlayPrefs {
    static final String PREFS = "overlay_prefs";
    static final String MASTER_ENABLED = "master_enabled";
    static final String SHOW_LOCK = "show_lock";
    static final String SHOW_AOD = "show_aod";
    static final String SHOW_HOME = "show_home";
    static final String SHOW_DOODLE = "show_doodle";
    static final String SEASONAL_UNLOCK_PARTNER = "seasonal_unlock_partner";
    static final String SEASON_MODE = "season_mode";
    static final String DOODLE_SIZE_PERCENT = "doodle_size_percent";
    static final String POSITION_OFFSET_X = "position_offset_x";
    static final String POSITION_OFFSET_Y = "position_offset_y";
    static final String DEBUG_ROLLING_CHARGE = "debug_rolling_charge";
    static final String DEBUG_TOUCH_AREA = "debug_touch_area";
    static final String DEBUG_TOUCH_TRANSPARENT = "debug_touch_transparent";
    static final String DEBUG_TOUCH_STANDBY = "debug_touch_standby";
    static final String DEBUG_LENS_LOOP = "debug_lens_loop";
    static final String ROOT_DEBUG_ENABLED = "root_debug_enabled";
    static final String ROOT_TOUCH_CAPTURE_TEST_ENABLED = "root_touch_capture_test_enabled";
    static final String ROOT_KEEPALIVE_PLAN_ENABLED = "root_keepalive_plan_enabled";
    static final String UNLOCK_EFFECT_ENABLED = "unlock_effect_enabled";
    static final String LOCK_SOUND_ENABLED = "lock_sound_enabled";
    static final String UNLOCK_EFFECT = "unlock_effect";
    static final String EFFECT_PROFILE_LAST_SUMMARY = "effect_profile_last_summary";
    static final String EFFECT_PROFILE_DIAGNOSTIC_SUMMARY =
            "effect_profile_diagnostic_summary";
    static final String EFFECT_PROFILE_LAST_CSV = "effect_profile_last_csv";
    static final String EFFECT_PROFILE_RUNNING = "effect_profile_running";
    static final String EFFECT_PROFILE_SAMPLE_TOKEN = "effect_profile_sample_token";
    private static final String EFFECT_PROFILE_SAMPLED_TOKEN_PREFIX =
            "effect_profile_sampled_token_";
    static final String EFFECT_BACKGROUND_REFRESH_TOKEN = "effect_background_refresh_token";
    static final String POPPING_COLOR_REFRESH_TOKEN = "popping_color_refresh_token";
    static final String EFFECT_BACKGROUND_AUTO_REFRESH_ENABLED =
            "effect_background_auto_refresh_enabled";
    static final String EFFECT_BACKGROUND_REFRESH_INTERVAL_HOURS =
            "effect_background_refresh_interval_hours";
    static final String EFFECT_BACKGROUND_SKIP_NIGHT =
            "effect_background_skip_night";
    static final String EFFECT_BACKGROUND_FORCE_RECAPTURE =
            "effect_background_force_recapture";
    static final String EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE =
            "effect_background_wake_capture_active";
    static final String EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK =
            "effect_background_wake_capture_should_relock";
    static final String EFFECT_BACKGROUND_LAST_CAPTURE_PREFIX =
            "effect_background_last_capture_";
    static final String EFFECT_BACKGROUND_HANDLED_REFRESH_TOKEN_PREFIX =
            "effect_background_handled_refresh_token_";
    static final String PERF_DEFAULTS_APPLIED = "perf_defaults_20260701";
    static final String TOUCH_BOX_CONFIGURED = "touch_box_configured";
    static final String TOUCH_BOX_LEFT = "touch_box_left";
    static final String TOUCH_BOX_TOP = "touch_box_top";
    static final String TOUCH_BOX_RIGHT = "touch_box_right";
    static final String TOUCH_BOX_BOTTOM = "touch_box_bottom";
    static final String TOUCH_BOX_CAPTURE_REQUEST_ID = "touch_box_capture_request_id";
    static final String TOUCH_BOX_CAPTURE_RESULT_ID = "touch_box_capture_result_id";
    static final String TOUCH_BOX_CAPTURE_STATE = "touch_box_capture_state";
    static final String TOUCH_BOX_CAPTURE_ERROR = "touch_box_capture_error";
    static final int TOUCH_BOX_CAPTURE_IDLE = 0;
    static final int TOUCH_BOX_CAPTURE_REQUESTED = 1;
    static final int TOUCH_BOX_CAPTURE_WAITING_LOCKSCREEN = 2;
    static final int TOUCH_BOX_CAPTURE_CAPTURING = 3;
    static final int TOUCH_BOX_CAPTURE_READY = 4;
    static final int TOUCH_BOX_CAPTURE_FAILED = 5;
    static final int EFFECT_S4_LENS_FLARE = 0;
    static final int EFFECT_S3_RIPPLE = 1;
    static final int EFFECT_S5_POPPING_COLOURS = 2;
    static final int EFFECT_WATERCOLOUR = 3;
    static final int EFFECT_N5_COLOUR_DROPLET = 4;
    static final int EFFECT_N5_SPARKLING_BUBBLES = 5;
    static final int EFFECT_S4_RIPPLE = 6;
    static final int EFFECT_S4_ABSTRACT_TILES = 7;
    static final int EFFECT_S4_GEOMETRIC_MOSAIC = 8;
    static final int EFFECT_N5_COLOUR_DROPLET_GYRO = 9;
    static final int EFFECT_COUNT = 10;
    static final int DEFAULT_TOUCH_BOX_LEFT = 0;
    static final int DEFAULT_TOUCH_BOX_TOP = 730;
    static final int DEFAULT_TOUCH_BOX_RIGHT = 1080;
    static final int DEFAULT_TOUCH_BOX_BOTTOM = 2100;
    static final int LEGACY_TOUCH_BOX_LEFT = 60;
    static final int LEGACY_TOUCH_BOX_TOP = 710;
    static final int LEGACY_TOUCH_BOX_RIGHT = 1030;
    static final int LEGACY_TOUCH_BOX_BOTTOM = 1900;
    static final int TOUCH_BOX_ROUNDING_PX = 10;
    static final int POSITION_OFFSET_MIN = -100;
    static final int POSITION_OFFSET_MAX = 100;
    static final int DOODLE_SIZE_MIN_PERCENT = 60;
    static final int DOODLE_SIZE_MAX_PERCENT = 125;
    static final int DOODLE_SIZE_DEFAULT_PERCENT = 75;
    static final int DEFAULT_EFFECT_BACKGROUND_REFRESH_HOURS = 24;
    static final int MIN_EFFECT_BACKGROUND_REFRESH_HOURS = 1;
    static final int MAX_EFFECT_BACKGROUND_REFRESH_HOURS = 168;

    private OverlayPrefs() {
    }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean showLock(Context context) {
        return get(context).getBoolean(SHOW_LOCK, true);
    }

    static boolean masterEnabled(Context context) {
        return get(context).getBoolean(MASTER_ENABLED, true);
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

    static boolean seasonalUnlockPartner(Context context) {
        return get(context).getBoolean(SEASONAL_UNLOCK_PARTNER, true);
    }

    static int seasonMode(Context context) {
        return get(context).getInt(SEASON_MODE, SeasonalDoodleView.SEASON_AUTO);
    }

    static int doodleSizePercent(Context context) {
        return clampDoodleSizePercent(get(context).getInt(DOODLE_SIZE_PERCENT,
                DOODLE_SIZE_DEFAULT_PERCENT));
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

    static boolean debugTouchStandby(Context context) {
        return get(context).getBoolean(DEBUG_TOUCH_STANDBY, true);
    }

    static boolean debugLensLoop(Context context) {
        return false;
    }

    static boolean unlockEffectEnabled(Context context) {
        return get(context).getBoolean(UNLOCK_EFFECT_ENABLED, true);
    }

    static boolean lockSoundEnabled(Context context) {
        return get(context).getBoolean(LOCK_SOUND_ENABLED, true);
    }

    static int unlockEffect(Context context) {
        int effect = get(context).getInt(UNLOCK_EFFECT, EFFECT_S4_LENS_FLARE);
        if (effect != EFFECT_S4_LENS_FLARE
                && effect != EFFECT_S3_RIPPLE
                && effect != EFFECT_S5_POPPING_COLOURS
                && effect != EFFECT_WATERCOLOUR
                && effect != EFFECT_N5_COLOUR_DROPLET
                && effect != EFFECT_N5_SPARKLING_BUBBLES
                && effect != EFFECT_S4_RIPPLE
                && effect != EFFECT_S4_ABSTRACT_TILES
                && effect != EFFECT_S4_GEOMETRIC_MOSAIC
                && effect != EFFECT_N5_COLOUR_DROPLET_GYRO) {
            return EFFECT_S4_LENS_FLARE;
        }
        return effect;
    }

    static String effectLabel(int effect) {
        switch (effect) {
            case EFFECT_S4_LENS_FLARE:
                return "S4 Lens Flare";
            case EFFECT_S3_RIPPLE:
                return "S3 Ripple WIP";
            case EFFECT_S5_POPPING_COLOURS:
                return "S5 Popping Colours";
            case EFFECT_WATERCOLOUR:
                return "N4 Watercolor WIP";
            case EFFECT_N5_COLOUR_DROPLET:
                return "N5 Colored Droplet";
            case EFFECT_N5_COLOUR_DROPLET_GYRO:
                return "N5 Colored Droplet + Gyro";
            case EFFECT_N5_SPARKLING_BUBBLES:
                return "N5 Sparkling Bubbles";
            case EFFECT_S4_RIPPLE:
                return "S4 Ripple native WIP";
            case EFFECT_S4_ABSTRACT_TILES:
                return "N4 Abstract Tiles";
            case EFFECT_S4_GEOMETRIC_MOSAIC:
                return "N4 Geometric Mosaic";
            default:
                return "Unknown effect " + effect;
        }
    }

    static boolean isColourDropletEffect(int effect) {
        return effect == EFFECT_N5_COLOUR_DROPLET
                || effect == EFFECT_N5_COLOUR_DROPLET_GYRO;
    }

    static boolean effectBackgroundAutoRefreshEnabled(Context context) {
        return get(context).getBoolean(EFFECT_BACKGROUND_AUTO_REFRESH_ENABLED, false);
    }

    static int effectBackgroundRefreshIntervalHours(Context context) {
        int hours = get(context).getInt(EFFECT_BACKGROUND_REFRESH_INTERVAL_HOURS,
                DEFAULT_EFFECT_BACKGROUND_REFRESH_HOURS);
        return Math.max(MIN_EFFECT_BACKGROUND_REFRESH_HOURS,
                Math.min(MAX_EFFECT_BACKGROUND_REFRESH_HOURS, hours));
    }

    static boolean effectBackgroundSkipNight(Context context) {
        return get(context).getBoolean(EFFECT_BACKGROUND_SKIP_NIGHT, true);
    }

    static boolean effectBackgroundForceRecapture(Context context) {
        return get(context).getBoolean(EFFECT_BACKGROUND_FORCE_RECAPTURE, false);
    }

    static boolean effectBackgroundWakeCaptureActive(Context context) {
        return get(context).getBoolean(EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE, false);
    }

    static boolean effectBackgroundWakeCaptureShouldRelock(Context context) {
        return get(context).getBoolean(EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK, false);
    }

    static long effectBackgroundLastCapturedAt(Context context, int effect) {
        return get(context).getLong(EFFECT_BACKGROUND_LAST_CAPTURE_PREFIX + effect, 0L);
    }

    static void saveEffectBackgroundLastCapturedAt(Context context, int effect, long timestamp) {
        get(context).edit()
                .putLong(EFFECT_BACKGROUND_LAST_CAPTURE_PREFIX + effect, Math.max(0L, timestamp))
                .apply();
    }

    static int effectBackgroundRefreshToken(Context context) {
        SharedPreferences prefs = get(context);
        int token = prefs.getInt(EFFECT_BACKGROUND_REFRESH_TOKEN, 0);
        int legacyToken = prefs.getInt(POPPING_COLOR_REFRESH_TOKEN, 0);
        return Math.max(token, legacyToken);
    }

    static void requestEffectBackgroundRefresh(Context context) {
        get(context).edit()
                .putInt(EFFECT_BACKGROUND_REFRESH_TOKEN,
                        effectBackgroundRefreshToken(context) + 1)
                .putBoolean(EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE, true)
                .putBoolean(EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK, false)
                .apply();
    }

    static int effectBackgroundHandledRefreshToken(Context context, int effect) {
        return get(context).getInt(EFFECT_BACKGROUND_HANDLED_REFRESH_TOKEN_PREFIX + effect, 0);
    }

    static void saveEffectBackgroundHandledRefreshToken(Context context, int effect, int token) {
        get(context).edit()
                .putInt(EFFECT_BACKGROUND_HANDLED_REFRESH_TOKEN_PREFIX + effect, Math.max(0, token))
                .apply();
    }

    static File effectBenchmarkFile(Context context) {
        return new File(context.getFilesDir(), "effect_profile_benchmark.csv");
    }

    static int effectProfileSampleToken(Context context) {
        return get(context).getInt(EFFECT_PROFILE_SAMPLE_TOKEN, 0);
    }

    static String effectProfileSampledTokenKey(int effect) {
        return EFFECT_PROFILE_SAMPLED_TOKEN_PREFIX + effect;
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

    static void saveTouchBoxOutward(Context context, int left, int top, int right, int bottom) {
        get(context).edit()
                .putBoolean(TOUCH_BOX_CONFIGURED, true)
                .putInt(TOUCH_BOX_LEFT, roundTouchCoordinateDown(left))
                .putInt(TOUCH_BOX_TOP, roundTouchCoordinateDown(top))
                .putInt(TOUCH_BOX_RIGHT, roundTouchCoordinateUp(right))
                .putInt(TOUCH_BOX_BOTTOM, roundTouchCoordinateUp(bottom))
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

    static void migrateLegacyTouchBoxIfNeeded(Context context) {
        if (!touchBoxConfigured(context)) {
            return;
        }
        int left = touchBoxLeft(context);
        int top = touchBoxTop(context);
        int right = touchBoxRight(context);
        int bottom = touchBoxBottom(context);
        if (left == LEGACY_TOUCH_BOX_LEFT
                && top == LEGACY_TOUCH_BOX_TOP
                && right == LEGACY_TOUCH_BOX_RIGHT
                && bottom == LEGACY_TOUCH_BOX_BOTTOM) {
            saveTouchBox(context,
                    DEFAULT_TOUCH_BOX_LEFT,
                    DEFAULT_TOUCH_BOX_TOP,
                    DEFAULT_TOUCH_BOX_RIGHT,
                    DEFAULT_TOUCH_BOX_BOTTOM);
        }
    }

    static int clampPositionOffset(int value) {
        return Math.max(POSITION_OFFSET_MIN, Math.min(POSITION_OFFSET_MAX, value));
    }

    static int clampDoodleSizePercent(int value) {
        return Math.max(DOODLE_SIZE_MIN_PERCENT, Math.min(DOODLE_SIZE_MAX_PERCENT, value));
    }

    static int roundTouchCoordinate(int value) {
        return Math.round(value / (float) TOUCH_BOX_ROUNDING_PX) * TOUCH_BOX_ROUNDING_PX;
    }

    static int roundTouchCoordinateDown(int value) {
        if (value >= 0) {
            return (value / TOUCH_BOX_ROUNDING_PX) * TOUCH_BOX_ROUNDING_PX;
        }
        return -roundTouchCoordinateUp(-value);
    }

    static int roundTouchCoordinateUp(int value) {
        if (value >= 0) {
            return ((value + TOUCH_BOX_ROUNDING_PX - 1)
                    / TOUCH_BOX_ROUNDING_PX) * TOUCH_BOX_ROUNDING_PX;
        }
        return -roundTouchCoordinateDown(-value);
    }

    static File touchBoxScreenshotFile(Context context) {
        return new File(context.getFilesDir(), "touch_box_lockscreen.png");
    }

    static File effectBackgroundFile(Context context, int effect) {
        return new File(context.getFilesDir(), "unlock_effect_background.png");
    }

    static File legacyEffectBackgroundFile(Context context, int effect) {
        return new File(context.getFilesDir(), "unlock_effect_background_" + effect + ".png");
    }
}

