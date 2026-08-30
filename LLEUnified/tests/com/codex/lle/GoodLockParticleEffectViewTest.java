package com.codex.lle;

import java.util.ArrayList;

/** Deterministic host seams for the Good Lock 24.0.15 particle port. */
public final class GoodLockParticleEffectViewTest {
    private static final float EPSILON = 0.00001f;
    private static final long BASE_NANOS = 1_000_000_000L;

    private GoodLockParticleEffectViewTest() {
    }

    public static void main(String[] args) {
        testDownCreatesExactlyFiveWallpaperSampledParticles();
        testMirroredInterpolationAndWidthThresholdOnBothAxes();
        testStockParticleRangesAndColourAdjustment();
        testBouncingPerDrawAccelerationAndFloorBounce();
        testElapsedFrameUnitsAndPresentationCap();
        testHighFrameClockAcrossRefreshRatesAndStalls();
        testHighFrameSpeedMultiplierClamp();
        testHighFrameBouncingCompositionAndExactQOne();
        testLongDragInjectionCountIsPresentationIndependent();
    }

    private static void testDownCreatesExactlyFiveWallpaperSampledParticles() {
        RecordingSampler sampler = new RecordingSampler(400, 800, 0xff204060);
        GoodLockParticleEffectView.Simulation simulation =
                new GoodLockParticleEffectView.Simulation(
                        GoodLockParticleEffectView.Variant.POPPING, 400, 800,
                        new FixedRandomSource());
        simulation.touch(GoodLockParticleEffectView.Simulation.ACTION_DOWN, 100, 200, sampler);
        assertInt("down particle count", 5, simulation.particleCount());
        assertInt("one wallpaper sample per particle", 5, sampler.samples.size());
        for (int index = 0; index < sampler.samples.size(); index++) {
            assertInt("down x " + index, 100, sampler.samples.get(index)[0]);
            assertInt("down y " + index, 200, sampler.samples.get(index)[1]);
        }
    }

    private static void testMirroredInterpolationAndWidthThresholdOnBothAxes() {
        RecordingSampler sampler = new RecordingSampler(400, 800, 0xff112233);
        GoodLockParticleEffectView.Simulation simulation =
                new GoodLockParticleEffectView.Simulation(
                        GoodLockParticleEffectView.Variant.RECTANGLE, 400, 800,
                        new FixedRandomSource());
        simulation.touch(GoodLockParticleEffectView.Simulation.ACTION_DOWN, 100, 100, sampler);
        sampler.samples.clear();

        // 15 px vertically is below height/40 (20), but exceeds stock width/40 (10), so it emits.
        simulation.touch(GoodLockParticleEffectView.Simulation.ACTION_MOVE, 100, 115, sampler);
        assertInt("width threshold applies to y", 1, sampler.samples.size());
        assertInt("mirrored y interpolation", 85, sampler.samples.get(0)[1]);

        sampler.samples.clear();
        // old=(100,115), current=(90,115): stock's old-current sign samples x=110, not 90.
        simulation.touch(GoodLockParticleEffectView.Simulation.ACTION_MOVE, 90, 115, sampler);
        assertInt("mirrored x interpolation samples", 1, sampler.samples.size());
        assertInt("mirrored x interpolation", 110, sampler.samples.get(0)[0]);
        assertNear("minimum distance", 10.0f, simulation.minimumCreateDistance());
    }

    private static void testStockParticleRangesAndColourAdjustment() {
        GoodLockParticleEffectView.Particle popping = GoodLockParticleEffectView.Particle.create(
                GoodLockParticleEffectView.Variant.POPPING, 0xfffefefe, 0, 0,
                new FixedRandomSource(new int[] {39}, new float[] {0.0f, 1.0f, 1.0f}));
        assertInt("popping delta is +19 and clamps", 0xffffffff, popping.color);
        assertNear("popping x range", -1.5f, popping.movementX);
        assertNear("popping y range", 10.0f, popping.movementY);
        assertNear("popping radius range", 40.0f, popping.size);

        GoodLockParticleEffectView.Particle rectangle = GoodLockParticleEffectView.Particle.create(
                GoodLockParticleEffectView.Variant.RECTANGLE, 0xff203040, 0, 0,
                new FixedRandomSource(new int[] {20}, new float[] {0.0f, 1.0f, 0.0f, 0.0f}));
        assertNear("rectangle x range", -5.0f, rectangle.movementX);
        assertNear("rectangle y range", 5.0f, rectangle.movementY);
        assertNear("rectangle size range", 10.0f, rectangle.size);
        assertNear("rectangle rotation preserves negative minimum", -6.0f, rectangle.rotation);
    }

    private static void testBouncingPerDrawAccelerationAndFloorBounce() {
        GoodLockParticleEffectView.Particle decelerating = GoodLockParticleEffectView.Particle.create(
                GoodLockParticleEffectView.Variant.BOUNCING, 0xff102030, 0, 50,
                new FixedRandomSource(new int[] {20, 0},
                        new float[] {0.5f, 0.0f, 0.0f, 0.0f}));
        decelerating.advance(3.0f, 400, 100,
                new FixedRandomSource(new int[0], new float[] {0.5f}));
        assertNear("bouncing x uses frame step", 9.0f, decelerating.x);
        assertNear("bouncing deceleration is once per draw", 2.9f, decelerating.movementX);

        GoodLockParticleEffectView.Particle floorBounce = GoodLockParticleEffectView.Particle.create(
                GoodLockParticleEffectView.Variant.BOUNCING, 0xff102030, 0, 95,
                new FixedRandomSource(new int[] {20, 1},
                        new float[] {0.5f, 0.0f, 0.0f}));
        floorBounce.advance(1.0f, 400, 100,
                new FixedRandomSource(new int[0], new float[] {0.5f}));
        assertNear("floor position", 90.0f, floorBounce.y);
        assertNear("floor rebound range", 14.5f, floorBounce.accelerationY);
    }

    private static void testElapsedFrameUnitsAndPresentationCap() {
        assertNear("10 ms stock unit", 1.0f,
                GoodLockParticleEffectView.frameStepForElapsedMs(10L));
        assertNear("real elapsed frame unit", 3.7f,
                GoodLockParticleEffectView.frameStepForElapsedMs(37L));
        if (GoodLockParticleEffectView.maximumPresentationIntervalMs() < 17L) {
            throw new AssertionError("presentation can exceed 60 Hz");
        }
    }

    private static void testHighFrameClockAcrossRefreshRatesAndStalls() {
        for (int refreshHz : new int[] {60, 90, 120, 144}) {
            GoodLockParticleEffectView.HighFrameClock clock =
                    new GoodLockParticleEffectView.HighFrameClock();
            assertNear("HFR first " + refreshHz, 0.0f, clock.consume(BASE_NANOS));
            float logicalFrames = 0.0f;
            for (int frame = 1; frame <= refreshHz; frame++) {
                long frameNanos = BASE_NANOS
                        + Math.round(frame * 1_000_000_000.0d / refreshHz);
                logicalFrames += clock.consume(frameNanos);
            }
            assertNearRelaxed("one 60 Hz logical second " + refreshHz, 60.0f, logicalFrames);
        }

        GoodLockParticleEffectView.HighFrameClock clock =
                new GoodLockParticleEffectView.HighFrameClock();
        clock.consume(BASE_NANOS);
        assertNearRelaxed("jitter q", 0.72f, clock.consume(BASE_NANOS + 12_000_000L));
        assertNear("stall discarded", 0.0f,
                clock.consume(BASE_NANOS + 12_000_000L + 70_000_000L));
        assertNearRelaxed("fresh post-stall q", 1.0f,
                clock.consume(BASE_NANOS + 12_000_000L + 70_000_000L + 16_666_667L));
    }

    private static void testHighFrameSpeedMultiplierClamp() {
        assertNear("speed lower clamp", 1.0f,
                GoodLockParticleEffectView.sanitizeSpeedMultiplier(0.2f));
        assertNear("speed default", 1.0f,
                GoodLockParticleEffectView.sanitizeSpeedMultiplier(Float.NaN));
        assertNear("speed 1.5", 1.5f,
                GoodLockParticleEffectView.sanitizeSpeedMultiplier(1.5f));
        assertNear("speed upper clamp", 2.0f,
                GoodLockParticleEffectView.sanitizeSpeedMultiplier(8.0f));
    }

    private static void testHighFrameBouncingCompositionAndExactQOne() {
        GoodLockParticleEffectView.Particle oneFrame = bouncingParticle();
        GoodLockParticleEffectView.Particle twoHalfFrames = bouncingParticle();
        FixedRandomSource noFloorRandom = new FixedRandomSource();
        oneFrame.advanceHighFrame(1.0f, 400, 800, noFloorRandom);
        twoHalfFrames.advanceHighFrame(0.5f, 400, 800, noFloorRandom);
        twoHalfFrames.advanceHighFrame(0.5f, 400, 800, noFloorRandom);
        assertNear("HFR q=1 exact x", oneFrame.x, twoHalfFrames.x);
        assertNear("HFR q=1 exact y", oneFrame.y, twoHalfFrames.y);
        assertNear("HFR composed movement x", oneFrame.movementX, twoHalfFrames.movementX);
        assertNear("HFR composed acceleration y", oneFrame.accelerationY,
                twoHalfFrames.accelerationY);
    }

    private static GoodLockParticleEffectView.Particle bouncingParticle() {
        return GoodLockParticleEffectView.Particle.create(
                GoodLockParticleEffectView.Variant.BOUNCING, 0xff102030, 120, 400,
                new FixedRandomSource(new int[] {20, 0},
                        new float[] {0.5f, 0.25f, 0.0f, 0.0f}));
    }

    private static void testLongDragInjectionCountIsPresentationIndependent() {
        RecordingSampler sampler = new RecordingSampler(400, 800, 0xff8090a0);
        GoodLockParticleEffectView.Simulation simulation =
                new GoodLockParticleEffectView.Simulation(
                        GoodLockParticleEffectView.Variant.POPPING, 400, 800,
                        new FixedRandomSource());
        simulation.touch(GoodLockParticleEffectView.Simulation.ACTION_DOWN, 200, 400, sampler);
        for (int index = 1; index <= 20; index++) {
            simulation.touch(GoodLockParticleEffectView.Simulation.ACTION_MOVE,
                    200, 400 - index * 15, sampler);
        }
        assertInt("long drag particle count", 25, simulation.particleCount());
        assertInt("long drag exact samples", 25, sampler.samples.size());
    }

    private static final class RecordingSampler implements GoodLockParticleEffectView.PixelSampler {
        private final int width;
        private final int height;
        private final int color;
        final ArrayList<int[]> samples = new ArrayList<int[]>();

        RecordingSampler(int width, int height, int color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int getPixel(int x, int y) {
            samples.add(new int[] {x, y});
            return color;
        }
    }

    private static final class FixedRandomSource implements GoodLockParticleEffectView.RandomSource {
        private final int[] ints;
        private final float[] floats;
        private int intIndex;
        private int floatIndex;

        FixedRandomSource() {
            this(new int[0], new float[0]);
        }

        FixedRandomSource(int[] ints, float[] floats) {
            this.ints = ints;
            this.floats = floats;
        }

        @Override
        public float nextFloat() {
            return floatIndex < floats.length ? floats[floatIndex++] : 0.0f;
        }

        @Override
        public int nextInt(int bound) {
            return intIndex < ints.length ? ints[intIndex++] : 0;
        }
    }

    private static void assertInt(String name, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNear(String name, float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNearRelaxed(String name, float expected, float actual) {
        if (Math.abs(expected - actual) > 0.0005f) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }
}
