package com.codex.lle;

/** Host-side regressions for the recovered Color Layered clock and reversible geometry. */
public final class LgHulaHoopSceneTest {
    private static int assertions;

    public static void main(String[] args) {
        LgHulaHoopScene scene = new LgHulaHoopScene();
        LgHulaHoopScene.Frame frame = new LgHulaHoopScene.Frame();
        scene.configure(1080, 2400, 3f);
        near(scene.minimumRadius(), 150.6f, "stock minimum radius");
        near(scene.thresholdRadius(), 384f, "stock threshold radius");

        scene.begin(500f, 1000f, 1_000L);
        scene.sample(1_000L, frame);
        require(frame.visible && frame.stage == LgHulaHoopScene.ACTIVE, "DOWN visible");
        near(frame.radius, scene.minimumRadius(), "minimum opening on DOWN");
        near(frame.layerRadius, frame.radius * .39f, "stock 30 percent layer intro");
        near(frame.rotationPeriodMs, 700f, "minimum-radius rotation period");
        scene.sample(1_300L, frame);
        near(frame.layerRadius, frame.radius * 1.3f, "stock layer intro completes at 300ms");
        scene.move(700f, 1000f, 1_316L);
        float expanded = scene.sample(1_320L, frame).radius;
        require(expanded > scene.minimumRadius(), "drag expands");
        require(frame.trailX < 0f && Math.abs(frame.trailX) < 20f,
                "fast drag creates only a small opposite holder trail");
        scene.move(600f, 1000f, 1_328L);
        require(scene.sample(1_330L, frame).radius < expanded, "drag reverses");

        scene.finish(false, 1_340L);
        require(scene.sample(1_639L, frame).visible, "cancel keeps final frame");
        require(!scene.sample(1_640L, frame).visible, "cancel clears exactly at 300ms");

        scene.begin(100f, 100f, 2_000L);
        scene.move(100f + scene.thresholdRadius(), 100f, 2_016L);
        scene.sample(2_300L, frame);
        near(frame.radius, scene.thresholdRadius(), "maximum moving radius");
        near(frame.layerScale, 1.2f, "maximum-radius layer scale");
        near(frame.rotationPeriodMs, 2_500f, "maximum-radius rotation period");

        for (int i = 0; i < 50; i++) {
            long start = 10_000L + i * 2_000L;
            scene.begin(15f, 20f, start);
            scene.move(520f, 400f, start + 16L);
            scene.finish(true, start + 20L);
            require(scene.sample(start + 619L, frame).visible && !frame.fullUnderlay,
                    "stock 600ms unlock is still opening");
            require(scene.sample(start + 620L, frame).fullUnderlay,
                    "full Last Screen begins at unlock completion");
            require(scene.sample(start + 1_169L, frame).visible,
                    "550ms Last Screen hold survives last millisecond");
            require(!scene.sample(start + 1_170L, frame).visible,
                    "hold clears without a stuck frame");
        }

        scene.startHint(400f, 800f, 500_000L);
        require(scene.sample(500_499L, frame).ping1 == 0f, "ping waits stock 500ms");
        require(scene.sample(500_501L, frame).ping1 > 0f, "first ping starts");
        require(scene.sample(500_731L, frame).ping2 > 0f, "second ping uses 230ms delay");
        require(!scene.sample(501_500L, frame).visible, "hint cannot remain stuck");

        scene.begin(Float.NaN, 0f, 600_000L);
        require(scene.state() == LgHulaHoopScene.IDLE, "invalid DOWN ignored");
        near(LgHulaHoopScene.LAYER_TRANSITION[0], 1.3f, "layer 0 transition");
        near(LgHulaHoopScene.LAYER_TRANSITION[3], .9f, "layer 3 transition");
        System.out.println("LgHulaHoopSceneTest: PASS (" + assertions + " assertions)");
    }

    private static void near(float actual, float expected, String label) {
        require(Math.abs(actual - expected) < .002f,
                label + ": " + actual + " != " + expected);
    }

    private static void require(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }
}
