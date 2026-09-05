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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Canvas port of LG's two Hula Hoop revisions. V1 is the G-era Color Layered renderer; V2 is
 * the G4 FluidicRenderer recovered from H81510E. The lockscreen remains live below the overlay;
 * Last Screen is revealed only through the opening anchored at ACTION_DOWN.
 */
public final class LgHulaHoopEffectView extends View implements UnlockEffectRenderer,
        BackgroundSourceRenderer, SecondaryBackgroundSourceRenderer, UnlockEffectReadiness {
    private static final int[] LAYER_RESOURCES = {
        R.drawable.lg_hula_layer0, R.drawable.lg_hula_layer1,
        R.drawable.lg_hula_layer2, R.drawable.lg_hula_layer3
    };
    private static final int[] UNLOCK_SOUNDS = {
        R.raw.lg_hula_unlock1, R.raw.lg_hula_unlock2,
        R.raw.lg_hula_unlock3, R.raw.lg_hula_unlock4
    };
    private static final int REFLECTION_COUNT = 5;
    private static final float STOCK_OUTER_RING_RADIUS_DP = 128f;
    private static final float DONOR_XHDPI_DENSITY = 2f;
    private static final int FLUIDIC_BLUE = Color.rgb(8, 39, 204);
    private static final int FLUIDIC_CYAN = Color.rgb(67, 207, 251);
    private static final int FLUIDIC_MAGENTA = Color.rgb(238, 65, 159);
    private static final int FLUIDIC_OVERLAP = Color.rgb(107, 76, 165);
    private static final int FLUIDIC_ALPHA = Math.round(255f * .8f);

    private final LgHulaHoopScene scene;
    private final LgHulaHoopScene.Frame frame = new LgHulaHoopScene.Frame();
    private final LgHulaHoopFluidicScene fluidicScene = new LgHulaHoopFluidicScene();
    private final LgHulaHoopFluidicScene.Frame fluidicFrame =
            new LgHulaHoopFluidicScene.Frame();
    private final FluidicMesh fluidicHole = new FluidicMesh();
    private final FluidicMesh fluidicCyan = new FluidicMesh();
    private final FluidicMesh fluidicMagenta = new FluidicMesh();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint layerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint reflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fluidicPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path revealPath = new Path();
    private final Path fluidicHolePath = new Path();
    private final Path fluidicCyanPath = new Path();
    private final Path fluidicMagentaPath = new Path();
    private final RectF destination = new RectF();
    private final Matrix sourceMatrix = new Matrix();
    private final DisplayMetrics displayMetrics = new DisplayMetrics();
    private final int[] location = new int[2];
    private final Bitmap[] layers = new Bitmap[LAYER_RESOURCES.length];
    private final Bitmap innerRing;
    private final Bitmap outerRing;
    private final Bitmap decoCircle;
    private final float[] reflectionX = new float[REFLECTION_COUNT];
    private final float[] reflectionY = new float[REFLECTION_COUNT];
    private final float[] reflectionScaleX = new float[REFLECTION_COUNT];
    private final float[] reflectionScaleY = new float[REFLECTION_COUNT];
    private final float[] reflectionAlpha = new float[REFLECTION_COUNT];
    private final Random reflectionRandom = new Random();
    private final SoundPool soundPool;
    private final int[] soundIds = new int[UNLOCK_SOUNDS.length];
    private final Set<Integer> loadedSounds = new HashSet<Integer>();
    private final Random soundRandom = new Random();
    private final Random fluidicRandom = new Random();
    private final int variant;
    private final int fluidicTouchdownSound;
    private final int fluidicUnlockSound;
    private int pendingSound;
    private float fluidicCyanOffset;
    private float fluidicMagentaOffset;
    private int previousFluidicRotationDelay;
    private Bitmap lockscreen;
    private Bitmap lastScreen;
    private BitmapShader lockscreenShader;
    private BitmapShader lastScreenShader;
    private boolean destroyed;
    private boolean firstFrameDrawn;
    private ReadinessListener readinessListener;
    private float hintX;
    private float hintY;
    private boolean reflectionsPrepared;
    private int reflectionRotationCycleMs = 3_000;
    private final Runnable hintStart = new Runnable() {
        @Override public void run() {
            if (!ready()) return;
            updateGeometry();
            scene.startHint(hintX, hintY, SystemClock.uptimeMillis());
            postInvalidateOnAnimation();
        }
    };

    public LgHulaHoopEffectView(Context context) {
        this(context, OverlayPrefs.hulaHoopVariant(context));
    }

    LgHulaHoopEffectView(Context context, int variant) {
        super(context);
        this.variant = OverlayPrefs.normalizeHulaHoopVariant(variant);
        scene = new LgHulaHoopScene();
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        for (int i = 0; i < layers.length; i++) layers[i] = decode(LAYER_RESOURCES[i]);
        innerRing = decode(R.drawable.lg_hula_inner_ring);
        outerRing = decode(R.drawable.lg_hula_outer_ring);
        decoCircle = decode(R.drawable.lg_hula_deco_circle);
        soundPool = new SoundPool.Builder().setMaxStreams(1)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context)).build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(SoundPool pool, int id, int status) {
                if (destroyed || pool != soundPool) return;
                if (status == 0) loadedSounds.add(id);
                if (pendingSound == id) {
                    pendingSound = 0;
                    if (status == 0 && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
                        soundPool.play(id, 1f, 1f, 1, 0, 1f);
                    }
                }
            }
        });
        if (this.variant == OverlayPrefs.HULA_HOOP_VARIANT_V2) {
            fluidicTouchdownSound = soundPool.load(context, R.raw.lg_hula_v2_touchdown, 1);
            fluidicUnlockSound = soundPool.load(context, R.raw.lg_hula_v2_unlock, 1);
        } else {
            fluidicTouchdownSound = fluidicUnlockSound = 0;
            for (int i = 0; i < soundIds.length; i++) {
                soundIds[i] = soundPool.load(context, UNLOCK_SOUNDS[i], 1);
            }
        }
    }

    @Override public View asView() { return this; }
    @Override public String effectName() {
        return "Hula Hoop V" + variant;
    }

    private boolean ready() {
        return !destroyed && getWidth() > 0 && getHeight() > 0
                && hasBackgroundSourceBitmap() && hasSecondaryBackgroundSourceBitmap();
    }

    @Override public void beginGesture(float x, float y) {
        if (!ready()) return;
        removeCallbacks(hintStart);
        pendingSound = 0;
        updateGeometry();
        reflectionsPrepared = false;
        long now = SystemClock.uptimeMillis();
        if (variant == OverlayPrefs.HULA_HOOP_VARIANT_V2) {
            fluidicScene.begin(x - location[0], y - location[1], now);
            resetFluidicMeshes(now);
            playSound(fluidicTouchdownSound);
        } else {
            scene.begin(x - location[0], y - location[1], now);
        }
        postInvalidateOnAnimation();
    }

    @Override public void updateGesture(float x, float y) {
        if (!ready() || !gestureActive()) return;
        getLocationOnScreen(location);
        if (variant == OverlayPrefs.HULA_HOOP_VARIANT_V2) {
            fluidicScene.move(x - location[0], y - location[1], SystemClock.uptimeMillis());
        } else {
            scene.move(x - location[0], y - location[1], SystemClock.uptimeMillis());
        }
        postInvalidateOnAnimation();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || !gestureActive()) return;
        if (variant == OverlayPrefs.HULA_HOOP_VARIANT_V2) {
            fluidicScene.finish(completed, SystemClock.uptimeMillis());
            if (completed) playSound(fluidicUnlockSound);
        } else {
            scene.finish(completed, SystemClock.uptimeMillis());
            if (completed) playRandomUnlockSound();
        }
        postInvalidateOnAnimation();
    }

    @Override public void cancelGesture() { finishGesture(false); }

    @Override public void resetEffect() {
        removeCallbacks(hintStart);
        pendingSound = 0;
        reflectionsPrepared = false;
        scene.reset();
        fluidicScene.reset();
        invalidate();
    }

    @Override public void warmUp() {
        if (lockscreen != null) lockscreen.prepareToDraw();
        if (lastScreen != null) lastScreen.prepareToDraw();
        for (Bitmap layer : layers) if (layer != null) layer.prepareToDraw();
        invalidate();
    }

    @Override public void showUnlockAffordance(Rect bounds, long delayMs) {
        if (!ready()) return;
        // V2's stock presentation has no V1 idle icon/ping sequence. Avoid synthesising a
        // coloured dot over the live lockscreen while the accessibility hint is dispatched.
        if (variant == OverlayPrefs.HULA_HOOP_VARIANT_V2) {
            removeCallbacks(hintStart);
            fluidicScene.reset();
            invalidate();
            return;
        }
        updateGeometry();
        hintX = bounds != null && !bounds.isEmpty()
                ? bounds.exactCenterX() - location[0] : getWidth() * .5f;
        hintY = bounds != null && !bounds.isEmpty()
                ? bounds.exactCenterY() - location[1] : getHeight() * .5f;
        removeCallbacks(hintStart);
        postDelayed(hintStart, Math.max(0L, delayMs));
    }

    @Override public boolean hasBackgroundSourceBitmap() {
        return lockscreen != null && !lockscreen.isRecycled();
    }

    @Override public boolean hasSecondaryBackgroundSourceBitmap() {
        return lastScreen != null && !lastScreen.isRecycled();
    }

    @Override public void setBackgroundSourceBitmap(Bitmap source, String name) {
        Bitmap owned = ownedCopy(source);
        if (owned == null) return;
        releasePrimary();
        lockscreen = owned;
        lockscreenShader = new BitmapShader(owned, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        sourcesChanged();
    }

    @Override public void setSecondaryBackgroundSourceBitmap(Bitmap source, String name) {
        Bitmap owned = ownedCopy(source);
        if (owned == null) return;
        releaseSecondary();
        lastScreen = owned;
        lastScreenShader = new BitmapShader(owned, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        sourcesChanged();
    }

    @Override public void clearBackgroundSourceBitmap() {
        releasePrimary();
        sourcesChanged();
        resetEffect();
    }

    @Override public void clearSecondaryBackgroundSourceBitmap() {
        releaseSecondary();
        sourcesChanged();
        resetEffect();
    }

    @Override public int getReadinessState() {
        if (destroyed) return STATE_FAILED;
        if (!isAttachedToWindow()) return STATE_CONSTRUCTED;
        if (!isLaidOut()) return STATE_ATTACHED;
        if (!hasBackgroundSourceBitmap() || !hasSecondaryBackgroundSourceBitmap()) {
            return STATE_SURFACE_READY;
        }
        return firstFrameDrawn ? STATE_FIRST_FRAME_READY : STATE_RESOURCES_READY;
    }

    @Override public String getReadinessDetail() {
        if (destroyed) return effectName() + ": destroyed";
        if (!hasBackgroundSourceBitmap()) return effectName() + ": waiting for lockscreen capture";
        if (!hasSecondaryBackgroundSourceBitmap()) return effectName() + ": waiting for Last screen";
        return effectName() + (firstFrameDrawn
                ? ": both sources ready" : ": sources loaded; waiting for first frame");
    }

    @Override public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    @Override public void destroy() {
        if (destroyed) return;
        destroyed = true;
        removeCallbacks(hintStart);
        scene.reset();
        fluidicScene.reset();
        releasePrimary();
        releaseSecondary();
        for (Bitmap layer : layers) recycle(layer);
        recycle(innerRing);
        recycle(outerRing);
        recycle(decoCircle);
        loadedSounds.clear();
        pendingSound = 0;
        soundPool.setOnLoadCompleteListener(null);
        soundPool.release();
        readinessListener = null;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateGeometry();
        notifyReadiness();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            scene.reset();
            fluidicScene.reset();
        }
        firstFrameDrawn = false;
        updateGeometry();
    }

    @Override protected void onDetachedFromWindow() {
        resetEffect();
        firstFrameDrawn = false;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!ready()) return;
        if (!firstFrameDrawn) {
            updateGeometry();
            firstFrameDrawn = true;
            notifyReadiness();
        }
        long now = SystemClock.uptimeMillis();
        if (variant == OverlayPrefs.HULA_HOOP_VARIANT_V2) {
            drawFluidicV2(canvas, now);
            return;
        }
        LgHulaHoopScene.Frame current = scene.sample(now, frame);
        if (!current.visible) return;

        // The accessibility overlay must leave the live lockscreen untouched. The cached
        // lockscreen is a readiness/fallback source, not a fullscreen replacement layer.
        if (current.fullUnderlay) {
            drawSource(canvas, lastScreenShader, 1f);
        } else if (current.stage != LgHulaHoopScene.HINT) {
            revealPath.reset();
            revealPath.addCircle(current.x, current.y, current.radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(revealPath);
            drawSource(canvas, lastScreenShader, 1f);
            canvas.restoreToCount(save);
        }

        if (current.stage != LgHulaHoopScene.HINT
                && current.backgroundAlpha > .001f) {
            drawBackgroundReflections(canvas, current);
        }
        // Hint and gesture share the same corona renderer. Hint simply omits Last Screen and the
        // ambient reflections, leaving the live lockscreen visible through its clean center.
        drawColorLayers(canvas, current);

        if (current.running) postInvalidateOnAnimation();
    }

    /** Canvas equivalent of the G4 FluidicRenderer stencil passes. */
    private void drawFluidicV2(Canvas canvas, long now) {
        LgHulaHoopFluidicScene.Frame current = fluidicScene.sample(now, fluidicFrame);
        if (!current.visible) return;
        if (current.fullUnderlay) {
            drawSource(canvas, lastScreenShader, 1f);
        } else {
            prepareFluidicGeometry(current, now);
            int reveal = canvas.save();
            canvas.clipPath(fluidicHolePath);
            drawSource(canvas, lastScreenShader, 1f);
            canvas.restoreToCount(reveal);

            if (current.drawColors) {
                // The donor increments a stencil once for each of the three meshes. Pixels
                // covered by exactly one mesh receive its own color, pixels covered by exactly
                // two receive COLOR6, and the common three-way center remains transparent.
                drawFluidicSingle(canvas, fluidicHolePath,
                        fluidicCyanPath, fluidicMagentaPath, FLUIDIC_BLUE);
                drawFluidicSingle(canvas, fluidicCyanPath,
                        fluidicHolePath, fluidicMagentaPath, FLUIDIC_CYAN);
                drawFluidicSingle(canvas, fluidicMagentaPath,
                        fluidicHolePath, fluidicCyanPath, FLUIDIC_MAGENTA);
                drawFluidicPair(canvas, fluidicHolePath,
                        fluidicCyanPath, fluidicMagentaPath);
                drawFluidicPair(canvas, fluidicHolePath,
                        fluidicMagentaPath, fluidicCyanPath);
                drawFluidicPair(canvas, fluidicCyanPath,
                        fluidicMagentaPath, fluidicHolePath);
            }
        }
        if (current.running) postInvalidateOnAnimation();
    }

    private void prepareFluidicGeometry(LgHulaHoopFluidicScene.Frame current, long now) {
        if (current.rotationDelayFrames == 0) {
            if (previousFluidicRotationDelay > 0) resetFluidicRotationSpeeds();
            fluidicCyan.rotate(now);
            fluidicMagenta.rotate(now);
        }
        previousFluidicRotationDelay = current.rotationDelayFrames;

        if (current.stretched && current.stretchDelayFrames == 0) {
            fluidicCyan.setRadii(current.radius,
                    current.dragDistance * (.7f + fluidicRandom.nextFloat() * .4f));
            fluidicMagenta.setRadii(current.radius,
                    current.dragDistance * (.7f + fluidicRandom.nextFloat() * .4f));
            fluidicCyan.setAngle(current.angle + fluidicCyanOffset);
            fluidicMagenta.setAngle(current.angle + fluidicMagentaOffset);
            fluidicCyan.setPivot(fluidicCyanOffset, fluidicMagentaOffset, 1f);
            fluidicMagenta.setPivot(fluidicMagentaOffset, fluidicCyanOffset, 1f);
            fluidicHole.setRadii(current.radius, current.dragDistance);
            fluidicHole.setAngle(current.angle);
        } else {
            float ringRadius = current.stretchDelayFrames > 0 ? 0f
                    : Math.min(.12f * current.radius, fluidicScene.outerRingStride());
            float cyanDelay = current.unlock ? 1f + fluidicRandom.nextFloat() * .3f : 1f;
            float magentaDelay = current.unlock ? 1f + fluidicRandom.nextFloat() * .3f : 1f;
            float cyanRadius = current.radius + ringRadius * cyanDelay;
            float magentaRadius = current.radius + ringRadius * magentaDelay;
            fluidicCyan.setRadii(cyanRadius, cyanRadius);
            fluidicMagenta.setRadii(magentaRadius, magentaRadius);
            fluidicCyan.setPivot(ringRadius * .25f * cyanDelay, 0f, 1f);
            fluidicMagenta.setPivot(ringRadius * .25f * magentaDelay, 0f, 1f);
            fluidicHole.setRadii(current.radius, current.radius);
            // The stock renderer keeps the last gesture angle even while the target
            // relaxes back to a circle. Resetting it here visibly snaps a still-soft
            // stretched mesh whenever the finger slows down or the gesture closes.
            fluidicHole.setAngle(current.angle);
        }

        fluidicHole.setSoftbody(current.softbody);
        fluidicCyan.setSoftbody(current.softbody);
        fluidicMagenta.setSoftbody(current.softbody);
        fluidicHole.update(now);
        fluidicCyan.update(now);
        fluidicMagenta.update(now);
        fluidicHole.buildPath(fluidicHolePath, current.x, current.y);
        fluidicCyan.buildPath(fluidicCyanPath, current.x, current.y);
        fluidicMagenta.buildPath(fluidicMagentaPath, current.x, current.y);
    }

    private void resetFluidicMeshes(long now) {
        fluidicHole.reset(now);
        fluidicCyan.reset(now);
        fluidicMagenta.reset(now);
        fluidicHole.setAngle(0f);
        fluidicCyan.setAngle(fluidicRandom.nextFloat() * 120f);
        fluidicMagenta.setAngle(180f + fluidicRandom.nextFloat() * 120f);
        resetFluidicRotationSpeeds();
        previousFluidicRotationDelay = 0;
    }

    private void resetFluidicRotationSpeeds() {
        fluidicCyan.setRotationSpeed(randomFluidicRotationSpeed());
        fluidicMagenta.setRotationSpeed(randomFluidicRotationSpeed());
        fluidicCyanOffset = (fluidicRandom.nextFloat() * 2f - 1f) * 8f;
        fluidicMagentaOffset = (fluidicRandom.nextFloat() * 2f - 1f) * 8f;
    }

    private float randomFluidicRotationSpeed() {
        float sign = fluidicRandom.nextBoolean() ? -1f : 1f;
        return sign * (1f + fluidicRandom.nextFloat()) * .18f;
    }

    private void drawFluidicSingle(Canvas canvas, Path base, Path exclude1,
            Path exclude2, int color) {
        int save = canvas.save();
        canvas.clipPath(base);
        canvas.clipPath(exclude1, Region.Op.DIFFERENCE);
        canvas.clipPath(exclude2, Region.Op.DIFFERENCE);
        fillFluidicClip(canvas, color);
        canvas.restoreToCount(save);
    }

    private void drawFluidicPair(Canvas canvas, Path base, Path intersection,
            Path exclude) {
        int save = canvas.save();
        canvas.clipPath(base);
        canvas.clipPath(intersection);
        canvas.clipPath(exclude, Region.Op.DIFFERENCE);
        fillFluidicClip(canvas, FLUIDIC_OVERLAP);
        canvas.restoreToCount(save);
    }

    private void fillFluidicClip(Canvas canvas, int color) {
        fluidicPaint.setColor(color);
        fluidicPaint.setAlpha(FLUIDIC_ALPHA);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), fluidicPaint);
    }

    private void drawColorLayers(Canvas canvas, LgHulaHoopScene.Frame current) {
        layerPaint.setAlpha(Math.round(255f * LgHulaHoopScene.clamp(current.ringAlpha, 0f, 1f)));
        // The four donor textures are translucent filled discs. LG masks their shared center so
        // only the orbiting corona remains; without this clip their alpha stacks into an opaque
        // blue-green patch over Last Screen.
        revealPath.reset();
        revealPath.addCircle(current.x, current.y, current.radius, Path.Direction.CW);
        int coronaClip = canvas.save();
        canvas.clipPath(revealPath, Region.Op.DIFFERENCE);
        // Stock draws 0 -> 3, leaving layer 3 on top.
        for (int i = 0; i < layers.length; i++) {
            Bitmap layer = layers[i];
            if (layer == null || layer.isRecycled()) continue;
            // The stock holder center moves only a tiny display-normalized amount opposite the
            // finger. Most of the apparent orbit comes from rotating the bitmap around its
            // internal pivot, not from translating the whole layer across the screen.
            float cx = current.x + current.trailX * LgHulaHoopScene.LAYER_TRANSITION[i];
            float cy = current.y + current.trailY * LgHulaHoopScene.LAYER_TRANSITION[i];
            drawStockLayerHolder(canvas, layer, cx, cy,
                    current.layerRadius / Math.max(1f, layer.getWidth() * .5f),
                    current.angle + LgHulaHoopScene.LAYER_ANGLE_OFFSET[i],
                    current.pivotX, current.pivotY);
        }
        canvas.restoreToCount(coronaClip);

        // ColorLayeredCircleEffect.setX/setY is called on DOWN only. The hole, rings and unlock
        // icon therefore remain fixed while setFingerPos() displaces the four color layers.
        float ringCenterX = current.x;
        float ringCenterY = current.y;
        drawCentered(canvas, innerRing, ringCenterX, ringCenterY,
                current.radius * 2.02f, current.ringAlpha, 0f);
        float outerDiameter = STOCK_OUTER_RING_RADIUS_DP * displayMetrics.density * 2f;
        drawCentered(canvas, outerRing, ringCenterX, ringCenterY,
                outerDiameter, current.ringAlpha, -current.angle * .35f);
        layerPaint.setAlpha(255);
    }

    /** Exact transform order used by LG's LgeDrawableHolder.draw(). */
    private void drawStockLayerHolder(Canvas canvas, Bitmap bitmap, float x, float y,
            float scale, float angle, float pivotX, float pivotY) {
        if (scale <= 0f) return;
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        canvas.translate(bitmap.getWidth() * -.5f, bitmap.getHeight() * -.5f);
        canvas.rotate(angle,
                pivotX / scale + bitmap.getWidth() * .5f,
                pivotY / scale + bitmap.getHeight() * .5f);
        canvas.drawBitmap(bitmap, 0f, 0f, layerPaint);
        canvas.restoreToCount(save);
    }

    private void drawBackgroundReflections(Canvas canvas, LgHulaHoopScene.Frame current) {
        if (decoCircle == null || decoCircle.isRecycled()) return;
        if (!reflectionsPrepared) prepareReflections();
        float ageProgress = LgHulaHoopScene.clamp(current.ageMs / 300f, 0f, 1f);
        float globalScale = backgroundOpenScale(ageProgress);
        float globalAlpha = ageProgress * LgHulaHoopScene.clamp(
                current.backgroundAlpha, 0f, 1f);
        if (globalScale <= 0f || globalAlpha <= 0f) return;
        float donorDensityScale = displayMetrics.density / DONOR_XHDPI_DENSITY;
        float angle = current.ageMs * 360f / Math.max(1, reflectionRotationCycleMs);
        for (int i = 0; i < REFLECTION_COUNT; i++) {
            float halfW = decoCircle.getWidth() * .5f * donorDensityScale
                    * reflectionScaleX[i] * globalScale;
            float halfH = decoCircle.getHeight() * .5f * donorDensityScale
                    * reflectionScaleY[i] * globalScale;
            destination.set(reflectionX[i] - halfW, reflectionY[i] - halfH,
                    reflectionX[i] + halfW, reflectionY[i] + halfH);
            reflectionPaint.setAlpha(Math.round(255f * reflectionAlpha[i] * globalAlpha));
            int save = canvas.save();
            canvas.rotate(angle, reflectionX[i], reflectionY[i]);
            canvas.drawBitmap(decoCircle, null, destination, reflectionPaint);
            canvas.restoreToCount(save);
        }
        reflectionPaint.setAlpha(255);
    }

    /** Exact random ranges and index clamps from LG ColorLayeredBackgroundEffect. */
    private void prepareReflections() {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        for (int i = 0; i < REFLECTION_COUNT; i++) {
            int x = reflectionRandom.nextInt(width);
            int y = reflectionRandom.nextInt(height);
            if (i == 0) {
                if (x < width / 2) x = width * 2 / 3;
                if (y > height / 3) y = height / 3;
            } else if (i == 1) {
                if (x > width / 2) x = width / 3;
                if (y < height / 3 || y > height * 2 / 3) y = height / 2;
            } else if (i == 2) {
                if (x > width / 2) x = width / 4;
                if (y < height / 3) y = height * 2 / 3;
            } else if (i == 3) {
                if (x > width / 2) x = width / 3;
                if (y < height * 2 / 3) y = height * 4 / 5;
            } else {
                if (x < width / 2) x = width * 2 / 3;
                if (y < height * 2 / 3) y = height * 4 / 5;
            }
            reflectionX[i] = x;
            reflectionY[i] = y;
        }

        float previousY = 1f;
        for (int i = 0; i < REFLECTION_COUNT; i++) {
            float scaleX = .65f + reflectionRandom.nextFloat() * 2.35f;
            float scaleY = Math.abs(scaleX - previousY) > .2f ? scaleX - .1f : previousY;
            reflectionScaleX[i] = scaleX;
            reflectionScaleY[i] = scaleY;
            previousY = scaleY;
            reflectionAlpha[i] = .1f + reflectionRandom.nextFloat() * .1f;
        }
        int forcedLarge = reflectionRandom.nextInt(REFLECTION_COUNT);
        reflectionScaleX[forcedLarge] = reflectionScaleY[forcedLarge] = 3f;
        reflectionRotationCycleMs = 2_000 + reflectionRandom.nextInt(3_000);
        reflectionsPrepared = true;
    }

    /** CircleOpenInterpolator: Overshoot(tension=1), then Decelerate(factor=.5). */
    private static float backgroundOpenScale(float input) {
        float t = LgHulaHoopScene.clamp(input, 0f, 1f) - 1f;
        return t * t * (2f * t + 1f) + 1f;
    }

    private void drawCentered(Canvas canvas, Bitmap bitmap, float x, float y,
            float diameter, float alpha, float rotation) {
        if (bitmap == null || bitmap.isRecycled() || diameter <= 0f || alpha <= 0f) return;
        float half = diameter * .5f;
        destination.set(x - half, y - half, x + half, y + half);
        layerPaint.setAlpha(Math.round(255f * LgHulaHoopScene.clamp(alpha, 0f, 1f)));
        int save = canvas.save();
        if (rotation != 0f) canvas.rotate(rotation, x, y);
        canvas.drawBitmap(bitmap, null, destination, layerPaint);
        canvas.restoreToCount(save);
    }

    private void drawSource(Canvas canvas, BitmapShader shader, float alpha) {
        if (shader == null) return;
        bitmapPaint.setShader(shader);
        bitmapPaint.setAlpha(Math.round(255f * alpha));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), bitmapPaint);
        bitmapPaint.setShader(null);
        bitmapPaint.setAlpha(255);
    }

    private void updateGeometry() {
        if (destroyed) return;
        getLocationOnScreen(location);
        displayMetrics.setTo(getResources().getDisplayMetrics());
        if (getDisplay() != null) getDisplay().getRealMetrics(displayMetrics);
        scene.configure(getWidth(), getHeight(), displayMetrics.density);
        fluidicScene.configure(getWidth(), getHeight(), displayMetrics.density);
        mapSource(lockscreen, lockscreenShader);
        mapSource(lastScreen, lastScreenShader);
    }

    private void mapSource(Bitmap bitmap, BitmapShader shader) {
        if (bitmap == null || shader == null) return;
        sourceMatrix.setScale(Math.max(1, displayMetrics.widthPixels) / (float) bitmap.getWidth(),
                Math.max(1, displayMetrics.heightPixels) / (float) bitmap.getHeight());
        sourceMatrix.postTranslate(-location[0], -location[1]);
        shader.setLocalMatrix(sourceMatrix);
    }

    private Bitmap ownedCopy(Bitmap source) {
        if (destroyed || source == null || source.isRecycled()) return null;
        try { return source.copy(Bitmap.Config.ARGB_8888, false); }
        catch (OutOfMemoryError ignored) { return null; }
    }

    private Bitmap decode(int resourceId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resourceId, options);
        if (bitmap != null) bitmap.prepareToDraw();
        return bitmap;
    }

    private void sourcesChanged() {
        firstFrameDrawn = false;
        updateGeometry();
        invalidate();
        notifyReadiness();
    }

    private void playRandomUnlockSound() {
        if (destroyed || !OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) return;
        int id = soundIds[soundRandom.nextInt(soundIds.length)];
        playSound(id);
    }

    private void playSound(int id) {
        if (destroyed || !OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) return;
        if (id == 0) return;
        if (loadedSounds.contains(id)) soundPool.play(id, 1f, 1f, 1, 0, 1f);
        else pendingSound = id;
    }

    private boolean gestureActive() {
        return variant == OverlayPrefs.HULA_HOOP_VARIANT_V2
                ? fluidicScene.gestureActive() : scene.gestureActive();
    }

    private void notifyReadiness() {
        final ReadinessListener listener = readinessListener;
        if (listener != null && !destroyed) post(new Runnable() {
            @Override public void run() { listener.onReadinessChanged(); }
        });
    }

    private void releasePrimary() {
        recycle(lockscreen);
        lockscreen = null;
        lockscreenShader = null;
    }

    private void releaseSecondary() {
        recycle(lastScreen);
        lastScreen = null;
        lastScreenShader = null;
    }

    /** 100-segment Hermite soft body translated from LG's FluidicCircleObject. */
    private static final class FluidicMesh {
        private static final int RESOLUTION = 100;
        private static final int VERTICES = RESOLUTION + 2;
        private static final float HERMITE_TANGENT = 1.6568542f;
        private static final float KS = .01f;
        private static final float KD = .03f;
        private static final float NOMINAL_FRAME_MS = 16.666666f;

        private final float[] position = new float[VERTICES * 2];
        private final float[] previousPosition = new float[VERTICES * 2];
        private final float[] previousVelocity = new float[VERTICES * 2];
        private final float[] targetPosition = new float[VERTICES * 2];
        private float innerRadius;
        private float outerRadius;
        private float angle;
        private float rotationSpeed = .18f;
        private float pivotX;
        private float pivotY;
        private float targetPivotX;
        private float targetPivotY;
        private float pivotStep;
        private boolean rotating;
        private boolean softbody = true;
        private long previousUpdateAt;
        private long previousRotateAt;

        void reset(long now) {
            java.util.Arrays.fill(position, 0f);
            java.util.Arrays.fill(previousPosition, 0f);
            java.util.Arrays.fill(previousVelocity, 0f);
            java.util.Arrays.fill(targetPosition, 0f);
            innerRadius = outerRadius = 0f;
            pivotX = pivotY = targetPivotX = targetPivotY = pivotStep = 0f;
            softbody = true;
            previousUpdateAt = 0L;
            previousRotateAt = now;
        }

        void setRadii(float innerRadius, float outerRadius) {
            this.innerRadius = Math.max(0f, innerRadius);
            this.outerRadius = Math.max(0f, outerRadius);
        }

        void setAngle(float angle) { this.angle = angle; }

        void setRotationSpeed(float speed) {
            rotationSpeed = speed;
            rotating = true;
        }

        void setPivot(float x, float y, float step) {
            targetPivotX = x;
            targetPivotY = y;
            pivotStep = step;
        }

        void setSoftbody(boolean softbody) { this.softbody = softbody; }

        void rotate(long now) {
            float elapsed = previousRotateAt == 0L ? NOMINAL_FRAME_MS : now - previousRotateAt;
            previousRotateAt = now;
            if (elapsed < 0f || elapsed > 50f) elapsed = NOMINAL_FRAME_MS;
            if (rotating) angle += rotationSpeed * elapsed;
            if (pivotStep > 0f) {
                pivotX = stepPivot(pivotX, targetPivotX, pivotStep);
                pivotY = stepPivot(pivotY, targetPivotY, pivotStep);
            } else {
                pivotX = targetPivotX;
                pivotY = targetPivotY;
            }
        }

        void update(long now) {
            float elapsed = previousUpdateAt == 0L ? NOMINAL_FRAME_MS : now - previousUpdateAt;
            previousUpdateAt = now;
            if (elapsed < 0f || elapsed > 50f) elapsed = NOMINAL_FRAME_MS;
            float normalTime = elapsed / NOMINAL_FRAME_MS;
            updateTarget();
            if (!softbody) {
                System.arraycopy(targetPosition, 0, position, 0, position.length);
                return;
            }
            // LG's integrator assumes one update per 16.666 ms display frame. Reusing its
            // unscaled velocity term at 90/120/144 Hz injects energy twice as often and makes
            // the hoop oscillate violently. Semi-implicit fractional/sub-stepped integration
            // is identical to the donor when normalTime == 1, but preserves that response on
            // modern high-refresh panels and across an occasional dropped frame.
            int steps = Math.max(1, (int) Math.ceil(normalTime));
            float stepTime = normalTime / steps;
            for (int i = 0; i < position.length; i++) {
                float p = previousPosition[i];
                float velocity = previousVelocity[i];
                for (int step = 0; step < steps; step++) {
                    float force = -KS * (p - targetPosition[i]) - KD * velocity;
                    velocity += force * stepTime;
                    p += velocity * stepTime;
                }
                position[i] = p;
                previousVelocity[i] = velocity;
                previousPosition[i] = p;
            }
        }

        void buildPath(Path path, float centerX, float centerY) {
            path.reset();
            float radians = (float) Math.toRadians(angle);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            for (int vertex = 1; vertex <= RESOLUTION; vertex++) {
                int index = vertex * 2;
                float localX = position[index] + pivotX;
                float localY = position[index + 1] + pivotY;
                float x = centerX + cos * localX - sin * localY;
                float y = centerY + sin * localX + cos * localY;
                if (vertex == 1) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.close();
        }

        private void updateTarget() {
            float stretch = innerRadius > 0f ? outerRadius / innerRadius : 1f;
            if (Float.isNaN(stretch) || Float.isInfinite(stretch)) stretch = 1f;
            targetPosition[0] = targetPosition[1] = 0f;
            int vertex = 1;
            vertex = addQuarter(vertex, -1f, 0f, 0f, -HERMITE_TANGENT,
                    0f, -1f, HERMITE_TANGENT, 0f, innerRadius);
            vertex = addQuarter(vertex, 0f, -1f, HERMITE_TANGENT, 0f,
                    stretch, 0f, 0f, HERMITE_TANGENT, innerRadius);
            vertex = addQuarter(vertex, stretch, 0f, 0f, HERMITE_TANGENT,
                    0f, 1f, -HERMITE_TANGENT, 0f, innerRadius);
            addQuarter(vertex, 0f, 1f, -HERMITE_TANGENT, 0f,
                    -1f, 0f, 0f, -HERMITE_TANGENT, innerRadius);
            targetPosition[(RESOLUTION + 1) * 2] = targetPosition[2];
            targetPosition[(RESOLUTION + 1) * 2 + 1] = targetPosition[3];
        }

        private int addQuarter(int startVertex, float fromX, float fromY,
                float tangentFromX, float tangentFromY, float toX, float toY,
                float tangentToX, float tangentToY, float scale) {
            int quarter = RESOLUTION / 4;
            for (int i = 0; i < quarter; i++) {
                float s = i / (float) quarter;
                float s2 = s * s;
                float s3 = s2 * s;
                float h1 = 2f * s3 - 3f * s2 + 1f;
                float h2 = -2f * s3 + 3f * s2;
                float h3 = s3 - 2f * s2 + s;
                float h4 = s3 - s2;
                int index = (startVertex + i) * 2;
                targetPosition[index] = (fromX * h1 + toX * h2
                        + tangentFromX * h3 + tangentToX * h4) * scale;
                targetPosition[index + 1] = (fromY * h1 + toY * h2
                        + tangentFromY * h3 + tangentToY * h4) * scale;
            }
            return startVertex + quarter;
        }

        private static float stepPivot(float from, float to, float step) {
            float difference = from - to;
            if (Math.abs(difference) <= step) return to;
            return difference > 0f ? from - step : from + step;
        }
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }
}
