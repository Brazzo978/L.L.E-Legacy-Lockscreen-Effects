package com.codex.lle;

/** Host-only regression checks for the donor-derived G2 Pixelate scene. */
public final class LgPixelateSceneTest {
    private static final float EPSILON = .01f;

    private LgPixelateSceneTest() { }

    public static void main(String[] args) {
        testFixedOriginAndReversibleDrag();
        testDonorScaleAndOpacity();
        testCancelRetractThenFade();
        testUnlockAndUnderlayHold();
        testAffordanceIsFinite();
        testClockBackstepCannotResurrect();
    }

    private static void testFixedOriginAndReversibleDrag() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.begin(100f, 200f, 1000L);
        scene.move(220f, 200f, 1010L);
        LgPixelateScene.Frame far = scene.frameAt(1010L, 120f, 2400f);
        scene.move(130f, 200f, 1020L);
        LgPixelateScene.Frame near = scene.frameAt(1020L, 120f, 2400f);
        near("origin x remains fixed", 100f, near.x);
        near("origin y remains fixed", 200f, near.y);
        require("drag reverses", near.dragPx < far.dragPx);
    }

    private static void testDonorScaleAndOpacity() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.begin(0f, 0f, 100L);
        scene.move(120f, 0f, 110L);
        near("threshold reaches six times scale", 6f,
                scene.frameAt(110L, 120f, 2400f).meshScale);
        near("alpha stays full through 1.5 threshold", 1f,
                LgPixelateScene.donorAlpha(180f, 120f, 2400f));
        near("alpha reaches zero at diagonal", 0f,
                LgPixelateScene.donorAlpha(2400f, 120f, 2400f));
    }

    private static void testCancelRetractThenFade() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.begin(0f, 0f, 1000L);
        scene.move(120f, 0f, 1010L);
        scene.finish(false, 1020L);
        LgPixelateScene.Frame middleRetract = scene.frameAt(1170L, 120f, 2400f);
        require("accelerated retract remains mosaic", middleRetract.mosaicEnabled);
        require("retract keeps fixed underlay beneath radial mask",
                middleRetract.revealUnderlay);
        near("accelerated retract formula", 90f, middleRetract.dragPx);
        LgPixelateScene.Frame fade = scene.frameAt(1020L
                + LgPixelateScene.CANCEL_RETRACT_MS + 175L, 120f, 2400f);
        require("fade uses normal lockscreen without full-screen home",
                !fade.mosaicEnabled && fade.primaryVisible && !fade.revealUnderlay);
        near("fade midpoint", .5f, fade.primaryAlpha);
        require("cancel finite", !scene.frameAt(1020L + LgPixelateScene.CANCEL_RETRACT_MS
                + LgPixelateScene.CANCEL_FADE_MS, 120f, 2400f).visible);
    }

    private static void testUnlockAndUnderlayHold() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.begin(10f, 20f, 2000L);
        scene.move(130f, 20f, 2010L);
        scene.finish(true, 2020L);
        LgPixelateScene.Frame half = scene.frameAt(2220L, 120f, 2400f);
        near("accelerated unlock", 690f, half.dragPx);
        LgPixelateScene.Frame hold = scene.frameAt(2020L + LgPixelateScene.UNLOCK_MS + 75L,
                120f, 2400f);
        require("underlay persists after mosaic",
                hold.visible && !hold.primaryVisible && hold.revealUnderlay);
        require("unlock hold finite", !scene.frameAt(2020L + LgPixelateScene.UNLOCK_HOLD_MS,
                120f, 2400f).visible);
    }

    private static void testAffordanceIsFinite() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.affordance(50f, 70f, 3000L);
        LgPixelateScene.Frame hint = scene.frameAt(3200L, 120f, 2400f);
        require("hint becomes mosaic", hint.mosaicEnabled);
        require("hint stays subtle", hint.meshScale < 1.55f);
        require("hint clears", !scene.frameAt(3000L + LgPixelateScene.AFFORDANCE_MS,
                120f, 2400f).visible);
    }

    private static void testClockBackstepCannotResurrect() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.affordance(0f, 0f, 5000L);
        require("hint complete", !scene.frameAt(5600L, 120f, 2400f).visible);
        require("old clock does not revive", !scene.frameAt(5010L, 120f, 2400f).visible);
    }

    private static void require(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }

    private static void near(String label, float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
