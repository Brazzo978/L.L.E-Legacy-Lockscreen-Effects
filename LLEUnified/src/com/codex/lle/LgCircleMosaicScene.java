package com.codex.lle;

/**
 * Host-testable clock and geometry for LG G4 Circle Mosaic.
 *
 * <p>The donor uses a 15 x 25 mesh. Its global drag radius feeds two local
 * circles inside every mesh cell: a broad blurred circle and a smaller fully
 * transparent circle. Values below mirror CircleMosaicRenderer/Object.</p>
 */
final class LgCircleMosaicScene {
    static final int COLUMNS = 15;
    static final int ROWS = 25;
    static final long CANCEL_MS = 250L;
    static final long UNLOCK_MS = 250L;
    static final long UNDERLAY_HOLD_MS = 550L;
    static final int IDLE = 0;
    static final int ACTIVE = 1;
    static final int CANCEL = 2;
    static final int COMPLETE = 3;

    private int width = 1;
    private int height = 1;
    private float density = 1f;
    private float xdpi;
    private int stage = IDLE;
    private float downX;
    private float downY;
    private float radius;
    private float terminalRadius;
    private long finishedAt;

    void configure(int width, int height, float density) {
        configure(width, height, density, 0f);
    }

    void configure(int width, int height, float density, float xdpi) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.density = finite(density) && density > 0f ? density : 1f;
        this.xdpi = finite(xdpi) && xdpi > 0f ? xdpi : 0f;
    }

    float minRadius() { return 50.199982f * density; }
    /** unlock_handler_radius_max is exactly 25mm in every donor resource bucket. */
    float boundaryRadius() {
        return xdpi > 0f ? 25f * xdpi / 25.4f : 201f * density;
    }
    float maxRadius() { return (float) Math.hypot(width, height); }
    float opaqueFactor() { return 3.5f * (14f - 2f * density); }
    float alphaFactor() { return .75f * (14f - 2f * density); }
    float initialOpaqueInset() { return density >= 4f ? 16f : 0f; }
    int state() { return stage; }
    boolean gestureActive() { return stage == ACTIVE; }

    void begin(float x, float y, long now) {
        if (!finite(x) || !finite(y)) return;
        downX = clamp(x, 0f, width);
        downY = clamp(y, 0f, height);
        radius = minRadius();
        terminalRadius = radius;
        finishedAt = 0L;
        stage = ACTIVE;
    }

    void move(float x, float y, long now) {
        if (stage != ACTIVE || !finite(x) || !finite(y)) return;
        float drag = (float) Math.hypot(x - downX, y - downY);
        float boundary = boundaryRadius();
        if (drag < boundary) {
            radius = minRadius() + ((boundary - minRadius()) / boundary) * drag;
        } else {
            radius = drag;
        }
        radius = Math.min(radius, maxRadius());
    }

    void finish(boolean completed, long now) {
        if (stage != ACTIVE) return;
        terminalRadius = radius;
        finishedAt = now;
        stage = completed ? COMPLETE : CANCEL;
    }

    void reset() {
        stage = IDLE;
        radius = terminalRadius = 0f;
        finishedAt = 0L;
    }

    Frame sample(long now, Frame out) {
        out.clear();
        if (stage == IDLE) return out;
        long age = Math.max(0L, now - finishedAt);
        if (stage == CANCEL && age >= CANCEL_MS
                || stage == COMPLETE && age >= UNLOCK_MS + UNDERLAY_HOLD_MS) {
            reset();
            return out;
        }
        out.visible = true;
        out.running = stage != ACTIVE;
        out.stage = stage;
        out.x = downX;
        out.y = downY;
        out.alpha = 1f;
        out.radius = radius;
        out.density = density;
        out.opaqueFactor = Math.max(.001f, opaqueFactor());
        out.alphaFactor = Math.max(.001f, alphaFactor());
        out.initialOpaqueInset = initialOpaqueInset();
        if (stage == CANCEL) {
            float t = clamp(age / (float) CANCEL_MS, 0f, 1f);
            out.radius = t > .5f ? 0f : terminalRadius * (1f - t);
        } else if (stage == COMPLETE) {
            if (age >= UNLOCK_MS) {
                out.fullUnderlay = true;
                return out;
            }
            float t = clamp(age / (float) UNLOCK_MS, 0f, 1f);
            out.radius = terminalRadius + (maxRadius() * .8f - terminalRadius) * t;
            out.alpha = 1f - t;
        }
        return out;
    }

    static float cellBlurRadius(Frame frame) {
        return Math.max(0f, (frame.radius - frame.initialOpaqueInset) / frame.opaqueFactor);
    }

    static float cellRevealRadius(Frame frame, float cellCenterX, float cellCenterY) {
        float distance = (float) Math.hypot(frame.x - cellCenterX, frame.y - cellCenterY);
        return Math.max(0f, (frame.radius - distance) / frame.alphaFactor);
    }

    static boolean cellAffected(Frame frame, float cellCenterX, float cellCenterY) {
        return (float) Math.hypot(frame.x - cellCenterX, frame.y - cellCenterY)
                < frame.radius;
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
        float x;
        float y;
        float radius;
        float alpha;
        float density;
        float opaqueFactor;
        float alphaFactor;
        float initialOpaqueInset;

        void clear() {
            visible = running = fullUnderlay = false;
            stage = IDLE;
            x = y = radius = alpha = density = initialOpaqueInset = 0f;
            opaqueFactor = alphaFactor = 1f;
        }
    }
}
