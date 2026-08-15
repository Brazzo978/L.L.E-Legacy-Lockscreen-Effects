package com.codex.lle;

public final class CrystalPrismBetaEffectViewTest {
    private static final long BASE_MS = 1000;
    private static final float EPSILON = 1.0E-4f;

    private CrystalPrismBetaEffectViewTest() {
    }

    public static void main(String[] strArr) {
        testSpeedSanitization();
        testRetractIsBoundedAndCompletes();
        testUnlockWallClockDurationAtAllRefreshRates();
        testAffordanceNeverBecomesOpaque();
        testDragRadiusNeverExceedsCornerCoverage();
    }

    private static void testSpeedSanitization() {
        assertNear("NaN", 1.0f, CrystalPrismBetaEffectView.sanitizeSpeedMultiplier(Float.NaN));
        assertNear("low clamp", 0.75f, CrystalPrismBetaEffectView.sanitizeSpeedMultiplier(0.1f));
        assertNear("high clamp", 1.35f, CrystalPrismBetaEffectView.sanitizeSpeedMultiplier(2.0f));
        if (!CrystalPrismBetaEffectView.supportsHighFrameRatePresentation()) {
            throw new AssertionError("Crystal should expose HFR presentation support");
        }
        if (CrystalPrismBetaEffectView.maximumFullSizeWallpaperTextures() != 1) {
            throw new AssertionError("Crystal must stay within one full-size wallpaper texture");
        }
    }

    private static void testRetractIsBoundedAndCompletes() {
        CrystalPrismBetaEffectView.MotionPlan motionPlanNewPlan = newPlan();
        motionPlanNewPlan.begin(540.0f, 960.0f, BASE_MS);
        motionPlanNewPlan.drag(840.0f, 960.0f, 1040L);
        float f = motionPlanNewPlan.advance(1040L).radiusPx;
        motionPlanNewPlan.release(false, 1050L);
        float f2 = f;
        long j = 1060;
        while (true) {
            long j2 = j;
            if (j2 < 1330) {
                CrystalPrismBetaEffectView.MotionPlan.Frame frameAdvance = motionPlanNewPlan.advance(j2);
                if (frameAdvance.radiusPx > f2 + EPSILON) {
                    throw new AssertionError("retract radius grew");
                }
                f2 = frameAdvance.radiusPx;
                j = j2 + 20;
            } else {
                if (motionPlanNewPlan.advance(1400L).active) {
                    throw new AssertionError("retract did not complete");
                }
                return;
            }
        }
    }

    private static void testUnlockWallClockDurationAtAllRefreshRates() {
        for (int i : new int[]{60, 90, 120, 144}) {
            CrystalPrismBetaEffectView.MotionPlan motionPlanNewPlan = newPlan();
            motionPlanNewPlan.begin(540.0f, 960.0f, BASE_MS);
            motionPlanNewPlan.drag(850.0f, 960.0f, 1020L);
            motionPlanNewPlan.release(true, 1030L);
            int iFloor = (int) Math.floor((450.0d * ((double) i)) / 1000.0d);
            for (int i2 = 1; i2 <= iFloor; i2++) {
                motionPlanNewPlan.advance(1030 + Math.round((((double) i2) * 1000.0d) / ((double) i)));
            }
            if (!motionPlanNewPlan.advance(1489L).active) {
                throw new AssertionError("unlock ended before 460ms on " + i + " Hz");
            }
            if (motionPlanNewPlan.advance(1490L).active) {
                throw new AssertionError("unlock still active at 460ms on " + i + " Hz");
            }
        }
    }

    private static void testAffordanceNeverBecomesOpaque() {
        CrystalPrismBetaEffectView.MotionPlan motionPlanNewPlan = newPlan();
        motionPlanNewPlan.affordance(500.0f, 900.0f, BASE_MS);
        long j = BASE_MS;
        while (true) {
            long j2 = j;
            if (j2 < 1920) {
                CrystalPrismBetaEffectView.MotionPlan.Frame frameAdvance = motionPlanNewPlan.advance(j2);
                if (frameAdvance.opacity <= 0.2601f) {
                    j = j2 + 16;
                } else {
                    throw new AssertionError("affordance opacity is too strong=" + frameAdvance.opacity);
                }
            } else {
                if (motionPlanNewPlan.advance(1921L).active) {
                    throw new AssertionError("affordance did not complete");
                }
                return;
            }
        }
    }

    private static void testDragRadiusNeverExceedsCornerCoverage() {
        CrystalPrismBetaEffectView.MotionPlan motionPlanNewPlan = newPlan();
        motionPlanNewPlan.begin(20.0f, 20.0f, BASE_MS);
        motionPlanNewPlan.drag(10000.0f, 10000.0f, 1001L);
        if (motionPlanNewPlan.advance(1001L).radiusPx > (CrystalPrismBetaEffectView.MotionPlan.distance(20.0f, 20.0f, 1080.0f, 1920.0f) * 1.15f) + EPSILON) {
            throw new AssertionError("drag radius exceeded maximum coverage");
        }
    }

    private static CrystalPrismBetaEffectView.MotionPlan newPlan() {
        CrystalPrismBetaEffectView.MotionPlan motionPlan = new CrystalPrismBetaEffectView.MotionPlan();
        motionPlan.setViewport(1080, 1920);
        return motionPlan;
    }

    private static void assertNear(String str, float f, float f2) {
        if (Math.abs(f - f2) > EPSILON) {
            throw new AssertionError(str + " expected=" + f + " actual=" + f2);
        }
    }
}
