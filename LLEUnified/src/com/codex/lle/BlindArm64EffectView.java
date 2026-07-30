package com.codex.lle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;
import android.view.View;

/**
 * App-owned reconstruction of the Tab S Blind effect.
 *
 * <p>The stock renderer divides the wallpaper into 25 portrait or 40 landscape
 * vertical strips. A touch-driven brightness/scale wave travels across those
 * strips while a very faint additive light follows the finger. This renderer
 * keeps the same recovered column counts, distance equation, animation values,
 * interpolation and timings, but draws only strips whose transform is non-neutral.
 * That last rule is L.L.E.'s transparent-overlay equivalent of the mask previously
 * installed around Samsung's full-screen DEX renderer.</p>
 */
public final class BlindArm64EffectView extends View
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "ChargingTabSBlindOwn";

    private static final int PORTRAIT_COLUMNS = 25;
    private static final int LANDSCAPE_COLUMNS = 40;
    private static final long DOWN_DURATION_MS = 200L;
    private static final long UP_DURATION_MS = 1_000L;
    private static final long MOVE_DURATION_MS = 3_600_000L;
    private static final long AFFORDANCE_HOLD_MS = 100L;

    private static final float DOWN_INITIAL_VALUE = 0.30f;
    private static final float MOVE_FOLLOW = 0.17f;
    private static final float PORTRAIT_SCALE_FACTOR = 0.625f;
    private static final float LANDSCAPE_BRIGHT_RANGE = 8f;
    private static final float PORTRAIT_BRIGHT_RANGE = 5f;
    private static final float DISTANCE_DIVISOR = 1_000f;
    private static final float BRIGHTNESS_MULTIPLIER = 200f;
    private static final float RELEASE_SPLIT_PX = 50f;
    private static final float LIGHT_MAX_ALPHA = 0.15f;
    private static final float DRAW_EPSILON = 0.0001f;

    private static final TimeInterpolator QUINT_EASE_OUT = new TimeInterpolator() {
        @Override
        public float getInterpolation(float input) {
            float inverse = 1f - input;
            return 1f - inverse * inverse * inverse * inverse * inverse;
        }
    };

    private final Paint stripPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect sourceRect = new Rect();
    private final android.graphics.RectF destinationRect = new android.graphics.RectF();
    private final ColorMatrix brightnessMatrix = new ColorMatrix();
    private final SoundPool soundPool;
    private final int touchSound;
    private final int unlockSound;
    private final Bitmap lightBitmap;

    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private String backgroundSource = "none";
    private boolean externalBackground;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean firstFrameDrawn;
    private ReadinessListener readinessListener;

    private ValueAnimator downAnimator;
    private ValueAnimator upAnimator;
    private ValueAnimator moveAnimator;

    private float animationValue;
    private float currentX;
    private float currentY;
    private float pointX;
    private float point2X;
    private float point2Y;
    private float lightX;
    private float lightY;

    private final Runnable affordanceDown = new Runnable() {
        @Override
        public void run() {
            if (!destroyed) {
                playDownAnimator(currentX, currentY);
                postDelayed(affordanceUp, AFFORDANCE_HOLD_MS);
            }
        }
    };

    private final Runnable affordanceUp = new Runnable() {
        @Override
        public void run() {
            if (!destroyed) {
                playUpAnimator();
            }
        }
    };

    public BlindArm64EffectView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        lightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeResource(
                getResources(), R.drawable.keyguard_blind_light, options);
        if (decoded == null) {
            throw new IllegalStateException("Tab S Blind light asset unavailable");
        }
        decoded.prepareToDraw();
        lightBitmap = decoded;

        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        touchSound = soundPool.load(context, R.raw.blind_touch, 1);
        unlockSound = soundPool.load(context, R.raw.blind_unlock, 1);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "Tab S Blind";
    }

    public boolean isReady() {
        return !destroyed;
    }

    @Override
    public int getReadinessState() {
        if (destroyed) {
            return STATE_FAILED;
        }
        if (!isAttachedToWindow()) {
            return STATE_CONSTRUCTED;
        }
        if (firstFrameDrawn) {
            return STATE_FIRST_FRAME_READY;
        }
        return isLaidOut() ? STATE_RESOURCES_READY : STATE_ATTACHED;
    }

    @Override
    public String getReadinessDetail() {
        if (destroyed) {
            return "Blind: renderer destroyed";
        }
        if (!isAttachedToWindow()) {
            return "Blind: canvas constructed";
        }
        return firstFrameDrawn
                ? "Blind: app-owned canvas warm frame drawn"
                : isLaidOut()
                ? "Blind: app-owned canvas resources ready"
                : "Blind: canvas attached; waiting for layout";
    }

    @Override
    public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadinessChanged();
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        removeCallbacks(affordanceDown);
        removeCallbacks(affordanceUp);
        gestureActive = true;
        currentX = screenX;
        currentY = screenY;
        play(touchSound);
        playDownAnimator(screenX, screenY);
        Log.i(TAG, "Blind begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY)
                + " bg=" + backgroundSource);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        currentX = screenX;
        currentY = screenY;
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive || destroyed) {
            return;
        }
        gestureActive = false;
        playUpAnimator();
        if (completed) {
            play(unlockSound);
        }
        Log.i(TAG, "Blind finish completed=" + completed
                + " x=" + Math.round(currentX)
                + " y=" + Math.round(currentY));
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive || destroyed) {
            return;
        }
        gestureActive = false;
        playUpAnimator();
        Log.i(TAG, "Blind cancel");
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        removeCallbacks(affordanceDown);
        removeCallbacks(affordanceUp);
        cancelAnimators();
        animationValue = 0f;
        invalidate();
    }

    @Override
    public void warmUp() {
        if (!destroyed) {
            if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
                backgroundBitmap.prepareToDraw();
            }
            lightBitmap.prepareToDraw();
            invalidate();
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (destroyed) {
            return;
        }
        Rect rect = screenRect;
        if (rect == null || rect.width() <= 0 || rect.height() <= 0) {
            rect = new Rect(0, 0, getRenderWidth(), getRenderHeight());
        }
        currentX = rect.exactCenterX();
        currentY = rect.exactCenterY();
        removeCallbacks(affordanceDown);
        removeCallbacks(affordanceUp);
        postDelayed(affordanceDown, Math.max(0L, startDelayMs));
        Log.i(TAG, "Blind affordance queued delayMs=" + Math.max(0L, startDelayMs));
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalBackground
                && backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == getRenderWidth()
                && backgroundBitmap.getHeight() == getRenderHeight();
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        int width = getRenderWidth();
        int height = getRenderHeight();
        boolean borrow = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, width, height);
        Bitmap next = borrow ? source : createCenterCropBitmap(source, width, height);
        next.prepareToDraw();
        releaseBackgroundBitmap();
        backgroundBitmap = next;
        ownsBackgroundBitmap = !borrow;
        backgroundSource = sourceName == null ? "external" : sourceName;
        externalBackground = true;
        invalidate();
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        if (destroyed) {
            return;
        }
        releaseBackgroundBitmap();
        externalBackground = false;
        backgroundSource = "none";
        invalidate();
    }

    @Override
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && backgroundBitmap == bitmap;
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        notifyReadinessChanged();
        readinessListener = null;
        soundPool.release();
        releaseBackgroundBitmap();
        if (!lightBitmap.isRecycled()) {
            lightBitmap.recycle();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!firstFrameDrawn) {
            firstFrameDrawn = true;
            notifyReadinessChanged();
        }
        if (destroyed || animationValue <= DRAW_EPSILON
                || backgroundBitmap == null || backgroundBitmap.isRecycled()) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0
                || backgroundBitmap.getWidth() != width
                || backgroundBitmap.getHeight() != height) {
            return;
        }
        boolean landscape = width > height;
        int columns = landscape ? LANDSCAPE_COLUMNS : PORTRAIT_COLUMNS;
        float brightRange = landscape ? LANDSCAPE_BRIGHT_RANGE : PORTRAIT_BRIGHT_RANGE;
        float scaleFactor = landscape ? 1f : PORTRAIT_SCALE_FACTOR;
        float reach = Math.min(width, height) / brightRange;

        for (int index = 0; index < columns; index++) {
            int left = Math.round(index * width / (float) columns);
            int right = Math.round((index + 1) * width / (float) columns);
            if (right <= left) {
                continue;
            }
            float midPoint = left + (right - left) * 0.5f;
            float distance = Math.max(0f,
                    (reach - Math.abs(midPoint - pointX)) / DISTANCE_DIVISOR);
            if (upAnimator != null && upAnimator.isRunning()) {
                distance = Math.max(distance, Math.max(0f,
                        (reach - Math.abs(midPoint - point2X)) / DISTANCE_DIVISOR));
            }
            if (distance <= 0f) {
                continue;
            }

            float scale = 1f + animationValue * distance * scaleFactor;
            float deltaScale = scale - 1f;
            if (deltaScale <= DRAW_EPSILON) {
                continue;
            }

            float halfWidth = (right - left) * scale * 0.5f;
            float halfHeight = height * scale * 0.5f;
            destinationRect.set(midPoint - halfWidth, height * 0.5f - halfHeight,
                    midPoint + halfWidth, height * 0.5f + halfHeight);
            sourceRect.set(left, 0, right, height);

            float brightness = animationValue * distance * BRIGHTNESS_MULTIPLIER;
            brightnessMatrix.set(new float[] {
                    1f, 0f, 0f, 0f, brightness,
                    0f, 1f, 0f, 0f, brightness,
                    0f, 0f, 1f, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
            });
            stripPaint.setColorFilter(new ColorMatrixColorFilter(brightnessMatrix));
            canvas.drawBitmap(backgroundBitmap, sourceRect, destinationRect, stripPaint);
        }
        stripPaint.setColorFilter(null);

        int lightSize = Math.max(1, Math.min(width, height) / 2);
        float lightAlpha = Math.max(0f, Math.min(1f,
                animationValue * LIGHT_MAX_ALPHA));
        lightPaint.setAlpha(Math.round(lightAlpha * 255f));
        destinationRect.set(lightX - lightSize * 0.5f, lightY - lightSize * 0.5f,
                lightX + lightSize * 0.5f, lightY + lightSize * 0.5f);
        canvas.drawBitmap(lightBitmap, null, destinationRect, lightPaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        firstFrameDrawn = false;
        notifyReadinessChanged();
        warmUp();
    }

    @Override
    protected void onDetachedFromWindow() {
        firstFrameDrawn = false;
        notifyReadinessChanged();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        firstFrameDrawn = false;
        notifyReadinessChanged();
    }

    private void playDownAnimator(float x, float y) {
        animationValue = 0f;
        cancelAnimators();
        currentX = x;
        currentY = y;
        pointX = x;
        point2X = x;
        point2Y = y;
        lightX = x;
        lightY = y;

        downAnimator = ValueAnimator.ofFloat(DOWN_INITIAL_VALUE, 1f);
        downAnimator.setDuration(DOWN_DURATION_MS);
        downAnimator.setInterpolator(QUINT_EASE_OUT);
        downAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                animationValue = ((Float) animation.getAnimatedValue()).floatValue();
                invalidate();
            }
        });
        downAnimator.start();
        startMoveAnimator();
    }

    private void playUpAnimator() {
        if (destroyed || (upAnimator != null && upAnimator.isRunning())) {
            return;
        }
        cancelAnimator(downAnimator);
        downAnimator = null;
        float releaseStart = animationValue;
        upAnimator = ValueAnimator.ofFloat(1f, 0f);
        upAnimator.setDuration(UP_DURATION_MS);
        upAnimator.setInterpolator(QUINT_EASE_OUT);
        upAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = ((Float) animation.getAnimatedValue()).floatValue();
                animationValue = releaseStart * value;
                float split = (1f - animationValue) * RELEASE_SPLIT_PX;
                pointX -= split;
                point2X += split;
                invalidate();
            }
        });
        upAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (upAnimator == animation) {
                    upAnimator = null;
                    stopMoveAnimator();
                    animationValue = 0f;
                    invalidate();
                }
            }
        });
        upAnimator.start();
    }

    private void startMoveAnimator() {
        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.setDuration(MOVE_DURATION_MS);
        moveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        moveAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                pointX += (currentX - pointX) * MOVE_FOLLOW;
                point2Y += (currentY - point2Y) * MOVE_FOLLOW;
                point2X += (currentX - point2X) * MOVE_FOLLOW;
                if (upAnimator == null || !upAnimator.isRunning()) {
                    lightX = pointX;
                    lightY = currentY;
                }
                invalidate();
            }
        });
        moveAnimator.start();
    }

    private void cancelAnimators() {
        cancelAnimator(downAnimator);
        cancelAnimator(upAnimator);
        cancelAnimator(moveAnimator);
        downAnimator = null;
        upAnimator = null;
        moveAnimator = null;
    }

    private void stopMoveAnimator() {
        cancelAnimator(moveAnimator);
        moveAnimator = null;
    }

    private void cancelAnimator(ValueAnimator animator) {
        if (animator != null) {
            animator.removeAllUpdateListeners();
            animator.removeAllListeners();
            animator.cancel();
        }
    }

    private Bitmap createCenterCropBitmap(Bitmap source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float srcRatio = source.getWidth() / (float) source.getHeight();
        float dstRatio = width / (float) height;
        Rect src;
        if (srcRatio > dstRatio) {
            int srcWidth = Math.max(1, Math.round(source.getHeight() * dstRatio));
            int left = Math.max(0, (source.getWidth() - srcWidth) / 2);
            src = new Rect(left, 0,
                    Math.min(source.getWidth(), left + srcWidth), source.getHeight());
        } else {
            int srcHeight = Math.max(1, Math.round(source.getWidth() / dstRatio));
            int top = Math.max(0, (source.getHeight() - srcHeight) / 2);
            src = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + srcHeight));
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        new Canvas(out).drawBitmap(source, src, new Rect(0, 0, width, height), paint);
        return out;
    }

    private void releaseBackgroundBitmap() {
        if (ownsBackgroundBitmap
                && backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = null;
        ownsBackgroundBitmap = false;
    }

    private int getRenderWidth() {
        return getWidth() > 0
                ? getWidth()
                : Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        return getHeight() > 0
                ? getHeight()
                : Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    private void play(int soundId) {
        if (soundId != 0 && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(soundId, 1f, 1f, 0, 0, 1f);
        }
    }

    private void notifyReadinessChanged() {
        ReadinessListener listener = readinessListener;
        if (listener != null) {
            try {
                listener.onReadinessChanged();
            } catch (RuntimeException ignored) {
                // Readiness is advisory and must never break rendering.
            }
        }
    }
}
