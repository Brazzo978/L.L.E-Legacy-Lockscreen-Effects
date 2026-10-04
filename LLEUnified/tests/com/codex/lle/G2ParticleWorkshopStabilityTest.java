package com.codex.lle;

import java.util.HashMap;
import java.util.Map;

/** Multi-second stress coverage of configured physical gains, full batches and stall cadence. */
public final class G2ParticleWorkshopStabilityTest {
    public static void main(String[] args) {
        for (float speed : new float[] {.5f, 1f, 2f}) {
            exercise(corner(true), speed, 50);
            exercise(corner(false), speed, 16);
        }
        Map<String, Float> mixed = new HashMap<String, Float>();
        mixed.put("attraction", .4f); mixed.put("motion_speed", 2f);
        mixed.put("life_min_ms", 2500f); mixed.put("life_range_ms", 3000f);
        mixed.put("escape_strength", 50000f); mixed.put("target_f_dp", 50f);
        exercise(EffectWorkshopConfig.create(33, true, mixed), 2f, 50);
        checkStrongerAttractionStillConvergesFaster();
        System.out.println("G2 configured stability: 8-second held stress, max life/count/gain, stall cadence, release and speed controls passed");
    }
    private static EffectWorkshopConfig.Values corner(boolean maximum) {
        Map<String, Float> map = new HashMap<String, Float>();
        for (EffectWorkshopConfig.Parameter p : EffectWorkshopLgParameters.parametersFor(33)) map.put(p.key, maximum ? p.max : p.min);
        return EffectWorkshopConfig.create(33, true, map);
    }
    private static void exercise(EffectWorkshopConfig.Values values, float speed, long cadence) {
        G2ParticleScene scene = new G2ParticleScene(values);
        scene.setSurfaceSize(1080, 1920); scene.setDensity(4); scene.setSpeedMultiplier(speed);
        scene.begin(500, 900, 100);
        for (long t = 100; t <= 6100; t += cadence) checkBatch(scene, t);
        scene.move(950, 1500, 6150);
        for (long t = 6150; t <= 8100; t += cadence) checkBatch(scene, t);
        scene.finish(true, 8150);
        for (long t = 8150; t <= 9150; t += cadence) checkBatch(scene, t);
        scene.reset(); scene.begin(500, 900, 10000); scene.move(950, 1500, 10050); scene.finish(false, 10100);
        for (long t = 10100; t <= 10500; t += cadence) checkBatch(scene, t);
    }
    private static void checkBatch(G2ParticleScene scene, long time) {
        float[] batch = scene.fillVertices(time);
        if (batch.length != scene.particleCount() * G2ParticleScene.VERTEX_STRIDE) throw new AssertionError("Batch count");
        float limit = (float) Math.hypot(1080, 1920) * 5f;
        for (int i = 0; i < batch.length; i += G2ParticleScene.VERTEX_STRIDE) {
            for (int j = 0; j < G2ParticleScene.VERTEX_STRIDE; j++) {
                if (!EffectWorkshopConfig.finite(batch[i + j])) throw new AssertionError("Nonfinite at " + time + " tuple " + i);
            }
            float distance = (float) Math.hypot(batch[i] - 500, batch[i + 1] - 900);
            if (distance > limit) throw new AssertionError("Runaway at " + time + ": " + distance + " tuple " + i);
            if (batch[i + 2] < 0 || batch[i + 3] < 0 || batch[i + 3] > 1) throw new AssertionError("Size/alpha bounds");
        }
    }
    private static void checkStrongerAttractionStillConvergesFaster() {
        G2ParticleScene weak = oneE(.01f), strong = oneE(.4f);
        weak.begin(500, 900, 100); strong.begin(500, 900, 100);
        float[] a = weak.fillVertices(116), b = strong.fillVertices(116);
        float da = Math.abs(50 - (float) Math.hypot(a[0] - 500, a[1] - 900));
        float db = Math.abs(50 - (float) Math.hypot(b[0] - 500, b[1] - 900));
        if (!(db < da)) throw new AssertionError("Attraction slider lost its effect");
    }
    private static G2ParticleScene oneE(float attraction) {
        Map<String, Float> map = new HashMap<String, Float>();
        map.put("d_count", 0f); map.put("f_count", 0f); map.put("b_count", 0f); map.put("e_count", 1f);
        map.put("attraction", attraction);
        G2ParticleScene scene = new G2ParticleScene(EffectWorkshopConfig.create(33, true, map));
        scene.setSurfaceSize(1080, 1920); return scene;
    }
}
