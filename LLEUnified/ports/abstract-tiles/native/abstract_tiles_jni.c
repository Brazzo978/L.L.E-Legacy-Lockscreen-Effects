#include "abstract_tiles_internal.h"

#include <android/log.h>
#include <jni.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#define AT_LOG_TAG "LLE64AbstractTiles"
#define AT_BRIDGE_VERSION 1
#define AT_ERROR_SIZE 512

static AtScene g_scene;
static AtGles g_gles;
static char g_error[AT_ERROR_SIZE];

static void at_jni_error(const char *format, ...) {
    va_list args;
    va_start(args, format);
    (void) vsnprintf(g_error, sizeof(g_error), format, args);
    va_end(args);
    __android_log_print(ANDROID_LOG_ERROR, AT_LOG_TAG, "%s", g_error);
}

static void at_jni_clear_error(void) {
    g_error[0] = '\0';
}

JNIEXPORT jint JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeBridgeVersion(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return AT_BRIDGE_VERSION;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeInitGpu(
        JNIEnv *env, jclass clazz, jint width, jint height) {
    (void) env;
    (void) clazz;
    if (width <= 0 || height <= 0) {
        at_jni_error("Invalid surface size %dx%d", width, height);
        return JNI_FALSE;
    }
    at_jni_clear_error();
    at_scene_init(&g_scene, width, height);
    /* A resize can re-enter init in the same current context. Reclaim that generation. */
    if (g_gles.ready) at_gles_destroy(&g_gles);
    if (!at_gles_init(&g_gles, g_error, sizeof(g_error))) {
        __android_log_print(ANDROID_LOG_ERROR, AT_LOG_TAG, "GLES init: %s", g_error);
        return JNI_FALSE;
    }
    __android_log_print(
            ANDROID_LOG_INFO,
            AT_LOG_TAG,
            "Initialized %dx%d triangles=%d",
            width,
            height,
            g_scene.triangle_count);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeAbandonGpu(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    at_gles_abandon(&g_gles);
    at_jni_clear_error();
}

JNIEXPORT void JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeDestroyGpu(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    /* This API is also legal after the GL thread/context has already disappeared. */
    at_gles_abandon(&g_gles);
    memset(&g_scene, 0, sizeof(g_scene));
    at_jni_clear_error();
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeUploadBitmap(
        JNIEnv *env, jclass clazz, jint slot, jobject bitmap) {
    (void) clazz;
    if (!at_gles_upload_bitmap(&g_gles, env, slot, bitmap, g_error, sizeof(g_error))) {
        __android_log_print(ANDROID_LOG_ERROR, AT_LOG_TAG, "Bitmap upload: %s", g_error);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeClearBitmap(
        JNIEnv *env, jclass clazz, jint slot) {
    (void) env;
    (void) clazz;
    at_gles_clear_bitmap(&g_gles, slot);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeReset(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    at_scene_reset(&g_scene);
    at_jni_clear_error();
}

JNIEXPORT void JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeTouch(
        JNIEnv *env,
        jclass clazz,
        jint action,
        jfloat x,
        jfloat y,
        jlong event_time_ms) {
    (void) env;
    (void) clazz;
    at_scene_touch(&g_scene, action, x, y, (int64_t) event_time_ms);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeRealign(
        JNIEnv *env, jclass clazz, jfloat x, jfloat y, jlong event_time_ms) {
    (void) env;
    (void) clazz;
    (void) event_time_ms;
    at_scene_realign(&g_scene, x, y);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeAffordance(
        JNIEnv *env,
        jclass clazz,
        jint left,
        jint top,
        jint right,
        jint bottom) {
    (void) env;
    (void) clazz;
    at_scene_affordance(&g_scene, left, top, right, bottom);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeUnlock(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    at_scene_unlock(&g_scene);
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeStep(
        JNIEnv *env, jclass clazz, jfloat elapsed_seconds) {
    (void) env;
    (void) clazz;
    if (!at_scene_step(&g_scene, elapsed_seconds)) {
        at_jni_error("Invalid simulation step %.9g", (double) elapsed_seconds);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeDraw(
        JNIEnv *env, jclass clazz, jint width, jint height) {
    (void) env;
    (void) clazz;
    if (width <= 0 || height <= 0) {
        at_jni_error("Invalid draw size %dx%d", width, height);
        return JNI_FALSE;
    }
    if (g_scene.width != width || g_scene.height != height) {
        at_scene_init(&g_scene, width, height);
    }
    if (!at_gles_draw(&g_gles, &g_scene, width, height, g_error, sizeof(g_error))) {
        __android_log_print(ANDROID_LOG_ERROR, AT_LOG_TAG, "Draw: %s", g_error);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeIsIdle(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return at_scene_is_idle(&g_scene) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_codex_lle_AbstractTilesNative_nativeGetLastError(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, g_error);
}
