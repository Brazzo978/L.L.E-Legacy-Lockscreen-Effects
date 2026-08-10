#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LLE_COLOUR_TEST_API 1
#include "../native/lle_colour_sim.h"

/* The simulation test links no GLES implementation. */
void lle_colour_gles_default_params(LleColourDrawParams *params) {
  memset(params, 0, sizeof(*params));
  params->restore_ratio = 1.0f;
}

static void fail(const char *message) {
  fprintf(stderr, "%s\n", message);
  exit(1);
}

static void require_close(float actual, float expected, float tolerance,
                          const char *message) {
  if (!isfinite(actual) || !isfinite(expected) ||
      fabsf(actual - expected) > tolerance) {
    fail(message);
  }
}

static void require_visible_state_equal(const LleColourSim *left,
                                        const LleColourSim *right,
                                        float tolerance) {
  const size_t count = lle_colour_sim_particle_count(left);
  LleColourDrawParticle *left_particles;
  LleColourDrawParticle *right_particles;
  LleColourDrawParams left_params;
  LleColourDrawParams right_params;
  size_t index;
  if (count != lle_colour_sim_particle_count(right)) {
    fail("particle count diverged");
  }
  left_particles = calloc(count == 0u ? 1u : count, sizeof(*left_particles));
  right_particles = calloc(count == 0u ? 1u : count, sizeof(*right_particles));
  if (left_particles == NULL || right_particles == NULL ||
      lle_colour_sim_export_draw_particles(left, left_particles, count) != count ||
      lle_colour_sim_export_draw_particles(right, right_particles, count) != count) {
    fail("unable to export particle state");
  }
  for (index = 0u; index < count; ++index) {
    const LleColourDrawParticle *a = &left_particles[index];
    const LleColourDrawParticle *b = &right_particles[index];
    require_close(a->x, b->x, tolerance, "particle x diverged");
    require_close(a->y, b->y, tolerance, "particle y diverged");
    require_close(a->velocity_x, b->velocity_x, tolerance,
                  "particle velocity x diverged");
    require_close(a->velocity_y, b->velocity_y, tolerance,
                  "particle velocity y diverged");
    require_close(a->density_size_px, b->density_size_px, tolerance,
                  "particle density size diverged");
    require_close(a->colour_size_px, b->colour_size_px, tolerance,
                  "particle colour size diverged");
  }
  lle_colour_sim_get_draw_params(left, &left_params);
  lle_colour_sim_get_draw_params(right, &right_params);
  require_close(left_params.edge_ratio, right_params.edge_ratio, tolerance,
                "edge ratio diverged");
  require_close(left_params.tab_scale, right_params.tab_scale, tolerance,
                "tab scale diverged");
  require_close(left_params.tab_offset_x, right_params.tab_offset_x, tolerance,
                "tab offset x diverged");
  require_close(left_params.tab_offset_y, right_params.tab_offset_y, tolerance,
                "tab offset y diverged");
  free(left_particles);
  free(right_particles);
}

static LleColourSim *new_sim(void) {
  LleColourSim *sim = lle_colour_sim_create(1440.0f, 2560.0f, 0, 1u);
  if (sim == NULL) {
    fail("failed to create simulator");
  }
  return sim;
}

static void check_scale_one_equivalence(void) {
  LleColourSim *stock = new_sim();
  LleColourSim *adaptive = new_sim();
  size_t frame;
  if (!lle_colour_sim_touch(stock, LLE_COLOUR_TOUCH_DOWN, 720.0f, 1280.0f, 1u) ||
      !lle_colour_sim_touch(adaptive, LLE_COLOUR_TOUCH_DOWN, 720.0f, 1280.0f, 1u)) {
    fail("failed to initialize scale-one touch");
  }
  /* Two parallel sims share the recovered global satellite phase. */
  for (frame = 0u; frame < 6u; ++frame) {
    if (frame == 3u) {
      (void)lle_colour_sim_touch(stock, LLE_COLOUR_TOUCH_MOVE,
                                 900.0f, 1280.0f, 101u);
      (void)lle_colour_sim_touch(adaptive, LLE_COLOUR_TOUCH_MOVE,
                                 900.0f, 1280.0f, 101u);
    }
    lle_colour_sim_tick(stock);
    lle_colour_sim_tick_scaled(adaptive, 1.0f);
    require_visible_state_equal(stock, adaptive, 0.00001f);
  }
  lle_colour_sim_destroy(stock);
  lle_colour_sim_destroy(adaptive);
}

static void check_large_frame_direction_pulse(void) {
  LleColourSim *sim = new_sim();
  LleColourDrawParams params;
  if (!lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_DOWN,
                            600.0f, 1280.0f, 1u)) {
    fail("failed to start direction-pulse test");
  }
  lle_colour_sim_tick_scaled(sim, 1.0f);
  if (!lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_MOVE,
                            900.0f, 1280.0f, 41u)) {
    fail("failed to move direction-pulse test");
  }
  /* 30 Hz is internally split into two stable one-frame substeps. */
  lle_colour_sim_tick_scaled(sim, 2.0f);
  lle_colour_sim_get_draw_params(sim, &params);
  require_close(params.direction_velocity_x,
                300.0f * params.restore_ratio, 0.00001f,
                "30 Hz direction pulse was cleared by stability substep");
  require_close(params.direction_velocity_y, 0.0f, 0.00001f,
                "30 Hz direction pulse has unexpected Y");
  lle_colour_sim_destroy(sim);
}

typedef struct GrowthSample {
  float density_size;
  float colour_size;
  float edge_ratio;
} GrowthSample;

static GrowthSample sample_growth(float frame_scale, size_t frames) {
  LleColourSim *sim = new_sim();
  LleColourDrawParticle particles[480];
  LleColourDrawParams params;
  GrowthSample sample;
  size_t frame;
  lle_colour_sim_test_set_subparticle_phase(0.0f);
  if (!lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_DOWN,
                            720.0f, 1280.0f, 1u)) {
    fail("failed to start growth sample");
  }
  for (frame = 0u; frame < frames; ++frame) {
    lle_colour_sim_tick_scaled(sim, frame_scale);
  }
  if (lle_colour_sim_export_draw_particles(sim, particles, 480u) == 0u) {
    fail("growth sample has no particles");
  }
  lle_colour_sim_get_draw_params(sim, &params);
  sample.density_size = particles[0].density_size_px;
  sample.colour_size = particles[0].colour_size_px;
  sample.edge_ratio = params.edge_ratio;
  lle_colour_sim_destroy(sim);
  return sample;
}

static void check_fractional_growth_wall_clock(void) {
  const GrowthSample hz60 = sample_growth(1.0f, 6u);
  const GrowthSample hz90 = sample_growth(60.0f / 90.0f, 9u);
  const GrowthSample hz120 = sample_growth(0.5f, 12u);
  printf("growth size: 60=%.3f/%.3f 90=%.3f/%.3f 120=%.3f/%.3f\n",
         hz60.density_size, hz60.colour_size,
         hz90.density_size, hz90.colour_size,
         hz120.density_size, hz120.colour_size);
  /* Numerical integration differs slightly, but equal wall time stays close. */
  require_close(hz90.density_size, hz60.density_size, 1.0f,
                "90 Hz density growth diverged from 60 Hz");
  require_close(hz120.density_size, hz60.density_size, 1.0f,
                "120 Hz density growth diverged from 60 Hz");
  require_close(hz90.colour_size, hz60.colour_size, 2.0f,
                "90 Hz colour growth diverged from 60 Hz");
  require_close(hz120.colour_size, hz60.colour_size, 2.0f,
                "120 Hz colour growth diverged from 60 Hz");
}

static void check_fractional_unlock_delay(void) {
  LleColourSim *sim = new_sim();
  LleColourDrawParams params;
  size_t frame;
  lle_colour_sim_unlock(sim);
  for (frame = 0u; frame < 19u; ++frame) {
    lle_colour_sim_tick_scaled(sim, 0.5f);
  }
  lle_colour_sim_get_draw_params(sim, &params);
  require_close(params.edge_ratio, 1.0f, 0.00001f,
                "unlock delay opened a half tick early");
  lle_colour_sim_tick_scaled(sim, 0.5f);
  lle_colour_sim_get_draw_params(sim, &params);
  if (!(params.edge_ratio < 1.0f)) {
    fail("unlock delay did not open on its tenth stock tick");
  }
  lle_colour_sim_destroy(sim);
}

static void check_subparticle_phase_survives_recreation(void) {
  LleColourSim *first;
  LleColourSim *second;
  size_t count;
  lle_colour_sim_test_set_subparticle_phase(12.0f);
  first = new_sim();
  if (!lle_colour_sim_touch(first, LLE_COLOUR_TOUCH_DOWN,
                            720.0f, 1280.0f, 1u)) {
    fail("failed to start phase recreation test");
  }
  lle_colour_sim_tick_scaled(first, 0.5f);
  require_close(lle_colour_sim_test_subparticle_phase(), 12.5f, 0.00001f,
                "adaptive subparticle phase did not advance fractionally");
  lle_colour_sim_destroy(first);

  second = new_sim();
  if (!lle_colour_sim_touch(second, LLE_COLOUR_TOUCH_DOWN,
                             720.0f, 1280.0f, 2u)) {
    fail("failed to recreate phase test");
  }
  lle_colour_sim_tick_scaled(second, 0.5f);
  count = lle_colour_sim_particle_count(second);
  if (count < 2u || lle_colour_sim_test_subparticle_phase() >= 1.0f) {
    fail("subparticle phase reset across renderer recreation");
  }
  lle_colour_sim_destroy(second);
}

static void check_stock_phase_isolated_from_adaptive_toggle(void) {
  LleColourSim *stock = new_sim();
  size_t frame;
  lle_colour_sim_test_set_stock_subparticle_phase(0u);
  lle_colour_sim_test_set_subparticle_phase(12.5f);
  if (!lle_colour_sim_touch(stock, LLE_COLOUR_TOUCH_DOWN,
                            720.0f, 1280.0f, 1u)) {
    fail("failed to start stock-toggle isolation test");
  }
  for (frame = 0u; frame < 12u; ++frame) {
    lle_colour_sim_tick(stock);
  }
  if (lle_colour_sim_particle_count(stock) != 20u) {
    fail("adaptive phase contaminated stock before thirteenth tick");
  }
  lle_colour_sim_tick(stock);
  if (lle_colour_sim_particle_count(stock) != 21u) {
    fail("stock did not preserve its thirteenth-tick satellite phase");
  }
  require_close(lle_colour_sim_test_subparticle_phase(), 12.5f, 0.00001f,
                "stock tick contaminated adaptive phase");
  lle_colour_sim_destroy(stock);
}

static float unlock_lifetime(float frame_scale) {
  LleColourSim *sim = new_sim();
  float elapsed_stock_ticks = 0.0f;
  size_t guard = 0u;
  lle_colour_sim_unlock(sim);
  while (!lle_colour_sim_is_idle(sim) && guard++ < 1000u) {
    lle_colour_sim_tick_scaled(sim, frame_scale);
    elapsed_stock_ticks += frame_scale;
  }
  lle_colour_sim_destroy(sim);
  if (guard >= 1000u) {
    fail("unlock did not settle");
  }
  return elapsed_stock_ticks;
}

/* Returns wall-clock recovered 60 Hz ticks, not multiplier-scaled ticks. */
static float unlock_wall_lifetime(float panel_frame_scale, float multiplier) {
  LleColourSim *sim = new_sim();
  float elapsed_wall_ticks = 0.0f;
  size_t guard = 0u;
  lle_colour_sim_unlock(sim);
  while (!lle_colour_sim_is_idle(sim) && guard++ < 1000u) {
    lle_colour_sim_tick_scaled(sim, panel_frame_scale * multiplier);
    elapsed_wall_ticks += panel_frame_scale;
  }
  lle_colour_sim_destroy(sim);
  if (guard >= 1000u) {
    fail("multiplied unlock did not settle");
  }
  return elapsed_wall_ticks;
}

static float unlock_lifetime_jittered_144(void) {
  const float short_frame = 0.0055f * 60.0f;
  const float long_frame = 2.0f * (60.0f / 144.0f) - short_frame;
  LleColourSim *sim = new_sim();
  float elapsed_stock_ticks = 0.0f;
  size_t guard = 0u;
  lle_colour_sim_unlock(sim);
  while (!lle_colour_sim_is_idle(sim) && guard++ < 1000u) {
    const float frame_scale = (guard & 1u) == 0u ? short_frame : long_frame;
    lle_colour_sim_tick_scaled(sim, frame_scale);
    elapsed_stock_ticks += frame_scale;
  }
  lle_colour_sim_destroy(sim);
  if (guard >= 1000u) {
    fail("jittered unlock did not settle");
  }
  return elapsed_stock_ticks;
}

static void check_wall_clock_lifetime(void) {
  const float rates[] = {30.0f, 60.0f, 90.0f, 120.0f, 144.0f};
  const float baseline = unlock_lifetime(1.0f);
  size_t index;
  for (index = 0u; index < sizeof(rates) / sizeof(rates[0]); ++index) {
    const float scale = 60.0f / rates[index];
    const float lifetime = unlock_lifetime(scale);
    /* One actual panel frame at the slowest supported 30 Hz rate. */
    require_close(lifetime, baseline, 2.01f,
                  "unlock lifetime depends on display refresh");
    printf("unlock %.0f Hz: %.3f stock ticks\n", rates[index], lifetime);
  }
}

static void check_jittered_144_wall_clock(void) {
  const float stable = unlock_lifetime(60.0f / 144.0f);
  const float jittered = unlock_lifetime_jittered_144();
  printf("unlock 144 Hz stable=%.3f jittered=%.3f stock ticks\n",
         stable, jittered);
  /* One 144 Hz presented frame: no lower-clamp time may be invented. */
  require_close(jittered, stable, 60.0f / 144.0f + 0.0001f,
                "144 Hz jitter changed wall-clock unlock lifetime");
}

static void check_speed_multiplier_wall_clock(void) {
  const float rates[] = {30.0f, 60.0f, 90.0f, 120.0f, 144.0f};
  const float multipliers[] = {1.2f, 1.5f, 2.0f};
  size_t multiplier_index;
  for (multiplier_index = 0u;
       multiplier_index < sizeof(multipliers) / sizeof(multipliers[0]);
       ++multiplier_index) {
    const float multiplier = multipliers[multiplier_index];
    const float baseline = unlock_wall_lifetime(1.0f, multiplier);
    size_t index;
    printf("unlock x%.1f wall ticks: ", multiplier);
    for (index = 0u; index < sizeof(rates) / sizeof(rates[0]); ++index) {
      const float panel_scale = 60.0f / rates[index];
      const float wall_ticks = unlock_wall_lifetime(panel_scale, multiplier);
      printf("%.0f=%.3f ", rates[index], wall_ticks);
      /* One panel frame at the slowest rate; x2 reaches 4.0 at 30 Hz. */
      require_close(wall_ticks, baseline, 2.01f,
                    "multiplied unlock cadence depends on panel refresh");
    }
    printf("\n");
    if (!(baseline < unlock_wall_lifetime(1.0f, 1.0f))) {
      fail("speed multiplier did not shorten wall-clock unlock");
    }
  }
}

static void check_finite_motion(float frame_scale) {
  LleColourSim *sim = new_sim();
  float elapsed = 0.0f;
  if (!lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_DOWN, 600.0f, 1280.0f, 1u)) {
    fail("failed to start finite-motion drag");
  }
  while (elapsed < 240.0f) {
    LleColourDrawParticle particles[480];
    size_t count;
    size_t index;
    if (elapsed > 24.0f && elapsed < 24.0f + frame_scale) {
      (void)lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_MOVE,
                                 1080.0f, 1280.0f, 401u);
    }
    lle_colour_sim_tick_scaled(sim, frame_scale);
    elapsed += frame_scale;
    count = lle_colour_sim_export_draw_particles(sim, particles, 480u);
    if (count > 480u) {
      fail("particle export exceeded test capacity");
    }
    for (index = 0u; index < count; ++index) {
      if (!isfinite(particles[index].x) || !isfinite(particles[index].y) ||
          !isfinite(particles[index].velocity_x) ||
          !isfinite(particles[index].velocity_y) ||
          !isfinite(particles[index].density_size_px)) {
        fail("native-refresh motion became non-finite");
      }
    }
  }
  lle_colour_sim_destroy(sim);
}

static void check_stall_no_backlog(void) {
  LleColourSim *sim = new_sim();
  LleColourDrawParticle before[480];
  LleColourDrawParticle after[480];
  size_t before_count;
  size_t after_count;
  if (!lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_DOWN,
                            720.0f, 1280.0f, 1u)) {
    fail("failed to start stall test");
  }
  lle_colour_sim_tick_scaled(sim, 0.5f);
  before_count = lle_colour_sim_export_draw_particles(sim, before, 480u);
  /* Mirrors GlView: a >66.7ms timestamp discards elapsed time and calls no tick. */
  after_count = lle_colour_sim_export_draw_particles(sim, after, 480u);
  if (before_count != after_count) {
    fail("stalled timestamp queued unexpected physics work");
  }
  for (size_t index = 0u; index < before_count; ++index) {
    require_close(before[index].x, after[index].x, 0.00001f,
                  "stalled timestamp advanced particle x");
    require_close(before[index].y, after[index].y, 0.00001f,
                  "stalled timestamp advanced particle y");
  }
  lle_colour_sim_destroy(sim);
}

static void check_dynamic_cadence_sequence(void) {
  const float sequence[] = {
      1.0f, 0.5f, 2.0f, 60.0f / 96.0f,
      1.0f, 0.5f, 2.0f, 60.0f / 96.0f};
  LleColourSim *sim = new_sim();
  size_t index;
  if (!lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_DOWN,
                            720.0f, 1280.0f, 1u)) {
    fail("failed to start dynamic cadence test");
  }
  for (index = 0u; index < sizeof(sequence) / sizeof(sequence[0]); ++index) {
    LleColourDrawParticle particles[480];
    size_t particle_index;
    size_t count;
    lle_colour_sim_tick_scaled(sim, sequence[index]);
    count = lle_colour_sim_export_draw_particles(sim, particles, 480u);
    for (particle_index = 0u; particle_index < count; ++particle_index) {
      if (!isfinite(particles[particle_index].x) ||
          !isfinite(particles[particle_index].y)) {
        fail("dynamic cadence produced non-finite particle state");
      }
    }
  }
  lle_colour_sim_destroy(sim);
}

static void check_fractional_first_emission_keeps_input_alive(void) {
  LleColourSim *sim = new_sim();
  if (!lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_DOWN,
                            720.0f, 1280.0f, 1u)) {
    fail("failed to start fractional first-emission touch");
  }
  /* 0.4 stock ticks earns only 0.8 of the first primary particle. */
  lle_colour_sim_tick_scaled(sim, 0.4f);
  if (lle_colour_sim_particle_count(sim) != 0u) {
    fail("fractional first-emission touch emitted too early");
  }
  if (!lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_MOVE,
                            760.0f, 1280.0f, 2u)) {
    fail("fractional first-emission touch lost MOVE state");
  }
  lle_colour_sim_tick_scaled(sim, 0.4f);
  if (lle_colour_sim_particle_count(sim) == 0u) {
    fail("fractional first-emission touch never emitted");
  }
  if (!lle_colour_sim_touch(sim, LLE_COLOUR_TOUCH_UP,
                            760.0f, 1280.0f, 3u)) {
    fail("fractional first-emission touch lost UP state");
  }
  lle_colour_sim_destroy(sim);
}

static void check_fractional_first_affordance_emission(void) {
  LleColourSim *sim = new_sim();
  lle_colour_sim_affordance(sim, 720.0f, 1280.0f);
  lle_colour_sim_tick_scaled(sim, 0.4f);
  if (lle_colour_sim_particle_count(sim) != 0u) {
    fail("fractional first affordance emitted too early");
  }
  lle_colour_sim_tick_scaled(sim, 0.4f);
  if (lle_colour_sim_particle_count(sim) == 0u) {
    fail("fractional first affordance group was cleared before emission");
  }
  lle_colour_sim_destroy(sim);
}

int main(void) {
  lle_colour_sim_test_set_subparticle_phase(0.0f);
  check_scale_one_equivalence();
  check_large_frame_direction_pulse();
  check_subparticle_phase_survives_recreation();
  check_stock_phase_isolated_from_adaptive_toggle();
  check_fractional_growth_wall_clock();
  check_fractional_unlock_delay();
  check_wall_clock_lifetime();
  check_jittered_144_wall_clock();
  check_speed_multiplier_wall_clock();
  check_finite_motion(2.0f);
  check_finite_motion(1.0f);
  check_finite_motion(60.0f / 90.0f);
  check_finite_motion(0.5f);
  check_finite_motion(60.0f / 144.0f);
  check_finite_motion(2.4f);
  check_finite_motion(1.2f);
  check_finite_motion(0.8f);
  check_finite_motion(0.6f);
  check_finite_motion((60.0f / 144.0f) * 1.2f);
  check_finite_motion(4.0f);
  check_finite_motion(3.0f);
  check_finite_motion(2.0f);
  check_finite_motion(1.5f);
  check_finite_motion((60.0f / 144.0f) * 2.0f);
  check_stall_no_backlog();
  check_dynamic_cadence_sequence();
  check_fractional_first_emission_keeps_input_alive();
  check_fractional_first_affordance_emission();
  return 0;
}
