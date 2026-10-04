package com.codex.lle;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Each effect keeps an independent, atomic applied snapshot. */
final class EffectWorkshopPrefs {
    private static final String PREFIX = "effect_workshop_config_";
    private static final Map<SharedPreferences, Map<Integer, SavedValues>> CACHE =
            new WeakHashMap<SharedPreferences, Map<Integer, SavedValues>>();
    private static final class SavedValues {
        final String encoded;
        final EffectWorkshopConfig.Values values;
        SavedValues(String encoded, EffectWorkshopConfig.Values values) {
            this.encoded = encoded;
            this.values = values;
        }
    }
    private EffectWorkshopPrefs() {}

    static synchronized EffectWorkshopConfig.Values values(Context context, int effect) {
        SharedPreferences preferences = OverlayPrefs.get(context);
        String encoded;
        try {
            encoded = preferences.getString(PREFIX + effect, null);
        } catch (ClassCastException ignored) {
            encoded = null;
        }
        Map<Integer, SavedValues> effects = CACHE.get(preferences);
        if (effects == null) {
            effects = new HashMap<Integer, SavedValues>();
            CACHE.put(preferences, effects);
        }
        SavedValues saved = effects.get(effect);
        if (saved != null && (encoded == null ? saved.encoded == null : encoded.equals(saved.encoded))) {
            return saved.values;
        }
        EffectWorkshopConfig.Values values = EffectWorkshopConfig.decode(effect, encoded);
        effects.put(effect, new SavedValues(encoded, values));
        return values;
    }

    static void save(Context context, EffectWorkshopConfig.Values values) {
        OverlayPrefs.get(context).edit().putString(PREFIX + values.effect,
                EffectWorkshopConfig.encode(values)).apply();
    }

    static int effectFromPreferenceKey(String key) {
        if (key == null || !key.startsWith(PREFIX)) return -1;
        try {
            int effect = Integer.parseInt(key.substring(PREFIX.length()));
            return key.equals(PREFIX + effect) && EffectWorkshopConfig.parametersFor(effect).length > 0
                    ? effect : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }
}
