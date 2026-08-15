package com.codex.lle;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Note5NativeProbeActivity extends Activity {
    private static final String TAG = "LLE64NativeProbe";
    private static final String DROPLET =
            "com.samsung.android.visualeffect.lock.colourdroplet.JniColourDropletRenderer";
    private static final String BUBBLES =
            "com.samsung.android.visualeffect.lock.sparklingbubbles.JniSparklingBubblesRenderer";
    private static final String WATER_RIPPLE =
            "com.android.internal.policy.impl.keyguard.sec.JniWaterRippleRender";
    private static final String SCENARIO_FAST_DRAG = "fast-drag";
    private static final String SCENARIO_EDGE_CLOSURE = "edge-closure";
    private static final String SCENARIO_RELEASE = "release";
    private static final String SCENARIO_UNLOCK_FULLSCREEN = "unlock-fullscreen";
    private static final String SCENARIO_REFRACTION_CHART = "refraction-chart";
    private static final int SETTLE_FRAMES = 60;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private UnlockEffectRenderer activeRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String requested = getIntent().getStringExtra("effect");
        if ("colour-arm64-render".equals(requested)
                || "colour-arm64-gyro-render".equals(requested)
                || "colour-wip-render".equals(requested)
                || "colour-wip-gyro-render".equals(requested)) {
            boolean appOwned = "colour-wip-render".equals(requested)
                    || "colour-wip-gyro-render".equals(requested);
            boolean gyroEnabled = "colour-arm64-gyro-render".equals(requested)
                    || "colour-wip-gyro-render".equals(requested);
            probeColourRenderer(appOwned, gyroEnabled);
            return;
        }
        if ("bubbles-arm64-render".equals(requested)
                || "bubbles-wip-render".equals(requested)) {
            probeSparklingRenderer("bubbles-wip-render".equals(requested));
            return;
        }
        if ("droplet-render".equals(requested) || "bubbles-render".equals(requested)) {
            probeRenderer("droplet-render".equals(requested));
            return;
        }
        StringBuilder result = new StringBuilder("LLE64 native JNI probe\n");
        if ("ripple-core".equals(requested)) {
            probeRippleCore(result);
        }
        if (requested == null || "droplet".equals(requested) || "all".equals(requested)) {
            probeClass(DROPLET, result);
        }
        if (requested == null || "bubbles".equals(requested) || "all".equals(requested)) {
            probeClass(BUBBLES, result);
        }
        TextView view = new TextView(this);
        view.setText(result.toString());
        view.setTextColor(Color.BLACK);
        view.setTextSize(16f);
        view.setPadding(32, 64, 32, 32);
        setContentView(view);
    }

    private void probeColourRenderer(final boolean appOwned, final boolean gyroEnabled) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        final String name = appOwned
                ? (gyroEnabled
                        ? "Colored Droplet app-owned + Gyro"
                        : "Colored Droplet app-owned")
                : (gyroEnabled
                        ? "Colored Droplet Samsung oracle + Gyro"
                        : "Colored Droplet Samsung oracle");
        final FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(Color.BLACK);
        setContentView(host);

        int effect = gyroEnabled
                ? OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                : OverlayPrefs.EFFECT_N5_COLOUR_DROPLET;
        String scenario = getIntent().getStringExtra("scenario");
        Bitmap background;
        if (SCENARIO_REFRACTION_CHART.equals(scenario)) {
            Point realSize = new Point();
            getWindowManager().getDefaultDisplay().getRealSize(realSize);
            background = createRefractionChart(
                    Math.max(1, realSize.x),
                    Math.max(1, realSize.y));
            Log.i(TAG, "AB_BACKGROUND scenario=" + scenario
                    + " mode=refraction-chart size=" + background.getWidth()
                    + "x" + background.getHeight()
                    + " rawPixelSha256=" + bitmapPixelSha256(background));
        } else {
            background = Argb8888BitmapStore.decode(
                    OverlayPrefs.effectBackgroundFile(this, effect));
            Log.i(TAG, "AB_BACKGROUND scenario=" + scenario
                    + " mode=frozen size=" + (background == null ? "missing"
                    : background.getWidth() + "x" + background.getHeight())
                    + " rawPixelSha256=" + bitmapPixelSha256(background));
        }
        if (background != null) {
            ImageView backgroundView = new ImageView(this);
            backgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            backgroundView.setImageBitmap(background);
            host.addView(backgroundView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        try {
            activeRenderer = appOwned
                    ? new ColourDropletAppOwnedEffectView(this, gyroEnabled)
                    : new ColourDropletArm64EffectView(this, gyroEnabled);
            View effectView = activeRenderer.asView();
            host.addView(effectView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            if (background != null && activeRenderer instanceof BackgroundSourceRenderer) {
                ((BackgroundSourceRenderer) activeRenderer).setBackgroundSourceBitmap(
                        background, "colour_probe");
            }
            Log.i(TAG, "PASS " + name + " constructed background="
                    + (background == null ? "missing" :
                    background.getWidth() + "x" + background.getHeight()));
        } catch (Throwable failure) {
            Log.e(TAG, "FAIL " + name + " renderer construction", failure);
            showFailure(host, name, failure);
            return;
        }

        scheduleColourScenarioWhenReady(name, host, activeRenderer);
    }

    private void scheduleColourScenarioWhenReady(final String name,
            final FrameLayout host, final UnlockEffectRenderer renderer) {
        final String scenario = getIntent().getStringExtra("scenario");
        if (!(renderer instanceof UnlockEffectReadiness)) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isFinalAbScenario(scenario)) {
                        startFinalAbScenarioAfterSettle(name, host, renderer, scenario);
                    } else {
                        startColourScenario(name, host, renderer);
                    }
                }
            }, 250L);
            return;
        }
        final UnlockEffectReadiness readiness = (UnlockEffectReadiness) renderer;
        final boolean[] scheduled = new boolean[] { false };
        readiness.setReadinessListener(new UnlockEffectReadiness.ReadinessListener() {
            @Override
            public void onReadinessChanged() {
                if (scheduled[0] || activeRenderer != renderer) {
                    return;
                }
                int state = readiness.getReadinessState();
                Log.i(TAG, name + " readiness=" + state
                        + " detail=" + readiness.getReadinessDetail());
                if (state == UnlockEffectReadiness.STATE_FAILED) {
                    scheduled[0] = true;
                    readiness.setReadinessListener(null);
                    showFailure(host, name, new IllegalStateException(
                            readiness.getReadinessDetail()));
                    return;
                }
                if (state < UnlockEffectReadiness.STATE_FIRST_FRAME_READY) {
                    return;
                }
                scheduled[0] = true;
                readiness.setReadinessListener(null);
                if (isFinalAbScenario(scenario)) {
                    startFinalAbScenarioAfterSettle(name, host, renderer, scenario);
                } else {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startColourScenario(name, host, renderer);
                        }
                    }, 250L);
                }
            }
        });
    }

    private static boolean isFinalAbScenario(String scenario) {
        return SCENARIO_FAST_DRAG.equals(scenario)
                || SCENARIO_EDGE_CLOSURE.equals(scenario)
                || SCENARIO_RELEASE.equals(scenario)
                || SCENARIO_UNLOCK_FULLSCREEN.equals(scenario)
                || SCENARIO_REFRACTION_CHART.equals(scenario);
    }

    private void startFinalAbScenarioAfterSettle(final String name,
            final FrameLayout host, final UnlockEffectRenderer renderer,
            final String scenario) {
        if (activeRenderer != renderer) {
            return;
        }
        renderer.warmUp();
        final String requestedRunId = getIntent().getStringExtra("runId");
        final String runId = requestedRunId == null
                ? name.replace(' ', '_') + "-" + scenario
                : requestedRunId;
        final List<AbEvent> events = buildFinalAbEvents(scenario);
        final long endNs = finalAbDurationMs(scenario) * 1_000_000L;
        Log.i(TAG, "AB_READY runId=" + runId + " scenario=" + scenario
                + " renderer=" + name + " settleFrames=" + SETTLE_FRAMES
                + " host=" + host.getWidth() + "x" + host.getHeight()
                + " eventCount=" + events.size());

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            private int settleFrames;
            private int nextEvent;
            private long epochNs = -1L;

            @Override
            public void doFrame(long frameTimeNanos) {
                if (activeRenderer != renderer) {
                    return;
                }
                if (settleFrames < SETTLE_FRAMES) {
                    settleFrames++;
                    Choreographer.getInstance().postFrameCallback(this);
                    return;
                }
                if (epochNs < 0L) {
                    epochNs = frameTimeNanos;
                    Log.i(TAG, "AB_EPOCH runId=" + runId + " scenario=" + scenario
                            + " frameTimeNs=" + epochNs + " width=" + host.getWidth()
                            + " height=" + host.getHeight());
                }
                long elapsedNs = Math.max(0L, frameTimeNanos - epochNs);
                while (nextEvent < events.size()
                        && events.get(nextEvent).targetNs <= elapsedNs) {
                    dispatchFinalAbEvent(runId, scenario, host, renderer,
                            events.get(nextEvent), epochNs);
                    nextEvent++;
                }
                if (nextEvent >= events.size() && elapsedNs >= endNs) {
                    long actualNs = System.nanoTime();
                    Log.i(TAG, "AB_DONE runId=" + runId + " scenario=" + scenario
                            + " intendedEndNs=" + (epochNs + endNs)
                            + " actualNs=" + actualNs
                            + " elapsedMs=" + ((actualNs - epochNs) / 1_000_000.0));
                    destroyRenderer(name);
                    finish();
                    return;
                }
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }

    private void dispatchFinalAbEvent(String runId, String scenario,
            FrameLayout host, UnlockEffectRenderer renderer, AbEvent event,
            long epochNs) {
        float width = Math.max(1, host.getWidth());
        float height = Math.max(1, host.getHeight());
        float x = width * event.xNorm;
        float y = height * event.yNorm;
        long actualNs = System.nanoTime();
        if (AbEvent.DOWN.equals(event.kind)) {
            renderer.beginGesture(x, y);
        } else if (AbEvent.MOVE.equals(event.kind)) {
            renderer.updateGesture(x, y);
        } else if (AbEvent.UP_UNLOCK.equals(event.kind)) {
            renderer.finishGesture(true);
        } else {
            renderer.finishGesture(false);
        }
        Log.i(TAG, String.format(Locale.US,
                "AB_EVENT runId=%s scenario=%s kind=%s index=%d"
                        + " intendedNs=%d actualNs=%d latenessMs=%.3f"
                        + " x=%.3f y=%.3f xNorm=%.6f yNorm=%.6f",
                runId, scenario, event.kind, event.index,
                epochNs + event.targetNs, actualNs,
                (actualNs - epochNs - event.targetNs) / 1_000_000.0,
                x, y, event.xNorm, event.yNorm));
    }

    private static List<AbEvent> buildFinalAbEvents(String scenario) {
        ArrayList<AbEvent> events = new ArrayList<>();
        if (SCENARIO_FAST_DRAG.equals(scenario)) {
            addLinearDrag(events, 0.20f, 0.78f, 0.80f, 0.22f,
                    36, 300L, false);
        } else if (SCENARIO_EDGE_CLOSURE.equals(scenario)) {
            events.add(new AbEvent(AbEvent.DOWN, 0, 0L, 0.18f, 0.56f));
            addLinearMoves(events, 0.18f, 0.56f, 0.02f, 0.56f,
                    1, 30, 0L, 400L);
            addLinearMoves(events, 0.02f, 0.56f, 0.02f, 0.28f,
                    31, 24, 700L, 1100L);
            events.add(new AbEvent(AbEvent.UP_RELEASE, 55, 1100L,
                    0.02f, 0.28f));
        } else if (SCENARIO_RELEASE.equals(scenario)) {
            addLinearDrag(events, 0.30f, 0.70f, 0.70f, 0.30f,
                    75, 1250L, false);
        } else if (SCENARIO_UNLOCK_FULLSCREEN.equals(scenario)) {
            addLinearDrag(events, 0.30f, 0.70f, 0.70f, 0.30f,
                    75, 1250L, true);
        } else if (SCENARIO_REFRACTION_CHART.equals(scenario)) {
            events.add(new AbEvent(AbEvent.DOWN, 0, 0L, 0.50f, 0.55f));
            events.add(new AbEvent(AbEvent.UP_RELEASE, 1, 1000L, 0.50f, 0.55f));
        }
        return events;
    }

    private static void addLinearDrag(List<AbEvent> events,
            float startX, float startY, float endX, float endY,
            int moveCount, long durationMs, boolean unlock) {
        events.add(new AbEvent(AbEvent.DOWN, 0, 0L, startX, startY));
        addLinearMoves(events, startX, startY, endX, endY,
                1, moveCount, 0L, durationMs);
        events.add(new AbEvent(unlock ? AbEvent.UP_UNLOCK : AbEvent.UP_RELEASE,
                moveCount + 1, durationMs, endX, endY));
    }

    private static void addLinearMoves(List<AbEvent> events,
            float startX, float startY, float endX, float endY,
            int firstIndex, int moveCount, long startMs, long endMs) {
        for (int i = 1; i <= moveCount; i++) {
            float fraction = i / (float) moveCount;
            long targetMs = startMs + Math.round((endMs - startMs) * fraction);
            events.add(new AbEvent(AbEvent.MOVE, firstIndex + i - 1, targetMs,
                    startX + (endX - startX) * fraction,
                    startY + (endY - startY) * fraction));
        }
    }

    private static long finalAbDurationMs(String scenario) {
        if (SCENARIO_FAST_DRAG.equals(scenario)) {
            return 3000L;
        }
        if (SCENARIO_EDGE_CLOSURE.equals(scenario)) {
            return 4000L;
        }
        if (SCENARIO_RELEASE.equals(scenario)) {
            return 5000L;
        }
        if (SCENARIO_UNLOCK_FULLSCREEN.equals(scenario)) {
            return 4500L;
        }
        return 3200L;
    }

    private static final class AbEvent {
        static final String DOWN = "DOWN";
        static final String MOVE = "MOVE";
        static final String UP_RELEASE = "UP_RELEASE";
        static final String UP_UNLOCK = "UP_UNLOCK";

        final String kind;
        final int index;
        final long targetNs;
        final float xNorm;
        final float yNorm;

        AbEvent(String kind, int index, long targetMs, float xNorm, float yNorm) {
            this.kind = kind;
            this.index = index;
            this.targetNs = targetMs * 1_000_000L;
            this.xNorm = xNorm;
            this.yNorm = yNorm;
        }
    }

    private static Bitmap createRefractionChart(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawColor(Color.rgb(127, 127, 127));

        int topHeight = Math.round(height * (512f / 3088f));
        int bottomStart = height - topHeight;
        for (int i = 0; i < 12; i++) {
            int value = Math.round(16f + (224f * i / 11f));
            paint.setColor(Color.rgb(value, value, value));
            canvas.drawRect(width * i / 12f, 0f,
                    width * (i + 1) / 12f, topHeight, paint);
        }

        int[] colours = {
                Color.rgb(255, 32, 32),
                Color.rgb(255, 128, 24),
                Color.rgb(255, 232, 24),
                Color.rgb(32, 224, 64),
                Color.rgb(24, 224, 224),
                Color.rgb(32, 96, 255),
                Color.rgb(144, 48, 255),
                Color.rgb(255, 40, 176)
        };
        for (int i = 0; i < colours.length; i++) {
            paint.setColor(colours[i]);
            canvas.drawRect(width * i / 8f, topHeight,
                    width * (i + 1) / 8f, bottomStart, paint);
        }
        paint.setStrokeWidth(1f);
        for (int x = 0; x < width; x += 32) {
            paint.setColor(Color.WHITE);
            canvas.drawLine(x, topHeight, x, bottomStart, paint);
            if (x + 16 < width) {
                paint.setColor(Color.BLACK);
                canvas.drawLine(x + 16, topHeight, x + 16, bottomStart, paint);
            }
        }
        for (int y = topHeight; y < bottomStart; y += 32) {
            paint.setColor(Color.WHITE);
            canvas.drawLine(0, y, width, y, paint);
            if (y + 16 < bottomStart) {
                paint.setColor(Color.BLACK);
                canvas.drawLine(0, y + 16, width, y + 16, paint);
            }
        }

        int bottomPatchCount = 9;
        for (int i = 0; i < 6; i++) {
            paint.setColor(Color.HSVToColor(new float[] { i * 60f, 0.30f, 0.75f }));
            canvas.drawRect(width * i / (float) bottomPatchCount, bottomStart,
                    width * (i + 1) / (float) bottomPatchCount, height, paint);
        }
        int[] greys = { 64, 128, 192 };
        for (int i = 0; i < greys.length; i++) {
            paint.setColor(Color.rgb(greys[i], greys[i], greys[i]));
            int patch = i + 6;
            canvas.drawRect(width * patch / (float) bottomPatchCount, bottomStart,
                    width * (patch + 1) / (float) bottomPatchCount, height, paint);
        }

        float crossX = width * 0.50f;
        float crossY = height * 0.55f;
        paint.setStrokeWidth(8f);
        paint.setColor(Color.BLACK);
        canvas.drawLine(crossX - 80f, crossY, crossX + 80f, crossY, paint);
        canvas.drawLine(crossX, crossY - 80f, crossX, crossY + 80f, paint);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.WHITE);
        canvas.drawLine(crossX - 80f, crossY, crossX + 80f, crossY, paint);
        canvas.drawLine(crossX, crossY - 80f, crossX, crossY + 80f, paint);
        return bitmap;
    }

    private static String bitmapPixelSha256(Bitmap bitmap) {
        if (bitmap == null) {
            return "missing";
        }
        try {
            ByteBuffer buffer = ByteBuffer.allocate(bitmap.getRowBytes() * bitmap.getHeight());
            bitmap.copyPixelsToBuffer(buffer);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(buffer.array());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.US, "%02X", value & 0xff));
            }
            return hex.toString();
        } catch (Throwable error) {
            Log.e(TAG, "Failed to hash probe background", error);
            return "error";
        }
    }

    private void startColourScenario(final String name, final FrameLayout host,
            final UnlockEffectRenderer renderer) {
        if (activeRenderer != renderer) {
            return;
        }
        int width = Math.max(1, host.getWidth());
        int height = Math.max(1, host.getHeight());
        renderer.warmUp();
        int radius = Math.max(100, Math.min(width, height) / 4);
        renderer.showUnlockAffordance(new Rect(
                width / 2 - radius, height / 2 - radius,
                width / 2 + radius, height / 2 + radius), 0L);
        Log.i(TAG, "PASS " + name + " scenario started/affordance queued size="
                + width + "x" + height);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeRenderer != renderer) return;
                final float startX = Math.max(1, host.getWidth()) * 0.30f;
                final float startY = Math.max(1, host.getHeight()) * 0.70f;
                final float endX = Math.max(1, host.getWidth()) * 0.70f;
                final float endY = Math.max(1, host.getHeight()) * 0.30f;
                final int moveCount = 75;
                final long durationMs = 1250L;
                renderer.beginGesture(startX, startY);
                for (int i = 1; i <= moveCount; i++) {
                    final float fraction = i / (float) moveCount;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (activeRenderer != renderer) return;
                            renderer.updateGesture(
                                    startX + (endX - startX) * fraction,
                                    startY + (endY - startY) * fraction);
                        }
                    }, Math.round(durationMs * fraction));
                }
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (activeRenderer != renderer) return;
                        renderer.finishGesture(false);
                        Log.i(TAG, "PASS " + name
                                + " deterministic drag finished moves=" + moveCount
                                + " durationMs=" + durationMs);
                    }
                }, durationMs);
                Log.i(TAG, "PASS " + name + " deterministic drag queued");
            }
        }, 1700L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeRenderer == renderer) {
                    destroyRenderer(name);
                    finish();
                }
            }
        }, 6700L);
    }

    private void probeSparklingRenderer(final boolean appOwned) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        final String name = appOwned
                ? "Sparkling Bubbles app-owned"
                : "Sparkling Bubbles Samsung oracle";
        final FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(Color.BLACK);
        setContentView(host);

        Bitmap background = Argb8888BitmapStore.decode(
                OverlayPrefs.effectBackgroundFile(
                        this, OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES));
        if (background != null) {
            ImageView backgroundView = new ImageView(this);
            backgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            backgroundView.setImageBitmap(background);
            host.addView(backgroundView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        try {
            activeRenderer = appOwned
                    ? new SparklingBubblesAppOwnedEffectView(this)
                    : new SparklingBubblesArm64EffectView(this);
            View effect = activeRenderer.asView();
            host.addView(effect, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            if (background != null && activeRenderer instanceof BackgroundSourceRenderer) {
                ((BackgroundSourceRenderer) activeRenderer).setBackgroundSourceBitmap(
                        background, "sparkling_probe");
            }
            Log.i(TAG, "PASS " + name + " constructed background="
                    + (background == null ? "missing" :
                    background.getWidth() + "x" + background.getHeight()));
        } catch (Throwable failure) {
            Log.e(TAG, "FAIL " + name + " renderer construction", failure);
            showFailure(host, name, failure);
            return;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeRenderer == null) return;
                int width = Math.max(1, host.getWidth());
                int height = Math.max(1, host.getHeight());
                activeRenderer.warmUp();
                int radius = Math.max(100, Math.min(width, height) / 4);
                activeRenderer.showUnlockAffordance(new Rect(
                        width / 2 - radius, height / 2 - radius,
                        width / 2 + radius, height / 2 + radius), 0L);
                Log.i(TAG, "PASS " + name + " affordance queued size="
                        + width + "x" + height);
            }
        }, 900L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeRenderer == null) return;
                float x = Math.max(1, host.getWidth()) * 0.45f;
                float y = Math.max(1, host.getHeight()) * 0.50f;
                activeRenderer.beginGesture(x, y);
                activeRenderer.updateGesture(x + 80f, y + 60f);
                activeRenderer.finishGesture(false);
                Log.i(TAG, "PASS " + name + " touch sequence queued");
            }
        }, 2600L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                destroyRenderer(name);
                finish();
            }
        }, 6200L);
    }

    private void probeRenderer(final boolean droplet) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        final String name = droplet ? "Colour Droplet" : "Sparkling Bubbles";
        final FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(Color.BLACK);
        setContentView(host);
        try {
            activeRenderer = droplet
                    ? new ColourDropletEffectView(this, false)
                    : new SparklingBubblesEffectView(this);
            View effect = activeRenderer.asView();
            host.addView(effect, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            if (!readReady(activeRenderer)) {
                throw new IllegalStateException(name + " wrapper reported renderer unavailable");
            }
            Log.i(TAG, "PASS " + name + " wrapper/native renderer constructed");
        } catch (Throwable failure) {
            Log.e(TAG, "FAIL " + name + " renderer construction", failure);
            showFailure(host, name, failure);
            return;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeRenderer == null) return;
                int width = Math.max(1, host.getWidth());
                int height = Math.max(1, host.getHeight());
                activeRenderer.warmUp();
                int radius = Math.max(100, Math.min(width, height) / 4);
                activeRenderer.showUnlockAffordance(new Rect(
                        width / 2 - radius, height / 2 - radius,
                        width / 2 + radius, height / 2 + radius), 0L);
                Log.i(TAG, "PASS " + name + " warmup/affordance queued size="
                        + width + "x" + height);
            }
        }, 900L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeRenderer == null) return;
                float x = Math.max(1, host.getWidth()) * 0.45f;
                float y = Math.max(1, host.getHeight()) * 0.50f;
                activeRenderer.beginGesture(x, y);
                activeRenderer.updateGesture(x + 80f, y + 60f);
                activeRenderer.finishGesture(false);
                Log.i(TAG, "PASS " + name + " touch sequence queued");
            }
        }, 2100L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeRenderer == null) return;
                Log.i(TAG, "BEGIN " + name + " reset");
                activeRenderer.resetEffect();
                Log.i(TAG, "PASS " + name + " survived init/GLES/touch/reset window");
            }
        }, 5000L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                destroyRenderer(name);
            }
        }, 6500L);
    }

    private boolean readReady(UnlockEffectRenderer renderer) throws Exception {
        Field ready = renderer.getClass().getDeclaredField("ready");
        ready.setAccessible(true);
        return ready.getBoolean(renderer);
    }

    private void showFailure(FrameLayout host, String name, Throwable failure) {
        TextView view = new TextView(this);
        view.setText("FAIL " + name + "\n" + failure);
        view.setTextColor(Color.RED);
        view.setTextSize(16f);
        view.setPadding(32, 64, 32, 32);
        host.addView(view);
    }

    private void destroyRenderer(String name) {
        UnlockEffectRenderer renderer = activeRenderer;
        activeRenderer = null;
        if (renderer == null) return;
        try {
            Log.i(TAG, "BEGIN " + name + " renderer destroy");
            renderer.destroy();
            Log.i(TAG, "PASS " + name + " renderer destroyed cleanly");
        } catch (Throwable failure) {
            Log.e(TAG, "FAIL " + name + " renderer destruction", failure);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        destroyRenderer("active Note5");
        super.onDestroy();
    }

    private void probeRippleCore(StringBuilder result) {
        try {
            Class<?> cls = Class.forName(WATER_RIPPLE, true, getClassLoader());
            Method initWaters = cls.getMethod("initWaters", float[].class, short[].class,
                    int.class, int.class, int.class, int.class, int.class);
            Method ripple = cls.getMethod("ripple", float[].class,
                    int.class, int.class, int.class, int.class,
                    float.class, float.class, float.class);
            Method move = cls.getMethod("move", float[].class, float[].class,
                    int.class, int.class, int.class, int.class, int.class, int.class,
                    boolean.class, float.class, float.class);

            float[] vertices = new float[27];
            short[] indices = new short[24];
            initWaters.invoke(null, vertices, indices, 9, 4, 6, 3, 3);
            short[] expectedIndices = {
                    0, 1, 4, 0, 4, 3,
                    1, 2, 5, 1, 5, 4,
                    3, 4, 7, 3, 7, 6,
                    4, 5, 8, 4, 8, 7
            };
            requireClose(vertices[0], -3f, "mesh first x");
            requireClose(vertices[1], 2f, "mesh first y");
            requireClose(vertices[24], 3f, "mesh last x");
            requireClose(vertices[25], -2f, "mesh last y");
            for (int i = 0; i < expectedIndices.length; i++) {
                if (indices[i] != expectedIndices[i]) {
                    throw new AssertionError("mesh index " + i + "=" + indices[i]);
                }
            }

            final int detail = 104;
            float[] velocity = new float[detail * detail];
            float[] height = new float[detail * detail];
            ripple.invoke(null, velocity, 50, 50, detail, detail, 0f, 0f, 2f);
            int center = 52 + 52 * detail;
            requireClose(velocity[center], 6f, "ripple center");
            requireClose(velocity[center + 1], 4f, "ripple right");
            requireClose(velocity[center + detail], 4f, "ripple down");
            Object empty = move.invoke(null, velocity, height,
                    2, 2, 102, 102, detail, detail, true, 0.94f, 0.5f);
            if (!(empty instanceof Integer) || ((Integer) empty).intValue() != 0) {
                throw new AssertionError("active ripple returned empty=" + empty);
            }

            result.append("PASS Water Ripple ARM64 JNI core\n");
            Log.i(TAG, "PASS Water Ripple ARM64 initWaters/ripple/move through ART");
        } catch (Throwable failure) {
            result.append("FAIL Water Ripple ARM64 JNI core: ")
                    .append(failure).append("\n");
            Log.e(TAG, "FAIL Water Ripple ARM64 JNI core", failure);
        }
    }

    private static void requireClose(float actual, float expected, String label) {
        if (Math.abs(actual - expected) > 0.00001f) {
            throw new AssertionError(label + "=" + actual + " expected=" + expected);
        }
    }

    private void probeClass(String className, StringBuilder result) {
        try {
            Class.forName(className, true, getClassLoader());
            result.append("PASS ").append(className).append("\n");
            Log.i(TAG, "PASS class-load/JNI_OnLoad/RegisterNatives " + className);
        } catch (Throwable failure) {
            result.append("FAIL ").append(className).append(": ")
                    .append(failure).append("\n");
            Log.e(TAG, "FAIL " + className, failure);
        }
    }
}
