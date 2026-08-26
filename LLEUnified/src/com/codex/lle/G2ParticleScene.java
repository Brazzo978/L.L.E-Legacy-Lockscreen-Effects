package com.codex.lle;

/** Allocation-free motion model recovered from the authorized OptimusDev/XLocker archive. */
final class G2ParticleScene {
    static final long EXIT_DURATION_MS = 400L;
    static final long COMPLETE_HOLD_MS = 550L;
    static final int VERTEX_STRIDE = 5; // x, y, point size, alpha, mask start

    // Donor j.a(): two sets of eight emitters for each recovered count table.
    private static final int D_COUNT = 480;
    private static final int F_COUNT = 1470;
    private static final int B_COUNT = 740;
    private static final int E_COUNT = 400;
    static final int PARTICLE_COUNT = D_COUNT + F_COUNT + B_COUNT + E_COUNT;

    private static final int D_END = D_COUNT;
    private static final int F_END = D_END + F_COUNT;
    private static final int B_END = F_END + B_COUNT;
    private static final int IDLE = 0, TRACKING = 1, CANCEL = 2, COMPLETE = 3;
    private static final float TWO_PI = (float) Math.PI * 2f;

    private final float[] x = new float[PARTICLE_COUNT];
    private final float[] y = new float[PARTICLE_COUNT];
    private final float[] directionX = new float[PARTICLE_COUNT];
    private final float[] directionY = new float[PARTICLE_COUNT];
    private final float[] velocity = new float[PARTICLE_COUNT];
    private final float[] sizeDp = new float[PARTICLE_COUNT];
    private final float[] seedAlpha = new float[PARTICLE_COUNT];
    private final float[] lifeMs = new float[PARTICLE_COUNT];
    private final float[] bornAtMs = new float[PARTICLE_COUNT];
    private final float[] maskStart = new float[PARTICLE_COUNT];
    private final float[] fTargetRadius = new float[PARTICLE_COUNT];
    private final byte[] speedClass = new byte[PARTICLE_COUNT];
    private final int[] randomState = new int[PARTICLE_COUNT];
    private final float[] output = new float[PARTICLE_COUNT * VERTEX_STRIDE];

    private int width = 1, height = 1, state = IDLE;
    private float density = 1f;
    private float centreX, centreY, downX, downY;
    private float radius, terminalRadius, multiplier = 1f;
    private float particleInputRadius, fPreviousTarget, fMotionFactor = 1f;
    private long startedAt, terminalAt, lastFrameAt;
    private boolean dInitialMode, eInitialMode, bEnabled;

    G2ParticleScene() {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            randomState[i] = 0x6d2b79f5 ^ (i * 0x9e3779b9);
            if (randomState[i] == 0) randomState[i] = i + 1;
            speedClass[i] = (byte) (nextRandom(i) * 3f);
            int visualClass = Math.min(2, (int) (nextRandom(i) * 3f));
            float familyBase = family(i) == 1 ? 4f : 3f;
            float visualSize = familyBase + 4f * visualClass;
            sizeDp[i] = visualSize * (.8f + .05f * nextRandom(i));
            seedAlpha[i] = .2f * ((int) (5f * nextRandom(i)) + 1);
            lifeMs[i] = 600f + 1000f * nextRandom(i);
            float startChance = nextRandom(i);
            maskStart[i] = startChance < .5f ? 0f : (startChance < .8f ? .3f : .4f);
        }
    }

    void setSurfaceSize(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
    }

    void setDensity(float value) { density = Math.max(1f, value); }

    void begin(float touchX, float touchY, long now) {
        state = TRACKING;
        centreX = downX = clamp(touchX, 0f, width);
        centreY = downY = clamp(touchY, 0f, height);
        radius = minimumRadius();
        terminalRadius = radius;
        particleInputRadius = 0f; // donor explicitly calls F.a(0, NONE) on ACTION_DOWN
        startedAt = lastFrameAt = now;
        terminalAt = 0L;
        dInitialMode = true;
        eInitialMode = true;
        bEnabled = false;
        fMotionFactor = 1f;
        fPreviousTarget = 170f * density;
        initialiseParticlePositions(now);
        updateFTargets(0f);
    }

    void move(float touchX, float touchY, long now) {
        if (state != TRACKING) {
            begin(touchX, touchY, now);
            return;
        }
        radius = Math.max(minimumRadius(),
                (float) Math.hypot(touchX - downX, touchY - downY));
        particleInputRadius = radius;
        if (radius > minimumRadius()) {
            if (dInitialMode) resetRingVelocities(0, D_END, .35f);
            if (eInitialMode) resetRingVelocities(B_END, PARTICLE_COUNT, 5f);
            dInitialMode = false;
            eInitialMode = false;
        }
        bEnabled = true;
        updateFTargets(radius);
    }

    void finish(boolean completed, long now) {
        if (state != TRACKING) return;
        terminalRadius = radius;
        terminalAt = now;
        state = completed ? COMPLETE : CANCEL;
        if (completed) {
            fMotionFactor = 1f;
        } else {
            // Recovered f.a(CANCEL): a very distant ring pulled with a 0.06 multiplier.
            fMotionFactor = .06f;
            float distant = 170f * density + fullRadius();
            for (int i = D_END; i < F_END; i++) {
                fTargetRadius[i] = distant + signedRandom(i) * 6f * density;
            }
        }
    }

    void reset() {
        state = IDLE;
        radius = terminalRadius = particleInputRadius = 0f;
        startedAt = terminalAt = lastFrameAt = 0L;
        bEnabled = false;
    }

    boolean isAnimating() { return state != IDLE; }

    void setSpeedMultiplier(float value) { multiplier = sanitizeSpeedMultiplier(value); }
    float getSpeedMultiplier() { return multiplier; }

    static float sanitizeSpeedMultiplier(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 1f : clamp(value, .5f, 2f);
    }

    float[] fillVertices(long now) {
        if (state == IDLE) return output;
        if (terminalAt > 0L && now - terminalAt >= terminalLifetime()) {
            reset();
            return output;
        }

        float frameScale = lastFrameAt <= 0L ? 1f
                : clamp((now - lastFrameAt) / (1000f / 60f), 0f, 2.88f);
        lastFrameAt = now;
        frameScale *= multiplier;

        float activeRadius = currentRadius(now);
        if (state == COMPLETE) {
            particleInputRadius = activeRadius;
            updateFTargets(activeRadius);
            bEnabled = true;
        }

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            int family = family(i);
            float target = targetRadius(i, family, activeRadius);
            if (now - bornAtMs[i] > lifeMs[i]) respawn(i, family, target, now);

            if (family == 0) {
                attractToRing(i, target, 8f * density, 1f, frameScale);
            } else if (family == 1) {
                attractToRing(i, target, 8f * density, fMotionFactor, frameScale);
            } else if (family == 2) {
                advanceEscapingParticle(i, frameScale, state == COMPLETE ? 15f : 1f);
            } else {
                attractToRing(i, target, 0f, 1f, frameScale);
            }

            float life = clamp((now - bornAtMs[i]) / lifeMs[i], 0f, 1f);
            float easedLife = life * life;
            float localAlpha = Math.max(.1f, (1f - easedLife) * seedAlpha[i]);
            float familyAlpha = particleGlobalAlpha(now, family);
            if (family == 2 && !bEnabled) familyAlpha = 0f;
            int offset = i * VERTEX_STRIDE;
            output[offset] = x[i];
            output[offset + 1] = y[i];
            output[offset + 2] = sizeDp[i] * density * (1f - .7f * easedLife);
            output[offset + 3] = localAlpha * familyAlpha;
            output[offset + 4] = maskStart[i];
        }
        return output;
    }

    float currentRadius(long now) {
        if (state == CANCEL) return terminalRadius * (1f - terminalProgress(now));
        if (state == COMPLETE) {
            return terminalRadius + (fullRadius() - terminalRadius) * terminalProgress(now);
        }
        return radius;
    }

    float currentRadius() { return radius; }
    float currentAlpha(long now) { return state == IDLE ? 0f : 1f; }

    float currentHoleAlpha(long now) {
        if (state == IDLE) return 0f;
        if (state == CANCEL) return .5f * (1f - terminalProgress(now));
        return .5f;
    }

    float centreX() { return centreX; }
    float centreY() { return centreY; }
    int particleCount() { return PARTICLE_COUNT; }

    private void initialiseParticlePositions(long now) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            int family = family(i);
            float initialRadius = family == 1 ? 245f * density : 35f * density;
            setDirectionAndPosition(i, initialRadius);
            velocity[i] = initialVelocity(i, family);
            bornAtMs[i] = now;
        }
    }

    private float initialVelocity(int i, int family) {
        float random = nextRandom(i);
        int size = speedClass[i];
        float base;
        if (family == 1) {
            base = size == 0 ? .1f + .4f * random
                    : (size == 1 ? .3f + .4f * random : .6f + .4f * random);
        } else {
            // d/e/b use the reverse class weighting recovered from their emitters.
            base = size == 2 ? .1f + .4f * random
                    : (size == 1 ? .3f + .4f * random : .6f + .4f * random);
        }
        if (family == 0) return base * .35f;
        if (family == 2) return base * .55f;
        if (family == 3) return base * 5f;
        return base;
    }

    private void resetRingVelocities(int start, int end, float oldMultiplier) {
        for (int i = start; i < end; i++) velocity[i] /= oldMultiplier;
    }

    private void updateFTargets(float inputRadius) {
        float target = inputRadius;
        if (inputRadius < fPreviousTarget) {
            target = 170f * density - .3f * (inputRadius - 50f * density);
            fPreviousTarget = target;
        }
        for (int i = D_END; i < F_END; i++) {
            fTargetRadius[i] = target + signedRandom(i) * 6f * density;
        }
    }

    private float targetRadius(int i, int family, float activeRadius) {
        if (family == 0) {
            if (dInitialMode) return 128f * density;
            return Math.max(activeRadius, minimumRadius());
        }
        if (family == 1) return fTargetRadius[i];
        if (family == 2) return particleInputRadius;
        if (eInitialMode) return minimumRadius();
        return Math.max(activeRadius, minimumRadius());
    }

    private void attractToRing(int i, float target, float tolerance, float motion,
            float frameScale) {
        float distance = (float) Math.hypot(x[i] - centreX, y[i] - centreY);
        float step;
        if (tolerance > 0f && Math.abs(distance - target) < tolerance) {
            // Donor uses (random - .5) * .5 in quarter-resolution world coordinates.
            step = signedRandom(i) * velocity[i] * 2f;
        } else {
            step = .1f * (target - distance) * velocity[i] * motion;
        }
        x[i] += directionX[i] * step * frameScale;
        y[i] += directionY[i] * step * frameScale;
    }

    private void advanceEscapingParticle(int i, float frameScale, float unlockBoost) {
        if (!bEnabled) return;
        float distance = (float) Math.hypot(x[i] - centreX, y[i] - centreY);
        float denominator = Math.max(1f, 3f * fullRadius() - distance);
        // Convert the donor's quarter-resolution 1300dp velocity term to screen pixels.
        float step = (20800f * density / denominator) * velocity[i] * unlockBoost;
        x[i] += directionX[i] * step * frameScale;
        y[i] += directionY[i] * step * frameScale;
    }

    private void respawn(int i, int family, float target, long now) {
        float respawnRadius;
        if (family == 0) respawnRadius = dInitialMode ? particleInputRadius : target;
        else if (family == 1) respawnRadius = target;
        else if (family == 2) respawnRadius = particleInputRadius;
        else respawnRadius = eInitialMode ? particleInputRadius : target;
        setDirectionAndPosition(i, Math.max(0f, respawnRadius));
        bornAtMs[i] = now;
    }

    private void setDirectionAndPosition(int i, float spawnRadius) {
        float angle = nextRandom(i) * TWO_PI;
        directionX[i] = (float) Math.cos(angle);
        directionY[i] = (float) Math.sin(angle);
        x[i] = centreX + directionX[i] * spawnRadius;
        y[i] = centreY + directionY[i] * spawnRadius;
    }

    private float particleGlobalAlpha(long now, int family) {
        float alpha;
        if (state == TRACKING) alpha = clamp((now - startedAt) / 500f, 0f, 1f);
        else alpha = 1f - terminalProgress(now);
        if (family == 1 && state == TRACKING) alpha = Math.min(1f, 1.5f * alpha);
        return alpha;
    }

    private long terminalLifetime() {
        return state == COMPLETE ? EXIT_DURATION_MS + COMPLETE_HOLD_MS : EXIT_DURATION_MS;
    }

    private float terminalProgress(long now) {
        return terminalAt <= 0L ? 0f
                : clamp((now - terminalAt) / (float) EXIT_DURATION_MS, 0f, 1f);
    }

    private float minimumRadius() { return 50f * density; }
    private float fullRadius() { return (float) Math.hypot(width, height); }

    private static int family(int index) {
        return index < D_END ? 0 : (index < F_END ? 1 : (index < B_END ? 2 : 3));
    }

    private float nextRandom(int index) {
        int value = randomState[index];
        value ^= value << 13;
        value ^= value >>> 17;
        value ^= value << 5;
        randomState[index] = value;
        return (value & 0x7fffffff) / 2147483648f;
    }

    private float signedRandom(int index) { return nextRandom(index) * 2f - 1f; }
    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
