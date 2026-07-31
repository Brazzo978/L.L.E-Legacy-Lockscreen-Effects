package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Hosts Samsung's original DEX-only Tab S Blind renderer (effect id 10).
 *
 * The Samsung implementation redraws the complete background as 25/40 bitmap
 * strips. Keep that layer fully transparent while idle so a cached lockscreen
 * frame can never remain parked over the live keyguard.
 */
public final class BlindDexEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "ChargingTabSBlind";
    private static final int SAMSUNG_EFFECT_ID = 10;
    private static final int CMD_SET_BITMAPS = 0;
    private static final int CMD_LOCK_AFFORDANCE = 1;
    private static final int CMD_UNLOCK = 2;
    private static final int CMD_LIFECYCLE = 3;
    private static final long RELEASE_ANIMATION_MS = 1_000L;
    private static final long HIDE_SAFETY_MS = 50L;
    private static final long AFFORDANCE_DOWN_MS = 100L;
    private static final float NEUTRAL_STRIP_SCALE_EPSILON = 0.0001f;

    private final SoundPool soundPool;
    private final int touchSound;
    private final int unlockSound;
    private final UnlockEffectReadinessCoordinator readiness =
            new UnlockEffectReadinessCoordinator(this, "Blind");
    private final ArrayList<View> blindStripViews = new ArrayList<View>();
    private final ViewTreeObserver.OnPreDrawListener stripAlphaMaskListener =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (stripMaskActive && !destroyed) {
                        updateStripAlphaMask();
                    }
                    return true;
                }
            };
    private final Runnable showLayerRunnable = new Runnable() {
        @Override
        public void run() {
            setSamsungLayerAlpha(1f);
        }
    };
    private final Runnable hideLayerRunnable = new Runnable() {
        @Override
        public void run() {
            if (gestureActive || destroyed) {
                return;
            }
            clearSamsungEffect();
            setSamsungLayerAlpha(0f);
        }
    };

    private Object effectView;
    private View effectViewAsView;
    private Method handleTouchEvent;
    private Method handleCustomEvent;
    private Method clearScreen;
    private Method removeEffect;
    private Bitmap stockLightBitmap;
    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private Bitmap lastSentBackgroundBitmap;
    private String backgroundSource = "none";
    private String lastSentBackgroundSource = "";
    private boolean externalBackground;
    private boolean initialized;
    private boolean ready;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean stripMaskActive;
    private long downTime;
    private float lastX;
    private float lastY;

    public BlindDexEffectView(Context context) {
        super(context);
        long startedAt = SystemClock.uptimeMillis();
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        touchSound = soundPool.load(context, R.raw.blind_touch, 1);
        unlockSound = soundPool.load(context, R.raw.blind_unlock, 1);

        try {
            createSamsungEffect(context);
            ready = true;
            Log.i(TAG, "Tab S Blind DEX renderer loaded elapsedMs="
                    + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            ready = false;
            readiness.constructionFailed(t.getClass().getSimpleName());
            Log.e(TAG, "Tab S Blind DEX renderer unavailable", t);
        }
    }

    public boolean isReady() {
        return ready && initialized && !destroyed;
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "Tab S Blind";
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
        if (!canRender()) {
            return;
        }
        removeCallbacks(showLayerRunnable);
        removeCallbacks(hideLayerRunnable);
        sendBackgroundBitmap();
        setSamsungLayerAlpha(1f);
        downTime = SystemClock.uptimeMillis();
        gestureActive = true;
        lastX = screenX;
        lastY = screenY;
        play(touchSound);
        forwardTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
        Log.i(TAG, "Blind begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY)
                + " bg=" + backgroundSource);
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
        forwardTouch(MotionEvent.ACTION_UP, lastX, lastY);
        if (completed) {
            sendUnlockCommand();
            play(unlockSound);
        }
        scheduleLayerHide(RELEASE_ANIMATION_MS + HIDE_SAFETY_MS);
        Log.i(TAG, "Blind finish completed=" + completed
                + " x=" + Math.round(lastX)
                + " y=" + Math.round(lastY));
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        forwardTouch(MotionEvent.ACTION_CANCEL, lastX, lastY);
        scheduleLayerHide(RELEASE_ANIMATION_MS + HIDE_SAFETY_MS);
        Log.i(TAG, "Blind cancel");
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        downTime = 0L;
        removeCallbacks(showLayerRunnable);
        removeCallbacks(hideLayerRunnable);
        clearSamsungEffect();
        setSamsungLayerAlpha(0f);
    }

    @Override
    public void warmUp() {
        if (!canRender()) {
            return;
        }
        sendBackgroundBitmap();
        setSamsungLayerAlpha(0f);
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (!canRender() || handleCustomEvent == null) {
            return;
        }
        sendBackgroundBitmap();
        removeCallbacks(showLayerRunnable);
        removeCallbacks(hideLayerRunnable);
        setSamsungLayerAlpha(0f);
        long delayMs = Math.max(0L, startDelayMs);
        postDelayed(showLayerRunnable, delayMs);
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("StartDelay", Long.valueOf(delayMs));
            params.put("Rect", safeRect(screenRect));
            handleCustomEvent.invoke(effectView, CMD_LOCK_AFFORDANCE, params);
            postDelayed(hideLayerRunnable,
                    delayMs + AFFORDANCE_DOWN_MS + RELEASE_ANIMATION_MS + HIDE_SAFETY_MS);
            Log.i(TAG, "Blind affordance queued delayMs=" + delayMs);
        } catch (Throwable t) {
            removeCallbacks(showLayerRunnable);
            removeCallbacks(hideLayerRunnable);
            setSamsungLayerAlpha(0f);
            Log.d(TAG, "Blind affordance command ignored", t);
        }
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalBackground
                && backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == Math.max(1, getRenderWidth())
                && backgroundBitmap.getHeight() == Math.max(1, getRenderHeight());
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        replaceBackgroundBitmap(source, sourceName == null ? "external" : sourceName);
        sendBackgroundBitmap();
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        if (destroyed) {
            return;
        }
        externalBackground = false;
        backgroundSource = "none";
        invalidateSentBackground();
        releaseBackgroundBitmap();
        sendTransparentBackground();
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
        ready = false;
        sendLifecycleDestroy();
        if (removeEffect != null && effectView != null) {
            try {
                removeEffect.invoke(effectView);
            } catch (Throwable t) {
                Log.d(TAG, "removeEffect ignored", t);
            }
        }
        soundPool.release();
        releaseBackgroundBitmap();
        if (stockLightBitmap != null && !stockLightBitmap.isRecycled()) {
            stockLightBitmap.recycle();
        }
        stockLightBitmap = null;
        removeStripAlphaMaskListener();
        blindStripViews.clear();
        removeAllViews();
        effectView = null;
        effectViewAsView = null;
        handleTouchEvent = null;
        handleCustomEvent = null;
        clearScreen = null;
        removeEffect = null;
        initialized = false;
        externalBackground = false;
        invalidateSentBackground();
        readiness.destroyed();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!canRender()) {
            readiness.rendererUnavailable("Samsung Blind EffectView is not ready");
            return;
        }
        readiness.attachVendor(effectViewAsView, new Runnable() {
            @Override
            public void run() {
                warmUp();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        readiness.detached("hardware Blind layer detached");
        super.onDetachedFromWindow();
    }

    private void createSamsungEffect(Context context) throws Exception {
        Class<?> effectViewClass =
                Class.forName("com.samsung.android.visualeffect.EffectView");
        Class<?> dataClass =
                Class.forName("com.samsung.android.visualeffect.EffectDataObj");
        effectView = effectViewClass.getConstructor(Context.class).newInstance(context);
        effectViewAsView = (View) effectView;
        effectViewAsView.setBackgroundColor(Color.TRANSPARENT);
        effectViewAsView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        effectViewAsView.setAlpha(0f);
        addView(effectViewAsView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        Method setEffect = effectViewClass.getMethod("setEffect", int.class);
        Method init = effectViewClass.getMethod("init", dataClass);
        handleTouchEvent = effectViewClass.getMethod(
                "handleTouchEvent", MotionEvent.class, View.class);
        handleCustomEvent = effectViewClass.getMethod(
                "handleCustomEvent", int.class, HashMap.class);
        clearScreen = effectViewClass.getMethod("clearScreen");
        removeEffect = effectViewClass.getMethod("removeEffect");

        Object data = dataClass.getConstructor().newInstance();
        dataClass.getMethod("setEffect", int.class).invoke(data, SAMSUNG_EFFECT_ID);
        stockLightBitmap = decodeStockLight();
        Bitmap transparent = createTransparentBitmap(getRenderWidth(), getRenderHeight());
        try {
            setEffect.invoke(effectView, SAMSUNG_EFFECT_ID);
            HashMap<String, Object> timing = new HashMap<String, Object>();
            timing.put("unlockDelay", Long.valueOf(0L));
            handleCustomEvent.invoke(effectView, CMD_UNLOCK, timing);
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("background", transparent);
            params.put("light", stockLightBitmap);
            handleCustomEvent.invoke(effectView, CMD_SET_BITMAPS, params);
            init.invoke(effectView, data);
            initialized = true;
            installStripAlphaMask();
        } finally {
            if (!transparent.isRecycled()) {
                transparent.recycle();
            }
        }
        setSamsungLayerAlpha(0f);
    }

    private Bitmap decodeStockLight() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.keyguard_blind_light, options);
        if (bitmap == null) {
            throw new IllegalStateException("Tab S Blind light asset unavailable");
        }
        bitmap.prepareToDraw();
        return bitmap;
    }

    private void sendBackgroundBitmap() {
        if (!canRender() || backgroundBitmap == null || backgroundBitmap.isRecycled()) {
            return;
        }
        if (backgroundBitmap == lastSentBackgroundBitmap
                && backgroundSource.equals(lastSentBackgroundSource)) {
            return;
        }
        Bitmap dexInput = null;
        boolean commandCompleted = false;
        try {
            // BlindEffect assumes ownership of an exact-size portrait input and may recycle it
            // from setBlind()/backgroundImageUpdate(). Never expose our retained/shared master.
            dexInput = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (dexInput == null) {
                throw new IllegalStateException("Unable to copy Blind background for DEX ownership");
            }
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("background", dexInput);
            handleCustomEvent.invoke(effectView, CMD_SET_BITMAPS, params);
            commandCompleted = true;
            lastSentBackgroundBitmap = backgroundBitmap;
            lastSentBackgroundSource = backgroundSource;
            Log.i(TAG, "Blind background sent source=" + backgroundSource
                    + " size=" + backgroundBitmap.getWidth()
                    + "x" + backgroundBitmap.getHeight());
        } catch (Throwable t) {
            Log.e(TAG, "Blind background command failed", t);
        } finally {
            // Exact-size inputs are normally recycled by BlindEffect itself. If display/view
            // dimensions differed, the DEX made scaled copies and left our transfer bitmap alive.
            if (commandCompleted && dexInput != null && !dexInput.isRecycled()) {
                dexInput.recycle();
            }
        }
    }

    private void sendTransparentBackground() {
        if (!canRender()) {
            return;
        }
        Bitmap transparent = createTransparentBitmap(getRenderWidth(), getRenderHeight());
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("background", transparent);
            handleCustomEvent.invoke(effectView, CMD_SET_BITMAPS, params);
            Log.i(TAG, "Blind transparent fallback restored");
        } catch (Throwable t) {
            Log.e(TAG, "Blind transparent fallback failed", t);
        } finally {
            if (!transparent.isRecycled()) {
                transparent.recycle();
            }
        }
    }

    private void replaceBackgroundBitmap(Bitmap source, String sourceName) {
        int width = Math.max(1, getRenderWidth());
        int height = Math.max(1, getRenderHeight());
        boolean borrow = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, width, height);
        Bitmap next = borrow ? source : createCenterCropBitmap(source, width, height);
        next.prepareToDraw();
        releaseBackgroundBitmap();
        backgroundBitmap = next;
        ownsBackgroundBitmap = !borrow;
        backgroundSource = sourceName;
        externalBackground = true;
        invalidateSentBackground();
    }

    private Bitmap createCenterCropBitmap(Bitmap source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float srcRatio = source.getWidth() / (float) source.getHeight();
        float dstRatio = width / (float) height;
        Rect src;
        if (srcRatio > dstRatio) {
            int srcWidth = Math.max(1, Math.round(source.getHeight() * dstRatio));
            int left = Math.max(0, (source.getWidth() - srcWidth) / 2);
            src = new Rect(left, 0,
                    Math.min(source.getWidth(), left + srcWidth), source.getHeight());
        } else {
            int srcHeight = Math.max(1, Math.round(source.getWidth() / dstRatio));
            int top = Math.max(0, (source.getHeight() - srcHeight) / 2);
            src = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + srcHeight));
        }
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(source, src, new Rect(0, 0, width, height), paint);
        return out;
    }

    private Bitmap createTransparentBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(
                Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);
        return bitmap;
    }

    private void releaseBackgroundBitmap() {
        if (ownsBackgroundBitmap
                && backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = null;
        ownsBackgroundBitmap = false;
    }

    private void invalidateSentBackground() {
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSource = "";
    }

    private void forwardTouch(int action, float screenX, float screenY) {
        if (!canRender() || handleTouchEvent == null) {
            return;
        }
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                downTime == 0L ? eventTime : downTime,
                eventTime,
                action,
                screenX,
                screenY,
                0);
        try {
            handleTouchEvent.invoke(effectView, event, this);
        } catch (Throwable t) {
            Log.e(TAG, "Blind touch forwarding failed", t);
        } finally {
            event.recycle();
        }
    }

    private void sendUnlockCommand() {
        if (!canRender()) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("unlock", Boolean.TRUE);
            handleCustomEvent.invoke(effectView, CMD_UNLOCK, params);
        } catch (Throwable t) {
            Log.d(TAG, "Blind unlock command ignored", t);
        }
    }

    private void sendLifecycleDestroy() {
        if (!initialized || handleCustomEvent == null || effectView == null) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("destroy", Boolean.TRUE);
            handleCustomEvent.invoke(effectView, CMD_LIFECYCLE, params);
        } catch (Throwable t) {
            Log.d(TAG, "Blind destroy command ignored", t);
        }
    }

    private void clearSamsungEffect() {
        if (!initialized || clearScreen == null || effectView == null) {
            return;
        }
        try {
            clearScreen.invoke(effectView);
        } catch (Throwable t) {
            Log.d(TAG, "Blind clearScreen ignored", t);
        }
    }

    private void scheduleLayerHide(long delayMs) {
        removeCallbacks(hideLayerRunnable);
        postDelayed(hideLayerRunnable, Math.max(0L, delayMs));
    }

    private void setSamsungLayerAlpha(float alpha) {
        if (effectViewAsView != null) {
            effectViewAsView.setAlpha(alpha);
        }
        stripMaskActive = alpha > 0f;
        if (stripMaskActive) {
            // The stock renderer assumes the same wallpaper is already visible below it.
            // LLE supplies a captured/imported frame instead, so neutral strips must remain
            // transparent or their full-screen copy (including lock-screen UI) flashes on DOWN.
            updateStripAlphaMask();
        } else {
            hideBlindStrips();
        }
    }

    private void installStripAlphaMask() {
        blindStripViews.clear();
        collectBlindStrips(effectViewAsView);
        hideBlindStrips();
        ViewTreeObserver observer = getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnPreDrawListener(stripAlphaMaskListener);
        }
        Log.i(TAG, "Blind interaction-only strip mask installed strips="
                + blindStripViews.size());
    }

    private void removeStripAlphaMaskListener() {
        ViewTreeObserver observer = getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(stripAlphaMaskListener);
        }
    }

    private void collectBlindStrips(View view) {
        if (view == null) {
            return;
        }
        if ("com.samsung.android.visualeffect.lock.blind.Blind"
                .equals(view.getClass().getName())) {
            blindStripViews.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectBlindStrips(group.getChildAt(i));
            }
        }
    }

    private void hideBlindStrips() {
        for (int i = 0; i < blindStripViews.size(); i++) {
            blindStripViews.get(i).setAlpha(0f);
        }
    }

    private void updateStripAlphaMask() {
        for (int i = 0; i < blindStripViews.size(); i++) {
            View strip = blindStripViews.get(i);
            boolean modified = Math.abs(strip.getScaleX() - 1f)
                    > NEUTRAL_STRIP_SCALE_EPSILON
                    || Math.abs(strip.getScaleY() - 1f)
                    > NEUTRAL_STRIP_SCALE_EPSILON;
            strip.setAlpha(modified ? 1f : 0f);
        }
    }

    private Rect safeRect(Rect rect) {
        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            return new Rect(rect);
        }
        return new Rect(0, 0, Math.max(1, getRenderWidth()), Math.max(1, getRenderHeight()));
    }

    private int getRenderWidth() {
        int width = getWidth();
        return width > 0 ? width : Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        int height = getHeight();
        return height > 0 ? height : Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    private boolean canRender() {
        return !destroyed && ready && initialized && effectView != null;
    }

    private void play(int soundId) {
        if (soundId != 0 && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(soundId, 1f, 1f, 0, 0, 1f);
        }
    }
}
