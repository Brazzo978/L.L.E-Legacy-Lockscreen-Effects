package com.codex.lle;

/** UI-only selection, independent of lockscreen and effect rendering. */
final class AppearanceMode {
    static final int SYSTEM = 0;
    static final int LIGHT = 1;
    static final int DARK = 2;

    private AppearanceMode() {}

    static int normalize(int mode) {
        return mode == LIGHT || mode == DARK ? mode : SYSTEM;
    }

    static boolean isDark(int mode, boolean systemDark) {
        mode = normalize(mode);
        return mode == DARK || (mode == SYSTEM && systemDark);
    }

    static String label(int mode) {
        switch (normalize(mode)) {
            case LIGHT: return "Light";
            case DARK: return "Dark";
            default: return "Auto";
        }
    }
}
