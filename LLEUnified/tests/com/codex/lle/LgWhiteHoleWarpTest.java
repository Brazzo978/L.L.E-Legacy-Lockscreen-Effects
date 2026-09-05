package com.codex.lle;

/** Host-side checks for the recovered LG White Hole shader displacement. */
public final class LgWhiteHoleWarpTest {
    private LgWhiteHoleWarpTest() { }

    public static void main(String[] args) {
        near("modernized band at xhdpi", 200f, LgWhiteHoleWarp.bandWidth(2f));
        require("active edge band", LgWhiteHoleWarp.active(100f, 100f, 150f));
        near("edge normal at absorb radius", 1f,
                LgWhiteHoleWarp.normal(100f, 100f, 100f, 150f));
        near("weak edge displacement", 140f,
                LgWhiteHoleWarp.displacement(100f, 100f, 100f, 150f, 1000f));
        near("outside band untouched", 0f,
                LgWhiteHoleWarp.displacement(250f, 100f, 100f, 150f, 1000f));
        require("completed hole has no distortion",
                !LgWhiteHoleWarp.active(250f, 100f, 150f));

        float normal = (250f - 100f) / 250f;
        near("absorb normal", normal,
                LgWhiteHoleWarp.normal(100f, 50f, 100f, 150f));
        near("absorb displacement", .48f * normal * normal * 1000f,
                LgWhiteHoleWarp.displacement(100f, 50f, 100f, 150f, 1000f));
        near("inside hole discarded", 0f,
                LgWhiteHoleWarp.displacement(49f, 50f, 100f, 150f, 1000f));
    }

    private static void require(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }

    private static void near(String label, float expected, float actual) {
        if (Math.abs(expected - actual) > .001f) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
