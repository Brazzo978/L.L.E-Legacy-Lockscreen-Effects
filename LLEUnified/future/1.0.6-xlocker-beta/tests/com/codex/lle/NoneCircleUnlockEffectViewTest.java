package com.codex.lle;

/** Deterministic timing regression checks for the Samsung None/Circle reconstruction. */
public final class NoneCircleUnlockEffectViewTest {
    private static final float EPSILON = 0.00001f;

    private NoneCircleUnlockEffectViewTest() {
    }

    public static void main(String[] args) {
        assertLong("OEM enter duration", 666L,
                NoneCircleUnlockEffectView.Timing.ENTER_DURATION_MS);
        assertLong("OEM exit duration", 333L,
                NoneCircleUnlockEffectView.Timing.EXIT_DURATION_MS);
        assertNear("enter begins transparent", 0f,
                NoneCircleUnlockEffectView.Timing.enterProgress(0L));
        assertNear("enter completes at 666 ms", 1f,
                NoneCircleUnlockEffectView.Timing.enterProgress(666L));
        assertNear("enter clamps after 666 ms", 1f,
                NoneCircleUnlockEffectView.Timing.enterProgress(1000L));
        assertNear("exit begins opaque", 0f,
                NoneCircleUnlockEffectView.Timing.exitProgress(0L));
        assertNear("exit completes at 333 ms", 1f,
                NoneCircleUnlockEffectView.Timing.exitProgress(333L));
        assertNear("stock min scale", 0.85f,
                NoneCircleUnlockEffectView.Timing.enterScale(0f));
        assertNear("stock full scale", 1f,
                NoneCircleUnlockEffectView.Timing.enterScale(1f));
        assertNear("exit expands by eight percent", 1.08f,
                NoneCircleUnlockEffectView.Timing.exitScale(1f, 1f));
    }

    private static void assertLong(String name, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNear(String name, float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }
}
