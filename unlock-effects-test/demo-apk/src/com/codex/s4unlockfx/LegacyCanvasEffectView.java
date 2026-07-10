package com.codex.s4unlockfx;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class LegacyCanvasEffectView extends View {
    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_S3_MASS_RIPPLE = 1;
    public static final int EFFECT_NOTE5_FESTIVAL_TOUCH = 2;
    public static final int EFFECT_NOTE5_FESTIVAL_UNLOCK = 3;
    public static final int EFFECT_NOTE5_WATER_DROPLET = 4;
    public static final int EFFECT_NOTE5_SPARKLING_BUBBLES = 5;
    public static final int EFFECT_DEBUG_TOUCH = 6;
    public static final int EFFECT_NOTE4_SEASONAL_TOUCH = 7;
    public static final int EFFECT_NOTE4_SEASONAL_UNLOCK = 8;
    public static final int EFFECT_NOTE4_CHARGER_CABLE = 9;
    public static final int EFFECT_NOTE4_CHARGER_WIRELESS = 10;
    public static final int EFFECT_NOTE4_SEASONAL_CHARGING = 11;
    public static final int EFFECT_NOTE4_COLORED_PAPER = 12;
    public static final int EFFECT_NOTE4_DREAMY_FESTIVAL = 13;
    public static final int EFFECT_NOTE4_CHARGE_SPRING = 14;
    public static final int EFFECT_NOTE4_CHARGE_SUMMER = 15;
    public static final int EFFECT_NOTE4_CHARGE_AUTUMN = 16;
    public static final int EFFECT_NOTE4_CHARGE_WINTER = 17;
    public static final int EFFECT_WATERCOLOR = 18;
    public static final int EFFECT_RIPPLE_INK = 19;
    public static final int EFFECT_INDIGO_RIPPLE = 20;
    public static final int EFFECT_ABSTRACT_TILES = 21;
    public static final int EFFECT_GEOMETRIC_MOSAIC = 22;
    public static final int EFFECT_BRILLIANT_CUT = 23;
    public static final int EFFECT_BRILLIANT_RING = 24;
    private static final int MAX_RIPPLES = 12;
    private static final int MAX_PARTICLES = 120;
    private static final int SEASONAL_CHARGE_FRAME_COUNT = 5;
    private static final long SEASONAL_CHARGE_PERCENT_STEP_MS = 200L;
    private static final long SEASONAL_CHARGE_CYCLE_MS = 101L * SEASONAL_CHARGE_PERCENT_STEP_MS;
    private static final float STOCK_CHARGE_BASE_WIDTH = 360f;
    private static final float STOCK_CHARGE_BASE_HEIGHT = 640f;
    private static final float STOCK_CHARGE_ASSET_SCALE = 4f;
    private static final long STOCK_CHARGE_MOVE_DURATION_MS = 3300L;
    private static final long STOCK_CHARGE_ROTATE_DURATION_MS = 20010L;
    private static final long SPRING_PARTICLE_DURATION_MS = 1600L;
    private static final long SPRING_PARTICLE_DELAY_MS = 500L;
    private static final int[] SPRING_PARTICLE_SPRITE_INDEX = {
            5, 5, 5, 5, 5, 5, 6, 6, 7, 7, 7, 7, 8
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final Random random = new Random(20130504L);
    private final List<Ripple> ripples = new ArrayList<Ripple>();
    private final List<Particle> particles = new ArrayList<Particle>();
    private final Bitmap[] touchSprites;
    private final Bitmap[] unlockSprites;
    private final Bitmap[] seasonalTouchSprites;
    private final Bitmap[] seasonalUnlockSprites;
    private final Bitmap[] seasonalChargeFlowerSprites;
    private final Bitmap[] seasonalChargeSummerSprites;
    private final Bitmap[] seasonalChargeLeafSprites;
    private final Bitmap[] seasonalChargeWinterSprites;
    private final Bitmap[] watercolorMasks;
    private final Bitmap coloredPaperBirthdayBackground;
    private final Bitmap coloredPaperCake;
    private final Bitmap[] coloredPaperCandies;
    private final Bitmap[] coloredPaperFlames;
    private final StockFestivalScene coloredPaperBirthdayScene;
    private final Bitmap[] dreamyWaves;
    private final Bitmap[] dreamyFireworkA;
    private final Bitmap[] dreamyFireworkB;
    private final Bitmap[] dreamyFireworkC;
    private final Bitmap[] dreamyLanterns;
    private final Bitmap[] dreamySnow;
    private final Bitmap[] dreamyPetals;
    private final String[] chargerCableFrameNames;
    private final String[] chargerWirelessFrameNames;
    private final Bitmap[] chargerCableFrames;
    private final Bitmap[] chargerWirelessFrames;
    private final boolean[] chargerCableFrameTried;
    private final boolean[] chargerWirelessFrameTried;
    private int effectType = EFFECT_NONE;
    private float downX;
    private float downY;
    private float lastRippleX;
    private float lastRippleY;
    private long lastRippleAt;
    private long chargerStartedAt;

    public LegacyCanvasEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        touchSprites = loadSprites(new String[] {
                "festival_unlock_touch_01.pio",
                "festival_unlock_touch_02.qmg",
                "spring_particle_01.qmg",
                "spring_particle_02.qmg",
                "summer_particle_01.qmg",
                "autumn_particle_01.qmg"
        });
        unlockSprites = loadSprites(new String[] {
                "festival_unlock_effect_01.pio",
                "festival_unlock_effect_02.pio",
                "festival_unlock_effect_03.pio",
                "unlock_spring_particle_01.qio",
                "unlock_summer_particle_01.qmg",
                "winter_particle_01.pio",
                "winter_particle_04.pio"
        });
        seasonalTouchSprites = loadSprites(new String[] {
                "festival_unlock_touch_01.pio",
                "festival_unlock_touch_02.qmg",
                "spring_particle_01.qmg",
                "spring_particle_02.qmg",
                "spring_particle_03.qio",
                "spring_particle_04.qmg",
                "summer_particle_01.qmg",
                "summer_particle_02.qio",
                "summer_particle_03.pio",
                "autumn_particle_01.qmg",
                "autumn_particle_02.qmg",
                "autumn_particle_03.qio",
                "autumn_particle_04.qmg",
                "winter_particle_01.pio",
                "winter_particle_02.pio",
                "winter_particle_03.pio",
                "winter_particle_04.pio",
                "unlock_spring_touch_01.qmg",
                "unlock_spring_touch_02.qio",
                "unlock_summer_touch_01.qmg",
                "unlock_summer_touch_02.pio",
                "unlock_summer_touch_03.pio"
        });
        seasonalUnlockSprites = loadSprites(new String[] {
                "festival_unlock_effect_01.pio",
                "festival_unlock_effect_02.pio",
                "festival_unlock_effect_03.pio",
                "unlock_spring_particle_01.qio",
                "unlock_spring_particle_02.qmg",
                "unlock_spring_particle_03.qmg",
                "unlock_spring_particle_04.qmg",
                "unlock_summer_particle_01.qmg",
                "unlock_summer_particle_02.qmg",
                "unlock_summer_particle_03.qmg",
                "unlock_summer_particle_04.qmg",
                "unlock_summer_particle_05.qmg",
                "unlock_summer_particle_06.qmg",
                "unlock_autumn_particle_01.qmg",
                "unlock_autumn_particle_02.qmg",
                "unlock_autumn_particle_03.qmg",
                "unlock_autumn_particle_04.qmg",
                "unlock_autumn_particle_05.qio",
                "winter_particle_01.pio",
                "winter_particle_02.pio",
                "winter_particle_03.pio",
                "winter_particle_04.pio"
        });
        seasonalChargeFlowerSprites = loadSprites(note4ChargeSpringNames());
        seasonalChargeSummerSprites = loadSprites(note4ChargeSummerNames());
        seasonalChargeLeafSprites = loadSprites(note4ChargeAutumnNames());
        seasonalChargeWinterSprites = loadSprites(note4ChargeWinterNames());
        watercolorMasks = loadSprites(new String[] {
                "watercolor_mask1.png",
                "watercolor_mask2.png",
                "watercolor_mask3.png"
        });
        coloredPaperBirthdayBackground = loadBitmap("note4festival/full/ColoredPaper/res/drawable-xxxhdpi-v4/lockscreen_birthday_bg.png");
        coloredPaperCake = loadBitmap("note4festival/full/ColoredPaper/res/drawable-xxxhdpi-v4/lockscreen_birthday_cake.qmg");
        coloredPaperCandies = loadSprites(coloredPaperCandyNames());
        coloredPaperFlames = loadSprites(numberedAssetNames("note4festival/full/ColoredPaper/res/drawable-xxxhdpi-v4/lockscreen_birthday_flame_", 0, 30, 4, ".qmg"));
        coloredPaperBirthdayScene = loadStockFestivalScene(
                "note4festival/apks/ColoredPaper.apk",
                "com.bst.festivalrespreload1",
                "birthday",
                "note4festival/full/ColoredPaper/res/drawable-xxxhdpi-v4");
        dreamyWaves = loadSprites(numberedAssetNames("note4festival/full/Dreamy/res/drawable-xxxhdpi-v4/festival_wave", 1, 6, 2, ".png"));
        dreamyFireworkA = loadSprites(fireworkNames("a"));
        dreamyFireworkB = loadSprites(fireworkNames("b"));
        dreamyFireworkC = loadSprites(fireworkNames("c"));
        dreamyLanterns = loadSprites(dreamyLanternNames());
        dreamySnow = loadSprites(numberedAssetNames("note4festival/full/Dreamy/res/drawable-xxxhdpi-v4/christmas_snow_", 1, 19, 2, ".qmg"));
        dreamyPetals = loadSprites(yearPetalNames());
        chargerCableFrameNames = chargerFrameNames("charger_anim_cable_", false);
        chargerWirelessFrameNames = chargerFrameNames("charger_anim_wireless_", true);
        chargerCableFrames = new Bitmap[chargerCableFrameNames.length];
        chargerWirelessFrames = new Bitmap[chargerWirelessFrameNames.length];
        chargerCableFrameTried = new boolean[chargerCableFrameNames.length];
        chargerWirelessFrameTried = new boolean[chargerWirelessFrameNames.length];
    }

    public void setEffectType(int nextEffectType) {
        effectType = nextEffectType;
        ripples.clear();
        particles.clear();
        lastRippleAt = 0L;
        chargerStartedAt = SystemClock.uptimeMillis();
        setVisibility(effectType == EFFECT_NONE ? GONE : VISIBLE);
        invalidate();
    }

    public int getEffectType() {
        return effectType;
    }

    public boolean hasActiveAnimations() {
        return isContinuousEffect() || !ripples.isEmpty() || !particles.isEmpty();
    }

    public boolean onHostTouchEvent(MotionEvent event) {
        if (effectType == EFFECT_NONE) {
            return false;
        }
        if (isContinuousEffect()) {
            invalidate();
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            lastRippleX = x;
            lastRippleY = y;
            lastRippleAt = SystemClock.uptimeMillis();
            if (isRippleDrivenEffect()) {
                addRipple(x, y, false);
            } else if (effectType == EFFECT_NOTE5_SPARKLING_BUBBLES) {
                addBubbles(x, y, 18, false);
            } else if (isTileDrivenEffect()) {
                addTileBurst(x, y, 18, false);
            } else {
                addFestivalTouch(x, y, 18, false);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (isRippleDrivenEffect()) {
                maybeAddMovingRipple(x, y);
            } else if (effectType == EFFECT_NOTE5_SPARKLING_BUBBLES) {
                addBubbles(x, y, 2, false);
            } else if (isTileDrivenEffect()) {
                addTileBurst(x, y, 2, false);
            } else {
                addFestivalTouch(x, y, 3, false);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (isRippleDrivenEffect()) {
                addRipple(x, y, true);
            } else if (effectType == EFFECT_NOTE5_SPARKLING_BUBBLES) {
                addBubbles(x, y, 44, true);
            } else if (isTileDrivenEffect()) {
                addTileBurst(x, y, 44, true);
            } else if (effectType == EFFECT_NOTE5_FESTIVAL_UNLOCK || effectType == EFFECT_NOTE4_SEASONAL_UNLOCK) {
                addFestivalUnlock(x, y);
            } else {
                addFestivalTouch(x, y, 28, true);
            }
            return true;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        if (effectType == EFFECT_S3_MASS_RIPPLE) {
            drawMassRipple(canvas, now);
        } else if (effectType == EFFECT_DEBUG_TOUCH) {
            drawDebugTouch(canvas, now);
        } else if (effectType == EFFECT_NOTE5_WATER_DROPLET) {
            drawWaterDroplet(canvas, now);
        } else if (effectType == EFFECT_NOTE5_SPARKLING_BUBBLES) {
            drawSparklingBubbles(canvas, now);
        } else if (effectType == EFFECT_WATERCOLOR) {
            drawWatercolorFallback(canvas, now);
        } else if (effectType == EFFECT_RIPPLE_INK) {
            drawInkRippleFallback(canvas, now);
        } else if (effectType == EFFECT_INDIGO_RIPPLE) {
            drawIndigoRippleFallback(canvas, now);
        } else if (effectType == EFFECT_ABSTRACT_TILES
                || effectType == EFFECT_GEOMETRIC_MOSAIC
                || effectType == EFFECT_BRILLIANT_CUT) {
            drawTileFallback(canvas, now);
        } else if (effectType == EFFECT_BRILLIANT_RING) {
            drawBrilliantRingFallback(canvas, now);
        } else if (effectType == EFFECT_NOTE5_FESTIVAL_TOUCH
                || effectType == EFFECT_NOTE5_FESTIVAL_UNLOCK
                || effectType == EFFECT_NOTE4_SEASONAL_TOUCH
                || effectType == EFFECT_NOTE4_SEASONAL_UNLOCK) {
            drawFestival(canvas, now);
        } else if (effectType == EFFECT_NOTE4_CHARGER_CABLE) {
            drawChargerAnimation(canvas, now, chargerCableFrameNames, chargerCableFrames, chargerCableFrameTried);
        } else if (effectType == EFFECT_NOTE4_CHARGER_WIRELESS) {
            drawChargerAnimation(canvas, now, chargerWirelessFrameNames, chargerWirelessFrames, chargerWirelessFrameTried);
        } else if (effectType == EFFECT_NOTE4_SEASONAL_CHARGING) {
            drawSeasonalChargingIndicator(canvas, now);
        } else if (effectType == EFFECT_NOTE4_CHARGE_SPRING) {
            drawSeasonalChargingDoodle(canvas, now, 0);
        } else if (effectType == EFFECT_NOTE4_CHARGE_SUMMER) {
            drawSeasonalChargingDoodle(canvas, now, 1);
        } else if (effectType == EFFECT_NOTE4_CHARGE_AUTUMN) {
            drawSeasonalChargingDoodle(canvas, now, 2);
        } else if (effectType == EFFECT_NOTE4_CHARGE_WINTER) {
            drawSeasonalChargingDoodle(canvas, now, 3);
        } else if (effectType == EFFECT_NOTE4_COLORED_PAPER) {
            drawColoredPaperScene(canvas, now);
            drawFestival(canvas, now);
        } else if (effectType == EFFECT_NOTE4_DREAMY_FESTIVAL) {
            drawDreamyFestivalScene(canvas, now);
            drawFestival(canvas, now);
        }
        if (isAutoAnimatedEffect() || !ripples.isEmpty() || !particles.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }

    private void maybeAddMovingRipple(float x, float y) {
        long now = SystemClock.uptimeMillis();
        float dx = x - lastRippleX;
        float dy = y - lastRippleY;
        if (dx * dx + dy * dy < dp(28) * dp(28) && now - lastRippleAt < 90L) {
            return;
        }
        lastRippleX = x;
        lastRippleY = y;
        lastRippleAt = now;
        addRipple(x, y, false);
    }

    private void addRipple(float x, float y, boolean unlock) {
        long now = SystemClock.uptimeMillis();
        ripples.add(new Ripple(x, y, now, unlock ? 1650f : 1250f, unlock));
        if (unlock) {
            float centerX = (x + downX) * 0.5f;
            float centerY = (y + downY) * 0.5f;
            ripples.add(new Ripple(centerX, centerY, now + 110L, 1750f, true));
            ripples.add(new Ripple(centerX, centerY, now + 220L, 1850f, true));
        }
        trimRipples();
        invalidate();
    }

    private void drawMassRipple(Canvas canvas, long now) {
        Iterator<Ripple> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            float t = (now - ripple.startedAt) / ripple.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t) * (1f - t);
            float baseRadius = ripple.unlock ? Math.max(getWidth(), getHeight()) * 0.54f : dp(185);
            float rx = dp(16) + baseRadius * ease;
            float ry = dp(12) + baseRadius * 0.58f * ease;
            int alpha = (int) ((ripple.unlock ? 235 : 205) * (1f - t));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 4; i++) {
                float offset = dp(14 + i * 18) * (1f + t * 0.6f);
                paint.setStrokeWidth(dp(ripple.unlock ? 4.2f + i * 0.5f : 3.2f + i * 0.35f));
                paint.setColor(Color.argb(Math.max(0, alpha / 2 - i * 18), 4, 12, 24));
                RectF shadowOval = new RectF(
                        ripple.x - rx - offset,
                        ripple.y - ry - offset * 0.55f,
                        ripple.x + rx + offset,
                        ripple.y + ry + offset * 0.55f);
                canvas.drawOval(shadowOval, paint);
                paint.setStrokeWidth(dp(ripple.unlock ? 2.3f + i * 0.35f : 1.7f + i * 0.25f));
                paint.setColor(Color.argb(Math.max(0, alpha - i * 28), 235, 248, 255));
                RectF oval = new RectF(
                        ripple.x - rx - offset,
                        ripple.y - ry - offset * 0.55f,
                        ripple.x + rx + offset,
                        ripple.y + ry + offset * 0.55f);
                canvas.drawOval(oval, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(
                    ripple.x,
                    ripple.y,
                    Math.max(rx, ry),
                    Color.argb((int) (42 * (1f - t)), 210, 235, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(ripple.x, ripple.y, Math.max(rx, ry), paint);
            paint.setShader(null);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDebugTouch(Canvas canvas, long now) {
        Iterator<Ripple> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            float t = (now - ripple.startedAt) / ripple.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t);
            float radius = dp(ripple.unlock ? 42f : 24f) + dp(ripple.unlock ? 170f : 88f) * ease;
            int alpha = (int) ((ripple.unlock ? 220 : 180) * (1f - t));
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(
                    ripple.x,
                    ripple.y,
                    radius,
                    Color.argb(alpha / 3, 120, 230, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(ripple.x, ripple.y, radius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(ripple.unlock ? 3.4f : 2.4f));
            paint.setColor(Color.argb(alpha, 120, 230, 255));
            canvas.drawCircle(ripple.x, ripple.y, radius, paint);
            paint.setStrokeWidth(dp(1.8f));
            paint.setColor(Color.argb((int) (alpha * 0.74f), 255, 255, 255));
            canvas.drawCircle(ripple.x, ripple.y, radius * 0.58f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawWaterDroplet(Canvas canvas, long now) {
        Iterator<Ripple> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            float t = (now - ripple.startedAt) / ripple.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t);
            float radius = dp(ripple.unlock ? 30f : 18f) + Math.max(getWidth(), getHeight()) * (ripple.unlock ? 0.46f : 0.18f) * ease;
            int alpha = (int) ((ripple.unlock ? 150 : 115) * (1f - t));
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(
                    ripple.x,
                    ripple.y,
                    radius,
                    Color.argb(alpha / 2, 235, 252, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(ripple.x, ripple.y, radius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(ripple.unlock ? 2.2f : 1.6f));
            paint.setColor(Color.argb(alpha, 222, 248, 255));
            canvas.drawCircle(ripple.x, ripple.y, radius * 0.72f, paint);
            paint.setStrokeWidth(dp(1.1f));
            paint.setColor(Color.argb(alpha / 2, 45, 120, 180));
            canvas.drawCircle(ripple.x + radius * 0.06f, ripple.y + radius * 0.05f, radius * 0.48f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawWatercolorFallback(Canvas canvas, long now) {
        Iterator<Ripple> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            float t = (now - ripple.startedAt) / ripple.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t);
            float radius = dp(ripple.unlock ? 48f : 30f) + Math.max(getWidth(), getHeight()) * (ripple.unlock ? 0.58f : 0.26f) * ease;
            int alpha = (int) ((ripple.unlock ? 190 : 145) * (1f - t));
            int base = watercolorColor(ripple, 0);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(
                    ripple.x,
                    ripple.y,
                    radius,
                    withAlpha(Color.WHITE, alpha / 2),
                    withAlpha(base, alpha),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(ripple.x, ripple.y, radius, paint);
            paint.setShader(null);
            for (int i = 0; i < 3; i++) {
                float angle = ripple.x * 0.017f + ripple.y * 0.011f + i * 2.1f;
                float x = ripple.x + (float) Math.cos(angle + t * 2.6f) * radius * 0.24f;
                float y = ripple.y + (float) Math.sin(angle + t * 2.1f) * radius * 0.18f;
                float size = radius * (0.56f + i * 0.17f);
                Bitmap mask = spriteAt(watercolorMasks, i);
                if (mask != null) {
                    paint.setAlpha(Math.max(0, Math.min(255, alpha - i * 24)));
                    drawBitmapCentered(canvas, mask, x, y, size, (alpha - i * 24) / 255f, t * 80f + i * 38f);
                    paint.setAlpha(255);
                } else {
                    paint.setColor(withAlpha(watercolorColor(ripple, i + 1), alpha - i * 24));
                    canvas.drawCircle(x, y, size * 0.35f, paint);
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawInkRippleFallback(Canvas canvas, long now) {
        Iterator<Ripple> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            float t = (now - ripple.startedAt) / ripple.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t);
            float radius = dp(ripple.unlock ? 38f : 22f) + Math.max(getWidth(), getHeight()) * (ripple.unlock ? 0.54f : 0.22f) * ease;
            int alpha = (int) ((ripple.unlock ? 205 : 165) * (1f - t));
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(
                    ripple.x,
                    ripple.y,
                    radius,
                    Color.argb(alpha / 2, 25, 180, 210),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(ripple.x, ripple.y, radius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 5; i++) {
                paint.setStrokeWidth(dp(1.4f + i * 0.36f));
                paint.setColor(Color.argb(Math.max(0, alpha - i * 28), 20 + i * 18, 145 + i * 10, 190 + i * 8));
                float wobble = (float) Math.sin(t * Math.PI * 2f + i) * dp(8f);
                canvas.drawCircle(ripple.x, ripple.y, radius * (0.38f + i * 0.14f) + wobble, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawIndigoRippleFallback(Canvas canvas, long now) {
        Iterator<Ripple> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            float t = (now - ripple.startedAt) / ripple.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t);
            float radius = dp(ripple.unlock ? 50f : 28f) + Math.max(getWidth(), getHeight()) * (ripple.unlock ? 0.62f : 0.24f) * ease;
            int alpha = (int) ((ripple.unlock ? 185 : 140) * (1f - t));
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(
                    ripple.x,
                    ripple.y,
                    radius,
                    Color.argb(alpha, 120, 190, 255),
                    Color.argb(0, 50, 45, 160),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(ripple.x, ripple.y, radius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.2f));
            paint.setColor(Color.argb(alpha, 180, 220, 255));
            canvas.drawCircle(ripple.x, ripple.y, radius * 0.62f, paint);
            paint.setStrokeWidth(dp(1.3f));
            paint.setColor(Color.argb(alpha / 2, 80, 115, 255));
            canvas.drawCircle(ripple.x, ripple.y, radius * 0.34f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBrilliantRingFallback(Canvas canvas, long now) {
        Iterator<Ripple> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            float t = (now - ripple.startedAt) / ripple.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t);
            float radius = dp(ripple.unlock ? 34f : 20f) + Math.max(getWidth(), getHeight()) * (ripple.unlock ? 0.5f : 0.2f) * ease;
            int alpha = (int) ((ripple.unlock ? 230 : 175) * (1f - t));
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 3; i++) {
                paint.setStrokeWidth(dp(2.8f - i * 0.5f));
                paint.setColor(Color.argb(Math.max(0, alpha - i * 42), 245, 248, 255));
                canvas.drawCircle(ripple.x, ripple.y, radius * (0.72f + i * 0.2f), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 12; i++) {
                float angle = (float) (i * Math.PI * 2f / 12f + t * Math.PI * 1.5f);
                float x = ripple.x + (float) Math.cos(angle) * radius;
                float y = ripple.y + (float) Math.sin(angle) * radius;
                drawDiamond(canvas, x, y, dp(7f + i % 3), Color.argb(alpha, 240, 250, 255), (float) Math.toDegrees(angle));
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void addTileBurst(float x, float y, int count, boolean unlock) {
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * 360f;
            float speed = dp(unlock ? 150f + random.nextFloat() * 360f : 50f + random.nextFloat() * 160f);
            float vx = (float) Math.cos(Math.toRadians(angle)) * speed;
            float vy = (float) Math.sin(Math.toRadians(angle)) * speed - dp(unlock ? 80f : 20f);
            int color = tileColor(i);
            particles.add(new Particle(x, y, vx, vy, now + i * 6L, unlock ? 1280f + random.nextFloat() * 520f : 880f, color, null, unlock));
        }
        trimParticles();
        invalidate();
    }

    private void drawTileFallback(Canvas canvas, long now) {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            float t = (now - particle.startedAt) / particle.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t);
            float x = particle.x + particle.vx * t;
            float y = particle.y + particle.vy * t + dp(190f) * t * t;
            float size = dp(particle.unlock ? 24f : 16f) * (0.8f + 0.8f * (1f - t));
            int color = withAlpha(particle.color, (int) ((particle.unlock ? 225 : 170) * (1f - t)));
            float rotation = particle.spin + ease * (particle.unlock ? 360f : 180f);
            if (effectType == EFFECT_GEOMETRIC_MOSAIC) {
                drawTriangle(canvas, x, y, size, color, rotation);
            } else if (effectType == EFFECT_BRILLIANT_CUT) {
                drawDiamond(canvas, x, y, size, color, rotation);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1f));
                paint.setColor(Color.argb((int) (150 * (1f - t)), 255, 255, 255));
                canvas.drawLine(x - size * 0.7f, y, x + size * 0.7f, y, paint);
                canvas.drawLine(x, y - size * 0.7f, x, y + size * 0.7f, paint);
            } else {
                drawTile(canvas, x, y, size, color, rotation);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void addBubbles(float x, float y, int count, boolean unlock) {
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * 360f;
            float spread = dp(unlock ? 260f : 85f) * random.nextFloat();
            float sx = x + (float) Math.cos(Math.toRadians(angle)) * spread * 0.35f;
            float sy = y + (float) Math.sin(Math.toRadians(angle)) * spread * 0.35f;
            float vx = (float) Math.cos(Math.toRadians(angle)) * dp(unlock ? 90f + random.nextFloat() * 210f : 20f + random.nextFloat() * 65f);
            float vy = -dp(unlock ? 180f + random.nextFloat() * 420f : 70f + random.nextFloat() * 140f);
            particles.add(new Particle(sx, sy, vx, vy, now + i * 9L, unlock ? 1550f + random.nextFloat() * 650f : 920f, Color.rgb(210, 245, 255), null, unlock));
        }
        trimParticles();
        invalidate();
    }

    private void drawSparklingBubbles(Canvas canvas, long now) {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            float t = (now - particle.startedAt) / particle.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float x = particle.x + particle.vx * t + (float) Math.sin(t * Math.PI * 4f + particle.spin) * dp(18f);
            float y = particle.y + particle.vy * t + dp(80f) * t * t;
            float radius = dp(particle.unlock ? 8f + 18f * (1f - t) : 6f + 8f * (1f - t));
            int alpha = (int) ((particle.unlock ? 205 : 150) * (1f - t));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.4f));
            paint.setColor(Color.argb(alpha, 226, 250, 255));
            canvas.drawCircle(x, y, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(alpha / 2, 255, 255, 255));
            canvas.drawCircle(x - radius * 0.32f, y - radius * 0.34f, Math.max(2f, radius * 0.18f), paint);
            if (particle.unlock && ((int) (particle.spin + t * 10f) & 1) == 0) {
                paint.setColor(Color.argb(alpha, 255, 255, 255));
                canvas.drawLine(x - radius * 0.8f, y, x + radius * 0.8f, y, paint);
                canvas.drawLine(x, y - radius * 0.8f, x, y + radius * 0.8f, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void addFestivalTouch(float x, float y, int count, boolean strong) {
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * 360f;
            float speed = dp(strong ? 90f + random.nextFloat() * 260f : 40f + random.nextFloat() * 120f);
            float vx = (float) Math.cos(Math.toRadians(angle)) * speed;
            float vy = (float) Math.sin(Math.toRadians(angle)) * speed - dp(strong ? 80f : 30f);
            int color = festivalColor(i);
            Bitmap sprite = randomSprite(currentTouchSprites());
            particles.add(new Particle(x, y, vx, vy, now, strong ? 1200f : 760f, color, sprite, strong));
        }
        trimParticles();
        invalidate();
    }

    private void addFestivalUnlock(float x, float y) {
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < 54; i++) {
            float angle = -165f + random.nextFloat() * 150f;
            float speed = dp(180f + random.nextFloat() * 420f);
            float vx = (float) Math.cos(Math.toRadians(angle)) * speed;
            float vy = (float) Math.sin(Math.toRadians(angle)) * speed - dp(140f + random.nextFloat() * 160f);
            int color = festivalColor(i + 7);
            Bitmap sprite = randomSprite(currentUnlockSprites());
            particles.add(new Particle(x, y, vx, vy, now + i * 7L, 1350f + random.nextFloat() * 420f, color, sprite, true));
        }
        trimParticles();
        invalidate();
    }

    private Bitmap[] currentTouchSprites() {
        if (effectType == EFFECT_NOTE4_COLORED_PAPER) {
            return coloredPaperCandies;
        }
        if (effectType == EFFECT_NOTE4_DREAMY_FESTIVAL) {
            return dreamyPetals;
        }
        return effectType == EFFECT_NOTE4_SEASONAL_TOUCH || effectType == EFFECT_NOTE4_SEASONAL_UNLOCK
                ? seasonalTouchSprites
                : touchSprites;
    }

    private Bitmap[] currentUnlockSprites() {
        if (effectType == EFFECT_NOTE4_COLORED_PAPER) {
            return coloredPaperCandies;
        }
        if (effectType == EFFECT_NOTE4_DREAMY_FESTIVAL) {
            return dreamyFireworkA;
        }
        return effectType == EFFECT_NOTE4_SEASONAL_TOUCH || effectType == EFFECT_NOTE4_SEASONAL_UNLOCK
                ? seasonalUnlockSprites
                : unlockSprites;
    }

    private void trimRipples() {
        while (ripples.size() > MAX_RIPPLES) {
            ripples.remove(0);
        }
    }

    private void trimParticles() {
        while (particles.size() > MAX_PARTICLES) {
            particles.remove(0);
        }
    }

    private void drawFestival(Canvas canvas, long now) {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            float t = (now - particle.startedAt) / particle.duration;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            if (t < 0f) {
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t);
            float x = particle.x + particle.vx * t;
            float y = particle.y + particle.vy * t + dp(300f) * t * t;
            float alpha = (1f - t) * (particle.unlock ? 0.95f : 0.72f);
            float size = dp(particle.unlock ? 22f + 18f * (1f - t) : 13f + 10f * (1f - t));
            if (particle.sprite != null) {
                drawBitmapCentered(canvas, particle.sprite, x, y, size * 2.4f, alpha, particle.spin + ease * 220f);
            } else {
                drawPetal(canvas, x, y, size, alpha, particle.color, particle.spin + ease * 210f);
            }
        }
    }

    private void drawColoredPaperScene(Canvas canvas, long now) {
        if (coloredPaperBirthdayScene != null && !coloredPaperBirthdayScene.sprites.isEmpty()) {
            drawStockFestivalScene(canvas, coloredPaperBirthdayScene, now);
            return;
        }
        drawBitmapCover(canvas, coloredPaperBirthdayBackground, Color.rgb(38, 42, 74));
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.58f;
        float scene = Math.min(getWidth(), getHeight());
        if (coloredPaperCake != null) {
            drawBitmapCentered(canvas, coloredPaperCake, cx, cy, scene * 0.52f, 1f, 0f);
        }
        Bitmap flame = spriteAt(coloredPaperFlames, (int) ((now - chargerStartedAt) / 46L));
        if (flame != null) {
            drawBitmapCentered(canvas, flame, cx, cy - scene * 0.19f, scene * 0.16f, 1f, 0f);
        }
        for (int i = 0; i < 14; i++) {
            Bitmap candy = spriteAt(coloredPaperCandies, i);
            if (candy == null) {
                continue;
            }
            float phase = (now - chargerStartedAt) / (900f + i * 37f) + i * 0.73f;
            float x = (0.1f + (i % 7) * 0.135f) * getWidth() + (float) Math.sin(phase) * dp(12f);
            float y = getHeight() * (0.72f + (i / 7) * 0.13f) + (float) Math.cos(phase * 0.8f) * dp(10f);
            float size = dp(24f + (i % 4) * 6f);
            drawBitmapCentered(canvas, candy, x, y, size * 2.4f, 0.72f, phase * 24f);
        }
    }

    private void drawStockFestivalScene(Canvas canvas, StockFestivalScene scene, long now) {
        long elapsed = now - chargerStartedAt;
        float scale = Math.max(getWidth() / STOCK_CHARGE_BASE_WIDTH, getHeight() / STOCK_CHARGE_BASE_HEIGHT);
        float left = (getWidth() - STOCK_CHARGE_BASE_WIDTH * scale) * 0.5f;
        float top = (getHeight() - STOCK_CHARGE_BASE_HEIGHT * scale) * 0.5f;
        for (StockFestivalSprite sprite : scene.sprites) {
            Bitmap bitmap = sprite.bitmapAt(elapsed);
            if (bitmap == null) {
                continue;
            }
            float x = sprite.hasTranslateX ? sprite.translateX.valueAt(elapsed) : sprite.x;
            float y = sprite.hasTranslateY ? sprite.translateY.valueAt(elapsed) : sprite.y;
            float alpha = sprite.alpha.valueAt(elapsed);
            float scaleX = sprite.scaleX.valueAt(elapsed);
            float scaleY = sprite.scaleY.valueAt(elapsed);
            float rotation = sprite.rotate.valueAt(elapsed);
            drawStockFestivalBitmap(canvas, bitmap, scale, left, top, x, y, alpha, rotation, scaleX, scaleY);
        }
    }

    private void drawStockFestivalBitmap(Canvas canvas, Bitmap bitmap, float scale, float left, float top,
                                         float baseX, float baseY, float alpha, float rotation,
                                         float objectScaleX, float objectScaleY) {
        if (bitmap == null || alpha <= 0f || objectScaleX <= 0f || objectScaleY <= 0f) {
            return;
        }
        float baseWidth = bitmap.getWidth() / STOCK_CHARGE_ASSET_SCALE;
        float baseHeight = bitmap.getHeight() / STOCK_CHARGE_ASSET_SCALE;
        float centerX = stockX(baseX + baseWidth * 0.5f, scale, left);
        float centerY = stockY(baseY + baseHeight * 0.5f, scale, top);
        matrix.reset();
        matrix.postTranslate(-bitmap.getWidth() * 0.5f, -bitmap.getHeight() * 0.5f);
        matrix.postScale(scale * objectScaleX / STOCK_CHARGE_ASSET_SCALE,
                scale * objectScaleY / STOCK_CHARGE_ASSET_SCALE);
        matrix.postRotate(rotation);
        matrix.postTranslate(centerX, centerY);
        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        canvas.drawBitmap(bitmap, matrix, paint);
        paint.setAlpha(255);
    }

    private void drawDreamyFestivalScene(Canvas canvas, long now) {
        int top = Color.rgb(16, 32, 64);
        int bottom = Color.rgb(64, 18, 74);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new android.graphics.LinearGradient(0f, 0f, 0f, getHeight(), top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        Bitmap wave = spriteAt(dreamyWaves, (int) ((now - chargerStartedAt) / 130L));
        if (wave != null) {
            drawBitmapCover(canvas, wave, Color.TRANSPARENT);
        }
        drawFireworkSequence(canvas, dreamyFireworkA, now, getWidth() * 0.28f, getHeight() * 0.24f, dp(210f), 0L);
        drawFireworkSequence(canvas, dreamyFireworkB, now, getWidth() * 0.72f, getHeight() * 0.20f, dp(180f), 170L);
        drawFireworkSequence(canvas, dreamyFireworkC, now, getWidth() * 0.52f, getHeight() * 0.34f, dp(160f), 340L);
        for (int i = 0; i < 7; i++) {
            Bitmap lantern = spriteAt(dreamyLanterns, (int) ((now - chargerStartedAt) / 120L) + i * 6);
            if (lantern == null) {
                continue;
            }
            float phase = (now - chargerStartedAt) / (900f + i * 80f) + i * 0.8f;
            float x = getWidth() * (0.12f + i * 0.13f) + (float) Math.sin(phase) * dp(10f);
            float y = getHeight() * (0.52f + (i % 2) * 0.08f) + (float) Math.cos(phase) * dp(8f);
            drawBitmapCentered(canvas, lantern, x, y, dp(84f + (i % 3) * 18f), 0.88f, (float) Math.sin(phase) * 5f);
        }
        for (int i = 0; i < 18; i++) {
            Bitmap snow = spriteAt(dreamySnow, i);
            float phase = ((now - chargerStartedAt) / 2600f + i * 0.173f) % 1f;
            float x = getWidth() * ((i * 0.381f) % 1f) + (float) Math.sin(phase * Math.PI * 2f + i) * dp(18f);
            float y = getHeight() * phase;
            if (snow != null) {
                drawBitmapCentered(canvas, snow, x, y, dp(18f + (i % 4) * 5f), 0.45f, phase * 180f);
            } else {
                drawSnowflake(canvas, x, y, dp(8f + (i % 4) * 2f), 0.45f, phase * 180f);
            }
        }
    }

    private void drawFireworkSequence(Canvas canvas, Bitmap[] frames, long now, float x, float y, float size, long offset) {
        int index = (int) (((now - chargerStartedAt + offset) / 58L) % Math.max(1, frames.length));
        Bitmap frame = spriteAt(frames, index);
        if (frame != null) {
            drawBitmapCentered(canvas, frame, x, y, size, 0.92f, 0f);
            return;
        }
        float t = ((now - chargerStartedAt + offset) % 900L) / 900f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.6f));
        paint.setColor(Color.argb((int) (180 * (1f - t)), 255, 230, 130));
        for (int i = 0; i < 16; i++) {
            float angle = (float) (Math.PI * 2.0 * i / 16.0);
            float r = size * 0.08f + size * 0.34f * t;
            canvas.drawLine(x, y, x + (float) Math.cos(angle) * r, y + (float) Math.sin(angle) * r, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawChargerAnimation(Canvas canvas, long now, String[] names, Bitmap[] frames, boolean[] tried) {
        if (names.length == 0) {
            drawChargerFallback(canvas, now);
            return;
        }
        int frameIndex = (int) (((now - chargerStartedAt) / 34L) % names.length);
        Bitmap frame = chargerFrameAt(names, frames, tried, frameIndex);
        if (frame == null) {
            drawChargerFallback(canvas, now);
            return;
        }
        float targetSize = Math.min(Math.min(getWidth() * 0.72f, getHeight() * 0.42f), dp(360f));
        drawBitmapCentered(canvas, frame, getWidth() * 0.5f, getHeight() * 0.54f, targetSize, 1f, 0f);
    }

    private Bitmap chargerFrameAt(String[] names, Bitmap[] frames, boolean[] tried, int frameIndex) {
        for (int offset = 0; offset < 4; offset++) {
            int index = (frameIndex + offset) % names.length;
            if (!tried[index]) {
                frames[index] = loadBitmap(names[index]);
                tried[index] = true;
            }
            if (frames[index] != null) {
                return frames[index];
            }
        }
        return null;
    }

    private void drawChargerFallback(Canvas canvas, long now) {
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.54f;
        float width = Math.min(getWidth() * 0.42f, dp(180f));
        float height = width * 0.54f;
        float pulse = 0.5f + 0.5f * (float) Math.sin((now - chargerStartedAt) / 220f);
        RectF body = new RectF(cx - width * 0.5f, cy - height * 0.5f, cx + width * 0.5f, cy + height * 0.5f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3f));
        paint.setColor(Color.argb(180, 220, 245, 255));
        canvas.drawRoundRect(body, dp(6f), dp(6f), paint);
        RectF fill = new RectF(body.left + dp(7f), body.top + dp(7f), body.left + dp(7f) + (body.width() - dp(14f)) * pulse, body.bottom - dp(7f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(165, 130, 230, 255));
        canvas.drawRoundRect(fill, dp(4f), dp(4f), paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(13f));
        paint.setColor(Color.argb(180, 235, 245, 255));
        canvas.drawText("Note4 charger assets not decoded", cx, body.bottom + dp(28f), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSeasonalChargingIndicator(Canvas canvas, long now) {
        long elapsed = Math.max(0L, now - chargerStartedAt);
        int theme = (int) ((elapsed / SEASONAL_CHARGE_CYCLE_MS) % 4L);
        drawSeasonalChargingDoodle(canvas, now, theme);
    }

    private void drawSeasonalChargingDoodle(Canvas canvas, long now, int theme) {
        long elapsed = Math.max(0L, now - chargerStartedAt);
        int percent = (int) Math.min(100L, elapsed / SEASONAL_CHARGE_PERCENT_STEP_MS);
        Bitmap[] sprites = seasonalChargeSpritesForTheme(theme);
        drawStockChargeBackground(canvas, theme);
        float scale = stockChargeScale();
        float left = stockChargeLeft(scale);
        float top = stockChargeTop(scale);
        drawStockChargePreviewText(canvas, percent, scale, left, top);
        if (theme == 0) {
            drawSamsungSpringCharge(canvas, elapsed, percent, scale, left, top, sprites);
            return;
        }

        drawSamsungSeasonCharge(canvas, theme, elapsed, percent, scale, left, top, sprites);
    }

    private void drawStockChargeBackground(Canvas canvas, int theme) {
        int color;
        switch (theme) {
            case 1:
                color = Color.rgb(13, 142, 158);
                break;
            case 2:
                color = Color.rgb(8, 119, 157);
                break;
            case 3:
                color = Color.rgb(14, 122, 156);
                break;
            case 0:
            default:
                color = Color.rgb(8, 145, 148);
                break;
        }
        canvas.drawColor(color);
    }

    private void drawStockChargePreviewText(Canvas canvas, int percent, float scale, float left, float top) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.argb(238, 250, 255, 255));
        paint.setTextSize(58f * scale);
        canvas.drawText("12:45", stockX(180f, scale, left), stockY(83f, scale, top), paint);
        paint.setTextSize(18f * scale);
        paint.setColor(Color.argb(218, 250, 255, 255));
        canvas.drawText("Tue, 28 October", stockX(180f, scale, left), stockY(122f, scale, top), paint);
        paint.setTextSize(14f * scale);
        paint.setColor(Color.argb(230, 250, 255, 255));
        canvas.drawText(seasonalChargeStatus(percent), stockX(180f, scale, left), stockY(238f, scale, top), paint);
        paint.setTextAlign(Paint.Align.LEFT);
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
        int count = frameIndex == 4 ? SPRING_PARTICLE_SPRITE_INDEX.length - 1 : SPRING_PARTICLE_SPRITE_INDEX.length;
        for (int i = 0; i < count; i++) {
            int spriteIndex = SPRING_PARTICLE_SPRITE_INDEX[i];
            if (spriteIndex >= sprites.length || sprites[spriteIndex] == null) {
                continue;
            }
            long local = elapsed - i * SPRING_PARTICLE_DELAY_MS;
            if (local < 0L) {
                continue;
            }
            float raw = (local % SPRING_PARTICLE_DURATION_MS) / (float) SPRING_PARTICLE_DURATION_MS;
            float eased = accelerateDecelerate(raw);
            float startX;
            float startY;
            float endX;
            float endY;
            if (frameIndex == 4) {
                float angleX = stableInt(i, 11, 359) * (float) Math.PI / 180f;
                float angleY = stableInt(i, 12, 359) * (float) Math.PI / 180f;
                startX = 170f;
                startY = 350f;
                endX = 170f + 113f * (float) Math.sin(angleX);
                endY = 350f - 113f * (float) Math.cos(angleY);
            } else {
                startX = 108f + stableInt(i, 1, 144);
                startY = 888f;
                endX = 170f;
                endY = 334f;
            }
            float objectScale = springParticleScale(i, eased);
            float alpha = 1f - raw;
            float rotation = 359f * eased;
            drawStockBitmapTopLeft(canvas, sprites[spriteIndex], scale, left, top,
                    lerp(startX, endX, eased), lerp(startY, endY, eased), alpha, rotation, objectScale);
        }
    }

    private void drawSamsungSeasonCharge(Canvas canvas, int theme, long elapsed, int percent, float scale, float left, float top, Bitmap[] sprites) {
        int frameIndex = seasonalChargeFrameIndex(percent);
        drawSamsungSeasonParticles(canvas, theme, elapsed, frameIndex, scale, left, top, sprites);

        float baseX = seasonChargeTopLeftX(theme);
        float baseFrameY = lerp(300f, 267f, accelerateDecelerate(reverseRepeatPhase(elapsed, STOCK_CHARGE_MOVE_DURATION_MS)));
        float frameY = lerp(300f, 266f, accelerateDecelerate(reverseRepeatPhase(elapsed, STOCK_CHARGE_MOVE_DURATION_MS)));
        Bitmap base = theme == 3 && seasonalChargeWinterSprites != null && seasonalChargeWinterSprites.length > 0 ? seasonalChargeWinterSprites[0] : null;
        if (theme == 3 && base != null) {
            drawStockBitmapTopLeft(canvas, base, scale, left, top, baseX, baseFrameY, 1f,
                    seasonChargeRotation(theme, elapsed, true), 1f);
        }
        if (theme == 2 && frameIndex == 4) {
            drawAutumnChargeCircles(canvas, elapsed, scale, left, top, sprites);
        }

        Bitmap frame = seasonalChargeFrameBitmap(theme, sprites, frameIndex);
        if (frame != null) {
            drawStockBitmapTopLeft(canvas, frame, scale, left, top, baseX, frameY, 1f,
                    seasonChargeRotation(theme, elapsed, false), 1f);
        }
    }

    private void drawSamsungSeasonParticles(Canvas canvas, int theme, long elapsed, int frameIndex, float scale, float left, float top, Bitmap[] sprites) {
        int count = seasonParticleCount(theme, frameIndex);
        for (int i = 0; i < count; i++) {
            int spriteIndex = seasonParticleSpriteIndex(theme, i);
            if (spriteIndex < 0 || sprites == null || spriteIndex >= sprites.length || sprites[spriteIndex] == null) {
                continue;
            }
            long local = elapsed - i * SPRING_PARTICLE_DELAY_MS;
            if (local < 0L) {
                continue;
            }
            float raw = (local % SPRING_PARTICLE_DURATION_MS) / (float) SPRING_PARTICLE_DURATION_MS;
            float eased = accelerateDecelerate(raw);
            float startX;
            float startY;
            float endX;
            float endY;
            if (frameIndex == 4 && theme != 2) {
                startX = 170f;
                startY = 350f;
                if (theme == 1) {
                    float angleX = (90f - stableInt(i, 21, 180)) * (float) Math.PI / 180f;
                    float angleY = (90f - stableInt(i, 22, 180)) * (float) Math.PI / 180f;
                    endX = 170f + 113f * (float) Math.sin(angleX);
                    endY = 350f - 113f * (float) Math.cos(angleY);
                } else {
                    float angleX = stableInt(i, 23, 359) * (float) Math.PI / 180f;
                    float angleY = stableInt(i, 24, 359) * (float) Math.PI / 180f;
                    endX = 170f + 113f * (float) Math.sin(angleX);
                    endY = 350f - 113f * (float) Math.cos(angleY);
                }
            } else {
                startX = 170f;
                startY = 888f;
                endX = 108f + stableInt(i, 25 + theme, 144);
                endY = 334f;
            }
            float alpha = 1f - raw;
            float objectScale = seasonParticleScale(theme, i, eased);
            float rotation = seasonParticleRotates(theme, i) ? 359f * accelerateDecelerate((local % seasonParticleRotateDuration(theme)) / (float) seasonParticleRotateDuration(theme)) : 0f;
            drawStockBitmapTopLeft(canvas, sprites[spriteIndex], scale, left, top,
                    lerp(startX, endX, eased), lerp(startY, endY, eased), alpha, rotation, objectScale);
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
            float alpha = 1f - raw * raw;
            float objectScale = lerp(1f, 2.2f, accelerateDecelerate(raw));
            drawStockBitmapTopLeft(canvas, sprites[SEASONAL_CHARGE_FRAME_COUNT], scale, left, top,
                    100f, 264f, alpha, 0f, objectScale);
        }
    }

    private float seasonChargeTopLeftX(int theme) {
        if (theme == 2) {
            return 114f;
        }
        if (theme == 3) {
            return 121f;
        }
        return 118f;
    }

    private float seasonChargeRotation(int theme, long elapsed, boolean base) {
        if (theme == 2) {
            long duration = base ? STOCK_CHARGE_ROTATE_DURATION_MS : 6670L;
            return lerp(-10f, 10f, accelerateDecelerate(reverseRepeatPhase(elapsed, duration)));
        }
        return 359f * accelerateDecelerate((elapsed % STOCK_CHARGE_ROTATE_DURATION_MS) / (float) STOCK_CHARGE_ROTATE_DURATION_MS);
    }

    private Bitmap seasonalChargeFrameBitmap(int theme, Bitmap[] sprites, int frameIndex) {
        if (sprites == null) {
            return null;
        }
        if (theme == 3) {
            if (frameIndex == 0) {
                return null;
            }
            int winterIndex = frameIndex;
            return winterIndex < sprites.length ? sprites[winterIndex] : null;
        }
        return frameIndex < sprites.length ? sprites[frameIndex] : null;
    }

    private int seasonParticleCount(int theme, int frameIndex) {
        if (theme == 1) {
            return frameIndex == 4 ? 8 : 10;
        }
        if (theme == 2) {
            return frameIndex == 4 ? 0 : 6;
        }
        if (theme == 3) {
            return 12;
        }
        return 0;
    }

    private int seasonParticleSpriteIndex(int theme, int index) {
        if (theme == 1) {
            return index < 8 ? 5 : index == 8 ? 6 : 7;
        }
        if (theme == 2) {
            return index < 3 ? 6 : index == 3 ? 7 : index == 4 ? 8 : 9;
        }
        if (theme == 3) {
            int[] ids = {0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 2, 3};
            int id = ids[stableInt(index, 31, ids.length)];
            return 5 + id;
        }
        return -1;
    }

    private float seasonParticleScale(int theme, int index, float eased) {
        float random = stableUnit(index, 41 + theme);
        float target = 1f;
        if (theme == 1) {
            target = index < 8 ? 0.5f + 0.5f * random : index == 8 ? 0.8f + 0.2f * random : 1f + 0.2f * random;
        } else if (theme == 2) {
            target = index < 3 ? 0.6f + 0.4f * random : 0.8f + 0.2f * random;
        } else if (theme == 3) {
            int[] ids = {0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 2, 3};
            int id = ids[stableInt(index, 31, ids.length)];
            if (id == 0) {
                target = 0.6f + 0.8f * random;
            } else if (id == 1) {
                target = 0.5f + 0.7f * random;
            } else if (id == 2) {
                target = 1f + 1.6f * random;
            } else {
                target = 1f;
            }
        }
        return lerp(1f, target, eased);
    }

    private boolean seasonParticleRotates(int theme, int index) {
        if (theme == 3) {
            int[] ids = {0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 2, 3};
            int id = ids[stableInt(index, 31, ids.length)];
            return id < 3;
        }
        return true;
    }

    private long seasonParticleRotateDuration(int theme) {
        return theme == 3 ? 1000L : SPRING_PARTICLE_DURATION_MS;
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
        return (getWidth() - STOCK_CHARGE_BASE_WIDTH * scale) * 0.5f;
    }

    private float stockChargeTop(float scale) {
        float contentHeight = STOCK_CHARGE_BASE_HEIGHT * scale;
        return getHeight() >= contentHeight ? 0f : (getHeight() - contentHeight) * 0.5f;
    }

    private float stockX(float x, float scale, float left) {
        return left + x * scale;
    }

    private float stockY(float y, float scale, float top) {
        return top + y * scale;
    }

    private float springParticleScale(int index, float eased) {
        float random = stableUnit(index, 2);
        float target;
        if (index < 6) {
            target = 0.5f + 0.3f * random;
        } else if (index < 8) {
            target = 0.7f + 0.3f * random;
        } else if (index < 12) {
            target = 0.4f + 0.2f * random;
        } else {
            target = 0.5f + 0.7f * random;
        }
        return lerp(1f, target, eased);
    }

    private float stableUnit(int index, int salt) {
        int value = 0x45d9f3b ^ (index * 0x1f123bb5) ^ (salt * 0x6c8e9cf5);
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;
        return (value & 0x00ffffff) / (float) 0x01000000;
    }

    private int stableInt(int index, int salt, int bound) {
        return Math.max(0, Math.min(bound - 1, (int) (stableUnit(index, salt) * bound)));
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
        float baseWidth = bitmap.getWidth() / STOCK_CHARGE_ASSET_SCALE;
        float baseHeight = bitmap.getHeight() / STOCK_CHARGE_ASSET_SCALE;
        float centerX = stockX(baseX + baseWidth * 0.5f, scale, left);
        float centerY = stockY(baseY + baseHeight * 0.5f, scale, top);
        float renderScale = scale * objectScale / STOCK_CHARGE_ASSET_SCALE;
        matrix.reset();
        matrix.postTranslate(-bitmap.getWidth() * 0.5f, -bitmap.getHeight() * 0.5f);
        matrix.postScale(renderScale, renderScale);
        matrix.postRotate(rotation);
        matrix.postTranslate(centerX, centerY);
        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        canvas.drawBitmap(bitmap, matrix, paint);
        paint.setAlpha(255);
    }

    private void drawSeasonalStockChargeFrame(Canvas canvas, int theme, long elapsed, int percent, float cx, float cy, float radius, Bitmap[] sprites, int color, float pulse) {
        Bitmap frame = seasonalChargeFrame(theme, sprites, percent);
        if (frame == null) {
            drawSeasonalFallbackGlyph(canvas, theme, cx, cy, radius * 0.88f, 0.82f, elapsed / 80f);
            return;
        }

        if (theme == 3) {
            Bitmap base = sprites[0];
            if (base != null) {
                drawBitmapCentered(canvas, base, cx, cy, seasonalChargeFrameSize(theme, radius), 0.92f, 0f);
            }
        } else if (theme == 2) {
            Bitmap circle = sprites.length > SEASONAL_CHARGE_FRAME_COUNT ? sprites[SEASONAL_CHARGE_FRAME_COUNT] : null;
            if (circle != null) {
                drawBitmapCentered(canvas, circle, cx, cy, radius * 1.58f, 0.80f, 0f);
            }
        }

        float size = seasonalChargeFrameSize(theme, radius);
        drawBitmapCentered(canvas, frame, cx, cy, size, 1f, 0f);
    }

    private Bitmap seasonalChargeFrame(int theme, Bitmap[] sprites, int percent) {
        if (sprites == null || sprites.length < SEASONAL_CHARGE_FRAME_COUNT) {
            return null;
        }
        return sprites[seasonalChargeFrameIndex(percent)];
    }

    private float seasonalChargeFrameSize(int theme, float radius) {
        switch (theme) {
            case 1:
                return radius * 1.78f;
            case 2:
                return radius * 1.58f;
            case 3:
                return radius * 1.54f;
            case 0:
            default:
                return radius * 1.48f;
        }
    }

    private String seasonalChargeStatus(int percent) {
        return percent >= 100 ? "Charged" : "Charging, " + percent + "%";
    }

    private void drawSeasonalStockParticles(Canvas canvas, int theme, long elapsed, float cx, float cy, float radius, Bitmap[] sprites) {
        int start = seasonalChargeParticleStart(theme);
        if (sprites == null || sprites.length <= start) {
            return;
        }
        int count = sprites.length - start;
        int particleCount = theme == 3 ? 24 : theme == 2 ? 18 : 16;
        for (int i = 0; i < particleCount; i++) {
            Bitmap sprite = sprites[start + (i % count)];
            if (sprite == null) {
                continue;
            }
            float phase = elapsed / (theme == 1 ? 520f + i * 17f : 720f + i * 21f) + i * 0.77f;
            float drift = ((elapsed / (24f + i)) + i * dp(23f)) % (radius * 1.65f);
            float spread = radius * (0.38f + (i % 5) * 0.09f);
            float x = cx + (float) Math.sin(phase) * spread;
            float y = cy + radius * 0.70f - drift;
            float size = dp(18f + (i % 4) * 4f);
            float alpha = 0.30f + (i % 4) * 0.08f;
            float rotation = elapsed / (theme == 2 ? 42f : 58f) + i * 31f;
            if (theme == 3) {
                alpha *= 0.85f;
                size *= i % 5 == 0 ? 1.45f : 0.82f;
            } else if (theme == 1) {
                x = cx + (float) Math.sin(phase * 0.72f) * radius * (0.48f + (i % 3) * 0.12f);
                y = cy - radius * 0.58f + drift;
            }
            drawBitmapCentered(canvas, sprite, x, y, size, alpha, rotation);
        }
    }

    private int seasonalChargeParticleStart(int theme) {
        return theme == 2 ? SEASONAL_CHARGE_FRAME_COUNT + 1 : SEASONAL_CHARGE_FRAME_COUNT;
    }

    private void drawChargeProgressTrail(Canvas canvas, float cx, float y, float width, int percent, int color) {
        float left = cx - width * 0.5f;
        float right = cx + width * 0.5f;
        float filled = left + width * percent / 100f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(2.8f));
        paint.setColor(Color.argb(62, 255, 255, 255));
        canvas.drawLine(left, y, right, y, paint);
        paint.setStrokeWidth(dp(4.2f));
        paint.setColor(withAlpha(color, 190));
        canvas.drawLine(left, y, filled, y, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i <= 10; i++) {
            float x = left + width * i / 10f;
            paint.setColor(i * 10 <= percent ? withAlpha(color, 215) : Color.argb(74, 255, 255, 255));
            canvas.drawCircle(x, y, dp(i * 10 <= percent ? 2.5f : 1.8f), paint);
        }
    }

    private void drawSeasonalChargeFillGlyph(Canvas canvas, int theme, float cx, float cy, float size, int percent, int color, long elapsed) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(elapsed / 520f);
        float rotation = theme == 2 ? -18f + pulse * 5f : theme == 3 ? elapsed / 120f : 0f;
        int ghostColor = Color.argb(54, 255, 255, 255);
        int outlineColor = Color.argb(150, 255, 255, 255);
        int fillColor = withAlpha(color, 205);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                cx,
                cy,
                size * 0.82f,
                withAlpha(color, 30),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, size * 0.78f, paint);
        paint.setShader(null);

        drawChargeGlyphShape(canvas, theme, cx, cy, size, ghostColor, true, rotation, dp(2.4f));

        canvas.save();
        float fillTop = cy + size * 0.56f - size * 1.12f * percent / 100f;
        canvas.clipRect(cx - size * 0.72f, fillTop, cx + size * 0.72f, cy + size * 0.62f);
        drawChargeGlyphShape(canvas, theme, cx, cy, size * (1f + pulse * 0.018f), fillColor, true, rotation, dp(2.4f));
        canvas.restore();

        drawChargeGlyphShape(canvas, theme, cx, cy, size, outlineColor, false, rotation, dp(2.2f));
    }

    private void drawChargeGlyphShape(Canvas canvas, int theme, float cx, float cy, float size, int color, boolean filled, float rotation, float strokeWidth) {
        if (theme == 0) {
            drawChargeFlowerShape(canvas, cx, cy, size, color, filled, rotation, strokeWidth);
        } else if (theme == 1) {
            drawChargeSunShape(canvas, cx, cy, size, color, filled, rotation, strokeWidth);
        } else if (theme == 2) {
            drawChargeLeafShape(canvas, cx, cy, size, color, filled, rotation, strokeWidth);
        } else {
            drawChargeSnowflakeShape(canvas, cx, cy, size, color, filled, rotation, strokeWidth);
        }
    }

    private void drawChargeFlowerShape(Canvas canvas, float cx, float cy, float size, int color, boolean filled, float rotation, float strokeWidth) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        for (int i = 0; i < 6; i++) {
            canvas.save();
            canvas.rotate(i * 60f);
            Path petal = new Path();
            float petalLength = size * 0.38f;
            float petalWidth = size * 0.20f;
            petal.moveTo(0f, -size * 0.09f);
            petal.cubicTo(petalWidth, -size * 0.18f, petalWidth, -petalLength * 0.82f, 0f, -petalLength);
            petal.cubicTo(-petalWidth, -petalLength * 0.82f, -petalWidth, -size * 0.18f, 0f, -size * 0.09f);
            drawChargePath(canvas, petal, color, filled, strokeWidth);
            canvas.restore();
        }
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(color);
        canvas.drawCircle(0f, 0f, size * 0.105f, paint);
        canvas.restore();
    }

    private void drawChargeSunShape(Canvas canvas, float cx, float cy, float size, int color, boolean filled, float rotation, float strokeWidth) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        paint.setColor(color);
        if (filled) {
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 12; i++) {
                canvas.save();
                canvas.rotate(i * 30f);
                Path ray = new Path();
                ray.moveTo(0f, -size * 0.36f);
                ray.lineTo(size * 0.055f, -size * 0.52f);
                ray.lineTo(0f, -size * 0.64f);
                ray.lineTo(-size * 0.055f, -size * 0.52f);
                ray.close();
                canvas.drawPath(ray, paint);
                canvas.restore();
            }
            canvas.drawCircle(0f, 0f, size * 0.32f, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(strokeWidth);
            for (int i = 0; i < 12; i++) {
                canvas.save();
                canvas.rotate(i * 30f);
                canvas.drawLine(0f, -size * 0.41f, 0f, -size * 0.62f, paint);
                canvas.restore();
            }
            canvas.drawCircle(0f, 0f, size * 0.32f, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
        canvas.restore();
    }

    private void drawChargeLeafShape(Canvas canvas, float cx, float cy, float size, int color, boolean filled, float rotation, float strokeWidth) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        Path leaf = new Path();
        leaf.moveTo(0f, -size * 0.52f);
        leaf.cubicTo(size * 0.42f, -size * 0.38f, size * 0.50f, size * 0.22f, 0f, size * 0.54f);
        leaf.cubicTo(-size * 0.52f, size * 0.08f, -size * 0.40f, -size * 0.38f, 0f, -size * 0.52f);
        drawChargePath(canvas, leaf, color, filled, strokeWidth);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth * 0.62f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        canvas.drawLine(0f, -size * 0.34f, 0f, size * 0.38f, paint);
        for (int i = -2; i <= 2; i++) {
            if (i == 0) {
                continue;
            }
            float y = i * size * 0.10f;
            canvas.drawLine(0f, y, i < 0 ? -size * 0.13f : size * 0.15f, y + size * 0.10f, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        canvas.restore();
    }

    private void drawChargeSnowflakeShape(Canvas canvas, float cx, float cy, float size, int color, boolean filled, float rotation, float strokeWidth) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(filled ? strokeWidth * 3.1f : strokeWidth * 1.35f);
        paint.setColor(color);
        for (int i = 0; i < 6; i++) {
            canvas.save();
            canvas.rotate(i * 60f);
            canvas.drawLine(0f, -size * 0.48f, 0f, size * 0.48f, paint);
            canvas.drawLine(0f, -size * 0.36f, size * 0.13f, -size * 0.24f, paint);
            canvas.drawLine(0f, -size * 0.36f, -size * 0.13f, -size * 0.24f, paint);
            canvas.drawLine(0f, size * 0.36f, size * 0.13f, size * 0.24f, paint);
            canvas.drawLine(0f, size * 0.36f, -size * 0.13f, size * 0.24f, paint);
            canvas.restore();
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(0f, 0f, filled ? size * 0.055f : size * 0.035f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        canvas.restore();
    }

    private void drawChargePath(Canvas canvas, Path path, int color, boolean filled, float strokeWidth) {
        paint.setColor(color);
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
    }

    private void drawSpringChargeDoodle(Canvas canvas, long elapsed, float cx, float cy, float radius, Bitmap[] sprites, int color, float pulse) {
        float flowerY = cy - radius * 0.14f;
        for (int i = 0; i < 6; i++) {
            float angle = i * 60f + elapsed / 75f;
            float radians = (float) Math.toRadians(angle);
            float x = cx + (float) Math.cos(radians) * radius * 0.28f;
            float y = flowerY + (float) Math.sin(radians) * radius * 0.20f;
            Bitmap sprite = spriteAt(sprites, i);
            if (sprite != null) {
                drawBitmapCentered(canvas, sprite, x, y, dp(54f + pulse * 8f), 0.86f, angle);
            } else {
                drawPetal(canvas, x, y, dp(23f + pulse * 4f), 0.82f, color, angle);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color, 210));
        canvas.drawCircle(cx, flowerY, dp(14f + pulse * 3f), paint);

        for (int i = 0; i < 18; i++) {
            float phase = elapsed / (720f + i * 24f) + i * 0.82f;
            float drift = (elapsed / (46f + i)) % (radius * 1.55f);
            float x = cx + (float) Math.sin(phase) * radius * (0.42f + (i % 5) * 0.08f);
            float y = cy + radius * 0.55f - drift;
            float size = dp(10f + (i % 4) * 3f);
            Bitmap sprite = spriteAt(sprites, i + 8);
            if (sprite != null) {
                drawBitmapCentered(canvas, sprite, x, y, size * 2.4f, 0.52f, elapsed / 42f + i * 29f);
            } else {
                drawPetal(canvas, x, y, size, 0.45f, color, elapsed / 42f + i * 29f);
            }
        }
    }

    private void drawSummerChargeDoodle(Canvas canvas, long elapsed, float cx, float cy, float radius, Bitmap[] sprites, int color, float pulse) {
        int frame = 1 + (int) ((elapsed / 130L) % 6L);
        Bitmap wave = spriteAt(sprites, frame);
        if (wave != null) {
            drawBitmapCentered(canvas, wave, cx, cy + radius * 0.38f, radius * 1.7f, 0.78f, 0f);
        } else {
            for (int i = 0; i < 3; i++) {
                drawWaveStroke(canvas, cx, cy + radius * (0.25f + i * 0.12f), radius * 1.42f, color, 0.38f + i * 0.12f, elapsed / 320f + i * 0.75f);
            }
        }

        Bitmap rain = spriteAt(sprites, 0);
        for (int i = 0; i < 16; i++) {
            float phase = elapsed / (610f + i * 15f) + i * 0.73f;
            float x = cx + (float) Math.sin(phase) * radius * (0.50f + (i % 3) * 0.12f);
            float y = cy - radius * 0.58f + ((elapsed / (18f + i)) + i * dp(21f)) % (radius * 1.06f);
            float size = dp(9f + (i % 4) * 2f);
            if (rain != null && i % 3 == 0) {
                drawBitmapCentered(canvas, rain, x, y, size * 3.0f, 0.46f, -18f);
            } else {
                drawDrop(canvas, x, y, size, 0.55f, color, elapsed / 30f + i * 21f);
            }
        }

        for (int i = 0; i < 7; i++) {
            float angle = elapsed / 55f + i * 51f;
            float radians = (float) Math.toRadians(angle);
            float x = cx + (float) Math.cos(radians) * radius * (0.35f + pulse * 0.08f);
            float y = cy - radius * 0.08f + (float) Math.sin(radians) * radius * 0.23f;
            drawDrop(canvas, x, y, dp(12f), 0.42f, color, angle);
        }
    }

    private void drawAutumnChargeDoodle(Canvas canvas, long elapsed, float cx, float cy, float radius, Bitmap[] sprites, int color, float pulse) {
        for (int i = 0; i < 20; i++) {
            float phase = elapsed / (680f + i * 18f) + i * 0.54f;
            float orbit = radius * (0.36f + (i % 5) * 0.08f + pulse * 0.03f);
            float x = cx + (float) Math.sin(phase) * orbit;
            float y = cy - radius * 0.04f + (float) Math.cos(phase * 0.72f) * radius * (0.22f + (i % 4) * 0.07f);
            float size = dp(13f + (i % 4) * 4f);
            float rotation = elapsed / 32f + i * 38f;
            Bitmap sprite = spriteAt(sprites, i);
            if (sprite != null) {
                drawBitmapCentered(canvas, sprite, x, y, size * 2.35f, 0.72f, rotation);
            } else {
                drawLeaf(canvas, x, y, size, 0.64f, color, rotation);
            }
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(2f));
        paint.setColor(withAlpha(color, 120));
        RectF sweep = new RectF(cx - radius * 0.72f, cy - radius * 0.50f, cx + radius * 0.72f, cy + radius * 0.54f);
        canvas.drawArc(sweep, 205f, 205f, false, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawWinterChargeDoodle(Canvas canvas, long elapsed, float cx, float cy, float radius, Bitmap[] sprites, int color, float pulse) {
        Bitmap main = spriteAt(sprites, (int) ((elapsed / 120L) % Math.max(1, sprites.length)));
        if (main != null) {
            drawBitmapCentered(canvas, main, cx, cy - radius * 0.15f, radius * (0.62f + pulse * 0.08f), 0.46f, elapsed / 70f);
        } else {
            drawSnowflake(canvas, cx, cy - radius * 0.15f, radius * (0.25f + pulse * 0.03f), 0.58f, elapsed / 55f);
        }

        for (int i = 0; i < 24; i++) {
            float phase = elapsed / (760f + i * 12f) + i * 0.49f;
            float fall = ((elapsed / (22f + i)) + i * dp(17f)) % (radius * 1.55f);
            float x = cx + (float) Math.sin(phase) * radius * (0.55f + (i % 4) * 0.08f);
            float y = cy - radius * 0.74f + fall;
            float size = dp(7f + (i % 4) * 3f);
            Bitmap sprite = spriteAt(sprites, i + 3);
            if (sprite != null && i % 2 == 0) {
                drawBitmapCentered(canvas, sprite, x, y, size * 2.2f, 0.45f, elapsed / 54f + i * 15f);
            } else {
                drawSnowflake(canvas, x, y, size, 0.46f, elapsed / 54f + i * 15f);
            }
        }
    }

    private Bitmap[] seasonalChargeSpritesForTheme(int theme) {
        switch (theme) {
            case 0:
                return seasonalChargeFlowerSprites;
            case 1:
                return seasonalChargeSummerSprites;
            case 2:
                return seasonalChargeLeafSprites;
            case 3:
            default:
                return seasonalChargeWinterSprites;
        }
    }

    private String seasonalChargeName(int theme) {
        switch (theme) {
            case 0:
                return "flower";
            case 1:
                return "summer";
            case 2:
                return "leaf";
            case 3:
            default:
                return "winter";
        }
    }

    private int seasonalChargeColor(int theme) {
        switch (theme) {
            case 0:
                return Color.rgb(255, 150, 190);
            case 1:
                return Color.rgb(255, 216, 95);
            case 2:
                return Color.rgb(244, 150, 72);
            case 3:
            default:
                return Color.rgb(170, 225, 255);
        }
    }

    private void drawSeasonalFallbackGlyph(Canvas canvas, int theme, float x, float y, float size, float alpha, float rotation) {
        if (theme == 3) {
            drawSnowflake(canvas, x, y, size, alpha, rotation);
        } else if (theme == 2) {
            drawLeaf(canvas, x, y, size, alpha, seasonalChargeColor(theme), rotation);
        } else if (theme == 1) {
            drawDrop(canvas, x, y, size, alpha, seasonalChargeColor(theme), rotation);
        } else {
            drawPetal(canvas, x, y, size, alpha, seasonalChargeColor(theme), rotation);
        }
    }

    private void drawDrop(Canvas canvas, float x, float y, float size, float alpha, int color, float rotation) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        Path path = new Path();
        path.moveTo(0f, -size);
        path.cubicTo(size * 0.92f, -size * 0.18f, size * 0.68f, size * 0.92f, 0f, size);
        path.cubicTo(-size * 0.68f, size * 0.92f, -size * 0.92f, -size * 0.18f, 0f, -size);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                -size * 0.18f,
                -size * 0.18f,
                size * 1.35f,
                Color.argb((int) (180 * alpha), 255, 255, 255),
                withAlpha(color, (int) (170 * alpha)),
                Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void drawWaveStroke(Canvas canvas, float cx, float y, float width, int color, float alpha, float phase) {
        Path path = new Path();
        float left = cx - width * 0.5f;
        float step = width / 4f;
        path.moveTo(left, y);
        for (int i = 0; i < 4; i++) {
            float start = left + i * step;
            float middle = start + step * 0.5f;
            float end = start + step;
            float crest = (float) Math.sin(phase + i * 0.9f) * dp(8f);
            path.cubicTo(start + step * 0.18f, y - dp(15f) + crest,
                    middle - step * 0.18f, y + dp(15f) - crest,
                    middle, y);
            path.cubicTo(middle + step * 0.18f, y - dp(15f) - crest,
                    end - step * 0.18f, y + dp(15f) + crest,
                    end, y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(2.4f));
        paint.setColor(withAlpha(color, (int) (210 * alpha)));
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawLeaf(Canvas canvas, float x, float y, float size, float alpha, int color, float rotation) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        Path path = new Path();
        path.moveTo(0f, -size);
        path.cubicTo(size * 0.9f, -size * 0.65f, size * 0.78f, size * 0.55f, 0f, size);
        path.cubicTo(-size * 0.78f, size * 0.55f, -size * 0.9f, -size * 0.65f, 0f, -size);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color, (int) (190 * alpha)));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(Color.argb((int) (115 * alpha), 255, 255, 255));
        canvas.drawLine(0f, -size * 0.75f, 0f, size * 0.78f, paint);
        canvas.restore();
    }

    private void drawSnowflake(Canvas canvas, float x, float y, float size, float alpha, float rotation) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(Color.argb((int) (205 * alpha), 230, 250, 255));
        for (int i = 0; i < 6; i++) {
            canvas.rotate(60f);
            canvas.drawLine(0f, -size, 0f, size, paint);
            canvas.drawLine(0f, -size * 0.7f, size * 0.22f, -size * 0.48f, paint);
            canvas.drawLine(0f, -size * 0.7f, -size * 0.22f, -size * 0.48f, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        canvas.restore();
    }

    private void drawPetal(Canvas canvas, float x, float y, float size, float alpha, int color, float rotation) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        paint.setShader(new RadialGradient(0f, 0f, size * 1.6f,
                withAlpha(color, (int) (210 * alpha)),
                withAlpha(Color.WHITE, (int) (30 * alpha)),
                Shader.TileMode.CLAMP));
        Path path = new Path();
        path.moveTo(0f, -size);
        path.cubicTo(size * 0.95f, -size * 0.35f, size * 0.55f, size * 0.85f, 0f, size);
        path.cubicTo(-size * 0.55f, size * 0.85f, -size * 0.95f, -size * 0.35f, 0f, -size);
        canvas.drawPath(path, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void drawBitmapCentered(Canvas canvas, Bitmap bitmap, float cx, float cy, float targetSize, float alpha, float rotation) {
        if (bitmap == null || alpha <= 0f) {
            return;
        }
        float scale = targetSize / Math.max(bitmap.getWidth(), bitmap.getHeight());
        matrix.reset();
        matrix.postTranslate(-bitmap.getWidth() * 0.5f, -bitmap.getHeight() * 0.5f);
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        matrix.postTranslate(cx, cy);
        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        canvas.drawBitmap(bitmap, matrix, paint);
        paint.setAlpha(255);
    }

    private void drawBitmapCover(Canvas canvas, Bitmap bitmap, int fallbackColor) {
        if (fallbackColor != Color.TRANSPARENT) {
            canvas.drawColor(fallbackColor);
        }
        if (bitmap == null) {
            return;
        }
        float scale = Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        RectF dst = new RectF(
                (getWidth() - width) * 0.5f,
                (getHeight() - height) * 0.5f,
                (getWidth() + width) * 0.5f,
                (getHeight() + height) * 0.5f);
        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, null, dst, paint);
    }

    private Bitmap[] loadSprites(String[] names) {
        Bitmap[] sprites = new Bitmap[names.length];
        for (int i = 0; i < names.length; i++) {
            sprites[i] = loadBitmap(names[i]);
        }
        return sprites;
    }

    private static String[] note4ChargeSpringNames() {
        return concat(new String[] {
                        "note4seasonal/charging/spring_charging_25p.png",
                        "note4seasonal/charging/spring_charging_50p.png",
                        "note4seasonal/charging/spring_charging_75p.png",
                        "note4seasonal/charging/spring_charging_99p.png",
                        "note4seasonal/charging/spring_charging_100p.png"
                },
                numberedAssetNames("note4seasonal/charging/spring_particle_", 1, 4, 2, ".png"),
                new String[] {
                        "note4seasonal/charging/flower_01.png",
                        "note4seasonal/charging/flower_02.png",
                        "note4seasonal/charging/flower_03.png"
                });
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
                numberedAssetNames("note4seasonal/charging/autumn_particle_", 1, 4, 2, ".png"),
                new String[] {
                        "note4seasonal/charging/leaf_01.png",
                        "note4seasonal/charging/leaf_02.png",
                        "note4seasonal/charging/leaf_03.png",
                        "note4seasonal/charging/leaf_04.png"
                });
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

    private static String[] yearPetalNames() {
        String[] names = new String[27];
        for (int i = 0; i < names.length; i++) {
            String extension = i == 0 || i == 26 ? ".png" : ".qmg";
            names[i] = "note4festival/full/Dreamy/res/drawable-xxxhdpi-v4/year_petal" + zeroPad(i, 2) + extension;
        }
        return names;
    }

    private static String[] coloredPaperCandyNames() {
        String[] names = new String[22];
        for (int i = 0; i < names.length; i++) {
            int value = i + 1;
            String extension = value >= 13 && value <= 17 || value >= 19 ? ".png" : ".qmg";
            names[i] = "note4festival/full/ColoredPaper/res/drawable-xxxhdpi-v4/lockscreen_birthday_candy_" + twoDigit(value) + extension;
        }
        return names;
    }

    private static String[] fireworkNames(String group) {
        String[] names = new String[12];
        for (int i = 0; i < names.length; i++) {
            int value = i + 1;
            String extension;
            if ("a".equals(group)) {
                extension = value >= 9 ? ".png" : ".qmg";
            } else if ("b".equals(group)) {
                extension = value <= 5 || value >= 11 ? ".png" : ".qmg";
            } else {
                extension = value >= 5 && value <= 10 ? ".png" : ".qmg";
            }
            names[i] = "note4festival/full/Dreamy/res/drawable-xxxhdpi-v4/firework_" + group + "_" + twoDigit(value) + extension;
        }
        return names;
    }

    private static String[] dreamyLanternNames() {
        String[] names = new String[42];
        int index = 0;
        for (int lantern = 1; lantern <= 7; lantern++) {
            for (int frame = 1; frame <= 6; frame++) {
                names[index++] = "note4festival/full/Dreamy/res/drawable-xxxhdpi-v4/lantern" + lantern + "_" + twoDigit(frame) + ".qmg";
            }
        }
        return names;
    }

    private static String[] numberedAssetNames(String prefix, int first, int last, int digits, String extension) {
        String[] names = new String[last - first + 1];
        for (int i = 0; i < names.length; i++) {
            names[i] = prefix + zeroPad(first + i, digits) + extension;
        }
        return names;
    }

    private static String[] numberedPlainAssetNames(String prefix, int first, int last, String extension) {
        String[] names = new String[last - first + 1];
        for (int i = 0; i < names.length; i++) {
            names[i] = prefix + (first + i) + extension;
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

    private static String[] chargerFrameNames(String prefix, boolean wireless) {
        String[] names = new String[79];
        for (int i = 0; i < names.length; i++) {
            String extension;
            if (wireless) {
                if (i == 0 || i >= 67) {
                    extension = ".pio";
                } else if (i == 1 || i == 66) {
                    extension = ".qio";
                } else {
                    extension = ".qmg";
                }
            } else if (i == 0 || i == 77 || i == 78) {
                extension = ".pio";
            } else if (i == 1 || i == 2 || i == 3 || i == 76) {
                extension = ".qio";
            } else {
                extension = ".qmg";
            }
            names[i] = prefix + twoDigit(i) + extension;
        }
        return names;
    }

    private static String twoDigit(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static String zeroPad(int value, int digits) {
        String result = String.valueOf(value);
        while (result.length() < digits) {
            result = "0" + result;
        }
        return result;
    }

    private Bitmap loadBitmap(String name) {
        Bitmap bitmap = loadBitmapExact(name);
        if (bitmap == null && name.indexOf('/') >= 0) {
            bitmap = loadBitmapExact(name.replace('/', '\\'));
        }
        return bitmap;
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

    private StockFestivalScene loadStockFestivalScene(String apkAssetName, String packageName, String xmlName, String drawableAssetBase) {
        XmlResourceParser parser = null;
        try {
            File apk = copyAssetToCache(apkAssetName);
            if (apk == null) {
                return null;
            }
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            int cookie = ((Integer) addAssetPath.invoke(assetManager, apk.getAbsolutePath())).intValue();
            if (cookie == 0) {
                return null;
            }
            Resources resources = new Resources(assetManager,
                    getResources().getDisplayMetrics(),
                    getResources().getConfiguration());
            int xmlId = resources.getIdentifier(xmlName, "xml", packageName);
            if (xmlId == 0) {
                return null;
            }
            parser = resources.getXml(xmlId);
            ArrayList<StockFestivalSprite> sprites = new ArrayList<StockFestivalSprite>();
            StockFestivalSprite current = null;
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("view".equals(tag)) {
                        current = new StockFestivalSprite();
                        current.name = stringAttr(parser, "img");
                        current.x = floatAttr(parser, "x", 0f);
                        current.y = floatAttr(parser, "y", 0f);
                        current.bitmap = loadFestivalBitmap(resources, packageName, current.name, drawableAssetBase);
                        sprites.add(current);
                    } else if (current != null) {
                        if ("translateX".equals(tag)) {
                            current.hasTranslateX = true;
                            current.translateX = StockFestivalAnim.from(parser, "fromXDelta", "toXDelta", 0f);
                        } else if ("translateY".equals(tag)) {
                            current.hasTranslateY = true;
                            current.translateY = StockFestivalAnim.from(parser, "fromYDelta", "toYDelta", 0f);
                        } else if ("rotate".equals(tag)) {
                            current.rotate = StockFestivalAnim.from(parser, "fromDegrees", "toDegrees", 0f);
                        } else if ("alpha".equals(tag)) {
                            current.alpha = StockFestivalAnim.from(parser, "fromAlpha", "toAlpha", 1f);
                        } else if ("scaleX".equals(tag)) {
                            current.scaleX = StockFestivalAnim.from(parser, "fromXScale", "toXScale", 1f);
                        } else if ("scaleY".equals(tag)) {
                            current.scaleY = StockFestivalAnim.from(parser, "fromYScale", "toYScale", 1f);
                        } else if ("image".equals(tag)) {
                            current.sequenceFrames = loadFestivalSequence(resources, packageName,
                                    stringAttr(parser, "ImageResource"),
                                    intAttr(parser, "length", 0),
                                    drawableAssetBase);
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && "view".equals(parser.getName())) {
                    current = null;
                }
            }
            StockFestivalScene scene = new StockFestivalScene(sprites);
            return scene.hasAnimatedBitmap() ? scene : null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private File copyAssetToCache(String assetName) {
        InputStream input = null;
        FileOutputStream output = null;
        try {
            File dir = new File(getContext().getCacheDir(), "festival_assets");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            String fileName = assetName.substring(assetName.lastIndexOf('/') + 1);
            File outFile = new File(dir, fileName);
            input = openAssetWithFallback(assetName);
            if (input == null) {
                return null;
            }
            output = new FileOutputStream(outFile, false);
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return outFile;
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(output);
            closeQuietly(input);
        }
    }

    private InputStream openAssetWithFallback(String name) {
        try {
            return getContext().getAssets().open(name);
        } catch (IOException e) {
            if (name.indexOf('/') < 0) {
                return null;
            }
            try {
                return getContext().getAssets().open(name.replace('/', '\\'));
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    private void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private Bitmap loadFestivalBitmap(Resources resources, String packageName, String name, String drawableAssetBase) {
        if (name == null || name.length() == 0) {
            return null;
        }
        Bitmap bitmap = loadFestivalBitmapFromAssets(drawableAssetBase, name);
        if (bitmap != null) {
            return bitmap;
        }
        int id = resources.getIdentifier(name, "drawable", packageName);
        if (id == 0) {
            return null;
        }
        InputStream input = null;
        try {
            input = resources.openRawResource(id);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            return BitmapFactory.decodeStream(input, null, options);
        } catch (Throwable t) {
            return null;
        } finally {
            closeQuietly(input);
        }
    }

    private Bitmap loadFestivalBitmapFromAssets(String drawableAssetBase, String name) {
        if (drawableAssetBase == null || name == null || name.length() == 0) {
            return null;
        }
        Bitmap bitmap = loadBitmap(drawableAssetBase + "/" + name + ".png");
        if (bitmap != null) {
            return bitmap;
        }
        bitmap = loadBitmap(drawableAssetBase + "/" + name + ".qmg");
        if (bitmap != null) {
            return bitmap;
        }
        bitmap = loadBitmap(drawableAssetBase + "/" + name + ".pio");
        if (bitmap != null) {
            return bitmap;
        }
        return loadBitmap(drawableAssetBase + "/" + name + ".qio");
    }

    private Bitmap[] loadFestivalSequence(Resources resources, String packageName, String baseName, int length, String drawableAssetBase) {
        if (baseName == null || length <= 0) {
            return null;
        }
        int digitStart = baseName.length();
        while (digitStart > 0 && Character.isDigit(baseName.charAt(digitStart - 1))) {
            digitStart--;
        }
        String prefix = baseName.substring(0, digitStart);
        int digits = Math.max(1, baseName.length() - digitStart);
        Bitmap[] frames = new Bitmap[length];
        for (int i = 0; i < length; i++) {
            frames[i] = loadFestivalBitmap(resources, packageName, prefix + zeroPad(i, digits), drawableAssetBase);
        }
        return frames;
    }

    private static String stringAttr(XmlPullParser parser, String name) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (name.equals(parser.getAttributeName(i))) {
                return parser.getAttributeValue(i);
            }
        }
        return null;
    }

    private static int intAttr(XmlPullParser parser, String name, int fallback) {
        String value = stringAttr(parser, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float floatAttr(XmlPullParser parser, String name, float fallback) {
        String value = stringAttr(parser, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Bitmap randomSprite(Bitmap[] sprites) {
        if (sprites == null || sprites.length == 0) {
            return null;
        }
        for (int i = 0; i < sprites.length; i++) {
            Bitmap sprite = sprites[random.nextInt(sprites.length)];
            if (sprite != null) {
                return sprite;
            }
        }
        return null;
    }

    private Bitmap spriteAt(Bitmap[] sprites, int index) {
        if (sprites == null || sprites.length == 0) {
            return null;
        }
        for (int i = 0; i < sprites.length; i++) {
            Bitmap sprite = sprites[Math.abs(index + i) % sprites.length];
            if (sprite != null) {
                return sprite;
            }
        }
        return null;
    }

    private int festivalColor(int index) {
        int[] colors = new int[] {
                Color.rgb(255, 234, 140),
                Color.rgb(255, 152, 156),
                Color.rgb(150, 218, 255),
                Color.rgb(178, 255, 190),
                Color.rgb(226, 182, 255)
        };
        return colors[Math.abs(index) % colors.length];
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00ffffff);
    }

    private int watercolorColor(Ripple ripple, int salt) {
        float hue = (ripple.x * 0.07f + ripple.y * 0.03f + salt * 42f) % 360f;
        return Color.HSVToColor(new float[] { hue, 0.42f, 1f });
    }

    private int tileColor(int index) {
        if (effectType == EFFECT_BRILLIANT_CUT) {
            int[] colors = new int[] {
                    Color.rgb(235, 250, 255),
                    Color.rgb(170, 225, 255),
                    Color.rgb(255, 238, 185),
                    Color.rgb(205, 185, 255)
            };
            return colors[Math.abs(index) % colors.length];
        }
        if (effectType == EFFECT_GEOMETRIC_MOSAIC) {
            int[] colors = new int[] {
                    Color.rgb(58, 205, 220),
                    Color.rgb(255, 198, 80),
                    Color.rgb(245, 90, 120),
                    Color.rgb(95, 220, 150),
                    Color.rgb(155, 125, 255)
            };
            return colors[Math.abs(index) % colors.length];
        }
        int[] colors = new int[] {
                Color.rgb(70, 190, 235),
                Color.rgb(235, 115, 190),
                Color.rgb(255, 210, 85),
                Color.rgb(120, 230, 170)
        };
        return colors[Math.abs(index) % colors.length];
    }

    private void drawTile(Canvas canvas, float x, float y, float size, int color, float rotation) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        RectF rect = new RectF(-size * 0.5f, -size * 0.5f, size * 0.5f, size * 0.5f);
        canvas.drawRoundRect(rect, dp(2f), dp(2f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(Color.argb(Math.min(210, Color.alpha(color)), 255, 255, 255));
        canvas.drawRoundRect(rect, dp(2f), dp(2f), paint);
        canvas.restore();
    }

    private void drawTriangle(Canvas canvas, float x, float y, float size, int color, float rotation) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        Path path = new Path();
        path.moveTo(0f, -size * 0.62f);
        path.lineTo(size * 0.6f, size * 0.46f);
        path.lineTo(-size * 0.6f, size * 0.46f);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(Color.argb(Math.min(180, Color.alpha(color)), 255, 255, 255));
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private void drawDiamond(Canvas canvas, float x, float y, float size, int color, float rotation) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        Path path = new Path();
        path.moveTo(0f, -size);
        path.lineTo(size * 0.72f, 0f);
        path.lineTo(0f, size);
        path.lineTo(-size * 0.72f, 0f);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                -size * 0.18f,
                -size * 0.22f,
                size * 1.3f,
                Color.argb(Math.min(255, Color.alpha(color) + 20), 255, 255, 255),
                color,
                Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(Color.argb(Math.min(190, Color.alpha(color)), 255, 255, 255));
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private boolean isRippleDrivenEffect() {
        return effectType == EFFECT_S3_MASS_RIPPLE
                || effectType == EFFECT_NOTE5_WATER_DROPLET
                || effectType == EFFECT_DEBUG_TOUCH
                || effectType == EFFECT_WATERCOLOR
                || effectType == EFFECT_RIPPLE_INK
                || effectType == EFFECT_INDIGO_RIPPLE
                || effectType == EFFECT_BRILLIANT_RING;
    }

    private boolean isTileDrivenEffect() {
        return effectType == EFFECT_ABSTRACT_TILES
                || effectType == EFFECT_GEOMETRIC_MOSAIC
                || effectType == EFFECT_BRILLIANT_CUT;
    }

    private boolean isChargerEffect() {
        return effectType == EFFECT_NOTE4_CHARGER_CABLE || effectType == EFFECT_NOTE4_CHARGER_WIRELESS;
    }

    private boolean isChargeDoodleEffect() {
        return effectType == EFFECT_NOTE4_CHARGE_SPRING
                || effectType == EFFECT_NOTE4_CHARGE_SUMMER
                || effectType == EFFECT_NOTE4_CHARGE_AUTUMN
                || effectType == EFFECT_NOTE4_CHARGE_WINTER;
    }

    private boolean isContinuousEffect() {
        return isChargerEffect()
                || effectType == EFFECT_NOTE4_SEASONAL_CHARGING
                || isChargeDoodleEffect();
    }

    private boolean isAutoAnimatedEffect() {
        return isContinuousEffect()
                || effectType == EFFECT_NOTE4_COLORED_PAPER
                || effectType == EFFECT_NOTE4_DREAMY_FESTIVAL;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static final class StockFestivalScene {
        final ArrayList<StockFestivalSprite> sprites;

        StockFestivalScene(ArrayList<StockFestivalSprite> sprites) {
            this.sprites = sprites;
        }

        boolean hasAnimatedBitmap() {
            for (StockFestivalSprite sprite : sprites) {
                if (sprite == null || "lockscreen_birthday_bg".equals(sprite.name)) {
                    continue;
                }
                if (sprite.bitmap != null || sprite.hasSequenceBitmap()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class StockFestivalSprite {
        String name;
        Bitmap bitmap;
        Bitmap[] sequenceFrames;
        float x;
        float y;
        boolean hasTranslateX;
        boolean hasTranslateY;
        StockFestivalAnim translateX = StockFestivalAnim.constant(0f);
        StockFestivalAnim translateY = StockFestivalAnim.constant(0f);
        StockFestivalAnim rotate = StockFestivalAnim.constant(0f);
        StockFestivalAnim alpha = StockFestivalAnim.constant(1f);
        StockFestivalAnim scaleX = StockFestivalAnim.constant(1f);
        StockFestivalAnim scaleY = StockFestivalAnim.constant(1f);

        Bitmap bitmapAt(long elapsed) {
            if (sequenceFrames != null && sequenceFrames.length > 0) {
                int index = (int) ((elapsed / 46L) % sequenceFrames.length);
                Bitmap frame = sequenceFrames[index];
                if (frame != null) {
                    return frame;
                }
            }
            return bitmap;
        }

        boolean hasSequenceBitmap() {
            if (sequenceFrames == null) {
                return false;
            }
            for (Bitmap frame : sequenceFrames) {
                if (frame != null) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class StockFestivalAnim {
        final float from;
        final float to;
        final long duration;
        final int repeatCount;
        final int repeatMode;
        final long delay;

        StockFestivalAnim(float from, float to, long duration, int repeatCount, int repeatMode, long delay) {
            this.from = from;
            this.to = to;
            this.duration = duration;
            this.repeatCount = repeatCount;
            this.repeatMode = repeatMode;
            this.delay = delay;
        }

        static StockFestivalAnim constant(float value) {
            return new StockFestivalAnim(value, value, 0L, 0, 1, 0L);
        }

        static StockFestivalAnim from(XmlPullParser parser, String fromName, String toName, float fallback) {
            return new StockFestivalAnim(
                    floatAttr(parser, fromName, fallback),
                    floatAttr(parser, toName, fallback),
                    intAttr(parser, "duration", 0),
                    intAttr(parser, "repeatCount", 0),
                    intAttr(parser, "repeatMode", 1),
                    intAttr(parser, "delay", 0));
        }

        float valueAt(long elapsed) {
            if (duration <= 0L) {
                return to;
            }
            long adjusted = elapsed - delay;
            if (adjusted <= 0L) {
                return from;
            }
            long iteration = adjusted / duration;
            if (repeatCount >= 0 && iteration > repeatCount) {
                return to;
            }
            float phase = (adjusted % duration) / (float) duration;
            if (repeatMode == 2 && (iteration & 1L) == 1L) {
                phase = 1f - phase;
            }
            float eased = (float) (Math.cos((phase + 1f) * Math.PI) * 0.5f + 0.5f);
            return from + (to - from) * eased;
        }
    }

    private static final class Ripple {
        final float x;
        final float y;
        final long startedAt;
        final float duration;
        final boolean unlock;

        Ripple(float x, float y, long startedAt, float duration, boolean unlock) {
            this.x = x;
            this.y = y;
            this.startedAt = startedAt;
            this.duration = duration;
            this.unlock = unlock;
        }
    }

    private static final class Particle {
        final float x;
        final float y;
        final float vx;
        final float vy;
        final long startedAt;
        final float duration;
        final int color;
        final Bitmap sprite;
        final boolean unlock;
        final float spin;

        Particle(float x, float y, float vx, float vy, long startedAt, float duration, int color, Bitmap sprite, boolean unlock) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.startedAt = startedAt;
            this.duration = duration;
            this.color = color;
            this.sprite = sprite;
            this.unlock = unlock;
            this.spin = (x * 0.11f + y * 0.07f + vx * 0.01f) % 360f;
        }
    }
}
