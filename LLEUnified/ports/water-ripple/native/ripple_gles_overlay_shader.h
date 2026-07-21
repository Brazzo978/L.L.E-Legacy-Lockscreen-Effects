#ifndef LLE64_RIPPLE_GLES_OVERLAY_SHADER_H
#define LLE64_RIPPLE_GLES_OVERLAY_SHADER_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Intentional LLE overlay variant. The Samsung-exact shader strings remain in
 * ripple_gles_shaders.c and are not changed by this compositing experiment.
 */
extern const char lle_ripple_overlay_normal_fragment_shader[];
extern const char lle_ripple_overlay_ink_fragment_shader[];

#ifdef __cplusplus
}
#endif

#endif
