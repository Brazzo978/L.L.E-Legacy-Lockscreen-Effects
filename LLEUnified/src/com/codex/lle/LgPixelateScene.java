package com.codex.lle;

/** Deterministic donor-derived timing model for the LG G2 Pixelate restoration. */
final class LgPixelateScene {
    static final long CANCEL_RETRACT_MS = 300L;
    static final long CANCEL_FADE_MS = 350L;
    static final long UNLOCK_MS = 400L;
    static final long UNLOCK_HOLD_MS = 550L;
    static final long AFFORDANCE_MS = 520L;

    static final class Frame {
        final boolean visible;
        final boolean primaryVisible;
        final boolean mosaicEnabled;
        final boolean revealUnderlay;
        final float x;
        final float y;
        final float dragPx;
        final float meshScale;
        final float primaryAlpha;

        Frame(boolean visible, boolean primaryVisible, boolean mosaicEnabled,
                boolean revealUnderlay,
                float x, float y, float dragPx, float meshScale, float primaryAlpha) {
            this.visible = visible;
            this.primaryVisible = primaryVisible;
            this.mosaicEnabled = mosaicEnabled;
            this.revealUnderlay = revealUnderlay;
            this.x = x;
            this.y = y;
            this.dragPx = dragPx;
            this.meshScale = meshScale;
            this.primaryAlpha = primaryAlpha;
        }
    }

    private enum Phase { IDLE, HELD, CANCEL, UNLOCK, AFFORDANCE }

    private Phase phase = Phase.IDLE;
    private float originX;
    private float originY;
    private float heldDragPx;
    private float releasedDragPx;
    private long phaseStartedAtMs;
    private long lastClockMs;

    void begin(float x, float y, long nowMs) {
        long now = normalizeTime(nowMs);
        originX = x;
        originY = y;
        heldDragPx = releasedDragPx = 0f;
        phase = Phase.HELD;
        phaseStartedAtMs = now;
    }

    void move(float x, float y, long nowMs) {
        if (phase != Phase.HELD) {
            begin(x, y, nowMs);
            return;
        }
        normalizeTime(nowMs);
        float dx = x - originX;
        float dy = y - originY;
        heldDragPx = (float) Math.hypot(dx, dy);
        releasedDragPx = heldDragPx;
    }

    void finish(boolean completed, long nowMs) {
        long now = normalizeTime(nowMs);
        if (phase != Phase.HELD) return;
        releasedDragPx = heldDragPx;
        phase = completed ? Phase.UNLOCK : Phase.CANCEL;
        phaseStartedAtMs = now;
    }

    void cancel(long nowMs) { finish(false, nowMs); }

    void affordance(float x, float y, long nowMs) {
        long now = normalizeTime(nowMs);
        originX = x;
        originY = y;
        heldDragPx = releasedDragPx = 0f;
        phase = Phase.AFFORDANCE;
        phaseStartedAtMs = now;
    }

    void reset() {
        phase = Phase.IDLE;
        heldDragPx = releasedDragPx = 0f;
        phaseStartedAtMs = 0L;
        lastClockMs = 0L;
    }

    Frame frameAt(long nowMs, float thresholdPx, float displayDiagonalPx) {
        return frameAt(nowMs, thresholdPx, displayDiagonalPx, 1f);
    }

    Frame frameAt(long nowMs, float thresholdPx, float displayDiagonalPx,
            float speedMultiplier) {
        long now = normalizeTime(nowMs);
        if (phase == Phase.IDLE) return hidden();
        float threshold = Math.max(1f, thresholdPx);
        float diagonal = Math.max(threshold + 1f, displayDiagonalPx);
        long elapsed = Math.max(0L, Math.round((now - phaseStartedAtMs)
                * clamp(speedMultiplier, 1f, 2f)));
        float drag = heldDragPx;
        float globalAlpha = 1f;
        boolean revealUnderlay = false;

        switch (phase) {
            case HELD:
                break;
            case CANCEL:
                if (elapsed < CANCEL_RETRACT_MS) {
                    float t = elapsed / (float) CANCEL_RETRACT_MS;
                    drag = releasedDragPx * (1f - t * t);
                } else if (elapsed < CANCEL_RETRACT_MS + CANCEL_FADE_MS) {
                    drag = 0f;
                    globalAlpha = 1f - (elapsed - CANCEL_RETRACT_MS)
                            / (float) CANCEL_FADE_MS;
                } else {
                    phase = Phase.IDLE;
                    return hidden();
                }
                break;
            case UNLOCK:
                revealUnderlay = true;
                if (elapsed < UNLOCK_MS) {
                    float t = elapsed / (float) UNLOCK_MS;
                    drag = releasedDragPx + (diagonal - releasedDragPx) * t * t;
                } else if (elapsed < UNLOCK_HOLD_MS) {
                    drag = diagonal;
                    globalAlpha = 0f;
                } else {
                    phase = Phase.IDLE;
                    return hidden();
                }
                break;
            case AFFORDANCE:
                if (elapsed >= AFFORDANCE_MS) {
                    phase = Phase.IDLE;
                    return hidden();
                }
                float hintT = elapsed / (float) AFFORDANCE_MS;
                // Donor touchdown is almost imperceptible: expose the triangular topology
                // without jumping to the large cells reserved for an actual drag.
                drag = threshold * .10f * (float) Math.sin(Math.PI * hintT);
                globalAlpha = hintT < .78f ? 1f : 1f - (hintT - .78f) / .22f;
                break;
            default:
                return hidden();
        }

        // The donor composition keeps its fixed background beneath the mosaic while the
        // radial user mask is active.  Once a cancelled gesture has fully retracted, remove
        // that background before the final primary fade so L.L.E. cannot flash the home
        // capture full-screen.  Unlock deliberately keeps it through the hand-off tail.
        revealUnderlay = revealUnderlay || drag > .01f;
        float alpha = clamp(globalAlpha * donorAlpha(drag, threshold, diagonal), 0f, 1f);
        return new Frame(true, alpha > .001f, drag > .01f, revealUnderlay,
                originX, originY, drag,
                1f + 5f * clamp(drag / threshold, 0f, 1f), alpha);
    }

    static float donorAlpha(float dragPx, float thresholdPx, float diagonalPx) {
        float start = 1.5f * Math.max(1f, thresholdPx);
        float end = Math.max(start + 1f, diagonalPx);
        return 1f - clamp((dragPx - start) / (end - start), 0f, 1f);
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private long normalizeTime(long requestedMs) {
        if (lastClockMs == 0L) lastClockMs = Math.max(1L, requestedMs);
        else lastClockMs = Math.max(lastClockMs, requestedMs);
        return lastClockMs;
    }

    private static Frame hidden() {
        return new Frame(false, false, false, false, 0f, 0f, 0f, 1f, 0f);
    }
}
