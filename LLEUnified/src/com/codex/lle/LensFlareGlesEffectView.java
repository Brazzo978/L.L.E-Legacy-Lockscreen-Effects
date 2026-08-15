package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.SoundPool;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Experimental GLES2 port of the current app-owned Canvas Lens Flare renderer. */
interface LensFlareGlesListener {
    void onSurfaceReady();
    void onResourcesReady(boolean hasBackground);
    void onFirstFrame();
    void onRendererFailure(Throwable error, String detail);
}

public final class LensFlareGlesEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer,
        RawArgb8888BackgroundRenderer, UnlockEffectReadiness,
        LensFlareGlesListener {
    private static final String TAG = "LLELensFlareGles";
    /**
     * App-owned GLES-only experimental style. Central integration may construct this view with
     * this value without adding an XLocker resource, shader, or sound to the application.
     */
    public static final String MODE_LIGHTNING = "lightning";
    private static final float BASE_FINGER_Y_OFFSET_PX = -80f;
    private static final float BASE_MAX_ALPHA_DISTANCE_PX = 1500f;
    private static final float BASE_TAP_AREA_RADIUS_PX = 600f;
    private static final float BASE_SCREEN_WIDTH_PX = 1080f;

    private final Object sceneLock = new Object();
    private final LensFlareScene scene;
    private final GlesView glView;
    private final String lensFlareMode;
    private final SoundPool soundPool;
    private final int tapSound;
    private final int unlockSound;
    private final float fingerYOffsetPx;
    private volatile int readinessState = STATE_CONSTRUCTED;
    private volatile String readinessDetail = "constructed";
    private volatile ReadinessListener readinessListener;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean warmedUp;
    private boolean pausedForDetach;
    private File rawBackgroundFile;
    private String backgroundSource = "none";
    private boolean backgroundAccepted;
    private float pendingAffordanceX;
    private float pendingAffordanceY;

    private final Runnable unlockAffordanceRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || gestureActive) {
                return;
            }
            synchronized (sceneLock) {
                scene.affordance(pendingAffordanceX, pendingAffordanceY,
                        SystemClock.uptimeMillis());
            }
            warmedUp = true;
            glView.activate();
        }
    };

    public LensFlareGlesEffectView(Context context) {
        this(context, OverlayPrefs.lensFlareMode(context));
    }

    /**
     * Stable construction hook for Lens-owned experimental styles. Existing callers should use
     * the no-argument mode constructor; unknown values safely normalize to the saved stock mode.
     */
    public LensFlareGlesEffectView(Context context, String requestedMode) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        float ratio = screenScaleRatio();
        fingerYOffsetPx = BASE_FINGER_Y_OFFSET_PX * ratio;
        lensFlareMode = normalizeMode(requestedMode);
        scene = new LensFlareScene(
                BASE_MAX_ALPHA_DISTANCE_PX * ratio,
                BASE_TAP_AREA_RADIUS_PX * ratio,
                BuildFlavor.TESTER,
                MODE_LIGHTNING.equals(lensFlareMode));
        glView = new GlesView(context, sceneLock, scene, this,
                MODE_LIGHTNING.equals(lensFlareMode)
                        ? "keyguard_flare_" : OverlayPrefs.lensFlareAssetPrefix(context));
        addView(glView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        tapSound = soundPool.load(context, R.raw.lens_flare_tap, 1);
        unlockSound = soundPool.load(context, R.raw.lens_flare_unlock, 1);
        Log.i(TAG, "GLES renderer constructed ratio=" + ratio
                + " mode=" + lensFlareMode);
    }

    /** Returns the app-owned lightning hook or one of the already persisted Lens modes. */
    public static String normalizeMode(String requestedMode) {
        if (MODE_LIGHTNING.equals(requestedMode)) {
            return MODE_LIGHTNING;
        }
        return OverlayPrefs.normalizeLensFlareMode(requestedMode);
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S4 lens flare (GLES, " + lensFlareMode + ")";
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
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        cancelAffordance();
        gestureActive = true;
        warmedUp = true;
        synchronized (sceneLock) {
            scene.begin(screenX, visualY(screenY), SystemClock.uptimeMillis());
        }
        play(tapSound);
        glView.activate();
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        synchronized (sceneLock) {
            scene.move(screenX, visualY(screenY));
        }
        glView.activate();
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        synchronized (sceneLock) {
            scene.finish(completed, SystemClock.uptimeMillis());
        }
        if (completed) {
            play(unlockSound);
        }
        glView.activate();
    }

    @Override
    public void cancelGesture() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        synchronized (sceneLock) {
            scene.cancel(SystemClock.uptimeMillis());
        }
        glView.activate();
    }

    @Override
    public void resetEffect() {
        cancelAffordance();
        gestureActive = false;
        synchronized (sceneLock) {
            scene.reset();
        }
        glView.clearScene();
    }

    @Override
    public void warmUp() {
        if (destroyed || warmedUp) {
            return;
        }
        warmedUp = true;
        synchronized (sceneLock) {
            scene.warmUp();
        }
        glView.activate();
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (destroyed) {
            return;
        }
        Rect safe = safeRect(screenRect);
        pendingAffordanceX = safe.exactCenterX();
        pendingAffordanceY = safe.exactCenterY();
        removeCallbacks(unlockAffordanceRunnable);
        postDelayed(unlockAffordanceRunnable, Math.max(0L, startDelayMs));
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
        // Compatibility-only path for a legacy PNG or a synthetic test source. Normal AUTO and
        // imported maps are delivered through setRawArgb8888BackgroundSource without this copy.
        Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
        if (copy == null || copy.isRecycled()) {
            return;
        }
        backgroundAccepted = true;
        backgroundSource = sourceName == null ? "bitmap_fallback" : sourceName;
        rawBackgroundFile = null;
        glView.setBackgroundBitmap(copy, backgroundSource);
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        backgroundAccepted = false;
        backgroundSource = "none";
        rawBackgroundFile = null;
        glView.clearBackground();
    }

    @Override
    public boolean hasRawArgb8888BackgroundSource() {
        return backgroundAccepted && rawBackgroundFile != null;
    }

    @Override
    public void setRawArgb8888BackgroundSource(File file, String sourceName) {
        Argb8888BitmapStore.Info info = Argb8888BitmapStore.inspect(file);
        if (destroyed || info == null || !info.raw) {
            return;
        }
        rawBackgroundFile = file;
        backgroundSource = sourceName == null ? "raw_argb8888" : sourceName;
        backgroundAccepted = true;
        glView.setBackgroundFile(file, backgroundSource);
        Log.i(TAG, "raw background accepted source=" + backgroundSource
                + " size=" + info.width + "x" + info.height
                + " fileKb=" + Math.max(1L, file.length() / 1024L));
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        backgroundAccepted = false;
        rawBackgroundFile = null;
        glView.destroyRenderer();
        soundPool.release();
        transition(STATE_FAILED, "destroyed");
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pausedForDetach && !destroyed) {
            glView.onResume();
            pausedForDetach = false;
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
        resetEffect();
        warmedUp = false;
        if (!destroyed) {
            glView.onPause();
            pausedForDetach = true;
            transition(STATE_DETACHED, "GLSurfaceView detached");
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onSurfaceReady() {
        transition(STATE_SURFACE_READY, "transparent EGL surface ready");
    }

    @Override
    public void onResourcesReady(boolean hasBackground) {
        transition(STATE_RESOURCES_READY, hasBackground
                ? "sprite and direct ARGB8888 textures ready"
                : "sprite textures ready; transparent fallback");
    }

    @Override
    public void onFirstFrame() {
        transition(STATE_FIRST_FRAME_READY, "first GLES frame drawn");
    }

    @Override
    public void onRendererFailure(Throwable error, String detail) {
        Log.e(TAG, "GLES renderer failed detail=" + detail, error);
        transition(STATE_FAILED, detail);
        if (!destroyed && OverlayPrefs.lensFlareGlesRendererEnabled(getContext())) {
            // Lens is the normal fallback for every other renderer, so a failed experimental
            // Lens path must explicitly return to Canvas instead of asking the service to
            // fallback to the same failing selection again.
            post(new Runnable() {
                @Override
                public void run() {
                    OverlayPrefs.get(getContext()).edit()
                            .putBoolean(OverlayPrefs.LENS_FLARE_GLES_RENDERER, false)
                            .apply();
                }
            });
        }
    }

    private void cancelAffordance() {
        removeCallbacks(unlockAffordanceRunnable);
    }

    private void play(int soundId) {
        if (!destroyed && soundId != 0
                && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private float visualY(float screenY) {
        return screenY + fingerYOffsetPx;
    }

    private float screenScaleRatio() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int smallestWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
        return smallestWidth <= 0 || smallestWidth == (int) BASE_SCREEN_WIDTH_PX
                ? 1f : smallestWidth / BASE_SCREEN_WIDTH_PX;
    }

    private Rect safeRect(Rect rect) {
        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            return rect;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return new Rect(0, 0, Math.max(1, metrics.widthPixels),
                Math.max(1, metrics.heightPixels));
    }

    private void transition(int state, String detail) {
        readinessState = state;
        readinessDetail = detail == null ? "" : detail;
        notifyReadiness();
    }

    private void notifyReadiness() {
        final ReadinessListener listener = readinessListener;
        if (listener != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onReadinessChanged();
                }
            });
        }
    }

    /** Transparent GLES2 surface and exact two-pass additive compositor. */
    static final class GlesView extends GLSurfaceView implements GLSurfaceView.Renderer {
        private static final int BACKGROUND_HIGHLIGHT_CHANNEL = 240;
        private static final float BACKGROUND_HIGHLIGHT_FRACTION = 0.10f;
        private static final float HIGH_BACKGROUND_DIM_ALPHA = 20f / 255f;
        private static final int BACKGROUND_BRIGHTNESS_SAMPLE_SIDE = 64;
        private static final float DEFAULT_IN_SAMPLE_SIZE = 2f;
        private static final long DESTROY_TIMEOUT_MS = 500L;

        private static final String SPRITE_VERTEX =
                "attribute vec2 aPosition; attribute vec2 aTexCoord;"
                + "varying vec2 vTexCoord;"
                + "void main(){gl_Position=vec4(aPosition,0.0,1.0);vTexCoord=aTexCoord;}";
        private static final String SPRITE_FRAGMENT =
                "precision highp float; varying vec2 vTexCoord;"
                + "uniform sampler2D uTexture; uniform float uAlpha;"
                + "void main(){gl_FragColor=texture2D(uTexture,vTexCoord)*uAlpha;}";
        private static final String LIGHTNING_VERTEX =
                "attribute vec2 aPosition;"
                + "void main(){gl_Position=vec4(aPosition,0.0,1.0);}";
        private static final String LIGHTNING_FRAGMENT =
                "precision mediump float; uniform vec4 uColor;"
                + "void main(){gl_FragColor=uColor;}";
        private static final String FINAL_VERTEX =
                "attribute vec2 aPosition; attribute vec2 aTexCoord; varying vec2 vTexCoord;"
                + "void main(){gl_Position=vec4(aPosition,0.0,1.0);vTexCoord=aTexCoord;}";
        private static final String FINAL_FRAGMENT =
                "precision highp float; varying vec2 vTexCoord;"
                + "uniform sampler2D uFlare; uniform sampler2D uBackground;"
                + "uniform sampler2D uVignette; uniform vec2 uBackgroundScale;"
                + "uniform vec2 uBackgroundOffset; uniform float uHasBackground;"
                + "uniform float uVignetteAlpha; uniform float uParentVignetteAlpha;"
                + "uniform float uBackgroundDimAlpha;"
                + "void main(){"
                + "vec4 f=texture2D(uFlare,vec2(vTexCoord.x,1.0-vTexCoord.y));"
                + "float fa=clamp(f.a,0.0,1.0); vec3 fr=min(max(f.rgb,vec3(0.0)),vec3(fa));"
                + "float va=texture2D(uVignette,vTexCoord).a;"
                + "float mask=clamp(va*uVignetteAlpha,0.0,1.0);"
                + "float parentMask=clamp(va*uParentVignetteAlpha,0.0,1.0);"
                + "float parentA=clamp(uBackgroundDimAlpha+parentMask*(1.0-uBackgroundDimAlpha),0.0,1.0);"
                + "if(uHasBackground<0.5){gl_FragColor=min(vec4(fr,fa)+vec4(0.0,0.0,0.0,parentMask),vec4(1.0));return;}"
                + "vec2 buv=uBackgroundOffset+vTexCoord*uBackgroundScale;"
                + "vec4 raw=texture2D(uBackground,buv); vec4 b=raw.bgra;"
                + "vec3 base=b.rgb*(1.0-mask)*(1.0-clamp(uBackgroundDimAlpha,0.0,1.0));"
                + "vec3 target=min(base+fr,vec3(1.0)); vec3 delta=max(target-base,vec3(0.0));"
                + "vec3 room=max(vec3(0.0001),vec3(1.0)-base);"
                + "float a=min(clamp(max(max(delta.r/room.r,delta.g/room.g),delta.b/room.b),0.0,1.0),fa);"
                + "vec3 premul=min(vec3(a),delta+base*a);"
                + "float layerA=a+parentA*(1.0-a); gl_FragColor=vec4(premul,layerA);}";

        private final Object sceneLock;
        private final LensFlareScene scene;
        private final LensFlareGlesListener listener;
        private final String[] assetNames;
        private final AtomicInteger animationGeneration = new AtomicInteger();
        private final FloatBuffer spriteVertices = allocateFloats(16);
        // One bolt is currently at most eight segments. Keep a little headroom for a future
        // longer procedural branch without allocating on the GL thread.
        private final FloatBuffer lightningVertices = allocateFloats(512);
        private final FloatBuffer fullScreenVertices = makeBuffer(new float[] {
                -1f, 1f, 0f, 0f, -1f, -1f, 0f, 1f,
                1f, 1f, 1f, 0f, 1f, -1f, 1f, 1f
        });
        private final int[] assetTextures = new int[LensFlareScene.ASSET_COUNT];
        private final int[] assetWidths = new int[LensFlareScene.ASSET_COUNT];
        private final int[] assetHeights = new int[LensFlareScene.ASSET_COUNT];
        private final Object sourceLock = new Object();
        private File backgroundFile;
        private Bitmap fallbackBitmap;
        private int backgroundSerial;
        private int uploadedBackgroundSerial = -1;
        private String backgroundSource = "none";
        private int surfaceWidth;
        private int surfaceHeight;
        private int spriteProgram;
        private int lightningProgram;
        private int finalProgram;
        private int vignetteTexture;
        private int backgroundTexture;
        private int flareTexture;
        private int flareFramebuffer;
        private boolean resourcesReady;
        private boolean destroyed;
        private boolean firstFrameReported;
        private boolean backgroundReady;
        private int backgroundWidth;
        private int backgroundHeight;
        private float backgroundDimAlpha;
        private float backgroundScaleX = 1f;
        private float backgroundScaleY = 1f;
        private float backgroundOffsetX;
        private float backgroundOffsetY;

        GlesView(Context context, Object sceneLock, LensFlareScene scene,
                LensFlareGlesListener listener, String assetPrefix) {
            super(context);
            this.sceneLock = sceneLock;
            this.scene = scene;
            this.listener = listener;
            assetNames = assetNames(assetPrefix);
            setZOrderOnTop(true);
            getHolder().setFormat(PixelFormat.TRANSLUCENT);
            setBackgroundColor(Color.TRANSPARENT);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8, 8, 8, 8, 0, 0);
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
                releaseGlObjects();
                spriteProgram = createProgram(SPRITE_VERTEX, SPRITE_FRAGMENT);
                lightningProgram = createProgram(LIGHTNING_VERTEX, LIGHTNING_FRAGMENT);
                finalProgram = createProgram(FINAL_VERTEX, FINAL_FRAGMENT);
                uploadAssets();
                vignetteTexture = uploadDrawable("keyguard_flare_vignetting", null);
                uploadedBackgroundSerial = -1;
                resourcesReady = true;
                firstFrameReported = false;
                clearTransparent();
                post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onSurfaceReady();
                    }
                });
            } catch (Throwable error) {
                fail(error, "surface init failed");
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            if (destroyed || width <= 0 || height <= 0) {
                return;
            }
            try {
                surfaceWidth = width;
                surfaceHeight = height;
                GLES20.glViewport(0, 0, width, height);
                createFlareTarget(width, height);
                uploadBackgroundIfNeeded();
                postResourcesReady();
            } catch (Throwable error) {
                fail(error, "surface resize failed");
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (destroyed || !resourcesReady || surfaceWidth <= 0 || surfaceHeight <= 0) {
                clearTransparent();
                return;
            }
            try {
                uploadBackgroundIfNeeded();
                LensFlareScene.Frame frame;
                synchronized (sceneLock) {
                    frame = scene.frame(SystemClock.uptimeMillis());
                }
                drawFlareTarget(frame);
                drawComposite(frame);
                if (!firstFrameReported) {
                    firstFrameReported = true;
                    post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onFirstFrame();
                        }
                    });
                }
                if (!frame.keepAnimating && !frame.warmFrameDrawn) {
                    scheduleIdle(animationGeneration.get());
                }
            } catch (Throwable error) {
                fail(error, "draw failed");
            }
        }

        void activate() {
            if (destroyed) {
                return;
            }
            animationGeneration.incrementAndGet();
            if (getRenderMode() != RENDERMODE_CONTINUOUSLY) {
                setRenderMode(RENDERMODE_CONTINUOUSLY);
            }
            requestRender();
        }

        void clearScene() {
            animationGeneration.incrementAndGet();
            requestRender();
        }

        void setBackgroundFile(File file, String sourceName) {
            synchronized (sourceLock) {
                recycleFallbackLocked();
                backgroundFile = file;
                backgroundSource = sourceName;
                backgroundSerial++;
            }
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    uploadBackgroundIfNeeded();
                }
            });
            activate();
        }

        void setBackgroundBitmap(Bitmap bitmap, String sourceName) {
            synchronized (sourceLock) {
                recycleFallbackLocked();
                fallbackBitmap = bitmap;
                backgroundFile = null;
                backgroundSource = sourceName;
                backgroundSerial++;
            }
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    uploadBackgroundIfNeeded();
                }
            });
            activate();
        }

        void clearBackground() {
            synchronized (sourceLock) {
                recycleFallbackLocked();
                backgroundFile = null;
                backgroundSource = "none";
                backgroundSerial++;
            }
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    deleteTexture(backgroundTexture);
                    backgroundTexture = 0;
                    backgroundReady = false;
                    uploadedBackgroundSerial = backgroundSerial;
                }
            });
            activate();
        }

        void destroyRenderer() {
            if (destroyed) {
                return;
            }
            destroyed = true;
            final CountDownLatch latch = new CountDownLatch(1);
            try {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        releaseGlObjects();
                        latch.countDown();
                    }
                });
                requestRender();
                latch.await(DESTROY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
            }
            synchronized (sourceLock) {
                recycleFallbackLocked();
                backgroundFile = null;
            }
            onPause();
        }

        private void scheduleIdle(final int generation) {
            post(new Runnable() {
                @Override
                public void run() {
                    if (!destroyed && generation == animationGeneration.get()
                            && getRenderMode() != RENDERMODE_WHEN_DIRTY) {
                        setRenderMode(RENDERMODE_WHEN_DIRTY);
                    }
                }
            });
        }

        private void drawFlareTarget(LensFlareScene.Frame frame) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, flareFramebuffer);
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
            GLES20.glUseProgram(spriteProgram);
            int position = GLES20.glGetAttribLocation(spriteProgram, "aPosition");
            int texCoord = GLES20.glGetAttribLocation(spriteProgram, "aTexCoord");
            int alpha = GLES20.glGetUniformLocation(spriteProgram, "uAlpha");
            GLES20.glUniform1i(GLES20.glGetUniformLocation(spriteProgram, "uTexture"), 0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glEnableVertexAttribArray(texCoord);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            for (LensFlareScene.Sprite sprite : frame.sprites) {
                int asset = sprite.asset;
                if (asset < 0 || asset >= assetTextures.length || assetTextures[asset] == 0) {
                    continue;
                }
                float factor = DEFAULT_IN_SAMPLE_SIZE * Math.max(0f, sprite.scale);
                float width = assetWidths[asset] * factor;
                float height = assetHeights[asset] * factor;
                fillSpriteVertices(sprite.x, sprite.y, width, height, sprite.rotation);
                spriteVertices.position(0);
                GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT,
                        false, 16, spriteVertices);
                spriteVertices.position(2);
                GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT,
                        false, 16, spriteVertices);
                GLES20.glUniform1f(alpha, sprite.alpha);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, assetTextures[asset]);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glDisableVertexAttribArray(texCoord);
            drawLightning(frame);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        /** Draws generated bolts into the same additive accumulation target as Lens sprites. */
        private void drawLightning(LensFlareScene.Frame frame) {
            if (lightningProgram == 0 || frame.lightningBolts.isEmpty()) {
                return;
            }
            GLES20.glUseProgram(lightningProgram);
            int position = GLES20.glGetAttribLocation(lightningProgram, "aPosition");
            int color = GLES20.glGetUniformLocation(lightningProgram, "uColor");
            GLES20.glEnableVertexAttribArray(position);
            for (LensFlareScene.LightningBolt bolt : frame.lightningBolts) {
                drawLightningBolt(position, color, bolt, bolt.glowWidthPx,
                        0.16f, 0.48f, 1f, 0.42f);
                drawLightningBolt(position, color, bolt, bolt.coreWidthPx,
                        0.82f, 0.94f, 1f, 1f);
            }
            GLES20.glDisableVertexAttribArray(position);
        }

        private void drawLightningBolt(int position, int color, LensFlareScene.LightningBolt bolt,
                float widthPx, float red, float green, float blue, float colorAlpha) {
            if (bolt.points == null || bolt.points.length < 4 || widthPx <= 0f) {
                return;
            }
            float alpha = Math.max(0f, Math.min(1f, bolt.alpha * colorAlpha));
            if (alpha <= 0f) {
                return;
            }
            int vertices = fillLightningVertices(bolt.points, widthPx);
            if (vertices == 0) {
                return;
            }
            GLES20.glUniform4f(color, red * alpha, green * alpha, blue * alpha, alpha);
            lightningVertices.position(0);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false,
                    2 * 4, lightningVertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices);
        }

        private int fillLightningVertices(float[] points, float widthPx) {
            lightningVertices.position(0);
            int vertexCount = 0;
            float halfWidth = widthPx * 0.5f;
            for (int index = 0; index + 3 < points.length; index += 2) {
                float x0 = points[index];
                float y0 = points[index + 1];
                float x1 = points[index + 2];
                float y1 = points[index + 3];
                float dx = x1 - x0;
                float dy = y1 - y0;
                float length = (float) Math.hypot(dx, dy);
                if (length < 0.01f || lightningVertices.remaining() < 12) {
                    continue;
                }
                float offsetX = -dy / length * halfWidth;
                float offsetY = dx / length * halfWidth;
                putLightningVertex(x0 - offsetX, y0 - offsetY);
                putLightningVertex(x0 + offsetX, y0 + offsetY);
                putLightningVertex(x1 - offsetX, y1 - offsetY);
                putLightningVertex(x1 - offsetX, y1 - offsetY);
                putLightningVertex(x0 + offsetX, y0 + offsetY);
                putLightningVertex(x1 + offsetX, y1 + offsetY);
                vertexCount += 6;
            }
            lightningVertices.position(0);
            return vertexCount;
        }

        private void putLightningVertex(float x, float y) {
            lightningVertices.put(x * 2f / surfaceWidth - 1f);
            lightningVertices.put(1f - y * 2f / surfaceHeight);
        }

        private void drawComposite(LensFlareScene.Frame frame) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glUseProgram(finalProgram);
            int position = GLES20.glGetAttribLocation(finalProgram, "aPosition");
            int texCoord = GLES20.glGetAttribLocation(finalProgram, "aTexCoord");
            fullScreenVertices.position(0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT,
                    false, 16, fullScreenVertices);
            fullScreenVertices.position(2);
            GLES20.glEnableVertexAttribArray(texCoord);
            GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT,
                    false, 16, fullScreenVertices);

            bindTexture(finalProgram, "uFlare", flareTexture, 0);
            bindTexture(finalProgram, "uBackground", backgroundTexture, 1);
            bindTexture(finalProgram, "uVignette", vignetteTexture, 2);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(finalProgram, "uHasBackground"),
                    backgroundReady ? 1f : 0f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(finalProgram, "uVignetteAlpha"),
                    frame.vignetteAlpha);
            float parentVignetteAlpha = Math.max(0, Math.min(255,
                    (int) (frame.vignetteAlpha * 255f))) / 255f;
            GLES20.glUniform1f(GLES20.glGetUniformLocation(
                    finalProgram, "uParentVignetteAlpha"), parentVignetteAlpha);
            // Keep highlight headroom only while a visible flare animation is alive. The
            // terminal frame is transparent and removes the dim before WHEN_DIRTY parking.
            float activeBackgroundDimAlpha = frame.keepAnimating ? backgroundDimAlpha : 0f;
            GLES20.glUniform1f(GLES20.glGetUniformLocation(
                    finalProgram, "uBackgroundDimAlpha"), activeBackgroundDimAlpha);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(finalProgram, "uBackgroundScale"),
                    backgroundScaleX, backgroundScaleY);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(finalProgram, "uBackgroundOffset"),
                    backgroundOffsetX, backgroundOffsetY);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glDisableVertexAttribArray(texCoord);
        }

        private void uploadAssets() {
            for (int i = 0; i < assetNames.length; i++) {
                final int slot = i;
                assetTextures[i] = uploadDrawable(assetNames[i], new BitmapObserver() {
                    @Override
                    public void onBitmap(Bitmap bitmap) {
                        assetWidths[slot] = bitmap.getWidth();
                        assetHeights[slot] = bitmap.getHeight();
                    }
                });
            }
        }

        private static String[] assetNames(String prefix) {
            return new String[] {
                    prefix + "light_00040", prefix + "ring",
                    prefix + "particle", prefix + "long",
                    prefix + "rainbow", prefix + "hoverlight",
                    prefix + "hexagon_blue", prefix + "hexagon_orange",
                    prefix + "hexagon_green"
            };
        }

        private int uploadDrawable(String name, BitmapObserver observer) {
            int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
            if (id == 0) {
                throw new IllegalStateException("missing Lens Flare asset " + name);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), id, options);
            if (bitmap == null || bitmap.isRecycled()) {
                throw new IllegalStateException("unreadable Lens Flare asset " + name);
            }
            try {
                if (observer != null) {
                    observer.onBitmap(bitmap);
                }
                return uploadBitmapTexture(bitmap);
            } finally {
                bitmap.recycle();
            }
        }

        private void uploadBackgroundIfNeeded() {
            int serial;
            File file;
            Bitmap bitmap;
            String source;
            synchronized (sourceLock) {
                serial = backgroundSerial;
                if (serial == uploadedBackgroundSerial) {
                    return;
                }
                file = backgroundFile;
                bitmap = fallbackBitmap;
                source = backgroundSource;
            }
            int nextTexture = 0;
            int width = 0;
            int height = 0;
            float dim = 0f;
            try {
                if (file != null) {
                    Argb8888BitmapStore.MappedImage image = Argb8888BitmapStore.map(file);
                    if (image == null) {
                        throw new IllegalStateException("raw ARGB8888 map failed CRC validation");
                    }
                    try {
                        nextTexture = uploadRawTexture(image);
                        width = image.width;
                        height = image.height;
                        dim = adaptiveDim(image.pixels(), width, height, image.rowBytes);
                    } finally {
                        image.close();
                    }
                } else if (bitmap != null && !bitmap.isRecycled()) {
                    nextTexture = uploadBitmapTexture(bitmap);
                    width = bitmap.getWidth();
                    height = bitmap.getHeight();
                    dim = adaptiveDim(bitmap);
                }
                deleteTexture(backgroundTexture);
                backgroundTexture = nextTexture;
                backgroundWidth = width;
                backgroundHeight = height;
                backgroundDimAlpha = dim;
                backgroundReady = nextTexture != 0;
                updateBackgroundCrop();
                uploadedBackgroundSerial = serial;
                Log.i(TAG, "background uploaded direct=" + (file != null)
                        + " source=" + source + " size=" + width + "x" + height
                        + " dimAlpha=" + dim);
                postResourcesReady();
            } catch (Throwable error) {
                deleteTexture(nextTexture);
                uploadedBackgroundSerial = serial;
                backgroundReady = false;
                fail(error, "background upload failed");
            }
        }

        private int uploadRawTexture(Argb8888BitmapStore.MappedImage image) {
            int texture = generateTexture();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    image.width, image.height, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, image.pixels());
            checkGl("raw background upload");
            return texture;
        }

        private int uploadBitmapTexture(Bitmap bitmap) {
            int texture = generateTexture();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            checkGl("bitmap upload");
            return texture;
        }

        private int generateTexture() {
            int[] texture = new int[1];
            GLES20.glGenTextures(1, texture, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            return texture[0];
        }

        private void createFlareTarget(int width, int height) {
            deleteFramebuffer(flareFramebuffer);
            deleteTexture(flareTexture);
            flareTexture = generateTexture();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, flareTexture);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            int[] framebuffer = new int[1];
            GLES20.glGenFramebuffers(1, framebuffer, 0);
            flareFramebuffer = framebuffer[0];
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, flareFramebuffer);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, flareTexture, 0);
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
                    != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Lens Flare accumulation framebuffer incomplete");
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        private void fillSpriteVertices(float cx, float cy, float width, float height,
                float rotationDegrees) {
            double radians = Math.toRadians(rotationDegrees);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            float halfWidth = width * 0.5f;
            float halfHeight = height * 0.5f;
            float[] localX = {-halfWidth, -halfWidth, halfWidth, halfWidth};
            float[] localY = {-halfHeight, halfHeight, -halfHeight, halfHeight};
            float[] u = {0f, 0f, 1f, 1f};
            float[] v = {0f, 1f, 0f, 1f};
            spriteVertices.position(0);
            for (int i = 0; i < 4; i++) {
                float x = cx + localX[i] * cos - localY[i] * sin;
                float y = cy + localX[i] * sin + localY[i] * cos;
                spriteVertices.put(x * 2f / surfaceWidth - 1f);
                spriteVertices.put(1f - y * 2f / surfaceHeight);
                spriteVertices.put(u[i]);
                spriteVertices.put(v[i]);
            }
            spriteVertices.position(0);
        }

        private void updateBackgroundCrop() {
            backgroundScaleX = 1f;
            backgroundScaleY = 1f;
            backgroundOffsetX = 0f;
            backgroundOffsetY = 0f;
            if (backgroundWidth <= 0 || backgroundHeight <= 0
                    || surfaceWidth <= 0 || surfaceHeight <= 0) {
                return;
            }
            float sourceRatio = backgroundWidth / (float) backgroundHeight;
            float targetRatio = surfaceWidth / (float) surfaceHeight;
            if (sourceRatio > targetRatio) {
                backgroundScaleX = targetRatio / sourceRatio;
                backgroundOffsetX = (1f - backgroundScaleX) * 0.5f;
            } else if (sourceRatio < targetRatio) {
                backgroundScaleY = sourceRatio / targetRatio;
                backgroundOffsetY = (1f - backgroundScaleY) * 0.5f;
            }
        }

        private float adaptiveDim(ByteBuffer pixels, int width, int height, int rowBytes) {
            int columns = Math.max(1, Math.min(BACKGROUND_BRIGHTNESS_SAMPLE_SIDE, width));
            int rows = Math.max(1, Math.min(BACKGROUND_BRIGHTNESS_SAMPLE_SIDE, height));
            int bright = 0;
            for (int row = 0; row < rows; row++) {
                int y = rows == 1 ? 0 : Math.round(row * (height - 1f) / (rows - 1f));
                for (int column = 0; column < columns; column++) {
                    int x = columns == 1 ? 0
                            : Math.round(column * (width - 1f) / (columns - 1f));
                    int offset = y * rowBytes + x * 4;
                    int blue = pixels.get(offset) & 0xff;
                    int green = pixels.get(offset + 1) & 0xff;
                    int red = pixels.get(offset + 2) & 0xff;
                    if (Math.max(red, Math.max(green, blue))
                            >= BACKGROUND_HIGHLIGHT_CHANNEL) {
                        bright++;
                    }
                }
            }
            float fraction = bright / (float) (columns * rows);
            return fraction >= BACKGROUND_HIGHLIGHT_FRACTION
                    ? HIGH_BACKGROUND_DIM_ALPHA : 0f;
        }

        private float adaptiveDim(Bitmap bitmap) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int columns = Math.max(1, Math.min(BACKGROUND_BRIGHTNESS_SAMPLE_SIDE, width));
            int rows = Math.max(1, Math.min(BACKGROUND_BRIGHTNESS_SAMPLE_SIDE, height));
            int bright = 0;
            for (int row = 0; row < rows; row++) {
                int y = rows == 1 ? 0 : Math.round(row * (height - 1f) / (rows - 1f));
                for (int column = 0; column < columns; column++) {
                    int x = columns == 1 ? 0
                            : Math.round(column * (width - 1f) / (columns - 1f));
                    int color = bitmap.getPixel(x, y);
                    if (Math.max(Color.red(color),
                            Math.max(Color.green(color), Color.blue(color)))
                            >= BACKGROUND_HIGHLIGHT_CHANNEL) {
                        bright++;
                    }
                }
            }
            return bright / (float) (columns * rows) >= BACKGROUND_HIGHLIGHT_FRACTION
                    ? HIGH_BACKGROUND_DIM_ALPHA : 0f;
        }

        private void postResourcesReady() {
            if (!resourcesReady || surfaceWidth <= 0 || surfaceHeight <= 0) {
                return;
            }
            final boolean hasBackground = backgroundReady;
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onResourcesReady(hasBackground);
                }
            });
        }

        private void bindTexture(int program, String uniform, int texture, int unit) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, uniform), unit);
        }

        private static int createProgram(String vertexSource, String fragmentSource) {
            int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertex);
            GLES20.glAttachShader(program, fragment);
            GLES20.glLinkProgram(program);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            if (linked[0] == 0) {
                GLES20.glDeleteProgram(program);
                throw new IllegalStateException("Lens Flare program link failed: " + log);
            }
            return program;
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
                throw new IllegalStateException("Lens Flare shader compile failed: " + log);
            }
            return shader;
        }

        private void releaseGlObjects() {
            deleteFramebuffer(flareFramebuffer);
            flareFramebuffer = 0;
            deleteTexture(flareTexture);
            flareTexture = 0;
            deleteTexture(backgroundTexture);
            backgroundTexture = 0;
            deleteTexture(vignetteTexture);
            vignetteTexture = 0;
            for (int i = 0; i < assetTextures.length; i++) {
                deleteTexture(assetTextures[i]);
                assetTextures[i] = 0;
            }
            if (spriteProgram != 0) {
                GLES20.glDeleteProgram(spriteProgram);
                spriteProgram = 0;
            }
            if (lightningProgram != 0) {
                GLES20.glDeleteProgram(lightningProgram);
                lightningProgram = 0;
            }
            if (finalProgram != 0) {
                GLES20.glDeleteProgram(finalProgram);
                finalProgram = 0;
            }
            backgroundReady = false;
        }

        private static void deleteTexture(int texture) {
            if (texture != 0) {
                GLES20.glDeleteTextures(1, new int[] {texture}, 0);
            }
        }

        private static void deleteFramebuffer(int framebuffer) {
            if (framebuffer != 0) {
                GLES20.glDeleteFramebuffers(1, new int[] {framebuffer}, 0);
            }
        }

        private void recycleFallbackLocked() {
            if (fallbackBitmap != null && !fallbackBitmap.isRecycled()) {
                fallbackBitmap.recycle();
            }
            fallbackBitmap = null;
        }

        private void clearTransparent() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }

        private void fail(final Throwable error, final String detail) {
            resourcesReady = false;
            clearTransparent();
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onRendererFailure(error, detail);
                }
            });
        }

        private static void checkGl(String operation) {
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                throw new IllegalStateException(operation + " glError=0x"
                        + Integer.toHexString(error));
            }
        }

        private static FloatBuffer allocateFloats(int count) {
            return ByteBuffer.allocateDirect(count * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        }

        private static FloatBuffer makeBuffer(float[] values) {
            FloatBuffer buffer = allocateFloats(values.length);
            buffer.put(values);
            buffer.position(0);
            return buffer;
        }

        private interface BitmapObserver {
            void onBitmap(Bitmap bitmap);
        }
    }
}
