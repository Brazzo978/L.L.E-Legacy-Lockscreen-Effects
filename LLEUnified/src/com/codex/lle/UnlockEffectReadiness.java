package com.codex.lle;

/**
 * Optional readiness contract for unlock renderers whose expensive resources are created only
 * after their View is attached to a real Window. The accessibility service must not equate a
 * successfully constructed Java object with a drawable Surface/EGL pipeline.
 */
interface UnlockEffectReadiness {
    int STATE_FAILED = -1;
    int STATE_DETACHED = 0;
    int STATE_CONSTRUCTED = 1;
    int STATE_ATTACHED = 2;
    int STATE_SURFACE_READY = 3;
    int STATE_RESOURCES_READY = 4;
    int STATE_FIRST_FRAME_READY = 5;

    interface ReadinessListener {
        void onReadinessChanged();
    }

    int getReadinessState();

    String getReadinessDetail();

    void setReadinessListener(ReadinessListener listener);
}
