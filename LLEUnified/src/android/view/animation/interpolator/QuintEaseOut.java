package android.view.animation.interpolator;

import android.view.animation.Interpolator;

/** Samsung framework compatibility interpolator used by the original Blind DEX. */
public class QuintEaseOut implements Interpolator {
    @Override
    public float getInterpolation(float input) {
        float t = input - 1f;
        return t * t * t * t * t + 1f;
    }
}
