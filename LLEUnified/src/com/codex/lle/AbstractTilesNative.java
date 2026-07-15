package com.codex.lle;

import android.graphics.Bitmap;

/** Lazy, app-owned JNI boundary for the reconstructed ARM64 Abstract Tiles renderer. */
final class AbstractTilesNative {
    static final int TEXTURE_BACKGROUND = 0;
    static final int TEXTURE_LINE_MASK = 1;
    static final int BRIDGE_VERSION = 1;

    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            // Deliberately reached only from the ARM64 Abstract Tiles renderer branch.
            System.loadLibrary("secveAbstractTile");
            loaded = true;
        } catch (LinkageError error) {
            loaded = false;
        } catch (SecurityException exception) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private AbstractTilesNative() {
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

    /** Creates or resizes GPU state. A GLES2 context must be current. */
    static native boolean nativeInitGpu(int width, int height);

    /** Forgets names belonging to a lost GLES context while preserving CPU simulation state. */
    static native void nativeAbandonGpu();

    /** Releases all native state; implementations must tolerate there being no current context. */
    static native void nativeDestroyGpu();

    /** Locks, uploads and unlocks a bitmap synchronously on the GL thread. */
    static native boolean nativeUploadBitmap(int slot, Bitmap bitmap);

    static native void nativeClearBitmap(int slot);

    static native void nativeReset();

    /** Receives Android action values and panel-local, raw pixel coordinates. */
    static native void nativeTouch(int action, float x, float y, long eventTimeMs);

    /** Changes only the current MOVE anchor after multi-touch suppression. */
    static native void nativeRealign(float x, float y, long eventTimeMs);

    static native void nativeAffordance(int left, int top, int right, int bottom);

    static native void nativeUnlock();

    /** Advances the monotonic animation timeline by the supplied wall-clock interval. */
    static native boolean nativeStep(float elapsedSeconds);

    /** Draws the current transparent overlay without advancing simulation. */
    static native boolean nativeDraw(int width, int height);

    static native boolean nativeIsIdle();

    static native String nativeGetLastError();
}
