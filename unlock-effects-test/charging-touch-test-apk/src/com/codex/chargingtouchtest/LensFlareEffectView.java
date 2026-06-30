package com.codex.chargingtouchtest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

public class LensFlareEffectView extends View {
    private static final int MODE_IDLE = 0;
    private static final int MODE_DRAG = 1;
    private static final int MODE_FINISH = 2;
    private static final int MODE_CANCEL = 3;
    private static final long FINISH_DURATION_MS = 360L;
    private static final long CANCEL_DURATION_MS = 220L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float startX;
    private float startY;
    private float currentX;
    private float currentY;
    private long phaseStartTime;
    private int mode = MODE_IDLE;

    public LensFlareEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void beginGesture(float x, float y) {
        startX = x;
        startY = y;
        currentX = x;
        currentY = y;
        phaseStartTime = SystemClock.uptimeMillis();
        mode = MODE_DRAG;
        invalidate();
    }

    public void updateGesture(float x, float y) {
        if (mode != MODE_DRAG) {
            return;
        }
        currentX = x;
        currentY = y;
        invalidate();
    }

    public void finishGesture(boolean completed) {
        if (mode == MODE_IDLE) {
            return;
        }
        phaseStartTime = SystemClock.uptimeMillis();
        mode = completed ? MODE_FINISH : MODE_CANCEL;
        invalidate();
    }

    public void cancelGesture() {
        finishGesture(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mode == MODE_IDLE) {
            return;
        }

        if (mode == MODE_DRAG) {
            float distance = distance(startX, startY, currentX, currentY);
            float progress = clamp(distance / dp(150), 0f, 1f);
            drawGestureFlare(canvas, currentX, currentY, progress, 1f, false);
            postInvalidateOnAnimation();
            return;
        }

        long elapsed = SystemClock.uptimeMillis() - phaseStartTime;
        long duration = mode == MODE_FINISH ? FINISH_DURATION_MS : CANCEL_DURATION_MS;
        float progress = clamp(elapsed / (float) duration, 0f, 1f);
        float fade = 1f - progress;
        boolean completed = mode == MODE_FINISH;
        drawGestureFlare(canvas, currentX, currentY, completed ? 1f : 0.35f, fade, completed);

        if (progress >= 1f) {
            mode = MODE_IDLE;
        } else {
            postInvalidateOnAnimation();
        }
    }

    private void drawGestureFlare(Canvas canvas, float x, float y, float progress,
            float fade, boolean completed) {
        float dx = x - startX;
        float dy = y - startY;
        float length = Math.max(1f, distance(startX, startY, x, y));
        float nx = dx / length;
        float ny = dy / length;
        if (length < dp(4)) {
            nx = 1f;
            ny = 0f;
        }

        drawTrail(canvas, x, y, nx, ny, length, progress, fade);
        drawCoreGlow(canvas, x, y, progress, fade, completed);
        drawRings(canvas, x, y, progress, fade);
        drawStreaks(canvas, x, y, nx, ny, progress, fade);
        drawGhosts(canvas, x, y, progress, fade);
    }

    private void drawTrail(Canvas canvas, float x, float y, float nx, float ny, float length,
            float progress, float fade) {
        float tail = Math.min(length, dp(220));
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(4));
        paint.setColor(Color.argb((int) (155 * fade), 120, 215, 255));
        canvas.drawLine(x - nx * tail, y - ny * tail, x, y, paint);

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb((int) (165 * fade * progress), 255, 244, 205));
        canvas.drawLine(startX, startY, x, y, paint);
    }

    private void drawCoreGlow(Canvas canvas, float x, float y, float progress,
            float fade, boolean completed) {
        float radius = dp(28) + dp(completed ? 145 : 74) * progress;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                x,
                y,
                radius,
                new int[]{
                        Color.argb((int) (235 * fade), 255, 255, 245),
                        Color.argb((int) (135 * fade), 130, 215, 255),
                        Color.argb(0, 130, 215, 255)
                },
                new float[]{0f, 0.28f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
        paint.setShader(null);

        paint.setColor(Color.argb((int) (245 * fade), 255, 255, 255));
        canvas.drawCircle(x, y, dp(5) + dp(4) * progress, paint);
    }

    private void drawRings(Canvas canvas, float x, float y, float progress, float fade) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb((int) (145 * fade), 175, 235, 255));
        canvas.drawCircle(x, y, dp(18) + dp(105) * progress, paint);

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb((int) (90 * fade), 255, 230, 160));
        canvas.drawCircle(x, y, dp(44) + dp(150) * progress, paint);
    }

    private void drawStreaks(Canvas canvas, float x, float y, float nx, float ny,
            float progress, float fade) {
        float px = -ny;
        float py = nx;
        float length = dp(55) + dp(210) * progress;

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(3));
        paint.setColor(Color.argb((int) (185 * fade), 155, 225, 255));
        canvas.drawLine(x - nx * length, y - ny * length, x + nx * length, y + ny * length, paint);

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb((int) (120 * fade), 255, 246, 214));
        canvas.drawLine(x - px * length * 0.36f, y - py * length * 0.36f,
                x + px * length * 0.36f, y + py * length * 0.36f, paint);
    }

    private void drawGhosts(Canvas canvas, float x, float y, float progress, float fade) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float dx = centerX - x;
        float dy = centerY - y;
        drawGhost(canvas, x + dx * 0.30f, y + dy * 0.30f,
                dp(8) + dp(18) * progress, fade, 90, 255, 230, 120);
        drawGhost(canvas, x + dx * 0.58f, y + dy * 0.58f,
                dp(6) + dp(14) * progress, fade, 80, 130, 220, 255);
        drawGhost(canvas, x - dx * 0.22f, y - dy * 0.22f,
                dp(5) + dp(12) * progress, fade, 75, 255, 255, 255);
    }

    private void drawGhost(Canvas canvas, float x, float y, float radius, float fade,
            int alpha, int red, int green, int blue) {
        paint.setColor(Color.argb((int) (alpha * fade), red, green, blue));
        canvas.drawCircle(x, y, radius, paint);
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.hypot(dx, dy);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
