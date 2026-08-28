package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.SoundPool;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Clean-room GLES2 reconstruction of Revolving Glass.
 *
 * <p>The primary cache is drawn only on the three front bands of the rotating glass box. The
 * independent Last screen cache is a fixed underlay, so neither source replaces the other.</p>
 */
public final class RevolvingGlassEffectView extends GLSurfaceView
        implements UnlockEffectRenderer, BackgroundSourceRenderer,
        SecondaryBackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "LLERevolvingGlass";
    private static final long RELEASE_TIMEOUT_MS = 350L;
    private static final float BOX_DEPTH = .045f;
    private static final int CARD_CORNER_SEGMENTS = 8;
    private static final float CARD_CORNER_RADIUS_X = .070f;
    // Keep the rotating tile at 87% of the display without altering its aspect ratio.
    // The vertical offset leaves 5% above and 8% below; uniform scaling therefore
    // leaves 6.5% at each side. The independent Last screen underlay stays full-screen.
    private static final float CARD_LEFT = -.87f;
    private static final float CARD_RIGHT = .87f;
    private static final float CARD_BOTTOM = -.84f;
    private static final float CARD_TOP = .90f;

    private final Object sourceLock = new Object();
    private final Object sceneLock = new Object();
    private final Object soundLock = new Object();
    private final RevolvingGlassScene scene = new RevolvingGlassScene();
    private final GlassRenderer renderer = new GlassRenderer();
    private final SoundPool soundPool;
    private final int unlockSound;
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();

    private volatile boolean destroyed;
    private volatile boolean paused;
    private volatile boolean framePosted;
    private volatile boolean primaryAccepted;
    private volatile boolean secondaryAccepted;
    private volatile int readinessState = STATE_CONSTRUCTED;
    private volatile String readinessDetail = "constructed";
    private volatile ReadinessListener readinessListener;
    private Bitmap primaryBitmap;
    private Bitmap secondaryBitmap;
    private int primarySerial;
    private int secondarySerial;
    private float downX;
    private int affordanceGeneration;
    private Runnable affordanceRunnable;
    private int unlockSoundGeneration;
    private Runnable unlockSoundRunnable;

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
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                handleSoundLoadComplete(pool, sampleId, status);
            }
        });
        unlockSound = soundPool.load(context, R.raw.revolving_glass_unlock, 1);

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(false);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setFocusable(false);
    }

    public static boolean supportsHighFrameRatePresentation() { return true; }
    public static int maximumFullSizeWallpaperTextures() { return 2; }

    @Override public View asView() { return this; }
    @Override public String effectName() { return "Revolving Glass"; }

    @Override public void beginGesture(float x, float y) {
        if (!canRender()) return;
        cancelAffordance();
        cancelUnlockSound();
        downX = toLocalX(x);
        synchronized (sceneLock) {
            scene.begin(downX, Math.max(1, getWidth()), SystemClock.uptimeMillis());
        }
        activate();
    }

    @Override public void updateGesture(float x, float y) {
        if (!canRender()) return;
        float localX = toLocalX(x);
        synchronized (sceneLock) {
            scene.move(downX, localX, Math.max(1, getWidth()), SystemClock.uptimeMillis());
        }
        activate();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || paused) return;
        long soundDelay;
        synchronized (sceneLock) {
            soundDelay = scene.finish(completed, SystemClock.uptimeMillis());
        }
        if (completed) {
            scheduleUnlockSound(soundDelay);
        } else {
            cancelUnlockSound();
        }
        activate();
    }

    @Override public void cancelGesture() { finishGesture(false); }

    @Override public void resetEffect() {
        cancelAffordance();
        cancelUnlockSound();
        synchronized (sceneLock) { scene.reset(); }
        activate();
    }

    @Override public void warmUp() {
        if (!destroyed && !paused) requestRender();
    }

    @Override public void showUnlockAffordance(Rect ignored, long delayMs) {
        if (!canRender()) return;
        cancelAffordance();
        final int generation = ++affordanceGeneration;
        affordanceRunnable = new Runnable() {
            @Override public void run() {
                affordanceRunnable = null;
                if (!destroyed && !paused && generation == affordanceGeneration) {
                    synchronized (sceneLock) {
                        scene.affordance(SystemClock.uptimeMillis());
                    }
                    activate();
                }
            }
        };
        postDelayed(affordanceRunnable, Math.max(0L, delayMs));
    }

    @Override public boolean hasBackgroundSourceBitmap() { return primaryAccepted; }

    @Override public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        Bitmap copy = ownedCopy(source);
        if (copy == null) return;
        Bitmap previous;
        synchronized (sourceLock) {
            previous = primaryBitmap;
            primaryBitmap = copy;
            primarySerial++;
        }
        recycle(previous);
        primaryAccepted = true;
        renderer.invalidateSources();
        activate();
    }

    @Override public void clearBackgroundSourceBitmap() {
        Bitmap previous;
        synchronized (sourceLock) {
            previous = primaryBitmap;
            primaryBitmap = null;
            primarySerial++;
        }
        recycle(previous);
        primaryAccepted = false;
        renderer.invalidateSources();
        activate();
    }

    @Override public boolean hasSecondaryBackgroundSourceBitmap() { return secondaryAccepted; }

    @Override public void setSecondaryBackgroundSourceBitmap(Bitmap source, String sourceName) {
        Bitmap copy = ownedCopy(source);
        if (copy == null) return;
        Bitmap previous;
        synchronized (sourceLock) {
            previous = secondaryBitmap;
            secondaryBitmap = copy;
            secondarySerial++;
        }
        recycle(previous);
        secondaryAccepted = true;
        renderer.invalidateSources();
        activate();
    }

    @Override public void clearSecondaryBackgroundSourceBitmap() {
        Bitmap previous;
        synchronized (sourceLock) {
            previous = secondaryBitmap;
            secondaryBitmap = null;
            secondarySerial++;
        }
        recycle(previous);
        secondaryAccepted = false;
        renderer.invalidateSources();
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
        cancelUnlockSound();
        removeCallbacks(vsyncFrame);
        Bitmap primary;
        Bitmap secondary;
        synchronized (sourceLock) {
            primary = primaryBitmap;
            secondary = secondaryBitmap;
            primaryBitmap = secondaryBitmap = null;
            primarySerial++;
            secondarySerial++;
        }
        recycle(primary);
        recycle(secondary);
        primaryAccepted = secondaryAccepted = false;
        synchronized (soundLock) {
            pendingSoundIds.clear();
            loadedSoundIds.clear();
            soundPool.setOnLoadCompleteListener(null);
            soundPool.release();
        }
        final CountDownLatch released = new CountDownLatch(1);
        try {
            queueEvent(new Runnable() {
                @Override public void run() {
                    renderer.releaseGl();
                    released.countDown();
                }
            });
            requestRender();
            released.await(RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) { }
        transition(STATE_FAILED, "destroyed");
        readinessListener = null;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (paused && !destroyed) {
            paused = false;
            onResume();
        }
        transition(STATE_ATTACHED, "attached; waiting for translucent EGL");
        warmUp();
    }

    @Override protected void onDetachedFromWindow() {
        cancelAffordance();
        cancelUnlockSound();
        synchronized (sceneLock) { scene.reset(); }
        if (!destroyed) {
            paused = true;
            removeCallbacks(vsyncFrame);
            onPause();
            transition(STATE_DETACHED, "detached; source copies retained");
        }
        super.onDetachedFromWindow();
    }

    private boolean canRender() {
        return !destroyed && !paused && primaryAccepted;
    }

    private float toLocalX(float x) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return x - location[0];
    }

    private void activate() {
        if (!destroyed && !paused) requestRender();
    }

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

    private void scheduleUnlockSound(long delayMs) {
        cancelUnlockSound();
        final int generation = ++unlockSoundGeneration;
        unlockSoundRunnable = new Runnable() {
            @Override public void run() {
                unlockSoundRunnable = null;
                if (!destroyed && generation == unlockSoundGeneration) {
                    playSound(unlockSound);
                }
            }
        };
        postDelayed(unlockSoundRunnable, Math.max(0L, delayMs));
    }

    private void cancelUnlockSound() {
        unlockSoundGeneration++;
        if (unlockSoundRunnable != null) removeCallbacks(unlockSoundRunnable);
        unlockSoundRunnable = null;
    }

    private void playSound(int soundId) {
        if (soundId == 0 || destroyed
                || !OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) return;
        synchronized (soundLock) {
            if (destroyed) return;
            if (!loadedSoundIds.contains(soundId)) {
                pendingSoundIds.add(soundId);
                return;
            }
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void handleSoundLoadComplete(SoundPool pool, int sampleId, int status) {
        synchronized (soundLock) {
            if (pool != soundPool || destroyed) return;
            if (status != 0) {
                pendingSoundIds.remove(sampleId);
                return;
            }
            loadedSoundIds.add(sampleId);
            if (pendingSoundIds.remove(sampleId)
                    && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
                soundPool.play(sampleId, 1f, 1f, 1, 0, 1f);
            }
        }
    }

    private void transition(int state, String detail) {
        readinessState = state;
        readinessDetail = detail == null ? "" : detail;
        notifyReadiness();
    }

    private void notifyReadiness() {
        final ReadinessListener listener = readinessListener;
        if (listener != null && !destroyed) {
            post(new Runnable() {
                @Override public void run() { listener.onReadinessChanged(); }
            });
        }
    }

    private Bitmap ownedCopy(Bitmap source) {
        if (destroyed || source == null || source.isRecycled()) return null;
        try {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable error) {
            Log.w(TAG, "source copy failed", error);
            return null;
        }
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private final class GlassRenderer implements GLSurfaceView.Renderer {
        private static final String VERTEX =
                "attribute vec3 aPosition;attribute vec2 aUv;varying vec2 vUv;"
                + "uniform float uAngle;uniform float uFlat;uniform float uScale;"
                + "void main(){vUv=aUv;if(uFlat>.5){gl_Position=vec4(aPosition.xy,0.,1.);return;}"
                + "float c=cos(uAngle);float s=sin(uAngle);"
                + "vec3 q=aPosition;q.xy=vec2(0.,.03)+(q.xy-vec2(0.,.03))*uScale;"
                + "q.z*=uScale;vec3 p=vec3(q.x*c+q.z*s,q.y,"
                + "-q.x*s+q.z*c);float camera=2.6;"
                + "float perspective=camera/(camera-p.z);"
                + "gl_Position=vec4(p.x*perspective,p.y*perspective,-p.z/camera,1.);}";
        private static final String FRAGMENT =
                "precision mediump float;varying vec2 vUv;uniform sampler2D uMap;"
                + "uniform float uMode;uniform float uAngle;uniform float uAlpha;"
                + "uniform vec2 uTexel;"
                + "float cardBorder(){vec2 size=1./max(uTexel,vec2(.000001));"
                + "float radius=min(size.x,size.y)*.035;"
                + "vec2 p=abs(vUv-.5)*size-size*.5+vec2(radius);"
                + "float d=length(max(p,vec2(0.)))+min(max(p.x,p.y),0.)-radius;"
                + "return smoothstep(-7.,-1.,d);}"
                + "void main(){if(uMode>2.5){vec4 c=texture2D(uMap,vUv);"
                + "gl_FragColor=vec4(c.rgb*uAlpha,c.a*uAlpha);return;}"
                + "float light=.72+.28*abs(cos(uAngle));"
                + "if(uMode<.5){float border=cardBorder();vec4 c=texture2D(uMap,vUv);"
                + "vec3 lit=mix(c.rgb,vec3(.94,.98,1.),border*.76);"
                + "gl_FragColor=vec4(min(lit,vec3(1.))*uAlpha,c.a*uAlpha);return;}"
                + "if(uMode<1.5){vec2 o=uTexel*1.25;"
                + "vec4 sharp=texture2D(uMap,vUv);vec4 soft=sharp*.25;"
                + "soft+=(texture2D(uMap,vUv+vec2(o.x,0.))"
                + "+texture2D(uMap,vUv-vec2(o.x,0.))"
                + "+texture2D(uMap,vUv+vec2(0.,o.y))"
                + "+texture2D(uMap,vUv-vec2(0.,o.y)))*.125;"
                + "soft+=(texture2D(uMap,vUv+o)+texture2D(uMap,vUv-o)"
                + "+texture2D(uMap,vUv+vec2(o.x,-o.y))"
                + "+texture2D(uMap,vUv+vec2(-o.x,o.y)))*.0625;"
                + "vec4 c=mix(sharp,soft,.20);"
                + "float border=cardBorder();"
                + "vec3 lit=mix(c.rgb*light,vec3(.94,.98,1.),border*.68);"
                + "gl_FragColor=vec4(lit*uAlpha,c.a*uAlpha);return;}"
                + "float glint=pow(abs(sin(vUv.x*55.+uAngle*3.)),10.);"
                + "float edgeLight=.78+.22*abs(sin(uAngle));float a=.88;"
                + "vec3 edge=vec3(.78,.92,1.)*(.82+.18*glint)*edgeLight;"
                + "gl_FragColor=vec4(min(edge,vec3(1.))*a*uAlpha,a*uAlpha);}";

        private final FloatBuffer fullScreen = quad(-1f, -1f, 1f, 1f, 0f,
                0f, 1f, 1f, 0f);
        private FloatBuffer front;
        private FloatBuffer back;
        private FloatBuffer roundedEdge;
        private int faceVertexCount;
        private int edgeVertexCount;

        private int program;
        private int primaryTexture;
        private int secondaryTexture;
        private int primaryTextureWidth = 1;
        private int primaryTextureHeight = 1;
        private volatile int uploadedPrimarySerial = -1;
        private volatile int uploadedSecondarySerial = -1;
        private boolean firstFrame;
        private volatile boolean needsFrame;

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            try {
                releaseGl();
                program = createProgram(VERTEX, FRAGMENT);
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                GLES20.glEnable(GLES20.GL_DEPTH_TEST);
                GLES20.glDepthFunc(GLES20.GL_LEQUAL);
                GLES20.glDisable(GLES20.GL_CULL_FACE);
                firstFrame = false;
                transition(STATE_SURFACE_READY, "GLES2 glass box ready");
            } catch (Throwable error) {
                fail(error, "surface creation failed");
            }
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
            rebuildCardGeometry(Math.max(1, width), Math.max(1, height));
            uploadIfNeeded();
        }

        @Override public void onDrawFrame(GL10 gl) {
            if (destroyed || program == 0) return;
            try {
                uploadIfNeeded();
                RevolvingGlassScene.Frame frame;
                synchronized (sceneLock) {
                    frame = scene.frameAt(SystemClock.uptimeMillis());
                }
                needsFrame = frame.animating;
                clear();
                if (frame.visible && primaryTexture != 0) drawScene(frame);
                if (!firstFrame && primaryTexture != 0) {
                    firstFrame = true;
                    transition(STATE_FIRST_FRAME_READY,
                            secondaryTexture != 0
                                    ? "lockscreen tile and Last screen ready"
                                    : "lockscreen tile ready; Last screen fallback absent");
                }
                if (needsFrame) scheduleVsync();
            } catch (Throwable error) {
                fail(error, "draw failed");
            }
        }

        boolean needsAnotherFrame() { return needsFrame; }

        void invalidateSources() {
            uploadedPrimarySerial = -1;
            uploadedSecondarySerial = -1;
        }

        private void uploadIfNeeded() {
            boolean changed = false;
            synchronized (sourceLock) {
                if (uploadedPrimarySerial != primarySerial) {
                    primaryTexture = replaceTexture(primaryTexture, primaryBitmap);
                    primaryTextureWidth = primaryBitmap == null
                            ? 1 : Math.max(1, primaryBitmap.getWidth());
                    primaryTextureHeight = primaryBitmap == null
                            ? 1 : Math.max(1, primaryBitmap.getHeight());
                    uploadedPrimarySerial = primarySerial;
                    changed = true;
                }
                if (uploadedSecondarySerial != secondarySerial) {
                    secondaryTexture = replaceTexture(secondaryTexture, secondaryBitmap);
                    uploadedSecondarySerial = secondarySerial;
                    changed = true;
                }
            }
            if (!changed) return;
            // A source refresh drops readiness to resources-ready. The very next draw below
            // must be allowed to promote it back to first-frame-ready, including after AOD.
            firstFrame = false;
            if (primaryTexture != 0) {
                transition(STATE_RESOURCES_READY,
                        secondaryTexture != 0
                                ? "both independent full-screen sources uploaded"
                                : "lockscreen tile uploaded; Last screen fallback absent");
            } else {
                transition(STATE_SURFACE_READY, "waiting for lockscreen tile cache");
            }
        }

        private int replaceTexture(int existing, Bitmap bitmap) {
            if (existing != 0) GLES20.glDeleteTextures(1, new int[] { existing }, 0);
            if (bitmap == null || bitmap.isRecycled()) return 0;
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            return ids[0];
        }

        private void drawScene(RevolvingGlassScene.Frame frame) {
            float angle = frame.angleRadians();
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            if (secondaryTexture != 0) {
                drawQuad(fullScreen, secondaryTexture, 0f, 3f, 1f,
                        frame.underlayAlpha);
            }
            if (!frame.tileVisible) return;
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
            drawMesh(back, faceVertexCount, GLES20.GL_TRIANGLE_FAN,
                    primaryTexture, angle, 1f, 0f, frame.tileScale, frame.tileAlpha);
            drawMesh(roundedEdge, edgeVertexCount, GLES20.GL_TRIANGLE_STRIP,
                    0, angle, 2f, 0f, frame.tileScale, frame.tileAlpha);
            drawMesh(front, faceVertexCount, GLES20.GL_TRIANGLE_FAN,
                    primaryTexture, angle, 0f, 0f, frame.tileScale, frame.tileAlpha);
        }

        private void drawQuad(FloatBuffer vertices, int texture, float angle,
                float mode, float flat, float alpha) {
            drawMesh(vertices, 4, GLES20.GL_TRIANGLE_STRIP,
                    texture, angle, mode, flat, 1f, alpha);
        }

        private void drawMesh(FloatBuffer vertices, int vertexCount, int primitive,
                int texture, float angle, float mode, float flat,
                float scale, float alpha) {
            if (vertices == null || vertexCount <= 0) return;
            GLES20.glUseProgram(program);
            int position = GLES20.glGetAttribLocation(program, "aPosition");
            int uv = GLES20.glGetAttribLocation(program, "aUv");
            vertices.position(0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 20, vertices);
            vertices.position(3);
            GLES20.glEnableVertexAttribArray(uv);
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 20, vertices);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uMap"), 0);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uAngle"), angle);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uMode"), mode);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFlat"), flat);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uScale"), scale);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uAlpha"), alpha);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uTexel"),
                    1f / primaryTextureWidth, 1f / primaryTextureHeight);
            GLES20.glDrawArrays(primitive, 0, vertexCount);
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glDisableVertexAttribArray(uv);
        }

        private void rebuildCardGeometry(int surfaceWidth, int surfaceHeight) {
            int pointsPerCorner = CARD_CORNER_SEGMENTS + 1;
            int perimeterCount = pointsPerCorner * 4;
            float[] xs = new float[perimeterCount];
            float[] ys = new float[perimeterCount];
            float radiusX = Math.min(CARD_CORNER_RADIUS_X,
                    (CARD_RIGHT - CARD_LEFT) * .25f);
            float radiusY = Math.min(radiusX * surfaceWidth / (float) surfaceHeight,
                    (CARD_TOP - CARD_BOTTOM) * .25f);
            float[] centerX = new float[] {
                    CARD_RIGHT - radiusX, CARD_LEFT + radiusX,
                    CARD_LEFT + radiusX, CARD_RIGHT - radiusX
            };
            float[] centerY = new float[] {
                    CARD_TOP - radiusY, CARD_TOP - radiusY,
                    CARD_BOTTOM + radiusY, CARD_BOTTOM + radiusY
            };
            int point = 0;
            for (int corner = 0; corner < 4; corner++) {
                float startDegrees = corner * 90f;
                for (int segment = 0; segment <= CARD_CORNER_SEGMENTS; segment++) {
                    double radians = Math.toRadians(startDegrees
                            + segment * (90f / CARD_CORNER_SEGMENTS));
                    xs[point] = centerX[corner] + radiusX * (float) Math.cos(radians);
                    ys[point] = centerY[corner] + radiusY * (float) Math.sin(radians);
                    point++;
                }
            }

            faceVertexCount = perimeterCount + 2;
            float[] frontValues = new float[faceVertexCount * 5];
            float[] backValues = new float[faceVertexCount * 5];
            putVertex(frontValues, 0, (CARD_LEFT + CARD_RIGHT) * .5f,
                    (CARD_BOTTOM + CARD_TOP) * .5f, 0f, .5f, .5f);
            putVertex(backValues, 0, (CARD_LEFT + CARD_RIGHT) * .5f,
                    (CARD_BOTTOM + CARD_TOP) * .5f, -BOX_DEPTH, .5f, .5f);
            for (int i = 0; i <= perimeterCount; i++) {
                int source = i % perimeterCount;
                float u = (xs[source] - CARD_LEFT) / (CARD_RIGHT - CARD_LEFT);
                float v = (CARD_TOP - ys[source]) / (CARD_TOP - CARD_BOTTOM);
                putVertex(frontValues, i + 1, xs[source], ys[source], 0f, u, v);
                putVertex(backValues, i + 1, xs[source], ys[source],
                        -BOX_DEPTH, u, v);
            }
            front = buffer(frontValues);
            back = buffer(backValues);

            edgeVertexCount = (perimeterCount + 1) * 2;
            float[] edgeValues = new float[edgeVertexCount * 5];
            for (int i = 0; i <= perimeterCount; i++) {
                int source = i % perimeterCount;
                float along = i / (float) perimeterCount;
                putVertex(edgeValues, i * 2, xs[source], ys[source],
                        0f, along, 0f);
                putVertex(edgeValues, i * 2 + 1, xs[source], ys[source],
                        -BOX_DEPTH, along, 1f);
            }
            roundedEdge = buffer(edgeValues);
        }

        private void clear() {
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        }

        void releaseGl() {
            if (primaryTexture != 0) {
                GLES20.glDeleteTextures(1, new int[] { primaryTexture }, 0);
                primaryTexture = 0;
            }
            primaryTextureWidth = primaryTextureHeight = 1;
            if (secondaryTexture != 0) {
                GLES20.glDeleteTextures(1, new int[] { secondaryTexture }, 0);
                secondaryTexture = 0;
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            uploadedPrimarySerial = uploadedSecondarySerial = -1;
            needsFrame = false;
        }
    }

    private void fail(Throwable error, String detail) {
        Log.e(TAG, detail, error);
        transition(STATE_FAILED, detail);
    }

    private static int createProgram(String vertex, String fragment) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertex);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("glass program link failed: " + log);
        }
        return program;
    }

    private static int compile(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("glass shader compile failed: " + log);
        }
        return shader;
    }

    private static FloatBuffer quad(float left, float bottom, float right, float top,
            float z, float u0, float v0, float u1, float v1) {
        return buffer(new float[] {
                left, top, z, u0, v1,
                left, bottom, z, u0, v0,
                right, top, z, u1, v1,
                right, bottom, z, u1, v0
        });
    }

    private static FloatBuffer buffer(float[] values) {
        FloatBuffer result = ByteBuffer.allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        result.put(values);
        result.position(0);
        return result;
    }

    private static void putVertex(float[] values, int vertex, float x, float y,
            float z, float u, float v) {
        int offset = vertex * 5;
        values[offset] = x;
        values[offset + 1] = y;
        values[offset + 2] = z;
        values[offset + 3] = u;
        values[offset + 4] = v;
    }
}
