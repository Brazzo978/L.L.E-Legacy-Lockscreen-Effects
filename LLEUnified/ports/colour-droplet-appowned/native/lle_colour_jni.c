#include "lle_colour_gles.h"
#include "lle_colour_sim.h"

#include <android/log.h>
#include <jni.h>
#include <math.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LLE_COLOUR_LOG_TAG "LLEColourDroplet"
#define LLE_COLOUR_BRIDGE_VERSION 1
#define LLE_COLOUR_HANDLE_MAGIC UINT64_C(0x4c4c45434f4c4f52)
#define LLE_COLOUR_DEFAULT_WIDTH 1440
#define LLE_COLOUR_DEFAULT_HEIGHT 2560
#define LLE_COLOUR_FIXED_SECONDS (1.0 / (double) LLE_COLOUR_TICK_HZ)
#define LLE_COLOUR_MAX_TICKS_PER_STEP 8
#define LLE_COLOUR_MAX_ELAPSED_SECONDS 0.25

typedef struct LleColourHandle {
    uint64_t magic;
    LleColourSim *sim;
    LleColourGles gles;
    LleColourDrawParticle *draw_particles;
    size_t draw_particle_capacity;
    double accumulator_seconds;
    int project_kind;
    int width;
    int height;
    int logical_width;
    int logical_height;
    pthread_t gl_thread;
    bool gl_thread_bound;
    char error[LLE_COLOUR_ERROR_SIZE];
} LleColourHandle;

static LleColourHandle *colour_handle(jlong value) {
    if (value == 0) {
        return NULL;
    }
    LleColourHandle *handle = (LleColourHandle *) (intptr_t) value;
    return handle->magic == LLE_COLOUR_HANDLE_MAGIC ? handle : NULL;
}

static bool colour_require_handle(const LleColourHandle *handle) {
    return handle != NULL && handle->sim != NULL;
}

static void colour_clear_error(LleColourHandle *handle) {
    if (handle != NULL) {
        handle->error[0] = '\0';
    }
}

static void colour_set_error(LleColourHandle *handle, const char *message) {
    if (message == NULL) {
        message = "Unknown native error";
    }
    if (handle != NULL) {
        (void) snprintf(handle->error, sizeof(handle->error), "%s", message);
        __android_log_print(
                ANDROID_LOG_ERROR, LLE_COLOUR_LOG_TAG, "%s", handle->error);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, LLE_COLOUR_LOG_TAG, "%s", message);
    }
}

static void colour_log_component_error(
        LleColourHandle *handle,
        const char *operation) {
    if (handle == NULL) {
        return;
    }
    handle->error[sizeof(handle->error) - 1U] = '\0';
    __android_log_print(
            ANDROID_LOG_ERROR,
            LLE_COLOUR_LOG_TAG,
            "%s failed: %s",
            operation,
            handle->error[0] != '\0' ? handle->error : "unknown error");
}

static void colour_bind_gl_thread(LleColourHandle *handle) {
    if (handle == NULL) {
        return;
    }
    handle->gl_thread = pthread_self();
    handle->gl_thread_bound = true;
}

static bool colour_require_gl_thread(
        LleColourHandle *handle,
        const char *operation) {
    if (!colour_require_handle(handle)) {
        return false;
    }
    if (!handle->gl_thread_bound
            || !pthread_equal(handle->gl_thread, pthread_self())) {
        char message[160];
        (void) snprintf(
                message,
                sizeof(message),
                "%s must run on the owning GL thread",
                operation != NULL ? operation : "Native operation");
        colour_set_error(handle, message);
        return false;
    }
    return true;
}

static bool colour_resize_particle_buffer(
        LleColourHandle *handle,
        size_t required) {
    if (required <= handle->draw_particle_capacity) {
        return true;
    }
    if (required > SIZE_MAX / sizeof(*handle->draw_particles)) {
        colour_set_error(handle, "Particle export size overflow");
        return false;
    }
    LleColourDrawParticle *resized = (LleColourDrawParticle *) realloc(
            handle->draw_particles,
            required * sizeof(*handle->draw_particles));
    if (resized == NULL) {
        colour_set_error(handle, "Unable to allocate particle draw staging");
        return false;
    }
    handle->draw_particles = resized;
    handle->draw_particle_capacity = required;
    return true;
}

JNIEXPORT jint JNICALL
Java_com_codex_lle_ColourDropletNative_nativeBridgeVersion(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    return LLE_COLOUR_BRIDGE_VERSION;
}

JNIEXPORT jlong JNICALL
Java_com_codex_lle_ColourDropletNative_nativeCreate(
        JNIEnv *env,
        jclass clazz,
        jint project_kind) {
    (void) env;
    (void) clazz;
    if (project_kind != 0 && project_kind != 1) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_COLOUR_LOG_TAG,
                "nativeCreate received invalid project kind %d",
                project_kind);
        return 0;
    }

    LleColourHandle *handle =
            (LleColourHandle *) calloc(1U, sizeof(*handle));
    if (handle == NULL) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_COLOUR_LOG_TAG,
                "Unable to allocate native handle");
        return 0;
    }
    handle->project_kind = project_kind;
    handle->width = LLE_COLOUR_DEFAULT_WIDTH;
    handle->height = LLE_COLOUR_DEFAULT_HEIGHT;
    handle->logical_width = LLE_COLOUR_DEFAULT_WIDTH;
    handle->logical_height = LLE_COLOUR_DEFAULT_HEIGHT;
    handle->sim = lle_colour_sim_create(
            (float) handle->width,
            (float) handle->height,
            handle->project_kind,
            UINT64_C(1));
    if (handle->sim == NULL) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_COLOUR_LOG_TAG,
                "Unable to allocate Coloured Droplet simulation");
        free(handle);
        return 0;
    }
    handle->magic = LLE_COLOUR_HANDLE_MAGIC;
    colour_bind_gl_thread(handle);
    return (jlong) (intptr_t) handle;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_ColourDropletNative_nativeDestroy(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_handle(handle)) {
        return;
    }

    /*
     * Normal teardown is queued on the owning GL thread. If a caller violates
     * that contract, abandon names first so cleanup never issues GLES calls in
     * a foreign/no-context thread.
     */
    if (!handle->gl_thread_bound
            || !pthread_equal(handle->gl_thread, pthread_self())) {
        __android_log_print(
                ANDROID_LOG_WARN,
                LLE_COLOUR_LOG_TAG,
                "nativeDestroy called outside the owning GL thread; abandoning GLES names");
        lle_colour_gles_abandon(&handle->gles);
    }
    lle_colour_gles_destroy(&handle->gles);
    lle_colour_sim_destroy(handle->sim);
    handle->sim = NULL;
    free(handle->draw_particles);
    handle->draw_particles = NULL;
    handle->draw_particle_capacity = 0U;
    handle->magic = 0U;
    free(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_ColourDropletNative_nativeInitGpu(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint width,
        jint height,
        jint logical_width,
        jint logical_height) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_handle(handle)
            || width <= 0
            || height <= 0
            || logical_width <= 0
            || logical_height <= 0) {
        colour_set_error(
                handle,
                "nativeInitGpu received invalid handle or dimensions");
        return JNI_FALSE;
    }
    if (!colour_require_gl_thread(handle, "nativeInitGpu")) {
        return JNI_FALSE;
    }
    colour_clear_error(handle);

    if (handle->gles.ready) {
        lle_colour_gles_destroy(&handle->gles);
    }
    handle->width = width;
    handle->height = height;
    handle->logical_width = logical_width;
    handle->logical_height = logical_height;
    lle_colour_sim_set_surface(
            handle->sim,
            (float) width,
            (float) height,
            (float) logical_width,
            (float) logical_height);
    if (!lle_colour_gles_init(
            &handle->gles,
            width,
            height,
            handle->error,
            sizeof(handle->error))) {
        colour_log_component_error(handle, "GLES initialization");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_ColourDropletNative_nativeResize(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint width,
        jint height) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_handle(handle) || width <= 0 || height <= 0) {
        colour_set_error(handle, "nativeResize received invalid handle or dimensions");
        return JNI_FALSE;
    }
    if (!colour_require_gl_thread(handle, "nativeResize")) {
        return JNI_FALSE;
    }
    if (!handle->gles.ready) {
        colour_set_error(handle, "nativeResize called before GLES initialization");
        return JNI_FALSE;
    }
    colour_clear_error(handle);
    if (!lle_colour_gles_resize(
            &handle->gles,
            width,
            height,
            handle->error,
            sizeof(handle->error))) {
        colour_log_component_error(handle, "GLES resize");
        return JNI_FALSE;
    }
    handle->width = width;
    handle->height = height;
    lle_colour_sim_set_surface(
            handle->sim,
            (float) width,
            (float) height,
            (float) handle->logical_width,
            (float) handle->logical_height);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_ColourDropletNative_nativeAbandonGpu(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_handle(handle)) {
        return;
    }
    /*
     * onSurfaceCreated can run on a replacement GL thread after context loss.
     * abandon does not issue GLES calls, so it is the safe hand-off point.
     */
    lle_colour_gles_abandon(&handle->gles);
    colour_bind_gl_thread(handle);
    colour_clear_error(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_ColourDropletNative_nativeUploadBitmap(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint slot,
        jobject bitmap) {
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_handle(handle)
            || bitmap == NULL
            || slot < 0
            || slot >= LLE_COLOUR_INPUT_TEXTURE_COUNT) {
        colour_set_error(
                handle,
                "nativeUploadBitmap received invalid handle, slot or bitmap");
        return JNI_FALSE;
    }
    if (!colour_require_gl_thread(handle, "nativeUploadBitmap")) {
        return JNI_FALSE;
    }
    if (!handle->gles.ready) {
        colour_set_error(
                handle,
                "nativeUploadBitmap called before GLES initialization");
        return JNI_FALSE;
    }
    colour_clear_error(handle);
    if (!lle_colour_gles_upload_bitmap(
            &handle->gles,
            env,
            slot,
            bitmap,
            handle->error,
            sizeof(handle->error))) {
        colour_log_component_error(handle, "Bitmap upload");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_ColourDropletNative_nativeClearBitmap(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint slot) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_handle(handle)
            || slot < 0
            || slot >= LLE_COLOUR_INPUT_TEXTURE_COUNT) {
        colour_set_error(handle, "nativeClearBitmap received invalid handle or slot");
        return;
    }
    if (!colour_require_gl_thread(handle, "nativeClearBitmap")) {
        return;
    }
    if (!handle->gles.ready) {
        colour_set_error(
                handle,
                "nativeClearBitmap called before GLES initialization");
        return;
    }
    colour_clear_error(handle);
    lle_colour_gles_clear_bitmap(&handle->gles, slot);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_ColourDropletNative_nativeReset(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_gl_thread(handle, "nativeReset")) {
        return;
    }
    lle_colour_sim_reset(handle->sim);
    lle_colour_gles_reset_direction_velocity(&handle->gles);
    handle->accumulator_seconds = 0.0;
    colour_clear_error(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_ColourDropletNative_nativeTouch(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint event_type,
        jfloat screen_x,
        jfloat screen_y,
        jlong event_time_ms) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_gl_thread(handle, "nativeTouch")) {
        return;
    }
    if ((event_type != LLE_COLOUR_TOUCH_DOWN
            && event_type != LLE_COLOUR_TOUCH_UP
            && event_type != LLE_COLOUR_TOUCH_MOVE)
            || !isfinite(screen_x)
            || !isfinite(screen_y)) {
        colour_set_error(handle, "nativeTouch received invalid input");
        return;
    }
    uint64_t time_ms = event_time_ms > 0
            ? (uint64_t) event_time_ms : UINT64_C(0);
    if (!lle_colour_sim_touch(
            handle->sim,
            event_type,
            screen_x,
            screen_y,
            time_ms)) {
        colour_set_error(handle, "Simulation rejected touch input");
        return;
    }
    colour_clear_error(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_ColourDropletNative_nativeSensor(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint sensor_type,
        jfloat x,
        jfloat y,
        jfloat z) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_gl_thread(handle, "nativeSensor")) {
        return;
    }
    if (!isfinite(x) || !isfinite(y) || !isfinite(z)) {
        colour_set_error(handle, "nativeSensor received non-finite input");
        return;
    }
    lle_colour_sim_sensor(handle->sim, sensor_type, x, y, z);
    colour_clear_error(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_ColourDropletNative_nativeAffordance(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jfloat center_x,
        jfloat center_y) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_gl_thread(handle, "nativeAffordance")) {
        return;
    }
    if (!isfinite(center_x) || !isfinite(center_y)) {
        colour_set_error(handle, "nativeAffordance received non-finite input");
        return;
    }
    lle_colour_sim_affordance(handle->sim, center_x, center_y);
    colour_clear_error(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_ColourDropletNative_nativeUnlock(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_gl_thread(handle, "nativeUnlock")) {
        return;
    }
    lle_colour_sim_unlock(handle->sim);
    colour_clear_error(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_ColourDropletNative_nativeResetBackgroundScale(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_gl_thread(handle, "nativeResetBackgroundScale")) {
        return;
    }
    lle_colour_sim_reset_bg_scale(handle->sim);
    colour_clear_error(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_ColourDropletNative_nativeStep(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jfloat elapsed_seconds) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_gl_thread(handle, "nativeStep")) {
        return JNI_FALSE;
    }
    if (!isfinite(elapsed_seconds) || elapsed_seconds < 0.0f) {
        colour_set_error(handle, "nativeStep received invalid elapsedSeconds");
        return JNI_FALSE;
    }

    const double bounded = fmin(
            (double) elapsed_seconds, LLE_COLOUR_MAX_ELAPSED_SECONDS);
    handle->accumulator_seconds += bounded;
    int tick_count = 0;
    while (handle->accumulator_seconds >= LLE_COLOUR_FIXED_SECONDS
            && tick_count < LLE_COLOUR_MAX_TICKS_PER_STEP) {
        lle_colour_sim_tick(handle->sim);
        handle->accumulator_seconds -= LLE_COLOUR_FIXED_SECONDS;
        ++tick_count;
    }
    if (handle->accumulator_seconds >= LLE_COLOUR_FIXED_SECONDS) {
        handle->accumulator_seconds =
                fmod(handle->accumulator_seconds, LLE_COLOUR_FIXED_SECONDS);
    }
    colour_clear_error(handle);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_ColourDropletNative_nativeDraw(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint width,
        jint height) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_handle(handle) || width <= 0 || height <= 0) {
        colour_set_error(handle, "nativeDraw received invalid handle or dimensions");
        return JNI_FALSE;
    }
    if (!colour_require_gl_thread(handle, "nativeDraw")) {
        return JNI_FALSE;
    }
    if (!handle->gles.ready) {
        colour_set_error(handle, "nativeDraw called before GLES initialization");
        return JNI_FALSE;
    }
    colour_clear_error(handle);

    if (handle->width != width || handle->height != height) {
        if (!lle_colour_gles_resize(
                &handle->gles,
                width,
                height,
                handle->error,
                sizeof(handle->error))) {
            colour_log_component_error(handle, "Implicit GLES resize");
            return JNI_FALSE;
        }
        handle->width = width;
        handle->height = height;
        lle_colour_sim_set_surface(
                handle->sim,
                (float) width,
                (float) height,
                (float) handle->logical_width,
                (float) handle->logical_height);
    }

    size_t required =
            lle_colour_sim_export_draw_particles(handle->sim, NULL, 0U);
    if (!colour_resize_particle_buffer(handle, required)) {
        return JNI_FALSE;
    }
    size_t exported = lle_colour_sim_export_draw_particles(
            handle->sim,
            handle->draw_particles,
            handle->draw_particle_capacity);
    if (exported > handle->draw_particle_capacity) {
        if (!colour_resize_particle_buffer(handle, exported)) {
            return JNI_FALSE;
        }
        exported = lle_colour_sim_export_draw_particles(
                handle->sim,
                handle->draw_particles,
                handle->draw_particle_capacity);
    }
    if (exported > handle->draw_particle_capacity) {
        colour_set_error(handle, "Particle export changed during a GL-thread draw");
        return JNI_FALSE;
    }

    LleColourDrawParams params;
    lle_colour_gles_default_params(&params);
    lle_colour_sim_get_draw_params(handle->sim, &params);
    if (!lle_colour_gles_draw(
            &handle->gles,
            handle->draw_particles,
            exported,
            &params,
            width,
            height,
            handle->error,
            sizeof(handle->error))) {
        colour_log_component_error(handle, "GLES draw");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_ColourDropletNative_nativeIsIdle(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    if (!colour_require_handle(handle)) {
        return JNI_TRUE;
    }
    return lle_colour_sim_is_idle(handle->sim) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_codex_lle_ColourDropletNative_nativeGetLastError(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) clazz;
    LleColourHandle *handle = colour_handle(native_handle);
    const char *message = handle != NULL
            ? handle->error
            : "Invalid Coloured Droplet native handle";
    return (*env)->NewStringUTF(env, message);
}
