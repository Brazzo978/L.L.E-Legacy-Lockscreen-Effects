package com.codex.lle;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Visual, non-destructive editor for the global charging-doodle transform.
 *
 * <p>The activity keeps all edits local until Save is pressed. Its preview has the active
 * display's exact aspect ratio and scales pixel offsets between preview and display space, so
 * drag gestures remain compatible with {@link SeasonalDoodleView}'s existing preferences.</p>
 */
public final class DoodlePositionActivity extends Activity {
    private static final String STATE_X = "doodle_editor_x";
    private static final String STATE_Y = "doodle_editor_y";
    private static final String STATE_SIZE = "doodle_editor_size";
    private static final int HORIZONTAL_SNAP_PX = 5;
    private static final int VERTICAL_SNAP_PX = 5;

    private static final int BLUE = Color.rgb(20, 126, 245);
    private static final int BACKGROUND = Color.rgb(244, 246, 249);
    private static final int TEXT = Color.rgb(36, 42, 50);
    private static final int MUTED = Color.rgb(104, 114, 128);

    private int targetWidth;
    private int targetHeight;
    private int offsetX;
    private int offsetY;
    private int sizePercent;
    private String activeProfile;

    private PreviewHost previewHost;
    private ImageView wallpaperView;
    private SeasonalDoodleView doodleView;
    private GestureLayer gestureLayer;
    private TextView transformLabel;
    private TextView sourceLabel;
    private SeekBar sizeSlider;
    private SeekBar horizontalSlider;
    private SeekBar verticalSlider;
    private Bitmap previewBitmap;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        resolveTargetDisplay();
        activeProfile = FoldDisplayTarget.isFoldDevice(this)
                && OverlayPrefs.foldModeEnabled(this)
                ? FoldDisplayTarget.cacheProfileForContext(this)
                : FoldDisplayTarget.PROFILE_SINGLE;

        if (savedInstanceState == null) {
            offsetX = OverlayPrefs.positionOffsetX(this);
            offsetY = OverlayPrefs.positionOffsetY(this);
            sizePercent = OverlayPrefs.doodleSizePercent(this);
        } else {
            offsetX = OverlayPrefs.clampPositionOffset(
                    savedInstanceState.getInt(STATE_X, 0));
            offsetY = OverlayPrefs.clampPositionOffset(
                    savedInstanceState.getInt(STATE_Y, 0));
            sizePercent = OverlayPrefs.clampDoodleSizePercent(
                    savedInstanceState.getInt(
                            STATE_SIZE, OverlayPrefs.DOODLE_SIZE_DEFAULT_PERCENT));
        }

        offsetX = clampHorizontalOffset(offsetX);
        offsetY = clampVerticalOffset(offsetY);
        setContentView(buildContent());
        updateEditor(false);
        loadPreviewBackground();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_X, offsetX);
        outState.putInt(STATE_Y, offsetY);
        outState.putInt(STATE_SIZE, sizePercent);
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onDestroy() {
        destroyed = true;
        if (wallpaperView != null) {
            wallpaperView.setImageDrawable(null);
        }
        if (previewBitmap != null && !previewBitmap.isRecycled()) {
            previewBitmap.recycle();
        }
        previewBitmap = null;
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(BACKGROUND);
            window.setNavigationBarColor(BACKGROUND);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }
    @SuppressWarnings("deprecation")
    private void resolveTargetDisplay() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (manager != null) {
            manager.getDefaultDisplay().getRealMetrics(metrics);
        }
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            metrics = getResources().getDisplayMetrics();
        }
        targetWidth = Math.max(1, metrics.widthPixels);
        targetHeight = Math.max(1, metrics.heightPixels);
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.addView(appBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));

        FrameLayout previewArea = new FrameLayout(this);
        previewArea.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(previewArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        previewHost = new PreviewHost(this);
        previewHost.setTargetAspect(targetWidth, targetHeight);
        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.FIT_XY);
        wallpaperView.setBackground(fallbackWallpaper());
        previewHost.addView(wallpaperView, matchFrame());

        doodleView = new SeasonalDoodleView(this);
        doodleView.setSeasonMode(OverlayPrefs.seasonMode(this));
        doodleView.setBatteryPercent(readBatteryPercent());
        doodleView.setDebugRollingCharge(false);
        previewHost.addView(doodleView, matchFrame());

        gestureLayer = new GestureLayer(this);
        previewHost.addView(gestureLayer, matchFrame());

        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        previewArea.addView(previewHost, previewParams);

        root.addView(editorControls(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View appBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(8), dp(20), dp(8));
        bar.setBackgroundColor(Color.WHITE);

        TextView title = text("Doodle layout", TEXT, 20f, true);
        bar.addView(title);
        TextView subtitle = text("Drag to move · pinch or use the slider to resize",
                MUTED, 13f, false);
        bar.addView(subtitle);
        return bar;
    }

    private View editorControls() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(10), dp(14), dp(14));
        panel.setBackgroundColor(Color.WHITE);

        transformLabel = text("", TEXT, 14f, true);
        transformLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        panel.addView(transformLabel);

        sourceLabel = text("Preview: loading lockscreen background…", MUTED, 12f, false);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sourceParams.setMargins(0, dp(2), 0, dp(6));
        panel.addView(sourceLabel, sourceParams);

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sizeCaption = text("Size", TEXT, 14f, true);
        sizeRow.addView(sizeCaption, new LinearLayout.LayoutParams(dp(48), dp(42)));
        sizeSlider = new SeekBar(this);
        sizeSlider.setMax(OverlayPrefs.DOODLE_SIZE_MAX_PERCENT
                - OverlayPrefs.DOODLE_SIZE_MIN_PERCENT);
        sizeSlider.setProgress(sizePercent - OverlayPrefs.DOODLE_SIZE_MIN_PERCENT);
        sizeSlider.setContentDescription("Doodle size");
        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                sizePercent = OverlayPrefs.clampDoodleSizePercent(
                        OverlayPrefs.DOODLE_SIZE_MIN_PERCENT + progress);
                updateEditor(false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        sizeRow.addView(sizeSlider, new LinearLayout.LayoutParams(0, dp(42), 1f));
        panel.addView(sizeRow);

        panel.addView(axisSliderGroup(), rowParams());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(actionButton("Reset", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                offsetX = 0;
                offsetY = 0;
                sizePercent = OverlayPrefs.DOODLE_SIZE_DEFAULT_PERCENT;
                updateEditor(true);
            }
        }), actionParams(false));
        actions.addView(actionButton("Cancel", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        }), actionParams(true));
        actions.addView(actionButton("Save", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAndFinish();
            }
        }), actionParams(true));
        panel.addView(actions, rowParams());
        return panel;
    }

    private View axisSliderGroup() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView horizontalLabel = text("Horizontal  ·  center = 0  ·  step 5 px",
                TEXT, 12f, true);
        group.addView(horizontalLabel);
        horizontalSlider = axisSlider("Horizontal", true);
        group.addView(axisControlRow(horizontalSlider, true), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        TextView verticalLabel = text("Vertical  ·  center = 0  ·  step 5 px",
                TEXT, 12f, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(4), 0, 0);
        group.addView(verticalLabel, labelParams);
        verticalSlider = axisSlider("Vertical", false);
        group.addView(axisControlRow(verticalSlider, false), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        syncAxisSliders();
        return group;
    }

    private View axisControlRow(SeekBar slider, final boolean horizontal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(axisStepButton("−5", horizontal, -5),
                new LinearLayout.LayoutParams(dp(52), dp(38)));
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                0, dp(42), 1f);
        sliderParams.setMargins(dp(4), 0, dp(4), 0);
        row.addView(slider, sliderParams);
        row.addView(axisStepButton("+5", horizontal, 5),
                new LinearLayout.LayoutParams(dp(52), dp(38)));
        return row;
    }

    private Button axisStepButton(String label, final boolean horizontal, final int delta) {
        Button button = actionButton(label, false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (horizontal) {
                    offsetX = clampHorizontalOffset(offsetX + delta);
                } else {
                    offsetY = clampVerticalOffset(offsetY + delta);
                }
                updateEditor(false);
            }
        });
        button.setTextSize(12f);
        button.setContentDescription((delta < 0 ? "Decrease " : "Increase ")
                + (horizontal ? "horizontal" : "vertical")
                + " doodle position by 5 pixels");
        return button;
    }
    private SeekBar axisSlider(String label, final boolean horizontal) {
        SeekBar slider = new SeekBar(this);
        final int axisMax = horizontal ? maxHorizontalOffset() : maxVerticalOffset();
        slider.setMax(axisMax * 2);
        slider.setProgress(axisMax);
        slider.setContentDescription(label + " doodle position. Center is zero.");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int rawValue = progress - axisMax;
                int snap = horizontal ? HORIZONTAL_SNAP_PX : VERTICAL_SNAP_PX;
                int value = Math.round(rawValue / (float) snap) * snap;
                if (value != rawValue) {
                    seekBar.setProgress(axisMax + value);
                }
                if (horizontal) {
                    offsetX = value;
                } else {
                    offsetY = value;
                }
                updateEditor(false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        return slider;
    }

    private void syncAxisSliders() {
        if (horizontalSlider != null) {
            horizontalSlider.setProgress(maxHorizontalOffset() + offsetX);
        }
        if (verticalSlider != null) {
            verticalSlider.setProgress(maxVerticalOffset() + offsetY);
        }
    }

    private int maxHorizontalOffset() {
        return Math.min(OverlayPrefs.POSITION_OFFSET_MAX,
                Math.max(100, targetWidth / 2));
    }

    private int maxVerticalOffset() {
        return Math.min(OverlayPrefs.POSITION_OFFSET_MAX,
                Math.max(100, targetHeight / 2));
    }

    private int clampHorizontalOffset(int value) {
        return Math.max(-maxHorizontalOffset(), Math.min(maxHorizontalOffset(), value));
    }

    private int clampVerticalOffset(int value) {
        return Math.max(-maxVerticalOffset(), Math.min(maxVerticalOffset(), value));
    }
    private void saveAndFinish() {
        OverlayPrefs.get(this).edit()
                .putInt(OverlayPrefs.POSITION_OFFSET_X, offsetX)
                .putInt(OverlayPrefs.POSITION_OFFSET_Y, offsetY)
                .putInt(OverlayPrefs.DOODLE_SIZE_PERCENT, sizePercent)
                .apply();
        Intent result = new Intent();
        result.putExtra(OverlayPrefs.POSITION_OFFSET_X, offsetX);
        result.putExtra(OverlayPrefs.POSITION_OFFSET_Y, offsetY);
        result.putExtra(OverlayPrefs.DOODLE_SIZE_PERCENT, sizePercent);
        setResult(RESULT_OK, result);
        Toast.makeText(this, "Doodle layout saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateEditor(boolean syncSlider) {
        if (transformLabel != null) {
            transformLabel.setText("X " + signed(offsetX) + " px   ·   Y "
                    + signed(offsetY) + " px   ·   Size " + sizePercent + "%");
        }
        syncAxisSliders();
        if (syncSlider && sizeSlider != null) {
            sizeSlider.setProgress(sizePercent - OverlayPrefs.DOODLE_SIZE_MIN_PERCENT);
        }
        if (doodleView != null && previewHost != null
                && previewHost.getWidth() > 0 && previewHost.getHeight() > 0) {
            float scaleX = previewHost.getWidth() / (float) targetWidth;
            float scaleY = previewHost.getHeight() / (float) targetHeight;
            doodleView.setPositionOffset(
                    Math.round(offsetX * scaleX),
                    Math.round(offsetY * scaleY));
            doodleView.setDoodleSizePercent(sizePercent);
        }
        if (gestureLayer != null) {
            gestureLayer.invalidate();
        }
    }

    private int readBatteryPercent() {
        try {
            android.os.BatteryManager manager =
                    (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
            int value = manager == null ? -1
                    : manager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return value >= 0 ? Math.min(100, value) : 100;
        } catch (Throwable ignored) {
            return 100;
        }
    }

    private void loadPreviewBackground() {
        final String profile = activeProfile;
        final int width = targetWidth;
        final int height = targetHeight;
        new Thread(new Runnable() {
            @Override
            public void run() {
                BackgroundResult result = loadCachedBackground(profile, width, height);
                if (result == null) {
                    try {
                        LockscreenWallpaperProbe.Result pulled =
                                LockscreenWallpaperProbe.read(
                                        DoodlePositionActivity.this, profile, width, height);
                        result = new BackgroundResult(
                                pulled.bitmap, pulled.sourceLabel());
                    } catch (Throwable ignored) {
                        // A grid/gradient remains available when Samsung protects the layer.
                    }
                }
                final BackgroundResult completed = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed || isFinishing()) {
                            recycle(completed == null ? null : completed.bitmap);
                            return;
                        }
                        if (completed == null || completed.bitmap == null) {
                            sourceLabel.setText("Preview: positioning grid (wallpaper unavailable)");
                            return;
                        }
                        recycle(previewBitmap);
                        previewBitmap = completed.bitmap;
                        wallpaperView.setImageBitmap(previewBitmap);
                        sourceLabel.setText("Preview: " + completed.label);
                    }
                });
            }
        }, "LLE-doodle-layout-background").start();
    }

    private BackgroundResult loadCachedBackground(String profile, int width, int height) {
        File touchShot = OverlayPrefs.touchBoxScreenshotFile(this, profile);
        Bitmap bitmap = decodePreviewFile(touchShot, width, height);
        if (bitmap != null) {
            return new BackgroundResult(bitmap, "cached lockscreen screenshot");
        }

        int effect = OverlayPrefs.unlockEffect(this);
        if (OverlayPrefs.importedEffectBackgroundEnabled(this, effect, profile)) {
            File imported = OverlayPrefs.importedEffectBackgroundFile(this, effect, profile);
            bitmap = decodePreviewFile(imported, width, height);
            if (bitmap != null) {
                return new BackgroundResult(bitmap, "direct wallpaper source");
            }
        }

        File captured = OverlayPrefs.effectBackgroundFile(this, effect, profile);
        bitmap = decodePreviewFile(captured, width, height);
        if (bitmap != null) {
            return new BackgroundResult(bitmap, "captured lockscreen background");
        }
        File legacyCaptured = OverlayPrefs.legacyPngEffectBackgroundFile(this, profile);
        bitmap = decodePreviewFile(legacyCaptured, width, height);
        if (bitmap != null) {
            return new BackgroundResult(bitmap, "legacy captured lockscreen background");
        }
        return null;
    }

    private Bitmap decodePreviewFile(File file, int width, int height) {
        if (file == null || !file.isFile() || file.length() <= 0L) {
            return null;
        }
        Argb8888BitmapStore.Info bounds = Argb8888BitmapStore.inspect(file);
        if (bounds == null) {
            return null;
        }
        int sample = 1;
        while (bounds.width / (sample * 2) >= width
                && bounds.height / (sample * 2) >= height) {
            sample *= 2;
        }
        try {
            return Argb8888BitmapStore.decode(file, Math.max(1, sample));
        } catch (OutOfMemoryError ignored) {
            return null;
        }
    }

    private GradientDrawable fallbackWallpaper() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                        Color.rgb(25, 46, 76),
                        Color.rgb(34, 101, 114),
                        Color.rgb(90, 54, 124)
                });
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private Button actionButton(String value, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : TEXT);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? BLUE : Color.rgb(241, 245, 249));
        background.setCornerRadius(dp(10));
        if (!primary) {
            background.setStroke(dp(1), Color.rgb(204, 214, 225));
        }
        button.setBackground(background);
        button.setOnClickListener(listener);
        return button;
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

    private LinearLayout.LayoutParams actionParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
        if (withStartMargin) {
            params.setMargins(dp(8), 0, 0, 0);
        }
        return params;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(7), 0, 0);
        return params;
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class PreviewHost extends FrameLayout {
        private int aspectWidth = 1;
        private int aspectHeight = 1;

        PreviewHost(Activity context) {
            super(context);
            setClipToOutline(false);
        }

        void setTargetAspect(int width, int height) {
            aspectWidth = Math.max(1, width);
            aspectHeight = Math.max(1, height);
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
            int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
            float targetRatio = aspectWidth / (float) aspectHeight;
            int width = availableWidth;
            int height = Math.round(width / targetRatio);
            if (height > availableHeight) {
                height = availableHeight;
                width = Math.round(height * targetRatio);
            }
            int exactWidth = MeasureSpec.makeMeasureSpec(Math.max(1, width), MeasureSpec.EXACTLY);
            int exactHeight = MeasureSpec.makeMeasureSpec(Math.max(1, height), MeasureSpec.EXACTLY);
            super.onMeasure(exactWidth, exactHeight);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            post(new Runnable() {
                @Override
                public void run() {
                    updateEditor(false);
                }
            });
        }
    }

    private final class GestureLayer extends View {
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float downX;
        private float downY;
        private int downOffsetX;
        private int downOffsetY;
        private float initialSpan;
        private int initialSize;
        private boolean pinching;

        GestureLayer(Activity context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setContentDescription("Doodle preview. Drag to move and pinch to resize.");
            gridPaint.setColor(Color.argb(72, 255, 255, 255));
            gridPaint.setStrokeWidth(dp(1));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            canvas.drawLine(width * 0.25f, 0f, width * 0.25f, height, gridPaint);
            canvas.drawLine(width * 0.5f, 0f, width * 0.5f, height, gridPaint);
            canvas.drawLine(width * 0.75f, 0f, width * 0.75f, height, gridPaint);
            canvas.drawLine(0f, height * 0.25f, width, height * 0.25f, gridPaint);
            canvas.drawLine(0f, height * 0.5f, width, height * 0.5f, gridPaint);
            canvas.drawLine(0f, height * 0.75f, width, height * 0.75f, gridPaint);

        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    downOffsetX = offsetX;
                    downOffsetY = offsetY;
                    pinching = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2) {
                        initialSpan = pointerSpan(event);
                        initialSize = sizePercent;
                        pinching = initialSpan > 0f;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() >= 2 && pinching) {
                        float span = pointerSpan(event);
                        if (span > 0f && initialSpan > 0f) {
                            sizePercent = OverlayPrefs.clampDoodleSizePercent(
                                    Math.round(initialSize * span / initialSpan));
                            updateEditor(true);
                        }
                        return true;
                    }
                    if (!pinching) {
                        float scaleX = getWidth() / (float) targetWidth;
                        float scaleY = getHeight() / (float) targetHeight;
                        offsetX = clampHorizontalOffset(
                                downOffsetX + Math.round((event.getX() - downX)
                                        / Math.max(0.001f, scaleX)));
                        offsetY = clampVerticalOffset(
                                downOffsetY + Math.round((event.getY() - downY)
                                        / Math.max(0.001f, scaleY)));
                        updateEditor(false);
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    pinching = false;
                    int remainingIndex = event.getActionIndex() == 0
                            && event.getPointerCount() > 1 ? 1 : 0;
                    downX = event.getX(remainingIndex);
                    downY = event.getY(remainingIndex);
                    downOffsetX = offsetX;
                    downOffsetY = offsetY;
                    return true;
                case MotionEvent.ACTION_UP:
                    performClick();
                    pinching = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    pinching = false;
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private float pointerSpan(MotionEvent event) {
            if (event.getPointerCount() < 2) {
                return 0f;
            }
            float dx = event.getX(1) - event.getX(0);
            float dy = event.getY(1) - event.getY(0);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }

    private static final class BackgroundResult {
        final Bitmap bitmap;
        final String label;

        BackgroundResult(Bitmap bitmap, String label) {
            this.bitmap = bitmap;
            this.label = label == null ? "lockscreen background" : label;
        }
    }
}
