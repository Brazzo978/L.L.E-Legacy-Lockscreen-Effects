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

/**
 * Normalizes a display-frame duration expressed in recovered 60 Hz ticks.
 *
 * The adaptive Java host discards compositor stalls before entering native code and
 * therefore supplies values in (0, 4].  Keeping the same bound here makes the
 * portable core safe to exercise directly from host regressions too.
 */
float lle_ripple_sanitize_adaptive_ticks(float stock_ticks);

/** Returns a per-stock-tick multiplier raised to an adaptive tick duration. */
float lle_ripple_scale_dissipation(float per_stock_tick, float stock_ticks);

/** Converts elapsed monotonic nanoseconds into exact recovered-60 tick units. */
uint64_t lle_ripple_elapsed_ns_to_tick_units(uint64_t elapsed_ns);

/**
 * Converts recovered tick units to a float phase without rounding an integral
 * logical tick through a large uint64_t float conversion first.
 */
float lle_ripple_tick_units_to_stock_ticks(uint64_t tick_units);

/**
 * Advances a logical-tick credit represented in nanosecond tick units and
 * returns the number of whole recovered ticks crossed.  This is used by Indigo
 * to keep RNG/events at their original 60 Hz logical rate even when its fluid
 * is advanced per display frame, without float cadence drift at 90/144 Hz.
 */
unsigned int lle_ripple_consume_logical_ticks(
        uint64_t *fractional_credit_units,
        uint64_t elapsed_ns);

/** Advances a logical credit directly from already-normalized tick units. */
unsigned int lle_ripple_consume_logical_tick_units(
        uint64_t *fractional_credit_units,
        uint64_t tick_units);

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

/**
 * Advances the recovered wave PDE by a display-frame duration in 60 Hz ticks.
 * A duration of exactly 1.0 delegates to lle_ripple_move(), preserving the
 * fixed-60 oracle bit-for-bit.  Fractional durations use a stable scaled
 * semi-implicit update; durations above one tick are split into stable steps.
 */
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
        float stock_ticks);

#ifdef __cplusplus
}
#endif

#endif
