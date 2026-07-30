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
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * DEX-free lifecycle host for LLE's reconstructed ARM64 Watercolor engine.
 */
public final class WatercolorArm64EffectView extends GLSurfaceView
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "LLE64Watercolor";
    private static final long FRAME_INTERVAL_MS = 16L;
    private static final long MIN_AFFORDANCE_DELAY_MS = 1_000L;
    private static final long LONG_PRESS_SOUND_MS = 411L;

    private static final AtomicReference<WatercolorArm64EffectView> NATIVE_OWNER =
            new AtomicReference<WatercolorArm64EffectView>();

    private final FrameLayout windowHost;
    private final WatercolorRenderer renderer = new WatercolorRenderer();
    private final Object bitmapLock = new Object();
    private final Object readinessLock = new Object();
    private final boolean ownsNativeSlot;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;

    private volatile boolean destroyed;
    private volatile boolean paused;
    private volatile boolean constructionFailed;
    private volatile Bitmap backgroundBitmap;
    private volatile boolean ownsBackgroundBitmap;
    private volatile long backgroundSerial;
    private String backgroundSource = "none";
    private boolean gestureActive;
    private long downTime;
    private boolean longPressSoundPlayed;
    private float lastX;
    private float lastY;
    private int animationGeneration;
    private boolean animationScheduled;
    private int affordanceGeneration;
    private int readinessState = UnlockEffectReadiness.STATE_CONSTRUCTED;
    private String readinessDetail = "constructed";
    private UnlockEffectReadiness.ReadinessListener readinessListener;

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || paused || !animationScheduled) {
                return;
            }
            requestRender();
            postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    public WatercolorArm64EffectView(Context context) {
        super(context);
        ownsNativeSlot = NATIVE_OWNER.compareAndSet(null, this);

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(false);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        windowHost = new WindowHost(context);
        windowHost.setBackgroundColor(Color.TRANSPARENT);
        windowHost.setClipChildren(false);
        windowHost.setClipToPadding(false);
        windowHost.addView(this, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tapSound = soundPool.load(context, R.raw.ve_watercolour_tap, 1);
        unlockSound = soundPool.load(context, R.raw.ve_watercolour_unlock, 1);

        if (!ownsNativeSlot) {
            constructionFailed = true;
            failReadiness("native singleton already owned");
        } else if (!WatercolorArm64Native.isAvailable()) {
            constructionFailed = true;
            failReadiness("native bridge unavailable");
        }
    }

    public boolean isReady() {
        return ownsCurrentNativeSlot() && !destroyed && !constructionFailed
                && WatercolorArm64Native.isAvailable();
    }

    @Override
    public View asView() {
        return windowHost;
    }

    @Override
    public String effectName() {
        return "N3 Watercolor ARM64";
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

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRenderEffect()) {
            return;
        }
        cancelPendingAffordance();
        downTime = SystemClock.uptimeMillis();
        longPressSoundPlayed = false;
        lastX = screenX;
        lastY = screenY;
        gestureActive = true;
        play(tapSound);
        queueTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        if (!canRenderEffect()) {
            return;
        }
        lastX = screenX;
        lastY = screenY;
        queueTouch(MotionEvent.ACTION_MOVE, screenX, screenY);
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        playReleaseTapIfNeeded();
        queueTouch(MotionEvent.ACTION_UP, lastX, lastY);
        downTime = 0L;
        if (completed) {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    if (renderer.nativeBridge != null) {
                        renderer.nativeBridge.showUnlock();
                    }
                }
            });
            play(unlockSound);
            startAnimationLoop();
        }
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        playReleaseTapIfNeeded();
        queueTouch(MotionEvent.ACTION_CANCEL, lastX, lastY);
        downTime = 0L;
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        longPressSoundPlayed = false;
        cancelPendingAffordance();
        if (!isReady()) {
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (renderer.nativeBridge != null) {
                    renderer.nativeBridge.clear();
                }
            }
        });
        requestRender();
    }

    @Override
    public void warmUp() {
        if (!isReady()) {
            return;
        }
        requestRender();
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (!canRenderEffect()) {
            return;
        }
        cancelPendingAffordance();
        final int generation = ++affordanceGeneration;
        Rect bounds = screenRect == null || screenRect.isEmpty()
                ? new Rect(0, 0, getRenderWidth(), getRenderHeight())
                : new Rect(screenRect);
        final int x = bounds.centerX();
        final int y = bounds.centerY();
        long delay = Math.max(MIN_AFFORDANCE_DELAY_MS, startDelayMs);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (generation != affordanceGeneration || !canRenderEffect()) {
                    return;
                }
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        if (renderer.nativeBridge != null) {
                            renderer.nativeBridge.showAffordance(x, y);
                        }
                    }
                });
                startAnimationLoop();
            }
        }, delay);
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        Bitmap bitmap = backgroundBitmap;
        return bitmap != null && !bitmap.isRecycled()
                && bitmap.getWidth() == getRenderWidth()
                && bitmap.getHeight() == getRenderHeight();
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        int width = getRenderWidth();
        int height = getRenderHeight();
        boolean borrow = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, width, height);
        Bitmap next = borrow ? source : centerCrop(source, width, height);
        next.prepareToDraw();
        synchronized (bitmapLock) {
            releaseBackgroundBitmapLocked();
            backgroundBitmap = next;
            ownsBackgroundBitmap = !borrow;
            backgroundSource = sourceName == null ? "external" : sourceName;
            backgroundSerial++;
        }
        requestRender();
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        synchronized (bitmapLock) {
            releaseBackgroundBitmapLocked();
            backgroundSource = "none";
            backgroundSerial++;
        }
        resetEffect();
    }

    @Override
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && backgroundBitmap == bitmap;
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        gestureActive = false;
        cancelPendingAffordance();
        stopAnimationLoop();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.destroyNative();
            }
        });
        onPause();
        soundPool.release();
        synchronized (bitmapLock) {
            releaseBackgroundBitmapLocked();
        }
        NATIVE_OWNER.compareAndSet(this, null);
        markReadiness(UnlockEffectReadiness.STATE_DETACHED, "destroyed");
    }

    public void parkForReuse() {
        if (!destroyed && !paused) {
            paused = true;
            stopAnimationLoop();
            WatercolorArm64Native.pauseAnimation();
            onPause();
            markReadiness(UnlockEffectReadiness.STATE_DETACHED, "parked");
        }
    }

    public void resumeForReuse() {
        if (!destroyed && paused) {
            paused = false;
            onResume();
            WatercolorArm64Native.resumeAnimation();
            requestRender();
        }
    }

    private void queueTouch(final int action, float screenX, float screenY) {
        final float[] local = toLocalCoordinates(screenX, screenY);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (renderer.nativeBridge != null) {
                    renderer.nativeBridge.onTouch(
                            Math.round(local[0]), Math.round(local[1]), action);
                }
            }
        });
        startAnimationLoop();
    }

    private float[] toLocalCoordinates(float screenX, float screenY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return new float[]{screenX - location[0], screenY - location[1]};
    }

    private boolean canRenderEffect() {
        return isReady() && !paused && hasBackgroundSourceBitmap();
    }

    private boolean ownsCurrentNativeSlot() {
        return ownsNativeSlot && NATIVE_OWNER.get() == this;
    }

    private void startAnimationLoop() {
        if (destroyed || paused) {
            return;
        }
        animationGeneration++;
        if (!animationScheduled) {
            animationScheduled = true;
            removeCallbacks(animationRunnable);
            post(animationRunnable);
        }
    }

    private void stopAnimationLoop() {
        animationGeneration++;
        animationScheduled = false;
        removeCallbacks(animationRunnable);
    }

    private void cancelPendingAffordance() {
        affordanceGeneration++;
    }

    private void playReleaseTapIfNeeded() {
        if (!longPressSoundPlayed && downTime > 0L
                && SystemClock.uptimeMillis() - downTime > LONG_PRESS_SOUND_MS) {
            longPressSoundPlayed = true;
            play(tapSound);
        }
    }

    private void play(int soundId) {
        if (!destroyed && soundId != 0
                && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private Bitmap centerCrop(Bitmap source, int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
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
        new Canvas(output).drawBitmap(source, sourceRect,
                new Rect(0, 0, width, height), paint);
        return output;
    }

    private int getRenderWidth() {
        return Math.max(1, getWidth() > 0
                ? getWidth() : getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        return Math.max(1, getHeight() > 0
                ? getHeight() : getResources().getDisplayMetrics().heightPixels);
    }

    private void releaseBackgroundBitmapLocked() {
        Bitmap bitmap = backgroundBitmap;
        if (ownsBackgroundBitmap && bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        backgroundBitmap = null;
        ownsBackgroundBitmap = false;
    }

    private void failReadiness(String detail) {
        markReadiness(UnlockEffectReadiness.STATE_FAILED, detail);
    }

    private void markReadiness(int state, String detail) {
        ReadinessListener listener;
        synchronized (readinessLock) {
            readinessState = state;
            readinessDetail = detail;
            listener = readinessListener;
        }
        notifyReadinessListener(listener);
    }

    private void notifyReadinessListener(final ReadinessListener listener) {
        if (listener == null) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                listener.onReadinessChanged();
            }
        });
    }

    private int[] bitmapPixels(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return pixels;
    }

    private Bitmap decodeTexture(int resourceId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(getResources(), resourceId, options);
    }

    private final class WatercolorRenderer implements GLSurfaceView.Renderer {
        private WatercolorArm64Native nativeBridge;
        private boolean initialized;
        private boolean assetsUploaded;
        private boolean firstFrameReported;
        private long uploadedBackgroundSerial = -1L;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            destroyNative();
            try {
                nativeBridge = new WatercolorArm64Native();
                String[] names = nativeBridge.loadEffect("libsecveWaterColor.so");
                if (names == null || names.length < 5) {
                    throw new IllegalStateException("unexpected texture manifest");
                }
                initialized = false;
                assetsUploaded = false;
                firstFrameReported = false;
                uploadedBackgroundSerial = -1L;
                markReadiness(UnlockEffectReadiness.STATE_ATTACHED, "surface created");
            } catch (Throwable error) {
                constructionFailed = true;
                failReadiness("native surface creation failed: "
                        + error.getClass().getSimpleName());
                Log.e(TAG, "Watercolor surface creation failed", error);
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            if (nativeBridge == null || constructionFailed) {
                return;
            }
            try {
                nativeBridge.init(Math.max(1, width), Math.max(1, height), true);
                initialized = true;
                uploadAssets();
                uploadBackgroundIfNeeded();
                markReadiness(UnlockEffectReadiness.STATE_RESOURCES_READY,
                        "native resources ready " + width + "x" + height);
                requestRender();
            } catch (Throwable error) {
                constructionFailed = true;
                failReadiness("native initialization failed: "
                        + error.getClass().getSimpleName());
                Log.e(TAG, "Watercolor initialization failed", error);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (!initialized || nativeBridge == null || constructionFailed) {
                return;
            }
            try {
                uploadBackgroundIfNeeded();
                boolean active = nativeBridge.draw();
                if (!firstFrameReported) {
                    firstFrameReported = true;
                    markReadiness(UnlockEffectReadiness.STATE_FIRST_FRAME_READY,
                            "first native frame drawn");
                }
                if (!active && !gestureActive) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (!gestureActive) {
                                stopAnimationLoop();
                            }
                        }
                    });
                }
            } catch (Throwable error) {
                constructionFailed = true;
                failReadiness("native draw failed: " + error.getClass().getSimpleName());
                Log.e(TAG, "Watercolor draw failed", error);
            }
        }

        private void uploadAssets() {
            if (assetsUploaded || nativeBridge == null) {
                return;
            }
            uploadTexture("watercolor_mask1", R.drawable.watercolor_mask1);
            uploadTexture("watercolor_mask2", R.drawable.watercolor_mask2);
            uploadTexture("watercolor_mask3", R.drawable.watercolor_mask3);
            uploadTexture("watercolor_noise", R.drawable.watercolor_noise);
            uploadTexture("waterbrush_tube", R.drawable.waterbrush_tube);
            assetsUploaded = true;
        }

        private void uploadTexture(String name, int resourceId) {
            Bitmap bitmap = decodeTexture(resourceId);
            if (bitmap == null) {
                throw new IllegalStateException("missing texture " + name);
            }
            try {
                nativeBridge.loadTexture(name, bitmapPixels(bitmap),
                        bitmap.getWidth(), bitmap.getHeight());
            } finally {
                bitmap.recycle();
            }
        }

        private void uploadBackgroundIfNeeded() {
            Bitmap bitmap;
            long serial;
            synchronized (bitmapLock) {
                bitmap = backgroundBitmap;
                serial = backgroundSerial;
                if (bitmap == null || bitmap.isRecycled()
                        || uploadedBackgroundSerial == serial) {
                    return;
                }
                int[] pixels = bitmapPixels(bitmap);
                nativeBridge.loadTexture("bg", pixels, bitmap.getWidth(), bitmap.getHeight());
                uploadedBackgroundSerial = serial;
            }
            Log.i(TAG, "background uploaded source=" + backgroundSource
                    + " serial=" + serial);
        }

        private void destroyNative() {
            if (nativeBridge != null) {
                try {
                    nativeBridge.destroy();
                } catch (Throwable ignored) {
                    // The GL context may already have been abandoned by SurfaceView.
                }
            }
            nativeBridge = null;
            initialized = false;
            assetsUploaded = false;
            firstFrameReported = false;
            uploadedBackgroundSerial = -1L;
        }
    }

    private static final class WindowHost extends FrameLayout {
        WindowHost(Context context) {
            super(context);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
        }
    }
}
