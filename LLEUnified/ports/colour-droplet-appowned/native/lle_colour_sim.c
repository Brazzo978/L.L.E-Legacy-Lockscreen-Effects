#include "lle_colour_sim.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#define LLE_COLOUR_PI 3.14159265358979323846f
#define LLE_COLOUR_VENDOR_DEFAULT_SEED UINT32_C(1)
#define LLE_COLOUR_VENDOR_RNG_DEGREE 31u
#define LLE_COLOUR_VENDOR_RNG_SEPARATION 3u
#define LLE_COLOUR_INVALID_GROUP UINT32_MAX

typedef enum LleColourGroupPhase {
  LLE_COLOUR_GROUP_FREE = 0,
  LLE_COLOUR_GROUP_TOUCH = 1,
  LLE_COLOUR_GROUP_RELEASED = 2,
  LLE_COLOUR_GROUP_AFFORDANCE = 3,
  LLE_COLOUR_GROUP_SUBPARTICLE = 4
} LleColourGroupPhase;

typedef struct LleColourParticle {
  float x;
  float y;
  float velocity_x;
  float velocity_y;
  float render_offset_x;
  float render_offset_y;
  float color_x;
  float color_y;
  float scale;
  float alpha;
  float age;
  float smoothing_radius;
  float target_scale;
  float fade_rate;
  float rest_density;
  float pressure;
  float near_pressure;
  float viscosity;
  float staged_acceleration_x;
  float staged_acceleration_y;
  float transient_force_x;
  float transient_force_y;
  float released_scale;
  float released_radius;
  float affordance_start_x;
  float affordance_start_y;
  bool active;
  bool special;
} LleColourParticle;

typedef struct LleColourGroup {
  LleColourParticle particles[LLE_COLOUR_LIVE_GROUP_PARTICLES];
  size_t count;
  float center_x;
  float center_y;
  float previous_center_x;
  float previous_center_y;
  float age;
  uint64_t serial;
  LleColourGroupPhase phase;
} LleColourGroup;

struct LleColourSim {
  LleColourGroup groups[LLE_COLOUR_GROUP_CAPACITY];
  float width;
  float height;
  float logical_width;
  float logical_height;
  float pixel_scale;
  float touch_x;
  float touch_y;
  float previous_touch_x;
  float previous_touch_y;
  float touch_velocity_x;
  float touch_velocity_y;
  float direction_move_delta_x;
  float direction_move_delta_y;
  bool direction_target_pending;
  float background_center_x;
  float background_center_y;
  float sensor_acceleration_x;
  float sensor_acceleration_y;
  uint64_t touch_time_ms;
  uint64_t next_group_serial;
  uint32_t current_group;
  uint32_t rng_state[LLE_COLOUR_VENDOR_RNG_DEGREE];
  uint8_t rng_front;
  uint8_t rng_rear;
  int project_kind;
  int unlock_delay_frames;
  int unlock_tail_frames;
  float unlock_progress;
  float particle_size_control;
  float breath_phase;
  float breath_accumulator;
  bool touching;
  bool unlocking;
  bool unlock_finished;
  LleColourDrawParams draw_params;
};

/*
 * Stock DAT_0019fa7c is library-global rather than SPColourDropletApp state.
 * Its satellite-emission phase therefore survives app destruction/recreation
 * for the lifetime of the loaded native library.
 */
static uint32_t g_subparticle_counter;

static float clampf(float value, float minimum, float maximum) {
  if (value < minimum) {
    return minimum;
  }
  if (value > maximum) {
    return maximum;
  }
  return value;
}

static float minf(float a, float b) { return a < b ? a : b; }

static float colour_world_width(const LleColourSim *sim) {
  const float aspect = sim->width > 0.0f ? sim->height / sim->width : 1.0f;
  return aspect > 1.0f ? 0.45f : 0.6f;
}

static float pixels_per_world_unit(const LleColourSim *sim) {
  return sim->width / colour_world_width(sim);
}

static float stock_cell_width_px(const LleColourSim *sim) {
  const float aspect = sim->width > 0.0f ? sim->height / sim->width : 1.0f;
  return sim->width / (aspect > 1.0f ? 18.0f : 24.0f);
}

static uint32_t rng_u32(LleColourSim *sim) {
  const uint32_t sum =
      sim->rng_state[sim->rng_front] + sim->rng_state[sim->rng_rear];
  sim->rng_state[sim->rng_front] = sum;
  if (++sim->rng_front >= LLE_COLOUR_VENDOR_RNG_DEGREE) {
    sim->rng_front = 0;
  }
  if (++sim->rng_rear >= LLE_COLOUR_VENDOR_RNG_DEGREE) {
    sim->rng_rear = 0;
  }
  return (sum >> 1u) & UINT32_C(0x7fffffff);
}

static float rng_unit(LleColourSim *sim) {
  return (float)rng_u32(sim) * (1.0f / 2147483648.0f);
}

static float rng_range(LleColourSim *sim, float minimum, float maximum) {
  return minimum + (maximum - minimum) * rng_unit(sim);
}

static uint8_t rng_vendor_byte(LleColourSim *sim) {
  const uint8_t value = (uint8_t)(rng_u32(sim) & UINT32_C(0xff));
  /*
   * Samsung's byte pack uses `b + b/255 & 255`, which maps the lone 0xff
   * endpoint back to zero.
   */
  return value == UINT8_MAX ? 0u : value;
}

static uint32_t rng_vendor_word(LleColourSim *sim) {
  uint32_t value = 0u;
  size_t byte_index;
  for (byte_index = 0; byte_index < 4u; ++byte_index) {
    value = (value << 8u) | (uint32_t)rng_vendor_byte(sim);
  }
  return value;
}

static void rng_vendor_offset_pair(LleColourSim *sim, uint32_t *out_x,
                                   uint32_t *out_y) {
  uint32_t x = 0u;
  uint32_t y = 0u;
  size_t byte_index;
  /*
   * addSubParticles interleaves eight rand() calls. Odd calls form Y and
   * even calls form X, most-significant byte first.
   */
  for (byte_index = 0; byte_index < 4u; ++byte_index) {
    y = (y << 8u) | (uint32_t)rng_vendor_byte(sim);
    x = (x << 8u) | (uint32_t)rng_vendor_byte(sim);
  }
  *out_x = x;
  *out_y = y;
}

static void seed_vendor_rng(LleColourSim *sim, uint64_t requested_seed) {
  size_t index;
  uint32_t seed = (uint32_t)requested_seed;
  if (seed == 0u) {
    seed = LLE_COLOUR_VENDOR_DEFAULT_SEED;
  }
  sim->rng_state[0] = seed;
  for (index = 1; index < LLE_COLOUR_VENDOR_RNG_DEGREE; ++index) {
    const int32_t previous = (int32_t)sim->rng_state[index - 1u];
    const int32_t high = previous / 127773;
    const int32_t low = previous % 127773;
    int32_t next = 16807 * low - 2836 * high;
    if (next <= 0) {
      next += INT32_MAX;
    }
    sim->rng_state[index] = (uint32_t)next;
  }
  sim->rng_front = LLE_COLOUR_VENDOR_RNG_SEPARATION;
  sim->rng_rear = 0;
  for (index = 0; index < 10u * LLE_COLOUR_VENDOR_RNG_DEGREE; ++index) {
    (void)rng_u32(sim);
  }
}

static float quadratic_segment(float start, float control, float end,
                               float progress) {
  return start + progress * (progress * (end - start) +
                             (1.0f - progress) * 2.0f * (control - start));
}

static float sine_in_out33(float value) {
  static const float points[2][3] = {
      {0.0f, 0.05f, 0.495f},
      {0.495f, 0.94f, 1.0f},
  };
  const float bounded = clampf(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 2.0f);
  float progress;
  if (segment > 1) {
    segment = 1;
  }
  progress = (bounded - (float)segment * 0.5f) * 2.0f;
  return quadratic_segment(points[segment][0], points[segment][1],
                           points[segment][2], progress);
}

static float sine_in_out80(float value) {
  static const float points[5][3] = {
      {0.0f, 0.0f, 0.195f},     {0.195f, 0.48f, 0.645f},
      {0.645f, 0.835f, 0.885f}, {0.885f, 0.955f, 0.978f},
      {0.978f, 0.9999f, 1.0f},
  };
  const float bounded = clampf(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 5.0f);
  float progress;
  if (segment > 4) {
    segment = 4;
  }
  progress = (bounded - (float)segment * 0.2f) * 5.0f;
  return quadratic_segment(points[segment][0], points[segment][1],
                           points[segment][2], progress);
}

static float sine_in_out70(float value) {
  static const float points[3][3] = {
      {0.0f, 0.01f, 0.45f},
      {0.45f, 0.8f, 0.908f},
      {0.908f, 0.9999f, 1.0f},
  };
  const float bounded = clampf(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 3.0f);
  float progress;
  if (segment > 2) {
    segment = 2;
  }
  progress = (bounded - (float)segment * (1.0f / 3.0f)) * 3.0f;
  return quadratic_segment(points[segment][0], points[segment][1],
                           points[segment][2], progress);
}

static float sine_in_out90(float value) {
  /*
   * SineInOut90::getInterpolation @ 0x53df0.  Preserve the stock table
   * literally, including the small discontinuities between segments 1/2 and
   * 2/3; these are present in the vendor ELF rather than decompiler noise.
   */
  static const float points[5][3] = {
      {0.0f, 0.0f, 0.247f},   {0.247f, 0.48f, 0.72f},
      {0.7f, 0.835f, 0.905f}, {0.91f, 0.955f, 0.978f},
      {0.978f, 0.9999f, 1.0f},
  };
  const float bounded = clampf(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 5.0f);
  float progress;
  if (segment > 4) {
    segment = 4;
  }
  progress = (bounded - (float)segment * 0.2f) * 5.0f;
  return quadratic_segment(points[segment][0], points[segment][1],
                           points[segment][2], progress);
}

static float quint_out(float value) {
  static const float points[2][3] = {
      {0.04f, 0.718f, 0.84f},
      {0.845f, 0.998f, 1.0f},
  };
  const float bounded = clampf(value, 0.0f, 1.0f);
  int segment = (int)(bounded * 2.0f);
  float progress;
  if (segment > 1) {
    segment = 1;
  }
  progress = (bounded - (float)segment * 0.5f) * 2.0f;
  return quadratic_segment(points[segment][0], points[segment][1],
                           points[segment][2], progress);
}

static float surface_restore_ratio(const LleColourSim *sim) {
  const float factor = sim->project_kind == 1 ? 0.0009765625f : 0.00078125f;
  return sim->height > 0.0f ? sim->height * factor : 0.0111111114f;
}

static void reset_draw_params(LleColourSim *sim) {
  lle_colour_gles_default_params(&sim->draw_params);
  /*
   * drawApp evaluates SineInOut90(unlockProgress), then
   * SPDrawColourDroplet::setEdgeRatio stores its complement as the raw shader
   * uniform. Reset progress is zero, so the ordinary touch/hint state starts
   * with uEdgeRatio == 1.
   */
  sim->draw_params.edge_ratio = 1.0f - sine_in_out90(0.0f);
  sim->draw_params.restore_ratio = surface_restore_ratio(sim);
  sim->draw_params.refraction_ratio = 1.0f;
  sim->draw_params.tab_scale = 0.9675f;
  sim->draw_params.tab_offset_x = 0.0f;
  sim->draw_params.tab_offset_y = 0.0f;
  sim->draw_params.edge_offset_ratio = 1.0f;
  sim->draw_params.inner_shadow_width = 0.6f;
  sim->draw_params.color_saturation = 1.3f;
  sim->draw_params.color_brightness = 1.3f;
  sim->draw_params.color_min_value = 0.15f;
  sim->draw_params.shadow_enabled = true;
}

static size_t active_primary_count(const LleColourSim *sim) {
  size_t group_index;
  size_t count = 0;
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    const LleColourGroup *group = &sim->groups[group_index];
    if (group->phase == LLE_COLOUR_GROUP_TOUCH ||
        group->phase == LLE_COLOUR_GROUP_RELEASED) {
      count += group->count;
    }
  }
  return count;
}

static void clear_group(LleColourGroup *group) {
  memset(group, 0, sizeof(*group));
  group->phase = LLE_COLOUR_GROUP_FREE;
}

static uint32_t allocate_group(LleColourSim *sim, LleColourGroupPhase phase) {
  uint32_t index;
  uint32_t oldest_index = LLE_COLOUR_INVALID_GROUP;
  uint64_t oldest_serial = UINT64_MAX;

  for (index = 0; index < LLE_COLOUR_GROUP_CAPACITY; ++index) {
    if (sim->groups[index].phase == LLE_COLOUR_GROUP_FREE) {
      oldest_index = index;
      break;
    }
    if (sim->groups[index].phase != LLE_COLOUR_GROUP_TOUCH &&
        sim->groups[index].serial < oldest_serial) {
      oldest_serial = sim->groups[index].serial;
      oldest_index = index;
    }
  }
  if (oldest_index == LLE_COLOUR_INVALID_GROUP) {
    return oldest_index;
  }
  clear_group(&sim->groups[oldest_index]);
  sim->groups[oldest_index].phase = phase;
  sim->groups[oldest_index].serial = ++sim->next_group_serial;
  return oldest_index;
}

static void release_current_group(LleColourSim *sim) {
  LleColourGroup *group;
  size_t index;
  if (sim->current_group == LLE_COLOUR_INVALID_GROUP) {
    return;
  }
  group = &sim->groups[sim->current_group];
  if (group->phase == LLE_COLOUR_GROUP_TOUCH) {
    group->phase = LLE_COLOUR_GROUP_RELEASED;
    group->age = 0.0f;
    for (index = 0; index < group->count; ++index) {
      group->particles[index].released_scale = group->particles[index].scale;
      group->particles[index].released_radius =
          group->particles[index].smoothing_radius;
    }
  }
  sim->current_group = LLE_COLOUR_INVALID_GROUP;
}

static void initialize_particle(LleColourSim *sim, LleColourGroup *group,
                                LleColourParticle *particle, bool special) {
  const float cell_width = stock_cell_width_px(sim);
  const float jitter_radius = cell_width * 0.1f;
  const float angle = rng_range(sim, 0.0f, 2.0f * LLE_COLOUR_PI);
  const float radius = sqrtf(rng_unit(sim)) * jitter_radius;

  memset(particle, 0, sizeof(*particle));
  particle->x = group->center_x + cosf(angle) * radius;
  particle->y = group->center_y + sinf(angle) * radius;
  particle->color_x = clampf(particle->x, 0.0f, sim->width);
  particle->color_y = clampf(particle->y, 0.0f, sim->height);
  particle->scale = 0.1f;
  particle->alpha = 1.0f;
  particle->smoothing_radius = cell_width * 0.1f;
  particle->target_scale = 1.0f;
  particle->fade_rate = 1.0f;
  particle->rest_density = 12.0f;
  particle->pressure = 0.0f;
  particle->near_pressure = 0.0f;
  particle->viscosity = 3.0f;
  particle->active = true;
  particle->special = special;
}

static void emit_particles(LleColourSim *sim, LleColourGroup *group,
                           size_t requested, bool special,
                           bool enforce_primary_limit) {
  size_t count = requested;
  size_t primary_count = active_primary_count(sim);
  if (enforce_primary_limit) {
    if (primary_count >= LLE_COLOUR_PRIMARY_PARTICLE_LIMIT) {
      return;
    }
    if (count > LLE_COLOUR_PRIMARY_PARTICLE_LIMIT - primary_count) {
      count = LLE_COLOUR_PRIMARY_PARTICLE_LIMIT - primary_count;
    }
  }
  if (count > LLE_COLOUR_LIVE_GROUP_PARTICLES - group->count) {
    count = LLE_COLOUR_LIVE_GROUP_PARTICLES - group->count;
  }
  while (count-- > 0u) {
    initialize_particle(sim, group, &group->particles[group->count], special);
    ++group->count;
  }
}

static void emit_subparticle(LleColourSim *sim) {
  const uint32_t group_index =
      allocate_group(sim, LLE_COLOUR_GROUP_SUBPARTICLE);
  LleColourGroup *group;
  LleColourParticle *particle;
  float offset_x;
  float offset_y;
  const float radius = sim->width * 0.005f;
  const float touch_delta_scale =
      0.0001f * pixels_per_world_unit(sim);
  if (group_index == LLE_COLOUR_INVALID_GROUP) {
    return;
  }
  group = &sim->groups[group_index];
  particle = &group->particles[0];
  do {
    uint32_t random_x;
    uint32_t random_y;
    rng_vendor_offset_pair(sim, &random_x, &random_y);
    offset_x =
        (float)random_x * radius * 4.6566128730773926e-10f - radius;
    offset_y =
        (float)random_y * radius * 4.6566128730773926e-10f - radius;
  } while (sqrtf(offset_x * offset_x + offset_y * offset_y) > radius);

  memset(particle, 0, sizeof(*particle));
  particle->x = sim->touch_x;
  particle->y = sim->touch_y;
  particle->velocity_x =
      -sim->touch_velocity_x * touch_delta_scale + offset_x;
  particle->velocity_y =
      -sim->touch_velocity_y * touch_delta_scale + offset_y;
  particle->color_x = particle->x;
  particle->color_y = particle->y;
  particle->scale = 0.0f;
  particle->alpha = 1.0f;
  particle->target_scale =
      0.5f + (float)rng_vendor_word(sim) * 1.1641532182693481e-10f;
  particle->fade_rate =
      0.5f + (float)rng_vendor_word(sim) * 3.4924597938073015e-11f;
  particle->active = true;
  particle->special = false;
  group->count = 1u;
  group->center_x = particle->x;
  group->center_y = particle->y;
}

/*
 * SPSmoothedParticle2D<float>::step at 0x46304.  Stock calls the complete
 * 1/60 s engine update twice per rendered frame; it does not split one 1/60
 * step in half.
 */
static bool group_in_sph_domain(const LleColourGroup *group, bool primary) {
  if (primary) {
    return group->phase == LLE_COLOUR_GROUP_TOUCH ||
           group->phase == LLE_COLOUR_GROUP_RELEASED;
  }
  return group->phase == LLE_COLOUR_GROUP_AFFORDANCE;
}

/*
 * The oracle owns one primary SPH engine containing every live type-0
 * particle plus all type-0 particles referenced by released state-1 records.
 * Lifecycle records do not partition neighbour interaction.  Affordance uses
 * the separate secondary engine.
 */
static void apply_sph_forces(LleColourSim *sim, bool primary,
                             float time_step) {
  size_t first_group;
  const float pixels_per_world = pixels_per_world_unit(sim);

  for (first_group = 0; first_group < LLE_COLOUR_GROUP_CAPACITY;
       ++first_group) {
    LleColourGroup *a_group = &sim->groups[first_group];
    size_t first;
    if (!group_in_sph_domain(a_group, primary)) {
      continue;
    }
    for (first = 0; first < a_group->count; ++first) {
      LleColourParticle *a = &a_group->particles[first];
      size_t second_group;
      float density = 0.0f;
      float near_density = 0.0f;
      float self_impulse_x = 0.0f;
      float self_impulse_y = 0.0f;
      if (!a->active || a->smoothing_radius <= 0.0f) {
        continue;
      }

      for (second_group = 0;
           second_group < LLE_COLOUR_GROUP_CAPACITY; ++second_group) {
        const LleColourGroup *b_group = &sim->groups[second_group];
        size_t second;
        if (!group_in_sph_domain(b_group, primary)) {
          continue;
        }
        for (second = 0; second < b_group->count; ++second) {
          const LleColourParticle *b = &b_group->particles[second];
          const float dx = (b->x - a->x) / pixels_per_world;
          const float dy = (b->y - a->y) / pixels_per_world;
          const float distance = sqrtf(dx * dx + dy * dy);
          const float smoothing_radius =
              a->smoothing_radius / pixels_per_world;
          float q;
          if (b == a || !b->active || distance <= 1.0e-7f ||
              distance >= smoothing_radius) {
            continue;
          }
          q = 1.0f - distance / smoothing_radius;
          near_density += q * q;
          density += q * q * q;
        }
      }

      for (second_group = 0;
           second_group < LLE_COLOUR_GROUP_CAPACITY; ++second_group) {
        LleColourGroup *b_group = &sim->groups[second_group];
        size_t second;
        if (!group_in_sph_domain(b_group, primary)) {
          continue;
        }
        for (second = 0; second < b_group->count; ++second) {
          LleColourParticle *b = &b_group->particles[second];
          float dx;
          float dy;
          float distance;
          float smoothing_radius;
          float q;
          float nx;
          float ny;
          float relative_radial_velocity;
          float impulse;
          float impulse_x;
          float impulse_y;
          if (b == a || !b->active) {
            continue;
          }
          dx = (b->x - a->x) / pixels_per_world;
          dy = (b->y - a->y) / pixels_per_world;
          distance = sqrtf(dx * dx + dy * dy);
          smoothing_radius = a->smoothing_radius / pixels_per_world;
          if (distance <= 1.0e-7f || distance >= smoothing_radius) {
            continue;
          }
          q = 1.0f - distance / smoothing_radius;
          nx = dx / distance;
          ny = dy / distance;
          relative_radial_velocity =
              nx * ((a->velocity_x - b->velocity_x) / pixels_per_world) +
              ny * ((a->velocity_y - b->velocity_y) / pixels_per_world);
          impulse = q * time_step * 0.5f *
                    ((near_density - a->rest_density) * a->pressure +
                     density * a->near_pressure * q +
                     a->viscosity * relative_radial_velocity);
          impulse_x = nx * impulse * pixels_per_world;
          impulse_y = ny * impulse * pixels_per_world;
          self_impulse_x -= impulse_x;
          self_impulse_y -= impulse_y;
          b->velocity_x += impulse_x;
          b->velocity_y += impulse_y;
          b->x += time_step * impulse_x;
          b->y += time_step * impulse_y;
        }
      }
      a->velocity_x += self_impulse_x;
      a->velocity_y += self_impulse_y;
      a->x += time_step * self_impulse_x;
      a->y += time_step * self_impulse_y;
    }
  }
}

static void update_particle_growth(const LleColourSim *sim,
                                   LleColourParticle *particle,
                                   float increment) {
  const float target_radius = stock_cell_width_px(sim) * 3.7f;
  const float target_density = sim->project_kind == 1 ? 6.0f : 4.0f;
  float interpolation;
  if (particle->scale >= 1.0f) {
    return;
  }
  particle->scale = minf(particle->scale + increment, 1.0f);
  interpolation = sine_in_out33(particle->scale);
  particle->smoothing_radius +=
      (target_radius - particle->smoothing_radius) * interpolation;
  particle->rest_density +=
      (target_density - particle->rest_density) * interpolation;
  particle->pressure += (0.4f - particle->pressure) * interpolation;
  particle->near_pressure += (0.4f - particle->near_pressure) * interpolation;
}

static void constrain_particle(const LleColourSim *sim,
                               LleColourParticle *particle) {
  if (particle->x < 0.0f) {
    const float overshoot = -particle->x;
    particle->velocity_x = -particle->velocity_x * 0.15f;
    particle->x = 2.0f * overshoot;
  }
  /*
   * checkForWall @ 0x14ad9c uses two independent tests.  Preserve that
   * ordering for extreme overshoots which can cross the opposite wall after
   * the first reflection.
   */
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

/*
 * Touch-released particles keep an auxiliary render displacement at stock
 * offsets +0xb8/+0xbc.  It is intentionally not a physics velocity: the draw
 * helper renders at position + displacement while the SPH centre continues
 * its inertial/collision path.
 */
static void update_released_edge_offset(const LleColourSim *sim,
                                        LleColourParticle *particle) {
  const float margin = sim->width * 0.15f;
  const float edge_kick =
      0.35f * pixels_per_world_unit(sim) * (1.0f / 240.0f);

  if (particle->x < margin || particle->x > sim->width - margin) {
    const float direction =
        particle->x < sim->width * 0.5f ? -1.0f : 1.0f;
    particle->render_offset_x =
        particle->render_offset_x * 1.05f + direction * edge_kick;
  }
  if (particle->y < margin || particle->y > sim->height - margin) {
    const float direction =
        particle->y < sim->height * 0.5f ? -1.0f : 1.0f;
    particle->render_offset_y =
        particle->render_offset_y * 1.05f + direction * edge_kick;
  }
}

static void advance_subparticle(LleColourSim *sim, LleColourGroup *group) {
  LleColourParticle *particle = &group->particles[0];
  const float time_step = 1.0f / 60.0f;
  const float pixels_per_world = pixels_per_world_unit(sim);
  if (!particle->active) {
    return;
  }
  particle->x += particle->velocity_x +
                 sim->sensor_acceleration_x * pixels_per_world * time_step;
  particle->y += particle->velocity_y +
                 sim->sensor_acceleration_y * pixels_per_world * time_step;
  particle->age += 1.0f;
  if (particle->age < 20.0f) {
    particle->scale += 0.19f * (particle->target_scale - particle->scale);
  } else {
    particle->scale -= 0.016f * particle->fade_rate;
  }
  if (particle->scale < 0.0f) {
    particle->active = false;
  }
}

/*
 * updateSPH runs both complete engine updates before it emits the next two
 * live particles.  Keep the engine phase separate so particles born in the
 * current display tick do not get integrated one frame too early.
 */
static void apply_sph_domain_acceleration(LleColourSim *sim, bool primary,
                                          float stock_time_step) {
  size_t group_index;
  const float pixels_per_world = pixels_per_world_unit(sim);
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    LleColourGroup *group = &sim->groups[group_index];
    size_t index;
    if (!group_in_sph_domain(group, primary)) {
      continue;
    }
    for (index = 0; index < group->count; ++index) {
      LleColourParticle *particle = &group->particles[index];
      if (!particle->active) {
        continue;
      }
      particle->velocity_x +=
          particle->staged_acceleration_x * pixels_per_world * stock_time_step;
      particle->velocity_y +=
          particle->staged_acceleration_y * pixels_per_world * stock_time_step;
    }
  }
}

static void integrate_sph_domain(LleColourSim *sim, bool primary,
                                 float stock_time_step) {
  size_t group_index;
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    LleColourGroup *group = &sim->groups[group_index];
    size_t index;
    if (!group_in_sph_domain(group, primary)) {
      continue;
    }
    for (index = 0; index < group->count; ++index) {
      LleColourParticle *particle = &group->particles[index];
      if (!particle->active) {
        continue;
      }
      particle->velocity_x += particle->transient_force_x;
      particle->velocity_y += particle->transient_force_y;
      particle->transient_force_x = 0.0f;
      particle->transient_force_y = 0.0f;
      particle->x += particle->velocity_x * stock_time_step;
      particle->y += particle->velocity_y * stock_time_step;
      constrain_particle(sim, particle);
    }
  }
}

static void advance_sph_domain(LleColourSim *sim, bool primary) {
  size_t substep;
  const float stock_time_step = 1.0f / 60.0f;
  size_t group_index;

  if (primary) {
    for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
         ++group_index) {
      LleColourGroup *group = &sim->groups[group_index];
      if (group->phase == LLE_COLOUR_GROUP_TOUCH) {
        group->previous_center_x = group->center_x;
        group->previous_center_y = group->center_y;
        group->center_x = sim->touch_x;
        group->center_y = sim->touch_y;
      }
    }
  }

  for (substep = 0; substep < LLE_COLOUR_SUBSTEPS; ++substep) {
    apply_sph_domain_acceleration(sim, primary, stock_time_step);
    apply_sph_forces(sim, primary, stock_time_step);
    integrate_sph_domain(sim, primary, stock_time_step);
  }
}

/*
 * updateSPH stages the latest app acceleration into particle +0x64/+0x68
 * only after both complete primary/secondary engine updates. The engines
 * therefore consume the preceding display tick's sample. Newly emitted
 * particles are included in this copy and begin consuming it next tick.
 */
static void stage_sph_domain_acceleration(LleColourSim *sim) {
  size_t group_index;
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    LleColourGroup *group = &sim->groups[group_index];
    size_t particle_index;
    if (!group_in_sph_domain(group, true) &&
        !group_in_sph_domain(group, false)) {
      continue;
    }
    for (particle_index = 0; particle_index < group->count;
         ++particle_index) {
      LleColourParticle *particle = &group->particles[particle_index];
      if (!particle->active) {
        continue;
      }
      particle->staged_acceleration_x = sim->sensor_acceleration_x;
      particle->staged_acceleration_y = sim->sensor_acceleration_y;
    }
  }
}

/*
 * State/type 0 in updateSPH is the secondary affordance engine.  It does not
 * share the released-touch Q80 clock:
 *
 *   1. emit to 20 particles and grow each to at least 0.5 with 0.0216666659;
 *   2. snapshot position/scale and switch the group progress from 0 to 1;
 *   3. advance progress by 0.02 while SineInOut33 closes scale and
 *      SineInOut70 returns the particles through the stock cosine window;
 *   4. delete once 2-progress reaches 0.15.
 */
static void advance_affordance_state(LleColourSim *sim,
                                     LleColourGroup *group) {
  const bool fading = group->age >= 1.0f;
  bool all_mature =
      group->count >= LLE_COLOUR_LIVE_GROUP_PARTICLES;
  bool finished = false;
  float average_x = 0.0f;
  float average_y = 0.0f;
  size_t active_count = 0u;
  size_t index;

  for (index = 0; index < group->count; ++index) {
    LleColourParticle *particle = &group->particles[index];
    float dx;
    float dy;
    float distance;
    if (!particle->active) {
      continue;
    }

    if (fading) {
      const float fade_source = 2.0f - group->age;
      float return_progress = 0.0f;
      particle->scale =
          particle->released_scale * sine_in_out33(fade_source);
      if (fade_source > 0.2f) {
        return_progress = 1.0f;
        if (fade_source < 1.0f) {
          const float window =
              clampf((fade_source - 0.2f) * 1.25f, 0.0f, 1.0f);
          const float cosine_ease =
              (1.0f - cosf(window * LLE_COLOUR_PI)) * 0.5f;
          return_progress = sine_in_out70(cosine_ease);
        }
      }
      particle->x =
          group->center_x +
          (particle->affordance_start_x - group->center_x) *
              return_progress;
      particle->y =
          group->center_y +
          (particle->affordance_start_y - group->center_y) *
              return_progress;
      if (fade_source <= 0.15f) {
        finished = true;
      }
    } else if (particle->scale < 0.5f) {
      all_mature = false;
      update_particle_growth(sim, particle, 0.0216666659f);
    }

    dx = group->center_x - particle->x;
    dy = group->center_y - particle->y;
    distance = sqrtf(dx * dx + dy * dy);
    if (distance > sim->width * 0.16f) {
      particle->transient_force_x += 0.75f * dx;
      particle->transient_force_y += 0.75f * dy;
      particle->velocity_x *= 0.9f;
      particle->velocity_y *= 0.9f;
    }
  }

  if (!fading && all_mature) {
    group->age = 1.0f;
    for (index = 0; index < group->count; ++index) {
      LleColourParticle *particle = &group->particles[index];
      if (!particle->active) {
        continue;
      }
      particle->affordance_start_x = particle->x;
      particle->affordance_start_y = particle->y;
      particle->released_scale = particle->scale;
      particle->smoothing_radius = 0.0f;
    }
  } else if (fading) {
    group->age += 0.02f;
  }

  if (finished) {
    for (index = 0; index < group->count; ++index) {
      group->particles[index].active = false;
    }
    return;
  }

  for (index = 0; index < group->count; ++index) {
    if (group->particles[index].active) {
      average_x += group->particles[index].x;
      average_y += group->particles[index].y;
      ++active_count;
    }
  }
  if (active_count > 0u) {
    average_x /= (float)active_count;
    average_y /= (float)active_count;
    for (index = 0; index < group->count; ++index) {
      if (group->particles[index].active) {
        group->particles[index].color_x = average_x;
        group->particles[index].color_y = average_y;
      }
    }
  }
}

/*
 * Stock performs this per-particle state pass after the engine updates and
 * after live emission.  In particular, the last touch delta is restaged as a
 * force on every displayed update until updateTouchEvent supplies the next
 * MOVE delta or releases the live group:
 *
 *   vx += worldWidth  * (currentX - previousX) / screenWidth
 *   vy += worldHeight * (currentY - previousY) / screenHeight
 *
 * Positions and velocities in this port are expressed in pixels, so the
 * world-to-pixel conversion cancels and the exact equivalent is the raw pixel
 * delta.  The engine clears its per-particle force during integration; stock
 * updateSPH then adds this same retained delta again on the next display tick.
 */
static void advance_group_state(LleColourSim *sim, LleColourGroup *group) {
  const bool held = group->phase == LLE_COLOUR_GROUP_TOUCH;
  const bool released = group->phase == LLE_COLOUR_GROUP_RELEASED;
  const bool affordance = group->phase == LLE_COLOUR_GROUP_AFFORDANCE;
  const float target_x = held ? sim->touch_x : group->center_x;
  const float target_y = held ? sim->touch_y : group->center_y;
  float released_growth_max = 0.0f;
  bool released_finished = false;
  size_t index;

  if (affordance) {
    advance_affordance_state(sim, group);
    return;
  }
  if (released) {
    group->age = minf(group->age + 0.015f, 1.0f);
  }
  for (index = 0; index < group->count; ++index) {
    LleColourParticle *particle = &group->particles[index];
    float dx;
    float dy;
    float distance;
    if (!particle->active) {
      continue;
    }
    if (held) {
      update_particle_growth(sim, particle, 0.0241666675f);
      particle->transient_force_x += sim->touch_velocity_x;
      particle->transient_force_y += sim->touch_velocity_y;
    } else if (released) {
      if (group->age >= 0.3f) {
        const float fade_source =
            1.0f - (group->age - 0.3f) * 1.42857146f;
        if (fade_source <= 0.0f) {
          released_finished = true;
        } else {
          particle->scale =
              particle->released_scale * quint_out(fade_source);
          particle->smoothing_radius =
              particle->released_radius *
              quint_out(fade_source * fade_source);
        }
      } else {
        const float current_scale = particle->scale;
        const bool below_running_max =
            index != 0u && current_scale < released_growth_max;
        if (!below_running_max) {
          released_growth_max = current_scale;
        }
        if (below_running_max || current_scale < 0.35f) {
          update_particle_growth(sim, particle, 1.0f / 60.0f);
          particle->released_scale = particle->scale;
          particle->released_radius = particle->smoothing_radius;
        }
      }
      update_released_edge_offset(sim, particle);
    }

    dx = target_x - particle->x;
    dy = target_y - particle->y;
    distance = sqrtf(dx * dx + dy * dy);
    if (held && distance > sim->width * 0.16f) {
      particle->transient_force_x += 1.5f * dx;
      particle->transient_force_y += 1.5f * dy;
      particle->velocity_x *= 0.6f;
      particle->velocity_y *= 0.6f;
    }

  }

  if (released && (released_finished || group->age >= 1.0f)) {
    for (index = 0; index < group->count; ++index) {
      group->particles[index].active = false;
    }
  }

  {
    float average_x = 0.0f;
    float average_y = 0.0f;
    size_t active_count = 0u;
    for (index = 0; index < group->count; ++index) {
      if (group->particles[index].active) {
        average_x += group->particles[index].x;
        average_y += group->particles[index].y;
        ++active_count;
      }
    }
    if (active_count > 0u) {
      average_x /= (float)active_count;
      average_y /= (float)active_count;
      for (index = 0; index < group->count; ++index) {
        if (group->particles[index].active) {
          group->particles[index].color_x = average_x;
          group->particles[index].color_y = average_y;
        }
      }
    }

  }
}

LleColourSim *lle_colour_sim_create(float width, float height, int project_kind,
                                    uint64_t seed) {
  LleColourSim *sim = (LleColourSim *)calloc(1u, sizeof(*sim));
  if (sim == NULL) {
    return NULL;
  }
  sim->project_kind = project_kind;
  sim->current_group = LLE_COLOUR_INVALID_GROUP;
  seed_vendor_rng(sim, seed);
  reset_draw_params(sim);
  lle_colour_sim_set_surface(sim, width, height, width, height);
  lle_colour_sim_reset(sim);
  return sim;
}

void lle_colour_sim_destroy(LleColourSim *sim) { free(sim); }

void lle_colour_sim_set_surface(LleColourSim *sim, float width, float height,
                                float logical_width, float logical_height) {
  size_t group_index;
  float scale_x;
  float scale_y;
  if (sim == NULL) {
    return;
  }
  width = width > 0.0f ? width : 1.0f;
  height = height > 0.0f ? height : 1.0f;
  scale_x = sim->width > 0.0f ? width / sim->width : 1.0f;
  scale_y = sim->height > 0.0f ? height / sim->height : 1.0f;
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    LleColourGroup *group = &sim->groups[group_index];
    size_t particle_index;
    group->center_x *= scale_x;
    group->center_y *= scale_y;
    group->previous_center_x *= scale_x;
    group->previous_center_y *= scale_y;
    for (particle_index = 0; particle_index < group->count; ++particle_index) {
      LleColourParticle *particle = &group->particles[particle_index];
      particle->x *= scale_x;
      particle->y *= scale_y;
      particle->velocity_x *= scale_x;
      particle->velocity_y *= scale_y;
      particle->render_offset_x *= scale_x;
      particle->render_offset_y *= scale_y;
      particle->color_x *= scale_x;
      particle->color_y *= scale_y;
      particle->smoothing_radius *= scale_x;
      particle->released_radius *= scale_x;
      particle->transient_force_x *= scale_x;
      particle->transient_force_y *= scale_y;
    }
  }
  sim->touch_x *= scale_x;
  sim->touch_y *= scale_y;
  sim->previous_touch_x *= scale_x;
  sim->previous_touch_y *= scale_y;
  sim->touch_velocity_x *= scale_x;
  sim->touch_velocity_y *= scale_y;
  sim->direction_move_delta_x *= scale_x;
  sim->direction_move_delta_y *= scale_y;
  sim->background_center_x *= scale_x;
  sim->background_center_y *= scale_y;
  sim->width = width;
  sim->height = height;
  sim->logical_width = logical_width > 0.0f ? logical_width : width;
  sim->logical_height = logical_height > 0.0f ? logical_height : height;
  sim->pixel_scale = minf(width, height) / 1080.0f;
  sim->draw_params.restore_ratio = surface_restore_ratio(sim);
}

bool lle_colour_sim_touch(LleColourSim *sim, int action, float x, float y,
                          uint64_t event_time_ms) {
  if (sim == NULL) {
    return false;
  }
  x = clampf(x, 0.0f, sim->width);
  y = clampf(y, 0.0f, sim->height);
  if (action == LLE_COLOUR_TOUCH_DOWN) {
    uint32_t group_index;
    release_current_group(sim);
    group_index = allocate_group(sim, LLE_COLOUR_GROUP_TOUCH);
    if (group_index == LLE_COLOUR_INVALID_GROUP) {
      return false;
    }
    sim->current_group = group_index;
    sim->touching = true;
    sim->touch_x = x;
    sim->touch_y = y;
    sim->previous_touch_x = x;
    sim->previous_touch_y = y;
    sim->touch_velocity_x = 0.0f;
    sim->touch_velocity_y = 0.0f;
    sim->touch_time_ms = event_time_ms;
    sim->groups[group_index].center_x = x;
    sim->groups[group_index].center_y = y;
    sim->groups[group_index].previous_center_x = x;
    sim->groups[group_index].previous_center_y = y;
    return true;
  }
  if (!sim->touching || sim->current_group == LLE_COLOUR_INVALID_GROUP) {
    return false;
  }
  if (action == LLE_COLOUR_TOUCH_MOVE) {
    sim->previous_touch_x = sim->touch_x;
    sim->previous_touch_y = sim->touch_y;
    sim->touch_x = x;
    sim->touch_y = y;
    sim->touch_velocity_x = sim->touch_x - sim->previous_touch_x;
    sim->touch_velocity_y = sim->touch_y - sim->previous_touch_y;
    sim->direction_move_delta_x = sim->touch_velocity_x;
    sim->direction_move_delta_y = sim->touch_velocity_y;
    sim->direction_target_pending = true;
    sim->touch_time_ms = event_time_ms;
    return true;
  }
  if (action == LLE_COLOUR_TOUCH_UP) {
    sim->touch_x = x;
    sim->touch_y = y;
    sim->touching = false;
    release_current_group(sim);
    return true;
  }
  return false;
}

void lle_colour_sim_sensor(LleColourSim *sim, int sensor_type, float x, float y,
                           float z) {
  (void)z;
  if (sim == NULL) {
    return;
  }
  if (sensor_type != 0 && sensor_type != 1) {
    return;
  }
  sim->sensor_acceleration_x = -clampf(x, -10.0f, 10.0f) * 0.01f;
  sim->sensor_acceleration_y = -clampf(y, -10.0f, 10.0f) * 0.015f;
}

void lle_colour_sim_affordance(LleColourSim *sim, float x, float y) {
  uint32_t group_index;
  LleColourGroup *group;
  if (sim == NULL) {
    return;
  }
  /*
   * Stock custom event 0x5c is accepted only while +0x40 == 3 (the previous
   * type-0 record has completed).  While the record is live +0x40 == 1, so a
   * duplicate request is ignored rather than layering or restarting it.
   */
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    if (sim->groups[group_index].phase == LLE_COLOUR_GROUP_AFFORDANCE) {
      return;
    }
  }
  group_index = allocate_group(sim, LLE_COLOUR_GROUP_AFFORDANCE);
  if (group_index == LLE_COLOUR_INVALID_GROUP) {
    return;
  }
  group = &sim->groups[group_index];
  group->center_x = clampf(x, 0.0f, sim->width);
  group->center_y = clampf(y, 0.0f, sim->height);
  group->previous_center_x = group->center_x;
  group->previous_center_y = group->center_y;
}

void lle_colour_sim_unlock(LleColourSim *sim) {
  if (sim == NULL) {
    return;
  }
  /*
   * SPColourDropletApp::unlock @ 0x5b304 only raises the unlock flag.  Delay,
   * progress, tail and point-size state belong to resetApp and must not be
   * restarted by a duplicate key event.
   */
  sim->unlocking = true;
  sim->unlock_finished = false;
}

void lle_colour_sim_reset_bg_scale(LleColourSim *sim) {
  size_t group_index;
  if (sim == NULL) {
    return;
  }
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    LleColourGroup *group = &sim->groups[group_index];
    size_t particle_index;
    for (particle_index = 0; particle_index < group->count; ++particle_index) {
      LleColourParticle *particle = &group->particles[particle_index];
      particle->color_x = clampf(particle->x, 0.0f, sim->width);
      particle->color_y = clampf(particle->y, 0.0f, sim->height);
    }
  }
}

void lle_colour_sim_reset(LleColourSim *sim) {
  size_t index;
  if (sim == NULL) {
    return;
  }
  for (index = 0; index < LLE_COLOUR_GROUP_CAPACITY; ++index) {
    clear_group(&sim->groups[index]);
  }
  sim->current_group = LLE_COLOUR_INVALID_GROUP;
  sim->touch_x = sim->width * 0.5f;
  sim->touch_y = sim->height * 0.5f;
  sim->previous_touch_x = sim->touch_x;
  sim->previous_touch_y = sim->touch_y;
  sim->touch_velocity_x = 0.0f;
  sim->touch_velocity_y = 0.0f;
  sim->direction_move_delta_x = 0.0f;
  sim->direction_move_delta_y = 0.0f;
  sim->direction_target_pending = false;
  sim->background_center_x = sim->touch_x;
  sim->background_center_y = sim->touch_y;
  /*
   * Stock resetState @ 0x15eb90 clears particles and interaction state but
   * deliberately preserves the latest sensor acceleration at +0xb84/+0xb88.
   * A newly calloc-created simulation still starts at zero; subsequent resets
   * keep the last accelerometer sample until onEventSensor replaces it.
   */
  sim->touch_time_ms = 0u;
  sim->unlock_delay_frames = 10;
  sim->unlock_tail_frames = 60;
  sim->unlock_progress = 0.0f;
  sim->particle_size_control = 140.0f;
  sim->breath_phase = 0.0f;
  sim->breath_accumulator = 0.0f;
  /*
   * Stock keeps the satellite emission phase across effect resets.  The
   * calloc-initialised value still makes the first run begin at phase zero.
   */
  sim->touching = false;
  sim->unlocking = false;
  sim->unlock_finished = false;
  reset_draw_params(sim);
}

void lle_colour_sim_tick(LleColourSim *sim) {
  size_t group_index;
  if (sim == NULL) {
    return;
  }

  /*
   * updateTouchEvent clears the direction renderer target before draining the
   * current input batch; the final MOVE delta in that batch wins.  Keep this
   * pulse separate from the persistent SPH touch velocity used by physics.
   */
  if (sim->direction_target_pending) {
    sim->draw_params.direction_velocity_x =
        sim->direction_move_delta_x * sim->draw_params.restore_ratio;
    sim->draw_params.direction_velocity_y =
        sim->direction_move_delta_y * sim->draw_params.restore_ratio;
  } else {
    sim->draw_params.direction_velocity_x = 0.0f;
    sim->draw_params.direction_velocity_y = 0.0f;
  }
  sim->direction_target_pending = false;

  if (!sim->unlocking) {
    const float half_width = sim->width * 0.5f;
    const float half_height = sim->height * 0.5f;
    sim->breath_phase += 0.02f;
    sim->breath_accumulator += sinf(sim->breath_phase);
    if (sim->breath_phase >= 2.0f * LLE_COLOUR_PI) {
      sim->breath_phase = 0.0f;
      sim->breath_accumulator = 0.0f;
    }
    /*
     * calculationTabScaleUV @ 0x5ad20 low-pass filters the current touch by
     * 0.05, then createTabScaledTextureUV @ 0x56ea0 rebuilds the final
     * composite UV range as [1-scale+offset, scale+offset]. Stock keeps touch
     * Y bottom-up; this simulation keeps Android top-down Y, so the equivalent
     * Y numerator is intentionally backgroundCenterY-halfHeight.
     */
    sim->background_center_x +=
        0.05f * (sim->touch_x - sim->background_center_x);
    sim->background_center_y +=
        0.05f * (sim->touch_y - sim->background_center_y);
    sim->draw_params.tab_scale =
        0.98f + 0.00025f * (sim->breath_accumulator - 50.0f);
    sim->draw_params.tab_offset_x =
        half_width > 0.0f
            ? ((sim->background_center_x - half_width) / half_width) *
                  (1.0f - sim->draw_params.tab_scale)
            : 0.0f;
    sim->draw_params.tab_offset_y =
        half_height > 0.0f
            ? ((sim->background_center_y - half_height) / half_height) *
                  (1.0f - sim->draw_params.tab_scale)
            : 0.0f;
  }

  if (sim->unlocking) {
    /*
     * updateUnlock @ 0x5ae70 runs before updateSPH on every displayed tick.
     * Expansion starts immediately; the ten-frame counter delays only the
     * optical curves.  The stock comparison is made before multiplication,
     * so the final 1.08 step is allowed to cross 2000.
     */
    if (sim->particle_size_control < 2000.0f) {
      sim->particle_size_control *= 1.08f;
    }
    sim->breath_accumulator *= 0.99f;

    --sim->unlock_delay_frames;
    if (sim->unlock_delay_frames < 1) {
      float edge_source;
      const float progress = sim->unlock_progress;
      sim->draw_params.refraction_ratio =
          quint_out(1.0f - progress);
      edge_source = powf(progress, 0.125f);
      sim->draw_params.edge_offset_ratio = quint_out(1.0f - edge_source);
      /*
       * Stock also evaluates the specular ratio from pow(progress, 0.05).
       * The app-owned composite has no independent specular term, so retain
       * only the recovered refraction/edge parameters in this isolated port.
       */
      if (progress < 1.0f) {
        sim->unlock_progress = progress + 0.05f;
        if (sim->unlock_progress >= 1.0f) {
          sim->unlock_progress = 1.0f;
        }
      } else {
        --sim->unlock_tail_frames;
      }
    }
    /*
     * drawApp applies edge ratio after updateUnlock, hence it observes the
     * newly incremented progress while refraction/edge-offset use the old one.
     */
    sim->draw_params.edge_ratio =
        1.0f - sine_in_out90(sim->unlock_progress);
  }

  /*
   * Native updateSPH ordering: update both SPH engines twice, emit, rebuild
   * the live vector, then apply growth and touch/restorative impulses.
   */
  advance_sph_domain(sim, true);
  advance_sph_domain(sim, false);

  if (sim->touching && sim->current_group != LLE_COLOUR_INVALID_GROUP) {
    LleColourGroup *group = &sim->groups[sim->current_group];
    if (group->count < LLE_COLOUR_LIVE_GROUP_PARTICLES) {
      /*
       * Stock emits two per update and uses begin()[0] as the
       * continuation origin after a large pointer jump.
       */
      if (group->count > 0u) {
        const LleColourParticle *first = &group->particles[0];
        const float dx = sim->touch_x - first->x;
        const float dy = sim->touch_y - first->y;
        const float threshold = sim->width * 0.1f;
        if (sqrtf(dx * dx + dy * dy) > threshold) {
          group->center_x = first->x;
          group->center_y = first->y;
        } else {
          group->center_x = sim->touch_x;
          group->center_y = sim->touch_y;
        }
      }
      emit_particles(sim, group, 2u, false, true);
      group->center_x = sim->touch_x;
      group->center_y = sim->touch_y;
    }
  }

  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    LleColourGroup *group = &sim->groups[group_index];
    if (group->phase == LLE_COLOUR_GROUP_AFFORDANCE &&
        group->count < LLE_COLOUR_LIVE_GROUP_PARTICLES) {
      emit_particles(sim, group, 2u, true, false);
    }
  }

  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    LleColourGroup *group = &sim->groups[group_index];
    if (group->phase != LLE_COLOUR_GROUP_FREE &&
        group->phase != LLE_COLOUR_GROUP_SUBPARTICLE) {
      advance_group_state(sim, group);
    }
  }

  stage_sph_domain_acceleration(sim);

  /* Stock updates the lightweight sub-particle system after updateSPH. */
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    LleColourGroup *group = &sim->groups[group_index];
    if (group->phase == LLE_COLOUR_GROUP_SUBPARTICLE) {
      advance_subparticle(sim, group);
    }
  }

  /*
   * Stock advances existing satellites first and emits afterwards, so a new
   * scale-zero satellite is not advanced until the following display tick.
   */
  if (sim->touching && !sim->unlocking) {
    if (++g_subparticle_counter > 12u) {
      emit_subparticle(sim);
      g_subparticle_counter = 0u;
    }
  }

  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    LleColourGroup *group = &sim->groups[group_index];
    size_t particle_index;
    bool any_active = false;
    if (group->phase == LLE_COLOUR_GROUP_FREE) {
      continue;
    }
    for (particle_index = 0; particle_index < group->count; ++particle_index) {
      if (group->particles[particle_index].active) {
        any_active = true;
        break;
      }
    }
    if (!any_active) {
      if (sim->current_group == group_index) {
        sim->current_group = LLE_COLOUR_INVALID_GROUP;
        sim->touching = false;
      }
      clear_group(group);
    }
  }

  if (sim->unlocking && sim->unlock_progress >= 1.0f &&
      sim->unlock_tail_frames <= 0) {
    sim->unlocking = false;
    sim->unlock_finished = true;
  }
}

bool lle_colour_sim_is_idle(const LleColourSim *sim) {
  return sim == NULL || sim->unlock_finished ||
         (!sim->touching && !sim->unlocking &&
                         lle_colour_sim_particle_count(sim) == 0u);
}

size_t lle_colour_sim_particle_count(const LleColourSim *sim) {
  size_t group_index;
  size_t count = 0;
  if (sim == NULL) {
    return 0u;
  }
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    const LleColourGroup *group = &sim->groups[group_index];
    size_t particle_index;
    for (particle_index = 0; particle_index < group->count; ++particle_index) {
      if (group->particles[particle_index].active) {
        ++count;
      }
    }
  }
  return count;
}

size_t
lle_colour_sim_export_draw_particles(const LleColourSim *sim,
                                     LleColourDrawParticle *out_particles,
                                     size_t capacity) {
  size_t group_index;
  size_t required;
  size_t output_index = 0;
  if (sim == NULL) {
    return 0u;
  }
  required = lle_colour_sim_particle_count(sim);
  if (out_particles == NULL || capacity < required) {
    return required;
  }
  for (group_index = 0; group_index < LLE_COLOUR_GROUP_CAPACITY;
       ++group_index) {
    const LleColourGroup *group = &sim->groups[group_index];
    size_t particle_index;
    for (particle_index = 0; particle_index < group->count; ++particle_index) {
      const LleColourParticle *particle = &group->particles[particle_index];
      LleColourDrawParticle *out;
      float render_curve;
      bool satellite;
      if (!particle->active) {
        continue;
      }
      out = &out_particles[output_index++];
      render_curve = sine_in_out80(particle->scale);
      satellite = group->phase == LLE_COLOUR_GROUP_SUBPARTICLE;
      out->x = particle->x + particle->render_offset_x;
      out->y = particle->y + particle->render_offset_y;
      out->velocity_x = particle->velocity_x;
      out->velocity_y = particle->velocity_y;
      out->density_size_px =
          (satellite ? 116.666667f : 93.333333f) * render_curve *
          (sim->particle_size_control / 140.0f);
      out->colour_size_px =
          (satellite ? 378.0f : 189.0f) * render_curve *
          (sim->particle_size_control / 140.0f);
      out->alpha = clampf(particle->alpha, 0.0f, 1.0f);
      out->color_x = particle->color_x;
      out->color_y = particle->color_y;
      out->flags = (particle->special ? LLE_COLOUR_PARTICLE_SPECIAL : 0u) |
                   (satellite ? LLE_COLOUR_PARTICLE_SATELLITE : 0u);
    }
  }
  return required;
}

void lle_colour_sim_get_draw_params(const LleColourSim *sim,
                                    LleColourDrawParams *out_params) {
  if (sim == NULL || out_params == NULL) {
    return;
  }
  *out_params = sim->draw_params;
}
