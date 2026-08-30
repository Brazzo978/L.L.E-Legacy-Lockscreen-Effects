package com.codex.lle;

/** Deterministic timing and geometry checks for the S3 None reconstruction. */
public final class NoneCircleUnlockEffectViewTest {
    private static final float EPSILON = 0.00002f;

    private NoneCircleUnlockEffectViewTest() {
    }

    public static void main(String[] args) {
        assertLong("OEM enter duration", 666L,
                NoneCircleUnlockEffectView.Timing.ENTER_DURATION_MS);
        assertLong("OEM exit duration", 333L,
                NoneCircleUnlockEffectView.Timing.EXIT_DURATION_MS);
        assertLong("arrow half cycle", 500L,
                NoneCircleUnlockEffectView.Timing.ARROW_HALF_CYCLE_MS);
        assertLong("affordance exit starts", 466L,
                NoneCircleUnlockEffectView.Timing.AFFORDANCE_EXIT_START_MS);
        assertLong("affordance total", 1166L,
                NoneCircleUnlockEffectView.Timing.AFFORDANCE_TOTAL_MS);

        assertNear("enter begins at zero", 0f,
                NoneCircleUnlockEffectView.Timing.enterValue(0L));
        assertNear("quint enter midpoint", 0.96875f,
                NoneCircleUnlockEffectView.Timing.enterValue(333L));
        assertNear("enter completes", 1f,
                NoneCircleUnlockEffectView.Timing.enterValue(666L));
        assertNear("exit begins opaque", 1f,
                NoneCircleUnlockEffectView.Timing.exitRemaining(0L));
        // 333 ms has no integer midpoint; 166 / 333 leaves this exact sampled remainder.
        assertNear("quint exit midpoint sample", 0.03172207f,
                NoneCircleUnlockEffectView.Timing.exitRemaining(166L));
        assertNear("exit completes", 0f,
                NoneCircleUnlockEffectView.Timing.exitRemaining(333L));

        assertNear("drag inside minimum radius", 0f,
                NoneCircleUnlockEffectView.Timing.dragProgress(20f, 40f, 140f));
        assertNear("drag midpoint", 0.5f,
                NoneCircleUnlockEffectView.Timing.dragProgress(90f, 40f, 140f));
        assertNear("drag at maximum radius", 1f,
                NoneCircleUnlockEffectView.Timing.dragProgress(140f, 40f, 140f));
        assertNear("lock sequence begins on frame zero", 0f,
                NoneCircleUnlockEffectView.Timing.lockSequenceProgress(0f));
        assertNear("lock sequence quantizes to thirty frames", 14f / 29f,
                NoneCircleUnlockEffectView.Timing.lockSequenceProgress(0.5f));
        assertNear("lock sequence reaches final frame", 1f,
                NoneCircleUnlockEffectView.Timing.lockSequenceProgress(1f));
        assertInt("lock sequence begins with OEM frame 01", 0,
                NoneCircleUnlockEffectView.Timing.lockFrameIndex(0f));
        assertInt("lock sequence midpoint is OEM frame 15", 14,
                NoneCircleUnlockEffectView.Timing.lockFrameIndex(0.5f));
        assertInt("lock sequence ends with OEM frame 30", 29,
                NoneCircleUnlockEffectView.Timing.lockFrameIndex(1f));

        assertNear("arrow starts hidden", 0f,
                NoneCircleUnlockEffectView.Timing.arrowPulse(0L, 0f));
        assertNear("arrow first half", 0.5f,
                NoneCircleUnlockEffectView.Timing.arrowPulse(250L, 0f));
        assertNear("arrow first peak", 1f,
                NoneCircleUnlockEffectView.Timing.arrowPulse(500L, 0f));
        assertNear("arrow reverse half", 0.5f,
                NoneCircleUnlockEffectView.Timing.arrowPulse(750L, 0f));
        assertNear("drag hides arrow", 0f,
                NoneCircleUnlockEffectView.Timing.arrowPulse(250L, 0.41f));

        assertNear("affordance remains full before exit", 1f,
                NoneCircleUnlockEffectView.Timing.affordanceRemaining(466L));
        assertNear("affordance completes", 0f,
                NoneCircleUnlockEffectView.Timing.affordanceRemaining(1166L));
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

    private static void assertInt(String name, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }
}
