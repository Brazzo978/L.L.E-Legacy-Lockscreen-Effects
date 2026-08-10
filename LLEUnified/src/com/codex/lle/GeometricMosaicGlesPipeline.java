package com.codex.lle;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

/**
 * Literal GLES2 multi-pass reconstruction of the Note 4 Geometric Mosaic scene.
 *
 * <p>This class deliberately owns no {@code View} or Android lifecycle state. All methods must
 * be called on the GL thread while its context is current. Input coordinates are Android-normalized
 * to [0, 1], with (0, 0) at the top-left; conversion to the native clip space is internal.</p>
 */
public final class GeometricMosaicGlesPipeline {
    private static final String TAG = "LLE64GeometricGL";

    private static final int MASK_COLUMNS = 12;
    private static final int MASK_ROWS = 21;
    private static final int MAX_TOUCHES = 100;
    // FUN_16084 emits four independent triangle faces (12 vertices) per grid cell.
    private static final int MASK_VERTEX_COUNT = MASK_COLUMNS * MASK_ROWS * 12;

    private static final float TOUCH_GROW_SECONDS = 0.15f;
    private static final float TOUCH_SHRINK_SECONDS = 0.60f;
    private static final float TOUCH_START_RADIUS = 0.30f;
    private static final float TOUCH_PEAK_RADIUS = 0.80f;
    private static final float TOUCH_SAMPLE_THRESHOLD = 0.0085f;
    private static final float UNLOCK_RADIUS = 5.0f;
    private static final float UNLOCK_EXPAND_SECONDS = 0.40f;
    private static final float UNLOCK_FADE_SECONDS = 0.60f;
    private static final float UNLOCK_TOTAL_SECONDS =
            UNLOCK_EXPAND_SECONDS + UNLOCK_FADE_SECONDS;
    private static final float AFFORDANCE_SECONDS = 2.0f;
    private static final float AFFORDANCE_RADIUS_FROM = -0.8f;
    private static final float AFFORDANCE_RADIUS_TO = 3.0f;

    private static final float BASE_RADIUS = 2.0f / 6.0f * 1.25f;
    private static final float[] RING_FROM = {
            BASE_RADIUS * 0.60f,
            BASE_RADIUS * 0.20f,
            0.0f,
            BASE_RADIUS * 0.60f,
            0.0f
    };
    private static final float[] RING_DELAY = {0.0f, 0.0f, 0.60f, 0.0f, 0.0f};
    private static final float[] RING_END = {1.20f, 2.40f, 3.60f, 1.20f, 3.00f};

    private static final float[][] PALETTE = {
            {0.5176471f, 0.4392157f, 1.0000000f}, // #8470FF
            {0.6784314f, 1.0000000f, 0.1843137f}, // #ADFF2F
            {1.0000000f, 0.8431373f, 0.0000000f}, // #FFD700
            {0.8039216f, 0.3607843f, 0.3607843f}, // #CD5C5C
            {0.8039216f, 0.7137255f, 0.7568628f}, // #CDB6C1
            {0.5137255f, 0.0431373f, 1.0000000f}, // #830BFF
            {0.2627451f, 0.8039216f, 0.5019608f}, // #43CD80
            {1.0000000f, 0.7529412f, 0.7529412f}, // #FFC0C0
            {0.8039216f, 0.5215687f, 0.2470588f}, // #CD853F
            {1.0000000f, 0.1882353f, 0.1882353f}  // #FF3030
    };

    private final TouchRecord[] touches = new TouchRecord[MAX_TOUCHES];
    private final int[] freeTouchIndices = new int[MAX_TOUCHES];
    private final RingLayer[] rings = new RingLayer[5];
    private final FloatBuffer maskVertices = directFloats(MASK_VERTEX_COUNT * 3);
    private final FloatBuffer fullscreen = directFloats(new float[] {
            -1.0f, -1.0f, 0.0f, 1.0f,
             1.0f, -1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 0.0f
    });

    private Program maskProgram;
    private Program circle3Program;
    private Program circle2Program;
    private Program blurProgram;
    private Program finalProgram;
    private RenderTarget maskTarget;
    private RenderTarget circle3Target;
    private RenderTarget circle2Target;
    private RenderTarget blurTarget;
    private CircleMesh circle3Mesh;
    private CircleMesh circle2Mesh;
    private int backgroundTexture;
    private int originNoiseTexture;
    private int width;
    private int height;
    private int gridColumns = MASK_COLUMNS;
    private int gridRows = MASK_ROWS;
    private boolean initialized;
    private boolean backgroundReady;
    private boolean blurDirty;
    private int freeTouchCount;
    private float lastTouchX;
    private float lastTouchY;
    private boolean hasLastTouch;
    private TouchRecord currentTouch;
    private double ringStartSeconds = -1000.0;
    private boolean ringsActive;
    private boolean specialActive;
    private double specialStartSeconds = -1000.0;
    private float specialCenterX;
    private float specialCenterY;
    private double unlockStartSeconds = -1000.0;
    private float unlockAlphaFrom = 1.0f;
    private float sceneAlpha = 1.0f;
    // Native_init supplies these defaults; setMaskDistanceScale remains available when a host's
    // native viewport differs from its GL surface.
    private float maskScaleX = 1.0f;
    private float maskScaleY = 1.0f;
    private long colorSeed = System.currentTimeMillis() / 1000L;

    public GeometricMosaicGlesPipeline() {
        for (int i = 0; i < touches.length; ++i) {
            touches[i] = new TouchRecord(i);
        }
        for (int i = 0; i < rings.length; ++i) {
            rings[i] = new RingLayer(i);
        }
        reset();
    }

    /** Creates or recreates every context-bound resource. */
    public void initialize(int surfaceWidth, int surfaceHeight) {
        release();
        width = Math.max(1, surfaceWidth);
        height = Math.max(1, surfaceHeight);
        gridColumns = width <= height ? MASK_COLUMNS : MASK_ROWS;
        gridRows = width <= height ? MASK_ROWS : MASK_COLUMNS;
        // Native_init writes these aspect-normalized distance multipliers and the Geometric
        // constructor copies them into scene+0xdc/e0 (raw f92c..f968, 1752c/17554).
        maskScaleX = Math.max(width / (float) height, 1.0f);
        maskScaleY = Math.max(height / (float) width, 1.0f);
        maskProgram = new Program(MASK_VERTEX_SHADER, MASK_FRAGMENT_SHADER);
        circle3Program = new Program(CIRCLE_VERTEX_SHADER, CIRCLE3_FRAGMENT_SHADER);
        circle2Program = new Program(CIRCLE_VERTEX_SHADER, CIRCLE2_FRAGMENT_SHADER);
        blurProgram = new Program(TEXTURE_VERTEX_SHADER, BLUR_FRAGMENT_SHADER);
        finalProgram = new Program(TEXTURE_VERTEX_SHADER, FINAL_FRAGMENT_SHADER);

        maskTarget = new RenderTarget(Math.max(1, width >> 2), Math.max(1, height >> 2),
                false, true);
        int shortSide = Math.min(width, height);
        int longSide = Math.max(width, height);
        int circleWidth = Math.max(1, ((shortSide / 12) * 18) >> 2);
        int circleHeight = Math.max(1, ((longSide / 21) * 30) >> 2);
        circle3Target = new RenderTarget(circleWidth, circleHeight, true, true);
        circle2Target = new RenderTarget(circleWidth, circleHeight, true, true);
        // Native RT 0x90: the blurred colour origin is deliberately only 12 x 21.
        blurTarget = new RenderTarget(gridColumns, gridRows, false, false);
        Random sceneRandom = new Random(colorSeed);
        // The original consumes one shared stream: 45 + 30 palette draws, then 252 bytes.
        circle3Mesh = CircleMesh.firstLattice(sceneRandom, 3);
        circle2Mesh = CircleMesh.secondLattice(sceneRandom, 2);

        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        backgroundTexture = texture[0];
        bindTextureParameters(backgroundTexture, false, true);
        ByteBuffer empty = ByteBuffer.allocateDirect(4);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, empty);
        GLES20.glGenTextures(1, texture, 0);
        originNoiseTexture = texture[0];
        bindTextureParameters(originNoiseTexture, false, false);
        ByteBuffer originNoise = ByteBuffer.allocateDirect(gridColumns * gridRows);
        for (int i = 0; i < gridColumns * gridRows; ++i) {
            originNoise.put((byte) sceneRandom.nextInt(256));
        }
        originNoise.position(0);
        // Texture 0x8e is one byte per cell and the final shader samples its alpha channel.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_ALPHA,
                gridColumns, gridRows, 0, GLES20.GL_ALPHA,
                GLES20.GL_UNSIGNED_BYTE, originNoise);
        backgroundReady = false;
        blurDirty = true;
        initialized = true;
        checkGl("initialize");
    }

    /** Reallocates size-dependent targets. The caller should re-upload its background afterwards. */
    public void resize(int surfaceWidth, int surfaceHeight) {
        int nextWidth = Math.max(1, surfaceWidth);
        int nextHeight = Math.max(1, surfaceHeight);
        if (!initialized || nextWidth != width || nextHeight != height) {
            initialize(nextWidth, nextHeight);
        }
    }

    public void setColorSeed(long seed) {
        colorSeed = seed;
        if (initialized) {
            Random sceneRandom = new Random(colorSeed);
            circle3Mesh = CircleMesh.firstLattice(sceneRandom, 3);
            circle2Mesh = CircleMesh.secondLattice(sceneRandom, 2);
            ByteBuffer originNoise = ByteBuffer.allocateDirect(gridColumns * gridRows);
            for (int i = 0; i < gridColumns * gridRows; ++i) {
                originNoise.put((byte) sceneRandom.nextInt(256));
            }
            originNoise.position(0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, originNoiseTexture);
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0,
                    gridColumns, gridRows, GLES20.GL_ALPHA, GLES20.GL_UNSIGNED_BYTE,
                    originNoise);
        }
    }

    /**
     * Supplies the two base-renderer distance multipliers at scene+0xdc/e0.
     * initialize() derives the recovered Native_init values from the active viewport. This
     * override is only for a host whose native viewport differs from the GL surface.
     */
    public void setMaskDistanceScale(float x, float y) {
        maskScaleX = Math.max(0.0001f, x);
        maskScaleY = Math.max(0.0001f, y);
    }

    public boolean uploadBackground(Bitmap bitmap) {
        if (!initialized || bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        int error = GLES20.glGetError();
        backgroundReady = error == GLES20.GL_NO_ERROR;
        blurDirty = backgroundReady;
        if (backgroundReady) {
            // The native constructor pre-renders RT 0x90 as soon as the source texture exists.
            renderBlur();
            blurDirty = false;
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, width, height);
        }
        if (!backgroundReady) {
            Log.e(TAG, "background upload failed: 0x" + Integer.toHexString(error));
        }
        return backgroundReady;
    }

    public void reset() {
        for (TouchRecord touch : touches) {
            touch.active = false;
        }
        freeTouchCount = touches.length;
        for (int i = 0; i < freeTouchIndices.length; ++i) {
            // The native free stack is initialized with 0..99 and allocations pop from its end.
            freeTouchIndices[i] = i;
        }
        hasLastTouch = false;
        currentTouch = null;
        ringStartSeconds = -1000.0;
        ringsActive = false;
        specialActive = false;
        specialStartSeconds = -1000.0;
        specialCenterX = 0.0f;
        specialCenterY = 0.0f;
        unlockStartSeconds = -1000.0;
        sceneAlpha = 1.0f;
    }

    /** Adds one native-style mask record. x/y are normalized, not clip coordinates. */
    public boolean addTouch(float x, float y, long frameTimeNanos) {
        x = clamp(x, 0.0f, 1.0f);
        y = clamp(y, 0.0f, 1.0f);
        if (hasLastTouch) {
            float dx = x - lastTouchX;
            float dy = y - lastTouchY;
            if ((float) Math.sqrt(dx * dx + dy * dy) < TOUCH_SAMPLE_THRESHOLD) {
                return false;
            }
        }
        double now = seconds(frameTimeNanos);
        if (freeTouchCount == 0) {
            return false;
        }
        TouchRecord record = touches[freeTouchIndices[--freeTouchCount]];
        record.begin(2.0f * x - 1.0f, 1.0f - 2.0f * y, now);
        currentTouch = record;
        lastTouchX = x;
        lastTouchY = y;
        hasLastTouch = true;
        // Primary DOWN and the special hint share the native scene+0x131 ring latch.
        startRingsIfInactive(now);
        sceneAlpha = 1.0f;
        unlockStartSeconds = -1000.0;
        return true;
    }

    /** Lets the host re-anchor MOVE after a multi-touch gate without emitting a mask record. */
    public void realignTouch(float x, float y) {
        lastTouchX = clamp(x, 0.0f, 1.0f);
        lastTouchY = clamp(y, 0.0f, 1.0f);
        hasLastTouch = true;
    }

    public void endTouch() {
        hasLastTouch = false;
    }

    /** Starts the stock two-second scene+0xe4 annular lock-screen affordance. */
    public boolean addAffordance(float x, float y, long frameTimeNanos) {
        x = clamp(x, 0.0f, 1.0f);
        y = clamp(y, 0.0f, 1.0f);
        double now = seconds(frameTimeNanos);
        // Common Native_draw event type 1 clears all scene/touch state before vtable +0x08.
        reset();
        specialCenterX = x - 0.5f;
        specialCenterY = 0.5f - y;
        specialStartSeconds = now;
        specialActive = true;
        startRingsIfInactive(now);
        return true;
    }

    /**
     * Expands a terminal mask record at the final gesture coordinates, then fades the complete
     * scene. The terminal record is deliberately rearmed even when the normal trail record has
     * already aged out: a long hold or a sub-threshold final MOVE must not turn unlock into a
     * no-op.
     */
    public void unlock(long frameTimeNanos) {
        float x = hasLastTouch ? lastTouchX
                : (currentTouch != null && currentTouch.active
                        ? (currentTouch.x + 1.0f) * 0.5f : 0.5f);
        float y = hasLastTouch ? lastTouchY
                : (currentTouch != null && currentTouch.active
                        ? (1.0f - currentTouch.y) * 0.5f : 0.5f);
        unlockAt(x, y, frameTimeNanos);
    }

    /** Package seam for callers/tests that have a final coordinate independent of trail sampling. */
    boolean unlockAt(float x, float y, long frameTimeNanos) {
        double now = seconds(frameTimeNanos);
        TouchRecord terminal = currentTouch;
        if (terminal == null || !terminal.active) {
            terminal = obtainTerminalTouch();
        }
        float radius = terminal.active ? terminal.radiusAt(now) : TOUCH_START_RADIUS;
        terminal.begin(2.0f * clamp(x, 0.0f, 1.0f) - 1.0f,
                1.0f - 2.0f * clamp(y, 0.0f, 1.0f), now);
        terminal.unlock(now, radius);
        currentTouch = terminal;
        lastTouchX = clamp(x, 0.0f, 1.0f);
        lastTouchY = clamp(y, 0.0f, 1.0f);
        hasLastTouch = true;
        unlockAlphaFrom = sceneAlpha;
        unlockStartSeconds = now;
        return true;
    }

    /**
     * Runs all native passes into the currently bound default framebuffer.
     *
     * @return true while another animation frame is required.
     */
    public boolean render(long frameTimeNanos) {
        if (!initialized || !backgroundReady) {
            return false;
        }
        double now = seconds(frameTimeNanos);
        updateAnimation(now);
        if (blurDirty) {
            renderBlur();
            blurDirty = false;
        }
        renderMask(now);
        updateRings(now);
        renderCircleTarget(circle3Target, circle3Program, circle3Mesh,
                orderCircle3Layers());
        renderCircleTarget(circle2Target, circle2Program, circle2Mesh,
                orderCircle2Layers());
        renderFinal();
        checkGl("render");
        return isAnimating(now);
    }

    public boolean isInitialized() {
        return initialized;
    }

    /** Drops names from a lost EGL context without issuing deletes in the replacement context. */
    public void abandon() {
        maskProgram = null;
        circle3Program = null;
        circle2Program = null;
        blurProgram = null;
        finalProgram = null;
        maskTarget = null;
        circle3Target = null;
        circle2Target = null;
        blurTarget = null;
        backgroundTexture = 0;
        originNoiseTexture = 0;
        circle3Mesh = null;
        circle2Mesh = null;
        initialized = false;
        backgroundReady = false;
        blurDirty = false;
    }

    public void release() {
        deleteProgram(maskProgram);
        deleteProgram(circle3Program);
        deleteProgram(circle2Program);
        deleteProgram(blurProgram);
        deleteProgram(finalProgram);
        maskProgram = null;
        circle3Program = null;
        circle2Program = null;
        blurProgram = null;
        finalProgram = null;
        deleteTarget(maskTarget);
        deleteTarget(circle3Target);
        deleteTarget(circle2Target);
        deleteTarget(blurTarget);
        maskTarget = null;
        circle3Target = null;
        circle2Target = null;
        blurTarget = null;
        if (backgroundTexture != 0) {
            int[] texture = {backgroundTexture};
            GLES20.glDeleteTextures(1, texture, 0);
            backgroundTexture = 0;
        }
        if (originNoiseTexture != 0) {
            int[] texture = {originNoiseTexture};
            GLES20.glDeleteTextures(1, texture, 0);
            originNoiseTexture = 0;
        }
        circle3Mesh = null;
        circle2Mesh = null;
        initialized = false;
        backgroundReady = false;
    }

    private void updateAnimation(double now) {
        if (now - unlockStartSeconds <= UNLOCK_TOTAL_SECONDS) {
            sceneAlpha = unlockFadeAlpha(unlockAlphaFrom,
                    (float) (now - unlockStartSeconds));
        } else if (unlockStartSeconds > 0.0) {
            sceneAlpha = 0.0f;
        }
        for (TouchRecord record : touches) {
            if (!record.active) {
                continue;
            }
            if (record.unlocking) {
                if (now > record.cleanupTime) {
                    reclaimTouch(record);
                    if (record == currentTouch) {
                        currentTouch = null;
                    }
                }
            } else if (!record.decaying && now >= record.growthExpiresAt) {
                // FUN_1b580 distinguishes the current record from old trail records.
                record.beginDecay(now, record == currentTouch ? 0.0f : TOUCH_START_RADIUS);
            } else if (record.decaying && now >= record.cleanupTime) {
                reclaimTouch(record);
                if (record == currentTouch) {
                    currentTouch = null;
                }
            }
        }
        // The timed-byte animator keeps +0xe4 set through the inclusive end time.
        if (specialActive && now - specialStartSeconds > AFFORDANCE_SECONDS) {
            specialActive = false;
        }
        if (!specialActive && !hasActiveTouches()) {
            // FUN_1d394 cancels unfinished ring records once neither mask source is alive.
            ringsActive = false;
            ringStartSeconds = -1000.0;
        }
    }

    private void startRingsIfInactive(double now) {
        if (!ringsActive) {
            ringStartSeconds = now;
            ringsActive = true;
        }
    }

    private boolean hasActiveTouches() {
        for (TouchRecord record : touches) {
            if (record.active) {
                return true;
            }
        }
        return false;
    }

    private void reclaimTouch(TouchRecord record) {
        record.active = false;
        if (freeTouchCount < freeTouchIndices.length) {
            freeTouchIndices[freeTouchCount++] = record.index;
        }
    }

    /** Obtains a record even under a full trail, preserving the unlock terminal guarantee. */
    private TouchRecord obtainTerminalTouch() {
        if (freeTouchCount > 0) {
            return touches[freeTouchIndices[--freeTouchCount]];
        }
        TouchRecord oldest = touches[0];
        for (TouchRecord record : touches) {
            if (record.cleanupTime < oldest.cleanupTime) {
                oldest = record;
            }
        }
        return oldest;
    }

    private boolean isAnimating(double now) {
        if (specialActive || (ringsActive && now - ringStartSeconds <= RING_END[2])) {
            return true;
        }
        for (TouchRecord record : touches) {
            if (record.active) {
                return true;
            }
        }
        return false;
    }

    private void updateRings(double now) {
        float age = Math.max(0.0f, (float) (now - ringStartSeconds));
        // On the Note 4 oracle the three slow circle bands remain as the colour
        // pattern for as long as an ordinary dragged mask stays alive. Without
        // this hold all five reconstructed bands reach alpha zero by 3.6 s and
        // leave only the triangular colour-origin layer visible.
        if (ringsActive && !specialActive && hasActiveTouches()) {
            age = Math.min(age, RING_END[0]);
        }
        for (int i = 0; i < rings.length; ++i) {
            RingLayer ring = rings[i];
            float progress = clamp((age - RING_DELAY[i])
                    / Math.max(0.0001f, RING_END[i] - RING_DELAY[i]), 0.0f, 1.0f);
            ring.radius = mix(RING_FROM[i], BASE_RADIUS, progress);
            ring.alpha = clamp(1.5f - ring.radius * 3.0f / (2.0f * BASE_RADIUS),
                    0.0f, 1.0f);
        }
    }

    private void renderMask(double now) {
        maskVertices.position(0);
        for (int row = 0; row < gridRows; ++row) {
            float yB = -1.0f + 2.0f * row / gridRows;
            float yT = -1.0f + 2.0f * (row + 1) / gridRows;
            for (int column = 0; column < gridColumns; ++column) {
                float xL = -1.0f + 2.0f * column / gridColumns;
                float xR = -1.0f + 2.0f * (column + 1) / gridColumns;
                float xC = (xL + xR) * 0.5f;
                float yC = (yB + yT) * 0.5f;

                // FUN_16084 samples the side midpoints, not the cell corners.
                float left = rawMaskCoverage(xL, yC, now);
                float right = rawMaskCoverage(xR, yC, now);
                float bottom = rawMaskCoverage(xC, yB, now);
                float top = rawMaskCoverage(xC, yT, now);
                float alphaLeft = maskAlpha(Math.min(2.0f * left, bottom + top));
                float alphaBottom = maskAlpha(Math.min(2.0f * bottom, left + right));
                float alphaRight = maskAlpha(Math.min(2.0f * right, bottom + top));
                float alphaTop = maskAlpha(Math.min(2.0f * top, left + right));

                // Four faces around the centre. Every face has one constant alpha repeated on
                // all three vertices; merging them into two interpolated triangles is not exact.
                putMaskTriangle(xL, yT, xC, yC, xL, yB, alphaLeft);
                putMaskTriangle(xL, yB, xC, yC, xR, yB, alphaBottom);
                putMaskTriangle(xR, yB, xC, yC, xR, yT, alphaRight);
                putMaskTriangle(xR, yT, xC, yC, xL, yT, alphaTop);
            }
        }
        maskVertices.position(0);
        maskTarget.bind();
        clear(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        maskProgram.use();
        int position = maskProgram.attribute("aPos");
        int alpha = maskProgram.attribute("aTex");
        maskVertices.position(0);
        enableAttribute(position, 2, 12, maskVertices);
        maskVertices.position(2);
        enableAttribute(alpha, 1, 12, maskVertices);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, MASK_VERTEX_COUNT);
        disableAttribute(position);
        disableAttribute(alpha);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private float rawMaskCoverage(float vx, float vy, double now) {
        float coverage = specialActive ? specialMaskCoverage(vx, vy, now) : 0.0f;
        for (TouchRecord record : touches) {
            if (!record.active) {
                continue;
            }
            if (coverage < 0.0f) {
                coverage = 0.0f;
            }
            float radius = record.radiusAt(now);
            float dx = (vx - record.x) * maskScaleX;
            float dy = (vy - record.y) * maskScaleY;
            float value = 1.0f - (float) Math.sqrt(dx * dx + dy * dy)
                    / Math.max(radius, 0.0001f);
            coverage = Math.max(coverage, value);
        }
        // The stock special field remains signed until the four side values are combined;
        // ordinary-touch-only coverage is clamped at zero by its own max seed.
        return specialActive ? coverage : Math.max(0.0f, coverage);
    }

    private float specialMaskCoverage(float vx, float vy, double now) {
        float progress = clamp((float) ((now - specialStartSeconds) / AFFORDANCE_SECONDS),
                0.0f, 1.0f);
        float radius = mix(AFFORDANCE_RADIUS_FROM, AFFORDANCE_RADIUS_TO, progress);
        float dx = (vx - specialCenterX) * maskScaleX;
        float dy = (vy - specialCenterY) * maskScaleY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float denominator = radius + 1.8f;
        float gain = 10.5f / (denominator * denominator * denominator);
        return gain * (0.5f - Math.abs(radius - distance));
    }

    private float maskAlpha(float smoothedCoverage) {
        return clamp(smoothedCoverage * sceneAlpha, 0.0f, 1.0f);
    }

    private void putMaskVertex(float x, float y, float alpha) {
        maskVertices.put(x).put(y).put(alpha);
    }

    private void putMaskTriangle(float x0, float y0, float x1, float y1,
            float x2, float y2, float alpha) {
        putMaskVertex(x0, y0, alpha);
        putMaskVertex(x1, y1, alpha);
        putMaskVertex(x2, y2, alpha);
    }

    private RingLayer[] orderCircle3Layers() {
        RingLayer r0 = rings[0];
        RingLayer r1 = rings[1];
        RingLayer r2 = rings[2];
        if (r2.radius < r1.radius && r1.radius < r0.radius) {
            return new RingLayer[] {r0, r1, r2};
        }
        if (r2.radius < r1.radius && r0.radius < r2.radius) {
            return new RingLayer[] {r1, r2, r0};
        }
        return new RingLayer[] {r2, r0, r1};
    }

    private RingLayer[] orderCircle2Layers() {
        RingLayer r3 = rings[3];
        RingLayer r4 = rings[4];
        return r3.radius <= r4.radius
                ? new RingLayer[] {r4, r3}
                : new RingLayer[] {r3, r4};
    }

    private void renderCircleTarget(RenderTarget target, Program program,
            CircleMesh mesh, RingLayer[] layers) {
        target.bind();
        clear(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        program.use();

        bindCircleCommon(program, mesh);
        float nativeCircleWidth = (Math.min(width, height) / 12) * 6.0f;
        float nativeCircleHeight = (Math.max(width, height) / 21) * 10.0f;
        GLES20.glUniform1f(program.uniform("uSquareRatio"),
                square(nativeCircleHeight / Math.max(1.0f, nativeCircleWidth)));
        for (int i = 0; i < layers.length; ++i) {
            char suffix = (char) ('A' + i);
            GLES20.glUniform1f(program.uniform("uRadius" + suffix), layers[i].radius);
            GLES20.glUniform1f(program.uniform("uAlpha" + suffix), layers[i].alpha);
            int colorLocation = program.attribute("aColor" + suffix);
            int groupBase = layers.length == 3 ? 0 : 3;
            FloatBuffer colors = mesh.colors[layers[i].index - groupBase];
            colors.position(0);
            enableAttribute(colorLocation, 3, 0, colors);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount);
        disableAttribute(program.attribute("aPos"));
        disableAttribute(program.attribute("aCenter"));
        for (int i = 0; i < layers.length; ++i) {
            disableAttribute(program.attribute("aColor" + (char) ('A' + i)));
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void bindCircleCommon(Program program, CircleMesh mesh) {
        int position = program.attribute("aPos");
        int center = program.attribute("aCenter");
        mesh.geometry.position(0);
        enableAttribute(position, 2, 16, mesh.geometry);
        mesh.geometry.position(2);
        enableAttribute(center, 2, 16, mesh.geometry);
    }

    private void renderBlur() {
        blurTarget.bind();
        clear(0.0f, 0.0f, 0.0f, 1.0f);
        blurProgram.use();
        bindFullscreen(blurProgram);
        bindSampler(blurProgram, "uTexture", backgroundTexture, 0);
        GLES20.glUniform2f(blurProgram.uniform("uTexel"),
                1.0f / width, 1.0f / height);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindFullscreen(blurProgram);
    }

    private void renderFinal() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        clear(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        // Keep the patched ARM32 shader tail and its original blend equation exactly paired.
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        finalProgram.use();
        bindFullscreen(finalProgram);
        bindSampler(finalProgram, "uBackground", backgroundTexture, 0);
        bindSampler(finalProgram, "uTextureOrigin", originNoiseTexture, 1);
        bindSampler(finalProgram, "uTextureColorOrigin", blurTarget.texture, 2);
        bindSampler(finalProgram, "uTextureCircles", circle3Target.texture, 3);
        bindSampler(finalProgram, "uTextureAnotherCircles", circle2Target.texture, 4);
        bindSampler(finalProgram, "uMask", maskTarget.texture, 5);
        GLES20.glUniform1f(finalProgram.uniform("uBlockSizeWidthNormalize"),
                1.0f / gridColumns);
        GLES20.glUniform1f(finalProgram.uniform("uBlockSizeHeightNormalize"),
                1.0f / gridRows);
        GLES20.glUniform1i(finalProgram.uniform("uLandscape"), width > height ? 1 : 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindFullscreen(finalProgram);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    private void bindFullscreen(Program program) {
        int position = program.attribute("aPos");
        int texture = program.attribute("aTex");
        fullscreen.position(0);
        enableAttribute(position, 2, 16, fullscreen);
        fullscreen.position(2);
        enableAttribute(texture, 2, 16, fullscreen);
    }

    private void unbindFullscreen(Program program) {
        disableAttribute(program.attribute("aPos"));
        disableAttribute(program.attribute("aTex"));
    }

    private static void bindSampler(Program program, String name, int texture, int unit) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(program.uniform(name), unit);
    }

    private static void enableAttribute(int location, int size, int stride, FloatBuffer buffer) {
        if (location >= 0) {
            GLES20.glEnableVertexAttribArray(location);
            GLES20.glVertexAttribPointer(location, size, GLES20.GL_FLOAT, false, stride, buffer);
        }
    }

    private static void disableAttribute(int location) {
        if (location >= 0) {
            GLES20.glDisableVertexAttribArray(location);
        }
    }

    private static void clear(float red, float green, float blue, float alpha) {
        GLES20.glClearColor(red, green, blue, alpha);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private static void bindTextureParameters(int texture, boolean repeat, boolean linear) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                linear ? GLES20.GL_LINEAR : GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                linear ? GLES20.GL_LINEAR : GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE);
    }

    private static final class TouchRecord {
        final int index;
        boolean active;
        boolean unlocking;
        boolean decaying;
        float x;
        float y;
        float unlockFrom;
        float decayFrom;
        float decayTo;
        double start;
        double unlockStart;
        double decayStart;
        double growthExpiresAt;
        double cleanupTime;

        TouchRecord(int index) {
            this.index = index;
        }

        void begin(float clipX, float clipY, double now) {
            active = true;
            unlocking = false;
            decaying = false;
            x = clipX;
            y = clipY;
            start = now;
            growthExpiresAt = now + TOUCH_GROW_SECONDS;
            cleanupTime = 0.0;
        }

        void beginDecay(double now, float targetRadius) {
            decayFrom = radiusAt(now);
            decayTo = targetRadius;
            decayStart = now;
            cleanupTime = now + TOUCH_SHRINK_SECONDS;
            decaying = true;
        }

        void unlock(double now, float currentRadius) {
            unlocking = true;
            decaying = false;
            unlockStart = now;
            unlockFrom = currentRadius;
            cleanupTime = now + UNLOCK_TOTAL_SECONDS;
        }

        float radiusAt(double now) {
            if (unlocking) {
                return unlockRadius(unlockFrom, (float) (now - unlockStart));
            }
            if (decaying) {
                float progress = clamp((float) ((now - decayStart)
                        / TOUCH_SHRINK_SECONDS), 0.0f, 1.0f);
                return mix(decayFrom, decayTo, progress * progress * progress);
            }
            float age = Math.max(0.0f, (float) (now - start));
            float progress = clamp(age / TOUCH_GROW_SECONDS, 0.0f, 1.0f);
            return mix(TOUCH_START_RADIUS, TOUCH_PEAK_RADIUS,
                    cosineEaseInOut(progress));
        }
    }

    private static final class RingLayer {
        final int index;
        float radius;
        float alpha;

        RingLayer(int index) {
            this.index = index;
        }
    }

    private static final class CircleMesh {
        private static final int[][] FIRST_COLOR_PATTERN = {
                {6, 4, 5, 6, 4},
                {0, 7, 11, 0, 7},
                {1, 8, 12, 1, 8},
                {2, 9, 13, 2, 9},
                {3, 10, 14, 3, 10},
                {6, 4, 5, 6, 4}
        };
        private static final int[][] SECOND_COLOR_PATTERN = {
                {4, 1, 0, 4},
                {2, 6, 5, 2},
                {3, 9, 10, 3},
                {7, 11, 12, 7},
                {8, 13, 14, 8},
                {4, 1, 0, 4},
                {2, 6, 5, 2}
        };

        final int vertexCount;
        final FloatBuffer geometry;
        final FloatBuffer[] colors;

        CircleMesh(int cells, int layerCount) {
            vertexCount = cells * 6;
            geometry = directFloats(vertexCount * 4);
            colors = new FloatBuffer[layerCount];
            for (int i = 0; i < layerCount; ++i) {
                colors[i] = directFloats(vertexCount * 3);
            }
        }

        static CircleMesh firstLattice(Random random, int layerCount) {
            CircleMesh mesh = new CircleMesh(5 * 6, layerCount);
            for (int y = 0; y < 6; ++y) {
                for (int x = 0; x < 5; ++x) {
                    float centerX = -4.0f / 3.0f + x * (2.0f / 3.0f);
                    float centerY = -1.0f + y * 0.4f;
                    mesh.putQuadGeometry(centerX, centerY, BASE_RADIUS, 0.25f);
                }
            }
            for (int layer = 0; layer < layerCount; ++layer) {
                float[][] seeds = drawSeedColors(random);
                for (int row = 0; row < FIRST_COLOR_PATTERN.length; ++row) {
                    for (int column = 0; column < FIRST_COLOR_PATTERN[row].length; ++column) {
                        mesh.putCellColor(layer, seeds[FIRST_COLOR_PATTERN[row][column]]);
                    }
                }
            }
            mesh.finish();
            return mesh;
        }

        static CircleMesh secondLattice(Random random, int layerCount) {
            CircleMesh mesh = new CircleMesh(4 * 7, layerCount);
            for (int y = 0; y < 7; ++y) {
                for (int x = 0; x < 4; ++x) {
                    float centerX = -1.0f + x * (2.0f / 3.0f);
                    float centerY = -1.2f + y * 0.4f;
                    mesh.putQuadGeometry(centerX, centerY, BASE_RADIUS, 0.25f);
                }
            }
            for (int layer = 0; layer < layerCount; ++layer) {
                float[][] seeds = drawSeedColors(random);
                for (int row = 0; row < SECOND_COLOR_PATTERN.length; ++row) {
                    for (int column = 0; column < SECOND_COLOR_PATTERN[row].length;
                            ++column) {
                        mesh.putCellColor(layer, seeds[SECOND_COLOR_PATTERN[row][column]]);
                    }
                }
            }
            mesh.finish();
            return mesh;
        }

        private static float[][] drawSeedColors(Random random) {
            float[][] result = new float[15][];
            for (int i = 0; i < result.length; ++i) {
                result[i] = PALETTE[random.nextInt(PALETTE.length)];
            }
            return result;
        }

        private void putQuadGeometry(float cx, float cy, float halfX, float halfY) {
            // Original vertex order 0,1,2,2,1,3 from FUN_257f8.
            float[] xy = {
                    cx - halfX, cy + halfY,
                    cx - halfX, cy - halfY,
                    cx + halfX, cy + halfY,
                    cx + halfX, cy + halfY,
                    cx - halfX, cy - halfY,
                    cx + halfX, cy - halfY
            };
            for (int vertex = 0; vertex < 6; ++vertex) {
                geometry.put(xy[vertex * 2]).put(xy[vertex * 2 + 1]).put(cx).put(cy);
            }
        }

        private void putCellColor(int layer, float[] color) {
            FloatBuffer buffer = colors[layer];
            for (int vertex = 0; vertex < 6; ++vertex) {
                buffer.put(color[0]).put(color[1]).put(color[2]);
            }
        }

        private void finish() {
            geometry.position(0);
            for (FloatBuffer color : colors) {
                color.position(0);
            }
        }
    }

    private static final class RenderTarget {
        final int width;
        final int height;
        int framebuffer;
        int texture;

        RenderTarget(int width, int height, boolean repeat, boolean linear) {
            this.width = width;
            this.height = height;
            int[] names = new int[1];
            GLES20.glGenTextures(1, names, 0);
            texture = names[0];
            bindTextureParameters(texture, repeat, linear);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glGenFramebuffers(1, names, 0);
            framebuffer = names[0];
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("incomplete FBO 0x"
                        + Integer.toHexString(status) + " size=" + width + "x" + height);
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        void bind() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
            GLES20.glViewport(0, 0, width, height);
        }

        void delete() {
            if (texture != 0) {
                int[] names = {texture};
                GLES20.glDeleteTextures(1, names, 0);
                texture = 0;
            }
            if (framebuffer != 0) {
                int[] names = {framebuffer};
                GLES20.glDeleteFramebuffers(1, names, 0);
                framebuffer = 0;
            }
        }
    }

    private static final class Program {
        int name;

        Program(String vertexSource, String fragmentSource) {
            int vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            name = GLES20.glCreateProgram();
            GLES20.glAttachShader(name, vertex);
            GLES20.glAttachShader(name, fragment);
            GLES20.glLinkProgram(name);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(name, GLES20.GL_LINK_STATUS, linked, 0);
            String log = GLES20.glGetProgramInfoLog(name);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            if (linked[0] == 0) {
                GLES20.glDeleteProgram(name);
                name = 0;
                throw new IllegalStateException("GL program link failed: " + log);
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

        void delete() {
            if (name != 0) {
                GLES20.glDeleteProgram(name);
                name = 0;
            }
        }
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
            throw new IllegalStateException("GL shader compile failed: " + log);
        }
        return shader;
    }

    private static void deleteProgram(Program program) {
        if (program != null) {
            program.delete();
        }
    }

    private static void deleteTarget(RenderTarget target) {
        if (target != null) {
            target.delete();
        }
    }

    private static void checkGl(String operation) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(operation + " GL error 0x"
                    + Integer.toHexString(error));
        }
    }

    private static FloatBuffer directFloats(int count) {
        return ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static FloatBuffer directFloats(float[] values) {
        FloatBuffer buffer = directFloats(values.length);
        buffer.put(values).position(0);
        return buffer;
    }

    private static double seconds(long nanos) {
        return nanos * 1.0e-9;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float square(float value) {
        return value * value;
    }

    private static float cosineEaseInOut(float value) {
        return 0.5f * (1.0f - (float) Math.cos(Math.PI * value));
    }

    private static float sineEaseOut(float value) {
        return (float) Math.sin(Math.PI * 0.5 * value);
    }

    /** Package seam for deterministic unlock expansion verification without a GLES context. */
    static float unlockRadius(float fromRadius, float elapsedSeconds) {
        return mix(fromRadius, UNLOCK_RADIUS,
                clamp(elapsedSeconds / UNLOCK_EXPAND_SECONDS, 0.0f, 1.0f));
    }

    /** Package seam for deterministic two-phase whole-scene unlock fade verification. */
    static float unlockFadeAlpha(float fromAlpha, float elapsedSeconds) {
        float fadeElapsed = Math.max(0.0f, elapsedSeconds - UNLOCK_EXPAND_SECONDS);
        return mix(fromAlpha, 0.0f, sineEaseOut(
                clamp(fadeElapsed / UNLOCK_FADE_SECONDS, 0.0f, 1.0f)));
    }

    /** The full-coverage boundary; wall-time based for both stock and HFR presentation. */
    static long unlockCoverageDelayMs() {
        return Math.round(UNLOCK_EXPAND_SECONDS * 1000.0f);
    }

    /** The handoff/neutralization boundary is deliberately tied to coverage, not fade completion. */
    static long unlockHandoffDelayMs() {
        return unlockCoverageDelayMs();
    }

    /** The fade duration after full coverage; it does not gate the unlock dispatch. */
    static long unlockFadeDelayMs() {
        return Math.round(UNLOCK_FADE_SECONDS * 1000.0f);
    }

    /** The visual completion boundary; wall-time based for both stock and HFR presentation. */
    static long unlockCompleteDelayMs() {
        return Math.round(UNLOCK_TOTAL_SECONDS * 1000.0f);
    }

    void advanceAnimationForTest(long frameTimeNanos) {
        updateAnimation(seconds(frameTimeNanos));
    }

    boolean isTerminalUnlockArmedForTest() {
        return currentTouch != null && currentTouch.active && currentTouch.unlocking;
    }

    float terminalTouchXForTest() {
        return currentTouch == null ? Float.NaN : (currentTouch.x + 1.0f) * 0.5f;
    }

    float terminalTouchYForTest() {
        return currentTouch == null ? Float.NaN : (1.0f - currentTouch.y) * 0.5f;
    }

    float terminalRadiusForTest(long frameTimeNanos) {
        return currentTouch == null ? Float.NaN : currentTouch.radiusAt(seconds(frameTimeNanos));
    }

    float sceneAlphaForTest() {
        return sceneAlpha;
    }

    boolean isAnimatingForTest(long frameTimeNanos) {
        return isAnimating(seconds(frameTimeNanos));
    }

    private static final String MASK_VERTEX_SHADER =
            "precision mediump float;\n"
            + "uniform mat4 uModelViewProjectionMatrix;\n"
            + "attribute vec2 aPos;\n"
            + "attribute float aTex;\n"
            + "varying float alpha;\n"
            + "void main(){alpha=aTex;gl_Position=vec4(aPos,0.0,1.0);}\n";

    private static final String MASK_FRAGMENT_SHADER =
            "precision mediump float;\n"
            + "varying float alpha;\n"
            + "void main(){gl_FragColor=vec4(1.0,1.0,1.0,alpha);}\n";

    private static final String CIRCLE_VERTEX_SHADER =
            "precision mediump float;\n"
            + "attribute vec2 aPos;\n"
            + "attribute vec2 aCenter;\n"
            + "attribute vec3 aColorA;\n"
            + "attribute vec3 aColorB;\n"
            + "attribute vec3 aColorC;\n"
            + "varying vec2 position;\n"
            + "varying vec2 center;\n"
            + "varying vec3 colorA;\n"
            + "varying vec3 colorB;\n"
            + "varying vec3 colorC;\n"
            + "void main(){position=aPos;center=aCenter;colorA=aColorA;colorB=aColorB;"
            + "colorC=aColorC;gl_Position=vec4(aPos,0.0,1.0);}\n";

    private static final String CIRCLE3_FRAGMENT_SHADER =
            "precision mediump float;\n"
            + "varying vec2 position;varying vec2 center;\n"
            + "varying vec3 colorA;varying vec3 colorB;varying vec3 colorC;\n"
            + "uniform float uSquareRatio;uniform float uRadiusA;uniform float uRadiusB;"
            + "uniform float uRadiusC;uniform float uAlphaA;uniform float uAlphaB;"
            + "uniform float uAlphaC;\n"
            + "void main(){vec2 d=position-center;float dist=sqrt(d.x*d.x+d.y*d.y*uSquareRatio);"
            + "if(dist<uRadiusC)gl_FragColor=vec4(colorC,uAlphaC);"
            + "else if(dist<uRadiusB)gl_FragColor=vec4(colorB,uAlphaB);"
            + "else if(dist<uRadiusA)gl_FragColor=vec4(colorA,uAlphaA);else discard;}\n";

    /* aColorC/uRadiusC/uAlphaC are optimized out for the two-ring program. */
    private static final String CIRCLE2_FRAGMENT_SHADER =
            "precision mediump float;\n"
            + "varying vec2 position;varying vec2 center;\n"
            + "varying vec3 colorA;varying vec3 colorB;\n"
            + "uniform float uSquareRatio;uniform float uRadiusA;uniform float uRadiusB;"
            + "uniform float uAlphaA;uniform float uAlphaB;\n"
            + "void main(){vec2 d=position-center;float dist=sqrt(d.x*d.x+d.y*d.y*uSquareRatio);"
            + "if(dist<uRadiusB)gl_FragColor=vec4(colorB,uAlphaB);"
            + "else if(dist<uRadiusA)gl_FragColor=vec4(colorA,uAlphaA);else discard;}\n";

    private static final String TEXTURE_VERTEX_SHADER =
            "precision mediump float;attribute vec2 aPos;attribute vec2 aTex;"
            + "varying vec2 UV;varying highp vec2 UVhighp;varying vec2 UVNorm;\n"
            + "void main(){gl_Position=vec4(aPos,0.0,1.0);"
            + "UV=(gl_Position.xy+vec2(1.0))*0.5;"
            + "UVhighp=(gl_Position.xy+vec2(1.0))*0.5;UVNorm=aTex;}\n";

    private static final String BLUR_FRAGMENT_SHADER =
            "precision mediump float;varying vec2 UVNorm;uniform sampler2D uTexture;"
            + "uniform vec2 uTexel;\n"
            + "void main(){vec3 c=vec3(0.0);int distance=10;"
            + "for(int i=-distance;i<=distance;i+=2){"
            + "float xCoord=UVNorm.x+float(i)*uTexel.x;"
            + "for(int j=-distance;j<=distance;j+=2){"
            + "float yCoord=UVNorm.y+float(j)*uTexel.y;"
            + "c+=texture2D(uTexture,vec2(xCoord,yCoord)).rgb;}}"
            + "c=c/vec3((distance+1)*(distance+1));"
            + "float g=0.299*c.r+0.587*c.g+0.114*c.b;"
            + "if(g<0.2)c+=0.3;gl_FragColor=vec4(c,1.0);}\n";

    private static final String FINAL_FRAGMENT_SHADER =
            "precision mediump float;varying vec2 UV;varying highp vec2 UVhighp;"
            + "varying vec2 UVNorm;\n"
            + "uniform sampler2D uBackground;uniform sampler2D uMask;"
            + "uniform sampler2D uTextureCircles;uniform sampler2D uTextureAnotherCircles;"
            + "uniform sampler2D uTextureOrigin;uniform sampler2D uTextureColorOrigin;\n"
            + "uniform float uBlockSizeWidthNormalize;"
            + "uniform float uBlockSizeHeightNormalize;uniform int uLandscape;\n"
            + "float overlay1(float a,float b){return a<0.5?2.0*a*b:1.0-2.0*(1.0-a)*(1.0-b);}"
            + "vec3 overlay3(vec3 a,vec3 b){return vec3(overlay1(a.r,b.r),overlay1(a.g,b.g),overlay1(a.b,b.b));}"
            + "vec3 overlayGray(vec3 a,float b){return vec3(overlay1(a.r,b),overlay1(a.g,b),overlay1(a.b,b));}"
            + "vec3 linearDodge(vec3 a,vec3 b){return min(a+b,vec3(1.0));}"
            + "vec3 softLight(vec3 a,vec3 b){return (vec3(1.0)-2.0*b)*a*a+2.0*b*a;}"
            + "void main(){float alpha=1.0-texture2D(uMask,UV).x;if(alpha<1.0){"
            + "float shift=uBlockSizeWidthNormalize*mod(UVhighp.y,uBlockSizeHeightNormalize)"
            + "/uBlockSizeHeightNormalize;"
            + "vec2 coordL=vec2(UVhighp.x+shift,UVhighp.y);"
            + "vec2 coordR=vec2(UVhighp.x-shift,UVhighp.y);"
            + "vec2 coordRInv=vec2(UVhighp.x-shift,1.0-UVhighp.y);"
            + "vec3 colorLayerA=texture2D(uTextureColorOrigin,coordL).rgb;"
            + "vec3 colorLayerB=texture2D(uTextureColorOrigin,coordR).rgb;"
            + "vec3 colorMosaicC=texture2D(uTextureColorOrigin,UVhighp).rgb;"
            + "vec2 circleUV=uLandscape==0?vec2(UV.x*2.0,UV.y*2.0*1.05)"
            + ":vec2(UV.y*2.0,UV.x*2.0*1.05);"
            + "vec3 circleA=texture2D(uTextureCircles,circleUV).rgb;"
            + "vec3 circleB=texture2D(uTextureAnotherCircles,circleUV).rgb;"
            + "float colorGrayB=texture2D(uTextureOrigin,coordL).a;"
            + "float colorGrayC=texture2D(uTextureOrigin,coordRInv).a;"
            + "vec3 c1=mix(colorLayerA,linearDodge(colorLayerA,circleA),0.75);"
            + "vec3 c2=mix(c1,softLight(c1,circleB),0.40);"
            + "vec3 c3=overlay3(c2,colorLayerB);"
            + "vec3 c4=mix(c3,max(c3,colorMosaicC),0.75);"
            + "vec3 c5=mix(c4,overlayGray(c4,colorGrayB),0.5);"
            + "vec3 c6=mix(c5,overlayGray(c5,colorGrayC),0.5);"
            + "float m=clamp(1.0-alpha,0.0,1.0);float a=sqrt(m);"
            + "gl_FragColor=vec4(c6*a,a);}else gl_FragColor=vec4(0.0);}\n";
}
