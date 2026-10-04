#ifndef LLE_WATERCOLOR_PARAMETERS_H
#define LLE_WATERCOLOR_PARAMETERS_H
#include <math.h>
/* Explicit workshop IDs shared by JNI and host tests. No render size or lifecycle timers. */
typedef struct LleWatercolorParameters {
    float brush_scale;
    float drag_threshold;
    float stamp_spacing;
    float noise_scalar;
    float radial_scalar;
    float saturation;
    float red_saturation;
    float green_saturation;
    float blue_saturation;
    float brightness;
} LleWatercolorParameters;
static inline LleWatercolorParameters lle_watercolor_parameters_defaults(void) {
    const LleWatercolorParameters result = {0.8f, 0.025f, 0.05f, 425.0f, 66.69f, 1.2f, 1.3f, 0.4f, 0.4f, 1.35f};
    return result;
}
static inline void lle_watercolor_parameters_set(LleWatercolorParameters *p, int id, float value) {
    if (p == 0 || !isfinite(value)) return;
    switch(id) {
    case 1: p->brush_scale = fminf(1.6f, fmaxf(0.2f, value)); break;
    case 2: p->drag_threshold = fminf(0.1f, fmaxf(0.005f, value)); break;
    case 3: p->stamp_spacing = fminf(0.12f, fmaxf(0.015f, value)); break;
    case 4: p->noise_scalar = fminf(850.0f, fmaxf(0.0f, value)); break;
    case 5: p->radial_scalar = fminf(150.0f, fmaxf(0.0f, value)); break;
    case 6: p->saturation = fminf(2.5f, fmaxf(0.0f, value)); break;
    case 7: p->red_saturation = fminf(2.5f, fmaxf(0.0f, value)); break;
    case 8: p->green_saturation = fminf(2.5f, fmaxf(0.0f, value)); break;
    case 9: p->blue_saturation = fminf(2.5f, fmaxf(0.0f, value)); break;
    case 10: p->brightness = fminf(2.5f, fmaxf(0.2f, value)); break;
    default: break;
    }
}
#endif
