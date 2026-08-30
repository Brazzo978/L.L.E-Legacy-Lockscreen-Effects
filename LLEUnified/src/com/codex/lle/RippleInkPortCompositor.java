package com.codex.lle;

/** Pure math shared by Ripple Ink's procedural reflection and transparent-overlay tests. */
final class RippleInkPortCompositor {
    static final int REFLECTION_BLACK_CUTOFF = 12;
    static final int REFLECTION_FULL_BOOST_AT = 48;
    static final float REFLECTION_CHANNEL_BOOST = 90.0f;
    static final float OVERLAY_MASK_LOW = 0.035f;
    static final float OVERLAY_MASK_HIGH = 0.18f;
    private static final float INK_WHITE_LEVEL = 1.5f;

    private RippleInkPortCompositor() {
    }

    /** One-shot stock idle notification; it deliberately owns no cleanup or elapsed-time state. */
    static final class RetainedInkIdleSignal {
        private boolean published;

        boolean shouldPublish(boolean touched, boolean waterActive) {
            if (touched || waterActive) {
                published = false;
                return false;
            }
            if (published) {
                return false;
            }
            published = true;
            return true;
        }

        void onActivity() {
            published = false;
        }

        void reset() {
            published = false;
        }
    }

    static float visibleInkMass(float densityMass, float opacity) {
        return Math.max(0.0f, densityMass) * clamp01(opacity);
    }

    /**
     * Calibrates the bundled dark forest-sphere map toward the bright Ripple Ink family map.
     * Black surround stays black so the calibration does not introduce a bright border.
     */
    static int calibrateReflectionPixel(int argb) {
        int alpha = argb >>> 24;
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        int peak = Math.max(red, Math.max(green, blue));
        if (peak <= REFLECTION_BLACK_CUTOFF) {
            return argb;
        }
        float progress = clamp01((peak - REFLECTION_BLACK_CUTOFF)
                / (float) (REFLECTION_FULL_BOOST_AT - REFLECTION_BLACK_CUTOFF));
        float smooth = progress * progress * (3.0f - 2.0f * progress);
        int boost = Math.round(REFLECTION_CHANNEL_BOOST * smooth);
        return (alpha << 24)
                | (Math.min(255, red + boost) << 16)
                | (Math.min(255, green + boost) << 8)
                | Math.min(255, blue + boost);
    }

    /** CPU mirror of the production fragment's exact reconstruction over nonrefracted RGB. */
    static float[] transparentInkOverlay(
            float baseRed,
            float baseGreen,
            float baseBlue,
            float waterRed,
            float waterGreen,
            float waterBlue,
            float paletteRed,
            float paletteGreen,
            float paletteBlue,
            float decodedDensity,
            float intensity,
            float slopeStrength) {
        float[] base = {clamp01(baseRed), clamp01(baseGreen), clamp01(baseBlue)};
        float[] waterTarget = {
                clamp01(waterRed), clamp01(waterGreen), clamp01(waterBlue)
        };
        float[] palette = {paletteRed, paletteGreen, paletteBlue};
        float[] inkTarget = inkTarget(waterTarget, palette, decodedDensity, intensity);
        float waterAlpha = smoothstep(OVERLAY_MASK_LOW, OVERLAY_MASK_HIGH, slopeStrength);
        float inkCoverage = clamp01(decodedDensity * intensity);
        float effectiveWaterAlpha = waterAlpha + (1.0f - waterAlpha) * inkCoverage;
        float[] target = new float[3];
        for (int channel = 0; channel < 3; ++channel) {
            float waterComposite = base[channel]
                    + effectiveWaterAlpha * (waterTarget[channel] - base[channel]);
            target[channel] = clamp01(
                    waterComposite + (inkTarget[channel] - waterTarget[channel]));
        }
        float alpha = minimumSourceOverAlpha(base, target);
        float[] output = new float[4];
        for (int channel = 0; channel < 3; ++channel) {
            output[channel] = clamp(
                    target[channel] - (1.0f - alpha) * base[channel], 0.0f, alpha);
        }
        output[3] = alpha;
        return output;
    }

    /** Finite raw-palette form with continuous transparent adaptation for exact-zero channels. */
    static float[] inkTarget(
            float[] waterTarget,
            float[] palette,
            float decodedDensity,
            float intensity) {
        if (waterTarget == null || waterTarget.length < 3
                || palette == null || palette.length < 3) {
            throw new IllegalArgumentException("water target and palette must have three channels");
        }
        float[] target = {
                clamp01(waterTarget[0]), clamp01(waterTarget[1]), clamp01(waterTarget[2])
        };
        float weight = decodedDensity * intensity;
        if (weight <= 0.0f) {
            return target;
        }
        float inkCoverage = clamp01(weight);
        for (int channel = 0; channel < 3; ++channel) {
            float component = palette[channel];
            float waterComponent = clamp01(waterTarget[channel]);
            float denominator = component + weight * (INK_WHITE_LEVEL - component);
            target[channel] = denominator > 0.0f
                    ? clamp01(target[channel] * component / denominator)
                    : target[channel];
            if (component == 0.0f) {
                target[channel] = clamp01(waterComponent
                        + inkCoverage * (target[channel] - waterComponent));
            }
        }
        return target;
    }

    static float minimumSourceOverAlpha(float[] base, float[] target) {
        if (base == null || base.length < 3 || target == null || target.length < 3) {
            throw new IllegalArgumentException("base and target must have three channels");
        }
        float alpha = 0.0f;
        for (int channel = 0; channel < 3; ++channel) {
            float baseChannel = clamp01(base[channel]);
            float targetChannel = clamp01(target[channel]);
            float need = 0.0f;
            if (targetChannel < baseChannel && baseChannel > 0.0f) {
                need = (baseChannel - targetChannel) / baseChannel;
            } else if (targetChannel > baseChannel && baseChannel < 1.0f) {
                need = (targetChannel - baseChannel) / (1.0f - baseChannel);
            }
            alpha = Math.max(alpha, need);
        }
        return clamp01(alpha);
    }

    /** CPU form of the fragment shader's local-alpha/source-over reconstruction. */
    static float[] transparentOverlay(
            float baseRed,
            float baseGreen,
            float baseBlue,
            float resultRed,
            float resultGreen,
            float resultBlue,
            float opacity) {
        float[] base = {
                clamp01(baseRed), clamp01(baseGreen), clamp01(baseBlue)
        };
        float[] result = {
                clamp01(resultRed), clamp01(resultGreen), clamp01(resultBlue)
        };
        float mask = clamp01(minimumSourceOverAlpha(base, result) * opacity);
        if (mask <= 0.0f) {
            return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        }
        float[] output = new float[4];
        for (int channel = 0; channel < 3; ++channel) {
            output[channel] = clamp(
                    result[channel] - (1.0f - mask) * base[channel], 0.0f, mask);
        }
        output[3] = mask;
        return output;
    }

    static float[] sourceOver(float[] premultiplied, float red, float green, float blue) {
        float remainder = 1.0f - premultiplied[3];
        return new float[]{
                premultiplied[0] + remainder * red,
                premultiplied[1] + remainder * green,
                premultiplied[2] + remainder * blue
        };
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float progress = clamp01((value - edge0) / (edge1 - edge0));
        return progress * progress * (3.0f - 2.0f * progress);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
