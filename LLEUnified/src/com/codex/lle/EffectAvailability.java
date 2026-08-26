package com.codex.lle;

import android.content.Context;
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

    static boolean hasLegacyVendorEffects() {
        return BuildFlavor.LEGACY_VENDOR_EFFECTS;
    }

    static String buildFlavorLabel() {
        return BuildFlavor.NAME;
    }

    static boolean isLegacyVendorEffect(int effect) {
        return effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET;
    }

    static boolean isAvailable(Context context, int effect) {
        return isAvailable(effect);
    }

    static boolean isAvailable(int effect) {
        if (!BuildFlavor.LEGACY_VENDOR_EFFECTS && isLegacyVendorEffect(effect)) {
            return false;
        }
        switch (effect) {
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
            case OverlayPrefs.EFFECT_N4_INK_IN_WATER:
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
            case OverlayPrefs.EFFECT_WATERCOLOUR:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
            case OverlayPrefs.EFFECT_TABS_BLIND:
            case OverlayPrefs.EFFECT_STONE_SKIPPING:
            case OverlayPrefs.EFFECT_MASS_TENSION:
            case OverlayPrefs.EFFECT_BRILLIANT_RING:
            case OverlayPrefs.EFFECT_BRILLIANT_CUT:
            case OverlayPrefs.EFFECT_SEASONAL_SPRING:
            case OverlayPrefs.EFFECT_SEASONAL_SUMMER:
            case OverlayPrefs.EFFECT_SEASONAL_AUTUMN:
            case OverlayPrefs.EFFECT_SEASONAL_WINTER:
            case OverlayPrefs.EFFECT_SEASONAL_AUTO:
            case OverlayPrefs.EFFECT_GOOD_LOCK_POPPING:
            case OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE:
            case OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING:
                return true;
            case OverlayPrefs.EFFECT_S3_NONE:
            case OverlayPrefs.EFFECT_LG_G2_PIXELATE:
            case OverlayPrefs.EFFECT_LG_G2_PARTICLE:
            case OverlayPrefs.EFFECT_LG_G2_CRYSTAL:
            case OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS:
            case OverlayPrefs.EFFECT_REVOLVING_GLASS:
            case OverlayPrefs.EFFECT_LG_G1_WHITE_HOLE:
            case OverlayPrefs.EFFECT_LG_SODA:
            case OverlayPrefs.EFFECT_LG_G1_DEWDROP:
            case OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE:
                return BuildFlavor.TESTER && ARM64_PROCESS;
            case OverlayPrefs.EFFECT_RIPPLE_INK:
                return ARM64_PROCESS;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED:
                return ARM64_PROCESS;
            default:
                return false;
        }
    }

    static boolean usesLegacyArm32Engine(int effect) {
        if (ARM64_PROCESS) {
            return false;
        }
        return effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || effect == OverlayPrefs.EFFECT_N4_INK_IN_WATER
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                || effect == OverlayPrefs.EFFECT_BRILLIANT_RING
                || effect == OverlayPrefs.EFFECT_BRILLIANT_CUT;
    }
}
