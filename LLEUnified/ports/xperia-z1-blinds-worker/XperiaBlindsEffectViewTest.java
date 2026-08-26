package com.codex.lle;

/** Host-only oracle checks for the scalar parts of the Xperia Z1 Blinds port. */
public final class XperiaBlindsEffectViewTest {
    private static final float EPSILON = 1.0e-5f;

    private XperiaBlindsEffectViewTest() { }

    public static void main(String[] args) {
        testDonorConstants();
        testAffectedWindowAtCentre();
        testAffectedWindowAtEdge();
        testBandMappingHasNoOverlap();
        testWaveMatchesDonorFormula();
        testNoElapsedTimeDoesNotMove();
        testSpringPullsBothDirections();
        testRefreshCadenceIsStable();
        testStallIsBounded();
    }

    private static void testDonorConstants() {
        assertEquals("strip count", 17, XperiaBlindsEffectView.STRIP_COUNT);
        assertEquals("affected strip count", 5, XperiaBlindsEffectView.AFFECTED_STRIP_COUNT);
        assertNear("affected range", 5f / 17f, XperiaBlindsEffectView.AFFECTED_RANGE);
        assertNear("fold degrees", 3f, XperiaBlindsEffectView.MAX_FOLD_DEGREES);
        assertNear("camera depth", 3f, XperiaBlindsEffectView.CAMERA_DEPTH);
        assertNear("spring stiffness", 400f, XperiaBlindsEffectView.SPRING_STIFFNESS);
        assertNear("spring damping", .85f, XperiaBlindsEffectView.SPRING_DAMPING_RATIO);
    }

    private static void testAffectedWindowAtCentre() {
        assertEquals("centre start", 6, XperiaBlindsEffectView.affectedStart(.5f));
        assertEquals("centre end", 11, XperiaBlindsEffectView.affectedEnd(.5f));
    }

    private static void testAffectedWindowAtEdge() {
        assertEquals("top start", 0, XperiaBlindsEffectView.affectedStart(0f));
        assertEquals("top end", 2, XperiaBlindsEffectView.affectedEnd(0f));
        assertEquals("bottom start", 15, XperiaBlindsEffectView.affectedStart(1f));
        assertEquals("bottom end", 17, XperiaBlindsEffectView.affectedEnd(1f));
    }

    private static void testBandMappingHasNoOverlap() {
        int previous = 0;
        for (int strip = 0; strip <= XperiaBlindsEffectView.STRIP_COUNT; strip++) {
            int top = XperiaBlindsEffectView.bandTop(1700, strip);
            if (top < previous) throw new AssertionError("band mapping regressed at strip " + strip);
            previous = top;
        }
        assertEquals("last band ends at bitmap height", 1700,
                XperiaBlindsEffectView.bandTop(1700, XperiaBlindsEffectView.STRIP_COUNT));
    }

    private static void testWaveMatchesDonorFormula() {
        assertNear("centre strip is unrotated", 0f, XperiaBlindsEffectView.stripWave(8, .5f));
        assertNear("one strip above centre", -.95105654f, XperiaBlindsEffectView.stripWave(7, .5f));
        assertNear("one strip below centre", .95105654f, XperiaBlindsEffectView.stripWave(9, .5f));
    }

    private static void testNoElapsedTimeDoesNotMove() {
        float[] step = XperiaBlindsEffectView.springStep(.25f, .5f, 1f, 0f);
        assertNear("zero position", .25f, step[0]);
        assertNear("zero velocity", .5f, step[1]);
    }

    private static void testSpringPullsBothDirections() {
        float[] pressed = XperiaBlindsEffectView.springStep(0f, 0f, 1f, 1f / 120f);
        if (pressed[0] <= 0f || pressed[1] <= 0f) throw new AssertionError("pressed spring did not advance");
        float[] released = XperiaBlindsEffectView.springStep(1f, 0f, 0f, 1f / 120f);
        if (released[0] >= 1f || released[1] >= 0f) throw new AssertionError("released spring did not return");
    }

    private static void testRefreshCadenceIsStable() {
        float baseline = simulateOneSecond(60);
        for (int hz : new int[] {90, 120, 144}) assertNear("one second at " + hz + "Hz", baseline, simulateOneSecond(hz));
    }

    private static void testStallIsBounded() {
        float[] stalled = XperiaBlindsEffectView.springStep(0f, 0f, 1f, 1f);
        float[] capped = XperiaBlindsEffectView.springStep(0f, 0f, 1f, .05f);
        assertNear("stalled position cap", capped[0], stalled[0]);
        assertNear("stalled velocity cap", capped[1], stalled[1]);
    }

    private static float simulateOneSecond(int hz) {
        float position = 0f;
        float velocity = 0f;
        for (int frame = 0; frame < hz; frame++) {
            float[] step = XperiaBlindsEffectView.springStep(position, velocity, 1f, 1f / hz);
            position = step[0];
            velocity = step[1];
        }
        return position;
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
    }

    private static void assertNear(String label, float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
    }
}
