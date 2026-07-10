package com.codex.s4unlockfx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class UnlockEffectView extends View {
    private static final int MODE_LENS = 0;
    private static final int MODE_PARTICLE = 1;
    private static final int MODE_WATERCOLOR = 2;
    private static final int MODE_COUNT = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(9505);
    private final List<Burst> bursts = new ArrayList<Burst>();
    private final List<Dot> dots = new ArrayList<Dot>();
    private final Bitmap flareLight;
    private final Bitmap flareRing;
    private final Bitmap flareParticle;
    private final Bitmap flareLong;
    private final Bitmap flareRainbow;
    private final Bitmap waterMask1;
    private final Bitmap waterMask2;
    private final Bitmap waterMask3;
    private final Bitmap waterNoise;
    private final Matrix matrix = new Matrix();
    private int mode = MODE_LENS;
    private long lastModeSwitch;
    private float downX;
    private float downY;

    public UnlockEffectView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        textPaint.setColor(Color.argb(210, 235, 245, 255));
        textPaint.setTextSize(dp(13));
        textPaint.setShadowLayer(dp(7), 0, dp(1), Color.BLACK);
        flareLight = load("keyguard_flare_light_00040.png");
        flareRing = load("keyguard_flare_ring.png");
        flareParticle = load("keyguard_flare_particle.png");
        flareLong = load("keyguard_flare_long.png");
        flareRainbow = load("keyguard_flare_rainbow.png");
        waterMask1 = load("watercolor_mask1.png");
        waterMask2 = load("watercolor_mask2.png");
        waterMask3 = load("watercolor_mask3.png");
        waterNoise = load("watercolor_noise.jpg");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        drawBackground(canvas);

        Iterator<Burst> burstIterator = bursts.iterator();
        while (burstIterator.hasNext()) {
            Burst burst = burstIterator.next();
            float t = (now - burst.startedAt) / burst.duration;
            if (t >= 1f) {
                burstIterator.remove();
                continue;
            }
            if (burst.mode == MODE_LENS) {
                drawLens(canvas, burst, t);
            } else if (burst.mode == MODE_PARTICLE) {
                drawParticleBurst(canvas, burst, t);
            } else {
                drawWatercolor(canvas, burst, t);
            }
        }

        Iterator<Dot> dotIterator = dots.iterator();
        while (dotIterator.hasNext()) {
            Dot dot = dotIterator.next();
            float t = (now - dot.startedAt) / 650f;
            if (t >= 1f) {
                dotIterator.remove();
                continue;
            }
            paint.setColor(Color.argb((int) (150 * (1f - t)), 180, 230, 255));
            canvas.drawCircle(dot.x, dot.y, dp(12) + dp(44) * t, paint);
        }

        drawHud(canvas);
        if (!bursts.isEmpty() || !dots.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() > 1 && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            switchMode();
            return true;
        }

        float x = event.getX();
        float y = event.getY();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            addBurst(x, y, true);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getHistorySize(); i += 2) {
                dots.add(new Dot(event.getHistoricalX(i), event.getHistoricalY(i)));
            }
            dots.add(new Dot(x, y));
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (Math.abs(x - downX) < dp(8) && Math.abs(y - downY) < dp(8) && y < dp(86)) {
                switchMode();
            } else {
                addBurst(x, y, false);
            }
            return true;
        }
        return true;
    }

    private void addBurst(float x, float y, boolean initial) {
        bursts.add(new Burst(x, y, mode, initial ? 980f : 1300f));
        if (mode == MODE_PARTICLE) {
            for (int i = 0; i < 28; i++) {
                dots.add(new Dot(x + random.nextFloat() * dp(32) - dp(16), y + random.nextFloat() * dp(32) - dp(16)));
            }
        }
        invalidate();
    }

    private void switchMode() {
        long now = SystemClock.uptimeMillis();
        if (now - lastModeSwitch < 180) {
            return;
        }
        lastModeSwitch = now;
        mode = (mode + 1) % MODE_COUNT;
        addBurst(getWidth() * 0.5f, getHeight() * 0.48f, true);
    }

    private void drawBackground(Canvas canvas) {
        int w = Math.max(getWidth(), 1);
        int h = Math.max(getHeight(), 1);
        paint.setShader(new LinearGradient(0, 0, w, h, 0xff080a10, 0xff142536, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * 0.7f, h * 0.24f, h * 0.7f, 0x445fb7ff, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private void drawLens(Canvas canvas, Burst burst, float t) {
        float ease = 1f - (1f - t) * (1f - t);
        drawBitmapCentered(canvas, flareRainbow, burst.x, burst.y, dp(360) * (0.55f + ease * 0.55f), 0.36f * (1f - t), burst.spin + t * 28f);
        drawBitmapCentered(canvas, flareRing, burst.x, burst.y, dp(220) + dp(260) * ease, 0.9f * (1f - t), burst.spin);
        drawBitmapCentered(canvas, flareLong, burst.x, burst.y, dp(620) * (0.5f + t), 0.55f * (1f - t), burst.spin - 18f);
        drawBitmapCentered(canvas, flareParticle, burst.x, burst.y, dp(360) * (0.45f + ease), 0.8f * (1f - t), burst.spin + 45f * t);
        drawBitmapCentered(canvas, flareLight, burst.x, burst.y, dp(260) * (0.75f + ease * 0.45f), 0.95f * (1f - t * 0.6f), burst.spin);
    }

    private void drawParticleBurst(Canvas canvas, Burst burst, float t) {
        float radius = dp(24) + dp(210) * t;
        for (int i = 0; i < 34; i++) {
            float a = burst.spin + i * 137.5f;
            float r = radius * (0.35f + ((i * 37) % 100) / 100f);
            float x = burst.x + (float) Math.cos(Math.toRadians(a)) * r;
            float y = burst.y + (float) Math.sin(Math.toRadians(a)) * r;
            float size = dp(16) + dp((i % 5) * 5);
            drawBitmapCentered(canvas, flareParticle, x, y, size, 0.75f * (1f - t), a + t * 130f);
        }
        paint.setColor(Color.argb((int) (80 * (1f - t)), 125, 215, 255));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        canvas.drawCircle(burst.x, burst.y, radius * 0.8f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawWatercolor(Canvas canvas, Burst burst, float t) {
        float scale = dp(310) * (0.35f + t * 1.25f);
        drawBitmapCentered(canvas, waterNoise, burst.x, burst.y, scale * 1.25f, 0.25f * (1f - t), burst.spin * 0.2f);
        drawBitmapCentered(canvas, waterMask1, burst.x - dp(22), burst.y + dp(8), scale, 0.55f * (1f - t * 0.65f), burst.spin + 12f * t);
        drawBitmapCentered(canvas, waterMask2, burst.x + dp(16), burst.y - dp(14), scale * 0.92f, 0.5f * (1f - t * 0.7f), burst.spin - 24f * t);
        drawBitmapCentered(canvas, waterMask3, burst.x, burst.y, scale * 1.08f, 0.42f * (1f - t * 0.7f), burst.spin + 42f * t);
    }

    private void drawBitmapCentered(Canvas canvas, Bitmap bitmap, float cx, float cy, float targetSize, float alpha, float rotation) {
        if (bitmap == null || alpha <= 0f) {
            return;
        }
        float scale = targetSize / Math.max(bitmap.getWidth(), bitmap.getHeight());
        matrix.reset();
        matrix.postTranslate(-bitmap.getWidth() * 0.5f, -bitmap.getHeight() * 0.5f);
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        matrix.postTranslate(cx, cy);
        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255))));
        canvas.drawBitmap(bitmap, matrix, paint);
        paint.setAlpha(255);
    }

    private void drawHud(Canvas canvas) {
        String name = mode == MODE_LENS ? "Lens flare" : mode == MODE_PARTICLE ? "Particle" : "Watercolor";
        paint.setColor(Color.argb(100, 0, 0, 0));
        RectF pill = new RectF(dp(14), dp(14), dp(14) + dp(172), dp(48));
        canvas.drawRoundRect(pill, dp(17), dp(17), paint);
        canvas.drawText(name, dp(30), dp(36), textPaint);
    }

    private Bitmap load(String name) {
        try {
            InputStream input = getContext().getAssets().open(name);
            try {
                return BitmapFactory.decodeStream(input);
            } finally {
                input.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static final class Burst {
        final float x;
        final float y;
        final int mode;
        final float duration;
        final float spin;
        final long startedAt = SystemClock.uptimeMillis();

        Burst(float x, float y, int mode, float duration) {
            this.x = x;
            this.y = y;
            this.mode = mode;
            this.duration = duration;
            this.spin = (x * 0.07f + y * 0.05f) % 360f;
        }
    }

    private static final class Dot {
        final float x;
        final float y;
        final long startedAt = SystemClock.uptimeMillis();

        Dot(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
