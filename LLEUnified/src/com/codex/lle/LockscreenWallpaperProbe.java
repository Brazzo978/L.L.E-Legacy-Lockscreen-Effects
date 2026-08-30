package com.codex.lle;

import android.Manifest;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;

/** Read-only diagnostic access to the current static lockscreen wallpaper. */
final class LockscreenWallpaperProbe {
    private static final String TAG = "LLEWallpaperProbe";
    private static final int SAMSUNG_FLAG_DISPLAY_PHONE = 0x04;
    private static final int SAMSUNG_FLAG_DISPLAY_SUB = 0x10;

    static final class Result {
        final Bitmap bitmap;
        final int source;
        final String profile;
        final int originalWidth;
        final int originalHeight;

        Result(Bitmap bitmap, int source, String profile,
                int originalWidth, int originalHeight) {
            this.bitmap = bitmap;
            this.source = source;
            this.profile = FoldDisplayTarget.normalizeProfile(profile);
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
        }

        String sourceLabel() {
            String type = (source & WallpaperManager.FLAG_LOCK) != 0
                    ? "lock wallpaper" : "system wallpaper fallback";
            return FoldDisplayTarget.PROFILE_SINGLE.equals(profile)
                    ? type : profile + " " + type;
        }
    }

    private static final class DecodedWallpaper {
        final Bitmap bitmap;
        final int originalWidth;
        final int originalHeight;

        DecodedWallpaper(Bitmap bitmap, int originalWidth, int originalHeight) {
            this.bitmap = bitmap;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
        }
    }

    private LockscreenWallpaperProbe() {
    }

    static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    static boolean hasReadAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    static Result read(Context context) throws IOException {
        int width = Math.max(1, context.getResources().getDisplayMetrics().widthPixels);
        int height = Math.max(1, context.getResources().getDisplayMetrics().heightPixels);
        String profile = FoldDisplayTarget.cacheProfileForContext(context);
        return read(context, profile, width, height);
    }

    static Result read(Context context, String requestedProfile,
            int requestedWidth, int requestedHeight) throws IOException {
        if (!isSupported()) {
            throw new IOException("Lock wallpaper reading requires Android 7 or newer");
        }
        String profile = FoldDisplayTarget.normalizeProfile(requestedProfile);
        WallpaperManager manager = WallpaperManager.getInstance(context);
        int maxWidth = Math.max(1, requestedWidth);
        int maxHeight = Math.max(1, requestedHeight);
        int displayFlag = FoldDisplayTarget.PROFILE_COVER.equals(profile)
                ? SAMSUNG_FLAG_DISPLAY_SUB
                : FoldDisplayTarget.PROFILE_MAIN.equals(profile)
                ? SAMSUNG_FLAG_DISPLAY_PHONE : 0;
        int lockWhich = WallpaperManager.FLAG_LOCK | displayFlag;
        int systemWhich = WallpaperManager.FLAG_SYSTEM | displayFlag;

        DecodedWallpaper decoded = displayFlag == 0 ? null
                : decodeSamsungDrawable(manager, lockWhich, maxWidth, maxHeight);
        int lockType = displayFlag == 0 ? 0 : samsungWallpaperType(manager, lockWhich);
        if (decoded == null && lockType == 0) {
            decoded = decode(manager, lockWhich, maxWidth, maxHeight);
        }
        decoded = normalizeExactSize(decoded, maxWidth, maxHeight);
        int source = lockWhich;
        if (decoded == null) {
            decoded = displayFlag == 0 ? null
                    : decodeSamsungDrawable(manager, systemWhich, maxWidth, maxHeight);
            int systemType = displayFlag == 0
                    ? 0 : samsungWallpaperType(manager, systemWhich);
            if (decoded == null && systemType == 0) {
                decoded = decode(manager, systemWhich, maxWidth, maxHeight);
            }
            decoded = normalizeExactSize(decoded, maxWidth, maxHeight);
            source = systemWhich;
        }
        if (decoded == null || decoded.bitmap == null) {
            if (displayFlag != 0 && lockType != 0) {
                throw new IOException("Samsung does not expose an exact image for this "
                        + "layered or live lockscreen wallpaper");
            }
            throw new IOException("No readable static lockscreen wallpaper was returned");
        }
        return new Result(decoded.bitmap, source, profile,
                decoded.originalWidth, decoded.originalHeight);
    }

    /** Samsung's composed thumbnail represents layered/live wallpaper after its stock crop. */
    private static DecodedWallpaper decodeSamsungDrawable(WallpaperManager manager, int which,
            int maxWidth, int maxHeight) {
        try {
            Method method = manager.getClass().getMethod("semGetDrawable", Integer.TYPE);
            Object value = method.invoke(manager, Integer.valueOf(which));
            if (!(value instanceof Drawable)) {
                return null;
            }
            Drawable drawable = (Drawable) value;
            if (drawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    return new DecodedWallpaper(bitmap, bitmap.getWidth(), bitmap.getHeight());
                }
            }
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            if (width <= 0 || height <= 0) {
                width = Math.max(1, maxWidth);
                height = Math.max(1, maxHeight);
            }
            Bitmap rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(rendered);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            rendered.prepareToDraw();
            return new DecodedWallpaper(rendered, width, height);
        } catch (Throwable error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            Log.d(TAG, "Samsung composed wallpaper unavailable for which=" + which
                    + " (" + cause.getClass().getSimpleName() + ": "
                    + cause.getMessage() + ")");
            return null;
        }
    }

    private static int samsungWallpaperType(WallpaperManager manager, int which) {
        try {
            Method method = manager.getClass().getMethod(
                    "semGetWallpaperType", Integer.TYPE);
            Object value = method.invoke(manager, Integer.valueOf(which));
            return value instanceof Integer ? ((Integer) value).intValue() : -1;
        } catch (Throwable error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            Log.d(TAG, "Samsung wallpaper type unavailable for which=" + which
                    + " (" + cause.getClass().getSimpleName() + ")");
            return -1;
        }
    }

    private static DecodedWallpaper normalizeExactSize(DecodedWallpaper decoded,
            int targetWidth, int targetHeight) {
        if (decoded == null || decoded.bitmap == null || decoded.bitmap.isRecycled()) {
            return null;
        }
        Bitmap bitmap = decoded.bitmap;
        if (bitmap.getWidth() == targetWidth && bitmap.getHeight() == targetHeight) {
            return decoded;
        }
        float sourceRatio = bitmap.getWidth() / (float) bitmap.getHeight();
        float targetRatio = targetWidth / (float) targetHeight;
        if (Math.abs(sourceRatio - targetRatio) > 0.0025f) {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return null;
        }
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(
                    bitmap, targetWidth, targetHeight, true);
            if (scaled != bitmap && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            scaled.prepareToDraw();
            return new DecodedWallpaper(scaled,
                    decoded.originalWidth, decoded.originalHeight);
        } catch (Throwable error) {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            Log.w(TAG, "Exact wallpaper scaling failed", error);
            return null;
        }
    }

    private static DecodedWallpaper decode(WallpaperManager manager, int which,
            int maxWidth, int maxHeight) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        ParcelFileDescriptor descriptor = null;
        try {
            descriptor = manager.getWallpaperFile(which);
            if (descriptor == null) {
                return null;
            }
            BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(), null, bounds);
        } catch (SecurityException e) {
            throw new IOException("Android denied access to the current wallpaper", e);
        } catch (IllegalArgumentException e) {
            return null;
        } finally {
            closeQuietly(descriptor);
        }

        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= maxWidth
                && bounds.outHeight / (sampleSize * 2) >= maxHeight) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sampleSize);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            descriptor = manager.getWallpaperFile(which);
            if (descriptor == null) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeFileDescriptor(
                    descriptor.getFileDescriptor(), null, options);
            if (bitmap == null) {
                throw new IOException("The wallpaper file could not be decoded");
            }
            return new DecodedWallpaper(bitmap, bounds.outWidth, bounds.outHeight);
        } catch (SecurityException e) {
            throw new IOException("Android denied access to the current wallpaper", e);
        } catch (IllegalArgumentException e) {
            return null;
        } catch (OutOfMemoryError e) {
            throw new IOException("Not enough memory to preview the wallpaper", e);
        } finally {
            closeQuietly(descriptor);
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (IOException ignored) {
        }
    }
}
