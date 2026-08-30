#include "lle_spark_gles.h"
#include "lle_spark_sim.h"

#include <android/log.h>
#include <jni.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LLE_SPARK_LOG_TAG "LLESparklingBubbles"
#define LLE_SPARK_BRIDGE_VERSION 2
#define LLE_SPARK_HANDLE_MAGIC UINT64_C(0x4c4c45535041524b)
#define LLE_SPARK_DEFAULT_WIDTH 1440
#define LLE_SPARK_DEFAULT_HEIGHT 2560
#define LLE_SPARK_TARGET_SECONDS_PER_TICK (1.0 / 60.0)
#define LLE_SPARK_MAX_TICKS_PER_STEP 8
#define LLE_SPARK_MAX_ELAPSED_SECONDS 0.25
/* 30 Hz plus ordinary compositor jitter; the host discards longer stalls. */
#define LLE_SPARK_MAX_ADAPTIVE_ELAPSED_SECONDS (1.0 / 28.0)

typedef struct LleSparkHandle {
    uint64_t magic;
    LleSparkSim *sim;
    LleSparkGles gles;
    double accumulator_seconds;
    bool adaptive_physics;
    int width;
    int height;
    char error[LLE_SPARK_ERROR_SIZE];
} LleSparkHandle;

static LleSparkHandle *spark_handle(jlong value) {
    if (value == 0) return NULL;
    LleSparkHandle *handle = (LleSparkHandle *) (intptr_t) value;
    return handle->magic == LLE_SPARK_HANDLE_MAGIC ? handle : NULL;
}

static void spark_log_error(LleSparkHandle *handle, const char *message) {
    if (handle == NULL || message == NULL) return;
    (void) snprintf(handle->error, sizeof(handle->error), "%s", message);
    __android_log_print(ANDROID_LOG_ERROR, LLE_SPARK_LOG_TAG, "%s", handle->error);
}

static void spark_clear_handle_error(LleSparkHandle *handle) {
    if (handle != NULL) handle->error[0] = '\0';
}

static bool spark_require_handle(LleSparkHandle *handle) {
    return handle != NULL && handle->sim != NULL;
}

JNIEXPORT jint JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeBridgeVersion(
        JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return LLE_SPARK_BRIDGE_VERSION;
}

JNIEXPORT jlong JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeCreate(
        JNIEnv *env, jclass clazz, jlong seed) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = (LleSparkHandle *) calloc(1U, sizeof(*handle));
    if (handle == NULL) return 0;
    handle->width = LLE_SPARK_DEFAULT_WIDTH;
    handle->height = LLE_SPARK_DEFAULT_HEIGHT;
    handle->sim = lle_spark_sim_create(
            (float) handle->width, (float) handle->height, (uint64_t) seed);
    if (handle->sim == NULL) {
        free(handle);
        return 0;
    }
    handle->magic = LLE_SPARK_HANDLE_MAGIC;
    return (jlong) (intptr_t) handle;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeInitGpu(
        JNIEnv *env, jclass clazz, jlong native_handle, jint width, jint height) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle) || width <= 0 || height <= 0) {
        spark_log_error(handle, "nativeInitGpu received invalid handle or dimensions");
        return JNI_FALSE;
    }
    spark_clear_handle_error(handle);
    handle->width = width;
    handle->height = height;
    lle_spark_sim_set_surface(handle->sim, (float) width, (float) height);
    if (!lle_spark_gles_init(
            &handle->gles,
            width,
            height,
            handle->error,
            sizeof(handle->error))) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_SPARK_LOG_TAG,
                "GLES init failed: %s",
                handle->error);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeAbandonGpu(
        JNIEnv *env, jclass clazz, jlong native_handle) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return;
    lle_spark_gles_abandon(&handle->gles);
    spark_clear_handle_error(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeDestroy(
        JNIEnv *env, jclass clazz, jlong native_handle) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return;
    if (handle->gles.ready) lle_spark_gles_destroy(&handle->gles);
    else {
        /* Abandoned GLES state retains only renderer-side CPU arrays. */
        lle_spark_gles_destroy(&handle->gles);
    }
    lle_spark_sim_destroy(handle->sim);
    handle->sim = NULL;
    handle->magic = 0U;
    free(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeUploadBitmap(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint slot,
        jobject bitmap) {
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return JNI_FALSE;
    spark_clear_handle_error(handle);
    if (!lle_spark_gles_upload_bitmap(
            &handle->gles,
            env,
            slot,
            bitmap,
            handle->error,
            sizeof(handle->error))) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_SPARK_LOG_TAG,
                "Bitmap upload failed: %s",
                handle->error);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeClearBitmap(
        JNIEnv *env, jclass clazz, jlong native_handle, jint slot) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return;
    lle_spark_gles_clear_bitmap(&handle->gles, slot);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeReset(
        JNIEnv *env, jclass clazz, jlong native_handle) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return;
    lle_spark_sim_reset(handle->sim);
    handle->accumulator_seconds = 0.0;
    spark_clear_handle_error(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeTouch(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint action,
        jfloat x,
        jfloat y,
        jlong event_time_ms) {
    (void) env;
    (void) clazz;
    (void) event_time_ms;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return;
    const float render_y = (float) handle->height - y;
    if (action == 0) {
        (void) lle_spark_sim_touch_begin(handle->sim, x, render_y);
    } else if (action == 2) {
        if (handle->adaptive_physics) {
            (void) lle_spark_sim_touch_move_adaptive(handle->sim, x, render_y);
        } else {
            (void) lle_spark_sim_touch_move(handle->sim, x, render_y);
        }
    } else if (action == 1 || action == 3) {
        lle_spark_sim_touch_end(handle->sim);
    }
}

JNIEXPORT void JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeAffordance(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint left,
        jint top,
        jint right,
        jint bottom) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return;
    const float center_x = ((float) left + (float) right) * 0.5f;
    const float android_center_y = ((float) top + (float) bottom) * 0.5f;
    const float center_y = (float) handle->height - android_center_y;
    (void) lle_spark_sim_hint(handle->sim, center_x, center_y);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeUnlock(
        JNIEnv *env, jclass clazz, jlong native_handle) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return;
    lle_spark_sim_unlock(handle->sim);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeSetAdaptivePhysics(
        JNIEnv *env, jclass clazz, jlong native_handle, jboolean enabled) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return;
    const bool requested = enabled == JNI_TRUE;
    if (handle->adaptive_physics != requested) {
        handle->adaptive_physics = requested;
        handle->accumulator_seconds = 0.0;
    }
    spark_clear_handle_error(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeStep(
        JNIEnv *env, jclass clazz, jlong native_handle, jfloat elapsed_seconds) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return JNI_FALSE;
    if (!isfinite(elapsed_seconds) || elapsed_seconds < 0.0f) {
        spark_log_error(handle, "nativeStep received invalid elapsedSeconds");
        return JNI_FALSE;
    }
    const double bounded = fmin(
            (double) elapsed_seconds, LLE_SPARK_MAX_ELAPSED_SECONDS);
    handle->adaptive_physics = false;
    handle->accumulator_seconds += bounded;
    int tick_count = 0;
    /*
     * Intentional L.L.E cadence: retain each recovered full simulation tick
     * at the selected app-owned simulation cadence.
     */
    while (handle->accumulator_seconds >= LLE_SPARK_TARGET_SECONDS_PER_TICK
            && tick_count < LLE_SPARK_MAX_TICKS_PER_STEP) {
        lle_spark_sim_tick(handle->sim);
        handle->accumulator_seconds -= LLE_SPARK_TARGET_SECONDS_PER_TICK;
        ++tick_count;
    }
    if (handle->accumulator_seconds >= LLE_SPARK_TARGET_SECONDS_PER_TICK) {
        handle->accumulator_seconds =
                fmod(handle->accumulator_seconds,
                     LLE_SPARK_TARGET_SECONDS_PER_TICK);
    }
    spark_clear_handle_error(handle);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeStepAdaptive(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jfloat elapsed_seconds,
        jfloat speed_multiplier) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return JNI_FALSE;
    if (!isfinite(elapsed_seconds) || elapsed_seconds < 0.0f) {
        spark_log_error(handle, "nativeStepAdaptive received invalid elapsedSeconds");
        return JNI_FALSE;
    }
    if (!isfinite(speed_multiplier)
            || speed_multiplier < 1.0f || speed_multiplier > 2.0f) {
        spark_log_error(handle, "nativeStepAdaptive received invalid speedMultiplier");
        return JNI_FALSE;
    }

    /*
     * The Java host discards a stalled frame entirely.  Keep the same bound
     * here because JNI callers must not be able to create a catch-up jump.
     */
    const double bounded = fmin(
            (double) elapsed_seconds, LLE_SPARK_MAX_ADAPTIVE_ELAPSED_SECONDS);
    handle->adaptive_physics = true;
    handle->accumulator_seconds = 0.0;
    lle_spark_sim_advance_adaptive(
            handle->sim,
            /* Bound wall time before speed scaling; a 30 Hz frame at 2x is
             * intentionally four logical frames, never compositor catch-up. */
            fminf(4.0f, (float) (bounded / LLE_SPARK_TARGET_SECONDS_PER_TICK)
                    * speed_multiplier));
    spark_clear_handle_error(handle);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeDraw(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint width,
        jint height) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle) || width <= 0 || height <= 0) {
        spark_log_error(handle, "nativeDraw received invalid handle or dimensions");
        return JNI_FALSE;
    }
    if (handle->width != width || handle->height != height) {
        handle->width = width;
        handle->height = height;
        lle_spark_sim_set_surface(handle->sim, (float) width, (float) height);
    }
    spark_clear_handle_error(handle);
    const float presentation_fraction = handle->adaptive_physics
            ? 0.0f
            : (float) fmax(
                    0.0,
                    fmin(1.0,
                         handle->accumulator_seconds /
                                  LLE_SPARK_TARGET_SECONDS_PER_TICK));
    if (!lle_spark_gles_draw(
            &handle->gles,
            handle->sim,
            presentation_fraction,
            width,
            height,
            handle->error,
            sizeof(handle->error))) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_SPARK_LOG_TAG,
                "Draw failed: %s",
                handle->error);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeIsIdle(
        JNIEnv *env, jclass clazz, jlong native_handle) {
    (void) env;
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    if (!spark_require_handle(handle)) return JNI_TRUE;
    return lle_spark_sim_active_particle_count(handle->sim) == 0U
            ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_codex_lle_SparklingBubblesNative_nativeGetLastError(
        JNIEnv *env, jclass clazz, jlong native_handle) {
    (void) clazz;
    LleSparkHandle *handle = spark_handle(native_handle);
    const char *message = handle != NULL
            ? handle->error : "Invalid Sparkling Bubbles native handle";
    return (*env)->NewStringUTF(env, message);
}
