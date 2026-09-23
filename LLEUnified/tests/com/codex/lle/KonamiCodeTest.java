package com.codex.lle;

/** Deterministic completion, wrong-step and timeout tests for the hidden code. */
public final class KonamiCodeTest {
    private KonamiCodeTest() {
    }

    public static void main(String[] args) {
        int[] steps = {KonamiCode.UP, KonamiCode.UP, KonamiCode.DOWN,
                KonamiCode.DOWN, KonamiCode.LEFT, KonamiCode.RIGHT,
                KonamiCode.LEFT, KonamiCode.RIGHT, KonamiCode.B,
                KonamiCode.A, KonamiCode.START};
        KonamiCode code = new KonamiCode();
        long now = 10000L;
        for (int index = 0; index < steps.length; index++) {
            KonamiCode.Result result = code.input(steps[index], now += 500L);
            if (!result.accepted || result.completedSteps != index + 1
                    || result.unlocked != (index == steps.length - 1)) {
                throw new AssertionError("step " + (index + 1));
            }
        }
        if (code.progress() != 0) {
            throw new AssertionError("completion must reset state");
        }
        code.input(KonamiCode.UP, now += 100L);
        KonamiCode.Result wrong = code.input(KonamiCode.DOWN, now += 100L);
        if (wrong.accepted || wrong.completedSteps != 1 || code.progress() != 0) {
            throw new AssertionError("wrong second input must report 1/11 and reset");
        }
        code.input(KonamiCode.UP, now += 100L);
        if (code.expire(now + KonamiCode.MAX_GAP_MS) != 0
                || code.expire(now + KonamiCode.MAX_GAP_MS + 1L) != 1
                || code.progress() != 0) {
            throw new AssertionError("gap timeout must report previous progress");
        }
        code.input(KonamiCode.UP, now += 100L);
        KonamiCode.Result atLimit = code.input(KonamiCode.UP,
                now + KonamiCode.MAX_GAP_MS, now + KonamiCode.MAX_GAP_MS + 200L);
        if (!atLimit.accepted || atLimit.completedSteps != 2) {
            throw new AssertionError("swipe begun at 2 s must count even if it ends later");
        }
        code.reset();
        now += KonamiCode.MAX_GAP_MS + 200L;
        for (int index = 0; index < 10; index++) {
            code.input(steps[index], now += 100L);
        }
        wrong = code.input(KonamiCode.SELECT, now += 100L);
        if (wrong.accepted || wrong.completedSteps != 10 || code.progress() != 0) {
            throw new AssertionError("Select must not substitute for Start");
        }
        for (int index = 0; index < 8; index++) {
            code.input(steps[index], now += 100L);
        }
        if (code.timeoutMs() != KonamiCode.BUTTON_GAP_MS
                || code.expire(now + KonamiCode.BUTTON_GAP_MS) != 0) {
            throw new AssertionError("A/B stage must allow a full 10 s gap");
        }
        for (int index = 8; index < steps.length; index++) {
            now += KonamiCode.BUTTON_GAP_MS;
            KonamiCode.Result result = code.input(steps[index], now);
            if (!result.accepted || result.unlocked != (index == steps.length - 1)) {
                throw new AssertionError("button stage " + index + " must allow 10 s");
            }
        }
        for (int index = 0; index < 8; index++) {
            code.input(steps[index], now += 100L);
        }
        if (code.expire(now + KonamiCode.BUTTON_GAP_MS + 1L) != 8
                || code.progress() != 0) {
            throw new AssertionError("A/B stage must expire after 10 s");
        }
    }
}
