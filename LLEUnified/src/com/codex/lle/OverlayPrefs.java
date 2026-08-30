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
import java.util.Random;
import java.util.Set;

final class OverlayPrefs {
    private static final String TAG = "LLEPrefs";
    private static volatile long cachedEpochMinute = Long.MIN_VALUE;
    private static volatile int cachedMinuteOfDay;
    static final String PREFS = "overlay_prefs";
    static final String MASTER_ENABLED = "master_enabled";
    /** Tester-only low-memory mode: never capture, load or retain lockscreen colormaps. */
    static final String TESTER_NO_COLORMAP_MODE = "tester_no_colormap_mode";
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
    static final String THREE_FINGER_SAFETY_BYPASS_ENABLED =
            "three_finger_safety_bypass_enabled";
    static final String DEBUG_BYPASS_BOOT_SAFETY = "debug_bypass_boot_safety";
    static final String DEBUG_CONSERVATIVE_UNLOCK_HANDOFF =
            "debug_conservative_unlock_handoff";
    /**
     * Opt-in comparison path for high-refresh unlock effects. Legacy motion and
     * presentation stay the production default until device testing confirms
     * the display-refresh path.
     */
    static final String DEBUG_EXPERIMENTAL_NATIVE_REFRESH_PHYSICS =
            "debug_experimental_native_refresh_physics";
    /** 1.0x–2.0x, stored as integer tenths to avoid preference float drift. */
    static final String DEBUG_EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_SPEED_TENTHS =
            "debug_experimental_native_refresh_physics_speed_tenths";
    /** One-shot migration marker for the per-effect native-refresh preferences. */
    private static final String EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_PER_EFFECT_SCHEMA =
            "experimental_native_refresh_physics_per_effect_schema";
    private static final int EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_PER_EFFECT_SCHEMA_VERSION = 1;
    private static final String EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_EFFECT_PREFIX =
            "experimental_native_refresh_physics_effect_";
    private static final String EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_EFFECT_PREFIX =
            "experimental_native_refresh_physics_speed_tenths_effect_";
    private static final int NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_DEFAULT = 10;
    private static final int NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_MIN = 10;
    private static final int NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_MAX = 20;
    static final String DEBUG_LEGACY_QUICK_PANEL_DETECTION =
            "debug_legacy_quick_panel_detection";
    static final String DEBUG_LENS_LOOP = "debug_lens_loop";
    /** Retired Lens Flare GLES A/B key. Kept only so older tester commands stay harmless. */
    static final String LENS_FLARE_GLES_RENDERER = "lens_flare_gles_renderer_v2";
    static final String LENS_FLARE_MODE = "lens_flare_mode";
    static final String LENS_FLARE_MODE_FLARE = "flare";
    static final String LENS_FLARE_MODE_BLUE_RING = "bluering";
    static final String LENS_FLARE_MODE_BLOOD = "blood";
    static final String LENS_FLARE_MODE_LIGHTNING = "lightning";
    static final String USER_RUNTIME_BLACKLIST_PACKAGES =
            "user_runtime_blacklist_packages";
    static final String FOLD_MODE = "fold_mode";
    static final String TABLET_MODE = "tablet_mode";
    static final String FOLD_COVER_UNLOCK_EFFECT_ENABLED =
            "fold_cover_unlock_effect_enabled";
    static final String FOLD_COVER_DOODLE_ENABLED = "fold_cover_doodle_enabled";
    static final String FOLD_MAIN_UNLOCK_EFFECT_ENABLED =
            "fold_main_unlock_effect_enabled";
    static final String FOLD_MAIN_DOODLE_ENABLED = "fold_main_doodle_enabled";
    static final String UNLOCK_EFFECT_ENABLED = "unlock_effect_enabled";
    static final String LOCK_SOUND_ENABLED = "lock_sound_enabled";
    static final String UNLOCK_EFFECT = "unlock_effect";
    /** Random is a selection mode, never a renderer id. */
    static final String UNLOCK_EFFECT_RANDOM_ENABLED = "unlock_effect_random_enabled";
    /** String-set of decimal effect ids selected for the Random shuffle bag. */
    static final String UNLOCK_EFFECT_RANDOM_POOL = "unlock_effect_random_pool";
    /** Stable renderer selected for the current lock/unlock cycle. */
    static final String UNLOCK_EFFECT_RANDOM_CURRENT = "unlock_effect_random_current";
    /** Pool members not yet drawn in the current shuffle-bag pass. */
    static final String UNLOCK_EFFECT_RANDOM_REMAINING = "unlock_effect_random_remaining";
    /** Emergency None renderer remains active until the next completed unlock. */
    private static final String UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE =
            "unlock_effect_random_fallback_active";
    /** Reserved until the Ripple Ink reverse path has a production-ready ABI. */
    static final String RIPPLE_INK_PALETTE = "ripple_ink_palette";
    static final int RIPPLE_INK_PALETTE_DEFAULT = 4;
    static final int RIPPLE_INK_PALETTE_MIN = 1;
    static final int RIPPLE_INK_PALETTE_MAX = 8;
    static final String ABSTRACT_TILES_LINE_ENABLED = "abstract_tiles_line_enabled";
    static final String N5_COLOUR_DROPLET_GYRO_ENABLED =
            "n5_colour_droplet_gyro_enabled";
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
    private static final String LG_PRELOCK_UNDERLAY_METADATA_PREFIX =
            "lg_prelock_underlay_metadata_";
    static final String LG_PRELOCK_UNDERLAY_ORIGIN_LAST_SCREEN = "last_screen";
    static final String LG_PRELOCK_UNDERLAY_ORIGIN_WALLPAPER_FALLBACK =
            "wallpaper_fallback";
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
    /** Reserved: hidden until Ripple Ink has a verified app-owned ABI path. */
    static final int EFFECT_RIPPLE_INK = 27;
    /** App-owned Good Lock particle renderer: popping-color variant. */
    static final int EFFECT_GOOD_LOCK_POPPING = 28;
    /** App-owned Good Lock particle renderer: rectangle-traveller variant. */
    static final int EFFECT_GOOD_LOCK_RECTANGLE = 29;
    /** App-owned Good Lock particle renderer: bouncing-color variant. */
    static final int EFFECT_GOOD_LOCK_BOUNCING = 30;
    /** Tester-only app-owned reconstruction of the Galaxy S3 None / Circle Unlock. */
    static final int EFFECT_S3_NONE = 31;
    /** Clean-room LG G2 Pixelate restoration with separate lockscreen and Last screen sources. */
    static final int EFFECT_LG_G2_PIXELATE = 32;
    /** Tester-only restoration of LG G2 Particle from the authorized XLocker archive. */
    static final int EFFECT_LG_G2_PARTICLE = 33;
    /** Tester-only app-owned LG G2 Crystal-inspired renderer. */
    static final int EFFECT_LG_G2_CRYSTAL = 34;
    /** Tester-only app-owned Xperia Z1 Blinds-inspired renderer. */
    static final int EFFECT_XPERIA_Z1_BLINDS = 35;
    /** Clean-room Revolving Glass renderer with independent lockscreen and Last screen sources. */
    static final int EFFECT_REVOLVING_GLASS = 36;
    /** Tester-only restoration of LG G1 White Hole from the authorized XLocker archive. */
    static final int EFFECT_LG_G1_WHITE_HOLE = 37;
    /** Tester-only restoration of LG Soda from the authorized XLocker archive. */
    static final int EFFECT_LG_SODA = 38;
    /** Tester-only restoration of LG G1 Dewdrop from the authorized XLocker archive. */
    static final int EFFECT_LG_G1_DEWDROP = 39;
    /** Tester-only restoration of LG G2 Light Particle from the authorized XLocker archive. */
    static final int EFFECT_LG_G2_LIGHT_PARTICLE = 40;
    static final int EFFECT_COUNT = 41;
    static final int EFFECT_BACKGROUND_SOURCE_AUTO = 0;
    static final int EFFECT_BACKGROUND_SOURCE_IMPORTED = 1;
    static final int DEFAULT_TIME_START_MINUTE = 0;
    static final int DEFAULT_TIME_END_MINUTE = 0;
    static final int DEFAULT_TOUCH_BOX_LEFT = 0;
    static final int DEFAULT_TOUCH_BOX_TOP = 730;
    static final int DEFAULT_TOUCH_BOX_RIGHT = 1080;
    static final int DEFAULT_TOUCH_BOX_BOTTOM = 2100;
    /** Deliberately small fallback used only until a touch box is explicitly saved. */
    static final int SAFE_TOUCH_BOX_LEFT = 490;
    static final int SAFE_TOUCH_BOX_TOP = 1100;
    static final int SAFE_TOUCH_BOX_RIGHT = 590;
    static final int SAFE_TOUCH_BOX_BOTTOM = 1200;
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

    static boolean tabletModeEnabled(Context context) {
        return get(context).getBoolean(TABLET_MODE,
                FoldDisplayTarget.isTabletDevice(context)
                        && !FoldDisplayTarget.isFoldDevice(context));
    }

    static boolean foldPanelUnlockEffectEnabled(Context context, String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        if (!foldModeEnabled(context)
                || (!FoldDisplayTarget.PROFILE_COVER.equals(normalized)
                && !FoldDisplayTarget.PROFILE_MAIN.equals(normalized))) {
            return true;
        }
        String key = FoldDisplayTarget.PROFILE_MAIN.equals(normalized)
                ? FOLD_MAIN_UNLOCK_EFFECT_ENABLED
                : FOLD_COVER_UNLOCK_EFFECT_ENABLED;
        return get(context).getBoolean(key, true);
    }

    static boolean foldPanelDoodleEnabled(Context context, String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        if (!foldModeEnabled(context)
                || (!FoldDisplayTarget.PROFILE_COVER.equals(normalized)
                && !FoldDisplayTarget.PROFILE_MAIN.equals(normalized))) {
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
                && isImplementedEffect(context, unlockEffect(context))
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

    static boolean threeFingerSafetyBypassEnabled(Context context) {
        return get(context).getBoolean(THREE_FINGER_SAFETY_BYPASS_ENABLED, true);
    }

    static boolean debugBypassBootSafety(Context context) {
        return get(context).getBoolean(DEBUG_BYPASS_BOOT_SAFETY, false);
    }

    static boolean debugConservativeUnlockHandoff(Context context) {
        return get(context).getBoolean(DEBUG_CONSERVATIVE_UNLOCK_HANDOFF, false);
    }

    static boolean debugExperimentalNativeRefreshPhysics(Context context) {
        return get(context).getBoolean(DEBUG_EXPERIMENTAL_NATIVE_REFRESH_PHYSICS, false);
    }

    static int debugExperimentalNativeRefreshPhysicsSpeedTenths(Context context) {
        int tenths = get(context).getInt(
                DEBUG_EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_SPEED_TENTHS,
                NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_DEFAULT);
        return Math.max(NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_MIN,
                Math.min(NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_MAX, tenths));
    }

    /**
     * Copies the former global native-refresh controls into every renderer that supports
     * them. Keep the legacy keys intact so a tester can safely roll back to an older build.
     */
    static void migrateExperimentalNativeRefreshPrefsIfNeeded(Context context) {
        SharedPreferences preferences = get(context);
        if (preferences.getInt(EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_PER_EFFECT_SCHEMA, 0)
                >= EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_PER_EFFECT_SCHEMA_VERSION) {
            return;
        }
        boolean legacyEnabled = debugExperimentalNativeRefreshPhysics(context);
        int legacySpeedTenths = debugExperimentalNativeRefreshPhysicsSpeedTenths(context);
        SharedPreferences.Editor editor = preferences.edit();
        for (int effect = 0; effect < EFFECT_COUNT; effect++) {
            if (!supportsExperimentalNativeRefreshPhysics(effect)) {
                continue;
            }
            editor.putBoolean(experimentalNativeRefreshPhysicsKey(effect), legacyEnabled);
            if (supportsExperimentalNativeRefreshPhysicsSpeed(effect)) {
                editor.putInt(experimentalNativeRefreshPhysicsSpeedTenthsKey(effect),
                        legacySpeedTenths);
            }
        }
        // Write this last in the same synchronous transaction: a partial migration will run
        // again, while a completed marker means all effect values became visible atomically.
        boolean committed = editor.putInt(EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_PER_EFFECT_SCHEMA,
                EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_PER_EFFECT_SCHEMA_VERSION).commit();
        if (committed) {
            Log.i(TAG, "migrated native refresh preferences to per-effect schema"
                    + " enabled=" + legacyEnabled + " speedTenths=" + legacySpeedTenths);
        } else {
            Log.w(TAG, "native refresh per-effect preference migration failed; will retry");
        }
    }

    static boolean debugLegacyQuickPanelDetection(Context context) {
        return get(context).getBoolean(DEBUG_LEGACY_QUICK_PANEL_DETECTION, false);
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

    static boolean randomUnlockEffectEnabled(Context context) {
        return get(context).getBoolean(UNLOCK_EFFECT_RANDOM_ENABLED, false);
    }

    static synchronized void setRandomUnlockEffectEnabled(Context context, boolean enabled) {
        SharedPreferences preferences = get(context);
        boolean wasEnabled = preferences.getBoolean(UNLOCK_EFFECT_RANDOM_ENABLED, false);
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(UNLOCK_EFFECT_RANDOM_ENABLED, enabled);
        if (enabled && !preferences.contains(UNLOCK_EFFECT_RANDOM_POOL)) {
            editor.putStringSet(UNLOCK_EFFECT_RANDOM_POOL,
                    encodeRandomUnlockEffectPool(defaultRandomUnlockEffectPool(context)));
        }
        if (enabled && !wasEnabled) {
            editor.remove(UNLOCK_EFFECT_RANDOM_CURRENT)
                    .remove(UNLOCK_EFFECT_RANDOM_REMAINING)
                    .putBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, false);
        }
        editor.apply();
    }

    static Set<Integer> randomUnlockEffectPool(Context context) {
        SharedPreferences preferences = get(context);
        if (!preferences.contains(UNLOCK_EFFECT_RANDOM_POOL)) {
            return defaultRandomUnlockEffectPool(context);
        }
        HashSet<Integer> result = new HashSet<Integer>();
        Set<String> encoded;
        try {
            encoded = preferences.getStringSet(
                    UNLOCK_EFFECT_RANDOM_POOL, new HashSet<String>());
        } catch (ClassCastException malformedPreference) {
            encoded = new HashSet<String>();
        }
        if (encoded != null) {
            for (String value : encoded) {
                try {
                    int effect = Integer.parseInt(value);
                    if (isRandomUnlockEffectEligible(context, effect)) {
                        result.add(effect);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore corrupt or future non-numeric members without losing the pool.
                }
            }
        }
        return result;
    }

    static boolean randomUnlockEffectSelected(Context context, int effect) {
        return randomUnlockEffectPool(context).contains(effect);
    }

    static synchronized void setRandomUnlockEffectSelected(Context context, int effect,
            boolean selected) {
        if (!isRandomUnlockEffectEligible(context, effect)) {
            return;
        }
        SharedPreferences preferences = get(context);
        Set<Integer> pool = randomUnlockEffectPool(context);
        if (selected) {
            pool.add(effect);
        } else {
            pool.remove(effect);
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putStringSet(UNLOCK_EFFECT_RANDOM_POOL, encodeRandomUnlockEffectPool(pool))
                .remove(UNLOCK_EFFECT_RANDOM_REMAINING)
                .putBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, false);
        if (!selected
                && preferences.getInt(UNLOCK_EFFECT_RANDOM_CURRENT, -1) == effect) {
            editor.remove(UNLOCK_EFFECT_RANDOM_CURRENT);
        }
        editor.apply();
    }

    static synchronized int currentRandomUnlockEffect(Context context) {
        SharedPreferences preferences = get(context);
        Set<Integer> pool = randomUnlockEffectPool(context);
        if (pool.isEmpty()) {
            preferences.edit()
                    .putInt(UNLOCK_EFFECT_RANDOM_CURRENT, EFFECT_S3_NONE)
                    .remove(UNLOCK_EFFECT_RANDOM_REMAINING)
                    .putBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, true)
                    .apply();
            return EFFECT_S3_NONE;
        }
        if (preferences.getBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, false)) {
            return EFFECT_S3_NONE;
        }
        int current = preferences.getInt(UNLOCK_EFFECT_RANDOM_CURRENT, -1);
        if (pool.contains(current) && isRandomUnlockEffectEligible(context, current)) {
            return current;
        }
        return drawRandomUnlockEffect(context, pool, current);
    }

    static synchronized int advanceRandomUnlockEffect(Context context) {
        SharedPreferences preferences = get(context);
        Set<Integer> pool = randomUnlockEffectPool(context);
        if (pool.isEmpty()) {
            preferences.edit()
                    .putInt(UNLOCK_EFFECT_RANDOM_CURRENT, EFFECT_S3_NONE)
                    .remove(UNLOCK_EFFECT_RANDOM_REMAINING)
                    .putBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, true)
                    .apply();
            return EFFECT_S3_NONE;
        }
        int previous = preferences.getBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, false)
                ? EFFECT_S3_NONE
                : preferences.getInt(UNLOCK_EFFECT_RANDOM_CURRENT, -1);
        preferences.edit()
                .putBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, false)
                .apply();
        return drawRandomUnlockEffect(context, pool, previous);
    }

    static synchronized void useRandomUnlockEffectFallback(Context context) {
        get(context).edit()
                .putInt(UNLOCK_EFFECT_RANDOM_CURRENT, EFFECT_S3_NONE)
                .putBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, true)
                .apply();
    }

    private static int drawRandomUnlockEffect(Context context, Set<Integer> pool,
            int previous) {
        SharedPreferences preferences = get(context);
        Set<Integer> remaining = decodeRandomUnlockEffectSet(
                preferences.getStringSet(
                        UNLOCK_EFFECT_RANDOM_REMAINING, new HashSet<String>()));
        remaining.retainAll(pool);
        remaining.remove(previous);
        if (remaining.isEmpty()) {
            remaining.addAll(pool);
            if (remaining.size() > 1) {
                remaining.remove(previous);
            }
        }
        if (remaining.isEmpty()) {
            preferences.edit()
                    .putInt(UNLOCK_EFFECT_RANDOM_CURRENT, EFFECT_S3_NONE)
                    .putBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, true)
                    .apply();
            return EFFECT_S3_NONE;
        }
        ArrayList<Integer> candidates = new ArrayList<Integer>(remaining);
        int seed = (int) (System.nanoTime() ^ System.currentTimeMillis());
        int next = candidates.get(new Random(seed).nextInt(candidates.size()));
        remaining.remove(next);
        preferences.edit()
                .putInt(UNLOCK_EFFECT_RANDOM_CURRENT, next)
                .putStringSet(UNLOCK_EFFECT_RANDOM_REMAINING,
                        encodeRandomUnlockEffectPool(remaining))
                .putBoolean(UNLOCK_EFFECT_RANDOM_FALLBACK_ACTIVE, false)
                .apply();
        Log.i(TAG, "random effect draw previous=" + previous
                + " next=" + next
                + " remaining=" + remaining.size()
                + " pool=" + pool.size());
        return next;
    }

    private static Set<Integer> decodeRandomUnlockEffectSet(Set<String> encoded) {
        HashSet<Integer> result = new HashSet<Integer>();
        if (encoded == null) {
            return result;
        }
        for (String value : encoded) {
            try {
                result.add(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                // Ignore corrupt or future non-numeric members.
            }
        }
        return result;
    }

    static boolean isRandomUnlockEffectEligible(Context context, int effect) {
        if (!EffectAvailability.isAvailable(context, effect)) {
            return false;
        }
        return !testerNoColormapModeEnabled(context)
                || supportsTesterNoColormapMode(effect);
    }

    /**
     * Conservative first-release pool. These engines either retain several full-screen
     * surfaces/textures, have costly native simulations, or have visibly slow cold starts.
     */
    static boolean isRandomUnlockEffectExcludedForCost(int effect) {
        switch (effect) {
            case EFFECT_S3_RIPPLE_NATIVE:
            case EFFECT_N4_INK_IN_WATER:
            case EFFECT_N5_COLOUR_DROPLET:
            case EFFECT_N5_COLOUR_DROPLET_GYRO:
            case EFFECT_N5_COLOUR_DROPLET_WIP:
            case EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
            case EFFECT_N5_SPARKLING_BUBBLES:
            case EFFECT_N5_SPARKLING_BUBBLES_WIP:
            case EFFECT_S6_WATER_DROPLET:
            case EFFECT_S6_WATER_DROPLET_APP_OWNED:
            case EFFECT_RIPPLE_INK:
            case EFFECT_GOOD_LOCK_POPPING:
            case EFFECT_GOOD_LOCK_RECTANGLE:
            case EFFECT_GOOD_LOCK_BOUNCING:
            case EFFECT_LG_G2_PARTICLE:
            case EFFECT_REVOLVING_GLASS:
                return true;
            default:
                return false;
        }
    }

    private static Set<Integer> defaultRandomUnlockEffectPool(Context context) {
        HashSet<Integer> result = new HashSet<Integer>();
        for (int effect = 0; effect < EFFECT_COUNT; effect++) {
            // Resource-heavy effects remain opt-in so every first inclusion passes through
            // ControlActivity's explicit two-step warning.
            if (isRandomUnlockEffectEligible(context, effect)
                    && !isRandomUnlockEffectExcludedForCost(effect)) {
                result.add(effect);
            }
        }
        return result;
    }

    private static Set<String> encodeRandomUnlockEffectPool(Set<Integer> pool) {
        HashSet<String> encoded = new HashSet<String>();
        if (pool != null) {
            for (Integer effect : pool) {
                if (effect != null) {
                    encoded.add(Integer.toString(effect));
                }
            }
        }
        return encoded;
    }

    static boolean testerNoColormapModeEnabled(Context context) {
        return BuildFlavor.TESTER
                && get(context).getBoolean(TESTER_NO_COLORMAP_MODE, false);
    }

    static boolean supportsTesterNoColormapMode(int effect) {
        return effect == EFFECT_S4_LENS_FLARE
                || effect == EFFECT_S5_POPPING_COLOURS
                || effect == EFFECT_STONE_SKIPPING
                || effect == EFFECT_MASS_TENSION
                || effect == EFFECT_S3_NONE
                || effect == EFFECT_N5_SPARKLING_BUBBLES_WIP
                || isSeasonalUnlockEffect(effect);
    }

    static boolean usesTesterSyntheticColormap(int effect) {
        return effect == EFFECT_S5_POPPING_COLOURS
                || effect == EFFECT_N5_SPARKLING_BUBBLES_WIP;
    }

    static boolean lockSoundEnabled(Context context) {
        return get(context).getBoolean(LOCK_SOUND_ENABLED, true);
    }

    static int rippleInkPalette(Context context) {
        int palette = get(context).getInt(RIPPLE_INK_PALETTE, RIPPLE_INK_PALETTE_DEFAULT);
        return Math.max(RIPPLE_INK_PALETTE_MIN, Math.min(RIPPLE_INK_PALETTE_MAX, palette));
    }

    static boolean abstractTilesLineEnabled(Context context) {
        return get(context).getBoolean(ABSTRACT_TILES_LINE_ENABLED, true);
    }

    static boolean colourDropletGyroEnabled(Context context) {
        SharedPreferences preferences = get(context);
        if (preferences.contains(N5_COLOUR_DROPLET_GYRO_ENABLED)) {
            return preferences.getBoolean(N5_COLOUR_DROPLET_GYRO_ENABLED, false);
        }
        return isColourDropletGyroEffect(preferences.getInt(
                UNLOCK_EFFECT, EFFECT_S4_LENS_FLARE));
    }

    static int unlockEffect(Context context) {
        SharedPreferences preferences = get(context);
        if (preferences.getBoolean(UNLOCK_EFFECT_RANDOM_ENABLED, false)) {
            return currentRandomUnlockEffect(context);
        }
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
            if (!isImplementedEffect(context, effect)) {
                effect = EFFECT_S4_LENS_FLARE;
            }
            preferences.edit().putInt(UNLOCK_EFFECT, effect).apply();
            Log.i(TAG, "migrated unavailable legacy vendor effect "
                    + previousEffect + " -> " + effect);
        }
        if (testerNoColormapModeEnabled(context)
                && !supportsTesterNoColormapMode(effect)) {
            int previousEffect = effect;
            effect = EFFECT_MASS_TENSION;
            preferences.edit().putInt(UNLOCK_EFFECT, effect).apply();
            Log.w(TAG, "no-colormap mode fallback " + previousEffect + " -> " + effect);
        }
        if (!isImplementedEffect(context, effect)) {
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
            case EFFECT_RIPPLE_INK:
                return "N3 Ripple Ink";
            case EFFECT_GOOD_LOCK_POPPING:
                return "Good Lock Popping Color";
            case EFFECT_GOOD_LOCK_RECTANGLE:
                return "Good Lock Rectangle Traveller";
            case EFFECT_GOOD_LOCK_BOUNCING:
                return "Good Lock Bouncing Color";
            case EFFECT_S3_NONE:
                return "S3 None";
            case EFFECT_LG_G2_PIXELATE:
                return "G2 Pixelate";
            case EFFECT_LG_G2_PARTICLE:
                return "G2 Particle";
            case EFFECT_LG_G2_CRYSTAL:
                return "G2 Crystal";
            case EFFECT_XPERIA_Z1_BLINDS:
                return "Xperia Z1 Blinds";
            case EFFECT_REVOLVING_GLASS:
                return "Revolving Glass";
            case EFFECT_LG_G1_WHITE_HOLE:
                return "G1 White Hole";
            case EFFECT_LG_SODA:
                return "G2 Soda";
            case EFFECT_LG_G1_DEWDROP:
                return "G1 Dewdrop";
            case EFFECT_LG_G2_LIGHT_PARTICLE:
                return "G2 Light Particle";
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

    static boolean isColourDropletGyroEffect(int effect) {
        return effect == EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == EFFECT_N5_COLOUR_DROPLET_GYRO_WIP;
    }

    /** Effects with an ARM64 renderer that can opt into display-refresh timing. */
    static boolean supportsExperimentalNativeRefreshPhysics(int effect) {
        return effect == EFFECT_S6_WATER_DROPLET_APP_OWNED
                || effect == EFFECT_N5_SPARKLING_BUBBLES_WIP
                || effect == EFFECT_N5_COLOUR_DROPLET_WIP
                || effect == EFFECT_N5_COLOUR_DROPLET_GYRO_WIP
                || effect == EFFECT_S5_POPPING_COLOURS
                || effect == EFFECT_S4_ABSTRACT_TILES
                || effect == EFFECT_S4_GEOMETRIC_MOSAIC
                || effect == EFFECT_S3_RIPPLE_NATIVE
                || effect == EFFECT_RIPPLE_INK
                || effect == EFFECT_WATERCOLOUR
                || effect == EFFECT_BRILLIANT_RING
                || effect == EFFECT_BRILLIANT_CUT
                || effect == EFFECT_GOOD_LOCK_POPPING
                || effect == EFFECT_GOOD_LOCK_RECTANGLE
                || effect == EFFECT_GOOD_LOCK_BOUNCING;
    }

    /** Only these renderers consume the native-refresh motion-speed preference. */
    static boolean supportsExperimentalNativeRefreshPhysicsSpeed(int effect) {
        return effect == EFFECT_S6_WATER_DROPLET_APP_OWNED
                || effect == EFFECT_N5_SPARKLING_BUBBLES_WIP
                || effect == EFFECT_N5_COLOUR_DROPLET_WIP
                || effect == EFFECT_N5_COLOUR_DROPLET_GYRO_WIP
                || effect == EFFECT_S5_POPPING_COLOURS
                || effect == EFFECT_GOOD_LOCK_POPPING
                || effect == EFFECT_GOOD_LOCK_RECTANGLE
                || effect == EFFECT_GOOD_LOCK_BOUNCING;
    }

    /** Dynamic boolean key for one ARM64 renderer's native-refresh toggle. */
    static String experimentalNativeRefreshPhysicsKey(int effect) {
        requireExperimentalNativeRefreshPhysicsEffect(effect);
        return EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_EFFECT_PREFIX + effect;
    }

    /** Dynamic speed key for a renderer whose native-refresh simulation has speed control. */
    static String experimentalNativeRefreshPhysicsSpeedTenthsKey(int effect) {
        if (!supportsExperimentalNativeRefreshPhysicsSpeed(effect)) {
            throw new IllegalArgumentException("Native-refresh speed unsupported effect=" + effect);
        }
        return EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_EFFECT_PREFIX + effect;
    }

    static boolean experimentalNativeRefreshPhysicsEnabled(Context context, int effect) {
        return supportsExperimentalNativeRefreshPhysics(effect)
                && get(context).getBoolean(experimentalNativeRefreshPhysicsKey(effect), true);
    }

    static int experimentalNativeRefreshPhysicsSpeedTenths(Context context, int effect) {
        if (!supportsExperimentalNativeRefreshPhysicsSpeed(effect)) {
            return NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_DEFAULT;
        }
        int tenths = get(context).getInt(experimentalNativeRefreshPhysicsSpeedTenthsKey(effect),
                NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_DEFAULT);
        return Math.max(NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_MIN,
                Math.min(NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_MAX, tenths));
    }

    static float experimentalNativeRefreshPhysicsSpeedMultiplier(Context context, int effect) {
        return experimentalNativeRefreshPhysicsEnabled(context, effect)
                && supportsExperimentalNativeRefreshPhysicsSpeed(effect)
                ? experimentalNativeRefreshPhysicsSpeedTenths(context, effect) / 10.0f
                : 1.0f;
    }

    /** Returns the supported effect encoded by an exact dynamic preference key, or -1. */
    static int experimentalNativeRefreshPhysicsEffectFromPreferenceKey(String key) {
        int effect = effectFromExactPreferenceKey(
                key, EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_EFFECT_PREFIX);
        if (effect >= 0 && supportsExperimentalNativeRefreshPhysics(effect)) {
            return effect;
        }
        effect = effectFromExactPreferenceKey(
                key, EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_EFFECT_PREFIX);
        return effect >= 0 && supportsExperimentalNativeRefreshPhysicsSpeed(effect) ? effect : -1;
    }

    static boolean isExperimentalNativeRefreshPhysicsPreferenceKey(String key) {
        return experimentalNativeRefreshPhysicsEffectFromPreferenceKey(key) >= 0;
    }

    static boolean isExperimentalNativeRefreshPhysicsSpeedTenthsPreferenceKey(String key) {
        int effect = effectFromExactPreferenceKey(
                key, EXPERIMENTAL_NATIVE_REFRESH_PHYSICS_SPEED_TENTHS_EFFECT_PREFIX);
        return effect >= 0 && supportsExperimentalNativeRefreshPhysicsSpeed(effect);
    }

    private static void requireExperimentalNativeRefreshPhysicsEffect(int effect) {
        if (!supportsExperimentalNativeRefreshPhysics(effect)) {
            throw new IllegalArgumentException("Native-refresh unsupported effect=" + effect);
        }
    }

    private static int effectFromExactPreferenceKey(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) {
            return -1;
        }
        String encodedEffect = key.substring(prefix.length());
        if (encodedEffect.length() == 0) {
            return -1;
        }
        try {
            int effect = Integer.parseInt(encodedEffect);
            return Integer.toString(effect).equals(encodedEffect) ? effect : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static boolean isImplementedEffect(int effect) {
        return EffectAvailability.isAvailable(effect);
    }

    static boolean lensFlareGlesRendererEnabled(Context context) {
        // The GLSurfaceView port proved unreliable across repeated keyguard attach/detach
        // cycles. Lens Flare is Canvas/HWUI-only now, regardless of a stale saved tester value.
        return false;
    }

    static String lensFlareMode(Context context) {
        return normalizeLensFlareMode(
                get(context).getString(LENS_FLARE_MODE, LENS_FLARE_MODE_FLARE));
    }

    static String normalizeLensFlareMode(String mode) {
        if (LENS_FLARE_MODE_BLUE_RING.equals(mode) || "blue_ring".equals(mode)) {
            return LENS_FLARE_MODE_BLUE_RING;
        }
        if (LENS_FLARE_MODE_BLOOD.equals(mode)) {
            return LENS_FLARE_MODE_BLOOD;
        }
        if (LENS_FLARE_MODE_LIGHTNING.equals(mode)) {
            return LENS_FLARE_MODE_LIGHTNING;
        }
        return LENS_FLARE_MODE_FLARE;
    }

    static String lensFlareAssetPrefix(Context context) {
        return "keyguard_" + lensFlareMode(context) + "_";
    }

    static boolean isImplementedEffect(Context context, int effect) {
        return EffectAvailability.isAvailable(context, effect);
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
        return FoldDisplayTarget.touchBoxProfileForContext(context);
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
                touchBoxKey(TOUCH_BOX_LEFT, profile), touchBoxConfigured(context, profile)
                        ? DEFAULT_TOUCH_BOX_LEFT : SAFE_TOUCH_BOX_LEFT));
    }

    static int touchBoxTop(Context context) {
        return touchBoxTop(context, touchBoxProfile(context));
    }

    static int touchBoxTop(Context context, String profile) {
        return roundTouchCoordinate(get(context).getInt(
                touchBoxKey(TOUCH_BOX_TOP, profile), touchBoxConfigured(context, profile)
                        ? DEFAULT_TOUCH_BOX_TOP : SAFE_TOUCH_BOX_TOP));
    }

    static int touchBoxRight(Context context) {
        return touchBoxRight(context, touchBoxProfile(context));
    }

    static int touchBoxRight(Context context, String profile) {
        return roundTouchCoordinate(get(context).getInt(
                touchBoxKey(TOUCH_BOX_RIGHT, profile), touchBoxConfigured(context, profile)
                        ? DEFAULT_TOUCH_BOX_RIGHT : SAFE_TOUCH_BOX_RIGHT));
    }

    static int touchBoxBottom(Context context) {
        return touchBoxBottom(context, touchBoxProfile(context));
    }

    static int touchBoxBottom(Context context, String profile) {
        return roundTouchCoordinate(get(context).getInt(
                touchBoxKey(TOUCH_BOX_BOTTOM, profile), touchBoxConfigured(context, profile)
                        ? DEFAULT_TOUCH_BOX_BOTTOM : SAFE_TOUCH_BOX_BOTTOM));
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
            String legacyProfile = (FoldDisplayTarget.PROFILE_TABLET_PORTRAIT.equals(normalized)
                    || FoldDisplayTarget.PROFILE_TABLET_LANDSCAPE.equals(normalized))
                    ? FoldDisplayTarget.tabletProfileForSize(referenceWidth, referenceHeight)
                    : FoldDisplayTarget.profileForSize(referenceWidth, referenceHeight);
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
        return new File(context.getFilesDir(),
                "unlock_effect_background" + suffix + ".argb8888");
    }

    static File lgPreLockUnderlayFile(Context context, String profile, int displayId,
            int width, int height) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile)
                .replaceAll("[^A-Za-z0-9_-]", "_");
        return new File(context.getFilesDir(),
                "lg_prelock_underlay_" + normalized
                        + "_d" + Math.max(0, displayId)
                        + "_" + Math.max(1, width) + "x" + Math.max(1, height)
                        + ".argb8888");
    }

    static boolean usesLgPreLockUnderlay(int effect) {
        return effect == EFFECT_LG_G2_PARTICLE
                || effect == EFFECT_LG_G1_WHITE_HOLE
                || effect == EFFECT_LG_SODA
                || effect == EFFECT_LG_G1_DEWDROP
                || effect == EFFECT_LG_G2_LIGHT_PARTICLE;
    }

    static boolean usesLgPreLockUnderlayAsSecondary(int effect) {
        return effect == EFFECT_LG_G2_PIXELATE || effect == EFFECT_REVOLVING_GLASS;
    }

    static boolean needsLgPreLockUnderlay(int effect) {
        return usesLgPreLockUnderlay(effect) || usesLgPreLockUnderlayAsSecondary(effect);
    }

    static int markLgPreLockUnderlayCaptured(Context context, String profile, int displayId,
            int width, int height, long timestamp) {
        return markLgPreLockUnderlay(
                context, profile, displayId, width, height, timestamp,
                LG_PRELOCK_UNDERLAY_ORIGIN_LAST_SCREEN);
    }

    static int markLgPreLockUnderlayFallback(Context context, String profile, int displayId,
            int width, int height, long timestamp) {
        return markLgPreLockUnderlay(
                context, profile, displayId, width, height, timestamp,
                LG_PRELOCK_UNDERLAY_ORIGIN_WALLPAPER_FALLBACK);
    }

    static long lgPreLockUnderlayCapturedAt(Context context, String profile, int displayId,
            int width, int height) {
        return get(context).getLong(lgPreLockUnderlayMetadataKey(
                profile, displayId, width, height) + "_captured_at", 0L);
    }

    static String lgPreLockUnderlayOrigin(Context context, String profile, int displayId,
            int width, int height) {
        return get(context).getString(lgPreLockUnderlayMetadataKey(
                        profile, displayId, width, height) + "_origin",
                LG_PRELOCK_UNDERLAY_ORIGIN_LAST_SCREEN);
    }

    private static int markLgPreLockUnderlay(Context context, String profile, int displayId,
            int width, int height, long timestamp, String origin) {
        String key = lgPreLockUnderlayMetadataKey(profile, displayId, width, height);
        SharedPreferences prefs = get(context);
        int generation = Math.max(0, prefs.getInt(key + "_generation", 0)) + 1;
        prefs.edit()
                .putInt(key + "_generation", generation)
                .putLong(key + "_captured_at", Math.max(0L, timestamp))
                .putString(key + "_origin", origin)
                .apply();
        return generation;
    }

    private static String lgPreLockUnderlayMetadataKey(String profile, int displayId,
            int width, int height) {
        return LG_PRELOCK_UNDERLAY_METADATA_PREFIX
                + FoldDisplayTarget.normalizeProfile(profile)
                + "_d" + Math.max(0, displayId)
                + "_" + Math.max(1, width) + "x" + Math.max(1, height);
    }

    static File legacyPngEffectBackgroundFile(Context context, String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        String suffix = FoldDisplayTarget.PROFILE_SINGLE.equals(normalized)
                ? "" : "_" + normalized;
        return new File(context.getFilesDir(), "unlock_effect_background" + suffix + ".png");
    }

    static File legacyEffectBackgroundFile(Context context, int effect) {
        return new File(context.getFilesDir(), "unlock_effect_background_" + effect + ".png");
    }
}

