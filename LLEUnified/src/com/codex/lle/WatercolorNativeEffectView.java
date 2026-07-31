package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.locks.LockSupport;

/** Hosts Samsung's WaterColor Java shell with LLE64's reconstructed ARM64 engine. */
public final class WatercolorNativeEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "ChargingWaterNative";
    private static final int EFFECT_ID = 5;
    private static final int CMD_SET_BACKGROUND = 0;
    private static final int CMD_AFFORDANCE = 1;
    private static final int CMD_UNLOCK = 2;
    private static final long MIN_AFFORDANCE_DELAY_MS = 1_000L;
    private static final long ORIGINAL_FRAME_INTERVAL_NS = 16_666_667L;
    private static final long LONG_PRESS_SOUND_MS = 411L;
    private static volatile long lastOriginalFrameNs;

    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;
    private final UnlockEffectReadinessCoordinator readiness =
            new UnlockEffectReadinessCoordinator(this, "Watercolor");

    private Object effectView;
    private View effectViewAsView;
    private Method handleTouchEvent;
    private Method handleCustomEvent;
    private Method clearScreen;
    private Method removeEffect;
    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private Bitmap lastSentBackgroundBitmap;
    private Object lastSentBackgroundSurface;
    private String backgroundSource = "none";
    private String lastSentBackgroundSource = "";
    private boolean ready;
    private boolean destroyed;
    private boolean gestureActive;
    private long downTime;
    private boolean longPressSoundPlayed;
    private float lastX;
    private float lastY;

    public WatercolorNativeEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        tapSound = soundPool.load(context, R.raw.ve_watercolour_tap, 1);
        unlockSound = soundPool.load(context, R.raw.ve_watercolour_unlock, 1);

        long startedAt = SystemClock.uptimeMillis();
        try {
            createSamsungEffect(context);
            ready = true;
            Log.i(TAG, "original Watercolor renderer loaded elapsedMs="
                    + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            ready = false;
            readiness.constructionFailed(t.getClass().getSimpleName());
            Log.e(TAG, "original Watercolor renderer unavailable", t);
        }
    }

    public boolean isReady() {
        return ready && !destroyed && effectView != null;
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "N3 Watercolor";
    }

    @Override
    public int getReadinessState() {
        return readiness.getState();
    }

    @Override
    public String getReadinessDetail() {
        return readiness.getDetail();
    }

    @Override
    public void setReadinessListener(ReadinessListener listener) {
        readiness.setListener(listener);
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!isReady() || !hasBackgroundSourceBitmap()) {
            Log.d(TAG, "touch ignored while background is unavailable");
            return;
        }
        sendBackgroundBitmap();
        downTime = SystemClock.uptimeMillis();
        longPressSoundPlayed = false;
        lastX = screenX;
        lastY = screenY;
        gestureActive = true;
        play(tapSound);
        forwardTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
        Log.i(TAG, "watercolor begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY) + " bg=" + backgroundSource);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        lastX = screenX;
        lastY = screenY;
        forwardTouch(MotionEvent.ACTION_MOVE, screenX, screenY);
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        playReleaseTapIfNeeded();
        forwardTouch(MotionEvent.ACTION_UP, lastX, lastY);
        downTime = 0L;
        if (completed) {
            sendCommand(CMD_UNLOCK, new HashMap<String, Object>());
            play(unlockSound);
        }
        Log.i(TAG, "watercolor finish completed=" + completed);
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        playReleaseTapIfNeeded();
        forwardTouch(MotionEvent.ACTION_CANCEL, lastX, lastY);
        downTime = 0L;
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        longPressSoundPlayed = false;
        if (!isReady() || clearScreen == null) {
            return;
        }
        try {
            clearScreen.invoke(effectView);
        } catch (Throwable t) {
            Log.d(TAG, "clearScreen ignored", t);
        }
    }

    @Override
    public void warmUp() {
        if (isReady() && hasBackgroundSourceBitmap()) {
            makeTransparent(effectViewAsView);
            sendBackgroundBitmap();
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (!isReady() || !hasBackgroundSourceBitmap()) {
            return;
        }
        sendBackgroundBitmap();
        Rect rect = screenRect == null || screenRect.isEmpty()
                ? new Rect(0, 0, getRenderWidth(), getRenderHeight())
                : new Rect(screenRect);
        HashMap<String, Object> params = new HashMap<String, Object>();
        long effectiveDelayMs = Math.max(MIN_AFFORDANCE_DELAY_MS, startDelayMs);
        params.put("StartDelay", Long.valueOf(effectiveDelayMs));
        params.put("Rect", rect);
        sendCommand(CMD_AFFORDANCE, params);
        Log.i(TAG, "watercolor affordance queued delayMs=" + effectiveDelayMs);
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return backgroundBitmap != null && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == getRenderWidth()
                && backgroundBitmap.getHeight() == getRenderHeight();
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
        releaseBackgroundBitmap();
        backgroundBitmap = next;
        ownsBackgroundBitmap = !borrow;
        backgroundSource = sourceName == null ? "external" : sourceName;
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSurface = null;
        lastSentBackgroundSource = "";
        sendBackgroundBitmap();
        Log.i(TAG, "watercolor background ready source=" + backgroundSource
                + " size=" + next.getWidth() + "x" + next.getHeight());
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSurface = null;
        lastSentBackgroundSource = "";
        releaseBackgroundBitmap();
        backgroundSource = "none";
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
        resetEffect();
        destroyed = true;
        if (removeEffect != null && effectView != null) {
            try {
                removeEffect.invoke(effectView);
            } catch (Throwable t) {
                Log.d(TAG, "removeEffect ignored", t);
            }
        }
        soundPool.release();
        releaseBackgroundBitmap();
        readiness.destroyed();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Samsung recreates the TextureView/EGL context when this parked renderer is
        // detached and attached again. A bitmap uploaded to the previous context is
        // no longer a valid GL texture even though the Java Bitmap instance is unchanged.
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSurface = null;
        lastSentBackgroundSource = "";
        if (!isReady()) {
            readiness.rendererUnavailable("Samsung Watercolor EffectView is not ready");
            return;
        }
        readiness.attachVendor(effectViewAsView, new Runnable() {
            @Override
            public void run() {
                makeTransparent(effectViewAsView);
                warmUp();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        readiness.detached("Watercolor TextureView detached");
        super.onDetachedFromWindow();
    }

    private void createSamsungEffect(Context context) throws Exception {
        Class<?> effectViewClass = Class.forName("com.samsung.android.visualeffect.EffectView");
        Class<?> dataClass = Class.forName("com.samsung.android.visualeffect.EffectDataObj");
        effectView = effectViewClass.getConstructor(Context.class).newInstance(context);
        effectViewAsView = (View) effectView;
        addView(effectViewAsView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        handleTouchEvent = effectViewClass.getMethod(
                "handleTouchEvent", MotionEvent.class, View.class);
        handleCustomEvent = effectViewClass.getMethod(
                "handleCustomEvent", int.class, HashMap.class);
        clearScreen = effectViewClass.getMethod("clearScreen");
        removeEffect = optionalMethod(effectViewClass, "removeEffect");

        Object data = dataClass.getConstructor().newInstance();
        dataClass.getMethod("setEffect", int.class).invoke(data, EFFECT_ID);
        effectViewClass.getMethod("setEffect", int.class).invoke(effectView, EFFECT_ID);
        makeTransparent(effectViewAsView);
        effectViewClass.getMethod("init", dataClass).invoke(effectView, data);
        makeTransparent(effectViewAsView);
    }

    private Method optionalMethod(Class<?> owner, String name, Class<?>... args) {
        try {
            return owner.getMethod(name, args);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /** Keeps Samsung's frame-stepped Watercolor simulation at its stock 60 Hz cadence. */
    public static void paceOriginalFrame() {
        long now = System.nanoTime();
        long previous = lastOriginalFrameNs;
        if (previous > 0L) {
            long remaining = ORIGINAL_FRAME_INTERVAL_NS - (now - previous);
            if (remaining > 1_000_000L) {
                LockSupport.parkNanos(remaining);
            }
        }
        lastOriginalFrameNs = System.nanoTime();
    }

    private void sendBackgroundBitmap() {
        if (!isReady() || !hasBackgroundSourceBitmap()) {
            return;
        }
        Object currentSurface = findSurfaceToken(effectViewAsView);
        if (currentSurface != null
                && currentSurface == lastSentBackgroundSurface
                && backgroundBitmap == lastSentBackgroundBitmap
                && backgroundSource.equals(lastSentBackgroundSource)) {
            return;
        }
        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("Bitmap", backgroundBitmap);
        sendCommand(CMD_SET_BACKGROUND, params);
        lastSentBackgroundBitmap = backgroundBitmap;
        lastSentBackgroundSurface = currentSurface;
        lastSentBackgroundSource = backgroundSource;
    }

    private Object findSurfaceToken(View view) {
        if (view == null) {
            return null;
        }
        if (view instanceof TextureView) {
            return ((TextureView) view).getSurfaceTexture();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Object token = findSurfaceToken(group.getChildAt(i));
                if (token != null) {
                    return token;
                }
            }
        }
        return null;
    }

    private void sendCommand(int command, HashMap<String, Object> params) {
        if (!isReady() || handleCustomEvent == null) {
            return;
        }
        try {
            handleCustomEvent.invoke(effectView, command, params);
        } catch (Throwable t) {
            Log.e(TAG, "custom command failed cmd=" + command, t);
        }
    }

    private void forwardTouch(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                downTime == 0L ? now : downTime, now, action, x, y, 0);
        try {
            handleTouchEvent.invoke(effectView, event, effectViewAsView);
        } catch (Throwable t) {
            Log.e(TAG, "touch forwarding failed action=" + action, t);
        } finally {
            event.recycle();
        }
    }

    private void playReleaseTapIfNeeded() {
        if (!longPressSoundPlayed
                && downTime > 0L
                && SystemClock.uptimeMillis() - downTime > LONG_PRESS_SOUND_MS) {
            longPressSoundPlayed = true;
            play(tapSound);
        }
    }

    private void makeTransparent(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof TextureView) {
            ((TextureView) view).setOpaque(false);
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                makeTransparent(group.getChildAt(i));
            }
        }
    }

    private Bitmap centerCrop(Bitmap source, int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float srcRatio = source.getWidth() / (float) source.getHeight();
        float dstRatio = width / (float) height;
        Rect src;
        if (srcRatio > dstRatio) {
            int sw = Math.max(1, Math.round(source.getHeight() * dstRatio));
            int left = Math.max(0, (source.getWidth() - sw) / 2);
            src = new Rect(left, 0, Math.min(source.getWidth(), left + sw), source.getHeight());
        } else {
            int sh = Math.max(1, Math.round(source.getWidth() / dstRatio));
            int top = Math.max(0, (source.getHeight() - sh) / 2);
            src = new Rect(0, top, source.getWidth(), Math.min(source.getHeight(), top + sh));
        }
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        new Canvas(out).drawBitmap(source, src, new Rect(0, 0, width, height), paint);
        return out;
    }

    private int getRenderWidth() {
        return Math.max(1, getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        return Math.max(1, getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels);
    }

    private void play(int soundId) {
        if (!destroyed && soundId != 0
                && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void releaseBackgroundBitmap() {
        if (ownsBackgroundBitmap) {
            recycle(backgroundBitmap);
        }
        backgroundBitmap = null;
        ownsBackgroundBitmap = false;
    }
}
