package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * App-owned Canvas port of the S5 Popping Colours renderer.
 *
 * <p>The supplied lockscreen bitmap is sampled only as a colour map. It is never
 * drawn, so pixels outside the particles remain transparent.</p>
 */
final class PoppingColoursArm64EffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "ChargingS5Popping64";

    private static final int CREATED_DOTS_AMOUNT_MOVE = 3;
    private static final int CREATED_DOTS_AMOUNT_DOWN = 15;
    private static final int CREATED_DOTS_AMOUNT_AFFORDANCE = 50;
    private static final int PARTICLE_POOL_SIZE = 250;
    private static final int PARTICLE_MAX_ALIVE = 150;
    private static final int PARTICLE_UNLOCK_SPEED = 5;
    private static final int DRAWING_MARGIN_PX = 11;
    private static final long DRAWING_DELAY_MS = 16L;

    private static final int DRAG_SOUND_COUNT_START_POINT = 40;
    private static final int DRAG_SOUND_COUNT_INTERVAL = 60;
    private static final long DRAG_SOUND_MOVE_SAMPLE_MS = 16L;
    private static final float TOUCH_SOUND_VOLUME = 0.3f;

    private final List<Particle> particlePool =
            new ArrayList<Particle>(PARTICLE_POOL_SIZE);
    private final List<Particle> aliveParticles =
            new ArrayList<Particle>(PARTICLE_MAX_ALIVE);
    private final float[] hsvOrigin = new float[3];
    private final float[] hsvTemp = new float[3];
    private final UnlockEffectReadinessCoordinator readiness =
            new UnlockEffectReadinessCoordinator(this, "Popping Colours ARM64");

    private final SoundPool soundPool;
    private final int tapSound;
    private final int dragSound;
    private final int unlockSound;

    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private boolean externalColorSource;
    private String backgroundSource = "none";

    private boolean destroyed;
    private boolean gestureActive;
    private boolean drawing;
    private boolean canvasReady;
    private int nextParticleIndex = -1;
    private int drawingLeft;
    private int drawingTop;
    private int drawingRight = 1;
    private int drawingBottom = 1;
    private int dragSoundCount;
    private long lastDragSoundMoveAtMs;
    private float lastGestureX;
    private float lastGestureY;
    private float lastAddedX;
    private float lastAddedY;
    private int lastAddedColor;

    private float pendingAffordanceX;
    private float pendingAffordanceY;
    private int pendingAffordanceColor;

    private final Runnable drawingRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !drawing) {
                return;
            }
            if (isAvailableDrawingRect()) {
                invalidate(
                        drawingLeft - DRAWING_MARGIN_PX,
                        drawingTop - DRAWING_MARGIN_PX,
                        drawingRight + DRAWING_MARGIN_PX,
                        drawingBottom + DRAWING_MARGIN_PX);
            } else {
                invalidate(0, 0, 1, 1);
            }
            if (drawing && !destroyed) {
                postDelayed(this, DRAWING_DELAY_MS);
            }
        }
    };

    private final Runnable affordanceRunnable = new Runnable() {
        @Override
        public void run() {
            if (!destroyed) {
                addDots(CREATED_DOTS_AMOUNT_AFFORDANCE,
                        pendingAffordanceX,
                        pendingAffordanceY,
                        pendingAffordanceColor);
            }
        }
    };

    PoppingColoursArm64EffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        float particleRatio = Math.min(width, height) / 1080f;
        for (int index = 0; index < PARTICLE_POOL_SIZE; index++) {
            particlePool.add(new Particle(particleRatio));
        }

        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(getContext()))
                .build();
        tapSound = soundPool.load(context, R.raw.particle_tap, 1);
        dragSound = soundPool.load(context, R.raw.particle_drag, 1);
        unlockSound = soundPool.load(context, R.raw.particle_unlock, 1);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S5 popping colours";
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
        gestureActive = true;
        lastGestureX = screenX;
        lastGestureY = screenY;
        dragSoundCount = DRAG_SOUND_COUNT_START_POINT;
        lastDragSoundMoveAtMs = SystemClock.uptimeMillis();
        play(tapSound, TOUCH_SOUND_VOLUME);
        addDots(CREATED_DOTS_AMOUNT_DOWN, screenX, screenY,
                getColor(screenX, screenY));
        Log.i(TAG, "popping colours ARM64 begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY));
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
        lastGestureX = screenX;
        lastGestureY = screenY;
        long now = SystemClock.uptimeMillis();
        if (now - lastDragSoundMoveAtMs >= DRAG_SOUND_MOVE_SAMPLE_MS) {
            lastDragSoundMoveAtMs = now;
            dragSoundCount++;
            if (dragSoundCount >= DRAG_SOUND_COUNT_INTERVAL) {
                play(dragSound, TOUCH_SOUND_VOLUME);
                dragSoundCount = 0;
            }
        }
        addDots(CREATED_DOTS_AMOUNT_MOVE, screenX, screenY,
                getColor(screenX, screenY));
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        lastDragSoundMoveAtMs = 0L;
        if (completed) {
            unlockDots();
            play(unlockSound, 1f);
        }
        Log.i(TAG, "popping colours ARM64 finish completed=" + completed
                + " x=" + Math.round(lastGestureX)
                + " y=" + Math.round(lastGestureY));
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        lastDragSoundMoveAtMs = 0L;
        Log.i(TAG, "popping colours ARM64 cancel");
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        dragSoundCount = 0;
        lastDragSoundMoveAtMs = 0L;
        removeCallbacks(affordanceRunnable);
        stopDrawing();
        aliveParticles.clear();
        drawingLeft = 0;
        drawingTop = 0;
        drawingRight = 1;
        drawingBottom = 1;
        invalidate();
    }

    @Override
    public void warmUp() {
        if (!destroyed) {
            getBackgroundBitmap();
            invalidate();
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
        pendingAffordanceColor = getColor(pendingAffordanceX, pendingAffordanceY);
        removeCallbacks(affordanceRunnable);
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
        Log.i(TAG, "popping colours ARM64 affordance queued delayMs=" + startDelayMs
                + " rect=" + rect.left + "," + rect.top + ","
                + rect.right + "," + rect.bottom);
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalColorSource
                && backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == Math.max(1, getRenderWidth())
                && backgroundBitmap.getHeight() == Math.max(1, getRenderHeight());
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        int width = Math.max(1, getRenderWidth());
        int height = Math.max(1, getRenderHeight());
        boolean borrow = BackgroundSourceRenderer.canBorrowSharedCache(
                source, sourceName, width, height);
        Bitmap next = borrow ? source : createCenterCropBitmap(source, width, height);
        next.prepareToDraw();
        releaseBackgroundBitmap();
        backgroundBitmap = next;
        ownsBackgroundBitmap = !borrow;
        externalColorSource = true;
        backgroundSource = sourceName == null ? "external" : sourceName;
        Log.i(TAG, "colour map replaced source=" + backgroundSource
                + " size=" + next.getWidth() + "x" + next.getHeight());
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        externalColorSource = false;
        backgroundSource = "none";
        releaseBackgroundBitmap();
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
        soundPool.release();
        externalColorSource = false;
        backgroundSource = "none";
        releaseBackgroundBitmap();
        readiness.destroyed();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        canvasReady = false;
        readiness.attachCanvas();
        warmUp();
        if (!aliveParticles.isEmpty()) {
            startDrawing();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopDrawing();
        canvasReady = false;
        readiness.detached("canvas detached");
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!canvasReady) {
            canvasReady = true;
            readiness.canvasWarmFrameDrawn();
        }
        if (aliveParticles.isEmpty()) {
            stopDrawing();
            return;
        }

        for (int index = 0; index < aliveParticles.size(); index++) {
            Particle particle = aliveParticles.get(index);
            if (!particle.alive) {
                aliveParticles.remove(index--);
                continue;
            }

            particle.move();
            particle.draw(canvas);
            int left = particle.left();
            int top = particle.top();
            int right = particle.right();
            int bottom = particle.bottom();
            drawingLeft = index == 0 ? left : Math.min(drawingLeft, left);
            drawingTop = index == 0 ? top : Math.min(drawingTop, top);
            drawingRight = index == 0 ? right : Math.max(drawingRight, right);
            drawingBottom = index == 0 ? bottom : Math.max(drawingBottom, bottom);
        }
    }

    private void addDots(int amount, float x, float y, int color) {
        if (destroyed || aliveParticles.size() + amount > PARTICLE_MAX_ALIVE) {
            return;
        }
        lastAddedX = x;
        lastAddedY = y;
        lastAddedColor = color;

        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsvOrigin);
        for (int index = 0; index < amount; index++) {
            hsvTemp[0] = hsvOrigin[0];
            hsvTemp[1] = (float) (hsvOrigin[1] * (1.0 - 0.7 * Math.random()));
            hsvTemp[2] = (float) (hsvOrigin[2]
                    + (1f - hsvOrigin[2]) * Math.random());
            int particleColor = Color.HSVToColor(hsvTemp);
            Particle particle = getNextParticle();
            particle.initialize(x, y, particleColor);
            aliveParticles.add(particle);
        }
        startDrawing();
    }

    private Particle getNextParticle() {
        if (nextParticleIndex >= PARTICLE_POOL_SIZE - 1) {
            nextParticleIndex = 0;
        } else {
            nextParticleIndex++;
        }
        return particlePool.get(nextParticleIndex);
    }

    private void unlockDots() {
        addDots(PARTICLE_MAX_ALIVE - aliveParticles.size(),
                lastAddedX, lastAddedY, lastAddedColor);
        for (Particle particle : aliveParticles) {
            particle.unlock(PARTICLE_UNLOCK_SPEED);
        }
    }

    private void startDrawing() {
        if (drawing || destroyed) {
            return;
        }
        drawing = true;
        postDelayed(drawingRunnable, DRAWING_DELAY_MS);
    }

    private void stopDrawing() {
        drawing = false;
        removeCallbacks(drawingRunnable);
    }

    private boolean isAvailableDrawingRect() {
        return drawingLeft < drawingRight
                && drawingTop < drawingBottom
                && drawingLeft < getWidth()
                && drawingRight > 0
                && drawingTop < getHeight()
                && drawingBottom > 0;
    }

    private int getColor(float x, float y) {
        int color = 0x00ffffff;
        float stageWidth = Math.max(1f, getRenderWidth());
        float stageHeight = Math.max(1f, getRenderHeight());
        if (x <= 0f || x > stageWidth || y <= 0f || y > stageHeight) {
            return color;
        }

        Bitmap bitmap = getBackgroundBitmap();
        if (bitmap == null || bitmap.isRecycled()) {
            return color;
        }
        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();
        float bitmapRatio = bitmapWidth / (float) bitmapHeight;
        float stageRatio = stageWidth / stageHeight;
        float ratio;
        int offsetX = 0;
        int offsetY = 0;
        if (bitmapRatio > stageRatio) {
            ratio = bitmapHeight / stageHeight;
            float resizedStageWidth = stageWidth * ratio;
            offsetX = (int) ((bitmapWidth - resizedStageWidth) / 2f);
        } else {
            ratio = bitmapWidth / stageWidth;
            float resizedStageHeight = stageHeight * ratio;
            offsetY = (int) ((bitmapHeight - resizedStageHeight) / 2f);
        }
        int sampleX = clamp(offsetX + (int) (x * ratio), 0, bitmapWidth - 1);
        int sampleY = clamp(offsetY + (int) (y * ratio), 0, bitmapHeight - 1);
        try {
            return bitmap.getPixel(sampleX, sampleY);
        } catch (IllegalArgumentException ignored) {
            return color;
        }
    }

    private Bitmap getBackgroundBitmap() {
        int width = Math.max(1, getRenderWidth());
        int height = Math.max(1, getRenderHeight());
        if (backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == width
                && backgroundBitmap.getHeight() == height) {
            return backgroundBitmap;
        }
        releaseBackgroundBitmap();
        backgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        backgroundBitmap.eraseColor(Color.WHITE);
        backgroundBitmap.prepareToDraw();
        ownsBackgroundBitmap = true;
        externalColorSource = false;
        backgroundSource = "white_fallback";
        return backgroundBitmap;
    }

    private Bitmap createCenterCropBitmap(Bitmap source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float sourceRatio = source.getWidth() / (float) source.getHeight();
        float outputRatio = width / (float) height;
        Rect sourceRect;
        if (sourceRatio > outputRatio) {
            int sourceWidth = Math.max(1, Math.round(source.getHeight() * outputRatio));
            int left = Math.max(0, (source.getWidth() - sourceWidth) / 2);
            sourceRect = new Rect(left, 0,
                    Math.min(source.getWidth(), left + sourceWidth), source.getHeight());
        } else {
            int sourceHeight = Math.max(1, Math.round(source.getWidth() / outputRatio));
            int top = Math.max(0, (source.getHeight() - sourceHeight) / 2);
            sourceRect = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + sourceHeight));
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG
                | Paint.DITHER_FLAG);
        new Canvas(output).drawBitmap(source, sourceRect,
                new Rect(0, 0, width, height), paint);
        return output;
    }

    private void releaseBackgroundBitmap() {
        if (ownsBackgroundBitmap && backgroundBitmap != null
                && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = null;
        ownsBackgroundBitmap = false;
    }

    private Rect safeRect(Rect rect) {
        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            return new Rect(rect);
        }
        return new Rect(0, 0, Math.max(1, getRenderWidth()),
                Math.max(1, getRenderHeight()));
    }

    private int getRenderWidth() {
        int width = getWidth();
        return width > 0 ? width
                : Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        int height = getHeight();
        return height > 0 ? height
                : Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    private void play(int soundId, float volume) {
        if (!destroyed && soundId != 0
                && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(soundId, volume, volume, 0, 0, 1f);
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Particle {
        private static final float GRAVITY = 4f;
        private static final float MAX_SPEED = 7f;
        private static final float SMALL_RADIUS = 25f;
        private static final float BIG_RADIUS = 66f;
        private static final int DOT_ALPHA = 200;
        private static final int RANDOM_TOTAL = 20;

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float gravity;
        final float maxSpeed;
        final int smallRadius;
        final int bigRadius;

        boolean alive;
        boolean unlocked;
        int life;
        int radius;
        float x;
        float y;
        float dx;
        float dy;

        Particle(float ratio) {
            gravity = GRAVITY * ratio;
            maxSpeed = MAX_SPEED * ratio;
            smallRadius = (int) (SMALL_RADIUS * ratio);
            bigRadius = (int) (BIG_RADIUS * ratio);
        }

        void initialize(float initialX, float initialY, int color) {
            Random random = new Random();
            life = random.nextInt(100) + 50;
            float randomTotal = random.nextInt(RANDOM_TOTAL) / (float) RANDOM_TOTAL;
            radius = (int) ((random.nextInt(10) == 0 ? bigRadius : smallRadius)
                    * randomTotal);
            dx = (float) (maxSpeed * Math.random() - maxSpeed / 2f);
            dy = (float) (maxSpeed * Math.random() - maxSpeed / 2f - gravity);
            alive = true;
            unlocked = false;
            x = initialX;
            y = initialY;
            paint.setColor(color);
        }

        void move() {
            x += dx;
            y += dy;
        }

        void draw(Canvas canvas) {
            int alphaStartFrame = unlocked ? 20 : 30;
            int alpha = life < alphaStartFrame
                    ? DOT_ALPHA * life / alphaStartFrame
                    : DOT_ALPHA;
            paint.setAlpha(alpha);
            canvas.drawCircle(x, y, radius, paint);
            if (life <= 0) {
                alive = false;
            } else {
                life--;
            }
        }

        void unlock(float speed) {
            unlocked = true;
            dx *= speed;
            dy *= speed;
            life = 19;
        }

        int left() {
            return (int) (x - radius);
        }

        int top() {
            return (int) (y - radius);
        }

        int right() {
            return (int) (x + radius);
        }

        int bottom() {
            return (int) (y + radius);
        }
    }
}
