#ifndef LLE_COLOUR_GLES_H
#define LLE_COLOUR_GLES_H

#include <GLES2/gl2.h>
#include <jni.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define LLE_COLOUR_TEXTURE_BACKGROUND 0
#define LLE_COLOUR_TEXTURE_NORMAL 1
#define LLE_COLOUR_TEXTURE_EDGE_DENSITY 2
#define LLE_COLOUR_INPUT_TEXTURE_COUNT 3

#define LLE_COLOUR_FBO_DENSITY 0
#define LLE_COLOUR_FBO_COLOR_DIRECTION 1
#define LLE_COLOUR_FBO_COLOR_MAP 2
#define LLE_COLOUR_FBO_COUNT 3

#define LLE_COLOUR_PROGRAM_ENHANCE 0
#define LLE_COLOUR_PROGRAM_COPY 1
#define LLE_COLOUR_PROGRAM_DENSITY 2
#define LLE_COLOUR_PROGRAM_COLOR_DIRECTION 3
#define LLE_COLOUR_PROGRAM_COMPOSITE 4
#define LLE_COLOUR_PROGRAM_STENCIL 5
#define LLE_COLOUR_PROGRAM_COUNT 6

#define LLE_COLOUR_PARTICLE_SPECIAL 0x00000001u
#define LLE_COLOUR_PARTICLE_SATELLITE 0x00000002u
#define LLE_COLOUR_ERROR_SIZE 512u

/*
 * Screen-space draw particle consumed by the GLES renderer.
 *
 * x/y, velocity_x/velocity_y, size_px and color_x/color_y use the Android
 * top-left coordinate system. density_size_px and colour_size_px are distinct
 * stock point diameters, already expressed in their respective reduced render
 * targets' pixel domains. They must not be multiplied by FBO/surface
 * resolution: the stock main/satellite paths use different formulas and
 * cannot be represented by one generic ratio. color_x/color_y normally equal
 * the particle position, but are separate because the stock renderer preserves
 * the colour pick-up position while a group moves.
 */
typedef struct LleColourDrawParticle {
    float x;
    float y;
    float velocity_x;
    float velocity_y;
    float density_size_px;
    float colour_size_px;
    float alpha;
    float color_x;
    float color_y;
    uint32_t flags;
} LleColourDrawParticle;

typedef struct LleColourDrawParams {
    float edge_ratio;
    float restore_ratio;
    float direction_velocity_x;
    float direction_velocity_y;
    float refraction_ratio;
    float tab_scale;
    float tab_offset_x;
    float tab_offset_y;
    float edge_offset_ratio;
    float inner_shadow_width;
    float color_saturation;
    float color_brightness;
    float color_min_value;
    bool shadow_enabled;
} LleColourDrawParams;

typedef struct LleColourGles {
    GLuint programs[LLE_COLOUR_PROGRAM_COUNT];
    GLuint buffers[4];
    GLuint input_textures[LLE_COLOUR_INPUT_TEXTURE_COUNT];
    GLuint fbo_textures[LLE_COLOUR_FBO_COUNT];
    GLuint framebuffers[LLE_COLOUR_FBO_COUNT];
    int input_width[LLE_COLOUR_INPUT_TEXTURE_COUNT];
    int input_height[LLE_COLOUR_INPUT_TEXTURE_COUNT];
    int fbo_width[LLE_COLOUR_FBO_COUNT];
    int fbo_height[LLE_COLOUR_FBO_COUNT];
    float *particle_staging;
    float *density_staging;
    size_t particle_capacity;
    float direction_velocity_x;
    float direction_velocity_y;
    int surface_width;
    int surface_height;
    bool ready;
    bool has_input[LLE_COLOUR_INPUT_TEXTURE_COUNT];
} LleColourGles;

void lle_colour_gles_default_params(LleColourDrawParams *params);

bool lle_colour_gles_init(
        LleColourGles *gles,
        int width,
        int height,
        char *error,
        size_t error_size);

bool lle_colour_gles_resize(
        LleColourGles *gles,
        int width,
        int height,
        char *error,
        size_t error_size);

/*
 * Deletes GLES names in the current owning context and releases CPU staging.
 * Use abandon when the context has already been destroyed.
 */
void lle_colour_gles_destroy(LleColourGles *gles);

/* Forgets stale GLES names without issuing GLES calls. CPU staging is kept. */
void lle_colour_gles_abandon(LleColourGles *gles);

/* Clears only the stock direction renderer's persistent low-pass state. */
void lle_colour_gles_reset_direction_velocity(LleColourGles *gles);

bool lle_colour_gles_upload_bitmap(
        LleColourGles *gles,
        JNIEnv *env,
        int slot,
        jobject bitmap,
        char *error,
        size_t error_size);

void lle_colour_gles_clear_bitmap(LleColourGles *gles, int slot);

/*
 * Draws one transparent premultiplied frame to the currently bound default
 * framebuffer. All stock intermediate fields are reconstructed internally.
 */
bool lle_colour_gles_draw(
        LleColourGles *gles,
        const LleColourDrawParticle *particles,
        size_t particle_count,
        const LleColourDrawParams *params,
        int width,
        int height,
        char *error,
        size_t error_size);

#ifdef __cplusplus
}
#endif

#endif
