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

    private final EffectWorkshopConfig.Values workshop;

    LgHulaHoopFluidicScene() { this(EffectWorkshopConfig.originals(42)); }

    LgHulaHoopFluidicScene(EffectWorkshopConfig.Values workshop) {
        this.workshop = workshop == null ? EffectWorkshopConfig.originals(42) : workshop;
    }

    void configure(int width, int height, float density) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.density = finite(density) && density > 0f ? density : 1f;
    }

    int state() { return stage; }
    boolean gestureActive() { return stage == ACTIVE; }
    float minimumRadius() { return workshop.get("v2_minimum_radius") * density; }
    float outerRingStride() { return workshop.get("v2_ring_stride") * density; }
    float stretchScale(float randomUnit) {
        // Keep the donor's literal range in the disabled path: subtracting the
        // schema endpoints (1.1f - .7f) changes its floating-point result.
        return workshop.enabled
                ? workshop.get("v2_stretch_min") + randomUnit
                        * (workshop.get("v2_stretch_max") - workshop.get("v2_stretch_min"))
                : .7f + randomUnit * .4f;
    }
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

        if (speed < workshop.get("v2_stretch_speed")) {
            radius = Math.max(nextDistance, minimumRadius());
            dragDistance = radius;
            if (stretched) rotationDelayFrames = workshop.intValue("v2_stretch_delay");
            stretched = false;
        } else {
            radius = Math.max(radius, minimumRadius());
            if (nextDistance < minimumRadius()) {
                dragDistance = radius;
                stretched = false;
            } else if (nextDistance > radius) {
                if (!stretched) stretchDelayFrames = workshop.intValue("v2_stretch_delay");
                stretched = true;
                dragDistance = nextDistance;
            } else {
                radius = nextDistance;
                dragDistance = nextDistance;
                stretched = false;
            }
        }
        if (radius > 0f && dragDistance / radius > workshop.get("v2_max_stretch")) {
            radius = dragDistance / workshop.get("v2_max_stretch");
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
            rotationDelayFrames = workshop.intValue("v2_stretch_delay");
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
    static class SoftBody {
        protected static final int RESOLUTION = 100;
        private static final int VERTICES = RESOLUTION + 2;
        private static final float HERMITE_TANGENT = 1.6568542f;
        private static final float KS = .01f;
        private static final float KD = .03f;
        private static final float NOMINAL_FRAME_MS = 16.666666f;

        private final EffectWorkshopConfig.Values workshop;

        SoftBody(EffectWorkshopConfig.Values workshop) { this.workshop = workshop; }

        protected final float[] position = new float[VERTICES * 2];
        private final float[] previousPosition = new float[VERTICES * 2];
        private final float[] previousVelocity = new float[VERTICES * 2];
        private final float[] targetPosition = new float[VERTICES * 2];
        private float innerRadius;
        private float outerRadius;
        protected float angle;
        private float rotationSpeed = .18f;
        protected float pivotX;
        protected float pivotY;
        private float targetPivotX;
        private float targetPivotY;
        private float pivotStep;
        private boolean rotating;
        private boolean softbody = true;
        private long previousUpdateAt;
        private long previousRotateAt;

        float coordinate(int index) { return position[index]; }

        void reset(long now) {
            java.util.Arrays.fill(position, 0f);
            java.util.Arrays.fill(previousPosition, 0f);
            java.util.Arrays.fill(previousVelocity, 0f);
            java.util.Arrays.fill(targetPosition, 0f);
            innerRadius = outerRadius = 0f;
            pivotX = pivotY = targetPivotX = targetPivotY = pivotStep = 0f;
            softbody = true;
            previousUpdateAt = 0L;
            previousRotateAt = now;
        }

        void setRadii(float innerRadius, float outerRadius) {
            this.innerRadius = Math.max(0f, innerRadius);
            this.outerRadius = Math.max(0f, outerRadius);
        }

        void setAngle(float angle) { this.angle = angle; }

        void setRotationSpeed(float speed) {
            rotationSpeed = speed;
            rotating = true;
        }

        void setPivot(float x, float y, float step) {
            targetPivotX = x;
            targetPivotY = y;
            pivotStep = step;
        }

        void setSoftbody(boolean softbody) { this.softbody = softbody; }

        void rotate(long now) {
            float elapsed = previousRotateAt == 0L ? NOMINAL_FRAME_MS : now - previousRotateAt;
            previousRotateAt = now;
            if (elapsed < 0f || elapsed > 50f) elapsed = NOMINAL_FRAME_MS;
            if (rotating) angle += rotationSpeed * elapsed;
            if (pivotStep > 0f) {
                pivotX = stepPivot(pivotX, targetPivotX, pivotStep);
                pivotY = stepPivot(pivotY, targetPivotY, pivotStep);
            } else {
                pivotX = targetPivotX;
                pivotY = targetPivotY;
            }
        }

        void update(long now) {
            float elapsed = previousUpdateAt == 0L ? NOMINAL_FRAME_MS : now - previousUpdateAt;
            previousUpdateAt = now;
            if (elapsed < 0f || elapsed > 50f) elapsed = NOMINAL_FRAME_MS;
            float normalTime = elapsed / NOMINAL_FRAME_MS;
            updateTarget();
            if (!softbody) {
                System.arraycopy(targetPosition, 0, position, 0, position.length);
                return;
            }
            // LG's integrator assumes one update per 16.666 ms display frame. Reusing its
            // unscaled velocity term at 90/120/144 Hz injects energy twice as often and makes
            // the hoop oscillate violently. Semi-implicit fractional/sub-stepped integration
            // is identical to the donor when normalTime == 1, but preserves that response on
            // modern high-refresh panels and across an occasional dropped frame.
            int steps = Math.max(1, (int) Math.ceil(normalTime));
            float stepTime = normalTime / steps;
            for (int i = 0; i < position.length; i++) {
                float p = previousPosition[i];
                float velocity = previousVelocity[i];
                for (int step = 0; step < steps; step++) {
                    float force = -workshop.get("v2_spring") * (p - targetPosition[i])
                            - workshop.get("v2_damping") * velocity;
                    velocity += force * stepTime;
                    p += velocity * stepTime;
                }
                position[i] = p;
                previousVelocity[i] = velocity;
                previousPosition[i] = p;
            }
        }

        private void updateTarget() {
            float stretch = innerRadius > 0f ? outerRadius / innerRadius : 1f;
            if (Float.isNaN(stretch) || Float.isInfinite(stretch)) stretch = 1f;
            targetPosition[0] = targetPosition[1] = 0f;
            float tangent = workshop.get("v2_hermite_tangent");
            int vertex = 1;
            vertex = addQuarter(vertex, -1f, 0f, 0f, -tangent,
                    0f, -1f, tangent, 0f, innerRadius);
            vertex = addQuarter(vertex, 0f, -1f, tangent, 0f,
                    stretch, 0f, 0f, tangent, innerRadius);
            vertex = addQuarter(vertex, stretch, 0f, 0f, tangent,
                    0f, 1f, -tangent, 0f, innerRadius);
            addQuarter(vertex, 0f, 1f, -tangent, 0f,
                    -1f, 0f, 0f, -tangent, innerRadius);
            targetPosition[(RESOLUTION + 1) * 2] = targetPosition[2];
            targetPosition[(RESOLUTION + 1) * 2 + 1] = targetPosition[3];
        }

        private int addQuarter(int startVertex, float fromX, float fromY,
                float tangentFromX, float tangentFromY, float toX, float toY,
                float tangentToX, float tangentToY, float scale) {
            int quarter = RESOLUTION / 4;
            for (int i = 0; i < quarter; i++) {
                float s = i / (float) quarter;
                float s2 = s * s;
                float s3 = s2 * s;
                float h1 = 2f * s3 - 3f * s2 + 1f;
                float h2 = -2f * s3 + 3f * s2;
                float h3 = s3 - 2f * s2 + s;
                float h4 = s3 - s2;
                int index = (startVertex + i) * 2;
                targetPosition[index] = (fromX * h1 + toX * h2
                        + tangentFromX * h3 + tangentToX * h4) * scale;
                targetPosition[index + 1] = (fromY * h1 + toY * h2
                        + tangentFromY * h3 + tangentToY * h4) * scale;
            }
            return startVertex + quarter;
        }

        private static float stepPivot(float from, float to, float step) {
            float difference = from - to;
            if (Math.abs(difference) <= step) return to;
            return difference > 0f ? from - step : from + step;
        }
    }
}
