package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Display;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Transparent GLES host for the app-owned Coloured Droplet core. */
final class ColourDropletAppOwnedGlView extends GLSurfaceView
        implements GLSurfaceView.Renderer {
    interface Listener {
        void onSurfaceReady();
        void onResourcesReady();
        void onFirstFrame();
        void onNativeFailure(Throwable error, String detail);
    }

    private static final String TAG = "LLEColourDropletGl";
    private static final float TARGET_TICK_SECONDS = 1f / 60f;
    private static final long STALLED_FRAME_NS = 66_666_668L;
    private static final long DESTROY_TIMEOUT_MS = 500L;
    private static final int WARM_KEEP_ALIVE_FRAMES = 100;
    private static final int MAX_PENDING_COMMANDS = 100;
    private static final int COMMAND_TOUCH = 0;
    private static final int COMMAND_UNLOCK = 1;

    private static final class PendingCommand {
        final long generation;
        final int kind;
        final int touchType;
        final float x;
        final float y;
        final long eventTimeMs;

        PendingCommand(
                long generation,
                int kind,
                int touchType,
                float x,
                float y,
                long eventTimeMs) {
            this.generation = generation;
            this.kind = kind;
            this.touchType = touchType;
            this.x = x;
            this.y = y;
            this.eventTimeMs = eventTimeMs;
        }
    }

    private final Listener listener;
    private final Object bitmapLock = new Object();
    private final Object commandLock = new Object();
    private final AtomicInteger animationGeneration = new AtomicInteger();
    private final ArrayDeque<PendingCommand> pendingCommands =
            new ArrayDeque<>(MAX_PENDING_COMMANDS);
    private final Bitmap normalMap;
    private final Bitmap edgeDensityMap;
    private final int projectKind;
    private final int logicalWidth;
    private final int logicalHeight;

    private Bitmap backgroundBitmap;
    private volatile long nativeHandle;
    private volatile boolean gpuReady;
    private volatile boolean resourcesReady;
    private volatile boolean destroyed;
    private volatile long minimumRenderUntilMs;
    private long commandGeneration;
    private int surfaceWidth;
    private int surfaceHeight;
    private int drawCount;
    private volatile int keepAliveFrames;
    private int emptyFrames;
    /* Experimental mode uses stock-frame time credits across panel rates. */
    private volatile float adaptiveKeepAliveFrameCredits;
    private float adaptiveEmptyFrameCredits;
    private long lastSimulationTimeNs;
    private volatile boolean simulationClockResetPending = true;
    private final boolean nativeRefreshPhysicsEnabled;
    private final float nativeRefreshSpeedMultiplier;

    ColourDropletAppOwnedGlView(
            Context context,
            Bitmap normalMap,
            Bitmap edgeDensityMap,
            int projectKind,
            int logicalWidth,
            int logicalHeight,
            Listener listener) {
        this(context, normalMap, edgeDensityMap, projectKind, logicalWidth,
                logicalHeight, listener, false);
    }

    ColourDropletAppOwnedGlView(
            Context context,
            Bitmap normalMap,
            Bitmap edgeDensityMap,
            int projectKind,
            int logicalWidth,
            int logicalHeight,
            Listener listener,
            boolean nativeRefreshPhysicsEnabled) {
        this(context, normalMap, edgeDensityMap, projectKind, logicalWidth,
                logicalHeight, listener, nativeRefreshPhysicsEnabled, 1.0f);
    }

    ColourDropletAppOwnedGlView(
            Context context,
            Bitmap normalMap,
            Bitmap edgeDensityMap,
            int projectKind,
            int logicalWidth,
            int logicalHeight,
            Listener listener,
            boolean nativeRefreshPhysicsEnabled,
            float nativeRefreshSpeedMultiplier) {
        super(context);
        this.normalMap = normalMap;
        this.edgeDensityMap = edgeDensityMap;
        this.projectKind = projectKind;
        this.logicalWidth = Math.max(1, logicalWidth);
        this.logicalHeight = Math.max(1, logicalHeight);
        this.listener = listener;
        this.nativeRefreshPhysicsEnabled = nativeRefreshPhysicsEnabled;
        this.nativeRefreshSpeedMultiplier = nativeRefreshPhysicsEnabled
                ? normalizeNativeRefreshSpeedMultiplier(nativeRefreshSpeedMultiplier)
                : 1.0f;

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
        clearTransparent();
        try {
            if (nativeHandle == 0L) {
                nativeHandle = ColourDropletNative.nativeCreate(projectKind);
                if (nativeHandle == 0L) {
                    throw new IllegalStateException("nativeCreate returned zero");
                }
            } else {
                ColourDropletNative.nativeAbandonGpu(nativeHandle);
            }
            gpuReady = false;
            resourcesReady = false;
            drawCount = 0;
            emptyFrames = 0;
            adaptiveEmptyFrameCredits = 0.0f;
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
        int nextWidth = Math.max(1, width);
        int nextHeight = Math.max(1, height);
        try {
            boolean sameGpuContext = gpuReady;
            surfaceWidth = nextWidth;
            surfaceHeight = nextHeight;
            if (sameGpuContext) {
                gpuReady = ColourDropletNative.nativeResize(
                        nativeHandle, surfaceWidth, surfaceHeight);
            } else {
                gpuReady = ColourDropletNative.nativeInitGpu(
                        nativeHandle,
                        surfaceWidth,
                        surfaceHeight,
                        Math.min(logicalWidth, logicalHeight),
                        Math.max(logicalWidth, logicalHeight));
            }
            if (!gpuReady) {
                throw new IllegalStateException(nativeError());
            }
            if (!sameGpuContext) {
                uploadFixedResources();
            }
            ColourDropletNative.nativeResetBackgroundScale(nativeHandle);
            uploadCurrentBackground();
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
            /*
             * Stock drains the pending input batch before updateSPH. Commands
             * are therefore applied before the frame-owned fixed simulation
             * tick.
             */
            flushPendingCommandsIfReady();
            final float elapsedSeconds = simulationElapsedSecondsForDraw(
                    SystemClock.elapsedRealtimeNanos());
            final boolean stepped = nativeRefreshPhysicsEnabled
                    ? ColourDropletNative.nativeStepAtRefresh(
                            nativeHandle,
                            elapsedSeconds,
                            displayRefreshHz(),
                            nativeRefreshSpeedMultiplier)
                    : ColourDropletNative.nativeStep(nativeHandle, elapsedSeconds);
            if (!stepped) {
                throw new IllegalStateException(nativeError());
            }
            if (!ColourDropletNative.nativeDraw(
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
            if (nativeRefreshPhysicsEnabled) {
                final float stockFrameCredits = elapsedSeconds / TARGET_TICK_SECONDS;
                if (adaptiveKeepAliveFrameCredits > 0.0f) {
                    adaptiveKeepAliveFrameCredits = Math.max(
                            0.0f,
                            adaptiveKeepAliveFrameCredits - stockFrameCredits);
                }
                if (ColourDropletNative.nativeIsIdle(nativeHandle)
                        && adaptiveKeepAliveFrameCredits <= 0.0f
                        && SystemClock.uptimeMillis() >= minimumRenderUntilMs) {
                    adaptiveEmptyFrameCredits += stockFrameCredits;
                    if (adaptiveEmptyFrameCredits >= 2.0f) {
                        stopAnimationFromGlThread();
                    }
                } else {
                    adaptiveEmptyFrameCredits = 0.0f;
                }
            } else {
                if (keepAliveFrames > 0) {
                    keepAliveFrames--;
                }
                if (ColourDropletNative.nativeIsIdle(nativeHandle)
                        && keepAliveFrames <= 0
                        && SystemClock.uptimeMillis() >= minimumRenderUntilMs) {
                    if (++emptyFrames >= 2) {
                        stopAnimationFromGlThread();
                    }
                } else {
                    emptyFrames = 0;
                }
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
                        resourcesReady = ColourDropletNative.nativeUploadBitmap(
                                nativeHandle,
                                ColourDropletNative.TEXTURE_BACKGROUND,
                                bitmap);
                        if (!resourcesReady) {
                            throw new IllegalStateException(
                                    "background upload failed: " + nativeError());
                        }
                        ColourDropletNative.nativeResetBackgroundScale(nativeHandle);
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
        advanceCommandGeneration();
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
                        ColourDropletNative.nativeClearBitmap(
                                nativeHandle,
                                ColourDropletNative.TEXTURE_BACKGROUND);
                        ColourDropletNative.nativeReset(nativeHandle);
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
        final int eventType = nativeTouchType(action);
        queueCommand(new PendingCommand(
                currentCommandGeneration(),
                COMMAND_TOUCH,
                eventType,
                screenX,
                screenY,
                eventTimeMs));
        activateAnimation(0L, 2);
    }

    void sensor(final int sensorType, final float x, final float y, final float z) {
        if (destroyed) {
            return;
        }
        /*
         * A sensor sample only replaces the native gravity state. Active
         * particle animation is already continuous; while idle, queueing the
         * sample must not start a render loop on its own.
         */
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (canIssueNativeCommand()) {
                    ColourDropletNative.nativeSensor(
                            nativeHandle, sensorType, x, y, z);
                }
            }
        });
    }

    void affordance(final float screenX, final float screenY, long minimumRenderMs) {
        if (destroyed) {
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (canIssueNativeCommand()) {
                    ColourDropletNative.nativeAffordance(
                            nativeHandle, screenX, screenY);
                }
            }
        });
        activateAnimation(Math.max(0L, minimumRenderMs), 2);
    }

    void unlock() {
        if (destroyed) {
            return;
        }
        queueCommand(new PendingCommand(
                currentCommandGeneration(),
                COMMAND_UNLOCK,
                0,
                0f,
                0f,
                0L));
        activateAnimation(0L, 2);
    }

    void resetEffect() {
        if (destroyed) {
            return;
        }
        minimumRenderUntilMs = 0L;
        requestSimulationClockReset();
        final long generation = advanceCommandGeneration();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (isCurrentCommandGeneration(generation)
                        && canIssueNativeCommand()) {
                    ColourDropletNative.nativeReset(nativeHandle);
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
        requestSimulationClockReset();
        final long generation = advanceCommandGeneration();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (isCurrentCommandGeneration(generation)
                        && canIssueNativeCommand()) {
                    ColourDropletNative.nativeReset(nativeHandle);
                    ColourDropletNative.nativeResetBackgroundScale(nativeHandle);
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
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (handle != 0L) {
                            ColourDropletNative.nativeDestroy(handle);
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
        recycle(normalMap);
        recycle(edgeDensityMap);
    }

    private void uploadFixedResources() {
        if (!ColourDropletNative.nativeUploadBitmap(
                nativeHandle, ColourDropletNative.TEXTURE_NORMAL, normalMap)) {
            throw new IllegalStateException("normal-map upload failed: " + nativeError());
        }
        if (!ColourDropletNative.nativeUploadBitmap(
                nativeHandle,
                ColourDropletNative.TEXTURE_EDGE_DENSITY,
                edgeDensityMap)) {
            throw new IllegalStateException(
                    "edge-density upload failed: " + nativeError());
        }
    }

    private void uploadCurrentBackground() {
        Bitmap background;
        synchronized (bitmapLock) {
            background = backgroundBitmap;
        }
        resourcesReady = false;
        if (background == null || background.isRecycled()) {
            stopAnimationFromGlThread();
            return;
        }
        resourcesReady = ColourDropletNative.nativeUploadBitmap(
                nativeHandle,
                ColourDropletNative.TEXTURE_BACKGROUND,
                background);
        if (!resourcesReady) {
            throw new IllegalStateException(
                    "background upload failed: " + nativeError());
        }
        notifyResourcesReady();
        warmUp();
    }

    private void activateAnimation(long minimumDurationMs, int minimumFrames) {
        animationGeneration.incrementAndGet();
        minimumRenderUntilMs = Math.max(
                minimumRenderUntilMs, SystemClock.uptimeMillis() + minimumDurationMs);
        emptyFrames = 0;
        adaptiveEmptyFrameCredits = 0.0f;
        if (nativeRefreshPhysicsEnabled) {
            adaptiveKeepAliveFrameCredits = Math.max(
                    adaptiveKeepAliveFrameCredits, (float) Math.max(1, minimumFrames));
        } else {
            keepAliveFrames = Math.max(keepAliveFrames, Math.max(1, minimumFrames));
        }
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
            return nativeRefreshPhysicsEnabled ? 0.0f : TARGET_TICK_SECONDS;
        }
        long elapsedNs = nowNs - lastSimulationTimeNs;
        lastSimulationTimeNs = nowNs;
        if (elapsedNs <= 0L) {
            return 0.0f;
        }
        if (elapsedNs > STALLED_FRAME_NS) {
            return nativeRefreshPhysicsEnabled ? 0.0f : TARGET_TICK_SECONDS;
        }
        return (float) elapsedNs / 1_000_000_000.0f;
    }

    private int displayRefreshHz() {
        Display display = getDisplay();
        float refreshRate = display == null ? 60.0f : display.getRefreshRate();
        if (Float.isNaN(refreshRate) || Float.isInfinite(refreshRate)) {
            return 60;
        }
        return Math.max(30, Math.min(144, Math.round(refreshRate)));
    }

    private static float normalizeNativeRefreshSpeedMultiplier(float multiplier) {
        if (Float.isNaN(multiplier) || Float.isInfinite(multiplier)) {
            return 1.0f;
        }
        return Math.max(1.0f, Math.min(2.0f, multiplier));
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

    private long currentCommandGeneration() {
        synchronized (commandLock) {
            return commandGeneration;
        }
    }

    private long advanceCommandGeneration() {
        synchronized (commandLock) {
            commandGeneration++;
            pendingCommands.clear();
            return commandGeneration;
        }
    }

    private boolean isCurrentCommandGeneration(long generation) {
        synchronized (commandLock) {
            return generation == commandGeneration;
        }
    }

    private void queueCommand(final PendingCommand command) {
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    synchronized (commandLock) {
                        if (destroyed
                                || command.generation != commandGeneration) {
                            return;
                        }
                        /*
                         * Preserve the stock input queue: all commands are
                         * drained as one ordered batch immediately before the
                         * next simulation step.
                         */
                        enqueuePendingCommandLocked(command);
                    }
                }
            });
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    private void enqueuePendingCommandLocked(PendingCommand command) {
        if (pendingCommands.size() >= MAX_PENDING_COMMANDS) {
            boolean removedMove = false;
            Iterator<PendingCommand> iterator =
                    command.kind == COMMAND_TOUCH && command.touchType == 2
                            ? pendingCommands.descendingIterator()
                            : pendingCommands.iterator();
            while (iterator.hasNext()) {
                PendingCommand candidate = iterator.next();
                if (candidate.kind == COMMAND_TOUCH
                        && candidate.touchType == 2) {
                    iterator.remove();
                    removedMove = true;
                    break;
                }
            }
            if (!removedMove) {
                pendingCommands.removeFirst();
            }
        }
        pendingCommands.addLast(command);
    }

    private void flushPendingCommandsIfReady() {
        if (!canIssueNativeCommand() || drawCount <= 0) {
            return;
        }
        synchronized (commandLock) {
            while (!pendingCommands.isEmpty()) {
                PendingCommand command = pendingCommands.removeFirst();
                if (command.generation == commandGeneration) {
                    dispatchCommandLocked(command);
                }
            }
        }
    }

    private void dispatchCommandLocked(PendingCommand command) {
        if (command.kind == COMMAND_UNLOCK) {
            ColourDropletNative.nativeUnlock(nativeHandle);
            return;
        }
        ColourDropletNative.nativeTouch(
                nativeHandle,
                command.touchType,
                command.x,
                command.y,
                command.eventTimeMs);
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

    private int nativeTouchType(int action) {
        if (action == MotionEvent.ACTION_MOVE) {
            return 2;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            return 1;
        }
        return 0;
    }

    private void clearTransparent() {
        GLES20.glViewport(0, 0, Math.max(1, surfaceWidth), Math.max(1, surfaceHeight));
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT
                | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    private String nativeError() {
        if (nativeHandle == 0L) {
            return "native handle unavailable";
        }
        try {
            String detail = ColourDropletNative.nativeGetLastError(nativeHandle);
            return detail == null || detail.length() == 0
                    ? "unknown native error" : detail;
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
