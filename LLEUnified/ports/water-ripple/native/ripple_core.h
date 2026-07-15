#ifndef LLE64_RIPPLE_CORE_H
#define LLE64_RIPPLE_CORE_H

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void lle_ripple_inject(
        float *velocity,
        int mesh_width,
        int mesh_height,
        int detail_width,
        int detail_height,
        float mesh_x,
        float mesh_y,
        float strength);

void lle_ripple_init_waters(
        float *vertices,
        int16_t *indices,
        int vertex_count,
        int mesh_height,
        int mesh_width,
        int surface_height,
        int surface_width);

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
        float wave_coefficient);

#ifdef __cplusplus
}
#endif

#endif
