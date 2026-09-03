package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

/**
 * App-owned port of OptimusDev/XLocker Vector 1.1's shader geometry and clock.
 * Canvas/HWUI draws the recovered 2D primitives without the donor GLSurfaceView lifecycle.
 * The Last Screen is fixed below the opening; the regular lock capture is used ONLY in the
 * tinted annulus. Nothing paints the untouched exterior, and neither cache replaces the other.
 */
public final class LgVectorEffectView extends View implements UnlockEffectRenderer,
        BackgroundSourceRenderer, SecondaryBackgroundSourceRenderer, UnlockEffectReadiness {
    private final LgVectorScene scene = new LgVectorScene();
    private final LgVectorScene.Frame frame = new LgVectorScene.Frame();
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Matrix sourceMatrix = new Matrix();
    private final int[] location = new int[2];
    private final DisplayMetrics screenMetrics = new DisplayMetrics();
    private final ColorMatrixColorFilter[] paletteFilters = new ColorMatrixColorFilter[4];
    private Bitmap primary, secondary;
    private BitmapShader primaryShader, secondaryShader;
    private Bitmap lineTexture, tapLineTexture;
    private boolean destroyed, firstFrameDrawn;
    private ReadinessListener readinessListener;
    private float hintX, hintY;
    private final SoundPool soundPool;
    private final int touchdownSound, unlockSound;
    private final Set<Integer> loadedSounds = new HashSet<Integer>();
    private int pendingSound;
    private final Runnable hintStart = new Runnable() {
        @Override public void run() {
            if (!ready() || scene.gestureActive()) return;
            scene.begin(hintX, hintY, SystemClock.uptimeMillis());
            // An affordance owns its own terminal clock; it never waits for a physical UP.
            scene.finish(false, SystemClock.uptimeMillis());
            postInvalidateOnAnimation();
        }
    };

    public LgVectorEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(false);
        lineTexture = BitmapFactory.decodeResource(getResources(), R.drawable.lg_vector_line);
        tapLineTexture = BitmapFactory.decodeResource(getResources(), R.drawable.lg_vector_tab_line);
        for (int i = 0; i < paletteFilters.length; i++) {
            int color = LgVectorScene.PALETTES[i][0];
            // vector_main_fs: mix(lockscreen, uColor, .5), independently of the source alpha.
            paletteFilters[i] = new ColorMatrixColorFilter(new float[] {
                .5f, 0, 0, 0, Color.red(color) * .5f,
                0, .5f, 0, 0, Color.green(color) * .5f,
                0, 0, .5f, 0, Color.blue(color) * .5f,
                0, 0, 0, 1, 0
            });
        }
        soundPool = new SoundPool.Builder().setMaxStreams(2)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context)).build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(SoundPool pool, int id, int status) {
                // Creation and completion are delivered on the UI looper; never replay a stale
                // touchdown after reset, detach or a later unlock.
                if (destroyed || pool != soundPool) return;
                if (status == 0) loadedSounds.add(id);
                if (pendingSound == id) {
                    pendingSound = 0;
                    if (status == 0) playSound(id);
                }
            }
        });
        touchdownSound = soundPool.load(context, R.raw.lg_vector_touchdown, 1);
        unlockSound = soundPool.load(context, R.raw.lg_vector_unlock, 1);
    }

    @Override public View asView() { return this; }
    @Override public String effectName() { return "G2 Vector"; }

    private boolean ready() {
        return !destroyed && getWidth() > 0 && getHeight() > 0
                && hasBackgroundSourceBitmap() && hasSecondaryBackgroundSourceBitmap();
    }

    @Override public void beginGesture(float x, float y) {
        if (!ready()) return;
        removeCallbacks(hintStart);
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
        removeCallbacks(hintStart);
        pendingSound = 0;
        scene.reset();
        invalidate();
    }
    @Override public void warmUp() {
        if (destroyed) return;
        if (primary != null) primary.prepareToDraw();
        if (secondary != null) secondary.prepareToDraw();
        if (lineTexture != null) lineTexture.prepareToDraw();
        if (tapLineTexture != null) tapLineTexture.prepareToDraw();
        invalidate();
    }
    @Override public void showUnlockAffordance(Rect bounds, long delayMs) {
        if (!ready()) return;
        updateGeometry();
        hintX = bounds != null && !bounds.isEmpty()
                ? bounds.exactCenterX() - location[0] : getWidth() * .5f;
        hintY = bounds != null && !bounds.isEmpty()
                ? bounds.exactCenterY() - location[1] : getHeight() * .5f;
        removeCallbacks(hintStart);
        postDelayed(hintStart, Math.max(0, delayMs));
    }

    @Override public boolean hasBackgroundSourceBitmap() { return primary != null; }
    @Override public boolean hasSecondaryBackgroundSourceBitmap() { return secondary != null; }
    @Override public void setBackgroundSourceBitmap(Bitmap source, String name) {
        Bitmap copy = ownedCopy(source);
        if (copy == null) return;
        primary = copy;
        primaryShader = new BitmapShader(copy, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bandPaint.setShader(primaryShader);
        sourcesChanged();
    }
    @Override public void setSecondaryBackgroundSourceBitmap(Bitmap source, String name) {
        Bitmap copy = ownedCopy(source);
        if (copy == null) return;
        secondary = copy;
        secondaryShader = new BitmapShader(copy, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        imagePaint.setShader(secondaryShader);
        sourcesChanged();
    }
    @Override public void clearBackgroundSourceBitmap() {
        bandPaint.setShader(null);
        primaryShader = null;
        primary = null;
        sourcesChanged();
        resetEffect();
    }
    @Override public void clearSecondaryBackgroundSourceBitmap() {
        imagePaint.setShader(null);
        secondaryShader = null;
        secondary = null;
        sourcesChanged();
        resetEffect();
    }
    private Bitmap ownedCopy(Bitmap source) {
        if (destroyed || source == null || source.isRecycled()) return null;
        try { return source.copy(Bitmap.Config.ARGB_8888, false); }
        catch (OutOfMemoryError ignored) { return null; }
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
        scene.configure(getWidth(), getHeight(), screenMetrics.density);
        mapSource(primary, primaryShader);
        mapSource(secondary, secondaryShader);
    }
    private void mapSource(Bitmap bitmap, BitmapShader shader) {
        if (bitmap == null || shader == null) return;
        // Both captures use display-space UVs, never the moving ring's bounding rectangle.
        // Account for an overlay inset without stretching the 2400 px capture into 2273 px.
        sourceMatrix.setScale(Math.max(1, screenMetrics.widthPixels) / (float) bitmap.getWidth(),
                Math.max(1, screenMetrics.heightPixels) / (float) bitmap.getHeight());
        sourceMatrix.postTranslate(-location[0], -location[1]);
        shader.setLocalMatrix(sourceMatrix);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateGeometry();
        notifyReadiness();
        invalidate();
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw != w || oldh != h) resetEffect();
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
            canvas.drawRect(0, 0, getWidth(), getHeight(), imagePaint);
        } else if (frame.tap) {
            drawTap(canvas, frame);
        } else {
            drawOpening(canvas, frame);
            drawRings(canvas, frame);
        }
        if (frame.running) postInvalidateOnAnimation();
    }

    private void drawOpening(Canvas canvas, LgVectorScene.Frame f) {
        canvas.drawCircle(f.x, f.y, f.outerRadius, imagePaint);
        if (f.outerRadius <= f.innerRadius) return;
        mapSource(primary, primaryShader);
        // Preserve the donor's subtle lock-capture scaling inside the coloured annulus only.
        // The Last Screen (secondaryShader) and the real lockscreen outside never move.
        sourceMatrix.postScale(f.primaryScale, f.primaryScale,
                screenMetrics.widthPixels * .5f - location[0],
                screenMetrics.heightPixels * .5f - location[1]);
        primaryShader.setLocalMatrix(sourceMatrix);
        annulus(f.x, f.y, f.innerRadius, f.outerRadius);
        bandPaint.setColorFilter(paletteFilters[f.palette]);
        bandPaint.setAlpha(Math.round(255 * f.bandAlpha));
        canvas.drawPath(path, bandPaint);
    }

    private void drawTap(Canvas canvas, LgVectorScene.Frame f) {
        float radius = 1.3f * f.minRadius * f.tapProgress;
        float bw = 4.666667f * f.density;
        float limit = f.minRadius + bw * 3.5f;
        shapePaint.setColor(LgVectorScene.PALETTES[f.palette][0]);
        shapePaint.setAlpha(128);
        shapePaint.setStyle(Paint.Style.FILL);
        if (radius <= f.minRadius) {
            canvas.drawCircle(f.x, f.y, Math.min(limit, radius + bw), shapePaint);
        }
        drawAnnulus(canvas, f.x, f.y, radius + bw, Math.min(limit, radius + 2 * bw));
        drawAnnulus(canvas, f.x, f.y, radius + 3 * bw, Math.min(limit, radius + 4 * bw));
        if (tapLineTexture == null) return;
        path.reset();
        path.addCircle(f.x, f.y, 1.8f * f.minRadius, Path.Direction.CW);
        int clip = canvas.save();
        canvas.clipPath(path);
        float halfWidth = tapLineTexture.getWidth() * f.density / 4f;
        float halfHeight = tapLineTexture.getHeight() * f.density / 4f;
        spritePaint.setAlpha(128);
        for (int i = 0; i < 12; i++) {
            float t = LgVectorScene.normalize(0, .5f, f.tapProgress - (.3f + i * .01666667f));
            if (t <= 0) continue;
            float distance = (1.3f + .7f * t) * f.minRadius;
            int save = canvas.save();
            canvas.rotate(i * 30f, f.x, f.y);
            rect.set(f.x - halfWidth, f.y - distance - halfHeight,
                    f.x + halfWidth, f.y - distance + halfHeight);
            canvas.drawBitmap(tapLineTexture, null, rect, spritePaint);
            canvas.restoreToCount(save);
        }
        canvas.restoreToCount(clip);
    }

    private void drawRings(Canvas canvas, LgVectorScene.Frame f) {
        float gap = Math.max(0, f.outerRadius - f.innerRadius);
        float arcOuter = f.outerRadius + gap / 8f;
        float whiteInner = f.innerRadius;
        float whiteFraction, whiteAlpha;
        if (f.outerRadius > f.boundary) {
            whiteFraction = 1f - LgVectorScene.normalize(f.boundary, scene.fullRadius(), f.outerRadius);
            whiteAlpha = .4f;
        } else {
            whiteFraction = LgVectorScene.normalize(f.minRadius, f.boundary, f.outerRadius);
            whiteAlpha = Math.min(.4f, whiteFraction);
            whiteInner += gap / 3f;
        }
        // Both donor strips enumerate sin(theta),cos(theta) with DECREASING theta.
        // Screen-space starts are 150-90=60 degrees and 44-90=-46 degrees.
        drawArcStrip(canvas, f.x, f.y, whiteInner, arcOuter, 150f, whiteFraction,
                Color.WHITE, whiteAlpha);
        float fraction = f.outerRadius > f.boundary
                ? LgVectorScene.normalize(f.boundary, scene.fullRadius(), f.distance)
                : 1f - LgVectorScene.normalize(0, f.boundary, f.distance);
        float coloredAlpha = f.outerRadius > f.boundary ? .5f
                : Math.min(.5f, LgVectorScene.normalize(0, f.boundary, f.distance));
        drawArcStrip(canvas, f.x, f.y, f.innerRadius, arcOuter, 44f, fraction,
                LgVectorScene.PALETTES[f.palette][1], coloredAlpha);
        drawParticles(canvas, f);
        drawDragLines(canvas, f);
    }

    private void drawArcStrip(Canvas canvas, float x, float y, float inner, float outer,
            float start, float fraction, int color, float alpha) {
        int vertices = (int) (200 * LgVectorScene.clamp(fraction, 0, 1));
        if (vertices < 2 || outer <= inner || alpha <= 0) return;
        path.reset();
        for (int i = 0; i < vertices; i++) {
            double angle = Math.toRadians(start - i * 1.8);
            float px = x + (float) Math.sin(angle) * outer;
            float py = y - (float) Math.cos(angle) * outer;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        for (int i = vertices - 1; i >= 0; i--) {
            double angle = Math.toRadians(start - i * 1.8);
            path.lineTo(x + (float) Math.sin(angle) * inner, y - (float) Math.cos(angle) * inner);
        }
        path.close();
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(color);
        shapePaint.setAlpha(Math.round(255 * alpha));
        canvas.drawPath(path, shapePaint);
    }

    private void drawParticles(Canvas canvas, LgVectorScene.Frame f) {
        // e.i(): particles advance with INNER radius, not wall time. Reversing the
        // finger reverses their colour/size/hole, and there are exactly eleven points.
        float progress = LgVectorScene.normalize(0, .5f * f.boundary, f.innerRadius) * 7.35f;
        for (int i = 0; i < 11; i++) {
            float start = .35f * i;
            if (progress < start) continue;
            float n = LgVectorScene.normalize(start, start + 3.5f, progress);
            float alpha = 1f - .3f * n;
            if (n > .9f) alpha *= (1f - n) / .1f;
            if (alpha <= 0) continue;
            float diameter = i < 6 ? 6.67f * f.density
                    : (3.33f + (26.67f - 3.33f) * n) * f.density * (1f + (i - 5) / 5f);
            float radius = i < 6 ? .95f * f.outerRadius * (1f - .2f * (i + 1) / 6f)
                    : .76f * f.outerRadius;
            double angle = Math.toRadians(i < 6 ? 120 + 18 * i : 240 + 30 * (i - 6));
            float x = f.x + (float) Math.sin(angle) * radius;
            float y = f.y - (float) Math.cos(angle) * radius;
            shapePaint.setColor(mixColor(LgVectorScene.PALETTES[f.palette][2],
                    LgVectorScene.PALETTES[f.palette][3], n));
            shapePaint.setAlpha(Math.round(255 * alpha));
            shapePaint.setStyle(Paint.Style.FILL);
            if (i >= 6 && n > .5f) {
                // gl_PointCoord hole threshold n-.5 is relative to DIAMETER.
                drawAnnulus(canvas, x, y, diameter * (n - .5f), diameter * .5f);
            } else {
                canvas.drawCircle(x, y, diameter * .5f, shapePaint);
            }
        }
    }

    private void drawDragLines(Canvas canvas, LgVectorScene.Frame f) {
        if (lineTexture == null || f.distance < .8f * f.boundary
                || f.distance >= 1.15f * f.boundary) return;
        float t = LgVectorScene.normalize(.8f * f.boundary, 1.15f * f.boundary, f.distance);
        float growth = .2145f * t;
        float scale = (.7455f + growth) * f.density * .5f;
        float alpha = t <= .3f ? t / .3f : growth > .15f
                ? 1f - LgVectorScene.normalize(.15f, .25f, growth) : 1f;
        float clipRadius = .5f * lineTexture.getWidth() * f.density * .5f * (.7455f + .15f);
        path.reset();
        path.addCircle(f.x, f.y, clipRadius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(path);
        float halfW = lineTexture.getWidth() * scale * .5f;
        float halfH = lineTexture.getHeight() * scale * .5f;
        rect.set(f.x - halfW, f.y - halfH, f.x + halfW, f.y + halfH);
        spritePaint.setAlpha(Math.round(255 * alpha));
        canvas.drawBitmap(lineTexture, null, rect, spritePaint);
        canvas.restoreToCount(save);
    }

    private static int mixColor(int from, int to, float t) {
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }

    private void annulus(float x, float y, float inner, float outer) {
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addCircle(x, y, outer, Path.Direction.CW);
        if (inner > 0) path.addCircle(x, y, inner, Path.Direction.CW);
    }
    private void drawAnnulus(Canvas canvas, float x, float y, float inner, float outer) {
        if (outer <= inner || outer <= 0) return;
        annulus(x, y, Math.max(0, inner), outer);
        canvas.drawPath(path, shapePaint);
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
        imagePaint.setShader(null);
        bandPaint.setShader(null);
        primaryShader = secondaryShader = null;
        // HWUI may still own the preceding display list. Let bitmap reference counting free
        // these copies, instead of recycling a texture while RenderThread is using it.
        primary = secondary = lineTexture = tapLineTexture = null;
        readinessListener = null;
    }
}
