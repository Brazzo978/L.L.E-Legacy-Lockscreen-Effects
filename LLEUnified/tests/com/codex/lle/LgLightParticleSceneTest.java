package com.codex.lle;

/** Host-JVM checks for the recovered G2 Light Particle clock and particle layout. */
public final class LgLightParticleSceneTest {
    private LgLightParticleSceneTest() {
    }

    public static void main(String[] args) {
        checkArchiveGeometry();
        checkTouchFadeAndParticles();
        checkCancelClock();
        checkCompleteClockAndHold();
        checkEdgeShaderGeometry();
    }

    private static void checkArchiveGeometry() {
        LgLightParticleScene scene = new LgLightParticleScene(true);
        scene.setDensity(3f);
        scene.setSurfaceSize(1080, 2340);
        require("44 dp donor radius", near(scene.minRadius(), 132f, 0.001f));
        require("113.33 dp donor threshold", near(scene.unlockRadius(), 339.99f, 0.01f));
        require("ten archived textures", LgLightParticleScene.TEXTURE_COUNT == 10);
        require("five background plus 69 bokeh quads",
                LgLightParticleScene.PARTICLE_CAPACITY == 74);
    }

    private static void checkTouchFadeAndParticles() {
        LgLightParticleScene scene = new LgLightParticleScene(true);
        scene.setDensity(2f);
        scene.setSurfaceSize(1080, 1920);
        long start = 10_000L;
        scene.begin(400f, 700f, start);
        LgLightParticleScene.Frame first = scene.sample(start, new LgLightParticleScene.Frame());
        require("touch starts active", first.visible && first.running
                && first.stage == LgLightParticleScene.ACTIVE);
        require("touch starts at half particle alpha", near(first.particleAlpha, 0.5f, 0.0001f));
        require("touch starts at 0.6 particle scale",
                near(first.backgroundSizeScale, 0.6f, 0.0001f)
                        && near(first.bokehSizeScale, 0.6f, 0.0001f));

        LgLightParticleScene.Frame faded =
                scene.sample(start + 300L, new LgLightParticleScene.Frame());
        require("300 ms donor fade reaches full alpha", near(faded.particleAlpha, 1f, 0.0001f));
        require("particle system produces visible quads", faded.spriteCount > 5
                && faded.spriteCount <= LgLightParticleScene.PARTICLE_CAPACITY);
        for (int index = 0; index < faded.spriteCount; index++) {
            LgLightParticleScene.ParticleSprite sprite = faded.sprites[index];
            require("particle texture index is valid", sprite.texture >= 0
                    && sprite.texture < LgLightParticleScene.TEXTURE_COUNT);
            require("particle coordinates are finite",
                    Float.isFinite(sprite.x) && Float.isFinite(sprite.y));
            require("particle alpha remains normalized", sprite.alpha > 0f && sprite.alpha <= 1f);
        }
    }

    private static void checkCancelClock() {
        LgLightParticleScene scene = new LgLightParticleScene(true);
        scene.setDensity(2f);
        scene.setSurfaceSize(1080, 1920);
        long start = 20_000L;
        scene.begin(300f, 900f, start);
        scene.move(450f, 950f);
        scene.finish(false, start + 100L);
        LgLightParticleScene.Frame beforeEnd =
                scene.sample(start + 399L, new LgLightParticleScene.Frame());
        require("cancel remains visible for 299 ms", beforeEnd.visible && beforeEnd.running
                && beforeEnd.stage == LgLightParticleScene.CANCEL);
        LgLightParticleScene.Frame atEnd =
                scene.sample(start + 400L, new LgLightParticleScene.Frame());
        require("cancel clears exactly at 300 ms", !atEnd.visible && !atEnd.running
                && scene.state() == LgLightParticleScene.IDLE);
    }

    private static void checkCompleteClockAndHold() {
        LgLightParticleScene scene = new LgLightParticleScene(true);
        scene.setDensity(2f);
        scene.setSurfaceSize(1080, 1920);
        long start = 30_000L;
        scene.begin(500f, 1000f, start);
        scene.move(850f, 1000f);
        long release = start + 200L;
        scene.finish(true, release);
        LgLightParticleScene.Frame expanding =
                scene.sample(release + 499L, new LgLightParticleScene.Frame());
        require("unlock expands for the donor 500 ms", expanding.visible && expanding.running
                && expanding.stage == LgLightParticleScene.COMPLETE
                && expanding.radius < scene.fullRadius());
        LgLightParticleScene.Frame held =
                scene.sample(release + 500L, new LgLightParticleScene.Frame());
        require("full underlay is held after expansion", held.visible && held.running
                && near(held.radius, scene.fullRadius(), 0.01f)
                && held.spriteCount == 0);
        require("550 ms hold survives its final millisecond",
                scene.sample(release + 1049L, new LgLightParticleScene.Frame()).visible);
        require("hold clears at 1050 ms",
                !scene.sample(release + 1050L, new LgLightParticleScene.Frame()).visible
                        && scene.state() == LgLightParticleScene.IDLE);
    }

    private static void checkEdgeShaderGeometry() {
        float radius = 300f;
        float min = 100f;
        require("bandwidth is capped at 80 percent of the start radius",
                near(LgLightParticleScene.edgeBandwidth(radius, min), 80f, 0.001f));
        require("shader inner edge follows radius times 0.7 minus bandwidth",
                near(LgLightParticleScene.edgeInnerRadius(radius, min), 130f, 0.001f));
        require("shader outer edge follows radius times 0.7 plus bandwidth",
                near(LgLightParticleScene.edgeOuterRadius(radius, min), 290f, 0.001f));
    }

    private static boolean near(float actual, float expected, float tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }

    private static void require(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }
}
