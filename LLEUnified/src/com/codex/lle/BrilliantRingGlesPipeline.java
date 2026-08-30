package com.codex.lle;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * GL-thread-only reconstruction of Samsung's {@code libsecveBrilliantRing} scene.
 *
 * <p>The CPU fields, three GLES passes and shader arithmetic intentionally follow the ARM32
 * implementation. Android touch coordinates use pixels with an upper-left origin. No method is
 * synchronized: the owning {@code GLSurfaceView} must marshal every call onto its GL thread.</p>
 */
public final class BrilliantRingGlesPipeline {
    private static final String TAG = "LLEBrilliantRingGL";

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;

    // Native-refresh timing is deliberately expressed in the stock renderer's 60 Hz
    // logical updates. The legacy boolean renderFrame path below remains untouched.
    static final long STOCK_SIMULATION_TICK_NS = 16_666_667L;
    static final long ADAPTIVE_STALL_NS = 66_666_667L;
    static final float NOISE_CYCLE_CREDITS = 20.0f;
    private static final int LONG_AXIS_CELLS = 128;
    private static final int ADVECT_SCALE = 5;
    private static final int MAX_ACTIVE_RECORDS = 7;
    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_UNLOCK = 1;
    private static final int TYPE_FORCED_FADE = 2;
    private static final int NORMAL_INITIAL_AGE = 12;
    private static final int NORMAL_REMOVAL_AGE = 66;
    private static final int UNLOCK_REMOVAL_AGE = 56;
    private static final float NORMAL_INNER_DELAY = 10.666667f;
    private static final float NORMAL_OPACITY_HOLD = 40.0f;
    private static final float NORMAL_DURATION = 66.666664f;
    private static final float NORMAL_RADIUS = 25.0f;
    private static final float NORMAL_FORCED_FADE_STEP = 0.05f;
    private static final float NORMAL_EMIT_DISTANCE_CELLS = 19.0f;
    private static final int NORMAL_EMIT_TIMEOUT_UPDATES = 61;
    private static final float UNLOCK_EARLY_LIMIT = 16.5f;
    private static final float RADIAL_QUAD_SCALE = 2.5f;
    private static final float TAB_SCALE = 0.95f;
    private static final float TAB_SHIFT_RANGE = 0.050000012f;

    private static final float[] ADVECT_WEIGHT = {
            0.0f, 0.5f, 0.5f, 0.4f, 0.4f, 0.3f, 0.2f, 0.1f
    };
    private static final float[] ADVECT_OFFSET = {
            0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f
    };
    private static final float[] IDENTITY_MATRIX = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };

    private final ArrayList<Record> records = new ArrayList<Record>(10);
    private final ArrayList<InputEvent> pendingInput = new ArrayList<InputEvent>(8);
    private final FloatBuffer fullscreenPositions = directFloats(new float[] {
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
    });
    private final FloatBuffer androidBackgroundUv = directFloats(new float[] {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
    });
    private final FloatBuffer tabScaledUv = directFloats(12);
    private final FloatBuffer radialPositions = directFloats(12);
    private final FloatBuffer radialLocalUv = directFloats(new float[] {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    });
    private final FloatBuffer radialScreenUv = directFloats(12);
    private final FloatBuffer radialPatternUv = directFloats(12);

    private Program radialProgram;
    private Program advectProgram;
    private Program ringProgram;
    private RenderTarget radialTarget;
    private RenderTarget advectTarget;
    private int alphaTexture;
    private int blurTexture;
    private int backgroundTexture;
    private int patternTexture;
    private int width;
    private int height;
    private int simWidth;
    private int simHeight;
    private float screenPixelsPerCell;
    private float[] narrowField;
    private float[] wideField;
    private float[] noiseCurrent;
    private float[] noiseFrom;
    private float[] noiseTarget;
    private ByteBuffer alphaBytes;
    private ByteBuffer blurBytes;
    private boolean initialized;
    private boolean backgroundReady;
    private boolean patternReady;
    private boolean abandoned;
    private boolean clearIntermediate = true;
    private boolean radialBlendPrimed;
    private boolean unlockActive;
    private boolean hasLastEmission;
    private float lastTouchX;
    private float lastTouchY;
    private float lastEmissionX;
    private float lastEmissionY;
    private int updatesSinceEmission;
    private float updatesSinceEmissionCredits;
    private static final Object NOISE_PHASE_LOCK = new Object();
    private static final AdaptiveNoisePhase adaptiveNoisePhase = new AdaptiveNoisePhase();
    private final AdaptiveStepMode adaptiveStepMode = new AdaptiveStepMode();
    // SrkCommon's process-global phase starts at -1, survives reset and scene recreation.
    private static int noiseCounter = -1;
    // BOB4 imports rand/srand from API-21 Bionic. There rand() delegates to the default
    // 128-byte BSD random() generator (TYPE_3: x^31+x^3+1), not the old ANSI LCG.
    private static final int[] bionicRandState = new int[31];
    private static int bionicRandFront;
    private static int bionicRandRear;
    private float tabOffsetX;
    private float tabOffsetY;

    public BrilliantRingGlesPipeline() {
        bionicSrand((int) (System.currentTimeMillis() / 1000L));
    }

    /** Creates all context-owned resources and uploads the stock DiamondPT bitmap. */
    public void initialize(int surfaceWidth, int surfaceHeight, Bitmap diamond) {
        // Implemented below with the simulation and render passes.
        initializeResources(surfaceWidth, surfaceHeight, diamond);
    }

    public boolean uploadBackground(Bitmap bitmap) {
        if (!initialized || bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        drainErrors();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        int error = GLES20.glGetError();
        backgroundReady = error == GLES20.GL_NO_ERROR;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        if (!backgroundReady) {
            Log.e(TAG, "background upload failed: 0x" + Integer.toHexString(error));
        }
        return backgroundReady;
    }

    public void touch(int action, float x, float y) {
        if (!initialized || abandoned || unlockActive) {
            return;
        }
        pendingInput.add(new InputEvent(action, x, y));
    }

    public void realign(float x, float y) {
        lastTouchX = x;
        lastTouchY = y;
        lastEmissionX = x;
        lastEmissionY = y;
        hasLastEmission = true;
        updatesSinceEmission = 0;
        updatesSinceEmissionCredits = 0.0f;
    }

    public void unlock() {
        if (initialized && !abandoned) {
            // Samsung consumes the queued touch packet before handling the subsequent 0x5b
            // unlock key. Keep that ordering so the type-1 radial starts at the final touch.
            processInput();
            unlockActive = true;
            addRecord(lastTouchX, lastTouchY, 0, TYPE_UNLOCK);
        }
    }

    /**
     * Adaptive counterpart to {@link #unlock()}. It preserves the stock ordering of the
     * terminal touch before its type-1 record, but does not manufacture one 60 Hz update.
     */
    public void unlockAdaptive() {
        if (initialized && !abandoned) {
            processInputAdaptive(0.0f);
            unlockActive = true;
            addRecord(lastTouchX, lastTouchY, 0, TYPE_UNLOCK);
        }
    }

    public void affordance(float x, float y) {
        if (initialized && !abandoned) {
            // Stock BrilliantRingEffect$1 forwards the hint as a real ACTION_DOWN.
            pendingInput.add(new InputEvent(ACTION_DOWN, x, y));
        }
    }

    public void reset() {
        records.clear();
        pendingInput.clear();
        unlockActive = false;
        hasLastEmission = false;
        updatesSinceEmission = 0;
        updatesSinceEmissionCredits = 0.0f;
        adaptiveStepMode.reset();
        tabOffsetX = 0.0f;
        tabOffsetY = 0.0f;
        clearIntermediate = true;
        seedNoiseCurrent();
    }

    public boolean updateAndRender() {
        return updateAndRender(true);
    }

    /**
     * Advances Samsung's frame-stepped simulation only when requested by the host.
     * High-refresh displays may still redraw an unchanged frame between stock 60 Hz updates.
     */
    public boolean updateAndRender(boolean advanceSimulation) {
        if (!initialized || abandoned) {
            return false;
        }
        if (advanceSimulation) {
            processInput();
            if (!records.isEmpty()) {
                updateSimulation();
                uploadCpuFields();
            }
        }
        renderPasses();
        return !records.isEmpty() || !pendingInput.isEmpty();
    }

    /**
     * Advances by a measured number of stock-60-Hz logical credits.
     *
     * <p>Exactly one credit is routed through the original integer implementation. Besides
     * keeping a real 60 Hz cadence bit-for-bit on the stock equations, that gives tests a
     * direct equivalence seam. Fractional credits are only used by the opt-in native-refresh
     * host.</p>
     */
    public boolean updateAndRender(float logicalCredits) {
        if (!initialized || abandoned) {
            return false;
        }
        if (isAdaptiveZeroCreditFrame(logicalCredits)) {
            // A first/native-stall display frame still accepts queued touch packets and draws
            // their current geometry. It advances neither logical age nor the shared RNG.
            processInputAdaptive(0.0f);
            if (!records.isEmpty()) {
                updateSimulationAdaptive(0.0f);
                uploadCpuFields();
            }
        } else if (logicalCredits > 0.0f && !Float.isInfinite(logicalCredits)) {
            boolean useStockStep = adaptiveStepMode.usesStockStep(logicalCredits);
            boolean enteringFractionalStep = adaptiveStepMode.record(logicalCredits);
            if (useStockStep) {
                processInput();
                updatesSinceEmissionCredits = updatesSinceEmission;
                if (!records.isEmpty()) {
                    updateSimulation();
                    captureLegacyFrameForAdaptiveHandoff();
                    syncAdaptiveNoisePhaseFromStock();
                    uploadCpuFields();
                }
            } else {
                if (enteringFractionalStep) {
                    synchronizeRecordsForFirstFractionalStep(logicalCredits);
                }
                processInputAdaptive(logicalCredits);
                if (!records.isEmpty()) {
                    updateSimulationAdaptive(logicalCredits);
                    uploadCpuFields();
                }
            }
        }
        renderPasses();
        return !records.isEmpty() || !pendingInput.isEmpty();
    }

    /** Aligns post-increment integer records to the last stock geometry before fractional draw. */
    private void synchronizeRecordsForFirstFractionalStep(float logicalCredits) {
        for (int i = 0; i < records.size(); ++i) {
            Record record = records.get(i);
            if (record.hasRenderedGeometry) {
                record.adaptiveAge = firstAdaptiveAgeAfterLegacyRender(
                        record.lastRenderedAge, logicalCredits);
            }
        }
    }

    /**
     * The legacy simulation deliberately remains untouched. This sidecar snapshot is populated
     * only when the opt-in float API selected one exact stock step, so a later 60→120 transition
     * can start from the age that was actually drawn rather than the stored post-increment age.
     */
    private void captureLegacyFrameForAdaptiveHandoff() {
        for (int i = 0; i < records.size(); ++i) {
            Record record = records.get(i);
            record.lastRenderedAge = record.age - 1.0f;
            record.hasRenderedGeometry = true;
            record.adaptiveAge = record.age;
        }
    }

    private static void syncAdaptiveNoisePhaseFromStock() {
        synchronized (NOISE_PHASE_LOCK) {
            adaptiveNoisePhase.syncStockCounter(noiseCounter);
        }
    }

    /** Host-facing name used by the GLSurfaceView renderer. */
    public boolean renderFrame() {
        return updateAndRender();
    }

    /** Host-facing redraw with an independently gated stock simulation tick. */
    public boolean renderFrame(boolean advanceSimulation) {
        return updateAndRender(advanceSimulation);
    }

    /** Host-facing redraw with a measured native-refresh logical delta. */
    public boolean renderFrame(float logicalCredits) {
        return updateAndRender(logicalCredits);
    }

    public boolean isIdle() {
        return records.isEmpty() && pendingInput.isEmpty();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean hasBackground() {
        return backgroundReady;
    }

    public boolean hasPattern() {
        return patternReady;
    }

    public void abandon() {
        abandoned = true;
        initialized = false;
        backgroundReady = false;
        patternReady = false;
        // The EGL context is already gone: forget its integer names without issuing GL deletes.
        radialProgram = null;
        advectProgram = null;
        ringProgram = null;
        radialTarget = null;
        advectTarget = null;
        alphaTexture = 0;
        blurTexture = 0;
        backgroundTexture = 0;
        patternTexture = 0;
        records.clear();
        pendingInput.clear();
    }

    public void release() {
        if (radialProgram != null) {
            radialProgram.release();
            radialProgram = null;
        }
        if (advectProgram != null) {
            advectProgram.release();
            advectProgram = null;
        }
        if (ringProgram != null) {
            ringProgram.release();
            ringProgram = null;
        }
        if (radialTarget != null) {
            radialTarget.release();
            radialTarget = null;
        }
        if (advectTarget != null) {
            advectTarget.release();
            advectTarget = null;
        }
        deleteTexture(alphaTexture);
        deleteTexture(blurTexture);
        deleteTexture(backgroundTexture);
        deleteTexture(patternTexture);
        alphaTexture = 0;
        blurTexture = 0;
        backgroundTexture = 0;
        patternTexture = 0;
        initialized = false;
        backgroundReady = false;
        patternReady = false;
        records.clear();
        pendingInput.clear();
    }

    private void initializeResources(int surfaceWidth, int surfaceHeight, Bitmap diamond) {
        release();
        abandoned = false;
        width = Math.max(1, surfaceWidth);
        height = Math.max(1, surfaceHeight);
        if (width <= height) {
            simHeight = LONG_AXIS_CELLS;
            simWidth = Math.max(1, (int) ((width / (float) height) * LONG_AXIS_CELLS));
            screenPixelsPerCell = width / (float) simWidth;
        } else {
            simWidth = LONG_AXIS_CELLS;
            simHeight = Math.max(1, (int) ((height / (float) width) * LONG_AXIS_CELLS));
            screenPixelsPerCell = width / (float) LONG_AXIS_CELLS;
        }
        int cellCount = simWidth * simHeight;
        narrowField = new float[cellCount];
        wideField = new float[cellCount];
        noiseCurrent = new float[cellCount];
        noiseFrom = new float[cellCount];
        noiseTarget = new float[cellCount];
        alphaBytes = ByteBuffer.allocateDirect(cellCount).order(ByteOrder.nativeOrder());
        blurBytes = ByteBuffer.allocateDirect(cellCount).order(ByteOrder.nativeOrder());

        radialProgram = new Program(RADIAL_VERTEX_SHADER, RADIAL_FRAGMENT_SHADER);
        advectProgram = new Program(ADVECT_VERTEX_SHADER, ADVECT_FRAGMENT_SHADER);
        ringProgram = new Program(RING_VERTEX_SHADER, overlayRingFragmentShader());
        radialTarget = new RenderTarget(width, height);
        advectTarget = new RenderTarget(simWidth * ADVECT_SCALE, simHeight * ADVECT_SCALE);
        alphaTexture = createLuminanceTexture(simWidth, simHeight, false);
        blurTexture = createLuminanceTexture(simWidth, simHeight, true);
        backgroundTexture = createRgbaTexture(1, 1, true, true);
        patternTexture = createRgbaTexture(1, 1, true, true);
        backgroundReady = false;
        patternReady = false;
        radialBlendPrimed = false;
        if (diamond != null && !diamond.isRecycled()) {
            drainErrors();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, patternTexture);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, diamond, 0);
            patternReady = GLES20.glGetError() == GLES20.GL_NO_ERROR;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        if (!patternReady) {
            release();
            throw new IllegalStateException("DiamondPT texture upload failed");
        }
        initialized = true;
        reset();
        clearRenderTarget(radialTarget, 0.5f, 0.5f, 0.0f, 0.0f);
        clearRenderTarget(advectTarget, 0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        checkGl("initialize");
    }

    private void processInput() {
        if (pendingInput.isEmpty()) {
            updatesSinceEmission++;
            return;
        }
        for (int i = 0; i < pendingInput.size(); ++i) {
            InputEvent event = pendingInput.get(i);
            float x = event.x;
            float y = event.y;
            if (event.action == ACTION_DOWN) {
                lastTouchX = x;
                lastTouchY = y;
                if (records.isEmpty()) {
                    tabOffsetX = ((x - width * 0.5f) / (width * 0.5f)) * TAB_SHIFT_RANGE;
                    tabOffsetY = ((height * 0.5f - y) / (height * 0.5f)) * TAB_SHIFT_RANGE;
                }
                addRecord(x, y, NORMAL_INITIAL_AGE, TYPE_NORMAL);
                lastEmissionX = x;
                lastEmissionY = y;
                hasLastEmission = true;
                updatesSinceEmission = 0;
            } else if (event.action == ACTION_MOVE) {
                lastTouchX = x;
                lastTouchY = y;
                if (!hasLastEmission) {
                    lastEmissionX = x;
                    lastEmissionY = y;
                    hasLastEmission = true;
                }
                float dx = x - lastEmissionX;
                float dy = y - lastEmissionY;
                float threshold = NORMAL_EMIT_DISTANCE_CELLS * screenPixelsPerCell;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance > threshold
                        || updatesSinceEmission >= NORMAL_EMIT_TIMEOUT_UPDATES) {
                    addRecord(x, y, NORMAL_INITIAL_AGE, TYPE_NORMAL);
                    lastEmissionX = x;
                    lastEmissionY = y;
                    updatesSinceEmission = 0;
                }
            } else if (event.action == ACTION_UP || event.action == 3) {
                hasLastEmission = false;
            }
        }
        pendingInput.clear();
        updatesSinceEmission++;
    }

    /** Same packet ordering as {@link #processInput()}, with timeouts in 60 Hz credits. */
    private void processInputAdaptive(float logicalCredits) {
        if (pendingInput.isEmpty()) {
            updatesSinceEmissionCredits = advanceLogicalAge(
                    updatesSinceEmissionCredits, logicalCredits);
            return;
        }
        for (int i = 0; i < pendingInput.size(); ++i) {
            InputEvent event = pendingInput.get(i);
            float x = event.x;
            float y = event.y;
            if (event.action == ACTION_DOWN) {
                lastTouchX = x;
                lastTouchY = y;
                if (records.isEmpty()) {
                    tabOffsetX = ((x - width * 0.5f) / (width * 0.5f)) * TAB_SHIFT_RANGE;
                    tabOffsetY = ((height * 0.5f - y) / (height * 0.5f)) * TAB_SHIFT_RANGE;
                }
                addRecord(x, y, NORMAL_INITIAL_AGE, TYPE_NORMAL);
                lastEmissionX = x;
                lastEmissionY = y;
                hasLastEmission = true;
                updatesSinceEmissionCredits = 0.0f;
            } else if (event.action == ACTION_MOVE) {
                lastTouchX = x;
                lastTouchY = y;
                if (!hasLastEmission) {
                    lastEmissionX = x;
                    lastEmissionY = y;
                    hasLastEmission = true;
                }
                float dx = x - lastEmissionX;
                float dy = y - lastEmissionY;
                float threshold = NORMAL_EMIT_DISTANCE_CELLS * screenPixelsPerCell;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance > threshold
                        || emissionTimeoutReached(updatesSinceEmissionCredits)) {
                    addRecord(x, y, NORMAL_INITIAL_AGE, TYPE_NORMAL);
                    lastEmissionX = x;
                    lastEmissionY = y;
                    updatesSinceEmissionCredits = 0.0f;
                }
            } else if (event.action == ACTION_UP || event.action == 3) {
                hasLastEmission = false;
            }
        }
        pendingInput.clear();
        updatesSinceEmissionCredits = advanceLogicalAge(
                updatesSinceEmissionCredits, logicalCredits);
    }

    private void updateSimulation() {
        java.util.Arrays.fill(narrowField, 0.0f);
        java.util.Arrays.fill(wideField, 0.0f);
        int overflow = records.size() - MAX_ACTIVE_RECORDS;
        for (int i = 0; i < overflow; ++i) {
            records.get(i).type = TYPE_FORCED_FADE;
        }
        Iterator<Record> iterator = records.iterator();
        while (iterator.hasNext()) {
            Record record = iterator.next();
            if (!updateRecordGeometry(record)) {
                iterator.remove();
                continue;
            }
            rasterizeRecord(record);
            record.age++;
        }
        if (records.isEmpty()) {
            unlockActive = false;
            clearIntermediate = true;
            return;
        }
        updateNoise();
    }

    /** Fractional version of the stock update used only by the opt-in host path. */
    private void updateSimulationAdaptive(float logicalCredits) {
        java.util.Arrays.fill(narrowField, 0.0f);
        java.util.Arrays.fill(wideField, 0.0f);
        if (logicalCredits == 0.0f) {
            Iterator<Record> zeroIterator = records.iterator();
            while (zeroIterator.hasNext()) {
                Record record = zeroIterator.next();
                if (!record.hasRenderedGeometry) {
                    if (!updateRecordGeometryAdaptive(record, 0.0f)) {
                        zeroIterator.remove();
                        continue;
                    }
                    record.lastRenderedAge = record.adaptiveAge;
                    record.hasRenderedGeometry = true;
                }
                rasterizeRecord(record);
            }
            if (records.isEmpty()) {
                unlockActive = false;
                clearIntermediate = true;
            }
            return;
        }
        int overflow = records.size() - MAX_ACTIVE_RECORDS;
        for (int i = 0; i < overflow; ++i) {
            records.get(i).type = TYPE_FORCED_FADE;
        }
        Iterator<Record> iterator = records.iterator();
        while (iterator.hasNext()) {
            Record record = iterator.next();
            if (!updateRecordGeometryAdaptive(record, logicalCredits)) {
                iterator.remove();
                continue;
            }
            record.lastRenderedAge = record.adaptiveAge;
            record.hasRenderedGeometry = true;
            rasterizeRecord(record);
            record.adaptiveAge = advanceLogicalAge(record.adaptiveAge, logicalCredits);
        }
        if (records.isEmpty()) {
            unlockActive = false;
            clearIntermediate = true;
            return;
        }
        updateNoiseAdaptive(logicalCredits);
    }

    private boolean updateRecordGeometry(Record record) {
        if (record.type == TYPE_UNLOCK) {
            if (record.age >= UNLOCK_REMOVAL_AGE) {
                return false;
            }
            if (record.age < UNLOCK_EARLY_LIMIT) {
                record.outer = 27.2f * quintOut80(record.age / 33.0f);
                record.inner = record.age < 4 ? 0.0f
                        : 26.7f * quintOut80((record.age - 4.0f) / 29.0f);
            } else {
                record.outer *= 1.075f;
                record.inner *= 1.01f;
            }
            record.opacity = 1.0f;
            return true;
        }
        if (record.type == TYPE_FORCED_FADE) {
            record.opacity -= NORMAL_FORCED_FADE_STEP;
            if (record.opacity <= 0.0f) {
                return false;
            }
        } else if (record.age < NORMAL_OPACITY_HOLD) {
            record.opacity = 1.0f;
        } else {
            record.opacity = 1.0f - sineInOut80((record.age - NORMAL_OPACITY_HOLD)
                    / (NORMAL_DURATION - NORMAL_OPACITY_HOLD));
        }
        record.outer = NORMAL_RADIUS * sineInOut90(record.age / NORMAL_DURATION);
        record.inner = record.age < NORMAL_INNER_DELAY ? 0.0f
                : NORMAL_RADIUS * sineInOut90((record.age - NORMAL_INNER_DELAY)
                / (NORMAL_DURATION - NORMAL_INNER_DELAY));
        if (unlockActive && record.inner > 0.0f) {
            record.inner *= 1.225f;
            if (record.inner >= NORMAL_RADIUS) {
                return false;
            }
        }
        return record.age < NORMAL_REMOVAL_AGE && record.opacity > 0.0f;
    }

    /**
     * Temporal form of the discrete record update. Curved type-0 geometry remains a direct
     * function of fractional age; type-1's per-update multipliers use exponentiation, and the
     * type-2 linear fade consumes fractional logical credits.
     */
    private boolean updateRecordGeometryAdaptive(Record record, float logicalCredits) {
        float age = record.adaptiveAge;
        if (record.type == TYPE_UNLOCK) {
            if (!isAdaptiveRecordAgeVisible(record.type, age)) {
                return false;
            }
            if (age < UNLOCK_EARLY_LIMIT) {
                float endAge = advanceLogicalAge(age, logicalCredits);
                if (endAge < UNLOCK_EARLY_LIMIT) {
                    record.outer = 27.2f * quintOut80(age / 33.0f);
                    record.inner = age < 4.0f ? 0.0f
                            : 26.7f * quintOut80((age - 4.0f) / 29.0f);
                } else {
                    record.outer = 27.2f * quintOut80(UNLOCK_EARLY_LIMIT / 33.0f);
                    record.inner = 26.7f * quintOut80(
                            (UNLOCK_EARLY_LIMIT - 4.0f) / 29.0f);
                    float postEarlyCredits = endAge - UNLOCK_EARLY_LIMIT;
                    record.outer *= scaleTickMultiplier(1.075f, postEarlyCredits);
                    record.inner *= scaleTickMultiplier(1.01f, postEarlyCredits);
                }
            } else {
                record.outer *= scaleTickMultiplier(1.075f, logicalCredits);
                record.inner *= scaleTickMultiplier(1.01f, logicalCredits);
            }
            record.opacity = 1.0f;
            return true;
        }
        if (record.type == TYPE_FORCED_FADE) {
            record.opacity = scaleLinearFade(record.opacity,
                    NORMAL_FORCED_FADE_STEP, logicalCredits);
            if (record.opacity <= 0.0f) {
                return false;
            }
        } else if (age < NORMAL_OPACITY_HOLD) {
            record.opacity = 1.0f;
        } else {
            record.opacity = 1.0f - sineInOut80((age - NORMAL_OPACITY_HOLD)
                    / (NORMAL_DURATION - NORMAL_OPACITY_HOLD));
        }
        record.outer = NORMAL_RADIUS * sineInOut90(age / NORMAL_DURATION);
        record.inner = age < NORMAL_INNER_DELAY ? 0.0f
                : NORMAL_RADIUS * sineInOut90((age - NORMAL_INNER_DELAY)
                / (NORMAL_DURATION - NORMAL_INNER_DELAY));
        if (unlockActive && record.inner > 0.0f) {
            // Type-0 inner is derived from age every sample, so this stock unlock modifier is
            // an instantaneous geometry scale, not the persistent type-1 recurrence above.
            record.inner = applyNormalUnlockInnerScale(record.inner);
            if (record.inner >= NORMAL_RADIUS) {
                return false;
            }
        }
        return isAdaptiveRecordAgeVisible(record.type, age) && record.opacity > 0.0f;
    }

    private void rasterizeRecord(Record record) {
        float inner = record.inner;
        float outer = record.outer;
        float half = (outer + 2.0f - inner) * 0.5f;
        float middle = outer + 1.0f - half;
        for (int y = 0; y < simHeight; ++y) {
            float dy = record.y - y;
            int row = y * simWidth;
            for (int x = 0; x < simWidth; ++x) {
                float dx = record.x - x;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (!(inner - 1.0f < distance && distance < outer + 1.0f)) {
                    continue;
                }
                float q = sineInOut90(1.0f + Math.abs(distance - middle) / half);
                int index = row + x;
                float wide = record.opacity * record.opacity * q;
                if (wideField[index] < wide) {
                    wideField[index] = wide;
                }
                if (!(inner < distance && distance < outer)) {
                    continue;
                }
                float alpha = q;
                if (distance >= outer - 2.0f) {
                    alpha = (outer - distance) * 0.5f;
                }
                if (record.type != TYPE_UNLOCK && inner != 0.0f
                        && distance <= inner + 2.0f) {
                    alpha = (distance - inner) * 0.5f;
                }
                alpha *= record.opacity;
                if (narrowField[index] < alpha) {
                    narrowField[index] = alpha;
                }
            }
        }
    }

    private void updateNoise() {
        noiseCounter++;
        if (noiseCounter % 20 == 0) {
            noiseCounter = 0;
            System.arraycopy(noiseCurrent, 0, noiseFrom, 0, noiseCurrent.length);
            for (int i = 0; i < noiseTarget.length; ++i) {
                noiseTarget[i] = libcRand() * 2.188608e-10f + 0.1f;
            }
        }
        float t = noiseCounter * 0.05f;
        float inverse = 1.0f - t;
        for (int i = 0; i < noiseCurrent.length; ++i) {
            noiseCurrent[i] = noiseFrom[i] * inverse + noiseTarget[i] * t;
        }
    }

    /**
     * Advances the crystalline noise on the same 20-stock-update cadence. The target RNG is
     * sampled only when a logical boundary is crossed; intermediate display frames simply
     * interpolate the already-selected target.
     */
    private void updateNoiseAdaptive(float logicalCredits) {
        synchronized (NOISE_PHASE_LOCK) {
            // The phase and RNG are process-global. A recreated adaptive pipeline resumes the
            // same fractional phase; it never creates a new target merely on attachment.
            adaptiveNoisePhase.initializeFromStockCounter(noiseCounter);
            float remainingCredits = logicalCredits;
            if (adaptiveNoisePhase.credits() < 0.0f) {
                float untilFirstTarget = -adaptiveNoisePhase.credits();
                if (remainingCredits < untilFirstTarget) {
                    adaptiveNoisePhase.setCredits(
                            adaptiveNoisePhase.credits() + remainingCredits);
                    noiseCounter = adaptiveNoisePhase.stockCounter();
                    return;
                }
                remainingCredits -= untilFirstTarget;
                adaptiveNoisePhase.setCredits(0.0f);
                noiseCounter = 0;
                beginAdaptiveNoiseTarget();
            }
            while (remainingCredits > 0.0f) {
                float untilNextTarget = NOISE_CYCLE_CREDITS - adaptiveNoisePhase.credits();
                if (remainingCredits < untilNextTarget) {
                    adaptiveNoisePhase.setCredits(
                            adaptiveNoisePhase.credits() + remainingCredits);
                    noiseCounter = adaptiveNoisePhase.stockCounter();
                    interpolateAdaptiveNoise();
                    return;
                }
                // Stock never samples t=1.0: its wrap copies the last t=.95 field, then
                // selects a target for a fresh t=0 cycle.
                adaptiveNoisePhase.setCredits(NOISE_CYCLE_CREDITS);
                interpolateAdaptiveNoise();
                remainingCredits -= untilNextTarget;
                adaptiveNoisePhase.setCredits(0.0f);
                noiseCounter = 0;
                beginAdaptiveNoiseTarget();
            }
        }
    }

    private void beginAdaptiveNoiseTarget() {
        System.arraycopy(noiseCurrent, 0, noiseFrom, 0, noiseCurrent.length);
        for (int i = 0; i < noiseTarget.length; ++i) {
            noiseTarget[i] = libcRand() * 2.188608e-10f + 0.1f;
        }
    }

    private void interpolateAdaptiveNoise() {
        float t = adaptiveNoiseInterpolationForCredits(adaptiveNoisePhase.credits());
        float inverse = 1.0f - t;
        for (int i = 0; i < noiseCurrent.length; ++i) {
            noiseCurrent[i] = noiseFrom[i] * inverse + noiseTarget[i] * t;
        }
    }

    private void uploadCpuFields() {
        alphaBytes.clear();
        blurBytes.clear();
        for (int i = 0; i < narrowField.length; ++i) {
            alphaBytes.put((byte) clampByte(narrowField[i] * noiseCurrent[i] * 255.0f));
            blurBytes.put((byte) clampByte(wideField[i] * 25.5f));
        }
        alphaBytes.position(0);
        blurBytes.position(0);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, alphaTexture);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, simWidth, simHeight,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, alphaBytes);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, simWidth, simHeight,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, blurBytes);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void renderPasses() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        if (clearIntermediate) {
            clearRenderTarget(radialTarget, 0.5f, 0.5f, 0.0f, 0.0f);
            clearRenderTarget(advectTarget, 0.0f, 0.0f, 0.0f, 0.0f);
            clearIntermediate = false;
        }
        if (!records.isEmpty()) {
            renderRadial();
            renderAdvect();
        }
        renderComposite();
    }

    private void renderRadial() {
        radialTarget.bind();
        GLES20.glClearColor(0.5f, 0.5f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                radialBlendPrimed ? GLES20.GL_ONE : GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA);
        radialProgram.use();
        GLES20.glUniformMatrix4fv(radialProgram.uniform("uMVPMatrix"), 1, false,
                IDENTITY_MATRIX, 0);
        bindTexture(radialProgram, "uAlphaMap", 0, alphaTexture);
        bindTexture(radialProgram, "uPatternMap", 1, patternTexture);
        for (int i = 0; i < records.size(); ++i) {
            Record record = records.get(i);
            prepareRadialQuad(record);
            attribute(radialProgram, "aPosition", 3, radialPositions);
            attribute(radialProgram, "aTexUV", 2, radialLocalUv);
            attribute(radialProgram, "aScreenUV", 3, radialScreenUv);
            attribute(radialProgram, "aPatternUV", 3, radialPatternUv);
            float input = record.outer * (record.type == TYPE_UNLOCK
                    ? 0.017035775f : 0.04f);
            GLES20.glUniform1f(radialProgram.uniform("uTimeStep"), sineInOut33(input));
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            if (!radialBlendPrimed) {
                // SPIRenderer applies its current blend fields before drawRender(). The first
                // radial draw uses initialized SRC_ALPHA; drawRender primes GL_ONE thereafter.
                radialBlendPrimed = true;
                GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,
                        GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE,
                        GLES20.GL_ONE_MINUS_SRC_ALPHA);
            }
            disableAttribute(radialProgram, "aPosition");
            disableAttribute(radialProgram, "aTexUV");
            disableAttribute(radialProgram, "aScreenUV");
            disableAttribute(radialProgram, "aPatternUV");
        }
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    private void prepareRadialQuad(Record record) {
        float half = record.outer * RADIAL_QUAD_SCALE * 0.5f;
        float left = (record.x - half) / simWidth;
        float right = (record.x + half) / simWidth;
        // SPDrawBrilliantRadial::setPosition receives (xGrid, simH - yGrid).
        float centerY = simHeight - record.y;
        float bottom = (centerY - half) / simHeight;
        float top = (centerY + half) / simHeight;
        put(radialPositions,
                left * 2.0f - 1.0f, bottom * 2.0f - 1.0f, 0.0f,
                right * 2.0f - 1.0f, bottom * 2.0f - 1.0f, 0.0f,
                left * 2.0f - 1.0f, top * 2.0f - 1.0f, 0.0f,
                right * 2.0f - 1.0f, top * 2.0f - 1.0f, 0.0f);
        // SrkCommon maps both CPU and DiamondPT coordinates through the portrait Y flip.
        put(radialScreenUv,
                left, 1.0f - bottom, 0.0f,
                right, 1.0f - bottom, 0.0f,
                left, 1.0f - top, 0.0f,
                right, 1.0f - top, 0.0f);
        if (width <= height) {
            put(radialPatternUv,
                    left, 1.0f - bottom, 0.0f,
                    right, 1.0f - bottom, 0.0f,
                    left, 1.0f - top, 0.0f,
                    right, 1.0f - top, 0.0f);
        } else {
            put(radialPatternUv,
                    1.0f - bottom, left, 0.0f,
                    1.0f - bottom, right, 0.0f,
                    1.0f - top, left, 0.0f,
                    1.0f - top, right, 0.0f);
        }
    }

    private void renderAdvect() {
        advectTarget.bind();
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        advectProgram.use();
        GLES20.glUniformMatrix4fv(advectProgram.uniform("uMVPMatrix"), 1, false,
                IDENTITY_MATRIX, 0);
        GLES20.glUniform1fv(advectProgram.uniform("offset"), 8, ADVECT_OFFSET, 0);
        GLES20.glUniform1fv(advectProgram.uniform("weight"), 8, ADVECT_WEIGHT, 0);
        bindTexture(advectProgram, "uRadialVelocity", 0, radialTarget.texture);
        attribute(advectProgram, "aPosition", 3, fullscreenPositions);
        attribute(advectProgram, "aTexUV", 2, androidBackgroundUv);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        disableAttribute(advectProgram, "aPosition");
        disableAttribute(advectProgram, "aTexUV");
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void renderComposite() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (records.isEmpty() || !backgroundReady || !patternReady) {
            return;
        }
        prepareTabScaledUv();
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        ringProgram.use();
        GLES20.glUniformMatrix4fv(ringProgram.uniform("uMVPMatrix"), 1, false,
                IDENTITY_MATRIX, 0);
        GLES20.glUniform1f(ringProgram.uniform("uAlpha"), 1.0f);
        bindTexture(ringProgram, "uBackgroundMap", 0, backgroundTexture);
        bindTexture(ringProgram, "uShineMap", 1, advectTarget.texture);
        bindTexture(ringProgram, "uDiamondMap", 2, radialTarget.texture);
        bindTexture(ringProgram, "uAlphaMap", 3, alphaTexture);
        bindTexture(ringProgram, "uBlurMap", 4, blurTexture);
        attribute(ringProgram, "aPosition", 3, fullscreenPositions);
        attribute(ringProgram, "aTexUV", 2, radialLocalUv);
        attribute(ringProgram, "aTexUVBg", 2, androidBackgroundUv);
        attribute(ringProgram, "aTabScaledUV", 3, tabScaledUv);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        disableAttribute(ringProgram, "aPosition");
        disableAttribute(ringProgram, "aTexUV");
        disableAttribute(ringProgram, "aTexUVBg");
        disableAttribute(ringProgram, "aTabScaledUV");
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    private void prepareTabScaledUv() {
        float lowX = 1.0f - TAB_SCALE + tabOffsetX;
        float highX = TAB_SCALE + tabOffsetX;
        float lowY = 1.0f - TAB_SCALE + tabOffsetY;
        float highY = TAB_SCALE + tabOffsetY;
        // Matches SPDrawBrilliantRing::createTabScaledTextureUV vertex order.
        put(tabScaledUv,
                lowX, highY, 0.0f,
                highX, highY, 0.0f,
                lowX, lowY, 0.0f,
                highX, lowY, 0.0f);
    }

    private static void bindTexture(Program program, String uniform, int unit, int texture) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(program.uniform(uniform), unit);
    }

    private static void attribute(Program program, String name, int size, FloatBuffer values) {
        int location = program.attribute(name);
        if (location >= 0) {
            values.position(0);
            GLES20.glEnableVertexAttribArray(location);
            GLES20.glVertexAttribPointer(location, size, GLES20.GL_FLOAT, false, 0, values);
        }
    }

    private static void disableAttribute(Program program, String name) {
        int location = program.attribute(name);
        if (location >= 0) {
            GLES20.glDisableVertexAttribArray(location);
        }
    }

    private static void put(FloatBuffer buffer, float... values) {
        buffer.position(0);
        buffer.put(values);
        buffer.position(0);
    }

    /** Returns stock-60-Hz logical credits for one measured display interval. */
    static float adaptiveSimulationCreditsForElapsedNanos(long elapsedNanos) {
        if (elapsedNanos <= 0L || elapsedNanos > ADAPTIVE_STALL_NS) {
            return 0.0f;
        }
        return (float) (elapsedNanos / (double) STOCK_SIMULATION_TICK_NS);
    }

    /** Exactly one logical credit follows the pre-existing integer implementation. */
    static boolean usesExactStockStep(float logicalCredits) {
        return Float.floatToIntBits(logicalCredits) == Float.floatToIntBits(1.0f);
    }

    /** Zero is a valid first/stall frame: accept input and redraw, but advance no simulation. */
    static boolean isAdaptiveZeroCreditFrame(float logicalCredits) {
        return logicalCredits == 0.0f;
    }

    /** Pure seam for temporal ages and emission credits. */
    static float advanceLogicalAge(float age, float logicalCredits) {
        if (!(logicalCredits > 0.0f) || Float.isInfinite(logicalCredits)) {
            return age;
        }
        return age + logicalCredits;
    }

    /** First fractional geometry after legacy rendered {@code lastRenderedAge}. */
    static float firstAdaptiveAgeAfterLegacyRender(float lastRenderedAge,
            float logicalCredits) {
        return advanceLogicalAge(lastRenderedAge, logicalCredits);
    }

    /** Converts a per-stock-tick multiplier to a fractional logical duration. */
    static float scaleTickMultiplier(float perTickMultiplier, float logicalCredits) {
        if (usesExactStockStep(logicalCredits)) {
            return perTickMultiplier;
        }
        return (float) Math.pow(perTickMultiplier, logicalCredits);
    }

    /** Converts the stock type-2 linear opacity decrement to logical credits. */
    static float scaleLinearFade(float opacity, float perTickFade, float logicalCredits) {
        if (usesExactStockStep(logicalCredits)) {
            return opacity - perTickFade;
        }
        return opacity - perTickFade * logicalCredits;
    }

    /** Applies the stateless normal-ring unlock geometry scale once per rendered sample. */
    static float applyNormalUnlockInnerScale(float inner) {
        return inner * 1.225f;
    }

    /** Fraction of the current 20-credit adaptive noise target blend. */
    static float adaptiveNoiseInterpolationForCredits(float credits) {
        // SrkCommon only samples 0.00 through 0.95. At the 20-credit wrap it copies that
        // .95 field into noiseFrom before generating the next target; it never anchors at 1.
        return Math.max(0.0f, Math.min(0.95f, credits * 0.05f));
    }

    /** The MOVE timeout is evaluated before the current frame's credits are added. */
    static boolean emissionTimeoutReached(float creditsSinceEmission) {
        return creditsSinceEmission >= NORMAL_EMIT_TIMEOUT_UPDATES;
    }

    /** A terminal age is removed on the next CPU update, after its prior frame was composed. */
    static boolean isAdaptiveRecordAgeVisible(int type, float age) {
        return type == TYPE_UNLOCK ? age < UNLOCK_REMOVAL_AGE : age < NORMAL_REMOVAL_AGE;
    }

    /**
     * Monotonic native-refresh clock. It samples every timestamp, including a discarded
     * compositor stall, so a later valid frame can never replay hidden elapsed time.
     */
    static final class AdaptiveSimulationClock {
        private long previousFrameNanos = Long.MIN_VALUE;

        float advance(long frameTimeNanos) {
            if (previousFrameNanos == Long.MIN_VALUE) {
                previousFrameNanos = frameTimeNanos;
                return 0.0f;
            }
            long elapsedNanos = frameTimeNanos - previousFrameNanos;
            previousFrameNanos = frameTimeNanos;
            return adaptiveSimulationCreditsForElapsedNanos(elapsedNanos);
        }

        void reset() {
            previousFrameNanos = Long.MIN_VALUE;
        }
    }

    /**
     * A dynamic panel can move from an exact 60 Hz interval to a fractional interval and back.
     * Once fractional state exists, later unit deltas must keep consuming that same state instead
     * of re-entering the legacy integer fields. Reset/recreation starts a fresh stock-compatible
     * trace.
     */
    static final class AdaptiveStepMode {
        private boolean fractionalStepSeen;

        boolean usesStockStep(float logicalCredits) {
            return !fractionalStepSeen && usesExactStockStep(logicalCredits);
        }

        boolean record(float logicalCredits) {
            if (logicalCredits > 0.0f && !usesExactStockStep(logicalCredits)
                    && !fractionalStepSeen) {
                fractionalStepSeen = true;
                return true;
            }
            return false;
        }

        void reset() {
            fractionalStepSeen = false;
        }
    }

    /**
     * Fractional facade over SrkCommon's process-global integer noise phase. The stock counter
     * is mirrored only at completed logical integer credits; random targets are still generated
     * exclusively by the owning pipeline at its 20-credit boundaries.
     */
    static final class AdaptiveNoisePhase {
        private boolean initialized;
        private float logicalCredits = -1.0f;

        void initializeFromStockCounter(int stockCounter) {
            if (!initialized) {
                logicalCredits = stockCounter;
                initialized = true;
            }
        }

        void syncStockCounter(int stockCounter) {
            logicalCredits = stockCounter;
            initialized = true;
        }

        float credits() {
            return logicalCredits;
        }

        void setCredits(float credits) {
            logicalCredits = credits;
            initialized = true;
        }

        int stockCounter() {
            if (logicalCredits < 0.0f) {
                return -1;
            }
            return Math.min((int) NOISE_CYCLE_CREDITS - 1, (int) logicalCredits);
        }
    }

    private static int clampByte(float value) {
        if (!(value > 0.0f)) {
            return 0;
        }
        return Math.min(255, (int) value);
    }

    private static int createLuminanceTexture(int width, int height, boolean linear) {
        int texture = generateTexture();
        textureParameters(texture, linear);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                width, height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null);
        return texture;
    }

    private static int createRgbaTexture(int width, int height,
            boolean linear, boolean mirroredRepeat) {
        int texture = generateTexture();
        textureParameters(texture, linear, mirroredRepeat);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        return texture;
    }

    private static int generateTexture() {
        int[] names = new int[1];
        GLES20.glGenTextures(1, names, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, names[0]);
        return names[0];
    }

    private static void textureParameters(int texture, boolean linear) {
        textureParameters(texture, linear, false);
    }

    private static void textureParameters(int texture, boolean linear, boolean mirroredRepeat) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        int filter = linear ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        int wrap = mirroredRepeat ? GLES20.GL_MIRRORED_REPEAT : GLES20.GL_CLAMP_TO_EDGE;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
    }

    private static void deleteTexture(int texture) {
        if (texture != 0) {
            int[] names = {texture};
            GLES20.glDeleteTextures(1, names, 0);
        }
    }

    private static void clearRenderTarget(RenderTarget target,
            float red, float green, float blue, float alpha) {
        if (target == null) {
            return;
        }
        target.bind();
        GLES20.glClearColor(red, green, blue, alpha);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private static void checkGl(String operation) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(operation + " GLES error 0x"
                    + Integer.toHexString(error));
        }
    }

    private void seedNoiseCurrent() {
        if (noiseCurrent == null) {
            return;
        }
        for (int i = 0; i < noiseCurrent.length; ++i) {
            float value = libcRand() * 3.4924597e-10f + 0.2f;
            noiseCurrent[i] = value;
        }
    }

    private static synchronized int libcRand() {
        int sum = bionicRandState[bionicRandFront] + bionicRandState[bionicRandRear];
        bionicRandState[bionicRandFront] = sum;
        int result = (sum >>> 1) & 0x7fffffff;
        if (++bionicRandFront >= bionicRandState.length) {
            bionicRandFront = 0;
            ++bionicRandRear;
        } else if (++bionicRandRear >= bionicRandState.length) {
            bionicRandRear = 0;
        }
        return result;
    }

    /** Exact API-21 Bionic srandom() initialization used by srand() in the stock binary. */
    private static synchronized void bionicSrand(int seed) {
        bionicRandState[0] = seed;
        for (int i = 1; i < bionicRandState.length; ++i) {
            int previous = bionicRandState[i - 1];
            int high = previous / 127773;
            int low = previous % 127773;
            int value = 16807 * low - 2836 * high;
            if (value <= 0) {
                value += 0x7fffffff;
            }
            bionicRandState[i] = value;
        }
        bionicRandFront = 3;
        bionicRandRear = 0;
        for (int i = 0; i < 10 * bionicRandState.length; ++i) {
            libcRand();
        }
    }

    private static float sineInOut90(float value) {
        return piecewiseBezier(value, 0.2f, SINE_IN_OUT_90);
    }

    private static float sineInOut80(float value) {
        return piecewiseBezier(value, 0.2f, SINE_IN_OUT_80);
    }

    private static float quintOut80(float value) {
        return piecewiseBezier(value, 0.5f, QUINT_OUT_80);
    }

    private static float sineInOut33(float value) {
        return piecewiseBezier(value, 0.5f, SINE_IN_OUT_33);
    }

    private static float piecewiseBezier(float value, float segmentWidth, float[][] points) {
        float x = Math.max(0.0f, value);
        int segment = Math.min(points.length - 1, (int) (x / segmentWidth));
        float local = (x - segment * segmentWidth) / segmentWidth;
        float p0 = points[segment][0];
        float p1 = points[segment][1];
        float p2 = points[segment][2];
        return p0 + (local * (p2 - p0)
                + 2.0f * (p1 - p0) * (1.0f - local)) * local;
    }

    private void addRecord(float screenX, float screenY, int age, int type) {
        float xGrid = screenX / Math.max(1.0f, width) * simWidth;
        float yGrid = (1.0f - screenY / Math.max(1.0f, height)) * simHeight;
        records.add(new Record(xGrid, yGrid, age, type));
    }

    private static FloatBuffer directFloats(int count) {
        return ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static FloatBuffer directFloats(float[] values) {
        FloatBuffer result = directFloats(values.length);
        result.put(values).position(0);
        return result;
    }

    private static void drainErrors() {
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            // Drain stale errors before an operation whose status matters.
        }
    }

    private static final float[][] SINE_IN_OUT_90 = {
            {0.000f, 0.000f, 0.247f},
            {0.247f, 0.480f, 0.720f},
            {0.700f, 0.835f, 0.905f},
            {0.910f, 0.955f, 0.978f},
            {0.978f, 0.9999f, 1.000f}
    };
    private static final float[][] SINE_IN_OUT_80 = {
            {0.000f, 0.000f, 0.195f},
            {0.195f, 0.480f, 0.645f},
            {0.645f, 0.835f, 0.885f},
            {0.885f, 0.955f, 0.978f},
            {0.978f, 0.9999f, 1.000f}
    };
    private static final float[][] QUINT_OUT_80 = {
            {0.040f, 0.718f, 0.840f},
            {0.845f, 0.998f, 1.000f}
    };
    private static final float[][] SINE_IN_OUT_33 = {
            {0.000f, 0.050f, 0.495f},
            {0.495f, 0.940f, 1.000f}
    };

    /* Literal SrkCommon shader at Ghidra 0x5ce00 (274 bytes before NUL). */
    private static final String ADVECT_VERTEX_SHADER =
            "precision mediump float;\n"
            + "uniform mediump mat4 uMVPMatrix;\n"
            + "attribute vec4 aPosition;\n"
            + "attribute vec2 aTexUV;\n"
            + "varying vec2 vTexUV;\n"
            + "void main()\n"
            + "{\n"
            + "   vTexUV = vec2(aTexUV.x, aTexUV.y);\n"
            + "   gl_Position = uMVPMatrix * aPosition;\n"
            + "}\n";

    /* Literal SrkCommon shader at Ghidra 0x5cf14 (1328 bytes before NUL). */
    private static final String ADVECT_FRAGMENT_SHADER =
            "precision lowp float;\n"
            + "uniform sampler2D uRadialVelocity;\n"
            + "varying vec2 vTexUV;\n"
            + "uniform float offset[8];\n"
            + "uniform float weight[8];\n"
            + "void main()\n"
            + "{\n"
            + " vec4 current = texture2D(uRadialVelocity, vTexUV);\n"
            + " if(current.x == 0.5 && current.y == 0.5) discard;\n"
            + " vec2 velocity = (current.xy) * 2.0 - vec2(1.0);\n"
            + " current = vec4(current.z / current.a);\n"
            + " vec4 TexColor = current * weight[0];\n"
            + " for (int i = 1; i < 8; i++)\n"
            + " {\n"
            + "  vec2 offset = -velocity * 0.02 * offset[i] * 0.4;\n"
            + "  vec4 RadialVelocity = texture2D(uRadialVelocity, vTexUV + offset);\n"
            + "  TexColor += vec4(RadialVelocity.z / RadialVelocity.a) * weight[i];\n"
            + " }\n"
            + " gl_FragColor = TexColor;\n"
            + "}\n";

    /* Literal SrkCommon shader at Ghidra 0x5d484 (612 bytes before NUL). */
    private static final String RADIAL_VERTEX_SHADER =
            "precision mediump float;\n"
            + "uniform mediump mat4 uMVPMatrix;\n"
            + "attribute vec4 aPosition;\n"
            + "attribute vec2 aScreenUV;\n"
            + "attribute vec2 aTexUV;\n"
            + "attribute vec2 aPatternUV;\n"
            + "varying vec2 vTexUV;\n"
            + "varying vec2 vScreenUV;\n"
            + "varying vec2 vPatternUV;\n"
            + "void main()\n"
            + "{\n"
            + "   vTexUV = aTexUV;\n"
            + "   vPatternUV = aPatternUV;\n"
            + "   vScreenUV = aScreenUV;\n"
            + "   gl_Position = uMVPMatrix * aPosition;\n"
            + "}\n";

    /* Literal SrkCommon shader at Ghidra 0x5d6ec (873 bytes before NUL). */
    private static final String RADIAL_FRAGMENT_SHADER =
            "precision lowp float;\n"
            + "uniform sampler2D uAlphaMap;\n"
            + "uniform sampler2D uPatternMap;\n"
            + "uniform float uTimeStep;\n"
            + "varying vec2 vTexUV;\n"
            + "varying vec2 vScreenUV;\n"
            + "varying vec2 vPatternUV;\n"
            + "void main()\n"
            + "{\n"
            + " vec2 N;\n"
            + " N.xy = (vTexUV * 2.0 - vec2(1.0));\n"
            + " float mag = dot(N.xy, N.xy);\n"
            + " if(mag > 1.0) discard;\n"
            + " N.y *= 0.5625;\n"
            + " N.xy = (N.xy * uTimeStep + 1.0) * 0.5;\n"
            + " float alpha1 = texture2D(uAlphaMap, vScreenUV).r;\n"
            + " float alpha2 = texture2D(uPatternMap, vPatternUV).a;\n"
            + " gl_FragColor = vec4(N.xy, alpha1 * alpha2, uTimeStep);\n"
            + "}\n";

    /* Literal SrkCommon shader at Ghidra 0x5dab0 (1388 bytes before NUL). */
    private static final String RING_VERTEX_SHADER =
            "precision mediump float;\n"
            + "uniform mediump mat4 uMVPMatrix;\n"
            + "attribute vec4 aPosition;\n"
            + "attribute vec2 aTexUV;\n"
            + "attribute vec2 aTexUVBg;\n"
            + "attribute vec2 aTabScaledUV;\n"
            + "varying vec2 vTexUV;\n"
            + "varying vec2 vTexUVBg;\n"
            + "varying vec2 vTabScaledUV;\n"
            + "void main()\n"
            + "{\n"
            + "   vTexUV = aTexUV;\n"
            + "   vTexUVBg = aTexUVBg;\n"
            + "   vTabScaledUV = aTabScaledUV ;\n"
            + "   gl_Position = uMVPMatrix * aPosition;\n"
            + "}\n";

    /* Literal SrkCommon shader at Ghidra 0x5e020 (3802 bytes before NUL). */
    private static final String STOCK_RING_FRAGMENT_SHADER =
            "precision lowp float;\n"
            + "uniform sampler2D uBackgroundMap;\n"
            + "uniform sampler2D uShineMap;\n"
            + "uniform sampler2D uDiamondMap;\n"
            + "uniform sampler2D uAlphaMap;\n"
            + "uniform sampler2D uBlurMap;\n"
            + "uniform float uAlpha;\n"
            + "varying highp vec2 vTexUV;\n"
            + "varying vec2 vTabScaledUV;\n"
            + "varying vec2 vTexUVBg;\n"
            + "vec3 rgb2hsv(vec3 c)\n"
            + "{\n"
            + " vec4 K = vec4(0.0, -0.333333333, 0.666666666, -1.0);\n"
            + " vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));\n"
            + " vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));\n"
            + " float d = q.x - min(q.w, q.y);\n"
            + " float e = 1.0e-10;\n"
            + " return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);\n"
            + "}\n"
            + "vec3 hsv2rgb(vec3 c)\n"
            + "{\n"
            + " vec4 K = vec4(1.0, 0.666666666, 0.333333333, 3.0);\n"
            + " vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);\n"
            + " return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);\n"
            + "}\n"
            + "void main()\n"
            + "{\n"
            + " vec3 bgColor = texture2D( uBackgroundMap, vTexUVBg).rgb;\n"
            + " float shineColor = texture2D( uShineMap, vTexUV).r;\n"
            + " vec4 diamondColor = texture2D( uDiamondMap, vec2(vTexUV.x, 1.0 - vTexUV.y));\n"
            + " float alpha = diamondColor.z / diamondColor.a + shineColor;\n"
            + " alpha = clamp(alpha, 0.0, 1.0);\n"
            + " if(alpha != 0.0)\n"
            + " {\n"
            + "  float alphaColor = texture2D( uAlphaMap, vTexUV).r;\n"
            + "  float blurColor = texture2D( uBlurMap, vTexUV).r;\n"
            + "  vec3 bgScaledColor = texture2D( uBackgroundMap, vTabScaledUV).rgb;\n"
            + "  vec3 hsv = rgb2hsv(bgScaledColor.rgb);\n"
            + "  if( hsv.g > 0.85 && hsv.b < 0.7 ) { hsv.r = hsv.r - 0.027; hsv.g = 1.4; hsv.b = hsv.b * 1.3;  }\n"
            + "  else { hsv.r = hsv.r - 0.027; }\n"
            + "  vec3 convertedRGB = hsv2rgb(hsv);\n"
            + "  gl_FragColor.rgb = mix(bgColor, convertedRGB, alphaColor) + blurColor + alpha;\n"
            + " }\n"
            + " else\n"
            + " {\n"
            + "  gl_FragColor.rgb = bgColor;\n"
            + " }\n"
            + " gl_FragColor.a = uAlpha;\n"
            + "}\n";

    private static String overlayRingFragmentShader() {
        // Same local-alpha patch staged into ARM32 SrkCommon: only the final alpha instruction
        // changes; stock RGB, branches and the deliberately unguarded z/a stay byte-equivalent.
        return STOCK_RING_FRAGMENT_SHADER.replace(
                " gl_FragColor.a = uAlpha;",
                " gl_FragColor.a = (alpha != 0.0) ? uAlpha : 0.0;");
    }

    private static final class InputEvent {
        final int action;
        final float x;
        final float y;

        InputEvent(int action, float x, float y) {
            this.action = action;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Record {
        final float x;
        final float y;
        float inner;
        float outer;
        int age;
        float adaptiveAge;
        float lastRenderedAge;
        boolean hasRenderedGeometry;
        float opacity = 1.0f;
        int type;

        Record(float x, float y, int age, int type) {
            this.x = x;
            this.y = y;
            this.age = age;
            this.adaptiveAge = age;
            this.lastRenderedAge = age;
            this.type = type;
        }
    }

    private static final class Program {
        private int name;

        Program(String vertexSource, String fragmentSource) {
            int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            name = GLES20.glCreateProgram();
            GLES20.glAttachShader(name, vertex);
            GLES20.glAttachShader(name, fragment);
            GLES20.glLinkProgram(name);
            int[] status = new int[1];
            GLES20.glGetProgramiv(name, GLES20.GL_LINK_STATUS, status, 0);
            String log = GLES20.glGetProgramInfoLog(name);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            if (status[0] == 0) {
                GLES20.glDeleteProgram(name);
                name = 0;
                throw new IllegalStateException("Brilliant Ring program link failed: " + log);
            }
        }

        void use() {
            GLES20.glUseProgram(name);
        }

        int attribute(String attribute) {
            return GLES20.glGetAttribLocation(name, attribute);
        }

        int uniform(String uniform) {
            return GLES20.glGetUniformLocation(name, uniform);
        }

        void release() {
            if (name != 0) {
                GLES20.glDeleteProgram(name);
                name = 0;
            }
        }

        private static int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("Brilliant Ring shader compile failed: " + log);
            }
            return shader;
        }
    }

    private static final class RenderTarget {
        int framebuffer;
        int texture;
        int width;
        int height;

        RenderTarget(int targetWidth, int targetHeight) {
            width = Math.max(1, targetWidth);
            height = Math.max(1, targetHeight);
            texture = createRgbaTexture(width, height, true, false);
            int[] names = new int[1];
            GLES20.glGenFramebuffers(1, names, 0);
            framebuffer = names[0];
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                release();
                throw new IllegalStateException("Brilliant Ring FBO incomplete: 0x"
                        + Integer.toHexString(status));
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        void bind() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
            GLES20.glViewport(0, 0, width, height);
        }

        void release() {
            if (framebuffer != 0) {
                int[] names = {framebuffer};
                GLES20.glDeleteFramebuffers(1, names, 0);
                framebuffer = 0;
            }
            deleteTexture(texture);
            texture = 0;
        }
    }
}
