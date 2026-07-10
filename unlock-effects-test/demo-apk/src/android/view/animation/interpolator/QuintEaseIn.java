package android.view.animation.interpolator;

import android.view.animation.Interpolator;

public class QuintEaseIn implements Interpolator {
    @Override
    public float getInterpolation(float input) {
        return input * input * input * input * input;
    }
}
