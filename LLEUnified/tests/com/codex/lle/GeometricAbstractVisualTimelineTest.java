package com.codex.lle;

/**
 * Host seam test for the opt-in visual clocks and the recovered Geometric unlock curve.  It
 * intentionally has no GLES context: GPU resource checks remain in the ARM64 build/runtime path.
 */
public final class GeometricAbstractVisualTimelineTest {
    private static final long BASE_NS = 1_000_000_000L;
    private static final float EPSILON = 0.00001f;

    private GeometricAbstractVisualTimelineTest() {
    }

    public static void main(String[] args) {
        testGeometricVisualTimelineMonotonicity();
        testGeometricUnlockPacingEvidence();
        testAbstractNativeStepTimelineOnly();
        testGeometricUnlockExpansionAndFade();
        testGeometricUnlockRearmsStaleTerminalRecord();
        testGeometricUnlockHandoffCutsAtFullCoverage();
    }

    private static void testGeometricUnlockPacingEvidence() {
        GeometricMosaicArm64EffectView.UnlockFramePacing pacing =
                new GeometricMosaicArm64EffectView.UnlockFramePacing();
        pacing.begin();
        pacing.recordFrame(BASE_NS, BASE_NS + 2_000_000L);
        pacing.recordFrame(BASE_NS + 16_000_000L, BASE_NS + 19_000_000L);
        pacing.recordFrame(BASE_NS + 66_000_000L, BASE_NS + 101_000_000L);
        String summary = pacing.finishIfActive();
        if (summary == null || !summary.contains("frames=3") || !summary.contains("jank=1")
                || !summary.contains("maxGapMs=50") || !summary.contains("maxDrawMs=35")) {
            throw new AssertionError("unexpected unlock pacing=" + summary);
        }
    }

    private static void testGeometricVisualTimelineMonotonicity() {
        GeometricMosaicArm64EffectView.VisualTimeline timeline =
                new GeometricMosaicArm64EffectView.VisualTimeline();
        assertLong("first sample", BASE_NS, timeline.sample(BASE_NS));
        assertLong("stock scale", BASE_NS + 16_000_000L,
                timeline.sample(BASE_NS + 16_000_000L));

        timeline.reset();
        assertLong("late frame preserves wall-clock timing", BASE_NS + 116_000_000L,
                timeline.sample(BASE_NS + 116_000_000L));
        assertLong("non-monotonic frame cannot replay", BASE_NS + 116_000_000L,
                timeline.sample(BASE_NS + 110_000_000L));
    }

    private static void testAbstractNativeStepTimelineOnly() {
        AbstractTilesArm64EffectView.ElapsedClock clock =
                new AbstractTilesArm64EffectView.ElapsedClock();
        assertNear("first abstract frame", 0.0f, clock.advance(BASE_NS));
        assertNear("abstract stock cadence", 0.012f,
                clock.advance(BASE_NS + 12_000_000L));
    }

    private static void testGeometricUnlockExpansionAndFade() {
        assertNear("unlock initial radius", 0.3f,
                GeometricMosaicGlesPipeline.unlockRadius(0.3f, 0.0f));
        assertNear("unlock midpoint radius", 2.65f,
                GeometricMosaicGlesPipeline.unlockRadius(0.3f, 0.20f));
        assertNear("unlock terminal radius", 5.0f,
                GeometricMosaicGlesPipeline.unlockRadius(0.3f, 0.40f));
        assertNear("full alpha through coverage boundary", 1.0f,
                GeometricMosaicGlesPipeline.unlockFadeAlpha(1.0f, 0.40f));
        assertNear("fade initial alpha", 1.0f,
                GeometricMosaicGlesPipeline.unlockFadeAlpha(1.0f, 0.0f));
        assertNear("fade midpoint alpha", 0.29289323f,
                GeometricMosaicGlesPipeline.unlockFadeAlpha(1.0f, 0.70f));
        assertNear("fade terminal alpha", 0.0f,
                GeometricMosaicGlesPipeline.unlockFadeAlpha(1.0f, 1.00f));
    }

    private static void testGeometricUnlockRearmsStaleTerminalRecord() {
        GeometricMosaicGlesPipeline pipeline = new GeometricMosaicGlesPipeline();
        if (!pipeline.addTouch(0.20f, 0.30f, BASE_NS)) {
            throw new AssertionError("initial touch was not recorded");
        }
        pipeline.advanceAnimationForTest(BASE_NS + 150_000_000L);
        pipeline.advanceAnimationForTest(BASE_NS + 751_000_000L);
        long releaseNs = BASE_NS + 760_000_000L;
        if (!pipeline.unlockAt(0.204f, 0.306f, releaseNs)) {
            throw new AssertionError("stale terminal unlock was not rearmed");
        }
        if (!pipeline.isTerminalUnlockArmedForTest()) {
            throw new AssertionError("terminal record is not armed");
        }
        assertNear("terminal x bypasses trail threshold", 0.204f,
                pipeline.terminalTouchXForTest());
        assertNear("terminal y bypasses trail threshold", 0.306f,
                pipeline.terminalTouchYForTest());
        long coverageNs = releaseNs + 400_000_000L;
        pipeline.advanceAnimationForTest(coverageNs);
        assertNear("terminal radius reaches full coverage", 5.0f,
                pipeline.terminalRadiusForTest(coverageNs));
        assertNear("scene remains opaque at full coverage", 1.0f,
                pipeline.sceneAlphaForTest());
    }

    private static void testGeometricUnlockHandoffCutsAtFullCoverage() {
        long coverageMs = GeometricMosaicGlesPipeline.unlockCoverageDelayMs();
        long handoffMs = GeometricMosaicGlesPipeline.unlockHandoffDelayMs();
        long completeMs = GeometricMosaicGlesPipeline.unlockCompleteDelayMs();
        assertLong("full coverage dispatch boundary", 400L, coverageMs);
        assertLong("coverage plus fade duration", 1_000L, completeMs);
        assertLong("handoff and neutralization occur at full coverage", coverageMs, handoffMs);
        if (handoffMs >= completeMs) {
            throw new AssertionError("handoff waited for the internal fade: dispatch="
                    + handoffMs + " complete=" + completeMs);
        }
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
