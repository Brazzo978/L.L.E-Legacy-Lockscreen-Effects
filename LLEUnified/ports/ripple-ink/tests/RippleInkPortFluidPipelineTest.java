package com.codex.lle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Host contracts for the app-owned 60 Hz finger source and the recovered GPU worker. */
public final class RippleInkPortFluidPipelineTest {
    private static final float EPSILON = 0.001f;

    public static void main(String[] args) {
        verifyHostKeepsScalarWorkerAsExplicitTestSeam();
        verifyAuthoritativeDimensionsAndWorkerSafety();
        verifyLatestMailboxConsumesCurrentSampleOnly();
        verifyHeldMoveModeTicksWithoutCallbacks();
        verifyPromotionRetainsPriorBackstepForOneUpdate();
        verifyRawCallbackClassifierAndPressStepPersistence();
        verifyReleaseKeepsPersistentWorkerProfile();
        verifyUiCallbackIsNotSerializedBehindDrawPasses();
        verifyConcurrentDownWinsOverPriorTickCommit();
        verifyStationaryPressDoesNotRestart();
        verifyCancelUsesNativeUpAction();
        verifyShortDragUpRetainsStateOneButLongDragReleases();
        verifyHybridHfrUsesOnlyFixedInkTicks();
        verifyDirectModeTwoCapsuleRemainsExact();
        verifyDirectVelocityCapsuleBeforeSelfAdvection();
        verifyVelocityNormalizedBilerpNotDampingOnly();
        verifyModeTwoLocalOverrideSignAndStrictRadius();
        verifyCenteredDivergenceUsesGridAnchor();
        verifySeededWorkerJitterIsBoundedDeterministicAndTwoDraw();
        verifyWorkerMarginSkipsProjection();
        verifyThousandWorkerTicksStayFinite();
        System.out.println("RippleInkPortFluidPipelineTest: PASS");
    }

    /** The host harness must never fake Android JNI availability or silently exercise it. */
    private static void verifyHostKeepsScalarWorkerAsExplicitTestSeam() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(1080, 1920);
        require("plain JVM retains only the scalar numerical seam",
                pipeline.isNativeWorkerReadyForProduction());
        require("host does not initialize Android JNI", "not required on host JVM".equals(
                pipeline.nativeWorkerFailureDetail()));
        pipeline.releaseNativeWorker();
        require("host release remains a no-op", pipeline.isNativeWorkerReadyForProduction());
    }

    private static void verifyAuthoritativeDimensionsAndWorkerSafety() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        require("portrait density", pipeline.densityWidth() == 256 && pipeline.densityHeight() == 512);
        require("screen/12 worker", pipeline.fluidWidth() == 90 && pipeline.fluidHeight() == 160);
        require("ten Jacobi iterations", RippleInkPortFluidPipeline.JACOBI_ITERATIONS == 10);
        RecordingSink sink = new RecordingSink();
        pipeline.execute(1.0f, sink);
        require("idle remains upload then advect", sink.events.size() == 2
                && "upload".equals(sink.events.get(0)) && sink.events.get(1).startsWith("advect:"));
        require("idle worker is finite", pipeline.hasFiniteRepresentableVelocity());
    }

    private static void verifyLatestMailboxConsumesCurrentSampleOnly() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        // Both callbacks precede the first GL tick. The state transition must still occur now,
        // rather than losing DOWN when a latest-value mailbox overwrites it with MOVE.
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 500.9f, 900.8f, 2.0f, 0L);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 520.7f, 900.2f, 2.0f, 1L);
        pipeline.executeFixedTick(sink);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 550.4f, 900.1f, 2.0f, 2L);
        pipeline.executeFixedTick(sink);
        require("pre-frame DOWN/MOVE retains state transition and promotion's held mode-0",
                sink.inks.size() == 2 && sink.inks.get(0).mode == 0
                        && sink.inks.get(1).mode == 2);
        require("N3 Java boundary truncates coordinates",
                Math.abs(sink.inks.get(sink.inks.size() - 1).currentX - 550.0f) < EPSILON);
    }

    private static void verifyStationaryPressDoesNotRestart() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 540.0f, 960.0f, 1.0f, 0L);
        for (int tick = 0; tick < 20; ++tick) {
            pipeline.executeFixedTick(sink);
        }
        require("state-1 emits only its ten native deposits", sink.inks.size() == 10);
        require("state-1 does not restart after n12", pipeline.densityUpperBound() < 127.0f);
    }

    private static void verifyHeldMoveModeTicksWithoutCallbacks() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 400.0f, 900.0f, 1.0f);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 420.0f, 900.0f, 1.0f);
        pipeline.executeFixedTick(sink); // promotion: held mode 0, radial source
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 460.0f, 900.0f, 1.0f);
        pipeline.executeFixedTick(sink); // raw callback latches mode 2
        pipeline.executeFixedTick(sink); // mode 2 is held, but zero capsule has no density write
        require("state-2 runs every draw but skips only its zero-length capsule",
                sink.inks.size() == 2 && sink.inks.get(0).mode == 0
                        && sink.inks.get(1).mode == 2);
        require("Update consumes the prior persistent mode-2 profile",
                sink.advects.get(sink.advects.size() - 1).dragMode == 2);
    }

    private static void verifyPromotionRetainsPriorBackstepForOneUpdate() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 400.0f, 900.0f, 1.0f);
        pipeline.executeFixedTick(sink); // n0 -> next backstep 0.0
        pipeline.executeFixedTick(sink); // n1 -> next backstep 0.1
        pipeline.executeFixedTick(sink); // n2 -> next backstep 0.2
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 420.0f, 900.0f, 1.0f);
        pipeline.executeFixedTick(sink); // promotion's first state-2 Update consumes .2
        pipeline.executeFixedTick(sink); // state-2 recipe has left 1.0
        require("promotion first state-2 update retains preceding state-1 backstep",
                Math.abs(sink.advects.get(3).backwardStep - 0.2f) < EPSILON);
        require("state-2 recipe restores its next backstep to one",
                Math.abs(sink.advects.get(4).backwardStep - 1.0f) < EPSILON);
    }

    private static void verifyRawCallbackClassifierAndPressStepPersistence() {
        RippleInkPortFluidPipeline classifier = configuredPipeline();
        RecordingSink classifierSink = new RecordingSink();
        classifier.onTouch(RippleInkPortEngine.ACTION_DOWN, 500.0f, 900.0f, 1.0f);
        classifier.onTouch(RippleInkPortEngine.ACTION_MOVE, 530.0f, 900.0f, 1.0f);
        classifier.executeFixedTick(classifierSink);
        classifier.onTouch(RippleInkPortEngine.ACTION_MOVE, 535.0f, 900.0f, 1.0f);
        classifier.onTouch(RippleInkPortEngine.ACTION_MOVE, 537.0f, 900.0f, 1.0f);
        classifier.executeFixedTick(classifierSink);
        require("mode classifier uses latest raw-callback delta, not draw-committed distance",
                classifierSink.inks.get(classifierSink.inks.size() - 1).mode == 0);

        RippleInkPortFluidPipeline resumed = configuredPipeline();
        RecordingSink resumedSink = new RecordingSink();
        resumed.onTouch(RippleInkPortEngine.ACTION_DOWN, 500.0f, 900.0f, 1.0f);
        for (int n = 0; n < 4; ++n) resumed.executeFixedTick(resumedSink);
        resumed.onTouch(RippleInkPortEngine.ACTION_MOVE, 530.0f, 900.0f, 1.0f);
        resumed.executeFixedTick(resumedSink);
        resumed.onTouch(RippleInkPortEngine.ACTION_UP, 530.0f, 900.0f, 1.0f);
        resumed.executeFixedTick(resumedSink);
        RippleInkPortFluidPipeline.AddInkPass next = resumedSink.inks.get(resumedSink.inks.size() - 1);
        require("state2 draws leave press step unchanged for short-UP resume",
                next.mode == -1 && Math.abs(next.radius - 32.0f) < EPSILON);
    }

    private static void verifyReleaseKeepsPersistentWorkerProfile() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 400.0f, 900.0f, 1.0f);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 430.0f, 900.0f, 1.0f);
        pipeline.executeFixedTick(sink);
        for (int tick = 0; tick < 10; ++tick) {
            pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE,
                    470.0f + 40.0f * tick, 900.0f, 1.0f);
            pipeline.executeFixedTick(sink);
        }
        int inkBeforeUp = sink.inks.size();
        pipeline.onTouch(RippleInkPortEngine.ACTION_UP, 830.0f, 900.0f, 1.0f);
        pipeline.executeFixedTick(sink);
        RippleInkPortFluidPipeline.AdvectPass release =
                sink.advects.get(sink.advects.size() - 1);
        pipeline.executeFixedTick(sink);
        RippleInkPortFluidPipeline.AdvectPass idle =
                sink.advects.get(sink.advects.size() - 1);
        require("release never injects", sink.inks.size() == inkBeforeUp);
        require("release and following state0 tick retain prior mode2 worker profile",
                release.dragMode == 2 && idle.dragMode == 2);
        require("release and following state0 tick use .9 density dissipation",
                Math.abs(release.dissipation - 0.90f) < EPSILON
                        && Math.abs(idle.dissipation - 0.90f) < EPSILON);
    }

    private static void verifyUiCallbackIsNotSerializedBehindDrawPasses() {
        final RippleInkPortFluidPipeline pipeline = configuredPipeline();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 400.0f, 900.0f, 1.0f);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 420.0f, 900.0f, 1.0f);
        pipeline.executeFixedTick(new RecordingSink());
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 460.0f, 900.0f, 1.0f);
        final CountDownLatch uploadEntered = new CountDownLatch(1);
        final CountDownLatch releaseUpload = new CountDownLatch(1);
        final BlockingUploadSink blockingSink = new BlockingUploadSink(uploadEntered, releaseUpload);
        Thread draw = new Thread(new Runnable() {
            @Override
            public void run() {
                pipeline.executeFixedTick(blockingSink);
            }
        }, "n3-draw-lock-probe");
        draw.start();
        try {
            require("draw reached upload pass", uploadEntered.await(2, TimeUnit.SECONDS));
            final CountDownLatch callbackReturned = new CountDownLatch(1);
            Thread callback = new Thread(new Runnable() {
                @Override
                public void run() {
                    pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE,
                            500.0f, 900.0f, 1.0f);
                    callbackReturned.countDown();
                }
            }, "n3-ui-callback-lock-probe");
            callback.start();
            require("native onTouch remains independent while GL draw is in progress",
                    callbackReturned.await(500, TimeUnit.MILLISECONDS));
            callback.join(2000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("lock probe interrupted");
        } finally {
            releaseUpload.countDown();
            try {
                draw.join(2000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        require("draw uses one immutable callback snapshot while later MOVE is accepted",
                blockingSink.inks.size() == 1
                        && Math.abs(blockingSink.inks.get(0).previousX - 420.0f) < EPSILON
                        && Math.abs(blockingSink.inks.get(0).currentX - 460.0f) < EPSILON);
        RecordingSink following = new RecordingSink();
        pipeline.executeFixedTick(following);
        require("callback race still commits tick-owned persistent worker profile",
                following.advects.size() == 1 && following.advects.get(0).dragMode == 2);
        require("callback race merges committed endpoint for the following raw segment",
                following.inks.size() == 1
                        && Math.abs(following.inks.get(0).previousX - 460.0f) < EPSILON
                        && Math.abs(following.inks.get(0).currentX - 500.0f) < EPSILON);
    }

    private static void verifyConcurrentDownWinsOverPriorTickCommit() {
        final RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink setup = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 400.0f, 900.0f, 1.0f);
        for (int n = 0; n < 4; ++n) pipeline.executeFixedTick(setup);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 430.0f, 900.0f, 1.0f);
        pipeline.executeFixedTick(setup);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 470.0f, 900.0f, 1.0f);

        final CountDownLatch uploadEntered = new CountDownLatch(1);
        final CountDownLatch releaseUpload = new CountDownLatch(1);
        Thread draw = new Thread(new Runnable() {
            @Override
            public void run() {
                pipeline.executeFixedTick(new BlockingUploadSink(uploadEntered, releaseUpload));
            }
        }, "n3-new-down-race-draw");
        draw.start();
        try {
            require("old draw reached upload before new DOWN",
                    uploadEntered.await(2, TimeUnit.SECONDS));
            pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 700.0f, 900.0f, 1.0f);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("new DOWN race probe interrupted");
        } finally {
            releaseUpload.countDown();
            try {
                draw.join(2000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        RecordingSink freshPress = new RecordingSink();
        pipeline.executeFixedTick(freshPress);
        require("concurrent new DOWN resets press chronology to n0",
                freshPress.inks.size() == 1 && freshPress.inks.get(0).mode == -1
                        && Math.abs(freshPress.inks.get(0).radius) < EPSILON
                        && Math.abs(freshPress.inks.get(0).currentX - 700.0f) < EPSILON);
    }

    private static void verifyCancelUsesNativeUpAction() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 540.0f, 960.0f, 1.0f, 0L);
        pipeline.onTouch(RippleInkPortEngine.ACTION_CANCEL, 540.0f, 960.0f, 1.0f, 1L);
        for (int tick = 0; tick < 14; ++tick) {
            pipeline.executeFixedTick(sink);
        }
        require("cancel follows UP state-1 completion", sink.inks.size() == 10
                && pipeline.densityUpperBound() < 127.0f);
    }

    private static void verifyShortDragUpRetainsStateOneButLongDragReleases() {
        RippleInkPortFluidPipeline shortDrag = configuredPipeline();
        RecordingSink shortSink = new RecordingSink();
        shortDrag.onTouch(RippleInkPortEngine.ACTION_DOWN, 400.0f, 900.0f, 1.0f);
        shortDrag.onTouch(RippleInkPortEngine.ACTION_MOVE, 420.0f, 900.0f, 1.0f);
        shortDrag.executeFixedTick(shortSink); // state-1 -> state-2 promotion only
        shortDrag.onTouch(RippleInkPortEngine.ACTION_MOVE, 450.0f, 900.0f, 1.0f);
        shortDrag.executeFixedTick(shortSink);
        shortDrag.onTouch(RippleInkPortEngine.ACTION_UP, 450.0f, 900.0f, 1.0f);
        shortDrag.executeFixedTick(shortSink);
        require("short drag UP returns to native state-1", shortSink.inks.size() >= 2
                && shortSink.inks.get(shortSink.inks.size() - 1).mode == -1);

        RippleInkPortFluidPipeline longDrag = configuredPipeline();
        RecordingSink longSink = new RecordingSink();
        longDrag.onTouch(RippleInkPortEngine.ACTION_DOWN, 400.0f, 900.0f, 1.0f);
        longDrag.onTouch(RippleInkPortEngine.ACTION_MOVE, 420.0f, 900.0f, 1.0f);
        longDrag.executeFixedTick(longSink);
        for (int step = 0; step < 10; ++step) {
            longDrag.onTouch(RippleInkPortEngine.ACTION_MOVE, 450.0f + step * 20.0f, 900.0f, 1.0f);
            longDrag.executeFixedTick(longSink);
        }
        int beforeUp = longSink.inks.size();
        longDrag.onTouch(RippleInkPortEngine.ACTION_UP, 650.0f, 900.0f, 1.0f);
        longDrag.executeFixedTick(longSink);
        require("long drag UP enters release rather than state-1", longSink.inks.size() == beforeUp);
    }

    private static void verifyImmediateUpRunsRecoveredStationaryPressCloud() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 540.0f, 960.0f, 1.0f, 0L);
        // Native updates touch coordinates before action dispatch: a same-ms UP moves the
        // state-1 cloud, while its counter/profile stay separate from source history.
        pipeline.onTouch(RippleInkPortEngine.ACTION_UP, 700.0f, 700.0f, 0.1f, 0L);
        pipeline.advanceSourceClock(217L);
        pipeline.execute(13.0f, sink);
        require("quick UP still emits ten state-1 deposits", sink.inks.size() == 10);
        for (int tick = 0; tick < 10; ++tick) {
            RippleInkPortFluidPipeline.AddInkPass ink = sink.inks.get(tick);
            require("press ink remains radial at tick " + tick,
                    ink.mode == -1 && ink.length == 1.0f);
            require("press radius is fixed 8*n at tick " + tick,
                    Math.abs(ink.radius - 8.0f * tick) < EPSILON);
            require("press impulse is fixed at tick " + tick,
                    Math.abs(ink.impulseDensity - 200.0f) < EPSILON);
            require("same-ms UP updates press centre at tick " + tick,
                    Math.abs(ink.currentX - 700.0f) < EPSILON
                            && Math.abs(ink.currentY - 1220.0f) < EPSILON);
        }
        require("thirteen press ticks include three no-source cooldown advects",
                sink.advects.size() == 13);
        float[] expectedBacksteps = {1.0f, 0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f,
                0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.0f};
        for (int tick = 0; tick < expectedBacksteps.length; ++tick) {
            require("press backstep chronology at tick " + tick,
                    Math.abs(sink.advects.get(tick).backwardStep - expectedBacksteps[tick]) < EPSILON);
            require("press keeps density dissipation .92 at tick " + tick,
                    Math.abs(sink.advects.get(tick).dissipation - 0.92f) < EPSILON);
        }
        pipeline.execute(1.0f, sink);
        require("no eleventh stationary AddInk", sink.inks.size() == 10);
        require("release begins only after n12 cleanup",
                Math.abs(sink.advects.get(sink.advects.size() - 1).dissipation - 0.90f) < EPSILON);
    }

    private static void verifyHeldFingerRepeatsStationaryPressCloud() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 540.0f, 960.0f, 1.0f, 0L);

        // Three complete fixed-60 press cycles. A held finger must keep producing the same
        // ten radial stamps followed by the three recovered cooldown ticks in every cycle.
        for (int tick = 0; tick < 39; ++tick) {
            long frameTimeMs = (tick * 1000L + 59L) / 60L;
            pipeline.advanceSourceClock(frameTimeMs);
            pipeline.execute(1.0f, sink);
        }

        require("held finger repeats three ten-pass clouds", sink.inks.size() == 30);
        require("held finger executes three complete thirteen-tick cycles",
                sink.advects.size() == 39);
        for (int cycle = 0; cycle < 3; ++cycle) {
            for (int tick = 0; tick < 10; ++tick) {
                RippleInkPortFluidPipeline.AddInkPass ink = sink.inks.get(cycle * 10 + tick);
                require("held press remains radial in cycle " + cycle + " tick " + tick,
                        ink.mode == -1 && Math.abs(ink.radius - 8.0f * tick) < EPSILON
                                && Math.abs(ink.impulseDensity - 200.0f) < EPSILON);
            }
        }
    }

    private static void verifyPressMoveGateAndModeTwoHandoff() {
        RippleInkPortFluidPipeline centreUpdate = configuredPipeline();
        RecordingSink centreSink = new RecordingSink();
        centreUpdate.onTouch(RippleInkPortEngine.ACTION_DOWN, 500.0f, 900.0f, 1.0f, 0L);
        centreUpdate.execute(1.0f, centreSink);
        centreUpdate.onTouch(RippleInkPortEngine.ACTION_MOVE, 501.0f, 899.0f, 1.0f, 1L);
        centreUpdate.advanceSourceClock(17L);
        centreUpdate.execute(1.0f, centreSink);
        require("small state-1 MOVE updates later press emissions", centreSink.inks.size() == 2
                && Math.abs(centreSink.inks.get(1).currentX - 501.0f) < EPSILON
                && Math.abs(centreSink.inks.get(1).currentY - 1021.0f) < EPSILON);

        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 500.0f, 900.0f, 1.0f, 0L);
        pipeline.advanceSourceClock(200L);
        pipeline.execute(11.0f, sink); // n=0..10, so the move gate is at n11.
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 502.0f, 900.0f, 1.0f, 201L);
        pipeline.execute(1.0f, sink);
        require("n11 move at exactly 2px remains press", sink.inks.size() == 10);

        RippleInkPortFluidPipeline overThreshold = configuredPipeline();
        RecordingSink overSink = new RecordingSink();
        overThreshold.onTouch(RippleInkPortEngine.ACTION_DOWN, 500.0f, 900.0f, 1.0f, 0L);
        overThreshold.advanceSourceClock(200L);
        overThreshold.execute(11.0f, overSink);
        overThreshold.onTouch(RippleInkPortEngine.ACTION_MOVE, 502.01f, 900.0f, 1.0f, 201L);
        overThreshold.advanceSourceClock(217L);
        overThreshold.execute(1.0f, overSink);
        require("state1 crossing MOVE promotes without an early source", overSink.inks.size() == 10);
        overThreshold.onTouch(RippleInkPortEngine.ACTION_MOVE, 514.01f, 900.0f, 1.0f, 218L);
        overThreshold.advanceSourceClock(235L);
        overThreshold.execute(1.0f, overSink);
        require("following state2 MOVE classifies the raw displacement",
                overSink.inks.get(overSink.inks.size() - 1).mode == 2);

        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 502.0f, 900.0f, 1.0f, 202L);
        pipeline.advanceSourceClock(217L);
        pipeline.execute(2.0f, sink); // The n12 zero-distance MOVE has now handed off to drag.
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 502.0f, 900.0f, 1.0f, 218L);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 520.0f, 900.0f, 1.0f, 219L);
        pipeline.advanceSourceClock(235L);
        pipeline.execute(2.0f, sink);
        require("n12 zero-distance MOVE transitions so later drag is mode2",
                sink.inks.get(sink.inks.size() - 1).mode == 2);
    }

    private static void verifyPressCancelAndNewDownReanchor() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 100.0f, 900.0f, 1.0f, 0L);
        pipeline.onTouch(RippleInkPortEngine.ACTION_CANCEL, 100.0f, 900.0f, 1.0f, 1L);
        pipeline.advanceSourceClock(300L);
        pipeline.execute(13.0f, sink);
        require("CANCEL drops a pending press burst immediately", sink.inks.isEmpty());

        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 700.0f, 900.0f, 1.0f, 400L);
        pipeline.advanceSourceClock(400L);
        pipeline.execute(1.0f, sink);
        require("new DOWN discards old press and reanchors centre", sink.inks.size() == 1
                && Math.abs(sink.inks.get(0).currentX - 700.0f) < EPSILON);
    }

    private static void verifyPressStallIsBoundedAndNeverRebased() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 540.0f, 960.0f, 1.0f, 0L);
        pipeline.advanceSourceClock(150L);
        pipeline.execute(4.0f, sink);
        require("press stall retains chronological bounded debt", sink.inks.size() == 4
                && pipeline.hasOverdueSourceDebtForTest());
        require("renderer rebase never collapses a stationary press", pipeline.rebaseOverdueSourceDebt(150L, sink) == 0);
        pipeline.advanceSourceClock(217L);
        pipeline.execute(9.0f, sink);
        require("150ms stall completes exactly thirteen press lifecycle ticks",
                sink.advects.size() == 13 && sink.inks.size() == 10);
        require("press debt drains completely", !pipeline.hasOverdueSourceDebtForTest());
    }

    private static void verifyPressDivergenceProfile() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(240, 240); // (6,6) is the recovered 72px grid anchor.
        pipeline.setWorkerRandomForTest(new ConstantRandom(0x40000000));
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 72.0f, 168.0f, 1.0f, 0L);
        pipeline.execute(1.0f, sink); // n0: strength 12*0.
        pipeline.advanceSourceClock(17L);
        pipeline.execute(1.0f, sink); // n1: 40px radius, strength 12.
        require("press n1 uses recovered divergence radius/strength",
                Math.abs(pipeline.divergenceForTest(6, 6) + 12.0f) < EPSILON);
    }

    private static void verifyPressWorkerMarginAndJitter() {
        RippleInkPortFluidPipeline invalid = new RippleInkPortFluidPipeline();
        invalid.configure(240, 240);
        SequenceRandom invalidRandom = new SequenceRandom(0, 0x7fffffff);
        invalid.setWorkerRandomForTest(invalidRandom);
        invalid.onTouch(RippleInkPortEngine.ACTION_DOWN, 60.0f, 168.0f, 1.0f, 0L);
        invalid.execute(1.0f, new RecordingSink());
        require("invalid-margin press consumes zero rand values", invalidRandom.calls == 0);

        RippleInkPortFluidPipeline valid = new RippleInkPortFluidPipeline();
        valid.configure(240, 240);
        SequenceRandom validRandom = new SequenceRandom(0, 0x7fffffff);
        valid.setWorkerRandomForTest(validRandom);
        valid.onTouch(RippleInkPortEngine.ACTION_DOWN, 72.0f, 168.0f, 1.0f, 0L);
        valid.execute(1.0f, new RecordingSink());
        require("valid-margin press consumes exactly two rand values", validRandom.calls == 2);
        require("press projection jitters only inside recovered bounds",
                valid.lastWorkerJitterXForTest() >= -5.0f && valid.lastWorkerJitterXForTest() <= 5.0f
                        && valid.lastWorkerJitterYForTest() >= -5.0f
                        && valid.lastWorkerJitterYForTest() <= 5.0f);
    }

    private static void verifyEarlyPressHandoffUsesModeTwoEvenSameMillisecond() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 500.0f, 900.0f, 1.0f, 0L);
        // The first meaningful MOVE shares the DOWN millisecond. Native uses it only to promote
        // state1 -> state2; classification and source emission begin with the following MOVE.
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 530.0f, 900.0f, 1.0f, 0L);
        pipeline.advanceSourceClock(0L);
        pipeline.execute(1.0f, sink);
        require("same-ms promotion MOVE emits no early capsule", sink.inks.isEmpty());
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 542.0f, 900.0f, 1.0f, 16L);
        pipeline.advanceSourceClock(17L);
        pipeline.execute(1.0f, sink);
        require("following state2 MOVE emits one classified source", sink.inks.size() == 1);
        RippleInkPortFluidPipeline.AddInkPass first = sink.inks.get(0);
        require("following 12px MOVE selects capsule profile",
                first.mode == 2 && Math.abs(first.radius - 30.0f) < EPSILON
                        && Math.abs(first.impulseDensity - 40.0f) < EPSILON);
        require("mode2 endpoints follow oracle old-to-new order",
                Math.abs(first.currentX - 542.0f) < EPSILON
                        && Math.abs(first.previousX - 530.0f) < EPSILON
                        && Math.abs(first.length - 12.0f) < EPSILON
                        && Math.abs(first.normalX - 1.0f) < EPSILON);
        pipeline.advanceSourceClock(34L);
        pipeline.execute(1.0f, sink);
        require("stationary post-handoff tick does not manufacture a ghost capsule",
                sink.inks.size() == 1 && !pipeline.hasOverdueSourceDebtForTest());
    }

    private static void verifyPostPressIdleMoveUsesOneRebasedModeTwoHandoff() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 500.0f, 900.0f, 1.0f, 0L);
        pipeline.advanceSourceClock(217L);
        pipeline.execute(13.0f, sink); // finish n=0..12 while the finger remains down.
        require("completed stationary press contributes its ten radial passes", sink.inks.size() == 10);

        // A real drag well after n12 must not be ignored or be interpolated from DOWN@0ms.
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 560.0f, 900.0f, 1.0f, 250L);
        pipeline.advanceSourceClock(250L);
        pipeline.execute(1.0f, sink);
        require("post-n12 MOVE promotes without direct source", sink.inks.size() == 10);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 572.0f, 900.0f, 1.0f, 266L);
        pipeline.advanceSourceClock(267L);
        pipeline.execute(1.0f, sink);
        require("post-n12 following MOVE emits one mode2 source", sink.inks.size() == 11);
        RippleInkPortFluidPipeline.AddInkPass handoff = sink.inks.get(10);
        require("post-n12 handoff keeps exact drag source constants",
                handoff.mode == 2 && Math.abs(handoff.radius - 30.0f) < EPSILON
                        && Math.abs(handoff.impulseDensity - 40.0f) < EPSILON);
        require("post-n12 source uses promoted point then following MOVE",
                Math.abs(handoff.currentX - 572.0f) < EPSILON
                        && Math.abs(handoff.previousX - 560.0f) < EPSILON
                        && Math.abs(handoff.length - 12.0f) < EPSILON);

        pipeline.advanceSourceClock(284L);
        pipeline.execute(1.0f, sink);
        require("rebased stationary tick has no DOWN-to-late-MOVE ghost or debt",
                sink.inks.size() == 11 && !pipeline.hasOverdueSourceDebtForTest());
    }

    private static void verifyRecoveredRawDragProfilesAndEndpointOrder() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 500.0f, 900.0f, 1.0f, 0L);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 503.0f, 900.0f, 1.0f, 0L);
        pipeline.execute(1.0f, sink);
        require("promotion MOVE remains source-free", sink.inks.isEmpty());

        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 505.0f, 900.0f, 1.0f, 16L);
        pipeline.advanceSourceClock(17L);
        pipeline.execute(1.0f, sink);
        RippleInkPortFluidPipeline.AddInkPass mode0 = sink.inks.get(0);
        require("distance exactly 2 selects mode0 radial profile",
                mode0.mode == 0 && mode0.radius == 48.0f && mode0.impulseDensity == 100.0f);

        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 510.0f, 900.0f, 1.0f, 33L);
        pipeline.advanceSourceClock(34L);
        pipeline.execute(1.0f, sink);
        RippleInkPortFluidPipeline.AddInkPass mode1 = sink.inks.get(1);
        require("distance 5 selects mode1 radial profile",
                mode1.mode == 1 && mode1.radius == 25.0f && mode1.impulseDensity == 150.0f);
        require("mode0 prepares .94 density for the following advect",
                Math.abs(sink.advects.get(2).dissipation - 0.94f) < EPSILON);

        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 522.0f, 900.0f, 1.0f, 50L);
        pipeline.advanceSourceClock(51L);
        pipeline.execute(1.0f, sink);
        RippleInkPortFluidPipeline.AddInkPass mode2 = sink.inks.get(2);
        require("distance above 10 selects mode2 capsule profile",
                mode2.mode == 2 && mode2.radius == 30.0f && mode2.impulseDensity == 40.0f);
        require("mode2 endpoints are previous-old and current-new",
                mode2.previousX == 510.0f && mode2.currentX == 522.0f
                        && mode2.normalX == 1.0f);
    }

    private static void verifyCadenceAndInputRateInvariance() {
        int[] inputRates = {120, 240};
        for (int inputRate : inputRates) {
            Trace trace = traceLinearGesture(inputRate, 600.0f);
            require("fixed-60 source remains populated at input " + inputRate,
                    trace.inks.size() > 20);
            require("all movement uses one recovered Samsung profile at input " + inputRate,
                    trace.allRecoveredProfiles());
        }
    }

    private static void verifyHybridHfrUsesOnlyFixedInkTicks() {
        RecordingSink stock60 = traceRendererInkAtRefresh(60, false);
        RecordingSink hybrid120 = traceRendererInkAtRefresh(120, true);
        require("hybrid 120 keeps exact upload/advect/AddInk trace",
                stock60.events.equals(hybrid120.events));
        require("hybrid 120 keeps exact advect count",
                stock60.advects.size() == hybrid120.advects.size());
        require("hybrid 120 keeps exact AddInk count",
                stock60.inks.size() == hybrid120.inks.size());
        for (int index = 0; index < hybrid120.advects.size(); ++index) {
            RippleInkPortFluidPipeline.AdvectPass pass = hybrid120.advects.get(index);
            require("hybrid never forwards fractional q to Advect " + index,
                    pass.logicalCredits == 1.0f && pass.dissipation == stock60.advects.get(index).dissipation
                            && pass.backwardStep == stock60.advects.get(index).backwardStep);
        }
        for (int index = 0; index < hybrid120.inks.size(); ++index) {
            RippleInkPortFluidPipeline.AddInkPass pass = hybrid120.inks.get(index);
            RippleInkPortFluidPipeline.AddInkPass stock = stock60.inks.get(index);
            require("hybrid never forwards fractional q to AddInk " + index,
                    pass.logicalCredits == 1.0f && pass.mode == stock.mode
                            && Math.abs(pass.currentX - stock.currentX) < EPSILON
                            && Math.abs(pass.currentY - stock.currentY) < EPSILON
                            && Math.abs(pass.previousX - stock.previousX) < EPSILON
                            && Math.abs(pass.previousY - stock.previousY) < EPSILON
                            && Math.abs(pass.radius - stock.radius) < EPSILON
                            && Math.abs(pass.impulseDensity - stock.impulseDensity) < EPSILON);
        }
    }

    private static void verifyHybridSkippedDisplayFrameDoesNotCreateExtraSource() {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        engine.setHighFrameRateEnabled(true);
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 540.0f, 960.0f, 1.0f, 0L);
        long start = 32_000_000_000L;
        engine.advanceRendererFrame(start);
        pipeline.advanceSourceClock(8L);
        RippleInkPortEngine.RendererFrameAdvance first = engine.advanceRendererFrame(
                start + 8_333_334L);
        require("first 120Hz HFR frame has no Ink tick", first.inkTicks == 0);

        // The 16.67 ms vsync is skipped.  One later presentation receives one fixed Ink tick;
        // it must not manufacture a second raw source emission merely because a display frame was
        // absent.
        pipeline.advanceSourceClock(25L);
        RippleInkPortEngine.RendererFrameAdvance afterSkip = engine.advanceRendererFrame(
                start + 25_000_000L);
        require("one skipped 120Hz frame yields one bounded Ink tick", afterSkip.inkTicks == 1);
        pipeline.executeFixedTick(sink);
        require("skipped display frame has one upload and one advect", sink.events.size() >= 2
                && "upload".equals(sink.events.get(0))
                && sink.events.get(1).startsWith("advect:"));
        require("skipped display frame does not add a duplicate source", sink.inks.size() == 1);
    }

    /** Mirrors the renderer boundary without Android/GLES: water may be HFR, Ink is q=1 only. */
    private static RecordingSink traceRendererInkAtRefresh(int refreshHz, boolean highFrameRate) {
        RippleInkPortEngine engine = new RippleInkPortEngine();
        engine.configureSurface(1080, 1920);
        engine.setHighFrameRateEnabled(highFrameRate);
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 240.0f, 960.0f, 1.0f, 0L);
        long start = 31_000_000_000L;
        engine.advanceRendererFrame(start);
        boolean moved = false;
        boolean released = false;
        for (int frame = 1; frame <= refreshHz; ++frame) {
            // Round upward so the exact 60 Hz baseline reaches its first logical tick at
            // 16,666,667 ns; truncation would incorrectly delay that baseline to frame two.
            long elapsedNs = (frame * 1_000_000_000L + refreshHz - 1L) / refreshHz;
            long elapsedMs = elapsedNs / 1_000_000L;
            if (!moved && elapsedMs >= 250L) {
                pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE,
                        540.0f, 960.0f, 1.0f, 250L);
                moved = true;
            }
            if (!released && elapsedMs >= 500L) {
                pipeline.onTouch(RippleInkPortEngine.ACTION_UP,
                        780.0f, 960.0f, 1.0f, 500L);
                released = true;
            }
            pipeline.advanceSourceClock(elapsedMs);
            RippleInkPortEngine.RendererFrameAdvance advance = engine.advanceRendererFrame(
                    start + elapsedNs);
            for (int tick = 0; tick < advance.inkTicks; ++tick) {
                pipeline.executeFixedTick(sink);
            }
        }
        return sink;
    }

    private static void verifyContinuousSlowAndFastSegments() {
        Trace slow = traceLinearGesture(240, 120.0f);
        Trace fast = traceLinearGesture(240, 1440.0f);
        require("slow keeps its state-1 cloud until the n12 move gate",
                slow.hasPressPrefix(10) && slow.allRecoveredProfiles());
        require("fast has recovered mode2 source trail", fast.inks.size() > 20
                && fast.containsMode(2) && fast.allRecoveredProfiles());
        require("slow post-press trail is contiguous", slow.isContiguousFrom(10));
        require("fast trail is contiguous", fast.isContiguous());
        require("fast source capsules carry more path than slow",
                fast.inks.get(20).length > slow.inks.get(20).length * 10.0f);
    }

    private static void verifyDirectModeTwoCapsuleRemainsExact() {
        Trace trace = traceLinearGesture(120, 1440.0f);
        for (int index = 0; index < trace.inks.size(); ++index) {
            RippleInkPortFluidPipeline.AddInkPass pass = trace.inks.get(index);
            if (pass.mode != 2) continue;
            require("mode2 keeps direct capsule radius30", Math.abs(pass.radius - 30.0f) < EPSILON);
            require("mode2 keeps source impulse40", Math.abs(pass.impulseDensity - 40.0f) < EPSILON);
            require("mode2 source is fixed logical tick", Math.abs(pass.logicalCredits - 1.0f) < EPSILON);
        }
    }

    private static void verifyDensityAddInkTenPixelBoundary() {
        require("density AddInk excludes x=10", tapInkCountAt(10.0f, 100.0f) == 0);
        require("density AddInk excludes bottom y=10", tapInkCountAt(100.0f, 1910.0f) == 0);
        require("density AddInk admits x/y above 10", tapInkCountAt(10.5f, 1909.5f) == 1);
    }

    private static int tapInkCountAt(float localX, float localY) {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, localX, localY, 1.0f, 0L);
        pipeline.execute(1.0f, sink);
        return sink.inks.size();
    }

    private static void verifyDirectVelocityCapsuleBeforeSelfAdvection() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(240, 240); // screen/12 gives 12px cell centres.
        // A=(72,72), B=(120,72), both inside the strict 60px mode2 gate.
        pipeline.runDirectVelocityCapsuleForTest(120.0f, 168.0f, 72.0f, 168.0f);
        // C=(78,78): t=6, P=(78,72), r=(0,6), B-P=(42,0), dist=6.
        require("FUN19530 direct field is present before self-advection",
                Math.abs(pipeline.velocityXForTest(6, 6) - 25.2f) < EPSILON
                        && Math.abs(pipeline.velocityYForTest(6, 6) - 3.6f) < EPSILON);
        require("direct capsule keeps both segment endpoints open",
                Math.abs(pipeline.velocityXForTest(5, 6)) < EPSILON
                        && Math.abs(pipeline.velocityYForTest(5, 6)) < EPSILON
                        && Math.abs(pipeline.velocityXForTest(10, 6)) < EPSILON
                        && Math.abs(pipeline.velocityYForTest(10, 6)) < EPSILON);
        // C=(78,42) has t=6 but exactly dist=30, which must be excluded strictly.
        require("direct velocity capsule keeps strict radius30 boundary",
                Math.abs(pipeline.velocityXForTest(6, 3)) < EPSILON
                        && Math.abs(pipeline.velocityYForTest(6, 3)) < EPSILON);
    }

    private static void verifyStallDrainsChronologicallyWithoutRawReplay() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 100.0f, 900.0f, 1.0f, 0L);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 130.0f, 900.0f, 1.0f, 10L);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 190.0f, 900.0f, 1.0f, 75L);
        pipeline.advanceSourceClock(75L);
        pipeline.execute(4.0f, sink); // Engine can cap solver catch-up; source debt must remain.
        // The first scheduled point is the DOWN anchor itself, so it deliberately emits no
        // zero-length source.  The remaining three chronological samples are all real mode-2
        // capsules; the old radial-tap path incorrectly made this count four.
        require("promotion MOVE is suppressed while chronological state2 samples remain",
                sink.inks.size() == 3);
        for (RippleInkPortFluidPipeline.AddInkPass ink : sink.inks) {
            require("stalled movement never falls back to radial tap", ink.mode == 2);
        }
        pipeline.execute(0.5f, sink);
        require("bounded source debt is fully drained", sink.inks.size() == 3
                && !pipeline.hasOverdueSourceDebtForTest());
        require("stalled sources remain contiguous", new Trace(sink.inks).isContiguous());
    }

    private static void verifyRendererCapRebasesLongSourceDebt() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 100.0f, 900.0f, 1.0f, 0L);
        pipeline.advanceSourceClock(0L);
        pipeline.execute(1.0f, sink);

        // Renderer-realistic sequence: a 75 ms Engine discontinuity returns no solver credit,
        // then its next fixed frame has one q=1 tick.  Source debt must not remain four ticks
        // behind forever after that frame.
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 130.0f, 900.0f, 1.0f, 10L);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 175.0f, 900.0f, 1.0f, 100L);
        pipeline.advanceSourceClock(100L);
        require("stall creates source debt", pipeline.hasOverdueSourceDebtForTest());
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 192.0f, 900.0f, 1.0f, 117L);
        pipeline.advanceSourceClock(117L);
        int beforeRebase = sink.inks.size();
        pipeline.execute(1.0f, sink); // Engine's normal post-stall q=1 only.
        int rebasePasses = pipeline.rebaseOverdueSourceDebt(117L, sink);
        require("rebase uses one bounded normal pass", rebasePasses >= 2 && rebasePasses <= 3);
        require("post-cap backlog is fully rebased", !pipeline.hasOverdueSourceDebtForTest());
        require("one normal plus at most one collapsed source", sink.inks.size() - beforeRebase <= 2);
        RippleInkPortFluidPipeline.AddInkPass collapsed = sink.inks.get(sink.inks.size() - 1);
        require("collapsed endpoint is current frame sample", Math.abs(collapsed.currentX - 192.0f) < EPSILON);
        require("collapsed source is one mode2 segment", collapsed.mode == 2);

        // A much longer second discontinuity exercises the same bounded policy, proving that
        // the first rebase did not leave a hidden, permanent source-clock lag.
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 1000.0f, 900.0f, 1.0f, 1_000L);
        pipeline.advanceSourceClock(1_000L);
        pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE, 1017.0f, 900.0f, 1.0f, 1_017L);
        pipeline.advanceSourceClock(1_017L);
        beforeRebase = sink.inks.size();
        pipeline.execute(1.0f, sink);
        pipeline.rebaseOverdueSourceDebt(1_017L, sink);
        require("long stall also leaves no source debt", !pipeline.hasOverdueSourceDebtForTest());
        require("long-stall collapse stays bounded", sink.inks.size() - beforeRebase <= 2);
        require("long-stall endpoint is current", Math.abs(
                sink.inks.get(sink.inks.size() - 1).currentX - 1017.0f) < EPSILON);
    }

    private static void verifyUpFlushesTerminalAndTailParksGradually() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 100.0f, 900.0f, 1.0f, 0L);
        pipeline.advanceSourceClock(0L);
        pipeline.execute(1.0f, sink);
        pipeline.onTouch(RippleInkPortEngine.ACTION_UP, 110.0f, 900.0f, 1.0f, 9L);
        pipeline.advanceSourceClock(217L);
        pipeline.execute(12.0f, sink);
        require("UP does not truncate the remaining stationary press ticks", sink.inks.size() == 10);
        require("UP cannot turn the press cloud into a terminal mode2 segment",
                sink.inks.get(sink.inks.size() - 1).mode == -1);
        require("tail remains live immediately after release", pipeline.hasVisibleTail());
        for (int tick = 0; tick < 105; ++tick) {
            pipeline.execute(1.0f, sink);
        }
        require("tail is gradual rather than timer-cleared", pipeline.hasVisibleTail()
                && pipeline.densityUpperBound() > RippleInkPortFluidPipeline.TAIL_DENSITY_THRESHOLD);
        pipeline.execute(1.0f, sink);
        require("conservative 106-credit perceptual envelope parks", !pipeline.hasVisibleTail());
        require("release never reinjects", sink.inks.size() == 10);
    }

    private static void verifyNewGestureReanchorsWithoutHardClear() {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 100.0f, 900.0f, 1.0f, 0L);
        pipeline.execute(1.0f, sink);
        pipeline.onTouch(RippleInkPortEngine.ACTION_UP, 120.0f, 900.0f, 1.0f, 8L);
        pipeline.execute(1.0f, sink);
        float retainedBound = pipeline.densityUpperBound();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 700.0f, 900.0f, 1.0f, 20L);
        pipeline.execute(1.0f, sink);
        require("new DOWN produces fresh radial tap", sink.inks.get(sink.inks.size() - 1).mode == -1);
        require("new DOWN cannot bridge to released endpoint",
                sink.inks.get(sink.inks.size() - 1).length == 1.0f);
        require("new tap preserves/refreshes retained field without clear",
                pipeline.densityUpperBound() >= retainedBound);
    }

    private static Trace traceLinearGesture(int inputRate, float speedPxPerSecond) {
        RippleInkPortFluidPipeline pipeline = configuredPipeline();
        RecordingSink sink = new RecordingSink();
        pipeline.onTouch(RippleInkPortEngine.ACTION_DOWN, 100.0f, 900.0f, 1.0f, 0L);
        int nextInput = 1;
        for (int frame = 0; frame <= 30; ++frame) {
            long frameMs = frame * 1000L / 60L;
            while (nextInput * 1000L <= inputRate * frameMs && nextInput < inputRate / 2) {
                long inputMs = Math.round(nextInput * 1000.0 / inputRate);
                pipeline.onTouch(RippleInkPortEngine.ACTION_MOVE,
                        100.0f + speedPxPerSecond * inputMs / 1000.0f, 900.0f, 1.0f, inputMs);
                ++nextInput;
            }
            if (frameMs == 500L) {
                pipeline.onTouch(RippleInkPortEngine.ACTION_UP,
                        100.0f + speedPxPerSecond * 0.5f, 900.0f, 1.0f, 500L);
            }
            pipeline.advanceSourceClock(frameMs);
            pipeline.execute(1.0f, sink);
        }
        // If a rounded display frame did not land precisely on terminal time, drain the queued
        // final source in one normal logical substep.
        pipeline.execute(1.0f, sink);
        return new Trace(sink.inks);
    }

    private static void verifyVelocityNormalizedBilerpNotDampingOnly() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(48, 48); // 4x4 velocity grid.
        pipeline.setWorkerRandomForTest(new ConstantRandom(0));
        for (int y = 0; y < pipeline.fluidHeight(); ++y) {
            pipeline.setVelocityCellForTest(2, y, 1.0f, 0.0f);
        }
        // mode -1 bypasses the move projection gate, isolating FUN16dd8's self-advection.
        pipeline.runWorkerForTest(-1, 0.0f, 48.0f, 0.0f, 48.0f);
        // u=((2.5-.25)/4)=.5625, p=u*(4-1)=1.6875; bilerp is .6875 then *.8=.55.
        require("normalized bilerp differs from damping-only",
                Math.abs(pipeline.velocityXForTest(2, 2) - 0.55f) < EPSILON);
    }

    private static void verifyModeTwoLocalOverrideSignAndStrictRadius() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(300, 300); // screen/12 grid; choose a centre 25px from an anchor.
        pipeline.setWorkerRandomForTest(new ConstantRandom(0));
        // center bottomY=48 is outside the 60px main gate, so projection cannot mask FUN16dd8.
        // raw delta=(4, 256-252) -> recovered bottom-space vector=(4,4), k(mode2)=.5.
        pipeline.runWorkerForTest(2, 95.0f, 252.0f, 91.0f, 256.0f);
        require("mode2 local override has recovered y sign",
                Math.abs(pipeline.velocityXForTest(8, 4) - 1.92f) < EPSILON
                        && Math.abs(pipeline.velocityYForTest(8, 4) - 1.92f) < EPSILON);
        require("25px circle is strict at boundary",
                Math.abs(pipeline.velocityXForTest(10, 4)) < EPSILON
                        && Math.abs(pipeline.velocityYForTest(10, 4)) < EPSILON);
    }

    private static void verifyCenteredDivergenceUsesGridAnchor() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(240, 240); // grid anchors are integral 12px steps.
        pipeline.setWorkerRandomForTest(new ConstantRandom(0x40000000)); // exactly zero jitter.
        // raw top 168 is GLES bottom 72; (6,6)'s grid anchor is exactly (72,72).
        pipeline.runWorkerForTest(2, 72.0f, 168.0f, 72.0f, 168.0f);
        require("divergence source is anchored at x,y not x+.5,y+.5",
                Math.abs(pipeline.divergenceForTest(6, 6) + 20.0f) < EPSILON);
    }

    private static void verifySeededWorkerJitterIsBoundedDeterministicAndTwoDraw() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(240, 240);
        SequenceRandom sequence = new SequenceRandom(0, 0x7fffffff);
        pipeline.setWorkerRandomForTest(sequence);
        pipeline.runWorkerForTest(2, 60.0f, 168.0f, 60.0f, 168.0f);
        require("invalid-margin worker consumes zero rand values", sequence.calls == 0);
        pipeline.runWorkerForTest(2, 72.0f, 168.0f, 72.0f, 168.0f);
        require("valid-margin worker consumes exactly two rand values", sequence.calls == 2);
        require("jitter x remains oracle bounded", pipeline.lastWorkerJitterXForTest() >= -5.0f
                && pipeline.lastWorkerJitterXForTest() <= 5.0f);
        require("jitter y remains oracle bounded", pipeline.lastWorkerJitterYForTest() >= -5.0f
                && pipeline.lastWorkerJitterYForTest() <= 5.0f);

        RippleInkPortFluidPipeline first = new RippleInkPortFluidPipeline();
        RippleInkPortFluidPipeline second = new RippleInkPortFluidPipeline();
        first.configure(240, 240);
        second.configure(240, 240);
        first.setWorkerRandomSeedForTest(0x2468ace);
        second.setWorkerRandomSeedForTest(0x2468ace);
        first.runWorkerForTest(2, 72.0f, 168.0f, 72.0f, 168.0f);
        second.runWorkerForTest(2, 72.0f, 168.0f, 72.0f, 168.0f);
        require("seeded jitter x is deterministic", Math.abs(first.lastWorkerJitterXForTest()
                - second.lastWorkerJitterXForTest()) < EPSILON);
        require("seeded jitter y is deterministic", Math.abs(first.lastWorkerJitterYForTest()
                - second.lastWorkerJitterYForTest()) < EPSILON);
        require("seeded worker result is deterministic", Math.abs(first.velocityChecksum()
                - second.velocityChecksum()) < EPSILON);
    }

    private static void verifyWorkerMarginSkipsProjection() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(240, 240);
        pipeline.setWorkerRandomForTest(new ConstantRandom(0x40000000));
        pipeline.runWorkerForTest(2, 72.0f, 168.0f, 72.0f, 168.0f);
        float retainedDivergence = pipeline.divergenceForTest(6, 6);
        // x=60 is invalid by the strict main gate, after self-advection but before pressure clear.
        pipeline.runWorkerForTest(2, 60.0f, 168.0f, 60.0f, 168.0f);
        require("strict 60px margin skips divergence/pressure/project",
                Math.abs(pipeline.divergenceForTest(6, 6) - retainedDivergence) < EPSILON);
    }

    private static void verifyThousandWorkerTicksStayFinite() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(240, 240);
        pipeline.setWorkerRandomForTest(new ConstantRandom(0x40000000));
        for (int tick = 0; tick < 1000; ++tick) {
            pipeline.runWorkerForTest(2, 72.0f, 168.0f, 71.0f, 169.0f);
        }
        require("1000 worker ticks remain finite and representable",
                pipeline.hasFiniteRepresentableVelocity());
    }

    private static RippleInkPortFluidPipeline configuredPipeline() {
        RippleInkPortFluidPipeline pipeline = new RippleInkPortFluidPipeline();
        pipeline.configure(1080, 1920);
        return pipeline;
    }

    private static final class Trace {
        final List<RippleInkPortFluidPipeline.AddInkPass> inks;

        Trace(List<RippleInkPortFluidPipeline.AddInkPass> inks) {
            this.inks = new ArrayList<RippleInkPortFluidPipeline.AddInkPass>(inks);
        }

        boolean equalsGeometry(Trace other) {
            if (inks.size() != other.inks.size()) {
                return false;
            }
            for (int index = 0; index < inks.size(); ++index) {
                RippleInkPortFluidPipeline.AddInkPass a = inks.get(index);
                RippleInkPortFluidPipeline.AddInkPass b = other.inks.get(index);
                if (a.mode != b.mode || Math.abs(a.currentX - b.currentX) > EPSILON
                        || Math.abs(a.previousX - b.previousX) > EPSILON
                        || Math.abs(a.length - b.length) > EPSILON) {
                    return false;
                }
            }
            return true;
        }

        boolean allRecoveredProfiles() {
            for (RippleInkPortFluidPipeline.AddInkPass pass : inks) {
                if (pass.mode == -1) {
                    if (pass.impulseDensity != 200.0f) return false;
                } else if (pass.mode == 0) {
                    if (pass.radius != 48.0f || pass.impulseDensity != 100.0f) return false;
                } else if (pass.mode == 1) {
                    if (pass.radius != 25.0f || pass.impulseDensity != 150.0f) return false;
                } else if (pass.mode == 2) {
                    if (pass.radius != 30.0f || pass.impulseDensity != 40.0f) return false;
                } else {
                    return false;
                }
            }
            return !inks.isEmpty();
        }

        boolean hasPressPrefix(int count) {
            if (inks.size() < count) {
                return false;
            }
            for (int index = 0; index < count; ++index) {
                if (inks.get(index).mode != -1) {
                    return false;
                }
            }
            return true;
        }

        boolean allModeTwoFrom(int start) {
            for (int index = start; index < inks.size(); ++index) {
                if (inks.get(index).mode != 2) {
                    return false;
                }
            }
            return true;
        }

        boolean allModeFrom(int start, int mode) {
            for (int index = start; index < inks.size(); ++index) {
                if (inks.get(index).mode != mode) return false;
            }
            return inks.size() > start;
        }

        boolean containsMode(int mode) {
            for (RippleInkPortFluidPipeline.AddInkPass pass : inks) {
                if (pass.mode == mode) return true;
            }
            return false;
        }

        boolean allExpectedMass() {
            // The fixed state-1 tick zero is the drag's historical first radial source; the
            // recovered profile makes it radius zero with impulse 200 before mode2 takes over.
            return inks.get(0).impulseDensity == 200.0f && allSegmentMass();
        }

        boolean allSegmentMass() {
            for (int index = 1; index < inks.size(); ++index) {
                if (inks.get(index).impulseDensity != 40.0f) {
                    return false;
                }
            }
            return true;
        }

        boolean isContiguous() {
            return isContiguousFrom(1);
        }



        boolean isContiguousFrom(int start) {
            for (int index = Math.max(1, start + 1); index < inks.size(); ++index) {
                if (Math.abs(inks.get(index - 1).currentX - inks.get(index).previousX) > EPSILON
                        || Math.abs(inks.get(index - 1).currentY - inks.get(index).previousY) > EPSILON) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class RecordingSink implements RippleInkPortFluidPipeline.PassSink {
        final List<String> events = new ArrayList<String>();
        final List<RippleInkPortFluidPipeline.AdvectPass> advects =
                new ArrayList<RippleInkPortFluidPipeline.AdvectPass>();
        final List<RippleInkPortFluidPipeline.AddInkPass> inks =
                new ArrayList<RippleInkPortFluidPipeline.AddInkPass>();

        @Override
        public void uploadVelocity(byte[] rgba, int width, int height) {
            events.add("upload");
        }

        @Override
        public void advectDensity(RippleInkPortFluidPipeline.AdvectPass pass) {
            events.add("advect:" + pass.sourceIndex + ">" + pass.destinationIndex);
            advects.add(pass);
        }

        @Override
        public void addInk(RippleInkPortFluidPipeline.AddInkPass pass) {
            events.add("ink:" + pass.sourceIndex + ">" + pass.destinationIndex);
            inks.add(pass);
        }
    }

    private static final class BlockingUploadSink implements RippleInkPortFluidPipeline.PassSink {
        private final CountDownLatch uploadEntered;
        private final CountDownLatch releaseUpload;
        final List<RippleInkPortFluidPipeline.AddInkPass> inks =
                new ArrayList<RippleInkPortFluidPipeline.AddInkPass>();

        BlockingUploadSink(CountDownLatch uploadEntered, CountDownLatch releaseUpload) {
            this.uploadEntered = uploadEntered;
            this.releaseUpload = releaseUpload;
        }

        @Override
        public void uploadVelocity(byte[] rgba, int width, int height) {
            uploadEntered.countDown();
            try {
                if (!releaseUpload.await(3, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting to release upload pass");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("upload lock probe interrupted");
            }
        }

        @Override
        public void advectDensity(RippleInkPortFluidPipeline.AdvectPass pass) {
        }

        @Override
        public void addInk(RippleInkPortFluidPipeline.AddInkPass pass) {
            inks.add(pass);
        }
    }

    private static final class ConstantRandom implements RippleInkPortFluidPipeline.WorkerRandom {
        private final int value;

        ConstantRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextRand31() {
            return value;
        }
    }

    private static final class SequenceRandom implements RippleInkPortFluidPipeline.WorkerRandom {
        private final int[] values;
        int calls;

        SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextRand31() {
            int value = values[Math.min(calls, values.length - 1)];
            ++calls;
            return value;
        }
    }

    private static void require(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private RippleInkPortFluidPipelineTest() {
    }
}
