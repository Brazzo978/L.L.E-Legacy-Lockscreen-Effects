#include "lle_s6_water_gles.h"
#include "lle_s6_water_sim.h"

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <limits.h>
#include <math.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LLE_S6_WATER_LOG_TAG "LLES6WaterDroplet"
#define LLE_S6_WATER_BRIDGE_VERSION 3
#define LLE_S6_WATER_STOCK_DT (1.0f / 60.0f)
#define LLE_S6_WATER_HANDLE_MAGIC UINT64_C(0x4c4c455336574154)
#define LLE_S6_WATER_DEFAULT_WIDTH 1440
#define LLE_S6_WATER_DEFAULT_HEIGHT 2560
#define LLE_S6_WATER_STOCK_QUALITY 2

typedef enum LleS6WaterGpuState {
    LLE_S6_WATER_GPU_NEVER_INITIALIZED = 0,
    LLE_S6_WATER_GPU_READY,
    LLE_S6_WATER_GPU_ABANDONED
} LleS6WaterGpuState;

typedef struct LleS6WaterHandle {
    uint64_t magic;
    pthread_mutex_t mutex;
    LleS6WaterSim *sim;
    LleS6WaterGles gles;
    LleS6WaterDensityParticle *draw_particles;
    size_t draw_particle_capacity;
    int project_kind;
    int quality;
    int width;
    int height;
    int logical_short;
    int logical_long;
    pthread_t owner_thread;
    bool owner_thread_bound;
    LleS6WaterGpuState gpu_state;
    char error[LLE_S6_WATER_GLES_ERROR_SIZE];
} LleS6WaterHandle;

static LleS6WaterHandle *s6_water_handle(jlong value) {
    if (value == 0) {
        return NULL;
    }
    LleS6WaterHandle *handle = (LleS6WaterHandle *) (intptr_t) value;
    return handle->magic == LLE_S6_WATER_HANDLE_MAGIC ? handle : NULL;
}

static bool s6_water_require_handle(const LleS6WaterHandle *handle) {
    return handle != NULL && handle->sim != NULL;
}

static void s6_water_clear_error_locked(LleS6WaterHandle *handle) {
    if (handle != NULL) {
        handle->error[0] = '\0';
    }
}

static void s6_water_set_error_locked(
        LleS6WaterHandle *handle,
        const char *format,
        ...)
        __attribute__((format(printf, 2, 3)));

static void s6_water_set_error_locked(
        LleS6WaterHandle *handle,
        const char *format,
        ...) {
    char message[LLE_S6_WATER_GLES_ERROR_SIZE];
    va_list arguments;
    va_start(arguments, format);
    (void) vsnprintf(message, sizeof(message), format, arguments);
    va_end(arguments);
    message[sizeof(message) - 1U] = '\0';

    if (handle != NULL) {
        (void) snprintf(handle->error, sizeof(handle->error), "%s", message);
    }
    __android_log_print(ANDROID_LOG_ERROR, LLE_S6_WATER_LOG_TAG, "%s", message);
}

static void s6_water_log_component_error_locked(
        LleS6WaterHandle *handle,
        const char *operation) {
    if (handle == NULL) {
        return;
    }
    handle->error[sizeof(handle->error) - 1U] = '\0';
    __android_log_print(
            ANDROID_LOG_ERROR,
            LLE_S6_WATER_LOG_TAG,
            "%s failed: %s",
            operation != NULL ? operation : "Native component",
            handle->error[0] != '\0' ? handle->error : "unknown error");
}

static bool s6_water_lock(LleS6WaterHandle *handle) {
    if (!s6_water_require_handle(handle)) {
        return false;
    }
    int result = pthread_mutex_lock(&handle->mutex);
    if (result != 0) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_S6_WATER_LOG_TAG,
                "Unable to lock native handle: pthread error %d",
                result);
        return false;
    }
    return true;
}

static void s6_water_unlock(LleS6WaterHandle *handle) {
    int result = pthread_mutex_unlock(&handle->mutex);
    if (result != 0) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_S6_WATER_LOG_TAG,
                "Unable to unlock native handle: pthread error %d",
                result);
    }
}

static void s6_water_bind_owner_locked(LleS6WaterHandle *handle) {
    handle->owner_thread = pthread_self();
    handle->owner_thread_bound = true;
}

static bool s6_water_is_owner_locked(const LleS6WaterHandle *handle) {
    return handle->owner_thread_bound
            && pthread_equal(handle->owner_thread, pthread_self());
}

static bool s6_water_require_owner_locked(
        LleS6WaterHandle *handle,
        const char *operation) {
    if (!s6_water_is_owner_locked(handle)) {
        s6_water_set_error_locked(
                handle,
                "%s must run on the owning GL thread",
                operation != NULL ? operation : "Native operation");
        return false;
    }
    return true;
}

/*
 * glGetString(GL_VERSION) is the GLES2-supported, allocation-free context
 * probe. It returns NULL when the calling thread has no current GLES context.
 * Keep this check in the JNI owner instead of letting a missing context turn
 * later resource operations into driver-dependent failures.
 */
static bool s6_water_require_gl_context_locked(
        LleS6WaterHandle *handle,
        const char *operation) {
    if (glGetString(GL_VERSION) == NULL) {
        s6_water_set_error_locked(
                handle,
                "%s requires a current GLES context",
                operation != NULL ? operation : "Native operation");
        return false;
    }
    return true;
}

static bool s6_water_require_gpu_locked(
        LleS6WaterHandle *handle,
        const char *operation) {
    if (handle->gpu_state != LLE_S6_WATER_GPU_READY
            || !handle->gles.ready) {
        s6_water_set_error_locked(
                handle,
                "%s called before GLES initialization or after GPU abandon",
                operation != NULL ? operation : "Native operation");
        return false;
    }
    return true;
}

static bool s6_water_resize_particle_buffer_locked(
        LleS6WaterHandle *handle,
        size_t required) {
    if (required <= handle->draw_particle_capacity) {
        return true;
    }
    if (required > SIZE_MAX / sizeof(*handle->draw_particles)) {
        s6_water_set_error_locked(handle, "Particle export size overflow");
        return false;
    }
    LleS6WaterDensityParticle *resized =
            (LleS6WaterDensityParticle *) realloc(
                    handle->draw_particles,
                    required * sizeof(*handle->draw_particles));
    if (resized == NULL) {
        s6_water_set_error_locked(
                handle, "Unable to allocate particle draw staging");
        return false;
    }
    handle->draw_particles = resized;
    handle->draw_particle_capacity = required;
    return true;
}

static bool s6_water_apply_resize_locked(
        LleS6WaterHandle *handle,
        int width,
        int height,
        int logical_short,
        int logical_long,
        const char *operation) {
    if (!lle_s6_water_gles_resize(
            &handle->gles,
            width,
            height,
            handle->error,
            sizeof(handle->error))) {
        s6_water_log_component_error_locked(handle, operation);
        return false;
    }
    handle->width = width;
    handle->height = height;
    handle->logical_short = logical_short;
    handle->logical_long = logical_long;
    lle_s6_water_sim_set_surface(
            handle->sim,
            (float) width,
            (float) height,
            (float) logical_short,
            (float) logical_long);
    return true;
}

JNIEXPORT jint JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeBridgeVersion(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    return LLE_S6_WATER_BRIDGE_VERSION;
}

JNIEXPORT jlong JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeCreate(
        JNIEnv *env,
        jclass clazz,
        jint project_kind,
        jint quality,
        jlong deterministic_seed) {
    (void) env;
    (void) clazz;
    if ((project_kind != LLE_S6_WATER_PROJECT_PHONE
            && project_kind != LLE_S6_WATER_PROJECT_TABLET)
            || quality != LLE_S6_WATER_STOCK_QUALITY) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_S6_WATER_LOG_TAG,
                "nativeCreate requires project kind 0/1 and stock quality 2; "
                "received project=%d quality=%d",
                project_kind,
                quality);
        return 0;
    }

    LleS6WaterHandle *handle =
            (LleS6WaterHandle *) calloc(1U, sizeof(*handle));
    if (handle == NULL) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_S6_WATER_LOG_TAG,
                "Unable to allocate native handle");
        return 0;
    }
    int mutex_result = pthread_mutex_init(&handle->mutex, NULL);
    if (mutex_result != 0) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_S6_WATER_LOG_TAG,
                "Unable to initialize native mutex: pthread error %d",
                mutex_result);
        free(handle);
        return 0;
    }

    handle->project_kind = project_kind;
    handle->quality = quality;
    handle->width = LLE_S6_WATER_DEFAULT_WIDTH;
    handle->height = LLE_S6_WATER_DEFAULT_HEIGHT;
    handle->logical_short = LLE_S6_WATER_DEFAULT_WIDTH;
    handle->logical_long = LLE_S6_WATER_DEFAULT_HEIGHT;
    handle->sim = lle_s6_water_sim_create(
            (float) handle->width,
            (float) handle->height,
            project_kind,
            quality,
            (uint64_t) deterministic_seed);
    if (handle->sim == NULL) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                LLE_S6_WATER_LOG_TAG,
                "Unable to allocate S6 Water Droplet simulation");
        (void) pthread_mutex_destroy(&handle->mutex);
        free(handle);
        return 0;
    }

    handle->magic = LLE_S6_WATER_HANDLE_MAGIC;
    s6_water_bind_owner_locked(handle);
    return (jlong) (intptr_t) handle;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeInitGpu(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint width,
        jint height) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return JNI_FALSE;
    }
    if (!s6_water_require_owner_locked(handle, "nativeInitGpu")
            || !s6_water_require_gl_context_locked(handle, "nativeInitGpu")
            || width <= 0
            || height <= 0) {
        if (width <= 0 || height <= 0) {
            s6_water_set_error_locked(
                    handle, "nativeInitGpu received invalid dimensions");
        }
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    s6_water_clear_error_locked(handle);
    if (handle->gpu_state == LLE_S6_WATER_GPU_READY
            || handle->gles.ready) {
        lle_s6_water_gles_destroy(&handle->gles);
    }
    if (!lle_s6_water_gles_init(
            &handle->gles,
            width,
            height,
            handle->error,
            sizeof(handle->error))) {
        handle->gpu_state = LLE_S6_WATER_GPU_ABANDONED;
        s6_water_log_component_error_locked(handle, "GLES initialization");
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    handle->width = width;
    handle->height = height;
    handle->logical_short = width < height ? width : height;
    handle->logical_long = width < height ? height : width;
    lle_s6_water_sim_set_surface(
            handle->sim,
            (float) width,
            (float) height,
            (float) handle->logical_short,
            (float) handle->logical_long);
    handle->gpu_state = LLE_S6_WATER_GPU_READY;
    s6_water_unlock(handle);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeResize(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint width,
        jint height,
        jint logical_short,
        jint logical_long) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return JNI_FALSE;
    }
    if (!s6_water_require_owner_locked(handle, "nativeResize")
            || !s6_water_require_gpu_locked(handle, "nativeResize")
            || !s6_water_require_gl_context_locked(handle, "nativeResize")) {
        s6_water_unlock(handle);
        return JNI_FALSE;
    }
    if (width <= 0
            || height <= 0
            || logical_short <= 0
            || logical_long <= 0
            || logical_short > logical_long) {
        s6_water_set_error_locked(
                handle, "nativeResize received invalid surface/logical dimensions");
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    s6_water_clear_error_locked(handle);
    bool resized = s6_water_apply_resize_locked(
            handle,
            width,
            height,
            logical_short,
            logical_long,
            "GLES resize");
    s6_water_unlock(handle);
    return resized ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeAbandonGpu(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return;
    }

    /*
     * This is the only legal owner-thread transfer. It must remain completely
     * GLES-free because the caller declares the old context dead. Abandon only
     * forgets stale names; the following init validates the replacement
     * context before recreating them.
     */
    lle_s6_water_gles_abandon(&handle->gles);
    handle->gpu_state = LLE_S6_WATER_GPU_ABANDONED;
    s6_water_bind_owner_locked(handle);
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeDestroy(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return;
    }

    bool can_delete_gl_names = s6_water_is_owner_locked(handle)
            && glGetString(GL_VERSION) != NULL;
    if (!can_delete_gl_names) {
        __android_log_print(
                ANDROID_LOG_WARN,
                LLE_S6_WATER_LOG_TAG,
                "nativeDestroy has no current context on the owning GL thread; "
                "abandoning GLES names before CPU teardown");
        lle_s6_water_gles_abandon(&handle->gles);
        handle->gpu_state = LLE_S6_WATER_GPU_ABANDONED;
    }
    lle_s6_water_gles_destroy(&handle->gles);
    lle_s6_water_sim_destroy(handle->sim);
    handle->sim = NULL;
    free(handle->draw_particles);
    handle->draw_particles = NULL;
    handle->draw_particle_capacity = 0U;
    handle->magic = 0U;

    s6_water_unlock(handle);
    int mutex_result = pthread_mutex_destroy(&handle->mutex);
    if (mutex_result != 0) {
        __android_log_print(
                ANDROID_LOG_WARN,
                LLE_S6_WATER_LOG_TAG,
                "Unable to destroy native mutex: pthread error %d",
                mutex_result);
    }
    free(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeUploadBitmap(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint slot,
        jobject bitmap) {
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return JNI_FALSE;
    }
    if (!s6_water_require_owner_locked(handle, "nativeUploadBitmap")
            || !s6_water_require_gpu_locked(handle, "nativeUploadBitmap")
            || !s6_water_require_gl_context_locked(
                    handle, "nativeUploadBitmap")) {
        s6_water_unlock(handle);
        return JNI_FALSE;
    }
    if (bitmap == NULL
            || slot < 0
            || slot >= LLE_S6_WATER_INPUT_TEXTURE_COUNT) {
        s6_water_set_error_locked(
                handle,
                "nativeUploadBitmap received invalid slot or null bitmap");
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    int bitmap_result = AndroidBitmap_getInfo(env, bitmap, &info);
    uint32_t alpha_mode =
            info.flags & (uint32_t) ANDROID_BITMAP_FLAGS_ALPHA_MASK;
    if (bitmap_result != ANDROID_BITMAP_RESULT_SUCCESS
            || info.width == 0U
            || info.height == 0U
            || info.width > (uint32_t) INT_MAX
            || info.height > (uint32_t) INT_MAX
            || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888
            || (info.flags
                    & (uint32_t) ANDROID_BITMAP_FLAGS_IS_HARDWARE) != 0U
            || alpha_mode
                    == (uint32_t) ANDROID_BITMAP_FLAGS_ALPHA_UNPREMUL
            || (uint64_t) info.stride < (uint64_t) info.width * UINT64_C(4)) {
        s6_water_set_error_locked(
                handle,
                "nativeUploadBitmap requires a non-empty, premultiplied or "
                "opaque software RGBA_8888 Android Bitmap");
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    s6_water_clear_error_locked(handle);
    if (!lle_s6_water_gles_upload_bitmap(
            &handle->gles,
            env,
            slot,
            bitmap,
            handle->error,
            sizeof(handle->error))) {
        s6_water_log_component_error_locked(handle, "Bitmap upload");
        s6_water_unlock(handle);
        return JNI_FALSE;
    }
    s6_water_unlock(handle);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeClearBitmap(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint slot) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return;
    }
    if (!s6_water_require_owner_locked(handle, "nativeClearBitmap")
            || !s6_water_require_gpu_locked(handle, "nativeClearBitmap")
            || !s6_water_require_gl_context_locked(
                    handle, "nativeClearBitmap")) {
        s6_water_unlock(handle);
        return;
    }
    if (slot < 0 || slot >= LLE_S6_WATER_INPUT_TEXTURE_COUNT) {
        s6_water_set_error_locked(
                handle, "nativeClearBitmap received invalid slot");
        s6_water_unlock(handle);
        return;
    }

    lle_s6_water_gles_clear_bitmap(&handle->gles, slot);
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeReset(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return;
    }
    if (!s6_water_require_owner_locked(handle, "nativeReset")) {
        s6_water_unlock(handle);
        return;
    }
    lle_s6_water_sim_request_reset(handle->sim);
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeTouch(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint event_type,
        jfloat screen_x,
        jfloat screen_y,
        jlong event_time_ms) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return;
    }
    if (!s6_water_require_owner_locked(handle, "nativeTouch")) {
        s6_water_unlock(handle);
        return;
    }
    if ((event_type != LLE_S6_WATER_TOUCH_DOWN
            && event_type != LLE_S6_WATER_TOUCH_UP
            && event_type != LLE_S6_WATER_TOUCH_MOVE)
            || !isfinite(screen_x)
            || !isfinite(screen_y)) {
        s6_water_set_error_locked(handle, "nativeTouch received invalid input");
        s6_water_unlock(handle);
        return;
    }

    uint64_t time_ms =
            event_time_ms > 0 ? (uint64_t) event_time_ms : UINT64_C(0);
    if (!lle_s6_water_sim_queue_touch(
            handle->sim,
            event_type,
            screen_x,
            screen_y,
            time_ms)) {
        s6_water_set_error_locked(
                handle, "Simulation touch event queue is full");
        s6_water_unlock(handle);
        return;
    }
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeTilt(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jfloat mapped_x,
        jfloat mapped_y,
        jlong sample_time_nanos) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return;
    }
    if (!s6_water_require_owner_locked(handle, "nativeTilt")) {
        s6_water_unlock(handle);
        return;
    }
    if (!isfinite(mapped_x)
            || !isfinite(mapped_y)
            || sample_time_nanos < 0) {
        s6_water_set_error_locked(handle, "nativeTilt received invalid input");
        s6_water_unlock(handle);
        return;
    }

    /*
     * The public bridge already receives the orientation-mapped accelerometer
     * pair. Preserve the monotonic Android sensor timestamp so the simulation
     * can deterministically order queued tilt samples with the frame boundary.
     */
    if (!lle_s6_water_sim_queue_tilt(
            handle->sim,
            mapped_x,
            mapped_y,
            (uint64_t) sample_time_nanos)) {
        s6_water_set_error_locked(
                handle, "Simulation tilt event queue is full");
        s6_water_unlock(handle);
        return;
    }
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeAffordance(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jfloat screen_x,
        jfloat screen_y) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return;
    }
    if (!s6_water_require_owner_locked(handle, "nativeAffordance")) {
        s6_water_unlock(handle);
        return;
    }
    if (!isfinite(screen_x) || !isfinite(screen_y)) {
        s6_water_set_error_locked(
                handle, "nativeAffordance received non-finite coordinates");
        s6_water_unlock(handle);
        return;
    }
    if (!lle_s6_water_sim_queue_affordance(
            handle->sim, screen_x, screen_y)) {
        s6_water_set_error_locked(
                handle, "Simulation affordance event queue is full");
        s6_water_unlock(handle);
        return;
    }
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeUnlock(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return;
    }
    if (!s6_water_require_owner_locked(handle, "nativeUnlock")) {
        s6_water_unlock(handle);
        return;
    }
    if (!lle_s6_water_sim_queue_unlock(handle->sim)) {
        s6_water_set_error_locked(
                handle, "Simulation unlock event queue is full");
        s6_water_unlock(handle);
        return;
    }
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeResetBackgroundScale(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return;
    }
    if (!s6_water_require_owner_locked(
            handle, "nativeResetBackgroundScale")) {
        s6_water_unlock(handle);
        return;
    }
    if (!lle_s6_water_sim_queue_reset_bg_scale(handle->sim)) {
        s6_water_set_error_locked(
                handle, "Simulation background-scale event queue is full");
        s6_water_unlock(handle);
        return;
    }
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeStep(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return JNI_FALSE;
    }
    if (!s6_water_require_owner_locked(handle, "nativeStep")) {
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    /*
     * key 90 is a request, not an immediate mutation. Consume it at the next
     * frame boundary before the recovered fixed tick and immutable export.
     */
    (void) lle_s6_water_sim_consume_deferred_reset(handle->sim);
    lle_s6_water_sim_tick(handle->sim);
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeStepNativeRefresh(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jfloat frame_scale) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!isfinite(frame_scale) || frame_scale <= 0.0f) {
        return JNI_FALSE;
    }
    if (!s6_water_lock(handle)) {
        return JNI_FALSE;
    }
    if (!s6_water_require_owner_locked(handle, "nativeStepNativeRefresh")) {
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    (void) lle_s6_water_sim_consume_deferred_reset(handle->sim);
    lle_s6_water_sim_tick_native_refresh(handle->sim, frame_scale);
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeDraw(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle,
        jint width,
        jint height,
        jfloat presentation_fraction) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return JNI_FALSE;
    }
    if (!s6_water_require_owner_locked(handle, "nativeDraw")
            || !s6_water_require_gpu_locked(handle, "nativeDraw")
            || !s6_water_require_gl_context_locked(handle, "nativeDraw")) {
        s6_water_unlock(handle);
        return JNI_FALSE;
    }
    if (width <= 0 || height <= 0) {
        s6_water_set_error_locked(
                handle, "nativeDraw received invalid dimensions");
        s6_water_unlock(handle);
        return JNI_FALSE;
    }
    if (!isfinite(presentation_fraction)
            || presentation_fraction < 0.0f
            || presentation_fraction > 1.0f) {
        s6_water_set_error_locked(
                handle, "nativeDraw received invalid presentation fraction");
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    s6_water_clear_error_locked(handle);
    if (handle->width != width || handle->height != height) {
        if (!s6_water_apply_resize_locked(
                handle,
                width,
                height,
                handle->logical_short,
                handle->logical_long,
                "Implicit GLES resize")) {
            s6_water_unlock(handle);
            return JNI_FALSE;
        }
    }

    /*
     * Also consume here so reset is honored if a lifecycle caller draws a
     * transparent park frame without first requesting a simulation step.
     */
    (void) lle_s6_water_sim_consume_deferred_reset(handle->sim);

    size_t required =
            lle_s6_water_sim_export_density_particles(handle->sim, NULL, 0U);
    if (!s6_water_resize_particle_buffer_locked(handle, required)) {
        s6_water_unlock(handle);
        return JNI_FALSE;
    }
    size_t exported = lle_s6_water_sim_export_density_particles(
            handle->sim,
            handle->draw_particles,
            handle->draw_particle_capacity);
    if (exported > handle->draw_particle_capacity) {
        if (!s6_water_resize_particle_buffer_locked(handle, exported)) {
            s6_water_unlock(handle);
            return JNI_FALSE;
        }
        exported = lle_s6_water_sim_export_density_particles(
                handle->sim,
                handle->draw_particles,
                handle->draw_particle_capacity);
    }
    if (exported > handle->draw_particle_capacity) {
        s6_water_set_error_locked(
                handle, "Particle export changed during serialized draw");
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    /*
     * Stock physics advances at 60 Hz. On modern 80/120 Hz panels, present
     * the fractional time remaining in the fixed-step accumulator instead of
     * displaying every particle position for two consecutive refreshes.
     * This affects only the immutable draw snapshot, never simulation state.
     */
    const float presentation_seconds =
            presentation_fraction * LLE_S6_WATER_STOCK_DT;
    for (size_t index = 0U; index < exported; ++index) {
        LleS6WaterDensityParticle *particle = &handle->draw_particles[index];
        particle->center_x_px +=
                particle->velocity_x_px_per_second * presentation_seconds;
        particle->center_y_px +=
                particle->velocity_y_px_per_second * presentation_seconds;
    }

    LleS6WaterRenderState render_state;
    memset(&render_state, 0, sizeof(render_state));
    lle_s6_water_sim_get_render_state(handle->sim, &render_state);
    if (render_state.density_particle_count != exported) {
        s6_water_set_error_locked(
                handle,
                "Simulation snapshot count mismatch: state=%zu export=%zu",
                render_state.density_particle_count,
                exported);
        s6_water_unlock(handle);
        return JNI_FALSE;
    }
    if (!lle_s6_water_gles_draw(
            &handle->gles,
            handle->draw_particles,
            exported,
            &render_state,
            handle->error,
            sizeof(handle->error))) {
        s6_water_log_component_error_locked(handle, "GLES draw");
        s6_water_unlock(handle);
        return JNI_FALSE;
    }

    s6_water_unlock(handle);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeIsIdle(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) env;
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_lock(handle)) {
        return JNI_TRUE;
    }
    if (!s6_water_require_owner_locked(handle, "nativeIsIdle")) {
        s6_water_unlock(handle);
        return JNI_TRUE;
    }
    bool idle = lle_s6_water_sim_is_idle(handle->sim);
    s6_water_clear_error_locked(handle);
    s6_water_unlock(handle);
    return idle ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeGetLastError(
        JNIEnv *env,
        jclass clazz,
        jlong native_handle) {
    (void) clazz;
    LleS6WaterHandle *handle = s6_water_handle(native_handle);
    if (!s6_water_require_handle(handle)) {
        return (*env)->NewStringUTF(
                env, "Invalid S6 Water Droplet native handle");
    }
    if (!s6_water_lock(handle)) {
        return (*env)->NewStringUTF(
                env, "Unable to lock S6 Water Droplet native handle");
    }
    if (!s6_water_is_owner_locked(handle)) {
        s6_water_unlock(handle);
        return (*env)->NewStringUTF(
                env, "nativeGetLastError must run on the owning GL thread");
    }

    char message[LLE_S6_WATER_GLES_ERROR_SIZE];
    (void) snprintf(message, sizeof(message), "%s", handle->error);
    message[sizeof(message) - 1U] = '\0';
    s6_water_unlock(handle);
    return (*env)->NewStringUTF(env, message);
}
