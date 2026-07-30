package com.codex.lle;

import android.graphics.Bitmap;

/** Small app-owned contract around the two ABI-compatible Note 5 JNI shims. */
interface Note5PhysicsNative {
    void initJni();
    void deinitJni();
    void initPhysics(int projectKind, int quality, int width, int height);
    void surfaceChanged(int width, int height);
    void draw();
    void touch(int touchId, int touchCount, int eventType, int[] x, int[] y);
    void sensor(int sensorType, float x, float y, float z);
    void texture(String name, Bitmap bitmap);
    void textureColor(String name, Bitmap bitmap);
    void key(int eventId);
    void custom(int eventId, float value);
    void custom(int eventId, float x, float y, float z);
    int isEmpty();
}
