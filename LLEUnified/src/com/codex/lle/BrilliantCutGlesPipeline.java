package com.codex.lle;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * GL-thread-only ARM64 reconstruction of Samsung's Brilliant Cut compositor.
 *
 * <p>The geometry stream, plane grouping, random normal construction, stock shader constants and
 * animation durations are taken from the Tab S ARM32 oracle.  The final fragment is expressed as a
 * premultiplied transparent overlay so SurfaceFlinger recreates the stock full-background result
 * without replacing the live lock-screen wallpaper.</p>
 */
public final class BrilliantCutGlesPipeline {
    private static final String TAG = "LLEBrilliantCutGL";

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;

    private static final float STOCK_STEP_SECONDS = 0.016f;
    /* Note 4 oracle starts Brilliant Cut in IMAGE_TYPE_SPECIAL (.34/.45). */
    private static final float TOUCH_RADIUS_ORACLE = 0.34f;
    private static final float TOUCH_BRIGHTNESS_ORACLE = 0.45f;
    private static final float TOUCH_LIFETIME_SECONDS = 1.6f;
    private static final float TOUCH_REPEAT_COUNT = 1.0f;
    private static final float TOUCH_NEXT_TERM_SECONDS = 0.075f;
    private static final float TOUCH_GROW_SECONDS = 0.2f;
    private static final float NORMAL_IMAGE_SHIFT = 0.1f;
    private static final float AFFORDANCE_UNLOCK_BRIGHTNESS = 1.0f;
    private static final float AFFORDANCE_SECONDS = 1.0f;
    private static final float UNLOCK_SECONDS = 0.4f;
    private static final float UNLOCK_MASK_TARGET = 0.4f;
    private static final float AFFORDANCE_CENTER_X = 5.0f;
    private static final float AFFORDANCE_CENTER_Y = -5.0f;
    private static final float AFFORDANCE_STROKE = 2.0f;

    private final ArrayList<InputEvent> pendingInput = new ArrayList<InputEvent>(8);

    private Program program;
    private Program maskProgram;
    private Program affordanceMaskProgram;
    private int backgroundTexture;
    private int brushTexture;
    private int maskTexture;
    private int maskFramebuffer;
    private int maskWidth;
    private int maskHeight;
    private int width;
    private int height;
    private float widthRatio = 1.0f;
    private float heightRatio = 1.0f;
    private boolean initialized;
    private boolean backgroundReady;
    private boolean brushReady;
    private boolean abandoned;

    private BrilliantCutStockGeometry.Mesh mesh;
    private PlaneState[] planes;
    private FloatBuffer positions;
    private FloatBuffer uvs;
    private FloatBuffer normals;
    private FloatBuffer auxNormals;
    private FloatBuffer vertexAlphas;
    private FloatBuffer maskQuadPositions;
    private FloatBuffer maskQuadUvs;
    private float[] alphaValues;
    private boolean alphaBufferDirty;

    private float lightX;
    private float lightY;
    private boolean affordanceActive;
    private float affordanceAge;
    private boolean unlockActive;
    private float unlockAge;
    private boolean unlockFinalFramePending;
    private final AdaptiveFinalFrameHold adaptiveFinalFrameHold = new AdaptiveFinalFrameHold();
    private final ArrayList<LightState> touchLights = new ArrayList<LightState>(24);
    private float touchEmitClock = TOUCH_NEXT_TERM_SECONDS;
    private boolean releaseBounceActive;
    private float releaseBounceAge;
    private float releaseBounceStartX;
    private float releaseBounceStartY;
    private float releaseBounceTargetX;
    private float releaseBounceTargetY;

    public BrilliantCutGlesPipeline() {
    }

    /** Creates all context-owned resources and decodes the exact stock portrait/landscape mesh. */
    public void initialize(int surfaceWidth, int surfaceHeight, Bitmap lightBrush) {
        release();
        abandoned = false;
        width = Math.max(1, surfaceWidth);
        height = Math.max(1, surfaceHeight);
        if (width <= height) {
            widthRatio = width / (float) height;
            heightRatio = 1.0f;
            mesh = BrilliantCutStockGeometry.get(BrilliantCutStockGeometry.PORTRAIT_NORMAL);
        } else {
            widthRatio = 1.0f;
            heightRatio = height / (float) width;
            mesh = BrilliantCutStockGeometry.get(BrilliantCutStockGeometry.LANDSCAPE_NORMAL);
        }
        buildMeshStreams(mesh);

        program = new Program(VERTEX_SHADER, OVERLAY_FRAGMENT_SHADER);
        maskProgram = new Program(MASK_VERTEX_SHADER, MASK_FRAGMENT_SHADER);
        affordanceMaskProgram = new Program(AFFORDANCE_MASK_VERTEX_SHADER,
                AFFORDANCE_MASK_FRAGMENT_SHADER);
        backgroundTexture = createTexture();
        brushTexture = createTexture();
        maskWidth = Math.max(1, width / 2);
        maskHeight = Math.max(1, height / 2);
        maskTexture = createRenderTexture(maskWidth, maskHeight);
        maskFramebuffer = createFramebuffer(maskTexture);
        maskQuadPositions = directFloats(new float[] {
                -1.0f, 1.0f, 0.0f,
                -1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                1.0f, -1.0f, 0.0f
        });
        maskQuadUvs = directFloats(new float[] {
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 1.0f,
                1.0f, 0.0f
        });
        backgroundReady = false;
        brushReady = false;
        if (lightBrush != null && !lightBrush.isRecycled()) {
            drainErrors();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, brushTexture);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, lightBrush, 0);
            brushReady = GLES20.glGetError() == GLES20.GL_NO_ERROR;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        if (!brushReady) {
            release();
            throw new IllegalStateException("Brilliant Cut LightBrush upload failed");
        }
        initialized = true;
        reset();
        checkGl("initialize");
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

    /** Updates only Samsung's light anchor after multi-touch suppression; no plane is emitted. */
    public void realign(float x, float y) {
        setLightPosition(x, y);
    }

    public void unlock() {
        if (!initialized || abandoned) {
            return;
        }
        processInput();
        affordanceActive = false;
        releaseBounceActive = false;
        unlockActive = true;
        unlockAge = 0.0f;
        unlockFinalFramePending = false;
        adaptiveFinalFrameHold.reset();
    }

    public void affordance(float x, float y) {
        if (!initialized || abandoned) {
            return;
        }
        pendingInput.clear();
        touchLights.clear();
        releaseBounceActive = false;
        unlockActive = false;
        unlockFinalFramePending = false;
        adaptiveFinalFrameHold.reset();
        affordanceActive = true;
        affordanceAge = 0.0f;
    }

    public void reset() {
        pendingInput.clear();
        affordanceActive = false;
        affordanceAge = 0.0f;
        unlockActive = false;
        unlockAge = 0.0f;
        unlockFinalFramePending = false;
        adaptiveFinalFrameHold.reset();
        lightX = 0.0f;
        lightY = 0.0f;
        touchLights.clear();
        touchEmitClock = TOUCH_NEXT_TERM_SECONDS;
        releaseBounceActive = false;
        releaseBounceAge = 0.0f;
        clearPlanes();
    }

    public boolean renderFrame() {
        return renderFrame(true);
    }

    public boolean renderFrame(boolean advanceSimulation) {
        if (!initialized || abandoned) {
            return false;
        }
        if (advanceSimulation) {
            processInput();
            updateSimulation(STOCK_STEP_SECONDS);
        }
        renderComposite();
        return !isIdle();
    }

    /**
     * Native-refresh simulation entry point.  The caller maps display elapsed time into the
     * recovered 16 ms-per-60 Hz stock timebase before invoking this method.
     *
     * <p>Input is intentionally drained even for a zero first/stalled frame so its first visible
     * state is rendered without consuming simulation time.  All timed state below is already
     * expressed in seconds, so using the fractional elapsed value preserves lifetimes, the
     * 75 ms touch-emission gate, release bounce, affordance and unlock duration at any cadence.</p>
     */
    public boolean renderFrame(float elapsedSeconds) {
        if (!initialized || abandoned) {
            return false;
        }
        processInput();
        if (elapsedSeconds > 0.0f) {
            updateAdaptiveSimulation(elapsedSeconds);
        }
        renderComposite();
        return !isIdle();
    }

    public boolean isIdle() {
        if (!pendingInput.isEmpty() || affordanceActive || unlockActive
                || !touchLights.isEmpty()) {
            return false;
        }
        if (planes != null) {
            for (PlaneState plane : planes) {
                if (plane.animating || plane.alpha > 0.00001f) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean hasBackground() {
        return backgroundReady;
    }

    /** Host compatibility: the pattern resource is the stock LightBrush bitmap. */
    public boolean hasPattern() {
        return brushReady;
    }

    /** Forgets names owned by an EGL context that has already been destroyed. */
    public void abandon() {
        abandoned = true;
        initialized = false;
        backgroundReady = false;
        brushReady = false;
        program = null;
        maskProgram = null;
        affordanceMaskProgram = null;
        backgroundTexture = 0;
        brushTexture = 0;
        maskTexture = 0;
        maskFramebuffer = 0;
        touchLights.clear();
        pendingInput.clear();
    }

    public void release() {
        if (program != null) {
            program.release();
            program = null;
        }
        if (maskProgram != null) {
            maskProgram.release();
            maskProgram = null;
        }
        if (affordanceMaskProgram != null) {
            affordanceMaskProgram.release();
            affordanceMaskProgram = null;
        }
        deleteTexture(backgroundTexture);
        deleteTexture(brushTexture);
        deleteTexture(maskTexture);
        if (maskFramebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, new int[] {maskFramebuffer}, 0);
        }
        backgroundTexture = 0;
        brushTexture = 0;
        maskTexture = 0;
        maskFramebuffer = 0;
        initialized = false;
        backgroundReady = false;
        brushReady = false;
        touchLights.clear();
        pendingInput.clear();
    }

    private void processInput() {
        for (int i = 0; i < pendingInput.size(); ++i) {
            InputEvent event = pendingInput.get(i);
            setLightPosition(event.x, event.y);
            if (event.action == ACTION_DOWN || event.action == ACTION_MOVE) {
                affordanceActive = false;
                releaseBounceActive = false;
                if (touchEmitClock >= TOUCH_NEXT_TERM_SECONDS) {
                    touchLights.add(new LightState(event.x, event.y));
                    touchEmitClock = 0.0f;
                }
            } else if (event.action == ACTION_UP
                    || event.action == MotionEvent.ACTION_CANCEL) {
                startReleaseBounce();
            }
        }
        pendingInput.clear();
    }

    private void setLightPosition(float x, float y) {
        lightX = x / width * 2.0f - 1.0f;
        lightY = -(y / height * 2.0f - 1.0f);
    }

    private void startReleaseBounce() {
        releaseBounceActive = true;
        releaseBounceAge = 0.0f;
        releaseBounceStartX = lightX;
        releaseBounceStartY = lightY;
        releaseBounceTargetX = lightX > 0.0f ? lightX - 0.2f : lightX + 0.2f;
        releaseBounceTargetY = lightY > 0.0f ? lightY - 0.2f : lightY + 0.2f;
    }

    private void updateSimulation(float elapsed) {
        touchEmitClock += elapsed;
        for (int i = touchLights.size() - 1; i >= 0; --i) {
            LightState light = touchLights.get(i);
            light.age += elapsed;
            if (light.age > TOUCH_LIFETIME_SECONDS) {
                touchLights.remove(i);
            }
        }
        if (releaseBounceActive) {
            releaseBounceAge += elapsed;
            if (releaseBounceAge < TOUCH_GROW_SECONDS) {
                float amount = cosineInOut(releaseBounceAge / TOUCH_GROW_SECONDS);
                lightX = mix(releaseBounceStartX, releaseBounceTargetX, amount);
                lightY = mix(releaseBounceStartY, releaseBounceTargetY, amount);
            } else if (releaseBounceAge < TOUCH_LIFETIME_SECONDS) {
                float amount = cosineInOut((releaseBounceAge - TOUCH_GROW_SECONDS)
                        / (TOUCH_LIFETIME_SECONDS - TOUCH_GROW_SECONDS));
                lightX = mix(releaseBounceTargetX, releaseBounceStartX, amount);
                lightY = mix(releaseBounceTargetY, releaseBounceStartY, amount);
            } else {
                lightX = releaseBounceStartX;
                lightY = releaseBounceStartY;
                releaseBounceActive = false;
            }
        }
        if (affordanceActive) {
            affordanceAge += elapsed;
            if (affordanceAge >= AFFORDANCE_SECONDS) {
                affordanceAge = AFFORDANCE_SECONDS;
                affordanceActive = false;
            }
        }
        if (unlockActive) {
            if (unlockFinalFramePending) {
                unlockActive = false;
                unlockFinalFramePending = false;
            } else {
                unlockAge += elapsed;
                if (unlockAge >= UNLOCK_SECONDS) {
                    unlockAge = UNLOCK_SECONDS;
                    unlockFinalFramePending = true;
                }
            }
        }
    }

    /**
     * Adaptive-only copy of the recovered state advance.  Keeping this separate leaves the
     * default 60 Hz updateSimulation() instruction-for-instruction identical to its baseline.
     */
    private void updateAdaptiveSimulation(float elapsed) {
        touchEmitClock += elapsed;
        for (int i = touchLights.size() - 1; i >= 0; --i) {
            LightState light = touchLights.get(i);
            light.age += elapsed;
            if (light.age > TOUCH_LIFETIME_SECONDS) {
                touchLights.remove(i);
            }
        }
        if (releaseBounceActive) {
            releaseBounceAge += elapsed;
            if (releaseBounceAge < TOUCH_GROW_SECONDS) {
                float amount = cosineInOut(releaseBounceAge / TOUCH_GROW_SECONDS);
                lightX = mix(releaseBounceStartX, releaseBounceTargetX, amount);
                lightY = mix(releaseBounceStartY, releaseBounceTargetY, amount);
            } else if (releaseBounceAge < TOUCH_LIFETIME_SECONDS) {
                float amount = cosineInOut((releaseBounceAge - TOUCH_GROW_SECONDS)
                        / (TOUCH_LIFETIME_SECONDS - TOUCH_GROW_SECONDS));
                lightX = mix(releaseBounceTargetX, releaseBounceStartX, amount);
                lightY = mix(releaseBounceTargetY, releaseBounceStartY, amount);
            } else {
                lightX = releaseBounceStartX;
                lightY = releaseBounceStartY;
                releaseBounceActive = false;
            }
        }
        if (affordanceActive) {
            affordanceAge += elapsed;
            if (affordanceAge >= AFFORDANCE_SECONDS) {
                affordanceAge = AFFORDANCE_SECONDS;
                affordanceActive = false;
            }
        }
        if (unlockActive) {
            if (unlockFinalFramePending) {
                if (!adaptiveFinalFrameHold.advance(elapsed)) {
                    unlockActive = false;
                    unlockFinalFramePending = false;
                    adaptiveFinalFrameHold.reset();
                }
            } else {
                unlockAge += elapsed;
                if (unlockAge >= UNLOCK_SECONDS) {
                    unlockAge = UNLOCK_SECONDS;
                    unlockFinalFramePending = true;
                    // The crossing frame is the first terminal target presentation.
                    adaptiveFinalFrameHold.begin();
                }
            }
        }
    }

    private void clearPlanes() {
        if (planes == null) {
            return;
        }
        for (PlaneState plane : planes) {
            plane.alpha = 0.0f;
            plane.from = 0.0f;
            plane.target = 0.0f;
            plane.age = 0.0f;
            plane.animating = false;
            writePlaneAlpha(plane);
        }
    }

    private void writePlaneAlpha(PlaneState plane) {
        int end = plane.firstVertex + plane.vertexCount;
        for (int vertex = plane.firstVertex; vertex < end; ++vertex) {
            alphaValues[vertex] = plane.alpha;
        }
        alphaBufferDirty = true;
    }

    private void renderComposite() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (!backgroundReady || isIdle()) {
            return;
        }
        renderMask();

        float renderLightX = lightX;
        float renderLightY = lightY;
        // Stock keeps the composite/glint multiplier at 1.0 for normal touch.
        // TOUCH_BRIGHTNESS_ORACLE belongs only to the LightBrush mask below.
        float brightness = AFFORDANCE_UNLOCK_BRIGHTNESS;
        if (affordanceActive) {
            float progress = cosineInOut(Math.min(1.0f,
                    affordanceAge / AFFORDANCE_SECONDS));
            renderLightX = 1.0f - 2.0f * progress;
            renderLightY = 1.0f - 2.0f * progress;
            brightness = AFFORDANCE_UNLOCK_BRIGHTNESS;
        } else if (unlockActive) {
            brightness = AFFORDANCE_UNLOCK_BRIGHTNESS;
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_BLEND);
        program.use();
        GLES20.glUniform1f(program.uniform("uWidthRatio"), widthRatio);
        GLES20.glUniform1f(program.uniform("uHeightRatio"), heightRatio);
        GLES20.glUniform1f(program.uniform("uBrightness"), brightness);
        GLES20.glUniform3f(program.uniform("uLightPosition"),
                renderLightX, renderLightY, 1.0f);
        GLES20.glUniform1f(program.uniform("uShift"), NORMAL_IMAGE_SHIFT);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture);
        GLES20.glUniform1i(program.uniform("uBGTexture"), 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture);
        GLES20.glUniform1i(program.uniform("uMaskTexture"), 1);

        attribute(program, "aPosition", 3, positions);
        attribute(program, "aUV", 2, uvs);
        attribute(program, "aNormal", 3, normals);
        attribute(program, "aAuxNormal", 3, auxNormals);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount);
        disableAttribute(program, "aPosition");
        disableAttribute(program, "aUV");
        disableAttribute(program, "aNormal");
        disableAttribute(program, "aAuxNormal");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        checkGl("renderComposite");
    }

    private void renderMask() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, maskFramebuffer);
        GLES20.glViewport(0, 0, maskWidth, maskHeight);
        float clear = unlockActive ? UNLOCK_MASK_TARGET
                * Math.min(1.0f, unlockAge / UNLOCK_SECONDS) : 0.0f;
        GLES20.glClearColor(clear, clear, clear, clear);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        if (affordanceActive) {
            drawAffordanceMask();
        }
        for (int i = 0; i < touchLights.size(); ++i) {
            drawTouchMask(touchLights.get(i));
        }

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
    }

    private void drawTouchMask(LightState light) {
        float age = Math.min(TOUCH_LIFETIME_SECONDS, light.age);
        float peakBrightness = TOUCH_REPEAT_COUNT / TOUCH_LIFETIME_SECONDS;
        float brightness;
        float size;
        if (age < TOUCH_GROW_SECONDS) {
            float amount = cosineInOut(age / TOUCH_GROW_SECONDS);
            brightness = peakBrightness * amount;
            size = mix(TOUCH_RADIUS_ORACLE * 0.5f, TOUCH_RADIUS_ORACLE, amount);
        } else {
            float amount = cosineInOut((age - TOUCH_GROW_SECONDS)
                    / (TOUCH_LIFETIME_SECONDS - TOUCH_GROW_SECONDS));
            brightness = peakBrightness * (1.0f - amount);
            size = mix(TOUCH_RADIUS_ORACLE, TOUCH_RADIUS_ORACLE * 0.5f, amount);
        }

        maskProgram.use();
        GLES20.glUniform2f(maskProgram.uniform("uCenter"),
                light.x / width * 2.0f - 1.0f,
                1.0f - light.y / height * 2.0f);
        float maximum = Math.max(maskWidth, maskHeight);
        GLES20.glUniform2f(maskProgram.uniform("uScale"),
                size * maximum / maskWidth, size * maximum / maskHeight);
        GLES20.glUniform1f(maskProgram.uniform("uBrightness"),
                brightness * TOUCH_BRIGHTNESS_ORACLE);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, brushTexture);
        GLES20.glUniform1i(maskProgram.uniform("uLightTexture"), 0);
        attribute(maskProgram, "aPosition", 3, maskQuadPositions);
        attribute(maskProgram, "aUV", 2, maskQuadUvs);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        disableAttribute(maskProgram, "aPosition");
        disableAttribute(maskProgram, "aUV");
    }

    private void drawAffordanceMask() {
        float time = cosineInOut(Math.min(1.0f, affordanceAge / AFFORDANCE_SECONDS));
        float maximum = Math.max(maskWidth, maskHeight);
        float centerX = maximum * AFFORDANCE_CENTER_X;
        float centerY = maximum * AFFORDANCE_CENTER_Y;
        float stroke = maximum * AFFORDANCE_STROKE;
        float start = distance(centerX, centerY, maskWidth, 0.0f);
        float end = distance(centerX, centerY, 0.0f, maskHeight) + stroke;
        float currentLength = mix(start, end, time);

        affordanceMaskProgram.use();
        GLES20.glUniform2f(affordanceMaskProgram.uniform("uSize"), maskWidth, maskHeight);
        GLES20.glUniform2f(affordanceMaskProgram.uniform("uCenter"), centerX, centerY);
        GLES20.glUniform1f(affordanceMaskProgram.uniform("uStroke"), stroke);
        GLES20.glUniform1f(affordanceMaskProgram.uniform("uCurrentLength"), currentLength);
        attribute(affordanceMaskProgram, "aPosition", 3, maskQuadPositions);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        disableAttribute(affordanceMaskProgram, "aPosition");
    }

    private void buildMeshStreams(BrilliantCutStockGeometry.Mesh stock) {
        int vertexCount = stock.vertexCount;
        int planeCount = stock.planeVertexCounts.length;
        planes = new PlaneState[planeCount];
        alphaValues = new float[vertexCount];
        float[] uvValues = new float[vertexCount * 2];
        float[] normalValues = new float[vertexCount * 3];
        float[] auxValues = new float[vertexCount * 3];
        Drand48 random = new Drand48(System.currentTimeMillis() / 1000L);
        int precedingPlanes = 0;
        for (int type = 0; type < stock.geometryType; ++type) {
            precedingPlanes += BrilliantCutStockGeometry.get(type).planeVertexCounts.length;
        }
        for (int draw = 0; draw < precedingPlanes * 4; ++draw) {
            random.nextSignedUnit();
        }

        for (int index = 0; index < planeCount; ++index) {
            int first = stock.planeFirstVertices[index] & 0xffff;
            int count = stock.planeVertexCounts[index] & 0xff;
            float[] center = areaWeightedCenter(stock.xyz, first, count);
            PlaneState plane = new PlaneState(first, count, center[0], center[1]);
            planes[index] = plane;

            float[] aux = normalized(random.nextSignedUnit() - center[0],
                    random.nextSignedUnit() - center[1], 3.0f - center[2]);
            float[] normal = normalized(random.nextSignedUnit() - center[0],
                    random.nextSignedUnit() - center[1], 15.0f - center[2]);
            for (int vertex = first; vertex < first + count; ++vertex) {
                int xyz = vertex * 3;
                int uv = vertex * 2;
                float clipX = stock.xyz[xyz] / widthRatio;
                float clipY = stock.xyz[xyz + 1] / heightRatio;
                uvValues[uv] = clipX * 0.5f + 0.5f;
                uvValues[uv + 1] = 0.5f - clipY * 0.5f;
                normalValues[xyz] = normal[0];
                normalValues[xyz + 1] = normal[1];
                normalValues[xyz + 2] = normal[2];
                auxValues[xyz] = aux[0];
                auxValues[xyz + 1] = aux[1];
                auxValues[xyz + 2] = aux[2];
            }
        }
        positions = directFloats(stock.xyz);
        uvs = directFloats(uvValues);
        normals = directFloats(normalValues);
        auxNormals = directFloats(auxValues);
        vertexAlphas = directFloats(alphaValues);
        alphaBufferDirty = false;
    }

    /** Plane::CalcCenter: area-weighted centroid of the already-triangulated stream. */
    private static float[] areaWeightedCenter(float[] xyz, int first, int count) {
        float centerX = 0.0f;
        float centerY = 0.0f;
        float centerZ = 0.0f;
        float totalArea = 0.0f;
        for (int vertex = first; vertex < first + count; vertex += 3) {
            int a = vertex * 3;
            int b = a + 3;
            int c = a + 6;
            float ab = distance3(xyz, a, b);
            float bc = distance3(xyz, b, c);
            float ca = distance3(xyz, c, a);
            float semiperimeter = (ab + bc + ca) * 0.5f;
            float area = (float) Math.sqrt(Math.max(0.0f, semiperimeter
                    * (semiperimeter - ab) * (semiperimeter - bc)
                    * (semiperimeter - ca)));
            centerX += ((xyz[a] + xyz[b] + xyz[c]) / 3.0f) * area;
            centerY += ((xyz[a + 1] + xyz[b + 1] + xyz[c + 1]) / 3.0f) * area;
            centerZ += ((xyz[a + 2] + xyz[b + 2] + xyz[c + 2]) / 3.0f) * area;
            totalArea += area;
        }
        if (totalArea <= 0.0f) {
            return new float[] {0.0f, 0.0f, 0.0f};
        }
        return new float[] {centerX / totalArea, centerY / totalArea, centerZ / totalArea};
    }

    private static float distance3(float[] values, int a, int b) {
        float x = values[a] - values[b];
        float y = values[a + 1] - values[b + 1];
        float z = values[a + 2] - values[b + 2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static float[] normalized(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / length, y / length, z / length};
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        float x = x0 - x1;
        float y = y0 - y1;
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float cosineInOut(float value) {
        return 0.5f - 0.5f * (float) Math.cos(Math.PI * value);
    }

    private static int createTexture() {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return texture[0];
    }

    private static int createRenderTexture(int width, int height) {
        int texture = createTexture();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        checkGl("create mask texture");
        return texture;
    }

    private static int createFramebuffer(int texture) {
        int[] framebuffer = new int[1];
        GLES20.glGenFramebuffers(1, framebuffer, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteFramebuffers(1, framebuffer, 0);
            throw new IllegalStateException("Brilliant Cut mask framebuffer incomplete: 0x"
                    + Integer.toHexString(status));
        }
        return framebuffer[0];
    }

    private static void deleteTexture(int texture) {
        if (texture != 0) {
            GLES20.glDeleteTextures(1, new int[] {texture}, 0);
        }
    }

    private static void attribute(Program program, String name, int size, FloatBuffer buffer) {
        int location = program.attribute(name);
        if (location >= 0) {
            buffer.position(0);
            GLES20.glEnableVertexAttribArray(location);
            GLES20.glVertexAttribPointer(location, size, GLES20.GL_FLOAT, false, 0, buffer);
        }
    }

    private static void disableAttribute(Program program, String name) {
        int location = program.attribute(name);
        if (location >= 0) {
            GLES20.glDisableVertexAttribArray(location);
        }
    }

    private static FloatBuffer directFloats(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    private static void drainErrors() {
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            // Drain stale context errors before an operation whose result is checked.
        }
    }

    private static void checkGl(String operation) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(operation + " GL error 0x"
                    + Integer.toHexString(error));
        }
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

    private static final class LightState {
        final float x;
        final float y;
        float age;

        LightState(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Adaptive-only terminal phase.  Stock clears the pending flag on its next 16 ms tick;
     * high-refresh rendering instead retains the target for the equivalent recovered time.
     */
    static final class AdaptiveFinalFrameHold {
        private float elapsed;

        void begin() {
            elapsed = 0.0f;
        }

        boolean advance(float elapsedSeconds) {
            elapsed += Math.max(0.0f, elapsedSeconds);
            return elapsed < STOCK_STEP_SECONDS;
        }

        void reset() {
            elapsed = 0.0f;
        }
    }

    private static final class PlaneState {
        final int firstVertex;
        final int vertexCount;
        final float centerX;
        final float centerY;
        float alpha;
        float from;
        float target;
        float age;
        float duration;
        boolean animating;

        PlaneState(int firstVertex, int vertexCount, float centerX, float centerY) {
            this.firstVertex = firstVertex;
            this.vertexCount = vertexCount;
            this.centerX = centerX;
            this.centerY = centerY;
        }
    }

    /** POSIX drand48/lrand48 state used by the original AddPlane implementation. */
    private static final class Drand48 {
        private static final long MASK = (1L << 48) - 1L;
        private long state;

        Drand48(long seed) {
            state = ((seed & 0xffffffffL) << 16) | 0x330eL;
        }

        float nextSignedUnit() {
            state = (state * 0x5deece66dL + 0xbL) & MASK;
            long lrand48 = state >>> 17;
            return (float) (lrand48 * 9.313226e-10 - 1.0);
        }
    }

    private static final class Program {
        final int id;

        Program(String vertexSource, String fragmentSource) {
            int vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            id = GLES20.glCreateProgram();
            GLES20.glAttachShader(id, vertex);
            GLES20.glAttachShader(id, fragment);
            GLES20.glLinkProgram(id);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, linked, 0);
            String log = GLES20.glGetProgramInfoLog(id);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            if (linked[0] == 0) {
                GLES20.glDeleteProgram(id);
                throw new IllegalStateException("Brilliant Cut program link failed: " + log);
            }
        }

        void use() {
            GLES20.glUseProgram(id);
        }

        int attribute(String name) {
            return GLES20.glGetAttribLocation(id, name);
        }

        int uniform(String name) {
            return GLES20.glGetUniformLocation(id, name);
        }

        void release() {
            GLES20.glDeleteProgram(id);
        }

        private static int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("Brilliant Cut shader compile failed: " + log);
            }
            return shader;
        }
    }

    /* Literal Samsung normal-image glare math with transparent final composition. */
    private static final String VERTEX_SHADER =
            "precision highp float;\n"
            + "attribute vec3 aPosition;\n"
            + "attribute vec2 aUV;\n"
            + "attribute vec3 aNormal;\n"
            + "attribute vec3 aAuxNormal;\n"
            + "uniform float uWidthRatio;\n"
            + "uniform float uHeightRatio;\n"
            + "uniform float uBrightness;\n"
            + "uniform float uShift;\n"
            + "uniform vec3 uLightPosition;\n"
            + "varying vec3 vAuxNormal;\n"
            + "varying vec2 vUV;\n"
            + "varying vec4 vGlare;\n"
            + "varying float vShift;\n"
            + "void main() {\n"
            + " vec3 eyeDirection=vec3(0.0,0.0,1.0);\n"
            + " vec3 position=vec3(aPosition.x/uWidthRatio,aPosition.y/uHeightRatio,aPosition.z);\n"
            + " vAuxNormal=aAuxNormal; vUV=aUV;\n"
            + " vec3 lightDirection=normalize(uLightPosition-position);\n"
            + " vec3 reflected=normalize(-reflect(lightDirection,aNormal));\n"
            + " float specular=0.7*pow(max(0.0,dot(reflected,eyeDirection)),10.0);\n"
            + " vec3 reflectedAmb=normalize(-reflect(lightDirection,aAuxNormal));\n"
            + " float specularAmb=0.7*pow(max(0.0,dot(reflectedAmb,eyeDirection)),10.0);\n"
            + " vec3 color=vec3(1.0)*(0.5*specularAmb+0.5*specular)*uBrightness;\n"
            + " vGlare=clamp(vec4(color,1.0)*1.5,vec4(0.0),vec4(1.0));\n"
            + " vShift=(0.3*vGlare.x+0.5*vGlare.y+0.2*vGlare.z)*uShift;\n"
            + " gl_Position=vec4(position,1.0);\n"
            + "}\n";

    private static final String OVERLAY_FRAGMENT_SHADER =
            "precision highp float;\n"
            + "uniform sampler2D uBGTexture;\n"
            + "uniform sampler2D uMaskTexture;\n"
            + "varying vec3 vAuxNormal;\n"
            + "varying vec2 vUV;\n"
            + "varying vec4 vGlare;\n"
            + "varying float vShift;\n"
            + "void main() {\n"
            + " float maskAlpha=texture2D(uMaskTexture,vec2(vUV.x,1.0-vUV.y)).r;\n"
            + " if(maskAlpha<=0.0) discard;\n"
            + " vec3 base=texture2D(uBGTexture,vUV).rgb;\n"
            + " vec2 texCoord=vUV+vAuxNormal.xy*vShift*maskAlpha;\n"
            + " vec3 shifted=texture2D(uBGTexture,texCoord).rgb;\n"
            + " vec3 premul=shifted-(1.0-maskAlpha)*base+maskAlpha*vGlare.xyz;\n"
            + " gl_FragColor=vec4(max(premul,vec3(0.0)),maskAlpha);\n"
            + "}\n";

    private static final String MASK_VERTEX_SHADER =
            "precision mediump float;\n"
            + "attribute vec3 aPosition;\n"
            + "attribute vec2 aUV;\n"
            + "uniform vec2 uCenter;\n"
            + "uniform vec2 uScale;\n"
            + "varying vec2 vUV;\n"
            + "void main(){\n"
            + " vUV=aUV;\n"
            + " gl_Position=vec4(uCenter+aPosition.xy*uScale,aPosition.z,1.0);\n"
            + "}\n";

    private static final String MASK_FRAGMENT_SHADER =
            "precision mediump float;\n"
            + "uniform float uBrightness;\n"
            + "uniform sampler2D uLightTexture;\n"
            + "varying vec2 vUV;\n"
            + "void main(){\n"
            + " vec4 light=texture2D(uLightTexture,vUV);\n"
            + " gl_FragColor=vec4(1.0,1.0,1.0,min(light.a*uBrightness,1.0));\n"
            + "}\n";

    private static final String AFFORDANCE_MASK_VERTEX_SHADER =
            "precision highp float;\n"
            + "attribute vec3 aPosition;\n"
            + "uniform vec2 uSize;\n"
            + "varying vec3 vPosition;\n"
            + "void main(){\n"
            + " vPosition=vec3((aPosition.x*0.5+0.5)*uSize.x,"
            + "(-aPosition.y*0.5+0.5)*uSize.y,aPosition.z);\n"
            + " gl_Position=vec4(aPosition,1.0);\n"
            + "}\n";

    private static final String AFFORDANCE_MASK_FRAGMENT_SHADER =
            "precision highp float;\n"
            + "uniform vec2 uCenter;\n"
            + "uniform float uStroke;\n"
            + "uniform float uCurrentLength;\n"
            + "varying vec3 vPosition;\n"
            + "void main(){\n"
            + " float minDistance=uCurrentLength-uStroke;\n"
            + " float blurredStroke=uStroke*0.3;\n"
            + " float dist=length(uCenter-vPosition.xy);\n"
            + " float alpha=0.0;\n"
            + " if(minDistance<dist && dist<minDistance+blurredStroke)"
            + " alpha=((dist-minDistance)/blurredStroke)*0.12;\n"
            + " else if(minDistance+blurredStroke<dist"
            + " && dist<uCurrentLength-blurredStroke) alpha=0.12;\n"
            + " else if(uCurrentLength-blurredStroke<dist"
            + " && dist<uCurrentLength) alpha=(-(dist-uCurrentLength)/blurredStroke)*0.12;\n"
            + " else discard;\n"
            + " gl_FragColor=vec4(1.0,1.0,1.0,alpha);\n"
            + "}\n";
}
