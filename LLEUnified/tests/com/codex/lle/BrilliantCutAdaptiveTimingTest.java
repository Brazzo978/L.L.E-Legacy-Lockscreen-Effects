package com.codex.lle;

/**
 * Host-style deterministic seam test.  Compile it with the app's Android bootclasspath and run
 * its main method; no GLES context, Android component or wall clock is required.
 */
public final class BrilliantCutAdaptiveTimingTest {
    private static final long STOCK_INTERVAL_NS = 16_666_667L;
    private static final long BASE_TIME_NS = 1_000_000_000L;
    private static final float EPSILON = 0.000002f;

    private BrilliantCutAdaptiveTimingTest() {
    }

    public static void main(String[] args) {
        testFirstFrameAndCadences();
        testJitterAndStall();
        testAdaptiveFinalFrameHold();
    }

    private static void testFirstFrameAndCadences() {
        for (int refreshHz : new int[] {60, 90, 120, 144}) {
            BrilliantCutEffectView.AdaptiveSimulationClock clock =
                    new BrilliantCutEffectView.AdaptiveSimulationClock();
            assertNear("first " + refreshHz, 0.0f, clock.consume(BASE_TIME_NS));
            long nowNs = BASE_TIME_NS;
            float elapsed = 0.0f;
            for (int frame = 1; frame <= refreshHz; ++frame) {
                long nextNs = BASE_TIME_NS
                        + Math.round(frame * 1_000_000_000.0 / refreshHz);
                elapsed += clock.consume(nextNs);
                nowNs = nextNs;
            }
            assertNear("one wall second " + refreshHz, 0.96f, elapsed);
            if (nowNs <= 0L) {
                throw new AssertionError("non-monotonic test clock");
            }
        }
    }

    private static void testJitterAndStall() {
        BrilliantCutEffectView.AdaptiveSimulationClock clock =
                new BrilliantCutEffectView.AdaptiveSimulationClock();
        assertNear("first", 0.0f, clock.consume(BASE_TIME_NS));
        assertNear("jitter", 0.01152f, clock.consume(BASE_TIME_NS + 12_000_000L));
        assertNear("stall", 0.0f, clock.consume(BASE_TIME_NS + 12_000_000L
                + STOCK_INTERVAL_NS * 4L + 1L));
        assertNear("post-stall fresh frame", 0.016f,
                clock.consume(BASE_TIME_NS + 12_000_000L + STOCK_INTERVAL_NS * 5L + 1L));
    }

    private static void testAdaptiveFinalFrameHold() {
        assertTerminalHold(60, 1, 1.0f / 60.0f);
        assertTerminalHold(90, 2, 2.0f / 90.0f);
        assertTerminalHold(120, 2, 2.0f / 120.0f);
        assertTerminalHold(144, 3, 3.0f / 144.0f);
    }

    private static void assertTerminalHold(int refreshHz, int updatesUntilClear,
            float expectedWallSeconds) {
        BrilliantCutGlesPipeline.AdaptiveFinalFrameHold hold =
                new BrilliantCutGlesPipeline.AdaptiveFinalFrameHold();
        BrilliantCutEffectView.AdaptiveSimulationClock clock =
                new BrilliantCutEffectView.AdaptiveSimulationClock();
        hold.begin();
        clock.consume(BASE_TIME_NS);
        for (int update = 1; update < updatesUntilClear; ++update) {
            float simulationStep = clock.consume(BASE_TIME_NS
                    + Math.round(update * 1_000_000_000.0 / refreshHz));
            if (!hold.advance(simulationStep)) {
                throw new AssertionError("terminal hold cleared early at " + refreshHz + " Hz");
            }
        }
        float simulationStep = clock.consume(BASE_TIME_NS
                + Math.round(updatesUntilClear * 1_000_000_000.0 / refreshHz));
        if (hold.advance(simulationStep)) {
            throw new AssertionError("terminal hold did not clear at " + refreshHz + " Hz");
        }
        assertNear("terminal wall hold " + refreshHz,
                expectedWallSeconds, updatesUntilClear / (float) refreshHz);
    }

    private static void assertNear(String name, float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }
}
