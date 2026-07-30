package com.samsung.android.visualeffect.lock.waterdroplet;

import android.graphics.Bitmap;

/**
 * JNI contract registered by the stock Galaxy S6 Edge libWaterDropletEffect.so.
 *
 * <p>The package, class name, method names and signatures are ABI and must not be changed.</p>
 */
public final class JniWaterDropletRenderer {
    private static native void native_DeInit_JNI();
    private static native void native_Draw_PhysicsEngine();
    private static native void native_Init_JNI();
    private static native void native_Init_PhysicsEngine(
            int tabletMode, int quality, int width, int height);
    private static native void native_SetTexture(String textureName, Bitmap bitmap);
    private static native void native_SetTextureColor(String textureName, Bitmap bitmap);
    private static native int native_isEmpty();
    private static native void native_onCustomEvent(int keyId, float value);
    private static native void native_onCustomEventVec(
            int keyId, float xValue, float yValue, float zValue);
    private static native void native_onKeyEvent(int keyId);
    private static native void native_onSensorEvent(
            int sensorType, float xValue, float yValue, float zValue);
    private static native void native_onSurfaceChangedEvent(int width, int height);
    private static native void native_onTouchEvent(
            int touchId, int touchCount, int eventType, int[] x, int[] y);

    static {
        System.loadLibrary("WaterDropletEffect");
    }

    public void Init_PhysicsEngineJNI() {
        native_Init_JNI();
    }

    public void DeInit_PhysicsEngineJNI() {
        native_DeInit_JNI();
    }

    public void Init_PhysicsEngine(
            int tabletMode, int quality, int width, int height) {
        native_Init_PhysicsEngine(tabletMode, quality, width, height);
    }

    public void onSurfaceChangedEvent(int width, int height) {
        native_onSurfaceChangedEvent(width, height);
    }

    public void Draw_PhysicsEngine() {
        native_Draw_PhysicsEngine();
    }

    public void onTouchEvent(
            int touchId, int touchCount, int eventType, int[] x, int[] y) {
        native_onTouchEvent(touchId, touchCount, eventType, x, y);
    }

    public void onSensorEvent(
            int sensorType, float xValue, float yValue, float zValue) {
        native_onSensorEvent(sensorType, xValue, yValue, zValue);
    }

    public void SetTexture(String textureName, Bitmap bitmap) {
        native_SetTexture(textureName, bitmap);
    }

    public void SetTextureColor(String textureName, Bitmap bitmap) {
        native_SetTextureColor(textureName, bitmap);
    }

    public void onKeyEvent(int keyId) {
        native_onKeyEvent(keyId);
    }

    public void onCustomEvent(int keyId, float value) {
        native_onCustomEvent(keyId, value);
    }

    public void onCustomEvent(
            int keyId, float xValue, float yValue, float zValue) {
        native_onCustomEventVec(keyId, xValue, yValue, zValue);
    }

    public int isEmpty() {
        return native_isEmpty();
    }
}
