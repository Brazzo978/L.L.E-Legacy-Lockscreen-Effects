package com.codex.lle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Exercises actual pipeline snapshots without creating an Android View or a GLES context. */
public final class EffectWorkshopSamsungParametersTest {
    public static void main(String[] args) throws Exception {
        int total = 0;
        for (int effect = 0; effect <= 44; effect++) {
            EffectWorkshopConfig.Parameter[] full = EffectWorkshopConfig.parametersFor(effect);
            HashSet<String> keys = new HashSet<String>();
            for (EffectWorkshopConfig.Parameter p : full) {
                if (!keys.add(p.key)) throw new AssertionError("Duplicate " + effect + ":" + p.key);
                if (p.defaultValue < p.min || p.defaultValue > p.max) throw new AssertionError(p.key);
            }
        }
        for (int effect : new int[] {2, 11, 13, 14, 15, 31}) {
            EffectWorkshopConfig.Parameter[] schema = EffectWorkshopSamsungParameters.parametersFor(effect);
            total += schema.length;
            for (EffectWorkshopConfig.Parameter p : schema) {
                Map<String, Float> override = new HashMap<String, Float>();
                override.put(p.key, p.max + 10000f);
                near(p.defaultValue, EffectWorkshopConfig.create(effect, false, override).get(p.key));
                near(p.max, EffectWorkshopConfig.create(effect, true, override).get(p.key));
                override.put(p.key, p.min - 10000f);
                near(p.min, EffectWorkshopConfig.create(effect, true, override).get(p.key));
            }
        }
        if (total != 103) throw new AssertionError("Samsung controls " + total);
        if (EffectWorkshopSamsungParameters.parametersFor(35).length != 0) throw new AssertionError("Sony scope");
        BrilliantCutGlesPipeline originalCut = new BrilliantCutGlesPipeline();
        near(.075f, field(originalCut, "touchEmitClock"));
        Map<String, Float> cutChanges = new HashMap<String, Float>();
        cutChanges.put("touch_radius", .6f);
        cutChanges.put("touch_lifetime_seconds", .3f);
        cutChanges.put("touch_grow_seconds", .5f);
        cutChanges.put("touch_next_term_seconds", .2f);
        cutChanges.put("specular_strength", 1.2f);
        cutChanges.put("specular_power", 23f);
        cutChanges.put("glare_gain", 2f);
        BrilliantCutGlesPipeline cut = new BrilliantCutGlesPipeline(EffectWorkshopConfig.create(15, true, cutChanges));
        near(.6f, field(cut, "workshopTouchRadius"));
        near(.27f, field(cut, "workshopTouchGrowSeconds"));
        near(.2f, field(cut, "touchEmitClock"));
        String shader = method(cut, "workshopVertexShader");
        if (!shader.contains("1.2*pow") || !shader.contains(")),23.0)")
                || !shader.contains("vec4(color,1.0)*2.0")) throw new AssertionError("Cut optics not applied");
        Map<String, Float> ringChanges = new HashMap<String, Float>();
        ringChanges.put("normal_radius", 48f);
        ringChanges.put("max_active_records", 15f);
        ringChanges.put("hue_shift", .08f);
        ringChanges.put("blur_gain", .4f);
        BrilliantRingGlesPipeline ring = new BrilliantRingGlesPipeline(EffectWorkshopConfig.create(14, true, ringChanges));
        near(48f, field(ring, "workshopNormalRadius"));
        near(15f, field(ring, "workshopMaxActiveRecords"));
        shader = method(ring, "workshopRingFragmentShader");
        if (!shader.contains("hsv.r - 0.08") || !shader.contains("blurColor * 0.4")) throw new AssertionError("Ring optics not applied");
        BrilliantRingGlesPipeline originalRing = new BrilliantRingGlesPipeline();
        Method stock = BrilliantRingGlesPipeline.class.getDeclaredMethod("overlayRingFragmentShader");
        stock.setAccessible(true);
        if (!stock.invoke(null).equals(method(originalRing, "workshopRingFragmentShader")))
                throw new AssertionError("Disabled Ring shader changed");
        near(.5f, BlindArm64EffectView.moveFollowForElapsedNanos(16666667L, .5f));
        near(.75f, BlindArm64EffectView.moveFollowForElapsedNanos(33333334L, .5f));
        System.out.println("Samsung workshop: " + total + " descriptors, all IDs schema, snapshots, optics and bounds passed");
    }
    private static float field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return ((Number) f.get(object)).floatValue();
    }
    private static String method(Object object, String name) throws Exception {
        Method m = object.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return (String) m.invoke(object);
    }
    private static void near(float expected, float actual) {
        if (Math.abs(expected - actual) > .00001f) throw new AssertionError(expected + " != " + actual);
    }
}
