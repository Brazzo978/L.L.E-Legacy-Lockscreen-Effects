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
import android.media.AudioManager;
import android.media.SoundPool;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Transparent GLES lifecycle host for Samsung's S5/Note 4 Brilliant Cut scene. */
final class BrilliantCutEffectView extends GLSurfaceView
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "LLEBrilliantCut";
    private static final long GL_CLEANUP_TIMEOUT_MS = 350L;
    private static final long STOCK_SIMULATION_INTERVAL_NS = 16_666_667L;
    private static final long SIMULATION_TICK_TOLERANCE_NS = 500_000L;
    private static final long DRAG_SOUND_LONG_PRESS_MS = 411L;
    private static final long DRAG_SOUND_FADE_STEP_MS = 10L;
    private static final float DRAG_SOUND_RELEASE_FADE_STEP = 0.039f;
    private static final float DRAG_SOUND_UNLOCK_FADE_STEP = 0.059f;

    private final CutRenderer cutRenderer = new CutRenderer();
    private final FrameLayout windowHost;
    private final Object bitmapLock = new Object();
    private final Object readinessLock = new Object();
    private final Set<Bitmap> ownedBitmaps = Collections.newSetFromMap(
            new IdentityHashMap<Bitmap, Boolean>());
    private final Set<Runnable> pendingAffordanceRunnables = Collections.newSetFromMap(
            new IdentityHashMap<Runnable, Boolean>());
    private final Bitmap brushTexture;
    private final SoundPool soundPool;
    private final AudioManager audioManager;
    private final int tapSound;
    private final int dragSound;
    private final int unlockSound;

    private volatile boolean destroyed;
    private volatile boolean paused;
    private volatile boolean externalBackground;
    private volatile Bitmap borrowedBackgroundPending;
    private long backgroundSerial;
    private long gestureDownAt;
    private float lastScreenX;
    private float lastScreenY;
    private volatile int animationGeneration;
    private volatile boolean animationScheduled;
    private int dragSoundStreamId;
    private float dragSoundVolume = 1f;
    private float dragSoundFadeStep = DRAG_SOUND_RELEASE_FADE_STEP;
    private int readinessState = UnlockEffectReadiness.STATE_CONSTRUCTED;
    private String readinessDetail = "constructed";
    private UnlockEffectReadiness.ReadinessListener readinessListener;

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!canAcceptCommands() || !animationScheduled) {
                return;
            }
            requestRender();
            postOnAnimation(this);
        }
    };

    private final Runnable dragSoundFadeRunnable = new Runnable() {
        @Override
        public void run() {
            stepDragSoundFade();
        }
    };

    BrilliantCutEffectView(Context context) {
        super(context);

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(false);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setRenderer(cutRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        // SurfaceView is not a safe direct WindowManager root on every Android release.
        windowHost = new WindowHost(context);
        windowHost.setBackgroundColor(Color.TRANSPARENT);
        windowHost.setClipChildren(false);
        windowHost.setClipToPadding(false);
        windowHost.addView(this, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        brushTexture = BitmapFactory.decodeResource(
                getResources(), R.drawable.brilliantcut_light_brush);
        if (brushTexture == null) {
            throw new IllegalStateException("Brilliant Cut LightBrush texture missing");
        }
        brushTexture.prepareToDraw();

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tapSound = soundPool.load(context, R.raw.brilliantcut_tap, 1);
        dragSound = soundPool.load(context, R.raw.brilliantcut_drag, 1);
        unlockSound = soundPool.load(context, R.raw.brilliantcut_unlock, 1);
    }

    @Override
    public View asView() {
        return windowHost;
    }

    @Override
    public String effectName() {
        return "Tab S Brilliant Cut";
    }

    @Override
    public int getReadinessState() {
        synchronized (readinessLock) {
            return readinessState;
        }
    }

    @Override
    public String getReadinessDetail() {
        synchronized (readinessLock) {
            return readinessDetail;
        }
    }

    @Override
    public void setReadinessListener(ReadinessListener listener) {
        synchronized (readinessLock) {
            readinessListener = listener;
        }
        notifyReadinessListener(listener);
    }

    boolean isReady() {
        return !destroyed && !cutRenderer.initializationFailed;
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRenderEffect()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        gestureDownAt = now;
        lastScreenX = screenX;
        lastScreenY = screenY;
        stopDragSound();
        playOneShot(tapSound);
        queueTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (gestureDownAt == 0L) {
            beginGesture(screenX, screenY);
            return;
        }
        if (!canRenderEffect()) {
            return;
        }
        lastScreenX = screenX;
        lastScreenY = screenY;
        maybeStartDragSound(SystemClock.uptimeMillis());
        queueTouch(MotionEvent.ACTION_MOVE, screenX, screenY);
    }

    /** Reanchors MOVE after Samsung-style multi-touch suppression without emitting a ring. */
    public void realignGesture(float screenX, float screenY) {
        if (!canRenderEffect() || gestureDownAt == 0L) {
            return;
        }
        final float[] local = toLocalCoordinates(screenX, screenY);
        lastScreenX = screenX;
        lastScreenY = screenY;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cutRenderer.pipeline.realign(local[0], local[1]);
            }
        });
    }

    @Override
    public void finishGesture(final boolean completed) {
        finishGestureAt(lastScreenX, lastScreenY, completed);
    }

    /** Sends the terminal UP at its real coordinate without synthesizing a MOVE record. */
    public void finishGestureAt(float screenX, float screenY, final boolean completed) {
        if (gestureDownAt == 0L) {
            return;
        }
        lastScreenX = screenX;
        lastScreenY = screenY;
        if (canRenderEffect()) {
            final float[] local = toLocalCoordinates(screenX, screenY);
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    cutRenderer.pipeline.touch(MotionEvent.ACTION_UP, local[0], local[1]);
                    if (completed) {
                        cutRenderer.pipeline.unlock();
                    }
                    cutRenderer.idle = false;
                }
            });
            activateAnimation();
            requestRender();
            if (completed) {
                playOneShot(unlockSound);
            }
        }
        gestureDownAt = 0L;
        fadeOutDragSound(completed
                ? DRAG_SOUND_UNLOCK_FADE_STEP
                : DRAG_SOUND_RELEASE_FADE_STEP);
    }

    @Override
    public void cancelGesture() {
        if (gestureDownAt != 0L && canRenderEffect()) {
            queueTouch(MotionEvent.ACTION_CANCEL, lastScreenX, lastScreenY);
        }
        gestureDownAt = 0L;
        fadeOutDragSound(DRAG_SOUND_RELEASE_FADE_STEP);
    }

    @Override
    public void resetEffect() {
        gestureDownAt = 0L;
        stopDragSound();
        if (!canAcceptCommands()) {
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cutRenderer.pipeline.reset();
                cutRenderer.idle = true;
            }
        });
        stopAnimation();
        requestRender();
    }

    @Override
    public void warmUp() {
        if (destroyed) {
            return;
        }
        brushTexture.prepareToDraw();
        Bitmap active = cutRenderer.activeBackground;
        if (active != null && !active.isRecycled()) {
            active.prepareToDraw();
        }
        if (paused) {
            onResume();
        } else if (canAcceptCommands()) {
            requestRender();
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (!canRenderEffect()) {
            return;
        }
        final Rect target = screenRect == null || screenRect.isEmpty()
                ? new Rect(0, 0, renderWidth(), renderHeight())
                : new Rect(screenRect);
        final Runnable affordanceRunnable = new Runnable() {
            @Override
            public void run() {
                pendingAffordanceRunnables.remove(this);
                if (!canRenderEffect()) {
                    return;
                }
                final float[] local = toLocalCoordinates(
                        target.centerX(), target.centerY());
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        cutRenderer.pipeline.affordance(local[0], local[1]);
                        cutRenderer.idle = false;
                    }
                });
                activateAnimation();
                requestRender();
            }
        };
        pendingAffordanceRunnables.add(affordanceRunnable);
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalBackground;
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, final String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        int width = renderWidth();
        int height = renderHeight();
        final boolean borrowed = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, width, height);
        final Bitmap mapped = borrowed ? source : createMappedBackground(source, width, height);
        if (mapped == null) {
            Log.e(TAG, "Could not normalize background source=" + sourceName);
            return;
        }
        mapped.prepareToDraw();
        final long serial;
        synchronized (bitmapLock) {
            if (destroyed) {
                if (!borrowed) {
                    mapped.recycle();
                }
                return;
            }
            if (!borrowed) {
                ownedBitmaps.add(mapped);
            }
            borrowedBackgroundPending = borrowed ? mapped : null;
            serial = ++backgroundSerial;
        }
        externalBackground = false;
        invalidateResourceReadiness("background pending");
        if (paused) {
            cutRenderer.stageBackground(mapped, serial);
            return;
        }
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    cutRenderer.installBackground(mapped, serial, sourceName);
                }
            });
            requestRender();
        } catch (RuntimeException error) {
            borrowedBackgroundPending = null;
            recycleOwnedBitmap(mapped);
            Log.e(TAG, "Could not queue Brilliant Cut background upload", error);
        }
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        final long serial;
        synchronized (bitmapLock) {
            serial = ++backgroundSerial;
        }
        externalBackground = false;
        borrowedBackgroundPending = null;
        invalidateResourceReadiness("background cleared");
        if (!canAcceptCommands()) {
            cutRenderer.clearBackgroundReference(serial);
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cutRenderer.clearBackground(serial);
            }
        });
        requestRender();
    }

    @Override
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && (borrowedBackgroundPending == bitmap
                || cutRenderer.activeBackground == bitmap);
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        gestureDownAt = 0L;
        cancelPendingAffordance();
        stopAnimation();
        stopDragSound();
        releaseGlBounded(true);
        soundPool.release();
        recycleAllOwnedBitmaps();
        borrowedBackgroundPending = null;
        externalBackground = false;
        if (!brushTexture.isRecycled()) {
            brushTexture.recycle();
        }
        setReadinessState(UnlockEffectReadiness.STATE_FAILED, "renderer destroyed");
        synchronized (readinessLock) {
            readinessListener = null;
        }
    }

    @Override
    public void onPause() {
        releaseGlBounded(false);
    }

    @Override
    public void onResume() {
        if (destroyed || !paused) {
            return;
        }
        super.onResume();
        paused = false;
        advanceReadiness(UnlockEffectReadiness.STATE_ATTACHED, "resumed");
        requestRender();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        advanceReadiness(UnlockEffectReadiness.STATE_ATTACHED, "attached");
        if (paused && !destroyed) {
            onResume();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseGlBounded(false);
        super.onDetachedFromWindow();
    }

    private boolean canAcceptCommands() {
        return !destroyed && !paused && !cutRenderer.initializationFailed;
    }

    private boolean canRenderEffect() {
        return canAcceptCommands() && externalBackground;
    }

    private void queueTouch(final int action, float screenX, float screenY) {
        final float[] local = toLocalCoordinates(screenX, screenY);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cutRenderer.pipeline.touch(action, local[0], local[1]);
                cutRenderer.idle = false;
            }
        });
        activateAnimation();
        requestRender();
    }

    private float[] toLocalCoordinates(float screenX, float screenY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        // Samsung's LockBG bridge casts the incoming touch packet to int before JNI.
        return new float[] {(int) screenX - location[0], (int) screenY - location[1]};
    }

    private void activateAnimation() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(new Runnable() {
                @Override
                public void run() {
                    activateAnimation();
                }
            });
            return;
        }
        ++animationGeneration;
        if (!animationScheduled && canAcceptCommands()) {
            animationScheduled = true;
            removeCallbacks(animationRunnable);
            post(animationRunnable);
        }
    }

    private void requestStopAnimation(final int generation) {
        post(new Runnable() {
            @Override
            public void run() {
                if (generation == animationGeneration && cutRenderer.idle) {
                    stopAnimation();
                }
            }
        });
    }

    private void stopAnimation() {
        ++animationGeneration;
        animationScheduled = false;
        removeCallbacks(animationRunnable);
    }

    private void cancelPendingAffordance() {
        Runnable[] pending = pendingAffordanceRunnables.toArray(
                new Runnable[pendingAffordanceRunnables.size()]);
        pendingAffordanceRunnables.clear();
        for (Runnable runnable : pending) {
            removeCallbacks(runnable);
        }
    }

    private void releaseGlBounded(final boolean finalDestroy) {
        if (paused) {
            if (finalDestroy) {
                cutRenderer.releaseBitmapReferences();
            }
            return;
        }
        stopAnimation();
        if (cutRenderer.isGlThread()) {
            cutRenderer.releaseCurrentContext(finalDestroy);
        } else if (cutRenderer.hasGlThread()) {
            final CountDownLatch released = new CountDownLatch(1);
            try {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            cutRenderer.releaseCurrentContext(finalDestroy);
                        } finally {
                            released.countDown();
                        }
                    }
                });
                requestRender();
                if (!released.await(GL_CLEANUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "Timed out waiting for Brilliant Cut GL cleanup");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted during Brilliant Cut GL cleanup", error);
            } catch (RuntimeException error) {
                Log.w(TAG, "Brilliant Cut GL cleanup could not be queued", error);
            }
        } else if (finalDestroy) {
            cutRenderer.pipeline.abandon();
            cutRenderer.releaseBitmapReferences();
        }
        super.onPause();
        paused = true;
        markReadinessDetached(finalDestroy ? "destroyed" : "context released");
    }

    private void advanceReadiness(int state, String detail) {
        ReadinessListener listener;
        synchronized (readinessLock) {
            if (readinessState == UnlockEffectReadiness.STATE_FAILED
                    || state <= readinessState) {
                return;
            }
            readinessState = state;
            readinessDetail = detail;
            listener = readinessListener;
        }
        notifyReadinessListener(listener);
    }

    private void setReadinessState(int state, String detail) {
        ReadinessListener listener;
        synchronized (readinessLock) {
            if (readinessState == state && readinessDetail.equals(detail)) {
                return;
            }
            readinessState = state;
            readinessDetail = detail;
            listener = readinessListener;
        }
        notifyReadinessListener(listener);
    }

    private void invalidateResourceReadiness(String detail) {
        ReadinessListener listener = null;
        synchronized (readinessLock) {
            if (readinessState >= UnlockEffectReadiness.STATE_RESOURCES_READY) {
                readinessState = UnlockEffectReadiness.STATE_SURFACE_READY;
                readinessDetail = detail;
                listener = readinessListener;
            }
        }
        notifyReadinessListener(listener);
    }

    private void failReadiness(String detail) {
        setReadinessState(UnlockEffectReadiness.STATE_FAILED, detail);
    }

    private void markReadinessDetached(String detail) {
        setReadinessState(UnlockEffectReadiness.STATE_DETACHED, detail);
    }

    private void notifyReadinessListener(ReadinessListener listener) {
        if (listener == null) {
            return;
        }
        try {
            listener.onReadinessChanged();
        } catch (RuntimeException error) {
            Log.w(TAG, "Brilliant Cut readiness listener failed", error);
        }
    }

    private int renderWidth() {
        if (getWidth() > 0) {
            return getWidth();
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(1, metrics.widthPixels);
    }

    private int renderHeight() {
        if (getHeight() > 0) {
            return getHeight();
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(1, metrics.heightPixels);
    }

    private Bitmap createMappedBackground(Bitmap source, int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        Bitmap mapped = null;
        try {
            mapped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            float sourceRatio = source.getWidth() / (float) source.getHeight();
            float targetRatio = width / (float) height;
            Rect sourceRect;
            if (sourceRatio > targetRatio) {
                int cropWidth = Math.max(1, Math.round(source.getHeight() * targetRatio));
                int left = Math.max(0, (source.getWidth() - cropWidth) / 2);
                sourceRect = new Rect(left, 0,
                        Math.min(source.getWidth(), left + cropWidth), source.getHeight());
            } else {
                int cropHeight = Math.max(1, Math.round(source.getWidth() / targetRatio));
                int top = Math.max(0, (source.getHeight() - cropHeight) / 2);
                sourceRect = new Rect(0, top, source.getWidth(),
                        Math.min(source.getHeight(), top + cropHeight));
            }
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            new Canvas(mapped).drawBitmap(
                    source, sourceRect, new Rect(0, 0, width, height), paint);
            return mapped;
        } catch (RuntimeException error) {
            if (mapped != null && !mapped.isRecycled()) {
                mapped.recycle();
            }
            Log.e(TAG, "Brilliant Cut background crop failed", error);
            return null;
        }
    }

    private void recycleOwnedBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        synchronized (bitmapLock) {
            if (!ownedBitmaps.remove(bitmap)) {
                return;
            }
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

    private long currentBackgroundSerial() {
        synchronized (bitmapLock) {
            return backgroundSerial;
        }
    }

    private void playOneShot(int soundId) {
        if (soundId != 0 && !destroyed && canPlaySound()) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private boolean canPlaySound() {
        if (!OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            return false;
        }
        if (Settings.System.getInt(getContext().getContentResolver(),
                "lockscreen_sounds_enabled", 1) == 0) {
            return false;
        }
        return audioManager == null
                || audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM) > 0;
    }

    private void maybeStartDragSound(long now) {
        if (dragSoundStreamId != 0 || dragSound == 0 || gestureDownAt == 0L
                || now - gestureDownAt <= DRAG_SOUND_LONG_PRESS_MS || !canPlaySound()) {
            return;
        }
        removeCallbacks(dragSoundFadeRunnable);
        dragSoundVolume = 1f;
        dragSoundStreamId = soundPool.play(dragSound, 1f, 1f, 0, -1, 1f);
    }

    private void fadeOutDragSound(float step) {
        if (dragSoundStreamId == 0) {
            return;
        }
        dragSoundFadeStep = step;
        removeCallbacks(dragSoundFadeRunnable);
        post(dragSoundFadeRunnable);
    }

    private void stepDragSoundFade() {
        if (dragSoundStreamId == 0 || destroyed) {
            return;
        }
        dragSoundVolume = Math.max(0f, dragSoundVolume - dragSoundFadeStep);
        soundPool.setVolume(dragSoundStreamId, dragSoundVolume, dragSoundVolume);
        if (dragSoundVolume > 0f) {
            postDelayed(dragSoundFadeRunnable, DRAG_SOUND_FADE_STEP_MS);
        } else {
            stopDragSound();
        }
    }

    private void stopDragSound() {
        removeCallbacks(dragSoundFadeRunnable);
        if (dragSoundStreamId != 0) {
            soundPool.stop(dragSoundStreamId);
            dragSoundStreamId = 0;
        }
        dragSoundVolume = 1f;
    }

    private final class CutRenderer implements GLSurfaceView.Renderer {
        final BrilliantCutGlesPipeline pipeline = new BrilliantCutGlesPipeline();
        volatile Bitmap activeBackground;
        volatile boolean initializationFailed;
        volatile boolean idle = true;
        private Thread glThread;
        private boolean surfaceReady;
        private boolean backgroundReady;
        private int contextGeneration;
        private int initializedGeneration;
        private int initializedWidth;
        private int initializedHeight;
        private int width = 1;
        private int height = 1;
        private long lastDrawNs;
        private long simulationAccumulatorNs;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            glThread = Thread.currentThread();
            ++contextGeneration;
            initializedGeneration = 0;
            initializedWidth = 0;
            initializedHeight = 0;
            surfaceReady = true;
            backgroundReady = false;
            initializationFailed = false;
            idle = true;
            resetSimulationClock();
            pipeline.abandon();
            clearTransparent();
            setReadinessState(UnlockEffectReadiness.STATE_SURFACE_READY,
                    "surface generation=" + contextGeneration);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int surfaceWidth, int surfaceHeight) {
            width = Math.max(1, surfaceWidth);
            height = Math.max(1, surfaceHeight);
            GLES20.glViewport(0, 0, width, height);
            if (!surfaceReady || surfaceWidth <= 0 || surfaceHeight <= 0) {
                return;
            }
            boolean needsInitialization = initializedGeneration != contextGeneration
                    || initializedWidth != width || initializedHeight != height
                    || !pipeline.isInitialized();
            if (needsInitialization) {
                setReadinessState(UnlockEffectReadiness.STATE_SURFACE_READY,
                        "surface resize " + width + "x" + height);
                try {
                    pipeline.initialize(width, height, brushTexture);
                    initializedGeneration = contextGeneration;
                    initializedWidth = width;
                    initializedHeight = height;
                    backgroundReady = false;
                    resetSimulationClock();
                } catch (RuntimeException error) {
                    initializationFailed = true;
                    failReadiness("GLES initialization failed: "
                            + error.getClass().getSimpleName());
                    Log.e(TAG, "Brilliant Cut GLES initialization failed", error);
                    return;
                }
            }
            remapBackgroundForSurface(width, height);
            if (!initializationFailed && activeBackground != null && !backgroundReady) {
                backgroundReady = pipeline.uploadBackground(activeBackground);
                externalBackground = backgroundReady;
                if (!backgroundReady) {
                    initializationFailed = true;
                    failReadiness("background upload failed");
                    return;
                }
            }
            publishResourcesReadyIfComplete();
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            clearTransparent();
            if (!surfaceReady || initializationFailed || !backgroundReady || destroyed) {
                idle = true;
                return;
            }
            try {
                boolean needsMoreFrames = pipeline.renderFrame(consumeSimulationTick());
                advanceReadiness(UnlockEffectReadiness.STATE_FIRST_FRAME_READY,
                        "first transparent GLES frame");
                idle = !needsMoreFrames;
                if (idle) {
                    requestStopAnimation(animationGeneration);
                }
            } catch (RuntimeException error) {
                initializationFailed = true;
                idle = true;
                clearTransparent();
                failReadiness("GLES draw failed: " + error.getClass().getSimpleName());
                Log.e(TAG, "Brilliant Cut GLES draw failed", error);
            }
        }

        private boolean consumeSimulationTick() {
            long now = System.nanoTime();
            if (lastDrawNs == 0L) {
                lastDrawNs = now;
                simulationAccumulatorNs = 0L;
                return true;
            }
            long elapsed = now - lastDrawNs;
            lastDrawNs = now;
            if (elapsed <= 0L) {
                return false;
            }
            // An idle gap starts one fresh stock frame; it never catches up dozens of old ticks.
            if (elapsed >= STOCK_SIMULATION_INTERVAL_NS * 2L) {
                simulationAccumulatorNs = 0L;
                return true;
            }
            simulationAccumulatorNs += elapsed;
            if (simulationAccumulatorNs + SIMULATION_TICK_TOLERANCE_NS
                    < STOCK_SIMULATION_INTERVAL_NS) {
                return false;
            }
            simulationAccumulatorNs = Math.max(0L,
                    simulationAccumulatorNs - STOCK_SIMULATION_INTERVAL_NS);
            return true;
        }

        private void resetSimulationClock() {
            lastDrawNs = 0L;
            simulationAccumulatorNs = 0L;
        }

        void installBackground(Bitmap bitmap, long serial, String sourceName) {
            if (serial != currentBackgroundSerial()) {
                recycleOwnedBitmap(bitmap);
                return;
            }
            if (borrowedBackgroundPending == bitmap) {
                borrowedBackgroundPending = null;
            }
            Bitmap previous = activeBackground;
            activeBackground = bitmap;
            if (previous != bitmap) {
                recycleOwnedBitmap(previous);
            }
            boolean pipelineReady = surfaceReady
                    && initializedGeneration == contextGeneration
                    && pipeline.isInitialized();
            backgroundReady = pipelineReady && pipeline.uploadBackground(bitmap);
            externalBackground = backgroundReady;
            if (backgroundReady) {
                publishResourcesReadyIfComplete();
            } else if (pipelineReady) {
                initializationFailed = true;
                failReadiness("background upload failed source=" + sourceName);
                Log.e(TAG, "Brilliant Cut background upload failed source=" + sourceName);
            }
        }

        void stageBackground(Bitmap bitmap, long serial) {
            if (serial != currentBackgroundSerial()) {
                recycleOwnedBitmap(bitmap);
                return;
            }
            if (borrowedBackgroundPending == bitmap) {
                borrowedBackgroundPending = null;
            }
            Bitmap previous = activeBackground;
            activeBackground = bitmap;
            if (previous != bitmap) {
                recycleOwnedBitmap(previous);
            }
            backgroundReady = false;
            externalBackground = false;
        }

        void clearBackground(long serial) {
            if (serial != currentBackgroundSerial()) {
                return;
            }
            clearBackgroundReference(serial);
            backgroundReady = false;
            externalBackground = false;
            pipeline.reset();
            idle = true;
            clearTransparent();
        }

        void clearBackgroundReference(long serial) {
            if (serial != currentBackgroundSerial()) {
                return;
            }
            Bitmap previous = activeBackground;
            activeBackground = null;
            recycleOwnedBitmap(previous);
        }

        boolean hasGlThread() {
            return glThread != null;
        }

        boolean isGlThread() {
            return glThread != null && Thread.currentThread() == glThread;
        }

        void releaseCurrentContext(boolean finalDestroy) {
            stopAnimation();
            clearTransparent();
            pipeline.release();
            surfaceReady = false;
            backgroundReady = false;
            externalBackground = false;
            initializedGeneration = 0;
            initializedWidth = 0;
            initializedHeight = 0;
            idle = true;
            markReadinessDetached(finalDestroy ? "destroyed" : "context released");
            if (finalDestroy) {
                releaseBitmapReferences();
            }
        }

        void releaseBitmapReferences() {
            Bitmap previous = activeBackground;
            activeBackground = null;
            recycleOwnedBitmap(previous);
        }

        private void publishResourcesReadyIfComplete() {
            if (surfaceReady && backgroundReady && !initializationFailed) {
                advanceReadiness(UnlockEffectReadiness.STATE_RESOURCES_READY,
                        "GLES programs, FBOs, LightBrush and background ready");
                requestRender();
            }
        }

        private void remapBackgroundForSurface(int surfaceWidth, int surfaceHeight) {
            Bitmap active = activeBackground;
            if (active == null || active.isRecycled()
                    || active.getWidth() == surfaceWidth
                    && active.getHeight() == surfaceHeight) {
                return;
            }
            Bitmap remapped = createMappedBackground(active, surfaceWidth, surfaceHeight);
            if (remapped == null) {
                return;
            }
            Bitmap previous = activeBackground;
            activeBackground = remapped;
            synchronized (bitmapLock) {
                ownedBitmaps.add(remapped);
            }
            recycleOwnedBitmap(previous);
        }

        private void clearTransparent() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }
    }

    private final class WindowHost extends FrameLayout {
        WindowHost(Context context) {
            super(context);
        }

        @Override
        public void setAlpha(float alpha) {
            super.setAlpha(alpha);
            BrilliantCutEffectView.this.setAlpha(alpha);
        }

        @Override
        public void setVisibility(int visibility) {
            super.setVisibility(visibility);
            BrilliantCutEffectView.this.setVisibility(visibility);
        }
    }
}
