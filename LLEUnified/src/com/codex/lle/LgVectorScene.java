package com.codex.lle;

import java.util.Random;

/** Clock and radius equations recovered from OptimusDev Vector 1.1, renderer a.b.l.
 * No Android state: a stopped hint or a repeated unlock cannot retain an animator callback.
 */
final class LgVectorScene {
    private final EffectWorkshopConfig.Values workshop;
    static final long TAP_MS = 680L;
    static final long CANCEL_MS = 300L;
    static final long UNLOCK_MS = 400L;
    static final long UNDERLAY_HOLD_MS = 550L;
    static final int IDLE = 0, ACTIVE = 1, CANCEL = 2, COMPLETE = 3;
    // a.b.j: band, coloured arcs, particle source, particle destination.
    static final int[][] PALETTES = {
        {0xffebfa63, 0xff44ffff, 0xffffffff, 0xffffb45e},
        {0xff50ffff, 0xffffa42e, 0xffffffff, 0xffeeff44},
        {0xffb7fa45, 0xff99a8ff, 0xffffffff, 0xff50ffff},
        {0xffff2e6f, 0xff44ffff, 0xffffffff, 0xffffb45e}
    };
    private final Random random;
    private float density = 1f;
    private int width = 1, height = 1;
    private int stage;
    private long startedAt, finishedAt;
    private float centerX, centerY, distance, terminalDistance;
    private boolean dragging;
    private int palette;

    LgVectorScene() { this(new Random()); }
    LgVectorScene(Random random) { this(random, EffectWorkshopConfig.originals(41)); }
    LgVectorScene(EffectWorkshopConfig.Values values) { this(new Random(), values); }
    LgVectorScene(Random random, EffectWorkshopConfig.Values values) { this.random = random; workshop = values; }

    void configure(int width, int height, float density) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.density = finite(density) && density > 0f ? density : 1f;
    }
    float minRadius() { return workshop.get("min_radius_dp") * density; }
    float boundaryRadius() { return workshop.get("boundary_dp") * density; }
    float fullRadius() { return (float) Math.hypot(width, height); }
    int state() { return stage; }
    boolean gestureActive() { return stage == ACTIVE; }

    void begin(float x, float y, long now) {
        if (!finite(x) || !finite(y)) return;
        stage = ACTIVE;
        startedAt = now;
        finishedAt = 0;
        centerX = clamp(x, 0, width);
        centerY = clamp(y, 0, height);
        distance = terminalDistance = 0f;
        dragging = false;
        palette = workshop.intValue("palette") == 0 ? random.nextInt(PALETTES.length) : workshop.intValue("palette") - 1;
    }

    void move(float x, float y, long now) {
        if (stage != ACTIVE || !finite(x) || !finite(y)) return;
        distance = Math.min(fullRadius(), (float) Math.hypot(x - centerX, y - centerY));
        // l.b(x,y) ends the tap animator when the drag exceeds half the minimum ring.
        if (distance > workshop.get("drag_fraction") * minRadius()) dragging = true;
    }

    void finish(boolean completed, long now) {
        if (stage != ACTIVE) return;
        finishedAt = now;
        terminalDistance = distance;
        stage = completed ? COMPLETE : CANCEL;
        if (completed) dragging = true;
    }

    void reset() {
        stage = IDLE;
        dragging = false;
        distance = terminalDistance = 0;
        startedAt = finishedAt = 0;
    }

    Frame sample(long now, Frame out) {
        out.clear();
        if (stage == IDLE) return out;
        long age = Math.max(0, now - startedAt);
        long terminalAge = Math.max(0, now - finishedAt);
        if (stage == COMPLETE && terminalAge >= UNLOCK_MS + UNDERLAY_HOLD_MS
                || stage == CANCEL && (dragging ? terminalAge >= workshop.get("cancel_ms") : age >= workshop.get("tap_ms"))) {
            reset();
            return out;
        }
        out.stage = stage;
        out.x = centerX;
        out.y = centerY;
        out.palette = palette;
        out.ageMs = age;
        out.alpha = 1f;
        out.minRadius = minRadius();
        out.boundary = boundaryRadius();
        out.density = density;
        out.fullUnderlay = stage == COMPLETE && terminalAge >= UNLOCK_MS;
        if (out.fullUnderlay) {
            out.visible = out.running = true;
            out.outerRadius = out.innerRadius = fullRadius();
            return out;
        }
        if (!dragging && age < workshop.get("tap_ms")) {
            out.tap = true;
            out.tapProgress = .5f - .5f * (float) Math.cos(Math.PI * clamp(age / (float) workshop.get("tap_ms"), 0, 1));
            out.visible = out.running = true;
            return out;
        }
        // An untouched, finished tap leaves no pixels and no busy render loop.
        if (!dragging && distance == 0f) return out;
        float d = distance;
        if (stage == CANCEL) {
            float t = clamp(terminalAge / (float) workshop.get("cancel_ms"), 0, 1);
            d = terminalDistance * (1f - t * t);
        } else if (stage == COMPLETE) {
            float t = clamp(terminalAge / (float) UNLOCK_MS, 0, 1);
            d = terminalDistance + (fullRadius() - terminalDistance) * t * t;
        }
        out.distance = d;
        if (d <= out.boundary) {
            out.outerRadius = (workshop.enabled ? customOuterRadius(d, out.minRadius, out.boundary) : outerRadius(d, out.minRadius, out.boundary));
            out.innerRadius = innerRadius(d, out.minRadius, out.boundary);
        } else {
            out.outerRadius = d;
            out.innerRadius = out.boundary + .5f * (d - out.boundary);
        }
        float alpha = 1f - workshop.get("alpha_falloff") * normalize(0f, out.boundary, d);
        // Q.f is the primary texture MODEL SCALE, not uAlphaBG (Q.b). Do not
        // mistake the donor's zoom uniform for an opacity multiplier.
        out.primaryScale = 1f + workshop.get("texture_growth") * normalize(out.boundary / 4f, fullRadius(), d);
        out.bandAlpha = alpha * (1f - workshop.get("band_base_alpha")) + workshop.get("band_base_alpha");
        out.visible = out.running = true;
        return out;
    }

    private float customOuterRadius(float d, float min, float boundary) {
        float knee = workshop.get("outer_knee");
        if (d < boundary * .5f) return min + d / (boundary * .5f) * (boundary * knee - min);
        if (d >= boundary) return boundary;
        return boundary * knee + (d - boundary * .5f) / (boundary * .5f) * boundary * (1f - knee);
    }

    static float outerRadius(float d, float min, float boundary) {
        if (d < boundary * .5f) return min + d / (boundary * .5f) * (boundary * .9266f - min);
        if (d >= boundary) return boundary;
        return boundary * .9266f + (d - boundary * .5f) / (boundary * .5f) * boundary * .0734f;
    }
    static float innerRadius(float d, float min, float boundary) {
        if (d < boundary * .5f) return d / (boundary * .5f) * min;
        if (d >= boundary) return boundary;
        return min + (d - boundary * .5f) / (boundary * .5f) * (boundary - min);
    }
    static float normalize(float from, float to, float value) {
        return to <= from ? 0 : clamp((value - from) / (to - from), 0, 1);
    }
    static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }

    static final class Frame {
        boolean visible, running, tap, fullUnderlay;
        int stage, palette;
        long ageMs;
        float x, y, distance, outerRadius, innerRadius, minRadius, boundary, density;
        float tapProgress, bandAlpha, alpha, primaryScale;
        void clear() {
            visible = running = tap = fullUnderlay = false;
            stage = IDLE;
            x = y = distance = outerRadius = innerRadius = tapProgress = bandAlpha = alpha = 0;
            primaryScale = 1f;
        }
    }
}
