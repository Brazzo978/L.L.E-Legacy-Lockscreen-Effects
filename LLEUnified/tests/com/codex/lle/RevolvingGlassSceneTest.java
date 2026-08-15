package com.codex.lle;

/** Host-only regression coverage for the 1.0.6 clean-room Glass motion timeline. */
public final class RevolvingGlassSceneTest {
    private RevolvingGlassSceneTest() { }

    public static void main(String[] args) {
        testDragIsBounded();
        testCancelFinishes();
        testUnlockUsesWallClockAtAllRefreshRates();
        testHintIsFinite();
        testOldClockCannotResurrectScene();
    }

    private static void testDragIsBounded() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.begin(200f, 300f, 1_000L);
        scene.move(200f, 99_000f, 1_080f, 1_010L);
        RevolvingGlassScene.Frame frame = scene.frameAt(1_010L);
        if (!frame.visible || frame.angleRadians > RevolvingGlassScene.MAX_DRAG_RADIANS) {
            throw new AssertionError("glass slab angle must be bounded");
        }
    }

    private static void testCancelFinishes() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.begin(400f, 600f, 2_000L);
        scene.move(400f, 800f, 1_080f, 2_010L);
        float held = Math.abs(scene.frameAt(2_010L).angleRadians);
        scene.finish(false, 2_020L);
        float returning = Math.abs(scene.frameAt(2_120L).angleRadians);
        if (returning >= held || scene.frameAt(2_020L + RevolvingGlassScene.CANCEL_MS).visible) {
            throw new AssertionError("cancel must return the slab and terminate");
        }
    }

    private static void testUnlockUsesWallClockAtAllRefreshRates() {
        for (int hz : new int[] {60, 90, 120, 144}) {
            RevolvingGlassScene scene = new RevolvingGlassScene();
            scene.begin(200f, 300f, 10_000L);
            scene.move(200f, 600f, 1_080f, 10_010L);
            scene.finish(true, 10_020L);
            for (int frame = 1; frame <= hz; frame++) {
                scene.frameAt(10_020L + Math.round(frame * 600f / hz));
            }
            if (!scene.frameAt(10_020L + RevolvingGlassScene.UNLOCK_MS - 1L).visible
                    || scene.frameAt(10_020L + RevolvingGlassScene.UNLOCK_MS).visible) {
                throw new AssertionError("unlock duration differs at " + hz + " Hz");
            }
        }
    }

    private static void testHintIsFinite() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.affordance(20_000L);
        float maximumAlpha = 0f;
        for (long now = 20_000L; now < 20_000L + RevolvingGlassScene.AFFORDANCE_MS; now += 8L) {
            RevolvingGlassScene.Frame frame = scene.frameAt(now);
            maximumAlpha = Math.max(maximumAlpha, frame.alpha);
        }
        if (maximumAlpha > .301f || scene.frameAt(20_000L + RevolvingGlassScene.AFFORDANCE_MS).visible) {
            throw new AssertionError("hint should stay subtle and finite");
        }
    }

    private static void testOldClockCannotResurrectScene() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.begin(0f, 0f, 30_000L);
        scene.finish(false, 30_010L);
        if (scene.frameAt(30_500L).visible || scene.frameAt(30_020L).visible) {
            throw new AssertionError("old timestamp resurrected scene");
        }
    }
}
