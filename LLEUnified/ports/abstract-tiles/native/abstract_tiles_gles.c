#include "abstract_tiles_internal.h"

#include <math.h>
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
        "attribute float aProximity;\n"
        "attribute float aRandom;\n"
        "attribute float aRay;\n"
        "varying float vScatter;\n"
        "void main(){\n"
        "  gl_Position=vec4(aPosition,0.0,1.0);\n"
        "  vScatter=aProximity+aRandom+aRay;\n"
        "}\n";

static const char *AT_SCATTER_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "varying float vScatter;\n"
        "void main(){\n"
        "  float alpha=clamp(vScatter,0.0,1.0);\n"
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
/* The recovered pass requires a wallpaper-only texture. LLE currently caches
 * the complete lockscreen, so enabling it duplicates clock/status UI in eleven
 * large moving slabs. Keep the exact implementation dormant until the host can
 * supply a clean wallpaper source; ARM32 applies the same shipping boundary. */
#define AT_TRANSPARENT_LINE_AVAILABLE 0

typedef struct AtSeamDefinition {
    uint16_t atlas_x;
    uint16_t index_a;
    uint16_t index_b;
    uint16_t index_c;
    uint16_t index_d;
    uint16_t delta_from[4];
    uint16_t delta_to[4];
    uint8_t threshold_mask;
} AtSeamDefinition;

static const AtSeamDefinition AT_PORTRAIT_SEAMS[AT_SEAM_COUNT] = {
        {26, 171,  16,  19, 173, {171, 171, 171, 171}, { 16,  16,  16,  16}, 0x0},
        {18, 103, 267, 268, 101, {103, 103, 101, 103}, {301, 301, 268, 301}, 0x6},
        {46, 441, 290, 288, 443, {290, 290, 290, 290}, {441, 441, 441, 441}, 0x9},
        { 2, 952, 918, 881, 951, {881, 881, 881, 881}, {951, 951, 951, 951}, 0x0},
        { 6, 962, 856, 857, 961, {857, 857, 857, 857}, {961, 961, 961, 961}, 0x0},
        {14, 603, 374, 372, 605, {374, 374, 374, 374}, {603, 603, 603, 603}, 0x0},
        {34, 531, 638, 636, 533, {531, 531, 531, 531}, {638, 638, 638, 638}, 0x6},
        {22, 243, 309, 310, 245, {310, 310, 310, 310}, {245, 245, 245, 245}, 0x0},
        {30, 747, 854, 852, 749, {854, 854, 854, 854}, {747, 747, 747, 747}, 0x0},
        {38, 909, 794, 792, 911, {792, 792, 792, 792}, {911, 911, 911, 911}, 0x9},
        {42, 773, 576, 578, 771, {773, 773, 773, 773}, {576, 576, 576, 576}, 0x0}
};

static const AtSeamDefinition AT_LANDSCAPE_SEAMS[AT_SEAM_COUNT] = {
        {26,  10, 216, 218,  13, { 13,  13,  13,  13}, {218, 218, 218, 218}, 0x0},
        {18,   4, 236, 235,   6, {  4,   4,   4,   4}, {236, 236, 236, 236}, 0x6},
        {46, 880, 732, 679, 883, {880, 880, 880, 880}, {732, 732, 732, 732}, 0x6},
        { 2, 481, 423, 364, 373, {364, 364, 364, 364}, {373, 373, 373, 373}, 0x0},
        { 6, 211, 208,  94, 103, { 94,  94,  94,  94}, {103, 103, 103, 103}, 0x0},
        {14,  34, 238, 237,  37, {237, 237, 237, 237}, { 37,  37,  37,  37}, 0x0},
        {34,  28, 202, 205,  31, { 28,  28,  28,  28}, {202, 202, 202, 202}, 0x6},
        {22,  16,  80,  79,  19, { 80,  80,  80,  80}, { 16,  16,  16,  16}, 0x0},
        {30, 886, 544, 547, 889, {544, 544, 544, 544}, {886, 886, 886, 886}, 0x0},
        {38, 892, 640, 643, 895, {892, 892, 892, 892}, {640, 640, 640, 640}, 0x6},
        {42, 910, 682, 685, 913, {682, 682, 682, 682}, {910, 910, 910, 910}, 0x0}
};

static void at_write_line_vertex(
        float *output,
        const AtScene *scene,
        uint16_t source_index,
        uint16_t delta_from_index,
        uint16_t delta_to_index,
        float threshold,
        float progress,
        float crop_x,
        float crop_y,
        float atlas_u,
        float atlas_v,
        float line_alpha) {
    const AtTriangle *source_triangle = &scene->triangles[source_index / 3U];
    const AtTriangle *delta_from_triangle = &scene->triangles[delta_from_index / 3U];
    const AtTriangle *delta_to_triangle = &scene->triangles[delta_to_index / 3U];
    const int source_vertex = (int) (source_index % 3U);
    const int delta_from_vertex = (int) (delta_from_index % 3U);
    const int delta_to_vertex = (int) (delta_to_index % 3U);
    const float start_x = source_triangle->base[source_vertex * 2];
    const float start_y = source_triangle->base[source_vertex * 2 + 1];
    const float delta_x = delta_to_triangle->base[delta_to_vertex * 2]
            - delta_from_triangle->base[delta_from_vertex * 2];
    const float delta_y = delta_to_triangle->base[delta_to_vertex * 2 + 1]
            - delta_from_triangle->base[delta_from_vertex * 2 + 1];
    /* FUN_13B10 updates background UV only before the per-corner threshold.
     * Its persistent OEM buffer keeps the last displaced UV afterwards; an
     * absolute reconstruction must therefore clamp, not reset it to start. */
    const float uv_progress = fminf(progress, threshold);
    const float position_progress = fmaxf(progress - threshold, 0.0f);
    const float uv_x = start_x - uv_progress * delta_x;
    const float uv_y = start_y - uv_progress * delta_y;
    output[0] = start_x + position_progress * delta_x;
    output[1] = start_y + position_progress * delta_y;
    output[2] = atlas_u;
    output[3] = atlas_v;
    output[4] = crop_x + (1.0f - 2.0f * crop_x) * (1.0f + uv_x) * 0.5f;
    output[5] = crop_y + (1.0f - 2.0f * crop_y) * (1.0f - uv_y) * 0.5f;
    /* The OEM Line shader has no per-tile alpha: every seam uses mask.a.
     * LLE only adds a shared scene gate so an otherwise transparent overlay
     * does not retain eleven seam ghosts after the effect becomes idle. */
    output[6] = line_alpha;
}

static int at_build_line_vertices(
        const AtScene *scene,
        int tile_vertex_count,
        int texture_width,
        int texture_height,
        float *line_vertices,
        size_t line_float_capacity) {
    const size_t required = AT_SEAM_COUNT * AT_LINE_VERTICES_PER_SEAM
            * AT_FLOATS_PER_LINE_VERTEX;
    if (scene == NULL || line_vertices == NULL
            || texture_width <= 0 || texture_height <= 0
            || line_float_capacity < required) {
        return 0;
    }
    const AtSeamDefinition *seams = scene->columns == 5
            ? AT_PORTRAIT_SEAMS : AT_LANDSCAPE_SEAMS;
    const float sx = (float) scene->width / (float) texture_width;
    const float sy = (float) scene->height / (float) texture_height;
    const float crop_x = sy > sx ? fabsf(sx / sy - 1.0f) * 0.5f : 0.0f;
    const float crop_y = sy <= sx ? fabsf(sy / sx - 1.0f) * 0.5f : 0.0f;
    const float progress = fminf(fmaxf(scene->unlock_line_progress, 0.0f), 1.0f);
    /* At p=0 the stock Line is visually neutral because it is drawn over its
     * identical opaque Background. On LLE's live transparent underlay those
     * static quads expose stale UI pixels, so gate only the unlock track. */
    const float line_alpha = AT_TRANSPARENT_LINE_AVAILABLE
                    && !at_scene_is_idle(scene)
                    && progress > 0.0f
            ? 1.0f : 0.0f;
    int emitted = 0;
    for (int seam_index = 0; seam_index < AT_SEAM_COUNT; ++seam_index) {
        const AtSeamDefinition *seam = &seams[seam_index];
        if (seam->index_a >= tile_vertex_count || seam->index_b >= tile_vertex_count
                || seam->index_c >= tile_vertex_count
                || seam->index_d >= tile_vertex_count) {
            return 0;
        }
        for (int logical_vertex = 0; logical_vertex < 4; ++logical_vertex) {
            if (seam->delta_from[logical_vertex] >= tile_vertex_count
                    || seam->delta_to[logical_vertex] >= tile_vertex_count) {
                return 0;
            }
        }
        const float atlas_u = (float) seam->atlas_x / 56.0f;
        const uint16_t indices[AT_LINE_VERTICES_PER_SEAM] = {
                seam->index_a, seam->index_c, seam->index_d,
                seam->index_a, seam->index_b, seam->index_c
        };
        const uint8_t logical_indices[AT_LINE_VERTICES_PER_SEAM] = {0, 2, 3, 0, 1, 2};
        const float atlas_v[AT_LINE_VERTICES_PER_SEAM] = {
                0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f
        };
        for (int vertex = 0; vertex < AT_LINE_VERTICES_PER_SEAM; ++vertex) {
            const uint8_t logical_index = logical_indices[vertex];
            at_write_line_vertex(
                    line_vertices + (size_t) emitted * AT_FLOATS_PER_LINE_VERTEX,
                    scene,
                    indices[vertex],
                    seam->delta_from[logical_index],
                    seam->delta_to[logical_index],
                    (seam->threshold_mask & (uint8_t) (1U << logical_index)) != 0U
                            ? 1.0f : 0.0f,
                    progress,
                    crop_x,
                    crop_y,
                    atlas_u,
                    atlas_v[vertex],
                    line_alpha);
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
            vertex_count,
            gles->background_width,
            gles->background_height,
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
            gles->scatter_program, "aPosition", 2, AT_FLOATS_PER_VERTEX, 4U);
    at_enable_attribute(
            gles->scatter_program, "aProximity", 1, AT_FLOATS_PER_VERTEX, 9U);
    at_enable_attribute(
            gles->scatter_program, "aRandom", 1, AT_FLOATS_PER_VERTEX, 10U);
    at_enable_attribute(
            gles->scatter_program, "aRay", 1, AT_FLOATS_PER_VERTEX, 11U);
    glBlendFunc(GL_ONE, GL_ONE);
    glDrawArrays(GL_TRIANGLES, 0, vertex_count);
    at_disable_attribute(gles->scatter_program, "aPosition");
    at_disable_attribute(gles->scatter_program, "aProximity");
    at_disable_attribute(gles->scatter_program, "aRandom");
    at_disable_attribute(gles->scatter_program, "aRay");

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);
    glDisable(GL_BLEND);
    return at_capture_error("Abstract Tiles draw", error, error_size);
}
