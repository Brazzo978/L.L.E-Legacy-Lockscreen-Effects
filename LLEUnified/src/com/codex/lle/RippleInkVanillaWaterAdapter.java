package com.codex.lle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Water-only bridge for Ripple Ink.
 *
 * <p>Production uses the already packaged {@code libWaterRipple.so} through its original
 * {@code JniWaterRippleRender} ABI.  The reflection boundary deliberately keeps this small
 * host-testable module free of an Android dependency: the same exact contract is selected on
 * device, while the scalar mirror below is used only when that class/library is unavailable
 * (for host JVM tests).  Ink density, its velocity worker and palette rendering do not belong
 * here.</p>
 */
final class RippleInkVanillaWaterAdapter {
    private static final String JNI_CLASS =
            "com.android.internal.policy.impl.keyguard.sec.JniWaterRippleRender";

    private static final float IDLE_VELOCITY = 0.01f;
    private static final float MIN_HEIGHT = -100.0f;
    private static final float MAX_HEIGHT = 100.0f;
    private static final float STOCK_DAMPING = 0.94f;
    private static final float STOCK_EXTRA_SMOOTHING = 0.068f;
    private static final float OTHER_EXTRA_SMOOTHING = 0.018f;
    private static final float MAX_ADAPTIVE_TICKS = 4.0f;

    private final float[] vertices;
    private final short[] indices;
    private final float[] heights;
    private final float[] velocity;
    private final float[] gpuHeights;
    private final Backend backend;

    RippleInkVanillaWaterAdapter(
            int detailWidth,
            int detailHeight,
            int surfaceWidth,
            int surfaceHeight) {
        vertices = new float[surfaceWidth * surfaceHeight * 3];
        indices = new short[(surfaceWidth - 1) * (surfaceHeight - 1) * 6];
        heights = new float[detailWidth * detailHeight];
        velocity = new float[detailWidth * detailHeight];
        gpuHeights = new float[surfaceWidth * surfaceHeight * 3];
        backend = NativeBackend.tryCreate(nativeAbiIsRequired());
    }

    void initWaters(
            int vertexCount,
            int meshHeight,
            int meshWidth,
            int surfaceHeight,
            int surfaceWidth) {
        backend.initWaters(
                vertices, indices, vertexCount, meshHeight, meshWidth, surfaceHeight, surfaceWidth);
    }

    void ripple(
            int meshWidth,
            int meshHeight,
            int detailWidth,
            int detailHeight,
            float meshX,
            float meshY,
            float strength) {
        backend.ripple(
                velocity, meshWidth, meshHeight, detailWidth, detailHeight, meshX, meshY, strength);
    }

    boolean move(
            int xBegin,
            int yBegin,
            int xEnd,
            int yEnd,
            int detailWidth,
            int detailHeight,
            float damping,
            float waveCoefficient) {
        return backend.move(
                velocity, heights, xBegin, yBegin, xEnd, yEnd, detailWidth, detailHeight,
                true, damping, waveCoefficient);
    }

    boolean moveAdaptive(
            int xBegin,
            int yBegin,
            int xEnd,
            int yEnd,
            int detailWidth,
            int detailHeight,
            float damping,
            float waveCoefficient,
            float stockTicks) {
        // The native ABI has the same hard q=1 boundary.  Keep it explicit here too so a
        // nominal 60 Hz frame always selects the recovered fixed-tick function.
        if (stockTicks == 1.0f) {
            return move(
                    xBegin, yBegin, xEnd, yEnd, detailWidth, detailHeight,
                    damping, waveCoefficient);
        }
        return backend.moveAdaptive(
                velocity, heights, xBegin, yBegin, xEnd, yEnd, detailWidth, detailHeight,
                true, damping, waveCoefficient, stockTicks);
    }

    void packGpuHeights(int detailWidth, int surfaceWidth, int surfaceHeight) {
        // Exact Samsung tuple/order: current, left, upper.  Mesh and ripple axes are
        // intentionally transposed together; do not conventionalize this loop.
        for (int i = 0; i < surfaceHeight; ++i) {
            for (int j = 0; j < surfaceWidth; ++j) {
                int target = (surfaceHeight * j + i) * 3;
                gpuHeights[target] = heights[(j + 2) * detailWidth + i + 2];
                gpuHeights[target + 1] = heights[(j + 2) * detailWidth + i + 1];
                gpuHeights[target + 2] = heights[(j + 1) * detailWidth + i + 2];
            }
        }
    }

    void reset() {
        java.util.Arrays.fill(heights, 0.0f);
        java.util.Arrays.fill(velocity, 0.0f);
        java.util.Arrays.fill(gpuHeights, 0.0f);
    }

    float[] vertices() {
        return vertices;
    }

    short[] indices() {
        return indices;
    }

    float[] gpuHeights() {
        return gpuHeights;
    }

    // Package-visible only for host regression coverage of the exact packing/bounds contract.
    float[] heightValuesForTest() {
        return heights;
    }

    float[] velocityValuesForTest() {
        return velocity;
    }

    boolean usesNativeAbi() {
        return backend instanceof NativeBackend;
    }

    /** Host-test seam: an Android-like runtime must never select the scalar fallback. */
    static boolean androidAbiFailureFailsClosedForTest() {
        try {
            NativeBackend.tryCreate(true, new BridgeLoader() {
                @Override
                public Class<?> load() throws ClassNotFoundException {
                    throw new ClassNotFoundException("test missing WaterRipple ABI");
                }
            });
            return false;
        } catch (IllegalStateException expected) {
            return expected.getCause() instanceof ClassNotFoundException;
        }
    }

    /** Host-test seam: the no-Android-classpath JVM keeps its deterministic test mirror. */
    static boolean hostAbiFailureUsesFallbackForTest() {
        Backend backend = NativeBackend.tryCreate(false, new BridgeLoader() {
            @Override
            public Class<?> load() throws ClassNotFoundException {
                throw new ClassNotFoundException("test host JVM");
            }
        });
        return backend instanceof HostVanillaCore;
    }

    /**
     * The fallback is intentionally permitted only outside Android.  Resolving android.os.Build
     * is a boot-classpath check and does not initialize or depend on the framework object.
     */
    private static boolean nativeAbiIsRequired() {
        try {
            Class.forName("android.os.Build", false, RippleInkVanillaWaterAdapter.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException hostJvm) {
            return false;
        } catch (LinkageError | SecurityException unknownRuntime) {
            // If this is not conclusively the host JVM, fail closed rather than approximating
            // Android water motion with the test-only mirror.
            return true;
        }
    }

    private interface Backend {
        void initWaters(
                float[] vertices,
                short[] indices,
                int vertexCount,
                int meshHeight,
                int meshWidth,
                int surfaceHeight,
                int surfaceWidth);

        void ripple(
                float[] velocity,
                int meshWidth,
                int meshHeight,
                int detailWidth,
                int detailHeight,
                float meshX,
                float meshY,
                float strength);

        boolean move(
                float[] velocity,
                float[] height,
                int xBegin,
                int yBegin,
                int xEnd,
                int yEnd,
                int detailWidth,
                int detailHeight,
                boolean checkEmpty,
                float damping,
                float waveCoefficient);

        boolean moveAdaptive(
                float[] velocity,
                float[] height,
                int xBegin,
                int yBegin,
                int xEnd,
                int yEnd,
                int detailWidth,
                int detailHeight,
                boolean checkEmpty,
                float damping,
                float waveCoefficient,
                float stockTicks);
    }

    private interface BridgeLoader {
        Class<?> load() throws ClassNotFoundException;
    }

    /** Original packaged Java ABI, reached without making host javac resolve Android classes. */
    private static final class NativeBackend implements Backend {
        private final Method initWaters;
        private final Method ripple;
        private final Method move;
        private final Method moveAdaptive;

        static Backend tryCreate(boolean nativeRequired) {
            return tryCreate(nativeRequired, new BridgeLoader() {
                @Override
                public Class<?> load() throws ClassNotFoundException {
                    return Class.forName(JNI_CLASS);
                }
            });
        }

        static Backend tryCreate(boolean nativeRequired, BridgeLoader loader) {
            try {
                return new NativeBackend(loader.load());
            } catch (ClassNotFoundException | LinkageError unavailable) {
                return unavailableBackend(nativeRequired, unavailable);
            } catch (ReflectiveOperationException unavailable) {
                return unavailableBackend(nativeRequired, unavailable);
            } catch (SecurityException unavailable) {
                return unavailableBackend(nativeRequired, unavailable);
            }
        }

        private static Backend unavailableBackend(boolean nativeRequired, Throwable unavailable) {
            if (nativeRequired) {
                throw new IllegalStateException(
                        "Ripple Ink requires JniWaterRippleRender/libWaterRipple on Android",
                        unavailable);
            }
            return new HostVanillaCore();
        }

        NativeBackend(Class<?> bridge) throws ReflectiveOperationException {
            initWaters = bridge.getMethod(
                    "initWaters", float[].class, short[].class,
                    int.class, int.class, int.class, int.class, int.class);
            ripple = bridge.getMethod(
                    "ripple", float[].class,
                    int.class, int.class, int.class, int.class,
                    float.class, float.class, float.class);
            move = bridge.getMethod(
                    "move", float[].class, float[].class,
                    int.class, int.class, int.class, int.class,
                    int.class, int.class, boolean.class, float.class, float.class);
            moveAdaptive = bridge.getMethod(
                    "moveAdaptive", float[].class, float[].class,
                    int.class, int.class, int.class, int.class,
                    int.class, int.class, boolean.class, float.class, float.class, float.class);
        }

        @Override
        public void initWaters(
                float[] vertices,
                short[] indices,
                int vertexCount,
                int meshHeight,
                int meshWidth,
                int surfaceHeight,
                int surfaceWidth) {
            invokeVoid(initWaters,
                    vertices, indices, vertexCount, meshHeight, meshWidth, surfaceHeight, surfaceWidth);
        }

        @Override
        public void ripple(
                float[] velocity,
                int meshWidth,
                int meshHeight,
                int detailWidth,
                int detailHeight,
                float meshX,
                float meshY,
                float strength) {
            invokeVoid(ripple,
                    velocity, meshWidth, meshHeight, detailWidth, detailHeight, meshX, meshY, strength);
        }

        @Override
        public boolean move(
                float[] velocity,
                float[] height,
                int xBegin,
                int yBegin,
                int xEnd,
                int yEnd,
                int detailWidth,
                int detailHeight,
                boolean checkEmpty,
                float damping,
                float waveCoefficient) {
            return invokeInt(move,
                    velocity, height, xBegin, yBegin, xEnd, yEnd, detailWidth, detailHeight,
                    checkEmpty, damping, waveCoefficient) != 0;
        }

        @Override
        public boolean moveAdaptive(
                float[] velocity,
                float[] height,
                int xBegin,
                int yBegin,
                int xEnd,
                int yEnd,
                int detailWidth,
                int detailHeight,
                boolean checkEmpty,
                float damping,
                float waveCoefficient,
                float stockTicks) {
            return invokeInt(moveAdaptive,
                    velocity, height, xBegin, yBegin, xEnd, yEnd, detailWidth, detailHeight,
                    checkEmpty, damping, waveCoefficient, stockTicks) != 0;
        }

        private static void invokeVoid(Method method, Object... arguments) {
            invoke(method, arguments);
        }

        private static int invokeInt(Method method, Object... arguments) {
            Object result = invoke(method, arguments);
            return ((Integer) result).intValue();
        }

        private static Object invoke(Method method, Object... arguments) {
            try {
                return method.invoke(null, arguments);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Water Ripple ABI became inaccessible", exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException("Water Ripple ABI call failed", cause);
            }
        }
    }

    /**
     * Host-JVM mirror of {@code ripple_core.c}; production never selects it when libWaterRipple
     * is packaged.  Keeping it here preserves deterministic JVM regression tests without a
     * second copy in {@link RippleInkPortEngine}.
     */
    private static final class HostVanillaCore implements Backend {
        @Override
        public void initWaters(
                float[] vertices,
                short[] indices,
                int vertexCount,
                int meshHeight,
                int meshWidth,
                int surfaceHeight,
                int surfaceWidth) {
            float rowStepX = meshHeight / (float) (surfaceHeight - 1);
            float columnStepY = meshWidth / (float) (surfaceWidth - 1);
            float halfX = meshHeight * 0.5f;
            float halfY = meshWidth * 0.5f;
            int bulkVertexCount = vertexCount & ~3;
            for (int vertex = 0; vertex < bulkVertexCount; ++vertex) {
                float rowFraction = vertex / (float) surfaceWidth;
                int row = (int) rowFraction;
                int column = vertex - row * surfaceWidth;
                vertices[vertex * 3] = rowFraction * rowStepX - halfX;
                vertices[vertex * 3 + 1] = -(column * columnStepY - halfY);
                vertices[vertex * 3 + 2] = 0.0f;
            }
            for (int vertex = bulkVertexCount; vertex < vertexCount; ++vertex) {
                int row = vertex / surfaceWidth;
                int column = vertex % surfaceWidth;
                vertices[vertex * 3] = row * rowStepX - halfX;
                vertices[vertex * 3 + 1] = -(column * columnStepY - halfY);
                vertices[vertex * 3 + 2] = 0.0f;
            }
            int output = 0;
            for (int x = 1; x < surfaceHeight; ++x) {
                for (int y = 1; y < surfaceWidth; ++y) {
                    int bottomRight = x * surfaceHeight + y;
                    int topLeft = bottomRight - surfaceHeight - 1;
                    int topRight = bottomRight - surfaceHeight;
                    int bottomLeft = bottomRight - 1;
                    indices[output++] = (short) topLeft;
                    indices[output++] = (short) topRight;
                    indices[output++] = (short) bottomRight;
                    indices[output++] = (short) topLeft;
                    indices[output++] = (short) bottomRight;
                    indices[output++] = (short) bottomLeft;
                }
            }
        }

        @Override
        public void ripple(
                float[] velocity,
                int meshWidth,
                int meshHeight,
                int detailWidth,
                int detailHeight,
                float meshX,
                float meshY,
                float strength) {
            float cellX = (meshX / meshWidth + 0.5f) * detailWidth;
            float cellY = (meshY / meshHeight + 0.5f) * detailHeight;
            int xBegin = cellX < 5.0f ? 2 : (int) Math.floor(cellX - 3.0f);
            int yBegin = cellY < 5.0f ? 2 : (int) Math.floor(cellY - 3.0f);
            int xEnd = cellX < detailWidth - 5
                    ? (int) Math.floor(cellX + 4.0f) : detailWidth - 1;
            int yEnd = cellY < detailHeight - 5
                    ? (int) Math.floor(cellY + 4.0f) : detailHeight - 1;
            for (int x = xBegin; x < xEnd; ++x) {
                float dx = cellX - x;
                for (int y = yBegin; y < yEnd; ++y) {
                    float dy = cellY - y;
                    float impulse = 3.0f - (float) Math.sqrt(dx * dx + dy * dy);
                    if (impulse > 0.0f) {
                        velocity[y * detailWidth + x] += impulse * strength;
                    }
                }
            }
        }

        @Override
        public boolean move(
                float[] velocity,
                float[] height,
                int xBegin,
                int yBegin,
                int xEnd,
                int yEnd,
                int detailWidth,
                int detailHeight,
                boolean checkEmpty,
                float damping,
                float waveCoefficient) {
            Bounds bounds = Bounds.clamp(
                    xBegin, yBegin, xEnd, yEnd, detailWidth, detailHeight);
            boolean empty = true;
            for (int x = bounds.xBegin; x < bounds.xEnd; ++x) {
                for (int y = bounds.yBegin; y < bounds.yEnd; ++y) {
                    int index = y * detailWidth + x;
                    float nextVelocity = (velocity[index]
                            + laplacian(height, index, detailWidth) * waveCoefficient) * damping;
                    velocity[index] = nextVelocity;
                    if (checkEmpty && empty
                            && (nextVelocity > IDLE_VELOCITY || nextVelocity < -IDLE_VELOCITY)) {
                        empty = false;
                    }
                }
            }
            for (int x = bounds.xBegin; x < bounds.xEnd; ++x) {
                for (int y = bounds.yBegin; y < bounds.yEnd; ++y) {
                    int index = y * detailWidth + x;
                    height[index] = clamp(height[index] + velocity[index], MIN_HEIGHT, MAX_HEIGHT);
                }
            }
            float extra = damping == STOCK_DAMPING ? STOCK_EXTRA_SMOOTHING : OTHER_EXTRA_SMOOTHING;
            for (int x = bounds.xBegin; x < bounds.xEnd; ++x) {
                for (int y = bounds.yBegin; y < bounds.yEnd; ++y) {
                    int index = y * detailWidth + x;
                    height[index] += laplacian(height, index, detailWidth) * extra;
                }
            }
            return empty;
        }

        @Override
        public boolean moveAdaptive(
                float[] velocity,
                float[] height,
                int xBegin,
                int yBegin,
                int xEnd,
                int yEnd,
                int detailWidth,
                int detailHeight,
                boolean checkEmpty,
                float damping,
                float waveCoefficient,
                float stockTicks) {
            float bounded = sanitizeAdaptiveTicks(stockTicks);
            Bounds bounds = Bounds.clamp(
                    xBegin, yBegin, xEnd, yEnd, detailWidth, detailHeight);
            if (bounded == 0.0f) {
                return !checkEmpty || velocityRegionEmpty(velocity, bounds, detailWidth);
            }
            if (bounded == 1.0f) {
                return move(velocity, height, xBegin, yBegin, xEnd, yEnd,
                        detailWidth, detailHeight, checkEmpty, damping, waveCoefficient);
            }
            int whole = (int) Math.floor(bounded);
            float fractional = bounded - whole;
            boolean empty = true;
            for (int step = 0; step < whole; ++step) {
                empty = move(velocity, height, xBegin, yBegin, xEnd, yEnd,
                        detailWidth, detailHeight, checkEmpty, damping, waveCoefficient);
            }
            if (fractional > 0.0f) {
                empty = moveFractional(velocity, height, bounds, detailWidth, checkEmpty,
                        damping, waveCoefficient, fractional);
            }
            return empty;
        }

        private static boolean moveFractional(
                float[] velocity,
                float[] height,
                Bounds bounds,
                int detailWidth,
                boolean checkEmpty,
                float damping,
                float waveCoefficient,
                float stockTicks) {
            float scaledDamping = scaleDissipation(damping, stockTicks);
            float scaledWave = waveCoefficient * stockTicks;
            boolean empty = true;
            for (int x = bounds.xBegin; x < bounds.xEnd; ++x) {
                for (int y = bounds.yBegin; y < bounds.yEnd; ++y) {
                    int index = y * detailWidth + x;
                    float nextVelocity = (velocity[index]
                            + laplacian(height, index, detailWidth) * scaledWave) * scaledDamping;
                    velocity[index] = nextVelocity;
                    if (checkEmpty && empty
                            && (nextVelocity > IDLE_VELOCITY || nextVelocity < -IDLE_VELOCITY)) {
                        empty = false;
                    }
                }
            }
            for (int x = bounds.xBegin; x < bounds.xEnd; ++x) {
                for (int y = bounds.yBegin; y < bounds.yEnd; ++y) {
                    int index = y * detailWidth + x;
                    height[index] = clamp(
                            height[index] + velocity[index] * stockTicks, MIN_HEIGHT, MAX_HEIGHT);
                }
            }
            float extra = damping == STOCK_DAMPING ? STOCK_EXTRA_SMOOTHING : OTHER_EXTRA_SMOOTHING;
            float scaledExtra = extra * stockTicks;
            for (int x = bounds.xBegin; x < bounds.xEnd; ++x) {
                for (int y = bounds.yBegin; y < bounds.yEnd; ++y) {
                    int index = y * detailWidth + x;
                    height[index] += laplacian(height, index, detailWidth) * scaledExtra;
                }
            }
            return empty;
        }

        private static float laplacian(float[] field, int index, int stride) {
            float center = field[index];
            float value = field[index - stride] - center * 4.0f;
            value += field[index - 1];
            value += field[index + 1];
            value += field[index + stride];
            return value;
        }

        private static float sanitizeAdaptiveTicks(float ticks) {
            if (Float.isNaN(ticks) || Float.isInfinite(ticks) || ticks <= 0.0f) {
                return 0.0f;
            }
            return Math.min(MAX_ADAPTIVE_TICKS, ticks);
        }

        private static float scaleDissipation(float damping, float ticks) {
            if (ticks == 0.0f || damping == 1.0f) {
                return 1.0f;
            }
            if (ticks == 1.0f) {
                return damping;
            }
            return (float) Math.pow(damping, ticks);
        }

        private static boolean velocityRegionEmpty(
                float[] velocity, Bounds bounds, int detailWidth) {
            for (int x = bounds.xBegin; x < bounds.xEnd; ++x) {
                for (int y = bounds.yBegin; y < bounds.yEnd; ++y) {
                    float value = velocity[y * detailWidth + x];
                    if (value > IDLE_VELOCITY || value < -IDLE_VELOCITY) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static float clamp(float value, float minimum, float maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }

    private static final class Bounds {
        final int xBegin;
        final int yBegin;
        final int xEnd;
        final int yEnd;

        private Bounds(int xBegin, int yBegin, int xEnd, int yEnd) {
            this.xBegin = xBegin;
            this.yBegin = yBegin;
            this.xEnd = xEnd;
            this.yEnd = yEnd;
        }

        static Bounds clamp(
                int xBegin, int yBegin, int xEnd, int yEnd, int detailWidth, int detailHeight) {
            return new Bounds(
                    Math.max(1, xBegin),
                    Math.max(1, yBegin),
                    Math.min(detailWidth - 1, xEnd),
                    Math.min(detailHeight - 1, yEnd));
        }
    }
}
