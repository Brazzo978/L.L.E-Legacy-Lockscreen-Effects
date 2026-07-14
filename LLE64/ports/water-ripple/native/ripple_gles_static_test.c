#include "ripple_gles_shaders.h"

#include <stdio.h>
#include <string.h>

static int require_text(const char *source, const char *needle, const char *label) {
    if (strstr(source, needle) == NULL) {
        fprintf(stderr, "FAIL %s missing: %s\n", label, needle);
        return 1;
    }
    return 0;
}

int main(void) {
    const int16_t expected[16] = {
            -1, -1, 0, 0,
             1, -1, 1, 0,
            -1,  1, 0, 1,
             1,  1, 1, 1
    };
    if (sizeof(lle_ripple_quad_vertices) != 32
            || memcmp(lle_ripple_quad_vertices, expected, sizeof(expected)) != 0) {
        fprintf(stderr, "FAIL quad GL_SHORT payload\n");
        return 1;
    }

    int failed = 0;
    failed |= require_text(
            lle_ripple_normal_fragment_shader,
            "clamp(specularRatio * pow(NdotHV, exponent), 1.0, 5.5)",
            "normal");
    failed |= require_text(
            lle_ripple_ink_fragment_shader,
            "clamp(specularRatio * pow(NdotHV, exponent), 1.0, 4.5)",
            "ink");
    failed |= require_text(
            lle_ripple_ink_fragment_shader,
            "rippleRGB / (1.0+w*ink_color)",
            "ink");
    failed |= require_text(
            lle_ripple_advect_density_fragment_shader,
            "if( d < 80.0 ) back_step = 0.0075*d",
            "advect-density");
    failed |= require_text(
            lle_ripple_add_ink_fragment_shader,
            "ImpulseDensity*exp(-d*d/(0.8*Radius*Radius))",
            "add-ink");
    failed |= require_text(
            lle_ripple_gravity_vertex_shader,
            "uRefractiveIndex - 1.1",
            "gravity-vertex");
    failed |= require_text(
            lle_ripple_gravity_fragment_shader,
            "0.00208333333333333333333333333333*2.0",
            "gravity-fragment");
    failed |= require_text(
            lle_ripple_gravity_fragment_shader,
            "pow(CausticsResult, 5.0)",
            "gravity-fragment");
    failed |= require_text(
            lle_ripple_normal_fragment_shader,
            "gl_FragColor = vec4(rippleRGB, 1.0)",
            "normal-alpha");
    failed |= require_text(
            lle_ripple_gravity_fragment_shader,
            "gl_FragColor = vec4(rippleRGB,1.0)",
            "gravity-alpha");
    if (failed != 0) {
        return 1;
    }
    printf("PASS Water Ripple GLSL constants and GL_SHORT quad\n");
    return 0;
}
