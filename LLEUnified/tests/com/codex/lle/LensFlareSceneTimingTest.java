package com.codex.lle;

/** Host-JVM timing and terminal-tail checks for the Canvas-equivalent Lens Flare scene. */
public final class LensFlareSceneTimingTest {
    private LensFlareSceneTimingTest() {
    }

    public static void main(String[] args) {
        checkWarmFrame();
        checkGestureAndFadeTiming();
        checkUnlockTailTiming();
        checkAffordanceTiming();
        checkArchivedVariantDensity();
    }

    private static void checkWarmFrame() {
        LensFlareScene scene = new LensFlareScene(1500f, 600f);
        scene.warmUp();
        LensFlareScene.Frame frame = scene.frame(10_000L);
        require("warm frame is acknowledged", frame.warmFrameDrawn);
        require("warm frame keeps no timeline alive", !frame.keepAnimating);
        require("all 15 Canvas warm-up sprite draws are retained",
                frame.sprites.size() == 15);
        for (LensFlareScene.Sprite sprite : frame.sprites) {
            require("warm-up scale remains 0.004", near(sprite.scale, 0.004f, 0.000001f));
            require("Canvas alpha quantization remains exact", sprite.alpha == 1f);
        }
    }

    private static void checkGestureAndFadeTiming() {
        LensFlareScene scene = new LensFlareScene(1500f, 600f);
        long start = 20_000L;
        scene.begin(200f, 300f, start);
        require("gesture starts active", scene.isGestureActive());
        require("tap burst retains all seven recovered Samsung hexagons",
                countHexagons(scene.frame(start)) == 7);
        require("gesture remains live at the recovered 6 s show boundary",
                scene.frame(start + 6000L).keepAnimating);
        scene.move(800f, 900f);
        LensFlareScene.Frame drag = scene.frame(start + 100L);
        require("moving gesture produces the light and six drag hexagons",
                countAsset(drag, LensFlareScene.LIGHT) == 1 && drag.sprites.size() >= 7);

        long release = start + 200L;
        scene.finish(false, release);
        require("release ends active gesture", !scene.isGestureActive());
        require("drag light remains in the release fade before 500 ms",
                countAsset(scene.frame(release + 499L), LensFlareScene.LIGHT) == 1);
        require("drag light terminates at 500 ms while the independent tap tail continues",
                countAsset(scene.frame(release + 500L), LensFlareScene.LIGHT) == 0);
    }

    private static void checkUnlockTailTiming() {
        LensFlareScene scene = new LensFlareScene(1500f, 600f);
        long start = 30_000L;
        scene.begin(100f, 100f, start);
        scene.move(900f, 500f);
        long release = start + 100L;
        scene.finish(true, release);
        LensFlareScene.Frame beforeEnd = scene.frame(release + 900L);
        require("unlock rainbow remains in the recovered 1200 ms timeline",
                beforeEnd.keepAnimating
                        && countAsset(beforeEnd, LensFlareScene.RAINBOW) == 1);
        LensFlareScene.Frame atEnd = scene.frame(release + 1200L);
        require("unlock rainbow terminates exactly at 1200 ms",
                countAsset(atEnd, LensFlareScene.RAINBOW) == 0);
    }

    private static void checkAffordanceTiming() {
        LensFlareScene scene = new LensFlareScene(1500f, 600f);
        long start = 40_000L;
        scene.affordance(500f, 700f, start);
        require("affordance begins with the tap layer", scene.frame(start).keepAnimating);
        require("affordance light survives 1299 ms",
                countAsset(scene.frame(start + 1299L), LensFlareScene.LIGHT) == 1);
        require("affordance light ends at 1300 ms",
                countAsset(scene.frame(start + 1300L), LensFlareScene.LIGHT) == 0);
    }

    private static void checkArchivedVariantDensity() {
        require("stock half-xxhdpi assets retain the calibrated scale",
                near(LensFlareScene.assetScaleForMode("flare"), 1f, 0.000001f));
        require("Blue Ring xhdpi assets emulate xxhdpi selection before inSampleSize",
                near(LensFlareScene.assetScaleForMode("bluering"), 0.75f, 0.000001f));
        require("Blood xxhdpi assets are halved once",
                near(LensFlareScene.assetScaleForMode("blood"), 0.5f, 0.000001f));
        require("Lightning uses its archived xxhdpi family, not procedural geometry",
                near(LensFlareScene.assetScaleForMode("lightning"), 0.5f, 0.000001f));
    }

    private static int countAsset(LensFlareScene.Frame frame, int asset) {
        int count = 0;
        for (LensFlareScene.Sprite sprite : frame.sprites) {
            if (sprite.asset == asset) {
                count++;
            }
        }
        return count;
    }

    private static int countHexagons(LensFlareScene.Frame frame) {
        return countAsset(frame, LensFlareScene.HEXAGON_BLUE)
                + countAsset(frame, LensFlareScene.HEXAGON_ORANGE)
                + countAsset(frame, LensFlareScene.HEXAGON_GREEN);
    }

    private static boolean near(float actual, float expected, float tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }

    private static void require(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
