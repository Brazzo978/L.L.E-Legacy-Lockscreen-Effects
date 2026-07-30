package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Transparent GLES host for the app-owned Galaxy S6 Water Droplet core.
 *
 * <p>Every stateful JNI operation is serialized through the GLSurfaceView GL
 * thread. Like the vendor renderer, each valid continuous draw advances the
 * simulation exactly once.</p>
 */
final class S6WaterDropletAppOwnedGlView extends GLSurfaceView
        implements GLSurfaceView.Renderer {
    interface Listener {
        void onSurfaceReady();
        void onResourcesReady();
        void onSecondDrawReady();
        void onNativeFailure(Throwable error, String detail);
    }

    private static final String TAG = "LLES6WaterOwnedGl";
    private static final int QUALITY = 2;
    private static final long DETERMINISTIC_SEED = 1L;
    private static final int WARM_KEEP_ALIVE_STEPS = 100;
    private static final long DESTROY_TIMEOUT_MS = 500L;

    private final Listener listener;
    private final Object bitmapLock = new Object();
    private final Object generationLock = new Object();
    private final Bitmap normalMap;
    private final Bitmap edgeDensityMap;
    private final int projectKind;
    private final int logicalShortSide;
    private final int logicalLongSide;

    private Bitmap portraitBackground;
    private Bitmap landscapeBackground;
    private volatile long nativeHandle;
    private volatile boolean gpuReady;
    private volatile boolean resourcesReady;
    private volatile boolean destroyed;
    private volatile long minimumRenderUntilMs;
    private volatile int keepAliveSteps;
    private volatile int drawCount;
    private volatile Thread glOwnerThread;
    private long commandGeneration;
    private int surfaceWidth;
    private int surfaceHeight;
    private int idleTicks;

    S6WaterDropletAppOwnedGlView(
            Context context,
            Bitmap normalMap,
            Bitmap edgeDensityMap,
            int projectKind,
            int logicalShortSide,
            int logicalLongSide,
            Listener listener) {
        super(context);
        this.normalMap = normalMap;
        this.edgeDensityMap = edgeDensityMap;
        this.projectKind = projectKind;
        this.logicalShortSide = Math.max(1, logicalShortSide);
        this.logicalLongSide = Math.max(this.logicalShortSide, logicalLongSide);
        this.listener = listener;

        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 8);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        if (destroyed) {
            return;
        }
        glOwnerThread = Thread.currentThread();
        clearTransparent();
        try {
            if (!S6WaterDropletAppOwnedNative.isAvailable()) {
                throw new IllegalStateException("app-owned native bridge unavailable");
            }
            if (nativeHandle == 0L) {
                nativeHandle = S6WaterDropletAppOwnedNative.nativeCreate(
                        projectKind, QUALITY, DETERMINISTIC_SEED);
                if (nativeHandle == 0L) {
                    throw new IllegalStateException("nativeCreate returned zero");
                }
            } else {
                /*
                 * The Java bitmaps survive EGL loss. Native CPU/simulation
                 * state survives too; only context-owned GL names are
                 * abandoned and recreated.
                 */
                S6WaterDropletAppOwnedNative.nativeAbandonGpu(nativeHandle);
            }
            gpuReady = false;
            resourcesReady = false;
            drawCount = 0;
            idleTicks = 0;
            notifySurfaceReady();
        } catch (Throwable error) {
            fail(error);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (destroyed || nativeHandle == 0L || width <= 0 || height <= 0) {
            return;
        }
        /*
         * Window attachment can briefly report a tiny intermediate surface.
         * Stock rejects that geometry and waits for the real display-sized
         * callback instead of permanently sizing its physics world to it.
         */
        int transientSurfaceThreshold = Math.max(
                logicalShortSide, logicalLongSide) / 5;
        if (width < transientSurfaceThreshold
                || height < transientSurfaceThreshold) {
            surfaceWidth = 0;
            surfaceHeight = 0;
            return;
        }
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        try {
            boolean contextAlreadyInitialized = gpuReady;
            if (!contextAlreadyInitialized) {
                gpuReady = S6WaterDropletAppOwnedNative.nativeInitGpu(
                        nativeHandle, surfaceWidth, surfaceHeight);
                if (!gpuReady) {
                    throw new IllegalStateException(
                            "GPU init failed: " + nativeError());
                }
            }
            if (!S6WaterDropletAppOwnedNative.nativeResize(
                    nativeHandle,
                    surfaceWidth,
                    surfaceHeight,
                    logicalShortSide,
                    logicalLongSide)) {
                throw new IllegalStateException(
                        "native resize failed: " + nativeError());
            }
            if ((!contextAlreadyInitialized || !resourcesReady)
                    && hasBackgroundBitmaps()) {
                uploadAllResources();
            }
            S6WaterDropletAppOwnedNative.nativeResetBackgroundScale(nativeHandle);
            if (resourcesReady) {
                activateAnimation(0L, WARM_KEEP_ALIVE_STEPS);
            } else {
                stopAnimationFromGlThread();
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        clearTransparent();
        if (destroyed || !gpuReady || !resourcesReady
                || nativeHandle == 0L || surfaceWidth <= 0 || surfaceHeight <= 0) {
            if (!destroyed) {
                stopAnimationFromGlThread();
            }
            return;
        }
        try {
            /*
             * The first valid frame is a transparent priming frame. Stock
             * does not advance or composite the background/physics until
             * draw two.
             */
            if (drawCount == 0) {
                drawCount = 1;
                return;
            }
            if (!S6WaterDropletAppOwnedNative.nativeStep(nativeHandle)) {
                throw new IllegalStateException(
                        "native step failed: " + nativeError());
            }
            if (!S6WaterDropletAppOwnedNative.nativeDraw(
                    nativeHandle, surfaceWidth, surfaceHeight)) {
                throw new IllegalStateException(
                        "native draw failed: " + nativeError());
            }
            drawCount++;
            if (drawCount == 2) {
                notifySecondDrawReady();
            }

            keepAliveSteps = Math.max(0, keepAliveSteps - 1);
            if (S6WaterDropletAppOwnedNative.nativeIsIdle(nativeHandle)
                    && keepAliveSteps <= 0
                    && SystemClock.uptimeMillis() >= minimumRenderUntilMs) {
                idleTicks++;
                if (idleTicks >= 2) {
                    stopAnimationFromGlThread();
                }
            } else {
                idleTicks = 0;
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    boolean isTouchReady() {
        return !destroyed && nativeHandle != 0L && gpuReady && resourcesReady
                && drawCount > 1;
    }

    boolean isHintReady() {
        return !destroyed && nativeHandle != 0L && gpuReady && resourcesReady
                && drawCount > 2;
    }

    boolean hasBackgroundBitmaps() {
        synchronized (bitmapLock) {
            return valid(portraitBackground) && valid(landscapeBackground);
        }
    }

    void setBackgroundBitmaps(
            final Bitmap portrait, final Bitmap landscape) {
        if (!valid(portrait) || !valid(landscape)) {
            recycle(portrait);
            recycle(landscape);
            return;
        }
        if (destroyed) {
            recycle(portrait);
            recycle(landscape);
            return;
        }

        final Bitmap oldPortrait;
        final Bitmap oldLandscape;
        synchronized (bitmapLock) {
            oldPortrait = portraitBackground;
            oldLandscape = landscapeBackground;
            portraitBackground = portrait;
            landscapeBackground = landscape;
        }
        resourcesReady = false;
        drawCount = 0;
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (destroyed || !gpuReady || nativeHandle == 0L
                                || !isCurrentBackground(
                                        portrait, landscape)) {
                            return;
                        }
                        uploadAllResources();
                        S6WaterDropletAppOwnedNative.nativeResetBackgroundScale(
                                nativeHandle);
                        activateAnimation(0L, WARM_KEEP_ALIVE_STEPS);
                    } catch (Throwable error) {
                        fail(error);
                    } finally {
                        recycle(oldPortrait);
                        recycle(oldLandscape);
                    }
                }
            });
            requestRender();
        } catch (RuntimeException error) {
            recycle(oldPortrait);
            recycle(oldLandscape);
            fail(error);
        }
    }

    void clearBackgroundBitmaps() {
        final Bitmap oldPortrait;
        final Bitmap oldLandscape;
        synchronized (bitmapLock) {
            oldPortrait = portraitBackground;
            oldLandscape = landscapeBackground;
            portraitBackground = null;
            landscapeBackground = null;
        }
        resourcesReady = false;
        advanceCommandGeneration();
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!destroyed && nativeHandle != 0L) {
                            S6WaterDropletAppOwnedNative.nativeClearBitmap(
                                    nativeHandle,
                                    S6WaterDropletAppOwnedNative
                                            .TEXTURE_PORTRAIT_BACKGROUND);
                            S6WaterDropletAppOwnedNative.nativeClearBitmap(
                                    nativeHandle,
                                    S6WaterDropletAppOwnedNative
                                            .TEXTURE_LANDSCAPE_BACKGROUND);
                            S6WaterDropletAppOwnedNative.nativeReset(nativeHandle);
                        }
                    } finally {
                        recycle(oldPortrait);
                        recycle(oldLandscape);
                    }
                }
            });
        } catch (RuntimeException error) {
            recycle(oldPortrait);
            recycle(oldLandscape);
            fail(error);
        }
    }

    boolean touch(
            final int eventType,
            final int screenX,
            final int screenY,
            final long eventTimeMs) {
        if (!isTouchReady()) {
            return false;
        }
        final long generation = currentCommandGeneration();
        queueSerializedCommand(new Runnable() {
            @Override
            public void run() {
                if (isCurrentCommandGeneration(generation)
                        && isTouchReady()) {
                    S6WaterDropletAppOwnedNative.nativeTouch(
                            nativeHandle,
                            eventType,
                            screenX,
                            screenY,
                            eventTimeMs);
                }
            }
        });
        activateAnimation(0L, 2);
        return true;
    }

    void tilt(
            final float mappedX,
            final float mappedY,
            final long sampleTimeNanos) {
        if (!isTouchReady()) {
            return;
        }
        queueSerializedCommand(new Runnable() {
            @Override
            public void run() {
                if (canIssueNativeCommand()) {
                    S6WaterDropletAppOwnedNative.nativeTilt(
                            nativeHandle, mappedX, mappedY, sampleTimeNanos);
                }
            }
        });
        activateAnimation(0L, 2);
    }

    boolean affordance(final float screenX, final float screenY) {
        if (!isHintReady()) {
            return false;
        }
        final long generation = currentCommandGeneration();
        queueSerializedCommand(new Runnable() {
            @Override
            public void run() {
                if (isCurrentCommandGeneration(generation)
                        && isHintReady()) {
                    S6WaterDropletAppOwnedNative.nativeAffordance(
                            nativeHandle, screenX, screenY);
                }
            }
        });
        activateAnimation(0L, WARM_KEEP_ALIVE_STEPS);
        return true;
    }

    boolean unlock() {
        if (!isTouchReady()) {
            return false;
        }
        final long acceptedHandle = nativeHandle;
        queueSerializedCommand(new Runnable() {
            @Override
            public void run() {
                /*
                 * A successful terminal gesture is stock key event 91 only.
                 * Do not let a later clear-generation invalidate an unlock
                 * which was already accepted by the Java host.
                 */
                if (acceptedHandle != 0L
                        && nativeHandle == acceptedHandle) {
                    S6WaterDropletAppOwnedNative.nativeUnlock(
                            acceptedHandle);
                }
            }
        });
        activateAnimation(0L, WARM_KEEP_ALIVE_STEPS);
        return true;
    }

    void resetEffect() {
        if (destroyed) {
            return;
        }
        minimumRenderUntilMs = 0L;
        final long generation = advanceCommandGeneration();
        queueSerializedCommand(new Runnable() {
            @Override
            public void run() {
                if (isCurrentCommandGeneration(generation)
                        && nativeHandle != 0L) {
                    S6WaterDropletAppOwnedNative.nativeReset(nativeHandle);
                }
            }
        });
        activateAnimation(0L, 2);
    }

    void parkForReuse() {
        if (destroyed) {
            return;
        }
        minimumRenderUntilMs = 0L;
        final long generation = advanceCommandGeneration();
        queueSerializedCommand(new Runnable() {
            @Override
            public void run() {
                if (isCurrentCommandGeneration(generation)
                        && nativeHandle != 0L) {
                    S6WaterDropletAppOwnedNative.nativeReset(nativeHandle);
                    S6WaterDropletAppOwnedNative.nativeResetBackgroundScale(
                            nativeHandle);
                }
            }
        });
        activateAnimation(0L, 2);
    }

    void warmUp() {
        if (!destroyed) {
            activateAnimation(0L, WARM_KEEP_ALIVE_STEPS);
        }
    }

    void pauseRenderer() {
        stopAnimation();
    }

    void discardPendingCommands() {
        advanceCommandGeneration();
    }

    void destroyRenderer() {
        if (destroyed) {
            return;
        }
        advanceCommandGeneration();
        destroyed = true;
        stopAnimation();

        final CountDownLatch finished = new CountDownLatch(1);
        final long handle = nativeHandle;
        try {
            /*
             * A detached GLSurfaceView may have a paused GL thread. Resume it
             * only to drain the serialized teardown command; native ownership
             * never escapes the GL queue.
             */
            onResume();
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (handle != 0L) {
                            S6WaterDropletAppOwnedNative.nativeDestroy(handle);
                        }
                    } finally {
                        markNativeDestroyed();
                        finished.countDown();
                    }
                }
            });
            requestRender();
            if (!finished.await(DESTROY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                /*
                 * Never fall back to a UI-thread JNI destroy. If Android has
                 * already removed the GL thread, leave this now-inert handle
                 * for process teardown instead of violating GL ownership.
                 */
                Log.w(TAG, "GL teardown timed out; native handle left inert");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            Log.w(TAG, "GL thread unavailable during teardown", error);
        }
        onPause();
        synchronized (bitmapLock) {
            recycle(portraitBackground);
            recycle(landscapeBackground);
            portraitBackground = null;
            landscapeBackground = null;
        }
        recycle(normalMap);
        recycle(edgeDensityMap);
    }

    private void uploadAllResources() {
        Bitmap portrait;
        Bitmap landscape;
        synchronized (bitmapLock) {
            portrait = portraitBackground;
            landscape = landscapeBackground;
        }
        resourcesReady = false;
        if (!valid(normalMap) || !valid(edgeDensityMap)
                || !valid(portrait) || !valid(landscape)) {
            throw new IllegalStateException(
                    "one or more retained Water Droplet textures are unavailable");
        }
        upload(S6WaterDropletAppOwnedNative.TEXTURE_NORMAL,
                normalMap, "normal");
        upload(S6WaterDropletAppOwnedNative.TEXTURE_EDGE_DENSITY,
                edgeDensityMap, "edge density");
        upload(S6WaterDropletAppOwnedNative.TEXTURE_PORTRAIT_BACKGROUND,
                portrait, "portrait background");
        upload(S6WaterDropletAppOwnedNative.TEXTURE_LANDSCAPE_BACKGROUND,
                landscape, "landscape background");
        resourcesReady = true;
        notifyResourcesReady();
    }

    private void upload(int slot, Bitmap bitmap, String label) {
        if (!S6WaterDropletAppOwnedNative.nativeUploadBitmap(
                nativeHandle, slot, bitmap)) {
            throw new IllegalStateException(
                    label + " upload failed: " + nativeError());
        }
    }

    private void queueSerializedCommand(Runnable command) {
        if (destroyed) {
            return;
        }
        try {
            queueEvent(command);
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    private boolean canIssueNativeCommand() {
        return !destroyed && nativeHandle != 0L && gpuReady && resourcesReady;
    }

    private long currentCommandGeneration() {
        synchronized (generationLock) {
            return commandGeneration;
        }
    }

    private long advanceCommandGeneration() {
        synchronized (generationLock) {
            commandGeneration++;
            return commandGeneration;
        }
    }

    private boolean isCurrentCommandGeneration(long generation) {
        synchronized (generationLock) {
            return generation == commandGeneration;
        }
    }

    private boolean isCurrentBackground(
            Bitmap portrait, Bitmap landscape) {
        synchronized (bitmapLock) {
            return portraitBackground == portrait
                    && landscapeBackground == landscape;
        }
    }

    private void activateAnimation(long minimumDurationMs, int minimumSteps) {
        minimumRenderUntilMs = Math.max(
                minimumRenderUntilMs,
                SystemClock.uptimeMillis() + Math.max(0L, minimumDurationMs));
        keepAliveSteps = Math.max(
                keepAliveSteps, Math.max(1, minimumSteps));
        boolean wasStopped = getRenderMode() != RENDERMODE_CONTINUOUSLY;
        if (wasStopped) {
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }
        requestRender();
    }

    private void stopAnimationFromGlThread() {
        post(new Runnable() {
            @Override
            public void run() {
                stopAnimation();
            }
        });
    }

    private void stopAnimation() {
        if (getRenderMode() != RENDERMODE_WHEN_DIRTY) {
            setRenderMode(RENDERMODE_WHEN_DIRTY);
        }
    }

    private void markNativeDestroyed() {
        nativeHandle = 0L;
        gpuReady = false;
        resourcesReady = false;
        drawCount = 0;
        glOwnerThread = null;
    }

    private void notifySurfaceReady() {
        if (listener != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onSurfaceReady();
                }
            });
        }
    }

    private void notifyResourcesReady() {
        if (listener != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onResourcesReady();
                }
            });
        }
    }

    private void notifySecondDrawReady() {
        if (listener != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onSecondDrawReady();
                }
            });
        }
    }

    private void clearTransparent() {
        GLES20.glViewport(
                0, 0, Math.max(1, surfaceWidth), Math.max(1, surfaceHeight));
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClearDepthf(1f);
        GLES20.glClearStencil(0);
        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT
                        | GLES20.GL_DEPTH_BUFFER_BIT
                        | GLES20.GL_STENCIL_BUFFER_BIT);
    }

    private String nativeError() {
        if (nativeHandle == 0L) {
            return "native handle unavailable";
        }
        try {
            String detail =
                    S6WaterDropletAppOwnedNative.nativeGetLastError(
                            nativeHandle);
            return detail == null || detail.length() == 0
                    ? "unknown native error" : detail;
        } catch (Throwable ignored) {
            return "native error unavailable";
        }
    }

    private void fail(final Throwable error) {
        stopAnimation();
        gpuReady = false;
        resourcesReady = false;
        /*
         * nativeGetLastError is owner-thread state too. Queue failures may be
         * reported by a caller whose GLSurfaceView thread is already gone, so
         * only inspect native state while executing on the current GL owner.
         */
        final String detail = Thread.currentThread() == glOwnerThread
                ? nativeError()
                : (error.getMessage() == null
                        ? error.getClass().getSimpleName()
                        : error.getMessage());
        Log.e(TAG, detail, error);
        if (listener != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onNativeFailure(error, detail);
                }
            });
        }
    }

    private static boolean valid(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled();
    }

    private static void recycle(Bitmap bitmap) {
        if (valid(bitmap)) {
            bitmap.recycle();
        }
    }
}
