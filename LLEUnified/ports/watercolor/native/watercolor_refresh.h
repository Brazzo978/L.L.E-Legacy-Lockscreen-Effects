#ifndef LLE_WATERCOLOR_REFRESH_H
#define LLE_WATERCOLOR_REFRESH_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct LleWatercolorRefreshStamp {
    float initial_size;
    float size;
    float alpha;
} LleWatercolorRefreshStamp;

/* Advances the recovered primary-brush operators by 60 Hz tick units. */
void lle_watercolor_refresh_advance_primary(
        LleWatercolorRefreshStamp *stamp,
        int receives_extra_age,
        float stock_ticks);

/* Same update with caller-owned fractional logical-60-tick state. */
void lle_watercolor_refresh_advance_primary_phased(
        LleWatercolorRefreshStamp *stamp,
        int receives_extra_age,
        float stock_ticks,
        float *growth_multiplier,
        float *growth_progress,
        float *growth_alpha_step);

/* Advances the four persistent unlock snapshot stamps. */
void lle_watercolor_refresh_advance_secondary(
        LleWatercolorRefreshStamp *stamp,
        float stock_ticks);

/* Recovered cc8 recovery (+0.02 per stock tick), clamped to one. */
float lle_watercolor_refresh_move_scale(float current, float stock_ticks);

/* Recovered 30-tick hold followed by -0.06 gate decay per stock tick. */
void lle_watercolor_refresh_unlock_gate(
        float *countdown_ticks,
        float *gate,
        float stock_ticks);

/* Converts a per-stock-tick mix fraction to an elapsed-time-equivalent one. */
float lle_watercolor_refresh_fraction(float per_tick_fraction, float stock_ticks);

#ifdef __cplusplus
}
#endif

#endif
