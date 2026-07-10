package com.codex.s4unlockfx;

final class UnlockFxPrefs {
    static final String NAME = "unlock_fx";
    static final String MODE_INDEX = "mode_index";
    static final String MODE_NAME = "mode_name";
    static final String WALLPAPER_MODE = "wallpaper_mode";
    static final String CUSTOM_WALLPAPER_URI = "custom_wallpaper_uri";
    static final String STOCK_WALLPAPER_INDEX = "stock_wallpaper_index";
    static final String WALLPAPER_CHOICE = "wallpaper_choice";
    static final int WALLPAPER_MODE_AUTO = 0;
    static final int WALLPAPER_MODE_CUSTOM = 1;
    static final int WALLPAPER_MODE_STOCK = 2;
    static final int WALLPAPER_AUTO = 0;
    static final int WALLPAPER_S3 = 1;
    static final int WALLPAPER_S4_S5 = 2;
    static final int WALLPAPER_NOTE4 = 3;
    static final int WALLPAPER_NOTE5 = 4;
    static final int WALLPAPER_DEBUG = 5;
    static final String[] MODE_NAMES = new String[] {
            "Lens flare S4",
            "S3 ripple original",
            "Popping colours / Particle space",
            "Blind",
            "Watercolor native",
            "Ink in water / Ripple ink",
            "Indigo diffusion",
            "Abstract tiles native",
            "Geometric mosaic native",
            "Brilliant cut native",
            "Brilliant ring native",
            "Coloured droplets",
            "Sparkling bubbles",
            "Note4 seasonal particles unlock",
            "Note4 Coloured paper festival",
            "Note4 charge doodle spring",
            "Note4 charge doodle summer",
            "Note4 charge doodle autumn",
            "Note4 charge doodle winter"
    };
    static final String[] WALLPAPER_NAMES = new String[] {
            "Auto per effetto",
            "Galaxy S3 stock",
            "Galaxy S4 / S5 stock",
            "Galaxy Note 4 era",
            "Galaxy Note 5 stock",
            "Debug earth"
    };
    static final String[] STOCK_WALLPAPER_NAMES = new String[] {
            "Galaxy S3 default",
            "Galaxy S3 keyguard",
            "Galaxy S4 default",
            "Galaxy S4 aeroplane wing",
            "Galaxy S4 balloon",
            "Galaxy S4 balloon 2",
            "Galaxy S4 balloons",
            "Galaxy S4 balloons boy",
            "Galaxy S4 balloons girl",
            "Galaxy S4 blue sky",
            "Galaxy S4 blue sky 2",
            "Galaxy S4 blue sky 3",
            "Galaxy S4 boat",
            "Galaxy S4 dandelion",
            "Galaxy S4 drops",
            "Galaxy S4 flower",
            "Galaxy S4 geometric",
            "Galaxy S4 iris",
            "Galaxy S4 leaf drops",
            "Galaxy S4 leaves",
            "Galaxy S4 leaves 2",
            "Galaxy S4 easy mode",
            "Galaxy S4 lockscreen",
            "Galaxy S4 lockscreen text",
            "Galaxy S4 stock wallpaper",
            "Galaxy S4 Samsung Galaxy S4",
            "Galaxy S4 spotlight",
            "Galaxy S5 keyguard",
            "Galaxy Note 4 default",
            "Galaxy Note 5 default",
            "Galaxy Note 5 essential 1",
            "Galaxy Note 5 essential 2",
            "Galaxy Note 5 essential 3",
            "Galaxy Note 5 essential 4",
            "Galaxy Note 5 essential 5",
            "Galaxy Note 5 essential 6"
    };
    static final String[] STOCK_WALLPAPER_RESOURCES = new String[] {
            "s3_default_wallpaper",
            "s3_keyguard_default_wallpaper",
            "s4_default_wallpaper",
            "s4_wp_aeroplane_wing",
            "s4_wp_balloon",
            "s4_wp_balloon2",
            "s4_wp_balloons",
            "s4_wp_balloons_boy",
            "s4_wp_balloons_girl",
            "s4_wp_bluesky",
            "s4_wp_bluesky2",
            "s4_wp_bluesky3",
            "s4_wp_boat",
            "s4_wp_dandelion",
            "s4_wp_drops",
            "s4_wp_flower",
            "s4_wp_geometric",
            "s4_wp_iris",
            "s4_wp_leaf_drops",
            "s4_wp_leaves",
            "s4_wp_leaves2",
            "s4_wp_s4_easy_mode_wallpaper",
            "s4_wp_s4_lockscreen",
            "s4_wp_s4_lockscreen_with_text",
            "s4_wp_s4_stock_wallpaper",
            "s4_wp_samsung_galaxy_s4",
            "s4_wp_spotlight",
            "s5_keyguard_default_wallpaper",
            "note4_default_wallpaper",
            "note5_default_wallpaper",
            "note5_essential_wallpaper_1",
            "note5_essential_wallpaper_2",
            "note5_essential_wallpaper_3",
            "note5_essential_wallpaper_4",
            "note5_essential_wallpaper_5",
            "note5_essential_wallpaper_6"
    };

    private UnlockFxPrefs() {
    }

    static int canvasEffectForModeIndex(int modeIndex) {
        switch (normalizeModeIndex(modeIndex)) {
            case 13:
                return LegacyCanvasEffectView.EFFECT_NOTE4_SEASONAL_UNLOCK;
            case 14:
                return LegacyCanvasEffectView.EFFECT_NOTE4_COLORED_PAPER;
            case 15:
                return LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_SPRING;
            case 16:
                return LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_SUMMER;
            case 17:
                return LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_AUTUMN;
            case 18:
                return LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_WINTER;
            default:
                return LegacyCanvasEffectView.EFFECT_NONE;
        }
    }

    static int normalizeModeIndex(int modeIndex) {
        int count = MODE_NAMES.length;
        return ((modeIndex % count) + count) % count;
    }

    static int normalizeWallpaperChoice(int wallpaperChoice) {
        int count = WALLPAPER_NAMES.length;
        return ((wallpaperChoice % count) + count) % count;
    }

    static int normalizeStockWallpaperIndex(int stockWallpaperIndex) {
        int count = STOCK_WALLPAPER_NAMES.length;
        return ((stockWallpaperIndex % count) + count) % count;
    }

    static String modeName(int modeIndex) {
        return MODE_NAMES[normalizeModeIndex(modeIndex)];
    }

    static String wallpaperName(int wallpaperChoice) {
        return WALLPAPER_NAMES[normalizeWallpaperChoice(wallpaperChoice)];
    }

    static String stockWallpaperName(int stockWallpaperIndex) {
        return STOCK_WALLPAPER_NAMES[normalizeStockWallpaperIndex(stockWallpaperIndex)];
    }

    static String stockWallpaperResourceName(int stockWallpaperIndex) {
        return STOCK_WALLPAPER_RESOURCES[normalizeStockWallpaperIndex(stockWallpaperIndex)];
    }

    static String resolvedWallpaperName(int modeIndex, int wallpaperChoice) {
        int normalized = normalizeWallpaperChoice(wallpaperChoice);
        if (normalized == WALLPAPER_AUTO) {
            return wallpaperName(defaultWallpaperChoiceForModeIndex(modeIndex));
        }
        return wallpaperName(normalized);
    }

    static int resolvedWallpaperChoice(int modeIndex, int wallpaperChoice) {
        int normalized = normalizeWallpaperChoice(wallpaperChoice);
        return normalized == WALLPAPER_AUTO ? defaultWallpaperChoiceForModeIndex(modeIndex) : normalized;
    }

    static String wallpaperResourceName(int modeIndex, int wallpaperChoice) {
        switch (resolvedWallpaperChoice(modeIndex, wallpaperChoice)) {
            case WALLPAPER_S3:
                return "s3_keyguard_default_wallpaper";
            case WALLPAPER_NOTE5:
                return "note5_default_wallpaper";
            case WALLPAPER_DEBUG:
                return "earth_banner";
            case WALLPAPER_NOTE4:
                return "note4_default_wallpaper";
            case WALLPAPER_S4_S5:
            default:
                return "keyguard_default_wallpaper";
        }
    }

    static String defaultWallpaperResourceNameForModeIndex(int modeIndex) {
        switch (normalizeModeIndex(modeIndex)) {
            case 0:
                return "s4_default_wallpaper";
            case 1:
                return "s3_keyguard_default_wallpaper";
            case 4:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
                return "note4_default_wallpaper";
            case 11:
            case 12:
                return "note5_default_wallpaper";
            case 2:
            case 3:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            default:
                return "s5_keyguard_default_wallpaper";
        }
    }

    static String defaultWallpaperNameForModeIndex(int modeIndex) {
        switch (normalizeModeIndex(modeIndex)) {
            case 0:
                return "Galaxy S4 default";
            case 1:
                return "Galaxy S3 keyguard";
            case 4:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
                return "Galaxy Note 4 default";
            case 11:
            case 12:
                return "Galaxy Note 5 default";
            case 2:
            case 3:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            default:
                return "Galaxy S5 keyguard";
        }
    }

    static int defaultWallpaperChoiceForModeIndex(int modeIndex) {
        switch (normalizeModeIndex(modeIndex)) {
            case 1:
                return WALLPAPER_S3;
            case 4:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
                return WALLPAPER_NOTE4;
            case 11:
            case 12:
                return WALLPAPER_NOTE5;
            case 0:
            case 2:
            case 3:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            default:
                return WALLPAPER_S4_S5;
        }
    }

    static String modelNameForModeIndex(int modeIndex) {
        switch (normalizeModeIndex(modeIndex)) {
            case 0:
                return "Galaxy S4";
            case 1:
                return "Galaxy S3";
            case 2:
                return "Galaxy S5 / S4 Particle";
            case 4:
                return "Galaxy Note 4";
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
                return "Galaxy Note 4 / Festival";
            case 11:
            case 12:
                return "Galaxy Note 5";
            case 3:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            default:
                return "Galaxy S5 era";
        }
    }

    static String modeDetail(int modeIndex) {
        return modelNameForModeIndex(modeIndex) + " - " + modeName(modeIndex);
    }
}
