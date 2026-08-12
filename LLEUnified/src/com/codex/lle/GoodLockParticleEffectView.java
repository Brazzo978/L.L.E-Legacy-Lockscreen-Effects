package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.View;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Transparent, app-owned port of the three Canvas particle effects shipped by
 * Good Lock/OpenSesame SystemUI 24.0.15.
 *
 * <p>The supplied bitmap is a colour source only.  In particular it is never
 * installed as this View's background or drawn into the overlay, so lockscreen
 * pixels outside a particle remain untouched.</p>
 */
public final class GoodLockParticleEffectView extends View
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    /** The original filled, upward travelling circles. */
    public enum Variant {
        POPPING,
        RECTANGLE,
        BOUNCING
    }

    private static final long STOCK_FRAME_UNIT_MS = 10L;
    private static final long STOCK_PRESENTATION_INTERVAL_NANOS = 16_666_667L;
    /* Do not let a 90/120 Hz display make the 10 ms stock unit run faster. */
    private static final long MAX_PRESENTATION_INTERVAL_MS = 17L;
    private static final float TOUCH_SOUND_VOLUME = 0.3f;

    private final Variant variant;
    private final Simulation simulation;
    private final Map<Particle, Paint> paints = new IdentityHashMap<Particle, Paint>();
    private final HighFrameClock highFrameClock = new HighFrameClock();
    private final SoundPool soundPool;
    private final int tapSound;
    private final int dragSound;
    private final int unlockSound;
    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            frameScheduled = false;
            if (!destroyed && !paused && !simulation.isEmpty()) {
                invalidate();
            }
        }
    };
    private final Choreographer.FrameCallback highFrameCallback =
            new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    frameScheduled = false;
                    if (destroyed || paused || !highFrameRateEnabled || simulation.isEmpty()) {
                        return;
                    }
                    pendingHighFrameStep = highFrameClock.consume(frameTimeNanos)
                            * highFrameRateSpeedMultiplier;
                    invalidate();
                }
            };

    private Bitmap backgroundBitmap;
    private PixelSampler wallpaperSampler;
    private boolean externalBackground;
    private boolean destroyed;
    private boolean paused;
    private boolean canvasReady;
    private boolean frameScheduled;
    private boolean highFrameRateEnabled;
    private float highFrameRateSpeedMultiplier = 1.0f;
    private long previousFrameTimeMs;
    private long lastPresentationAtMs;
    private float pendingHighFrameStep;
    private float lastSoundX;
    private float lastSoundY;
    private float dragSoundDistance;
    private int readinessState = UnlockEffectReadiness.STATE_CONSTRUCTED;
    private String readinessDetail = "Good Lock particle: constructed";
    private UnlockEffectReadiness.ReadinessListener readinessListener;

    /** Production constructor: randomness follows Samsung's time-seeded {@link Random}. */
    public GoodLockParticleEffectView(Context context, Variant variant) {
        this(context, variant, false, 1.0f);
    }

    /** Production constructor with optional display-refresh presentation. */
    public GoodLockParticleEffectView(Context context, Variant variant,
            boolean highFrameRateEnabled) {
        this(context, variant, highFrameRateEnabled, 1.0f);
    }

    /** Production constructor with optional display-refresh presentation and physics speed. */
    public GoodLockParticleEffectView(Context context, Variant variant,
            boolean highFrameRateEnabled, float speedMultiplier) {
        this(context, variant, new JavaRandomSource(new Random(System.currentTimeMillis())));
        this.highFrameRateEnabled = highFrameRateEnabled;
        highFrameRateSpeedMultiplier = highFrameRateEnabled
                ? sanitizeSpeedMultiplier(speedMultiplier) : 1.0f;
    }

    /**
     * Changes presentation live.  The default is {@code false}, preserving Samsung's capped
     * legacy scheduler; enabling it requests every display vsync while retaining 60 Hz physics.
     */
    public void setHighFrameRateEnabled(boolean enabled) {
        if (highFrameRateEnabled == enabled) {
            return;
        }
        highFrameRateEnabled = enabled;
        removeCallbacks(frameRunnable);
        Choreographer.getInstance().removeFrameCallback(highFrameCallback);
        frameScheduled = false;
        previousFrameTimeMs = 0L;
        pendingHighFrameStep = 0.0f;
        highFrameClock.reset();
        if (!destroyed && !paused && !simulation.isEmpty()) {
            scheduleFrame();
        }
    }

    /**
     * Package seam for deterministic host tests.  Product wiring must use the
     * two-argument constructor; this neither changes nor persists production randomness.
     */
    GoodLockParticleEffectView(Context context, Variant variant, Random random) {
        this(context, variant, new JavaRandomSource(random));
    }

    GoodLockParticleEffectView(Context context, Variant variant, RandomSource random) {
        super(context);
        if (variant == null) {
            throw new IllegalArgumentException("variant == null");
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        simulation = new Simulation(variant, Math.max(1, metrics.widthPixels),
                Math.max(1, metrics.heightPixels), random);
        this.variant = variant;
        soundPool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        tapSound = soundPool.load(context, R.raw.particle_tap, 1);
        dragSound = soundPool.load(context, R.raw.particle_drag, 1);
        unlockSound = soundPool.load(context, R.raw.particle_unlock, 1);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        switch (variant) {
            case POPPING:
                return "Popping color (Good Lock)";
            case RECTANGLE:
                return "Rectangle traveller (Good Lock)";
            case BOUNCING:
                return "Bouncing color (Good Lock)";
            default:
                return "Good Lock particle";
        }
    }

    @Override
    public int getReadinessState() {
        return readinessState;
    }

    @Override
    public String getReadinessDetail() {
        return readinessDetail;
    }

    @Override
    public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed || paused) {
            return;
        }
        lastSoundX = screenX;
        lastSoundY = screenY;
        dragSoundDistance = 0.0f;
        play(tapSound, TOUCH_SOUND_VOLUME);
        addTouch(Simulation.ACTION_DOWN, screenX, screenY);
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (destroyed || paused) {
            return;
        }
        float soundDx = screenX - lastSoundX;
        float soundDy = screenY - lastSoundY;
        dragSoundDistance += (float) Math.sqrt(soundDx * soundDx + soundDy * soundDy);
        lastSoundX = screenX;
        lastSoundY = screenY;
        if (dragSoundDistance > dragSoundThresholdPx()) {
            play(dragSound, TOUCH_SOUND_VOLUME);
            dragSoundDistance = 0.0f;
        }
        addTouch(Simulation.ACTION_MOVE, screenX, screenY);
    }

    @Override
    public void finishGesture(boolean completed) {
        // Stock particle effects have neither an unlock burst nor accelerated unlock physics.
        // There is deliberately no frameStep * 5 adaptation here.
        dragSoundDistance = 0.0f;
        if (completed) {
            play(unlockSound, 1.0f);
        }
    }

    @Override
    public void cancelGesture() {
        // The stock gesture handler has no cancellation-specific particle action.
        dragSoundDistance = 0.0f;
    }

    @Override
    public void resetEffect() {
        simulation.clear();
        paints.clear();
        previousFrameTimeMs = 0L;
        removeCallbacks(frameRunnable);
        Choreographer.getInstance().removeFrameCallback(highFrameCallback);
        frameScheduled = false;
        pendingHighFrameStep = 0.0f;
        highFrameClock.reset();
        invalidate();
    }

    @Override
    public void warmUp() {
        if (!destroyed) {
            invalidate();
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        // Samsung's Good Lock particle classes do not implement a screen-on/affordance burst.
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalBackground && isUsable(backgroundBitmap);
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || !isUsable(source)) {
            return;
        }
        // Keep the source dimensions: Samsung maps screen coordinates using its actual wallpaper
        // size, rather than centre-cropping it to the View.  The bitmap remains sample-only.
        backgroundBitmap = source;
        wallpaperSampler = new CachedBitmapPixelSampler(source);
        externalBackground = true;
        source.prepareToDraw();
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        backgroundBitmap = null;
        wallpaperSampler = null;
        externalBackground = false;
        resetEffect();
    }

    @Override
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && bitmap == backgroundBitmap;
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        resetEffect();
        soundPool.release();
        backgroundBitmap = null;
        wallpaperSampler = null;
        externalBackground = false;
        setReadiness(UnlockEffectReadiness.STATE_FAILED, "renderer destroyed");
        readinessListener = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        paused = false;
        canvasReady = false;
        setReadiness(UnlockEffectReadiness.STATE_ATTACHED, "canvas attached; waiting for warm draw");
        if (!simulation.isEmpty()) {
            scheduleFrame();
        }
        warmUp();
    }

    @Override
    protected void onDetachedFromWindow() {
        paused = true;
        removeCallbacks(frameRunnable);
        Choreographer.getInstance().removeFrameCallback(highFrameCallback);
        frameScheduled = false;
        previousFrameTimeMs = 0L;
        highFrameClock.reset();
        setReadiness(UnlockEffectReadiness.STATE_DETACHED, "canvas detached");
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!canvasReady) {
            canvasReady = true;
            setReadiness(UnlockEffectReadiness.STATE_FIRST_FRAME_READY,
                    "transparent canvas warm frame drawn");
        }
        if (destroyed || paused || simulation.isEmpty()) {
            return;
        }

        long nowMs = SystemClock.uptimeMillis();
        if (previousFrameTimeMs == 0L) {
            previousFrameTimeMs = nowMs;
        }
        if (highFrameRateEnabled) {
            simulation.advanceHighFrame(pendingHighFrameStep);
            pendingHighFrameStep = 0.0f;
        } else {
            float frameStep = frameStepForElapsedMs(nowMs - previousFrameTimeMs);
            previousFrameTimeMs = nowMs;
            simulation.advance(frameStep);
        }
        simulation.removeReleasedPaints(paints);
        drawParticles(canvas);
        lastPresentationAtMs = nowMs;
        if (simulation.isEmpty()) {
            paints.clear();
        } else {
            scheduleFrame();
        }
    }

    private void addTouch(int action, float screenX, float screenY) {
        PixelSampler wallpaper = wallpaperSampler;
        if (!isUsable(backgroundBitmap) || wallpaper == null) {
            return;
        }
        boolean wasEmpty = simulation.isEmpty();
        simulation.touch(action, screenX, screenY, wallpaper);
        if (wasEmpty && !simulation.isEmpty()) {
            previousFrameTimeMs = SystemClock.uptimeMillis();
            scheduleFrame();
        }
    }

    private void drawParticles(Canvas canvas) {
        for (int index = 0; index < simulation.particleCount(); index++) {
            Particle particle = simulation.particleAt(index);
            Paint paint = paints.get(particle);
            if (paint == null) {
                // Samsung allocates a plain Paint in each Particle init (no anti-alias flag).
                paint = new Paint();
                paint.setColor(particle.color);
                if (particle.kind == Variant.RECTANGLE) {
                    paint.setStrokeWidth((particle.size / 4.0f) + 1.0f);
                    paint.setStyle(Paint.Style.STROKE);
                }
                paints.put(particle, paint);
            }
            if (particle.kind == Variant.RECTANGLE) {
                canvas.save();
                canvas.rotate(particle.degree, particle.x, particle.y);
                float half = particle.size / 2.0f;
                canvas.drawRect(particle.x - half, particle.y - half,
                        particle.x + half, particle.y + half, paint);
                canvas.restore();
            } else {
                canvas.drawCircle(particle.x, particle.y, particle.size, paint);
            }
        }
    }

    private void scheduleFrame() {
        if (frameScheduled || destroyed || paused || simulation.isEmpty()) {
            return;
        }
        if (highFrameRateEnabled) {
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(highFrameCallback);
            return;
        }
        long nowMs = SystemClock.uptimeMillis();
        long dueInMs = Math.max(0L, MAX_PRESENTATION_INTERVAL_MS
                - Math.max(0L, nowMs - lastPresentationAtMs));
        frameScheduled = true;
        postDelayed(frameRunnable, dueInMs);
    }

    private float dragSoundThresholdPx() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float minimumDimension = Math.min(
                Math.max(1, metrics.widthPixels), Math.max(1, metrics.heightPixels));
        return Math.max(72.0f * metrics.density, minimumDimension * 0.2f);
    }

    private void play(int soundId, float volume) {
        if (!destroyed && soundId != 0
                && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(soundId, volume, volume, 0, 0, 1.0f);
        }
    }

    private static boolean isUsable(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled();
    }

    private void setReadiness(int state, String detail) {
        readinessState = state;
        readinessDetail = "Good Lock particle: " + detail;
        notifyReadiness();
    }

    private void notifyReadiness() {
        UnlockEffectReadiness.ReadinessListener listener = readinessListener;
        if (listener == null) {
            return;
        }
        try {
            listener.onReadinessChanged();
        } catch (RuntimeException ignored) {
            // Readiness is advisory and cannot be allowed to break View lifecycle work.
        }
    }

    static float frameStepForElapsedMs(long elapsedMs) {
        return elapsedMs / (float) STOCK_FRAME_UNIT_MS;
    }

    static long maximumPresentationIntervalMs() {
        return MAX_PRESENTATION_INTERVAL_MS;
    }

    static float sanitizeSpeedMultiplier(float multiplier) {
        if (Float.isNaN(multiplier) || Float.isInfinite(multiplier)) {
            return 1.0f;
        }
        return Math.max(1.0f, Math.min(2.0f, multiplier));
    }

    /** Deterministic seam for HFR tests; returns elapsed display frames in 60 Hz units. */
    static final class HighFrameClock {
        private static final long MAX_ACCEPTED_DELTA_NANOS = STOCK_PRESENTATION_INTERVAL_NANOS * 4L;
        private long previousFrameNanos;

        float consume(long frameNanos) {
            if (previousFrameNanos == 0L) {
                previousFrameNanos = frameNanos;
                return 0.0f;
            }
            long elapsedNanos = frameNanos - previousFrameNanos;
            previousFrameNanos = frameNanos;
            if (elapsedNanos <= 0L || elapsedNanos > MAX_ACCEPTED_DELTA_NANOS) {
                return 0.0f;
            }
            return elapsedNanos / (float) STOCK_PRESENTATION_INTERVAL_NANOS;
        }

        void reset() {
            previousFrameNanos = 0L;
        }
    }

    /**
     * A screenshot does not change while it is installed as this effect's colour map.  Snapshot
     * its pixels once, outside the input/draw hot paths, so a long Popping drag does not cross the
     * Bitmap JNI boundary once for every emitted particle.  Each particle still obtains its own
     * coordinate's exact ARGB value from this sampler.
     */
    private static final class CachedBitmapPixelSampler implements PixelSampler {
        private final Bitmap fallbackBitmap;
        private final int width;
        private final int height;
        private final int[] pixels;

        CachedBitmapPixelSampler(Bitmap bitmap) {
            fallbackBitmap = bitmap;
            width = bitmap.getWidth();
            height = bitmap.getHeight();
            int[] snapshot = null;
            try {
                snapshot = new int[width * height];
                bitmap.getPixels(snapshot, 0, width, 0, 0, width, height);
            } catch (OutOfMemoryError ignored) {
                // Preserve behaviour on unusually large captures; only the optimization is lost.
                snapshot = null;
            } catch (RuntimeException ignored) {
                snapshot = null;
            }
            pixels = snapshot;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int getPixel(int x, int y) {
            int[] snapshot = pixels;
            return snapshot != null ? snapshot[y * width + x] : fallbackBitmap.getPixel(x, y);
        }
    }

    interface RandomSource {
        float nextFloat();

        int nextInt(int bound);
    }

    private static final class JavaRandomSource implements RandomSource {
        private final Random random;

        JavaRandomSource(Random random) {
            if (random == null) {
                throw new IllegalArgumentException("random == null");
            }
            this.random = random;
        }

        @Override
        public float nextFloat() {
            return random.nextFloat();
        }

        @Override
        public int nextInt(int bound) {
            return random.nextInt(bound);
        }
    }

    interface PixelSampler {
        int width();

        int height();

        int getPixel(int x, int y);
    }

    /**
     * Android-free physics/input core.  Its package-visible seams are used by host tests to pin
     * the OEM formulae while the View remains the small Android drawing/lifecycle adapter.
     */
    static final class Simulation {
        static final int ACTION_DOWN = 0;
        static final int ACTION_MOVE = 2;
        private static final int INITIAL_PARTICLE_COUNT = 5;
        private static final float PARTICLE_INTERPOLATION_COUNT = 20.0f;

        private final Variant variant;
        private final float screenWidth;
        private final float screenHeight;
        private final float interpolationX;
        private final float interpolationY;
        /* Decompiled stock derives this from X and then applies it to both axes. */
        private final float minimumCreateDistance;
        private final RandomSource random;
        private final ArrayList<Particle> particles = new ArrayList<Particle>();
        private final ArrayList<Particle> releasedParticles = new ArrayList<Particle>();
        private float oldTouchX = -1.0f;
        private float oldTouchY = -1.0f;

        Simulation(Variant variant, float screenWidth, float screenHeight, RandomSource random) {
            if (variant == null || random == null) {
                throw new IllegalArgumentException("variant/random == null");
            }
            this.variant = variant;
            this.screenWidth = Math.max(1.0f, screenWidth);
            this.screenHeight = Math.max(1.0f, screenHeight);
            this.random = random;
            interpolationX = this.screenWidth / PARTICLE_INTERPOLATION_COUNT;
            interpolationY = this.screenHeight / PARTICLE_INTERPOLATION_COUNT;
            minimumCreateDistance = interpolationX / 2.0f;
        }

        void touch(int action, float currentX, float currentY, PixelSampler wallpaper) {
            if (wallpaper == null || wallpaper.width() <= 0 || wallpaper.height() <= 0) {
                return;
            }
            if (action == ACTION_DOWN) {
                oldTouchX = currentX;
                oldTouchY = currentY;
            }
            float distanceX = oldTouchX - currentX;
            float distanceY = oldTouchY - currentY;
            float interpolationStepCntX = Math.abs(distanceX) / interpolationX;
            float interpolationStepCntY = Math.abs(distanceY) / interpolationY;
            int interpolationStepCnt = (int) interpolationStepCntX;
            if (interpolationStepCntX < interpolationStepCntY) {
                interpolationStepCnt = (int) interpolationStepCntY;
            }
            boolean skip = false;
            if (action == ACTION_DOWN) {
                interpolationStepCnt = INITIAL_PARTICLE_COUNT;
            } else if (interpolationStepCnt == 0) {
                if (Math.abs(distanceX) < minimumCreateDistance
                        && Math.abs(distanceY) < minimumCreateDistance) {
                    skip = true;
                } else {
                    interpolationStepCnt = 1;
                }
            }
            // Retain Samsung's signed formula.  It is counter-intuitive but deliberately mirrors
            // a drag segment around the old touch point instead of heading toward the new point.
            float interpolationDistanceX = distanceX / interpolationStepCnt;
            float interpolationDistanceY = distanceY / interpolationStepCnt;
            for (int step = 1; step <= interpolationStepCnt; step++) {
                float stepX = oldTouchX + step * interpolationDistanceX;
                float stepY = oldTouchY + step * interpolationDistanceY;
                float adjustedX = stepX * (wallpaper.width() / screenWidth);
                float adjustedY = stepY * (wallpaper.height() / screenHeight);
                if (adjustedX >= 0.0f && wallpaper.width() > adjustedX
                        && adjustedY >= 0.0f && wallpaper.height() > adjustedY) {
                    particles.add(Particle.create(variant,
                            wallpaper.getPixel((int) adjustedX, (int) adjustedY),
                            stepX, stepY, random));
                }
            }
            if (!skip) {
                oldTouchX = currentX;
                oldTouchY = currentY;
            }
        }

        void advance(float frameStep) {
            releasedParticles.clear();
            for (int index = particles.size() - 1; index >= 0; index--) {
                Particle particle = particles.get(index);
                particle.advance(frameStep, screenWidth, screenHeight, random);
                if (particle.removed) {
                    particles.remove(index);
                    releasedParticles.add(particle);
                }
            }
        }

        /** Advances a HFR presentation frame in 60 Hz logical-frame units. */
        void advanceHighFrame(float q) {
            if (q <= 0.0f) {
                return;
            }
            if (q == 1.0f) {
                // Keep the stock operation order and values bit-for-bit on a 60 Hz callback.
                advance(STOCK_PRESENTATION_INTERVAL_NANOS / 10_000_000.0f);
                return;
            }
            releasedParticles.clear();
            for (int index = particles.size() - 1; index >= 0; index--) {
                Particle particle = particles.get(index);
                particle.advanceHighFrame(q, screenWidth, screenHeight, random);
                if (particle.removed) {
                    particles.remove(index);
                    releasedParticles.add(particle);
                }
            }
        }

        /** Kept allocation-free; Paint instances are only retained while a particle is alive. */
        void removeReleasedPaints(Map<Particle, Paint> paintMap) {
            for (int index = 0; index < releasedParticles.size(); index++) {
                paintMap.remove(releasedParticles.get(index));
            }
            releasedParticles.clear();
        }

        void clear() {
            particles.clear();
            releasedParticles.clear();
        }

        boolean isEmpty() {
            return particles.isEmpty();
        }

        int particleCount() {
            return particles.size();
        }

        Particle particleAt(int index) {
            return particles.get(index);
        }

        float minimumCreateDistance() {
            return minimumCreateDistance;
        }
    }

    static final class Particle {
        final Variant kind;
        final int color;
        final float size;
        final float movementY;
        final float rotation;
        final float decelerationX;
        float x;
        float y;
        float movementX;
        float accelerationY;
        float degree;
        boolean removed;

        private Particle(Variant kind, int color, float x, float y, float size,
                float movementX, float movementY, float rotation, float accelerationY,
                float decelerationX) {
            this.kind = kind;
            this.color = color;
            this.x = x;
            this.y = y;
            this.size = size;
            this.movementX = movementX;
            this.movementY = movementY;
            this.rotation = rotation;
            this.accelerationY = accelerationY;
            this.decelerationX = decelerationX;
        }

        static Particle create(Variant variant, int wallpaperColor, float x, float y,
                RandomSource random) {
            int color = adjustColor(wallpaperColor, random.nextInt(40) - 20);
            if (variant == Variant.POPPING) {
                float movementX = random.nextFloat() * 3.0f - 1.5f;
                float movementY = random.nextFloat() * 5.0f + 5.0f;
                float radius = random.nextFloat() * 30.0f + 10.0f;
                return new Particle(variant, color, x, y, radius, movementX, movementY,
                        0.0f, 0.0f, 0.0f);
            }
            if (variant == Variant.RECTANGLE) {
                float movementX = random.nextFloat() * 10.0f - 5.0f;
                float movementY = random.nextFloat() * 10.0f - 5.0f;
                float size = random.nextFloat() * 30.0f + 10.0f;
                float rotation = random.nextFloat() * 8.0f - 4.0f;
                rotation = rotation < 0.0f ? rotation - 2.0f : rotation + 2.0f;
                return new Particle(variant, color, x, y, size, movementX, movementY,
                        rotation, 0.0f, 0.0f);
            }
            float movementX = random.nextFloat() * 7.0f - 3.5f;
            movementX = movementX < 0.0f ? movementX - 3.0f : movementX + 3.0f;
            float accelerationY = random.nextFloat() * 15.0f + 5.0f;
            float radius = random.nextFloat() * 30.0f + 10.0f;
            float decelerationX = 0.0f;
            if (random.nextInt(3) == 0) {
                decelerationX = random.nextFloat() * 0.15f + 0.1f;
                if (movementX > 0.0f) {
                    decelerationX *= -1.0f;
                }
            }
            return new Particle(variant, color, x, y, radius, movementX, 0.0f,
                    0.0f, accelerationY, decelerationX);
        }

        void advance(float step, float screenWidth, float screenHeight, RandomSource random) {
            if (kind == Variant.POPPING) {
                x += movementX * step;
                y -= movementY * step;
                removed = y < 0.0f;
                return;
            }
            if (kind == Variant.RECTANGLE) {
                x += movementX * step;
                y += movementY * step;
                degree += rotation * step;
                removed = y < 0.0f || y > screenHeight || x < 0.0f || x > screenWidth;
                return;
            }
            x += movementX * step;
            // This remains per draw, rather than per elapsed 10 ms unit, exactly as Samsung.
            movementX += decelerationX;
            y -= accelerationY * step;
            accelerationY -= 1.0f;
            if (y + size >= screenHeight) {
                accelerationY = random.nextFloat() * 19.0f + 5.0f;
                y = screenHeight - size;
            }
            removed = x < 0.0f || screenWidth < x;
        }

        void advanceHighFrame(float q, float screenWidth, float screenHeight,
                RandomSource random) {
            if (q == 1.0f) {
                advance(STOCK_PRESENTATION_INTERVAL_NANOS / 10_000_000.0f,
                        screenWidth, screenHeight, random);
                return;
            }
            float stockStep = STOCK_PRESENTATION_INTERVAL_NANOS / 10_000_000.0f;
            if (kind == Variant.POPPING) {
                x += movementX * stockStep * q;
                y -= movementY * stockStep * q;
                removed = y < 0.0f;
                return;
            }
            if (kind == Variant.RECTANGLE) {
                x += movementX * stockStep * q;
                y += movementY * stockStep * q;
                degree += rotation * stockStep * q;
                removed = y < 0.0f || y > screenHeight || x < 0.0f || x > screenWidth;
                return;
            }
            // Exact composition of the stock "position, then per-draw velocity" update.
            x += stockStep * (movementX * q
                    + 0.5f * decelerationX * q * (q - 1.0f));
            movementX += decelerationX * q;
            y -= stockStep * (accelerationY * q - 0.5f * q * (q - 1.0f));
            accelerationY -= q;
            if (y + size >= screenHeight) {
                accelerationY = random.nextFloat() * 19.0f + 5.0f;
                y = screenHeight - size;
            }
            removed = x < 0.0f || screenWidth < x;
        }

        private static int adjustColor(int color, int adjustment) {
            int red = clamp(((color >> 16) & 0xff) + adjustment);
            int green = clamp(((color >> 8) & 0xff) + adjustment);
            int blue = clamp((color & 0xff) + adjustment);
            return 0xff000000 | (red << 16) | (green << 8) | blue;
        }

        private static int clamp(int value) {
            return value < 0 ? 0 : (value > 255 ? 255 : value);
        }
    }
}
