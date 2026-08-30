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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

interface LgPixelateRendererListener {
    void onSurfaceReady();
    void onResourcesReady(boolean hasPrimary, boolean hasSecondary);
    void onFirstFrame();
    void onFailure(Throwable error, String detail);
}

/**
 * Clean-room GLES2 restoration of LG G2 Pixelate.
 *
 * <p>The regular lockscreen capture is the primary triangular mosaic. The independent
 * Last-screen cache is the stable full-screen underlay revealed by the donor-shaped alpha mask.
 * Neither cache replaces the other.</p>
 */
public final class LgPixelateEffectView extends FrameLayout
        implements UnlockEffectRenderer, BackgroundSourceRenderer, RawArgb8888BackgroundRenderer,
        SecondaryBackgroundSourceRenderer, UnlockEffectReadiness, LgPixelateRendererListener {
    private static final String TAG = "LLELgPixelate";

    private final Object sceneLock = new Object();
    private final Object soundLock = new Object();
    private final LgPixelateScene scene = new LgPixelateScene();
    private final PixelateGlSurface glSurface;
    private final SoundPool soundPool;
    private final int touchdownSound;
    private final int unlockSound;
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();
    private volatile int readinessState = STATE_CONSTRUCTED;
    private volatile String readinessDetail = "constructed";
    private volatile ReadinessListener readinessListener;
    private boolean destroyed;
    private boolean attached;
    private boolean gestureActive;
    private boolean primaryAccepted;
    private boolean secondaryAccepted;
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
            glSurface.showAndActivate();
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
        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                handleSoundLoadComplete(pool, sampleId, status);
            }
        });
        touchdownSound = soundPool.load(context, R.raw.lg_pixelate_touchdown, 1);
        unlockSound = soundPool.load(context, R.raw.lg_pixelate_unlock, 1);
    }

    @Override public View asView() { return this; }
    @Override public String effectName() { return "G2 Pixelate (app-owned GLES)"; }
    @Override public int getReadinessState() { return readinessState; }
    @Override public String getReadinessDetail() { return effectName() + ": " + readinessDetail; }

    @Override public void setReadinessListener(ReadinessListener listener) {
        readinessListener = listener;
        notifyReadiness();
    }

    public void setHighFrameRateEnabled(boolean enabled) {
        glSurface.setHighFrameRateEnabled(enabled);
    }
    public boolean isHighFrameRateEnabled() { return glSurface.isHighFrameRateEnabled(); }
    public void setSpeedMultiplier(float multiplier) {
        glSurface.setSpeedMultiplier(sanitizeSpeedMultiplier(multiplier));
    }
    public float getSpeedMultiplier() { return glSurface.getSpeedMultiplier(); }

    @Override public void beginGesture(float x, float y) {
        if (destroyed) return;
        removeCallbacks(affordanceRunnable);
        gestureActive = true;
        synchronized (sceneLock) { scene.begin(x, y, SystemClock.uptimeMillis()); }
        playSound(touchdownSound);
        glSurface.showAndActivate();
    }

    @Override public void updateGesture(float x, float y) {
        if (destroyed) return;
        if (!gestureActive) {
            beginGesture(x, y);
            return;
        }
        synchronized (sceneLock) { scene.move(x, y, SystemClock.uptimeMillis()); }
        glSurface.showAndActivate();
    }

    @Override public void finishGesture(boolean completed) {
        if (destroyed || !gestureActive) return;
        gestureActive = false;
        synchronized (sceneLock) { scene.finish(completed, SystemClock.uptimeMillis()); }
        if (completed) playSound(unlockSound);
        glSurface.showAndActivate();
    }

    @Override public void cancelGesture() {
        if (destroyed || !gestureActive) return;
        gestureActive = false;
        synchronized (sceneLock) { scene.cancel(SystemClock.uptimeMillis()); }
        glSurface.showAndActivate();
    }

    @Override public void resetEffect() {
        removeCallbacks(affordanceRunnable);
        gestureActive = false;
        synchronized (sceneLock) { scene.reset(); }
        glSurface.hideSurface();
    }

    @Override public void warmUp() {
        if (destroyed) return;
        if (readinessState >= STATE_FIRST_FRAME_READY) glSurface.activate();
        else glSurface.showAndActivate();
    }

    @Override public void showUnlockAffordance(Rect rect, long delayMs) {
        if (destroyed) return;
        Rect safe = rect != null && rect.width() > 0 && rect.height() > 0
                ? rect : displayRect();
        affordanceX = safe.exactCenterX();
        affordanceY = safe.exactCenterY();
        removeCallbacks(affordanceRunnable);
        postDelayed(affordanceRunnable, Math.max(0L, delayMs));
    }

    @Override public boolean hasBackgroundSourceBitmap() { return primaryAccepted; }

    @Override public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        Bitmap owned = ownedCopy(source);
        if (destroyed || owned == null) return;
        rawBackgroundFile = null;
        rawBackgroundLength = 0L;
        rawBackgroundModified = 0L;
        primaryAccepted = true;
        glSurface.setPrimaryBitmap(owned);
    }

    @Override public void clearBackgroundSourceBitmap() {
        rawBackgroundFile = null;
        rawBackgroundLength = 0L;
        rawBackgroundModified = 0L;
        primaryAccepted = false;
        glSurface.clearPrimary();
    }

    @Override public boolean hasRawArgb8888BackgroundSource() {
        return primaryAccepted && rawBackgroundFile != null;
    }

    @Override public void setRawArgb8888BackgroundSource(File file, String sourceName) {
        Argb8888BitmapStore.Info info = Argb8888BitmapStore.inspect(file);
        if (destroyed || info == null || !info.raw) return;
        long length = file.length();
        long modified = file.lastModified();
        if (primaryAccepted && rawBackgroundFile != null
                && rawBackgroundFile.getAbsolutePath().equals(file.getAbsolutePath())
                && rawBackgroundLength == length && rawBackgroundModified == modified) return;
        rawBackgroundFile = file;
        rawBackgroundLength = length;
        rawBackgroundModified = modified;
        primaryAccepted = true;
        glSurface.setPrimaryFile(file);
        Log.i(TAG, "raw lockscreen mosaic accepted " + info.width + "x" + info.height);
    }

    @Override public boolean hasSecondaryBackgroundSourceBitmap() { return secondaryAccepted; }

    @Override public void setSecondaryBackgroundSourceBitmap(Bitmap source, String sourceName) {
        Bitmap owned = ownedCopy(source);
        if (destroyed || owned == null) return;
        secondaryAccepted = true;
        glSurface.setSecondaryBitmap(owned);
    }

    @Override public void clearSecondaryBackgroundSourceBitmap() {
        secondaryAccepted = false;
        glSurface.clearSecondary();
    }

    @Override public void destroy() {
        if (destroyed) return;
        removeCallbacks(affordanceRunnable);
        gestureActive = false;
        synchronized (sceneLock) { scene.reset(); }
        destroyed = true;
        glSurface.hideSurface();
        primaryAccepted = secondaryAccepted = false;
        rawBackgroundFile = null;
        rawBackgroundLength = rawBackgroundModified = 0L;
        glSurface.destroyRenderer();
        synchronized (soundLock) {
            pendingSoundIds.clear();
            loadedSoundIds.clear();
            soundPool.setOnLoadCompleteListener(null);
            soundPool.release();
        }
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
            transition(STATE_DETACHED, "GLSurfaceView detached; cache copies retained");
        }
        super.onDetachedFromWindow();
    }

    @Override public void onSurfaceReady() {
        advanceReadiness(STATE_SURFACE_READY, "transparent EGL surface ready");
    }

    @Override public void onResourcesReady(boolean hasPrimary, boolean hasSecondary) {
        if (hasPrimary) {
            advanceReadiness(STATE_RESOURCES_READY, hasSecondary
                    ? "lockscreen mosaic and Last screen textures ready"
                    : "lockscreen mosaic ready; waiting for Last screen fallback");
        } else if (attached && !destroyed && readinessState >= STATE_CONSTRUCTED
                && readinessState < STATE_RESOURCES_READY) {
            transition(Math.max(readinessState, STATE_SURFACE_READY),
                    "waiting for lockscreen mosaic cache");
        }
    }

    @Override public void onFirstFrame() {
        advanceReadiness(STATE_FIRST_FRAME_READY, "first donor-mesh frame drawn");
    }

    @Override public void onFailure(Throwable error, String detail) {
        Log.e(TAG, "renderer failure " + detail, error);
        transition(STATE_FAILED, detail);
    }

    static float sanitizeSpeedMultiplier(float value) {
        return Float.isNaN(value) || Float.isInfinite(value)
                ? 1f : Math.max(1f, Math.min(2f, value));
    }

    private static Bitmap ownedCopy(Bitmap source) {
        if (source == null || source.isRecycled()) return null;
        Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
        return copy == null || copy.isRecycled() ? null : copy;
    }

    private Rect displayRect() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return new Rect(0, 0, Math.max(1, metrics.widthPixels),
                Math.max(1, metrics.heightPixels));
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

    private void handleSoundLoadComplete(SoundPool completedPool, int sampleId, int status) {
        synchronized (soundLock) {
            if (completedPool != soundPool || destroyed) return;
            if (status != 0) {
                pendingSoundIds.remove(sampleId);
                Log.w(TAG, "sound load failed id=" + sampleId + " status=" + status);
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
        readinessDetail = detail;
        notifyReadiness();
    }

    private void advanceReadiness(int state, String detail) {
        if (attached && !destroyed && readinessState != STATE_FAILED && state >= readinessState) {
            transition(state, detail);
        }
    }

    private void notifyReadiness() {
        final ReadinessListener listener = readinessListener;
        if (listener != null && attached && !destroyed) post(new Runnable() {
            @Override public void run() { listener.onReadinessChanged(); }
        });
    }

    /** GLES thread owns texture creation and all draws; source copies survive context loss. */
    static final class PixelateGlSurface extends GLSurfaceView implements GLSurfaceView.Renderer {
        private static final long LEGACY_FRAME_NS = 16666667L;
        private static final String MESH_VERTEX =
                "attribute vec2 aPosition;attribute vec2 aTexCoord;"
                + "attribute vec2 aTexCoord2;attribute float aUserAttrib;"
                + "uniform vec2 uSurface,uCropScale,uCropOffset;uniform float uMeshScale;"
                + "varying vec2 vUv;varying vec2 vUv2;varying float vUserAlpha;"
                + "void main(){vec2 center=uSurface*.5;vec2 p=center+(aPosition-center)*uMeshScale;"
                + "gl_Position=vec4(p.x/uSurface.x*2.-1.,1.-p.y/uSurface.y*2.,0.,1.);"
                // The model mesh expands about the display centre.  Apply the matching
                // inverse screen-space compensation to both UV streams so the captured
                // lockscreen remains anchored while only the triangular cell size changes.
                // Scaling UVs about (0,0) makes the image drift up-left and eventually
                // clamps almost the whole screen to edge texels.
                + "vec2 t=.5+(aTexCoord-.5)*uMeshScale;"
                + "vec2 t2=.5+(aTexCoord2-.5)*uMeshScale;"
                + "vUv=uCropOffset+t*uCropScale;vUv2=uCropOffset+t2*uCropScale;"
                + "vUserAlpha=aUserAttrib;}";
        private static final String MESH_FRAGMENT =
                "precision mediump float;varying vec2 vUv;varying vec2 vUv2;"
                + "varying float vUserAlpha;uniform sampler2D uMap;"
                + "uniform float uTouch,uAlpha;"
                + "void main(){vec4 normal=texture2D(uMap,vUv);vec4 mosaic=texture2D(uMap,vUv2);"
                + "vec4 c=mix(normal,mosaic,step(.5,uTouch));"
                + "c.a*=vUserAlpha*uAlpha;gl_FragColor=c;}";
        private static final String REVEAL_VERTEX =
                "attribute vec2 aPosition;attribute float aEffectAttrib;"
                + "uniform vec2 uSurface,uCropScale,uCropOffset;uniform float uMeshScale;"
                + "varying vec2 vUv;varying float vEffectAlpha;"
                + "void main(){vec2 center=uSurface*.5;vec2 p=center+(aPosition-center)*uMeshScale;"
                + "gl_Position=vec4(p.x/uSurface.x*2.-1.,1.-p.y/uSurface.y*2.,0.,1.);"
                + "vUv=uCropOffset+(p/uSurface)*uCropScale;vEffectAlpha=aEffectAttrib;}";
        private static final String REVEAL_FRAGMENT =
                "precision mediump float;varying vec2 vUv;varying float vEffectAlpha;"
                + "uniform sampler2D uMap;uniform float uForceFull;"
                + "void main(){vec4 c=texture2D(uMap,vUv);"
                + "c.a*=mix(vEffectAlpha,1.,uForceFull);gl_FragColor=c;}";

        private final Object sceneLock;
        private final LgPixelateScene scene;
        private final LgPixelateRendererListener listener;
        private final Object sourceLock = new Object();
        private Bitmap primaryBitmap;
        private File primaryRawFile;
        private boolean primaryRaw;
        private int primarySerial;
        private int uploadedPrimarySerial = -1;
        private Bitmap secondaryBitmap;
        private int secondarySerial;
        private int uploadedSecondarySerial = -1;
        private boolean destroyed;
        private boolean paused;
        private boolean hfr;
        private float speed = 1f;
        private int revealProgram;
        private int meshProgram;
        private int primaryTexture;
        private int secondaryTexture;
        private int width;
        private int height;
        private int primaryWidth;
        private int primaryHeight;
        private int secondaryWidth;
        private int secondaryHeight;
        private boolean primaryReady;
        private boolean secondaryReady;
        private boolean firstFrame;
        private volatile long surfaceActivitySerial;
        private boolean idleHidePosted;
        private int transparentIdleFrames;
        private long lastPresentationNs;
        private LgPixelateMesh mesh;
        private FloatBuffer positionBuffer;
        private FloatBuffer textureBuffer;
        private FloatBuffer mosaicBuffer;
        private FloatBuffer alphaBuffer;
        private FloatBuffer effectBuffer;

        PixelateGlSurface(Context context, Object lock, LgPixelateScene scene,
                LgPixelateRendererListener listener) {
            super(context);
            sceneLock = lock;
            this.scene = scene;
            this.listener = listener;
            setZOrderOnTop(true);
            getHolder().setFormat(PixelFormat.TRANSLUCENT);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8, 8, 8, 8, 0, 0);
            setPreserveEGLContextOnPause(true);
            setRenderer(this);
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }

        void setHighFrameRateEnabled(boolean value) { hfr = value; activate(); }
        boolean isHighFrameRateEnabled() { return hfr; }
        void setSpeedMultiplier(float value) { speed = value; activate(); }
        float getSpeedMultiplier() { return speed; }
        void activate() { if (!destroyed && getVisibility() == VISIBLE) requestRender(); }

        void showAndActivate() {
            if (destroyed) return;
            surfaceActivitySerial++;
            idleHidePosted = false;
            transparentIdleFrames = 0;
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            requestRender();
        }

        void hideSurface() {
            surfaceActivitySerial++;
            idleHidePosted = false;
            // SurfaceView owns a separate SurfaceFlinger layer.  Making the child invisible
            // removes that layer even when EGL is paused before a transparent clear can swap.
            if (getVisibility() != INVISIBLE) setVisibility(INVISIBLE);
        }

        void pauseForDetach() {
            hideSurface();
            if (!paused) {
                paused = true;
                onPause();
            }
        }
        void resumeIfNeeded() { if (paused && !destroyed) { onResume(); paused = false; } }

        void setPrimaryBitmap(Bitmap value) {
            synchronized (sourceLock) {
                recycle(primaryBitmap);
                primaryBitmap = value;
                primaryRawFile = null;
                primaryRaw = false;
                primarySerial++;
            }
            activate();
        }

        void setPrimaryFile(File value) {
            synchronized (sourceLock) {
                recycle(primaryBitmap);
                primaryBitmap = null;
                primaryRawFile = value;
                primaryRaw = true;
                primarySerial++;
            }
            activate();
        }

        void clearPrimary() {
            synchronized (sourceLock) {
                recycle(primaryBitmap);
                primaryBitmap = null;
                primaryRawFile = null;
                primaryRaw = false;
                primarySerial++;
            }
            activate();
        }

        void setSecondaryBitmap(Bitmap value) {
            synchronized (sourceLock) {
                recycle(secondaryBitmap);
                secondaryBitmap = value;
                secondarySerial++;
            }
            activate();
        }

        void clearSecondary() {
            synchronized (sourceLock) {
                recycle(secondaryBitmap);
                secondaryBitmap = null;
                secondarySerial++;
            }
            activate();
        }

        void destroyRenderer() {
            destroyed = true;
            synchronized (sourceLock) {
                recycle(primaryBitmap);
                recycle(secondaryBitmap);
                primaryBitmap = secondaryBitmap = null;
                primaryRawFile = null;
                primarySerial++;
                secondarySerial++;
            }
            queueEvent(new Runnable() { @Override public void run() { releaseGl(); } });
        }

        @Override public void onSurfaceCreated(GL10 ignored, EGLConfig config) {
            if (destroyed) return;
            try {
                releaseGl();
                revealProgram = program(REVEAL_VERTEX, REVEAL_FRAGMENT);
                meshProgram = program(MESH_VERTEX, MESH_FRAGMENT);
                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                GLES20.glDisable(GLES20.GL_CULL_FACE);
                GLES20.glEnable(GLES20.GL_BLEND);
                // The EGL layer is composited as premultiplied RGBA.  Keep RGB blended by
                // source alpha, but accumulate framebuffer alpha separately.  Using the same
                // SRC_ALPHA factor for alpha squared partially transparent values and made the
                // lockscreen mosaic look colour-shifted/washed while the opaque hole source
                // remained correct.
                GLES20.glBlendFuncSeparate(
                        GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                        GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                clear();
                GLES20.glFinish();
                uploadedPrimarySerial = uploadedSecondarySerial = -1;
                firstFrame = false;
                idleHidePosted = false;
                transparentIdleFrames = 0;
                post(new Runnable() { @Override public void run() { listener.onSurfaceReady(); } });
            } catch (Throwable error) {
                fail(error, "surface initialization failed");
            }
        }

        @Override public void onSurfaceChanged(GL10 ignored, int newWidth, int newHeight) {
            if (destroyed || newWidth <= 0 || newHeight <= 0) return;
            width = newWidth;
            height = newHeight;
            GLES20.glViewport(0, 0, width, height);
            rebuildMesh();
            uploadIfNeeded();
            ready();
        }

        @Override public void onDrawFrame(GL10 ignored) {
            if (destroyed || revealProgram == 0 || meshProgram == 0
                    || width <= 0 || height <= 0) {
                return;
            }
            try {
                uploadIfNeeded();
                long nanos = System.nanoTime();
                if (!hfr && lastPresentationNs != 0L
                        && nanos - lastPresentationNs < LEGACY_FRAME_NS) return;
                float density = Math.max(.5f, getResources().getDisplayMetrics().density);
                float diagonal = (float) Math.hypot(width, height);
                LgPixelateScene.Frame frame;
                synchronized (sceneLock) {
                    frame = scene.frameAt(SystemClock.uptimeMillis(), 120f * density,
                            diagonal, speed);
                }
                if (!primaryReady || !frame.visible) {
                    clear();
                    // Complete rendering before retiring the SurfaceView.  The hide is the
                    // fail-closed boundary; the transparent frame avoids a one-frame flash on
                    // compositors which latch the final buffer while removing the layer.
                    GLES20.glFinish();
                    transparentIdleFrames++;
                    if (transparentIdleFrames >= 3) postIdleHideIfUnchanged();
                } else {
                    draw(frame);
                    idleHidePosted = false;
                    transparentIdleFrames = 0;
                }
                lastPresentationNs = nanos;
                if (!firstFrame && primaryReady) {
                    firstFrame = true;
                    post(new Runnable() { @Override public void run() { listener.onFirstFrame(); } });
                }
            } catch (Throwable error) {
                fail(error, "draw failed");
            }
        }

        private void rebuildMesh() {
            mesh = LgPixelateMesh.build(width, height);
            positionBuffer = buffer(mesh.positions);
            textureBuffer = buffer(mesh.textureCoordinates);
            mosaicBuffer = buffer(mesh.mosaicCoordinates);
            alphaBuffer = buffer(mesh.userAlpha);
            effectBuffer = buffer(mesh.effectAlpha);
        }

        private void uploadIfNeeded() {
            synchronized (sourceLock) {
                if (uploadedPrimarySerial != primarySerial) {
                    deletePrimaryTexture();
                    primaryReady = false;
                    primaryWidth = primaryHeight = 0;
                    if (primaryRaw && primaryRawFile != null) {
                        Argb8888BitmapStore.MappedImage image =
                                Argb8888BitmapStore.map(primaryRawFile);
                        if (image != null) try {
                            primaryTexture = rawUpload(image);
                            primaryWidth = image.width;
                            primaryHeight = image.height;
                            primaryReady = primaryTexture != 0;
                        } finally {
                            image.close();
                        }
                    } else if (primaryBitmap != null && !primaryBitmap.isRecycled()) {
                        primaryTexture = bitmapUpload(primaryBitmap);
                        primaryWidth = primaryBitmap.getWidth();
                        primaryHeight = primaryBitmap.getHeight();
                        primaryReady = primaryTexture != 0;
                    }
                    uploadedPrimarySerial = primarySerial;
                }
                if (uploadedSecondarySerial != secondarySerial) {
                    deleteSecondaryTexture();
                    secondaryReady = false;
                    secondaryWidth = secondaryHeight = 0;
                    if (secondaryBitmap != null && !secondaryBitmap.isRecycled()) {
                        secondaryTexture = bitmapUpload(secondaryBitmap);
                        secondaryWidth = secondaryBitmap.getWidth();
                        secondaryHeight = secondaryBitmap.getHeight();
                        secondaryReady = secondaryTexture != 0;
                    }
                    uploadedSecondarySerial = secondarySerial;
                }
            }
            ready();
        }

        private void draw(LgPixelateScene.Frame frame) {
            clear();
            if (mesh == null) rebuildMesh();
            mesh.updateUserAlpha(frame.x, frame.y, frame.dragPx, frame.meshScale);
            alphaBuffer.position(0);
            alphaBuffer.put(mesh.userAlpha);
            alphaBuffer.position(0);
            effectBuffer.position(0);
            effectBuffer.put(mesh.effectAlpha);
            effectBuffer.position(0);
            if (secondaryReady && frame.revealUnderlay) drawUnderlay(frame);
            if (frame.primaryVisible) drawPrimary(frame);
        }

        private void drawUnderlay(LgPixelateScene.Frame frame) {
            GLES20.glUseProgram(revealProgram);
            int position = attribute(revealProgram, "aPosition", positionBuffer, 2);
            int effectAlpha = attribute(revealProgram, "aEffectAttrib", effectBuffer, 1);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, secondaryTexture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(revealProgram, "uMap"), 0);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(revealProgram, "uSurface"),
                    width, height);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(revealProgram, "uMeshScale"),
                    1f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(revealProgram, "uForceFull"), 1f);
            setCropUniforms(revealProgram, secondaryWidth, secondaryHeight);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount);
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glDisableVertexAttribArray(effectAlpha);
        }

        private void drawPrimary(LgPixelateScene.Frame frame) {
            GLES20.glUseProgram(meshProgram);
            int position = attribute(meshProgram, "aPosition", positionBuffer, 2);
            int uv = attribute(meshProgram, "aTexCoord", textureBuffer, 2);
            int mosaicUv = attribute(meshProgram, "aTexCoord2", mosaicBuffer, 2);
            int userAlpha = attribute(meshProgram, "aUserAttrib", alphaBuffer, 1);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, primaryTexture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(meshProgram, "uMap"), 0);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(meshProgram, "uSurface"),
                    width, height);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uMeshScale"),
                    frame.meshScale);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uTouch"),
                    frame.mosaicEnabled ? 1f : 0f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uAlpha"),
                    frame.primaryAlpha);
            setCropUniforms(meshProgram, primaryWidth, primaryHeight);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount);
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glDisableVertexAttribArray(uv);
            GLES20.glDisableVertexAttribArray(mosaicUv);
            GLES20.glDisableVertexAttribArray(userAlpha);
        }

        private static int attribute(int program, String name, FloatBuffer values, int size) {
            int location = GLES20.glGetAttribLocation(program, name);
            values.position(0);
            GLES20.glEnableVertexAttribArray(location);
            GLES20.glVertexAttribPointer(location, size, GLES20.GL_FLOAT, false, 0, values);
            return location;
        }

        private void setCropUniforms(int program, int mapWidth, int mapHeight) {
            float[] crop = centerCropTransform(mapWidth, mapHeight, width, height);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uCropScale"),
                    crop[0], crop[1]);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uCropOffset"),
                    crop[2], crop[3]);
        }

        private static int bitmapUpload(Bitmap source) {
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            bindTexture(ids[0]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, source, 0);
            return ids[0];
        }

        private static int rawUpload(Argb8888BitmapStore.MappedImage source) {
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            bindTexture(ids[0]);
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    source.width, source.height, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, source.pixels());
            return ids[0];
        }

        private static void bindTexture(int texture) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
        }

        private void clear() {
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }

        private void postIdleHideIfUnchanged() {
            if (idleHidePosted || destroyed) return;
            idleHidePosted = true;
            final long serial = surfaceActivitySerial;
            post(new Runnable() {
                @Override public void run() {
                    if (destroyed || serial != surfaceActivitySerial) return;
                    if (getVisibility() != INVISIBLE) setVisibility(INVISIBLE);
                    Log.i(TAG, "transparent Pixelate surface retired at idle");
                }
            });
        }

        private void ready() {
            final boolean primary = primaryReady;
            final boolean secondary = secondaryReady;
            post(new Runnable() {
                @Override public void run() { listener.onResourcesReady(primary, secondary); }
            });
        }

        private void fail(final Throwable error, final String detail) {
            clear();
            GLES20.glFinish();
            post(new Runnable() {
                @Override public void run() {
                    hideSurface();
                    listener.onFailure(error, detail);
                }
            });
        }

        private void releaseGl() {
            deletePrimaryTexture();
            deleteSecondaryTexture();
            if (revealProgram != 0) GLES20.glDeleteProgram(revealProgram);
            if (meshProgram != 0) GLES20.glDeleteProgram(meshProgram);
            revealProgram = meshProgram = 0;
            primaryReady = secondaryReady = false;
        }

        private void deletePrimaryTexture() {
            if (primaryTexture != 0) {
                GLES20.glDeleteTextures(1, new int[] {primaryTexture}, 0);
                primaryTexture = 0;
            }
        }

        private void deleteSecondaryTexture() {
            if (secondaryTexture != 0) {
                GLES20.glDeleteTextures(1, new int[] {secondaryTexture}, 0);
                secondaryTexture = 0;
            }
        }

        static float[] centerCropTransform(int mapWidth, int mapHeight,
                int outputWidth, int outputHeight) {
            if (mapWidth <= 0 || mapHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) {
                return new float[] {1f, 1f, 0f, 0f};
            }
            float mapAspect = mapWidth / (float) mapHeight;
            float outputAspect = outputWidth / (float) outputHeight;
            if (mapAspect > outputAspect) {
                float scaleX = outputAspect / mapAspect;
                return new float[] {scaleX, 1f, (1f - scaleX) * .5f, 0f};
            }
            float scaleY = mapAspect / outputAspect;
            return new float[] {1f, scaleY, 0f, (1f - scaleY) * .5f};
        }

        private static int program(String vertex, String fragment) {
            int vertexShader = shader(GLES20.GL_VERTEX_SHADER, vertex);
            int fragmentShader = shader(GLES20.GL_FRAGMENT_SHADER, fragment);
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                throw new IllegalStateException("Pixelate link failed: " + log);
            }
            return program;
        }

        private static int shader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("Pixelate shader failed: " + log);
            }
            return shader;
        }

        private static FloatBuffer buffer(float[] values) {
            FloatBuffer out = ByteBuffer.allocateDirect(values.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            out.put(values);
            out.position(0);
            return out;
        }

        private static void recycle(Bitmap source) {
            if (source != null && !source.isRecycled()) source.recycle();
        }
    }
}
