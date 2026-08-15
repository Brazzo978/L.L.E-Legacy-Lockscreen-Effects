package com.codex.lle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class TouchDebugView extends View {
    private static final String TAG = "LLEDebug";
    private static final long MOVE_LOG_INTERVAL_MS = 250L;
    private static final int INVALID_POINTER_ID = -1;
    private static final int SAFETY_BYPASS_POINTER_COUNT = 3;
    private static final int SAFETY_BYPASS_DISTANCE_DP = 48;

    interface TouchTriggerListener {
        boolean onTouchStarted(float screenX, float screenY);

        void onTouchMoved(float screenX, float screenY, float deltaX, float deltaY, float distance);

        void onTouchRealigned(float screenX, float screenY);

        void onTouchEnded(float screenX, float screenY, float deltaX, float deltaY, float distance);

        void onTouchCancelled();

        void onSafetyBypassRequested();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final int[] locationOnScreen = new int[2];
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
    private int activePointerId = INVALID_POINTER_ID;
    private boolean transparentMode = true;
    private boolean gestureActive;
    private boolean multiTouchSuppressed;
    private boolean safetyBypassTracking;
    private boolean safetyBypassTriggered;
    private boolean safetyBypassEnabled = true;
    private float safetyBypassStartScreenX;
    private float safetyBypassStartScreenY;
    private boolean listeningEnabled = true;
    private int windowLeft;
    private int windowTop;
    private long lastMoveLogAt;

    public TouchDebugView(Context context) {
        super(context);
        setWillNotDraw(true);
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
        setWillNotDraw(transparentMode);
        invalidate();
    }

    public void setListeningEnabled(boolean listeningEnabled) {
        if (this.listeningEnabled == listeningEnabled) {
            return;
        }
        if (!listeningEnabled && gestureActive && touchTriggerListener != null) {
            touchTriggerListener.onTouchCancelled();
        }
        this.listeningEnabled = listeningEnabled;
        gestureActive = false;
        multiTouchSuppressed = false;
        resetSafetyBypassGesture();
        activePointerId = INVALID_POINTER_ID;
    }

    public void setSafetyBypassEnabled(boolean enabled) {
        safetyBypassEnabled = enabled;
        if (!enabled) {
            resetSafetyBypassGesture();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return true;
        }
        if (!listeningEnabled) {
            return false;
        }
        int action = event.getActionMasked();
        lastAction = actionName(action);
        pointerCount = event.getPointerCount();
        int pointerIndex = resolvePointerIndex(event, action);
        if (action == MotionEvent.ACTION_DOWN || !gestureActive) {
            getLocationOnScreen(locationOnScreen);
            windowLeft = locationOnScreen[0];
            windowTop = locationOnScreen[1];
        }
        updatePointerCoordinates(event, pointerIndex);
        if (shouldLogTouch(action)) {
            Log.i(TAG, "action=" + lastAction
                    + " pointers=" + pointerCount
                    + " gestureActive=" + gestureActive
                    + " listening=" + listeningEnabled);
        }

        if (handleSafetyBypassGesture(event, action)) {
            invalidateIfVisible();
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP && pointerCount == 2) {
            int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
            activePointerId = event.getPointerId(remainingIndex);
            updatePointerCoordinates(event, remainingIndex);
            multiTouchSuppressed = false;
            notifyRealigned();
            invalidateIfVisible();
            return true;
        }

        if (pointerCount > 1) {
            multiTouchSuppressed = true;
            invalidateIfVisible();
            return true;
        }

        if (multiTouchSuppressed) {
            multiTouchSuppressed = false;
            if (action == MotionEvent.ACTION_CANCEL) {
                cancelActiveGesture();
                activePointerId = INVALID_POINTER_ID;
                invalidateIfVisible();
                return true;
            }
            activePointerId = event.getPointerId(0);
            notifyRealigned();
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                if (!startGestureAtCurrentPoint()) {
                    invalidateIfVisible();
                    return false;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                notifyMove();
                break;
            case MotionEvent.ACTION_UP:
                notifyEnd();
                activePointerId = INVALID_POINTER_ID;
                performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelActiveGesture();
                activePointerId = INVALID_POINTER_ID;
                break;
            default:
                break;
        }
        invalidateIfVisible();
        return true;
    }

    private boolean handleSafetyBypassGesture(MotionEvent event, int action) {
        if (!safetyBypassEnabled) {
            return false;
        }
        if (action == MotionEvent.ACTION_POINTER_UP
                && pointerCount - 1 < SAFETY_BYPASS_POINTER_COUNT) {
            resetSafetyBypassGesture();
            return false;
        }
        if (pointerCount >= SAFETY_BYPASS_POINTER_COUNT) {
            float centroidScreenX = windowLeft + pointerCentroidX(event);
            float centroidScreenY = windowTop + pointerCentroidY(event);
            if (!safetyBypassTracking) {
                safetyBypassTracking = true;
                safetyBypassTriggered = false;
                safetyBypassStartScreenX = centroidScreenX;
                safetyBypassStartScreenY = centroidScreenY;
            } else if (!safetyBypassTriggered) {
                float distance = (float) Math.hypot(
                        centroidScreenX - safetyBypassStartScreenX,
                        centroidScreenY - safetyBypassStartScreenY);
                if (distance >= dp(SAFETY_BYPASS_DISTANCE_DP)) {
                    safetyBypassTriggered = true;
                    cancelActiveGesture();
                    if (touchTriggerListener != null) {
                        touchTriggerListener.onSafetyBypassRequested();
                    }
                    Log.w(TAG, "three-finger lock-cycle safety bypass requested distance="
                            + Math.round(distance));
                }
            }
            multiTouchSuppressed = true;
            return safetyBypassTriggered;
        }
        if (safetyBypassTracking
                && (action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL)) {
            resetSafetyBypassGesture();
        }
        return false;
    }

    private float pointerCentroidX(MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            sum += event.getX(i);
        }
        return sum / Math.max(1, event.getPointerCount());
    }

    private float pointerCentroidY(MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            sum += event.getY(i);
        }
        return sum / Math.max(1, event.getPointerCount());
    }

    private void resetSafetyBypassGesture() {
        safetyBypassTracking = false;
        safetyBypassTriggered = false;
        safetyBypassStartScreenX = 0f;
        safetyBypassStartScreenY = 0f;
    }

    private void invalidateIfVisible() {
        if (!transparentMode) {
            invalidate();
        }
    }

    private int resolvePointerIndex(MotionEvent event, int action) {
        if (action == MotionEvent.ACTION_DOWN || activePointerId == INVALID_POINTER_ID) {
            return 0;
        }
        int index = event.findPointerIndex(activePointerId);
        return index >= 0 ? index : 0;
    }

    private void updatePointerCoordinates(MotionEvent event, int pointerIndex) {
        int safeIndex = Math.max(0, Math.min(pointerIndex, event.getPointerCount() - 1));
        lastX = event.getX(safeIndex);
        lastY = event.getY(safeIndex);
        lastRawX = event.getRawX() + lastX - event.getX(0);
        lastRawY = event.getRawY() + lastY - event.getY(0);
        lastScreenX = windowLeft + lastX;
        lastScreenY = windowTop + lastY;
    }

    private boolean startGestureAtCurrentPoint() {
        gestureStartScreenX = lastScreenX;
        gestureStartScreenY = lastScreenY;
        boolean accepted = touchTriggerListener == null
                || touchTriggerListener.onTouchStarted(lastScreenX, lastScreenY);
        gestureActive = accepted;
        return accepted;
    }

    private void cancelActiveGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        if (touchTriggerListener != null) {
            touchTriggerListener.onTouchCancelled();
        }
    }

    private void notifyRealigned() {
        if (gestureActive && touchTriggerListener != null) {
            touchTriggerListener.onTouchRealigned(lastScreenX, lastScreenY);
        }
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
        canvas.drawText("swipe: unlock effect", x, y, paint);
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

    private boolean shouldLogTouch(int action) {
        if (action != MotionEvent.ACTION_MOVE) {
            return true;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastMoveLogAt < MOVE_LOG_INTERVAL_MS) {
            return false;
        }
        lastMoveLogAt = now;
        return true;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
