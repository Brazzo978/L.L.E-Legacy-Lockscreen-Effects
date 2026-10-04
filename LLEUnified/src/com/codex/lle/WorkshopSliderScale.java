package com.codex.lle;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal slider arithmetic, independent of Android and renderer settings. */
final class WorkshopSliderScale {
    private final BigDecimal min, max, step;
    final int steps;

    WorkshopSliderScale(float minimum, float maximum, float increment) {
        min = decimal(minimum);
        max = decimal(maximum);
        step = decimal(increment);
        if (step.signum() <= 0 || max.compareTo(min) <= 0) {
            throw new IllegalArgumentException("Invalid slider range");
        }
        steps = max.subtract(min).divide(step, 0, RoundingMode.CEILING).intValueExact();
    }

    float valueAt(int progress) {
        return bound(min.add(step.multiply(BigDecimal.valueOf(
                Math.max(0, Math.min(steps, progress))))));
    }

    int progressFor(float value) {
        // The final interval can be shorter than one step.
        if (value >= max.floatValue()) return steps;
        return Math.max(0, Math.min(steps, decimal(value).subtract(min)
                .divide(step, 0, RoundingMode.HALF_UP).intValue()));
    }

    float nudge(float value, int direction) {
        return bound(decimal(value).add(step.multiply(BigDecimal.valueOf(direction))));
    }

    private float bound(BigDecimal value) {
        return value.max(min).min(max).floatValue();
    }

    private static BigDecimal decimal(float value) {
        return new BigDecimal(Float.toString(value));
    }
}
