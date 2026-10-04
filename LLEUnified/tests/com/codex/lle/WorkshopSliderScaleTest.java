package com.codex.lle;

public final class WorkshopSliderScaleTest {
    private static int assertions;

    public static void main(String[] args) {
        WorkshopSliderScale decimals = new WorkshopSliderScale(0, 1, .01f);
        equal(.35f, decimals.valueAt(35), "exact decimal step");
        equal(.36f, decimals.nudge(.35f, 1), "decimal increment");
        equal(.34f, decimals.nudge(.35f, -1), "decimal decrement");
        float repeated = 0;
        for (int i = 0; i < 100; i++) repeated = decimals.nudge(repeated, 1);
        equal(1, repeated, "no accumulated decimal drift");
        equal(.355f, decimals.nudge(.345f, 1), "manual precision preserved");
        WorkshopSliderScale negative = new WorkshopSliderScale(-1, 1, .1f);
        equal(-.7f, negative.valueAt(3), "negative decimal grid");
        equal(-1, negative.nudge(-1, -1), "minimum clamp");
        equal(1, negative.nudge(1, 1), "maximum clamp");
        WorkshopSliderScale partial = new WorkshopSliderScale(0, 1, .3f);
        check(partial.steps == 4, "short final interval");
        equal(1, partial.valueAt(4), "maximum reachable off grid");
        check(partial.progressFor(1) == 4, "maximum thumb position");
        int controls = 0;
        for (int effect = 0; effect <= 44; effect++) {
            for (EffectWorkshopConfig.Parameter p : EffectWorkshopConfig.parametersFor(effect)) {
                WorkshopSliderScale scale = new WorkshopSliderScale(p.min, p.max, p.step);
                controls++;
                check(scale.steps > 0, p.key + " positive range");
                equal(p.min, scale.valueAt(0), p.key + " minimum");
                equal(p.max, scale.valueAt(scale.steps), p.key + " maximum");
                equal(p.min, scale.nudge(p.min, -1), p.key + " lower bound");
                equal(p.max, scale.nudge(p.max, 1), p.key + " upper bound");
                float previous = p.min;
                for (int i = 0; i <= 100; i++) {
                    int progress = (int) (scale.steps * (i / 100.0));
                    float value = scale.valueAt(progress);
                    check(value >= previous && value >= p.min && value <= p.max,
                            p.key + " bounded monotonic grid");
                    check(scale.progressFor(value) == progress, p.key + " grid round trip");
                    if (p.integer) check(value == Math.round(value), p.key + " integer grid");
                    previous = value;
                }
            }
        }
        check(controls == 705, "all workshop controls");
        System.out.println("WorkshopSliderScaleTest: " + assertions
                + " assertions passed across " + controls + " controls");
    }

    private static void equal(float expected, float actual, String message) {
        check(Float.floatToIntBits(expected) == Float.floatToIntBits(actual), message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
