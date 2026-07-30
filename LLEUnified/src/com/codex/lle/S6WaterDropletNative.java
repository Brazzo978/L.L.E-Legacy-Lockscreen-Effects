package com.codex.lle;

import android.graphics.Bitmap;

interface S6WaterDropletNative {
    void initJni();
    void deinitJni();
    void initPhysics(int project, int quality, int width, int height);
    void surfaceChanged(int width, int height);
    void draw();
    void touch(int id, int count, int type, int[] x, int[] y);
    void sensor(int type, float x, float y, float z);
    void texture(String name, Bitmap bitmap);
    void textureColor(String name, Bitmap bitmap);
    void key(int event);
    void custom(int event, float value);
    void custom(int event, float x, float y, float z);
    int isEmpty();
}
