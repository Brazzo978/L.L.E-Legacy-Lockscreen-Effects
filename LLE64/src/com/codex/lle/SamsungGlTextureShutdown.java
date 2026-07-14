package com.codex.lle;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class SamsungGlTextureShutdown {
    private SamsungGlTextureShutdown() {
    }

    static void shutdown(View root, String tag) {
        shutdownRecursive(root, tag);
    }

    private static void shutdownRecursive(View view, String tag) {
        if (view == null) {
            return;
        }
        if (isSamsungGlTextureView(view)) {
            invokeNoArg(view, "onPause", tag);
            requestExitAndWait(view, tag);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                shutdownRecursive(group.getChildAt(i), tag);
            }
        }
    }

    private static boolean isSamsungGlTextureView(View view) {
        Class<?> type = view.getClass();
        while (type != null) {
            if ("com.samsung.android.visualeffect.common.GLTextureView".equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static void requestExitAndWait(View target, String tag) {
        try {
            Field field = findField(target.getClass(), "mGLThread");
            if (field == null) {
                Log.d(tag, "Samsung GLTextureView mGLThread not found");
                return;
            }
            field.setAccessible(true);
            Object glThread = field.get(target);
            if (glThread == null) {
                Log.d(tag, "Samsung GLTextureView GLThread already null");
                return;
            }
            if (glThread == Thread.currentThread()) {
                Log.w(tag, "Samsung GLTextureView requestExitAndWait skipped on GLThread");
                return;
            }
            Method method = glThread.getClass().getDeclaredMethod("requestExitAndWait");
            method.setAccessible(true);
            method.invoke(glThread);
            Log.i(tag, "Samsung GLTextureView requestExitAndWait sent");
        } catch (Throwable t) {
            Log.d(tag, "Samsung GLTextureView requestExitAndWait ignored", t);
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void invokeNoArg(View target, String methodName, String tag) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.invoke(target);
            Log.i(tag, "Samsung GLTextureView " + methodName + " sent");
        } catch (Throwable t) {
            Log.d(tag, "Samsung GLTextureView " + methodName + " ignored", t);
        }
    }
}
