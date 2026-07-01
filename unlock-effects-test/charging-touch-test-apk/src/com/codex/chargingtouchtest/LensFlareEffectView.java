package com.codex.chargingtouchtest;

import android.content.Context;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

public class LensFlareEffectView extends FrameLayout {
    private static final String TAG = "ChargingS4LensFlare";
    private static final int EFFECT_LENS_FLARE_S4 = 11;
    private static final int CMD_UNLOCK = 2;
    private static final int CMD_LENS_FLARE = 3;
    private static final long STARTUP_RETRY_DELAY_MS = 180L;
    private static final long CHILD_LAYOUT_RETRY_DELAY_MS = 90L;

    private Object effectView;
    private Method handleTouchEvent;
    private Method handleCustomEvent;
    private Method clearScreen;
    private final Runnable startupRetry = new Runnable() {
        @Override
        public void run() {
            sendStartupCommands();
        }
    };
    private final Runnable initRunnable = new Runnable() {
        @Override
        public void run() {
            ensureReady();
        }
    };
    private boolean ready;
    private boolean touchDownSent;
    private boolean childLayoutLogged;
    private float lastX;
    private float lastY;
    private long downTime;

    public LensFlareEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(initRunnable);
    }

    public void beginGesture(float screenX, float screenY) {
        lastX = screenX;
        lastY = screenY;
        if (!ensureReady()) {
            return;
        }
        downTime = SystemClockCompat.uptimeMillis();
        touchDownSent = sendTouch(MotionEvent.ACTION_DOWN, screenX, screenY);
    }

    public void updateGesture(float screenX, float screenY) {
        lastX = screenX;
        lastY = screenY;
        if (!touchDownSent) {
            beginGesture(screenX, screenY);
            return;
        }
        if (!ensureReady()) {
            return;
        }
        sendTouch(MotionEvent.ACTION_MOVE, screenX, screenY);
    }

    public void finishGesture(boolean completed) {
        if (!touchDownSent || !ensureReady()) {
            touchDownSent = false;
            return;
        }
        sendTouch(MotionEvent.ACTION_UP, lastX, lastY);
        touchDownSent = false;
        if (completed) {
            sendUnlockCommand();
        }
    }

    public void cancelGesture() {
        if (!touchDownSent || !ensureReady()) {
            touchDownSent = false;
            return;
        }
        sendTouch(MotionEvent.ACTION_CANCEL, lastX, lastY);
        touchDownSent = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (ready && clearScreen != null && effectView != null) {
            try {
                clearScreen.invoke(effectView);
            } catch (Throwable t) {
                Log.d(TAG, "Samsung lens flare clear ignored", t);
            }
        }
        removeCallbacks(startupRetry);
        removeCallbacks(initRunnable);
        super.onDetachedFromWindow();
    }

    private boolean ensureReady() {
        if (ready) {
            return true;
        }
        if (getWidth() <= 0 || getHeight() <= 0) {
            Log.d(TAG, "waiting for layout before Samsung lens flare init");
            return false;
        }
        ready = initOriginalSamsungLensFlare(getContext());
        return ready;
    }

    private boolean initOriginalSamsungLensFlare(Context context) {
        try {
            Class<?> effectViewClass = Class.forName("com.samsung.android.visualeffect.EffectView");
            Class<?> dataClass = Class.forName("com.samsung.android.visualeffect.EffectDataObj");
            Class<?> lensDataClass = Class.forName(
                    "com.samsung.android.visualeffect.lock.data.LensFlareData");

            effectView = effectViewClass.getConstructor(Context.class).newInstance(context);
            handleTouchEvent = effectViewClass.getMethod(
                    "handleTouchEvent", MotionEvent.class, View.class);
            handleCustomEvent = effectViewClass.getMethod(
                    "handleCustomEvent", int.class, HashMap.class);
            clearScreen = effectViewClass.getMethod("clearScreen");
            Method setEffect = effectViewClass.getMethod("setEffect", int.class);
            Method init = effectViewClass.getMethod("init", dataClass);
            Method setEffectData = dataClass.getMethod("setEffect", int.class);

            Object data = dataClass.getConstructor().newInstance();
            setEffectData.invoke(data, EFFECT_LENS_FLARE_S4);
            Object lensData = getField(dataClass, data, "lensFlareData");
            if (lensData == null) {
                lensData = lensDataClass.getConstructor().newInstance();
                setField(dataClass, data, "lensFlareData", lensData);
            }
            prepareLensFlareData(lensDataClass, lensData);

            setEffect.invoke(effectView, EFFECT_LENS_FLARE_S4);
            init.invoke(effectView, data);
            View effectViewAsView = (View) effectView;
            effectViewAsView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            if (effectViewAsView instanceof ViewGroup) {
                ((ViewGroup) effectViewAsView).setClipChildren(false);
                ((ViewGroup) effectViewAsView).setClipToPadding(false);
            }
            addView(effectViewAsView, new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            sendStartupCommands();
            postDelayed(startupRetry, STARTUP_RETRY_DELAY_MS);
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    logEffectTree("post_init");
                }
            }, CHILD_LAYOUT_RETRY_DELAY_MS);
            Log.i(TAG, "original Samsung S4 lens flare loaded width=" + getWidth()
                    + " height=" + getHeight());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "original Samsung S4 lens flare unavailable", t);
            effectView = null;
            handleTouchEvent = null;
            handleCustomEvent = null;
            clearScreen = null;
            return false;
        }
    }

    private void prepareLensFlareData(Class<?> lensDataClass, Object lensData) throws Exception {
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
        setInt(lensDataClass, lensData, "tapSound", R.raw.lens_flare_tap);
        setInt(lensDataClass, lensData, "unlockSound", R.raw.lens_flare_unlock);
    }

    private boolean sendTouch(int action, float screenX, float screenY) {
        try {
            if (!areSamsungChildrenLaidOut()) {
                requestLayout();
                postInvalidate();
                Log.d(TAG, "waiting for Samsung child layout before touch action=" + action
                        + " tree=" + describeEffectTree());
                postDelayed(initRunnable, CHILD_LAYOUT_RETRY_DELAY_MS);
                return false;
            }
            long now = SystemClockCompat.uptimeMillis();
            long eventDownTime = downTime == 0L ? now : downTime;
            MotionEvent event = MotionEvent.obtain(eventDownTime, now, action, screenX, screenY, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            handleTouchEvent.invoke(effectView, event, this);
            int[] viewLocation = new int[2];
            getLocationOnScreen(viewLocation);
            Log.i(TAG, "touch action=" + action
                    + " screen=" + Math.round(screenX) + "," + Math.round(screenY)
                    + " viewLoc=" + viewLocation[0] + "," + viewLocation[1]
                    + " view=" + getWidth() + "x" + getHeight()
                    + " tree=" + describeEffectTree());
            event.recycle();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Samsung lens flare touch forwarding failed action=" + action
                    + " cause=" + rootCauseMessage(t));
            return false;
        }
    }

    private boolean areSamsungChildrenLaidOut() {
        if (!(effectView instanceof View)) {
            return false;
        }
        View effectViewAsView = (View) effectView;
        if (effectViewAsView.getWidth() <= 0 || effectViewAsView.getHeight() <= 0) {
            return false;
        }
        return hasLaidOutImageViewBlended(effectViewAsView);
    }

    private boolean hasLaidOutImageViewBlended(View view) {
        String className = view.getClass().getName();
        if (className.contains("ImageViewBlended")) {
            return view.getWidth() > 0 && view.getHeight() > 0;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (hasLaidOutImageViewBlended(group.getChildAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void logEffectTree(String reason) {
        if (childLayoutLogged && areSamsungChildrenLaidOut()) {
            return;
        }
        childLayoutLogged = areSamsungChildrenLaidOut();
        Log.i(TAG, "effect tree " + reason + " ready=" + childLayoutLogged
                + " tree=" + describeEffectTree());
        if (!childLayoutLogged) {
            requestLayout();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    logEffectTree("retry_layout");
                }
            }, CHILD_LAYOUT_RETRY_DELAY_MS);
        }
    }

    private String describeEffectTree() {
        if (!(effectView instanceof View)) {
            return "no-effect-view";
        }
        StringBuilder builder = new StringBuilder();
        describeView((View) effectView, builder, 0);
        return builder.toString();
    }

    private void describeView(View view, StringBuilder builder, int depth) {
        if (builder.length() > 420) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" | ");
        }
        String name = view.getClass().getSimpleName();
        if (name == null || name.length() == 0) {
            name = view.getClass().getName();
        }
        builder.append(depth)
                .append(":")
                .append(name)
                .append("@")
                .append(Math.round(view.getX()))
                .append(",")
                .append(Math.round(view.getY()))
                .append(" ")
                .append(view.getWidth())
                .append("x")
                .append(view.getHeight())
                .append(" v=")
                .append(view.getVisibility());
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        int childCount = Math.min(group.getChildCount(), 6);
        for (int i = 0; i < childCount; i++) {
            describeView(group.getChildAt(i), builder, depth + 1);
        }
    }

    private void sendStartupCommands() {
        sendLensFlareCommand("manualInit");
        sendLensFlareCommand("show");
    }

    private void sendLensFlareCommand(String command) {
        try {
            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put(command, Boolean.TRUE);
            handleCustomEvent.invoke(effectView, CMD_LENS_FLARE, params);
        } catch (Throwable t) {
            Log.d(TAG, "Samsung lens flare command ignored: " + command, t);
        }
    }

    private void sendUnlockCommand() {
        try {
            handleCustomEvent.invoke(effectView, CMD_UNLOCK, new HashMap<String, Object>());
            Log.i(TAG, "Samsung lens flare unlock command sent");
        } catch (Throwable t) {
            Log.d(TAG, "Samsung lens flare unlock ignored", t);
        }
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName()
                + (message == null ? "" : ": " + message);
    }

    private int drawableId(String name) {
        int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
        if (id == 0) {
            throw new IllegalStateException("Missing drawable " + name);
        }
        return id;
    }

    private static Object getField(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getField(name);
        return field.get(target);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value)
            throws Exception {
        Field field = owner.getField(name);
        field.set(target, value);
    }

    private static void setInt(Class<?> owner, Object target, String name, int value)
            throws Exception {
        Field field = owner.getField(name);
        field.setInt(target, value);
    }

    private static final class SystemClockCompat {
        private SystemClockCompat() {
        }

        static long uptimeMillis() {
            return android.os.SystemClock.uptimeMillis();
        }
    }
}
