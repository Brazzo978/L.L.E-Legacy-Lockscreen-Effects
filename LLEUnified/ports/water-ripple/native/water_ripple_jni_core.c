#include "ripple_core.h"

#include <jni.h>
#include <stdint.h>

JNIEXPORT void JNICALL
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_initWaters(
        JNIEnv *env,
        jclass clazz,
        jfloatArray vertices_array,
        jshortArray indices_array,
        jint vertex_count,
        jint mesh_height,
        jint mesh_width,
        jint surface_height,
        jint surface_width) {
    (void) clazz;
    if (vertices_array == NULL || indices_array == NULL) {
        return;
    }

    jfloat *vertices = (*env)->GetFloatArrayElements(env, vertices_array, NULL);
    jshort *indices = (*env)->GetShortArrayElements(env, indices_array, NULL);
    if (vertices != NULL && indices != NULL) {
        lle_ripple_init_waters(
                vertices,
                (int16_t *) indices,
                vertex_count,
                mesh_height,
                mesh_width,
                surface_height,
                surface_width);
    }
    if (vertices != NULL) {
        (*env)->ReleaseFloatArrayElements(env, vertices_array, vertices, 0);
    }
    if (indices != NULL) {
        (*env)->ReleaseShortArrayElements(env, indices_array, indices, 0);
    }
}

JNIEXPORT jint JNICALL
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_move(
        JNIEnv *env,
        jclass clazz,
        jfloatArray velocity_array,
        jfloatArray height_array,
        jint x_begin,
        jint y_begin,
        jint x_end,
        jint y_end,
        jint detail_width,
        jint detail_height,
        jboolean check_empty,
        jfloat damping,
        jfloat wave_coefficient) {
    (void) clazz;
    if (velocity_array == NULL || height_array == NULL) {
        return JNI_TRUE;
    }

    jfloat *velocity = (*env)->GetFloatArrayElements(env, velocity_array, NULL);
    jfloat *height = (*env)->GetFloatArrayElements(env, height_array, NULL);
    jboolean empty = JNI_TRUE;
    if (velocity != NULL && height != NULL) {
        empty = lle_ripple_move(
                velocity,
                height,
                x_begin,
                y_begin,
                x_end,
                y_end,
                detail_width,
                detail_height,
                check_empty == JNI_TRUE,
                damping,
                wave_coefficient) ? JNI_TRUE : JNI_FALSE;
    }
    if (velocity != NULL) {
        (*env)->ReleaseFloatArrayElements(env, velocity_array, velocity, 0);
    }
    if (height != NULL) {
        (*env)->ReleaseFloatArrayElements(env, height_array, height, 0);
    }
    return empty;
}

JNIEXPORT jint JNICALL
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_moveAdaptive(
        JNIEnv *env,
        jclass clazz,
        jfloatArray velocity_array,
        jfloatArray height_array,
        jint x_begin,
        jint y_begin,
        jint x_end,
        jint y_end,
        jint detail_width,
        jint detail_height,
        jboolean check_empty,
        jfloat damping,
        jfloat wave_coefficient,
        jfloat stock_ticks) {
    (void) clazz;
    if (velocity_array == NULL || height_array == NULL) {
        return JNI_TRUE;
    }

    jfloat *velocity = (*env)->GetFloatArrayElements(env, velocity_array, NULL);
    jfloat *height = (*env)->GetFloatArrayElements(env, height_array, NULL);
    jboolean empty = JNI_TRUE;
    if (velocity != NULL && height != NULL) {
        empty = lle_ripple_move_adaptive(
                velocity,
                height,
                x_begin,
                y_begin,
                x_end,
                y_end,
                detail_width,
                detail_height,
                check_empty == JNI_TRUE,
                damping,
                wave_coefficient,
                stock_ticks) ? JNI_TRUE : JNI_FALSE;
    }
    if (velocity != NULL) {
        (*env)->ReleaseFloatArrayElements(env, velocity_array, velocity, 0);
    }
    if (height != NULL) {
        (*env)->ReleaseFloatArrayElements(env, height_array, height, 0);
    }
    return empty;
}

JNIEXPORT void JNICALL
Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_ripple(
        JNIEnv *env,
        jclass clazz,
        jfloatArray velocity_array,
        jint mesh_width,
        jint mesh_height,
        jint detail_width,
        jint detail_height,
        jfloat mesh_x,
        jfloat mesh_y,
        jfloat strength) {
    (void) clazz;
    if (velocity_array == NULL) {
        return;
    }

    jfloat *velocity = (*env)->GetFloatArrayElements(env, velocity_array, NULL);
    if (velocity != NULL) {
        lle_ripple_inject(
                velocity,
                mesh_width,
                mesh_height,
                detail_width,
                detail_height,
                mesh_x,
                mesh_y,
                strength);
        (*env)->ReleaseFloatArrayElements(env, velocity_array, velocity, 0);
    }
}
