package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.SoundPool;
import android.opengl.GLSurfaceView;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Hidden app-owned ARM64 host for Samsung's Note 3 Ripple Ink lineage.
 *
 * <p>This class never loads Samsung's ARM32 ELF. It remains unregistered until the recovered
 * velocity/advection pipeline, exact reflection asset and transparent-overlay output have device
 * parity evidence.</p>
 */
public final class RippleInkPortEffectView extends GLSurfaceView
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness,
        RippleInkPortGlesRenderer.Host {
    public static final int DEFAULT_PALETTE_SLOT = 4;

    private final RippleInkPortGlesRenderer rippleRenderer;
    private final SoundPool soundPool;
    private final int downSound;
    private final int upSound;
    private final Object readinessLock = new Object();
    private final Object bitmapLock = new Object();
    private volatile boolean destroyed;
    private volatile boolean paused;
    private volatile boolean externalBackground;
    private int paletteSlot;
    private boolean highFrameRateEnabled;
    private int readinessState = UnlockEffectReadiness.STATE_CONSTRUCTED;
    private String readinessDetail = "constructed; N3 ARM64 renderer";
    private UnlockEffectReadiness.ReadinessListener readinessListener;
    private Bitmap ownedBackground;
    private Bitmap ownedReflection;
    private float lastLocalX;
    private float lastLocalY;
    private float lastSoundX;
    private float lastSoundY;
    private float dragSoundDistance;
    private float lastStylusPressure = 1.0f;
    private long gestureDownAtMs;
    private volatile int animationGeneration;
    private Runnable affordanceRunnable;

    public RippleInkPortEffectView(Context context) {
        this(context, DEFAULT_PALETTE_SLOT, false);
    }

    public RippleInkPortEffectView(Context context, int paletteSlot) {
        this(context, paletteSlot, false);
    }

    public RippleInkPortEffectView(
            Context context, int paletteSlot, boolean highFrameRateEnabled) {
        super(context);
        validatePaletteSlot(paletteSlot);
        this.paletteSlot = paletteSlot;
        this.highFrameRateEnabled = highFrameRateEnabled;
        rippleRenderer = new RippleInkPortGlesRenderer(
                this, paletteSlot, highFrameRateEnabled);
        soundPool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        // These resources are byte-identical to the demo host's ve_ripple_down/up assets.
        downSound = soundPool.load(context, R.raw.s3_ripple_down, 1);
        upSound = soundPool.load(context, R.raw.s3_ripple_up, 1);
        ownedReflection = createBundledProceduralReflection(context);
        if (ownedReflection != null) {
            // This happens before setRenderer(), so no cross-thread GL call is involved.
            rippleRenderer.installReflection(ownedReflection);
        }
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(false);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setRenderer(rippleRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setFocusable(false);
    }

    /** Thread-safe live palette update; the effect does not need to be recreated. */
    public void setInkPaletteSlot(final int slot) {
        validatePaletteSlot(slot);
        paletteSlot = slot;
        if (!canAcceptCommands()) {
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.setPaletteSelector(slot);
            }
        });
        requestRender();
    }

    public int getInkPaletteSlot() {
        return paletteSlot;
    }

    /** Live HFR toggle: only water/mesh becomes display-adaptive; Ink remains fixed 60 Hz. */
    public void setHighFrameRateEnabled(final boolean enabled) {
        if (highFrameRateEnabled == enabled) {
            return;
        }
        highFrameRateEnabled = enabled;
        if (!canAcceptCommands()) {
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.setHighFrameRateEnabled(enabled);
            }
        });
        requestRender();
    }

    public boolean isHighFrameRateEnabled() {
        return highFrameRateEnabled;
    }

    /** Installs a caller-supplied effect-owned environment/reflection texture. */
    public void setRippleInkReflectionBitmap(Bitmap source) {
        if (source == null || source.isRecycled() || destroyed) {
            return;
        }
        final Bitmap candidate = copyArgb8888(source);
        if (candidate == null) {
            return;
        }
        final Bitmap previous;
        synchronized (bitmapLock) {
            if (destroyed) {
                candidate.recycle();
                return;
            }
            previous = ownedReflection;
            ownedReflection = candidate;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.installReflection(candidate);
                recycle(previous);
            }
        });
        requestRender();
    }

    /** Production gate: the first frame proves the full GLES/native resource chain. */
    public boolean isProductionReady() {
        return !destroyed && rippleRenderer.isProductionReady();
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "N3 Ripple Ink ARM64";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        beginGestureWithPressure(screenX, screenY, 1.0f);
    }

    public void beginStylusGesture(float screenX, float screenY, float pressure) {
        lastStylusPressure = normalizePressure(pressure);
        beginGestureWithPressure(screenX, screenY, lastStylusPressure, true);
    }

    public void beginWaterOnlyGesture(float screenX, float screenY) {
        beginGestureWithPressure(screenX, screenY, 0.0f, false);
    }

    private void beginGestureWithPressure(float screenX, float screenY, float pressure) {
        beginGestureWithPressure(screenX, screenY, pressure, true);
        }

        private void beginGestureWithPressure(float screenX, float screenY, float pressure,
            boolean inkEnabled) {
        cancelAffordance();
        float[] local = toLocal(screenX, screenY);
        gestureDownAtMs = SystemClock.uptimeMillis();
        lastSoundX = local[0];
        lastSoundY = local[1];
        dragSoundDistance = 0.0f;
        play(downSound);
        routeTouch(RippleInkPortEngine.ACTION_DOWN, local[0], local[1], pressure,
            SystemClock.uptimeMillis(), inkEnabled);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        updateGestureWithPressure(screenX, screenY, 1.0f);
    }

    public void updateStylusGesture(float screenX, float screenY, float pressure) {
        lastStylusPressure = normalizePressure(pressure);
        updateGestureWithPressure(screenX, screenY, lastStylusPressure, true);
    }

    public void updateWaterOnlyGesture(float screenX, float screenY) {
        updateGestureWithPressure(screenX, screenY, 0.0f, false);
    }

    private void updateGestureWithPressure(float screenX, float screenY, float pressure) {
        updateGestureWithPressure(screenX, screenY, pressure, true);
        }

        private void updateGestureWithPressure(float screenX, float screenY, float pressure,
            boolean inkEnabled) {
        float[] local = toLocal(screenX, screenY);
        float soundDx = local[0] - lastSoundX;
        float soundDy = local[1] - lastSoundY;
        dragSoundDistance += (float) Math.sqrt(soundDx * soundDx + soundDy * soundDy);
        lastSoundX = local[0];
        lastSoundY = local[1];
        if (dragSoundDistance > 150.0f) {
            play(downSound);
            dragSoundDistance = 0.0f;
        }
        routeTouch(RippleInkPortEngine.ACTION_MOVE, local[0], local[1], pressure,
            SystemClock.uptimeMillis(), inkEnabled);
    }

    @Override
    public void finishGesture(boolean completed) {
        finishGestureWithPressure(completed, 1.0f);
    }

    public void finishStylusGesture() {
        finishGestureWithPressure(false, lastStylusPressure, true);
    }

    public void finishWaterOnlyGesture() {
        finishGestureWithPressure(false, 0.0f, false);
    }

    private void finishGestureWithPressure(boolean completed, float pressure) {
        finishGestureWithPressure(completed, pressure, true);
        }

        private void finishGestureWithPressure(boolean completed, float pressure,
            boolean inkEnabled) {
        long heldForMs = SystemClock.uptimeMillis() - gestureDownAtMs;
        routeTouch(RippleInkPortEngine.ACTION_UP, lastLocalX, lastLocalY, pressure,
            SystemClock.uptimeMillis(), inkEnabled);
        dragSoundDistance = 0.0f;
        if (completed) {
            play(upSound);
        } else if (heldForMs > 600L) {
            play(downSound);
        }
    }

    private static float normalizePressure(float pressure) {
        return Math.max(0.0f, Math.min(1.0f, pressure));
    }

    @Override
    public void cancelGesture() {
        routeTouch(RippleInkPortEngine.ACTION_CANCEL, lastLocalX, lastLocalY,
                1.0f, SystemClock.uptimeMillis());
        dragSoundDistance = 0.0f;
    }

    @Override
    public void resetEffect() {
        cancelAffordance();
        ++animationGeneration;
        if (!canAcceptCommands()) {
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.reset();
            }
        });
        requestRender();
    }

    @Override
    public void warmUp() {
        if (canAcceptCommands()) {
            requestRender();
        }
    }

    @Override
    public void showUnlockAffordance(final Rect screenRect, long startDelayMs) {
        cancelAffordance();
        if (screenRect == null || !canAcceptCommands()) {
            return;
        }
        affordanceRunnable = new Runnable() {
            @Override
            public void run() {
                if (!canAcceptCommands()) {
                    return;
                }
                // Samsung affordance invokes only the water ripple helper. It never sends a
                // synthetic RippleInk onTouch DOWN/UP pair, which would create a false cloud.
                // The app-owned water path is refreshed by the normal renderer wake-up here.
                requestRender();
            }
        };
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null || !canAcceptCommands()) {
            return false;
        }
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE
                && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }
        // Samsung forwarded the current MotionEvent coordinates once. Replaying Android's
        // historical samples changes the recovered 2/10 px mode classifier and over-densifies
        // the trail, so the fixed-60 Ink clock receives only this current callback sample.
        routeTouch(action, event.getX(), event.getY(), event.getPressure(), event.getEventTime());
        return true;
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalBackground;
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (source == null || source.isRecycled() || destroyed) {
            return;
        }
        int targetWidth = Math.max(1, getWidth() > 0
                ? getWidth() : getResources().getDisplayMetrics().widthPixels);
        int targetHeight = Math.max(1, getHeight() > 0
                ? getHeight() : getResources().getDisplayMetrics().heightPixels);
        final Bitmap candidate = centerCropArgb8888(source, targetWidth, targetHeight);
        if (candidate == null) {
            return;
        }
        final Bitmap previous;
        synchronized (bitmapLock) {
            if (destroyed) {
                candidate.recycle();
                return;
            }
            previous = ownedBackground;
            ownedBackground = candidate;
        }
        externalBackground = true;
        setReadinessState(UnlockEffectReadiness.STATE_SURFACE_READY,
                "background queued; reflection/advection validation pending");
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.installBackground(candidate);
                recycle(previous);
            }
        });
        requestRender();
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        final Bitmap previous;
        synchronized (bitmapLock) {
            previous = ownedBackground;
            ownedBackground = null;
        }
        externalBackground = false;
        if (destroyed) {
            recycle(previous);
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.installBackground(null);
                recycle(previous);
            }
        });
        setReadinessState(UnlockEffectReadiness.STATE_SURFACE_READY,
                "background cleared; diagnostic renderer unavailable");
        requestRender();
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
        notifyReadiness(listener);
    }

    @Override
    public void onRippleInkGlesState(int state, String detail) {
        setReadinessState(state, detail);
    }

    @Override
    public void onRippleInkIdle() {
        final int generation = animationGeneration;
        post(new Runnable() {
            @Override
            public void run() {
                if (!destroyed && generation == animationGeneration) {
                    setRenderMode(RENDERMODE_WHEN_DIRTY);
                }
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setReadinessState(UnlockEffectReadiness.STATE_ATTACHED,
                "attached; hidden diagnostic renderer");
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!destroyed) {
            onPause();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onPause() {
        if (paused) {
            return;
        }
        paused = true;
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    // Keep the retained density field; only discard timing debt across pause.
                    rippleRenderer.resetFrameClock();
                }
            });
        } catch (RuntimeException ignored) {
            // The surface can already be detached.
        }
        super.onPause();
        setReadinessState(UnlockEffectReadiness.STATE_DETACHED, "paused");
    }

    @Override
    public void onResume() {
        if (destroyed || !paused) {
            return;
        }
        super.onResume();
        paused = false;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.resetFrameClock();
            }
        });
        setReadinessState(UnlockEffectReadiness.STATE_ATTACHED, "resumed");
        requestRender();
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        cancelAffordance();
        soundPool.release();
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    rippleRenderer.releaseGl();
                }
            });
            requestRender();
        } catch (RuntimeException ignored) {
            // The EGL thread can already be gone during service teardown.
        }
        synchronized (bitmapLock) {
            recycle(ownedBackground);
            recycle(ownedReflection);
            ownedBackground = null;
            ownedReflection = null;
        }
        externalBackground = false;
    }

    private void routeTouch(final int action, final float localX, final float localY,
            final float pressure, final long eventTimeMs) {
        routeTouch(action, localX, localY, pressure, eventTimeMs, true);
    }

    private void routeTouch(final int action, final float localX, final float localY,
            final float pressure, final long eventTimeMs, final boolean inkEnabled) {
        if (!canAcceptCommands()) {
            return;
        }
        lastLocalX = localX;
        lastLocalY = localY;
        ++animationGeneration;
        activateContinuousRendering();
        // N3's Java wrapper calls JNI from the UI callback with only its current sample.  Keep
        // that temporal boundary with a latest-value mailbox; the queued GL work below is water.
        rippleRenderer.publishFinger(action, localX, localY, pressure, inkEnabled);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                rippleRenderer.handleFinger(action, localX, localY, pressure, eventTimeMs,
                        inkEnabled);
            }
        });
        requestRender();
    }

    private void activateContinuousRendering() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (canAcceptCommands()) {
                setRenderMode(RENDERMODE_CONTINUOUSLY);
            }
        } else {
            post(new Runnable() {
                @Override
                public void run() {
                    if (canAcceptCommands()) {
                        setRenderMode(RENDERMODE_CONTINUOUSLY);
                    }
                }
            });
        }
    }

    private float[] toLocal(float screenX, float screenY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return new float[]{screenX - location[0], screenY - location[1]};
    }

    private boolean canAcceptCommands() {
        return !destroyed && !paused;
    }

    private void play(int soundId) {
        if (!destroyed && soundId != 0
                && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
        }
    }

    private void cancelAffordance() {
        if (affordanceRunnable != null) {
            removeCallbacks(affordanceRunnable);
            affordanceRunnable = null;
        }
    }

    private void setReadinessState(int state, String detail) {
        UnlockEffectReadiness.ReadinessListener listener;
        synchronized (readinessLock) {
            readinessState = state;
            readinessDetail = detail == null ? "" : detail;
            listener = readinessListener;
        }
        notifyReadiness(listener);
    }

    private void notifyReadiness(final UnlockEffectReadiness.ReadinessListener listener) {
        if (listener == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onReadinessChanged();
        } else {
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onReadinessChanged();
                }
            });
        }
    }

    private static Bitmap centerCropArgb8888(Bitmap source, int width, int height) {
        try {
            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            float scale = Math.max(width / (float) source.getWidth(),
                    height / (float) source.getHeight());
            float drawWidth = source.getWidth() * scale;
            float drawHeight = source.getHeight() * scale;
            float left = (width - drawWidth) * 0.5f;
            float top = (height - drawHeight) * 0.5f;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(source, null,
                    new android.graphics.RectF(left, top, left + drawWidth, top + drawHeight),
                    paint);
            return output;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Bitmap copyArgb8888(Bitmap source) {
        try {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Bitmap createBundledProceduralReflection(Context context) {
        // Provenance: the app-owned S3 forest-sphere source is SHA256 061ce0...b130.
        // The authoritative 88991 ELF names/uses a reflection texture but its extracted bundle
        // contains no paired resource. A same-family S4 candidate (SHA256 75390a...0d79) is the
        // same 512px forest sphere with an approximately +90 non-black channel exposure. Keep
        // this procedural equivalent explicit until the exact paired bytes are provenance-safe.
        int identifier = context.getResources().getIdentifier(
                "s3_reflectionmap", "drawable", context.getPackageName());
        if (identifier == 0) {
            return null;
        }
        Bitmap source = null;
        Bitmap output = null;
        try {
            source = BitmapFactory.decodeResource(context.getResources(), identifier);
            if (source == null || source.isRecycled()) {
                return null;
            }
            int width = source.getWidth();
            int height = source.getHeight();
            int[] pixels = new int[width * height];
            source.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int index = 0; index < pixels.length; ++index) {
                pixels[index] = RippleInkPortCompositor.calibrateReflectionPixel(pixels[index]);
            }
            output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            output.setPixels(pixels, 0, width, 0, 0, width, height);
            return output;
        } catch (RuntimeException exception) {
            recycle(output);
            return null;
        } finally {
            recycle(source);
        }
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private static void validatePaletteSlot(int slot) {
        if (!RippleInkPortEngine.isInkEnabledSelector(slot)) {
            throw new IllegalArgumentException("Ripple Ink palette slot must be 1..8");
        }
    }
}
