package com.codex.lle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/**
 * App-owned Canvas reconstruction of Samsung's {@code CircleUnlockEffect}, presented by the
 * Samsung picker as the deliberately minimal "None" effect.
 *
 * <p>The OEM implementation is a transparent FrameLayout with two {@code ValueAnimator}s
 * (666 ms in / 333 ms out), a direction arrow and a 30-frame lock sequence. This renderer
 * preserves the observable circular affordance, timing and transparent lifecycle without
 * embedding Samsung or XLocker artwork. It owns no wallpaper bitmap, GL surface, native
 * library or SoundPool.</p>
 */
final class NoneCircleUnlockEffectView extends View implements UnlockEffectRenderer {
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_ENTER = 1;
    private static final int PHASE_ACTIVE = 2;
    private static final int PHASE_EXIT = 3;
    private static final int PHASE_AFFORDANCE_ENTER = 4;

    // Samsung CircleUnlockEffect constructor: 0x29a and 0x14d respectively.
    static final long ENTER_DURATION_MS = Timing.ENTER_DURATION_MS;
    static final long EXIT_DURATION_MS = Timing.EXIT_DURATION_MS;

    // The stock CircleData used by the Samsung host supplies 260 dp and a 24 dp min-width
    // offset, with 3 dp outer and 2 dp inner strokes.
    private static final float MAX_DIAMETER_DP = 260f;
    private static final float MIN_WIDTH_OFFSET_DP = 24f;
    private static final float OUTER_STROKE_DP = 3f;
    private static final float INNER_STROKE_DP = 2f;
    private static final float ARROW_STROKE_DP = 2f;

    private final Paint outerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean destroyed;
    private boolean gestureActive;
    private int phase = PHASE_IDLE;
    private long phaseStartedAt;
    private float centerX;
    private float centerY;
    private float startX;
    private float startY;
    private float dragProgress;
    private float exitScale;
    private float exitFill;
    private Runnable pendingAffordance;

    NoneCircleUnlockEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);

        outerStroke.setStyle(Paint.Style.STROKE);
        outerStroke.setStrokeCap(Paint.Cap.ROUND);
        outerStroke.setColor(0xaaffffff);

        innerStroke.setStyle(Paint.Style.STROKE);
        innerStroke.setStrokeCap(Paint.Cap.ROUND);
        innerStroke.setColor(Color.WHITE);

        fillStroke.setStyle(Paint.Style.STROKE);
        fillStroke.setStrokeCap(Paint.Cap.ROUND);
        fillStroke.setColor(0x55ffffff);

        arrowStroke.setStyle(Paint.Style.STROKE);
        arrowStroke.setStrokeCap(Paint.Cap.ROUND);
        arrowStroke.setStrokeJoin(Paint.Join.ROUND);
        arrowStroke.setColor(Color.WHITE);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "Samsung None";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        gestureActive = true;
        startX = screenX;
        startY = screenY;
        centerX = screenX;
        centerY = screenY;
        dragProgress = 0f;
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
        if (phase == PHASE_EXIT) {
            return;
        }
        float dx = screenX - startX;
        float dy = screenY - startY;
        float maxRadius = dp(MAX_DIAMETER_DP) * 0.5f;
        dragProgress = Timing.clamp01((float) Math.hypot(dx, dy) / Math.max(1f, maxRadius));
        invalidate();
    }

    @Override
    public void finishGesture(boolean completed) {
        if (destroyed || phase == PHASE_IDLE) {
            return;
        }
        gestureActive = false;
        startExit(SystemClock.uptimeMillis());
    }

    @Override
    public void cancelGesture() {
        if (!destroyed) {
            clearState();
            invalidate();
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
        if (pendingAffordance != null) {
            removeCallbacks(pendingAffordance);
        }
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
                dragProgress = 0f;
                phase = PHASE_AFFORDANCE_ENTER;
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
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (destroyed || phase == PHASE_IDLE) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float scale = 1f;
        float alpha = 1f;
        float fill = dragProgress;
        if (phase == PHASE_ENTER || phase == PHASE_AFFORDANCE_ENTER) {
            float progress = Timing.enterProgress(now - phaseStartedAt);
            scale = Timing.enterScale(progress);
            alpha = progress;
            if (progress >= 1f) {
                if (phase == PHASE_AFFORDANCE_ENTER) {
                    startExit(now);
                } else {
                    phase = PHASE_ACTIVE;
                }
            }
        } else if (phase == PHASE_EXIT) {
            float progress = Timing.exitProgress(now - phaseStartedAt);
            scale = Timing.exitScale(exitScale, progress);
            alpha = 1f - progress;
            fill = exitFill;
            if (progress >= 1f) {
                clearState();
                return;
            }
        }
        drawCircle(canvas, scale, alpha, fill);
        if (phase != PHASE_IDLE) {
            postInvalidateOnAnimation();
        }
    }

    private void startExit(long now) {
        if (phase == PHASE_EXIT || phase == PHASE_IDLE) {
            return;
        }
        exitScale = phase == PHASE_ENTER || phase == PHASE_AFFORDANCE_ENTER
                ? Timing.enterScale(Timing.enterProgress(now - phaseStartedAt)) : 1f;
        exitFill = dragProgress;
        phase = PHASE_EXIT;
        phaseStartedAt = now;
        invalidate();
    }

    private void drawCircle(Canvas canvas, float scale, float alpha, float fill) {
        float maxRadius = dp(MAX_DIAMETER_DP) * 0.5f * scale;
        float minRadius = Math.max(0f,
                dp(MAX_DIAMETER_DP - MIN_WIDTH_OFFSET_DP) * 0.5f * scale);
        outerStroke.setStrokeWidth(dp(OUTER_STROKE_DP) * scale);
        outerStroke.setAlpha(Math.round(170f * alpha));
        canvas.drawCircle(centerX, centerY, maxRadius, outerStroke);

        innerStroke.setStrokeWidth(dp(INNER_STROKE_DP) * scale);
        innerStroke.setAlpha(Math.round(255f * alpha));
        canvas.drawCircle(centerX, centerY, minRadius, innerStroke);

        int fillAlpha = Math.round(85f * alpha * fill);
        if (fillAlpha > 0) {
            fillStroke.setStrokeWidth(Math.max(dp(INNER_STROKE_DP), maxRadius * fill));
            fillStroke.setAlpha(fillAlpha);
            canvas.drawCircle(centerX, centerY, minRadius * (1f - fill * 0.35f), fillStroke);
        }

        float arrow = Math.max(dp(10f), minRadius * 0.18f);
        arrowStroke.setStrokeWidth(dp(ARROW_STROKE_DP) * scale);
        arrowStroke.setAlpha(Math.round(255f * alpha));
        canvas.drawLine(centerX - arrow, centerY + arrow * 0.25f,
                centerX, centerY - arrow * 0.75f, arrowStroke);
        canvas.drawLine(centerX, centerY - arrow * 0.75f,
                centerX + arrow, centerY + arrow * 0.25f, arrowStroke);
        outerStroke.setAlpha(255);
        innerStroke.setAlpha(255);
        fillStroke.setAlpha(255);
        arrowStroke.setAlpha(255);
    }

    private void clearState() {
        if (pendingAffordance != null) {
            removeCallbacks(pendingAffordance);
            pendingAffordance = null;
        }
        gestureActive = false;
        phase = PHASE_IDLE;
        dragProgress = 0f;
        exitScale = 1f;
        exitFill = 0f;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /** Pure timing seam used by host-side regression tests. */
    static final class Timing {
        static final long ENTER_DURATION_MS = 666L;
        static final long EXIT_DURATION_MS = 333L;

        private Timing() {
        }

        static float enterProgress(long elapsedMs) {
            return accelerateDecelerate(clamp01(elapsedMs / (float) ENTER_DURATION_MS));
        }

        static float exitProgress(long elapsedMs) {
            return accelerateDecelerate(clamp01(elapsedMs / (float) EXIT_DURATION_MS));
        }

        static float enterScale(float progress) {
            return 0.85f + 0.15f * clamp01(progress);
        }

        static float exitScale(float initialScale, float progress) {
            return initialScale + 0.08f * clamp01(progress);
        }

        static float clamp01(float value) {
            return Math.max(0f, Math.min(1f, value));
        }

        private static float accelerateDecelerate(float input) {
            return (float) (Math.cos((input + 1f) * Math.PI) * 0.5f + 0.5f);
        }
    }
}
