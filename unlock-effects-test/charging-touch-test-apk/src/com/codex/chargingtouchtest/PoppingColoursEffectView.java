package com.codex.chargingtouchtest;

import android.content.Context;
import android.graphics.Bitmap;
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
import android.view.View;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

public class PoppingColoursEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer {
    private static final String TAG = "ChargingS5Popping";
    private static final int SAMSUNG_EFFECT_ID = 3;

    private final SoundPool soundPool;
    private final int tapSound;
    private final int dragSound;
    private final int unlockSound;

    private Object effectView;
    private View effectViewAsView;
    private Method handleTouchEvent;
    private Method handleCustomEvent;
    private Method clearScreen;
    private Bitmap backgroundBitmap;
    private String backgroundSource = "none";
    private boolean externalColorSource;
    private boolean ready;
    private boolean destroyed;
    private boolean gestureActive;
    private long downTime;
    private float lastDragSoundX;
    private float lastDragSoundY;
    private float dragSoundDistance;

    public PoppingColoursEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tapSound = soundPool.load(context, R.raw.particle_tap, 1);
        dragSound = soundPool.load(context, R.raw.particle_drag, 1);
        unlockSound = soundPool.load(context, R.raw.particle_unlock, 1);

        try {
            createSamsungEffect(context);
            ready = true;
            sendBackgroundBitmap();
            Log.i(TAG, "S5 popping colours Samsung renderer loaded");
        } catch (Throwable t) {
            ready = false;
            Log.e(TAG, "S5 popping colours Samsung renderer unavailable", t);
        }
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S5 popping colours";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRender()) {
            return;
        }
        downTime = SystemClock.uptimeMillis();
        gestureActive = true;
        lastDragSoundX = screenX;
        lastDragSoundY = screenY;
        dragSoundDistance = 0f;
        play(tapSound);
        forwardTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
        Log.i(TAG, "popping colours begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY));
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        float dx = screenX - lastDragSoundX;
        float dy = screenY - lastDragSoundY;
        dragSoundDistance += (float) Math.sqrt(dx * dx + dy * dy);
        lastDragSoundX = screenX;
        lastDragSoundY = screenY;
        if (dragSoundDistance >= dragSoundThreshold()) {
            play(dragSound);
            dragSoundDistance = 0f;
        }
        forwardTouch(MotionEvent.ACTION_MOVE, screenX, screenY);
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        forwardTouch(MotionEvent.ACTION_UP, lastDragSoundX, lastDragSoundY);
        if (completed) {
            sendUnlockCommand();
            play(unlockSound);
        }
        Log.i(TAG, "popping colours finish completed=" + completed
                + " x=" + Math.round(lastDragSoundX)
                + " y=" + Math.round(lastDragSoundY));
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        forwardTouch(MotionEvent.ACTION_CANCEL, lastDragSoundX, lastDragSoundY);
        Log.i(TAG, "popping colours cancel");
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        dragSoundDistance = 0f;
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
        sendBackgroundBitmap();
    }

    public void setColorSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        replaceBackgroundBitmap(source, sourceName == null ? "external" : sourceName);
        sendBackgroundBitmap();
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        setColorSourceBitmap(source, sourceName);
    }

    public boolean hasColorSourceBitmap() {
        return externalColorSource
                && backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == Math.max(1, getRenderWidth())
                && backgroundBitmap.getHeight() == Math.max(1, getRenderHeight());
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return hasColorSourceBitmap();
    }

    public void clearColorSourceBitmap() {
        externalColorSource = false;
        backgroundSource = "none";
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = null;
        sendBackgroundBitmap();
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        clearColorSourceBitmap();
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        soundPool.release();
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(new Runnable() {
            @Override
            public void run() {
                warmUp();
            }
        });
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

        Object data = dataClass.getConstructor().newInstance();
        dataClass.getMethod("setEffect", int.class).invoke(data, SAMSUNG_EFFECT_ID);
        Object poppingData = getOrCreate(
                dataClass,
                data,
                "poppingColorData",
                "com.samsung.android.visualeffect.lock.data.PoppingColorData");
        Class<?> poppingDataClass = poppingData.getClass();
        FrameLayout widgetLayer = new FrameLayout(context);
        FrameLayout wallpaperLayer = new FrameLayout(context);
        widgetLayer.setAlpha(0f);
        wallpaperLayer.setAlpha(0f);
        getField(poppingDataClass, "widgetLayout").set(poppingData, widgetLayer);
        getField(poppingDataClass, "wallpaperWidget").set(poppingData, wallpaperLayer);

        setEffect.invoke(effectView, SAMSUNG_EFFECT_ID);
        init.invoke(effectView, data);
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

    private void sendBackgroundBitmap() {
        if (!ready || handleCustomEvent == null || effectView == null) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("BGBitmap", getBackgroundBitmap());
            handleCustomEvent.invoke(effectView, 0, params);
        } catch (Throwable t) {
            Log.d(TAG, "BGBitmap command ignored", t);
        }
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
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = createWhiteBitmap(width, height);
        backgroundSource = "white_fallback";
        externalColorSource = false;
        backgroundBitmap.prepareToDraw();
        Log.i(TAG, "BGBitmap prepared source=" + backgroundSource
                + " size=" + backgroundBitmap.getWidth()
                + "x" + backgroundBitmap.getHeight());
        return backgroundBitmap;
    }

    private void replaceBackgroundBitmap(Bitmap source, String sourceName) {
        int width = Math.max(1, getRenderWidth());
        int height = Math.max(1, getRenderHeight());
        Bitmap next = createCenterCropBitmap(source, width, height);
        next.prepareToDraw();
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = next;
        backgroundSource = sourceName;
        externalColorSource = true;
        Log.i(TAG, "BGBitmap replaced source=" + backgroundSource
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
        try {
            handleTouchEvent.invoke(effectView, event, this);
        } catch (Throwable t) {
            Log.e(TAG, "touch forwarding failed", t);
        } finally {
            event.recycle();
        }
    }

    private void sendUnlockCommand() {
        if (!canRender() || handleCustomEvent == null) {
            return;
        }
        try {
            handleCustomEvent.invoke(effectView, 2, new HashMap<String, Object>());
        } catch (Throwable t) {
            Log.d(TAG, "unlock command ignored", t);
        }
    }

    private boolean canRender() {
        return !destroyed && ready && effectView != null;
    }

    private float dragSoundThreshold() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int smallest = Math.min(metrics.widthPixels, metrics.heightPixels);
        return Math.max(dp(72), smallest * 0.2f);
    }

    private int getRenderWidth() {
        int width = getWidth();
        if (width > 0) {
            return width;
        }
        return Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        int height = getHeight();
        if (height > 0) {
            return height;
        }
        return Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void play(int soundId) {
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private static Field getField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getField(name);
        field.setAccessible(true);
        return field;
    }
}
