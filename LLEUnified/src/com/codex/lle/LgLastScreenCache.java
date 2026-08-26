package com.codex.lle;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import java.io.File;

/** Shared UI-side access to the private last-unlocked-frame cache used by LG effects. */
final class LgLastScreenCache {
    static final class Target {
        final String profile;
        final int displayId;
        final int width;
        final int height;
        final File file;

        Target(String profile, int displayId, int width, int height, File file) {
            this.profile = profile;
            this.displayId = displayId;
            this.width = width;
            this.height = height;
            this.file = file;
        }
    }

    static final class FallbackResult {
        final boolean saved;
        final String message;

        FallbackResult(boolean saved, String message) {
            this.saved = saved;
            this.message = message;
        }
    }

    private LgLastScreenCache() {
    }

    static Target activeTarget(Activity activity) {
        String profile = FoldDisplayTarget.cacheProfileForContext(activity);
        int[] size = FoldDisplayTarget.displaySizeForProfile(activity, profile);
        Display display = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display = activity.getDisplay();
        }
        if (display == null) {
            WindowManager manager = (WindowManager) activity.getSystemService(
                    Context.WINDOW_SERVICE);
            if (manager != null) {
                //noinspection deprecation
                display = manager.getDefaultDisplay();
            }
        }
        int displayId = display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
        return new Target(profile, displayId, size[0], size[1],
                OverlayPrefs.lgPreLockUnderlayFile(
                        activity, profile, displayId, size[0], size[1]));
    }

    static Argb8888BitmapStore.Info inspect(Target target) {
        if (target == null) {
            return null;
        }
        Argb8888BitmapStore.Info info = Argb8888BitmapStore.inspect(target.file);
        return info != null && info.width == target.width && info.height == target.height
                ? info : null;
    }

    static boolean isReady(Target target) {
        return inspect(target) != null;
    }

    static File findWallpaperFallback(Activity activity, int effect, Target target) {
        if (activity == null || target == null) {
            return null;
        }
        if (OverlayPrefs.importedEffectBackgroundEnabled(
                activity, effect, target.profile)) {
            File imported = OverlayPrefs.importedEffectBackgroundFile(
                    activity, effect, target.profile);
            if (matches(imported, target)) {
                return imported;
            }
        }
        File shared = OverlayPrefs.effectBackgroundFile(activity, effect, target.profile);
        if (matches(shared, target)) {
            return shared;
        }
        File legacyProfile = OverlayPrefs.legacyPngEffectBackgroundFile(
                activity, target.profile);
        if (matches(legacyProfile, target)) {
            return legacyProfile;
        }
        File legacyEffect = OverlayPrefs.legacyEffectBackgroundFile(activity, effect);
        return matches(legacyEffect, target) ? legacyEffect : null;
    }

    static FallbackResult forceWallpaperFallback(Activity activity, int effect, Target target) {
        File source = findWallpaperFallback(activity, effect, target);
        if (source == null) {
            return new FallbackResult(false,
                    "No exact-size wallpaper cache is available for this display");
        }
        Bitmap bitmap = Argb8888BitmapStore.decode(source);
        if (bitmap == null || bitmap.isRecycled()) {
            return new FallbackResult(false, "The wallpaper cache is unreadable");
        }
        File temp = new File(target.file.getParentFile(), target.file.getName() + ".fallback.tmp");
        boolean saved = false;
        try {
            if (bitmap.getWidth() != target.width || bitmap.getHeight() != target.height
                    || !Argb8888BitmapStore.write(temp, bitmap)) {
                return new FallbackResult(false,
                        "The wallpaper cache does not match the active display");
            }
            saved = Argb8888BitmapStore.replace(temp, target.file);
            if (!saved && target.file.exists() && target.file.delete()) {
                saved = Argb8888BitmapStore.replace(temp, target.file);
            }
            if (!saved) {
                return new FallbackResult(false, "Last screen fallback could not be saved");
            }
            OverlayPrefs.markLgPreLockUnderlayFallback(
                    activity, target.profile, target.displayId,
                    target.width, target.height, System.currentTimeMillis());
            return new FallbackResult(true,
                    "Wallpaper fallback saved as Last screen");
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (temp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    static boolean isWallpaperFallback(Activity activity, Target target) {
        return target != null
                && OverlayPrefs.LG_PRELOCK_UNDERLAY_ORIGIN_WALLPAPER_FALLBACK.equals(
                OverlayPrefs.lgPreLockUnderlayOrigin(
                        activity, target.profile, target.displayId,
                        target.width, target.height));
    }

    static long capturedAt(Activity activity, Target target) {
        if (target == null) {
            return 0L;
        }
        long markedAt = OverlayPrefs.lgPreLockUnderlayCapturedAt(
                activity, target.profile, target.displayId, target.width, target.height);
        return markedAt > 0L ? markedAt : target.file.lastModified();
    }

    private static boolean matches(File file, Target target) {
        Argb8888BitmapStore.Info info = Argb8888BitmapStore.inspect(file);
        return info != null && info.width == target.width && info.height == target.height;
    }
}
