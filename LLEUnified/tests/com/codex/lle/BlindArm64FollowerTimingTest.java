package com.codex.lle;

/** Deterministic seam test for Blind's stock-60 Hz follower normalization. */
public final class BlindArm64FollowerTimingTest {
    private static final long STOCK_FRAME_NS = 16_666_667L;
    private static final float STOCK_FOLLOW = 0.17f;
    private static final float EPSILON = 0.000002f;

    private BlindArm64FollowerTimingTest() {
    }

    public static void main(String[] args) {
        testStock60HzCoefficient();
        testRefreshCadences();
        testJitterAndStall();
    }

    private static void testStock60HzCoefficient() {
        assertNear("one stock tick", STOCK_FOLLOW,
                BlindArm64EffectView.moveFollowForElapsedNanos(STOCK_FRAME_NS));
        assertNear("zero elapsed", 0f,
                BlindArm64EffectView.moveFollowForElapsedNanos(0L));
    }

    private static void testRefreshCadences() {
        float stockPosition = followAtCadence(60);
        for (int refreshHz : new int[] {90, 120, 144}) {
            assertNear("one second at " + refreshHz + " Hz", stockPosition,
                    followAtCadence(refreshHz));
        }
    }

    private static void testJitterAndStall() {
        float position = 0f;
        position = step(position, 12_000_000L);
        position = step(position, 21_000_000L);
        position = step(position, 17_000_000L);
        float expected = stockPositionForElapsed(50_000_000L);
        assertNear("jitter total", expected, position);

        float beforeStall = step(0f, STOCK_FRAME_NS);
        float afterStall = step(beforeStall, STOCK_FRAME_NS * 4L);
        assertNear("stall equals four virtual ticks", applyStockTicks(beforeStall, 4), afterStall);
    }

    private static float followAtCadence(int refreshHz) {
        float position = 0f;
        long previousNs = 0L;
        for (int frame = 1; frame <= refreshHz; ++frame) {
            long nowNs = Math.round(frame * 1_000_000_000.0d / refreshHz);
            position = step(position, nowNs - previousNs);
            previousNs = nowNs;
        }
        return position;
    }

    private static float stockPositionForElapsed(long elapsedNs) {
        return 1f - (float) Math.pow(1.0d - STOCK_FOLLOW, elapsedNs / (double) STOCK_FRAME_NS);
    }

    private static float step(float position, long elapsedNs) {
        return position + (1f - position)
                * BlindArm64EffectView.moveFollowForElapsedNanos(elapsedNs);
    }

    private static float applyStockTicks(float position, int ticks) {
        for (int i = 0; i < ticks; ++i) {
            position += (1f - position) * STOCK_FOLLOW;
        }
        return position;
    }

    private static void assertNear(String name, float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }
}
