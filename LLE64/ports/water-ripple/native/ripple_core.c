#include "ripple_core.h"

#include <math.h>
#include <stddef.h>

static float laplacian_xy(const float *field, int index, int stride) {
    const float center = field[index];
    float value = field[index - stride] - center * 4.0f;
    value = value + field[index - 1];
    value = value + field[index + 1];
    value = value + field[index + stride];
    return value;
}

void lle_ripple_init_waters(
        float *vertices,
        int16_t *indices,
        int vertex_count,
        int mesh_height,
        int mesh_width,
        int surface_height,
        int surface_width) {
    if (vertices == NULL || indices == NULL || vertex_count <= 0
            || surface_height <= 1 || surface_width <= 1) {
        return;
    }

    // Samsung's ARM32 mesh is intentionally transposed: the array row drives visual X,
    // while the array column drives the negated visual Y. This is paired with the Java
    // ripple(glY, glX, ...) call and must not be normalized to a conventional row/Y mesh.
    //
    // Its NEON bulk loop also keeps the fractional part of vertex / surface_width when
    // calculating X. That produces a small historical shear (about 1% on the 100x100
    // profile). Only the scalar tail truncates to an integer row. All known Samsung
    // profiles have a vertex count divisible by four, but retaining the split reproduces
    // the original behavior for diagnostic non-profile grids too.
    const float row_step_x = (float) mesh_height / (float) (surface_height - 1);
    const float column_step_y = (float) mesh_width / (float) (surface_width - 1);
    const float half_x = (float) mesh_height * 0.5f;
    const float half_y = (float) mesh_width * 0.5f;

    const int bulk_vertex_count = vertex_count & ~3;
    for (int vertex = 0; vertex < bulk_vertex_count; ++vertex) {
        const float row_fraction = (float) vertex / (float) surface_width;
        const int row = (int) row_fraction;
        const int column = vertex - row * surface_width;
        vertices[vertex * 3] = row_fraction * row_step_x - half_x;
        vertices[vertex * 3 + 1] = -((float) column * column_step_y - half_y);
        vertices[vertex * 3 + 2] = 0.0f;
    }
    for (int vertex = bulk_vertex_count; vertex < vertex_count; ++vertex) {
        const int row = vertex / surface_width;
        const int column = vertex % surface_width;
        vertices[vertex * 3] = (float) row * row_step_x - half_x;
        vertices[vertex * 3 + 1] = -((float) column * column_step_y - half_y);
        vertices[vertex * 3 + 2] = 0.0f;
    }

    // The original implementation uses surface_height as its index stride.
    // Samsung profiles are square, so retaining that order is required for
    // parity and does not change the supported 70x70/100x100 meshes.
    int output = 0;
    for (int x = 1; x < surface_height; ++x) {
        for (int y = 1; y < surface_width; ++y) {
            const int bottom_right = x * surface_height + y;
            const int top_left = bottom_right - surface_height - 1;
            const int top_right = bottom_right - surface_height;
            const int bottom_left = bottom_right - 1;
            indices[output++] = (int16_t) top_left;
            indices[output++] = (int16_t) top_right;
            indices[output++] = (int16_t) bottom_right;
            indices[output++] = (int16_t) top_left;
            indices[output++] = (int16_t) bottom_right;
            indices[output++] = (int16_t) bottom_left;
        }
    }
}

void lle_ripple_inject(
        float *velocity,
        int mesh_width,
        int mesh_height,
        int detail_width,
        int detail_height,
        float mesh_x,
        float mesh_y,
        float strength) {
    if (velocity == NULL || mesh_width <= 0 || mesh_height <= 0
            || detail_width <= 0 || detail_height <= 0) {
        return;
    }

    const float cell_x = (mesh_x / (float) mesh_width + 0.5f) * (float) detail_width;
    const float cell_y = (mesh_y / (float) mesh_height + 0.5f) * (float) detail_height;
    const int x_begin = cell_x < 5.0f ? 2 : (int) floorf(cell_x - 3.0f);
    const int y_begin = cell_y < 5.0f ? 2 : (int) floorf(cell_y - 3.0f);
    const int x_end = cell_x < (float) (detail_width - 5)
            ? (int) floorf(cell_x + 4.0f)
            : detail_width - 1;
    const int y_end = cell_y < (float) (detail_height - 5)
            ? (int) floorf(cell_y + 4.0f)
            : detail_height - 1;

    for (int x = x_begin; x < x_end; ++x) {
        const float dx = cell_x - (float) x;
        for (int y = y_begin; y < y_end; ++y) {
            const float dy = cell_y - (float) y;
            const float distance = sqrtf(dx * dx + dy * dy);
            const float impulse = 3.0f - distance;
            if (impulse > 0.0f) {
                velocity[y * detail_width + x] += impulse * strength;
            }
        }
    }
}

bool lle_ripple_move(
        float *velocity,
        float *height,
        int x_begin,
        int y_begin,
        int x_end,
        int y_end,
        int detail_width,
        int detail_height,
        bool check_empty,
        float damping,
        float wave_coefficient) {
    if (velocity == NULL || height == NULL || detail_width <= 2 || detail_height <= 2) {
        return true;
    }

    if (x_begin < 1) x_begin = 1;
    if (y_begin < 1) y_begin = 1;
    if (x_end > detail_width - 1) x_end = detail_width - 1;
    if (y_end > detail_height - 1) y_end = detail_height - 1;

    bool empty = true;
    for (int x = x_begin; x < x_end; ++x) {
        for (int y = y_begin; y < y_end; ++y) {
            const int index = y * detail_width + x;
            const float laplacian = laplacian_xy(height, index, detail_width);
            const float next_velocity =
                    (velocity[index] + laplacian * wave_coefficient) * damping;
            velocity[index] = next_velocity;
            if (check_empty && empty && (next_velocity > 0.01f || next_velocity < -0.01f)) {
                empty = false;
            }
        }
    }

    for (int x = x_begin; x < x_end; ++x) {
        for (int y = y_begin; y < y_end; ++y) {
            const int index = y * detail_width + x;
            float next_height = height[index] + velocity[index];
            if (next_height > 100.0f) {
                next_height = 100.0f;
            } else if (next_height < -100.0f) {
                next_height = -100.0f;
            }
            height[index] = next_height;
        }
    }

    // This is intentionally in-place and X-major/Y-minor. The ARM32 function
    // obtains the same Java height array twice and the third pass consequently
    // observes values already updated earlier in this loop.
    const float extra_coefficient = damping == 0.94f ? 0.068f : 0.018f;
    for (int x = x_begin; x < x_end; ++x) {
        for (int y = y_begin; y < y_end; ++y) {
            const int index = y * detail_width + x;
            const float laplacian = laplacian_xy(height, index, detail_width);
            height[index] = height[index] + laplacian * extra_coefficient;
        }
    }

    return empty;
}
