package com.codex.s4unlockfx;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class OriginalSamsungEffectHost extends FrameLayout {
    private static final String TAG = "S4OriginalHost";
    private static final boolean ALLOW_TOUCH_MODE_CYCLING = false;
    private static final long STOCK_REPEAT_PRESS_MS = 600L;
    private static final int FORWARDED_ACTION_NONE = -1;
    private static final Mode[] MODES = new Mode[] {
            new Mode(11, "Lens flare S4", 0, 0, 0),
            new Mode(-1, "S3 ripple original", 0, 0, 0, "com.android.internal.policy.impl.keyguard.sec.RippleUnlockView"),
            new Mode(3, "Popping colours / Particle space", R.raw.particle_tap, R.raw.particle_drag, 0, R.raw.particle_unlock),
            new Mode(10, "Blind", R.raw.blind_touch, 0, R.raw.blind_unlock),
            new Mode(5, "Watercolor native", R.raw.ve_watercolour_tap, 0, R.raw.ve_watercolour_unlock),
            new Mode(8, "Ink in water / Ripple ink", R.raw.ve_ripple_down, R.raw.ve_ripple_up, 0, R.raw.ve_ripple_up),
            new Mode(9, "Indigo diffusion", R.raw.simple_ripple_down, R.raw.simple_ripple_up, 0, R.raw.simple_ripple_up),
            new Mode(0, "Abstract tiles native", R.raw.abstracttile_tap, R.raw.abstracttile_drag, 0, R.raw.abstracttile_unlock),
            new Mode(1, "Geometric mosaic native", R.raw.abstracttile_tap, R.raw.abstracttile_drag, 0, R.raw.abstracttile_unlock),
            new Mode(6, "Brilliant cut native", R.raw.brilliantcut_tap, R.raw.brilliantcut_drag, 0, R.raw.brilliantcut_unlock),
            new Mode(7, "Brilliant ring native", R.raw.brilliantring_tap, R.raw.brilliantring_drag, 0, R.raw.brilliantring_unlock),
            new Mode(16, "Coloured droplets", R.raw.ve_colourdroplet_tap, 0, 0, R.raw.ve_colourdroplet_unlock),
            new Mode(14, "Sparkling bubbles", R.raw.ve_sparklingbubbles_tap, R.raw.ve_sparklingbubbles_drag, 0, R.raw.ve_sparklingbubbles_unlock),
            new Mode(-5, "Note4 seasonal particles unlock", R.raw.spring_tap, R.raw.spring_drag, R.raw.spring_tap, R.raw.spring_unlock, LegacyCanvasEffectView.EFFECT_NOTE4_SEASONAL_UNLOCK, null),
            new Mode(-9, "Note4 Coloured paper festival", R.raw.spring_tap, R.raw.spring_drag, R.raw.spring_tap, R.raw.spring_unlock, LegacyCanvasEffectView.EFFECT_NOTE4_COLORED_PAPER, null),
            new Mode(-11, "Note4 charge doodle spring", 0, 0, 0, 0, LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_SPRING, null),
            new Mode(-12, "Note4 charge doodle summer", 0, 0, 0, 0, LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_SUMMER, null),
            new Mode(-13, "Note4 charge doodle autumn", 0, 0, 0, 0, LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_AUTUMN, null),
            new Mode(-14, "Note4 charge doodle winter", 0, 0, 0, 0, LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_WINTER, null)
    };

    private final TextView label;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap wallpaperBitmap;
    private Bitmap displayWallpaperBitmap;
    private ImageView wallpaperView;
    private String currentWallpaperResourceName;
    private final int screenWidth;
    private final int screenHeight;
    private final SoundPool soundPool;
    private final HashMap<Integer, Integer> loadedSounds = new HashMap<Integer, Integer>();
    private Object effectView;
    private View effectViewAsView;
    private final LegacyCanvasEffectView customEffectView;
    private final Method handleTouchEvent;
    private final Method handleCustomEvent;
    private final Method setListener;
    private final Object effectListener;
    private int modeIndex;
    private long lastSwitchAt;
    private float downX;
    private float downY;
    private float lastDragSoundX;
    private float lastDragSoundY;
    private float dragSoundDistance;
    private long touchDownAt;
    private int forwardedAction = FORWARDED_ACTION_NONE;
    private boolean forwardedReleaseTriggersUnlock;
    private final SystemUiLegacyEffectView systemUiEffectView;

    public static View tryCreate(Activity activity) {
        try {
            return new OriginalSamsungEffectHost(activity);
        } catch (Throwable t) {
            Log.e(TAG, "Original Samsung effect unavailable", t);
            return null;
        }
    }

    private OriginalSamsungEffectHost(Context context) throws Exception {
        super(context);
        setBackgroundColor(Color.rgb(8, 12, 18));
        setFocusable(true);
        setClickable(true);
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        preloadModeSounds();

        Class<?> effectViewClass = Class.forName("com.samsung.android.visualeffect.EffectView");
        Class<?> listenerClass = Class.forName("com.samsung.android.visualeffect.IEffectListener");
        effectView = effectViewClass.getConstructor(Context.class).newInstance(context);
        handleTouchEvent = effectViewClass.getMethod("handleTouchEvent", MotionEvent.class, View.class);
        handleCustomEvent = effectViewClass.getMethod("handleCustomEvent", int.class, HashMap.class);
        setListener = effectViewClass.getMethod("setListener", listenerClass);
        effectListener = Proxy.newProxyInstance(
                listenerClass.getClassLoader(),
                new Class<?>[] { listenerClass },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("onReceive".equals(method.getName()) && args != null && args.length > 0) {
                            handleEffectCallback(args);
                            Log.d(TAG, "effect callback " + args[0]);
                        }
                        return null;
                    }
                });

        screenWidth = Math.max(getResources().getDisplayMetrics().widthPixels, 1);
        screenHeight = Math.max(getResources().getDisplayMetrics().heightPixels, 1);
        wallpaperView = new ImageView(context);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(wallpaperView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        effectViewAsView = (View) effectView;
        addView(effectViewAsView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        customEffectView = new LegacyCanvasEffectView(context);
        customEffectView.setVisibility(GONE);
        addView(customEffectView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        systemUiEffectView = new SystemUiLegacyEffectView(context);
        systemUiEffectView.setVisibility(GONE);
        addView(systemUiEffectView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        label = new TextView(context);
        label.setTextColor(Color.argb(230, 240, 248, 255));
        label.setTextSize(12f);
        label.setGravity(Gravity.CENTER);
        label.setBackgroundColor(Color.argb(120, 0, 0, 0));
        LayoutParams labelParams = new LayoutParams(dp(280), dp(54));
        labelParams.leftMargin = dp(14);
        labelParams.topMargin = dp(14);
        addView(label, labelParams);

        int initialMode = getContext()
                .getSharedPreferences(UnlockFxPrefs.NAME, Context.MODE_PRIVATE)
                .getInt(UnlockFxPrefs.MODE_INDEX, 0);
        setOriginalEffect(initialMode);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        backgroundPaint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(), 0xff080a10, 0xff142536, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        backgroundPaint.setShader(null);
        super.dispatchDraw(canvas);
    }

    private void setOriginalEffect(int nextIndex) {
        modeIndex = UnlockFxPrefs.normalizeModeIndex(nextIndex);
        Mode mode = MODES[modeIndex];
        updateWallpaperForMode(modeIndex);
        getContext().getSharedPreferences(UnlockFxPrefs.NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(UnlockFxPrefs.MODE_INDEX, modeIndex)
                .putString(UnlockFxPrefs.MODE_NAME, mode.name)
                .apply();
        if (mode.isSystemUiLegacy()) {
            effectViewAsView.setVisibility(INVISIBLE);
            customEffectView.setEffectType(LegacyCanvasEffectView.EFFECT_NONE);
            label.setVisibility(VISIBLE);
            if (!systemUiEffectView.setEffectClassName(mode.systemUiClassName)) {
                label.setText("Failed: " + mode.name);
                Log.e(TAG, "Failed SystemUI legacy effect " + mode.systemUiClassName + " " + mode.name);
                return;
            }
            label.setText(formatLabel(modeIndex));
            Log.i(TAG, "Loaded SystemUI legacy effect " + mode.systemUiClassName + " " + mode.name);
            return;
        }
        if (mode.isCustom()) {
            effectViewAsView.setVisibility(INVISIBLE);
            systemUiEffectView.clearEffect();
            customEffectView.setEffectType(mode.customEffectType);
            boolean hideLabel = isChargeDoodleCustomEffect(mode.customEffectType);
            label.setVisibility(hideLabel ? GONE : VISIBLE);
            if (!hideLabel) {
                label.setText(formatLabel(modeIndex));
            }
            Log.i(TAG, "Loaded custom legacy effect " + mode.customEffectType + " " + mode.name);
            return;
        }
        try {
            systemUiEffectView.clearEffect();
            effectViewAsView.setVisibility(VISIBLE);
            customEffectView.setEffectType(LegacyCanvasEffectView.EFFECT_NONE);
            label.setVisibility(VISIBLE);
            Class<?> effectViewClass = Class.forName("com.samsung.android.visualeffect.EffectView");
            Class<?> dataClass = Class.forName("com.samsung.android.visualeffect.EffectDataObj");
            Method setEffect = effectViewClass.getMethod("setEffect", int.class);
            Method init = effectViewClass.getMethod("init", dataClass);
            Method reInit = effectViewClass.getMethod("reInit", dataClass);

            Object data = dataClass.getConstructor().newInstance();
            prepareData(dataClass, data, mode.effectId);

            setEffect.invoke(effectView, mode.effectId);
            if (mode.effectId == 10) {
                sendBlindBitmaps();
            }
            init.invoke(effectView, data);
            if (mode.effectId == 9) {
                reInit.invoke(effectView, data);
            }
            setListener.invoke(effectView, effectListener);
            if (mode.effectId == 11) {
                sendLensFlareStartupCommands();
            }
            if (mode.effectId == 3) {
                sendParticleSpaceBitmap();
            }
            sendBackgroundBitmap(mode.effectId);
            sendBackgroundBitmapWhenReady(mode.effectId);
            label.setText(formatLabel(modeIndex));
            Log.i(TAG, "Loaded original effect " + mode.effectId + " " + mode.name);
        } catch (Throwable t) {
            Log.e(TAG, "Failed original effect " + mode.effectId + " " + mode.name, t);
            customEffectView.setEffectType(LegacyCanvasEffectView.EFFECT_NONE);
            systemUiEffectView.clearEffect();
            effectViewAsView.setVisibility(INVISIBLE);
            label.setVisibility(VISIBLE);
            label.setText("Failed: " + mode.name);
        }
    }

    private boolean isChargeDoodleCustomEffect(int customEffectType) {
        return customEffectType == LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_SPRING
                || customEffectType == LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_SUMMER
                || customEffectType == LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_AUTUMN
                || customEffectType == LegacyCanvasEffectView.EFFECT_NOTE4_CHARGE_WINTER;
    }

    private String formatLabel(int modeIndex) {
        SharedPreferences prefs = getContext().getSharedPreferences(UnlockFxPrefs.NAME, Context.MODE_PRIVATE);
        int wallpaperMode = prefs.getInt(UnlockFxPrefs.WALLPAPER_MODE, UnlockFxPrefs.WALLPAPER_MODE_AUTO);
        String wallpaperLabel;
        if (wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_CUSTOM) {
            wallpaperLabel = "Custom gallery";
        } else if (wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_STOCK) {
            int stockIndex = prefs.getInt(UnlockFxPrefs.STOCK_WALLPAPER_INDEX, 0);
            wallpaperLabel = UnlockFxPrefs.stockWallpaperName(stockIndex);
        } else {
            wallpaperLabel = UnlockFxPrefs.defaultWallpaperNameForModeIndex(modeIndex);
        }
        return UnlockFxPrefs.modeName(modeIndex)
                + "\n"
                + UnlockFxPrefs.modelNameForModeIndex(modeIndex)
                + " | "
                + wallpaperLabel;
    }

    private void sendLensFlareStartupCommands() {
        sendLensFlareCommand("manualInit");
        sendLensFlareCommand("show");
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (MODES[modeIndex].effectId == 11) {
                    sendLensFlareCommand("manualInit");
                    sendLensFlareCommand("show");
                }
            }
        }, 180L);
    }

    private void sendLensFlareCommand(String command) {
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put(command, Boolean.TRUE);
            handleCustomEvent.invoke(effectView, 3, params);
        } catch (Throwable t) {
            Log.d(TAG, "lens flare command ignored: " + command, t);
        }
    }

    private void prepareData(Class<?> dataClass, Object data, int effectId) throws Exception {
        Method setEffectData = dataClass.getMethod("setEffect", int.class);
        setEffectData.invoke(data, effectId);

        if (effectId == 11) {
            Class<?> lensDataClass = Class.forName("com.samsung.android.visualeffect.lock.data.LensFlareData");
            Object lensData = getOrCreate(dataClass, data, "lensFlareData", lensDataClass);
            setInt(lensDataClass, lensData, "light", drawableId("keyguard_flare_light_00040"));
            setInt(lensDataClass, lensData, "ring", drawableId("keyguard_flare_ring"));
            setInt(lensDataClass, lensData, "particle", drawableId("keyguard_flare_particle"));
            setInt(lensDataClass, lensData, "long_light", drawableId("keyguard_flare_long"));
            setInt(lensDataClass, lensData, "rainbow", drawableId("keyguard_flare_rainbow"));
            setInt(lensDataClass, lensData, "hoverlight", drawableId("keyguard_flare_hoverlight"));
            setInt(lensDataClass, lensData, "vignetting", drawableId("keyguard_flare_vignetting"));
            setInt(lensDataClass, lensData, "hexagon_blue", drawableId("keyguard_flare_hexagon_blue"));
            setInt(lensDataClass, lensData, "hexagon_green", drawableId("keyguard_flare_hexagon_green"));
            setInt(lensDataClass, lensData, "hexagon_orange", drawableId("keyguard_flare_hexagon_orange"));
            setIntIfFieldExists(lensDataClass, lensData, "tapSound", R.raw.s4_lens_flare_tap);
            setIntIfFieldExists(lensDataClass, lensData, "unlockSound", R.raw.s4_lens_flare_unlock);
        } else if (effectId == 8) {
            Class<?> rippleDataClass = Class.forName("com.samsung.android.visualeffect.lock.data.RippleInkData");
            Object rippleData = getOrCreate(dataClass, data, "rippleInkData", rippleDataClass);
            prepareBitmapData(rippleDataClass, rippleData);
        } else if (effectId == 9) {
            Class<?> indigoDataClass = Class.forName("com.samsung.android.visualeffect.lock.data.IndigoDiffuseData");
            Object indigoData = getOrCreate(dataClass, data, "indigoDiffuseData", indigoDataClass);
            prepareBitmapData(indigoDataClass, indigoData);
            setInt(indigoDataClass, indigoData, "red", 80);
            setInt(indigoDataClass, indigoData, "green", 160);
            setInt(indigoDataClass, indigoData, "blue", 255);
        } else if (effectId == 2) {
            Class<?> circleDataClass = Class.forName("com.samsung.android.visualeffect.lock.data.CircleData");
            Object circleData = getOrCreate(dataClass, data, "circleData", circleDataClass);
            setInt(circleDataClass, circleData, "circleUnlockMaxWidth", dp(260));
            setInt(circleDataClass, circleData, "outerStrokeWidth", dp(3));
            setInt(circleDataClass, circleData, "innerStrokeWidth", dp(2));
            setInt(circleDataClass, circleData, "minWidthOffset", dp(24));
            setInt(circleDataClass, circleData, "arrowId", drawableId("keyguard_none_arrow"));
            setInt(circleDataClass, circleData, "arrowForButtonId", drawableId("keyguard_none_arrow"));
            int[] sequence = new int[30];
            for (int i = 0; i < sequence.length; i++) {
                sequence[i] = drawableId(String.format("keyguard_none_lock_%02d", i + 1));
            }
            getField(circleDataClass, "lockSequenceImageId").set(circleData, sequence);
        } else if (effectId == 3) {
            Object poppingData = tryGetOrCreate(dataClass, data, "poppingColorData", "com.samsung.android.visualeffect.lock.data.PoppingColorData");
            if (poppingData != null) {
                Class<?> poppingDataClass = poppingData.getClass();
                FrameLayout widget = new FrameLayout(getContext());
                FrameLayout wallpaper = new FrameLayout(getContext());
                widget.setBackgroundColor(Color.TRANSPARENT);
                wallpaper.setBackgroundColor(Color.rgb(12, 20, 30));
                ImageView image = new ImageView(getContext());
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(copyWallpaperBitmap());
                wallpaper.addView(image, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                getField(poppingDataClass, "widgetLayout").set(poppingData, widget);
                getField(poppingDataClass, "wallpaperWidget").set(poppingData, wallpaper);
            }
        } else if (effectId == 12 || effectId == 13) {
            Class<?> dropletDataClass = Class.forName("com.samsung.android.visualeffect.lock.data.WaterDropletData");
            Object dropletData = getOrCreate(dataClass, data, "waterDropletData", dropletDataClass);
            prepareDropletTextureData(dropletDataClass, dropletData);
        } else if (effectId == 14 || effectId == 15) {
            Class<?> bubblesDataClass = Class.forName("com.samsung.android.visualeffect.lock.data.SparklingBullesData");
            Object bubblesData = getOrCreate(dataClass, data, "sparklingBubblesData", bubblesDataClass);
            prepareSparklingBubblesData(bubblesDataClass, bubblesData);
        } else if (effectId == 16 || effectId == 17) {
            Class<?> colourDataClass = Class.forName("com.samsung.android.visualeffect.lock.data.ColourDropletData");
            Object colourData = getOrCreate(dataClass, data, "colorDroplet", colourDataClass);
            prepareColourDropletTextureData(colourDataClass, colourData);
        }
    }

    private void sendParticleSpaceBitmap() {
        if (displayWallpaperBitmap == null) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("BGBitmap", copyWallpaperBitmap());
            handleCustomEvent.invoke(effectView, 0, params);
        } catch (Throwable t) {
            Log.d(TAG, "particle-space bitmap command ignored", t);
        }
    }

    private void sendBackgroundBitmap(int effectId) {
        if (displayWallpaperBitmap == null || !usesLockBackground(effectId)) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("Bitmap", copyWallpaperBitmap());
            if (usesSPhysicsBackground(effectId)) {
                params.put("Mode", Integer.valueOf(0));
            }
            handleCustomEvent.invoke(effectView, 0, params);
        } catch (Throwable t) {
            Log.d(TAG, "background bitmap command ignored by this effect", t);
        }
    }

    private void sendBackgroundBitmapWhenReady(final int effectId) {
        if (!usesSparklingBackgroundRetry(effectId)) {
            return;
        }
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (MODES[modeIndex].effectId == effectId) {
                    sendBackgroundBitmap(effectId);
                }
            }
        }, 160L);
    }

    private void sendBlindBitmaps() {
        if (displayWallpaperBitmap == null) {
            return;
        }
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("background", copyWallpaperBitmap());
            params.put("light", createBlindLightBitmap());
            handleCustomEvent.invoke(effectView, 0, params);
        } catch (Throwable t) {
            Log.e(TAG, "blind bitmap setup failed", t);
        }
    }

    private boolean usesLockBackground(int effectId) {
        return effectId == 0
                || effectId == 1
                || effectId == 5
                || effectId == 6
                || effectId == 7
                || effectId == 8
                || effectId == 9
                || usesSPhysicsBackground(effectId);
    }

    private boolean usesSPhysicsBackground(int effectId) {
        return effectId == 14
                || effectId == 15
                || effectId == 16
                || effectId == 17;
    }

    private boolean usesSparklingBackgroundRetry(int effectId) {
        return effectId == 14 || effectId == 15;
    }

    private Object getOrCreate(Class<?> owner, Object target, String fieldName, Class<?> valueClass) throws Exception {
        Field field = getField(owner, fieldName);
        Object value = field.get(target);
        if (value == null) {
            value = valueClass.getConstructor().newInstance();
            field.set(target, value);
        }
        return value;
    }

    private Object tryGetOrCreate(Class<?> owner, Object target, String fieldName, String className) {
        try {
            Class<?> valueClass = Class.forName(className);
            return getOrCreate(owner, target, fieldName, valueClass);
        } catch (Throwable t) {
            Log.d(TAG, "optional effect data unavailable: " + fieldName + " (" + t.getClass().getSimpleName() + ")");
            return null;
        }
    }

    private void prepareBitmapData(Class<?> dataClass, Object data) throws Exception {
        setInt(dataClass, data, "windowWidth", screenWidth);
        setInt(dataClass, data, "windowHeight", screenHeight);
        getField(dataClass, "reflectionBitmap").set(data, copyWallpaperBitmap());
    }

    private void prepareDropletTextureData(Class<?> dataClass, Object data) throws Exception {
        setInt(dataClass, data, "windowWidth", screenWidth);
        setInt(dataClass, data, "windowHeight", screenHeight);
        getField(dataClass, "resNormal").set(data, copyWallpaperBitmap());
        getField(dataClass, "resEdgeDensity").set(data, createEdgeDensityBitmap());
        setObjectIfFieldExists(dataClass, data, "mIEffectListener", effectListener);
    }

    private void prepareColourDropletTextureData(Class<?> dataClass, Object data) throws Exception {
        setInt(dataClass, data, "windowWidth", screenWidth);
        setInt(dataClass, data, "windowHeight", screenHeight);
        Bitmap normal = loadStockBitmap("note5_normal_low_z_256.png");
        Bitmap edgeDensity = loadStockBitmap("note5_edge_density_720.png");
        getField(dataClass, "resNormal").set(data, normal != null ? normal : createFlatNormalBitmap());
        getField(dataClass, "resEdgeDensity").set(data, edgeDensity != null ? edgeDensity : createSolidBitmap(Color.BLACK));
        setObjectIfFieldExists(dataClass, data, "mIEffectListener", effectListener);
    }

    private void prepareSparklingBubblesData(Class<?> dataClass, Object data) throws Exception {
        setInt(dataClass, data, "windowWidth", screenWidth);
        setInt(dataClass, data, "windowHeight", screenHeight);
        getField(dataClass, "resBmp").set(data, copyWallpaperBitmap());
        setObjectIfFieldExists(dataClass, data, "mIEffectListener", effectListener);
    }

    private Bitmap copyWallpaperBitmap() {
        Bitmap source = displayWallpaperBitmap;
        if (source == null || source.isRecycled()) {
            updateWallpaperForMode(modeIndex);
            source = displayWallpaperBitmap;
        }
        if (source == null || source.isRecycled()) {
            source = createCenterCropBitmap(loadWallpaperBitmap("keyguard_default_wallpaper"), screenWidth, screenHeight);
        }
        Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
        return copy != null ? copy : source;
    }

    private Bitmap createEdgeDensityBitmap() {
        Bitmap source = displayWallpaperBitmap;
        if (source == null || source.isRecycled()) {
            updateWallpaperForMode(modeIndex);
            source = displayWallpaperBitmap;
        }
        if (source == null || source.isRecycled()) {
            source = createCenterCropBitmap(loadWallpaperBitmap("keyguard_default_wallpaper"), screenWidth, screenHeight);
        }
        Bitmap bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        int step = Math.max(1, Math.min(screenWidth, screenHeight) / 320);
        for (int y = 0; y < screenHeight; y++) {
            int sampleY = Math.min(screenHeight - 1, y + step);
            for (int x = 0; x < screenWidth; x++) {
                int sampleX = Math.min(screenWidth - 1, x + step);
                int current = source.getPixel(x, y);
                int right = source.getPixel(sampleX, y);
                int down = source.getPixel(x, sampleY);
                int currentLum = luminance(current);
                int edge = Math.min(255, Math.abs(currentLum - luminance(right)) + Math.abs(currentLum - luminance(down)));
                bitmap.setPixel(x, y, Color.argb(255, edge, edge, edge));
            }
        }
        return bitmap;
    }

    private Bitmap createFlatNormalBitmap() {
        return createSolidBitmap(Color.rgb(128, 128, 255));
    }

    private Bitmap createSolidBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private Bitmap loadStockBitmap(String assetName) {
        InputStream stream = null;
        try {
            stream = getContext().getAssets().open(assetName);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap != null) {
                return bitmap;
            }
        } catch (Throwable t) {
            Log.w(TAG, "stock bitmap unavailable: " + assetName, t);
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

    private int luminance(int color) {
        return (Color.red(color) * 54 + Color.green(color) * 183 + Color.blue(color) * 19) >> 8;
    }

    private Bitmap createBlindLightBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                screenHeight,
                new int[] {
                        Color.argb(150, 255, 255, 255),
                        Color.argb(70, 255, 255, 255),
                        Color.argb(0, 255, 255, 255)
                },
                new float[] { 0f, 0.22f, 0.72f },
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint);
        paint.setShader(new LinearGradient(
                0f,
                screenHeight * 0.15f,
                screenWidth,
                screenHeight * 0.55f,
                Color.argb(0, 255, 255, 255),
                Color.argb(90, 255, 255, 255),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint);
        paint.setShader(null);
        return bitmap;
    }

    private Bitmap createCenterCropBitmap(Bitmap source, int outWidth, int outHeight) {
        Bitmap bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        float scale = Math.max(outWidth / (float) source.getWidth(), outHeight / (float) source.getHeight());
        float width = source.getWidth() * scale;
        float height = source.getHeight() * scale;
        float left = (outWidth - width) * 0.5f;
        float top = (outHeight - height) * 0.5f;
        canvas.drawBitmap(source, null, new RectF(left, top, left + width, top + height), paint);
        return bitmap;
    }

    private void updateWallpaperForMode(int nextModeIndex) {
        SharedPreferences prefs = getContext().getSharedPreferences(UnlockFxPrefs.NAME, Context.MODE_PRIVATE);
        int wallpaperMode = prefs.getInt(UnlockFxPrefs.WALLPAPER_MODE, UnlockFxPrefs.WALLPAPER_MODE_AUTO);
        int stockIndex = prefs.getInt(UnlockFxPrefs.STOCK_WALLPAPER_INDEX, 0);
        String customUri = prefs.getString(UnlockFxPrefs.CUSTOM_WALLPAPER_URI, null);
        String resourceName = UnlockFxPrefs.defaultWallpaperResourceNameForModeIndex(nextModeIndex);
        String wallpaperKey = "res:" + resourceName;
        if (wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_STOCK) {
            resourceName = UnlockFxPrefs.stockWallpaperResourceName(stockIndex);
            wallpaperKey = "stock:" + stockIndex + ":" + resourceName;
        } else if (wallpaperMode == UnlockFxPrefs.WALLPAPER_MODE_CUSTOM && customUri != null && customUri.length() > 0) {
            wallpaperKey = "uri:" + customUri;
        }
        if (wallpaperKey.equals(currentWallpaperResourceName)
                && displayWallpaperBitmap != null
                && !displayWallpaperBitmap.isRecycled()) {
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
        Bitmap nextDisplayWallpaper = createCenterCropBitmap(nextWallpaper, screenWidth, screenHeight);
        wallpaperView.setImageBitmap(nextDisplayWallpaper);
        recycleBitmap(displayWallpaperBitmap);
        recycleBitmap(wallpaperBitmap);
        wallpaperBitmap = nextWallpaper;
        displayWallpaperBitmap = nextDisplayWallpaper;
        currentWallpaperResourceName = wallpaperKey;
        Log.i(TAG, "Loaded wallpaper " + wallpaperKey + " for " + UnlockFxPrefs.modeName(nextModeIndex));
    }

    private Bitmap loadWallpaperBitmapFromUri(String uriString) {
        InputStream stream = null;
        try {
            stream = getContext().getContentResolver().openInputStream(Uri.parse(uriString));
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap != null) {
                return bitmap;
            }
        } catch (Throwable t) {
            Log.w(TAG, "custom wallpaper unavailable: " + uriString, t);
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

    private Bitmap loadWallpaperBitmap(String resourceName) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), drawableId(resourceName));
        if (bitmap == null && !"keyguard_default_wallpaper".equals(resourceName)) {
            bitmap = BitmapFactory.decodeResource(getResources(), drawableId("keyguard_default_wallpaper"));
        }
        if (bitmap != null) {
            return bitmap;
        }
        Bitmap fallback = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        fallback.eraseColor(Color.rgb(20, 34, 48));
        return fallback;
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (ALLOW_TOUCH_MODE_CYCLING && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            switchMode();
            return true;
        }
        if (ALLOW_TOUCH_MODE_CYCLING && event.getActionMasked() == MotionEvent.ACTION_DOWN && event.getY() < dp(70)) {
            return true;
        }
        if (ALLOW_TOUCH_MODE_CYCLING && event.getActionMasked() == MotionEvent.ACTION_UP && event.getY() < dp(70)) {
            switchMode();
            return true;
        }
        Mode mode = MODES[modeIndex];
        int action = event.getActionMasked();
        boolean unlockRelease = action == MotionEvent.ACTION_UP && shouldTriggerUnlock(event);
        handleDirectTouchSound(mode, event);
        if (mode.isCustom()) {
            customEffectView.onHostTouchEvent(event);
            if (action == MotionEvent.ACTION_UP) {
                playReleaseSound(mode, unlockRelease);
            }
            return true;
        }
        if (mode.isSystemUiLegacy()) {
            systemUiEffectView.onHostTouchEvent(event);
            if (unlockRelease) {
                systemUiEffectView.onHostUnlock(event);
                playModeSound(mode.unlockSoundRes);
            } else if (action == MotionEvent.ACTION_UP) {
                playReleaseSound(mode, false);
            }
            return true;
        }
        try {
            forwardedAction = action;
            forwardedReleaseTriggersUnlock = unlockRelease;
            handleTouchEvent.invoke(effectView, event, this);
        } catch (Throwable t) {
            Log.e(TAG, "touch forwarding failed", t);
        } finally {
            forwardedAction = FORWARDED_ACTION_NONE;
            forwardedReleaseTriggersUnlock = false;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (unlockRelease) {
                triggerUnlockAnimation(mode);
                playModeSound(mode.unlockSoundRes);
            } else {
                playReleaseSound(mode, false);
            }
        }
        return true;
    }

    private void handleDirectTouchSound(Mode mode, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            lastDragSoundX = downX;
            lastDragSoundY = downY;
            dragSoundDistance = 0f;
            touchDownAt = SystemClock.uptimeMillis();
            if (!usesNativeSoundCallbacks(mode)) {
                playModeSound(mode.downSoundRes);
            }
            return;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (!usesNativeSoundCallbacks(mode) && mode.dragSoundRes != 0) {
                float dx = event.getX() - lastDragSoundX;
                float dy = event.getY() - lastDragSoundY;
                dragSoundDistance += (float) Math.sqrt(dx * dx + dy * dy);
                lastDragSoundX = event.getX();
                lastDragSoundY = event.getY();
                if (dragSoundDistance > dragSoundThreshold()) {
                    playModeSound(mode.dragSoundRes);
                    dragSoundDistance = 0f;
                }
            }
            return;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            dragSoundDistance = 0f;
        }
    }

    private void playReleaseSound(Mode mode, boolean unlockRelease) {
        if (unlockRelease) {
            playModeSound(mode.unlockSoundRes);
            return;
        }
        if (usesNativeSoundCallbacks(mode)) {
            return;
        }
        long heldFor = SystemClock.uptimeMillis() - touchDownAt;
        if (heldFor > STOCK_REPEAT_PRESS_MS) {
            playModeSound(repeatPressSoundRes(mode));
        } else {
            playModeSound(mode.upSoundRes);
        }
    }

    private int repeatPressSoundRes(Mode mode) {
        return mode.downSoundRes != 0 ? mode.downSoundRes : mode.upSoundRes;
    }

    private float dragSoundThreshold() {
        return Math.max(dp(72), Math.min(screenWidth, screenHeight) * 0.2f);
    }

    private boolean usesNativeSoundCallbacks(Mode mode) {
        return mode.effectId == 8 || mode.effectId == 9;
    }

    private void handleEffectCallback(Object[] args) {
        if (args.length < 2 || !(args[0] instanceof Number) || !(args[1] instanceof Map)) {
            return;
        }
        if (((Number) args[0]).intValue() != 1) {
            return;
        }
        Object sound = ((Map<?, ?>) args[1]).get("sound");
        if (!(sound instanceof String)) {
            return;
        }
        Mode mode = MODES[modeIndex];
        if (!usesNativeSoundCallbacks(mode)) {
            return;
        }
        if ("down".equals(sound)) {
            if (forwardedAction == MotionEvent.ACTION_UP && forwardedReleaseTriggersUnlock) {
                return;
            }
            playModeSound(mode.downSoundRes);
        } else if ("drag".equals(sound)) {
            playModeSound(mode.dragSoundRes != 0 ? mode.dragSoundRes : mode.unlockSoundRes);
        }
    }

    private boolean shouldTriggerUnlock(MotionEvent event) {
        float dx = event.getX() - downX;
        float dy = event.getY() - downY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        return distance > dp(150) || dy < -dp(120);
    }

    private void triggerUnlockAnimation(Mode mode) {
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            if (mode.effectId == 10) {
                params.put("unlock", Boolean.TRUE);
            }
            handleCustomEvent.invoke(effectView, 2, params);
            Log.d(TAG, "unlock command sent to effect " + mode.effectId + " " + mode.name);
        } catch (Throwable t) {
            Log.d(TAG, "unlock command ignored by effect " + mode.effectId + " " + mode.name, t);
        }
    }

    private void preloadModeSounds() {
        for (Mode mode : MODES) {
            loadSound(mode.downSoundRes);
            loadSound(mode.dragSoundRes);
            loadSound(mode.upSoundRes);
            loadSound(mode.unlockSoundRes);
        }
    }

    private int loadSound(int resId) {
        if (resId == 0) {
            return 0;
        }
        Integer existing = loadedSounds.get(resId);
        if (existing != null) {
            return existing;
        }
        int soundId = soundPool.load(getContext(), resId, 1);
        loadedSounds.put(resId, soundId);
        return soundId;
    }

    private void playModeSound(int resId) {
        int soundId = loadSound(resId);
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        systemUiEffectView.clearEffect();
        recycleBitmap(displayWallpaperBitmap);
        recycleBitmap(wallpaperBitmap);
        soundPool.release();
    }

    private void switchMode() {
        long now = System.currentTimeMillis();
        if (now - lastSwitchAt < 220) {
            return;
        }
        lastSwitchAt = now;
        setOriginalEffect(modeIndex + 1);
    }

    private int drawableId(String name) {
        return getResources().getIdentifier(name, "drawable", getContext().getPackageName());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static Field getField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getField(name);
        field.setAccessible(true);
        return field;
    }

    private static void setInt(Class<?> owner, Object target, String name, int value) throws Exception {
        getField(owner, name).setInt(target, value);
    }

    private static void setIntIfFieldExists(Class<?> owner, Object target, String name, int value) throws Exception {
        try {
            getField(owner, name).setInt(target, value);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private static void setObjectIfFieldExists(Class<?> owner, Object target, String name, Object value) throws Exception {
        try {
            getField(owner, name).set(target, value);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private static final class Mode {
        final int effectId;
        final String name;
        final int downSoundRes;
        final int dragSoundRes;
        final int upSoundRes;
        final int unlockSoundRes;
        final int customEffectType;
        final String systemUiClassName;

        Mode(int effectId, String name, int downSoundRes, int upSoundRes, int unlockSoundRes) {
            this(effectId, name, downSoundRes, 0, upSoundRes, unlockSoundRes, LegacyCanvasEffectView.EFFECT_NONE, null);
        }

        Mode(int effectId, String name, int downSoundRes, int dragSoundRes, int upSoundRes, int unlockSoundRes) {
            this(effectId, name, downSoundRes, dragSoundRes, upSoundRes, unlockSoundRes, LegacyCanvasEffectView.EFFECT_NONE, null);
        }

        Mode(int effectId, String name, int downSoundRes, int upSoundRes, int unlockSoundRes, String systemUiClassName) {
            this(effectId, name, downSoundRes, 0, upSoundRes, unlockSoundRes, LegacyCanvasEffectView.EFFECT_NONE, systemUiClassName);
        }

        Mode(int effectId, String name, int downSoundRes, int dragSoundRes, int upSoundRes, int unlockSoundRes, int customEffectType, String systemUiClassName) {
            this.effectId = effectId;
            this.name = name;
            this.downSoundRes = downSoundRes;
            this.dragSoundRes = dragSoundRes;
            this.upSoundRes = upSoundRes;
            this.unlockSoundRes = unlockSoundRes;
            this.customEffectType = customEffectType;
            this.systemUiClassName = systemUiClassName;
        }

        boolean isCustom() {
            return customEffectType != LegacyCanvasEffectView.EFFECT_NONE;
        }

        boolean isSystemUiLegacy() {
            return systemUiClassName != null;
        }
    }
}
