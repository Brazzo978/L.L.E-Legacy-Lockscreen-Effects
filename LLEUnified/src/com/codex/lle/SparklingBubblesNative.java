package com.codex.lle;

import android.graphics.Bitmap;

/** Lazy JNI boundary for LLE's app-owned ARM64 Sparkling Bubbles renderer. */
final class SparklingBubblesNative {
    static final int TEXTURE_BACKGROUND = 0;
    static final int TEXTURE_BLUR_MASK = 1;
    static final int BRIDGE_VERSION = 2;

    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("lleSparklingBubbles");
            loaded = true;
        } catch (LinkageError error) {
            loaded = false;
        } catch (SecurityException exception) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private SparklingBubblesNative() {
    }

    static boolean isAvailable() {
        if (!LIBRARY_LOADED) {
            return false;
        }
        try {
            return nativeBridgeVersion() == BRIDGE_VERSION;
        } catch (LinkageError error) {
            return false;
        }
    }

    static native int nativeBridgeVersion();

    static native long nativeCreate(long seed);

    /** Creates GLES resources. A GLES2 context must be current. */
    static native boolean nativeInitGpu(long handle, int width, int height);

    /** Forgets names belonging to a lost GLES context without issuing GL calls. */
    static native void nativeAbandonGpu(long handle);

    /** Releases CPU state after GLES state has been released or abandoned. */
    static native void nativeDestroy(long handle);

    static native boolean nativeUploadBitmap(long handle, int slot, Bitmap bitmap);

    static native void nativeClearBitmap(long handle, int slot);

    static native void nativeReset(long handle);

    static native void nativeTouch(
            long handle, int action, float x, float y, long eventTimeMs);

    static native void nativeAffordance(
            long handle, int left, int top, int right, int bottom);

    static native void nativeUnlock(long handle);

    /** Latches the renderer mode before touch events reach the native core. */
    static native void nativeSetAdaptivePhysics(long handle, boolean enabled);

    static native boolean nativeStep(long handle, float elapsedSeconds);

    /**
     * Experimental display-refresh-driven physics. The default nativeStep()
     * path remains the recovered fixed 60 Hz simulation.
     */
    static native boolean nativeStepAdaptive(
            long handle, float elapsedSeconds, float speedMultiplier);

    static native boolean nativeDraw(long handle, int width, int height);

    static native boolean nativeIsIdle(long handle);

    static native String nativeGetLastError(long handle);
}
