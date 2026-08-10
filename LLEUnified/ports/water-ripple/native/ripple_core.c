#include "ripple_core.h"

#include <math.h>
#include <stddef.h>

#define LLE_RIPPLE_MAX_ADAPTIVE_TICKS 4.0f
#define LLE_RIPPLE_REFERENCE_HZ UINT64_C(60)
#define LLE_RIPPLE_TICK_UNITS UINT64_C(1000000000)

static float laplacian_xy(const float *field, int index, int stride) {
    const float center = field[index];
    float value = field[index - stride] - center * 4.0f;
    value = value + field[index - 1];
    value = value + field[index + 1];
    value = value + field[index + stride];
    return value;
}

static bool lle_ripple_velocity_region_empty(
        const float *velocity,
        int x_begin,
        int y_begin,
        int x_end,
        int y_end,
        int detail_width,
        int detail_height) {
    if (velocity == NULL || detail_width <= 2 || detail_height <= 2) {
        return true;
    }
    if (x_begin < 1) x_begin = 1;
    if (y_begin < 1) y_begin = 1;
    if (x_end > detail_width - 1) x_end = detail_width - 1;
    if (y_end > detail_height - 1) y_end = detail_height - 1;
    for (int x = x_begin; x < x_end; ++x) {
        for (int y = y_begin; y < y_end; ++y) {
            const float value = velocity[y * detail_width + x];
            if (value > 0.01f || value < -0.01f) {
                return false;
            }
        }
    }
    return true;
}

float lle_ripple_sanitize_adaptive_ticks(float stock_ticks) {
    if (!isfinite(stock_ticks) || stock_ticks <= 0.0f) {
        return 0.0f;
    }
    return fminf(stock_ticks, LLE_RIPPLE_MAX_ADAPTIVE_TICKS);
}

float lle_ripple_scale_dissipation(float per_stock_tick, float stock_ticks) {
    const float ticks = lle_ripple_sanitize_adaptive_ticks(stock_ticks);
    if (ticks == 0.0f || per_stock_tick == 1.0f) {
        return 1.0f;
    }
    /* Keep the exact recovered value in the scale=1 oracle path. */
    if (ticks == 1.0f) {
        return per_stock_tick;
    }
    if (per_stock_tick <= 0.0f || !isfinite(per_stock_tick)) {
        return per_stock_tick;
    }
    return powf(per_stock_tick, ticks);
}

uint64_t lle_ripple_elapsed_ns_to_tick_units(uint64_t elapsed_ns) {
    return elapsed_ns * LLE_RIPPLE_REFERENCE_HZ;
}

float lle_ripple_tick_units_to_stock_ticks(uint64_t tick_units) {
    const uint64_t whole_ticks = tick_units / LLE_RIPPLE_TICK_UNITS;
    const uint64_t fractional_units = tick_units % LLE_RIPPLE_TICK_UNITS;
    /* Casting the full unit count to float first makes e.g. 9 ticks become
     * 8.999999 due to the magnitude of 9e9.  Keep integral q=1 phases exactly
     * aligned with the legacy integer counters. */
    return (float) whole_ticks
            + (float) fractional_units / (float) LLE_RIPPLE_TICK_UNITS;
}

unsigned int lle_ripple_consume_logical_ticks(
        uint64_t *fractional_credit_units,
        uint64_t elapsed_ns) {
    return lle_ripple_consume_logical_tick_units(
            fractional_credit_units,
            lle_ripple_elapsed_ns_to_tick_units(elapsed_ns));
}

unsigned int lle_ripple_consume_logical_tick_units(
        uint64_t *fractional_credit_units,
        uint64_t tick_units) {
    if (fractional_credit_units == NULL) {
        return 0U;
    }
    if (tick_units == 0U) {
        return 0U;
    }
    const uint64_t total_units = *fractional_credit_units
            + tick_units;
    const unsigned int whole = (unsigned int) (total_units / LLE_RIPPLE_TICK_UNITS);
    *fractional_credit_units = total_units % LLE_RIPPLE_TICK_UNITS;
    return whole;
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

/*
 * The recovered stock tick is a semi-implicit wave update:
 *
 *   v' = damping * (v + waveCoefficient * Laplacian(h))
 *   h' = h + v'
 *
 * where v is measured in height per recovered 60 Hz tick.  For a fractional
 * display interval s=dt/(1/60), preserve those units by scaling the force and
 * height integration by s, and turn the per-tick damping/diffusion into their
 * time-scaled forms.  The final in-place smoothing pass intentionally retains
 * the recovered X-major traversal; only its diffusion amount is time-scaled.
 */
static bool lle_ripple_move_fractional(
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
        float wave_coefficient,
        float stock_ticks) {
    if (velocity == NULL || height == NULL || detail_width <= 2 || detail_height <= 2) {
        return true;
    }

    if (x_begin < 1) x_begin = 1;
    if (y_begin < 1) y_begin = 1;
    if (x_end > detail_width - 1) x_end = detail_width - 1;
    if (y_end > detail_height - 1) y_end = detail_height - 1;

    const float scaled_damping = lle_ripple_scale_dissipation(damping, stock_ticks);
    const float scaled_wave = wave_coefficient * stock_ticks;
    bool empty = true;
    for (int x = x_begin; x < x_end; ++x) {
        for (int y = y_begin; y < y_end; ++y) {
            const int index = y * detail_width + x;
            const float laplacian = laplacian_xy(height, index, detail_width);
            const float next_velocity =
                    (velocity[index] + laplacian * scaled_wave) * scaled_damping;
            velocity[index] = next_velocity;
            if (check_empty && empty
                    && (next_velocity > 0.01f || next_velocity < -0.01f)) {
                empty = false;
            }
        }
    }

    for (int x = x_begin; x < x_end; ++x) {
        for (int y = y_begin; y < y_end; ++y) {
            const int index = y * detail_width + x;
            float next_height = height[index] + velocity[index] * stock_ticks;
            if (next_height > 100.0f) {
                next_height = 100.0f;
            } else if (next_height < -100.0f) {
                next_height = -100.0f;
            }
            height[index] = next_height;
        }
    }

    const float extra_coefficient = damping == 0.94f ? 0.068f : 0.018f;
    const float scaled_extra = extra_coefficient * stock_ticks;
    for (int x = x_begin; x < x_end; ++x) {
        for (int y = y_begin; y < y_end; ++y) {
            const int index = y * detail_width + x;
            const float laplacian = laplacian_xy(height, index, detail_width);
            height[index] = height[index] + laplacian * scaled_extra;
        }
    }

    return empty;
}

bool lle_ripple_move_adaptive(
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
        float wave_coefficient,
        float stock_ticks) {
    const float bounded_ticks = lle_ripple_sanitize_adaptive_ticks(stock_ticks);
    if (bounded_ticks == 0.0f) {
        /* A duplicate/backward timestamp is a strict no-op and must not make
         * a live field appear idle merely because no integration occurred. */
        return !check_empty || lle_ripple_velocity_region_empty(
                velocity,
                x_begin,
                y_begin,
                x_end,
                y_end,
                detail_width,
                detail_height);
    }

    /* Bitwise recovered 60 Hz equivalence is a hard compatibility boundary. */
    if (bounded_ticks == 1.0f) {
        return lle_ripple_move(
                velocity,
                height,
                x_begin,
                y_begin,
                x_end,
                y_end,
                detail_width,
                detail_height,
                check_empty,
                damping,
                wave_coefficient);
    }

    /* Never take an explicit PDE step larger than the recovered stable tick.
     * This is for rare <=66.667 ms jitter only; native-refresh panels spend
     * their normal 90/120/144 Hz frames in the fractional branch above. */
    unsigned int whole_steps = (unsigned int) floorf(bounded_ticks);
    float fractional_step = bounded_ticks - (float) whole_steps;
    bool empty = true;
    for (unsigned int step = 0U; step < whole_steps; ++step) {
        empty = lle_ripple_move(
                velocity,
                height,
                x_begin,
                y_begin,
                x_end,
                y_end,
                detail_width,
                detail_height,
                check_empty,
                damping,
                wave_coefficient);
    }
    if (fractional_step > 0.0f) {
        empty = lle_ripple_move_fractional(
                velocity,
                height,
                x_begin,
                y_begin,
                x_end,
                y_end,
                detail_width,
                detail_height,
                check_empty,
                damping,
                wave_coefficient,
                fractional_step);
    }
    return empty;
}
