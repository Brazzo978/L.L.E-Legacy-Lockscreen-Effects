package com.codex.lle;

import java.util.LinkedHashMap;
import java.util.Map;

/** Semantic host checks using the same optical math and soft-body solver as the Views. */
public final class EffectWorkshopLgOpticsTest {
    private static int assertions;
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < .001f, message + " " + actual + " != " + expected);
    }
    private static EffectWorkshopConfig.Values values(int effect, String key, float value) {
        Map<String, Float> overrides = new LinkedHashMap<String, Float>();
        overrides.put(key, value);
        return EffectWorkshopConfig.create(effect, true, overrides);
    }
    private static EffectWorkshopConfig.Values corner(int effect, boolean maximum) {
        Map<String, Float> overrides = new LinkedHashMap<String, Float>();
        for (EffectWorkshopConfig.Parameter p : EffectWorkshopLgOpticsParameters.parametersFor(effect)) {
            overrides.put(p.key, maximum ? p.max : p.min);
        }
        return EffectWorkshopConfig.create(effect, true, overrides);
    }
    private static void finite(float value, String message) {
        require(EffectWorkshopConfig.finite(value), message);
    }
    public static void main(String[] args) {
        whiteHole(); dewdrop(); soda(); hula();
        System.out.println("EffectWorkshopLgOpticsTest: PASS (" + assertions + " assertions)");
    }
    private static void whiteHole() {
        float original = LgWhiteHoleWarp.displacement(100, 50, 100, 150, 1080);
        near(LgWhiteHoleWarp.displacement(100, 50, 100, 150, 1080, .48f, .14f), original,
                "explicit stock warp");
        near(LgWhiteHoleWarp.displacement(100, 50, 100, 150, 1080, 0, .14f), 0,
                "absorption strength really disables displacement");
        near(LgWhiteHoleWarp.displacement(100, 100, 100, 150, 1080, .48f, .28f),
                2 * LgWhiteHoleWarp.displacement(100, 100, 100, 150, 1080),
                "edge strength changes displacement");
        require(LgWhiteHoleWarp.displacement(150, 100, 100, 30, 1080) == 0,
                "narrow band leaves exterior untouched");
    }
    private static void dewdrop() {
        EffectWorkshopConfig.Values original = EffectWorkshopConfig.originals(39);
        near(EffectWorkshopLgOpticsParameters.dewdropEllipseHeight(original, 100, 1, 1000), 40,
                "stock ellipse aspect");
        near(EffectWorkshopLgOpticsParameters.dewdropEllipseHeight(
                values(39, "ellipse_ratio", .6f), 100, 1, 1000), 60, "custom ellipse aspect");
        float stockCap = EffectWorkshopLgOpticsParameters.dewdropEllipseHeight(original, 300, 1, 1000);
        float changedCap = EffectWorkshopLgOpticsParameters.dewdropEllipseHeight(
                values(39, "ellipse_cap", 180), 300, 1, 1000);
        require(stockCap != changedCap, "ellipse cap changes large-drop shape");
        float stockSource = EffectWorkshopLgOpticsParameters.dewdropSourceRadius(original, 50, 100, 40);
        float changedSource = EffectWorkshopLgOpticsParameters.dewdropSourceRadius(
                values(39, "refraction_index", 1.1f), 50, 100, 40);
        require(Math.abs(stockSource - changedSource) > .1f, "index changes actual Snell mapping");
        float overlay = EffectWorkshopLgOpticsParameters.dewdropOverlayDiameter(original, 200, 3);
        near(EffectWorkshopLgOpticsParameters.dewdropOverlayDiameter(
                values(39, "optical_overlay_scale", .5f), 200, 3), overlay * .5f,
                "optical overlay changes independently");
        for (boolean max : new boolean[] {false, true}) {
            EffectWorkshopConfig.Values c = corner(39, max);
            for (float radius : new float[] {0, .001f, 50, 200, 1000}) {
                float b = EffectWorkshopLgOpticsParameters.dewdropEllipseHeight(c, radius, 3, 1200);
                finite(b, "finite ellipse");
                finite(EffectWorkshopLgOpticsParameters.dewdropSourceRadius(c, radius * .7f,
                        radius, b), "finite refraction at limits");
                finite(EffectWorkshopLgOpticsParameters.dewdropOverlayDiameter(c, radius, 3),
                        "finite overlay at limits");
            }
        }
    }
    private static void soda() {
        EffectWorkshopConfig.Values original = EffectWorkshopConfig.originals(38);
        near(EffectWorkshopLgOpticsParameters.sodaRiseSpeed(original, .5f, false), 200,
                "stock rising speed");
        near(EffectWorkshopLgOpticsParameters.sodaRiseSpeed(original, .5f, true), 190,
                "stock large rising speed");
        near(EffectWorkshopLgOpticsParameters.sodaRiseSpeed(
                values(38, "rise_speed_max", 500), .5f, false), 290, "custom rising speed");
        near(EffectWorkshopLgOpticsParameters.sodaRiseSpeed(
                values(38, "large_speed_min", 200), .5f, true), 250, "custom large speed");
        require(EffectWorkshopLgOpticsParameters.sodaRiseCycle(original, 1000, 200) == 5000,
                "cycle comes from actual travel and speed");
        require(EffectWorkshopLgOpticsParameters.sodaRiseCycle(
                values(38, "rise_life_scale", 2), 1000, 200) == 10000, "custom derived cycle");
        for (int kind = 0; kind < 4; kind++) {
            String key = kind == 2 ? "large_size_scale" : kind == 3
                    ? "column_size_scale" : "small_size_scale";
            near(EffectWorkshopLgOpticsParameters.sodaSpriteSize(values(38, key, 2), kind, 10),
                    20, "family-specific sprite size " + kind);
        }
        near(EffectWorkshopLgOpticsParameters.sodaParticleAlpha(
                values(38, "particle_alpha", .5f), .8f, .75f), .3f, "alpha changes actual output");
    }
    private static float softbodyResponse(EffectWorkshopConfig.Values values, int index) {
        LgHulaHoopFluidicScene.SoftBody body = new LgHulaHoopFluidicScene.SoftBody(values);
        body.reset(1000); body.setRadii(100, 180);
        for (int i = 0; i < 20; i++) body.update(1016 + i * 16);
        return body.coordinate(index);
    }
    private static void hula() {
        EffectWorkshopConfig.Values original = EffectWorkshopConfig.originals(42);
        LgHulaHoopFluidicScene stockStretch = new LgHulaHoopFluidicScene(original);
        java.util.Random stretchRandom = new java.util.Random(42);
        for (int i = 0; i < 1000; i++) {
            float unit = stretchRandom.nextFloat();
            require(Float.floatToIntBits(stockStretch.stretchScale(unit))
                            == Float.floatToIntBits(.7f + unit * .4f),
                    "disabled V2 stretch preserves donor arithmetic exactly");
        }
        LgHulaHoopFluidicScene customStretch = new LgHulaHoopFluidicScene(
                values(42, "v2_stretch_max", 1.4f));
        near(customStretch.stretchScale(.5f), 1.05f, "custom V2 stretch range is consumed");
        LgHulaHoopScene independentTrail = new LgHulaHoopScene(
                values(42, "v1_layer_trail_0", 0));
        independentTrail.configure(1080, 2400, 3);
        independentTrail.begin(200, 500, 1000);
        independentTrail.move(700, 900, 1016);
        LgHulaHoopScene.Frame remainingTrail = independentTrail.sample(1216,
                new LgHulaHoopScene.Frame());
        require(Math.abs(remainingTrail.trailX) * independentTrail.layerTransition(1) >= 1f,
                "zero first holder preserves visible horizontal trail on another holder");
        require(Math.abs(remainingTrail.trailY) * independentTrail.layerTransition(1) >= 1f,
                "zero first holder preserves visible vertical trail on another holder");
        remainingTrail = independentTrail.sample(10000, new LgHulaHoopScene.Frame());
        require(remainingTrail.trailX == 0 && remainingTrail.trailY == 0,
                "shared trail still clears once all active holders are below one pixel");
        LgHulaHoopScene v1 = new LgHulaHoopScene(values(42, "v1_minimum_radius", 100));
        v1.configure(1080, 2400, 3);
        near(v1.minimumRadius(), 300, "V1 radius is consumed");
        LgHulaHoopScene rotated = new LgHulaHoopScene(values(42, "v1_rotation_min", 1400));
        rotated.configure(1080, 2400, 3); rotated.begin(200, 500, 1000);
        LgHulaHoopScene.Frame f = rotated.sample(1016, new LgHulaHoopScene.Frame());
        near(f.rotationPeriodMs, 1400, "V1 custom rotation period");
        LgHulaHoopScene layer = new LgHulaHoopScene(values(42, "v1_layer_base", 1.6f));
        layer.configure(1080, 2400, 3); layer.begin(200, 500, 1000);
        near(layer.sample(1400, new LgHulaHoopScene.Frame()).layerScale, 1.6f,
                "V1 actual layer geometry");
        for (int i = 0; i < 4; i++) {
            near(new LgHulaHoopScene(values(42, "v1_layer_trail_" + i, 2)).layerTransition(i), 2,
                    "V1 independent holder displacement");
            near(new LgHulaHoopScene(values(42, "v1_layer_angle_" + i, 45)).layerAngle(i), 45,
                    "V1 independent holder angle");
        }
        LgHulaHoopFluidicScene v2 = new LgHulaHoopFluidicScene(values(42, "v2_stretch_delay", 9));
        v2.configure(1080, 2400, 3); v2.begin(200, 500, 1000); v2.move(700, 500, 1016);
        LgHulaHoopFluidicScene.Frame g = v2.sample(1016, new LgHulaHoopFluidicScene.Frame());
        require(g.stretched && g.stretchDelayFrames == 8, "V2 custom delay enters actual frame state");
        LgHulaHoopFluidicScene limited = new LgHulaHoopFluidicScene(values(42, "v2_max_stretch", 1));
        limited.configure(1080, 2400, 3); limited.begin(200, 500, 1000); limited.move(700, 500, 1016);
        g = limited.sample(1016, new LgHulaHoopFluidicScene.Frame());
        near(g.radius, g.dragDistance, "V2 stretch cap changes shape");
        float baseline = softbodyResponse(original, 30);
        for (String key : new String[] {"v2_spring", "v2_damping", "v2_hermite_tangent"}) {
            float value = key.equals("v2_spring") ? .04f : key.equals("v2_damping") ? .15f : 2;
            require(Math.abs(softbodyResponse(values(42, key, value), 30) - baseline) > .01f,
                    "actual mesh responds to " + key);
        }
        for (boolean max : new boolean[] {false, true}) {
            EffectWorkshopConfig.Values c = corner(42, max);
            LgHulaHoopScene a = new LgHulaHoopScene(c); a.configure(1080, 2400, 3);
            a.begin(200, 500, 1000); a.move(700, 900, 1016);
            LgHulaHoopFluidicScene b = new LgHulaHoopFluidicScene(c); b.configure(1080, 2400, 3);
            b.begin(200, 500, 1000); b.move(700, 900, 1016);
            LgHulaHoopFluidicScene.SoftBody mesh = new LgHulaHoopFluidicScene.SoftBody(c);
            mesh.reset(1000); mesh.setRadii(100, 250);
            for (int frame = 0; frame < 200; frame++) {
                long time = 1032 + frame * 16L;
                f = a.sample(time, new LgHulaHoopScene.Frame());
                g = b.sample(time, new LgHulaHoopFluidicScene.Frame());
                finite(f.radius, "finite V1 radius"); finite(f.layerRadius, "finite V1 layer");
                finite(g.radius, "finite V2 radius"); mesh.update(time);
                for (int i = 0; i < 204; i++) finite(mesh.coordinate(i), "finite softbody coordinate");
            }
            a.finish(true, 5000); b.finish(true, 5000);
            require(a.sample(5600, new LgHulaHoopScene.Frame()).fullUnderlay, "V1 unlock clock unchanged");
            require(!a.sample(6150, new LgHulaHoopScene.Frame()).visible, "V1 terminal hold unchanged");
            require(b.sample(5250, new LgHulaHoopFluidicScene.Frame()).fullUnderlay, "V2 unlock clock unchanged");
            require(!b.sample(5800, new LgHulaHoopFluidicScene.Frame()).visible, "V2 terminal hold unchanged");
        }
    }
}
