package com.codex.lle;

import java.util.LinkedHashMap;
import java.util.Map;

/** Portable verification of the exact native layout, including hidden non-gyro slots. */
public final class EffectWorkshopDropsMappingTest {
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    public static void main(String[] args) {
        int[] effects = {22, 23, 24, 26};
        int[] visibleCounts = {48, 12, 14, 16};
        int[] nativeCounts = {48, 14, 14, 16};
        for (int e = 0; e < effects.length; e++) {
            int effect = effects[e];
            EffectWorkshopConfig.Parameter[] visible = EffectWorkshopDropsParameters.parametersFor(effect);
            require(visible.length == visibleCounts[e]);
            Map<String, Float> overrides = new LinkedHashMap<String, Float>();
            for (EffectWorkshopConfig.Parameter parameter : visible) {
                overrides.put(parameter.key, parameter.max);
                require(effect != 23 || !parameter.key.startsWith("tilt_"));
            }
            EffectWorkshopConfig.Values values = EffectWorkshopConfig.create(effect, true, overrides);
            overrides.clear(); // Constructor owns a snapshot.
            float[] packed = EffectWorkshopDropsParameters.pack(values);
            require(packed.length == nativeCounts[e]);
            EffectWorkshopConfig.Parameter[] nativeLayout =
                    EffectWorkshopDropsParameters.parametersFor(effect == 23 ? 24 : effect);
            for (int i = 0; i < packed.length; i++) {
                EffectWorkshopConfig.Parameter parameter = nativeLayout[i];
                boolean hidden = effect == 23 && parameter.key.startsWith("tilt_");
                require(packed[i] == (hidden ? parameter.defaultValue : parameter.max));
            }
            float[] originals = EffectWorkshopDropsParameters.pack(EffectWorkshopConfig.originals(effect));
            for (int i = 0; i < originals.length; i++) require(originals[i] == nativeLayout[i].defaultValue);
        }
        // Explicit named positions protect the Colour/S6 ABI shared by the two renderers.
        require("tilt_x_scale".equals(EffectWorkshopDropsParameters.parametersFor(24)[7].key));
        require("tilt_y_scale".equals(EffectWorkshopDropsParameters.parametersFor(24)[8].key));
        require("refraction_scale".equals(EffectWorkshopDropsParameters.parametersFor(24)[9].key));
        require("refraction_eta".equals(EffectWorkshopDropsParameters.parametersFor(26)[14].key));
        require("refraction_amplitude".equals(EffectWorkshopDropsParameters.parametersFor(26)[15].key));
        System.out.println("PASS drops Java snapshots, visible controls and native mapping");
    }
}
