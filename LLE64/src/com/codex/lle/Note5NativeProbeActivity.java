package com.codex.lle;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.Method;

public final class Note5NativeProbeActivity extends Activity {
    private static final String TAG = "LLE64NativeProbe";
    private static final String DROPLET =
            "com.samsung.android.visualeffect.lock.colourdroplet.JniColourDropletRenderer";
    private static final String BUBBLES =
            "com.samsung.android.visualeffect.lock.sparklingbubbles.JniSparklingBubblesRenderer";
    private static final String WATER_RIPPLE =
            "com.android.internal.policy.impl.keyguard.sec.JniWaterRippleRender";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String requested = getIntent().getStringExtra("effect");
        StringBuilder result = new StringBuilder("LLE64 native JNI probe\n");
        if ("ripple-core".equals(requested)) {
            probeRippleCore(result);
        }
        if (requested == null || "droplet".equals(requested) || "all".equals(requested)) {
            probeClass(DROPLET, result);
        }
        if (requested == null || "bubbles".equals(requested) || "all".equals(requested)) {
            probeClass(BUBBLES, result);
        }
        TextView view = new TextView(this);
        view.setText(result.toString());
        view.setTextColor(Color.BLACK);
        view.setTextSize(16f);
        view.setPadding(32, 64, 32, 32);
        setContentView(view);
    }

    private void probeRippleCore(StringBuilder result) {
        try {
            Class<?> cls = Class.forName(WATER_RIPPLE, true, getClassLoader());
            Method initWaters = cls.getMethod("initWaters", float[].class, short[].class,
                    int.class, int.class, int.class, int.class, int.class);
            Method ripple = cls.getMethod("ripple", float[].class,
                    int.class, int.class, int.class, int.class,
                    float.class, float.class, float.class);
            Method move = cls.getMethod("move", float[].class, float[].class,
                    int.class, int.class, int.class, int.class, int.class, int.class,
                    boolean.class, float.class, float.class);

            float[] vertices = new float[27];
            short[] indices = new short[24];
            initWaters.invoke(null, vertices, indices, 9, 4, 6, 3, 3);
            short[] expectedIndices = {
                    0, 1, 4, 0, 4, 3,
                    1, 2, 5, 1, 5, 4,
                    3, 4, 7, 3, 7, 6,
                    4, 5, 8, 4, 8, 7
            };
            requireClose(vertices[0], -3f, "mesh first x");
            requireClose(vertices[1], 2f, "mesh first y");
            requireClose(vertices[24], 3f, "mesh last x");
            requireClose(vertices[25], -2f, "mesh last y");
            for (int i = 0; i < expectedIndices.length; i++) {
                if (indices[i] != expectedIndices[i]) {
                    throw new AssertionError("mesh index " + i + "=" + indices[i]);
                }
            }

            final int detail = 104;
            float[] velocity = new float[detail * detail];
            float[] height = new float[detail * detail];
            ripple.invoke(null, velocity, 50, 50, detail, detail, 0f, 0f, 2f);
            int center = 52 + 52 * detail;
            requireClose(velocity[center], 6f, "ripple center");
            requireClose(velocity[center + 1], 4f, "ripple right");
            requireClose(velocity[center + detail], 4f, "ripple down");
            Object empty = move.invoke(null, velocity, height,
                    2, 2, 102, 102, detail, detail, true, 0.94f, 0.5f);
            if (!(empty instanceof Integer) || ((Integer) empty).intValue() != 0) {
                throw new AssertionError("active ripple returned empty=" + empty);
            }

            result.append("PASS Water Ripple ARM64 JNI core\n");
            Log.i(TAG, "PASS Water Ripple ARM64 initWaters/ripple/move through ART");
        } catch (Throwable failure) {
            result.append("FAIL Water Ripple ARM64 JNI core: ")
                    .append(failure).append("\n");
            Log.e(TAG, "FAIL Water Ripple ARM64 JNI core", failure);
        }
    }

    private static void requireClose(float actual, float expected, String label) {
        if (Math.abs(actual - expected) > 0.00001f) {
            throw new AssertionError(label + "=" + actual + " expected=" + expected);
        }
    }

    private void probeClass(String className, StringBuilder result) {
        try {
            Class.forName(className, true, getClassLoader());
            result.append("PASS ").append(className).append("\n");
            Log.i(TAG, "PASS class-load/JNI_OnLoad/RegisterNatives " + className);
        } catch (Throwable failure) {
            result.append("FAIL ").append(className).append(": ")
                    .append(failure).append("\n");
            Log.e(TAG, "FAIL " + className, failure);
        }
    }
}
