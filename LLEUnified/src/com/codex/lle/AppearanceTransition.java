package com.codex.lle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** A short, bounded snapshot of our own UI, retained only across theme recreation. */
final class AppearanceTransition {
    private static final int MAX_PIXELS = 1_000_000;

    static final class Snapshot {
        Bitmap bitmap;
        final int width, height;
        final float x, y;
        final boolean targetDark;
        final long capturedAt = SystemClock.uptimeMillis();

        Snapshot(Bitmap bitmap, int width, int height, float x, float y, boolean dark) {
            this.bitmap = bitmap;
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
            targetDark = dark;
        }

        void release() {
            // Drop the reference instead of recycling a bitmap still referenced by a display list.
            bitmap = null;
        }
    }

    static Snapshot capture(FrameLayout scene, View origin, boolean targetDark) {
        int width = scene.getWidth(), height = scene.getHeight();
        if (width <= 0 || height <= 0) return null;
        Bitmap bitmap = null;
        try {
            double scale = Math.min(1d, Math.sqrt(MAX_PIXELS / ((double) width * height)));
            int bitmapWidth = Math.max(1, (int) (width * scale));
            int bitmapHeight = Math.max(1, (int) (height * scale));
            bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(bitmapWidth / (float) width, bitmapHeight / (float) height);
            scene.draw(canvas);
            int[] sceneLocation = new int[2], originLocation = new int[2];
            scene.getLocationInWindow(sceneLocation);
            origin.getLocationInWindow(originLocation);
            float x = Math.max(0f, Math.min(width,
                    originLocation[0] - sceneLocation[0] + origin.getWidth() / 2f));
            float y = Math.max(0f, Math.min(height,
                    originLocation[1] - sceneLocation[1] + origin.getHeight() / 2f));
            return new Snapshot(bitmap, width, height, x, y, targetDark);
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            if (bitmap != null) bitmap.recycle();
            return null; // Applying a theme must not depend on an optional animation.
        }
    }

    static Reveal attach(FrameLayout scene, Snapshot snapshot, boolean dark, boolean animate) {
        if (snapshot == null) return null;
        if (!animate || snapshot.bitmap == null || snapshot.targetDark != dark
                || SystemClock.uptimeMillis() - snapshot.capturedAt > 5000L) {
            snapshot.release();
            return null;
        }
        Reveal reveal = new Reveal(scene, snapshot);
        scene.addView(reveal, new FrameLayout.LayoutParams(-1, -1));
        reveal.getViewTreeObserver().addOnPreDrawListener(reveal);
        return reveal;
    }

    static final class Reveal extends View implements ViewTreeObserver.OnPreDrawListener {
        private final FrameLayout scene;
        private final Snapshot snapshot;
        private final Path mask = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect destination = new Rect();
        private ValueAnimator animator;
        private float radius;
        private boolean finished;

        Reveal(FrameLayout scene, Snapshot snapshot) {
            super(scene.getContext());
            this.scene = scene;
            this.snapshot = snapshot;
            setClickable(true);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setElevation(24f * getResources().getDisplayMetrics().density);
        }

        @Override public boolean onPreDraw() {
            if (getViewTreeObserver().isAlive())
                getViewTreeObserver().removeOnPreDrawListener(this);
            if (finished) return true;
            if (getWidth() != snapshot.width || getHeight() != snapshot.height) {
                finish(); // Rotation / a different Fold panel should get its own layout immediately.
                return true;
            }
            float farX = Math.max(snapshot.x, getWidth() - snapshot.x);
            float farY = Math.max(snapshot.y, getHeight() - snapshot.y);
            animator = ValueAnimator.ofFloat(0f, (float) Math.hypot(farX, farY) + 2f);
            animator.setDuration(440L);
            animator.setInterpolator(new PathInterpolator(0.22f, 0f, 0.15f, 1f));
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    radius = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { finish(); }
            });
            Log.i("LLEAppearance", "theme reveal started dark=" + snapshot.targetDark);
            animator.start();
            return true;
        }

        @Override protected void onDraw(Canvas canvas) {
            if (finished || snapshot.bitmap == null) return;
            mask.reset();
            mask.setFillType(Path.FillType.EVEN_ODD);
            mask.addRect(0f, 0f, getWidth(), getHeight(), Path.Direction.CW);
            if (radius > 0f) mask.addCircle(snapshot.x, snapshot.y, radius, Path.Direction.CW);
            int saved = canvas.save();
            canvas.clipPath(mask);
            destination.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(snapshot.bitmap, null, destination, paint);
            canvas.restoreToCount(saved);
        }

        void finish() {
            if (finished) return;
            finished = true;
            if (getViewTreeObserver().isAlive())
                getViewTreeObserver().removeOnPreDrawListener(this);
            if (animator != null) {
                animator.removeAllListeners();
                animator.removeAllUpdateListeners();
                animator.cancel();
                animator = null;
            }
            scene.removeView(this);
            snapshot.release();
            Log.i("LLEAppearance", "theme reveal released");
        }
    }

    private AppearanceTransition() {}
}
