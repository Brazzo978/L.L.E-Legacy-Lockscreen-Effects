package com.codex.chargingtouchtest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class TouchBoxSetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        setImmersive();
        setContentView(new TouchBoxSetupView(this));
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

    private final class TouchBoxSetupView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] xs = new float[4];
        private final float[] ys = new float[4];
        private final RectF savedBox = new RectF();
        private int pointCount;
        private boolean saving;

        TouchBoxSetupView(Context context) {
            super(context);
            setBackgroundColor(Color.rgb(12, 15, 22));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event == null || saving) {
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                xs[pointCount] = event.getX();
                ys[pointCount] = event.getY();
                pointCount++;
                if (pointCount >= 4) {
                    saving = true;
                    saveTouchBox();
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 260);
                }
                invalidate();
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(12, 15, 22));
            canvas.drawRect(0, 0, width, height, paint);

            drawExistingBox(canvas);
            drawCurrentPoints(canvas);
            drawCopy(canvas, width);
        }

        private void drawExistingBox(Canvas canvas) {
            if (!OverlayPrefs.touchBoxConfigured(TouchBoxSetupActivity.this)) {
                return;
            }
            savedBox.set(
                    OverlayPrefs.touchBoxLeft(TouchBoxSetupActivity.this),
                    OverlayPrefs.touchBoxTop(TouchBoxSetupActivity.this),
                    OverlayPrefs.touchBoxRight(TouchBoxSetupActivity.this),
                    OverlayPrefs.touchBoxBottom(TouchBoxSetupActivity.this));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(145, 112, 225, 255));
            canvas.drawRect(savedBox, paint);
        }

        private void drawCurrentPoints(Canvas canvas) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(255, 218, 90));
            for (int i = 1; i < pointCount; i++) {
                canvas.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], paint);
            }

            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < pointCount; i++) {
                paint.setColor(Color.rgb(255, 218, 90));
                canvas.drawCircle(xs[i], ys[i], dp(8), paint);
                paint.setColor(Color.rgb(12, 15, 22));
                paint.setTextSize(dp(12));
                paint.setFakeBoldText(true);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(String.valueOf(i + 1), xs[i], ys[i] + dp(4), paint);
            }
        }

        private void drawCopy(Canvas canvas, int width) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(dp(20));
            paint.setColor(Color.WHITE);
            canvas.drawText("Touch box setup", width / 2f, dp(52), paint);

            paint.setFakeBoldText(false);
            paint.setTextSize(dp(15));
            paint.setColor(Color.rgb(195, 210, 224));
            if (saving) {
                canvas.drawText("Saved", width / 2f, dp(82), paint);
            } else {
                canvas.drawText("Tap 4 corners of the listen area", width / 2f, dp(82), paint);
                canvas.drawText("Point " + (pointCount + 1) + " of 4", width / 2f, dp(108), paint);
            }
        }

        private void saveTouchBox() {
            float left = xs[0];
            float right = xs[0];
            float top = ys[0];
            float bottom = ys[0];
            for (int i = 1; i < xs.length; i++) {
                left = Math.min(left, xs[i]);
                right = Math.max(right, xs[i]);
                top = Math.min(top, ys[i]);
                bottom = Math.max(bottom, ys[i]);
            }

            int minSize = dp(48);
            int intLeft = Math.round(left);
            int intTop = Math.round(top);
            int intRight = Math.round(right);
            int intBottom = Math.round(bottom);
            if (intRight - intLeft < minSize) {
                int center = (intLeft + intRight) / 2;
                intLeft = center - minSize / 2;
                intRight = intLeft + minSize;
            }
            if (intBottom - intTop < minSize) {
                int center = (intTop + intBottom) / 2;
                intTop = center - minSize / 2;
                intBottom = intTop + minSize;
            }

            intLeft = clamp(intLeft, 0, getWidth() - minSize);
            intTop = clamp(intTop, 0, getHeight() - minSize);
            intRight = clamp(intRight, intLeft + minSize, getWidth());
            intBottom = clamp(intBottom, intTop + minSize, getHeight());
            OverlayPrefs.saveTouchBox(TouchBoxSetupActivity.this,
                    intLeft, intTop, intRight, intBottom);
        }

        private int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }
}
