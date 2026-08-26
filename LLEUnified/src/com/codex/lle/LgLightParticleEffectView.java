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

import java.util.HashSet;
import java.util.Set;

/**
 * LG G2 Light Particle restoration from the authorized OptimusDev/XLocker archive.
 *
 * <p>The archived renderer uses GLES2 for a radial background mask and 74 textured quads.
 * Modern HWUI can render the same workload without owning a second EGL surface, so this port
 * keeps the donor's timing, particle families, density behavior and shader edge in one stable
 * Canvas view. The revealed image is L.L.E.'s captured pre-lock underlay.</p>
 */
public final class LgLightParticleEffectView extends View
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    static final long COMPLETE_MS = LgLightParticleScene.COMPLETE_MS;
    static final long COMPLETE_HOLD_MS = LgLightParticleScene.COMPLETE_HOLD_MS;

    private static final long AFFORDANCE_HOLD_MS = 150L;
    private static final float ARCHIVE_DENSITY = 2f; // drawable-xhdpi
    private static final int FALLBACK_EDGE_BANDS = 10;
    private static final String REVEAL_SHADER =
            "uniform shader uUnderlay;"
            + "uniform float2 uCenter;"
            + "uniform float uRadius;"
            + "uniform float uBandWidth;"
            + "half4 main(float2 p) {"
            + " half4 source=uUnderlay.eval(p);"
            + " float dist=length(p-uCenter);"
            + " float edge=uRadius*0.7;"
            + " float bandwidth=max(uBandWidth,0.001);"
            + " float reveal=1.0-smoothstep(edge-bandwidth,edge+bandwidth,dist);"
            + " float whiten=pow(1.0-reveal,3.0);"
            + " float3 rgb=float3(source.rgb)*(1.0-whiten)+float3(1.0)*whiten;"
            + " float alpha=clamp(reveal,0.0,1.0);"
            + " return half4(half3(rgb*alpha),half(alpha));"
            + "}";

    private final LgLightParticleScene scene =
            new LgLightParticleScene(BuildFlavor.TESTER);
    private final LgLightParticleScene.Frame frame = new LgLightParticleScene.Frame();
    private final Bitmap[] textures = new Bitmap[LgLightParticleScene.TEXTURE_COUNT];
    private final Paint underlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF screenRect = new RectF();
    private final RectF particleRect = new RectF();
    private final Path fallbackClip = new Path();
    private final Matrix underlayMatrix = new Matrix();
    private final SoundPool soundPool;
    private final int touchdownSound;
    private final int unlockSound;
    private final Object soundLock = new Object();
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();
    private final float assetDensityScale;

    private Bitmap underlay;
    private BitmapShader underlayShader;
    private RuntimeShader revealShader;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean firstFrameDrawn;
    private ReadinessListener readinessListener;
    private float pendingAffordanceX;
    private float pendingAffordanceY;

    private final Runnable affordanceStart = new Runnable() {
        @Override public void run() {
            if (destroyed || !hasBackgroundSourceBitmap()) return;
            beginGesture(pendingAffordanceX, pendingAffordanceY);
            postDelayed(affordanceRelease, AFFORDANCE_HOLD_MS);
        }
    };

    private final Runnable affordanceRelease = new Runnable() {
        @Override public void run() {
            if (!destroyed && scene.state() == LgLightParticleScene.ACTIVE) {
                finishGesture(false);
            }
        }
    };

    public LgLightParticleEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        float density = getResources().getDisplayMetrics().density;
        scene.setDensity(density);
        assetDensityScale = density / ARCHIVE_DENSITY;
        loadTextures();
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                revealShader = new RuntimeShader(REVEAL_SHADER);
            } catch (RuntimeException ignored) {
                revealShader = null;
            }
        }

        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                handleSoundLoadComplete(pool, sampleId, status);
            }
        });
        touchdownSound = soundPool.load(context, R.raw.lg_lightparticle_touchdown, 1);
        unlockSound = soundPool.load(context, R.raw.lg_lightparticle_unlock, 1);
    }

    @Override public View asView() { return this; }

    @Override public String effectName() {
        return "G2 Light Particle (XLocker restoration tester)";
    }

    @Override public void beginGesture(float screenX, float screenY) {
        if (destroyed || !hasBackgroundSourceBitmap()) return;
        removeCallbacks(affordanceStart);
        removeCallbacks(affordanceRelease);
        gestureActive = true;
        scene.begin(screenX, screenY, SystemClock.uptimeMillis());
        playSound(touchdownSound);
        postInvalidateOnAnimation();
    }

    @Override public void updateGesture(float screenX, float screenY) {
        if (destroyed) return;
        if (!gestureActive || scene.state() != LgLightParticleScene.ACTIVE) {
            beginGesture(screenX, screenY);
            return;
        }
        scene.move(screenX, screenY);
        postInvalidateOnAnimation();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || scene.state() != LgLightParticleScene.ACTIVE) return;
        gestureActive = false;
        scene.finish(completed, SystemClock.uptimeMillis());
        if (completed) playSound(unlockSound);
        postInvalidateOnAnimation();
    }

    @Override public void cancelGesture() {
        if (scene.state() == LgLightParticleScene.ACTIVE) finishGesture(false);
    }

    @Override public void resetEffect() {
        removeCallbacks(affordanceStart);
        removeCallbacks(affordanceRelease);
        gestureActive = false;
        scene.reset();
        invalidate();
    }

    @Override public void warmUp() {
        if (destroyed) return;
        if (underlay != null && !underlay.isRecycled()) underlay.prepareToDraw();
        for (Bitmap texture : textures) {
            if (texture != null && !texture.isRecycled()) texture.prepareToDraw();
        }
        invalidate();
    }

    @Override public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (destroyed || !hasBackgroundSourceBitmap()) return;
        Rect safe = screenRect != null && screenRect.width() > 0 && screenRect.height() > 0
                ? screenRect : new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        pendingAffordanceX = safe.exactCenterX();
        pendingAffordanceY = safe.exactCenterY();
        removeCallbacks(affordanceStart);
        removeCallbacks(affordanceRelease);
        postDelayed(affordanceStart, Math.max(0L, startDelayMs));
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
                ? ": archive textures and underlay ready"
                : ": resources loaded; waiting for first HWUI frame");
    }

    @Override public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    @Override public void destroy() {
        if (destroyed) return;
        destroyed = true;
        removeCallbacks(affordanceStart);
        removeCallbacks(affordanceRelease);
        scene.reset();
        releaseUnderlay();
        for (int index = 0; index < textures.length; index++) {
            Bitmap texture = textures[index];
            if (texture != null && !texture.isRecycled()) texture.recycle();
            textures[index] = null;
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
        firstFrameDrawn = false;
        notifyReadiness();
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        resetEffect();
        firstFrameDrawn = false;
        notifyReadiness();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        scene.setSurfaceSize(width, height);
        rebuildUnderlayShader();
        firstFrameDrawn = false;
        notifyReadiness();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Translucent accessibility surfaces may retain their last buffer when the next display
        // list is empty. Clear every frame so hints and cancelled gestures cannot freeze.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        if (destroyed || !hasBackgroundSourceBitmap() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (!firstFrameDrawn) {
            firstFrameDrawn = true;
            notifyReadiness();
        }

        LgLightParticleScene.Frame current = scene.sample(SystemClock.uptimeMillis(), frame);
        if (current.visible && current.radius > 0.5f) {
            drawArchivedReveal(canvas, current);
            drawParticles(canvas, current);
        }
        if (current.running) {
            postInvalidateOnAnimation();
        }
    }

    private void drawArchivedReveal(Canvas canvas, LgLightParticleScene.Frame current) {
        float bandwidth = LgLightParticleScene.edgeBandwidth(
                current.radius, scene.minRadius());
        if (Build.VERSION.SDK_INT >= 33 && revealShader != null) {
            if (underlayShader == null) rebuildUnderlayShader();
            if (underlayShader != null) {
                revealShader.setInputShader("uUnderlay", underlayShader);
                revealShader.setFloatUniform("uCenter", scene.centreX(), scene.centreY());
                revealShader.setFloatUniform("uRadius", current.radius);
                revealShader.setFloatUniform("uBandWidth", Math.max(0.001f, bandwidth));
                underlayPaint.setShader(revealShader);
                screenRect.set(0f, 0f, getWidth(), getHeight());
                canvas.drawRect(screenRect, underlayPaint);
                underlayPaint.setShader(null);
                return;
            }
        }
        drawFallbackReveal(canvas, current, bandwidth);
    }

    private void drawFallbackReveal(Canvas canvas, LgLightParticleScene.Frame current,
            float bandwidth) {
        float edgeRadius = current.radius * 0.7f;
        fallbackClip.reset();
        fallbackClip.addCircle(scene.centreX(), scene.centreY(),
                Math.max(0f, edgeRadius), Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(fallbackClip);
        canvas.drawBitmap(underlay, underlayMatrix, underlayPaint);
        canvas.restoreToCount(save);

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setColor(Color.WHITE);
        float inner = Math.max(0f, edgeRadius - bandwidth);
        float step = Math.max(1f, (bandwidth * 2f) / FALLBACK_EDGE_BANDS);
        edgePaint.setStrokeWidth(step + 1f);
        for (int band = 0; band < FALLBACK_EDGE_BANDS; band++) {
            float t = (band + 0.5f) / FALLBACK_EDGE_BANDS;
            float alpha = (1f - t) * (1f - t) * 0.8f;
            edgePaint.setAlpha(Math.round(255f * alpha));
            canvas.drawCircle(scene.centreX(), scene.centreY(), inner + step * band, edgePaint);
        }
        edgePaint.setAlpha(255);
        edgePaint.setStyle(Paint.Style.FILL);
    }

    private void drawParticles(Canvas canvas, LgLightParticleScene.Frame current) {
        for (int index = 0; index < current.spriteCount; index++) {
            LgLightParticleScene.ParticleSprite sprite = current.sprites[index];
            if (sprite.texture < 0 || sprite.texture >= textures.length) continue;
            Bitmap texture = textures[sprite.texture];
            if (texture == null || texture.isRecycled()) continue;
            float size = texture.getWidth() * assetDensityScale * sprite.sizeScale
                    + sprite.sizeExtraPx;
            if (size <= 0.5f || sprite.alpha <= 0f) continue;
            float half = size * 0.5f;
            particleRect.set(sprite.x - half, sprite.y - half,
                    sprite.x + half, sprite.y + half);
            particlePaint.setAlpha(Math.round(255f * sprite.alpha));
            canvas.drawBitmap(texture, null, particleRect, particlePaint);
        }
        particlePaint.setAlpha(255);
    }

    private void loadTextures() {
        textures[LgLightParticleScene.TEXTURE_BG] =
                decodeTexture(R.drawable.lg_lightparticle_bg);
        textures[LgLightParticleScene.TEXTURE_A_1] =
                decodeTexture(R.drawable.lg_lightparticle_a_1);
        textures[LgLightParticleScene.TEXTURE_A_2] =
                decodeTexture(R.drawable.lg_lightparticle_a_2);
        textures[LgLightParticleScene.TEXTURE_A_3] =
                decodeTexture(R.drawable.lg_lightparticle_a_3);
        textures[LgLightParticleScene.TEXTURE_A_4] =
                decodeTexture(R.drawable.lg_lightparticle_a_4);
        textures[LgLightParticleScene.TEXTURE_B_1] =
                decodeTexture(R.drawable.lg_lightparticle_b_1);
        textures[LgLightParticleScene.TEXTURE_B_2] =
                decodeTexture(R.drawable.lg_lightparticle_b_2);
        textures[LgLightParticleScene.TEXTURE_D_1] =
                decodeTexture(R.drawable.lg_lightparticle_d_1);
        textures[LgLightParticleScene.TEXTURE_D_2] =
                decodeTexture(R.drawable.lg_lightparticle_d_2);
        textures[LgLightParticleScene.TEXTURE_D_3] =
                decodeTexture(R.drawable.lg_lightparticle_d_3);
    }

    private Bitmap decodeTexture(int resourceId) {
        Bitmap texture = BitmapFactory.decodeResource(getResources(), resourceId);
        if (texture != null) texture.prepareToDraw();
        return texture;
    }

    private void rebuildUnderlayShader() {
        underlayShader = null;
        if (underlay == null || underlay.isRecycled() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        underlayMatrix.reset();
        // Preserve the complete pre-lock frame. The capture is normally already
        // surface-sized; if dimensions differ, map the full rectangle without a
        // centre crop and keep that mapping fixed for the entire gesture.
        underlayMatrix.setScale(
                getWidth() / (float) underlay.getWidth(),
                getHeight() / (float) underlay.getHeight());
        if (Build.VERSION.SDK_INT >= 33) {
            underlayShader = new BitmapShader(underlay,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            underlayShader.setLocalMatrix(underlayMatrix);
        }
    }

    private void releaseUnderlay() {
        underlayShader = null;
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

    private void handleSoundLoadComplete(SoundPool completedPool, int sampleId, int status) {
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
}
