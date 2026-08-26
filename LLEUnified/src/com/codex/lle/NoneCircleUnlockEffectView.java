package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

/**
 * App-owned Canvas port of the stock S3 Neo {@code KeyguardEffectViewNone} /
 * {@code CircleUnlockEffect} path.
 *
 * <p>The firmware implementation is deliberately light: one transparent Canvas scene, no
 * screenshot, no wallpaper texture, no GLES surface and no native library. Geometry is scaled
 * from the stock 1080 px reference rather than from modern Android density, which keeps the
 * circle at the physical size Samsung intended on phones, tablets and foldables.</p>
 */
final class NoneCircleUnlockEffectView extends View implements UnlockEffectRenderer {
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_ENTER = 1;
    private static final int PHASE_ACTIVE = 2;
    private static final int PHASE_EXIT = 3;
    private static final int PHASE_AFFORDANCE = 4;

    static final long ENTER_DURATION_MS = Timing.ENTER_DURATION_MS;
    static final long EXIT_DURATION_MS = Timing.EXIT_DURATION_MS;

    // Exact S3 Neo host geometry at its 1080 px reference width.
    private static final float STOCK_REFERENCE_PX = 1080f;
    private static final float STOCK_MAX_DIAMETER_PX = 576f;
    private static final float STOCK_ARROW_BOX_PX = 180f;
    private static final float STOCK_LOCK_BOX_PX = 120f;
    private static final float STOCK_OUTER_STROKE_PX = 4f;
    private static final float STOCK_INNER_STROKE_PX = 6f;
    private static final float STOCK_MIN_RADIUS_ADJUST_PX = 4f;

    private static final int[] STOCK_LOCK_FRAME_RES_IDS = {
            R.drawable.keyguard_none_lock_01,
            R.drawable.keyguard_none_lock_02,
            R.drawable.keyguard_none_lock_03,
            R.drawable.keyguard_none_lock_04,
            R.drawable.keyguard_none_lock_05,
            R.drawable.keyguard_none_lock_06,
            R.drawable.keyguard_none_lock_07,
            R.drawable.keyguard_none_lock_08,
            R.drawable.keyguard_none_lock_09,
            R.drawable.keyguard_none_lock_10,
            R.drawable.keyguard_none_lock_11,
            R.drawable.keyguard_none_lock_12,
            R.drawable.keyguard_none_lock_13,
            R.drawable.keyguard_none_lock_14,
            R.drawable.keyguard_none_lock_15,
            R.drawable.keyguard_none_lock_16,
            R.drawable.keyguard_none_lock_17,
            R.drawable.keyguard_none_lock_18,
            R.drawable.keyguard_none_lock_19,
            R.drawable.keyguard_none_lock_20,
            R.drawable.keyguard_none_lock_21,
            R.drawable.keyguard_none_lock_22,
            R.drawable.keyguard_none_lock_23,
            R.drawable.keyguard_none_lock_24,
            R.drawable.keyguard_none_lock_25,
            R.drawable.keyguard_none_lock_26,
            R.drawable.keyguard_none_lock_27,
            R.drawable.keyguard_none_lock_28,
            R.drawable.keyguard_none_lock_29,
            R.drawable.keyguard_none_lock_30
    };

    private final Paint outerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path glyphPath = new Path();
    private final RectF lockDestination = new RectF();
    private final Bitmap[] lockFrames = new Bitmap[STOCK_LOCK_FRAME_RES_IDS.length];

    private boolean destroyed;
    private boolean gestureActive;
    private boolean darkGlyphs;
    private int phase = PHASE_IDLE;
    private long phaseStartedAt;
    private float centerX;
    private float centerY;
    private float startX;
    private float startY;
    private float strokeAnimationValue;
    private float dragAnimationValue;
    private float exitStrokeMax;
    private float exitDragMax;
    private float exitArrowAlphaMax;
    private Runnable pendingAffordance;

    NoneCircleUnlockEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);

        outerStroke.setStyle(Paint.Style.STROKE);
        innerStroke.setStyle(Paint.Style.STROKE);
        fillStroke.setStyle(Paint.Style.STROKE);
        glyphFill.setStyle(Paint.Style.FILL);
        for (int index = 0; index < STOCK_LOCK_FRAME_RES_IDS.length; index++) {
            lockFrames[index] = BitmapFactory.decodeResource(
                    getResources(), STOCK_LOCK_FRAME_RES_IDS[index]);
        }
        updatePaintColours();
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S3 None";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        cancelPendingAffordance();
        gestureActive = true;
        startX = screenX;
        startY = screenY;
        centerX = screenX;
        centerY = screenY;
        strokeAnimationValue = 0f;
        dragAnimationValue = 0f;
        darkGlyphs = readsWhiteLockscreenSetting();
        updatePaintColours();
        phase = PHASE_ENTER;
        phaseStartedAt = SystemClock.uptimeMillis();
        invalidate();
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
        if (phase == PHASE_EXIT || phase == PHASE_IDLE) {
            return;
        }
        float distance = (float) Math.hypot(screenX - startX, screenY - startY);
        dragAnimationValue = Timing.dragProgress(distance, minRadiusPx(), maxRadiusPx());
        invalidate();
    }

    @Override
    public void finishGesture(boolean completed) {
        if (destroyed || phase == PHASE_IDLE) {
            return;
        }
        gestureActive = false;
        if (completed) {
            // Stock KeyguardEffectViewNone calls CircleUnlockEffect.unlock(), which cancels all
            // animators immediately once SystemUI accepts the unlock.
            clearState();
            invalidate();
            return;
        }
        startExit(SystemClock.uptimeMillis());
    }

    @Override
    public void cancelGesture() {
        if (!destroyed && phase != PHASE_IDLE) {
            gestureActive = false;
            startExit(SystemClock.uptimeMillis());
        }
    }

    @Override
    public void resetEffect() {
        if (!destroyed) {
            clearState();
            invalidate();
        }
    }

    @Override
    public void warmUp() {
        if (!destroyed) {
            invalidate();
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (destroyed || screenRect == null) {
            return;
        }
        final Rect safeRect = new Rect(screenRect);
        cancelPendingAffordance();
        pendingAffordance = new Runnable() {
            @Override
            public void run() {
                pendingAffordance = null;
                if (destroyed) {
                    return;
                }
                gestureActive = false;
                centerX = safeRect.exactCenterX();
                centerY = safeRect.exactCenterY();
                startX = centerX;
                startY = centerY;
                strokeAnimationValue = 0f;
                dragAnimationValue = 0f;
                darkGlyphs = readsWhiteLockscreenSetting();
                updatePaintColours();
                phase = PHASE_AFFORDANCE;
                phaseStartedAt = SystemClock.uptimeMillis();
                invalidate();
            }
        };
        postDelayed(pendingAffordance, Math.max(0L, startDelayMs));
    }

    @Override
    public void destroy() {
        if (!destroyed) {
            destroyed = true;
            clearState();
            for (int index = 0; index < lockFrames.length; index++) {
                lockFrames[index] = null;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (destroyed || phase == PHASE_IDLE) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        float groupAlpha = 1f;
        float arrowAlpha;
        if (phase == PHASE_ENTER) {
            strokeAnimationValue = Timing.enterValue(now - phaseStartedAt);
            groupAlpha = strokeAnimationValue;
            if (strokeAnimationValue >= 1f) {
                strokeAnimationValue = 1f;
                phase = PHASE_ACTIVE;
            }
            arrowAlpha = Timing.arrowPulse(now - phaseStartedAt, dragAnimationValue);
        } else if (phase == PHASE_ACTIVE) {
            strokeAnimationValue = 1f;
            arrowAlpha = Timing.arrowPulse(now - phaseStartedAt, dragAnimationValue);
        } else if (phase == PHASE_EXIT) {
            float remaining = Timing.exitRemaining(now - phaseStartedAt);
            strokeAnimationValue = exitStrokeMax * remaining;
            dragAnimationValue = exitDragMax * remaining;
            groupAlpha = strokeAnimationValue;
            arrowAlpha = Timing.exitArrowAlpha(exitArrowAlphaMax, remaining);
            if (remaining <= 0f) {
                clearState();
                return;
            }
        } else {
            long elapsed = now - phaseStartedAt;
            float enter = Timing.enterValue(elapsed);
            float out = Timing.affordanceRemaining(elapsed);
            strokeAnimationValue = Math.min(enter, out);
            dragAnimationValue = 0f;
            groupAlpha = strokeAnimationValue;
            arrowAlpha = strokeAnimationValue;
            if (elapsed >= Timing.AFFORDANCE_TOTAL_MS) {
                clearState();
                return;
            }
        }

        drawStockScene(canvas, groupAlpha, arrowAlpha);
        if (phase != PHASE_IDLE) {
            postInvalidateOnAnimation();
        }
    }

    private void startExit(long now) {
        if (phase == PHASE_EXIT || phase == PHASE_IDLE) {
            return;
        }
        if (phase == PHASE_ENTER) {
            strokeAnimationValue = Timing.enterValue(now - phaseStartedAt);
        }
        exitStrokeMax = strokeAnimationValue;
        exitDragMax = dragAnimationValue;
        exitArrowAlphaMax = Timing.arrowPulse(now - phaseStartedAt, dragAnimationValue);
        phase = PHASE_EXIT;
        phaseStartedAt = now;
        invalidate();
    }

    private void drawStockScene(Canvas canvas, float groupAlpha, float arrowAlpha) {
        float minRadius = minRadiusPx();
        float maxRadius = maxRadiusPx();
        float betweenRadius = maxRadius - minRadius;
        float outerWidth = stockPx(STOCK_OUTER_STROKE_PX);
        float innerWidth = stockPx(STOCK_INNER_STROKE_PX);

        outerStroke.setStrokeWidth(outerWidth);
        outerStroke.setAlpha(Math.round(170f * Timing.clamp01(groupAlpha)));
        float radius = minRadius + betweenRadius * strokeAnimationValue - outerWidth * 0.5f;
        canvas.drawCircle(centerX, centerY, Math.max(0f, radius), outerStroke);

        innerStroke.setStrokeWidth(innerWidth);
        innerStroke.setAlpha(Math.round(255f * Timing.clamp01(groupAlpha)));
        canvas.drawCircle(centerX, centerY, minRadius, innerStroke);

        float fill = Math.min(dragAnimationValue, strokeAnimationValue);
        if (fill > 0f) {
            fillStroke.setStrokeWidth(betweenRadius * fill);
            fillStroke.setAlpha(Math.round(85f * Timing.clamp01(groupAlpha)));
            canvas.drawCircle(centerX, centerY,
                    minRadius + betweenRadius * fill * 0.5f, fillStroke);
        }

        drawCornerArrow(canvas, Timing.clamp01(arrowAlpha * groupAlpha));
        drawLockSequence(canvas, Timing.clamp01(dragAnimationValue), groupAlpha);
    }

    /** Procedural equivalent of the stock four-corner keyguard_none_arrow bitmap. */
    private void drawCornerArrow(Canvas canvas, float alpha) {
        if (alpha <= 0f) {
            return;
        }
        float box = stockPx(STOCK_ARROW_BOX_PX);
        float half = box * 0.5f;
        float wedge = box * (16f / STOCK_ARROW_BOX_PX);
        glyphFill.setAlpha(Math.round(255f * alpha));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                float x = centerX + sx * half;
                float y = centerY + sy * half;
                glyphPath.reset();
                glyphPath.moveTo(x, y);
                glyphPath.lineTo(x - sx * wedge, y);
                glyphPath.lineTo(x, y - sy * wedge);
                glyphPath.close();
                canvas.drawPath(glyphPath, glyphFill);
            }
        }
        glyphFill.setAlpha(255);
    }

    /** Draws the exact stock 30-frame sequence in its original left-opening direction. */
    private void drawLockSequence(Canvas canvas, float progress, float groupAlpha) {
        float box = stockPx(STOCK_LOCK_BOX_PX);
        int frameIndex = Timing.lockFrameIndex(progress);
        Bitmap frame = lockFrames[frameIndex];
        if (frame == null) {
            return;
        }
        float half = box * 0.5f;
        lockDestination.set(centerX - half, centerY - half,
                centerX + half, centerY + half);
        lockPaint.setAlpha(Math.round(255f * Timing.clamp01(groupAlpha)));
        canvas.drawBitmap(frame, null, lockDestination, lockPaint);
        lockPaint.setAlpha(255);
    }

    private float maxRadiusPx() {
        return stockPx(STOCK_MAX_DIAMETER_PX) * 0.5f;
    }

    private float minRadiusPx() {
        float arrowWidth = stockPx(STOCK_ARROW_BOX_PX);
        return Math.max(1f, (arrowWidth - stockPx(STOCK_INNER_STROKE_PX)
                - stockPx(STOCK_MIN_RADIUS_ADJUST_PX)) * 0.5f);
    }

    private float stockPx(float value) {
        int width = getWidth();
        int height = getHeight();
        int smallest = width > 0 && height > 0
                ? Math.min(width, height)
                : Math.min(getResources().getDisplayMetrics().widthPixels,
                        getResources().getDisplayMetrics().heightPixels);
        return value * Math.max(1, smallest) / STOCK_REFERENCE_PX;
    }

    private boolean readsWhiteLockscreenSetting() {
        try {
            return Settings.Global.getInt(getContext().getContentResolver(),
                    "white_lockscreen_wallpaper", 0) == 1;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void updatePaintColours() {
        int circleColour = darkGlyphs ? Color.rgb(68, 68, 68) : Color.WHITE;
        outerStroke.setColor(circleColour);
        innerStroke.setColor(circleColour);
        fillStroke.setColor(circleColour);
        // The S3 host only recolours CircleUnlockCircle. Arrow and lock rasters stay white.
        glyphFill.setColor(Color.WHITE);
    }

    private void cancelPendingAffordance() {
        if (pendingAffordance != null) {
            removeCallbacks(pendingAffordance);
            pendingAffordance = null;
        }
    }

    private void clearState() {
        cancelPendingAffordance();
        gestureActive = false;
        phase = PHASE_IDLE;
        strokeAnimationValue = 0f;
        dragAnimationValue = 0f;
        exitStrokeMax = 0f;
        exitDragMax = 0f;
        exitArrowAlphaMax = 0f;
    }

    /** Pure stock-timing seam used by host-side regression tests. */
    static final class Timing {
        static final long ENTER_DURATION_MS = 666L;
        static final long EXIT_DURATION_MS = 333L;
        static final long ARROW_HALF_CYCLE_MS = 500L;
        static final long AFFORDANCE_EXIT_START_MS = 466L;
        static final long AFFORDANCE_EXIT_DURATION_MS = 700L;
        static final long AFFORDANCE_TOTAL_MS =
                AFFORDANCE_EXIT_START_MS + AFFORDANCE_EXIT_DURATION_MS;

        private Timing() {
        }

        static float enterValue(long elapsedMs) {
            return quintEaseOut(clamp01(elapsedMs / (float) ENTER_DURATION_MS));
        }

        static float exitRemaining(long elapsedMs) {
            float progress = quintEaseOut(clamp01(elapsedMs / (float) EXIT_DURATION_MS));
            return 1f - progress;
        }

        static float affordanceRemaining(long elapsedMs) {
            if (elapsedMs <= AFFORDANCE_EXIT_START_MS) {
                return 1f;
            }
            float progress = clamp01((elapsedMs - AFFORDANCE_EXIT_START_MS)
                    / (float) AFFORDANCE_EXIT_DURATION_MS);
            return 1f - quintEaseIn(progress);
        }

        static float arrowPulse(long elapsedMs, float drag) {
            if (drag > 0.4f) {
                return 0f;
            }
            long positive = Math.max(0L, elapsedMs);
            long leg = positive / ARROW_HALF_CYCLE_MS;
            float fraction = (positive % ARROW_HALF_CYCLE_MS) / (float) ARROW_HALF_CYCLE_MS;
            float pulse = (leg & 1L) == 0L ? fraction : 1f - fraction;
            return ((0.4f - clamp01(drag)) * pulse) / 0.4f;
        }

        static float exitArrowAlpha(float maximum, float remaining) {
            return remaining > 0.4f ? maximum * (remaining - 0.4f) / 0.6f : 0f;
        }

        static float dragProgress(float distance, float minRadius, float maxRadius) {
            float span = Math.max(1f, maxRadius - minRadius);
            return clamp01((distance - minRadius) / span);
        }

        static float lockSequenceProgress(float drag) {
            return lockFrameIndex(drag) / 29f;
        }

        static int lockFrameIndex(float drag) {
            return (int) (29f * clamp01(drag));
        }

        static float quintEaseOut(float input) {
            float inverse = 1f - clamp01(input);
            return 1f - inverse * inverse * inverse * inverse * inverse;
        }

        static float quintEaseIn(float input) {
            float value = clamp01(input);
            return value * value * value * value * value;
        }

        static float clamp01(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }
}
