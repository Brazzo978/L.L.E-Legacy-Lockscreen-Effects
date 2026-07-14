#include "ripple_gles_pipeline.h"

#include <EGL/egl.h>

#include <stdio.h>
#include <string.h>

static int fail_pipeline(
        const char *stage,
        const char *error,
        LleRippleGles *gles,
        LleRippleSurface *surfaces,
        size_t surface_count,
        EGLDisplay display,
        EGLSurface surface,
        EGLContext context) {
    fprintf(stderr, "FAIL %s: %s\n", stage, error);
    for (size_t index = 0; index < surface_count; ++index) {
        lle_ripple_gles_destroy_surface(&surfaces[index]);
    }
    lle_ripple_gles_destroy(gles);
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
    return 1;
}

int main(void) {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY || eglInitialize(display, NULL, NULL) != EGL_TRUE) {
        fprintf(stderr, "FAIL eglInitialize 0x%x\n", eglGetError());
        return 1;
    }
    const EGLint config_attributes[] = {
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_NONE
    };
    EGLConfig config;
    EGLint count = 0;
    if (eglChooseConfig(display, config_attributes, &config, 1, &count) != EGL_TRUE || count != 1) {
        fprintf(stderr, "FAIL eglChooseConfig 0x%x count=%d\n", eglGetError(), count);
        eglTerminate(display);
        return 1;
    }
    const EGLint pbuffer_attributes[] = {EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE};
    EGLSurface surface = eglCreatePbufferSurface(display, config, pbuffer_attributes);
    const EGLint context_attributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attributes);
    if (surface == EGL_NO_SURFACE || context == EGL_NO_CONTEXT
            || eglMakeCurrent(display, surface, surface, context) != EGL_TRUE) {
        fprintf(stderr, "FAIL EGL context 0x%x\n", eglGetError());
        if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
        eglTerminate(display);
        return 1;
    }

    char error[2048];
    LleRippleGles gles;
    if (!lle_ripple_gles_init(&gles, error, sizeof(error))) {
        fprintf(stderr, "FAIL pipeline init: %s\n", error);
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(display, context);
        eglDestroySurface(display, surface);
        eglTerminate(display);
        return 1;
    }
    LleRippleSurface framebuffers[3];
    memset(framebuffers, 0, sizeof(framebuffers));
    for (size_t index = 0; index < 3; ++index) {
        if (!lle_ripple_gles_create_surface(&framebuffers[index], 16, 16, error, sizeof(error))) {
            return fail_pipeline(
                    "surface",
                    error,
                    &gles,
                    framebuffers,
                    3,
                    display,
                    surface,
                    context);
        }
    }

    uint32_t pixels[16 * 16];
    for (size_t index = 0; index < sizeof(pixels) / sizeof(pixels[0]); ++index) {
        pixels[index] = 0xffffffffu;
    }
    for (int slot = LLE_RIPPLE_TEXTURE_BACKGROUND;
            slot <= LLE_RIPPLE_TEXTURE_CAUSTIC_2;
            ++slot) {
        if (!lle_ripple_gles_upload_rgba(
                &gles,
                (LleRippleTextureSlot) slot,
                16,
                16,
                pixels,
                error,
                sizeof(error))) {
            return fail_pipeline(
                    "texture upload",
                    error,
                    &gles,
                    framebuffers,
                    3,
                    display,
                    surface,
                    context);
        }
    }

    LleRippleAdvectDensityArgs advect = {
            .velocity = &framebuffers[0],
            .source = &framebuffers[1],
            .destination = &framebuffers[2],
            .time_step_x = 0.01f,
            .time_step_y = 0.01f,
            .backward_step_size = 1.0f,
            .dissipation = 0.94f,
            .scale_x = 16.0f,
            .scale_y = 16.0f,
            .center_x = 8.0f,
            .center_y = 8.0f,
            .drag = 2
    };
    if (!lle_ripple_gles_advect_density(&gles, &advect, error, sizeof(error))) {
        return fail_pipeline(
                "AdvectDensity",
                error,
                &gles,
                framebuffers,
                3,
                display,
                surface,
                context);
    }
    LleRippleAddInkArgs add_ink = {
            .source = &framebuffers[2],
            .destination = &framebuffers[1],
            .scale_x = 16.0f,
            .scale_y = 16.0f,
            .current_x = 8.0f,
            .current_y = 8.0f,
            .previous_x = 6.0f,
            .previous_y = 8.0f,
            .normal_x = 1.0f,
            .normal_y = 0.0f,
            .length = 2.0f,
            .radius = 20.0f,
            .impulse_density = 4.0f,
            .mode = 2
    };
    if (!lle_ripple_gles_add_ink(&gles, &add_ink, error, sizeof(error))) {
        return fail_pipeline(
                "AddInk",
                error,
                &gles,
                framebuffers,
                3,
                display,
                surface,
                context);
    }

    const float vertices[12] = {
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
             1.0f,  1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f
    };
    const float heights[12] = {
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f
    };
    const uint16_t indices[6] = {0, 1, 2, 0, 2, 3};
    const float identity[16] = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };
    LleRippleRenderArgs render = {
            .vertices = vertices,
            .heights = heights,
            .indices = indices,
            .vertex_float_count = 12,
            .height_float_count = 12,
            .index_count = 6,
            .mvp = identity,
            .viewport_width = 16,
            .viewport_height = 16,
            .mesh_width = 2,
            .mesh_height = 2,
            .detail_width = 4,
            .detail_height = 4,
            .refractive_index = 0.93f,
            .reflection_ratio = 0.13f,
            .alpha_ratio_1 = 1.0f,
            .alpha_ratio_2 = 1.0f,
            .fresnel_ratio = 0.1f,
            .specular_ratio = 0.5f,
            .exponent_ratio = 20.0f,
            .with_ink = false,
            .density = &framebuffers[1],
            .clear_ink = 0.7f,
            .ink_red = 1.05f,
            .ink_green = 1.05f,
            .ink_blue = 1.05f,
            .ink_intensity_a = 0.02f,
            .ink_intensity_b = 1.0f
    };
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (!lle_ripple_gles_render(&gles, &render, error, sizeof(error))) {
        return fail_pipeline(
                "normal render",
                error,
                &gles,
                framebuffers,
                3,
                display,
                surface,
                context);
    }
    render.with_ink = true;
    if (!lle_ripple_gles_render(&gles, &render, error, sizeof(error))) {
        return fail_pipeline(
                "ink render",
                error,
                &gles,
                framebuffers,
                3,
                display,
                surface,
                context);
    }
    LleRippleGravityRenderArgs gravity = {
            .base = render,
            .caustic_time_ratio = 0.5f,
            .caustic_time_ratio_2 = 0.5f,
            .caustic_time_mix = 0.5f,
            .reference_point = 40.0f,
            .tex_move = 0.0f,
            .gravity_direction = false,
            .water_brightness = 1.0f
    };
    if (!lle_ripple_gles_render_gravity(&gles, &gravity, error, sizeof(error))) {
        return fail_pipeline(
                "gravity render",
                error,
                &gles,
                framebuffers,
                3,
                display,
                surface,
                context);
    }

    printf(
            "PASS Water Ripple GLES2 programs=%u,%u,%u,%u,%u gravityDeadWaterBrightness=%d\n",
            gles.normal_program,
            gles.ink_program,
            gles.advect_density_program,
            gles.add_ink_program,
            gles.gravity_program,
            gles.gravity_locations.water_brightness);
    for (size_t index = 0; index < 3; ++index) {
        lle_ripple_gles_destroy_surface(&framebuffers[index]);
    }
    lle_ripple_gles_destroy(&gles);
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
    return 0;
}
