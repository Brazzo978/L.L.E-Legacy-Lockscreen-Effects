#ifndef LLE_SPARK_GLES_H
#define LLE_SPARK_GLES_H

#include "lle_spark_sim.h"

#include <GLES2/gl2.h>
#include <jni.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define LLE_SPARK_TEXTURE_BACKGROUND 0
#define LLE_SPARK_TEXTURE_BLUR_MASK 1
#define LLE_SPARK_ERROR_SIZE 512u

typedef struct LleSparkGles {
    GLuint program;
    GLuint buffers[4];
    GLuint textures[2];
    GLint attribute_position;
    GLint attribute_initial_position;
    GLint attribute_point_size;
    GLint attribute_point_alpha;
    GLint uniform_mvp_matrix;
    GLint uniform_background;
    GLint uniform_mask;
    GLint uniform_inverse_width;
    GLint uniform_inverse_background_height;
    GLint uniform_crop_max_v;
    float *positions_xy;
    float *initial_positions_xy;
    float *sizes;
    float *alphas;
    LleSparkDrawGroup groups[LLE_SPARK_GROUP_CAPACITY];
    size_t point_capacity;
    int surface_width;
    int surface_height;
    int background_width;
    int background_height;
    bool ready;
    bool has_background;
    bool has_mask;
} LleSparkGles;

bool lle_spark_gles_init(
        LleSparkGles *gles,
        int width,
        int height,
        char *error,
        size_t error_size);

/*
 * Deletes names in the current GLES context and releases renderer-side CPU
 * arrays. Use abandon instead when the context owning the names is already
 * gone.
 */
void lle_spark_gles_destroy(LleSparkGles *gles);

/* Forgets stale GLES names without issuing any GL call. CPU arrays are kept. */
void lle_spark_gles_abandon(LleSparkGles *gles);

bool lle_spark_gles_upload_bitmap(
        LleSparkGles *gles,
        JNIEnv *env,
        int slot,
        jobject bitmap,
        char *error,
        size_t error_size);

void lle_spark_gles_clear_bitmap(LleSparkGles *gles, int slot);

bool lle_spark_gles_draw(
        LleSparkGles *gles,
        const LleSparkSim *sim,
        float presentation_fraction,
        int width,
        int height,
        char *error,
        size_t error_size);

#ifdef __cplusplus
}
#endif

#endif
