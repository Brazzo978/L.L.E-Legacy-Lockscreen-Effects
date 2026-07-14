package com.android.internal.policy.impl.keyguard.sec;

import android.graphics.Bitmap;

/** Exact Java ABI expected by Samsung's S3 Water Ripple renderer. */
public final class JniWaterRippleRender {
    static {
        System.loadLibrary("WaterRipple");
    }

    private JniWaterRippleRender() {
    }

    public static native void clearInkValue();
    public static native int getClearInkValue();
    public static native void initWaters(float[] vertices, short[] indices,
            int vertexCount, int meshHeight, int meshWidth,
            int surfaceHeight, int surfaceWidth);
    public static native int move(float[] velocity, float[] height,
            int xBegin, int yBegin, int xEnd, int yEnd,
            int detailWidth, int detailHeight, boolean checkEmpty,
            float damping, float waveCoefficient);
    public static native void onDraw(float[] vertices, float[] heights, short[] indices,
            int vertexFloatCount, int heightFloatCount, int indexCount, float[] matrix,
            int meshWidth, int meshHeight, int detailWidth, int detailHeight,
            float refractiveIndex, float reflectionRatio,
            float alphaRatio1, float alphaRatio2,
            float inkR, float inkG, float inkB,
            float fresnelRatio, float specularRatio, float exponent);
    public static native void onDrawGravity(float[] vertices, float[] heights, short[] indices,
            int vertexFloatCount, int heightFloatCount, int indexCount, float[] matrix,
            int meshWidth, int meshHeight, int detailWidth, int detailHeight,
            float refractiveIndex, float reflectionRatio,
            float alphaRatio1, float alphaRatio2,
            float inkR, float inkG, float inkB,
            float fresnelRatio, float specularRatio, float exponent,
            int gravityMode, float causticTimeRatio, float causticTimeRatio2,
            float causticTimeMix, float referencePoint, float textureMove,
            boolean gravityDirection, float waterBrightness);
    public static native void onFreeBGTextures();
    public static native void onFreeGravityTextures();
    public static native void onFreeWaterTextures();
    public static native void onInitGPU();
    public static native void onInitGPUGravity();
    public static native void onInitSetting(int width, int height, boolean inkMode);
    public static native void onLoadBGTextures();
    public static native void onLoadGravityTextures();
    public static native void onLoadWaterTextures();
    public static native void onTouch(int x, int y, int action, float pressure);
    public static native void ripple(float[] velocity,
            int meshWidth, int meshHeight, int detailWidth, int detailHeight,
            float meshX, float meshY, float strength);
    public static native void transferBGBitmap(Bitmap bitmap);
    public static native void transferGravityBitmap(
            Bitmap gravity, Bitmap caustic1, Bitmap caustic2);
    public static native void transferWaterBitmap(Bitmap bitmap);
}
