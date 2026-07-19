package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Full-screen, target-aspect wallpaper crop surface with pinch zoom and constrained pan. */
public final class WallpaperCropView extends View {
    interface OnTransformChangedListener {
        void onTransformChanged(int zoomPercent);
    }

    private static final float MAX_ZOOM = 8f;

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cropRect = new RectF();
    private final RectF imageRect = new RectF();
    private final ScaleGestureDetector scaleDetector;

    private Bitmap bitmap;
    private int targetWidth = 1;
    private int targetHeight = 1;
    private float baseScale = 1f;
    private float zoom = 1f;
    private float offsetX;
    private float offsetY;
    private float lastX;
    private float lastY;
    private boolean transformNeedsReset = true;
    private boolean restorePending;
    private float restoreZoom = 1f;
    private float restoreOffsetX;
    private float restoreOffsetY;
    private OnTransformChangedListener transformListener;

    public WallpaperCropView(Context context) {
        this(context, null);
    }

    public WallpaperCropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.rgb(9, 12, 17));
        setWillNotDraw(false);
        setClickable(true);
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        return bitmap != null && !bitmap.isRecycled();
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float oldZoom = zoom;
                        float newZoom = clamp(oldZoom * detector.getScaleFactor(), 1f, MAX_ZOOM);
                        if (Math.abs(newZoom - oldZoom) < 0.0001f) {
                            return true;
                        }
                        float ratio = newZoom / oldZoom;
                        float oldCenterX = cropRect.centerX() + offsetX;
                        float oldCenterY = cropRect.centerY() + offsetY;
                        float newCenterX = detector.getFocusX()
                                + (oldCenterX - detector.getFocusX()) * ratio;
                        float newCenterY = detector.getFocusY()
                                + (oldCenterY - detector.getFocusY()) * ratio;
                        zoom = newZoom;
                        offsetX = newCenterX - cropRect.centerX();
                        offsetY = newCenterY - cropRect.centerY();
                        constrainTransform();
                        notifyTransformChanged();
                        invalidate();
                        return true;
                    }
                });
    }

    void setTargetSize(int width, int height) {
        targetWidth = Math.max(1, width);
        targetHeight = Math.max(1, height);
        transformNeedsReset = true;
        requestLayout();
        invalidate();
    }

    void setBitmap(Bitmap value) {
        bitmap = value;
        transformNeedsReset = true;
        updateGeometry(true);
        invalidate();
    }

    void setOnTransformChangedListener(OnTransformChangedListener listener) {
        transformListener = listener;
        notifyTransformChanged();
    }

    boolean isReady() {
        return bitmap != null && !bitmap.isRecycled()
                && getWidth() > 0 && getHeight() > 0 && !cropRect.isEmpty();
    }

    float currentZoom() {
        return zoom;
    }

    float normalizedOffsetX() {
        float max = horizontalOffsetLimit();
        return max <= 0f ? 0f : clamp(offsetX / max, -1f, 1f);
    }

    float normalizedOffsetY() {
        float max = verticalOffsetLimit();
        return max <= 0f ? 0f : clamp(offsetY / max, -1f, 1f);
    }

    void restoreTransform(float savedZoom, float normalizedX, float normalizedY) {
        restoreZoom = clamp(savedZoom, 1f, MAX_ZOOM);
        restoreOffsetX = clamp(normalizedX, -1f, 1f);
        restoreOffsetY = clamp(normalizedY, -1f, 1f);
        restorePending = true;
        updateGeometry(false);
        invalidate();
    }

    /** Renders exactly the framed crop at the requested physical display dimensions. */
    Bitmap renderCrop() {
        if (!isReady()) {
            return null;
        }
        updateGeometry(false);
        Bitmap output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.BLACK);
        float outputX = targetWidth / cropRect.width();
        float outputY = targetHeight / cropRect.height();
        RectF destination = new RectF(
                (imageRect.left - cropRect.left) * outputX,
                (imageRect.top - cropRect.top) * outputY,
                (imageRect.right - cropRect.left) * outputX,
                (imageRect.bottom - cropRect.top) * outputY);
        canvas.drawBitmap(bitmap, null, destination, imagePaint);
        output.prepareToDraw();
        return output;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        transformNeedsReset = true;
        updateGeometry(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateGeometry(false);
        canvas.drawColor(Color.rgb(9, 12, 17));
        if (bitmap == null || bitmap.isRecycled() || cropRect.isEmpty()) {
            return;
        }
        canvas.drawBitmap(bitmap, null, imageRect, imagePaint);
        drawOutsideShade(canvas);
        drawCropGuides(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null || !isEnabled()) {
            return true;
        }
        scaleDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1
                && !scaleDetector.isInProgress() && isReady()) {
            float x = event.getX();
            float y = event.getY();
            offsetX += x - lastX;
            offsetY += y - lastY;
            lastX = x;
            lastY = y;
            constrainTransform();
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerCount() > 1) {
            int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
            lastX = event.getX(remainingIndex);
            lastY = event.getY(remainingIndex);
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateGeometry(boolean forceReset) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float margin = dp(12);
        float availableWidth = Math.max(1f, getWidth() - margin * 2f);
        float availableHeight = Math.max(1f, getHeight() - margin * 2f);
        float targetRatio = targetWidth / (float) targetHeight;
        float cropWidth;
        float cropHeight;
        if (availableWidth / availableHeight > targetRatio) {
            cropHeight = availableHeight;
            cropWidth = cropHeight * targetRatio;
        } else {
            cropWidth = availableWidth;
            cropHeight = cropWidth / targetRatio;
        }
        float left = (getWidth() - cropWidth) * 0.5f;
        float top = (getHeight() - cropHeight) * 0.5f;
        cropRect.set(left, top, left + cropWidth, top + cropHeight);
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        baseScale = Math.max(cropRect.width() / bitmap.getWidth(),
                cropRect.height() / bitmap.getHeight());
        if (restorePending) {
            zoom = restoreZoom;
            offsetX = restoreOffsetX * horizontalOffsetLimit();
            offsetY = restoreOffsetY * verticalOffsetLimit();
            restorePending = false;
            transformNeedsReset = false;
            notifyTransformChanged();
        } else if (forceReset || transformNeedsReset) {
            zoom = 1f;
            offsetX = 0f;
            offsetY = 0f;
            transformNeedsReset = false;
            notifyTransformChanged();
        }
        constrainTransform();
        updateImageRect();
    }

    private void updateImageRect() {
        float scale = baseScale * zoom;
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float centerX = cropRect.centerX() + offsetX;
        float centerY = cropRect.centerY() + offsetY;
        imageRect.set(centerX - width * 0.5f, centerY - height * 0.5f,
                centerX + width * 0.5f, centerY + height * 0.5f);
    }

    private void constrainTransform() {
        if (bitmap == null || bitmap.isRecycled() || cropRect.isEmpty()) {
            return;
        }
        float maxX = horizontalOffsetLimit();
        float maxY = verticalOffsetLimit();
        offsetX = clamp(offsetX, -maxX, maxX);
        offsetY = clamp(offsetY, -maxY, maxY);
        updateImageRect();
    }

    private float horizontalOffsetLimit() {
        if (bitmap == null || bitmap.isRecycled() || cropRect.isEmpty()) {
            return 0f;
        }
        float scaledWidth = bitmap.getWidth() * baseScale * zoom;
        return Math.max(0f, (scaledWidth - cropRect.width()) * 0.5f);
    }

    private float verticalOffsetLimit() {
        if (bitmap == null || bitmap.isRecycled() || cropRect.isEmpty()) {
            return 0f;
        }
        float scaledHeight = bitmap.getHeight() * baseScale * zoom;
        return Math.max(0f, (scaledHeight - cropRect.height()) * 0.5f);
    }

    private void drawOutsideShade(Canvas canvas) {
        overlayPaint.setStyle(Paint.Style.FILL);
        overlayPaint.setColor(Color.argb(178, 0, 0, 0));
        canvas.drawRect(0f, 0f, getWidth(), cropRect.top, overlayPaint);
        canvas.drawRect(0f, cropRect.bottom, getWidth(), getHeight(), overlayPaint);
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, overlayPaint);
    }

    private void drawCropGuides(Canvas canvas) {
        overlayPaint.setStyle(Paint.Style.STROKE);
        overlayPaint.setStrokeWidth(dp(1.5f));
        overlayPaint.setColor(Color.argb(235, 255, 255, 255));
        canvas.drawRect(cropRect, overlayPaint);
        overlayPaint.setStrokeWidth(dp(1f));
        overlayPaint.setColor(Color.argb(95, 255, 255, 255));
        float thirdX = cropRect.width() / 3f;
        float thirdY = cropRect.height() / 3f;
        canvas.drawLine(cropRect.left + thirdX, cropRect.top,
                cropRect.left + thirdX, cropRect.bottom, overlayPaint);
        canvas.drawLine(cropRect.left + thirdX * 2f, cropRect.top,
                cropRect.left + thirdX * 2f, cropRect.bottom, overlayPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdY,
                cropRect.right, cropRect.top + thirdY, overlayPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdY * 2f,
                cropRect.right, cropRect.top + thirdY * 2f, overlayPaint);
    }

    private void notifyTransformChanged() {
        if (transformListener != null) {
            transformListener.onTransformChanged(Math.round(zoom * 100f));
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
