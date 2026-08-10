#include "../native/lle_spark_sim.h"

#include <math.h>
#include <stdint.h>
#include <stdio.h>

enum {
    REFERENCE_HZ = 60,
    STALLED_FRAME_NS = 66666668,
    MAX_ADAPTIVE_FRAME_NS = 35714286
};

static int near(float actual, float expected, float tolerance) {
    return fabsf(actual - expected) <= tolerance;
}

static int snapshot(
        const LleSparkSim *sim,
        LleSparkParticleSnapshot *out_particle) {
    return lle_spark_sim_get_particle(sim, 0U, 0U, out_particle) ? 0 : 1;
}

static void advance_frames(LleSparkSim *sim, int refresh_hz, int frame_count) {
    const float frame_delta = (float) REFERENCE_HZ / (float) refresh_hz;
    for (int frame = 0; frame < frame_count; ++frame) {
        lle_spark_sim_advance_adaptive(sim, frame_delta);
    }
}

static void advance_scaled_frames(
        LleSparkSim *sim,
        int refresh_hz,
        int frame_count,
        float speed_multiplier) {
    const float frame_delta = (float) REFERENCE_HZ / (float) refresh_hz;
    for (int frame = 0; frame < frame_count; ++frame) {
        /* Mirrors JNI: bound is applied to wall time before this scale. */
        lle_spark_sim_advance_adaptive(
                sim, fminf(4.0f, frame_delta * speed_multiplier));
    }
}

static LleSparkSim *new_pressed_sim(void) {
    LleSparkSim *sim = lle_spark_sim_create(1440.0f, 2560.0f, 1U);
    if (sim != NULL) {
        (void) lle_spark_sim_touch_begin(sim, 720.0f, 1280.0f);
    }
    return sim;
}

/* Mirrors SparklingBubblesAppOwnedGlView's adaptive host-clock contract. */
static float host_adaptive_elapsed_seconds(
        uint64_t *last_time_ns,
        int *reset_pending,
        uint64_t now_ns) {
    if (*reset_pending || *last_time_ns == 0U) {
        *reset_pending = 0;
        *last_time_ns = now_ns;
        return 0.0f;
    }
    const uint64_t elapsed_ns = now_ns - *last_time_ns;
    *last_time_ns = now_ns;
    if (elapsed_ns == 0U || elapsed_ns > STALLED_FRAME_NS) {
        return 0.0f;
    }
    return (float) (elapsed_ns < MAX_ADAPTIVE_FRAME_NS
            ? elapsed_ns : MAX_ADAPTIVE_FRAME_NS) / 1000000000.0f;
}

static int verify_one_second_per_refresh(void) {
    static const int refresh_rates[] = {30, 60, 90, 120, 144};
    LleSparkSim *stock = new_pressed_sim();
    LleSparkParticleSnapshot stock_particle;
    if (stock == NULL) {
        fprintf(stderr, "stock simulation allocation failed\n");
        return 1;
    }
    for (int frame = 0; frame < REFERENCE_HZ; ++frame) {
        lle_spark_sim_tick(stock);
    }
    if (snapshot(stock, &stock_particle) != 0) {
        fprintf(stderr, "stock particle snapshot failed\n");
        lle_spark_sim_destroy(stock);
        return 2;
    }

    for (size_t index = 0; index < sizeof(refresh_rates) / sizeof(refresh_rates[0]);
            ++index) {
        const int refresh_hz = refresh_rates[index];
        LleSparkSim *adaptive = new_pressed_sim();
        LleSparkParticleSnapshot particle;
        if (adaptive == NULL) {
            fprintf(stderr, "adaptive simulation allocation failed at %d Hz\n", refresh_hz);
            lle_spark_sim_destroy(stock);
            return 3;
        }
        advance_frames(adaptive, refresh_hz, refresh_hz);
        if (snapshot(adaptive, &particle) != 0 ||
                !near(particle.lifetime_ticks, 60.0f, 0.0002f) ||
                !near(particle.x, stock_particle.x, 3.5f) ||
                !near(particle.y, stock_particle.y, 3.5f)) {
            fprintf(stderr,
                    "one-second cadence mismatch at %d Hz stock=(%.3f,%.3f,%.3f) adaptive=(%.3f,%.3f,%.3f)\n",
                    refresh_hz,
                    stock_particle.lifetime_ticks, stock_particle.x, stock_particle.y,
                    particle.lifetime_ticks, particle.x, particle.y);
            lle_spark_sim_destroy(adaptive);
            lle_spark_sim_destroy(stock);
            return 4;
        }
        lle_spark_sim_destroy(adaptive);
    }
    lle_spark_sim_destroy(stock);
    return 0;
}

static int verify_live_refresh_sequence(void) {
    LleSparkSim *sequence = new_pressed_sim();
    LleSparkSim *reference = new_pressed_sim();
    LleSparkParticleSnapshot particle;
    LleSparkParticleSnapshot reference_particle;
    static const int refresh_rates[] = {60, 120, 30, 96};
    if (sequence == NULL || reference == NULL) {
        fprintf(stderr, "live cadence simulation allocation failed\n");
        lle_spark_sim_destroy(sequence);
        lle_spark_sim_destroy(reference);
        return 10;
    }

    /* Four half-second periods: 60 -> 120 -> 30 -> 96 Hz = two seconds. */
    for (size_t stage = 0;
            stage < sizeof(refresh_rates) / sizeof(refresh_rates[0]);
            ++stage) {
        const int refresh_hz = refresh_rates[stage];
        advance_frames(sequence, refresh_hz, refresh_hz / 2);
        if (snapshot(sequence, &particle) != 0 ||
                !near(particle.lifetime_ticks, 30.0f * (float) (stage + 1U), 0.0003f)
                || !particle.active) {
            fprintf(stderr, "live refresh reset/jump at stage %zu (%d Hz), life=%.3f active=%d\n",
                    stage, refresh_hz, particle.lifetime_ticks, particle.active);
            lle_spark_sim_destroy(sequence);
            lle_spark_sim_destroy(reference);
            return 11;
        }
    }
    advance_frames(reference, 60, 120);
    if (snapshot(reference, &reference_particle) != 0 ||
            !near(particle.lifetime_ticks, reference_particle.lifetime_ticks, 0.0003f)
            || !near(particle.x, reference_particle.x, 6.0f)
            || !near(particle.y, reference_particle.y, 6.0f)) {
        fprintf(stderr,
                "live refresh final mismatch sequence=(%.3f,%.3f,%.3f) reference=(%.3f,%.3f,%.3f)\n",
                particle.lifetime_ticks, particle.x, particle.y,
                reference_particle.lifetime_ticks,
                reference_particle.x, reference_particle.y);
        lle_spark_sim_destroy(sequence);
        lle_spark_sim_destroy(reference);
        return 12;
    }
    lle_spark_sim_unlock(sequence);
    lle_spark_sim_advance_adaptive(sequence, 0.5f);
    if (snapshot(sequence, &particle) != 0 ||
            particle.acceleration_x != 0.0f || particle.acceleration_y != 0.0f) {
        fprintf(stderr, "adaptive unlock acceleration reset failed\n");
        lle_spark_sim_destroy(sequence);
        lle_spark_sim_destroy(reference);
        return 13;
    }
    lle_spark_sim_destroy(sequence);
    lle_spark_sim_destroy(reference);
    return 0;
}

static int verify_speed_multipliers(void) {
    static const float multipliers[] = {1.2f, 1.5f, 2.0f};
    for (size_t index = 0; index < sizeof(multipliers) / sizeof(multipliers[0]);
            ++index) {
        const float multiplier = multipliers[index];
        LleSparkSim *sim = new_pressed_sim();
        LleSparkParticleSnapshot particle;
        if (sim == NULL) {
            fprintf(stderr, "speed multiplier allocation failed\n");
            return 14;
        }
        /* At 30 Hz, 2x deliberately reaches the bounded four logical steps. */
        advance_scaled_frames(sim, 30, 30, multiplier);
        if (snapshot(sim, &particle) != 0 ||
                !near(particle.lifetime_ticks, 60.0f * multiplier, 0.0003f)
                || !particle.active) {
            fprintf(stderr, "speed multiplier mismatch multiplier=%.1f life=%.3f active=%d\n",
                    multiplier, particle.lifetime_ticks, particle.active);
            lle_spark_sim_destroy(sim);
            return 15;
        }
        lle_spark_sim_destroy(sim);
    }
    return 0;
}

static int verify_stall_contract(void) {
    LleSparkSim *sim = new_pressed_sim();
    LleSparkParticleSnapshot particle;
    uint64_t last_time_ns = 0U;
    uint64_t now_ns = UINT64_C(1000000000);
    int reset_pending = 1;
    float elapsed;
    if (sim == NULL) {
        fprintf(stderr, "stall contract simulation allocation failed\n");
        return 20;
    }

    elapsed = host_adaptive_elapsed_seconds(&last_time_ns, &reset_pending, now_ns);
    if (elapsed != 0.0f) {
        fprintf(stderr, "first adaptive frame advanced unexpectedly\n");
        return 21;
    }
    now_ns += UINT64_C(8333333); /* 120 Hz */
    elapsed = host_adaptive_elapsed_seconds(&last_time_ns, &reset_pending, now_ns);
    lle_spark_sim_advance_adaptive(sim, elapsed * (float) REFERENCE_HZ);
    now_ns += UINT64_C(33333333); /* 30 Hz */
    elapsed = host_adaptive_elapsed_seconds(&last_time_ns, &reset_pending, now_ns);
    lle_spark_sim_advance_adaptive(sim, elapsed * (float) REFERENCE_HZ);
    now_ns += UINT64_C(70000000); /* compositor stall: must be discarded */
    elapsed = host_adaptive_elapsed_seconds(&last_time_ns, &reset_pending, now_ns);
    if (elapsed != 0.0f) {
        fprintf(stderr, "stalled frame was not discarded: %.6f\n", elapsed);
        return 22;
    }
    now_ns += UINT64_C(8333333); /* fresh frame: no delayed catch-up */
    elapsed = host_adaptive_elapsed_seconds(&last_time_ns, &reset_pending, now_ns);
    lle_spark_sim_advance_adaptive(sim, elapsed * (float) REFERENCE_HZ);
    if (snapshot(sim, &particle) != 0 ||
            !near(particle.lifetime_ticks, 3.0f, 0.001f)) {
        fprintf(stderr, "stall accumulated backlog: life=%.6f\n", particle.lifetime_ticks);
        return 23;
    }
    lle_spark_sim_destroy(sim);
    return 0;
}

int main(void) {
    int result = verify_one_second_per_refresh();
    if (result != 0) return result;
    result = verify_live_refresh_sequence();
    if (result != 0) return result;
    result = verify_speed_multipliers();
    if (result != 0) return result;
    return verify_stall_contract();
}
