package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Transparent EGL host preserving the stock S6 WaterDropletRenderer call order.
 */
final class S6WaterDropletGlView extends GLSurfaceView
        implements GLSurfaceView.Renderer {
    interface Listener {
        void onNativeResourcesReady();
        void onFirstFrame();
        void onNativeFailure(Throwable error);
    }

    private static final String TAG = "S6WaterDropletGl";
    private static final int QUALITY_LEVEL_2 = 2;
    private static final int EVENT_INIT_RESOLUTION = 95;
    private static final int EVENT_RESET_BG_SCALE = 96;

    private final S6WaterDropletNative nativeRenderer;
    private final Listener listener;
    private final int projectKind;
    private final int configuredWidth;
    private final int configuredHeight;
    private final Bitmap normalTexture;
    private final Bitmap edgeDensityTexture;
    private final Object backgroundLock = new Object();
    private final int[] pointerX = new int[10];
    private final int[] pointerY = new int[10];

    private Bitmap portraitBackground;
    private Bitmap landscapeBackground;
    private volatile boolean nativeCreated;
    private volatile boolean initialized;
    private volatile boolean destroyed;
    private volatile boolean touched;
    private volatile int keepAliveFrames = 100;
    private int emptyFrames;
    private int drawCount;

    S6WaterDropletGlView(
            Context context,
            S6WaterDropletNative nativeRenderer,
            int projectKind,
            int configuredWidth,
            int configuredHeight,
            Bitmap normalTexture,
            Bitmap edgeDensityTexture,
            Listener listener) {
        super(context);
        this.nativeRenderer = nativeRenderer;
        this.projectKind = projectKind;
        this.configuredWidth = Math.max(1, configuredWidth);
        this.configuredHeight = Math.max(1, configuredHeight);
        this.normalTexture = normalTexture;
        this.edgeDensityTexture = edgeDensityTexture;
        this.listener = listener;

        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        if (destroyed) {
            return;
        }
        try {
            if (nativeCreated) {
                nativeRenderer.deinitJni();
            }
            nativeRenderer.initJni();
            nativeCreated = true;
            initialized = false;
            drawCount = 0;
            emptyFrames = 0;
            nativeRenderer.texture("Normal", normalTexture);
            nativeRenderer.texture("EdgeDensity", edgeDensityTexture);
            Log.i(TAG, "native surface created");
        } catch (Throwable error) {
            fail(error);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (destroyed || !nativeCreated || width <= 0 || height <= 0) {
            return;
        }
        try {
            if (!initialized) {
                uploadCurrentBackground();
                nativeRenderer.initPhysics(
                        projectKind, QUALITY_LEVEL_2, width, height);
                nativeRenderer.custom(
                        EVENT_INIT_RESOLUTION,
                        Math.min(configuredWidth, configuredHeight),
                        Math.max(configuredWidth, configuredHeight),
                        0f);
                // Stock WaterDropletRenderer calls this after Init_PhysicsEngine even on
                // the first surface. Omitting it can leave projection/FBO state stale.
                nativeRenderer.surfaceChanged(width, height);
                initialized = true;
                if (listener != null) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onNativeResourcesReady();
                        }
                    });
                }
            } else {
                nativeRenderer.key(90);
                uploadCurrentBackground();
                nativeRenderer.surfaceChanged(width, height);
            }
            nativeRenderer.key(EVENT_RESET_BG_SCALE);
            wake(100);
        } catch (Throwable error) {
            fail(error);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (destroyed || !nativeCreated || !initialized) {
            return;
        }
        try {
            nativeRenderer.draw();
            drawCount++;
            if (drawCount == 1 && listener != null) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onFirstFrame();
                    }
                });
            }
            if (keepAliveFrames > 0) {
                keepAliveFrames--;
            }
            if (nativeRenderer.isEmpty() == 1 && !touched
                    && keepAliveFrames <= 0) {
                if (++emptyFrames >= 2
                        && getRenderMode() != RENDERMODE_WHEN_DIRTY) {
                    setRenderMode(RENDERMODE_WHEN_DIRTY);
                }
            } else {
                emptyFrames = 0;
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    void setTouched(boolean value) {
        touched = value;
        wake(2);
    }

    void wake(int minimumFrames) {
        if (destroyed) {
            return;
        }
        keepAliveFrames =
                Math.max(keepAliveFrames, Math.max(1, minimumFrames));
        if (getRenderMode() != RENDERMODE_CONTINUOUSLY) {
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }
        requestRender();
    }

    void setBackground(final Bitmap portrait, final Bitmap landscape) {
        if (destroyed) {
            recycle(portrait);
            recycle(landscape);
            return;
        }
        synchronized (backgroundLock) {
            recycle(portraitBackground);
            recycle(landscapeBackground);
            portraitBackground = portrait;
            landscapeBackground = landscape;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (!destroyed && nativeCreated) {
                    try {
                        uploadCurrentBackground();
                        wake(100);
                    } catch (Throwable error) {
                        fail(error);
                    }
                }
            }
        });
    }

    void touch(int eventType, int x, int y) {
        if (!initialized || destroyed) {
            return;
        }
        // JNI copies ten positions even though stock sends one active pointer.
        pointerX[0] = x;
        pointerY[0] = y;
        nativeRenderer.touch(0, 1, eventType, pointerX, pointerY);
        touched = eventType != 1;
        wake(2);
    }

    void sensor(int sensorType, float x, float y, float z) {
        if (initialized && !destroyed) {
            nativeRenderer.sensor(sensorType, x, y, z);
        }
    }

    void key(int eventId) {
        if (initialized && !destroyed) {
            nativeRenderer.key(eventId);
            wake(eventId == EVENT_RESET_BG_SCALE ? 100 : 2);
        }
    }

    void custom(int eventId, float x, float y, float z) {
        if (initialized && !destroyed) {
            nativeRenderer.custom(eventId, x, y, z);
            wake(2);
        }
    }

    void destroyNative() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        final CountDownLatch finished = new CountDownLatch(1);
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (nativeCreated) {
                            nativeRenderer.deinitJni();
                        }
                    } finally {
                        nativeCreated = false;
                        initialized = false;
                        finished.countDown();
                    }
                }
            });
            requestRender();
            finished.await(500L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // A detached GLSurfaceView may already have stopped its GL thread.
        }
        onPause();
        synchronized (backgroundLock) {
            recycle(portraitBackground);
            recycle(landscapeBackground);
            portraitBackground = null;
            landscapeBackground = null;
        }
        recycle(normalTexture);
        recycle(edgeDensityTexture);
    }

    private void uploadCurrentBackground() {
        synchronized (backgroundLock) {
            if (portraitBackground != null
                    && !portraitBackground.isRecycled()) {
                nativeRenderer.texture("PortraitBG", portraitBackground);
            }
            if (landscapeBackground != null
                    && !landscapeBackground.isRecycled()) {
                nativeRenderer.texture("LandscapeBG", landscapeBackground);
            }
        }
    }

    private void fail(final Throwable error) {
        Log.e(TAG, "native failure", error);
        if (listener != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onNativeFailure(error);
                }
            });
        }
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
