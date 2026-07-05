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
    private static final int BACKGROUND_MODE_NORMAL = 0;
    private static final long DRAG_SOUND_MIN_TIME_MS = 1100L;
    private static final float DRAG_SOUND_DISTANCE_PX = 120f;

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

    public SparklingBubblesEffectView(Context context) {
        super(context);
        long startedAt = SystemClock.uptimeMillis();
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
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
        sendBackgroundBitmap();
        sendScreenTurnedOnCommand();
        downTime = SystemClock.uptimeMillis();
        gestureActive = true;
        lastX = screenX;
        lastY = screenY;
        lastDragSoundX = screenX;
        lastDragSoundY = screenY;
        stopDragSound();
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
        stopDragSound();
        if (completed) {
            sendUnlockCommand();
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
        stopDragSound();
        Log.i(TAG, "sparkling bubbles cancel");
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        stopDragSound();
        sendScreenTurnedOffCommand();
        if (!ready || clearScreen == null || effectView == null) {
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
        if (destroyed || !ready) {
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        sendBackgroundBitmap();
        Log.i(TAG, "sparkling bubbles warmed elapsedMs="
                + (SystemClock.uptimeMillis() - startedAt));
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (!canRender()) {
            return;
        }
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
        sendBackgroundBitmap();
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        soundPool.release();
        if (removeEffect != null && effectView != null) {
            try {
                removeEffect.invoke(effectView);
            } catch (Throwable t) {
                Log.d(TAG, "removeEffect ignored", t);
            }
        }
        recycle(blurMaskBitmap);
        recycle(backgroundBitmap);
        blurMaskBitmap = null;
        backgroundBitmap = null;
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
        stopDragSound();
        sendScreenTurnedOffCommand();
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
        if (!ready || handleCustomEvent == null || effectView == null) {
            return;
        }
        Bitmap bitmap = getBackgroundBitmap();
        if (bitmap == null) {
            return;
        }
        if (bitmap == lastSentBackgroundBitmap
                && backgroundSource.equals(lastSentBackgroundSource)) {
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
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
        return null;
    }

    private void replaceBackgroundBitmap(Bitmap source, String sourceName) {
        int width = Math.max(1, getRenderWidth());
        int height = Math.max(1, getRenderHeight());
        Bitmap next = createCenterCropBitmap(source, width, height);
        next.prepareToDraw();
        recycle(backgroundBitmap);
        backgroundBitmap = next;
        backgroundSource = sourceName == null ? "external" : sourceName;
        externalColorSource = true;
        invalidateSentBackground();
        Log.i(TAG, "sparkling bubbles background replaced source=" + backgroundSource
                + " size=" + backgroundBitmap.getWidth()
                + "x" + backgroundBitmap.getHeight());
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
            src = new Rect(left, 0, Math.min(source.getWidth(), left + srcWidth),
                    source.getHeight());
        } else {
            int srcHeight = Math.max(1, Math.round(source.getWidth() / dstRatio));
            int top = Math.max(0, (source.getHeight() - srcHeight) / 2);
            src = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + srcHeight));
        }
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG
                | Paint.DITHER_FLAG);
        canvas.drawBitmap(source, src, new Rect(0, 0, width, height), paint);
        return out;
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

    private void maybeStartDragSound(float screenX, float screenY) {
        if (dragStreamId != 0
                || SystemClock.uptimeMillis() - downTime < DRAG_SOUND_MIN_TIME_MS) {
            return;
        }
        float dx = screenX - lastDragSoundX;
        float dy = screenY - lastDragSoundY;
        if ((float) Math.sqrt(dx * dx + dy * dy) < DRAG_SOUND_DISTANCE_PX) {
            return;
        }
        dragStreamId = soundPool.play(dragSound, 1f, 1f, 1, -1, 1f);
        lastDragSoundX = screenX;
        lastDragSoundY = screenY;
        Log.d(TAG, "sparkling bubbles drag loop started");
    }

    private void stopDragSound() {
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
        if (!destroyed && soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
