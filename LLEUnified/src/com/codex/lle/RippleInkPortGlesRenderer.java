package com.codex.lle;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** GLES2 mesh renderer for the isolated app-owned Ripple Ink ARM64 port. */
final class RippleInkPortGlesRenderer
        implements GLSurfaceView.Renderer, RippleInkPortFluidPipeline.PassSink {
    interface Host {
        void onRippleInkGlesState(int state, String detail);

        void onRippleInkIdle();
    }

    private static final String TAG = "LLERippleInkPort";
    private static final float REFRACTIVE_INDEX = 0.93f;
    private static final float REFLECTION_RATIO = 0.13f;
    private static final float FRESNEL_RATIO = 0.1f;
    private static final float SPECULAR_RATIO = 0.5f;
    private static final float EXPONENT_RATIO = 20.0f;
    private static final float INK_INTENSITY = 0.02f;

    private final Host host;
    private final RippleInkPortEngine engine;
    private final RippleInkPortFluidPipeline fluidPipeline;
    private final FloatBuffer vertexBuffer;
    private final FloatBuffer heightBuffer;
    private final ShortBuffer indexBuffer;
    private final FloatBuffer fullscreenVertexBuffer;
    private final FloatBuffer fullscreenTexCoordBuffer;
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] worldMatrix = new float[16];
    private final float[] worldViewMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];
    private final RippleInkPortCompositor.RetainedInkIdleSignal idleSignal =
            new RippleInkPortCompositor.RetainedInkIdleSignal();

    private Bitmap backgroundBitmap;
    private Bitmap reflectionBitmap;
    private ByteBuffer velocityUpload;
    private int meshProgram;
    private int advectProgram;
    private int addInkProgram;
    private int backgroundTexture;
    private int reflectionTexture;
    private int velocityTexture;
    private final int[] densityTextures = new int[2];
    private final int[] densityFramebuffers = new int[2];
    private int surfaceWidth;
    private int surfaceHeight;
    private volatile boolean surfaceCreated;
    private volatile boolean fluidResourcesReady;
    private volatile boolean failed;
    private volatile boolean firstFrame;
    private String failureDetail = "not initialized";

    RippleInkPortGlesRenderer(Host host, int paletteSelector, boolean highFrameRateEnabled) {
        this.host = host;
        engine = new RippleInkPortEngine();
        fluidPipeline = new RippleInkPortFluidPipeline();
        engine.setPaletteSelector(paletteSelector);
        engine.setHighFrameRateEnabled(highFrameRateEnabled);
        vertexBuffer = directFloatBuffer(engine.vertices());
        heightBuffer = directFloatBuffer(engine.gpuHeights());
        indexBuffer = directShortBuffer(engine.indices());
        fullscreenVertexBuffer = directFloatBuffer(new float[]{
                -1.0f, -1.0f,
                1.0f, -1.0f,
                -1.0f, 1.0f,
                1.0f, 1.0f
        });
        fullscreenTexCoordBuffer = directFloatBuffer(new float[]{
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f
        });
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        surfaceCreated = true;
        failed = false;
        firstFrame = false;
        idleSignal.reset();
        engine.reset();
        fluidPipeline.reset();
        meshProgram = 0;
        advectProgram = 0;
        addInkProgram = 0;
        backgroundTexture = 0;
        reflectionTexture = 0;
        velocityTexture = 0;
        Arrays.fill(densityTextures, 0);
        Arrays.fill(densityFramebuffers, 0);
        fluidResourcesReady = false;
        failureDetail = "surface created; resources pending";
        clearTransparent();
        try {
            meshProgram = linkProgram(
                    RippleInkPortGlesShaders.MESH_VERTEX,
                    RippleInkPortGlesShaders.TRANSPARENT_INK_FRAGMENT,
                    "mesh-overlay");
            advectProgram = linkProgram(
                    RippleInkPortGlesShaders.FULLSCREEN_VERTEX,
                    RippleInkPortGlesShaders.STOCK_ADVECT_DENSITY_FRAGMENT,
                    "density-advect");
            addInkProgram = linkProgram(
                    RippleInkPortGlesShaders.FULLSCREEN_VERTEX,
                    RippleInkPortGlesShaders.STOCK_ADD_INK_FRAGMENT,
                    "density-add");
            if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
                backgroundTexture = uploadBitmap(backgroundBitmap);
            }
            if (reflectionBitmap != null && !reflectionBitmap.isRecycled()) {
                reflectionTexture = uploadBitmap(reflectionBitmap);
            }
            publishResourceState();
        } catch (RuntimeException exception) {
            fail("shader/context setup failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        engine.configureSurface(surfaceWidth, surfaceHeight);
        fluidPipeline.configure(surfaceWidth, surfaceHeight);
        if (!fluidPipeline.isNativeWorkerReadyForProduction()) {
            fail("N3 Ripple Ink worker unavailable: "
                    + fluidPipeline.nativeWorkerFailureDetail(),
                    new IllegalStateException(fluidPipeline.nativeWorkerFailureDetail()));
            return;
        }
        idleSignal.reset();
        engine.resetFrameClock();
        buildMvp(surfaceWidth, surfaceHeight);
        recreateFluidResources();
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        publishResourceState();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        clearTransparent();
        if (!canDraw()) {
            return;
        }
        try {
            long frameTimeNanos = System.nanoTime();
            RippleInkPortEngine.RendererFrameAdvance frame =
                    engine.advanceRendererFrame(frameTimeNanos);
            for (int tick = 0; tick < frame.inkTicks; ++tick) {
                // Ink is an exact fixed-60 pipeline even when adaptive water is presented at
                // native refresh.  Do not replace this with execute(frame.waterCredits, ...).
                fluidPipeline.executeFixedTick(this);
            }
            // Keep the app overlay fully opaque. S4 density Dissipation supplies the local age
            // fade; a host alpha tail would erase old and new deposits at the same time.
            drawMesh();
            if (!firstFrame) {
                firstFrame = true;
                host.onRippleInkGlesState(
                        UnlockEffectReadiness.STATE_FIRST_FRAME_READY,
                        "app-owned Ripple Ink GPU pass rendered; device parity pending");
            }
            // Stock parks the renderer when water is quiet. It does not clear or inspect the
            // density FBO here; retained density resumes evolving on the next DOWN.
            if (!fluidPipeline.hasVisibleTail()
                    && idleSignal.shouldPublish(engine.isTouched(), engine.isWaterActive())) {
                host.onRippleInkIdle();
            }
        } catch (RuntimeException exception) {
            fail("draw failed: " + exception.getMessage(), exception);
        }
    }

    /** UI-thread latest-value input mailbox; no MotionEvent history reaches the source worker. */
    void publishFinger(int action, float x, float y, float pressure) {
        publishFinger(action, x, y, pressure, true);
    }

    void publishFinger(int action, float x, float y, float pressure, boolean inkEnabled) {
        fluidPipeline.onTouch(action, (int) x, (int) y,
                Math.max(0.0f, Math.min(1.0f, pressure)), inkEnabled);
    }

    /** GL-thread water/ripple command. Ink was already published to its independent mailbox. */
    void handleFinger(int action, float x, float y, float pressure, long eventTimeMs) {
        handleFinger(action, x, y, pressure, eventTimeMs, true);
    }

    void handleFinger(int action, float x, float y, float pressure, long eventTimeMs,
            boolean inkEnabled) {
        // The view has already switched to continuous mode for this queued event. Rearm the
        // one-shot idle signal even if Engine rejects a stale event, so the next quiet frame can
        // always park rendering again.
        idleSignal.onActivity();
        // Samsung truncates at the Java/JNI boundary and only caps pressure at one. The source
        // gate is intentionally bypassed for ordinary fingers by the port, not by changing the
        // coordinate or pressure contract.
        int stockX = (int) x;
        int stockY = (int) y;
        float stockPressure = Math.max(0.0f, Math.min(1.0f, pressure));
        if (inkEnabled) {
            engine.handleFinger(action, stockX, stockY, stockPressure, eventTimeMs);
        } else {
            engine.handleWaterOnly(action, stockX, stockY, eventTimeMs);
        }
    }

    void reset() {
        engine.reset();
        fluidPipeline.reset();
        idleSignal.reset();
        if (fluidResourcesReady) {
            clearDensitySurfaces();
        }
    }

    void setPaletteSelector(int selector) {
        engine.setPaletteSelector(selector);
    }

    void setHighFrameRateEnabled(boolean enabled) {
        engine.setHighFrameRateEnabled(enabled);
    }

    void resetFrameClock() {
        engine.resetFrameClock();
    }

    void installBackground(Bitmap bitmap) {
        backgroundBitmap = bitmap;
        if (!surfaceCreated || failed) {
            return;
        }
        if (backgroundTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{backgroundTexture}, 0);
            backgroundTexture = 0;
        }
        if (bitmap != null && !bitmap.isRecycled()) {
            backgroundTexture = uploadBitmap(bitmap);
        }
        publishResourceState();
    }

    void installReflection(Bitmap bitmap) {
        reflectionBitmap = bitmap;
        if (!surfaceCreated || failed) {
            return;
        }
        if (reflectionTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{reflectionTexture}, 0);
            reflectionTexture = 0;
        }
        if (bitmap != null && !bitmap.isRecycled()) {
            reflectionTexture = uploadBitmap(bitmap);
        }
        publishResourceState();
    }

    void releaseGl() {
        // The worker may still be computing N while the EGL surface is being torn down.
        // Join/destroy it before forgetting the density/velocity texture ownership.
        fluidPipeline.releaseNativeWorker();
        int[] textures = {
                backgroundTexture,
                reflectionTexture,
                velocityTexture,
                densityTextures[0],
                densityTextures[1]
        };
        GLES20.glDeleteTextures(textures.length, textures, 0);
        GLES20.glDeleteFramebuffers(
                densityFramebuffers.length, densityFramebuffers, 0);
        backgroundTexture = 0;
        reflectionTexture = 0;
        velocityTexture = 0;
        Arrays.fill(densityTextures, 0);
        Arrays.fill(densityFramebuffers, 0);
        fluidResourcesReady = false;
        if (meshProgram != 0) {
            GLES20.glDeleteProgram(meshProgram);
        }
        if (advectProgram != 0) {
            GLES20.glDeleteProgram(advectProgram);
        }
        if (addInkProgram != 0) {
            GLES20.glDeleteProgram(addInkProgram);
        }
        meshProgram = 0;
        advectProgram = 0;
        addInkProgram = 0;
        surfaceCreated = false;
        firstFrame = false;
    }

    boolean isFailed() {
        return failed;
    }

    String failureDetail() {
        return failureDetail;
    }

    boolean isProductionReady() {
        return firstFrame && canDraw();
    }

    private boolean canDraw() {
        return !failed && surfaceCreated && surfaceWidth > 0 && surfaceHeight > 0
                && meshProgram != 0 && backgroundTexture != 0
                && reflectionTexture != 0 && fluidResourcesReady;
    }

    private void publishResourceState() {
        if (failed) {
            return;
        }
        if (canDraw()) {
            host.onRippleInkGlesState(
                    UnlockEffectReadiness.STATE_RESOURCES_READY,
                    "88991-order GPU ping-pong resources ready; device output pending");
        } else if (surfaceCreated) {
            String detail;
            if (backgroundTexture == 0) {
                detail = "awaiting lockscreen background texture";
            } else if (reflectionTexture == 0) {
                detail = "awaiting Ripple Ink reflection texture";
            } else if (!fluidResourcesReady) {
                detail = "awaiting density/velocity GPU surfaces";
            } else {
                detail = "GLES programs incomplete";
            }
            host.onRippleInkGlesState(UnlockEffectReadiness.STATE_SURFACE_READY, detail);
        }
    }

    private void recreateFluidResources() {
        if (!surfaceCreated || failed) {
            return;
        }
        deleteFluidResources();
        GLES20.glGenTextures(densityTextures.length, densityTextures, 0);
        GLES20.glGenFramebuffers(densityFramebuffers.length, densityFramebuffers, 0);
        for (int index = 0; index < densityTextures.length; ++index) {
            if (densityTextures[index] == 0 || densityFramebuffers[index] == 0) {
                throw new IllegalStateException("density ping-pong allocation returned zero");
            }
            configureRgbaTexture(
                    densityTextures[index],
                    fluidPipeline.densityWidth(),
                    fluidPipeline.densityHeight(),
                    null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, densityFramebuffers[index]);
            GLES20.glFramebufferTexture2D(
                    GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D,
                    densityTextures[index],
                    0);
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("density framebuffer " + index
                        + " incomplete: 0x" + Integer.toHexString(status));
            }
        }

        int[] generated = new int[1];
        GLES20.glGenTextures(1, generated, 0);
        velocityTexture = generated[0];
        if (velocityTexture == 0) {
            throw new IllegalStateException("velocity texture allocation returned zero");
        }
        velocityUpload = ByteBuffer.allocateDirect(
                fluidPipeline.fluidWidth() * fluidPipeline.fluidHeight() * 4)
                .order(ByteOrder.nativeOrder());
        for (int index = 0;
                index < fluidPipeline.fluidWidth() * fluidPipeline.fluidHeight();
                ++index) {
            // Stock decode is 255*high+low-127, therefore 127,0 is exact zero velocity.
            velocityUpload.put((byte) 127).put((byte) 0).put((byte) 127).put((byte) 0);
        }
        velocityUpload.position(0);
        configureRgbaTexture(
                velocityTexture,
                fluidPipeline.fluidWidth(),
                fluidPipeline.fluidHeight(),
                velocityUpload);

        fluidResourcesReady = true;
        clearDensitySurfaces();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        checkGlError("fluid resource setup");
    }

    private void deleteFluidResources() {
        GLES20.glDeleteTextures(densityTextures.length, densityTextures, 0);
        GLES20.glDeleteFramebuffers(densityFramebuffers.length, densityFramebuffers, 0);
        if (velocityTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{velocityTexture}, 0);
        }
        Arrays.fill(densityTextures, 0);
        Arrays.fill(densityFramebuffers, 0);
        velocityTexture = 0;
        velocityUpload = null;
        fluidResourcesReady = false;
    }

    private static void configureRgbaTexture(
            int texture, int width, int height, ByteBuffer pixels) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                pixels);
    }

    private void clearDensitySurfaces() {
        if (!fluidResourcesReady) {
            return;
        }
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        for (int framebuffer : densityFramebuffers) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
            GLES20.glViewport(
                    0, 0, fluidPipeline.densityWidth(), fluidPipeline.densityHeight());
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
    }

    @Override
    public void uploadVelocity(byte[] rgba, int width, int height) {
        requireFluidResources();
        if (rgba == null || width != fluidPipeline.fluidWidth()
                || height != fluidPipeline.fluidHeight() || rgba.length != width * height * 4) {
            throw new IllegalArgumentException("velocity upload dimensions do not match surface");
        }
        velocityUpload.clear();
        velocityUpload.put(rgba);
        velocityUpload.position(0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, velocityTexture);
        // ENB4 Update redefines the RGBA velocity texture on every worker tick.
        // Keep this distinct from a sub-image upload: its allocation chronology is
        // observable by the original driver path.
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                velocityUpload);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void advectDensity(RippleInkPortFluidPipeline.AdvectPass pass) {
        requireFluidPass(pass.sourceIndex, pass.destinationIndex);
        beginFullscreenPass(advectProgram, densityFramebuffers[pass.destinationIndex]);
        uniform2f(advectProgram, "TimeStep", pass.timeStepX, pass.timeStepY);
        uniform1f(advectProgram, "BackwardStepSize", pass.backwardStep);
        uniform1f(advectProgram, "Dissipation", pass.dissipation);
        uniform2f(advectProgram, "Scale", pass.scaleX, pass.scaleY);
        uniform2f(advectProgram, "center", pass.centerX, pass.centerY);
        uniform1i(advectProgram, "drag", pass.dragMode);
        bindTexture(advectProgram, "VelocityTexture", 0, velocityTexture);
        bindTexture(advectProgram, "SourceTexture", 1, densityTextures[pass.sourceIndex]);
        drawFullscreen(advectProgram);
        endFullscreenPass(2);
    }

    @Override
    public void addInk(RippleInkPortFluidPipeline.AddInkPass pass) {
        requireFluidPass(pass.sourceIndex, pass.destinationIndex);
        beginFullscreenPass(addInkProgram, densityFramebuffers[pass.destinationIndex]);
        uniform2f(addInkProgram, "Scale", pass.scaleX, pass.scaleY);
        uniform2f(addInkProgram, "current", pass.currentX, pass.currentY);
        uniform2f(addInkProgram, "previous", pass.previousX, pass.previousY);
        uniform2f(addInkProgram, "normal", pass.normalX, pass.normalY);
        uniform1i(addInkProgram, "mode", pass.mode);
        uniform1f(addInkProgram, "len", pass.length);
        uniform1f(addInkProgram, "ImpulseDensity", pass.impulseDensity);
        uniform1f(addInkProgram, "Radius", pass.radius);
        bindTexture(addInkProgram, "Source", 0, densityTextures[pass.sourceIndex]);
        drawFullscreen(addInkProgram);
        endFullscreenPass(1);
    }

    private void requireFluidResources() {
        if (!fluidResourcesReady || velocityTexture == 0) {
            throw new IllegalStateException("fluid GPU resources are unavailable");
        }
    }

    private void requireFluidPass(int sourceIndex, int destinationIndex) {
        requireFluidResources();
        if (sourceIndex < 0 || sourceIndex >= densityTextures.length
                || destinationIndex < 0 || destinationIndex >= densityTextures.length
                || sourceIndex == destinationIndex) {
            throw new IllegalArgumentException("invalid density ping-pong pass "+ sourceIndex
                    + ">" + destinationIndex);
        }
    }

    private void beginFullscreenPass(int program, int framebuffer) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glViewport(0, 0, fluidPipeline.densityWidth(), fluidPipeline.densityHeight());
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(program);
    }

    private void drawFullscreen(int program) {
        int vertex = GLES20.glGetAttribLocation(program, "vertex");
        int texCoord = GLES20.glGetAttribLocation(program, "texCoord");
        if (vertex < 0 || texCoord < 0) {
            throw new IllegalStateException("fullscreen attributes missing");
        }
        fullscreenVertexBuffer.position(0);
        fullscreenTexCoordBuffer.position(0);
        GLES20.glEnableVertexAttribArray(vertex);
        GLES20.glEnableVertexAttribArray(texCoord);
        GLES20.glVertexAttribPointer(
                vertex, 2, GLES20.GL_FLOAT, false, 0, fullscreenVertexBuffer);
        GLES20.glVertexAttribPointer(
                texCoord, 2, GLES20.GL_FLOAT, false, 0, fullscreenTexCoordBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(texCoord);
        GLES20.glDisableVertexAttribArray(vertex);
    }

    private void endFullscreenPass(int textureUnits) {
        for (int unit = textureUnits - 1; unit >= 0; --unit) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void drawMesh() {
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(meshProgram);

        vertexBuffer.position(0);
        heightBuffer.clear();
        heightBuffer.put(engine.gpuHeights());
        heightBuffer.position(0);
        indexBuffer.position(0);
        int position = GLES20.glGetAttribLocation(meshProgram, "aPosition");
        int heights = GLES20.glGetAttribLocation(meshProgram, "aHeights");
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glEnableVertexAttribArray(heights);
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glVertexAttribPointer(heights, 3, GLES20.GL_FLOAT, false, 0, heightBuffer);

        uniformMatrix("uMVPMatrix", mvpMatrix);
        float bitmapRatio = Math.max(surfaceWidth, surfaceHeight)
                / (float) Math.min(surfaceWidth, surfaceHeight);
        boolean landscape = surfaceWidth > surfaceHeight;
        float renderMeshWidth = landscape
                ? RippleInkPortEngine.MESH_WIDTH
                : Math.max(1, (int) (RippleInkPortEngine.MESH_WIDTH / bitmapRatio));
        float renderMeshHeight = landscape
                ? Math.max(1, (int) (RippleInkPortEngine.MESH_HEIGHT * bitmapRatio))
                : RippleInkPortEngine.MESH_HEIGHT;
        uniform1f("uMESH_SIZE_WIDTH", renderMeshWidth);
        uniform1f("uMESH_SIZE_HEIGHT", renderMeshHeight);
        uniform1f("uNUM_DETAILS_WIDTH", RippleInkPortEngine.DETAIL_WIDTH / 2.0f);
        uniform1f("uNUM_DETAILS_HEIGHT", RippleInkPortEngine.DETAIL_HEIGHT / 2.0f);
        uniform1f("uRefractiveIndex", REFRACTIVE_INDEX);
        uniform1f("alphaRatio1", REFLECTION_RATIO);
        uniform1f("fresnelRatio", FRESNEL_RATIO);
        uniform1f("specularRatio", SPECULAR_RATIO);
        uniform1f("exponent", EXPONENT_RATIO);
        uniform2f("Scale", 1.0f / surfaceWidth, 1.0f / surfaceHeight);
        uniform1f("intensity", INK_INTENSITY);
        uniform3f("ink_color",
                engine.getPaletteRed(),
                engine.getPaletteGreen(),
                engine.getPaletteBlue());
        uniform1f("uOverlayMaskLow", RippleInkPortCompositor.OVERLAY_MASK_LOW);
        uniform1f("uOverlayMaskHigh", RippleInkPortCompositor.OVERLAY_MASK_HIGH);

        bindTexture(meshProgram, "sWaterTexture", 0, reflectionTexture);
        bindTexture(meshProgram, "sBGTexture", 1, backgroundTexture);
        bindTexture(meshProgram, "Density", 2,
                densityTextures[fluidPipeline.currentDensityIndex()]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                RippleInkPortEngine.INDEX_COUNT, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        GLES20.glDisableVertexAttribArray(heights);
        GLES20.glDisableVertexAttribArray(position);
        for (int unit = 2; unit >= 0; --unit) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private int uploadBitmap(Bitmap bitmap) {
        int[] generated = new int[1];
        GLES20.glGenTextures(1, generated, 0);
        int texture = generated[0];
        if (texture == 0) {
            throw new IllegalStateException("bitmap texture allocation returned zero");
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            GLES20.glDeleteTextures(1, generated, 0);
            throw new IllegalStateException("bitmap upload GL error=0x"
                    + Integer.toHexString(error));
        }
        return texture;
    }

    private void buildMvp(int width, int height) {
        float ratio = width / (float) height;
        Matrix.setLookAtM(viewMatrix, 0,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f);
        perspectiveM(projectionMatrix, 45.0f, ratio, 0.1f, 500.0f);
        Matrix.setIdentityM(worldMatrix, 0);
        Matrix.multiplyMM(worldViewMatrix, 0, viewMatrix, 0, worldMatrix, 0);
        Matrix.translateM(worldViewMatrix, 0,
                0.0f, 0.0f, width > height ? -23.8f : -43.05f);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, worldViewMatrix, 0);
    }

    private static void perspectiveM(
            float[] matrix, float angle, float aspect, float near, float far) {
        float f = (float) Math.tan(0.5d * (Math.PI - angle));
        float range = near - far;
        Arrays.fill(matrix, 0.0f);
        matrix[0] = f / aspect;
        matrix[5] = f;
        matrix[10] = far / range;
        matrix[11] = -1.0f;
        matrix[14] = near * far / range;
    }

    private void clearTransparent() {
        GLES20.glViewport(0, 0, Math.max(1, surfaceWidth), Math.max(1, surfaceHeight));
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    private void bindTexture(int program, String uniform, int unit, int texture) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, uniform), unit);
    }

    private void uniform1f(String name, float value) {
        uniform1f(meshProgram, name, value);
    }

    private static void uniform1f(int program, String name, float value) {
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, name), value);
    }

    private static void uniform1i(int program, String name, int value) {
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, name), value);
    }

    private void uniform2f(String name, float x, float y) {
        uniform2f(meshProgram, name, x, y);
    }

    private static void uniform2f(
            int program, String name, float x, float y) {
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, name), x, y);
    }

    private void uniform3f(String name, float x, float y, float z) {
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, name), x, y, z);
    }

    private void uniformMatrix(String name, float[] value) {
        GLES20.glUniformMatrix4fv(
                GLES20.glGetUniformLocation(meshProgram, name), 1, false, value, 0);
    }

    private static int linkProgram(String vertexSource, String fragmentSource, String label) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource, label + " vertex");
        int fragment = compileShader(
                GLES20.GL_FRAGMENT_SHADER, fragmentSource, label + " fragment");
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            throw new IllegalStateException(label + " glCreateProgram returned zero");
        }
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        String log = GLES20.glGetProgramInfoLog(program);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (status[0] == 0) {
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException(label + " link failed: " + log);
        }
        return program;
    }

    private static int compileShader(int type, String source, String label) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            throw new IllegalStateException(label + " glCreateShader returned zero");
        }
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException(label + " compile failed: " + log);
        }
        return shader;
    }

    private static void checkGlError(String operation) {
        int first = GLES20.GL_NO_ERROR;
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            if (first == GLES20.GL_NO_ERROR) {
                first = error;
            }
        }
        if (first != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(operation + " GL error=0x"
                    + Integer.toHexString(first));
        }
    }

    private void fail(String detail, RuntimeException exception) {
        failed = true;
        failureDetail = detail;
        Log.e(TAG, detail, exception);
        host.onRippleInkGlesState(UnlockEffectReadiness.STATE_FAILED, detail);
    }

    private static FloatBuffer directFloatBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(values);
        buffer.position(0);
        return buffer;
    }

    private static ShortBuffer directShortBuffer(short[] values) {
        ShortBuffer buffer = ByteBuffer.allocateDirect(values.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        buffer.put(values);
        buffer.position(0);
        return buffer;
    }
}
