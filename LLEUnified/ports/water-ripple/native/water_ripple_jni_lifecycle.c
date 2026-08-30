#include "ripple_core.h"
#include "ripple_gles_pipeline.h"
#include "ripple_gles_overlay.h"

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <limits.h>
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "LLE64Ripple"
#define ERROR_SIZE 512

/*
 * Samsung's ARM32 library exposes one process-global Fluid. Keep the same external
 * singleton semantics for now; S3Arm64RippleEffectView enforces one Java owner.
 */
static LleRippleGles g_gles;
static LleRippleOverlay g_overlay;
static LleRippleSurface g_ink_density[2];
static LleRippleSurface g_ink_velocity;
static bool g_initialized;
static bool g_overlay_initialized;
static bool g_ink_initialized;
static unsigned int g_ink_density_index;
static jint g_ink_viewport_width;
static jint g_ink_viewport_height;
static jint g_ink_fluid_width;
static jint g_ink_fluid_height;
static float *g_ink_flow_x;
static float *g_ink_flow_y;
static float *g_ink_flow_tmp_x;
static float *g_ink_flow_tmp_y;
static float *g_ink_pressure[2];
static float *g_ink_divergence;
static uint8_t *g_ink_velocity_pixels;
static int g_ink_touch_state;
static int g_ink_touch_mode;
static int g_ink_press_step;
static int g_ink_motion_count;
static int g_ink_idle_frames;
static int g_ink_last_action;
static int g_ink_debug_frames;
static bool g_ink_finger_down;
static float g_ink_current_x;
static float g_ink_current_y;
static float g_ink_previous_x;
static float g_ink_previous_y;
static float g_ink_anchor_x;
static float g_ink_anchor_y;
static uint32_t g_ink_random_state = 0x4e34494eU;
static char g_last_error[ERROR_SIZE];

typedef struct InkStockPreset {
    int mode;
    float add_radius;
    float add_impulse;
    float velocity_radius;
    float velocity_impulse;
    float velocity_dissipation;
    float density_dissipation;
    float backward_step;
    bool add_ink;
    bool add_velocity_segment;
} InkStockPreset;

static void set_last_error(const char *message);
static bool upload_ink_velocity(void);

static void free_ink_fluid(void) {
    free(g_ink_flow_x);
    free(g_ink_flow_y);
    free(g_ink_flow_tmp_x);
    free(g_ink_flow_tmp_y);
    free(g_ink_pressure[0]);
    free(g_ink_pressure[1]);
    free(g_ink_divergence);
    free(g_ink_velocity_pixels);
    g_ink_flow_x = NULL;
    g_ink_flow_y = NULL;
    g_ink_flow_tmp_x = NULL;
    g_ink_flow_tmp_y = NULL;
    g_ink_pressure[0] = NULL;
    g_ink_pressure[1] = NULL;
    g_ink_divergence = NULL;
    g_ink_velocity_pixels = NULL;
    g_ink_fluid_width = 0;
    g_ink_fluid_height = 0;
}

static void reset_ink_touch_state(void) {
    g_ink_touch_state = 0;
    g_ink_touch_mode = 0;
    g_ink_press_step = 0;
    g_ink_motion_count = 0;
    g_ink_idle_frames = 0;
    g_ink_last_action = 3;
    g_ink_debug_frames = 0;
    g_ink_finger_down = false;
    g_ink_current_x = 0.0f;
    g_ink_current_y = 0.0f;
    g_ink_previous_x = 0.0f;
    g_ink_previous_y = 0.0f;
    g_ink_anchor_x = 0.0f;
    g_ink_anchor_y = 0.0f;
    g_ink_random_state = 0x4e34494eU;
}

static void abandon_ink_surfaces(void) {
    free_ink_fluid();
    memset(g_ink_density, 0, sizeof(g_ink_density));
    memset(&g_ink_velocity, 0, sizeof(g_ink_velocity));
    g_ink_initialized = false;
    g_ink_density_index = 0U;
    g_ink_viewport_width = 0;
    g_ink_viewport_height = 0;
    reset_ink_touch_state();
}

static float clamp_float(float value, float minimum, float maximum) {
    if (value < minimum) return minimum;
    if (value > maximum) return maximum;
    return value;
}

static float sample_fluid(const float *field, float x, float y) {
    const int width = g_ink_fluid_width;
    const int height = g_ink_fluid_height;
    x = clamp_float(x, 0.5f, (float) width - 1.5f);
    y = clamp_float(y, 0.5f, (float) height - 1.5f);
    const int x0 = (int) floorf(x);
    const int y0 = (int) floorf(y);
    const int x1 = x0 + 1;
    const int y1 = y0 + 1;
    const float tx = x - (float) x0;
    const float ty = y - (float) y0;
    const float a = field[y0 * width + x0]
            + tx * (field[y0 * width + x1] - field[y0 * width + x0]);
    const float b = field[y1 * width + x0]
            + tx * (field[y1 * width + x1] - field[y1 * width + x0]);
    return a + ty * (b - a);
}

static float next_ink_jitter(void) {
    /* Stock calls rand() twice and maps each result to roughly +/-5 screen pixels. */
    g_ink_random_state = g_ink_random_state * 1664525U + 1013904223U;
    return ((float) (g_ink_random_state & 0x00ffffffU) / 16777216.0f - 0.5f) * 10.0f;
}

static InkStockPreset current_ink_preset(void) {
    InkStockPreset preset;
    memset(&preset, 0, sizeof(preset));
    preset.mode = g_ink_touch_mode;
    /* Live Note4 post-release tail: velocity remains at 0.98. Density stays
     * at the active 0.99 for 35 idle draws, then switches to 0.9625. */
    preset.velocity_dissipation = 0.98f;
    preset.density_dissipation =
            g_ink_idle_frames >= 35 ? 0.9625f : 0.99f;
    preset.backward_step = 1.0f;

    if (g_ink_touch_state == 1) {
        const float step = (float) g_ink_press_step;
        preset.mode = -1;
        preset.add_radius = step * 8.0f;
        preset.add_impulse = 200.0f;
        preset.velocity_radius = 40.0f;
        preset.velocity_impulse = g_ink_press_step < 5 ? step * 12.0f : 0.0f;
        preset.velocity_dissipation = 0.98f;
        preset.density_dissipation = 0.99f;
        preset.backward_step = g_ink_press_step < 10 ? step * 0.1f : 1.0f;
        preset.add_ink = g_ink_press_step < 10;
        return preset;
    }

    if (g_ink_touch_state != 2) {
        return preset;
    }

    preset.add_ink = true;
    if (g_ink_touch_mode == 2) {
        preset.add_radius = 30.0f;
        preset.add_impulse = 40.0f;
        preset.velocity_radius = 4.0f;
        preset.velocity_impulse = 20.0f;
        preset.velocity_dissipation = 0.96f;
        preset.density_dissipation = 0.99f;
        preset.add_velocity_segment = true;
    } else if (g_ink_touch_mode == 1) {
        preset.add_radius = 25.0f;
        preset.add_impulse = 150.0f;
        preset.velocity_radius = 20.0f;
        preset.velocity_impulse = 10.0f;
        preset.velocity_dissipation = 0.94f;
        preset.density_dissipation = 0.99f;
    } else {
        /* Accessibility's renderer does not receive MotionEvent pressure. Samsung's
         * normal pressure is 1.0, therefore q = pressure^2 + 0.2 = 1.2. */
        const float pressure_q = 1.2f;
        preset.mode = 0;
        preset.add_radius = 40.0f * pressure_q;
        preset.add_impulse = 100.0f;
        preset.velocity_radius = 20.0f;
        preset.velocity_impulse = 45.0f * pressure_q;
        preset.velocity_dissipation = 0.90f;
        preset.density_dissipation = 0.99f;
    }
    return preset;
}

static void set_fluid_boundary(float *field) {
    const int width = g_ink_fluid_width;
    const int height = g_ink_fluid_height;
    if (field == NULL || width < 2 || height < 2) return;
    for (int x = 0; x < width; ++x) {
        field[x] = 0.0f;
        field[(height - 1) * width + x] = 0.0f;
    }
    for (int y = 1; y < height - 1; ++y) {
        field[y * width] = 0.0f;
        field[y * width + width - 1] = 0.0f;
    }
}

static void add_fast_ink_segment_velocity(const InkStockPreset *preset) {
    if (preset == NULL || !preset->add_velocity_segment) return;
    const int width = g_ink_fluid_width;
    const int height = g_ink_fluid_height;
    const float segment_x = g_ink_current_x - g_ink_previous_x;
    const float segment_y = g_ink_current_y - g_ink_previous_y;
    const float segment_length = sqrtf(segment_x * segment_x + segment_y * segment_y);
    if (segment_length <= 0.001f) return;
    const float direction_x = segment_x / segment_length;
    const float direction_y = segment_y / segment_length;
    for (int y = 1; y < height - 1; ++y) {
        const float screen_y = ((float) y + 0.5f)
                * (float) g_ink_viewport_height / (float) height;
        for (int x = 1; x < width - 1; ++x) {
            const float screen_x = ((float) x + 0.5f)
                    * (float) g_ink_viewport_width / (float) width;
            const float relative_x = screen_x - g_ink_previous_x;
            const float relative_y = screen_y - g_ink_previous_y;
            const float along = direction_x * relative_x + direction_y * relative_y;
            if (along <= 0.0f || along >= segment_length) continue;
            const float projected_x = g_ink_previous_x + along * direction_x;
            const float projected_y = g_ink_previous_y + along * direction_y;
            const float from_projection_x = screen_x - projected_x;
            const float from_projection_y = screen_y - projected_y;
            const float distance = sqrtf(from_projection_x * from_projection_x
                    + from_projection_y * from_projection_y);
            if (distance >= preset->add_radius) continue;
            const int index = y * width + x;
            g_ink_flow_x[index] += distance * 0.1f * (
                    from_projection_x + g_ink_current_x - projected_x);
            g_ink_flow_y[index] += distance * 0.1f * (
                    from_projection_y + g_ink_current_y - projected_y);
        }
    }
}

static void project_ink_velocity(const InkStockPreset *preset) {
    const int width = g_ink_fluid_width;
    const int height = g_ink_fluid_height;
    const size_t count = (size_t) width * (size_t) height;
    memset(g_ink_pressure[0], 0, count * sizeof(float));
    memset(g_ink_pressure[1], 0, count * sizeof(float));
    memset(g_ink_divergence, 0, count * sizeof(float));
    const float impulse_center_x = g_ink_current_x + next_ink_jitter();
    const float impulse_center_y = g_ink_current_y + next_ink_jitter();
    for (int y = 1; y < height - 1; ++y) {
        const float screen_y = ((float) y + 0.5f)
                * (float) g_ink_viewport_height / (float) height;
        for (int x = 1; x < width - 1; ++x) {
            const int index = y * width + x;
            g_ink_divergence[index] = 0.2f * (
                    g_ink_flow_x[index + 1] - g_ink_flow_x[index - 1]
                    + g_ink_flow_y[index + width] - g_ink_flow_y[index - width]);
            if (preset != NULL && preset->velocity_impulse > 0.0f) {
                const float screen_x = ((float) x + 0.5f)
                        * (float) g_ink_viewport_width / (float) width;
                const float dx = screen_x - impulse_center_x;
                const float dy = screen_y - impulse_center_y;
                if (dx * dx + dy * dy
                        < preset->velocity_radius * preset->velocity_radius) {
                    g_ink_divergence[index] -= preset->velocity_impulse;
                }
            }
        }
    }

    unsigned int pressure_index = 0U;
    /* Indigo's stock Fluid uses twenty Jacobi iterations. */
    for (int iteration = 0; iteration < 20; ++iteration) {
        const unsigned int destination = 1U - pressure_index;
        float *source = g_ink_pressure[pressure_index];
        float *target = g_ink_pressure[destination];
        for (int y = 1; y < height - 1; ++y) {
            for (int x = 1; x < width - 1; ++x) {
                const int index = y * width + x;
                target[index] = 0.25f * (source[index - 1] + source[index + 1]
                        + source[index - width] + source[index + width]);
                target[index] -= 1.5625f * g_ink_divergence[index];
            }
        }
        set_fluid_boundary(target);
        pressure_index = destination;
    }

    const float *pressure = g_ink_pressure[pressure_index];
    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            const int index = y * width + x;
            g_ink_flow_x[index] -= 0.2f * (
                    pressure[index + 1] - pressure[index - 1]);
            g_ink_flow_y[index] -= 0.2f * (
                    pressure[index + width] - pressure[index - width]);
        }
    }
    set_fluid_boundary(g_ink_flow_x);
    set_fluid_boundary(g_ink_flow_y);
}

static void advance_ink_velocity(const InkStockPreset *preset) {
    const int width = g_ink_fluid_width;
    const int height = g_ink_fluid_height;
    add_fast_ink_segment_velocity(preset);
    /* Stock performs one semi-Lagrangian velocity advection with dt=0.25. */
    const float time_step_cells = 0.25f;
    const float dissipation = preset == NULL ? 0.90f : preset->velocity_dissipation;
    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            const int index = y * width + x;
            const float previous_x = (float) x - time_step_cells * g_ink_flow_x[index];
            const float previous_y = (float) y - time_step_cells * g_ink_flow_y[index];
            g_ink_flow_tmp_x[index] = dissipation * sample_fluid(
                    g_ink_flow_x, previous_x, previous_y);
            g_ink_flow_tmp_y[index] = dissipation * sample_fluid(
                    g_ink_flow_y, previous_x, previous_y);
        }
    }
    set_fluid_boundary(g_ink_flow_tmp_x);
    set_fluid_boundary(g_ink_flow_tmp_y);
    float *swap = g_ink_flow_x;
    g_ink_flow_x = g_ink_flow_tmp_x;
    g_ink_flow_tmp_x = swap;
    swap = g_ink_flow_y;
    g_ink_flow_y = g_ink_flow_tmp_y;
    g_ink_flow_tmp_y = swap;
    project_ink_velocity(preset);
    if (g_ink_debug_frames < 20) {
        float maximum = 0.0f;
        for (int index = 0; index < width * height; ++index) {
            maximum = fmaxf(maximum, fabsf(g_ink_flow_x[index]));
            maximum = fmaxf(maximum, fabsf(g_ink_flow_y[index]));
        }
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                "Indigo solver frame=%d state=%d mode=%d step=%d Rv=%.1f Iv=%.1f maxV=%.3f",
                g_ink_debug_frames, g_ink_touch_state,
                preset == NULL ? 0 : preset->mode, g_ink_press_step,
                preset == NULL ? 0.0f : preset->velocity_radius,
                preset == NULL ? 0.0f : preset->velocity_impulse,
                maximum);
        ++g_ink_debug_frames;
    }
}

static uint8_t encode_velocity_high(float value) {
    value = clamp_float(value, -127.0f, 127.0f) + 127.0f;
    const int high = (int) floorf(value);
    return (uint8_t) (high < 0 ? 0 : (high > 255 ? 255 : high));
}

static uint8_t encode_velocity_low(float value) {
    value = clamp_float(value, -127.0f, 127.0f) + 127.0f;
    const float fractional = value - floorf(value);
    /* Samsung's RGBA pack truncates the fractional byte; it does not round. */
    const int low = (int) floorf(fractional * 255.0f);
    return (uint8_t) (low < 0 ? 0 : (low > 255 ? 255 : low));
}

static bool upload_ink_velocity(void) {
    const int width = g_ink_fluid_width;
    const int height = g_ink_fluid_height;
    if (g_ink_velocity_pixels == NULL || g_ink_velocity.texture == 0) return false;
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const int index = y * width + x;
            uint8_t *pixel = g_ink_velocity_pixels + (size_t) index * 4U;
            pixel[0] = encode_velocity_high(g_ink_flow_x[index]);
            pixel[1] = encode_velocity_low(g_ink_flow_x[index]);
            pixel[2] = encode_velocity_high(g_ink_flow_y[index]);
            pixel[3] = encode_velocity_low(g_ink_flow_y[index]);
        }
    }
    glBindTexture(GL_TEXTURE_2D, g_ink_velocity.texture);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
            GL_RGBA, GL_UNSIGNED_BYTE, g_ink_velocity_pixels);
    glBindTexture(GL_TEXTURE_2D, 0);
    const GLenum code = glGetError();
    if (code != GL_NO_ERROR) {
        char error[ERROR_SIZE];
        (void) snprintf(error, sizeof(error),
                "Indigo velocity upload glError=0x%04x", (unsigned int) code);
        set_last_error(error);
        return false;
    }
    return true;
}

static void destroy_ink_surfaces(void) {
    if (g_ink_initialized) {
        lle_ripple_gles_destroy_surface(&g_ink_density[0]);
        lle_ripple_gles_destroy_surface(&g_ink_density[1]);
        lle_ripple_gles_destroy_surface(&g_ink_velocity);
    }
    abandon_ink_surfaces();
}

static void clear_last_error(void) {
    g_last_error[0] = '\0';
}

static void set_last_error(const char *message) {
    if (message == NULL) {
        message = "unknown Water Ripple error";
    }
    (void) snprintf(g_last_error, sizeof(g_last_error), "%s", message);
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
}

static bool valid_texture_slot(jint slot) {
    return slot >= (jint) LLE_RIPPLE_TEXTURE_BACKGROUND
            && slot <= (jint) LLE_RIPPLE_TEXTURE_CAUSTIC_2;
}

JNIEXPORT jint JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeBridgeVersion(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    return 3;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeInitGpu(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    clear_last_error();

    destroy_ink_surfaces();
    if (g_overlay_initialized) {
        /* The caller guarantees that the owning context is current here. */
        lle_ripple_overlay_destroy(&g_overlay);
        g_overlay_initialized = false;
    }
    if (g_initialized) {
        /* The caller guarantees that the owning context is current here. */
        lle_ripple_gles_destroy(&g_gles);
        g_initialized = false;
    }
    if (!lle_ripple_gles_init(&g_gles, g_last_error, sizeof(g_last_error))) {
        if (g_last_error[0] == '\0') {
            set_last_error("lle_ripple_gles_init failed");
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
        }
        lle_ripple_gles_abandon(&g_gles);
        return JNI_FALSE;
    }
    g_initialized = true;
    if (!lle_ripple_overlay_init(&g_overlay, g_last_error, sizeof(g_last_error))) {
        if (g_last_error[0] == '\0') {
            set_last_error("lle_ripple_overlay_init failed");
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
        }
        lle_ripple_overlay_abandon(&g_overlay);
        lle_ripple_gles_destroy(&g_gles);
        g_initialized = false;
        return JNI_FALSE;
    }
    g_overlay_initialized = true;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeAbandonGpu(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    abandon_ink_surfaces();
    lle_ripple_overlay_abandon(&g_overlay);
    lle_ripple_gles_abandon(&g_gles);
    g_overlay_initialized = false;
    g_initialized = false;
    clear_last_error();
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeDestroyGpu(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    destroy_ink_surfaces();
    if (g_overlay_initialized) {
        lle_ripple_overlay_destroy(&g_overlay);
    } else {
        lle_ripple_overlay_abandon(&g_overlay);
    }
    if (g_initialized) {
        lle_ripple_gles_destroy(&g_gles);
    } else {
        lle_ripple_gles_abandon(&g_gles);
    }
    g_overlay_initialized = false;
    g_initialized = false;
    clear_last_error();
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeInitInk(
        JNIEnv *env,
        jclass clazz,
        jint viewport_width,
        jint viewport_height) {
    (void) env;
    (void) clazz;
    clear_last_error();
    if (!g_initialized || viewport_width <= 0 || viewport_height <= 0) {
        set_last_error("invalid Indigo surface initialization state");
        return JNI_FALSE;
    }

    destroy_ink_surfaces();
    const GLsizei density_width = viewport_width >= viewport_height ? 1024 : 512;
    const GLsizei density_height = viewport_width >= viewport_height ? 512 : 1024;
    const GLsizei fluid_width = viewport_width / 12;
    const GLsizei fluid_height = viewport_height / 12;
    if (fluid_width < 8 || fluid_height < 8) {
        set_last_error("Indigo velocity grid is too small");
        return JNI_FALSE;
    }
    if (!lle_ripple_gles_create_surface(
            &g_ink_velocity, fluid_width, fluid_height,
            g_last_error, sizeof(g_last_error))
            || !lle_ripple_gles_create_surface(
            &g_ink_density[0], density_width, density_height,
            g_last_error, sizeof(g_last_error))
            || !lle_ripple_gles_create_surface(
            &g_ink_density[1], density_width, density_height,
            g_last_error, sizeof(g_last_error))) {
        lle_ripple_gles_destroy_surface(&g_ink_density[0]);
        lle_ripple_gles_destroy_surface(&g_ink_density[1]);
        lle_ripple_gles_destroy_surface(&g_ink_velocity);
        abandon_ink_surfaces();
        if (g_last_error[0] == '\0') {
            set_last_error("Indigo density surface creation failed");
        }
        return JNI_FALSE;
    }

    const size_t fluid_count = (size_t) fluid_width * (size_t) fluid_height;
    g_ink_flow_x = (float *) calloc(fluid_count, sizeof(float));
    g_ink_flow_y = (float *) calloc(fluid_count, sizeof(float));
    g_ink_flow_tmp_x = (float *) calloc(fluid_count, sizeof(float));
    g_ink_flow_tmp_y = (float *) calloc(fluid_count, sizeof(float));
    g_ink_pressure[0] = (float *) calloc(fluid_count, sizeof(float));
    g_ink_pressure[1] = (float *) calloc(fluid_count, sizeof(float));
    g_ink_divergence = (float *) calloc(fluid_count, sizeof(float));
    g_ink_velocity_pixels = (uint8_t *) malloc(fluid_count * 4U);
    if (g_ink_flow_x == NULL || g_ink_flow_y == NULL
            || g_ink_flow_tmp_x == NULL || g_ink_flow_tmp_y == NULL
            || g_ink_pressure[0] == NULL || g_ink_pressure[1] == NULL
            || g_ink_divergence == NULL || g_ink_velocity_pixels == NULL) {
        lle_ripple_gles_destroy_surface(&g_ink_density[0]);
        lle_ripple_gles_destroy_surface(&g_ink_density[1]);
        lle_ripple_gles_destroy_surface(&g_ink_velocity);
        abandon_ink_surfaces();
        set_last_error("Indigo velocity solver allocation failed");
        return JNI_FALSE;
    }
    g_ink_fluid_width = fluid_width;
    g_ink_fluid_height = fluid_height;
    lle_ripple_gles_clear_surface(&g_ink_density[0], 0.0f, 0.0f, 0.0f, 0.0f);
    lle_ripple_gles_clear_surface(&g_ink_density[1], 0.0f, 0.0f, 0.0f, 0.0f);
    g_ink_initialized = true;
    g_ink_density_index = 0U;
    g_ink_viewport_width = viewport_width;
    g_ink_viewport_height = viewport_height;
    reset_ink_touch_state();
    if (!upload_ink_velocity()) {
        destroy_ink_surfaces();
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeResetInk(
        JNIEnv *env,
        jclass clazz) {
    (void) env;
    (void) clazz;
    if (!g_ink_initialized) {
        return;
    }
    lle_ripple_gles_clear_surface(&g_ink_density[0], 0.0f, 0.0f, 0.0f, 0.0f);
    lle_ripple_gles_clear_surface(&g_ink_density[1], 0.0f, 0.0f, 0.0f, 0.0f);
    const size_t fluid_count = (size_t) g_ink_fluid_width * (size_t) g_ink_fluid_height;
    memset(g_ink_flow_x, 0, fluid_count * sizeof(float));
    memset(g_ink_flow_y, 0, fluid_count * sizeof(float));
    memset(g_ink_flow_tmp_x, 0, fluid_count * sizeof(float));
    memset(g_ink_flow_tmp_y, 0, fluid_count * sizeof(float));
    memset(g_ink_pressure[0], 0, fluid_count * sizeof(float));
    memset(g_ink_pressure[1], 0, fluid_count * sizeof(float));
    memset(g_ink_divergence, 0, fluid_count * sizeof(float));
    reset_ink_touch_state();
    (void) upload_ink_velocity();
    g_ink_density_index = 0U;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeAdvanceInk(
        JNIEnv *env,
        jclass clazz,
        jfloat center_x,
        jfloat center_y,
        jint drag) {
    (void) env;
    (void) clazz;
    (void) center_x;
    (void) center_y;
    (void) drag;
    clear_last_error();
    if (!g_ink_initialized) {
        set_last_error("Indigo density update requested before initialization");
        return JNI_FALSE;
    }

    const InkStockPreset preset = current_ink_preset();
    /* The stock frame scheduler uploads the solver result from the previous
     * frame before starting the next CPU update. */
    if (!upload_ink_velocity()) {
        return JNI_FALSE;
    }

    /* The origin performs four density advections per rendered frame. */
    for (int pass = 0; pass < 4; ++pass) {
        const unsigned int destination_index = 1U - g_ink_density_index;
        LleRippleAdvectDensityArgs args;
        memset(&args, 0, sizeof(args));
        args.velocity = &g_ink_velocity;
        args.source = &g_ink_density[g_ink_density_index];
        args.destination = &g_ink_density[destination_index];
        args.time_step_x = 0.225f / (float) g_ink_fluid_width;
        args.time_step_y = 0.225f / (float) g_ink_fluid_height;
        args.backward_step_size = preset.backward_step;
        args.dissipation = preset.density_dissipation;
        args.scale_x = (float) g_ink_viewport_width;
        args.scale_y = (float) g_ink_viewport_height;
        args.center_x = g_ink_current_x;
        args.center_y = g_ink_current_y;
        args.drag = preset.mode;
        if (!lle_ripple_gles_advect_density(
                &g_gles, &args, g_last_error, sizeof(g_last_error))) {
            return JNI_FALSE;
        }
        g_ink_density_index = destination_index;
    }

    if (preset.add_ink
            && g_ink_current_x > 10.0f
            && g_ink_current_y > 10.0f
            && g_ink_current_x < (float) g_ink_viewport_width - 10.0f
            && g_ink_current_y < (float) g_ink_viewport_height - 10.0f) {
        const float dx = g_ink_current_x - g_ink_previous_x;
        const float dy = g_ink_current_y - g_ink_previous_y;
        float length = sqrtf(dx * dx + dy * dy);
        if (length <= 0.0f) length = 1.0f;
        const unsigned int destination_index = 1U - g_ink_density_index;
        LleRippleAddInkArgs args;
        memset(&args, 0, sizeof(args));
        args.source = &g_ink_density[g_ink_density_index];
        args.destination = &g_ink_density[destination_index];
        args.scale_x = (float) g_ink_viewport_width;
        args.scale_y = (float) g_ink_viewport_height;
        args.current_x = g_ink_current_x;
        args.current_y = g_ink_current_y;
        args.previous_x = g_ink_previous_x;
        args.previous_y = g_ink_previous_y;
        args.normal_x = dx / length;
        args.normal_y = dy / length;
        args.length = length;
        args.radius = preset.add_radius;
        args.impulse_density = preset.add_impulse;
        args.mode = preset.mode;
        if (!lle_ripple_gles_add_ink(
                &g_gles, &args, g_last_error, sizeof(g_last_error))) {
            return JNI_FALSE;
        }
        g_ink_density_index = destination_index;
    }

    /* Prepare the stock CPU velocity map for the next rendered frame. */
    advance_ink_velocity(&preset);
    if (g_ink_touch_state == 1) {
        ++g_ink_press_step;
        if (g_ink_press_step > 12 && g_ink_last_action != 2) {
            if (g_ink_finger_down) {
                /* The stock view receives stationary ACTION_MOVE samples during
                 * a long press and enters its slow-drag plume. Accessibility can
                 * suppress identical coordinates, so continue that state here. */
                g_ink_touch_state = 2;
                g_ink_touch_mode = 0;
                g_ink_motion_count = 0;
            } else {
                g_ink_touch_state = 0;
                /* Stock retains the press render mode and increments the idle
                 * counter in this same post-UP draw. */
                g_ink_touch_mode = -1;
                g_ink_idle_frames = 1;
            }
        }
    } else if (g_ink_touch_state == 2) {
        ++g_ink_motion_count;
        g_ink_previous_x = g_ink_current_x;
        g_ink_previous_y = g_ink_current_y;
    } else {
        ++g_ink_idle_frames;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeInjectInk(
        JNIEnv *env,
        jclass clazz,
        jfloat current_x,
        jfloat current_y,
        jfloat previous_x,
        jfloat previous_y,
        jint mode) {
    (void) env;
    (void) clazz;
    clear_last_error();
    if (!g_ink_initialized) {
        set_last_error("Indigo injection requested before initialization");
        return JNI_FALSE;
    }

    const float flipped_current_y = (float) g_ink_viewport_height - current_y;
    const float flipped_previous_y = (float) g_ink_viewport_height - previous_y;
    g_ink_last_action = mode;
    g_ink_idle_frames = 0;

    if (mode == 0) {
        g_ink_debug_frames = 0;
        g_ink_finger_down = true;
        g_ink_touch_state = 1;
        g_ink_touch_mode = -1;
        g_ink_press_step = 1;
        g_ink_motion_count = 0;
        g_ink_current_x = current_x;
        g_ink_current_y = flipped_current_y;
        g_ink_previous_x = current_x;
        g_ink_previous_y = flipped_current_y;
        g_ink_anchor_x = current_x;
        g_ink_anchor_y = flipped_current_y;
        return JNI_TRUE;
    }

    if (mode == 3) {
        g_ink_finger_down = false;
        g_ink_touch_state = 0;
        g_ink_touch_mode = 0;
        g_ink_press_step = 0;
        g_ink_motion_count = 0;
        return JNI_TRUE;
    }

    g_ink_current_x = current_x;
    g_ink_current_y = flipped_current_y;
    /* Samsung keeps the previous *rendered* position for AddInk/AddVelocity.
     * MotionEvent history can contain many samples between two GL frames; using
     * the immediately preceding event collapses the stock plume into a dot. */
    if (g_ink_touch_state == 0) {
        g_ink_previous_x = previous_x;
        g_ink_previous_y = flipped_previous_y;
    }

    if (mode == 1) {
        g_ink_finger_down = false;
        const bool short_motion = g_ink_press_step < 12 && g_ink_motion_count < 10;
        if (g_ink_touch_state == 2 && !short_motion) {
            g_ink_press_step = 20;
        }
        if (g_ink_touch_state != 0) g_ink_touch_state = 1;
        return JNI_TRUE;
    }

    const float dx = current_x - g_ink_anchor_x;
    const float dy = flipped_current_y - g_ink_anchor_y;
    const float distance = sqrtf(dx * dx + dy * dy);
    const bool press_before_transition = g_ink_press_step < 12;
    if (g_ink_touch_state == 1 && press_before_transition && distance <= 2.0f) {
        g_ink_anchor_x = current_x;
        g_ink_anchor_y = flipped_current_y;
        return JNI_TRUE;
    }
    g_ink_touch_state = 2;
    g_ink_touch_mode = distance > 10.0f ? 2 : (distance > 2.0f ? 1 : 0);
    g_ink_anchor_x = current_x;
    g_ink_anchor_y = flipped_current_y;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeUploadBitmap(
        JNIEnv *env,
        jclass clazz,
        jint slot,
        jobject bitmap) {
    (void) clazz;
    clear_last_error();
    if (!g_initialized) {
        set_last_error("bitmap upload requested before GLES init");
        return JNI_FALSE;
    }
    if (!valid_texture_slot(slot) || bitmap == NULL) {
        set_last_error("invalid bitmap upload arguments");
        return JNI_FALSE;
    }

    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    int result = AndroidBitmap_getInfo(env, bitmap, &info);
    if (result != ANDROID_BITMAP_RESULT_SUCCESS) {
        char error[ERROR_SIZE];
        (void) snprintf(error, sizeof(error), "AndroidBitmap_getInfo failed: %d", result);
        set_last_error(error);
        return JNI_FALSE;
    }
    if (info.width == 0 || info.height == 0
            || info.width > (uint32_t) INT_MAX || info.height > (uint32_t) INT_MAX
            || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        set_last_error("bitmap must be non-empty software RGBA_8888");
        return JNI_FALSE;
    }

    const size_t tight_stride = (size_t) info.width * 4U;
    if (info.stride < tight_stride || (size_t) info.height > SIZE_MAX / tight_stride) {
        set_last_error("invalid bitmap stride or dimensions");
        return JNI_FALSE;
    }

    void *locked_pixels = NULL;
    result = AndroidBitmap_lockPixels(env, bitmap, &locked_pixels);
    if (result != ANDROID_BITMAP_RESULT_SUCCESS || locked_pixels == NULL) {
        char error[ERROR_SIZE];
        (void) snprintf(error, sizeof(error), "AndroidBitmap_lockPixels failed: %d", result);
        set_last_error(error);
        return JNI_FALSE;
    }

    const void *upload_pixels = locked_pixels;
    uint8_t *tight_pixels = NULL;
    if ((size_t) info.stride != tight_stride) {
        const size_t byte_count = tight_stride * (size_t) info.height;
        tight_pixels = (uint8_t *) malloc(byte_count);
        if (tight_pixels == NULL) {
            (void) AndroidBitmap_unlockPixels(env, bitmap);
            set_last_error("out of memory while normalizing bitmap stride");
            return JNI_FALSE;
        }
        const uint8_t *source = (const uint8_t *) locked_pixels;
        for (uint32_t row = 0; row < info.height; ++row) {
            memcpy(
                    tight_pixels + (size_t) row * tight_stride,
                    source + (size_t) row * (size_t) info.stride,
                    tight_stride);
        }
        upload_pixels = tight_pixels;
    }

    const bool uploaded = lle_ripple_gles_upload_rgba(
            &g_gles,
            (LleRippleTextureSlot) slot,
            (GLsizei) info.width,
            (GLsizei) info.height,
            upload_pixels,
            g_last_error,
            sizeof(g_last_error));
    free(tight_pixels);
    const int unlock_result = AndroidBitmap_unlockPixels(env, bitmap);
    if (!uploaded) {
        if (g_last_error[0] == '\0') {
            set_last_error("GLES bitmap upload failed");
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
        }
        return JNI_FALSE;
    }
    if (unlock_result != ANDROID_BITMAP_RESULT_SUCCESS) {
        char error[ERROR_SIZE];
        (void) snprintf(error, sizeof(error), "AndroidBitmap_unlockPixels failed: %d", unlock_result);
        set_last_error(error);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeFreeTexture(
        JNIEnv *env,
        jclass clazz,
        jint slot) {
    (void) env;
    (void) clazz;
    if (g_initialized && valid_texture_slot(slot)) {
        lle_ripple_gles_free_texture(&g_gles, (LleRippleTextureSlot) slot);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeRender(
        JNIEnv *env,
        jclass clazz,
        jboolean with_ink,
        jfloatArray vertices_array,
        jfloatArray heights_array,
        jshortArray indices_array,
        jfloatArray mvp_array,
        jint viewport_width,
        jint viewport_height,
        jint mesh_width,
        jint mesh_height,
        jint detail_width,
        jint detail_height,
        jfloat refractive_index,
        jfloat reflection_ratio,
        jfloat alpha_ratio_1,
        jfloat alpha_ratio_2,
        jfloat fresnel_ratio,
        jfloat specular_ratio,
        jfloat exponent_ratio) {
    (void) clazz;
    clear_last_error();
    if (!g_initialized || !g_overlay_initialized
            || (with_ink && !g_ink_initialized)
            || vertices_array == NULL || heights_array == NULL
            || indices_array == NULL || mvp_array == NULL
            || viewport_width <= 0 || viewport_height <= 0) {
        set_last_error("invalid normal render state");
        return JNI_FALSE;
    }

    const jsize vertex_count = (*env)->GetArrayLength(env, vertices_array);
    const jsize height_count = (*env)->GetArrayLength(env, heights_array);
    const jsize index_count = (*env)->GetArrayLength(env, indices_array);
    const jsize matrix_count = (*env)->GetArrayLength(env, mvp_array);
    if (vertex_count <= 0 || height_count <= 0 || index_count <= 0 || matrix_count < 16) {
        set_last_error("invalid normal render arrays");
        return JNI_FALSE;
    }

    jfloat *vertices = (*env)->GetFloatArrayElements(env, vertices_array, NULL);
    jfloat *heights = (*env)->GetFloatArrayElements(env, heights_array, NULL);
    jshort *indices = (*env)->GetShortArrayElements(env, indices_array, NULL);
    jfloat *mvp = (*env)->GetFloatArrayElements(env, mvp_array, NULL);
    if (vertices == NULL || heights == NULL || indices == NULL || mvp == NULL) {
        if (vertices != NULL) {
            (*env)->ReleaseFloatArrayElements(env, vertices_array, vertices, JNI_ABORT);
        }
        if (heights != NULL) {
            (*env)->ReleaseFloatArrayElements(env, heights_array, heights, JNI_ABORT);
        }
        if (indices != NULL) {
            (*env)->ReleaseShortArrayElements(env, indices_array, indices, JNI_ABORT);
        }
        if (mvp != NULL) {
            (*env)->ReleaseFloatArrayElements(env, mvp_array, mvp, JNI_ABORT);
        }
        set_last_error("JNI array acquisition failed");
        return JNI_FALSE;
    }

    LleRippleRenderArgs args;
    memset(&args, 0, sizeof(args));
    args.vertices = vertices;
    args.heights = heights;
    args.indices = (const uint16_t *) indices;
    args.vertex_float_count = (GLsizei) vertex_count;
    args.height_float_count = (GLsizei) height_count;
    args.index_count = (GLsizei) index_count;
    args.mvp = mvp;
    args.viewport_width = (GLsizei) viewport_width;
    args.viewport_height = (GLsizei) viewport_height;
    args.mesh_width = (GLint) mesh_width;
    args.mesh_height = (GLint) mesh_height;
    args.detail_width = (GLint) detail_width;
    args.detail_height = (GLint) detail_height;
    args.refractive_index = refractive_index;
    args.reflection_ratio = reflection_ratio;
    args.alpha_ratio_1 = alpha_ratio_1;
    args.alpha_ratio_2 = alpha_ratio_2;
    args.fresnel_ratio = fresnel_ratio;
    args.specular_ratio = specular_ratio;
    args.exponent_ratio = exponent_ratio;
    args.with_ink = with_ink;
    if (with_ink) {
        args.density = &g_ink_density[g_ink_density_index];
        args.clear_ink = 0.001f;
        args.ink_red = 35.0f / 255.0f;
        args.ink_green = 35.0f / 255.0f;
        args.ink_blue = 85.0f / 255.0f;
        args.ink_intensity_a = 0.02f;
        args.ink_intensity_b = 1.0f;
    }

    LleRippleOverlayOptions overlay_options;
    lle_ripple_overlay_default_options(&overlay_options);
    const bool rendered = lle_ripple_gles_render_normal_variant(
            &g_gles,
            &g_overlay,
            LLE_RIPPLE_NORMAL_COMPOSITE_TRANSPARENT_DELTA,
            &args,
            &overlay_options,
            g_last_error,
            sizeof(g_last_error));
    (*env)->ReleaseFloatArrayElements(env, vertices_array, vertices, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, heights_array, heights, JNI_ABORT);
    (*env)->ReleaseShortArrayElements(env, indices_array, indices, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, mvp_array, mvp, JNI_ABORT);

    if (!rendered) {
        if (g_last_error[0] == '\0') {
            set_last_error("transparent Water Ripple/Indigo render failed");
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", g_last_error);
        }
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_codex_lle_S3RippleLifecycleNative_nativeGetLastError(
        JNIEnv *env,
        jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, g_last_error);
}
