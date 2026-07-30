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
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Common DEX-free Java shell for the two original Note 5 physics libraries.
 */
abstract class Note5NativeEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness,
        SensorEventListener, Note5PhysicsGlView.Listener {
    enum Kind {
        COLOUR_DROPLET,
        SPARKLING_BUBBLES
    }

    private static final int EVENT_CLEAR = 90;
    private static final int EVENT_UNLOCK = 91;
    private static final int EVENT_AFFORDANCE = 92;
    private static final int EVENT_RESET_BG_SCALE = 96;
    private static final long SENSOR_REGISTER_DELAY_MS = 10L;
    private static final long DRAG_SOUND_MIN_TIME_MS = 1100L;
    private static final float DRAG_SOUND_DISTANCE_PX = 120f;
    private static final long DRAG_SOUND_FADE_FRAME_MS = 10L;
    private static final float DRAG_SOUND_RELEASE_FADE_STEP = 0.039f;
    private static final float DRAG_SOUND_UNLOCK_FADE_STEP = 0.059f;

    private final Context appContext;
    private final Kind kind;
    private final boolean gyroEnabled;
    private final String tag;
    private final Note5PhysicsGlView glView;
    private final AudioManager audioManager;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int dragSound;
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
    private long downTime;
    private float lastX;
    private float lastY;
    private float lastDragSoundX;
    private float lastDragSoundY;
    private int dragStreamId;
    private float dragSoundVolume = 1f;
    private float dragSoundFadeStep = DRAG_SOUND_RELEASE_FADE_STEP;
    private boolean dragSoundFading;
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
    private Rect pendingAffordance;

    private final Runnable registerSensorRunnable = new Runnable() {
        @Override
        public void run() {
            registerSensor();
        }
    };

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
            } else {
                soundPool.stop(dragStreamId);
                dragStreamId = 0;
                dragSoundFading = false;
            }
        }
    };

    Note5NativeEffectView(Context context, Kind kind, boolean gyroEnabled) {
        super(context);
        this.appContext = context.getApplicationContext();
        this.kind = kind;
        this.gyroEnabled = gyroEnabled && kind == Kind.COLOUR_DROPLET;
        this.tag = kind == Kind.COLOUR_DROPLET
                ? "ColourDropletArm64" : "SparklingBubblesArm64";

        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);

        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        if (kind == Kind.COLOUR_DROPLET) {
            tapSound = soundPool.load(context, R.raw.ve_colourdroplet_tap, 1);
            dragSound = 0;
            unlockSound = soundPool.load(context, R.raw.ve_colourdroplet_unlock, 1);
        } else {
            tapSound = soundPool.load(context, R.raw.ve_sparklingbubbles_tap, 1);
            dragSound = soundPool.load(context, R.raw.ve_sparklingbubbles_drag, 1);
            unlockSound = soundPool.load(context, R.raw.ve_sparklingbubbles_unlock, 1);
        }

        SensorManager manager = this.gyroEnabled
                ? (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE) : null;
        sensorManager = manager;
        accelerometer = manager == null
                ? null : manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        WindowManager windowManager = this.gyroEnabled
                ? (WindowManager) context.getSystemService(Context.WINDOW_SERVICE) : null;
        sensorDisplay = windowManager == null ? null : windowManager.getDefaultDisplay();

        Note5PhysicsNative nativeBridge;
        Bitmap resource1;
        Bitmap resource2 = null;
        String resource1Name;
        String resource2Name = null;
        if (kind == Kind.COLOUR_DROPLET) {
            nativeBridge = new ColourNativeBridge();
            resource1Name = "Normal";
            resource1 = decode(R.drawable.n5_colour_droplet_normal);
            resource2Name = "EdgeDensity";
            resource2 = decode(R.drawable.n5_colour_droplet_edge_density);
        } else {
            nativeBridge = new SparklingNativeBridge();
            resource1Name = "BlurMask";
            resource1 = decode(R.drawable.n5_sparkling_bubbles_blur_mask);
        }
        int width = renderWidth();
        int height = renderHeight();
        int projectKind = getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 1 : 0;
        glView = new Note5PhysicsGlView(context, nativeBridge, effectName(),
                kind == Kind.SPARKLING_BUBBLES, projectKind, width, height,
                resource1Name, resource1, resource2Name, resource2, this);
        addView(glView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        submitBackground(createWhiteBitmap(width, height), false);
        constructed = true;
        Log.i(tag, effectName() + " DEX-free shell constructed");
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        if (kind == Kind.SPARKLING_BUBBLES) {
            return "N5 Sparkling Bubbles";
        }
        return gyroEnabled ? "N5 Colored Droplet + Gyro" : "N5 Colored Droplet";
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
                && (!gyroEnabled || (sensorManager != null && accelerometer != null));
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRender()) {
            return;
        }
        removeCallbacks(affordanceRunnable);
        downTime = SystemClock.uptimeMillis();
        gestureActive = true;
        lastX = screenX;
        lastY = screenY;
        lastDragSoundX = screenX;
        lastDragSoundY = screenY;
        stopDragSoundImmediately();
        play(tapSound);
        glView.touch(0, Math.round(screenX), Math.round(screenY));
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
        glView.touch(2, Math.round(screenX), Math.round(screenY));
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        glView.touch(1, Math.round(lastX), Math.round(lastY));
        startDragSoundFade(DRAG_SOUND_RELEASE_FADE_STEP);
        if (completed) {
            glView.key(EVENT_UNLOCK);
            dragSoundFadeStep = DRAG_SOUND_UNLOCK_FADE_STEP;
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
        startDragSoundFade(DRAG_SOUND_RELEASE_FADE_STEP);
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
        Rect safe = screenRect != null && screenRect.width() > 0 && screenRect.height() > 0
                ? new Rect(screenRect)
                : new Rect(0, 0, renderWidth(), renderHeight());
        pendingAffordance = safe;
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
        removeCallbacks(dragSoundFadeRunnable);
        unregisterSensor();
        stopDragSoundImmediately();
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
        if (gyroEnabled) {
            postDelayed(registerSensorRunnable, SENSOR_REGISTER_DELAY_MS);
        }
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
        transition(STATE_FAILED, "native failure: " + error.getClass().getSimpleName());
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
        int rotation = sensorDisplay == null ? Surface.ROTATION_0 : sensorDisplay.getRotation();
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
                portrait, Math.max(renderWidth(), renderHeight()),
                Math.min(renderWidth(), renderHeight()));
        recycle(backgroundBitmap);
        backgroundBitmap = portrait;
        externalColorSource = external;
        glView.setBackground(
                portrait.copy(Bitmap.Config.ARGB_8888, false), landscape);
    }

    private boolean validBackground() {
        return backgroundBitmap != null && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == renderWidth()
                && backgroundBitmap.getHeight() == renderHeight();
    }

    private Bitmap decode(int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId, options);
        if (bitmap == null) {
            throw new IllegalStateException("Missing Note 5 texture " + resId);
        }
        bitmap.prepareToDraw();
        return bitmap;
    }

    private Bitmap centerCrop(Bitmap source, int width, int height) {
        float sourceRatio = source.getWidth() / (float) source.getHeight();
        float targetRatio = width / (float) height;
        Rect sourceRect;
        if (sourceRatio > targetRatio) {
            int cropWidth = Math.max(1, Math.round(source.getHeight() * targetRatio));
            int left = Math.max(0, (source.getWidth() - cropWidth) / 2);
            sourceRect = new Rect(left, 0, Math.min(source.getWidth(), left + cropWidth),
                    source.getHeight());
        } else {
            int cropHeight = Math.max(1, Math.round(source.getWidth() / targetRatio));
            int top = Math.max(0, (source.getHeight() - cropHeight) / 2);
            sourceRect = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + cropHeight));
        }
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        new Canvas(output).drawBitmap(source, sourceRect, new Rect(0, 0, width, height), paint);
        output.prepareToDraw();
        return output;
    }

    private Bitmap createWhiteBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        return bitmap;
    }

    private int renderWidth() {
        int width = getWidth();
        if (width > 0) {
            return width;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(1, metrics.widthPixels);
    }

    private int renderHeight() {
        int height = getHeight();
        if (height > 0) {
            return height;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.max(1, metrics.heightPixels);
    }

    private boolean canRender() {
        return constructed && !destroyed;
    }

    private void registerSensor() {
        if (!gyroEnabled || destroyed || sensorRegistered
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
            if (Settings.System.getInt(appContext.getContentResolver(),
                    "lockscreen_sounds_enabled", 1) == 0) {
                return false;
            }
        } catch (RuntimeException ignored) {
        }
        return audioManager != null
                && audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM) > 0;
    }

    private void maybeStartDragSound(float x, float y) {
        if (kind != Kind.SPARKLING_BUBBLES || dragSound == 0 || dragStreamId != 0
                || SystemClock.uptimeMillis() - downTime < DRAG_SOUND_MIN_TIME_MS
                || !canPlaySound()) {
            return;
        }
        float dx = x - lastDragSoundX;
        float dy = y - lastDragSoundY;
        if (Math.sqrt(dx * dx + dy * dy) < DRAG_SOUND_DISTANCE_PX) {
            return;
        }
        dragStreamId = soundPool.play(dragSound, 1f, 1f, 1, -1, 1f);
        dragSoundVolume = 1f;
        dragSoundFading = false;
        lastDragSoundX = x;
        lastDragSoundY = y;
    }

    private void startDragSoundFade(float step) {
        if (dragStreamId == 0) {
            return;
        }
        dragSoundFadeStep = step;
        if (!dragSoundFading) {
            dragSoundFading = true;
            dragSoundFadeRunnable.run();
        }
    }

    private void stopDragSoundImmediately() {
        removeCallbacks(dragSoundFadeRunnable);
        dragSoundFading = false;
        dragSoundVolume = 1f;
        if (dragStreamId != 0) {
            soundPool.stop(dragStreamId);
            dragStreamId = 0;
        }
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

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private static final class ColourNativeBridge implements Note5PhysicsNative {
        private final com.samsung.android.visualeffect.lock.colourdroplet
                .JniColourDropletRenderer bridge =
                new com.samsung.android.visualeffect.lock.colourdroplet
                        .JniColourDropletRenderer();

        @Override public void initJni() { bridge.Init_PhysicsEngineJNI(); }
        @Override public void deinitJni() { bridge.DeInit_PhysicsEngineJNI(); }
        @Override public void initPhysics(int p, int q, int w, int h) {
            bridge.Init_PhysicsEngine(p, q, w, h);
        }
        @Override public void surfaceChanged(int w, int h) {
            bridge.onSurfaceChangedEvent(w, h);
        }
        @Override public void draw() { bridge.Draw_PhysicsEngine(); }
        @Override public void touch(int id, int count, int type, int[] x, int[] y) {
            bridge.onTouchEvent(id, count, type, x, y);
        }
        @Override public void sensor(int type, float x, float y, float z) {
            bridge.onSensorEvent(type, x, y, z);
        }
        @Override public void texture(String name, Bitmap bitmap) {
            bridge.SetTexture(name, bitmap);
        }
        @Override public void textureColor(String name, Bitmap bitmap) {
            bridge.SetTextureColor(name, bitmap);
        }
        @Override public void key(int event) { bridge.onKeyEvent(event); }
        @Override public void custom(int event, float value) {
            bridge.onCustomEvent(event, value);
        }
        @Override public void custom(int event, float x, float y, float z) {
            bridge.onCustomEvent(event, x, y, z);
        }
        @Override public int isEmpty() { return bridge.isEmpty(); }
    }

    private static final class SparklingNativeBridge implements Note5PhysicsNative {
        private final com.samsung.android.visualeffect.lock.sparklingbubbles
                .JniSparklingBubblesRenderer bridge =
                new com.samsung.android.visualeffect.lock.sparklingbubbles
                        .JniSparklingBubblesRenderer();

        @Override public void initJni() { bridge.Init_PhysicsEngineJNI(); }
        @Override public void deinitJni() { bridge.DeInit_PhysicsEngineJNI(); }
        @Override public void initPhysics(int p, int q, int w, int h) {
            bridge.Init_PhysicsEngine(p, q, w, h);
        }
        @Override public void surfaceChanged(int w, int h) {
            bridge.onSurfaceChangedEvent(w, h);
        }
        @Override public void draw() { bridge.Draw_PhysicsEngine(); }
        @Override public void touch(int id, int count, int type, int[] x, int[] y) {
            bridge.onTouchEvent(id, count, type, x, y);
        }
        @Override public void sensor(int type, float x, float y, float z) {
            bridge.onSensorEvent(type, x, y, z);
        }
        @Override public void texture(String name, Bitmap bitmap) {
            bridge.SetTexture(name, bitmap);
        }
        @Override public void textureColor(String name, Bitmap bitmap) {
            bridge.SetTextureColor(name, bitmap);
        }
        @Override public void key(int event) { bridge.onKeyEvent(event); }
        @Override public void custom(int event, float value) {
            bridge.onCustomEvent(event, value);
        }
        @Override public void custom(int event, float x, float y, float z) {
            bridge.onCustomEvent(event, x, y, z);
        }
        @Override public int isEmpty() { return bridge.isEmpty(); }
    }
}
