package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

/**
 * App-owned ARM64 reconstruction of the Note 5 Sparkling Bubbles effect.
 *
 * <p>The background supplied by LLE is used only as a colour map by the transparent GLES
 * renderer. It is never drawn as a fullscreen layer.</p>
 */
public final class SparklingBubblesAppOwnedEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer,
        UnlockEffectReadiness, SparklingBubblesAppOwnedGlView.Listener {
    private static final String TAG = "LLESparklingBubbles";
    private static final long HINT_MINIMUM_RENDER_MS = 500L;
    private static final long DRAG_SOUND_MIN_TIME_MS = 1100L;
    private static final float DRAG_SOUND_DISTANCE_PX = 120f;
    private static final long DRAG_SOUND_FADE_FRAME_MS = 10L;
    private static final float DRAG_SOUND_RELEASE_FADE_STEP = 0.039f;
    private static final float DRAG_SOUND_UNLOCK_FADE_STEP = 0.059f;

    private final Context appContext;
    private final SparklingBubblesAppOwnedGlView glView;
    private final AudioManager audioManager;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int dragSound;
    private final int unlockSound;

    private Bitmap backgroundBitmap;
    private Bitmap backgroundSourceIdentity;
    private String backgroundSourceName = "fallback";
    private boolean externalColorSource;
    private boolean constructed;
    private boolean destroyed;
    private boolean gestureActive;
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
            glView.affordance(target, HINT_MINIMUM_RENDER_MS);
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

    public SparklingBubblesAppOwnedEffectView(Context context) {
        super(context);
        appContext = context.getApplicationContext();
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        tapSound = soundPool.load(context, R.raw.ve_sparklingbubbles_tap, 1);
        dragSound = soundPool.load(context, R.raw.ve_sparklingbubbles_drag, 1);
        unlockSound = soundPool.load(context, R.raw.ve_sparklingbubbles_unlock, 1);

        Bitmap blurMask = decodeMask();
        glView = new SparklingBubblesAppOwnedGlView(context, blurMask, this);
        addView(glView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        constructed = SparklingBubblesNative.isAvailable();
        if (!constructed) {
            transition(STATE_FAILED, "app-owned native bridge unavailable");
        }
        Log.i(TAG, "app-owned shell constructed native=" + constructed);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return EffectAvailability.hasLegacyVendorEffects()
                ? "N5 Sparkling Bubbles (LLE renderer)"
                : "N5 Sparkling Bubbles";
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
        return constructed && !destroyed && SparklingBubblesNative.isAvailable();
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (!canRender()) {
            return;
        }
        removeCallbacks(affordanceRunnable);
        pendingAffordance = null;
        downTime = SystemClock.uptimeMillis();
        gestureActive = true;
        lastX = screenX;
        lastY = screenY;
        lastDragSoundX = screenX;
        lastDragSoundY = screenY;
        stopDragSoundImmediately();
        play(tapSound);
        glView.touch(android.view.MotionEvent.ACTION_DOWN, screenX, screenY, downTime);
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
        glView.touch(android.view.MotionEvent.ACTION_MOVE, screenX, screenY,
                SystemClock.uptimeMillis());
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        glView.touch(android.view.MotionEvent.ACTION_UP, lastX, lastY,
                SystemClock.uptimeMillis());
        startDragSoundFade(DRAG_SOUND_RELEASE_FADE_STEP);
        if (completed) {
            glView.unlock();
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
        glView.touch(android.view.MotionEvent.ACTION_CANCEL, lastX, lastY,
                SystemClock.uptimeMillis());
        startDragSoundFade(DRAG_SOUND_RELEASE_FADE_STEP);
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        pendingAffordance = null;
        removeCallbacks(affordanceRunnable);
        stopDragSoundImmediately();
        if (!destroyed) {
            glView.resetEffect();
        }
    }

    @Override
    public void warmUp() {
        if (!destroyed) {
            if (pausedForDetach && isAttachedToWindow()) {
                pausedForDetach = false;
                glView.onResume();
            }
            glView.warmUp();
        }
    }

    void parkForReuse() {
        resetEffect();
    }

    void resumeForReuse() {
        warmUp();
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
        submitBackground(
                centerCrop(source, renderWidth(), renderHeight()),
                true,
                source,
                sourceName);
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        if (!destroyed) {
            recycle(backgroundBitmap);
            backgroundBitmap = null;
            backgroundSourceIdentity = null;
            backgroundSourceName = "none";
            externalColorSource = false;
            glView.clearBackgroundBitmap();
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
        removeCallbacks(dragSoundFadeRunnable);
        stopDragSoundImmediately();
        soundPool.release();
        glView.destroyRenderer();
        removeAllViews();
        recycle(backgroundBitmap);
        backgroundBitmap = null;
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
        warmUp();
    }

    @Override
    protected void onDetachedFromWindow() {
        gestureActive = false;
        pendingAffordance = null;
        removeCallbacks(affordanceRunnable);
        startDragSoundFade(DRAG_SOUND_RELEASE_FADE_STEP);
        if (!destroyed) {
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
        submitBackground(
                centerCrop(backgroundBitmap, width, height),
                externalColorSource,
                backgroundSourceIdentity,
                backgroundSourceName);
    }

    @Override
    public void onSurfaceReady() {
        transition(STATE_SURFACE_READY, "transparent EGL surface ready");
    }

    @Override
    public void onResourcesReady() {
        transition(STATE_RESOURCES_READY, "GPU and colour-map textures ready");
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

    private void submitBackground(Bitmap mapped, boolean external) {
        submitBackground(mapped, external, null, external ? "external" : "fallback");
    }

    private void submitBackground(Bitmap mapped, boolean external,
            Bitmap sourceIdentity, String sourceName) {
        if (mapped == null) {
            return;
        }
        recycle(backgroundBitmap);
        backgroundBitmap = mapped;
        backgroundSourceIdentity = sourceIdentity;
        backgroundSourceName = sourceName == null ? "external" : sourceName;
        externalColorSource = external;
        int centerColor = mapped.getPixel(
                Math.max(0, mapped.getWidth() / 2),
                Math.max(0, mapped.getHeight() / 2));
        Log.i(TAG, "colour map source=" + backgroundSourceName
                + " size=" + mapped.getWidth() + "x" + mapped.getHeight()
                + " center=#" + String.format("%08X", centerColor));
        glView.setBackgroundBitmap(mapped.copy(Bitmap.Config.ARGB_8888, false));
    }

    private Bitmap decodeMask() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.n5_sparkling_bubbles_blur_mask, options);
        if (bitmap == null) {
            throw new IllegalStateException("Missing Sparkling Bubbles blur mask");
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

    private void maybeStartDragSound(float x, float y) {
        if (dragStreamId != 0
                || SystemClock.uptimeMillis() - downTime < DRAG_SOUND_MIN_TIME_MS
                || !canPlayEffectSound()) {
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

    private void play(int soundId) {
        if (!destroyed && soundId != 0 && canPlayEffectSound()) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
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
}
