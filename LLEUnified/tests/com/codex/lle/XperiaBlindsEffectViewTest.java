package com.codex.lle;

/* JADX INFO: loaded from: XperiaBlindsEffectViewTest.class */
public final class XperiaBlindsEffectViewTest {
    private static final float EPSILON = 1.0E-5f;

    private XperiaBlindsEffectViewTest() {
    }

    public static void main(String[] strArr) {
        testDonorConstants();
        testAffectedWindowAtCentre();
        testAffectedWindowAtEdge();
        testBandMappingHasNoOverlap();
        testWaveAndFoldMatchDonorFormula();
        testNoElapsedTimeDoesNotMove();
        testPullsTowardTarget();
        testReleasePullsTowardRest();
        testRefreshCadenceIsStable();
        testStallIsBounded();
    }

    private static void testDonorConstants() {
        assertEquals("strip count", 17, XperiaBlindsEffectView.STRIP_COUNT);
        assertEquals("affected strip count", 5,
                XperiaBlindsEffectView.AFFECTED_STRIP_COUNT);
        assertNear("affected range", 5f / 17f,
                XperiaBlindsEffectView.AFFECTED_RANGE);
        assertNear("horizontal fold degrees", 3f,
                XperiaBlindsEffectView.HORIZONTAL_FOLD_DEGREES);
        assertNear("camera fold degrees", 17f,
                XperiaBlindsEffectView.CAMERA_FOLD_DEGREES);
        assertNear("camera depth", 3f, XperiaBlindsEffectView.CAMERA_DEPTH);
        assertNear("shade strength", 2f, XperiaBlindsEffectView.SHADE_STRENGTH);
        assertNear("spring stiffness", 400f,
                XperiaBlindsEffectView.SPRING_STIFFNESS);
        assertNear("spring damping", .85f,
                XperiaBlindsEffectView.SPRING_DAMPING_RATIO);
        assertEquals("strip fade", 300,
                (int) XperiaBlindsEffectView.STRIP_FADE_MS);
        assertEquals("global fade delay", 40,
                (int) XperiaBlindsEffectView.EXIT_FADE_DELAY_MS);
        assertEquals("exit complete", 200,
                (int) XperiaBlindsEffectView.EXIT_COMPLETE_MS);
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
            if (top < previous) {
                throw new AssertionError("band mapping regressed at strip " + strip);
            }
            previous = top;
        }
        assertEquals("last band ends at bitmap height", 1700,
                XperiaBlindsEffectView.bandTop(
                        1700, XperiaBlindsEffectView.STRIP_COUNT));
    }

    private static void testWaveAndFoldMatchDonorFormula() {
        assertNear("centre strip wave", 0f,
                XperiaBlindsEffectView.stripWave(8, .5f));
        assertNear("one strip above centre", -.95105654f,
                XperiaBlindsEffectView.stripWave(7, .5f));
        assertNear("one strip below centre", .95105654f,
                XperiaBlindsEffectView.stripWave(9, .5f));
        assertNear("centre fold reaches two", 2f,
                XperiaBlindsEffectView.stripFold(8, .5f, 1f));
    }

    private static void testNoElapsedTimeDoesNotMove() {
        float[] fArrSpringStep = XperiaBlindsEffectView.springStep(0.25f, 0.5f, 1.0f, 0.0f);
        assertNear("zero position", 0.25f, fArrSpringStep[0]);
        assertNear("zero velocity", 0.5f, fArrSpringStep[1]);
    }

    private static void testPullsTowardTarget() {
        float[] fArrSpringStep = XperiaBlindsEffectView.springStep(0.0f, 0.0f, 1.0f, 0.008333334f);
        if (fArrSpringStep[0] <= 0.0f || fArrSpringStep[1] <= 0.0f) {
            throw new AssertionError("spring did not advance toward pressed target");
        }
    }

    private static void testReleasePullsTowardRest() {
        float[] fArrSpringStep = XperiaBlindsEffectView.springStep(1.0f, 0.0f, 0.0f, 0.008333334f);
        if (fArrSpringStep[0] >= 1.0f || fArrSpringStep[1] >= 0.0f) {
            throw new AssertionError("spring did not advance toward release target");
        }
    }

    private static void testRefreshCadenceIsStable() {
        float fSimulateOneSecond = simulateOneSecond(60);
        for (int i : new int[]{90, 120, 144}) {
            assertNear("one pressed second at " + i + "Hz", fSimulateOneSecond, simulateOneSecond(i));
        }
    }

    private static void testStallIsBounded() {
        float[] fArrSpringStep = XperiaBlindsEffectView.springStep(0.0f, 0.0f, 1.0f, 1.0f);
        float[] fArrSpringStep2 = XperiaBlindsEffectView.springStep(0.0f, 0.0f, 1.0f, 0.05f);
        assertNear("one-second stall bounded to 50ms", fArrSpringStep2[0], fArrSpringStep[0]);
        assertNear("one-second stall velocity bounded to 50ms", fArrSpringStep2[1], fArrSpringStep[1]);
    }

    private static float simulateOneSecond(int i) {
        float f = 0.0f;
        float f2 = 0.0f;
        for (int i2 = 0; i2 < i; i2++) {
            float[] fArrSpringStep = XperiaBlindsEffectView.springStep(f, f2, 1.0f, 1.0f / i);
            f = fArrSpringStep[0];
            f2 = fArrSpringStep[1];
        }
        return f;
    }

    private static void assertNear(String str, float f, float f2) {
        if (Math.abs(f - f2) > EPSILON) {
            throw new AssertionError(str + " expected=" + f + " actual=" + f2);
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + " expected=" + expected
                    + " actual=" + actual);
        }
    }
}
