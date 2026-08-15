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

import java.util.HashSet;
import java.util.Set;

/**
 * App-owned ARM64 reconstruction of the Note 5 Coloured Droplet effect.
 *
 * <p>The supplied lockscreen bitmap is only a color/refraction map. The
 * transparent GLES surface never draws it as an opaque full-screen layer.</p>
 */
public final class ColourDropletAppOwnedEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer,
        UnlockEffectReadiness, SensorEventListener,
        ColourDropletAppOwnedGlView.Listener {
    private static final String TAG = "LLEColourDroplet";
    private static final long HINT_MINIMUM_RENDER_MS = 500L;
    private static final long SENSOR_REGISTER_DELAY_MS = 10L;

    private final Context appContext;
    private final boolean gyroEnabled;
    private final ColourDropletAppOwnedGlView glView;
    private final AudioManager audioManager;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;
    private final Object soundLock = new Object();
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Display sensorDisplay;

    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private Bitmap backgroundSourceIdentity;
    private String backgroundSourceName = "none";
    private boolean externalColorSource;
    private boolean constructed;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean pausedForDetach;
    private boolean sensorRegistered;
    private boolean sensorActive;
    private long downTime;
    private float lastX;
    private float lastY;
    private Rect pendingAffordance;
    private volatile int readinessState = STATE_CONSTRUCTED;
    private volatile String readinessDetail = "constructed";
    private volatile ReadinessListener readinessListener;

    private final Runnable affordanceRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || pendingAffordance == null) {
                return;
            }
            Rect target = pendingAffordance;
            pendingAffordance = null;
            glView.affordance(
                    target.centerX(), target.centerY(), HINT_MINIMUM_RENDER_MS);
        }
    };

    private final Runnable registerSensorRunnable = new Runnable() {
        @Override
        public void run() {
            registerSensor();
        }
    };

    public ColourDropletAppOwnedEffectView(Context context) {
        this(context, false);
    }

    public ColourDropletAppOwnedEffectView(Context context, boolean gyroEnabled) {
        this(context, gyroEnabled, false);
    }

    /**
     * The experimental mode is latched for this renderer lifetime. Callers
     * recreate the effect on a preference change; display-rate changes inside
     * that lifetime remain live and do not reset the simulation.
     */
    public ColourDropletAppOwnedEffectView(
            Context context, boolean gyroEnabled, boolean nativeRefreshPhysics) {
        this(context, gyroEnabled, nativeRefreshPhysics, 1.0f);
    }

    /** Experimental speed multiplier is latched with the renderer mode. */
    public ColourDropletAppOwnedEffectView(
            Context context,
            boolean gyroEnabled,
            boolean nativeRefreshPhysics,
            float nativeRefreshSpeedMultiplier) {
        super(context);
        appContext = context.getApplicationContext();
        this.gyroEnabled = gyroEnabled;
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool completedPool,
                    int sampleId, int status) {
                handleSoundLoadComplete(completedPool, sampleId, status);
            }
        });
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        tapSound = soundPool.load(context, R.raw.ve_colourdroplet_tap, 1);
        unlockSound = soundPool.load(context, R.raw.ve_colourdroplet_unlock, 1);

        SensorManager manager = gyroEnabled
                ? (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE) : null;
        sensorManager = manager;
        accelerometer = manager == null
                ? null : manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        WindowManager windowManager = gyroEnabled
                ? (WindowManager) context.getSystemService(Context.WINDOW_SERVICE) : null;
        sensorDisplay = windowManager == null ? null : windowManager.getDefaultDisplay();

        Bitmap normalMap = decodeTexture(
                R.drawable.n5_colour_droplet_normal, "normal map");
        Bitmap edgeDensityMap = decodeTexture(
                R.drawable.n5_colour_droplet_edge_density, "edge-density map");
        int projectKind =
                getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 1 : 0;
        glView = new ColourDropletAppOwnedGlView(
                context,
                normalMap,
                edgeDensityMap,
                projectKind,
                renderWidth(),
                renderHeight(),
                this,
                nativeRefreshPhysics,
                nativeRefreshSpeedMultiplier);
        addView(glView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        constructed = ColourDropletNative.isAvailable()
                && (!gyroEnabled || sensorManager != null && accelerometer != null);
        if (!constructed) {
            transition(STATE_FAILED, gyroEnabled && accelerometer == null
                    ? "accelerometer unavailable"
                    : "app-owned native bridge unavailable");
        }
        Log.i(TAG, "app-owned shell constructed native=" + constructed
                + " gyro=" + gyroEnabled
                + " nativeRefreshPhysics=" + nativeRefreshPhysics
                + " nativeRefreshSpeedMultiplier=" + nativeRefreshSpeedMultiplier);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        String name = gyroEnabled
                ? "N5 Colored Droplet + Gyro"
                : "N5 Colored Droplet";
        return EffectAvailability.hasLegacyVendorEffects()
                ? name + " (LLE renderer)"
                : name;
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
        return constructed && !destroyed && ColourDropletNative.isAvailable()
                && (!gyroEnabled || sensorManager != null && accelerometer != null);
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!isReady() || !isAttachedToWindow()) {
            return;
        }
        removeCallbacks(affordanceRunnable);
        pendingAffordance = null;
        downTime = SystemClock.uptimeMillis();
        gestureActive = true;
        lastX = screenX;
        lastY = screenY;
        play(tapSound, "tap");
        glView.touch(
                MotionEvent.ACTION_DOWN,
                Math.round(screenX),
                Math.round(screenY),
                downTime);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        lastX = screenX;
        lastY = screenY;
        glView.touch(
                MotionEvent.ACTION_MOVE,
                Math.round(screenX),
                Math.round(screenY),
                SystemClock.uptimeMillis());
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        glView.touch(
                MotionEvent.ACTION_UP,
                Math.round(lastX),
                Math.round(lastY),
                SystemClock.uptimeMillis());
        if (completed) {
            glView.unlock();
            play(unlockSound, "unlock");
        }
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        glView.touch(
                MotionEvent.ACTION_CANCEL,
                Math.round(lastX),
                Math.round(lastY),
                SystemClock.uptimeMillis());
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        pendingAffordance = null;
        removeCallbacks(affordanceRunnable);
        if (!destroyed) {
            glView.resetEffect();
        }
    }

    @Override
    public void warmUp() {
        if (destroyed) {
            return;
        }
        if (pausedForDetach && isAttachedToWindow()) {
            pausedForDetach = false;
            glView.onResume();
        }
        glView.warmUp();
    }

    void parkForReuse() {
        gestureActive = false;
        pendingAffordance = null;
        removeCallbacks(affordanceRunnable);
        sensorActive = false;
        removeCallbacks(registerSensorRunnable);
        unregisterSensor();
        if (!destroyed) {
            glView.parkForReuse();
        }
    }

    void resumeForReuse() {
        warmUp();
        if (gyroEnabled) {
            sensorActive = true;
            removeCallbacks(registerSensorRunnable);
            postDelayed(registerSensorRunnable, SENSOR_REGISTER_DELAY_MS);
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (!canRender()) {
            return;
        }
        Rect safe = screenRect != null && !screenRect.isEmpty()
                ? new Rect(screenRect)
                : new Rect(0, 0, renderWidth(), renderHeight());
        pendingAffordance = safe;
        removeCallbacks(affordanceRunnable);
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
        glView.warmUp();
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalColorSource && validBackground();
    }

    @Override
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && bitmap == backgroundSourceIdentity;
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        boolean borrowed = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, renderWidth(), renderHeight());
        submitBackground(
                borrowed ? source : centerCrop(source, renderWidth(), renderHeight()),
                !borrowed,
                source,
                sourceName);
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        if (destroyed) {
            return;
        }
        releaseBackgroundBitmap();
        backgroundSourceIdentity = null;
        backgroundSourceName = "none";
        externalColorSource = false;
        glView.clearBackgroundBitmap();
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        removeCallbacks(affordanceRunnable);
        sensorActive = false;
        removeCallbacks(registerSensorRunnable);
        unregisterSensor();
        synchronized (soundLock) {
            soundPool.setOnLoadCompleteListener(null);
            loadedSoundIds.clear();
            pendingSoundIds.clear();
        }
        soundPool.release();
        glView.destroyRenderer();
        removeAllViews();
        releaseBackgroundBitmap();
        backgroundSourceIdentity = null;
        externalColorSource = false;
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
            sensorActive = true;
            removeCallbacks(registerSensorRunnable);
            postDelayed(registerSensorRunnable, SENSOR_REGISTER_DELAY_MS);
        }
        warmUp();
    }

    @Override
    protected void onDetachedFromWindow() {
        gestureActive = false;
        pendingAffordance = null;
        removeCallbacks(affordanceRunnable);
        sensorActive = false;
        removeCallbacks(registerSensorRunnable);
        unregisterSensor();
        if (!destroyed) {
            glView.discardPendingCommands();
            glView.pauseRenderer();
            glView.onPause();
            pausedForDetach = true;
            transition(STATE_DETACHED, "GLSurfaceView detached");
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (destroyed || width <= 0 || height <= 0
                || backgroundBitmap == null || backgroundBitmap.isRecycled()
                || backgroundBitmap.getWidth() == width
                && backgroundBitmap.getHeight() == height) {
            return;
        }
        Bitmap source = backgroundSourceIdentity != null
                && !backgroundSourceIdentity.isRecycled()
                ? backgroundSourceIdentity : backgroundBitmap;
        submitBackground(
                centerCrop(source, width, height),
                true,
                backgroundSourceIdentity,
                backgroundSourceName);
    }

    @Override
    public void onSurfaceReady() {
        transition(STATE_SURFACE_READY, "transparent EGL surface ready");
    }

    @Override
    public void onResourcesReady() {
        transition(STATE_RESOURCES_READY, "GPU and color-map textures ready");
    }

    @Override
    public void onFirstFrame() {
        transition(STATE_FIRST_FRAME_READY, "first transparent frame drawn");
    }

    @Override
    public void onNativeFailure(Throwable error, String detail) {
        constructed = false;
        String reason = detail == null || detail.length() == 0
                ? error.getClass().getSimpleName() : detail;
        transition(STATE_FAILED, "native failure: " + reason);
        Log.e(TAG, "app-owned renderer failed: " + reason, error);
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
        int rotation = sensorDisplay == null
                ? Surface.ROTATION_0 : sensorDisplay.getRotation();
        /*
         * Stock physics stores world Y bottom-up and its draw helper renders
         * `worldHeight - particleY`. The app-owned simulation stores Android
         * screen Y top-down directly, so invert only the already rotation-
         * remapped Y value at this gyro-only host boundary.
         */
        if (rotation == Surface.ROTATION_90) {
            glView.sensor(event.sensor.getType(), -y, -x, z);
        } else if (rotation == Surface.ROTATION_180) {
            glView.sensor(event.sensor.getType(), -x, y, z);
        } else if (rotation == Surface.ROTATION_270) {
            glView.sensor(event.sensor.getType(), y, x, z);
        } else {
            glView.sensor(event.sensor.getType(), x, -y, z);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void submitBackground(
            Bitmap mapped, boolean ownsMapped,
            Bitmap sourceIdentity, String sourceName) {
        if (mapped == null) {
            return;
        }
        releaseBackgroundBitmap();
        backgroundBitmap = mapped;
        ownsBackgroundBitmap = ownsMapped;
        backgroundSourceIdentity = sourceIdentity;
        backgroundSourceName = sourceName == null ? "external" : sourceName;
        externalColorSource = true;
        int centerColor = mapped.getPixel(
                Math.max(0, mapped.getWidth() / 2),
                Math.max(0, mapped.getHeight() / 2));
        Log.i(TAG, "color map source=" + backgroundSourceName
                + " size=" + mapped.getWidth() + "x" + mapped.getHeight()
                + " ownership=" + (ownsMapped ? "private" : "shared_cache_borrow")
                + " center=#" + String.format("%08X", centerColor));
        glView.setBackgroundBitmap(mapped.copy(Bitmap.Config.ARGB_8888, false));
    }

    String backgroundMemoryDebugSnapshot() {
        return "colour_view_background_dimensions=" + dimensions(backgroundBitmap) + '\n'
                + "colour_view_background_ownership="
                + (backgroundBitmap == null ? "none"
                        : ownsBackgroundBitmap ? "private" : "shared_cache_borrow") + '\n'
                + "colour_view_background_allocation_bytes="
                + allocationBytes(backgroundBitmap) + '\n'
                + glView.backgroundMemoryDebugSnapshot();
    }

    private static String dimensions(Bitmap bitmap) {
        return bitmap == null || bitmap.isRecycled()
                ? "unavailable" : bitmap.getWidth() + "x" + bitmap.getHeight();
    }

    private static long allocationBytes(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 0L;
        }
        try {
            return bitmap.getAllocationByteCount();
        } catch (RuntimeException ignored) {
            return (long) bitmap.getRowBytes() * bitmap.getHeight();
        }
    }

    private void releaseBackgroundBitmap() {
        if (ownsBackgroundBitmap) {
            recycle(backgroundBitmap);
        }
        backgroundBitmap = null;
        ownsBackgroundBitmap = false;
    }

    private Bitmap decodeTexture(int resourceId, String label) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeResource(
                getResources(), resourceId, options);
        if (bitmap == null) {
            throw new IllegalStateException("Missing Coloured Droplet " + label);
        }
        Bitmap normalized = bitmap.getConfig() == Bitmap.Config.ARGB_8888
                ? bitmap : bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (normalized != bitmap) {
            bitmap.recycle();
        }
        normalized.prepareToDraw();
        return normalized;
    }

    private Bitmap centerCrop(Bitmap source, int width, int height) {
        float sourceRatio = source.getWidth() / (float) source.getHeight();
        float targetRatio = width / (float) height;
        Rect sourceRect;
        if (sourceRatio > targetRatio) {
            int cropWidth = Math.max(1, Math.round(source.getHeight() * targetRatio));
            int left = Math.max(0, (source.getWidth() - cropWidth) / 2);
            sourceRect = new Rect(left, 0,
                    Math.min(source.getWidth(), left + cropWidth), source.getHeight());
        } else {
            int cropHeight = Math.max(1, Math.round(source.getWidth() / targetRatio));
            int top = Math.max(0, (source.getHeight() - cropHeight) / 2);
            sourceRect = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + cropHeight));
        }
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        new Canvas(output).drawBitmap(
                source, sourceRect, new Rect(0, 0, width, height), paint);
        output.prepareToDraw();
        return output;
    }

    private boolean validBackground() {
        return backgroundBitmap != null && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == renderWidth()
                && backgroundBitmap.getHeight() == renderHeight();
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
        return isReady() && glView.isRendererReady();
    }

    private void registerSensor() {
        if (!gyroEnabled || !sensorActive || destroyed || sensorRegistered
                || pausedForDetach
                || !isAttachedToWindow()
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

    private void play(int soundId, String source) {
        if (destroyed || soundId == 0 || !canPlayEffectSound()) {
            return;
        }
        synchronized (soundLock) {
            if (!loadedSoundIds.contains(soundId)) {
                pendingSoundIds.add(soundId);
                Log.d(TAG, "sound queued until load source=" + source);
                return;
            }
            playLoadedSoundLocked(soundId, source);
        }
    }

    private void handleSoundLoadComplete(
            SoundPool completedPool, int sampleId, int status) {
        synchronized (soundLock) {
            if (completedPool != soundPool || destroyed) {
                return;
            }
            if (status != 0) {
                pendingSoundIds.remove(sampleId);
                Log.w(TAG, "sound load failed id=" + sampleId + " status=" + status);
                return;
            }
            loadedSoundIds.add(sampleId);
            if (pendingSoundIds.remove(sampleId) && canPlayEffectSound()) {
                playLoadedSoundLocked(sampleId, "deferred");
            }
        }
    }

    private void playLoadedSoundLocked(int soundId, String source) {
        int streamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        if (streamId == 0) {
            Log.w(TAG, "sound play rejected source=" + source);
        }
    }

    private boolean canPlayEffectSound() {
        if (!OverlayPrefs.unlockEffectSoundAllowedNow(appContext)) {
            return false;
        }
        try {
            if (!EffectAudio.platformSoundSwitchAllows(appContext)) {
                return false;
            }
        } catch (RuntimeException ignored) {
            // Match Samsung's permissive behavior when the setting cannot be queried.
        }
        if (audioManager == null) {
            return false;
        }
        return EffectAudio.ringerModeAllows(appContext, audioManager)
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
}
