package com.codex.lle;

import java.lang.reflect.Constructor;

/** Host checks for readable theme roles and forced-mode precedence. */
public final class AppearanceContrastTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Constructor<AppAppearance.Palette> constructor =
                AppAppearance.Palette.class.getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        AppAppearance.Palette dark = constructor.newInstance(true);
        AppAppearance.Palette light = constructor.newInstance(false);
        // Validate actual palette values; no Android runtime calls are involved.
        for (int background : new int[] {dark.background, dark.surface, dark.surfaceRaised,
                dark.accentSoft}) {
            readable(dark.text, background, "dark primary text", 4.5);
            readable(dark.muted, background, "dark secondary text", 4.5);
            readable(dark.accentDeep, background, "dark accent label", 4.5);
        }
        readable(dark.buttonText, dark.accent, "dark primary action", 4.5);
        readable(dark.buttonText, dark.accentDeep, "dark selected control", 4.5);
        readable(light.text, light.surface, "light primary text", 4.5);
        readable(light.muted, light.surface, "light secondary text", 4.5);
        readable(light.accentDeep, light.surface, "light accent label", 4.5);
        readable(light.buttonText, light.actionFill, "light primary action", 4.5);
        readable(dark.buttonText, dark.actionFill, "dark primary action fill", 4.5);
        for (boolean systemDark : new boolean[] {false, true}) {
            require(AppearanceMode.isDark(AppearanceMode.SYSTEM, systemDark) == systemDark,
                    "System must follow both system configurations");
            require(!AppearanceMode.isDark(AppearanceMode.LIGHT, systemDark),
                    "Light must override a dark device");
            require(AppearanceMode.isDark(AppearanceMode.DARK, systemDark),
                    "Dark must override a light device");
            for (int invalid : new int[] {-1, 3, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                require(AppearanceMode.isDark(invalid, systemDark) == systemDark,
                        "Unknown saved modes must fall back to System");
            }
        }
        System.out.println("AppearanceContrastTest passed: " + checks + " checks");
    }

    private static void readable(int foreground, int background, String role, double minimum) {
        double first = luminance(foreground), second = luminance(background);
        double contrast = (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
        require(contrast >= minimum, role + " contrast " + contrast + " < " + minimum);
    }

    private static double luminance(int color) {
        return .2126 * linear((color >> 16) & 255)
                + .7152 * linear((color >> 8) & 255) + .0722 * linear(color & 255);
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
