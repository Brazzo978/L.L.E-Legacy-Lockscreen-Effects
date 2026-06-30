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
    private static final long DURATION_MS = 900L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float touchX;
    private float touchY;
    private long startTime;
    private boolean running;

    public LensFlareEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void trigger(float x, float y) {
        touchX = x;
        touchY = y;
        startTime = SystemClock.uptimeMillis();
        running = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running) {
            return;
        }

        long elapsed = SystemClock.uptimeMillis() - startTime;
        float progress = Math.min(1f, elapsed / (float) DURATION_MS);
        float fade = progress < 0.18f
                ? progress / 0.18f
                : 1f - ((progress - 0.18f) / 0.82f);
        fade = clamp(fade, 0f, 1f);

        drawCoreGlow(canvas, progress, fade);
        drawRings(canvas, progress, fade);
        drawStreaks(canvas, progress, fade);
        drawGhosts(canvas, progress, fade);

        if (progress >= 1f) {
            running = false;
        } else {
            postInvalidateOnAnimation();
        }
    }

    private void drawCoreGlow(Canvas canvas, float progress, float fade) {
        float radius = dp(30) + dp(120) * progress;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                touchX,
                touchY,
                radius,
                new int[]{
                        Color.argb((int) (220 * fade), 255, 255, 245),
                        Color.argb((int) (135 * fade), 120, 205, 255),
                        Color.argb(0, 120, 205, 255)
                },
                new float[]{0f, 0.28f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(touchX, touchY, radius, paint);
        paint.setShader(null);

        paint.setColor(Color.argb((int) (245 * fade), 255, 255, 255));
        canvas.drawCircle(touchX, touchY, dp(5) + dp(7) * (1f - progress), paint);
    }

    private void drawRings(Canvas canvas, float progress, float fade) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float first = dp(26) + dp(165) * progress;
        float second = dp(54) + dp(230) * progress;
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb((int) (135 * fade), 180, 235, 255));
        canvas.drawCircle(touchX, touchY, first, paint);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb((int) (90 * fade), 255, 230, 160));
        canvas.drawCircle(touchX, touchY, second, paint);
    }

    private void drawStreaks(Canvas canvas, float progress, float fade) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float horizontal = getWidth() * (0.18f + 0.82f * progress);
        paint.setStrokeWidth(dp(3));
        paint.setColor(Color.argb((int) (180 * fade), 150, 220, 255));
        canvas.drawLine(touchX - horizontal, touchY, touchX + horizontal, touchY, paint);

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb((int) (120 * fade), 255, 245, 210));
        canvas.drawLine(touchX - horizontal * 0.45f, touchY - dp(18),
                touchX + horizontal * 0.45f, touchY + dp(18), paint);
        canvas.drawLine(touchX - horizontal * 0.45f, touchY + dp(18),
                touchX + horizontal * 0.45f, touchY - dp(18), paint);
    }

    private void drawGhosts(Canvas canvas, float progress, float fade) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float dx = centerX - touchX;
        float dy = centerY - touchY;
        drawGhost(canvas, touchX + dx * 0.36f, touchY + dy * 0.36f,
                dp(10) + dp(20) * progress, fade, 95, 255, 230, 120);
        drawGhost(canvas, touchX + dx * 0.68f, touchY + dy * 0.68f,
                dp(8) + dp(16) * progress, fade, 85, 130, 220, 255);
        drawGhost(canvas, touchX - dx * 0.30f, touchY - dy * 0.30f,
                dp(6) + dp(15) * progress, fade, 75, 255, 255, 255);
    }

    private void drawGhost(Canvas canvas, float x, float y, float radius, float fade,
            int alpha, int red, int green, int blue) {
        paint.setColor(Color.argb((int) (alpha * fade), red, green, blue));
        canvas.drawCircle(x, y, radius, paint);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
