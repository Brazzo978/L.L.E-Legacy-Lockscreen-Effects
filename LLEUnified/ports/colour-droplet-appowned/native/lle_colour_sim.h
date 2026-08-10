#ifndef LLE_COLOUR_SIM_H
#define LLE_COLOUR_SIM_H

#include "lle_colour_gles.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define LLE_COLOUR_TICK_HZ 60u
#define LLE_COLOUR_SUBSTEPS 2u
#define LLE_COLOUR_LIVE_GROUP_PARTICLES 20u
#define LLE_COLOUR_PRIMARY_PARTICLE_LIMIT 300u
#define LLE_COLOUR_GROUP_CAPACITY 24u
#define LLE_COLOUR_PARTICLE_CAPACITY 480u

#define LLE_COLOUR_TOUCH_DOWN 0
#define LLE_COLOUR_TOUCH_UP 1
#define LLE_COLOUR_TOUCH_MOVE 2

typedef struct LleColourSim LleColourSim;

LleColourSim *lle_colour_sim_create(float width, float height, int project_kind,
                                    uint64_t seed);

void lle_colour_sim_destroy(LleColourSim *sim);

void lle_colour_sim_set_surface(LleColourSim *sim, float width, float height,
                                float logical_width, float logical_height);

/*
 * Android/stock touch action mapping: 0 down, 1 up, 2 move. Coordinates use
 * Android top-left screen space. event_time_ms is retained for deterministic
 * event ordering and accepts zero when the caller has no timestamp.
 */
bool lle_colour_sim_touch(LleColourSim *sim, int action, float x, float y,
                          uint64_t event_time_ms);

void lle_colour_sim_sensor(LleColourSim *sim, int sensor_type, float x, float y,
                           float z);

void lle_colour_sim_affordance(LleColourSim *sim, float x, float y);
void lle_colour_sim_unlock(LleColourSim *sim);
void lle_colour_sim_reset_bg_scale(LleColourSim *sim);
void lle_colour_sim_reset(LleColourSim *sim);

/* Advances one recovered 60 Hz frame, internally using two physics substeps. */
void lle_colour_sim_tick(LleColourSim *sim);

/*
 * Experimental display-refresh variant. frame_scale is the duration of this
 * frame relative to the recovered 60 Hz stock frame (60 / displayHz).
 * The stock entry point above intentionally remains separate so its cadence
 * and floating-point operation order stay unchanged.
 */
void lle_colour_sim_tick_scaled(LleColourSim *sim, float frame_scale);

bool lle_colour_sim_is_idle(const LleColourSim *sim);
size_t lle_colour_sim_particle_count(const LleColourSim *sim);

/*
 * Exports draw particles in stable group/particle order without allocating.
 * A NULL output pointer performs a size query. If capacity is insufficient no
 * data is written and the required count is returned.
 */
size_t
lle_colour_sim_export_draw_particles(const LleColourSim *sim,
                                     LleColourDrawParticle *out_particles,
                                     size_t capacity);

void lle_colour_sim_get_draw_params(const LleColourSim *sim,
                                    LleColourDrawParams *out_params);

#ifdef LLE_COLOUR_TEST_API
/* Host-test controls for the recovered library-global satellite phase. */
void lle_colour_sim_test_set_subparticle_phase(float phase);
float lle_colour_sim_test_subparticle_phase(void);
void lle_colour_sim_test_set_stock_subparticle_phase(uint32_t phase);
#endif

#ifdef __cplusplus
}
#endif

#endif
