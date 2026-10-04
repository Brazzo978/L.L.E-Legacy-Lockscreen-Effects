package com.codex.lle;

import java.util.LinkedHashMap;
import java.util.Map;

/** A preloaded renderer must stop being reusable after a missed preference notification. */
public final class EffectWorkshopRuntimeSignatureTest {
    public static void main(String[] args) {
        for (int effect : new int[] {0, 10, 12, 23, 24, 26, 27}) {
            EffectWorkshopConfig.Parameter first = EffectWorkshopConfig.parametersFor(effect)[0];
            Map<String, Float> change = new LinkedHashMap<String, Float>();
            change.put(first.key, first.defaultValue == first.max ? first.min : first.max);
            EffectWorkshopConfig.Values stock = EffectWorkshopConfig.originals(effect);
            String originalRenderer = EffectWorkshopConfig.runtimeSignature(stock);
            EffectWorkshopConfig.Values kept = EffectWorkshopConfig.create(effect, false, change);
            require(originalRenderer.equals(EffectWorkshopConfig.runtimeSignature(kept)),
                    "disabled drafts do not change a running effect");
            EffectWorkshopConfig.Values active = EffectWorkshopConfig.create(effect, true, change);
            String customRenderer = EffectWorkshopConfig.runtimeSignature(active);
            require(!originalRenderer.equals(customRenderer), "enable/edit requires replacement");
            require(customRenderer.equals(EffectWorkshopConfig.runtimeSignature(
                    EffectWorkshopConfig.decode(effect, EffectWorkshopConfig.encode(active)))),
                    "saved snapshot matches replacement renderer");
            require(!customRenderer.equals(EffectWorkshopConfig.runtimeSignature(kept)),
                    "disable requires restoring original renderer");
            change.put(first.key, first.defaultValue);
            require(!customRenderer.equals(EffectWorkshopConfig.runtimeSignature(
                    EffectWorkshopConfig.create(effect, true, change))), "individual reset requires replacement");
            require(originalRenderer.equals(EffectWorkshopConfig.runtimeSignature(
                    EffectWorkshopConfig.decode(effect, "future;1"))), "corrupt snapshot restores stock");
        }
        System.out.println("EffectWorkshopRuntimeSignatureTest: missed notification, enable/disable, reset and persistence passed");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
