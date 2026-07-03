package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;

public class WatercolorEffectView extends View
        implements UnlockEffectRenderer, BackgroundSourceRenderer {
    private static final String TAG = "ChargingWatercolor";
    private static final long MARK_LIFETIME_MS = 920L;
    private static final long UNLOCK_MARK_LIFETIME_MS = 760L;
    private static final float MOVE_SPACING_DP = 18f;
    private static final int MAX_MARKS = 64;
    private static final int FALLBACK_COLOR = 0xfff5fbff;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG
            | Paint.DITHER_FLAG);
    private final Paint tubePaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG
            | Paint.DITHER_FLAG);
    private final Rect clipBounds = new Rect();
    private final Rect dirtyRect = new Rect();
    private final RectF dst = new RectF();
    private final ArrayList<Mark> marks = new ArrayList<Mark>();
    private final Bitmap[] masks = new Bitmap[3];
    private final Bitmap tube;
    private final Bitmap noise;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;
    private final float moveSpacingPx;
    private final float dirtyPadPx;

    private Bitmap backgroundBitmap;
    private String backgroundSource = "white_fallback";
    private boolean destroyed;
    private boolean gestureActive;
    private float lastX;
    private float lastY;
    private float lastMarkX;
    private float lastMarkY;
    private int nextMask;

    public WatercolorEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        masks[0] = decode(R.drawable.watercolor_mask1);
        masks[1] = decode(R.drawable.watercolor_mask2);
        masks[2] = decode(R.drawable.watercolor_mask3);
        tube = decode(R.drawable.waterbrush_tube);
        noise = decode(R.drawable.watercolor_noise);
        moveSpacingPx = dp(MOVE_SPACING_DP);
        dirtyPadPx = dp(42f);

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tapSound = soundPool.load(context, R.raw.ve_watercolour_tap, 1);
        unlockSound = soundPool.load(context, R.raw.ve_watercolour_unlock, 1);
        Log.i(TAG, "Watercolor transparent port renderer loaded");
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "N4 Watercolor WIP";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        gestureActive = true;
        lastX = screenX;
        lastY = screenY;
        lastMarkX = screenX;
        lastMarkY = screenY;
        addMark(screenX, screenY, 0f, 0f, 1.05f, true, MARK_LIFETIME_MS);
        play(tapSound);
        invalidateActiveRegion();
        Log.i(TAG, "watercolor port begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY)
                + " bg=" + backgroundSource);
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

        float dx = screenX - lastX;
        float dy = screenY - lastY;
        float fromLastMarkX = screenX - lastMarkX;
        float fromLastMarkY = screenY - lastMarkY;
        float distanceFromLastMark =
                (float) Math.hypot(fromLastMarkX, fromLastMarkY);
        if (distanceFromLastMark >= moveSpacingPx) {
            int steps = Math.max(1, (int) (distanceFromLastMark / moveSpacingPx));
            for (int i = 1; i <= steps; i++) {
                float t = i / (float) steps;
                float x = lastMarkX + fromLastMarkX * t;
                float y = lastMarkY + fromLastMarkY * t;
                addMark(x, y, dx, dy, 0.72f, false, MARK_LIFETIME_MS);
            }
            lastMarkX = screenX;
            lastMarkY = screenY;
        }
        lastX = screenX;
        lastY = screenY;
        invalidateActiveRegion();
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive || destroyed) {
            return;
        }
        gestureActive = false;
        if (completed) {
            addMark(lastX, lastY, 0f, -1f, 1.35f, true, UNLOCK_MARK_LIFETIME_MS);
            addMark(lastX - dp(34f), lastY + dp(16f), -1f, 0f, 0.82f, true,
                    UNLOCK_MARK_LIFETIME_MS);
            addMark(lastX + dp(38f), lastY - dp(10f), 1f, 0f, 0.78f, true,
                    UNLOCK_MARK_LIFETIME_MS);
            play(unlockSound);
        }
        invalidateActiveRegion();
        Log.i(TAG, "watercolor port finish completed=" + completed
                + " x=" + Math.round(lastX)
                + " y=" + Math.round(lastY));
    }

    @Override
    public void cancelGesture() {
        gestureActive = false;
        invalidateActiveRegion();
    }

    @Override
    public void resetEffect() {
        gestureActive = false;
        marks.clear();
        invalidate();
    }

    @Override
    public void warmUp() {
        invalidateActiveRegion();
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        // S4-only screen-on hint; Watercolor is still a separate WIP effect.
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == getRenderWidth()
                && backgroundBitmap.getHeight() == getRenderHeight();
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        Bitmap next = createCenterCropBitmap(source, getRenderWidth(), getRenderHeight());
        next.prepareToDraw();
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = next;
        backgroundSource = sourceName == null ? "external" : sourceName;
        Log.i(TAG, "watercolor background color map ready source=" + backgroundSource
                + " size=" + backgroundBitmap.getWidth()
                + "x" + backgroundBitmap.getHeight());
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = null;
        backgroundSource = "white_fallback";
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        soundPool.release();
        for (int i = 0; i < masks.length; i++) {
            if (masks[i] != null && !masks[i].isRecycled()) {
                masks[i].recycle();
            }
        }
        if (tube != null && !tube.isRecycled()) {
            tube.recycle();
        }
        if (noise != null && !noise.isRecycled()) {
            noise.recycle();
        }
        clearBackgroundSourceBitmap();
        marks.clear();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (destroyed) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        canvas.getClipBounds(clipBounds);
        Iterator<Mark> iterator = marks.iterator();
        while (iterator.hasNext()) {
            Mark mark = iterator.next();
            float t = (now - mark.birthMs) / (float) mark.durationMs;
            if (t >= 1f) {
                iterator.remove();
                continue;
            }
            markBounds(mark, dirtyRect);
            if (Rect.intersects(clipBounds, dirtyRect)) {
                drawMark(canvas, mark, t);
            }
        }
        if (!marks.isEmpty() || gestureActive) {
            invalidateActiveRegion();
        }
    }

    private void drawMark(Canvas canvas, Mark mark, float t) {
        Bitmap mask = masks[mark.maskIndex];
        if (mask == null || mask.isRecycled()) {
            return;
        }

        float appear = Math.min(1f, t / 0.14f);
        float fadeStart = mark.burst ? 0.62f : 0.70f;
        float fade = t < fadeStart ? 1f : Math.max(0f, 1f - (t - fadeStart) / (1f - fadeStart));
        float nativeAlpha = fade * fade * fade * fade * 0.95f;
        float size = mark.sizePx * (0.74f + quintOut(t) * 0.52f);
        float alpha = mark.alpha * appear * nativeAlpha;
        float wobbleX = (float) Math.sin(t * Math.PI * 1.9f + mark.phase) * mark.driftPx;
        float wobbleY = (float) Math.cos(t * Math.PI * 1.6f + mark.phase) * mark.driftPx;
        float centerX = mark.x + wobbleX;
        float centerY = mark.y + wobbleY;

        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.rotate(mark.angle + mark.rotationDrift * t);
        canvas.scale(mark.stretchX, mark.stretchY);
        dst.set(-size * 0.5f, -size * 0.5f, size * 0.5f, size * 0.5f);

        paint.setAlpha(clampAlpha(alpha));
        paint.setColorFilter(new PorterDuffColorFilter(mark.color, PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(mask, null, dst, paint);

        if (tube != null && !tube.isRecycled()) {
            float tubeSize = size * (0.36f + 0.16f * (1f - t));
            dst.set(-tubeSize * 0.5f, -tubeSize * 0.5f,
                    tubeSize * 0.5f, tubeSize * 0.5f);
            tubePaint.setAlpha(clampAlpha(alpha * 0.42f));
            tubePaint.setColorFilter(new PorterDuffColorFilter(mark.highlightColor,
                    PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(tube, null, dst, tubePaint);
            tubePaint.setColorFilter(null);
        }
        paint.setColorFilter(null);
        canvas.restore();
    }

    private void addMark(float x, float y, float velocityX, float velocityY,
            float strength, boolean burst, long durationMs) {
        int maskIndex = nextMask % masks.length;
        int sampled = sampleBackgroundColor(x, y);
        int color = watercolorColor(sampled, nextMask);
        int highlight = mixColor(color, Color.WHITE, 0.34f);
        float minSide = Math.max(1f, Math.min(getRenderWidth(), getRenderHeight()));
        float baseSize = minSide * (burst ? 0.42f : 0.28f) * strength;
        float speed = (float) Math.hypot(velocityX, velocityY);
        float stretch = Math.min(1.34f, 1f + speed / Math.max(1f, minSide) * 1.7f);
        float angle = speed > 0.1f
                ? (float) Math.toDegrees(Math.atan2(velocityY, velocityX))
                : ((nextMask * 47) % 360);
        float alpha = burst ? 226f : 184f;
        float noiseValue = sampleNoise(x, y, nextMask);
        float drift = (4f + noiseValue * 12f) * getResources().getDisplayMetrics().density;

        marks.add(new Mark(
                x,
                y,
                Math.max(minSide * 0.16f, baseSize),
                maskIndex,
                color,
                highlight,
                alpha * (0.82f + noiseValue * 0.18f),
                angle,
                ((nextMask * 61) % 80) - 40f,
                drift,
                nextMask * 0.73f,
                stretch,
                burst ? 1.03f : 0.92f,
                burst,
                durationMs));
        nextMask++;
        while (marks.size() > MAX_MARKS) {
            marks.remove(0);
        }
    }

    private int sampleBackgroundColor(float x, float y) {
        if (backgroundBitmap == null || backgroundBitmap.isRecycled()) {
            return FALLBACK_COLOR;
        }
        int width = backgroundBitmap.getWidth();
        int height = backgroundBitmap.getHeight();
        int cx = clamp(Math.round(x), 0, width - 1);
        int cy = clamp(Math.round(y), 0, height - 1);
        int radius = Math.max(2, Math.round(dp(7f)));
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (int yy = Math.max(0, cy - radius); yy <= Math.min(height - 1, cy + radius);
                yy += Math.max(1, radius / 2)) {
            for (int xx = Math.max(0, cx - radius); xx <= Math.min(width - 1, cx + radius);
                    xx += Math.max(1, radius / 2)) {
                int color = backgroundBitmap.getPixel(xx, yy);
                red += Color.red(color);
                green += Color.green(color);
                blue += Color.blue(color);
                count++;
            }
        }
        if (count == 0) {
            return FALLBACK_COLOR;
        }
        return Color.rgb(red / count, green / count, blue / count);
    }

    private int watercolorColor(int source, int variant) {
        float r = Color.red(source) / 255f;
        float g = Color.green(source) / 255f;
        float b = Color.blue(source) / 255f;
        float luminance = (float) Math.sqrt(r * r * 0.32f + g * g * 0.46f + b * b * 0.22f);
        float saturation = 1.42f;
        float brightness = 1.13f;
        r = clamp01((luminance + (r - luminance) * saturation) * brightness);
        g = clamp01((luminance + (g - luminance) * saturation) * brightness);
        b = clamp01((luminance + (b - luminance) * saturation) * brightness);

        int tint;
        switch (variant % 4) {
            case 0:
                tint = Color.rgb(226, 246, 255);
                break;
            case 1:
                tint = Color.rgb(205, 232, 255);
                break;
            case 2:
                tint = Color.rgb(236, 240, 255);
                break;
            default:
                tint = Color.rgb(216, 252, 244);
                break;
        }
        return mixColor(Color.rgb(Math.round(r * 255f), Math.round(g * 255f),
                Math.round(b * 255f)), tint, 0.24f);
    }

    private float sampleNoise(float x, float y, int seed) {
        if (noise == null || noise.isRecycled()) {
            return ((seed * 37) & 255) / 255f;
        }
        int xx = Math.abs((Math.round(x) + seed * 43) % noise.getWidth());
        int yy = Math.abs((Math.round(y) + seed * 29) % noise.getHeight());
        int color = noise.getPixel(xx, yy);
        return (Color.red(color) + Color.green(color) + Color.blue(color)) / (255f * 3f);
    }

    private void invalidateActiveRegion() {
        if (marks.isEmpty()) {
            if (gestureActive) {
                postInvalidateOnAnimation();
            }
            return;
        }
        Rect union = null;
        for (int i = 0; i < marks.size(); i++) {
            markBounds(marks.get(i), dirtyRect);
            if (union == null) {
                union = new Rect(dirtyRect);
            } else {
                union.union(dirtyRect);
            }
        }
        if (union == null) {
            invalidate();
            return;
        }
        union.intersect(0, 0, getRenderWidth(), getRenderHeight());
        if (union.isEmpty()) {
            invalidate();
        } else {
            postInvalidateOnAnimation(union.left, union.top, union.right, union.bottom);
        }
    }

    private void markBounds(Mark mark, Rect out) {
        float radius = mark.sizePx * Math.max(mark.stretchX, mark.stretchY) * 0.72f
                + mark.driftPx + dirtyPadPx;
        out.set(
                Math.max(0, (int) Math.floor(mark.x - radius)),
                Math.max(0, (int) Math.floor(mark.y - radius)),
                Math.min(getRenderWidth(), (int) Math.ceil(mark.x + radius)),
                Math.min(getRenderHeight(), (int) Math.ceil(mark.y + radius)));
    }

    private Bitmap decode(int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId, options);
        if (bitmap != null) {
            bitmap.prepareToDraw();
        }
        return bitmap;
    }

    private Bitmap createCenterCropBitmap(Bitmap source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float srcRatio = source.getWidth() / (float) source.getHeight();
        float dstRatio = width / (float) height;
        Rect src;
        if (srcRatio > dstRatio) {
            int srcWidth = Math.max(1, Math.round(source.getHeight() * dstRatio));
            int left = Math.max(0, (source.getWidth() - srcWidth) / 2);
            src = new Rect(left, 0, Math.min(source.getWidth(), left + srcWidth),
                    source.getHeight());
        } else {
            int srcHeight = Math.max(1, Math.round(source.getWidth() / dstRatio));
            int top = Math.max(0, (source.getHeight() - srcHeight) / 2);
            src = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + srcHeight));
        }
        Bitmap.Config config = Bitmap.Config.ARGB_8888;
        Bitmap mutable = Bitmap.createBitmap(width, height, config);
        Canvas canvas = new Canvas(mutable);
        Paint scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG
                | Paint.DITHER_FLAG);
        canvas.drawBitmap(source, src, new Rect(0, 0, width, height), scalePaint);
        return mutable;
    }

    private int getRenderWidth() {
        int width = getWidth();
        if (width > 0) {
            return width;
        }
        return Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        int height = getHeight();
        if (height > 0) {
            return height;
        }
        return Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    private int clampAlpha(float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha)));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float quintOut(float t) {
        float inv = 1f - clamp01(t);
        return 1f - inv * inv * inv * inv * inv;
    }

    private int mixColor(int from, int to, float amount) {
        float t = clamp01(amount);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void play(int soundId) {
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private static final class Mark {
        final float x;
        final float y;
        final float sizePx;
        final int maskIndex;
        final int color;
        final int highlightColor;
        final float alpha;
        final float angle;
        final float rotationDrift;
        final float driftPx;
        final float phase;
        final float stretchX;
        final float stretchY;
        final boolean burst;
        final long durationMs;
        final long birthMs;

        Mark(float x, float y, float sizePx, int maskIndex, int color,
                int highlightColor, float alpha, float angle, float rotationDrift,
                float driftPx, float phase, float stretchX, float stretchY,
                boolean burst, long durationMs) {
            this.x = x;
            this.y = y;
            this.sizePx = sizePx;
            this.maskIndex = maskIndex;
            this.color = color;
            this.highlightColor = highlightColor;
            this.alpha = alpha;
            this.angle = angle;
            this.rotationDrift = rotationDrift;
            this.driftPx = driftPx;
            this.phase = phase;
            this.stretchX = stretchX;
            this.stretchY = stretchY;
            this.burst = burst;
            this.durationMs = durationMs;
            this.birthMs = SystemClock.uptimeMillis();
        }
    }
}
