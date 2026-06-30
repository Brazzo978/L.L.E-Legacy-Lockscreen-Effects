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

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private String lastAction = "waiting";
    private float lastX = -1f;
    private float lastY = -1f;
    private float lastRawX = -1f;
    private float lastRawY = -1f;
    private int pointerCount;

    public TouchDebugView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
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
        pointerCount = event.getPointerCount();
        Log.i(TAG, "action=" + lastAction
                + " local=" + Math.round(lastX) + "," + Math.round(lastY)
                + " raw=" + Math.round(lastRawX) + "," + Math.round(lastRawY)
                + " pointers=" + pointerCount);
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
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
        canvas.drawText("action: " + lastAction, x, y, paint);
        y += dp(20);
        canvas.drawText("local: " + point(lastX, lastY), x, y, paint);
        y += dp(20);
        canvas.drawText("raw: " + point(lastRawX, lastRawY), x, y, paint);
        y += dp(20);
        canvas.drawText("pointers: " + pointerCount, x, y, paint);

        if (lastX >= 0f && lastY >= 0f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(210, 255, 210, 82));
            canvas.drawCircle(lastX, lastY, dp(8), paint);
        }
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
