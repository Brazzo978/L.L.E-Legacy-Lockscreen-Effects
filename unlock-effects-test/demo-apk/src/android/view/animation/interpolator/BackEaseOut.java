package android.view.animation.interpolator;

import android.view.animation.Interpolator;

public class BackEaseOut implements Interpolator {
    private final float tension;

    public BackEaseOut() {
        this(1.70158f);
    }

    public BackEaseOut(float tension) {
        this.tension = tension;
    }

    @Override
    public float getInterpolation(float input) {
        float t = input - 1f;
        return t * t * ((tension + 1f) * t + tension) + 1f;
    }
}
