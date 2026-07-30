#ifndef LLE_S6_WATER_GLES_H
#define LLE_S6_WATER_GLES_H

#include "lle_s6_water_sim.h"

#include <GLES2/gl2.h>
#include <jni.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define LLE_S6_WATER_TEXTURE_PORTRAIT_BACKGROUND 0
#define LLE_S6_WATER_TEXTURE_LANDSCAPE_BACKGROUND 1
#define LLE_S6_WATER_TEXTURE_NORMAL 2
#define LLE_S6_WATER_TEXTURE_EDGE_DENSITY 3
#define LLE_S6_WATER_INPUT_TEXTURE_COUNT 4

#define LLE_S6_WATER_GLES_PROGRAM_COUNT 2
#define LLE_S6_WATER_GLES_BUFFER_COUNT 2
#define LLE_S6_WATER_GLES_ERROR_SIZE 512u

/*
 * GLES2 state owned by one EGL context and one GL thread.
 *
 * Zero-initialize this value before its first init. The simulation remains a
 * separate owner: draw only reads the copied render state and density array.
 */
typedef struct LleS6WaterGles {
  GLuint programs[LLE_S6_WATER_GLES_PROGRAM_COUNT];
  GLuint buffers[LLE_S6_WATER_GLES_BUFFER_COUNT];
  GLuint input_textures[LLE_S6_WATER_INPUT_TEXTURE_COUNT];
  GLuint density_texture;
  GLuint density_framebuffer;
  int input_width[LLE_S6_WATER_INPUT_TEXTURE_COUNT];
  int input_height[LLE_S6_WATER_INPUT_TEXTURE_COUNT];
  int density_width;
  int density_height;
  int surface_width;
  int surface_height;
  float *particle_staging;
  size_t particle_capacity;
  bool has_input[LLE_S6_WATER_INPUT_TEXTURE_COUNT];
  bool ready;
} LleS6WaterGles;

bool lle_s6_water_gles_init(LleS6WaterGles *gles, int width, int height,
                            char *error, size_t error_size);

bool lle_s6_water_gles_resize(LleS6WaterGles *gles, int width, int height,
                              char *error, size_t error_size);

/*
 * Deletes GLES names in the current owning context and releases CPU staging.
 * Use abandon instead when the EGL context has already been destroyed.
 */
void lle_s6_water_gles_destroy(LleS6WaterGles *gles);

/*
 * Forgets stale GLES names without issuing GLES calls. CPU particle staging
 * remains allocated for the replacement context. All four bitmaps must be
 * uploaded again after the following init.
 */
void lle_s6_water_gles_abandon(LleS6WaterGles *gles);

/*
 * Uploads an RGBA_8888 Android Bitmap without un-premultiplying it. This is
 * intentional: the recovered density blend consumes Android's premultiplied
 * normal and edge-density texels verbatim.
 */
bool lle_s6_water_gles_upload_bitmap(LleS6WaterGles *gles, JNIEnv *env,
                                     int slot, jobject bitmap, char *error,
                                     size_t error_size);

void lle_s6_water_gles_clear_bitmap(LleS6WaterGles *gles, int slot);

/*
 * Draws one transparent, premultiplied LLE overlay to framebuffer zero.
 * particles are consumed in the supplied order because density RGB blending
 * is not commutative. render_state and particles are immutable and are never
 * retained after this call.
 *
 * The active background is selected from the portrait/landscape slots using
 * render_state's surface dimensions. A successful frame clears outside the
 * recovered metaball to transparent; it never emits an opaque fullscreen
 * bootstrap/background frame.
 */
bool lle_s6_water_gles_draw(
    LleS6WaterGles *gles, const LleS6WaterDensityParticle *particles,
    size_t particle_count, const LleS6WaterRenderState *render_state,
    char *error, size_t error_size);

#ifdef __cplusplus
}
#endif

#endif
