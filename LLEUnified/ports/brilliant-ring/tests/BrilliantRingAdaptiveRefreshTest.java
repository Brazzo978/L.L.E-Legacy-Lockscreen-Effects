package com.codex.lle;

/** Deterministic host-free checks for Brilliant Ring's opt-in native-refresh clock/math. */
public final class BrilliantRingAdaptiveRefreshTest {
    private static final float EPSILON = 0.0002f;

    public static void main(String[] args) {
        verifyNominalCadences();
        verifyJitterCadence();
        verifyStallDoesNotCreateBacklog();
        verifyZeroCreditFrameContract();
        verifyRefreshTransitionKeepsTemporalState();
        verifyUnitCreditUsesStockMath();
        verifyTimeoutAndTerminalCreditBoundaries();
        verifyNoiseCreditCadence();
        verifyNoisePhaseRecreation();
        System.out.println("BrilliantRingAdaptiveRefreshTest: PASS");
    }

    private static void verifyNominalCadences() {
        verifyCadence(60);
        verifyCadence(90);
        verifyCadence(120);
        verifyCadence(144);
    }

    private static void verifyCadence(int refreshHz) {
        BrilliantRingGlesPipeline.AdaptiveSimulationClock clock =
                new BrilliantRingGlesPipeline.AdaptiveSimulationClock();
        long start = 5_000_000_000L;
        requireClose("first " + refreshHz + " Hz frame", 0.0f, clock.advance(start));
        float credits = 0.0f;
        for (int frame = 1; frame <= refreshHz; ++frame) {
            long now = start + frame * 1_000_000_000L / refreshHz;
            float frameCredits = clock.advance(now);
            require("live " + refreshHz + " Hz frame " + frame,
                    frameCredits > 0.0f);
            credits += frameCredits;
        }
        requireClose(refreshHz + " Hz one-second wall-clock credits", 60.0f, credits);
    }

    private static void verifyJitterCadence() {
        BrilliantRingGlesPipeline.AdaptiveSimulationClock clock =
                new BrilliantRingGlesPipeline.AdaptiveSimulationClock();
        long now = 9_000_000_000L;
        requireClose("jitter first frame", 0.0f, clock.advance(now));
        long[] deltas = {
                7_900_000L, 8_700_000L, 8_100_000L, 8_566_667L,
                8_300_000L, 8_366_667L, 8_200_000L, 8_466_667L
        };
        float actualCredits = 0.0f;
        long totalNanos = 0L;
        for (long delta : deltas) {
            now += delta;
            totalNanos += delta;
            actualCredits += clock.advance(now);
        }
        float expectedCredits = totalNanos
                / (float) BrilliantRingGlesPipeline.STOCK_SIMULATION_TICK_NS;
        requireClose("jitter preserves total logical time", expectedCredits, actualCredits);
    }

    private static void verifyStallDoesNotCreateBacklog() {
        BrilliantRingGlesPipeline.AdaptiveSimulationClock clock =
                new BrilliantRingGlesPipeline.AdaptiveSimulationClock();
        long now = 12_000_000_000L;
        requireClose("stall first frame", 0.0f, clock.advance(now));
        now += 8_333_333L;
        requireClose("pre-stall live frame", 0.5f, clock.advance(now));
        now += BrilliantRingGlesPipeline.ADAPTIVE_STALL_NS + 1L;
        requireClose("stalled frame discarded", 0.0f, clock.advance(now));
        now += 8_333_333L;
        requireClose("post-stall frame has no debt", 0.5f, clock.advance(now));
    }

    private static void verifyUnitCreditUsesStockMath() {
        require("one credit selects stock implementation",
                BrilliantRingGlesPipeline.usesExactStockStep(1.0f));
        require("fractional credit avoids stock implementation",
                !BrilliantRingGlesPipeline.usesExactStockStep(0.5f));
        requireClose("one logical age credit", 13.0f,
                BrilliantRingGlesPipeline.advanceLogicalAge(12.0f, 1.0f));
        requireClose("unlock outer multiplier at one credit", 1.075f,
                BrilliantRingGlesPipeline.scaleTickMultiplier(1.075f, 1.0f));
        requireClose("unlock inner multiplier at one credit", 1.01f,
                BrilliantRingGlesPipeline.scaleTickMultiplier(1.01f, 1.0f));
        requireClose("normal-ring unlock scale is independent of display delta", 1.225f,
                BrilliantRingGlesPipeline.applyNormalUnlockInnerScale(1.0f));
        requireClose("forced fade at one credit", 0.95f,
                BrilliantRingGlesPipeline.scaleLinearFade(1.0f, 0.05f, 1.0f));
        requireClose("stock nominal timestamp gives one credit", 1.0f,
                BrilliantRingGlesPipeline.adaptiveSimulationCreditsForElapsedNanos(
                        BrilliantRingGlesPipeline.STOCK_SIMULATION_TICK_NS));
    }

    private static void verifyRefreshTransitionKeepsTemporalState() {
        BrilliantRingGlesPipeline.AdaptiveStepMode mode =
                new BrilliantRingGlesPipeline.AdaptiveStepMode();
        require("fresh 60 Hz adaptive trace may use exact stock step",
                mode.usesStockStep(1.0f));
        mode.record(1.0f);
        require("60 Hz trace remains stock-compatible before a fractional delta",
                mode.usesStockStep(1.0f));
        mode.record(0.5f); // 120 Hz frame: adaptive age/timeout/noise state now exists.
        require("120 Hz delta leaves the integer branch", !mode.usesStockStep(0.5f));
        require("returning to 60 Hz cannot jump back into integer state",
                !mode.usesStockStep(1.0f));
        float first120Age = BrilliantRingGlesPipeline.firstAdaptiveAgeAfterLegacyRender(
                12.0f, 0.5f);
        requireClose("60->120 handoff starts halfway after the last displayed stock age",
                12.5f, first120Age);
        requireClose("second 120 Hz sample reaches the next stock age",
                13.0f, BrilliantRingGlesPipeline.advanceLogicalAge(first120Age, 0.5f));
        mode.reset();
        require("reset isolates the next trace", mode.usesStockStep(1.0f));
    }

    private static void verifyZeroCreditFrameContract() {
        require("first/stall zero credit is a valid input-and-redraw frame",
                BrilliantRingGlesPipeline.isAdaptiveZeroCreditFrame(0.0f));
        require("negative zero is also a zero-duration redraw",
                BrilliantRingGlesPipeline.isAdaptiveZeroCreditFrame(-0.0f));
        require("a live fractional frame is not a zero-duration redraw",
                !BrilliantRingGlesPipeline.isAdaptiveZeroCreditFrame(0.5f));
    }

    private static void verifyNoiseCreditCadence() {
        requireClose("noise target starts at zero", 0.0f,
                BrilliantRingGlesPipeline.adaptiveNoiseInterpolationForCredits(0.0f));
        requireClose("stock's last integer noise sample", 0.95f,
                BrilliantRingGlesPipeline.adaptiveNoiseInterpolationForCredits(19.0f));
        requireClose("adaptive noise keeps the stock .95 wrap anchor", 0.95f,
                BrilliantRingGlesPipeline.adaptiveNoiseInterpolationForCredits(20.0f));
        requireClose("noise interpolation never synthesizes t=1", 0.95f,
                BrilliantRingGlesPipeline.adaptiveNoiseInterpolationForCredits(21.0f));
    }

    private static void verifyTimeoutAndTerminalCreditBoundaries() {
        require("MOVE timeout waits for 61 logical credits",
                !BrilliantRingGlesPipeline.emissionTimeoutReached(60.999f));
        require("MOVE timeout fires on logical credit 61",
                BrilliantRingGlesPipeline.emissionTimeoutReached(61.0f));
        require("normal ring keeps its final fractional pre-66 frame",
                BrilliantRingGlesPipeline.isAdaptiveRecordAgeVisible(0, 65.999f));
        require("normal ring removes at 66", !BrilliantRingGlesPipeline
                .isAdaptiveRecordAgeVisible(0, 66.0f));
        require("unlock ring keeps its final fractional pre-56 frame",
                BrilliantRingGlesPipeline.isAdaptiveRecordAgeVisible(1, 55.999f));
        require("unlock ring removes at 56", !BrilliantRingGlesPipeline
                .isAdaptiveRecordAgeVisible(1, 56.0f));
    }

    private static void verifyNoisePhaseRecreation() {
        BrilliantRingGlesPipeline.AdaptiveNoisePhase phase =
                new BrilliantRingGlesPipeline.AdaptiveNoisePhase();
        phase.initializeFromStockCounter(-1);
        requireClose("recreated adaptive phase starts from global -1", -1.0f, phase.credits());
        phase.setCredits(-0.5f);
        require("fraction before first target retains stock phase", phase.stockCounter() == -1);
        phase.setCredits(0.0f);
        require("first logical target boundary mirrors global zero", phase.stockCounter() == 0);

        phase.syncStockCounter(7);
        phase.setCredits(7.75f);
        require("fractional adaptive phase does not advance global early",
                phase.stockCounter() == 7);
        // A new pipeline attaches to this same process-global phase rather than resetting it
        // to an integer stock counter and losing .75 logical credit.
        phase.initializeFromStockCounter(7);
        requireClose("surface recreation retains global fractional phase", 7.75f,
                phase.credits());
        phase.setCredits(8.0f);
        require("completed logical credit advances mirrored global phase",
                phase.stockCounter() == 8);
        phase.initializeFromStockCounter(8);
        requireClose("toggle/recreation retains the latest global cadence", 8.0f,
                phase.credits());
    }

    private static void requireClose(String label, float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void require(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private BrilliantRingAdaptiveRefreshTest() {
    }
}
