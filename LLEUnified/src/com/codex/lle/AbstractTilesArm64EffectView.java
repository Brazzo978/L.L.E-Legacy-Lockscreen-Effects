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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Lifecycle host for LLE's standalone ARM64 reconstruction of N4 Abstract Tiles. */
public final class AbstractTilesArm64EffectView extends GLSurfaceView
        implements UnlockEffectRenderer, BackgroundSourceRenderer,
        DebugAbstractTilesCaptureRenderer {
    private static final String TAG = "LLE64AbstractTiles";
    private static final long GL_CLEANUP_TIMEOUT_MS = 350L;
    /* The legacy renderer presents the 400 ms Line track at roughly 60 fps.
     * A 33 ms request loop sampled it at only twelve visible steps and made the
     * otherwise exact cosine movement look different on both 60 and 120 Hz panels. */
    private static final long FRAME_INTERVAL_MS = 16L;
    private static final long DRAG_SOUND_LONG_PRESS_MS = 411L;
    private static final long DRAG_SOUND_FADE_STEP_MS = 10L;
    private static final float DRAG_SOUND_RELEASE_FADE_STEP = 0.039f;
    private static final float DRAG_SOUND_UNLOCK_FADE_STEP = 0.059f;
    private static final long AFFORDANCE_DEDUP_WINDOW_MS = 2_500L;

    private static final AtomicReference<AbstractTilesArm64EffectView> NATIVE_OWNER =
            new AtomicReference<AbstractTilesArm64EffectView>();

    private final TileRenderer tileRenderer = new TileRenderer();
    private final FrameLayout windowHost;
    private final Object bitmapLock = new Object();
    private final Set<Bitmap> ownedBitmaps = Collections.newSetFromMap(
            new IdentityHashMap<Bitmap, Boolean>());
    private final boolean ownsNativeSlot;
    private final boolean lineEnabled;
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
    private long gestureDownTimeMs;
    private float lastScreenX;
    private float lastScreenY;
    private long lastAffordanceQueuedAt;
    private Runnable affordanceRunnable;
    private int affordanceGeneration;
    private volatile int animationGeneration;
    private volatile boolean animationScheduled;
    private int dragSoundStreamId;
    private float dragSoundVolume = 1.0f;
    private float dragSoundFadeStep = DRAG_SOUND_RELEASE_FADE_STEP;
    private boolean dragSoundFading;
    private int debugCaptureGeneration;

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!canAcceptCommands() || !animationScheduled) {
                return;
            }
            requestRender();
            postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    private final Runnable dragSoundFadeRunnable = new Runnable() {
        @Override
        public void run() {
            stepDragSoundFade();
        }
    };

    public AbstractTilesArm64EffectView(Context context) {
        super(context);
        lineEnabled = OverlayPrefs.abstractTilesLineEnabled(context);
        ownsNativeSlot = NATIVE_OWNER.compareAndSet(null, this);

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(false);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setRenderer(tileRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        /*
         * A SurfaceView cannot safely be the direct WindowManager root on every Android
         * release: SurfaceView.onAttachedToWindow expects a real ViewGroup parent. Keep the
         * GL view inside a transparent host before the accessibility overlay is attached.
         */
        windowHost = new WindowHost(context);
        windowHost.setBackgroundColor(Color.TRANSPARENT);
        windowHost.setClipChildren(false);
        windowHost.setClipToPadding(false);
        windowHost.addView(this, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tapSound = soundPool.load(context, R.raw.abstracttile_tap, 1);
        dragSound = soundPool.load(context, R.raw.abstracttile_drag, 1);
        unlockSound = soundPool.load(context, R.raw.abstracttile_unlock, 1);

        if (!ownsNativeSlot) {
            Log.e(TAG, "Abstract Tiles singleton already owned; this view stays inert");
        }
    }

    boolean isReady() {
        return ownsCurrentNativeSlot()
                && !destroyed
                && AbstractTilesNative.isAvailable()
                && !tileRenderer.hasInitializationFailed();
    }

    @Override
    public View asView() {
        return windowHost;
    }

    @Override
    public String effectName() {
        return "N4 Abstract Tiles ARM64 · Line " + (lineEnabled ? "ON" : "OFF");
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        cancelPendingAffordance();
        if (!canRenderEffect()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        gestureDownTimeMs = now;
        lastScreenX = screenX;
        lastScreenY = screenY;
        stopDragSoundImmediately();
        dragSoundVolume = 1.0f;
        playOneShot(tapSound);
        queueTouch(MotionEvent.ACTION_DOWN, screenX, screenY, now);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (gestureDownTimeMs == 0L) {
            beginGesture(screenX, screenY);
            return;
        }
        if (!canRenderEffect()) {
            return;
        }
        maybeStartDragSound();
        lastScreenX = screenX;
        lastScreenY = screenY;
        queueTouch(MotionEvent.ACTION_MOVE, screenX, screenY, SystemClock.uptimeMillis());
    }

    /** Realigns the native MOVE anchor after Samsung-style multi-touch suppression. */
    public void realignGesture(float screenX, float screenY) {
        if (!canRenderEffect() || gestureDownTimeMs == 0L) {
            return;
        }
        final float[] local = toLocalCoordinates(screenX, screenY);
        final long eventTimeMs = SystemClock.uptimeMillis();
        lastScreenX = screenX;
        lastScreenY = screenY;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                AbstractTilesNative.nativeRealign(local[0], local[1], eventTimeMs);
            }
        });
    }

    @Override
    public void finishGesture(boolean completed) {
        if (gestureDownTimeMs == 0L) {
            return;
        }
        if (canRenderEffect()) {
            queueTouch(MotionEvent.ACTION_UP, lastScreenX, lastScreenY,
                    SystemClock.uptimeMillis());
            if (completed) {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        AbstractTilesNative.nativeUnlock();
                    }
                });
                playOneShot(unlockSound);
                activateAnimation();
            }
        }
        gestureDownTimeMs = 0L;
        fadeOutDragSound(completed
                ? DRAG_SOUND_UNLOCK_FADE_STEP : DRAG_SOUND_RELEASE_FADE_STEP);
    }

    @Override
    public void cancelGesture() {
        if (gestureDownTimeMs != 0L && canRenderEffect()) {
            queueTouch(MotionEvent.ACTION_CANCEL, lastScreenX, lastScreenY,
                    SystemClock.uptimeMillis());
        }
        gestureDownTimeMs = 0L;
        fadeOutDragSound(DRAG_SOUND_RELEASE_FADE_STEP);
    }

    @Override
    public void resetEffect() {
        cancelPendingAffordance();
        gestureDownTimeMs = 0L;
        stopDragSoundImmediately();
        if (!canAcceptCommands()) {
            return;
        }
        activateAnimation();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                AbstractTilesNative.nativeReset();
                tileRenderer.resetClock();
            }
        });
        requestRender();
    }

    @Override
    public void warmUp() {
        if (destroyed) {
            return;
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
        long now = SystemClock.uptimeMillis();
        if (lastAffordanceQueuedAt > 0L
                && now - lastAffordanceQueuedAt < AFFORDANCE_DEDUP_WINDOW_MS) {
            return;
        }
        cancelPendingAffordance();
        lastAffordanceQueuedAt = now;
        final int generation = affordanceGeneration;
        final Rect target = screenRect == null || screenRect.isEmpty()
                ? new Rect(0, 0, getRenderWidth(), getRenderHeight())
                : new Rect(screenRect);
        affordanceRunnable = new Runnable() {
            @Override
            public void run() {
                affordanceRunnable = null;
                if (!canRenderEffect() || generation != affordanceGeneration) {
                    return;
                }
                int[] location = new int[2];
                getLocationOnScreen(location);
                final int left = target.left - location[0];
                final int top = target.top - location[1];
                final int right = target.right - location[0];
                final int bottom = target.bottom - location[1];
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        AbstractTilesNative.nativeAffordance(left, top, right, bottom);
                    }
                });
                activateAnimation();
                requestRender();
            }
        };
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
    }

    /** Captures a deterministic Abstract Tiles sequence directly from the GLES surface. */
    @Override
    public void captureDebugAbstractTilesFrame(final String sequence, final long phaseMs) {
        if (!canRenderEffect()) {
            Log.i(TAG, "debug capture skipped; renderer unavailable");
            return;
        }
        final int captureGeneration = ++debugCaptureGeneration;
        resetEffect();
        lastAffordanceQueuedAt = 0L;
        final float centerX = getRenderWidth() * 0.5f;
        final float centerY = getRenderHeight() * 0.5f;
        if ("hint".equals(sequence)) {
            showUnlockAffordance(new Rect(0, 0, getRenderWidth(), getRenderHeight()), 0L);
        } else {
            beginGesture(centerX, centerY);
            if ("unlock".equals(sequence) || "unlock-series".equals(sequence)) {
                updateGesture(centerX + Math.min(320f, getRenderWidth() * 0.22f),
                        centerY - Math.min(180f, getRenderHeight() * 0.07f));
                finishGesture(true);
            }
        }
        if ("unlock-series".equals(sequence)) {
            /* Start the capture clock only after nativeUnlock has executed on
             * the GL queue. Every image then belongs to one continuous Line
             * animator instead of nine independently scheduled replays. */
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            scheduleDebugAbstractTilesUnlockSeries(captureGeneration);
                        }
                    });
                }
            });
            requestRender();
            return;
        }
        scheduleDebugAbstractTilesCapture(sequence, phaseMs, captureGeneration);
    }

    private void scheduleDebugAbstractTilesUnlockSeries(final int captureGeneration) {
        final long[] phasesMs = {0L, 40L, 80L, 120L, 160L, 200L, 240L, 320L, 400L};
        for (long phaseMs : phasesMs) {
            scheduleDebugAbstractTilesCapture("unlock", phaseMs, captureGeneration);
        }
    }

    private void scheduleDebugAbstractTilesCapture(final String sequence,
            final long phaseMs, final int captureGeneration) {
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (captureGeneration != debugCaptureGeneration || !canRenderEffect()) {
                    return;
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    Log.i(TAG, "debug capture requires API 24+");
                    return;
                }
                AbstractTilesPixelCopyCapture.request(
                        AbstractTilesArm64EffectView.this, sequence, phaseMs);
                if ("touch".equals(sequence)) {
                    cancelGesture();
                }
            }
        }, Math.max(0L, Math.min(2_400L, phaseMs)));
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private static final class AbstractTilesPixelCopyCapture {
        static void request(final AbstractTilesArm64EffectView view,
                final String sequence, final long phaseMs) {
            int width = Math.max(1, view.getWidth());
            int height = Math.max(1, view.getHeight());
            final Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            PixelCopy.request(view, frame, new PixelCopy.OnPixelCopyFinishedListener() {
                @Override
                public void onPixelCopyFinished(int result) {
                    if (result == PixelCopy.SUCCESS) {
                        DebugFrameCaptureFiles.saveAsync(view.getContext(), frame,
                                "abstract_tiles_" + sequence + "_arm64_"
                                        + phaseMs + "ms.png",
                                TAG, phaseMs);
                    } else {
                        frame.recycle();
                        Log.e(TAG, "debug PixelCopy failed sequence=" + sequence
                                + " phaseMs=" + phaseMs + " result=" + result);
                    }
                }
            }, new Handler(Looper.getMainLooper()));
        }
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
        int targetWidth = getRenderWidth();
        int targetHeight = getRenderHeight();
        final boolean borrowed = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, targetWidth, targetHeight);
        final Bitmap mapped = borrowed
                ? source : createMappedBackground(source, targetWidth, targetHeight);
        if (mapped == null) {
            Log.e(TAG, "Could not normalize background source=" + sourceName);
            return;
        }
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
        if (paused) {
            tileRenderer.stageBackground(mapped, serial);
            return;
        }
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    tileRenderer.installBackground(mapped, serial, sourceName);
                }
            });
            requestRender();
        } catch (RuntimeException exception) {
            if (borrowedBackgroundPending == mapped) {
                borrowedBackgroundPending = null;
            }
            recycleOwnedBitmap(mapped);
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
        borrowedBackgroundPending = null;
        if (!canAcceptCommands()) {
            tileRenderer.clearBackgroundReference(serial);
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                tileRenderer.clearBackground(serial);
            }
        });
        requestRender();
    }

    @Override
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null
                && (borrowedBackgroundPending == bitmap
                || tileRenderer.isUsingBackground(bitmap));
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        gestureDownTimeMs = 0L;
        cancelPendingAffordance();
        stopAnimation();
        stopDragSoundImmediately();
        removeCallbacks(dragSoundFadeRunnable);
        pauseRendererBounded(true);
        soundPool.release();
        recycleAllOwnedBitmaps();
        externalBackground = false;
        borrowedBackgroundPending = null;
        NATIVE_OWNER.compareAndSet(this, null);
    }

    @Override
    public void onPause() {
        pauseRendererBounded(false);
    }

    @Override
    public void onResume() {
        if (destroyed || !ownsCurrentNativeSlot() || !paused) {
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
        return !destroyed && !paused && ownsCurrentNativeSlot()
                && AbstractTilesNative.isAvailable();
    }

    private boolean ownsCurrentNativeSlot() {
        return ownsNativeSlot && NATIVE_OWNER.get() == this;
    }

    private boolean canRenderEffect() {
        return canAcceptCommands() && externalBackground;
    }

    private void queueTouch(final int action, float screenX, float screenY,
            final long eventTimeMs) {
        final float[] local = toLocalCoordinates(screenX, screenY);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                AbstractTilesNative.nativeTouch(action, local[0], local[1], eventTimeMs);
            }
        });
        activateAnimation();
        requestRender();
    }

    private float[] toLocalCoordinates(float screenX, float screenY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return new float[] {screenX - location[0], screenY - location[1]};
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
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    tileRenderer.resetClock();
                }
            });
            removeCallbacks(animationRunnable);
            post(animationRunnable);
        }
    }

    private void stopAnimation() {
        ++animationGeneration;
        animationScheduled = false;
        removeCallbacks(animationRunnable);
    }

    private void requestStopAnimation(final int generation) {
        post(new Runnable() {
            @Override
            public void run() {
                if (generation == animationGeneration && tileRenderer.isIdle()) {
                    stopAnimation();
                }
            }
        });
    }

    private void cancelPendingAffordance() {
        ++affordanceGeneration;
        Runnable pending = affordanceRunnable;
        affordanceRunnable = null;
        if (pending != null) {
            removeCallbacks(pending);
        }
    }

    private void pauseRendererBounded(final boolean finalDestroy) {
        if (paused) {
            if (finalDestroy) {
                // The GL thread may already be gone after window removal. The bridge contract
                // makes final destruction safe in that state as well.
                if (ownsCurrentNativeSlot()) {
                    try {
                        AbstractTilesNative.nativeDestroyGpu();
                    } catch (Throwable error) {
                        Log.w(TAG, "Native final cleanup after pause failed", error);
                    }
                }
                tileRenderer.releaseBitmapReferences();
            }
            return;
        }

        if (!tileRenderer.hasGlThread()) {
            if (finalDestroy) {
                if (ownsCurrentNativeSlot()) {
                    try {
                        AbstractTilesNative.nativeDestroyGpu();
                    } catch (Throwable error) {
                        Log.w(TAG, "Native cleanup before first surface failed", error);
                    }
                }
                tileRenderer.releaseBitmapReferences();
                try {
                    /* setRenderer() starts GLThread even before the first surface callback. */
                    super.onDetachedFromWindow();
                } catch (RuntimeException error) {
                    Log.w(TAG, "Pre-attach GL thread shutdown failed", error);
                }
            }
            paused = true;
            return;
        }

        final CountDownLatch released = new CountDownLatch(1);
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        tileRenderer.releaseCurrentContext(finalDestroy);
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
        super.onPause();
        paused = true;
        if (finalDestroy) {
            tileRenderer.releaseBitmapReferences();
        }
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
        } catch (RuntimeException exception) {
            if (mapped != null && !mapped.isRecycled()) {
                mapped.recycle();
            }
            Log.e(TAG, "Background crop failed", exception);
            return null;
        }
    }

    private int getRenderWidth() {
        int width = getWidth();
        if (width > 0) {
            return width;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(1, metrics.widthPixels);
    }

    private int getRenderHeight() {
        int height = getHeight();
        if (height > 0) {
            return height;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(1, metrics.heightPixels);
    }

    private void recycleOwnedBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        boolean owned;
        synchronized (bitmapLock) {
            owned = ownedBitmaps.remove(bitmap);
        }
        if (owned && !bitmap.isRecycled()) {
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

    private void playOneShot(int soundId) {
        if (!destroyed && soundId != 0 && canPlaySound()) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
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

    private void maybeStartDragSound() {
        if (dragSoundStreamId != 0
                || gestureDownTimeMs == 0L
                || SystemClock.uptimeMillis() - gestureDownTimeMs <= DRAG_SOUND_LONG_PRESS_MS
                || !canPlaySound()) {
            return;
        }
        dragSoundVolume = 1.0f;
        dragSoundFading = false;
        removeCallbacks(dragSoundFadeRunnable);
        dragSoundStreamId = soundPool.play(
                dragSound, dragSoundVolume, dragSoundVolume, 0, -1, 1.0f);
    }

    private void fadeOutDragSound(float fadeStep) {
        if (dragSoundStreamId == 0) {
            return;
        }
        dragSoundFadeStep = fadeStep;
        dragSoundFading = true;
        removeCallbacks(dragSoundFadeRunnable);
        post(dragSoundFadeRunnable);
    }

    private void stepDragSoundFade() {
        if (!dragSoundFading || dragSoundStreamId == 0 || destroyed) {
            return;
        }
        dragSoundVolume = Math.max(0.0f, dragSoundVolume - dragSoundFadeStep);
        soundPool.setVolume(dragSoundStreamId, dragSoundVolume, dragSoundVolume);
        if (dragSoundVolume > 0.0f) {
            postDelayed(dragSoundFadeRunnable, DRAG_SOUND_FADE_STEP_MS);
        } else {
            stopDragSoundImmediately();
        }
    }

    private void stopDragSoundImmediately() {
        removeCallbacks(dragSoundFadeRunnable);
        dragSoundFading = false;
        if (dragSoundStreamId != 0) {
            soundPool.stop(dragSoundStreamId);
            dragSoundStreamId = 0;
        }
    }

    private void logNativeError(String prefix) {
        String detail = "";
        try {
            detail = AbstractTilesNative.nativeGetLastError();
        } catch (Throwable ignored) {
            // Keep the original failure visible when the bridge itself is unavailable.
        }
        Log.e(TAG, prefix + (detail == null || detail.length() == 0 ? "" : ": " + detail));
    }

    private final class TileRenderer implements GLSurfaceView.Renderer {
        private final ElapsedClock simulationClock = new ElapsedClock();
        private Thread glThread;
        private volatile Bitmap activeBackground;
        private Bitmap lineMask;
        private boolean bridgeAvailable;
        private boolean surfaceReady;
        private boolean gpuReady;
        private boolean backgroundReady;
        private boolean lineMaskReady;
        private volatile boolean initializationFailed;
        private volatile boolean idle = true;
        private int contextGeneration;
        private int initializedGeneration;
        private int surfaceWidth;
        private int surfaceHeight;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            glThread = Thread.currentThread();
            ++contextGeneration;
            initializedGeneration = 0;
            surfaceReady = true;
            gpuReady = false;
            backgroundReady = false;
            lineMaskReady = false;
            initializationFailed = false;
            simulationClock.reset();
            clearTransparent();

            bridgeAvailable = ownsCurrentNativeSlot() && AbstractTilesNative.isAvailable();
            if (!bridgeAvailable) {
                initializationFailed = true;
                Log.w(TAG, "Abstract Tiles ARM64 bridge is not packaged in this build");
                return;
            }
            try {
                AbstractTilesNative.nativeAbandonGpu();
                if (lineEnabled) {
                    ensureLineMask();
                }
            } catch (Throwable error) {
                bridgeAvailable = false;
                initializationFailed = true;
                Log.e(TAG, "Context setup failed", error);
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            surfaceWidth = Math.max(1, width);
            surfaceHeight = Math.max(1, height);
            if (!surfaceReady || !bridgeAvailable || width <= 0 || height <= 0) {
                return;
            }
            try {
                if (!AbstractTilesNative.nativeInitGpu(
                        surfaceWidth, surfaceHeight, lineEnabled)) {
                    initializationFailed = true;
                    logNativeError("GLES init failed");
                    return;
                }
                gpuReady = true;
                initializationFailed = false;
                initializedGeneration = contextGeneration;
                remapBackgroundForSurface(surfaceWidth, surfaceHeight);
                lineMaskReady = !lineEnabled || (lineMask != null
                        && AbstractTilesNative.nativeUploadBitmap(
                        AbstractTilesNative.TEXTURE_LINE_MASK, lineMask));
                backgroundReady = activeBackground != null
                        && AbstractTilesNative.nativeUploadBitmap(
                        AbstractTilesNative.TEXTURE_BACKGROUND, activeBackground);
                if (!lineMaskReady || activeBackground != null && !backgroundReady) {
                    initializationFailed = true;
                    logNativeError(!lineMaskReady
                            ? "Line mask upload failed" : "Background upload failed");
                    return;
                }
                externalBackground = backgroundReady;
                idle = true;
                simulationClock.reset();
                clearTransparent();
            } catch (Throwable error) {
                gpuReady = false;
                initializationFailed = true;
                Log.e(TAG, "Surface initialization failed", error);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            clearTransparent();
            if (!surfaceReady || !gpuReady || !backgroundReady
                    || (lineEnabled && !lineMaskReady)
                    || destroyed) {
                idle = true;
                return;
            }
            try {
                if (AbstractTilesNative.nativeIsIdle()) {
                    idle = true;
                    requestStopAnimation(animationGeneration);
                    return;
                }
                idle = false;
                float elapsedSeconds = simulationClock.advance(System.nanoTime());
                if (elapsedSeconds > 0.0f
                        && !AbstractTilesNative.nativeStep(elapsedSeconds)) {
                    initializationFailed = true;
                    logNativeError("Simulation step failed");
                    return;
                }
                if (AbstractTilesNative.nativeIsIdle()) {
                    idle = true;
                    requestStopAnimation(animationGeneration);
                    return;
                }
                if (!AbstractTilesNative.nativeDraw(surfaceWidth, surfaceHeight)) {
                    initializationFailed = true;
                    logNativeError("Transparent draw failed");
                    return;
                }
            } catch (Throwable error) {
                initializationFailed = true;
                Log.e(TAG, "Draw failed", error);
            }
        }

        boolean hasInitializationFailed() {
            return initializationFailed;
        }

        boolean hasGlThread() {
            return glThread != null;
        }

        boolean isIdle() {
            return idle;
        }

        boolean isUsingBackground(Bitmap bitmap) {
            return activeBackground == bitmap;
        }

        void resetClock() {
            simulationClock.reset();
            idle = true;
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
            backgroundReady = gpuReady && AbstractTilesNative.nativeUploadBitmap(
                    AbstractTilesNative.TEXTURE_BACKGROUND, bitmap);
            externalBackground = backgroundReady;
            if (!backgroundReady && gpuReady) {
                initializationFailed = true;
                logNativeError("Background upload failed source=" + sourceName);
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
            AbstractTilesNative.nativeClearBitmap(AbstractTilesNative.TEXTURE_BACKGROUND);
            clearBackgroundReference(serial);
            backgroundReady = false;
            externalBackground = false;
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

        void releaseCurrentContext(boolean finalDestroy) {
            stopAnimation();
            clearTransparent();
            try {
                if (bridgeAvailable && ownsCurrentNativeSlot()) {
                    if (finalDestroy) {
                        AbstractTilesNative.nativeDestroyGpu();
                    } else {
                        AbstractTilesNative.nativeAbandonGpu();
                    }
                }
            } catch (Throwable error) {
                Log.w(TAG, "Native context release failed", error);
            }
            surfaceReady = false;
            gpuReady = false;
            backgroundReady = false;
            lineMaskReady = false;
            externalBackground = false;
            initializedGeneration = 0;
            simulationClock.reset();
            if (finalDestroy) {
                releaseBitmapReferences();
            }
        }

        void releaseBitmapReferences() {
            Bitmap previousBackground = activeBackground;
            Bitmap previousLineMask = lineMask;
            activeBackground = null;
            lineMask = null;
            recycleOwnedBitmap(previousBackground);
            recycleOwnedBitmap(previousLineMask);
        }

        private void ensureLineMask() {
            if (lineMask != null && !lineMask.isRecycled()) {
                return;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded = BitmapFactory.decodeResource(
                    getResources(), R.drawable.special_abstracttile_linemask, options);
            if (decoded == null) {
                throw new IllegalStateException("Abstract Tiles line mask decode failed");
            }
            Bitmap normalized = decoded.getConfig() == Bitmap.Config.ARGB_8888
                    ? decoded : decoded.copy(Bitmap.Config.ARGB_8888, false);
            if (normalized == null) {
                decoded.recycle();
                throw new IllegalStateException("Abstract Tiles line mask RGBA copy failed");
            }
            if (normalized != decoded) {
                decoded.recycle();
            }
            lineMask = normalized;
            synchronized (bitmapLock) {
                ownedBitmaps.add(normalized);
            }
        }

        private void remapBackgroundForSurface(int width, int height) {
            if (activeBackground == null || activeBackground.isRecycled()
                    || activeBackground.getWidth() == width
                    && activeBackground.getHeight() == height) {
                return;
            }
            Bitmap remapped = createMappedBackground(activeBackground, width, height);
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
            GLES20.glViewport(0, 0, Math.max(1, surfaceWidth), Math.max(1, surfaceHeight));
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }

        private long currentBackgroundSerial() {
            synchronized (bitmapLock) {
                return backgroundSerial;
            }
        }

    }

    private static final class ElapsedClock {
        private long previousFrameNs = Long.MIN_VALUE;

        float advance(long frameTimeNs) {
            if (previousFrameNs == Long.MIN_VALUE) {
                previousFrameNs = frameTimeNs;
                return 0.0f;
            }
            long elapsedNs = frameTimeNs - previousFrameNs;
            previousFrameNs = frameTimeNs;
            if (elapsedNs <= 0L) {
                return 0.0f;
            }
            // The OEM animator is monotonic-time based. Preserve all elapsed time so a missed
            // render request advances the timeline instead of slowing the effect down.
            return elapsedNs / 1_000_000_000.0f;
        }

        void reset() {
            previousFrameNs = Long.MIN_VALUE;
        }
    }

    /** Mirrors WindowManager parking state onto the child SurfaceControl-backed view. */
    private final class WindowHost extends FrameLayout {
        WindowHost(Context context) {
            super(context);
        }

        @Override
        public void setAlpha(float alpha) {
            super.setAlpha(alpha);
            AbstractTilesArm64EffectView.this.setAlpha(alpha);
        }

        @Override
        public void setVisibility(int visibility) {
            super.setVisibility(visibility);
            AbstractTilesArm64EffectView.this.setVisibility(visibility);
        }
    }
}
