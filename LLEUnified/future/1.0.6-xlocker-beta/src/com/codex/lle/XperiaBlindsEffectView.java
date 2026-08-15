package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/* JADX INFO: loaded from: XperiaBlindsEffectView.class */
public final class XperiaBlindsEffectView extends View implements UnlockEffectRenderer, BackgroundSourceRenderer, UnlockEffectReadiness {
    private static final int STRIP_COUNT = 17;
    private static final int AFFECTED_STRIP_COUNT = 5;
    private static final float AFFECTED_RANGE = 0.29411766f;
    private static final float MAX_FOLD_DEGREES = 17.0f;
    private static final float CAMERA_DEPTH = 3.0f;
    private static final float SPRING_STIFFNESS = 400.0f;
    private static final float SPRING_DAMPING_RATIO = 0.85f;
    private static final float EXIT_DURATION_MS = 300.0f;
    private static final float IDLE_POSITION_EPSILON = 0.0015f;
    private static final float IDLE_VELOCITY_EPSILON = 0.012f;
    private static final long MAX_PHYSICS_STEP_NS = 50000000;
    private final Paint stripPaint;
    private final Paint seamPaint;
    private final Rect sourceRect;
    private final Rect destinationRect;
    private final Camera camera;
    private final Matrix transform;
    private final Matrix cameraTransform;
    private final ColorMatrix colorMatrix;
    private final float[] springOutput;
    private Bitmap backgroundBitmap;
    private boolean ownsBackgroundBitmap;
    private boolean externalBackground;
    private String backgroundSource;
    private boolean destroyed;
    private boolean firstFrameDrawn;
    private boolean gestureActive;
    private boolean exitRequested;
    private long exitStartedAtNs;
    private long lastFrameAtNs;
    private float touchX;
    private float touchY;
    private float springPosition;
    private float springVelocity;
    private float targetPosition;
    private UnlockEffectReadiness.ReadinessListener readinessListener;
    private final Runnable affordanceRelease;

    public XperiaBlindsEffectView(Context context) {
        super(context);
        this.stripPaint = new Paint(7);
        this.seamPaint = new Paint(1);
        this.sourceRect = new Rect();
        this.destinationRect = new Rect();
        this.camera = new Camera();
        this.transform = new Matrix();
        this.cameraTransform = new Matrix();
        this.colorMatrix = new ColorMatrix();
        this.springOutput = new float[2];
        this.backgroundSource = "none";
        this.affordanceRelease = new Runnable() {
            @Override
            public void run() {
                if (!XperiaBlindsEffectView.this.destroyed) {
                    XperiaBlindsEffectView.this.requestExit();
                }
            }
        };
        setWillNotDraw(false);
        setBackgroundColor(0);
        setLayerType(2, null);
        this.seamPaint.setColor(1426063360);
        this.seamPaint.setStrokeWidth(1.0f);
    }

    public View asView() {
        return this;
    }

    public String effectName() {
        return "Xperia Blinds";
    }

    public boolean supportsHighFrameRate() {
        return true;
    }

    public boolean supportsSpeedMultiplier() {
        return false;
    }

    public void beginGesture(float f, float f2) {
        if (this.destroyed) {
            return;
        }
        removeCallbacks(this.affordanceRelease);
        this.gestureActive = true;
        this.exitRequested = false;
        this.exitStartedAtNs = 0L;
        this.touchX = clamp(f, 0.0f, Math.max(0.0f, getRenderWidth() - 1.0f));
        this.touchY = clamp(f2, 0.0f, Math.max(0.0f, getRenderHeight() - 1.0f));
        this.springPosition = 0.0f;
        this.springVelocity = 0.0f;
        this.targetPosition = 1.0f;
        this.lastFrameAtNs = 0L;
        postInvalidateOnAnimation();
    }

    public void updateGesture(float f, float f2) {
        if (this.destroyed) {
            return;
        }
        if (!this.gestureActive) {
            beginGesture(f, f2);
            return;
        }
        this.touchX = clamp(f, 0.0f, Math.max(0.0f, getRenderWidth() - 1.0f));
        this.touchY = clamp(f2, 0.0f, Math.max(0.0f, getRenderHeight() - 1.0f));
        postInvalidateOnAnimation();
    }

    public void finishGesture(boolean z) {
        if (this.destroyed) {
            return;
        }
        if (!this.gestureActive && !this.exitRequested) {
            return;
        }
        this.gestureActive = false;
        requestExit();
    }

    public void cancelGesture() {
        if (this.destroyed) {
            return;
        }
        clearMotion();
        invalidate();
    }

    public void resetEffect() {
        if (!this.destroyed) {
            removeCallbacks(this.affordanceRelease);
            clearMotion();
            invalidate();
        }
    }

    public void warmUp() {
        if (this.destroyed) {
            return;
        }
        if (this.backgroundBitmap != null && !this.backgroundBitmap.isRecycled()) {
            this.backgroundBitmap.prepareToDraw();
        }
        invalidate();
    }

    public void showUnlockAffordance(Rect rect, long j) {
        if (this.destroyed) {
            return;
        }
        Rect rect2 = rect;
        if (rect2 == null || rect2.width() <= 0 || rect2.height() <= 0) {
            rect2 = new Rect(0, 0, getRenderWidth(), getRenderHeight());
        }
        beginGesture(rect2.exactCenterX(), rect2.exactCenterY());
        removeCallbacks(this.affordanceRelease);
        postDelayed(this.affordanceRelease, Math.max(0L, j) + 130);
    }

    public boolean hasBackgroundSourceBitmap() {
        return this.externalBackground && this.backgroundBitmap != null && !this.backgroundBitmap.isRecycled() && this.backgroundBitmap.getWidth() == getRenderWidth() && this.backgroundBitmap.getHeight() == getRenderHeight();
    }

    public void setBackgroundSourceBitmap(Bitmap bitmap, String str) {
        if (this.destroyed || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        int renderWidth = getRenderWidth();
        int renderHeight = getRenderHeight();
        boolean zCanBorrowSharedCache = BackgroundSourceRenderer.canBorrowSharedCache(bitmap, str, renderWidth, renderHeight);
        Bitmap bitmapCreateCenterCropBitmap = zCanBorrowSharedCache ? bitmap : createCenterCropBitmap(bitmap, renderWidth, renderHeight);
        bitmapCreateCenterCropBitmap.prepareToDraw();
        releaseBackgroundBitmap();
        this.backgroundBitmap = bitmapCreateCenterCropBitmap;
        this.ownsBackgroundBitmap = !zCanBorrowSharedCache;
        this.externalBackground = true;
        this.backgroundSource = str == null ? "external" : str;
        invalidate();
    }

    public void clearBackgroundSourceBitmap() {
        if (this.destroyed) {
            return;
        }
        releaseBackgroundBitmap();
        this.externalBackground = false;
        this.backgroundSource = "none";
        invalidate();
    }

    public boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return bitmap != null && bitmap == this.backgroundBitmap;
    }

    public int getReadinessState() {
        if (this.destroyed) {
            return -1;
        }
        if (!isAttachedToWindow()) {
            return 1;
        }
        if (this.firstFrameDrawn) {
            return AFFECTED_STRIP_COUNT;
        }
        return isLaidOut() ? 4 : 2;
    }

    public String getReadinessDetail() {
        if (this.destroyed) {
            return "Xperia Blinds: renderer destroyed";
        }
        if (!isAttachedToWindow()) {
            return "Xperia Blinds: canvas constructed";
        }
        if (this.firstFrameDrawn) {
            return "Xperia Blinds: app-owned canvas warm frame drawn";
        }
        if (isLaidOut()) {
            return "Xperia Blinds: canvas resources ready";
        }
        return "Xperia Blinds: canvas attached; waiting for layout";
    }

    public void setReadinessListener(UnlockEffectReadiness.ReadinessListener readinessListener) {
        this.readinessListener = readinessListener;
        notifyReadinessChanged();
    }

    public void destroy() {
        if (this.destroyed) {
            return;
        }
        removeCallbacks(this.affordanceRelease);
        clearMotion();
        this.destroyed = true;
        releaseBackgroundBitmap();
        this.readinessListener = null;
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!this.firstFrameDrawn) {
            this.firstFrameDrawn = true;
            notifyReadinessChanged();
        }
        if (this.destroyed || this.backgroundBitmap == null || this.backgroundBitmap.isRecycled()) {
            return;
        }
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || this.backgroundBitmap.getWidth() != width || this.backgroundBitmap.getHeight() != height) {
            return;
        }
        long jElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
        advancePhysics(jElapsedRealtimeNanos);
        float fExitAlpha = exitAlpha(jElapsedRealtimeNanos);
        if (this.springPosition > IDLE_POSITION_EPSILON && fExitAlpha > 0.0f) {
            drawBlinds(canvas, width, height, this.springPosition, fExitAlpha);
        }
        if (needsNextFrame(fExitAlpha)) {
            postInvalidateOnAnimation();
        } else if (this.exitRequested) {
            clearMotion();
        }
    }

    private void drawBlinds(Canvas canvas, int i, int i2, float f, float f2) {
        float fClamp = clamp(this.touchY / Math.max(1.0f, i2), 0.0f, 1.0f);
        float f3 = fClamp - 0.14705883f;
        float f4 = fClamp + 0.14705883f;
        int iClampInt = clampInt((int) Math.floor((f3 * MAX_FOLD_DEGREES) + 0.5f), 0, 16);
        int iClampInt2 = clampInt((int) Math.ceil((f4 * MAX_FOLD_DEGREES) - 0.5f), 0, STRIP_COUNT);
        if (iClampInt2 <= iClampInt) {
            return;
        }
        int i3 = iClampInt + ((iClampInt2 - iClampInt) / 2);
        for (int i4 = i3; i4 >= iClampInt; i4--) {
            drawStrip(canvas, i, i2, i4, fClamp, f, f2);
        }
        for (int i5 = i3 + 1; i5 < iClampInt2; i5++) {
            drawStrip(canvas, i, i2, i5, fClamp, f, f2);
        }
    }

    private void drawStrip(Canvas canvas, int i, int i2, int i3, float f, float f2, float f3) {
        int iRound = Math.round((i3 * i2) / MAX_FOLD_DEGREES);
        int iRound2 = Math.round(((i3 + 1) * i2) / MAX_FOLD_DEGREES);
        if (iRound2 <= iRound) {
            return;
        }
        float f4 = (2.0f * (((i3 + 0.5f) / MAX_FOLD_DEGREES) - f)) / AFFECTED_RANGE;
        if (Math.abs(f4) >= 1.0f) {
            return;
        }
        float fSin = (float) Math.sin(3.141592653589793d * ((double) f4));
        float fCos = (1.0f + ((float) Math.cos(3.141592653589793d * ((double) f4)))) * f2;
        if (fCos <= IDLE_POSITION_EPSILON) {
            return;
        }
        this.sourceRect.set(0, iRound, i, iRound2);
        this.destinationRect.set(0, iRound, i, iRound2);
        float f5 = i * 0.5f;
        float f6 = (iRound + iRound2) * 0.5f;
        this.transform.reset();
        this.transform.postTranslate(-f5, -f6);
        this.camera.save();
        this.camera.translate(0.0f, 0.0f, CAMERA_DEPTH * fCos);
        this.camera.rotateX(MAX_FOLD_DEGREES * fSin * f2);
        this.cameraTransform.reset();
        this.camera.getMatrix(this.cameraTransform);
        this.camera.restore();
        this.transform.postConcat(this.cameraTransform);
        this.transform.postTranslate(f5, f6);
        float fClamp = clamp(fSin * fCos * 0.3f, -0.3f, 0.3f);
        float f7 = fClamp * 96.0f;
        this.colorMatrix.set(new float[]{1.0f + (fClamp * 0.25f), 0.0f, 0.0f, 0.0f, f7, 0.0f, 1.0f + (fClamp * 0.25f), 0.0f, 0.0f, f7, 0.0f, 0.0f, 1.0f + (fClamp * 0.25f), 0.0f, f7, 0.0f, 0.0f, 0.0f, f3, 0.0f});
        this.stripPaint.setColorFilter(new ColorMatrixColorFilter(this.colorMatrix));
        int iSave = canvas.save();
        canvas.concat(this.transform);
        canvas.drawBitmap(this.backgroundBitmap, this.sourceRect, this.destinationRect, this.stripPaint);
        if (fCos > 0.12f) {
            this.seamPaint.setAlpha(Math.round(70.0f * fCos * f3));
            canvas.drawLine(0.0f, iRound, i, iRound, this.seamPaint);
            canvas.drawLine(0.0f, iRound2, i, iRound2, this.seamPaint);
            this.seamPaint.setAlpha(255);
        }
        canvas.restoreToCount(iSave);
        this.stripPaint.setColorFilter(null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void requestExit() {
        if (this.exitRequested) {
            return;
        }
        this.gestureActive = false;
        this.exitRequested = true;
        this.targetPosition = 0.0f;
        this.exitStartedAtNs = SystemClock.elapsedRealtimeNanos();
        this.lastFrameAtNs = 0L;
        postInvalidateOnAnimation();
    }

    @Override // android.view.View
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.firstFrameDrawn = false;
        notifyReadinessChanged();
        warmUp();
    }

    @Override // android.view.View
    protected void onDetachedFromWindow() {
        this.firstFrameDrawn = false;
        notifyReadinessChanged();
        super.onDetachedFromWindow();
    }

    @Override // android.view.View
    protected void onSizeChanged(int i, int i2, int i3, int i4) {
        super.onSizeChanged(i, i2, i3, i4);
        this.firstFrameDrawn = false;
        notifyReadinessChanged();
    }

    private void advancePhysics(long j) {
        if (this.lastFrameAtNs == 0) {
            this.lastFrameAtNs = j;
            return;
        }
        long jMax = Math.max(0L, Math.min(MAX_PHYSICS_STEP_NS, j - this.lastFrameAtNs));
        this.lastFrameAtNs = j;
        if (jMax == 0) {
            return;
        }
        advanceSpring(jMax / 1.0E9f);
    }

    private float exitAlpha(long j) {
        if (!this.exitRequested) {
            return 1.0f;
        }
        return 1.0f - clamp(Math.max(0.0f, (j - this.exitStartedAtNs) / 1000000.0f) / EXIT_DURATION_MS, 0.0f, 1.0f);
    }

    private boolean needsNextFrame(float f) {
        if (this.gestureActive) {
            return true;
        }
        if (this.exitRequested) {
            return f > 0.0f || this.springPosition > IDLE_POSITION_EPSILON || Math.abs(this.springVelocity) > IDLE_VELOCITY_EPSILON;
        }
        return false;
    }

    private void clearMotion() {
        this.gestureActive = false;
        this.exitRequested = false;
        this.exitStartedAtNs = 0L;
        this.lastFrameAtNs = 0L;
        this.springPosition = 0.0f;
        this.springVelocity = 0.0f;
        this.targetPosition = 0.0f;
    }

    static float[] springStep(float f, float f2, float f3, float f4) {
        float[] fArr = new float[2];
        springStepInto(f, f2, f3, f4, fArr);
        return fArr;
    }

    private static void springStepInto(float f, float f2, float f3, float f4, float[] fArr) {
        float fClamp = clamp(f4, 0.0f, 0.05f);
        float fSqrt = (float) Math.sqrt(400.0d);
        float fSqrt2 = fSqrt * ((float) Math.sqrt(0.2774999737739563d));
        float f5 = f - f3;
        float fExp = (float) Math.exp((-0.85f) * fSqrt * fClamp);
        float fCos = (float) Math.cos(fSqrt2 * fClamp);
        float fSin = (float) Math.sin(fSqrt2 * fClamp);
        float f6 = (f2 + ((SPRING_DAMPING_RATIO * fSqrt) * f5)) / fSqrt2;
        float f7 = (f5 * fCos) + (f6 * fSin);
        fArr[0] = f3 + (fExp * f7);
        fArr[1] = fExp * (((-0.85f) * fSqrt * f7) + ((-f5) * fSqrt2 * fSin) + (f6 * fSqrt2 * fCos));
    }

    private void advanceSpring(float f) {
        springStepInto(this.springPosition, this.springVelocity, this.targetPosition, f, this.springOutput);
        this.springPosition = Math.max(0.0f, this.springOutput[0]);
        this.springVelocity = this.springOutput[1];
    }

    private Bitmap createCenterCropBitmap(Bitmap bitmap, int i, int i2) {
        Rect rect;
        if (bitmap.getWidth() == i && bitmap.getHeight() == i2) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i2, Bitmap.Config.ARGB_8888);
        float f = i / i2;
        if (bitmap.getWidth() / bitmap.getHeight() > f) {
            int iMax = Math.max(1, Math.round(bitmap.getHeight() * f));
            int iMax2 = Math.max(0, (bitmap.getWidth() - iMax) / 2);
            rect = new Rect(iMax2, 0, Math.min(bitmap.getWidth(), iMax2 + iMax), bitmap.getHeight());
        } else {
            int iMax3 = Math.max(1, Math.round(bitmap.getWidth() / f));
            int iMax4 = Math.max(0, (bitmap.getHeight() - iMax3) / 2);
            rect = new Rect(0, iMax4, bitmap.getWidth(), Math.min(bitmap.getHeight(), iMax4 + iMax3));
        }
        new Canvas(bitmapCreateBitmap).drawBitmap(bitmap, rect, new Rect(0, 0, i, i2), new Paint(7));
        return bitmapCreateBitmap;
    }

    private void releaseBackgroundBitmap() {
        if (this.ownsBackgroundBitmap && this.backgroundBitmap != null && !this.backgroundBitmap.isRecycled()) {
            this.backgroundBitmap.recycle();
        }
        this.backgroundBitmap = null;
        this.ownsBackgroundBitmap = false;
    }

    private int getRenderWidth() {
        return getWidth() > 0 ? getWidth() : Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int getRenderHeight() {
        return getHeight() > 0 ? getHeight() : Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    private void notifyReadinessChanged() {
        UnlockEffectReadiness.ReadinessListener readinessListener = this.readinessListener;
        if (readinessListener != null) {
            try {
                readinessListener.onReadinessChanged();
            } catch (RuntimeException e) {
            }
        }
    }

    private static float clamp(float f, float f2, float f3) {
        return Math.max(f2, Math.min(f3, f));
    }

    private static int clampInt(int i, int i2, int i3) {
        return Math.max(i2, Math.min(i3, i));
    }
}
