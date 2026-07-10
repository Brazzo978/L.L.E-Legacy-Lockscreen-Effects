package com.codex.s4unlockfx;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.reflect.Method;

public class SystemUiLegacyEffectView extends FrameLayout {
    private static final String TAG = "SystemUiLegacyEffect";

    private Object effect;
    private View effectAsView;
    private Method handleTouchEvent;
    private Method handleUnlock;
    private Method show;
    private Method cleanUp;
    private Method reset;
    private String effectClassName;

    public SystemUiLegacyEffectView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public boolean setEffectClassName(String className) {
        if (className != null && className.equals(effectClassName) && effect != null) {
            setVisibility(VISIBLE);
            return true;
        }
        clearEffect();
        effectClassName = className;
        if (className == null) {
            return false;
        }
        try {
            Class<?> effectClass = Class.forName(className);
            effect = effectClass.getConstructor(Context.class).newInstance(getContext());
            effectAsView = (View) effect;
            handleTouchEvent = effectClass.getMethod("handleTouchEvent", View.class, MotionEvent.class);
            handleUnlock = effectClass.getMethod("handleUnlock", View.class, MotionEvent.class);
            show = effectClass.getMethod("show");
            cleanUp = effectClass.getMethod("cleanUp");
            reset = effectClass.getMethod("reset");
            addView(effectAsView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            invokeNoArgs(show);
            setVisibility(VISIBLE);
            Log.i(TAG, "Loaded SystemUI effect " + className);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load SystemUI effect " + className, t);
            clearEffect();
            return false;
        }
    }

    public boolean onHostTouchEvent(MotionEvent event) {
        if (effect == null || handleTouchEvent == null) {
            return false;
        }
        try {
            Object result = handleTouchEvent.invoke(effect, this, event);
            return result instanceof Boolean ? (Boolean) result : true;
        } catch (Throwable t) {
            Log.e(TAG, "SystemUI touch forwarding failed", t);
            return false;
        }
    }

    public void onHostUnlock(MotionEvent event) {
        if (effect == null || handleUnlock == null) {
            return;
        }
        try {
            handleUnlock.invoke(effect, this, event);
        } catch (Throwable t) {
            Log.d(TAG, "SystemUI unlock forwarding ignored", t);
        }
    }

    public void clearEffect() {
        invokeNoArgs(reset);
        invokeNoArgs(cleanUp);
        removeAllViews();
        effect = null;
        effectAsView = null;
        handleTouchEvent = null;
        handleUnlock = null;
        show = null;
        cleanUp = null;
        reset = null;
        effectClassName = null;
        setVisibility(GONE);
    }

    private void invokeNoArgs(Method method) {
        if (effect == null || method == null) {
            return;
        }
        try {
            method.invoke(effect);
        } catch (Throwable t) {
            Log.d(TAG, "SystemUI method ignored: " + method.getName(), t);
        }
    }
}
