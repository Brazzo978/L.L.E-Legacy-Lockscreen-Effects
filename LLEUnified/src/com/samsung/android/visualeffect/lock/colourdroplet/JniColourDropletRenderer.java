package com.samsung.android.visualeffect.lock.colourdroplet;

import android.graphics.Bitmap;

/**
 * ABI bridge retained at the exact class name registered by the Note 5 native engine.
 *
 * <p>The public methods deliberately mirror Samsung's original Java shim while the native
 * declarations remain static and keep their original signatures.  The implementation contains
 * no rendering logic: it only owns the native state pointer and forwards calls.</p>
 */
public final class JniColourDropletRenderer {
    private long mNativeJNI = -1L;

    static {
        System.loadLibrary("ColourDropletEffect");
    }

    private static native long native_Init_JNI();
    private static native void native_DeInit_JNI(long nativeJni);
    private static native void native_Init_PhysicsEngine(
            long nativeJni, int tabletMode, int quality, int width, int height);
    private static native void native_onSurfaceChangedEvent(
            long nativeJni, int width, int height);
    private static native void native_Draw_PhysicsEngine(long nativeJni);
    private static native void native_onTouchEvent(
            long nativeJni, int touchId, int touchCount, int eventType, int[] x, int[] y);
    private static native void native_onSensorEvent(
            long nativeJni, int sensorType, float x, float y, float z);
    private static native void native_SetTexture(
            long nativeJni, String textureName, Bitmap bitmap);
    private static native void native_SetTextureColor(
            long nativeJni, String textureName, Bitmap bitmap);
    private static native void native_onKeyEvent(long nativeJni, int keyId);
    private static native void native_onCustomEvent(long nativeJni, int keyId, float value);
    private static native void native_onCustomEventVec(
            long nativeJni, int keyId, float x, float y, float z);
    private static native int native_isEmpty(long nativeJni);

    public void Init_PhysicsEngineJNI() {
        mNativeJNI = native_Init_JNI();
    }

    public void DeInit_PhysicsEngineJNI() {
        if (mNativeJNI != -1L) {
            native_DeInit_JNI(mNativeJNI);
            mNativeJNI = -1L;
        }
    }

    public void Init_PhysicsEngine(int tabletMode, int quality, int width, int height) {
        native_Init_PhysicsEngine(mNativeJNI, tabletMode, quality, width, height);
    }

    public void onSurfaceChangedEvent(int width, int height) {
        native_onSurfaceChangedEvent(mNativeJNI, width, height);
    }

    public void Draw_PhysicsEngine() {
        native_Draw_PhysicsEngine(mNativeJNI);
    }

    public void onTouchEvent(
            int touchId, int touchCount, int eventType, int[] x, int[] y) {
        native_onTouchEvent(mNativeJNI, touchId, touchCount, eventType, x, y);
    }

    public void onSensorEvent(int sensorType, float x, float y, float z) {
        native_onSensorEvent(mNativeJNI, sensorType, x, y, z);
    }

    public void SetTexture(String textureName, Bitmap bitmap) {
        native_SetTexture(mNativeJNI, textureName, bitmap);
    }

    public void SetTextureColor(String textureName, Bitmap bitmap) {
        native_SetTextureColor(mNativeJNI, textureName, bitmap);
    }

    public void onKeyEvent(int keyId) {
        native_onKeyEvent(mNativeJNI, keyId);
    }

    public void onCustomEvent(int keyId, float value) {
        native_onCustomEvent(mNativeJNI, keyId, value);
    }

    public void onCustomEvent(int keyId, float x, float y, float z) {
        native_onCustomEventVec(mNativeJNI, keyId, x, y, z);
    }

    public int isEmpty() {
        return native_isEmpty(mNativeJNI);
    }
}
