package com.codex.lle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/** Brief, app-owned confetti burst after the hidden code is completed. */
final class KonamiConfettiView extends View {
    private static final int PARTICLE_COUNT = 144;
    private static final float DURATION_SECONDS = 1.35f;
    private static final int[] COLORS = {
            Color.rgb(0, 176, 190),
            Color.rgb(255, 196, 69),
            Color.rgb(255, 105, 116),
            Color.rgb(125, 109, 229),
            Color.rgb(96, 192, 117),
            Color.rgb(255, 255, 255)
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Particle[] particles = new Particle[PARTICLE_COUNT];
    private final float density;
    private ValueAnimator animator;
    private float progress;

    KonamiConfettiView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(true);
        Random random = new Random(0x4b4f4e41L);
        for (int index = 0; index < particles.length; index++) {
            boolean fromLeft = index % 2 == 0;
            Particle particle = new Particle();
            particle.originX = fromLeft ? 0.07f : 0.93f;
            particle.velocityX = (fromLeft ? 1f : -1f)
                    * (0.18f + random.nextFloat() * 0.48f);
            particle.velocityY = -(0.70f + random.nextFloat() * 0.48f);
            particle.delaySeconds = random.nextFloat() * 0.20f;
            particle.size = (4f + random.nextFloat() * 5f) * density;
            particle.spin = (random.nextBoolean() ? 1f : -1f)
                    * (310f + random.nextFloat() * 620f);
            particle.color = COLORS[random.nextInt(COLORS.length)];
            particles[index] = particle;
        }
    }

    void play(final Runnable completion) {
        stop();
        progress = 0f;
        final ValueAnimator animation = ValueAnimator.ofFloat(0f, 1f);
        animator = animation;
        animation.setDuration((long) (DURATION_SECONDS * 1000f));
        animation.setInterpolator(new LinearInterpolator());
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                progress = ((Float) valueAnimator.getAnimatedValue()).floatValue();
                invalidate();
            }
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator ended) {
                if (animator == animation) {
                    animator = null;
                    if (completion != null) {
                        completion.run();
                    }
                }
            }
        });
        animation.start();
    }

    void stop() {
        if (animator != null) {
            ValueAnimator running = animator;
            animator = null;
            running.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float elapsed = progress * DURATION_SECONDS;
        for (Particle particle : particles) {
            float age = elapsed - particle.delaySeconds;
            if (age < 0f || age > 1.26f) {
                continue;
            }
            float x = width * (particle.originX + particle.velocityX * age);
            float y = height * (0.91f + particle.velocityY * age
                    + 0.78f * age * age);
            if (x < -particle.size || x > width + particle.size
                    || y < -particle.size || y > height + particle.size) {
                continue;
            }
            paint.setColor(particle.color);
            paint.setAlpha((int) (255f * Math.min(1f,
                    Math.max(0f, (1.26f - age) / 0.26f))));
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(particle.spin * age);
            canvas.drawRoundRect(-particle.size * 0.5f,
                    -particle.size * 0.28f,
                    particle.size * 0.5f,
                    particle.size * 0.28f,
                    particle.size * 0.10f,
                    particle.size * 0.10f,
                    paint);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private static final class Particle {
        float originX;
        float velocityX;
        float velocityY;
        float delaySeconds;
        float size;
        float spin;
        int color;
    }
}
