package android.view.animation.interpolator;

import android.view.animation.Interpolator;

public class QuadEaseIn implements Interpolator {
    @Override
    public float getInterpolation(float input) {
        return input * input;
    }
}
