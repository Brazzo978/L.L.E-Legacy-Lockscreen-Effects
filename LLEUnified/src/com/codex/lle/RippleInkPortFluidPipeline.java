package com.codex.lle;

import java.util.Arrays;

/**
 * Deterministic app-owned controller for Ripple Ink's velocity producer and GLES density passes.
 *
 * <p>The pass contract uses one velocity upload, one density advection and an optional AddInk
 * pass per logical stock tick; screen/12 velocity cells; 256x512 (portrait) or 512x256
 * (landscape) density; ten pressure Jacobi iterations; density dt=.25*.9 and RGBA fixed-point
 * packing. On Android, the ENB4 N3 pthread worker is the production producer and returns the
 * completed N-1 velocity surface before it launches N. The scalar worker remains solely for the
 * host numerical tests, where JNI is intentionally unavailable.</p>
 */
final class RippleInkPortFluidPipeline {
    static final int PASS_UPLOAD_VELOCITY = 1;
    static final int PASS_ADVECT_DENSITY = 2;
    static final int PASS_ADD_INK = 3;

    static final int JACOBI_ITERATIONS = 10;
    static final float VELOCITY_TIME_STEP = 0.25f;
    static final float DENSITY_BACKWARD_STEP = 1.0f;
    static final float DENSITY_DISSIPATION_PRESS = 0.92f;
    static final float DENSITY_DISSIPATION_MODE_0 = 0.94f;
    static final float DENSITY_DISSIPATION_RELEASE = 0.90f;
    static final float DENSITY_DISSIPATION_CLEAR = 0.0f;
    static final float DENSITY_TIME_STEP_SCALE = 0.9f;
    static final float DIVERGENCE_SCALE = 0.2f;
    // Stable normalization used by the coherent Java-domain worker port.
    static final float JACOBI_ALPHA = -6.25f;
    static final float JACOBI_INVERSE_BETA = 0.25f;
    private static final float VELOCITY_ENCODE_MIN = -127.0f;
    private static final float VELOCITY_ENCODE_MAX = 127.0f;
    private static final float VELOCITY_ENCODE_BIAS = 127.0f;
    /** The source producer is deliberately fixed at logical 60 Hz, never display refresh. */
    static final int SOURCE_HZ = 60;
    /**
     * 127 * .9^106 is below .5/255.  This is a conservative perceptual envelope (the RGBA8
     * low-byte may remain sticky); parking never clears or reads back the retained density FBO.
     */
    static final float TAIL_DENSITY_THRESHOLD = 0.5f / 255.0f;
    private static final float SOURCE_TAP_RADIUS = 40.0f;
    private static final float SOURCE_TAP_IMPULSE = 100.0f;
    private static final float SOURCE_MODE_1_RADIUS = 25.0f;
    private static final float SOURCE_MODE_1_IMPULSE = 150.0f;
    private static final float SOURCE_SEGMENT_RADIUS = 30.0f;
    private static final float SOURCE_SEGMENT_IMPULSE = 40.0f;
    private static final float SOURCE_MODE_0_DIVERGENCE_RADIUS = 20.0f;
    private static final float SOURCE_MODE_0_DIVERGENCE_STRENGTH = 45.0f;
    private static final float SOURCE_MODE_1_DIVERGENCE_RADIUS = 20.0f;
    private static final float SOURCE_MODE_1_DIVERGENCE_STRENGTH = 10.0f;
    private static final float VELOCITY_SELF_ADVECT_STEP = 0.25f;
    private static final float VELOCITY_LOCAL_RADIUS_SQUARED = 625.0f;
    private static final float VELOCITY_LOCAL_MODE_BIAS = 1.5f;
    private static final float VELOCITY_DIVERGENCE_RADIUS = 4.0f;
    private static final float VELOCITY_DIVERGENCE_STRENGTH = 20.0f;
    /** Recovered state-1 (stationary press) profile, distinct from the mode-2 source stream. */
    private static final int PRESS_TICK_COUNT = 13;
    private static final int PRESS_INK_TICK_COUNT = 10;
    private static final float PRESS_RADIUS_INCREMENT = 8.0f;
    private static final float PRESS_IMPULSE = 200.0f;
    private static final float PRESS_VELOCITY_DISSIPATION = 0.94f;
    private static final float PRESS_DIVERGENCE_RADIUS = 40.0f;
    private static final float PRESS_MOVE_DISTANCE_SQUARED = 4.0f;
    private static final float VELOCITY_WORKER_MARGIN = 60.0f;
    private static final float RAND_31_INVERSE = 1.0f / 2147483648.0f;
    private static final int DEFAULT_WORKER_RANDOM_SEED = 0x13579bdf;
    private static int productionWorkerRandomCounter;

    private static final int SOURCE_IDLE = 0;
    private static final int SOURCE_ARMED = 1;
    private static final int SOURCE_DRAGGING = 2;
    private static final int SOURCE_RELEASE_PENDING = 3;
    private static final int SOURCE_RELEASING = 4;

    /**
     * Latest-value UI-to-GL mailbox. Samsung's JNI boundary receives only the current callback;
     * it does not retain a MotionEvent history or timestamps for later interpolation.
     */
    private static final class InputSample {
        final int action;
        final float x;
        final float y;
        final float pressure;
        final boolean inkEnabled;

        InputSample(int action, float x, float y, float pressure, boolean inkEnabled) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.pressure = pressure;
            this.inkEnabled = inkEnabled;
        }
    }

    private static final class SourceEmission {
        final float previousX;
        final float previousY;
        final float currentX;
        final float currentY;
        final float pressure;
        final boolean tap;
        /** -1 is the normal input sampler; 0..12 is the self-contained state-1 press recipe. */
        final int pressTick;
        /** Native isMovingEvent latched by the most recent raw MOVE callback (0/1/2). */
        final int movingMode;

        SourceEmission(float previousX, float previousY, float currentX, float currentY,
                float pressure, boolean tap, int pressTick, int movingMode) {
            this.previousX = previousX;
            this.previousY = previousY;
            this.currentX = currentX;
            this.currentY = currentY;
            this.pressure = pressure;
            this.tap = tap;
            this.pressTick = pressTick;
            this.movingMode = movingMode;
        }
    }

    /** Immutable-at-dispatch copy of the UI-owned native globals for one GL tick. */
    private static final class TickSnapshot {
        final long inputGeneration;
        final int sourceStateAtStart;
        int sourceState;
        boolean fingerDown;
        float currentX;
        float currentY;
        float previousX;
        float previousY;
        float committedX;
        float committedY;
        float adjustedPressure;
        float densityDissipation;
        float backwardStep;
        float densityUpperBound;
        boolean pressActive;
        int stateStep;
        int dragStep;
        float pressCenterX;
        float pressCenterY;
        float pressPressure;
        boolean pressCycleCompleted;
        int lastInputAction;
        int heldMovingMode;
        Preset persistentProfile;
        SourceEmission emission;
        Preset nextPreset;
        boolean inkInjected;

        TickSnapshot(long inputGeneration, int sourceState, boolean fingerDown, float currentX,
                float currentY, float previousX, float previousY, float committedX,
                float committedY, float adjustedPressure, float densityDissipation,
                float backwardStep, float densityUpperBound, boolean pressActive, int stateStep,
                int dragStep, float pressCenterX, float pressCenterY, float pressPressure,
                boolean pressCycleCompleted, int lastInputAction, int heldMovingMode,
                Preset persistentProfile) {
            this.inputGeneration = inputGeneration;
            this.sourceStateAtStart = sourceState;
            this.sourceState = sourceState;
            this.fingerDown = fingerDown;
            this.currentX = currentX;
            this.currentY = currentY;
            this.previousX = previousX;
            this.previousY = previousY;
            this.committedX = committedX;
            this.committedY = committedY;
            this.adjustedPressure = adjustedPressure;
            this.densityDissipation = densityDissipation;
            this.backwardStep = backwardStep;
            this.densityUpperBound = densityUpperBound;
            this.pressActive = pressActive;
            this.stateStep = stateStep;
            this.dragStep = dragStep;
            this.pressCenterX = pressCenterX;
            this.pressCenterY = pressCenterY;
            this.pressPressure = pressPressure;
            this.pressCycleCompleted = pressCycleCompleted;
            this.lastInputAction = lastInputAction;
            this.heldMovingMode = heldMovingMode;
            this.persistentProfile = persistentProfile;
        }
    }

    interface PassSink {
        void uploadVelocity(byte[] rgba, int width, int height);

        void advectDensity(AdvectPass pass);

        void addInk(AddInkPass pass);
    }

    static final class AdvectPass {
        final int sourceIndex;
        final int destinationIndex;
        final float timeStepX;
        final float timeStepY;
        final float backwardStep;
        final float dissipation;
        final float scaleX;
        final float scaleY;
        final float centerX;
        final float centerY;
        final int dragMode;
        final float logicalCredits;

        AdvectPass(
                int sourceIndex,
                int destinationIndex,
                float timeStepX,
                float timeStepY,
                float backwardStep,
                float dissipation,
                float scaleX,
                float scaleY,
                float centerX,
                float centerY,
                int dragMode,
                float logicalCredits) {
            this.sourceIndex = sourceIndex;
            this.destinationIndex = destinationIndex;
            this.timeStepX = timeStepX;
            this.timeStepY = timeStepY;
            this.backwardStep = backwardStep;
            this.dissipation = dissipation;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.centerX = centerX;
            this.centerY = centerY;
            this.dragMode = dragMode;
            this.logicalCredits = logicalCredits;
        }
    }

    static final class AddInkPass {
        final int sourceIndex;
        final int destinationIndex;
        final float currentX;
        final float currentY;
        final float previousX;
        final float previousY;
        final float normalX;
        final float normalY;
        final float length;
        final float radius;
        final float impulseDensity;
        final float scaleX;
        final float scaleY;
        final int mode;
        final float logicalCredits;

        AddInkPass(
                int sourceIndex,
                int destinationIndex,
                float currentX,
                float currentY,
                float previousX,
                float previousY,
                float normalX,
                float normalY,
                float length,
                float radius,
                float impulseDensity,
                float scaleX,
                float scaleY,
                int mode,
                float logicalCredits) {
            this.sourceIndex = sourceIndex;
            this.destinationIndex = destinationIndex;
            this.currentX = currentX;
            this.currentY = currentY;
            this.previousX = previousX;
            this.previousY = previousY;
            this.normalX = normalX;
            this.normalY = normalY;
            this.length = length;
            this.radius = radius;
            this.impulseDensity = impulseDensity;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.mode = mode;
            this.logicalCredits = logicalCredits;
        }
    }

    private static final class Preset {
        int mode;
        float addRadius;
        float addImpulse;
        float velocityDissipation;
        float nextDensityDissipation;
        boolean addInk;
        boolean forceProjection;
        float divergenceRadius = VELOCITY_DIVERGENCE_RADIUS;
        float divergenceStrength = VELOCITY_DIVERGENCE_STRENGTH;
    }

    private static Preset defaultPreset() {
        Preset preset = new Preset();
        preset.mode = 0;
        // ENB4 onInit native globals: f8=.9, fc=.92, 100=20, 10c=50.  The first
        // Update runs before any state recipe is allowed to overwrite this profile.
        preset.velocityDissipation = 0.90f;
        preset.nextDensityDissipation = DENSITY_DISSIPATION_PRESS;
        preset.divergenceRadius = 20.0f;
        preset.divergenceStrength = 50.0f;
        return preset;
    }

    /**
     * Host-only scalar test seam. Android production calls ENB4-compatible native `lrand48()`;
     * the Java LCG below exists solely so the plain-JVM numerical tests remain deterministic.
     */
    interface WorkerRandom {
        /** Returns an unsigned 31-bit rand-style value in [0, 2^31). */
        int nextRand31();
    }

    private static final class JavaWorkerRandom implements WorkerRandom {
        private int state;

        JavaWorkerRandom(int seed) {
            state = seed;
        }

        @Override
        public int nextRand31() {
            state = state * 1103515245 + 12345;
            return state >>> 1;
        }
    }

    private static final class WorkerSnapshot {
        final int mode;
        final float centerX;
        final float bottomY;
        final float previousX;
        final float previousBottomY;
        final float deltaX;
        final float deltaY;
        final float dissipation;

        WorkerSnapshot(int mode, float centerX, float bottomY, float previousX,
                float previousBottomY, float deltaX, float deltaY, float dissipation) {
            this.mode = mode;
            this.centerX = centerX;
            this.bottomY = bottomY;
            this.previousX = previousX;
            this.previousBottomY = previousBottomY;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
            this.dissipation = dissipation;
        }
    }

    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private int fluidWidth = 1;
    private int fluidHeight = 1;
    private int densityWidth = RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH;
    private int densityHeight = RippleInkPortEngine.PORTRAIT_DENSITY_HEIGHT;
    private int densityIndex;
    private float[] flowX = new float[1];
    private float[] flowY = new float[1];
    /** Distinct source/destination storage for the oracle's velocity self-advection. */
    private float[] advectedFlowX = new float[1];
    private float[] advectedFlowY = new float[1];
    private float[][] pressure = {new float[1], new float[1]};
    private float[] divergence = new float[1];
    private byte[] velocityRgba = new byte[4];
    private boolean fingerDown;
    private float currentX;
    private float currentY;
    private float previousX;
    private float previousY;
    private float adjustedPressure = 1.2f;
    private float densityDissipationState = DENSITY_DISSIPATION_PRESS;
    private float backwardStepState = DENSITY_BACKWARD_STEP;
    private final Object inputLock = new Object();
    private InputSample latestInput;
    /** Guards the compare-and-commit of a GL snapshot against a later UI callback. */
    private long inputGeneration;
    private int sourceState;
    private float committedX;
    private float committedY;
    private float densityUpperBound;
    /** State-1 owns its fixed 13-tick recipe; raw motion history must not alter this profile. */
    private boolean pressActive;
    /** Native state_step: incremented once on every state-1/state-2 onDraw, never on callback. */
    private int stateStep;
    private int dragStep;
    /** Profile persisted by N3's native globals between onDraw calls. */
    private Preset persistentProfile = defaultPreset();
    /** isMovingEvent, written only by an already-dragging raw MOVE callback. */
    private int heldMovingMode;
    private float pressCenterX;
    private float pressCenterY;
    private float pressPressure;
    private float pressLastEventX;
    private float pressLastEventY;
    private boolean pressCycleCompleted;
    private boolean inkEnabled = true;
    private int lastInputAction = RippleInkPortEngine.ACTION_UP;
    private long executedSubsteps;
    private int workerRandomSeed;
    private WorkerRandom workerRandom;
    private float lastWorkerJitterX;
    private float lastWorkerJitterY;
    /** Android must not silently fall back to the scalar test worker if JNI cannot be loaded. */
    private final boolean nativeWorkerRequired = isAndroidRuntime();
    private long nativeWorkerHandle;
    private boolean nativeWorkerFailed;
    private String nativeWorkerFailureDetail = "not initialized";

    RippleInkPortFluidPipeline() {
        workerRandomSeed = nextHostWorkerRandomSeed(System.identityHashCode(this));
        workerRandom = new JavaWorkerRandom(workerRandomSeed);
    }

    private static synchronized int nextHostWorkerRandomSeed(int identity) {
        int counter = ++productionWorkerRandomCounter;
        long mixed = System.nanoTime() ^ ((long) identity << 32)
                ^ ((long) counter * 0x9e3779b9L);
        int seed = (int) (mixed ^ (mixed >>> 32));
        seed ^= seed << 13;
        seed ^= seed >>> 17;
        seed ^= seed << 5;
        return seed == 0 ? DEFAULT_WORKER_RANDOM_SEED : seed;
    }

    /**
     * Keep the host test path dependency-free: the actual Android runtime is the only place
     * where the N3 worker is mandatory.  This deliberately does not reference android.* so the
     * plain-JVM numerical harness remains valid.
     */
    private static boolean isAndroidRuntime() {
        String vmName = System.getProperty("java.vm.name", "");
        String runtimeName = System.getProperty("java.runtime.name", "");
        return vmName.toLowerCase().contains("dalvik")
                || runtimeName.toLowerCase().contains("android");
    }

    private void createNativeWorker() {
        nativeWorkerFailed = false;
        nativeWorkerFailureDetail = "not required on host JVM";
        if (!nativeWorkerRequired) {
            return;
        }
        if (!N3RippleInkWorkerNative.isAvailable()) {
            nativeWorkerFailed = true;
            nativeWorkerFailureDetail = "N3 Ripple Ink JNI worker is unavailable";
            return;
        }
        try {
            nativeWorkerHandle = N3RippleInkWorkerNative.nativeCreate(
                    fluidWidth, fluidHeight, viewportWidth, viewportHeight);
            if (nativeWorkerHandle == 0L) {
                nativeWorkerFailed = true;
                nativeWorkerFailureDetail = "N3 Ripple Ink JNI worker create returned zero";
            } else {
                nativeWorkerFailureDetail = "ready";
            }
        } catch (LinkageError error) {
            nativeWorkerFailed = true;
            nativeWorkerFailureDetail = "N3 Ripple Ink JNI worker create linkage failed";
            nativeWorkerHandle = 0L;
        }
    }

    /** Must run on the renderer's GL lifecycle thread; nativeDestroy joins the worker. */
    void releaseNativeWorker() {
        destroyNativeWorker();
    }

    private void destroyNativeWorker() {
        if (nativeWorkerHandle == 0L) {
            return;
        }
        long handle = nativeWorkerHandle;
        nativeWorkerHandle = 0L;
        try {
            N3RippleInkWorkerNative.nativeDestroy(handle);
        } catch (LinkageError error) {
            // The handle cannot be safely reused after a failed destroy. Android remains
            // fail-closed on the next configure/tick rather than using scalar state.
            nativeWorkerFailed = true;
            nativeWorkerFailureDetail = "N3 Ripple Ink JNI worker destroy linkage failed";
        }
    }

    boolean isNativeWorkerReadyForProduction() {
        return !nativeWorkerRequired || (!nativeWorkerFailed && nativeWorkerHandle != 0L);
    }

    String nativeWorkerFailureDetail() {
        return nativeWorkerFailureDetail;
    }

    private byte[] stepNativeWorker(Preset activePreset, float currentX, float currentY,
            float previousX, float previousY) {
        if (!isNativeWorkerReadyForProduction()) {
            throw new IllegalStateException(nativeWorkerFailureDetail);
        }
        try {
            byte[] completed = N3RippleInkWorkerNative.nativeStep(
                    nativeWorkerHandle,
                    activePreset.mode,
                    currentX,
                    viewportHeight - currentY,
                    previousX,
                    viewportHeight - previousY,
                    activePreset.velocityDissipation,
                    activePreset.divergenceRadius,
                    activePreset.divergenceStrength,
                    activePreset.forceProjection);
            if (completed == null || completed.length != velocityRgba.length) {
                nativeWorkerFailed = true;
                nativeWorkerFailureDetail = "N3 Ripple Ink JNI worker returned invalid velocity";
                nativeWorkerHandle = 0L;
                throw new IllegalStateException(nativeWorkerFailureDetail);
            }
            return completed;
        } catch (LinkageError error) {
            nativeWorkerFailed = true;
            nativeWorkerFailureDetail = "N3 Ripple Ink JNI worker step linkage failed";
            nativeWorkerHandle = 0L;
            throw new IllegalStateException(nativeWorkerFailureDetail, error);
        }
    }

    void configure(int width, int height) {
        destroyNativeWorker();
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
        fluidWidth = Math.max(2, viewportWidth / 12);
        fluidHeight = Math.max(2, viewportHeight / 12);
        densityWidth = viewportWidth > viewportHeight
                ? RippleInkPortEngine.LANDSCAPE_DENSITY_WIDTH
                : RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH;
        densityHeight = viewportWidth > viewportHeight
                ? RippleInkPortEngine.LANDSCAPE_DENSITY_HEIGHT
                : RippleInkPortEngine.PORTRAIT_DENSITY_HEIGHT;
        int count = fluidWidth * fluidHeight;
        flowX = new float[count];
        flowY = new float[count];
        advectedFlowX = new float[count];
        advectedFlowY = new float[count];
        pressure = new float[][]{new float[count], new float[count]};
        divergence = new float[count];
        velocityRgba = new byte[count * 4];
        createNativeWorker();
        reset();
    }

    void reset() {
        Arrays.fill(flowX, 0.0f);
        Arrays.fill(flowY, 0.0f);
        Arrays.fill(advectedFlowX, 0.0f);
        Arrays.fill(advectedFlowY, 0.0f);
        Arrays.fill(pressure[0], 0.0f);
        Arrays.fill(pressure[1], 0.0f);
        Arrays.fill(divergence, 0.0f);
        Arrays.fill(velocityRgba, (byte) 0);
        densityIndex = 0;
        fingerDown = false;
        currentX = 0.0f;
        currentY = 0.0f;
        previousX = 0.0f;
        previousY = 0.0f;
        adjustedPressure = 1.2f;
        densityDissipationState = DENSITY_DISSIPATION_PRESS;
        synchronized (inputLock) {
            latestInput = null;
            inputGeneration = 0L;
        }
        sourceState = SOURCE_IDLE;
        pressActive = false;
        stateStep = 0;
        dragStep = 0;
        heldMovingMode = 0;
        persistentProfile = defaultPreset();
        pressCenterX = 0.0f;
        pressCenterY = 0.0f;
        pressPressure = 0.0f;
        pressLastEventX = 0.0f;
        pressLastEventY = 0.0f;
        committedX = 0.0f;
        committedY = 0.0f;
        densityUpperBound = 0.0f;
        pressCycleCompleted = false;
        lastInputAction = RippleInkPortEngine.ACTION_UP;
        executedSubsteps = 0L;
        workerRandom = new JavaWorkerRandom(workerRandomSeed);
        lastWorkerJitterX = 0.0f;
        lastWorkerJitterY = 0.0f;
        if (nativeWorkerHandle != 0L) {
            try {
                N3RippleInkWorkerNative.nativeReset(nativeWorkerHandle);
            } catch (LinkageError error) {
                nativeWorkerFailed = true;
                nativeWorkerFailureDetail = "N3 worker reset linkage failed";
                nativeWorkerHandle = 0L;
            }
        }
    }

    /** UI-thread entry point. It stores exactly one current callback sample; no history exists. */
    void onTouch(int action, float localX, float localY, float pressureValue,
            boolean inkEnabledValue) {
        int nativeAction = action == RippleInkPortEngine.ACTION_CANCEL
                ? RippleInkPortEngine.ACTION_UP : action;
        float x = (int) localX;
        float y = (int) localY;
        float pressure = Math.max(0.0f, Math.min(1.0f, pressureValue));
        synchronized (inputLock) {
            InputSample input = new InputSample(nativeAction, x, y, pressure, inkEnabledValue);
            // The stock JNI call mutates its globals synchronously on every UI callback.  The
            // mailbox records that already-applied state for the GL worker; it must not coalesce
            // DOWN->MOVE transitions that happen before the first frame.
            applyInputLocked(input);
            latestInput = input;
            ++inputGeneration;
        }
    }

    void onTouch(int action, float localX, float localY, float pressureValue) {
        onTouch(action, localX, localY, pressureValue, true);
    }

    /** Compatibility overload: firmware does not pass timestamp data over this boundary. */
    void onTouch(int action, float localX, float localY, float pressureValue, long ignoredEventTimeMs) {
        onTouch(action, localX, localY, pressureValue);
    }

    /** Test-compatibility seam: source timing is deliberately no longer event-time driven. */
    void advanceSourceClock(long ignoredFrameTimeMs) {
    }

    /** No history/debt exists in the N3 mailbox implementation. */
    int rebaseOverdueSourceDebt(long ignoredFrameTimeMs, PassSink ignoredSink) {
        return 0;
    }

    boolean hasOverdueSourceDebtForTest() {
        return false;
    }

    /** Mirrors native clearInkValue(): its next advect encodes zero density everywhere. */
    void clearInkValue() {
        densityDissipationState = DENSITY_DISSIPATION_CLEAR;
        densityUpperBound = 0.0f;
    }

    int execute(float logicalCredits, PassSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("pass sink is required");
        }
        if (!isFinite(logicalCredits) || logicalCredits <= 0.0f) {
            return 0;
        }
        int passes = 0;
        float remaining = logicalCredits;
        // Do not pass through Engine's four-credit adaptive clamp: source debt is deliberately
        // retained and drained one chronological source tick per advection substep.
        while (remaining > 0.0f) {
            float q = Math.min(1.0f, remaining);
            TickSnapshot snapshot = takeTickSnapshot(q);
            passes += executeSubstep(q, snapshot, sink);
            remaining -= q;
        }
        return passes;
    }

    /**
     * Production renderer entry point: one recovered 60 Hz Ink tick.  HFR presentation must use
     * this method repeatedly rather than forwarding a fractional water credit to {@link #execute}.
     */
    int executeFixedTick(PassSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("pass sink is required");
        }
        TickSnapshot snapshot = takeTickSnapshot(1.0f);
        int passes = executeSubstep(1.0f, snapshot, sink);
        return passes;
    }

    private int executeSubstep(float q, TickSnapshot snapshot, PassSink sink) {
        // ENB4 Update precedes the onDraw recipe.  Thus this draw's advection/worker consumes
        // the profile written by the previous draw; the current state recipe is latched only
        // after that work has completed.
        SourceEmission emission = snapshot.emission;
        Preset activePreset = snapshot.persistentProfile;
        Preset nextPreset = snapshot.nextPreset;
        final boolean useNativeWorker = nativeWorkerRequired;
        if (useNativeWorker) {
            // ENB4 Update: join/encode N-1 and launch N before the density FBO passes.  The
            // current worker consumes the persisted profile selected by the preceding onDraw.
            velocityRgba = stepNativeWorker(activePreset, snapshot.currentX, snapshot.currentY,
                    snapshot.previousX, snapshot.previousY);
        } else {
            // Host-only numerical oracle seam. Keep it after GLES passes below so tests retain
            // the historical scalar N-1 ordering without needing a JVM JNI library.
            encodeVelocity();
        }
        sink.uploadVelocity(velocityRgba, fluidWidth, fluidHeight);
        int destination = 1 - densityIndex;
        float dissipation = q == 1.0f
                ? snapshot.densityDissipation
                : RippleInkPortEngine.scaleDissipation(snapshot.densityDissipation, q);
        sink.advectDensity(new AdvectPass(
                densityIndex,
                destination,
                VELOCITY_TIME_STEP * DENSITY_TIME_STEP_SCALE * q / fluidWidth,
                VELOCITY_TIME_STEP * DENSITY_TIME_STEP_SCALE * q / fluidHeight,
                snapshot.backwardStep,
                dissipation,
                viewportWidth,
                viewportHeight,
                snapshot.currentX,
                snapshot.currentY,
                activePreset.mode,
                q));
        densityIndex = destination;
        int passes = 2;

        boolean inkInjected = snapshot.inkInjected;
        if (inkInjected) {
            // FUN_1b128 binds previous=prior drawn point and current=latest point. Keep the
            // recovered old -> new direction; the strict open capsule endpoints depend on it.
            float shaderCurrentX = snapshot.currentX;
            float shaderCurrentY = snapshot.currentY;
            float shaderPreviousX = snapshot.previousX;
            float shaderPreviousY = snapshot.previousY;
            float dx = shaderCurrentX - shaderPreviousX;
            float dy = shaderCurrentY - shaderPreviousY;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length <= 0.0f) {
                length = 1.0f;
            }
            destination = 1 - densityIndex;
            sink.addInk(new AddInkPass(
                    densityIndex,
                    destination,
                    shaderCurrentX,
                    shaderCurrentY,
                    shaderPreviousX,
                    shaderPreviousY,
                    dx / length,
                    dy / length,
                    length,
                    nextPreset.addRadius,
                    // One source emission has mass one. Solver q only scales density advection;
                    // neither the direct capsule nor the fixed-60 worker is q-rescaled.
                    nextPreset.addImpulse,
                    viewportWidth,
                    viewportHeight,
                    nextPreset.mode,
                    q));
            densityIndex = destination;
            ++passes;
        }

        if (!useNativeWorker) {
            advanceVelocity(activePreset, snapshot.currentX, snapshot.currentY,
                    snapshot.previousX, snapshot.previousY);
        }
        ++executedSubsteps;
        return passes;
    }

    private Preset sourcePreset(SourceEmission emission, float densityDissipation) {
        Preset preset = defaultPreset();
        preset.nextDensityDissipation = densityDissipation;
        if (emission == null) {
            return preset;
        }
        if (emission.pressTick >= 0) {
            int tick = emission.pressTick;
            preset.mode = -1;
            preset.addInk = inkEnabled && tick < PRESS_INK_TICK_COUNT;
            preset.addRadius = PRESS_RADIUS_INCREMENT * tick;
            preset.addImpulse = PRESS_IMPULSE;
            preset.velocityDissipation = PRESS_VELOCITY_DISSIPATION;
            preset.forceProjection = true;
            preset.divergenceRadius = PRESS_DIVERGENCE_RADIUS;
            preset.divergenceStrength = tick < 5 ? 12.0f * tick : 0.0f;
            preset.nextDensityDissipation = DENSITY_DISSIPATION_PRESS;
            return preset;
        }
        preset.addInk = inkEnabled;
        if (!emission.tap) {
            float dx = emission.currentX - emission.previousX;
            float dy = emission.currentY - emission.previousY;
            float distanceSquared = dx * dx + dy * dy;
            preset.forceProjection = true;
            if (emission.movingMode == 2) {
                preset.mode = 2;
                // Native mode-2's strict capsule is empty at zero length. State-2 still ticks
                // and advances drag_step in that case; only its density write is skipped.
                preset.addInk = inkEnabled && distanceSquared > 0.0f;
                preset.addRadius = SOURCE_SEGMENT_RADIUS;
                preset.addImpulse = SOURCE_SEGMENT_IMPULSE;
                preset.velocityDissipation = 0.96f;
                preset.divergenceRadius = VELOCITY_DIVERGENCE_RADIUS;
                preset.divergenceStrength = VELOCITY_DIVERGENCE_STRENGTH;
                preset.nextDensityDissipation = DENSITY_DISSIPATION_PRESS;
            } else if (emission.movingMode == 1) {
                preset.mode = 1;
                preset.addRadius = SOURCE_MODE_1_RADIUS;
                preset.addImpulse = SOURCE_MODE_1_IMPULSE;
                preset.velocityDissipation = 0.94f;
                preset.divergenceRadius = SOURCE_MODE_1_DIVERGENCE_RADIUS;
                preset.divergenceStrength = SOURCE_MODE_1_DIVERGENCE_STRENGTH;
                preset.nextDensityDissipation = DENSITY_DISSIPATION_PRESS;
            } else {
                preset.mode = 0;
                preset.addRadius = SOURCE_TAP_RADIUS * nativeAdjustedPressure(emission.pressure);
                preset.addImpulse = SOURCE_TAP_IMPULSE;
                preset.velocityDissipation = 0.80f;
                preset.divergenceRadius = SOURCE_MODE_0_DIVERGENCE_RADIUS;
                preset.divergenceStrength = SOURCE_MODE_0_DIVERGENCE_STRENGTH
                        * nativeAdjustedPressure(emission.pressure);
                preset.nextDensityDissipation = DENSITY_DISSIPATION_MODE_0;
            }
        } else {
            preset.mode = -1;
            preset.addRadius = SOURCE_TAP_RADIUS * nativeAdjustedPressure(emission.pressure);
            preset.addImpulse = SOURCE_TAP_IMPULSE;
            preset.velocityDissipation = 0.80f;
            preset.nextDensityDissipation = DENSITY_DISSIPATION_MODE_0;
        }
        return preset;
    }

    private TickSnapshot takeTickSnapshot(float q) {
        synchronized (inputLock) {
            return takeTickSnapshotLocked(q);
        }
    }

    /** Atomically advances the logical tick, then returns its immutable GL/JNI draw inputs. */
    private TickSnapshot takeTickSnapshotLocked(float q) {
        TickSnapshot snapshot = new TickSnapshot(inputGeneration, sourceState, fingerDown,
                currentX, currentY, previousX, previousY, committedX, committedY,
                adjustedPressure, densityDissipationState, backwardStepState, densityUpperBound,
                pressActive, stateStep, dragStep, pressCenterX, pressCenterY, pressPressure,
                pressCycleCompleted, lastInputAction, heldMovingMode, persistentProfile);
        if (snapshot.pressActive) {
            int tick = snapshot.stateStep;
            snapshot.currentX = snapshot.pressCenterX;
            snapshot.currentY = snapshot.pressCenterY;
            snapshot.previousX = snapshot.pressCenterX;
            snapshot.previousY = snapshot.pressCenterY;
            snapshot.committedX = snapshot.pressCenterX;
            snapshot.committedY = snapshot.pressCenterY;
            snapshot.emission = new SourceEmission(snapshot.pressCenterX, snapshot.pressCenterY,
                    snapshot.pressCenterX, snapshot.pressCenterY, snapshot.pressPressure,
                    false, tick, 0);
        } else if (snapshot.sourceState == SOURCE_DRAGGING) {
            snapshot.previousX = snapshot.committedX;
            snapshot.previousY = snapshot.committedY;
            snapshot.committedX = snapshot.currentX;
            snapshot.committedY = snapshot.currentY;
            snapshot.emission = new SourceEmission(snapshot.previousX, snapshot.previousY,
                    snapshot.currentX, snapshot.currentY, snapshot.adjustedPressure, false, -1,
                    snapshot.heldMovingMode);
        }
        snapshot.nextPreset = snapshot.emission == null ? null
                : sourcePreset(snapshot.emission, snapshot.densityDissipation);
        snapshot.inkInjected = snapshot.nextPreset != null && snapshot.nextPreset.addInk
                && withinInkBounds(snapshot.currentX, snapshot.currentY);
        if (snapshot.emission != null && snapshot.emission.pressTick < 0
                && snapshot.sourceStateAtStart == SOURCE_DRAGGING) {
            ++snapshot.dragStep;
        }
        if (snapshot.emission != null && snapshot.emission.pressTick >= 0) {
            finishPressTick(snapshot, snapshot.emission.pressTick);
        }
        if (snapshot.inkInjected) {
            snapshot.densityUpperBound = 127.0f;
            snapshot.densityDissipation = snapshot.nextPreset.nextDensityDissipation;
        }
        if (snapshot.nextPreset != null) {
            snapshot.persistentProfile = snapshot.nextPreset;
        }
        if (snapshot.sourceStateAtStart == SOURCE_RELEASING && !snapshot.inkInjected) {
            snapshot.densityUpperBound *= q == 1.0f ? DENSITY_DISSIPATION_RELEASE
                    : RippleInkPortEngine.scaleDissipation(DENSITY_DISSIPATION_RELEASE, q);
            if (snapshot.densityUpperBound < TAIL_DENSITY_THRESHOLD) {
                snapshot.sourceState = SOURCE_IDLE;
            }
        }
        if (snapshot.sourceStateAtStart == SOURCE_ARMED) {
            ++snapshot.stateStep;
        }
        applyPreparedTickLocked(snapshot);
        // State-2 writes its one-frame-ahead backstep only after this draw's Update inputs have
        // been snapshotted. The next callback observes the logical completion immediately.
        if (snapshot.emission != null && snapshot.emission.pressTick < 0
                && snapshot.sourceStateAtStart == SOURCE_DRAGGING) {
            backwardStepState = DENSITY_BACKWARD_STEP;
        }
        return snapshot;
    }

    /** Called while inputLock is held, before JNI/GLES can block the UI callback. */
    private void applyPreparedTickLocked(TickSnapshot snapshot) {
        sourceState = snapshot.sourceState;
        fingerDown = snapshot.fingerDown;
        currentX = snapshot.currentX;
        currentY = snapshot.currentY;
        previousX = snapshot.previousX;
        previousY = snapshot.previousY;
        committedX = snapshot.committedX;
        committedY = snapshot.committedY;
        adjustedPressure = snapshot.adjustedPressure;
        densityDissipationState = snapshot.densityDissipation;
        backwardStepState = snapshot.backwardStep;
        densityUpperBound = snapshot.densityUpperBound;
        pressActive = snapshot.pressActive;
        stateStep = snapshot.stateStep;
        dragStep = snapshot.dragStep;
        pressCenterX = snapshot.pressCenterX;
        pressCenterY = snapshot.pressCenterY;
        pressPressure = snapshot.pressPressure;
        pressCycleCompleted = snapshot.pressCycleCompleted;
        lastInputAction = snapshot.lastInputAction;
        heldMovingMode = snapshot.heldMovingMode;
        persistentProfile = snapshot.persistentProfile;
    }

    private void finishPressTick(TickSnapshot snapshot, int tick) {
        // Native state 1 writes the next backstep only after this tick's source pass.
        if (tick < PRESS_INK_TICK_COUNT) {
            snapshot.backwardStep = 0.1f * tick;
        } else if (tick == PRESS_INK_TICK_COUNT) {
            snapshot.backwardStep = DENSITY_BACKWARD_STEP;
        }
        if (tick != PRESS_TICK_COUNT - 1) {
            return;
        }
        // N3 does not restart state-1. Once its counter passes 12, a stationary DOWN/UP enters
        // the release tail. A MOVE keeps state-1 alive without another AddInk until that MOVE
        // promotes it to state-2 in applyLatestInput().
        snapshot.pressCycleCompleted = true;
        if (snapshot.fingerDown && snapshot.lastInputAction == RippleInkPortEngine.ACTION_MOVE) {
            return;
        }
        snapshot.pressActive = false;
        snapshot.pressCenterX = 0.0f;
        snapshot.pressCenterY = 0.0f;
        snapshot.pressPressure = 0.0f;
        snapshot.sourceState = SOURCE_RELEASING;
        snapshot.densityDissipation = DENSITY_DISSIPATION_RELEASE;
    }

    private void clearPressProfile() {
        pressActive = false;
        pressCenterX = 0.0f;
        pressCenterY = 0.0f;
        pressPressure = 0.0f;
        pressLastEventX = 0.0f;
        pressLastEventY = 0.0f;
    }

    private void applyInputLocked(InputSample input) {
        float flippedY = viewportHeight - input.y;
        lastInputAction = input.action;
        inkEnabled = input.inkEnabled;
        if (input.action == RippleInkPortEngine.ACTION_DOWN) {
            adjustedPressure = nativeAdjustedPressure(input.pressure);
            fingerDown = true;
            sourceState = SOURCE_ARMED;
            pressActive = true;
            stateStep = 0;
            dragStep = 0;
            heldMovingMode = 0;
            pressCycleCompleted = false;
            pressCenterX = input.x;
            pressCenterY = flippedY;
            pressPressure = input.pressure;
            pressLastEventX = input.x;
            pressLastEventY = flippedY;
            backwardStepState = DENSITY_BACKWARD_STEP;
            currentX = input.x;
            currentY = flippedY;
            previousX = input.x;
            previousY = flippedY;
            committedX = input.x;
            committedY = flippedY;
            densityDissipationState = DENSITY_DISSIPATION_PRESS;
            return;
        }
        if (pressActive) {
            pressCenterX = input.x;
            pressCenterY = flippedY;
            currentX = input.x;
            currentY = flippedY;
        }
        if (input.action == RippleInkPortEngine.ACTION_UP) {
            fingerDown = false;
            if (!pressActive && sourceState == SOURCE_DRAGGING
                    && stateStep < PRESS_TICK_COUNT - 1 && dragStep < PRESS_INK_TICK_COUNT) {
                // Native's short state-2 UP returns to state-1; it does not immediately fade.
                pressActive = true;
                sourceState = SOURCE_ARMED;
                pressCycleCompleted = false;
                pressCenterX = input.x;
                pressCenterY = flippedY;
                pressPressure = input.pressure;
                pressLastEventX = input.x;
                pressLastEventY = flippedY;
            } else if (!pressActive) {
                sourceState = SOURCE_RELEASING;
                densityDissipationState = DENSITY_DISSIPATION_RELEASE;
            }
            return;
        }
        if (input.action != RippleInkPortEngine.ACTION_MOVE || !fingerDown) {
            return;
        }
        adjustedPressure = nativeAdjustedPressure(input.pressure);
        if (pressActive) {
            float dx = input.x - pressLastEventX;
            float dy = flippedY - pressLastEventY;
            pressLastEventX = input.x;
            pressLastEventY = flippedY;
            if (!pressCycleCompleted && stateStep < 12
                    && dx * dx + dy * dy <= PRESS_MOVE_DISTANCE_SQUARED) {
                return;
            }
            // Promotion itself writes neither isMovingEvent nor backwardStep. The first state-2
            // Update therefore consumes the value left by the final state-1 recipe.
            clearPressProfile();
            sourceState = SOURCE_DRAGGING;
            heldMovingMode = 0;
            previousX = input.x;
            previousY = flippedY;
            currentX = input.x;
            currentY = flippedY;
            committedX = input.x;
            committedY = flippedY;
            return;
        }
        if (sourceState == SOURCE_ARMED || sourceState == SOURCE_IDLE) {
            sourceState = SOURCE_DRAGGING;
            previousX = committedX;
            previousY = committedY;
        }
        float dx = input.x - currentX;
        float dy = flippedY - currentY;
        float distanceSquared = dx * dx + dy * dy;
        heldMovingMode = distanceSquared > 100.0f ? 2
                : distanceSquared > PRESS_MOVE_DISTANCE_SQUARED ? 1 : 0;
        previousX = currentX;
        previousY = currentY;
        currentX = input.x;
        currentY = flippedY;
    }

    /** True while the producer needs a final commit or the retained field remains visible. */
    boolean hasVisibleTail() {
        return sourceState == SOURCE_ARMED || sourceState == SOURCE_DRAGGING
                || sourceState == SOURCE_RELEASE_PENDING
                || densityUpperBound >= TAIL_DENSITY_THRESHOLD;
    }

    float densityUpperBound() {
        return densityUpperBound;
    }

    /**
     * Fixed-60 scalar translation of FUN_00016dd8 followed by FUN_00018520 and the recovered
     * pressure/projection worker. FUN_00019530's direct velocity capsule runs first for steady
     * mode2, using its own source/destination swap rather than mutating the source in place.
     */
    private void advanceVelocity(Preset preset) {
        advanceVelocity(preset, currentX, currentY, previousX, previousY);
    }

    private void advanceVelocity(Preset preset, float currentX, float currentY,
            float previousX, float previousY) {
        WorkerSnapshot snapshot = captureWorkerSnapshot(preset, currentX, currentY,
                previousX, previousY);
        addSegmentVelocity(snapshot);
        selfAdvectVelocity(snapshot);

        // State-1 has its own divergence profile, but it shares the native strict margin gate
        // and two rand draws with state-2.  Only direct capsule injection is mode2-exclusive.
        if (snapshot.mode != 2 && !preset.forceProjection) {
            return;
        }
        if (!isWorkerWithinMargin(snapshot)) {
            return;
        }
        lastWorkerJitterX = jitterFromRand();
        lastWorkerJitterY = jitterFromRand();
        float jitteredX = snapshot.centerX + lastWorkerJitterX;
        float jitteredY = snapshot.bottomY + lastWorkerJitterY;
        projectVelocity(jitteredX, jitteredY, preset.divergenceRadius,
                preset.divergenceStrength);
    }

    /**
     * FUN_00019530.  This is a direct velocity capsule, distinct from density AddInk: it copies
     * the old field into the destination, adds the recovered open-endpoint force where eligible,
     * then swaps so FUN_00016dd8 reads the result as its source.
     */
    private void addSegmentVelocity(WorkerSnapshot snapshot) {
        if (!isWorkerMoveWithinMargin(snapshot)) {
            return;
        }
        float segmentX = snapshot.centerX - snapshot.previousX;
        float segmentY = snapshot.bottomY - snapshot.previousBottomY;
        float length = (float) Math.sqrt(segmentX * segmentX + segmentY * segmentY);
        if (length <= 0.0f) {
            return;
        }
        float normalX = segmentX / length;
        float normalY = segmentY / length;
        for (int y = 0; y < fluidHeight; ++y) {
            float cellY = (y + 0.5f) * viewportHeight / (float) fluidHeight;
            for (int x = 0; x < fluidWidth; ++x) {
                int index = y * fluidWidth + x;
                float sourceX = finiteOrZero(flowX[index]);
                float sourceY = finiteOrZero(flowY[index]);
                float cellX = (x + 0.5f) * viewportWidth / (float) fluidWidth;
                float relativeX = cellX - snapshot.previousX;
                float relativeY = cellY - snapshot.previousBottomY;
                float along = normalX * relativeX + normalY * relativeY;
                float outputX = sourceX;
                float outputY = sourceY;
                if (along > 0.0f && along < length) {
                    float projectedX = snapshot.previousX + along * normalX;
                    float projectedY = snapshot.previousBottomY + along * normalY;
                    float radialX = cellX - projectedX;
                    float radialY = cellY - projectedY;
                    float distance = (float) Math.sqrt(radialX * radialX + radialY * radialY);
                    if (distance < SOURCE_SEGMENT_RADIUS) {
                        outputX += distance * 0.1f
                                * (radialX + snapshot.centerX - projectedX);
                        outputY += distance * 0.1f
                                * (radialY + snapshot.bottomY - projectedY);
                    }
                }
                advectedFlowX[index] = finiteOrZero(outputX);
                advectedFlowY[index] = finiteOrZero(outputY);
            }
        }
        swapVelocityBuffers();
    }

    /**
     * FUN_00016dd8: sample the source velocity at a backtraced cell centre using the oracle's
     * normalized texture coordinates, apply its strict 25 px local override, then damp once per
     * fixed stock tick.  Scratch becomes the new source before divergence/projection.
     */
    private void selfAdvectVelocity(WorkerSnapshot snapshot) {
        float inverseWidth = 1.0f / fluidWidth;
        float inverseHeight = 1.0f / fluidHeight;
        for (int y = 0; y < fluidHeight; ++y) {
            float cellCenterY = y + 0.5f;
            float anchorY = y * viewportHeight / (float) fluidHeight;
            for (int x = 0; x < fluidWidth; ++x) {
                int index = y * fluidWidth + x;
                float sourceX = finiteOrZero(flowX[index]);
                float sourceY = finiteOrZero(flowY[index]);
                float u = ((x + 0.5f) - VELOCITY_SELF_ADVECT_STEP * sourceX) * inverseWidth;
                float v = (cellCenterY - VELOCITY_SELF_ADVECT_STEP * sourceY) * inverseHeight;
                float sampledX = sampleVelocityBilinear(flowX, u, v);
                float sampledY = sampleVelocityBilinear(flowY, u, v);

                if (snapshot.mode > 0) {
                    float anchorX = x * viewportWidth / (float) fluidWidth;
                    float distanceX = anchorX - snapshot.centerX;
                    float distanceY = anchorY - snapshot.bottomY;
                    if (distanceX * distanceX + distanceY * distanceY
                            < VELOCITY_LOCAL_RADIUS_SQUARED) {
                        float multiplier = snapshot.mode - VELOCITY_LOCAL_MODE_BIAS;
                        sampledX += multiplier * snapshot.deltaX;
                        sampledY += multiplier * snapshot.deltaY;
                    }
                }
                advectedFlowX[index] = finiteOrZero(sampledX * snapshot.dissipation);
                advectedFlowY[index] = finiteOrZero(sampledY * snapshot.dissipation);
            }
        }

        swapVelocityBuffers();
    }

    private void swapVelocityBuffers() {
        float[] oldFlowX = flowX;
        float[] oldFlowY = flowY;
        flowX = advectedFlowX;
        flowY = advectedFlowY;
        advectedFlowX = oldFlowX;
        advectedFlowY = oldFlowY;
    }

    /** Oracle normalized bilerp: clamp u/v, map to [0, size-1], then interpolate four corners. */
    private float sampleVelocityBilinear(float[] field, float u, float v) {
        float sampleX = clamp(finiteOrZero(u), 0.0f, 1.0f) * (fluidWidth - 1);
        float sampleY = clamp(finiteOrZero(v), 0.0f, 1.0f) * (fluidHeight - 1);
        int x0 = (int) sampleX;
        int y0 = (int) sampleY;
        int x1 = Math.min(fluidWidth - 1, x0 + 1);
        int y1 = Math.min(fluidHeight - 1, y0 + 1);
        float fractionX = sampleX - x0;
        float fractionY = sampleY - y0;
        float lower = finiteOrZero(field[y0 * fluidWidth + x0])
                + (finiteOrZero(field[y0 * fluidWidth + x1])
                - finiteOrZero(field[y0 * fluidWidth + x0])) * fractionX;
        float upper = finiteOrZero(field[y1 * fluidWidth + x0])
                + (finiteOrZero(field[y1 * fluidWidth + x1])
                - finiteOrZero(field[y1 * fluidWidth + x0])) * fractionX;
        return finiteOrZero(lower + (upper - lower) * fractionY);
    }

    private WorkerSnapshot captureWorkerSnapshot(Preset preset) {
        return captureWorkerSnapshot(preset, currentX, currentY, previousX, previousY);
    }

    private WorkerSnapshot captureWorkerSnapshot(Preset preset, float currentX, float currentY,
            float previousX, float previousY) {
        // Input is stored bottom-origin for GLES.  Spell out the recovered top-origin conversion
        // so delta=(currentX-prevEventX, prevEventYTop-currentYTop) remains auditable.
        float currentYTop = viewportHeight - currentY;
        float previousYTop = viewportHeight - previousY;
        return new WorkerSnapshot(
                preset.mode,
                currentX,
                viewportHeight - currentYTop,
                previousX,
                viewportHeight - previousYTop,
                currentX - previousX,
                previousYTop - currentYTop,
                preset.velocityDissipation);
    }

    private float jitterFromRand() {
        int rand31 = workerRandom.nextRand31() & 0x7fffffff;
        return (0.5f - rand31 * RAND_31_INVERSE) * 10.0f;
    }

    private boolean isWorkerMoveWithinMargin(WorkerSnapshot snapshot) {
        return snapshot.mode == 2 && isWorkerWithinMargin(snapshot);
    }

    private boolean isWorkerWithinMargin(WorkerSnapshot snapshot) {
        return snapshot.centerX > VELOCITY_WORKER_MARGIN
                && snapshot.centerX < viewportWidth - VELOCITY_WORKER_MARGIN
                && snapshot.bottomY > VELOCITY_WORKER_MARGIN
                && snapshot.bottomY < viewportHeight - VELOCITY_WORKER_MARGIN;
    }

    private void projectVelocity(float impulseX, float impulseY, float divergenceRadius,
            float divergenceStrength) {
        Arrays.fill(pressure[0], 0.0f);
        Arrays.fill(pressure[1], 0.0f);
        Arrays.fill(divergence, 0.0f);
        for (int y = 0; y < fluidHeight; ++y) {
            float screenY = y * viewportHeight / (float) fluidHeight;
            for (int x = 0; x < fluidWidth; ++x) {
                int index = y * fluidWidth + x;
                divergence[index] = finiteOrZero(DIVERGENCE_SCALE * (
                        sampleCell(flowX, x + 1, y) - sampleCell(flowX, x - 1, y)
                                + sampleCell(flowY, x, y + 1) - sampleCell(flowY, x, y - 1)));
                float screenX = x * viewportWidth / (float) fluidWidth;
                float dx = screenX - impulseX;
                float dy = screenY - impulseY;
                if (dx * dx + dy * dy < divergenceRadius * divergenceRadius) {
                    divergence[index] -= divergenceStrength;
                }
            }
        }

        int pressureIndex = 0;
        for (int iteration = 0; iteration < JACOBI_ITERATIONS; ++iteration) {
            int destination = 1 - pressureIndex;
            float[] source = pressure[pressureIndex];
            float[] target = pressure[destination];
            for (int y = 0; y < fluidHeight; ++y) {
                for (int x = 0; x < fluidWidth; ++x) {
                    int index = y * fluidWidth + x;
                    target[index] = finiteOrZero(JACOBI_INVERSE_BETA * (
                            sampleCell(source, x - 1, y) + sampleCell(source, x + 1, y)
                                    + sampleCell(source, x, y - 1) + sampleCell(source, x, y + 1)
                                    + JACOBI_ALPHA * divergence[index]));
                }
            }
            pressureIndex = destination;
        }

        float[] projectedPressure = pressure[pressureIndex];
        for (int y = 0; y < fluidHeight; ++y) {
            for (int x = 0; x < fluidWidth; ++x) {
                int index = y * fluidWidth + x;
                flowX[index] = finiteOrZero(flowX[index] - DIVERGENCE_SCALE
                        * (sampleCell(projectedPressure, x + 1, y)
                                - sampleCell(projectedPressure, x - 1, y)));
                flowY[index] = finiteOrZero(flowY[index] - DIVERGENCE_SCALE
                        * (sampleCell(projectedPressure, x, y + 1)
                                - sampleCell(projectedPressure, x, y - 1)));
            }
        }
    }

    private boolean withinInkBounds(float x, float y) {
        return x > 10.0f && y > 10.0f
                && x < viewportWidth - 10.0f
                && y < viewportHeight - 10.0f;
    }

    private float sampleCell(float[] field, int x, int y) {
        int clampedX = Math.max(0, Math.min(fluidWidth - 1, x));
        int clampedY = Math.max(0, Math.min(fluidHeight - 1, y));
        return finiteOrZero(field[clampedY * fluidWidth + clampedX]);
    }

    private void encodeVelocity() {
        for (int i = 0; i < flowX.length; ++i) {
            int output = i * 4;
            encodeComponent(flowX[i], velocityRgba, output);
            encodeComponent(flowY[i], velocityRgba, output + 2);
        }
    }

    static void encodeComponent(float value, byte[] output, int offset) {
        float biased = sanitizeVelocity(value) + VELOCITY_ENCODE_BIAS;
        int whole = (int) biased;
        int fraction = (int) ((biased - whole) * 255.0f);
        output[offset] = (byte) whole;
        output[offset + 1] = (byte) fraction;
    }

    int currentDensityIndex() {
        return densityIndex;
    }

    int fluidWidth() {
        return fluidWidth;
    }

    int fluidHeight() {
        return fluidHeight;
    }

    int densityWidth() {
        return densityWidth;
    }

    int densityHeight() {
        return densityHeight;
    }

    long executedSubsteps() {
        return executedSubsteps;
    }

    float velocityChecksum() {
        float sum = 0.0f;
        for (int i = 0; i < flowX.length; ++i) {
            sum += Math.abs(flowX[i]) + Math.abs(flowY[i]);
        }
        return sum;
    }

    boolean hasFiniteRepresentableVelocity() {
        for (int index = 0; index < flowX.length; ++index) {
            if (!isFinite(flowX[index]) || !isFinite(flowY[index])
                    || flowX[index] < VELOCITY_ENCODE_MIN
                    || flowX[index] > VELOCITY_ENCODE_MAX
                    || flowY[index] < VELOCITY_ENCODE_MIN
                    || flowY[index] > VELOCITY_ENCODE_MAX) {
                return false;
            }
        }
        return true;
    }

    // Narrow package test seam for numerical worker discriminators.  Production never invokes it.
    void setWorkerRandomForTest(WorkerRandom random) {
        if (random == null) {
            throw new IllegalArgumentException("worker random is required");
        }
        workerRandom = random;
    }

    void setWorkerRandomSeedForTest(int seed) {
        workerRandomSeed = seed;
        workerRandom = new JavaWorkerRandom(seed);
    }

    void setVelocityCellForTest(int x, int y, float velocityX, float velocityY) {
        int index = checkedFluidIndex(x, y);
        flowX[index] = velocityX;
        flowY[index] = velocityY;
    }

    float velocityXForTest(int x, int y) {
        return flowX[checkedFluidIndex(x, y)];
    }

    float velocityYForTest(int x, int y) {
        return flowY[checkedFluidIndex(x, y)];
    }

    float divergenceForTest(int x, int y) {
        return divergence[checkedFluidIndex(x, y)];
    }

    float lastWorkerJitterXForTest() {
        return lastWorkerJitterX;
    }

    float lastWorkerJitterYForTest() {
        return lastWorkerJitterY;
    }

    /** Inputs use MotionEvent's top-origin convention, matching the recovered worker snapshot. */
    void runWorkerForTest(int mode, float currentEventX, float currentEventYTop,
            float previousEventX, float previousEventYTop) {
        currentX = currentEventX;
        currentY = viewportHeight - currentEventYTop;
        previousX = previousEventX;
        previousY = viewportHeight - previousEventYTop;
        Preset preset = new Preset();
        preset.mode = mode;
        preset.velocityDissipation = mode == 2 ? 0.96f : 0.80f;
        advanceVelocity(preset);
    }

    /** Isolates FUN_00019530 so its direct field can be asserted before FUN_00016dd8. */
    void runDirectVelocityCapsuleForTest(float currentEventX, float currentEventYTop,
            float previousEventX, float previousEventYTop) {
        currentX = currentEventX;
        currentY = viewportHeight - currentEventYTop;
        previousX = previousEventX;
        previousY = viewportHeight - previousEventYTop;
        Preset preset = new Preset();
        preset.mode = 2;
        preset.velocityDissipation = 0.96f;
        addSegmentVelocity(captureWorkerSnapshot(preset));
    }

    private int checkedFluidIndex(int x, int y) {
        if (x < 0 || x >= fluidWidth || y < 0 || y >= fluidHeight) {
            throw new IllegalArgumentException("fluid coordinate outside worker grid");
        }
        return y * fluidWidth + x;
    }

    private static float nativeAdjustedPressure(float pressureValue) {
        if (!isFinite(pressureValue) || pressureValue <= 0.0f) {
            return 0.0f;
        }
        return finiteOrZero(0.2f + pressureValue * pressureValue);
    }

    private static float sanitizeVelocity(float value) {
        return clamp(finiteOrZero(value), VELOCITY_ENCODE_MIN, VELOCITY_ENCODE_MAX);
    }

    private static float finiteOrZero(float value) {
        return isFinite(value) ? value : 0.0f;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
