package android.view.animation.interpolator;

import android.view.animation.Interpolator;

public class CubicEaseIn implements Interpolator {
    @Override
    public float getInterpolation(float input) {
        return input * input * input;
    }
}
