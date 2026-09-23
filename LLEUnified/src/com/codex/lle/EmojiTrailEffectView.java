package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.SoundPool;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Easter-egg particle trail using the user's selected system emoji glyphs. */
final class EmojiTrailEffectView extends View implements UnlockEffectRenderer {
    private static final int MAX_PARTICLES = 72;
    private static final float MIN_TRAVEL_DP = 12f;
    private static final long DRAG_SOUND_INTERVAL_MS = 240L;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final Random random = new Random();
    private final ArrayList<Bitmap> emojiBitmaps = new ArrayList<Bitmap>();
    private final ArrayList<Particle> particles = new ArrayList<Particle>();
    private float lastSpawnX;
    private float lastSpawnY;
    private long lastDragSoundAt;
    private boolean gestureActive;
    private boolean destroyed;
    private SoundPool soundPool;
    private int tapSound;
    private int unlockSound;
    private int dragSound;

    EmojiTrailEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        buildEmojiBitmaps(OverlayPrefs.emojiTrailEmojis(context));
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "Emoji Trail";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed || emojiBitmaps.isEmpty()) {
            return;
        }
        gestureActive = true;
        lastSpawnX = screenX;
        lastSpawnY = screenY;
        lastDragSoundAt = SystemClock.uptimeMillis();
        ensureSounds();
        play(tapSound);
        for (int i = 0; i < 4; i++) {
            spawnParticle(screenX, screenY, true);
        }
        invalidate();
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (destroyed || emojiBitmaps.isEmpty()) {
            return;
        }
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        float dx = screenX - lastSpawnX;
        float dy = screenY - lastSpawnY;
        float minimum = MIN_TRAVEL_DP * density();
        if (dx * dx + dy * dy >= minimum * minimum) {
            spawnParticle(screenX, screenY, false);
            if (random.nextInt(4) == 0) {
                spawnParticle(screenX, screenY, false);
            }
            lastSpawnX = screenX;
            lastSpawnY = screenY;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastDragSoundAt >= DRAG_SOUND_INTERVAL_MS) {
            ensureSounds();
            play(dragSound);
            lastDragSoundAt = now;
        }
        invalidate();
    }

    @Override
    public void finishGesture(boolean completed) {
        gestureActive = false;
        if (completed) {
            ensureSounds();
            play(unlockSound);
        }
        invalidate();
    }

    @Override
    public void cancelGesture() {
        gestureActive = false;
        particles.clear();
        invalidate();
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        particles.clear();
        invalidate();
    }

    @Override
    public void warmUp() {
        ensureSounds();
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        // Intentionally no hint: the hidden effect only reacts to touch.
    }

    @Override
    public void destroy() {
        destroyed = true;
        resetEffect();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        for (Bitmap bitmap : emojiBitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        emojiBitmaps.clear();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        Iterator<Particle> iterator = particles.iterator();
        boolean animating = false;
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            float age = now - particle.startMs;
            if (age >= particle.durationMs) {
                iterator.remove();
                continue;
            }
            drawParticle(canvas, particle, age);
            animating = true;
        }
        if (animating && !destroyed) {
            postInvalidateOnAnimation();
        }
    }

    private void spawnParticle(float x, float y, boolean burst) {
        if (emojiBitmaps.isEmpty()) {
            return;
        }
        while (particles.size() >= MAX_PARTICLES) {
            particles.remove(0);
        }
        float d = density();
        Particle particle = new Particle();
        particle.bitmap = emojiBitmaps.get(random.nextInt(emojiBitmaps.size()));
        particle.startX = x + (random.nextFloat() - 0.5f) * (burst ? 46f : 24f) * d;
        particle.startY = y + (random.nextFloat() - 0.5f) * (burst ? 38f : 18f) * d;
        particle.travelX = (random.nextFloat() - 0.5f) * (burst ? 96f : 72f) * d;
        particle.travelY = -(64f + random.nextFloat() * (burst ? 92f : 68f)) * d;
        particle.startScale = 0.24f + random.nextFloat() * 0.16f;
        particle.peakScale = 0.72f + random.nextFloat() * 0.52f;
        particle.endScale = 0.46f + random.nextFloat() * 0.24f;
        particle.rotationStart = -12f + random.nextFloat() * 24f;
        particle.rotationEnd = particle.rotationStart - 42f + random.nextFloat() * 84f;
        particle.durationMs = 850L + random.nextInt(501);
        particle.startMs = SystemClock.uptimeMillis();
        particles.add(particle);
    }

    private void drawParticle(Canvas canvas, Particle particle, float age) {
        float progress = clamp(age / particle.durationMs);
        float motion = decelerate(progress);
        float x = particle.startX + particle.travelX * motion;
        float y = particle.startY + particle.travelY * motion;
        float scale;
        if (progress < 0.22f) {
            scale = lerp(particle.startScale, particle.peakScale, progress / 0.22f);
        } else {
            scale = lerp(particle.peakScale, particle.endScale,
                    (progress - 0.22f) / 0.78f);
        }
        float alpha = progress < 0.12f
                ? progress / 0.12f
                : 1f - clamp((progress - 0.58f) / 0.42f);
        float rotation = lerp(particle.rotationStart, particle.rotationEnd, progress);
        drawBitmap(canvas, particle.bitmap, x, y, scale * displayScale(), rotation,
                Math.round(alpha * 255f));
    }

    private void drawBitmap(Canvas canvas, Bitmap bitmap, float x, float y,
            float scale, float rotation, int alpha) {
        if (bitmap == null || bitmap.isRecycled() || alpha <= 0) {
            return;
        }
        bitmapPaint.setAlpha(Math.max(0, Math.min(255, alpha)));
        matrix.reset();
        matrix.postTranslate(-bitmap.getWidth() * 0.5f, -bitmap.getHeight() * 0.5f);
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        matrix.postTranslate(x, y);
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        bitmapPaint.setAlpha(255);
    }

    private void buildEmojiBitmaps(List<String> emojis) {
        float d = density();
        int size = Math.max(48, Math.round(58f * d));
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(42f * d);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = size * 0.5f - (metrics.ascent + metrics.descent) * 0.5f;
        for (String emoji : emojis) {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawText(emoji, size * 0.5f, baseline, textPaint);
            emojiBitmaps.add(bitmap);
        }
    }

    private void ensureSounds() {
        if (soundPool != null) {
            return;
        }
        soundPool = new SoundPool(4, EffectAudio.streamType(getContext()), 0);
        tapSound = soundPool.load(getContext(), R.raw.summer_tap, 1);
        unlockSound = soundPool.load(getContext(), R.raw.summer_unlock, 1);
        dragSound = soundPool.load(getContext(), R.raw.summer_drag, 1);
    }

    private void play(int soundId) {
        if (!OverlayPrefs.unlockEffectSoundAllowedNow(getContext())
                || !EffectAudio.platformSoundSwitchAllows(getContext())) {
            return;
        }
        ensureSounds();
        if (soundPool != null && soundId != 0) {
            soundPool.play(soundId, 0.28f, 0.28f, 0, 0, 1f);
        }
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private float displayScale() {
        float d = density();
        int width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int height = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        if (d <= 0f || width <= 0 || height <= 0) {
            return 1f;
        }
        float shortSideDp = Math.min(width, height) / d;
        return Math.max(0.9f, Math.min(1.35f, shortSideDp / 360f));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * clamp(progress);
    }

    private static float decelerate(float value) {
        float inverse = 1f - clamp(value);
        return 1f - inverse * inverse;
    }

    private static final class Particle {
        Bitmap bitmap;
        float startX;
        float startY;
        float travelX;
        float travelY;
        float startScale;
        float peakScale;
        float endScale;
        float rotationStart;
        float rotationEnd;
        long startMs;
        long durationMs;
    }
}
