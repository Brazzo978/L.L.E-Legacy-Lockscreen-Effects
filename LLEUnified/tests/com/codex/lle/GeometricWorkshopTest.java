package com.codex.lle;
import java.util.HashMap;
import java.util.Map;

/** Exercises the actual mask timeline without a GLES context. */
public final class GeometricWorkshopTest {
    public static void main(String[] args) {
        Map<String, Float> values = new HashMap<String, Float>();
        values.put("touch_grow", .3f);
        values.put("touch_start", .5f);
        values.put("touch_peak", 2f);
        values.put("touch_spacing", .1f);
        GeometricMosaicGlesPipeline custom = new GeometricMosaicGlesPipeline(
                EffectWorkshopConfig.create(8, true, values));
        long start=1_000_000_000L;
        if (!custom.addTouch(.2f,.3f,start)) throw new AssertionError("first touch");
        near(.5f,custom.terminalRadiusForTest(start));
        near(2f,custom.terminalRadiusForTest(start+300_000_000L));
        if (custom.addTouch(.21f,.31f,start+350_000_000L)) throw new AssertionError("custom spacing");
        custom.unlockAt(.21f,.31f,start+350_000_000L);
        near(5f,custom.terminalRadiusForTest(start+750_000_000L));
        GeometricMosaicGlesPipeline stock = new GeometricMosaicGlesPipeline(
                EffectWorkshopConfig.create(8, false, values));
        stock.addTouch(.2f,.3f,start);
        near(.3f,stock.terminalRadiusForTest(start));
        near(.8f,stock.terminalRadiusForTest(start+150_000_000L));
        if (!stock.addTouch(.21f,.31f,start+350_000_000L)) throw new AssertionError("stock spacing");
        System.out.println("GeometricWorkshopTest: custom timeline, spacing, disabled stock and fixed handoff passed");
    }
    private static void near(float expected,float actual) {
        if (Math.abs(expected-actual)>.00001f) throw new AssertionError(expected+" != "+actual);
    }
}
