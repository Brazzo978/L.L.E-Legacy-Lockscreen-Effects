package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.SoundPool;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class SeasonalUnlockEffectView extends View implements UnlockEffectRenderer {
    private static final long FRAME_MS = 16L;
    private static final int SOUND_TAP = 0;
    private static final int SOUND_UNLOCK = 1;
    private static final int SOUND_DRAG = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final Random random = new Random();
    private final List<Sprite> particles = new ArrayList<Sprite>();
    private final TouchSprite[] touchSprites = new TouchSprite[3];
    private final Bitmap[][] particleBitmaps = new Bitmap[4][];
    private final Bitmap[][] touchBitmaps = new Bitmap[4][];
    private int seasonMode = SeasonalDoodleView.SEASON_AUTO;
    private int activeSeason = SeasonalDoodleView.SEASON_SPRING;
    private int dragSoundCount;
    private boolean destroyed;
    private boolean gestureActive;
    private SoundPool soundPool;
    private final int[][] sounds = new int[4][3];

    public SeasonalUnlockEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        loadBitmaps();
    }

    void setSeasonMode(int mode) {
        seasonMode = mode;
        int resolved = resolveSeason();
        if (activeSeason != resolved) {
            activeSeason = resolved;
            resetEffect();
        }
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "Seasonal unlock partner";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        activeSeason = resolveSeason();
        gestureActive = true;
        dragSoundCount = 50;
        playSound(SOUND_TAP);
        clearTouchSprites();
        addTouchSprites(screenX, screenY);
        invalidate();
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        dragSoundCount++;
        if (dragSoundCount >= 60) {
            playSound(SOUND_DRAG);
            dragSoundCount = 0;
        }
        updateTouchSprites(screenX, screenY);
        spawnParticle(screenX, screenY);
        invalidate();
    }

    @Override
    public void finishGesture(boolean completed) {
        if (completed) {
            playSound(SOUND_UNLOCK);
        }
        gestureActive = false;
        particles.clear();
        clearTouchSprites();
        invalidate();
    }

    @Override
    public void cancelGesture() {
        gestureActive = false;
        particles.clear();
        clearTouchSprites();
        invalidate();
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        particles.clear();
        clearTouchSprites();
        invalidate();
    }

    @Override
    public void warmUp() {
        activeSeason = resolveSeason();
        ensureSounds();
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        // Samsung seasonal partner has no separate screen-on hint path.
    }

    @Override
    public void destroy() {
        destroyed = true;
        resetEffect();
        releaseSounds();
        recycleAll(particleBitmaps);
        recycleAll(touchBitmaps);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean keepAnimating = false;
        for (int i = 0; i < touchSprites.length; i++) {
            TouchSprite touch = touchSprites[i];
            if (touch != null) {
                drawTouchSprite(canvas, touch, now);
                keepAnimating = true;
            }
        }
        Iterator<Sprite> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Sprite sprite = iterator.next();
            if (now >= sprite.startMs + sprite.durationMs) {
                iterator.remove();
                continue;
            }
            drawParticle(canvas, sprite, now);
            keepAnimating = true;
        }
        if (keepAnimating && !destroyed) {
            postInvalidateDelayed(FRAME_MS);
        }
    }

    private void addTouchSprites(float x, float y) {
        int season = activeSeason;
        if (season == SeasonalDoodleView.SEASON_SUMMER) {
            touchSprites[0] = TouchSprite.summer(touchBitmaps[season][0], 92f, 92f);
            touchSprites[1] = TouchSprite.summer(touchBitmaps[season][1], 14f, 13f);
            touchSprites[2] = TouchSprite.summer(touchBitmaps[season][2], 29f, 25f);
        } else if (season == SeasonalDoodleView.SEASON_WINTER) {
            touchSprites[0] = TouchSprite.staticSprite(touchBitmaps[season][0], 30f, 30f);
            touchSprites[1] = TouchSprite.staticSprite(touchBitmaps[season][1], 44f, 31f);
        } else {
            touchSprites[0] = TouchSprite.springLike(touchBitmaps[season][1], 72f, 72f, true);
            touchSprites[1] = TouchSprite.springLike(touchBitmaps[season][0], 35f, 35f, false);
        }
        updateTouchSprites(x, y);
    }

    private void updateTouchSprites(float x, float y) {
        float d = density();
        int season = activeSeason;
        for (int i = 0; i < touchSprites.length; i++) {
            TouchSprite touch = touchSprites[i];
            if (touch == null) {
                continue;
            }
            touch.x = x - touch.offsetXdp * d;
            touch.y = y - touch.offsetYdp * d;
            if (season == SeasonalDoodleView.SEASON_SUMMER) {
                if (i == 1) {
                    touch.driftDp = Math.min(63f, touch.driftDp + 2f);
                    touch.x -= touch.driftDp * d;
                    touch.y -= touch.driftDp * d;
                } else if (i == 2) {
                    touch.driftDp = Math.min(81f, touch.driftDp + 2f);
                    touch.x -= touch.driftDp * d;
                    touch.y -= touch.driftDp * d;
                }
            } else if (season == SeasonalDoodleView.SEASON_WINTER && i == 1) {
                touch.driftDp = Math.min(10f, touch.driftDp + 2f);
                touch.x -= touch.driftDp * d;
                touch.y -= touch.driftDp * d;
            }
        }
    }

    private void spawnParticle(float x, float y) {
        int season = activeSeason;
        int randomSlot;
        int spriteIndex;
        float dx;
        if (season == SeasonalDoodleView.SEASON_SPRING) {
            randomSlot = random.nextInt(7);
            if (randomSlot >= 4) {
                return;
            }
            spriteIndex = randomSlot;
            dx = random.nextFloat() * 200f;
            addParticle(season, spriteIndex, x - dx, y + dx, springScale(spriteIndex),
                    0.55f, 125L, 375L, 500L, 250L, 750L,
                    random.nextInt(360), 1000L);
        } else if (season == SeasonalDoodleView.SEASON_SUMMER) {
            randomSlot = random.nextInt(11);
            if (randomSlot >= 6) {
                return;
            }
            spriteIndex = randomSlot;
            dx = random.nextFloat() * 200f;
            Sprite particle = addParticle(season, spriteIndex, x - dx, y + dx, summerScale(spriteIndex),
                    0.55f, 125L, 375L, 500L, 250L, 500L,
                    359f, 1000L);
            if (particle != null) {
                particle.alphaHoldMs = 250L;
            }
        } else if (season == SeasonalDoodleView.SEASON_AUTUMN) {
            randomSlot = random.nextInt(9);
            if (randomSlot >= 5) {
                return;
            }
            spriteIndex = randomSlot;
            dx = random.nextFloat() * 200f;
            addParticle(season, spriteIndex, x - dx, y + dx, 1f,
                    0.5f, 125L, 375L, 500L, 250L, 750L,
                    random.nextInt(360), 1000L);
        } else {
            randomSlot = random.nextInt(5);
            if (randomSlot >= 3) {
                return;
            }
            spriteIndex = randomSlot;
            dx = random.nextFloat() * 100f;
            if (spriteIndex == 0) {
                addParticle(season, spriteIndex, x - dx, y + dx,
                        0.6f + 0.1f * random.nextInt(9), 0f,
                        80L, 70L, 450L, 150L, 450L, 0f, 0L);
            } else if (spriteIndex == 1) {
                addParticle(season, spriteIndex, x - dx, y + dx,
                        0.5f + 0.1f * random.nextInt(7), 0f,
                        130L, 120L, 750L, 250L, 750L, 0f, 0L);
            } else {
                addParticle(season, spriteIndex, x - dx, y + dx,
                        0.5f + 0.1f * random.nextInt(11), 0f,
                        250L, 250L, 500L, 250L, 750L, 340f, 1000L);
            }
        }
    }

    private Sprite addParticle(int season, int spriteIndex, float x, float y,
            float peakScale, float endScale, long scaleInMs, long holdMs, long scaleOutMs,
            long alphaInMs, long alphaOutMs, float rotationEnd, long rotationMs) {
        Bitmap[] bitmaps = particleBitmaps[season];
        if (bitmaps == null || spriteIndex < 0 || spriteIndex >= bitmaps.length) {
            return null;
        }
        Sprite sprite = new Sprite();
        sprite.bitmap = bitmaps[spriteIndex];
        sprite.x = x;
        sprite.y = y;
        sprite.peakScale = peakScale;
        sprite.endScale = endScale;
        sprite.scaleInMs = scaleInMs;
        sprite.holdMs = holdMs;
        sprite.scaleOutMs = scaleOutMs;
        sprite.alphaInMs = alphaInMs;
        sprite.alphaOutMs = alphaOutMs;
        sprite.rotationEnd = rotationEnd;
        sprite.rotationMs = rotationMs;
        sprite.startMs = SystemClock.uptimeMillis();
        sprite.durationMs = Math.max(scaleInMs + holdMs + scaleOutMs, alphaInMs + alphaOutMs);
        particles.add(sprite);
        return sprite;
    }

    private void drawTouchSprite(Canvas canvas, TouchSprite touch, long now) {
        if (touch.bitmap == null || touch.bitmap.isRecycled()) {
            return;
        }
        float age = now - touch.startMs;
        float scale = touchScale(touch, age);
        int alpha = Math.round(255f * touchAlpha(touch, age));
        drawBitmap(canvas, touch.bitmap, touch.x, touch.y, scale, 0f, alpha);
    }

    private float touchScale(TouchSprite touch, float age) {
        if (touch.mode == TouchSprite.MODE_STATIC) {
            return 1f;
        }
        if (touch.mode == TouchSprite.MODE_SUMMER) {
            return touch.offsetXdp > 80f
                    ? lerp(0.3f, 0.95f, clamp(age / 2130f))
                    : 1f;
        }
        if (touch.bigSpring) {
            if (age < 1470f) {
                return lerp(0.75f, 0.95f, age / 1470f);
            }
            return lerp(0.95f, 1f, clamp((age - 1470f) / 330f));
        }
        if (age < 330f) {
            return lerp(0.4f, 0.5f, age / 330f);
        }
        return lerp(0.5f, 1f, clamp((age - 330f) / 1470f));
    }

    private float touchAlpha(TouchSprite touch, float age) {
        if (touch.mode == TouchSprite.MODE_STATIC) {
            return 1f;
        }
        if (touch.mode == TouchSprite.MODE_SUMMER && touch.offsetXdp > 80f) {
            return clamp(age / 1800f);
        }
        if (touch.mode == TouchSprite.MODE_SUMMER) {
            return clamp(age / 330f);
        }
        return clamp(age / (touch.bigSpring ? 1470f : 330f));
    }

    private void drawParticle(Canvas canvas, Sprite sprite, long now) {
        if (sprite.bitmap == null || sprite.bitmap.isRecycled()) {
            return;
        }
        float age = now - sprite.startMs;
        float scale = particleScale(sprite, age);
        float alpha = particleAlpha(sprite, age);
        float rotation = sprite.rotationMs <= 0L
                ? 0f
                : lerp(0f, sprite.rotationEnd, clamp(age / sprite.rotationMs));
        drawBitmap(canvas, sprite.bitmap, sprite.x, sprite.y, scale, rotation,
                Math.round(255f * alpha));
    }

    private float particleScale(Sprite sprite, float age) {
        if (age < sprite.scaleInMs) {
            return lerp(0f, sprite.peakScale, age / sprite.scaleInMs);
        }
        age -= sprite.scaleInMs;
        if (age < sprite.holdMs) {
            return sprite.peakScale;
        }
        age -= sprite.holdMs;
        return lerp(sprite.peakScale, sprite.endScale, clamp(age / sprite.scaleOutMs));
    }

    private float particleAlpha(Sprite sprite, float age) {
        if (age < sprite.alphaInMs) {
            return clamp(age / sprite.alphaInMs);
        }
        age -= sprite.alphaInMs;
        if (age < sprite.alphaHoldMs) {
            return 1f;
        }
        age -= sprite.alphaHoldMs;
        return lerp(1f, 0f, clamp(age / sprite.alphaOutMs));
    }

    private void drawBitmap(Canvas canvas, Bitmap bitmap, float x, float y,
            float scale, float rotation, int alpha) {
        if (bitmap == null || bitmap.isRecycled() || alpha <= 0) {
            return;
        }
        paint.setAlpha(Math.max(0, Math.min(255, alpha)));
        matrix.reset();
        matrix.postTranslate(-bitmap.getWidth() * 0.5f, -bitmap.getHeight() * 0.5f);
        matrix.postScale(scale, scale);
        if (rotation != 0f) {
            matrix.postRotate(rotation);
        }
        matrix.postTranslate(x + bitmap.getWidth() * 0.5f, y + bitmap.getHeight() * 0.5f);
        canvas.drawBitmap(bitmap, matrix, paint);
        paint.setAlpha(255);
    }

    private float springScale(int index) {
        if (index == 3) {
            return 0.2f + 0.1f * random.nextInt(9);
        }
        return 0.4f + 0.1f * random.nextInt(7);
    }

    private float summerScale(int index) {
        if (index <= 1) {
            return 0.6f + 0.1f * random.nextInt(5);
        }
        if (index <= 3) {
            return 0.7f + 0.1f * random.nextInt(4);
        }
        if (index == 4) {
            return 1.0f + 0.1f * random.nextInt(3);
        }
        return 0.9f + 0.1f * random.nextInt(2);
    }

    private void clearTouchSprites() {
        for (int i = 0; i < touchSprites.length; i++) {
            touchSprites[i] = null;
        }
    }

    private int resolveSeason() {
        if (seasonMode >= SeasonalDoodleView.SEASON_SPRING
                && seasonMode <= SeasonalDoodleView.SEASON_WINTER) {
            return seasonMode;
        }
        int month = Calendar.getInstance().get(Calendar.MONTH);
        if (month >= Calendar.MARCH && month <= Calendar.MAY) {
            return SeasonalDoodleView.SEASON_SPRING;
        }
        if (month >= Calendar.JUNE && month <= Calendar.AUGUST) {
            return SeasonalDoodleView.SEASON_SUMMER;
        }
        if (month >= Calendar.SEPTEMBER && month <= Calendar.NOVEMBER) {
            return SeasonalDoodleView.SEASON_AUTUMN;
        }
        return SeasonalDoodleView.SEASON_WINTER;
    }

    private void loadBitmaps() {
        particleBitmaps[SeasonalDoodleView.SEASON_SPRING] = new Bitmap[] {
                bitmap(R.drawable.unlock_spring_particle_01),
                bitmap(R.drawable.unlock_spring_particle_02),
                bitmap(R.drawable.unlock_spring_particle_03),
                bitmap(R.drawable.unlock_spring_particle_04)
        };
        touchBitmaps[SeasonalDoodleView.SEASON_SPRING] = new Bitmap[] {
                bitmap(R.drawable.unlock_spring_touch_01),
                bitmap(R.drawable.unlock_spring_touch_02)
        };
        particleBitmaps[SeasonalDoodleView.SEASON_SUMMER] = new Bitmap[] {
                bitmap(R.drawable.unlock_summer_particle_01),
                bitmap(R.drawable.unlock_summer_particle_02),
                bitmap(R.drawable.unlock_summer_particle_03),
                bitmap(R.drawable.unlock_summer_particle_04),
                bitmap(R.drawable.unlock_summer_particle_05),
                bitmap(R.drawable.unlock_summer_particle_06)
        };
        touchBitmaps[SeasonalDoodleView.SEASON_SUMMER] = new Bitmap[] {
                bitmap(R.drawable.unlock_summer_touch_01),
                bitmap(R.drawable.unlock_summer_touch_02),
                bitmap(R.drawable.unlock_summer_touch_03)
        };
        particleBitmaps[SeasonalDoodleView.SEASON_AUTUMN] = new Bitmap[] {
                bitmap(R.drawable.unlock_autumn_particle_01),
                bitmap(R.drawable.unlock_autumn_particle_02),
                bitmap(R.drawable.unlock_autumn_particle_03),
                bitmap(R.drawable.unlock_autumn_particle_04),
                bitmap(R.drawable.unlock_autumn_particle_05)
        };
        touchBitmaps[SeasonalDoodleView.SEASON_AUTUMN] = touchBitmaps[SeasonalDoodleView.SEASON_SPRING];
        particleBitmaps[SeasonalDoodleView.SEASON_WINTER] = new Bitmap[] {
                bitmap(R.drawable.festival_unlock_effect_01),
                bitmap(R.drawable.festival_unlock_effect_02),
                bitmap(R.drawable.festival_unlock_effect_03)
        };
        touchBitmaps[SeasonalDoodleView.SEASON_WINTER] = new Bitmap[] {
                bitmap(R.drawable.festival_unlock_touch_01),
                bitmap(R.drawable.festival_unlock_touch_02)
        };
    }

    private Bitmap bitmap(int resId) {
        return BitmapFactory.decodeResource(getResources(), resId);
    }

    private void ensureSounds() {
        if (soundPool != null) {
            return;
        }
        soundPool = new SoundPool(10, 1, 0);
        loadAllSeasonSounds();
    }

    private void loadAllSeasonSounds() {
        if (soundPool == null) {
            return;
        }
        loadSeasonSounds(SeasonalDoodleView.SEASON_SPRING,
                R.raw.spring_tap, R.raw.spring_unlock, R.raw.spring_drag);
        loadSeasonSounds(SeasonalDoodleView.SEASON_SUMMER,
                R.raw.summer_tap, R.raw.summer_unlock, R.raw.summer_drag);
        loadSeasonSounds(SeasonalDoodleView.SEASON_AUTUMN,
                R.raw.autumn_tap, R.raw.autumn_unlock, R.raw.autumn_drag);
        loadSeasonSounds(SeasonalDoodleView.SEASON_WINTER,
                R.raw.winter_tap, R.raw.winter_unlock, R.raw.winter_drag);
    }

    private void loadSeasonSounds(int season, int tapRes, int unlockRes, int dragRes) {
        sounds[season][SOUND_TAP] = soundPool.load(getContext(), tapRes, 1);
        sounds[season][SOUND_UNLOCK] = soundPool.load(getContext(), unlockRes, 1);
        sounds[season][SOUND_DRAG] = soundPool.load(getContext(), dragRes, 1);
    }

    private void playSound(int id) {
        if (!lockscreenSoundsEnabled()) {
            return;
        }
        int season = resolveSeason();
        activeSeason = season;
        ensureSounds();
        if (soundPool != null && id >= 0 && id < sounds[season].length
                && sounds[season][id] != 0) {
            soundPool.play(sounds[season][id], 0.3f, 0.3f, 0, 0, 1f);
        }
    }

    private boolean lockscreenSoundsEnabled() {
        return Settings.System.getInt(getContext().getContentResolver(),
                "lockscreen_sounds_enabled", 1) != 0;
    }

    private void releaseSounds() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        for (int season = 0; season < sounds.length; season++) {
            for (int i = 0; i < sounds[season].length; i++) {
                sounds[season][i] = 0;
            }
        }
    }

    private void recycleAll(Bitmap[][] groups) {
        for (int i = 0; i < groups.length; i++) {
            Bitmap[] group = groups[i];
            if (group == null) {
                continue;
            }
            for (int j = 0; j < group.length; j++) {
                Bitmap bitmap = group[j];
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float start, float end, float t) {
        float eased = accelerateDecelerate(clamp(t));
        return start + (end - start) * eased;
    }

    private static float accelerateDecelerate(float input) {
        return (float) (Math.cos((input + 1f) * Math.PI) * 0.5f + 0.5f);
    }

    private static final class Sprite {
        Bitmap bitmap;
        float x;
        float y;
        float peakScale;
        float endScale;
        float rotationEnd;
        long rotationMs;
        long scaleInMs;
        long holdMs;
        long scaleOutMs;
        long alphaInMs;
        long alphaHoldMs;
        long alphaOutMs;
        long startMs;
        long durationMs;
    }

    private static final class TouchSprite {
        static final int MODE_SPRING = 0;
        static final int MODE_SUMMER = 1;
        static final int MODE_STATIC = 2;

        Bitmap bitmap;
        float x;
        float y;
        float offsetXdp;
        float offsetYdp;
        float driftDp;
        long startMs;
        int mode;
        boolean bigSpring;

        static TouchSprite springLike(Bitmap bitmap, float offsetXdp, float offsetYdp,
                boolean big) {
            TouchSprite sprite = base(bitmap, offsetXdp, offsetYdp);
            sprite.mode = MODE_SPRING;
            sprite.bigSpring = big;
            return sprite;
        }

        static TouchSprite summer(Bitmap bitmap, float offsetXdp, float offsetYdp) {
            TouchSprite sprite = base(bitmap, offsetXdp, offsetYdp);
            sprite.mode = MODE_SUMMER;
            return sprite;
        }

        static TouchSprite staticSprite(Bitmap bitmap, float offsetXdp, float offsetYdp) {
            TouchSprite sprite = base(bitmap, offsetXdp, offsetYdp);
            sprite.mode = MODE_STATIC;
            return sprite;
        }

        private static TouchSprite base(Bitmap bitmap, float offsetXdp, float offsetYdp) {
            TouchSprite sprite = new TouchSprite();
            sprite.bitmap = bitmap;
            sprite.offsetXdp = offsetXdp;
            sprite.offsetYdp = offsetYdp;
            sprite.startMs = SystemClock.uptimeMillis();
            return sprite;
        }
    }
}
