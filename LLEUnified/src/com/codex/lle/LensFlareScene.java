package com.codex.lle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure timing/geometry model shared by the experimental GLES Lens Flare renderer. */
final class LensFlareScene {
    static final long AB_RANDOM_SEED = 0x4c4c454c454e5346L;
    static final int LIGHT = 0;
    static final int RING = 1;
    static final int PARTICLE = 2;
    static final int LONG = 3;
    static final int RAINBOW = 4;
    static final int HOVER_LIGHT = 5;
    static final int HEXAGON_BLUE = 6;
    static final int HEXAGON_ORANGE = 7;
    static final int HEXAGON_GREEN = 8;
    static final int ASSET_COUNT = 9;

    private static final long SHOW_ANIMATION_DURATION_MS = 6000L;
    private static final long FOG_ON_DURATION_MS = 100L;
    private static final long TAP_ANIMATION_DURATION_MS = 4000L;
    private static final long FADE_OUT_DURATION_MS = 500L;
    private static final long UNLOCK_ANIMATION_DURATION_MS = 1200L;
    private static final long AFFORDANCE_ON_DURATION_MS = 200L;
    private static final long AFFORDANCE_OFF_DURATION_MS = 1100L;
    private static final float GLOBAL_ALPHA = 0.8f;
    private static final float FOG_MAX_ALPHA = 0.6f;
    private static final int TAP_HEXAGON_TOTAL = 5;
    private static final int DRAG_HEXAGON_TOTAL = 6;

    private static final int[] TAP_HEXAGONS = {
            HEXAGON_BLUE, HEXAGON_ORANGE, HEXAGON_GREEN
    };
    private static final int[] DRAG_HEXAGONS = {
            HEXAGON_BLUE, HEXAGON_ORANGE, HEXAGON_BLUE,
            HEXAGON_ORANGE, HEXAGON_GREEN, HEXAGON_GREEN
    };

    static final class Sprite {
        final int asset;
        final float x;
        final float y;
        final float scale;
        final float alpha;
        final float rotation;

        Sprite(int asset, float x, float y, float scale, float alpha, float rotation) {
            this.asset = asset;
            this.x = x;
            this.y = y;
            this.scale = scale;
            // Canvas truncates alpha*255 into Paint. Preserve that exact quantization.
            int quantized = Math.max(0, Math.min(255, (int) (alpha * 255f)));
            this.alpha = quantized / 255f;
            this.rotation = rotation;
        }
    }

    static final class Frame {
        final List<Sprite> sprites = new ArrayList<Sprite>(32);
        float vignetteAlpha;
        boolean keepAnimating;
        boolean warmFrameDrawn;
    }

    private final Random random;
    private final float[] tapHexagonRotations = new float[TAP_HEXAGON_TOTAL];
    private final float[] dragHexagonDistance = new float[DRAG_HEXAGON_TOTAL];
    private final float[] dragHexagonScale = new float[DRAG_HEXAGON_TOTAL];
    private final float[] dragHexagonRotations = new float[DRAG_HEXAGON_TOTAL];
    private final float maxAlphaDistancePx;
    private final float tapAreaRadiusPx;

    private boolean warmUpPending;
    private boolean gestureActive;
    private boolean fading;
    private float startX;
    private float startY;
    private float currentX;
    private float currentY;
    private float fadeX;
    private float fadeY;
    private long gestureStartedAt;
    private long fadeStartedAt;
    private float fadeFogAnimationValue;
    private float randomRotation;
    private TapAnimation tapAnimation;
    private UnlockAnimation unlockAnimation;
    private AffordanceAnimation affordanceAnimation;

    LensFlareScene(float maxAlphaDistancePx, float tapAreaRadiusPx) {
        this(maxAlphaDistancePx, tapAreaRadiusPx, false);
    }

    LensFlareScene(float maxAlphaDistancePx, float tapAreaRadiusPx,
            boolean deterministicAb) {
        this.maxAlphaDistancePx = maxAlphaDistancePx;
        this.tapAreaRadiusPx = tapAreaRadiusPx;
        random = deterministicAb ? new Random(AB_RANDOM_SEED) : new Random();
        for (int i = 0; i < tapHexagonRotations.length; i++) {
            tapHexagonRotations[i] = random.nextInt(360);
        }
        for (int i = 0; i < dragHexagonRotations.length; i++) {
            dragHexagonRotations[i] = random.nextFloat() * 20f;
        }
    }

    void warmUp() {
        warmUpPending = true;
    }

    void begin(float x, float y, long now) {
        gestureActive = true;
        fading = false;
        startX = x;
        startY = y;
        currentX = x;
        currentY = y;
        fadeStartedAt = 0L;
        fadeFogAnimationValue = 0f;
        gestureStartedAt = now;
        randomRotation = random.nextInt(360);
        setHexagonRandomTarget();
        tapAnimation = createTapAnimation(x, y, now);
        unlockAnimation = null;
        affordanceAnimation = null;
    }

    void move(float x, float y) {
        currentX = x;
        currentY = y;
    }

    void finish(boolean completed, long now) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        fadeX = currentX;
        fadeY = currentY;
        fadeStartedAt = now;
        fadeFogAnimationValue = currentFogAnimationValue(now);
        fading = true;
        if (completed) {
            unlockAnimation = new UnlockAnimation(
                    startX, startY, currentX, currentY, now, unlockRotation());
        }
    }

    void cancel(long now) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        fading = true;
        fadeX = currentX;
        fadeY = currentY;
        fadeStartedAt = now;
        fadeFogAnimationValue = currentFogAnimationValue(now);
    }

    void affordance(float x, float y, long now) {
        if (gestureActive) {
            return;
        }
        randomRotation = random.nextInt(360);
        setHexagonRandomTarget();
        tapAnimation = createTapAnimation(x, y, now);
        affordanceAnimation = new AffordanceAnimation(x, y, now);
    }

    void reset() {
        gestureActive = false;
        fading = false;
        warmUpPending = false;
        tapAnimation = null;
        unlockAnimation = null;
        affordanceAnimation = null;
    }

    boolean isGestureActive() {
        return gestureActive;
    }

    Frame frame(long now) {
        Frame frame = new Frame();
        frame.vignetteAlpha = currentVignettingAlpha(now);

        if (warmUpPending) {
            addWarmUpFrame(frame);
            warmUpPending = false;
            frame.warmFrameDrawn = true;
        }

        if (gestureActive) {
            addDragFlare(frame, now, currentX, currentY, 1f,
                    currentFogAnimationValue(now));
            frame.keepAnimating = true;
        } else if (fading) {
            float t = clamp01((now - fadeStartedAt) / (float) FADE_OUT_DURATION_MS);
            addDragFlare(frame, now, fadeX, fadeY, 1f - t, fadeFogAnimationValue);
            frame.keepAnimating = t < 1f;
            if (!frame.keepAnimating) {
                fading = false;
            }
        }

        if (tapAnimation != null) {
            float t = clamp01((now - tapAnimation.startedAt)
                    / (float) TAP_ANIMATION_DURATION_MS);
            if (t < 1f) {
                addTapAnimation(frame, tapAnimation, quintOut(t));
                frame.keepAnimating = true;
            } else {
                tapAnimation = null;
            }
        }

        if (affordanceAnimation != null) {
            if (addUnlockAffordance(frame, now, affordanceAnimation)) {
                frame.keepAnimating = true;
            } else {
                affordanceAnimation = null;
            }
        }

        if (unlockAnimation != null) {
            float t = clamp01((now - unlockAnimation.startedAt)
                    / (float) UNLOCK_ANIMATION_DURATION_MS);
            if (t < 1f) {
                addUnlockAnimation(frame, unlockAnimation, quintOut(t));
                frame.keepAnimating = true;
            } else {
                unlockAnimation = null;
            }
        }
        return frame;
    }

    private void addWarmUpFrame(Frame frame) {
        add(frame, LIGHT, 0.5f, 0.5f, 0.004f, 1f, 0f);
        add(frame, RING, 0.5f, 0.5f, 0.004f, 1f, 0f);
        add(frame, PARTICLE, 0.5f, 0.5f, 0.004f, 1f, 0f);
        add(frame, LONG, 0.5f, 0.5f, 0.004f, 1f, 0f);
        add(frame, RAINBOW, 0.5f, 0.5f, 0.004f, 1f, 0f);
        add(frame, HOVER_LIGHT, 0.5f, 0.5f, 0.004f, 1f, 0f);
        for (int asset : TAP_HEXAGONS) {
            add(frame, asset, 0.5f, 0.5f, 0.004f, 1f, 0f);
        }
        for (int asset : DRAG_HEXAGONS) {
            add(frame, asset, 0.5f, 0.5f, 0.004f, 1f, 0f);
        }
    }

    private void addDragFlare(Frame frame, long now, float x, float y, float fadeAlpha,
            float fogAnimationValue) {
        float objValue = quintOut(clamp01((now - gestureStartedAt)
                / (float) SHOW_ANIMATION_DURATION_MS));
        float distance = (float) Math.hypot(x - startX, y - startY);
        float distanceAlpha = clamp01(distance / maxAlphaDistancePx);
        float fogAlpha = clamp01(fogAnimationValue * (1f - distanceAlpha))
                * GLOBAL_ALPHA * fadeAlpha;
        float objAlpha = clamp01(distanceAlpha * 3f) * fadeAlpha;
        float rotation = -objValue * 30f - distanceAlpha * 160f;
        add(frame, LIGHT, x, y, 1f + distanceAlpha, fogAlpha, rotation);
        if (objAlpha <= 0f) {
            return;
        }
        for (int i = 0; i < DRAG_HEXAGON_TOTAL; i++) {
            float animationScale = 0.5f + objValue * 0.5f;
            float byDistanceScale = 0.5f + (distance / 720f) * 0.5f;
            float scale = dragHexagonScale[i] * byDistanceScale * animationScale;
            float pathScale = dragHexagonDistance[i] * animationScale;
            float tx = startX + (x - startX) * pathScale;
            float ty = startY + (y - startY) * pathScale;
            add(frame, DRAG_HEXAGONS[i], tx, ty, scale, objAlpha,
                    dragHexagonRotations[i]);
        }
    }

    private void addTapAnimation(Frame frame, TapAnimation animation, float value) {
        float alpha = value < 0.5f ? 1f : 1f - (value - 0.5f) * 2f;
        alpha = clamp01(alpha) * GLOBAL_ALPHA;
        float distanceScale = 0.2f + 0.8f * value;
        for (TapHexagon hexagon : animation.hexagons) {
            float scale = hexagon.scale * (value * 0.8f + 0.7f);
            add(frame, hexagon.asset,
                    animation.x + hexagon.dx * distanceScale,
                    animation.y + hexagon.dy * distanceScale,
                    scale, alpha, hexagon.rotation);
        }
        float particleValue = value * 1.8f;
        add(frame, PARTICLE, animation.x, animation.y, value * 1.2f,
                pulseAlpha(particleValue), animation.rotation);
        float ringValue = value * 1.4f;
        float ringAlpha = pulseAlpha(ringValue);
        add(frame, RING, animation.x, animation.y, 0.5f + value, ringAlpha, 0f);
        add(frame, LONG, animation.x, animation.y, 1.5f + value * 2f, ringAlpha,
                animation.rotation + 30f * value);
    }

    private boolean addUnlockAffordance(Frame frame, long now,
            AffordanceAnimation animation) {
        long elapsed = now - animation.startedAt;
        float alpha;
        if (elapsed < AFFORDANCE_ON_DURATION_MS) {
            alpha = FOG_MAX_ALPHA * clamp01(elapsed / (float) AFFORDANCE_ON_DURATION_MS);
        } else if (elapsed < AFFORDANCE_ON_DURATION_MS + AFFORDANCE_OFF_DURATION_MS) {
            float offT = (elapsed - AFFORDANCE_ON_DURATION_MS)
                    / (float) AFFORDANCE_OFF_DURATION_MS;
            alpha = FOG_MAX_ALPHA * (1f - clamp01(offT));
        } else {
            return false;
        }
        add(frame, LIGHT, animation.x, animation.y, 1f, alpha, 0f);
        return true;
    }

    private void addUnlockAnimation(Frame frame, UnlockAnimation animation, float value) {
        float alpha = value < 0.5f ? value * 2f : 1f - (value - 0.5f) * 2f;
        float x = animation.startX + (animation.endX - animation.startX) * 0.4f;
        float y = animation.startY + (animation.endY - animation.startY) * 0.4f;
        add(frame, RAINBOW, x, y, 1f + value * 1.3f,
                clamp01(alpha), animation.rotation);
    }

    private void add(Frame frame, int asset, float x, float y, float scale,
            float alpha, float rotation) {
        if (alpha > 0f && scale > 0f) {
            frame.sprites.add(new Sprite(asset, x, y, scale, alpha, rotation));
        }
    }

    private TapAnimation createTapAnimation(float x, float y, long now) {
        TapHexagon[] animationHexagons = new TapHexagon[TAP_HEXAGON_TOTAL];
        for (int i = 0; i < animationHexagons.length; i++) {
            // Preserve the current Canvas renderer literally: randomRotation is fed directly
            // to Math.cos/sin, including its historical degree/radian mismatch.
            float angle = randomRotation;
            float distance = random.nextFloat() * tapAreaRadiusPx;
            animationHexagons[i] = new TapHexagon(
                    (float) Math.cos(angle) * distance,
                    (float) Math.sin(angle) * distance,
                    0.2f + random.nextFloat() * 0.8f,
                    TAP_HEXAGONS[i % TAP_HEXAGONS.length],
                    tapHexagonRotations[i]);
        }
        return new TapAnimation(x, y, now, randomRotation, animationHexagons);
    }

    private void setHexagonRandomTarget() {
        float startDistance = 0.2f;
        float distanceGap = 0.24f;
        for (int i = 0; i < DRAG_HEXAGON_TOTAL; i++) {
            dragHexagonDistance[i] = startDistance + i * distanceGap
                    + (random.nextFloat() - 0.5f) * 0.4f;
            dragHexagonScale[i] = dragHexagonDistance[i] + 0.1f;
        }
        for (int i = DRAG_HEXAGON_TOTAL - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            float distance = dragHexagonDistance[i];
            dragHexagonDistance[i] = dragHexagonDistance[swapIndex];
            dragHexagonDistance[swapIndex] = distance;
            float scale = dragHexagonScale[i];
            dragHexagonScale[i] = dragHexagonScale[swapIndex];
            dragHexagonScale[swapIndex] = scale;
        }
    }

    private float unlockRotation() {
        float dx = currentX - startX;
        float dy = currentY - startY;
        if (Math.abs(dx) < 1f && Math.abs(dy) < 1f) {
            return randomRotation;
        }
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 40f;
    }

    private float currentVignettingAlpha(long now) {
        float x;
        float y;
        float fadeAlpha;
        if (gestureActive) {
            x = currentX;
            y = currentY;
            fadeAlpha = 1f;
        } else if (fading) {
            x = fadeX;
            y = fadeY;
            fadeAlpha = 1f - clamp01((now - fadeStartedAt) / (float) FADE_OUT_DURATION_MS);
        } else {
            return 0f;
        }
        float distance = (float) Math.hypot(x - startX, y - startY);
        return clamp01((distance / maxAlphaDistancePx) * 1.3f) * fadeAlpha;
    }

    private float currentFogAnimationValue(long now) {
        return FOG_MAX_ALPHA * quintOut(clamp01((now - gestureStartedAt)
                / (float) FOG_ON_DURATION_MS));
    }

    private static float pulseAlpha(float value) {
        float corrected = value < 0.5f ? 1f : 1f - (value - 0.5f) * 2f;
        return clamp01(corrected);
    }

    private static float quintOut(float value) {
        float inverse = 1f - clamp01(value);
        return 1f - inverse * inverse * inverse * inverse * inverse;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class TapAnimation {
        final float x;
        final float y;
        final long startedAt;
        final float rotation;
        final TapHexagon[] hexagons;

        TapAnimation(float x, float y, long startedAt, float rotation,
                TapHexagon[] hexagons) {
            this.x = x;
            this.y = y;
            this.startedAt = startedAt;
            this.rotation = rotation;
            this.hexagons = hexagons;
        }
    }

    private static final class TapHexagon {
        final float dx;
        final float dy;
        final float scale;
        final int asset;
        final float rotation;

        TapHexagon(float dx, float dy, float scale, int asset, float rotation) {
            this.dx = dx;
            this.dy = dy;
            this.scale = scale;
            this.asset = asset;
            this.rotation = rotation;
        }
    }

    private static final class AffordanceAnimation {
        final float x;
        final float y;
        final long startedAt;

        AffordanceAnimation(float x, float y, long startedAt) {
            this.x = x;
            this.y = y;
            this.startedAt = startedAt;
        }
    }

    private static final class UnlockAnimation {
        final float startX;
        final float startY;
        final float endX;
        final float endY;
        final long startedAt;
        final float rotation;

        UnlockAnimation(float startX, float startY, float endX, float endY,
                long startedAt, float rotation) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.startedAt = startedAt;
            this.rotation = rotation;
        }
    }
}
