package com.codex.lle;

/** Host-only regression coverage for donor-derived Revolving Glass motion. */
public final class RevolvingGlassSceneTest {
    private RevolvingGlassSceneTest() { }

    public static void main(String[] args) {
        testDonorDragMappingAndDirection();
        testDragVelocityIsCapped();
        testEntryFadeIsFiniteAndInPlace();
        testCancelOscillatesAndTerminates();
        testUnlockUsesWallClockAtAllRefreshRates();
        testUnlockTurnShortensAfterDrag();
        testHintIsFinite();
        testRepeatedGesturesAndOldClock();
    }

    private static void testDonorDragMappingAndDirection() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.begin(540f, 1_080f, 1_000L);
        scene.move(540f, 640f, 1_080f, 1_050L);
        assertClose(44f, scene.frameAt(1_050L).angleDegrees, .001f,
                "right drag must use donor 0.44 degree/pixel mapping");
        scene.reset();
        scene.begin(540f, 1_080f, 2_000L);
        scene.move(540f, 440f, 1_080f, 2_050L);
        assertClose(-44f, scene.frameAt(2_050L).angleDegrees, .001f,
                "left drag direction is inverted");
    }

    private static void testDragVelocityIsCapped() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.begin(540f, 1_080f, 2_500L);
        scene.move(540f, 1_040f, 1_080f, 2_501L);
        assertClose(RevolvingGlassScene.MAX_DRAG_DEGREES_PER_MS,
                scene.frameAt(2_501L).angleDegrees, .001f,
                "one-frame fling bypassed the drag velocity cap");
        scene.move(540f, 1_040f, 1_080f, 2_511L);
        assertClose(RevolvingGlassScene.MAX_DRAG_DEGREES_PER_MS * 11f,
                scene.frameAt(2_511L).angleDegrees, .001f,
                "velocity-capped drag did not advance by wall clock");
    }

    private static void testEntryFadeIsFiniteAndInPlace() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.begin(540f, 1_080f, 2_700L);
        RevolvingGlassScene.Frame start = scene.frameAt(2_700L);
        RevolvingGlassScene.Frame middle = scene.frameAt(
                2_700L + RevolvingGlassScene.TILE_ENTER_MS / 2L);
        RevolvingGlassScene.Frame complete = scene.frameAt(
                2_700L + RevolvingGlassScene.TILE_ENTER_MS);
        if (!start.visible || !start.animating || start.tileAlpha != 0f
                || start.underlayAlpha != 0f || start.tileScale != 1f
                || middle.tileAlpha <= 0f || middle.tileAlpha >= 1f
                || middle.underlayAlpha != middle.tileAlpha
                || complete.tileAlpha != 1f || complete.underlayAlpha != 1f
                || complete.tileScale != 1f) {
            throw new AssertionError("entry must crossfade in place and terminate");
        }
    }

    private static void testCancelOscillatesAndTerminates() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.begin(540f, 1_080f, 3_000L);
        scene.move(540f, 640f, 1_080f, 3_050L);
        scene.finish(false, 3_060L);
        boolean crossedZero = false;
        float previous = scene.frameAt(3_060L).angleDegrees;
        for (long now = 3_070L; now < 3_060L + RevolvingGlassScene.CANCEL_MAX_MS;
                now += RevolvingGlassScene.CANCEL_TICK_MS) {
            RevolvingGlassScene.Frame frame = scene.frameAt(now);
            if (!frame.visible) break;
            if ((previous > 0f && frame.angleDegrees < 0f)
                    || (previous < 0f && frame.angleDegrees > 0f)) {
                crossedZero = true;
            }
            previous = frame.angleDegrees;
        }
        if (!crossedZero
                || scene.frameAt(3_060L + RevolvingGlassScene.CANCEL_MAX_MS).visible) {
            throw new AssertionError("cancel must damp through zero and terminate");
        }
    }

    private static void testUnlockUsesWallClockAtAllRefreshRates() {
        for (int hz : new int[] {60, 90, 120, 144}) {
            RevolvingGlassScene scene = new RevolvingGlassScene();
            scene.begin(540f, 1_080f, 10_000L);
            scene.finish(true, 10_020L);
            long turnMs = scene.unlockTurnDurationMs();
            long frameStepMs = Math.max(1L, Math.round(1_000f / hz));
            for (long elapsed = frameStepMs; elapsed < turnMs; elapsed += frameStepMs) {
                scene.frameAt(10_020L + elapsed);
            }
            RevolvingGlassScene.Frame turned = scene.frameAt(
                    10_020L + turnMs);
            assertClose(180f, turned.angleDegrees, .5f,
                    "unlock rotation differs at " + hz + " Hz");
            long visibleUntil = Math.min(RevolvingGlassScene.UNLOCK_MS,
                    turnMs + RevolvingGlassScene.UNLOCK_TAIL_MS);
            long exitStart = visibleUntil - RevolvingGlassScene.TILE_EXIT_MS;
            RevolvingGlassScene.Frame beforeExit = scene.frameAt(10_020L + exitStart);
            RevolvingGlassScene.Frame midExit = scene.frameAt(
                    10_020L + exitStart + RevolvingGlassScene.TILE_EXIT_MS / 2L);
            if (beforeExit.tileScale != 1f || beforeExit.tileAlpha != 1f
                    || midExit.tileScale >= 1f
                    || midExit.tileScale <= RevolvingGlassScene.TILE_EXIT_SCALE
                    || midExit.tileAlpha >= 1f || midExit.tileAlpha <= 0f) {
                throw new AssertionError("tile exit shrink/fade differs at " + hz + " Hz");
            }
            RevolvingGlassScene.Frame lastTile =
                    scene.frameAt(10_020L + visibleUntil - 1L);
            RevolvingGlassScene.Frame underlayOnly =
                    scene.frameAt(10_020L + visibleUntil);
            if (!lastTile.visible || !lastTile.tileVisible
                    || !underlayOnly.visible || underlayOnly.tileVisible) {
                throw new AssertionError("tile tail differs at " + hz + " Hz");
            }
            if (!scene.frameAt(10_020L + RevolvingGlassScene.UNDERLAY_HOLD_MS - 1L).visible
                    || scene.frameAt(10_020L + RevolvingGlassScene.UNDERLAY_HOLD_MS).visible) {
                throw new AssertionError("underlay handoff hold differs at " + hz + " Hz");
            }
        }
    }

    private static void testUnlockTurnShortensAfterDrag() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.begin(540f, 1_080f, 20_000L);
        scene.move(540f, 740f, 1_080f, 20_100L);
        long delay = scene.finish(true, 20_110L);
        float remaining = 180f - 88f;
        int fastTicks = (int) Math.ceil((remaining * 7f / 8f)
                / RevolvingGlassScene.UNLOCK_FAST_STEP_DEGREES);
        float afterFast = Math.max(0f,
                remaining - fastTicks * RevolvingGlassScene.UNLOCK_FAST_STEP_DEGREES);
        int slowTicks = (int) Math.ceil(
                afterFast / RevolvingGlassScene.UNLOCK_SLOW_STEP_DEGREES);
        long expected = (fastTicks + slowTicks) * RevolvingGlassScene.UNLOCK_TICK_MS;
        if (delay != expected) {
            throw new AssertionError("unlock sound delay does not track remaining donor rotation");
        }
    }

    private static void testHintIsFinite() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.affordance(30_000L);
        float maximum = 0f;
        for (long now = 30_000L; now < 30_000L + RevolvingGlassScene.AFFORDANCE_MS;
                now += 8L) {
            maximum = Math.max(maximum, Math.abs(scene.frameAt(now).angleDegrees));
        }
        if (maximum > 9.01f
                || scene.frameAt(30_000L + RevolvingGlassScene.AFFORDANCE_MS).visible) {
            throw new AssertionError("hint should remain subtle and finite");
        }
    }

    private static void testRepeatedGesturesAndOldClock() {
        RevolvingGlassScene scene = new RevolvingGlassScene();
        scene.begin(540f, 1_080f, 40_000L);
        scene.finish(false, 40_010L);
        scene.frameAt(50_000L);
        if (scene.frameAt(40_020L).visible) {
            throw new AssertionError("old timestamp resurrected scene");
        }
        scene.begin(540f, 1_080f, 50_010L);
        scene.move(540f, 640f, 1_080f, 50_020L);
        if (!scene.frameAt(50_020L).visible) {
            throw new AssertionError("second gesture did not restart scene");
        }
    }

    private static void assertClose(float expected, float actual, float tolerance,
            String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
