package com.codex.lle;

/** Wall-clock motion for the clean-room Revolving Glass beta. */
final class RevolvingGlassScene {
    static final long CANCEL_MS = 260L;
    static final long UNLOCK_MS = 620L;
    static final long AFFORDANCE_MS = 480L;
    static final float MAX_DRAG_RADIANS = 1.18f;

    static final class Frame {
        final boolean visible;
        final boolean animating;
        final float angleRadians;
        final float alpha;
        final float lift;
        final float shine;

        Frame(boolean visible, boolean animating, float angleRadians, float alpha,
                float lift, float shine) {
            this.visible = visible;
            this.animating = animating;
            this.angleRadians = angleRadians;
            this.alpha = alpha;
            this.lift = lift;
            this.shine = shine;
        }
    }

    private enum Phase { IDLE, HELD, CANCEL, UNLOCK, AFFORDANCE }

    private Phase phase = Phase.IDLE;
    private long phaseStartMs;
    private long clockMs;
    private float initialAngle;
    private float heldAngle;
    private float releaseAngle;

    void begin(float x, float y, long nowMs) {
        long now = normalize(nowMs);
        initialAngle = heldAngle * .22f;
        heldAngle = initialAngle;
        releaseAngle = heldAngle;
        phase = Phase.HELD;
        phaseStartMs = now;
    }

    void move(float downX, float x, float screenWidth, long nowMs) {
        normalize(nowMs);
        if (phase != Phase.HELD) {
            begin(x, 0f, nowMs);
            downX = x;
        }
        heldAngle = clamp(initialAngle + (x - downX) / Math.max(1f, screenWidth) * 2f,
                -MAX_DRAG_RADIANS, MAX_DRAG_RADIANS);
        releaseAngle = heldAngle;
    }

    void finish(boolean completed, long nowMs) {
        if (phase != Phase.HELD) {
            return;
        }
        phaseStartMs = normalize(nowMs);
        releaseAngle = heldAngle;
        phase = completed ? Phase.UNLOCK : Phase.CANCEL;
    }

    void affordance(long nowMs) {
        phaseStartMs = normalize(nowMs);
        initialAngle = heldAngle = releaseAngle = 0f;
        phase = Phase.AFFORDANCE;
    }

    void reset() {
        phase = Phase.IDLE;
        phaseStartMs = clockMs = 0L;
        initialAngle = heldAngle = releaseAngle = 0f;
    }

    Frame frameAt(long nowMs) {
        long now = normalize(nowMs);
        long elapsed = Math.max(0L, now - phaseStartMs);
        switch (phase) {
            case HELD:
                return new Frame(true, false, heldAngle, .94f,
                        .10f + .10f * Math.abs(heldAngle), .18f);
            case CANCEL:
                if (elapsed >= CANCEL_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                float cancel = easeOut(elapsed / (float) CANCEL_MS);
                return new Frame(true, true, lerp(releaseAngle, 0f, cancel), .94f,
                        .10f * (1f - cancel), .18f * (1f - cancel));
            case UNLOCK:
                if (elapsed >= UNLOCK_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                float unlock = elapsed / (float) UNLOCK_MS;
                float direction = releaseAngle < 0f ? -1f : 1f;
                float alpha = unlock < .68f ? .94f : .94f * (1f - (unlock - .68f) / .32f);
                return new Frame(true, true,
                        lerp(releaseAngle, direction * 3.35f, easeInOut(unlock)), alpha,
                        .10f + .18f * unlock, .18f + .62f * unlock);
            case AFFORDANCE:
                if (elapsed >= AFFORDANCE_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                float hint = elapsed / (float) AFFORDANCE_MS;
                float pulse = (float) Math.sin(Math.PI * hint);
                return new Frame(true, true, (float) Math.sin(hint * Math.PI * 2d) * .20f,
                        .30f * pulse, .05f, .32f * pulse);
            case IDLE:
            default:
                return hidden();
        }
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private long normalize(long nowMs) {
        clockMs = clockMs == 0L ? Math.max(1L, nowMs) : Math.max(clockMs, nowMs);
        return clockMs;
    }

    private static Frame hidden() {
        return new Frame(false, false, 0f, 0f, 0f, 0f);
    }

    private static float easeOut(float t) {
        float inverse = 1f - clamp(t, 0f, 1f);
        return 1f - inverse * inverse * inverse;
    }

    private static float easeInOut(float t) {
        t = clamp(t, 0f, 1f);
        return t < .5f ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3f) / 2f;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
