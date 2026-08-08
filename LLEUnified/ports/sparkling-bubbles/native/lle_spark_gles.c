#include "lle_spark_gles.h"

#include <android/bitmap.h>
#include <math.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Recovered SPDrawSparklingBubbles point-sprite vertex stage. */
static const char *LLE_SPARK_VERTEX_SHADER =
        "precision mediump float;\n"
        "attribute vec3 aPosition;\n"
        "attribute vec3 aInitPosition;\n"
        "attribute float aPointSize;\n"
        "attribute float aPointAlpha;\n"
        "attribute vec2 aRandUV;\n"
        "uniform mat4 mvpMatrix;\n"
        "varying vec2 vPos;\n"
        "varying float vPointAlpha;\n"
        "void main() {\n"
        "  vec4 updatedPosition = vec4(aPosition.xyz, 1.0);\n"
        "  vPos = aInitPosition.xy;\n"
        "  vPointAlpha = aPointAlpha;\n"
        "  gl_Position = mvpMatrix * updatedPosition;\n"
        "  gl_PointSize = aPointSize;\n"
        "}\n";

/* Recovered verbatim math from SPDrawSparklingBubbles' GLES2 fragment stage. */
static const char *LLE_SPARK_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "uniform sampler2D uBGTexMap;\n"
        "uniform sampler2D uMaskMap;\n"
        "uniform float uInvScWidth;\n"
        "uniform float uInvScHeight;\n"
        "uniform float uCropMaxTexV;\n"
        "varying vec2 vPos;\n"
        "varying float vPointAlpha;\n"
        "void main() {\n"
        "  float PointerAlpha = texture2D(uMaskMap, gl_PointCoord.xy).a"
        " * vPointAlpha;\n"
        "  if (PointerAlpha < 0.005) discard;\n"
        "  vec3 PointerBGColor = texture2D(uBGTexMap,\n"
        "      vec2(vPos.x * uInvScWidth,\n"
        "           uCropMaxTexV - vPos.y * uInvScHeight)).rgb;\n"
        "  vec3 exposureV = PointerBGColor * 1.8;\n"
        "  float Y = 0.299 * exposureV.r + 0.587 * exposureV.g"
        " + 0.144 * exposureV.b;\n"
        "  float Cb = -0.1687 * exposureV.r + -0.3313 * exposureV.g"
        " + 0.5 * exposureV.b + 0.5;\n"
        "  float Cr = 0.5 * exposureV.r - 0.4187 * exposureV.g"
        " - 0.0813 * exposureV.b + 0.5;\n"
        "  Y = Y - 0.3;\n"
        "  PointerBGColor.r = Y + 1.402 * (Cr - 0.5);\n"
        "  PointerBGColor.g = Y - 0.34414 * (Cb - 0.5)"
        " - 0.71414 * (Cr - 0.5);\n"
        "  PointerBGColor.b = Y + 1.772 * (Cb - 0.5);\n"
        "  gl_FragColor.rgb = vec3(0.3184, 0.3184, 0.3184)"
        " + PointerBGColor * 0.7;\n"
        "  gl_FragColor.a = PointerAlpha;\n"
        "}\n";

enum {
    LLE_SPARK_BUFFER_POSITION = 0,
    LLE_SPARK_BUFFER_INITIAL_POSITION = 1,
    LLE_SPARK_BUFFER_SIZE = 2,
    LLE_SPARK_BUFFER_ALPHA = 3
};

static void spark_set_error(
        char *error, size_t error_size, const char *format, ...) {
    if (error == NULL || error_size == 0U) return;
    va_list arguments;
    va_start(arguments, format);
    (void) vsnprintf(error, error_size, format, arguments);
    va_end(arguments);
}

static void spark_clear_error(char *error, size_t error_size) {
    if (error != NULL && error_size > 0U) error[0] = '\0';
}

static void spark_drain_errors(void) {
    for (int index = 0; index < 16; ++index) {
        if (glGetError() == GL_NO_ERROR) return;
    }
}

static bool spark_capture_error(
        const char *where, char *error, size_t error_size) {
    const GLenum code = glGetError();
    if (code == GL_NO_ERROR) return true;
    spark_set_error(
            error,
            error_size,
            "%s glError=0x%04x",
            where,
            (unsigned int) code);
    spark_drain_errors();
    return false;
}

static GLuint spark_compile_shader(
        GLenum type,
        const char *source,
        const char *label,
        char *error,
        size_t error_size) {
    const GLuint shader = glCreateShader(type);
    if (shader == 0U) {
        spark_set_error(error, error_size, "%s glCreateShader returned 0", label);
        return 0U;
    }
    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled == GL_TRUE) return shader;
    GLchar log[2048];
    GLsizei length = 0;
    log[0] = '\0';
    glGetShaderInfoLog(shader, (GLsizei) sizeof(log), &length, log);
    spark_set_error(error, error_size, "%s compile failed: %s", label, log);
    glDeleteShader(shader);
    return 0U;
}

static GLuint spark_create_program(char *error, size_t error_size) {
    const GLuint vertex = spark_compile_shader(
            GL_VERTEX_SHADER,
            LLE_SPARK_VERTEX_SHADER,
            "sparkling vertex",
            error,
            error_size);
    if (vertex == 0U) return 0U;
    const GLuint fragment = spark_compile_shader(
            GL_FRAGMENT_SHADER,
            LLE_SPARK_FRAGMENT_SHADER,
            "sparkling fragment",
            error,
            error_size);
    if (fragment == 0U) {
        glDeleteShader(vertex);
        return 0U;
    }
    GLuint program = glCreateProgram();
    if (program == 0U) {
        spark_set_error(error, error_size, "sparkling glCreateProgram returned 0");
    } else {
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        GLint linked = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linked);
        if (linked != GL_TRUE) {
            GLchar log[2048];
            GLsizei length = 0;
            log[0] = '\0';
            glGetProgramInfoLog(program, (GLsizei) sizeof(log), &length, log);
            spark_set_error(error, error_size, "sparkling link failed: %s", log);
            glDeleteProgram(program);
            program = 0U;
        }
    }
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    return program;
}

static void spark_forget_names(LleSparkGles *gles) {
    if (gles == NULL) return;
    gles->program = 0U;
    memset(gles->buffers, 0, sizeof(gles->buffers));
    memset(gles->textures, 0, sizeof(gles->textures));
    gles->attribute_position = -1;
    gles->attribute_initial_position = -1;
    gles->attribute_point_size = -1;
    gles->attribute_point_alpha = -1;
    gles->uniform_mvp_matrix = -1;
    gles->uniform_background = -1;
    gles->uniform_mask = -1;
    gles->uniform_inverse_width = -1;
    gles->uniform_inverse_background_height = -1;
    gles->uniform_crop_max_v = -1;
    gles->background_width = 0;
    gles->background_height = 0;
    gles->ready = false;
    gles->has_background = false;
    gles->has_mask = false;
}

static void spark_delete_names(LleSparkGles *gles) {
    if (gles == NULL) return;
    if (gles->program != 0U) glDeleteProgram(gles->program);
    glDeleteBuffers(4, gles->buffers);
    glDeleteTextures(2, gles->textures);
    spark_forget_names(gles);
}

static bool spark_ensure_cpu_arrays(
        LleSparkGles *gles, char *error, size_t error_size) {
    const size_t capacity =
            LLE_SPARK_GROUP_CAPACITY * LLE_SPARK_PARTICLES_PER_GROUP;
    if (gles->point_capacity == capacity
            && gles->positions_xy != NULL
            && gles->initial_positions_xy != NULL
            && gles->sizes != NULL
            && gles->alphas != NULL) {
        return true;
    }
    free(gles->positions_xy);
    free(gles->initial_positions_xy);
    free(gles->sizes);
    free(gles->alphas);
    gles->positions_xy = (float *) calloc(capacity * 2U, sizeof(float));
    gles->initial_positions_xy = (float *) calloc(capacity * 2U, sizeof(float));
    gles->sizes = (float *) calloc(capacity, sizeof(float));
    gles->alphas = (float *) calloc(capacity, sizeof(float));
    if (gles->positions_xy == NULL
            || gles->initial_positions_xy == NULL
            || gles->sizes == NULL
            || gles->alphas == NULL) {
        spark_set_error(
                error,
                error_size,
                "Could not allocate packed buffers for %zu points",
                capacity);
        free(gles->positions_xy);
        free(gles->initial_positions_xy);
        free(gles->sizes);
        free(gles->alphas);
        gles->positions_xy = NULL;
        gles->initial_positions_xy = NULL;
        gles->sizes = NULL;
        gles->alphas = NULL;
        gles->point_capacity = 0U;
        return false;
    }
    gles->point_capacity = capacity;
    return true;
}

bool lle_spark_gles_init(
        LleSparkGles *gles,
        int width,
        int height,
        char *error,
        size_t error_size) {
    if (gles == NULL || width <= 0 || height <= 0) {
        spark_set_error(error, error_size, "Invalid GLES init size %dx%d", width, height);
        return false;
    }
    spark_clear_error(error, error_size);
    spark_drain_errors();
    if (!spark_ensure_cpu_arrays(gles, error, error_size)) return false;
    if (gles->ready) spark_delete_names(gles);
    gles->program = spark_create_program(error, error_size);
    if (gles->program == 0U) goto fail;
    gles->attribute_position = glGetAttribLocation(gles->program, "aPosition");
    gles->attribute_initial_position =
            glGetAttribLocation(gles->program, "aInitPosition");
    gles->attribute_point_size = glGetAttribLocation(gles->program, "aPointSize");
    gles->attribute_point_alpha = glGetAttribLocation(gles->program, "aPointAlpha");
    gles->uniform_mvp_matrix = glGetUniformLocation(gles->program, "mvpMatrix");
    gles->uniform_background = glGetUniformLocation(gles->program, "uBGTexMap");
    gles->uniform_mask = glGetUniformLocation(gles->program, "uMaskMap");
    gles->uniform_inverse_width = glGetUniformLocation(gles->program, "uInvScWidth");
    gles->uniform_inverse_background_height =
            glGetUniformLocation(gles->program, "uInvScHeight");
    gles->uniform_crop_max_v = glGetUniformLocation(gles->program, "uCropMaxTexV");
    if (gles->attribute_position < 0
            || gles->attribute_initial_position < 0
            || gles->attribute_point_size < 0
            || gles->attribute_point_alpha < 0
            || gles->uniform_mvp_matrix < 0
            || gles->uniform_background < 0
            || gles->uniform_mask < 0
            || gles->uniform_inverse_width < 0
            || gles->uniform_inverse_background_height < 0
            || gles->uniform_crop_max_v < 0) {
        spark_set_error(error, error_size, "Sparkling shader interface is incomplete");
        goto fail;
    }
    glGenBuffers(4, gles->buffers);
    for (int index = 0; index < 4; ++index) {
        if (gles->buffers[index] == 0U) {
            spark_set_error(error, error_size, "glGenBuffers[%d] returned 0", index);
            goto fail;
        }
    }
    const GLsizeiptr vector_bytes =
            (GLsizeiptr) (gles->point_capacity * 2U * sizeof(float));
    const GLsizeiptr scalar_bytes =
            (GLsizeiptr) (gles->point_capacity * sizeof(float));
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_SPARK_BUFFER_POSITION]);
    glBufferData(GL_ARRAY_BUFFER, vector_bytes, NULL, GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_SPARK_BUFFER_INITIAL_POSITION]);
    glBufferData(GL_ARRAY_BUFFER, vector_bytes, NULL, GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_SPARK_BUFFER_SIZE]);
    glBufferData(GL_ARRAY_BUFFER, scalar_bytes, NULL, GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_SPARK_BUFFER_ALPHA]);
    glBufferData(GL_ARRAY_BUFFER, scalar_bytes, NULL, GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    if (!spark_capture_error("Sparkling GLES init", error, error_size)) goto fail;
    gles->surface_width = width;
    gles->surface_height = height;
    gles->ready = true;
    return true;

fail:
    spark_delete_names(gles);
    return false;
}

void lle_spark_gles_abandon(LleSparkGles *gles) {
    spark_forget_names(gles);
}

void lle_spark_gles_destroy(LleSparkGles *gles) {
    if (gles == NULL) return;
    if (gles->ready) spark_delete_names(gles);
    free(gles->positions_xy);
    free(gles->initial_positions_xy);
    free(gles->sizes);
    free(gles->alphas);
    memset(gles, 0, sizeof(*gles));
}

static GLuint *spark_texture_slot(LleSparkGles *gles, int slot) {
    if (gles == NULL || slot < 0 || slot > 1) return NULL;
    return &gles->textures[slot];
}

bool lle_spark_gles_upload_bitmap(
        LleSparkGles *gles,
        JNIEnv *env,
        int slot,
        jobject bitmap,
        char *error,
        size_t error_size) {
    GLuint *texture = spark_texture_slot(gles, slot);
    if (gles == NULL
            || !gles->ready
            || env == NULL
            || bitmap == NULL
            || texture == NULL) {
        spark_set_error(error, error_size, "Invalid bitmap upload state or slot=%d", slot);
        return false;
    }
    AndroidBitmapInfo info;
    const int info_result = AndroidBitmap_getInfo(env, bitmap, &info);
    if (info_result != ANDROID_BITMAP_RESULT_SUCCESS
            || info.width == 0U
            || info.height == 0U) {
        spark_set_error(
                error,
                error_size,
                "AndroidBitmap_getInfo failed result=%d",
                info_result);
        return false;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        spark_set_error(
                error,
                error_size,
                "Bitmap slot=%d must be RGBA_8888; format=%u",
                slot,
                info.format);
        return false;
    }
    if (slot == LLE_SPARK_TEXTURE_BLUR_MASK
            && (info.width != 20U || info.height != 20U)) {
        spark_set_error(
                error,
                error_size,
                "BlurMask must be 20x20; got %ux%u",
                info.width,
                info.height);
        return false;
    }
    void *locked_pixels = NULL;
    const int lock_result = AndroidBitmap_lockPixels(env, bitmap, &locked_pixels);
    if (lock_result != ANDROID_BITMAP_RESULT_SUCCESS || locked_pixels == NULL) {
        spark_set_error(
                error,
                error_size,
                "AndroidBitmap_lockPixels failed result=%d",
                lock_result);
        return false;
    }
    const size_t row_bytes = (size_t) info.width * 4U;
    const size_t packed_size = row_bytes * (size_t) info.height;
    void *upload_pixels = locked_pixels;
    uint8_t *packed_pixels = NULL;
    if ((size_t) info.stride != row_bytes) {
        packed_pixels = (uint8_t *) malloc(packed_size);
        if (packed_pixels == NULL) {
            (void) AndroidBitmap_unlockPixels(env, bitmap);
            spark_set_error(
                    error,
                    error_size,
                    "Could not pack bitmap stride=%u",
                    info.stride);
            return false;
        }
        for (uint32_t row = 0; row < info.height; ++row) {
            memcpy(
                    packed_pixels + (size_t) row * row_bytes,
                    (const uint8_t *) locked_pixels + (size_t) row * info.stride,
                    row_bytes);
        }
        upload_pixels = packed_pixels;
    }
    if (*texture == 0U) glGenTextures(1, texture);
    glBindTexture(GL_TEXTURE_2D, *texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_MIRRORED_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_MIRRORED_REPEAT);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            (GLsizei) info.width,
            (GLsizei) info.height,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            upload_pixels);
    glBindTexture(GL_TEXTURE_2D, 0);
    const int unlock_result = AndroidBitmap_unlockPixels(env, bitmap);
    free(packed_pixels);
    if (unlock_result != ANDROID_BITMAP_RESULT_SUCCESS) {
        spark_set_error(
                error,
                error_size,
                "AndroidBitmap_unlockPixels failed result=%d",
                unlock_result);
        return false;
    }
    if (!spark_capture_error("Sparkling bitmap upload", error, error_size)) {
        return false;
    }
    if (slot == LLE_SPARK_TEXTURE_BACKGROUND) {
        gles->background_width = (int) info.width;
        gles->background_height = (int) info.height;
        gles->has_background = true;
    } else {
        gles->has_mask = true;
    }
    return true;
}

void lle_spark_gles_clear_bitmap(LleSparkGles *gles, int slot) {
    GLuint *texture = spark_texture_slot(gles, slot);
    if (texture == NULL || *texture == 0U) return;
    glDeleteTextures(1, texture);
    *texture = 0U;
    if (slot == LLE_SPARK_TEXTURE_BACKGROUND) {
        gles->has_background = false;
        gles->background_width = 0;
        gles->background_height = 0;
    } else {
        gles->has_mask = false;
    }
}

static void spark_enable_vector_attribute(
        GLint location, GLuint buffer, GLint components) {
    glBindBuffer(GL_ARRAY_BUFFER, buffer);
    glEnableVertexAttribArray((GLuint) location);
    glVertexAttribPointer(
            (GLuint) location, components, GL_FLOAT, GL_FALSE, 0, (const void *) 0);
}

static void spark_upload_buffers(LleSparkGles *gles, size_t point_count) {
    const GLsizeiptr vector_bytes =
            (GLsizeiptr) (point_count * 2U * sizeof(float));
    const GLsizeiptr scalar_bytes =
            (GLsizeiptr) (point_count * sizeof(float));
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_SPARK_BUFFER_POSITION]);
    glBufferSubData(GL_ARRAY_BUFFER, 0, vector_bytes, gles->positions_xy);
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_SPARK_BUFFER_INITIAL_POSITION]);
    glBufferSubData(GL_ARRAY_BUFFER, 0, vector_bytes, gles->initial_positions_xy);
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_SPARK_BUFFER_SIZE]);
    glBufferSubData(GL_ARRAY_BUFFER, 0, scalar_bytes, gles->sizes);
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_SPARK_BUFFER_ALPHA]);
    glBufferSubData(GL_ARRAY_BUFFER, 0, scalar_bytes, gles->alphas);
}

bool lle_spark_gles_draw(
        LleSparkGles *gles,
        const LleSparkSim *sim,
        float presentation_fraction,
        int width,
        int height,
        char *error,
        size_t error_size) {
    if (gles == NULL || sim == NULL || !gles->ready || width <= 0 || height <= 0) {
        spark_set_error(error, error_size, "Invalid Sparkling draw state %dx%d", width, height);
        return false;
    }
    spark_clear_error(error, error_size);
    spark_drain_errors();
    glViewport(0, 0, width, height);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    gles->surface_width = width;
    gles->surface_height = height;

    /* Texture replacement is asynchronous with respect to initial GL setup.
     * Until both inputs exist, a transparent frame is a valid, non-fatal draw. */
    if (!gles->has_background || !gles->has_mask) {
        return spark_capture_error("Sparkling transparent wait", error, error_size);
    }
    const size_t required = lle_spark_sim_export_draw_data(
            sim, presentation_fraction,
            NULL, NULL, NULL, NULL, 0U, NULL, 0U);
    if (required == 0U) {
        return spark_capture_error("Sparkling idle draw", error, error_size);
    }
    if (required > gles->point_capacity) {
        spark_set_error(
                error,
                error_size,
                "Simulation requires %zu points; capacity=%zu",
                required,
                gles->point_capacity);
        return false;
    }
    const size_t exported = lle_spark_sim_export_draw_data(
            sim,
            presentation_fraction,
            gles->positions_xy,
            gles->initial_positions_xy,
            gles->sizes,
            gles->alphas,
            gles->point_capacity,
            gles->groups,
            LLE_SPARK_GROUP_CAPACITY);
    if (exported != required) {
        spark_set_error(
                error,
                error_size,
                "Simulation export changed from %zu to %zu points",
                required,
                exported);
        return false;
    }
    spark_upload_buffers(gles, exported);
    glUseProgram(gles->program);
    spark_enable_vector_attribute(
            gles->attribute_position,
            gles->buffers[LLE_SPARK_BUFFER_POSITION],
            2);
    spark_enable_vector_attribute(
            gles->attribute_initial_position,
            gles->buffers[LLE_SPARK_BUFFER_INITIAL_POSITION],
            2);
    spark_enable_vector_attribute(
            gles->attribute_point_size,
            gles->buffers[LLE_SPARK_BUFFER_SIZE],
            1);
    spark_enable_vector_attribute(
            gles->attribute_point_alpha,
            gles->buffers[LLE_SPARK_BUFFER_ALPHA],
            1);
    const float mvp_matrix[16] = {
            2.0f / (float) width, 0.0f, 0.0f, 0.0f,
            0.0f, 2.0f / (float) height, 0.0f, 0.0f,
            0.0f, 0.0f, -0.0002f, 0.0f,
            -1.0f, -1.0f, 0.0f, 1.0f
    };
    glUniformMatrix4fv(gles->uniform_mvp_matrix, 1, GL_FALSE, mvp_matrix);
    glUniform1f(gles->uniform_inverse_width, 1.0f / (float) width);
    const float background_height =
            gles->background_height > 0 ? (float) gles->background_height : (float) height;
    float crop = 0.0f;
    if (background_height > (float) height) {
        crop = ((background_height - (float) height) / background_height) * 0.5f;
    }
    glUniform1f(gles->uniform_inverse_background_height, 1.0f / background_height);
    glUniform1f(gles->uniform_crop_max_v, 1.0f - crop);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, gles->textures[LLE_SPARK_TEXTURE_BACKGROUND]);
    glUniform1i(gles->uniform_background, 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, gles->textures[LLE_SPARK_TEXTURE_BLUR_MASK]);
    glUniform1i(gles->uniform_mask, 1);
    glEnable(GL_BLEND);
    glBlendEquation(GL_FUNC_ADD);
    /* Exact blend retained by Samsung's transparent-patched vendor oracle. */
    glBlendFuncSeparate(
            GL_SRC_ALPHA,
            GL_ONE_MINUS_SRC_ALPHA,
            GL_ONE,
            GL_ONE);
    const size_t group_count =
            exported / (size_t) LLE_SPARK_PARTICLES_PER_GROUP;
    for (size_t group_index = 0U; group_index < group_count; ++group_index) {
        const LleSparkDrawGroup *group = &gles->groups[group_index];
        if (group->point_count == 0U) continue;
        glDrawArrays(
                GL_POINTS,
                (GLint) group->first_point,
                (GLsizei) group->point_count);
    }
    glDisableVertexAttribArray((GLuint) gles->attribute_position);
    glDisableVertexAttribArray((GLuint) gles->attribute_initial_position);
    glDisableVertexAttribArray((GLuint) gles->attribute_point_size);
    glDisableVertexAttribArray((GLuint) gles->attribute_point_alpha);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);
    glDisable(GL_BLEND);
    return spark_capture_error("Sparkling draw", error, error_size);
}
