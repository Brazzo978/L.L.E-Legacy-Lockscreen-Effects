package com.codex.lle;

/** Deterministic wall-clock reconstruction of the donor Revolving Glass motion. */
final class RevolvingGlassScene {
    static final long UNLOCK_MS = 620L;
    static final long TILE_ENTER_MS = 140L;
    static final long UNLOCK_TAIL_MS = 180L;
    static final long TILE_EXIT_MS = 160L;
    static final float TILE_EXIT_SCALE = .84f;
    static final long UNDERLAY_HOLD_MS = 1_200L;
    static final long AFFORDANCE_MS = 480L;
    static final long CANCEL_TICK_MS = 10L;
    static final long CANCEL_MAX_MS = 3_200L;
    static final float CENTER_DEGREES_PER_PIXEL = .02f;
    static final float DRAG_DEGREES_PER_PIXEL = .44f;
    static final float MAX_DRAG_DEGREES_PER_MS = .9f;
    static final float INITIAL_DEGREES_PER_MS = .13f;
    static final long UNLOCK_TICK_MS = 12L;
    static final float UNLOCK_FAST_STEP_DEGREES = 6f;
    static final float UNLOCK_SLOW_STEP_DEGREES = 4f;
    static final float MOVE_SLOP_PX = 20f;
    static final float MAX_ANGLE_DEGREES = 179f;

    static final class Frame {
        final boolean visible;
        final boolean tileVisible;
        final boolean animating;
        final float angleDegrees;
        final float tileScale;
        final float tileAlpha;
        final float underlayAlpha;

        Frame(boolean visible, boolean tileVisible, boolean animating, float angleDegrees,
                float tileScale, float tileAlpha, float underlayAlpha) {
            this.visible = visible;
            this.tileVisible = tileVisible;
            this.animating = animating;
            this.angleDegrees = angleDegrees;
            this.tileScale = tileScale;
            this.tileAlpha = tileAlpha;
            this.underlayAlpha = underlayAlpha;
        }

        float angleRadians() {
            return (float) Math.toRadians(angleDegrees);
        }
    }

    private static final class CancelFrame {
        final float angle;
        final boolean finished;

        CancelFrame(float angle, boolean finished) {
            this.angle = angle;
            this.finished = finished;
        }
    }

    private enum Phase { IDLE, HELD, CANCEL, UNLOCK, AFFORDANCE }

    private Phase phase = Phase.IDLE;
    private long phaseStartMs;
    private long gestureStartMs;
    private long clockMs;
    private float baseAngle;
    private float heldAngle;
    private float releaseAngle;
    private long unlockTurnMs;
    private float unlockFastDistance;
    private long unlockFastMs;
    private long lastMoveMs;
    private boolean dragged;

    void begin(float x, float screenWidth, long nowMs) {
        long now = normalize(nowMs);
        baseAngle = clamp((x - Math.max(1f, screenWidth) * .5f)
                * CENTER_DEGREES_PER_PIXEL, -MAX_ANGLE_DEGREES, MAX_ANGLE_DEGREES);
        heldAngle = 0f;
        releaseAngle = 0f;
        lastMoveMs = now;
        dragged = false;
        phase = Phase.HELD;
        phaseStartMs = now;
        gestureStartMs = now;
    }

    void move(float downX, float x, float screenWidth, long nowMs) {
        long now = normalize(nowMs);
        if (phase != Phase.HELD) {
            begin(downX, screenWidth, now);
        }
        float delta = x - downX;
        if (Math.abs(delta) <= MOVE_SLOP_PX && !dragged) {
            heldAngle = initialAngleAt(now);
            return;
        }
        dragged = true;
        float target = clamp(baseAngle + delta * DRAG_DEGREES_PER_PIXEL,
                -MAX_ANGLE_DEGREES, MAX_ANGLE_DEGREES);
        float maxTravel = Math.max(1L, now - lastMoveMs) * MAX_DRAG_DEGREES_PER_MS;
        heldAngle = approach(heldAngle, target, maxTravel);
        lastMoveMs = now;
    }

    /** Returns the donor-style delay before the unlock sound should be played. */
    long finish(boolean completed, long nowMs) {
        if (phase != Phase.HELD) {
            return 0L;
        }
        long now = normalize(nowMs);
        releaseAngle = dragged ? heldAngle : initialAngleAt(now);
        heldAngle = releaseAngle;
        phaseStartMs = now;
        if (!completed) {
            // The donor halves only a positive return angle above 40 degrees.
            if (releaseAngle > 40f) releaseAngle *= .5f;
            phase = Phase.CANCEL;
            unlockTurnMs = 0L;
            return 0L;
        }
        phase = Phase.UNLOCK;
        float remaining = Math.max(0f, 180f - Math.abs(releaseAngle));
        int fastTicks = (int) Math.ceil(
                (remaining * 7f / 8f) / UNLOCK_FAST_STEP_DEGREES);
        unlockFastDistance = Math.min(remaining,
                fastTicks * UNLOCK_FAST_STEP_DEGREES);
        int slowTicks = (int) Math.ceil(
                Math.max(0f, remaining - unlockFastDistance)
                        / UNLOCK_SLOW_STEP_DEGREES);
        unlockFastMs = fastTicks * UNLOCK_TICK_MS;
        unlockTurnMs = Math.min(UNLOCK_MS,
                unlockFastMs + slowTicks * UNLOCK_TICK_MS);
        return unlockTurnMs;
    }

    void affordance(long nowMs) {
        phaseStartMs = normalize(nowMs);
        gestureStartMs = phaseStartMs;
        baseAngle = heldAngle = releaseAngle = 0f;
        unlockTurnMs = 0L;
        dragged = false;
        phase = Phase.AFFORDANCE;
    }

    void reset() {
        phase = Phase.IDLE;
        phaseStartMs = gestureStartMs = clockMs = unlockTurnMs = unlockFastMs
                = lastMoveMs = 0L;
        baseAngle = heldAngle = releaseAngle = 0f;
        unlockFastDistance = 0f;
        dragged = false;
    }

    Frame frameAt(long nowMs) {
        long now = normalize(nowMs);
        long elapsed = Math.max(0L, now - phaseStartMs);
        float entryAlpha = entryAlphaAt(now);
        switch (phase) {
            case HELD:
                heldAngle = dragged ? heldAngle : initialAngleAt(now);
                return new Frame(true, true,
                        entryAlpha < .999f || (!dragged && heldAngle != baseAngle),
                        heldAngle, 1f, entryAlpha, entryAlpha);
            case CANCEL:
                CancelFrame cancel = cancelFrameAt(releaseAngle, elapsed);
                if (cancel.finished || elapsed >= CANCEL_MAX_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                return new Frame(true, true, true, cancel.angle,
                        1f, entryAlpha, entryAlpha);
            case UNLOCK:
                if (elapsed >= UNDERLAY_HOLD_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                float direction = releaseAngle < 0f ? -1f : 1f;
                float remaining = 180f - Math.abs(releaseAngle);
                float travel = elapsed <= unlockFastMs
                        ? elapsed * (UNLOCK_FAST_STEP_DEGREES / UNLOCK_TICK_MS)
                        : unlockFastDistance
                                + (elapsed - unlockFastMs)
                                * (UNLOCK_SLOW_STEP_DEGREES / UNLOCK_TICK_MS);
                travel = Math.min(remaining, travel);
                long tileEndMs = Math.min(UNLOCK_MS, unlockTurnMs + UNLOCK_TAIL_MS);
                long exitStartMs = Math.max(0L, tileEndMs - TILE_EXIT_MS);
                float exitT = clamp((elapsed - exitStartMs) / (float) TILE_EXIT_MS,
                        0f, 1f);
                float exitEase = smoothStep(exitT);
                float tileScale = 1f - (1f - TILE_EXIT_SCALE) * exitEase;
                float tileAlpha = 1f - exitEase;
                boolean tileVisible = elapsed < tileEndMs;
                return new Frame(true, tileVisible, true,
                        releaseAngle + direction * travel, tileScale,
                        tileAlpha * entryAlpha, entryAlpha);
            case AFFORDANCE:
                if (elapsed >= AFFORDANCE_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                float t = elapsed / (float) AFFORDANCE_MS;
                float envelope = (float) Math.sin(Math.PI * t);
                float angle = (float) Math.sin(t * Math.PI * 2d) * 9f * envelope;
                return new Frame(true, true, true, angle,
                        1f, entryAlpha, entryAlpha);
            case IDLE:
            default:
                return hidden();
        }
    }

    long unlockTurnDurationMs() {
        return unlockTurnMs;
    }

    private float initialAngleAt(long now) {
        float travel = Math.max(0L, now - phaseStartMs) * INITIAL_DEGREES_PER_MS;
        return approach(0f, baseAngle, travel);
    }

    private static CancelFrame cancelFrameAt(float start, long elapsedMs) {
        float position = clamp(start, -MAX_ANGLE_DEGREES, MAX_ANGLE_DEGREES);
        float amplitude = Math.abs(position);
        if (amplitude <= 1f && elapsedMs >= CANCEL_TICK_MS) {
            return new CancelFrame(0f, true);
        }
        if (elapsedMs <= 0L) {
            return new CancelFrame(position, false);
        }
        int ticks = (int) Math.min(CANCEL_MAX_MS / CANCEL_TICK_MS,
                elapsedMs / CANCEL_TICK_MS);
        float direction = position > 0f ? -1f : 1f;
        float target = direction * amplitude;
        for (int tick = 0; tick < ticks; tick++) {
            float speed = cancelSpeed(amplitude);
            float next = position + direction * speed;
            if ((direction > 0f && next >= target) || (direction < 0f && next <= target)) {
                position = target;
                amplitude = Math.max(0f, amplitude - cancelAttenuation(amplitude));
                if (amplitude <= 1f) {
                    return new CancelFrame(0f, true);
                }
                direction = -direction;
                target = direction * amplitude;
            } else {
                position = next;
            }
        }
        return new CancelFrame(position, false);
    }

    private static float cancelSpeed(float amplitude) {
        if (amplitude > 30f) return 6.5f;
        if (amplitude > 20f) return 5f;
        if (amplitude > 7f) return 4f;
        if (amplitude > 5f) return 3f;
        if (amplitude > 1f) return 1f;
        return .25f;
    }

    private static float cancelAttenuation(float amplitude) {
        if (amplitude > 30f) return 7f;
        if (amplitude > 20f) return 6f;
        if (amplitude > 10f) return 5f;
        if (amplitude > 5f) return 3f;
        if (amplitude > 2f) return 2f;
        return 1f;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private long normalize(long nowMs) {
        clockMs = clockMs == 0L ? Math.max(1L, nowMs) : Math.max(clockMs, nowMs);
        return clockMs;
    }

    private static float approach(float from, float to, float distance) {
        if (from < to) return Math.min(to, from + distance);
        return Math.max(to, from - distance);
    }

    private static Frame hidden() {
        return new Frame(false, false, false, 0f, 1f, 0f, 0f);
    }

    private float entryAlphaAt(long now) {
        if (gestureStartMs <= 0L) return 1f;
        return smoothStep((now - gestureStartMs) / (float) TILE_ENTER_MS);
    }

    private static float smoothStep(float value) {
        float t = clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
