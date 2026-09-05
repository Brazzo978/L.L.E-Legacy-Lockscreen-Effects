package com.codex.lle;

/**
 * Deterministic scalar state for the LG G4 Hula Hoop V2 (FluidicRenderer).
 *
 * <p>The original renderer is OpenGL-based. This class preserves its gesture thresholds,
 * stretch delay, radius limits and terminal clocks while the View owns the Canvas meshes.
 * Keeping the clock here also guarantees that a cancelled gesture cannot leave a stale
 * overlay attached.</p>
 */
final class LgHulaHoopFluidicScene {
    static final long CANCEL_MS = 250L;
    static final long UNLOCK_MS = 250L;
    static final long UNDERLAY_HOLD_MS = 550L;
    static final int IDLE = 0;
    static final int ACTIVE = 1;
    static final int CANCEL = 2;
    static final int COMPLETE = 3;
    static final int STRETCH_DELAY_FRAMES = 5;
    static final float MIN_RADIUS_DP = 50.199982f;
    static final float OUTER_RING_STRIDE_DP = 15f;
    static final float STRETCH_SPEED_PX_PER_MS = .2f;
    static final float MAX_STRETCH_RATIO = 2f;

    private int width = 1;
    private int height = 1;
    private float density = 1f;
    private int stage;
    private float downX;
    private float downY;
    private float radius;
    private float dragDistance;
    private float previousDragDistance;
    private float angle;
    private long previousTouchAt;
    private long terminalAt;
    private float radiusStartValue;
    private float maxRingSize;
    private boolean stretched;
    private boolean unlock;
    private boolean softbody = true;
    private int stretchDelayFrames;
    private int rotationDelayFrames;
    private long lastRenderedAt = Long.MIN_VALUE;

    void configure(int width, int height, float density) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.density = finite(density) && density > 0f ? density : 1f;
    }

    int state() { return stage; }
    boolean gestureActive() { return stage == ACTIVE; }
    float minimumRadius() { return MIN_RADIUS_DP * density; }
    float outerRingStride() { return OUTER_RING_STRIDE_DP * density; }
    float maxDistance() { return (float) Math.hypot(width, height); }

    void begin(float x, float y, long now) {
        if (!finite(x) || !finite(y)) return;
        stage = ACTIVE;
        downX = clamp(x, 0f, width);
        downY = clamp(y, 0f, height);
        radius = minimumRadius();
        dragDistance = radius;
        previousDragDistance = 0f;
        angle = 0f;
        previousTouchAt = now;
        terminalAt = 0L;
        radiusStartValue = maxRingSize = 0f;
        stretched = unlock = false;
        softbody = true;
        stretchDelayFrames = rotationDelayFrames = 0;
        lastRenderedAt = Long.MIN_VALUE;
    }

    void move(float x, float y, long now) {
        if (stage != ACTIVE || !finite(x) || !finite(y)) return;
        float nextX = clamp(x, 0f, width);
        float nextY = clamp(y, 0f, height);
        float nextDistance = (float) Math.hypot(nextX - downX, nextY - downY);
        long elapsed = Math.max(1L, now - previousTouchAt);
        float speed = Math.abs(previousDragDistance - nextDistance) / elapsed;
        // FluidicRenderer rotates the deformation axis in GL space, whose Y axis is
        // opposite Android Canvas coordinates. Preserve the donor's explicit minus
        // sign or diagonal drags bend the hoop in the mirrored direction.
        angle = (float) -Math.toDegrees(Math.atan2(nextY - downY, nextX - downX));

        if (speed < STRETCH_SPEED_PX_PER_MS) {
            radius = Math.max(nextDistance, minimumRadius());
            dragDistance = radius;
            if (stretched) rotationDelayFrames = STRETCH_DELAY_FRAMES;
            stretched = false;
        } else {
            radius = Math.max(radius, minimumRadius());
            if (nextDistance < minimumRadius()) {
                dragDistance = radius;
                stretched = false;
            } else if (nextDistance > radius) {
                if (!stretched) stretchDelayFrames = STRETCH_DELAY_FRAMES;
                stretched = true;
                dragDistance = nextDistance;
            } else {
                radius = nextDistance;
                dragDistance = nextDistance;
                stretched = false;
            }
        }
        if (radius > 0f && dragDistance / radius > MAX_STRETCH_RATIO) {
            radius = dragDistance / MAX_STRETCH_RATIO;
        }
        previousDragDistance = nextDistance;
        previousTouchAt = now;
    }

    void finish(boolean completed, long now) {
        if (stage != ACTIVE) return;
        terminalAt = now;
        lastRenderedAt = Long.MIN_VALUE;
        if (completed) {
            stage = COMPLETE;
            stretched = false;
            unlock = true;
            rotationDelayFrames = STRETCH_DELAY_FRAMES;
            float bounce = clamp(dragDistance / maxDistance(), .5f, 1f);
            radiusStartValue = radius * bounce;
            maxRingSize = maxDistance() * (.7f + bounce);
        } else {
            stage = CANCEL;
            radiusStartValue = radius;
            softbody = false;
            unlock = false;
        }
    }

    void reset() {
        stage = IDLE;
        downX = downY = radius = dragDistance = previousDragDistance = angle = 0f;
        previousTouchAt = terminalAt = 0L;
        radiusStartValue = maxRingSize = 0f;
        stretched = unlock = false;
        softbody = true;
        stretchDelayFrames = rotationDelayFrames = 0;
        lastRenderedAt = Long.MIN_VALUE;
    }

    Frame sample(long now, Frame out) {
        out.clear();
        if (stage == IDLE) return out;
        if (now != lastRenderedAt) {
            if (stretchDelayFrames > 0) stretchDelayFrames--;
            if (rotationDelayFrames > 0) rotationDelayFrames--;
            lastRenderedAt = now;
        }

        long terminalAge = Math.max(0L, now - terminalAt);
        if (stage == CANCEL) {
            if (terminalAge >= CANCEL_MS) {
                reset();
                return out;
            }
            float t = clamp(terminalAge / (float) CANCEL_MS, 0f, 1f);
            radius = lerp(radiusStartValue, 0f, t);
            dragDistance = radius;
            out.drawColors = t <= .8f;
        } else if (stage == COMPLETE) {
            if (terminalAge >= UNLOCK_MS + UNDERLAY_HOLD_MS) {
                reset();
                return out;
            }
            if (terminalAge < UNLOCK_MS) {
                float t = clamp(terminalAge / (float) UNLOCK_MS, 0f, 1f);
                radius = lerp(radiusStartValue, maxRingSize, t);
                dragDistance = radius;
                out.drawColors = true;
            } else {
                radius = dragDistance = maxRingSize;
                out.fullUnderlay = true;
                out.drawColors = false;
            }
        } else {
            out.drawColors = true;
        }

        out.visible = out.running = true;
        out.stage = stage;
        out.x = downX;
        out.y = downY;
        out.radius = Math.max(0f, radius);
        out.dragDistance = Math.max(0f, dragDistance);
        out.angle = angle;
        out.stretched = stretched;
        out.unlock = unlock;
        out.softbody = softbody;
        out.stretchDelayFrames = stretchDelayFrames;
        out.rotationDelayFrames = rotationDelayFrames;
        out.terminalAgeMs = terminalAge;
        return out;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static final class Frame {
        boolean visible;
        boolean running;
        boolean fullUnderlay;
        boolean drawColors;
        boolean stretched;
        boolean unlock;
        boolean softbody;
        int stage;
        int stretchDelayFrames;
        int rotationDelayFrames;
        long terminalAgeMs;
        float x;
        float y;
        float radius;
        float dragDistance;
        float angle;

        void clear() {
            visible = running = fullUnderlay = drawColors = stretched = unlock = false;
            softbody = true;
            stage = IDLE;
            stretchDelayFrames = rotationDelayFrames = 0;
            terminalAgeMs = 0L;
            x = y = radius = dragDistance = angle = 0f;
        }
    }
}
