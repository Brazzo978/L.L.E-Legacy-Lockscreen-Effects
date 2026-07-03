package com.codex.chargingtouchtest;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
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
import android.widget.LinearLayout;
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
    private PinEditorView editorView;
    private TextView statusLabel;
    private TextView waitingStatusLabel;
    private boolean waitingForCapture;

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

        boolean startCapture = getIntent() == null
                || getIntent().getBooleanExtra(EXTRA_START_CAPTURE, true);
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
        super.onDestroy();
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
        File file = OverlayPrefs.touchBoxScreenshotFile(this);
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
                .remove(OverlayPrefs.TOUCH_BOX_CAPTURE_ERROR)
                .apply();
    }

    private void showWaitingView() {
        waitingForCapture = true;
        LinearLayout root = graceRoot();
        root.addView(appBar("Lockscreen capture", "Touch box wizard"));

        LinearLayout card = panel();
        TextView title = titleText("Capture needed");
        card.addView(title);

        TextView body = bodyText("Lock the phone, show the lockscreen for about 2 seconds, then unlock and return here.");
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
                finish();
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
        LinearLayout root = graceRoot();
        root.addView(appBar("Touch box editor", "Lockscreen screenshot"));

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

        TextView hint = bodyText("Tap image to add a pin. Drag a pin to move it.");
        hint.setTextSize(13f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(0, dp(4), 0, dp(8));
        bottom.addView(hint, hintParams);

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(rowOne, rowParams());
        rowOne.addView(materialButton("Remove pin", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editorView.removeSelectedPin();
            }
        }), weightedParams(false));
        rowOne.addView(materialButton("Reset pins", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editorView.resetPinsFromSavedBox();
            }
        }), weightedParams(true));
        rowOne.addView(materialButton("New shot", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreshCapture();
            }
        }), weightedParams(true));

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(rowTwo, rowParams());
        rowTwo.addView(materialButton("Cancel", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }), weightedParams(false));
        rowTwo.addView(materialButton("Save box", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentBox();
            }
        }), weightedParams(true));

        setContentView(root);
        editorView.resetPinsFromSavedBox();
    }

    private void startFreshCapture() {
        File file = OverlayPrefs.touchBoxScreenshotFile(this);
        if (file.exists()) {
            file.delete();
        }
        recycleScreenshot();
        requestScreenshotCapture();
        showWaitingView();
    }

    private void saveCurrentBox() {
        Rect box = editorView == null ? null : editorView.currentRoundedBox();
        if (box == null) {
            Toast.makeText(this, "Add at least one pin", Toast.LENGTH_SHORT).show();
            return;
        }
        OverlayPrefs.saveTouchBoxOutward(this, box.left, box.top, box.right, box.bottom);
        Toast.makeText(this, "Touch box saved", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
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
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageRect = new RectF();
        private final RectF boxRect = new RectF();
        private final Path pinPath = new Path();
        private final ArrayList<PointF> pins = new ArrayList<PointF>();
        private int selectedPin = -1;
        private boolean draggingPin;

        PinEditorView(Context context) {
            super(context);
            setBackgroundColor(Color.rgb(18, 20, 24));
            setWillNotDraw(false);
            setClickable(true);
        }

        void resetPinsFromSavedBox() {
            pins.clear();
            Rect box = savedOrDefaultBox();
            pins.add(new PointF(box.left, box.top));
            pins.add(new PointF(box.right, box.top));
            pins.add(new PointF(box.right, box.bottom));
            pins.add(new PointF(box.left, box.bottom));
            selectedPin = pins.size() - 1;
            updateStatus();
            invalidate();
        }

        void removeSelectedPin() {
            if (selectedPin < 0 || selectedPin >= pins.size()) {
                Toast.makeText(TouchBoxSetupActivity.this,
                        "Select a pin first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pins.size() <= 1) {
                Toast.makeText(TouchBoxSetupActivity.this,
                        "Keep at least one pin", Toast.LENGTH_SHORT).show();
                return;
            }
            pins.remove(selectedPin);
            selectedPin = Math.min(selectedPin, pins.size() - 1);
            updateStatus();
            invalidate();
        }

        Rect currentRoundedBox() {
            if (pins.isEmpty() || screenshotBitmap == null) {
                return null;
            }
            float minX = pins.get(0).x;
            float minY = pins.get(0).y;
            float maxX = pins.get(0).x;
            float maxY = pins.get(0).y;
            for (int i = 1; i < pins.size(); i++) {
                PointF pin = pins.get(i);
                minX = Math.min(minX, pin.x);
                minY = Math.min(minY, pin.y);
                maxX = Math.max(maxX, pin.x);
                maxY = Math.max(maxY, pin.y);
            }

            int imageWidth = screenshotBitmap.getWidth();
            int imageHeight = screenshotBitmap.getHeight();
            int minSize = dp(48);
            int roundedLeft = OverlayPrefs.roundTouchCoordinateDown((int) Math.floor(minX));
            int roundedTop = OverlayPrefs.roundTouchCoordinateDown((int) Math.floor(minY));
            int roundedRight = OverlayPrefs.roundTouchCoordinateUp((int) Math.ceil(maxX));
            int roundedBottom = OverlayPrefs.roundTouchCoordinateUp((int) Math.ceil(maxY));

            roundedLeft = clamp(roundedLeft, 0, Math.max(0, imageWidth - minSize));
            roundedTop = clamp(roundedTop, 0, Math.max(0, imageHeight - minSize));
            roundedRight = clamp(roundedRight, roundedLeft + minSize, imageWidth);
            roundedBottom = clamp(roundedBottom, roundedTop + minSize, imageHeight);
            return new Rect(roundedLeft, roundedTop, roundedRight, roundedBottom);
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

            Rect box = currentRoundedBox();
            if (box != null) {
                boxRect.set(
                        screenToViewX(box.left),
                        screenToViewY(box.top),
                        screenToViewX(box.right),
                        screenToViewY(box.bottom));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(48, 20, 126, 245));
                paint.setPathEffect(null);
                canvas.drawRect(boxRect, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(GRACE_BLUE);
                paint.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(5)}, 0));
                canvas.drawRect(boxRect, paint);
                paint.setPathEffect(null);
            }

            drawPinPath(canvas);
            drawPins(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event == null || screenshotBitmap == null || screenshotBitmap.isRecycled()) {
                return true;
            }
            updateImageRect();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                int hit = hitPin(event.getX(), event.getY());
                if (hit >= 0) {
                    selectedPin = hit;
                    draggingPin = true;
                } else if (imageRect.contains(event.getX(), event.getY())) {
                    PointF point = viewToScreenPoint(event.getX(), event.getY());
                    pins.add(point);
                    selectedPin = pins.size() - 1;
                    draggingPin = true;
                }
                updateStatus();
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && draggingPin && selectedPin >= 0) {
                PointF point = viewToScreenPoint(event.getX(), event.getY());
                pins.get(selectedPin).set(point.x, point.y);
                updateStatus();
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                draggingPin = false;
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

        private Rect savedOrDefaultBox() {
            int imageWidth = screenshotBitmap.getWidth();
            int imageHeight = screenshotBitmap.getHeight();
            int minSize = dp(48);
            int boxLeft;
            int boxTop;
            int boxRight;
            int boxBottom;
            if (OverlayPrefs.touchBoxConfigured(TouchBoxSetupActivity.this)) {
                boxLeft = OverlayPrefs.touchBoxLeft(TouchBoxSetupActivity.this);
                boxTop = OverlayPrefs.touchBoxTop(TouchBoxSetupActivity.this);
                boxRight = OverlayPrefs.touchBoxRight(TouchBoxSetupActivity.this);
                boxBottom = OverlayPrefs.touchBoxBottom(TouchBoxSetupActivity.this);
            } else {
                boxLeft = OverlayPrefs.DEFAULT_TOUCH_BOX_LEFT;
                boxTop = OverlayPrefs.DEFAULT_TOUCH_BOX_TOP;
                boxRight = OverlayPrefs.DEFAULT_TOUCH_BOX_RIGHT;
                boxBottom = OverlayPrefs.DEFAULT_TOUCH_BOX_BOTTOM;
            }
            boxLeft = clamp(boxLeft, 0, Math.max(0, imageWidth - minSize));
            boxTop = clamp(boxTop, 0, Math.max(0, imageHeight - minSize));
            boxRight = clamp(boxRight, boxLeft + minSize, imageWidth);
            boxBottom = clamp(boxBottom, boxTop + minSize, imageHeight);
            return new Rect(boxLeft, boxTop, boxRight, boxBottom);
        }

        private void drawPinPath(Canvas canvas) {
            if (pins.size() < 2) {
                return;
            }
            pinPath.reset();
            PointF first = pins.get(0);
            pinPath.moveTo(screenToViewX(first.x), screenToViewY(first.y));
            for (int i = 1; i < pins.size(); i++) {
                PointF pin = pins.get(i);
                pinPath.lineTo(screenToViewX(pin.x), screenToViewY(pin.y));
            }
            if (pins.size() > 2) {
                pinPath.close();
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(220, 255, 214, 72));
            paint.setPathEffect(null);
            canvas.drawPath(pinPath, paint);
        }

        private void drawPins(Canvas canvas) {
            for (int i = 0; i < pins.size(); i++) {
                PointF pin = pins.get(i);
                float x = screenToViewX(pin.x);
                float y = screenToViewY(pin.y);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(i == selectedPin ? GRACE_BLUE : Color.WHITE);
                canvas.drawCircle(x, y, dp(8), paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(i == selectedPin ? Color.WHITE : GRACE_BLUE);
                canvas.drawCircle(x, y, dp(8), paint);
            }
        }

        private int hitPin(float x, float y) {
            float radius = dp(26);
            int best = -1;
            float bestDistance = radius * radius;
            for (int i = 0; i < pins.size(); i++) {
                PointF pin = pins.get(i);
                float dx = x - screenToViewX(pin.x);
                float dy = y - screenToViewY(pin.y);
                float distance = dx * dx + dy * dy;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            return best;
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

        private PointF viewToScreenPoint(float x, float y) {
            float screenX = (x - imageRect.left) / imageRect.width() * screenshotBitmap.getWidth();
            float screenY = (y - imageRect.top) / imageRect.height() * screenshotBitmap.getHeight();
            screenX = clamp(Math.round(screenX), 0, screenshotBitmap.getWidth());
            screenY = clamp(Math.round(screenY), 0, screenshotBitmap.getHeight());
            return new PointF(screenX, screenY);
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
            Rect box = currentRoundedBox();
            if (box == null) {
                statusLabel.setText("Pins 0");
                return;
            }
            statusLabel.setText("Pins " + pins.size()
                    + "   Box " + box.left + "," + box.top
                    + " - " + box.right + "," + box.bottom
                    + "   " + (box.right - box.left)
                    + " x " + (box.bottom - box.top));
        }
    }
}
