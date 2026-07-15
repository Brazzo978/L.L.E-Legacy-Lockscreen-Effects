#include "abstract_tiles_internal.h"

#include <math.h>
#include <string.h>
#include <time.h>

#define AT_PI 3.14159265358979323846f

static float at_clamp(float value, float low, float high) {
    if (value < low) return low;
    if (value > high) return high;
    return value;
}

static uint32_t at_random(AtScene *scene) {
    uint32_t value = scene->random_state;
    if (value == 0U) value = 0x6d2b79f5U;
    value ^= value << 13;
    value ^= value >> 17;
    value ^= value << 5;
    scene->random_state = value;
    return value;
}

static float at_random_unit(AtScene *scene) {
    return (float) (at_random(scene) & 0x00ffffffU) / 16777215.0f;
}

static void at_add_triangle(
        AtScene *scene,
        float ax, float ay,
        float bx, float by,
        float cx, float cy) {
    if (scene->triangle_count >= AT_MAX_TRIANGLES) return;
    AtTriangle *triangle = &scene->triangles[scene->triangle_count++];
    memset(triangle, 0, sizeof(*triangle));
    triangle->base[0] = ax;
    triangle->base[1] = ay;
    triangle->base[2] = bx;
    triangle->base[3] = by;
    triangle->base[4] = cx;
    triangle->base[5] = cy;
    triangle->centroid_x = (ax + bx + cx) / 3.0f;
    triangle->centroid_y = (ay + by + cy) / 3.0f;
    triangle->direction = 1;
}

static void at_build_grid(AtScene *scene) {
    scene->triangle_count = 0;
    const float wx = 1.0f / (float) scene->columns;
    const float hy = 1.0f / (float) scene->rows;
    for (int j = 0; j <= scene->rows; ++j) {
        for (int i = 0; i <= scene->columns; ++i) {
            const float x_left = 2.0f * (float) i * wx - 1.0f;
            const float x_center = x_left + wx;
            const float x_right = x_left + 2.0f * wx;
            const float y_top = 1.0f + hy - 2.0f * (float) j * hy;
            const float y_center = 1.0f - 2.0f * (float) j * hy;
            const float y_bottom = 1.0f - hy - 2.0f * (float) j * hy;

            at_add_triangle(scene, x_left, y_top, x_center, y_center, x_left, y_bottom);
            at_add_triangle(scene, x_right, y_bottom, x_center, y_center, x_right, y_top);
            at_add_triangle(scene,
                    x_left - wx, y_center,
                    x_left, y_bottom,
                    x_left - wx, y_bottom - hy);
            at_add_triangle(scene,
                    x_left + wx, y_bottom - hy,
                    x_left, y_bottom,
                    x_left + wx, y_center);
        }
    }
}

void at_scene_init(AtScene *scene, int width, int height) {
    if (scene == NULL) return;
    const bool portrait = height >= width;
    const bool geometry_changed = scene->width != width || scene->height != height
            || scene->triangle_count == 0;
    scene->width = width > 0 ? width : 1;
    scene->height = height > 0 ? height : 1;
    scene->columns = portrait ? 5 : 8;
    scene->rows = portrait ? 13 : 8;
    if (scene->random_state == 0U) {
        scene->random_state = (uint32_t) time(NULL)
                ^ (uint32_t) scene->width * 0x9e3779b9U
                ^ (uint32_t) scene->height * 0x85ebca6bU;
    }
    if (geometry_changed) at_build_grid(scene);
}

void at_scene_reset(AtScene *scene) {
    if (scene == NULL) return;
    scene->held = false;
    scene->time = 0.0f;
    scene->next_held_scatter = 0.0f;
    for (int i = 0; i < scene->triangle_count; ++i) {
        AtTriangle *triangle = &scene->triangles[i];
        triangle->start = 0.0f;
        triangle->duration = 0.0f;
        triangle->strength = 0.0f;
        triangle->brightness = 0.0f;
        triangle->scatter_start = 0.0f;
        triangle->scatter_duration = 0.0f;
        triangle->scatter_strength = 0.0f;
    }
}

static void at_to_normalized(const AtScene *scene, float x, float y, float *nx, float *ny) {
    /* Samsung's common JNI bridge truncates MotionEvent coordinates before normalization. */
    const float pixel_x = (float) (int32_t) x;
    const float pixel_y = (float) (int32_t) y;
    *nx = at_clamp(pixel_x / (float) scene->width, -0.25f, 1.25f);
    *ny = at_clamp(pixel_y / (float) scene->height, -0.25f, 1.25f);
}

static float at_aspect_distance_squared(
        const AtScene *scene, float ax, float ay, float bx, float by) {
    float dx = ax - bx;
    float dy = ay - by;
    if (scene->height >= scene->width) {
        dy *= (float) scene->height / (float) scene->width;
    } else {
        dx *= (float) scene->width / (float) scene->height;
    }
    return dx * dx + dy * dy;
}

static bool at_transform_record_live(const AtTriangle *triangle, float now) {
    return triangle->duration > 0.0f
            && now < triangle->start + triangle->duration + 0.3f;
}

static bool at_scatter_record_live(const AtTriangle *triangle, float now) {
    return triangle->scatter_duration > 0.0f
            && now < triangle->scatter_start + triangle->scatter_duration;
}

static void at_clear_transform_record(AtTriangle *triangle) {
    triangle->start = 0.0f;
    triangle->duration = 0.0f;
    triangle->strength = 0.0f;
    triangle->brightness = 0.0f;
}

static void at_release_touch(AtScene *scene) {
    scene->held = false;
    /* Stock UP removes only staggered records whose absolute start is still future. */
    for (int i = 0; i < scene->triangle_count; ++i) {
        AtTriangle *triangle = &scene->triangles[i];
        if (triangle->duration > 0.0f && scene->time < triangle->start) {
            at_clear_transform_record(triangle);
        }
    }
}

static void at_emit_scatter(
        AtScene *scene, float center_x, float center_y, float radius, float multiplier,
        float duration) {
    const float wx = 1.0f / (float) scene->columns;
    const float hy = 1.0f / (float) scene->rows;
    const float physical_radius = hy
            * sqrtf(fabsf((2.0f * wx - hy) / (2.0f * wx + hy)));
    for (int i = 0; i < scene->triangle_count; ++i) {
        AtTriangle *triangle = &scene->triangles[i];
        if (at_scatter_record_live(triangle, scene->time)) continue;
        float tx = (triangle->centroid_x + 1.0f) * 0.5f;
        float ty = (1.0f - triangle->centroid_y) * 0.5f;
        float distance = sqrtf(at_aspect_distance_squared(scene, tx, ty, center_x, center_y));
        if (distance > radius || distance <= 0.0f
                || (2.0f * physical_radius) / distance > 1.0f) continue;
        float delay = distance * multiplier - 0.1f;
        triangle->scatter_start = scene->time + delay;
        triangle->scatter_duration = duration * 2.0f + 0.2f;
        triangle->scatter_strength = 0.001f + at_random_unit(scene) * 0.999f;
    }
}

static void at_activate_touch(AtScene *scene, float center_x, float center_y) {
    int activation_order[AT_MAX_TRIANGLES];
    int activation_count = 0;
    /* Pop transforms are selected only by the recovered near/far probabilities. */
    for (int i = 0; i < scene->triangle_count; ++i) {
        AtTriangle *triangle = &scene->triangles[i];
        if (at_transform_record_live(triangle, scene->time)) continue;
        float tx = (triangle->centroid_x + 1.0f) * 0.5f;
        float ty = (1.0f - triangle->centroid_y) * 0.5f;
        float distance_squared = at_aspect_distance_squared(
                scene, tx, ty, center_x, center_y);
        float probability = distance_squared <= 0.1406f ? 0.8f : 0.016f;
        if (at_random_unit(scene) < probability) activation_order[activation_count++] = i;
    }

    /* OEM randomizes the selected path before assigning the 20 ms animator stagger. */
    for (int i = activation_count - 1; i > 0; --i) {
        int swap_index = (int) (at_random(scene) % (uint32_t) (i + 1));
        int temporary = activation_order[i];
        activation_order[i] = activation_order[swap_index];
        activation_order[swap_index] = temporary;
    }
    for (int order = 0; order < activation_count; ++order) {
        AtTriangle *triangle = &scene->triangles[activation_order[order]];
        triangle->start = scene->time + (float) order * 0.02f;
        triangle->duration = 0.4f;
        triangle->strength = at_random_unit(scene) < 0.5f ? 0.5f : 1.0f;
        triangle->brightness = (at_random_unit(scene) * 0.75f) - 0.375f;
        triangle->pivot = (uint8_t) (at_random(scene) % 3U);
    }
    at_emit_scatter(scene, center_x, center_y, 0.6f, 0.5f, 0.5f);
}

void at_scene_touch(AtScene *scene, int action, float x, float y, int64_t event_time_ms) {
    if (scene == NULL || scene->triangle_count == 0) return;
    float nx;
    float ny;
    at_to_normalized(scene, x, y, &nx, &ny);
    if (event_time_ms > 0 && scene->random_state == 0U) {
        scene->random_state = (uint32_t) event_time_ms ^ 0xa511e9b3U;
    }
    switch (action) {
        case 0:
            scene->held = true;
            scene->anchor_x = nx;
            scene->anchor_y = ny;
            scene->accepted_x = nx;
            scene->accepted_y = ny;
            scene->next_held_scatter = scene->time + 0.16f;
            at_activate_touch(scene, nx, ny);
            break;
        case 2: {
            if (!scene->held) break;
            scene->anchor_x = nx;
            scene->anchor_y = ny;
            const float threshold = 0.25f / ((float) scene->rows * (float) scene->rows);
            if (at_aspect_distance_squared(
                    scene, nx, ny, scene->accepted_x, scene->accepted_y) > threshold) {
                scene->accepted_x = nx;
                scene->accepted_y = ny;
                at_activate_touch(scene, nx, ny);
                scene->next_held_scatter = scene->time + 0.16f;
            }
            break;
        }
        case 1:
        case 3:
            at_release_touch(scene);
            break;
        default:
            break;
    }
}

void at_scene_realign(AtScene *scene, float x, float y) {
    if (scene == NULL || !scene->held) return;
    at_to_normalized(scene, x, y, &scene->anchor_x, &scene->anchor_y);
    scene->accepted_x = scene->anchor_x;
    scene->accepted_y = scene->anchor_y;
}

void at_scene_affordance(AtScene *scene, int left, int top, int right, int bottom) {
    if (scene == NULL) return;
    at_scene_reset(scene);
    float center_x = ((float) left + (float) right) * 0.5f;
    float center_y = ((float) top + (float) bottom) * 0.5f
            - (float) scene->height / (2.0f * (float) scene->rows);
    float nx;
    float ny;
    at_to_normalized(scene, center_x, center_y, &nx, &ny);
    scene->anchor_x = nx;
    scene->anchor_y = ny;
    at_emit_scatter(scene, nx, ny, 2.0f, 0.5f, 0.3f);
}

void at_scene_unlock(AtScene *scene) {
    if (scene == NULL) return;
    at_release_touch(scene);
    for (int i = 0; i < scene->triangle_count; ++i) {
        AtTriangle *triangle = &scene->triangles[i];
        if (at_transform_record_live(triangle, scene->time)) continue;
        float tx = (triangle->centroid_x + 1.0f) * 0.5f;
        float ty = (1.0f - triangle->centroid_y) * 0.5f;
        float distance_squared = at_aspect_distance_squared(
                scene, tx, ty, scene->anchor_x, scene->anchor_y);
        if (distance_squared > 2.812f) continue;
        float probability = distance_squared <= 0.1406f ? 0.8f : 0.016f;
        if (at_random_unit(scene) > probability) continue;
        triangle->start = scene->time;
        triangle->duration = 0.9f;
        triangle->strength = at_random_unit(scene) < 0.5f ? 1.0f : 2.0f;
        triangle->brightness = (at_random_unit(scene) * 0.75f) - 0.375f;
        triangle->pivot = (uint8_t) (at_random(scene) % 3U);
        triangle->direction = (at_random(scene) & 1U) != 0U ? 1 : -1;
    }
}

bool at_scene_step(AtScene *scene, float elapsed_seconds) {
    if (scene == NULL || !isfinite(elapsed_seconds) || elapsed_seconds < 0.0f) return false;
    scene->time += elapsed_seconds;
    if (scene->held && scene->time >= scene->next_held_scatter) {
        at_activate_touch(scene, scene->anchor_x, scene->anchor_y);
        scene->next_held_scatter = scene->time + 0.16f;
    }
    return true;
}

static bool at_triangle_active(const AtTriangle *triangle, float now) {
    return at_transform_record_live(triangle, now);
}

static bool at_scatter_active(const AtTriangle *triangle, float now) {
    return triangle->scatter_duration > 0.0f
            && now >= triangle->scatter_start
            && now < triangle->scatter_start + triangle->scatter_duration;
}

bool at_scene_is_idle(const AtScene *scene) {
    if (scene == NULL) return true;
    if (scene->held) return false;
    for (int i = 0; i < scene->triangle_count; ++i) {
        if (at_triangle_active(&scene->triangles[i], scene->time)
                || at_scatter_record_live(&scene->triangles[i], scene->time)) return false;
    }
    return true;
}

static float at_animation_envelope(const AtTriangle *triangle, float now) {
    if (triangle->duration <= 0.0f || now < triangle->start) return 0.0f;
    float progress = (now - triangle->start) / triangle->duration;
    if (progress < 0.0f || progress >= 1.0f) return 0.0f;
    /* Absolute monotonic progress; geometry is rebuilt from base and restored at completion. */
    return progress;
}

static float at_scatter_envelope(const AtTriangle *triangle, float now) {
    if (!at_scatter_active(triangle, now)) return 0.0f;
    const float rise_duration = (triangle->scatter_duration - 0.2f) * 0.5f;
    const float elapsed = now - triangle->scatter_start;
    float envelope;
    if (elapsed < rise_duration) {
        envelope = elapsed / rise_duration;
    } else {
        envelope = 1.0f - (elapsed - rise_duration) / (rise_duration + 0.2f);
    }
    return at_clamp(envelope, 0.0f, 1.0f) * triangle->scatter_strength;
}

static float at_tile_alpha(const AtTriangle *triangle, float now) {
    if (triangle->duration <= 0.0f || now < triangle->start) return 0.0f;
    const float elapsed = now - triangle->start;
    if (elapsed >= triangle->duration) return 0.0f;
    if (elapsed <= 0.2f) return 0.3f;
    const float fade_duration = triangle->duration - 0.2f;
    if (fade_duration <= 0.0f) return 0.0f;
    return 0.3f * at_clamp(1.0f - (elapsed - 0.2f) / fade_duration, 0.0f, 1.0f);
}

int at_scene_build_vertices(
        const AtScene *scene,
        int texture_width,
        int texture_height,
        float *vertices,
        size_t vertex_capacity) {
    if (scene == NULL || vertices == NULL || texture_width <= 0 || texture_height <= 0) {
        return 0;
    }
    size_t required = (size_t) scene->triangle_count * AT_VERTICES_PER_TRIANGLE
            * AT_FLOATS_PER_VERTEX;
    if (vertex_capacity < required) return 0;

    float sx = (float) scene->width / (float) texture_width;
    float sy = (float) scene->height / (float) texture_height;
    float crop_x = sy > sx ? fabsf(sx / sy - 1.0f) * 0.5f : 0.0f;
    float crop_y = sy <= sx ? fabsf(sy / sx - 1.0f) * 0.5f : 0.0f;
    int emitted = 0;
    for (int i = 0; i < scene->triangle_count; ++i) {
        const AtTriangle *triangle = &scene->triangles[i];
        float envelope = at_animation_envelope(triangle, scene->time);
        float scatter = at_scatter_envelope(triangle, scene->time);
        float alpha = at_tile_alpha(triangle, scene->time);
        float brightness = triangle->brightness * envelope;
        for (int vertex_index = 0; vertex_index < 3; ++vertex_index) {
            /* Keep OEM mesh indices stable: the eleven seam strips address this flat array. */
            int source_index = vertex_index;
            float px = triangle->base[source_index * 2];
            float py = triangle->base[source_index * 2 + 1];
            const int pivot = triangle->pivot % 3;
            float pivot_x = triangle->base[pivot * 2];
            float pivot_y = triangle->base[pivot * 2 + 1];
            if (source_index != pivot) {
                px += (px - pivot_x) * triangle->strength * envelope;
                py += (py - pivot_y) * triangle->strength * envelope;
            }
            /* OEM keeps texture coordinates in a separate, undeformed buffer. */
            float base_x = triangle->base[source_index * 2];
            float base_y = triangle->base[source_index * 2 + 1];
            float u = crop_x + (1.0f - 2.0f * crop_x) * (1.0f + base_x) * 0.5f;
            float v = crop_y + (1.0f - 2.0f * crop_y) * (1.0f - base_y) * 0.5f;
            float *out = &vertices[(size_t) emitted * AT_FLOATS_PER_VERTEX];
            out[0] = px;
            out[1] = py;
            out[2] = u;
            out[3] = v;
            out[4] = vertex_index == 0 ? 1.0f : 0.0f;
            out[5] = vertex_index == 1 ? 1.0f : 0.0f;
            out[6] = vertex_index == 2 ? 1.0f : 0.0f;
            out[7] = alpha;
            out[8] = brightness;
            out[9] = scatter;
            ++emitted;
        }
    }
    return emitted;
}
