package com.codex.s4unlockfx;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

public class EffectSelectorActivity extends Activity {
    private static final int REQUEST_PICK_WALLPAPER = 1001;
    private static final int SAMSUNG_TEAL = Color.rgb(4, 166, 184);
    private static final int SELECTED_GREEN = Color.rgb(76, 190, 96);
    private static final int DIVIDER = Color.rgb(214, 214, 214);

    private LegacyCanvasEffectView effectPreview;
    private MiniLockPreviewView lockPreview;
    private RadioButton[] modeButtons;
    private int pendingModeIndex;
    private int pendingWallpaperMode;
    private int pendingStockWallpaperIndex;
    private String pendingCustomWallpaperUri;
    private int previewEffectType = LegacyCanvasEffectView.EFFECT_NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPendingState();

        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 246, 246));

        root.addView(makeHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.rgb(246, 246, 246));
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        View previewPane = makePreviewPane();
        LinearLayout.LayoutParams previewParams = landscape
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360));
        content.addView(previewPane, previewParams);

        ScrollView listPane = makeListPane();
        LinearLayout.LayoutParams listParams = landscape
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        content.addView(listPane, listParams);

        setContentView(root);
        updateSelections();
        updatePreview();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_WALLPAPER || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        int flags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        pendingWallpaperMode = UnlockFxPrefs.WALLPAPER_MODE_CUSTOM;
        pendingCustomWallpaperUri = uri.toString();
        updateSelections();
        updatePreview();
        Toast.makeText(this, "Wallpaper selezionato", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout makeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);
        header.setBackgroundColor(SAMSUNG_TEAL);

        TextView cancel = makeHeaderButton("CANCEL");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(cancel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView save = makeHeaderButton("SAVE");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePendingState();
                startActivity(new Intent(EffectSelectorActivity.this, MainActivity.class));
            }
        });
        header.addView(save, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        return header;
    }

    private TextView makeHeaderButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(23f);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        return button;
    }

    private View makePreviewPane() {
        LinearLayout pane = new LinearLayout(this);
        pane.setGravity(Gravity.CENTER);
        pane.setPadding(dp(28), dp(28), dp(28), dp(28));
        pane.setBackgroundColor(Color.rgb(246, 246, 246));

        AspectPreviewFrame frame = new AspectPreviewFrame(this);
        frame.setBackgroundColor(Color.BLACK);
        lockPreview = new MiniLockPreviewView(this);
        frame.addView(lockPreview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        effectPreview = null;

        pane.addView(frame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        return pane;
    }

    private ScrollView makeListPane() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(Color.WHITE);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.WHITE);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        addSectionTitle(list, "Unlock effect");
        modeButtons = new RadioButton[UnlockFxPrefs.MODE_NAMES.length];
        for (int i = 0; i < UnlockFxPrefs.MODE_NAMES.length; i++) {
            addModeRow(list, i);
        }

        addSectionTitle(list, "Actions");
        addActionButton(list, "Open fullscreen tester", "Salva la selezione e apre la demo", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePendingState();
                startActivity(new Intent(EffectSelectorActivity.this, MainActivity.class));
            }
        });
        addActionButton(list, "Pick wallpaper", wallpaperActionSubtitle(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickWallpaperFromGallery();
            }
        });
        addActionButton(list, "Use default wallpaper", "Auto per effetto", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingWallpaperMode = UnlockFxPrefs.WALLPAPER_MODE_AUTO;
                pendingCustomWallpaperUri = null;
                updateSelections();
                updatePreview();
                Toast.makeText(EffectSelectorActivity.this, "Wallpaper auto per effetto", Toast.LENGTH_SHORT).show();
            }
        });
        return scroll;
    }

    private void addSectionTitle(LinearLayout list, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(Color.rgb(90, 90, 90));
        title.setTextSize(13f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(24), dp(14), dp(24), dp(6));
        list.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addModeRow(LinearLayout list, final int modeIndex) {
        RadioButton radio = makeRadioButton();
        modeButtons[modeIndex] = radio;
        View row = makeSelectableRow(
                UnlockFxPrefs.modeName(modeIndex),
                UnlockFxPrefs.modelNameForModeIndex(modeIndex),
                radio,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pendingModeIndex = UnlockFxPrefs.normalizeModeIndex(modeIndex);
                        updateSelections();
                        updatePreview();
                    }
                });
        list.addView(row);
        addDivider(list);
    }

    private View makeSelectableRow(String title, String subtitle, RadioButton radio, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), 0, dp(18), 0);
        row.setMinimumHeight(subtitle == null ? dp(62) : dp(72));
        row.setBackgroundColor(Color.WHITE);
        row.setClickable(true);
        row.setOnClickListener(listener);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.BLACK);
        titleView.setTextSize(20f);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(false);
        textBox.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (subtitle != null) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(Color.rgb(112, 112, 112));
            subtitleView.setTextSize(12f);
            subtitleView.setGravity(Gravity.CENTER_VERTICAL);
            textBox.addView(subtitleView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        row.addView(textBox, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));
        row.addView(radio, new LinearLayout.LayoutParams(dp(58), dp(58)));
        return row;
    }

    private void addActionButton(LinearLayout list, String title, String subtitle, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(title + "\n" + subtitle);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16f);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(SAMSUNG_TEAL);
        button.setClickable(true);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(68));
        params.leftMargin = dp(24);
        params.rightMargin = dp(24);
        params.topMargin = dp(10);
        params.bottomMargin = dp(4);
        list.addView(button, params);
    }

    private String wallpaperActionSubtitle() {
        return pendingWallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_CUSTOM && pendingCustomWallpaperUri != null
                ? "Custom gallery selezionato"
                : "Altrimenti usa il default dell'effetto";
    }

    private RadioButton makeRadioButton() {
        RadioButton radio = new RadioButton(this);
        radio.setClickable(false);
        radio.setFocusable(false);
        radio.setGravity(Gravity.CENTER);
        radio.setButtonTintList(new ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] {}
                },
                new int[] {
                        SELECTED_GREEN,
                        Color.rgb(116, 116, 116)
                }));
        return radio;
    }

    private void addDivider(LinearLayout list) {
        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1);
        params.leftMargin = dp(24);
        list.addView(divider, params);
    }

    private void loadPendingState() {
        SharedPreferences prefs = getSharedPreferences(UnlockFxPrefs.NAME, MODE_PRIVATE);
        pendingModeIndex = UnlockFxPrefs.normalizeModeIndex(prefs.getInt(UnlockFxPrefs.MODE_INDEX, 1));
        pendingWallpaperMode = prefs.getInt(UnlockFxPrefs.WALLPAPER_MODE, UnlockFxPrefs.WALLPAPER_MODE_AUTO);
        pendingStockWallpaperIndex = UnlockFxPrefs.normalizeStockWallpaperIndex(
                prefs.getInt(UnlockFxPrefs.STOCK_WALLPAPER_INDEX, 0));
        pendingCustomWallpaperUri = prefs.getString(UnlockFxPrefs.CUSTOM_WALLPAPER_URI, null);
        if (pendingWallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_STOCK) {
            pendingWallpaperMode = UnlockFxPrefs.WALLPAPER_MODE_AUTO;
        }
        if (pendingWallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_CUSTOM && pendingCustomWallpaperUri == null) {
            pendingWallpaperMode = UnlockFxPrefs.WALLPAPER_MODE_AUTO;
        }
    }

    private void savePendingState() {
        int normalizedMode = UnlockFxPrefs.normalizeModeIndex(pendingModeIndex);
        SharedPreferences.Editor editor = getSharedPreferences(UnlockFxPrefs.NAME, MODE_PRIVATE)
                .edit()
                .putInt(UnlockFxPrefs.MODE_INDEX, normalizedMode)
                .putString(UnlockFxPrefs.MODE_NAME, UnlockFxPrefs.modeName(normalizedMode))
                .putInt(UnlockFxPrefs.WALLPAPER_MODE, pendingWallpaperMode);
        if (pendingWallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_CUSTOM && pendingCustomWallpaperUri != null) {
            editor.putString(UnlockFxPrefs.CUSTOM_WALLPAPER_URI, pendingCustomWallpaperUri);
        } else {
            editor.remove(UnlockFxPrefs.CUSTOM_WALLPAPER_URI);
        }
        if (pendingWallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_STOCK) {
            editor.putInt(UnlockFxPrefs.STOCK_WALLPAPER_INDEX,
                    UnlockFxPrefs.normalizeStockWallpaperIndex(pendingStockWallpaperIndex));
        }
        editor.apply();
        Toast.makeText(this, "Salvato: " + UnlockFxPrefs.modeName(normalizedMode), Toast.LENGTH_SHORT).show();
    }

    private void updateSelections() {
        if (modeButtons != null) {
            for (int i = 0; i < modeButtons.length; i++) {
                modeButtons[i].setChecked(i == UnlockFxPrefs.normalizeModeIndex(pendingModeIndex));
            }
        }
    }

    private void updatePreview() {
        if (lockPreview == null) {
            return;
        }
        lockPreview.setPreview(
                pendingModeIndex,
                pendingWallpaperMode,
                pendingStockWallpaperIndex,
                pendingCustomWallpaperUri);
        previewEffectType = LegacyCanvasEffectView.EFFECT_NONE;
    }

    private int previewEffectForModeIndex(int modeIndex) {
        switch (UnlockFxPrefs.normalizeModeIndex(modeIndex)) {
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

    private void pickWallpaperFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AspectPreviewFrame extends FrameLayout {
        private static final float ASPECT = 360f / 640f;

        AspectPreviewFrame(Activity context) {
            super(context);
            setClipChildren(true);
            setClipToPadding(true);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
            int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
            if (maxWidth <= 0) {
                maxWidth = dpFallback(260);
            }
            if (maxHeight <= 0) {
                maxHeight = dpFallback(460);
            }
            int width = maxWidth;
            int height = Math.round(width / ASPECT);
            if (height > maxHeight) {
                height = maxHeight;
                width = Math.round(height * ASPECT);
            }
            setMeasuredDimension(width, height);
            int childWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int childHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.measure(childWidth, childHeight);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).layout(0, 0, getMeasuredWidth(), getMeasuredHeight());
            }
        }

        private int dpFallback(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }

    private final class MiniLockPreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private Bitmap wallpaperBitmap;
        private String wallpaperKey;
        private int modeIndex;
        private int wallpaperMode;
        private int stockWallpaperIndex;
        private String customWallpaperUri;

        MiniLockPreviewView(Activity context) {
            super(context);
        }

        void setPreview(int nextModeIndex, int nextWallpaperMode, int nextStockWallpaperIndex, String nextCustomWallpaperUri) {
            modeIndex = UnlockFxPrefs.normalizeModeIndex(nextModeIndex);
            wallpaperMode = nextWallpaperMode;
            stockWallpaperIndex = UnlockFxPrefs.normalizeStockWallpaperIndex(nextStockWallpaperIndex);
            customWallpaperUri = nextCustomWallpaperUri;
            updateWallpaperBitmap();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawWallpaper(canvas);
            drawLockChrome(canvas);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            recycleWallpaper();
        }

        private void updateWallpaperBitmap() {
            String nextKey = wallpaperKeyForCurrentState();
            if (nextKey.equals(wallpaperKey)) {
                return;
            }
            wallpaperKey = nextKey;
            recycleWallpaper();
            wallpaperBitmap = loadWallpaperBitmap();
        }

        private String wallpaperKeyForCurrentState() {
            if (wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_CUSTOM && customWallpaperUri != null) {
                return "custom:" + customWallpaperUri;
            }
            if (wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_STOCK) {
                return "stock:" + stockWallpaperIndex;
            }
            return "auto:" + modeIndex;
        }

        private Bitmap loadWallpaperBitmap() {
            if (wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_CUSTOM && customWallpaperUri != null) {
                InputStream stream = null;
                try {
                    stream = getContentResolver().openInputStream(Uri.parse(customWallpaperUri));
                    return BitmapFactory.decodeStream(stream);
                } catch (Throwable ignored) {
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            String resourceName = wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_STOCK
                    ? UnlockFxPrefs.stockWallpaperResourceName(stockWallpaperIndex)
                    : UnlockFxPrefs.defaultWallpaperResourceNameForModeIndex(modeIndex);
            int resId = getResources().getIdentifier(resourceName, "drawable", getPackageName());
            if (resId == 0) {
                return null;
            }
            return BitmapFactory.decodeResource(getResources(), resId);
        }

        private void recycleWallpaper() {
            if (wallpaperBitmap != null && !wallpaperBitmap.isRecycled()) {
                wallpaperBitmap.recycle();
            }
            wallpaperBitmap = null;
        }

        private void drawWallpaper(Canvas canvas) {
            if (wallpaperBitmap != null && !wallpaperBitmap.isRecycled()) {
                float scale = Math.max(
                        getWidth() / (float) wallpaperBitmap.getWidth(),
                        getHeight() / (float) wallpaperBitmap.getHeight());
                float width = wallpaperBitmap.getWidth() * scale;
                float height = wallpaperBitmap.getHeight() * scale;
                RectF dst = new RectF(
                        (getWidth() - width) * 0.5f,
                        (getHeight() - height) * 0.5f,
                        (getWidth() + width) * 0.5f,
                        (getHeight() + height) * 0.5f);
                canvas.drawBitmap(wallpaperBitmap, null, dst, paint);
                return;
            }
            paint.setShader(new LinearGradient(
                    0f,
                    0f,
                    0f,
                    getHeight(),
                    Color.rgb(10, 137, 156),
                    Color.rgb(7, 82, 130),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
            paint.setShader(null);
        }

        private void drawLockChrome(Canvas canvas) {
            float width = Math.max(1f, getWidth());
            float height = Math.max(1f, getHeight());
            paint.setShader(null);
            paint.setFakeBoldText(false);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.argb(235, 255, 255, 255));
            paint.setTextSize(width * 0.18f);
            canvas.drawText("12:45", width * 0.5f, height * 0.17f, paint);
            paint.setTextSize(width * 0.052f);
            paint.setColor(Color.argb(220, 255, 255, 255));
            canvas.drawText("Tue, 28 October", width * 0.5f, height * 0.22f, paint);

            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(width * 0.04f);
            paint.setColor(Color.argb(230, 255, 255, 255));
            canvas.drawText("70%", width * 0.93f, height * 0.035f, paint);

            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(width * 0.038f);
            paint.setColor(Color.argb(180, 255, 255, 255));
            canvas.drawText(UnlockFxPrefs.modelNameForModeIndex(modeIndex), width * 0.08f, height * 0.92f, paint);
        }
    }
}
