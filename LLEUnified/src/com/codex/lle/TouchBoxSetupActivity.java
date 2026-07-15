package com.codex.lle;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class TouchBoxSetupActivity extends Activity {
    public static final String EXTRA_START_CAPTURE = "start_capture";

    private static final long WAITING_POLL_MS = 700L;
    private static final int GRACE_BLUE = Color.rgb(20, 126, 245);
    private static final int GRACE_BACKGROUND = Color.rgb(244, 246, 249);
    private static final int GRACE_TEXT = Color.rgb(36, 42, 50);
    private static final int GRACE_MUTED = Color.rgb(104, 114, 128);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable waitingPollRunnable = new Runnable() {
        @Override
        public void run() {
            refreshWaitingState();
        }
    };

    private Bitmap screenshotBitmap;
    private final ArrayList<Bitmap> overviewBitmaps = new ArrayList<Bitmap>();
    private PinEditorView editorView;
    private TextView statusLabel;
    private TextView waitingStatusLabel;
    private boolean waitingForCapture;
    private boolean showingFoldOverview;
    private boolean foldMode;
    private String selectedProfile = FoldDisplayTarget.PROFILE_SINGLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        setImmersive();

        foldMode = OverlayPrefs.foldModeEnabled(this);
        selectedProfile = foldMode
                ? FoldDisplayTarget.cacheProfileForContext(this)
                : FoldDisplayTarget.PROFILE_SINGLE;
        OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this, selectedProfile);

        boolean startCapture = getIntent() == null
                || getIntent().getBooleanExtra(EXTRA_START_CAPTURE, true);
        if (foldMode && !isCapturePending()) {
            showFoldOverview();
            return;
        }
        if (foldMode && isCapturePending()) {
            selectedProfile = pendingCaptureProfile();
        }
        if (!showEditorFromCachedScreenshot() && startCapture) {
            requestScreenshotCapture();
            showWaitingView();
        } else if (screenshotBitmap == null) {
            showWaitingView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setImmersive();
        if (waitingForCapture) {
            refreshWaitingState();
        } else if (showingFoldOverview) {
            showFoldOverview();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(waitingPollRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(waitingPollRunnable);
        recycleScreenshot();
        recycleOverviewBitmaps();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (foldMode && !showingFoldOverview) {
            cancelPendingCaptureForSelectedProfile();
            showFoldOverview();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersive();
        }
    }

    private void setImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private boolean showEditorFromCachedScreenshot() {
        Bitmap bitmap = loadCachedScreenshot();
        if (bitmap == null) {
            return false;
        }
        recycleScreenshot();
        screenshotBitmap = bitmap;
        waitingForCapture = false;
        handler.removeCallbacks(waitingPollRunnable);
        showEditorView();
        return true;
    }

    private Bitmap loadCachedScreenshot() {
        File file = bestWizardScreenshotFile(selectedProfile);
        if (!file.exists() || file.length() <= 0L) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void requestScreenshotCapture() {
        SharedPreferences prefs = OverlayPrefs.get(this);
        int nextRequestId = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_REQUEST_ID, 0) + 1;
        prefs.edit()
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_REQUEST_ID, nextRequestId)
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                        OverlayPrefs.TOUCH_BOX_CAPTURE_REQUESTED)
                .putString(OverlayPrefs.TOUCH_BOX_CAPTURE_PROFILE, selectedProfile)
                .remove(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR)
                .apply();
    }

    private void showWaitingView() {
        showingFoldOverview = false;
        recycleOverviewBitmaps();
        waitingForCapture = true;
        LinearLayout root = graceRoot();
        root.addView(appBar("Lockscreen capture", "Touch box wizard"));
        addProfileSlider(root);

        LinearLayout card = panel();
        TextView title = titleText("Capture needed");
        card.addView(title);

        TextView body = bodyText("Select the " + profileLabel(selectedProfile)
                + " panel, lock the phone, show the lockscreen for about 2 seconds, then unlock and return here.");
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.setMargins(0, dp(10), 0, dp(18));
        card.addView(body, bodyParams);

        waitingStatusLabel = bodyText("");
        waitingStatusLabel.setTextColor(GRACE_BLUE);
        waitingStatusLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(waitingStatusLabel);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        buttonRowParams.setMargins(0, dp(22), 0, 0);
        card.addView(buttons, buttonRowParams);

        buttons.addView(materialButton("Cancel", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelOrReturnToOverview();
            }
        }), weightedParams(false));
        buttons.addView(materialButton("Retry", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestScreenshotCapture();
                refreshWaitingState();
            }
        }), weightedParams(true));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(dp(18), dp(28), dp(18), 0);
        root.addView(card, cardParams);
        setContentView(root);
        refreshWaitingState();
    }

    private void refreshWaitingState() {
        if (!waitingForCapture) {
            return;
        }
        if (showEditorFromCachedScreenshot()) {
            return;
        }
        if (waitingStatusLabel != null) {
            waitingStatusLabel.setText(captureStatusText());
        }
        handler.removeCallbacks(waitingPollRunnable);
        handler.postDelayed(waitingPollRunnable, WAITING_POLL_MS);
    }

    private String captureStatusText() {
        SharedPreferences prefs = OverlayPrefs.get(this);
        int state = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                OverlayPrefs.TOUCH_BOX_CAPTURE_IDLE);
        if (state == OverlayPrefs.TOUCH_BOX_CAPTURE_WAITING_LOCKSCREEN) {
            return "Waiting on lockscreen...";
        }
        if (state == OverlayPrefs.TOUCH_BOX_CAPTURE_CAPTURING) {
            return "Capturing clean screenshot...";
        }
        if (state == OverlayPrefs.TOUCH_BOX_CAPTURE_FAILED) {
            String error = prefs.getString(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR, "");
            return error.length() == 0 ? "Capture failed" : error;
        }
        return "Waiting for accessibility service...";
    }

    private void showEditorView() {
        showingFoldOverview = false;
        recycleOverviewBitmaps();
        LinearLayout root = graceRoot();
        root.addView(appBar("Touch box editor",
                profileLabel(selectedProfile) + " lockscreen screenshot"));
        addProfileSlider(root);

        editorView = new PinEditorView(this);
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        root.addView(editorView, editorParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(14), dp(10), dp(14), dp(14));
        bottom.setBackgroundColor(Color.WHITE);
        root.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        statusLabel = bodyText("");
        statusLabel.setTypeface(Typeface.MONOSPACE);
        bottom.addView(statusLabel);

        TextView hint = bodyText("Add areas, drag inside to move, or drag a corner to resize. Edges snap within 20 px.");
        hint.setTextSize(13f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(0, dp(4), 0, dp(8));
        bottom.addView(hint, hintParams);

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(rowOne, rowParams());
        rowOne.addView(materialButton("Add area", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editorView.addArea();
            }
        }), weightedParams(false));
        rowOne.addView(materialButton("Remove area", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editorView.removeSelectedArea();
            }
        }), weightedParams(true));

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(rowTwo, rowParams());
        rowTwo.addView(materialButton("Reset areas", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editorView.resetAreasFromSavedBox();
            }
        }), weightedParams(false));
        rowTwo.addView(materialButton("New shot", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreshCapture();
            }
        }), weightedParams(true));

        LinearLayout rowThree = new LinearLayout(this);
        rowThree.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(rowThree, rowParams());
        rowThree.addView(materialButton("Cancel", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelOrReturnToOverview();
            }
        }), weightedParams(false));
        rowThree.addView(materialButton("Save areas", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentBox();
            }
        }), weightedParams(true));

        setContentView(root);
        editorView.resetAreasFromSavedBox();
    }

    private void startFreshCapture() {
        File file = OverlayPrefs.touchBoxScreenshotFile(this, selectedProfile);
        if (file.exists()) {
            file.delete();
        }
        recycleScreenshot();
        requestScreenshotCapture();
        showWaitingView();
    }

    private void saveCurrentBox() {
        ArrayList<Rect> boxes = editorView == null ? null : editorView.currentRoundedBoxes();
        if (boxes == null || boxes.isEmpty()) {
            Toast.makeText(this, "Add at least one area", Toast.LENGTH_SHORT).show();
            return;
        }
        OverlayPrefs.saveTouchBoxRegionsOutward(this, selectedProfile, boxes,
                screenshotBitmap.getWidth(), screenshotBitmap.getHeight());
        Toast.makeText(this, profileLabel(selectedProfile)
                + " touch areas saved", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        if (foldMode) {
            showFoldOverview();
        } else {
            finish();
        }
    }

    private void addProfileSlider(LinearLayout root) {
        if (!foldMode) {
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(20), dp(8), dp(20), dp(8));
        row.setBackgroundColor(Color.WHITE);

        TextView cover = bodyText("Cover");
        cover.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(cover, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Switch slider = new Switch(this);
        slider.setChecked(FoldDisplayTarget.PROFILE_MAIN.equals(selectedProfile));
        slider.setContentDescription("Switch between cover and main touch boxes");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            slider.setShowText(false);
        }
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                dp(72), dp(48));
        sliderParams.setMargins(dp(12), 0, dp(12), 0);
        row.addView(slider, sliderParams);

        TextView main = bodyText("Main");
        main.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.addView(main, new LinearLayout.LayoutParams(0, dp(48), 1f));

        slider.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switchProfile(isChecked
                        ? FoldDisplayTarget.PROFILE_MAIN
                        : FoldDisplayTarget.PROFILE_COVER);
            }
        });
        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void switchProfile(String profile) {
        String normalized = FoldDisplayTarget.normalizeProfile(profile);
        if (normalized.equals(selectedProfile)) {
            return;
        }
        if (waitingForCapture) {
            cancelPendingCaptureForSelectedProfile();
        }
        if (editorView != null && screenshotBitmap != null) {
            ArrayList<Rect> boxes = editorView.currentRoundedBoxes();
            if (boxes != null && !boxes.isEmpty()) {
                OverlayPrefs.saveTouchBoxRegionsOutward(this, selectedProfile, boxes,
                        screenshotBitmap.getWidth(), screenshotBitmap.getHeight());
            }
        }
        selectedProfile = normalized;
        editorView = null;
        recycleScreenshot();
        if (!showEditorFromCachedScreenshot()) {
            requestScreenshotCapture();
            showWaitingView();
        }
    }

    private void showFoldOverview() {
        waitingForCapture = false;
        showingFoldOverview = true;
        editorView = null;
        handler.removeCallbacks(waitingPollRunnable);
        recycleScreenshot();
        recycleOverviewBitmaps();

        LinearLayout root = graceRoot();
        root.addView(appBar("Dual touch box setup", "Cover + main panel"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView explanation = bodyText("Each panel has its own lockscreen image and touch areas. "
                + "Configure either panel below; captures only run when that physical panel is active and locked.");
        explanation.setBackground(overviewInfoBackground());
        explanation.setPadding(dp(16), dp(14), dp(16), dp(14));
        content.addView(explanation, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(profileOverviewCard("Cover panel", FoldDisplayTarget.PROFILE_COVER),
                overviewCardParams());
        content.addView(profileOverviewCard("Main panel", FoldDisplayTarget.PROFILE_MAIN),
                overviewCardParams());
        content.addView(materialButton("Close", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }), overviewButtonParams());
        setContentView(root);
    }

    private View profileOverviewCard(String label, final String profile) {
        LinearLayout card = panel();
        card.addView(titleText(label));

        final File dedicatedScreenshot = OverlayPrefs.touchBoxScreenshotFile(this, profile);
        final File screenshot = bestWizardScreenshotFile(profile);
        final boolean hasScreenshot = screenshot.exists() && screenshot.length() > 0L;
        ArrayList<Rect> areas = OverlayPrefs.touchBoxRegions(this, profile);
        TextView status = bodyText(profileOverviewStatus(
                screenshot, dedicatedScreenshot.equals(screenshot), areas));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(6), 0, dp(10));
        card.addView(status, statusParams);

        Bitmap preview = hasScreenshot ? decodeOverviewBitmap(screenshot) : null;
        if (preview != null) {
            overviewBitmaps.add(preview);
            ImageView image = new ImageView(this);
            image.setBackgroundColor(Color.BLACK);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageBitmap(preview);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(210));
            imageParams.setMargins(0, 0, 0, dp(12));
            card.addView(image, imageParams);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(buttons, rowParams());
        if (hasScreenshot) {
            buttons.addView(materialButton("Edit areas", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openProfile(profile, false);
                }
            }), weightedParams(false));
            buttons.addView(materialButton("New shot", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openProfile(profile, true);
                }
            }), weightedParams(true));
        } else {
            buttons.addView(materialButton("Capture " + profileLabel(profile), true,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openProfile(profile, true);
                        }
                    }), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        }
        return card;
    }

    private LinearLayout.LayoutParams overviewCardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(16), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams overviewButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(16), 0, 0);
        return params;
    }

    private String profileOverviewStatus(File screenshot, boolean dedicated,
            ArrayList<Rect> areas) {
        String areaStatus = areas.size() + (areas.size() == 1 ? " touch area" : " touch areas");
        if (screenshot == null || !screenshot.exists() || screenshot.length() <= 0L) {
            return "No screenshot yet | " + areaStatus;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(screenshot.getAbsolutePath(), bounds);
        String dimensions = bounds.outWidth > 0 && bounds.outHeight > 0
                ? bounds.outWidth + " x " + bounds.outHeight
                : "unreadable image";
        return (dedicated ? "Wizard screenshot: " : "Effect screenshot reused: ")
                + dimensions + " | " + areaStatus;
    }

    private File bestWizardScreenshotFile(String profile) {
        File dedicated = OverlayPrefs.touchBoxScreenshotFile(this, profile);
        if (dedicated.exists() && dedicated.length() > 0L) {
            return dedicated;
        }
        File effectCache = OverlayPrefs.effectBackgroundFile(
                this, OverlayPrefs.unlockEffect(this), profile);
        if (effectCache.exists() && effectCache.length() > 0L) {
            return effectCache;
        }
        return dedicated;
    }

    private Bitmap decodeOverviewBitmap(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > 720) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void openProfile(String profile, boolean freshCapture) {
        selectedProfile = FoldDisplayTarget.normalizeProfile(profile);
        showingFoldOverview = false;
        recycleOverviewBitmaps();
        if (freshCapture) {
            File file = OverlayPrefs.touchBoxScreenshotFile(this, selectedProfile);
            if (file.exists() && !file.delete()) {
                Toast.makeText(this, "Could not replace old screenshot", Toast.LENGTH_SHORT).show();
            }
            requestScreenshotCapture();
            showWaitingView();
            return;
        }
        if (!showEditorFromCachedScreenshot()) {
            requestScreenshotCapture();
            showWaitingView();
        }
    }

    private void cancelOrReturnToOverview() {
        cancelPendingCaptureForSelectedProfile();
        if (foldMode) {
            showFoldOverview();
        } else {
            finish();
        }
    }

    private boolean isCapturePending() {
        SharedPreferences prefs = OverlayPrefs.get(this);
        int state = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE,
                OverlayPrefs.TOUCH_BOX_CAPTURE_IDLE);
        int requestId = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_REQUEST_ID, 0);
        int resultId = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_RESULT_ID, 0);
        return requestId > 0 && requestId != resultId
                && (state == OverlayPrefs.TOUCH_BOX_CAPTURE_REQUESTED
                || state == OverlayPrefs.TOUCH_BOX_CAPTURE_WAITING_LOCKSCREEN
                || state == OverlayPrefs.TOUCH_BOX_CAPTURE_CAPTURING);
    }

    private String pendingCaptureProfile() {
        return FoldDisplayTarget.normalizeProfile(OverlayPrefs.get(this).getString(
                OverlayPrefs.TOUCH_BOX_CAPTURE_PROFILE, selectedProfile));
    }

    private void cancelPendingCaptureForSelectedProfile() {
        if (!isCapturePending() || !selectedProfile.equals(pendingCaptureProfile())) {
            return;
        }
        SharedPreferences prefs = OverlayPrefs.get(this);
        int requestId = prefs.getInt(OverlayPrefs.TOUCH_BOX_CAPTURE_REQUEST_ID, 0);
        prefs.edit()
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_RESULT_ID, requestId)
                .putInt(OverlayPrefs.TOUCH_BOX_CAPTURE_STATE, OverlayPrefs.TOUCH_BOX_CAPTURE_IDLE)
                .remove(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR)
                .apply();
        waitingForCapture = false;
        handler.removeCallbacks(waitingPollRunnable);
    }

    private void recycleOverviewBitmaps() {
        for (Bitmap bitmap : overviewBitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        overviewBitmaps.clear();
    }

    private String profileLabel(String profile) {
        if (FoldDisplayTarget.PROFILE_COVER.equals(profile)) {
            return "cover";
        }
        if (FoldDisplayTarget.PROFILE_MAIN.equals(profile)) {
            return "main";
        }
        return "current";
    }

    private LinearLayout graceRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(GRACE_BACKGROUND);
        return root;
    }

    private View appBar(String title, String subtitle) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(16), dp(20), dp(12));
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(3));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(GRACE_TEXT);
        titleView.setTextSize(22f);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(GRACE_MUTED);
        subtitleView.setTextSize(14f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(2), 0, 0);
        bar.addView(subtitleView, subtitleParams);
        return bar;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(8));
        panel.setBackground(background);
        panel.setElevation(dp(2));
        return panel;
    }

    private GradientDrawable overviewInfoBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), Color.rgb(224, 229, 235));
        return background;
    }

    private TextView titleText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(GRACE_TEXT);
        view.setTextSize(20f);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(GRACE_MUTED);
        view.setTextSize(15f);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private Button materialButton(String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14f);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setOnClickListener(listener);

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(6));
        background.setColor(primary ? GRACE_BLUE : Color.WHITE);
        background.setStroke(dp(1), primary ? GRACE_BLUE : Color.rgb(214, 220, 228));
        button.setBackground(background);
        button.setTextColor(primary ? Color.WHITE : GRACE_TEXT);
        return button;
    }

    private LinearLayout.LayoutParams weightedParams(boolean withLeftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f);
        if (withLeftMargin) {
            params.setMargins(dp(8), 0, 0, 0);
        }
        return params;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44));
        params.setMargins(0, dp(6), 0, 0);
        return params;
    }

    private void recycleScreenshot() {
        if (screenshotBitmap != null && !screenshotBitmap.isRecycled()) {
            screenshotBitmap.recycle();
        }
        screenshotBitmap = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class PinEditorView extends View {
        private static final int DRAG_NONE = 0;
        private static final int DRAG_MOVE = 1;
        private static final int DRAG_TOP_LEFT = 2;
        private static final int DRAG_TOP_RIGHT = 3;
        private static final int DRAG_BOTTOM_RIGHT = 4;
        private static final int DRAG_BOTTOM_LEFT = 5;
        private static final int SNAP_EDGE_PX = 20;
        private static final int MAX_AREAS = 4;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageRect = new RectF();
        private final RectF boxRect = new RectF();
        private final ArrayList<RectF> areas = new ArrayList<RectF>();
        private int selectedArea = -1;
        private int dragMode = DRAG_NONE;
        private float lastScreenX;
        private float lastScreenY;

        PinEditorView(Context context) {
            super(context);
            setBackgroundColor(Color.rgb(18, 20, 24));
            setWillNotDraw(false);
            setClickable(true);
        }

        void resetAreasFromSavedBox() {
            areas.clear();
            for (Rect box : savedOrDefaultAreas()) {
                areas.add(new RectF(box));
            }
            selectedArea = areas.isEmpty() ? -1 : areas.size() - 1;
            updateStatus();
            invalidate();
        }

        void addArea() {
            if (screenshotBitmap == null || areas.size() >= MAX_AREAS) {
                Toast.makeText(TouchBoxSetupActivity.this,
                        "Maximum " + MAX_AREAS + " areas", Toast.LENGTH_SHORT).show();
                return;
            }
            float size = Math.min(Math.min(screenshotBitmap.getWidth(),
                    screenshotBitmap.getHeight()) * 0.24f, dp(240));
            size = Math.max(dp(96), size);
            float centerX = screenshotBitmap.getWidth() * 0.5f;
            float centerY = screenshotBitmap.getHeight() * 0.62f;
            float offset = areas.size() * SNAP_EDGE_PX;
            RectF area = new RectF(centerX - size / 2f + offset,
                    centerY - size / 2f + offset,
                    centerX + size / 2f + offset,
                    centerY + size / 2f + offset);
            keepInsideScreen(area);
            areas.add(area);
            selectedArea = areas.size() - 1;
            updateStatus();
            invalidate();
        }

        void removeSelectedArea() {
            if (selectedArea < 0 || selectedArea >= areas.size()) {
                Toast.makeText(TouchBoxSetupActivity.this,
                        "Select an area first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (areas.size() <= 1) {
                Toast.makeText(TouchBoxSetupActivity.this,
                        "Keep at least one area", Toast.LENGTH_SHORT).show();
                return;
            }
            areas.remove(selectedArea);
            selectedArea = Math.min(selectedArea, areas.size() - 1);
            updateStatus();
            invalidate();
        }

        ArrayList<Rect> currentRoundedBoxes() {
            ArrayList<Rect> boxes = new ArrayList<Rect>();
            if (areas.isEmpty() || screenshotBitmap == null) {
                return boxes;
            }
            int imageWidth = screenshotBitmap.getWidth();
            int imageHeight = screenshotBitmap.getHeight();
            int minSize = dp(48);
            for (RectF area : areas) {
                int left = OverlayPrefs.roundTouchCoordinateDown((int) Math.floor(area.left));
                int top = OverlayPrefs.roundTouchCoordinateDown((int) Math.floor(area.top));
                int right = OverlayPrefs.roundTouchCoordinateUp((int) Math.ceil(area.right));
                int bottom = OverlayPrefs.roundTouchCoordinateUp((int) Math.ceil(area.bottom));
                left = clamp(left, 0, Math.max(0, imageWidth - minSize));
                top = clamp(top, 0, Math.max(0, imageHeight - minSize));
                right = clamp(right, left + minSize, imageWidth);
                bottom = clamp(bottom, top + minSize, imageHeight);
                boxes.add(new Rect(left, top, right, bottom));
            }
            return boxes;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (screenshotBitmap == null || screenshotBitmap.isRecycled()) {
                return;
            }
            updateImageRect();
            canvas.drawColor(Color.rgb(18, 20, 24));
            canvas.drawBitmap(screenshotBitmap, null, imageRect, paint);

            ArrayList<Rect> boxes = currentRoundedBoxes();
            for (int i = 0; i < boxes.size(); i++) {
                Rect box = boxes.get(i);
                boxRect.set(
                        screenToViewX(box.left),
                        screenToViewY(box.top),
                        screenToViewX(box.right),
                        screenToViewY(box.bottom));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(i == selectedArea
                        ? Color.argb(72, 20, 126, 245)
                        : Color.argb(42, 20, 126, 245));
                paint.setPathEffect(null);
                canvas.drawRect(boxRect, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(i == selectedArea ? GRACE_BLUE : Color.argb(190, 116, 215, 255));
                paint.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(5)}, 0));
                canvas.drawRect(boxRect, paint);
                paint.setPathEffect(null);
                if (i == selectedArea) {
                    drawAreaHandles(canvas, box);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event == null || screenshotBitmap == null || screenshotBitmap.isRecycled()) {
                return true;
            }
            updateImageRect();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                float screenX = viewToScreenX(event.getX());
                float screenY = viewToScreenY(event.getY());
                int hitArea = hitArea(screenX, screenY);
                if (hitArea >= 0) {
                    selectedArea = hitArea;
                    dragMode = hitHandle(areas.get(hitArea), screenX, screenY);
                    if (dragMode == DRAG_NONE) {
                        dragMode = DRAG_MOVE;
                    }
                    lastScreenX = screenX;
                    lastScreenY = screenY;
                } else {
                    selectedArea = -1;
                    dragMode = DRAG_NONE;
                }
                updateStatus();
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && dragMode != DRAG_NONE
                    && selectedArea >= 0) {
                float screenX = viewToScreenX(event.getX());
                float screenY = viewToScreenY(event.getY());
                updateDraggedArea(areas.get(selectedArea), screenX, screenY);
                lastScreenX = screenX;
                lastScreenY = screenY;
                updateStatus();
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_UP && selectedArea >= 0
                        && dragMode != DRAG_NONE) {
                    snapAreaToEdges(areas.get(selectedArea));
                    updateStatus();
                    invalidate();
                }
                dragMode = DRAG_NONE;
                performClick();
                return true;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private ArrayList<Rect> savedOrDefaultAreas() {
            int imageWidth = screenshotBitmap.getWidth();
            int imageHeight = screenshotBitmap.getHeight();
            int minSize = dp(48);
            ArrayList<Rect> result = new ArrayList<Rect>();
            for (Rect source : OverlayPrefs.touchBoxRegions(TouchBoxSetupActivity.this,
                    selectedProfile, imageWidth, imageHeight)) {
                int left = clamp(source.left, 0, Math.max(0, imageWidth - minSize));
                int top = clamp(source.top, 0, Math.max(0, imageHeight - minSize));
                int right = clamp(source.right, left + minSize, imageWidth);
                int bottom = clamp(source.bottom, top + minSize, imageHeight);
                result.add(new Rect(left, top, right, bottom));
            }
            return result;
        }

        private void drawAreaHandles(Canvas canvas, Rect box) {
            float[] xs = {box.left, box.right, box.right, box.left};
            float[] ys = {box.top, box.top, box.bottom, box.bottom};
            for (int i = 0; i < xs.length; i++) {
                float x = screenToViewX(xs[i]);
                float y = screenToViewY(ys[i]);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(GRACE_BLUE);
                canvas.drawCircle(x, y, dp(8), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, dp(8), paint);
            }
        }

        private int hitArea(float screenX, float screenY) {
            for (int i = areas.size() - 1; i >= 0; i--) {
                RectF area = areas.get(i);
                if (hitHandle(area, screenX, screenY) != DRAG_NONE
                        || area.contains(screenX, screenY)) {
                    return i;
                }
            }
            return -1;
        }

        private int hitHandle(RectF area, float x, float y) {
            float screenRadius = dp(26) / Math.max(0.01f,
                    imageRect.width() / screenshotBitmap.getWidth());
            float radiusSquared = screenRadius * screenRadius;
            if (distanceSquared(x, y, area.left, area.top) <= radiusSquared) {
                return DRAG_TOP_LEFT;
            }
            if (distanceSquared(x, y, area.right, area.top) <= radiusSquared) {
                return DRAG_TOP_RIGHT;
            }
            if (distanceSquared(x, y, area.right, area.bottom) <= radiusSquared) {
                return DRAG_BOTTOM_RIGHT;
            }
            if (distanceSquared(x, y, area.left, area.bottom) <= radiusSquared) {
                return DRAG_BOTTOM_LEFT;
            }
            return DRAG_NONE;
        }

        private float distanceSquared(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2;
            float dy = y1 - y2;
            return dx * dx + dy * dy;
        }

        private void updateDraggedArea(RectF area, float screenX, float screenY) {
            float minSize = dp(48);
            if (dragMode == DRAG_MOVE) {
                area.offset(screenX - lastScreenX, screenY - lastScreenY);
                keepInsideScreen(area);
                return;
            }
            if (dragMode == DRAG_TOP_LEFT || dragMode == DRAG_BOTTOM_LEFT) {
                area.left = clampFloat(screenX, 0f, area.right - minSize);
            }
            if (dragMode == DRAG_TOP_RIGHT || dragMode == DRAG_BOTTOM_RIGHT) {
                area.right = clampFloat(screenX, area.left + minSize,
                        screenshotBitmap.getWidth());
            }
            if (dragMode == DRAG_TOP_LEFT || dragMode == DRAG_TOP_RIGHT) {
                area.top = clampFloat(screenY, 0f, area.bottom - minSize);
            }
            if (dragMode == DRAG_BOTTOM_LEFT || dragMode == DRAG_BOTTOM_RIGHT) {
                area.bottom = clampFloat(screenY, area.top + minSize,
                        screenshotBitmap.getHeight());
            }
        }

        private void keepInsideScreen(RectF area) {
            float dx = area.left < 0f ? -area.left
                    : area.right > screenshotBitmap.getWidth()
                    ? screenshotBitmap.getWidth() - area.right : 0f;
            float dy = area.top < 0f ? -area.top
                    : area.bottom > screenshotBitmap.getHeight()
                    ? screenshotBitmap.getHeight() - area.bottom : 0f;
            area.offset(dx, dy);
        }

        private void snapAreaToEdges(RectF area) {
            if (area.left <= SNAP_EDGE_PX) {
                area.left = 0f;
            }
            if (screenshotBitmap.getWidth() - area.right <= SNAP_EDGE_PX) {
                area.right = screenshotBitmap.getWidth();
            }
            if (area.top <= SNAP_EDGE_PX) {
                area.top = 0f;
            }
            if (screenshotBitmap.getHeight() - area.bottom <= SNAP_EDGE_PX) {
                area.bottom = screenshotBitmap.getHeight();
            }
        }

        private float clampFloat(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private void updateImageRect() {
            int width = Math.max(1, getWidth());
            int height = Math.max(1, getHeight());
            float scale = Math.min(width / (float) screenshotBitmap.getWidth(),
                    height / (float) screenshotBitmap.getHeight());
            float imageWidth = screenshotBitmap.getWidth() * scale;
            float imageHeight = screenshotBitmap.getHeight() * scale;
            float left = (width - imageWidth) / 2f;
            float top = (height - imageHeight) / 2f;
            imageRect.set(left, top, left + imageWidth, top + imageHeight);
        }

        private float viewToScreenX(float x) {
            float screenX = (x - imageRect.left) / imageRect.width() * screenshotBitmap.getWidth();
            return clampFloat(screenX, 0f, screenshotBitmap.getWidth());
        }

        private float viewToScreenY(float y) {
            float screenY = (y - imageRect.top) / imageRect.height() * screenshotBitmap.getHeight();
            return clampFloat(screenY, 0f, screenshotBitmap.getHeight());
        }

        private float screenToViewX(float x) {
            return imageRect.left + x / screenshotBitmap.getWidth() * imageRect.width();
        }

        private float screenToViewY(float y) {
            return imageRect.top + y / screenshotBitmap.getHeight() * imageRect.height();
        }

        private void updateStatus() {
            if (statusLabel == null) {
                return;
            }
            ArrayList<Rect> boxes = currentRoundedBoxes();
            if (boxes.isEmpty()) {
                statusLabel.setText("Areas 0");
                return;
            }
            int index = selectedArea >= 0 && selectedArea < boxes.size()
                    ? selectedArea : 0;
            Rect box = boxes.get(index);
            statusLabel.setText("Areas " + boxes.size() + "   Selected " + (index + 1)
                    + "   " + box.left + "," + box.top
                    + " - " + box.right + "," + box.bottom
                    + "   " + (box.right - box.left)
                    + " x " + (box.bottom - box.top));
        }
    }
}
