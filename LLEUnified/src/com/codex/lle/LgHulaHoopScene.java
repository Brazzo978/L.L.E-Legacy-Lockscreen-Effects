package com.codex.lle;

/**
 * Deterministic clock and geometry for LG's Hula Hoop / Color Layered effect.
 *
 * <p>The constants and motion stages are translated from the E975T LockScreen3 ODEX. Keeping
 * the clock outside the View prevents a cancelled hint, display switch or late UP from leaving
 * a permanently attached animation behind.</p>
 */
final class LgHulaHoopScene {
    static final long CANCEL_MS = 300L;
    static final long UNLOCK_MS = 600L;
    static final long UNDERLAY_HOLD_MS = 550L;
    static final long HINT_MS = 1_500L;
    static final long PING_START_DELAY_MS = 500L;
    static final long PING_EXPAND_MS = 630L;
    static final long PING_SECOND_DELAY_MS = 230L;
    static final int IDLE = 0;
    static final int ACTIVE = 1;
    static final int CANCEL = 2;
    static final int COMPLETE = 3;
    static final int HINT = 4;
    static final float[] LAYER_TRANSITION = {1.3f, 1.2f, 1.1f, .9f};
    static final float[] LAYER_ANGLE_OFFSET = {0f, 90f, 180f, 270f};

    private int width = 1;
    private int height = 1;
    private float density = 1f;
    private int stage;
    private long startedAt;
    private long terminalAt;
    private float downX;
    private float downY;
    private float fingerX;
    private float fingerY;
    private float terminalDx;
    private float terminalDy;
    private float terminalDistance;
    private float previousFingerX;
    private float previousFingerY;
    private float currentAngle;
    private float currentPivotAngle;
    private float destinationPivotAngle;
    private long lastSampleAt;
    private float transitionPerDot;
    private float velocityThreshold;
    private float trailX;
    private float trailY;
    private long lastTouchAt;
    private long lastPhysicsAt;

    LgHulaHoopScene() {}

    void configure(int width, int height, float density) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.density = finite(density) && density > 0f ? density : 1f;
        transitionPerDot = thresholdRadius() / Math.max(1f,
                5f * (float) Math.hypot(this.width, this.height));
        velocityThreshold = 16f * this.density;
    }

    int state() { return stage; }
    boolean gestureActive() { return stage == ACTIVE; }
    float minimumRadius() { return 50.2f * density; }
    float thresholdRadius() { return 128f * density; }

    void begin(float x, float y, long now) {
        if (!finite(x) || !finite(y)) return;
        stage = ACTIVE;
        startedAt = now;
        terminalAt = 0L;
        downX = fingerX = clamp(x, 0f, width);
        downY = fingerY = clamp(y, 0f, height);
        previousFingerX = fingerX;
        previousFingerY = fingerY;
        currentAngle = 180f;
        currentPivotAngle = 0f;
        destinationPivotAngle = 5f;
        lastSampleAt = now;
        lastTouchAt = lastPhysicsAt = now;
        trailX = trailY = 0f;
        terminalDx = terminalDy = terminalDistance = 0f;
    }

    void move(float x, float y) {
        move(x, y, Math.max(lastTouchAt + 16L, startedAt + 16L));
    }

    void move(float x, float y, long now) {
        if (stage != ACTIVE || !finite(x) || !finite(y)) return;
        float nextX = clamp(x, 0f, width);
        float nextY = clamp(y, 0f, height);
        float deltaX = nextX - previousFingerX;
        float deltaY = nextY - previousFingerY;
        float dragX = nextX - downX;
        float dragY = nextY - downY;
        if (dragX != 0f || dragY != 0f) {
            // The donor aims the common pivot from the original DOWN point, not from the
            // direction of the latest MotionEvent segment.
            destinationPivotAngle = normalizeDegrees((float) Math.toDegrees(
                    Math.atan2(dragY, dragX)) + 180f);
        }
        long elapsed = Math.max(1L, now - lastTouchAt);
        float velocityX = deltaX * 1_000f / elapsed;
        float velocityY = deltaY * 1_000f / elapsed;
        // ColorLayeredCircleEffect only adds fast samples to its small, display-normalized
        // holder trail. Slow samples let the existing trail return toward the fixed center.
        if (Math.abs(velocityX) >= velocityThreshold) {
            trailX += (previousFingerX - nextX) * transitionPerDot;
        }
        if (Math.abs(velocityY) >= velocityThreshold) {
            trailY += (previousFingerY - nextY) * transitionPerDot;
        }
        fingerX = previousFingerX = nextX;
        fingerY = previousFingerY = nextY;
        lastTouchAt = now;
    }

    void finish(boolean completed, long now) {
        if (stage != ACTIVE) return;
        terminalDx = fingerX - downX;
        terminalDy = fingerY - downY;
        terminalDistance = distance(terminalDx, terminalDy);
        terminalAt = now;
        stage = completed ? COMPLETE : CANCEL;
    }

    void startHint(float x, float y, long now) {
        if (!finite(x) || !finite(y)) return;
        stage = HINT;
        startedAt = now;
        terminalAt = 0L;
        downX = fingerX = clamp(x, 0f, width);
        downY = fingerY = clamp(y, 0f, height);
        previousFingerX = fingerX;
        previousFingerY = fingerY;
        currentAngle = 180f;
        currentPivotAngle = 0f;
        destinationPivotAngle = 5f;
        lastSampleAt = now;
        lastTouchAt = lastPhysicsAt = now;
        trailX = trailY = 0f;
        terminalDx = terminalDy = terminalDistance = 0f;
    }

    void reset() {
        stage = IDLE;
        startedAt = terminalAt = 0L;
        lastSampleAt = 0L;
        lastTouchAt = lastPhysicsAt = 0L;
        currentAngle = currentPivotAngle = destinationPivotAngle = 0f;
        trailX = trailY = 0f;
        terminalDx = terminalDy = terminalDistance = 0f;
    }

    Frame sample(long now, Frame out) {
        out.clear();
        if (stage == IDLE) return out;
        advanceTrail(now);
        long age = Math.max(0L, now - startedAt);
        if (stage == HINT) {
            if (age >= HINT_MS) {
                reset();
                return out;
            }
            out.visible = out.running = true;
            out.stage = HINT;
            out.x = downX;
            out.y = downY;
            out.ageMs = age;
            out.iconAlpha = 1f;
            out.ringAlpha = .9f;
            out.backgroundAlpha = 1f;
            out.radius = minimumRadius();
            out.layerScale = 1.2f;
            out.layerRadius = out.radius * out.layerScale;
            out.rotationPeriodMs = 2_500f;
            advanceAngles(now, out.rotationPeriodMs);
            out.angle = currentAngle;
            out.pivotAngle = currentPivotAngle;
            out.pivotRadius = .5f * Math.abs(out.layerRadius - out.radius);
            out.pivotX = polarX(out.pivotRadius, out.pivotAngle);
            out.pivotY = polarY(out.pivotRadius, out.pivotAngle);
            out.trailX = trailX;
            out.trailY = trailY;
            out.ping1 = ping(age - PING_START_DELAY_MS);
            out.ping2 = ping(age - PING_START_DELAY_MS - PING_SECOND_DELAY_MS);
            return out;
        }

        long terminalAge = Math.max(0L, now - terminalAt);
        if (stage == CANCEL && terminalAge >= CANCEL_MS
                || stage == COMPLETE && terminalAge >= UNLOCK_MS + UNDERLAY_HOLD_MS) {
            reset();
            return out;
        }

        float dx = fingerX - downX;
        float dy = fingerY - downY;
        float drag = distance(dx, dy);
        if (stage == CANCEL) {
            float t = clamp(terminalAge / (float) CANCEL_MS, 0f, 1f);
            float close = circleClose(t);
            float remaining = 1f - clamp(close, 0f, 1f);
            dx = terminalDx * remaining;
            dy = terminalDy * remaining;
            drag = terminalDistance * remaining;
            out.ringAlpha = remaining;
            out.backgroundAlpha = remaining;
            out.iconAlpha = remaining;
        } else if (stage == COMPLETE) {
            dx = terminalDx;
            dy = terminalDy;
            drag = terminalDistance;
            out.ringAlpha = 1f - clamp(terminalAge / 300f, 0f, 1f);
            out.backgroundAlpha = out.ringAlpha;
            out.iconAlpha = 0f;
        } else {
            out.ringAlpha = 1f;
            out.backgroundAlpha = 1f;
            out.iconAlpha = 1f - clamp(drag / thresholdRadius(), 0f, 1f);
        }

        out.visible = out.running = true;
        out.stage = stage;
        // Color Layered V1 keeps LG's opening anchored at ACTION_DOWN. Fluidic V2 has its own
        // scene because its fixed center drives a deformable soft-body mesh, not this circle.
        out.x = downX;
        out.y = downY;
        out.dx = dx;
        out.dy = dy;
        out.distance = drag;
        out.ageMs = age;
        out.terminalAgeMs = terminalAge;
        out.radius = dragRadius(drag);
        float radiusProgress = clamp((out.radius - minimumRadius())
                / Math.max(1f, thresholdRadius() - minimumRadius()), 0f, 1f);
        float deceleratedRadius = 1f - (1f - radiusProgress) * (1f - radiusProgress);
        // ColorLayeredCircleEffect.setRadius(): colored circle radius is 1.3x the hole near the
        // minimum and eases to 1.2x at the maximum. Its first 300 ms use the donor's tension-2
        // OvershootInterpolator, starting at 30 percent of the destination scale.
        out.layerScale = (1.3f - .1f * deceleratedRadius) * introScale(age);
        out.layerRadius = out.radius * out.layerScale;
        // The donor is fastest near the minimum radius and slows as the opening grows.
        out.rotationPeriodMs = 700f + 1_800f * deceleratedRadius;
        advanceAngles(now, out.rotationPeriodMs);
        out.angle = currentAngle;
        out.pivotAngle = currentPivotAngle;
        out.pivotRadius = .5f * Math.abs(out.layerRadius - out.radius);
        out.pivotX = polarX(out.pivotRadius, out.pivotAngle);
        out.pivotY = polarY(out.pivotRadius, out.pivotAngle);
        out.trailX = trailX;
        out.trailY = trailY;

        if (stage == COMPLETE) {
            float t = clamp(terminalAge / (float) UNLOCK_MS, 0f, 1f);
            float target = farthestCornerRadius(out.x, out.y) + 8f;
            out.radius = out.radius + (target - out.radius) * t; // stock unlock clock is linear
            out.fullUnderlay = terminalAge >= UNLOCK_MS;
            if (out.fullUnderlay) out.radius = target;
        }
        return out;
    }

    float farthestCornerRadius(float x, float y) {
        return (float) Math.hypot(Math.max(x, width - x), Math.max(y, height - y));
    }

    float dragRadius(float drag) {
        float threshold = thresholdRadius();
        if (drag >= threshold) return drag;
        return minimumRadius() + drag / threshold * (threshold - minimumRadius());
    }

    static float ping(long elapsed) {
        if (elapsed < 0L || elapsed >= PING_EXPAND_MS) return 0f;
        return clamp(elapsed / (float) PING_EXPAND_MS, 0f, 1f);
    }

    /** Anticipate(tension=1) followed by Decelerate(factor=1), as in the donor. */
    static float circleClose(float t) {
        t = clamp(t, 0f, 1f);
        float anticipate = t * t * (2f * t - 1f);
        return 1f - (1f - anticipate) * (1f - anticipate);
    }

    static float rotationDegrees(long elapsedMs, float periodMs) {
        return (elapsedMs % Math.max(1L, (long) periodMs)) * 360f / periodMs;
    }

    private void advanceAngles(long now, float rotationPeriodMs) {
        long elapsed = lastSampleAt == 0L ? 0L : Math.max(0L, now - lastSampleAt);
        lastSampleAt = now;
        if (elapsed <= 0L) return;
        currentAngle = normalizeDegrees(currentAngle + elapsed * 360f
                / Math.max(1f, rotationPeriodMs));
        float difference = shortestAngle(destinationPivotAngle - currentPivotAngle);
        float maxStep = 5f * elapsed / 16f;
        if (Math.abs(difference) <= maxStep) currentPivotAngle = destinationPivotAngle;
        else currentPivotAngle = normalizeDegrees(currentPivotAngle
                + Math.copySign(maxStep, difference));
    }

    private void advanceTrail(long now) {
        long elapsed = lastPhysicsAt == 0L ? 0L : Math.max(0L, now - lastPhysicsAt);
        lastPhysicsAt = now;
        if (elapsed <= 0L || now - lastTouchAt < 160L) return;
        // The stock handler applies one thirty-first of the remaining displacement on every
        // nominal 16 ms update. The exponential form preserves that behavior across dropped
        // frames instead of making the return depend on the display refresh rate.
        float decay = (float) Math.pow(30f / 31f, elapsed / 16f);
        trailX *= decay;
        trailY *= decay;
        if (Math.abs(trailX) * LAYER_TRANSITION[0] < 1f) trailX = 0f;
        if (Math.abs(trailY) * LAYER_TRANSITION[0] < 1f) trailY = 0f;
    }

    private static float introScale(long ageMs) {
        if (ageMs >= 300L) return 1f;
        float t = clamp(ageMs / 300f, 0f, 1f) - 1f;
        float overshoot = t * t * (3f * t + 2f) + 1f; // tension = 2
        return .3f + .7f * overshoot;
    }

    private static float polarX(float radius, float degrees) {
        return radius * (float) Math.cos(Math.toRadians(degrees));
    }

    private static float polarY(float radius, float degrees) {
        return radius * (float) Math.sin(Math.toRadians(degrees));
    }

    private static float normalizeDegrees(float degrees) {
        degrees %= 360f;
        return degrees < 0f ? degrees + 360f : degrees;
    }

    private static float shortestAngle(float degrees) {
        degrees = normalizeDegrees(degrees);
        return degrees > 180f ? degrees - 360f : degrees;
    }

    private static float distance(float x, float y) {
        return (float) Math.hypot(x, y);
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static final class Frame {
        boolean visible;
        boolean running;
        boolean fullUnderlay;
        int stage;
        long ageMs;
        long terminalAgeMs;
        float x;
        float y;
        float dx;
        float dy;
        float distance;
        float radius;
        float layerScale;
        float layerRadius;
        float angle;
        float rotationPeriodMs;
        float pivotAngle;
        float pivotRadius;
        float pivotX;
        float pivotY;
        float trailX;
        float trailY;
        float ringAlpha;
        float backgroundAlpha;
        float iconAlpha;
        float ping1;
        float ping2;

        void clear() {
            visible = running = fullUnderlay = false;
            stage = IDLE;
            ageMs = terminalAgeMs = 0L;
            x = y = dx = dy = distance = radius = angle = ringAlpha = backgroundAlpha = 0f;
            layerRadius = rotationPeriodMs = pivotAngle = pivotRadius = pivotX = pivotY = 0f;
            trailX = trailY = 0f;
            iconAlpha = ping1 = ping2 = 0f;
            layerScale = 1f;
        }
    }
}
