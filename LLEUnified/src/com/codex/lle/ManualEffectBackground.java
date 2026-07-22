package com.codex.lle;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Private, profile-aware storage and decoding for an imported effect wallpaper. */
final class ManualEffectBackground {
    private static final String DIRECTORY = "manual-effect-backgrounds";

    private ManualEffectBackground() {
    }

    static final class ImportResult {
        final File file;
        final String displayName;
        final int width;
        final int height;

        ImportResult(File file, String displayName, int width, int height) {
            this.file = file;
            this.displayName = displayName;
            this.width = width;
            this.height = height;
        }
    }

    static ImportResult importUri(Context context, Uri uri, int effect, String profile,
            int targetWidth, int targetHeight) throws IOException {
        if (context == null || uri == null) {
            throw new IOException("Missing image URI");
        }
        String normalizedProfile = FoldDisplayTarget.normalizeProfile(profile);
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create private wallpaper directory");
        }

        // A new immutable version is created for every import. Switching back to automatic
        // capture only flips a preference and deliberately leaves prior user files recoverable.
        long version = System.currentTimeMillis();
        String baseName = "manual_wallpaper_effect" + effect + "_" + normalizedProfile
                + "_" + version;
        File destination = new File(directory,
                baseName + ".source");
        InputStream raw = context.getContentResolver().openInputStream(uri);
        if (raw == null) {
            throw new IOException("The selected image cannot be opened");
        }
        BufferedInputStream input = new BufferedInputStream(raw, 64 * 1024);
        BufferedOutputStream output = null;
        try {
            output = new BufferedOutputStream(new FileOutputStream(destination), 64 * 1024);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            try {
                input.close();
            } catch (Throwable ignored) {
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(destination.getAbsolutePath(), bounds);
        if (destination.length() <= 0L || bounds.outWidth < 100 || bounds.outHeight < 100) {
            throw new IOException("The selected file is not a readable bitmap");
        }
        float aspect = bounds.outWidth / (float) bounds.outHeight;
        if (aspect < 0.10f || aspect > 10f) {
            throw new IOException("The selected image has an unsupported aspect ratio");
        }

        int preparedWidth = Math.max(1, targetWidth);
        int preparedHeight = Math.max(1, targetHeight);
        Bitmap prepared = decodeCenterCrop(destination, preparedWidth, preparedHeight);
        if (prepared == null || prepared.isRecycled()) {
            throw new IOException("The selected image could not be prepared for this display");
        }
        File preparedFile = new File(directory,
                baseName + "_" + preparedWidth + "x" + preparedHeight + ".png");
        BufferedOutputStream preparedOutput = null;
        try {
            preparedOutput = new BufferedOutputStream(
                    new FileOutputStream(preparedFile), 64 * 1024);
            if (!prepared.compress(Bitmap.CompressFormat.PNG, 100, preparedOutput)) {
                throw new IOException("The prepared wallpaper could not be saved");
            }
            preparedOutput.flush();
        } finally {
            if (preparedOutput != null) {
                try {
                    preparedOutput.close();
                } catch (Throwable ignored) {
                }
            }
            if (!prepared.isRecycled()) {
                prepared.recycle();
            }
        }
        if (!isUsable(preparedFile)) {
            throw new IOException("The private wallpaper copy is unreadable");
        }
        return new ImportResult(preparedFile, queryDisplayName(context, uri),
                bounds.outWidth, bounds.outHeight);
    }

    /**
     * Stores a user-positioned crop without applying another transformation.
     *
     * <p>The original document and the prepared PNG are both written as new private files.
     * Older imports are intentionally retained so changing source mode never destroys a user's
     * previous choice.</p>
     */
    static ImportResult importPreparedBitmap(Context context, Uri sourceUri, Bitmap prepared,
            int effect, String profile, String displayName, int originalWidth,
            int originalHeight) throws IOException {
        if (context == null || sourceUri == null || prepared == null || prepared.isRecycled()) {
            throw new IOException("Missing prepared wallpaper");
        }
        if (prepared.getWidth() <= 0 || prepared.getHeight() <= 0) {
            throw new IOException("The prepared wallpaper has invalid dimensions");
        }
        String normalizedProfile = FoldDisplayTarget.normalizeProfile(profile);
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create private wallpaper directory");
        }

        long version = System.currentTimeMillis();
        String baseName = "manual_wallpaper_effect" + effect + "_" + normalizedProfile
                + "_" + version;
        File sourceFile = new File(directory, baseName + ".source");
        copyUri(context, sourceUri, sourceFile);

        File preparedFile = new File(directory, baseName + "_"
                + prepared.getWidth() + "x" + prepared.getHeight() + ".png");
        BufferedOutputStream output = null;
        try {
            output = new BufferedOutputStream(new FileOutputStream(preparedFile), 64 * 1024);
            if (!prepared.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("The prepared wallpaper could not be saved");
            }
            output.flush();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
        }
        if (!isUsable(sourceFile) || !isUsable(preparedFile)) {
            throw new IOException("The private wallpaper copy is unreadable");
        }
        String label = displayName == null || displayName.trim().isEmpty()
                ? queryDisplayName(context, sourceUri) : displayName.trim();
        return new ImportResult(preparedFile, label,
                Math.max(0, originalWidth), Math.max(0, originalHeight));
    }

    /** Stores a display-sized bitmap obtained directly from WallpaperManager. */
    static ImportResult importPulledLockWallpaper(Context context, Bitmap prepared,
            int effect, String profile, String displayName) throws IOException {
        if (context == null || prepared == null || prepared.isRecycled()) {
            throw new IOException("Missing pulled lockscreen wallpaper");
        }
        if (prepared.getWidth() <= 0 || prepared.getHeight() <= 0) {
            throw new IOException("The pulled wallpaper has invalid dimensions");
        }
        String normalizedProfile = FoldDisplayTarget.normalizeProfile(profile);
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create private wallpaper directory");
        }

        long version = System.currentTimeMillis();
        String baseName = "pulled_lock_wallpaper_effect" + effect + "_"
                + normalizedProfile + "_" + version;
        File preparedFile = new File(directory, baseName + "_"
                + prepared.getWidth() + "x" + prepared.getHeight() + ".png");
        BufferedOutputStream output = null;
        try {
            output = new BufferedOutputStream(new FileOutputStream(preparedFile), 64 * 1024);
            if (!prepared.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("The pulled wallpaper could not be saved");
            }
            output.flush();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
        }
        if (!isUsable(preparedFile)) {
            throw new IOException("The private wallpaper copy is unreadable");
        }
        String label = displayName == null || displayName.trim().isEmpty()
                ? "Current lockscreen wallpaper" : displayName.trim();
        return new ImportResult(preparedFile, label,
                prepared.getWidth(), prepared.getHeight());
    }

    static String displayName(Context context, Uri uri) {
        return context == null || uri == null ? "Imported wallpaper"
                : queryDisplayName(context, uri);
    }

    private static void copyUri(Context context, Uri uri, File destination) throws IOException {
        InputStream raw = context.getContentResolver().openInputStream(uri);
        if (raw == null) {
            throw new IOException("The selected image cannot be opened");
        }
        BufferedInputStream input = new BufferedInputStream(raw, 64 * 1024);
        BufferedOutputStream output = null;
        try {
            output = new BufferedOutputStream(new FileOutputStream(destination), 64 * 1024);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            try {
                input.close();
            } catch (Throwable ignored) {
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static File resolvePrivateFile(Context context, String path) {
        if (context == null || path == null || path.trim().isEmpty()) {
            return null;
        }
        try {
            File directory = new File(context.getFilesDir(), DIRECTORY).getCanonicalFile();
            File candidate = new File(path).getCanonicalFile();
            String prefix = directory.getPath() + File.separator;
            if (!candidate.getPath().startsWith(prefix)) {
                return null;
            }
            return candidate;
        } catch (IOException e) {
            return null;
        }
    }

    static boolean isUsable(File file) {
        if (file == null || !file.isFile() || file.length() <= 0L) {
            return false;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        return bounds.outWidth > 0 && bounds.outHeight > 0;
    }

    static Bitmap decodeCenterCrop(File file, int targetWidth, int targetHeight) {
        if (!isUsable(file)) {
            return null;
        }
        int width = Math.max(1, targetWidth);
        int height = Math.max(1, targetHeight);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= width
                && bounds.outHeight / (sample * 2) >= height) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = Math.max(1, sample);
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (decoded == null || decoded.isRecycled()) {
            return null;
        }

        Bitmap output = null;
        try {
            output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            float sourceRatio = decoded.getWidth() / (float) decoded.getHeight();
            float targetRatio = width / (float) height;
            Rect source;
            if (sourceRatio > targetRatio) {
                int cropWidth = Math.max(1, Math.round(decoded.getHeight() * targetRatio));
                int left = Math.max(0, (decoded.getWidth() - cropWidth) / 2);
                source = new Rect(left, 0,
                        Math.min(decoded.getWidth(), left + cropWidth), decoded.getHeight());
            } else {
                int cropHeight = Math.max(1, Math.round(decoded.getWidth() / targetRatio));
                int top = Math.max(0, (decoded.getHeight() - cropHeight) / 2);
                source = new Rect(0, top, decoded.getWidth(),
                        Math.min(decoded.getHeight(), top + cropHeight));
            }
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
                    | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            canvas.drawBitmap(decoded, source, new Rect(0, 0, width, height), paint);
            output.prepareToDraw();
            return output;
        } catch (Throwable t) {
            if (output != null && !output.isRecycled()) {
                output.recycle();
            }
            return null;
        } finally {
            if (!decoded.isRecycled()) {
                decoded.recycle();
            }
        }
    }

    private static String queryDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri, new String[] {OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String value = cursor.getString(column);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String segment = uri.getLastPathSegment();
        return segment == null || segment.trim().isEmpty()
                ? "Imported wallpaper" : segment.trim();
    }
}
