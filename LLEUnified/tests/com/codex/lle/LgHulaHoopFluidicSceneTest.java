package com.codex.lle;

/** Host-side regressions for the recovered LG G4 FluidicRenderer gesture clock. */
public final class LgHulaHoopFluidicSceneTest {
    private static int assertions;

    public static void main(String[] args) {
        LgHulaHoopFluidicScene scene = new LgHulaHoopFluidicScene();
        LgHulaHoopFluidicScene.Frame frame = new LgHulaHoopFluidicScene.Frame();
        scene.configure(1080, 2400, 3f);
        near(scene.minimumRadius(), 150.59995f, "G4 minimum ring radius");
        near(scene.outerRingStride(), 45f, "G4 outer ring stride");

        scene.begin(200f, 500f, 1_000L);
        scene.sample(1_000L, frame);
        require(frame.visible && frame.stage == LgHulaHoopFluidicScene.ACTIVE,
                "DOWN starts Fluidic scene");
        near(frame.x, 200f, "opening is anchored at DOWN X");
        near(frame.y, 500f, "opening is anchored at DOWN Y");
        near(frame.radius, scene.minimumRadius(), "DOWN starts at minimum radius");

        scene.move(800f, 500f, 1_016L);
        scene.sample(1_017L, frame);
        require(frame.stretched, "fast outward move stretches the mesh");
        near(frame.x, 200f, "stretched opening remains anchored X");
        near(frame.y, 500f, "stretched opening remains anchored Y");
        require(frame.dragDistance / frame.radius <= 2.0001f,
                "stock maximum stretch ratio is clamped to two");
        require(frame.stretchDelayFrames == 4, "first rendered frame consumes stretch delay");
        scene.sample(1_018L, frame);
        scene.sample(1_019L, frame);
        scene.sample(1_020L, frame);
        scene.sample(1_021L, frame);
        require(frame.stretchDelayFrames == 0, "stretch deformation begins after five frames");

        scene.move(801f, 500f, 1_121L);
        scene.sample(1_122L, frame);
        require(!frame.stretched, "slow radial motion returns to a circular body");
        near(frame.radius, 601f, "slow motion radius follows the absolute drag");

        scene.finish(false, 1_130L);
        scene.sample(1_331L, frame);
        require(frame.visible && !frame.drawColors,
                "cancel hides cyan and magenta after eighty percent");
        near(frame.radius, 117.796f, "cancel closes linearly");
        require(!scene.sample(1_380L, frame).visible, "cancel clears at 250ms");

        scene.begin(300f, 900f, 2_000L);
        scene.move(900f, 900f, 2_016L);
        scene.sample(2_020L, frame);
        scene.finish(true, 2_030L);
        scene.sample(2_030L, frame);
        require(frame.visible && !frame.fullUnderlay, "unlock begins with the fluidic mesh");
        require(frame.radius < 600f, "stock bounce reduces the unlock start radius");
        scene.sample(2_279L, frame);
        require(frame.visible && !frame.fullUnderlay, "250ms expansion keeps its last frame");
        scene.sample(2_280L, frame);
        require(frame.fullUnderlay && !frame.drawColors,
                "Last Screen takes over when stock expansion completes");
        require(scene.sample(2_829L, frame).visible,
                "Last Screen remains through the 550ms transition hold");
        require(!scene.sample(2_830L, frame).visible,
                "Fluidic scene clears after the transition hold");

        scene.begin(Float.NaN, 0f, 3_000L);
        require(scene.state() == LgHulaHoopFluidicScene.IDLE, "invalid DOWN is ignored");

        LgHulaHoopFluidicScene angleScene = new LgHulaHoopFluidicScene();
        angleScene.configure(1080, 2400, 3f);
        angleScene.begin(100f, 100f, 4_000L);
        angleScene.move(200f, 200f, 4_016L);
        angleScene.sample(4_017L, frame);
        near(frame.angle, -45f, "G4 GL-space deformation angle is preserved");
        System.out.println("LgHulaHoopFluidicSceneTest: PASS (" + assertions
                + " assertions)");
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
