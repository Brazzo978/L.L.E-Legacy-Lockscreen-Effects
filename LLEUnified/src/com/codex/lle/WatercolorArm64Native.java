package com.codex.lle;

/**
 * App-owned JNI surface for the reconstructed ARM64 Watercolor engine.
 *
 * <p>The native state pointer deliberately keeps the historical {@code mEffectId}
 * field name because the reconstructed engine stores its per-instance state there.
 * No Samsung Java class is required by this bridge.</p>
 */
final class WatercolorArm64Native {
    private static final boolean AVAILABLE;

    @SuppressWarnings("unused")
    private long mEffectId;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("secveSrkCommon");
            loaded = true;
        } catch (Throwable ignored) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    native String[] loadEffect(String path);

    native void loadTexture(String name, int[] pixels, int width, int height);

    native void init(int width, int height, boolean force);

    native boolean draw();

    /** Draws one display-refresh frame, advancing feedback by the supplied elapsed time. */
    native boolean drawAdaptive(float elapsedSeconds);

    native void onTouch(int x, int y, int action);

    native void showUnlock();

    native void showAffordance(int x, int y);

    native void clear();

    native void destroy();

    native void setParameters(int[] numbers, float[] values);

    native void loadModel(String name, byte[] bytes);

    static native void pauseAnimation();

    static native void resumeAnimation();
}
