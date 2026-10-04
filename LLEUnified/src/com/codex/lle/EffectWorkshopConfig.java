package com.codex.lle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Android-independent schema, bounded values and versioned persistence for workshop settings. */
final class EffectWorkshopConfig {
    private EffectWorkshopConfig() {}

    static final class Parameter {
        final String key, label, group, unit, description;
        final float defaultValue, min, max, step;
        final boolean integer;

        private Parameter(String key, String label, String group, float value,
                float min, float max, float step, String unit, boolean integer, String description) {
            if (!key.matches("[a-z0-9_]+") || !finite(value) || !finite(min)
                    || !finite(max) || !finite(step) || min > max || step <= 0
                    || value < min || value > max) throw new IllegalArgumentException(key);
            this.key = key; this.label = label; this.group = group; this.unit = unit;
            this.description = description == null ? "" : description;
            this.defaultValue = value; this.min = min; this.max = max; this.step = step;
            this.integer = integer;
        }

        static Parameter number(String key, String label, String group, float value,
                float min, float max, float step, String unit) {
            return number(key, label, group, value, min, max, step, unit, "");
        }

        static Parameter integer(String key, String label, String group, float value,
                float min, float max, float step, String unit) {
            return integer(key, label, group, value, min, max, step, unit, "");
        }

        static Parameter number(String key, String label, String group, float value,
                float min, float max, float step, String unit, String description) {
            return new Parameter(key, label, group, value, min, max, step, unit, false, description);
        }

        static Parameter integer(String key, String label, String group, float value,
                float min, float max, float step, String unit, String description) {
            return new Parameter(key, label, group, value, min, max, step, unit, true, description);
        }

        float sanitize(float value) {
            if (!finite(value)) return defaultValue;
            float bounded = Math.max(min, Math.min(max, value));
            return integer ? Math.round(bounded) : bounded;
        }
    }

    static Parameter number(String key, String label, String group, float value,
            float min, float max, float step, String unit) {
        return Parameter.number(key, label, group, value, min, max, step, unit);
    }

    static Parameter integer(String key, String label, String group, float value,
            float min, float max, float step, String unit) {
        return Parameter.integer(key, label, group, value, min, max, step, unit);
    }

    static Parameter number(String key, String label, String group, float value,
            float min, float max, float step, String unit, String description) {
        return Parameter.number(key, label, group, value, min, max, step, unit, description);
    }

    static Parameter integer(String key, String label, String group, float value,
            float min, float max, float step, String unit, String description) {
        return Parameter.integer(key, label, group, value, min, max, step, unit, description);
    }

    static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static Parameter[] parametersFor(int effect) {
        if (effect == 0) return EffectWorkshopLensParameters.parameters();
        if (effect == 44) return EffectWorkshopEmojiParameters.parameters();
        Parameter[] parameters = EffectWorkshopParticleParameters.parametersFor(effect);
        if (parameters.length > 0) return parameters;
        parameters = EffectWorkshopSamsungParameters.parametersFor(effect);
        if (parameters.length > 0) return parameters;
        parameters = EffectWorkshopMosaicParameters.parametersFor(effect);
        if (parameters.length > 0) return parameters;
        parameters = EffectWorkshopDropsParameters.parametersFor(effect);
        if (parameters.length > 0) return parameters;
        parameters = EffectWorkshopInkParameters.parametersFor(effect);
        if (parameters.length > 0) return parameters;
        parameters = EffectWorkshopLgParameters.parametersFor(effect);
        if (parameters.length > 0) return parameters;
        parameters = EffectWorkshopLgOpticsParameters.parametersFor(effect);
        if (parameters.length > 0) return parameters;
        return EffectWorkshopSonyParameters.parametersFor(effect);
    }

    static final class Values {
        final int effect;
        public final boolean enabled;
        private final Map<String, Parameter> schema;
        private final Map<String, Float> configured;
        private String signature;

        private Values(int effect, boolean enabled, Map<String, Float> overrides) {
            this.effect = effect;
            LinkedHashMap<String, Parameter> schema = new LinkedHashMap<String, Parameter>();
            LinkedHashMap<String, Float> configured = new LinkedHashMap<String, Float>();
            for (Parameter p : parametersFor(effect)) {
                if (schema.put(p.key, p) != null) throw new IllegalArgumentException(p.key);
                Float value = overrides == null ? null : overrides.get(p.key);
                configured.put(p.key, p.sanitize(value == null ? p.defaultValue : value));
            }
            this.enabled = enabled && !schema.isEmpty();
            this.schema = Collections.unmodifiableMap(schema);
            this.configured = Collections.unmodifiableMap(configured);
        }

        public float get(String key) {
            Parameter p = schema.get(key);
            if (p == null) throw new IllegalArgumentException("Unknown workshop parameter: " + key);
            return enabled ? configured.get(key) : p.defaultValue;
        }

        public int intValue(String key) { return Math.round(get(key)); }

        float configuredValue(String key) {
            Float value = configured.get(key);
            if (value == null) throw new IllegalArgumentException(key);
            return value;
        }

        Map<String, Float> configuredValues() { return configured; }
    }

    static Values create(int effect, boolean enabled, Map<String, Float> overrides) {
        return new Values(effect, enabled, overrides);
    }

    static Values originals(int effect) { return create(effect, false, null); }

    static String runtimeSignature(Values values) {
        // Retained drafts have no runtime effect while customization is disabled.
        if (values.signature == null) {
            values.signature = encode(values.enabled ? values : originals(values.effect));
        }
        return values.signature;
    }

    // One preference contains both enable state and values, applied as one renderer update.
    static String encode(Values values) {
        StringBuilder result = new StringBuilder("1;").append(values.enabled ? '1' : '0');
        for (Map.Entry<String, Float> entry : values.configured.entrySet()) {
            result.append(';').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }

    static Values decode(int effect, String encoded) {
        if (encoded == null || encoded.length() > 16384) return originals(effect);
        String[] parts = encoded.split(";");
        if (parts.length < 2 || !"1".equals(parts[0])
                || !("0".equals(parts[1]) || "1".equals(parts[1]))) return originals(effect);
        Map<String, Float> values = new LinkedHashMap<String, Float>();
        for (int i = 2; i < parts.length; i++) {
            int split = parts[i].indexOf('=');
            if (split <= 0) continue;
            try {
                float value = Float.parseFloat(parts[i].substring(split + 1));
                if (finite(value)) values.put(parts[i].substring(0, split), value);
            } catch (NumberFormatException ignored) { /* A bad field uses its original value. */ }
        }
        return create(effect, "1".equals(parts[1]), values);
    }
}
