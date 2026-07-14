package android.view.animation.interpolator;

import android.animation.TimeInterpolator;

public class CubicEaseOut implements TimeInterpolator {
    @Override
    public float getInterpolation(float input) {
        float t = input - 1f;
        return t * t * t + 1f;
    }
}

