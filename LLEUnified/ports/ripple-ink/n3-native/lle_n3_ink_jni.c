#include "lle_n3_ink_worker.h"

#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>

#define LLE_N3_INK_HANDLE_MAGIC UINT64_C(0x4c4c454e33494e4b)
#define LLE_N3_INK_BRIDGE_VERSION 1

typedef struct LleN3InkHandle {
  uint64_t magic;
  pthread_mutex_t mutex;
  LleN3InkWorker *worker;
} LleN3InkHandle;

static LleN3InkHandle *n3_handle(jlong value) {
  LleN3InkHandle *handle = (LleN3InkHandle *)(intptr_t)value;
  return handle != NULL && handle->magic == LLE_N3_INK_HANDLE_MAGIC ? handle : NULL;
}

JNIEXPORT jint JNICALL
Java_com_codex_lle_N3RippleInkWorkerNative_nativeBridgeVersion(
    JNIEnv *environment,
    jclass clazz) {
  (void)environment;
  (void)clazz;
  return LLE_N3_INK_BRIDGE_VERSION;
}

JNIEXPORT jlong JNICALL
Java_com_codex_lle_N3RippleInkWorkerNative_nativeCreate(
    JNIEnv *environment,
    jclass clazz,
    jint velocity_width,
    jint velocity_height,
    jint screen_width,
    jint screen_height) {
  (void)environment;
  (void)clazz;
  LleN3InkHandle *handle = calloc(1U, sizeof(*handle));
  if (handle == NULL) return 0;
  handle->worker = lle_n3_ink_worker_create(velocity_width, velocity_height,
                                             screen_width, screen_height);
  if (handle->worker == NULL || pthread_mutex_init(&handle->mutex, NULL) != 0) {
    lle_n3_ink_worker_destroy(handle->worker);
    free(handle);
    return 0;
  }
  handle->magic = LLE_N3_INK_HANDLE_MAGIC;
  return (jlong)(intptr_t)handle;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_N3RippleInkWorkerNative_nativeReset(
    JNIEnv *environment,
    jclass clazz,
    jlong value) {
  (void)environment;
  (void)clazz;
  LleN3InkHandle *handle = n3_handle(value);
  if (handle == NULL || pthread_mutex_lock(&handle->mutex) != 0) return;
  lle_n3_ink_worker_reset(handle->worker);
  (void)pthread_mutex_unlock(&handle->mutex);
}

JNIEXPORT jbyteArray JNICALL
Java_com_codex_lle_N3RippleInkWorkerNative_nativeStep(
    JNIEnv *environment,
    jclass clazz,
    jlong value,
    jint mode,
    jfloat current_x,
    jfloat current_y_top,
    jfloat previous_x,
    jfloat previous_y_top,
    jfloat velocity_dissipation,
    jfloat divergence_radius,
    jfloat divergence_strength,
    jboolean force_projection) {
  (void)clazz;
  LleN3InkHandle *handle = n3_handle(value);
  if (handle == NULL || pthread_mutex_lock(&handle->mutex) != 0) return NULL;
  const size_t size = lle_n3_ink_worker_rgba_size(handle->worker);
  if (size > (size_t)INT32_MAX) {
    (void)pthread_mutex_unlock(&handle->mutex);
    return NULL;
  }
  jbyteArray output = (*environment)->NewByteArray(environment, (jsize)size);
  if (output == NULL) {
    (void)pthread_mutex_unlock(&handle->mutex);
    return NULL;
  }
  jbyte *bytes = (*environment)->GetByteArrayElements(environment, output, NULL);
  LleN3InkWorkerStep step;
  step.mode = mode;
  step.current_x = current_x;
  step.current_y_top = current_y_top;
  step.previous_x = previous_x;
  step.previous_y_top = previous_y_top;
  step.velocity_dissipation = velocity_dissipation;
  step.divergence_radius = divergence_radius;
  step.divergence_strength = divergence_strength;
  step.force_projection = force_projection == JNI_TRUE;
  const bool completed = bytes != NULL && lle_n3_ink_worker_step(
      handle->worker, &step, (uint8_t *)bytes, size);
  if (bytes != NULL) {
    (*environment)->ReleaseByteArrayElements(environment, output, bytes,
                                              completed ? 0 : JNI_ABORT);
  }
  (void)pthread_mutex_unlock(&handle->mutex);
  if (!completed) return NULL;
  return output;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_N3RippleInkWorkerNative_nativeDestroy(
    JNIEnv *environment,
    jclass clazz,
    jlong value) {
  (void)environment;
  (void)clazz;
  LleN3InkHandle *handle = n3_handle(value);
  if (handle == NULL) return;
  if (pthread_mutex_lock(&handle->mutex) != 0) return;
  handle->magic = 0;
  LleN3InkWorker *worker = handle->worker;
  handle->worker = NULL;
  (void)pthread_mutex_unlock(&handle->mutex);
  lle_n3_ink_worker_destroy(worker);
  (void)pthread_mutex_destroy(&handle->mutex);
  free(handle);
}
