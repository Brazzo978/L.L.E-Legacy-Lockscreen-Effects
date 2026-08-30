package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
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
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * App-owned ARM64 reconstruction of the Note 4 Geometric Mosaic LockBG scene.
 *
 * The original scene uses 100 reusable circular mask records, a 12 x 21 block
 * mesh, two concentric-circle colour passes and a final wallpaper blend.  This
 * host keeps the same observable model while implementing the render path in
 * GLES2 that is available on modern 64-bit-only devices.
 */
public final class GeometricMosaicArm64EffectView extends GLSurfaceView
        implements UnlockEffectRenderer, BackgroundSourceRenderer, DebugFrameCaptureRenderer,
        UnlockEffectReadiness {
    private static final String TAG = "LLE64GeometricMosaic";
    private static final long FRAME_INTERVAL_MS = 33L;
    private static final long GL_CLEANUP_TIMEOUT_MS = 350L;
    private static final long DRAG_SOUND_LONG_PRESS_MS = 411L;
    private static final long DRAG_SOUND_FADE_STEP_MS = 10L;
    private static final float DRAG_SOUND_RELEASE_FADE_STEP = 0.039f;
    private static final float DRAG_SOUND_UNLOCK_FADE_STEP = 0.059f;
    private static final long AFFORDANCE_DEDUP_WINDOW_MS = 2_500L;

    private static final AtomicReference<GeometricMosaicArm64EffectView> OWNER =
            new AtomicReference<GeometricMosaicArm64EffectView>();

    private final MosaicRenderer mosaicRenderer = new MosaicRenderer();
    private final FrameLayout windowHost;
    private final Object bitmapLock = new Object();
    private final Object readinessLock = new Object();
    private final Set<Bitmap> ownedBitmaps = Collections.newSetFromMap(
            new IdentityHashMap<Bitmap, Boolean>());
    private final boolean ownsRenderer;
    /* Presentation cadence for the display-refresh opt-in. */
    private final boolean highRefreshPresentation;
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
    private boolean animationScheduled;
    private int animationGeneration;
    private long lastAffordanceQueuedAt;
    private int affordanceGeneration;
    private Runnable affordanceRunnable;
    private int dragSoundStreamId;
    private float dragSoundVolume = 1.0f;
    private float dragSoundFadeStep = DRAG_SOUND_RELEASE_FADE_STEP;
    private boolean dragSoundFading;
    private int readinessState = UnlockEffectReadiness.STATE_CONSTRUCTED;
    private String readinessDetail = "constructed";
    private UnlockEffectReadiness.ReadinessListener readinessListener;

    private final Runnable dragSoundFadeRunnable = new Runnable() {
        @Override
        public void run() {
            stepDragSoundFade();
        }
    };

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!canAcceptCommands() || !animationScheduled) {
                return;
            }
            requestRender();
            if (highRefreshPresentation) {
                postOnAnimation(this);
            } else {
                // Preserve the measured stock request cadence exactly.
                postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

    public GeometricMosaicArm64EffectView(Context context) {
        this(context, false);
    }

    public GeometricMosaicArm64EffectView(Context context, boolean highRefreshPresentation) {
        super(context);
        this.highRefreshPresentation = highRefreshPresentation;
        ownsRenderer = OWNER.compareAndSet(null, this);

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(false);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setRenderer(mosaicRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

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
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        tapSound = soundPool.load(context, R.raw.brilliantcut_tap, 1);
        dragSound = soundPool.load(context, R.raw.brilliantcut_drag, 1);
        unlockSound = soundPool.load(context, R.raw.brilliantcut_unlock, 1);

        if (!ownsRenderer) {
            Log.e(TAG, "Geometric Mosaic singleton already owned; this view stays inert");
            failReadiness("renderer singleton already owned");
        }
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
    public void setReadinessListener(UnlockEffectReadiness.ReadinessListener listener) {
        synchronized (readinessLock) {
            readinessListener = listener;
        }
        notifyReadinessListener(listener);
    }

    boolean isReady() {
        return ownsCurrentRenderer() && !destroyed && !mosaicRenderer.initializationFailed;
    }

    @Override
    public View asView() {
        return windowHost;
    }

    @Override
    public String effectName() {
        return "N4 Geometric Mosaic ARM64";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        cancelPendingAffordance();
        if (!canRenderEffect()) {
            return;
        }
        gestureDownTimeMs = SystemClock.uptimeMillis();
        lastScreenX = screenX;
        lastScreenY = screenY;
        stopDragSound();
        dragSoundVolume = 1.0f;
        dragSoundFadeStep = DRAG_SOUND_RELEASE_FADE_STEP;
        playOneShot(tapSound);
        queueTouch(MosaicRenderer.ACTION_DOWN, screenX, screenY);
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
        queueTouch(MosaicRenderer.ACTION_MOVE, screenX, screenY);
    }

    /** Reanchors MOVE after the shared multi-touch gate releases the gesture. */
    public void realignGesture(float screenX, float screenY) {
        if (!canRenderEffect() || gestureDownTimeMs == 0L) {
            return;
        }
        final float[] local = toLocalCoordinates(screenX, screenY);
        lastScreenX = screenX;
        lastScreenY = screenY;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mosaicRenderer.realign(local[0], local[1]);
            }
        });
    }

    @Override
    public void finishGesture(final boolean completed) {
        if (gestureDownTimeMs == 0L) {
            return;
        }
        if (canRenderEffect()) {
            queueTouch(MosaicRenderer.ACTION_UP, lastScreenX, lastScreenY);
            if (completed) {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        mosaicRenderer.unlock();
                    }
                });
                playOneShot(unlockSound);
                activateAnimation();
            }
        }
        gestureDownTimeMs = 0L;
        fadeOutDragSound(completed
                ? DRAG_SOUND_UNLOCK_FADE_STEP
                : DRAG_SOUND_RELEASE_FADE_STEP);
    }

    @Override
    public void cancelGesture() {
        if (gestureDownTimeMs != 0L && canRenderEffect()) {
            queueTouch(MosaicRenderer.ACTION_CANCEL, lastScreenX, lastScreenY);
        }
        gestureDownTimeMs = 0L;
        fadeOutDragSound(DRAG_SOUND_RELEASE_FADE_STEP);
    }

    @Override
    public void resetEffect() {
        cancelPendingAffordance();
        lastAffordanceQueuedAt = 0L;
        gestureDownTimeMs = 0L;
        stopDragSound();
        if (!canAcceptCommands()) {
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mosaicRenderer.resetScene();
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
                final float centerX = target.exactCenterX() - location[0];
                final float centerY = target.exactCenterY() - location[1];
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        mosaicRenderer.affordance(centerX, centerY);
                    }
                });
                activateAnimation();
                requestRender();
            }
        };
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
    }

    /** Captures the transparent GLES surface only when the ADB debug API requests it. */
    @Override
    public void captureDebugAffordanceFrame(final long phaseMs) {
        if (!canRenderEffect()) {
            Log.i(TAG, "debug hint capture skipped; renderer unavailable");
            return;
        }
        lastAffordanceQueuedAt = 0L;
        showUnlockAffordance(new Rect(0, 0, getRenderWidth(), getRenderHeight()), 0L);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!canRenderEffect()) {
                    return;
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    Log.i(TAG, "debug hint capture requires API 24+");
                    return;
                }
                PixelCopyCapture.request(GeometricMosaicArm64EffectView.this, phaseMs);
            }
        }, Math.max(0L, Math.min(2_400L, phaseMs)));
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private static final class PixelCopyCapture {
        static void request(final GeometricMosaicArm64EffectView view, final long phaseMs) {
            int width = Math.max(1, view.getWidth());
            int height = Math.max(1, view.getHeight());
            final Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            PixelCopy.request(view, frame, new PixelCopy.OnPixelCopyFinishedListener() {
                @Override
                public void onPixelCopyFinished(int result) {
                    if (result == PixelCopy.SUCCESS) {
                        DebugFrameCaptureFiles.saveAsync(view.getContext(), frame,
                                "geometric_hint_arm64_" + phaseMs + "ms.png",
                                TAG, phaseMs);
                    } else {
                        frame.recycle();
                        Log.e(TAG, "debug hint PixelCopy failed phaseMs=" + phaseMs
                                + " result=" + result);
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
        int width = getRenderWidth();
        int height = getRenderHeight();
        final boolean borrowed = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, width, height);
        final Bitmap mapped = borrowed ? source : createMappedBackground(source, width, height);
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
        invalidateResourceReadiness("background pending");
        if (paused) {
            mosaicRenderer.stageBackground(mapped, serial);
            return;
        }
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    mosaicRenderer.installBackground(mapped, serial, sourceName);
                }
            });
            requestRender();
        } catch (RuntimeException error) {
            borrowedBackgroundPending = null;
            recycleOwnedBitmap(mapped);
            Log.e(TAG, "Could not queue background upload", error);
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
            mosaicRenderer.clearBackgroundReference(serial);
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mosaicRenderer.clearBackground(serial);
            }
        });
        requestRender();
    }

    @Override
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && (borrowedBackgroundPending == bitmap
                || mosaicRenderer.activeBackground == bitmap);
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
        stopDragSound();
        releaseGlBounded(true);
        soundPool.release();
        recycleAllOwnedBitmaps();
        borrowedBackgroundPending = null;
        externalBackground = false;
        OWNER.compareAndSet(this, null);
    }

    @Override
    public void onPause() {
        releaseGlBounded(false);
    }

    @Override
    public void onResume() {
        if (destroyed || !ownsCurrentRenderer() || !paused) {
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

    private boolean ownsCurrentRenderer() {
        return ownsRenderer && OWNER.get() == this;
    }

    private boolean canAcceptCommands() {
        return !destroyed && !paused && ownsCurrentRenderer();
    }

    private void advanceReadiness(int state, String detail) {
        UnlockEffectReadiness.ReadinessListener listener;
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
        UnlockEffectReadiness.ReadinessListener listener;
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
        UnlockEffectReadiness.ReadinessListener listener = null;
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

    private void notifyReadinessListener(
            UnlockEffectReadiness.ReadinessListener listener) {
        if (listener == null) {
            return;
        }
        try {
            listener.onReadinessChanged();
        } catch (RuntimeException error) {
            Log.w(TAG, "Readiness listener failed", error);
        }
    }

    private boolean canRenderEffect() {
        return canAcceptCommands() && externalBackground;
    }

    private void queueTouch(final int action, float screenX, float screenY) {
        final float[] local = toLocalCoordinates(screenX, screenY);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mosaicRenderer.touch(action, local[0], local[1]);
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
        if (!animationScheduled) {
            animationScheduled = true;
            removeCallbacks(animationRunnable);
            if (highRefreshPresentation) {
                postOnAnimation(animationRunnable);
            } else {
                post(animationRunnable);
            }
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
                if (generation == animationGeneration && mosaicRenderer.idle) {
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

    private void releaseGlBounded(final boolean finalDestroy) {
        if (paused) {
            if (finalDestroy) {
                mosaicRenderer.releaseBitmapReferences();
            }
            return;
        }
        if (!mosaicRenderer.hasGlThread()) {
            if (finalDestroy) {
                mosaicRenderer.releaseBitmapReferences();
            }
            paused = true;
            markReadinessDetached(finalDestroy ? "destroyed" : "context released");
            return;
        }
        final CountDownLatch released = new CountDownLatch(1);
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        mosaicRenderer.releaseCurrentContext(finalDestroy);
                    } finally {
                        released.countDown();
                    }
                }
            });
            requestRender();
            if (!released.await(GL_CLEANUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for bounded GLES cleanup");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            Log.w(TAG, "GLES cleanup could not be queued", error);
        }
        super.onPause();
        paused = true;
        markReadinessDetached(finalDestroy ? "destroyed" : "context released");
        if (finalDestroy) {
            mosaicRenderer.releaseBitmapReferences();
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
            new Canvas(mapped).drawBitmap(source, sourceRect,
                    new Rect(0, 0, width, height), paint);
            return mapped;
        } catch (RuntimeException error) {
            if (mapped != null && !mapped.isRecycled()) {
                mapped.recycle();
            }
            Log.e(TAG, "Background crop failed", error);
            return null;
        }
    }

    private int getRenderWidth() {
        if (getWidth() > 0) {
            return getWidth();
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(1, metrics.widthPixels);
    }

    private int getRenderHeight() {
        if (getHeight() > 0) {
            return getHeight();
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
        if (!EffectAudio.platformSoundSwitchAllows(getContext())) {
            return false;
        }
        return audioManager == null
                || EffectAudio.outputHasVolume(getContext(), audioManager);
    }

    private void maybeStartDragSound() {
        if (dragSoundStreamId != 0 || gestureDownTimeMs == 0L
                || SystemClock.uptimeMillis() - gestureDownTimeMs <= DRAG_SOUND_LONG_PRESS_MS
                || !canPlaySound()) {
            return;
        }
        dragSoundVolume = 1.0f;
        dragSoundFading = false;
        removeCallbacks(dragSoundFadeRunnable);
        dragSoundStreamId = soundPool.play(
                dragSound,
                dragSoundVolume,
                dragSoundVolume,
                0,
                -1,
                1.0f);
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
            return;
        }
        stopDragSound();
    }

    private void stopDragSound() {
        removeCallbacks(dragSoundFadeRunnable);
        dragSoundFading = false;
        if (dragSoundStreamId != 0) {
            soundPool.stop(dragSoundStreamId);
            dragSoundStreamId = 0;
        }
    }

    private final class MosaicRenderer implements GLSurfaceView.Renderer {
        static final int ACTION_DOWN = 0;
        static final int ACTION_UP = 1;
        static final int ACTION_MOVE = 2;
        static final int ACTION_CANCEL = 3;
        private static final int MAX_SCENE_PULSES = 100;
        private static final int MASK_COLUMNS = 12;
        private static final int MASK_ROWS = 21;
        /* ARM32 compares clip-space samples against 0.017. */
        private static final float TOUCH_SAMPLE_DISTANCE = 0.0085f;
        private static final float TOUCH_GROW_SECONDS = 0.15f;
        private static final float TOUCH_SETTLE_SECONDS = 0.60f;
        private static final float TOUCH_LIFETIME =
                TOUCH_GROW_SECONDS + TOUCH_SETTLE_SECONDS;
        private static final float UNLOCK_EXPAND_SECONDS = 0.45f;
        private static final float UNLOCK_FADE_SECONDS = 0.60f;

        private final GeometricMosaicGlesPipeline exactPipeline =
                new GeometricMosaicGlesPipeline();
        private final ArrayList<Pulse> pulses = new ArrayList<Pulse>(MAX_SCENE_PULSES);
        private final FloatBuffer vertices;
        private final ByteBuffer maskPixels = ByteBuffer.allocateDirect(
                MASK_COLUMNS * MASK_ROWS);
        private Thread glThread;
        private volatile Bitmap activeBackground;
        private volatile boolean initializationFailed;
        private volatile boolean idle = true;
        private boolean surfaceReady;
        private boolean backgroundReady;
        private boolean held;
        private int contextGeneration;
        private int initializedGeneration;
        private int initializedWidth;
        private int initializedHeight;
        private int width = 1;
        private int height = 1;
        private int program;
        private int backgroundTexture;
        private int maskTexture;
        private int positionLocation;
        private int texCoordLocation;
        private int backgroundLocation;
        private int maskLocation;
        private int ringAgeLocation;
        private int circleAspectLocation;
        private int seedLocation;
        private float sceneTime;
        private float ringStart = -100.0f;
        private float unlockStart = -100.0f;
        private float randomSeed = 1.0f;
        private float lastX = 0.5f;
        private float lastY = 0.5f;
        private long previousFrameNs = Long.MIN_VALUE;
        private final VisualTimeline visualTimeline = new VisualTimeline();
        private final UnlockFramePacing unlockFramePacing = new UnlockFramePacing();

        MosaicRenderer() {
            float[] quad = {
                    -1.0f, -1.0f, 0.0f, 1.0f,
                     1.0f, -1.0f, 1.0f, 1.0f,
                    -1.0f,  1.0f, 0.0f, 0.0f,
                     1.0f,  1.0f, 1.0f, 0.0f
            };
            vertices = ByteBuffer.allocateDirect(quad.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(quad).position(0);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            glThread = Thread.currentThread();
            ++contextGeneration;
            initializedGeneration = 0;
            initializedWidth = 0;
            initializedHeight = 0;
            surfaceReady = true;
            initializationFailed = false;
            backgroundReady = false;
            previousFrameNs = Long.MIN_VALUE;
            visualTimeline.reset();
            unlockFramePacing.reset();
            exactPipeline.abandon();
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
                    || initializedWidth != width
                    || initializedHeight != height
                    || !exactPipeline.isInitialized();
            if (needsInitialization) {
                setReadinessState(UnlockEffectReadiness.STATE_SURFACE_READY,
                        "surface resize " + width + "x" + height);
                try {
                    exactPipeline.initialize(width, height);
                    initializedGeneration = contextGeneration;
                    initializedWidth = width;
                    initializedHeight = height;
                    backgroundReady = false;
                } catch (RuntimeException error) {
                    initializationFailed = true;
                    failReadiness("multipass initialization failed: "
                            + error.getClass().getSimpleName());
                    Log.e(TAG, "Geometric Mosaic multipass initialization failed", error);
                    return;
                }
            }
            remapBackgroundForSurface(width, height);
            if (!initializationFailed && activeBackground != null && !backgroundReady) {
                backgroundReady = uploadBackground(activeBackground);
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
            long now = System.nanoTime();
            previousFrameNs = now;
            try {
                boolean needsMoreFrames = exactPipeline.render(visualTimeline.sample(now));
                unlockFramePacing.recordFrame(now, System.nanoTime());
                advanceReadiness(UnlockEffectReadiness.STATE_FIRST_FRAME_READY,
                        "first transparent frame");
                idle = !needsMoreFrames;
                if (idle) {
                    String unlockMetrics = unlockFramePacing.finishIfActive();
                    if (unlockMetrics != null) {
                        Log.i(TAG, unlockMetrics);
                    }
                    requestStopAnimation(animationGeneration);
                }
            } catch (RuntimeException error) {
                initializationFailed = true;
                idle = true;
                clearTransparent();
                failReadiness("multipass draw failed: "
                        + error.getClass().getSimpleName());
                Log.e(TAG, "Geometric Mosaic multipass render failed", error);
            }
        }

        void touch(int action, float pixelX, float pixelY) {
            float x = clamp(pixelX / Math.max(1.0f, width), 0.0f, 1.0f);
            float y = clamp(pixelY / Math.max(1.0f, height), 0.0f, 1.0f);
            long now = visualTimeline.sample(System.nanoTime());
            if (action == ACTION_DOWN) {
                held = true;
                lastX = x;
                lastY = y;
                exactPipeline.endTouch();
                exactPipeline.addTouch(x, y, now);
            } else if (action == ACTION_MOVE && held) {
                float dx = x - lastX;
                float dy = y - lastY;
                // Keep the terminal coordinate even when this MOVE is correctly filtered from
                // the native trail by its 0.0085 normalized minimum distance.
                lastX = x;
                lastY = y;
                if (dx * dx + dy * dy >= TOUCH_SAMPLE_DISTANCE * TOUCH_SAMPLE_DISTANCE) {
                    exactPipeline.addTouch(x, y, now);
                }
            } else if (action == ACTION_UP || action == ACTION_CANCEL) {
                held = false;
                lastX = x;
                lastY = y;
                exactPipeline.endTouch();
            }
            idle = false;
        }

        void realign(float pixelX, float pixelY) {
            lastX = clamp(pixelX / Math.max(1.0f, width), 0.0f, 1.0f);
            lastY = clamp(pixelY / Math.max(1.0f, height), 0.0f, 1.0f);
            exactPipeline.realignTouch(lastX, lastY);
        }

        void unlock() {
            held = false;
            exactPipeline.endTouch();
            unlockFramePacing.begin();
            if (!exactPipeline.unlockAt(lastX, lastY, visualTimeline.sample(System.nanoTime()))) {
                Log.w(TAG, "Geometric Mosaic terminal unlock could not be armed");
            }
            idle = false;
        }

        void affordance(float pixelX, float pixelY) {
            float x = clamp(pixelX / Math.max(1.0f, width), 0.0f, 1.0f);
            float y = clamp(pixelY / Math.max(1.0f, height), 0.0f, 1.0f);
            exactPipeline.addAffordance(x, y, visualTimeline.sample(System.nanoTime()));
            idle = false;
        }

        void resetScene() {
            pulses.clear();
            held = false;
            sceneTime = 0.0f;
            ringStart = -100.0f;
            unlockStart = -100.0f;
            randomSeed = 1.0f + (System.nanoTime() & 0xffffL) / 65535.0f * 97.0f;
            previousFrameNs = Long.MIN_VALUE;
            visualTimeline.reset();
            unlockFramePacing.reset();
            exactPipeline.reset();
            idle = true;
            clearTransparent();
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
            backgroundReady = exactPipeline.isInitialized() && uploadBackground(bitmap);
            externalBackground = backgroundReady;
            if (backgroundReady) {
                publishResourcesReadyIfComplete();
            }
            if (!backgroundReady && exactPipeline.isInitialized()) {
                initializationFailed = true;
                failReadiness("background upload failed source=" + sourceName);
                Log.e(TAG, "Background upload failed source=" + sourceName);
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
            exactPipeline.reset();
            if (backgroundTexture != 0) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture);
                byte[] transparent = {0, 0, 0, 0};
                ByteBuffer pixel = ByteBuffer.allocateDirect(4);
                pixel.put(transparent).position(0);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel);
            }
            resetScene();
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

        void releaseCurrentContext(boolean finalDestroy) {
            stopAnimation();
            clearTransparent();
            deleteGpuResources();
            surfaceReady = false;
            backgroundReady = false;
            externalBackground = false;
            initializedGeneration = 0;
            initializedWidth = 0;
            initializedHeight = 0;
            previousFrameNs = Long.MIN_VALUE;
            visualTimeline.reset();
            unlockFramePacing.reset();
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
            if (surfaceReady && exactPipeline.isInitialized() && backgroundReady) {
                advanceReadiness(UnlockEffectReadiness.STATE_RESOURCES_READY,
                        "GPU and background ready " + width + "x" + height);
            }
        }

        private void addPulse(float x, float y, float kind, float start) {
            addPulse(x, y, kind, start, 0.3f);
        }

        private void addPulse(float x, float y, float kind, float start, float startRadius) {
            if (pulses.size() >= MAX_SCENE_PULSES) {
                pulses.remove(0);
            }
            pulses.add(new Pulse(clamp(x, 0.0f, 1.0f), clamp(y, 0.0f, 1.0f),
                    start, kind, startRadius));
        }

        private void pruneExpiredPulses() {
            for (int i = pulses.size() - 1; i >= 0; --i) {
                Pulse pulse = pulses.get(i);
                float age = sceneTime - pulse.start;
                float lifetime = pulse.kind == Pulse.KIND_UNLOCK
                        ? UNLOCK_FADE_SECONDS : TOUCH_LIFETIME;
                if (age > lifetime) {
                    pulses.remove(i);
                }
            }
        }

        private float touchRadius(float age) {
            if (age <= TOUCH_GROW_SECONDS) {
                return mix(0.3f, 0.8f, clamp(age / TOUCH_GROW_SECONDS, 0.0f, 1.0f));
            }
            return mix(0.8f, 0.3f, clamp(
                    (age - TOUCH_GROW_SECONDS) / TOUCH_SETTLE_SECONDS, 0.0f, 1.0f));
        }

        /** Builds the original low-resolution 12 x 21 mask instead of a
         * full-screen analytic circle.  Nearest sampling is intentional: the
         * coarse mask is what gives Geometric Mosaic its stepped footprint. */
        private void updateMaskTexture() {
            if (maskTexture == 0) {
                return;
            }
            float physicalAspect = width / (float) Math.max(1, height);
            float sceneAlpha = 1.0f;
            float unlockAge = sceneTime - unlockStart;
            if (unlockAge >= 0.0f && unlockAge <= UNLOCK_FADE_SECONDS) {
                sceneAlpha = 1.0f - unlockAge / UNLOCK_FADE_SECONDS;
            }
            maskPixels.position(0);
            for (int row = 0; row < MASK_ROWS; ++row) {
                float y = (row + 0.5f) / MASK_ROWS;
                for (int column = 0; column < MASK_COLUMNS; ++column) {
                    float x = (column + 0.5f) / MASK_COLUMNS;
                    float coverage = 0.0f;
                    for (int i = 0; i < pulses.size(); ++i) {
                        Pulse pulse = pulses.get(i);
                        float age = sceneTime - pulse.start;
                        if (age < 0.0f) {
                            continue;
                        }
                        float radius;
                        if (pulse.kind == Pulse.KIND_UNLOCK) {
                            if (age > UNLOCK_FADE_SECONDS) {
                                continue;
                            }
                            radius = mix(pulse.startRadius, 5.0f, clamp(
                                    age / UNLOCK_EXPAND_SECONDS, 0.0f, 1.0f));
                        } else {
                            if (age > TOUCH_LIFETIME) {
                                continue;
                            }
                            radius = touchRadius(age);
                        }
                        float dx = (x - pulse.x) * 2.0f * physicalAspect;
                        float dy = (y - pulse.y) * 2.0f;
                        float distance = (float) Math.sqrt(dx * dx + dy * dy);
                        coverage = Math.max(coverage,
                                1.0f - distance / Math.max(0.0001f, radius));
                    }
                    coverage = clamp(coverage * sceneAlpha, 0.0f, 1.0f);
                    maskPixels.put((byte) Math.round(coverage * 255.0f));
                }
            }
            maskPixels.position(0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture);
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0,
                    MASK_COLUMNS, MASK_ROWS, GLES20.GL_LUMINANCE,
                    GLES20.GL_UNSIGNED_BYTE, maskPixels);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }

        private void createGpuResources() {
            deleteGpuResources();
            int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            String linkLog = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            if (linked[0] == 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
                throw new IllegalStateException("program link failed: " + linkLog);
            }
            positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
            texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord");
            backgroundLocation = GLES20.glGetUniformLocation(program, "uBackground");
            maskLocation = GLES20.glGetUniformLocation(program, "uMask");
            ringAgeLocation = GLES20.glGetUniformLocation(program, "uRingAge");
            circleAspectLocation = GLES20.glGetUniformLocation(program, "uCircleAspect");
            seedLocation = GLES20.glGetUniformLocation(program, "uSeed");

            int[] textures = new int[2];
            GLES20.glGenTextures(2, textures, 0);
            backgroundTexture = textures[0];
            maskTexture = textures[1];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            maskPixels.position(0);
            for (int i = 0; i < MASK_COLUMNS * MASK_ROWS; ++i) {
                maskPixels.put((byte) 0);
            }
            maskPixels.position(0);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                    MASK_COLUMNS, MASK_ROWS, 0, GLES20.GL_LUMINANCE,
                    GLES20.GL_UNSIGNED_BYTE, maskPixels);
        }

        private void deleteGpuResources() {
            exactPipeline.release();
            if (backgroundTexture != 0) {
                int[] texture = {backgroundTexture};
                GLES20.glDeleteTextures(1, texture, 0);
                backgroundTexture = 0;
            }
            if (maskTexture != 0) {
                int[] texture = {maskTexture};
                GLES20.glDeleteTextures(1, texture, 0);
                maskTexture = 0;
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }

        private int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("shader compile failed: " + log);
            }
            return shader;
        }

        private boolean uploadBackground(Bitmap bitmap) {
            if (!exactPipeline.isInitialized() || bitmap == null || bitmap.isRecycled()) {
                return false;
            }
            while (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
                // Clear stale driver state before the synchronous upload.
            }
            boolean uploaded = exactPipeline.uploadBackground(bitmap);
            int error = GLES20.glGetError();
            if (!uploaded || error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "GL texture upload error=0x" + Integer.toHexString(error));
                return false;
            }
            return true;
        }

        private void drawOverlay() {
            GLES20.glViewport(0, 0, width, height);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture);
            GLES20.glUniform1i(backgroundLocation, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture);
            GLES20.glUniform1i(maskLocation, 1);
            GLES20.glUniform1f(ringAgeLocation, Math.max(0.0f, sceneTime - ringStart));
            float shortSide = Math.min(width, height);
            float longSide = Math.max(width, height);
            float circleWidth = Math.max(1.0f,
                    ((int) shortSide / 12) * 18.0f / 4.0f);
            float circleHeight = Math.max(1.0f,
                    ((int) longSide / 21) * 30.0f / 4.0f);
            GLES20.glUniform1f(circleAspectLocation, circleHeight / circleWidth);
            GLES20.glUniform1f(seedLocation, randomSeed);

            vertices.position(0);
            GLES20.glEnableVertexAttribArray(positionLocation);
            GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT,
                    false, 16, vertices);
            vertices.position(2);
            GLES20.glEnableVertexAttribArray(texCoordLocation);
            GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT,
                    false, 16, vertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(positionLocation);
            GLES20.glDisableVertexAttribArray(texCoordLocation);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }

        private void remapBackgroundForSurface(int surfaceWidth, int surfaceHeight) {
            if (activeBackground == null || activeBackground.isRecycled()
                    || activeBackground.getWidth() == surfaceWidth
                    && activeBackground.getHeight() == surfaceHeight) {
                return;
            }
            Bitmap remapped = createMappedBackground(activeBackground,
                    surfaceWidth, surfaceHeight);
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
            GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }

        private long currentBackgroundSerial() {
            synchronized (bitmapLock) {
                return backgroundSerial;
            }
        }

        private float clamp(float value, float low, float high) {
            return Math.max(low, Math.min(high, value));
        }

        private float mix(float from, float to, float amount) {
            return from + (to - from) * amount;
        }
    }

    /**
     * Monotonic scene clock. It deliberately has no Handler, gesture, sound or lifecycle
     * dependency: it only protects timestamps passed to the GLES pipeline from replay.
     */
    static final class VisualTimeline {
        private long previousWallNanos = Long.MIN_VALUE;
        private long visualNanos;

        long sample(long wallNanos) {
            if (previousWallNanos == Long.MIN_VALUE) {
                previousWallNanos = wallNanos;
                visualNanos = wallNanos;
                return visualNanos;
            }
            if (wallNanos <= previousWallNanos) {
                return visualNanos;
            }
            previousWallNanos = wallNanos;
            visualNanos = wallNanos;
            return visualNanos;
        }

        void reset() {
            previousWallNanos = Long.MIN_VALUE;
            visualNanos = 0L;
        }
    }

    /**
     * Unlock-only render pacing evidence. Frame gaps include compositor/GPU scheduling delay;
     * draw duration captures CPU-side GLES submission time. Neither value feeds animation state.
     */
    static final class UnlockFramePacing {
        private static final long JANK_THRESHOLD_NANOS = 33_333_334L;
        private boolean active;
        private long previousFrameNanos;
        private long maxFrameGapNanos;
        private long maxDrawNanos;
        private int frameCount;
        private int jankCount;

        void begin() {
            active = true;
            previousFrameNanos = Long.MIN_VALUE;
            maxFrameGapNanos = 0L;
            maxDrawNanos = 0L;
            frameCount = 0;
            jankCount = 0;
        }

        void recordFrame(long frameStartedNanos, long drawFinishedNanos) {
            if (!active) {
                return;
            }
            long drawNanos = Math.max(0L, drawFinishedNanos - frameStartedNanos);
            maxDrawNanos = Math.max(maxDrawNanos, drawNanos);
            boolean jank = drawNanos > JANK_THRESHOLD_NANOS;
            if (previousFrameNanos != Long.MIN_VALUE) {
                long frameGapNanos = Math.max(0L, frameStartedNanos - previousFrameNanos);
                maxFrameGapNanos = Math.max(maxFrameGapNanos, frameGapNanos);
                jank |= frameGapNanos > JANK_THRESHOLD_NANOS;
            }
            previousFrameNanos = frameStartedNanos;
            ++frameCount;
            if (jank) {
                ++jankCount;
            }
        }

        String finishIfActive() {
            if (!active) {
                return null;
            }
            active = false;
            return "geometric unlock render metrics frames=" + frameCount
                    + " jank=" + jankCount
                    + " maxGapMs=" + (maxFrameGapNanos / 1_000_000L)
                    + " maxDrawMs=" + (maxDrawNanos / 1_000_000L);
        }

        void reset() {
            active = false;
            previousFrameNanos = Long.MIN_VALUE;
        }
    }

    private static final class Pulse {
        static final float KIND_TOUCH = 0.0f;
        static final float KIND_UNLOCK = 1.0f;
        static final float KIND_AFFORDANCE = 2.0f;

        final float x;
        final float y;
        final float start;
        final float kind;
        final float startRadius;

        Pulse(float x, float y, float start, float kind, float startRadius) {
            this.x = x;
            this.y = y;
            this.start = start;
            this.kind = kind;
            this.startRadius = startRadius;
        }
    }

    private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n"
            + "attribute vec2 aTexCoord;\n"
            + "varying highp vec2 UV;\n"
            + "void main() { UV = aTexCoord; gl_Position = vec4(aPosition, 0.0, 1.0); }\n";

    /*
     * Blend order and the two staggered circle lattices come from the ARM32
     * shaders/mesh builder.  The original renders the circles into two repeat
     * textures first; evaluating the same lattices here avoids two extra FBOs
     * without changing their geometry, palette, radii or alpha tracks.
     */
    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
            + "varying highp vec2 UV;\n"
            + "uniform sampler2D uBackground;\n"
            + "uniform sampler2D uMask;\n"
            + "uniform float uRingAge;\n"
            + "uniform float uCircleAspect;\n"
            + "uniform float uSeed;\n"
            + "float overlay1(float a, float b) { return a < 0.5 ? 2.0*a*b : 1.0-2.0*(1.0-a)*(1.0-b); }\n"
            + "vec3 overlay3(vec3 a, vec3 b) { return vec3(overlay1(a.r,b.r),overlay1(a.g,b.g),overlay1(a.b,b.b)); }\n"
            + "vec3 overlayGray(vec3 a, float b) { return vec3(overlay1(a.r,b),overlay1(a.g,b),overlay1(a.b,b)); }\n"
            + "vec3 linearDodge(vec3 a, vec3 b) { return min(a+b, vec3(1.0)); }\n"
            + "vec3 softLight(vec3 a, vec3 b) { return (vec3(1.0)-2.0*b)*a*a+2.0*b*a; }\n"
            + "float hash21(vec2 p) { return fract(sin(dot(p+uSeed,vec2(127.1,311.7)))*43758.5453); }\n"
            + "vec3 palette(float i) {\n"
            + "  if(i<0.5)return vec3(0.517647088,0.439215690,1.0);\n"
            + "  if(i<1.5)return vec3(0.678431392,1.0,0.184313729);\n"
            + "  if(i<2.5)return vec3(1.0,0.843137264,0.0);\n"
            + "  if(i<3.5)return vec3(0.803921580,0.360784322,0.360784322);\n"
            + "  if(i<4.5)return vec3(0.803921580,0.713725507,0.756862760);\n"
            + "  if(i<5.5)return vec3(0.513725519,0.043137256,1.0);\n"
            + "  if(i<6.5)return vec3(0.262745112,0.803921580,0.501960814);\n"
            + "  if(i<7.5)return vec3(1.0,0.752941191,0.752941191);\n"
            + "  if(i<8.5)return vec3(0.803921580,0.521568656,0.247058824);\n"
            + "  return vec3(1.0,0.188235298,0.188235298);\n"
            + "}\n"
            + "float ringAlpha(float r) { return clamp(1.5-(r*3.0)/(2.0*(2.0/6.0*1.25)),0.0,1.0); }\n"
            + "float radius0(){return mix(0.6,1.0,clamp(uRingAge/1.2,0.0,1.0))*(2.0/6.0*1.25);}\n"
            + "float radius1(){return mix(0.2,1.0,clamp(uRingAge/2.4,0.0,1.0))*(2.0/6.0*1.25);}\n"
            + "float radius2(){return clamp((uRingAge-0.6)/3.0,0.0,1.0)*(2.0/6.0*1.25);}\n"
            + "float radius3(){return radius0();}\n"
            + "float radius4(){return clamp(uRingAge/3.0,0.0,1.0)*(2.0/6.0*1.25);}\n"
            + "vec3 circle3(vec2 uv) {\n"
            + "  if(uRingAge>3.65)return vec3(0.0); vec2 p=fract(vec2(uv.x*2.0,uv.y*2.0*1.05))*2.0-1.0;\n"
            + "  float ix=clamp(floor((p.x+1.3333333)/0.6666667+0.5),0.0,4.0);\n"
            + "  float iy=clamp(floor((p.y+1.0)/0.4+0.5),0.0,5.0);\n"
            + "  vec2 center=vec2(-1.3333333+ix*0.6666667,-1.0+iy*0.4); vec2 d=p-center;\n"
            + "  float dist=sqrt(d.x*d.x+d.y*d.y*uCircleAspect*uCircleAspect);\n"
            + "  float r0=radius0(),r1=radius1(),r2=radius2(),best=99.0,a=0.0; vec3 c=vec3(0.0);\n"
            + "  vec2 id=vec2(ix,iy); if(dist<r0&&r0<best){best=r0;a=ringAlpha(r0);c=palette(floor(hash21(id+vec2(1.3,2.7))*10.0));}\n"
            + "  if(dist<r1&&r1<best){best=r1;a=ringAlpha(r1);c=palette(floor(hash21(id+vec2(4.1,7.9))*10.0));}\n"
            + "  if(dist<r2&&r2<best){a=ringAlpha(r2);c=palette(floor(hash21(id+vec2(8.3,3.4))*10.0));}\n"
            + "  return c*a;\n"
            + "}\n"
            + "vec3 circle2(vec2 uv) {\n"
            + "  if(uRingAge>3.65)return vec3(0.0); vec2 p=fract(vec2(uv.x*2.0,uv.y*2.0*1.05))*2.0-1.0;\n"
            + "  float ix=clamp(floor((p.x+1.0)/0.6666667+0.5),0.0,3.0);\n"
            + "  float iy=clamp(floor((p.y+1.2)/0.4+0.5),0.0,6.0);\n"
            + "  vec2 center=vec2(-1.0+ix*0.6666667,-1.2+iy*0.4); vec2 d=p-center;\n"
            + "  float dist=sqrt(d.x*d.x+d.y*d.y*uCircleAspect*uCircleAspect);\n"
            + "  float r3=radius3(),r4=radius4(),best=99.0,a=0.0; vec3 c=vec3(0.0); vec2 id=vec2(ix,iy);\n"
            + "  if(dist<r3&&r3<best){best=r3;a=ringAlpha(r3);c=palette(floor(hash21(id+vec2(11.7,5.2))*10.0));}\n"
            + "  if(dist<r4&&r4<best){a=ringAlpha(r4);c=palette(floor(hash21(id+vec2(15.1,9.6))*10.0));}\n"
            + "  return c*a;\n"
            + "}\n"
            + "vec3 mosaicSample(vec2 uv){vec2 grid=vec2(12.0,21.0);vec2 p=(floor(clamp(uv,vec2(0.0),vec2(0.9999))*grid)+0.5)/grid;return texture2D(uBackground,p).rgb;}\n"
            + "void main() {\n"
            + "  float m=texture2D(uMask,UV).r; if(m<=0.001){gl_FragColor=vec4(0.0);return;}\n"
            + "  vec2 grid=vec2(12.0,21.0); float bw=1.0/grid.x,bh=1.0/grid.y;\n"
            + "  float shift=bw*mod(UV.y,bh)/bh; vec2 coordL=vec2(UV.x+shift,UV.y);\n"
            + "  vec2 coordR=vec2(UV.x-shift,UV.y); vec2 coordRInv=vec2(UV.x-shift,1.0-UV.y);\n"
            + "  vec3 colorA=mosaicSample(coordL); vec3 colorB=mosaicSample(coordR); vec3 colorC=mosaicSample(UV);\n"
            + "  vec3 blur=(mosaicSample(coordL)+mosaicSample(coordL+vec2(bw,0.0))"
            + "+mosaicSample(coordL-vec2(bw,0.0))+mosaicSample(coordL+vec2(0.0,bh))"
            + "+mosaicSample(coordL-vec2(0.0,bh)))/5.0;\n"
            + "  float grayB=dot(blur,vec3(0.299,0.587,0.114)); if(grayB<0.2){blur+=vec3(0.3);grayB+=0.3;}\n"
            + "  float grayC=dot(mosaicSample(coordRInv),vec3(0.299,0.587,0.114));\n"
            + "  vec3 circles=circle3(UV); vec3 circles2=circle2(UV);\n"
            + "  vec3 c1=mix(colorA,linearDodge(colorA,circles),0.75);\n"
            + "  vec3 c2=mix(c1,softLight(c1,circles2),0.40); vec3 c3=overlay3(c2,colorB);\n"
            + "  vec3 c4=mix(c3,max(c3,colorC),0.75); vec3 c5=mix(c4,overlayGray(c4,grayB),0.5);\n"
            + "  vec3 c6=mix(c5,overlayGray(c5,grayC),0.5); float alpha=sqrt(clamp(m,0.0,1.0));\n"
            + "  gl_FragColor=vec4(clamp(c6,0.0,1.0)*alpha,alpha);\n"
            + "}\n";

    private final class WindowHost extends FrameLayout {
        WindowHost(Context context) {
            super(context);
        }

        @Override
        public void setAlpha(float alpha) {
            super.setAlpha(alpha);
            GeometricMosaicArm64EffectView.this.setAlpha(alpha);
        }

        @Override
        public void setVisibility(int visibility) {
            super.setVisibility(visibility);
            GeometricMosaicArm64EffectView.this.setVisibility(visibility);
        }
    }
}
