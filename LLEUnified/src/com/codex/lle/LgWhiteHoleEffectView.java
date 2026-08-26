package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.SoundPool;
import android.os.SystemClock;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

/**
 * Tester-only White Hole restoration.
 *
 * <p>The renderer consumes L.L.E's dedicated pre-lock underlay. Audio is restored from the
 * archival XLocker effect package supplied and authorized by the original project author.</p>
 */
public final class LgWhiteHoleEffectView extends View
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    static final long CANCEL_MS = 300L;
    static final long COMPLETE_MS = 400L;
    static final long COMPLETE_SOLID_HOLD_MS = 500L;
    static final long COMPLETE_FADE_MS = 300L;
    static final long COMPLETE_HOLD_MS = COMPLETE_SOLID_HOLD_MS + COMPLETE_FADE_MS;
    private static final long AFFORDANCE_HOLD_MS = 150L;
    private static final float CORONA_HALF_SIZE_PER_RADIUS = 1.515f;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint coronaPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clipPath = new Path();
    private final Rect sourceRect = new Rect();
    private final RectF destinationRect = new RectF();
    private final RectF coronaDestination = new RectF();
    private final Frame frame = new Frame();
    private final Bitmap sparkle;
    private final Bitmap sparkleAlternate;
    private final SoundPool soundPool;
    private final int unlockSound;
    private final Object soundLock = new Object();
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();
    private Bitmap underlay;
    private boolean destroyed;
    private boolean firstFrameDrawn;
    private boolean gestureActive;
    private boolean terminalComplete;
    private long gestureStartedAt;
    private long terminalStartedAt;
    private float centerX;
    private float centerY;
    private float downX;
    private float downY;
    private float heldRadius;
    private float requestedRadius;
    private UnlockEffectReadiness.ReadinessListener readinessListener;
    private final Runnable affordanceRelease = new Runnable() {
        @Override public void run() {
            if (!destroyed) finishGesture(false);
        }
    };

    public LgWhiteHoleEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        sparkle = decodeCoronaTexture(R.drawable.lg_whitehole_sparkle);
        sparkleAlternate = decodeCoronaTexture(R.drawable.lg_whitehole_sparkle_01);
        coronaPaint.setColor(Color.WHITE);
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(
                    SoundPool completedPool, int sampleId, int status) {
                handleSoundLoadComplete(completedPool, sampleId, status);
            }
        });
        unlockSound = soundPool.load(context, R.raw.lg_whitehole_unlock, 1);
    }

    @Override public View asView() { return this; }
    @Override public String effectName() { return "G1 White Hole (XLocker restoration tester)"; }

    @Override public void beginGesture(float x, float y) {
        if (destroyed || !hasBackgroundSourceBitmap()) return;
        removeCallbacks(affordanceRelease);
        gestureActive = true;
        terminalStartedAt = 0L;
        terminalComplete = false;
        downX = centerX = x;
        downY = centerY = y;
        gestureStartedAt = SystemClock.uptimeMillis();
        heldRadius = 0f;
        requestedRadius = 0f;
        postInvalidateOnAnimation();
    }

    @Override public void updateGesture(float x, float y) {
        if (destroyed) return;
        if (!gestureActive) {
            beginGesture(x, y);
            return;
        }
        float distance = (float) Math.hypot(x - downX, y - downY);
        requestedRadius = distance
                * Math.max(0.34f, 1f - minRadius() / Math.max(1f, unlockDistance()));
        postInvalidateOnAnimation();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || (!gestureActive && terminalStartedAt == 0L)) return;
        gestureActive = false;
        terminalComplete = completed;
        terminalStartedAt = SystemClock.uptimeMillis();
        if (completed) playSound(unlockSound);
        postInvalidateOnAnimation();
    }

    @Override public void cancelGesture() {
        if (destroyed) return;
        gestureActive = false;
        terminalComplete = false;
        terminalStartedAt = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override public void resetEffect() {
        removeCallbacks(affordanceRelease);
        gestureActive = false;
        terminalStartedAt = 0L;
        terminalComplete = false;
        gestureStartedAt = 0L;
        heldRadius = 0f;
        requestedRadius = 0f;
        invalidate();
    }

    @Override public void warmUp() {
        if (underlay != null && !underlay.isRecycled()) underlay.prepareToDraw();
        invalidate();
    }

    @Override public void showUnlockAffordance(Rect rect, long delayMs) {
        if (destroyed || !hasBackgroundSourceBitmap()) return;
        Rect safe = rect != null && rect.width() > 0 && rect.height() > 0
                ? rect : new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        final float x = safe.exactCenterX();
        final float y = safe.exactCenterY();
        postDelayed(new Runnable() {
            @Override public void run() {
                if (destroyed) return;
                beginGesture(x, y);
                postDelayed(affordanceRelease, AFFORDANCE_HOLD_MS);
            }
        }, Math.max(0L, delayMs));
    }

    @Override public boolean hasBackgroundSourceBitmap() {
        return underlay != null && !underlay.isRecycled();
    }

    @Override public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) return;
        Bitmap owned = source.copy(Bitmap.Config.ARGB_8888, false);
        if (owned == null || owned.isRecycled()) return;
        releaseUnderlay();
        underlay = owned;
        underlay.prepareToDraw();
        firstFrameDrawn = false;
        invalidate();
        notifyReadiness();
    }

    @Override public void clearBackgroundSourceBitmap() {
        releaseUnderlay();
        firstFrameDrawn = false;
        resetEffect();
        notifyReadiness();
    }

    @Override public int getReadinessState() {
        if (destroyed) return STATE_FAILED;
        if (!isAttachedToWindow()) return STATE_CONSTRUCTED;
        if (!isLaidOut()) return STATE_ATTACHED;
        if (!hasBackgroundSourceBitmap()) return STATE_SURFACE_READY;
        return firstFrameDrawn ? STATE_FIRST_FRAME_READY : STATE_RESOURCES_READY;
    }

    @Override public String getReadinessDetail() {
        if (destroyed) return effectName() + ": destroyed";
        if (!hasBackgroundSourceBitmap()) return effectName() + ": waiting for pre-lock underlay";
        return effectName() + (firstFrameDrawn
                ? ": underlay frame ready" : ": underlay loaded; waiting for first frame");
    }

    @Override public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    @Override public void destroy() {
        if (destroyed) return;
        removeCallbacks(affordanceRelease);
        destroyed = true;
        releaseUnderlay();
        if (sparkle != null && !sparkle.isRecycled()) sparkle.recycle();
        if (sparkleAlternate != null && !sparkleAlternate.isRecycled()) {
            sparkleAlternate.recycle();
        }
        synchronized (soundLock) {
            pendingSoundIds.clear();
            loadedSoundIds.clear();
            soundPool.setOnLoadCompleteListener(null);
            soundPool.release();
        }
        readinessListener = null;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        notifyReadiness();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (destroyed) return;
        if (!firstFrameDrawn && hasBackgroundSourceBitmap()) {
            firstFrameDrawn = true;
            notifyReadiness();
        }
        if (!hasBackgroundSourceBitmap() || getWidth() <= 0 || getHeight() <= 0) return;
        Frame frame = frameAt(SystemClock.uptimeMillis());
        if (!frame.visible || frame.radius <= 0.5f) return;

        clipPath.reset();
        clipPath.addCircle(centerX, centerY, frame.radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        sourceRect.set(0, 0, underlay.getWidth(), underlay.getHeight());
        destinationRect.set(0f, 0f, getWidth(), getHeight());
        bitmapPaint.setAlpha(Math.round(255f * frame.alpha));
        canvas.drawBitmap(underlay, sourceRect, destinationRect, bitmapPaint);
        canvas.restoreToCount(save);

        drawOriginalCorona(canvas, frame);
        bitmapPaint.setAlpha(255);

        if (frame.running) postInvalidateOnAnimation();
        else if (!gestureActive) resetEffect();
    }

    Frame frameAt(long now) {
        if (gestureActive) {
            float t = clamp((now - gestureStartedAt) / (float) AFFORDANCE_HOLD_MS, 0f, 1f);
            float eased = t * t;
            heldRadius = minRadius() * eased + requestedRadius;
            return frame.set(true, true, heldRadius, 1f, now - gestureStartedAt);
        }
        if (terminalStartedAt <= 0L) return frame.set(false, false, 0f, 0f, 0L);
        long elapsed = now - terminalStartedAt;
        long duration = terminalComplete ? COMPLETE_MS : CANCEL_MS;
        float t = clamp(elapsed / (float) duration, 0f, 1f);
        float eased = t * t;
        if (terminalComplete) {
            float max = maxRadius();
            float radius = heldRadius + (max - heldRadius) * eased;
            boolean tailRunning = elapsed < COMPLETE_MS + COMPLETE_HOLD_MS;
            long holdElapsed = Math.max(0L, elapsed - COMPLETE_MS);
            float alpha = holdElapsed <= COMPLETE_SOLID_HOLD_MS
                    ? 1f
                    : 1f - clamp((holdElapsed - COMPLETE_SOLID_HOLD_MS)
                            / (float) COMPLETE_FADE_MS, 0f, 1f);
            return frame.set(tailRunning, tailRunning, radius, alpha,
                    now - gestureStartedAt);
        }
        return frame.set(t < 1f, t < 1f, heldRadius * (1f - eased), 1f - t,
                now - gestureStartedAt);
    }

    private float minRadius() {
        return 54f * getResources().getDisplayMetrics().density;
    }

    private float maxRadius() {
        float left = Math.max(centerX, getWidth() - centerX);
        float top = Math.max(centerY, getHeight() - centerY);
        return (float) Math.hypot(left, top) + 8f;
    }

    private float unlockDistance() {
        return Math.min(getWidth(), getHeight()) * 0.31f;
    }

    private void drawOriginalCorona(Canvas canvas, Frame frame) {
        if (sparkle == null || sparkle.isRecycled()
                || sparkleAlternate == null || sparkleAlternate.isRecycled()) return;
        float halfSize = frame.radius * CORONA_HALF_SIZE_PER_RADIUS;
        coronaDestination.set(centerX - halfSize, centerY - halfSize,
                centerX + halfSize, centerY + halfSize);
        coronaPaint.setAlpha(Math.round(255f * frame.alpha));
        float degrees = frame.elapsedMs * 0.0072f;
        int save = canvas.save();
        canvas.rotate(degrees, centerX, centerY);
        canvas.drawBitmap(sparkle, null, coronaDestination, coronaPaint);
        canvas.restoreToCount(save);

        save = canvas.save();
        canvas.rotate(-degrees, centerX, centerY);
        canvas.drawBitmap(sparkleAlternate, null, coronaDestination, coronaPaint);
        canvas.restoreToCount(save);
        coronaPaint.setAlpha(255);
    }

    private Bitmap decodeCoronaTexture(int resourceId) {
        Bitmap texture = BitmapFactory.decodeResource(getResources(), resourceId);
        if (texture != null) texture.prepareToDraw();
        return texture;
    }

    private void releaseUnderlay() {
        if (underlay != null && !underlay.isRecycled()) underlay.recycle();
        underlay = null;
    }

    private void playSound(int soundId) {
        if (soundId == 0 || destroyed
                || !OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) return;
        synchronized (soundLock) {
            if (destroyed) return;
            if (!loadedSoundIds.contains(soundId)) {
                pendingSoundIds.add(soundId);
                return;
            }
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void handleSoundLoadComplete(
            SoundPool completedPool, int sampleId, int status) {
        synchronized (soundLock) {
            if (completedPool != soundPool || destroyed) return;
            if (status != 0) {
                pendingSoundIds.remove(sampleId);
                return;
            }
            loadedSoundIds.add(sampleId);
            if (pendingSoundIds.remove(sampleId)
                    && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
                soundPool.play(sampleId, 1f, 1f, 1, 0, 1f);
            }
        }
    }

    private void notifyReadiness() {
        final ReadinessListener listener = readinessListener;
        if (listener != null && !destroyed) post(new Runnable() {
            @Override public void run() { listener.onReadinessChanged(); }
        });
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Frame {
        boolean visible;
        boolean running;
        float radius;
        float alpha;
        long elapsedMs;

        Frame set(boolean visible, boolean running, float radius, float alpha, long elapsedMs) {
            this.visible = visible;
            this.running = running;
            this.radius = radius;
            this.alpha = alpha;
            this.elapsedMs = Math.max(0L, elapsedMs);
            return this;
        }
    }
}
