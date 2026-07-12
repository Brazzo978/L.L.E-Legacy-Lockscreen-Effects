package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.opengl.GLSurfaceView;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.concurrent.locks.LockSupport;

/** Host for the original Samsung S3 keyguard GLSurfaceView and libWaterRipple. */
public final class S3NativeRippleEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer {
    private static final String TAG = "ChargingS3Native";
    private static final String EFFECT_CLASS =
            "com.android.internal.policy.impl.keyguard.sec.RippleUnlockView";
    private static final long ORIGINAL_FRAME_INTERVAL_NS = 16_666_667L;
    private static volatile long lastOriginalFrameNs;

    private Object effect;
    private View effectView;
    private Method handleTouchEvent;
    private Method handleUnlock;
    private Method show;
    private Method screenTurnedOn;
    private Method showUnlockAffordance;
    private Method reset;
    private Method cleanUp;
    private Method onResume;
    private Method onPause;
    private Object nativeRenderer;
    private Method setTexture;
    private Method transferBGBitmap;
    private Method onLoadBGTextures;
    private volatile boolean externalBackground;
    private boolean resumed;
    private long gestureDownTime;
    private float lastX;
    private float lastY;
    private boolean destroyed;

    public S3NativeRippleEffectView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(false);
        setClipToPadding(false);
        ensureEffect();
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S3 ripple original native";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        gestureDownTime = SystemClock.uptimeMillis();
        lastX = screenX;
        lastY = screenY;
        sendTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        lastX = screenX;
        lastY = screenY;
        sendTouch(MotionEvent.ACTION_MOVE, screenX, screenY);
    }

    @Override
    public void finishGesture(boolean completed) {
        MotionEvent up = obtainEvent(MotionEvent.ACTION_UP, lastX, lastY);
        if (up == null) {
            return;
        }
        try {
            invokeTouch(up);
            if (completed && effect != null && handleUnlock != null) {
                handleUnlock.invoke(effect, this, up);
            }
        } catch (Throwable t) {
            Log.e(TAG, "native ripple finish failed", t);
        } finally {
            up.recycle();
            gestureDownTime = 0L;
        }
    }

    @Override
    public void cancelGesture() {
        sendTouch(MotionEvent.ACTION_CANCEL, lastX, lastY);
        gestureDownTime = 0L;
    }

    @Override
    public void resetEffect() {
        invokeNoArgs(reset);
    }

    @Override
    public void warmUp() {
        ensureEffect();
        resumeOriginalRendererIfNeeded();
    }

    @Override
    public void showUnlockAffordance(final Rect screenRect, final long startDelayMs) {
        if (!ensureEffect() || showUnlockAffordance == null) {
            return;
        }
        final Rect rect = screenRect == null
                ? new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()))
                : new Rect(screenRect);
        post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Samsung's renderer deliberately ignores the affordance until
                    // screenTurnedOn() sets calledScreenTurnedOn. The original keyguard
                    // host invoked this lifecycle callback; the accessibility host must
                    // reproduce it explicitly before scheduling the center ripple.
                    if (screenTurnedOn != null) {
                        screenTurnedOn.invoke(effect);
                    }
                    showUnlockAffordance.invoke(effect, startDelayMs, rect);
                    if (effectView instanceof GLSurfaceView) {
                        ((GLSurfaceView) effectView).requestRender();
                    }
                } catch (Throwable t) {
                    Log.d(TAG, "native affordance ignored", t);
                }
            }
        });
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalBackground;
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (source == null || source.isRecycled() || !ensureEffect()
                || nativeRenderer == null || setTexture == null
                || transferBGBitmap == null || onLoadBGTextures == null
                || !(effectView instanceof GLSurfaceView)) {
            return;
        }
        final Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
        if (copy == null) {
            return;
        }
        try {
            // setTexture transfers ownership to the original renderer and
            // recycles its previous Samsung fallback bitmap.
            setTexture.invoke(nativeRenderer, copy);
            ((GLSurfaceView) effectView).queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        transferBGBitmap.invoke(null, copy);
                        onLoadBGTextures.invoke(null);
                        externalBackground = true;
                        Log.i(TAG, "live lockscreen background uploaded size="
                                + copy.getWidth() + "x" + copy.getHeight());
                    } catch (Throwable t) {
                        externalBackground = false;
                        Log.e(TAG, "native background JNI upload failed", t);
                    }
                }
            });
            ((GLSurfaceView) effectView).requestRender();
            Log.i(TAG, "live lockscreen background queued source=" + sourceName);
        } catch (Throwable t) {
            externalBackground = false;
            if (!copy.isRecycled()) {
                copy.recycle();
            }
            Log.e(TAG, "native background setTexture failed", t);
        }
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        externalBackground = false;
    }

    @Override
    public void destroy() {
        destroyed = true;
        if (resumed) {
            invokeNoArgs(onPause);
            resumed = false;
        }
        invokeNoArgs(reset);
        invokeNoArgs(cleanUp);
        removeAllViews();
        effect = null;
        effectView = null;
        handleTouchEvent = null;
        handleUnlock = null;
        show = null;
        screenTurnedOn = null;
        showUnlockAffordance = null;
        reset = null;
        cleanUp = null;
        onResume = null;
        onPause = null;
        nativeRenderer = null;
        setTexture = null;
        transferBGBitmap = null;
        onLoadBGTextures = null;
        externalBackground = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        resumeOriginalRendererIfNeeded();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (resumed) {
            invokeNoArgs(onPause);
            resumed = false;
        }
        super.onDetachedFromWindow();
    }

    private void resumeOriginalRendererIfNeeded() {
        if (!ensureEffect() || resumed) {
            return;
        }
        invokeNoArgs(onResume);
        resumed = true;
    }

    private boolean ensureEffect() {
        if (destroyed || effect != null) {
            return effect != null;
        }
        try {
            Class<?> cls = Class.forName(EFFECT_CLASS);
            effect = cls.getConstructor(Context.class).newInstance(getContext());
            effectView = (View) effect;
            if (effectView instanceof SurfaceView) {
                SurfaceView surfaceView = (SurfaceView) effectView;
                // A translucent EGL buffer is not enough for SurfaceView: without
                // an overlay Z-order Android punches an opaque hole through the
                // accessibility window and hides SystemUI behind the Samsung GL
                // surface even when the patched fragment shader writes alpha 0.
                surfaceView.setZOrderOnTop(true);
                surfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            }
            handleTouchEvent = cls.getMethod("handleTouchEvent", View.class, MotionEvent.class);
            handleUnlock = cls.getMethod("handleUnlock", View.class, MotionEvent.class);
            show = cls.getMethod("show");
            screenTurnedOn = cls.getMethod("screenTurnedOn");
            showUnlockAffordance = cls.getMethod(
                    "showUnlockAffordance", long.class, Rect.class);
            reset = cls.getMethod("reset");
            cleanUp = cls.getMethod("cleanUp");
            onResume = cls.getMethod("onResume");
            onPause = cls.getMethod("onPause");
            Field rendererField = cls.getDeclaredField("mRenderer");
            rendererField.setAccessible(true);
            nativeRenderer = rendererField.get(effect);
            setTexture = nativeRenderer.getClass().getMethod("setTexture", Bitmap.class);
            Class<?> jniClass = Class.forName(
                    "com.android.internal.policy.impl.keyguard.sec.JniWaterRippleRender");
            transferBGBitmap = jniClass.getMethod("transferBGBitmap", Bitmap.class);
            onLoadBGTextures = jniClass.getMethod("onLoadBGTextures");
            addView(effectView, new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            invokeNoArgs(show);
            Log.i(TAG, "original S3 renderer loaded class=" + EFFECT_CLASS);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "original S3 renderer load failed", t);
            removeAllViews();
            effect = null;
            effectView = null;
            return false;
        }
    }

    private void sendTouch(int action, float x, float y) {
        MotionEvent event = obtainEvent(action, x, y);
        if (event == null) {
            return;
        }
        try {
            invokeTouch(event);
        } finally {
            event.recycle();
        }
    }

    private MotionEvent obtainEvent(int action, float x, float y) {
        if (!ensureEffect()) {
            return null;
        }
        long now = SystemClock.uptimeMillis();
        long down = gestureDownTime > 0L ? gestureDownTime : now;
        return MotionEvent.obtain(down, now, action, x, y, 0);
    }

    private void invokeTouch(MotionEvent event) {
        if (effect == null || handleTouchEvent == null) {
            return;
        }
        try {
            handleTouchEvent.invoke(effect, this, event);
        } catch (Throwable t) {
            Log.e(TAG, "original S3 touch failed action=" + event.getActionMasked(), t);
        }
    }

    private void invokeNoArgs(Method method) {
        if (effect == null || method == null) {
            return;
        }
        try {
            method.invoke(effect);
        } catch (Throwable t) {
            Log.d(TAG, "original S3 method ignored " + method.getName(), t);
        }
    }

    /** Keeps Samsung's frame-stepped fluid simulation at its original ~60 Hz cadence. */
    public static void paceOriginalFrame() {
        long now = System.nanoTime();
        long previous = lastOriginalFrameNs;
        if (previous > 0L) {
            long remaining = ORIGINAL_FRAME_INTERVAL_NS - (now - previous);
            // A 120 Hz panel arrives roughly 8.3 ms early. Avoid sub-millisecond
            // sleeps on an already-60 Hz source, where they could miss the next vsync.
            if (remaining > 1_000_000L) {
                LockSupport.parkNanos(remaining);
            }
        }
        lastOriginalFrameNs = System.nanoTime();
    }
}
