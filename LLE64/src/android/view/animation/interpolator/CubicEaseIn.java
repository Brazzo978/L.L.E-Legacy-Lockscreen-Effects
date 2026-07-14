package android.view.animation.interpolator;

import android.animation.TimeInterpolator;

public class CubicEaseIn implements TimeInterpolator {
    @Override
    public float getInterpolation(float input) {
        return input * input * input;
    }
}
