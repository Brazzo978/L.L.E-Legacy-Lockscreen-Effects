#include "lle_spark_sim.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#define LLE_SPARK_PI 3.14159265358979323846f
#define LLE_SPARK_FIXED_DT 0.5f
#define LLE_SPARK_VENDOR_DEFAULT_SEED UINT32_C(1)
#define LLE_SPARK_VENDOR_RNG_DEGREE 31u
#define LLE_SPARK_VENDOR_RNG_SEPARATION 3u

enum {
    LLE_SPARK_ANIMATION_AFFORDANCE = 1,
    LLE_SPARK_ANIMATION_PRESS = 2
};

enum {
    LLE_SPARK_EDGE_NONE = 0,
    LLE_SPARK_EDGE_LEFT = 1,
    LLE_SPARK_EDGE_RIGHT = 2
};

typedef struct LleSparkParticle {
    float x;
    float y;
    float initial_x;
    float initial_y;
    float velocity_x;
    float velocity_y;
    float initial_velocity_x;
    float initial_velocity_y;
    float acceleration_x;
    float acceleration_y;
    float size;
    float max_size;
    float size_increment;
    float alpha;
    float lifetime;
    float max_lifetime;
    float boundary_count;
    float edge_band_ratio;
    float size_control_seconds;
    float alpha_start_tick;
    float affordance_y_factor;
    float twinkle_clock;
    float next_twinkle;
    uint8_t animation_type;
    uint8_t edge_direction;
    bool active;
    bool fast_hide;
    bool twinkle;
} LleSparkParticle;

typedef struct LleSparkGroup {
    LleSparkParticle particles[LLE_SPARK_PARTICLES_PER_GROUP];
    bool active;
} LleSparkGroup;

struct LleSparkSim {
    LleSparkGroup groups[LLE_SPARK_GROUP_CAPACITY];
    uint8_t active_order[LLE_SPARK_GROUP_CAPACITY];
    size_t active_order_count;
    float width;
    float height;
    float scale;
    float last_emission_x;
    float last_emission_y;
    uint32_t frames_since_emission;
    float adaptive_frames_since_emission;
    float adaptive_stochastic_credit;
    bool touching;
    uint32_t rng_state[LLE_SPARK_VENDOR_RNG_DEGREE];
    uint8_t rng_front;
    uint8_t rng_rear;
};

typedef enum LleSparkBurstKind {
    LLE_SPARK_BURST_PRESS,
    LLE_SPARK_BURST_AFFORDANCE
} LleSparkBurstKind;

static uint32_t rng_u32(LleSparkSim *sim) {
    const uint32_t sum =
            sim->rng_state[sim->rng_front] +
            sim->rng_state[sim->rng_rear];
    sim->rng_state[sim->rng_front] = sum;
    if (++sim->rng_front >= LLE_SPARK_VENDOR_RNG_DEGREE) {
        sim->rng_front = 0;
    }
    if (++sim->rng_rear >= LLE_SPARK_VENDOR_RNG_DEGREE) {
        sim->rng_rear = 0;
    }
    return (sum >> 1u) & UINT32_C(0x7fffffff);
}

static float rng_unit(LleSparkSim *sim) {
    return (float) rng_u32(sim) * (1.0f / 2147483648.0f);
}

static float rng_range(LleSparkSim *sim, float minimum, float maximum) {
    return minimum + (maximum - minimum) * rng_unit(sim);
}

static void seed_vendor_rng(LleSparkSim *sim, uint64_t requested_seed) {
    size_t index;
    uint32_t seed = (uint32_t) requested_seed;
    if (seed == 0u) {
        seed = LLE_SPARK_VENDOR_DEFAULT_SEED;
    }
    sim->rng_state[0] = seed;
    for (index = 1; index < LLE_SPARK_VENDOR_RNG_DEGREE; ++index) {
        const int32_t previous = (int32_t) sim->rng_state[index - 1u];
        const int32_t high = previous / 127773;
        const int32_t low = previous % 127773;
        int32_t next = 16807 * low - 2836 * high;
        if (next <= 0) {
            next += INT32_MAX;
        }
        sim->rng_state[index] = (uint32_t) next;
    }
    sim->rng_front = LLE_SPARK_VENDOR_RNG_SEPARATION;
    sim->rng_rear = 0;
    for (index = 0;
            index < 10u * LLE_SPARK_VENDOR_RNG_DEGREE;
            ++index) {
        (void) rng_u32(sim);
    }
    /*
     * Samsung creates 25 groups of 1,100 particles and generateParticles()
     * consumes one libc rand() value per slot before the first visible burst.
     */
    for (index = 0;
            index < LLE_SPARK_GROUP_CAPACITY * LLE_SPARK_PARTICLES_PER_GROUP;
            ++index) {
        (void) rng_u32(sim);
    }
}

static float scaled(const LleSparkSim *sim, float pixels) {
    return pixels * sim->scale;
}

static void deactivate_particle(LleSparkParticle *particle) {
    particle->x = 0.0f;
    particle->y = 0.0f;
    particle->velocity_x = 0.0f;
    particle->velocity_y = 0.0f;
    particle->initial_velocity_x = 0.0f;
    particle->initial_velocity_y = 0.0f;
    particle->acceleration_x = 0.0f;
    particle->acceleration_y = 0.0f;
    particle->size = 0.0f;
    particle->max_size = 0.0f;
    particle->size_increment = 0.0f;
    particle->alpha = 0.0f;
    particle->lifetime = 0.0f;
    particle->boundary_count = 0.0f;
    particle->animation_type = 0;
    particle->edge_direction = LLE_SPARK_EDGE_NONE;
    particle->active = false;
    particle->fast_hide = false;
    particle->twinkle = false;
    particle->twinkle_clock = 0.0f;
    particle->next_twinkle = 0.0f;
    particle->affordance_y_factor = 0.4f;
}

static void enable_fast_hide(LleSparkGroup *group) {
    size_t index;
    for (index = 0; index < LLE_SPARK_PARTICLES_PER_GROUP; ++index) {
        LleSparkParticle *particle = &group->particles[index];
        if (!particle->active || particle->fast_hide) {
            continue;
        }
        particle->fast_hide = true;
        particle->twinkle = false;
        particle->velocity_x *= 1.3f;
        particle->acceleration_x *= 1.3f;
        if (particle->boundary_count > 0.0f &&
                particle->lifetime < particle->max_lifetime) {
            particle->max_lifetime +=
                    (particle->lifetime - particle->max_lifetime) * 0.6f;
        }
    }
}

static int first_inactive_group(const LleSparkSim *sim) {
    size_t index;
    for (index = 0; index < LLE_SPARK_GROUP_CAPACITY; ++index) {
        if (!sim->groups[index].active) {
            return (int)index;
        }
    }
    return -1;
}

static void initialize_common_particle(
        LleSparkSim *sim,
        LleSparkParticle *particle,
        LleSparkBurstKind kind,
        float center_x,
        float center_y,
        float radius) {
    const float angle = rng_range(sim, 0.0f, 2.0f * LLE_SPARK_PI);
    const float radial_x = cosf(angle) * radius;
    const float radial_y = sinf(angle) * radius;
    const float velocity_scale = rng_range(sim, 0.01f, 0.03f);

    memset(particle, 0, sizeof(*particle));
    particle->x = center_x + radial_x;
    particle->y = center_y + radial_y;
    particle->initial_x = particle->x;
    particle->initial_y = particle->y;
    particle->velocity_x = radial_x * velocity_scale;
    particle->velocity_y = radial_y * velocity_scale;
    particle->initial_velocity_x = particle->velocity_x;
    particle->initial_velocity_y = particle->velocity_y;
    particle->animation_type = kind == LLE_SPARK_BURST_PRESS
            ? LLE_SPARK_ANIMATION_PRESS
            : LLE_SPARK_ANIMATION_AFFORDANCE;
    particle->alpha_start_tick = 1.5f * (float)LLE_SPARK_TICK_HZ;
    particle->edge_band_ratio = rng_range(sim, 0.03f, 0.12f);
    particle->size_control_seconds = 0.3f;
    particle->affordance_y_factor = 0.4f;
    particle->active = true;

    if (center_x < sim->width * 0.5f) {
        particle->edge_direction = LLE_SPARK_EDGE_LEFT;
        particle->acceleration_x =
                kind == LLE_SPARK_BURST_PRESS ? -0.6f : -1.0f;
    } else {
        particle->edge_direction = LLE_SPARK_EDGE_RIGHT;
        particle->acceleration_x =
                kind == LLE_SPARK_BURST_PRESS ? 0.6f : 1.0f;
    }
}

static void configure_size_growth(
        LleSparkParticle *particle,
        float size,
        float max_size,
        float control_seconds) {
    particle->size = size;
    particle->max_size = max_size;
    particle->size_control_seconds = control_seconds;
    particle->size_increment = control_seconds > 0.0f
            ? (max_size - size) /
                    (control_seconds * (float)LLE_SPARK_TICK_HZ)
            : 0.0f;
}

static void initialize_press_particle(
        LleSparkSim *sim,
        LleSparkParticle *particle,
        size_t particle_index,
        float center_x,
        float center_y) {
    float radius;
    const size_t small_end =
            (size_t)((float)LLE_SPARK_PARTICLES_PER_GROUP * 0.8f);
    const size_t medium_end = small_end +
            (size_t)((float)LLE_SPARK_PARTICLES_PER_GROUP * 0.15f);

    if (particle_index <= small_end) {
        radius = scaled(sim, rng_range(sim, 40.0f, 250.0f));
    } else if (particle_index <= medium_end) {
        radius = scaled(sim, rng_range(sim, 10.0f, 220.0f));
    } else {
        radius = scaled(sim, rng_range(sim, 10.0f, 240.0f));
    }

    initialize_common_particle(
            sim, particle, LLE_SPARK_BURST_PRESS,
            center_x, center_y, radius);
    particle->max_lifetime =
            rng_range(sim, 2.2f, 2.5f) * (float)LLE_SPARK_TICK_HZ;

    if (particle_index <= small_end) {
        particle->alpha = rng_range(sim, 0.35f, 0.60f);
        configure_size_growth(
                particle,
                scaled(sim, rng_range(sim, 4.0f, 10.0f)),
                0.0f,
                0.3f);
        particle->max_size = particle->size;
        particle->size_increment = 0.0f;
    } else if (particle_index <= medium_end) {
        const float size =
                scaled(sim, rng_range(sim, 12.0f, 18.0f));
        const float max_size =
                scaled(sim, rng_range(sim, 12.0f, 25.0f));
        particle->alpha = rng_range(sim, 0.35f, 0.60f);
        configure_size_growth(
                particle, size, max_size, rng_range(sim, 0.3f, 0.5f));
    } else {
        const float size =
                scaled(sim, rng_range(sim, 24.0f, 28.0f));
        const float max_size =
                scaled(sim, rng_range(sim, 28.0f, 43.0f));
        particle->twinkle = true;
        particle->next_twinkle = rng_range(sim, 0.0f, 0.2f);
        particle->alpha = rng_range(sim, 0.25f, 0.60f);
        configure_size_growth(
                particle, size, max_size, rng_range(sim, 0.3f, 0.7f));
    }
}

static void initialize_affordance_particle(
        LleSparkSim *sim,
        LleSparkParticle *particle,
        size_t particle_index,
        float center_x,
        float center_y) {
    float radius;
    const size_t small_end =
            (size_t)((float)LLE_SPARK_PARTICLES_PER_GROUP * 0.8f);
    const size_t medium_end = small_end +
            (size_t)((float)LLE_SPARK_PARTICLES_PER_GROUP * 0.15f);

    if (particle_index <= small_end) {
        radius = scaled(sim, rng_range(sim, 30.0f, 250.0f));
    } else if (particle_index <= medium_end) {
        radius = scaled(sim, rng_range(sim, 10.0f, 220.0f));
    } else {
        radius = scaled(sim, rng_range(sim, 10.0f, 200.0f));
    }

    initialize_common_particle(
            sim, particle, LLE_SPARK_BURST_AFFORDANCE,
            center_x, center_y, radius);
    particle->alpha = rng_range(sim, 0.50f, 0.60f);

    if (particle_index <= small_end) {
        particle->max_lifetime =
                rng_range(sim, 2.0f, 2.5f) * (float)LLE_SPARK_TICK_HZ;
        configure_size_growth(
                particle,
                scaled(sim, rng_range(sim, 4.0f, 10.0f)),
                0.0f,
                0.3f);
        particle->max_size = particle->size;
        particle->size_increment = 0.0f;
    } else if (particle_index <= medium_end) {
        const float size =
                scaled(sim, rng_range(sim, 12.0f, 18.0f));
        const float max_size =
                scaled(sim, rng_range(sim, 12.0f, 25.0f));
        particle->max_lifetime =
                rng_range(sim, 1.8f, 2.3f) * (float)LLE_SPARK_TICK_HZ;
        configure_size_growth(
                particle, size, max_size, rng_range(sim, 0.3f, 0.5f));
    } else {
        const float size =
                scaled(sim, rng_range(sim, 24.0f, 28.0f));
        const float max_size =
                scaled(sim, rng_range(sim, 28.0f, 43.0f));
        particle->max_lifetime =
                rng_range(sim, 1.6f, 2.1f) * (float)LLE_SPARK_TICK_HZ;
        particle->twinkle = true;
        particle->next_twinkle = rng_range(sim, 0.0f, 0.2f);
        configure_size_growth(
                particle, size, max_size, rng_range(sim, 0.3f, 0.7f));
    }
}

static bool emit_group(
        LleSparkSim *sim,
        LleSparkBurstKind kind,
        float center_x,
        float center_y) {
    const int group_index = first_inactive_group(sim);
    LleSparkGroup *group;
    size_t particle_index;

    if (group_index < 0) {
        return false;
    }

    group = &sim->groups[group_index];
    memset(group, 0, sizeof(*group));
    for (particle_index = 0;
            particle_index < LLE_SPARK_PARTICLES_PER_GROUP;
            ++particle_index) {
        if (kind == LLE_SPARK_BURST_PRESS) {
            initialize_press_particle(
                    sim,
                    &group->particles[particle_index],
                    particle_index,
                    center_x,
                    center_y);
        } else {
            initialize_affordance_particle(
                    sim,
                    &group->particles[particle_index],
                    particle_index,
                    center_x,
                    center_y);
        }
    }
    group->active = true;
    sim->active_order[sim->active_order_count++] = (uint8_t)group_index;

    /*
     * The recovered press path returns early for physical group zero. Preserve
     * that quirk because it changes which old burst receives fast-hide.
     * Hint/affordance bursts never invoke the rolling fast-hide path.
     */
    if (kind == LLE_SPARK_BURST_PRESS &&
            group_index != 0 &&
            sim->active_order_count >= 15u) {
        const size_t old_order_index = sim->active_order_count - 15u;
        const uint8_t old_group_index =
                sim->active_order[old_order_index];
        enable_fast_hide(&sim->groups[old_group_index]);
    }
    return true;
}

static void apply_edge_bounce(
        const LleSparkSim *sim,
        LleSparkParticle *particle) {
    const float candidate_x =
            particle->x + particle->velocity_x * LLE_SPARK_FIXED_DT;

    if (particle->edge_direction == LLE_SPARK_EDGE_LEFT) {
        if (candidate_x <= 0.0f) {
            particle->acceleration_x = 0.0f;
            if (particle->boundary_count < 1.0f) {
                particle->velocity_x *= 0.35f;
            }
            particle->velocity_x = -particle->velocity_x;
            particle->boundary_count += 1.0f;
        }
        if (particle->boundary_count > 0.0f &&
                candidate_x >=
                        particle->edge_band_ratio * sim->width) {
            particle->velocity_x = -particle->velocity_x;
        }
    } else if (particle->edge_direction == LLE_SPARK_EDGE_RIGHT) {
        if (candidate_x >= sim->width) {
            particle->acceleration_x = 0.0f;
            if (particle->boundary_count < 1.0f) {
                particle->velocity_x *= 0.35f;
            }
            particle->velocity_x = -particle->velocity_x;
            particle->boundary_count += 1.0f;
        }
        if (particle->boundary_count > 0.0f &&
                candidate_x <=
                        (1.0f - particle->edge_band_ratio) * sim->width) {
            particle->velocity_x = -particle->velocity_x;
        }
    }
}

static void apply_edge_bounce_adaptive(
        const LleSparkSim *sim,
        LleSparkParticle *particle,
        float frame_delta) {
    const float candidate_x = particle->x + particle->velocity_x *
            LLE_SPARK_FIXED_DT * frame_delta;

    if (particle->edge_direction == LLE_SPARK_EDGE_LEFT) {
        if (candidate_x <= 0.0f) {
            particle->acceleration_x = 0.0f;
            if (particle->boundary_count < 1.0f) {
                particle->velocity_x *= 0.35f;
            }
            particle->velocity_x = -particle->velocity_x;
            particle->boundary_count += 1.0f;
        }
        if (particle->boundary_count > 0.0f &&
                candidate_x >= particle->edge_band_ratio * sim->width) {
            particle->velocity_x = -particle->velocity_x;
        }
    } else if (particle->edge_direction == LLE_SPARK_EDGE_RIGHT) {
        if (candidate_x >= sim->width) {
            particle->acceleration_x = 0.0f;
            if (particle->boundary_count < 1.0f) {
                particle->velocity_x *= 0.35f;
            }
            particle->velocity_x = -particle->velocity_x;
            particle->boundary_count += 1.0f;
        }
        if (particle->boundary_count > 0.0f &&
                candidate_x <=
                        (1.0f - particle->edge_band_ratio) * sim->width) {
            particle->velocity_x = -particle->velocity_x;
        }
    }
}

static void move_particle(
        LleSparkSim *sim,
        LleSparkParticle *particle) {
    float acceleration_scale = 1.0f;

    if (!particle->active) {
        return;
    }

    particle->lifetime += 1.0f;
    if (particle->lifetime > particle->max_lifetime ||
            (particle->fast_hide && particle->alpha < 0.05f)) {
        deactivate_particle(particle);
        return;
    }

    if (particle->lifetime <=
            particle->size_control_seconds *
                    (float)LLE_SPARK_TICK_HZ) {
        particle->size += particle->size_increment;
    }

    if (particle->animation_type == LLE_SPARK_ANIMATION_AFFORDANCE) {
        if (particle->lifetime >=
                particle->max_lifetime * 0.15f) {
            particle->y += LLE_SPARK_FIXED_DT *
                    particle->affordance_y_factor *
                    particle->initial_velocity_y;
            if (particle->lifetime <
                    particle->max_lifetime * 0.9f) {
                if (particle->boundary_count < 1.0f) {
                    particle->affordance_y_factor *= 1.05f;
                } else {
                    particle->affordance_y_factor = 2.0f;
                }
            } else {
                particle->affordance_y_factor *= 0.95f;
            }
        }
    } else {
        acceleration_scale = 1.0f + particle->size * 0.012f;
    }

    if (particle->twinkle) {
        particle->twinkle_clock += 0.1f;
        if (particle->twinkle_clock >= particle->next_twinkle) {
            particle->alpha = rng_range(sim, 0.0f, 0.7f);
            particle->twinkle_clock = 0.0f;
            particle->next_twinkle = rng_range(sim, 0.3f, 0.7f);
        }
    }

    if (particle->lifetime >= particle->alpha_start_tick) {
        if (particle->animation_type ==
                LLE_SPARK_ANIMATION_AFFORDANCE) {
            particle->alpha *= 0.945f;
        } else if (particle->fast_hide) {
            particle->alpha *= rng_range(sim, 0.75f, 0.90f);
        } else {
            particle->alpha *= 0.985f;
        }
    }

    apply_edge_bounce(sim, particle);

    particle->velocity_x += LLE_SPARK_FIXED_DT *
            acceleration_scale * particle->acceleration_x;
    particle->velocity_y += LLE_SPARK_FIXED_DT *
            acceleration_scale * particle->acceleration_y;
    particle->x += LLE_SPARK_FIXED_DT * particle->velocity_x;
    particle->y += LLE_SPARK_FIXED_DT * particle->velocity_y;
}

static void move_particle_adaptive(
        LleSparkSim *sim,
        LleSparkParticle *particle,
        float frame_delta) {
    float acceleration_scale = 1.0f;

    if (!particle->active || frame_delta <= 0.0f) {
        return;
    }

    particle->lifetime += frame_delta;
    if (particle->lifetime > particle->max_lifetime ||
            (particle->fast_hide && particle->alpha < 0.05f)) {
        deactivate_particle(particle);
        return;
    }

    if (particle->lifetime <= particle->size_control_seconds *
            (float)LLE_SPARK_TICK_HZ) {
        particle->size += particle->size_increment * frame_delta;
    }

    if (particle->animation_type == LLE_SPARK_ANIMATION_AFFORDANCE) {
        if (particle->lifetime >= particle->max_lifetime * 0.15f) {
            particle->y += LLE_SPARK_FIXED_DT * frame_delta *
                    particle->affordance_y_factor * particle->initial_velocity_y;
            if (particle->lifetime < particle->max_lifetime * 0.9f) {
                if (particle->boundary_count < 1.0f) {
                    particle->affordance_y_factor *= powf(1.05f, frame_delta);
                } else {
                    particle->affordance_y_factor = 2.0f;
                }
            } else {
                particle->affordance_y_factor *= powf(0.95f, frame_delta);
            }
        }
    } else {
        acceleration_scale = 1.0f + particle->size * 0.012f;
    }

    if (particle->twinkle) {
        particle->twinkle_clock += 0.1f * frame_delta;
    }

    if (particle->lifetime >= particle->alpha_start_tick) {
        if (particle->animation_type == LLE_SPARK_ANIMATION_AFFORDANCE) {
            particle->alpha *= powf(0.945f, frame_delta);
        } else if (!particle->fast_hide) {
            particle->alpha *= powf(0.985f, frame_delta);
        }
    }

    apply_edge_bounce_adaptive(sim, particle, frame_delta);

    particle->velocity_x += LLE_SPARK_FIXED_DT * frame_delta *
            acceleration_scale * particle->acceleration_x;
    particle->velocity_y += LLE_SPARK_FIXED_DT * frame_delta *
            acceleration_scale * particle->acceleration_y;
    particle->x += LLE_SPARK_FIXED_DT * frame_delta * particle->velocity_x;
    particle->y += LLE_SPARK_FIXED_DT * frame_delta * particle->velocity_y;
}

/*
 * Keep stochastic decisions on the original 60 Hz logical boundary.  The
 * adaptive path may render twice for one stock tick at 120 Hz, but must not
 * consume two random values or make fast-hide fade twice as quickly.
 */
static void apply_adaptive_stochastic_events(LleSparkSim *sim) {
    size_t order_index;
    for (order_index = 0;
            order_index < sim->active_order_count;
            ++order_index) {
        LleSparkGroup *group = &sim->groups[sim->active_order[order_index]];
        size_t particle_index;
        for (particle_index = 0;
                particle_index < LLE_SPARK_PARTICLES_PER_GROUP;
                ++particle_index) {
            LleSparkParticle *particle = &group->particles[particle_index];
            if (!particle->active) {
                continue;
            }
            if (particle->twinkle &&
                    particle->twinkle_clock >= particle->next_twinkle) {
                particle->alpha = rng_range(sim, 0.0f, 0.7f);
                particle->twinkle_clock = 0.0f;
                particle->next_twinkle = rng_range(sim, 0.3f, 0.7f);
            }
            if (particle->fast_hide &&
                    particle->lifetime >= particle->alpha_start_tick) {
                particle->alpha *= rng_range(sim, 0.75f, 0.90f);
            }
        }
    }
}

static void compact_active_order(LleSparkSim *sim) {
    size_t read_index;
    size_t write_index = 0;
    for (read_index = 0;
            read_index < sim->active_order_count;
            ++read_index) {
        const uint8_t group_index = sim->active_order[read_index];
        if (sim->groups[group_index].active) {
            sim->active_order[write_index++] = group_index;
        }
    }
    sim->active_order_count = write_index;
}

LleSparkSim *lle_spark_sim_create(
        float width,
        float height,
        uint64_t seed) {
    LleSparkSim *sim = (LleSparkSim *)calloc(1, sizeof(*sim));
    if (sim == NULL) {
        return NULL;
    }
    seed_vendor_rng(sim, seed);
    lle_spark_sim_set_surface(sim, width, height);
    return sim;
}

void lle_spark_sim_destroy(LleSparkSim *sim) {
    free(sim);
}

void lle_spark_sim_set_surface(
        LleSparkSim *sim,
        float width,
        float height) {
    float minimum;
    if (sim == NULL) {
        return;
    }
    sim->width = width > 0.0f ? width : 1.0f;
    sim->height = height > 0.0f ? height : 1.0f;
    minimum = sim->width < sim->height ? sim->width : sim->height;
    sim->scale = minimum / 1440.0f;
}

void lle_spark_sim_set_seed(LleSparkSim *sim, uint64_t seed) {
    if (sim != NULL) {
        seed_vendor_rng(sim, seed);
    }
}

bool lle_spark_sim_touch_begin(
        LleSparkSim *sim,
        float x,
        float y) {
    bool emitted;
    if (sim == NULL) {
        return false;
    }
    sim->touching = true;
    sim->last_emission_x = x;
    sim->last_emission_y = y;
    sim->frames_since_emission = 0;
    sim->adaptive_frames_since_emission = 0.0f;
    emitted = emit_group(sim, LLE_SPARK_BURST_PRESS, x, y);
    return emitted;
}

bool lle_spark_sim_touch_move_adaptive(
        LleSparkSim *sim,
        float x,
        float y) {
    float delta_x;
    float delta_y;
    float distance;
    float threshold;
    size_t active;
    bool emitted;

    if (sim == NULL || !sim->touching) {
        return false;
    }

    active = sim->active_order_count;
    threshold = 120.0f;
    if (active > 13u) {
        threshold += (float)(active - 13u) * 6.0f;
    }
    threshold *= sim->scale;

    delta_x = sim->last_emission_x - x;
    delta_y = sim->last_emission_y - y;
    distance = sqrtf(delta_x * delta_x + delta_y * delta_y);
    if (distance <= threshold ||
            (active > 13u && sim->adaptive_frames_since_emission < 4.0f)) {
        return false;
    }

    emitted = emit_group(sim, LLE_SPARK_BURST_PRESS, x, y);
    sim->last_emission_x = x;
    sim->last_emission_y = y;
    sim->frames_since_emission = 0;
    sim->adaptive_frames_since_emission = 0.0f;
    return emitted;
}

bool lle_spark_sim_touch_move(
        LleSparkSim *sim,
        float x,
        float y) {
    float delta_x;
    float delta_y;
    float distance;
    float threshold;
    size_t active;
    bool emitted;

    if (sim == NULL || !sim->touching) {
        return false;
    }

    active = sim->active_order_count;
    threshold = 120.0f;
    if (active > 13u) {
        threshold += (float)(active - 13u) * 6.0f;
    }
    threshold *= sim->scale;

    delta_x = sim->last_emission_x - x;
    delta_y = sim->last_emission_y - y;
    distance = sqrtf(delta_x * delta_x + delta_y * delta_y);
    if (distance <= threshold ||
            (active > 13u && sim->frames_since_emission < 4u)) {
        return false;
    }

    emitted = emit_group(sim, LLE_SPARK_BURST_PRESS, x, y);
    sim->last_emission_x = x;
    sim->last_emission_y = y;
    sim->frames_since_emission = 0;
    return emitted;
}

void lle_spark_sim_touch_end(LleSparkSim *sim) {
    if (sim != NULL) {
        sim->touching = false;
    }
}

size_t lle_spark_sim_hint(
        LleSparkSim *sim,
        float center_x,
        float center_y) {
    static const float offsets[4][2] = {
        {-50.0f, 25.0f},
        {-100.0f, -25.0f},
        {50.0f, -25.0f},
        {100.0f, 25.0f}
    };
    size_t index;
    size_t emitted = 0;
    if (sim == NULL) {
        return 0;
    }
    for (index = 0; index < 4u; ++index) {
        if (emit_group(
                sim,
                LLE_SPARK_BURST_AFFORDANCE,
                center_x + offsets[index][0],
                center_y + offsets[index][1])) {
            ++emitted;
        }
    }
    return emitted;
}

void lle_spark_sim_tick(LleSparkSim *sim) {
    size_t order_index;
    if (sim == NULL) {
        return;
    }
    if (sim->touching) {
        ++sim->frames_since_emission;
    }
    for (order_index = 0;
            order_index < sim->active_order_count;
            ++order_index) {
        const uint8_t group_index = sim->active_order[order_index];
        LleSparkGroup *group = &sim->groups[group_index];
        size_t particle_index;
        bool any_active = false;
        for (particle_index = 0;
                particle_index < LLE_SPARK_PARTICLES_PER_GROUP;
                ++particle_index) {
            move_particle(sim, &group->particles[particle_index]);
            any_active |= group->particles[particle_index].active;
        }
        group->active = any_active;
    }
    compact_active_order(sim);
}

void lle_spark_sim_advance_adaptive(LleSparkSim *sim, float frame_delta) {
    size_t order_index;
    float remaining;
    if (sim == NULL || !isfinite(frame_delta) || frame_delta <= 0.0f) {
        return;
    }
    if (sim->touching) {
        sim->adaptive_frames_since_emission += frame_delta;
    }
    remaining = frame_delta;
    while (remaining > 0.00001f) {
        const float until_event = 1.0f - sim->adaptive_stochastic_credit;
        const float segment = fminf(remaining, until_event);
        for (order_index = 0;
                order_index < sim->active_order_count;
                ++order_index) {
            const uint8_t group_index = sim->active_order[order_index];
            LleSparkGroup *group = &sim->groups[group_index];
            size_t particle_index;
            bool any_active = false;
            for (particle_index = 0;
                    particle_index < LLE_SPARK_PARTICLES_PER_GROUP;
                    ++particle_index) {
                move_particle_adaptive(
                        sim, &group->particles[particle_index], segment);
                any_active |= group->particles[particle_index].active;
            }
            group->active = any_active;
        }
        compact_active_order(sim);
        remaining -= segment;
        sim->adaptive_stochastic_credit += segment;
        if (sim->adaptive_stochastic_credit >= 0.99999f) {
            apply_adaptive_stochastic_events(sim);
            sim->adaptive_stochastic_credit = 0.0f;
        }
    }
}

void lle_spark_sim_unlock(LleSparkSim *sim) {
    size_t order_index;
    if (sim == NULL) {
        return;
    }
    for (order_index = 0;
            order_index < sim->active_order_count;
            ++order_index) {
        const uint8_t group_index = sim->active_order[order_index];
        LleSparkGroup *group = &sim->groups[group_index];
        size_t particle_index;
        for (particle_index = 0;
                particle_index < LLE_SPARK_PARTICLES_PER_GROUP;
                ++particle_index) {
            LleSparkParticle *particle =
                    &group->particles[particle_index];
            if (!particle->active) {
                continue;
            }
            particle->velocity_x =
                    particle->initial_velocity_x * 6.0f;
            particle->velocity_y =
                    particle->initial_velocity_y * 6.0f;
            particle->acceleration_x = 0.0f;
            particle->acceleration_y = 0.0f;
        }
    }
}

void lle_spark_sim_reset(LleSparkSim *sim) {
    uint32_t rng_state[LLE_SPARK_VENDOR_RNG_DEGREE];
    uint8_t rng_front;
    uint8_t rng_rear;
    float width;
    float height;
    float scale_value;
    if (sim == NULL) {
        return;
    }
    memcpy(rng_state, sim->rng_state, sizeof(rng_state));
    rng_front = sim->rng_front;
    rng_rear = sim->rng_rear;
    width = sim->width;
    height = sim->height;
    scale_value = sim->scale;
    memset(sim, 0, sizeof(*sim));
    memcpy(sim->rng_state, rng_state, sizeof(rng_state));
    sim->rng_front = rng_front;
    sim->rng_rear = rng_rear;
    sim->width = width;
    sim->height = height;
    sim->scale = scale_value;
}

size_t lle_spark_sim_active_group_count(const LleSparkSim *sim) {
    return sim == NULL ? 0u : sim->active_order_count;
}

size_t lle_spark_sim_active_particle_count(const LleSparkSim *sim) {
    size_t order_index;
    size_t count = 0;
    if (sim == NULL) {
        return 0;
    }
    for (order_index = 0;
            order_index < sim->active_order_count;
            ++order_index) {
        const LleSparkGroup *group =
                &sim->groups[sim->active_order[order_index]];
        size_t particle_index;
        for (particle_index = 0;
                particle_index < LLE_SPARK_PARTICLES_PER_GROUP;
                ++particle_index) {
            count += group->particles[particle_index].active ? 1u : 0u;
        }
    }
    return count;
}

static uint64_t hash_bytes(
        uint64_t hash,
        const void *bytes,
        size_t byte_count) {
    const uint8_t *cursor = (const uint8_t *)bytes;
    size_t index;
    for (index = 0; index < byte_count; ++index) {
        hash ^= cursor[index];
        hash *= UINT64_C(1099511628211);
    }
    return hash;
}

uint64_t lle_spark_sim_state_hash(const LleSparkSim *sim) {
    uint64_t hash = UINT64_C(1469598103934665603);
    size_t group_index;
    if (sim == NULL) {
        return 0;
    }

    hash = hash_bytes(hash, &sim->width, sizeof(sim->width));
    hash = hash_bytes(hash, &sim->height, sizeof(sim->height));
    hash = hash_bytes(hash, &sim->scale, sizeof(sim->scale));
    hash = hash_bytes(hash, sim->rng_state, sizeof(sim->rng_state));
    hash = hash_bytes(hash, &sim->rng_front, sizeof(sim->rng_front));
    hash = hash_bytes(hash, &sim->rng_rear, sizeof(sim->rng_rear));
    hash = hash_bytes(
            hash, &sim->active_order_count,
            sizeof(sim->active_order_count));
    hash = hash_bytes(
            hash, sim->active_order,
            sim->active_order_count * sizeof(sim->active_order[0]));
    hash = hash_bytes(
            hash, &sim->last_emission_x,
            sizeof(sim->last_emission_x));
    hash = hash_bytes(
            hash, &sim->last_emission_y,
            sizeof(sim->last_emission_y));
    hash = hash_bytes(
            hash, &sim->frames_since_emission,
            sizeof(sim->frames_since_emission));
    hash = hash_bytes(hash, &sim->touching, sizeof(sim->touching));

    for (group_index = 0;
            group_index < LLE_SPARK_GROUP_CAPACITY;
            ++group_index) {
        const LleSparkGroup *group = &sim->groups[group_index];
        size_t particle_index;
        hash = hash_bytes(hash, &group->active, sizeof(group->active));
        for (particle_index = 0;
                particle_index < LLE_SPARK_PARTICLES_PER_GROUP;
                ++particle_index) {
            const LleSparkParticle *particle =
                    &group->particles[particle_index];
            hash = hash_bytes(hash, particle, sizeof(*particle));
        }
    }
    return hash;
}

bool lle_spark_sim_get_particle(
        const LleSparkSim *sim,
        size_t group_slot,
        size_t particle_index,
        LleSparkParticleSnapshot *out_snapshot) {
    const LleSparkParticle *particle;
    if (sim == NULL ||
            out_snapshot == NULL ||
            group_slot >= LLE_SPARK_GROUP_CAPACITY ||
            particle_index >= LLE_SPARK_PARTICLES_PER_GROUP) {
        return false;
    }
    particle = &sim->groups[group_slot].particles[particle_index];
    out_snapshot->x = particle->x;
    out_snapshot->y = particle->y;
    out_snapshot->velocity_x = particle->velocity_x;
    out_snapshot->velocity_y = particle->velocity_y;
    out_snapshot->acceleration_x = particle->acceleration_x;
    out_snapshot->acceleration_y = particle->acceleration_y;
    out_snapshot->size = particle->size;
    out_snapshot->alpha = particle->alpha;
    out_snapshot->lifetime_ticks = particle->lifetime;
    out_snapshot->max_lifetime_ticks = particle->max_lifetime;
    out_snapshot->boundary_count = particle->boundary_count;
    out_snapshot->active = particle->active;
    out_snapshot->fast_hide = particle->fast_hide;
    out_snapshot->twinkle = particle->twinkle;
    out_snapshot->animation_type = particle->animation_type;
    out_snapshot->edge_direction = particle->edge_direction;
    return true;
}

size_t lle_spark_sim_export_draw_data(
        const LleSparkSim *sim,
        float presentation_fraction,
        float *positions_xy,
        float *initial_positions_xy,
        float *sizes,
        float *alphas,
        size_t point_capacity,
        LleSparkDrawGroup *groups,
        size_t group_capacity) {
    size_t required_points;
    size_t group_slot;
    size_t exported_group_index = 0;
    size_t output_index = 0;

    if (sim == NULL) {
        return 0;
    }
    const float presentation_step =
            isfinite(presentation_fraction)
                    ? fmaxf(0.0f, fminf(1.0f, presentation_fraction)) *
                            LLE_SPARK_FIXED_DT
                    : 0.0f;
    required_points =
            sim->active_order_count * LLE_SPARK_PARTICLES_PER_GROUP;

    if (positions_xy == NULL ||
            initial_positions_xy == NULL ||
            sizes == NULL ||
            alphas == NULL ||
            groups == NULL) {
        return required_points;
    }
    if (point_capacity < required_points ||
            group_capacity < sim->active_order_count) {
        return required_points;
    }

    for (group_slot = 0;
            group_slot < LLE_SPARK_GROUP_CAPACITY;
            ++group_slot) {
        const LleSparkGroup *group = &sim->groups[group_slot];
        size_t particle_index;

        if (!group->active) {
            continue;
        }

        groups[exported_group_index].first_point = output_index;
        groups[exported_group_index].point_count =
                LLE_SPARK_PARTICLES_PER_GROUP;
        groups[exported_group_index].group_slot = (uint8_t) group_slot;
        ++exported_group_index;

        for (particle_index = 0;
                particle_index < LLE_SPARK_PARTICLES_PER_GROUP;
                ++particle_index, ++output_index) {
            const LleSparkParticle *particle =
                    &group->particles[particle_index];
            positions_xy[output_index * 2u] =
                    particle->x + particle->velocity_x * presentation_step;
            positions_xy[output_index * 2u + 1u] =
                    particle->y + particle->velocity_y * presentation_step;
            initial_positions_xy[output_index * 2u] =
                    particle->initial_x;
            initial_positions_xy[output_index * 2u + 1u] =
                    particle->initial_y;
            sizes[output_index] =
                    particle->active ? particle->size : 0.0f;
            alphas[output_index] =
                    particle->active ? particle->alpha : 0.0f;
        }
    }
    return required_points;
}
