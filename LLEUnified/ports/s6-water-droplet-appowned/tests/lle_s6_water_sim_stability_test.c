#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#define LLE_S6_TEST_API 1
#include "../native/lle_s6_water_sim.h"

static float particle_speed(const LleS6WaterTestParticleState *particle) {
  return sqrtf(particle->velocity_x * particle->velocity_x +
               particle->velocity_y * particle->velocity_y);
}

static float run_large_tablet_drag(float *terminal_speed) {
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1968.0f, 2184.0f, LLE_S6_WATER_PROJECT_TABLET, 2, 1u);
  float maximum_speed = 0.0f;
  size_t frame;
  if (sim == NULL) {
    fprintf(stderr, "failed to create simulator\n");
    exit(2);
  }

  if (!lle_s6_water_sim_queue_touch(sim, LLE_S6_WATER_TOUCH_DOWN,
                                    440.0f, 1092.0f, 1u)) {
    fprintf(stderr, "failed to queue touch down\n");
    exit(2);
  }
  for (frame = 0u; frame < 24u; ++frame) {
    lle_s6_water_sim_tick(sim);
  }
  if (!lle_s6_water_sim_queue_touch(sim, LLE_S6_WATER_TOUCH_MOVE,
                                    1528.0f, 1092.0f, 401u)) {
    fprintf(stderr, "failed to queue touch move\n");
    exit(2);
  }
  for (frame = 0u; frame < 90u; ++frame) {
    size_t particle_index;
    size_t particle_count;
    float frame_maximum_speed = 0.0f;
    lle_s6_water_sim_tick(sim);
    particle_count = lle_s6_water_sim_particle_count(sim);
    for (particle_index = 0u; particle_index < particle_count;
         ++particle_index) {
      LleS6WaterTestParticleState particle;
      float speed;
      if (!lle_s6_water_sim_test_particle_state(
              sim, particle_index, &particle)) {
        fprintf(stderr, "failed to inspect particle %zu\n", particle_index);
        exit(2);
      }
      speed = particle_speed(&particle);
      if (!isfinite(speed)) {
        fprintf(stderr, "non-finite tablet particle speed\n");
        exit(1);
      }
      if (speed > maximum_speed) {
        maximum_speed = speed;
      }
      if (speed > frame_maximum_speed) {
        frame_maximum_speed = speed;
      }
    }
    if (frame == 89u) {
      *terminal_speed = frame_maximum_speed;
    }
  }
  lle_s6_water_sim_destroy(sim);
  return maximum_speed;
}

int main(void) {
  float terminal_speed = 0.0f;
  const float maximum_speed = run_large_tablet_drag(&terminal_speed);
  printf("large-tablet particle speed: peak=%.3f terminal=%.3f px/s\n",
         maximum_speed, terminal_speed);
  if (maximum_speed >= 10000.0f || terminal_speed >= 1000.0f) {
    fprintf(stderr, "large-tablet drag entered an unstable velocity regime\n");
    return 1;
  }
  return 0;
}
