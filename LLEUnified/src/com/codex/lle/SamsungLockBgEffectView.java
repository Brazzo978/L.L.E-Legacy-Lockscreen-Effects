package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.locks.LockSupport;

public class SamsungLockBgEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, DebugFrameCaptureRenderer,
        DebugAbstractTilesCaptureRenderer {
    private static final String TAG = "ChargingSamsungLockBg";
    private static final int CMD_SET_BACKGROUND = 0;
    private static final int CMD_LOCK_AFFORDANCE = 1;
    private static final int CMD_UNLOCK = 2;
    private static final int SAMSUNG_ABSTRACT_TILES = 0;
    private static final int SAMSUNG_GEOMETRIC_MOSAIC = 1;
    private static final float ABSTRACT_BACKGROUND_GAIN = 1.0f;
    private static final float GEOMETRIC_BACKGROUND_GAIN = 1.0f;
    private static final long DRAG_SOUND_LONG_PRESS_MS = 411L;
    private static final long DRAG_SOUND_FADE_STEP_MS = 10L;
    private static final float DRAG_SOUND_RELEASE_FADE_STEP = 0.039f;
    private static final float DRAG_SOUND_UNLOCK_FADE_STEP = 0.059f;
    private static final long AFFORDANCE_DEDUP_WINDOW_MS = 2_500L;
    private static final long ABSTRACT_FRAME_INTERVAL_NS = 33_333_333L;
    private static final long GEOMETRIC_FRAME_INTERVAL_NS = 33_333_333L;
    private static volatile long lastAbstractFrameNs;
    private static volatile long lastGeometricFrameNs;

    private final int samsungEffectId;
    private final String effectName;
    private final float backgroundGain;
    private final AudioManager audioManager;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int dragSound;
    private final int unlockSound;
    private final HandlerThread nativeCommandThread;
    private final Handler nativeCommandHandler;

    private Object effectView;
    private View effectViewAsView;
    private Method handleTouchEvent;
    private Method handleCustomEvent;
    private Method clearScreen;
    private Method removeEffect;
    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private Bitmap lastSentBackgroundBitmap;
    private String backgroundSource = "none";
    private String lastSentBackgroundSource = "";
    private boolean externalColorSource;
    private boolean ready;
    private boolean destroyed;
    private boolean gestureActive;
    private long downTime;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private int dragSoundStreamId;
    private float dragSoundVolume = 1f;
    private float dragSoundFadeStep = DRAG_SOUND_RELEASE_FADE_STEP;
    private boolean dragSoundFading;
    private long lastAffordanceQueuedAt;
    private int debugCaptureGeneration;
    private final Runnable dragSoundFadeRunnable = new Runnable() {
        @Override
        public void run() {
            stepDragSoundFade();
        }
    };

    public static SamsungLockBgEffectView abstractTiles(Context context) {
        return new SamsungLockBgEffectView(
                context,
                SAMSUNG_ABSTRACT_TILES,
                "N4 Abstract Tiles",
                ABSTRACT_BACKGROUND_GAIN);
    }

    public static SamsungLockBgEffectView geometricMosaic(Context context) {
        return new SamsungLockBgEffectView(
                context,
                SAMSUNG_GEOMETRIC_MOSAIC,
                "N4 Geometric Mosaic",
                GEOMETRIC_BACKGROUND_GAIN);
    }

    private SamsungLockBgEffectView(Context context, int effectId, String name,
            float bgGain) {
        super(context);
        long startedAt = SystemClock.uptimeMillis();
        samsungEffectId = effectId;
        effectName = name;
        backgroundGain = bgGain;
        nativeCommandThread = new HandlerThread("LLE-" + name.replace(' ', '-') + "-commands");
        nativeCommandThread.start();
        nativeCommandHandler = new Handler(nativeCommandThread.getLooper());
        audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        long soundStartedAt = SystemClock.uptimeMillis();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        boolean geometricMosaic = samsungEffectId == SAMSUNG_GEOMETRIC_MOSAIC;
        tapSound = soundPool.load(context, geometricMosaic
                ? R.raw.brilliantcut_tap : R.raw.abstracttile_tap, 1);
        dragSound = soundPool.load(context, geometricMosaic
                ? R.raw.brilliantcut_drag : R.raw.abstracttile_drag, 1);
        unlockSound = soundPool.load(context, geometricMosaic
                ? R.raw.brilliantcut_unlock : R.raw.abstracttile_unlock, 1);
        long soundsQueuedMs = SystemClock.uptimeMillis() - soundStartedAt;

        try {
            long nativeStartedAt = SystemClock.uptimeMillis();
            createSamsungEffect(context);
            ready = true;
            Log.i(TAG, effectName + " native renderer loaded elapsedMs="
                    + (SystemClock.uptimeMillis() - startedAt)
                    + " soundsQueuedMs=" + soundsQueuedMs
                    + " nativeCreateMs=" + (SystemClock.uptimeMillis() - nativeStartedAt));
        } catch (Throwable t) {
            ready = false;
            Log.e(TAG, effectName + " native renderer unavailable", t);
        }
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return effectName;
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRender()) {
            return;
        }
        sendBackgroundBitmap();
        long now = SystemClock.uptimeMillis();
        downTime = now;
        gestureActive = true;
        downX = screenX;
        downY = screenY;
        lastX = screenX;
        lastY = screenY;
        stopDragSoundImmediately();
        dragSoundVolume = 1f;
        dragSoundFadeStep = DRAG_SOUND_RELEASE_FADE_STEP;
        playOneShot(tapSound);
        forwardTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
        Log.i(TAG, effectName + " begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY)
                + " bg=" + backgroundSource);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        maybeStartDragSound();
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
        fadeOutDragSound(completed
                ? DRAG_SOUND_UNLOCK_FADE_STEP
                : DRAG_SOUND_RELEASE_FADE_STEP);
        if (completed) {
            sendUnlockCommand();
            playOneShot(unlockSound);
        }
        Log.i(TAG, effectName + " finish completed=" + completed
                + " from=" + Math.round(downX) + "," + Math.round(downY)
                + " to=" + Math.round(lastX) + "," + Math.round(lastY));
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        forwardTouch(MotionEvent.ACTION_CANCEL, lastX, lastY);
        fadeOutDragSound(DRAG_SOUND_RELEASE_FADE_STEP);
        Log.i(TAG, effectName + " cancel");
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
        makeTransparent(effectViewAsView);
        long elapsedMs = SystemClock.uptimeMillis() - startedAt;
        if (elapsedMs >= 4L) {
            Log.i(TAG, effectName + " warmed elapsedMs=" + elapsedMs);
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (!canRender()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (lastAffordanceQueuedAt > 0L
                && now - lastAffordanceQueuedAt < AFFORDANCE_DEDUP_WINDOW_MS) {
            Log.i(TAG, effectName + " duplicate affordance suppressed elapsedMs="
                    + (now - lastAffordanceQueuedAt));
            return;
        }
        sendBackgroundBitmap();
        Rect rect = safeRect(screenRect);
        final HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("StartDelay", Long.valueOf(Math.max(0L, startDelayMs)));
        params.put("Rect", rect);
        lastAffordanceQueuedAt = now;
        nativeCommandHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!canRender()) {
                    return;
                }
                try {
                    handleCustomEvent.invoke(effectView, CMD_LOCK_AFFORDANCE, params);
                } catch (Throwable t) {
                    Log.d(TAG, "affordance command ignored", t);
                }
            }
        });
        Log.i(TAG, effectName + " affordance queued off-main delayMs="
                + Math.max(0L, startDelayMs)
                + " rect=" + rect.left + "," + rect.top + ","
                + rect.right + "," + rect.bottom);
    }

    /** Captures the native GLTextureView only when the ADB debug API requests it. */
    @Override
    public void captureDebugAffordanceFrame(final long phaseMs) {
        if (!canRender()) {
            Log.i(TAG, "native hint capture skipped; renderer unavailable");
            return;
        }
        lastAffordanceQueuedAt = 0L;
        showUnlockAffordance(new Rect(0, 0, getRenderWidth(), getRenderHeight()), 0L);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                final TextureView textureView = findTextureView(effectViewAsView);
                if (textureView == null || !textureView.isAvailable()
                        || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) {
                    Log.i(TAG, "native hint capture skipped; TextureView unavailable phaseMs="
                            + phaseMs);
                    return;
                }
                final Bitmap frame = textureView.getBitmap(
                        textureView.getWidth(), textureView.getHeight());
                if (frame == null) {
                    Log.i(TAG, "native hint capture returned null phaseMs=" + phaseMs);
                    return;
                }
                DebugFrameCaptureFiles.saveAsync(getContext(), frame,
                        "geometric_hint_arm32_" + phaseMs + "ms.png", TAG, phaseMs);
            }
        }, Math.max(0L, Math.min(2_400L, phaseMs)));
    }

    /** Captures a deterministic Abstract Tiles sequence directly from its TextureView. */
    @Override
    public void captureDebugAbstractTilesFrame(final String sequence, final long phaseMs) {
        if (samsungEffectId != SAMSUNG_ABSTRACT_TILES || !canRender()) {
            Log.i(TAG, "native Abstract Tiles capture skipped; renderer unavailable");
            return;
        }
        final int captureGeneration = ++debugCaptureGeneration;
        resetEffect();
        lastAffordanceQueuedAt = 0L;
        final float centerX = getRenderWidth() * 0.5f;
        final float centerY = getRenderHeight() * 0.5f;
        if ("hint".equals(sequence)) {
            showUnlockAffordance(new Rect(0, 0, getRenderWidth(), getRenderHeight()), 0L);
        } else {
            beginGesture(centerX, centerY);
            if ("unlock".equals(sequence) || "unlock-series".equals(sequence)) {
                updateGesture(centerX + Math.min(320f, getRenderWidth() * 0.22f),
                        centerY - Math.min(180f, getRenderHeight() * 0.07f));
                finishGesture(true);
            }
        }
        if ("unlock-series".equals(sequence)) {
            /* forwardTouch() and sendUnlockCommand() share this handler. This
             * marker runs after both native calls, then anchors every capture
             * to one continuous stock animation. */
            nativeCommandHandler.post(new Runnable() {
                @Override
                public void run() {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            scheduleDebugAbstractTilesUnlockSeries(captureGeneration);
                        }
                    });
                }
            });
            return;
        }
        scheduleDebugAbstractTilesCapture(sequence, phaseMs, captureGeneration);
    }

    private void scheduleDebugAbstractTilesUnlockSeries(final int captureGeneration) {
        final long[] phasesMs = {0L, 40L, 80L, 120L, 160L, 200L, 240L, 320L, 400L};
        for (long phaseMs : phasesMs) {
            scheduleDebugAbstractTilesCapture("unlock", phaseMs, captureGeneration);
        }
    }

    private void scheduleDebugAbstractTilesCapture(final String sequence,
            final long phaseMs, final int captureGeneration) {
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (captureGeneration != debugCaptureGeneration) {
                    return;
                }
                final TextureView textureView = findTextureView(effectViewAsView);
                if (textureView == null || !textureView.isAvailable()
                        || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) {
                    Log.i(TAG, "native Abstract Tiles capture skipped; TextureView unavailable"
                            + " sequence=" + sequence + " phaseMs=" + phaseMs);
                    return;
                }
                final Bitmap frame = textureView.getBitmap(
                        textureView.getWidth(), textureView.getHeight());
                if (frame == null) {
                    Log.i(TAG, "native Abstract Tiles capture returned null sequence="
                            + sequence + " phaseMs=" + phaseMs);
                    return;
                }
                DebugFrameCaptureFiles.saveAsync(getContext(), frame,
                        "abstract_tiles_" + sequence + "_arm32_"
                                + phaseMs + "ms.png",
                        TAG, phaseMs);
                if ("touch".equals(sequence)) {
                    cancelGesture();
                }
            }
        }, Math.max(0L, Math.min(2_400L, phaseMs)));
    }

    private TextureView findTextureView(View view) {
        if (view instanceof TextureView) {
            return (TextureView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextureView textureView = findTextureView(group.getChildAt(i));
                if (textureView != null) {
                    return textureView;
                }
            }
        }
        return null;
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
        releaseBackgroundBitmap();
        sendBackgroundBitmap();
    }

    @Override
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && backgroundBitmap == bitmap;
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        removeCallbacks(dragSoundFadeRunnable);
        nativeCommandHandler.removeCallbacksAndMessages(null);
        nativeCommandThread.quitSafely();
        stopDragSoundImmediately();
        soundPool.release();
        SamsungGlTextureShutdown.shutdown(effectViewAsView, TAG);
        if (removeEffect != null && effectView != null) {
            try {
                removeEffect.invoke(effectView);
                Log.i(TAG, effectName + " removeEffect sent after GL shutdown");
            } catch (Throwable t) {
                Log.d(TAG, "removeEffect ignored", t);
            }
        }
        releaseBackgroundBitmap();
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSource = "";
        backgroundSource = "none";
        externalColorSource = false;
        removeAllViews();
        cleanupSamsungState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(new Runnable() {
            @Override
            public void run() {
                makeTransparent(effectViewAsView);
                warmUp();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        gestureActive = false;
        invalidateSentBackground();
        super.onDetachedFromWindow();
    }

    private void createSamsungEffect(Context context) throws Exception {
        long totalStartedAt = SystemClock.uptimeMillis();
        long stepStartedAt = totalStartedAt;
        Class<?> effectViewClass =
                Class.forName("com.samsung.android.visualeffect.EffectView");
        Class<?> dataClass =
                Class.forName("com.samsung.android.visualeffect.EffectDataObj");
        long classLookupMs = SystemClock.uptimeMillis() - stepStartedAt;

        stepStartedAt = SystemClock.uptimeMillis();
        effectView = effectViewClass.getConstructor(Context.class).newInstance(context);
        effectViewAsView = (View) effectView;
        addView(effectViewAsView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        long constructMs = SystemClock.uptimeMillis() - stepStartedAt;

        stepStartedAt = SystemClock.uptimeMillis();
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
        removeEffect = optionalMethod(effectViewClass, "removeEffect");
        long methodLookupMs = SystemClock.uptimeMillis() - stepStartedAt;

        stepStartedAt = SystemClock.uptimeMillis();
        Object data = dataClass.getConstructor().newInstance();
        dataClass.getMethod("setEffect", int.class).invoke(data, samsungEffectId);
        long dataMs = SystemClock.uptimeMillis() - stepStartedAt;

        stepStartedAt = SystemClock.uptimeMillis();
        setEffect.invoke(effectView, samsungEffectId);
        long setEffectMs = SystemClock.uptimeMillis() - stepStartedAt;
        makeTransparent(effectViewAsView);
        stepStartedAt = SystemClock.uptimeMillis();
        init.invoke(effectView, data);
        long initMs = SystemClock.uptimeMillis() - stepStartedAt;
        Log.i(TAG, effectName + " native init profile"
                + " classMs=" + classLookupMs
                + " constructMs=" + constructMs
                + " methodMs=" + methodLookupMs
                + " dataMs=" + dataMs
                + " setEffectMs=" + setEffectMs
                + " initMs=" + initMs
                + " totalMs=" + (SystemClock.uptimeMillis() - totalStartedAt));
        post(new Runnable() {
            @Override
            public void run() {
                makeTransparent(effectViewAsView);
            }
        });
    }

    private Method optionalMethod(Class<?> owner, String methodName, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Keeps Abstract Tiles' frame-stepped simulation at the stock S4 ~30 Hz cadence. */
    public static void paceAbstractTileFrame() {
        long now = System.nanoTime();
        long previous = lastAbstractFrameNs;
        if (previous > 0L) {
            long remaining = ABSTRACT_FRAME_INTERVAL_NS - (now - previous);
            if (remaining > 1_000_000L) {
                LockSupport.parkNanos(remaining);
            }
        }
        lastAbstractFrameNs = System.nanoTime();
    }

    /** Keeps Geometric Mosaic at the measured stock S4 SystemUI ~30 Hz cadence. */
    public static void paceGeometricMosaicFrame() {
        long now = System.nanoTime();
        long previous = lastGeometricFrameNs;
        if (previous > 0L) {
            long remaining = GEOMETRIC_FRAME_INTERVAL_NS - (now - previous);
            if (remaining > 1_000_000L) {
                LockSupport.parkNanos(remaining);
            }
        }
        lastGeometricFrameNs = System.nanoTime();
    }

    private void sendBackgroundBitmap() {
        if (!ready || handleCustomEvent == null || effectView == null) {
            return;
        }
        long startedAt = SystemClock.uptimeMillis();
        Bitmap bitmap = getBackgroundBitmap();
        long getBitmapMs = SystemClock.uptimeMillis() - startedAt;
        if (bitmap == lastSentBackgroundBitmap
                && backgroundSource.equals(lastSentBackgroundSource)) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("Bitmap", bitmap);
            long commandStartedAt = SystemClock.uptimeMillis();
            handleCustomEvent.invoke(effectView, CMD_SET_BACKGROUND, params);
            lastSentBackgroundBitmap = bitmap;
            lastSentBackgroundSource = backgroundSource;
            Log.i(TAG, effectName + " background sent source=" + backgroundSource
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt)
                    + " getBitmapMs=" + getBitmapMs
                    + " commandMs=" + (SystemClock.uptimeMillis() - commandStartedAt));
        } catch (Throwable t) {
            Log.d(TAG, "background command ignored", t);
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

    private void invalidateSentBackground() {
        lastSentBackgroundBitmap = null;
        lastSentBackgroundSource = "";
    }

    private void cleanupSamsungState() {
        ready = false;
        gestureActive = false;
        effectView = null;
        effectViewAsView = null;
        handleTouchEvent = null;
        handleCustomEvent = null;
        clearScreen = null;
        removeEffect = null;
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
        backgroundBitmap = createTransparentBitmap(width, height);
        ownsBackgroundBitmap = true;
        backgroundSource = "transparent_fallback";
        externalColorSource = false;
        backgroundBitmap.prepareToDraw();
        Log.i(TAG, effectName + " fallback background prepared size="
                + backgroundBitmap.getWidth() + "x" + backgroundBitmap.getHeight());
        return backgroundBitmap;
    }

    private void replaceBackgroundBitmap(Bitmap source, String sourceName) {
        long startedAt = SystemClock.uptimeMillis();
        int width = Math.max(1, getRenderWidth());
        int height = Math.max(1, getRenderHeight());
        long cropStartedAt = SystemClock.uptimeMillis();
        boolean borrow = backgroundGain == 1f
                && BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, width, height);
        Bitmap next = borrow ? source : createCenterCropBitmap(source, width, height);
        long cropMs = SystemClock.uptimeMillis() - cropStartedAt;
        long prepareStartedAt = SystemClock.uptimeMillis();
        next.prepareToDraw();
        long prepareMs = SystemClock.uptimeMillis() - prepareStartedAt;
        releaseBackgroundBitmap();
        backgroundBitmap = next;
        ownsBackgroundBitmap = !borrow;
        backgroundSource = sourceName;
        externalColorSource = true;
        invalidateSentBackground();
        Log.i(TAG, effectName + " background replaced source=" + backgroundSource
                + " size=" + backgroundBitmap.getWidth()
                + "x" + backgroundBitmap.getHeight()
                + " gain=" + backgroundGain
                + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt)
                + " cropMs=" + cropMs
                + " prepareMs=" + prepareMs);
    }

    private Bitmap createCenterCropBitmap(Bitmap source, int width, int height) {
        if (source.getWidth() == width
                && source.getHeight() == height
                && backgroundGain == 1f) {
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

    private Bitmap createTransparentBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);
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
                Log.w(TAG, effectName + " touch slow action="
                        + actionName(action)
                        + " elapsedMs=" + elapsedMs);
            }
            event.recycle();
        }
    }

    private void maybeStartDragSound() {
        if (dragSoundStreamId != 0
                || downTime <= 0L
                || SystemClock.uptimeMillis() - downTime <= DRAG_SOUND_LONG_PRESS_MS
                || !canPlaySound()) {
            return;
        }
        dragSoundVolume = 1f;
        dragSoundFading = false;
        removeCallbacks(dragSoundFadeRunnable);
        dragSoundStreamId = soundPool.play(
                dragSound,
                dragSoundVolume,
                dragSoundVolume,
                0,
                -1,
                1f);
        Log.i(TAG, effectName + " drag sound loop started stream=" + dragSoundStreamId);
    }

    private void fadeOutDragSound(float fadeStep) {
        if (dragSoundStreamId == 0) {
            return;
        }
        dragSoundFadeStep = fadeStep;
        dragSoundFading = true;
        removeCallbacks(dragSoundFadeRunnable);
        post(dragSoundFadeRunnable);
    }

    private void stepDragSoundFade() {
        if (!dragSoundFading || dragSoundStreamId == 0 || destroyed) {
            return;
        }
        dragSoundVolume = Math.max(0f, dragSoundVolume - dragSoundFadeStep);
        soundPool.setVolume(dragSoundStreamId, dragSoundVolume, dragSoundVolume);
        if (dragSoundVolume > 0f) {
            postDelayed(dragSoundFadeRunnable, DRAG_SOUND_FADE_STEP_MS);
            return;
        }
        stopDragSoundImmediately();
    }

    private void stopDragSoundImmediately() {
        removeCallbacks(dragSoundFadeRunnable);
        dragSoundFading = false;
        if (dragSoundStreamId != 0) {
            soundPool.stop(dragSoundStreamId);
            dragSoundStreamId = 0;
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

    private void playOneShot(int soundId) {
        if (!destroyed && soundId != 0 && canPlaySound()) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private boolean canPlaySound() {
        if (!OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            return false;
        }
        if (Settings.System.getInt(getContext().getContentResolver(),
                "lockscreen_sounds_enabled", 1) == 0) {
            return false;
        }
        return audioManager == null
                || audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM) > 0;
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
