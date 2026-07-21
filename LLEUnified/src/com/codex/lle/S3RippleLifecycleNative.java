package com.codex.lle;

import android.graphics.Bitmap;

/**
 * App-owned JNI boundary for the ARM64 Water Ripple GLES port.
 *
 * <p>This deliberately does not reuse Samsung's process-global bitmap pointer ABI. Bitmap
 * pixels are locked, uploaded synchronously and unlocked inside one native call.</p>
 */
final class S3RippleLifecycleNative {
    static final int TEXTURE_BACKGROUND = 0;
    static final int TEXTURE_WATER = 1;
    static final int BRIDGE_VERSION = 3;
    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("WaterRipple");
            loaded = true;
        } catch (LinkageError error) {
            loaded = false;
        } catch (SecurityException exception) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private S3RippleLifecycleNative() {
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

    /** Initializes a zeroed GLES state. A GLES2 context must be current. */
    static native boolean nativeInitGpu();

    /** Drops names from a lost context without issuing GL deletes. */
    static native void nativeAbandonGpu();

    /** Deletes all resources in the current GLES2 context and zeros the state. */
    static native void nativeDestroyGpu();

    /** Creates the stock Indigo 1024x512 (orientation-adjusted) density surfaces. */
    static native boolean nativeInitInk(int viewportWidth, int viewportHeight);

    static native void nativeResetInk();

    static native boolean nativeAdvanceInk(float centerX, float centerY, int drag);

    static native boolean nativeInjectInk(
            float currentX,
            float currentY,
            float previousX,
            float previousY,
            int mode);

    /** Locks, uploads and unlocks {@code bitmap} synchronously on the GL thread. */
    static native boolean nativeUploadBitmap(int slot, Bitmap bitmap);

    static native void nativeFreeTexture(int slot);

    /** Renders the local premultiplied normal-mode overlay, not Samsung's opaque framebuffer. */
    static native boolean nativeRender(
            boolean withInk,
            float[] vertices,
            float[] heights,
            short[] indices,
            float[] mvp,
            int viewportWidth,
            int viewportHeight,
            int meshWidth,
            int meshHeight,
            int detailWidth,
            int detailHeight,
            float refractiveIndex,
            float reflectionRatio,
            float alphaRatio1,
            float alphaRatio2,
            float fresnelRatio,
            float specularRatio,
            float exponentRatio);

    static native String nativeGetLastError();
}
