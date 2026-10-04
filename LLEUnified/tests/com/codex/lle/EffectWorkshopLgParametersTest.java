package com.codex.lle;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Host checks for configured LG geometry, actual particle arrays and bounded extreme frames. */
public final class EffectWorkshopLgParametersTest {
    public static void main(String[] args) {
        int total = 0;
        for (int id : new int[] {32, 33, 34, 40, 41, 43}) {
            EffectWorkshopConfig.Parameter[] params = EffectWorkshopLgParameters.parametersFor(id);
            total += params.length;
            for (EffectWorkshopConfig.Parameter p : params) {
                Map<String, Float> overrides = new HashMap<String, Float>();
                overrides.put(p.key, p.max + 99999f);
                near(p.defaultValue, EffectWorkshopConfig.create(id, false, overrides).get(p.key));
                near(p.max, EffectWorkshopConfig.create(id, true, overrides).get(p.key));
            }
            for (boolean high : new boolean[] {false, true}) exerciseExtremes(id, extremes(id, high));
        }
        if (total != 111) throw new AssertionError("Controls " + total);
        checkCustomPixelate();
        checkCustomParticle();
        checkCustomCrystal();
        checkCustomLight();
        checkCustomVector();
        checkCustomMosaic();
        checkDisabledIdentity();
        System.out.println("LG first half: " + total + " controls; actual scene defaults, customized geometry and min/max frames passed");
    }
    private static EffectWorkshopConfig.Values values(int id, Object... pairs) {
        Map<String, Float> map = new HashMap<String, Float>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], ((Number) pairs[i + 1]).floatValue());
        return EffectWorkshopConfig.create(id, true, map);
    }
    private static EffectWorkshopConfig.Values extremes(int id, boolean high) {
        Map<String, Float> map = new HashMap<String, Float>();
        for (EffectWorkshopConfig.Parameter p : EffectWorkshopLgParameters.parametersFor(id)) map.put(p.key, high ? p.max : p.min);
        return EffectWorkshopConfig.create(id, true, map);
    }
    private static void checkCustomPixelate() {
        EffectWorkshopConfig.Values v = values(32, "mesh_resolution", 30, "uv_mix", .2f, "mesh_growth", 2);
        LgPixelateMesh mesh = LgPixelateMesh.build(100, 200, v);
        if (mesh.rows != 32 || mesh.columns != 17) throw new AssertionError("Custom mesh resolution");
        near(.8f / 17f, mesh.mosaicCoordinates[0]);
        LgPixelateScene scene = new LgPixelateScene(v);
        scene.begin(50, 100, 100);
        scene.move(150, 100, 110);
        near(3, scene.frameAt(120, 100, 300).meshScale);
    }
    private static void checkCustomParticle() {
        G2ParticleScene scene = new G2ParticleScene(values(33, "d_count", 3, "f_count", 4, "b_count", 5, "e_count", 6,
                "size_base", 9, "size_f", 9, "size_class_step", 0, "size_random_min", 1, "size_random_range", 0,
                "min_radius_dp", 80, "hole_alpha", .8f));
        scene.setSurfaceSize(1000, 2000);
        scene.begin(500, 1000, 100);
        if (scene.particleCount() != 18) throw new AssertionError("Dynamic G2 count");
        near(80, scene.currentRadius());
        near(.8f, scene.currentHoleAlpha(100));
        float[] batch = scene.fillVertices(100);
        if (batch.length != 18 * 5) throw new AssertionError("Dynamic G2 buffer");
        near(9, batch[2]);
    }
    private static void checkCustomCrystal() {
        EffectWorkshopConfig.Values v = values(34, "min_radius_px", 90, "boundary_px", 300, "table_radius", .6f,
                "inner_angle_deg", 45, "shine_power", 120, "light_gain", 2, "refraction_gain", .5f);
        CrystalPrismBetaEffectView.MotionPlan plan = new CrystalPrismBetaEffectView.MotionPlan(v);
        plan.setViewport(1080, 1920);
        plan.begin(500, 900, 100);
        near(90, plan.advance(100).radiusPx);
        plan.drag(800, 900, 120);
        near(300, plan.advance(120).radiusPx);
        CrystalPrismBetaEffectView.CrystalMesh custom = new CrystalPrismBetaEffectView.CrystalMesh(1080, 1920, v);
        CrystalPrismBetaEffectView.CrystalMesh original = new CrystalPrismBetaEffectView.CrystalMesh(1080, 1920);
        if (custom.table.get(0) == original.table.get(0)) throw new AssertionError("Crystal radius not applied");
        finite(custom.table);
        String shader = CrystalPrismBetaEffectView.configuredCrystalVertexShader(
                "shiness = 80.0; vLightColor = (ambient + diffuse + specular) * 1.2; texCoord + deltaTexCoord", v);
        if (!shader.contains("shiness = 120.0") || !shader.contains("specular) * 2.0") || !shader.contains("deltaTexCoord * 0.5"))
                throw new AssertionError("Crystal optics not applied");
    }
    private static void checkCustomLight() {
        EffectWorkshopConfig.Values v = values(40, "bg_count", 10, "min_radius_v2_dp", 80, "boundary_v2_mm", 40,
                "edge_width", 1.2f, "orbit_speed", 2);
        LgLightParticleScene scene = new LgLightParticleScene(true, LgLightParticleScene.REVISION_LG_NATIVE, v);
        if (scene.particleCapacity() != 79) throw new AssertionError("Dynamic Light capacity");
        scene.setDensity(2); scene.setHorizontalDpi(254); scene.setSurfaceSize(1080, 1920);
        near(160, scene.minRadius()); near(400, scene.unlockRadius()); near(192, scene.configuredEdgeBandwidth(200));
        scene.begin(500, 900, 100);
        LgLightParticleScene.Frame frame = scene.sample(1100, new LgLightParticleScene.Frame(scene.particleCapacity()));
        if (frame.sprites.length != 79 || frame.spriteCount <= 5) throw new AssertionError("Light emission count");
        lightFinite(frame);
    }
    private static void checkCustomVector() {
        LgVectorScene scene = new LgVectorScene(new Random(1), values(41, "min_radius_dp", 70, "boundary_dp", 200,
                "palette", 3, "band_base_alpha", .8f, "outer_knee", .8f));
        scene.configure(1080, 1920, 1); scene.begin(500, 900, 100); scene.move(600, 900, 200);
        LgVectorScene.Frame f = scene.sample(200, new LgVectorScene.Frame());
        near(70, f.minRadius); near(200, f.boundary); near(160, f.outerRadius);
        if (f.palette != 2 || f.bandAlpha < .8f) throw new AssertionError("Vector palette/alpha");
    }
    private static void checkCustomMosaic() {
        LgCircleMosaicScene scene = new LgCircleMosaicScene(values(43, "columns", 7, "rows", 9,
                "opaque_multiplier", 1, "alpha_multiplier", .5f, "density_base", 16, "density_slope", 1));
        scene.configure(1080, 1920, 2); scene.begin(500, 900, 100);
        if (scene.columns() != 7 || scene.rows() != 9) throw new AssertionError("Circle cells");
        near(14, scene.opaqueFactor()); near(7, scene.alphaFactor());
        LgCircleMosaicScene.Frame frame = scene.sample(100, new LgCircleMosaicScene.Frame());
        near(frame.radius / 14, LgCircleMosaicScene.cellBlurRadius(frame));
    }
    private static void checkDisabledIdentity() {
        LgPixelateMesh a = LgPixelateMesh.build(1080, 1920);
        LgPixelateMesh b = LgPixelateMesh.build(1080, 1920, EffectWorkshopConfig.create(32, false,
                extremes(32, true).configuredValues()));
        equal(a.mosaicCoordinates, b.mosaicCoordinates);
        G2ParticleScene c = new G2ParticleScene();
        G2ParticleScene d = new G2ParticleScene(EffectWorkshopConfig.create(33, false, extremes(33, true).configuredValues()));
        c.setSurfaceSize(1080, 1920); d.setSurfaceSize(1080, 1920); c.begin(500, 900, 100); d.begin(500, 900, 100);
        equal(c.fillVertices(140), d.fillVertices(140));
        LgLightParticleScene x = new LgLightParticleScene(true);
        LgLightParticleScene y = new LgLightParticleScene(true, 1, EffectWorkshopConfig.create(40, false,
                extremes(40, true).configuredValues()));
        x.setSurfaceSize(1080, 1920); y.setSurfaceSize(1080, 1920); x.begin(500, 900, 100); y.begin(500, 900, 100);
        LgLightParticleScene.Frame fx = x.sample(700, new LgLightParticleScene.Frame());
        LgLightParticleScene.Frame fy = y.sample(700, new LgLightParticleScene.Frame());
        if (fx.spriteCount != fy.spriteCount) throw new AssertionError("Disabled Light count");
        for (int i = 0; i < fx.spriteCount; i++) { near(fx.sprites[i].x, fy.sprites[i].x); near(fx.sprites[i].sizeScale, fy.sprites[i].sizeScale); }
    }
    private static void exerciseExtremes(int id, EffectWorkshopConfig.Values v) {
        if (id == 32) {
            LgPixelateMesh mesh = LgPixelateMesh.build(1080, 1920, v); mesh.updateUserAlpha(500, 900, 300, 3);
            finite(mesh.positions); finite(mesh.userAlpha);
        } else if (id == 33) {
            G2ParticleScene scene = new G2ParticleScene(v); scene.setSurfaceSize(1080, 1920);
            scene.begin(500, 900, 100); scene.move(900, 1200, 200);
            finite(scene.fillVertices(250)); scene.finish(true, 300); finite(scene.fillVertices(500));
        } else if (id == 34) {
            CrystalPrismBetaEffectView.CrystalMesh mesh = new CrystalPrismBetaEffectView.CrystalMesh(1080, 1920, v);
            finite(mesh.table); finite(mesh.upperGirdle); finite(mesh.star);
        } else if (id == 40) {
            for (int revision : new int[] {1, 2}) {
                LgLightParticleScene scene = new LgLightParticleScene(true, revision, v); scene.setSurfaceSize(1080, 1920);
                scene.begin(500, 900, 100); scene.move(900, 1200);
                LgLightParticleScene.Frame frame = new LgLightParticleScene.Frame(scene.particleCapacity());
                for (long time : new long[] {200, 500, 2000, 6000}) lightFinite(scene.sample(time, frame));
                scene.finish(true, 6100); lightFinite(scene.sample(6300, frame));
            }
        } else if (id == 41) {
            LgVectorScene scene = new LgVectorScene(v); scene.configure(1080, 1920, 4);
            scene.begin(500, 900, 100); scene.move(900, 1200, 200);
            LgVectorScene.Frame f = scene.sample(500, new LgVectorScene.Frame()); finite(f.outerRadius); finite(f.bandAlpha);
        } else {
            LgCircleMosaicScene scene = new LgCircleMosaicScene(v); scene.configure(1080, 1920, 6);
            scene.begin(500, 900, 100); scene.move(900, 1200, 200);
            LgCircleMosaicScene.Frame f = scene.sample(500, new LgCircleMosaicScene.Frame());
            finite(LgCircleMosaicScene.cellBlurRadius(f)); finite(LgCircleMosaicScene.cellRevealRadius(f, 500, 900));
        }
    }
    private static void lightFinite(LgLightParticleScene.Frame frame) {
        for (int i = 0; i < frame.spriteCount; i++) {
            LgLightParticleScene.ParticleSprite p = frame.sprites[i]; finite(p.x); finite(p.y); finite(p.alpha); finite(p.sizeScale);
            if (p.alpha < 0 || p.alpha > 1) throw new AssertionError("Particle alpha");
        }
    }
    private static void equal(float[] a, float[] b) {
        if (a.length != b.length) throw new AssertionError("Different arrays");
        for (int i = 0; i < a.length; i++) if (Float.floatToIntBits(a[i]) != Float.floatToIntBits(b[i])) throw new AssertionError("Changed default " + i);
    }
    private static void finite(FloatBuffer b) { for (int i = 0; i < b.capacity(); i++) finite(b.get(i)); }
    private static void finite(float[] values) { for (float v : values) finite(v); }
    private static void finite(float v) { if (Float.isNaN(v) || Float.isInfinite(v)) throw new AssertionError("Nonfinite"); }
    private static void near(float expected, float actual) { if (Math.abs(expected - actual) > .0001f) throw new AssertionError(expected + " != " + actual); }
}
