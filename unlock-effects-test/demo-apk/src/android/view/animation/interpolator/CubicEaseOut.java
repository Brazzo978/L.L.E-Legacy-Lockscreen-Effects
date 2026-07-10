package android.view.animation.interpolator;

import android.view.animation.Interpolator;

public class CubicEaseOut implements Interpolator {
    @Override
    public float getInterpolation(float input) {
        float t = input - 1f;
        return t * t * t + 1f;
    }
}
