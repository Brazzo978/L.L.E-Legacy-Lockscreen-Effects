#include "watercolor_refresh.h"

#include <math.h>
#include <stddef.h>

static const float kStampAlphaStep = 0.025f;

typedef struct LegacyPhaseSlot {
    const LleWatercolorRefreshStamp *stamp;
    float growth_multiplier;
    float growth_progress;
    float growth_alpha_step;
    float observed_initial_size;
    float observed_size;
    float observed_alpha;
} LegacyPhaseSlot;

/* Compatibility state for the compact three-float public test stamp.  The
 * renderer uses the explicit caller-owned form below. */
static LegacyPhaseSlot g_legacy_phase_slots[32];

static int usable_ticks(float stock_ticks) {
    return isfinite(stock_ticks) && stock_ticks > 0.0f;
}

static float growth_multiplier(float ratio) {
    if (ratio < 2.3f) {
        return 1.075f;
    }
    if (ratio < 2.6f) {
        return 1.025f;
    }
    if (ratio < 2.8f) {
        return 1.005f;
    }
    return 1.0045f;
}

void lle_watercolor_refresh_advance_primary(
        LleWatercolorRefreshStamp *stamp,
        int receives_extra_age,
        float stock_ticks) {
    if (stamp == NULL) {
        return;
    }
    LegacyPhaseSlot *slot = &g_legacy_phase_slots[0];
    for (size_t i = 0; i < sizeof(g_legacy_phase_slots) / sizeof(g_legacy_phase_slots[0]); ++i) {
        if (g_legacy_phase_slots[i].stamp == stamp) {
            slot = &g_legacy_phase_slots[i];
            break;
        }
        if (g_legacy_phase_slots[i].stamp == NULL) {
            slot = &g_legacy_phase_slots[i];
        }
    }
    if (slot->stamp != stamp || slot->observed_initial_size != stamp->initial_size
            || slot->observed_size != stamp->size
            || slot->observed_alpha != stamp->alpha) {
        slot->stamp = stamp;
        slot->growth_multiplier = 0.0f;
        slot->growth_progress = 0.0f;
        slot->growth_alpha_step = 0.0f;
    }
    lle_watercolor_refresh_advance_primary_phased(stamp, receives_extra_age,
            stock_ticks, &slot->growth_multiplier, &slot->growth_progress,
            &slot->growth_alpha_step);
    slot->observed_initial_size = stamp->initial_size;
    slot->observed_size = stamp->size;
    slot->observed_alpha = stamp->alpha;
}

void lle_watercolor_refresh_advance_primary_phased(
        LleWatercolorRefreshStamp *stamp,
        int receives_extra_age,
        float stock_ticks,
        float *pending_multiplier,
        float *pending_progress,
        float *pending_alpha_step) {
    if (stamp == NULL || pending_multiplier == NULL || pending_progress == NULL
            || pending_alpha_step == NULL || stamp->initial_size <= 0.0f
            || !usable_ticks(stock_ticks)) {
        return;
    }

    /* Preserve the recovered branch and operation order exactly at q=1. */
    if (fabsf(stock_ticks - 1.0f) <= 0.000001f) {
        *pending_multiplier = 0.0f;
        *pending_progress = 0.0f;
        *pending_alpha_step = 0.0f;
        if (stamp->size < stamp->initial_size * 2.3f) {
            stamp->size *= 1.075f;
        } else if (stamp->size >= stamp->initial_size * 2.6f) {
            stamp->size *= stamp->size >= stamp->initial_size * 2.8f
                    ? 1.0045f : 1.005f;
        } else {
            stamp->size *= 1.025f;
        }
        if (stamp->size > stamp->initial_size * 2.8f) {
            stamp->alpha += kStampAlphaStep;
        }
        if (receives_extra_age) {
            stamp->alpha += kStampAlphaStep;
        }
        return;
    }

    /*
     * The recovered branch is chosen once at the beginning of each 60 Hz
     * tick.  Re-evaluating it at each fractional display frame crosses a
     * threshold early, so the 90/120/144 Hz envelope diverges from stock.
     * Keep the selected logical tick pending and apportion its growth/alpha
     * smoothly until its original boundary.
    */
    float remaining = stock_ticks;
    while (remaining > 0.000001f) {
        if (!isfinite(*pending_multiplier) || *pending_multiplier <= 0.0f
                || !isfinite(*pending_progress) || *pending_progress < 0.0f
                || *pending_progress >= 1.0f) {
            *pending_multiplier = growth_multiplier(
                    stamp->size / stamp->initial_size);
            *pending_progress = 0.0f;
            *pending_alpha_step = stamp->size * *pending_multiplier
                    > stamp->initial_size * 2.8f ? kStampAlphaStep : 0.0f;
        }
        float segment = 1.0f - *pending_progress;
        if (segment > remaining) {
            segment = remaining;
        }
        stamp->size *= powf(*pending_multiplier, segment);
        stamp->alpha += *pending_alpha_step * segment;
        if (receives_extra_age) {
            stamp->alpha += kStampAlphaStep * segment;
        }
        *pending_progress += segment;
        remaining -= segment;
        if (*pending_progress >= 1.0f - 0.000001f) {
            *pending_multiplier = 0.0f;
            *pending_progress = 0.0f;
            *pending_alpha_step = 0.0f;
        }
    }
}

void lle_watercolor_refresh_advance_secondary(
        LleWatercolorRefreshStamp *stamp,
        float stock_ticks) {
    if (stamp == NULL || !usable_ticks(stock_ticks)) {
        return;
    }
    stamp->size *= powf(1.1f, stock_ticks);
    stamp->alpha = 0.5f;
}

float lle_watercolor_refresh_move_scale(float current, float stock_ticks) {
    if (!isfinite(current) || !usable_ticks(stock_ticks)) {
        return current;
    }
    current += 0.02f * stock_ticks;
    return current < 1.0f ? current : 1.0f;
}

void lle_watercolor_refresh_unlock_gate(
        float *countdown_ticks,
        float *gate,
        float stock_ticks) {
    if (countdown_ticks == NULL || gate == NULL || !usable_ticks(stock_ticks)) {
        return;
    }
    if (*countdown_ticks <= 0.0f) {
        *gate -= 0.06f * stock_ticks;
        *countdown_ticks -= stock_ticks;
        return;
    }
    if (stock_ticks <= *countdown_ticks) {
        *countdown_ticks -= stock_ticks;
        return;
    }
    float decay_ticks = stock_ticks - *countdown_ticks;
    *countdown_ticks = -decay_ticks;
    *gate -= 0.06f * decay_ticks;
}

float lle_watercolor_refresh_fraction(float per_tick_fraction, float stock_ticks) {
    if (!isfinite(per_tick_fraction) || per_tick_fraction <= 0.0f
            || !usable_ticks(stock_ticks)) {
        return 0.0f;
    }
    if (per_tick_fraction >= 1.0f) {
        return 1.0f;
    }
    /* The q=1 adaptive path is the recovered renderer's original shader
     * uniform.  Avoid a powf round-trip so that it remains an exact oracle
     * match as well as the temporal-composition identity. */
    if (fabsf(stock_ticks - 1.0f) <= 0.000001f) {
        return per_tick_fraction;
    }
    return 1.0f - powf(1.0f - per_tick_fraction, stock_ticks);
}
