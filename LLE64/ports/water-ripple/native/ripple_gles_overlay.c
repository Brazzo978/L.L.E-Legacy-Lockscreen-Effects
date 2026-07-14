#include "ripple_gles_overlay.h"

#include "ripple_gles_overlay_shader.h"
#include "ripple_gles_shaders.h"

#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

typedef struct OverlayMeshLocations {
    GLint position;
    GLint heights;
} OverlayMeshLocations;

static void set_error(char *error, size_t error_size, const char *format, ...) {
    if (error == NULL || error_size == 0) {
        return;
    }
    va_list args;
    va_start(args, format);
    (void) vsnprintf(error, error_size, format, args);
    va_end(args);
}

static void clear_error(char *error, size_t error_size) {
    if (error != NULL && error_size != 0) {
        error[0] = '\0';
    }
}

static void drain_gl_errors(void) {
    /* GLES has a finite error flag set; keep the guard for broken drivers. */
    for (unsigned int count = 0; count < 16U; ++count) {
        if (glGetError() == GL_NO_ERROR) {
            return;
        }
    }
}

static bool capture_gl_error(const char *where, char *error, size_t error_size) {
    const GLenum code = glGetError();
    if (code == GL_NO_ERROR) {
        return true;
    }
    set_error(error, error_size, "%s glError=0x%04x", where, (unsigned int) code);
    drain_gl_errors();
    return false;
}

static GLuint compile_shader(
        GLenum type,
        const char *source,
        const char *label,
        char *error,
        size_t error_size) {
    const GLuint shader = glCreateShader(type);
    if (shader == 0) {
        set_error(error, error_size, "%s glCreateShader returned 0", label);
        return 0;
    }
    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled == GL_TRUE) {
        return shader;
    }

    GLchar log[2048];
    GLsizei length = 0;
    log[0] = '\0';
    glGetShaderInfoLog(shader, (GLsizei) sizeof(log), &length, log);
    set_error(error, error_size, "%s compile failed: %s", label, log);
    glDeleteShader(shader);
    return 0;
}

static GLuint create_overlay_program(char *error, size_t error_size) {
    GLuint vertex = compile_shader(
            GL_VERTEX_SHADER,
            lle_ripple_normal_vertex_shader,
            "overlay-normal vertex",
            error,
            error_size);
    if (vertex == 0) {
        return 0;
    }
    GLuint fragment = compile_shader(
            GL_FRAGMENT_SHADER,
            lle_ripple_overlay_normal_fragment_shader,
            "overlay-normal fragment",
            error,
            error_size);
    if (fragment == 0) {
        glDeleteShader(vertex);
        return 0;
    }

    GLuint program = glCreateProgram();
    if (program == 0) {
        set_error(error, error_size, "overlay-normal glCreateProgram returned 0");
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return 0;
    }
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glBindAttribLocation(program, 0, "vertex");
    glBindAttribLocation(program, 1, "texCoord");
    glLinkProgram(program);

    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (linked != GL_TRUE) {
        GLchar log[2048];
        GLsizei length = 0;
        log[0] = '\0';
        glGetProgramInfoLog(program, (GLsizei) sizeof(log), &length, log);
        set_error(error, error_size, "overlay-normal link failed: %s", log);
        glDeleteProgram(program);
        program = 0;
    }
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    return program;
}

void lle_ripple_overlay_default_options(LleRippleOverlayOptions *options) {
    if (options == NULL) {
        return;
    }
    options->mask_low = 0.035f;
    options->mask_high = 0.18f;
    options->opacity = 1.0f;
}

void lle_ripple_overlay_abandon(LleRippleOverlay *overlay) {
    if (overlay != NULL) {
        memset(overlay, 0, sizeof(*overlay));
    }
}

void lle_ripple_overlay_destroy(LleRippleOverlay *overlay) {
    if (overlay == NULL) {
        return;
    }
    if (overlay->normal_program != 0) {
        glDeleteProgram(overlay->normal_program);
    }
    lle_ripple_overlay_abandon(overlay);
}

bool lle_ripple_overlay_init(
        LleRippleOverlay *overlay,
        char *error,
        size_t error_size) {
    if (overlay == NULL) {
        set_error(error, error_size, "overlay is null");
        return false;
    }
    clear_error(error, error_size);
    drain_gl_errors();
    memset(overlay, 0, sizeof(*overlay));
    overlay->normal_program = create_overlay_program(error, error_size);
    if (overlay->normal_program == 0) {
        return false;
    }
    if (!capture_gl_error("lle_ripple_overlay_init", error, error_size)) {
        lle_ripple_overlay_destroy(overlay);
        return false;
    }
    return true;
}

static bool valid_overlay_options(const LleRippleOverlayOptions *options) {
    return options != NULL
            && isfinite(options->mask_low)
            && isfinite(options->mask_high)
            && isfinite(options->opacity)
            && options->mask_low >= 0.0f
            && options->mask_high > options->mask_low
            && options->opacity >= 0.0f
            && options->opacity <= 1.0f;
}

static bool valid_render_args(const LleRippleRenderArgs *args) {
    if (args == NULL
            || args->vertices == NULL
            || args->heights == NULL
            || args->indices == NULL
            || args->mvp == NULL
            || args->with_ink
            || args->vertex_float_count <= 0
            || args->height_float_count != args->vertex_float_count
            || args->vertex_float_count % 3 != 0
            || args->index_count <= 0
            || args->viewport_width <= 0
            || args->viewport_height <= 0
            || args->mesh_width <= 0
            || args->mesh_height <= 0
            || args->detail_width <= 0
            || args->detail_height <= 0) {
        return false;
    }
    const GLsizei vertex_count = args->vertex_float_count / 3;
    for (GLsizei index = 0; index < args->index_count; ++index) {
        if ((GLsizei) args->indices[index] >= vertex_count) {
            return false;
        }
    }
    return true;
}

static void bind_sampler(GLuint program, const char *name, GLenum unit, GLuint texture) {
    glActiveTexture(unit);
    glBindTexture(GL_TEXTURE_2D, texture);
    glUniform1i(glGetUniformLocation(program, name), (GLint) (unit - GL_TEXTURE0));
}

static void cleanup_mesh(OverlayMeshLocations locations) {
    glDisableVertexAttribArray((GLuint) locations.heights);
    glDisableVertexAttribArray((GLuint) locations.position);
    for (int unit = 1; unit >= 0; --unit) {
        glActiveTexture((GLenum) (GL_TEXTURE0 + unit));
        glBindTexture(GL_TEXTURE_2D, 0);
    }
}

static bool render_transparent_delta(
        LleRippleGles *gles,
        LleRippleOverlay *overlay,
        const LleRippleRenderArgs *args,
        const LleRippleOverlayOptions *options,
        char *error,
        size_t error_size) {
    if (gles == NULL
            || overlay == NULL
            || overlay->normal_program == 0
            || gles->position_vbo == 0
            || gles->height_vbo == 0
            || gles->index_ibo == 0
            || gles->background_texture == 0
            || gles->water_texture == 0
            || !valid_render_args(args)
            || !valid_overlay_options(options)) {
        set_error(error, error_size, "invalid transparent ripple render state");
        return false;
    }

    clear_error(error, error_size);
    drain_gl_errors();
    const GLuint program = overlay->normal_program;
    glViewport(0, 0, args->viewport_width, args->viewport_height);
    glUseProgram(program);
    glUniform1f(glGetUniformLocation(program, "uMESH_SIZE_WIDTH"), (GLfloat) args->mesh_width);
    glUniform1f(glGetUniformLocation(program, "uMESH_SIZE_HEIGHT"), (GLfloat) args->mesh_height);
    glUniform1f(glGetUniformLocation(program, "uNUM_DETAILS_WIDTH"), (GLfloat) args->detail_width);
    glUniform1f(glGetUniformLocation(program, "uNUM_DETAILS_HEIGHT"), (GLfloat) args->detail_height);
    glUniform1f(glGetUniformLocation(program, "uRefractiveIndex"), args->refractive_index);

    OverlayMeshLocations locations;
    locations.position = glGetAttribLocation(program, "aPosition");
    locations.heights = glGetAttribLocation(program, "aHeights");
    const GLint mvp = glGetUniformLocation(program, "uMVPMatrix");
    if (locations.position < 0 || locations.heights < 0 || mvp < 0) {
        set_error(error, error_size, "overlay-normal shader locations are inactive");
        return false;
    }
    glUniformMatrix4fv(mvp, 1, GL_FALSE, args->mvp);

    glBindBuffer(GL_ARRAY_BUFFER, gles->position_vbo);
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) args->vertex_float_count * (GLsizeiptr) sizeof(float),
            args->vertices,
            GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, gles->height_vbo);
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) args->height_float_count * (GLsizeiptr) sizeof(float),
            args->heights,
            GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gles->index_ibo);
    glBufferData(
            GL_ELEMENT_ARRAY_BUFFER,
            (GLsizeiptr) args->index_count * (GLsizeiptr) sizeof(uint16_t),
            args->indices,
            GL_DYNAMIC_DRAW);

    glBindBuffer(GL_ARRAY_BUFFER, gles->height_vbo);
    glVertexAttribPointer((GLuint) locations.heights, 3, GL_FLOAT, GL_FALSE, 0, (const void *) 0);
    glEnableVertexAttribArray((GLuint) locations.heights);
    glBindBuffer(GL_ARRAY_BUFFER, gles->position_vbo);
    glVertexAttribPointer((GLuint) locations.position, 3, GL_FLOAT, GL_FALSE, 0, (const void *) 0);
    glEnableVertexAttribArray((GLuint) locations.position);

    glUniform1f(
            glGetUniformLocation(program, "alphaRatio1"),
            args->alpha_ratio_1 * args->reflection_ratio);
    glUniform1f(glGetUniformLocation(program, "fresnelRatio"), args->fresnel_ratio);
    glUniform1f(glGetUniformLocation(program, "specularRatio"), args->specular_ratio);
    glUniform1f(glGetUniformLocation(program, "exponent"), args->exponent_ratio);
    glUniform1f(glGetUniformLocation(program, "viewportHeight"), (GLfloat) args->viewport_height);
    glUniform1f(glGetUniformLocation(program, "uOverlayMaskLow"), options->mask_low);
    glUniform1f(glGetUniformLocation(program, "uOverlayMaskHigh"), options->mask_high);
    glUniform1f(glGetUniformLocation(program, "uOverlayOpacity"), options->opacity);

    bind_sampler(program, "sBGTexture", GL_TEXTURE0, gles->background_texture);
    bind_sampler(program, "sWaterTexture", GL_TEXTURE1, gles->water_texture);

    /*
     * The framebuffer stores premultiplied pixels for later SurfaceFlinger
     * composition. GL blending here would blend the layer with itself; the
     * host clears it to transparent before this draw.
     */
    glDisable(GL_BLEND);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gles->index_ibo);
    glDrawElements(GL_TRIANGLES, args->index_count, GL_UNSIGNED_SHORT, (const void *) 0);
    cleanup_mesh(locations);
    return capture_gl_error("lle_ripple_overlay_render", error, error_size);
}

bool lle_ripple_gles_render_normal_variant(
        LleRippleGles *gles,
        LleRippleOverlay *overlay,
        LleRippleNormalCompositeMode mode,
        const LleRippleRenderArgs *args,
        const LleRippleOverlayOptions *options,
        char *error,
        size_t error_size) {
    switch (mode) {
        case LLE_RIPPLE_NORMAL_COMPOSITE_SAMSUNG_EXACT:
            return lle_ripple_gles_render(gles, args, error, error_size);
        case LLE_RIPPLE_NORMAL_COMPOSITE_TRANSPARENT_DELTA:
            return render_transparent_delta(
                    gles,
                    overlay,
                    args,
                    options,
                    error,
                    error_size);
        default:
            set_error(error, error_size, "unknown normal compositing mode");
            return false;
    }
}
