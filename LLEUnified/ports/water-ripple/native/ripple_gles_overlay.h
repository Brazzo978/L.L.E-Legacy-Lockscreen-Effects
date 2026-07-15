#ifndef LLE64_RIPPLE_GLES_OVERLAY_H
#define LLE64_RIPPLE_GLES_OVERLAY_H

#include "ripple_gles_pipeline.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum LleRippleNormalCompositeMode {
    /* Calls the untouched Samsung-exact normal pipeline. */
    LLE_RIPPLE_NORMAL_COMPOSITE_SAMSUNG_EXACT = 0,
    /* Writes a local premultiplied layer for Android/SurfaceFlinger. */
    LLE_RIPPLE_NORMAL_COMPOSITE_TRANSPARENT_DELTA = 1
} LleRippleNormalCompositeMode;

typedef struct LleRippleOverlayOptions {
    float mask_low;
    float mask_high;
    float opacity;
} LleRippleOverlayOptions;

typedef struct LleRippleOverlay {
    GLuint normal_program;
} LleRippleOverlay;

void lle_ripple_overlay_default_options(LleRippleOverlayOptions *options);

bool lle_ripple_overlay_init(
        LleRippleOverlay *overlay,
        char *error,
        size_t error_size);
void lle_ripple_overlay_destroy(LleRippleOverlay *overlay);
void lle_ripple_overlay_abandon(LleRippleOverlay *overlay);

/*
 * The exact mode is a direct dispatch to lle_ripple_gles_render(). The delta
 * mode requires an initialized overlay and accepts normal-mode args only.
 */
bool lle_ripple_gles_render_normal_variant(
        LleRippleGles *gles,
        LleRippleOverlay *overlay,
        LleRippleNormalCompositeMode mode,
        const LleRippleRenderArgs *args,
        const LleRippleOverlayOptions *options,
        char *error,
        size_t error_size);

#ifdef __cplusplus
}
#endif

#endif
