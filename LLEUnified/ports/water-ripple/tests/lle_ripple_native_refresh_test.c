#include "../native/ripple_core.h"

#include <limits.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

enum {
    DETAIL = 104,
    CELL_COUNT = DETAIL * DETAIL,
    REFERENCE_HZ = 60,
    X_BEGIN = 3,
    Y_BEGIN = 21,
    X_END = 101,
    Y_END = 83
};

#define NANOS_PER_SECOND UINT64_C(1000000000)
#define STALLED_FRAME_NS UINT64_C(66666668)

typedef struct RippleState {
    float velocity[CELL_COUNT];
    float height[CELL_COUNT];
} RippleState;

typedef struct RippleMetrics {
    double energy;
    double height_l1;
    float max_velocity;
    float max_height;
} RippleMetrics;

typedef struct HostAdaptiveClock {
    uint64_t previous_frame_ns;
    int initialized;
} HostAdaptiveClock;

static uint64_t hash_float_array(uint64_t hash, const float *values, size_t count) {
    for (size_t i = 0U; i < count; ++i) {
        uint32_t bits = 0U;
        memcpy(&bits, &values[i], sizeof(bits));
        for (int byte = 0; byte < 4; ++byte) {
            hash ^= (uint8_t) (bits >> (byte * 8));
            hash *= UINT64_C(1099511628211);
        }
    }
    return hash;
}
static uint64_t state_hash(const RippleState *state) {
    uint64_t hash = UINT64_C(1469598103934665603);
    hash = hash_float_array(hash, state->velocity, CELL_COUNT);
    return hash_float_array(hash, state->height, CELL_COUNT);
}

static void init_active_state(RippleState *state) {
    memset(state, 0, sizeof(*state));
    lle_ripple_inject(state->velocity, 50, 50, DETAIL, DETAIL, 0.0f, 0.0f, 2.0f);
}

static int advance_state(RippleState *state, float stock_ticks) {
    return lle_ripple_move_adaptive(
            state->velocity,
            state->height,
            X_BEGIN,
            Y_BEGIN,
            X_END,
            Y_END,
            DETAIL,
            DETAIL,
            true,
            0.94f,
            0.5f,
            stock_ticks) ? 1 : 0;
}

static int state_metrics(const RippleState *state, RippleMetrics *out) {
    RippleMetrics metrics;
    memset(&metrics, 0, sizeof(metrics));
    for (size_t index = 0U; index < CELL_COUNT; ++index) {
        const float velocity = state->velocity[index];
        const float height = state->height[index];
        if (!isfinite(velocity) || !isfinite(height)) {
            return 0;
        }
        metrics.energy += (double) velocity * velocity + (double) height * height;
        metrics.height_l1 += fabs((double) height);
        metrics.max_velocity = fmaxf(metrics.max_velocity, fabsf(velocity));
        metrics.max_height = fmaxf(metrics.max_height, fabsf(height));
    }
    *out = metrics;
    return 1;
}

static int metrics_close(
        const RippleMetrics *actual,
        const RippleMetrics *reference,
        double relative_tolerance) {
    const double energy_scale = fmax(reference->energy, 1.0e-6);
    const double l1_scale = fmax(reference->height_l1, 1.0e-6);
    return fabs(actual->energy - reference->energy) <= energy_scale * relative_tolerance
            && fabs(actual->height_l1 - reference->height_l1)
                    <= l1_scale * relative_tolerance;
}

/* Mirrors S3Arm64RippleEffectView.AdaptiveFrameClock: no accumulated backlog. */
static uint64_t host_adaptive_elapsed_ns(HostAdaptiveClock *clock, uint64_t now_ns) {
    if (!clock->initialized) {
        clock->initialized = 1;
        clock->previous_frame_ns = now_ns;
        return 0U;
    }
    if (now_ns <= clock->previous_frame_ns) {
        clock->previous_frame_ns = now_ns;
        return 0U;
    }
    const uint64_t elapsed_ns = now_ns - clock->previous_frame_ns;
    clock->previous_frame_ns = now_ns;
    return elapsed_ns > STALLED_FRAME_NS ? 0U : elapsed_ns;
}

static float physics_ticks_from_elapsed(uint64_t elapsed_ns) {
    const double ticks = (double) elapsed_ns * (double) REFERENCE_HZ
            / (double) NANOS_PER_SECOND;
    if (fabs(ticks - 1.0) <= 0.0001) {
        return 1.0f;
    }
    return (float) fmin(4.0, ticks);
}

static uint64_t logical_tick_units_from_elapsed(uint64_t elapsed_ns, float physics_ticks) {
    if (physics_ticks <= 0.0f || elapsed_ns == 0U) {
        return 0U;
    }
    if (physics_ticks == 1.0f) {
        return NANOS_PER_SECOND;
    }
    const uint64_t units = lle_ripple_elapsed_ns_to_tick_units(elapsed_ns);
    return units > 4U * NANOS_PER_SECOND ? 4U * NANOS_PER_SECOND : units;
}

static int verify_stock_tick_equivalence(void) {
    RippleState stock;
    RippleState adaptive;
    init_active_state(&stock);
    adaptive = stock;
    for (int frame = 0; frame < 180; ++frame) {
        const int stock_empty = lle_ripple_move(
                stock.velocity, stock.height,
                X_BEGIN, Y_BEGIN, X_END, Y_END,
                DETAIL, DETAIL, true, 0.94f, 0.5f) ? 1 : 0;
        const int adaptive_empty = advance_state(&adaptive, 1.0f);
        if (stock_empty != adaptive_empty
                || memcmp(&stock, &adaptive, sizeof(stock)) != 0) {
            fprintf(stderr, "q=1 changed recovered Ripple state at frame %d\n", frame);
            return 1;
        }
        if (frame == 31) {
            lle_ripple_inject(stock.velocity, 50, 50, DETAIL, DETAIL,
                    8.0f, -6.0f, 1.5f);
            lle_ripple_inject(adaptive.velocity, 50, 50, DETAIL, DETAIL,
                    8.0f, -6.0f, 1.5f);
        }
    }
    printf("Ripple q=1 stock hash=%016llx\n",
            (unsigned long long) state_hash(&adaptive));
    return 0;
}

static int verify_zero_step_noop(void) {
    RippleState state;
    RippleState before;
    init_active_state(&state);
    (void) advance_state(&state, 1.0f);
    before = state;
    if (advance_state(&state, 0.0f) != 0
            || memcmp(&before, &state, sizeof(state)) != 0) {
        fprintf(stderr, "zero adaptive duration mutated or idled a live Ripple\n");
        return 2;
    }
    if (advance_state(&state, NAN) != 0
            || memcmp(&before, &state, sizeof(state)) != 0) {
        fprintf(stderr, "non-finite adaptive duration mutated or idled a live Ripple\n");
        return 3;
    }
    return 0;
}

static int verify_stable_substep_equivalence(void) {
    RippleState legacy;
    RippleState adaptive;
    init_active_state(&legacy);
    adaptive = legacy;
    for (int step = 0; step < 2; ++step) {
        (void) lle_ripple_move(
                legacy.velocity, legacy.height,
                X_BEGIN, Y_BEGIN, X_END, Y_END,
                DETAIL, DETAIL, true, 0.94f, 0.5f);
    }
    (void) advance_state(&adaptive, 2.0f);
    if (memcmp(&legacy, &adaptive, sizeof(legacy)) != 0) {
        fprintf(stderr, "q=2 did not partition into recovered stable substeps\n");
        return 4;
    }
    const float half_damping = lle_ripple_scale_dissipation(0.94f, 0.5f);
    if (lle_ripple_scale_dissipation(0.94f, 1.0f) != 0.94f
            || fabsf(half_damping * half_damping - 0.94f) > 0.000001f) {
        fprintf(stderr, "adaptive damping scale lost its recovered composition\n");
        return 5;
    }
    return 0;
}

static void advance_exact_cadence(
        RippleState *state,
        int refresh_hz,
        int seconds) {
    uint64_t previous_ns = 0U;
    const int frame_count = refresh_hz * seconds;
    for (int frame = 1; frame <= frame_count; ++frame) {
        const uint64_t now_ns = ((uint64_t) frame * NANOS_PER_SECOND) / (uint64_t) refresh_hz;
        const uint64_t elapsed_ns = now_ns - previous_ns;
        previous_ns = now_ns;
        (void) advance_state(state, physics_ticks_from_elapsed(elapsed_ns));
    }
}

static int verify_refresh_cadences(void) {
    static const int refresh_rates[] = {60, 90, 120, 144};
    RippleState reference;
    RippleMetrics reference_metrics;
    init_active_state(&reference);
    advance_exact_cadence(&reference, 60, 1);
    if (!state_metrics(&reference, &reference_metrics)) {
        fprintf(stderr, "60 Hz Ripple state is non-finite\n");
        return 6;
    }

    for (size_t index = 0U; index < sizeof(refresh_rates) / sizeof(refresh_rates[0]); ++index) {
        const int refresh_hz = refresh_rates[index];
        RippleState candidate;
        RippleMetrics candidate_metrics;
        init_active_state(&candidate);
        advance_exact_cadence(&candidate, refresh_hz, 1);
        if (!state_metrics(&candidate, &candidate_metrics)
                || candidate_metrics.max_height > 101.0f
                || candidate_metrics.max_velocity > 1000.0f
                || !metrics_close(&candidate_metrics, &reference_metrics, 0.24)) {
            fprintf(stderr,
                    "Ripple one-second cadence mismatch/non-finite at %d Hz "
                    "E=%.6g/%.6g L1=%.6g/%.6g maxH=%.5g maxV=%.5g\n",
                    refresh_hz,
                    candidate_metrics.energy, reference_metrics.energy,
                    candidate_metrics.height_l1, reference_metrics.height_l1,
                    candidate_metrics.max_height, candidate_metrics.max_velocity);
            return 7;
        }
        printf("Ripple %d Hz: E=%.6g L1=%.6g maxH=%.5g\n",
                refresh_hz, candidate_metrics.energy, candidate_metrics.height_l1,
                candidate_metrics.max_height);
    }
    return 0;
}

static int verify_jitter_and_live_refresh(void) {
    static const uint64_t jitter_ns[] = {UINT64_C(5500000), UINT64_C(8388000)};
    RippleState jittered;
    RippleState regular;
    RippleState live;
    RippleState reference;
    RippleMetrics jittered_metrics;
    RippleMetrics regular_metrics;
    RippleMetrics live_metrics;
    RippleMetrics reference_metrics;
    uint64_t total_ns = 0U;

    init_active_state(&jittered);
    for (int pair = 0; pair < 180; ++pair) {
        for (size_t phase = 0U; phase < 2U; ++phase) {
            total_ns += jitter_ns[phase];
            (void) advance_state(&jittered, physics_ticks_from_elapsed(jitter_ns[phase]));
        }
    }
    init_active_state(&regular);
    for (int frame = 0; frame < 180; ++frame) {
        const uint64_t elapsed_ns = total_ns / UINT64_C(180);
        (void) advance_state(&regular, physics_ticks_from_elapsed(elapsed_ns));
    }
    if (!state_metrics(&jittered, &jittered_metrics)
            || !state_metrics(&regular, &regular_metrics)
            || !metrics_close(&jittered_metrics, &regular_metrics, 0.16)) {
        fprintf(stderr, "jittered 144 Hz Ripple wall-clock motion diverged\n");
        return 8;
    }

    init_active_state(&live);
    static const int refresh_rates[] = {60, 90, 120, 144};
    for (size_t stage = 0U; stage < sizeof(refresh_rates) / sizeof(refresh_rates[0]); ++stage) {
        const int refresh_hz = refresh_rates[stage];
        const int frames = refresh_hz / 2;
        for (int frame = 0; frame < frames; ++frame) {
            (void) advance_state(&live, (float) REFERENCE_HZ / (float) refresh_hz);
        }
    }
    init_active_state(&reference);
    advance_exact_cadence(&reference, 60, 2);
    if (!state_metrics(&live, &live_metrics)
            || !state_metrics(&reference, &reference_metrics)
            || !metrics_close(&live_metrics, &reference_metrics, 0.30)) {
        fprintf(stderr, "live refresh change reset or retimed Ripple\n");
        return 9;
    }
    return 0;
}

static int verify_stall_contract(void) {
    HostAdaptiveClock clock = {0U, 0};
    HostAdaptiveClock rebase_clock = {0U, 0};
    RippleState stalled;
    RippleState reference;
    uint64_t now_ns = NANOS_PER_SECOND;
    uint64_t elapsed_ns;
    const uint64_t nominal_60_a = UINT64_C(16666666);
    const uint64_t nominal_60_b = UINT64_C(16666667);
    if (host_adaptive_elapsed_ns(&rebase_clock, NANOS_PER_SECOND) != 0U
            || host_adaptive_elapsed_ns(&rebase_clock, NANOS_PER_SECOND) != 0U
            || host_adaptive_elapsed_ns(&rebase_clock, NANOS_PER_SECOND - 1U) != 0U
            || host_adaptive_elapsed_ns(&rebase_clock,
                    NANOS_PER_SECOND - 1U + UINT64_C(8333333)) != UINT64_C(8333333)) {
        fprintf(stderr, "adaptive clock did not discard/rebase duplicate or backward frames\n");
        return 10;
    }
    if (physics_ticks_from_elapsed(nominal_60_a) != 1.0f
            || physics_ticks_from_elapsed(nominal_60_b) != 1.0f
            || logical_tick_units_from_elapsed(nominal_60_a, 1.0f) != NANOS_PER_SECOND
            || logical_tick_units_from_elapsed(nominal_60_b, 1.0f) != NANOS_PER_SECOND) {
        fprintf(stderr, "nominal 60 Hz nanoseconds lost q=1/logical-tick equivalence\n");
        return 11;
    }
    init_active_state(&stalled);
    reference = stalled;

    elapsed_ns = host_adaptive_elapsed_ns(&clock, now_ns);
    if (elapsed_ns != 0U) {
        fprintf(stderr, "first adaptive frame advanced unexpectedly\n");
        return 12;
    }
    now_ns += UINT64_C(8333333);
    elapsed_ns = host_adaptive_elapsed_ns(&clock, now_ns);
    (void) advance_state(&stalled, physics_ticks_from_elapsed(elapsed_ns));
    (void) advance_state(&reference, physics_ticks_from_elapsed(elapsed_ns));
    now_ns += UINT64_C(70000000);
    if (host_adaptive_elapsed_ns(&clock, now_ns) != 0U) {
        fprintf(stderr, "stall was not discarded\n");
        return 13;
    }
    now_ns += UINT64_C(8333333);
    elapsed_ns = host_adaptive_elapsed_ns(&clock, now_ns);
    (void) advance_state(&stalled, physics_ticks_from_elapsed(elapsed_ns));
    (void) advance_state(&reference, physics_ticks_from_elapsed(elapsed_ns));
    if (memcmp(&stalled, &reference, sizeof(stalled)) != 0) {
        fprintf(stderr, "discarded stall replayed a Ripple backlog\n");
        return 14;
    }
    return 0;
}

static int verify_stability(void) {
    static const int refresh_rates[] = {60, 90, 120, 144};
    for (size_t rate_index = 0U;
            rate_index < sizeof(refresh_rates) / sizeof(refresh_rates[0]); ++rate_index) {
        const int refresh_hz = refresh_rates[rate_index];
        RippleState state;
        RippleMetrics metrics;
        init_active_state(&state);
        for (int frame = 0; frame < refresh_hz * 8; ++frame) {
            if (frame % (refresh_hz / 2) == 0) {
                const float offset = (float) ((frame / (refresh_hz / 2)) % 7) - 3.0f;
                lle_ripple_inject(state.velocity, 50, 50, DETAIL, DETAIL,
                        offset * 4.0f, -offset * 2.0f, 1.5f);
            }
            (void) advance_state(&state, (float) REFERENCE_HZ / (float) refresh_hz);
        }
        if (!state_metrics(&state, &metrics)
                || metrics.max_height > 101.0f || metrics.max_velocity > 1000.0f) {
            fprintf(stderr, "Ripple became unstable at %d Hz\n", refresh_hz);
            return 13;
        }
    }
    return 0;
}


int main(void) {
    int result = verify_stock_tick_equivalence();
    if (result != 0) return result;
    result = verify_zero_step_noop();
    if (result != 0) return result;
    result = verify_stable_substep_equivalence();
    if (result != 0) return result;
    result = verify_refresh_cadences();
    if (result != 0) return result;
    result = verify_jitter_and_live_refresh();
    if (result != 0) return result;
    result = verify_stall_contract();
    if (result != 0) return result;
    return verify_stability();
}
