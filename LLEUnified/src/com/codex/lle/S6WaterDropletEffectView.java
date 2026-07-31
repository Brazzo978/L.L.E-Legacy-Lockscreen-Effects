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
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * DEX-free LLE shell for the original Galaxy S6 Edge Water Droplet renderer.
 *
 * <p>The native engine owns global state. LLE must keep at most one live instance of this view
 * in its process and must call {@link #destroy()} before constructing another.</p>
 */
public final class S6WaterDropletEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness,
        SensorEventListener, S6WaterDropletGlView.Listener {
    private static final String TAG = "S6WaterDroplet";
    private static final int EVENT_CLEAR = 90;
    private static final int EVENT_UNLOCK = 91;
    private static final int EVENT_AFFORDANCE = 92;
    private static final int EVENT_RESET_BG_SCALE = 96;
    private static final long SENSOR_REGISTER_DELAY_MS = 10L;

    private final Context appContext;
    private final S6WaterDropletGlView glView;
    private final AudioManager audioManager;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Display sensorDisplay;

    private Bitmap backgroundBitmap;
    private boolean externalColorSource;
    private boolean constructed;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean sensorRegistered;
    private boolean pausedForDetach;
    private float lastX;
    private float lastY;
    private Rect pendingAffordance;
    private volatile int readinessState = STATE_CONSTRUCTED;
    private volatile String readinessDetail = "constructed";
    private volatile ReadinessListener readinessListener;

    private final Runnable affordanceRunnable = new Runnable() {
        @Override
        public void run() {
            if (!destroyed && pendingAffordance != null) {
                Rect rect = pendingAffordance;
                pendingAffordance = null;
                glView.custom(EVENT_AFFORDANCE, rect.centerX(), rect.centerY(), 0f);
            }
        }
    };

    private final Runnable registerSensorRunnable = new Runnable() {
        @Override
        public void run() {
            registerSensor();
        }
    };

    public S6WaterDropletEffectView(Context context) {
        super(context);
        appContext = context.getApplicationContext();

        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);

        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        tapSound = soundPool.load(context, R.raw.s6_water_droplet_tap, 1);
        unlockSound = soundPool.load(context, R.raw.s6_water_droplet_unlock, 1);

        sensorManager =
                (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null
                ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        sensorDisplay = windowManager == null ? null : windowManager.getDefaultDisplay();

        int width = renderWidth();
        int height = renderHeight();
        int projectKind =
                getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 1 : 0;
        glView = new S6WaterDropletGlView(
                context,
                new WaterDropletNativeBridge(),
                projectKind,
                width,
                height,
                decode(R.drawable.s6_water_droplet_normal),
                decode(R.drawable.s6_water_droplet_edge_density),
                this);
        addView(glView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        submitBackground(createWhiteBitmap(width, height), false);
        constructed = true;
        Log.i(TAG, "S6 Edge Water Droplet shell constructed");
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S6 Edge Water Droplet";
    }

    @Override
    public int getReadinessState() {
        return readinessState;
    }

    @Override
    public String getReadinessDetail() {
        return effectName() + ": " + readinessDetail;
    }

    @Override
    public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    boolean isReady() {
        return constructed && !destroyed
                && sensorManager != null && accelerometer != null;
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRender()) {
            return;
        }
        removeCallbacks(affordanceRunnable);
        gestureActive = true;
        lastX = screenX;
        lastY = screenY;
        play(tapSound);
        glView.touch(0, Math.round(screenX), Math.round(screenY));
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        lastX = screenX;
        lastY = screenY;
        glView.touch(2, Math.round(screenX), Math.round(screenY));
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        glView.touch(1, Math.round(lastX), Math.round(lastY));
        if (completed) {
            glView.key(EVENT_UNLOCK);
            play(unlockSound);
        }
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        glView.touch(1, Math.round(lastX), Math.round(lastY));
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        removeCallbacks(affordanceRunnable);
        if (canRender()) {
            glView.setTouched(false);
            glView.key(EVENT_CLEAR);
        }
    }

    @Override
    public void warmUp() {
        if (!destroyed) {
            glView.wake(100);
        }
    }

    void parkForReuse() {
        resetEffect();
        if (!destroyed) {
            glView.key(EVENT_RESET_BG_SCALE);
        }
    }

    void resumeForReuse() {
        warmUp();
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (!canRender()) {
            return;
        }
        pendingAffordance =
                screenRect != null && screenRect.width() > 0 && screenRect.height() > 0
                        ? new Rect(screenRect)
                        : new Rect(0, 0, renderWidth(), renderHeight());
        removeCallbacks(affordanceRunnable);
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
        glView.wake(100);
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalColorSource && validBackground();
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        submitBackground(centerCrop(source, renderWidth(), renderHeight()), true);
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        if (!destroyed) {
            submitBackground(createWhiteBitmap(renderWidth(), renderHeight()), false);
        }
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        removeCallbacks(affordanceRunnable);
        removeCallbacks(registerSensorRunnable);
        unregisterSensor();
        soundPool.release();
        glView.destroyNative();
        removeAllViews();
        recycle(backgroundBitmap);
        backgroundBitmap = null;
        transition(STATE_FAILED, "destroyed");
        readinessListener = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        transition(STATE_ATTACHED, "attached; waiting for EGL");
        if (pausedForDetach) {
            pausedForDetach = false;
            glView.onResume();
        }
        postDelayed(registerSensorRunnable, SENSOR_REGISTER_DELAY_MS);
        warmUp();
    }

    @Override
    protected void onDetachedFromWindow() {
        gestureActive = false;
        removeCallbacks(affordanceRunnable);
        removeCallbacks(registerSensorRunnable);
        unregisterSensor();
        if (!destroyed) {
            glView.onPause();
            pausedForDetach = true;
            transition(STATE_DETACHED, "GLSurfaceView detached");
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onNativeResourcesReady() {
        transition(STATE_RESOURCES_READY, "native resources ready");
    }

    @Override
    public void onFirstFrame() {
        transition(STATE_FIRST_FRAME_READY, "first native frame drawn");
    }

    @Override
    public void onNativeFailure(Throwable error) {
        constructed = false;
        transition(STATE_FAILED, "native failure: "
                + error.getClass().getSimpleName());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!sensorRegistered || event == null || event.sensor == null
                || event.values == null || event.values.length < 3) {
            return;
        }
        float x = clampSensor(event.values[0]);
        float y = clampSensor(event.values[1]);
        float z = event.values[2];
        int rotation =
                sensorDisplay == null ? Surface.ROTATION_0 : sensorDisplay.getRotation();
        if (rotation == Surface.ROTATION_90) {
            glView.sensor(event.sensor.getType(), -y, x, z);
        } else if (rotation == Surface.ROTATION_180) {
            glView.sensor(event.sensor.getType(), -x, -y, z);
        } else if (rotation == Surface.ROTATION_270) {
            glView.sensor(event.sensor.getType(), y, -x, z);
        } else {
            glView.sensor(event.sensor.getType(), x, y, z);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void submitBackground(Bitmap portrait, boolean external) {
        Bitmap landscape = centerCrop(
                portrait,
                Math.max(renderWidth(), renderHeight()),
                Math.min(renderWidth(), renderHeight()));
        recycle(backgroundBitmap);
        backgroundBitmap = portrait;
        externalColorSource = external;
        glView.setBackground(
                portrait.copy(Bitmap.Config.ARGB_8888, false),
                landscape);
    }

    private boolean validBackground() {
        return backgroundBitmap != null && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == renderWidth()
                && backgroundBitmap.getHeight() == renderHeight();
    }

    private Bitmap decode(int resourceId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap =
                BitmapFactory.decodeResource(getResources(), resourceId, options);
        if (bitmap == null) {
            throw new IllegalStateException(
                    "Missing S6 Water Droplet texture " + resourceId);
        }
        bitmap.prepareToDraw();
        return bitmap;
    }

    private Bitmap centerCrop(Bitmap source, int width, int height) {
        float sourceRatio = source.getWidth() / (float) source.getHeight();
        float targetRatio = width / (float) height;
        Rect sourceRect;
        if (sourceRatio > targetRatio) {
            int cropWidth =
                    Math.max(1, Math.round(source.getHeight() * targetRatio));
            int left = Math.max(0, (source.getWidth() - cropWidth) / 2);
            sourceRect = new Rect(
                    left, 0, Math.min(source.getWidth(), left + cropWidth),
                    source.getHeight());
        } else {
            int cropHeight =
                    Math.max(1, Math.round(source.getWidth() / targetRatio));
            int top = Math.max(0, (source.getHeight() - cropHeight) / 2);
            sourceRect = new Rect(
                    0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + cropHeight));
        }
        Bitmap output =
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        new Canvas(output).drawBitmap(
                source, sourceRect, new Rect(0, 0, width, height), paint);
        output.prepareToDraw();
        return output;
    }

    private Bitmap createWhiteBitmap(int width, int height) {
        Bitmap bitmap =
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        return bitmap;
    }

    private int renderWidth() {
        if (getWidth() > 0) {
            return getWidth();
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(1, metrics.widthPixels);
    }

    private int renderHeight() {
        if (getHeight() > 0) {
            return getHeight();
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(1, metrics.heightPixels);
    }

    private boolean canRender() {
        return constructed && !destroyed;
    }

    private void registerSensor() {
        if (destroyed || sensorRegistered
                || sensorManager == null || accelerometer == null) {
            return;
        }
        sensorRegistered = sensorManager.registerListener(
                this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    private void unregisterSensor() {
        if (sensorRegistered && sensorManager != null) {
            sensorManager.unregisterListener(this);
            sensorRegistered = false;
        }
    }

    private float clampSensor(float value) {
        return Math.max(-10f, Math.min(10f, value));
    }

    private void play(int soundId) {
        if (soundId != 0 && canPlaySound()) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private boolean canPlaySound() {
        if (!OverlayPrefs.unlockEffectSoundAllowedNow(appContext)) {
            return false;
        }
        try {
            if (!EffectAudio.platformSoundSwitchAllows(appContext)) {
                return false;
            }
        } catch (RuntimeException ignored) {
        }
        return audioManager != null
                && EffectAudio.outputHasVolume(appContext, audioManager);
    }

    private void transition(int state, String detail) {
        readinessState = state;
        readinessDetail = detail;
        notifyReadiness();
    }

    private void notifyReadiness() {
        final ReadinessListener listener = readinessListener;
        if (listener != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onReadinessChanged();
                }
            });
        }
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private static final class WaterDropletNativeBridge
            implements S6WaterDropletNative {
        private final com.samsung.android.visualeffect.lock.waterdroplet
                .JniWaterDropletRenderer bridge =
                new com.samsung.android.visualeffect.lock.waterdroplet
                        .JniWaterDropletRenderer();

        @Override
        public void initJni() {
            bridge.Init_PhysicsEngineJNI();
        }

        @Override
        public void deinitJni() {
            bridge.DeInit_PhysicsEngineJNI();
        }

        @Override
        public void initPhysics(int project, int quality, int width, int height) {
            bridge.Init_PhysicsEngine(project, quality, width, height);
        }

        @Override
        public void surfaceChanged(int width, int height) {
            bridge.onSurfaceChangedEvent(width, height);
        }

        @Override
        public void draw() {
            bridge.Draw_PhysicsEngine();
        }

        @Override
        public void touch(
                int id, int count, int type, int[] x, int[] y) {
            bridge.onTouchEvent(id, count, type, x, y);
        }

        @Override
        public void sensor(int type, float x, float y, float z) {
            bridge.onSensorEvent(type, x, y, z);
        }

        @Override
        public void texture(String name, Bitmap bitmap) {
            bridge.SetTexture(name, bitmap);
        }

        @Override
        public void textureColor(String name, Bitmap bitmap) {
            bridge.SetTextureColor(name, bitmap);
        }

        @Override
        public void key(int event) {
            bridge.onKeyEvent(event);
        }

        @Override
        public void custom(int event, float value) {
            bridge.onCustomEvent(event, value);
        }

        @Override
        public void custom(int event, float x, float y, float z) {
            bridge.onCustomEvent(event, x, y, z);
        }

        @Override
        public int isEmpty() {
            return bridge.isEmpty();
        }
    }
}
