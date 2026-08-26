package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.SoundPool;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;
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
    private final SoundPool soundPool;
    private final int unlockSound;
    private final Object soundLock = new Object();
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Set<Integer> pendingSoundIds = new HashSet<Integer>();
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
        this.soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(EffectAudio.soundPoolAttributes(context))
                .build();
        this.soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override public void onLoadComplete(
                    SoundPool completedPool, int sampleId, int status) {
                handleSoundLoadComplete(completedPool, sampleId, status);
            }
        });
        this.unlockSound = this.soundPool.load(context, R.raw.lg_crystal_unlock, 1);
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
        return "G2 Crystal";
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
        if (z) {
            playSound(this.unlockSound);
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
        synchronized (this.soundLock) {
            this.pendingSoundIds.clear();
            this.loadedSoundIds.clear();
            this.soundPool.setOnLoadCompleteListener(null);
            this.soundPool.release();
        }
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

    private void playSound(int soundId) {
        if (soundId == 0 || this.destroyed
                || !OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
            return;
        }
        synchronized (this.soundLock) {
            if (this.destroyed) {
                return;
            }
            if (!this.loadedSoundIds.contains(soundId)) {
                this.pendingSoundIds.add(soundId);
                return;
            }
            this.soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    private void handleSoundLoadComplete(
            SoundPool completedPool, int sampleId, int status) {
        synchronized (this.soundLock) {
            if (completedPool != this.soundPool || this.destroyed) {
                return;
            }
            if (status != 0) {
                this.pendingSoundIds.remove(sampleId);
                return;
            }
            this.loadedSoundIds.add(sampleId);
            if (this.pendingSoundIds.remove(sampleId)
                    && OverlayPrefs.unlockEffectSoundAllowedNow(getContext())) {
                this.soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
            }
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
        private static final String CRYSTAL_VERTEX_SHADER =
                "varying vec2 vTexCoord;\n"
                + "varying highp vec4 vLightColor;\n"
                + "attribute vec4 aPosition;\n"
                + "attribute vec2 aTexCoord;\n"
                + "attribute vec3 aNormal;\n"
                + "uniform highp mat4 uPMatrix;\n"
                + "uniform highp mat4 uMMatrix;\n"
                + "uniform highp mat4 uVMatrix;\n"
                + "uniform highp mat4 uInverseRotateMatrix;\n"
                + "uniform highp vec3 uLightPos;\n"
                + "uniform highp vec3 uLightPos2;\n"
                + "uniform highp vec2 uSpaceInfo;\n"
                + "uniform highp vec2 uDownPos;\n"
                + "uniform highp float uRadius;\n"
                + "const vec4 ambientLight = vec4(0.2, 0.2, 0.2, 1.0);\n"
                + "const vec4 diffuseMaterial = vec4(0.4, 0.4, 0.4, 1.0);\n"
                + "const vec4 specularMaterial = vec4(1.0, 1.0, 1.0, 1.0);\n"
                + "const float shiness = 80.0;\n"
                + "void calcDirectionalLight(highp vec3 lightDir, highp vec3 normal,"
                + " inout vec4 ambient, inout vec4 diffuse, inout vec4 specular) {\n"
                + "  vec3 lightVec = normalize(lightDir);\n"
                + "  float df = max(0.0, dot(normal, lightVec));\n"
                + "  diffuse += df * diffuseMaterial;\n"
                + "  float enableSpecular = step(0.00001, df);\n"
                + "  vec3 eyeVec = vec3(0.0, 0.0, 1.0);\n"
                + "  vec3 halfVec = normalize(lightVec + eyeVec);\n"
                + "  float sf = pow(max(0.0, dot(normal, halfVec)), shiness);\n"
                + "  specular += specularMaterial * sf * enableSpecular;\n"
                + "  ambient += ambientLight;\n"
                + "}\n"
                + "void main() {\n"
                + "  vec4 pos = aPosition;\n"
                + "  pos.xyz = aPosition.xyz * uRadius;\n"
                + "  gl_Position = uPMatrix * uVMatrix * uMMatrix * pos;\n"
                + "  vec4 ambient = vec4(0.0);\n"
                + "  vec4 diffuse = vec4(0.0);\n"
                + "  vec4 specular = vec4(0.0);\n"
                + "  calcDirectionalLight(uLightPos, aNormal, ambient, diffuse, specular);\n"
                + "  calcDirectionalLight(uLightPos2, aNormal, ambient, diffuse, specular);\n"
                + "  vLightColor = (ambient + diffuse + specular) * 1.2;\n"
                + "  float theta = atan(aNormal.z,"
                + " sqrt(aNormal.x * aNormal.x + aNormal.y * aNormal.y));\n"
                + "  float xy = pos.z / tan(theta);\n"
                + "  float angle = atan(pos.y, pos.x);\n"
                + "  vec2 deltaTexCoord = vec2("
                + "-xy * cos(angle) / uSpaceInfo.x, xy * sin(angle) / uSpaceInfo.y);\n"
                + "  vec2 texCoord = aTexCoord - vec2(0.5);\n"
                + "  texCoord.y *= (uSpaceInfo.y / uSpaceInfo.x);\n"
                + "  texCoord = vec2(uInverseRotateMatrix * vec4(texCoord, 0.0, 1.0));\n"
                + "  texCoord.y *= (uSpaceInfo.x / uSpaceInfo.y);\n"
                + "  texCoord = vec2(0.5) + texCoord * uRadius + uDownPos;\n"
                + "  vTexCoord = texCoord + deltaTexCoord;\n"
                + "}\n";
        private static final String CRYSTAL_FRAGMENT_SHADER =
                "precision mediump float;\n"
                + "varying vec2 vTexCoord;\n"
                + "varying highp vec4 vLightColor;\n"
                + "uniform sampler2D uTexture;\n"
                + "uniform float uAlpha;\n"
                + "void main() {\n"
                + "  highp vec4 col = texture2D(uTexture, vTexCoord);\n"
                + "  col *= vLightColor;\n"
                + "  col.a *= uAlpha;\n"
                + "  gl_FragColor = col;\n"
                + "}\n";
        private static final String MESH_OVERLAY_VERTEX_SHADER =
                "varying vec2 vTexCoord;\n"
                + "attribute vec4 aPosition;\n"
                + "attribute vec2 aTexCoord;\n"
                + "uniform highp mat4 uPMatrix;\n"
                + "uniform highp mat4 uMMatrix;\n"
                + "uniform highp mat4 uVMatrix;\n"
                + "uniform highp float uRadius;\n"
                + "void main() {\n"
                + "  vec4 pos = aPosition;\n"
                + "  pos.xyz = aPosition.xyz * uRadius;\n"
                + "  gl_Position = uPMatrix * uVMatrix * uMMatrix * pos;\n"
                + "  vTexCoord = aTexCoord;\n"
                + "}\n";
        private static final String TEXTURE_FRAGMENT_SHADER =
                "precision mediump float;\n"
                + "varying vec2 vTexCoord;\n"
                + "uniform sampler2D uTexture;\n"
                + "uniform float uAlpha;\n"
                + "void main() {\n"
                + "  highp vec4 col = texture2D(uTexture, vTexCoord);\n"
                + "  col.a *= uAlpha;\n"
                + "  gl_FragColor = col;\n"
                + "}\n";
        private static final String SPRITE_VERTEX_SHADER =
                "attribute vec2 aPosition;\n"
                + "attribute vec2 aTexCoord;\n"
                + "uniform vec2 uViewport;\n"
                + "uniform vec2 uCenterPx;\n"
                + "uniform vec2 uSizePx;\n"
                + "uniform float uScale;\n"
                + "uniform float uAngle;\n"
                + "varying vec2 vTexCoord;\n"
                + "void main() {\n"
                + "  float c = cos(uAngle);\n"
                + "  float s = sin(uAngle);\n"
                + "  vec2 p = aPosition * uSizePx * uScale;\n"
                + "  p = vec2(c * p.x - s * p.y, s * p.x + c * p.y);\n"
                + "  vec2 screen = vec2(uCenterPx.x + p.x, uCenterPx.y - p.y);\n"
                + "  vec2 clip = vec2(screen.x / uViewport.x * 2.0 - 1.0,"
                + " 1.0 - screen.y / uViewport.y * 2.0);\n"
                + "  gl_Position = vec4(clip, 0.0, 1.0);\n"
                + "  vTexCoord = aTexCoord;\n"
                + "}\n";
        private final FloatBuffer spriteQuad;
        private final MotionPlan motion;
        private final float[] projectionMatrix = new float[16];
        private final float[] viewMatrix = new float[16];
        private final float[] modelMatrix = new float[16];
        private final float[] inverseRotationMatrix = new float[16];
        private Thread glThread;
        private int crystalProgram;
        private int meshOverlayProgram;
        private int spriteProgram;
        private int wallpaperTexture;
        private int mainLayerTexture;
        private int lightingOneTexture;
        private int lightingTwoTexture;
        private int shadowTexture;
        private CrystalMesh mesh;
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
            this.spriteQuad = CrystalPrismBetaEffectView.directFloats(new float[]{
                    -0.5f, -0.5f, 0.0f, 1.0f,
                    0.5f, -0.5f, 1.0f, 1.0f,
                    -0.5f, 0.5f, 0.0f, 0.0f,
                    0.5f, 0.5f, 1.0f, 0.0f
            });
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
            this.crystalProgram = 0;
            this.meshOverlayProgram = 0;
            this.spriteProgram = 0;
            this.initializationFailed = false;
            try {
                this.crystalProgram = CrystalPrismBetaEffectView.linkProgram(
                        CRYSTAL_VERTEX_SHADER, CRYSTAL_FRAGMENT_SHADER);
                this.meshOverlayProgram = CrystalPrismBetaEffectView.linkProgram(
                        MESH_OVERLAY_VERTEX_SHADER, TEXTURE_FRAGMENT_SHADER);
                this.spriteProgram = CrystalPrismBetaEffectView.linkProgram(
                        SPRITE_VERTEX_SHADER, TEXTURE_FRAGMENT_SHADER);
                this.mainLayerTexture = loadResourceTexture(R.drawable.lg_crystal_main_layer);
                this.lightingOneTexture = loadResourceTexture(R.drawable.lg_crystal_lighting_1);
                this.lightingTwoTexture = loadResourceTexture(R.drawable.lg_crystal_lighting_2);
                this.shadowTexture = loadResourceTexture(R.drawable.lg_crystal_shadow_layer);
                if (this.crystalProgram == 0 || this.meshOverlayProgram == 0
                        || this.spriteProgram == 0 || this.mainLayerTexture == 0
                        || this.lightingOneTexture == 0 || this.lightingTwoTexture == 0
                        || this.shadowTexture == 0) {
                    throw new IllegalStateException("Crystal oracle resources missing");
                }
                CrystalPrismBetaEffectView.this.advanceReadiness(
                        3, "OEM Crystal mesh programs and textures created");
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
            this.mesh = new CrystalMesh(this.width, this.height);
            Matrix.frustumM(this.projectionMatrix, 0,
                    -this.width / 512.0f, this.width / 512.0f,
                    -this.height / 512.0f, this.height / 512.0f,
                    4.0f, 512.0f);
            Matrix.setIdentityM(this.viewMatrix, 0);
            Matrix.translateM(this.viewMatrix, 0, 0.0f, 0.0f, -256.0f);
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
                CrystalPrismBetaEffectView.this.advanceReadiness(
                        5, "transparent OEM Crystal warm frame drawn");
                return;
            }
            drawCrystal(frameAdvance);
            CrystalPrismBetaEffectView.this.advanceReadiness(
                    5, "first OEM Crystal mesh frame");
        }

        private void drawCrystal(MotionPlan.Frame frame) {
            if (this.mesh == null) {
                return;
            }
            float radiusUnits = frame.radiusPx * 0.25f;
            float angleDegrees = -90.0f * (frame.radiusPx / Math.max(1.0f, this.width));
            Matrix.setIdentityM(this.modelMatrix, 0);
            Matrix.translateM(this.modelMatrix, 0,
                    (frame.centerX - (this.width * 0.5f)) * 0.25f,
                    ((this.height * 0.5f) - frame.centerY) * 0.25f, 0.0f);
            Matrix.rotateM(this.modelMatrix, 0, angleDegrees, 0.0f, 0.0f, 1.0f);
            Matrix.setIdentityM(this.inverseRotationMatrix, 0);
            Matrix.rotateM(this.inverseRotationMatrix, 0,
                    -angleDegrees, 0.0f, 0.0f, 1.0f);

            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,
                    GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE,
                    GLES20.GL_ONE_MINUS_SRC_ALPHA);

            drawRefractedMesh(frame, radiusUnits);
            drawMainLayerMesh(frame.opacity, radiusUnits);

            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glBlendFuncSeparate(GLES20.GL_ONE,
                    GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE,
                    GLES20.GL_ONE_MINUS_SRC_ALPHA);
            float overlayScale = oracleOverlayScale(frame.radiusPx);
            if (overlayScale > 0.0f) {
                float overlaySize = 666.6667f
                        * getResources().getDisplayMetrics().density;
                float angleRadians = (float) Math.toRadians(angleDegrees);
                drawSprite(this.shadowTexture, frame.centerX, frame.centerY,
                        overlaySize, overlaySize, overlayScale, angleRadians, frame.opacity);
                double lightOneAngle = Math.toRadians(angleDegrees - 54.0f);
                drawSprite(this.lightingOneTexture,
                        frame.centerX + ((float) Math.cos(lightOneAngle)
                                * frame.radiusPx * 0.85f),
                        frame.centerY - ((float) Math.sin(lightOneAngle)
                                * frame.radiusPx * 0.85f),
                        overlaySize, overlaySize, overlayScale, 0.0f, frame.opacity);
                double lightTwoAngle = Math.toRadians(angleDegrees - 56.5f);
                drawSprite(this.lightingTwoTexture,
                        frame.centerX + ((float) Math.cos(lightTwoAngle)
                                * frame.radiusPx * 0.73f),
                        frame.centerY - ((float) Math.sin(lightTwoAngle)
                                * frame.radiusPx * 0.73f),
                        overlaySize, overlaySize, overlayScale, angleRadians, frame.opacity);
            }
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            int drawError = GLES20.glGetError();
            if (drawError != GLES20.GL_NO_ERROR) {
                this.initializationFailed = true;
                String detail = "OEM Crystal draw GL error=0x"
                        + Integer.toHexString(drawError);
                CrystalPrismBetaEffectView.this.failReadiness(detail);
                Log.e(CrystalPrismBetaEffectView.TAG, detail);
            }
        }

        private void drawRefractedMesh(MotionPlan.Frame frame, float radiusUnits) {
            GLES20.glUseProgram(this.crystalProgram);
            bindCommonMeshMatrices(this.crystalProgram, radiusUnits);
            uniform1f(this.crystalProgram, "uAlpha", frame.opacity);
            uniform2f(this.crystalProgram, "uSpaceInfo",
                    this.width * 0.25f, this.height * 0.25f);
            uniform2f(this.crystalProgram, "uDownPos",
                    (frame.centerX - (this.width * 0.5f)) / this.width,
                    -(((this.height * 0.5f) - frame.centerY) / this.height));
            int inverseLocation = GLES20.glGetUniformLocation(
                    this.crystalProgram, "uInverseRotateMatrix");
            GLES20.glUniformMatrix4fv(inverseLocation, 1, false,
                    this.inverseRotationMatrix, 0);
            float ratio = frame.radiusPx / MotionPlan.ORACLE_UNLOCK_RADIUS_PX;
            double orbit = 0.0d;
            if (ratio <= 1.0f) {
                orbit = ratio * Math.PI * 2.0d;
            } else if (ratio <= 2.0f) {
                orbit = (2.0f - ratio) * Math.PI * 2.0d;
            }
            float centerXUnits = (frame.centerX - (this.width * 0.5f)) * 0.25f;
            float centerYUnits = ((this.height * 0.5f) - frame.centerY) * 0.25f;
            uniform3f(this.crystalProgram, "uLightPos2",
                    centerXUnits + ((float) Math.cos(orbit) * radiusUnits),
                    centerYUnits + ((float) Math.sin(orbit) * radiusUnits),
                    radiusUnits * 0.7f);
            float fixedLightRatio = Math.min(ratio, 0.6f);
            uniform3f(this.crystalProgram, "uLightPos",
                    this.width * 0.125f * (1.0f - fixedLightRatio),
                    this.height * 0.125f * (1.0f - fixedLightRatio),
                    fixedLightRatio * 100.0f);
            bindTexture(this.crystalProgram, this.wallpaperTexture);
            drawMesh(this.crystalProgram, false);
        }

        private void drawMainLayerMesh(float alpha, float radiusUnits) {
            GLES20.glUseProgram(this.meshOverlayProgram);
            bindCommonMeshMatrices(this.meshOverlayProgram, radiusUnits);
            uniform1f(this.meshOverlayProgram, "uAlpha", alpha);
            bindTexture(this.meshOverlayProgram, this.mainLayerTexture);
            drawMesh(this.meshOverlayProgram, true);
        }

        private void bindCommonMeshMatrices(int targetProgram, float radiusUnits) {
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(
                    targetProgram, "uPMatrix"), 1, false, this.projectionMatrix, 0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(
                    targetProgram, "uVMatrix"), 1, false, this.viewMatrix, 0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(
                    targetProgram, "uMMatrix"), 1, false, this.modelMatrix, 0);
            uniform1f(targetProgram, "uRadius", radiusUnits);
        }

        private void drawMesh(int targetProgram, boolean overlayUv) {
            int position = GLES20.glGetAttribLocation(targetProgram, "aPosition");
            int normal = GLES20.glGetAttribLocation(targetProgram, "aNormal");
            int texCoord = GLES20.glGetAttribLocation(targetProgram, "aTexCoord");
            GLES20.glEnableVertexAttribArray(position);
            if (normal >= 0) {
                GLES20.glEnableVertexAttribArray(normal);
            }
            GLES20.glEnableVertexAttribArray(texCoord);
            drawMeshBuffer(this.mesh.upperGirdle, GLES20.GL_TRIANGLES, 30,
                    position, normal, texCoord, overlayUv);
            drawMeshBuffer(this.mesh.upperBezel, GLES20.GL_TRIANGLES, 30,
                    position, normal, texCoord, overlayUv);
            for (int i = 0; i < 5; i++) {
                drawMeshBuffer(this.mesh.lowerBezel, GLES20.GL_TRIANGLE_STRIP,
                        i * 4, 4, position, normal, texCoord, overlayUv);
            }
            drawMeshBuffer(this.mesh.star, GLES20.GL_TRIANGLES, 15,
                    position, normal, texCoord, overlayUv);
            drawMeshBuffer(this.mesh.table, GLES20.GL_TRIANGLE_FAN, 5,
                    position, normal, texCoord, overlayUv);
            GLES20.glDisableVertexAttribArray(position);
            if (normal >= 0) {
                GLES20.glDisableVertexAttribArray(normal);
            }
            GLES20.glDisableVertexAttribArray(texCoord);
        }

        private void drawMeshBuffer(FloatBuffer buffer, int mode, int count,
                int position, int normal, int texCoord, boolean overlayUv) {
            drawMeshBuffer(buffer, mode, 0, count,
                    position, normal, texCoord, overlayUv);
        }

        private void drawMeshBuffer(FloatBuffer buffer, int mode, int first, int count,
                int position, int normal, int texCoord, boolean overlayUv) {
            buffer.position(0);
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT,
                    false, 40, (Buffer) buffer);
            if (normal >= 0) {
                buffer.position(3);
                GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT,
                        false, 40, (Buffer) buffer);
            }
            buffer.position(overlayUv ? 8 : 6);
            GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT,
                    false, 40, (Buffer) buffer);
            GLES20.glDrawArrays(mode, first, count);
        }

        private void drawSprite(int texture, float centerX, float centerY,
                float sizeX, float sizeY, float scale, float angle, float alpha) {
            GLES20.glUseProgram(this.spriteProgram);
            uniform2f(this.spriteProgram, "uViewport", this.width, this.height);
            uniform2f(this.spriteProgram, "uCenterPx", centerX, centerY);
            uniform2f(this.spriteProgram, "uSizePx", sizeX, sizeY);
            uniform1f(this.spriteProgram, "uScale", scale);
            uniform1f(this.spriteProgram, "uAngle", angle);
            uniform1f(this.spriteProgram, "uAlpha", alpha);
            bindTexture(this.spriteProgram, texture);
            int position = GLES20.glGetAttribLocation(this.spriteProgram, "aPosition");
            int texCoord = GLES20.glGetAttribLocation(this.spriteProgram, "aTexCoord");
            this.spriteQuad.position(0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT,
                    false, 16, (Buffer) this.spriteQuad);
            this.spriteQuad.position(2);
            GLES20.glEnableVertexAttribArray(texCoord);
            GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT,
                    false, 16, (Buffer) this.spriteQuad);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glDisableVertexAttribArray(texCoord);
        }

        private float oracleOverlayScale(float radiusPx) {
            if (radiusPx <= MotionPlan.ORACLE_MIN_RADIUS_PX) {
                return 0.0f;
            }
            float density = Math.max(0.01f,
                    getResources().getDisplayMetrics().density);
            return radiusPx / (229.27676f * density);
        }

        private void bindTexture(int targetProgram, int texture) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(
                    targetProgram, "uTexture"), 0);
        }

        private void uniform1f(int targetProgram, String name, float value) {
            GLES20.glUniform1f(GLES20.glGetUniformLocation(
                    targetProgram, name), value);
        }

        private void uniform2f(int targetProgram, String name, float x, float y) {
            GLES20.glUniform2f(GLES20.glGetUniformLocation(
                    targetProgram, name), x, y);
        }

        private void uniform3f(int targetProgram, String name,
                float x, float y, float z) {
            GLES20.glUniform3f(GLES20.glGetUniformLocation(
                    targetProgram, name), x, y, z);
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
            if (bitmap == null || bitmap.isRecycled() || this.crystalProgram == 0) {
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

        private int loadResourceTexture(int resourceId) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(
                    getResources(), resourceId, options);
            if (bitmap == null) {
                return 0;
            }
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            int texture = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            if (GLES20.glGetError() == GLES20.GL_NO_ERROR) {
                return texture;
            }
            GLES20.glDeleteTextures(1, new int[]{texture}, 0);
            return 0;
        }

        private void clearTransparent() {
            GLES20.glBindFramebuffer(36160, 0);
            GLES20.glViewport(0, 0, Math.max(1, this.width), Math.max(1, this.height));
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClearDepthf(1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        }

        private void releaseGlObjects() {
            deleteWallpaperTexture();
            deleteTexture(this.mainLayerTexture);
            deleteTexture(this.lightingOneTexture);
            deleteTexture(this.lightingTwoTexture);
            deleteTexture(this.shadowTexture);
            this.mainLayerTexture = 0;
            this.lightingOneTexture = 0;
            this.lightingTwoTexture = 0;
            this.shadowTexture = 0;
            deleteProgram(this.crystalProgram);
            deleteProgram(this.meshOverlayProgram);
            deleteProgram(this.spriteProgram);
            this.crystalProgram = 0;
            this.meshOverlayProgram = 0;
            this.spriteProgram = 0;
            this.mesh = null;
        }

        private void deleteProgram(int targetProgram) {
            if (targetProgram != 0) {
                GLES20.glDeleteProgram(targetProgram);
            }
        }

        private void deleteTexture(int texture) {
            if (texture != 0) {
                GLES20.glDeleteTextures(1, new int[]{texture}, 0);
            }
        }

        private void deleteWallpaperTexture() {
            if (this.wallpaperTexture != 0) {
                GLES20.glDeleteTextures(1, new int[]{this.wallpaperTexture}, 0);
                this.wallpaperTexture = 0;
            }
        }
    }

    static final class MotionPlan {
        static final int IDLE = 0;
        static final int DRAG = 1;
        static final int RETRACT = 2;
        static final int UNLOCK = 3;
        static final int AFFORDANCE = 4;
        static final float ORACLE_MIN_RADIUS_PX = 50.0f;
        static final float ORACLE_UNLOCK_RADIUS_PX = 201.0f;
        private static final long RETRACT_MS = 300;
        private static final long UNLOCK_MS = 400;
        private static final long AFFORDANCE_MS = 900;
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
            this.radiusPx = ORACLE_MIN_RADIUS_PX;
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
                float dragDistance = distance(this.downX, this.downY,
                        clamp(f, 0.0f, this.width), clamp(f2, 0.0f, this.height));
                this.radiusPx = ORACLE_MIN_RADIUS_PX
                        + (dragDistance
                        * ((ORACLE_UNLOCK_RADIUS_PX - ORACLE_MIN_RADIUS_PX)
                        / ORACLE_UNLOCK_RADIUS_PX));
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
                this.unlockTargetRadiusPx = 1.3f
                        * ((float) Math.hypot(this.width, this.height));
            } else {
                this.phase = 2;
            }
        }

        void affordance(float f, float f2, long j) {
            this.centerX = clamp(f, 0.0f, this.width);
            this.centerY = clamp(f2, 0.0f, this.height);
            this.downX = this.centerX;
            this.downY = this.centerY;
            this.radiusPx = ORACLE_MIN_RADIUS_PX;
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
                float fSaturate = saturate(fMax / RETRACT_MS);
                float accelerated = fSaturate * fSaturate;
                this.radiusPx = this.startRadiusPx * (1.0f - accelerated);
                if (fSaturate >= CrystalPrismBetaEffectView.DEFAULT_SPEED) {
                    reset();
                    return new Frame(this.centerX, this.centerY, 0.0f, 0.0f, fMax, false);
                }
            } else if (this.phase == 3) {
                float fSaturate2 = saturate(fMax / UNLOCK_MS);
                float accelerated2 = fSaturate2 * fSaturate2;
                this.radiusPx = this.startRadiusPx
                        + ((this.unlockTargetRadiusPx - this.startRadiusPx)
                        * accelerated2);
                f = 1.0f - fSaturate2;
                if (fSaturate2 >= CrystalPrismBetaEffectView.DEFAULT_SPEED) {
                    reset();
                    return new Frame(this.centerX, this.centerY, 0.0f, 0.0f, fMax, false);
                }
            } else if (this.phase == 4) {
                float fSaturate3 = saturate(fMax / AFFORDANCE_MS);
                this.radiusPx = ORACLE_MIN_RADIUS_PX
                        + (12.0f * ((float) Math.sin(
                        ((double) fSaturate3) * Math.PI * 3.0d)));
                f = 0.26f * (1.0f - fSaturate3);
                if (fSaturate3 >= CrystalPrismBetaEffectView.DEFAULT_SPEED) {
                    reset();
                    return new Frame(this.centerX, this.centerY, 0.0f, 0.0f, fMax, false);
                }
            }
            return new Frame(this.centerX, this.centerY, Math.max(0.0f, this.radiusPx), saturate(f), fMax, true);
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

    static final class CrystalMesh {
        private static final float STEP = (float) Math.toRadians(36.0d);
        private static final float HALF_STEP = STEP * 0.5f;
        private static final float INNER_TAN = (float) Math.tan(Math.toRadians(26.0d));
        private static final float MID_TAN = (float) Math.tan(Math.toRadians(28.0d));
        final FloatBuffer lowerBezel;
        final FloatBuffer star;
        final FloatBuffer table;
        final FloatBuffer upperBezel;
        final FloatBuffer upperGirdle;

        CrystalMesh(float width, float height) {
            float[] source = buildSource(width, height);
            this.upperGirdle = buildFaces(source, new int[][]{
                    {9, 10, 0}, {0, 11, 1}, {1, 12, 2}, {2, 13, 3},
                    {3, 14, 4}, {4, 15, 5}, {5, 16, 6}, {6, 17, 7},
                    {7, 18, 8}, {8, 19, 9}
            });
            this.upperBezel = buildFaces(source, new int[][]{
                    {9, 19, 10}, {0, 10, 11}, {1, 11, 12}, {2, 12, 13},
                    {3, 13, 14}, {4, 14, 15}, {5, 15, 16}, {6, 16, 17},
                    {7, 17, 18}, {8, 18, 19}
            });
            this.lowerBezel = buildFaces(source, new int[][]{
                    {18, 24, 19, 10}, {10, 20, 11, 12},
                    {12, 21, 13, 14}, {14, 22, 15, 16},
                    {16, 23, 17, 18}
            });
            this.star = buildFaces(source, new int[][]{
                    {24, 20, 10}, {20, 21, 12}, {21, 22, 14},
                    {22, 23, 16}, {23, 24, 18}
            });
            this.table = buildFaces(source, new int[][]{
                    {24}, {23}, {22}, {21}, {20}
            });
        }

        private static float[] buildSource(float width, float height) {
            float[] out = new float[250];
            float widthUnits = width * 0.25f;
            float heightUnits = height * 0.25f;
            float halfWidthUnits = widthUnits * 0.5f;
            float halfHeightUnits = heightUnits * 0.5f;
            int cursor = 0;
            for (int i = 0; i < 10; i++) {
                float angle = (i * STEP) + HALF_STEP;
                float x = (float) Math.sin(angle);
                float y = (float) Math.cos(angle);
                cursor = writeSourceVertex(out, cursor, x, y, 0.0f,
                        (x + halfWidthUnits) / widthUnits,
                        (halfHeightUnits - y) / heightUnits,
                        secondaryUv(x, y, -0.008f));
            }
            for (int i = 0; i < 10; i++) {
                boolean even = (i & 1) == 0;
                float radius = even ? 0.82f : 0.87f;
                float x = ((float) Math.sin(i * STEP)) * radius;
                float y = ((float) Math.cos(i * STEP)) * radius;
                float z = (even ? 0.18f : 0.13f) * MID_TAN;
                cursor = writeSourceVertex(out, cursor, x, y, z,
                        (x + halfWidthUnits) / widthUnits,
                        (halfHeightUnits - y) / heightUnits,
                        secondaryUv(x, y, even ? 0.007f : 0.005f));
            }
            float z = 0.537f * INNER_TAN;
            for (int i = 0; i < 5; i++) {
                float angle = (i * STEP * 2.0f) + STEP;
                float x = ((float) Math.sin(angle)) * 0.463f;
                float y = ((float) Math.cos(angle)) * 0.463f;
                cursor = writeSourceVertex(out, cursor, x, y, z,
                        (x + halfWidthUnits) / widthUnits,
                        (halfHeightUnits - y) / heightUnits,
                        secondaryUv(x, y, 0.043f));
            }
            return out;
        }

        private static float[] secondaryUv(float x, float y, float delta) {
            return new float[]{
                    ((0.348f + delta) * x) + 0.5f,
                    0.5f - ((0.348f + delta) * y)
            };
        }

        private static int writeSourceVertex(float[] out, int cursor,
                float x, float y, float z, float u, float v, float[] overlayUv) {
            out[cursor++] = x;
            out[cursor++] = y;
            out[cursor++] = z;
            out[cursor++] = 0.0f;
            out[cursor++] = 0.0f;
            out[cursor++] = 1.0f;
            out[cursor++] = u;
            out[cursor++] = v;
            out[cursor++] = overlayUv[0];
            out[cursor++] = overlayUv[1];
            return cursor;
        }

        private static FloatBuffer buildFaces(float[] source, int[][] faces) {
            int vertexCount = 0;
            for (int[] face : faces) {
                vertexCount += face.length;
            }
            float[] result = new float[vertexCount * 10];
            int cursor = 0;
            for (int[] face : faces) {
                float[] normal = face.length >= 3
                        ? faceNormal(source, face[0], face[1], face[2])
                        : new float[]{0.0f, 0.0f, 1.0f};
                for (int index : face) {
                    int sourceOffset = index * 10;
                    result[cursor++] = source[sourceOffset];
                    result[cursor++] = source[sourceOffset + 1];
                    result[cursor++] = source[sourceOffset + 2];
                    result[cursor++] = normal[0];
                    result[cursor++] = normal[1];
                    result[cursor++] = normal[2];
                    result[cursor++] = source[sourceOffset + 6];
                    result[cursor++] = source[sourceOffset + 7];
                    result[cursor++] = source[sourceOffset + 8];
                    result[cursor++] = source[sourceOffset + 9];
                }
            }
            return directFloats(result);
        }

        private static float[] faceNormal(float[] source, int a, int b, int c) {
            int ia = a * 10;
            int ib = b * 10;
            int ic = c * 10;
            float ux = source[ib] - source[ia];
            float uy = source[ib + 1] - source[ia + 1];
            float uz = source[ib + 2] - source[ia + 2];
            float vx = source[ic] - source[ia];
            float vy = source[ic + 1] - source[ia + 1];
            float vz = source[ic + 2] - source[ia + 2];
            float x = (uy * vz) - (uz * vy);
            float y = (uz * vx) - (ux * vz);
            float z = (ux * vy) - (uy * vx);
            float length = (float) Math.sqrt((x * x) + (y * y) + (z * z));
            if (length <= 0.000001f) {
                return new float[]{0.0f, 0.0f, 1.0f};
            }
            return new float[]{x / length, y / length, z / length};
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
