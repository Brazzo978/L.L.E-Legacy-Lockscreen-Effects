package com.codex.lle;

import java.util.Random;

/** Host regressions for the donor clock, reversible gestures and LLE's handoff tail. */
public final class LgVectorSceneTest {
    private static int assertions;
    public static void main(String[] args) {
        LgVectorScene scene = new LgVectorScene(new Random(1));
        scene.configure(1080, 2400, 3f);
        near(scene.minRadius(), 132f, "donor 44dp");
        near(scene.boundaryRadius(), 339.98997f, "donor 113.33dp");
        LgVectorScene.Frame frame = new LgVectorScene.Frame();
        scene.begin(500, 1000, 1000);
        require(scene.sample(1000, frame).tap, "DOWN starts tap, not a Last Screen splash");
        scene.finish(false, 1010);
        require(scene.sample(1679, frame).visible, "short tap completes donor 680ms sequence");
        require(!scene.sample(1680, frame).visible && scene.state() == LgVectorScene.IDLE,
                "tap releases every pixel and callback at 680ms");
        scene.begin(500, 1000, 2000);
        require(!scene.sample(2680, frame).visible, "held stationary tap does not stay stuck");
        scene.move(600, 1000, 2700);
        float radius = scene.sample(2700, frame).outerRadius;
        scene.move(700, 1000, 2800);
        require(scene.sample(2800, frame).outerRadius > radius, "drag expands");
        scene.move(600, 1000, 2900);
        near(scene.sample(2900, frame).outerRadius, radius, "drag is reversible");
        near(frame.x, 500, "opening is anchored to DOWN");
        near(frame.y, 1000, "opening never tracks drag UVs");
        scene.finish(false, 3000);
        require(scene.sample(3299, frame).visible, "drag cancellation lasts 300ms");
        require(!scene.sample(3300, frame).visible, "cancel always clears");
        for (int i = 0; i < 100; i++) {
            long start = 10_000 + i * 2000;
            scene.begin(15, 20, start);
            scene.move(500, 20, start + 10);
            scene.finish(true, start + 20);
            scene.sample(start + 419, frame);
            require(frame.visible && !frame.fullUnderlay, "opening lasts400ms");
            scene.sample(start + 420, frame);
            require(frame.fullUnderlay, "complete means fixed full Last Screen");
            require(frame.innerRadius >= Math.hypot(1080 - 15, 2400 - 20), "edge touch covers corners");
            require(scene.sample(start + 969, frame).visible, "550ms hold survives its last millisecond");
            require(!scene.sample(start + 970, frame).visible, "hold ends exactly without fade");
        }
        scene.begin(500, 1000, 500_000);
        scene.move(900, 1300, 500_050);
        scene.reset();
        require(!scene.sample(500_060, frame).visible, "screen off/reset is immediately empty");
        scene.finish(true, 500_100);
        require(!scene.sample(500_100, frame).visible, "late UP cannot resurrect reset scene");
        scene.begin(Float.NaN, 0, 500_101);
        require(scene.state() == LgVectorScene.IDLE, "invalid DOWN ignored");
        scene.configure(1968, 2184, 2.5f);
        scene.begin(1000, 1000, 600_000);
        scene.move(Float.POSITIVE_INFINITY, 1000, 600_010);
        scene.sample(600_010, frame);
        require(Float.isFinite(frame.outerRadius), "invalid MOVE ignored");
        checkRadiusContinuity();
        System.out.println("LgVectorSceneTest: PASS (" + assertions + " assertions)");
    }

    private static void checkRadiusContinuity() {
        float min = 44, boundary = 113.32999f;
        near(LgVectorScene.outerRadius(0, min, boundary), min, "minimum drag outer");
        near(LgVectorScene.innerRadius(0, min, boundary), 0, "minimum drag inner");
        near(LgVectorScene.outerRadius(boundary / 2, min, boundary), boundary * .9266f,
                "donor midpoint outer");
        near(LgVectorScene.innerRadius(boundary / 2, min, boundary), min, "donor midpoint inner");
        near(LgVectorScene.outerRadius(boundary, min, boundary), boundary, "outer reaches boundary");
        near(LgVectorScene.innerRadius(boundary, min, boundary), boundary, "inner reaches boundary");
    }
    private static void near(float a, float b, String label) {
        require(Math.abs(a - b) < .002f, label + ": " + a + " != " + b);
    }
    private static void require(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }
}
