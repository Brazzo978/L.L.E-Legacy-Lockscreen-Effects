package com.codex.lle;

/** Host-only regression checks for the recovered LG G2 Particle scene. */
public final class G2ParticleSceneTest {
    private G2ParticleSceneTest() {
    }

    public static void main(String[] args) {
        testBoundedThreeBandBatch();
        testTrackingRadiusAndFiniteVertices();
        testUnlockKeepsRecoveredExpansionAndLleHold();
        testSpeedMultiplierSafetyClamp();
    }

    private static void testBoundedThreeBandBatch() {
        G2ParticleScene scene = new G2ParticleScene();
        assertInt("recovered donor particle count", 3090, scene.particleCount());
        assertInt("tuple count", scene.particleCount() * G2ParticleScene.VERTEX_STRIDE,
                scene.fillVertices(1L).length);
    }

    private static void testTrackingRadiusAndFiniteVertices() {
        G2ParticleScene scene = new G2ParticleScene();
        scene.setSurfaceSize(1080, 2400);
        scene.begin(540f, 1200f, 100L);
        float minimum = scene.currentRadius();
        scene.move(960f, 1200f, 120L);
        if (scene.currentRadius() <= minimum) {
            throw new AssertionError("drag did not grow circular reveal");
        }
        float[] vertices = scene.fillVertices(140L);
        for (int index = 0; index < vertices.length; index++) {
            if (Float.isNaN(vertices[index]) || Float.isInfinite(vertices[index])) {
                throw new AssertionError("non-finite vertex at " + index);
            }
        }
    }

    private static void testUnlockKeepsRecoveredExpansionAndLleHold() {
        G2ParticleScene scene = new G2ParticleScene();
        scene.setSurfaceSize(720, 1280);
        scene.begin(200f, 300f, 1_000L);
        scene.move(500f, 600f, 1_050L);
        scene.finish(true, 1_100L);
        long tailEnd = 1_100L + G2ParticleScene.EXIT_DURATION_MS
                + G2ParticleScene.COMPLETE_HOLD_MS;
        scene.fillVertices(tailEnd - 1L);
        if (!scene.isAnimating()) {
            throw new AssertionError("unlock tail ended before expansion plus hold");
        }
        scene.fillVertices(tailEnd);
        if (scene.isAnimating()) {
            throw new AssertionError("unlock tail exceeded expansion plus hold");
        }
    }

    private static void testSpeedMultiplierSafetyClamp() {
        assertNear("invalid speed", 1f,
                G2ParticleScene.sanitizeSpeedMultiplier(Float.NaN));
        assertNear("minimum speed", 0.5f,
                G2ParticleScene.sanitizeSpeedMultiplier(0.1f));
        assertNear("maximum speed", 2f,
                G2ParticleScene.sanitizeSpeedMultiplier(9f));
    }

    private static void assertInt(String name, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNear(String name, float expected, float actual) {
        if (Math.abs(expected - actual) > 0.0001f) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }
}
