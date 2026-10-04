package com.codex.lle;

import java.util.HashMap;
import java.util.Map;

public final class EffectWorkshopConfigTest {
    private static int assertions;
    public static void main(String[] args) {
        int[] effects = {0, 2, 3, 7, 8, 10, 11, 12, 13, 14, 15, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 16, 17, 18, 19, 20, 44,
                32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43};
        int count = 0;
        for (int effect : effects) {
            EffectWorkshopConfig.Parameter[] schema = EffectWorkshopConfig.parametersFor(effect);
            check(schema.length > 0, "registered effect " + effect);
            Map<String, Float> overrides = new HashMap<String, Float>();
            for (EffectWorkshopConfig.Parameter p : schema) {
                count++;
                check(!overrides.containsKey(p.key), "unique keys");
                check(p.defaultValue >= p.min && p.defaultValue <= p.max, "default in range");
                check(!p.label.trim().isEmpty() && !p.group.trim().isEmpty(), "visible label/group");
                check(!p.description.trim().isEmpty(), "help required for effect " + effect + ": " + p.key);
                overrides.put(p.key, p.max);
            }
            EffectWorkshopConfig.Values active = EffectWorkshopConfig.create(effect, true, overrides);
            EffectWorkshopConfig.Values roundTrip = EffectWorkshopConfig.decode(effect,
                    EffectWorkshopConfig.encode(active));
            check(roundTrip.enabled, "enable round trip");
            EffectWorkshopConfig.Values inactive = EffectWorkshopConfig.create(effect, false, overrides);
            EffectWorkshopConfig.Values inactiveRoundTrip = EffectWorkshopConfig.decode(effect,
                    EffectWorkshopConfig.encode(inactive));
            for (EffectWorkshopConfig.Parameter p : schema) {
                equal(active.get(p.key), roundTrip.get(p.key), "active round trip");
                equal(p.defaultValue, inactiveRoundTrip.get(p.key), "disabled stock");
                equal(p.max, inactiveRoundTrip.configuredValue(p.key), "disabled keeps draft");
                overrides.put(p.key, Float.NaN);
            }
            EffectWorkshopConfig.Values invalid = EffectWorkshopConfig.create(effect, true, overrides);
            for (EffectWorkshopConfig.Parameter p : schema) {
                equal(p.defaultValue, invalid.get(p.key), "nonfinite fallback");
                overrides.put(p.key, p.max + 1000);
            }
            EffectWorkshopConfig.Values high = EffectWorkshopConfig.create(effect, true, overrides);
            for (EffectWorkshopConfig.Parameter p : schema) {
                equal(p.max, high.get(p.key), "upper clamp");
                overrides.put(p.key, p.min - 1000);
            }
            EffectWorkshopConfig.Values low = EffectWorkshopConfig.create(effect, true, overrides);
            for (EffectWorkshopConfig.Parameter p : schema) equal(p.min, low.get(p.key), "lower clamp");
        }
        EffectWorkshopConfig.Values corrupt = EffectWorkshopConfig.decode(0,
                "1;1;global_alpha=NaN;tap_hexagon_count=999;fade_duration=bad;future_field=42");
        equal(.8f, corrupt.get("global_alpha"), "corrupt field original");
        equal(32, corrupt.intValue("tap_hexagon_count"), "count bounded");
        equal(500, corrupt.get("fade_duration"), "malformed field original");
        equal(8, EffectWorkshopConfig.decode(0, "1;1;tap_hexagon_count=7.7")
                .intValue("tap_hexagon_count"), "count integer");
        check(!EffectWorkshopConfig.decode(0, "2;1;global_alpha=.1").enabled, "future version stock");
        check(!EffectWorkshopConfig.decode(0, "1;maybe").enabled, "corrupt enable stock");
        check(!EffectWorkshopConfig.decode(0, null).enabled, "absent stock");
        check(!EffectWorkshopConfig.create(999, true, null).enabled, "unsupported disabled");
        equal(.8f, EffectWorkshopConfig.originals(0).get("global_alpha"), "effect isolation");
        System.out.println("EffectWorkshopConfigTest: " + assertions + " assertions passed; "
                + count + " controls across " + effects.length + " effect IDs");
    }
    private static void equal(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .0001f, message + ": " + expected + " != " + actual);
    }
    private static void check(boolean pass, String message) {
        assertions++;
        if (!pass) throw new AssertionError(message);
    }
}
