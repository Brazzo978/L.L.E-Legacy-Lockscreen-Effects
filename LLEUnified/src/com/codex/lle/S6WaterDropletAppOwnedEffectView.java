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
 * App-owned ARM64 reconstruction of the Galaxy S6 Water Droplet effect.
 *
 * <p>The two retained wallpaper crops are texture inputs for refraction. The
 * transparent GLES surface never paints either crop as a full-screen
 * background layer.</p>
 */
public final class S6WaterDropletAppOwnedEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer,
        UnlockEffectReadiness, SensorEventListener,
        S6WaterDropletAppOwnedGlView.Listener {
    private static final String TAG = "LLES6WaterOwned";
    private static final long HINT_RETRY_MS = 100L;
    private static final long SENSOR_REGISTER_DELAY_MS = 10L;

    private final Context appContext;
    private final S6WaterDropletAppOwnedGlView glView;
    private final AudioManager audioManager;
    private final SoundPool soundPool;
    private final int tapSound;
    private final Object soundLock = new Object();
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Display sensorDisplay;

    private Bitmap portraitBackground;
    private Bitmap backgroundSourceIdentity;
    private String backgroundSourceName = "none";
    private boolean externalColorSource;
    private boolean constructed;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean pausedForDetach;
    private boolean sensorRegistered;
    private boolean sensorActive;
    private int lastX;
    private int lastY;
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
            if (glView.affordance(target.centerX(), target.centerY())) {
                pendingAffordance = null;
                return;
            }

            /*
             * Samsung does not dispatch event 92 until the renderer has
             * completed its transparent priming draw and two real draws.
             * Surface/background setup is asynchronous, so retain the same
             * target and retry rather than dropping the hint.
             */
            glView.warmUp();
            postDelayed(this, HINT_RETRY_MS);
        }
    };

    private final Runnable registerSensorRunnable = new Runnable() {
        @Override
        public void run() {
            registerSensor();
        }
    };

    public S6WaterDropletAppOwnedEffectView(Context context) {
        super(context);
        appContext = context.getApplicationContext();
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        soundPool.setOnLoadCompleteListener(
                new SoundPool.OnLoadCompleteListener() {
                    @Override
                    public void onLoadComplete(
                            SoundPool completedPool,
                            int sampleId,
                            int status) {
                        handleSoundLoadComplete(
                                completedPool, sampleId, status);
                    }
                });
        audioManager =
                (AudioManager) appContext.getSystemService(
                        Context.AUDIO_SERVICE);
        tapSound =
                soundPool.load(context, R.raw.s6_water_droplet_tap, 1);

        sensorManager =
                (SensorManager) appContext.getSystemService(
                        Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        WindowManager windowManager =
                (WindowManager) context.getSystemService(
                        Context.WINDOW_SERVICE);
        sensorDisplay = windowManager == null
                ? null
                : windowManager.getDefaultDisplay();

        Bitmap normalMap = decodeTexture(
                R.drawable.s6_water_droplet_normal, "normal map");
        Bitmap edgeDensityMap = decodeTexture(
                R.drawable.s6_water_droplet_edge_density,
                "edge-density map");
        int width = renderWidth();
        int height = renderHeight();
        int projectKind =
                getResources().getConfiguration().smallestScreenWidthDp >= 600
                        ? 1
                        : 0;
        glView = new S6WaterDropletAppOwnedGlView(
                context,
                normalMap,
                edgeDensityMap,
                projectKind,
                Math.min(width, height),
                Math.max(width, height),
                this);
        addView(
                glView,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));

        constructed = S6WaterDropletAppOwnedNative.isAvailable();
        if (!constructed) {
            transition(
                    STATE_FAILED,
                    "app-owned native bridge unavailable");
        }
        Log.i(
                TAG,
                "app-owned shell constructed native=" + constructed
                        + " accelerometer=" + (accelerometer != null));
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return EffectAvailability.hasLegacyVendorEffects()
                ? "S6 Water Droplet (LLE renderer)"
                : "S6 Water Droplet";
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
        return constructed
                && !destroyed
                && S6WaterDropletAppOwnedNative.isAvailable();
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRender()) {
            return;
        }
        removeCallbacks(affordanceRunnable);
        pendingAffordance = null;

        int x = (int) screenX;
        int y = (int) screenY;
        long now = SystemClock.uptimeMillis();
        if (!glView.touch(
                MotionEvent.ACTION_DOWN, x, y, now)) {
            return;
        }
        gestureActive = true;
        lastX = x;
        lastY = y;
        play(tapSound, "tap");
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        int x = (int) screenX;
        int y = (int) screenY;
        if (glView.touch(
                MotionEvent.ACTION_MOVE,
                x,
                y,
                SystemClock.uptimeMillis())) {
            lastX = x;
            lastY = y;
        }
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        if (completed) {
            /*
             * Stock's successful terminal path is key event 91 by itself.
             * It does not precede it with touch UP, and the shared LLE audio
             * pipeline owns completion audio, so do not duplicate it here.
             */
            glView.unlock();
        } else {
            glView.touch(
                    MotionEvent.ACTION_UP,
                    lastX,
                    lastY,
                    SystemClock.uptimeMillis());
        }
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        /*
         * The stock JNI ABI accepts only DOWN(0), UP(1), and MOVE(2).
         * Android ACTION_CANCEL(3) must therefore terminate as stock UP.
         */
        glView.touch(
                MotionEvent.ACTION_UP,
                lastX,
                lastY,
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
        if (!destroyed && isAttachedToWindow()) {
            sensorActive = true;
            removeCallbacks(registerSensorRunnable);
            postDelayed(
                    registerSensorRunnable,
                    SENSOR_REGISTER_DELAY_MS);
        }
    }

    @Override
    public void showUnlockAffordance(
            Rect screenRect, long startDelayMs) {
        if (!isReady()) {
            return;
        }
        pendingAffordance =
                screenRect != null && !screenRect.isEmpty()
                        ? new Rect(screenRect)
                        : new Rect(
                                0, 0, renderWidth(), renderHeight());
        removeCallbacks(affordanceRunnable);
        postDelayed(
                affordanceRunnable,
                Math.max(0L, startDelayMs));
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
    public void setBackgroundSourceBitmap(
            Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        submitBackground(source, source, sourceName);
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        if (destroyed) {
            return;
        }
        recycle(portraitBackground);
        portraitBackground = null;
        backgroundSourceIdentity = null;
        backgroundSourceName = "none";
        externalColorSource = false;
        glView.clearBackgroundBitmaps();
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
        recycle(portraitBackground);
        portraitBackground = null;
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
        sensorActive = true;
        removeCallbacks(registerSensorRunnable);
        postDelayed(
                registerSensorRunnable,
                SENSOR_REGISTER_DELAY_MS);
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
    protected void onSizeChanged(
            int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (destroyed
                || width <= 0
                || height <= 0
                || portraitBackground == null
                || portraitBackground.isRecycled()
                || portraitBackground.getWidth() == width
                        && portraitBackground.getHeight() == height) {
            return;
        }
        Bitmap source =
                backgroundSourceIdentity != null
                                && !backgroundSourceIdentity.isRecycled()
                        ? backgroundSourceIdentity
                        : portraitBackground;
        submitBackground(
                source,
                backgroundSourceIdentity,
                backgroundSourceName);
    }

    @Override
    public void onSurfaceReady() {
        transition(
                STATE_SURFACE_READY,
                "transparent EGL surface ready");
    }

    @Override
    public void onResourcesReady() {
        transition(
                STATE_RESOURCES_READY,
                "GPU and refraction textures ready");
    }

    @Override
    public void onSecondDrawReady() {
        transition(
                STATE_FIRST_FRAME_READY,
                "second renderer draw ready");
    }

    @Override
    public void onNativeFailure(Throwable error, String detail) {
        constructed = false;
        String reason =
                detail == null || detail.length() == 0
                        ? error.getClass().getSimpleName()
                        : detail;
        transition(STATE_FAILED, "native failure: " + reason);
        Log.e(
                TAG,
                "app-owned renderer failed: " + reason,
                error);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!sensorRegistered
                || !sensorActive
                || pausedForDetach
                || !isAttachedToWindow()
                || event == null
                || event.sensor == null
                || event.sensor.getType()
                        != Sensor.TYPE_ACCELEROMETER
                || event.values == null
                || event.values.length < 2) {
            return;
        }

        /*
         * This listener is registered from the UI looper. Clamp raw Samsung
         * accelerometer axes first, then rotate them into the current display
         * orientation. The native simulation alone applies stock's force
         * coefficients; Java must not swap or pre-scale the pair.
         */
        float rawX = clampSensor(event.values[0]);
        float rawY = clampSensor(event.values[1]);
        float mappedX;
        float mappedY;
        int rotation =
                sensorDisplay == null
                        ? Surface.ROTATION_0
                        : sensorDisplay.getRotation();
        if (rotation == Surface.ROTATION_90) {
            mappedX = -rawY;
            mappedY = rawX;
        } else if (rotation == Surface.ROTATION_180) {
            mappedX = -rawX;
            mappedY = -rawY;
        } else if (rotation == Surface.ROTATION_270) {
            mappedX = rawY;
            mappedY = -rawX;
        } else {
            mappedX = rawX;
            mappedY = rawY;
        }
        glView.tilt(mappedX, mappedY, event.timestamp);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void submitBackground(
            Bitmap source,
            Bitmap sourceIdentity,
            String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        int width = renderWidth();
        int height = renderHeight();

        /*
         * Produce both stock texture orientations independently from the
         * original source. Deriving landscape from the portrait crop loses
         * the source's side content and changes refraction sampling.
         */
        Bitmap portrait = centerCrop(source, width, height);
        Bitmap landscape = centerCrop(source, height, width);
        Bitmap rendererPortrait =
                portrait.copy(Bitmap.Config.ARGB_8888, false);
        if (rendererPortrait == null) {
            recycle(portrait);
            recycle(landscape);
            return;
        }
        rendererPortrait.prepareToDraw();

        recycle(portraitBackground);
        portraitBackground = portrait;
        backgroundSourceIdentity = sourceIdentity;
        backgroundSourceName =
                sourceName == null ? "external" : sourceName;
        externalColorSource = sourceIdentity != null;

        int centerColor = portrait.getPixel(
                Math.max(0, portrait.getWidth() / 2),
                Math.max(0, portrait.getHeight() / 2));
        Log.i(
                TAG,
                "refraction source=" + backgroundSourceName
                        + " portrait=" + portrait.getWidth()
                        + "x" + portrait.getHeight()
                        + " landscape=" + landscape.getWidth()
                        + "x" + landscape.getHeight()
                        + " center=#"
                        + String.format("%08X", centerColor));
        glView.setBackgroundBitmaps(rendererPortrait, landscape);
    }

    private Bitmap decodeTexture(int resourceId, String label) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap =
                BitmapFactory.decodeResource(
                        getResources(), resourceId, options);
        if (bitmap == null) {
            throw new IllegalStateException(
                    "Missing S6 Water Droplet " + label);
        }
        Bitmap normalized =
                bitmap.getConfig() == Bitmap.Config.ARGB_8888
                        ? bitmap
                        : bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (normalized != bitmap) {
            bitmap.recycle();
        }
        normalized.prepareToDraw();
        return normalized;
    }

    private Bitmap centerCrop(
            Bitmap source, int requestedWidth, int requestedHeight) {
        int width = Math.max(1, requestedWidth);
        int height = Math.max(1, requestedHeight);
        float sourceRatio =
                source.getWidth() / (float) source.getHeight();
        float targetRatio = width / (float) height;
        Rect sourceRect;
        if (sourceRatio > targetRatio) {
            int cropWidth =
                    Math.max(
                            1,
                            Math.round(
                                    source.getHeight() * targetRatio));
            int left =
                    Math.max(
                            0,
                            (source.getWidth() - cropWidth) / 2);
            sourceRect = new Rect(
                    left,
                    0,
                    Math.min(source.getWidth(), left + cropWidth),
                    source.getHeight());
        } else {
            int cropHeight =
                    Math.max(
                            1,
                            Math.round(
                                    source.getWidth() / targetRatio));
            int top =
                    Math.max(
                            0,
                            (source.getHeight() - cropHeight) / 2);
            sourceRect = new Rect(
                    0,
                    top,
                    source.getWidth(),
                    Math.min(source.getHeight(), top + cropHeight));
        }

        Bitmap output =
                Bitmap.createBitmap(
                        width, height, Bitmap.Config.ARGB_8888);
        Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                                | Paint.FILTER_BITMAP_FLAG
                                | Paint.DITHER_FLAG);
        new Canvas(output).drawBitmap(
                source,
                sourceRect,
                new Rect(0, 0, width, height),
                paint);
        output.prepareToDraw();
        return output;
    }

    private boolean validBackground() {
        return portraitBackground != null
                && !portraitBackground.isRecycled()
                && portraitBackground.getWidth() == renderWidth()
                && portraitBackground.getHeight() == renderHeight();
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
        return isReady()
                && isAttachedToWindow()
                && glView.isTouchReady();
    }

    private void registerSensor() {
        if (!sensorActive
                || destroyed
                || sensorRegistered
                || pausedForDetach
                || !isAttachedToWindow()
                || sensorManager == null
                || accelerometer == null) {
            return;
        }
        sensorRegistered =
                sensorManager.registerListener(
                        this,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_GAME);
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
        if (destroyed
                || soundId == 0
                || !canPlayEffectSound()) {
            return;
        }
        synchronized (soundLock) {
            if (!loadedSoundIds.contains(soundId)) {
                pendingSoundIds.add(soundId);
                Log.d(
                        TAG,
                        "sound queued until load source=" + source);
                return;
            }
            playLoadedSoundLocked(soundId, source);
        }
    }

    private void handleSoundLoadComplete(
            SoundPool completedPool,
            int sampleId,
            int status) {
        synchronized (soundLock) {
            if (completedPool != soundPool || destroyed) {
                return;
            }
            if (status != 0) {
                pendingSoundIds.remove(sampleId);
                Log.w(
                        TAG,
                        "sound load failed id=" + sampleId
                                + " status=" + status);
                return;
            }
            loadedSoundIds.add(sampleId);
            if (pendingSoundIds.remove(sampleId)
                    && canPlayEffectSound()) {
                playLoadedSoundLocked(sampleId, "deferred");
            }
        }
    }

    private void playLoadedSoundLocked(
            int soundId, String source) {
        int streamId =
                soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        if (streamId == 0) {
            Log.w(
                    TAG,
                    "sound play rejected source=" + source);
        }
    }

    private boolean canPlayEffectSound() {
        if (!OverlayPrefs.unlockEffectSoundAllowedNow(appContext)) {
            return false;
        }
        try {
            if (Settings.System.getInt(
                    appContext.getContentResolver(),
                    "lockscreen_sounds_enabled",
                    1) == 0) {
                return false;
            }
        } catch (RuntimeException ignored) {
            // Match Samsung's permissive setting lookup.
        }
        if (audioManager == null) {
            return false;
        }
        int ringerMode = audioManager.getRingerMode();
        return ringerMode != AudioManager.RINGER_MODE_SILENT
                && ringerMode != AudioManager.RINGER_MODE_VIBRATE
                && audioManager.getStreamVolume(
                        AudioManager.STREAM_SYSTEM) > 0;
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
