package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

/** App-owned Canvas port of the LG G4 Circle Mosaic renderer. */
public final class LgCircleMosaicEffectView extends View implements UnlockEffectRenderer,
        BackgroundSourceRenderer, SecondaryBackgroundSourceRenderer, UnlockEffectReadiness {
    private final LgCircleMosaicScene scene = new LgCircleMosaicScene();
    private final LgCircleMosaicScene.Frame frame = new LgCircleMosaicScene.Frame();
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint underlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix sourceMatrix = new Matrix();
    private final int[] location = new int[2];
    private final DisplayMetrics screenMetrics = new DisplayMetrics();
    private final SoundPool soundPool;
    private final int touchdownSound;
    private final int unlockSound;
    private final Set<Integer> loadedSounds = new HashSet<Integer>();

    private Bitmap primary;
    private Bitmap secondary;
    private Bitmap blurredPrimary;
    private BitmapShader blurredShader;
    private BitmapShader secondaryShader;
    private boolean destroyed;
    private boolean firstFrameDrawn;
    private int pendingSound;
    private ReadinessListener readinessListener;

    public LgCircleMosaicEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        soundPool = new SoundPool.Builder().setMaxStreams(2)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context)).build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(SoundPool pool, int id, int status) {
                if (destroyed || pool != soundPool) return;
                if (status == 0) loadedSounds.add(id);
                if (pendingSound == id) {
                    pendingSound = 0;
                    if (status == 0) playSound(id);
                }
            }
        });
        touchdownSound = soundPool.load(context, R.raw.lg_circlemosaic_touchdown, 1);
        unlockSound = soundPool.load(context, R.raw.lg_circlemosaic_unlock, 1);
    }

    @Override public View asView() { return this; }
    @Override public String effectName() { return "Circle Mosaic"; }
    public boolean supportsHighFrameRate() { return true; }
    public boolean supportsSpeedMultiplier() { return false; }

    private boolean ready() {
        return !destroyed && getWidth() > 0 && getHeight() > 0
                && primary != null && blurredPrimary != null && secondary != null;
    }

    @Override public void beginGesture(float x, float y) {
        if (!ready()) return;
        updateGeometry();
        scene.begin(x - location[0], y - location[1], SystemClock.uptimeMillis());
        playSound(touchdownSound);
        postInvalidateOnAnimation();
    }

    @Override public void updateGesture(float x, float y) {
        if (!ready() || !scene.gestureActive()) return;
        getLocationOnScreen(location);
        scene.move(x - location[0], y - location[1], SystemClock.uptimeMillis());
        postInvalidateOnAnimation();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || !scene.gestureActive()) return;
        pendingSound = 0;
        scene.finish(completed, SystemClock.uptimeMillis());
        if (completed) playSound(unlockSound);
        postInvalidateOnAnimation();
    }

    @Override public void cancelGesture() { finishGesture(false); }

    @Override public void resetEffect() {
        pendingSound = 0;
        scene.reset();
        invalidate();
    }

    @Override public void warmUp() {
        if (primary != null) primary.prepareToDraw();
        if (blurredPrimary != null) blurredPrimary.prepareToDraw();
        if (secondary != null) secondary.prepareToDraw();
        invalidate();
    }

    /** Stock has no standalone hint animation; leaving this empty prevents a stuck hint frame. */
    @Override public void showUnlockAffordance(Rect bounds, long delayMs) { }

    @Override public boolean hasBackgroundSourceBitmap() { return primary != null; }
    @Override public boolean hasSecondaryBackgroundSourceBitmap() { return secondary != null; }

    @Override public void setBackgroundSourceBitmap(Bitmap source, String name) {
        Bitmap copy = ownedCopy(source);
        if (copy == null) return;
        primary = copy;
        blurredPrimary = createDonorBlur(copy);
        blurredShader = blurredPrimary == null ? null
                : new BitmapShader(blurredPrimary, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        blurPaint.setShader(blurredShader);
        sourcesChanged();
    }

    @Override public void setSecondaryBackgroundSourceBitmap(Bitmap source, String name) {
        Bitmap copy = ownedCopy(source);
        if (copy == null) return;
        secondary = copy;
        secondaryShader = new BitmapShader(copy, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        underlayPaint.setShader(secondaryShader);
        sourcesChanged();
    }

    @Override public void clearBackgroundSourceBitmap() {
        blurPaint.setShader(null);
        primary = blurredPrimary = null;
        blurredShader = null;
        sourcesChanged();
        resetEffect();
    }

    @Override public void clearSecondaryBackgroundSourceBitmap() {
        underlayPaint.setShader(null);
        secondary = null;
        secondaryShader = null;
        sourcesChanged();
        resetEffect();
    }

    private Bitmap ownedCopy(Bitmap source) {
        if (destroyed || source == null || source.isRecycled()) return null;
        try { return source.copy(Bitmap.Config.ARGB_8888, false); }
        catch (OutOfMemoryError ignored) { return null; }
    }

    /** RenderScript radius 20 on a quarter-size source, reproduced with three box passes. */
    private static Bitmap createDonorBlur(Bitmap source) {
        try {
            int width = Math.max(1, source.getWidth() / 4);
            int height = Math.max(1, source.getHeight() / 4);
            Bitmap small = Bitmap.createScaledBitmap(source, width, height, true);
            int[] pixels = new int[width * height];
            int[] work = new int[pixels.length];
            small.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int pass = 0; pass < 3; pass++) {
                boxBlurHorizontal(pixels, work, width, height, 5);
                boxBlurVertical(work, pixels, width, height, 5);
            }
            small.setPixels(pixels, 0, width, 0, 0, width, height);
            return small;
        } catch (OutOfMemoryError ignored) {
            return null;
        }
    }

    private static void boxBlurHorizontal(int[] source, int[] target,
            int width, int height, int radius) {
        int span = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            long a = 0, r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int c = source[row + clampInt(i, 0, width - 1)];
                a += c >>> 24; r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255;
            }
            for (int x = 0; x < width; x++) {
                target[row + x] = ((int) (a / span) << 24) | ((int) (r / span) << 16)
                        | ((int) (g / span) << 8) | (int) (b / span);
                int remove = source[row + clampInt(x - radius, 0, width - 1)];
                int add = source[row + clampInt(x + radius + 1, 0, width - 1)];
                a += (add >>> 24) - (remove >>> 24);
                r += (add >> 16 & 255) - (remove >> 16 & 255);
                g += (add >> 8 & 255) - (remove >> 8 & 255);
                b += (add & 255) - (remove & 255);
            }
        }
    }

    private static void boxBlurVertical(int[] source, int[] target,
            int width, int height, int radius) {
        int span = radius * 2 + 1;
        for (int x = 0; x < width; x++) {
            long a = 0, r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int c = source[clampInt(i, 0, height - 1) * width + x];
                a += c >>> 24; r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255;
            }
            for (int y = 0; y < height; y++) {
                target[y * width + x] = ((int) (a / span) << 24) | ((int) (r / span) << 16)
                        | ((int) (g / span) << 8) | (int) (b / span);
                int remove = source[clampInt(y - radius, 0, height - 1) * width + x];
                int add = source[clampInt(y + radius + 1, 0, height - 1) * width + x];
                a += (add >>> 24) - (remove >>> 24);
                r += (add >> 16 & 255) - (remove >> 16 & 255);
                g += (add >> 8 & 255) - (remove >> 8 & 255);
                b += (add & 255) - (remove & 255);
            }
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void sourcesChanged() {
        firstFrameDrawn = false;
        updateGeometry();
        invalidate();
        notifyReadiness();
    }

    private void updateGeometry() {
        if (destroyed) return;
        getLocationOnScreen(location);
        screenMetrics.setTo(getResources().getDisplayMetrics());
        if (getDisplay() != null) getDisplay().getRealMetrics(screenMetrics);
        scene.configure(getWidth(), getHeight(), screenMetrics.density, screenMetrics.xdpi);
        mapSource(blurredPrimary, blurredShader);
        mapSource(secondary, secondaryShader);
    }

    private void mapSource(Bitmap bitmap, BitmapShader shader) {
        if (bitmap == null || shader == null) return;
        sourceMatrix.setScale(Math.max(1, screenMetrics.widthPixels) / (float) bitmap.getWidth(),
                Math.max(1, screenMetrics.heightPixels) / (float) bitmap.getHeight());
        sourceMatrix.postTranslate(-location[0], -location[1]);
        shader.setLocalMatrix(sourceMatrix);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateGeometry();
        notifyReadiness();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) resetEffect();
        updateGeometry();
        firstFrameDrawn = false;
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
        scene.sample(SystemClock.uptimeMillis(), frame);
        if (!frame.visible) return;
        if (frame.fullUnderlay) {
            underlayPaint.setAlpha(255);
            canvas.drawRect(0, 0, getWidth(), getHeight(), underlayPaint);
        } else {
            drawMosaic(canvas, frame);
        }
        if (frame.running) postInvalidateOnAnimation();
    }

    private void drawMosaic(Canvas canvas, LgCircleMosaicScene.Frame f) {
        float cellWidth = getWidth() / (float) LgCircleMosaicScene.COLUMNS;
        float cellHeight = getHeight() / (float) LgCircleMosaicScene.ROWS;
        float blurRadius = LgCircleMosaicScene.cellBlurRadius(f);
        blurPaint.setAlpha(Math.round(255f * f.alpha));
        underlayPaint.setAlpha(255);
        for (int row = 0; row < LgCircleMosaicScene.ROWS; row++) {
            float top = row * cellHeight;
            float bottom = row == LgCircleMosaicScene.ROWS - 1
                    ? getHeight() : (row + 1) * cellHeight;
            for (int column = 0; column < LgCircleMosaicScene.COLUMNS; column++) {
                float left = column * cellWidth;
                float right = column == LgCircleMosaicScene.COLUMNS - 1
                        ? getWidth() : (column + 1) * cellWidth;
                float centerX = (left + right) * .5f;
                float centerY = (top + bottom) * .5f;
                // The donor can render a mathematically non-zero blur spot in every cell
                // because its normal and blurred textures come from the exact same frame.
                // In an overlay that would expose the tiny live/cached-frame delta as a
                // visible 15x25 grid. Limit painting to the cells reached by the circular
                // wave: nine cells at the S23 touchdown geometry, then progressively more.
                if (!LgCircleMosaicScene.cellAffected(f, centerX, centerY)) continue;
                float revealRadius = LgCircleMosaicScene.cellRevealRadius(f, centerX, centerY);
                int save = canvas.save();
                canvas.clipRect(left, top, right, bottom);
                if (blurRadius > 0f && f.alpha > 0f) {
                    canvas.drawCircle(centerX, centerY, blurRadius, blurPaint);
                }
                if (revealRadius > 0f) {
                    canvas.drawCircle(centerX, centerY, revealRadius, underlayPaint);
                }
                canvas.restoreToCount(save);
            }
        }
    }

    private void playSound(int id) {
        if (destroyed || id == 0 || !OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) return;
        if (!loadedSounds.contains(id)) { pendingSound = id; return; }
        soundPool.play(id, 1f, 1f, 1, 0, 1f);
    }

    @Override public int getReadinessState() {
        if (destroyed) return STATE_FAILED;
        if (!isAttachedToWindow()) return STATE_CONSTRUCTED;
        if (!isLaidOut()) return STATE_ATTACHED;
        if (!ready()) return STATE_SURFACE_READY;
        return firstFrameDrawn ? STATE_FIRST_FRAME_READY : STATE_RESOURCES_READY;
    }

    @Override public String getReadinessDetail() {
        return effectName() + ": lockscreen=" + hasBackgroundSourceBitmap()
                + " lastScreen=" + hasSecondaryBackgroundSourceBitmap()
                + " blur=" + (blurredPrimary != null)
                + " firstFrame=" + firstFrameDrawn + " state=" + scene.state();
    }

    @Override public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    private void notifyReadiness() {
        if (destroyed || readinessListener == null) return;
        post(new Runnable() { @Override public void run() {
            if (!destroyed && readinessListener != null) readinessListener.onReadinessChanged();
        }});
    }

    @Override public void destroy() {
        if (destroyed) return;
        resetEffect();
        destroyed = true;
        soundPool.setOnLoadCompleteListener(null);
        soundPool.release();
        loadedSounds.clear();
        blurPaint.setShader(null);
        underlayPaint.setShader(null);
        primary = secondary = blurredPrimary = null;
        blurredShader = secondaryShader = null;
        readinessListener = null;
    }
}
