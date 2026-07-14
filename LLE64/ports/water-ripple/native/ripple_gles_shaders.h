#ifndef LLE64_RIPPLE_GLES_SHADERS_H
#define LLE64_RIPPLE_GLES_SHADERS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

extern const int16_t lle_ripple_quad_vertices[16];
extern const char lle_ripple_quad_vertex_shader[];
extern const char lle_ripple_advect_density_fragment_shader[];
extern const char lle_ripple_add_ink_fragment_shader[];
extern const char lle_ripple_normal_vertex_shader[];
extern const char lle_ripple_normal_fragment_shader[];
extern const char lle_ripple_ink_fragment_shader[];
extern const char lle_ripple_gravity_vertex_shader[];
extern const char lle_ripple_gravity_fragment_shader[];

#ifdef __cplusplus
}
#endif

#endif
