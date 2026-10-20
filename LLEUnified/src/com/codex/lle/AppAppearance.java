package com.codex.lle;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

/** Shared palette and framework theme for app UI; never applied to effect overlays. */
final class AppAppearance {
    static final String PREF_MODE = "ui_theme_mode";
    private static final Palette LIGHT = new Palette(false);
    private static final Palette DARK = new Palette(true);

    private AppAppearance() {}

    static int mode(Context context) {
        try {
            return AppearanceMode.normalize(OverlayPrefs.get(context.getApplicationContext())
                    .getInt(PREF_MODE, AppearanceMode.SYSTEM));
        } catch (ClassCastException invalidPreference) {
            return AppearanceMode.SYSTEM;
        }
    }

    static void setMode(Context context, int mode) {
        OverlayPrefs.get(context.getApplicationContext()).edit()
                .putInt(PREF_MODE, AppearanceMode.normalize(mode)).apply();
    }

    static boolean isDark(Context context) {
        // Read the application resources: an activity's explicit override must not
        // become the system setting when the user switches back to System.
        int uiMode = context.getApplicationContext().getResources().getConfiguration().uiMode;
        return AppearanceMode.isDark(mode(context),
                (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }

    static Palette palette(Context context) {
        return isDark(context) ? DARK : LIGHT;
    }

    static int color(Context context, int light, int dark) {
        return isDark(context) ? dark : light;
    }

    /** Call before super.onCreate and before reading the activity resources. */
    static void prepare(Activity activity) {
        boolean dark = isDark(activity);
        // System remains managed by Android, including normal configuration recreation.
        if (mode(activity) != AppearanceMode.SYSTEM) {
            Configuration override = new Configuration();
            int systemUiMode = activity.getApplicationContext().getResources()
                    .getConfiguration().uiMode;
            override.uiMode = (systemUiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            activity.applyOverrideConfiguration(override);
        }
        activity.setTheme(dark ? R.style.AppThemeDark : R.style.AppThemeLight);
    }

    static void configureWindow(Activity activity) {
        Palette colors = palette(activity);
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(colors.background);
        int flags = window.getDecorView().getSystemUiVisibility();
        flags = colors.dark ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                : flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            flags = colors.dark ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    static final class Palette {
        final boolean dark;
        final int background, surface, surfaceRaised, text, muted, divider;
        final int accent, accentDeep, accentSoft, actionFill, buttonText;

        private Palette(boolean dark) {
            this.dark = dark;
            background = dark ? 0xff0e151d : 0xffeef6fb;
            surface = dark ? 0xff18232e : Color.WHITE;
            surfaceRaised = dark ? 0xff21313e : 0xfff8fbfc;
            text = dark ? 0xffedf4f7 : 0xff212121;
            muted = dark ? 0xffa8bac7 : 0xff757575;
            divider = dark ? 0xff354957 : 0xffe6e6e6;
            accent = dark ? 0xff6cd5d3 : 0xff4098a0;
            accentDeep = dark ? 0xff80ddd8 : 0xff007983;
            accentSoft = dark ? 0xff243e44 : 0xffe7f7f8;
            actionFill = accentDeep;
            buttonText = dark ? 0xff082b30 : Color.WHITE;
        }
    }
}
