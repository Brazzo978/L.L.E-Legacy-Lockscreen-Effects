package com.codex.chargingtouchtest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ChargingActivity extends Activity {
    private static final String TAG = "ChargingTouchTest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0f);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        setContentView(new ChargingTouchTestView(this));
        hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    static final class ChargingTouchTestView extends View {
        private static final int MAX_PARTICLES = 180;
        private static final long PARTICLE_DURATION_MS = 1450L;
        private static final long RING_DURATION_MS = 1250L;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Random random = new Random(20260630L);
        private final List<Particle> particles = new ArrayList<Particle>();
        private final List<Ring> rings = new ArrayList<Ring>();
        private final ColorMatrixColorFilter brightenFilter;
        private long lastMoveBurstAt;
        private long startedAt;

        ChargingTouchTestView(Context context) {
            super(context);
            setWillNotDraw(false);
            setFocusable(true);
            setClickable(true);
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(1.35f);
            brightenFilter = new ColorMatrixColorFilter(matrix);
            startedAt = SystemClock.uptimeMillis();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            post(new Runnable() {
                @Override
                public void run() {
                    burst(getWidth() * 0.5f, getHeight() * 0.62f, 28, true);
                }
            });
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                burst(event.getX(), event.getY(), 42, true);
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                long now = SystemClock.uptimeMillis();
                if (now - lastMoveBurstAt > 55L) {
                    lastMoveBurstAt = now;
                    burst(event.getX(), event.getY(), 10, false);
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                burst(event.getX(), event.getY(), 20, false);
                return true;
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long now = SystemClock.uptimeMillis();
            drawAmbientPulse(canvas, now);
            drawRings(canvas, now);
            drawParticles(canvas, now);
            if (!particles.isEmpty() || !rings.isEmpty()) {
                postInvalidateOnAnimation();
            } else {
                postInvalidateDelayed(240L);
            }
        }

        private void burst(float x, float y, int count, boolean ring) {
            long now = SystemClock.uptimeMillis();
            if (ring) {
                rings.add(new Ring(x, y, now));
            }
            for (int i = 0; i < count; i++) {
                float angle = random.nextFloat() * 360f;
                float speed = dp(90f + random.nextFloat() * 300f);
                float vx = (float) Math.cos(Math.toRadians(angle)) * speed;
                float vy = (float) Math.sin(Math.toRadians(angle)) * speed - dp(55f + random.nextFloat() * 120f);
                particles.add(new Particle(
                        x,
                        y,
                        vx,
                        vy,
                        now + i * 6L,
                        particleColor(i),
                        0.65f + random.nextFloat() * 0.9f));
            }
            while (particles.size() > MAX_PARTICLES) {
                particles.remove(0);
            }
            invalidate();
        }

        private void drawAmbientPulse(Canvas canvas, long now) {
            float phase = ((now - startedAt) % 2600L) / 2600f;
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.62f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(28, 0, 210, 235));
            canvas.drawCircle(cx, cy, dp(150f), paint);
            float radius = dp(80f) + dp(110f) * phase;
            paint.setColor(Color.TRANSPARENT);
            paint.setShader(new RadialGradient(
                    cx,
                    cy,
                    radius,
                    Color.argb((int) (64 * (1f - phase)), 90, 235, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null);
        }

        private void drawRings(Canvas canvas, long now) {
            Iterator<Ring> iterator = rings.iterator();
            while (iterator.hasNext()) {
                Ring ring = iterator.next();
                float t = (now - ring.startedAt) / (float) RING_DURATION_MS;
                if (t >= 1f) {
                    iterator.remove();
                    continue;
                }
                float eased = 1f - (1f - t) * (1f - t);
                float radius = dp(24f) + dp(190f) * eased;
                int alpha = (int) (190f * (1f - t));
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.4f + 2.2f * (1f - t)));
                paint.setColor(Color.argb(alpha, 185, 245, 255));
                canvas.drawCircle(ring.x, ring.y, radius, paint);
                paint.setStrokeWidth(dp(0.8f));
                paint.setColor(Color.argb(alpha / 2, 255, 255, 255));
                canvas.drawCircle(ring.x, ring.y, radius * 0.58f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawParticles(Canvas canvas, long now) {
            Iterator<Particle> iterator = particles.iterator();
            while (iterator.hasNext()) {
                Particle particle = iterator.next();
                float t = (now - particle.startedAt) / (float) PARTICLE_DURATION_MS;
                if (t >= 1f) {
                    iterator.remove();
                    continue;
                }
                if (t < 0f) {
                    continue;
                }
                float eased = 1f - (1f - t) * (1f - t);
                float x = particle.x + particle.vx * t + (float) Math.sin((t + particle.seed) * 8f) * dp(18f);
                float y = particle.y + particle.vy * t + dp(240f) * t * t;
                float size = dp(4f + 10f * particle.seed) * (1f - t * 0.35f);
                int alpha = (int) (210f * (1f - t));
                paint.setColorFilter(brightenFilter);
                paint.setShader(new RadialGradient(
                        x,
                        y,
                        size * 2.3f,
                        withAlpha(particle.color, alpha),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP));
                canvas.drawCircle(x, y, size * 2.3f, paint);
                paint.setShader(null);
                paint.setColor(withAlpha(particle.color, alpha));
                canvas.drawCircle(x, y, size, paint);
                paint.setColorFilter(null);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(0.8f));
                paint.setColor(Color.argb(alpha, 255, 255, 255));
                float sparkle = size * (0.7f + eased);
                canvas.drawLine(x - sparkle, y, x + sparkle, y, paint);
                canvas.drawLine(x, y - sparkle, x, y + sparkle, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        private int particleColor(int index) {
            int[] colors = {
                    Color.rgb(118, 238, 255),
                    Color.rgb(255, 238, 145),
                    Color.rgb(255, 160, 210),
                    Color.rgb(168, 255, 192),
                    Color.rgb(205, 180, 255)
            };
            return colors[Math.abs(index) % colors.length];
        }

        private int withAlpha(int color, int alpha) {
            return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00ffffff);
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    private static final class Particle {
        final float x;
        final float y;
        final float vx;
        final float vy;
        final long startedAt;
        final int color;
        final float seed;

        Particle(float x, float y, float vx, float vy, long startedAt, int color, float seed) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.startedAt = startedAt;
            this.color = color;
            this.seed = seed;
        }
    }

    private static final class Ring {
        final float x;
        final float y;
        final long startedAt;

        Ring(float x, float y, long startedAt) {
            this.x = x;
            this.y = y;
            this.startedAt = startedAt;
        }
    }
}

