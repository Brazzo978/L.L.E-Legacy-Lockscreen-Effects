#include "../native/watercolor_refresh.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>

static void require_close(const char *name, float actual, float expected, float tolerance) {
    if (!isfinite(actual) || fabsf(actual - expected) > tolerance) {
        fprintf(stderr, "%s: actual=%.8f expected=%.8f tolerance=%.8f\n",
                name, actual, expected, tolerance);
        exit(1);
    }
}

static LleWatercolorRefreshStamp advance_at_hz(int hz, float seconds, int older) {
    LleWatercolorRefreshStamp stamp = {100.0f, 100.0f, 0.0f};
    int frames = (int)lroundf((float)hz * seconds);
    float q = 60.0f / (float)hz;
    for (int frame = 0; frame < frames; ++frame) {
        lle_watercolor_refresh_advance_primary(&stamp, older, q);
    }
    return stamp;
}

static void check_stock_tick(void) {
    LleWatercolorRefreshStamp stamp = {100.0f, 100.0f, 0.0f};
    lle_watercolor_refresh_advance_primary(&stamp, 0, 1.0f);
    require_close("stock first growth", stamp.size, 107.5f, 0.0001f);
    require_close("stock first alpha", stamp.alpha, 0.0f, 0.000001f);
    stamp.size = 281.0f;
    lle_watercolor_refresh_advance_primary(&stamp, 1, 1.0f);
    require_close("stock tail growth", stamp.size, 282.2645f, 0.0002f);
    require_close("stock double age", stamp.alpha, 0.05f, 0.000001f);
}

static void check_wall_clock(void) {
    LleWatercolorRefreshStamp at60 = advance_at_hz(60, 1.0f, 1);
    const int refresh_rates[] = {90, 120, 144};
    for (size_t index = 0; index < sizeof(refresh_rates) / sizeof(refresh_rates[0]);
            ++index) {
        int hz = refresh_rates[index];
        LleWatercolorRefreshStamp candidate = advance_at_hz(hz, 1.0f, 1);
        require_close("adaptive size", candidate.size, at60.size, 1.5f);
        require_close("adaptive alpha", candidate.alpha, at60.alpha, 0.04f);
    }
}

static void check_low_cadence_defensive_path(void) {
    LleWatercolorRefreshStamp at30 = advance_at_hz(30, 1.0f, 1);
    if (!isfinite(at30.size) || !isfinite(at30.alpha)
            || at30.size <= 100.0f || at30.alpha < 0.0f) {
        fprintf(stderr, "30 Hz defensive path produced invalid state\n");
        exit(1);
    }
}

static void check_fractional_composition(void) {
    LleWatercolorRefreshStamp half = {100.0f, 100.0f, 0.0f};
    LleWatercolorRefreshStamp quarters = half;
    lle_watercolor_refresh_advance_primary(&half, 1, 0.5f);
    lle_watercolor_refresh_advance_primary(&quarters, 1, 0.25f);
    lle_watercolor_refresh_advance_primary(&quarters, 1, 0.25f);
    require_close("primary fractional size", quarters.size, half.size, 0.0002f);
    require_close("primary fractional alpha", quarters.alpha, half.alpha, 0.0002f);

    LleWatercolorRefreshStamp boundary = {100.0f, 229.0f, 0.0f};
    LleWatercolorRefreshStamp boundary_split = boundary;
    lle_watercolor_refresh_advance_primary(&boundary, 1, 0.5f);
    lle_watercolor_refresh_advance_primary(&boundary_split, 1, 0.25f);
    lle_watercolor_refresh_advance_primary(&boundary_split, 1, 0.25f);
    require_close("boundary fractional size", boundary_split.size, boundary.size, 0.0003f);
    require_close("boundary fractional alpha", boundary_split.alpha, boundary.alpha, 0.0003f);
}

static void check_secondary_and_shader_scaling(void) {
    LleWatercolorRefreshStamp one = {100.0f, 100.0f, 0.0f};
    LleWatercolorRefreshStamp two = one;
    lle_watercolor_refresh_advance_secondary(&one, 1.0f);
    lle_watercolor_refresh_advance_secondary(&two, 0.5f);
    lle_watercolor_refresh_advance_secondary(&two, 0.5f);
    require_close("secondary size", two.size, one.size, 0.0001f);
    require_close("secondary alpha", two.alpha, 0.5f, 0.000001f);
    float half = lle_watercolor_refresh_fraction(0.03f, 0.5f);
    require_close("relaxation composition", 1.0f - (1.0f - half) * (1.0f - half),
            0.03f, 0.000001f);
}

static void check_clocks(void) {
    float scale60 = 0.5f;
    float scale120 = 0.5f;
    for (int i = 0; i < 30; ++i) {
        scale60 = lle_watercolor_refresh_move_scale(scale60, 1.0f);
    }
    for (int i = 0; i < 60; ++i) {
        scale120 = lle_watercolor_refresh_move_scale(scale120, 0.5f);
    }
    require_close("move recovery", scale120, scale60, 0.00001f);

    float countdown60 = 30.0f;
    float countdown120 = 30.0f;
    float gate60 = 1.0f;
    float gate120 = 1.0f;
    for (int i = 0; i < 40; ++i) {
        lle_watercolor_refresh_unlock_gate(&countdown60, &gate60, 1.0f);
    }
    for (int i = 0; i < 80; ++i) {
        lle_watercolor_refresh_unlock_gate(&countdown120, &gate120, 0.5f);
    }
    require_close("unlock countdown", countdown120, countdown60, 0.00001f);
    require_close("unlock gate", gate120, gate60, 0.00001f);
}

int main(void) {
    check_stock_tick();
    check_wall_clock();
    check_low_cadence_defensive_path();
    check_fractional_composition();
    check_secondary_and_shader_scaling();
    check_clocks();
    puts("PASS watercolor-refresh");
    return 0;
}
