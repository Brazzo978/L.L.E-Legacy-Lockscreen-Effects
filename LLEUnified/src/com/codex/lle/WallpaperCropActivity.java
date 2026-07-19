package com.codex.lle;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Shared wallpaper cropper used by onboarding and settings.
 *
 * <p>Callers may pass a document URI or omit it to let this activity launch an image picker.
 * The crop is rendered once, then that exact bitmap is used for both the lock wallpaper and
 * LLE's fixed effect source when {@link #MODE_SET_LOCK_AND_CACHE} is requested.</p>
 */
public final class WallpaperCropActivity extends Activity {
    public static final String EXTRA_SOURCE_URI = "source_uri";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_PROFILE = "profile";
    public static final String EXTRA_EFFECT = "effect";
    public static final String EXTRA_TARGET_WIDTH = "target_width";
    public static final String EXTRA_TARGET_HEIGHT = "target_height";
    public static final String EXTRA_REQUIRE_PRECISE_ACK = "require_precise_ack";
    public static final String EXTRA_SAVED_PATH = "saved_path";

    public static final String MODE_SET_LOCK_AND_CACHE = "set_lock_and_cache";
    public static final String MODE_CACHE_ONLY = "cache_only";

    private static final String TAG = "LLEWallpaperCrop";
    private static final String STATE_SOURCE_URI = "crop_source_uri";
    private static final String STATE_PRECISE_ACK = "crop_precise_ack";
    private static final String STATE_ZOOM = "crop_zoom";
    private static final String STATE_OFFSET_X = "crop_offset_x";
    private static final String STATE_OFFSET_Y = "crop_offset_y";
    private static final int REQUEST_OPEN_DOCUMENT = 7231;
    private static final int BLUE = Color.rgb(64, 142, 255);
    private static final int SURFACE = Color.rgb(19, 24, 32);
    private static final int MUTED = Color.rgb(165, 176, 192);

    private WallpaperCropView cropView;
    private TextView zoomLabel;
    private TextView loadingLabel;
    private ProgressBar progressBar;
    private Button resetButton;
    private Button saveButton;
    private Bitmap sourceBitmap;
    private Uri sourceUri;
    private String sourceLabel = "Imported wallpaper";
    private String mode = MODE_CACHE_ONLY;
    private String profile = FoldDisplayTarget.PROFILE_SINGLE;
    private int effect;
    private int targetWidth;
    private int targetHeight;
    private int originalWidth;
    private int originalHeight;
    private boolean requirePreciseAck;
    private boolean preciseAcknowledged;
    private boolean busy;
    private volatile boolean destroyed;
    private boolean restoreTransform;
    private float restoredZoom = 1f;
    private float restoredOffsetX;
    private float restoredOffsetY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        readConfiguration(getIntent());
        if (savedInstanceState != null) {
            String savedUri = savedInstanceState.getString(STATE_SOURCE_URI, "");
            if (savedUri != null && !savedUri.trim().isEmpty()) {
                sourceUri = Uri.parse(savedUri);
            }
            preciseAcknowledged = savedInstanceState.getBoolean(STATE_PRECISE_ACK, false);
            restoreTransform = savedInstanceState.containsKey(STATE_ZOOM);
            restoredZoom = savedInstanceState.getFloat(STATE_ZOOM, 1f);
            restoredOffsetX = savedInstanceState.getFloat(STATE_OFFSET_X, 0f);
            restoredOffsetY = savedInstanceState.getFloat(STATE_OFFSET_Y, 0f);
        }
        buildUi();
        if (sourceUri == null) {
            openDocumentPicker();
        } else {
            loadSource(sourceUri);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (sourceUri != null) {
            outState.putString(STATE_SOURCE_URI, sourceUri.toString());
        }
        outState.putBoolean(STATE_PRECISE_ACK, preciseAcknowledged);
        if (!busy && cropView != null && cropView.isReady()) {
            outState.putFloat(STATE_ZOOM, cropView.currentZoom());
            outState.putFloat(STATE_OFFSET_X, cropView.normalizedOffsetX());
            outState.putFloat(STATE_OFFSET_Y, cropView.normalizedOffsetY());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
            sourceBitmap.recycle();
        }
        sourceBitmap = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (busy) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_DOCUMENT) {
            return;
        }
        Uri selected = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (selected == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        sourceUri = selected;
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(selected, flags);
        } catch (Throwable ignored) {
        }
        loadSource(selected);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
        }
    }

    private void readConfiguration(Intent intent) {
        effect = intent == null ? OverlayPrefs.unlockEffect(this)
                : intent.getIntExtra(EXTRA_EFFECT, OverlayPrefs.unlockEffect(this));
        String requestedProfile = intent == null ? null : intent.getStringExtra(EXTRA_PROFILE);
        profile = FoldDisplayTarget.normalizeProfile(requestedProfile == null
                ? FoldDisplayTarget.cacheProfileForContext(this) : requestedProfile);
        String requestedMode = intent == null ? null : intent.getStringExtra(EXTRA_MODE);
        mode = MODE_SET_LOCK_AND_CACHE.equals(requestedMode)
                ? MODE_SET_LOCK_AND_CACHE : MODE_CACHE_ONLY;

        int[] fallbackSize = currentDisplaySize();
        int configuredWidth = intent == null ? fallbackSize[0]
                : Math.max(1, intent.getIntExtra(EXTRA_TARGET_WIDTH, fallbackSize[0]));
        int configuredHeight = intent == null ? fallbackSize[1]
                : Math.max(1, intent.getIntExtra(EXTRA_TARGET_HEIGHT, fallbackSize[1]));
        targetWidth = Math.min(configuredWidth, configuredHeight);
        targetHeight = Math.max(configuredWidth, configuredHeight);
        requirePreciseAck = intent == null || intent.getBooleanExtra(
                EXTRA_REQUIRE_PRECISE_ACK, MODE_CACHE_ONLY.equals(mode));
        sourceUri = uriFromIntent(intent);
    }

    private Uri uriFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        String encoded = intent.getStringExtra(EXTRA_SOURCE_URI);
        if (encoded != null && !encoded.trim().isEmpty()) {
            return Uri.parse(encoded.trim());
        }
        try {
            Object parcelable = intent.getParcelableExtra(EXTRA_SOURCE_URI);
            if (parcelable instanceof Uri) {
                return (Uri) parcelable;
            }
        } catch (Throwable ignored) {
        }
        return intent.getData();
    }

    private int[] currentDisplaySize() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int width = Math.max(1, metrics.widthPixels);
        int height = Math.max(1, metrics.heightPixels);
        return new int[] {Math.min(width, height), Math.max(width, height)};
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11, 15, 21));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(10), dp(12));
        header.setBackgroundColor(SURFACE);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Center your wallpaper", Color.WHITE, 21f, true);
        titles.addView(title);
        String destination = MODE_SET_LOCK_AND_CACHE.equals(mode)
                ? "Lock screen + all LLE effects" : "All LLE effects";
        TextView subtitle = text(targetWidth + " × " + targetHeight + "  •  " + destination,
                MUTED, 13f, false);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button close = iconButton("×", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!busy) {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        FrameLayout preview = new FrameLayout(this);
        cropView = new WallpaperCropView(this);
        cropView.setTargetSize(targetWidth, targetHeight);
        preview.addView(cropView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        progressBar = new ProgressBar(this);
        loading.addView(progressBar, new LinearLayout.LayoutParams(dp(44), dp(44)));
        loadingLabel = text("Preparing full-resolution preview…", Color.WHITE, 14f, false);
        LinearLayout.LayoutParams loadingTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        loadingTextParams.setMargins(0, dp(12), 0, 0);
        loading.addView(loadingLabel, loadingTextParams);
        preview.addView(loading, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        loading.setTag("loading_container");
        root.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(18), dp(12), dp(18), dp(18));
        footer.setBackgroundColor(SURFACE);
        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.addView(text("Pinch to zoom  •  drag to position", MUTED, 13f, false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        zoomLabel = text("100%", Color.WHITE, 13f, true);
        status.addView(zoomLabel);
        footer.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        actionsParams.setMargins(0, dp(12), 0, 0);
        footer.addView(actions, actionsParams);
        resetButton = actionButton("Reset", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!busy && sourceBitmap != null) {
                    cropView.setBitmap(sourceBitmap);
                }
            }
        });
        actions.addView(resetButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.34f));
        saveButton = actionButton(MODE_SET_LOCK_AND_CACHE.equals(mode)
                ? "Set lock wallpaper" : "Use this crop", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestSave();
            }
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.66f);
        saveParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(saveButton, saveParams);
        root.addView(footer);

        cropView.setOnTransformChangedListener(
                new WallpaperCropView.OnTransformChangedListener() {
                    @Override
                    public void onTransformChanged(int zoomPercent) {
                        if (zoomLabel != null) {
                            zoomLabel.setText(zoomPercent + "%");
                        }
                    }
                });
        setContentView(root);
        root.setAlpha(0f);
        root.setTranslationY(dp(10));
        root.animate().alpha(1f).translationY(0f).setDuration(280L).start();
        setControlsEnabled(false);
    }

    private void openDocumentPicker() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(picker, REQUEST_OPEN_DOCUMENT);
        } catch (Throwable firstFailure) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("image/*");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(Intent.createChooser(fallback, "Choose wallpaper"),
                        REQUEST_OPEN_DOCUMENT);
            } catch (Throwable secondFailure) {
                Toast.makeText(this, "No image picker is available", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void loadSource(final Uri uri) {
        showLoading("Preparing full-resolution preview…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final LoadedBitmap loaded = decodeSource(uri);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (destroyed || isFinishing()) {
                                if (!loaded.bitmap.isRecycled()) {
                                    loaded.bitmap.recycle();
                                }
                                return;
                            }
                            sourceBitmap = loaded.bitmap;
                            originalWidth = loaded.originalWidth;
                            originalHeight = loaded.originalHeight;
                            sourceLabel = ManualEffectBackground.displayName(
                                    WallpaperCropActivity.this, uri);
                            cropView.setBitmap(sourceBitmap);
                            if (restoreTransform) {
                                cropView.restoreTransform(restoredZoom,
                                        restoredOffsetX, restoredOffsetY);
                                restoreTransform = false;
                            }
                            hideLoading();
                            setControlsEnabled(true);
                        }
                    });
                } catch (final Throwable error) {
                    Log.w(TAG, "Could not decode selected wallpaper", error);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!destroyed) {
                                showLoading("This image could not be opened");
                                Toast.makeText(WallpaperCropActivity.this,
                                        messageFor(error, "Unreadable image"),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        }, "LLE-wallpaper-decode").start();
    }

    private LoadedBitmap decodeSource(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream boundsInput = getContentResolver().openInputStream(uri);
        if (boundsInput == null) {
            throw new IOException("The selected image cannot be opened");
        }
        try {
            BitmapFactory.decodeStream(boundsInput, null, bounds);
        } finally {
            boundsInput.close();
        }
        if (bounds.outWidth < 100 || bounds.outHeight < 100) {
            throw new IOException("The selected file is not a readable bitmap");
        }

        int orientation = readExifOrientation(uri);
        int orientedWidth = swapsAxes(orientation) ? bounds.outHeight : bounds.outWidth;
        int orientedHeight = swapsAxes(orientation) ? bounds.outWidth : bounds.outHeight;
        float aspect = orientedWidth / (float) Math.max(1, orientedHeight);
        if (aspect < 0.10f || aspect > 10f) {
            throw new IOException("The selected image has an unsupported aspect ratio");
        }
        int sample = 1;
        int desiredWidth = Math.max(targetWidth, 1080);
        int desiredHeight = Math.max(targetHeight, 1080);
        final long maxDecodePixels = 16L * 1024L * 1024L;
        while (true) {
            int next = sample * 2;
            int nextWidth = Math.max(1, orientedWidth / next);
            int nextHeight = Math.max(1, orientedHeight / next);
            long currentPixels = (long) Math.max(1, orientedWidth / sample)
                    * Math.max(1, orientedHeight / sample);
            boolean preservesTarget = nextWidth >= Math.round(desiredWidth * 0.88f)
                    && nextHeight >= Math.round(desiredHeight * 0.88f);
            boolean needsMemoryCap = currentPixels > maxDecodePixels
                    && nextWidth >= 256 && nextHeight >= 256;
            if (!preservesTarget && !needsMemoryCap) {
                break;
            }
            sample = next;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = Math.max(1, sample);
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IOException("The selected image cannot be opened");
        }
        Bitmap decoded;
        try {
            decoded = BitmapFactory.decodeStream(input, null, options);
        } finally {
            input.close();
        }
        if (decoded == null || decoded.isRecycled()) {
            throw new IOException("The selected image cannot be decoded");
        }
        Bitmap oriented = orientBitmap(decoded, orientation);
        if (oriented != decoded && !decoded.isRecycled()) {
            decoded.recycle();
        }
        return new LoadedBitmap(oriented, orientedWidth, orientedHeight);
    }

    private int readExifOrientation(Uri uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return 1;
        }
        InputStream input = null;
        try {
            // Reflection keeps the minSdk 23 class verifier independent of API 24 ExifInterface.
            Class<?> type = Class.forName("android.media.ExifInterface");
            Constructor<?> constructor = type.getConstructor(InputStream.class);
            input = getContentResolver().openInputStream(uri);
            if (input == null) {
                return 1;
            }
            Object exif = constructor.newInstance(input);
            Method getter = type.getMethod("getAttributeInt", String.class, int.class);
            Object value = getter.invoke(exif, "Orientation", 1);
            return value instanceof Integer ? (Integer) value : 1;
        } catch (Throwable ignored) {
            return 1;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private Bitmap orientBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case 2:
                matrix.setScale(-1f, 1f);
                break;
            case 3:
                matrix.setRotate(180f);
                break;
            case 4:
                matrix.setScale(1f, -1f);
                break;
            case 5:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case 6:
                matrix.setRotate(90f);
                break;
            case 7:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case 8:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }
        try {
            return Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Throwable ignored) {
            return bitmap;
        }
    }

    private boolean swapsAxes(int orientation) {
        return orientation >= 5 && orientation <= 8;
    }

    private void requestSave() {
        if (busy || !cropView.isReady()) {
            return;
        }
        if (requirePreciseAck && !preciseAcknowledged) {
            new AlertDialog.Builder(this)
                    .setTitle("Precise alignment matters")
                    .setMessage("The crop in the frame will be used pixel-for-pixel by LLE. "
                            + "Center it carefully: even a small offset can be visible in some "
                            + "unlock effects. Are you happy with this alignment?")
                    .setNegativeButton("Go back", null)
                    .setPositiveButton("I understand, use it",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    preciseAcknowledged = true;
                                    savePreparedCrop();
                                }
                            })
                    .show();
            return;
        }
        savePreparedCrop();
    }

    private void savePreparedCrop() {
        final Bitmap prepared;
        try {
            prepared = cropView.renderCrop();
        } catch (Throwable error) {
            Toast.makeText(this, "Not enough memory to prepare this wallpaper",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (prepared == null) {
            Toast.makeText(this, "The crop is not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }
        busy = true;
        setControlsEnabled(false);
        showLoading(MODE_SET_LOCK_AND_CACHE.equals(mode)
                ? "Setting lock wallpaper and LLE source…" : "Saving exact LLE source…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ManualEffectBackground.ImportResult imported =
                            ManualEffectBackground.importPreparedBitmap(
                                    WallpaperCropActivity.this, sourceUri, prepared,
                                    effect, profile, sourceLabel, originalWidth, originalHeight);
                    if (MODE_SET_LOCK_AND_CACHE.equals(mode)) {
                        setExactLockWallpaper(prepared);
                    }
                    // The same immutable prepared PNG backs every screenshot-driven renderer.
                    if (!OverlayPrefs.useImportedEffectBackgroundForAll(
                            WallpaperCropActivity.this, profile, imported.file,
                            imported.displayName, imported.width, imported.height)) {
                        throw new IOException("LLE could not commit the fixed wallpaper source");
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (destroyed) {
                                if (!prepared.isRecycled()) {
                                    prepared.recycle();
                                }
                                return;
                            }
                            prepared.recycle();
                            Intent result = new Intent();
                            result.putExtra(EXTRA_SAVED_PATH, imported.file.getAbsolutePath());
                            result.putExtra(EXTRA_MODE, mode);
                            result.putExtra(EXTRA_PROFILE, profile);
                            setResult(RESULT_OK, result);
                            Toast.makeText(WallpaperCropActivity.this,
                                    MODE_SET_LOCK_AND_CACHE.equals(mode)
                                            ? "Lock wallpaper and LLE source are aligned"
                                            : "Exact wallpaper source is active for LLE",
                                    Toast.LENGTH_LONG).show();
                            busy = false;
                            finish();
                        }
                    });
                } catch (final Throwable error) {
                    Log.w(TAG, "Could not save prepared wallpaper", error);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (destroyed) {
                                if (!prepared.isRecycled()) {
                                    prepared.recycle();
                                }
                                return;
                            }
                            if (!prepared.isRecycled()) {
                                prepared.recycle();
                            }
                            busy = false;
                            hideLoading();
                            setControlsEnabled(true);
                            Toast.makeText(WallpaperCropActivity.this,
                                    messageFor(error, "Wallpaper could not be saved"),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, "LLE-wallpaper-save").start();
    }

    private void setExactLockWallpaper(Bitmap prepared) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            throw new IOException("Lock-only wallpaper requires Android 7 or newer");
        }
        WallpaperManager manager = WallpaperManager.getInstance(this);
        if (!manager.isWallpaperSupported()) {
            throw new IOException("Wallpaper changes are disabled on this device");
        }
        if (!manager.isSetWallpaperAllowed()) {
            throw new IOException("This user is not allowed to change the lock wallpaper");
        }
        manager.setBitmap(prepared, null, true, WallpaperManager.FLAG_LOCK);
    }

    private void setControlsEnabled(boolean enabled) {
        if (resetButton != null) {
            resetButton.setEnabled(enabled && !busy);
            resetButton.setAlpha(enabled && !busy ? 1f : 0.45f);
        }
        if (saveButton != null) {
            saveButton.setEnabled(enabled && !busy);
            saveButton.setAlpha(enabled && !busy ? 1f : 0.45f);
        }
        if (cropView != null) {
            cropView.setEnabled(enabled && !busy);
        }
    }

    private void showLoading(String text) {
        if (loadingLabel != null) {
            loadingLabel.setText(text);
            View container = (View) loadingLabel.getParent();
            container.setVisibility(View.VISIBLE);
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoading() {
        if (loadingLabel != null) {
            View container = (View) loadingLabel.getParent();
            container.setVisibility(View.GONE);
        }
    }

    private TextView text(String value, int color, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private Button iconButton(String value, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(28f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, dp(4));
        button.setMinWidth(0);
        button.setMinHeight(0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setCornerRadius(dp(24));
        button.setBackground(background);
        button.setOnClickListener(listener);
        return button;
    }

    private Button actionButton(String value, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? BLUE : Color.rgb(35, 43, 55));
        background.setCornerRadius(dp(10));
        if (!primary) {
            background.setStroke(dp(1), Color.rgb(65, 76, 92));
        }
        button.setBackground(background);
        button.setOnClickListener(listener);
        return button;
    }

    private String messageFor(Throwable error, String fallback) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LoadedBitmap {
        final Bitmap bitmap;
        final int originalWidth;
        final int originalHeight;

        LoadedBitmap(Bitmap bitmap, int originalWidth, int originalHeight) {
            this.bitmap = bitmap;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
        }
    }
}
