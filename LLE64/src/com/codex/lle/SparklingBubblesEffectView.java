package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

public class SparklingBubblesEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer {
    private static final String TAG = "ChargingSparkling";
    private static final int SAMSUNG_EFFECT_ID = 0x0f;
    private static final int CMD_SET_BACKGROUND = 0;
    private static final int CMD_LOCK_AFFORDANCE = 1;
    private static final int CMD_UNLOCK = 2;
    private static final int CMD_SCREEN_OFF = 3;
    private static final int CMD_SCREEN_ON = 4;
    private static final int CMD_CUSTOM = 99;
    private static final int BACKGROUND_MODE_NORMAL = 0;
    private static final String CUSTOM_EVENT_FORCE_DIRTY = "ForceDirty";
    private static final long DRAG_SOUND_MIN_TIME_MS = 1100L;
    private static final float DRAG_SOUND_DISTANCE_PX = 120f;
    private static final long DRAG_SOUND_FADE_FRAME_MS = 10L;
    private static final float DRAG_SOUND_RELEASE_FADE_STEP = 0.039f;
    private static final float DRAG_SOUND_UNLOCK_FADE_STEP = 0.059f;

    private final Context context;
    private final AudioManager audioManager;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int dragSound;
    private final int lockSound;
    private final int unlockSound;

    private Object effectView;
    private View effectViewAsView;
    private Method handleTouchEvent;
    private Method handleCustomEvent;
    private Method clearScreen;
    private Method removeEffect;
    private Bitmap blurMaskBitmap;
    private Bitmap backgroundBitmap;
    private Bitmap lastSentBackgroundBitmap;
    private String backgroundSource = "none";
    private String lastSentBackgroundSource = "";
    private boolean externalColorSource;
    private boolean ready;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean nativeScreenOn;
    private long downTime;
    private float lastX;
    private float lastY;
    private float lastDragSoundX;
    private float lastDragSoundY;
    private int dragStreamId;
    private float dragSoundVolume = 1f;
    private float dragSoundFadeStep = DRAG_SOUND_RELEASE_FADE_STEP;
    private boolean dragSoundFading;
    private final Runnable dragSoundFadeRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !dragSoundFading || dragStreamId == 0) {
                return;
            }
            dragSoundVolume = Math.max(0f, dragSoundVolume);
            soundPool.setVolume(dragStreamId, dragSoundVolume, dragSoundVolume);
            if (dragSoundVolume > 0f) {
                dragSoundVolume -= dragSoundFadeStep;
                postDelayed(this, DRAG_SOUND_FADE_FRAME_MS);
                return;
            }
            soundPool.stop(dragStreamId);
            dragStreamId = 0;
            dragSoundFading = false;
            Log.d(TAG, "sparkling bubbles drag sound fade complete");
        }
    };
    private final Runnable forceDirtyRunnable = new Runnable() {
        @Override
        public void run() {
            sendForceDirtyCommand();
        }
    };

    public SparklingBubblesEffectView(Context context) {
        super(context);
        this.context = context.getApplicationContext();
        audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        long startedAt = SystemClock.uptimeMillis();
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tapSound = soundPool.load(context, R.raw.ve_sparklingbubbles_tap, 1);
        dragSound = soundPool.load(context, R.raw.ve_sparklingbubbles_drag, 1);
        lockSound = soundPool.load(context, R.raw.ve_sparklingbubbles_lock, 1);
        unlockSound = soundPool.load(context, R.raw.ve_sparklingbubbles_unlock, 1);

        try {
            createSamsungEffect(context);
            ready = true;
            Log.i(TAG, "Note5 sparkling bubbles native renderer loaded elapsedMs="
                    + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            ready = false;
            cleanupSamsungState();
            Log.e(TAG, "Note5 sparkling bubbles native renderer unavailable", t);
        }
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "N5 Sparkling Bubbles";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRender()) {
            return;
        }
        removeCallbacks(forceDirtyRunnable);
        sendBackgroundBitmap();
        sendScreenTurnedOnCommand();
        downTime = SystemClock.uptimeMillis();
        gestureActive = true;
        lastX = screenX;
        lastY = screenY;
        lastDragSoundX = screenX;
        lastDragSoundY = screenY;
        stopDragSoundImmediately();
        play(tapSound);
        forwardTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
        Log.i(TAG, "sparkling bubbles begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY)
                + " bg=" + backgroundSource);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        maybeStartDragSound(screenX, screenY);
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
        startDragSoundFade(DRAG_SOUND_RELEASE_FADE_STEP);
        if (completed) {
            sendUnlockCommand();
            dragSoundFadeStep = DRAG_SOUND_UNLOCK_FADE_STEP;
            play(unlockSound);
        }
        Log.i(TAG, "sparkling bubbles finish completed=" + completed
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
        startDragSoundFade(DRAG_SOUND_RELEASE_FADE_STEP);
        Log.i(TAG, "sparkling bubbles cancel");
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        stopDragSoundImmediately();
        if (!ready || clearScreen == null || effectView == null) {
            return;
        }
        try {
            clearScreen.invoke(effectView);
            scheduleForceDirty(120L);
        } catch (Throwable t) {
            Log.d(TAG, "clearScreen ignored", t);
        }
    }

    @Override
    public void warmUp() {
        if (destroyed || !ready) {
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        sendBackgroundBitmap();
        long elapsedMs = SystemClock.uptimeMillis() - startedAt;
        if (elapsedMs >= 4L) {
            Log.i(TAG, "sparkling bubbles warmed elapsedMs=" + elapsedMs);
        }
    }

    void parkForReuse() {
        resetEffect();
        sendScreenTurnedOffCommand();
        scheduleForceDirty(80L);
    }

    void resumeForReuse() {
        if (nativeScreenOn) {
            return;
        }
        removeCallbacks(forceDirtyRunnable);
        sendBackgroundBitmap();
        sendScreenTurnedOnCommand();
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (!canRender()) {
            return;
        }
        removeCallbacks(forceDirtyRunnable);
        sendBackgroundBitmap();
        Rect rect = safeRect(screenRect);
        sendScreenTurnedOnCommand();
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("StartDelay", Long.valueOf(Math.max(0L, startDelayMs)));
            params.put("Rect", rect);
            handleCustomEvent.invoke(effectView, CMD_LOCK_AFFORDANCE, params);
            Log.i(TAG, "sparkling bubbles affordance sent delayMs="
                    + Math.max(0L, startDelayMs)
                    + " rect=" + rect.left + "," + rect.top + ","
                    + rect.right + "," + rect.bottom);
        } catch (Throwable t) {
            Log.d(TAG, "affordance command ignored", t);
        }
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalColorSource
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
        externalColorSource = false;
        backgroundSource = "none";
        invalidateSentBackground();
        recycle(backgroundBitmap);
        backgroundBitmap = null;
        if (!destroyed) {
            sendBackgroundBitmap();
        }
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        Log.i(TAG, "BEGIN sparkling bubbles destroy");
        Log.i(TAG, "BEGIN sparkling bubbles reset");
        resetEffect();
        Log.i(TAG, "END sparkling bubbles reset");
        removeCallbacks(forceDirtyRunnable);
        removeCallbacks(dragSoundFadeRunnable);
        sendScreenTurnedOffCommand();
        destroyed = true;
        soundPool.release();
        if (removeEffect != null && effectView != null) {
            try {
                Log.i(TAG, "BEGIN sparkling bubbles removeEffect/detach");
                removeEffect.invoke(effectView);
                Log.i(TAG, "END sparkling bubbles removeEffect/detach");
            } catch (Throwable t) {
                Log.d(TAG, "removeEffect ignored", t);
            }
        }
        removeAllViews();
        recycle(backgroundBitmap);
        recycle(blurMaskBitmap);
        backgroundBitmap = null;
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSource = "";
        backgroundSource = "none";
        externalColorSource = false;
        blurMaskBitmap = null;
        cleanupSamsungState();
        Log.i(TAG, "END sparkling bubbles destroy");
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        invalidateSentBackground();
        post(new Runnable() {
            @Override
            public void run() {
                warmUp();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        gestureActive = false;
        startDragSoundFade(DRAG_SOUND_RELEASE_FADE_STEP);
        sendScreenTurnedOffCommand();
        scheduleForceDirty(80L);
        invalidateSentBackground();
        super.onDetachedFromWindow();
    }

    private void createSamsungEffect(Context context) throws Exception {
        Class<?> effectViewClass =
                Class.forName("com.samsung.android.visualeffect.EffectView");
        Class<?> dataClass =
                Class.forName("com.samsung.android.visualeffect.EffectDataObj");
        effectView = effectViewClass.getConstructor(Context.class).newInstance(context);
        effectViewAsView = (View) effectView;
        addView(effectViewAsView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        Method setEffect = effectViewClass.getMethod("setEffect", int.class);
        Method init = effectViewClass.getMethod("init", dataClass);
        handleTouchEvent = effectViewClass.getMethod(
                "handleTouchEvent",
                MotionEvent.class,
                View.class);
        handleCustomEvent = effectViewClass.getMethod(
                "handleCustomEvent",
                int.class,
                HashMap.class);
        clearScreen = effectViewClass.getMethod("clearScreen");
        removeEffect = effectViewClass.getMethod("removeEffect");

        Object data = dataClass.getConstructor().newInstance();
        dataClass.getMethod("setEffect", int.class).invoke(data, SAMSUNG_EFFECT_ID);
        Object sparklingData = getOrCreate(
                dataClass,
                data,
                "sparklingBubblesData",
                "com.samsung.android.visualeffect.lock.data.SparklingBullesData");
        Class<?> sparklingDataClass = sparklingData.getClass();

        blurMaskBitmap = decodeOriginalBitmap(R.drawable.n5_sparkling_bubbles_blur_mask);
        getField(sparklingDataClass, "windowWidth").setInt(
                sparklingData,
                Math.max(1, getRenderWidth()));
        getField(sparklingDataClass, "windowHeight").setInt(
                sparklingData,
                Math.max(1, getRenderHeight()));
        getField(sparklingDataClass, "resBmp").set(sparklingData, blurMaskBitmap);

        setEffect.invoke(effectView, SAMSUNG_EFFECT_ID);
        makeTransparent(effectViewAsView);
        init.invoke(effectView, data);
        post(new Runnable() {
            @Override
            public void run() {
                makeTransparent(effectViewAsView);
            }
        });
    }

    private Object getOrCreate(Class<?> owner, Object target, String fieldName,
            String className) throws Exception {
        Field field = getField(owner, fieldName);
        Object value = field.get(target);
        if (value == null) {
            value = Class.forName(className).getConstructor().newInstance();
            field.set(target, value);
        }
        return value;
    }

    private Field getField(Class<?> owner, String fieldName) throws Exception {
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + fieldName);
    }

    private Bitmap decodeOriginalBitmap(int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId, options);
        if (bitmap == null) {
            throw new IllegalStateException("Missing Sparkling Bubbles blur mask");
        }
        bitmap.prepareToDraw();
        return bitmap;
    }

    private void sendBackgroundBitmap() {
        if (destroyed || !ready || handleCustomEvent == null || effectView == null) {
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        Bitmap bitmap = getBackgroundBitmap();
        if (bitmap == lastSentBackgroundBitmap
                && backgroundSource.equals(lastSentBackgroundSource)) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("Bitmap", bitmap);
            params.put("Mode", Integer.valueOf(BACKGROUND_MODE_NORMAL));
            handleCustomEvent.invoke(effectView, CMD_SET_BACKGROUND, params);
            lastSentBackgroundBitmap = bitmap;
            lastSentBackgroundSource = backgroundSource;
            Log.i(TAG, "sparkling bubbles background sent source=" + backgroundSource
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            Log.d(TAG, "background command ignored", t);
        }
    }

    private void invalidateSentBackground() {
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSource = "";
    }

    private Bitmap getBackgroundBitmap() {
        int width = Math.max(1, getRenderWidth());
        int height = Math.max(1, getRenderHeight());
        if (backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == width
                && backgroundBitmap.getHeight() == height) {
            return backgroundBitmap;
        }
        recycle(backgroundBitmap);
        backgroundBitmap = createWhiteBitmap(width, height);
        backgroundSource = "white_fallback";
        externalColorSource = false;
        backgroundBitmap.prepareToDraw();
        Log.i(TAG, "sparkling bubbles fallback background prepared size="
                + backgroundBitmap.getWidth() + "x" + backgroundBitmap.getHeight());
        return backgroundBitmap;
    }

    private void replaceBackgroundBitmap(Bitmap source, String sourceName) {
        int width = Math.max(1, getRenderWidth());
        int height = Math.max(1, getRenderHeight());
        Bitmap next = createColorMapBitmap(source, width, height);
        next.prepareToDraw();
        recycle(backgroundBitmap);
        backgroundBitmap = next;
        backgroundSource = (sourceName == null ? "external" : sourceName) + "_colour_map";
        externalColorSource = true;
        Log.i(TAG, "sparkling bubbles colour map replaced source=" + backgroundSource
                + " size=" + backgroundBitmap.getWidth()
                + "x" + backgroundBitmap.getHeight());
    }

    private Bitmap createColorMapBitmap(Bitmap source, int width, int height) {
        float srcRatio = source.getWidth() / (float) source.getHeight();
        float dstRatio = width / (float) height;
        Rect src;
        if (srcRatio > dstRatio) {
            int srcWidth = Math.max(1, Math.round(source.getHeight() * dstRatio));
            int left = Math.max(0, (source.getWidth() - srcWidth) / 2);
            src = new Rect(left, 0, Math.min(source.getWidth(), left + srcWidth),
                    source.getHeight());
        } else {
            int srcHeight = Math.max(1, Math.round(source.getWidth() / dstRatio));
            int top = Math.max(0, (source.getHeight() - srcHeight) / 2);
            src = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + srcHeight));
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG
                | Paint.DITHER_FLAG);
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(source, src, new Rect(0, 0, width, height), paint);
        return out;
    }

    private Bitmap createWhiteBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        return bitmap;
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
        long startedAt = SystemClock.uptimeMillis();
        try {
            handleTouchEvent.invoke(effectView, event, effectViewAsView);
        } catch (Throwable t) {
            Log.e(TAG, "touch forwarding failed", t);
        } finally {
            long elapsedMs = SystemClock.uptimeMillis() - startedAt;
            if (elapsedMs > 16L) {
                Log.w(TAG, "sparkling bubbles touch slow action="
                        + actionName(action)
                        + " elapsedMs=" + elapsedMs);
            }
            event.recycle();
        }
    }

    private void sendUnlockCommand() {
        if (!canRender() || handleCustomEvent == null) {
            return;
        }
        try {
            handleCustomEvent.invoke(effectView, CMD_UNLOCK, new HashMap<String, Object>());
        } catch (Throwable t) {
            Log.d(TAG, "unlock command ignored", t);
        }
    }

    private void sendScreenTurnedOnCommand() {
        if (!canRender() || handleCustomEvent == null) {
            return;
        }
        if (nativeScreenOn) {
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        try {
            handleCustomEvent.invoke(effectView, CMD_SCREEN_ON, new HashMap<String, Object>());
            nativeScreenOn = true;
            Log.i(TAG, "sparkling bubbles screen-on sent elapsedMs="
                    + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            Log.d(TAG, "screen-on command ignored", t);
        }
    }

    private void sendScreenTurnedOffCommand() {
        if (!canRender() || handleCustomEvent == null) {
            return;
        }
        if (!nativeScreenOn) {
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        try {
            handleCustomEvent.invoke(effectView, CMD_SCREEN_OFF, new HashMap<String, Object>());
            nativeScreenOn = false;
            Log.i(TAG, "sparkling bubbles screen-off sent elapsedMs="
                    + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            Log.d(TAG, "screen-off command ignored", t);
        }
    }

    private void scheduleForceDirty(long delayMs) {
        if (!canRender()) {
            return;
        }
        removeCallbacks(forceDirtyRunnable);
        postDelayed(forceDirtyRunnable, Math.max(0L, delayMs));
    }

    private void sendForceDirtyCommand() {
        if (!canRender() || handleCustomEvent == null) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("CustomEvent", CUSTOM_EVENT_FORCE_DIRTY);
            handleCustomEvent.invoke(effectView, CMD_CUSTOM, params);
            Log.i(TAG, "sparkling bubbles force-dirty sent");
        } catch (Throwable t) {
            Log.d(TAG, "force-dirty command ignored", t);
        }
    }

    private void cleanupSamsungState() {
        ready = false;
        nativeScreenOn = false;
        gestureActive = false;
        stopDragSoundImmediately();
        removeCallbacks(forceDirtyRunnable);
        effectView = null;
        effectViewAsView = null;
        handleTouchEvent = null;
        handleCustomEvent = null;
        clearScreen = null;
        removeEffect = null;
    }

    private void maybeStartDragSound(float screenX, float screenY) {
        if (dragStreamId != 0
                || SystemClock.uptimeMillis() - downTime < DRAG_SOUND_MIN_TIME_MS
                || !canPlayEffectSound()) {
            return;
        }
        float dx = screenX - lastDragSoundX;
        float dy = screenY - lastDragSoundY;
        if ((float) Math.sqrt(dx * dx + dy * dy) < DRAG_SOUND_DISTANCE_PX) {
            return;
        }
        dragStreamId = soundPool.play(dragSound, 1f, 1f, 1, -1, 1f);
        dragSoundVolume = 1f;
        dragSoundFading = false;
        lastDragSoundX = screenX;
        lastDragSoundY = screenY;
        Log.d(TAG, "sparkling bubbles drag loop started");
    }

    private void startDragSoundFade(float fadeStep) {
        if (dragStreamId == 0) {
            return;
        }
        dragSoundFadeStep = fadeStep;
        if (dragSoundFading) {
            return;
        }
        dragSoundFading = true;
        dragSoundFadeRunnable.run();
    }

    private void stopDragSoundImmediately() {
        removeCallbacks(dragSoundFadeRunnable);
        dragSoundFading = false;
        dragSoundVolume = 1f;
        if (dragStreamId == 0) {
            return;
        }
        soundPool.stop(dragStreamId);
        dragStreamId = 0;
    }

    private Rect safeRect(Rect rect) {
        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            return new Rect(rect);
        }
        return new Rect(0, 0, Math.max(1, getRenderWidth()), Math.max(1, getRenderHeight()));
    }

    private boolean canRender() {
        return !destroyed && ready && effectView != null;
    }

    private String actionName(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            default:
                return String.valueOf(action);
        }
    }

    private void makeTransparent(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof TextureView) {
            ((TextureView) view).setOpaque(false);
            return;
        }
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                makeTransparent(group.getChildAt(i));
            }
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

    private void play(int soundId) {
        if (!destroyed && soundId != 0 && canPlayEffectSound()) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private boolean canPlayEffectSound() {
        if (!OverlayPrefs.unlockEffectSoundAllowedNow(context)) {
            return false;
        }
        try {
            if (Settings.System.getInt(context.getContentResolver(),
                    "lockscreen_sounds_enabled", 1) == 0) {
                return false;
            }
        } catch (RuntimeException ignored) {
            // Match Samsung's permissive behavior when the setting cannot be queried.
        }
        return audioManager != null
                && audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM) > 0;
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
