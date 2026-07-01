package com.codex.chargingtouchtest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LensFlareEffectView extends View {
    private static final String TAG = "ChargingS4LensFlare";
    private static final long SHOW_ANIMATION_DURATION_MS = 6000L;
    private static final long TAP_ANIMATION_DURATION_MS = 650L;
    private static final long FADE_OUT_DURATION_MS = 500L;
    private static final long UNLOCK_ANIMATION_DURATION_MS = 1200L;
    private static final float GLOBAL_ALPHA = 0.8f;
    private static final float FOG_MAX_ALPHA = 0.6f;
    private static final float FINGER_Y_OFFSET_PX = -80f;
    private static final int MAX_TOUCH_HEXAGONS = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG
            | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final List<FlareBurst> bursts = new ArrayList<FlareBurst>();
    private final Bitmap flareLight;
    private final Bitmap flareRing;
    private final Bitmap flareParticle;
    private final Bitmap flareLong;
    private final Bitmap flareRainbow;
    private final Bitmap flareHoverLight;
    private final Bitmap flareVignetting;
    private final Bitmap[] hexagons;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;

    private boolean gestureActive;
    private boolean fading;
    private float startX;
    private float startY;
    private float currentX;
    private float currentY;
    private float fadeX;
    private float fadeY;
    private long gestureStartedAt;
    private long fadeStartedAt;
    private long lastHexagonAt;

    public LensFlareEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        flareLight = loadDrawable("keyguard_flare_light_00040");
        flareRing = loadDrawable("keyguard_flare_ring");
        flareParticle = loadDrawable("keyguard_flare_particle");
        flareLong = loadDrawable("keyguard_flare_long");
        flareRainbow = loadDrawable("keyguard_flare_rainbow");
        flareHoverLight = loadDrawable("keyguard_flare_hoverlight");
        flareVignetting = loadDrawable("keyguard_flare_vignetting");
        hexagons = new Bitmap[] {
                loadDrawable("keyguard_flare_hexagon_blue"),
                loadDrawable("keyguard_flare_hexagon_green"),
                loadDrawable("keyguard_flare_hexagon_orange")
        };

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tapSound = soundPool.load(context, R.raw.lens_flare_tap, 1);
        unlockSound = soundPool.load(context, R.raw.lens_flare_unlock, 1);
        Log.i(TAG, "S4 lens flare Canvas renderer loaded");
    }

    public void beginGesture(float screenX, float screenY) {
        long now = SystemClock.uptimeMillis();
        gestureActive = true;
        fading = false;
        startX = screenX;
        startY = visualY(screenY);
        currentX = startX;
        currentY = startY;
        gestureStartedAt = now;
        fadeStartedAt = 0L;
        bursts.clear();
        addTrailBurst(startX, startY, now);
        play(tapSound);
        Log.i(TAG, "canvas lens flare begin x=" + Math.round(startX)
                + " y=" + Math.round(startY));
        invalidate();
    }

    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        currentX = screenX;
        currentY = visualY(screenY);
        maybeAddMovingHexagon(SystemClock.uptimeMillis());
        invalidate();
    }

    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        gestureActive = false;
        fading = !completed;
        fadeX = currentX;
        fadeY = currentY;
        fadeStartedAt = now;
        addTapBurst(currentX, currentY, now, completed);
        if (completed) {
            play(unlockSound);
        }
        Log.i(TAG, "canvas lens flare finish completed=" + completed
                + " x=" + Math.round(currentX)
                + " y=" + Math.round(currentY));
        invalidate();
    }

    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        fading = true;
        fadeX = currentX;
        fadeY = currentY;
        fadeStartedAt = SystemClock.uptimeMillis();
        Log.i(TAG, "canvas lens flare cancel");
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        soundPool.release();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean keepAnimating = false;

        if (gestureActive) {
            drawActiveFlare(canvas, now, currentX, currentY, 1f);
            keepAnimating = true;
        } else if (fading) {
            float t = clamp01((now - fadeStartedAt) / (float) FADE_OUT_DURATION_MS);
            drawActiveFlare(canvas, now, fadeX, fadeY, 1f - t);
            keepAnimating = t < 1f;
            if (!keepAnimating) {
                fading = false;
            }
        }

        Iterator<FlareBurst> iterator = bursts.iterator();
        while (iterator.hasNext()) {
            FlareBurst burst = iterator.next();
            float t = clamp01((now - burst.startedAt) / (float) burst.durationMs);
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            drawBurst(canvas, burst, t);
            keepAnimating = true;
        }

        if (keepAnimating) {
            postInvalidateOnAnimation();
        }
    }

    private void drawActiveFlare(Canvas canvas, long now, float x, float y, float fadeAlpha) {
        float elapsed = now - gestureStartedAt;
        float show = quintOut(clamp01(elapsed / (float) SHOW_ANIMATION_DURATION_MS));
        float fog = FOG_MAX_ALPHA * quintOut(clamp01(elapsed / 100f));
        float distance = (float) Math.hypot(x - startX, y - startY);
        float distanceAlpha = clamp01(distance / 1500f);
        float alpha = GLOBAL_ALPHA * fadeAlpha * (0.56f + 0.44f * distanceAlpha);
        float rotation = unlockRotation();

        if (fadeAlpha > 0.98f) {
            drawBitmapCentered(canvas, flareVignetting, getWidth() * 0.5f, getHeight() * 0.5f,
                    Math.max(getWidth(), getHeight()) * 1.25f, 0.08f * alpha, 0f);
        }
        drawBitmapCentered(canvas, flareRainbow, x, y, dp(430f + show * 110f),
                0.32f * alpha, rotation + show * 22f);
        drawBitmapCentered(canvas, flareLong, x, y, dp(520f + show * 120f),
                0.62f * alpha, rotation - 16f);
        drawBitmapCentered(canvas, flareRing, x, y, dp(250f + show * 70f),
                0.88f * alpha, rotation);
        drawBitmapCentered(canvas, flareParticle, x, y, dp(270f + show * 95f),
                0.78f * alpha, rotation + show * 50f);
        drawBitmapCentered(canvas, flareHoverLight, x, y, dp(230f + show * 34f),
                0.40f * alpha, rotation - 8f);
        drawBitmapCentered(canvas, flareLight, x, y, dp(250f + show * 50f),
                (0.62f + fog * 0.38f) * alpha, rotation);
    }

    private void drawBurst(Canvas canvas, FlareBurst burst, float t) {
        if (burst.trail) {
            drawTrailBurst(canvas, burst, t);
            return;
        }
        float ease = quintOut(t);
        float alpha = (burst.unlock ? GLOBAL_ALPHA : 0.58f) * (1f - t);
        float base = burst.unlock ? dp(420f) : dp(300f);
        float rotation = burst.rotation + (burst.unlock ? ease * 72f : ease * 28f);

        if (burst.unlock) {
            drawBitmapCentered(canvas, flareRainbow, burst.x, burst.y,
                    base * (0.62f + ease * 0.76f), 0.38f * alpha, rotation);
            drawBitmapCentered(canvas, flareLong, burst.x, burst.y,
                    base * (1.25f + ease * 0.75f), 0.55f * alpha, rotation - 20f);
        }
        drawBitmapCentered(canvas, flareRing, burst.x, burst.y,
                base * (0.70f + ease * 0.65f), 0.86f * alpha, rotation);
        drawBitmapCentered(canvas, flareParticle, burst.x, burst.y,
                base * (0.70f + ease * 0.95f), 0.72f * alpha, rotation + 52f * ease);
        drawBitmapCentered(canvas, flareLight, burst.x, burst.y,
                base * (0.58f + ease * 0.55f), 0.95f * alpha, rotation);

        for (int i = 0; i < MAX_TOUCH_HEXAGONS; i++) {
            Bitmap hexagon = hexagons[i % hexagons.length];
            float angle = burst.rotation + i * 72f + ease * (burst.unlock ? 90f : 40f);
            float radius = dp(burst.unlock ? 58f + i * 24f : 30f + i * 18f) * ease;
            float x = burst.x + (float) Math.cos(Math.toRadians(angle)) * radius;
            float y = burst.y + (float) Math.sin(Math.toRadians(angle)) * radius;
            drawBitmapCentered(canvas, hexagon, x, y,
                    dp(42f + i * 5f) * (1f + ease * 0.5f),
                    0.52f * alpha, angle + 35f);
        }
    }

    private void drawTrailBurst(Canvas canvas, FlareBurst burst, float t) {
        float ease = quintOut(t);
        float alpha = 0.42f * (1f - t);
        drawBitmapCentered(canvas, flareParticle, burst.x, burst.y,
                dp(96f + ease * 44f), alpha, burst.rotation + ease * 70f);
        for (int i = 0; i < MAX_TOUCH_HEXAGONS; i++) {
            Bitmap hexagon = hexagons[i % hexagons.length];
            float angle = burst.rotation + i * 72f + ease * 35f;
            float radius = dp(16f + i * 8f) * ease;
            float x = burst.x + (float) Math.cos(Math.toRadians(angle)) * radius;
            float y = burst.y + (float) Math.sin(Math.toRadians(angle)) * radius;
            drawBitmapCentered(canvas, hexagon, x, y,
                    dp(24f + i * 3f), alpha * 0.85f, angle);
        }
    }

    private void addTapBurst(float x, float y, long now, boolean unlock) {
        bursts.add(new FlareBurst(
                x,
                y,
                now,
                unlock ? UNLOCK_ANIMATION_DURATION_MS : TAP_ANIMATION_DURATION_MS,
                unlock,
                unlock ? unlockRotation() : rotationFor(x, y),
                false));
    }

    private void addTrailBurst(float x, float y, long now) {
        bursts.add(new FlareBurst(x, y, now, TAP_ANIMATION_DURATION_MS,
                false, rotationFor(x, y), true));
    }

    private void maybeAddMovingHexagon(long now) {
        if (now - lastHexagonAt < 120L) {
            return;
        }
        lastHexagonAt = now;
        addTrailBurst(currentX, currentY, now);
        while (bursts.size() > 5) {
            bursts.remove(0);
        }
    }

    private float unlockRotation() {
        float dx = currentX - startX;
        float dy = currentY - startY;
        if (Math.abs(dx) < 1f && Math.abs(dy) < 1f) {
            return rotationFor(currentX, currentY);
        }
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 40f;
    }

    private float rotationFor(float x, float y) {
        return (x * 0.07f + y * 0.05f) % 360f;
    }

    private void drawBitmapCentered(Canvas canvas, Bitmap bitmap, float cx, float cy,
            float targetSize, float alpha, float rotation) {
        if (bitmap == null || alpha <= 0f || targetSize <= 0f) {
            return;
        }
        float scale = targetSize / Math.max(bitmap.getWidth(), bitmap.getHeight());
        matrix.reset();
        matrix.postTranslate(-bitmap.getWidth() * 0.5f, -bitmap.getHeight() * 0.5f);
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        matrix.postTranslate(cx, cy);
        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        canvas.drawBitmap(bitmap, matrix, paint);
        paint.setAlpha(255);
    }

    private Bitmap loadDrawable(String name) {
        int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
        if (id == 0) {
            Log.w(TAG, "missing lens flare drawable " + name);
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(getResources(), id, options);
    }

    private void play(int soundId) {
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private float visualY(float screenY) {
        return screenY + FINGER_Y_OFFSET_PX;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float quintOut(float value) {
        float inverse = 1f - clamp01(value);
        return 1f - inverse * inverse * inverse * inverse * inverse;
    }

    private float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private static final class FlareBurst {
        final float x;
        final float y;
        final long startedAt;
        final long durationMs;
        final boolean unlock;
        final float rotation;
        final boolean trail;

        FlareBurst(float x, float y, long startedAt, long durationMs,
                boolean unlock, float rotation, boolean trail) {
            this.x = x;
            this.y = y;
            this.startedAt = startedAt;
            this.durationMs = durationMs;
            this.unlock = unlock;
            this.rotation = rotation;
            this.trail = trail;
        }
    }
}
