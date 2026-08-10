package com.codex.lle;

import android.graphics.Bitmap;

/** Lazy JNI boundary for LLE's app-owned Galaxy S6 Water Droplet renderer. */
final class S6WaterDropletAppOwnedNative {
    static final int TEXTURE_PORTRAIT_BACKGROUND = 0;
    static final int TEXTURE_LANDSCAPE_BACKGROUND = 1;
    static final int TEXTURE_NORMAL = 2;
    static final int TEXTURE_EDGE_DENSITY = 3;
    static final int BRIDGE_VERSION = 3;

    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("lleS6WaterDroplet");
            loaded = true;
        } catch (LinkageError error) {
            loaded = false;
        } catch (SecurityException exception) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private S6WaterDropletAppOwnedNative() {
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

    static native long nativeCreate(
            int projectKind, int quality, long deterministicSeed);

    static native boolean nativeInitGpu(long handle, int width, int height);

    static native boolean nativeResize(
            long handle,
            int width,
            int height,
            int logicalShortSide,
            int logicalLongSide);

    static native void nativeAbandonGpu(long handle);

    static native void nativeDestroy(long handle);

    static native boolean nativeUploadBitmap(long handle, int slot, Bitmap bitmap);

    static native void nativeClearBitmap(long handle, int slot);

    static native void nativeReset(long handle);

    static native void nativeTouch(
            long handle,
            int eventType,
            float screenX,
            float screenY,
            long eventTimeMs);

    static native void nativeTilt(
            long handle, float mappedX, float mappedY, long sampleTimeNanos);

    static native void nativeAffordance(long handle, float screenX, float screenY);

    static native void nativeUnlock(long handle);

    static native void nativeResetBackgroundScale(long handle);

    /** Advances exactly one stock 60 Hz simulation tick. */
    static native boolean nativeStep(long handle);

    /**
     * Experimental display-clock step. {@code frameScale} is elapsed time in
     * 60 Hz ticks and is deliberately supplied by the GL host per presented
     * frame, not inferred from a nominal refresh-rate setting.
     */
    static native boolean nativeStepNativeRefresh(long handle, float frameScale);

    static native boolean nativeDraw(
            long handle, int width, int height, float presentationFraction);

    static native boolean nativeIsIdle(long handle);

    static native String nativeGetLastError(long handle);
}
