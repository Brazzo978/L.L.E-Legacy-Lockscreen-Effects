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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

interface LgPixelateRendererListener {
    void onSurfaceReady();
    void onResourcesReady(boolean hasBackground);
    void onFirstFrame();
    void onFailure(Throwable error, String detail);
}

/**
 * 1.0.6-beta-only app-owned GLES2 Pixelate renderer.
 *
 * <p>The view composites only the locally pixelated circle over the existing lockscreen. It
 * samples the dedicated pre-lock underlay supplied by the service (bitmap or direct ARGB8888
 * upload), never the normal lockscreen colormap, and never paints it as a full-screen background.
 * This is an independent implementation and deliberately contains no XLocker Java, GLSL, sound,
 * bitmap or binary.</p>
 */
public final class LgPixelateEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, RawArgb8888BackgroundRenderer,
        UnlockEffectReadiness, LgPixelateRendererListener {
    private static final String TAG = "LLELgPixelate";

    private final Object sceneLock = new Object();
    private final LgPixelateScene scene = new LgPixelateScene();
    private final PixelateGlSurface glSurface;
    private volatile int readinessState = STATE_CONSTRUCTED;
    private volatile String readinessDetail = "constructed";
    private volatile ReadinessListener readinessListener;
    private boolean destroyed;
    private boolean attached;
    private boolean gestureActive;
    private boolean backgroundAccepted;
    private File rawBackgroundFile;
    private long rawBackgroundLength;
    private long rawBackgroundModified;
    private float affordanceX;
    private float affordanceY;
    private final Runnable affordanceRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed || gestureActive) return;
            synchronized (sceneLock) {
                scene.affordance(affordanceX, affordanceY, SystemClock.uptimeMillis());
            }
            glSurface.activate();
        }
    };

    public LgPixelateEffectView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        glSurface = new PixelateGlSurface(context, sceneLock, scene, this);
        addView(glSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override public View asView() { return this; }
    @Override public String effectName() { return "LG G2 Pixelate (app-owned GLES)"; }
    @Override public int getReadinessState() { return readinessState; }
    @Override public String getReadinessDetail() { return effectName() + ": " + readinessDetail; }

    @Override public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    /** HFR changes presentation cadence but cannot make the elapsed-time scene run faster. */
    public void setHighFrameRateEnabled(boolean enabled) { glSurface.setHighFrameRateEnabled(enabled); }
    public boolean isHighFrameRateEnabled() { return glSurface.isHighFrameRateEnabled(); }

    /** Optional future speed control, deliberately constrained to 1x--2x. */
    public void setSpeedMultiplier(float multiplier) { glSurface.setSpeedMultiplier(sanitizeSpeedMultiplier(multiplier)); }
    public float getSpeedMultiplier() { return glSurface.getSpeedMultiplier(); }

    @Override public void beginGesture(float x, float y) {
        if (destroyed) return;
        removeCallbacks(affordanceRunnable);
        gestureActive = true;
        synchronized (sceneLock) { scene.begin(x, y, SystemClock.uptimeMillis()); }
        glSurface.activate();
    }

    @Override public void updateGesture(float x, float y) {
        if (destroyed) return;
        if (!gestureActive) { beginGesture(x, y); return; }
        synchronized (sceneLock) { scene.move(x, y, SystemClock.uptimeMillis()); }
        glSurface.activate();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || !gestureActive) return;
        gestureActive = false;
        synchronized (sceneLock) { scene.finish(completed, SystemClock.uptimeMillis()); }
        glSurface.activate();
    }

    @Override public void cancelGesture() {
        if (destroyed || !gestureActive) return;
        gestureActive = false;
        synchronized (sceneLock) { scene.cancel(SystemClock.uptimeMillis()); }
        glSurface.activate();
    }

    @Override public void resetEffect() {
        removeCallbacks(affordanceRunnable);
        gestureActive = false;
        synchronized (sceneLock) { scene.reset(); }
        glSurface.activate();
    }

    @Override public void warmUp() { if (!destroyed) glSurface.activate(); }

    @Override public void showUnlockAffordance(Rect rect, long delayMs) {
        if (destroyed) return;
        Rect safe = rect != null && rect.width() > 0 && rect.height() > 0 ? rect : displayRect();
        affordanceX = safe.exactCenterX();
        affordanceY = safe.exactCenterY();
        removeCallbacks(affordanceRunnable);
        postDelayed(affordanceRunnable, Math.max(0L, delayMs));
    }

    @Override public boolean hasBackgroundSourceBitmap() { return backgroundAccepted; }

    @Override public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) return;
        Bitmap owned = source.copy(Bitmap.Config.ARGB_8888, false);
        if (owned == null || owned.isRecycled()) return;
        rawBackgroundFile = null;
        rawBackgroundLength = 0L;
        rawBackgroundModified = 0L;
        backgroundAccepted = true;
        glSurface.setBackgroundBitmap(owned);
    }

    @Override public void clearBackgroundSourceBitmap() {
        rawBackgroundFile = null;
        rawBackgroundLength = 0L;
        rawBackgroundModified = 0L;
        backgroundAccepted = false;
        glSurface.clearBackground();
    }

    @Override public boolean hasRawArgb8888BackgroundSource() {
        return backgroundAccepted && rawBackgroundFile != null;
    }

    @Override public void setRawArgb8888BackgroundSource(File file, String sourceName) {
        Argb8888BitmapStore.Info info = Argb8888BitmapStore.inspect(file);
        if (destroyed || info == null || !info.raw) return;
        long length = file.length();
        long modified = file.lastModified();
        if (backgroundAccepted && rawBackgroundFile != null
                && rawBackgroundFile.getAbsolutePath().equals(file.getAbsolutePath())
                && rawBackgroundLength == length
                && rawBackgroundModified == modified) {
            return;
        }
        rawBackgroundFile = file;
        rawBackgroundLength = length;
        rawBackgroundModified = modified;
        backgroundAccepted = true;
        glSurface.setBackgroundFile(file);
        Log.i(TAG, "raw pre-lock underlay accepted " + info.width + "x" + info.height);
    }

    @Override public void destroy() {
        if (destroyed) return;
        destroyed = true;
        resetEffect();
        backgroundAccepted = false;
        rawBackgroundFile = null;
        rawBackgroundLength = 0L;
        rawBackgroundModified = 0L;
        glSurface.destroyRenderer();
        transition(STATE_FAILED, "destroyed");
        readinessListener = null;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        glSurface.resumeIfNeeded();
        transition(STATE_ATTACHED, "attached; waiting for EGL");
        post(new Runnable() { @Override public void run() { warmUp(); } });
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        resetEffect();
        if (!destroyed) {
            glSurface.pauseForDetach();
            transition(STATE_DETACHED, "GLSurfaceView detached");
        }
        super.onDetachedFromWindow();
    }

    @Override public void onSurfaceReady() {
        advanceReadiness(STATE_SURFACE_READY, "transparent EGL surface ready");
    }
    @Override public void onResourcesReady(boolean hasBackground) {
        if (hasBackground) {
            advanceReadiness(STATE_RESOURCES_READY, "pre-lock underlay texture ready");
        } else if (attached && !destroyed && readinessState >= STATE_CONSTRUCTED
                && readinessState < STATE_RESOURCES_READY) {
            transition(Math.max(readinessState, STATE_SURFACE_READY),
                    "waiting for pre-lock underlay");
        }
    }
    @Override public void onFirstFrame() {
        advanceReadiness(STATE_FIRST_FRAME_READY,
                "first textured transparent GLES frame drawn");
    }
    @Override public void onFailure(Throwable error, String detail) {
        Log.e(TAG, "renderer failure " + detail, error);
        transition(STATE_FAILED, detail);
    }

    static float sanitizeSpeedMultiplier(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 1f : Math.max(1f, Math.min(2f, value));
    }

    private Rect displayRect() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return new Rect(0, 0, Math.max(1, metrics.widthPixels), Math.max(1, metrics.heightPixels));
    }
    private void transition(int state, String detail) { readinessState = state; readinessDetail = detail; notifyReadiness(); }
    private void advanceReadiness(int state, String detail) {
        if (attached && !destroyed && readinessState != STATE_FAILED
                && state >= readinessState) transition(state, detail);
    }
    private void notifyReadiness() {
        final ReadinessListener listener = readinessListener;
        if (listener != null && attached && !destroyed) post(new Runnable() {
            @Override public void run() { listener.onReadinessChanged(); }
        });
    }

    /** GLES thread owns texture creation, upload and release. */
    static final class PixelateGlSurface extends GLSurfaceView implements GLSurfaceView.Renderer {
        private static final long LEGACY_FRAME_NS = 16666667L;
        private static final String VERTEX =
                "attribute vec2 aPosition;attribute vec2 aTexCoord;varying vec2 vUv;"
                + "void main(){gl_Position=vec4(aPosition,0.,1.);vUv=aTexCoord;}";
        private static final String FRAGMENT =
                "precision mediump float;varying vec2 vUv;uniform sampler2D uMap;"
                + "uniform vec2 uSurface,uScale,uOffset,uCenter;uniform float uRadius,uPixel,uAlpha,uRaw,uHas;"
                + "void main(){if(uHas<.5)discard;vec2 p=vUv*uSurface;float d=distance(p,uCenter);"
                + "float edge=1.-smoothstep(uRadius*.84,uRadius,d);if(edge<=.001)discard;"
                + "float block=max(uPixel,1.);vec2 cell=floor(p/block)*block+.5*block;"
                + "vec4 c=texture2D(uMap,uOffset+(cell/uSurface)*uScale);if(uRaw>.5)c=c.bgra;"
                + "gl_FragColor=vec4(c.rgb,c.a*edge*uAlpha);}";

        private final Object sceneLock;
        private final LgPixelateScene scene;
        private final LgPixelateRendererListener listener;
        private final FloatBuffer quad = buffer(new float[] {-1,1,0,0,-1,-1,0,1,1,1,1,0,1,-1,1,1});
        private final Object sourceLock = new Object();
        private Bitmap bitmap;
        private File rawFile;
        private int sourceSerial;
        private int uploadedSerial = -1;
        private boolean raw;
        private boolean destroyed;
        private boolean paused;
        private boolean hfr;
        private float speed = 1f;
        private int program;
        private int texture;
        private int width;
        private int height;
        private int mapWidth;
        private int mapHeight;
        private boolean rawBgra;
        private boolean mapReady;
        private boolean firstFrame;
        private boolean idleCleared;
        private long lastPresentationNs;

        PixelateGlSurface(Context context, Object lock, LgPixelateScene scene,
                LgPixelateRendererListener listener) {
            super(context);
            sceneLock = lock;
            this.scene = scene;
            this.listener = listener;
            setZOrderOnTop(true);
            getHolder().setFormat(PixelFormat.TRANSLUCENT);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8,8,8,8,0,0);
            setPreserveEGLContextOnPause(true);
            setRenderer(this);
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }

        void setHighFrameRateEnabled(boolean value) { hfr = value; activate(); }
        boolean isHighFrameRateEnabled() { return hfr; }
        void setSpeedMultiplier(float value) { speed = value; activate(); }
        float getSpeedMultiplier() { return speed; }
        void activate() { if (!destroyed) requestRender(); }
        void pauseForDetach() { if (!paused) { paused = true; onPause(); } }
        void resumeIfNeeded() { if (paused && !destroyed) { onResume(); paused = false; } }
        void setBackgroundBitmap(Bitmap value) {
            synchronized (sourceLock) { recycle(bitmap); bitmap = value; rawFile = null; raw = false; sourceSerial++; }
            activate();
        }
        void setBackgroundFile(File value) {
            synchronized (sourceLock) { recycle(bitmap); bitmap = null; rawFile = value; raw = true; sourceSerial++; }
            activate();
        }
        void clearBackground() {
            synchronized (sourceLock) { recycle(bitmap); bitmap = null; rawFile = null; raw = false; sourceSerial++; }
            activate();
        }
        void destroyRenderer() {
            destroyed = true;
            synchronized (sourceLock) { recycle(bitmap); bitmap = null; rawFile = null; sourceSerial++; }
            queueEvent(new Runnable() { @Override public void run() { releaseGl(); } });
        }

        @Override public void onSurfaceCreated(GL10 ignored, EGLConfig config) {
            if (destroyed) return;
            try {
                releaseGl();
                program = program(VERTEX, FRAGMENT);
                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                uploadedSerial = -1;
                firstFrame = false;
                post(new Runnable() { @Override public void run() { listener.onSurfaceReady(); } });
            } catch (Throwable error) { fail(error, "surface initialization failed"); }
        }

        @Override public void onSurfaceChanged(GL10 ignored, int newWidth, int newHeight) {
            if (destroyed || newWidth <= 0 || newHeight <= 0) return;
            width = newWidth; height = newHeight;
            GLES20.glViewport(0, 0, width, height);
            uploadIfNeeded();
            ready();
        }

        @Override public void onDrawFrame(GL10 ignored) {
            if (destroyed || program == 0 || width <= 0 || height <= 0) return;
            try {
                uploadIfNeeded();
                long nanos = System.nanoTime();
                if (!hfr && lastPresentationNs != 0L && nanos - lastPresentationNs < LEGACY_FRAME_NS) return;
                LgPixelateScene.Frame frame;
                synchronized (sceneLock) {
                    frame = scene.frameAt(SystemClock.uptimeMillis(), screenScale(), speed);
                }
                if (!mapReady || !frame.visible) {
                    if (!idleCleared) clear();
                    idleCleared = true;
                } else {
                    draw(frame);
                    idleCleared = false;
                }
                lastPresentationNs = nanos;
                if (!firstFrame && mapReady) {
                    firstFrame = true;
                    post(new Runnable() { @Override public void run() { listener.onFirstFrame(); } });
                }
            } catch (Throwable error) { fail(error, "draw failed"); }
        }

        private void uploadIfNeeded() {
            final Bitmap pending;
            final File file;
            final boolean isRaw;
            final int serial;
            synchronized (sourceLock) {
                serial = sourceSerial;
                if (serial == uploadedSerial) return;
                pending = bitmap; file = rawFile; isRaw = raw;
            }
            deleteTexture(); mapReady = false; rawBgra = false; mapWidth = mapHeight = 0;
            if (isRaw && file != null) {
                Argb8888BitmapStore.MappedImage image = Argb8888BitmapStore.map(file);
                if (image != null) try {
                    rawUpload(image); mapWidth = image.width; mapHeight = image.height;
                    rawBgra = true; mapReady = texture != 0;
                } finally { image.close(); }
            } else if (pending != null && !pending.isRecycled()) {
                bitmapUpload(pending); mapWidth = pending.getWidth(); mapHeight = pending.getHeight(); mapReady = texture != 0;
                synchronized (sourceLock) { if (serial == sourceSerial && bitmap == pending) { bitmap = null; recycle(pending); } }
            }
            uploadedSerial = serial;
            ready();
        }

        private void draw(LgPixelateScene.Frame frame) {
            clear();
            GLES20.glUseProgram(program);
            int pos = GLES20.glGetAttribLocation(program, "aPosition");
            int uv = GLES20.glGetAttribLocation(program, "aTexCoord");
            quad.position(0); GLES20.glEnableVertexAttribArray(pos);
            GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,quad);
            quad.position(2); GLES20.glEnableVertexAttribArray(uv);
            GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,quad);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uMap"),0);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uSurface"),width,height);
            float[] transform = centerCropTransform(mapWidth,mapHeight,width,height);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uScale"),transform[0],transform[1]);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uOffset"),transform[2],transform[3]);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uCenter"),frame.x,frame.y);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uRadius"),frame.radiusPx);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uPixel"),frame.pixelSizePx);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uAlpha"),frame.alpha);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uRaw"),rawBgra ? 1f : 0f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uHas"),1f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(uv);
        }

        private void bitmapUpload(Bitmap source) { int[] ids = new int[1]; GLES20.glGenTextures(1,ids,0); texture=ids[0]; bindTexture(); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,source,0); }
        private void rawUpload(Argb8888BitmapStore.MappedImage source) { int[] ids = new int[1]; GLES20.glGenTextures(1,ids,0); texture=ids[0]; bindTexture(); GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT,1); GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,source.width,source.height,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,source.pixels()); }
        private void bindTexture() { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE); }
        private float screenScale() { return Math.min(width,height) <= 0 ? 1f : Math.min(width,height) / 1080f; }
        private void clear() { GLES20.glClearColor(0,0,0,0); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); }
        private void ready() { final boolean ready = mapReady; post(new Runnable() { @Override public void run() { listener.onResourcesReady(ready); } }); }
        private void fail(final Throwable error, final String detail) { clear(); post(new Runnable() { @Override public void run() { listener.onFailure(error,detail); } }); }
        private void releaseGl() { deleteTexture(); if(program!=0){GLES20.glDeleteProgram(program);program=0;} mapReady=false; }
        private void deleteTexture() { if(texture!=0){GLES20.glDeleteTextures(1,new int[]{texture},0);texture=0;} }

        static float[] centerCropTransform(int mw,int mh,int ow,int oh) {
            if(mw<=0||mh<=0||ow<=0||oh<=0) return new float[]{1,1,0,0};
            float ma=mw/(float)mh, oa=ow/(float)oh;
            if(ma>oa){float sx=oa/ma;return new float[]{sx,1,(1-sx)*.5f,0};}
            float sy=ma/oa; return new float[]{1,sy,0,(1-sy)*.5f};
        }
        private static int program(String vertex,String fragment) { int v=shader(GLES20.GL_VERTEX_SHADER,vertex),f=shader(GLES20.GL_FRAGMENT_SHADER,fragment),p=GLES20.glCreateProgram(); GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] linked=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,linked,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(linked[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("pixelate link failed: "+log);}return p; }
        private static int shader(int type,String source) { int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] compiled=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,compiled,0);if(compiled[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("pixelate shader failed: "+log);}return s; }
        private static FloatBuffer buffer(float[] values) { FloatBuffer out=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();out.put(values);out.position(0);return out; }
        private static void recycle(Bitmap source) { if(source!=null&&!source.isRecycled())source.recycle(); }
    }
}
