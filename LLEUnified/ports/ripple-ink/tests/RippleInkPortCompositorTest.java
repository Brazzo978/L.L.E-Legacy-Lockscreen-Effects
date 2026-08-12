package com.codex.lle;

/** Deterministic offscreen-equivalent checks for reflection and transparent local alpha. */
public final class RippleInkPortCompositorTest {
    private static final float EPSILON = 0.00001f;

    public static void main(String[] args) {
        verifyRetainedInkIdleSignalHasNoReleaseTimer();
        verifyReflectionCalibrationPreservesBlackSurround();
        verifyUnchangedPixelsAreFullyTransparent();
        verifyDarkAndLightReconstruction();
        verifyRawPaletteStockFormula();
        verifyFinitePaletteDensityBackgroundMatrix();
        verifyZeroChannelDeltaAdaptation();
        verifyExactReconstructionIdentities();
        verifyDensityConditionedStockConvergence();
        verifyShaderKeepsRecoveredPassMathAndLocalAlpha();
        System.out.println("RippleInkPortCompositorTest: PASS");
    }

    private static void verifyRetainedInkIdleSignalHasNoReleaseTimer() {
        RippleInkPortCompositor.RetainedInkIdleSignal signal =
                new RippleInkPortCompositor.RetainedInkIdleSignal();
        for (int frame = 0; frame < 1_000; ++frame) {
            require("active water never reaches a timed terminal state",
                    !signal.shouldPublish(false, true));
        }
        require("first physically quiet frame publishes idle",
                signal.shouldPublish(false, false));
        require("quiet state publishes only once",
                !signal.shouldPublish(false, false));

        signal.onActivity();
        require("new DOWN/MOVE rearms without clearing retained ink",
                !signal.shouldPublish(true, true));
        require("following quiet frame publishes again",
                signal.shouldPublish(false, false));
    }

    private static void verifyReflectionCalibrationPreservesBlackSurround() {
        require("black remains byte-identical",
                RippleInkPortCompositor.calibrateReflectionPixel(0xff000000) == 0xff000000);
        require("near-black surround remains byte-identical",
                RippleInkPortCompositor.calibrateReflectionPixel(0xff0c0804) == 0xff0c0804);
        require("bright forest pixel receives calibrated +90",
                RippleInkPortCompositor.calibrateReflectionPixel(0xff643219) == 0xffbe8c73);
        require("calibration clamps without wrapping",
                RippleInkPortCompositor.calibrateReflectionPixel(0xfff0e0d0) == 0xffffffff);
    }

    private static void verifyUnchangedPixelsAreFullyTransparent() {
        float[] output = RippleInkPortCompositor.transparentOverlay(
                0.2f, 0.5f, 0.8f,
                0.2f, 0.5f, 0.8f,
                1.0f);
        requireRaw("unchanged red", 0.0f, output[0]);
        requireRaw("unchanged green", 0.0f, output[1]);
        requireRaw("unchanged blue", 0.0f, output[2]);
        requireRaw("unchanged alpha", 0.0f, output[3]);
    }

    private static void verifyDarkAndLightReconstruction() {
        verifyReconstruction(
                new float[]{0.8f, 0.6f, 0.4f},
                new float[]{0.4f, 0.3f, 0.2f});
        verifyReconstruction(
                new float[]{0.1f, 0.3f, 0.5f},
                new float[]{0.55f, 0.65f, 0.75f});
        verifyReconstruction(
                new float[]{0.8f, 0.2f, 0.5f},
                new float[]{0.5f, 0.7f, 0.4f});
    }

    private static void verifyReconstruction(float[] base, float[] target) {
        float[] output = RippleInkPortCompositor.transparentOverlay(
                base[0], base[1], base[2],
                target[0], target[1], target[2],
                1.0f);
        float[] composed = RippleInkPortCompositor.sourceOver(
                output, base[0], base[1], base[2]);
        for (int channel = 0; channel < 3; ++channel) {
            requireClose("source-over reconstructs target channel " + channel,
                    target[channel], composed[channel]);
        }
    }

    private static void verifyFinitePaletteDensityBackgroundMatrix() {
        float[][] bases = {
                {0.0f, 0.0f, 0.0f},
                {0.0001f, 0.0001f, 0.0001f},
                {0.2f, 0.5f, 0.8f},
                {0.9999f, 0.9999f, 0.9999f},
                {1.0f, 1.0f, 1.0f}
        };
        float[][] waterTargets = {
                {0.0f, 0.0f, 0.0f},
                {0.02f, 0.04f, 0.08f},
                {0.23f, 0.61f, 0.84f},
                {0.91f, 0.22f, 0.46f},
                {1.0f, 1.0f, 1.0f}
        };
        float[] densities = {0.0f, 0.0001f, 1.0f, 25.0f, 50.0f, 127.0f};
        float[] slopes = {0.0f, 0.035f, 0.1075f, 0.18f, 0.5f};
        for (int selector = 1; selector <= RippleInkPortEngine.paletteCount(); ++selector) {
            float[] palette = {
                    RippleInkPortEngine.paletteComponent(selector, 0),
                    RippleInkPortEngine.paletteComponent(selector, 1),
                    RippleInkPortEngine.paletteComponent(selector, 2)
            };
            for (float[] water : waterTargets) {
                for (float density : densities) {
                    float[] inkTarget = RippleInkPortCompositor.inkTarget(
                            water, palette, density, 0.02f);
                    assertFiniteRgb("palette " + selector + " density " + density
                            + " ink target", inkTarget);
                    for (float[] base : bases) {
                        for (float slope : slopes) {
                            float[] layer = RippleInkPortCompositor.transparentInkOverlay(
                                    base[0], base[1], base[2],
                                    water[0], water[1], water[2],
                                    palette[0], palette[1], palette[2],
                                    density, 0.02f, slope);
                            String label = "palette " + selector + " density " + density
                                    + " slope " + slope + " base " + base[0];
                            assertPremultiplied(label, layer);
                            float waterAlpha = smoothstep(0.035f, 0.18f, slope);
                            float[] expectedTarget = exactTarget(
                                    base, water, inkTarget, waterAlpha,
                                    clamp01(density * 0.02f));
                            float[] composed = RippleInkPortCompositor.sourceOver(
                                    layer, base[0], base[1], base[2]);
                            for (int channel = 0; channel < 3; ++channel) {
                                requireClose(label + " exact channel " + channel,
                                        expectedTarget[channel], composed[channel]);
                            }
                        }
                    }
                }
            }
        }

        float[] water = {0.7f, 0.6f, 0.5f};
        float[] navy = {
                RippleInkPortEngine.paletteComponent(5, 0),
                RippleInkPortEngine.paletteComponent(5, 1),
                RippleInkPortEngine.paletteComponent(5, 2)
        };
        requireRaw("navy raw red is zero", 0.0f, navy[0]);
        float[] navyZero = RippleInkPortCompositor.inkTarget(water, navy, 0.0f, 0.02f);
        float[] navyPositive = RippleInkPortCompositor.inkTarget(water, navy, 50.0f, 0.02f);
        for (int channel = 0; channel < 3; ++channel) {
            requireRaw("navy zero-density identity channel " + channel,
                    water[channel], navyZero[channel]);
        }
        requireRaw("navy saturated-density red is exact stock black",
                0.0f, navyPositive[0]);
    }

    private static void verifyRawPaletteStockFormula() {
        float[] water = {0.7f, 0.6f, 0.5f};
        for (int selector = 1; selector <= RippleInkPortEngine.paletteCount(); ++selector) {
            float[] palette = palette(selector);
            assertFiniteRgb("palette " + selector + " finite raw palette", palette);
            for (float density : new float[]{0.0f, 1.0f / 255.0f, 25.0f, 50.0f}) {
                float[] actual = RippleInkPortCompositor.inkTarget(
                        water, palette, density, 0.02f);
                for (int channel = 0; channel < 3; ++channel) {
                    if (palette[channel] > 0.0f) {
                        requireRaw("palette " + selector
                                        + " keeps raw stock target channel " + channel
                                        + " density " + density,
                                legacyPositiveComponentTarget(
                                        water[channel], palette[channel], density, 0.02f),
                                actual[channel]);
                    }
                }
            }
        }
    }

    private static void verifyZeroChannelDeltaAdaptation() {
        float[] water = {0.7f, 0.6f, 0.5f};
        float[] densities = {
                0.0f, Math.nextUp(0.0f), 1.0f / 255.0f, 2.0f / 255.0f,
                4.0f / 255.0f, 1.0f, 25.0f, 50.0f, 127.0f
        };

        for (int selector = 1; selector <= RippleInkPortEngine.paletteCount(); ++selector) {
            float[] palette = palette(selector);
            for (float density : densities) {
                float[] actual = RippleInkPortCompositor.inkTarget(
                        water, palette, density, 0.02f);
                assertFiniteRgb("adapted palette " + selector + " density " + density,
                        actual);
                for (int channel = 0; channel < 3; ++channel) {
                    if (palette[channel] > 0.0f) {
                        float expected = legacyPositiveComponentTarget(
                                water[channel], palette[channel], density, 0.02f);
                        requireRaw("nonzero palette math remains bit-equivalent selector "
                                        + selector + " channel " + channel + " density " + density,
                                expected, actual[channel]);
                    }
                }
            }
        }

        float[] navy = palette(5);
        float[] zero = RippleInkPortCompositor.inkTarget(water, navy, 0.0f, 0.02f);
        for (int channel = 0; channel < 3; ++channel) {
            requireRaw("slot5 zero-density identity channel " + channel,
                    water[channel], zero[channel]);
        }

        float previousRed = water[0];
        for (float density : densities) {
            float[] actual = RippleInkPortCompositor.inkTarget(
                    water, navy, density, 0.02f);
            float expectedRed = zeroChannelAdaptedTarget(
                    water[0], navy[0], density, 0.02f);
            requireRaw("slot5 shader/CPU formula parity density " + density,
                    expectedRed, actual[0]);
            require("slot5 red is continuous monotone density " + density,
                    actual[0] <= previousRed + EPSILON);
            previousRed = actual[0];

            float[] layer = RippleInkPortCompositor.transparentInkOverlay(
                    water[0], water[1], water[2],
                    water[0], water[1], water[2],
                    navy[0], navy[1], navy[2], density, 0.02f, 0.0f);
            assertPremultiplied("slot5 adapted layer density " + density, layer);
            float[] composed = RippleInkPortCompositor.sourceOver(
                    layer, water[0], water[1], water[2]);
            for (int channel = 0; channel < 3; ++channel) {
                requireClose("slot5 exact source-over density " + density
                                + " channel " + channel,
                        actual[channel], composed[channel]);
            }
        }

        float densityLsb = 1.0f / 255.0f;
        float[] oneLsbLayer = RippleInkPortCompositor.transparentInkOverlay(
                water[0], water[1], water[2],
                water[0], water[1], water[2],
                navy[0], navy[1], navy[2], densityLsb, 0.02f, 0.0f);
        float[] oneLsbInk = RippleInkPortCompositor.inkTarget(
                water, navy, densityLsb, 0.02f);
        float redNeed = (water[0] - oneLsbInk[0]) / water[0];
        require("slot5 one-density-LSB zero-red contribution uses q exactly",
                Math.abs(redNeed - 0.02f / 255.0f) <= EPSILON);
        require("slot5 one-density-LSB complete layer stays below one tenth percent",
                oneLsbLayer[3] > 0.0f && oneLsbLayer[3] < 0.001f);

        float[] density50 = RippleInkPortCompositor.inkTarget(
                water, navy, 50.0f, 0.02f);
        requireRaw("slot5 density50 reaches exact stock black red",
                0.0f, density50[0]);

        float[] density25 = RippleInkPortCompositor.inkTarget(
                water, navy, 25.0f, 0.02f);
        requireClose("slot5 half-coverage zero-red channel is continuous midpoint",
                0.5f * water[0], density25[0]);
    }

    private static void verifyExactReconstructionIdentities() {
        float[] base = {0.8f, 0.2f, 0.5f};
        float[] water = {0.23f, 0.61f, 0.84f};
        float[] palette = {
                RippleInkPortEngine.paletteComponent(8, 0),
                RippleInkPortEngine.paletteComponent(8, 1),
                RippleInkPortEngine.paletteComponent(8, 2)
        };

        float[] noSlopeNoDensity = RippleInkPortCompositor.transparentInkOverlay(
                base[0], base[1], base[2],
                water[0], water[1], water[2],
                palette[0], palette[1], palette[2],
                0.0f, 0.02f, 0.0f);
        for (int channel = 0; channel < 4; ++channel) {
            requireRaw("flat zero-density transparent channel " + channel,
                    0.0f, noSlopeNoDensity[channel]);
        }

        float[] waterOnly = RippleInkPortCompositor.transparentInkOverlay(
                base[0], base[1], base[2],
                water[0], water[1], water[2],
                palette[0], palette[1], palette[2],
                0.0f, 0.02f, 0.1075f);
        float[] waterComposite = exactTarget(base, water, water, 0.5f, 0.0f);
        float[] waterOnlyComposed = RippleInkPortCompositor.sourceOver(
                waterOnly, base[0], base[1], base[2]);
        for (int channel = 0; channel < 3; ++channel) {
            requireClose("zero density reconstructs accepted S3 Cw channel " + channel,
                    waterComposite[channel], waterOnlyComposed[channel]);
        }

        float density = 50.0f;
        float[] inkTarget = RippleInkPortCompositor.inkTarget(
                water, palette, density, 0.02f);
        float[] inkOnly = RippleInkPortCompositor.transparentInkOverlay(
                base[0], base[1], base[2],
                water[0], water[1], water[2],
                palette[0], palette[1], palette[2],
                density, 0.02f, 0.0f);
        float[] inkOnlyComposed = RippleInkPortCompositor.sourceOver(
                inkOnly, base[0], base[1], base[2]);
        float[] inkOnlyTarget = exactTarget(base, water, inkTarget, 0.0f, 1.0f);
        for (int channel = 0; channel < 3; ++channel) {
            requireClose("saturated ink coverage reconstructs stock I channel " + channel,
                    inkOnlyTarget[channel], inkOnlyComposed[channel]);
            requireClose("saturated ink target equals stock I channel " + channel,
                    inkTarget[channel], inkOnlyComposed[channel]);
        }

        float[] both = RippleInkPortCompositor.transparentInkOverlay(
                base[0], base[1], base[2],
                water[0], water[1], water[2],
                palette[0], palette[1], palette[2],
                density, 0.02f, 0.1075f);
        float[] bothComposed = RippleInkPortCompositor.sourceOver(
                both, base[0], base[1], base[2]);
        float[] bothTarget = exactTarget(base, water, inkTarget, 0.5f, 1.0f);
        for (int channel = 0; channel < 3; ++channel) {
            requireClose("water plus ink reconstructs T over independent B channel " + channel,
                    bothTarget[channel], bothComposed[channel]);
        }
    }

    private static void verifyDensityConditionedStockConvergence() {
        float[] base = {0.17f, 0.43f, 0.73f};
        float[] water = {0.31f, 0.57f, 0.81f};
        float[] slopes = {0.0f, 0.1075f, 0.5f};
        for (int selector = 1; selector <= RippleInkPortEngine.paletteCount(); ++selector) {
            float[] palette = {
                    RippleInkPortEngine.paletteComponent(selector, 0),
                    RippleInkPortEngine.paletteComponent(selector, 1),
                    RippleInkPortEngine.paletteComponent(selector, 2)
            };
            for (float density : new float[]{50.0f, 127.0f}) {
                float[] stockInk = RippleInkPortCompositor.inkTarget(
                        water, palette, density, 0.02f);
                for (float slope : slopes) {
                    float[] layer = RippleInkPortCompositor.transparentInkOverlay(
                            base[0], base[1], base[2],
                            water[0], water[1], water[2],
                            palette[0], palette[1], palette[2],
                            density, 0.02f, slope);
                    float[] composed = RippleInkPortCompositor.sourceOver(
                            layer, base[0], base[1], base[2]);
                    for (int channel = 0; channel < 3; ++channel) {
                        requireClose("palette " + selector + " saturated density "
                                        + density + " stock channel " + channel,
                                stockInk[channel], composed[channel]);
                    }
                }
            }
        }

        float[] palette = {
                RippleInkPortEngine.paletteComponent(8, 0),
                RippleInkPortEngine.paletteComponent(8, 1),
                RippleInkPortEngine.paletteComponent(8, 2)
        };
        float density = 25.0f;
        float inkCoverage = clamp01(density * 0.02f);
        float waterAlpha = 0.5f;
        float[] ink = RippleInkPortCompositor.inkTarget(
                water, palette, density, 0.02f);
        float[] target = exactTarget(base, water, ink, waterAlpha, inkCoverage);
        for (int channel = 0; channel < 3; ++channel) {
            float oldTarget = base[channel]
                    + waterAlpha * (water[channel] - base[channel])
                    + ink[channel] - water[channel];
            float expected = clamp01(oldTarget + inkCoverage * (ink[channel] - oldTarget));
            requireClose("partial coverage interpolates toward stock channel " + channel,
                    expected, target[channel]);
            float expectedGap = (1.0f - inkCoverage) * (1.0f - waterAlpha)
                    * (base[channel] - water[channel]);
            requireClose("partial coverage residual gap identity channel " + channel,
                    expectedGap, target[channel] - ink[channel]);
        }
    }

    private static void verifyShaderKeepsRecoveredPassMathAndLocalAlpha() {
        String advect = RippleInkPortGlesShaders.STOCK_ADVECT_DENSITY_FRAGMENT;
        require("velocity fixed-point decode retained",
                advect.contains("255.0 * buf.x + buf.y - 127.0"));
        require("88991 has no extra backtrace factor",
                advect.contains("back_step * TimeStep * u;")
                        && !advect.contains("* u * 0.25"));
        String ink = RippleInkPortGlesShaders.STOCK_ADD_INK_FRAGMENT;
        require("mode-two segment retained", ink.contains("if (mode == 2)"));
        require("stock radial injection retained",
                ink.contains("ImpulseDensity / (1.0 + d)"));
        String overlay = RippleInkPortGlesShaders.TRANSPARENT_INK_FRAGMENT;
        String vertex = RippleInkPortGlesShaders.MESH_VERTEX;
        require("nonrefracted varying uses saved pre-refraction projection",
                vertex.contains("varying vec2 vBGScreenCoord")
                        && vertex.contains("vec3 preRefractionD = d")
                        && vertex.contains("preRefractionD.x * r0 + v.x")
                        && vertex.contains("preRefractionD.y * r0 + v.y")
                        && vertex.contains("vBGScreenCoord = vec2(screenU0, screenV0)"));
        require("raw palette uses the recovered stock weight while exact-zero deltas use coverage",
                overlay.contains("float w = intensity * d")
                        && overlay.contains("float inkCoverage = clamp(w, 0.0, 1.0)")
                        && overlay.contains("if (w > 0.0)")
                        && overlay.contains("waterTarget * ink_color")
                        && overlay.contains("w * (vec3(1.5) - ink_color)")
                        && overlay.contains("if (ink_color.r == 0.0)")
                        && overlay.contains("mix(waterTarget.r, inkTarget.r, inkCoverage)")
                        && !overlay.contains("strength" + "Scale")
                        && !overlay.contains("w" + "Opt"));
        require("accepted S3 water alpha restored",
                overlay.contains("smoothstep(uOverlayMaskLow, uOverlayMaskHigh,")
                        && overlay.contains("waterA + (1.0 - waterA) * inkCoverage"));
        require("density-conditioned union reaches stock without tuning",
                overlay.contains("float inkCoverage = clamp(w, 0.0, 1.0)")
                        && overlay.contains("base + effectiveWaterA * (waterTarget - base)")
                        && !overlay.contains("max(waterA, inkCoverage)"));
        require("exact target is reconstructed over nonrefracted base",
                overlay.contains("+ (inkTarget - waterTarget)")
                        && overlay.contains("sourceOverNeed(base.r, target.r)")
                        && overlay.contains("target - (1.0 - alpha) * base"));
        require("source-over division guards exact black and white endpoints",
                overlay.contains("baseChannel > 0.0")
                        && overlay.contains("baseChannel < 1.0")
                        && !overlay.contains("vec3(0.001)"));
        require("refracted DELTA_ONLY cancellation removed",
                !overlay.contains("deltaMask")
                        && !overlay.contains("uOverlayOpacity")
                        && !overlay.contains("samsungResult - (1.0 - mask) * base")
                        && !overlay.contains("1.0 + w * ink_color"));
        require("incorrect water-relative two-layer output removed",
                !overlay.contains("inkPremul")
                        && !overlay.contains("outA")
                        && !overlay.contains("waterPremul"));
        String renderer = readRendererSource();
        require("renderer uploads finite raw palette components",
                renderer.contains("engine.getPaletteRed(),")
                        && renderer.contains("engine.getPaletteGreen(),")
                        && renderer.contains("engine.getPaletteBlue())")
                        && !renderer.contains("stockInkDenominator"));
        require("renderer binds accepted water thresholds",
                renderer.contains("uniform1f(\"uOverlayMaskLow\",")
                        && renderer.contains("uniform1f(\"uOverlayMaskHigh\","));
        require("renderer restores N3 integer touch phase and capped pressure",
                renderer.contains("int stockX = (int) x;")
                        && renderer.contains("int stockY = (int) y;")
                        && renderer.contains("float stockPressure = Math.max(0.0f, Math.min(1.0f, pressure));")
                        && renderer.contains(
                                "engine.handleFinger(action, stockX, stockY, stockPressure, eventTimeMs)")
                        && renderer.contains("void publishFinger(int action, float x, float y, float pressure)"));
    }

    private static void assertFiniteRgb(String label, float[] value) {
        require(label + " length", value != null && value.length >= 3);
        for (int channel = 0; channel < 3; ++channel) {
            require(label + " finite channel " + channel,
                    !Float.isNaN(value[channel]) && !Float.isInfinite(value[channel]));
            require(label + " range channel " + channel,
                    value[channel] >= 0.0f && value[channel] <= 1.0f);
        }
    }

    private static float[] exactTarget(
            float[] base, float[] water, float[] ink, float waterAlpha,
            float inkCoverage) {
        float[] target = new float[3];
        float effectiveWaterAlpha = waterAlpha
                + (1.0f - waterAlpha) * clamp01(inkCoverage);
        for (int channel = 0; channel < 3; ++channel) {
            float waterComposite = base[channel]
                    + effectiveWaterAlpha * (water[channel] - base[channel]);
            target[channel] = clamp01(waterComposite + ink[channel] - water[channel]);
        }
        return target;
    }

    private static float[] palette(int selector) {
        return new float[]{
                RippleInkPortEngine.paletteComponent(selector, 0),
                RippleInkPortEngine.paletteComponent(selector, 1),
                RippleInkPortEngine.paletteComponent(selector, 2)
        };
    }

    private static float legacyPositiveComponentTarget(
            float water, float component, float density, float intensity) {
        float weight = density * intensity;
        if (weight <= 0.0f) {
            return clamp01(water);
        }
        float denominator = component + weight * (1.5f - component);
        return clamp01(clamp01(water) * component / denominator);
    }

    private static float zeroChannelAdaptedTarget(
            float water, float rawComponent, float density, float intensity) {
        float weight = density * intensity;
        if (weight <= 0.0f) {
            return clamp01(water);
        }
        float component = rawComponent;
        float denominator = component + weight * (1.5f - component);
        float stock = clamp01(clamp01(water) * component / denominator);
        if (component == 0.0f) {
            return clamp01(clamp01(water)
                    + clamp01(weight) * (stock - clamp01(water)));
        }
        return stock;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float progress = clamp01((value - edge0) / (edge1 - edge0));
        return progress * progress * (3.0f - 2.0f * progress);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static void assertPremultiplied(String label, float[] value) {
        require(label + " length", value != null && value.length >= 4);
        require(label + " finite alpha",
                !Float.isNaN(value[3]) && !Float.isInfinite(value[3]));
        require(label + " alpha range", value[3] >= 0.0f && value[3] <= 1.0f);
        for (int channel = 0; channel < 3; ++channel) {
            require(label + " finite channel " + channel,
                    !Float.isNaN(value[channel]) && !Float.isInfinite(value[channel]));
            require(label + " premultiplied channel " + channel,
                    value[channel] >= 0.0f && value[channel] <= value[3]);
        }
    }

    private static String readRendererSource() {
        try {
            String configuredRoot = System.getProperty("lle.repoRoot");
            java.nio.file.Path root = configuredRoot == null
                    ? java.nio.file.Paths.get("").toAbsolutePath()
                    : java.nio.file.Paths.get(configuredRoot);
            java.nio.file.Path source = root.resolve(
                    "LLEUnified/src/com/codex/lle/RippleInkPortGlesRenderer.java");
            return new String(java.nio.file.Files.readAllBytes(source),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new AssertionError("cannot read Ripple Ink renderer source", exception);
        }
    }

    private static void requireRaw(String label, float expected, float actual) {
        if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void requireClose(String label, float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void require(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private RippleInkPortCompositorTest() {
    }
}
