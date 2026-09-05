package com.codex.lle;

/** CPU translation of LG's White Hole vertex-shader texture displacement. */
final class LgWhiteHoleWarp {
    // The donor's 150 dp band spreads too far on modern tall, high-density panels.
    // Preserve its curve and strength while tightening only the affected perimeter.
    static final float BAND_WIDTH_DP = 100f;
    static final float ABSORB_STRENGTH = .48f;
    static final float EDGE_STRENGTH = .14f;

    private LgWhiteHoleWarp() { }

    static float bandWidth(float density) {
        return BAND_WIDTH_DP * Math.max(0f, density);
    }

    static boolean active(float holeRadius, float absorbRadius, float bandWidth) {
        return absorbRadius > 0f && bandWidth > 0f
                && holeRadius < absorbRadius + bandWidth;
    }

    static float normal(float distance, float holeRadius,
            float absorbRadius, float bandWidth) {
        float outerRadius = absorbRadius + bandWidth;
        if (distance < holeRadius || distance >= outerRadius || !active(
                holeRadius, absorbRadius, bandWidth)) {
            return 0f;
        }
        float denominator = holeRadius >= absorbRadius ? bandWidth : outerRadius;
        return clamp((outerRadius - distance) / Math.max(.001f, denominator), 0f, 1f);
    }

    static float strength(float holeRadius, float absorbRadius) {
        return holeRadius >= absorbRadius ? EDGE_STRENGTH : ABSORB_STRENGTH;
    }

    static float displacement(float distance, float holeRadius,
            float absorbRadius, float bandWidth, float viewportWidth) {
        float normal = normal(distance, holeRadius, absorbRadius, bandWidth);
        return strength(holeRadius, absorbRadius) * normal * normal
                * Math.max(0f, viewportWidth);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
