#include "lle_colour_gles.h"

#include <android/bitmap.h>
#include <math.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum {
    LLE_COLOUR_BUFFER_QUAD = 0,
    LLE_COLOUR_BUFFER_PARTICLES = 1,
    LLE_COLOUR_BUFFER_DENSITY = 2,
    LLE_COLOUR_BUFFER_STENCIL = 3,
    LLE_COLOUR_BUFFER_COUNT = 4
};

enum {
    LLE_COLOUR_PARTICLE_STRIDE_FLOATS = 10,
    LLE_COLOUR_DENSITY_VERTEX_STRIDE_FLOATS = 4,
    LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE = 6,
    LLE_COLOUR_STENCIL_SEGMENTS = 20,
    LLE_COLOUR_STENCIL_VERTICES = LLE_COLOUR_STENCIL_SEGMENTS + 2
};

static const float LLE_COLOUR_STENCIL_RADIUS = 0.00000575f;
static const float LLE_COLOUR_STENCIL_SCALE_FROM_DENSITY = 150.0f;

static const char *LLE_COLOUR_QUAD_VERTEX_SHADER =
        "precision mediump float;\n"
        "attribute vec2 aPosition;\n"
        "attribute vec2 aTexUV;\n"
        "varying highp vec2 vTexUV;\n"
        "void main() {\n"
        "  vTexUV = aTexUV;\n"
        "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
        "}\n";

/*
 * Recovered SPDrawColourDropletBlur math. The tiny render target plus linear
 * upsampling supplies the spatial blur; this shader supplies the stock colour
 * expansion.
 */
static const char *LLE_COLOUR_ENHANCE_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "uniform sampler2D uBackground;\n"
        "uniform float uSaturation;\n"
        "uniform float uBrightness;\n"
        "uniform float uMinValue;\n"
        "varying highp vec2 vTexUV;\n"
        "vec3 rgb2hsv(vec3 c) {\n"
        "  vec4 K = vec4(0.0, -0.3333333333333333,"
        " 0.6666666666666667, -1.0);\n"
        "  vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy),"
        " step(c.b, c.g));\n"
        "  vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx),"
        " step(p.x, c.r));\n"
        "  float d = q.x - min(q.w, q.y);\n"
        "  float e = 1.0e-10;\n"
        "  return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)),"
        " d / (q.x + e), q.x);\n"
        "}\n"
        "vec3 hsv2rgb(vec3 c) {\n"
        "  vec4 K = vec4(1.0, 0.6666666666666667,"
        " 0.3333333333333333, 3.0);\n"
        "  vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);\n"
        "  return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);\n"
        "}\n"
        "void main() {\n"
        "  vec4 color = texture2D(uBackground, vTexUV);\n"
        "  vec3 hsv = rgb2hsv(color.rgb);\n"
        "  hsv.y *= uSaturation;\n"
        "  hsv.z *= uBrightness;\n"
        "  hsv.z = max(hsv.z, uMinValue);\n"
        "  gl_FragColor = vec4(hsv2rgb(hsv), 1.0);\n"
        "}\n";

static const char *LLE_COLOUR_COPY_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "uniform sampler2D uTexture;\n"
        "varying highp vec2 vTexUV;\n"
        "void main() {\n"
        "  gl_FragColor = texture2D(uTexture, vTexUV);\n"
        "}\n";

static const char *LLE_COLOUR_STENCIL_VERTEX_SHADER =
        "uniform mediump mat4 uMVPMatrix;\n"
        "attribute vec4 aPosition;\n"
        "attribute vec3 aPositionScale;\n"
        "void main() {\n"
        "  mat4 modelMatrix = mat4("
        "aPositionScale.z, 0.0, 0.0, 0.0,"
        "0.0, aPositionScale.z, 0.0, 0.0,"
        "0.0, 0.0, 1.0, 0.0,"
        "aPositionScale.x, aPositionScale.y, 0.0, 1.0);\n"
        "  gl_Position = uMVPMatrix * modelMatrix * aPosition;\n"
        "}\n";

static const char *LLE_COLOUR_STENCIL_FRAGMENT_SHADER =
        "precision lowp float;\n"
        "void main() {\n"
        "  gl_FragColor = vec4(1.0);\n"
        "}\n";

static const char *LLE_COLOUR_PARTICLE_VERTEX_SHADER =
        "precision mediump float;\n"
        "attribute vec2 aPosition;\n"
        "attribute float aPointSize;\n"
        "attribute float aAlpha;\n"
        "attribute vec2 aColorPosition;\n"
        "attribute float aFlags;\n"
        "uniform vec2 uSurfaceSize;\n"
        "uniform float uPointScale;\n"
        "varying mediump vec2 vCenterUV;\n"
        "varying mediump vec2 vColorUV;\n"
        "varying mediump float vAlpha;\n"
        "void main() {\n"
        "  vec2 uv = aPosition / uSurfaceSize;\n"
        "  vec2 colorUV = aColorPosition / uSurfaceSize;\n"
        /*
         * Mesh helper 0x59528 writes the group-average centre into both the
         * main-particle color UV and direction-centre attributes. Satellites
         * keep their own rendered position as the direction centre and their
         * fixed birth coordinate as color UV.
         */
        "  vCenterUV = mix(uv, colorUV, step(0.5, aFlags));\n"
        "  vColorUV = colorUV;\n"
        "  vAlpha = aAlpha;\n"
        "  gl_PointSize = aPointSize * uPointScale;\n"
        "  gl_Position = vec4(uv.x * 2.0 - 1.0,"
        " 1.0 - uv.y * 2.0, 0.0, 1.0);\n"
        "}\n";

/*
 * Stock SPDrawColourDropletDensity is indexed quad geometry, not point
 * sprites. Positions staged below are already in clip space and preserve the
 * recovered 0,1,2 / 2,3,1 triangle order.
 */
static const char *LLE_COLOUR_DENSITY_VERTEX_SHADER =
        "precision mediump float;\n"
        "attribute vec2 aPosition;\n"
        "attribute vec2 aTexUV;\n"
        "varying mediump vec2 vTexUV;\n"
        "void main() {\n"
        "  vTexUV = aTexUV;\n"
        "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
        "}\n";

static const char *LLE_COLOUR_DENSITY_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "uniform sampler2D uNormal;\n"
        "varying mediump vec2 vTexUV;\n"
        "void main() {\n"
        "  vec4 normalSample = texture2D(uNormal, vTexUV);\n"
        "  float alpha = normalSample.a * 0.75;\n"
        "  gl_FragColor = vec4(normalSample.rgb, alpha);\n"
        "}\n";

static const char *LLE_COLOUR_COLOR_DIRECTION_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "uniform sampler2D uNormal;\n"
        "uniform sampler2D uColorMap;\n"
        "uniform sampler2D uDensity;\n"
        "uniform vec2 uTargetSize;\n"
        "uniform vec2 uVelocity;\n"
        "uniform float uMode;\n"
        "varying mediump vec2 vCenterUV;\n"
        "varying mediump vec2 vColorUV;\n"
        "varying mediump float vAlpha;\n"
        "void main() {\n"
        /*
         * Stock colour/direction are true point sprites and sample the mask
         * directly in gl_PointCoord space (unlike the density quad above).
         */
        "  vec2 pointUV = gl_PointCoord.xy;\n"
        "  float mask = texture2D(uNormal, pointUV).a;\n"
        "  if (uMode < 0.5) {\n"
        /*
         * aColorPosition is Android top-left space. The enhanced-map pass
         * renders that top-left bitmap into an FBO, whose visual top is v=1.
         */
        "    vec3 color = texture2D("
        "uColorMap, vec2(vColorUV.x, 1.0 - vColorUV.y)).rgb;\n"
        "    gl_FragColor = vec4(color, mask);\n"
        "    return;\n"
        "  }\n"
        "  highp vec2 screenUV = vec2(gl_FragCoord.x / uTargetSize.x,"
        " 1.0 - gl_FragCoord.y / uTargetSize.y);\n"
        "  highp vec2 densityUV = vec2(screenUV.x, 1.0 - screenUV.y);\n"
        "  float hoi = texture2D(uDensity, densityUV).a;\n"
        "  if (hoi < 0.5) discard;\n"
        "  highp vec2 displacement = screenUV - vCenterUV;\n"
        "  highp vec2 direction = normalize(displacement);\n"
        /*
         * The stock GLSL contains a vColor.r == 1 branch, but helper 0x59528
         * never fills SPMesh::color and setShaderArrayMeshColor returns early.
         * The generic aColor value therefore stays zero and runtime always
         * executes this plain global-velocity branch.
         */
        "  float directional = dot(direction, uVelocity);\n"
        "  gl_FragColor = vec4("
        "0.0, 0.0, 0.0, directional * 0.8 * mask);\n"
        "}\n";

/*
 * Stock SPDrawColourDroplet math with a local transparent-overlay adaptation.
 * Pixels outside the metaball remain transparent and its displaced shadow is
 * encoded as a premultiplied delta.  Stock and the validated ARM32 transparent
 * oracle both keep the metaball itself opaque, which also prevents live
 * keyguard layers absent from the cached background from leaking through it.
 */
static const char *LLE_COLOUR_COMPOSITE_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "uniform sampler2D uBackground;\n"
        "uniform sampler2D uDensity;\n"
        "uniform sampler2D uColorDirection;\n"
        "uniform vec2 uInvSurfaceSize;\n"
        "uniform highp float uEdgeRatio;\n"
        "uniform float uRestore;\n"
        "uniform float uRefractionRatio;\n"
        "uniform highp float uTabScale;\n"
        "uniform highp vec2 uTabOffset;\n"
        "uniform highp float uEdgeOffsetRatio;\n"
        "uniform highp float uInnerShadowWidth;\n"
        "uniform float uShadowEnable;\n"
        "varying highp vec2 vTexUV;\n"
        "void main() {\n"
        "  vec2 densityUV = vec2(vTexUV.x, 1.0 - vTexUV.y);\n"
        "  vec4 densitySample = texture2D(uDensity, densityUV);\n"
        "  highp float hoi = densitySample.a;\n"
        "  highp float edgeOffset ="
        " (0.55 - 0.55 * uInnerShadowWidth) * uEdgeOffsetRatio;\n"
        "  float shadowOffset = 0.15 * uEdgeOffsetRatio;\n"
        "  highp float edgeRatio = (1.0 - uEdgeRatio) * edgeOffset;\n"
        "  highp float edgeRange = 0.5 + edgeOffset - edgeRatio;\n"
        "  float edgeDisplacement = max(edgeRange - 0.5, 0.0001);\n"
        "  float invEdgeDisplacement = 1.0 / edgeDisplacement;\n"
        "  if (hoi < 0.5) {\n"
        "    vec2 shadowUV = densityUV + vec2("
        "uInvSurfaceSize.x * 8.0 * uEdgeOffsetRatio * uRestore * 0.5,"
        "-uInvSurfaceSize.y * 8.0 * uEdgeOffsetRatio * uRestore);\n"
        "    float upside = texture2D(uDensity, shadowUV).a;\n"
        "    if (uShadowEnable > 0.5 && upside > 0.5) {\n"
        "      float shadowDenominator = max("
        "0.15 * uEdgeOffsetRatio -"
        " ((1.0 - uEdgeRatio) * shadowOffset), 0.0001);\n"
        "      float shadowMix = min("
        "(upside - 0.5) / shadowDenominator, 0.95) * 0.75;\n"
        "      gl_FragColor = vec4(0.0, 0.0, 0.0,"
        " shadowMix * 0.2);\n"
        "    } else {\n"
        "      gl_FragColor = vec4(0.0);\n"
        "    }\n"
        "    return;\n"
        "  }\n"
        "  vec3 normal = densitySample.rgb * 2.0 - 1.0;\n"
        "  vec3 refracted = -refract("
        "vec3(0.0, 0.0, -1.0), normal,"
        " 0.75001875046876171904297607440186)"
        " * 0.075 * uRefractionRatio;\n"
        /*
         * Stock rebuilds aTabScaledUV every update. It refracts this subtly
         * magnified/panned coordinate, not the raw fullscreen UV.
         */
        "  highp vec2 tabUV = vec2(1.0 - uTabScale) + uTabOffset"
        " + vTexUV * (2.0 * uTabScale - 1.0);\n"
        "  vec2 refractedUV = tabUV + refracted.xy;\n"
        "  vec3 refractedBackground ="
        " texture2D(uBackground, refractedUV).rgb;\n"
        "  highp vec4 colorDirection ="
        " texture2D(uColorDirection, densityUV);\n"
        "  float edgeSmooth ="
        " 1.0 - (hoi - 0.5) * invEdgeDisplacement;\n"
        "  if (edgeSmooth < 0.955) hoi += colorDirection.a * 0.5;\n"
        "  vec3 target = refractedBackground;\n"
        "  if (hoi < edgeRange) {\n"
        "    float smooth = pow("
        "1.0 - (hoi - 0.5) * invEdgeDisplacement, 2.0);\n"
        "    target = mix(refractedBackground, colorDirection.rgb, smooth);\n"
        "  }\n"
        "  gl_FragColor = vec4(target, 1.0);\n"
        "}\n";

static void colour_set_error(
        char *error, size_t error_size, const char *format, ...) {
    if (error == NULL || error_size == 0U) return;
    va_list arguments;
    va_start(arguments, format);
    (void) vsnprintf(error, error_size, format, arguments);
    va_end(arguments);
}

static void colour_clear_error(char *error, size_t error_size) {
    if (error != NULL && error_size > 0U) error[0] = '\0';
}

static void colour_drain_errors(void) {
    for (int index = 0; index < 16; ++index) {
        if (glGetError() == GL_NO_ERROR) return;
    }
}

static bool colour_capture_error(
        const char *where, char *error, size_t error_size) {
    const GLenum code = glGetError();
    if (code == GL_NO_ERROR) return true;
    colour_set_error(
            error,
            error_size,
            "%s glError=0x%04x",
            where,
            (unsigned int) code);
    colour_drain_errors();
    return false;
}

static GLuint colour_compile_shader(
        GLenum type,
        const char *source,
        const char *label,
        char *error,
        size_t error_size) {
    const GLuint shader = glCreateShader(type);
    if (shader == 0U) {
        colour_set_error(error, error_size, "%s glCreateShader returned 0", label);
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
    colour_set_error(error, error_size, "%s compile failed: %s", label, log);
    glDeleteShader(shader);
    return 0U;
}

static GLuint colour_create_program(
        const char *vertex_source,
        const char *fragment_source,
        const char *label,
        char *error,
        size_t error_size) {
    const GLuint vertex = colour_compile_shader(
            GL_VERTEX_SHADER, vertex_source, label, error, error_size);
    if (vertex == 0U) return 0U;
    const GLuint fragment = colour_compile_shader(
            GL_FRAGMENT_SHADER, fragment_source, label, error, error_size);
    if (fragment == 0U) {
        glDeleteShader(vertex);
        return 0U;
    }
    GLuint program = glCreateProgram();
    if (program == 0U) {
        colour_set_error(error, error_size, "%s glCreateProgram returned 0", label);
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
            colour_set_error(error, error_size, "%s link failed: %s", label, log);
            glDeleteProgram(program);
            program = 0U;
        }
    }
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    return program;
}

static void colour_set_fbo_texture_parameters(void) {
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
}

static void colour_set_input_texture_parameters(void) {
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_MIRRORED_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_MIRRORED_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
}

static void colour_forget_names(LleColourGles *gles) {
    if (gles == NULL) return;
    memset(gles->programs, 0, sizeof(gles->programs));
    memset(gles->buffers, 0, sizeof(gles->buffers));
    memset(gles->input_textures, 0, sizeof(gles->input_textures));
    memset(gles->fbo_textures, 0, sizeof(gles->fbo_textures));
    memset(gles->framebuffers, 0, sizeof(gles->framebuffers));
    memset(gles->input_width, 0, sizeof(gles->input_width));
    memset(gles->input_height, 0, sizeof(gles->input_height));
    memset(gles->fbo_width, 0, sizeof(gles->fbo_width));
    memset(gles->fbo_height, 0, sizeof(gles->fbo_height));
    memset(gles->has_input, 0, sizeof(gles->has_input));
    gles->direction_velocity_x = 0.0f;
    gles->direction_velocity_y = 0.0f;
    gles->surface_width = 0;
    gles->surface_height = 0;
    gles->ready = false;
}

static void colour_delete_names(LleColourGles *gles) {
    if (gles == NULL) return;
    for (int index = 0; index < LLE_COLOUR_PROGRAM_COUNT; ++index) {
        if (gles->programs[index] != 0U) glDeleteProgram(gles->programs[index]);
    }
    glDeleteBuffers(LLE_COLOUR_BUFFER_COUNT, gles->buffers);
    glDeleteTextures(LLE_COLOUR_INPUT_TEXTURE_COUNT, gles->input_textures);
    glDeleteTextures(LLE_COLOUR_FBO_COUNT, gles->fbo_textures);
    glDeleteFramebuffers(LLE_COLOUR_FBO_COUNT, gles->framebuffers);
    colour_forget_names(gles);
}

static bool colour_make_programs(
        LleColourGles *gles, char *error, size_t error_size) {
    static const char *const labels[LLE_COLOUR_PROGRAM_COUNT] = {
            "colour enhance",
            "colour copy",
            "colour density",
            "colour direction",
            "colour composite",
            "colour stencil"
    };
    const char *const vertices[LLE_COLOUR_PROGRAM_COUNT] = {
            LLE_COLOUR_QUAD_VERTEX_SHADER,
            LLE_COLOUR_QUAD_VERTEX_SHADER,
            LLE_COLOUR_DENSITY_VERTEX_SHADER,
            LLE_COLOUR_PARTICLE_VERTEX_SHADER,
            LLE_COLOUR_QUAD_VERTEX_SHADER,
            LLE_COLOUR_STENCIL_VERTEX_SHADER
    };
    const char *const fragments[LLE_COLOUR_PROGRAM_COUNT] = {
            LLE_COLOUR_ENHANCE_FRAGMENT_SHADER,
            LLE_COLOUR_COPY_FRAGMENT_SHADER,
            LLE_COLOUR_DENSITY_FRAGMENT_SHADER,
            LLE_COLOUR_COLOR_DIRECTION_FRAGMENT_SHADER,
            LLE_COLOUR_COMPOSITE_FRAGMENT_SHADER,
            LLE_COLOUR_STENCIL_FRAGMENT_SHADER
    };
    for (int index = 0; index < LLE_COLOUR_PROGRAM_COUNT; ++index) {
        gles->programs[index] = colour_create_program(
                vertices[index],
                fragments[index],
                labels[index],
                error,
                error_size);
        if (gles->programs[index] == 0U) return false;
    }
    return true;
}

static void colour_compute_target_sizes(
        int width,
        int height,
        int output_width[LLE_COLOUR_FBO_COUNT],
        int output_height[LLE_COLOUR_FBO_COUNT]) {
    if (height >= width) {
        output_width[LLE_COLOUR_FBO_DENSITY] = 135;
        output_height[LLE_COLOUR_FBO_DENSITY] =
                (int) (135.0f * (float) height / (float) width);
        output_width[LLE_COLOUR_FBO_COLOR_DIRECTION] = 270;
        output_height[LLE_COLOUR_FBO_COLOR_DIRECTION] =
                (int) (270.0f * (float) height / (float) width);
    } else {
        output_width[LLE_COLOUR_FBO_DENSITY] = 178;
        output_height[LLE_COLOUR_FBO_DENSITY] =
                (int) (178.5f * (float) height / (float) width);
        output_width[LLE_COLOUR_FBO_COLOR_DIRECTION] = 357;
        output_height[LLE_COLOUR_FBO_COLOR_DIRECTION] =
                (int) (357.0f * (float) height / (float) width);
    }
    const int short_side = width < height ? width : height;
    const float map_scale =
            (720.0f / (float) (short_side > 0 ? short_side : 1)) * 0.01f;
    output_width[LLE_COLOUR_FBO_COLOR_MAP] =
            (int) ((float) width * map_scale);
    output_height[LLE_COLOUR_FBO_COLOR_MAP] =
            (int) ((float) height * map_scale);
    for (int index = 0; index < LLE_COLOUR_FBO_COUNT; ++index) {
        if (output_width[index] < 2) output_width[index] = 2;
        if (output_height[index] < 2) output_height[index] = 2;
    }
}

static bool colour_allocate_target(
        LleColourGles *gles,
        int index,
        int width,
        int height,
        char *error,
        size_t error_size) {
    glBindTexture(GL_TEXTURE_2D, gles->fbo_textures[index]);
    colour_set_fbo_texture_parameters();
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
    glBindFramebuffer(GL_FRAMEBUFFER, gles->framebuffers[index]);
    glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            gles->fbo_textures[index],
            0);
    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        colour_set_error(
                error,
                error_size,
                "colour FBO %d incomplete status=0x%04x",
                index,
                (unsigned int) status);
        glBindFramebuffer(GL_FRAMEBUFFER, 0U);
        return false;
    }
    glViewport(0, 0, width, height);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    gles->fbo_width[index] = width;
    gles->fbo_height[index] = height;
    glBindFramebuffer(GL_FRAMEBUFFER, 0U);
    return true;
}

static void colour_bind_texture(GLuint texture, int unit, GLint uniform) {
    glActiveTexture((GLenum) (GL_TEXTURE0 + unit));
    glBindTexture(GL_TEXTURE_2D, texture);
    if (uniform >= 0) glUniform1i(uniform, unit);
}

static void colour_bind_quad(GLuint program, const LleColourGles *gles) {
    const GLint position = glGetAttribLocation(program, "aPosition");
    const GLint texture_uv = glGetAttribLocation(program, "aTexUV");
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_COLOUR_BUFFER_QUAD]);
    if (position >= 0) {
        glEnableVertexAttribArray((GLuint) position);
        glVertexAttribPointer(
                (GLuint) position,
                2,
                GL_FLOAT,
                GL_FALSE,
                (GLsizei) (4U * sizeof(float)),
                (const void *) 0);
    }
    if (texture_uv >= 0) {
        glEnableVertexAttribArray((GLuint) texture_uv);
        glVertexAttribPointer(
                (GLuint) texture_uv,
                2,
                GL_FLOAT,
                GL_FALSE,
                (GLsizei) (4U * sizeof(float)),
                (const void *) (uintptr_t) (2U * sizeof(float)));
    }
}

static void colour_unbind_quad(GLuint program) {
    const GLint position = glGetAttribLocation(program, "aPosition");
    const GLint texture_uv = glGetAttribLocation(program, "aTexUV");
    if (position >= 0) glDisableVertexAttribArray((GLuint) position);
    if (texture_uv >= 0) glDisableVertexAttribArray((GLuint) texture_uv);
}

static void colour_draw_quad(GLuint program, const LleColourGles *gles) {
    colour_bind_quad(program, gles);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    colour_unbind_quad(program);
}

static bool colour_ensure_particle_capacity(
        LleColourGles *gles,
        size_t count,
        char *error,
        size_t error_size) {
    if (count <= gles->particle_capacity
            && gles->particle_staging != NULL
            && gles->density_staging != NULL) {
        return true;
    }
    size_t capacity = gles->particle_capacity > 0U
            ? gles->particle_capacity
            : 256U;
    while (capacity < count) {
        if (capacity > SIZE_MAX / 2U) {
            colour_set_error(error, error_size, "colour particle capacity overflow");
            return false;
        }
        capacity *= 2U;
    }
    if (capacity > SIZE_MAX
            / (LLE_COLOUR_PARTICLE_STRIDE_FLOATS * sizeof(float))) {
        colour_set_error(error, error_size, "colour particle allocation overflow");
        return false;
    }
    if (capacity > SIZE_MAX
            / (LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE
                    * LLE_COLOUR_DENSITY_VERTEX_STRIDE_FLOATS
                    * sizeof(float))) {
        colour_set_error(error, error_size, "colour density allocation overflow");
        return false;
    }
    float *next = (float *) realloc(
            gles->particle_staging,
            capacity * LLE_COLOUR_PARTICLE_STRIDE_FLOATS * sizeof(float));
    if (next == NULL) {
        colour_set_error(
                error,
                error_size,
                "colour particle allocation failed count=%zu",
                count);
        return false;
    }
    gles->particle_staging = next;
    float *next_density = (float *) realloc(
            gles->density_staging,
            capacity
                    * LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE
                    * LLE_COLOUR_DENSITY_VERTEX_STRIDE_FLOATS
                    * sizeof(float));
    if (next_density == NULL) {
        colour_set_error(
                error,
                error_size,
                "colour density allocation failed count=%zu",
                count);
        return false;
    }
    gles->density_staging = next_density;
    gles->particle_capacity = capacity;
    return true;
}

static bool colour_upload_particles(
        LleColourGles *gles,
        const LleColourDrawParticle *particles,
        size_t count,
        char *error,
        size_t error_size) {
    if (count == 0U) return true;
    if (particles == NULL) {
        colour_set_error(error, error_size, "colour particles are null");
        return false;
    }
    if (!colour_ensure_particle_capacity(gles, count, error, error_size)) {
        return false;
    }
    for (size_t index = 0U; index < count; ++index) {
        const LleColourDrawParticle *source = &particles[index];
        float *target = gles->particle_staging
                + index * LLE_COLOUR_PARTICLE_STRIDE_FLOATS;
        target[0] = source->x;
        target[1] = source->y;
        target[2] = source->velocity_x;
        target[3] = source->velocity_y;
        target[4] = source->density_size_px;
        target[5] = source->colour_size_px;
        target[6] = source->alpha;
        target[7] = source->color_x;
        target[8] = source->color_y;
        /*
         * Internal centre-source selector: stock helper 0x59528 gives main
         * particles their group-average centre and satellites their own
         * rendered centre. This is deliberately not the dead stock aColor
         * shader attribute.
         */
        target[9] =
                (source->flags & LLE_COLOUR_PARTICLE_SATELLITE) == 0U
                        ? 1.0f
                        : 0.0f;

        /*
         * Exact density mesh topology from updateDrawVertexPoint @ 0x57820
         * and updateDrawVertexIndex @ 0x578fc:
         *   vertices center +/- size/2
         *   indices 0,1,2 / 2,3,1
         *   UV (1,0),(0,0),(1,1),(0,1)
         * Convert the stock bottom-up density-FBO geometry to clip space;
         * source particle Y is Android top-down.
         */
        static const float corner_x[] = {
                -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f
        };
        static const float corner_y[] = {
                -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f
        };
        static const float texture_u[] = {
                1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f
        };
        static const float texture_v[] = {
                0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f
        };
        const float center_x =
                source->x / (float) gles->surface_width * 2.0f - 1.0f;
        const float center_y =
                1.0f - source->y / (float) gles->surface_height * 2.0f;
        const float half_x = source->density_size_px
                / (float) gles->fbo_width[LLE_COLOUR_FBO_DENSITY];
        const float half_y = source->density_size_px
                / (float) gles->fbo_height[LLE_COLOUR_FBO_DENSITY];
        float *density_target = gles->density_staging
                + index
                        * LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE
                        * LLE_COLOUR_DENSITY_VERTEX_STRIDE_FLOATS;
        for (size_t vertex = 0U;
                vertex < LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE;
                ++vertex) {
            density_target[0] = center_x + corner_x[vertex] * half_x;
            density_target[1] = center_y + corner_y[vertex] * half_y;
            density_target[2] = texture_u[vertex];
            density_target[3] = texture_v[vertex];
            density_target += LLE_COLOUR_DENSITY_VERTEX_STRIDE_FLOATS;
        }
    }
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_COLOUR_BUFFER_PARTICLES]);
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) (count
                    * LLE_COLOUR_PARTICLE_STRIDE_FLOATS
                    * sizeof(float)),
            gles->particle_staging,
            GL_STREAM_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_COLOUR_BUFFER_DENSITY]);
    glBufferData(
            GL_ARRAY_BUFFER,
            (GLsizeiptr) (count
                    * LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE
                    * LLE_COLOUR_DENSITY_VERTEX_STRIDE_FLOATS
                    * sizeof(float)),
            gles->density_staging,
            GL_STREAM_DRAW);
    return colour_capture_error("colour particle upload", error, error_size);
}

static void colour_bind_particle_attributes(
        GLuint program,
        const LleColourGles *gles,
        int surface_width,
        int surface_height,
        float point_scale,
        size_t point_size_offset) {
    static const char *const names[] = {
            "aPosition",
            "aVelocity",
            "aPointSize",
            "aAlpha",
            "aColorPosition",
            "aFlags"
    };
    static const GLint sizes[] = {2, 2, 1, 1, 2, 1};
    static const size_t offsets[] = {0U, 2U, 4U, 6U, 7U, 9U};
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_COLOUR_BUFFER_PARTICLES]);
    for (size_t index = 0U; index < 6U; ++index) {
        const GLint location = glGetAttribLocation(program, names[index]);
        if (location < 0) continue;
        glEnableVertexAttribArray((GLuint) location);
        const size_t offset = index == 2U ? point_size_offset : offsets[index];
        glVertexAttribPointer(
                (GLuint) location,
                sizes[index],
                GL_FLOAT,
                GL_FALSE,
                (GLsizei) (LLE_COLOUR_PARTICLE_STRIDE_FLOATS * sizeof(float)),
                (const void *) (uintptr_t) (offset * sizeof(float)));
    }
    const GLint surface = glGetUniformLocation(program, "uSurfaceSize");
    const GLint scale = glGetUniformLocation(program, "uPointScale");
    if (surface >= 0) {
        glUniform2f(surface, (float) surface_width, (float) surface_height);
    }
    if (scale >= 0) glUniform1f(scale, point_scale);
}

static void colour_unbind_particle_attributes(GLuint program) {
    static const char *const names[] = {
            "aPosition",
            "aVelocity",
            "aPointSize",
            "aAlpha",
            "aColorPosition",
            "aFlags"
    };
    for (size_t index = 0U; index < 6U; ++index) {
        const GLint location = glGetAttribLocation(program, names[index]);
        if (location >= 0) glDisableVertexAttribArray((GLuint) location);
    }
}

static void colour_bind_density_attributes(
        GLuint program, const LleColourGles *gles) {
    const GLint position = glGetAttribLocation(program, "aPosition");
    const GLint texture_uv = glGetAttribLocation(program, "aTexUV");
    glBindBuffer(
            GL_ARRAY_BUFFER, gles->buffers[LLE_COLOUR_BUFFER_DENSITY]);
    if (position >= 0) {
        glEnableVertexAttribArray((GLuint) position);
        glVertexAttribPointer(
                (GLuint) position,
                2,
                GL_FLOAT,
                GL_FALSE,
                (GLsizei) (LLE_COLOUR_DENSITY_VERTEX_STRIDE_FLOATS
                        * sizeof(float)),
                (const void *) 0);
    }
    if (texture_uv >= 0) {
        glEnableVertexAttribArray((GLuint) texture_uv);
        glVertexAttribPointer(
                (GLuint) texture_uv,
                2,
                GL_FLOAT,
                GL_FALSE,
                (GLsizei) (LLE_COLOUR_DENSITY_VERTEX_STRIDE_FLOATS
                        * sizeof(float)),
                (const void *) (uintptr_t) (2U * sizeof(float)));
    }
}

static void colour_unbind_density_attributes(GLuint program) {
    const GLint position = glGetAttribLocation(program, "aPosition");
    const GLint texture_uv = glGetAttribLocation(program, "aTexUV");
    if (position >= 0) glDisableVertexAttribArray((GLuint) position);
    if (texture_uv >= 0) {
        glDisableVertexAttribArray((GLuint) texture_uv);
    }
}

void lle_colour_gles_default_params(LleColourDrawParams *params) {
    if (params == NULL) return;
    params->edge_ratio = 1.0f;
    params->restore_ratio = 1.0f;
    params->direction_velocity_x = 0.0f;
    params->direction_velocity_y = 0.0f;
    params->refraction_ratio = 1.0f;
    params->tab_scale = 0.9675f;
    params->tab_offset_x = 0.0f;
    params->tab_offset_y = 0.0f;
    params->edge_offset_ratio = 1.0f;
    params->inner_shadow_width = 0.6f;
    params->color_saturation = 1.3f;
    params->color_brightness = 1.3f;
    params->color_min_value = 0.15f;
    params->shadow_enabled = true;
}

void lle_colour_gles_reset_direction_velocity(LleColourGles *gles) {
    if (gles == NULL) return;
    gles->direction_velocity_x = 0.0f;
    gles->direction_velocity_y = 0.0f;
}

bool lle_colour_gles_init(
        LleColourGles *gles,
        int width,
        int height,
        char *error,
        size_t error_size) {
    colour_clear_error(error, error_size);
    if (gles == NULL || width <= 0 || height <= 0) {
        colour_set_error(error, error_size, "invalid colour GLES init arguments");
        return false;
    }
    float *staging = gles->particle_staging;
    float *density_staging = gles->density_staging;
    const size_t capacity = gles->particle_capacity;
    memset(gles, 0, sizeof(*gles));
    gles->particle_staging = staging;
    gles->density_staging = density_staging;
    gles->particle_capacity = capacity;
    colour_drain_errors();
    if (!colour_make_programs(gles, error, error_size)) {
        colour_delete_names(gles);
        return false;
    }
    glGenBuffers(LLE_COLOUR_BUFFER_COUNT, gles->buffers);
    glGenTextures(LLE_COLOUR_INPUT_TEXTURE_COUNT, gles->input_textures);
    glGenTextures(LLE_COLOUR_FBO_COUNT, gles->fbo_textures);
    glGenFramebuffers(LLE_COLOUR_FBO_COUNT, gles->framebuffers);
    const float quad[] = {
            -1.0f, -1.0f, 0.0f, 1.0f,
             1.0f, -1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 0.0f
    };
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_COLOUR_BUFFER_QUAD]);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quad), quad, GL_STATIC_DRAW);
    float stencil_circle[LLE_COLOUR_STENCIL_VERTICES * 2];
    stencil_circle[0] = 0.0f;
    stencil_circle[1] = 0.0f;
    for (int index = 0; index <= LLE_COLOUR_STENCIL_SEGMENTS; ++index) {
        const float angle = 6.28318530717958647692f
                * (float) index / (float) LLE_COLOUR_STENCIL_SEGMENTS;
        stencil_circle[(index + 1) * 2] =
                cosf(angle) * LLE_COLOUR_STENCIL_RADIUS;
        stencil_circle[(index + 1) * 2 + 1] =
                sinf(angle) * LLE_COLOUR_STENCIL_RADIUS;
    }
    glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_COLOUR_BUFFER_STENCIL]);
    glBufferData(
            GL_ARRAY_BUFFER,
            sizeof(stencil_circle),
            stencil_circle,
            GL_STATIC_DRAW);
    for (int index = 0; index < LLE_COLOUR_INPUT_TEXTURE_COUNT; ++index) {
        glBindTexture(GL_TEXTURE_2D, gles->input_textures[index]);
        colour_set_input_texture_parameters();
    }
    if (!colour_capture_error("colour GLES init", error, error_size)
            || !lle_colour_gles_resize(gles, width, height, error, error_size)) {
        colour_delete_names(gles);
        return false;
    }
    gles->ready = true;
    return true;
}

bool lle_colour_gles_resize(
        LleColourGles *gles,
        int width,
        int height,
        char *error,
        size_t error_size) {
    colour_clear_error(error, error_size);
    if (gles == NULL || width <= 0 || height <= 0) {
        colour_set_error(error, error_size, "invalid colour GLES surface size");
        return false;
    }
    int target_width[LLE_COLOUR_FBO_COUNT];
    int target_height[LLE_COLOUR_FBO_COUNT];
    colour_compute_target_sizes(width, height, target_width, target_height);
    bool changed = width != gles->surface_width
            || height != gles->surface_height;
    for (int index = 0; index < LLE_COLOUR_FBO_COUNT; ++index) {
        if (gles->fbo_width[index] != target_width[index]
                || gles->fbo_height[index] != target_height[index]) {
            changed = true;
        }
    }
    if (!changed) return true;
    for (int index = 0; index < LLE_COLOUR_FBO_COUNT; ++index) {
        if (!colour_allocate_target(
                    gles,
                    index,
                    target_width[index],
                    target_height[index],
                    error,
                    error_size)) {
            return false;
        }
    }
    gles->surface_width = width;
    gles->surface_height = height;
    glViewport(0, 0, width, height);
    return colour_capture_error("colour resize", error, error_size);
}

void lle_colour_gles_destroy(LleColourGles *gles) {
    if (gles == NULL) return;
    colour_delete_names(gles);
    free(gles->particle_staging);
    free(gles->density_staging);
    gles->particle_staging = NULL;
    gles->density_staging = NULL;
    gles->particle_capacity = 0U;
}

void lle_colour_gles_abandon(LleColourGles *gles) {
    if (gles == NULL) return;
    colour_forget_names(gles);
}

bool lle_colour_gles_upload_bitmap(
        LleColourGles *gles,
        JNIEnv *env,
        int slot,
        jobject bitmap,
        char *error,
        size_t error_size) {
    colour_clear_error(error, error_size);
    if (gles == NULL
            || env == NULL
            || bitmap == NULL
            || slot < 0
            || slot >= LLE_COLOUR_INPUT_TEXTURE_COUNT) {
        colour_set_error(error, error_size, "invalid colour bitmap upload");
        return false;
    }
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    const int info_result = AndroidBitmap_getInfo(env, bitmap, &info);
    if (info_result != ANDROID_BITMAP_RESULT_SUCCESS
            || info.width == 0U
            || info.height == 0U
            || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        colour_set_error(
                error,
                error_size,
                "unsupported colour bitmap slot=%d result=%d format=%u size=%ux%u",
                slot,
                info_result,
                info.format,
                info.width,
                info.height);
        return false;
    }
    void *pixels = NULL;
    const int lock_result = AndroidBitmap_lockPixels(env, bitmap, &pixels);
    if (lock_result != ANDROID_BITMAP_RESULT_SUCCESS || pixels == NULL) {
        colour_set_error(
                error,
                error_size,
                "colour bitmap lock failed slot=%d result=%d",
                slot,
                lock_result);
        return false;
    }
    colour_drain_errors();
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glBindTexture(GL_TEXTURE_2D, gles->input_textures[slot]);
    colour_set_input_texture_parameters();
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            (GLsizei) info.width,
            (GLsizei) info.height,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            pixels);
    const bool uploaded = colour_capture_error(
            "colour bitmap upload", error, error_size);
    (void) AndroidBitmap_unlockPixels(env, bitmap);
    if (!uploaded) return false;
    gles->input_width[slot] = (int) info.width;
    gles->input_height[slot] = (int) info.height;
    gles->has_input[slot] = true;
    return true;
}

void lle_colour_gles_clear_bitmap(LleColourGles *gles, int slot) {
    if (gles == NULL
            || slot < 0
            || slot >= LLE_COLOUR_INPUT_TEXTURE_COUNT) {
        return;
    }
    gles->has_input[slot] = false;
    gles->input_width[slot] = 0;
    gles->input_height[slot] = 0;
}

static void colour_draw_enhanced_map(
        LleColourGles *gles, const LleColourDrawParams *params) {
    const int index = LLE_COLOUR_FBO_COLOR_MAP;
    glBindFramebuffer(GL_FRAMEBUFFER, gles->framebuffers[index]);
    glViewport(0, 0, gles->fbo_width[index], gles->fbo_height[index]);
    glDisable(GL_BLEND);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    const GLuint program = gles->programs[LLE_COLOUR_PROGRAM_ENHANCE];
    glUseProgram(program);
    colour_bind_texture(
            gles->input_textures[LLE_COLOUR_TEXTURE_BACKGROUND],
            0,
            glGetUniformLocation(program, "uBackground"));
    glUniform1f(
            glGetUniformLocation(program, "uSaturation"),
            params->color_saturation);
    glUniform1f(
            glGetUniformLocation(program, "uBrightness"),
            params->color_brightness);
    glUniform1f(
            glGetUniformLocation(program, "uMinValue"),
            params->color_min_value);
    colour_draw_quad(program, gles);
}

static void colour_draw_density_field(
        LleColourGles *gles, size_t particle_count) {
    const int index = LLE_COLOUR_FBO_DENSITY;
    glBindFramebuffer(GL_FRAMEBUFFER, gles->framebuffers[index]);
    glViewport(0, 0, gles->fbo_width[index], gles->fbo_height[index]);
    glDisable(GL_BLEND);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (gles->has_input[LLE_COLOUR_TEXTURE_EDGE_DENSITY]) {
        const GLuint copy = gles->programs[LLE_COLOUR_PROGRAM_COPY];
        glUseProgram(copy);
        colour_bind_texture(
                gles->input_textures[LLE_COLOUR_TEXTURE_EDGE_DENSITY],
                0,
                glGetUniformLocation(copy, "uTexture"));
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_SRC_ALPHA, GL_ZERO);
        colour_draw_quad(copy, gles);
        glDisable(GL_BLEND);
    }
    if (particle_count == 0U) return;
    const GLuint program = gles->programs[LLE_COLOUR_PROGRAM_DENSITY];
    glUseProgram(program);
    colour_bind_density_attributes(program, gles);
    colour_bind_texture(
            gles->input_textures[LLE_COLOUR_TEXTURE_NORMAL],
            0,
            glGetUniformLocation(program, "uNormal"));
    glEnable(GL_BLEND);
    glBlendEquation(GL_FUNC_ADD);
    glBlendFuncSeparate(
            GL_ONE,
            GL_ONE_MINUS_SRC_ALPHA,
            GL_SRC_ALPHA,
            GL_ONE);
    /*
     * updateDensityField_GPU stages the independent satellite vector twice
     * before the ordinary live/released groups. Preserve that order because
     * the stock separate alpha/RGB blend is not commutative.
     */
    for (int satellite_pass = 0; satellite_pass < 2; ++satellite_pass) {
        for (size_t index = 0U; index < particle_count; ++index) {
            const float *particle = gles->particle_staging
                    + index * LLE_COLOUR_PARTICLE_STRIDE_FLOATS;
            if (particle[9] >= 0.5f) continue;
            glDrawArrays(
                    GL_TRIANGLES,
                    (GLint) (index
                            * LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE),
                    LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE);
        }
    }
    for (size_t index = 0U; index < particle_count; ++index) {
        const float *particle = gles->particle_staging
                + index * LLE_COLOUR_PARTICLE_STRIDE_FLOATS;
        if (particle[9] < 0.5f) continue;
        glDrawArrays(
                GL_TRIANGLES,
                (GLint) (index * LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE),
                LLE_COLOUR_DENSITY_VERTICES_PER_PARTICLE);
    }
    glDisable(GL_BLEND);
    colour_unbind_density_attributes(program);
}

static void colour_draw_color_direction_field(
        LleColourGles *gles,
        size_t particle_count,
        const LleColourDrawParams *params) {
    const float target_x = params->direction_velocity_x;
    const float target_y = params->direction_velocity_y;
    float candidate_x = target_x
            + 0.99f * (gles->direction_velocity_x - target_x);
    float candidate_y = target_y
            + 0.99f * (gles->direction_velocity_y - target_y);
    if (sqrtf(candidate_x * candidate_x + candidate_y * candidate_y) > 1.3f
            || (target_x == 0.0f && target_y == 0.0f)) {
        candidate_x = gles->direction_velocity_x * 0.99f;
        candidate_y = gles->direction_velocity_y * 0.99f;
    }
    gles->direction_velocity_x = candidate_x;
    gles->direction_velocity_y = candidate_y;

    const int index = LLE_COLOUR_FBO_COLOR_DIRECTION;
    glBindFramebuffer(GL_FRAMEBUFFER, gles->framebuffers[index]);
    glViewport(0, 0, gles->fbo_width[index], gles->fbo_height[index]);
    glDisable(GL_BLEND);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (particle_count == 0U) return;
    const GLuint program =
            gles->programs[LLE_COLOUR_PROGRAM_COLOR_DIRECTION];
    glUseProgram(program);
    colour_bind_particle_attributes(
            program,
            gles,
            gles->surface_width,
            gles->surface_height,
            1.0f,
            5U);
    colour_bind_texture(
            gles->input_textures[LLE_COLOUR_TEXTURE_NORMAL],
            0,
            glGetUniformLocation(program, "uNormal"));
    colour_bind_texture(
            gles->fbo_textures[LLE_COLOUR_FBO_COLOR_MAP],
            1,
            glGetUniformLocation(program, "uColorMap"));
    colour_bind_texture(
            gles->fbo_textures[LLE_COLOUR_FBO_DENSITY],
            2,
            glGetUniformLocation(program, "uDensity"));
    glUniform2f(
            glGetUniformLocation(program, "uTargetSize"),
            (float) gles->fbo_width[index],
            (float) gles->fbo_height[index]);
    glUniform2f(
            glGetUniformLocation(program, "uVelocity"),
            gles->direction_velocity_x,
            gles->direction_velocity_y);

    glUniform1f(glGetUniformLocation(program, "uMode"), 0.0f);
    glEnable(GL_BLEND);
    glBlendEquation(GL_FUNC_ADD);
    glBlendFuncSeparate(
            GL_SRC_ALPHA,
            GL_ONE_MINUS_SRC_ALPHA,
            GL_ONE_MINUS_SRC_ALPHA,
            GL_ONE);
    glDrawArrays(GL_POINTS, 0, (GLsizei) particle_count);

    glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_TRUE);
    glDisable(GL_BLEND);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glUniform1f(glGetUniformLocation(program, "uMode"), 1.0f);
    glEnable(GL_BLEND);
    glBlendFuncSeparate(
            GL_ZERO,
            GL_ONE,
            GL_SRC_ALPHA,
            GL_ONE_MINUS_SRC_ALPHA);
    glDrawArrays(GL_POINTS, 0, (GLsizei) particle_count);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glDisable(GL_BLEND);
    colour_unbind_particle_attributes(program);
}

static void colour_draw_stencil_mask(
        LleColourGles *gles, size_t particle_count) {
    if (particle_count == 0U || gles->particle_staging == NULL) return;
    const GLuint program = gles->programs[LLE_COLOUR_PROGRAM_STENCIL];
    const GLint position = glGetAttribLocation(program, "aPosition");
    const GLint position_scale =
            glGetAttribLocation(program, "aPositionScale");
    const float world_width =
            gles->surface_height >= gles->surface_width ? 0.45f : 0.6f;
    const float world_height = world_width
            * (float) gles->surface_height / (float) gles->surface_width;
    const float mvp[] = {
            2.0f / world_width, 0.0f, 0.0f, 0.0f,
            0.0f, 2.0f / world_height, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f, 1.0f
    };

    glUseProgram(program);
    glUniformMatrix4fv(
            glGetUniformLocation(program, "uMVPMatrix"),
            1,
            GL_FALSE,
            mvp);
    glBindBuffer(
            GL_ARRAY_BUFFER,
            gles->buffers[LLE_COLOUR_BUFFER_STENCIL]);
    if (position >= 0) {
        glEnableVertexAttribArray((GLuint) position);
        glVertexAttribPointer(
                (GLuint) position,
                2,
                GL_FLOAT,
                GL_FALSE,
                (GLsizei) (2U * sizeof(float)),
                (const void *) 0);
    }
    if (position_scale >= 0) {
        glDisableVertexAttribArray((GLuint) position_scale);
    }

    for (int phase = 0; phase < 3; ++phase) {
        for (size_t index = 0U; index < particle_count; ++index) {
            const float *particle = gles->particle_staging
                    + index * LLE_COLOUR_PARTICLE_STRIDE_FLOATS;
            const bool satellite = particle[9] < 0.5f;
            if ((phase < 2 && !satellite) || (phase == 2 && satellite)) {
                continue;
            }
            const float world_x =
                    particle[0] / (float) gles->surface_width * world_width;
            const float world_y =
                    (1.0f - particle[1] / (float) gles->surface_height)
                    * world_height;
            const float scale = particle[4]
                    * LLE_COLOUR_STENCIL_SCALE_FROM_DENSITY;
            if (position_scale >= 0) {
                glVertexAttrib3f(
                        (GLuint) position_scale,
                        world_x,
                        world_y,
                        scale);
            }
            glDrawArrays(
                    GL_TRIANGLE_FAN,
                    0,
                    LLE_COLOUR_STENCIL_VERTICES);
        }
    }
    if (position >= 0) glDisableVertexAttribArray((GLuint) position);
}

static void colour_draw_composite(
        LleColourGles *gles,
        size_t particle_count,
        const LleColourDrawParams *params) {
    glBindFramebuffer(GL_FRAMEBUFFER, 0U);
    glViewport(0, 0, gles->surface_width, gles->surface_height);
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_STENCIL_TEST);
    glDisable(GL_BLEND);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glStencilMask(0xffU);
    glClearStencil(1);
    glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    glStencilMask(1U);
    glStencilFunc(GL_NEVER, 1, 1U);
    glStencilOp(GL_ZERO, GL_KEEP, GL_KEEP);
    colour_draw_stencil_mask(gles, particle_count);
    glStencilMask(0U);
    glStencilFunc(GL_NOTEQUAL, 1, 1U);
    glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    const GLuint program = gles->programs[LLE_COLOUR_PROGRAM_COMPOSITE];
    glUseProgram(program);
    colour_bind_texture(
            gles->input_textures[LLE_COLOUR_TEXTURE_BACKGROUND],
            0,
            glGetUniformLocation(program, "uBackground"));
    colour_bind_texture(
            gles->fbo_textures[LLE_COLOUR_FBO_DENSITY],
            1,
            glGetUniformLocation(program, "uDensity"));
    colour_bind_texture(
            gles->fbo_textures[LLE_COLOUR_FBO_COLOR_DIRECTION],
            2,
            glGetUniformLocation(program, "uColorDirection"));
    glUniform2f(
            glGetUniformLocation(program, "uInvSurfaceSize"),
            1.0f / (float) gles->surface_width,
            1.0f / (float) gles->surface_height);
    glUniform1f(
            glGetUniformLocation(program, "uEdgeRatio"),
            params->edge_ratio);
    glUniform1f(
            glGetUniformLocation(program, "uRestore"),
            params->restore_ratio);
    glUniform1f(
            glGetUniformLocation(program, "uRefractionRatio"),
            params->refraction_ratio
                    * params->refraction_ratio
                    * params->refraction_ratio);
    glUniform1f(
            glGetUniformLocation(program, "uTabScale"),
            params->tab_scale);
    glUniform2f(
            glGetUniformLocation(program, "uTabOffset"),
            params->tab_offset_x,
            params->tab_offset_y);
    glUniform1f(
            glGetUniformLocation(program, "uEdgeOffsetRatio"),
            params->edge_offset_ratio);
    glUniform1f(
            glGetUniformLocation(program, "uInnerShadowWidth"),
            params->inner_shadow_width);
    glUniform1f(
            glGetUniformLocation(program, "uShadowEnable"),
            params->shadow_enabled ? 1.0f : 0.0f);
    colour_draw_quad(program, gles);
    glDisable(GL_STENCIL_TEST);
    glStencilMask(0xffU);
}

bool lle_colour_gles_draw(
        LleColourGles *gles,
        const LleColourDrawParticle *particles,
        size_t particle_count,
        const LleColourDrawParams *params,
        int width,
        int height,
        char *error,
        size_t error_size) {
    colour_clear_error(error, error_size);
    if (gles == NULL || !gles->ready || width <= 0 || height <= 0) {
        colour_set_error(error, error_size, "colour GLES renderer is not ready");
        return false;
    }
    if (!gles->has_input[LLE_COLOUR_TEXTURE_BACKGROUND]
            || !gles->has_input[LLE_COLOUR_TEXTURE_NORMAL]) {
        colour_set_error(error, error_size, "colour background/normal texture missing");
        return false;
    }
    GLint stencil_bits = 0;
    glGetIntegerv(GL_STENCIL_BITS, &stencil_bits);
    if (stencil_bits < 8) {
        colour_set_error(
                error,
                error_size,
                "colour default framebuffer needs stencil8, has %d bits",
                stencil_bits);
        return false;
    }
    if (!lle_colour_gles_resize(gles, width, height, error, error_size)
            || !colour_upload_particles(
                    gles,
                    particles,
                    particle_count,
                    error,
                    error_size)) {
        return false;
    }
    LleColourDrawParams defaults;
    if (params == NULL) {
        lle_colour_gles_default_params(&defaults);
        defaults.restore_ratio = (float) height * 0.00078125f;
        params = &defaults;
    }
    colour_drain_errors();
    colour_draw_enhanced_map(gles, params);
    colour_draw_density_field(gles, particle_count);
    colour_draw_color_direction_field(gles, particle_count, params);
    colour_draw_composite(gles, particle_count, params);
    glBindBuffer(GL_ARRAY_BUFFER, 0U);
    glBindTexture(GL_TEXTURE_2D, 0U);
    glUseProgram(0U);
    return colour_capture_error("colour frame", error, error_size);
}
