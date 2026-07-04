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

public class ColourDropletEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer {
    private static final String TAG = "ChargingColourDroplet";
    private static final int SAMSUNG_EFFECT_ID = 0x11;
    private static final int CMD_SET_BACKGROUND = 0;
    private static final int CMD_LOCK_AFFORDANCE = 1;
    private static final int CMD_UNLOCK = 2;
    private static final int CMD_SCREEN_OFF = 3;
    private static final int CMD_SCREEN_ON = 4;
    private static final int BACKGROUND_MODE_NORMAL = 0;

    private final SoundPool soundPool;
    private final int tapSound;
    private final int lockSound;
    private final int unlockSound;

    private Object effectView;
    private View effectViewAsView;
    private Method handleTouchEvent;
    private Method handleCustomEvent;
    private Method clearScreen;
    private Method removeEffect;
    private Bitmap normalResourceBitmap;
    private Bitmap edgeDensityResourceBitmap;
    private Bitmap backgroundBitmap;
    private Bitmap lastSentBackgroundBitmap;
    private String backgroundSource = "none";
    private String lastSentBackgroundSource = "";
    private boolean externalColorSource;
    private boolean ready;
    private boolean destroyed;
    private boolean gestureActive;
    private long downTime;
    private float lastX;
    private float lastY;

    public ColourDropletEffectView(Context context) {
        super(context);
        long startedAt = SystemClock.uptimeMillis();
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tapSound = soundPool.load(context, R.raw.ve_colourdroplet_tap, 1);
        lockSound = soundPool.load(context, R.raw.ve_colourdroplet_lock, 1);
        unlockSound = soundPool.load(context, R.raw.ve_colourdroplet_unlock, 1);

        try {
            createSamsungEffect(context);
            ready = true;
            Log.i(TAG, "Note5 colour droplet native renderer loaded elapsedMs="
                    + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            ready = false;
            Log.e(TAG, "Note5 colour droplet native renderer unavailable", t);
        }
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "N5 Colored Droplet";
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
        play(tapSound);
        forwardTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
        Log.i(TAG, "colour droplet begin x=" + Math.round(screenX)
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
        Log.i(TAG, "colour droplet finish completed=" + completed
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
        Log.i(TAG, "colour droplet cancel");
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
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
        sendScreenTurnedOnCommand();
        Log.i(TAG, "colour droplet warmed elapsedMs="
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
            Log.i(TAG, "colour droplet affordance sent delayMs=" + Math.max(0L, startDelayMs)
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
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSource = "";
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
        clearBackgroundSourceBitmap();
        recycle(normalResourceBitmap);
        recycle(edgeDensityResourceBitmap);
        normalResourceBitmap = null;
        edgeDensityResourceBitmap = null;
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
        Object colourDropletData = getOrCreate(
                dataClass,
                data,
                "colorDroplet",
                "com.samsung.android.visualeffect.lock.data.ColourDropletData");
        Class<?> colourDropletDataClass = colourDropletData.getClass();

        normalResourceBitmap = decodeOriginalBitmap(R.drawable.n5_colour_droplet_normal);
        edgeDensityResourceBitmap =
                decodeOriginalBitmap(R.drawable.n5_colour_droplet_edge_density);
        getField(colourDropletDataClass, "windowWidth").setInt(
                colourDropletData,
                Math.max(1, getRenderWidth()));
        getField(colourDropletDataClass, "windowHeight").setInt(
                colourDropletData,
                Math.max(1, getRenderHeight()));
        getField(colourDropletDataClass, "resNormal").set(
                colourDropletData,
                normalResourceBitmap);
        getField(colourDropletDataClass, "resEdgeDensity").set(
                colourDropletData,
                edgeDensityResourceBitmap);

        setEffect.invoke(effectView, SAMSUNG_EFFECT_ID);
        makeTransparent(effectViewAsView);
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
        bitmap.prepareToDraw();
        return bitmap;
    }

    private void sendBackgroundBitmap() {
        if (!ready || handleCustomEvent == null || effectView == null) {
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
            Log.i(TAG, "colour droplet background sent source=" + backgroundSource
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            Log.d(TAG, "background command ignored", t);
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
        recycle(backgroundBitmap);
        backgroundBitmap = createWhiteBitmap(width, height);
        backgroundSource = "white_fallback";
        externalColorSource = false;
        backgroundBitmap.prepareToDraw();
        Log.i(TAG, "colour droplet fallback background prepared size="
                + backgroundBitmap.getWidth() + "x" + backgroundBitmap.getHeight());
        return backgroundBitmap;
    }

    private void replaceBackgroundBitmap(Bitmap source, String sourceName) {
        int width = Math.max(1, getRenderWidth());
        int height = Math.max(1, getRenderHeight());
        Bitmap next = createCenterCropBitmap(source, width, height);
        next.prepareToDraw();
        recycle(backgroundBitmap);
        backgroundBitmap = next;
        backgroundSource = sourceName;
        externalColorSource = true;
        Log.i(TAG, "colour droplet background replaced source=" + backgroundSource
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
            handleTouchEvent.invoke(effectView, event, effectViewAsView);
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
            handleCustomEvent.invoke(effectView, CMD_UNLOCK, new HashMap<String, Object>());
        } catch (Throwable t) {
            Log.d(TAG, "unlock command ignored", t);
        }
    }

    private void sendScreenTurnedOnCommand() {
        if (!canRender() || handleCustomEvent == null) {
            return;
        }
        try {
            handleCustomEvent.invoke(effectView, CMD_SCREEN_ON, new HashMap<String, Object>());
        } catch (Throwable t) {
            Log.d(TAG, "screen-on command ignored", t);
        }
    }

    @SuppressWarnings("unused")
    private void sendScreenTurnedOffCommand() {
        if (!canRender() || handleCustomEvent == null) {
            return;
        }
        try {
            handleCustomEvent.invoke(effectView, CMD_SCREEN_OFF, new HashMap<String, Object>());
        } catch (Throwable t) {
            Log.d(TAG, "screen-off command ignored", t);
        }
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
