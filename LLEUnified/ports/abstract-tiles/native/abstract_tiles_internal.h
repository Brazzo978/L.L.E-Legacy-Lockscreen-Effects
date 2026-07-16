#ifndef LLE64_ABSTRACT_TILES_INTERNAL_H
#define LLE64_ABSTRACT_TILES_INTERNAL_H

#include <android/bitmap.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <GLES2/gl2.h>

#define AT_MAX_TRIANGLES 336
#define AT_FLOATS_PER_VERTEX 12
#define AT_VERTICES_PER_TRIANGLE 3
#define AT_MAX_VERTICES (AT_MAX_TRIANGLES * AT_VERTICES_PER_TRIANGLE)
#define AT_RANDOM_LUT_SIZE 1024
#define AT_RAY_COUNT 8
#define AT_MAX_RAY_LENGTH 48

enum AtTransformKind {
    AT_TRANSFORM_NONE = 0,
    AT_TRANSFORM_POP = 1,
    AT_TRANSFORM_UNLOCK = 2
};

typedef struct AtTriangle {
    float base[6];
    float centroid_x;
    float centroid_y;
    float start;
    float duration;
    float strength;
    float brightness;
    float proximity_alpha;
    float radial_start;
    float radial_rise;
    float radial_strength;
    float ray_start;
    float ray_strength;
    uint8_t pivot;
    uint8_t transform_kind;
    bool radial_active;
    bool ray_active;
} AtTriangle;

typedef struct AtScene {
    AtTriangle triangles[AT_MAX_TRIANGLES];
    int triangle_count;
    int width;
    int height;
    int columns;
    int rows;
    float time;
    float anchor_x;
    float anchor_y;
    float accepted_x;
    float accepted_y;
    float ray_origin_x;
    float ray_origin_y;
    float physical_radius;
    float ray_radius_squared;
    float ray_reach;
    float next_held_batch;
    float unlock_line_start;
    float unlock_line_progress;
    bool held;
    bool touch_moved;
    bool ray_scheduled;
    bool unlock_line_active;
    bool lookup_tables_ready;
    uint32_t random_state;
    uint32_t uint_lut[AT_RANDOM_LUT_SIZE];
    float float_lut[AT_RANDOM_LUT_SIZE];
    float trig_lut[AT_RANDOM_LUT_SIZE][2];
    uint16_t tile_order[AT_MAX_TRIANGLES];
    uint16_t uint_lut_cursor;
    uint16_t float_lut_cursor;
    uint16_t ray_paths[AT_RAY_COUNT][AT_MAX_RAY_LENGTH];
    uint8_t ray_lengths[AT_RAY_COUNT];
} AtScene;

typedef struct AtGles {
    GLuint tile_program;
    GLuint line_program;
    GLuint scatter_program;
    GLuint vertex_buffer;
    GLuint line_buffer;
    GLuint background_texture;
    GLuint line_mask_texture;
    int background_width;
    int background_height;
    bool ready;
} AtGles;

void at_scene_init(AtScene *scene, int width, int height);
void at_scene_reset(AtScene *scene);
void at_scene_touch(AtScene *scene, int action, float x, float y, int64_t event_time_ms);
void at_scene_realign(AtScene *scene, float x, float y);
void at_scene_affordance(AtScene *scene, int left, int top, int right, int bottom);
void at_scene_unlock(AtScene *scene);
bool at_scene_step(AtScene *scene, float elapsed_seconds);
bool at_scene_is_idle(const AtScene *scene);
int at_scene_build_vertices(
        const AtScene *scene,
        int texture_width,
        int texture_height,
        float *vertices,
        size_t vertex_capacity);

bool at_gles_init(AtGles *gles, char *error, size_t error_size);
void at_gles_abandon(AtGles *gles);
void at_gles_destroy(AtGles *gles);
bool at_gles_upload_bitmap(
        AtGles *gles,
        JNIEnv *env,
        int slot,
        jobject bitmap,
        char *error,
        size_t error_size);
void at_gles_clear_bitmap(AtGles *gles, int slot);
bool at_gles_draw(
        AtGles *gles,
        const AtScene *scene,
        int width,
        int height,
        char *error,
        size_t error_size);

#endif
