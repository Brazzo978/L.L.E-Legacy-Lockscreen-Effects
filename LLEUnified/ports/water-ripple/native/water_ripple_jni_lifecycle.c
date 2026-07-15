#include "ripple_gles_pipeline.h"
#include "ripple_gles_overlay.h"

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <limits.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "LLE64Ripple"
#define ERROR_SIZE 512

/*
 * Samsung's ARM32 library exposes one process-global Fluid. Keep the same external
 * singleton semantics for now; S3Arm64RippleEffectView enforces one Java owner.
 */
static LleRippleGles g_gles;
static LleRippleOverlay g_overlay;
static bool g_initialized;
static bool g_overlay_initialized;
static char g_last_error[ERROR_SIZE];

static void clear_last_error(void) {
    g_last_error[0] = '\0';
}

static void set_last_error(const char *message) {
    if (message == NULL) {
        message = "unknown Water Ripple error";
    }
    (void) snprintf(g_last_error, sizeof(g_last_error), "%s", message);
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
}

static bool valid_texture_slot(jint slot) {
    return slot >= (jint) LLE_RIPPLE_TEXTURE_BACKGROUND
            && slot <= (jint) LLE_RIPPLE_TEXTURE_CAUSTIC_2;
}

JNIEXPORT jint JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeBridgeVersion(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    return 2;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeInitGpu(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    clear_last_error();

    if (g_overlay_initialized) {
        /* The caller guarantees that the owning context is current here. */
        lle_ripple_overlay_destroy(&g_overlay);
        g_overlay_initialized = false;
    }
    if (g_initialized) {
        /* The caller guarantees that the owning context is current here. */
        lle_ripple_gles_destroy(&g_gles);
        g_initialized = false;
    }
    if (!lle_ripple_gles_init(&g_gles, g_last_error, sizeof(g_last_error))) {
        if (g_last_error[0] == '\0') {
            set_last_error("lle_ripple_gles_init failed");
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
        }
        lle_ripple_gles_abandon(&g_gles);
        return JNI_FALSE;
    }
    g_initialized = true;
    if (!lle_ripple_overlay_init(&g_overlay, g_last_error, sizeof(g_last_error))) {
        if (g_last_error[0] == '\0') {
            set_last_error("lle_ripple_overlay_init failed");
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
        }
        lle_ripple_overlay_abandon(&g_overlay);
        lle_ripple_gles_destroy(&g_gles);
        g_initialized = false;
        return JNI_FALSE;
    }
    g_overlay_initialized = true;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeAbandonGpu(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    lle_ripple_overlay_abandon(&g_overlay);
    lle_ripple_gles_abandon(&g_gles);
    g_overlay_initialized = false;
    g_initialized = false;
    clear_last_error();
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeDestroyGpu(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    if (g_overlay_initialized) {
        lle_ripple_overlay_destroy(&g_overlay);
    } else {
        lle_ripple_overlay_abandon(&g_overlay);
    }
    if (g_initialized) {
        lle_ripple_gles_destroy(&g_gles);
    } else {
        lle_ripple_gles_abandon(&g_gles);
    }
    g_overlay_initialized = false;
    g_initialized = false;
    clear_last_error();
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeUploadBitmap(
        JNIEnv *env,
        jclass clazz,
        jint slot,
        jobject bitmap) {
    (void) clazz;
    clear_last_error();
    if (!g_initialized) {
        set_last_error("bitmap upload requested before GLES init");
        return JNI_FALSE;
    }
    if (!valid_texture_slot(slot) || bitmap == NULL) {
        set_last_error("invalid bitmap upload arguments");
        return JNI_FALSE;
    }

    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    int result = AndroidBitmap_getInfo(env, bitmap, &info);
    if (result != ANDROID_BITMAP_RESULT_SUCCESS) {
        char error[ERROR_SIZE];
        (void) snprintf(error, sizeof(error), "AndroidBitmap_getInfo failed: %d", result);
        set_last_error(error);
        return JNI_FALSE;
    }
    if (info.width == 0 || info.height == 0
            || info.width > (uint32_t) INT_MAX || info.height > (uint32_t) INT_MAX
            || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        set_last_error("bitmap must be non-empty software RGBA_8888");
        return JNI_FALSE;
    }

    const size_t tight_stride = (size_t) info.width * 4U;
    if (info.stride < tight_stride || (size_t) info.height > SIZE_MAX / tight_stride) {
        set_last_error("invalid bitmap stride or dimensions");
        return JNI_FALSE;
    }

    void *locked_pixels = NULL;
    result = AndroidBitmap_lockPixels(env, bitmap, &locked_pixels);
    if (result != ANDROID_BITMAP_RESULT_SUCCESS || locked_pixels == NULL) {
        char error[ERROR_SIZE];
        (void) snprintf(error, sizeof(error), "AndroidBitmap_lockPixels failed: %d", result);
        set_last_error(error);
        return JNI_FALSE;
    }

    const void *upload_pixels = locked_pixels;
    uint8_t *tight_pixels = NULL;
    if ((size_t) info.stride != tight_stride) {
        const size_t byte_count = tight_stride * (size_t) info.height;
        tight_pixels = (uint8_t *) malloc(byte_count);
        if (tight_pixels == NULL) {
            (void) AndroidBitmap_unlockPixels(env, bitmap);
            set_last_error("out of memory while normalizing bitmap stride");
            return JNI_FALSE;
        }
        const uint8_t *source = (const uint8_t *) locked_pixels;
        for (uint32_t row = 0; row < info.height; ++row) {
            memcpy(
                    tight_pixels + (size_t) row * tight_stride,
                    source + (size_t) row * (size_t) info.stride,
                    tight_stride);
        }
        upload_pixels = tight_pixels;
    }

    const bool uploaded = lle_ripple_gles_upload_rgba(
            &g_gles,
            (LleRippleTextureSlot) slot,
            (GLsizei) info.width,
            (GLsizei) info.height,
            upload_pixels,
            g_last_error,
            sizeof(g_last_error));
    free(tight_pixels);
    const int unlock_result = AndroidBitmap_unlockPixels(env, bitmap);
    if (!uploaded) {
        if (g_last_error[0] == '\0') {
            set_last_error("GLES bitmap upload failed");
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
        }
        return JNI_FALSE;
    }
    if (unlock_result != ANDROID_BITMAP_RESULT_SUCCESS) {
        char error[ERROR_SIZE];
        (void) snprintf(error, sizeof(error), "AndroidBitmap_unlockPixels failed: %d", unlock_result);
        set_last_error(error);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeFreeTexture(
        JNIEnv *env,
        jclass clazz,
        jint slot) {
    (void) env;
    (void) clazz;
    if (g_initialized && valid_texture_slot(slot)) {
        lle_ripple_gles_free_texture(&g_gles, (LleRippleTextureSlot) slot);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeRenderNormal(
        JNIEnv *env,
        jclass clazz,
        jfloatArray vertices_array,
        jfloatArray heights_array,
        jshortArray indices_array,
        jfloatArray mvp_array,
        jint viewport_width,
        jint viewport_height,
        jint mesh_width,
        jint mesh_height,
        jint detail_width,
        jint detail_height,
        jfloat refractive_index,
        jfloat reflection_ratio,
        jfloat alpha_ratio_1,
        jfloat alpha_ratio_2,
        jfloat fresnel_ratio,
        jfloat specular_ratio,
        jfloat exponent_ratio) {
    (void) clazz;
    clear_last_error();
    if (!g_initialized || !g_overlay_initialized
            || vertices_array == NULL || heights_array == NULL
            || indices_array == NULL || mvp_array == NULL
            || viewport_width <= 0 || viewport_height <= 0) {
        set_last_error("invalid normal render state");
        return JNI_FALSE;
    }

    const jsize vertex_count = (*env)->GetArrayLength(env, vertices_array);
    const jsize height_count = (*env)->GetArrayLength(env, heights_array);
    const jsize index_count = (*env)->GetArrayLength(env, indices_array);
    const jsize matrix_count = (*env)->GetArrayLength(env, mvp_array);
    if (vertex_count <= 0 || height_count <= 0 || index_count <= 0 || matrix_count < 16) {
        set_last_error("invalid normal render arrays");
        return JNI_FALSE;
    }

    jfloat *vertices = (*env)->GetFloatArrayElements(env, vertices_array, NULL);
    jfloat *heights = (*env)->GetFloatArrayElements(env, heights_array, NULL);
    jshort *indices = (*env)->GetShortArrayElements(env, indices_array, NULL);
    jfloat *mvp = (*env)->GetFloatArrayElements(env, mvp_array, NULL);
    if (vertices == NULL || heights == NULL || indices == NULL || mvp == NULL) {
        if (vertices != NULL) {
            (*env)->ReleaseFloatArrayElements(env, vertices_array, vertices, JNI_ABORT);
        }
        if (heights != NULL) {
            (*env)->ReleaseFloatArrayElements(env, heights_array, heights, JNI_ABORT);
        }
        if (indices != NULL) {
            (*env)->ReleaseShortArrayElements(env, indices_array, indices, JNI_ABORT);
        }
        if (mvp != NULL) {
            (*env)->ReleaseFloatArrayElements(env, mvp_array, mvp, JNI_ABORT);
        }
        set_last_error("JNI array acquisition failed");
        return JNI_FALSE;
    }

    LleRippleRenderArgs args;
    memset(&args, 0, sizeof(args));
    args.vertices = vertices;
    args.heights = heights;
    args.indices = (const uint16_t *) indices;
    args.vertex_float_count = (GLsizei) vertex_count;
    args.height_float_count = (GLsizei) height_count;
    args.index_count = (GLsizei) index_count;
    args.mvp = mvp;
    args.viewport_width = (GLsizei) viewport_width;
    args.viewport_height = (GLsizei) viewport_height;
    args.mesh_width = (GLint) mesh_width;
    args.mesh_height = (GLint) mesh_height;
    args.detail_width = (GLint) detail_width;
    args.detail_height = (GLint) detail_height;
    args.refractive_index = refractive_index;
    args.reflection_ratio = reflection_ratio;
    args.alpha_ratio_1 = alpha_ratio_1;
    args.alpha_ratio_2 = alpha_ratio_2;
    args.fresnel_ratio = fresnel_ratio;
    args.specular_ratio = specular_ratio;
    args.exponent_ratio = exponent_ratio;
    args.with_ink = false;

    LleRippleOverlayOptions overlay_options;
    lle_ripple_overlay_default_options(&overlay_options);
    const bool rendered = lle_ripple_gles_render_normal_variant(
            &g_gles,
            &g_overlay,
            LLE_RIPPLE_NORMAL_COMPOSITE_TRANSPARENT_DELTA,
            &args,
            &overlay_options,
            g_last_error,
            sizeof(g_last_error));
    (*env)->ReleaseFloatArrayElements(env, vertices_array, vertices, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, heights_array, heights, JNI_ABORT);
    (*env)->ReleaseShortArrayElements(env, indices_array, indices, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, mvp_array, mvp, JNI_ABORT);

    if (!rendered) {
        if (g_last_error[0] == '\0') {
            set_last_error("transparent normal Water Ripple render failed");
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
        }
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeGetLastError(
        JNIEnv *env,
        jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, g_last_error);
}
