package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.media.SoundPool;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import java.util.HashSet;
import java.util.Set;

/**
 * LG G1 Dewdrop restoration from the authorized OptimusDev/XLocker archive.
 *
 * <p>The original effect renders a 50 x 70 ellipsoid mesh and refracts the captured
 * lockscreen through it with an index of 3.0.  This modern Canvas port evaluates the same
 * radial refraction equation in concentric bands, then draws the original 720 px optical
 * overlay.  It deliberately consumes L.L.E.'s pre-lock frame rather than the lockscreen
 * colormap used by Samsung effects.</p>
 */
public final class LgDewdropEffectView extends View
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    static final long COMPLETE_MS = 400L;
    static final long COMPLETE_SOLID_HOLD_MS = 600L;
    static final long CANCEL_MS = 300L;

    private static final int STAGE_IDLE = 0;
    private static final int STAGE_ACTIVE = 1;
    private static final int STAGE_CANCEL = 2;
    private static final int STAGE_COMPLETE = 3;
    private static final int REFRACTION_BANDS = 44;
    private static final float REFRACTION_INDEX = 3f;
    private static final float ARCHIVE_BASE_DENSITY = 1.5f; // drawable-hdpi
    private static final long AFFORDANCE_HOLD_MS = 150L;
    private static final String REFRACTION_SHADER =
            "uniform shader uUnderlay;"
            + "uniform float2 uCenter;"
            + "uniform float uRadius;"
            + "uniform float uEllipseB;"
            + "uniform float uAlpha;"
            + "half4 main(float2 p) {"
            + " float2 d=p-uCenter; float r=length(d);"
            + " if (r>uRadius || uRadius<0.5) return half4(0.0);"
            + " float2 dir=r>0.0001?d/r:float2(0.0,0.0);"
            + " float a=max(uRadius,0.001); float b=max(uEllipseB,0.001);"
            + " float q=min(r,a);"
            + " float z=b*sqrt(max(0.0,1.0-q*q/(a*a)));"
            + " float slope=atan((a*a*z)/(b*b*max(q,0.0001)));"
            + " float incidence=1.57079632679-slope;"
            + " float refractAngle=asin(sin(incidence)/3.0);"
            + " float newM=tan(slope+refractAngle);"
            + " float sourceR=abs(r-z/(abs(newM)>0.0001?newM:0.0001));"
            + " half4 c=uUnderlay.eval(uCenter+dir*sourceR);"
            + " return half4(c.rgb*uAlpha,uAlpha);"
            + "}";

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect sourceRect = new Rect();
    private final RectF screenRect = new RectF();
    private final RectF holeRect = new RectF();
    private final Path annulus = new Path();
    private final Matrix underlayShaderMatrix = new Matrix();
    private final AccelerateInterpolator accelerate = new AccelerateInterpolator();
    private final Bitmap holeTexture;
    private RuntimeShader refractionShader;
    private BitmapShader underlayShader;
    private final SoundPool soundPool;
    private final int touchdownSound;
    private final int releaseSound;
    private final int unlockSound;
    private final Object soundLock = new Object();
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();

    private Bitmap underlay;
    private boolean destroyed;
    private boolean firstFrameDrawn;
    private boolean gestureActive;
    private int stage = STAGE_IDLE;
    private long terminalStartedAt;
    private float centerX;
    private float centerY;
    private float downX;
    private float downY;
    private float dragDistance;
    private float radius;
    private float terminalStartRadius;
    private int animationGeneration;
    private ReadinessListener readinessListener;
    private final Runnable affordanceRelease = new Runnable() {
        @Override public void run() {
            if (!destroyed && stage == STAGE_ACTIVE) finishGesture(false);
        }
    };

    public LgDewdropEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                refractionShader = new RuntimeShader(REFRACTION_SHADER);
            } catch (RuntimeException ignored) {
                refractionShader = null;
            }
        }
        holeTexture = BitmapFactory.decodeResource(getResources(), R.drawable.lg_dewdrop_hole);
        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                handleSoundLoadComplete(pool, sampleId, status);
            }
        });
        touchdownSound = soundPool.load(context, R.raw.lg_dewdrop_touchdown, 1);
        releaseSound = soundPool.load(context, R.raw.lg_dewdrop_touchrelease, 1);
        unlockSound = soundPool.load(context, R.raw.lg_dewdrop_unlock, 1);
    }

    @Override public View asView() { return this; }
    @Override public String effectName() {
        return "G1 Dewdrop";
    }

    @Override public void beginGesture(float x, float y) {
        if (destroyed || !hasBackgroundSourceBitmap()) return;
        animationGeneration++;
        animate().cancel();
        setVisibility(VISIBLE);
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
        removeCallbacks(affordanceRelease);
        gestureActive = true;
        stage = STAGE_ACTIVE;
        terminalStartedAt = 0L;
        centerX = downX = clamp(x, 0f, Math.max(0f, getWidth()));
        centerY = downY = clamp(y, 0f, Math.max(0f, getHeight()));
        dragDistance = 0f;
        radius = minRadius();
        terminalStartRadius = radius;
        playSound(touchdownSound);
        postInvalidateOnAnimation();
    }

    @Override public void updateGesture(float x, float y) {
        if (destroyed) return;
        if (!gestureActive || stage != STAGE_ACTIVE) {
            beginGesture(x, y);
            return;
        }
        dragDistance = (float) Math.hypot(x - downX, y - downY);
        float threshold = unlockDistance();
        radius = dragDistance > threshold
                ? dragDistance
                : minRadius() + ((threshold - minRadius()) / threshold) * dragDistance;
        postInvalidateOnAnimation();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || stage != STAGE_ACTIVE) return;
        gestureActive = false;
        terminalStartRadius = radius;
        terminalStartedAt = SystemClock.uptimeMillis();
        stage = completed ? STAGE_COMPLETE : STAGE_CANCEL;
        playSound(completed ? unlockSound : releaseSound);
        if (!completed) startRenderThreadRetraction();
        postInvalidateOnAnimation();
    }

    @Override public void cancelGesture() {
        if (stage == STAGE_ACTIVE) finishGesture(false);
    }

    @Override public void resetEffect() {
        animationGeneration++;
        animate().cancel();
        removeCallbacks(affordanceRelease);
        gestureActive = false;
        stage = STAGE_IDLE;
        terminalStartedAt = 0L;
        dragDistance = 0f;
        radius = 0f;
        terminalStartRadius = 0f;
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
        invalidate();
    }

    @Override public void warmUp() {
        if (underlay != null && !underlay.isRecycled()) underlay.prepareToDraw();
        if (holeTexture != null && !holeTexture.isRecycled()) holeTexture.prepareToDraw();
        invalidate();
    }

    @Override public void showUnlockAffordance(Rect rect, long delayMs) {
        if (destroyed || !hasBackgroundSourceBitmap()) return;
        final Rect safe = rect != null && rect.width() > 0 && rect.height() > 0
                ? rect : new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        postDelayed(new Runnable() {
            @Override public void run() {
                if (destroyed) return;
                beginGesture(safe.exactCenterX(), safe.exactCenterY());
                postDelayed(affordanceRelease, AFFORDANCE_HOLD_MS);
            }
        }, Math.max(0L, delayMs));
    }

    @Override public boolean hasBackgroundSourceBitmap() {
        return underlay != null && !underlay.isRecycled();
    }

    @Override public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) return;
        Bitmap owned;
        try {
            owned = source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (OutOfMemoryError ignored) {
            return;
        }
        if (owned == null || owned.isRecycled()) return;
        releaseUnderlay();
        underlay = owned;
        underlay.prepareToDraw();
        rebuildUnderlayShader();
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
                ? ": refracted underlay and archive optics ready"
                : ": underlay loaded; waiting for first frame");
    }

    @Override public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    @Override public void destroy() {
        if (destroyed) return;
        animationGeneration++;
        animate().cancel();
        destroyed = true;
        removeCallbacks(affordanceRelease);
        releaseUnderlay();
        if (holeTexture != null && !holeTexture.isRecycled()) holeTexture.recycle();
        synchronized (soundLock) {
            pendingSoundIds.clear();
            loadedSoundIds.clear();
            soundPool.setOnLoadCompleteListener(null);
            soundPool.release();
        }
        readinessListener = null;
    }

    /**
     * Samsung's translucent WindowManager surface may keep the last RuntimeShader display
     * list even though Canvas has advanced to an empty state. A RenderNode property animation
     * both preserves the donor's accelerated 300 ms retraction and guarantees that the stale
     * display list becomes non-visible. The generation prevents an old hint callback from
     * hiding a newer real gesture.
     */
    private void startRenderThreadRetraction() {
        final int generation = ++animationGeneration;
        setPivotX(centerX);
        setPivotY(centerY);
        animate().cancel();
        animate()
                .alpha(0f)
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(CANCEL_MS)
                .setInterpolator(accelerate)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (destroyed || generation != animationGeneration) return;
                        stage = STAGE_IDLE;
                        radius = 0f;
                        terminalStartRadius = 0f;
                        setVisibility(INVISIBLE);
                        setAlpha(1f);
                        setScaleX(1f);
                        setScaleY(1f);
                        invalidate();
                    }
                })
                .start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        notifyReadiness();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // The effect lives in a translucent WindowManager surface. Some Samsung render
        // paths preserve the previous GPU buffer when a later display list is empty, which
        // leaves the last droplet frozen after hint/cancel. Clear the complete surface before
        // evaluating every state, including IDLE's intentionally empty frame.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        if (destroyed || !hasBackgroundSourceBitmap() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (!firstFrameDrawn) {
            firstFrameDrawn = true;
            notifyReadiness();
        }
        long now = SystemClock.uptimeMillis();
        float drawRadius = radius;
        float alpha = 1f;
        boolean running = stage == STAGE_ACTIVE;
        if (stage == STAGE_CANCEL) {
            float t = clamp((now - terminalStartedAt) / (float) CANCEL_MS, 0f, 1f);
            float eased = accelerate.getInterpolation(t);
            drawRadius = terminalStartRadius * (1f - eased);
            alpha = 1f - eased;
            running = t < 1f;
        } else if (stage == STAGE_COMPLETE) {
            long elapsed = Math.max(0L, now - terminalStartedAt);
            float t = clamp(elapsed / (float) COMPLETE_MS, 0f, 1f);
            float eased = accelerate.getInterpolation(t);
            drawRadius = terminalStartRadius + (fullRadius() - terminalStartRadius) * eased;
            running = elapsed < COMPLETE_MS + COMPLETE_SOLID_HOLD_MS;
        }
        if (drawRadius > 0.5f && alpha > 0f) {
            drawRefractedDrop(canvas, drawRadius, alpha);
        }
        if (running) {
            postInvalidateOnAnimation();
        } else if (stage != STAGE_ACTIVE) {
            stage = STAGE_IDLE;
            invalidate();
        }
    }

    private void drawRefractedDrop(Canvas canvas, float drawRadius, float alpha) {
        sourceRect.set(0, 0, underlay.getWidth(), underlay.getHeight());
        screenRect.set(0f, 0f, getWidth(), getHeight());
        bitmapPaint.setAlpha(Math.round(255f * alpha));
        float ellipseB = ellipseHeight(drawRadius);

        if (Build.VERSION.SDK_INT >= 33 && refractionShader != null) {
            if (underlayShader == null) rebuildUnderlayShader();
            if (underlayShader != null) {
                updateUnderlayShaderMatrix();
                refractionShader.setInputShader("uUnderlay", underlayShader);
                refractionShader.setFloatUniform("uCenter", centerX, centerY);
                refractionShader.setFloatUniform("uRadius", drawRadius);
                refractionShader.setFloatUniform("uEllipseB", Math.max(0.001f, ellipseB));
                refractionShader.setFloatUniform("uAlpha", alpha);
                bitmapPaint.setShader(refractionShader);
                canvas.drawRect(screenRect, bitmapPaint);
                bitmapPaint.setShader(null);
            }
        } else {
            drawBandedRefraction(canvas, drawRadius, ellipseB);
        }

        if (holeTexture != null && !holeTexture.isRecycled()) {
            float diameter = archiveHoleDiameter(drawRadius);
            float half = diameter * 0.5f;
            holeRect.set(centerX - half, centerY - half, centerX + half, centerY + half);
            holePaint.setAlpha(Math.round(255f * alpha));
            canvas.drawBitmap(holeTexture, null, holeRect, holePaint);
            holePaint.setAlpha(255);
        }
        bitmapPaint.setAlpha(255);
    }

    /** Compatibility fallback for Android releases predating RuntimeShader. */
    private void drawBandedRefraction(Canvas canvas, float drawRadius, float ellipseB) {
        for (int band = REFRACTION_BANDS - 1; band >= 0; band--) {
            float inner = drawRadius * band / REFRACTION_BANDS;
            float outer = drawRadius * (band + 1f) / REFRACTION_BANDS;
            float sampleAt = (inner + outer) * 0.5f;
            float sourceAt = refractedSourceRadius(sampleAt, drawRadius, ellipseB);
            float scale = sourceAt > 0.001f ? sampleAt / sourceAt : 1f;
            scale = clamp(scale, 0.35f, 3f);
            annulus.reset();
            annulus.setFillType(Path.FillType.EVEN_ODD);
            annulus.addCircle(centerX, centerY, outer + 1f, Path.Direction.CW);
            if (inner > 0f) annulus.addCircle(centerX, centerY, Math.max(0f, inner - 1f),
                    Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(annulus);
            canvas.scale(scale, scale, centerX, centerY);
            canvas.drawBitmap(underlay, sourceRect, screenRect, bitmapPaint);
            canvas.restoreToCount(save);
        }
    }

    private void rebuildUnderlayShader() {
        underlayShader = null;
        if (Build.VERSION.SDK_INT < 33 || underlay == null || underlay.isRecycled()) return;
        underlayShader = new BitmapShader(underlay, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        updateUnderlayShaderMatrix();
    }

    private void updateUnderlayShaderMatrix() {
        if (underlayShader == null || underlay == null || underlay.isRecycled()
                || getWidth() <= 0 || getHeight() <= 0) return;
        underlayShaderMatrix.reset();
        underlayShaderMatrix.setScale(getWidth() / (float) underlay.getWidth(),
                getHeight() / (float) underlay.getHeight());
        underlayShader.setLocalMatrix(underlayShaderMatrix);
    }

    /** Direct translation of dewdrop_vs.glsl for a positive radial coordinate. */
    private static float refractedSourceRadius(float position, float a, float b) {
        if (position <= 0.0001f || a <= 0.0001f || b <= 0.0001f) return 0f;
        float clamped = Math.min(position, a);
        float z = b * (float) Math.sqrt(Math.max(0f, 1f - clamped * clamped / (a * a)));
        float slope = (float) Math.atan((a * a * z) / (b * b * clamped));
        float incidence = (float) (Math.PI * 0.5) - slope;
        float refraction = (float) Math.asin(Math.sin(incidence) / REFRACTION_INDEX);
        float newM = (float) Math.tan(slope + refraction);
        if (Math.abs(newM) < 0.0001f) return position;
        return Math.abs(position - z / newM);
    }

    private float ellipseHeight(float a) {
        float cap = dp(90f);
        float b = 0.4f * a;
        if (b > cap) {
            float full = fullRadius();
            float taperStart = cap / 0.4f;
            b = cap * ((full - a) / Math.max(1f, full - taperStart));
        }
        return Math.max(0f, b);
    }

    /** Direct translation of the donor m180b() optical-overlay scale curve. */
    private float archiveHoleDiameter(float r) {
        float density = getResources().getDisplayMetrics().density;
        float scale;
        if (r <= 47.342f * density) {
            scale = 0.23f / (47.342f * density) * r;
        } else if (r <= 123.865f * density) {
            scale = 0.00108987f + 0.37f / (76.522f * density) * r;
        } else if (r <= 205.346f * density) {
            scale = 0.4f / (81.48f * density) * r - 0.00807484f;
        } else if (r <= 265.969f * density) {
            scale = 0.7f / (142.103f * density) * r - 0.01016121f;
        } else if (r <= 373.158f * density) {
            scale = 0.05934662f + 0.5f / (107.189f * density) * r;
        } else {
            scale = 0.7f / (145.098f * density) * r - 0.000226446f;
        }
        // BitmapFactory scaled the original hdpi resource to the device density.
        float decodedArchiveWidth = 720f * density / ARCHIVE_BASE_DENSITY;
        return Math.max(0f, decodedArchiveWidth * scale);
    }

    private float minRadius() { return dp(44f); }
    private float unlockDistance() { return dp(113.33f); }
    private float fullRadius() {
        // Keep the donor's generous target, but express it in actual view pixels. Its old
        // renderer multiplied display pixels by density a second time, which modern QHD
        // displays do not need to reach full coverage.
        float farX = Math.max(centerX, getWidth() - centerX);
        float farY = Math.max(centerY, getHeight() - centerY);
        return 1.3f * (float) Math.hypot(farX, farY);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void playSound(int soundId) {
        if (soundId == 0 || destroyed) return;
        synchronized (soundLock) {
            if (loadedSoundIds.contains(soundId)) {
                soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
            } else {
                pendingSoundIds.add(soundId);
            }
        }
    }

    private void handleSoundLoadComplete(SoundPool pool, int soundId, int status) {
        synchronized (soundLock) {
            if (destroyed) return;
            if (status == 0) loadedSoundIds.add(soundId);
            if (status == 0 && pendingSoundIds.remove(soundId)) {
                pool.play(soundId, 1f, 1f, 1, 0, 1f);
            } else if (status != 0) {
                pendingSoundIds.remove(soundId);
            }
        }
    }

    private void releaseUnderlay() {
        underlayShader = null;
        if (underlay != null && !underlay.isRecycled()) underlay.recycle();
        underlay = null;
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
}
