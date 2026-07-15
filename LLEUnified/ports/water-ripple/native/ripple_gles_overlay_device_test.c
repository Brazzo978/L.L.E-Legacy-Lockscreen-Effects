#include "ripple_gles_overlay.h"

#include <EGL/egl.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

enum {
    TEST_WIDTH = 64,
    TEST_HEIGHT = 64,
    GRID_WIDTH = 9,
    GRID_HEIGHT = 9,
    VERTEX_COUNT = GRID_WIDTH * GRID_HEIGHT,
    INDEX_COUNT = (GRID_WIDTH - 1) * (GRID_HEIGHT - 1) * 6
};

typedef struct PixelMetrics {
    unsigned int nonzero_alpha;
    unsigned int border_nonzero_alpha;
    unsigned int rgb_over_alpha;
    unsigned int center_nonzero_alpha;
    uint8_t max_alpha;
} PixelMetrics;

static void build_mesh(float *vertices, uint16_t *indices) {
    size_t vertex_offset = 0;
    for (int row = 0; row < GRID_HEIGHT; ++row) {
        const float y = -1.0f + 2.0f * (float) row / (float) (GRID_HEIGHT - 1);
        for (int column = 0; column < GRID_WIDTH; ++column) {
            const float x = -1.0f + 2.0f * (float) column / (float) (GRID_WIDTH - 1);
            vertices[vertex_offset++] = x;
            vertices[vertex_offset++] = y;
            vertices[vertex_offset++] = 0.0f;
        }
    }

    size_t index_offset = 0;
    for (int row = 0; row < GRID_HEIGHT - 1; ++row) {
        for (int column = 0; column < GRID_WIDTH - 1; ++column) {
            const uint16_t top_left = (uint16_t) (row * GRID_WIDTH + column);
            const uint16_t top_right = (uint16_t) (top_left + 1U);
            const uint16_t bottom_left = (uint16_t) (top_left + GRID_WIDTH);
            const uint16_t bottom_right = (uint16_t) (bottom_left + 1U);
            indices[index_offset++] = top_left;
            indices[index_offset++] = top_right;
            indices[index_offset++] = bottom_right;
            indices[index_offset++] = top_left;
            indices[index_offset++] = bottom_right;
            indices[index_offset++] = bottom_left;
        }
    }
}

static PixelMetrics measure_pixels(const uint8_t *pixels) {
    PixelMetrics metrics;
    memset(&metrics, 0, sizeof(metrics));
    for (int y = 0; y < TEST_HEIGHT; ++y) {
        for (int x = 0; x < TEST_WIDTH; ++x) {
            const size_t offset = ((size_t) y * TEST_WIDTH + (size_t) x) * 4U;
            const uint8_t alpha = pixels[offset + 3U];
            if (alpha != 0U) {
                ++metrics.nonzero_alpha;
                if (x < 4 || y < 4 || x >= TEST_WIDTH - 4 || y >= TEST_HEIGHT - 4) {
                    ++metrics.border_nonzero_alpha;
                }
                if (x >= 20 && x < 44 && y >= 20 && y < 44) {
                    ++metrics.center_nonzero_alpha;
                }
            }
            if (alpha > metrics.max_alpha) {
                metrics.max_alpha = alpha;
            }
            if (pixels[offset] > alpha
                    || pixels[offset + 1U] > alpha
                    || pixels[offset + 2U] > alpha) {
                ++metrics.rgb_over_alpha;
            }
        }
    }
    return metrics;
}

static bool pixels_are_zero(const uint8_t *pixels) {
    for (size_t index = 0; index < (size_t) TEST_WIDTH * TEST_HEIGHT * 4U; ++index) {
        if (pixels[index] != 0U) {
            return false;
        }
    }
    return true;
}

int main(void) {
    int result = 1;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;
    LleRippleGles gles;
    LleRippleOverlay overlay;
    memset(&gles, 0, sizeof(gles));
    memset(&overlay, 0, sizeof(overlay));

    display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY || eglInitialize(display, NULL, NULL) != EGL_TRUE) {
        fprintf(stderr, "FAIL eglInitialize 0x%x\n", eglGetError());
        goto cleanup;
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
    EGLint config_count = 0;
    if (eglChooseConfig(display, config_attributes, &config, 1, &config_count) != EGL_TRUE
            || config_count != 1) {
        fprintf(stderr, "FAIL eglChooseConfig 0x%x count=%d\n", eglGetError(), config_count);
        goto cleanup;
    }
    const EGLint surface_attributes[] = {
            EGL_WIDTH, TEST_WIDTH,
            EGL_HEIGHT, TEST_HEIGHT,
            EGL_NONE
    };
    const EGLint context_attributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    surface = eglCreatePbufferSurface(display, config, surface_attributes);
    context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attributes);
    if (surface == EGL_NO_SURFACE || context == EGL_NO_CONTEXT
            || eglMakeCurrent(display, surface, surface, context) != EGL_TRUE) {
        fprintf(stderr, "FAIL EGL context 0x%x\n", eglGetError());
        goto cleanup;
    }

    char error[2048];
    if (!lle_ripple_gles_init(&gles, error, sizeof(error))) {
        fprintf(stderr, "FAIL pipeline init: %s\n", error);
        goto cleanup;
    }
    if (!lle_ripple_overlay_init(&overlay, error, sizeof(error))) {
        fprintf(stderr, "FAIL overlay init: %s\n", error);
        goto cleanup;
    }

    uint32_t background_pixels[4] = {
            0xffc08040U, 0xffc08040U, 0xffc08040U, 0xffc08040U
    };
    uint32_t water_pixels[4] = {
            0xffffffffU, 0xffffffffU, 0xffffffffU, 0xffffffffU
    };
    /* The upload must discard this unrelated error instead of blaming itself. */
    glEnable((GLenum) 0xdeadU);
    if (!lle_ripple_gles_upload_rgba(
            &gles,
            LLE_RIPPLE_TEXTURE_BACKGROUND,
            2,
            2,
            background_pixels,
            error,
            sizeof(error))
            || !lle_ripple_gles_upload_rgba(
            &gles,
            LLE_RIPPLE_TEXTURE_WATER,
            2,
            2,
            water_pixels,
            error,
            sizeof(error))) {
        fprintf(stderr, "FAIL texture upload: %s\n", error);
        goto cleanup;
    }

    float vertices[VERTEX_COUNT * 3];
    float heights[VERTEX_COUNT * 3];
    uint16_t indices[INDEX_COUNT];
    uint8_t pixels[TEST_WIDTH * TEST_HEIGHT * 4];
    const float identity[16] = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };
    build_mesh(vertices, indices);
    memset(heights, 0, sizeof(heights));

    LleRippleRenderArgs render;
    memset(&render, 0, sizeof(render));
    render.vertices = vertices;
    render.heights = heights;
    render.indices = indices;
    render.vertex_float_count = (GLsizei) (VERTEX_COUNT * 3);
    render.height_float_count = (GLsizei) (VERTEX_COUNT * 3);
    render.index_count = INDEX_COUNT;
    render.mvp = identity;
    render.viewport_width = TEST_WIDTH;
    render.viewport_height = TEST_HEIGHT;
    render.mesh_width = 2;
    render.mesh_height = 2;
    render.detail_width = GRID_WIDTH;
    render.detail_height = GRID_HEIGHT;
    render.refractive_index = 0.93f;
    render.reflection_ratio = 0.13f;
    render.alpha_ratio_1 = 1.0f;
    render.alpha_ratio_2 = 1.0f;
    render.fresnel_ratio = 0.1f;
    render.specular_ratio = 0.5f;
    render.exponent_ratio = 20.0f;
    render.with_ink = false;

    LleRippleOverlayOptions options;
    lle_ripple_overlay_default_options(&options);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (!lle_ripple_gles_render_normal_variant(
            &gles,
            &overlay,
            LLE_RIPPLE_NORMAL_COMPOSITE_TRANSPARENT_DELTA,
            &render,
            &options,
            error,
            sizeof(error))) {
        fprintf(stderr, "FAIL flat overlay render: %s\n", error);
        goto cleanup;
    }
    glReadPixels(0, 0, TEST_WIDTH, TEST_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    if (glGetError() != GL_NO_ERROR || !pixels_are_zero(pixels)) {
        fprintf(stderr, "FAIL flat mesh is not exact RGBA zero\n");
        goto cleanup;
    }

    const size_t center = ((size_t) (GRID_HEIGHT / 2) * GRID_WIDTH
            + (size_t) (GRID_WIDTH / 2)) * 3U;
    heights[center] = 1.0f;
    heights[center + 1U] = 0.0f;
    heights[center + 2U] = 0.0f;
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (!lle_ripple_gles_render_normal_variant(
            &gles,
            &overlay,
            LLE_RIPPLE_NORMAL_COMPOSITE_TRANSPARENT_DELTA,
            &render,
            &options,
            error,
            sizeof(error))) {
        fprintf(stderr, "FAIL impulse overlay render: %s\n", error);
        goto cleanup;
    }
    glReadPixels(0, 0, TEST_WIDTH, TEST_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    if (glGetError() != GL_NO_ERROR) {
        fprintf(stderr, "FAIL impulse glReadPixels\n");
        goto cleanup;
    }
    const PixelMetrics metrics = measure_pixels(pixels);
    if (metrics.nonzero_alpha == 0U
            || metrics.nonzero_alpha >= (unsigned int) (TEST_WIDTH * TEST_HEIGHT)
            || metrics.center_nonzero_alpha == 0U
            || metrics.border_nonzero_alpha != 0U
            || metrics.rgb_over_alpha != 0U) {
        fprintf(
                stderr,
                "FAIL impulse alpha=%u center=%u border=%u rgbOverAlpha=%u maxAlpha=%u\n",
                metrics.nonzero_alpha,
                metrics.center_nonzero_alpha,
                metrics.border_nonzero_alpha,
                metrics.rgb_over_alpha,
                metrics.max_alpha);
        goto cleanup;
    }

    printf(
            "PASS overlay formula=vec4(mask*SamsungResult,mask) defaults=%.3f/%.3f/%.3f "
            "flatNonzero=0 impulseNonzero=%u centerNonzero=%u borderNonzero=%u "
            "rgbOverAlpha=%u maxAlpha=%u\n",
            options.mask_low,
            options.mask_high,
            options.opacity,
            metrics.nonzero_alpha,
            metrics.center_nonzero_alpha,
            metrics.border_nonzero_alpha,
            metrics.rgb_over_alpha,
            metrics.max_alpha);
    result = 0;

cleanup:
    if (display != EGL_NO_DISPLAY && context != EGL_NO_CONTEXT
            && eglGetCurrentContext() == context) {
        lle_ripple_overlay_destroy(&overlay);
        lle_ripple_gles_destroy(&gles);
        (void) eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
    if (display != EGL_NO_DISPLAY && context != EGL_NO_CONTEXT) {
        (void) eglDestroyContext(display, context);
    }
    if (display != EGL_NO_DISPLAY && surface != EGL_NO_SURFACE) {
        (void) eglDestroySurface(display, surface);
    }
    if (display != EGL_NO_DISPLAY) {
        (void) eglTerminate(display);
    }
    return result;
}
