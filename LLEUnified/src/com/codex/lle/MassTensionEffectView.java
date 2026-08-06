package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

/**
 * App-owned, ABI-independent port of Samsung's hidden Mass Tension unlock effect.
 *
 * <p>The stock implementation is a transparent FrameLayout made from ImageViews.
 * This View preserves the original raw-pixel sprites, gesture ratios, layer order,
 * alpha curves and release timings while removing its obsolete SystemUI contracts.
 * It intentionally has no native/JNI dependency and can be shared by LLE ARM32 and
 * ARM64.</p>
 */
final class MassTensionEffectView extends View implements UnlockEffectRenderer {
    private static final int RELEASE_NONE = 0;
    private static final int RELEASE_FADE = 1;
    private static final int RELEASE_SNAP = 2;

    private static final float TEMP_THRESHOLD = 1.2000000476837158f;
    private static final float RELEASE_THRESHOLD = 1.399999976158142f;
    private static final float DRAG_THRESHOLD = 2.0999999046325684f;
    private static final float BETWEEN_FACTOR = 40f;
    private static final float CIRCLE_PLACE_ADJUST_PX = 5f;
    private static final float OUTER_ALPHA_FACTOR = 0.8f;
    private static final int OUTER_MIN_ALPHA = 50;
    private static final int MAX_ALPHA = 255;

    private static final long LONG_PRESS_SOUND_MS = 600L;
    private static final long OUTER_FADE_MS = 400L;
    private static final long SHORT_RELEASE_MS = 300L;
    private static final long SNAP_START_MS = 300L;
    private static final long SNAP_SCALE_MS = 180L;
    private static final long SNAP_ALPHA_MS = 160L;
    private static final long LINE_START_MS = 50L;
    private static final long LINE_RELEASE_MS = 250L;
    private static final float TAP_VOLUME = 0.5011872f; // Stock -6 dB attenuation.

    private final Paint bitmapPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF lineDestination = new RectF();

    private final Bitmap centerDot;
    private final Bitmap centerDotAfter;
    private final Bitmap finger;
    private final Bitmap fingerAfter;
    private final Bitmap line;
    private final Bitmap outer;

    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;
    private final int tensionTargetDensityDpi;
    private final float lineDeletePx;
    private final Object soundLock = new Object();
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();

    private boolean destroyed;
    private boolean gestureActive;
    private boolean releaseLockedUntilFinish;
    private float originX;
    private float originY;
    private float currentX;
    private float currentY;
    private float betweenX;
    private float betweenY;
    private float fingerX;
    private float fingerY;
    private float distanceRatio;
    private float lineSize;
    private float lineAngle;
    private long pressStartedAt;

    private int releaseMode = RELEASE_NONE;
    private long releaseStartedAt;
    private float releaseOriginX;
    private float releaseOriginY;
    private float releaseFingerX;
    private float releaseFingerY;
    private float releaseBetweenX;
    private float releaseBetweenY;
    private float releaseLineSize;
    private float releaseLineAngle;

    MassTensionEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);

        tensionTargetDensityDpi = getResources().getDisplayMetrics().densityDpi;
        centerDot = decode(R.drawable.mass_tension_center_dot);
        centerDotAfter = decode(R.drawable.mass_tension_center_dot_after);
        finger = decode(R.drawable.mass_tension_finger);
        fingerAfter = decode(R.drawable.mass_tension_finger_after);
        line = decode(R.drawable.mass_tension_line);
        outer = decode(R.drawable.mass_tension_outer);
        lineDeletePx = 20f * tensionTargetDensityDpi
                / DisplayMetrics.DENSITY_DEFAULT;

        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool completedPool, int sampleId, int status) {
                handleSoundLoadComplete(completedPool, sampleId, status);
            }
        });
        tapSound = soundPool.load(context, R.raw.mass_tension_tap, 1);
        unlockSound = soundPool.load(context, R.raw.mass_tension_unlock, 1);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "Mass Tension";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        clearState();
        gestureActive = true;
        originX = screenX;
        originY = screenY;
        currentX = screenX;
        currentY = screenY;
        betweenX = (int) screenX;
        betweenY = (int) screenY;
        fingerX = (int) screenX;
        fingerY = (int) screenY;
        pressStartedAt = SystemClock.uptimeMillis();
        playTap();
        invalidate();
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (destroyed || releaseMode != RELEASE_NONE || releaseLockedUntilFinish) {
            return;
        }
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }

        currentX = screenX;
        currentY = screenY;
        int diffX = (int) (screenX - originX);
        int diffY = (int) (screenY - originY);
        float distance = (float) Math.hypot(diffX, diffY);
        float threshold = Math.max(1f, outer.getWidth() * 0.5f);
        distanceRatio = distance / threshold;

        betweenX = (int) (originX + ((screenX - originX) / BETWEEN_FACTOR));
        betweenY = (int) (originY + ((screenY - originY) / BETWEEN_FACTOR));
        lineAngle = (float) Math.toDegrees(
                Math.atan2(screenY - originY, screenX - originX));

        float radius = (finger.getWidth() * 0.5f)
                + (outer.getWidth() * 0.5f) - CIRCLE_PLACE_ADJUST_PX;
        if (distanceRatio < TEMP_THRESHOLD) {
            fingerX = (int) screenX;
            fingerY = (int) screenY;
            updateLineSize(fingerX, fingerY);
        } else if (distanceRatio <= DRAG_THRESHOLD) {
            double angle = Math.toRadians(lineAngle);
            float clampedX = (float) (originX + radius * Math.cos(angle));
            float clampedY = (float) (originY + radius * Math.sin(angle));
            // Stock casts the top-left coordinate, not the circle centre.
            fingerX = (int) (clampedX - finger.getWidth() * 0.5f)
                    + finger.getWidth() * 0.5f;
            fingerY = (int) (clampedY - finger.getHeight() * 0.5f)
                    + finger.getHeight() * 0.5f;
            updateLineSize(fingerX, fingerY);
        } else {
            // The stock class starts the snap animation from MOVE as soon as the
            // 2.1 outer-radius threshold is crossed.
            startRelease(RELEASE_SNAP);
        }
        invalidate();
    }

    @Override
    public void finishGesture(boolean completed) {
        if (destroyed || !gestureActive) {
            return;
        }
        cancelPendingSound(tapSound);
        if (releaseLockedUntilFinish) {
            gestureActive = false;
            releaseLockedUntilFinish = false;
            if (releaseMode != RELEASE_NONE) {
                postInvalidateOnAnimation();
            }
            playUnlockIfCompleted(completed);
            return;
        }
        gestureActive = false;
        if (releaseMode != RELEASE_NONE) {
            postInvalidateOnAnimation();
            playUnlockIfCompleted(completed);
            return;
        }
        if (distanceRatio < RELEASE_THRESHOLD) {
            if (SystemClock.uptimeMillis() - pressStartedAt > LONG_PRESS_SOUND_MS) {
                playTap();
            }
            startRelease(RELEASE_FADE);
        } else {
            startRelease(RELEASE_SNAP);
        }
        playUnlockIfCompleted(completed);
    }

    @Override
    public void cancelGesture() {
        if (destroyed) {
            return;
        }
        // LLE can cancel because the lockscreen surface disappeared. Clearing here
        // avoids preserving the stale ImageViews that Samsung left for reset().
        clearState();
        invalidate();
    }

    @Override
    public void resetEffect() {
        if (destroyed) {
            return;
        }
        clearState();
        invalidate();
    }

    @Override
    public void warmUp() {
        if (!destroyed) {
            invalidate();
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        // Intentionally empty: the Samsung implementation exposes this method but
        // does not draw an affordance for Mass Tension.
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        clearState();
        destroyed = true;
        synchronized (soundLock) {
            soundPool.setOnLoadCompleteListener(null);
            loadedSoundIds.clear();
            pendingSoundIds.clear();
        }
        soundPool.release();
        recycle(centerDot);
        recycle(centerDotAfter);
        recycle(finger);
        recycle(fingerAfter);
        recycle(line);
        recycle(outer);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (destroyed) {
            return;
        }

        boolean needsNextFrame = false;
        if (releaseMode != RELEASE_NONE) {
            needsNextFrame = drawRelease(canvas, SystemClock.uptimeMillis());
        } else if (gestureActive) {
            drawActive(canvas);
        }
        if (needsNextFrame) {
            postInvalidateOnAnimation();
        }
    }

    private void drawActive(Canvas canvas) {
        // Preserve the stock FrameLayout child order.
        drawCentered(canvas, centerDot, betweenX, betweenY, 1f, MAX_ALPHA);
        drawCentered(canvas, finger, fingerX, fingerY, 1f, MAX_ALPHA);
        drawLine(canvas, betweenX, betweenY, lineSize, lineAngle, MAX_ALPHA);

        int alpha = (int) (OUTER_MIN_ALPHA
                + (MAX_ALPHA * distanceRatio * OUTER_ALPHA_FACTOR));
        drawCentered(canvas, outer, (int) originX, (int) originY, 1f,
                Math.min(MAX_ALPHA, Math.max(OUTER_MIN_ALPHA, alpha)));
    }

    private boolean drawRelease(Canvas canvas, long now) {
        long elapsed = Math.max(0L, now - releaseStartedAt);

        if (releaseMode == RELEASE_FADE) {
            drawReleaseOuter(canvas, elapsed);
            float progress = accelerateDecelerate(
                    clamp01(elapsed / (float) SHORT_RELEASE_MS));
            if (elapsed < SHORT_RELEASE_MS) {
                float scale = 0.4f + 0.6f * progress;
                int alpha = alphaFromRemaining(1f - progress);
                drawCentered(canvas, fingerAfter, releaseFingerX, releaseFingerY,
                        scale, alpha);
                drawCentered(canvas, centerDotAfter, releaseBetweenX, releaseBetweenY,
                        scale, alpha);
            }
            if (elapsed >= OUTER_FADE_MS) {
                releaseMode = RELEASE_NONE;
                return false;
            }
            return true;
        }

        // During a snap the original 20 px dot travels back to the initial touch
        // while the line retracts. It is below all four "after" roots in stock.
        if (elapsed >= LINE_START_MS && elapsed < LINE_START_MS + LINE_RELEASE_MS) {
            float progress = decelerate(clamp01(
                    (elapsed - LINE_START_MS) / (float) LINE_RELEASE_MS));
            float dotX = lerp(releaseBetweenX, (int) releaseOriginX, progress);
            float dotY = lerp(releaseBetweenY, (int) releaseOriginY, progress);
            drawCentered(canvas, centerDot, dotX, dotY, 1f, MAX_ALPHA);
        }

        drawReleaseOuter(canvas, elapsed);

        if (elapsed < SNAP_START_MS) {
            drawCentered(canvas, fingerAfter, releaseFingerX, releaseFingerY,
                    0.4f, MAX_ALPHA);
        } else if (elapsed < SNAP_START_MS + Math.max(SNAP_SCALE_MS, SNAP_ALPHA_MS)) {
            float scaleProgress = accelerate(clamp01(
                    (elapsed - SNAP_START_MS) / (float) SNAP_SCALE_MS));
            float alphaProgress = accelerate(clamp01(
                    (elapsed - SNAP_START_MS) / (float) SNAP_ALPHA_MS));
            drawCentered(canvas, fingerAfter, releaseFingerX, releaseFingerY,
                    0.4f + 0.6f * scaleProgress,
                    alphaFromRemaining(1f - alphaProgress));
        }

        if (elapsed < LINE_START_MS) {
            drawLine(canvas, releaseBetweenX, releaseBetweenY,
                    releaseLineSize, releaseLineAngle, MAX_ALPHA);
        } else if (elapsed < LINE_START_MS + LINE_RELEASE_MS) {
            float progress = decelerate(clamp01(
                    (elapsed - LINE_START_MS) / (float) LINE_RELEASE_MS));
            drawLine(canvas, releaseBetweenX, releaseBetweenY,
                    releaseLineSize * (1f - progress), releaseLineAngle, MAX_ALPHA);
        }

        long duration = Math.max(OUTER_FADE_MS, SNAP_START_MS + SNAP_SCALE_MS);
        if (elapsed >= duration) {
            releaseMode = RELEASE_NONE;
            return false;
        }
        return true;
    }

    private void startRelease(int mode) {
        if (releaseMode != RELEASE_NONE) {
            return;
        }
        releaseLockedUntilFinish = gestureActive;
        releaseMode = mode;
        releaseStartedAt = SystemClock.uptimeMillis();
        releaseOriginX = originX;
        releaseOriginY = originY;
        releaseFingerX = (int) currentX;
        releaseFingerY = (int) currentY;
        releaseBetweenX = betweenX;
        releaseBetweenY = betweenY;
        releaseLineSize = lineSize;
        releaseLineAngle = lineAngle;
        invalidate();
    }

    private void updateLineSize(float endX, float endY) {
        float dx = endX - betweenX;
        float dy = endY - betweenY;
        lineSize = (float) Math.hypot(dx, dy)
                - centerDot.getWidth() * 0.5f - lineDeletePx;
        lineSize = Math.max(0f, lineSize);
    }

    private void drawReleaseOuter(Canvas canvas, long elapsed) {
        if (elapsed >= OUTER_FADE_MS) {
            return;
        }
        float progress = accelerateDecelerate(
                clamp01(elapsed / (float) OUTER_FADE_MS));
        drawCentered(canvas, outer, (int) releaseOriginX, (int) releaseOriginY,
                1f, alphaFromRemaining(1f - progress));
    }

    private void drawCentered(Canvas canvas, Bitmap bitmap, float centerX, float centerY,
            float scale, int alpha) {
        if (bitmap == null || bitmap.isRecycled() || alpha <= 0 || scale <= 0f) {
            return;
        }
        bitmapPaint.setAlpha(Math.min(MAX_ALPHA, alpha));
        int save = canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(scale, scale);
        canvas.drawBitmap(bitmap, -bitmap.getWidth() * 0.5f,
                -bitmap.getHeight() * 0.5f, bitmapPaint);
        canvas.restoreToCount(save);
        bitmapPaint.setAlpha(MAX_ALPHA);
    }

    private void drawLine(Canvas canvas, float startX, float startY, float length,
            float angle, int alpha) {
        if (line == null || line.isRecycled() || length <= 0f || alpha <= 0) {
            return;
        }
        bitmapPaint.setAlpha(Math.min(MAX_ALPHA, alpha));
        int save = canvas.save();
        canvas.translate(startX, startY);
        canvas.rotate(angle);
        float halfLineHeight = line.getHeight() * 0.5f;
        lineDestination.set(0f, -halfLineHeight, length, halfLineHeight);
        canvas.drawBitmap(line, null, lineDestination, bitmapPaint);
        canvas.restoreToCount(save);
        bitmapPaint.setAlpha(MAX_ALPHA);
    }

    private void playTap() {
        if (destroyed || tapSound == 0
                || !OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            return;
        }
        playSound(tapSound);
    }

    private void playUnlockIfCompleted(boolean completed) {
        if (completed && !destroyed
                && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            playSound(unlockSound);
        }
    }

    private void playSound(int soundId) {
        if (soundId == 0) {
            return;
        }
        synchronized (soundLock) {
            if (destroyed) {
                return;
            }
            if (!loadedSoundIds.contains(soundId)) {
                pendingSoundIds.add(soundId);
                return;
            }
            soundPool.play(soundId, TAP_VOLUME, TAP_VOLUME, 1, 0, 1f);
        }
    }

    private void cancelPendingSound(int soundId) {
        synchronized (soundLock) {
            pendingSoundIds.remove(soundId);
        }
    }

    private void handleSoundLoadComplete(
            SoundPool completedPool, int sampleId, int status) {
        synchronized (soundLock) {
            if (completedPool != soundPool || destroyed) {
                return;
            }
            if (status != 0) {
                pendingSoundIds.remove(sampleId);
                return;
            }
            loadedSoundIds.add(sampleId);
            if (pendingSoundIds.remove(sampleId)
                    && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
                soundPool.play(sampleId, TAP_VOLUME, TAP_VOLUME, 1, 0, 1f);
            }
        }
    }

    private Bitmap decode(int resourceId) {
        // Samsung packages every Tension sprite in drawable-hdpi. The PNG ports
        // live in nodpi only to preserve their recovered bytes, so restore the
        // framework's original hdpi-to-device scaling explicitly when decoding.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = true;
        options.inDensity = DisplayMetrics.DENSITY_HIGH;
        options.inTargetDensity = tensionTargetDensityDpi;
        Bitmap bitmap = BitmapFactory.decodeResource(
                getResources(), resourceId, options);
        if (bitmap == null) {
            throw new IllegalStateException(
                    "Missing Mass Tension bitmap resource " + resourceId);
        }
        return bitmap;
    }

    private void clearState() {
        gestureActive = false;
        releaseLockedUntilFinish = false;
        releaseMode = RELEASE_NONE;
        distanceRatio = 0f;
        lineSize = 0f;
        lineAngle = 0f;
        pressStartedAt = 0L;
        synchronized (soundLock) {
            pendingSoundIds.clear();
        }
    }

    private static int alphaFromRemaining(float remaining) {
        return Math.max(0, Math.min(MAX_ALPHA, Math.round(MAX_ALPHA * remaining)));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static float accelerate(float value) {
        return value * value;
    }

    private static float decelerate(float value) {
        float remaining = 1f - value;
        return 1f - remaining * remaining;
    }

    private static float accelerateDecelerate(float value) {
        return (float) (Math.cos((value + 1f) * Math.PI) * 0.5f + 0.5f);
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
