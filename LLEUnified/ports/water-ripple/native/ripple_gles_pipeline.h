#ifndef LLE64_RIPPLE_GLES_PIPELINE_H
#define LLE64_RIPPLE_GLES_PIPELINE_H

#include <GLES2/gl2.h>

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum LleRippleTextureSlot {
    LLE_RIPPLE_TEXTURE_BACKGROUND = 0,
    LLE_RIPPLE_TEXTURE_WATER = 1,
    LLE_RIPPLE_TEXTURE_GRAVITY = 2,
    LLE_RIPPLE_TEXTURE_CAUSTIC_1 = 3,
    LLE_RIPPLE_TEXTURE_CAUSTIC_2 = 4
} LleRippleTextureSlot;

typedef struct LleRippleSurface {
    GLuint framebuffer;
    GLuint texture;
    GLuint renderbuffer;
    GLsizei width;
    GLsizei height;
} LleRippleSurface;

typedef struct LleRippleGravityLocations {
    GLint gravity_texture;
    GLint caustic_texture;
    GLint caustic_texture_2;
    GLint caustic_time_ratio;
    GLint caustic_time_ratio_2;
    GLint caustic_time_mix;
    GLint reference_point;
    GLint tex_move;
    GLint gravity_direction;
    GLint water_brightness;
} LleRippleGravityLocations;

typedef struct LleRippleGles {
    GLuint normal_program;
    GLuint ink_program;
    GLuint advect_density_program;
    GLuint add_ink_program;
    GLuint gravity_program;
    GLuint position_vbo;
    GLuint height_vbo;
    GLuint index_ibo;
    GLuint quad_vbo;
    GLuint background_texture;
    GLuint water_texture;
    GLuint gravity_texture;
    GLuint caustic_texture;
    GLuint caustic_texture_2;
    bool ink_blend_initialized;
    LleRippleGravityLocations gravity_locations;
} LleRippleGles;

typedef struct LleRippleRenderArgs {
    const float *vertices;
    const float *heights;
    const uint16_t *indices;
    GLsizei vertex_float_count;
    GLsizei height_float_count;
    GLsizei index_count;
    const float *mvp;
    GLsizei viewport_width;
    GLsizei viewport_height;
    GLint mesh_width;
    GLint mesh_height;
    GLint detail_width;
    GLint detail_height;
    float refractive_index;
    float reflection_ratio;
    float alpha_ratio_1;
    float alpha_ratio_2;
    float fresnel_ratio;
    float specular_ratio;
    float exponent_ratio;
    bool with_ink;
    const LleRippleSurface *density;
    float clear_ink;
    float ink_red;
    float ink_green;
    float ink_blue;
    float ink_intensity_a;
    float ink_intensity_b;
} LleRippleRenderArgs;

typedef struct LleRippleGravityRenderArgs {
    LleRippleRenderArgs base;
    float caustic_time_ratio;
    float caustic_time_ratio_2;
    float caustic_time_mix;
    float reference_point;
    float tex_move;
    bool gravity_direction;
    float water_brightness;
} LleRippleGravityRenderArgs;

typedef struct LleRippleAdvectDensityArgs {
    const LleRippleSurface *velocity;
    const LleRippleSurface *source;
    LleRippleSurface *destination;
    float time_step_x;
    float time_step_y;
    float backward_step_size;
    float dissipation;
    float scale_x;
    float scale_y;
    float center_x;
    float center_y;
    int drag;
} LleRippleAdvectDensityArgs;

typedef struct LleRippleAddInkArgs {
    const LleRippleSurface *source;
    LleRippleSurface *destination;
    float scale_x;
    float scale_y;
    float current_x;
    float current_y;
    float previous_x;
    float previous_y;
    float normal_x;
    float normal_y;
    float length;
    float radius;
    float impulse_density;
    int mode;
} LleRippleAddInkArgs;

bool lle_ripple_gles_init(LleRippleGles *gles, char *error, size_t error_size);
void lle_ripple_gles_destroy(LleRippleGles *gles);
void lle_ripple_gles_abandon(LleRippleGles *gles);

bool lle_ripple_gles_upload_rgba(
        LleRippleGles *gles,
        LleRippleTextureSlot slot,
        GLsizei width,
        GLsizei height,
        const void *pixels,
        char *error,
        size_t error_size);
void lle_ripple_gles_free_texture(LleRippleGles *gles, LleRippleTextureSlot slot);

bool lle_ripple_gles_create_surface(
        LleRippleSurface *surface,
        GLsizei width,
        GLsizei height,
        char *error,
        size_t error_size);
void lle_ripple_gles_clear_surface(
        const LleRippleSurface *surface,
        float red,
        float green,
        float blue,
        float alpha);
void lle_ripple_gles_destroy_surface(LleRippleSurface *surface);

bool lle_ripple_gles_render(
        LleRippleGles *gles,
        const LleRippleRenderArgs *args,
        char *error,
        size_t error_size);
bool lle_ripple_gles_render_gravity(
        LleRippleGles *gles,
        const LleRippleGravityRenderArgs *args,
        char *error,
        size_t error_size);
bool lle_ripple_gles_advect_density(
        LleRippleGles *gles,
        const LleRippleAdvectDensityArgs *args,
        char *error,
        size_t error_size);
bool lle_ripple_gles_add_ink(
        LleRippleGles *gles,
        const LleRippleAddInkArgs *args,
        char *error,
        size_t error_size);

#ifdef __cplusplus
}
#endif

#endif
