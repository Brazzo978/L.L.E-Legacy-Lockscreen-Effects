package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class CrystalPrismBetaEffectView extends GLSurfaceView implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final String TAG = "LLECrystalPrism";
    private static final long GL_RELEASE_TIMEOUT_MS = 350;
    private static final float DEFAULT_SPEED = 1.0f;
    private final Object bitmapLock;
    private final Object readinessLock;
    private final CrystalRenderer renderer;
    private volatile boolean destroyed;
    private volatile boolean paused;
    private volatile boolean externalBackground;
    private volatile boolean animationScheduled;
    private volatile long interactionSerial;
    private volatile Bitmap borrowedBackgroundPending;
    private long backgroundSerial;
    private Bitmap stagedBackground;
    private boolean stagedBackgroundOwned;
    private long stagedBackgroundSerial;
    private boolean highFrameRateEnabled;
    private float speedMultiplier;
    private Runnable affordanceRunnable;
    private int affordanceGeneration;
    private int readinessState;
    private String readinessDetail;
    private UnlockEffectReadiness.ReadinessListener readinessListener;
    private final Runnable frameRunnable;

    public CrystalPrismBetaEffectView(Context context) {
        this(context, false, DEFAULT_SPEED);
    }

    public CrystalPrismBetaEffectView(Context context, boolean z, float f) {
        super(context);
        this.bitmapLock = new Object();
        this.readinessLock = new Object();
        this.renderer = new CrystalRenderer();
        this.readinessState = 1;
        this.readinessDetail = "Crystal prism constructed";
        this.frameRunnable = new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.1
            @Override // java.lang.Runnable
            public void run() {
                if (CrystalPrismBetaEffectView.this.destroyed || CrystalPrismBetaEffectView.this.paused || !CrystalPrismBetaEffectView.this.animationScheduled) {
                    return;
                }
                CrystalPrismBetaEffectView.this.requestRender();
                if (CrystalPrismBetaEffectView.this.renderer.shouldRequestAnotherFrame(CrystalPrismBetaEffectView.this.interactionSerial)) {
                    CrystalPrismBetaEffectView.this.scheduleNextFrame();
                } else {
                    CrystalPrismBetaEffectView.this.animationScheduled = false;
                }
            }
        };
        this.highFrameRateEnabled = z;
        this.speedMultiplier = sanitizeSpeedMultiplier(f);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(false);
        setZOrderOnTop(true);
        getHolder().setFormat(-3);
        setBackgroundColor(0);
        setRenderer(this.renderer);
        setRenderMode(0);
        setFocusable(false);
    }

    public static boolean supportsHighFrameRatePresentation() {
        return true;
    }

    public static float recommendedHighFrameRateSpeedMultiplier() {
        return DEFAULT_SPEED;
    }

    public static int maximumFullSizeWallpaperTextures() {
        return 1;
    }

    public void setHighFrameRateEnabled(boolean z) {
        if (this.highFrameRateEnabled == z) {
            return;
        }
        this.highFrameRateEnabled = z;
        if (this.animationScheduled && !this.destroyed && !this.paused) {
            removeCallbacks(this.frameRunnable);
            scheduleNextFrame();
        }
    }

    public boolean isHighFrameRateEnabled() {
        return this.highFrameRateEnabled;
    }

    public void setSpeedMultiplier(float f) {
        final float fSanitizeSpeedMultiplier = sanitizeSpeedMultiplier(f);
        if (this.speedMultiplier == fSanitizeSpeedMultiplier) {
            return;
        }
        this.speedMultiplier = fSanitizeSpeedMultiplier;
        queueCrystalCommand(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.2
            @Override // java.lang.Runnable
            public void run() {
                CrystalPrismBetaEffectView.this.renderer.setSpeedMultiplier(fSanitizeSpeedMultiplier);
            }
        });
    }

    public float getSpeedMultiplier() {
        return this.speedMultiplier;
    }

    static float sanitizeSpeedMultiplier(float f) {
        if (Float.isNaN(f) || Float.isInfinite(f)) {
            return DEFAULT_SPEED;
        }
        return Math.max(0.75f, Math.min(1.35f, f));
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public View asView() {
        return this;
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public String effectName() {
        return "G2 Crystal Prism (beta)";
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public void beginGesture(float f, float f2) {
        cancelPendingAffordance();
        if (!canRenderEffect()) {
            return;
        }
        final float[] localCoordinates = toLocalCoordinates(f, f2);
        queueCrystalCommand(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.3
            @Override // java.lang.Runnable
            public void run() {
                CrystalPrismBetaEffectView.this.renderer.begin(localCoordinates[0], localCoordinates[1], SystemClock.uptimeMillis());
            }
        });
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public void updateGesture(float f, float f2) {
        if (!canRenderEffect()) {
            return;
        }
        final float[] localCoordinates = toLocalCoordinates(f, f2);
        queueCrystalCommand(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.4
            @Override // java.lang.Runnable
            public void run() {
                CrystalPrismBetaEffectView.this.renderer.drag(localCoordinates[0], localCoordinates[1], SystemClock.uptimeMillis());
            }
        });
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public void finishGesture(final boolean z) {
        if (!canAcceptCommands()) {
            return;
        }
        queueCrystalCommand(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.5
            @Override // java.lang.Runnable
            public void run() {
                CrystalPrismBetaEffectView.this.renderer.release(z, SystemClock.uptimeMillis());
            }
        });
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public void cancelGesture() {
        finishGesture(false);
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public void resetEffect() {
        cancelPendingAffordance();
        if (!canAcceptCommands()) {
            return;
        }
        queueCrystalCommand(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.6
            @Override // java.lang.Runnable
            public void run() {
                CrystalPrismBetaEffectView.this.renderer.reset();
            }
        });
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public void warmUp() {
        if (!this.destroyed && !this.paused) {
            requestRender();
        }
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public void showUnlockAffordance(Rect rect, long j) {
        cancelPendingAffordance();
        if (!canRenderEffect()) {
            return;
        }
        final Rect rect2 = (rect == null || rect.isEmpty()) ? new Rect(0, 0, renderWidth(), renderHeight()) : new Rect(rect);
        final int i = this.affordanceGeneration + 1;
        this.affordanceGeneration = i;
        this.affordanceRunnable = new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.7
            @Override // java.lang.Runnable
            public void run() {
                CrystalPrismBetaEffectView.this.affordanceRunnable = null;
                if (CrystalPrismBetaEffectView.this.canRenderEffect() && i == CrystalPrismBetaEffectView.this.affordanceGeneration) {
                    final float[] localCoordinates = CrystalPrismBetaEffectView.this.toLocalCoordinates(rect2.centerX(), rect2.centerY());
                    CrystalPrismBetaEffectView.this.queueCrystalCommand(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.7.1
                        @Override // java.lang.Runnable
                        public void run() {
                            CrystalPrismBetaEffectView.this.renderer.affordance(localCoordinates[0], localCoordinates[1], SystemClock.uptimeMillis());
                        }
                    });
                }
            }
        };
        postDelayed(this.affordanceRunnable, Math.max(0L, j));
    }

    @Override // com.codex.lle.BackgroundSourceRenderer
    public boolean hasBackgroundSourceBitmap() {
        return this.externalBackground;
    }

    @Override // com.codex.lle.BackgroundSourceRenderer
    public void setBackgroundSourceBitmap(Bitmap bitmap, String str) {
        long j;
        if (this.destroyed || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        int iRenderWidth = renderWidth();
        int iRenderHeight = renderHeight();
        boolean zCanBorrowSharedCache = BackgroundSourceRenderer.canBorrowSharedCache(bitmap, str, iRenderWidth, iRenderHeight);
        Bitmap bitmapCreateMappedBackground = zCanBorrowSharedCache ? bitmap : createMappedBackground(bitmap, iRenderWidth, iRenderHeight);
        if (bitmapCreateMappedBackground == null) {
            Log.e(TAG, "Could not normalize Crystal background source=" + str);
            return;
        }
        bitmapCreateMappedBackground.prepareToDraw();
        Bitmap bitmap2 = null;
        boolean z = false;
        synchronized (this.bitmapLock) {
            if (this.destroyed) {
                bitmap2 = bitmapCreateMappedBackground;
                z = !zCanBorrowSharedCache;
                j = 0;
            } else {
                long j2 = this.backgroundSerial + 1;
                this.backgroundSerial = j2;
                j = j2;
                this.borrowedBackgroundPending = zCanBorrowSharedCache ? bitmapCreateMappedBackground : null;
                if (this.paused) {
                    bitmap2 = this.stagedBackground;
                    z = this.stagedBackgroundOwned;
                    this.stagedBackground = bitmapCreateMappedBackground;
                    this.stagedBackgroundOwned = !zCanBorrowSharedCache;
                    this.stagedBackgroundSerial = j;
                }
            }
        }
        recycleIfOwned(bitmap2, z);
        if (j == 0) {
            recycleIfOwned(bitmapCreateMappedBackground, !zCanBorrowSharedCache);
            return;
        }
        this.externalBackground = false;
        invalidateResourceReadiness("background queued");
        if (this.paused) {
            return;
        }
        enqueueBackgroundInstall(bitmapCreateMappedBackground, !zCanBorrowSharedCache, j, str);
    }

    @Override // com.codex.lle.BackgroundSourceRenderer
    public void clearBackgroundSourceBitmap() {
        final long j;
        Bitmap bitmap = null;
        boolean z = false;
        synchronized (this.bitmapLock) {
            j = this.backgroundSerial + 1;
            this.backgroundSerial = j;
            this.borrowedBackgroundPending = null;
            if (this.paused) {
                bitmap = this.stagedBackground;
                z = this.stagedBackgroundOwned;
                this.stagedBackground = null;
                this.stagedBackgroundOwned = false;
                this.stagedBackgroundSerial = 0L;
            }
        }
        recycleIfOwned(bitmap, z);
        this.externalBackground = false;
        invalidateResourceReadiness("background cleared");
        if (this.paused || this.destroyed) {
            return;
        }
        try {
            queueEvent(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.8
                @Override // java.lang.Runnable
                public void run() {
                    CrystalPrismBetaEffectView.this.renderer.clearBackground(j);
                }
            });
            requestRender();
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not queue Crystal background clear", e);
        }
    }

    @Override // com.codex.lle.BackgroundSourceRenderer
    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && (bitmap == this.borrowedBackgroundPending || this.renderer.isUsingBackground(bitmap));
    }

    @Override // com.codex.lle.UnlockEffectReadiness
    public int getReadinessState() {
        int i;
        synchronized (this.readinessLock) {
            i = this.readinessState;
        }
        return i;
    }

    @Override // com.codex.lle.UnlockEffectReadiness
    public String getReadinessDetail() {
        String str;
        synchronized (this.readinessLock) {
            str = this.readinessDetail;
        }
        return str;
    }

    @Override // com.codex.lle.UnlockEffectReadiness
    public void setReadinessListener(UnlockEffectReadiness.ReadinessListener readinessListener) {
        synchronized (this.readinessLock) {
            this.readinessListener = readinessListener;
        }
        notifyReadiness(readinessListener);
    }

    @Override // android.opengl.GLSurfaceView
    public void onPause() {
        pauseRenderer(false);
    }

    @Override // android.opengl.GLSurfaceView
    public void onResume() {
        if (this.destroyed || !this.paused) {
            return;
        }
        super.onResume();
        this.paused = false;
        advanceReadiness(2, "resumed");
        installStagedBackgroundIfAny();
        requestRender();
    }

    @Override // android.opengl.GLSurfaceView, android.view.SurfaceView, android.view.View
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        advanceReadiness(2, "attached");
        if (this.paused && !this.destroyed) {
            onResume();
        }
    }

    @Override // android.opengl.GLSurfaceView, android.view.SurfaceView, android.view.View
    protected void onDetachedFromWindow() {
        pauseRenderer(false);
        super.onDetachedFromWindow();
    }

    @Override // com.codex.lle.UnlockEffectRenderer
    public void destroy() {
        Bitmap bitmap;
        boolean z;
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        cancelPendingAffordance();
        this.animationScheduled = false;
        removeCallbacks(this.frameRunnable);
        synchronized (this.bitmapLock) {
            bitmap = this.stagedBackground;
            z = this.stagedBackgroundOwned;
            this.stagedBackground = null;
            this.stagedBackgroundOwned = false;
            this.borrowedBackgroundPending = null;
        }
        recycleIfOwned(bitmap, z);
        pauseRenderer(true);
        this.externalBackground = false;
        setReadinessState(-1, "renderer destroyed");
        synchronized (this.readinessLock) {
            this.readinessListener = null;
        }
    }

    private boolean canAcceptCommands() {
        return (this.destroyed || this.paused || this.renderer.initializationFailed) ? false : true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean canRenderEffect() {
        return canAcceptCommands() && this.externalBackground;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void queueCrystalCommand(final Runnable runnable) {
        if (!canAcceptCommands()) {
            return;
        }
        final long j = this.interactionSerial + 1;
        this.interactionSerial = j;
        try {
            queueEvent(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.9
                @Override // java.lang.Runnable
                public void run() {
                    runnable.run();
                    CrystalPrismBetaEffectView.this.renderer.noteInteractionApplied(j);
                }
            });
            this.animationScheduled = true;
            removeCallbacks(this.frameRunnable);
            post(this.frameRunnable);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not queue Crystal gesture", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scheduleNextFrame() {
        if (this.destroyed || this.paused || !this.animationScheduled) {
            return;
        }
        if (this.highFrameRateEnabled) {
            postOnAnimation(this.frameRunnable);
        } else {
            postDelayed(this.frameRunnable, 16L);
        }
    }

    private void enqueueBackgroundInstall(final Bitmap bitmap, final boolean z, final long j, final String str) {
        try {
            queueEvent(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.10
                @Override // java.lang.Runnable
                public void run() {
                    CrystalPrismBetaEffectView.this.renderer.installBackground(bitmap, z, j, str);
                }
            });
            requestRender();
        } catch (RuntimeException e) {
            recycleIfOwned(bitmap, z);
            Log.w(TAG, "Could not queue Crystal background upload", e);
        }
    }

    private void installStagedBackgroundIfAny() {
        Bitmap bitmap;
        boolean z;
        long j;
        synchronized (this.bitmapLock) {
            bitmap = this.stagedBackground;
            z = this.stagedBackgroundOwned;
            j = this.stagedBackgroundSerial;
            this.stagedBackground = null;
            this.stagedBackgroundOwned = false;
            this.stagedBackgroundSerial = 0L;
        }
        if (bitmap != null) {
            enqueueBackgroundInstall(bitmap, z, j, "staged_background");
        }
    }

    private void pauseRenderer(final boolean z) {
        if (this.paused) {
            if (z) {
                this.renderer.releaseWithoutGlThread();
                return;
            }
            return;
        }
        this.animationScheduled = false;
        removeCallbacks(this.frameRunnable);
        if (this.renderer.isGlThread()) {
            this.renderer.releaseCurrentContext(z);
        } else if (this.renderer.hasGlThread()) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            try {
                queueEvent(new Runnable() { // from class: com.codex.lle.CrystalPrismBetaEffectView.11
                    @Override // java.lang.Runnable
                    public void run() {
                        try {
                            CrystalPrismBetaEffectView.this.renderer.releaseCurrentContext(z);
                        } finally {
                            countDownLatch.countDown();
                        }
                    }
                });
                requestRender();
                if (!countDownLatch.await(GL_RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "Timed out waiting for Crystal GL cleanup");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted during Crystal GL cleanup", e);
            } catch (RuntimeException e2) {
                Log.w(TAG, "Could not queue Crystal GL cleanup", e2);
            }
        } else if (z) {
            this.renderer.releaseWithoutGlThread();
        }
        if (!this.paused) {
            super.onPause();
            this.paused = true;
        }
        this.externalBackground = false;
        markReadinessDetached(z ? "destroyed" : "context released");
    }

    private void cancelPendingAffordance() {
        this.affordanceGeneration++;
        if (this.affordanceRunnable != null) {
            removeCallbacks(this.affordanceRunnable);
            this.affordanceRunnable = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public float[] toLocalCoordinates(float f, float f2) {
        int[] iArr = new int[2];
        getLocationOnScreen(iArr);
        return new float[]{f - iArr[0], f2 - iArr[1]};
    }

    private int renderWidth() {
        if (getWidth() > 0) {
            return getWidth();
        }
        return Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int renderHeight() {
        if (getHeight() > 0) {
            return getHeight();
        }
        return Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Bitmap createMappedBackground(Bitmap bitmap, int i, int i2) {
        Rect rect;
        Bitmap bitmapCreateBitmap = null;
        try {
            bitmapCreateBitmap = Bitmap.createBitmap(Math.max(1, i), Math.max(1, i2), Bitmap.Config.ARGB_8888);
            float width = bitmap.getWidth() / Math.max(1, bitmap.getHeight());
            float fMax = i / Math.max(1, i2);
            if (width > fMax) {
                int iMax = Math.max(1, Math.round(bitmap.getHeight() * fMax));
                int iMax2 = Math.max(0, (bitmap.getWidth() - iMax) / 2);
                rect = new Rect(iMax2, 0, Math.min(bitmap.getWidth(), iMax2 + iMax), bitmap.getHeight());
            } else {
                int iMax3 = Math.max(1, Math.round(bitmap.getWidth() / fMax));
                int iMax4 = Math.max(0, (bitmap.getHeight() - iMax3) / 2);
                rect = new Rect(0, iMax4, bitmap.getWidth(), Math.min(bitmap.getHeight(), iMax4 + iMax3));
            }
            new Canvas(bitmapCreateBitmap).drawBitmap(bitmap, rect, new Rect(0, 0, bitmapCreateBitmap.getWidth(), bitmapCreateBitmap.getHeight()), new Paint(6));
            return bitmapCreateBitmap;
        } catch (RuntimeException e) {
            recycleIfOwned(bitmapCreateBitmap, true);
            Log.w(TAG, "Crystal background crop failed", e);
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void recycleIfOwned(Bitmap bitmap, boolean z) {
        if (z && bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public long currentBackgroundSerial() {
        long j;
        synchronized (this.bitmapLock) {
            j = this.backgroundSerial;
        }
        return j;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void advanceReadiness(int i, String str) {
        synchronized (this.readinessLock) {
            if (this.readinessState == -1 || i <= this.readinessState) {
                return;
            }
            this.readinessState = i;
            this.readinessDetail = "Crystal prism: " + str;
            notifyReadiness(this.readinessListener);
        }
    }

    private void setReadinessState(int i, String str) {
        UnlockEffectReadiness.ReadinessListener readinessListener;
        synchronized (this.readinessLock) {
            this.readinessState = i;
            this.readinessDetail = "Crystal prism: " + str;
            readinessListener = this.readinessListener;
        }
        notifyReadiness(readinessListener);
    }

    private void invalidateResourceReadiness(String str) {
        UnlockEffectReadiness.ReadinessListener readinessListener = null;
        synchronized (this.readinessLock) {
            if (this.readinessState >= 4 && this.readinessState != -1) {
                this.readinessState = 3;
                this.readinessDetail = "Crystal prism: " + str;
                readinessListener = this.readinessListener;
            }
        }
        notifyReadiness(readinessListener);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void failReadiness(String str) {
        setReadinessState(-1, str);
    }

    private void markReadinessDetached(String str) {
        if (!this.destroyed) {
            setReadinessState(0, str);
        }
    }

    private void notifyReadiness(UnlockEffectReadiness.ReadinessListener readinessListener) {
        if (readinessListener == null) {
            return;
        }
        try {
            readinessListener.onReadinessChanged();
        } catch (RuntimeException e) {
            Log.w(TAG, "Crystal readiness listener failed", e);
        }
    }

    /* JADX INFO: loaded from: crystal-static-check.jar:com/codex/lle/CrystalPrismBetaEffectView$CrystalRenderer.class */
    private final class CrystalRenderer implements GLSurfaceView.Renderer {
        private static final int NO_TEXTURE = 0;
        private static final String VERTEX_SHADER = "attribute vec2 aPosition;\nvarying vec2 vUv;\nvoid main() {\n  vUv = aPosition * 0.5 + 0.5;\n  gl_Position = vec4(aPosition, 0.0, 1.0);\n}\n";
        private static final String FRAGMENT_SHADER = "precision mediump float;\nuniform sampler2D uWallpaper;\nuniform vec2 uSize;\nuniform vec2 uCenter;\nuniform float uRadius;\nuniform float uOpacity;\nuniform float uTime;\nvarying vec2 vUv;\nfloat saturate(float v) { return clamp(v, 0.0, 1.0); }\nvoid main() {\n  vec2 delta = (vUv - uCenter) * uSize;\n  float distancePx = length(delta);\n  float edge = max(2.0, uRadius * 0.085);\n  float body = 1.0 - smoothstep(uRadius - edge, uRadius + edge, distancePx);\n  if (body <= 0.001 || uOpacity <= 0.001) { discard; }\n  vec2 direction = delta / max(distancePx, 1.0);\n  float angle = atan(direction.y, direction.x);\n  float radial = saturate(distancePx / max(uRadius, 1.0));\n  float sectors = 0.5 + 0.5 * sin(angle * 7.0 + radial * 13.0);\n  float facets = 0.5 + 0.5 * sin(angle * 15.0 - radial * 23.0 + uTime * 0.0007);\n  float refractAmount = (1.0 - radial) * (0.010 + 0.010 * sectors);\n  vec2 offset = direction * refractAmount * (0.35 + 0.65 * facets);\n  vec3 refracted = texture2D(uWallpaper, clamp(vUv - offset, 0.001, 0.999)).rgb;\n  float rim = pow(radial, 2.1) * (0.42 + 0.28 * facets);\n  float innerLight = pow(1.0 - radial, 2.6) * (0.14 + 0.14 * sectors);\n  float lightA = pow(saturate(dot(direction, normalize(vec2(-0.62, 0.78)))), 6.0);\n  float lightB = pow(saturate(dot(direction, normalize(vec2(0.86, -0.50)))), 11.0);\n  vec3 coolLight = vec3(0.36, 0.78, 1.00) * (innerLight + lightA * (1.0 - radial) * 0.46);\n  vec3 warmLight = vec3(1.00, 0.74, 0.36) * (lightB * (1.0 - radial) * 0.26);\n  vec3 shadow = vec3(0.16, 0.22, 0.34) * rim;\n  vec3 colour = refracted * (1.0 - rim * 0.38) + coolLight + warmLight - shadow;\n  float alpha = body * uOpacity * (0.78 + 0.22 * (1.0 - radial));\n  gl_FragColor = vec4(clamp(colour, 0.0, 1.0), alpha);\n}\n";
        private final FloatBuffer quad;
        private final MotionPlan motion;
        private Thread glThread;
        private int program;
        private int wallpaperTexture;
        private int positionLocation;
        private int wallpaperLocation;
        private int sizeLocation;
        private int centerLocation;
        private int radiusLocation;
        private int opacityLocation;
        private int timeLocation;
        private int width;
        private int height;
        private boolean surfaceReady;
        private boolean backgroundReady;
        private volatile boolean initializationFailed;
        private volatile boolean animating;
        private volatile long appliedInteractionSerial;
        private volatile Bitmap activeBackground;
        private Bitmap activeOwnedBackground;

        private CrystalRenderer() {
            this.quad = CrystalPrismBetaEffectView.directFloats(new float[]{-1.0f, -1.0f, CrystalPrismBetaEffectView.DEFAULT_SPEED, -1.0f, -1.0f, CrystalPrismBetaEffectView.DEFAULT_SPEED, CrystalPrismBetaEffectView.DEFAULT_SPEED, CrystalPrismBetaEffectView.DEFAULT_SPEED});
            this.motion = new MotionPlan();
            this.width = 1;
            this.height = 1;
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceCreated(GL10 gl10, EGLConfig eGLConfig) {
            this.glThread = Thread.currentThread();
            this.surfaceReady = true;
            this.backgroundReady = false;
            this.wallpaperTexture = 0;
            this.program = 0;
            this.initializationFailed = false;
            try {
                this.program = CrystalPrismBetaEffectView.linkProgram(VERTEX_SHADER, FRAGMENT_SHADER);
                this.positionLocation = GLES20.glGetAttribLocation(this.program, "aPosition");
                this.wallpaperLocation = GLES20.glGetUniformLocation(this.program, "uWallpaper");
                this.sizeLocation = GLES20.glGetUniformLocation(this.program, "uSize");
                this.centerLocation = GLES20.glGetUniformLocation(this.program, "uCenter");
                this.radiusLocation = GLES20.glGetUniformLocation(this.program, "uRadius");
                this.opacityLocation = GLES20.glGetUniformLocation(this.program, "uOpacity");
                this.timeLocation = GLES20.glGetUniformLocation(this.program, "uTime");
                if (this.positionLocation >= 0 && this.wallpaperLocation >= 0 && this.sizeLocation >= 0 && this.centerLocation >= 0 && this.radiusLocation >= 0 && this.opacityLocation >= 0 && this.timeLocation >= 0) {
                    CrystalPrismBetaEffectView.this.advanceReadiness(3, "GLES program created");
                    return;
                }
                throw new IllegalStateException("Crystal shader locations missing");
            } catch (RuntimeException e) {
                this.initializationFailed = true;
                releaseGlObjects();
                CrystalPrismBetaEffectView.this.failReadiness("GLES initialization failed: " + e.getClass().getSimpleName());
                Log.e(CrystalPrismBetaEffectView.TAG, "Crystal GLES initialization failed", e);
            }
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceChanged(GL10 gl10, int i, int i2) {
            this.width = Math.max(1, i);
            this.height = Math.max(1, i2);
            GLES20.glViewport(0, 0, this.width, this.height);
            this.motion.setViewport(this.width, this.height);
            Bitmap bitmapRemapActiveBackgroundIfNeeded = remapActiveBackgroundIfNeeded(this.width, this.height);
            if (!this.initializationFailed && bitmapRemapActiveBackgroundIfNeeded != null && !bitmapRemapActiveBackgroundIfNeeded.isRecycled()) {
                this.backgroundReady = uploadBackgroundTexture(bitmapRemapActiveBackgroundIfNeeded);
                CrystalPrismBetaEffectView.this.externalBackground = this.backgroundReady;
                if (!this.backgroundReady) {
                    this.initializationFailed = true;
                    CrystalPrismBetaEffectView.this.failReadiness("background restore failed after resize");
                } else {
                    publishResourcesReady();
                }
            }
        }

        private Bitmap remapActiveBackgroundIfNeeded(int i, int i2) {
            Bitmap bitmap = this.activeBackground;
            if (bitmap != null && !bitmap.isRecycled() && (bitmap.getWidth() != i || bitmap.getHeight() != i2)) {
                Bitmap bitmapCreateMappedBackground = CrystalPrismBetaEffectView.this.createMappedBackground(bitmap, i, i2);
                if (bitmapCreateMappedBackground == null) {
                    return bitmap;
                }
                Bitmap bitmap2 = this.activeOwnedBackground;
                this.activeOwnedBackground = bitmapCreateMappedBackground;
                this.activeBackground = bitmapCreateMappedBackground;
                CrystalPrismBetaEffectView.recycleIfOwned(bitmap2, bitmap2 != bitmapCreateMappedBackground);
                return bitmapCreateMappedBackground;
            }
            return bitmap;
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onDrawFrame(GL10 gl10) {
            clearTransparent();
            if (CrystalPrismBetaEffectView.this.destroyed || this.initializationFailed || !this.surfaceReady || !this.backgroundReady) {
                this.animating = false;
                return;
            }
            MotionPlan.Frame frameAdvance = this.motion.advance(SystemClock.uptimeMillis());
            this.animating = frameAdvance.active;
            if (!frameAdvance.active || frameAdvance.opacity <= 0.001f || frameAdvance.radiusPx <= 0.001f) {
                CrystalPrismBetaEffectView.this.advanceReadiness(5, "transparent warm frame drawn");
                return;
            }
            GLES20.glUseProgram(this.program);
            GLES20.glDisable(2929);
            GLES20.glDisable(2884);
            GLES20.glDisable(3042);
            GLES20.glActiveTexture(33984);
            GLES20.glBindTexture(3553, this.wallpaperTexture);
            GLES20.glUniform1i(this.wallpaperLocation, 0);
            GLES20.glUniform2f(this.sizeLocation, this.width, this.height);
            GLES20.glUniform2f(this.centerLocation, frameAdvance.centerX / this.width, CrystalPrismBetaEffectView.DEFAULT_SPEED - (frameAdvance.centerY / this.height));
            GLES20.glUniform1f(this.radiusLocation, frameAdvance.radiusPx);
            GLES20.glUniform1f(this.opacityLocation, frameAdvance.opacity);
            GLES20.glUniform1f(this.timeLocation, frameAdvance.elapsedMs);
            this.quad.position(0);
            GLES20.glEnableVertexAttribArray(this.positionLocation);
            GLES20.glVertexAttribPointer(this.positionLocation, 2, 5126, false, 0, (Buffer) this.quad);
            GLES20.glDrawArrays(5, 0, 4);
            GLES20.glDisableVertexAttribArray(this.positionLocation);
            GLES20.glBindTexture(3553, 0);
            CrystalPrismBetaEffectView.this.advanceReadiness(5, "first transparent Crystal frame");
        }

        void begin(float f, float f2, long j) {
            this.motion.setSpeedMultiplier(CrystalPrismBetaEffectView.this.speedMultiplier);
            this.motion.begin(f, f2, j);
            this.animating = true;
        }

        void drag(float f, float f2, long j) {
            this.motion.drag(f, f2, j);
            this.animating = true;
        }

        void release(boolean z, long j) {
            this.motion.release(z, j);
            this.animating = this.motion.isActive();
        }

        void affordance(float f, float f2, long j) {
            this.motion.setSpeedMultiplier(CrystalPrismBetaEffectView.this.speedMultiplier);
            this.motion.affordance(f, f2, j);
            this.animating = true;
        }

        void reset() {
            this.motion.reset();
            this.animating = false;
        }

        void setSpeedMultiplier(float f) {
            this.motion.setSpeedMultiplier(f);
        }

        void noteInteractionApplied(long j) {
            this.appliedInteractionSerial = j;
        }

        boolean shouldRequestAnotherFrame(long j) {
            return this.animating || this.appliedInteractionSerial < j;
        }

        void installBackground(Bitmap bitmap, boolean z, long j, String str) {
            if (j != CrystalPrismBetaEffectView.this.currentBackgroundSerial()) {
                CrystalPrismBetaEffectView.recycleIfOwned(bitmap, z);
                return;
            }
            Bitmap bitmap2 = this.activeOwnedBackground;
            this.activeOwnedBackground = z ? bitmap : null;
            this.activeBackground = bitmap;
            CrystalPrismBetaEffectView.this.borrowedBackgroundPending = null;
            CrystalPrismBetaEffectView.recycleIfOwned(bitmap2, bitmap2 != bitmap);
            if (!this.surfaceReady || this.initializationFailed) {
                this.backgroundReady = false;
                CrystalPrismBetaEffectView.this.externalBackground = false;
                return;
            }
            this.backgroundReady = uploadBackgroundTexture(bitmap);
            CrystalPrismBetaEffectView.this.externalBackground = this.backgroundReady;
            if (!this.backgroundReady) {
                this.initializationFailed = true;
                CrystalPrismBetaEffectView.this.failReadiness("background upload failed source=" + str);
                Log.e(CrystalPrismBetaEffectView.TAG, "Crystal background upload failed source=" + str);
                return;
            }
            publishResourcesReady();
        }

        void clearBackground(long j) {
            if (j != CrystalPrismBetaEffectView.this.currentBackgroundSerial()) {
                return;
            }
            Bitmap bitmap = this.activeOwnedBackground;
            this.activeOwnedBackground = null;
            this.activeBackground = null;
            this.backgroundReady = false;
            CrystalPrismBetaEffectView.this.externalBackground = false;
            this.motion.reset();
            this.animating = false;
            deleteWallpaperTexture();
            CrystalPrismBetaEffectView.recycleIfOwned(bitmap, true);
        }

        boolean isUsingBackground(Bitmap bitmap) {
            return bitmap != null && bitmap == this.activeBackground;
        }

        boolean hasGlThread() {
            return this.glThread != null;
        }

        boolean isGlThread() {
            return this.glThread != null && Thread.currentThread() == this.glThread;
        }

        void releaseCurrentContext(boolean z) {
            clearTransparent();
            releaseGlObjects();
            this.surfaceReady = false;
            this.backgroundReady = false;
            CrystalPrismBetaEffectView.this.externalBackground = false;
            this.animating = false;
            if (z) {
                Bitmap bitmap = this.activeOwnedBackground;
                this.activeOwnedBackground = null;
                this.activeBackground = null;
                CrystalPrismBetaEffectView.recycleIfOwned(bitmap, true);
            }
        }

        void releaseWithoutGlThread() {
            Bitmap bitmap = this.activeOwnedBackground;
            this.activeOwnedBackground = null;
            this.activeBackground = null;
            CrystalPrismBetaEffectView.recycleIfOwned(bitmap, true);
            this.backgroundReady = false;
            CrystalPrismBetaEffectView.this.externalBackground = false;
            this.animating = false;
        }

        private void publishResourcesReady() {
            if (this.surfaceReady && this.backgroundReady && !this.initializationFailed) {
                CrystalPrismBetaEffectView.this.advanceReadiness(4, "shader and wallpaper texture ready");
                CrystalPrismBetaEffectView.this.requestRender();
            }
        }

        private boolean uploadBackgroundTexture(Bitmap bitmap) {
            if (bitmap == null || bitmap.isRecycled() || this.program == 0) {
                return false;
            }
            deleteWallpaperTexture();
            int[] iArr = new int[1];
            GLES20.glGenTextures(1, iArr, 0);
            this.wallpaperTexture = iArr[0];
            GLES20.glBindTexture(3553, this.wallpaperTexture);
            GLES20.glTexParameteri(3553, 10241, 9729);
            GLES20.glTexParameteri(3553, 10240, 9729);
            GLES20.glTexParameteri(3553, 10242, 33071);
            GLES20.glTexParameteri(3553, 10243, 33071);
            GLUtils.texImage2D(3553, 0, bitmap, 0);
            GLES20.glBindTexture(3553, 0);
            if (GLES20.glGetError() == 0) {
                return true;
            }
            deleteWallpaperTexture();
            return false;
        }

        private void clearTransparent() {
            GLES20.glBindFramebuffer(36160, 0);
            GLES20.glViewport(0, 0, Math.max(1, this.width), Math.max(1, this.height));
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(16384);
        }

        private void releaseGlObjects() {
            deleteWallpaperTexture();
            if (this.program != 0) {
                GLES20.glDeleteProgram(this.program);
                this.program = 0;
            }
        }

        private void deleteWallpaperTexture() {
            if (this.wallpaperTexture != 0) {
                GLES20.glDeleteTextures(1, new int[]{this.wallpaperTexture}, 0);
                this.wallpaperTexture = 0;
            }
        }
    }

    /* JADX INFO: loaded from: crystal-static-check.jar:com/codex/lle/CrystalPrismBetaEffectView$MotionPlan.class */
    static final class MotionPlan {
        static final int IDLE = 0;
        static final int DRAG = 1;
        static final int RETRACT = 2;
        static final int UNLOCK = 3;
        static final int AFFORDANCE = 4;
        private static final long RETRACT_MS = 280;
        private static final long UNLOCK_MS = 460;
        private static final long AFFORDANCE_MS = 920;
        private float centerX;
        private float centerY;
        private float downX;
        private float downY;
        private float radiusPx;
        private float startRadiusPx;
        private float unlockTargetRadiusPx;
        private long phaseStartMs;
        private int width = 1;
        private int height = 1;
        private int phase = 0;
        private float speedMultiplier = CrystalPrismBetaEffectView.DEFAULT_SPEED;

        MotionPlan() {
        }

        void setViewport(int i, int i2) {
            this.width = Math.max(1, i);
            this.height = Math.max(1, i2);
            this.centerX = clamp(this.centerX, 0.0f, this.width);
            this.centerY = clamp(this.centerY, 0.0f, this.height);
        }

        void setSpeedMultiplier(float f) {
            this.speedMultiplier = CrystalPrismBetaEffectView.sanitizeSpeedMultiplier(f);
        }

        void begin(float f, float f2, long j) {
            this.centerX = clamp(f, 0.0f, this.width);
            this.centerY = clamp(f2, 0.0f, this.height);
            this.downX = this.centerX;
            this.downY = this.centerY;
            this.radiusPx = baseRadiusPx();
            this.startRadiusPx = this.radiusPx;
            this.phase = 1;
            this.phaseStartMs = j;
        }

        void drag(float f, float f2, long j) {
            if (this.phase == 0) {
                begin(f, f2, j);
            } else {
                if (this.phase != 1) {
                    return;
                }
                this.radiusPx = Math.min(maximumRadiusPx(), baseRadiusPx() + (distance(this.downX, this.downY, clamp(f, 0.0f, this.width), clamp(f2, 0.0f, this.height)) * 0.74f));
            }
        }

        void release(boolean z, long j) {
            if (this.phase == 0) {
                return;
            }
            this.startRadiusPx = this.radiusPx;
            this.phaseStartMs = j;
            if (z) {
                this.phase = 3;
                this.unlockTargetRadiusPx = farthestCornerDistance(this.centerX, this.centerY) * 1.15f;
            } else {
                this.phase = 2;
            }
        }

        void affordance(float f, float f2, long j) {
            this.centerX = clamp(f, 0.0f, this.width);
            this.centerY = clamp(f2, 0.0f, this.height);
            this.downX = this.centerX;
            this.downY = this.centerY;
            this.radiusPx = baseRadiusPx();
            this.startRadiusPx = this.radiusPx;
            this.phaseStartMs = j;
            this.phase = 4;
        }

        void reset() {
            this.phase = 0;
            this.radiusPx = 0.0f;
            this.startRadiusPx = 0.0f;
        }

        boolean isActive() {
            return this.phase != 0;
        }

        Frame advance(long j) {
            if (this.phase == 0) {
                return new Frame(this.centerX, this.centerY, 0.0f, 0.0f, 0.0f, false);
            }
            float fMax = Math.max(0L, j - this.phaseStartMs) * this.speedMultiplier;
            float f = 1.0f;
            if (this.phase == 2) {
                float fSaturate = saturate(fMax / 280.0f);
                this.radiusPx = this.startRadiusPx * (CrystalPrismBetaEffectView.DEFAULT_SPEED - (fSaturate * fSaturate));
                if (fSaturate >= CrystalPrismBetaEffectView.DEFAULT_SPEED) {
                    reset();
                    return new Frame(this.centerX, this.centerY, 0.0f, 0.0f, fMax, false);
                }
            } else if (this.phase == 3) {
                float fSaturate2 = saturate(fMax / 460.0f);
                this.radiusPx = this.startRadiusPx + ((this.unlockTargetRadiusPx - this.startRadiusPx) * (CrystalPrismBetaEffectView.DEFAULT_SPEED - (((CrystalPrismBetaEffectView.DEFAULT_SPEED - fSaturate2) * (CrystalPrismBetaEffectView.DEFAULT_SPEED - fSaturate2)) * (CrystalPrismBetaEffectView.DEFAULT_SPEED - fSaturate2))));
                if (fSaturate2 > 0.7f) {
                    f = CrystalPrismBetaEffectView.DEFAULT_SPEED - ((fSaturate2 - 0.7f) / 0.3f);
                }
                if (fSaturate2 >= CrystalPrismBetaEffectView.DEFAULT_SPEED) {
                    reset();
                    return new Frame(this.centerX, this.centerY, 0.0f, 0.0f, fMax, false);
                }
            } else if (this.phase == 4) {
                float fSaturate3 = saturate(fMax / 920.0f);
                this.radiusPx = baseRadiusPx() * (CrystalPrismBetaEffectView.DEFAULT_SPEED + (0.15f * ((float) Math.sin(((double) fSaturate3) * 3.141592653589793d * 3.0d))));
                f = 0.26f * (CrystalPrismBetaEffectView.DEFAULT_SPEED - fSaturate3);
                if (fSaturate3 >= CrystalPrismBetaEffectView.DEFAULT_SPEED) {
                    reset();
                    return new Frame(this.centerX, this.centerY, 0.0f, 0.0f, fMax, false);
                }
            }
            return new Frame(this.centerX, this.centerY, Math.max(0.0f, this.radiusPx), saturate(f), fMax, true);
        }

        private float baseRadiusPx() {
            return Math.max(46.0f, Math.min(132.0f, Math.min(this.width, this.height) * 0.105f));
        }

        private float maximumRadiusPx() {
            return Math.max(baseRadiusPx(), farthestCornerDistance(this.centerX, this.centerY) * 1.15f);
        }

        private float farthestCornerDistance(float f, float f2) {
            return Math.max(Math.max(distance(f, f2, 0.0f, 0.0f), distance(f, f2, this.width, 0.0f)), Math.max(distance(f, f2, 0.0f, this.height), distance(f, f2, this.width, this.height)));
        }

        static float distance(float f, float f2, float f3, float f4) {
            return (float) Math.hypot(f3 - f, f4 - f2);
        }

        private static float saturate(float f) {
            return Math.max(0.0f, Math.min(CrystalPrismBetaEffectView.DEFAULT_SPEED, f));
        }

        private static float clamp(float f, float f2, float f3) {
            return Math.max(f2, Math.min(f3, f));
        }

        /* JADX INFO: loaded from: crystal-static-check.jar:com/codex/lle/CrystalPrismBetaEffectView$MotionPlan$Frame.class */
        static final class Frame {
            final float centerX;
            final float centerY;
            final float radiusPx;
            final float opacity;
            final float elapsedMs;
            final boolean active;

            Frame(float f, float f2, float f3, float f4, float f5, boolean z) {
                this.centerX = f;
                this.centerY = f2;
                this.radiusPx = f3;
                this.opacity = f4;
                this.elapsedMs = f5;
                this.active = z;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static FloatBuffer directFloats(float[] fArr) {
        FloatBuffer floatBufferAsFloatBuffer = ByteBuffer.allocateDirect(fArr.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        floatBufferAsFloatBuffer.put(fArr).position(0);
        return floatBufferAsFloatBuffer;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int linkProgram(String str, String str2) {
        int iCompileShader = compileShader(35633, str, "vertex");
        int iCompileShader2 = compileShader(35632, str2, "fragment");
        int iGlCreateProgram = GLES20.glCreateProgram();
        if (iGlCreateProgram == 0) {
            GLES20.glDeleteShader(iCompileShader);
            GLES20.glDeleteShader(iCompileShader2);
            throw new IllegalStateException("Could not create Crystal program");
        }
        GLES20.glAttachShader(iGlCreateProgram, iCompileShader);
        GLES20.glAttachShader(iGlCreateProgram, iCompileShader2);
        GLES20.glLinkProgram(iGlCreateProgram);
        int[] iArr = new int[1];
        GLES20.glGetProgramiv(iGlCreateProgram, 35714, iArr, 0);
        GLES20.glDeleteShader(iCompileShader);
        GLES20.glDeleteShader(iCompileShader2);
        if (iArr[0] == 0) {
            String strGlGetProgramInfoLog = GLES20.glGetProgramInfoLog(iGlCreateProgram);
            GLES20.glDeleteProgram(iGlCreateProgram);
            throw new IllegalStateException("Crystal program link failed: " + strGlGetProgramInfoLog);
        }
        return iGlCreateProgram;
    }

    private static int compileShader(int i, String str, String str2) {
        int iGlCreateShader = GLES20.glCreateShader(i);
        if (iGlCreateShader == 0) {
            throw new IllegalStateException("Could not create Crystal " + str2 + " shader");
        }
        GLES20.glShaderSource(iGlCreateShader, str);
        GLES20.glCompileShader(iGlCreateShader);
        int[] iArr = new int[1];
        GLES20.glGetShaderiv(iGlCreateShader, 35713, iArr, 0);
        if (iArr[0] == 0) {
            String strGlGetShaderInfoLog = GLES20.glGetShaderInfoLog(iGlCreateShader);
            GLES20.glDeleteShader(iGlCreateShader);
            throw new IllegalStateException("Crystal " + str2 + " shader failed: " + strGlGetShaderInfoLog);
        }
        return iGlCreateShader;
    }
}
