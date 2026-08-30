package com.codex.lle;

/* JADX INFO: loaded from: XperiaBlindsEffectViewTest.class */
public final class XperiaBlindsEffectViewTest {
    private static final float EPSILON = 1.0E-5f;

    private XperiaBlindsEffectViewTest() {
    }

    public static void main(String[] strArr) {
        testNoElapsedTimeDoesNotMove();
        testPullsTowardTarget();
        testReleasePullsTowardRest();
        testRefreshCadenceIsStable();
        testStallIsBounded();
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
}
