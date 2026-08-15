package com.codex.lle;

/**
 * Bounded, app-owned motion model for the experimental G2 Particle-inspired effect.
 *
 * <p>The scene deliberately models a transparent centre surrounded by independently phased
 * particle bands. It does not derive data or formulas from the third-party oracle renderer; the
 * public interaction contract used here is simply a touch-centred circular reveal with a short
 * exit animation.</p>
 */
final class G2ParticleScene {
    static final long EXIT_DURATION_MS = 400L;
    static final int INNER_PARTICLE_COUNT = 48;
    static final int MIDDLE_PARTICLE_COUNT = 72;
    static final int OUTER_PARTICLE_COUNT = 96;
    static final int PARTICLE_COUNT = INNER_PARTICLE_COUNT
            + MIDDLE_PARTICLE_COUNT + OUTER_PARTICLE_COUNT;

    private static final int STATE_IDLE = 0;
    private static final int STATE_TRACKING = 1;
    private static final int STATE_EXITING = 2;
    private static final float MIN_SPEED_MULTIPLIER = 0.5f;
    private static final float MAX_SPEED_MULTIPLIER = 2.0f;
    private static final long MAX_STEP_MS = 48L;

    private final float[] phase = new float[PARTICLE_COUNT];
    private final float[] radiusJitter = new float[PARTICLE_COUNT];
    private final float[] sizeJitter = new float[PARTICLE_COUNT];
    private final float[] output = new float[PARTICLE_COUNT * 4];

    private int surfaceWidth = 1;
    private int surfaceHeight = 1;
    private int state = STATE_IDLE;
    private float centreX;
    private float centreY;
    private float radius;
    private float exitStartRadius;
    private boolean exitExpands;
    private long exitStartedAtMs;
    private long lastAdvancedAtMs;
    private float elapsedSeconds;
    private float speedMultiplier = 1.0f;

    G2ParticleScene() {
        // Fixed, decorrelated phases make the scene deterministic and allocation-free. The
        // numbers are intentionally generated from simple irrational-looking ratios rather than
        // reusing a third-party distribution.
        for (int index = 0; index < PARTICLE_COUNT; index++) {
            phase[index] = fractional(index * 0.6180339887f) * ((float) Math.PI * 2f);
            radiusJitter[index] = fractional(index * 0.4142135623f) - 0.5f;
            sizeJitter[index] = fractional(index * 0.7320508076f);
        }
    }

    void setSurfaceSize(int width, int height) {
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        if (state == STATE_IDLE) {
            centreX = surfaceWidth * 0.5f;
            centreY = surfaceHeight * 0.5f;
        } else {
            centreX = clamp(centreX, 0f, surfaceWidth);
            centreY = clamp(centreY, 0f, surfaceHeight);
            radius = clampRadius(radius);
        }
    }

    void begin(float x, float y, long nowMs) {
        state = STATE_TRACKING;
        centreX = clamp(x, 0f, surfaceWidth);
        centreY = clamp(y, 0f, surfaceHeight);
        radius = minimumRadius();
        exitStartRadius = radius;
        exitStartedAtMs = 0L;
        lastAdvancedAtMs = nowMs;
        elapsedSeconds = 0f;
    }

    void move(float x, float y, long nowMs) {
        if (state != STATE_TRACKING) {
            begin(x, y, nowMs);
            return;
        }
        float dx = x - centreX;
        float dy = y - centreY;
        radius = clampRadius(Math.max(minimumRadius(), (float) Math.hypot(dx, dy)));
        advance(nowMs);
    }

    void finish(boolean completed, long nowMs) {
        if (state == STATE_IDLE) {
            return;
        }
        // The app host only differentiates the unlock/cancel route at this boundary. Both leave
        // a visible tail, while a completed route expands rather than collapses the reveal.
        state = STATE_EXITING;
        exitStartRadius = radius;
        exitExpands = completed;
        exitStartedAtMs = nowMs;
        lastAdvancedAtMs = nowMs;
    }

    void cancel(long nowMs) {
        finish(false, nowMs);
    }

    void reset() {
        state = STATE_IDLE;
        radius = 0f;
        exitStartRadius = 0f;
        exitExpands = false;
        exitStartedAtMs = 0L;
        lastAdvancedAtMs = 0L;
        elapsedSeconds = 0f;
    }

    void advance(long nowMs) {
        if (state == STATE_IDLE) {
            return;
        }
        if (lastAdvancedAtMs == 0L) {
            lastAdvancedAtMs = nowMs;
        }
        long deltaMs = Math.max(0L, Math.min(MAX_STEP_MS, nowMs - lastAdvancedAtMs));
        lastAdvancedAtMs = nowMs;
        elapsedSeconds += deltaMs * 0.001f * speedMultiplier;
        if (state == STATE_EXITING && nowMs - exitStartedAtMs >= EXIT_DURATION_MS) {
            reset();
        }
    }

    boolean isAnimating() {
        return state != STATE_IDLE;
    }

    void setSpeedMultiplier(float multiplier) {
        speedMultiplier = sanitizeSpeedMultiplier(multiplier);
    }

    float getSpeedMultiplier() {
        return speedMultiplier;
    }

    /**
     * Fills {@code x, y, pointSize, alpha} tuples for a GLES point-sprite batch. Keeping this in
     * a compact float array bounds both Java heap use and GPU upload work to 216 vertices.
     */
    float[] fillVertices(long nowMs) {
        advance(nowMs);
        if (state == STATE_IDLE) {
            return output;
        }

        float progress = exitProgress(nowMs);
        float alpha = state == STATE_EXITING ? (1f - progress) * (1f - progress) : 1f;
        float activeRadius = radius;
        if (state == STATE_EXITING) {
            // The surviving ring blooms outward, preserving the sense of a circular reveal while
            // its transparent centre leaves the underlying lockscreen untouched.
            activeRadius = exitExpands
                    ? exitStartRadius + progress * maximumRadius() * 0.46f
                    : exitStartRadius + (minimumRadius() - exitStartRadius) * progress;
        }
        float minDimension = Math.min(surfaceWidth, surfaceHeight);
        for (int index = 0; index < PARTICLE_COUNT; index++) {
            int band = bandFor(index);
            float bandScale = band == 0 ? 0.72f : (band == 1 ? 1.0f : 1.30f);
            float angle = phase[index] + elapsedSeconds * (band == 0 ? 1.4f
                    : (band == 1 ? -1.0f : 0.72f));
            float pulse = (float) Math.sin(elapsedSeconds * (1.6f + band * 0.35f)
                    + phase[index] * 1.7f);
            float radial = activeRadius * bandScale
                    + radiusJitter[index] * minDimension * (0.018f + band * 0.006f)
                    + pulse * (3f + band * 1.5f);
            float localAlpha = alpha * (0.55f + sizeJitter[index] * 0.45f);
            float size = (2.6f + sizeJitter[index] * 4.2f) * (1f + band * 0.18f);
            int offset = index * 4;
            output[offset] = centreX + (float) Math.cos(angle) * radial;
            output[offset + 1] = centreY + (float) Math.sin(angle) * radial;
            output[offset + 2] = size;
            output[offset + 3] = localAlpha;
        }
        return output;
    }

    float currentRadius() {
        return radius;
    }

    int particleCount() {
        return PARTICLE_COUNT;
    }

    static float sanitizeSpeedMultiplier(float multiplier) {
        if (Float.isNaN(multiplier) || Float.isInfinite(multiplier)) {
            return 1.0f;
        }
        return clamp(multiplier, MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER);
    }

    private float exitProgress(long nowMs) {
        if (state != STATE_EXITING || exitStartedAtMs <= 0L) {
            return 0f;
        }
        return clamp((nowMs - exitStartedAtMs) / (float) EXIT_DURATION_MS, 0f, 1f);
    }

    private float minimumRadius() {
        return Math.max(34f, Math.min(surfaceWidth, surfaceHeight) * 0.058f);
    }

    private float maximumRadius() {
        return Math.max(minimumRadius(), (float) Math.hypot(surfaceWidth, surfaceHeight) * 0.72f);
    }

    private float clampRadius(float candidate) {
        return clamp(candidate, minimumRadius(), maximumRadius());
    }

    private static int bandFor(int index) {
        return index < INNER_PARTICLE_COUNT ? 0
                : (index < INNER_PARTICLE_COUNT + MIDDLE_PARTICLE_COUNT ? 1 : 2);
    }

    private static float fractional(float value) {
        return value - (float) Math.floor(value);
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
