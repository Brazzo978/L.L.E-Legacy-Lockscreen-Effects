package com.codex.s4unlockfx;

import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.io.InputStream;

public class UnlockWallpaperService extends WallpaperService {
    private static final String TAG = "UnlockWallpaper";
    private static final String COMMAND_KEYGUARD_GOING_AWAY = "android.wallpaper.keyguardgoingaway";
    private static int nextEngineId = 1;

    @Override
    public Engine onCreateEngine() {
        return new UnlockEngine();
    }

    private final class UnlockEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final RectF dst = new RectF();
        private final Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                drawFrame();
            }
        };

        private final int engineId = nextEngineId++;
        private KeyguardManager keyguardManager;
        private LegacyCanvasEffectView effectView;
        private Bitmap wallpaper;
        private Bitmap scaledWallpaper;
        private boolean visible;
        private int width = 1;
        private int height = 1;
        private int lastModeIndex = Integer.MIN_VALUE;
        private String lastWallpaperResourceName;
        private long animateUntil;
        private long lastMoveLogAt;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);
            keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            effectView = new LegacyCanvasEffectView(UnlockWallpaperService.this);
            updateSelectedEffect();
            updateSelectedWallpaper();
            Log.i(TAG, prefix() + "created");
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunnable);
            if (wallpaper != null && !wallpaper.isRecycled()) {
                wallpaper.recycle();
            }
            recycleScaledWallpaper();
            Log.i(TAG, prefix() + "destroyed");
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean nextVisible) {
            visible = nextVisible;
            Log.i(TAG, prefix() + "visibility=" + visible + " lock=" + isLockscreen());
            if (visible) {
                drawFrame();
            } else {
                handler.removeCallbacks(drawRunnable);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int nextWidth, int nextHeight) {
            super.onSurfaceChanged(holder, format, nextWidth, nextHeight);
            width = Math.max(1, nextWidth);
            height = Math.max(1, nextHeight);
            Log.i(TAG, prefix() + "surfaceChanged " + width + "x" + height + " lock=" + isLockscreen());
            rebuildScaledWallpaper();
            effectView.measure(
                    ViewMeasureSpec.exactly(width),
                    ViewMeasureSpec.exactly(height));
            effectView.layout(0, 0, width, height);
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(drawRunnable);
            recycleScaledWallpaper();
            Log.i(TAG, prefix() + "surfaceDestroyed lock=" + isLockscreen());
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            boolean locked = isLockscreen();
            logTouchForDebug(event, locked);
            updateSelectedEffect();
            updateSelectedWallpaper();
            if (effectView.onHostTouchEvent(event) && effectView.hasActiveAnimations()) {
                startAnimationLoop();
            } else {
                animateUntil = 0L;
            }
            drawFrame();
            super.onTouchEvent(event);
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            Log.d(TAG, prefix() + "command action=" + action + " x=" + x + " y=" + y + " z=" + z + " lock=" + isLockscreen());
            if (WallpaperManager.COMMAND_TAP.equals(action)
                    || WallpaperManager.COMMAND_SECONDARY_TAP.equals(action)
                    || WallpaperManager.COMMAND_DROP.equals(action)
                    || COMMAND_KEYGUARD_GOING_AWAY.equals(action)) {
                updateSelectedEffect();
                updateSelectedWallpaper();
                runSyntheticTap(x, y);
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        private String prefix() {
            return "engine#" + engineId + " ";
        }

        private void runSyntheticTap(int x, int y) {
            float fx = x > 0 ? x : width * 0.5f;
            float fy = y > 0 ? y : height * 0.5f;
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, fx, fy, 0);
            MotionEvent up = MotionEvent.obtain(now, now + 24L, MotionEvent.ACTION_UP, fx, fy, 0);
            try {
                effectView.onHostTouchEvent(down);
                effectView.onHostTouchEvent(up);
                if (effectView.hasActiveAnimations()) {
                    startAnimationLoop();
                }
                drawFrame();
            } finally {
                down.recycle();
                up.recycle();
            }
        }

        private void startAnimationLoop() {
            animateUntil = SystemClock.uptimeMillis() + 2100L;
        }

        private void drawFrame() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) {
                    return;
                }
                drawStaticWallpaper(canvas);
                if (isLockscreen()) {
                    drawLockscreenShade(canvas);
                }
                effectView.draw(canvas);
            } catch (Throwable t) {
                Log.e(TAG, "draw failed", t);
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }

            handler.removeCallbacks(drawRunnable);
            if (visible && SystemClock.uptimeMillis() < animateUntil && effectView.hasActiveAnimations()) {
                handler.postDelayed(drawRunnable, 33L);
            }
        }

        private void drawStaticWallpaper(Canvas canvas) {
            canvas.drawColor(Color.rgb(8, 12, 18));
            if (scaledWallpaper != null && !scaledWallpaper.isRecycled()) {
                canvas.drawBitmap(scaledWallpaper, 0f, 0f, paint);
                return;
            }
            if (wallpaper == null || wallpaper.isRecycled()) {
                return;
            }
            float scale = Math.max(width / (float) wallpaper.getWidth(), height / (float) wallpaper.getHeight());
            float drawWidth = wallpaper.getWidth() * scale;
            float drawHeight = wallpaper.getHeight() * scale;
            float left = (width - drawWidth) * 0.5f;
            float top = (height - drawHeight) * 0.5f;
            dst.set(left, top, left + drawWidth, top + drawHeight);
            canvas.drawBitmap(wallpaper, null, dst, paint);
        }

        private void rebuildScaledWallpaper() {
            recycleScaledWallpaper();
            if (wallpaper == null || wallpaper.isRecycled() || width <= 1 || height <= 1) {
                return;
            }
            Bitmap target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(target);
            float scale = Math.max(width / (float) wallpaper.getWidth(), height / (float) wallpaper.getHeight());
            float drawWidth = wallpaper.getWidth() * scale;
            float drawHeight = wallpaper.getHeight() * scale;
            float left = (width - drawWidth) * 0.5f;
            float top = (height - drawHeight) * 0.5f;
            dst.set(left, top, left + drawWidth, top + drawHeight);
            canvas.drawColor(Color.rgb(8, 12, 18));
            canvas.drawBitmap(wallpaper, null, dst, paint);
            scaledWallpaper = target;
            Log.i(TAG, prefix() + "cached wallpaper " + width + "x" + height);
        }

        private void recycleScaledWallpaper() {
            if (scaledWallpaper != null && !scaledWallpaper.isRecycled()) {
                scaledWallpaper.recycle();
            }
            scaledWallpaper = null;
        }

        private void drawLockscreenShade(Canvas canvas) {
            paint.setShader(new LinearGradient(
                    0f,
                    0f,
                    0f,
                    height,
                    new int[] {
                            Color.argb(28, 0, 0, 0),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(34, 0, 0, 0)
                    },
                    new float[] { 0f, 0.46f, 1f },
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, width, height, paint);
            paint.setShader(null);
        }

        private boolean isLockscreen() {
            return keyguardManager != null && keyguardManager.isKeyguardLocked();
        }

        private void updateSelectedEffect() {
            SharedPreferences prefs = getSharedPreferences(UnlockFxPrefs.NAME, MODE_PRIVATE);
            int modeIndex = prefs.getInt(UnlockFxPrefs.MODE_INDEX, 1);
            if (modeIndex == lastModeIndex) {
                return;
            }
            lastModeIndex = modeIndex;
            int effectType = UnlockFxPrefs.canvasEffectForModeIndex(modeIndex);
            effectView.setEffectType(effectType);
            Log.i(TAG, prefix() + "selected mode=" + modeIndex + " canvasEffect=" + effectType);
        }

        private void updateSelectedWallpaper() {
            SharedPreferences prefs = getSharedPreferences(UnlockFxPrefs.NAME, MODE_PRIVATE);
            int modeIndex = prefs.getInt(UnlockFxPrefs.MODE_INDEX, 1);
            int wallpaperMode = prefs.getInt(UnlockFxPrefs.WALLPAPER_MODE, UnlockFxPrefs.WALLPAPER_MODE_AUTO);
            int stockIndex = prefs.getInt(UnlockFxPrefs.STOCK_WALLPAPER_INDEX, 0);
            String customUri = prefs.getString(UnlockFxPrefs.CUSTOM_WALLPAPER_URI, null);
            String resourceName = UnlockFxPrefs.defaultWallpaperResourceNameForModeIndex(modeIndex);
            String wallpaperKey = "res:" + resourceName;
            if (wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_STOCK) {
                resourceName = UnlockFxPrefs.stockWallpaperResourceName(stockIndex);
                wallpaperKey = "stock:" + stockIndex + ":" + resourceName;
            } else if (wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_CUSTOM && customUri != null && customUri.length() > 0) {
                wallpaperKey = "uri:" + customUri;
            }
            if (wallpaperKey.equals(lastWallpaperResourceName)
                    && wallpaper != null
                    && !wallpaper.isRecycled()) {
                return;
            }
            Bitmap nextWallpaper = null;
            if (wallpaperKey.startsWith("uri:")) {
                nextWallpaper = loadWallpaperBitmapFromUri(customUri);
                if (nextWallpaper == null) {
                    wallpaperKey = "res:" + resourceName;
                }
            }
            if (nextWallpaper == null) {
                nextWallpaper = loadWallpaperBitmap(resourceName);
            }
            if (wallpaper != null && !wallpaper.isRecycled()) {
                wallpaper.recycle();
            }
            recycleScaledWallpaper();
            wallpaper = nextWallpaper;
            lastWallpaperResourceName = wallpaperKey;
            rebuildScaledWallpaper();
            Log.i(TAG, prefix() + "selected wallpaper=" + wallpaperKey);
        }

        private void logTouchForDebug(MotionEvent event, boolean locked) {
            int action = event.getActionMasked();
            long now = SystemClock.uptimeMillis();
            if (action == MotionEvent.ACTION_MOVE && now - lastMoveLogAt < 500L) {
                return;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                lastMoveLogAt = now;
            }
            Log.d(TAG, prefix() + "touch action=" + action
                    + " lock=" + locked
                    + " debugHome=true"
                    + " canvasEffect=" + effectView.getEffectType());
        }

        private Bitmap loadWallpaperBitmap(String resourceName) {
            int resId = getResources().getIdentifier(resourceName, "drawable", getPackageName());
            if (resId == 0 && !"keyguard_default_wallpaper".equals(resourceName)) {
                resId = getResources().getIdentifier("keyguard_default_wallpaper", "drawable", getPackageName());
            }
            Bitmap bitmap = resId != 0 ? BitmapFactory.decodeResource(getResources(), resId) : null;
            if (bitmap != null) {
                return bitmap;
            }
            Bitmap fallback = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            fallback.eraseColor(Color.rgb(20, 34, 48));
            return fallback;
        }

        private Bitmap loadWallpaperBitmapFromUri(String uriString) {
            InputStream stream = null;
            try {
                stream = getContentResolver().openInputStream(Uri.parse(uriString));
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (bitmap != null) {
                    return bitmap;
                }
            } catch (Throwable t) {
                Log.w(TAG, prefix() + "custom wallpaper unavailable: " + uriString, t);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            return null;
        }
    }

    private static final class ViewMeasureSpec {
        static int exactly(int size) {
            return android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY);
        }
    }
}
