package com.codex.lle;

import android.os.Process;

/** Central ABI policy for the shared ARM32/ARM64 application source. */
final class EffectAvailability {
    private static final boolean ARM64_PROCESS = Process.is64Bit();

    private EffectAvailability() {
    }

    static boolean is64BitProcess() {
        return ARM64_PROCESS;
    }

    static String processAbiLabel() {
        return ARM64_PROCESS ? "ARM64" : "ARM32 legacy";
    }

    static boolean isAvailable(int effect) {
        switch (effect) {
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
            case OverlayPrefs.EFFECT_WATERCOLOUR:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                return true;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                return !ARM64_PROCESS;
            case OverlayPrefs.EFFECT_TABS_BLIND_WIP:
            case OverlayPrefs.EFFECT_N3_INK_IN_WATER_WIP:
            default:
                return false;
        }
    }

    static boolean usesLegacyArm32Engine(int effect) {
        if (ARM64_PROCESS) {
            return false;
        }
        return effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC;
    }
}
