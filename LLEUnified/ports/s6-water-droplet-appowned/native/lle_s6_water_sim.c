#include "lle_s6_water_sim.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#define LLE_S6_PI 3.14159265358979323846f
#define LLE_S6_GROUP_CAPACITY 18u
#define LLE_S6_PARTICLES_PER_GROUP 21u
#define LLE_S6_INVALID_GROUP UINT32_MAX
#define LLE_S6_RNG_DEGREE 31u
#define LLE_S6_RNG_SEPARATION 3u
#define LLE_S6_STOCK_DT (1.0f / 60.0f)
#define LLE_S6_NATIVE_REFRESH_MAX_SCALE 4.0f

typedef enum LleS6GroupPhase {
  LLE_S6_GROUP_FREE = 0,
  LLE_S6_GROUP_TOUCH,
  LLE_S6_GROUP_RELEASE_SHORT,
  LLE_S6_GROUP_RELEASE_EDGE,
  LLE_S6_GROUP_AFFORDANCE
} LleS6GroupPhase;

typedef enum LleS6EventType {
  LLE_S6_EVENT_TOUCH = 0,
  LLE_S6_EVENT_AFFORDANCE,
  LLE_S6_EVENT_UNLOCK,
  LLE_S6_EVENT_RESET_BG,
  LLE_S6_EVENT_TILT
} LleS6EventType;

typedef struct LleS6Particle {
  float x;
  float y;
  float velocity_x;
  float velocity_y;
  float render_offset_x;
  float render_offset_y;
  float phase;
  float extra_scale;
  float smoothing_radius;
  float rest_density;
  float pressure;
  float near_pressure;
  float viscosity_sigma;
  float viscosity_beta;
  float staged_acceleration_x;
  float staged_acceleration_y;
  float transient_force_x;
  float transient_force_y;
  float released_phase;
  float released_radius;
  float affordance_start_x;
  float affordance_start_y;
  bool active;
} LleS6Particle;

typedef struct LleS6Group {
  LleS6Particle particles[LLE_S6_PARTICLES_PER_GROUP];
  size_t count;
  float center_x;
  float center_y;
  float previous_center_x;
  float previous_center_y;
  float age;
  float emission_credit;
  uint64_t serial;
  LleS6GroupPhase phase;
} LleS6Group;

typedef struct LleS6Event {
  LleS6EventType type;
  int action;
  float x;
  float y;
  uint64_t timestamp;
} LleS6Event;

struct LleS6WaterSim {
  LleS6Group groups[LLE_S6_GROUP_CAPACITY];
  LleS6Event events[LLE_S6_WATER_EVENT_QUEUE_CAPACITY];
  size_t event_head;
  size_t event_count;

  float width;
  float height;
  float logical_width;
  float logical_height;
  float world_width;
  float world_height;
  float cell_width;

  float touch_x;
  float touch_y;
  float previous_touch_x;
  float previous_touch_y;
  float touch_delta_x;
  float touch_delta_y;
  uint64_t touch_time_ms;
  bool touching;
  bool slow_touch;

  float mapped_tilt_x;
  float mapped_tilt_y;
  uint64_t tilt_time_ns;
  float pending_tilt_x;
  float pending_tilt_y;
  uint64_t pending_tilt_time_ns;
  bool tilt_pending;

  float background_center_x;
  float background_center_y;
  float background_progress;
  float breath_phase;
  float breath_accumulator;

  float particle_size;
  float unlock_progress;
  float unlock_delay_frames;
  float unlock_tail_frames;
  bool unlocking;
  bool unlock_from_edge;

  float edge_ratio;
  float refraction_ratio;
  float edge_offset_ratio;
  float specular_ratio;

  uint32_t current_group;
  uint64_t next_group_serial;
  uint32_t rng_state[LLE_S6_RNG_DEGREE];
  uint8_t rng_front;
  uint8_t rng_rear;

  int project_kind;
  int quality;
  uint64_t frame_index;
  uint64_t reset_serial;
  bool reset_requested;
};

static float s6_clamp(float value, float minimum, float maximum) {
  if (value < minimum) {
    return minimum;
  }
  if (value > maximum) {
    return maximum;
  }
  return value;
}

static float s6_min(float a, float b) { return a < b ? a : b; }

/* Keep the recovered 60 Hz path bit-for-bit on its original multipliers. */
static float s6_temporal_multiplier(float base, float frame_scale) {
  return frame_scale == 1.0f ? base : powf(base, frame_scale);
}

static float s6_temporal_lerp(float recovered_step, float frame_scale) {
  return frame_scale == 1.0f
             ? recovered_step
             : 1.0f - powf(1.0f - recovered_step, frame_scale);
}

static float s6_quadratic(float start, float control, float end,
                          float progress) {
  return start + progress *
                     (progress * (end - start) +
                      (1.0f - progress) * 2.0f * (control - start));
}

static float s6_sine33(float value) {
  static const float points[2][3] = {
      {0.0f, 0.05f, 0.495f},
      {0.495f, 0.94f, 1.0f},
  };
  const float bounded = s6_clamp(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 2.0f);
  float progress;
  if (segment > 1) {
    segment = 1;
  }
  progress = (bounded - (float)segment * 0.5f) * 2.0f;
  return s6_quadratic(points[segment][0], points[segment][1],
                      points[segment][2], progress);
}

static float s6_sine70(float value) {
  static const float points[3][3] = {
      {0.0f, 0.01f, 0.45f},
      {0.45f, 0.8f, 0.908f},
      {0.908f, 0.9999f, 1.0f},
  };
  const float bounded = s6_clamp(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 3.0f);
  float progress;
  if (segment > 2) {
    segment = 2;
  }
  progress = (bounded - (float)segment / 3.0f) * 3.0f;
  return s6_quadratic(points[segment][0], points[segment][1],
                      points[segment][2], progress);
}

static float s6_sine80(float value) {
  static const float points[5][3] = {
      {0.0f, 0.0f, 0.195f},     {0.195f, 0.48f, 0.645f},
      {0.645f, 0.835f, 0.885f}, {0.885f, 0.955f, 0.978f},
      {0.978f, 0.9999f, 1.0f},
  };
  const float bounded = s6_clamp(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 5.0f);
  float progress;
  if (segment > 4) {
    segment = 4;
  }
  progress = (bounded - (float)segment * 0.2f) * 5.0f;
  return s6_quadratic(points[segment][0], points[segment][1],
                      points[segment][2], progress);
}

static float s6_sine90(float value) {
  static const float points[5][3] = {
      {0.0f, 0.0f, 0.247f},   {0.247f, 0.48f, 0.72f},
      {0.7f, 0.835f, 0.905f}, {0.91f, 0.955f, 0.978f},
      {0.978f, 0.9999f, 1.0f},
  };
  const float bounded = s6_clamp(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 5.0f);
  float progress;
  if (segment > 4) {
    segment = 4;
  }
  progress = (bounded - (float)segment * 0.2f) * 5.0f;
  return s6_quadratic(points[segment][0], points[segment][1],
                      points[segment][2], progress);
}

static float s6_quint_out(float value) {
  static const float points[2][3] = {
      {0.04f, 0.718f, 0.84f},
      {0.845f, 0.998f, 1.0f},
  };
  const float bounded = s6_clamp(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 2.0f);
  float progress;
  if (segment > 1) {
    segment = 1;
  }
  progress = (bounded - (float)segment * 0.5f) * 2.0f;
  return s6_quadratic(points[segment][0], points[segment][1],
                      points[segment][2], progress);
}

static uint32_t s6_rng_u32(LleS6WaterSim *sim) {
  const uint32_t result =
      sim->rng_state[sim->rng_front] + sim->rng_state[sim->rng_rear];
  sim->rng_state[sim->rng_front] = result;
  if (++sim->rng_front >= LLE_S6_RNG_DEGREE) {
    sim->rng_front = 0u;
  }
  if (++sim->rng_rear >= LLE_S6_RNG_DEGREE) {
    sim->rng_rear = 0u;
  }
  return (result >> 1u) & UINT32_C(0x7fffffff);
}

static float s6_rng_unit(LleS6WaterSim *sim) {
  return (float)s6_rng_u32(sim) * (1.0f / 2147483648.0f);
}

static void s6_seed_rng(LleS6WaterSim *sim, uint64_t requested_seed) {
  size_t index;
  uint32_t seed = (uint32_t)requested_seed;
  if (seed == 0u) {
    seed = 1u;
  }
  sim->rng_state[0] = seed;
  for (index = 1u; index < LLE_S6_RNG_DEGREE; ++index) {
    const int32_t previous = (int32_t)sim->rng_state[index - 1u];
    const int32_t high = previous / 127773;
    const int32_t low = previous % 127773;
    int32_t next = 16807 * low - 2836 * high;
    if (next <= 0) {
      next += INT32_MAX;
    }
    sim->rng_state[index] = (uint32_t)next;
  }
  sim->rng_front = LLE_S6_RNG_SEPARATION;
  sim->rng_rear = 0u;
  for (index = 0u; index < 10u * LLE_S6_RNG_DEGREE; ++index) {
    (void)s6_rng_u32(sim);
  }
}

static float s6_pixels_per_world(const LleS6WaterSim *sim) {
  return sim->world_width > 0.0f ? sim->width / sim->world_width : 1.0f;
}

static void s6_configure_world(LleS6WaterSim *sim) {
  const float aspect = sim->width > 0.0f ? sim->height / sim->width : 1.0f;
  sim->world_width = aspect > 1.0f ? 0.45f : 0.6f;
  sim->world_height = sim->world_width * aspect;
  sim->cell_width = 0.025f;
}

static bool s6_group_is_primary(const LleS6Group *group) {
  return group->phase == LLE_S6_GROUP_TOUCH ||
         group->phase == LLE_S6_GROUP_RELEASE_SHORT ||
         group->phase == LLE_S6_GROUP_RELEASE_EDGE;
}

static bool s6_group_is_secondary(const LleS6Group *group) {
  return group->phase == LLE_S6_GROUP_AFFORDANCE;
}

static void s6_clear_group(LleS6Group *group) {
  memset(group, 0, sizeof(*group));
  group->phase = LLE_S6_GROUP_FREE;
}

static size_t s6_primary_count(const LleS6WaterSim *sim) {
  size_t group_index;
  size_t count = 0u;
  for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
       ++group_index) {
    if (s6_group_is_primary(&sim->groups[group_index])) {
      count += sim->groups[group_index].count;
    }
  }
  return count;
}

static uint32_t s6_allocate_group(LleS6WaterSim *sim,
                                  LleS6GroupPhase phase) {
  uint32_t index;
  uint32_t oldest = LLE_S6_INVALID_GROUP;
  uint64_t oldest_serial = UINT64_MAX;
  for (index = 0u; index < LLE_S6_GROUP_CAPACITY; ++index) {
    if (sim->groups[index].phase == LLE_S6_GROUP_FREE) {
      oldest = index;
      break;
    }
    if (sim->groups[index].phase != LLE_S6_GROUP_TOUCH &&
        sim->groups[index].serial < oldest_serial) {
      oldest = index;
      oldest_serial = sim->groups[index].serial;
    }
  }
  if (oldest == LLE_S6_INVALID_GROUP) {
    return oldest;
  }
  s6_clear_group(&sim->groups[oldest]);
  sim->groups[oldest].phase = phase;
  sim->groups[oldest].serial = ++sim->next_group_serial;
  return oldest;
}

static void s6_initialize_particle_at(const LleS6WaterSim *sim,
                                      LleS6Particle *particle,
                                      float x, float y) {
  memset(particle, 0, sizeof(*particle));
  particle->x = x;
  particle->y = y;
  particle->phase = 0.1f;
  particle->extra_scale = 0.0f;
  particle->smoothing_radius =
      sim->cell_width * 0.1f * s6_pixels_per_world(sim);
  particle->rest_density = 12.0f;
  particle->pressure = 0.0f;
  particle->near_pressure = 0.0f;
  particle->viscosity_sigma = 3.0f;
  particle->viscosity_beta = 0.5f;
  particle->active = true;
}

static void s6_initialize_particle(LleS6WaterSim *sim, LleS6Group *group,
                                   LleS6Particle *particle) {
  const float radius =
      sqrtf(s6_rng_unit(sim)) * sim->cell_width * 0.1f *
      s6_pixels_per_world(sim);
  const float angle = s6_rng_unit(sim) * 2.0f * LLE_S6_PI;
  s6_initialize_particle_at(
      sim, particle,
      group->center_x + cosf(angle) * radius,
      group->center_y + sinf(angle) * radius);
}

static void s6_emit(LleS6WaterSim *sim, LleS6Group *group, size_t count,
                    bool enforce_primary_cap) {
  size_t primary = s6_primary_count(sim);
  if (enforce_primary_cap) {
    if (primary >= LLE_S6_WATER_PRIMARY_PARTICLE_LIMIT) {
      return;
    }
    if (count > LLE_S6_WATER_PRIMARY_PARTICLE_LIMIT - primary) {
      count = LLE_S6_WATER_PRIMARY_PARTICLE_LIMIT - primary;
    }
  }
  if (count > LLE_S6_PARTICLES_PER_GROUP - group->count) {
    count = LLE_S6_PARTICLES_PER_GROUP - group->count;
  }
  while (count-- > 0u) {
    s6_initialize_particle(sim, group, &group->particles[group->count]);
    ++group->count;
  }
}

/* Preserve the recovered two-particles-per-60-Hz-tick rate at arbitrary
 * display refreshes. The fractional remainder lives with the group so 120 Hz
 * alternates one/two particle emissions instead of doubling the density. */
static void s6_emit_scaled(LleS6WaterSim *sim, LleS6Group *group,
                           float count, bool enforce_primary_cap) {
  size_t whole_count;
  if (group == NULL || !isfinite(count) || count <= 0.0f) {
    return;
  }
  group->emission_credit =
      s6_clamp(group->emission_credit + count, 0.0f,
               (float)LLE_S6_PARTICLES_PER_GROUP);
  whole_count = (size_t)floorf(group->emission_credit);
  if (whole_count == 0u) {
    return;
  }
  group->emission_credit -= (float)whole_count;
  s6_emit(sim, group, whole_count, enforce_primary_cap);
}

static void s6_constrain(const LleS6WaterSim *sim,
                         LleS6Particle *particle) {
  if (particle->x < 0.0f) {
    const float overshoot = -particle->x;
    particle->velocity_x = -particle->velocity_x * 0.15f;
    particle->x = 2.0f * overshoot;
  }
  if (particle->x > sim->width) {
    const float overshoot = particle->x - sim->width;
    particle->velocity_x = -particle->velocity_x * 0.15f;
    particle->x = sim->width - 2.0f * overshoot;
  }
  if (particle->y < 0.0f) {
    const float overshoot = -particle->y;
    particle->velocity_y = -particle->velocity_y * 0.15f;
    particle->y = 2.0f * overshoot;
  }
  if (particle->y > sim->height) {
    const float overshoot = particle->y - sim->height;
    particle->velocity_y = -particle->velocity_y * 0.15f;
    particle->y = sim->height - 2.0f * overshoot;
  }
}

static void s6_apply_sph_forces(LleS6WaterSim *sim, bool primary,
                                float solver_dt) {
  size_t group_a_index;
  const float pixels_per_world = s6_pixels_per_world(sim);
  for (group_a_index = 0u; group_a_index < LLE_S6_GROUP_CAPACITY;
       ++group_a_index) {
    LleS6Group *group_a = &sim->groups[group_a_index];
    size_t particle_a_index;
    if ((primary && !s6_group_is_primary(group_a)) ||
        (!primary && !s6_group_is_secondary(group_a))) {
      continue;
    }
    for (particle_a_index = 0u; particle_a_index < group_a->count;
         ++particle_a_index) {
      LleS6Particle *a = &group_a->particles[particle_a_index];
      size_t group_b_index;
      float density = 0.0f;
      float near_density = 0.0f;
      float self_x = 0.0f;
      float self_y = 0.0f;
      if (!a->active || a->smoothing_radius <= 0.0f) {
        continue;
      }
      for (group_b_index = 0u; group_b_index < LLE_S6_GROUP_CAPACITY;
           ++group_b_index) {
        LleS6Group *group_b = &sim->groups[group_b_index];
        size_t particle_b_index;
        if ((primary && !s6_group_is_primary(group_b)) ||
            (!primary && !s6_group_is_secondary(group_b))) {
          continue;
        }
        for (particle_b_index = 0u; particle_b_index < group_b->count;
             ++particle_b_index) {
          const LleS6Particle *b = &group_b->particles[particle_b_index];
          float dx;
          float dy;
          float distance;
          float q;
          if (b == a || !b->active) {
            continue;
          }
          dx = (b->x - a->x) / pixels_per_world;
          dy = (b->y - a->y) / pixels_per_world;
          distance = sqrtf(dx * dx + dy * dy);
          if (distance <= 1.0e-7f ||
              distance >= a->smoothing_radius / pixels_per_world) {
            continue;
          }
          q = 1.0f -
              distance / (a->smoothing_radius / pixels_per_world);
          near_density += q * q;
          density += q * q * q;
        }
      }
      for (group_b_index = 0u; group_b_index < LLE_S6_GROUP_CAPACITY;
           ++group_b_index) {
        LleS6Group *group_b = &sim->groups[group_b_index];
        size_t particle_b_index;
        if ((primary && !s6_group_is_primary(group_b)) ||
            (!primary && !s6_group_is_secondary(group_b))) {
          continue;
        }
        for (particle_b_index = 0u; particle_b_index < group_b->count;
             ++particle_b_index) {
          LleS6Particle *b = &group_b->particles[particle_b_index];
          float dx;
          float dy;
          float distance;
          float q;
          float nx;
          float ny;
          float relative_velocity;
          float impulse;
          float impulse_x;
          float impulse_y;
          if (b == a || !b->active) {
            continue;
          }
          dx = (b->x - a->x) / pixels_per_world;
          dy = (b->y - a->y) / pixels_per_world;
          distance = sqrtf(dx * dx + dy * dy);
          if (distance <= 1.0e-7f ||
              distance >= a->smoothing_radius / pixels_per_world) {
            continue;
          }
          q = 1.0f -
              distance / (a->smoothing_radius / pixels_per_world);
          nx = dx / distance;
          ny = dy / distance;
          relative_velocity =
              nx * ((a->velocity_x - b->velocity_x) / pixels_per_world) +
              ny * ((a->velocity_y - b->velocity_y) / pixels_per_world);
          impulse =
              q * solver_dt * 0.5f *
              ((near_density - a->rest_density) * a->pressure +
               density * a->near_pressure * q +
               a->viscosity_sigma * relative_velocity);
          impulse_x = nx * impulse * pixels_per_world;
          impulse_y = ny * impulse * pixels_per_world;
          self_x -= impulse_x;
          self_y -= impulse_y;
          b->velocity_x += impulse_x;
          b->velocity_y += impulse_y;
          b->x += solver_dt * impulse_x;
          b->y += solver_dt * impulse_y;
        }
      }
      a->velocity_x += self_x;
      a->velocity_y += self_y;
      a->x += solver_dt * self_x;
      a->y += solver_dt * self_y;
    }
  }
}

static void s6_advance_domain(LleS6WaterSim *sim, bool primary,
                              float frame_scale) {
  size_t step;
  const float bounded_scale =
      s6_clamp(frame_scale, 0.25f, LLE_S6_NATIVE_REFRESH_MAX_SCALE);
  const unsigned int scale_segments =
      (unsigned int)fmaxf(1.0f, ceilf(bounded_scale));
  const size_t solver_steps =
      (size_t)LLE_S6_WATER_SOLVER_STEPS_PER_TICK * scale_segments;
  const float solver_dt =
      LLE_S6_STOCK_DT * bounded_scale / (float)scale_segments;
  const bool stock_tick = frame_scale == 1.0f;
  /* The recovered 60 Hz tick consumes a transient impulse in its first
   * solver pass. At a non-60-Hz cadence retain it through all stability
   * segments and split the total elapsed-time impulse between them. */
  const float transient_scale =
      stock_tick ? 1.0f : bounded_scale / (float)solver_steps;
  for (step = 0u; step < solver_steps; ++step) {
    size_t group_index;
    const float pixels_per_world = s6_pixels_per_world(sim);
    for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
         ++group_index) {
      LleS6Group *group = &sim->groups[group_index];
      size_t particle_index;
      if ((primary && !s6_group_is_primary(group)) ||
          (!primary && !s6_group_is_secondary(group))) {
        continue;
      }
      for (particle_index = 0u; particle_index < group->count;
           ++particle_index) {
        LleS6Particle *particle = &group->particles[particle_index];
        if (!particle->active) {
          continue;
        }
        particle->velocity_x +=
            particle->staged_acceleration_x * pixels_per_world *
            solver_dt;
        particle->velocity_y +=
            particle->staged_acceleration_y * pixels_per_world *
            solver_dt;
      }
    }
    s6_apply_sph_forces(sim, primary, solver_dt);
    for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
         ++group_index) {
      LleS6Group *group = &sim->groups[group_index];
      size_t particle_index;
      if ((primary && !s6_group_is_primary(group)) ||
          (!primary && !s6_group_is_secondary(group))) {
        continue;
      }
      for (particle_index = 0u; particle_index < group->count;
           ++particle_index) {
        LleS6Particle *particle = &group->particles[particle_index];
        if (!particle->active) {
          continue;
        }
        particle->velocity_x +=
            particle->transient_force_x * transient_scale;
        particle->velocity_y +=
            particle->transient_force_y * transient_scale;
        if (stock_tick || step + 1u == solver_steps) {
          particle->transient_force_x = 0.0f;
          particle->transient_force_y = 0.0f;
        }
        particle->x += particle->velocity_x * solver_dt;
        particle->y += particle->velocity_y * solver_dt;
        s6_constrain(sim, particle);
      }
    }
  }
}

static float s6_wall_curve(const LleS6WaterSim *sim, float position,
                           float extent) {
  const float distance =
      2.0f * fabsf(position / fmaxf(extent, 1.0f) - 0.5f);
  float base;
  float progress;
  if (distance <= 0.1f) {
    return 0.0f;
  }
  if (sim->project_kind == LLE_S6_WATER_PROJECT_PHONE) {
    if (distance >= 0.7f) {
      return 1.0f;
    }
    progress = (distance - 0.1f) * (1.0f / 0.6f);
    return (1.0f - cosf(progress * LLE_S6_PI)) * 0.5f;
  }
  if (distance >= 1.0f) {
    base = 1.0f;
  } else {
    progress = (distance - 0.1f) * (1.0f / 0.9f);
    base = (1.0f - cosf(progress * LLE_S6_PI)) * 0.5f;
  }
  return s6_sine90(base);
}

static void s6_nudge_primary_neighbours(LleS6WaterSim *sim,
                                         const LleS6Particle *source,
                                         float offset, bool horizontal) {
  size_t group_index;
  const float radius_squared =
      source->smoothing_radius * source->smoothing_radius;
  for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
       ++group_index) {
    LleS6Group *group = &sim->groups[group_index];
    size_t particle_index;
    if (!s6_group_is_primary(group)) {
      continue;
    }
    for (particle_index = 0u; particle_index < group->count;
         ++particle_index) {
      LleS6Particle *particle = &group->particles[particle_index];
      float dx;
      float dy;
      if (!particle->active || particle == source) {
        continue;
      }
      dx = particle->x - source->x;
      dy = particle->y - source->y;
      if (dx * dx + dy * dy >= radius_squared) {
        continue;
      }
      if (horizontal) {
        particle->render_offset_x += offset;
      } else {
        particle->render_offset_y += offset;
      }
    }
  }
}

/*
 * Stock has two independent wall mechanisms:
 *   - a broad, outward acceleration staged for the next pair of SPH steps;
 *   - a hard-edge render displacement stored at +0x80/+0x84.
 * The hard displacement never enters velocity.  RELEASE_SHORT uses only the
 * wider and softer render displacement; affordance particles use neither.
 */
static void s6_apply_wall_fields(LleS6WaterSim *sim, float frame_scale) {
  size_t group_index;
  const bool tablet =
      sim->project_kind == LLE_S6_WATER_PROJECT_TABLET;
  const float project_scale = tablet ? 0.5f : 1.0f;
  const float bounded_scale =
      s6_clamp(frame_scale, 0.25f, LLE_S6_NATIVE_REFRESH_MAX_SCALE);
  const float sensor_x =
      -s6_clamp(sim->mapped_tilt_x, -10.0f, 10.0f) * 0.01f;
  const float sensor_y =
      s6_clamp(sim->mapped_tilt_y, -10.0f, 10.0f) * 0.015f;
  for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
       ++group_index) {
    LleS6Group *group = &sim->groups[group_index];
    const bool broad =
        group->phase == LLE_S6_GROUP_TOUCH ||
        group->phase == LLE_S6_GROUP_RELEASE_EDGE;
    const bool short_release =
        group->phase == LLE_S6_GROUP_RELEASE_SHORT;
    const float hard_band =
        short_release ? sim->width * 0.2f : sim->width * 0.1f;
    const float hard_step =
        0.35f * project_scale *
        (short_release ? 0.004166667f : 0.015f) * bounded_scale *
        s6_pixels_per_world(sim);
    size_t particle_index;
    if (!broad && !short_release) {
      continue;
    }
    for (particle_index = 0u; particle_index < group->count;
         ++particle_index) {
      LleS6Particle *particle = &group->particles[particle_index];
      bool hard_x = false;
      bool hard_y = false;
      float direction_x = 0.0f;
      float direction_y = 0.0f;
      if (!particle->active) {
        continue;
      }
      if (particle->x < hard_band) {
        hard_x = true;
        direction_x = -1.0f;
      } else if (particle->x > sim->width - hard_band) {
        hard_x = true;
        direction_x = 1.0f;
      }
      if (tablet) {
        const float y_band =
            short_release ? hard_band : sim->height * 0.1f;
        if (particle->y < y_band) {
          hard_y = true;
          direction_y = -1.0f;
        } else if (particle->y > sim->height - y_band) {
          hard_y = true;
          direction_y = 1.0f;
        }
      }
      if (hard_x) {
        if (broad && tablet) {
          particle->render_offset_x *=
              s6_temporal_multiplier(1.0075f, bounded_scale);
        }
        particle->render_offset_x += direction_x * hard_step;
        if (broad) {
          particle->phase =
              s6_clamp(particle->phase - 0.015f * bounded_scale,
                       0.0f, 1.0f);
          particle->rest_density = tablet ? 4.5f : 3.0f;
          s6_nudge_primary_neighbours(
              sim, particle, direction_x * 0.35f * 0.0001f * bounded_scale *
                                 s6_pixels_per_world(sim),
              true);
        }
      }
      if (hard_y) {
        particle->render_offset_y += direction_y * hard_step;
        if (broad) {
          particle->rest_density = 4.5f;
          s6_nudge_primary_neighbours(
              sim, particle, direction_y * 0.35f * 0.0001f * bounded_scale *
                                 s6_pixels_per_world(sim),
              false);
        }
      }
      if (broad) {
        const float wall_x =
            (particle->x < sim->width * 0.5f ? -0.35f : 0.35f) *
            s6_wall_curve(sim, particle->x, sim->width);
        const float wall_y =
            tablet
                ? (particle->y < sim->height * 0.5f ? -0.35f : 0.35f) *
                      s6_wall_curve(sim, particle->y, sim->height)
                : 0.0f;
        const bool use_sensor =
            !tablet ||
            (particle->render_offset_x == 0.0f &&
             particle->render_offset_y == 0.0f);
        particle->staged_acceleration_x =
            wall_x + (use_sensor ? sensor_x : 0.0f);
        particle->staged_acceleration_y =
            wall_y + (use_sensor ? sensor_y : 0.0f);
      }
    }
  }
}

static void s6_grow_particle(const LleS6WaterSim *sim,
                             LleS6Particle *particle, float increment,
                             float frame_scale) {
  float curve;
  float blend;
  const float target_density =
      sim->project_kind == LLE_S6_WATER_PROJECT_TABLET ? 6.0f : 4.0f;
  const float target_radius =
      sim->cell_width * 3.7f * s6_pixels_per_world(sim);
  if (particle->phase >= 1.0f) {
    return;
  }
  particle->phase = s6_min(particle->phase + increment, 1.0f);
  curve = s6_sine33(particle->phase);
  /* phase is already advanced in elapsed 60 Hz ticks. Convert the recovered
   * per-tick attraction coefficient as well, otherwise high-refresh draws
   * converge the density state two or more times as quickly in wall time. */
  blend = s6_temporal_lerp(curve, frame_scale);
  particle->smoothing_radius +=
      (target_radius - particle->smoothing_radius) * blend;
  particle->rest_density +=
      (target_density - particle->rest_density) * blend;
  particle->pressure += (0.4f - particle->pressure) * blend;
  particle->near_pressure += (0.4f - particle->near_pressure) * blend;
}

static void s6_advance_affordance(LleS6WaterSim *sim, LleS6Group *group,
                                  float frame_scale) {
  bool mature = group->count >= LLE_S6_WATER_AFFORDANCE_PARTICLE_LIMIT;
  bool finished = false;
  /*
   * Stock keeps custom-event field 2 at its 1.0 default for both projects.
   * Do not conflate it with the separate tablet wall scale (0.5).
   */
  const float interaction_scale = 1.0f;
  size_t index;
  if (group->age >= 1.0f) {
    const float source = 2.0f - group->age;
    float return_progress = 0.0f;
    if (source > 0.2f) {
      return_progress = 1.0f;
      if (source < 1.0f) {
        const float window =
            s6_clamp((source - 0.2f) * 1.25f, 0.0f, 1.0f);
        return_progress =
            s6_sine70((1.0f - cosf(window * LLE_S6_PI)) * 0.5f);
      }
    }
    for (index = 0u; index < group->count; ++index) {
      LleS6Particle *particle = &group->particles[index];
      particle->phase = particle->released_phase * s6_sine33(source);
      particle->extra_scale *= s6_temporal_multiplier(
          fmaxf(source, 0.0f), frame_scale);
      particle->x =
          group->center_x +
          (particle->affordance_start_x - group->center_x) *
              return_progress;
      particle->y =
          group->center_y +
          (particle->affordance_start_y - group->center_y) *
              return_progress;
    }
    group->age += 0.02f * frame_scale;
    finished = source <= 0.15f;
  } else {
    for (index = 0u; index < group->count; ++index) {
      LleS6Particle *particle = &group->particles[index];
      if (particle->phase < 0.5f) {
        mature = false;
        s6_grow_particle(
            sim, particle, 0.021666666f * frame_scale, frame_scale);
        particle->extra_scale += 0.0025f * frame_scale;
      }
      {
        const float dx = group->center_x - particle->x;
        const float dy = group->center_y - particle->y;
        if (sqrtf(dx * dx + dy * dy) > sim->width * 0.16f) {
          const float attraction = 1.75f - interaction_scale;
          const float velocity_scale = 1.9f - interaction_scale;
          particle->transient_force_x += attraction * dx;
          particle->transient_force_y += attraction * dy;
          particle->velocity_x *=
              s6_temporal_multiplier(velocity_scale, frame_scale);
          particle->velocity_y *=
              s6_temporal_multiplier(velocity_scale, frame_scale);
        }
      }
    }
    if (mature) {
      group->age = 1.0f;
      for (index = 0u; index < group->count; ++index) {
        LleS6Particle *particle = &group->particles[index];
        particle->affordance_start_x = particle->x;
        particle->affordance_start_y = particle->y;
        particle->released_phase = particle->phase;
      }
    }
  }
  if (finished) {
    for (index = 0u; index < group->count; ++index) {
      group->particles[index].active = false;
    }
  }
}

static void s6_advance_group(LleS6WaterSim *sim, LleS6Group *group,
                             float frame_scale) {
  size_t index;
  if (group->phase == LLE_S6_GROUP_AFFORDANCE) {
    s6_advance_affordance(sim, group, frame_scale);
    return;
  }
  if (group->phase == LLE_S6_GROUP_RELEASE_SHORT) {
    bool finished = false;
    float running_max = 0.0f;
    group->age += 0.02f * frame_scale;
    for (index = 0u; index < group->count; ++index) {
      LleS6Particle *particle = &group->particles[index];
      if (group->age >= 0.3f) {
        const float source =
            1.0f - (group->age - 0.3f) * (1.0f / 0.7f);
        if (source <= 0.0f) {
          finished = true;
        } else {
          particle->phase =
              particle->released_phase * s6_quint_out(source);
          particle->smoothing_radius =
              particle->released_radius *
              s6_quint_out(source * source);
        }
      } else {
        const bool below =
            index > 0u && particle->phase < running_max;
        if (!below) {
          running_max = particle->phase;
        }
        if (below || particle->phase < 0.35f) {
          s6_grow_particle(sim, particle,
                           LLE_S6_STOCK_DT * frame_scale, frame_scale);
          particle->released_phase = particle->phase;
          particle->released_radius = particle->smoothing_radius;
        }
      }
    }
    if (finished || group->age >= 1.0f) {
      for (index = 0u; index < group->count; ++index) {
        group->particles[index].active = false;
      }
    }
    return;
  }
  if (group->phase == LLE_S6_GROUP_RELEASE_EDGE) {
    const float lifetime =
        sim->project_kind == LLE_S6_WATER_PROJECT_TABLET
            ? 75.0f
            : 50.0f;
    for (index = 0u; index < group->count; ++index) {
      LleS6Particle *particle = &group->particles[index];
      if (particle->active && particle->phase < 0.4f) {
        s6_grow_particle(sim, particle, 0.008f * frame_scale, frame_scale);
      }
    }
    group->age += frame_scale;
    if (group->age > lifetime) {
      /*
       * A mature drag first follows Samsung's long edge-release window, but
       * it must still pass through the recovered short-release shrink curve.
       * Clearing the group here made a fully visible droplet disappear in one
       * frame on large displays.  Enter at the start of the fade portion so
       * the edge motion is not replayed and both fixed/adaptive clocks share
       * the same wall-time cleanup.
       */
      group->phase = LLE_S6_GROUP_RELEASE_SHORT;
      group->age = 0.3f;
      for (index = 0u; index < group->count; ++index) {
        LleS6Particle *particle = &group->particles[index];
        if (!particle->active) {
          continue;
        }
        particle->released_phase = particle->phase;
        particle->released_radius = particle->smoothing_radius;
      }
    }
    return;
  }
  if (group->phase == LLE_S6_GROUP_TOUCH) {
    /* See the stock custom-event field distinction above. */
    const float interaction_scale = 1.0f;
    const float attraction = 2.5f - interaction_scale;
    const float velocity_scale = 1.6f - interaction_scale;
    const float attraction_threshold =
        fminf(sim->width, sim->height) * 0.16f;
    for (index = 0u; index < group->count; ++index) {
      LleS6Particle *particle = &group->particles[index];
      float dx;
      float dy;
      s6_grow_particle(
          sim, particle, 0.024166668f * frame_scale, frame_scale);
      if (particle->phase >= 1.0f) {
        if (sim->slow_touch) {
          particle->extra_scale =
              s6_min(particle->extra_scale +
                         0.0016666668f * frame_scale,
                     1.19f);
        } else {
          particle->extra_scale =
              s6_clamp(particle->extra_scale *
                           s6_temporal_multiplier(0.985f, frame_scale) -
                           0.004f * frame_scale,
                        0.0f, 1.2f);
        }
      }
      particle->transient_force_x += sim->touch_delta_x;
      particle->transient_force_y += sim->touch_delta_y;
      dx = sim->touch_x - particle->x;
      dy = sim->touch_y - particle->y;
      if (sqrtf(dx * dx + dy * dy) > attraction_threshold) {
        particle->transient_force_x += attraction * dx;
        particle->transient_force_y += attraction * dy;
        particle->velocity_x *=
            s6_temporal_multiplier(velocity_scale, frame_scale);
        particle->velocity_y *=
            s6_temporal_multiplier(velocity_scale, frame_scale);
      }
    }
  }
}

static void s6_release_current(LleS6WaterSim *sim) {
  LleS6Group *group;
  size_t index;
  if (sim->current_group == LLE_S6_INVALID_GROUP) {
    return;
  }
  group = &sim->groups[sim->current_group];
  if (group->phase == LLE_S6_GROUP_TOUCH) {
    group->phase =
        group->count < LLE_S6_WATER_TOUCH_PARTICLE_LIMIT ||
                (group->count > 0u && group->particles[0].phase < 0.4f)
            ? LLE_S6_GROUP_RELEASE_SHORT
            : LLE_S6_GROUP_RELEASE_EDGE;
    group->age = 0.0f;
    for (index = 0u; index < group->count; ++index) {
      group->particles[index].released_phase =
          group->particles[index].phase;
      group->particles[index].released_radius =
          group->particles[index].smoothing_radius;
    }
  }
  sim->current_group = LLE_S6_INVALID_GROUP;
}

static void s6_apply_touch(LleS6WaterSim *sim, int action, float x,
                           float y, uint64_t event_time_ms) {
  x = s6_clamp(x, 0.0f, sim->width);
  y = s6_clamp(y, 0.0f, sim->height);
  if (action == LLE_S6_WATER_TOUCH_DOWN) {
    uint32_t group_index;
    s6_release_current(sim);
    group_index = s6_allocate_group(sim, LLE_S6_GROUP_TOUCH);
    if (group_index == LLE_S6_INVALID_GROUP) {
      return;
    }
    sim->current_group = group_index;
    sim->touching = true;
    sim->slow_touch = false;
    sim->touch_x = x;
    sim->touch_y = y;
    sim->previous_touch_x = x;
    sim->previous_touch_y = y;
    sim->touch_delta_x = 0.0f;
    sim->touch_delta_y = 0.0f;
    sim->touch_time_ms = event_time_ms;
    sim->groups[group_index].center_x = x;
    sim->groups[group_index].center_y = y;
    sim->groups[group_index].previous_center_x = x;
    sim->groups[group_index].previous_center_y = y;
  } else if (action == LLE_S6_WATER_TOUCH_MOVE && sim->touching) {
    const float dx = x - sim->touch_x;
    const float dy = y - sim->touch_y;
    sim->previous_touch_x = sim->touch_x;
    sim->previous_touch_y = sim->touch_y;
    sim->touch_x = x;
    sim->touch_y = y;
    sim->touch_delta_x = dx;
    sim->touch_delta_y = dy;
    sim->slow_touch = sqrtf(dx * dx + dy * dy) < sim->width * 0.01f;
    sim->touch_time_ms = event_time_ms;
  } else if (action == LLE_S6_WATER_TOUCH_UP && sim->touching) {
    sim->touch_x = x;
    sim->touch_y = y;
    sim->touching = false;
    sim->slow_touch = false;
    sim->touch_delta_x = 0.0f;
    sim->touch_delta_y = 0.0f;
    s6_release_current(sim);
  }
}

static void s6_apply_affordance(LleS6WaterSim *sim, float x, float y) {
  uint32_t index;
  LleS6Group *group;
  for (index = 0u; index < LLE_S6_GROUP_CAPACITY; ++index) {
    if (sim->groups[index].phase == LLE_S6_GROUP_AFFORDANCE) {
      return;
    }
  }
  index = s6_allocate_group(sim, LLE_S6_GROUP_AFFORDANCE);
  if (index == LLE_S6_INVALID_GROUP) {
    return;
  }
  group = &sim->groups[index];
  group->center_x = s6_clamp(x, 0.0f, sim->width);
  group->center_y = s6_clamp(y, 0.0f, sim->height);
  group->previous_center_x = group->center_x;
  group->previous_center_y = group->center_y;
  s6_initialize_particle_at(
      sim, &group->particles[0], group->center_x, group->center_y);
  group->count = 1u;
}

static void s6_apply_unlock(LleS6WaterSim *sim) {
  const float dx = sim->touch_x - sim->width * 0.5f;
  const float dy = sim->touch_y - sim->height * 0.5f;
  const float minimum_dimension = s6_min(sim->width, sim->height);
  sim->unlocking = true;
  sim->unlock_from_edge =
      sqrtf(dx * dx + dy * dy) > minimum_dimension * 0.4f;
}

static bool s6_enqueue(LleS6WaterSim *sim, const LleS6Event *event) {
  size_t tail;
  if (sim == NULL || event == NULL ||
      sim->event_count >= LLE_S6_WATER_EVENT_QUEUE_CAPACITY) {
    return false;
  }
  tail = (sim->event_head + sim->event_count) %
         LLE_S6_WATER_EVENT_QUEUE_CAPACITY;
  sim->events[tail] = *event;
  ++sim->event_count;
  return true;
}

static void s6_drain_events(LleS6WaterSim *sim) {
  if (sim->tilt_pending) {
    sim->mapped_tilt_x = sim->pending_tilt_x;
    sim->mapped_tilt_y = sim->pending_tilt_y;
    sim->tilt_time_ns = sim->pending_tilt_time_ns;
    sim->tilt_pending = false;
  }
  while (sim->event_count > 0u) {
    const LleS6Event event = sim->events[sim->event_head];
    sim->event_head =
        (sim->event_head + 1u) % LLE_S6_WATER_EVENT_QUEUE_CAPACITY;
    --sim->event_count;
    switch (event.type) {
      case LLE_S6_EVENT_TOUCH:
        s6_apply_touch(sim, event.action, event.x, event.y,
                       event.timestamp);
        break;
      case LLE_S6_EVENT_AFFORDANCE:
        s6_apply_affordance(sim, event.x, event.y);
        break;
      case LLE_S6_EVENT_UNLOCK:
        s6_apply_unlock(sim);
        break;
      case LLE_S6_EVENT_RESET_BG:
        sim->background_progress = 0.0f;
        break;
      case LLE_S6_EVENT_TILT:
        break;
      default:
        break;
    }
  }
}

static void s6_reset_state(LleS6WaterSim *sim) {
  size_t index;
  const float retained_tilt_x = sim->mapped_tilt_x;
  const float retained_tilt_y = sim->mapped_tilt_y;
  const uint64_t retained_tilt_time = sim->tilt_time_ns;
  for (index = 0u; index < LLE_S6_GROUP_CAPACITY; ++index) {
    s6_clear_group(&sim->groups[index]);
  }
  sim->event_head = 0u;
  sim->event_count = 0u;
  sim->current_group = LLE_S6_INVALID_GROUP;
  sim->touch_x = sim->width * 0.5f;
  sim->touch_y = sim->height * 0.5f;
  sim->previous_touch_x = sim->touch_x;
  sim->previous_touch_y = sim->touch_y;
  sim->touch_delta_x = 0.0f;
  sim->touch_delta_y = 0.0f;
  sim->touch_time_ms = 0u;
  sim->touching = false;
  sim->slow_touch = false;
  sim->mapped_tilt_x = retained_tilt_x;
  sim->mapped_tilt_y = retained_tilt_y;
  sim->tilt_time_ns = retained_tilt_time;
  sim->pending_tilt_x = retained_tilt_x;
  sim->pending_tilt_y = retained_tilt_y;
  sim->pending_tilt_time_ns = retained_tilt_time;
  sim->tilt_pending = false;
  sim->background_center_x = sim->touch_x;
  sim->background_center_y = sim->touch_y;
  sim->background_progress = 1.0f;
  sim->breath_phase = 0.0f;
  sim->breath_accumulator = 0.0f;
  sim->particle_size =
      sim->project_kind == LLE_S6_WATER_PROJECT_TABLET ? 90.0f : 140.0f;
  sim->unlock_progress = 0.0f;
  sim->unlock_delay_frames = 10;
  sim->unlock_tail_frames = 60;
  sim->unlocking = false;
  sim->unlock_from_edge = false;
  sim->edge_ratio = 1.0f;
  sim->refraction_ratio = 1.0f;
  sim->edge_offset_ratio = 1.0f;
  sim->specular_ratio = 1.0f;
  sim->reset_requested = false;
  ++sim->reset_serial;
}

LleS6WaterSim *lle_s6_water_sim_create(float width, float height,
                                       int project_kind, int quality,
                                       uint64_t seed) {
  LleS6WaterSim *sim;
  if (project_kind != LLE_S6_WATER_PROJECT_PHONE &&
      project_kind != LLE_S6_WATER_PROJECT_TABLET) {
    return NULL;
  }
  sim = (LleS6WaterSim *)calloc(1u, sizeof(*sim));
  if (sim == NULL) {
    return NULL;
  }
  sim->project_kind = project_kind;
  sim->quality = quality;
  sim->width = width > 0.0f && isfinite(width) ? width : 1.0f;
  sim->height = height > 0.0f && isfinite(height) ? height : 1.0f;
  sim->logical_width = sim->width;
  sim->logical_height = sim->height;
  sim->current_group = LLE_S6_INVALID_GROUP;
  s6_configure_world(sim);
  s6_seed_rng(sim, seed);
  s6_reset_state(sim);
  return sim;
}

void lle_s6_water_sim_destroy(LleS6WaterSim *sim) { free(sim); }

void lle_s6_water_sim_set_surface(LleS6WaterSim *sim, float width,
                                  float height, float logical_width,
                                  float logical_height) {
  size_t group_index;
  float scale_x;
  float scale_y;
  if (sim == NULL || !isfinite(width) || !isfinite(height) ||
      width <= 0.0f || height <= 0.0f) {
    return;
  }
  scale_x = sim->width > 0.0f ? width / sim->width : 1.0f;
  scale_y = sim->height > 0.0f ? height / sim->height : 1.0f;
  for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
       ++group_index) {
    LleS6Group *group = &sim->groups[group_index];
    size_t particle_index;
    group->center_x *= scale_x;
    group->center_y *= scale_y;
    group->previous_center_x *= scale_x;
    group->previous_center_y *= scale_y;
    for (particle_index = 0u; particle_index < group->count;
         ++particle_index) {
      LleS6Particle *particle = &group->particles[particle_index];
      particle->x *= scale_x;
      particle->y *= scale_y;
      particle->velocity_x *= scale_x;
      particle->velocity_y *= scale_y;
      particle->render_offset_x *= scale_x;
      particle->render_offset_y *= scale_y;
      particle->smoothing_radius *= scale_x;
      particle->released_radius *= scale_x;
      particle->transient_force_x *= scale_x;
      particle->transient_force_y *= scale_y;
      particle->affordance_start_x *= scale_x;
      particle->affordance_start_y *= scale_y;
    }
  }
  sim->touch_x *= scale_x;
  sim->touch_y *= scale_y;
  sim->previous_touch_x *= scale_x;
  sim->previous_touch_y *= scale_y;
  sim->touch_delta_x *= scale_x;
  sim->touch_delta_y *= scale_y;
  sim->background_center_x *= scale_x;
  sim->background_center_y *= scale_y;
  sim->width = width;
  sim->height = height;
  sim->logical_width =
      isfinite(logical_width) && logical_width > 0.0f
          ? logical_width
          : width;
  sim->logical_height =
      isfinite(logical_height) && logical_height > 0.0f
          ? logical_height
          : height;
  s6_configure_world(sim);
}

bool lle_s6_water_sim_queue_touch(LleS6WaterSim *sim, int action, float x,
                                  float y, uint64_t event_time_ms) {
  LleS6Event event;
  if ((action != LLE_S6_WATER_TOUCH_DOWN &&
       action != LLE_S6_WATER_TOUCH_UP &&
       action != LLE_S6_WATER_TOUCH_MOVE) ||
      !isfinite(x) || !isfinite(y)) {
    return false;
  }
  memset(&event, 0, sizeof(event));
  event.type = LLE_S6_EVENT_TOUCH;
  event.action = action;
  event.x = x;
  event.y = y;
  event.timestamp = event_time_ms;
  return s6_enqueue(sim, &event);
}

bool lle_s6_water_sim_queue_affordance(LleS6WaterSim *sim, float x,
                                       float y) {
  LleS6Event event;
  if (!isfinite(x) || !isfinite(y)) {
    return false;
  }
  memset(&event, 0, sizeof(event));
  event.type = LLE_S6_EVENT_AFFORDANCE;
  event.x = x;
  event.y = y;
  return s6_enqueue(sim, &event);
}

bool lle_s6_water_sim_queue_unlock(LleS6WaterSim *sim) {
  LleS6Event event;
  memset(&event, 0, sizeof(event));
  event.type = LLE_S6_EVENT_UNLOCK;
  return s6_enqueue(sim, &event);
}

bool lle_s6_water_sim_queue_reset_bg_scale(LleS6WaterSim *sim) {
  LleS6Event event;
  memset(&event, 0, sizeof(event));
  event.type = LLE_S6_EVENT_RESET_BG;
  return s6_enqueue(sim, &event);
}

bool lle_s6_water_sim_queue_tilt(LleS6WaterSim *sim, float mapped_x,
                                 float mapped_y,
                                 uint64_t sample_time_ns) {
  if (sim == NULL || !isfinite(mapped_x) || !isfinite(mapped_y)) {
    return false;
  }
  /*
   * Sensor delivery is immediate global stock state rather than an element of
   * the 100-record touch/key/custom FIFO. Coalesce to the latest sample so an
   * idle WHEN_DIRTY renderer can never starve a later touch or unlock event.
   */
  sim->pending_tilt_x = mapped_x;
  sim->pending_tilt_y = mapped_y;
  sim->pending_tilt_time_ns = sample_time_ns;
  sim->tilt_pending = true;
  return true;
}

void lle_s6_water_sim_request_reset(LleS6WaterSim *sim) {
  if (sim != NULL) {
    sim->reset_requested = true;
  }
}

bool lle_s6_water_sim_consume_deferred_reset(LleS6WaterSim *sim) {
  if (sim == NULL || !sim->reset_requested) {
    return false;
  }
  s6_reset_state(sim);
  return true;
}

void lle_s6_water_sim_tick_native_refresh(LleS6WaterSim *sim,
                                          float frame_scale) {
  size_t group_index;
  const float bounded_scale =
      s6_clamp(frame_scale, 0.25f, LLE_S6_NATIVE_REFRESH_MAX_SCALE);
  if (sim == NULL || !isfinite(frame_scale) || frame_scale <= 0.0f) {
    return;
  }
  s6_drain_events(sim);

  if (!sim->unlocking) {
    const float half_width = sim->width * 0.5f;
    const float half_height = sim->height * 0.5f;
    sim->breath_phase += 0.02f * bounded_scale;
    sim->breath_accumulator += sinf(sim->breath_phase) * bounded_scale;
    if (sim->breath_phase >= 2.0f * LLE_S6_PI) {
      sim->breath_phase = 0.0f;
      sim->breath_accumulator = 0.0f;
    }
    sim->background_center_x +=
        (sim->touch_x - sim->background_center_x) *
            s6_temporal_lerp(0.05f, bounded_scale);
    sim->background_center_y +=
        (sim->touch_y - sim->background_center_y) *
            s6_temporal_lerp(0.05f, bounded_scale);
    (void)half_width;
    (void)half_height;
    if (sim->background_progress < 1.0f) {
      sim->background_progress =
          s6_min(sim->background_progress +
                     0.01325f * bounded_scale,
                 1.0f);
    }
  } else {
    const float progress = sim->unlock_progress;
    if (sim->particle_size < 2000.0f) {
      sim->particle_size *= s6_temporal_multiplier(
          sim->unlock_from_edge ? 1.1f : 1.08f, bounded_scale);
    }
    sim->breath_accumulator *=
        s6_temporal_multiplier(0.99f, bounded_scale);
    sim->unlock_delay_frames -= bounded_scale;
    if (sim->unlock_delay_frames <= 0.0f) {
      sim->refraction_ratio = s6_quint_out(1.0f - progress);
      sim->edge_offset_ratio =
          s6_quint_out(1.0f - powf(progress, 0.125f));
      sim->specular_ratio =
          s6_quint_out(1.0f - powf(progress, 0.05f));
      if (progress < 1.0f) {
        sim->unlock_progress = s6_min(
            progress + 0.05f * bounded_scale, 1.0f);
      } else {
        sim->unlock_tail_frames -= bounded_scale;
      }
    }
    sim->edge_ratio = 1.0f - s6_sine90(sim->unlock_progress);
    if (sim->unlock_progress >= 1.0f &&
        sim->unlock_tail_frames <= 0) {
      sim->unlocking = false;
    }
  }

  s6_advance_domain(sim, true, bounded_scale);
  s6_advance_domain(sim, false, bounded_scale);

  if (sim->touching && sim->current_group != LLE_S6_INVALID_GROUP) {
    LleS6Group *group = &sim->groups[sim->current_group];
    if (group->count < LLE_S6_WATER_TOUCH_PARTICLE_LIMIT) {
      if (group->count > 0u) {
        const LleS6Particle *first = &group->particles[0];
        const float dx = sim->touch_x - first->x;
        const float dy = sim->touch_y - first->y;
        const float threshold =
            sim->world_width * 0.1f * s6_pixels_per_world(sim);
        if (sqrtf(dx * dx + dy * dy) > threshold) {
          group->center_x = first->x;
          group->center_y = first->y;
        } else {
          group->center_x = sim->touch_x;
          group->center_y = sim->touch_y;
        }
      }
      s6_emit_scaled(sim, group, 2.0f * bounded_scale, true);
      group->previous_center_x = group->center_x;
      group->previous_center_y = group->center_y;
      group->center_x = sim->touch_x;
      group->center_y = sim->touch_y;
    }
  }

  for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
       ++group_index) {
    LleS6Group *group = &sim->groups[group_index];
    if (group->phase == LLE_S6_GROUP_AFFORDANCE &&
        group->count < LLE_S6_WATER_AFFORDANCE_PARTICLE_LIMIT) {
      s6_emit_scaled(sim, group, 2.0f * bounded_scale, false);
    }
  }

  for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
       ++group_index) {
    LleS6Group *group = &sim->groups[group_index];
    size_t particle_index;
    bool any = false;
    if (group->phase == LLE_S6_GROUP_FREE) {
      continue;
    }
    s6_advance_group(sim, group, bounded_scale);
    for (particle_index = 0u; particle_index < group->count;
         ++particle_index) {
      LleS6Particle *particle = &group->particles[particle_index];
      if (particle->active) {
        any = true;
      }
    }
    if (!any) {
      /* At 90/120/144 Hz the fractional emitter can legitimately need one
       * display frame before its first whole particle. Keep that live group
       * through the fractional credit rather than cancelling touch/hint. */
      if (group->emission_credit > 0.0f &&
          ((group->phase == LLE_S6_GROUP_TOUCH && sim->touching &&
            sim->current_group == group_index) ||
           group->phase == LLE_S6_GROUP_AFFORDANCE)) {
        continue;
      }
      if (sim->current_group == group_index) {
        sim->current_group = LLE_S6_INVALID_GROUP;
        sim->touching = false;
      }
      s6_clear_group(group);
    }
  }
  /*
   * updateApp writes the next smooth wall acceleration and the render-only
   * hard-edge displacement after both complete engine updates.
   */
  s6_apply_wall_fields(sim, bounded_scale);
  ++sim->frame_index;
}

void lle_s6_water_sim_tick(LleS6WaterSim *sim) {
  /* Fixed 60 Hz entry point retained for the production stock variant. */
  lle_s6_water_sim_tick_native_refresh(sim, 1.0f);
}

bool lle_s6_water_sim_is_idle(const LleS6WaterSim *sim) {
  if (sim == NULL) {
    return true;
  }
  return !sim->touching && !sim->unlocking &&
         sim->event_count == 0u &&
         lle_s6_water_sim_particle_count(sim) == 0u;
}

size_t lle_s6_water_sim_particle_count(const LleS6WaterSim *sim) {
  size_t group_index;
  size_t count = 0u;
  if (sim == NULL) {
    return 0u;
  }
  for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
       ++group_index) {
    const LleS6Group *group = &sim->groups[group_index];
    size_t particle_index;
    for (particle_index = 0u; particle_index < group->count;
         ++particle_index) {
      if (group->particles[particle_index].active) {
        ++count;
      }
    }
  }
  return count;
}

size_t lle_s6_water_sim_export_density_particles(
    const LleS6WaterSim *sim, LleS6WaterDensityParticle *out_particles,
    size_t capacity) {
  size_t group_index;
  size_t output_index = 0u;
  size_t required;
  if (sim == NULL) {
    return 0u;
  }
  required = lle_s6_water_sim_particle_count(sim);
  if (out_particles == NULL || capacity < required) {
    return required;
  }
  for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
       ++group_index) {
    const LleS6Group *group = &sim->groups[group_index];
    size_t particle_index;
    for (particle_index = 0u; particle_index < group->count;
         ++particle_index) {
      const LleS6Particle *particle = &group->particles[particle_index];
      LleS6WaterDensityParticle *output;
      float horizontal_scale;
      float bounded_x;
      float half_width;
      float base_size;
      if (!particle->active) {
        continue;
      }
      output = &out_particles[output_index++];
      bounded_x = s6_clamp(particle->x, 0.0f, sim->width);
      half_width = sim->width * 0.5f;
      horizontal_scale =
          half_width > 0.0f
              ? 1.0f +
                    0.75f * fabsf(half_width - bounded_x) / half_width
              : 1.0f;
      base_size =
          group->phase == LLE_S6_GROUP_AFFORDANCE
              ? (sim->project_kind == LLE_S6_WATER_PROJECT_TABLET
                     ? 90.0f
                     : 140.0f)
              : sim->particle_size;
      output->center_x_px = particle->x + particle->render_offset_x;
      output->center_y_px = particle->y + particle->render_offset_y;
      output->velocity_x_px_per_second = particle->velocity_x;
      output->velocity_y_px_per_second = particle->velocity_y;
      output->diameter_px =
          horizontal_scale * base_size *
          (s6_sine80(particle->phase) + particle->extra_scale) *
          sim->width / (450.0f * sim->world_width);
      output->phase = particle->phase;
      output->flags =
          (group->phase == LLE_S6_GROUP_AFFORDANCE
               ? LLE_S6_WATER_PARTICLE_AFFORDANCE
               : 0u) |
          (group->phase == LLE_S6_GROUP_RELEASE_SHORT ||
                   group->phase == LLE_S6_GROUP_RELEASE_EDGE
               ? LLE_S6_WATER_PARTICLE_RELEASED
               : 0u);
    }
  }
  return required;
}

void lle_s6_water_sim_get_render_state(const LleS6WaterSim *sim,
                                       LleS6WaterRenderState *out_state) {
  float uv_scale;
  float reset_offset;
  float half_width;
  float half_height;
  if (sim == NULL || out_state == NULL) {
    return;
  }
  memset(out_state, 0, sizeof(*out_state));
  uv_scale =
      0.98f + 0.00025f * (sim->breath_accumulator - 50.0f);
  reset_offset =
      (1.0f - s6_sine33(sim->background_progress)) * 0.05f;
  half_width = sim->width * 0.5f;
  half_height = sim->height * 0.5f;
  out_state->surface_width = sim->width;
  out_state->surface_height = sim->height;
  out_state->logical_width = sim->logical_width;
  out_state->logical_height = sim->logical_height;
  out_state->world_width = sim->world_width;
  out_state->world_height = sim->world_height;
  out_state->project_kind = sim->project_kind;
  out_state->quality = sim->quality;
  out_state->background_uv_scale =
      s6_clamp(uv_scale - reset_offset, 0.5f, 1.0f);
  out_state->background_uv_offset_x =
      half_width > 0.0f
          ? ((sim->background_center_x - half_width) / half_width) *
                (1.0f - uv_scale)
          : 0.0f;
  out_state->background_uv_offset_y =
      half_height > 0.0f
          ? ((sim->background_center_y - half_height) / half_height) *
                (1.0f - uv_scale)
          : 0.0f;
  out_state->background_touch_center_x = sim->background_center_x;
  out_state->background_touch_center_y = sim->background_center_y;
  out_state->breath_phase = sim->breath_phase;
  out_state->breath_accumulator = sim->breath_accumulator;
  out_state->restore_ratio =
      sim->height *
      (sim->project_kind == LLE_S6_WATER_PROJECT_TABLET
           ? 0.0009765625f
           : 0.00078125f);
  out_state->edge_ratio = sim->edge_ratio;
  out_state->refraction_ratio = sim->refraction_ratio;
  out_state->edge_offset_ratio = sim->edge_offset_ratio;
  out_state->specular_ratio = sim->specular_ratio;
  out_state->bottom_offset = 0.0f;
  out_state->density_threshold = 0.5f;
  out_state->edge_offset = 0.075f;
  out_state->shadow_offset = 0.15f;
  out_state->shadow_range = 12.0f;
  out_state->refraction_eta = 0.7500187504687617f;
  out_state->refraction_amplitude = 0.075f;
  out_state->density_particle_count =
      lle_s6_water_sim_particle_count(sim);
  out_state->frame_index = sim->frame_index;
  out_state->reset_serial = sim->reset_serial;
  out_state->unlocking = sim->unlocking;
}

#ifdef LLE_S6_TEST_API
bool lle_s6_water_sim_test_particle_state(
    const LleS6WaterSim *sim, size_t active_index,
    LleS6WaterTestParticleState *out_state) {
  size_t group_index;
  size_t current = 0u;
  if (sim == NULL || out_state == NULL) {
    return false;
  }
  for (group_index = 0u; group_index < LLE_S6_GROUP_CAPACITY;
       ++group_index) {
    const LleS6Group *group = &sim->groups[group_index];
    size_t particle_index;
    for (particle_index = 0u; particle_index < group->count;
         ++particle_index) {
      const LleS6Particle *particle = &group->particles[particle_index];
      if (!particle->active) {
        continue;
      }
      if (current++ != active_index) {
        continue;
      }
      out_state->x = particle->x;
      out_state->y = particle->y;
      out_state->velocity_x = particle->velocity_x;
      out_state->velocity_y = particle->velocity_y;
      out_state->render_offset_x = particle->render_offset_x;
      out_state->render_offset_y = particle->render_offset_y;
      out_state->staged_acceleration_x =
          particle->staged_acceleration_x;
      out_state->staged_acceleration_y =
          particle->staged_acceleration_y;
      out_state->transient_force_x = particle->transient_force_x;
      out_state->transient_force_y = particle->transient_force_y;
      out_state->phase = particle->phase;
      out_state->smoothing_radius = particle->smoothing_radius;
      out_state->rest_density = particle->rest_density;
      out_state->pressure = particle->pressure;
      out_state->near_pressure = particle->near_pressure;
      out_state->unlock_progress = sim->unlock_progress;
      out_state->unlock_delay_ticks = sim->unlock_delay_frames;
      out_state->flags =
          (group->phase == LLE_S6_GROUP_AFFORDANCE
               ? LLE_S6_WATER_PARTICLE_AFFORDANCE
               : 0u) |
          (group->phase == LLE_S6_GROUP_RELEASE_SHORT ||
                   group->phase == LLE_S6_GROUP_RELEASE_EDGE
               ? LLE_S6_WATER_PARTICLE_RELEASED
               : 0u);
      return true;
    }
  }
  return false;
}

static uint64_t s6_hash_bytes(uint64_t hash, const void *data, size_t size) {
  const uint8_t *bytes = (const uint8_t *)data;
  size_t index;
  for (index = 0u; index < size; ++index) {
    hash ^= bytes[index];
    hash *= UINT64_C(1099511628211);
  }
  return hash;
}

uint64_t lle_s6_water_sim_test_state_hash(const LleS6WaterSim *sim) {
  uint64_t hash = UINT64_C(1469598103934665603);
  LleS6WaterRenderState state;
  LleS6WaterDensityParticle particles
      [LLE_S6_WATER_PRIMARY_PARTICLE_LIMIT +
       LLE_S6_WATER_AFFORDANCE_PARTICLE_LIMIT + 1u];
  size_t count;
  if (sim == NULL) {
    return 0u;
  }
  lle_s6_water_sim_get_render_state(sim, &state);
  count = lle_s6_water_sim_export_density_particles(
      sim, particles, sizeof(particles) / sizeof(particles[0]));
  hash = s6_hash_bytes(hash, &state, sizeof(state));
  if (count <= sizeof(particles) / sizeof(particles[0])) {
    hash = s6_hash_bytes(hash, particles, count * sizeof(particles[0]));
  }
  hash = s6_hash_bytes(hash, sim->rng_state, sizeof(sim->rng_state));
  hash = s6_hash_bytes(hash, &sim->rng_front, sizeof(sim->rng_front));
  hash = s6_hash_bytes(hash, &sim->rng_rear, sizeof(sim->rng_rear));
  hash = s6_hash_bytes(hash, &sim->event_count, sizeof(sim->event_count));
  return hash;
}
#endif
