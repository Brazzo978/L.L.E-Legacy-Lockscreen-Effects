package com.codex.lle;

/** Ephemeral, timed input state for the hidden customization menu. */
final class KonamiCode {
    static final int UP = 1;
    static final int DOWN = 2;
    static final int LEFT = 3;
    static final int RIGHT = 4;
    static final int B = 5;
    static final int A = 6;
    static final int START = 7;
    static final int SELECT = 8;
    static final int TOTAL_STEPS = 11;
    static final long MAX_GAP_MS = 2000L;
    static final long BUTTON_GAP_MS = 10000L;

    private static final int[] SEQUENCE = {
            UP, UP, DOWN, DOWN, LEFT, RIGHT, LEFT, RIGHT, B, A, START
    };

    private int progress;
    private long lastCorrectAt;

    int progress() {
        return progress;
    }

    long timeoutMs() {
        return progress >= 8 ? BUTTON_GAP_MS : MAX_GAP_MS;
    }

    int expire(long now) {
        if (progress == 0 || now - lastCorrectAt <= timeoutMs()) {
            return 0;
        }
        int completed = progress;
        reset();
        return completed;
    }

    Result input(int step, long now) {
        return input(step, now, now);
    }

    Result input(int step, long startedAt, long completedAt) {
        int expired = expire(startedAt);
        if (expired > 0) {
            return new Result(false, expired, false);
        }
        if (step == SEQUENCE[progress]) {
            progress++;
            lastCorrectAt = completedAt;
            if (progress == TOTAL_STEPS) {
                reset();
                return new Result(true, TOTAL_STEPS, true);
            }
            return new Result(true, progress, false);
        }
        if (progress == 0) {
            return new Result(false, 0, false);
        }
        int completed = progress;
        reset();
        return new Result(false, completed, false);
    }

    void reset() {
        progress = 0;
        lastCorrectAt = 0L;
    }

    static final class Result {
        final boolean accepted;
        final int completedSteps;
        final boolean unlocked;

        Result(boolean accepted, int completedSteps, boolean unlocked) {
            this.accepted = accepted;
            this.completedSteps = completedSteps;
            this.unlocked = unlocked;
        }
    }
}
