package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.media.SoundPool;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

interface G2ParticleEffectHost {
    void onSurfaceReady();

    void onResourcesReady(boolean hasBackground);

    void onFirstFrame();

    void onIdle();

    void onRendererFailure(Throwable error, String detail);
}

/**
 * Experimental, app-owned GLES2 circular particle reveal inspired by the LG G2-era visual.
 *
 * <p>The renderer is intentionally transparent outside its point sprites. The lockscreen under
 * the surface remains authoritative; a transparent centre therefore reveals it without copying or
 * redrawing a complete wallpaper. When a colormap is available its pixels colour the sprites,
 * preserving the normal {@link BackgroundSourceRenderer} contract without retaining unbounded
 * per-particle bitmap data.</p>
 */
public final class G2ParticleEffectView extends GLSurfaceView
        implements UnlockEffectRenderer, BackgroundSourceRenderer,
        UnlockEffectReadiness, G2ParticleEffectHost {
    private static final String TAG = "LLEG2Particle";

    private final ParticleRenderer renderer;
    private final SoundPool soundPool;
    private final int unlockSound;
    private volatile boolean destroyed;
    private volatile boolean paused;
    private volatile boolean backgroundAccepted;
    private volatile int readinessState = STATE_CONSTRUCTED;
    private volatile String readinessDetail = "constructed";
    private volatile ReadinessListener readinessListener;
    private boolean continuousRendering;
    private Runnable affordanceRunnable;

    public G2ParticleEffectView(Context context) {
        super(context);
        renderer = new ParticleRenderer(this, context);
        soundPool = new SoundPool.Builder().setMaxStreams(1)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context)).build();
        unlockSound = soundPool.load(context, R.raw.lg_particle_unlock, 1);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(false);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setBackgroundColor(Color.TRANSPARENT);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setFocusable(false);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "G2 Particle";
    }

    /**
     * Keeps the experimental HFR hook local to this renderer. Physics are wall-clock based, so
     * the default already presents at native display cadence without running faster at 120/144 Hz.
     */
    public void setSpeedMultiplier(final float multiplier) {
        if (!canAcceptCommands()) {
            return;
        }
        queueSafely(new Runnable() {
            @Override
            public void run() {
                renderer.setSpeedMultiplier(multiplier);
            }
        });
        activateRendering();
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        cancelAffordance();
        final float[] local = toLocal(screenX, screenY);
        if (!canAcceptCommands()) {
            return;
        }
        queueSafely(new Runnable() {
            @Override
            public void run() {
                renderer.begin(local[0], local[1], SystemClock.uptimeMillis());
            }
        });
        activateRendering();
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        final float[] local = toLocal(screenX, screenY);
        if (!canAcceptCommands()) {
            return;
        }
        queueSafely(new Runnable() {
            @Override
            public void run() {
                renderer.move(local[0], local[1], SystemClock.uptimeMillis());
            }
        });
        activateRendering();
    }

    @Override
    public void finishGesture(final boolean completed) {
        if (!canAcceptCommands()) {
            return;
        }
        queueSafely(new Runnable() {
            @Override
            public void run() {
                renderer.finish(completed, SystemClock.uptimeMillis());
            }
        });
        if (completed && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(unlockSound, 1f, 1f, 1, 0, 1f);
        }
        activateRendering();
    }

    @Override
    public void cancelGesture() {
        finishGesture(false);
    }

    @Override
    public void resetEffect() {
        cancelAffordance();
        if (!canAcceptCommands()) {
            return;
        }
        queueSafely(new Runnable() {
            @Override
            public void run() {
                renderer.resetScene();
            }
        });
        requestRenderSafely();
    }

    @Override
    public void warmUp() {
        if (canAcceptCommands()) {
            requestRenderSafely();
        }
    }

    /** Explicitly synchronizes the cached Surface with the service's park/reuse lifecycle. */
    void parkForReuse() {
        continuousRendering = false;
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        resetEffect();
    }

    /** A reused Surface must begin from a stopped render loop, not the previous cycle's flag. */
    void resumeForReuse() {
        continuousRendering = false;
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        resetEffect();
        warmUp();
    }

    @Override
    public void showUnlockAffordance(final Rect screenRect, long startDelayMs) {
        cancelAffordance();
        if (screenRect == null || !canAcceptCommands()) {
            return;
        }
        final float[] local = toLocal(screenRect.exactCenterX(), screenRect.exactCenterY());
        affordanceRunnable = new Runnable() {
            @Override
            public void run() {
                if (!canAcceptCommands()) {
                    return;
                }
                queueSafely(new Runnable() {
                    @Override
                    public void run() {
                        // A brief, non-unlocking ring is enough to prove the surface is alive;
                        // it never fabricates an unlock gesture.
                        long now = SystemClock.uptimeMillis();
                        renderer.begin(local[0], local[1], now);
                        renderer.finish(false, now + 1L);
                    }
                });
                activateRendering();
            }
        };
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null || !canAcceptCommands()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginGesture(event.getRawX(), event.getRawY());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateGesture(event.getRawX(), event.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
                finishGesture(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelGesture();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return backgroundAccepted;
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        Bitmap owned;
        try {
            owned = source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (OutOfMemoryError ignored) {
            return;
        }
        if (owned == null || owned.isRecycled()) {
            return;
        }
        backgroundAccepted = true;
        renderer.setBackgroundBitmap(owned, sourceName == null ? "bitmap" : sourceName);
        requestRenderSafely();
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        backgroundAccepted = false;
        renderer.clearBackground();
        requestRenderSafely();
    }

    @Override
    public int getReadinessState() {
        return readinessState;
    }

    @Override
    public String getReadinessDetail() {
        return effectName() + ": " + readinessDetail;
    }

    @Override
    public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Surface surface = getHolder().getSurface();
            if (surface != null && surface.isValid()) {
                surface.setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
            }
        }
        if (paused && !destroyed) {
            onResume();
        }
        transition(STATE_ATTACHED, "attached; waiting for EGL");
        post(new Runnable() {
            @Override
            public void run() {
                warmUp();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!destroyed) {
            onPause();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onPause() {
        if (paused) {
            return;
        }
        paused = true;
        cancelAffordance();
        continuousRendering = false;
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        try {
            queueEvent(new Runnable() {
                @Override public void run() { renderer.resetScene(); }
            });
        } catch (RuntimeException ignored) {
            // The EGL thread may already be gone during a WindowManager detach.
        }
        super.onPause();
        transition(STATE_DETACHED, "paused");
    }

    @Override
    public void onResume() {
        if (destroyed || !paused) {
            return;
        }
        super.onResume();
        paused = false;
        transition(STATE_ATTACHED, "resumed; waiting for EGL");
        requestRenderSafely();
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        cancelAffordance();
        backgroundAccepted = false;
        renderer.disposeFromOwner();
        soundPool.release();
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    renderer.releaseGl();
                }
            });
        } catch (RuntimeException ignored) {
            // The EGL thread may already have been dismantled by WindowManager.
        }
        paused = true;
        transition(STATE_FAILED, "destroyed");
        readinessListener = null;
    }

    @Override
    public void onSurfaceReady() {
        transition(STATE_SURFACE_READY, "transparent EGL surface ready");
    }

    @Override
    public void onResourcesReady(boolean hasBackground) {
        transition(STATE_RESOURCES_READY, hasBackground
                ? "particle shader and colormap texture ready"
                : "particle shader ready; transparent colour fallback");
    }

    @Override
    public void onFirstFrame() {
        transition(STATE_FIRST_FRAME_READY, "first GLES frame drawn");
    }

    @Override
    public void onIdle() {
        post(new Runnable() {
            @Override
            public void run() {
                if (!destroyed && continuousRendering) {
                    continuousRendering = false;
                    setRenderMode(RENDERMODE_WHEN_DIRTY);
                    requestRenderSafely(); // Draw the terminal transparent frame after parking.
                }
            }
        });
    }

    @Override
    public void onRendererFailure(Throwable error, String detail) {
        Log.e(TAG, "renderer failure: " + detail, error);
        transition(STATE_FAILED, detail);
    }

    private void activateRendering() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            activateRenderingOnMain();
        } else {
            post(new Runnable() {
                @Override
                public void run() {
                    activateRenderingOnMain();
                }
            });
        }
    }

    private void activateRenderingOnMain() {
        if (!canAcceptCommands()) {
            return;
        }
        if (!continuousRendering) {
            continuousRendering = true;
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }
        requestRenderSafely();
    }

    private void requestRenderSafely() {
        if (!destroyed && !paused) {
            try {
                requestRender();
            } catch (RuntimeException ignored) {
                // An already-detached EGL thread is not a renderer failure.
            }
        }
    }

    private void queueSafely(Runnable command) {
        if (destroyed || paused) {
            return;
        }
        try {
            queueEvent(command);
        } catch (RuntimeException ignored) {
            // Service teardown races EGL shutdown on several OEM releases.
        }
    }

    private boolean canAcceptCommands() {
        return !destroyed && !paused;
    }

    private float[] toLocal(float screenX, float screenY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return new float[] {screenX - location[0], screenY - location[1]};
    }

    private void cancelAffordance() {
        if (affordanceRunnable != null) {
            removeCallbacks(affordanceRunnable);
            affordanceRunnable = null;
        }
    }

    private void transition(int state, String detail) {
        readinessState = state;
        readinessDetail = detail == null ? "" : detail;
        notifyReadiness();
    }

    private void notifyReadiness() {
        final ReadinessListener listener = readinessListener;
        if (listener == null) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                try {
                    listener.onReadinessChanged();
                } catch (RuntimeException ignored) {
                    // Readiness remains advisory during service teardown.
                }
            }
        });
    }

    private static final class ParticleRenderer implements GLSurfaceView.Renderer {
        private static final String VERTEX_SHADER =
                "uniform vec2 uSurface;\n"
                        + "attribute vec4 aParticle;\n"
                        + "attribute float aMaskStart;\n"
                        + "varying float vAlpha;\n"
                        + "varying float vMaskStart;\n"
                        + "void main() {\n"
                        + "  vec2 clip = vec2((aParticle.x / uSurface.x) * 2.0 - 1.0,\n"
                        + "      1.0 - (aParticle.y / uSurface.y) * 2.0);\n"
                        + "  gl_Position = vec4(clip, 0.0, 1.0);\n"
                        + "  gl_PointSize = aParticle.z;\n"
                        + "  vAlpha = aParticle.w;\n"
                        + "  vMaskStart = aMaskStart;\n"
                        + "}\n";
        private static final String FRAGMENT_SHADER =
                "precision mediump float;\n"
                        + "uniform sampler2D uTexture;\n"
                        + "varying float vAlpha;\n"
                        + "varying float vMaskStart;\n"
                        + "void main() {\n"
                        + "  vec4 col=texture2D(uTexture,gl_PointCoord);\n"
                        + "  float edge=1.0-smoothstep(vMaskStart,0.5,length(gl_PointCoord-vec2(0.5)));\n"
                        + "  col.a*=edge*vAlpha;\n"
                        + "  gl_FragColor=col;\n"
                        + "}\n";
        private static final String QUAD_VERTEX_SHADER =
                "attribute vec2 aPosition; varying vec2 vUv;"
                        + "void main(){gl_Position=vec4(aPosition,0.0,1.0);vUv=(aPosition+1.0)*0.5;}";
        private static final String REVEAL_VERTEX_SHADER =
                "attribute vec2 aPixel;varying vec2 vScreenUv;uniform vec2 uSurface;"
                        + "void main(){vScreenUv=aPixel/uSurface;"
                        + "gl_Position=vec4(vScreenUv.x*2.0-1.0,1.0-vScreenUv.y*2.0,0.0,1.0);}";
        private static final String REVEAL_FRAGMENT_SHADER =
                "precision mediump float; varying vec2 vScreenUv; uniform sampler2D uTexture;"
                        + "uniform float uAlpha; uniform float uRaw;"
                        + "void main(){vec4 raw=texture2D(uTexture,vScreenUv);"
                        + "vec4 c=mix(raw,raw.bgra,uRaw);"
                        + "c.a*=uAlpha;gl_FragColor=c;}";
        private static final String HOLE_VERTEX_SHADER =
                "attribute vec2 aPosition;varying vec2 vUv;uniform vec2 uSurface;"
                        + "uniform vec2 uCenter;uniform float uSize;"
                        + "void main(){vec2 p=uCenter+aPosition*uSize*0.5;"
                        + "vec2 c=vec2(p.x/uSurface.x*2.0-1.0,1.0-p.y/uSurface.y*2.0);"
                        + "gl_Position=vec4(c,0.0,1.0);vUv=vec2((aPosition.x+1.0)*0.5,(1.0-aPosition.y)*0.5);}";
        private static final String TEXTURE_FRAGMENT_SHADER =
                "precision mediump float;varying vec2 vUv;uniform sampler2D uTexture;uniform float uAlpha;"
                        + "void main(){vec4 c=texture2D(uTexture,vUv);c.a*=uAlpha;gl_FragColor=c;}";

        private final G2ParticleEffectHost host;
        private final Context context;
        private final Object sceneLock = new Object();
        private final Object sourceLock = new Object();
        private final G2ParticleScene scene = new G2ParticleScene();
        private final FloatBuffer particles = ByteBuffer.allocateDirect(
                G2ParticleScene.PARTICLE_COUNT * G2ParticleScene.VERTEX_STRIDE * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        private final FloatBuffer quad = ByteBuffer.allocateDirect(8 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        private final FloatBuffer revealFan = ByteBuffer.allocateDirect(66 * 2 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        private Bitmap backgroundBitmap;
        private File backgroundFile;
        private String backgroundSource = "none";
        private boolean rawBackground;
        private int sourceSerial;
        private int uploadedSourceSerial = -1;
        private int surfaceWidth = 1;
        private int surfaceHeight = 1;
        private int program;
        private int revealProgram;
        private int holeProgram;
        private int holeTexture;
        private int particleTexture;
        private int particleAttribute;
        private int particleMaskStartAttribute;
        private int surfaceUniform;
        private int wallpaperTexture;
        private int fallbackTexture;
        private boolean backgroundReady;
        private boolean uploadedRawBackground;
        private boolean firstFrame;
        private boolean idleReported = true;
        private boolean released;
        private boolean disposed;
        private boolean fatalError;

        ParticleRenderer(G2ParticleEffectHost host, Context context) {
            this.host = host;
            this.context = context.getApplicationContext();
            scene.setDensity(context.getResources().getDisplayMetrics().density);
            quad.put(new float[] {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f}).position(0);
        }

        void begin(float x, float y, long nowMs) {
            synchronized (sceneLock) {
                scene.begin(x, y, nowMs);
                idleReported = false;
            }
        }

        void move(float x, float y, long nowMs) {
            synchronized (sceneLock) {
                scene.move(x, y, nowMs);
                idleReported = false;
            }
        }

        void finish(boolean completed, long nowMs) {
            synchronized (sceneLock) {
                scene.finish(completed, nowMs);
                idleReported = false;
            }
        }

        void resetScene() {
            synchronized (sceneLock) {
                scene.reset();
                idleReported = false;
            }
        }

        void setSpeedMultiplier(float multiplier) {
            synchronized (sceneLock) {
                scene.setSpeedMultiplier(multiplier);
            }
        }

        void setBackgroundBitmap(Bitmap bitmap, String sourceName) {
            synchronized (sourceLock) {
                recycle(backgroundBitmap);
                backgroundBitmap = bitmap;
                backgroundFile = null;
                rawBackground = false;
                backgroundSource = sourceName;
                sourceSerial++;
            }
        }

        void setBackgroundFile(File file, String sourceName) {
            synchronized (sourceLock) {
                recycle(backgroundBitmap);
                backgroundBitmap = null;
                backgroundFile = file;
                rawBackground = true;
                backgroundSource = sourceName;
                sourceSerial++;
            }
        }

        void clearBackground() {
            synchronized (sourceLock) {
                recycle(backgroundBitmap);
                backgroundBitmap = null;
                backgroundFile = null;
                rawBackground = false;
                backgroundSource = "none";
                sourceSerial++;
            }
        }

        boolean hasRawBackground() {
            synchronized (sourceLock) {
                return rawBackground && backgroundFile != null;
            }
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            if (disposed) {
                clearTransparent();
                return;
            }
            released = false;
            try {
                releaseGlObjects();
                program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
                revealProgram = createProgram(REVEAL_VERTEX_SHADER, REVEAL_FRAGMENT_SHADER);
                holeProgram = createProgram(HOLE_VERTEX_SHADER, TEXTURE_FRAGMENT_SHADER);
                fallbackTexture = createFallbackTexture();
                Bitmap hole = BitmapFactory.decodeResource(
                        context.getResources(), R.drawable.lg_particle_hole);
                if (hole != null) {
                    holeTexture = uploadBitmapTexture(hole);
                    hole.recycle();
                }
                Bitmap particle = BitmapFactory.decodeResource(
                        context.getResources(), R.drawable.lg_particle_white_normal);
                if (particle != null) {
                    particleTexture = uploadBitmapTexture(particle);
                    particle.recycle();
                }
                particleAttribute = GLES20.glGetAttribLocation(program, "aParticle");
                particleMaskStartAttribute = GLES20.glGetAttribLocation(program, "aMaskStart");
                surfaceUniform = GLES20.glGetUniformLocation(program, "uSurface");
                if (particleAttribute < 0 || particleMaskStartAttribute < 0
                        || surfaceUniform < 0) {
                    throw new IllegalStateException("particle shader locations unavailable");
                }
                uploadedSourceSerial = -1;
                firstFrame = false;
                fatalError = false;
                host.onSurfaceReady();
            } catch (Throwable error) {
                fatalError = true;
                host.onRendererFailure(error, "GLES setup failed");
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            surfaceWidth = Math.max(1, width);
            surfaceHeight = Math.max(1, height);
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            synchronized (sceneLock) {
                scene.setSurfaceSize(surfaceWidth, surfaceHeight);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            try {
                if (released || fatalError || program == 0) {
                    clearTransparent();
                    if (!idleReported) {
                        idleReported = true;
                        host.onIdle();
                    }
                    return;
                }
                uploadBackgroundIfNeeded();
                clearTransparent();
                boolean animate;
                float radius;
                float alpha;
                float holeAlpha;
                long now = SystemClock.uptimeMillis();
                synchronized (sceneLock) {
                    float[] vertices = scene.fillVertices(now);
                    animate = scene.isAnimating();
                    radius = scene.currentRadius(now);
                    alpha = scene.currentAlpha(now);
                    holeAlpha = scene.currentHoleAlpha(now);
                    if (animate) {
                        particles.position(0);
                        particles.put(vertices, 0, vertices.length);
                        particles.position(0);
                    }
                }
                if (animate) {
                    drawReveal(radius, alpha);
                    drawHole(radius, holeAlpha);
                    drawParticles();
                    idleReported = false;
                } else if (!idleReported) {
                    idleReported = true;
                    host.onIdle();
                }
                if (!firstFrame) {
                    firstFrame = true;
                    host.onFirstFrame();
                }
            } catch (Throwable error) {
                clearTransparent();
                if (!fatalError) {
                    fatalError = true;
                    host.onRendererFailure(error, "frame draw failed");
                }
            }
        }

        void disposeFromOwner() {
            disposed = true;
            synchronized (sourceLock) {
                recycle(backgroundBitmap);
                backgroundBitmap = null;
                backgroundFile = null;
                rawBackground = false;
                sourceSerial++;
            }
        }

        void releaseGl() {
            released = true;
            disposeFromOwner();
            synchronized (sceneLock) {
                scene.reset();
            }
            releaseGlObjects();
        }

        private void drawParticles() {
            GLES20.glUseProgram(program);
            GLES20.glUniform2f(surfaceUniform, surfaceWidth, surfaceHeight);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            bindTexture(program, particleTexture != 0 ? particleTexture : fallbackTexture);
            GLES20.glEnableVertexAttribArray(particleAttribute);
            particles.position(0);
            GLES20.glVertexAttribPointer(particleAttribute, 4, GLES20.GL_FLOAT,
                    false, G2ParticleScene.VERTEX_STRIDE * 4, particles);
            GLES20.glEnableVertexAttribArray(particleMaskStartAttribute);
            particles.position(4);
            GLES20.glVertexAttribPointer(particleMaskStartAttribute, 1, GLES20.GL_FLOAT,
                    false, G2ParticleScene.VERTEX_STRIDE * 4, particles);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, G2ParticleScene.PARTICLE_COUNT);
            GLES20.glDisableVertexAttribArray(particleAttribute);
            GLES20.glDisableVertexAttribArray(particleMaskStartAttribute);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }

        private void drawReveal(float radius, float alpha) {
            if (!backgroundReady || wallpaperTexture == 0 || radius <= 0f) return;
            float centerX = sceneCentreX();
            float centerY = sceneCentreY();
            revealFan.position(0);
            revealFan.put(centerX).put(centerY);
            for (int i = 0; i <= 64; i++) {
                double theta = i * (Math.PI * 2.0 / 64.0);
                revealFan.put(centerX + (float) Math.cos(theta) * radius);
                revealFan.put(centerY + (float) Math.sin(theta) * radius);
            }
            revealFan.position(0);
            GLES20.glUseProgram(revealProgram);
            int position = GLES20.glGetAttribLocation(revealProgram, "aPixel");
            GLES20.glUniform2f(GLES20.glGetUniformLocation(revealProgram, "uSurface"),
                    surfaceWidth, surfaceHeight);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(revealProgram, "uAlpha"), alpha);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(revealProgram, "uRaw"),
                    uploadedRawBackground ? 1f : 0f);
            bindTexture(revealProgram, wallpaperTexture);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 8, revealFan);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 66);
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }

        private void drawHole(float radius, float alpha) {
            if (holeTexture == 0 || radius <= 0f) return;
            float density = context.getResources().getDisplayMetrics().density;
            // Donor k.a(): 3 * (0.5 / (234.21313 * density)) * radius, applied to
            // a 720 px hdpi plane. Express the decoded resource scale directly in pixels.
            float scale = 1.5f * radius / (234.21313f * density);
            float sizePx = 480f * density * scale;
            GLES20.glUseProgram(holeProgram);
            int position = GLES20.glGetAttribLocation(holeProgram, "aPosition");
            GLES20.glUniform2f(GLES20.glGetUniformLocation(holeProgram, "uSurface"),
                    surfaceWidth, surfaceHeight);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(holeProgram, "uCenter"),
                    sceneCentreX(), sceneCentreY());
            GLES20.glUniform1f(GLES20.glGetUniformLocation(holeProgram, "uSize"), sizePx);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(holeProgram, "uAlpha"), alpha);
            bindTexture(holeProgram, holeTexture);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            drawQuad(position);
            GLES20.glDisable(GLES20.GL_BLEND);
        }

        private float sceneCentreX() { synchronized (sceneLock) { return scene.centreX(); } }
        private float sceneCentreY() { synchronized (sceneLock) { return scene.centreY(); } }

        private static void bindTexture(int activeProgram, int texture) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(activeProgram, "uTexture"), 0);
        }

        private void drawQuad(int position) {
            quad.position(0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 8, quad);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }

        private void uploadBackgroundIfNeeded() {
            synchronized (sourceLock) {
                if (uploadedSourceSerial == sourceSerial) {
                    return;
                }
                // A source swap must publish a fresh composited frame. Without resetting this
                // latch, readiness stays at RESOURCES_READY after the second pre-lock capture
                // and the service buffers every later DOWN forever.
                firstFrame = false;
                int nextTexture = 0;
                boolean uploaded = false;
                boolean nextRaw = false;
                try {
                    if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
                        nextTexture = uploadBitmapTexture(backgroundBitmap);
                        uploaded = nextTexture != 0;
                    } else if (backgroundFile != null) {
                        Argb8888BitmapStore.MappedImage image = Argb8888BitmapStore.map(backgroundFile);
                        if (image == null) {
                            throw new IllegalStateException("invalid raw ARGB8888 colormap");
                        }
                        try {
                            nextTexture = uploadRawTexture(image);
                            uploaded = nextTexture != 0;
                            nextRaw = true;
                        } finally {
                            image.close();
                        }
                    }
                    deleteTexture(wallpaperTexture);
                    wallpaperTexture = nextTexture;
                    backgroundReady = uploaded;
                    uploadedRawBackground = nextRaw;
                    uploadedSourceSerial = sourceSerial;
                    host.onResourcesReady(uploaded);
                    Log.i(TAG, "background upload source=" + backgroundSource
                            + " raw=" + rawBackground + " ready=" + uploaded);
                } catch (Throwable error) {
                    deleteTexture(nextTexture);
                    deleteTexture(wallpaperTexture);
                    wallpaperTexture = 0;
                    backgroundReady = false;
                    uploadedRawBackground = false;
                    uploadedSourceSerial = sourceSerial;
                    host.onRendererFailure(error, "colormap upload failed");
                }
            }
        }

        private int uploadRawTexture(Argb8888BitmapStore.MappedImage image) {
            int texture = generateTexture();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    image.width, image.height, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, image.pixels());
            checkGl("raw colormap upload");
            return texture;
        }

        private int uploadBitmapTexture(Bitmap bitmap) {
            int texture = generateTexture();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            checkGl("bitmap colormap upload");
            return texture;
        }

        private static int generateTexture() {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            return textures[0];
        }

        private static int createFallbackTexture() {
            int texture = generateTexture();
            ByteBuffer pixel = ByteBuffer.allocateDirect(4);
            pixel.put((byte) 143);
            pixel.put((byte) 204);
            pixel.put((byte) 255);
            pixel.put((byte) 255);
            pixel.position(0);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel);
            checkGl("fallback texture upload");
            return texture;
        }

        private static int createProgram(String vertexSource, String fragmentSource) {
            int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            int nextProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(nextProgram, vertex);
            GLES20.glAttachShader(nextProgram, fragment);
            GLES20.glLinkProgram(nextProgram);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(nextProgram, GLES20.GL_LINK_STATUS, linked, 0);
            String log = GLES20.glGetProgramInfoLog(nextProgram);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            if (linked[0] == 0) {
                GLES20.glDeleteProgram(nextProgram);
                throw new IllegalStateException("particle shader link failed: " + log);
            }
            return nextProgram;
        }

        private static int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("particle shader compile failed: " + log);
            }
            return shader;
        }

        private void releaseGlObjects() {
            deleteTexture(wallpaperTexture);
            wallpaperTexture = 0;
            deleteTexture(fallbackTexture);
            fallbackTexture = 0;
            deleteTexture(holeTexture);
            holeTexture = 0;
            deleteTexture(particleTexture);
            particleTexture = 0;
            backgroundReady = false;
            uploadedRawBackground = false;
            if (program != 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            if (revealProgram != 0) {
                GLES20.glDeleteProgram(revealProgram);
                revealProgram = 0;
            }
            if (holeProgram != 0) {
                GLES20.glDeleteProgram(holeProgram);
                holeProgram = 0;
            }
        }

        private static void deleteTexture(int texture) {
            if (texture != 0) {
                GLES20.glDeleteTextures(1, new int[] {texture}, 0);
            }
        }

        private static void recycle(Bitmap bitmap) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }

        private static void clearTransparent() {
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }

        private static void checkGl(String operation) {
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                throw new IllegalStateException(operation + " glError=0x"
                        + Integer.toHexString(error));
            }
        }

    }
}
