package android.view.animation.interpolator;

import android.view.animation.Interpolator;

/** Samsung framework compatibility interpolator used by the original Blind DEX. */
public class QuadEaseIn implements Interpolator {
    @Override
    public float getInterpolation(float input) {
        return input * input;
    }
}
