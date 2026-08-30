package com.codex.lle;

/**
 * Deterministic, app-owned state machine for the LG G2 Pixelate inspired effect.
 * It is intentionally an independent interpretation, retained here for the 1.0.6 beta only.
 */
final class LgPixelateScene {
    static final long CANCEL_FADE_MS = 220L;
    static final long UNLOCK_MS = 400L;
    static final long AFFORDANCE_MS = 360L;
    static final float BASE_RADIUS_PX = 72.0f;
    static final float MAX_RADIUS_PX = 330.0f;
    static final float MIN_PIXEL_SIZE_PX = 3.0f;
    static final float MAX_PIXEL_SIZE_PX = 42.0f;

    static final class Frame {
        final boolean visible;
        final float x;
        final float y;
        final float radiusPx;
        final float pixelSizePx;
        final float alpha;

        Frame(boolean visible, float x, float y, float radiusPx,
                float pixelSizePx, float alpha) {
            this.visible = visible;
            this.x = x;
            this.y = y;
            this.radiusPx = radiusPx;
            this.pixelSizePx = pixelSizePx;
            this.alpha = alpha;
        }
    }

    private enum Phase { IDLE, HELD, CANCEL, UNLOCK, AFFORDANCE }

    private Phase phase = Phase.IDLE;
    private float x;
    private float y;
    private float downX;
    private float downY;
    private float heldStrength;
    private float releaseStrength;
    private long phaseStartedAtMs;
    private long lastClockMs;

    void begin(float nextX, float nextY, long nowMs) {
        long now = normalizeTime(nowMs);
        x = downX = nextX;
        y = downY = nextY;
        heldStrength = releaseStrength = 0.34f;
        phase = Phase.HELD;
        phaseStartedAtMs = now;
    }

    void move(float nextX, float nextY, long nowMs) {
        if (phase != Phase.HELD) {
            begin(nextX, nextY, nowMs);
            return;
        }
        normalizeTime(nowMs);
        x = nextX;
        y = nextY;
        float dx = nextX - downX;
        float dy = nextY - downY;
        heldStrength = clamp(0.34f + (float) Math.sqrt(dx * dx + dy * dy) / 640.0f,
                0.34f, 1.0f);
        releaseStrength = heldStrength;
    }

    void finish(boolean completed, long nowMs) {
        long now = normalizeTime(nowMs);
        if (phase != Phase.HELD) {
            return;
        }
        releaseStrength = heldStrength;
        phase = completed ? Phase.UNLOCK : Phase.CANCEL;
        phaseStartedAtMs = now;
    }

    void cancel(long nowMs) {
        finish(false, nowMs);
    }

    void affordance(float nextX, float nextY, long nowMs) {
        long now = normalizeTime(nowMs);
        x = nextX;
        y = nextY;
        heldStrength = releaseStrength = 0.32f;
        phase = Phase.AFFORDANCE;
        phaseStartedAtMs = now;
    }

    void reset() {
        phase = Phase.IDLE;
        heldStrength = releaseStrength = 0.0f;
        phaseStartedAtMs = 0L;
        lastClockMs = 0L;
    }

    Frame frameAt(long nowMs, float displayScale) {
        return frameAt(nowMs, displayScale, 1.0f);
    }

    Frame frameAt(long nowMs, float displayScale, float speedMultiplier) {
        long now = normalizeTime(nowMs);
        float scale = clamp(displayScale, 0.35f, 4.0f);
        if (phase == Phase.IDLE) {
            return hidden();
        }
        long elapsed = Math.max(0L, Math.round((now - phaseStartedAtMs)
                * clamp(speedMultiplier, 1.0f, 2.0f)));
        float strength = heldStrength;
        float alpha = 1.0f;
        switch (phase) {
            case HELD:
                break;
            case CANCEL:
                if (elapsed >= CANCEL_FADE_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                alpha = 1.0f - elapsed / (float) CANCEL_FADE_MS;
                strength = releaseStrength * alpha;
                break;
            case UNLOCK:
                if (elapsed >= UNLOCK_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                float unlockT = elapsed / (float) UNLOCK_MS;
                strength = clamp(releaseStrength + unlockT * (1.0f - releaseStrength),
                        releaseStrength, 1.0f);
                alpha = unlockT < 0.58f ? 1.0f
                        : 1.0f - ((unlockT - 0.58f) / 0.42f);
                break;
            case AFFORDANCE:
                if (elapsed >= AFFORDANCE_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                float pulse = elapsed / (float) AFFORDANCE_MS;
                strength = 0.20f + 0.42f * pulse;
                alpha = pulse < 0.60f ? pulse / 0.60f
                        : 1.0f - ((pulse - 0.60f) / 0.40f);
                break;
            default:
                return hidden();
        }
        return new Frame(alpha > 0.001f, x, y,
                lerp(BASE_RADIUS_PX, MAX_RADIUS_PX, strength) * scale,
                lerp(MIN_PIXEL_SIZE_PX, MAX_PIXEL_SIZE_PX, strength) * scale,
                clamp(alpha, 0.0f, 1.0f));
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private long normalizeTime(long requestedMs) {
        if (lastClockMs == 0L) {
            lastClockMs = Math.max(1L, requestedMs);
        } else {
            lastClockMs = Math.max(lastClockMs, requestedMs);
        }
        return lastClockMs;
    }

    private static Frame hidden() {
        return new Frame(false, 0f, 0f, 0f, 0f, 0f);
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }
}
