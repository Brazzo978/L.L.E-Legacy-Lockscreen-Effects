package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

public class S3RippleEffectView extends View
        implements UnlockEffectRenderer, BackgroundSourceRenderer {
    private static final String TAG = "ChargingS3Ripple";

    private static final int DETAIL_SIZE = 104;
    private static final float EMPTY_THRESHOLD = 0.012f;
    private static final float DENSITY_EMPTY_THRESHOLD = 0.02f;
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
    private static final float DENSITY_DAMPING = 0.955f;
    private static final float DENSITY_DIFFUSE = 0.026f;
    private static final float DENSITY_IMPULSE_SCALE = 1.45f;
    private static final float ALPHA_SCALE_HEIGHT = 0.032f;
    private static final float ALPHA_SCALE_GRADIENT = 0.17f;
    private static final float ALPHA_SCALE_DENSITY = 0.055f;
    private static final float ALPHA_MAX = 0.58f;
    private static final float REFRACTIVE_INDEX = 0.93f;
    private static final float REFLECTION_RATIO = 0.13f;
    private static final float FRESNEL_RATIO = 0.1f;
    private static final float SPECULAR_RATIO = 0.5f;
    private static final float SPECULAR_EXPONENT = 20f;
    private static final float NORMAL_Z = 0.6f;
    private static final float REFRACTION_PIXEL_SCALE = 22f;
    private static final float LIGHT_X = 5f;
    private static final float LIGHT_Y = -5f;
    private static final float LIGHT_Z = 1f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG
            | Paint.DITHER_FLAG);
    private final Rect dst = new Rect();
    private final Bitmap reflectionMap;
    private final Bitmap fallbackWallpaper;
    private final SoundPool soundPool;
    private final int downSound;
    private final int upSound;
    private final Runnable affordanceRunnable = new Runnable() {
        @Override
        public void run() {
            triggerAffordanceRipple();
        }
    };

    private Bitmap backgroundBitmap;
    private Bitmap rippleBitmap;
    private int[] pixels;
    private float[] heightMap;
    private float[] renderHeight;
    private float[] velocity;
    private float[] nextHeight;
    private float[] nextVelocity;
    private float[] densityMap;
    private float[] nextDensity;
    private int detailWidth;
    private int detailHeight;
    private boolean destroyed;
    private boolean gestureActive;
    private boolean animating;
    private boolean externalBackground;
    private String backgroundSource = "fallback";
    private float lastX;
    private float lastY;
    private float rippleDistance;
    private long pressStartedAt;
    private int emptyFrames;

    public S3RippleEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        reflectionMap = decode(R.drawable.s3_reflectionmap);
        fallbackWallpaper = decode(R.drawable.s3_keyguard_default_wallpaper);
        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        downSound = soundPool.load(context, R.raw.s3_ripple_down, 1);
        upSound = soundPool.load(context, R.raw.s3_ripple_up, 1);
        Log.i(TAG, "S3 ripple WIP renderer loaded");
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public String effectName() {
        return "S3 ripple WIP";
    }

    @Override
    public void beginGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        removeCallbacks(affordanceRunnable);
        ensureBuffers();
        gestureActive = true;
        animating = true;
        emptyFrames = 0;
        lastX = screenX;
        lastY = screenY;
        rippleDistance = 0f;
        pressStartedAt = SystemClock.uptimeMillis();
        addRipple(screenX, screenY, intensityForOrientation() * 4f);
        play(downSound);
        Log.i(TAG, "s3 ripple begin x=" + Math.round(screenX)
                + " y=" + Math.round(screenY)
                + " bg=" + backgroundSource);
        invalidate();
    }

    @Override
    public void updateGesture(float screenX, float screenY) {
        if (destroyed) {
            return;
        }
        if (!gestureActive) {
            beginGesture(screenX, screenY);
            return;
        }
        float dx = screenX - lastX;
        float dy = screenY - lastY;
        rippleDistance += (float) Math.hypot(dx, dy);
        if (rippleDistance > MOVE_RIPPLE_DISTANCE_PX) {
            addRipple(screenX, screenY, intensityForOrientation() * 3f);
            rippleDistance = 0f;
        }
        lastX = screenX;
        lastY = screenY;
        animating = true;
        invalidate();
    }

    @Override
    public void finishGesture(boolean completed) {
        if (!gestureActive || destroyed) {
            return;
        }
        gestureActive = false;
        long heldMs = SystemClock.uptimeMillis() - pressStartedAt;
        if (heldMs > UP_RIPPLE_HOLD_MS) {
            addRipple(lastX, lastY, intensityForOrientation() * 4f);
        }
        play(upSound);
        animating = true;
        Log.i(TAG, "s3 ripple finish completed=" + completed
                + " heldMs=" + heldMs
                + " x=" + Math.round(lastX)
                + " y=" + Math.round(lastY));
        invalidate();
    }

    @Override
    public void cancelGesture() {
        gestureActive = false;
        animating = true;
        invalidate();
    }

    @Override
    public void resetEffect() {
        removeCallbacks(affordanceRunnable);
        gestureActive = false;
        animating = false;
        clearBuffers();
        invalidate();
    }

    @Override
    public void warmUp() {
        ensureBuffers();
    }

    @Override
    public void showUnlockAffordance(Rect screenRect, long startDelayMs) {
        if (destroyed) {
            return;
        }
        removeCallbacks(affordanceRunnable);
        postDelayed(affordanceRunnable, Math.max(0L, startDelayMs));
    }

    @Override
    public boolean hasBackgroundSourceBitmap() {
        return externalBackground
                && backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap.getWidth() == getRenderWidth()
                && backgroundBitmap.getHeight() == getRenderHeight();
    }

    @Override
    public void setBackgroundSourceBitmap(Bitmap source, String sourceName) {
        if (destroyed || source == null || source.isRecycled()) {
            return;
        }
        Bitmap next = createCenterCropBitmap(source, getRenderWidth(), getRenderHeight());
        next.prepareToDraw();
        if (backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap != fallbackWallpaper) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = next;
        externalBackground = true;
        backgroundSource = sourceName == null ? "external" : sourceName;
        Log.i(TAG, "s3 ripple background map ready source=" + backgroundSource
                + " size=" + next.getWidth() + "x" + next.getHeight());
    }

    @Override
    public void clearBackgroundSourceBitmap() {
        if (backgroundBitmap != null
                && !backgroundBitmap.isRecycled()
                && backgroundBitmap != fallbackWallpaper) {
            backgroundBitmap.recycle();
        }
        backgroundBitmap = null;
        externalBackground = false;
        backgroundSource = "fallback";
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        resetEffect();
        destroyed = true;
        soundPool.release();
        clearBackgroundSourceBitmap();
        recycle(reflectionMap);
        recycle(fallbackWallpaper);
        recycle(rippleBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (destroyed) {
            return;
        }
        ensureBuffers();
        boolean empty = stepRipple();
        prepareRenderHeight();
        renderRipplePixels();
        dst.set(0, 0, getRenderWidth(), getRenderHeight());
        if (rippleBitmap != null && !rippleBitmap.isRecycled()) {
            canvas.drawBitmap(rippleBitmap, null, dst, paint);
        }
        if (empty) {
            emptyFrames++;
        } else {
            emptyFrames = 0;
        }
        if (gestureActive || animating) {
            animating = emptyFrames < 8;
            if (animating) {
                postInvalidateOnAnimation();
            }
        }
    }

    private void triggerAffordanceRipple() {
        if (destroyed || gestureActive) {
            return;
        }
        ensureBuffers();
        addRipple(getRenderWidth() * 0.5f, getRenderHeight() * 0.5f,
                intensityForOrientation() * 3.2f);
        animating = true;
        emptyFrames = 0;
        invalidate();
        Log.i(TAG, "s3 ripple affordance play center");
    }

    private boolean stepRipple() {
        if (heightMap == null || velocity == null || nextHeight == null
                || nextVelocity == null || densityMap == null || nextDensity == null) {
            return true;
        }
        boolean isEmpty = true;
        for (int x = 1; x < detailWidth - 1; x++) {
            for (int y = 1; y < detailHeight - 1; y++) {
                int i = y * detailWidth + x;
                float lap = heightMap[i - detailWidth]
                        + heightMap[i - 1]
                        + heightMap[i + 1]
                        + heightMap[i + detailWidth]
                        - heightMap[i] * 4f;
                velocity[i] = (velocity[i] + lap * NORMAL_WAVE_VELOCITY)
                        * NORMAL_DAMPING;
                if (Math.abs(velocity[i]) > EMPTY_THRESHOLD) {
                    isEmpty = false;
                }
            }
        }
        for (int x = 1; x < detailWidth - 1; x++) {
            for (int y = 1; y < detailHeight - 1; y++) {
                int i = y * detailWidth + x;
                heightMap[i] = clamp(heightMap[i] + velocity[i],
                        -HEIGHT_CLAMP,
                        HEIGHT_CLAMP);
            }
        }
        for (int x = 1; x < detailWidth - 1; x++) {
            for (int y = 1; y < detailHeight - 1; y++) {
                int i = y * detailWidth + x;
                float lap = heightMap[i - detailWidth]
                        + heightMap[i - 1]
                        + heightMap[i + 1]
                        + heightMap[i + detailWidth]
                        - heightMap[i] * 4f;
                heightMap[i] = clamp(heightMap[i] + lap * NORMAL_RELAX,
                        -HEIGHT_CLAMP,
                        HEIGHT_CLAMP);

                float densityLap = densityMap[i - detailWidth]
                        + densityMap[i - 1]
                        + densityMap[i + 1]
                        + densityMap[i + detailWidth]
                        - densityMap[i] * 4f;
                nextDensity[i] = Math.max(0f,
                        (densityMap[i] + densityLap * DENSITY_DIFFUSE) * DENSITY_DAMPING);
                if (Math.abs(heightMap[i]) > EMPTY_THRESHOLD
                        || Math.abs(velocity[i]) > EMPTY_THRESHOLD
                        || nextDensity[i] > DENSITY_EMPTY_THRESHOLD) {
                    isEmpty = false;
                }
            }
        }
        for (int x = 0; x < detailWidth; x++) {
            clearCell(x);
            clearCell((detailHeight - 1) * detailWidth + x);
        }
        for (int y = 1; y < detailHeight - 1; y++) {
            clearCell(y * detailWidth);
            clearCell(y * detailWidth + detailWidth - 1);
        }
        float[] densitySwap = densityMap;
        densityMap = nextDensity;
        nextDensity = densitySwap;
        return isEmpty;
    }

    private void addRipple(float screenX, float screenY, float intensity) {
        ensureBuffers();
        if (velocity == null || densityMap == null) {
            return;
        }
        float[] nativePoint = nativeRipplePoint(screenX, screenY);
        float cx = nativePoint[0];
        float cy = nativePoint[1];
        int x0 = (int) Math.floor(cx - RIPPLE_BOUNDS_PAD);
        int y0 = (int) Math.floor(cy - RIPPLE_BOUNDS_PAD);
        int x1 = (int) Math.ceil(cx + RIPPLE_BOUNDS_PAD);
        int y1 = (int) Math.ceil(cy + RIPPLE_BOUNDS_PAD);
        x0 = clamp(x0, 1, detailWidth - 2);
        y0 = clamp(y0, 1, detailHeight - 2);
        x1 = clamp(x1, x0 + 1, detailWidth - 1);
        y1 = clamp(y1, y0 + 1, detailHeight - 1);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                float dist = (float) Math.hypot(cx - x, cy - y);
                float amount = RIPPLE_RADIUS - dist;
                if (amount > 0f) {
                    int i = y * detailWidth + x;
                    velocity[i] += amount * intensity;
                    densityMap[i] += amount * intensity * DENSITY_IMPULSE_SCALE;
                }
            }
        }
    }

    private float[] nativeRipplePoint(float screenX, float screenY) {
        float width = Math.max(1f, getRenderWidth());
        float height = Math.max(1f, getRenderHeight());
        boolean portrait = height >= width;
        float xRatio = portrait ? PORTRAIT_X_RATIO : LANDSCAPE_X_RATIO;
        float yRatio = portrait ? PORTRAIT_Y_RATIO : LANDSCAPE_Y_RATIO;
        float glX = (screenX - width * 0.5f) * xRatio / width;
        float glY = -((height - screenY) - height * 0.5f) * yRatio / height;

        float mx = glY;
        float my = glX;
        float cx = (mx / MESH_SIZE_WIDTH + 0.5f) * detailWidth;
        float cy = (my / MESH_SIZE_HEIGHT + 0.5f) * detailHeight;
        return new float[] {
                clamp(cx, 1f, detailWidth - 2f),
                clamp(cy, 1f, detailHeight - 2f)
        };
    }

    private void renderRipplePixels() {
        if (pixels == null || rippleBitmap == null || rippleBitmap.isRecycled()) {
            return;
        }
        Bitmap source = colorSource();
        int sourceWidth = source == null || source.isRecycled() ? 0 : source.getWidth();
        int sourceHeight = source == null || source.isRecycled() ? 0 : source.getHeight();
        if (!externalBackground || sourceWidth <= 0 || sourceHeight <= 0) {
            clearOutputPixels();
            return;
        }
        int reflectionWidth = reflectionMap == null || reflectionMap.isRecycled()
                ? 0
                : reflectionMap.getWidth();
        int reflectionHeight = reflectionMap == null || reflectionMap.isRecycled()
                ? 0
                : reflectionMap.getHeight();

        for (int y = 0; y < detailHeight; y++) {
            int row = y * detailWidth;
            for (int x = 0; x < detailWidth; x++) {
                int outIndex = row + x;
                if (x == 0 || y == 0 || x == detailWidth - 1 || y == detailHeight - 1) {
                    pixels[outIndex] = 0;
                    continue;
                }
                int fieldX = y;
                int fieldY = x;
                int i = fieldY * detailWidth + fieldX;
                float dx = (renderHeight[i + detailWidth] - renderHeight[i - detailWidth]) * 0.5f;
                float dy = (renderHeight[i + 1] - renderHeight[i - 1]) * 0.5f;
                float h = renderHeight[i];
                float gradient = (float) Math.hypot(dx, dy);
                float alphaF = clamp01(Math.abs(h) * ALPHA_SCALE_HEIGHT
                        + gradient * ALPHA_SCALE_GRADIENT);
                alphaF = Math.min(alphaF, ALPHA_MAX);
                if (alphaF <= 0.01f) {
                    pixels[outIndex] = 0;
                    continue;
                }

                float invNormalLength = 1f / (float) Math.sqrt(dx * dx + dy * dy
                        + NORMAL_Z * NORMAL_Z);
                float nx = dx * invNormalLength;
                float ny = dy * invNormalLength;
                float nz = NORMAL_Z * invNormalLength;
                float refractX = nx * REFRACTIVE_INDEX * REFRACTION_PIXEL_SCALE;
                float refractY = ny * REFRACTIVE_INDEX * REFRACTION_PIXEL_SCALE;

                int baseX = clamp(Math.round(x / Math.max(1f, detailWidth - 1)
                                * Math.max(1, sourceWidth - 1)),
                        0,
                        Math.max(0, sourceWidth - 1));
                int baseY = clamp(Math.round(y / Math.max(1f, detailHeight - 1)
                                * Math.max(1, sourceHeight - 1)),
                        0,
                        Math.max(0, sourceHeight - 1));
                int sx = clamp(Math.round(x / Math.max(1f, detailWidth - 1)
                                * Math.max(1, sourceWidth - 1) + refractX),
                        0,
                        Math.max(0, sourceWidth - 1));
                int sy = clamp(Math.round(y / Math.max(1f, detailHeight - 1)
                                * Math.max(1, sourceHeight - 1) + refractY),
                        0,
                        Math.max(0, sourceHeight - 1));
                int base = sourceWidth > 0 && sourceHeight > 0
                        ? source.getPixel(baseX, baseY)
                        : Color.rgb(226, 238, 248);
                int refracted = sourceWidth > 0 && sourceHeight > 0
                        ? source.getPixel(sx, sy)
                        : Color.rgb(226, 238, 248);

                int rx = reflectionWidth <= 0 ? 0 : clamp(Math.round((0.5f + nx * 0.5f)
                        * (reflectionWidth - 1)), 0, reflectionWidth - 1);
                int ry = reflectionHeight <= 0 ? 0 : clamp(Math.round((0.5f + ny * 0.5f)
                        * (reflectionHeight - 1)), 0, reflectionHeight - 1);
                int reflection = reflectionWidth > 0 && reflectionHeight > 0
                        ? reflectionMap.getPixel(rx, ry)
                        : Color.WHITE;
                float nDotL = Math.max(0f, nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
                float nDotHalf = nDotL;
                float fresnel = FRESNEL_RATIO * clamp(nDotL - 0.99f, 0f, 0.3f);
                float specular = clamp(SPECULAR_RATIO
                        * (float) Math.pow(Math.max(0f, nDotHalf), SPECULAR_EXPONENT),
                        1f,
                        4.5f);
                float t = clamp(Math.abs(h) * 0.012f, 0f, 1.13f);
                float waterScale = t * specular * (REFLECTION_RATIO + fresnel);
                int samsungComposite = addWaterTerm(refracted, reflection, waterScale);
                samsungComposite = maxChannels(base, samsungComposite);
                int highlighted = encodePositiveDelta(base, samsungComposite, alphaF);
                pixels[outIndex] = Color.argb(Math.round(alphaF * 255f),
                        Color.red(highlighted),
                        Color.green(highlighted),
                        Color.blue(highlighted));
            }
        }
        rippleBitmap.setPixels(pixels, 0, detailWidth, 0, 0, detailWidth, detailHeight);
    }

    private void clearOutputPixels() {
        if (pixels == null || rippleBitmap == null || rippleBitmap.isRecycled()) {
            return;
        }
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0;
        }
        rippleBitmap.setPixels(pixels, 0, detailWidth, 0, 0, detailWidth, detailHeight);
    }

    private void prepareRenderHeight() {
        if (heightMap == null || renderHeight == null) {
            return;
        }
        System.arraycopy(heightMap, 0, renderHeight, 0, heightMap.length);
    }

    private void clearCell(int i) {
        heightMap[i] = 0f;
        renderHeight[i] = 0f;
        velocity[i] = 0f;
        nextHeight[i] = 0f;
        nextVelocity[i] = 0f;
        densityMap[i] = 0f;
        nextDensity[i] = 0f;
        pixels[i] = 0;
    }

    private float intensityForOrientation() {
        return getRenderHeight() >= getRenderWidth()
                ? PORTRAIT_INTENSITY
                : LANDSCAPE_INTENSITY;
    }

    private void ensureBuffers() {
        int nextWidth = DETAIL_SIZE;
        int nextGridHeight = DETAIL_SIZE;
        if (heightMap != null && detailWidth == nextWidth && detailHeight == nextGridHeight) {
            return;
        }
        detailWidth = nextWidth;
        detailHeight = nextGridHeight;
        int size = detailWidth * detailHeight;
        heightMap = new float[size];
        renderHeight = new float[size];
        velocity = new float[size];
        nextHeight = new float[size];
        nextVelocity = new float[size];
        densityMap = new float[size];
        nextDensity = new float[size];
        pixels = new int[size];
        recycle(rippleBitmap);
        rippleBitmap = Bitmap.createBitmap(detailWidth, detailHeight, Bitmap.Config.ARGB_8888);
        Log.i(TAG, "s3 ripple buffers ready " + detailWidth + "x" + detailHeight);
    }

    private void clearBuffers() {
        if (heightMap != null) {
            for (int i = 0; i < heightMap.length; i++) {
                heightMap[i] = 0f;
                renderHeight[i] = 0f;
                velocity[i] = 0f;
                nextHeight[i] = 0f;
                nextVelocity[i] = 0f;
                densityMap[i] = 0f;
                nextDensity[i] = 0f;
                pixels[i] = 0;
            }
        }
        if (rippleBitmap != null && !rippleBitmap.isRecycled()) {
            rippleBitmap.eraseColor(Color.TRANSPARENT);
        }
    }

    private Bitmap colorSource() {
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            return backgroundBitmap;
        }
        if (fallbackWallpaper != null && !fallbackWallpaper.isRecycled()) {
            return fallbackWallpaper;
        }
        return null;
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

    private Bitmap createCenterCropBitmap(Bitmap source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float srcRatio = source.getWidth() / (float) source.getHeight();
        float dstRatio = width / (float) height;
        Rect src;
        if (srcRatio > dstRatio) {
            int srcWidth = Math.max(1, Math.round(source.getHeight() * dstRatio));
            int left = Math.max(0, (source.getWidth() - srcWidth) / 2);
            src = new Rect(left, 0, Math.min(source.getWidth(), left + srcWidth),
                    source.getHeight());
        } else {
            int srcHeight = Math.max(1, Math.round(source.getWidth() / dstRatio));
            int top = Math.max(0, (source.getHeight() - srcHeight) / 2);
            src = new Rect(0, top, source.getWidth(),
                    Math.min(source.getHeight(), top + srcHeight));
        }
        Canvas canvas = new Canvas(out);
        Paint scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG
                | Paint.DITHER_FLAG);
        canvas.drawBitmap(source, src, new Rect(0, 0, width, height), scalePaint);
        return out;
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

    private int mix(int from, int to, float amount) {
        float t = clamp01(amount);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }

    private int addLight(int color, float amount) {
        int boost = Math.round(clamp01(amount) * 255f);
        return Color.rgb(
                Math.min(255, Color.red(color) + boost),
                Math.min(255, Color.green(color) + boost),
                Math.min(255, Color.blue(color) + boost));
    }

    private int addWaterTerm(int base, int water, float amount) {
        return Color.rgb(
                clamp(Math.round(Color.red(base) + Color.red(water) * amount), 0, 255),
                clamp(Math.round(Color.green(base) + Color.green(water) * amount), 0, 255),
                clamp(Math.round(Color.blue(base) + Color.blue(water) * amount), 0, 255));
    }

    private int encodePositiveDelta(int base, int target, float alpha) {
        float safeAlpha = Math.max(0.01f, alpha);
        return Color.rgb(
                encodePositiveDeltaChannel(Color.red(base), Color.red(target), safeAlpha),
                encodePositiveDeltaChannel(Color.green(base), Color.green(target), safeAlpha),
                encodePositiveDeltaChannel(Color.blue(base), Color.blue(target), safeAlpha));
    }

    private int encodePositiveDeltaChannel(int base, int target, float alpha) {
        return clamp(Math.round(base + Math.max(0, target - base) / alpha), 0, 255);
    }

    private int maxChannels(int a, int b) {
        return Color.rgb(
                Math.max(Color.red(a), Color.red(b)),
                Math.max(Color.green(a), Color.green(b)),
                Math.max(Color.blue(a), Color.blue(b)));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private void play(int soundId) {
        if (!destroyed && soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
