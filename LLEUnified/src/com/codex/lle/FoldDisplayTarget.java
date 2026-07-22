package com.codex.lle;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.Display;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * Resolves the physical panel that currently owns the focused Android UI.
 *
 * Samsung Fold devices expose the cover and inner panels as distinct logical displays
 * (for example 0=1080x2520 and 1=1968x2184 on the Fold7).  Display.DEFAULT_DISPLAY is
 * therefore not a safe proxy for the panel the user is looking at.
 */
final class FoldDisplayTarget {
    private static final String DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED =
            "android.hardware.display.category.ALL_INCLUDING_DISABLED";
    static final String PROFILE_SINGLE = "single";
    static final String PROFILE_COVER = "cover";
    static final String PROFILE_MAIN = "main";
    private static volatile int cachedFoldDevice = -1;

    final Display display;
    final int displayId;
    final int width;
    final int height;
    final String cacheProfile;
    final boolean multiPanel;

    private FoldDisplayTarget(Display display, int width, int height, String cacheProfile,
            boolean multiPanel) {
        this.display = display;
        this.displayId = display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.cacheProfile = normalizeProfile(cacheProfile);
        this.multiPanel = multiPanel;
    }

    static FoldDisplayTarget resolve(AccessibilityService service, DisplayManager manager,
            int previousDisplayId) {
        Display[] displays = manager == null ? new Display[0] : manager.getDisplays();
        Display defaultDisplay = manager == null
                ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
        String builtInName = defaultDisplay == null ? null : defaultDisplay.getName();
        int internalCount = 0;
        int activeInternalCount = 0;
        Display soleActiveInternalDisplay = null;
        for (Display display : displays) {
            if (isBuiltInPanel(display, builtInName)) {
                internalCount++;
                int state = display.getState();
                if (state == Display.STATE_ON || state == Display.STATE_DOZE
                        || state == Display.STATE_DOZE_SUSPEND) {
                    activeInternalCount++;
                    soleActiveInternalDisplay = display;
                }
            }
        }

        int focusedDisplayId = activeInternalCount == 1
                ? soleActiveInternalDisplay.getDisplayId()
                : focusedDisplayId(service);
        Display best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Display display : displays) {
            if (!isBuiltInPanel(display, builtInName)) {
                continue;
            }
            int score = displayScore(display, focusedDisplayId, previousDisplayId);
            if (best == null || score > bestScore) {
                best = display;
                bestScore = score;
            }
        }
        if (best == null && manager != null) {
            best = manager.getDisplay(Display.DEFAULT_DISPLAY);
        }
        if (best == null && displays.length > 0) {
            best = displays[0];
        }

        int[] size = realSize(best);
        boolean hingeFold = false;
        try {
            hingeFold = service != null && service.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_SENSOR_HINGE_ANGLE);
        } catch (Throwable ignored) {
        }
        boolean multiPanel = (internalCount > 1 || hingeFold)
                && OverlayPrefs.foldModeEnabled(service);
        String profile = multiPanel ? profileForSize(size[0], size[1]) : PROFILE_SINGLE;
        return new FoldDisplayTarget(best, size[0], size[1], profile, multiPanel);
    }

    static boolean bitmapMatches(String profile, int bitmapWidth, int bitmapHeight,
            int targetWidth, int targetHeight) {
        if (bitmapWidth < 100 || bitmapHeight < 100) {
            return false;
        }
        if (PROFILE_SINGLE.equals(normalizeProfile(profile))) {
            return true;
        }
        boolean bitmapLandscape = bitmapWidth > bitmapHeight;
        boolean targetLandscape = targetWidth > targetHeight;
        if (bitmapLandscape != targetLandscape) {
            return false;
        }
        float bitmapRatio = Math.max(bitmapWidth, bitmapHeight)
                / (float) Math.min(bitmapWidth, bitmapHeight);
        float targetRatio = Math.max(targetWidth, targetHeight)
                / (float) Math.min(targetWidth, targetHeight);
        return Math.abs(bitmapRatio - targetRatio) <= 0.08f;
    }

    static String normalizeProfile(String profile) {
        if (PROFILE_COVER.equals(profile) || PROFILE_MAIN.equals(profile)) {
            return profile;
        }
        return PROFILE_SINGLE;
    }

    static String cacheProfileForContext(Context context) {
        if (context == null || !OverlayPrefs.foldModeEnabled(context)) {
            return PROFILE_SINGLE;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return profileForSize(metrics.widthPixels, metrics.heightPixels);
    }

    static int[] displaySizeForProfile(Context context, String requestedProfile) {
        String profile = normalizeProfile(requestedProfile);
        DisplayMetrics active = context == null
                ? new DisplayMetrics() : context.getResources().getDisplayMetrics();
        int fallbackWidth = Math.max(1, active.widthPixels);
        int fallbackHeight = Math.max(1, active.heightPixels);
        int[] fallback = new int[] {Math.min(fallbackWidth, fallbackHeight),
                Math.max(fallbackWidth, fallbackHeight)};
        if (context == null || PROFILE_SINGLE.equals(profile)) {
            return fallback;
        }
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(
                    Context.DISPLAY_SERVICE);
            Display defaultDisplay = manager == null
                    ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
            String builtInName = defaultDisplay == null ? null : defaultDisplay.getName();
            Display[] displays = manager == null ? new Display[0]
                    : manager.getDisplays(DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED);
            if (displays == null || displays.length == 0) {
                displays = manager == null ? new Display[0] : manager.getDisplays();
            }
            for (Display display : displays) {
                if (!isBuiltInPanel(display, builtInName)) {
                    continue;
                }
                int[] size = realSize(display);
                if (profile.equals(profileForSize(size[0], size[1]))) {
                    return new int[] {Math.min(size[0], size[1]),
                            Math.max(size[0], size[1])};
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    static boolean isFoldDevice(Context context) {
        if (context == null) {
            return false;
        }
        int cached = cachedFoldDevice;
        if (cached >= 0) {
            return cached == 1;
        }
        boolean detected = false;
        try {
            if (context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_SENSOR_HINGE_ANGLE)) {
                detected = true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (detected) {
                cachedFoldDevice = 1;
                return true;
            }
            DisplayManager manager = (DisplayManager) context.getSystemService(
                    Context.DISPLAY_SERVICE);
            Display defaultDisplay = manager == null
                    ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
            String builtInName = defaultDisplay == null ? null : defaultDisplay.getName();
            int internalCount = 0;
            if (manager != null) {
                for (Display display : manager.getDisplays()) {
                    if (isBuiltInPanel(display, builtInName)) {
                        internalCount++;
                    }
                }
            }
            detected = internalCount > 1;
        } catch (Throwable ignored) {
        }
        cachedFoldDevice = detected ? 1 : 0;
        return detected;
    }

    private static int focusedDisplayId(AccessibilityService service) {
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Display.INVALID_DISPLAY;
        }
        try {
            SparseArray<List<AccessibilityWindowInfo>> windows =
                    service.getWindowsOnAllDisplays();
            int activeCandidate = Display.INVALID_DISPLAY;
            for (int i = 0; i < windows.size(); i++) {
                int displayId = windows.keyAt(i);
                List<AccessibilityWindowInfo> displayWindows = windows.valueAt(i);
                if (displayWindows == null) {
                    continue;
                }
                for (AccessibilityWindowInfo window : displayWindows) {
                    if (window == null) {
                        continue;
                    }
                    if (window.isFocused()) {
                        return displayId;
                    }
                    if (window.isActive()) {
                        activeCandidate = displayId;
                    }
                }
            }
            return activeCandidate;
        } catch (Throwable ignored) {
            return Display.INVALID_DISPLAY;
        }
    }

    private static int displayScore(Display display, int focusedDisplayId,
            int previousDisplayId) {
        int score = 0;
        int state = display.getState();
        if (state == Display.STATE_ON) {
            score += 1000;
        } else if (state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND) {
            score += 500;
        } else if (state == Display.STATE_OFF) {
            score -= 1000;
        }
        if (display.getDisplayId() == focusedDisplayId) {
            score += 10000;
        }
        if (display.getDisplayId() == previousDisplayId) {
            score += 25;
        }
        return score;
    }

    private static boolean isBuiltInPanel(Display display, String builtInName) {
        if (display == null) {
            return false;
        }
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            return true;
        }
        // Android's public Display API does not expose the INTERNAL type. Fold panels
        // do share the localized built-in display name, while HDMI/cast displays do not.
        return builtInName != null && builtInName.equals(display.getName());
    }

    private static int[] realSize(Display display) {
        if (display == null) {
            return new int[] {1, 1};
        }
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                return new int[] {metrics.widthPixels, metrics.heightPixels};
            }
        } catch (Throwable ignored) {
        }
        Display.Mode mode = display.getMode();
        return new int[] {Math.max(1, mode.getPhysicalWidth()),
                Math.max(1, mode.getPhysicalHeight())};
    }

    static String profileForSize(int width, int height) {
        float ratio = Math.max(width, height) / (float) Math.max(1, Math.min(width, height));
        return ratio >= 1.55f ? PROFILE_COVER : PROFILE_MAIN;
    }
}
