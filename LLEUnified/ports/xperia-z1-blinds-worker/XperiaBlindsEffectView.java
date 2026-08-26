package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Canvas reconstruction of the normal Xperia Z1 Blinds drawer in
 * com.othlocks.xperia.blinds v1.0.3.  The donor uses no bitmap shader: it
 * folds horizontal bitmap bands with Camera.rotateX(), then applies its
 * per-band colour/shadow treatment.
 */
public final class XperiaBlindsEffectView extends View implements UnlockEffectRenderer,
        BackgroundSourceRenderer, UnlockEffectReadiness {
    static final int STRIP_COUNT = 17;
    static final int AFFECTED_STRIP_COUNT = 5;
    static final float AFFECTED_RANGE = (float) AFFECTED_STRIP_COUNT / STRIP_COUNT;
    static final float MAX_FOLD_DEGREES = 3.0f;
    static final float CAMERA_DEPTH = 3.0f;
    static final float SPRING_STIFFNESS = 400.0f;
    static final float SPRING_DAMPING_RATIO = 0.85f;
    static final long EXIT_FADE_MS = 160L;
    static final long EXIT_COMPLETE_MS = 200L;
    static final long MAX_PHYSICS_STEP_NS = 50_000_000L;
    private static final float IDLE_POSITION_EPSILON = 0.0015f;
    private static final float IDLE_VELOCITY_EPSILON = 0.012f;
    private static final int BACKGROUND_COLOR = 0x9f000000;
    private static final int SEAM_COLOR = 0xbb000000;
    private static final int SHADOW_COLOR = 0x4d000000;

    private final Paint stripPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint seamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect sourceRect = new Rect();
    private final Rect destinationRect = new Rect();
    private final Camera camera = new Camera();
    private final Matrix transform = new Matrix();
    private final Matrix cameraTransform = new Matrix();
    private final PorterDuffColorFilter[] brightFilters = new PorterDuffColorFilter[100];
    private final LightingColorFilter[] darkFilters = new LightingColorFilter[100];
    private final float[] springOutput = new float[2];
    private final Runnable affordanceRelease = new Runnable() {
        @Override public void run() { if (!destroyed) finishGesture(false); }
    };

    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private boolean externalBackground;
    private String backgroundSource = "none";
    private boolean destroyed;
    private boolean firstFrameDrawn;
    private boolean gestureActive;
    private boolean exitRequested;
    private long exitStartedAtNs;
    private long lastFrameAtNs;
    private float touchX;
    private float touchY;
    private float springPosition;
    private float springVelocity;
    private float targetPosition;
    private final float[] stripAlpha = new float[STRIP_COUNT];
    private float stripFadePerMs;
    private long lastExitFrameAtNs;
    private UnlockEffectReadiness.ReadinessListener readinessListener;

    public XperiaBlindsEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        seamPaint.setColor(SEAM_COLOR);
        seamPaint.setStrokeWidth(2.0f);
        shadowPaint.setStyle(Paint.Style.FILL);
        resetStripAlpha();
        for (int i = 0; i < 100; i++) {
            brightFilters[i] = new PorterDuffColorFilter(Color.argb(i, 255, 255, 255), PorterDuff.Mode.OVERLAY);
            int value = 255 - i;
            darkFilters[i] = new LightingColorFilter(Color.rgb(value, value, value), 0);
        }
    }

    @Override public View asView() { return this; }
    @Override public String effectName() { return "Xperia Blinds"; }
    public boolean supportsHighFrameRate() { return true; }
    public boolean supportsSpeedMultiplier() { return false; }

    @Override public void beginGesture(float x, float y) {
        if (destroyed) return;
        removeCallbacks(affordanceRelease);
        gestureActive = true;
        exitRequested = false;
        exitStartedAtNs = 0L;
        touchX = clamp(x, 0f, Math.max(0f, renderWidth() - 1f));
        touchY = clamp(y, 0f, Math.max(0f, renderHeight() - 1f));
        targetPosition = 1f;
        lastFrameAtNs = 0L;
        postInvalidateOnAnimation();
    }

    @Override public void updateGesture(float x, float y) {
        if (destroyed) return;
        if (!gestureActive) { beginGesture(x, y); return; }
        touchX = clamp(x, 0f, Math.max(0f, renderWidth() - 1f));
        touchY = clamp(y, 0f, Math.max(0f, renderHeight() - 1f));
        postInvalidateOnAnimation();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || (!gestureActive && !exitRequested)) return;
        gestureActive = false;
        targetPosition = 0f;
        if (completed) requestExit();
        else postInvalidateOnAnimation();
    }

    @Override public void cancelGesture() { if (!destroyed) { clearMotion(); invalidate(); } }
    @Override public void resetEffect() { if (!destroyed) { removeCallbacks(affordanceRelease); clearMotion(); invalidate(); } }
    @Override public void warmUp() { if (!destroyed) { if (validBackground()) backgroundBitmap.prepareToDraw(); invalidate(); } }

    @Override public void showUnlockAffordance(Rect rect, long startDelayMs) {
        if (destroyed) return;
        Rect target = rect;
        if (target == null || target.width() <= 0 || target.height() <= 0) target = new Rect(0, 0, renderWidth(), renderHeight());
        beginGesture(target.exactCenterX(), target.exactCenterY());
        removeCallbacks(affordanceRelease);
        postDelayed(affordanceRelease, Math.max(0L, startDelayMs) + 130L);
    }

    @Override public boolean hasBackgroundSourceBitmap() {
        return externalBackground && validBackground() && backgroundBitmap.getWidth() == renderWidth()
                && backgroundBitmap.getHeight() == renderHeight();
    }

    @Override public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) return;
        int width = renderWidth();
        int height = renderHeight();
        boolean borrow = BackgroundSourceRenderer.canBorrowSharedCache(source, sourceName, width, height);
        Bitmap next = borrow ? source : createExactMappingBitmap(source, width, height);
        next.prepareToDraw();
        releaseBackgroundBitmap();
        backgroundBitmap = next;
        ownsBackgroundBitmap = !borrow;
        externalBackground = true;
        backgroundSource = sourceName == null ? "external" : sourceName;
        invalidate();
    }

    @Override public void clearBackgroundSourceBitmap() {
        if (destroyed) return;
        releaseBackgroundBitmap();
        externalBackground = false;
        backgroundSource = "none";
        invalidate();
    }

    @Override public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) { return bitmap != null && bitmap == backgroundBitmap; }

    @Override public int getReadinessState() {
        if (destroyed) return UnlockEffectReadiness.STATE_FAILED;
        if (!isAttachedToWindow()) return UnlockEffectReadiness.STATE_CONSTRUCTED;
        if (firstFrameDrawn) return UnlockEffectReadiness.STATE_FIRST_FRAME_READY;
        return isLaidOut() ? UnlockEffectReadiness.STATE_RESOURCES_READY : UnlockEffectReadiness.STATE_ATTACHED;
    }

    @Override public String getReadinessDetail() {
        if (destroyed) return "Xperia Blinds: renderer destroyed";
        if (!isAttachedToWindow()) return "Xperia Blinds: canvas constructed";
        if (firstFrameDrawn) return "Xperia Blinds: app-owned canvas warm frame drawn";
        return isLaidOut() ? "Xperia Blinds: canvas resources ready" : "Xperia Blinds: canvas attached; waiting for layout";
    }

    @Override public void setReadinessListener(UnlockEffectReadiness.ReadinessListener listener) { readinessListener = listener; notifyReadinessChanged(); }

    @Override public void destroy() {
        if (destroyed) return;
        removeCallbacks(affordanceRelease);
        clearMotion();
        destroyed = true;
        releaseBackgroundBitmap();
        readinessListener = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!firstFrameDrawn) { firstFrameDrawn = true; notifyReadinessChanged(); }
        if (destroyed || !validBackground()) return;
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || backgroundBitmap.getWidth() != width || backgroundBitmap.getHeight() != height) return;
        long now = SystemClock.elapsedRealtimeNanos();
        advancePhysics(now);
        updateExitFade(now);
        float exitAlpha = exitAlpha(now);
        if (springPosition > IDLE_POSITION_EPSILON && exitAlpha > 0f) drawBlinds(canvas, width, height, exitAlpha);
        if (needsNextFrame(exitAlpha)) postInvalidateOnAnimation();
        else if (exitRequested || (!gestureActive && springPosition <= IDLE_POSITION_EPSILON)) clearMotion();
    }

    private void drawBlinds(Canvas canvas, int width, int height, float exitAlpha) {
        canvas.drawColor(Color.argb(Math.round(Color.alpha(BACKGROUND_COLOR) * exitAlpha), 0, 0, 0));
        float pressY = clamp(touchY / Math.max(1f, height), 0f, 1f);
        float halfRange = AFFECTED_RANGE * .5f;
        int start = clampInt((int) Math.floor((pressY - halfRange) * STRIP_COUNT + .5f), 0, STRIP_COUNT - 1);
        int end = clampInt((int) Math.ceil((pressY + halfRange) * STRIP_COUNT - .5f), 0, STRIP_COUNT);
        if (end <= start) return;
        drawPlainRange(canvas, width, height, 0, start, exitAlpha);
        int middle = start + ((end - start) / 2);
        for (int strip = middle; strip >= start; strip--) drawFoldedStrip(canvas, width, height, strip, pressY, exitAlpha);
        for (int strip = middle + 1; strip < end; strip++) drawFoldedStrip(canvas, width, height, strip, pressY, exitAlpha);
        drawPlainRange(canvas, width, height, end, STRIP_COUNT, exitAlpha);
    }

    private void drawPlainRange(Canvas canvas, int width, int height, int from, int to, float alpha) {
        for (int strip = from; strip < to; strip++) {
            sourceRect.set(0, bandTop(height, strip), width, bandTop(height, strip + 1));
            if (sourceRect.height() <= 0) continue;
            destinationRect.set(sourceRect);
            stripPaint.setColorFilter(null);
            stripPaint.setAlpha(Math.round(stripAlphaFor(strip, alpha)));
            canvas.drawBitmap(backgroundBitmap, sourceRect, destinationRect, stripPaint);
        }
    }

    private void drawFoldedStrip(Canvas canvas, int width, int height, int strip, float pressY, float exitAlpha) {
        int top = bandTop(height, strip);
        int bottom = bandTop(height, strip + 1);
        if (bottom <= top) return;
        float stripCenter = ((strip + .5f) / STRIP_COUNT);
        float normalizedDistance = (2f * (stripCenter - pressY)) / AFFECTED_RANGE;
        if (Math.abs(normalizedDistance) >= 1f) { drawPlainRange(canvas, width, height, strip, strip + 1, exitAlpha); return; }
        float wave = (float) Math.sin(Math.PI * normalizedDistance);
        float fold = (1f + (float) Math.cos(Math.PI * normalizedDistance)) * springPosition;
        float foldUnit = fold / 2f;
        sourceRect.set(0, top, width, bottom);
        destinationRect.set(sourceRect);
        transform.reset();
        transform.setTranslate(-sourceRect.centerX(), -sourceRect.centerY());
        float rotationPivotX = (touchX / Math.max(1f, width)) < .5f ? sourceRect.centerX() : -sourceRect.centerX();
        transform.postRotate((.5f - (touchX / Math.max(1f, width))) * MAX_FOLD_DEGREES * (1f + (float) Math.cos(Math.PI * normalizedDistance)) * springPosition,
                rotationPivotX, sourceRect.width() / 2f);
        camera.save();
        camera.translate(0f, 0f, CAMERA_DEPTH * foldUnit);
        camera.rotateX(MAX_FOLD_DEGREES * wave * springPosition);
        cameraTransform.reset();
        camera.getMatrix(cameraTransform);
        camera.restore();
        transform.postConcat(cameraTransform);
        transform.postTranslate(sourceRect.centerX(), sourceRect.centerY());
        setFoldColorFilter(wave * springPosition);
        int alpha = Math.round(stripAlphaFor(strip, exitAlpha));
        stripPaint.setAlpha(alpha);
        seamPaint.setAlpha(alpha);
        int save = canvas.save();
        canvas.concat(transform);
        canvas.drawBitmap(backgroundBitmap, sourceRect, destinationRect, stripPaint);
        if (fold > .1f) {
            float shadowLength = (1f - Math.abs(normalizedDistance)) * 50f;
            shadowPaint.setShader(new LinearGradient(sourceRect.left, sourceRect.top - .5f, sourceRect.left, sourceRect.top + shadowLength,
                    SHADOW_COLOR, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRect(sourceRect.left, sourceRect.top - .5f, sourceRect.right, sourceRect.top + shadowLength, shadowPaint);
            canvas.drawLine(sourceRect.left, sourceRect.top - 1f, sourceRect.right, sourceRect.top - 1f, seamPaint);
            canvas.drawLine(sourceRect.left, sourceRect.bottom, sourceRect.right, sourceRect.bottom, seamPaint);
        }
        canvas.restoreToCount(save);
        shadowPaint.setShader(null);
        stripPaint.setColorFilter(null);
        stripPaint.setAlpha(255);
        seamPaint.setAlpha(255);
    }

    private void setFoldColorFilter(float value) {
        int index = clampInt((int) (99f * Math.abs(value) * 2f), 0, 99);
        stripPaint.setColorFilter(value > 0f ? brightFilters[index] : darkFilters[index]);
    }

    private void requestExit() {
        if (exitRequested) return;
        exitRequested = true;
        exitStartedAtNs = SystemClock.elapsedRealtimeNanos();
        lastExitFrameAtNs = exitStartedAtNs;
        configureExitStripFade();
        lastFrameAtNs = 0L;
        postInvalidateOnAnimation();
    }

    private void advancePhysics(long now) {
        if (lastFrameAtNs == 0L) { lastFrameAtNs = now; return; }
        long deltaNs = Math.max(0L, Math.min(MAX_PHYSICS_STEP_NS, now - lastFrameAtNs));
        lastFrameAtNs = now;
        if (deltaNs == 0L) return;
        springStepInto(springPosition, springVelocity, targetPosition, deltaNs / 1_000_000_000f, springOutput);
        springPosition = Math.max(0f, springOutput[0]);
        springVelocity = springOutput[1];
    }

    private float exitAlpha(long now) {
        if (!exitRequested) return 1f;
        float elapsedMs = (now - exitStartedAtNs) / 1_000_000f;
        return elapsedMs <= 40f ? 1f : 1f - clamp((elapsedMs - 40f) / EXIT_FADE_MS, 0f, 1f);
    }

    private boolean needsNextFrame(float exitAlpha) {
        if (gestureActive) return true;
        if (exitRequested) return (SystemClock.elapsedRealtimeNanos() - exitStartedAtNs) / 1_000_000L < EXIT_COMPLETE_MS;
        return springPosition > IDLE_POSITION_EPSILON || Math.abs(springVelocity) > IDLE_VELOCITY_EPSILON;
    }

    private void clearMotion() { gestureActive = false; exitRequested = false; exitStartedAtNs = 0L; lastExitFrameAtNs = 0L; lastFrameAtNs = 0L; springPosition = 0f; springVelocity = 0f; targetPosition = 0f; resetStripAlpha(); }

    private void configureExitStripFade() {
        float pressY = clamp(touchY / Math.max(1f, getHeight()), 0f, 1f);
        float greatestDistance = 0f;
        for (int strip = 0; strip < STRIP_COUNT; strip++) greatestDistance = Math.max(greatestDistance,
                Math.abs(((strip + .5f) / STRIP_COUNT) - pressY));
        float maxAlpha = 255f;
        for (int strip = 0; strip < STRIP_COUNT; strip++) {
            float distance = Math.abs(((strip + .5f) / STRIP_COUNT) - pressY);
            stripAlpha[strip] = 255f + 600f * (greatestDistance <= 0f ? 0f : distance / greatestDistance);
            maxAlpha = Math.max(maxAlpha, stripAlpha[strip]);
        }
        stripFadePerMs = maxAlpha / 300f;
    }

    private void updateExitFade(long now) {
        if (!exitRequested || lastExitFrameAtNs == 0L) return;
        float elapsedMs = Math.max(0f, Math.min(50f, (now - lastExitFrameAtNs) / 1_000_000f));
        lastExitFrameAtNs = now;
        for (int strip = 0; strip < STRIP_COUNT; strip++) stripAlpha[strip] = Math.max(0f, stripAlpha[strip] - stripFadePerMs * elapsedMs);
    }

    private float stripAlphaFor(int strip, float globalAlpha) { return Math.min(255f, stripAlpha[clampInt(strip, 0, STRIP_COUNT - 1)]) * globalAlpha; }
    private void resetStripAlpha() { for (int strip = 0; strip < STRIP_COUNT; strip++) stripAlpha[strip] = 255f; stripFadePerMs = 0f; }

    static float[] springStep(float position, float velocity, float target, float seconds) {
        float[] output = new float[2];
        springStepInto(position, velocity, target, seconds, output);
        return output;
    }

    private static void springStepInto(float position, float velocity, float target, float seconds, float[] output) {
        float dt = clamp(seconds, 0f, .05f);
        float omega = (float) Math.sqrt(SPRING_STIFFNESS);
        float dampedOmega = omega * (float) Math.sqrt(1f - SPRING_DAMPING_RATIO * SPRING_DAMPING_RATIO);
        float offset = position - target;
        float exp = (float) Math.exp(-SPRING_DAMPING_RATIO * omega * dt);
        float cos = (float) Math.cos(dampedOmega * dt);
        float sin = (float) Math.sin(dampedOmega * dt);
        float velocityTerm = (velocity + SPRING_DAMPING_RATIO * omega * offset) / dampedOmega;
        float displacement = offset * cos + velocityTerm * sin;
        output[0] = target + exp * displacement;
        output[1] = exp * (-SPRING_DAMPING_RATIO * omega * displacement - offset * dampedOmega * sin + velocityTerm * dampedOmega * cos);
    }

    static int affectedStart(float y) { return clampInt((int) Math.floor((clamp(y, 0f, 1f) - AFFECTED_RANGE * .5f) * STRIP_COUNT + .5f), 0, STRIP_COUNT - 1); }
    static int affectedEnd(float y) { return clampInt((int) Math.ceil((clamp(y, 0f, 1f) + AFFECTED_RANGE * .5f) * STRIP_COUNT - .5f), 0, STRIP_COUNT); }
    static float stripWave(int strip, float y) { return (float) Math.sin(Math.PI * (2f * (((strip + .5f) / STRIP_COUNT) - clamp(y, 0f, 1f)) / AFFECTED_RANGE)); }
    static int bandTop(int height, int strip) { return (int) (height * (strip / (float) STRIP_COUNT)); }

    private Bitmap createExactMappingBitmap(Bitmap source, int width, int height) {
        Bitmap copy = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        new Canvas(copy).drawBitmap(source, null, new Rect(0, 0, copy.getWidth(), copy.getHeight()), new Paint(Paint.FILTER_BITMAP_FLAG));
        return copy;
    }
    private boolean validBackground() { return backgroundBitmap != null && !backgroundBitmap.isRecycled(); }
    private void releaseBackgroundBitmap() { if (ownsBackgroundBitmap && validBackground()) backgroundBitmap.recycle(); backgroundBitmap = null; ownsBackgroundBitmap = false; }
    private int renderWidth() { return getWidth() > 0 ? getWidth() : Math.max(1, getResources().getDisplayMetrics().widthPixels); }
    private int renderHeight() { return getHeight() > 0 ? getHeight() : Math.max(1, getResources().getDisplayMetrics().heightPixels); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); firstFrameDrawn = false; notifyReadinessChanged(); warmUp(); }
    @Override protected void onDetachedFromWindow() { firstFrameDrawn = false; notifyReadinessChanged(); super.onDetachedFromWindow(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); firstFrameDrawn = false; notifyReadinessChanged(); }
    private void notifyReadinessChanged() { if (readinessListener != null) try { readinessListener.onReadinessChanged(); } catch (RuntimeException ignored) { } }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static int clampInt(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
