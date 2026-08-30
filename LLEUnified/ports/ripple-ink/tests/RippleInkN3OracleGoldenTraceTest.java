package com.codex.lle;

import java.util.ArrayList;
import java.util.List;

/**
 * Executable golden trace for the 66,800-byte Note 3 ENB4 oracle.
 *
 * <p>This deliberately has no dependency on the port implementation.  It records the recovered
 * raw-callback state machine and frame ordering, so a later adapter can compare production
 * snapshots against an oracle that cannot silently change with the implementation.</p>
 */
public final class RippleInkN3OracleGoldenTraceTest {
    private static final float EPSILON = 0.0001f;
    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;
    private static final int ACTION_MOVE = 2;

    public static void main(String[] args) {
        verifyStationaryPressGoldenTrace();
        verifyMovePreventsAutomaticStateOneCleanup();
        verifyCallbacksAreAppliedBeforeTheFirstDraw();
        verifyRawCallbackProfilesAreNotResampled();
        verifyStateTwoDrawCadenceAndShortUpResume();
        verifyIdleKeepsThePreviousWorkerProfile();
        verifyRecoveredWorkerMargins();
        verifyBionicLrand48SequenceAndJitter();
        verifyWorkerFrameLatencyOrder();
        System.out.println("RippleInkN3OracleGoldenTraceTest: PASS");
    }

    private static void verifyStationaryPressGoldenTrace() {
        Oracle oracle = new Oracle();
        oracle.touch(ACTION_DOWN, 500, 900);

        float[] expectedBackstep = {
                1.0f, 0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f,
                0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.0f
        };
        for (int n = 0; n <= 12; ++n) {
            Tick tick = oracle.tick();
            require("state1 before tick " + n, tick.stateBefore == 1);
            require("state1 counter " + n, tick.stepBefore == n && tick.stepAfter == n + 1);
            require("state1 source window " + n, tick.inject == (n < 10));
            require("state1 radius " + n, close(tick.profile.radius, 8.0f * n));
            require("state1 impulse " + n, close(tick.profile.impulse, 200.0f));
            require("state1 velocity dissipation " + n,
                    close(tick.profile.velocityDissipation, 0.94f));
            require("state1 density dissipation " + n,
                    close(tick.profile.densityDissipation, 0.92f));
            require("state1 backstep chronology " + n,
                    close(tick.backstep, expectedBackstep[n]));
        }
        require("step>12 with non-MOVE cleans state1", oracle.state == 0);
        Tick idle = oracle.tick();
        require("stationary press has no automatic repeat", !idle.inject && idle.stateBefore == 0);
    }

    private static void verifyMovePreventsAutomaticStateOneCleanup() {
        Oracle oracle = new Oracle();
        oracle.touch(ACTION_DOWN, 500, 900);
        for (int n = 0; n < 12; ++n) oracle.tick();
        // A zero-distance MOVE is still ACTION_MOVE in the native global.  It must not fabricate
        // a source, but it suppresses the end-of-cycle state=0 cleanup.
        oracle.touch(ACTION_MOVE, 500, 900);
        Tick n12 = oracle.tick();
        require("MOVE-retained n12 has no source", !n12.inject);
        require("last MOVE retains state1 after step 12", oracle.state == 1 && oracle.step == 13);
        Tick n13 = oracle.tick();
        require("retained state1 stays source-free", !n13.inject && oracle.state == 1);
        require("no hidden held-finger cycle reset", oracle.step == 14);
    }

    private static void verifyCallbacksAreAppliedBeforeTheFirstDraw() {
        Oracle oracle = new Oracle();
        oracle.touch(ACTION_DOWN, 500, 900);
        oracle.touch(ACTION_MOVE, 530, 900);

        require("DOWN transition survives a pre-draw MOVE", oracle.state == 2);
        Tick promotionFrame = oracle.tick();
        assertProfile("promotion frame uses persistent mode0", promotionFrame,
                0, 48.0f, 100.0f, 0.80f, 0.94f);
        require("promotion frame deposits radial density", promotionFrame.depositsDensity);

        oracle.touch(ACTION_MOVE, 542, 900);
        Tick firstDragFrame = oracle.tick();
        assertProfile("post-promotion raw distance 12", firstDragFrame,
                2, 30.0f, 40.0f, 0.96f, 0.92f);
        require("post-promotion segment starts at promotion callback",
                firstDragFrame.previousX == 530 && firstDragFrame.currentX == 542);

        Oracle incorrectlyCoalesced = new Oracle();
        incorrectlyCoalesced.touch(ACTION_MOVE, 530, 900);
        require("a lone MOVE cannot synthesize the missing DOWN transition",
                incorrectlyCoalesced.state == 0 && !incorrectlyCoalesced.tick().inject);
    }

    private static void verifyRawCallbackProfilesAreNotResampled() {
        Oracle oracle = new Oracle();
        oracle.forceDragging(500, 900);

        oracle.touch(ACTION_MOVE, 502, 900);
        Tick mode0 = oracle.tick();
        assertProfile("raw distance 2", mode0, 0, 48.0f, 100.0f, 0.80f, 0.94f);
        require("mode0 keeps raw integer endpoints",
                mode0.previousX == 500 && mode0.currentX == 502);

        oracle.touch(ACTION_MOVE, 507, 900);
        Tick mode1 = oracle.tick();
        assertProfile("raw distance 5", mode1, 1, 25.0f, 150.0f, 0.94f, 0.92f);
        require("mode1 starts at prior callback", mode1.previousX == 502 && mode1.currentX == 507);

        oracle.touch(ACTION_MOVE, 519, 900);
        Tick mode2 = oracle.tick();
        assertProfile("raw distance 12", mode2, 2, 30.0f, 40.0f, 0.96f, 0.92f);
        require("mode2 starts at prior callback", mode2.previousX == 507 && mode2.currentX == 519);

        // With no new callback the native draw path sees the same latest globals.  There is no
        // timestamp history, interpolation, source debt or endpoint rebase in ENB4.
        Tick repeatedLatest = oracle.tick();
        assertProfile("latest raw state is frame-latched", repeatedLatest,
                2, 30.0f, 40.0f, 0.96f, 0.92f);
        require("mode2 held frame collapses the draw segment to length zero",
                repeatedLatest.previousX == 519 && repeatedLatest.currentX == 519);
        require("mode2 length-zero Inject deposits no density", !repeatedLatest.depositsDensity);
    }

    private static void verifyStateTwoDrawCadenceAndShortUpResume() {
        Oracle mode0 = new Oracle();
        mode0.forceDragging(500, 900);
        mode0.touch(ACTION_MOVE, 502, 900);
        Tick mode0First = mode0.tick();
        Tick mode0Held = mode0.tick();
        require("held mode0 calls Inject every draw", mode0First.inject && mode0Held.inject);
        require("held mode0 radial source redeposits density",
                mode0First.depositsDensity && mode0Held.depositsDensity);

        Oracle mode1 = new Oracle();
        mode1.forceDragging(500, 900);
        mode1.touch(ACTION_MOVE, 505, 900);
        Tick mode1First = mode1.tick();
        Tick mode1Held = mode1.tick();
        require("held mode1 calls Inject every draw", mode1First.inject && mode1Held.inject);
        require("held mode1 radial source redeposits density",
                mode1First.depositsDensity && mode1Held.depositsDensity);

        Oracle mode2 = new Oracle();
        mode2.forceDragging(500, 900);
        mode2.touch(ACTION_MOVE, 512, 900);
        Tick mode2First = mode2.tick();
        Tick mode2Held = mode2.tick();
        require("mode2 first segment deposits density", mode2First.depositsDensity);
        require("held mode2 still calls Inject", mode2Held.inject);
        require("held mode2 length zero deposits no density", !mode2Held.depositsDensity);
        require("state2 drag counter increments per draw, not per callback", mode2.count == 2);

        Oracle resumed = new Oracle();
        resumed.touch(ACTION_DOWN, 500, 900);
        for (int n = 0; n < 4; ++n) resumed.tick();
        resumed.touch(ACTION_MOVE, 530, 900);
        resumed.tick();
        resumed.touch(ACTION_UP, 530, 900);
        Tick resumedPress = resumed.tick();
        require("short state2 UP resumes state1", resumedPress.stateBefore == 1);
        require("state1 step survives state2", resumedPress.stepBefore == 4);
        require("resumed state1 continues radius chronology",
                resumedPress.inject && close(resumedPress.profile.radius, 32.0f));
    }

    private static void verifyIdleKeepsThePreviousWorkerProfile() {
        WorkerProfileTimeline timeline = new WorkerProfileTimeline();
        timeline.drawStateTwo(2);
        timeline.release();
        WorkerProfile releaseFrame = timeline.drawIdle();
        WorkerProfile nextIdleFrame = timeline.drawIdle();
        require("release launch keeps previous mode2 worker profile", releaseFrame.mode == 2);
        require("state0 does not replace worker profile with default mode0", nextIdleFrame.mode == 2);
        require("release density dissipation is .9",
                close(releaseFrame.densityDissipation, 0.90f)
                        && close(nextIdleFrame.densityDissipation, 0.90f));
    }

    private static void verifyRecoveredWorkerMargins() {
        require("mode2 uses strict 60px margin", workerMargin(2, 72, 72, 240, 240) == 60);
        require("mode2 boundary 60 is rejected", !workerAdmitted(2, 60, 72, 240, 240));
        require("mode1 uses 10px margin", workerMargin(1, 11, 11, 240, 240) == 10
                && workerAdmitted(1, 11, 11, 240, 240));
        require("mode1 strict boundary is rejected", !workerAdmitted(1, 10, 11, 240, 240));
        require("mode0 interior keeps 60px margin", workerMargin(0, 72, 72, 240, 240) == 60);
        require("mode0 near edge degrades to 12px margin",
                workerMargin(0, 20, 72, 240, 240) == 12
                        && workerAdmitted(0, 20, 72, 240, 240));
        require("mode0 degraded margin is strict", !workerAdmitted(0, 12, 72, 240, 240));
    }

    private static void verifyBionicLrand48SequenceAndJitter() {
        Lrand48 random = new Lrand48();
        // ENB4 imports lrand48 but never calls srand48/seed48. Bionic therefore starts from
        // POSIX/Bionic's untouched process-default state; srand48(0) is a different sequence.
        int[] expected = {851401618, 1804928587, 758783491, 959030623, 684387517};
        for (int index = 0; index < expected.length; ++index) {
            require("Bionic default lrand48 value " + index, random.next() == expected[index]);
        }
        require("first recovered jitter", close(jitter(expected[0]), 1.035352f));
        require("second recovered jitter", close(jitter(expected[1]), -3.404854f));
    }

    private static void verifyWorkerFrameLatencyOrder() {
        WorkerTimeline timeline = new WorkerTimeline();
        timeline.frame(7);
        String[] expected = {
                "join:6", "upload:6", "launch:7:pre-profile", "advect:6", "profile:7", "inject:7"
        };
        require("worker frame event count", timeline.events.size() == expected.length);
        for (int index = 0; index < expected.length; ++index) {
            require("worker frame order " + index,
                    expected[index].equals(timeline.events.get(index)));
        }
    }

    private static void assertProfile(String label, Tick tick, int mode, float radius,
            float impulse, float velocityDissipation, float densityDissipation) {
        require(label + " injects", tick.inject);
        require(label + " mode", tick.profile.mode == mode);
        require(label + " radius", close(tick.profile.radius, radius));
        require(label + " impulse", close(tick.profile.impulse, impulse));
        require(label + " velocity dissipation",
                close(tick.profile.velocityDissipation, velocityDissipation));
        require(label + " density dissipation",
                close(tick.profile.densityDissipation, densityDissipation));
    }

    private static int workerMargin(int mode, int x, int y, int width, int height) {
        if (mode == 1) return 10;
        if (mode == 0 && !(x > 60 && y > 60 && x < width - 60 && y < height - 60)) {
            return 12;
        }
        return 60;
    }

    private static boolean workerAdmitted(int mode, int x, int y, int width, int height) {
        int margin = workerMargin(mode, x, y, width, height);
        return x > margin && y > margin && x < width - margin && y < height - margin;
    }

    private static float jitter(int value) {
        return (0.5f - value * (1.0f / 2147483648.0f)) * 10.0f;
    }

    private static final class Oracle {
        int state;
        int step;
        int lastAction = ACTION_UP;
        int mode;
        int previousX;
        int previousY;
        int currentX;
        int currentY;
        int count;
        int eventX;
        int eventY;
        float backstep = 1.0f;

        void touch(int action, int x, int y) {
            lastAction = action;
            if (action == ACTION_DOWN) {
                state = 1;
                step = 0;
                count = 0;
                previousX = currentX = x;
                previousY = currentY = y;
                eventX = x;
                eventY = y;
                backstep = 1.0f;
                return;
            }
            if (state == 1 && action == ACTION_MOVE) {
                int dx = x - eventX;
                int dy = y - eventY;
                eventX = x;
                eventY = y;
                currentX = x;
                currentY = y;
                if (dx * dx + dy * dy > 4) {
                    state = 2;
                    mode = 0;
                    count = 0;
                }
                return;
            }
            if (state == 2 && action == ACTION_MOVE) {
                int dx = x - eventX;
                int dy = y - eventY;
                eventX = x;
                eventY = y;
                currentX = x;
                currentY = y;
                double distance = Math.sqrt((double) dx * dx + (double) dy * dy);
                mode = distance > 10.0 ? 2 : distance > 2.0 ? 1 : 0;
                return;
            }
            if (state == 2 && action == ACTION_UP) {
                currentX = x;
                currentY = y;
                state = step < 10 && count < 10 ? 1 : 0;
            }
        }

        void forceDragging(int x, int y) {
            state = 2;
            step = 0;
            count = 0;
            mode = 0;
            previousX = currentX = x;
            previousY = currentY = y;
            eventX = x;
            eventY = y;
        }

        Tick tick() {
            int stateBefore = state;
            int stepBefore = step;
            Profile profile = profile();
            boolean inject = false;
            if (state == 1) {
                inject = step < 10;
                if (step < 10) backstep = 0.1f * step;
                else if (step == 10) backstep = 1.0f;
                ++step;
                if (step > 12 && lastAction != ACTION_MOVE) state = 0;
            } else if (state == 2) {
                inject = true;
                ++count;
            }
            boolean depositsDensity = inject && (stateBefore != 2 || mode != 2
                    || previousX != currentX || previousY != currentY);
            Tick result = new Tick(stateBefore, state, stepBefore, step, inject, depositsDensity,
                    profile,
                    stateBefore == 1 ? (stepBefore == 0 ? 1.0f
                            : stepBefore <= 10 ? 0.1f * (stepBefore - 1) : 1.0f) : 1.0f,
                    previousX, previousY, currentX, currentY);
            if (stateBefore == 2) {
                previousX = currentX;
                previousY = currentY;
            }
            return result;
        }

        private Profile profile() {
            if (state == 1) return new Profile(-1, 8.0f * step, 200.0f, 0.94f, 0.92f);
            if (state != 2) return new Profile(-2, 0.0f, 0.0f, 0.0f, 0.0f);
            if (mode == 0) return new Profile(0, 48.0f, 100.0f, 0.80f, 0.94f);
            if (mode == 1) return new Profile(1, 25.0f, 150.0f, 0.94f, 0.92f);
            return new Profile(2, 30.0f, 40.0f, 0.96f, 0.92f);
        }
    }

    private static final class Profile {
        final int mode;
        final float radius;
        final float impulse;
        final float velocityDissipation;
        final float densityDissipation;

        Profile(int mode, float radius, float impulse, float velocityDissipation,
                float densityDissipation) {
            this.mode = mode;
            this.radius = radius;
            this.impulse = impulse;
            this.velocityDissipation = velocityDissipation;
            this.densityDissipation = densityDissipation;
        }
    }

    private static final class Tick {
        final int stateBefore;
        final int stateAfter;
        final int stepBefore;
        final int stepAfter;
        final boolean inject;
        final boolean depositsDensity;
        final Profile profile;
        final float backstep;
        final int previousX;
        final int previousY;
        final int currentX;
        final int currentY;

        Tick(int stateBefore, int stateAfter, int stepBefore, int stepAfter, boolean inject,
                boolean depositsDensity, Profile profile, float backstep, int previousX, int previousY,
                int currentX, int currentY) {
            this.stateBefore = stateBefore;
            this.stateAfter = stateAfter;
            this.stepBefore = stepBefore;
            this.stepAfter = stepAfter;
            this.inject = inject;
            this.depositsDensity = depositsDensity;
            this.profile = profile;
            this.backstep = backstep;
            this.previousX = previousX;
            this.previousY = previousY;
            this.currentX = currentX;
            this.currentY = currentY;
        }
    }

    /** POSIX/Bionic process-default lrand48(), including its 48-bit state. */
    private static final class Lrand48 {
        private static final long MASK = (1L << 48) - 1L;
        private long state = 0x1234abcd330eL;

        Lrand48() {
        }

        int next() {
            state = (0x5deece66dL * state + 0xbL) & MASK;
            return (int) (state >>> 17);
        }
    }

    private static final class WorkerTimeline {
        final List<String> events = new ArrayList<String>();

        void frame(int frame) {
            events.add("join:" + (frame - 1));
            events.add("upload:" + (frame - 1));
            events.add("launch:" + frame + ":pre-profile");
            events.add("advect:" + (frame - 1));
            events.add("profile:" + frame);
            events.add("inject:" + frame);
        }
    }

    private static final class WorkerProfile {
        final int mode;
        final float densityDissipation;

        WorkerProfile(int mode, float densityDissipation) {
            this.mode = mode;
            this.densityDissipation = densityDissipation;
        }
    }

    private static final class WorkerProfileTimeline {
        int persistentMode;
        float densityDissipation = 0.92f;

        void drawStateTwo(int mode) {
            // Update launches using the profile already stored by the preceding draw; only after
            // that does the current state2 recipe become persistent for the following worker.
            persistentMode = mode;
            densityDissipation = 0.92f;
        }

        void release() {
            densityDissipation = 0.90f;
        }

        WorkerProfile drawIdle() {
            // State0 performs Update/advect but writes no replacement source/worker profile.
            return new WorkerProfile(persistentMode, densityDissipation);
        }
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    private static void require(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }

    private RippleInkN3OracleGoldenTraceTest() {
    }
}
