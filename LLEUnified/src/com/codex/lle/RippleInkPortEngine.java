package com.codex.lle;

import java.util.Arrays;

/**
 * App-owned, process-local simulation state for the Note 3 Ripple Ink reverse port.
 *
 * <p>The mesh, water impulse and 60 Hz wave step mirror the authoritative
 * {@code 88991de8...cebb} ARM32 oracle. Ink density in this class is a host-testable diagnostic;
 * production drawing uses the distinct GLES ping-pong controller so the renderer can advance the
 * water field without also paying for this CPU density oracle.</p>
 */
public final class RippleInkPortEngine {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;

    public static final int DETAIL_WIDTH = 104;
    public static final int DETAIL_HEIGHT = 104;
    public static final int SURFACE_WIDTH = 100;
    public static final int SURFACE_HEIGHT = 100;
    public static final int MESH_WIDTH = 50;
    public static final int MESH_HEIGHT = 50;
    public static final int VERTEX_COUNT = SURFACE_WIDTH * SURFACE_HEIGHT;
    public static final int INDEX_COUNT = (SURFACE_WIDTH - 1) * (SURFACE_HEIGHT - 1) * 6;

    public static final int PORTRAIT_DENSITY_WIDTH = 256;
    public static final int PORTRAIT_DENSITY_HEIGHT = 512;
    public static final int LANDSCAPE_DENSITY_WIDTH = 512;
    public static final int LANDSCAPE_DENSITY_HEIGHT = 256;

    static final float REDUCTION_RATE = 0.94f;
    static final float WAVE_COEFFICIENT = 0.5f;
    static final float PORTRAIT_INTENSITY = 0.5f;
    static final float LANDSCAPE_INTENSITY = 0.35f;
    static final float INK_RADIUS = 2.0f;
    static final float INK_IMPULSE_DENSITY = 200.0f;
    static final long LONG_PRESS_RIPPLE_MS = 600L;

    private static final int SIMULATION_HZ = 60;
    private static final int MAX_SIMULATION_STEPS = 4;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long ADAPTIVE_STALL_NS = 66_666_667L;
    private static final float VELOCITY_DISSIPATION = 0.9f;
    private static final float DENSITY_DISSIPATION_PRESS = 0.92f;
    private static final float DENSITY_DISSIPATION_MODE_0 = 0.94f;
    private static final float DENSITY_DISSIPATION_RELEASE = 0.90f;
    private static final float FLUID_TIME_STEP = 0.25f;
    private static final float FLUID_TIME_SCALE = 0.9f;
    private static final float BACKWARD_STEP_SIZE = 1.0f;
    private static final float TOUCH_VELOCITY_RADIUS = 40.0f;

    /* Selectors 1..8. Selector zero in Samsung's Java is "ink disabled" and is deliberately
     * excluded from this table. Raw bits retain the exact constants in the stock smali. */
    private static final int[][] PALETTE_BITS = {
            {0x3f43c3b5, 0x3ef0f0e9, 0x3f0c8c82},
            {0x3f3ebebe, 0x3edcdcca, 0x3df0f0e9},
            {0x3e8c8c72, 0x3f028273, 0x3df0f0e9},
            {0x3d209fe8, 0x3eaaaa9f, 0x3f7afaf8},
            {0x00000000, 0x3df0f0e9, 0x3eb4b4af},
            {0x3eb4b4af, 0x3e70f0e9, 0x3f34b4af},
            {0x3e5cdcca, 0x3dc8c8ac, 0x3d209fe8},
            {0x3ea0a090, 0x3f20a090, 0x3f34b4af}
    };

    /* Keep the water state behind the exact vanilla JNI ABI.  Ripple Ink owns only its
     * density/velocity-worker state below; it must not fork a second Java wave solver. */
    private final RippleInkVanillaWaterAdapter water = new RippleInkVanillaWaterAdapter(
            DETAIL_WIDTH, DETAIL_HEIGHT, SURFACE_WIDTH, SURFACE_HEIGHT);
    private final float[] vertices = water.vertices();
    private final short[] indices = water.indices();
    private final float[] gpuHeights = water.gpuHeights();
    private final FixedStepClock clock = new FixedStepClock();
    /* The GLES path has an independent Ink clock: HFR water must never turn a display-frame
     * fraction into a density/worker substep.  Keep the CPU oracle clock above untouched so its
     * host trace remains the historical fixed/adaptive test seam. */
    private final FixedStepClock rendererInkClock = new FixedStepClock();
    private final AdaptiveFrameClock adaptiveClock = new AdaptiveFrameClock();

    private float[] density = new float[PORTRAIT_DENSITY_WIDTH * PORTRAIT_DENSITY_HEIGHT];
    private float[] densityScratch = new float[density.length];
    private byte[] encodedDensity = new byte[density.length * 4];
    private float[] fluidVelocityX = new float[1];
    private float[] fluidVelocityY = new float[1];
    private int fluidVelocityWidth = 1;
    private int fluidVelocityHeight = 1;
    private int surfaceWidth;
    private int surfaceHeight;
    private int densityWidth = PORTRAIT_DENSITY_WIDTH;
    private int densityHeight = PORTRAIT_DENSITY_HEIGHT;
    private int paletteSelector = 4;
    private boolean touched;
    private boolean waterActive;
    private float previousTouchX;
    private float previousTouchY;
    private int rippleDistance;
    private long downTimeMs;
    private long lastEventTimeMs = Long.MIN_VALUE;
    private long simulationStepCount;
    private double simulationCreditCount;
    private int inkPathEventCount;
    private int lastInkAction = -1;
    private float lastAdjustedPressure;
    private int densityRevision;
    private int encodedDensityRevision = -1;
    private boolean densityActive;
    private boolean highFrameRateEnabled;
    private float lastAdaptiveCredits;
    private float densityDissipationState = DENSITY_DISSIPATION_PRESS;
    private float densityBackwardStepState = BACKWARD_STEP_SIZE;
    private int densityPressStep;
    private boolean releaseDensityActive;

    public RippleInkPortEngine() {
        water.initWaters(
                VERTEX_COUNT,
                MESH_HEIGHT,
                MESH_WIDTH,
                SURFACE_HEIGHT,
                SURFACE_WIDTH);
    }

    public static int paletteCount() {
        return PALETTE_BITS.length;
    }

    public static boolean isInkEnabledSelector(int selector) {
        return selector >= 1 && selector <= PALETTE_BITS.length;
    }

    public static int paletteComponentBits(int selector, int component) {
        checkPaletteSelector(selector);
        if (component < 0 || component > 2) {
            throw new IllegalArgumentException("component must be 0..2");
        }
        return PALETTE_BITS[selector - 1][component];
    }

    public static float paletteComponent(int selector, int component) {
        return Float.intBitsToFloat(paletteComponentBits(selector, component));
    }

    public void setPaletteSelector(int selector) {
        checkPaletteSelector(selector);
        paletteSelector = selector;
    }

    public int getPaletteSelector() {
        return paletteSelector;
    }

    public float getPaletteRed() {
        return paletteComponent(paletteSelector, 0);
    }

    public float getPaletteGreen() {
        return paletteComponent(paletteSelector, 1);
    }

    public float getPaletteBlue() {
        return paletteComponent(paletteSelector, 2);
    }

    /** Rebuilds orientation-owned density state and isolates the next clock trace. */
    public void configureSurface(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int nextDensityWidth = safeWidth > safeHeight
                ? LANDSCAPE_DENSITY_WIDTH : PORTRAIT_DENSITY_WIDTH;
        int nextDensityHeight = safeWidth > safeHeight
                ? LANDSCAPE_DENSITY_HEIGHT : PORTRAIT_DENSITY_HEIGHT;
        boolean densityShapeChanged = densityWidth != nextDensityWidth
                || densityHeight != nextDensityHeight;
        surfaceWidth = safeWidth;
        surfaceHeight = safeHeight;
        densityWidth = nextDensityWidth;
        densityHeight = nextDensityHeight;
        if (densityShapeChanged) {
            density = new float[densityWidth * densityHeight];
            densityScratch = new float[density.length];
            encodedDensity = new byte[density.length * 4];
        }
        fluidVelocityWidth = Math.max(1, safeWidth / 12);
        fluidVelocityHeight = Math.max(1, safeHeight / 12);
        fluidVelocityX = new float[fluidVelocityWidth * fluidVelocityHeight];
        fluidVelocityY = new float[fluidVelocityWidth * fluidVelocityHeight];
        reset();
    }

    /**
     * Enables elapsed-time integration for native-refresh displays. Default is false.
     * Toggling keeps simulation state but clears both clock origins, so no old frame debt crosses
     * the mode boundary.
     */
    public void setHighFrameRateEnabled(boolean enabled) {
        if (highFrameRateEnabled == enabled) {
            return;
        }
        highFrameRateEnabled = enabled;
        resetFrameClock();
    }

    public boolean isHighFrameRateEnabled() {
        return highFrameRateEnabled;
    }

    /** Call on pause/context recreation even when simulation state should be retained. */
    public void resetFrameClock() {
        clock.reset();
        rendererInkClock.reset();
        adaptiveClock.reset();
        lastAdaptiveCredits = 0.0f;
    }

    public float getLastAdaptiveCredits() {
        return lastAdaptiveCredits;
    }

    /**
     * Routes every ordinary pointer through the recovered ink action contract.
     *
     * <p>Samsung's original Java gated Ripple Ink deposition on an S Pen source and forced its
     * pressure to one. This port deliberately removes only that source gate: callers should pass
     * ordinary finger events here and they receive the same pressure-one ink path.</p>
     */
    public boolean handleFinger(
            int action,
            float localX,
            float localY,
            float pressure,
            long eventTimeMs) {
        return handlePointer(action, localX, localY, pressure, eventTimeMs, true);
        }

        public boolean handleWaterOnly(
            int action,
            float localX,
            float localY,
            long eventTimeMs) {
        return handlePointer(action, localX, localY, 0.0f, eventTimeMs, false);
        }

        private boolean handlePointer(
            int action,
            float localX,
            float localY,
            float pressure,
            long eventTimeMs,
            boolean inkEnabled) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || eventTimeMs < lastEventTimeMs) {
            return false;
        }
        lastEventTimeMs = eventTimeMs;
        lastAdjustedPressure = Math.max(0.0f, Math.min(1.0f, pressure));

        switch (action) {
            case ACTION_DOWN:
                touched = true;
                downTimeMs = eventTimeMs;
                previousTouchX = localX;
                previousTouchY = localY;
                densityPressStep = 0;
                rippleDistance = 0;
                if (inkEnabled) {
                    recordInkAction(ACTION_DOWN);
                    depositPoint(localX, localY);
                }
                injectWater(localX, localY, 4.0f * currentIntensity());
                return true;
            case ACTION_MOVE:
                if (!touched) {
                    return false;
                }
                float oldX = previousTouchX;
                float oldY = previousTouchY;
                float dx = localX - oldX;
                float dy = localY - oldY;
                rippleDistance += (int) Math.sqrt(dx * dx + dy * dy);
                previousTouchX = localX;
                previousTouchY = localY;
                if (inkEnabled) {
                    recordInkAction(ACTION_MOVE);
                    depositSegment(oldX, oldY, localX, localY);
                }
                injectFluidVelocity(oldX, oldY, localX, localY);
                if (rippleDistance > dragRippleThresholdPx()) {
                    rippleDistance = 0;
                    injectWater(localX, localY, 3.0f * currentIntensity());
                }
                return true;
            case ACTION_UP:
                if (!touched) {
                    return false;
                }
                if (inkEnabled) {
                    recordInkAction(ACTION_UP);
                }
                if (eventTimeMs - downTimeMs > LONG_PRESS_RIPPLE_MS) {
                    injectWater(localX, localY, 4.0f * currentIntensity());
                }
                endTouch();
                return true;
            case ACTION_CANCEL:
                if (!touched) {
                    return false;
                }
                if (inkEnabled) {
                    recordInkAction(ACTION_CANCEL);
                }
                endTouch();
                return true;
            default:
                return false;
        }
    }

    /** Advances only complete recovered 60 Hz ticks while HFR is disabled. */
    public int advanceTo(long frameTimeNs) {
        if (highFrameRateEnabled) {
            return (int) advanceFrame(frameTimeNs);
        }
        int steps = clock.advance(frameTimeNs);
        for (int step = 0; step < steps; ++step) {
            advanceExactStockTick();
            ++simulationStepCount;
            simulationCreditCount += 1.0d;
        }
        return steps;
    }

    /**
     * Advances either the stock fixed clock or the opt-in elapsed display-refresh clock.
     * The returned value is logical 60 Hz credits consumed by this presentation frame.
     */
    public float advanceFrame(long frameTimeNs) {
        if (!highFrameRateEnabled) {
            int steps = advanceTo(frameTimeNs);
            lastAdaptiveCredits = steps;
            return steps;
        }
        float credits = adaptiveClock.advance(frameTimeNs);
        lastAdaptiveCredits = credits;
        if (credits <= 0.0f) {
            return 0.0f;
        }
        advanceAdaptiveCredits(credits);
        return credits;
    }

    /**
     * Renderer clock path.  With HFR off both fields retain the exact old fixed-60 branch.
     * With HFR on, water is advanced by the elapsed display fraction while Ink advances only
     * complete rational 60 Hz ticks.  The renderer must execute one FluidPipeline q=1 pass for
     * each {@link RendererFrameAdvance#inkTicks}; fractions are deliberately not exposed there.
     */
    RendererFrameAdvance advanceRendererFrame(long frameTimeNs) {
        if (!highFrameRateEnabled) {
            int steps = rendererInkClock.advance(frameTimeNs);
            lastAdaptiveCredits = steps;
            for (int step = 0; step < steps; ++step) {
                advanceExactWaterTick();
                ++simulationStepCount;
                simulationCreditCount += 1.0d;
            }
            return new RendererFrameAdvance(steps, steps);
        }
        float waterCredits = adaptiveClock.advance(frameTimeNs);
        lastAdaptiveCredits = waterCredits;
        advanceRendererCredits(waterCredits);
        // A display timestamp is integer nanoseconds while 1/60 is recurring.  The HFR clock
        // accepts the sub-nanosecond representation residue so a 120 Hz sequence alternates
        // cleanly every two vsyncs rather than slipping its first Ink tick to frame three.
        int inkTicks = rendererInkClock.advanceHfr(frameTimeNs);
        return new RendererFrameAdvance(waterCredits, inkTicks);
    }

    static final class RendererFrameAdvance {
        final float waterCredits;
        final int inkTicks;

        RendererFrameAdvance(float waterCredits, int inkTicks) {
            this.waterCredits = waterCredits;
            this.inkTicks = inkTicks;
        }
    }

    public void reset() {
        water.reset();
        Arrays.fill(density, 0.0f);
        Arrays.fill(densityScratch, 0.0f);
        Arrays.fill(encodedDensity, (byte) 0);
        Arrays.fill(fluidVelocityX, 0.0f);
        Arrays.fill(fluidVelocityY, 0.0f);
        touched = false;
        waterActive = false;
        previousTouchX = 0.0f;
        previousTouchY = 0.0f;
        rippleDistance = 0;
        downTimeMs = 0L;
        lastEventTimeMs = Long.MIN_VALUE;
        simulationStepCount = 0L;
        simulationCreditCount = 0.0d;
        inkPathEventCount = 0;
        lastInkAction = -1;
        lastAdjustedPressure = 0.0f;
        densityActive = false;
        densityDissipationState = DENSITY_DISSIPATION_PRESS;
        densityBackwardStepState = BACKWARD_STEP_SIZE;
        densityPressStep = 0;
        releaseDensityActive = false;
        ++densityRevision;
        encodedDensityRevision = densityRevision;
        resetFrameClock();
    }

    public boolean isTouched() {
        return touched;
    }

    public boolean isWaterActive() {
        return waterActive;
    }

    public int getInkPathEventCount() {
        return inkPathEventCount;
    }

    public int getLastInkAction() {
        return lastInkAction;
    }

    public float getLastAdjustedPressure() {
        return lastAdjustedPressure;
    }

    public long getSimulationStepCount() {
        return simulationStepCount;
    }

    public double getSimulationCreditCount() {
        return simulationCreditCount;
    }

    public int getDensityWidth() {
        return densityWidth;
    }

    public int getDensityHeight() {
        return densityHeight;
    }

    public int getDensityRevision() {
        return densityRevision;
    }

    public float densitySum() {
        float sum = 0.0f;
        for (float value : density) {
            sum += value;
        }
        return sum;
    }

    float[] vertices() {
        return vertices;
    }

    short[] indices() {
        return indices;
    }

    float[] gpuHeights() {
        return gpuHeights;
    }

    float[] densityValues() {
        return density;
    }

    float[] fluidVelocityXValues() {
        return fluidVelocityX;
    }

    float[] fluidVelocityYValues() {
        return fluidVelocityY;
    }

    byte[] encodedDensityRgba() {
        if (encodedDensityRevision == densityRevision) {
            return encodedDensity;
        }
        for (int i = 0; i < density.length; ++i) {
            float value = Math.max(0.0f, Math.min(127.0f, density[i]));
            int whole = Math.min(127, (int) Math.floor(value));
            int fraction = Math.min(255, Math.max(0,
                    Math.round((value - whole) * 255.0f)));
            int output = i * 4;
            encodedDensity[output] = (byte) whole;
            encodedDensity[output + 1] = (byte) fraction;
            encodedDensity[output + 2] = 0;
            encodedDensity[output + 3] = (byte) 0xff;
        }
        encodedDensityRevision = densityRevision;
        return encodedDensity;
    }

    private void recordInkAction(int action) {
        ++inkPathEventCount;
        lastInkAction = action;
    }

    private void endTouch() {
        touched = false;
        densityDissipationState = DENSITY_DISSIPATION_RELEASE;
        releaseDensityActive = true;
        downTimeMs = 0L;
        rippleDistance = 0;
    }

    private int dragRippleThresholdPx() {
        return Math.max(1, (int) (0.2f * Math.min(surfaceWidth, surfaceHeight)));
    }

    private float currentIntensity() {
        return surfaceWidth > surfaceHeight ? LANDSCAPE_INTENSITY : PORTRAIT_INTENSITY;
    }

    private void injectWater(float localX, float localY, float strength) {
        float xRatio = surfaceWidth > surfaceHeight ? 45.0f : 30.0f;
        float yRatio = surfaceWidth > surfaceHeight ? 25.0f : 46.0f;
        float glX = (localX - surfaceWidth * 0.5f) * xRatio / surfaceWidth;
        float glY = (localY - surfaceHeight * 0.5f) * yRatio / surfaceHeight;
        // Samsung Java calls ripple(glY, glX, ...); retain that transposed contract.
        water.ripple(
                MESH_WIDTH,
                MESH_HEIGHT,
                DETAIL_WIDTH,
                DETAIL_HEIGHT,
                glY,
                glX,
                strength);
        waterActive = true;
    }

    private void depositPoint(float localX, float localY) {
        visitDensity(localX, localY, localX, localY, false);
    }

    private void depositSegment(float previousX, float previousY, float currentX, float currentY) {
        visitDensity(previousX, previousY, currentX, currentY, true);
    }

    /** CPU oracle for the recovered AddInk fragment. It is not the missing velocity-advection pass. */
    private void visitDensity(
            float previousX,
            float previousY,
            float currentX,
            float currentY,
            boolean segment) {
        float previousGlY = surfaceHeight - previousY;
        float currentGlY = surfaceHeight - currentY;
        float dx = currentX - previousX;
        float dy = currentGlY - previousGlY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = length == 0.0f ? 0.0f : dx / length;
        float ny = length == 0.0f ? 0.0f : dy / length;

        float minX = (segment ? Math.min(previousX, currentX) : currentX) - INK_RADIUS;
        float maxX = (segment ? Math.max(previousX, currentX) : currentX) + INK_RADIUS;
        float minY = (segment ? Math.min(previousGlY, currentGlY) : currentGlY) - INK_RADIUS;
        float maxY = (segment ? Math.max(previousGlY, currentGlY) : currentGlY) + INK_RADIUS;
        int xBegin = densityIndexFloor(minX, surfaceWidth, densityWidth);
        int xEnd = densityIndexCeil(maxX, surfaceWidth, densityWidth);
        int yBegin = densityIndexFloor(minY, surfaceHeight, densityHeight);
        int yEnd = densityIndexCeil(maxY, surfaceHeight, densityHeight);
        boolean changed = false;

        for (int y = yBegin; y <= yEnd; ++y) {
            float py = (y + 0.5f) * surfaceHeight / densityHeight;
            for (int x = xBegin; x <= xEnd; ++x) {
                float px = (x + 0.5f) * surfaceWidth / densityWidth;
                float distance;
                if (segment && length > 0.0f) {
                    float projection = nx * (px - previousX) + ny * (py - previousGlY);
                    // Recovered shader uses strict endpoints for mode 2.
                    if (projection <= 0.0f || projection >= length) {
                        continue;
                    }
                    float projectedX = previousX + projection * nx;
                    float projectedY = previousGlY + projection * ny;
                    float pdx = px - projectedX;
                    float pdy = py - projectedY;
                    distance = (float) Math.sqrt(pdx * pdx + pdy * pdy);
                } else {
                    float pdx = px - currentX;
                    float pdy = py - currentGlY;
                    distance = (float) Math.sqrt(pdx * pdx + pdy * pdy);
                }
                if (distance >= INK_RADIUS) {
                    continue;
                }
                int index = y * densityWidth + x;
                float baseAddition = segment
                        ? INK_IMPULSE_DENSITY * (float) Math.exp(
                                -distance * distance
                                        / (0.8f * INK_RADIUS * INK_RADIUS))
                        : INK_IMPULSE_DENSITY / (1.0f + distance);
                float addition = baseAddition * lastAdjustedPressure;
                density[index] = Math.min(127.0f, density[index] + addition);
                changed = true;
            }
        }
        if (changed) {
            densityActive = true;
            ++densityRevision;
        }
    }

    private static int densityIndexFloor(float coordinate, int extent, int count) {
        int index = (int) Math.floor(coordinate * count / extent - 0.5f);
        return Math.max(0, Math.min(count - 1, index));
    }

    private static int densityIndexCeil(float coordinate, int extent, int count) {
        int index = (int) Math.ceil(coordinate * count / extent - 0.5f);
        return Math.max(0, Math.min(count - 1, index));
    }

    private void advanceAdaptiveCredits(float credits) {
        float bounded = sanitizeAdaptiveCredits(credits);
        if (bounded <= 0.0f) {
            return;
        }
        // q=1 is a hard branch: it executes the same statements/order as fixed stock mode.
        if (bounded == 1.0f) {
            advanceExactStockTick();
            ++simulationStepCount;
            simulationCreditCount += 1.0d;
            return;
        }
        int whole = (int) Math.floor(bounded);
        float fractional = bounded - whole;
        for (int step = 0; step < whole; ++step) {
            advanceExactStockTick();
            ++simulationStepCount;
            simulationCreditCount += 1.0d;
        }
        if (fractional > 0.0f) {
            if (waterActive) {
                waterActive = !moveWaterFractional(fractional);
                fillGpuHeights();
            }
            advanceFluid(fractional);
            simulationCreditCount += fractional;
        }
    }

    private void advanceRendererCredits(float credits) {
        float bounded = sanitizeAdaptiveCredits(credits);
        if (bounded <= 0.0f) {
            return;
        }
        if (bounded == 1.0f) {
            advanceExactWaterTick();
            ++simulationStepCount;
            simulationCreditCount += 1.0d;
            return;
        }
        int whole = (int) Math.floor(bounded);
        float fractional = bounded - whole;
        for (int step = 0; step < whole; ++step) {
            advanceExactWaterTick();
            ++simulationStepCount;
            simulationCreditCount += 1.0d;
        }
        if (fractional > 0.0f) {
            if (waterActive) {
                waterActive = !moveWaterFractional(fractional);
                fillGpuHeights();
            }
            simulationCreditCount += fractional;
        }
    }

    private void advanceExactWaterTick() {
        if (waterActive) {
            waterActive = !moveWater();
            fillGpuHeights();
        }
    }

    private void advanceExactStockTick() {
        if (waterActive) {
            waterActive = !moveWater();
            fillGpuHeights();
        }
        advanceFluid(1.0f);
    }

    private boolean moveWaterFractional(float stockTicks) {
        if (stockTicks == 1.0f) {
            return moveWater();
        }
        boolean landscape = surfaceWidth > surfaceHeight;
        int xBegin = landscape ? 21 : 3;
        int yBegin = landscape ? 3 : 21;
        int xEnd = landscape ? 83 : 101;
        int yEnd = landscape ? 101 : 83;
        return water.moveAdaptive(
                xBegin,
                yBegin,
                xEnd,
                yEnd,
                DETAIL_WIDTH,
                DETAIL_HEIGHT,
                REDUCTION_RATE,
                WAVE_COEFFICIENT,
                stockTicks);
    }

    /**
     * Time-scaled CPU oracle for the recovered density backtrace.
     *
     * <p>The S4 implementation uploads a worker-produced velocity texture. Until that worker is
     * fully recovered, this state uses pointer velocity on the exact screen/12 grid and therefore
     * remains diagnostic. Damping, backtrace, density dissipation and clamp-to-edge boundary
     * behavior are integrated in stock-tick units with a q=1 exact branch.</p>
     */
    private void advanceFluid(float stockTicks) {
        if ((!densityActive && !hasFluidVelocity()) || stockTicks <= 0.0f) {
            return;
        }
        float velocityDamping = stockTicks == 1.0f
                ? VELOCITY_DISSIPATION
                : scaleDissipation(VELOCITY_DISSIPATION, stockTicks);
        float densityDamping = stockTicks == 1.0f
                ? densityDissipationState
                : scaleDissipation(densityDissipationState, stockTicks);
        float timeStepX = FLUID_TIME_STEP * FLUID_TIME_SCALE / fluidVelocityWidth;
        float timeStepY = FLUID_TIME_STEP * FLUID_TIME_SCALE / fluidVelocityHeight;
        float centerX = previousTouchX;
        float centerY = surfaceHeight - previousTouchY;
        boolean downOrUpPhase = lastInkAction >= 0 && lastInkAction < ACTION_MOVE;

        for (int y = 0; y < densityHeight; ++y) {
            float v = (y + 0.5f) / densityHeight;
            float screenY = v * surfaceHeight;
            for (int x = 0; x < densityWidth; ++x) {
                float u = (x + 0.5f) / densityWidth;
                float screenX = u * surfaceWidth;
                float velocityX = sampleVelocity(fluidVelocityX, u, v);
                float velocityY = sampleVelocity(fluidVelocityY, u, v);
                float backwardStep = densityBackwardStepState;
                if (downOrUpPhase) {
                    float dx = screenX - centerX;
                    float dy = screenY - centerY;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    if (distance < 80.0f) {
                        backwardStep = 0.0075f * distance;
                    }
                }
                float sourceU = u - backwardStep * timeStepX * velocityX * stockTicks;
                float sourceV = v - backwardStep * timeStepY * velocityY * stockTicks;
                densityScratch[y * densityWidth + x] = densityDamping
                        * sampleDensityClamped(sourceU, sourceV);
            }
        }
        float[] previousDensity = density;
        density = densityScratch;
        densityScratch = previousDensity;
        for (int i = 0; i < fluidVelocityX.length; ++i) {
            fluidVelocityX[i] *= velocityDamping;
            fluidVelocityY[i] *= velocityDamping;
        }
        if (touched) {
            releaseDensityActive = false;
            densityDissipationState = DENSITY_DISSIPATION_PRESS;
            densityBackwardStepState = densityPressStep < 10
                    ? 0.1f * densityPressStep : BACKWARD_STEP_SIZE;
            ++densityPressStep;
        } else if (!releaseDensityActive) {
            densityDissipationState = DENSITY_DISSIPATION_MODE_0;
            densityBackwardStepState = BACKWARD_STEP_SIZE;
        }
        ++densityRevision;
    }

    private void injectFluidVelocity(
            float oldX, float oldY, float currentX, float currentY) {
        float impulseX = currentX - oldX;
        float impulseY = oldY - currentY; // OpenGL's Y axis is bottom-up.
        if (impulseX == 0.0f && impulseY == 0.0f) {
            return;
        }
        float centerX = currentX * fluidVelocityWidth / surfaceWidth;
        float centerY = (surfaceHeight - currentY) * fluidVelocityHeight / surfaceHeight;
        float radiusX = Math.max(1.0f,
                TOUCH_VELOCITY_RADIUS * fluidVelocityWidth / surfaceWidth);
        float radiusY = Math.max(1.0f,
                TOUCH_VELOCITY_RADIUS * fluidVelocityHeight / surfaceHeight);
        int xBegin = Math.max(0, (int) Math.floor(centerX - radiusX));
        int xEnd = Math.min(fluidVelocityWidth - 1, (int) Math.ceil(centerX + radiusX));
        int yBegin = Math.max(0, (int) Math.floor(centerY - radiusY));
        int yEnd = Math.min(fluidVelocityHeight - 1, (int) Math.ceil(centerY + radiusY));
        for (int y = yBegin; y <= yEnd; ++y) {
            float normalizedY = (y + 0.5f - centerY) / radiusY;
            for (int x = xBegin; x <= xEnd; ++x) {
                float normalizedX = (x + 0.5f - centerX) / radiusX;
                float distanceSquared = normalizedX * normalizedX + normalizedY * normalizedY;
                if (distanceSquared >= 1.0f) {
                    continue;
                }
                float weight = 1.0f - distanceSquared;
                int index = y * fluidVelocityWidth + x;
                fluidVelocityX[index] = clampVelocity(
                        fluidVelocityX[index] + impulseX * weight);
                fluidVelocityY[index] = clampVelocity(
                        fluidVelocityY[index] + impulseY * weight);
            }
        }
    }

    private float sampleVelocity(float[] field, float u, float v) {
        float x = clamp01(u) * fluidVelocityWidth - 0.5f;
        float y = clamp01(v) * fluidVelocityHeight - 0.5f;
        int xFloor = (int) Math.floor(x);
        int yFloor = (int) Math.floor(y);
        int x0 = clampIndex(xFloor, fluidVelocityWidth);
        int y0 = clampIndex(yFloor, fluidVelocityHeight);
        int x1 = clampIndex(xFloor + 1, fluidVelocityWidth);
        int y1 = clampIndex(yFloor + 1, fluidVelocityHeight);
        float tx = x - xFloor;
        float ty = y - yFloor;
        float top = lerp(field[y0 * fluidVelocityWidth + x0],
                field[y0 * fluidVelocityWidth + x1], tx);
        float bottom = lerp(field[y1 * fluidVelocityWidth + x0],
                field[y1 * fluidVelocityWidth + x1], tx);
        return lerp(top, bottom, ty);
    }

    private float sampleDensityClamped(float u, float v) {
        float x = clamp01(u) * densityWidth - 0.5f;
        float y = clamp01(v) * densityHeight - 0.5f;
        int xFloor = (int) Math.floor(x);
        int yFloor = (int) Math.floor(y);
        int x0 = clampIndex(xFloor, densityWidth);
        int y0 = clampIndex(yFloor, densityHeight);
        int x1 = clampIndex(xFloor + 1, densityWidth);
        int y1 = clampIndex(yFloor + 1, densityHeight);
        float tx = x - xFloor;
        float ty = y - yFloor;
        float top = lerp(density[y0 * densityWidth + x0],
                density[y0 * densityWidth + x1], tx);
        float bottom = lerp(density[y1 * densityWidth + x0],
                density[y1 * densityWidth + x1], tx);
        return lerp(top, bottom, ty);
    }

    private boolean hasFluidVelocity() {
        for (int i = 0; i < fluidVelocityX.length; ++i) {
            if (Math.abs(fluidVelocityX[i]) > 0.0001f
                    || Math.abs(fluidVelocityY[i]) > 0.0001f) {
                return true;
            }
        }
        return false;
    }

    static float scaleDissipation(float perStockTick, float stockTicks) {
        if (stockTicks <= 0.0f || perStockTick == 1.0f) {
            return 1.0f;
        }
        if (stockTicks == 1.0f) {
            return perStockTick;
        }
        return (float) Math.pow(perStockTick, stockTicks);
    }

    static float sanitizeAdaptiveCredits(float stockTicks) {
        if (Float.isNaN(stockTicks) || Float.isInfinite(stockTicks) || stockTicks <= 0.0f) {
            return 0.0f;
        }
        return Math.min(MAX_SIMULATION_STEPS, stockTicks);
    }

    private static float clampVelocity(float value) {
        return Math.max(-127.0f, Math.min(127.0f, value));
    }

    private static int clampIndex(int index, int count) {
        return Math.max(0, Math.min(count - 1, index));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private boolean moveWater() {
        boolean landscape = surfaceWidth > surfaceHeight;
        int xBegin = landscape ? 21 : 3;
        int yBegin = landscape ? 3 : 21;
        int xEnd = landscape ? 83 : 101;
        int yEnd = landscape ? 101 : 83;
        return water.move(
                xBegin,
                yBegin,
                xEnd,
                yEnd,
                DETAIL_WIDTH,
                DETAIL_HEIGHT,
                REDUCTION_RATE,
                WAVE_COEFFICIENT);
    }

    private void fillGpuHeights() {
        water.packGpuHeights(DETAIL_WIDTH, SURFACE_WIDTH, SURFACE_HEIGHT);
    }

    private static void checkPaletteSelector(int selector) {
        if (!isInkEnabledSelector(selector)) {
            throw new IllegalArgumentException("Ripple Ink palette selector must be 1..8");
        }
    }

    /** Rational accumulator avoids drift from rounding one tick to 16,666,667 ns. */
    static final class FixedStepClock {
        private static final long MAX_ACCUMULATOR_UNITS =
                NANOS_PER_SECOND * MAX_SIMULATION_STEPS;
        private static final long MAX_FRAME_DELTA_NS =
                (MAX_ACCUMULATOR_UNITS + SIMULATION_HZ - 1L) / SIMULATION_HZ;

        private long previousFrameNs = Long.MIN_VALUE;
        private long accumulatorUnits;

        int advance(long frameTimeNs) {
            if (previousFrameNs == Long.MIN_VALUE) {
                previousFrameNs = frameTimeNs;
                return 0;
            }
            long elapsedNs = frameTimeNs - previousFrameNs;
            previousFrameNs = frameTimeNs;
            if (elapsedNs <= 0L) {
                return 0;
            }
            long boundedElapsedNs = Math.min(elapsedNs, MAX_FRAME_DELTA_NS);
            accumulatorUnits = Math.min(MAX_ACCUMULATOR_UNITS,
                    accumulatorUnits + boundedElapsedNs * SIMULATION_HZ);
            int steps = (int) (accumulatorUnits / NANOS_PER_SECOND);
            accumulatorUnits -= (long) steps * NANOS_PER_SECOND;
            return steps;
        }

        int advanceHfr(long frameTimeNs) {
            if (previousFrameNs == Long.MIN_VALUE) {
                previousFrameNs = frameTimeNs;
                return 0;
            }
            long elapsedNs = frameTimeNs - previousFrameNs;
            previousFrameNs = frameTimeNs;
            if (elapsedNs <= 0L) {
                return 0;
            }
            long boundedElapsedNs = Math.min(elapsedNs, MAX_FRAME_DELTA_NS);
            accumulatorUnits = Math.min(MAX_ACCUMULATOR_UNITS,
                    accumulatorUnits + boundedElapsedNs * SIMULATION_HZ);
            // 60 units equal one nanosecond of clock error.  Preserve the tiny negative residue
            // after consuming a tick; that makes the rational phase exact over long runs.
            int steps = (int) ((accumulatorUnits + SIMULATION_HZ) / NANOS_PER_SECOND);
            accumulatorUnits -= (long) steps * NANOS_PER_SECOND;
            return steps;
        }

        void reset() {
            previousFrameNs = Long.MIN_VALUE;
            accumulatorUnits = 0L;
        }
    }

    /** Elapsed display clock for opt-in HFR; stalled frames never create replay debt. */
    static final class AdaptiveFrameClock {
        private long previousFrameNs = Long.MIN_VALUE;

        float advance(long frameTimeNs) {
            if (previousFrameNs == Long.MIN_VALUE) {
                previousFrameNs = frameTimeNs;
                return 0.0f;
            }
            long elapsedNs = frameTimeNs - previousFrameNs;
            previousFrameNs = frameTimeNs;
            if (elapsedNs <= 0L || elapsedNs > ADAPTIVE_STALL_NS) {
                return 0.0f;
            }
            return adaptiveCreditsForElapsedNanos(elapsedNs);
        }

        void reset() {
            previousFrameNs = Long.MIN_VALUE;
        }
    }

    static float adaptiveCreditsForElapsedNanos(long elapsedNs) {
        if (elapsedNs <= 0L || elapsedNs > ADAPTIVE_STALL_NS) {
            return 0.0f;
        }
        double credits = elapsedNs * (double) SIMULATION_HZ / NANOS_PER_SECOND;
        // Nanoseconds cannot represent 1/60 exactly; keep the intended stock branch exact.
        if (Math.abs(credits - 1.0d) <= 0.0001d) {
            return 1.0f;
        }
        return sanitizeAdaptiveCredits((float) credits);
    }
}
