package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.SoundPool;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Canvas restoration of OptimusDev/XLocker LG Soda.
 *
 * <p>The original 2014 renderer was a transparent GLES2 particle scene: a radial pre-lock
 * image field sits below independently moving point and quad sprites.  Recreating that exact
 * scene in Canvas keeps the original archival sprites and timings while avoiding the old
 * vendor EGL assumptions on modern ARM64 devices.</p>
 */
public final class LgSodaEffectView extends View
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    /** Original Soda unlock tween: renderer radius goes from the current ring to screen diagonal. */
    static final long COMPLETE_MS = 500L;
    /** Let the fully opened pre-lock frame read before beginning the archival fade. */
    static final long COMPLETE_SOLID_HOLD_MS = 550L;
    /** Keep the captured pre-lock frame alive after handoff; original fade was about 350 ms. */
    static final long COMPLETE_FADE_MS = 0L;
    /** Original cancellation radius tween. */
    static final long CANCEL_RETRACT_MS = 300L;
    /** The donor holds cancellation fully opaque until 800 ms. */
    static final long CANCEL_FADE_DELAY_MS = 800L;
    /** The donor's 800..1200 ms alpha animator is a 400 ms linear fade. */
    static final long CANCEL_FADE_MS = 400L;

    private static final long AFFORDANCE_HOLD_MS = 150L;
    private static final int STAGE_IDLE = 0;
    private static final int STAGE_ACTIVE = 1;
    private static final int STAGE_CANCEL = 2;
    private static final int STAGE_COMPLETE = 3;

    private static final int KIND_CENTER = 0;
    private static final int KIND_CENTER_RISING = 1;
    private static final int KIND_LARGE_RISING = 2;
    private static final int KIND_SMALL_RISING = 3;

    /* The donor centres ring particles at the point where its broad 0.2r..1.2r shader band
       leaves 10.4% of the revealed layer visible (1 - smoothstep(0.8)). LLE preserves an
       opaque centre and uses a narrow 0.9..1.0 feather over the same 1.2r extent; matching
       that exact visual level maps the particle centre to 1.1875r. */
    private static final float PARTICLE_HALO_RADIUS_MULTIPLIER = 1.1875f;

    /* The original resources live in drawable-xhdpi. These are deliberately nodpi in LLE, so
       use the matching density scale at draw time rather than losing the legacy sprite geometry. */
    private static final float ARCHIVE_RESOURCE_DENSITY = 2f;

    private final Paint underlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint cutoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect sourceRect = new Rect();
    private final RectF destinationRect = new RectF();
    private final ArrayList<Particle> particles = new ArrayList<Particle>(128);
    private final Bitmap[] bigTextures = new Bitmap[8];
    private final Bitmap[] smallTextures = new Bitmap[6];
    private final Frame frame = new Frame();
    private final Random random = new Random(0x50DA2014L);
    private final SoundPool soundPool;
    private final int touchdownSound;
    private final int unlockSound;
    private final Object soundLock = new Object();
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();

    private Bitmap underlay;
    private RadialGradient cutoutGradient;
    private float gradientCenterX = Float.NaN;
    private float gradientCenterY = Float.NaN;
    private float gradientRadius = Float.NaN;
    private boolean destroyed;
    private boolean firstFrameDrawn;
    private boolean gestureActive;
    private int stage = STAGE_IDLE;
    private long sceneStartedAt;
    private long terminalStartedAt;
    private float centerX;
    private float centerY;
    private float downX;
    private float downY;
    private float dragDistance;
    private float revealRadius;
    private float terminalStartRadius;
    private ReadinessListener readinessListener;
    private final Runnable affordanceRelease = new Runnable() {
        @Override public void run() {
            if (!destroyed && stage == STAGE_ACTIVE) finishGesture(false);
        }
    };

    public LgSodaEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        // The captured pre-lock frame belongs inside Soda's opening. Outside the radial
        // mask the overlay stays transparent so SystemUI's lockscreen remains visible.
        cutoutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setColor(Color.WHITE);
        loadArchiveTextures();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(
                    SoundPool completedPool, int sampleId, int status) {
                handleSoundLoadComplete(completedPool, sampleId, status);
            }
        });
        touchdownSound = soundPool.load(context, R.raw.lg_soda_touchdown, 1);
        unlockSound = soundPool.load(context, R.raw.lg_soda_unlock, 1);
    }

    @Override public View asView() { return this; }

    @Override public String effectName() {
        return "G2 Soda (XLocker restoration tester)";
    }

    @Override public void beginGesture(float x, float y) {
        if (destroyed || !hasBackgroundSourceBitmap()) return;
        removeCallbacks(affordanceRelease);
        gestureActive = true;
        stage = STAGE_ACTIVE;
        sceneStartedAt = SystemClock.uptimeMillis();
        terminalStartedAt = 0L;
        centerX = downX = clamp(x, 0f, Math.max(0f, getWidth()));
        centerY = downY = clamp(y, 0f, Math.max(0f, getHeight()));
        dragDistance = 0f;
        revealRadius = minRingRadius();
        terminalStartRadius = revealRadius;
        createParticles();
        playSound(touchdownSound);
        postInvalidateOnAnimation();
    }

    @Override public void updateGesture(float x, float y) {
        if (destroyed) return;
        if (stage != STAGE_ACTIVE || !gestureActive) {
            beginGesture(x, y);
            return;
        }
        dragDistance = (float) Math.hypot(x - downX, y - downY);
        // Soda's original renderer maps pointer displacement onto its 44dp..113.33dp radial
        // shader value; it is intentionally reversible rather than monotonic.
        float mapped = minRingRadius()
                + ((maxRingRadius() - minRingRadius()) / Math.max(1f, maxRingRadius()))
                * dragDistance;
        // The 113.33dp value is the unlock threshold, not a visual ceiling. The original
        // renderer keeps increasing uRadius with drag distance and then reaches the display
        // diagonal during its completion tween.
        revealRadius = Math.max(minRingRadius(), Math.min(fullRevealRadius(), mapped));
        postInvalidateOnAnimation();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || stage != STAGE_ACTIVE) return;
        gestureActive = false;
        terminalStartRadius = revealRadius;
        terminalStartedAt = SystemClock.uptimeMillis();
        stage = completed ? STAGE_COMPLETE : STAGE_CANCEL;
        if (completed) playSound(unlockSound);
        postInvalidateOnAnimation();
    }

    @Override public void cancelGesture() {
        if (destroyed) return;
        if (stage == STAGE_ACTIVE) finishGesture(false);
    }

    @Override public void resetEffect() {
        removeCallbacks(affordanceRelease);
        gestureActive = false;
        stage = STAGE_IDLE;
        sceneStartedAt = 0L;
        terminalStartedAt = 0L;
        dragDistance = 0f;
        revealRadius = 0f;
        terminalStartRadius = 0f;
        particles.clear();
        invalidate();
    }

    @Override public void warmUp() {
        if (underlay != null && !underlay.isRecycled()) underlay.prepareToDraw();
        prepareTextures();
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
                ? ": underlay and Soda sprite frame ready"
                : ": underlay loaded; waiting for first frame");
    }

    @Override public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    @Override public void destroy() {
        if (destroyed) return;
        removeCallbacks(affordanceRelease);
        destroyed = true;
        particles.clear();
        releaseUnderlay();
        recycleTextures();
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

        Frame state = frameAt(SystemClock.uptimeMillis());
        if (!state.visible) return;
        drawImageField(canvas, state);
        drawParticles(canvas, state);
        if (state.running) {
            postInvalidateOnAnimation();
        } else if (stage != STAGE_IDLE) {
            // The original renderer hides the field at the end of the 500 ms unlock tween.
            stage = STAGE_IDLE;
            particles.clear();
            invalidate();
        }
    }

    Frame frameAt(long now) {
        if (stage == STAGE_ACTIVE) {
            return frame.set(true, true, revealRadius, 1f, 0f,
                    Math.max(0L, now - sceneStartedAt));
        }
        if (stage == STAGE_COMPLETE) {
            long elapsed = Math.max(0L, now - terminalStartedAt);
            float t = clamp(elapsed / (float) COMPLETE_MS, 0f, 1f);
            // C0015a defaults to LinearInterpolator in the donor.
            float eased = t;
            float radius = terminalStartRadius
                    + (fullRevealRadius() - terminalStartRadius) * eased;
            long fadeElapsed = elapsed - COMPLETE_MS - COMPLETE_SOLID_HOLD_MS;
            float tail = COMPLETE_FADE_MS <= 0L
                    ? 1f
                    : 1f - clamp(fadeElapsed / (float) COMPLETE_FADE_MS, 0f, 1f);
            boolean visible = elapsed
                    < COMPLETE_MS + COMPLETE_SOLID_HOLD_MS + COMPLETE_FADE_MS;
            return frame.set(visible, visible, radius, tail, eased,
                    Math.max(0L, now - sceneStartedAt));
        }
        if (stage == STAGE_CANCEL) {
            long elapsed = Math.max(0L, now - terminalStartedAt);
            float retract = clamp(elapsed / (float) CANCEL_RETRACT_MS, 0f, 1f);
            float fadeElapsed = elapsed - CANCEL_FADE_DELAY_MS;
            float fade = 1f - clamp(fadeElapsed / (float) CANCEL_FADE_MS, 0f, 1f);
            return frame.set(fade > 0f, fade > 0f,
                    terminalStartRadius * (1f - retract), fade, 0f,
                    Math.max(0L, now - sceneStartedAt));
        }
        return frame.set(false, false, 0f, 0f, 0f, 0L);
    }

    private void drawImageField(Canvas canvas, Frame state) {
        sourceRect.set(0, 0, underlay.getWidth(), underlay.getHeight());
        destinationRect.set(0f, 0f, getWidth(), getHeight());
        underlayPaint.setAlpha(Math.round(255f * state.alpha));
        int layer = canvas.saveLayer(0f, 0f, getWidth(), getHeight(), null);
        canvas.drawBitmap(underlay, sourceRect, destinationRect, underlayPaint);

        // Retain the captured pre-lock frame in the centre and feather it to transparency at
        // the edge; the transparent exterior exposes SystemUI's lockscreen below the overlay.
        float maskRadius = Math.max(1f, state.radius);
        applyCutoutGradient(maskRadius);
        // Porter-Duff only processes pixels covered by the source primitive. Drawing a circle
        // would leave the already-rendered bitmap untouched outside that circle. Cover the
        // complete layer instead; the CLAMPed zero-alpha edge clears every exterior pixel.
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), cutoutPaint);
        canvas.restoreToCount(layer);
        underlayPaint.setAlpha(255);

        if (state.radius > 2f) {
            glowPaint.setAlpha(Math.round(48f * state.alpha));
            glowPaint.setStrokeWidth(Math.max(1f, state.radius * 0.022f));
            canvas.drawCircle(centerX, centerY, state.radius * 0.92f, glowPaint);
            glowPaint.setAlpha(255);
        }
    }

    private void applyCutoutGradient(float radius) {
        float outer = Math.max(1f, radius * 1.2f);
        if (cutoutGradient == null
                || Math.abs(gradientCenterX - centerX) > 0.25f
                || Math.abs(gradientCenterY - centerY) > 0.25f
                || Math.abs(gradientRadius - outer) > 0.5f) {
            cutoutGradient = new RadialGradient(centerX, centerY, outer,
                    new int[] {0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                    // Preserve the captured frame at full opacity across the opening. Only
                    // the narrow outer rim is feathered into the transparent lockscreen.
                    new float[] {0f, 0.90f, 1f}, Shader.TileMode.CLAMP);
            gradientCenterX = centerX;
            gradientCenterY = centerY;
            gradientRadius = outer;
        }
        cutoutPaint.setShader(cutoutGradient);
    }

    private void drawParticles(Canvas canvas, Frame state) {
        if (particles.isEmpty()) return;
        final float assetScale = archiveDensityScale();
        final float elapsedSeconds = state.elapsedMs * 0.001f;
        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            updateParticle(particle, elapsedSeconds, state, assetScale);
            if (particle.drawAlpha <= 0.002f || particle.texture == null
                    || particle.texture.isRecycled()) continue;
            float half = particle.drawSize * 0.5f;
            particle.destination.set(particle.drawX - half, particle.drawY - half,
                    particle.drawX + half, particle.drawY + half);
            particlePaint.setAlpha(Math.round(255f * particle.drawAlpha));
            if (Math.abs(particle.drawRotation) > 0.01f) {
                int save = canvas.save();
                canvas.rotate(particle.drawRotation, particle.drawX, particle.drawY);
                canvas.drawBitmap(particle.texture, null, particle.destination, particlePaint);
                canvas.restoreToCount(save);
            } else {
                canvas.drawBitmap(particle.texture, null, particle.destination, particlePaint);
            }
        }
        particlePaint.setAlpha(255);
    }

    private void updateParticle(
            Particle particle, float elapsedSeconds, Frame state, float assetScale) {
        float cycleSeconds = Math.max(0.12f, particle.cycleMs * 0.001f);
        float age = elapsedSeconds - particle.delayMs * 0.001f;
        if (age < 0f) {
            particle.drawAlpha = 0f;
            return;
        }
        float local = positiveMod(age, cycleSeconds) / cycleSeconds;
        float localAngle;
        float x;
        float y;
        float alpha = particle.alpha;
        float size = particle.texture.getWidth() * assetScale * particle.size;
        switch (particle.kind) {
            case KIND_CENTER: {
                // Original C0062c: each point is repeatedly seeded on the current reveal edge.
                // It does not orbit continuously. A new pseudo-random angle every 600-800 ms
                // gives the dense, flickering ring visible in the N4 recording.
                int cycle = Math.max(0, (int) Math.floor(age / cycleSeconds));
                localAngle = particle.angle + cycle * 2.3999631f;
                float anchoredRadius = stage == STAGE_ACTIVE
                        ? state.radius : terminalStartRadius;
                float centerRadius = Math.max(dp(30f),
                        anchoredRadius * PARTICLE_HALO_RADIUS_MULTIPLIER)
                        // Donor stores positions in a quarter-scale orthographic scene:
                        // density*1.67 world units maps back to 6.68 dp on screen.
                        + (particle.radius - 0.5f) * dp(6.68f);
                x = centerX + (float) Math.cos(localAngle) * centerRadius;
                y = centerY + (float) Math.sin(localAngle) * centerRadius;
                alpha *= pulseAlpha(local, stage == STAGE_ACTIVE);
                if (stage == STAGE_COMPLETE) {
                    // Donor unlock velocity: density * (0.25..0.31) world units/ms,
                    // projected back to pixels by the original quarter-scale scene.
                    float burst = dp(500f + particle.phase / 6.2831855f * 120f)
                            * state.escape;
                    x += (float) Math.cos(localAngle) * burst;
                    y += (float) Math.sin(localAngle) * burst;
                } else if (stage == STAGE_CANCEL) {
                    float cancelAge = terminalAgeSeconds(state);
                    float drift = dp(120f + particle.angularVelocity * 280f) * cancelAge;
                    x += (float) Math.sin(particle.wander) * drift;
                    y -= (float) Math.cos(particle.wander) * drift;
                }
                break;
            }
            case KIND_CENTER_RISING: {
                // Original C0063d: particles wait on the reveal edge for 0-1000 ms, then
                // climb almost vertically. The +/-27 degree bias makes loose curved streams;
                // it is not a clockwise/counter-clockwise orbit around the circle.
                float holdSeconds = particle.holdMs * 0.001f;
                float cycleAge = positiveMod(age, cycleSeconds);
                int cycle = Math.max(0, (int) Math.floor(age / cycleSeconds));
                localAngle = particle.angle + cycle * 2.3999631f;
                float anchoredRadius = stage == STAGE_ACTIVE
                        ? state.radius : terminalStartRadius;
                float risingRadius = Math.max(dp(28f),
                        anchoredRadius * PARTICLE_HALO_RADIUS_MULTIPLIER)
                        + (particle.radius - 0.5f) * dp(6.68f);
                float baseX = centerX + (float) Math.cos(localAngle) * risingRadius;
                float baseY = centerY + (float) Math.sin(localAngle) * risingRadius;
                if (cycleAge <= holdSeconds) {
                    x = baseX;
                    y = baseY;
                    alpha *= Math.min(1f, cycleAge / Math.max(0.001f, cycleSeconds * 0.1f));
                } else {
                    float flightAge = cycleAge - holdSeconds;
                    float speed = dp(80f + particle.angularVelocity * 240f);
                    x = baseX + (float) Math.sin(particle.wander) * speed * flightAge;
                    y = baseY - (float) Math.cos(particle.wander) * speed * flightAge;
                }
                if (stage == STAGE_COMPLETE) {
                    float dx = x - centerX;
                    float dy = y - centerY;
                    float magnitude = Math.max(1f, (float) Math.hypot(dx, dy));
                    float burst = dp(500f + particle.phase / 6.2831855f * 120f)
                            * state.escape;
                    x += dx / magnitude * burst;
                    y += dy / magnitude * burst;
                }
                break;
            }
            case KIND_LARGE_RISING: {
                // Original C0066g/C0068i: quads are born across the lower semicircle and
                // travel upward with only a small angular spread.
                size = particle.texture.getWidth() * assetScale * particle.size;
                float travel = getHeight() + getWidth() * 0.72f + size * 2f;
                float along = travel * local;
                x = particle.lane * getWidth()
                        + (float) Math.sin(particle.angle) * along;
                y = getHeight() + size - (float) Math.cos(particle.angle) * along;
                break;
            }
            case KIND_SMALL_RISING:
            default: {
                // Original C0070k: 10/15/20 dp point sprites share the same upward field.
                size = dp(particle.pointSizeDp);
                float travel = getHeight() + getWidth() * 0.62f + size * 2f;
                float along = travel * local;
                x = particle.lane * getWidth()
                        + (float) Math.sin(particle.angle) * along;
                y = getHeight() + size - (float) Math.cos(particle.angle) * along;
                break;
            }
        }

        if (stage == STAGE_COMPLETE && state.escape > 0f
                && particle.kind >= KIND_LARGE_RISING) {
            float dx = x - centerX;
            float dy = y - centerY;
            float magnitude = Math.max(1f, (float) Math.hypot(dx, dy));
            float push = dp(500f + particle.phase / 6.2831855f * 120f)
                    * state.escape;
            x += (dx / magnitude) * push;
            y += (dy / magnitude) * push;
        }
        particle.drawX = x;
        particle.drawY = y;
        particle.drawSize = Math.max(1f, size);
        particle.drawAlpha = clamp(alpha * state.alpha, 0f, 1f);
        // The archival point/quad shaders never rotate the source texture.
        particle.drawRotation = 0f;
    }

    private void createParticles() {
        particles.clear();
        random.setSeed(0x50DA2014L ^ sceneStartedAt ^ ((long) Float.floatToIntBits(centerX) << 16)
                ^ Float.floatToIntBits(centerY));

        // Original b.java: 8 centre and 7 rising points per each of the six archival sprites.
        for (int textureIndex = 0; textureIndex < smallTextures.length; textureIndex++) {
            Bitmap texture = smallTextures[textureIndex];
            if (texture == null || texture.isRecycled()) continue;
            for (int i = 0; i < 8; i++) {
                Particle particle = addSmallParticle(texture, KIND_CENTER,
                        600L + random.nextInt(201), i < 2 ? 0L : random.nextInt(700),
                        0.32f + random.nextFloat() * 0.63f);
                particle.wander = (random.nextFloat() - 0.5f) * 0.942478f;
            }
            for (int i = 0; i < 7; i++) {
                Particle particle = addSmallParticle(texture, KIND_CENTER_RISING,
                        2600L + random.nextInt(1000), 0L,
                        0.32f + random.nextFloat() * 0.63f);
                particle.holdMs = random.nextInt(1001);
                particle.wander = (random.nextFloat() - 0.5f) * 0.942478f;
                particle.angularVelocity = random.nextFloat();
                float speed = dp(80f + particle.angularVelocity * 240f);
                particle.cycleMs = particle.holdMs + Math.max(1L,
                        Math.round((screenDiagonal() + maxRingRadius())
                                / Math.max(1f, speed) * 1000f));
            }
        }

        // The donor draws h.java before f.java: soft white disks below coloured rings.
        for (int textureIndex : new int[] {3, 7}) {
            for (int i = 0; i < 2; i++) {
                addLargeParticle(bigTextures[textureIndex], 2400L + random.nextInt(1500),
                        1.0f + random.nextFloat() * 0.20f, i == 0 ? 1.0f : 0.5f);
            }
        }
        // Original f.java: one large coloured ring per sprite.
        for (int textureIndex : new int[] {0, 1, 2, 4, 5, 6}) {
            addLargeParticle(bigTextures[textureIndex], 3000L + random.nextInt(1800),
                    1.0f + random.nextFloat() * 0.20f, 1.0f);
        }
        // Original j.java/k.java: two columns with ten small rising sprites each.
        for (int textureIndex = 0; textureIndex < 2; textureIndex++) {
            Bitmap texture = smallTextures[textureIndex];
            if (texture == null || texture.isRecycled()) continue;
            for (int i = 0; i < 10; i++) {
                Particle particle = newParticle(texture, KIND_SMALL_RISING, 1L, 0L);
                particle.angle = (random.nextFloat() - 0.5f) * 0.942478f;
                particle.pointSizeDp = 10f + random.nextInt(3) * 5f;
                particle.alpha = discreteParticleAlpha();
                particle.radius = random.nextFloat();
                particle.lane = 0.04f + random.nextFloat() * 0.92f;
                particle.angularVelocity = random.nextFloat();
                float speed = dp(80f + particle.angularVelocity * 240f);
                float travel = getHeight() + getWidth() * 0.62f
                        + dp(particle.pointSizeDp) * 2f;
                particle.cycleMs = Math.max(1L,
                        Math.round(travel / Math.max(1f, speed) * 1000f));
                particle.size = 1f;
                particles.add(particle);
            }
        }
    }

    private Particle addSmallParticle(
            Bitmap texture, int kind, long cycleMs, long delayMs, float size) {
        Particle particle = newParticle(texture, kind, cycleMs, delayMs);
        particle.size = size;
        particle.alpha = discreteParticleAlpha();
        particle.radius = random.nextFloat();
        particle.angularVelocity = random.nextFloat();
        particles.add(particle);
        return particle;
    }

    private void addLargeParticle(Bitmap texture, long cycleMs, float size, float alpha) {
        if (texture == null || texture.isRecycled()) return;
        Particle particle = newParticle(texture, KIND_LARGE_RISING, cycleMs, 0L);
        particle.angle = (random.nextFloat() - 0.5f) * 0.942478f;
        particle.size = size;
        particle.alpha = alpha;
        particle.lane = 0.04f + random.nextFloat() * 0.92f;
        particle.angularVelocity = random.nextFloat();
        float speed = dp(80f + particle.angularVelocity * 220f);
        float travel = getHeight() + getWidth() * 0.72f
                + texture.getWidth() * archiveDensityScale() * size * 2f;
        particle.cycleMs = Math.max(1L,
                Math.round(travel / Math.max(1f, speed) * 1000f));
        particles.add(particle);
    }

    private float discreteParticleAlpha() {
        return 0.2f * (random.nextInt(4) + 1);
    }

    private Particle newParticle(Bitmap texture, int kind, long cycleMs, long delayMs) {
        Particle particle = new Particle();
        particle.texture = texture;
        particle.kind = kind;
        particle.cycleMs = Math.max(1L, cycleMs);
        particle.delayMs = Math.max(0L, delayMs);
        particle.angle = random.nextFloat() * 6.2831855f;
        particle.phase = random.nextFloat() * 6.2831855f;
        return particle;
    }

    private void loadArchiveTextures() {
        int[] bigResources = new int[] {
                R.drawable.lg_soda_big_01,
                R.drawable.lg_soda_big_02,
                R.drawable.lg_soda_big_03,
                R.drawable.lg_soda_big_06,
                R.drawable.lg_soda_big_11,
                R.drawable.lg_soda_big_12,
                R.drawable.lg_soda_big_14,
                R.drawable.lg_soda_big_16
        };
        int[] smallResources = new int[] {
                R.drawable.lg_soda_small_01,
                R.drawable.lg_soda_small_02,
                R.drawable.lg_soda_small_03,
                R.drawable.lg_soda_small_04,
                R.drawable.lg_soda_small_05,
                R.drawable.lg_soda_small_06
        };
        for (int i = 0; i < bigResources.length; i++) bigTextures[i] = decodeTexture(bigResources[i]);
        for (int i = 0; i < smallResources.length; i++) {
            smallTextures[i] = decodeTexture(smallResources[i]);
        }
    }

    private Bitmap decodeTexture(int resourceId) {
        Bitmap texture = BitmapFactory.decodeResource(getResources(), resourceId);
        if (texture != null) texture.prepareToDraw();
        return texture;
    }

    private void prepareTextures() {
        for (Bitmap texture : bigTextures) {
            if (texture != null && !texture.isRecycled()) texture.prepareToDraw();
        }
        for (Bitmap texture : smallTextures) {
            if (texture != null && !texture.isRecycled()) texture.prepareToDraw();
        }
    }

    private void recycleTextures() {
        for (int i = 0; i < bigTextures.length; i++) {
            recycle(bigTextures[i]);
            bigTextures[i] = null;
        }
        for (int i = 0; i < smallTextures.length; i++) {
            recycle(smallTextures[i]);
            smallTextures[i] = null;
        }
    }

    private void releaseUnderlay() {
        recycle(underlay);
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

    private float minRingRadius() { return dp(44f); }

    private float maxRingRadius() { return dp(113.32999f); }

    private float fullRevealRadius() {
        return screenDiagonal();
    }

    private float screenDiagonal() {
        return (float) Math.hypot(getWidth(), getHeight());
    }

    private float archiveDensityScale() {
        return Math.max(0.75f, getResources().getDisplayMetrics().density
                / ARCHIVE_RESOURCE_DENSITY);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float pulseAlpha(float value, boolean fadeTail) {
        if (value < 0.1f) return value / 0.1f;
        if (!fadeTail || value < 0.7f) return 1f;
        return (1f - value) / 0.3f;
    }

    private float terminalAgeSeconds(Frame state) {
        if (terminalStartedAt <= 0L || sceneStartedAt <= 0L) return 0f;
        long terminalOffsetMs = Math.max(0L, terminalStartedAt - sceneStartedAt);
        return Math.max(0L, state.elapsedMs - terminalOffsetMs) * 0.001f;
    }

    private static float positiveMod(float value, float modulus) {
        float result = value % modulus;
        return result < 0f ? result + modulus : result;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    static final class Frame {
        boolean visible;
        boolean running;
        float radius;
        float alpha;
        float escape;
        long elapsedMs;

        Frame set(boolean visible, boolean running, float radius, float alpha, float escape,
                long elapsedMs) {
            this.visible = visible;
            this.running = running;
            this.radius = Math.max(0f, radius);
            this.alpha = clamp(alpha, 0f, 1f);
            this.escape = clamp(escape, 0f, 1f);
            this.elapsedMs = Math.max(0L, elapsedMs);
            return this;
        }
    }

    private static final class Particle {
        Bitmap texture;
        int kind;
        long cycleMs;
        long delayMs;
        long holdMs;
        float size;
        float alpha;
        float radius;
        float angle;
        float phase;
        float angularVelocity;
        float lane;
        float wander;
        float pointSizeDp;
        float drawX;
        float drawY;
        float drawSize;
        float drawAlpha;
        float drawRotation;
        final RectF destination = new RectF();
    }
}
