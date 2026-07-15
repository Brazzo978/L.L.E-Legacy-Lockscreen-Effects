package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

public class ColourDropletEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, SensorEventListener {
    private static final String TAG = "ChargingColourDroplet";
    private static final int SAMSUNG_EFFECT_ID = 0x11;
    private static final int CMD_SET_BACKGROUND = 0;
    private static final int CMD_LOCK_AFFORDANCE = 1;
    private static final int CMD_UNLOCK = 2;
    private static final int CMD_SCREEN_OFF = 3;
    private static final int CMD_SCREEN_ON = 4;
    private static final int CMD_CUSTOM = 99;
    private static final int BACKGROUND_MODE_NORMAL = 0;
    private static final String CUSTOM_EVENT_FORCE_DIRTY = "ForceDirty";
    private static final long SENSOR_REGISTER_DELAY_MS = 10L;
    private static final long SENSOR_LOG_INTERVAL_MS = 1000L;

    private final Context context;
    private final boolean gyroEnabled;
    private final AudioManager audioManager;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Display sensorDisplay;
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
    private Object sensorJniRenderer;
    private Method nativeOnSensorEvent;
    private Bitmap normalResourceBitmap;
    private Bitmap edgeDensityResourceBitmap;
    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private Bitmap lastSentBackgroundBitmap;
    private String backgroundSource = "none";
    private String lastSentBackgroundSource = "";
    private boolean externalColorSource;
    private boolean ready;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean nativeScreenOn;
    private boolean accelerometerRegistered;
    private long downTime;
    private long lastSensorLogAt;
    private float lastX;
    private float lastY;
    private final Runnable forceDirtyRunnable = new Runnable() {
        @Override
        public void run() {
            sendForceDirtyCommand();
        }
    };
    private final Runnable registerAccelerometerRunnable = new Runnable() {
        @Override
        public void run() {
            registerAccelerometer();
        }
    };

    public ColourDropletEffectView(Context context) {
        this(context, false);
    }

    public ColourDropletEffectView(Context context, boolean gyroEnabled) {
        super(context);
        this.context = context.getApplicationContext();
        this.gyroEnabled = gyroEnabled;
        audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        sensorManager = gyroEnabled
                ? (SensorManager) this.context.getSystemService(Context.SENSOR_SERVICE)
                : null;
        accelerometer = !gyroEnabled || sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        WindowManager windowManager = gyroEnabled
                ? (WindowManager) context.getSystemService(Context.WINDOW_SERVICE)
                : null;
        sensorDisplay = windowManager == null ? null : windowManager.getDefaultDisplay();
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
        tapSound = soundPool.load(context, R.raw.ve_colourdroplet_tap, 1);
        lockSound = soundPool.load(context, R.raw.ve_colourdroplet_lock, 1);
        unlockSound = soundPool.load(context, R.raw.ve_colourdroplet_unlock, 1);

        try {
            createSamsungEffect(context);
            ready = true;
            Log.i(TAG, "Note5 colour droplet native renderer loaded gyro=" + gyroEnabled
                    + " elapsedMs="
                    + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            ready = false;
            cleanupSamsungState();
            Log.e(TAG, "Note5 colour droplet native renderer unavailable", t);
        }
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return gyroEnabled
                ? "N5 Colored Droplet + Gyro"
                : "N5 Colored Droplet";
    }

    boolean isReady() {
        return ready
                && !destroyed
                && (!gyroEnabled
                || (sensorManager != null
                && accelerometer != null
                && sensorJniRenderer != null
                && nativeOnSensorEvent != null));
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
            Log.i(TAG, "colour droplet warmed elapsedMs=" + elapsedMs);
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
        releaseBackgroundBitmap();
        if (!destroyed) {
            sendBackgroundBitmap();
        }
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        Log.i(TAG, "BEGIN colour droplet destroy");
        Log.i(TAG, "BEGIN colour droplet reset");
        resetEffect();
        Log.i(TAG, "END colour droplet reset");
        removeCallbacks(forceDirtyRunnable);
        sendScreenTurnedOffCommand();
        destroyed = true;
        soundPool.release();
        if (removeEffect != null && effectView != null) {
            try {
                Log.i(TAG, "BEGIN colour droplet removeEffect/detach");
                removeEffect.invoke(effectView);
                Log.i(TAG, "END colour droplet removeEffect/detach");
            } catch (Throwable t) {
                Log.d(TAG, "removeEffect ignored", t);
            }
        }
        removeAllViews();
        externalColorSource = false;
        backgroundSource = "none";
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSource = "";
        releaseBackgroundBitmap();
        recycle(normalResourceBitmap);
        recycle(edgeDensityResourceBitmap);
        normalResourceBitmap = null;
        edgeDensityResourceBitmap = null;
        cleanupSamsungState();
        Log.i(TAG, "END colour droplet destroy");
    }

    @Override
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && backgroundBitmap == bitmap;
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
        resolveNativeSensorBridge();
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

    private void resolveNativeSensorBridge() {
        if (!gyroEnabled || effectView == null) {
            return;
        }
        try {
            Object samsungEffect = getField(effectView.getClass(), "mView").get(effectView);
            Object renderer = getField(samsungEffect.getClass(), "mIRenderer").get(samsungEffect);
            sensorJniRenderer = getField(renderer.getClass(), "mIJniRenderer").get(renderer);
            nativeOnSensorEvent = sensorJniRenderer.getClass().getMethod(
                    "onSensorEvent",
                    int.class,
                    float.class,
                    float.class,
                    float.class);
            Log.i(TAG, "colour droplet gyro JNI bridge ready renderer="
                    + renderer.getClass().getSimpleName());
        } catch (Throwable t) {
            sensorJniRenderer = null;
            nativeOnSensorEvent = null;
            Log.w(TAG, "colour droplet gyro bridge unavailable", t);
        }
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
            Log.i(TAG, "colour droplet background sent source=" + backgroundSource
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
        releaseBackgroundBitmap();
        backgroundBitmap = createWhiteBitmap(width, height);
        ownsBackgroundBitmap = true;
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
        boolean borrow = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, width, height);
        Bitmap next = borrow ? source : createCenterCropBitmap(source, width, height);
        next.prepareToDraw();
        releaseBackgroundBitmap();
        backgroundBitmap = next;
        ownsBackgroundBitmap = !borrow;
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
        if (nativeScreenOn) {
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        try {
            handleCustomEvent.invoke(effectView, CMD_SCREEN_ON, new HashMap<String, Object>());
            nativeScreenOn = true;
            if (gyroEnabled) {
                removeCallbacks(registerAccelerometerRunnable);
                postDelayed(registerAccelerometerRunnable, SENSOR_REGISTER_DELAY_MS);
            }
            Log.i(TAG, "colour droplet screen-on sent elapsedMs="
                    + (SystemClock.uptimeMillis() - startedAt));
        } catch (Throwable t) {
            Log.d(TAG, "screen-on command ignored", t);
        }
    }

    private void sendScreenTurnedOffCommand() {
        removeCallbacks(registerAccelerometerRunnable);
        unregisterAccelerometer();
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
            Log.i(TAG, "colour droplet screen-off sent elapsedMs="
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
            Log.i(TAG, "colour droplet force-dirty sent");
        } catch (Throwable t) {
            Log.d(TAG, "force-dirty command ignored", t);
        }
    }

    private void cleanupSamsungState() {
        removeCallbacks(registerAccelerometerRunnable);
        unregisterAccelerometer();
        ready = false;
        nativeScreenOn = false;
        gestureActive = false;
        removeCallbacks(forceDirtyRunnable);
        effectView = null;
        effectViewAsView = null;
        handleTouchEvent = null;
        handleCustomEvent = null;
        clearScreen = null;
        removeEffect = null;
        sensorJniRenderer = null;
        nativeOnSensorEvent = null;
    }

    private void registerAccelerometer() {
        if (!gyroEnabled
                || destroyed
                || !nativeScreenOn
                || accelerometerRegistered
                || sensorManager == null
                || accelerometer == null
                || sensorJniRenderer == null
                || nativeOnSensorEvent == null) {
            return;
        }
        accelerometerRegistered = sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME);
        Log.i(TAG, "colour droplet gyro registered="
                + accelerometerRegistered);
    }

    private void unregisterAccelerometer() {
        if (!accelerometerRegistered || sensorManager == null) {
            return;
        }
        sensorManager.unregisterListener(this);
        accelerometerRegistered = false;
        Log.i(TAG, "colour droplet gyro unregistered");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!accelerometerRegistered
                || event == null
                || event.sensor == null
                || event.values == null
                || event.values.length < 3
                || sensorJniRenderer == null
                || nativeOnSensorEvent == null) {
            return;
        }
        float x = clampSensor(event.values[0]);
        float y = clampSensor(event.values[1]);
        float z = event.values[2];
        float rotatedX = x;
        float rotatedY = y;
        int rotation = sensorDisplay == null
                ? Surface.ROTATION_0
                : sensorDisplay.getRotation();
        if (rotation == Surface.ROTATION_90) {
            rotatedX = -y;
            rotatedY = x;
        } else if (rotation == Surface.ROTATION_180) {
            rotatedX = -x;
            rotatedY = -y;
        } else if (rotation == Surface.ROTATION_270) {
            rotatedX = y;
            rotatedY = -x;
        }
        try {
            nativeOnSensorEvent.invoke(
                    sensorJniRenderer,
                    event.sensor.getType(),
                    rotatedX,
                    rotatedY,
                    z);
            long now = SystemClock.uptimeMillis();
            if (Log.isLoggable(TAG, Log.DEBUG)
                    && now - lastSensorLogAt >= SENSOR_LOG_INTERVAL_MS) {
                lastSensorLogAt = now;
                Log.d(TAG, "colour droplet gyro sample x="
                        + roundSensor(rotatedX)
                        + " y=" + roundSensor(rotatedY)
                        + " z=" + roundSensor(z));
            }
        } catch (Throwable t) {
            Log.w(TAG, "colour droplet gyro forwarding failed", t);
            unregisterAccelerometer();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private float clampSensor(float value) {
        return Math.max(-10f, Math.min(10f, value));
    }

    private float roundSensor(float value) {
        return Math.round(value * 100f) / 100f;
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

    private void releaseBackgroundBitmap() {
        if (ownsBackgroundBitmap) {
            recycle(backgroundBitmap);
        }
        backgroundBitmap = null;
        ownsBackgroundBitmap = false;
    }
}
