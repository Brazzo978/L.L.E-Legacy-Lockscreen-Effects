package com.codex.lle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * App-owned port of Samsung's {@code MassRippleUnlockTwin}, first verified in
 * the Galaxy S5 launch branch and exposed by the stock lockscreen picker as
 * "Stone Skipping".
 *
 * <p>The original renderer is a transparent FrameLayout containing up to six
 * white stroked ImageViews. Each ImageView runs the same 1300 ms scale/fade
 * animation. Drawing the equivalent rings in one View preserves the stock
 * geometry and timing without the obsolete SystemUI and DVFS dependencies.</p>
 */
final class StoneSkippingEffectView extends View implements UnlockEffectRenderer {
    private static final long RIPPLE_DURATION_MS = 1300L;
    private static final long SECOND_RING_DELAY_MS = 400L;
    private static final long LONG_PRESS_SOUND_MS = 600L;
    private static final int MAX_RIPPLE_SLOTS = 6;
    private static final int MAX_MOVING_RIPPLES = 3;

    private static final float MOVE_RATIO_STEP = 0.45f;
    private static final float NORMAL_DIAMETER_DP = 290f;
    private static final float AFFORDANCE_DIAMETER_DP = 224f;
    private static final float MOVING_DIAMETER_STEP = 0.20f;
    private static final float[] STROKE_DP = {49f, 26.6f, 37f, 30f};

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Ripple> ripples = new ArrayList<Ripple>(MAX_RIPPLE_SLOTS);
    private final SoundPool soundPool;
    private final int downSound;
    private final int upSound;

    private boolean destroyed;
    private boolean gestureActive;
    private float firstTouchX;
    private float firstTouchY;
    private float previousMovingRatio;
    private int movingRippleCount;
    private int movingStrokeSequence;
    private long pressStartedAt;

    private float pendingGestureSecondX;
    private float pendingGestureSecondY;
    private float pendingAffordanceX;
    private float pendingAffordanceY;

    private final Runnable gestureSecondRingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!destroyed) {
                addRipple(pendingGestureSecondX, pendingGestureSecondY, 1, false);
            }
        }
    };
    private final Runnable affordanceFirstRingRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed) {
                return;
            }
            movingRippleCount = 0;
            addRipple(pendingAffordanceX, pendingAffordanceY, 0, true);
            removeCallbacks(affordanceSecondRingRunnable);
            postDelayed(affordanceSecondRingRunnable, SECOND_RING_DELAY_MS);
        }
    };
    private final Runnable affordanceSecondRingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!destroyed) {
                addRipple(pendingAffordanceX, pendingAffordanceY, 1, true);
            }
        }
    };

    StoneSkippingEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.WHITE);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        downSound = soundPool.load(context, R.raw.stone_skipping_down, 1);
        upSound = soundPool.load(context, R.raw.stone_skipping_up, 1);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S5 Stone Skipping";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        gestureActive = true;
        firstTouchX = screenX;
        firstTouchY = screenY;
        previousMovingRatio = 0f;
        movingRippleCount = 0;
        pressStartedAt = SystemClock.uptimeMillis();

        play(downSound);
        addRipple(screenX, screenY, 0, false);
        pendingGestureSecondX = screenX;
        pendingGestureSecondY = screenY;
        removeCallbacks(gestureSecondRingRunnable);
        postDelayed(gestureSecondRingRunnable, SECOND_RING_DELAY_MS);
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
        float ratio = distanceRatio(screenX, screenY);
        if (ratio < MOVE_RATIO_STEP
                || Math.abs(previousMovingRatio - ratio) <= MOVE_RATIO_STEP
                || movingRippleCount >= MAX_MOVING_RIPPLES) {
            return;
        }
        previousMovingRatio = ratio;
        int strokeIndex = (movingStrokeSequence++ & 1) == 0 ? 2 : 3;
        addRipple(screenX, screenY, strokeIndex, false);
        movingRippleCount++;
        play(upSound);
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        if (SystemClock.uptimeMillis() - pressStartedAt > LONG_PRESS_SOUND_MS) {
            play(downSound);
        }
        firstTouchX = 0f;
        firstTouchY = 0f;
        previousMovingRatio = 0f;
    }

    @Override
    public void cancelGesture() {
        finishGesture(false);
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        firstTouchX = 0f;
        firstTouchY = 0f;
        previousMovingRatio = 0f;
        movingRippleCount = 0;
        removeCallbacks(gestureSecondRingRunnable);
        removeCallbacks(affordanceFirstRingRunnable);
        removeCallbacks(affordanceSecondRingRunnable);
        ripples.clear();
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
        if (destroyed || screenRect == null) {
            return;
        }
        pendingAffordanceX = screenRect.exactCenterX();
        pendingAffordanceY = screenRect.exactCenterY();
        removeCallbacks(affordanceFirstRingRunnable);
        removeCallbacks(affordanceSecondRingRunnable);
        // MassRippleUnlockTwin receives startDelay but intentionally draws the first
        // affordance ring immediately; only its twin is delayed by 400 ms.
        post(affordanceFirstRingRunnable);
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        resetEffect();
        soundPool.release();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (ripples.isEmpty()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        Iterator<Ripple> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            float progress = (now - ripple.startedAt) / (float) RIPPLE_DURATION_MS;
            if (progress >= 1f) {
                iterator.remove();
                continue;
            }
            progress = Math.max(0f, progress);
            float decelerated = 1f - (1f - progress) * (1f - progress);
            float remaining = 1f - progress;
            float radius = ripple.diameterPx * 0.5f * decelerated;
            float stroke = Math.max(1f,
                    ripple.strokePx * remaining * Math.max(0.01f, decelerated));
            int alpha = Math.max(0, Math.min(255,
                    Math.round(255f * (1f - decelerated))));
            ringPaint.setStrokeWidth(stroke);
            ringPaint.setAlpha(alpha);
            // MassRippleImageView builds the oval inset by one complete stroke width,
            // then lets the View animation scale the drawable and its stroke together.
            canvas.drawCircle(ripple.x, ripple.y, Math.max(0f, radius - stroke),
                    ringPaint);
        }
        ringPaint.setAlpha(255);
        if (!ripples.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }

    private void addRipple(float x, float y, int strokeIndex, boolean affordance) {
        if (destroyed || movingRippleCount > 2) {
            return;
        }
        if (ripples.size() >= MAX_RIPPLE_SLOTS) {
            ripples.remove(0);
        }
        float diameterDp = affordance
                ? AFFORDANCE_DIAMETER_DP
                : NORMAL_DIAMETER_DP
                        * (1f - MOVING_DIAMETER_STEP * movingRippleCount);
        int safeStrokeIndex = Math.max(0, Math.min(STROKE_DP.length - 1, strokeIndex));
        ripples.add(new Ripple(
                x,
                y,
                dp(diameterDp),
                dp(STROKE_DP[safeStrokeIndex]),
                SystemClock.uptimeMillis()));
        postInvalidateOnAnimation();
    }

    private float distanceRatio(float x, float y) {
        float dx = firstTouchX - x;
        float dy = firstTouchY - y;
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            width = getResources().getDisplayMetrics().widthPixels;
            height = getResources().getDisplayMetrics().heightPixels;
        }
        float threshold = Math.max(1f, Math.min(width, height) * 0.5f);
        return (float) Math.hypot(dx, dy) / threshold;
    }

    private void play(int soundId) {
        if (destroyed || soundId == 0
                || !OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            return;
        }
        soundPool.play(soundId, 1f, 1f, 0, 0, 1f);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static final class Ripple {
        final float x;
        final float y;
        final float diameterPx;
        final float strokePx;
        final long startedAt;

        Ripple(float x, float y, float diameterPx, float strokePx, long startedAt) {
            this.x = x;
            this.y = y;
            this.diameterPx = diameterPx;
            this.strokePx = strokePx;
            this.startedAt = startedAt;
        }
    }
}
