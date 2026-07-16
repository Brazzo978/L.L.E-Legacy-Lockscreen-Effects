#include "abstract_tiles_internal.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define AT_PI 3.14159265358979323846f
#define AT_TWO_PI (2.0f * AT_PI)
#define AT_NORMAL_POP_DURATION 0.4f
#define AT_UNLOCK_DURATION 0.9f
#define AT_POP_INTERVAL 0.16f
#define AT_RAY_STOP_DISTANCE_SQUARED 0.8f
#define AT_RAND_PI_OVER_4 3.6572952999414099e-10f
#define AT_RAND_PI 1.462918119976564e-9f

static float at_clamp(float value, float low, float high) {
    if (value < low) return low;
    if (value > high) return high;
    return value;
}

static uint32_t at_random(AtScene *scene) {
    (void) scene;
    return (uint32_t) rand() & 0x7fffffffU;
}

static void at_initialize_lookup_tables(AtScene *scene) {
    if (scene->lookup_tables_ready) return;

    /* The ARM32 engine creates both 1024-entry tables before its per-scene seed. */
    /* Bionic's unseeded rand() state is the sequence produced by seed 1.
     * Seed it explicitly so table construction remains identical even if a
     * host library happened to consume the process-global generator first. */
    srand(1U);
    for (int i = 0; i < AT_RANDOM_LUT_SIZE; ++i) {
        scene->float_lut[i] = (float) ((uint32_t) rand() & 0x7fffffffU)
                * (1.0f / 2147483648.0f);
    }
    for (int i = 0; i < AT_RANDOM_LUT_SIZE; ++i) {
        scene->uint_lut[i] = (uint32_t) rand() & 0x7fffffffU;
    }
    for (int i = 0; i < AT_RANDOM_LUT_SIZE; ++i) {
        const float angle = (float) i * AT_TWO_PI / (float) AT_RANDOM_LUT_SIZE;
        scene->trig_lut[i][0] = sinf(angle);
        scene->trig_lut[i][1] = cosf(angle);
    }
    scene->uint_lut_cursor = 0U;
    scene->float_lut_cursor = 0U;
    scene->lookup_tables_ready = true;
}

static uint32_t at_next_uint_lut(AtScene *scene) {
    scene->uint_lut_cursor = (uint16_t) ((scene->uint_lut_cursor + 1U)
            & (AT_RANDOM_LUT_SIZE - 1U));
    return scene->uint_lut[scene->uint_lut_cursor];
}

static float at_next_float_lut(AtScene *scene) {
    scene->float_lut_cursor = (uint16_t) ((scene->float_lut_cursor + 1U)
            & (AT_RANDOM_LUT_SIZE - 1U));
    return scene->float_lut[scene->float_lut_cursor];
}

static void at_shuffle_tile_order(AtScene *scene) {
    scene->uint_lut_cursor = 0U;
    for (int i = 0; i < scene->triangle_count; ++i) {
        scene->tile_order[i] = (uint16_t) i;
    }
    /* FUN_16BE8 shuffles complete Tile triangles (position and UV together)
     * once per clear/rebuild. Scatter keeps the original mesh order. */
    for (int i = 0; i < scene->triangle_count; ++i) {
        const int random_index = (int) (at_next_uint_lut(scene)
                % (uint32_t) scene->triangle_count);
        const uint16_t swap = scene->tile_order[i];
        scene->tile_order[i] = scene->tile_order[random_index];
        scene->tile_order[random_index] = swap;
    }
}

static void at_lookup_sin_cos(
        const AtScene *scene, float angle, float *sine, float *cosine) {
    while (angle < 0.0f) angle += AT_TWO_PI;
    while (angle >= AT_TWO_PI) angle -= AT_TWO_PI;
    int index = (int) (angle * ((float) AT_RANDOM_LUT_SIZE / AT_TWO_PI));
    index &= AT_RANDOM_LUT_SIZE - 1;
    *sine = scene->trig_lut[index][0];
    *cosine = scene->trig_lut[index][1];
}

static void at_add_triangle(
        AtScene *scene,
        float ax,
        float ay,
        float bx,
        float by,
        float cx,
        float cy) {
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
}

static void at_build_grid(AtScene *scene) {
    scene->triangle_count = 0;
    const float wx = 1.0f / (float) scene->columns;
    const float hy = 1.0f / (float) scene->rows;
    for (int row = 0; row <= scene->rows; ++row) {
        for (int column = 0; column <= scene->columns; ++column) {
            const float x_left = 2.0f * (float) column * wx - 1.0f;
            const float x_center = x_left + wx;
            const float x_right = x_left + 2.0f * wx;
            const float y_top = 1.0f + hy - 2.0f * (float) row * hy;
            const float y_center = 1.0f - 2.0f * (float) row * hy;
            const float y_bottom = 1.0f - hy - 2.0f * (float) row * hy;

            at_add_triangle(scene, x_left, y_top, x_center, y_center, x_left, y_bottom);
            at_add_triangle(scene, x_right, y_bottom, x_center, y_center, x_right, y_top);
            at_add_triangle(
                    scene,
                    x_left - wx,
                    y_center,
                    x_left,
                    y_bottom,
                    x_left - wx,
                    y_bottom - hy);
            at_add_triangle(
                    scene,
                    x_left + wx,
                    y_bottom - hy,
                    x_left,
                    y_bottom,
                    x_left + wx,
                    y_center);
        }
    }
}

static void at_clear_triangle_state(AtTriangle *triangle) {
    triangle->start = 0.0f;
    triangle->duration = 0.0f;
    triangle->strength = 0.0f;
    triangle->brightness = 0.0f;
    triangle->proximity_alpha = 0.0f;
    triangle->radial_start = 0.0f;
    triangle->radial_rise = 0.0f;
    triangle->radial_strength = 0.0f;
    triangle->ray_start = 0.0f;
    triangle->ray_strength = 0.0f;
    triangle->pivot = 0U;
    triangle->transform_kind = AT_TRANSFORM_NONE;
    triangle->radial_active = false;
    triangle->ray_active = false;
}

void at_scene_reset(AtScene *scene) {
    if (scene == NULL) return;
    scene->time = 0.0f;
    scene->anchor_x = 0.0f;
    scene->anchor_y = 0.0f;
    scene->accepted_x = 0.0f;
    scene->accepted_y = 0.0f;
    scene->ray_origin_x = 0.0f;
    scene->ray_origin_y = 0.0f;
    scene->next_held_batch = 0.0f;
    scene->unlock_line_start = 0.0f;
    scene->unlock_line_progress = 0.0f;
    scene->held = false;
    scene->touch_moved = false;
    scene->ray_scheduled = false;
    scene->unlock_line_active = false;
    memset(scene->ray_paths, 0, sizeof(scene->ray_paths));
    memset(scene->ray_lengths, 0, sizeof(scene->ray_lengths));
    for (int i = 0; i < scene->triangle_count; ++i) {
        at_clear_triangle_state(&scene->triangles[i]);
    }
    if (scene->triangle_count > 0 && scene->lookup_tables_ready) {
        /* FUN_16BE8 restarts its unsigned-table cursor at zero, then consumes
         * entries 1..triangle_count while permuting complete Tile records. */
        at_shuffle_tile_order(scene);
    }
}

void at_scene_init(AtScene *scene, int width, int height) {
    if (scene == NULL) return;
    const int safe_width = width > 0 ? width : 1;
    const int safe_height = height > 0 ? height : 1;
    const bool portrait = safe_height >= safe_width;
    const int columns = portrait ? 5 : 8;
    const int rows = portrait ? 13 : 8;
    const bool geometry_changed = scene->width != safe_width
            || scene->height != safe_height
            || scene->columns != columns
            || scene->rows != rows
            || scene->triangle_count == 0;

    at_initialize_lookup_tables(scene);
    scene->width = safe_width;
    scene->height = safe_height;
    scene->columns = columns;
    scene->rows = rows;
    const float wx = 1.0f / (float) columns;
    const float hy = 1.0f / (float) rows;
    scene->physical_radius = hy
            * sqrtf(fabsf((2.0f * wx - hy) / (2.0f * wx + hy)));
    const float display_aspect = (float) safe_width / (float) safe_height;
    const float ray_radius_multiplier = display_aspect >= 0.82f ? 1.25f : 3.25f;
    scene->ray_radius_squared = ray_radius_multiplier
            * scene->physical_radius * scene->physical_radius;
    scene->ray_reach = 10.0f * sqrtf(wx * wx + hy * hy);
    if (scene->random_state == 0U) {
        const uint32_t seed = (uint32_t) time(NULL);
        srand(seed);
        /* random_state is only the per-scene initialization marker now; the
         * legacy implementation itself consumes Bionic's global rand(). */
        scene->random_state = seed != 0U ? seed : 1U;
    }
    if (geometry_changed) {
        at_build_grid(scene);
        at_scene_reset(scene);
    }
}

static void at_to_clip(const AtScene *scene, float x, float y, float *clip_x, float *clip_y) {
    /* The legacy bridge truncates MotionEvent coordinates before normalization. */
    const float pixel_x = (float) (int32_t) x;
    const float pixel_y = (float) (int32_t) y;
    *clip_x = 2.0f * pixel_x / (float) scene->width - 1.0f;
    *clip_y = 1.0f - 2.0f * pixel_y / (float) scene->height;
}

static void at_aspect_scales(const AtScene *scene, float *scale_x, float *scale_y) {
    if (scene->height >= scene->width) {
        *scale_x = 1.0f;
        *scale_y = (float) scene->height / (float) scene->width;
    } else {
        *scale_x = (float) scene->width / (float) scene->height;
        *scale_y = 1.0f;
    }
}

static float at_aspect_distance_squared(
        const AtScene *scene, float ax, float ay, float bx, float by) {
    float scale_x;
    float scale_y;
    at_aspect_scales(scene, &scale_x, &scale_y);
    const float dx = (ax - bx) * scale_x;
    const float dy = (ay - by) * scale_y;
    return dx * dx + dy * dy;
}

static float at_normalized_distance_squared(
        const AtScene *scene, float ax, float ay, float bx, float by) {
    /* Pop and unlock convert clip coordinates to [0,1] before applying their
     * recovered probability thresholds. Proximity, radial and rays do not. */
    return 0.25f * at_aspect_distance_squared(scene, ax, ay, bx, by);
}

static float at_point_segment_distance_squared(
        float px,
        float py,
        float ax,
        float ay,
        float bx,
        float by) {
    const float abx = bx - ax;
    const float aby = by - ay;
    const float length_squared = abx * abx + aby * aby;
    if (length_squared <= 1.0e-12f) {
        const float dx = px - ax;
        const float dy = py - ay;
        return dx * dx + dy * dy;
    }
    const float t = at_clamp(
            ((px - ax) * abx + (py - ay) * aby) / length_squared,
            0.0f,
            1.0f);
    const float dx = px - (ax + t * abx);
    const float dy = py - (ay + t * aby);
    return dx * dx + dy * dy;
}

static bool at_triangle_touches_circle(
        const AtScene *scene,
        const AtTriangle *triangle,
        float center_x,
        float center_y,
        float radius_squared) {
    float scale_x;
    float scale_y;
    at_aspect_scales(scene, &scale_x, &scale_y);
    const float ax = (triangle->base[0] - center_x) * scale_x;
    const float ay = (triangle->base[1] - center_y) * scale_y;
    const float bx = (triangle->base[2] - center_x) * scale_x;
    const float by = (triangle->base[3] - center_y) * scale_y;
    const float cx = (triangle->base[4] - center_x) * scale_x;
    const float cy = (triangle->base[5] - center_y) * scale_y;
    /* The ARM32 routine tests only its three edge segments. The separate
     * centroid ratio handles the inner-radius case. */
    return at_point_segment_distance_squared(0.0f, 0.0f, ax, ay, bx, by)
                    <= radius_squared
            || at_point_segment_distance_squared(0.0f, 0.0f, bx, by, cx, cy)
                    <= radius_squared
            || at_point_segment_distance_squared(0.0f, 0.0f, cx, cy, ax, ay)
                    <= radius_squared;
}

static bool at_triangle_intersects_capsule(
        const AtScene *scene,
        const AtTriangle *triangle,
        float start_x,
        float start_y,
        float end_x,
        float end_y,
        float radius_squared) {
    float scale_x;
    float scale_y;
    at_aspect_scales(scene, &scale_x, &scale_y);
    const float sx = start_x * scale_x;
    const float sy = start_y * scale_y;
    const float ex = end_x * scale_x;
    const float ey = end_y * scale_y;
    const float center_x = triangle->centroid_x * scale_x;
    const float center_y = triangle->centroid_y * scale_y;
    /* The stock quadratic tests the ray segment against a radius-r circle at
     * the triangle centroid; it does not expand the full triangle outline. */
    return at_point_segment_distance_squared(center_x, center_y, sx, sy, ex, ey)
            <= radius_squared;
}

static bool at_transform_record_live(const AtTriangle *triangle, float now) {
    return triangle->transform_kind != AT_TRANSFORM_NONE
            && triangle->duration > 0.0f
            && now < triangle->start + triangle->duration;
}

static void at_clear_transform_record(AtTriangle *triangle) {
    triangle->start = 0.0f;
    triangle->duration = 0.0f;
    triangle->strength = 0.0f;
    triangle->brightness = 0.0f;
    triangle->pivot = 0U;
    triangle->transform_kind = AT_TRANSFORM_NONE;
}

static void at_release_touch(AtScene *scene) {
    scene->held = false;
    scene->touch_moved = false;
    /* UP only removes delayed pop animators whose absolute start is still future. */
    for (int slot = 0; slot < scene->triangle_count; ++slot) {
        AtTriangle *triangle = &scene->triangles[scene->tile_order[slot]];
        if (triangle->transform_kind == AT_TRANSFORM_POP && scene->time < triangle->start) {
            at_clear_transform_record(triangle);
        }
    }
}

static bool at_triangle_is_on_screen(
        const AtScene *scene, const AtTriangle *triangle) {
    const float normalized_x = triangle->centroid_x * 0.5f + 0.5f;
    const float normalized_y = triangle->centroid_y * 0.5f + 0.5f;
    const float bottom_half_cell = 0.5f / (float) scene->rows;
    return normalized_x >= 0.0f && normalized_x < 1.0f
            && normalized_y > bottom_half_cell && normalized_y < 1.0f;
}

static void at_pop_batch(AtScene *scene, float center_x, float center_y) {
    float stagger = 0.0f;
    for (int slot = 0; slot < scene->triangle_count; ++slot) {
        AtTriangle *triangle = &scene->triangles[scene->tile_order[slot]];
        if (at_transform_record_live(triangle, scene->time)) continue;
        if (!at_triangle_is_on_screen(scene, triangle)) continue;
        const float distance_squared = at_normalized_distance_squared(
                scene,
                triangle->centroid_x,
                triangle->centroid_y,
                center_x,
                center_y);
        const float probability = distance_squared < 0.1406f ? 0.8f : 0.016f;
        if (at_next_float_lut(scene) > probability) continue;

        triangle->start = scene->time + stagger;
        triangle->duration = AT_NORMAL_POP_DURATION;
        triangle->pivot = (uint8_t) (at_next_uint_lut(scene) % 3U);
        triangle->strength = (at_next_uint_lut(scene) & 1U) == 0U ? 0.5f : 1.0f;
        triangle->brightness = at_next_float_lut(scene) * 0.75f - 0.375f;
        triangle->transform_kind = AT_TRANSFORM_POP;
        stagger += 0.02f;
    }
}

static void at_schedule_radial(
        AtScene *scene,
        float center_x,
        float center_y,
        float radius,
        float delay_multiplier,
        float rise) {
    for (int i = 0; i < scene->triangle_count; ++i) {
        AtTriangle *triangle = &scene->triangles[i];
        if (triangle->radial_active) continue;
        const float distance_squared = at_aspect_distance_squared(
                scene,
                triangle->centroid_x,
                triangle->centroid_y,
                center_x,
                center_y);
        if (distance_squared <= 1.0e-12f) continue;
        const float distance = sqrtf(distance_squared);
        const float diameter_ratio = (2.0f * scene->physical_radius) / distance;
        if (diameter_ratio > 1.0f || distance > radius) continue;
        if (at_next_uint_lut(scene) <= 0x03ffffffU) continue;

        triangle->radial_start = scene->time + distance * delay_multiplier - 0.1f;
        triangle->radial_rise = rise;
        triangle->radial_strength = 0.12f * at_next_float_lut(scene)
                / powf(diameter_ratio, 0.7f);
        triangle->radial_active = true;
    }
}

static int at_find_ray_candidate(
        const AtScene *scene,
        float start_x,
        float start_y,
        float end_x,
        float end_y) {
    int nearest = -1;
    int second = -1;
    float nearest_distance = INFINITY;
    float second_distance = INFINITY;
    const float radius_squared = scene->ray_radius_squared;

    for (int i = 0; i < scene->triangle_count; ++i) {
        const AtTriangle *triangle = &scene->triangles[i];
        if (!at_triangle_intersects_capsule(
                    scene,
                    triangle,
                    start_x,
                    start_y,
                    end_x,
                    end_y,
                    radius_squared)) {
            continue;
        }
        const float distance = at_aspect_distance_squared(
                scene,
                start_x,
                start_y,
                triangle->centroid_x,
                triangle->centroid_y);
        if (distance < nearest_distance) {
            second = nearest;
            second_distance = nearest_distance;
            nearest = i;
            nearest_distance = distance;
        } else if (distance < second_distance) {
            second = i;
            second_distance = distance;
        }
    }

    (void) nearest;
    /* The binary deliberately appends the second-nearest intersection. The
     * nearest is normally the tile containing the current ray point. */
    return second;
}

static void at_build_ray_paths(AtScene *scene, float origin_x, float origin_y) {
    memset(scene->ray_paths, 0, sizeof(scene->ray_paths));
    memset(scene->ray_lengths, 0, sizeof(scene->ray_lengths));
    /* FUN_1B628 cancels the previous ray animator group before constructing a
     * new DOWN path set, including tails that have not started yet. */
    for (int i = 0; i < scene->triangle_count; ++i) {
        scene->triangles[i].ray_start = 0.0f;
        scene->triangles[i].ray_strength = 0.0f;
        scene->triangles[i].ray_active = false;
    }
    scene->ray_origin_x = origin_x;
    scene->ray_origin_y = origin_y;
    scene->ray_scheduled = false;

    for (int ray = 0; ray < AT_RAY_COUNT; ++ray) {
        const float base_theta = (float) ray * (AT_PI / 4.0f)
                + (float) at_random(scene) * AT_RAND_PI_OVER_4;
        float theta = base_theta;
        float current_x = origin_x;
        float current_y = origin_y;
        bool narrow_next = false;
        int length = 0;
        while (length < AT_MAX_RAY_LENGTH) {
            float sine;
            float cosine;
            at_lookup_sin_cos(scene, theta, &sine, &cosine);
            const float end_x = scene->ray_reach * cosine;
            const float end_y = scene->ray_reach * sine;
            int candidate = at_find_ray_candidate(
                    scene,
                    current_x,
                    current_y,
                    end_x,
                    end_y);
            if (candidate < 0) break;
            scene->ray_paths[ray][length++] = (uint16_t) candidate;
            const AtTriangle *triangle = &scene->triangles[candidate];
            current_x = triangle->centroid_x;
            current_y = triangle->centroid_y;
            /* FUN_2A710 consumes the next turn's rand() before checking the
             * 0.8 stop distance, even when that angle will never be used. */
            if (narrow_next) {
                theta = base_theta - AT_PI / 8.0f
                        + (float) at_random(scene) * AT_RAND_PI_OVER_4;
                narrow_next = false;
            } else if (length >= 3) {
                theta = base_theta - AT_PI / 2.0f
                        + (float) at_random(scene) * AT_RAND_PI;
                narrow_next = true;
            }
            if (at_aspect_distance_squared(
                        scene, origin_x, origin_y, current_x, current_y)
                    >= AT_RAY_STOP_DISTANCE_SQUARED) {
                break;
            }
        }
        scene->ray_lengths[ray] = (uint8_t) length;
    }
}

static void at_schedule_rays(AtScene *scene) {
    if (scene->ray_scheduled) return;
    for (int ray = 0; ray < AT_RAY_COUNT; ++ray) {
        const int length = scene->ray_lengths[ray];
        for (int path_index = 0; path_index < length; ++path_index) {
            const int triangle_index = (int) scene->ray_paths[ray][path_index];
            AtTriangle *triangle = &scene->triangles[triangle_index];
            const float distance_squared = at_aspect_distance_squared(
                    scene,
                    scene->ray_origin_x,
                    scene->ray_origin_y,
                    triangle->centroid_x,
                    triangle->centroid_y);
            const float base = at_clamp(1.2f - distance_squared, 0.0f, 1.2f);
            triangle->ray_start = scene->time + (float) path_index * 0.1f;
            triangle->ray_strength = (0.21f + 0.2f * at_next_float_lut(scene))
                    * powf(base, 1.3f);
            triangle->ray_active = true;
        }
    }
    scene->ray_scheduled = true;
}

static void at_update_proximity(AtScene *scene, float elapsed_seconds) {
    const float radius_squared = scene->physical_radius * scene->physical_radius;
    for (int i = 0; i < scene->triangle_count; ++i) {
        AtTriangle *triangle = &scene->triangles[i];
        bool near = false;
        float cap = 0.0f;
        if (scene->held) {
            const float distance_squared = at_aspect_distance_squared(
                    scene,
                    triangle->centroid_x,
                    triangle->centroid_y,
                    scene->anchor_x,
                    scene->anchor_y);
            const float distance = sqrtf(distance_squared);
            const float ratio = distance > 1.0e-8f
                    ? scene->physical_radius / distance
                    : INFINITY;
            near = ratio > 1.0f
                    || at_triangle_touches_circle(
                            scene,
                            triangle,
                            scene->anchor_x,
                            scene->anchor_y,
                            radius_squared);
            cap = 0.045f
                    * powf(at_clamp(ratio, 0.0f, 1.0f) + 1.5f, 2.5f);
        }

        const bool even = (i & 1) == 0;
        float delta;
        if (near) {
            delta = scene->touch_moved
                    ? 9.0f * elapsed_seconds
                    : (even ? 3.0f : 2.1f) * elapsed_seconds;
            triangle->proximity_alpha = fminf(
                    cap,
                    triangle->proximity_alpha + delta);
        } else {
            delta = scene->touch_moved
                    ? (even ? 4.0f : 2.8f) * elapsed_seconds
                    : (even ? 3.0f : 2.1f) * elapsed_seconds;
            triangle->proximity_alpha = fmaxf(
                    0.0f,
                    triangle->proximity_alpha - delta);
        }
    }
}

void at_scene_touch(AtScene *scene, int action, float x, float y, int64_t event_time_ms) {
    if (scene == NULL || scene->triangle_count == 0) return;
    (void) event_time_ms;
    float clip_x;
    float clip_y;
    at_to_clip(scene, x, y, &clip_x, &clip_y);
    switch (action) {
        case 0:
            scene->held = true;
            scene->touch_moved = false;
            scene->anchor_x = clip_x;
            scene->anchor_y = clip_y;
            scene->accepted_x = clip_x;
            scene->accepted_y = clip_y;
            scene->next_held_batch = scene->time + AT_POP_INTERVAL;
            at_build_ray_paths(scene, clip_x, clip_y);
            at_schedule_radial(scene, clip_x, clip_y, 0.6f, 0.5f, 0.5f);
            at_pop_batch(scene, clip_x, clip_y);
            /* OEM schedules the built ray animators in the draw immediately
             * following DOWN, after radial and pop have consumed their LUTs. */
            at_schedule_rays(scene);
            break;
        case 2: {
            if (!scene->held) break;
            scene->touch_moved = true;
            scene->anchor_x = clip_x;
            scene->anchor_y = clip_y;
            const float hy = 1.0f / (float) scene->rows;
            /* FUN_13A7C compares .25*hy^2 in normalized coordinates, which
             * is hy^2 after converting the delta back to clip space. */
            const float threshold = hy * hy;
            if (at_aspect_distance_squared(
                        scene,
                        clip_x,
                        clip_y,
                        scene->accepted_x,
                        scene->accepted_y)
                    > threshold) {
                scene->accepted_x = clip_x;
                scene->accepted_y = clip_y;
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
    at_to_clip(scene, x, y, &scene->anchor_x, &scene->anchor_y);
    scene->accepted_x = scene->anchor_x;
    scene->accepted_y = scene->anchor_y;
}

void at_scene_affordance(AtScene *scene, int left, int top, int right, int bottom) {
    if (scene == NULL) return;
    at_scene_reset(scene);
    const float center_x = ((float) left + (float) right) * 0.5f;
    const float center_y = ((float) top + (float) bottom) * 0.5f
            - (float) scene->height / (2.0f * (float) scene->rows);
    float clip_x;
    float clip_y;
    at_to_clip(scene, center_x, center_y, &clip_x, &clip_y);
    scene->anchor_x = clip_x;
    scene->anchor_y = clip_y;
    at_schedule_radial(scene, clip_x, clip_y, 2.0f, 0.5f, 0.3f);
}

void at_scene_unlock(AtScene *scene) {
    if (scene == NULL) return;
    at_release_touch(scene);
    for (int slot = 0; slot < scene->triangle_count; ++slot) {
        AtTriangle *triangle = &scene->triangles[scene->tile_order[slot]];
        if (at_transform_record_live(triangle, scene->time)) continue;
        if (!at_triangle_is_on_screen(scene, triangle)) continue;
        const float distance_squared = at_normalized_distance_squared(
                scene,
                triangle->centroid_x,
                triangle->centroid_y,
                scene->anchor_x,
                scene->anchor_y);
        const float probability = distance_squared < 20.0f * 0.1406f ? 0.8f : 0.016f;
        if (at_next_float_lut(scene) > probability) continue;
        triangle->start = scene->time;
        triangle->duration = AT_UNLOCK_DURATION;
        triangle->pivot = (uint8_t) (at_next_uint_lut(scene) % 3U);
        triangle->strength = (at_next_uint_lut(scene) & 1U) == 0U ? 1.0f : 2.0f;
        triangle->brightness = at_next_float_lut(scene) * 0.75f - 0.375f;
        triangle->transform_kind = AT_TRANSFORM_UNLOCK;
    }
    scene->unlock_line_start = scene->time;
    scene->unlock_line_progress = 0.0f;
    scene->unlock_line_active = true;
}

static void at_cleanup_completed_records(AtScene *scene) {
    for (int i = 0; i < scene->triangle_count; ++i) {
        AtTriangle *triangle = &scene->triangles[i];
        if (triangle->transform_kind != AT_TRANSFORM_NONE
                && scene->time >= triangle->start + triangle->duration) {
            at_clear_transform_record(triangle);
        }
        if (triangle->radial_active
                && scene->time
                        >= triangle->radial_start + 2.0f * triangle->radial_rise + 0.2f) {
            triangle->radial_active = false;
            triangle->radial_start = 0.0f;
            triangle->radial_rise = 0.0f;
            triangle->radial_strength = 0.0f;
        }
        if (triangle->ray_active && scene->time >= triangle->ray_start + 0.2f) {
            triangle->ray_active = false;
            triangle->ray_start = 0.0f;
            triangle->ray_strength = 0.0f;
        }
    }
}

bool at_scene_step(AtScene *scene, float elapsed_seconds) {
    if (scene == NULL || !isfinite(elapsed_seconds) || elapsed_seconds < 0.0f) return false;
    scene->time += elapsed_seconds;
    if (scene->unlock_line_active) {
        const float line_time = at_clamp(
                (scene->time - scene->unlock_line_start) / 0.4f,
                0.0f,
                1.0f);
        /* The ARM32 Line track uses the framework cosine ease, independently
         * from the 0.9-second cubic tile-unlock records. */
        scene->unlock_line_progress = 0.5f * (1.0f - cosf(AT_PI * line_time));
        if (line_time >= 1.0f) scene->unlock_line_active = false;
    }
    at_cleanup_completed_records(scene);
    at_update_proximity(scene, elapsed_seconds);

    if (scene->held && scene->time >= scene->next_held_batch) {
        at_pop_batch(scene, scene->anchor_x, scene->anchor_y);
        scene->next_held_batch = scene->time + AT_POP_INTERVAL;
    }
    if (scene->held) {
        at_schedule_rays(scene);
        at_schedule_radial(scene, scene->anchor_x, scene->anchor_y, 0.6f, 0.5f, 0.5f);
    }
    return true;
}

bool at_scene_is_idle(const AtScene *scene) {
    if (scene == NULL) return true;
    if (scene->held || scene->unlock_line_active) return false;
    for (int i = 0; i < scene->triangle_count; ++i) {
        const AtTriangle *triangle = &scene->triangles[i];
        if (at_transform_record_live(triangle, scene->time)
                || triangle->radial_active
                || triangle->ray_active
                || triangle->proximity_alpha > 1.0e-5f) {
            return false;
        }
    }
    return true;
}

static float at_linear_progress(const AtTriangle *triangle, float now) {
    if (!at_transform_record_live(triangle, now) || now < triangle->start) return 0.0f;
    return at_clamp((now - triangle->start) / triangle->duration, 0.0f, 1.0f);
}

static float at_geometry_envelope(const AtTriangle *triangle, float now) {
    const float linear = at_linear_progress(triangle, now);
    const float inverse = 1.0f - linear;
    return 1.0f - inverse * inverse * inverse;
}

static float at_tile_alpha(const AtTriangle *triangle, float now) {
    if (!at_transform_record_live(triangle, now)) return 0.0f;
    if (triangle->transform_kind == AT_TRANSFORM_UNLOCK) return 0.3f;
    /* Normal-pop records write alpha=.3 when scheduled, before their staggered
     * geometry start. The delayed stationary triangles are therefore visible. */
    if (now < triangle->start) return 0.3f;
    const float elapsed = now - triangle->start;
    if (elapsed <= 0.2f) return 0.3f;
    return 0.3f * at_clamp(1.0f - (elapsed - 0.2f) / 0.2f, 0.0f, 1.0f);
}

static float at_radial_envelope(const AtTriangle *triangle, float now) {
    if (!triangle->radial_active) return 0.0f;
    if (now < triangle->radial_start) return 0.001f;
    const float elapsed = now - triangle->radial_start;
    if (elapsed < triangle->radial_rise) {
        return triangle->radial_strength * elapsed / triangle->radial_rise;
    }
    const float fall_elapsed = elapsed - triangle->radial_rise;
    return triangle->radial_strength * at_clamp(
            1.0f - fall_elapsed / (triangle->radial_rise + 0.2f),
            0.0f,
            1.0f);
}

static float at_ray_envelope(const AtTriangle *triangle, float now) {
    if (!triangle->ray_active || now < triangle->ray_start) return 0.0f;
    const float elapsed = now - triangle->ray_start;
    if (elapsed < 0.1f) return triangle->ray_strength * elapsed / 0.1f;
    return triangle->ray_strength * at_clamp(1.0f - (elapsed - 0.1f) / 0.1f, 0.0f, 1.0f);
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
    const size_t required = (size_t) scene->triangle_count * AT_VERTICES_PER_TRIANGLE
            * AT_FLOATS_PER_VERTEX;
    if (vertex_capacity < required) return 0;

    const float sx = (float) scene->width / (float) texture_width;
    const float sy = (float) scene->height / (float) texture_height;
    const float crop_x = sy > sx ? fabsf(sx / sy - 1.0f) * 0.5f : 0.0f;
    const float crop_y = sy <= sx ? fabsf(sy / sx - 1.0f) * 0.5f : 0.0f;
    int emitted = 0;
    for (int slot = 0; slot < scene->triangle_count; ++slot) {
        const int triangle_index = (int) scene->tile_order[slot];
        if (triangle_index < 0 || triangle_index >= scene->triangle_count) return 0;
        const AtTriangle *triangle = &scene->triangles[triangle_index];
        const float geometry = at_geometry_envelope(triangle, scene->time);
        const float brightness = triangle->brightness
                * at_linear_progress(triangle, scene->time);
        const float alpha = at_tile_alpha(triangle, scene->time);
        const float radial = at_radial_envelope(triangle, scene->time);
        const float ray = at_ray_envelope(triangle, scene->time);
        for (int vertex_index = 0; vertex_index < 3; ++vertex_index) {
            float px = triangle->base[vertex_index * 2];
            float py = triangle->base[vertex_index * 2 + 1];
            const int pivot = triangle->pivot % 3;
            const float pivot_x = triangle->base[pivot * 2];
            const float pivot_y = triangle->base[pivot * 2 + 1];
            if (vertex_index != pivot) {
                px += (px - pivot_x) * triangle->strength * geometry;
                py += (py - pivot_y) * triangle->strength * geometry;
            }
            const float base_x = triangle->base[vertex_index * 2];
            const float base_y = triangle->base[vertex_index * 2 + 1];
            const float u = crop_x
                    + (1.0f - 2.0f * crop_x) * (1.0f + base_x) * 0.5f;
            const float v = crop_y
                    + (1.0f - 2.0f * crop_y) * (1.0f - base_y) * 0.5f;
            float *out = &vertices[(size_t) emitted * AT_FLOATS_PER_VERTEX];
            out[0] = px;
            out[1] = py;
            out[2] = u;
            out[3] = v;
            /* Scatter owns a separate, undeformed position array in the ARM32 scene. */
            out[4] = base_x;
            out[5] = base_y;
            out[6] = 0.0f;
            out[7] = alpha;
            out[8] = brightness;
            out[9] = triangle->proximity_alpha;
            out[10] = radial;
            out[11] = ray;
            ++emitted;
        }
    }
    return emitted;
}
