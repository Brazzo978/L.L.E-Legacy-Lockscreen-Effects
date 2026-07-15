#include "ripple_gles_pipeline.h"
#include "ripple_gles_shaders.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>

typedef struct MeshLocations {
    GLint position;
    GLint heights;
} MeshLocations;

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
    GLuint shader = glCreateShader(type);
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

static GLuint create_program(
        const char *vertex_source,
        const char *fragment_source,
        const char *label,
        char *error,
        size_t error_size) {
    GLuint vertex = compile_shader(GL_VERTEX_SHADER, vertex_source, label, error, error_size);
    if (vertex == 0) {
        return 0;
    }
    GLuint fragment = compile_shader(GL_FRAGMENT_SHADER, fragment_source, label, error, error_size);
    if (fragment == 0) {
        glDeleteShader(vertex);
        return 0;
    }
    GLuint program = glCreateProgram();
    if (program == 0) {
        set_error(error, error_size, "%s glCreateProgram returned 0", label);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return 0;
    }

    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    // The original helper binds these names for every program. They are active
    // only in the shared offscreen quad vertex shader.
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
        set_error(error, error_size, "%s link failed: %s", label, log);
        glDeleteProgram(program);
        program = 0;
    }

    // Visual behavior is identical; unlike the ARM32 helper, the port does not
    // intentionally leak successfully linked shader objects.
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    return program;
}

static GLuint *texture_slot(LleRippleGles *gles, LleRippleTextureSlot slot) {
    if (gles == NULL) {
        return NULL;
    }
    switch (slot) {
        case LLE_RIPPLE_TEXTURE_BACKGROUND: return &gles->background_texture;
        case LLE_RIPPLE_TEXTURE_WATER: return &gles->water_texture;
        case LLE_RIPPLE_TEXTURE_GRAVITY: return &gles->gravity_texture;
        case LLE_RIPPLE_TEXTURE_CAUSTIC_1: return &gles->caustic_texture;
        case LLE_RIPPLE_TEXTURE_CAUSTIC_2: return &gles->caustic_texture_2;
        default: return NULL;
    }
}

void lle_ripple_gles_abandon(LleRippleGles *gles) {
    if (gles != NULL) {
        memset(gles, 0, sizeof(*gles));
    }
}

void lle_ripple_gles_destroy(LleRippleGles *gles) {
    if (gles == NULL) {
        return;
    }
    const GLuint programs[] = {
            gles->normal_program,
            gles->ink_program,
            gles->advect_density_program,
            gles->add_ink_program,
            gles->gravity_program
    };
    for (size_t index = 0; index < sizeof(programs) / sizeof(programs[0]); ++index) {
        if (programs[index] != 0) {
            glDeleteProgram(programs[index]);
        }
    }
    const GLuint buffers[] = {
            gles->position_vbo,
            gles->height_vbo,
            gles->index_ibo,
            gles->quad_vbo
    };
    glDeleteBuffers((GLsizei) (sizeof(buffers) / sizeof(buffers[0])), buffers);
    const GLuint textures[] = {
            gles->background_texture,
            gles->water_texture,
            gles->gravity_texture,
            gles->caustic_texture,
            gles->caustic_texture_2
    };
    glDeleteTextures((GLsizei) (sizeof(textures) / sizeof(textures[0])), textures);
    lle_ripple_gles_abandon(gles);
}

bool lle_ripple_gles_init(LleRippleGles *gles, char *error, size_t error_size) {
    if (gles == NULL) {
        set_error(error, error_size, "gles is null");
        return false;
    }
    clear_error(error, error_size);
    drain_gl_errors();
    memset(gles, 0, sizeof(*gles));

    gles->normal_program = create_program(
            lle_ripple_normal_vertex_shader,
            lle_ripple_normal_fragment_shader,
            "normal",
            error,
            error_size);
    if (gles->normal_program == 0) goto fail;
    gles->ink_program = create_program(
            lle_ripple_normal_vertex_shader,
            lle_ripple_ink_fragment_shader,
            "ink",
            error,
            error_size);
    if (gles->ink_program == 0) goto fail;
    gles->advect_density_program = create_program(
            lle_ripple_quad_vertex_shader,
            lle_ripple_advect_density_fragment_shader,
            "advect-density",
            error,
            error_size);
    if (gles->advect_density_program == 0) goto fail;
    gles->add_ink_program = create_program(
            lle_ripple_quad_vertex_shader,
            lle_ripple_add_ink_fragment_shader,
            "add-ink",
            error,
            error_size);
    if (gles->add_ink_program == 0) goto fail;
    gles->gravity_program = create_program(
            lle_ripple_gravity_vertex_shader,
            lle_ripple_gravity_fragment_shader,
            "gravity",
            error,
            error_size);
    if (gles->gravity_program == 0) goto fail;

    glGenBuffers(1, &gles->position_vbo);
    glGenBuffers(1, &gles->height_vbo);
    glGenBuffers(1, &gles->index_ibo);
    glGenBuffers(1, &gles->quad_vbo);
    glBindBuffer(GL_ARRAY_BUFFER, gles->quad_vbo);
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) sizeof(lle_ripple_quad_vertices),
            lle_ripple_quad_vertices,
            GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    LleRippleGravityLocations *locations = &gles->gravity_locations;
    locations->gravity_texture = glGetUniformLocation(gles->gravity_program, "gravityTexture");
    locations->caustic_texture = glGetUniformLocation(gles->gravity_program, "causticTexture");
    locations->caustic_texture_2 = glGetUniformLocation(gles->gravity_program, "causticTexture2");
    locations->caustic_time_ratio = glGetUniformLocation(gles->gravity_program, "uCausticTimeRatio");
    locations->caustic_time_ratio_2 = glGetUniformLocation(gles->gravity_program, "uCausticTimeRatio2");
    locations->caustic_time_mix = glGetUniformLocation(gles->gravity_program, "uCausticTimeMix");
    locations->reference_point = glGetUniformLocation(gles->gravity_program, "uReferencePoint");
    locations->tex_move = glGetUniformLocation(gles->gravity_program, "uTexMove");
    locations->gravity_direction = glGetUniformLocation(gles->gravity_program, "uGravityDirection");
    locations->water_brightness = glGetUniformLocation(gles->gravity_program, "uWaterbrightness");

    if (!capture_gl_error("lle_ripple_gles_init", error, error_size)) goto fail;
    return true;

fail:
    lle_ripple_gles_destroy(gles);
    return false;
}

void lle_ripple_gles_free_texture(LleRippleGles *gles, LleRippleTextureSlot slot) {
    GLuint *texture = texture_slot(gles, slot);
    if (texture != NULL && *texture != 0) {
        glDeleteTextures(1, texture);
        *texture = 0;
    }
}

bool lle_ripple_gles_upload_rgba(
        LleRippleGles *gles,
        LleRippleTextureSlot slot,
        GLsizei width,
        GLsizei height,
        const void *pixels,
        char *error,
        size_t error_size) {
    GLuint *texture = texture_slot(gles, slot);
    if (texture == NULL || width <= 0 || height <= 0 || pixels == NULL) {
        set_error(error, error_size, "invalid RGBA upload arguments");
        return false;
    }
    clear_error(error, error_size);
    drain_gl_errors();
    lle_ripple_gles_free_texture(gles, slot);
    glGenTextures(1, texture);
    glBindTexture(GL_TEXTURE_2D, *texture);
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            width,
            height,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            pixels);
    // External Samsung bitmaps use the float form and this exact order.
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, (GLfloat) GL_CLAMP_TO_EDGE);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, (GLfloat) GL_CLAMP_TO_EDGE);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, (GLfloat) GL_LINEAR);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, (GLfloat) GL_LINEAR);
    if (!capture_gl_error("lle_ripple_gles_upload_rgba", error, error_size)) {
        lle_ripple_gles_free_texture(gles, slot);
        return false;
    }
    return true;
}

bool lle_ripple_gles_create_surface(
        LleRippleSurface *surface,
        GLsizei width,
        GLsizei height,
        char *error,
        size_t error_size) {
    if (surface == NULL || width <= 0 || height <= 0) {
        set_error(error, error_size, "invalid surface dimensions");
        return false;
    }
    clear_error(error, error_size);
    drain_gl_errors();
    memset(surface, 0, sizeof(*surface));
    surface->width = width;
    surface->height = height;

    glGenFramebuffers(1, &surface->framebuffer);
    glBindFramebuffer(GL_FRAMEBUFFER, surface->framebuffer);
    glGenTextures(1, &surface->texture);
    glBindTexture(GL_TEXTURE_2D, surface->texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            width,
            height,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            NULL);

    // ARM32 creates and binds an empty renderbuffer but does not attach it.
    // Keep the name so the port can clean it up instead of reproducing the leak.
    glGenRenderbuffers(1, &surface->renderbuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, surface->renderbuffer);
    glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            surface->texture,
            0);
    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        set_error(error, error_size, "framebuffer incomplete: 0x%04x", (unsigned int) status);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        lle_ripple_gles_destroy_surface(surface);
        return false;
    }
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (!capture_gl_error("lle_ripple_gles_create_surface", error, error_size)) {
        lle_ripple_gles_destroy_surface(surface);
        return false;
    }
    return true;
}

void lle_ripple_gles_clear_surface(
        const LleRippleSurface *surface,
        float red,
        float green,
        float blue,
        float alpha) {
    if (surface == NULL || surface->framebuffer == 0) {
        return;
    }
    glBindFramebuffer(GL_FRAMEBUFFER, surface->framebuffer);
    glClearColor(red, green, blue, alpha);
    glClear(GL_COLOR_BUFFER_BIT);
}

void lle_ripple_gles_destroy_surface(LleRippleSurface *surface) {
    if (surface == NULL) {
        return;
    }
    if (surface->framebuffer != 0) glDeleteFramebuffers(1, &surface->framebuffer);
    if (surface->texture != 0) glDeleteTextures(1, &surface->texture);
    if (surface->renderbuffer != 0) glDeleteRenderbuffers(1, &surface->renderbuffer);
    memset(surface, 0, sizeof(*surface));
}

static bool valid_render_args(const LleRippleGles *gles, const LleRippleRenderArgs *args) {
    return gles != NULL
            && args != NULL
            && args->vertices != NULL
            && args->heights != NULL
            && args->indices != NULL
            && args->mvp != NULL
            && args->vertex_float_count > 0
            && args->height_float_count > 0
            && args->index_count > 0
            && args->viewport_width > 0
            && args->viewport_height > 0;
}

static MeshLocations prepare_mesh(
        LleRippleGles *gles,
        GLuint program,
        const LleRippleRenderArgs *args) {
    glViewport(0, 0, args->viewport_width, args->viewport_height);
    glUseProgram(program);
    glUniform1f(glGetUniformLocation(program, "uMESH_SIZE_WIDTH"), (GLfloat) args->mesh_width);
    glUniform1f(glGetUniformLocation(program, "uMESH_SIZE_HEIGHT"), (GLfloat) args->mesh_height);
    glUniform1f(glGetUniformLocation(program, "uNUM_DETAILS_WIDTH"), (GLfloat) args->detail_width);
    glUniform1f(glGetUniformLocation(program, "uNUM_DETAILS_HEIGHT"), (GLfloat) args->detail_height);
    glUniform1f(glGetUniformLocation(program, "uRefractiveIndex"), args->refractive_index);

    MeshLocations locations;
    locations.position = glGetAttribLocation(program, "aPosition");
    locations.heights = glGetAttribLocation(program, "aHeights");
    const GLint mvp = glGetUniformLocation(program, "uMVPMatrix");
    glUniformMatrix4fv(mvp, 1, GL_FALSE, args->mvp);

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ARRAY_BUFFER, gles->position_vbo);
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) args->vertex_float_count * (GLsizeiptr) sizeof(float),
            args->vertices,
            GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ARRAY_BUFFER, gles->height_vbo);
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) args->height_float_count * (GLsizeiptr) sizeof(float),
            args->heights,
            GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gles->index_ibo);
    glBufferData(
            GL_ELEMENT_ARRAY_BUFFER,
            (GLsizeiptr) args->index_count * (GLsizeiptr) sizeof(uint16_t),
            args->indices,
            GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

    glBindBuffer(GL_ARRAY_BUFFER, gles->height_vbo);
    glVertexAttribPointer((GLuint) locations.heights, 3, GL_FLOAT, GL_FALSE, 0, (const void *) 0);
    glEnableVertexAttribArray((GLuint) locations.heights);
    glBindBuffer(GL_ARRAY_BUFFER, gles->position_vbo);
    glVertexAttribPointer((GLuint) locations.position, 3, GL_FLOAT, GL_FALSE, 0, (const void *) 0);
    glEnableVertexAttribArray((GLuint) locations.position);
    return locations;
}

static void set_optical_uniforms(GLuint program, const LleRippleRenderArgs *args) {
    glUniform1f(
            glGetUniformLocation(program, "alphaRatio1"),
            args->alpha_ratio_1 * args->reflection_ratio);
    glUniform1f(
            glGetUniformLocation(program, "alphaRatio2"),
            args->alpha_ratio_2 * (1.0f - args->reflection_ratio));
    glUniform1f(glGetUniformLocation(program, "fresnelRatio"), args->fresnel_ratio);
    glUniform1f(glGetUniformLocation(program, "specularRatio"), args->specular_ratio);
    glUniform1f(glGetUniformLocation(program, "exponent"), args->exponent_ratio);
    glUniform1f(glGetUniformLocation(program, "viewportHeight"), (GLfloat) args->viewport_height);
}

static void bind_sampler(GLuint program, const char *name, GLenum unit, GLuint texture) {
    glActiveTexture(unit);
    glBindTexture(GL_TEXTURE_2D, texture);
    glUniform1i(glGetUniformLocation(program, name), (GLint) (unit - GL_TEXTURE0));
}

static void cleanup_mesh_textures(MeshLocations locations, int highest_unit) {
    glDisableVertexAttribArray((GLuint) locations.heights);
    glDisableVertexAttribArray((GLuint) locations.position);
    for (int unit = highest_unit; unit >= 0; --unit) {
        glActiveTexture((GLenum) (GL_TEXTURE0 + unit));
        glBindTexture(GL_TEXTURE_2D, 0);
    }
}

bool lle_ripple_gles_render(
        LleRippleGles *gles,
        const LleRippleRenderArgs *args,
        char *error,
        size_t error_size) {
    if (!valid_render_args(gles, args)
            || gles->background_texture == 0
            || gles->water_texture == 0
            || (args->with_ink && (args->density == NULL || args->density->texture == 0))) {
        set_error(error, error_size, "invalid ripple render state");
        return false;
    }
    clear_error(error, error_size);
    drain_gl_errors();
    const GLuint program = args->with_ink ? gles->ink_program : gles->normal_program;
    if (args->with_ink && !gles->ink_blend_initialized) {
        // ARM32 InitializeGPU performs this only for the ink/fluid mode. It
        // configures the factors but does not enable blending.
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        gles->ink_blend_initialized = true;
    }
    MeshLocations locations = prepare_mesh(gles, program, args);

    if (args->with_ink) {
        glUniform2f(
                glGetUniformLocation(program, "Scale"),
                1.0f / (float) args->viewport_width,
                1.0f / (float) args->viewport_height);
        const float numerator = 1.5f - args->clear_ink;
        glUniform3f(
                glGetUniformLocation(program, "ink_color"),
                numerator / args->ink_red - 1.0f,
                numerator / args->ink_green - 1.0f,
                numerator / args->ink_blue - 1.0f);
        glUniform1f(
                glGetUniformLocation(program, "intensity"),
                args->ink_intensity_a * args->ink_intensity_b);
        bind_sampler(program, "Density", GL_TEXTURE2, args->density->texture);
    }

    set_optical_uniforms(program, args);
    bind_sampler(program, "sBGTexture", GL_TEXTURE0, gles->background_texture);
    bind_sampler(program, "sWaterTexture", GL_TEXTURE1, gles->water_texture);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gles->index_ibo);
    glDrawElements(GL_TRIANGLES, args->index_count, GL_UNSIGNED_SHORT, (const void *) 0);
    cleanup_mesh_textures(locations, 3);
    return capture_gl_error("lle_ripple_gles_render", error, error_size);
}

bool lle_ripple_gles_render_gravity(
        LleRippleGles *gles,
        const LleRippleGravityRenderArgs *args,
        char *error,
        size_t error_size) {
    if (args == NULL
            || !valid_render_args(gles, &args->base)
            || gles->background_texture == 0
            || gles->water_texture == 0
            || gles->gravity_texture == 0
            || gles->caustic_texture == 0
            || gles->caustic_texture_2 == 0) {
        set_error(error, error_size, "invalid gravity render state");
        return false;
    }
    clear_error(error, error_size);
    drain_gl_errors();
    const GLuint program = gles->gravity_program;
    MeshLocations locations = prepare_mesh(gles, program, &args->base);
    set_optical_uniforms(program, &args->base);
    bind_sampler(program, "sBGTexture", GL_TEXTURE0, gles->background_texture);
    bind_sampler(program, "sWaterTexture", GL_TEXTURE1, gles->water_texture);

    const LleRippleGravityLocations *uniforms = &gles->gravity_locations;
    glUniform1f(uniforms->caustic_time_ratio, args->caustic_time_ratio);
    glUniform1f(uniforms->caustic_time_ratio_2, args->caustic_time_ratio_2);
    glUniform1f(uniforms->caustic_time_mix, args->caustic_time_mix);
    glUniform1f(uniforms->reference_point, args->reference_point);
    glUniform1f(uniforms->tex_move, args->tex_move);
    glUniform1i(uniforms->gravity_direction, args->gravity_direction ? 1 : 0);
    glUniform1f(uniforms->water_brightness, args->water_brightness);
    glActiveTexture(GL_TEXTURE2);
    glBindTexture(GL_TEXTURE_2D, gles->gravity_texture);
    glUniform1i(uniforms->gravity_texture, 2);
    glActiveTexture(GL_TEXTURE3);
    glBindTexture(GL_TEXTURE_2D, gles->caustic_texture);
    glUniform1i(uniforms->caustic_texture, 3);
    glActiveTexture(GL_TEXTURE4);
    glBindTexture(GL_TEXTURE_2D, gles->caustic_texture_2);
    glUniform1i(uniforms->caustic_texture_2, 4);

    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gles->index_ibo);
    glDrawElements(GL_TRIANGLES, args->base.index_count, GL_UNSIGNED_SHORT, (const void *) 0);
    cleanup_mesh_textures(locations, 4);
    return capture_gl_error("lle_ripple_gles_render_gravity", error, error_size);
}

static void prepare_quad(LleRippleGles *gles) {
    glBindBuffer(GL_ARRAY_BUFFER, gles->quad_vbo);
    // ARM32 uploads this constant block for each pass with GL_DYNAMIC_DRAW.
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) sizeof(lle_ripple_quad_vertices),
            lle_ripple_quad_vertices,
            GL_DYNAMIC_DRAW);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(0, 2, GL_SHORT, GL_FALSE, 8, (const void *) 0);
    glVertexAttribPointer(1, 2, GL_SHORT, GL_FALSE, 8, (const void *) 4);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    // The original disables only location 0 here and leaves location 1 enabled.
    glDisableVertexAttribArray(0);
}

static void cleanup_quad(void) {
    for (int unit = 3; unit >= 0; --unit) {
        glActiveTexture((GLenum) (GL_TEXTURE0 + unit));
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glUseProgram(0);
    glDisable(GL_BLEND);
}

bool lle_ripple_gles_advect_density(
        LleRippleGles *gles,
        const LleRippleAdvectDensityArgs *args,
        char *error,
        size_t error_size) {
    if (gles == NULL || args == NULL
            || args->velocity == NULL || args->velocity->texture == 0
            || args->source == NULL || args->source->texture == 0
            || args->destination == NULL || args->destination->framebuffer == 0) {
        set_error(error, error_size, "invalid AdvectDensity state");
        return false;
    }
    clear_error(error, error_size);
    drain_gl_errors();
    const GLuint program = gles->advect_density_program;
    glViewport(0, 0, args->destination->width, args->destination->height);
    glUseProgram(program);
    glUniform2f(glGetUniformLocation(program, "Scale"), args->scale_x, args->scale_y);
    glUniform2f(glGetUniformLocation(program, "TimeStep"), args->time_step_x, args->time_step_y);
    glUniform1f(glGetUniformLocation(program, "Dissipation"), args->dissipation);
    glUniform1f(glGetUniformLocation(program, "BackwardStepSize"), args->backward_step_size);
    glUniform2f(glGetUniformLocation(program, "center"), args->center_x, args->center_y);
    glUniform1i(glGetUniformLocation(program, "drag"), args->drag);
    glUniform1i(glGetUniformLocation(program, "SourceTexture"), 1);
    glBindFramebuffer(GL_FRAMEBUFFER, args->destination->framebuffer);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, args->velocity->texture);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, args->source->texture);
    prepare_quad(gles);
    cleanup_quad();
    return capture_gl_error("lle_ripple_gles_advect_density", error, error_size);
}

bool lle_ripple_gles_add_ink(
        LleRippleGles *gles,
        const LleRippleAddInkArgs *args,
        char *error,
        size_t error_size) {
    if (gles == NULL || args == NULL
            || args->source == NULL || args->source->texture == 0
            || args->destination == NULL || args->destination->framebuffer == 0) {
        set_error(error, error_size, "invalid AddInk state");
        return false;
    }
    clear_error(error, error_size);
    drain_gl_errors();
    const GLuint program = gles->add_ink_program;
    glViewport(0, 0, args->destination->width, args->destination->height);
    glUseProgram(program);
    glUniform2f(glGetUniformLocation(program, "current"), args->current_x, args->current_y);
    glUniform2f(glGetUniformLocation(program, "previous"), args->previous_x, args->previous_y);
    glUniform2f(glGetUniformLocation(program, "normal"), args->normal_x, args->normal_y);
    glUniform1f(glGetUniformLocation(program, "len"), args->length);
    glUniform1f(glGetUniformLocation(program, "Radius"), args->radius);
    glUniform1f(glGetUniformLocation(program, "ImpulseDensity"), args->impulse_density);
    glUniform2f(glGetUniformLocation(program, "Scale"), args->scale_x, args->scale_y);
    glUniform1i(glGetUniformLocation(program, "mode"), args->mode);
    glUniform1i(glGetUniformLocation(program, "Source"), 0);
    glBindFramebuffer(GL_FRAMEBUFFER, args->destination->framebuffer);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, args->source->texture);
    prepare_quad(gles);
    cleanup_quad();
    return capture_gl_error("lle_ripple_gles_add_ink", error, error_size);
}
