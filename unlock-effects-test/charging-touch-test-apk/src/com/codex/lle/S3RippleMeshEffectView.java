package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class S3RippleMeshEffectView extends TextureView
        implements TextureView.SurfaceTextureListener, UnlockEffectRenderer, BackgroundSourceRenderer {
    private static final String TAG = "ChargingS3Ripple";

    private static final int DETAIL_SIZE = 84;
    private static final int SURFACE_SIZE = 80;
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int SHORT_SIZE_BYTES = 2;
    private static final int BACKGROUND_TEXTURE_MAX_WIDTH = 540;
    private static final int DRAW_CLIP_MIN_RADIUS_PX = 320;
    private static final float DRAW_CLIP_INITIAL_RADIUS_RATIO = 0.38f;
    private static final float DRAW_CLIP_EXPAND_RATIO = 0.035f;
    private static final float DRAW_CLIP_MAX_RADIUS_RATIO = 0.82f;
    private static final float EMPTY_THRESHOLD = 0.01f;
    private static final float HEIGHT_CLAMP = 100f;
    private static final float NORMAL_DAMPING = 0.94f;
    private static final float NORMAL_WAVE_VELOCITY = 0.5f;
    private static final float NORMAL_RELAX = 0.068f;
    private static final float MOVE_RIPPLE_DISTANCE_PX = 150f;
    private static final long UP_RIPPLE_HOLD_MS = 600L;
    private static final float MESH_SIZE_WIDTH = 50f;
    private static final float MESH_SIZE_HEIGHT = 50f;
    private static final float PORTRAIT_INTENSITY = 0.5f;
    private static final float LANDSCAPE_INTENSITY = 0.35f;
    private static final float PORTRAIT_X_RATIO = 30f;
    private static final float PORTRAIT_Y_RATIO = 46f;
    private static final float LANDSCAPE_X_RATIO = 45f;
    private static final float LANDSCAPE_Y_RATIO = 25f;
    private static final float RIPPLE_RADIUS = 3f;
    private static final int RIPPLE_BOUNDS_PAD = 5;
    private static final float REFRACTIVE_INDEX = 0.93f;
    private static final float REFLECTION_RATIO = 0.13f;
    private static final float FRESNEL_RATIO = 0.1f;
    private static final float SPECULAR_RATIO = 0.5f;
    private static final float SPECULAR_EXPONENT = 20f;
    private static final float TRANSLATE_Z_PORTRAIT = -43.05f;
    private static final float TRANSLATE_Z_LANDSCAPE = -23.8f;

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n"
                    + "attribute vec4 aPosition;\n"
                    + "attribute vec4 aHeights;\n"
                    + "varying vec2 vWaterTextureCoord;\n"
                    + "varying vec2 vBGTexture0Coord;\n"
                    + "varying vec2 vBGTexture1Coord;\n"
                    + "varying vec3 vNormal;\n"
                    + "varying vec3 vHalfVec;\n"
                    + "varying float vHeights;\n"
                    + "uniform float uMESH_SIZE_WIDTH;\n"
                    + "uniform float uMESH_SIZE_HEIGHT;\n"
                    + "uniform float uRefractiveIndex;\n"
                    + "void main() {\n"
                    + "  float maxX = uMESH_SIZE_WIDTH / 2.0;\n"
                    + "  float maxY = uMESH_SIZE_HEIGHT / 2.0;\n"
                    + "  float rimo = uRefractiveIndex - 1.0;\n"
                    + "  vec4 pos = aPosition;\n"
                    + "  float center = aHeights.x;\n"
                    + "  vec3 v = vec3(pos.x, pos.y, center * 0.25);\n"
                    + "  vec2 n = (vec2(center) - aHeights.yz) * 0.25;\n"
                    + "  float nz = sqrt(dot(n, n) + 1.0);\n"
                    + "  n = n / nz;\n"
                    + "  vec3 d = vec3(v.x, v.y, v.z + 30.0);\n"
                    + "  float len = sqrt(dot(d, d));\n"
                    + "  d = d / len;\n"
                    + "  vec3 baseD = d;\n"
                    + "  float baseR0 = (30.9 - v.z) / baseD.z;\n"
                    + "  float baseU = (baseD.x * baseR0 + v.x) / maxX * 0.25 + 0.5;\n"
                    + "  float baseV = (baseD.y * baseR0 + v.y) / maxY * -0.25 + 0.5;\n"
                    + "  float refractT = dot(d, vec3(n.x, n.y, 1.0)) * rimo;\n"
                    + "  d.x += n.x * refractT;\n"
                    + "  d.y += n.y * refractT;\n"
                    + "  float r0 = (30.9 - v.z) / d.z;\n"
                    + "  float u0 = (d.x * r0 + v.x) / maxX * 0.25 + 0.5;\n"
                    + "  float v0 = (d.y * r0 + v.y) / maxY * -0.25 + 0.5;\n"
                    + "  float uxx = n.x * 0.5 + 0.5 + pos.y / uMESH_SIZE_WIDTH * 0.25;\n"
                    + "  float vxx = n.y * 0.5 + 0.5 + pos.x / uMESH_SIZE_HEIGHT * 0.25;\n"
                    + "  vWaterTextureCoord = vec2(uxx, vxx);\n"
                    + "  vBGTexture0Coord = vec2(baseU, baseV);\n"
                    + "  vBGTexture1Coord = vec2(u0, v0);\n"
                    + "  vNormal = normalize(vec3(n.x, n.y, 0.6));\n"
                    + "  vec4 mvpPos = uMVPMatrix * pos;\n"
                    + "  vHalfVec = normalize(normalize(vec3(0.0, 0.0, 1.0) - mvpPos.xyz)\n"
                    + "      + (uMVPMatrix * vec4(5.0, -5.0, 1.0, 1.0)).xyz);\n"
                    + "  vHeights = center;\n"
                    + "  gl_Position = mvpPos;\n"
                    + "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "varying vec2 vWaterTextureCoord;\n"
                    + "varying vec2 vBGTexture0Coord;\n"
                    + "varying vec2 vBGTexture1Coord;\n"
                    + "varying vec3 vNormal;\n"
                    + "varying vec3 vHalfVec;\n"
                    + "varying float vHeights;\n"
                    + "uniform sampler2D sWaterTexture;\n"
                    + "uniform sampler2D sBGTexture;\n"
                    + "uniform vec2 uSurfaceSize;\n"
                    + "uniform float alphaRatio1;\n"
                    + "uniform float fresnelRatio;\n"
                    + "uniform float specularRatio;\n"
                    + "uniform float exponent;\n"
                    + "void main() {\n"
                    + "  vec4 waterColor = texture2D(sWaterTexture, vWaterTextureCoord);\n"
                    + "  float NdotHV = max(dot(vNormal, vHalfVec), 0.0);\n"
                    + "  float t = clamp(abs(vHeights), 0.0, 1.13);\n"
                    + "  float specular = clamp(specularRatio * pow(NdotHV, exponent), 1.0, 4.5);\n"
                    + "  float NdotL = max(dot(vNormal, vec3(5.0, -5.0, 1.0)), 0.0);\n"
                    + "  float samsungLight = alphaRatio1\n"
                    + "      + fresnelRatio * clamp((NdotL - 0.99), 0.0, 0.3);\n"
                    + "  vec3 baseColor = texture2D(sBGTexture, vBGTexture0Coord).rgb;\n"
                    + "  float baseLum = dot(baseColor, vec3(0.299, 0.587, 0.114));\n"
                    + "  vec3 baseTint = clamp(baseColor / max(baseLum, 0.18), vec3(0.72), vec3(1.35));\n"
                    + "  float tintMix = smoothstep(0.05, 0.60, baseLum) * 0.55;\n"
                    + "  vec3 screenTint = mix(vec3(0.82, 0.92, 1.0), baseTint, tintMix);\n"
                    + "  float clarity = mix(1.12, 0.96, tintMix);\n"
                    + "  vec3 delta = t * specular * waterColor.rgb * samsungLight\n"
                    + "      * screenTint * clarity;\n"
                    + "  float waterLum = max(max(delta.r, delta.g), delta.b);\n"
                    + "  float energy = t + length(vNormal.xy) * 0.18;\n"
                    + "  float outAlpha = clamp(waterLum * 0.78 + t * 0.018, 0.0, 0.34);\n"
                    + "  outAlpha *= smoothstep(0.010, 0.070, energy);\n"
                    + "  if (outAlpha <= 0.003) discard;\n"
                    + "  vec3 src = clamp(delta / max(outAlpha, 0.02), 0.0, 1.0);\n"
                    + "  gl_FragColor = vec4(src * outAlpha, outAlpha);\n"
                    + "}\n";

    private final Object lock = new Object();
    private final Bitmap reflectionMap;
    private final SoundPool soundPool;
    private final int downSound;
    private final int upSound;
    private final float[] heightMap = new float[DETAIL_SIZE * DETAIL_SIZE];
    private final float[] velocity = new float[DETAIL_SIZE * DETAIL_SIZE];
    private final float[] positions = new float[SURFACE_SIZE * SURFACE_SIZE * 3];
    private final float[] heightAttribs = new float[SURFACE_SIZE * SURFACE_SIZE * 4];
    private final short[] indices = new short[(SURFACE_SIZE - 1) * (SURFACE_SIZE - 1) * 6];
    private final float[] mvp = new float[16];
    private final Runnable affordanceRunnable = new Runnable() {
        @Override
        public void run() {
            triggerAffordanceRipple();
        }
    };

    private Bitmap pendingBackgroundBitmap;
    private GLThread glThread;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean animating;
    private boolean dirtyFrame;
    private boolean externalBackground;
    private boolean backgroundReadyForGl;
    private String backgroundSource = "none";
    private volatile boolean glReady;
    private volatile long glReadyAt;
    private float lastX;
    private float lastY;
    private float pendingAffordanceX;
    private float pendingAffordanceY;
    private float rippleDistance;
    private long pressStartedAt;
    private int emptyFrames;
    private boolean drawClipActive;
    private int drawClipLeft;
    private int drawClipTop;
    private int drawClipRight;
    private int drawClipBottom;

    public S3RippleMeshEffectView(Context context) {
        super(context);
        long startedAt = SystemClock.uptimeMillis();
        setOpaque(false);
        setSurfaceTextureListener(this);
        reflectionMap = decode(R.drawable.s3_reflectionmap);
        buildMesh();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        downSound = soundPool.load(context, R.raw.s3_ripple_down, 1);
        upSound = soundPool.load(context, R.raw.s3_ripple_up, 1);
        Log.i(TAG, "S3 ripple mesh renderer loaded elapsedMs="
                + (SystemClock.uptimeMillis() - startedAt));
    }

    @Override
    public View asView() {
        return this;
    }

    public boolean isGlReadyForFrame() {
        return glReady;
    }

    @Override
    public String effectName() {
        return "S3 ripple mesh WIP neutral-sharp";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        removeCallbacks(affordanceRunnable);
        synchronized (lock) {
            gestureActive = true;
            animating = true;
            dirtyFrame = true;
            emptyFrames = 0;
            lastX = screenX;
            lastY = screenY;
            rippleDistance = 0f;
            pressStartedAt = SystemClock.uptimeMillis();
            addRippleLocked(screenX, screenY, intensityForOrientation() * 4f);
            lock.notifyAll();
        }
        play(downSound);
        Log.i(TAG, "s3 mesh begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY)
                + " bg=" + backgroundSource
                + " glReady=" + glReady
                + " sinceGlReadyMs=" + (glReadyAt <= 0L
                ? -1L
                : SystemClock.uptimeMillis() - glReadyAt));
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        synchronized (lock) {
            if (!gestureActive) {
                // Defer the recursive call outside the lock.
            } else {
                float dx = screenX - lastX;
                float dy = screenY - lastY;
                rippleDistance += (float) Math.hypot(dx, dy);
                if (rippleDistance > MOVE_RIPPLE_DISTANCE_PX) {
                    addRippleLocked(screenX, screenY, intensityForOrientation() * 3f);
                    rippleDistance = 0f;
                    play(upSound);
                }
                lastX = screenX;
                lastY = screenY;
                animating = true;
                dirtyFrame = true;
                lock.notifyAll();
                return;
            }
        }
        beginGesture(screenX, screenY);
    }

    @Override
    public void finishGesture(boolean completed) {
        if (destroyed) {
            return;
        }
        long heldMs;
        boolean playLongReleaseSound = false;
        synchronized (lock) {
            if (!gestureActive) {
                return;
            }
            gestureActive = false;
            heldMs = SystemClock.uptimeMillis() - pressStartedAt;
            if (heldMs > UP_RIPPLE_HOLD_MS) {
                addRippleLocked(lastX, lastY, intensityForOrientation() * 4f);
                playLongReleaseSound = true;
            }
            animating = true;
            dirtyFrame = true;
            lock.notifyAll();
        }
        if (playLongReleaseSound) {
            play(downSound);
        }
        Log.i(TAG, "s3 mesh finish completed=" + completed
                + " heldMs=" + heldMs);
    }

    @Override
    public void cancelGesture() {
        synchronized (lock) {
            gestureActive = false;
            animating = true;
            dirtyFrame = true;
            lock.notifyAll();
        }
    }

    @Override
    public void resetEffect() {
        removeCallbacks(affordanceRunnable);
        synchronized (lock) {
            gestureActive = false;
            animating = false;
            dirtyFrame = true;
            for (int i = 0; i < heightMap.length; i++) {
                heightMap[i] = 0f;
                velocity[i] = 0f;
            }
            clearDrawClipLocked();
            lock.notifyAll();
        }
    }

    @Override
    public void warmUp() {
        synchronized (lock) {
            dirtyFrame = true;
            lock.notifyAll();
        }
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (destroyed) {
            return;
        }
        Rect rect = safeRect(screenRect);
        pendingAffordanceX = rect.exactCenterX();
        pendingAffordanceY = rect.exactCenterY();
        removeCallbacks(affordanceRunnable);
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
        Log.i(TAG, "s3 mesh affordance queued delayMs=" + startDelayMs
                + " center=" + Math.round(pendingAffordanceX)
                + "," + Math.round(pendingAffordanceY));
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        synchronized (lock) {
            return externalBackground && backgroundReadyForGl;
        }
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        int renderWidth = getRenderWidth();
        int renderHeight = getRenderHeight();
        Bitmap cropped = createBackgroundTextureSourceBitmap(source, renderWidth, renderHeight);
        BackgroundTexture texture = createRippleBackgroundTexture(cropped);
        Bitmap next = texture.bitmap;
        next.prepareToDraw();
        if (cropped != next) {
            recycle(cropped);
        }
        synchronized (lock) {
            recycle(pendingBackgroundBitmap);
            pendingBackgroundBitmap = next;
            externalBackground = true;
            backgroundReadyForGl = false;
            backgroundSource = sourceName == null ? "external" : sourceName;
            dirtyFrame = true;
            lock.notifyAll();
        }
        Log.i(TAG, "s3 mesh background queued source=" + backgroundSource
                + " sourceSize=" + source.getWidth() + "x" + source.getHeight()
                + " renderSize=" + renderWidth + "x" + renderHeight
                + " textureSize=" + next.getWidth() + "x" + next.getHeight()
                + " mode=" + texture.mode);
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        synchronized (lock) {
            recycle(pendingBackgroundBitmap);
            pendingBackgroundBitmap = null;
            externalBackground = false;
            backgroundReadyForGl = false;
            dirtyFrame = true;
            lock.notifyAll();
        }
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        GLThread thread = glThread;
        if (thread != null) {
            thread.requestStop();
            glThread = null;
        }
        soundPool.release();
        clearBackgroundSourceBitmap();
        recycle(reflectionMap);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "s3 surface available size=" + width + "x" + height);
        GLThread thread = new GLThread(surface, width, height);
        glThread = thread;
        thread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        GLThread thread = glThread;
        if (thread != null) {
            thread.resize(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        GLThread thread = glThread;
        if (thread != null) {
            thread.requestStop();
            glThread = null;
        }
        glReady = false;
        glReadyAt = 0L;
        Log.i(TAG, "s3 surface destroyed");
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void triggerAffordanceRipple() {
        if (destroyed || gestureActive) {
            return;
        }
        synchronized (lock) {
            float x = pendingAffordanceX > 0f
                    ? pendingAffordanceX
                    : getRenderWidth() * 0.5f;
            float y = pendingAffordanceY > 0f
                    ? pendingAffordanceY
                    : getRenderHeight() * 0.5f;
            addRippleLocked(x, y, intensityForOrientation() * 4f);
            animating = true;
            dirtyFrame = true;
            emptyFrames = 0;
            lock.notifyAll();
        }
        Log.i(TAG, "s3 mesh affordance fired center="
                + Math.round(pendingAffordanceX)
                + "," + Math.round(pendingAffordanceY));
    }

    private Rect safeRect(Rect rect) {
        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            return rect;
        }
        return new Rect(0, 0, getRenderWidth(), getRenderHeight());
    }

    private boolean stepRippleLocked() {
        boolean isEmpty = true;
        for (int x = 1; x < DETAIL_SIZE - 1; x++) {
            for (int y = 1; y < DETAIL_SIZE - 1; y++) {
                int i = y * DETAIL_SIZE + x;
                float lap = heightMap[i - DETAIL_SIZE]
                        + heightMap[i - 1]
                        + heightMap[i + 1]
                        + heightMap[i + DETAIL_SIZE]
                        - heightMap[i] * 4f;
                velocity[i] = (velocity[i] + lap * NORMAL_WAVE_VELOCITY)
                        * NORMAL_DAMPING;
                if (Math.abs(velocity[i]) > EMPTY_THRESHOLD) {
                    isEmpty = false;
                }
            }
        }
        for (int x = 1; x < DETAIL_SIZE - 1; x++) {
            for (int y = 1; y < DETAIL_SIZE - 1; y++) {
                int i = y * DETAIL_SIZE + x;
                heightMap[i] = clamp(heightMap[i] + velocity[i],
                        -HEIGHT_CLAMP,
                        HEIGHT_CLAMP);
            }
        }
        for (int x = 1; x < DETAIL_SIZE - 1; x++) {
            for (int y = 1; y < DETAIL_SIZE - 1; y++) {
                int i = y * DETAIL_SIZE + x;
                float lap = heightMap[i - DETAIL_SIZE]
                        + heightMap[i - 1]
                        + heightMap[i + 1]
                        + heightMap[i + DETAIL_SIZE]
                        - heightMap[i] * 4f;
                heightMap[i] = clamp(heightMap[i] + lap * NORMAL_RELAX,
                        -HEIGHT_CLAMP,
                        HEIGHT_CLAMP);
            }
        }
        return isEmpty;
    }

    private void buildHeightAttribsLocked() {
        int out = 0;
        for (int j = 0; j < SURFACE_SIZE; j++) {
            for (int i = 0; i < SURFACE_SIZE; i++) {
                int center = (j + 2) * DETAIL_SIZE + (i + 2);
                int neighborA = (j + 2) * DETAIL_SIZE + (i + 1);
                int neighborB = (j + 1) * DETAIL_SIZE + (i + 2);
                heightAttribs[out++] = heightMap[center];
                heightAttribs[out++] = heightMap[neighborA];
                heightAttribs[out++] = heightMap[neighborB];
                heightAttribs[out++] = 0f;
            }
        }
    }

    private void addRippleLocked(float screenX, float screenY, float intensity) {
        includeDrawClipLocked(screenX, screenY);
        float[] point = projectedRipplePoint(screenX, screenY);
        float cx = point[0];
        float cy = point[1];
        int x0 = cx <= 5f ? 2 : (int) Math.floor(cx - RIPPLE_RADIUS);
        int y0 = cy <= 5f ? 2 : (int) Math.floor(cy - RIPPLE_RADIUS);
        int x1 = cx >= DETAIL_SIZE - 5f
                ? DETAIL_SIZE - 1
                : (int) Math.floor(cx + RIPPLE_RADIUS + 1f);
        int y1 = cy >= DETAIL_SIZE - 5f
                ? DETAIL_SIZE - 1
                : (int) Math.floor(cy + RIPPLE_RADIUS + 1f);
        x0 = clamp(x0, 1, DETAIL_SIZE - 2);
        y0 = clamp(y0, 1, DETAIL_SIZE - 2);
        x1 = clamp(x1, x0 + 1, DETAIL_SIZE - 1);
        y1 = clamp(y1, y0 + 1, DETAIL_SIZE - 1);
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                float dist = (float) Math.hypot(cx - x, cy - y);
                float add = RIPPLE_RADIUS - dist;
                if (add > 0f) {
                    velocity[y * DETAIL_SIZE + x] += add * intensity;
                }
            }
        }
    }

    private void includeDrawClipLocked(float screenX, float screenY) {
        int width = getRenderWidth();
        int height = getRenderHeight();
        int minSide = Math.max(1, Math.min(width, height));
        int radius = Math.max(DRAW_CLIP_MIN_RADIUS_PX,
                Math.round(minSide * DRAW_CLIP_INITIAL_RADIUS_RATIO));
        int left = clamp(Math.round(screenX) - radius, 0, width);
        int top = clamp(Math.round(screenY) - radius, 0, height);
        int right = clamp(Math.round(screenX) + radius, 0, width);
        int bottom = clamp(Math.round(screenY) + radius, 0, height);
        if (!drawClipActive) {
            drawClipLeft = left;
            drawClipTop = top;
            drawClipRight = right;
            drawClipBottom = bottom;
            drawClipActive = true;
            return;
        }
        drawClipLeft = Math.min(drawClipLeft, left);
        drawClipTop = Math.min(drawClipTop, top);
        drawClipRight = Math.max(drawClipRight, right);
        drawClipBottom = Math.max(drawClipBottom, bottom);
    }

    private void expandDrawClipLocked() {
        if (!drawClipActive) {
            return;
        }
        int width = getRenderWidth();
        int height = getRenderHeight();
        int minSide = Math.max(1, Math.min(width, height));
        int maxDiameter = Math.max(DRAW_CLIP_MIN_RADIUS_PX * 2,
                Math.round(minSide * DRAW_CLIP_MAX_RADIUS_RATIO) * 2);
        int expand = Math.max(8, Math.round(minSide * DRAW_CLIP_EXPAND_RATIO));
        int currentWidth = drawClipRight - drawClipLeft;
        int currentHeight = drawClipBottom - drawClipTop;
        int expandX = currentWidth >= maxDiameter
                ? 0
                : Math.min(expand, Math.max(0, (maxDiameter - currentWidth + 1) / 2));
        int expandY = currentHeight >= maxDiameter
                ? 0
                : Math.min(expand, Math.max(0, (maxDiameter - currentHeight + 1) / 2));
        drawClipLeft = clamp(drawClipLeft - expandX, 0, width);
        drawClipTop = clamp(drawClipTop - expandY, 0, height);
        drawClipRight = clamp(drawClipRight + expandX, 0, width);
        drawClipBottom = clamp(drawClipBottom + expandY, 0, height);
    }

    private void clearDrawClipLocked() {
        drawClipActive = false;
        drawClipLeft = 0;
        drawClipTop = 0;
        drawClipRight = 0;
        drawClipBottom = 0;
    }

    private float[] projectedRipplePoint(float screenX, float screenY) {
        float width = Math.max(1f, getRenderWidth());
        float height = Math.max(1f, getRenderHeight());
        boolean portrait = height >= width;
        float translateZ = portrait ? TRANSLATE_Z_PORTRAIT : TRANSLATE_Z_LANDSCAPE;
        float distance = 1f - translateZ;
        float halfHeight = (float) Math.tan(Math.toRadians(45f * 0.5f)) * distance;
        float halfWidth = halfHeight * width / height;
        float ndcX = screenX / width * 2f - 1f;
        float ndcY = 1f - screenY / height * 2f;
        float worldX = ndcX * halfWidth;
        float worldY = ndcY * halfHeight;
        float surfaceX = (0.5f - worldY / MESH_SIZE_HEIGHT) * (SURFACE_SIZE - 1f);
        float surfaceY = (worldX / MESH_SIZE_WIDTH + 0.5f) * (SURFACE_SIZE - 1f);
        return new float[] {
                clamp(surfaceX + 2f, 1f, DETAIL_SIZE - 2f),
                clamp(surfaceY + 2f, 1f, DETAIL_SIZE - 2f)
        };
    }

    private void buildMesh() {
        float unitX = MESH_SIZE_WIDTH / (SURFACE_SIZE - 1f);
        float unitY = MESH_SIZE_HEIGHT / (SURFACE_SIZE - 1f);
        int vertex = 0;
        for (int row = 0; row < SURFACE_SIZE; row++) {
            for (int col = 0; col < SURFACE_SIZE; col++) {
                positions[vertex++] = row * unitX - MESH_SIZE_WIDTH * 0.5f;
                positions[vertex++] = -(col * unitY - MESH_SIZE_HEIGHT * 0.5f);
                positions[vertex++] = 0f;
            }
        }
        int p = 0;
        for (int y = 0; y < SURFACE_SIZE - 1; y++) {
            for (int x = 0; x < SURFACE_SIZE - 1; x++) {
                short a = (short) (y * SURFACE_SIZE + x);
                short b = (short) (a + 1);
                short c = (short) (a + SURFACE_SIZE);
                short d = (short) (c + 1);
                indices[p++] = a;
                indices[p++] = c;
                indices[p++] = b;
                indices[p++] = b;
                indices[p++] = c;
                indices[p++] = d;
            }
        }
    }

    private void updateMvp(int width, int height, float[] view, float[] projection,
            float[] model, float[] vp) {
        boolean portrait = height >= width;
        Matrix.setLookAtM(view, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f);
        Matrix.perspectiveM(projection, 0, 45f, width / (float) Math.max(1, height),
                0.1f, 500f);
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0f, 0f,
                portrait ? TRANSLATE_Z_PORTRAIT : TRANSLATE_Z_LANDSCAPE);
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0);
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
    }

    private Bitmap decode(int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId, options);
        if (bitmap != null) {
            bitmap.prepareToDraw();
        }
        return bitmap;
    }

    private Bitmap createBackgroundTextureSourceBitmap(Bitmap source, int renderWidth,
            int renderHeight) {
        int textureWidth = Math.max(1, renderWidth);
        int textureHeight = Math.max(1, renderHeight);
        if (textureWidth > BACKGROUND_TEXTURE_MAX_WIDTH) {
            textureWidth = BACKGROUND_TEXTURE_MAX_WIDTH;
            textureHeight = Math.max(1,
                    Math.round(textureWidth * renderHeight / (float) Math.max(1, renderWidth)));
        }
        return createTopStartCropBitmap(source, textureWidth, textureHeight);
    }

    private Bitmap createTopStartCropBitmap(Bitmap source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(out);
        android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
                        | android.graphics.Paint.FILTER_BITMAP_FLAG
                        | android.graphics.Paint.DITHER_FLAG);
        float srcRatio = source.getWidth() / (float) source.getHeight();
        float dstRatio = width / (float) height;
        Rect src;
        if (srcRatio > dstRatio) {
            int srcWidth = Math.max(1, Math.round(source.getHeight() * dstRatio));
            src = new Rect(0, 0, Math.min(source.getWidth(), srcWidth),
                    source.getHeight());
        } else {
            int srcHeight = Math.max(1, Math.round(source.getWidth() / dstRatio));
            src = new Rect(0, 0, source.getWidth(),
                    Math.min(source.getHeight(), srcHeight));
        }
        canvas.drawBitmap(source, src, new Rect(0, 0, width, height), paint);
        return out;
    }

    private BackgroundTexture createRippleBackgroundTexture(Bitmap source) {
        BackgroundStats stats = sampleBackgroundStats(source);
        Bitmap map = source.copy(Bitmap.Config.ARGB_8888, false);
        map.prepareToDraw();
        return new BackgroundTexture(map, "screen_map sourceMean="
                + Math.round(stats.meanLuma)
                + " brightPct=" + Math.round(stats.brightFraction * 100f));
    }

    private Bitmap createBlurredScreenMapBitmap(Bitmap source) {
        int scratchWidth = Math.min(256, Math.max(96, source.getWidth() / 4));
        int scratchHeight = Math.max(96,
                Math.round(scratchWidth * source.getHeight()
                        / (float) Math.max(1, source.getWidth())));
        Bitmap scratch = Bitmap.createBitmap(scratchWidth, scratchHeight,
                Bitmap.Config.ARGB_8888);
        android.graphics.Canvas scratchCanvas = new android.graphics.Canvas(scratch);
        android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.FILTER_BITMAP_FLAG
                        | android.graphics.Paint.DITHER_FLAG);
        scratchCanvas.drawBitmap(source, null,
                new Rect(0, 0, scratchWidth, scratchHeight), paint);
        boxBlurInPlace(scratch, 4, 5);
        Bitmap map = Bitmap.createBitmap(source.getWidth(), source.getHeight(),
                Bitmap.Config.ARGB_8888);
        android.graphics.Canvas mapCanvas = new android.graphics.Canvas(map);
        mapCanvas.drawBitmap(scratch, null,
                new Rect(0, 0, map.getWidth(), map.getHeight()), paint);
        scratch.recycle();
        map.prepareToDraw();
        return map;
    }

    private Bitmap createFallbackTintMapBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(210, 222, 235));
        bitmap.prepareToDraw();
        return bitmap;
    }

    private Bitmap createWhiteMapBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        bitmap.prepareToDraw();
        return bitmap;
    }

    private void boxBlurInPlace(Bitmap bitmap, int passes, int radius) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 1 || height <= 1 || radius <= 0 || passes <= 0) {
            return;
        }
        int[] src = new int[width * height];
        int[] tmp = new int[src.length];
        bitmap.getPixels(src, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < passes; pass++) {
            blurHorizontal(src, tmp, width, height, radius);
            blurVertical(tmp, src, width, height, radius);
        }
        bitmap.setPixels(src, 0, width, 0, 0, width, height);
    }

    private void blurHorizontal(int[] src, int[] dst, int width, int height, int radius) {
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int count = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    int xx = clamp(x + dx, 0, width - 1);
                    int color = src[row + xx];
                    red += Color.red(color);
                    green += Color.green(color);
                    blue += Color.blue(color);
                    count++;
                }
                dst[row + x] = Color.rgb(red / count, green / count, blue / count);
            }
        }
    }

    private void blurVertical(int[] src, int[] dst, int width, int height, int radius) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int count = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int yy = clamp(y + dy, 0, height - 1);
                    int color = src[yy * width + x];
                    red += Color.red(color);
                    green += Color.green(color);
                    blue += Color.blue(color);
                    count++;
                }
                dst[y * width + x] = Color.rgb(red / count, green / count, blue / count);
            }
        }
    }

    private BackgroundStats sampleBackgroundStats(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int step = Math.max(1, Math.min(width, height) / 96);
        long totalLuma = 0L;
        long darkRed = 0L;
        long darkGreen = 0L;
        long darkBlue = 0L;
        int darkSamples = 0;
        int brightSamples = 0;
        int samples = 0;
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int color = bitmap.getPixel(x, y);
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                int luma = (red * 299 + green * 587 + blue * 114) / 1000;
                totalLuma += luma;
                samples++;
                if (luma > 28) {
                    brightSamples++;
                } else {
                    darkSamples++;
                    darkRed += red;
                    darkGreen += green;
                    darkBlue += blue;
                }
            }
        }
        if (samples == 0) {
            return new BackgroundStats(0f, 0f, 0, 0, 0);
        }
        int red = darkSamples == 0 ? 0 : Math.round(darkRed / (float) darkSamples);
        int green = darkSamples == 0 ? 0 : Math.round(darkGreen / (float) darkSamples);
        int blue = darkSamples == 0 ? 0 : Math.round(darkBlue / (float) darkSamples);
        return new BackgroundStats(totalLuma / (float) samples,
                brightSamples / (float) samples,
                clamp(red, 0, 255),
                clamp(green, 0, 255),
                clamp(blue, 0, 255));
    }

    private int getRenderWidth() {
        int width = getWidth();
        if (width > 0) {
            return width;
        }
        return Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        int height = getHeight();
        if (height > 0) {
            return height;
        }
        return Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    private float intensityForOrientation() {
        return getRenderHeight() >= getRenderWidth()
                ? PORTRAIT_INTENSITY
                : LANDSCAPE_INTENSITY;
    }

    private void play(int soundId) {
        if (!destroyed && soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private static final class BackgroundTexture {
        final Bitmap bitmap;
        final String mode;

        BackgroundTexture(Bitmap bitmap, String mode) {
            this.bitmap = bitmap;
            this.mode = mode;
        }
    }

    private static final class BackgroundStats {
        final float meanLuma;
        final float brightFraction;
        final int darkRed;
        final int darkGreen;
        final int darkBlue;

        BackgroundStats(float meanLuma, float brightFraction,
                int darkRed, int darkGreen, int darkBlue) {
            this.meanLuma = meanLuma;
            this.brightFraction = brightFraction;
            this.darkRed = darkRed;
            this.darkGreen = darkGreen;
            this.darkBlue = darkBlue;
        }
    }

    private final class GLThread extends Thread {
        private final SurfaceTexture surfaceTexture;
        private final FloatBuffer positionBuffer = newFloatBuffer(positions.length);
        private final FloatBuffer heightsBuffer = newFloatBuffer(heightAttribs.length);
        private final ShortBuffer indexBuffer = newShortBuffer(indices.length);
        private final float[] viewMatrix = new float[16];
        private final float[] projectionMatrix = new float[16];
        private final float[] modelMatrix = new float[16];
        private final float[] viewProjectionMatrix = new float[16];
        private volatile boolean shouldStop;
        private int surfaceWidth;
        private int surfaceHeight;
        private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        private EGLContext context = EGL14.EGL_NO_CONTEXT;
        private EGLSurface surface = EGL14.EGL_NO_SURFACE;
        private int program;
        private int positionHandle;
        private int heightsHandle;
        private int mvpHandle;
        private int meshWidthHandle;
        private int meshHeightHandle;
        private int refractiveHandle;
        private int waterSamplerHandle;
        private int bgSamplerHandle;
        private int surfaceSizeHandle;
        private int alphaRatioHandle;
        private int fresnelHandle;
        private int specularHandle;
        private int exponentHandle;
        private int waterTexture;
        private int bgTexture;
        private boolean firstDrawLogged;

        GLThread(SurfaceTexture surfaceTexture, int width, int height) {
            super("S3RippleGL");
            this.surfaceTexture = surfaceTexture;
            this.surfaceWidth = Math.max(1, width);
            this.surfaceHeight = Math.max(1, height);
            positionBuffer.put(positions).position(0);
            indexBuffer.put(indices).position(0);
        }

        @Override
        public void run() {
            try {
                initGl();
                loop();
            } finally {
                releaseGl();
            }
        }

        void resize(int width, int height) {
            synchronized (lock) {
                surfaceWidth = Math.max(1, width);
                surfaceHeight = Math.max(1, height);
                dirtyFrame = true;
                lock.notifyAll();
            }
        }

        void requestStop() {
            shouldStop = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        private void loop() {
            while (!shouldStop) {
                Bitmap uploadBitmap = null;
                boolean shouldDraw;
                boolean clipActive;
                int clipLeft;
                int clipTop;
                int clipRight;
                int clipBottom;
                synchronized (lock) {
                    if (!dirtyFrame && !animating && !shouldStop) {
                        try {
                            lock.wait(250L);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    if (shouldStop) {
                        break;
                    }
                    if (pendingBackgroundBitmap != null) {
                        uploadBitmap = pendingBackgroundBitmap;
                        pendingBackgroundBitmap = null;
                    }
                    shouldDraw = dirtyFrame || animating || uploadBitmap != null;
                    dirtyFrame = false;
                    if (animating) {
                        boolean empty = stepRippleLocked();
                        buildHeightAttribsLocked();
                        expandDrawClipLocked();
                        emptyFrames = empty ? emptyFrames + 1 : 0;
                        animating = gestureActive || emptyFrames < 8;
                        if (!animating) {
                            clearDrawClipLocked();
                        }
                    } else {
                        buildHeightAttribsLocked();
                        clearDrawClipLocked();
                    }
                    clipActive = drawClipActive && animating;
                    clipLeft = drawClipLeft;
                    clipTop = drawClipTop;
                    clipRight = drawClipRight;
                    clipBottom = drawClipBottom;
                }
                if (uploadBitmap != null) {
                    uploadBackground(uploadBitmap);
                    recycle(uploadBitmap);
                    synchronized (lock) {
                        backgroundReadyForGl = true;
                    }
                }
                if (shouldDraw) {
                    drawFrame(clipActive, clipLeft, clipTop, clipRight, clipBottom);
                }
                if (animating) {
                    SystemClock.sleep(16L);
                }
            }
        }

        private void initGl() {
            long startedAt = SystemClock.uptimeMillis();
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            EGL14.eglInitialize(display, version, 0, version, 1);
            int[] configAttribs = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 16,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1,
                    numConfigs, 0);
            int[] contextAttribs = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    contextAttribs, 0);
            int[] surfaceAttribs = {EGL14.EGL_NONE};
            surface = EGL14.eglCreateWindowSurface(display, configs[0], surfaceTexture,
                    surfaceAttribs, 0);
            EGL14.eglMakeCurrent(display, surface, surface, context);
            program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            heightsHandle = GLES20.glGetAttribLocation(program, "aHeights");
            mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            meshWidthHandle = GLES20.glGetUniformLocation(program, "uMESH_SIZE_WIDTH");
            meshHeightHandle = GLES20.glGetUniformLocation(program, "uMESH_SIZE_HEIGHT");
            refractiveHandle = GLES20.glGetUniformLocation(program, "uRefractiveIndex");
            waterSamplerHandle = GLES20.glGetUniformLocation(program, "sWaterTexture");
            bgSamplerHandle = GLES20.glGetUniformLocation(program, "sBGTexture");
            surfaceSizeHandle = GLES20.glGetUniformLocation(program, "uSurfaceSize");
            alphaRatioHandle = GLES20.glGetUniformLocation(program, "alphaRatio1");
            fresnelHandle = GLES20.glGetUniformLocation(program, "fresnelRatio");
            specularHandle = GLES20.glGetUniformLocation(program, "specularRatio");
            exponentHandle = GLES20.glGetUniformLocation(program, "exponent");
            waterTexture = createTexture(reflectionMap);
            Bitmap fallbackTintMap = createFallbackTintMapBitmap();
            bgTexture = createTexture(fallbackTintMap);
            fallbackTintMap.recycle();
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glDisable(GLES20.GL_BLEND);
            glReadyAt = SystemClock.uptimeMillis();
            glReady = true;
            Log.i(TAG, "s3 gl ready surface=" + surfaceWidth + "x" + surfaceHeight
                    + " elapsedMs=" + (glReadyAt - startedAt));
        }

        private void drawFrame(boolean clipActive, int clipLeft, int clipTop,
                int clipRight, int clipBottom) {
            long startedAt = firstDrawLogged ? 0L : SystemClock.uptimeMillis();
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            boolean scissorEnabled = false;
            if (clipActive) {
                int left = clamp(clipLeft, 0, surfaceWidth);
                int top = clamp(clipTop, 0, surfaceHeight);
                int right = clamp(clipRight, left, surfaceWidth);
                int bottom = clamp(clipBottom, top, surfaceHeight);
                int width = right - left;
                int height = bottom - top;
                if (width > 0 && height > 0) {
                    GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
                    GLES20.glScissor(left, surfaceHeight - bottom, width, height);
                    scissorEnabled = true;
                }
            }
            synchronized (lock) {
                heightsBuffer.clear();
                heightsBuffer.put(heightAttribs).position(0);
            }
            updateMvp(surfaceWidth, surfaceHeight, viewMatrix, projectionMatrix,
                    modelMatrix, viewProjectionMatrix);
            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
            GLES20.glUniform1f(meshWidthHandle, MESH_SIZE_WIDTH);
            GLES20.glUniform1f(meshHeightHandle, MESH_SIZE_HEIGHT);
            GLES20.glUniform1f(refractiveHandle, REFRACTIVE_INDEX);
            GLES20.glUniform2f(surfaceSizeHandle, surfaceWidth, surfaceHeight);
            GLES20.glUniform1f(alphaRatioHandle, REFLECTION_RATIO);
            GLES20.glUniform1f(fresnelHandle, FRESNEL_RATIO);
            GLES20.glUniform1f(specularHandle, SPECULAR_RATIO);
            GLES20.glUniform1f(exponentHandle, SPECULAR_EXPONENT);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, waterTexture);
            GLES20.glUniform1i(waterSamplerHandle, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTexture);
            GLES20.glUniform1i(bgSamplerHandle, 1);
            GLES20.glEnableVertexAttribArray(positionHandle);
            positionBuffer.position(0);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT,
                    false, 3 * FLOAT_SIZE_BYTES, positionBuffer);
            GLES20.glEnableVertexAttribArray(heightsHandle);
            heightsBuffer.position(0);
            GLES20.glVertexAttribPointer(heightsHandle, 4, GLES20.GL_FLOAT,
                    false, 4 * FLOAT_SIZE_BYTES, heightsBuffer);
            indexBuffer.position(0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length,
                    GLES20.GL_UNSIGNED_SHORT, indexBuffer);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(heightsHandle);
            if (scissorEnabled) {
                GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            }
            EGL14.eglSwapBuffers(display, surface);
            if (!firstDrawLogged) {
                firstDrawLogged = true;
                Log.i(TAG, "s3 first frame drawn elapsedMs="
                        + (SystemClock.uptimeMillis() - startedAt));
            }
        }

        private void uploadBackground(Bitmap bitmap) {
            long startedAt = SystemClock.uptimeMillis();
            EGL14.eglMakeCurrent(display, surface, surface, context);
            if (bgTexture != 0) {
                int[] old = {bgTexture};
                GLES20.glDeleteTextures(1, old, 0);
            }
            bgTexture = createTexture(bitmap);
            Log.i(TAG, "s3 background uploaded size=" + bitmap.getWidth()
                    + "x" + bitmap.getHeight()
                    + " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt));
        }

        private int createSolidTexture(int color) {
            Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(color);
            int texture = createTexture(bitmap);
            bitmap.recycle();
            return texture;
        }

        private int createTexture(Bitmap bitmap) {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            int texture = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            return texture;
        }

        private int buildProgram(String vertex, String fragment) {
            int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertex);
            int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment);
            int nextProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(nextProgram, vertexShader);
            GLES20.glAttachShader(nextProgram, fragmentShader);
            GLES20.glLinkProgram(nextProgram);
            int[] status = new int[1];
            GLES20.glGetProgramiv(nextProgram, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(nextProgram);
                GLES20.glDeleteProgram(nextProgram);
                throw new IllegalStateException("S3 shader link failed: " + log);
            }
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return nextProgram;
        }

        private int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("S3 shader compile failed: " + log);
            }
            return shader;
        }

        private void releaseGl() {
            EGL14.eglMakeCurrent(display, surface, surface, context);
            if (program != 0) {
                GLES20.glDeleteProgram(program);
            }
            int[] textures = {waterTexture, bgTexture};
            GLES20.glDeleteTextures(2, textures, 0);
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, surface);
            }
            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context);
            }
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglTerminate(display);
            }
            glReady = false;
            glReadyAt = 0L;
        }

        private FloatBuffer newFloatBuffer(int floats) {
            return ByteBuffer.allocateDirect(floats * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
        }

        private ShortBuffer newShortBuffer(int shorts) {
            return ByteBuffer.allocateDirect(shorts * SHORT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer();
        }
    }
}
