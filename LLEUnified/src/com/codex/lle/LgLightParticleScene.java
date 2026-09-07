package com.codex.lle;

import java.util.Random;

/**
 * Allocation-free scene recovered from the authorized OptimusDev archive and native LG donor.
 *
 * <p>The donor renders in a world which is one quarter of the display size. Constants which
 * were authored directly in that world are therefore multiplied by four here, while touch and
 * radius values remain ordinary screen pixels. The XLocker revision keeps the archived shader's
 * unused rotation, while the native LG revision applies the source renderer's random bokeh
 * orientation and boundary-ring distribution.</p>
 */
final class LgLightParticleScene {
    static final long TOUCH_FADE_IN_MS = 300L;
    static final long CANCEL_MS = 300L;
    static final long COMPLETE_MS = 500L;
    static final long COMPLETE_HOLD_MS = 550L;
    static final float MIN_RADIUS_DP = 44f;
    static final float UNLOCK_RADIUS_DP = 113.33f;
    static final float LG_NATIVE_MIN_RADIUS_DP = 50.2f;
    static final float LG_NATIVE_BOUNDARY_RADIUS_MM = 25f;

    static final int REVISION_XLOCKER = 1;
    static final int REVISION_LG_NATIVE = 2;

    static final int IDLE = 0;
    static final int ACTIVE = 1;
    static final int CANCEL = 2;
    static final int COMPLETE = 3;

    static final int TEXTURE_BG = 0;
    static final int TEXTURE_A_1 = 1;
    static final int TEXTURE_A_2 = 2;
    static final int TEXTURE_A_3 = 3;
    static final int TEXTURE_A_4 = 4;
    static final int TEXTURE_B_1 = 5;
    static final int TEXTURE_B_2 = 6;
    static final int TEXTURE_D_1 = 7;
    static final int TEXTURE_D_2 = 8;
    static final int TEXTURE_D_3 = 9;
    static final int TEXTURE_COUNT = 10;
    static final int PARTICLE_CAPACITY = 74;

    private static final long TESTER_SEED = 0x4c475f4c49544854L;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float DONOR_FRAMES_PER_MS = 60f / 1000f;
    private static final int KIND_BACKGROUND = 0;
    private static final int KIND_BOKEH = 1;

    private final Random random;
    private final int revision;
    private final Particle[] particles = new Particle[PARTICLE_CAPACITY];
    private int particleCount;
    private int state = IDLE;
    private int width = 1;
    private int height = 1;
    private float density = 1f;
    private float horizontalDpi = 160f;
    private float centreX;
    private float centreY;
    private float downX;
    private float downY;
    private float dragDistance;
    private float radius;
    private float terminalRadius;
    private float terminalParticleAlpha;
    private float terminalBackgroundScale;
    private float terminalBokehScale;
    private long startedAt;
    private long terminalAt;

    LgLightParticleScene() {
        this(false, REVISION_XLOCKER);
    }

    LgLightParticleScene(boolean deterministic) {
        this(deterministic, REVISION_XLOCKER);
    }

    LgLightParticleScene(boolean deterministic, int requestedRevision) {
        revision = requestedRevision == REVISION_LG_NATIVE
                ? REVISION_LG_NATIVE : REVISION_XLOCKER;
        random = deterministic ? new Random(TESTER_SEED) : new Random();
        buildParticleLayout();
    }

    void setSurfaceSize(int surfaceWidth, int surfaceHeight) {
        width = Math.max(1, surfaceWidth);
        height = Math.max(1, surfaceHeight);
    }

    void setDensity(float value) {
        density = Math.max(0.5f, value);
    }

    void setHorizontalDpi(float value) {
        horizontalDpi = value > 0f && Float.isFinite(value) ? value : 160f * density;
    }

    void begin(float x, float y, long now) {
        state = ACTIVE;
        centreX = downX = clamp(x, 0f, width);
        centreY = downY = clamp(y, 0f, height);
        dragDistance = 0f;
        radius = minRadius();
        terminalRadius = radius;
        terminalParticleAlpha = 0.5f;
        terminalBackgroundScale = 0.6f;
        terminalBokehScale = 0.6f;
        startedAt = now;
        terminalAt = 0L;
        resetParticles(now);
    }

    void move(float x, float y) {
        if (state != ACTIVE) {
            return;
        }
        dragDistance = (float) Math.hypot(x - downX, y - downY);
        float threshold = unlockRadius();
        if (dragDistance < threshold) {
            radius = minRadius()
                    + ((threshold - minRadius()) / Math.max(1f, threshold)) * dragDistance;
        } else {
            radius = dragDistance;
        }
        radius = Math.min(fullRadius(), Math.max(minRadius(), radius));
    }

    void finish(boolean completed, long now) {
        if (state != ACTIVE) {
            return;
        }
        Frame active = sample(now, new Frame());
        terminalRadius = radius;
        terminalParticleAlpha = active.particleAlpha;
        terminalBackgroundScale = active.backgroundSizeScale;
        terminalBokehScale = active.bokehSizeScale;
        terminalAt = now;
        state = completed ? COMPLETE : CANCEL;
    }

    void reset() {
        state = IDLE;
        startedAt = terminalAt = 0L;
        dragDistance = radius = terminalRadius = 0f;
    }

    boolean isAnimating() {
        return state != IDLE;
    }

    int state() {
        return state;
    }

    Frame sample(long now, Frame out) {
        out.spriteCount = 0;
        if (state == IDLE) {
            return out.set(false, false, IDLE, 0f, 0f, 1f, 1f, 0L);
        }
        long sceneElapsed = Math.max(0L, now - startedAt);
        if (state == ACTIVE) {
            float touch = accelerate(clamp(sceneElapsed / (float) TOUCH_FADE_IN_MS, 0f, 1f));
            float spriteScale = lerp(0.6f, 1f, touch);
            out.set(true, true, ACTIVE, radius,
                    lerp(0.5f, 1f, touch), spriteScale, spriteScale,
                    sceneElapsed);
            out.particleDragDistance = dragDistance;
            populateParticles(out, now);
            return out;
        }

        long terminalElapsed = Math.max(0L, now - terminalAt);
        if (state == CANCEL) {
            float t = clamp(terminalElapsed / (float) CANCEL_MS, 0f, 1f);
            boolean running = terminalElapsed < CANCEL_MS;
            if (!running) {
                state = IDLE;
            }
            out.set(running, running, CANCEL, terminalRadius * (1f - t),
                    terminalParticleAlpha * (1f - t),
                    lerp(terminalBackgroundScale, terminalBackgroundScale * 1.3f, t),
                    terminalBokehScale * (1f - t), sceneElapsed);
            out.particleDragDistance = dragDistance;
            if (running) {
                populateParticles(out, now);
            }
            return out;
        }

        float t = clamp(terminalElapsed / (float) COMPLETE_MS, 0f, 1f);
        long lifetime = COMPLETE_MS + COMPLETE_HOLD_MS;
        boolean running = terminalElapsed < lifetime;
        if (!running) {
            state = IDLE;
        }
        out.set(running, running, COMPLETE,
                lerp(terminalRadius, fullRadius(), t),
                terminalParticleAlpha * (1f - t),
                terminalBackgroundScale, terminalBokehScale, sceneElapsed);
        out.particleDragDistance = revision == REVISION_LG_NATIVE
                ? lerp(dragDistance, fullRadius(), t) : dragDistance;
        if (running && out.particleAlpha > 0f) {
            populateParticles(out, now);
        }
        return out;
    }

    float centreX() { return centreX; }
    float centreY() { return centreY; }
    float dragDistance() { return dragDistance; }
    float minRadius() {
        return (revision == REVISION_LG_NATIVE ? LG_NATIVE_MIN_RADIUS_DP : MIN_RADIUS_DP)
                * density;
    }
    float unlockRadius() {
        if (revision == REVISION_LG_NATIVE) {
            return LG_NATIVE_BOUNDARY_RADIUS_MM * horizontalDpi / 25.4f;
        }
        return UNLOCK_RADIUS_DP * density;
    }
    float fullRadius() { return (float) Math.hypot(width, height); }
    int revision() { return revision; }

    static float edgeBandwidth(float radius, float minRadius) {
        return Math.min(Math.max(0f, radius), Math.max(0f, minRadius)) * 0.8f;
    }

    static float edgeInnerRadius(float radius, float minRadius) {
        return Math.max(0f, radius * 0.7f - edgeBandwidth(radius, minRadius));
    }

    static float edgeOuterRadius(float radius, float minRadius) {
        return Math.max(0f, radius * 0.7f + edgeBandwidth(radius, minRadius));
    }

    private void buildParticleLayout() {
        addGroup(KIND_BACKGROUND, TEXTURE_BG, 5, 1.50f, 2.00f, 0L, 0L);
        addGroup(KIND_BOKEH, TEXTURE_A_1, 7, 0.40f, 1.00f, 1500L, 2500L);
        addGroup(KIND_BOKEH, TEXTURE_A_1, 8, 0.20f, 0.75f, 1000L, 3500L);
        addGroup(KIND_BOKEH, TEXTURE_A_2, 7, 0.40f, 1.00f, 1500L, 2500L);
        addGroup(KIND_BOKEH, TEXTURE_A_3, 7, 0.40f, 1.00f, 1500L, 2500L);
        addGroup(KIND_BOKEH, TEXTURE_A_4, 7, 0.40f, 1.00f, 1500L, 2500L);
        addGroup(KIND_BOKEH, TEXTURE_A_4, 8, 0.20f, 0.75f, 1000L, 3500L);
        addGroup(KIND_BOKEH, TEXTURE_B_1, 8, 0.20f, 0.75f, 1000L, 3500L);
        addGroup(KIND_BOKEH, TEXTURE_B_2, 8, 0.20f, 0.75f, 1000L, 3500L);
        addGroup(KIND_BOKEH, TEXTURE_D_1, 3, 1.50f, 2.00f, 1500L, 3000L);
        addGroup(KIND_BOKEH, TEXTURE_D_2, 3, 1.50f, 2.00f, 1500L, 3000L);
        addGroup(KIND_BOKEH, TEXTURE_D_3, 3, 1.50f, 2.00f, 1500L, 3000L);
        if (particleCount != PARTICLE_CAPACITY) {
            throw new IllegalStateException("Light Particle layout=" + particleCount);
        }
    }

    private void addGroup(int kind, int texture, int count, float minSize, float maxSize,
            long minLifeMs, long maxLifeMs) {
        for (int index = 0; index < count; index++) {
            Particle particle = new Particle();
            particle.kind = kind;
            particle.texture = texture;
            particle.indexInGroup = index;
            particle.groupCount = count;
            particle.minSizeFactor = minSize;
            particle.maxSizeFactor = maxSize;
            particle.minLifeMs = minLifeMs;
            particle.maxLifeMs = maxLifeMs;
            particles[particleCount++] = particle;
        }
    }

    private void resetParticles(long now) {
        for (Particle particle : particles) {
            if (particle.kind == KIND_BACKGROUND) {
                float slice = TWO_PI / particle.groupCount;
                particle.angle = particle.indexInGroup * slice + random.nextFloat() * slice;
                particle.radialOffsetPx = (1.5f + random.nextFloat() * 1.5f)
                        * 9.6f * density * 4f;
                particle.angularRatePerMs = ((random.nextFloat() * 0.1f) - 0.05f)
                        * 0.125f * density * DONOR_FRAMES_PER_MS;
                particle.orbitRadiusPx = random.nextFloat() * 6.25f * density * 4f;
                particle.baseAlpha = random.nextInt(2) == 0 ? 0.2f : 0.6f;
                particle.baseSizeFactor = lerp(
                        particle.minSizeFactor, particle.maxSizeFactor, random.nextFloat());
                particle.startedAt = now;
                particle.initialPhase = random.nextFloat() * TWO_PI;
                continue;
            }

            float alphaSeed = random.nextFloat();
            particle.baseAlpha = 0.7f + 0.3f * alphaSeed;
            particle.textureRotationRadians = revision == REVISION_LG_NATIVE
                    ? TWO_PI * alphaSeed : 0f;
            particle.baseSizeFactor = lerp(
                    particle.minSizeFactor, particle.maxSizeFactor, random.nextFloat());
            long lifeRange = Math.max(0L, particle.maxLifeMs - particle.minLifeMs);
            particle.durationMs = particle.minLifeMs
                    + (lifeRange == 0L ? 0L : nextLongBounded(lifeRange));
            configureBokehOrbit(particle);
            particle.startedAt = now + (particle.indexInGroup < particle.groupCount / 3
                    ? 0L : random.nextInt(1500));
            particle.initialPhase = 0f;
        }
    }

    private void configureBokehOrbit(Particle particle) {
        float angleSeed = random.nextFloat();
        float offsetSeed = random.nextFloat();
        particle.angle = TWO_PI * angleSeed;
        particle.radialOffsetPx = ((3f * offsetSeed) - 1f) * 4.375f * density * 4f;
        particle.angularRatePerMs = ((0.1f * offsetSeed) - 0.05f)
                * 0.25f * density * DONOR_FRAMES_PER_MS;
        particle.orbitRadiusPx = angleSeed * 1.25f * density * 4f;
    }

    private void populateParticles(Frame frame, long now) {
        for (Particle particle : particles) {
            if (particle.kind == KIND_BACKGROUND) {
                float elapsed = Math.max(0L, now - particle.startedAt);
                float baseRadius = backgroundAnchorRadius(frame)
                        + 0.5f * frame.particleDragDistance
                        + particle.radialOffsetPx;
                float baseX = centreX + (float) Math.cos(particle.angle) * baseRadius;
                float baseY = centreY + (float) Math.sin(particle.angle) * baseRadius;
                float phase = particle.initialPhase + particle.angularRatePerMs * elapsed;
                float x = baseX - particle.orbitRadiusPx
                        + (float) Math.cos(phase) * particle.orbitRadiusPx;
                float y = baseY + (float) Math.sin(phase) * particle.orbitRadiusPx;
                addSprite(frame, particle.texture, x, y,
                        particle.baseSizeFactor * frame.backgroundSizeScale,
                        frame.particleDragDistance * 0.25f * frame.backgroundSizeScale,
                        particle.baseAlpha * frame.particleAlpha, 0f);
                continue;
            }

            long age = now - particle.startedAt;
            if (age < 0L) {
                continue;
            }
            if (age >= particle.durationMs) {
                particle.angle = TWO_PI * random.nextFloat();
                particle.textureRotationRadians = revision == REVISION_LG_NATIVE
                        ? random.nextFloat() * 360f : 0f;
                particle.startedAt = now;
                age = 0L;
            }
            float t = clamp(age / (float) Math.max(1L, particle.durationMs), 0f, 1f);
            float eased = decelerate(t);
            float envelope = particleEnvelope(eased);
            if (envelope <= 0f) {
                continue;
            }
            float baseRadius = frame.radius + particle.radialOffsetPx;
            float baseX = centreX + (float) Math.cos(particle.angle) * baseRadius;
            float baseY = centreY + (float) Math.sin(particle.angle) * baseRadius;
            float phase = particle.initialPhase + particle.angularRatePerMs * age;
            float x = baseX - particle.orbitRadiusPx
                    + (float) Math.cos(phase) * particle.orbitRadiusPx;
            float y = baseY + (float) Math.sin(phase) * particle.orbitRadiusPx;
            addSprite(frame, particle.texture, x, y,
                    particle.baseSizeFactor * (0.7f + 0.3f * eased)
                            * frame.bokehSizeScale,
                    0f,
                    particle.baseAlpha * envelope * frame.particleAlpha,
                    revision == REVISION_LG_NATIVE ? particle.textureRotationRadians : 0f);
        }
    }

    float backgroundAnchorRadius(Frame frame) {
        return revision == REVISION_LG_NATIVE ? unlockRadius() : frame.radius;
    }

    private static void addSprite(Frame frame, int texture, float x, float y,
            float sizeScale, float sizeExtraPx, float alpha, float rotationRadians) {
        if (frame.spriteCount >= frame.sprites.length || alpha <= 0f || sizeScale <= 0f) {
            return;
        }
        frame.sprites[frame.spriteCount++].set(texture, x, y,
                sizeScale, Math.max(0f, sizeExtraPx), clamp(alpha, 0f, 1f),
                rotationRadians);
    }

    private long nextLongBounded(long bound) {
        return bound <= Integer.MAX_VALUE
                ? random.nextInt((int) bound)
                : (long) (random.nextDouble() * bound);
    }

    private static float particleEnvelope(float value) {
        if (value < 0.3f) {
            return value / 0.3f;
        }
        if (value < 0.35f) {
            return 1f;
        }
        return Math.max(0f, (1f - value) / 0.65f);
    }

    private static float accelerate(float value) { return value * value; }
    private static float decelerate(float value) {
        float inverse = 1f - clamp(value, 0f, 1f);
        return 1f - inverse * inverse;
    }
    private static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Frame {
        boolean visible;
        boolean running;
        int stage;
        float radius;
        float particleAlpha;
        float backgroundSizeScale;
        float bokehSizeScale;
        float particleDragDistance;
        long elapsedMs;
        int spriteCount;
        final ParticleSprite[] sprites = new ParticleSprite[PARTICLE_CAPACITY];

        Frame() {
            for (int index = 0; index < sprites.length; index++) {
                sprites[index] = new ParticleSprite();
            }
        }

        Frame set(boolean nextVisible, boolean nextRunning, int nextStage, float nextRadius,
                float nextParticleAlpha, float nextBackgroundSizeScale,
                float nextBokehSizeScale, long nextElapsedMs) {
            visible = nextVisible;
            running = nextRunning;
            stage = nextStage;
            radius = Math.max(0f, nextRadius);
            particleAlpha = clamp(nextParticleAlpha, 0f, 1f);
            backgroundSizeScale = Math.max(0f, nextBackgroundSizeScale);
            bokehSizeScale = Math.max(0f, nextBokehSizeScale);
            particleDragDistance = 0f;
            elapsedMs = Math.max(0L, nextElapsedMs);
            return this;
        }

        boolean isComplete() { return stage == COMPLETE; }
    }

    static final class ParticleSprite {
        int texture;
        float x;
        float y;
        float sizeScale;
        float sizeExtraPx;
        float alpha;
        float rotationRadians;

        void set(int nextTexture, float nextX, float nextY,
                float nextSizeScale, float nextSizeExtraPx, float nextAlpha,
                float nextRotationRadians) {
            texture = nextTexture;
            x = nextX;
            y = nextY;
            sizeScale = nextSizeScale;
            sizeExtraPx = nextSizeExtraPx;
            alpha = nextAlpha;
            rotationRadians = nextRotationRadians;
        }
    }

    private static final class Particle {
        int kind;
        int texture;
        int indexInGroup;
        int groupCount;
        float minSizeFactor;
        float maxSizeFactor;
        long minLifeMs;
        long maxLifeMs;
        long durationMs;
        long startedAt;
        float angle;
        float initialPhase;
        float radialOffsetPx;
        float orbitRadiusPx;
        float angularRatePerMs;
        float baseAlpha;
        float baseSizeFactor;
        float textureRotationRadians;
    }
}
