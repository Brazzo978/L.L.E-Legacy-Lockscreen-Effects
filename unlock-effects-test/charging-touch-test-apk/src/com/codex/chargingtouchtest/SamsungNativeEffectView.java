package com.codex.chargingtouchtest;

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
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class SamsungNativeEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer {
    private static final String TAG = "ChargingSamsungFx";
    private static final long BACKGROUND_RETRY_MS = 160L;

    private final Config config;
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
    private boolean externalBackgroundSource;
    private boolean ready;
    private boolean destroyed;
    private boolean gestureActive;
    private long downTime;
    private float lastX;
    private float lastY;
    private float lastDragSoundX;
    private float lastDragSoundY;
    private float dragSoundDistance;

    public SamsungNativeEffectView(Context context, int effect) {
        super(context);
        config = Config.fromOverlayEffect(effect);
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
        tapSound = loadSound(config.tapSoundRes);
        dragSound = loadSound(config.dragSoundRes);
        unlockSound = loadSound(config.unlockSoundRes);

        try {
            createSamsungEffect(context);
            ready = true;
            sendBackgroundBitmap();
            scheduleBackgroundRetry();
            Log.i(TAG, config.name + " Samsung native renderer loaded");
        } catch (Throwable t) {
            ready = false;
            Log.e(TAG, config.name + " Samsung native renderer unavailable", t);
        }
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return config.name;
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRender()) {
            return;
        }
        downTime = SystemClock.uptimeMillis();
        gestureActive = true;
        lastX = screenX;
        lastY = screenY;
        lastDragSoundX = screenX;
        lastDragSoundY = screenY;
        dragSoundDistance = 0f;
        play(tapSound);
        forwardTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
        Log.i(TAG, config.name + " begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY));
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        maybePlayDragSound(screenX, screenY);
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
        Log.i(TAG, config.name + " finish completed=" + completed
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
        Log.i(TAG, config.name + " cancel");
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
            Log.d(TAG, config.name + " clearScreen ignored", t);
        }
    }

    @Override
    public void warmUp() {
        if (destroyed || !ready) {
            return;
        }
        sendBackgroundBitmap();
        scheduleBackgroundRetry();
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        // Other native effects are gesture-only in this package.
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalBackgroundSource
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
        scheduleBackgroundRetry();
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        externalBackgroundSource = false;
        backgroundSource = "none";
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
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
        Class<?> listenerClass =
                Class.forName("com.samsung.android.visualeffect.IEffectListener");
        Object effectListener = createEffectListener(listenerClass);

        effectView = effectViewClass.getConstructor(Context.class).newInstance(context);
        effectViewAsView = (View) effectView;
        effectViewAsView.setBackgroundColor(Color.TRANSPARENT);
        effectViewAsView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        addView(effectViewAsView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        Method setEffect = effectViewClass.getMethod("setEffect", int.class);
        Method init = effectViewClass.getMethod("init", dataClass);
        Method setListener = effectViewClass.getMethod("setListener", listenerClass);
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
        dataClass.getMethod("setEffect", int.class).invoke(data, config.samsungEffectId);
        prepareEffectData(dataClass, data, effectListener);

        setEffect.invoke(effectView, config.samsungEffectId);
        init.invoke(effectView, data);
        setListener.invoke(effectView, effectListener);
        configureTransparentSurfaces(effectViewAsView);
        post(new Runnable() {
            @Override
            public void run() {
                configureTransparentSurfaces(effectViewAsView);
            }
        });
    }

    private Object createEffectListener(Class<?> listenerClass) {
        return Proxy.newProxyInstance(
                listenerClass.getClassLoader(),
                new Class<?>[] { listenerClass },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("onReceive".equals(method.getName())) {
                            Log.d(TAG, config.name + " callback " + callbackSummary(args));
                        }
                        return null;
                    }
                });
    }

    private String callbackSummary(Object[] args) {
        if (args == null || args.length == 0) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            Object arg = args[i];
            if (arg instanceof Map) {
                builder.append(((Map<?, ?>) arg).keySet());
            } else {
                builder.append(arg);
            }
        }
        return builder.toString();
    }

    private void prepareEffectData(Class<?> dataClass, Object data, Object effectListener)
            throws Exception {
        if (config.kind == Config.KIND_COLOUR_DROPLET) {
            Class<?> colourDataClass =
                    Class.forName("com.samsung.android.visualeffect.lock.data.ColourDropletData");
            Object colourData = getOrCreate(dataClass, data, "colorDroplet", colourDataClass);
            setInt(colourDataClass, colourData, "windowWidth", getRenderWidth());
            setInt(colourDataClass, colourData, "windowHeight", getRenderHeight());
            getField(colourDataClass, "resNormal").set(
                    colourData,
                    loadAssetBitmap("note5_normal_low_z_256.png", createFlatNormalBitmap()));
            getField(colourDataClass, "resEdgeDensity").set(
                    colourData,
                    loadAssetBitmap("note5_edge_density_720.png", createSolidBitmap(Color.BLACK)));
            setObjectIfFieldExists(colourDataClass, colourData, "mIEffectListener", effectListener);
        } else if (config.kind == Config.KIND_SPARKLING_BUBBLES) {
            Class<?> bubblesDataClass =
                    Class.forName("com.samsung.android.visualeffect.lock.data.SparklingBullesData");
            Object bubblesData = getOrCreate(dataClass, data, "sparklingBubblesData",
                    bubblesDataClass);
            setInt(bubblesDataClass, bubblesData, "windowWidth", getRenderWidth());
            setInt(bubblesDataClass, bubblesData, "windowHeight", getRenderHeight());
            getField(bubblesDataClass, "resBmp").set(bubblesData, getBackgroundBitmap());
            setObjectIfFieldExists(bubblesDataClass, bubblesData, "mIEffectListener",
                    effectListener);
        }
    }

    private void sendBackgroundBitmap() {
        if (!ready || handleCustomEvent == null || effectView == null || !config.usesBackground) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("Bitmap", getBackgroundBitmap());
            if (config.usesSPhysicsBackground) {
                params.put("Mode", Integer.valueOf(0));
            }
            handleCustomEvent.invoke(effectView, 0, params);
            Log.i(TAG, config.name + " background sent source=" + backgroundSource
                    + " size=" + getBackgroundBitmap().getWidth()
                    + "x" + getBackgroundBitmap().getHeight());
        } catch (Throwable t) {
            Log.d(TAG, config.name + " background command ignored", t);
        }
    }

    private void scheduleBackgroundRetry() {
        if (!config.backgroundRetry) {
            return;
        }
        postDelayed(new Runnable() {
            @Override
            public void run() {
                sendBackgroundBitmap();
            }
        }, BACKGROUND_RETRY_MS);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                sendBackgroundBitmap();
            }
        }, BACKGROUND_RETRY_MS * 2L);
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
        backgroundBitmap = config.transparentBackground
                ? createTransparentBitmap(width, height)
                : createWhiteBitmap(width, height);
        backgroundSource = config.transparentBackground
                ? "transparent_fallback"
                : "white_fallback";
        externalBackgroundSource = false;
        backgroundBitmap.prepareToDraw();
        Log.i(TAG, config.name + " background prepared source=" + backgroundSource
                + " size=" + width + "x" + height);
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
        externalBackgroundSource = true;
        Log.i(TAG, config.name + " background replaced source=" + backgroundSource
                + " size=" + backgroundBitmap.getWidth()
                + "x" + backgroundBitmap.getHeight());
    }

    private Bitmap createCenterCropBitmap(Bitmap source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
            return copy != null ? copy : source;
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

    private Bitmap loadAssetBitmap(String assetName, Bitmap fallback) {
        InputStream stream = null;
        try {
            stream = getContext().getAssets().open(assetName);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap != null) {
                bitmap.prepareToDraw();
                Log.i(TAG, config.name + " asset loaded " + assetName
                        + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                return bitmap;
            }
        } catch (Throwable t) {
            Log.w(TAG, config.name + " asset unavailable " + assetName, t);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return fallback;
    }

    private Bitmap createFlatNormalBitmap() {
        return createSolidBitmap(Color.rgb(128, 128, 255));
    }

    private Bitmap createSolidBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(
                Math.max(1, getRenderWidth()),
                Math.max(1, getRenderHeight()),
                Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        bitmap.prepareToDraw();
        return bitmap;
    }

    private Bitmap createWhiteBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        return bitmap;
    }

    private Bitmap createTransparentBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);
        return bitmap;
    }

    private void configureTransparentSurfaces(View view) {
        if (view == null) {
            return;
        }
        view.setBackgroundColor(Color.TRANSPARENT);
        if (view instanceof TextureView) {
            try {
                ((TextureView) view).setOpaque(false);
                Log.i(TAG, config.name + " TextureView forced non-opaque");
            } catch (Throwable t) {
                Log.d(TAG, config.name + " TextureView transparency setup ignored", t);
            }
        }
        if (view instanceof SurfaceView) {
            SurfaceView surfaceView = (SurfaceView) view;
            try {
                surfaceView.setZOrderOnTop(true);
                surfaceView.setZOrderMediaOverlay(true);
                SurfaceHolder holder = surfaceView.getHolder();
                if (holder != null) {
                    holder.setFormat(PixelFormat.TRANSLUCENT);
                }
                Log.i(TAG, config.name + " SurfaceView forced translucent");
            } catch (Throwable t) {
                Log.d(TAG, config.name + " SurfaceView transparency setup ignored", t);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                configureTransparentSurfaces(group.getChildAt(i));
            }
        }
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
            Log.e(TAG, config.name + " touch forwarding failed", t);
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
            Log.d(TAG, config.name + " unlock command ignored", t);
        }
    }

    private void maybePlayDragSound(float screenX, float screenY) {
        if (dragSound == 0) {
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

    private int loadSound(int resId) {
        return resId == 0 ? 0 : soundPool.load(getContext(), resId, 1);
    }

    private void play(int soundId) {
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private static Object getOrCreate(Class<?> owner, Object target, String fieldName,
            Class<?> valueClass) throws Exception {
        Field field = getField(owner, fieldName);
        Object value = field.get(target);
        if (value == null) {
            value = valueClass.getConstructor().newInstance();
            field.set(target, value);
        }
        return value;
    }

    private static Field getField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getField(name);
        field.setAccessible(true);
        return field;
    }

    private static void setInt(Class<?> owner, Object target, String name, int value)
            throws Exception {
        getField(owner, name).setInt(target, value);
    }

    private static void setObjectIfFieldExists(Class<?> owner, Object target, String name,
            Object value) throws Exception {
        try {
            getField(owner, name).set(target, value);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private static final class Config {
        static final int KIND_WATERCOLOUR = 1;
        static final int KIND_COLOUR_DROPLET = 2;
        static final int KIND_SPARKLING_BUBBLES = 3;

        final int kind;
        final int samsungEffectId;
        final String name;
        final int tapSoundRes;
        final int dragSoundRes;
        final int unlockSoundRes;
        final boolean usesBackground;
        final boolean usesSPhysicsBackground;
        final boolean backgroundRetry;
        final boolean transparentBackground;

        Config(int kind, int samsungEffectId, String name, int tapSoundRes,
                int dragSoundRes, int unlockSoundRes, boolean usesBackground,
                boolean usesSPhysicsBackground, boolean backgroundRetry,
                boolean transparentBackground) {
            this.kind = kind;
            this.samsungEffectId = samsungEffectId;
            this.name = name;
            this.tapSoundRes = tapSoundRes;
            this.dragSoundRes = dragSoundRes;
            this.unlockSoundRes = unlockSoundRes;
            this.usesBackground = usesBackground;
            this.usesSPhysicsBackground = usesSPhysicsBackground;
            this.backgroundRetry = backgroundRetry;
            this.transparentBackground = transparentBackground;
        }

        static Config fromOverlayEffect(int effect) {
            if (effect == OverlayPrefs.EFFECT_WATERCOLOUR) {
                return new Config(
                        KIND_WATERCOLOUR,
                        5,
                        "Watercolor native",
                        R.raw.ve_watercolour_tap,
                        0,
                        R.raw.ve_watercolour_unlock,
                        true,
                        false,
                        true,
                        false);
            }
            if (effect == OverlayPrefs.EFFECT_COLOUR_DROPLET) {
                return new Config(
                        KIND_COLOUR_DROPLET,
                        16,
                        "S5 coloured droplets",
                        R.raw.ve_colourdroplet_tap,
                        0,
                        R.raw.ve_colourdroplet_unlock,
                        true,
                        true,
                        true,
                        true);
            }
            return new Config(
                    KIND_SPARKLING_BUBBLES,
                    14,
                    "S5 sparkling bubbles",
                    R.raw.ve_sparklingbubbles_tap,
                    R.raw.ve_sparklingbubbles_drag,
                    R.raw.ve_sparklingbubbles_unlock,
                    true,
                    true,
                    true,
                    true);
        }
    }
}
