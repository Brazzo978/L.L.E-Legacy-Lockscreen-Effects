package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.android.internal.policy.impl.keyguard.sec.JniWaterRippleRender;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Isolated lifecycle host for the app-owned ARM64 S3 Water Ripple port.
 *
 * <p>This is the ARM64 beta renderer selected by the S3 picker entry. It serializes
 * simulation, bitmap upload and all GLES calls on the GLSurfaceView render thread. The original
 * process-global native semantics are preserved with an explicit single-owner gate.</p>
 */
public final class S3Arm64RippleEffectView extends GLSurfaceView
        implements UnlockEffectRenderer, BackgroundSourceRenderer {
    private static final String TAG = "LLE64S3Ripple";
    private static final long GL_CLEANUP_TIMEOUT_MS = 350L;
    private static final long LONG_PRESS_RIPPLE_MS = 600L;
    private static final int DRAG_RIPPLE_THRESHOLD_PX = 150;
    /* The original renderer advances the water solver once per frame and targets a 60 Hz
     * display. Keep that intended cadence on a monotonic clock so 60/120/144 Hz panels change
     * presentation smoothness only, never propagation speed. */
    private static final int SIMULATION_HZ = 60;
    private static final int MAX_SIMULATION_STEPS_PER_FRAME = 4;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static final AtomicReference<S3Arm64RippleEffectView> NATIVE_OWNER =
            new AtomicReference<>();

    private final RippleRenderer rippleRenderer = new RippleRenderer();
    private final Object bitmapLock = new Object();
    private final Set<Bitmap> ownedBitmaps = Collections.newSetFromMap(
            new IdentityHashMap<Bitmap, Boolean>());
    private final Object moveTrailLock = new Object();
    private final Set<Runnable> pendingMoveTrailCallbacks = Collections.newSetFromMap(
            new IdentityHashMap<Runnable, Boolean>());
    private final Object touchSoundLock = new Object();

    private volatile boolean destroyed;
    private volatile boolean paused;
    private volatile boolean externalBackground;
    private final boolean ownsNativeSlot;

    private long gestureDownTimeMs;
    private float lastScreenX;
    private float lastScreenY;
    private long backgroundSerial;
    private Runnable affordanceRunnable;
    private volatile int affordanceGeneration;
    private int moveTrailGeneration;
    private SoundPool touchSoundPool;
    private int touchDownSoundId;
    private int touchUpSoundId;

    public S3Arm64RippleEffectView(Context context) {
        super(context);
        ownsNativeSlot = NATIVE_OWNER.compareAndSet(null, this);

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(false);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setRenderer(rippleRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        initializeTouchSounds(context);

        if (!ownsNativeSlot) {
            Log.e(TAG, "Water Ripple singleton already owned; this view stays inert");
        }
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S3 Water Ripple ARM64";
    }

    /**
     * True when this view owns the process singleton and the complete v2 native overlay bridge
     * can be called. Asynchronous GL setup/render failures make subsequent checks return false.
     */
    boolean isReady() {
        return ownsNativeSlot
                && !destroyed
                && S3RippleLifecycleNative.isAvailable()
                && !rippleRenderer.hasInitializationFailed();
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        cancelPendingAffordance();
        if (!canAcceptCommands()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        gestureDownTimeMs = now;
        lastScreenX = screenX;
        lastScreenY = screenY;
        queueTouch(MotionEvent.ACTION_DOWN, screenX, screenY, now, now);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!canAcceptCommands()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long down = gestureDownTimeMs != 0L ? gestureDownTimeMs : now;
        lastScreenX = screenX;
        lastScreenY = screenY;
        queueTouch(MotionEvent.ACTION_MOVE, screenX, screenY, down, now);
    }

    /** Realigns the incremental drag origin after Samsung-style multi-touch suppression. */
    public void realignGesture(float screenX, float screenY) {
        if (!canAcceptCommands()) {
            return;
        }
        final long eventTime = SystemClock.uptimeMillis();
        final float[] local = toLocalCoordinates(screenX, screenY);
        lastScreenX = screenX;
        lastScreenY = screenY;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.realignTouch(local[0], local[1], eventTime);
            }
        });
    }

    @Override
    public void finishGesture(boolean completed) {
        finishGestureAt(lastScreenX, lastScreenY, completed);
    }

    /** Sends the terminal UP at its real screen coordinates without synthesizing a MOVE. */
    public void finishGestureAt(float screenX, float screenY, boolean completed) {
        if (!canAcceptCommands()) {
            gestureDownTimeMs = 0L;
            return;
        }
        long now = SystemClock.uptimeMillis();
        long down = gestureDownTimeMs != 0L ? gestureDownTimeMs : now;
        lastScreenX = screenX;
        lastScreenY = screenY;
        queueTouch(MotionEvent.ACTION_UP, screenX, screenY, down, now);
        gestureDownTimeMs = 0L;
    }

    @Override
    public void cancelGesture() {
        if (canAcceptCommands()) {
            long now = SystemClock.uptimeMillis();
            long down = gestureDownTimeMs != 0L ? gestureDownTimeMs : now;
            queueTouch(MotionEvent.ACTION_CANCEL, lastScreenX, lastScreenY, down, now);
        }
        gestureDownTimeMs = 0L;
    }

    @Override
    public void resetEffect() {
        cancelPendingAffordance();
        cancelPendingMoveTrails();
        if (!canAcceptCommands()) {
            return;
        }
        activateContinuousRendering();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.resetSimulation();
            }
        });
        requestRender();
    }

    @Override
    public void warmUp() {
        if (!destroyed && paused) {
            onResume();
        } else if (canAcceptCommands()) {
            requestRender();
        }
    }

    @Override
    public void showUnlockAffordance(final Rect screenRect, long startDelayMs) {
        if (!canAcceptCommands()) {
            return;
        }
        cancelPendingAffordance();
        final int generation = affordanceGeneration;
        final Rect target = screenRect == null
                ? new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()))
                : new Rect(screenRect);
        affordanceRunnable = new Runnable() {
            @Override
            public void run() {
                affordanceRunnable = null;
                if (!canAcceptCommands() || generation != affordanceGeneration) {
                    return;
                }
                final long eventTime = SystemClock.uptimeMillis();
                final float[] local = toLocalCoordinates(
                        target.exactCenterX(), target.exactCenterY());
                activateContinuousRendering();
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        rippleRenderer.injectAffordance(
                                local[0], local[1], eventTime, generation);
                    }
                });
                requestRender();
            }
        };
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalBackground;
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, final String sourceName) {
        if (!canAcceptCommands() || source == null || source.isRecycled()) {
            return;
        }

        final int targetWidth = Math.max(1, getWidth() > 0
                ? getWidth() : getResources().getDisplayMetrics().widthPixels);
        final int targetHeight = Math.max(1, getHeight() > 0
                ? getHeight() : getResources().getDisplayMetrics().heightPixels);
        final Bitmap candidate = createMappedBackground(source, targetWidth, targetHeight);
        if (candidate == null) {
            Log.e(TAG, "Could not normalize background source=" + sourceName);
            return;
        }

        final long serial;
        synchronized (bitmapLock) {
            if (destroyed) {
                candidate.recycle();
                return;
            }
            ownedBitmaps.add(candidate);
            serial = ++backgroundSerial;
        }

        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    rippleRenderer.installBackground(candidate, serial, sourceName);
                }
            });
            requestRender();
        } catch (RuntimeException exception) {
            recycleOwnedBitmap(candidate);
            Log.e(TAG, "Could not queue background upload", exception);
        }
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        final long serial;
        synchronized (bitmapLock) {
            serial = ++backgroundSerial;
        }
        externalBackground = false;
        if (!canAcceptCommands()) {
            if (paused && !destroyed) {
                rippleRenderer.clearBackgroundWhilePaused(serial);
            }
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.clearBackground(serial);
            }
        });
        requestRender();
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        gestureDownTimeMs = 0L;
        cancelPendingAffordance();
        cancelPendingMoveTrails();

        pauseRendererBounded(true);
        recycleAllOwnedBitmaps();
        releaseTouchSounds();
        externalBackground = false;
        NATIVE_OWNER.compareAndSet(this, null);
    }

    @Override
    public void onPause() {
        pauseRendererBounded(false);
    }

    @Override
    public void onResume() {
        if (destroyed || !ownsNativeSlot || !paused) {
            return;
        }
        super.onResume();
        paused = false;
        requestRender();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (paused && !destroyed) {
            onResume();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        pauseRendererBounded(false);
        super.onDetachedFromWindow();
    }

    private boolean canAcceptCommands() {
        return !destroyed && !paused && ownsNativeSlot;
    }

    private void queueTouch(
            final int action,
            float screenX,
            float screenY,
            final long downTimeMs,
            final long eventTimeMs) {
        final float[] local = toLocalCoordinates(screenX, screenY);
        activateContinuousRendering();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.handleTouch(
                        action, local[0], local[1], downTimeMs, eventTimeMs);
            }
        });
        requestRender();
    }

    private float[] toLocalCoordinates(float screenX, float screenY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return new float[] {screenX - location[0], screenY - location[1]};
    }

    private void cancelPendingAffordance() {
        ++affordanceGeneration;
        Runnable pending = affordanceRunnable;
        affordanceRunnable = null;
        if (pending != null) {
            removeCallbacks(pending);
        }
    }

    private void scheduleMoveTrail(
            final float localX, final float localY, final float strength) {
        final int generation;
        synchronized (moveTrailLock) {
            generation = moveTrailGeneration;
        }
        scheduleMoveTrailImpulse(localX, localY, strength, generation, 20L);
        scheduleMoveTrailImpulse(localX, localY, strength, generation, 40L);
    }

    private void scheduleMoveTrailImpulse(
            final float localX,
            final float localY,
            final float strength,
            final int generation,
            long delayMs) {
        final Runnable callback = new Runnable() {
            @Override
            public void run() {
                synchronized (moveTrailLock) {
                    pendingMoveTrailCallbacks.remove(this);
                    if (generation != moveTrailGeneration) {
                        return;
                    }
                }
                if (!canAcceptCommands()) {
                    return;
                }
                try {
                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            rippleRenderer.injectMoveTrail(
                                    localX, localY, strength, generation);
                        }
                    });
                    requestRender();
                } catch (RuntimeException exception) {
                    Log.w(TAG, "Could not queue delayed move ripple", exception);
                }
            }
        };

        synchronized (moveTrailLock) {
            if (destroyed || generation != moveTrailGeneration) {
                return;
            }
            pendingMoveTrailCallbacks.add(callback);
            if (!postDelayed(callback, delayMs)) {
                pendingMoveTrailCallbacks.remove(callback);
            }
        }
    }

    private void cancelPendingMoveTrails() {
        Runnable[] callbacks;
        synchronized (moveTrailLock) {
            ++moveTrailGeneration;
            callbacks = pendingMoveTrailCallbacks.toArray(
                    new Runnable[pendingMoveTrailCallbacks.size()]);
            pendingMoveTrailCallbacks.clear();
        }
        for (Runnable callback : callbacks) {
            removeCallbacks(callback);
        }
    }

    private boolean isMoveTrailGenerationCurrent(int generation) {
        synchronized (moveTrailLock) {
            return generation == moveTrailGeneration;
        }
    }

    private void initializeTouchSounds(Context context) {
        try {
            SoundPool pool = new SoundPool.Builder()
                    .setMaxStreams(10)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            int downId = pool.load(context, R.raw.s3_ripple_down, 1);
            int upId = pool.load(context, R.raw.s3_ripple_up, 1);
            synchronized (touchSoundLock) {
                touchSoundPool = pool;
                touchDownSoundId = downId;
                touchUpSoundId = upId;
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not initialize original Ripple touch sounds", exception);
        }
    }

    private void playTouchSound(boolean down) {
        if (!OverlayPrefs.unlockEffectSoundAllowedNow(getContext())
                || !systemLockSoundsEnabled()) {
            return;
        }
        synchronized (touchSoundLock) {
            int soundId = down ? touchDownSoundId : touchUpSoundId;
            if (touchSoundPool != null && soundId != 0) {
                touchSoundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
            }
        }
    }

    private boolean systemLockSoundsEnabled() {
        try {
            return Settings.System.getInt(
                    getContext().getContentResolver(), "lockscreen_sounds_enabled", 1) != 0;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private void releaseTouchSounds() {
        synchronized (touchSoundLock) {
            if (touchSoundPool != null) {
                touchSoundPool.release();
                touchSoundPool = null;
            }
            touchDownSoundId = 0;
            touchUpSoundId = 0;
        }
    }

    /** Converts Android's top-down screen Y to Samsung's down-positive ripple input axis. */
    static float mapScreenYToRippleAxis(float localY, int surfaceHeight, float yRatio) {
        if (surfaceHeight <= 0) {
            return 0.0f;
        }
        return (localY - surfaceHeight * 0.5f) * yRatio / surfaceHeight;
    }

    /** Fixed legacy solver clock; rendering may still follow the display refresh rate. */
    static final class SimulationClock {
        private static final long MAX_ACCUMULATOR_UNITS =
                NANOS_PER_SECOND * MAX_SIMULATION_STEPS_PER_FRAME;
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
            long elapsedUnits = boundedElapsedNs * SIMULATION_HZ;
            accumulatorUnits = Math.min(
                    MAX_ACCUMULATOR_UNITS,
                    accumulatorUnits + elapsedUnits);
            int steps = (int) (accumulatorUnits / NANOS_PER_SECOND);
            accumulatorUnits -= (long) steps * NANOS_PER_SECOND;
            return steps;
        }

        void reset() {
            previousFrameNs = Long.MIN_VALUE;
            accumulatorUnits = 0L;
        }
    }

    private void activateContinuousRendering() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (canAcceptCommands()) {
                setRenderMode(RENDERMODE_CONTINUOUSLY);
            }
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                if (canAcceptCommands()) {
                    setRenderMode(RENDERMODE_CONTINUOUSLY);
                }
            }
        });
    }

    private void requestIdleRenderMode(final int generation) {
        post(new Runnable() {
            @Override
            public void run() {
                if (canAcceptCommands() && rippleRenderer.isIdle(generation)) {
                    setRenderMode(RENDERMODE_WHEN_DIRTY);
                }
            }
        });
    }

    private void pauseRendererBounded(final boolean finalDestroy) {
        if (paused) {
            if (finalDestroy) {
                rippleRenderer.releaseBitmapReferences();
            }
            return;
        }

        if (rippleRenderer.isGlThread()) {
            rippleRenderer.releaseCurrentContext(finalDestroy);
        } else {
            final CountDownLatch released = new CountDownLatch(1);
            try {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            rippleRenderer.releaseCurrentContext(finalDestroy);
                        } finally {
                            released.countDown();
                        }
                    }
                });
                requestRender();
                if (!released.await(GL_CLEANUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "Timed out waiting for bounded GL cleanup");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted during bounded GL cleanup", exception);
            } catch (RuntimeException exception) {
                Log.w(TAG, "GL cleanup could not be queued", exception);
            }
        }

        super.onPause();
        paused = true;
        if (finalDestroy) {
            rippleRenderer.releaseBitmapReferences();
        }
    }

    private Bitmap createMappedBackground(Bitmap source, int width, int height) {
        Bitmap normalized;
        try {
            normalized = source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RuntimeException exception) {
            Log.e(TAG, "ARGB_8888 background copy failed", exception);
            return null;
        }
        if (normalized == null) {
            return null;
        }
        if (normalized.getWidth() == width && normalized.getHeight() == height) {
            return normalized;
        }

        float sourceRatio = normalized.getWidth() / (float) normalized.getHeight();
        float targetRatio = width / (float) height;
        Rect sourceRect;
        if (sourceRatio > targetRatio) {
            int cropWidth = Math.max(1, Math.round(normalized.getHeight() * targetRatio));
            int left = Math.max(0, (normalized.getWidth() - cropWidth) / 2);
            sourceRect = new Rect(left, 0,
                    Math.min(normalized.getWidth(), left + cropWidth),
                    normalized.getHeight());
        } else {
            int cropHeight = Math.max(1, Math.round(normalized.getWidth() / targetRatio));
            int top = Math.max(0, (normalized.getHeight() - cropHeight) / 2);
            sourceRect = new Rect(0, top, normalized.getWidth(),
                    Math.min(normalized.getHeight(), top + cropHeight));
        }

        Bitmap mapped = null;
        try {
            mapped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            new Canvas(mapped).drawBitmap(
                    normalized, sourceRect, new Rect(0, 0, width, height), paint);
            return mapped;
        } catch (RuntimeException exception) {
            if (mapped != null && !mapped.isRecycled()) {
                mapped.recycle();
            }
            Log.e(TAG, "Background crop failed", exception);
            return null;
        } finally {
            normalized.recycle();
        }
    }

    private void ownBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        synchronized (bitmapLock) {
            ownedBitmaps.add(bitmap);
        }
    }

    private void recycleOwnedBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        synchronized (bitmapLock) {
            ownedBitmaps.remove(bitmap);
        }
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void recycleAllOwnedBitmaps() {
        Bitmap[] bitmaps;
        synchronized (bitmapLock) {
            bitmaps = ownedBitmaps.toArray(new Bitmap[ownedBitmaps.size()]);
            ownedBitmaps.clear();
        }
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private final class RippleRenderer implements GLSurfaceView.Renderer {
        private static final int DETAIL_WIDTH = 104;
        private static final int DETAIL_HEIGHT = 104;
        private static final int SURFACE_WIDTH = DETAIL_WIDTH - 4;
        private static final int SURFACE_HEIGHT = DETAIL_HEIGHT - 4;
        private static final int MESH_WIDTH = 50;
        private static final int MESH_HEIGHT = 50;
        private static final int S3_PORTRAIT_X_BEGIN = 3;
        private static final int S3_PORTRAIT_Y_BEGIN = 21;
        private static final int S3_PORTRAIT_X_END = 101;
        private static final int S3_PORTRAIT_Y_END = 83;
        private static final int S3_LANDSCAPE_X_BEGIN = 21;
        private static final int S3_LANDSCAPE_Y_BEGIN = 3;
        private static final int S3_LANDSCAPE_X_END = 83;
        private static final int S3_LANDSCAPE_Y_END = 101;

        private static final float REDUCTION_RATE = 0.94f;
        private static final float WAVE_COEFFICIENT = 0.5f;
        private static final float PORTRAIT_INTENSITY = 0.5f;
        private static final float LANDSCAPE_INTENSITY = 0.35f;
        private static final float REFRACTIVE_INDEX = 0.93f;
        private static final float REFLECTION_RATIO = 0.13f;
        private static final float ALPHA_RATIO_1 = 1.0f;
        private static final float ALPHA_RATIO_2 = 1.0f;
        private static final float FRESNEL_RATIO = 0.1f;
        private static final float SPECULAR_RATIO = 0.5f;
        private static final float EXPONENT_RATIO = 20.0f;

        private final float[] vertices = new float[SURFACE_WIDTH * SURFACE_HEIGHT * 3];
        private final short[] indices = new short[
                (SURFACE_WIDTH - 1) * (SURFACE_HEIGHT - 1) * 6];
        private final float[] heights = new float[DETAIL_WIDTH * DETAIL_HEIGHT];
        private final float[] velocity = new float[DETAIL_WIDTH * DETAIL_HEIGHT];
        private final float[] gpuHeights = new float[SURFACE_WIDTH * SURFACE_HEIGHT * 3];
        private final float[] viewMatrix = new float[16];
        private final float[] projectionMatrix = new float[16];
        private final float[] worldMatrix = new float[16];
        private final float[] worldViewMatrix = new float[16];
        private final float[] mvpMatrix = new float[16];
        private final SimulationClock simulationClock = new SimulationClock();

        private Thread glThread;
        private Bitmap activeBackground;
        private Bitmap waterBitmap;
        private boolean nativeBridgeAvailable;
        private boolean meshInitialized;
        private boolean surfaceReady;
        private boolean gpuReady;
        private boolean nativeGpuInitialized;
        private boolean backgroundTextureLoaded;
        private boolean waterTextureLoaded;
        private boolean glTouched;
        private boolean renderErrorLogged;
        private volatile boolean initializationFailed;
        private volatile boolean simulationIdle = true;
        private volatile int contextGeneration;
        private int initializedGeneration;
        private int surfaceWidth;
        private int surfaceHeight;
        private int initializedWidth;
        private int initializedHeight;
        private int drawCount;
        private float previousTouchX;
        private float previousTouchY;
        private int rippleDistance;
        private long activeDownTimeMs;
        private long lastTouchEventTimeMs;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            glThread = Thread.currentThread();
            ++contextGeneration;
            initializedGeneration = 0;
            initializedWidth = 0;
            initializedHeight = 0;
            surfaceReady = true;
            gpuReady = false;
            nativeGpuInitialized = false;
            backgroundTextureLoaded = false;
            waterTextureLoaded = false;
            renderErrorLogged = false;
            initializationFailed = false;
            drawCount = 0;
            simulationClock.reset();

            clearTransparent();
            nativeBridgeAvailable = ownsNativeSlot && S3RippleLifecycleNative.isAvailable();
            if (!nativeBridgeAvailable) {
                initializationFailed = true;
                Log.w(TAG, "Full Water Ripple GLES bridge is not packaged in this build");
                return;
            }

            try {
                // Context loss invalidates old names. Never glDelete them in the new context.
                S3RippleLifecycleNative.nativeAbandonGpu();
                nativeGpuInitialized = false;
                JniWaterRippleRender.initWaters(
                        vertices,
                        indices,
                        SURFACE_WIDTH * SURFACE_HEIGHT,
                        MESH_HEIGHT,
                        MESH_WIDTH,
                        SURFACE_HEIGHT,
                        SURFACE_WIDTH);
                meshInitialized = true;
                ensureWaterBitmap();
                Log.i(TAG, "GLES context generation=" + contextGeneration);
            } catch (Throwable throwable) {
                nativeBridgeAvailable = false;
                meshInitialized = false;
                initializationFailed = true;
                Log.e(TAG, "Water Ripple context setup failed", throwable);
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            surfaceWidth = width;
            surfaceHeight = height;
            buildMvp(width, height);
            if (!surfaceReady || !nativeBridgeAvailable || !meshInitialized
                    || width <= 0 || height <= 0) {
                return;
            }

            if (initializedGeneration == contextGeneration
                    && initializedWidth == width && initializedHeight == height
                    && gpuReady) {
                return;
            }

            try {
                // A same-context resize gets a full, bounded rebuild. A new context was already
                // abandoned in onSurfaceCreated and therefore must not delete stale names.
                if (initializedGeneration == contextGeneration && nativeGpuInitialized) {
                    S3RippleLifecycleNative.nativeDestroyGpu();
                    nativeGpuInitialized = false;
                }
                gpuReady = false;
                backgroundTextureLoaded = false;
                waterTextureLoaded = false;
                if (!S3RippleLifecycleNative.nativeInitGpu()) {
                    initializationFailed = true;
                    logNativeError("GLES init failed");
                    return;
                }
                nativeGpuInitialized = true;
                gpuReady = true;
                initializationFailed = false;
                initializedGeneration = contextGeneration;
                initializedWidth = width;
                initializedHeight = height;
                remapActiveBackgroundForSurface(width, height);
                waterTextureLoaded = uploadWaterTexture();
                backgroundTextureLoaded = uploadActiveBackground();
                externalBackground = backgroundTextureLoaded;
                drawCount = 0;
                simulationClock.reset();
                Log.i(TAG, "surface initialized generation=" + contextGeneration
                        + " size=" + width + "x" + height);
            } catch (Throwable throwable) {
                gpuReady = false;
                initializationFailed = true;
                Log.e(TAG, "Water Ripple surface init failed", throwable);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            clearTransparent();
            if (!surfaceReady || !gpuReady || !meshInitialized
                    || !backgroundTextureLoaded || !waterTextureLoaded
                    || destroyed) {
                return;
            }

            try {
                float bitmapRatio = Math.max(surfaceWidth, surfaceHeight)
                        / (float) Math.max(1, Math.min(surfaceWidth, surfaceHeight));
                boolean landscape = surfaceWidth > surfaceHeight;
                int renderMeshWidth = landscape
                        ? MESH_WIDTH : Math.max(1, (int) (MESH_WIDTH / bitmapRatio));
                int renderMeshHeight = landscape
                        ? Math.max(1, (int) (MESH_HEIGHT * bitmapRatio)) : MESH_HEIGHT;

                boolean rendered = S3RippleLifecycleNative.nativeRenderNormal(
                        vertices,
                        gpuHeights,
                        indices,
                        mvpMatrix,
                        surfaceWidth,
                        surfaceHeight,
                        renderMeshWidth,
                        renderMeshHeight,
                        DETAIL_WIDTH / 2,
                        DETAIL_HEIGHT / 2,
                        REFRACTIVE_INDEX,
                        REFLECTION_RATIO,
                        ALPHA_RATIO_1,
                        ALPHA_RATIO_2,
                        FRESNEL_RATIO,
                        SPECULAR_RATIO,
                        EXPONENT_RATIO);
                if (!rendered) {
                    if (!renderErrorLogged) {
                        logNativeError("normal render failed");
                        renderErrorLogged = true;
                    }
                    gpuReady = false;
                    initializationFailed = true;
                    return;
                }

                // The original advances move() once per frame against a 60 Hz target. Decouple
                // that cadence from 60/120/144 Hz presentation while retaining draw-before-move.
                int simulationSteps = simulationClock.advance(System.nanoTime());
                if (drawCount > 0 && !simulationIdle) {
                    for (int step = 0; step < simulationSteps; ++step) {
                        stepSimulation();
                        if (simulationIdle) {
                            simulationClock.reset();
                            break;
                        }
                    }
                }
                if (drawCount < 2) {
                    ++drawCount;
                }
            } catch (Throwable throwable) {
                gpuReady = false;
                initializationFailed = true;
                Log.e(TAG, "Water Ripple frame failed", throwable);
            }
        }

        void installBackground(Bitmap candidate, long serial, String sourceName) {
            if (destroyed || serial != currentBackgroundSerial()) {
                recycleOwnedBitmap(candidate);
                return;
            }

            Bitmap previous = activeBackground;
            if (!gpuReady) {
                activeBackground = candidate;
                backgroundTextureLoaded = false;
                externalBackground = false;
                if (previous != null && previous != candidate) {
                    recycleOwnedBitmap(previous);
                }
                return;
            }

            boolean uploaded = false;
            try {
                uploaded = S3RippleLifecycleNative.nativeUploadBitmap(
                        S3RippleLifecycleNative.TEXTURE_BACKGROUND, candidate);
            } catch (Throwable throwable) {
                Log.e(TAG, "background upload threw", throwable);
            }

            if (uploaded) {
                activeBackground = candidate;
                backgroundTextureLoaded = true;
                externalBackground = true;
                if (previous != null && previous != candidate) {
                    recycleOwnedBitmap(previous);
                }
                Log.i(TAG, "background uploaded source=" + sourceName + " size="
                        + candidate.getWidth() + "x" + candidate.getHeight());
            } else {
                recycleOwnedBitmap(candidate);
                backgroundTextureLoaded = previous != null
                        && S3RippleLifecycleNative.nativeUploadBitmap(
                        S3RippleLifecycleNative.TEXTURE_BACKGROUND, previous);
                externalBackground = backgroundTextureLoaded;
                logNativeError("background upload failed");
            }
        }

        void clearBackground(long serial) {
            if (serial != currentBackgroundSerial()) {
                return;
            }
            if (gpuReady && backgroundTextureLoaded) {
                S3RippleLifecycleNative.nativeFreeTexture(
                        S3RippleLifecycleNative.TEXTURE_BACKGROUND);
            }
            backgroundTextureLoaded = false;
            externalBackground = false;
            Bitmap previous = activeBackground;
            activeBackground = null;
            recycleOwnedBitmap(previous);
        }

        void clearBackgroundWhilePaused(long serial) {
            if (serial != currentBackgroundSerial()) {
                return;
            }
            backgroundTextureLoaded = false;
            externalBackground = false;
            Bitmap previous = activeBackground;
            activeBackground = null;
            recycleOwnedBitmap(previous);
        }

        void handleTouch(
                int action,
                float localX,
                float localY,
                long downTimeMs,
                long eventTimeMs) {
            if (!meshInitialized || destroyed || eventTimeMs < lastTouchEventTimeMs) {
                return;
            }
            lastTouchEventTimeMs = eventTimeMs;
            if (simulationIdle) {
                simulationClock.reset();
            }
            simulationIdle = false;

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    glTouched = true;
                    activeDownTimeMs = downTimeMs;
                    previousTouchX = localX;
                    previousTouchY = localY;
                    rippleDistance = 0;
                    inject(localX, localY, 4.0f * currentIntensity());
                    playTouchSound(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!glTouched) {
                        break;
                    }
                    float dx = localX - previousTouchX;
                    float dy = localY - previousTouchY;
                    rippleDistance += (int) Math.sqrt(dx * dx + dy * dy);
                    previousTouchX = localX;
                    previousTouchY = localY;
                    if (rippleDistance > DRAG_RIPPLE_THRESHOLD_PX) {
                        rippleDistance = 0;
                        float trailStrength = 3.0f * currentIntensity();
                        inject(localX, localY, trailStrength);
                        scheduleMoveTrail(localX, localY, trailStrength);
                        playTouchSound(false);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (glTouched && eventTimeMs - activeDownTimeMs > LONG_PRESS_RIPPLE_MS) {
                        inject(localX, localY, 4.0f * currentIntensity());
                        playTouchSound(true);
                    }
                    glTouched = false;
                    activeDownTimeMs = 0L;
                    rippleDistance = 0;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    glTouched = false;
                    activeDownTimeMs = 0L;
                    rippleDistance = 0;
                    break;
                default:
                    break;
            }
        }

        void realignTouch(float localX, float localY, long eventTimeMs) {
            if (!meshInitialized || destroyed || eventTimeMs < lastTouchEventTimeMs) {
                return;
            }
            lastTouchEventTimeMs = eventTimeMs;
            if (glTouched) {
                previousTouchX = localX;
                previousTouchY = localY;
                rippleDistance = 0;
            }
        }

        void injectAffordance(
                float localX, float localY, long eventTimeMs, int generation) {
            if (!meshInitialized || destroyed || generation != affordanceGeneration
                    || eventTimeMs < lastTouchEventTimeMs) {
                return;
            }
            lastTouchEventTimeMs = eventTimeMs;
            if (simulationIdle) {
                simulationClock.reset();
            }
            simulationIdle = false;
            inject(localX, localY, 4.0f * currentIntensity());
        }

        void injectMoveTrail(
                float localX, float localY, float strength, int generation) {
            if (!meshInitialized || destroyed
                    || !isMoveTrailGenerationCurrent(generation)) {
                return;
            }
            if (simulationIdle) {
                simulationClock.reset();
            }
            simulationIdle = false;
            inject(localX, localY, strength);
        }

        void resetSimulation() {
            Arrays.fill(heights, 0.0f);
            Arrays.fill(velocity, 0.0f);
            Arrays.fill(gpuHeights, 0.0f);
            glTouched = false;
            rippleDistance = 0;
            activeDownTimeMs = 0L;
            drawCount = 0;
            simulationIdle = true;
            simulationClock.reset();
            requestIdleRenderMode(contextGeneration);
        }

        boolean isGlThread() {
            return Thread.currentThread() == glThread;
        }

        boolean hasInitializationFailed() {
            return initializationFailed;
        }

        boolean isIdle(int generation) {
            return generation == contextGeneration && simulationIdle && !glTouched;
        }

        void releaseCurrentContext(boolean finalDestroy) {
            if (nativeBridgeAvailable && nativeGpuInitialized) {
                try {
                    S3RippleLifecycleNative.nativeDestroyGpu();
                } catch (Throwable throwable) {
                    Log.w(TAG, "native GLES destroy failed", throwable);
                }
            }
            nativeGpuInitialized = false;
            gpuReady = false;
            surfaceReady = false;
            backgroundTextureLoaded = false;
            waterTextureLoaded = false;
            initializedGeneration = 0;
            initializedWidth = 0;
            initializedHeight = 0;
            glTouched = false;
            simulationIdle = true;
            simulationClock.reset();
            if (finalDestroy) {
                releaseBitmapReferences();
            }
        }

        void releaseBitmapReferences() {
            Bitmap background = activeBackground;
            Bitmap water = waterBitmap;
            activeBackground = null;
            waterBitmap = null;
            recycleOwnedBitmap(background);
            recycleOwnedBitmap(water);
        }

        private void stepSimulation() {
            boolean landscape = surfaceWidth > surfaceHeight;
            int xBegin = landscape ? S3_LANDSCAPE_X_BEGIN : S3_PORTRAIT_X_BEGIN;
            int yBegin = landscape ? S3_LANDSCAPE_Y_BEGIN : S3_PORTRAIT_Y_BEGIN;
            int xEnd = landscape ? S3_LANDSCAPE_X_END : S3_PORTRAIT_X_END;
            int yEnd = landscape ? S3_LANDSCAPE_Y_END : S3_PORTRAIT_Y_END;
            int empty = JniWaterRippleRender.move(
                    velocity,
                    heights,
                    xBegin,
                    yBegin,
                    xEnd,
                    yEnd,
                    DETAIL_WIDTH,
                    DETAIL_HEIGHT,
                    true,
                    REDUCTION_RATE,
                    WAVE_COEFFICIENT);
            fillGpuHeights();
            simulationIdle = empty != 0 && !glTouched;
            if (simulationIdle && drawCount >= 2) {
                requestIdleRenderMode(contextGeneration);
            }
        }

        private void fillGpuHeights() {
            // Exact original transposed packing and three-neighbor height tuple.
            for (int i = 0; i < SURFACE_HEIGHT; ++i) {
                for (int j = 0; j < SURFACE_WIDTH; ++j) {
                    int target = (SURFACE_HEIGHT * j + i) * 3;
                    gpuHeights[target] = heights[(j + 2) * DETAIL_WIDTH + i + 2];
                    gpuHeights[target + 1] = heights[(j + 2) * DETAIL_WIDTH + i + 1];
                    gpuHeights[target + 2] = heights[(j + 1) * DETAIL_WIDTH + i + 2];
                }
            }
        }

        private void inject(float localX, float localY, float strength) {
            if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                return;
            }
            boolean landscape = surfaceWidth > surfaceHeight;
            float xRatio = landscape ? 45.0f : 30.0f;
            float yRatio = landscape ? 25.0f : 46.0f;
            float glX = (localX - surfaceWidth * 0.5f) * xRatio / surfaceWidth;
            float glY = mapScreenYToRippleAxis(localY, surfaceHeight, yRatio);

            // Samsung calls ripple(glY, glX, ...); the native mesh-to-detail mapper uses this
            // swapped basis and the render path transposes the height field back.
            JniWaterRippleRender.ripple(
                    velocity,
                    MESH_WIDTH,
                    MESH_HEIGHT,
                    DETAIL_WIDTH,
                    DETAIL_HEIGHT,
                    glY,
                    glX,
                    strength);
        }

        private float currentIntensity() {
            return surfaceWidth > surfaceHeight ? LANDSCAPE_INTENSITY : PORTRAIT_INTENSITY;
        }

        private boolean uploadActiveBackground() {
            return activeBackground != null && !activeBackground.isRecycled()
                    && S3RippleLifecycleNative.nativeUploadBitmap(
                    S3RippleLifecycleNative.TEXTURE_BACKGROUND, activeBackground);
        }

        private void remapActiveBackgroundForSurface(int width, int height) {
            if (activeBackground == null || activeBackground.isRecycled()
                    || (activeBackground.getWidth() == width
                    && activeBackground.getHeight() == height)) {
                return;
            }
            Bitmap remapped = createMappedBackground(activeBackground, width, height);
            if (remapped == null) {
                return;
            }
            Bitmap previous = activeBackground;
            activeBackground = remapped;
            ownBitmap(remapped);
            recycleOwnedBitmap(previous);
        }

        private boolean uploadWaterTexture() {
            return waterBitmap != null && !waterBitmap.isRecycled()
                    && S3RippleLifecycleNative.nativeUploadBitmap(
                    S3RippleLifecycleNative.TEXTURE_WATER, waterBitmap);
        }

        private void ensureWaterBitmap() {
            if (waterBitmap != null && !waterBitmap.isRecycled()) {
                return;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded = BitmapFactory.decodeResource(
                    getResources(), R.drawable.s3_reflectionmap, options);
            if (decoded == null) {
                throw new IllegalStateException("s3_reflectionmap decode failed");
            }
            Bitmap normalized = decoded.getConfig() == Bitmap.Config.ARGB_8888
                    ? decoded : decoded.copy(Bitmap.Config.ARGB_8888, false);
            if (normalized == null) {
                decoded.recycle();
                throw new IllegalStateException("s3_reflectionmap RGBA copy failed");
            }
            if (normalized != decoded) {
                decoded.recycle();
            }
            waterBitmap = normalized;
            ownBitmap(normalized);
        }

        private void buildMvp(int width, int height) {
            if (width <= 0 || height <= 0) {
                Matrix.setIdentityM(mvpMatrix, 0);
                return;
            }
            float ratio = width / (float) height;
            Matrix.setLookAtM(
                    viewMatrix, 0,
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f);
            perspectiveM(projectionMatrix, 45.0f, ratio, 0.1f, 500.0f);
            Matrix.setIdentityM(worldMatrix, 0);
            Matrix.multiplyMM(worldViewMatrix, 0, viewMatrix, 0, worldMatrix, 0);
            Matrix.translateM(
                    worldViewMatrix,
                    0,
                    0.0f,
                    0.0f,
                    width > height ? -23.8f : -43.05f);
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, worldViewMatrix, 0);
        }

        private void perspectiveM(
                float[] matrix, float angle, float aspect, float near, float far) {
            // Exact Samsung helper, including its nonstandard direct use of angle.
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

        private void logNativeError(String prefix) {
            String detail = "";
            try {
                detail = S3RippleLifecycleNative.nativeGetLastError();
            } catch (Throwable ignored) {
                // Preserve the original failure when the bridge itself is unavailable.
            }
            Log.e(TAG, prefix + (detail == null || detail.length() == 0 ? "" : ": " + detail));
        }

        private long currentBackgroundSerial() {
            synchronized (bitmapLock) {
                return backgroundSerial;
            }
        }
    }
}
