package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Random;

public class LensFlareEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "ChargingS4LensFlare";
    private static final long SHOW_ANIMATION_DURATION_MS = 6000L;
    private static final long FOG_ON_DURATION_MS = 100L;
    private static final long TAP_ANIMATION_DURATION_MS = 4000L;
    private static final long FADE_OUT_DURATION_MS = 500L;
    private static final long UNLOCK_ANIMATION_DURATION_MS = 1200L;
    private static final long AFFORDANCE_ON_DURATION_MS = 200L;
    private static final long AFFORDANCE_OFF_DURATION_MS = 1100L;
    private static final float GLOBAL_ALPHA = 0.8f;
    private static final float FOG_MAX_ALPHA = 0.6f;
    // The Note 4 oracle reserves highlight headroom for additive flares. Apply the
    // measured 8% compensation only to broadly saturated backgrounds: the matched
    // S23 wallpaper had 39.74% of pixels at max(R,G,B) >= 240, versus 0.15% on the
    // oracle. Sampling happens once per background update, never per frame.
    private static final float HIGH_BACKGROUND_DIM_ALPHA = 20f / 255f;
    private static final int BACKGROUND_HIGHLIGHT_CHANNEL = 240;
    private static final float BACKGROUND_HIGHLIGHT_FRACTION = 0.10f;
    private static final int BACKGROUND_BRIGHTNESS_SAMPLE_SIDE = 64;
    private static final float DEFAULT_IN_SAMPLE_SIZE = 2f;
    private static final float BASE_FINGER_Y_OFFSET_PX = -80f;
    private static final float BASE_MAX_ALPHA_DISTANCE_PX = 1500f;
    private static final float BASE_TAP_AREA_RADIUS_PX = 600f;
    private static final float BASE_SCREEN_WIDTH_PX = 1080f;
    private static final int TAP_HEXAGON_TOTAL = 5;
    private static final int DRAG_HEXAGON_TOTAL = 6;
    private static final String ADDITIVE_COMPOSITE_SHADER =
            "uniform shader flare;"
            + "uniform shader background;"
            + "uniform shader vignette;"
            + "uniform float vignetteAlpha;"
            + "uniform float backgroundDimAlpha;"
            + "half4 main(float2 p) {"
            + "  float4 f = float4(flare.eval(p));"
            + "  float flareAlpha = clamp(f.a, 0.0, 1.0);"
            + "  float3 flareRgb = min(max(f.rgb, float3(0.0)), float3(flareAlpha));"
            + "  float4 b = float4(background.eval(p));"
            + "  float mask = clamp(float(vignette.eval(p).a) * vignetteAlpha, 0.0, 1.0);"
            + "  float3 base = b.rgb * (1.0 - mask)"
            + "      * (1.0 - clamp(backgroundDimAlpha, 0.0, 1.0));"
            + "  float3 target = min(base + flareRgb, float3(1.0));"
            + "  float3 delta = max(target - base, float3(0.0));"
            + "  float3 room = max(float3(0.0001), float3(1.0) - base);"
            + "  float requiredA = clamp(max(max(delta.r / room.r, delta.g / room.g),"
            + "      delta.b / room.b), 0.0, 1.0);"
            + "  float a = min(requiredA, flareAlpha);"
            + "  float3 premul = min(float3(a), delta + base * a);"
            + "  return half4(premul, a);"
            + "}";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG
            | Paint.DITHER_FLAG);
    private final Paint additivePaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG
            | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final Random random = new Random();
    private final Bitmap flareLight;
    private final Bitmap flareRing;
    private final Bitmap flareParticle;
    private final Bitmap flareLong;
    private final Bitmap flareRainbow;
    private final Bitmap flareHoverLight;
    private final Bitmap flareVignetting;
    private final Bitmap[] tapHexagons;
    private final Bitmap[] dragHexagons;
    private final float[] tapHexagonRotations = new float[TAP_HEXAGON_TOTAL];
    private final float[] dragHexagonDistance = new float[DRAG_HEXAGON_TOTAL];
    private final float[] dragHexagonScale = new float[DRAG_HEXAGON_TOTAL];
    private final float[] dragHexagonRotations = new float[DRAG_HEXAGON_TOTAL];
    private final float fingerYOffsetPx;
    private final float maxAlphaDistancePx;
    private final float tapAreaRadiusPx;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;
    private final FlareContentView flareContentView;
    private final UnlockEffectReadinessCoordinator readiness =
            new UnlockEffectReadinessCoordinator(this, "Lens Flare");
    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private String backgroundSource = "none";
    private RuntimeShader additiveCompositeShader;
    private BitmapShader backgroundShader;
    private BitmapShader vignetteShader;
    private float backgroundDimAlpha;

    private boolean destroyed;
    private boolean warmUpPending;
    private boolean warmedUp;
    private boolean gestureActive;
    private boolean fading;
    private float startX;
    private float startY;
    private float currentX;
    private float currentY;
    private float fadeX;
    private float fadeY;
    private long gestureStartedAt;
    private long fadeStartedAt;
    private float fadeFogAnimationValue;
    private float randomRotation;
    private TapAnimation tapAnimation;
    private UnlockAnimation unlockAnimation;
    private AffordanceAnimation affordanceAnimation;
    private float pendingAffordanceX;
    private float pendingAffordanceY;
    private final Runnable unlockAffordanceRunnable = new Runnable() {
        @Override
        public void run() {
            playUnlockAffordance(pendingAffordanceX, pendingAffordanceY);
        }
    };

    public LensFlareEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        additivePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));

        String assetPrefix = lensFlareAssetPrefix(context);
        flareLight = loadDrawable(assetPrefix + "light_00040");
        flareRing = loadDrawable(assetPrefix + "ring");
        flareParticle = loadDrawable(assetPrefix + "particle");
        flareLong = loadDrawable(assetPrefix + "long");
        flareRainbow = loadDrawable(assetPrefix + "rainbow");
        flareHoverLight = loadDrawable(assetPrefix + "hoverlight");
        Bitmap vignetting = loadDrawable(assetPrefix + "vignetting");
        flareVignetting = vignetting != null
                ? vignetting
                : loadDrawable("keyguard_flare_vignetting");
        Bitmap hexagonBlue = loadDrawable(assetPrefix + "hexagon_blue");
        Bitmap hexagonOrange = loadDrawable(assetPrefix + "hexagon_orange");
        Bitmap hexagonGreen = loadDrawable(assetPrefix + "hexagon_green");
        tapHexagons = new Bitmap[] {
                hexagonBlue,
                hexagonOrange,
                hexagonGreen
        };
        dragHexagons = new Bitmap[] {
                hexagonBlue,
                hexagonOrange,
                hexagonBlue,
                hexagonOrange,
                hexagonGreen,
                hexagonGreen
        };
        flareContentView = new FlareContentView(context);
        addView(flareContentView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        prepareBitmapsForDraw();
        for (int i = 0; i < tapHexagonRotations.length; i++) {
            tapHexagonRotations[i] = random.nextInt(360);
        }
        for (int i = 0; i < dragHexagonRotations.length; i++) {
            dragHexagonRotations[i] = random.nextFloat() * 20f;
        }

        float ratio = screenScaleRatio();
        fingerYOffsetPx = BASE_FINGER_Y_OFFSET_PX * ratio;
        maxAlphaDistancePx = BASE_MAX_ALPHA_DISTANCE_PX * ratio;
        tapAreaRadiusPx = BASE_TAP_AREA_RADIUS_PX * ratio;

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        tapSound = soundPool.load(context, R.raw.lens_flare_tap, 1);
        unlockSound = soundPool.load(context, R.raw.lens_flare_unlock, 1);
        Log.i(TAG, "S4 lens flare Canvas renderer loaded ratio=" + ratio
                + " yOffset=" + Math.round(fingerYOffsetPx)
                + " maxAlphaDistance=" + Math.round(maxAlphaDistancePx)
                + " tapRadius=" + Math.round(tapAreaRadiusPx));
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S4 lens flare";
    }

    @Override
    public int getReadinessState() {
        return readiness.getState();
    }

    @Override
    public String getReadinessDetail() {
        return readiness.getDetail();
    }

    @Override
    public void setReadinessListener(ReadinessListener listener) {
        readiness.setListener(listener);
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        cancelUnlockAffordance();
        warmedUp = true;
        long now = SystemClock.uptimeMillis();
        gestureActive = true;
        fading = false;
        startX = screenX;
        startY = visualY(screenY);
        currentX = startX;
        currentY = startY;
        fadeStartedAt = 0L;
        fadeFogAnimationValue = 0f;
        gestureStartedAt = now;
        randomRotation = random.nextInt(360);
        setHexagonRandomTarget();
        tapAnimation = createTapAnimation(startX, startY, now);
        unlockAnimation = null;
        play(tapSound);
        Log.i(TAG, "canvas lens flare begin x=" + Math.round(startX)
                + " y=" + Math.round(startY));
        invalidateEffect();
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        currentX = screenX;
        currentY = visualY(screenY);
        invalidateEffect();
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        gestureActive = false;
        fadeX = currentX;
        fadeY = currentY;
        fadeStartedAt = now;
        fadeFogAnimationValue = currentFogAnimationValue(now);
        fading = true;
        if (completed) {
            unlockAnimation = new UnlockAnimation(
                    startX,
                    startY,
                    currentX,
                    currentY,
                    now,
                    unlockRotation());
            play(unlockSound);
        }
        Log.i(TAG, "canvas lens flare finish completed=" + completed
                + " x=" + Math.round(currentX)
                + " y=" + Math.round(currentY));
        invalidateEffect();
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        fading = true;
        fadeX = currentX;
        fadeY = currentY;
        fadeStartedAt = SystemClock.uptimeMillis();
        fadeFogAnimationValue = currentFogAnimationValue(fadeStartedAt);
        Log.i(TAG, "canvas lens flare cancel");
        invalidateEffect();
    }

    @Override
    public void resetEffect() {
        cancelUnlockAffordance();
        gestureActive = false;
        fading = false;
        tapAnimation = null;
        unlockAnimation = null;
        invalidateEffect();
    }

    @Override
    public void warmUp() {
        if (destroyed || warmedUp) {
            return;
        }
        warmUpPending = true;
        if (getWidth() > 0 && getHeight() > 0) {
            invalidateEffect();
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (destroyed) {
            return;
        }
        Rect rect = safeRect(screenRect);
        pendingAffordanceX = rect.exactCenterX();
        pendingAffordanceY = rect.exactCenterY();
        removeCallbacks(unlockAffordanceRunnable);
        postDelayed(unlockAffordanceRunnable, Math.max(0L, startDelayMs));
        Log.i(TAG, "lens flare affordance queued delayMs=" + startDelayMs
                + " center=" + Math.round(pendingAffordanceX)
                + "," + Math.round(pendingAffordanceY));
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return backgroundBitmap != null && !backgroundBitmap.isRecycled();
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        int width = getRenderWidth();
        int height = getRenderHeight();
        boolean borrow = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, width, height);
        Bitmap next = borrow ? source : createCenterCropBitmap(source, width, height);
        next.prepareToDraw();
        clearAdditiveComposite();
        recycleBackgroundBitmap();
        backgroundBitmap = next;
        ownsBackgroundBitmap = !borrow;
        backgroundSource = sourceName == null ? "external" : sourceName;
        updateAdaptiveBackgroundDim();
        configureAdditiveComposite();
        Log.i(TAG, "lens flare additive background ready source=" + backgroundSource
                + " size=" + backgroundBitmap.getWidth() + "x" + backgroundBitmap.getHeight()
                + " dimAlpha=" + backgroundDimAlpha
                + " shader=" + (additiveCompositeShader != null));
        invalidateEffect();
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        clearAdditiveComposite();
        recycleBackgroundBitmap();
        backgroundSource = "none";
        backgroundDimAlpha = 0f;
        invalidateEffect();
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
        clearBackgroundSourceBitmap();
        soundPool.release();
        readiness.destroyed();
    }

    @Override
    protected void onDetachedFromWindow() {
        resetEffect();
        warmUpPending = false;
        warmedUp = false;
        readiness.detached("HWUI layer detached");
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        readiness.attachCanvas();
        post(new Runnable() {
            @Override
            public void run() {
                warmUp();
            }
        });
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0 && backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && (backgroundBitmap.getWidth() != width
                || backgroundBitmap.getHeight() != height)) {
            Bitmap resized = createCenterCropBitmap(backgroundBitmap, width, height);
            clearAdditiveComposite();
            recycleBackgroundBitmap();
            backgroundBitmap = resized;
            ownsBackgroundBitmap = true;
            backgroundBitmap.prepareToDraw();
            updateAdaptiveBackgroundDim();
        }
        configureAdditiveComposite();
        if (warmUpPending && width > 0 && height > 0) {
            invalidateEffect();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float vignettingAlpha = currentVignettingAlpha(SystemClock.uptimeMillis());
        if (vignettingAlpha > 0f) {
            drawBitmapFitXY(canvas, flareVignetting, vignettingAlpha);
        }
        if (backgroundDimAlpha > 0f) {
            canvas.drawARGB(Math.round(backgroundDimAlpha * 255f), 0, 0, 0);
        }
        if (additiveCompositeShader != null) {
            additiveCompositeShader.setFloatUniform("vignetteAlpha", vignettingAlpha);
            additiveCompositeShader.setFloatUniform(
                    "backgroundDimAlpha", backgroundDimAlpha);
        }
    }

    private void drawFlareFrame(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean keepAnimating = false;

        if (warmUpPending) {
            drawWarmUpFrame(canvas);
            warmUpPending = false;
            warmedUp = true;
            readiness.canvasWarmFrameDrawn();
            Log.i(TAG, "canvas lens flare warmed");
        }

        if (gestureActive) {
            drawDragFlare(canvas, now, currentX, currentY, 1f,
                    currentFogAnimationValue(now));
            keepAnimating = true;
        } else if (fading) {
            float t = clamp01((now - fadeStartedAt) / (float) FADE_OUT_DURATION_MS);
            drawDragFlare(canvas, now, fadeX, fadeY, 1f - t,
                    fadeFogAnimationValue);
            keepAnimating = t < 1f;
            if (!keepAnimating) {
                fading = false;
            }
        }

        if (tapAnimation != null) {
            float t = clamp01((now - tapAnimation.startedAt) / (float) TAP_ANIMATION_DURATION_MS);
            if (t < 1f) {
                drawTapAnimation(canvas, tapAnimation, quintOut(t));
                keepAnimating = true;
            } else {
                tapAnimation = null;
            }
        }

        if (affordanceAnimation != null) {
            if (drawUnlockAffordance(canvas, now, affordanceAnimation)) {
                keepAnimating = true;
            } else {
                affordanceAnimation = null;
            }
        }

        if (unlockAnimation != null) {
            float t = clamp01((now - unlockAnimation.startedAt)
                    / (float) UNLOCK_ANIMATION_DURATION_MS);
            if (t < 1f) {
                drawUnlockAnimation(canvas, unlockAnimation, quintOut(t));
                keepAnimating = true;
            } else {
                unlockAnimation = null;
            }
        }

        if (keepAnimating) {
            flareContentView.postInvalidateOnAnimation();
            LensFlareEffectView.this.postInvalidateOnAnimation();
        }
    }

    private void drawWarmUpFrame(Canvas canvas) {
        drawAdditiveBitmapCentered(canvas, flareLight, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawAdditiveBitmapCentered(canvas, flareRing, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawAdditiveBitmapCentered(canvas, flareParticle, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawAdditiveBitmapCentered(canvas, flareLong, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawAdditiveBitmapCentered(canvas, flareRainbow, 0.5f, 0.5f, 1f, 0.004f, 0f);
        drawAdditiveBitmapCentered(canvas, flareHoverLight, 0.5f, 0.5f, 1f, 0.004f, 0f);
        for (int i = 0; i < tapHexagons.length; i++) {
            drawAdditiveBitmapCentered(canvas, tapHexagons[i],
                    0.5f, 0.5f, 1f, 0.004f, 0f);
        }
        for (int i = 0; i < dragHexagons.length; i++) {
            drawAdditiveBitmapCentered(canvas, dragHexagons[i],
                    0.5f, 0.5f, 1f, 0.004f, 0f);
        }
    }

    private void drawDragFlare(Canvas canvas, long now, float x, float y, float fadeAlpha,
            float fogAnimationValue) {
        float objValue = quintOut(clamp01((now - gestureStartedAt)
                / (float) SHOW_ANIMATION_DURATION_MS));
        float distance = (float) Math.hypot(x - startX, y - startY);
        float distanceAlpha = clamp01(distance / maxAlphaDistancePx);
        float fogAlpha = clamp01(fogAnimationValue * (1f - distanceAlpha))
                * GLOBAL_ALPHA * fadeAlpha;
        float objAlpha = clamp01(distanceAlpha * 3f) * fadeAlpha;
        float rotation = -objValue * 30f - distanceAlpha * 160f;
        float lightScale = 1f + distanceAlpha;

        drawAdditiveBitmapCentered(canvas, flareLight, x, y,
                bitmapSize(flareLight, lightScale), fogAlpha, rotation);

        if (objAlpha <= 0f) {
            return;
        }

        for (int i = 0; i < DRAG_HEXAGON_TOTAL; i++) {
            Bitmap hexagon = dragHexagons[i];
            float animationScale = 0.5f + objValue * 0.5f;
            float byDistanceScale = 0.5f + (distance / 720f) * 0.5f;
            float scale = dragHexagonScale[i] * byDistanceScale * animationScale;
            float pathScale = dragHexagonDistance[i] * animationScale;
            float tx = startX + (x - startX) * pathScale;
            float ty = startY + (y - startY) * pathScale;
            drawAdditiveBitmapCentered(canvas, hexagon, tx, ty,
                    bitmapSize(hexagon, scale), objAlpha, dragHexagonRotations[i]);
        }
    }

    private void drawTapAnimation(Canvas canvas, TapAnimation animation, float value) {
        float alpha = value < 0.5f ? 1f : 1f - (value - 0.5f) * 2f;
        alpha = clamp01(alpha) * GLOBAL_ALPHA;
        float distanceScale = 0.2f + 0.8f * value;

        for (int i = 0; i < animation.hexagons.length; i++) {
            TapHexagon hexagon = animation.hexagons[i];
            float scale = hexagon.scale * (value * 0.8f + 0.7f);
            float x = animation.x + hexagon.dx * distanceScale;
            float y = animation.y + hexagon.dy * distanceScale;
            drawAdditiveBitmapCentered(canvas, hexagon.bitmap, x, y,
                    bitmapSize(hexagon.bitmap, scale), alpha, hexagon.rotation);
        }

        float particleValue = value * 1.8f;
        float particleAlpha = pulseAlpha(particleValue);
        drawAdditiveBitmapCentered(canvas, flareParticle, animation.x, animation.y,
                bitmapSize(flareParticle, value * 1.2f), particleAlpha,
                animation.rotation);

        float ringValue = value * 1.4f;
        float ringAlpha = pulseAlpha(ringValue);
        drawAdditiveBitmapCentered(canvas, flareRing, animation.x, animation.y,
                bitmapSize(flareRing, 0.5f + value), ringAlpha, 0f);
        drawAdditiveBitmapCentered(canvas, flareLong, animation.x, animation.y,
                bitmapSize(flareLong, 1.5f + value * 2f), ringAlpha,
                animation.rotation + 30f * value);
    }

    private boolean drawUnlockAffordance(Canvas canvas, long now,
            AffordanceAnimation animation) {
        long elapsed = now - animation.startedAt;
        float alpha;
        if (elapsed < AFFORDANCE_ON_DURATION_MS) {
            alpha = FOG_MAX_ALPHA * clamp01(elapsed / (float) AFFORDANCE_ON_DURATION_MS);
        } else if (elapsed < AFFORDANCE_ON_DURATION_MS + AFFORDANCE_OFF_DURATION_MS) {
            float offT = (elapsed - AFFORDANCE_ON_DURATION_MS)
                    / (float) AFFORDANCE_OFF_DURATION_MS;
            alpha = FOG_MAX_ALPHA * (1f - clamp01(offT));
        } else {
            return false;
        }
        drawAdditiveBitmapCentered(canvas, flareLight, animation.x, animation.y,
                bitmapSize(flareLight, 1f), alpha, 0f);
        return true;
    }

    private void drawUnlockAnimation(Canvas canvas, UnlockAnimation animation, float value) {
        float alpha = value < 0.5f ? value * 2f : 1f - (value - 0.5f) * 2f;
        float x = animation.startX + (animation.endX - animation.startX) * 0.4f;
        float y = animation.startY + (animation.endY - animation.startY) * 0.4f;
        drawAdditiveBitmapCentered(canvas, flareRainbow, x, y,
                bitmapSize(flareRainbow, 1f + value * 1.3f),
                clamp01(alpha), animation.rotation);
    }

    private TapAnimation createTapAnimation(float x, float y, long now) {
        TapHexagon[] animationHexagons = new TapHexagon[TAP_HEXAGON_TOTAL];
        for (int i = 0; i < animationHexagons.length; i++) {
            float angle = randomRotation;
            float distance = random.nextFloat() * tapAreaRadiusPx;
            float dx = (float) Math.cos(angle) * distance;
            float dy = (float) Math.sin(angle) * distance;
            float scale = 0.2f + random.nextFloat() * 0.8f;
            Bitmap bitmap = tapHexagons[i % tapHexagons.length];
            animationHexagons[i] = new TapHexagon(
                    dx,
                    dy,
                    scale,
                    bitmap,
                    tapHexagonRotations[i]);
        }
        return new TapAnimation(x, y, now, randomRotation, animationHexagons);
    }

    private void playUnlockAffordance(float x, float y) {
        if (destroyed || gestureActive) {
            return;
        }
        warmedUp = true;
        randomRotation = random.nextInt(360);
        setHexagonRandomTarget();
        long now = SystemClock.uptimeMillis();
        tapAnimation = createTapAnimation(x, y, now);
        affordanceAnimation = new AffordanceAnimation(x, y, now);
        invalidateEffect();
        Log.i(TAG, "lens flare affordance play center="
                + Math.round(x) + "," + Math.round(y));
    }

    private void cancelUnlockAffordance() {
        removeCallbacks(unlockAffordanceRunnable);
        affordanceAnimation = null;
    }

    private Rect safeRect(Rect rect) {
        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            return rect;
        }
        int width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int height = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        return new Rect(0, 0, Math.max(1, width), Math.max(1, height));
    }

    private void setHexagonRandomTarget() {
        float startDistance = 0.2f;
        float distanceGap = 0.24f;
        for (int i = 0; i < DRAG_HEXAGON_TOTAL; i++) {
            float distance = startDistance + i * distanceGap
                    + (random.nextFloat() - 0.5f) * 0.4f;
            dragHexagonDistance[i] = distance;
            dragHexagonScale[i] = dragHexagonDistance[i] + 0.1f;
        }
        for (int i = DRAG_HEXAGON_TOTAL - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            float distance = dragHexagonDistance[i];
            dragHexagonDistance[i] = dragHexagonDistance[swapIndex];
            dragHexagonDistance[swapIndex] = distance;
            float scale = dragHexagonScale[i];
            dragHexagonScale[i] = dragHexagonScale[swapIndex];
            dragHexagonScale[swapIndex] = scale;
        }
    }

    private float unlockRotation() {
        float dx = currentX - startX;
        float dy = currentY - startY;
        if (Math.abs(dx) < 1f && Math.abs(dy) < 1f) {
            return randomRotation;
        }
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 40f;
    }

    private void drawBitmapCentered(Canvas canvas, Bitmap bitmap, float cx, float cy,
            float targetSize, float alpha, float rotation) {
        drawBitmapCentered(canvas, bitmap, cx, cy, targetSize, alpha, rotation, paint);
    }

    private void drawAdditiveBitmapCentered(Canvas canvas, Bitmap bitmap, float cx, float cy,
            float targetSize, float alpha, float rotation) {
        drawBitmapCentered(canvas, bitmap, cx, cy, targetSize, alpha, rotation, additivePaint);
    }

    private void drawBitmapCentered(Canvas canvas, Bitmap bitmap, float cx, float cy,
            float targetSize, float alpha, float rotation, Paint drawPaint) {
        if (bitmap == null || alpha <= 0f || targetSize <= 0f) {
            return;
        }
        float scale = targetSize / Math.max(bitmap.getWidth(), bitmap.getHeight());
        matrix.reset();
        matrix.postTranslate(-bitmap.getWidth() * 0.5f, -bitmap.getHeight() * 0.5f);
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        matrix.postTranslate(cx, cy);
        drawPaint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        canvas.drawBitmap(bitmap, matrix, drawPaint);
        drawPaint.setAlpha(255);
    }

    private void drawBitmapFitXY(Canvas canvas, Bitmap bitmap, float alpha) {
        if (bitmap == null || alpha <= 0f || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        matrix.reset();
        matrix.setScale(getWidth() / (float) bitmap.getWidth(),
                getHeight() / (float) bitmap.getHeight());
        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        canvas.drawBitmap(bitmap, matrix, paint);
        paint.setAlpha(255);
    }

    private float bitmapSize(Bitmap bitmap, float scale) {
        if (bitmap == null) {
            return 0f;
        }
        return Math.max(bitmap.getWidth(), bitmap.getHeight())
                * DEFAULT_IN_SAMPLE_SIZE
                * Math.max(0f, scale);
    }

    private static String lensFlareAssetPrefix(Context context) {
        return "keyguard_" + OverlayPrefs.lensFlareMode(context) + "_";
    }

    private Bitmap loadDrawable(String name) {
        int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
        if (id == 0) {
            Log.w(TAG, "missing lens flare drawable " + name);
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(getResources(), id, options);
    }

    private void prepareBitmapsForDraw() {
        prepareBitmap(flareLight);
        prepareBitmap(flareRing);
        prepareBitmap(flareParticle);
        prepareBitmap(flareLong);
        prepareBitmap(flareRainbow);
        prepareBitmap(flareHoverLight);
        prepareBitmap(flareVignetting);
        for (int i = 0; i < tapHexagons.length; i++) {
            prepareBitmap(tapHexagons[i]);
        }
        for (int i = 0; i < dragHexagons.length; i++) {
            prepareBitmap(dragHexagons[i]);
        }
    }

    private void prepareBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.prepareToDraw();
        }
    }

    private void play(int soundId) {
        if (!destroyed && soundId != 0
                && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private float visualY(float screenY) {
        return screenY + fingerYOffsetPx;
    }

    private float currentVignettingAlpha(long now) {
        float x;
        float y;
        float fadeAlpha;
        if (gestureActive) {
            x = currentX;
            y = currentY;
            fadeAlpha = 1f;
        } else if (fading) {
            x = fadeX;
            y = fadeY;
            fadeAlpha = 1f - clamp01((now - fadeStartedAt)
                    / (float) FADE_OUT_DURATION_MS);
        } else {
            return 0f;
        }
        float distance = (float) Math.hypot(x - startX, y - startY);
        return clamp01((distance / maxAlphaDistancePx) * 1.3f) * fadeAlpha;
    }

    private void invalidateEffect() {
        super.invalidate();
        if (flareContentView != null) {
            flareContentView.invalidate();
        }
    }

    private int getRenderWidth() {
        int width = getWidth();
        return width > 0 ? width : Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        int height = getHeight();
        return height > 0 ? height : Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    private Bitmap createCenterCropBitmap(Bitmap source, int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (source.getWidth() == safeWidth && source.getHeight() == safeHeight) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        Bitmap out = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888);
        float srcRatio = source.getWidth() / (float) source.getHeight();
        float dstRatio = safeWidth / (float) safeHeight;
        Rect srcRect;
        if (srcRatio > dstRatio) {
            int srcWidth = Math.max(1, Math.round(source.getHeight() * dstRatio));
            int left = Math.max(0, (source.getWidth() - srcWidth) / 2);
            srcRect = new Rect(left, 0,
                    Math.min(source.getWidth(), left + srcWidth), source.getHeight());
        } else {
            int srcHeight = Math.max(1, Math.round(source.getWidth() / dstRatio));
            int top = Math.max(0, (source.getHeight() - srcHeight) / 2);
            srcRect = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + srcHeight));
        }
        Canvas canvas = new Canvas(out);
        Paint copyPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
                | Paint.DITHER_FLAG);
        canvas.drawBitmap(source, srcRect, new Rect(0, 0, safeWidth, safeHeight), copyPaint);
        return out;
    }

    private void configureAdditiveComposite() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || backgroundBitmap == null || backgroundBitmap.isRecycled()
                || flareVignetting == null || flareVignetting.isRecycled()
                || flareContentView == null) {
            clearAdditiveComposite();
            return;
        }
        try {
            backgroundShader = new BitmapShader(backgroundBitmap,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            vignetteShader = new BitmapShader(flareVignetting,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix vignetteMatrix = new Matrix();
            vignetteMatrix.setScale(getRenderWidth() / (float) flareVignetting.getWidth(),
                    getRenderHeight() / (float) flareVignetting.getHeight());
            vignetteShader.setLocalMatrix(vignetteMatrix);
            additiveCompositeShader = new RuntimeShader(ADDITIVE_COMPOSITE_SHADER);
            additiveCompositeShader.setInputShader("background", backgroundShader);
            additiveCompositeShader.setInputShader("vignette", vignetteShader);
            additiveCompositeShader.setFloatUniform("vignetteAlpha", 0f);
            additiveCompositeShader.setFloatUniform(
                    "backgroundDimAlpha", backgroundDimAlpha);
            flareContentView.setRenderEffect(RenderEffect.createRuntimeShaderEffect(
                    additiveCompositeShader, "flare"));
        } catch (Throwable t) {
            Log.w(TAG, "lens flare additive background shader unavailable; using ADD fallback", t);
            clearAdditiveComposite();
        }
    }

    private void clearAdditiveComposite() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && flareContentView != null) {
            flareContentView.setRenderEffect(null);
        }
        additiveCompositeShader = null;
        backgroundShader = null;
        vignetteShader = null;
    }

    private void updateAdaptiveBackgroundDim() {
        if (backgroundBitmap == null || backgroundBitmap.isRecycled()) {
            backgroundDimAlpha = 0f;
            return;
        }
        int width = backgroundBitmap.getWidth();
        int height = backgroundBitmap.getHeight();
        int columns = Math.max(1, Math.min(BACKGROUND_BRIGHTNESS_SAMPLE_SIDE, width));
        int rows = Math.max(1, Math.min(BACKGROUND_BRIGHTNESS_SAMPLE_SIDE, height));
        int brightSamples = 0;
        int sampleCount = 0;
        try {
            for (int row = 0; row < rows; row++) {
                int y = rows == 1 ? 0 : Math.round(row * (height - 1f) / (rows - 1f));
                for (int column = 0; column < columns; column++) {
                    int x = columns == 1
                            ? 0 : Math.round(column * (width - 1f) / (columns - 1f));
                    int color = backgroundBitmap.getPixel(x, y);
                    if (Math.max(Color.red(color),
                            Math.max(Color.green(color), Color.blue(color)))
                            >= BACKGROUND_HIGHLIGHT_CHANNEL) {
                        brightSamples++;
                    }
                    sampleCount++;
                }
            }
        } catch (RuntimeException error) {
            backgroundDimAlpha = 0f;
            Log.w(TAG, "lens flare background brightness sample unavailable", error);
            return;
        }
        float brightFraction = sampleCount <= 0 ? 0f : brightSamples / (float) sampleCount;
        backgroundDimAlpha = brightFraction >= BACKGROUND_HIGHLIGHT_FRACTION
                ? HIGH_BACKGROUND_DIM_ALPHA : 0f;
        Log.i(TAG, "lens flare adaptive background dim source=" + backgroundSource
                + " brightFraction=" + brightFraction
                + " threshold=" + BACKGROUND_HIGHLIGHT_FRACTION
                + " channel=" + BACKGROUND_HIGHLIGHT_CHANNEL
                + " dimAlpha=" + backgroundDimAlpha
                + " samples=" + sampleCount);
    }

    private void recycleBackgroundBitmap() {
        if (ownsBackgroundBitmap
                && backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = null;
        ownsBackgroundBitmap = false;
    }

    private float currentFogAnimationValue(long now) {
        return FOG_MAX_ALPHA * quintOut(clamp01((now - gestureStartedAt)
                / (float) FOG_ON_DURATION_MS));
    }

    private float screenScaleRatio() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int smallestWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
        if (smallestWidth <= 0 || smallestWidth == (int) BASE_SCREEN_WIDTH_PX) {
            return 1f;
        }
        return smallestWidth / BASE_SCREEN_WIDTH_PX;
    }

    private float pulseAlpha(float value) {
        float corrected = value < 0.5f ? 1f : 1f - (value - 0.5f) * 2f;
        return clamp01(corrected);
    }

    private float quintOut(float value) {
        float inverse = 1f - clamp01(value);
        return 1f - inverse * inverse * inverse * inverse * inverse;
    }

    private float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private static final class TapAnimation {
        final float x;
        final float y;
        final long startedAt;
        final float rotation;
        final TapHexagon[] hexagons;

        TapAnimation(float x, float y, long startedAt, float rotation,
                TapHexagon[] hexagons) {
            this.x = x;
            this.y = y;
            this.startedAt = startedAt;
            this.rotation = rotation;
            this.hexagons = hexagons;
        }
    }

    private static final class TapHexagon {
        final float dx;
        final float dy;
        final float scale;
        final Bitmap bitmap;
        final float rotation;

        TapHexagon(float dx, float dy, float scale, Bitmap bitmap, float rotation) {
            this.dx = dx;
            this.dy = dy;
            this.scale = scale;
            this.bitmap = bitmap;
            this.rotation = rotation;
        }
    }

    private static final class AffordanceAnimation {
        final float x;
        final float y;
        final long startedAt;

        AffordanceAnimation(float x, float y, long startedAt) {
            this.x = x;
            this.y = y;
            this.startedAt = startedAt;
        }
    }

    private static final class UnlockAnimation {
        final float startX;
        final float startY;
        final float endX;
        final float endY;
        final long startedAt;
        final float rotation;

        UnlockAnimation(float startX, float startY, float endX, float endY,
                long startedAt, float rotation) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.startedAt = startedAt;
            this.rotation = rotation;
        }
    }

    private final class FlareContentView extends View {
        FlareContentView(Context context) {
            super(context);
            setWillNotDraw(false);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawFlareFrame(canvas);
        }
    }
}
