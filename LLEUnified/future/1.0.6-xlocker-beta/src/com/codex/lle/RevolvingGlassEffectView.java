package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 1.0.6 beta: independently authored revolving glass panel.
 *
 * <p>The front is the LLE-supplied bitmap; the rear is an original dark mirrored shader treatment.
 * The panel bevel, reflection and seams are procedural. No XLocker models, textures, Java/native
 * code, binaries or audio are used or required.</p>
 */
public final class RevolvingGlassEffectView extends GLSurfaceView
        implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "LLERevolvingGlass";
    private static final long RELEASE_TIMEOUT_MS = 350L;

    private final Object sourceLock = new Object();
    private final Object sceneLock = new Object();
    private final RevolvingGlassScene scene = new RevolvingGlassScene();
    private final GlassRenderer renderer = new GlassRenderer();
    private volatile boolean destroyed;
    private volatile boolean paused;
    private volatile boolean framePosted;
    private volatile boolean backgroundAccepted;
    private volatile int readinessState = STATE_CONSTRUCTED;
    private volatile String readinessDetail = "constructed";
    private volatile ReadinessListener readinessListener;
    private Bitmap pendingBitmap;
    private int sourceSerial;
    private float downX;
    private int affordanceGeneration;
    private Runnable affordanceRunnable;

    /** Vsync scheduler: motion is elapsed-time based, never a fixed 4/8/16-ms simulation tick. */
    private final Runnable vsyncFrame = new Runnable() {
        @Override public void run() {
            framePosted = false;
            if (!destroyed && !paused && renderer.needsAnotherFrame()) {
                requestRender();
            }
        }
    };

    public RevolvingGlassEffectView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setFocusable(false);
    }

    public static boolean supportsHighFrameRatePresentation() { return true; }
    public static int maximumFullSizeWallpaperTextures() { return 1; }

    @Override public View asView() { return this; }
    @Override public String effectName() { return "Revolving Glass (clean-room GLES beta)"; }

    @Override public void beginGesture(float x, float y) {
        if (destroyed) return;
        cancelAffordance();
        downX = x;
        synchronized (sceneLock) { scene.begin(x, y, SystemClock.uptimeMillis()); }
        activate();
    }

    @Override public void updateGesture(float x, float y) {
        if (destroyed) return;
        synchronized (sceneLock) {
            scene.move(downX, x, Math.max(1, getWidth()), SystemClock.uptimeMillis());
        }
        activate();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed) return;
        synchronized (sceneLock) { scene.finish(completed, SystemClock.uptimeMillis()); }
        activate();
    }

    @Override public void cancelGesture() { finishGesture(false); }

    @Override public void resetEffect() {
        cancelAffordance();
        synchronized (sceneLock) { scene.reset(); }
        activate();
    }

    @Override public void warmUp() { if (!destroyed && !paused) requestRender(); }

    @Override public void showUnlockAffordance(Rect ignored, long delayMs) {
        if (destroyed) return;
        cancelAffordance();
        final int generation = ++affordanceGeneration;
        affordanceRunnable = new Runnable() {
            @Override public void run() {
                if (!destroyed && generation == affordanceGeneration) {
                    synchronized (sceneLock) { scene.affordance(SystemClock.uptimeMillis()); }
                    activate();
                }
            }
        };
        postDelayed(affordanceRunnable, Math.max(0L, delayMs));
    }

    @Override public boolean hasBackgroundSourceBitmap() { return backgroundAccepted; }

    @Override public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) return;
        // The caller may reuse or recycle the service cache. Keep one owned upload copy only.
        Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
        if (copy == null || copy.isRecycled()) return;
        synchronized (sourceLock) {
            recycle(pendingBitmap);
            pendingBitmap = copy;
            sourceSerial++;
        }
        backgroundAccepted = true;
        renderer.invalidateSource();
        activate();
    }

    @Override public void clearBackgroundSourceBitmap() {
        synchronized (sourceLock) {
            recycle(pendingBitmap);
            pendingBitmap = null;
            sourceSerial++;
        }
        backgroundAccepted = false;
        renderer.invalidateSource();
        activate();
    }

    @Override public int getReadinessState() { return readinessState; }
    @Override public String getReadinessDetail() { return effectName() + ": " + readinessDetail; }
    @Override public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    @Override public void destroy() {
        if (destroyed) return;
        destroyed = true;
        cancelAffordance();
        removeCallbacks(vsyncFrame);
        synchronized (sourceLock) { recycle(pendingBitmap); pendingBitmap = null; sourceSerial++; }
        final CountDownLatch released = new CountDownLatch(1);
        try {
            queueEvent(new Runnable() { @Override public void run() {
                renderer.releaseGl(); released.countDown();
            }});
            requestRender();
            released.await(RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) { }
        transition(STATE_FAILED, "destroyed");
        readinessListener = null;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (paused && !destroyed) { onResume(); paused = false; }
        transition(STATE_ATTACHED, "attached; waiting for transparent EGL");
        warmUp();
    }

    @Override protected void onDetachedFromWindow() {
        resetEffect();
        if (!destroyed) {
            paused = true;
            removeCallbacks(vsyncFrame);
            onPause();
            transition(STATE_DETACHED, "detached");
        }
        super.onDetachedFromWindow();
    }

    private void activate() { if (!destroyed && !paused) requestRender(); }

    private void scheduleVsync() {
        if (!destroyed && !paused && !framePosted) {
            framePosted = true;
            postOnAnimation(vsyncFrame);
        }
    }

    private void cancelAffordance() {
        affordanceGeneration++;
        if (affordanceRunnable != null) removeCallbacks(affordanceRunnable);
        affordanceRunnable = null;
    }

    private void transition(int state, String detail) {
        readinessState = state;
        readinessDetail = detail == null ? "" : detail;
        notifyReadiness();
    }

    private void notifyReadiness() {
        final ReadinessListener listener = readinessListener;
        if (listener != null && !destroyed) post(new Runnable() {
            @Override public void run() { listener.onReadinessChanged(); }
        });
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private final class GlassRenderer implements GLSurfaceView.Renderer {
        private static final String VERTEX =
                "attribute vec2 aPosition;attribute vec2 aUv;varying vec2 vUv;"
                + "uniform float uAngle;uniform float uLift;uniform float uFace;"
                + "void main(){float c=cos(uAngle);float s=sin(uAngle);"
                + "float x=aPosition.x*c;float z=-aPosition.x*s+uFace*.055;"
                + "float p=1.0/(1.0+max(-.18,z)*.42);"
                + "gl_Position=vec4(x*p,(aPosition.y+uLift)*p,0.,1.);vUv=aUv;}";
        private static final String FRAGMENT =
                "precision mediump float;varying vec2 vUv;uniform sampler2D uMap;"
                + "uniform vec2 uMapScale;uniform vec2 uMapOffset;uniform float uAngle;"
                + "uniform float uAlpha;uniform float uShine;uniform float uFace;"
                + "void main(){vec2 uv=uMapOffset+vUv*uMapScale;if(uFace>.5)uv.x=1.-uv.x;"
                + "vec3 base=texture2D(uMap,uv).rgb;float e=min(min(vUv.x,1.-vUv.x),min(vUv.y,1.-vUv.y));"
                + "float b=1.-smoothstep(.015,.040,e);float d=smoothstep(.87,1.,fract((vUv.x+vUv.y*.45+uAngle*.12)*2.4));"
                + "float f=pow(1.-abs(cos(uAngle)),.38);vec3 front=mix(base,base*.72+vec3(.12,.17,.20),.16);"
                + "vec3 back=base*.22+vec3(.015,.035,.055)+vec3(.08,.15,.19)*(d*uShine);"
                + "vec3 color=mix(front,back,step(.5,uFace))+vec3(.34,.54,.62)*(b*(.42+.58*f)+d*uShine*.30);"
                + "float a=uAlpha*(.80+b*.20);gl_FragColor=vec4(color*a,a);}";
        private final FloatBuffer slab = buffer(new float[] {
                -.88f,.70f,0f,1f, -.88f,-.70f,0f,0f, .88f,.70f,1f,1f, .88f,-.70f,1f,0f
        });
        private int program;
        private int texture;
        private int uploadedSerial = -1;
        private int textureWidth, textureHeight, viewportWidth, viewportHeight;
        private boolean firstFrame, needsFrame;

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            try {
                releaseGl();
                program = createProgram(VERTEX, FRAGMENT);
                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                firstFrame = false;
                transition(STATE_SURFACE_READY, "GLES2 slab program ready");
            } catch (Throwable error) { fail(error, "surface creation failed"); }
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            viewportWidth = width; viewportHeight = height;
            GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
            uploadIfNeeded();
        }

        @Override public void onDrawFrame(GL10 gl) {
            if (destroyed || program == 0) return;
            try {
                uploadIfNeeded();
                RevolvingGlassScene.Frame frame;
                synchronized (sceneLock) { frame = scene.frameAt(SystemClock.uptimeMillis()); }
                needsFrame = frame.animating;
                clear();
                if (frame.visible && texture != 0) { draw(frame, 1f); draw(frame, 0f); }
                if (!firstFrame) {
                    firstFrame = true;
                    transition(STATE_FIRST_FRAME_READY, "first transparent frame drawn");
                }
                if (needsFrame) scheduleVsync();
            } catch (Throwable error) { fail(error, "draw failed"); }
        }

        boolean needsAnotherFrame() { return needsFrame; }
        void invalidateSource() { queueEvent(new Runnable() { @Override public void run() { uploadedSerial = -1; }}); }

        private void uploadIfNeeded() {
            final Bitmap bitmap;
            final int serial;
            synchronized (sourceLock) {
                serial = sourceSerial;
                if (serial == uploadedSerial) return;
                bitmap = pendingBitmap;
                pendingBitmap = null; // GL assumes ownership; next setter cannot recycle it.
            }
            deleteTexture();
            textureWidth = textureHeight = 0;
            if (bitmap != null && !bitmap.isRecycled()) {
                int[] ids = new int[1];
                GLES20.glGenTextures(1, ids, 0); texture = ids[0];
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
                textureWidth = bitmap.getWidth(); textureHeight = bitmap.getHeight();
            }
            recycle(bitmap);
            uploadedSerial = serial;
            transition(STATE_RESOURCES_READY, texture != 0 ? "front wallpaper texture ready" : "waiting for wallpaper texture");
        }

        private void draw(RevolvingGlassScene.Frame frame, float face) {
            GLES20.glUseProgram(program);
            int position = GLES20.glGetAttribLocation(program, "aPosition");
            int uv = GLES20.glGetAttribLocation(program, "aUv");
            slab.position(0); GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, slab);
            slab.position(2); GLES20.glEnableVertexAttribArray(uv);
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, slab);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uMap"), 0);
            float[] crop = centerCrop(textureWidth, textureHeight, viewportWidth, viewportHeight);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uMapScale"), crop[0], crop[1]);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uMapOffset"), crop[2], crop[3]);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uAngle"), face > .5f ? frame.angleRadians + (float) Math.PI : frame.angleRadians);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uLift"), frame.lift);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFace"), face);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uAlpha"), frame.alpha);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uShine"), frame.shine);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(uv);
        }

        private void clear() { GLES20.glClearColor(0f,0f,0f,0f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); }
        void releaseGl() { deleteTexture(); if (program != 0) { GLES20.glDeleteProgram(program); program = 0; } uploadedSerial = -1; needsFrame = false; }
        private void deleteTexture() { if (texture != 0) { GLES20.glDeleteTextures(1, new int[] {texture}, 0); texture = 0; } }
    }

    private void fail(Throwable error, String detail) { Log.e(TAG, detail, error); transition(STATE_FAILED, detail); }

    private static int createProgram(String vertex, String fragment) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertex), fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
        int program = GLES20.glCreateProgram(); GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs); GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs); int[] ok = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) { String log = GLES20.glGetProgramInfoLog(program); GLES20.glDeleteProgram(program); throw new IllegalStateException("glass program link failed: " + log); }
        return program;
    }

    private static int compile(int type, String code) {
        int shader = GLES20.glCreateShader(type); GLES20.glShaderSource(shader, code); GLES20.glCompileShader(shader);
        int[] ok = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) { String log = GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader); throw new IllegalStateException("glass shader compile failed: " + log); }
        return shader;
    }

    static float[] centerCrop(int mapWidth, int mapHeight, int outputWidth, int outputHeight) {
        if (mapWidth <= 0 || mapHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) return new float[] {1f,1f,0f,0f};
        float mapAspect = mapWidth / (float) mapHeight, outAspect = outputWidth / (float) outputHeight;
        if (mapAspect > outAspect) { float x = outAspect / mapAspect; return new float[] {x,1f,(1f-x)*.5f,0f}; }
        float y = mapAspect / outAspect; return new float[] {1f,y,0f,(1f-y)*.5f};
    }

    private static FloatBuffer buffer(float[] values) {
        FloatBuffer result = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        result.put(values); result.position(0); return result;
    }
}
