package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Random;

public class SeasonalDoodleView extends View {
    private static final String TAG = "LLEDoodle";
    static final int SEASON_AUTO = -1;
    static final int SEASON_SPRING = 0;
    static final int SEASON_SUMMER = 1;
    static final int SEASON_AUTUMN = 2;
    static final int SEASON_WINTER = 3;

    private static final int SEASONAL_CHARGE_FRAME_COUNT = 5;
    private static final long SEASONAL_CHARGE_PERCENT_STEP_MS = 200L;
    private static final float STOCK_CHARGE_BASE_WIDTH = 360f;
    private static final float STOCK_CHARGE_BASE_HEIGHT = 640f;
    private static final float STOCK_CHARGE_ASSET_SCALE = 3f;
    private static final float STOCK_TALL_SCREEN_VERTICAL_BIAS = 0.24f;
    private static final long STOCK_CHARGE_MOVE_DURATION_MS = 3300L;
    private static final long STOCK_CHARGE_ROTATE_DURATION_MS = 20010L;
    private static final long SPRING_PARTICLE_DURATION_MS = 1600L;
    private static final long SPRING_PARTICLE_DELAY_MS = 500L;
    private static final int[] SPRING_PARTICLE_SPRITE_INDEX = {
            5, 5, 5, 5, 5, 5, 6, 6, 7, 7, 7, 7, 8
    };
    private static final int[] WINTER_PARTICLE_RANDOM_SLOT_TO_SPRITE_ID = {
            0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 2, 3
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final Bitmap[] springSprites;
    private final Bitmap[] summerSprites;
    private final Bitmap[] autumnSprites;
    private final Bitmap[] winterSprites;
    private final Random particleRandom = new Random();
    private final ParticleSpec[] particleSpecs = new ParticleSpec[SPRING_PARTICLE_SPRITE_INDEX.length];
    private final int[] winterParticleRandomSlots = new int[12];
    private long chargeStartedAt = SystemClock.uptimeMillis();
    private int particleSpecTheme = Integer.MIN_VALUE;
    private int seasonMode = SEASON_AUTO;
    private int positionOffsetX;
    private int positionOffsetY;
    private int doodleSizePercent = OverlayPrefs.DOODLE_SIZE_DEFAULT_PERCENT;
    private int batteryPercent;
    private boolean debugRollingCharge;
    private boolean particleSpecFull;
    private boolean winterParticleSlotsReady;
    private boolean warmParked;
    private long resumeFrameRequestedAt;

    public SeasonalDoodleView(Context context) {
        super(context);
        setWillNotDraw(false);
        springSprites = loadSprites(note4ChargeSpringNames());
        summerSprites = loadSprites(note4ChargeSummerNames());
        autumnSprites = loadSprites(note4ChargeAutumnNames());
        winterSprites = loadSprites(note4ChargeWinterNames());
    }

    void setSeasonMode(int nextSeasonMode) {
        if (seasonMode == nextSeasonMode) {
            return;
        }
        seasonMode = nextSeasonMode;
        resetChargeCycle();
    }

    void resetChargeCycle() {
        chargeStartedAt = SystemClock.uptimeMillis();
        invalidateParticleSpecs();
        invalidate();
    }

    void setPositionOffset(int offsetX, int offsetY) {
        int clampedX = OverlayPrefs.clampPositionOffset(offsetX);
        int clampedY = OverlayPrefs.clampPositionOffset(offsetY);
        if (positionOffsetX == clampedX && positionOffsetY == clampedY) {
            return;
        }
        positionOffsetX = clampedX;
        positionOffsetY = clampedY;
        invalidate();
    }

    void setDoodleSizePercent(int percent) {
        int clamped = OverlayPrefs.clampDoodleSizePercent(percent);
        if (doodleSizePercent == clamped) {
            return;
        }
        doodleSizePercent = clamped;
        invalidate();
    }

    void setBatteryPercent(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        if (batteryPercent == clamped) {
            return;
        }
        batteryPercent = clamped;
        invalidate();
    }

    void setDebugRollingCharge(boolean enabled) {
        if (debugRollingCharge == enabled) {
            return;
        }
        debugRollingCharge = enabled;
        if (enabled) {
            resetChargeCycle();
        } else {
            invalidate();
        }
    }

    void setWarmParked(boolean parked) {
        if (warmParked == parked) {
            return;
        }
        warmParked = parked;
        if (!parked) {
            resumeFrameRequestedAt = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        int theme = resolvedTheme();
        drawSeasonalChargingDoodle(canvas, now, theme);
        if (resumeFrameRequestedAt > 0L) {
            Log.i(TAG, "first frame after warm resume ms="
                    + Math.max(0L, now - resumeFrameRequestedAt));
            resumeFrameRequestedAt = 0L;
        }
        if (!warmParked) {
            postInvalidateDelayed(16L);
        }
    }

    private int resolvedTheme() {
        if (seasonMode >= SEASON_SPRING && seasonMode <= SEASON_WINTER) {
            return seasonMode;
        }
        int month = Calendar.getInstance().get(Calendar.MONTH);
        if (month >= Calendar.MARCH && month <= Calendar.MAY) {
            return SEASON_SPRING;
        }
        if (month >= Calendar.JUNE && month <= Calendar.AUGUST) {
            return SEASON_SUMMER;
        }
        if (month >= Calendar.SEPTEMBER && month <= Calendar.NOVEMBER) {
            return SEASON_AUTUMN;
        }
        return SEASON_WINTER;
    }

    private void drawSeasonalChargingDoodle(Canvas canvas, long now, int theme) {
        long elapsed = Math.max(0L, now - chargeStartedAt);
        int percent = debugRollingCharge
                ? (int) Math.min(100L, elapsed / SEASONAL_CHARGE_PERCENT_STEP_MS)
                : batteryPercent;
        Bitmap[] sprites = seasonalChargeSpritesForTheme(theme);
        float scale = stockChargeScale();
        float left = stockChargeLeft(scale);
        float top = stockChargeTop(scale);
        if (theme == SEASON_SPRING) {
            drawSamsungSpringCharge(canvas, elapsed, percent, scale, left, top, sprites);
            return;
        }
        drawSamsungSeasonCharge(canvas, theme, elapsed, percent, scale, left, top, sprites);
    }

    private void drawSamsungSpringCharge(Canvas canvas, long elapsed, int percent, float scale, float left, float top, Bitmap[] sprites) {
        if (sprites == null || sprites.length < SEASONAL_CHARGE_FRAME_COUNT) {
            return;
        }
        int frameIndex = seasonalChargeFrameIndex(percent);
        drawSamsungSpringParticles(canvas, elapsed, frameIndex, scale, left, top, sprites);

        float movePhase = reverseRepeatPhase(elapsed, STOCK_CHARGE_MOVE_DURATION_MS);
        float y = lerp(300f, 266f, accelerateDecelerate(movePhase));
        float rotation = 359f * accelerateDecelerate((elapsed % STOCK_CHARGE_ROTATE_DURATION_MS) / (float) STOCK_CHARGE_ROTATE_DURATION_MS);
        drawStockBitmapTopLeft(canvas, sprites[frameIndex], scale, left, top, 118f, y, 1f, rotation, 1f);
    }

    private void drawSamsungSpringParticles(Canvas canvas, long elapsed, int frameIndex, float scale, float left, float top, Bitmap[] sprites) {
        ensureParticleSpecs(SEASON_SPRING, frameIndex == 4);
        int count = frameIndex == 4 ? SPRING_PARTICLE_SPRITE_INDEX.length - 1 : SPRING_PARTICLE_SPRITE_INDEX.length;
        for (int i = 0; i < count; i++) {
            ParticleSpec spec = particleSpecs[i];
            if (spec == null || spec.spriteIndex >= sprites.length || sprites[spec.spriteIndex] == null) {
                continue;
            }
            long local = elapsed - i * SPRING_PARTICLE_DELAY_MS;
            if (local < 0L) {
                continue;
            }
            float raw = (local % SPRING_PARTICLE_DURATION_MS) / (float) SPRING_PARTICLE_DURATION_MS;
            float eased = accelerateDecelerate(raw);
            drawStockBitmapTopLeft(canvas, sprites[spec.spriteIndex], scale, left, top,
                    lerp(spec.startX, spec.endX, eased), lerp(spec.startY, spec.endY, eased),
                    1f - raw, 359f * eased, lerp(1f, spec.targetScale, eased));
        }
    }

    private void drawSamsungSeasonCharge(Canvas canvas, int theme, long elapsed, int percent, float scale, float left, float top, Bitmap[] sprites) {
        int frameIndex = seasonalChargeFrameIndex(percent);
        drawSamsungSeasonParticles(canvas, theme, elapsed, frameIndex, scale, left, top, sprites);

        float baseX = seasonChargeTopLeftX(theme);
        float baseFrameY = lerp(300f, 267f, accelerateDecelerate(reverseRepeatPhase(elapsed, STOCK_CHARGE_MOVE_DURATION_MS)));
        float frameY = lerp(300f, 266f, accelerateDecelerate(reverseRepeatPhase(elapsed, STOCK_CHARGE_MOVE_DURATION_MS)));
        if (theme == SEASON_AUTUMN && frameIndex == 4) {
            drawAutumnChargeCircles(canvas, elapsed, scale, left, top, sprites);
        }
        Bitmap base = theme == SEASON_WINTER && winterSprites != null && winterSprites.length > 0
                ? winterSprites[0]
                : null;
        if (base != null) {
            drawStockBitmapTopLeft(canvas, base, scale, left, top, baseX, baseFrameY, 1f,
                    seasonChargeRotation(theme, elapsed, true), 1f);
        }

        Bitmap frame = seasonalChargeFrameBitmap(theme, sprites, frameIndex);
        if (frame != null) {
            drawStockBitmapTopLeft(canvas, frame, scale, left, top, baseX, frameY, 1f,
                    seasonChargeRotation(theme, elapsed, false), 1f);
        }
    }

    private void drawSamsungSeasonParticles(Canvas canvas, int theme, long elapsed, int frameIndex, float scale, float left, float top, Bitmap[] sprites) {
        ensureParticleSpecs(theme, frameIndex == 4);
        int count = seasonParticleCount(theme, frameIndex);
        for (int i = 0; i < count; i++) {
            ParticleSpec spec = particleSpecs[i];
            if (spec == null || spec.spriteIndex < 0 || sprites == null || spec.spriteIndex >= sprites.length || sprites[spec.spriteIndex] == null) {
                continue;
            }
            long local = elapsed - i * SPRING_PARTICLE_DELAY_MS;
            if (local < 0L) {
                continue;
            }
            float raw = (local % SPRING_PARTICLE_DURATION_MS) / (float) SPRING_PARTICLE_DURATION_MS;
            float eased = accelerateDecelerate(raw);
            float rotation = spec.rotate
                    ? 359f * accelerateDecelerate((local % spec.rotateDuration) / (float) spec.rotateDuration)
                    : 0f;
            drawStockBitmapTopLeft(canvas, sprites[spec.spriteIndex], scale, left, top,
                    lerp(spec.startX, spec.endX, eased), lerp(spec.startY, spec.endY, eased),
                    1f - raw, rotation, lerp(1f, spec.targetScale, eased));
        }
    }

    private void drawAutumnChargeCircles(Canvas canvas, long elapsed, float scale, float left, float top, Bitmap[] sprites) {
        if (sprites == null || sprites.length <= SEASONAL_CHARGE_FRAME_COUNT || sprites[SEASONAL_CHARGE_FRAME_COUNT] == null) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            long local = elapsed - i * 1500L;
            if (local < 0L) {
                continue;
            }
            float raw = (local % 4000L) / 4000f;
            drawStockBitmapTopLeft(canvas, sprites[SEASONAL_CHARGE_FRAME_COUNT], scale, left, top,
                    100f, 264f, 1f - raw * raw, 0f, lerp(1f, 2.2f, accelerateDecelerate(raw)));
        }
    }

    private float seasonChargeTopLeftX(int theme) {
        if (theme == SEASON_AUTUMN) {
            return 114f;
        }
        if (theme == SEASON_WINTER) {
            return 121f;
        }
        return 118f;
    }

    private float seasonChargeRotation(int theme, long elapsed, boolean base) {
        if (theme == SEASON_AUTUMN) {
            long duration = base ? STOCK_CHARGE_ROTATE_DURATION_MS : 6670L;
            return lerp(-10f, 10f, accelerateDecelerate(reverseRepeatPhase(elapsed, duration)));
        }
        return 359f * accelerateDecelerate((elapsed % STOCK_CHARGE_ROTATE_DURATION_MS) / (float) STOCK_CHARGE_ROTATE_DURATION_MS);
    }

    private Bitmap seasonalChargeFrameBitmap(int theme, Bitmap[] sprites, int frameIndex) {
        if (sprites == null) {
            return null;
        }
        if (theme == SEASON_WINTER) {
            return frameIndex == 0 ? null : frameIndex < sprites.length ? sprites[frameIndex] : null;
        }
        return frameIndex < sprites.length ? sprites[frameIndex] : null;
    }

    private int seasonParticleCount(int theme, int frameIndex) {
        if (theme == SEASON_SUMMER) {
            return frameIndex == 4 ? 8 : 10;
        }
        if (theme == SEASON_AUTUMN) {
            return frameIndex == 4 ? 0 : 6;
        }
        if (theme == SEASON_WINTER) {
            return 12;
        }
        return 0;
    }

    private void invalidateParticleSpecs() {
        particleSpecTheme = Integer.MIN_VALUE;
        winterParticleSlotsReady = false;
    }

    private void ensureParticleSpecs(int theme, boolean full) {
        if (particleSpecTheme == theme && particleSpecFull == full) {
            return;
        }
        boolean themeChanged = particleSpecTheme != theme;
        for (int i = 0; i < particleSpecs.length; i++) {
            particleSpecs[i] = null;
        }
        if (themeChanged && theme == SEASON_WINTER) {
            winterParticleSlotsReady = false;
        }
        if (theme == SEASON_WINTER) {
            ensureWinterParticleSlots();
        }
        int count = theme == SEASON_SPRING
                ? full ? SPRING_PARTICLE_SPRITE_INDEX.length - 1 : SPRING_PARTICLE_SPRITE_INDEX.length
                : seasonParticleCount(theme, full ? 4 : 0);
        for (int i = 0; i < count; i++) {
            particleSpecs[i] = createParticleSpec(theme, i, full);
        }
        particleSpecTheme = theme;
        particleSpecFull = full;
    }

    private void ensureWinterParticleSlots() {
        if (winterParticleSlotsReady) {
            return;
        }
        for (int i = 0; i < winterParticleRandomSlots.length; i++) {
            winterParticleRandomSlots[i] = particleRandom.nextInt(WINTER_PARTICLE_RANDOM_SLOT_TO_SPRITE_ID.length);
        }
        winterParticleSlotsReady = true;
    }

    private ParticleSpec createParticleSpec(int theme, int index, boolean full) {
        ParticleSpec spec = new ParticleSpec();
        spec.spriteIndex = particleSpriteIndex(theme, index);
        spec.rotate = theme != SEASON_WINTER || winterParticleRandomSlots[index] < 11;
        spec.rotateDuration = theme == SEASON_WINTER ? 1000L : SPRING_PARTICLE_DURATION_MS;
        spec.targetScale = particleTargetScale(theme, index);
        if (full && theme != SEASON_AUTUMN) {
            spec.startX = 170f;
            spec.startY = 350f;
            if (theme == SEASON_SUMMER) {
                float angleX = (90f - particleRandom.nextInt(180)) * (float) Math.PI / 180f;
                float angleY = (90f - particleRandom.nextInt(180)) * (float) Math.PI / 180f;
                spec.endX = 170f + 113f * (float) Math.sin(angleX);
                spec.endY = 350f - 113f * (float) Math.cos(angleY);
            } else {
                float angleX = particleRandom.nextInt(359) * (float) Math.PI / 180f;
                float angleY = particleRandom.nextInt(359) * (float) Math.PI / 180f;
                spec.endX = 170f + 113f * (float) Math.sin(angleX);
                spec.endY = 350f - 113f * (float) Math.cos(angleY);
            }
        } else if (theme == SEASON_SPRING) {
            spec.startX = randomStockParticleX();
            spec.startY = 888f;
            spec.endX = 170f;
            spec.endY = 334f;
        } else {
            spec.startX = 170f;
            spec.startY = 888f;
            spec.endX = randomStockParticleX();
            spec.endY = 334f;
        }
        return spec;
    }

    private float randomStockParticleX() {
        return 108f + 144f * particleRandom.nextFloat();
    }

    private int particleSpriteIndex(int theme, int index) {
        if (theme == SEASON_SPRING) {
            return SPRING_PARTICLE_SPRITE_INDEX[index];
        }
        if (theme == SEASON_SUMMER) {
            return index < 8 ? 5 : index == 8 ? 6 : 7;
        }
        if (theme == SEASON_AUTUMN) {
            return index < 3 ? 6 : index == 3 ? 7 : index == 4 ? 8 : 9;
        }
        if (theme == SEASON_WINTER) {
            return 5 + WINTER_PARTICLE_RANDOM_SLOT_TO_SPRITE_ID[winterParticleRandomSlots[index]];
        }
        return -1;
    }

    private float particleTargetScale(int theme, int index) {
        float random = particleRandom.nextFloat();
        if (theme == SEASON_SUMMER) {
            return index < 8 ? 0.5f + 0.5f * random : index == 8 ? 0.8f + 0.2f * random : 1f + 0.2f * random;
        }
        if (theme == SEASON_AUTUMN) {
            return index < 3 ? 0.6f + 0.4f * random : 0.8f + 0.2f * random;
        }
        if (theme == SEASON_WINTER) {
            int slot = winterParticleRandomSlots[index];
            if (slot < 3) {
                return 0.6f + 0.8f * random;
            }
            if (slot < 6) {
                return 0.5f + 0.7f * random;
            }
            if (slot < 11) {
                return 1f + 1.6f * random;
            }
            return 1f;
        }
        if (index < 6) {
            return 0.5f + 0.3f * random;
        }
        if (index < 8) {
            return 0.7f + 0.3f * random;
        }
        if (index < 12) {
            return 0.4f + 0.2f * random;
        }
        return 0.5f + 0.7f * random;
    }

    private int seasonalChargeFrameIndex(int percent) {
        if (percent <= 25) {
            return 0;
        }
        if (percent <= 50) {
            return 1;
        }
        if (percent <= 75) {
            return 2;
        }
        if (percent <= 99) {
            return 3;
        }
        return 4;
    }

    private float stockChargeScale() {
        float scale = getWidth() / STOCK_CHARGE_BASE_WIDTH;
        if (STOCK_CHARGE_BASE_HEIGHT * scale > getHeight()) {
            scale = getHeight() / STOCK_CHARGE_BASE_HEIGHT;
        }
        return Math.max(0.1f, scale);
    }

    private float stockChargeLeft(float scale) {
        return (getWidth() - STOCK_CHARGE_BASE_WIDTH * scale) * 0.5f + positionOffsetX;
    }

    private float stockChargeTop(float scale) {
        float contentHeight = STOCK_CHARGE_BASE_HEIGHT * scale;
        float spareHeight = getHeight() - contentHeight;
        float baseTop = spareHeight >= 0f ? spareHeight * STOCK_TALL_SCREEN_VERTICAL_BIAS : spareHeight * 0.5f;
        return baseTop + positionOffsetY;
    }

    private float stockX(float x, float scale, float left) {
        return left + x * scale;
    }

    private float stockY(float y, float scale, float top) {
        return top + y * scale;
    }

    private float accelerateDecelerate(float input) {
        return (float) (Math.cos((input + 1f) * Math.PI) * 0.5f + 0.5f);
    }

    private float reverseRepeatPhase(long elapsed, long duration) {
        long cycle = duration * 2L;
        long local = elapsed % cycle;
        float phase = local / (float) duration;
        return phase <= 1f ? phase : 2f - phase;
    }

    private float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private void drawStockBitmapTopLeft(Canvas canvas, Bitmap bitmap, float scale, float left, float top,
                                        float baseX, float baseY, float alpha, float rotation, float objectScale) {
        if (bitmap == null || alpha <= 0f || objectScale <= 0f) {
            return;
        }
        float sizeScale = doodleSizePercent / 100f;
        float baseWidth = bitmap.getWidth() / STOCK_CHARGE_ASSET_SCALE * sizeScale;
        float baseHeight = bitmap.getHeight() / STOCK_CHARGE_ASSET_SCALE * sizeScale;
        float centerX = stockX(baseX + baseWidth * 0.5f, scale, left);
        float centerY = stockY(baseY + baseHeight * 0.5f, scale, top);
        float renderScale = scale * objectScale / STOCK_CHARGE_ASSET_SCALE * sizeScale;
        matrix.reset();
        matrix.postTranslate(-bitmap.getWidth() * 0.5f, -bitmap.getHeight() * 0.5f);
        matrix.postScale(renderScale, renderScale);
        matrix.postRotate(rotation);
        matrix.postTranslate(centerX, centerY);
        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        canvas.drawBitmap(bitmap, matrix, paint);
        paint.setAlpha(255);
    }

    private Bitmap[] seasonalChargeSpritesForTheme(int theme) {
        switch (theme) {
            case SEASON_SPRING:
                return springSprites;
            case SEASON_SUMMER:
                return summerSprites;
            case SEASON_AUTUMN:
                return autumnSprites;
            case SEASON_WINTER:
            default:
                return winterSprites;
        }
    }

    private static final class ParticleSpec {
        int spriteIndex;
        float startX;
        float startY;
        float endX;
        float endY;
        float targetScale;
        boolean rotate;
        long rotateDuration;
    }

    private Bitmap[] loadSprites(String[] names) {
        Bitmap[] sprites = new Bitmap[names.length];
        for (int i = 0; i < names.length; i++) {
            sprites[i] = loadBitmap(names[i]);
        }
        return sprites;
    }

    private Bitmap loadBitmap(String name) {
        Bitmap bitmap = loadBitmapExact(name);
        if (bitmap != null) {
            return bitmap;
        }
        return loadBitmapExact(name.replace('/', '\\'));
    }

    private Bitmap loadBitmapExact(String name) {
        try {
            InputStream input = getContext().getAssets().open(name);
            try {
                return BitmapFactory.decodeStream(input);
            } finally {
                input.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String[] note4ChargeSpringNames() {
        return concat(new String[] {
                        "note4seasonal/charging/spring_charging_25p.png",
                        "note4seasonal/charging/spring_charging_50p.png",
                        "note4seasonal/charging/spring_charging_75p.png",
                        "note4seasonal/charging/spring_charging_99p.png",
                        "note4seasonal/charging/spring_charging_100p.png"
                },
                numberedAssetNames("note4seasonal/charging/spring_particle_", 1, 4, 2, ".png"));
    }

    private static String[] note4ChargeSummerNames() {
        return concat(new String[] {
                        "note4seasonal/charging/summer_charging_01.png",
                        "note4seasonal/charging/summer_charging_02.png",
                        "note4seasonal/charging/summer_charging_03.png",
                        "note4seasonal/charging/summer_charging_04.png",
                        "note4seasonal/charging/summer_charging_05.png"
                },
                numberedAssetNames("note4seasonal/charging/summer_particle_", 1, 3, 2, ".png"));
    }

    private static String[] note4ChargeAutumnNames() {
        return concat(new String[] {
                        "note4seasonal/charging/autumn_charging_01.png",
                        "note4seasonal/charging/autumn_charging_02.png",
                        "note4seasonal/charging/autumn_charging_03.png",
                        "note4seasonal/charging/autumn_charging_04.png",
                        "note4seasonal/charging/autumn_charging_05.png",
                        "note4seasonal/charging/autumn_charging_circle.png"
                },
                numberedAssetNames("note4seasonal/charging/autumn_particle_", 1, 4, 2, ".png"));
    }

    private static String[] note4ChargeWinterNames() {
        return concat(new String[] {
                        "note4seasonal/charging/winter_charging_base.png",
                        "note4seasonal/charging/winter_charging_25p.png",
                        "note4seasonal/charging/winter_charging_50p.png",
                        "note4seasonal/charging/winter_charging_75p.png",
                        "note4seasonal/charging/winter_charging_100p.png"
                },
                numberedAssetNames("note4seasonal/charging/winter_particle_", 1, 4, 2, ".png"));
    }

    private static String[] numberedAssetNames(String prefix, int first, int last, int digits, String extension) {
        String[] names = new String[last - first + 1];
        for (int i = 0; i < names.length; i++) {
            names[i] = prefix + zeroPad(first + i, digits) + extension;
        }
        return names;
    }

    private static String[] concat(String[]... groups) {
        int count = 0;
        for (int i = 0; i < groups.length; i++) {
            count += groups[i].length;
        }
        String[] result = new String[count];
        int index = 0;
        for (int i = 0; i < groups.length; i++) {
            String[] group = groups[i];
            for (int j = 0; j < group.length; j++) {
                result[index++] = group[j];
            }
        }
        return result;
    }

    private static String zeroPad(int value, int digits) {
        String result = String.valueOf(value);
        while (result.length() < digits) {
            result = "0" + result;
        }
        return result;
    }
}

