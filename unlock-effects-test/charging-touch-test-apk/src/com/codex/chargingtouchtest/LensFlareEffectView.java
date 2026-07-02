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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import java.util.Random;

public class LensFlareEffectView extends View implements UnlockEffectRenderer {
    private static final String TAG = "ChargingS4LensFlare";
    private static final long SHOW_ANIMATION_DURATION_MS = 6000L;
    private static final long FOG_ON_DURATION_MS = 100L;
    private static final long TAP_ANIMATION_DURATION_MS = 4000L;
    private static final long FADE_OUT_DURATION_MS = 500L;
    private static final long UNLOCK_ANIMATION_DURATION_MS = 1200L;
    private static final float GLOBAL_ALPHA = 0.8f;
    private static final float FOG_MAX_ALPHA = 0.6f;
    private static final float DEFAULT_IN_SAMPLE_SIZE = 2f;
    private static final float BASE_FINGER_Y_OFFSET_PX = -80f;
    private static final float BASE_MAX_ALPHA_DISTANCE_PX = 1500f;
    private static final float BASE_TAP_AREA_RADIUS_PX = 600f;
    private static final float BASE_SCREEN_WIDTH_PX = 1080f;
    private static final int TAP_HEXAGON_TOTAL = 5;
    private static final int DRAG_HEXAGON_TOTAL = 6;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG
            | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final Random random = new Random();
    private final Bitmap flareLight;
    private final Bitmap flareRing;
    private final Bitmap flareParticle;
    private final Bitmap flareLong;
    private final Bitmap flareRainbow;
    private final Bitmap flareHoverLight;
    private final Bitmap flareVignetting;
    private final Bitmap[] tapHexagons;
    private final Bitmap[] dragHexagons;
    private final float[] tapHexagonRotations = new float[TAP_HEXAGON_TOTAL];
    private final float[] dragHexagonDistance = new float[DRAG_HEXAGON_TOTAL];
    private final float[] dragHexagonScale = new float[DRAG_HEXAGON_TOTAL];
    private final float fingerYOffsetPx;
    private final float maxAlphaDistancePx;
    private final float tapAreaRadiusPx;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;

    private boolean destroyed;
    private boolean warmUpPending;
    private boolean warmedUp;
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
    private float randomRotation;
    private TapAnimation tapAnimation;
    private UnlockAnimation unlockAnimation;

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
        Bitmap hexagonBlue = loadDrawable("keyguard_flare_hexagon_blue");
        Bitmap hexagonOrange = loadDrawable("keyguard_flare_hexagon_orange");
        Bitmap hexagonGreen = loadDrawable("keyguard_flare_hexagon_green");
        tapHexagons = new Bitmap[] {
                hexagonBlue,
                hexagonOrange,
                hexagonGreen
        };
        dragHexagons = new Bitmap[] {
                hexagonBlue,
                hexagonOrange,
                hexagonBlue,
                hexagonOrange,
                hexagonGreen,
                hexagonGreen
        };
        prepareBitmapsForDraw();
        for (int i = 0; i < tapHexagonRotations.length; i++) {
            tapHexagonRotations[i] = random.nextInt(360);
        }

        float ratio = screenScaleRatio();
        fingerYOffsetPx = BASE_FINGER_Y_OFFSET_PX * ratio;
        maxAlphaDistancePx = BASE_MAX_ALPHA_DISTANCE_PX * ratio;
        tapAreaRadiusPx = BASE_TAP_AREA_RADIUS_PX * ratio;

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tapSound = soundPool.load(context, R.raw.lens_flare_tap, 1);
        unlockSound = soundPool.load(context, R.raw.lens_flare_unlock, 1);
        Log.i(TAG, "S4 lens flare Canvas renderer loaded ratio=" + ratio
                + " yOffset=" + Math.round(fingerYOffsetPx)
                + " maxAlphaDistance=" + Math.round(maxAlphaDistancePx)
                + " tapRadius=" + Math.round(tapAreaRadiusPx));
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S4 lens flare";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        warmedUp = true;
        long now = SystemClock.uptimeMillis();
        gestureActive = true;
        fading = false;
        startX = screenX;
        startY = visualY(screenY);
        currentX = startX;
        currentY = startY;
        fadeStartedAt = 0L;
        gestureStartedAt = now;
        randomRotation = random.nextInt(360);
        setHexagonRandomTarget();
        tapAnimation = createTapAnimation(startX, startY, now);
        unlockAnimation = null;
        play(tapSound);
        Log.i(TAG, "canvas lens flare begin x=" + Math.round(startX)
                + " y=" + Math.round(startY));
        invalidate();
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        currentX = screenX;
        currentY = visualY(screenY);
        invalidate();
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        gestureActive = false;
        fadeX = currentX;
        fadeY = currentY;
        fadeStartedAt = now;
        fading = !completed;
        if (completed) {
            unlockAnimation = new UnlockAnimation(
                    startX,
                    startY,
                    currentX,
                    currentY,
                    now,
                    unlockRotation());
            play(unlockSound);
        }
        Log.i(TAG, "canvas lens flare finish completed=" + completed
                + " x=" + Math.round(currentX)
                + " y=" + Math.round(currentY));
        invalidate();
    }

    @Override
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
    public void resetEffect() {
        gestureActive = false;
        fading = false;
        tapAnimation = null;
        unlockAnimation = null;
        invalidate();
    }

    @Override
    public void warmUp() {
        if (destroyed || warmedUp) {
            return;
        }
        warmUpPending = true;
        if (getWidth() > 0 && getHeight() > 0) {
            invalidate();
        }
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        soundPool.release();
    }

    @Override
    protected void onDetachedFromWindow() {
        resetEffect();
        warmUpPending = false;
        warmedUp = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(new Runnable() {
            @Override
            public void run() {
                warmUp();
            }
        });
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (warmUpPending && width > 0 && height > 0) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean keepAnimating = false;

        if (warmUpPending) {
            drawWarmUpFrame(canvas);
            warmUpPending = false;
            warmedUp = true;
            Log.i(TAG, "canvas lens flare warmed");
        }

        if (gestureActive) {
            drawDragFlare(canvas, now, currentX, currentY, 1f);
            keepAnimating = true;
        } else if (fading) {
            float t = clamp01((now - fadeStartedAt) / (float) FADE_OUT_DURATION_MS);
            drawDragFlare(canvas, now, fadeX, fadeY, 1f - t);
            keepAnimating = t < 1f;
            if (!keepAnimating) {
                fading = false;
            }
        }

        if (tapAnimation != null) {
            float t = clamp01((now - tapAnimation.startedAt) / (float) TAP_ANIMATION_DURATION_MS);
            if (t < 1f) {
                drawTapAnimation(canvas, tapAnimation, quintOut(t));
                keepAnimating = true;
            } else {
                tapAnimation = null;
            }
        }

        if (unlockAnimation != null) {
            float t = clamp01((now - unlockAnimation.startedAt)
                    / (float) UNLOCK_ANIMATION_DURATION_MS);
            if (t < 1f) {
                drawUnlockAnimation(canvas, unlockAnimation, quintOut(t));
                keepAnimating = true;
            } else {
                unlockAnimation = null;
            }
        }

        if (keepAnimating) {
            postInvalidateOnAnimation();
        }
    }

    private void drawWarmUpFrame(Canvas canvas) {
        drawBitmapCentered(canvas, flareLight, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawBitmapCentered(canvas, flareRing, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawBitmapCentered(canvas, flareParticle, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawBitmapCentered(canvas, flareLong, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawBitmapCentered(canvas, flareRainbow, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawBitmapCentered(canvas, flareHoverLight, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawBitmapCentered(canvas, flareVignetting, 0.5f, 0.5f, 1f, 0.004f, 0f);
        for (int i = 0; i < tapHexagons.length; i++) {
            drawBitmapCentered(canvas, tapHexagons[i], 0.5f, 0.5f, 1f, 0.004f, 0f);
        }
        for (int i = 0; i < dragHexagons.length; i++) {
            drawBitmapCentered(canvas, dragHexagons[i], 0.5f, 0.5f, 1f, 0.004f, 0f);
        }
    }

    private void drawDragFlare(Canvas canvas, long now, float x, float y, float fadeAlpha) {
        float objValue = quintOut(clamp01((now - gestureStartedAt)
                / (float) SHOW_ANIMATION_DURATION_MS));
        float fogValue = quintOut(clamp01((now - gestureStartedAt)
                / (float) FOG_ON_DURATION_MS));
        float distance = (float) Math.hypot(x - startX, y - startY);
        float distanceAlpha = clamp01(distance / maxAlphaDistancePx);
        float fogAlpha = clamp01(fogValue * (1f - distanceAlpha)) * GLOBAL_ALPHA * fadeAlpha;
        float objAlpha = clamp01(distanceAlpha * 3f) * GLOBAL_ALPHA * fadeAlpha;
        float vignettingAlpha = clamp01(distanceAlpha * 1.3f) * 0.18f * fadeAlpha;
        float rotation = -objValue * 30f - distanceAlpha * 160f;
        float lightScale = 1f + distanceAlpha;

        if (vignettingAlpha > 0f) {
            drawBitmapCentered(canvas, flareVignetting, getWidth() * 0.5f, getHeight() * 0.5f,
                    Math.max(getWidth(), getHeight()) * 1.2f, vignettingAlpha, 0f);
        }
        drawBitmapCentered(canvas, flareLight, x, y,
                bitmapSize(flareLight, lightScale), fogAlpha, rotation);
        drawBitmapCentered(canvas, flareHoverLight, x, y,
                bitmapSize(flareHoverLight, 1f + distanceAlpha * 0.4f),
                fogAlpha * 0.5f, rotation - 8f);

        if (objAlpha <= 0f) {
            return;
        }

        for (int i = 0; i < DRAG_HEXAGON_TOTAL; i++) {
            Bitmap hexagon = dragHexagons[i];
            float animationScale = 0.5f + objValue * 0.5f;
            float byDistanceScale = 0.5f + (distance / 720f) * 0.5f;
            float scale = dragHexagonScale[i] * byDistanceScale * animationScale;
            float pathScale = dragHexagonDistance[i] * animationScale;
            float tx = startX + (x - startX) * pathScale;
            float ty = startY + (y - startY) * pathScale;
            drawBitmapCentered(canvas, hexagon, tx, ty,
                    bitmapSize(hexagon, scale), objAlpha * 0.65f, rotation);
        }
    }

    private void drawTapAnimation(Canvas canvas, TapAnimation animation, float value) {
        float alpha = value < 0.5f ? 1f : 1f - (value - 0.5f) * 2f;
        alpha = clamp01(alpha) * GLOBAL_ALPHA;
        float distanceScale = 0.2f + 0.8f * value;

        for (int i = 0; i < animation.hexagons.length; i++) {
            TapHexagon hexagon = animation.hexagons[i];
            float scale = hexagon.scale * (value * 0.8f + 0.7f);
            float x = animation.x + hexagon.dx * distanceScale;
            float y = animation.y + hexagon.dy * distanceScale;
            drawBitmapCentered(canvas, hexagon.bitmap, x, y,
                    bitmapSize(hexagon.bitmap, scale), alpha, hexagon.rotation);
        }

        float particleValue = value * 1.8f;
        float particleAlpha = pulseAlpha(particleValue) * GLOBAL_ALPHA;
        drawBitmapCentered(canvas, flareParticle, animation.x, animation.y,
                bitmapSize(flareParticle, value * 1.2f), particleAlpha,
                animation.rotation + value * 40f);

        float ringValue = value * 1.4f;
        float ringAlpha = pulseAlpha(ringValue) * GLOBAL_ALPHA;
        drawBitmapCentered(canvas, flareRing, animation.x, animation.y,
                bitmapSize(flareRing, 0.5f + value), ringAlpha, animation.rotation);
        drawBitmapCentered(canvas, flareLong, animation.x, animation.y,
                bitmapSize(flareLong, 1.5f + value * 2f), ringAlpha,
                animation.rotation + 30f * value);
    }

    private void drawUnlockAnimation(Canvas canvas, UnlockAnimation animation, float value) {
        float alpha = value < 0.5f ? value * 2f : 1f - (value - 0.5f) * 2f;
        float x = animation.startX + (animation.endX - animation.startX) * 0.4f;
        float y = animation.startY + (animation.endY - animation.startY) * 0.4f;
        drawBitmapCentered(canvas, flareRainbow, x, y,
                bitmapSize(flareRainbow, 1f + value * 1.3f),
                clamp01(alpha) * GLOBAL_ALPHA, animation.rotation);
    }

    private TapAnimation createTapAnimation(float x, float y, long now) {
        TapHexagon[] animationHexagons = new TapHexagon[TAP_HEXAGON_TOTAL];
        for (int i = 0; i < animationHexagons.length; i++) {
            float angle = randomRotation;
            float distance = random.nextFloat() * tapAreaRadiusPx;
            float dx = (float) Math.cos(angle) * distance;
            float dy = (float) Math.sin(angle) * distance;
            float scale = 0.2f + random.nextFloat() * 0.8f;
            Bitmap bitmap = tapHexagons[i % tapHexagons.length];
            animationHexagons[i] = new TapHexagon(
                    dx,
                    dy,
                    scale,
                    bitmap,
                    tapHexagonRotations[i]);
        }
        return new TapAnimation(x, y, now, randomRotation, animationHexagons);
    }

    private void setHexagonRandomTarget() {
        float startDistance = 0.2f;
        float distanceGap = 0.24f;
        for (int i = 0; i < DRAG_HEXAGON_TOTAL; i++) {
            float distance = startDistance + i * distanceGap
                    + (random.nextFloat() - 0.5f) * 0.4f;
            dragHexagonDistance[i] = Math.max(0.05f, distance);
            dragHexagonScale[i] = dragHexagonDistance[i] + 0.1f;
        }
        for (int i = DRAG_HEXAGON_TOTAL - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            float distance = dragHexagonDistance[i];
            dragHexagonDistance[i] = dragHexagonDistance[swapIndex];
            dragHexagonDistance[swapIndex] = distance;
            float scale = dragHexagonScale[i];
            dragHexagonScale[i] = dragHexagonScale[swapIndex];
            dragHexagonScale[swapIndex] = scale;
        }
    }

    private float unlockRotation() {
        float dx = currentX - startX;
        float dy = currentY - startY;
        if (Math.abs(dx) < 1f && Math.abs(dy) < 1f) {
            return randomRotation;
        }
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 40f;
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

    private float bitmapSize(Bitmap bitmap, float scale) {
        if (bitmap == null) {
            return 0f;
        }
        return Math.max(bitmap.getWidth(), bitmap.getHeight())
                * DEFAULT_IN_SAMPLE_SIZE
                * Math.max(0f, scale);
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

    private void prepareBitmapsForDraw() {
        prepareBitmap(flareLight);
        prepareBitmap(flareRing);
        prepareBitmap(flareParticle);
        prepareBitmap(flareLong);
        prepareBitmap(flareRainbow);
        prepareBitmap(flareHoverLight);
        prepareBitmap(flareVignetting);
        for (int i = 0; i < tapHexagons.length; i++) {
            prepareBitmap(tapHexagons[i]);
        }
        for (int i = 0; i < dragHexagons.length; i++) {
            prepareBitmap(dragHexagons[i]);
        }
    }

    private void prepareBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.prepareToDraw();
        }
    }

    private void play(int soundId) {
        if (!destroyed && soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private float visualY(float screenY) {
        return screenY + fingerYOffsetPx;
    }

    private float screenScaleRatio() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int smallestWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
        if (smallestWidth <= 0 || smallestWidth == (int) BASE_SCREEN_WIDTH_PX) {
            return 1f;
        }
        return smallestWidth / BASE_SCREEN_WIDTH_PX;
    }

    private float pulseAlpha(float value) {
        float corrected = value < 0.5f ? 1f : 1f - (value - 0.5f) * 2f;
        return clamp01(corrected);
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

    private static final class TapAnimation {
        final float x;
        final float y;
        final long startedAt;
        final float rotation;
        final TapHexagon[] hexagons;

        TapAnimation(float x, float y, long startedAt, float rotation,
                TapHexagon[] hexagons) {
            this.x = x;
            this.y = y;
            this.startedAt = startedAt;
            this.rotation = rotation;
            this.hexagons = hexagons;
        }
    }

    private static final class TapHexagon {
        final float dx;
        final float dy;
        final float scale;
        final Bitmap bitmap;
        final float rotation;

        TapHexagon(float dx, float dy, float scale, Bitmap bitmap, float rotation) {
            this.dx = dx;
            this.dy = dy;
            this.scale = scale;
            this.bitmap = bitmap;
            this.rotation = rotation;
        }
    }

    private static final class UnlockAnimation {
        final float startX;
        final float startY;
        final float endX;
        final float endY;
        final long startedAt;
        final float rotation;

        UnlockAnimation(float startX, float startY, float endX, float endY,
                long startedAt, float rotation) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.startedAt = startedAt;
            this.rotation = rotation;
        }
    }
}
