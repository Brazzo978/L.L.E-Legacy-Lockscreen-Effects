#ifndef LLE_S6_WATER_SIM_H
#define LLE_S6_WATER_SIM_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define LLE_S6_WATER_TICK_HZ 60u
#define LLE_S6_WATER_SOLVER_STEPS_PER_TICK 2u
#define LLE_S6_WATER_PRIMARY_PARTICLE_LIMIT 300u
#define LLE_S6_WATER_AFFORDANCE_PARTICLE_LIMIT 20u
#define LLE_S6_WATER_TOUCH_PARTICLE_LIMIT 20u
#define LLE_S6_WATER_EVENT_QUEUE_CAPACITY 100u

#define LLE_S6_WATER_TOUCH_DOWN 0
#define LLE_S6_WATER_TOUCH_UP 1
#define LLE_S6_WATER_TOUCH_MOVE 2

#define LLE_S6_WATER_PROJECT_PHONE 0
#define LLE_S6_WATER_PROJECT_TABLET 1

#define LLE_S6_WATER_PARTICLE_RELEASED UINT32_C(0x00000001)
#define LLE_S6_WATER_PARTICLE_AFFORDANCE UINT32_C(0x00000002)

typedef struct LleS6WaterSim LleS6WaterSim;

/*
 * Immutable, renderer-facing density descriptor. Coordinates are surface
 * pixels in Android top-left space. diameter_px is the recovered stock
 * density-quad diameter after growth, edge perspective and unlock scaling.
 */
typedef struct LleS6WaterDensityParticle {
  float center_x_px;
  float center_y_px;
  float diameter_px;
  float phase;
  uint32_t flags;
} LleS6WaterDensityParticle;

/*
 * Optical and background-UV state copied at a frame boundary. The GLES owner
 * may keep this value after the simulation advances; it contains no pointers.
 */
typedef struct LleS6WaterRenderState {
  float surface_width;
  float surface_height;
  float logical_width;
  float logical_height;
  float world_width;
  float world_height;
  int project_kind;
  int quality;
  float background_uv_scale;
  float background_uv_offset_x;
  float background_uv_offset_y;
  float background_touch_center_x;
  float background_touch_center_y;
  float breath_phase;
  float breath_accumulator;
  float restore_ratio;
  /*
   * Final shader uEdgeRatio. Stock's setEdgeRatio stores
   * 1-SineInOut90(progress), so this is 1 at reset and falls during unlock.
   * The GLES owner must upload it directly without another inversion.
   */
  float edge_ratio;
  float refraction_ratio;
  float edge_offset_ratio;
  float specular_ratio;
  float bottom_offset;
  float density_threshold;
  float edge_offset;
  float shadow_offset;
  float shadow_range;
  float refraction_eta;
  float refraction_amplitude;
  size_t density_particle_count;
  uint64_t frame_index;
  uint64_t reset_serial;
  bool unlocking;
} LleS6WaterRenderState;

/*
 * project_kind selects recovered phone/tablet constants. quality is retained
 * in deterministic state for the renderer/integration contract; physics does
 * not silently change with GPU quality.
 */
LleS6WaterSim *lle_s6_water_sim_create(float width, float height,
                                       int project_kind, int quality,
                                       uint64_t seed);
void lle_s6_water_sim_destroy(LleS6WaterSim *sim);

void lle_s6_water_sim_set_surface(LleS6WaterSim *sim, float width,
                                  float height, float logical_width,
                                  float logical_height);

/*
 * Touch/key/custom input is serialized through the recovered fixed FIFO and
 * consumed at the next app tick. Tilt matches stock's immediate global state:
 * repeated samples are coalesced to the latest pending value and never consume
 * FIFO slots. Touch coordinates use Android top-left surface space. A
 * timestamp of zero is valid; it is used only to make event ordering/state
 * hashing observable.
 */
bool lle_s6_water_sim_queue_touch(LleS6WaterSim *sim, int action, float x,
                                  float y, uint64_t event_time_ms);
bool lle_s6_water_sim_queue_affordance(LleS6WaterSim *sim, float x, float y);
bool lle_s6_water_sim_queue_unlock(LleS6WaterSim *sim);
bool lle_s6_water_sim_queue_reset_bg_scale(LleS6WaterSim *sim);
bool lle_s6_water_sim_queue_tilt(LleS6WaterSim *sim, float mapped_x,
                                 float mapped_y, uint64_t sample_time_ns);

/*
 * Stock clear (key 90) is deferred by its render loop. This call only records
 * the request. The renderer must call consume_deferred_reset once at its draw
 * boundary, before tick/export. The return value says whether reset ran.
 */
void lle_s6_water_sim_request_reset(LleS6WaterSim *sim);
bool lle_s6_water_sim_consume_deferred_reset(LleS6WaterSim *sim);

/*
 * Advances one recovered 60 Hz app frame. Call exactly once per 1/60 second,
 * not once per unconstrained display vsync. Each call performs two complete
 * 1/60 solver updates; these are stock repeats, not half-sized substeps.
 */
void lle_s6_water_sim_tick(LleS6WaterSim *sim);

bool lle_s6_water_sim_is_idle(const LleS6WaterSim *sim);
size_t lle_s6_water_sim_particle_count(const LleS6WaterSim *sim);

/*
 * Stable main-then-affordance export without allocation. NULL performs a size
 * query. If capacity is insufficient, no output is written and the required
 * count is returned.
 */
size_t lle_s6_water_sim_export_density_particles(
    const LleS6WaterSim *sim, LleS6WaterDensityParticle *out_particles,
    size_t capacity);
void lle_s6_water_sim_get_render_state(const LleS6WaterSim *sim,
                                       LleS6WaterRenderState *out_state);

#ifdef LLE_S6_TEST_API
typedef struct LleS6WaterTestParticleState {
  float x;
  float y;
  float velocity_x;
  float velocity_y;
  float render_offset_x;
  float render_offset_y;
  float staged_acceleration_x;
  float staged_acceleration_y;
  float transient_force_x;
  float transient_force_y;
  float phase;
  float rest_density;
  uint32_t flags;
} LleS6WaterTestParticleState;

/* Host-test-only deterministic hash; never use as a persistence format. */
uint64_t lle_s6_water_sim_test_state_hash(const LleS6WaterSim *sim);
bool lle_s6_water_sim_test_particle_state(
    const LleS6WaterSim *sim, size_t active_index,
    LleS6WaterTestParticleState *out_state);
#endif

#ifdef __cplusplus
}
#endif

#endif
