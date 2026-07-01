package com.codex.chargingtouchtest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class TouchDebugView extends View {
    private static final String TAG = "ChargingTouchDebug";

    interface TouchTriggerListener {
        void onTouchStarted(float screenX, float screenY);

        void onTouchMoved(float screenX, float screenY, float deltaX, float deltaY, float distance);

        void onTouchEnded(float screenX, float screenY, float deltaX, float deltaY, float distance);

        void onTouchCancelled();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private TouchTriggerListener touchTriggerListener;
    private String lastAction = "waiting";
    private float lastX = -1f;
    private float lastY = -1f;
    private float lastRawX = -1f;
    private float lastRawY = -1f;
    private float lastScreenX = -1f;
    private float lastScreenY = -1f;
    private float gestureStartScreenX = -1f;
    private float gestureStartScreenY = -1f;
    private int pointerCount;
    private boolean transparentMode = true;
    private boolean gestureActive;

    public TouchDebugView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
    }

    public void setTouchTriggerListener(TouchTriggerListener touchTriggerListener) {
        this.touchTriggerListener = touchTriggerListener;
    }

    public void setTransparentMode(boolean transparentMode) {
        if (this.transparentMode == transparentMode) {
            return;
        }
        this.transparentMode = transparentMode;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return true;
        }
        lastAction = actionName(event.getActionMasked());
        lastX = event.getX();
        lastY = event.getY();
        lastRawX = event.getRawX();
        lastRawY = event.getRawY();
        int[] locationOnScreen = new int[2];
        getLocationOnScreen(locationOnScreen);
        lastScreenX = locationOnScreen[0] + lastX;
        lastScreenY = locationOnScreen[1] + lastY;
        pointerCount = event.getPointerCount();
        Log.i(TAG, "action=" + lastAction
                + " local=" + Math.round(lastX) + "," + Math.round(lastY)
                + " raw=" + Math.round(lastRawX) + "," + Math.round(lastRawY)
                + " screen=" + Math.round(lastScreenX) + "," + Math.round(lastScreenY)
                + " window=" + locationOnScreen[0] + "," + locationOnScreen[1]
                + " pointers=" + pointerCount);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureActive = true;
                gestureStartScreenX = lastScreenX;
                gestureStartScreenY = lastScreenY;
                if (touchTriggerListener != null) {
                    touchTriggerListener.onTouchStarted(lastScreenX, lastScreenY);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                notifyMove();
                break;
            case MotionEvent.ACTION_UP:
                notifyEnd();
                performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
                gestureActive = false;
                if (touchTriggerListener != null) {
                    touchTriggerListener.onTouchCancelled();
                }
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (transparentMode) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        bounds.set(0f, 0f, width, height);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 8, 18, 28));
        canvas.drawRoundRect(bounds, dp(12), dp(12), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(230, 116, 215, 255));
        canvas.drawRoundRect(bounds, dp(12), dp(12), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(true);
        paint.setTextSize(dp(15));
        paint.setColor(Color.WHITE);
        float x = dp(12);
        float y = dp(24);
        canvas.drawText("TOUCH DEBUG AREA", x, y, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(dp(13));
        paint.setColor(Color.rgb(205, 224, 236));
        y += dp(24);
        canvas.drawText("swipe: lens flare", x, y, paint);
        y += dp(20);
        canvas.drawText("action: " + lastAction, x, y, paint);
        y += dp(20);
        canvas.drawText("local: " + point(lastX, lastY), x, y, paint);
        y += dp(20);
        canvas.drawText("raw: " + point(lastRawX, lastRawY), x, y, paint);
        y += dp(20);
        canvas.drawText("screen: " + point(lastScreenX, lastScreenY), x, y, paint);
        y += dp(20);
        canvas.drawText("pointers: " + pointerCount, x, y, paint);

        if (lastX >= 0f && lastY >= 0f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(210, 255, 210, 82));
            canvas.drawCircle(lastX, lastY, dp(8), paint);
        }
    }

    private void notifyMove() {
        if (!gestureActive || touchTriggerListener == null) {
            return;
        }
        float deltaX = lastScreenX - gestureStartScreenX;
        float deltaY = lastScreenY - gestureStartScreenY;
        float distance = (float) Math.hypot(deltaX, deltaY);
        touchTriggerListener.onTouchMoved(lastScreenX, lastScreenY, deltaX, deltaY, distance);
    }

    private void notifyEnd() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        if (touchTriggerListener == null) {
            return;
        }
        float deltaX = lastScreenX - gestureStartScreenX;
        float deltaY = lastScreenY - gestureStartScreenY;
        float distance = (float) Math.hypot(deltaX, deltaY);
        touchTriggerListener.onTouchEnded(lastScreenX, lastScreenY, deltaX, deltaY, distance);
    }

    private String point(float x, float y) {
        if (x < 0f || y < 0f) {
            return "-";
        }
        return Math.round(x) + "," + Math.round(y);
    }

    private String actionName(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            case MotionEvent.ACTION_POINTER_DOWN:
                return "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP:
                return "POINTER_UP";
            default:
                return String.valueOf(action);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
