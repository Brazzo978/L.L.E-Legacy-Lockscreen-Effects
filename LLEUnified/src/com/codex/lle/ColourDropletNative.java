package com.codex.lle;

import android.graphics.Bitmap;

/** Lazy JNI boundary for LLE's app-owned ARM64 Coloured Droplet renderer. */
final class ColourDropletNative {
    static final int TEXTURE_BACKGROUND = 0;
    static final int TEXTURE_NORMAL = 1;
    static final int TEXTURE_EDGE_DENSITY = 2;
    static final int BRIDGE_VERSION = 2;

    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("lleColourDroplet");
            loaded = true;
        } catch (LinkageError error) {
            loaded = false;
        } catch (SecurityException exception) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private ColourDropletNative() {
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

    /** Allocates CPU/simulation state. Project kind is 0 for phone or 1 for tablet. */
    static native long nativeCreate(int projectKind);

    /**
     * Creates GLES resources. A GLES2 context must be current.
     *
     * <p>The logical dimensions retain Samsung's original short/long-side
     * initialization contract independently from the current surface orientation.</p>
     */
    static native boolean nativeInitGpu(
            long handle, int width, int height, int logicalWidth, int logicalHeight);

    /** Resizes resources while keeping the current GLES context and simulation state. */
    static native boolean nativeResize(long handle, int width, int height);

    /** Forgets names belonging to a lost GLES context without issuing GL calls. */
    static native void nativeAbandonGpu(long handle);

    /** Releases CPU state after GLES resources have been released or abandoned. */
    static native void nativeDestroy(long handle);

    static native boolean nativeUploadBitmap(long handle, int slot, Bitmap bitmap);

    static native void nativeClearBitmap(long handle, int slot);

    static native void nativeReset(long handle);

    /**
     * Delivers Samsung touch event types: 0 down, 2 move, and 1 release/cancel.
     * Coordinates are absolute screen pixels.
     */
    static native void nativeTouch(
            long handle, int eventType, float screenX, float screenY, long eventTimeMs);

    static native void nativeSensor(
            long handle, int sensorType, float x, float y, float z);

    static native void nativeAffordance(long handle, float centerX, float centerY);

    static native void nativeUnlock(long handle);

    static native void nativeResetBackgroundScale(long handle);

    static native boolean nativeStep(long handle, float elapsedSeconds);

    /**
     * Experimental native-refresh physics. The native bridge accepts only
     * 30–144 Hz and scales recovered 60 Hz coefficients by elapsed time;
     * multiplier is experimental and valid only from 1.0 through 2.0.
     */
    static native boolean nativeStepAtRefresh(
            long handle, float elapsedSeconds, int physicsHz, float speedMultiplier);

    static native boolean nativeDraw(long handle, int width, int height);

    static native boolean nativeIsIdle(long handle);

    static native String nativeGetLastError(long handle);
}
