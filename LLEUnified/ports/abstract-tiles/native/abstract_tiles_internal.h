#ifndef LLE64_ABSTRACT_TILES_INTERNAL_H
#define LLE64_ABSTRACT_TILES_INTERNAL_H

#include <android/bitmap.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <GLES2/gl2.h>

#define AT_MAX_TRIANGLES 336
#define AT_FLOATS_PER_VERTEX 10
#define AT_VERTICES_PER_TRIANGLE 3
#define AT_MAX_VERTICES (AT_MAX_TRIANGLES * AT_VERTICES_PER_TRIANGLE)

typedef struct AtTriangle {
    float base[6];
    float centroid_x;
    float centroid_y;
    float start;
    float duration;
    float strength;
    float brightness;
    float scatter_start;
    float scatter_duration;
    float scatter_strength;
    uint8_t pivot;
    int8_t direction;
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
    float next_held_scatter;
    bool held;
    uint32_t random_state;
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
