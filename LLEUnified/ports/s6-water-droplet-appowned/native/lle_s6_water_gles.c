#include "lle_s6_water_gles.h"

#include <android/bitmap.h>
#include <limits.h>
#include <math.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum {
  LLE_S6_WATER_PROGRAM_DENSITY = 0,
  LLE_S6_WATER_PROGRAM_COMPOSITE = 1
};

enum {
  LLE_S6_WATER_BUFFER_QUAD = 0,
  LLE_S6_WATER_BUFFER_PARTICLES = 1
};

enum {
  LLE_S6_WATER_QUAD_VERTEX_FLOATS = 8,
  LLE_S6_WATER_QUAD_VERTEX_COUNT = 4,
  LLE_S6_WATER_PARTICLE_VERTEX_FLOATS = 4,
  LLE_S6_WATER_PARTICLE_VERTICES = 6
};

/*
 * Stock phone density targets are constructed from a floating base width and
 * then passed through the GLES integer viewport/texture APIs. Preserve that
 * truncation, including 178.5 -> 178 in landscape.
 */
static const float LLE_S6_WATER_DENSITY_PORTRAIT_BASE = 135.0f;
static const float LLE_S6_WATER_DENSITY_LANDSCAPE_BASE = 178.5f;

static const char *LLE_S6_WATER_DENSITY_VERTEX_SHADER =
    "precision mediump float;\n"
    "attribute vec2 aPosition;\n"
    "attribute vec2 aTexUV;\n"
    "varying mediump vec2 vTexUV;\n"
    "void main() {\n"
    "  vTexUV = aTexUV;\n"
    "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
    "}\n";

static const char *LLE_S6_WATER_DENSITY_FRAGMENT_SHADER =
    "precision mediump float;\n"
    "uniform sampler2D uTexMap;\n"
    "varying mediump vec2 vTexUV;\n"
    "void main() {\n"
    "  gl_FragColor = texture2D(uTexMap, vTexUV);\n"
    "}\n";

/*
 * The app-owned vertex stream uses clip-space positions directly. Its three
 * UV sets reproduce SPDrawWaterDroplet's Android-background, cropped
 * background and density-FBO coordinate systems.
 */
static const char *LLE_S6_WATER_COMPOSITE_VERTEX_SHADER =
    "precision mediump float;\n"
    "attribute vec2 aPosition;\n"
    "attribute highp vec2 aTexUV;\n"
    "attribute highp vec2 aTabScaledUV;\n"
    "attribute highp vec2 aDensityUV;\n"
    "varying highp vec2 vTexUV;\n"
    "varying highp vec2 vTabScaledUV;\n"
    "varying highp vec2 vDensityUV;\n"
    "void main() {\n"
    "  vTexUV = aTexUV;\n"
    "  vTabScaledUV = aTabScaledUV;\n"
    "  vDensityUV = aDensityUV;\n"
    "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
    "}\n";

/*
 * SPDrawWaterDroplet::createWaterShader, with only the validated LLE
 * transparent-overlay adaptation:
 *
 *   - stock interior remains opaque and refracts the cached lock background;
 *   - ordinary exterior becomes transparent;
 *   - mix(bg, bg * .8, amount) becomes premultiplied black at .2 * amount.
 *
 * Constants are supplied as uniforms so the immutable simulation snapshot is
 * the single owner of recovered render state. The stock defaults are written
 * by the simulation (threshold .5, edge .075, shadow .15/range 12, eta
 * .75001875 and refraction amplitude .075).
 */
static const char *LLE_S6_WATER_COMPOSITE_FRAGMENT_SHADER =
    "precision mediump float;\n"
    "uniform sampler2D uBG;\n"
    "uniform sampler2D uDensity;\n"
    "uniform highp vec2 uInvRes;\n"
    "uniform highp vec2 uInvDensityRes;\n"
    "uniform highp float uEdgeRatio;\n"
    "uniform highp float uRestore;\n"
    "uniform highp float uRefractionRatio;\n"
    "uniform highp float uEdgeOffsetRatio;\n"
    "uniform highp float uSpecularRatio;\n"
    "uniform highp float uThreshold;\n"
    "uniform highp float uEdgeOffset;\n"
    "uniform highp float uShadowOffset;\n"
    "uniform highp float uShadowRange;\n"
    "uniform highp float uRefractionEta;\n"
    "uniform highp float uRefractionAmplitude;\n"
    "varying highp vec2 vTexUV;\n"
    "varying highp vec2 vTabScaledUV;\n"
    "varying highp vec2 vDensityUV;\n"
    "void main() {\n"
    "  highp float hoi = texture2D(uDensity, vDensityUV).a;\n"
    "  highp float edgeOffset = uEdgeOffset * uEdgeOffsetRatio;\n"
    "  highp float shadowOffset = uShadowOffset * uEdgeOffsetRatio;\n"
    "  highp float edgeRatio = (1.0 - uEdgeRatio) * edgeOffset;\n"
    "  highp float edgeRange = uThreshold + edgeOffset - edgeRatio;\n"
    "  highp float edgeDisplacement = max(\n"
    "      edgeRange - uThreshold, 0.000001);\n"
    "  highp float invEdgeDisplacement = 1.0 / edgeDisplacement;\n"
    "  if (hoi < uThreshold) {\n"
    "    highp float upside = texture2D(\n"
    "        uDensity,\n"
    "        vec2(\n"
    "            vDensityUV.x + uInvRes.x * uShadowRange *\n"
    "                uEdgeOffsetRatio * uRestore * 0.5,\n"
    "            vDensityUV.y - uInvRes.y * uShadowRange *\n"
    "                uEdgeOffsetRatio * uRestore)).a;\n"
    "    if (upside > uThreshold) {\n"
    "      highp float shadowDenominator = max(\n"
    "          uShadowOffset * uEdgeOffsetRatio -\n"
    "              ((1.0 - uEdgeRatio) * shadowOffset),\n"
    "          0.000001);\n"
    "      highp float shadowAmount = min(\n"
    "          (upside - uThreshold) / shadowDenominator,\n"
    "          0.95) * 0.75;\n"
    "      gl_FragColor = vec4(0.0, 0.0, 0.0, 0.2 * shadowAmount);\n"
    "    } else {\n"
    "      gl_FragColor = vec4(0.0);\n"
    "    }\n"
    "    return;\n"
    "  }\n"
    "  highp vec3 normal = texture2D(uDensity, vDensityUV).rgb * 2.0 - 1.0;\n"
    "  highp vec3 refracted = refract(\n"
    "      vec3(0.0, 0.0, -1.0), normal, uRefractionEta) *\n"
    "      uRefractionAmplitude * uRefractionRatio;\n"
    "  refracted.y = -refracted.y;\n"
    "  highp vec3 background = texture2D(\n"
    "      uBG, vTabScaledUV + refracted.xy).rgb;\n"
    "  if (hoi < edgeRange) {\n"
    "    highp float ndothv = dot(\n"
    "        normal, vec3(-0.705345616, 0.705345616, 0.070534562));\n"
    "    highp float specular = pow(ndothv, 2.0) *\n"
    "        0.75 * uSpecularRatio;\n"
    "    highp float smooth = smoothstep(\n"
    "        0.0, 1.0,\n"
    "        1.0 - (hoi - uThreshold) * invEdgeDisplacement);\n"
    "    gl_FragColor = vec4(background + specular * smooth, 1.0);\n"
    "  } else {\n"
    "    gl_FragColor = vec4(background, 1.0);\n"
    "  }\n"
    "}\n";

static void s6_water_set_error(char *error, size_t error_size,
                               const char *format, ...) {
  if (error == NULL || error_size == 0U) {
    return;
  }
  va_list arguments;
  va_start(arguments, format);
  (void)vsnprintf(error, error_size, format, arguments);
  va_end(arguments);
}

static void s6_water_clear_error(char *error, size_t error_size) {
  if (error != NULL && error_size > 0U) {
    error[0] = '\0';
  }
}

static void s6_water_drain_errors(void) {
  for (int index = 0; index < 32; ++index) {
    if (glGetError() == GL_NO_ERROR) {
      return;
    }
  }
}

static bool s6_water_capture_error(const char *where, char *error,
                                   size_t error_size) {
  const GLenum code = glGetError();
  if (code == GL_NO_ERROR) {
    return true;
  }
  s6_water_set_error(error, error_size, "%s glError=0x%04x", where,
                     (unsigned int)code);
  s6_water_drain_errors();
  return false;
}

static GLuint s6_water_compile_shader(GLenum type, const char *source,
                                      const char *label, char *error,
                                      size_t error_size) {
  const GLuint shader = glCreateShader(type);
  if (shader == 0U) {
    s6_water_set_error(error, error_size, "%s glCreateShader returned 0",
                       label);
    return 0U;
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
  glGetShaderInfoLog(shader, (GLsizei)sizeof(log), &length, log);
  (void)length;
  s6_water_set_error(error, error_size, "%s compile failed: %s", label, log);
  glDeleteShader(shader);
  return 0U;
}

static GLuint s6_water_create_program(const char *vertex_source,
                                      const char *fragment_source,
                                      const char *label, char *error,
                                      size_t error_size) {
  const GLuint vertex = s6_water_compile_shader(
      GL_VERTEX_SHADER, vertex_source, label, error, error_size);
  if (vertex == 0U) {
    return 0U;
  }
  const GLuint fragment = s6_water_compile_shader(
      GL_FRAGMENT_SHADER, fragment_source, label, error, error_size);
  if (fragment == 0U) {
    glDeleteShader(vertex);
    return 0U;
  }

  GLuint program = glCreateProgram();
  if (program == 0U) {
    s6_water_set_error(error, error_size, "%s glCreateProgram returned 0",
                       label);
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
      glGetProgramInfoLog(program, (GLsizei)sizeof(log), &length, log);
      (void)length;
      s6_water_set_error(error, error_size, "%s link failed: %s", label, log);
      glDeleteProgram(program);
      program = 0U;
    }
  }
  glDeleteShader(vertex);
  glDeleteShader(fragment);
  return program;
}

static void s6_water_set_input_texture_parameters(void) {
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_MIRRORED_REPEAT);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_MIRRORED_REPEAT);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
}

static void s6_water_set_fbo_texture_parameters(void) {
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
}

static void s6_water_forget_names(LleS6WaterGles *gles) {
  if (gles == NULL) {
    return;
  }
  memset(gles->programs, 0, sizeof(gles->programs));
  memset(gles->buffers, 0, sizeof(gles->buffers));
  memset(gles->input_textures, 0, sizeof(gles->input_textures));
  memset(gles->input_width, 0, sizeof(gles->input_width));
  memset(gles->input_height, 0, sizeof(gles->input_height));
  memset(gles->has_input, 0, sizeof(gles->has_input));
  gles->density_texture = 0U;
  gles->density_framebuffer = 0U;
  gles->density_width = 0;
  gles->density_height = 0;
  gles->surface_width = 0;
  gles->surface_height = 0;
  gles->ready = false;
}

static void s6_water_delete_names(LleS6WaterGles *gles) {
  if (gles == NULL) {
    return;
  }
  for (int index = 0; index < LLE_S6_WATER_GLES_PROGRAM_COUNT; ++index) {
    if (gles->programs[index] != 0U) {
      glDeleteProgram(gles->programs[index]);
    }
  }
  bool has_buffer = false;
  for (int index = 0; index < LLE_S6_WATER_GLES_BUFFER_COUNT; ++index) {
    has_buffer = has_buffer || gles->buffers[index] != 0U;
  }
  if (has_buffer) {
    glDeleteBuffers(LLE_S6_WATER_GLES_BUFFER_COUNT, gles->buffers);
  }
  bool has_input_texture = false;
  for (int index = 0; index < LLE_S6_WATER_INPUT_TEXTURE_COUNT; ++index) {
    has_input_texture =
        has_input_texture || gles->input_textures[index] != 0U;
  }
  if (has_input_texture) {
    glDeleteTextures(LLE_S6_WATER_INPUT_TEXTURE_COUNT,
                     gles->input_textures);
  }
  if (gles->density_texture != 0U) {
    glDeleteTextures(1, &gles->density_texture);
  }
  if (gles->density_framebuffer != 0U) {
    glDeleteFramebuffers(1, &gles->density_framebuffer);
  }
  s6_water_forget_names(gles);
}

static void s6_water_compute_density_size(int width, int height,
                                          int *density_width,
                                          int *density_height) {
  const bool portrait = height >= width;
  const float base = portrait ? LLE_S6_WATER_DENSITY_PORTRAIT_BASE
                              : LLE_S6_WATER_DENSITY_LANDSCAPE_BASE;
  int target_width = (int)base;
  int target_height =
      (int)(base * ((float)height / (float)width));
  if (target_width < 1) {
    target_width = 1;
  }
  if (target_height < 1) {
    target_height = 1;
  }
  *density_width = target_width;
  *density_height = target_height;
}

static bool s6_water_allocate_density_target(LleS6WaterGles *gles, int width,
                                             int height, char *error,
                                             size_t error_size) {
  GLint max_texture_size = 0;
  glGetIntegerv(GL_MAX_TEXTURE_SIZE, &max_texture_size);
  if (width <= 0 || height <= 0 || width > max_texture_size ||
      height > max_texture_size) {
    s6_water_set_error(error, error_size,
                       "invalid S6 density target %dx%d (max %d)", width,
                       height, max_texture_size);
    return false;
  }

  glBindTexture(GL_TEXTURE_2D, gles->density_texture);
  s6_water_set_fbo_texture_parameters();
  glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA,
               GL_UNSIGNED_BYTE, NULL);
  glBindFramebuffer(GL_FRAMEBUFFER, gles->density_framebuffer);
  glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                         gles->density_texture, 0);
  const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
  if (status != GL_FRAMEBUFFER_COMPLETE) {
    s6_water_set_error(error, error_size,
                       "S6 density framebuffer incomplete: 0x%04x",
                       (unsigned int)status);
    glBindFramebuffer(GL_FRAMEBUFFER, 0U);
    return false;
  }
  if (!s6_water_capture_error("S6 density target allocation", error,
                              error_size)) {
    glBindFramebuffer(GL_FRAMEBUFFER, 0U);
    return false;
  }
  gles->density_width = width;
  gles->density_height = height;
  glBindFramebuffer(GL_FRAMEBUFFER, 0U);
  return true;
}

static void s6_water_bind_texture(GLuint texture, int unit,
                                  GLint uniform_location) {
  glActiveTexture((GLenum)(GL_TEXTURE0 + unit));
  glBindTexture(GL_TEXTURE_2D, texture);
  if (uniform_location >= 0) {
    glUniform1i(uniform_location, unit);
  }
}

static void s6_water_set_attribute(GLuint program, const char *name, GLint size,
                                   GLsizei stride, size_t byte_offset) {
  const GLint location = glGetAttribLocation(program, name);
  if (location < 0) {
    return;
  }
  glEnableVertexAttribArray((GLuint)location);
  glVertexAttribPointer((GLuint)location, size, GL_FLOAT, GL_FALSE, stride,
                        (const void *)(uintptr_t)byte_offset);
}

static void s6_water_disable_attribute(GLuint program, const char *name) {
  const GLint location = glGetAttribLocation(program, name);
  if (location >= 0) {
    glDisableVertexAttribArray((GLuint)location);
  }
}

static bool s6_water_reserve_particle_staging(LleS6WaterGles *gles,
                                              size_t particle_count,
                                              char *error,
                                              size_t error_size) {
  if (particle_count <= gles->particle_capacity) {
    return true;
  }
  const size_t floats_per_particle =
      (size_t)LLE_S6_WATER_PARTICLE_VERTICES *
      (size_t)LLE_S6_WATER_PARTICLE_VERTEX_FLOATS;
  if (particle_count > SIZE_MAX / floats_per_particle ||
      particle_count * floats_per_particle > SIZE_MAX / sizeof(float)) {
    s6_water_set_error(error, error_size,
                       "S6 density particle staging size overflow");
    return false;
  }

  size_t capacity = gles->particle_capacity > 0U ? gles->particle_capacity : 32U;
  while (capacity < particle_count) {
    if (capacity > SIZE_MAX / 2U) {
      capacity = particle_count;
      break;
    }
    capacity *= 2U;
  }
  if (capacity > SIZE_MAX / floats_per_particle ||
      capacity * floats_per_particle > SIZE_MAX / sizeof(float)) {
    capacity = particle_count;
  }
  const size_t byte_count =
      capacity * floats_per_particle * sizeof(float);
  float *replacement = (float *)realloc(gles->particle_staging, byte_count);
  if (replacement == NULL) {
    s6_water_set_error(error, error_size,
                       "unable to allocate S6 density particle staging");
    return false;
  }
  gles->particle_staging = replacement;
  gles->particle_capacity = capacity;
  return true;
}

static void s6_water_write_density_vertex(float **cursor, float x, float y,
                                          float u, float v) {
  float *out = *cursor;
  out[0] = x;
  out[1] = y;
  out[2] = u;
  out[3] = v;
  *cursor = out + LLE_S6_WATER_PARTICLE_VERTEX_FLOATS;
}

static bool s6_water_upload_particles(
    LleS6WaterGles *gles, const LleS6WaterDensityParticle *particles,
    size_t particle_count, const LleS6WaterRenderState *render_state,
    char *error, size_t error_size) {
  if (particle_count == 0U) {
    return true;
  }
  if (particles == NULL ||
      particle_count > (size_t)(INT_MAX / LLE_S6_WATER_PARTICLE_VERTICES)) {
    s6_water_set_error(error, error_size,
                       "invalid S6 density particle array");
    return false;
  }
  if (!s6_water_reserve_particle_staging(gles, particle_count, error,
                                         error_size)) {
    return false;
  }

  const float inv_width = 1.0f / render_state->surface_width;
  const float inv_height = 1.0f / render_state->surface_height;
  float *cursor = gles->particle_staging;
  for (size_t index = 0U; index < particle_count; ++index) {
    const LleS6WaterDensityParticle *particle = &particles[index];
    if (!isfinite(particle->center_x_px) ||
        !isfinite(particle->center_y_px) ||
        !isfinite(particle->diameter_px) || particle->diameter_px <= 0.0f) {
      s6_water_set_error(error, error_size,
                         "non-finite S6 density particle at index %zu", index);
      return false;
    }
    const float half = particle->diameter_px * 0.5f;
    const float left =
        (particle->center_x_px - half) * inv_width * 2.0f - 1.0f;
    const float right =
        (particle->center_x_px + half) * inv_width * 2.0f - 1.0f;
    const float top =
        1.0f - (particle->center_y_px - half) * inv_height * 2.0f;
    const float bottom =
        1.0f - (particle->center_y_px + half) * inv_height * 2.0f;

    /*
     * Stock indexed order is 0,1,2 / 2,3,1. Its kernel coordinates are
     * horizontally mirrored: BL=(1,0), BR=(0,0), TL=(1,1), TR=(0,1).
     */
    s6_water_write_density_vertex(&cursor, left, bottom, 1.0f, 0.0f);
    s6_water_write_density_vertex(&cursor, right, bottom, 0.0f, 0.0f);
    s6_water_write_density_vertex(&cursor, left, top, 1.0f, 1.0f);
    s6_water_write_density_vertex(&cursor, left, top, 1.0f, 1.0f);
    s6_water_write_density_vertex(&cursor, right, top, 0.0f, 1.0f);
    s6_water_write_density_vertex(&cursor, right, bottom, 0.0f, 0.0f);
  }

  const size_t float_count =
      particle_count * (size_t)LLE_S6_WATER_PARTICLE_VERTICES *
      (size_t)LLE_S6_WATER_PARTICLE_VERTEX_FLOATS;
  glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_S6_WATER_BUFFER_PARTICLES]);
  glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr)(float_count * sizeof(float)),
               gles->particle_staging, GL_STREAM_DRAW);
  return s6_water_capture_error("S6 density particle upload", error,
                                error_size);
}

static void s6_water_fill_quad(float *quad,
                               const LleS6WaterRenderState *state) {
  const float scale = state->background_uv_scale;
  const float left = 1.0f - scale + state->background_uv_offset_x;
  const float right = scale + state->background_uv_offset_x;
  const float top = 1.0f - scale + state->background_uv_offset_y +
                    state->bottom_offset;
  const float bottom = scale + state->background_uv_offset_y -
                       state->bottom_offset;
  const float values[LLE_S6_WATER_QUAD_VERTEX_COUNT *
                     LLE_S6_WATER_QUAD_VERTEX_FLOATS] = {
      -1.0f, -1.0f, 0.0f, 1.0f, left,  bottom, 0.0f, 0.0f,
       1.0f, -1.0f, 1.0f, 1.0f, right, bottom, 1.0f, 0.0f,
      -1.0f,  1.0f, 0.0f, 0.0f, left,  top,    0.0f, 1.0f,
       1.0f,  1.0f, 1.0f, 0.0f, right, top,    1.0f, 1.0f};
  memcpy(quad, values, sizeof(values));
}

static void s6_water_bind_density_quad(LleS6WaterGles *gles, GLuint program) {
  glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_S6_WATER_BUFFER_QUAD]);
  const GLsizei stride =
      (GLsizei)(LLE_S6_WATER_QUAD_VERTEX_FLOATS * sizeof(float));
  s6_water_set_attribute(program, "aPosition", 2, stride, 0U);
  s6_water_set_attribute(program, "aTexUV", 2, stride,
                         2U * sizeof(float));
}

static void s6_water_unbind_density_quad(GLuint program) {
  s6_water_disable_attribute(program, "aPosition");
  s6_water_disable_attribute(program, "aTexUV");
}

static void s6_water_draw_density_field(
    LleS6WaterGles *gles, size_t particle_count) {
  glBindFramebuffer(GL_FRAMEBUFFER, gles->density_framebuffer);
  glViewport(0, 0, gles->density_width, gles->density_height);
  glDisable(GL_DEPTH_TEST);
  glDisable(GL_STENCIL_TEST);
  glDisable(GL_SCISSOR_TEST);
  glDisable(GL_CULL_FACE);
  glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
  glDisable(GL_BLEND);
  glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
  glClear(GL_COLOR_BUFFER_BIT);

  const GLuint program =
      gles->programs[LLE_S6_WATER_PROGRAM_DENSITY];
  glUseProgram(program);
  const GLint texture_uniform = glGetUniformLocation(program, "uTexMap");

  if (gles->has_input[LLE_S6_WATER_TEXTURE_EDGE_DENSITY]) {
    s6_water_bind_density_quad(gles, program);
    s6_water_bind_texture(
        gles->input_textures[LLE_S6_WATER_TEXTURE_EDGE_DENSITY], 0,
        texture_uniform);
    glEnable(GL_BLEND);
    glBlendEquation(GL_FUNC_ADD);
    glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_SRC_ALPHA, GL_ZERO);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, LLE_S6_WATER_QUAD_VERTEX_COUNT);
    glDisable(GL_BLEND);
    s6_water_unbind_density_quad(program);
  }

  if (particle_count == 0U) {
    return;
  }
  glBindBuffer(GL_ARRAY_BUFFER,
               gles->buffers[LLE_S6_WATER_BUFFER_PARTICLES]);
  const GLsizei stride =
      (GLsizei)(LLE_S6_WATER_PARTICLE_VERTEX_FLOATS * sizeof(float));
  s6_water_set_attribute(program, "aPosition", 2, stride, 0U);
  s6_water_set_attribute(program, "aTexUV", 2, stride,
                         2U * sizeof(float));
  s6_water_bind_texture(gles->input_textures[LLE_S6_WATER_TEXTURE_NORMAL], 0,
                        texture_uniform);
  glEnable(GL_BLEND);
  glBlendEquation(GL_FUNC_ADD);
  glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE);
  glDrawArrays(GL_TRIANGLES, 0,
               (GLsizei)(particle_count *
                         (size_t)LLE_S6_WATER_PARTICLE_VERTICES));
  glDisable(GL_BLEND);
  s6_water_disable_attribute(program, "aPosition");
  s6_water_disable_attribute(program, "aTexUV");
}

static void s6_water_set_uniform1f(GLuint program, const char *name,
                                   float value) {
  const GLint location = glGetUniformLocation(program, name);
  if (location >= 0) {
    glUniform1f(location, value);
  }
}

static void s6_water_set_uniform2f(GLuint program, const char *name, float x,
                                   float y) {
  const GLint location = glGetUniformLocation(program, name);
  if (location >= 0) {
    glUniform2f(location, x, y);
  }
}

static void s6_water_draw_composite(
    LleS6WaterGles *gles, int background_slot,
    const LleS6WaterRenderState *state) {
  glBindFramebuffer(GL_FRAMEBUFFER, 0U);
  glViewport(0, 0, gles->surface_width, gles->surface_height);
  glDisable(GL_DEPTH_TEST);
  glDisable(GL_STENCIL_TEST);
  glDisable(GL_SCISSOR_TEST);
  glDisable(GL_CULL_FACE);
  glDisable(GL_BLEND);
  glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
  glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
  glClear(GL_COLOR_BUFFER_BIT);

  const GLuint program =
      gles->programs[LLE_S6_WATER_PROGRAM_COMPOSITE];
  glUseProgram(program);
  s6_water_bind_texture(gles->input_textures[background_slot], 0,
                        glGetUniformLocation(program, "uBG"));
  s6_water_bind_texture(gles->density_texture, 1,
                        glGetUniformLocation(program, "uDensity"));
  s6_water_set_uniform2f(program, "uInvRes",
                         1.0f / (float)gles->surface_width,
                         1.0f / (float)gles->surface_height);
  s6_water_set_uniform2f(program, "uInvDensityRes",
                         1.0f / (float)gles->density_width,
                         1.0f / (float)gles->density_height);
  s6_water_set_uniform1f(program, "uEdgeRatio", state->edge_ratio);
  s6_water_set_uniform1f(program, "uRestore", state->restore_ratio);
  s6_water_set_uniform1f(
      program, "uRefractionRatio",
      state->refraction_ratio * state->refraction_ratio *
          state->refraction_ratio);
  s6_water_set_uniform1f(program, "uEdgeOffsetRatio",
                         state->edge_offset_ratio);
  s6_water_set_uniform1f(program, "uSpecularRatio",
                         state->specular_ratio);
  s6_water_set_uniform1f(program, "uThreshold",
                         state->density_threshold);
  s6_water_set_uniform1f(program, "uEdgeOffset", state->edge_offset);
  s6_water_set_uniform1f(program, "uShadowOffset", state->shadow_offset);
  s6_water_set_uniform1f(program, "uShadowRange", state->shadow_range);
  s6_water_set_uniform1f(program, "uRefractionEta",
                         state->refraction_eta);
  s6_water_set_uniform1f(program, "uRefractionAmplitude",
                         state->refraction_amplitude);

  glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_S6_WATER_BUFFER_QUAD]);
  const GLsizei stride =
      (GLsizei)(LLE_S6_WATER_QUAD_VERTEX_FLOATS * sizeof(float));
  s6_water_set_attribute(program, "aPosition", 2, stride, 0U);
  s6_water_set_attribute(program, "aTexUV", 2, stride,
                         2U * sizeof(float));
  s6_water_set_attribute(program, "aTabScaledUV", 2, stride,
                         4U * sizeof(float));
  s6_water_set_attribute(program, "aDensityUV", 2, stride,
                         6U * sizeof(float));
  glDrawArrays(GL_TRIANGLE_STRIP, 0, LLE_S6_WATER_QUAD_VERTEX_COUNT);
  s6_water_disable_attribute(program, "aPosition");
  s6_water_disable_attribute(program, "aTexUV");
  s6_water_disable_attribute(program, "aTabScaledUV");
  s6_water_disable_attribute(program, "aDensityUV");
}

bool lle_s6_water_gles_init(LleS6WaterGles *gles, int width, int height,
                            char *error, size_t error_size) {
  s6_water_clear_error(error, error_size);
  if (gles == NULL || width <= 0 || height <= 0) {
    s6_water_set_error(error, error_size,
                       "invalid S6 Water Droplet GLES initialization");
    return false;
  }

  /*
   * init is also the post-abandon reconstruction path. Delete only names
   * still owned by the current context and retain the CPU staging allocation.
   */
  if (gles->ready) {
    s6_water_delete_names(gles);
  } else {
    s6_water_forget_names(gles);
  }
  s6_water_drain_errors();

  gles->programs[LLE_S6_WATER_PROGRAM_DENSITY] =
      s6_water_create_program(
          LLE_S6_WATER_DENSITY_VERTEX_SHADER,
          LLE_S6_WATER_DENSITY_FRAGMENT_SHADER, "S6 density shader", error,
          error_size);
  if (gles->programs[LLE_S6_WATER_PROGRAM_DENSITY] == 0U) {
    s6_water_delete_names(gles);
    return false;
  }
  gles->programs[LLE_S6_WATER_PROGRAM_COMPOSITE] =
      s6_water_create_program(
          LLE_S6_WATER_COMPOSITE_VERTEX_SHADER,
          LLE_S6_WATER_COMPOSITE_FRAGMENT_SHADER, "S6 water shader", error,
          error_size);
  if (gles->programs[LLE_S6_WATER_PROGRAM_COMPOSITE] == 0U) {
    s6_water_delete_names(gles);
    return false;
  }

  glGenBuffers(LLE_S6_WATER_GLES_BUFFER_COUNT, gles->buffers);
  glGenTextures(LLE_S6_WATER_INPUT_TEXTURE_COUNT, gles->input_textures);
  glGenTextures(1, &gles->density_texture);
  glGenFramebuffers(1, &gles->density_framebuffer);
  for (int index = 0; index < LLE_S6_WATER_INPUT_TEXTURE_COUNT; ++index) {
    glBindTexture(GL_TEXTURE_2D, gles->input_textures[index]);
    s6_water_set_input_texture_parameters();
  }
  glBindTexture(GL_TEXTURE_2D, gles->density_texture);
  s6_water_set_fbo_texture_parameters();

  LleS6WaterRenderState initial_state;
  memset(&initial_state, 0, sizeof(initial_state));
  initial_state.background_uv_scale = 0.95f;
  float quad[LLE_S6_WATER_QUAD_VERTEX_COUNT *
             LLE_S6_WATER_QUAD_VERTEX_FLOATS];
  s6_water_fill_quad(quad, &initial_state);
  glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_S6_WATER_BUFFER_QUAD]);
  glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr)sizeof(quad), quad,
               GL_DYNAMIC_DRAW);

  if (!s6_water_capture_error("S6 GLES name initialization", error,
                              error_size) ||
      !lle_s6_water_gles_resize(gles, width, height, error, error_size)) {
    s6_water_delete_names(gles);
    return false;
  }
  gles->ready = true;
  return true;
}

bool lle_s6_water_gles_resize(LleS6WaterGles *gles, int width, int height,
                              char *error, size_t error_size) {
  s6_water_clear_error(error, error_size);
  if (gles == NULL || width <= 0 || height <= 0 ||
      gles->density_texture == 0U || gles->density_framebuffer == 0U) {
    s6_water_set_error(error, error_size,
                       "invalid S6 Water Droplet GLES resize");
    return false;
  }
  int density_width = 0;
  int density_height = 0;
  s6_water_compute_density_size(width, height, &density_width,
                                &density_height);
  const bool density_changed =
      density_width != gles->density_width ||
      density_height != gles->density_height;
  if (density_changed &&
      !s6_water_allocate_density_target(gles, density_width, density_height,
                                        error, error_size)) {
    return false;
  }
  gles->surface_width = width;
  gles->surface_height = height;
  glBindFramebuffer(GL_FRAMEBUFFER, 0U);
  glViewport(0, 0, width, height);
  return s6_water_capture_error("S6 GLES resize", error, error_size);
}

void lle_s6_water_gles_destroy(LleS6WaterGles *gles) {
  if (gles == NULL) {
    return;
  }
  s6_water_delete_names(gles);
  free(gles->particle_staging);
  gles->particle_staging = NULL;
  gles->particle_capacity = 0U;
}

void lle_s6_water_gles_abandon(LleS6WaterGles *gles) {
  if (gles == NULL) {
    return;
  }
  s6_water_forget_names(gles);
}

bool lle_s6_water_gles_upload_bitmap(LleS6WaterGles *gles, JNIEnv *env,
                                     int slot, jobject bitmap, char *error,
                                     size_t error_size) {
  s6_water_clear_error(error, error_size);
  if (gles == NULL || !gles->ready || env == NULL || bitmap == NULL ||
      slot < 0 || slot >= LLE_S6_WATER_INPUT_TEXTURE_COUNT ||
      gles->input_textures[slot] == 0U) {
    s6_water_set_error(error, error_size,
                       "invalid S6 Water Droplet bitmap upload");
    return false;
  }

  AndroidBitmapInfo info;
  memset(&info, 0, sizeof(info));
  const int info_result = AndroidBitmap_getInfo(env, bitmap, &info);
  if (info_result != ANDROID_BITMAP_RESULT_SUCCESS || info.width == 0U ||
      info.height == 0U || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
      info.width > (uint32_t)INT_MAX || info.height > (uint32_t)INT_MAX ||
      (uint64_t)info.width * UINT64_C(4) > (uint64_t)info.stride) {
    s6_water_set_error(
        error, error_size,
        "unsupported S6 bitmap slot=%d result=%d format=%u size=%ux%u "
        "stride=%u",
        slot, info_result, info.format, info.width, info.height, info.stride);
    return false;
  }

  void *locked_pixels = NULL;
  const int lock_result =
      AndroidBitmap_lockPixels(env, bitmap, &locked_pixels);
  if (lock_result != ANDROID_BITMAP_RESULT_SUCCESS ||
      locked_pixels == NULL) {
    s6_water_set_error(error, error_size,
                       "S6 bitmap lock failed slot=%d result=%d", slot,
                       lock_result);
    return false;
  }

  const size_t row_bytes = (size_t)info.width * 4U;
  const size_t packed_bytes = row_bytes * (size_t)info.height;
  const void *upload_pixels = locked_pixels;
  uint8_t *packed_pixels = NULL;
  if ((size_t)info.stride != row_bytes) {
    if ((size_t)info.height > SIZE_MAX / row_bytes) {
      s6_water_set_error(error, error_size,
                         "S6 bitmap packed size overflow");
      (void)AndroidBitmap_unlockPixels(env, bitmap);
      return false;
    }
    packed_pixels = (uint8_t *)malloc(packed_bytes);
    if (packed_pixels == NULL) {
      s6_water_set_error(error, error_size,
                         "unable to repack S6 bitmap rows");
      (void)AndroidBitmap_unlockPixels(env, bitmap);
      return false;
    }
    const uint8_t *source = (const uint8_t *)locked_pixels;
    for (uint32_t row = 0U; row < info.height; ++row) {
      memcpy(packed_pixels + (size_t)row * row_bytes,
             source + (size_t)row * (size_t)info.stride, row_bytes);
    }
    upload_pixels = packed_pixels;
  }

  s6_water_drain_errors();
  glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
  glBindTexture(GL_TEXTURE_2D, gles->input_textures[slot]);
  s6_water_set_input_texture_parameters();
  glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, (GLsizei)info.width,
               (GLsizei)info.height, 0, GL_RGBA, GL_UNSIGNED_BYTE,
               upload_pixels);
  const bool uploaded =
      s6_water_capture_error("S6 bitmap upload", error, error_size);
  free(packed_pixels);
  (void)AndroidBitmap_unlockPixels(env, bitmap);
  if (!uploaded) {
    return false;
  }

  gles->input_width[slot] = (int)info.width;
  gles->input_height[slot] = (int)info.height;
  gles->has_input[slot] = true;
  return true;
}

void lle_s6_water_gles_clear_bitmap(LleS6WaterGles *gles, int slot) {
  if (gles == NULL || slot < 0 ||
      slot >= LLE_S6_WATER_INPUT_TEXTURE_COUNT) {
    return;
  }
  gles->has_input[slot] = false;
  gles->input_width[slot] = 0;
  gles->input_height[slot] = 0;
}

static bool s6_water_render_state_valid(
    const LleS6WaterRenderState *state) {
  return state != NULL && isfinite(state->surface_width) &&
         isfinite(state->surface_height) &&
         state->surface_width >= 1.0f && state->surface_height >= 1.0f &&
         state->surface_width <= (float)INT_MAX &&
         state->surface_height <= (float)INT_MAX &&
         isfinite(state->background_uv_scale) &&
         isfinite(state->background_uv_offset_x) &&
         isfinite(state->background_uv_offset_y) &&
         isfinite(state->bottom_offset) && isfinite(state->restore_ratio) &&
         isfinite(state->edge_ratio) &&
         isfinite(state->refraction_ratio) &&
         isfinite(state->edge_offset_ratio) &&
         isfinite(state->specular_ratio) &&
         isfinite(state->density_threshold) &&
         isfinite(state->edge_offset) && isfinite(state->shadow_offset) &&
         isfinite(state->shadow_range) &&
         isfinite(state->refraction_eta) &&
         isfinite(state->refraction_amplitude) &&
         state->density_threshold > 0.0f &&
         state->density_threshold < 1.0f &&
         state->edge_offset >= 0.0f && state->shadow_offset >= 0.0f &&
         state->shadow_range >= 0.0f &&
         state->refraction_amplitude >= 0.0f;
}

bool lle_s6_water_gles_draw(
    LleS6WaterGles *gles, const LleS6WaterDensityParticle *particles,
    size_t particle_count, const LleS6WaterRenderState *render_state,
    char *error, size_t error_size) {
  s6_water_clear_error(error, error_size);
  if (gles == NULL || !gles->ready ||
      !s6_water_render_state_valid(render_state) ||
      (particle_count > 0U && particles == NULL)) {
    s6_water_set_error(error, error_size,
                       "S6 Water Droplet GLES renderer/state is not ready");
    return false;
  }

  const int width = (int)(render_state->surface_width + 0.5f);
  const int height = (int)(render_state->surface_height + 0.5f);
  if (width <= 0 || height <= 0) {
    s6_water_set_error(error, error_size,
                       "invalid S6 Water Droplet render dimensions");
    return false;
  }
  if ((width != gles->surface_width || height != gles->surface_height) &&
      !lle_s6_water_gles_resize(gles, width, height, error, error_size)) {
    return false;
  }

  const int background_slot =
      height >= width ? LLE_S6_WATER_TEXTURE_PORTRAIT_BACKGROUND
                      : LLE_S6_WATER_TEXTURE_LANDSCAPE_BACKGROUND;
  if (!gles->has_input[background_slot]) {
    s6_water_set_error(error, error_size,
                       "active S6 Water Droplet background is missing");
    return false;
  }
  if (!gles->has_input[LLE_S6_WATER_TEXTURE_EDGE_DENSITY]) {
    s6_water_set_error(error, error_size,
                       "S6 Water Droplet edge-density texture is missing");
    return false;
  }
  if (particle_count > 0U &&
      !gles->has_input[LLE_S6_WATER_TEXTURE_NORMAL]) {
    s6_water_set_error(error, error_size,
                       "S6 Water Droplet normal texture is missing");
    return false;
  }
  if (render_state->density_particle_count != particle_count) {
    s6_water_set_error(
        error, error_size,
        "S6 Water Droplet snapshot count mismatch: state=%zu draw=%zu",
        render_state->density_particle_count, particle_count);
    return false;
  }

  s6_water_drain_errors();
  if (!s6_water_upload_particles(gles, particles, particle_count,
                                 render_state, error, error_size)) {
    return false;
  }
  float quad[LLE_S6_WATER_QUAD_VERTEX_COUNT *
             LLE_S6_WATER_QUAD_VERTEX_FLOATS];
  s6_water_fill_quad(quad, render_state);
  glBindBuffer(GL_ARRAY_BUFFER, gles->buffers[LLE_S6_WATER_BUFFER_QUAD]);
  glBufferSubData(GL_ARRAY_BUFFER, 0, (GLsizeiptr)sizeof(quad), quad);

  s6_water_draw_density_field(gles, particle_count);
  s6_water_draw_composite(gles, background_slot, render_state);
  glBindFramebuffer(GL_FRAMEBUFFER, 0U);
  glBindBuffer(GL_ARRAY_BUFFER, 0U);
  glActiveTexture(GL_TEXTURE1);
  glBindTexture(GL_TEXTURE_2D, 0U);
  glActiveTexture(GL_TEXTURE0);
  glBindTexture(GL_TEXTURE_2D, 0U);
  glUseProgram(0U);
  return s6_water_capture_error("S6 Water Droplet frame", error,
                                error_size);
}
