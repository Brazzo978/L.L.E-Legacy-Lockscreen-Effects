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

static void check_stock_tick_equivalence(void) {
  LleS6WaterSim *stock = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterSim *native_60 = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  size_t frame;
  if (stock == NULL || native_60 == NULL ||
      !lle_s6_water_sim_queue_touch(stock, LLE_S6_WATER_TOUCH_DOWN,
                                    720.0f, 1280.0f, 1u) ||
      !lle_s6_water_sim_queue_touch(native_60, LLE_S6_WATER_TOUCH_DOWN,
                                    720.0f, 1280.0f, 1u)) {
    fprintf(stderr, "failed to initialize 60 Hz equivalence test\n");
    exit(2);
  }
  for (frame = 0u; frame < 32u; ++frame) {
    if (frame == 12u) {
      (void)lle_s6_water_sim_queue_touch(
          stock, LLE_S6_WATER_TOUCH_MOVE, 960.0f, 1280.0f, 201u);
      (void)lle_s6_water_sim_queue_touch(
          native_60, LLE_S6_WATER_TOUCH_MOVE, 960.0f, 1280.0f, 201u);
    }
    lle_s6_water_sim_tick(stock);
    lle_s6_water_sim_tick_native_refresh(native_60, 1.0f);
  }
  if (lle_s6_water_sim_test_state_hash(stock) !=
      lle_s6_water_sim_test_state_hash(native_60)) {
    fprintf(stderr, "native-refresh 60 Hz diverged from stock tick\n");
    exit(1);
  }
  lle_s6_water_sim_destroy(stock);
  lle_s6_water_sim_destroy(native_60);
}

static void check_release_edge_finishes_through_cleanup(float frame_scale) {
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1440.0f, 3088.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterTestParticleState before_cleanup;
  LleS6WaterTestParticleState during_cleanup;
  float elapsed_ticks = 0.0f;
  size_t frame;
  size_t guard = 0u;
  if (sim == NULL ||
      !lle_s6_water_sim_queue_touch(sim, LLE_S6_WATER_TOUCH_DOWN,
                                    420.0f, 2200.0f, 1u)) {
    fprintf(stderr, "failed to initialize edge-release cleanup test\n");
    exit(2);
  }
  for (frame = 0u; frame < (size_t)ceilf(60.0f / frame_scale); ++frame) {
    lle_s6_water_sim_tick_native_refresh(sim, frame_scale);
  }
  if (lle_s6_water_sim_particle_count(sim) < 18u ||
      !lle_s6_water_sim_test_particle_state(sim, 0u, &before_cleanup) ||
      before_cleanup.phase < 0.4f ||
      !lle_s6_water_sim_queue_touch(sim, LLE_S6_WATER_TOUCH_UP,
                                    1020.0f, 900.0f, 1001u)) {
    fprintf(stderr, "failed to create mature edge-release group\n");
    exit(1);
  }

  while (elapsed_ticks <= 52.0f && guard < 1000u) {
    lle_s6_water_sim_tick_native_refresh(sim, frame_scale);
    elapsed_ticks += frame_scale;
    ++guard;
  }
  if (lle_s6_water_sim_particle_count(sim) == 0u ||
      !lle_s6_water_sim_test_particle_state(sim, 0u, &before_cleanup)) {
    fprintf(stderr, "edge release was cut before cleanup at scale %.5f\n",
            frame_scale);
    exit(1);
  }

  for (frame = 0u; frame < (size_t)ceilf(12.0f / frame_scale); ++frame) {
    lle_s6_water_sim_tick_native_refresh(sim, frame_scale);
  }
  if (!lle_s6_water_sim_test_particle_state(sim, 0u, &during_cleanup) ||
      during_cleanup.phase >= before_cleanup.phase ||
      during_cleanup.smoothing_radius >= before_cleanup.smoothing_radius) {
    fprintf(stderr, "edge release did not enter a shrinking cleanup at scale %.5f\n",
            frame_scale);
    exit(1);
  }

  while (lle_s6_water_sim_particle_count(sim) > 0u && guard < 2000u) {
    lle_s6_water_sim_tick_native_refresh(sim, frame_scale);
    elapsed_ticks += frame_scale;
    ++guard;
  }
  if (guard >= 2000u || elapsed_ticks < 70.0f || elapsed_ticks > 95.0f) {
    fprintf(stderr,
            "edge-release cleanup lifetime invalid scale=%.5f ticks=%.3f\n",
            frame_scale, elapsed_ticks);
    exit(1);
  }
  lle_s6_water_sim_destroy(sim);
}

/* Returns wall-clock 60 Hz ticks until the recovered unlock envelope ends. */
static float run_native_refresh_unlock(float frame_scale) {
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterRenderState state;
  float elapsed_ticks = 0.0f;
  size_t guard = 0u;
  if (sim == NULL || !lle_s6_water_sim_queue_unlock(sim)) {
    fprintf(stderr, "failed to start native-refresh unlock\n");
    exit(2);
  }
  do {
    lle_s6_water_sim_tick_native_refresh(sim, frame_scale);
    elapsed_ticks += frame_scale;
    lle_s6_water_sim_get_render_state(sim, &state);
    ++guard;
  } while (state.unlocking && guard < 1000u);
  lle_s6_water_sim_destroy(sim);
  if (guard >= 1000u) {
    fprintf(stderr, "native-refresh unlock did not settle\n");
    exit(1);
  }
  return elapsed_ticks;
}

/* frame_scale is real display cadence; multiplier affects only simulation. */
static float run_native_refresh_unlock_at_speed(float frame_scale,
                                                float multiplier) {
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterRenderState state;
  float real_elapsed_ticks = 0.0f;
  size_t frame = 0u;
  if (sim == NULL || !lle_s6_water_sim_queue_unlock(sim)) {
    fprintf(stderr, "failed to start accelerated native-refresh unlock\n");
    exit(2);
  }
  do {
    lle_s6_water_sim_tick_native_refresh(sim, frame_scale * multiplier);
    real_elapsed_ticks += frame_scale;
    lle_s6_water_sim_get_render_state(sim, &state);
    ++frame;
  } while (state.unlocking && frame < 1000u);
  lle_s6_water_sim_destroy(sim);
  if (frame >= 1000u) {
    fprintf(stderr, "accelerated native-refresh unlock did not settle\n");
    exit(1);
  }
  return real_elapsed_ticks;
}

static float run_native_refresh_breath_phase(float frame_scale,
                                              size_t frame_count,
                                              float multiplier) {
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterRenderState state;
  size_t frame;
  if (sim == NULL) {
    fprintf(stderr, "failed to start native-refresh speed test\n");
    exit(2);
  }
  for (frame = 0u; frame < frame_count; ++frame) {
    lle_s6_water_sim_tick_native_refresh(sim, frame_scale * multiplier);
  }
  lle_s6_water_sim_get_render_state(sim, &state);
  lle_s6_water_sim_destroy(sim);
  return state.breath_phase;
}

static void check_native_refresh_speed_multiplier(void) {
  static const float real_scales[] = {
      2.0f, 1.0f, 2.0f / 3.0f, 0.5f, 60.0f / 144.0f};
  static const size_t frame_counts[] = {30u, 60u, 90u, 120u, 144u};
  static const char *labels[] = {"30", "60", "90", "120", "144"};
  const float fixed_duration = run_native_refresh_unlock(1.0f);
  static const float multipliers[] = {1.2f, 1.5f, 2.0f};
  size_t multiplier_index;
  size_t index;
  for (multiplier_index = 0u;
       multiplier_index < sizeof(multipliers) / sizeof(multipliers[0]);
       ++multiplier_index) {
    const float multiplier = multipliers[multiplier_index];
    const float expected_phase = 60.0f * 0.02f * multiplier;
    const float expected_real_duration = fixed_duration / multiplier;
    for (index = 0u; index < sizeof(real_scales) / sizeof(real_scales[0]);
         ++index) {
      const float phase = run_native_refresh_breath_phase(
          real_scales[index], frame_counts[index], multiplier);
      const float real_duration = run_native_refresh_unlock_at_speed(
          real_scales[index], multiplier);
      printf("native-refresh speed %.1fx at %s Hz: phase=%.5f real unlock="
             "%.3f ticks\n",
             multiplier, labels[index], phase, real_duration);
      if (fabsf(phase - expected_phase) > 0.0001f ||
          fabsf(real_duration - expected_real_duration) > 2.1f) {
        fprintf(stderr, "native-refresh %.1fx speed changed at %s Hz\n",
                multiplier, labels[index]);
        exit(1);
      }
    }
  }
}

static float run_jittered_native_refresh_unlock(void) {
  /* 5.500 ms and 8.388 ms are realistic uneven 144 Hz presentation gaps. */
  static const float scales[] = {
      5.5f / 16.666667f, 8.388f / 16.666667f};
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterRenderState state;
  float elapsed_ticks = 0.0f;
  size_t frame = 0u;
  if (sim == NULL || !lle_s6_water_sim_queue_unlock(sim)) {
    fprintf(stderr, "failed to start jittered native-refresh unlock\n");
    exit(2);
  }
  do {
    const float scale = scales[frame % 2u];
    lle_s6_water_sim_tick_native_refresh(sim, scale);
    elapsed_ticks += scale;
    lle_s6_water_sim_get_render_state(sim, &state);
    ++frame;
  } while (state.unlocking && frame < 1000u);
  lle_s6_water_sim_destroy(sim);
  if (frame >= 1000u) {
    fprintf(stderr, "jittered native-refresh unlock did not settle\n");
    exit(1);
  }
  return elapsed_ticks;
}

static void check_native_refresh_wall_clock(void) {
  const float duration_30 = run_native_refresh_unlock(2.0f);
  const float duration_60 = run_native_refresh_unlock(1.0f);
  const float duration_90 = run_native_refresh_unlock(2.0f / 3.0f);
  const float duration_120 = run_native_refresh_unlock(0.5f);
  const float duration_144 = run_native_refresh_unlock(60.0f / 144.0f);
  const float duration_jittered = run_jittered_native_refresh_unlock();
  const float tolerance_ticks = 2.1f;
  printf("native-refresh unlock ticks: 30=%.3f 60=%.3f 90=%.3f "
         "120=%.3f 144=%.3f jittered=%.3f\n",
         duration_30, duration_60, duration_90, duration_120, duration_144,
         duration_jittered);
  if (fabsf(duration_30 - duration_60) > tolerance_ticks ||
      fabsf(duration_90 - duration_60) > tolerance_ticks ||
      fabsf(duration_120 - duration_60) > tolerance_ticks ||
      fabsf(duration_144 - duration_60) > tolerance_ticks ||
      fabsf(duration_jittered - duration_60) > tolerance_ticks) {
    fprintf(stderr, "native-refresh unlock lifetime is refresh dependent\n");
    exit(1);
  }
}

static void check_native_refresh_unlock_delay_boundary(void) {
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterTestParticleState particle;
  size_t frame;
  if (sim == NULL || !lle_s6_water_sim_queue_unlock(sim) ||
      !lle_s6_water_sim_queue_touch(sim, LLE_S6_WATER_TOUCH_DOWN,
                                    720.0f, 1280.0f, 1u)) {
    fprintf(stderr, "failed to initialize unlock-delay boundary test\n");
    exit(2);
  }
  for (frame = 0u; frame < 19u; ++frame) {
    lle_s6_water_sim_tick_native_refresh(sim, 0.5f);
  }
  if (!lle_s6_water_sim_test_particle_state(sim, 0u, &particle) ||
      fabsf(particle.unlock_delay_ticks - 0.5f) > 0.001f ||
      particle.unlock_progress != 0.0f) {
    fprintf(stderr, "120 Hz unlock started before ten recovered ticks\n");
    exit(1);
  }
  lle_s6_water_sim_tick_native_refresh(sim, 0.5f);
  if (!lle_s6_water_sim_test_particle_state(sim, 0u, &particle) ||
      fabsf(particle.unlock_delay_ticks) > 0.001f ||
      particle.unlock_progress <= 0.0f) {
    fprintf(stderr, "120 Hz unlock did not start at ten recovered ticks\n");
    exit(1);
  }
  lle_s6_water_sim_destroy(sim);
}

static LleS6WaterTestParticleState run_native_refresh_growth(
    float frame_scale, size_t frame_count) {
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterTestParticleState state;
  size_t frame;
  if (sim == NULL ||
      !lle_s6_water_sim_queue_touch(sim, LLE_S6_WATER_TOUCH_DOWN,
                                    720.0f, 1280.0f, 1u)) {
    fprintf(stderr, "failed to start native-refresh growth test\n");
    exit(2);
  }
  /* Seed exactly the same first particle at a recovered 60 Hz boundary;
   * subsequent frames compare only the temporal blend, not fractional-emitter
   * quantization at different display cadences. */
  lle_s6_water_sim_tick_native_refresh(sim, 1.0f);
  for (frame = 0u; frame < frame_count; ++frame) {
    lle_s6_water_sim_tick_native_refresh(sim, frame_scale);
  }
  if (!lle_s6_water_sim_test_particle_state(sim, 0u, &state)) {
    fprintf(stderr, "native-refresh growth emitted no particle "
                    "scale=%.6f frames=%zu count=%zu\n",
            frame_scale, frame_count,
            lle_s6_water_sim_particle_count(sim));
    exit(1);
  }
  lle_s6_water_sim_destroy(sim);
  return state;
}

static void check_native_refresh_growth(void) {
  const LleS6WaterTestParticleState at_60 =
      run_native_refresh_growth(1.0f, 10u);
  const LleS6WaterTestParticleState at_90 =
      run_native_refresh_growth(2.0f / 3.0f, 15u);
  const LleS6WaterTestParticleState at_120 =
      run_native_refresh_growth(0.5f, 20u);
  const LleS6WaterTestParticleState at_144 =
      run_native_refresh_growth(60.0f / 144.0f, 24u);
  const LleS6WaterTestParticleState *states[] = {
      &at_90, &at_120, &at_144};
  const char *labels[] = {"90", "120", "144"};
  size_t index;
  const float relative_tolerance = 0.03f;
  printf("native-refresh growth at 10 ticks: radius=%.4f density=%.4f "
         "pressure=%.4f near=%.4f\n",
         at_60.smoothing_radius, at_60.rest_density, at_60.pressure,
         at_60.near_pressure);
  for (index = 0u; index < sizeof(states) / sizeof(states[0]); ++index) {
    const LleS6WaterTestParticleState *candidate = states[index];
    printf("native-refresh growth at %s Hz: radius=%.4f density=%.4f "
           "pressure=%.4f near=%.4f\n",
           labels[index], candidate->smoothing_radius,
           candidate->rest_density, candidate->pressure,
           candidate->near_pressure);
    if (fabsf(candidate->smoothing_radius - at_60.smoothing_radius) >
            fmaxf(1.0f, fabsf(at_60.smoothing_radius)) * relative_tolerance ||
        fabsf(candidate->rest_density - at_60.rest_density) >
            fmaxf(1.0f, fabsf(at_60.rest_density)) * relative_tolerance ||
        fabsf(candidate->pressure - at_60.pressure) >
            fmaxf(1.0f, fabsf(at_60.pressure)) * relative_tolerance ||
        fabsf(candidate->near_pressure - at_60.near_pressure) >
            fmaxf(1.0f, fabsf(at_60.near_pressure)) * relative_tolerance) {
      fprintf(stderr, "native-refresh growth differs at %s Hz\n", labels[index]);
      exit(1);
    }
  }
}

static float run_live_cadence_unlock(void) {
  static const float scales[] = {
      1.0f, 0.5f, 2.0f, 60.0f / 96.0f};
  static const size_t counts[] = {15u, 30u, 8u, 24u};
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterRenderState state;
  float elapsed_ticks = 0.0f;
  uint64_t reset_serial;
  size_t segment;
  if (sim == NULL || !lle_s6_water_sim_queue_unlock(sim)) {
    fprintf(stderr, "failed to initialize live cadence test\n");
    exit(2);
  }
  lle_s6_water_sim_get_render_state(sim, &state);
  reset_serial = state.reset_serial;
  for (segment = 0u; segment < sizeof(scales) / sizeof(scales[0]); ++segment) {
    size_t frame;
    for (frame = 0u; frame < counts[segment]; ++frame) {
      lle_s6_water_sim_tick_native_refresh(sim, scales[segment]);
      elapsed_ticks += scales[segment];
      lle_s6_water_sim_get_render_state(sim, &state);
      if (state.reset_serial != reset_serial ||
          !isfinite(state.edge_ratio) || !isfinite(state.refraction_ratio)) {
        fprintf(stderr, "live cadence reset or produced invalid render state\n");
        exit(1);
      }
    }
  }
  while (state.unlocking && elapsed_ticks < 200.0f) {
    lle_s6_water_sim_tick_native_refresh(sim, 60.0f / 96.0f);
    elapsed_ticks += 60.0f / 96.0f;
    lle_s6_water_sim_get_render_state(sim, &state);
  }
  lle_s6_water_sim_destroy(sim);
  if (state.unlocking) {
    fprintf(stderr, "live cadence unlock did not settle\n");
    exit(1);
  }
  return elapsed_ticks;
}

static void check_native_refresh_live_cadence(void) {
  const float live_ticks = run_live_cadence_unlock();
  const float fixed_ticks = run_native_refresh_unlock(1.0f);
  printf("native-refresh live cadence unlock ticks: %.3f (fixed 60 %.3f)\n",
         live_ticks, fixed_ticks);
  if (fabsf(live_ticks - fixed_ticks) > 2.1f) {
    fprintf(stderr, "live cadence changed unlock wall-clock progress\n");
    exit(1);
  }
}

static void check_native_refresh_no_backlog(void) {
  LleS6WaterSim *with_stall = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterSim *without_stall = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  uint64_t before;
  if (with_stall == NULL || without_stall == NULL ||
      !lle_s6_water_sim_queue_unlock(with_stall) ||
      !lle_s6_water_sim_queue_unlock(without_stall)) {
    fprintf(stderr, "failed to initialize no-backlog test\n");
    exit(2);
  }
  lle_s6_water_sim_tick_native_refresh(with_stall, 0.5f);
  lle_s6_water_sim_tick_native_refresh(without_stall, 0.5f);
  before = lle_s6_water_sim_test_state_hash(with_stall);
  lle_s6_water_sim_tick_native_refresh(with_stall, 0.0f);
  if (lle_s6_water_sim_test_state_hash(with_stall) != before) {
    fprintf(stderr, "zero-duration native frame changed simulation state\n");
    exit(1);
  }
  lle_s6_water_sim_tick_native_refresh(with_stall, 0.5f);
  lle_s6_water_sim_tick_native_refresh(without_stall, 0.5f);
  if (lle_s6_water_sim_test_state_hash(with_stall) !=
      lle_s6_water_sim_test_state_hash(without_stall)) {
    fprintf(stderr, "stalled native frame replayed a backlog\n");
    exit(1);
  }
  lle_s6_water_sim_destroy(with_stall);
  lle_s6_water_sim_destroy(without_stall);
}

static void check_jittered_native_refresh_time_accounting(void) {
  static const float short_scale = 5.5f / 16.666667f;
  static const float long_scale = 8.388f / 16.666667f;
  const size_t pairs = 25u;
  const float total_ticks = (short_scale + long_scale) * (float)pairs;
  const float regular_scale = total_ticks / (float)pairs;
  LleS6WaterSim *jittered = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterSim *regular = lle_s6_water_sim_create(
      1440.0f, 2560.0f, LLE_S6_WATER_PROJECT_PHONE, 2, 1u);
  LleS6WaterRenderState jittered_state;
  LleS6WaterRenderState regular_state;
  size_t index;
  if (jittered == NULL || regular == NULL) {
    fprintf(stderr, "failed to initialize jittered time-accounting test\n");
    exit(2);
  }
  for (index = 0u; index < pairs; ++index) {
    lle_s6_water_sim_tick_native_refresh(jittered, short_scale);
    lle_s6_water_sim_tick_native_refresh(jittered, long_scale);
    lle_s6_water_sim_tick_native_refresh(regular, regular_scale);
  }
  lle_s6_water_sim_get_render_state(jittered, &jittered_state);
  lle_s6_water_sim_get_render_state(regular, &regular_state);
  printf("native-refresh jittered clock: %.5f ticks, phase %.6f vs %.6f\n",
         total_ticks, jittered_state.breath_phase,
         regular_state.breath_phase);
  if (fabsf(jittered_state.breath_phase - regular_state.breath_phase) >
          0.00001f ||
      fabsf(jittered_state.background_touch_center_x -
            regular_state.background_touch_center_x) > 0.00001f ||
      fabsf(jittered_state.background_touch_center_y -
            regular_state.background_touch_center_y) > 0.00001f) {
    fprintf(stderr, "jittered refresh accumulated artificial wall time\n");
    exit(1);
  }
  lle_s6_water_sim_destroy(jittered);
  lle_s6_water_sim_destroy(regular);
}

static void check_native_refresh_stability(float frame_scale) {
  LleS6WaterSim *sim = lle_s6_water_sim_create(
      1968.0f, 2184.0f, LLE_S6_WATER_PROJECT_TABLET, 2, 1u);
  size_t frame;
  if (sim == NULL ||
      !lle_s6_water_sim_queue_touch(sim, LLE_S6_WATER_TOUCH_DOWN,
                                    440.0f, 1092.0f, 1u)) {
    fprintf(stderr, "failed to start native-refresh tablet drag\n");
    exit(2);
  }
  for (frame = 0u; frame < 240u; ++frame) {
    size_t particle_index;
    size_t particle_count;
    if (frame == 24u &&
        !lle_s6_water_sim_queue_touch(sim, LLE_S6_WATER_TOUCH_MOVE,
                                      1528.0f, 1092.0f, 401u)) {
      fprintf(stderr, "failed to move native-refresh tablet drag\n");
      exit(2);
    }
    lle_s6_water_sim_tick_native_refresh(sim, frame_scale);
    particle_count = lle_s6_water_sim_particle_count(sim);
    for (particle_index = 0u; particle_index < particle_count;
         ++particle_index) {
      LleS6WaterTestParticleState particle;
      if (!lle_s6_water_sim_test_particle_state(
              sim, particle_index, &particle) ||
          !isfinite(particle.x) || !isfinite(particle.y) ||
          !isfinite(particle.velocity_x) || !isfinite(particle.velocity_y)) {
        fprintf(stderr, "native-refresh tablet state became non-finite\n");
        exit(1);
      }
    }
  }
  lle_s6_water_sim_destroy(sim);
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
  /* Fixed-tick regression checkpoint captured from the recovered 60 Hz
   * tablet path. This is intentionally tighter than the instability guard:
   * the experimental display path must not retune production physics. */
  if (fabsf(maximum_speed - 7472.908f) > 0.5f ||
      fabsf(terminal_speed - 294.988f) > 0.5f) {
    fprintf(stderr, "fixed 60 Hz tablet physics changed unexpectedly\n");
    return 1;
  }
  check_stock_tick_equivalence();
  check_release_edge_finishes_through_cleanup(1.0f);
  check_release_edge_finishes_through_cleanup(0.5f);
  check_release_edge_finishes_through_cleanup(60.0f / 144.0f);
  check_native_refresh_wall_clock();
  check_native_refresh_speed_multiplier();
  check_native_refresh_unlock_delay_boundary();
  check_native_refresh_growth();
  check_native_refresh_live_cadence();
  check_native_refresh_no_backlog();
  check_jittered_native_refresh_time_accounting();
  check_native_refresh_stability(2.0f);
  check_native_refresh_stability(4.0f);
  check_native_refresh_stability(2.0f / 3.0f);
  check_native_refresh_stability(0.5f);
  check_native_refresh_stability(60.0f / 144.0f);
  return 0;
}
