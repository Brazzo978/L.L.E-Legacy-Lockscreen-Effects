package com.codex.lle;

/** Host regressions for LG G4 Circle Mosaic geometry and terminal clocks. */
public final class LgCircleMosaicSceneTest {
    private static int assertions;

    public static void main(String[] args) {
        LgCircleMosaicScene scene = new LgCircleMosaicScene();
        LgCircleMosaicScene.Frame frame = new LgCircleMosaicScene.Frame();
        scene.configure(1080, 2400, 3f, 400f);
        near(scene.minRadius(), 150.59995f, "donor 50.2dp minimum");
        near(scene.boundaryRadius(), 393.70078f, "donor 25mm boundary");
        require(LgCircleMosaicScene.COLUMNS == 15, "donor columns");
        require(LgCircleMosaicScene.ROWS == 25, "donor rows");

        scene.begin(500, 1000, 1000);
        scene.sample(1000, frame);
        near(frame.radius, scene.minRadius(), "touchdown starts at minimum ring");
        require(LgCircleMosaicScene.cellBlurRadius(frame) > 0f,
                "touchdown exposes local blurred cells");
        float initialReveal = LgCircleMosaicScene.cellRevealRadius(frame, 500, 1000);
        require(initialReveal > 0f, "touchdown center is transparent");

        LgCircleMosaicScene s23 = new LgCircleMosaicScene();
        s23.configure(1080, 2250, 2.8125f, 375.78f);
        s23.begin(540f, 1125f, 1000);
        s23.sample(1000, frame);
        int affected = 0;
        float cellWidth = 1080f / LgCircleMosaicScene.COLUMNS;
        float cellHeight = 2250f / LgCircleMosaicScene.ROWS;
        for (int row = 0; row < LgCircleMosaicScene.ROWS; row++) {
            for (int column = 0; column < LgCircleMosaicScene.COLUMNS; column++) {
                if (LgCircleMosaicScene.cellAffected(frame,
                        (column + .5f) * cellWidth, (row + .5f) * cellHeight)) {
                    affected++;
                }
            }
        }
        require(affected == 9, "S23 touchdown paints exactly nine cells, got " + affected);

        scene.move(600, 1000, 1010);
        float firstRadius = scene.sample(1010, frame).radius;
        scene.move(550, 1000, 1020);
        require(scene.sample(1020, frame).radius < firstRadius, "drag is reversible");
        scene.move(500 + scene.boundaryRadius(), 1000, 1030);
        near(scene.sample(1030, frame).radius, scene.boundaryRadius(),
                "boundary is continuous");

        scene.finish(false, 1100);
        require(scene.sample(1224, frame).visible && frame.radius > 0f,
                "cancel first half shrinks linearly");
        require(scene.sample(1226, frame).visible && frame.radius == 0f,
                "donor cancel snaps radius to zero after halfway");
        require(!scene.sample(1350, frame).visible, "cancel clears at 250ms");

        scene.begin(200, 400, 2000);
        scene.move(900, 400, 2010);
        scene.finish(true, 2020);
        require(scene.sample(2269, frame).visible && !frame.fullUnderlay,
                "unlock mosaic lasts 250ms");
        require(scene.sample(2270, frame).fullUnderlay,
                "Last Screen begins immediately after donor unlock");
        require(scene.sample(2819, frame).visible,
                "Last Screen persists for 550ms");
        require(!scene.sample(2820, frame).visible,
                "terminal frame releases exactly");

        scene.begin(Float.NaN, 0, 3000);
        require(scene.state() == LgCircleMosaicScene.IDLE, "invalid DOWN ignored");
        scene.begin(20, 30, 3010);
        scene.move(Float.POSITIVE_INFINITY, 0, 3020);
        require(Float.isFinite(scene.sample(3020, frame).radius), "invalid MOVE ignored");
        scene.reset();
        require(!scene.sample(3030, frame).visible, "reset leaves no frame");
        System.out.println("LgCircleMosaicSceneTest: PASS (" + assertions + " assertions)");
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
