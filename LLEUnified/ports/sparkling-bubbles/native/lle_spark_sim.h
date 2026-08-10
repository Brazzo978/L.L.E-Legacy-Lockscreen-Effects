#ifndef LLE_SPARK_SIM_H
#define LLE_SPARK_SIM_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define LLE_SPARK_GROUP_CAPACITY 25u
#define LLE_SPARK_PARTICLES_PER_GROUP 1100u
#define LLE_SPARK_TICK_HZ 60u

typedef struct LleSparkSim LleSparkSim;

typedef struct LleSparkParticleSnapshot {
    float x;
    float y;
    float velocity_x;
    float velocity_y;
    float acceleration_x;
    float acceleration_y;
    float size;
    float alpha;
    float lifetime_ticks;
    float max_lifetime_ticks;
    float boundary_count;
    bool active;
    bool fast_hide;
    bool twinkle;
    uint8_t animation_type;
    uint8_t edge_direction;
} LleSparkParticleSnapshot;

typedef struct LleSparkDrawGroup {
    size_t first_point;
    size_t point_count;
    uint8_t group_slot;
} LleSparkDrawGroup;

/*
 * Creates a fixed-capacity simulation for a surface expressed in pixels.
 *
 * The seed controls the complete particle sequence. A zero seed is accepted
 * and is mapped to a documented non-zero state so that it is deterministic.
 */
LleSparkSim *lle_spark_sim_create(float width, float height, uint64_t seed);
void lle_spark_sim_destroy(LleSparkSim *sim);

/*
 * Reconfigures the surface without modifying active particle coordinates.
 * New bursts use min(width, height) / 1440 as their pixel scale.
 */
void lle_spark_sim_set_surface(LleSparkSim *sim, float width, float height);

/* Changes the RNG sequence used by future bursts and twinkle updates. */
void lle_spark_sim_set_seed(LleSparkSim *sim, uint64_t seed);

/*
 * High-level touch input. Coordinates are already expected in render-space:
 * origin at the lower-left, matching the native simulation.
 */
bool lle_spark_sim_touch_begin(LleSparkSim *sim, float x, float y);
bool lle_spark_sim_touch_move(LleSparkSim *sim, float x, float y);
void lle_spark_sim_touch_end(LleSparkSim *sim);

/*
 * Emits the four offset hint bursts. The return value is the number of groups
 * actually emitted; it may be less than four if the fixed pool is saturated.
 */
size_t lle_spark_sim_hint(LleSparkSim *sim, float center_x, float center_y);

/* Advances exactly one recovered 60 Hz simulation tick. */
void lle_spark_sim_tick(LleSparkSim *sim);

/*
 * Experimental, display-refresh-driven path.  frame_delta is expressed in
 * recovered 60 Hz frame units (therefore 0.5 at 120 Hz and 2.0 at 30 Hz).
 * Unlike lle_spark_sim_tick(), this is intentionally not bit-for-bit stock:
 * it retimes the per-frame operators so elapsed wall-clock time, lifetimes,
 * damping and unlock motion remain stable across a 30--144 Hz display.
 */
void lle_spark_sim_advance_adaptive(LleSparkSim *sim, float frame_delta);

/* Adaptive equivalent of touch_move() with a real-time emission cool-down. */
bool lle_spark_sim_touch_move_adaptive(LleSparkSim *sim, float x, float y);

/* Applies the recovered six-times radial velocity unlock impulse. */
void lle_spark_sim_unlock(LleSparkSim *sim);

/* Clears all particles, touch state, active-order state and unlock state. */
void lle_spark_sim_reset(LleSparkSim *sim);

/* Read-only test and renderer integration hooks. */
size_t lle_spark_sim_active_group_count(const LleSparkSim *sim);
size_t lle_spark_sim_active_particle_count(const LleSparkSim *sim);
uint64_t lle_spark_sim_state_hash(const LleSparkSim *sim);
bool lle_spark_sim_get_particle(
        const LleSparkSim *sim,
        size_t group_slot,
        size_t particle_index,
        LleSparkParticleSnapshot *out_snapshot);

/*
 * Exports packed renderer data in active draw order without allocating.
 *
 * Each active group contributes exactly LLE_SPARK_PARTICLES_PER_GROUP entries,
 * matching the recovered grouped draw behavior. Inactive particles inside an
 * otherwise active group are exported with zero size and alpha.
 *
 * positions_xy and initial_positions_xy each require point_capacity * 2
 * floats. sizes and alphas each require point_capacity floats. groups requires
 * group_capacity entries. The function returns the required point count.
 *
 * Passing NULL output pointers performs a size query. If either capacity is
 * insufficient, no output is written and the required point count is returned.
 */
size_t lle_spark_sim_export_draw_data(
        const LleSparkSim *sim,
        float presentation_fraction,
        float *positions_xy,
        float *initial_positions_xy,
        float *sizes,
        float *alphas,
        size_t point_capacity,
        LleSparkDrawGroup *groups,
        size_t group_capacity);

#ifdef __cplusplus
}
#endif

#endif
