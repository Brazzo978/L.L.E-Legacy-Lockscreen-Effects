package com.codex.chargingtouchtest;

import android.view.View;

interface UnlockEffectRenderer {
    View asView();

    String effectName();

    void beginGesture(float screenX, float screenY);

    void updateGesture(float screenX, float screenY);

    void finishGesture(boolean completed);

    void cancelGesture();

    void resetEffect();

    void warmUp();

    void destroy();
}
