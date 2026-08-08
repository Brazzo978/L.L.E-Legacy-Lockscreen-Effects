package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Transparent, refresh-independent GLES host for the app-owned Sparkling Bubbles core. */
final class SparklingBubblesAppOwnedGlView extends GLSurfaceView
        implements GLSurfaceView.Renderer {
    interface Listener {
        void onSurfaceReady();
        void onResourcesReady();
        void onFirstFrame();
        void onNativeFailure(Throwable error, String detail);
    }

    private static final String TAG = "LLESparklingBubblesGl";
    private static final float TARGET_TICK_SECONDS = 1f / 60f;
    private static final long STALLED_FRAME_NS = 66_666_668L;
    private static final long DESTROY_TIMEOUT_MS = 500L;
    private static final int WARM_KEEP_ALIVE_FRAMES = 100;

    private final Listener listener;
    private final Object bitmapLock = new Object();
    private final Bitmap blurMask;

    private Bitmap backgroundBitmap;
    private volatile long nativeHandle;
    private volatile boolean gpuReady;
    private volatile boolean resourcesReady;
    private volatile boolean destroyed;
    private volatile long minimumRenderUntilMs;
    private final AtomicInteger animationGeneration = new AtomicInteger();
    private int surfaceWidth;
    private int surfaceHeight;
    private int drawCount;
    private volatile int keepAliveFrames;
    private int emptyFrames;
    private long lastSimulationTimeNs;
    private volatile boolean simulationClockResetPending = true;

    SparklingBubblesAppOwnedGlView(
            Context context, Bitmap blurMask, Listener listener) {
        super(context);
        this.blurMask = blurMask;
        this.listener = listener;

        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        if (destroyed) {
            return;
        }
        clearTransparent();
        try {
            if (nativeHandle == 0L) {
                nativeHandle = SparklingBubblesNative.nativeCreate(1L);
                if (nativeHandle == 0L) {
                    throw new IllegalStateException("nativeCreate returned zero");
                }
            } else {
                SparklingBubblesNative.nativeAbandonGpu(nativeHandle);
            }
            gpuReady = false;
            resourcesReady = false;
            drawCount = 0;
            emptyFrames = 0;
            resetSimulationClockFromGlThread();
            if (listener != null) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onSurfaceReady();
                    }
                });
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (destroyed || nativeHandle == 0L || width <= 0 || height <= 0) {
            return;
        }
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        try {
            gpuReady = SparklingBubblesNative.nativeInitGpu(
                    nativeHandle, surfaceWidth, surfaceHeight);
            if (!gpuReady) {
                throw new IllegalStateException(nativeError());
            }
            if (!SparklingBubblesNative.nativeUploadBitmap(
                    nativeHandle, SparklingBubblesNative.TEXTURE_BLUR_MASK, blurMask)) {
                throw new IllegalStateException("blur mask upload failed: " + nativeError());
            }
            Bitmap background;
            synchronized (bitmapLock) {
                background = backgroundBitmap;
            }
            resourcesReady = false;
            if (background != null && !background.isRecycled()) {
                resourcesReady = SparklingBubblesNative.nativeUploadBitmap(
                        nativeHandle,
                        SparklingBubblesNative.TEXTURE_BACKGROUND,
                        background);
                if (!resourcesReady) {
                    throw new IllegalStateException(
                            "background upload failed: " + nativeError());
                }
                notifyResourcesReady();
                warmUp();
            } else {
                stopAnimationFromGlThread();
            }
            resetSimulationClockFromGlThread();
        } catch (Throwable error) {
            fail(error);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        clearTransparent();
        if (destroyed || !gpuReady || !resourcesReady || nativeHandle == 0L) {
            return;
        }
        try {
            if (!SparklingBubblesNative.nativeStep(
                    nativeHandle,
                    simulationElapsedSecondsForDraw(
                            SystemClock.elapsedRealtimeNanos()))) {
                throw new IllegalStateException(nativeError());
            }
            if (!SparklingBubblesNative.nativeDraw(
                    nativeHandle, surfaceWidth, surfaceHeight)) {
                throw new IllegalStateException(nativeError());
            }
            drawCount++;
            if (drawCount == 1 && listener != null) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onFirstFrame();
                    }
                });
            }
            if (keepAliveFrames > 0) {
                keepAliveFrames--;
            }
            if (SparklingBubblesNative.nativeIsIdle(nativeHandle)
                    && keepAliveFrames <= 0
                    && SystemClock.uptimeMillis() >= minimumRenderUntilMs) {
                if (++emptyFrames >= 2) {
                    stopAnimationFromGlThread();
                }
            } else {
                emptyFrames = 0;
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    boolean isRendererReady() {
        return !destroyed && nativeHandle != 0L && gpuReady && resourcesReady
                && drawCount > 0;
    }

    void setBackgroundBitmap(final Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        if (destroyed) {
            recycle(bitmap);
            return;
        }
        final Bitmap previous;
        synchronized (bitmapLock) {
            previous = backgroundBitmap;
            backgroundBitmap = bitmap;
        }
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    if (destroyed || !gpuReady || nativeHandle == 0L) {
                        recycle(previous);
                        return;
                    }
                    try {
                        resourcesReady = SparklingBubblesNative.nativeUploadBitmap(
                                nativeHandle,
                                SparklingBubblesNative.TEXTURE_BACKGROUND,
                                bitmap);
                        if (!resourcesReady) {
                            throw new IllegalStateException(nativeError());
                        }
                        notifyResourcesReady();
                        activateAnimation(0L, WARM_KEEP_ALIVE_FRAMES);
                    } catch (Throwable error) {
                        fail(error);
                    } finally {
                        recycle(previous);
                    }
                }
            });
            requestRender();
        } catch (RuntimeException error) {
            recycle(previous);
            fail(error);
        }
    }

    void clearBackgroundBitmap() {
        final Bitmap previous;
        synchronized (bitmapLock) {
            previous = backgroundBitmap;
            backgroundBitmap = null;
        }
        resourcesReady = false;
        stopAnimation();
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    if (!destroyed && gpuReady && nativeHandle != 0L) {
                        SparklingBubblesNative.nativeClearBitmap(
                                nativeHandle,
                                SparklingBubblesNative.TEXTURE_BACKGROUND);
                    }
                    recycle(previous);
                }
            });
        } catch (RuntimeException error) {
            recycle(previous);
            fail(error);
        }
    }

    void touch(final int action, final float screenX, final float screenY,
            final long eventTimeMs) {
        if (destroyed) {
            return;
        }
        final float[] local = toLocalCoordinates(screenX, screenY);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (canIssueNativeCommand()) {
                    SparklingBubblesNative.nativeTouch(
                            nativeHandle, action, local[0], local[1], eventTimeMs);
                }
            }
        });
        activateAnimation(0L, 2);
    }

    void affordance(final Rect screenRect, long minimumRenderMs) {
        if (destroyed) {
            return;
        }
        int[] location = new int[2];
        getLocationOnScreen(location);
        final int left = screenRect.left - location[0];
        final int top = screenRect.top - location[1];
        final int right = screenRect.right - location[0];
        final int bottom = screenRect.bottom - location[1];
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (canIssueNativeCommand()) {
                    SparklingBubblesNative.nativeAffordance(
                            nativeHandle, left, top, right, bottom);
                }
            }
        });
        activateAnimation(Math.max(0L, minimumRenderMs), 2);
    }

    void unlock() {
        if (destroyed) {
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (canIssueNativeCommand()) {
                    SparklingBubblesNative.nativeUnlock(nativeHandle);
                }
            }
        });
        activateAnimation(0L, 2);
    }

    void resetEffect() {
        if (destroyed) {
            return;
        }
        minimumRenderUntilMs = 0L;
        requestSimulationClockReset();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (canIssueNativeCommand()) {
                    SparklingBubblesNative.nativeReset(nativeHandle);
                }
            }
        });
        activateAnimation(0L, 2);
    }

    void warmUp() {
        if (!destroyed) {
            activateAnimation(0L, WARM_KEEP_ALIVE_FRAMES);
        }
    }

    void pauseRenderer() {
        stopAnimation();
    }

    void destroyRenderer() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        stopAnimation();
        final CountDownLatch finished = new CountDownLatch(1);
        final long handle = nativeHandle;
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (handle != 0L) {
                            SparklingBubblesNative.nativeDestroy(handle);
                        }
                    } finally {
                        nativeHandle = 0L;
                        gpuReady = false;
                        resourcesReady = false;
                        finished.countDown();
                    }
                }
            });
            requestRender();
            if (!finished.await(DESTROY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "bounded native teardown timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            Log.w(TAG, "GL thread unavailable during teardown", error);
        }
        onPause();
        synchronized (bitmapLock) {
            recycle(backgroundBitmap);
            backgroundBitmap = null;
        }
        recycle(blurMask);
    }

    private void activateAnimation(long minimumDurationMs, int minimumFrames) {
        animationGeneration.incrementAndGet();
        minimumRenderUntilMs = Math.max(
                minimumRenderUntilMs, SystemClock.uptimeMillis() + minimumDurationMs);
        keepAliveFrames = Math.max(keepAliveFrames, Math.max(1, minimumFrames));
        boolean wasStopped = getRenderMode() != RENDERMODE_CONTINUOUSLY;
        if (wasStopped) {
            requestSimulationClockReset();
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }
        requestRender();
    }

    private void stopAnimationFromGlThread() {
        final int generation = animationGeneration.get();
        post(new Runnable() {
            @Override
            public void run() {
                if (generation == animationGeneration.get()) {
                    stopAnimation();
                }
            }
        });
    }

    private void stopAnimation() {
        animationGeneration.incrementAndGet();
        requestSimulationClockReset();
        if (getRenderMode() != RENDERMODE_WHEN_DIRTY) {
            setRenderMode(RENDERMODE_WHEN_DIRTY);
        }
    }

    private float simulationElapsedSecondsForDraw(long nowNs) {
        if (simulationClockResetPending || lastSimulationTimeNs == 0L) {
            resetSimulationClockFromGlThread();
            lastSimulationTimeNs = nowNs;
            return TARGET_TICK_SECONDS;
        }
        long elapsedNs = nowNs - lastSimulationTimeNs;
        lastSimulationTimeNs = nowNs;
        if (elapsedNs <= 0L) {
            return 0.0f;
        }
        if (elapsedNs > STALLED_FRAME_NS) {
            return TARGET_TICK_SECONDS;
        }
        return (float) elapsedNs / 1_000_000_000.0f;
    }

    private void requestSimulationClockReset() {
        simulationClockResetPending = true;
    }

    private void resetSimulationClockFromGlThread() {
        lastSimulationTimeNs = 0L;
        simulationClockResetPending = false;
    }

    private boolean canIssueNativeCommand() {
        return !destroyed && gpuReady && resourcesReady && nativeHandle != 0L;
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

    private float[] toLocalCoordinates(float screenX, float screenY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return new float[] {screenX - location[0], screenY - location[1]};
    }

    private void clearTransparent() {
        GLES20.glViewport(0, 0, Math.max(1, surfaceWidth), Math.max(1, surfaceHeight));
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private String nativeError() {
        if (nativeHandle == 0L) {
            return "native handle unavailable";
        }
        try {
            String detail = SparklingBubblesNative.nativeGetLastError(nativeHandle);
            return detail == null || detail.length() == 0 ? "unknown native error" : detail;
        } catch (Throwable ignored) {
            return "native error unavailable";
        }
    }

    private void fail(final Throwable error) {
        stopAnimation();
        final String detail = nativeError();
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

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
