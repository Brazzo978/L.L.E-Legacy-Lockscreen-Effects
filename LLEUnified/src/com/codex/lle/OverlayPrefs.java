package com.codex.lle;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class OverlayPrefs {
    private static final String TAG = "LLEPrefs";
    private static volatile long cachedEpochMinute = Long.MIN_VALUE;
    private static volatile int cachedMinuteOfDay;
    static final String PREFS = "overlay_prefs";
    static final String MASTER_ENABLED = "master_enabled";
    static final String SHOW_AOD = "show_aod";
    static final String SHOW_HOME = "show_home";
    static final String SHOW_DOODLE = "show_doodle";
    static final String SEASONAL_UNLOCK_PARTNER = "seasonal_unlock_partner";
    static final String UNLOCK_EFFECT_TIME_ENABLED = "unlock_effect_time_enabled";
    static final String UNLOCK_EFFECT_TIME_START = "unlock_effect_time_start";
    static final String UNLOCK_EFFECT_TIME_END = "unlock_effect_time_end";
    static final String UNLOCK_EFFECT_SOUND_ENABLED = "unlock_effect_sound_enabled";
    static final String LLE_AUDIO_ROUTE_MEDIA = "lle_audio_route_media";
    static final String UNLOCK_EFFECT_SOUND_TIME_ENABLED =
            "unlock_effect_sound_time_enabled";
    static final String UNLOCK_EFFECT_SOUND_TIME_START = "unlock_effect_sound_time_start";
    static final String UNLOCK_EFFECT_SOUND_TIME_END = "unlock_effect_sound_time_end";
    static final String LOCK_SOUND_TIME_ENABLED = "lock_sound_time_enabled";
    static final String LOCK_SOUND_TIME_START = "lock_sound_time_start";
    static final String LOCK_SOUND_TIME_END = "lock_sound_time_end";
    static final String DOODLE_TIME_ENABLED = "doodle_time_enabled";
    static final String DOODLE_TIME_START = "doodle_time_start";
    static final String DOODLE_TIME_END = "doodle_time_end";
    static final String SEASONAL_UNLOCK_PARTNER_TIME_ENABLED =
            "seasonal_unlock_partner_time_enabled";
    static final String SEASONAL_UNLOCK_PARTNER_TIME_START =
            "seasonal_unlock_partner_time_start";
    static final String SEASONAL_UNLOCK_PARTNER_TIME_END =
            "seasonal_unlock_partner_time_end";
    static final String SEASONAL_UNLOCK_PARTNER_SOUND_ENABLED =
            "seasonal_unlock_partner_sound_enabled";
    static final String SEASONAL_UNLOCK_PARTNER_SOUND_TIME_ENABLED =
            "seasonal_unlock_partner_sound_time_enabled";
    static final String SEASONAL_UNLOCK_PARTNER_SOUND_TIME_START =
            "seasonal_unlock_partner_sound_time_start";
    static final String SEASONAL_UNLOCK_PARTNER_SOUND_TIME_END =
            "seasonal_unlock_partner_sound_time_end";
    static final String DOODLE_LOCK_SOUND_ENABLED = "doodle_lock_sound_enabled";
    static final String DOODLE_LOCK_SOUND_TIME_ENABLED =
            "doodle_lock_sound_time_enabled";
    static final String DOODLE_LOCK_SOUND_TIME_START = "doodle_lock_sound_time_start";
    static final String DOODLE_LOCK_SOUND_TIME_END = "doodle_lock_sound_time_end";
    static final String SEASON_MODE = "season_mode";
    static final String DOODLE_SIZE_PERCENT = "doodle_size_percent";
    static final String POSITION_OFFSET_X = "position_offset_x";
    static final String POSITION_OFFSET_Y = "position_offset_y";
    static final String DEBUG_ROLLING_CHARGE = "debug_rolling_charge";
    // Keep the original preference value so existing tester installs retain their choice.
    static final String DOODLE_AOD_ENABLED = "debug_doodle_aod_freeze";
    static final String DOODLE_AOD_BRIGHTNESS_PERCENT = "doodle_aod_brightness_percent";
    static final String DOODLE_AOD_OPACITY_PERCENT = "doodle_aod_opacity_percent";
    static final String DOODLE_OPACITY_PERCENT = "doodle_opacity_percent";
    static final int DOODLE_AOD_BRIGHTNESS_DEFAULT_PERCENT = 50;
    static final int DOODLE_AOD_OPACITY_DEFAULT_PERCENT = 50;
    static final int DOODLE_OPACITY_DEFAULT_PERCENT = 100;
    static final String DEBUG_TOUCH_AREA = "debug_touch_area";
    static final String DEBUG_TOUCH_TRANSPARENT = "debug_touch_transparent";
    static final String DEBUG_TOUCH_STANDBY = "debug_touch_standby";
    static final String DEBUG_LENS_LOOP = "debug_lens_loop";
    static final String USER_RUNTIME_BLACKLIST_PACKAGES =
            "user_runtime_blacklist_packages";
    static final String FOLD_MODE = "fold_mode";
    static final String FOLD_COVER_UNLOCK_EFFECT_ENABLED =
            "fold_cover_unlock_effect_enabled";
    static final String FOLD_COVER_DOODLE_ENABLED = "fold_cover_doodle_enabled";
    static final String FOLD_MAIN_UNLOCK_EFFECT_ENABLED =
            "fold_main_unlock_effect_enabled";
    static final String FOLD_MAIN_DOODLE_ENABLED = "fold_main_doodle_enabled";
    static final String UNLOCK_EFFECT_ENABLED = "unlock_effect_enabled";
    static final String LOCK_SOUND_ENABLED = "lock_sound_enabled";
    static final String UNLOCK_EFFECT = "unlock_effect";
    static final String ABSTRACT_TILES_LINE_ENABLED = "abstract_tiles_line_enabled";
    static final String EFFECT_PROFILE_LAST_SUMMARY = "effect_profile_last_summary";
    static final String EFFECT_PROFILE_DIAGNOSTIC_SUMMARY =
            "effect_profile_diagnostic_summary";
    static final String EFFECT_PROFILE_LAST_CSV = "effect_profile_last_csv";
    static final String EFFECT_PROFILE_RUNNING = "effect_profile_running";
    static final String EFFECT_PROFILE_SAMPLE_PENDING = "effect_profile_sample_pending";
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
    static final String EFFECT_BACKGROUND_SOURCE_MODE_PREFIX =
            "effect_background_source_mode_";
    static final String EFFECT_BACKGROUND_IMPORTED_PATH_PREFIX =
            "effect_background_imported_path_";
    private static final String EFFECT_BACKGROUND_IMPORTED_LABEL_PREFIX =
            "effect_background_imported_label_";
    private static final String EFFECT_BACKGROUND_IMPORTED_WIDTH_PREFIX =
            "effect_background_imported_width_";
    private static final String EFFECT_BACKGROUND_IMPORTED_HEIGHT_PREFIX =
            "effect_background_imported_height_";
    private static final String EFFECT_BACKGROUND_IMPORTED_AT_PREFIX =
            "effect_background_imported_at_";
    static final String PERF_DEFAULTS_APPLIED = "perf_defaults_20260701";
    static final String TOUCH_BOX_CONFIGURED = "touch_box_configured";
    static final String TOUCH_BOX_LEFT = "touch_box_left";
    static final String TOUCH_BOX_TOP = "touch_box_top";
    static final String TOUCH_BOX_RIGHT = "touch_box_right";
    static final String TOUCH_BOX_BOTTOM = "touch_box_bottom";
    static final String TOUCH_BOX_REGIONS = "touch_box_regions";
    static final String TOUCH_BOX_REFERENCE_WIDTH = "touch_box_reference_width";
    static final String TOUCH_BOX_REFERENCE_HEIGHT = "touch_box_reference_height";
    static final String TOUCH_BOX_CAPTURE_REQUEST_ID = "touch_box_capture_request_id";
    static final String TOUCH_BOX_CAPTURE_RESULT_ID = "touch_box_capture_result_id";
    static final String TOUCH_BOX_CAPTURE_STATE = "touch_box_capture_state";
    static final String TOUCH_BOX_CAPTURE_ERROR = "touch_box_capture_error";
    static final String TOUCH_BOX_CAPTURE_PROFILE = "touch_box_capture_profile";
    static final int TOUCH_BOX_CAPTURE_IDLE = 0;
    static final int TOUCH_BOX_CAPTURE_REQUESTED = 1;
    static final int TOUCH_BOX_CAPTURE_WAITING_LOCKSCREEN = 2;
    static final int TOUCH_BOX_CAPTURE_CAPTURING = 3;
    static final int TOUCH_BOX_CAPTURE_READY = 4;
    static final int TOUCH_BOX_CAPTURE_FAILED = 5;
    static final int EFFECT_S4_LENS_FLARE = 0;
    static final int EFFECT_S5_POPPING_COLOURS = 2;
    static final int EFFECT_WATERCOLOUR = 3;
    static final int EFFECT_N5_COLOUR_DROPLET = 4;
    static final int EFFECT_N5_SPARKLING_BUBBLES = 5;
    static final int EFFECT_S4_ABSTRACT_TILES = 7;
    static final int EFFECT_S4_GEOMETRIC_MOSAIC = 8;
    static final int EFFECT_N5_COLOUR_DROPLET_GYRO = 9;
    static final int EFFECT_S3_RIPPLE_NATIVE = 10;
    static final int EFFECT_TABS_BLIND = 11;
    static final int EFFECT_N4_INK_IN_WATER = 12;
    /** Kept only so preferences/code from the former reserved WIP slot remain compatible. */
    @Deprecated
    static final int EFFECT_N3_INK_IN_WATER_WIP = EFFECT_N4_INK_IN_WATER;
    static final int EFFECT_STONE_SKIPPING = 13;
    static final int EFFECT_BRILLIANT_RING = 14;
    static final int EFFECT_BRILLIANT_CUT = 15;
    static final int EFFECT_SEASONAL_SPRING = 16;
    static final int EFFECT_SEASONAL_SUMMER = 17;
    static final int EFFECT_SEASONAL_AUTUMN = 18;
    static final int EFFECT_SEASONAL_WINTER = 19;
    static final int EFFECT_SEASONAL_AUTO = 20;
    /** Distinct Galaxy S6 Water Droplet engine, available in the ARM64 product only. */
    static final int EFFECT_S6_WATER_DROPLET = 21;
    /** App-owned Sparkling Bubbles reconstruction, kept separate while it is WIP. */
    static final int EFFECT_N5_SPARKLING_BUBBLES_WIP = 22;
    /** App-owned Colored Droplet reconstruction, kept separate while it is WIP. */
    static final int EFFECT_N5_COLOUR_DROPLET_WIP = 23;
    /** App-owned Colored Droplet reconstruction with accelerometer physics. */
    static final int EFFECT_N5_COLOUR_DROPLET_GYRO_WIP = 24;
    /** App-owned, ABI-independent port of Samsung's hidden Mass Tension effect. */
    static final int EFFECT_MASS_TENSION = 25;
    /** App-owned Galaxy S6 Water Droplet reconstruction, kept separate while it is WIP. */
    static final int EFFECT_S6_WATER_DROPLET_APP_OWNED = 26;
    static final int EFFECT_COUNT = 27;
    static final int EFFECT_BACKGROUND_SOURCE_AUTO = 0;
    static final int EFFECT_BACKGROUND_SOURCE_IMPORTED = 1;
    static final int DEFAULT_TIME_START_MINUTE = 0;
    static final int DEFAULT_TIME_END_MINUTE = 0;
    static final int DEFAULT_TOUCH_BOX_LEFT = 0;
    static final int DEFAULT_TOUCH_BOX_TOP = 730;
    static final int DEFAULT_TOUCH_BOX_RIGHT = 1080;
    static final int DEFAULT_TOUCH_BOX_BOTTOM = 2100;
    static final int LEGACY_TOUCH_BOX_LEFT = 60;
    static final int LEGACY_TOUCH_BOX_TOP = 710;
    static final int LEGACY_TOUCH_BOX_RIGHT = 1030;
    static final int LEGACY_TOUCH_BOX_BOTTOM = 1900;
    static final int TOUCH_BOX_ROUNDING_PX = 10;
    static final int POSITION_OFFSET_MIN = -2000;
    static final int POSITION_OFFSET_MAX = 2000;
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

    static boolean masterEnabled(Context context) {
        return get(context).getBoolean(MASTER_ENABLED, true);
    }

    static boolean foldModeEnabled(Context context) {
        return get(context).getBoolean(FOLD_MODE, FoldDisplayTarget.isFoldDevice(context));
    }

    static boolean foldPanelUnlockEffectEnabled(Context context, String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        if (!foldModeEnabled(context) || FoldDisplayTarget.PROFILE_SINGLE.equals(normalized)) {
            return true;
        }
        String key = FoldDisplayTarget.PROFILE_MAIN.equals(normalized)
                ? FOLD_MAIN_UNLOCK_EFFECT_ENABLED
                : FOLD_COVER_UNLOCK_EFFECT_ENABLED;
        return get(context).getBoolean(key, true);
    }

    static boolean foldPanelDoodleEnabled(Context context, String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        if (!foldModeEnabled(context) || FoldDisplayTarget.PROFILE_SINGLE.equals(normalized)) {
            return true;
        }
        String key = FoldDisplayTarget.PROFILE_MAIN.equals(normalized)
                ? FOLD_MAIN_DOODLE_ENABLED
                : FOLD_COVER_DOODLE_ENABLED;
        return get(context).getBoolean(key, true);
    }

    static boolean isFoldPanelRoutingKey(String key) {
        return FOLD_COVER_UNLOCK_EFFECT_ENABLED.equals(key)
                || FOLD_COVER_DOODLE_ENABLED.equals(key)
                || FOLD_MAIN_UNLOCK_EFFECT_ENABLED.equals(key)
                || FOLD_MAIN_DOODLE_ENABLED.equals(key);
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
        // Retired beta routing. Seasonal unlocks are now regular picker effects,
        // while the charging doodle can coexist with any selected effect.
        return false;
    }

    static boolean unlockEffectAllowedNow(Context context) {
        return unlockEffectEnabled(context)
                && isImplementedEffect(unlockEffect(context))
                && timeWindowAllows(context,
                UNLOCK_EFFECT_TIME_ENABLED,
                UNLOCK_EFFECT_TIME_START,
                UNLOCK_EFFECT_TIME_END);
    }

    static boolean useMediaAudioRoute(Context context) {
        return get(context).getBoolean(LLE_AUDIO_ROUTE_MEDIA, false);
    }

    static boolean unlockEffectSoundAllowedNow(Context context) {
        return get(context).getBoolean(UNLOCK_EFFECT_SOUND_ENABLED, true)
                && EffectAudio.platformSoundSwitchAllows(context)
                && timeWindowAllows(context,
                UNLOCK_EFFECT_SOUND_TIME_ENABLED,
                UNLOCK_EFFECT_SOUND_TIME_START,
                UNLOCK_EFFECT_SOUND_TIME_END);
    }

    static boolean lockscreenLockSoundAllowedNow(Context context) {
        return lockSoundEnabled(context) && timeWindowAllows(context,
                LOCK_SOUND_TIME_ENABLED,
                LOCK_SOUND_TIME_START,
                LOCK_SOUND_TIME_END);
    }

    static boolean doodleAllowedNow(Context context) {
        return showDoodle(context) && timeWindowAllows(context,
                DOODLE_TIME_ENABLED,
                DOODLE_TIME_START,
                DOODLE_TIME_END);
    }

    static boolean seasonalUnlockPartnerAllowedNow(Context context) {
        return seasonalUnlockPartner(context) && timeWindowAllows(context,
                SEASONAL_UNLOCK_PARTNER_TIME_ENABLED,
                SEASONAL_UNLOCK_PARTNER_TIME_START,
                SEASONAL_UNLOCK_PARTNER_TIME_END);
    }

    static boolean seasonalUnlockPartnerSoundAllowedNow(Context context) {
        return get(context).getBoolean(SEASONAL_UNLOCK_PARTNER_SOUND_ENABLED, true)
                && timeWindowAllows(context,
                SEASONAL_UNLOCK_PARTNER_SOUND_TIME_ENABLED,
                SEASONAL_UNLOCK_PARTNER_SOUND_TIME_START,
                SEASONAL_UNLOCK_PARTNER_SOUND_TIME_END);
    }

    static boolean doodleLockSoundAllowedNow(Context context) {
        return get(context).getBoolean(DOODLE_LOCK_SOUND_ENABLED, true)
                && timeWindowAllows(context,
                DOODLE_LOCK_SOUND_TIME_ENABLED,
                DOODLE_LOCK_SOUND_TIME_START,
                DOODLE_LOCK_SOUND_TIME_END);
    }

    static boolean hasRuntimeSurfaceTimeWindow(Context context) {
        SharedPreferences prefs = get(context);
        return prefs.getBoolean(UNLOCK_EFFECT_TIME_ENABLED, false)
                || prefs.getBoolean(DOODLE_TIME_ENABLED, false);
    }

    static boolean timeWindowAllows(Context context, String enabledKey,
            String startKey, String endKey) {
        SharedPreferences prefs = get(context);
        if (!prefs.getBoolean(enabledKey, false)) {
            return true;
        }
        return isMinuteInWindow(currentMinuteOfDay(),
                timeMinute(prefs, startKey, DEFAULT_TIME_START_MINUTE),
                timeMinute(prefs, endKey, DEFAULT_TIME_END_MINUTE));
    }

    private static int currentMinuteOfDay() {
        long epochMinute = System.currentTimeMillis() / 60_000L;
        if (cachedEpochMinute != epochMinute) {
            Calendar calendar = Calendar.getInstance();
            cachedMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60
                    + calendar.get(Calendar.MINUTE);
            cachedEpochMinute = epochMinute;
        }
        return cachedMinuteOfDay;
    }

    static boolean isMinuteInWindow(int nowMinute, int startMinute, int endMinute) {
        int now = clampTimeMinute(nowMinute);
        int start = clampTimeMinute(startMinute);
        int end = clampTimeMinute(endMinute);
        if (start == end) {
            return true;
        }
        if (start < end) {
            return now >= start && now < end;
        }
        return now >= start || now < end;
    }

    static int timeMinute(SharedPreferences preferences, String key, int defaultValue) {
        return clampTimeMinute(preferences.getInt(key, defaultValue));
    }

    static int clampTimeMinute(int minute) {
        return Math.max(0, Math.min((24 * 60) - 1, minute));
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

    static boolean doodleAodEnabled(Context context) {
        return get(context).getBoolean(DOODLE_AOD_ENABLED, false);
    }

    static int doodleAodBrightnessPercent(Context context) {
        return clampPercent(get(context).getInt(DOODLE_AOD_BRIGHTNESS_PERCENT,
                DOODLE_AOD_BRIGHTNESS_DEFAULT_PERCENT));
    }

    static int doodleAodOpacityPercent(Context context) {
        return clampPercent(get(context).getInt(DOODLE_AOD_OPACITY_PERCENT,
                DOODLE_AOD_OPACITY_DEFAULT_PERCENT));
    }

    static int doodleOpacityPercent(Context context) {
        return clampPercent(get(context).getInt(DOODLE_OPACITY_PERCENT,
                DOODLE_OPACITY_DEFAULT_PERCENT));
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
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

    static Set<String> userRuntimeBlacklistPackages(Context context) {
        Set<String> stored = get(context).getStringSet(
                USER_RUNTIME_BLACKLIST_PACKAGES, null);
        return stored == null ? new HashSet<String>() : new HashSet<String>(stored);
    }

    static void setUserRuntimeBlacklistPackages(Context context, Set<String> packages) {
        Set<String> copy = packages == null
                ? new HashSet<String>() : new HashSet<String>(packages);
        get(context).edit().putStringSet(USER_RUNTIME_BLACKLIST_PACKAGES, copy).apply();
    }

    static String normalizePackageName(String packageName) {
        return packageName == null
                ? "" : packageName.trim().toLowerCase(java.util.Locale.US);
    }

    static boolean isValidPackageName(String packageName) {
        String normalized = normalizePackageName(packageName);
        if (normalized.length() < 3 || normalized.length() > 255
                || normalized.indexOf('.') <= 0
                || normalized.endsWith(".")) {
            return false;
        }
        String[] segments = normalized.split("\\.");
        if (segments.length < 2) {
            return false;
        }
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.length() == 0 || !isAsciiPackageLetter(segment.charAt(0))) {
                return false;
            }
            for (int j = 1; j < segment.length(); j++) {
                char value = segment.charAt(j);
                if (!isAsciiPackageLetter(value)
                        && (value < '0' || value > '9')
                        && value != '_') {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isAsciiPackageLetter(char value) {
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
    }

    static boolean unlockEffectEnabled(Context context) {
        return get(context).getBoolean(UNLOCK_EFFECT_ENABLED, true);
    }

    static boolean lockSoundEnabled(Context context) {
        return get(context).getBoolean(LOCK_SOUND_ENABLED, true);
    }

    static boolean abstractTilesLineEnabled(Context context) {
        return get(context).getBoolean(ABSTRACT_TILES_LINE_ENABLED, true);
    }

    static int unlockEffect(Context context) {
        SharedPreferences preferences = get(context);
        int effect = preferences.getInt(UNLOCK_EFFECT, EFFECT_S4_LENS_FLARE);
        // Values 1 and 6 belonged to superseded ripple experiments in early builds.
        if (effect == 1 || effect == 6) {
            effect = EFFECT_S3_RIPPLE_NATIVE;
            preferences.edit().putInt(UNLOCK_EFFECT, effect).apply();
        }
        if (!EffectAvailability.hasLegacyVendorEffects()
                && EffectAvailability.isLegacyVendorEffect(effect)) {
            int previousEffect = effect;
            effect = samsungFreeReplacement(effect);
            if (!isImplementedEffect(effect)) {
                effect = EFFECT_S4_LENS_FLARE;
            }
            preferences.edit().putInt(UNLOCK_EFFECT, effect).apply();
            Log.i(TAG, "migrated unavailable legacy vendor effect "
                    + previousEffect + " -> " + effect);
        }
        if (!isImplementedEffect(effect)) {
            preferences.edit().putInt(UNLOCK_EFFECT, EFFECT_S4_LENS_FLARE).apply();
            return EFFECT_S4_LENS_FLARE;
        }
        return effect;
    }

    static int rawUnlockEffect(Context context) {
        return get(context).getInt(UNLOCK_EFFECT, EFFECT_S4_LENS_FLARE);
    }

    private static int samsungFreeReplacement(int effect) {
        switch (effect) {
            case EFFECT_N5_COLOUR_DROPLET:
                return EFFECT_N5_COLOUR_DROPLET_WIP;
            case EFFECT_N5_COLOUR_DROPLET_GYRO:
                return EFFECT_N5_COLOUR_DROPLET_GYRO_WIP;
            case EFFECT_N5_SPARKLING_BUBBLES:
                return EFFECT_N5_SPARKLING_BUBBLES_WIP;
            case EFFECT_S6_WATER_DROPLET:
                return EFFECT_S6_WATER_DROPLET_APP_OWNED;
            default:
                return EFFECT_S4_LENS_FLARE;
        }
    }

    static String effectLabel(int effect) {
        switch (effect) {
            case EFFECT_S4_LENS_FLARE:
                return "S4 Lens Flare";
            case EFFECT_S5_POPPING_COLOURS:
                return "S5 Popping Colours";
            case EFFECT_WATERCOLOUR:
                return "N3 Watercolor";
            case EFFECT_N5_COLOUR_DROPLET:
                return EffectAvailability.hasLegacyVendorEffects()
                        ? "N5 Colored Droplet (Samsung legacy)"
                        : "N5 Colored Droplet";
            case EFFECT_N5_COLOUR_DROPLET_WIP:
                return EffectAvailability.hasLegacyVendorEffects()
                        ? "N5 Colored Droplet (LLE renderer)"
                        : "N5 Colored Droplet";
            case EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
                return EffectAvailability.hasLegacyVendorEffects()
                        ? "N5 Colored Droplet + Gyro (LLE renderer)"
                        : "N5 Colored Droplet + Gyro";
            case EFFECT_N5_COLOUR_DROPLET_GYRO:
                return EffectAvailability.hasLegacyVendorEffects()
                        ? "N5 Colored Droplet + Gyro (Samsung legacy)"
                        : "N5 Colored Droplet + Gyro";
            case EFFECT_N5_SPARKLING_BUBBLES:
                return EffectAvailability.hasLegacyVendorEffects()
                        ? "N5 Sparkling Bubbles (Samsung legacy)"
                        : "N5 Sparkling Bubbles";
            case EFFECT_N5_SPARKLING_BUBBLES_WIP:
                return EffectAvailability.hasLegacyVendorEffects()
                        ? "N5 Sparkling Bubbles (LLE renderer)"
                        : "N5 Sparkling Bubbles";
            case EFFECT_S6_WATER_DROPLET:
                return EffectAvailability.hasLegacyVendorEffects()
                        ? "S6 Water Droplet (Samsung legacy)"
                        : "S6 Water Droplet";
            case EFFECT_S6_WATER_DROPLET_APP_OWNED:
                return EffectAvailability.hasLegacyVendorEffects()
                        ? "S6 Water Droplet (LLE renderer)"
                        : "S6 Water Droplet";
            case EFFECT_S4_ABSTRACT_TILES:
                return "N4 Abstract Tiles";
            case EFFECT_S4_GEOMETRIC_MOSAIC:
                return "N4 Geometric Mosaic";
            case EFFECT_S3_RIPPLE_NATIVE:
                return "S3 Water Ripple";
            case EFFECT_TABS_BLIND:
                return "Tab S Blind";
            case EFFECT_N4_INK_IN_WATER:
                return "N2 Ink in Water";
            case EFFECT_STONE_SKIPPING:
                return "S5 Stone Skipping";
            case EFFECT_MASS_TENSION:
                return "Mass Tension";
            case EFFECT_BRILLIANT_RING:
                return "S5 Brilliant Ring";
            case EFFECT_BRILLIANT_CUT:
                return "Tab S Brilliant Cut";
            case EFFECT_SEASONAL_AUTO:
                return "Seasonal";
            case EFFECT_SEASONAL_SPRING:
                return "Seasonal Spring";
            case EFFECT_SEASONAL_SUMMER:
                return "Seasonal Summer";
            case EFFECT_SEASONAL_AUTUMN:
                return "Seasonal Autumn";
            case EFFECT_SEASONAL_WINTER:
                return "Seasonal Winter";
            default:
                return "Unknown effect " + effect;
        }
    }

    static boolean isSeasonalUnlockEffect(int effect) {
        return effect == EFFECT_SEASONAL_AUTO
                || (effect >= EFFECT_SEASONAL_SPRING && effect <= EFFECT_SEASONAL_WINTER);
    }

    static int seasonForUnlockEffect(int effect) {
        switch (effect) {
            case EFFECT_SEASONAL_AUTO:
                return SeasonalDoodleView.SEASON_AUTO;
            case EFFECT_SEASONAL_SPRING:
                return SeasonalDoodleView.SEASON_SPRING;
            case EFFECT_SEASONAL_SUMMER:
                return SeasonalDoodleView.SEASON_SUMMER;
            case EFFECT_SEASONAL_AUTUMN:
                return SeasonalDoodleView.SEASON_AUTUMN;
            case EFFECT_SEASONAL_WINTER:
                return SeasonalDoodleView.SEASON_WINTER;
            default:
                return SeasonalDoodleView.SEASON_AUTO;
        }
    }

    static boolean isColourDropletEffect(int effect) {
        return effect == EFFECT_N5_COLOUR_DROPLET
                || effect == EFFECT_N5_COLOUR_DROPLET_WIP
                || effect == EFFECT_N5_COLOUR_DROPLET_GYRO_WIP
                || effect == EFFECT_N5_COLOUR_DROPLET_GYRO;
    }

    static boolean isImplementedEffect(int effect) {
        return EffectAvailability.isAvailable(effect);
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

    static int effectBackgroundSourceMode(Context context, int effect, String profile) {
        return get(context).getInt(
                effectBackgroundProfileKey(EFFECT_BACKGROUND_SOURCE_MODE_PREFIX, effect, profile),
                EFFECT_BACKGROUND_SOURCE_AUTO);
    }

    static boolean importedEffectBackgroundEnabled(Context context, int effect, String profile) {
        return effectBackgroundSourceMode(context, effect, profile)
                == EFFECT_BACKGROUND_SOURCE_IMPORTED;
    }

    static File importedEffectBackgroundFile(Context context, int effect, String profile) {
        String path = get(context).getString(
                effectBackgroundProfileKey(
                        EFFECT_BACKGROUND_IMPORTED_PATH_PREFIX, effect, profile), "");
        return ManualEffectBackground.resolvePrivateFile(context, path);
    }

    static String importedEffectBackgroundLabel(Context context, int effect, String profile) {
        return get(context).getString(
                effectBackgroundProfileKey(
                        EFFECT_BACKGROUND_IMPORTED_LABEL_PREFIX, effect, profile),
                "Imported wallpaper");
    }

    static int importedEffectBackgroundWidth(Context context, int effect, String profile) {
        return Math.max(0, get(context).getInt(
                effectBackgroundProfileKey(
                        EFFECT_BACKGROUND_IMPORTED_WIDTH_PREFIX, effect, profile), 0));
    }

    static int importedEffectBackgroundHeight(Context context, int effect, String profile) {
        return Math.max(0, get(context).getInt(
                effectBackgroundProfileKey(
                        EFFECT_BACKGROUND_IMPORTED_HEIGHT_PREFIX, effect, profile), 0));
    }

    static long importedEffectBackgroundAt(Context context, int effect, String profile) {
        return Math.max(0L, get(context).getLong(
                effectBackgroundProfileKey(
                        EFFECT_BACKGROUND_IMPORTED_AT_PREFIX, effect, profile), 0L));
    }

    static void useImportedEffectBackground(Context context, int effect, String profile,
            File file, String label, int width, int height) {
        if (file == null) {
            return;
        }
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        get(context).edit()
                .putString(effectBackgroundProfileKey(
                                EFFECT_BACKGROUND_IMPORTED_PATH_PREFIX, effect, normalized),
                        file.getAbsolutePath())
                .putString(effectBackgroundProfileKey(
                                EFFECT_BACKGROUND_IMPORTED_LABEL_PREFIX, effect, normalized),
                        label == null || label.trim().isEmpty()
                                ? "Imported wallpaper" : label.trim())
                .putInt(effectBackgroundProfileKey(
                                EFFECT_BACKGROUND_IMPORTED_WIDTH_PREFIX, effect, normalized),
                        Math.max(0, width))
                .putInt(effectBackgroundProfileKey(
                                EFFECT_BACKGROUND_IMPORTED_HEIGHT_PREFIX, effect, normalized),
                        Math.max(0, height))
                .putLong(effectBackgroundProfileKey(
                                EFFECT_BACKGROUND_IMPORTED_AT_PREFIX, effect, normalized),
                        System.currentTimeMillis())
                .putInt(effectBackgroundProfileKey(
                                EFFECT_BACKGROUND_SOURCE_MODE_PREFIX, effect, normalized),
                        EFFECT_BACKGROUND_SOURCE_IMPORTED)
                .apply();
        ManualEffectBackground.pruneUnreferenced(context);
    }

    /** Pins one prepared wallpaper as the direct source for every screenshot-backed effect. */
    static boolean useImportedEffectBackgroundForAll(Context context, String profile,
            File file, String label, int width, int height) {
        if (file == null) {
            return false;
        }
        final int[] effects = {
                EFFECT_S4_LENS_FLARE,
                EFFECT_S3_RIPPLE_NATIVE,
                EFFECT_N4_INK_IN_WATER,
                EFFECT_S5_POPPING_COLOURS,
                EFFECT_TABS_BLIND,
                EFFECT_WATERCOLOUR,
                EFFECT_N5_COLOUR_DROPLET,
                EFFECT_N5_COLOUR_DROPLET_WIP,
                EFFECT_N5_COLOUR_DROPLET_GYRO_WIP,
                EFFECT_N5_COLOUR_DROPLET_GYRO,
                EFFECT_N5_SPARKLING_BUBBLES,
                EFFECT_N5_SPARKLING_BUBBLES_WIP,
                EFFECT_S6_WATER_DROPLET,
                EFFECT_S6_WATER_DROPLET_APP_OWNED,
                EFFECT_S4_ABSTRACT_TILES,
                EFFECT_S4_GEOMETRIC_MOSAIC,
                EFFECT_BRILLIANT_RING,
                EFFECT_BRILLIANT_CUT
        };
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        String safeLabel = label == null || label.trim().isEmpty()
                ? "Imported wallpaper" : label.trim();
        long importedAt = System.currentTimeMillis();
        SharedPreferences.Editor editor = get(context).edit();
        for (int effect : effects) {
            editor.putString(effectBackgroundProfileKey(
                            EFFECT_BACKGROUND_IMPORTED_PATH_PREFIX, effect, normalized),
                    file.getAbsolutePath());
            editor.putString(effectBackgroundProfileKey(
                            EFFECT_BACKGROUND_IMPORTED_LABEL_PREFIX, effect, normalized),
                    safeLabel);
            editor.putInt(effectBackgroundProfileKey(
                            EFFECT_BACKGROUND_IMPORTED_WIDTH_PREFIX, effect, normalized),
                    Math.max(0, width));
            editor.putInt(effectBackgroundProfileKey(
                            EFFECT_BACKGROUND_IMPORTED_HEIGHT_PREFIX, effect, normalized),
                    Math.max(0, height));
            editor.putLong(effectBackgroundProfileKey(
                            EFFECT_BACKGROUND_IMPORTED_AT_PREFIX, effect, normalized),
                    importedAt);
            editor.putInt(effectBackgroundProfileKey(
                            EFFECT_BACKGROUND_SOURCE_MODE_PREFIX, effect, normalized),
                    EFFECT_BACKGROUND_SOURCE_IMPORTED);
        }
        boolean committed = editor.commit();
        if (committed) {
            ManualEffectBackground.pruneUnreferenced(context);
        }
        return committed;
    }

    static void useAutomaticEffectBackground(Context context, int effect, String profile) {
        // Keep the pinned private image and its metadata. Reset only changes the active mode.
        get(context).edit()
                .putInt(effectBackgroundProfileKey(
                                EFFECT_BACKGROUND_SOURCE_MODE_PREFIX, effect, profile),
                        EFFECT_BACKGROUND_SOURCE_AUTO)
                .apply();
    }

    /** Restores automatic capture for every screenshot-backed effect without deleting imports. */
    static void useAutomaticEffectBackgroundForAll(Context context, String profile) {
        final int[] effects = {
                EFFECT_S4_LENS_FLARE,
                EFFECT_S3_RIPPLE_NATIVE,
                EFFECT_N4_INK_IN_WATER,
                EFFECT_S5_POPPING_COLOURS,
                EFFECT_TABS_BLIND,
                EFFECT_WATERCOLOUR,
                EFFECT_N5_COLOUR_DROPLET,
                EFFECT_N5_COLOUR_DROPLET_WIP,
                EFFECT_N5_COLOUR_DROPLET_GYRO_WIP,
                EFFECT_N5_COLOUR_DROPLET_GYRO,
                EFFECT_N5_SPARKLING_BUBBLES,
                EFFECT_N5_SPARKLING_BUBBLES_WIP,
                EFFECT_S6_WATER_DROPLET,
                EFFECT_S6_WATER_DROPLET_APP_OWNED,
                EFFECT_S4_ABSTRACT_TILES,
                EFFECT_S4_GEOMETRIC_MOSAIC,
                EFFECT_BRILLIANT_RING,
                EFFECT_BRILLIANT_CUT
        };
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        SharedPreferences.Editor editor = get(context).edit();
        for (int effect : effects) {
            editor.putInt(effectBackgroundProfileKey(
                            EFFECT_BACKGROUND_SOURCE_MODE_PREFIX, effect, normalized),
                    EFFECT_BACKGROUND_SOURCE_AUTO);
        }
        editor.apply();
    }

    static boolean isImportedEffectBackgroundPreferenceKey(String key) {
        return key != null && (key.startsWith(EFFECT_BACKGROUND_SOURCE_MODE_PREFIX)
                || key.startsWith(EFFECT_BACKGROUND_IMPORTED_PATH_PREFIX)
                || key.startsWith(EFFECT_BACKGROUND_IMPORTED_LABEL_PREFIX)
                || key.startsWith(EFFECT_BACKGROUND_IMPORTED_WIDTH_PREFIX)
                || key.startsWith(EFFECT_BACKGROUND_IMPORTED_HEIGHT_PREFIX)
                || key.startsWith(EFFECT_BACKGROUND_IMPORTED_AT_PREFIX));
    }

    static boolean isImportedEffectBackgroundPreferenceKeyFor(String key, int effect,
            String profile) {
        if (key == null) {
            return false;
        }
        return key.equals(effectBackgroundProfileKey(
                EFFECT_BACKGROUND_SOURCE_MODE_PREFIX, effect, profile))
                || key.equals(effectBackgroundProfileKey(
                EFFECT_BACKGROUND_IMPORTED_PATH_PREFIX, effect, profile));
    }

    static long effectBackgroundLastCapturedAt(Context context, int effect) {
        return effectBackgroundLastCapturedAt(context, effect, FoldDisplayTarget.PROFILE_SINGLE);
    }

    static long effectBackgroundLastCapturedAt(Context context, int effect, String profile) {
        return get(context).getLong(effectBackgroundLastCaptureKey(effect, profile), 0L);
    }

    static void saveEffectBackgroundLastCapturedAt(Context context, int effect, long timestamp) {
        saveEffectBackgroundLastCapturedAt(
                context, effect, FoldDisplayTarget.PROFILE_SINGLE, timestamp);
    }

    static void saveEffectBackgroundLastCapturedAt(Context context, int effect, String profile,
            long timestamp) {
        get(context).edit()
                .putLong(effectBackgroundLastCaptureKey(effect, profile),
                        Math.max(0L, timestamp))
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
        return effectBackgroundHandledRefreshToken(
                context, effect, FoldDisplayTarget.PROFILE_SINGLE);
    }

    static int effectBackgroundHandledRefreshToken(Context context, int effect, String profile) {
        return get(context).getInt(effectBackgroundHandledRefreshTokenKey(effect, profile), 0);
    }

    static void saveEffectBackgroundHandledRefreshToken(Context context, int effect, int token) {
        saveEffectBackgroundHandledRefreshToken(
                context, effect, FoldDisplayTarget.PROFILE_SINGLE, token);
    }

    static void saveEffectBackgroundHandledRefreshToken(Context context, int effect, String profile,
            int token) {
        get(context).edit()
                .putInt(effectBackgroundHandledRefreshTokenKey(effect, profile), Math.max(0, token))
                .apply();
    }

    static String effectBackgroundLastCaptureKey(int effect, String profile) {
        return EFFECT_BACKGROUND_LAST_CAPTURE_PREFIX + effect + profileKeySuffix(profile);
    }

    static String effectBackgroundHandledRefreshTokenKey(int effect, String profile) {
        return EFFECT_BACKGROUND_HANDLED_REFRESH_TOKEN_PREFIX + effect + profileKeySuffix(profile);
    }

    private static String effectBackgroundProfileKey(String prefix, int effect, String profile) {
        return prefix + effect + profileKeySuffix(profile);
    }

    private static String profileKeySuffix(String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        return FoldDisplayTarget.PROFILE_SINGLE.equals(normalized) ? "" : "_" + normalized;
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

    private static String touchBoxProfile(Context context) {
        return FoldDisplayTarget.cacheProfileForContext(context);
    }

    private static String touchBoxKey(String base, String profile) {
        return base + profileKeySuffix(profile);
    }

    static boolean isTouchBoxPreferenceKey(String key) {
        return key != null && (key.startsWith(TOUCH_BOX_CONFIGURED)
                || key.startsWith(TOUCH_BOX_LEFT)
                || key.startsWith(TOUCH_BOX_TOP)
                || key.startsWith(TOUCH_BOX_RIGHT)
                || key.startsWith(TOUCH_BOX_BOTTOM)
                || key.startsWith(TOUCH_BOX_REGIONS)
                || key.startsWith(TOUCH_BOX_REFERENCE_WIDTH)
                || key.startsWith(TOUCH_BOX_REFERENCE_HEIGHT));
    }

    static boolean touchBoxConfigured(Context context) {
        return touchBoxConfigured(context, touchBoxProfile(context));
    }

    static boolean touchBoxConfigured(Context context, String profile) {
        return get(context).getBoolean(touchBoxKey(TOUCH_BOX_CONFIGURED, profile), false);
    }

    static int touchBoxLeft(Context context) {
        return touchBoxLeft(context, touchBoxProfile(context));
    }

    static int touchBoxLeft(Context context, String profile) {
        return roundTouchCoordinate(get(context).getInt(
                touchBoxKey(TOUCH_BOX_LEFT, profile), DEFAULT_TOUCH_BOX_LEFT));
    }

    static int touchBoxTop(Context context) {
        return touchBoxTop(context, touchBoxProfile(context));
    }

    static int touchBoxTop(Context context, String profile) {
        return roundTouchCoordinate(get(context).getInt(
                touchBoxKey(TOUCH_BOX_TOP, profile), DEFAULT_TOUCH_BOX_TOP));
    }

    static int touchBoxRight(Context context) {
        return touchBoxRight(context, touchBoxProfile(context));
    }

    static int touchBoxRight(Context context, String profile) {
        return roundTouchCoordinate(get(context).getInt(
                touchBoxKey(TOUCH_BOX_RIGHT, profile), DEFAULT_TOUCH_BOX_RIGHT));
    }

    static int touchBoxBottom(Context context) {
        return touchBoxBottom(context, touchBoxProfile(context));
    }

    static int touchBoxBottom(Context context, String profile) {
        return roundTouchCoordinate(get(context).getInt(
                touchBoxKey(TOUCH_BOX_BOTTOM, profile), DEFAULT_TOUCH_BOX_BOTTOM));
    }

    static void saveTouchBox(Context context, int left, int top, int right, int bottom) {
        saveTouchBox(context, touchBoxProfile(context), left, top, right, bottom);
    }

    static void saveTouchBox(Context context, String profile, int left, int top, int right,
            int bottom) {
        int[] displaySize = currentDisplaySize(context);
        get(context).edit()
                .putBoolean(touchBoxKey(TOUCH_BOX_CONFIGURED, profile), true)
                .putInt(touchBoxKey(TOUCH_BOX_LEFT, profile), roundTouchCoordinate(left))
                .putInt(touchBoxKey(TOUCH_BOX_TOP, profile), roundTouchCoordinate(top))
                .putInt(touchBoxKey(TOUCH_BOX_RIGHT, profile), roundTouchCoordinate(right))
                .putInt(touchBoxKey(TOUCH_BOX_BOTTOM, profile), roundTouchCoordinate(bottom))
                .putInt(touchBoxKey(TOUCH_BOX_REFERENCE_WIDTH, profile), displaySize[0])
                .putInt(touchBoxKey(TOUCH_BOX_REFERENCE_HEIGHT, profile), displaySize[1])
                .remove(touchBoxKey(TOUCH_BOX_REGIONS, profile))
                .apply();
    }

    static void saveTouchBoxOutward(Context context, int left, int top, int right, int bottom) {
        saveTouchBoxOutward(context, touchBoxProfile(context), left, top, right, bottom);
    }

    static void saveTouchBoxOutward(Context context, String profile, int left, int top,
            int right, int bottom) {
        int[] displaySize = currentDisplaySize(context);
        get(context).edit()
                .putBoolean(touchBoxKey(TOUCH_BOX_CONFIGURED, profile), true)
                .putInt(touchBoxKey(TOUCH_BOX_LEFT, profile), roundTouchCoordinateDown(left))
                .putInt(touchBoxKey(TOUCH_BOX_TOP, profile), roundTouchCoordinateDown(top))
                .putInt(touchBoxKey(TOUCH_BOX_RIGHT, profile), roundTouchCoordinateUp(right))
                .putInt(touchBoxKey(TOUCH_BOX_BOTTOM, profile), roundTouchCoordinateUp(bottom))
                .putInt(touchBoxKey(TOUCH_BOX_REFERENCE_WIDTH, profile), displaySize[0])
                .putInt(touchBoxKey(TOUCH_BOX_REFERENCE_HEIGHT, profile), displaySize[1])
                .remove(touchBoxKey(TOUCH_BOX_REGIONS, profile))
                .apply();
    }

    static ArrayList<Rect> touchBoxRegions(Context context) {
        return touchBoxRegions(context, touchBoxProfile(context));
    }

    static ArrayList<Rect> touchBoxRegions(Context context, String profile) {
        int[] displaySize = currentDisplaySize(context);
        return touchBoxRegions(context, profile, displaySize[0], displaySize[1]);
    }

    static ArrayList<Rect> touchBoxRegions(Context context, String profile, int targetWidth,
            int targetHeight) {
        ArrayList<Rect> regions = new ArrayList<Rect>();
        String encoded = get(context).getString(touchBoxKey(TOUCH_BOX_REGIONS, profile), "");
        if (encoded != null && encoded.length() > 0) {
            String[] entries = encoded.split(";");
            for (String entry : entries) {
                String[] values = entry.split(",");
                if (values.length != 4) {
                    continue;
                }
                try {
                    int left = Integer.parseInt(values[0]);
                    int top = Integer.parseInt(values[1]);
                    int right = Integer.parseInt(values[2]);
                    int bottom = Integer.parseInt(values[3]);
                    if (right > left && bottom > top) {
                        regions.add(new Rect(left, top, right, bottom));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (regions.isEmpty()) {
            regions.add(new Rect(touchBoxLeft(context, profile), touchBoxTop(context, profile),
                    touchBoxRight(context, profile), touchBoxBottom(context, profile)));
        }
        int[] displaySize = new int[]{Math.max(1, targetWidth), Math.max(1, targetHeight)};
        SharedPreferences prefs = get(context);
        int referenceWidth = prefs.getInt(touchBoxKey(TOUCH_BOX_REFERENCE_WIDTH, profile),
                DEFAULT_TOUCH_BOX_RIGHT);
        int referenceHeight = prefs.getInt(touchBoxKey(TOUCH_BOX_REFERENCE_HEIGHT, profile), 2316);
        if (referenceWidth > 0 && referenceHeight > 0
                && displaySize[0] > 0 && displaySize[1] > 0
                && (referenceWidth != displaySize[0]
                || referenceHeight != displaySize[1])) {
            float scaleX = displaySize[0] / (float) referenceWidth;
            float scaleY = displaySize[1] / (float) referenceHeight;
            for (Rect region : regions) {
                region.set(Math.round(region.left * scaleX),
                        Math.round(region.top * scaleY),
                        Math.round(region.right * scaleX),
                        Math.round(region.bottom * scaleY));
            }
        }
        return regions;
    }

    static void saveTouchBoxRegionsOutward(Context context, List<Rect> source) {
        saveTouchBoxRegionsOutward(context, touchBoxProfile(context), source);
    }

    static void saveTouchBoxRegionsOutward(Context context, String profile, List<Rect> source) {
        int[] displaySize = currentDisplaySize(context);
        saveTouchBoxRegionsOutward(context, profile, source, displaySize[0], displaySize[1]);
    }

    static void saveTouchBoxRegionsOutward(Context context, String profile, List<Rect> source,
            int referenceWidth, int referenceHeight) {
        if (source == null || source.isEmpty()) {
            return;
        }
        ArrayList<Rect> regions = new ArrayList<Rect>();
        Rect bounds = new Rect();
        for (Rect rect : source) {
            if (rect == null || rect.width() <= 0 || rect.height() <= 0) {
                continue;
            }
            Rect rounded = new Rect(
                    roundTouchCoordinateDown(rect.left),
                    roundTouchCoordinateDown(rect.top),
                    roundTouchCoordinateUp(rect.right),
                    roundTouchCoordinateUp(rect.bottom));
            if (regions.isEmpty()) {
                bounds.set(rounded);
            } else {
                bounds.union(rounded);
            }
            regions.add(rounded);
        }
        if (regions.isEmpty()) {
            return;
        }
        StringBuilder encoded = new StringBuilder();
        for (Rect rect : regions) {
            if (encoded.length() > 0) {
                encoded.append(';');
            }
            encoded.append(rect.left).append(',').append(rect.top).append(',')
                    .append(rect.right).append(',').append(rect.bottom);
        }
        int[] displaySize = new int[]{Math.max(1, referenceWidth), Math.max(1, referenceHeight)};
        get(context).edit()
                .putBoolean(touchBoxKey(TOUCH_BOX_CONFIGURED, profile), true)
                .putInt(touchBoxKey(TOUCH_BOX_LEFT, profile), bounds.left)
                .putInt(touchBoxKey(TOUCH_BOX_TOP, profile), bounds.top)
                .putInt(touchBoxKey(TOUCH_BOX_RIGHT, profile), bounds.right)
                .putInt(touchBoxKey(TOUCH_BOX_BOTTOM, profile), bounds.bottom)
                .putString(touchBoxKey(TOUCH_BOX_REGIONS, profile), encoded.toString())
                .putInt(touchBoxKey(TOUCH_BOX_REFERENCE_WIDTH, profile), displaySize[0])
                .putInt(touchBoxKey(TOUCH_BOX_REFERENCE_HEIGHT, profile), displaySize[1])
                .apply();
    }

    static void clearTouchBox(Context context) {
        clearTouchBox(context, touchBoxProfile(context));
    }

    static void clearTouchBox(Context context, String profile) {
        get(context).edit()
                .putBoolean(touchBoxKey(TOUCH_BOX_CONFIGURED, profile), false)
                .remove(touchBoxKey(TOUCH_BOX_LEFT, profile))
                .remove(touchBoxKey(TOUCH_BOX_TOP, profile))
                .remove(touchBoxKey(TOUCH_BOX_RIGHT, profile))
                .remove(touchBoxKey(TOUCH_BOX_BOTTOM, profile))
                .remove(touchBoxKey(TOUCH_BOX_REGIONS, profile))
                .remove(touchBoxKey(TOUCH_BOX_REFERENCE_WIDTH, profile))
                .remove(touchBoxKey(TOUCH_BOX_REFERENCE_HEIGHT, profile))
                .apply();
    }

    private static int[] currentDisplaySize(Context context) {
        int width = Math.max(1, context.getResources().getDisplayMetrics().widthPixels);
        int height = Math.max(1, context.getResources().getDisplayMetrics().heightPixels);
        return new int[]{width, height};
    }

    static void migrateLegacyTouchBoxIfNeeded(Context context) {
        migrateLegacyTouchBoxIfNeeded(context, touchBoxProfile(context));
    }

    static void migrateLegacyTouchBoxIfNeeded(Context context, String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        SharedPreferences prefs = get(context);
        if (!FoldDisplayTarget.PROFILE_SINGLE.equals(normalized)
                && !touchBoxConfigured(context, normalized)
                && prefs.getBoolean(TOUCH_BOX_CONFIGURED, false)) {
            int referenceWidth = prefs.getInt(TOUCH_BOX_REFERENCE_WIDTH,
                    DEFAULT_TOUCH_BOX_RIGHT);
            int referenceHeight = prefs.getInt(TOUCH_BOX_REFERENCE_HEIGHT, 2316);
            String legacyProfile = FoldDisplayTarget.profileForSize(
                    referenceWidth, referenceHeight);
            if (normalized.equals(legacyProfile)) {
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean(touchBoxKey(TOUCH_BOX_CONFIGURED, normalized), true)
                        .putInt(touchBoxKey(TOUCH_BOX_LEFT, normalized),
                                prefs.getInt(TOUCH_BOX_LEFT, DEFAULT_TOUCH_BOX_LEFT))
                        .putInt(touchBoxKey(TOUCH_BOX_TOP, normalized),
                                prefs.getInt(TOUCH_BOX_TOP, DEFAULT_TOUCH_BOX_TOP))
                        .putInt(touchBoxKey(TOUCH_BOX_RIGHT, normalized),
                                prefs.getInt(TOUCH_BOX_RIGHT, DEFAULT_TOUCH_BOX_RIGHT))
                        .putInt(touchBoxKey(TOUCH_BOX_BOTTOM, normalized),
                                prefs.getInt(TOUCH_BOX_BOTTOM, DEFAULT_TOUCH_BOX_BOTTOM))
                        .putInt(touchBoxKey(TOUCH_BOX_REFERENCE_WIDTH, normalized),
                                referenceWidth)
                        .putInt(touchBoxKey(TOUCH_BOX_REFERENCE_HEIGHT, normalized),
                                referenceHeight);
                String regions = prefs.getString(TOUCH_BOX_REGIONS, "");
                if (regions != null && regions.length() > 0) {
                    editor.putString(touchBoxKey(TOUCH_BOX_REGIONS, normalized), regions);
                }
                editor.apply();
                File legacyScreenshot = touchBoxScreenshotFile(
                        context, FoldDisplayTarget.PROFILE_SINGLE);
                File profileScreenshot = touchBoxScreenshotFile(context, normalized);
                if (legacyScreenshot.exists() && !profileScreenshot.exists()) {
                    legacyScreenshot.renameTo(profileScreenshot);
                }
            }
        }
        if (!touchBoxConfigured(context, normalized)) {
            return;
        }
        int left = touchBoxLeft(context, normalized);
        int top = touchBoxTop(context, normalized);
        int right = touchBoxRight(context, normalized);
        int bottom = touchBoxBottom(context, normalized);
        if (left == LEGACY_TOUCH_BOX_LEFT
                && top == LEGACY_TOUCH_BOX_TOP
                && right == LEGACY_TOUCH_BOX_RIGHT
                && bottom == LEGACY_TOUCH_BOX_BOTTOM) {
            saveTouchBox(context, normalized,
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
        return touchBoxScreenshotFile(context, touchBoxProfile(context));
    }

    static File touchBoxScreenshotFile(Context context, String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        String suffix = FoldDisplayTarget.PROFILE_SINGLE.equals(normalized)
                ? "" : "_" + normalized;
        return new File(context.getFilesDir(), "touch_box_lockscreen" + suffix + ".png");
    }

    static File effectBackgroundFile(Context context, int effect) {
        return effectBackgroundFile(context, effect, FoldDisplayTarget.PROFILE_SINGLE);
    }

    static File effectBackgroundFile(Context context, int effect, String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        String suffix = FoldDisplayTarget.PROFILE_SINGLE.equals(normalized)
                ? "" : "_" + normalized;
        return new File(context.getFilesDir(), "unlock_effect_background" + suffix + ".png");
    }

    static File legacyEffectBackgroundFile(Context context, int effect) {
        return new File(context.getFilesDir(), "unlock_effect_background_" + effect + ".png");
    }
}

