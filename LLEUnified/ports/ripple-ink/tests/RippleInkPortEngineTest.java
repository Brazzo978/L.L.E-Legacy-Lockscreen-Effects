package com.codex.lle;

/** Host-JVM regression checks for the isolated Ripple Ink first slice. */
public final class RippleInkPortEngineTest {
    private static final float EPSILON = 0.0001f;

    public static void main(String[] args) {
        verifyExactEightPalettes();
        verifyVanillaWaterAdapterFailClosedContract();
        verifyVanillaWaterAdapterContract();
        verifyRecoveredMeshShape();
        verifyOrdinaryFingerUsesInkPath();
        verifyDensityAgesLocallyWhileNewInkStaysBright();
        verifyOrientationDensityShape();
        verifyPerInstanceFixedClockAndReset();
        verifyAdaptiveNominalCadences();
        verifyAdaptiveJitterAndStall();
        verifyAdaptiveCompositionMath();
        verifyAdaptiveUnitCreditUsesExactBranch();
        verifyHybridRendererClockCadences();
        verifyHybridRendererToggleResetsBothClocks();
        verifyLiveToggleResetsClockWithoutStateReset();
        verifyFractionalFluidBoundarySafety();
        verifyResetClearsSimulationAndInk();
        verifyRetouchResetRemovesReleasedWater();
        System.out.println("RippleInkPortEngineTest: PASS");
    }

    private static void verifyVanillaWaterAdapterFailClosedContract() {
        require("Android-like missing ABI fails closed",
                RippleInkVanillaWaterAdapter.androidAbiFailureFailsClosedForTest());
        require("host JVM keeps isolated test mirror",
                RippleInkVanillaWaterAdapter.hostAbiFailureUsesFallbackForTest());
    }

    private static void verifyVanillaWaterAdapterContract() {
        RippleInkVanillaWaterAdapter water = new RippleInkVanillaWaterAdapter(
                RippleInkPortEngine.DETAIL_WIDTH,
                RippleInkPortEngine.DETAIL_HEIGHT,
                RippleInkPortEngine.SURFACE_WIDTH,
                RippleInkPortEngine.SURFACE_HEIGHT);
        water.initWaters(
                RippleInkPortEngine.VERTEX_COUNT,
                RippleInkPortEngine.MESH_HEIGHT,
                RippleInkPortEngine.MESH_WIDTH,
                RippleInkPortEngine.SURFACE_HEIGHT,
                RippleInkPortEngine.SURFACE_WIDTH);

        // initWaters retains the NEON fractional-row shear and height-stride index order.
        requireClose("adapter first x", -25.0f, water.vertices()[0]);
        require("adapter fractional-row shear", water.vertices()[3] > -25.0f);
        int[] first = {0, 1, 101, 0, 101, 100};
        for (int i = 0; i < first.length; ++i) {
            require("adapter first index " + i, water.indices()[i] == first[i]);
        }

        // Low-edge ripple keeps both cells 0 and 1 untouched, while the exclusive upper bound
        // leaves cell 4 untouched for a cell-space origin at zero.
        water.ripple(50, 50, 104, 104, -25.0f, -25.0f, 1.0f);
        float[] velocity = water.velocityValuesForTest();
        require("ripple leaves fixed low border", velocity[1 + 2 * 104] == 0.0f
                && velocity[2 + 1 * 104] == 0.0f);
        require("ripple starts at cell two", velocity[2 + 2 * 104] > 0.0f);
        require("ripple upper bound is exclusive", velocity[4 + 2 * 104] == 0.0f
                && velocity[2 + 4 * 104] == 0.0f);

        float[] height = water.heightValuesForTest();
        height[21 * 104 + 3] = 1.0f;
        water.move(3, 21, 101, 83, 104, 104, 0.94f, 0.5f);
        require("portrait first active cell evolves", velocity[21 * 104 + 3] != 0.0f);
        require("portrait low x remains outside solver", velocity[21 * 104 + 2] == 0.0f);
        require("portrait low y remains outside solver", velocity[20 * 104 + 3] == 0.0f);

        RippleInkVanillaWaterAdapter landscape = new RippleInkVanillaWaterAdapter(104, 104, 100, 100);
        landscape.initWaters(10_000, 50, 50, 100, 100);
        landscape.heightValuesForTest()[3 * 104 + 21] = 1.0f;
        landscape.move(21, 3, 83, 101, 104, 104, 0.94f, 0.5f);
        require("landscape first active cell evolves",
                landscape.velocityValuesForTest()[3 * 104 + 21] != 0.0f);
        require("landscape low x remains outside solver",
                landscape.velocityValuesForTest()[3 * 104 + 20] == 0.0f);
        require("landscape low y remains outside solver",
                landscape.velocityValuesForTest()[2 * 104 + 21] == 0.0f);

        // The renderer consumes the transposed current/left/upper tuple, not a conventional
        // row-major RGB texture.
        java.util.Arrays.fill(height, 0.0f);
        height[2 * 104 + 2] = 11.0f;
        height[2 * 104 + 1] = 12.0f;
        height[1 * 104 + 2] = 13.0f;
        water.packGpuHeights(104, 100, 100);
        requireClose("tuple current", 11.0f, water.gpuHeights()[0]);
        requireClose("tuple left", 12.0f, water.gpuHeights()[1]);
        requireClose("tuple upper", 13.0f, water.gpuHeights()[2]);

        RippleInkVanillaWaterAdapter fixed = new RippleInkVanillaWaterAdapter(104, 104, 100, 100);
        RippleInkVanillaWaterAdapter adaptive = new RippleInkVanillaWaterAdapter(104, 104, 100, 100);
        fixed.initWaters(10_000, 50, 50, 100, 100);
        adaptive.initWaters(10_000, 50, 50, 100, 100);
        fixed.ripple(50, 50, 104, 104, 0.0f, 0.0f, 2.0f);
        adaptive.ripple(50, 50, 104, 104, 0.0f, 0.0f, 2.0f);
        fixed.move(3, 21, 101, 83, 104, 104, 0.94f, 0.5f);
        adaptive.moveAdaptive(3, 21, 101, 83, 104, 104, 0.94f, 0.5f, 1.0f);
        requireRawEqual("adapter q=1 velocity", fixed.velocityValuesForTest(),
                adaptive.velocityValuesForTest());
        requireRawEqual("adapter q=1 height", fixed.heightValuesForTest(),
                adaptive.heightValuesForTest());
    }

    private static void verifyExactEightPalettes() {
        int[][] expected = {
                {0x3f43c3b5, 0x3ef0f0e9, 0x3f0c8c82},
                {0x3f3ebebe, 0x3edcdcca, 0x3df0f0e9},
                {0x3e8c8c72, 0x3f028273, 0x3df0f0e9},
                {0x3d209fe8, 0x3eaaaa9f, 0x3f7afaf8},
                {0x00000000, 0x3df0f0e9, 0x3eb4b4af},
                {0x3eb4b4af, 0x3e70f0e9, 0x3f34b4af},
                {0x3e5cdcca, 0x3dc8c8ac, 0x3d209fe8},
                {0x3ea0a090, 0x3f20a090, 0x3f34b4af}
        };
        require("exactly eight enabled palettes", RippleInkPortEngine.paletteCount() == 8);
        require("selector zero remains disabled",
                !RippleInkPortEngine.isInkEnabledSelector(0));
        for (int selector = 1; selector <= expected.length; ++selector) {
            require("selector " + selector + " enabled",
                    RippleInkPortEngine.isInkEnabledSelector(selector));
            for (int component = 0; component < 3; ++component) {
                int actual = Float.floatToRawIntBits(
                        RippleInkPortEngine.paletteComponent(selector, component));
                require("palette " + selector + " component " + component,
                        actual == expected[selector - 1][component]);
            }
        }
    }

    private static void verifyRecoveredMeshShape() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        require("vertex float count",
                engine.vertices().length == RippleInkPortEngine.VERTEX_COUNT * 3);
        require("index count", engine.indices().length == RippleInkPortEngine.INDEX_COUNT);
        requireClose("first x", -25.0f, engine.vertices()[0]);
        requireClose("first y", 25.0f, engine.vertices()[1]);
        // The ARM32 vector loop retained vertex/surfaceWidth's fractional row.
        require("historical fractional-row shear retained", engine.vertices()[3] > -25.0f);
        short[] indices = engine.indices();
        int[] first = {0, 1, 101, 0, 101, 100};
        for (int i = 0; i < first.length; ++i) {
            require("first triangle index " + i, indices[i] == first[i]);
        }
    }

    private static void verifyOrdinaryFingerUsesInkPath() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        float x = (128.5f * 1080.0f) / RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH;
        float glY = (256.5f * 1920.0f) / RippleInkPortEngine.PORTRAIT_DENSITY_HEIGHT;
        float y = 1920.0f - glY;

        require("finger down accepted", engine.handleFinger(
                RippleInkPortEngine.ACTION_DOWN, x, y, 1.0f, 100L));
        require("ordinary finger entered ink path", engine.getInkPathEventCount() == 1);
        require("down ink action", engine.getLastInkAction() == RippleInkPortEngine.ACTION_DOWN);
        requireClose("stock forced pressure transform", 1.2f,
                engine.getLastAdjustedPressure());
        require("point deposit populated density", engine.densitySum() > 0.0f);
        require("down also injected water", engine.isWaterActive());

        require("finger move accepted", engine.handleFinger(
                RippleInkPortEngine.ACTION_MOVE, x + 120.0f, y + 15.0f, 1.0f, 120L));
        require("move used segment ink path", engine.getInkPathEventCount() == 2
                && engine.getLastInkAction() == RippleInkPortEngine.ACTION_MOVE);
        require("finger up accepted", engine.handleFinger(
                RippleInkPortEngine.ACTION_UP, x + 120.0f, y + 15.0f, 1.0f, 140L));
        require("up routed to ink path", engine.getInkPathEventCount() == 3
                && engine.getLastInkAction() == RippleInkPortEngine.ACTION_UP);
        require("up ended touch", !engine.isTouched());

        require("second finger down accepted", engine.handleFinger(
                RippleInkPortEngine.ACTION_DOWN, x, y, 1.0f, 160L));
        require("cancel accepted", engine.handleFinger(
                RippleInkPortEngine.ACTION_CANCEL, x, y, 1.0f, 170L));
        require("cancel routed to ink path",
                engine.getLastInkAction() == RippleInkPortEngine.ACTION_CANCEL);
    }

    private static void verifyOrientationDensityShape() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        require("portrait density width",
                engine.getDensityWidth() == RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH);
        require("portrait density height",
                engine.getDensityHeight() == RippleInkPortEngine.PORTRAIT_DENSITY_HEIGHT);
        engine.configureSurface(1920, 1080);
        require("landscape density width",
                engine.getDensityWidth() == RippleInkPortEngine.LANDSCAPE_DENSITY_WIDTH);
        require("landscape density height",
                engine.getDensityHeight() == RippleInkPortEngine.LANDSCAPE_DENSITY_HEIGHT);
    }

    private static void verifyDensityAgesLocallyWhileNewInkStaysBright() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        float firstX = (80.5f * 1080.0f) / RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH;
        float secondX = (180.5f * 1080.0f) / RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH;
        float glY = (256.5f * 1920.0f) / RippleInkPortEngine.PORTRAIT_DENSITY_HEIGHT;
        float y = 1920.0f - glY;
        int firstIndex = 256 * RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH + 80;
        int secondIndex = 256 * RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH + 180;

        engine.handleFinger(RippleInkPortEngine.ACTION_DOWN, firstX, y, 1.0f, 1L);
        long start = 2_000_000_000L;
        engine.advanceTo(start);
        require("one density age tick", engine.advanceTo(start + 16_666_667L) == 1);
        float oldAfterOneTick = engine.densityValues()[firstIndex];
        engine.handleFinger(RippleInkPortEngine.ACTION_DOWN, secondX, y, 1.0f, 2L);
        float newDeposit = engine.densityValues()[secondIndex];
        require("old deposit is locally damped", oldAfterOneTick > 0.0f && oldAfterOneTick < 127.0f);
        require("new deposit remains brighter than old deposit", newDeposit > oldAfterOneTick);
    }

    private static void verifyPerInstanceFixedClockAndReset() {
        RippleInkPortEngine first = new RippleInkPortEngine();
        RippleInkPortEngine second = new RippleInkPortEngine();
        first.configureSurface(1080, 1920);
        second.configureSurface(1080, 1920);
        long start = 7_000_000_000L;
        require("first clock primes", first.advanceTo(start) == 0);
        require("second clock remains unprimed", second.advanceTo(start + 5_000_000L) == 0);
        require("sub-tick retained", first.advanceTo(start + 16_666_666L) == 0);
        require("rational 60 Hz boundary", first.advanceTo(start + 16_666_667L) == 1);
        require("second instance has independent origin",
                second.advanceTo(start + 21_666_667L) == 1);
        require("stall recovery bounded to four",
                first.advanceTo(start + 2_000_000_000L) == 4);

        first.reset();
        require("reset removes old time origin",
                first.advanceTo(start + 3_000_000_000L) == 0);
        require("reset first fresh tick",
                first.advanceTo(start + 3_016_666_667L) == 1);
    }

    private static void verifyAdaptiveNominalCadences() {
        verifyAdaptiveCadence(60);
        verifyAdaptiveCadence(90);
        verifyAdaptiveCadence(120);
        verifyAdaptiveCadence(144);
    }

    private static void verifyAdaptiveCadence(int refreshHz) {
        RippleInkPortEngine.AdaptiveFrameClock clock =
                new RippleInkPortEngine.AdaptiveFrameClock();
        long start = 11_000_000_000L;
        requireClose("adaptive first " + refreshHz + " Hz frame", 0.0f,
                clock.advance(start));
        float credits = 0.0f;
        for (int frame = 1; frame <= refreshHz; ++frame) {
            long now = start + frame * 1_000_000_000L / refreshHz;
            float q = clock.advance(now);
            require("adaptive " + refreshHz + " Hz live q", q > 0.0f);
            credits += q;
        }
        requireClose(refreshHz + " Hz consumes sixty logical ticks", 60.0f, credits);
    }

    private static void verifyAdaptiveJitterAndStall() {
        RippleInkPortEngine.AdaptiveFrameClock clock =
                new RippleInkPortEngine.AdaptiveFrameClock();
        long now = 15_000_000_000L;
        requireClose("jitter clock primes", 0.0f, clock.advance(now));
        long[] deltas = {
                6_900_000L, 8_700_000L, 7_400_000L, 8_566_667L,
                9_100_000L, 8_200_000L, 7_800_000L, 8_466_667L
        };
        long total = 0L;
        float actual = 0.0f;
        for (long delta : deltas) {
            now += delta;
            total += delta;
            actual += clock.advance(now);
        }
        requireClose("jitter preserves elapsed logical credits",
                total * 60.0f / 1_000_000_000.0f, actual);
        now += 66_666_668L;
        requireClose("stall creates no replay debt", 0.0f, clock.advance(now));
        now += 8_333_333L;
        requireClose("fresh 120 Hz frame after stall", 0.5f, clock.advance(now));
        requireClose("duplicate timestamp is no-op", 0.0f, clock.advance(now));
    }

    private static void verifyAdaptiveCompositionMath() {
        float half = RippleInkPortEngine.scaleDissipation(0.94f, 0.5f);
        requireClose("two half damping steps compose to one stock tick",
                0.94f, half * half);
        float quarter = RippleInkPortEngine.scaleDissipation(0.9f, 0.25f);
        float threeQuarter = RippleInkPortEngine.scaleDissipation(0.9f, 0.75f);
        requireClose("fractional velocity damping composes",
                0.9f, quarter * threeQuarter);
        requireClose("unit density dissipation stays unit", 1.0f,
                RippleInkPortEngine.scaleDissipation(1.0f, 0.37f));
        requireClose("one nominal tick snaps to exact q=1", 1.0f,
                RippleInkPortEngine.adaptiveCreditsForElapsedNanos(16_666_667L));
        requireClose("120 Hz is a half logical tick", 0.5f,
                RippleInkPortEngine.adaptiveCreditsForElapsedNanos(8_333_333L));
    }

    private static void verifyAdaptiveUnitCreditUsesExactBranch() {
        RippleInkPortEngine fixed = new RippleInkPortEngine();
        RippleInkPortEngine adaptive = new RippleInkPortEngine();
        fixed.configureSurface(1080, 1920);
        adaptive.configureSurface(1080, 1920);
        adaptive.setHighFrameRateEnabled(true);
        require("HFR default remains off", !fixed.isHighFrameRateEnabled());
        require("HFR opt-in enabled", adaptive.isHighFrameRateEnabled());

        float x = (128.5f * 1080.0f) / RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH;
        float glY = (256.5f * 1920.0f) / RippleInkPortEngine.PORTRAIT_DENSITY_HEIGHT;
        float y = 1920.0f - glY;
        fixed.handleFinger(RippleInkPortEngine.ACTION_DOWN, x, y, 1.0f, 100L);
        adaptive.handleFinger(RippleInkPortEngine.ACTION_DOWN, x, y, 1.0f, 100L);
        fixed.handleFinger(RippleInkPortEngine.ACTION_MOVE, x + 42.0f, y + 8.0f, 1.0f, 110L);
        adaptive.handleFinger(
                RippleInkPortEngine.ACTION_MOVE, x + 42.0f, y + 8.0f, 1.0f, 110L);

        long start = 20_000_000_000L;
        require("fixed q=1 trace primes", fixed.advanceTo(start) == 0);
        requireClose("adaptive q=1 trace primes", 0.0f, adaptive.advanceFrame(start));
        require("fixed performs one exact tick", fixed.advanceTo(start + 16_666_667L) == 1);
        requireClose("adaptive selects exact q=1", 1.0f,
                adaptive.advanceFrame(start + 16_666_667L));
        requireRawEqual("q=1 water output", fixed.gpuHeights(), adaptive.gpuHeights());
        requireRawEqual("q=1 density output", fixed.densityValues(), adaptive.densityValues());
        requireRawEqual("q=1 velocity X", fixed.fluidVelocityXValues(),
                adaptive.fluidVelocityXValues());
        requireRawEqual("q=1 velocity Y", fixed.fluidVelocityYValues(),
                adaptive.fluidVelocityYValues());
    }

    private static void verifyLiveToggleResetsClockWithoutStateReset() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        long start = 24_000_000_000L;
        engine.setHighFrameRateEnabled(true);
        requireClose("adaptive toggle primes a fresh clock", 0.0f, engine.advanceFrame(start));
        requireClose("adaptive half tick", 0.5f, engine.advanceFrame(start + 8_333_333L));
        double creditsBeforeToggle = engine.getSimulationCreditCount();
        engine.setHighFrameRateEnabled(false);
        require("toggle off keeps simulation credits",
                engine.getSimulationCreditCount() == creditsBeforeToggle);
        requireClose("fixed mode has fresh origin after toggle", 0.0f,
                engine.advanceFrame(start + 10_000_000L));
        engine.setHighFrameRateEnabled(true);
        requireClose("second toggle also drops old debt", 0.0f,
                engine.advanceFrame(start + 12_000_000L));
    }

    private static void verifyHybridRendererClockCadences() {
        verifyHybridRendererCadence(120);
        verifyHybridRendererCadence(90);
        verifyHybridRendererCadence(144);

        RippleInkPortEngine fixed = new RippleInkPortEngine();
        fixed.configureSurface(1080, 1920);
        long start = 26_000_000_000L;
        RippleInkPortEngine.RendererFrameAdvance prime = fixed.advanceRendererFrame(start);
        require("fixed renderer primes without work", prime.waterCredits == 0.0f
                && prime.inkTicks == 0);
        int waterTicks = 0;
        int inkTicks = 0;
        for (int frame = 1; frame <= 120; ++frame) {
            RippleInkPortEngine.RendererFrameAdvance advance = fixed.advanceRendererFrame(
                    start + frame * 1_000_000_000L / 120L);
            waterTicks += (int) advance.waterCredits;
            inkTicks += advance.inkTicks;
            require("fixed renderer returns identical water/ink credits",
                    advance.waterCredits == advance.inkTicks);
        }
        require("fixed renderer remains exactly 60Hz at 120 display", waterTicks == 60
                && inkTicks == 60);
    }

    private static void verifyHybridRendererCadence(int refreshHz) {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        engine.setHighFrameRateEnabled(true);
        long start = 25_000_000_000L;
        RippleInkPortEngine.RendererFrameAdvance prime = engine.advanceRendererFrame(start);
        require("hybrid " + refreshHz + " prime", prime.waterCredits == 0.0f
                && prime.inkTicks == 0);
        int waterFrames = 0;
        int inkTicks = 0;
        float waterCredits = 0.0f;
        for (int frame = 1; frame <= refreshHz; ++frame) {
            RippleInkPortEngine.RendererFrameAdvance advance = engine.advanceRendererFrame(
                    start + frame * 1_000_000_000L / refreshHz);
            require("hybrid water has a per-vsync adaptive credit " + refreshHz,
                    advance.waterCredits > 0.0f);
            ++waterFrames;
            waterCredits += advance.waterCredits;
            inkTicks += advance.inkTicks;
        }
        require("hybrid water updates every display frame " + refreshHz,
                waterFrames == refreshHz);
        requireClose("hybrid water consumes 60 logical ticks " + refreshHz,
                60.0f, waterCredits);
        require("hybrid ink consumes exact rational 60 ticks " + refreshHz, inkTicks == 60);
    }

    private static void verifyHybridRendererToggleResetsBothClocks() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        long start = 27_000_000_000L;
        engine.setHighFrameRateEnabled(true);
        engine.advanceRendererFrame(start);
        RippleInkPortEngine.RendererFrameAdvance half = engine.advanceRendererFrame(
                start + 8_333_333L);
        require("hybrid HFR gets fractional water only", half.waterCredits > 0.0f
                && half.waterCredits < 1.0f && half.inkTicks == 0);
        engine.setHighFrameRateEnabled(false);
        RippleInkPortEngine.RendererFrameAdvance offPrime = engine.advanceRendererFrame(
                start + 10_000_000L);
        require("hybrid toggle off discards both clock debt", offPrime.waterCredits == 0.0f
                && offPrime.inkTicks == 0);
        engine.setHighFrameRateEnabled(true);
        RippleInkPortEngine.RendererFrameAdvance onPrime = engine.advanceRendererFrame(
                start + 12_000_000L);
        require("hybrid toggle on discards both clock debt", onPrime.waterCredits == 0.0f
                && onPrime.inkTicks == 0);
    }

    private static void verifyFractionalFluidBoundarySafety() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        engine.setHighFrameRateEnabled(true);
        engine.handleFinger(RippleInkPortEngine.ACTION_DOWN, 0.5f, 0.5f, 1.0f, 1L);
        engine.handleFinger(RippleInkPortEngine.ACTION_MOVE, 140.0f, 2.0f, 1.0f, 2L);
        long start = 30_000_000_000L;
        engine.advanceFrame(start);
        engine.advanceFrame(start + 6_944_444L); // 144 Hz fractional q.
        require("fractional integration consumed credits",
                engine.getSimulationCreditCount() > 0.0d);
        for (float value : engine.densityValues()) {
            require("clamped density remains finite", !Float.isNaN(value)
                    && !Float.isInfinite(value) && value >= 0.0f && value <= 127.0f);
        }
        for (float value : engine.fluidVelocityXValues()) {
            require("velocity X remains encoded-range safe",
                    !Float.isNaN(value) && value >= -127.0f && value <= 127.0f);
        }
        for (float value : engine.fluidVelocityYValues()) {
            require("velocity Y remains encoded-range safe",
                    !Float.isNaN(value) && value >= -127.0f && value <= 127.0f);
        }
    }

    private static void verifyResetClearsSimulationAndInk() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        float x = (10.5f * 1080.0f) / RippleInkPortEngine.PORTRAIT_DENSITY_WIDTH;
        float y = 1920.0f
                - (10.5f * 1920.0f) / RippleInkPortEngine.PORTRAIT_DENSITY_HEIGHT;
        engine.handleFinger(RippleInkPortEngine.ACTION_DOWN, x, y, 1.0f, 1L);
        engine.advanceTo(1_000_000_000L);
        engine.advanceTo(1_016_666_667L);
        require("pre-reset simulation advanced", engine.getSimulationStepCount() == 1L);
        require("pre-reset density exists", engine.densitySum() > 0.0f);
        engine.reset();
        require("reset clears density", engine.densitySum() == 0.0f);
        require("reset clears water activity", !engine.isWaterActive());
        require("reset clears touch", !engine.isTouched());
        require("reset clears clock-visible step count", engine.getSimulationStepCount() == 0L);
        require("reset clears ink action state", engine.getInkPathEventCount() == 0
                && engine.getLastInkAction() == -1);
    }

    private static void verifyRetouchResetRemovesReleasedWater() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        engine.handleFinger(RippleInkPortEngine.ACTION_DOWN, 540.0f, 960.0f, 1.0f, 1L);
        engine.handleFinger(RippleInkPortEngine.ACTION_UP, 540.0f, 960.0f, 1.0f, 2L);
        require("released gesture leaves water eligible for tail", engine.isWaterActive());
        engine.reset();
        require("retouch reset removes released water before opacity returns", !engine.isWaterActive());
        for (float height : engine.gpuHeights()) {
            require("retouch reset removes old water mesh height", height == 0.0f);
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

    private static void requireRawEqual(String label, float[] expected, float[] actual) {
        require(label + " length", expected.length == actual.length);
        for (int i = 0; i < expected.length; ++i) {
            int expectedBits = Float.floatToRawIntBits(expected[i]);
            int actualBits = Float.floatToRawIntBits(actual[i]);
            if (expectedBits != actualBits) {
                throw new AssertionError(label + " differs at " + i
                        + ": expected=0x" + Integer.toHexString(expectedBits)
                        + " actual=0x" + Integer.toHexString(actualBits));
            }
        }
    }

    private RippleInkPortEngineTest() {
    }
}
