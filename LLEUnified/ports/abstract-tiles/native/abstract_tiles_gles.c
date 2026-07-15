#include "abstract_tiles_internal.h"

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static const char *AT_TILE_VERTEX_SHADER =
        "attribute vec2 aPosition;\n"
        "attribute vec2 aTexCoord;\n"
        "attribute float aAlpha;\n"
        "attribute float aBrightness;\n"
        "varying vec2 vTexCoord;\n"
        "varying float vAlpha;\n"
        "varying float vBrightness;\n"
        "void main(){\n"
        "  gl_Position=vec4(aPosition,0.0,1.0);\n"
        "  vTexCoord=aTexCoord;\n"
        "  vAlpha=aAlpha;\n"
        "  vBrightness=aBrightness;\n"
        "}\n";

static const char *AT_TILE_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "uniform sampler2D uBackground;\n"
        "varying vec2 vTexCoord;\n"
        "varying float vAlpha;\n"
        "varying float vBrightness;\n"
        "void main(){\n"
        "  vec4 source=texture2D(uBackground,vTexCoord);\n"
        "  float alpha=clamp(vAlpha,0.0,1.0);\n"
        "  vec3 rgb=clamp(source.rgb+vec3(vBrightness),0.0,1.0);\n"
        "  gl_FragColor=vec4(rgb*alpha,alpha);\n"
        "}\n";

static const char *AT_LINE_VERTEX_SHADER =
        "attribute vec2 aPosition;\n"
        "attribute vec2 aLineTextureCoord;\n"
        "attribute vec2 aBgTextureCoord;\n"
        "attribute float aAlpha;\n"
        "varying vec2 vLineTexture;\n"
        "varying vec2 vBgTexture;\n"
        "varying float vAlpha;\n"
        "void main(){\n"
        "  gl_Position=vec4(aPosition,0.0,1.0);\n"
        "  vLineTexture=aLineTextureCoord;\n"
        "  vBgTexture=aBgTextureCoord;\n"
        "  vAlpha=aAlpha;\n"
        "}\n";

static const char *AT_LINE_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "uniform sampler2D uLineMask;\n"
        "uniform sampler2D uBackground;\n"
        "varying vec2 vLineTexture;\n"
        "varying vec2 vBgTexture;\n"
        "varying float vAlpha;\n"
        "void main(){\n"
        "  vec4 mask=texture2D(uLineMask,vLineTexture);\n"
        "  if(mask.a==0.0) discard;\n"
        "  float alpha=mask.a*clamp(vAlpha,0.0,1.0);\n"
        "  vec3 rgb=texture2D(uBackground,vBgTexture).rgb;\n"
        "  gl_FragColor=vec4(rgb*alpha,alpha);\n"
        "}\n";

static const char *AT_SCATTER_VERTEX_SHADER =
        "attribute vec2 aPosition;\n"
        "attribute float aScatter;\n"
        "varying float vScatter;\n"
        "void main(){\n"
        "  gl_Position=vec4(aPosition,0.0,1.0);\n"
        "  vScatter=aScatter;\n"
        "}\n";

static const char *AT_SCATTER_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "varying float vScatter;\n"
        "void main(){\n"
        "  float alpha=clamp(vScatter,0.0,1.0)*0.22;\n"
        /* Additive light must not make the Android overlay's alpha opaque. */
        "  gl_FragColor=vec4(vec3(alpha),0.0);\n"
        "}\n";

static void at_set_error(char *error, size_t error_size, const char *format, ...) {
    if (error == NULL || error_size == 0U) return;
    va_list args;
    va_start(args, format);
    (void) vsnprintf(error, error_size, format, args);
    va_end(args);
}

static void at_clear_error(char *error, size_t error_size) {
    if (error != NULL && error_size > 0U) error[0] = '\0';
}

static void at_drain_errors(void) {
    for (int i = 0; i < 16; ++i) {
        if (glGetError() == GL_NO_ERROR) return;
    }
}

static bool at_capture_error(const char *where, char *error, size_t error_size) {
    GLenum code = glGetError();
    if (code == GL_NO_ERROR) return true;
    at_set_error(error, error_size, "%s glError=0x%04x", where, (unsigned int) code);
    at_drain_errors();
    return false;
}

static GLuint at_compile_shader(
        GLenum type, const char *source, const char *label, char *error, size_t error_size) {
    GLuint shader = glCreateShader(type);
    if (shader == 0U) {
        at_set_error(error, error_size, "%s glCreateShader returned 0", label);
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
    at_set_error(error, error_size, "%s compile failed: %s", label, log);
    glDeleteShader(shader);
    return 0U;
}

static GLuint at_create_program(
        const char *vertex_source,
        const char *fragment_source,
        const char *label,
        char *error,
        size_t error_size) {
    GLuint vertex = at_compile_shader(
            GL_VERTEX_SHADER, vertex_source, label, error, error_size);
    if (vertex == 0U) return 0U;
    GLuint fragment = at_compile_shader(
            GL_FRAGMENT_SHADER, fragment_source, label, error, error_size);
    if (fragment == 0U) {
        glDeleteShader(vertex);
        return 0U;
    }
    GLuint program = glCreateProgram();
    if (program == 0U) {
        at_set_error(error, error_size, "%s glCreateProgram returned 0", label);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return 0U;
    }
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
        at_set_error(error, error_size, "%s link failed: %s", label, log);
        glDeleteProgram(program);
        program = 0U;
    }
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    return program;
}

void at_gles_abandon(AtGles *gles) {
    if (gles != NULL) memset(gles, 0, sizeof(*gles));
}

void at_gles_destroy(AtGles *gles) {
    if (gles == NULL) return;
    if (gles->tile_program != 0U) glDeleteProgram(gles->tile_program);
    if (gles->line_program != 0U) glDeleteProgram(gles->line_program);
    if (gles->scatter_program != 0U) glDeleteProgram(gles->scatter_program);
    if (gles->vertex_buffer != 0U) glDeleteBuffers(1, &gles->vertex_buffer);
    if (gles->line_buffer != 0U) glDeleteBuffers(1, &gles->line_buffer);
    GLuint textures[2] = {gles->background_texture, gles->line_mask_texture};
    glDeleteTextures(2, textures);
    at_gles_abandon(gles);
}

bool at_gles_init(AtGles *gles, char *error, size_t error_size) {
    if (gles == NULL) {
        at_set_error(error, error_size, "GLES state is null");
        return false;
    }
    at_clear_error(error, error_size);
    at_drain_errors();
    at_gles_abandon(gles);
    gles->tile_program = at_create_program(
            AT_TILE_VERTEX_SHADER, AT_TILE_FRAGMENT_SHADER, "tile", error, error_size);
    if (gles->tile_program == 0U) goto fail;
    gles->line_program = at_create_program(
            AT_LINE_VERTEX_SHADER, AT_LINE_FRAGMENT_SHADER, "line", error, error_size);
    if (gles->line_program == 0U) goto fail;
    gles->scatter_program = at_create_program(
            AT_SCATTER_VERTEX_SHADER,
            AT_SCATTER_FRAGMENT_SHADER,
            "scatter",
            error,
            error_size);
    if (gles->scatter_program == 0U) goto fail;
    glGenBuffers(1, &gles->vertex_buffer);
    if (gles->vertex_buffer == 0U || !at_capture_error("glGenBuffers", error, error_size)) {
        goto fail;
    }
    glGenBuffers(1, &gles->line_buffer);
    if (gles->line_buffer == 0U
            || !at_capture_error("glGenBuffers(line)", error, error_size)) {
        goto fail;
    }
    gles->ready = true;
    return true;

fail:
    at_gles_destroy(gles);
    return false;
}

static GLuint *at_texture_slot(AtGles *gles, int slot) {
    if (gles == NULL) return NULL;
    if (slot == 0) return &gles->background_texture;
    if (slot == 1) return &gles->line_mask_texture;
    return NULL;
}

bool at_gles_upload_bitmap(
        AtGles *gles,
        JNIEnv *env,
        int slot,
        jobject bitmap,
        char *error,
        size_t error_size) {
    GLuint *texture = at_texture_slot(gles, slot);
    if (gles == NULL || !gles->ready || env == NULL || bitmap == NULL || texture == NULL) {
        at_set_error(error, error_size, "Bitmap upload has invalid state or slot=%d", slot);
        return false;
    }
    AndroidBitmapInfo info;
    int result = AndroidBitmap_getInfo(env, bitmap, &info);
    if (result != ANDROID_BITMAP_RESULT_SUCCESS || info.width == 0U || info.height == 0U) {
        at_set_error(error, error_size, "AndroidBitmap_getInfo failed result=%d", result);
        return false;
    }
    GLenum format;
    GLenum type;
    size_t bytes_per_pixel;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        format = GL_RGBA;
        type = GL_UNSIGNED_BYTE;
        bytes_per_pixel = 4U;
    } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        format = GL_RGB;
        type = GL_UNSIGNED_SHORT_5_6_5;
        bytes_per_pixel = 2U;
    } else if (info.format == ANDROID_BITMAP_FORMAT_A_8) {
        format = GL_ALPHA;
        type = GL_UNSIGNED_BYTE;
        bytes_per_pixel = 1U;
    } else {
        at_set_error(error, error_size, "Unsupported bitmap format=%u", info.format);
        return false;
    }

    void *pixels = NULL;
    result = AndroidBitmap_lockPixels(env, bitmap, &pixels);
    if (result != ANDROID_BITMAP_RESULT_SUCCESS || pixels == NULL) {
        at_set_error(error, error_size, "AndroidBitmap_lockPixels failed result=%d", result);
        return false;
    }
    const size_t row_bytes = (size_t) info.width * bytes_per_pixel;
    void *upload_pixels = pixels;
    void *packed_pixels = NULL;
    if ((size_t) info.stride != row_bytes) {
        packed_pixels = malloc(row_bytes * (size_t) info.height);
        if (packed_pixels == NULL) {
            (void) AndroidBitmap_unlockPixels(env, bitmap);
            at_set_error(error, error_size, "Could not pack bitmap stride=%u", info.stride);
            return false;
        }
        for (uint32_t row = 0; row < info.height; ++row) {
            memcpy(
                    (uint8_t *) packed_pixels + (size_t) row * row_bytes,
                    (const uint8_t *) pixels + (size_t) row * info.stride,
                    row_bytes);
        }
        upload_pixels = packed_pixels;
    }
    if (*texture == 0U) glGenTextures(1, texture);
    glBindTexture(GL_TEXTURE_2D, *texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            (GLint) format,
            (GLsizei) info.width,
            (GLsizei) info.height,
            0,
            format,
            type,
            upload_pixels);
    (void) AndroidBitmap_unlockPixels(env, bitmap);
    free(packed_pixels);
    glBindTexture(GL_TEXTURE_2D, 0);
    if (!at_capture_error("bitmap upload", error, error_size)) return false;
    if (slot == 0) {
        gles->background_width = (int) info.width;
        gles->background_height = (int) info.height;
    }
    return true;
}

void at_gles_clear_bitmap(AtGles *gles, int slot) {
    GLuint *texture = at_texture_slot(gles, slot);
    if (texture == NULL || *texture == 0U) return;
    glDeleteTextures(1, texture);
    *texture = 0U;
    if (slot == 0) {
        gles->background_width = 0;
        gles->background_height = 0;
    }
}

static void at_enable_attribute(
        GLuint program,
        const char *name,
        GLint size,
        size_t stride_floats,
        size_t float_offset) {
    GLint location = glGetAttribLocation(program, name);
    if (location < 0) return;
    glEnableVertexAttribArray((GLuint) location);
    glVertexAttribPointer(
            (GLuint) location,
            size,
            GL_FLOAT,
            GL_FALSE,
            (GLsizei) (stride_floats * sizeof(float)),
            (const void *) (float_offset * sizeof(float)));
}

static void at_disable_attribute(GLuint program, const char *name) {
    GLint location = glGetAttribLocation(program, name);
    if (location >= 0) glDisableVertexAttribArray((GLuint) location);
}

#define AT_SEAM_COUNT 11
#define AT_LINE_VERTICES_PER_SEAM 6
#define AT_FLOATS_PER_LINE_VERTEX 7

typedef struct AtSeamDefinition {
    uint16_t atlas_x;
    uint16_t index_a;
    uint16_t index_b;
    uint16_t index_c;
    uint16_t index_d;
} AtSeamDefinition;

static const AtSeamDefinition AT_PORTRAIT_SEAMS[AT_SEAM_COUNT] = {
        {26, 171,  16,  19, 173},
        {18, 103, 267, 268, 101},
        {46, 441, 290, 288, 443},
        { 2, 952, 918, 881, 951},
        { 6, 962, 856, 857, 961},
        {14, 603, 374, 372, 605},
        {34, 531, 638, 636, 533},
        {22, 243, 309, 310, 245},
        {30, 747, 854, 852, 749},
        {38, 909, 794, 792, 911},
        {42, 773, 576, 578, 771}
};

static const AtSeamDefinition AT_LANDSCAPE_SEAMS[AT_SEAM_COUNT] = {
        {26,  10, 216, 218,  13},
        {18,   4, 236, 235,   6},
        {46, 880, 732, 679, 883},
        { 2, 481, 423, 364, 373},
        { 6, 211, 208,  94, 103},
        {14,  34, 238, 237,  37},
        {34,  28, 202, 205,  31},
        {22,  16,  80,  79,  19},
        {30, 886, 544, 547, 889},
        {38, 892, 640, 643, 895},
        {42, 910, 682, 685, 913}
};

static void at_write_line_vertex(
        float *output,
        const float *tile_vertices,
        uint16_t source_index,
        float atlas_u,
        float atlas_v) {
    const float *source = tile_vertices
            + (size_t) source_index * AT_FLOATS_PER_VERTEX;
    output[0] = source[0];
    output[1] = source[1];
    output[2] = atlas_u;
    output[3] = atlas_v;
    /* Tile UVs are generated from undeformed geometry by the CPU core. */
    output[4] = source[2];
    output[5] = source[3];
    output[6] = source[7];
}

static int at_build_line_vertices(
        const AtScene *scene,
        const float *tile_vertices,
        int tile_vertex_count,
        float *line_vertices,
        size_t line_float_capacity) {
    const size_t required = AT_SEAM_COUNT * AT_LINE_VERTICES_PER_SEAM
            * AT_FLOATS_PER_LINE_VERTEX;
    if (scene == NULL || tile_vertices == NULL || line_vertices == NULL
            || line_float_capacity < required) {
        return 0;
    }
    const AtSeamDefinition *seams = scene->columns == 5
            ? AT_PORTRAIT_SEAMS : AT_LANDSCAPE_SEAMS;
    int emitted = 0;
    for (int seam_index = 0; seam_index < AT_SEAM_COUNT; ++seam_index) {
        const AtSeamDefinition *seam = &seams[seam_index];
        if (seam->index_a >= tile_vertex_count || seam->index_b >= tile_vertex_count
                || seam->index_c >= tile_vertex_count
                || seam->index_d >= tile_vertex_count) {
            return 0;
        }
        const float atlas_u = (float) seam->atlas_x / 56.0f;
        const uint16_t indices[AT_LINE_VERTICES_PER_SEAM] = {
                seam->index_a, seam->index_c, seam->index_d,
                seam->index_a, seam->index_b, seam->index_c
        };
        const float atlas_v[AT_LINE_VERTICES_PER_SEAM] = {
                0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f
        };
        for (int vertex = 0; vertex < AT_LINE_VERTICES_PER_SEAM; ++vertex) {
            at_write_line_vertex(
                    line_vertices + (size_t) emitted * AT_FLOATS_PER_LINE_VERTEX,
                    tile_vertices,
                    indices[vertex],
                    atlas_u,
                    atlas_v[vertex]);
            ++emitted;
        }
    }
    return emitted;
}

bool at_gles_draw(
        AtGles *gles,
        const AtScene *scene,
        int width,
        int height,
        char *error,
        size_t error_size) {
    if (gles == NULL || scene == NULL || !gles->ready
            || gles->background_texture == 0U || gles->line_mask_texture == 0U) {
        at_set_error(error, error_size, "Draw called without programs and both textures");
        return false;
    }
    float vertices[AT_MAX_VERTICES * AT_FLOATS_PER_VERTEX];
    float line_vertices[AT_SEAM_COUNT * AT_LINE_VERTICES_PER_SEAM
            * AT_FLOATS_PER_LINE_VERTEX];
    int vertex_count = at_scene_build_vertices(
            scene,
            gles->background_width,
            gles->background_height,
            vertices,
            sizeof(vertices) / sizeof(vertices[0]));
    if (vertex_count <= 0) {
        at_set_error(error, error_size, "No Abstract Tiles vertices");
        return false;
    }
    int line_vertex_count = at_build_line_vertices(
            scene,
            vertices,
            vertex_count,
            line_vertices,
            sizeof(line_vertices) / sizeof(line_vertices[0]));
    if (line_vertex_count != AT_SEAM_COUNT * AT_LINE_VERTICES_PER_SEAM) {
        at_set_error(error, error_size, "Could not build the eleven OEM seam strips");
        return false;
    }

    at_clear_error(error, error_size);
    at_drain_errors();
    glViewport(0, 0, width, height);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glBindBuffer(GL_ARRAY_BUFFER, gles->vertex_buffer);
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) ((size_t) vertex_count * AT_FLOATS_PER_VERTEX * sizeof(float)),
            vertices,
            GL_DYNAMIC_DRAW);

    glUseProgram(gles->tile_program);
    at_enable_attribute(
            gles->tile_program, "aPosition", 2, AT_FLOATS_PER_VERTEX, 0U);
    at_enable_attribute(
            gles->tile_program, "aTexCoord", 2, AT_FLOATS_PER_VERTEX, 2U);
    at_enable_attribute(
            gles->tile_program, "aAlpha", 1, AT_FLOATS_PER_VERTEX, 7U);
    at_enable_attribute(
            gles->tile_program, "aBrightness", 1, AT_FLOATS_PER_VERTEX, 8U);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, gles->background_texture);
    glUniform1i(glGetUniformLocation(gles->tile_program, "uBackground"), 0);
    glEnable(GL_BLEND);
    glBlendEquation(GL_FUNC_ADD);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glDrawArrays(GL_TRIANGLES, 0, vertex_count);
    at_disable_attribute(gles->tile_program, "aPosition");
    at_disable_attribute(gles->tile_program, "aTexCoord");
    at_disable_attribute(gles->tile_program, "aAlpha");
    at_disable_attribute(gles->tile_program, "aBrightness");

    /* OEM line pass: exactly eleven atlas-backed strips, after Tile and before Scatter. */
    glBindBuffer(GL_ARRAY_BUFFER, gles->line_buffer);
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) ((size_t) line_vertex_count
                    * AT_FLOATS_PER_LINE_VERTEX * sizeof(float)),
            line_vertices,
            GL_DYNAMIC_DRAW);
    glUseProgram(gles->line_program);
    at_enable_attribute(
            gles->line_program,
            "aPosition",
            2,
            AT_FLOATS_PER_LINE_VERTEX,
            0U);
    at_enable_attribute(
            gles->line_program,
            "aLineTextureCoord",
            2,
            AT_FLOATS_PER_LINE_VERTEX,
            2U);
    at_enable_attribute(
            gles->line_program,
            "aBgTextureCoord",
            2,
            AT_FLOATS_PER_LINE_VERTEX,
            4U);
    at_enable_attribute(
            gles->line_program,
            "aAlpha",
            1,
            AT_FLOATS_PER_LINE_VERTEX,
            6U);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, gles->background_texture);
    glUniform1i(glGetUniformLocation(gles->line_program, "uBackground"), 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, gles->line_mask_texture);
    glUniform1i(glGetUniformLocation(gles->line_program, "uLineMask"), 1);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glDrawArrays(GL_TRIANGLES, 0, line_vertex_count);
    at_disable_attribute(gles->line_program, "aPosition");
    at_disable_attribute(gles->line_program, "aLineTextureCoord");
    at_disable_attribute(gles->line_program, "aBgTextureCoord");
    at_disable_attribute(gles->line_program, "aAlpha");

    glBindBuffer(GL_ARRAY_BUFFER, gles->vertex_buffer);
    glUseProgram(gles->scatter_program);
    at_enable_attribute(
            gles->scatter_program, "aPosition", 2, AT_FLOATS_PER_VERTEX, 0U);
    at_enable_attribute(
            gles->scatter_program, "aScatter", 1, AT_FLOATS_PER_VERTEX, 9U);
    glBlendFunc(GL_ONE, GL_ONE);
    glDrawArrays(GL_TRIANGLES, 0, vertex_count);
    at_disable_attribute(gles->scatter_program, "aPosition");
    at_disable_attribute(gles->scatter_program, "aScatter");

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);
    glDisable(GL_BLEND);
    return at_capture_error("Abstract Tiles draw", error, error_size);
}
