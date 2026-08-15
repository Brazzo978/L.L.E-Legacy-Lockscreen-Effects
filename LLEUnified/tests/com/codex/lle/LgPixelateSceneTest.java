package com.codex.lle;

/** Host-only regression checks for the 1.0.6 Pixelate state machine. */
public final class LgPixelateSceneTest {
    private static final float EPSILON = 0.001f;

    private LgPixelateSceneTest() { }

    public static void main(String[] args) {
        testHeldFieldGrowsWithDrag();
        testCancelTailIsFinite();
        testUnlockKeepsCompleteTail();
        testSpeedUsesElapsedTime();
        testClockBackstepCannotResurrect();
    }

    private static void testHeldFieldGrowsWithDrag() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.begin(100f, 200f, 1000L);
        LgPixelateScene.Frame start = scene.frameAt(1000L, 1f);
        scene.move(500f, 200f, 1010L);
        LgPixelateScene.Frame moved = scene.frameAt(1010L, 1f);
        require("held visible", start.visible && moved.visible);
        near("x follows touch", 500f, moved.x);
        require("radius expands", moved.radiusPx > start.radiusPx);
        require("pixel size expands", moved.pixelSizePx > start.pixelSizePx);
    }

    private static void testCancelTailIsFinite() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.begin(300f, 400f, 10000L);
        scene.finish(false, 10050L);
        require("cancel visible before end", scene.frameAt(10100L, 1f).visible);
        require("cancel ends", !scene.frameAt(10050L + LgPixelateScene.CANCEL_FADE_MS, 1f).visible);
    }

    private static void testUnlockKeepsCompleteTail() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.begin(300f, 400f, 20000L);
        scene.move(620f, 400f, 20020L);
        scene.finish(true, 20030L);
        LgPixelateScene.Frame early = scene.frameAt(20130L, 1f);
        LgPixelateScene.Frame late = scene.frameAt(20030L + LgPixelateScene.UNLOCK_MS - 1L, 1f);
        require("unlock early visible", early.visible);
        require("unlock expands", late.radiusPx >= early.radiusPx);
        require("unlock tail remains", late.visible);
        require("unlock ends", !scene.frameAt(20030L + LgPixelateScene.UNLOCK_MS, 1f).visible);
    }

    private static void testSpeedUsesElapsedTime() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.begin(0f, 0f, 1000000L);
        scene.finish(true, 1000010L);
        require("half duration remains at 2x", scene.frameAt(1000110L, 1f, 2f).visible);
        require("full duration ends at 2x", !scene.frameAt(1000210L, 1f, 2f).visible);
    }

    private static void testClockBackstepCannotResurrect() {
        LgPixelateScene scene = new LgPixelateScene();
        scene.begin(0f, 0f, 5000L);
        scene.finish(false, 5010L);
        require("cancel complete", !scene.frameAt(5300L, 1f).visible);
        require("old clock does not revive", !scene.frameAt(5020L, 1f).visible);
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
