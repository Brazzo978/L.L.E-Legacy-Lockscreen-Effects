package com.codex.lle;

/**
 * Lazy JNI bridge for the independently implemented Note 3 ENB4 velocity worker.
 *
 * <p>This does not render or own density textures.  Each {@link #nativeStep} returns the
 * completed N-1 RGBA8 velocity surface, then launches the N worker.  The caller uploads that
 * array, advects density, optionally performs AddInk, and invokes this bridge for the next
 * fixed 60 Hz logical tick. Coordinates are raw MotionEvent pixels (top-left origin).</p>
 */
final class N3RippleInkWorkerNative {
    static final int BRIDGE_VERSION = 1;

    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("lleN3RippleInk");
            loaded = true;
        } catch (LinkageError error) {
            loaded = false;
        } catch (SecurityException exception) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private N3RippleInkWorkerNative() {
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

    /**
     * Creates a worker for an explicit screen/12 velocity grid. The two sizes must remain fixed
     * for the handle lifetime; recreate after a surface-size change.
     */
    static native long nativeCreate(
            int velocityWidth, int velocityHeight, int screenWidth, int screenHeight);

    /** Joins a pending worker and clears only velocity/pressure state, not GLES density. */
    static native void nativeReset(long handle);

    /**
     * Returns the completed N-1 velocity surface, packed (vx-hi, vx-lo, vy-hi, vy-lo), then
     * launches the current ENB4 worker. Profile values must be the exact onDraw profile selected
     * for this current source: velocity dissipation, divergence radius/strength and whether
     * state -1 is allowed through the projection gate.
     */
    static native byte[] nativeStep(
            long handle,
            int mode,
            float currentX,
            float currentYTop,
            float previousX,
            float previousYTop,
            float velocityDissipation,
            float divergenceRadius,
            float divergenceStrength,
            boolean forceProjection);

    /** Joins any pending worker before releasing the stateful handle. */
    static native void nativeDestroy(long handle);
}
