#include <GLES2/gl2.h>
#include <android/log.h>
#include <jni.h>

#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define LOG_TAG "LLE64-Watercolor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))
#ifndef LLE_WATERCOLOR_STOCK_FEEDBACK
#define LLE_WATERCOLOR_STOCK_FEEDBACK 0
#endif
/* Literal classic S4/Note 4 Watercolor values recovered from ARM32. */
static const float kRadialFboScale = 0.025f;
static const float kDensityFboScale = 0.60f;
static const float kBrushScale = 0.80f;
static const float kPortraitBrushFactor = 0.35f;
static const float kSquareBrushFactor = 0.196875f;
static const float kDragThresholdWidth = 0.025f;
static const float kDragInterpolationWidth = 0.05f;
static const float kMoveSizeMin = 0.55f;
static const float kMoveSizeRange = 0.25f;
static const float kMoveScaleStep = 0.025f;
static const float kMoveScaleMin = 0.50f;
/* Common-library setters scale the scene's nominal 3.4/3.6 before upload. */
static const float kNoiseVectorScalar = 425.0f;
static const float kRadialVectorScalar = 66.69f;
static const float kSaturation = 1.2f;
static const float kRedSaturation = 1.3f;
static const float kGreenSaturation = 0.4f;
static const float kBlueSaturation = 0.4f;
static const float kBrightness = 1.35f;
static const float kStampAlphaStep = 0.025f;
static const float kStampAlphaLimit = 1.06f;

typedef struct Stamp {
    float initial_size;
    float baseline_size;
    float size;
    float alpha;
    float x;
    float y;
    int mask_index;
    int tube_path;
} Stamp;

typedef struct BrushProgramLocations {
    GLint a_local;
    GLint u_center;
    GLint u_size;
    GLint u_screen;
    GLint u_screen_ratio;
    GLint u_time_step;
    GLint u_alpha;
    GLint u_mask;
    GLint u_tube;
} BrushProgramLocations;

typedef struct AdvectProgramLocations {
    GLint a_position;
    GLint a_uv;
    GLint u_density;
    GLint u_velocity;
    GLint u_radial;
    GLint u_original;
    GLint u_noise_scalar;
    GLint u_radial_scalar;
} AdvectProgramLocations;

typedef struct MixProgramLocations {
    GLint a_position;
    GLint a_uv;
    GLint u_density;
    GLint u_alpha;
    GLint u_saturation;
    GLint u_brightness;
    GLint u_red_saturation;
    GLint u_green_saturation;
    GLint u_blue_saturation;
} MixProgramLocations;

typedef struct WatercolorState {
    int width;
    int height;
    int radial_width;
    int radial_height;
    int density_width;
    int density_height;

    GLuint mask_textures[3];
    GLuint tube_texture;
    GLuint noise_texture;
    GLuint background_texture;
    GLuint radial_texture;
    GLuint radial_fbo;
    GLuint density_textures[2];
    GLuint density_fbos[2];

    GLuint brush_program;
    GLuint advect_program;
    GLuint mix_program;
    BrushProgramLocations brush_locations;
    AdvectProgramLocations advect_locations;
    MixProgramLocations mix_locations;

    uint32_t *noise_source_argb;
    int noise_source_width;
    int noise_source_height;
    Stamp *stamps;
    size_t stamp_count;
    size_t stamp_capacity;
    Stamp secondary_stamps[4];
    int secondary_count;
    int initialized;
    int background_ready;
    int texture_assets_ready;
    int density_read_index;
    int density_seeded;
    int gesture_active;
    int paused;
    int clear_requested;
    int unlock_special;
    int unlock_countdown;
    float unlock_gate;
    int pending_affordance_reset;
    int current_mask_index;
    float last_x;
    float last_y;
    float move_scale;
    uint64_t frame_number;
} WatercolorState;

static int g_paused;

static GLuint create_noise_gradient_texture(const uint32_t *argb,
        int width, int height, int screen_width, int screen_height);

static const char *kFullVertexShader =
        "precision mediump float;\n"
        "attribute vec2 aPosition;\n"
        "attribute vec2 aTexUV;\n"
        "varying vec2 vTexUV;\n"
        "void main() {\n"
        "  vTexUV = aTexUV;\n"
        "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
        "}\n";

/* FBO UV stays bottom-origin; Android ARGB upload needs one Y flip. */
static const char *kAdvectVertexShader =
        "precision mediump float;\n"
        "attribute vec2 aPosition;\n"
        "attribute vec2 aTexUV;\n"
        "varying highp vec2 vTexUV;\n"
        "varying vec2 vTexUVBG;\n"
        "void main() {\n"
        "  vTexUV = aTexUV;\n"
        "  vTexUVBG = vec2(aTexUV.x, 1.0 - aTexUV.y);\n"
        "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
        "}\n";

/*
 * SPDrawRadialWaterBrush, expressed with direct pixel-space uniforms.  The
 * fragment formula is the recovered Samsung tube variant: RG stores radial
 * direction, B stores time step shaped by Tube.r, and A stores the mask fade.
 */
static const char *kBrushVertexShader =
        "precision mediump float;\n"
        "attribute vec2 aLocal;\n"
        "uniform vec2 uCenter;\n"
        "uniform vec2 uSize;\n"
        "uniform vec2 uScreen;\n"
        "varying vec2 vTexUV;\n"
        "varying vec2 vPosition;\n"
        "void main() {\n"
        "  vec2 pixel = uCenter + aLocal * uSize * 0.5;\n"
        "  vec2 clip = vec2(pixel.x / uScreen.x * 2.0 - 1.0,\n"
        "                   pixel.y / uScreen.y * 2.0 - 1.0);\n"
        "  vTexUV = aLocal * 0.5 + 0.5;\n"
        "  vTexUV.y = 1.0 - vTexUV.y;\n"
        "  vPosition = pixel;\n"
        "  gl_Position = vec4(clip, 0.0, 1.0);\n"
        "}\n";

static const char *kBrushFragmentShader =
        "precision mediump float;\n"
        "uniform sampler2D uMask;\n"
        "uniform sampler2D uTube;\n"
        "uniform vec2 uCenter;\n"
        "uniform vec2 uScreenRatio;\n"
        "uniform float uTimeStep;\n"
        "uniform float uAlpha;\n"
        "varying vec2 vTexUV;\n"
        "varying vec2 vPosition;\n"
        "void main() {\n"
        "  vec4 maskColor = texture2D(uMask, vTexUV);\n"
        "  vec4 tubeColor = texture2D(uTube, vTexUV);\n"
        "  vec2 direction = uCenter - vPosition;\n"
        "  float len = length(direction);\n"
        "  vec2 radial = len > 0.0001 ? direction / len * 0.1 : vec2(0.0);\n"
        "  radial *= uScreenRatio;\n"
        "  radial += 0.5;\n"
        "  gl_FragColor = vec4(radial, uTimeStep * tubeColor.r,\n"
        "      maskColor.a * clamp(1.0 - uAlpha, 0.0, 1.0));\n"
        "}\n";

/* Recovered SPDrawBGAdvectWaterBrush displacement and source recovery. */
static const char *kAdvectFragmentShader =
        "precision mediump float;\n"
        "uniform sampler2D uDensity;\n"
        "uniform sampler2D uVelocity;\n"
        "uniform sampler2D uRadial;\n"
        "uniform sampler2D uOriginal;\n"
        "uniform float uNoiseVectorScalar;\n"
        "uniform float uRadialVectorScalar;\n"
        "varying highp vec2 vTexUV;\n"
        "varying vec2 vTexUVBG;\n"
        "void main() {\n"
        "  vec4 alphaColor = texture2D(uRadial, vTexUV);\n"
        "  vec4 texColor;\n"
        "  if (alphaColor.a != 0.0) {\n"
        "    highp vec4 noiseVelocity = texture2D(uVelocity, vTexUVBG);\n"
        "    vec2 densityUV = vTexUV + ((alphaColor.xy - 0.5) * 10.0\n"
        "        * uRadialVectorScalar + (noiseVelocity.xy - 0.5)\n"
        "        * uNoiseVectorScalar) * alphaColor.b * 0.0175 * 0.006;\n"
        "    texColor = mix(texture2D(uDensity, densityUV),\n"
        "        texture2D(uOriginal, vTexUVBG), 0.03);\n"
        "  } else {\n"
        "    texColor = texture2D(uOriginal, vTexUVBG);\n"
        "  }\n"
        "  gl_FragColor = texColor;\n"
        "}\n";

/*
 * Recovered classic SPDrawMixWaterBrush colour shaping. Samsung writes
 * mix(background, density, alpha) to an opaque framebuffer. LLE64 emits the
 * equivalent premultiplied local density term, letting Android composition
 * supply background * (1-alpha) from the real SystemUI pixel.
 */
static const char *kMixFragmentShader =
        "precision mediump float;\n"
        "uniform sampler2D uDensity;\n"
        "uniform sampler2D uAlpha;\n"
        "uniform float uSaturation;\n"
        "uniform float uBrightness;\n"
        "uniform float uRedSaturation;\n"
        "uniform float uGreenSaturation;\n"
        "uniform float uBlueSaturation;\n"
        "varying vec2 vTexUV;\n"
        "void main() {\n"
        "  vec4 alphaColor = texture2D(uAlpha, vTexUV);\n"
        "  vec4 densityColor = texture2D(uDensity, vTexUV) * uBrightness;\n"
        "  float p = sqrt(densityColor.r * densityColor.r * uRedSaturation\n"
        "      + densityColor.g * densityColor.g * uGreenSaturation\n"
        "      + densityColor.b * densityColor.b * uBlueSaturation);\n"
        "  densityColor.r = p + (densityColor.r - p) * uSaturation;\n"
        "  densityColor.g = p + (densityColor.g - p) * uSaturation;\n"
        "  densityColor.b = p + (densityColor.b - p) * uSaturation;\n"
        "  float localAlpha = alphaColor.a;\n"
        "  gl_FragColor = vec4(densityColor.rgb * localAlpha, localAlpha);\n"
        "}\n";

static const GLfloat kFullQuad[] = {
        -1.0f, -1.0f, 0.0f, 0.0f,
         1.0f, -1.0f, 1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f, 1.0f,
        -1.0f,  1.0f, 0.0f, 1.0f,
         1.0f, -1.0f, 1.0f, 0.0f,
         1.0f,  1.0f, 1.0f, 1.0f,
};

static const GLfloat kBrushQuad[] = {
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
        -1.0f,  1.0f,
         1.0f, -1.0f,
         1.0f,  1.0f,
};

static jfieldID effect_id_field(JNIEnv *env, jobject object) {
    jclass clazz = (*env)->GetObjectClass(env, object);
    if (clazz == NULL) {
        return NULL;
    }
    jfieldID field = (*env)->GetFieldID(env, clazz, "mEffectId", "J");
    (*env)->DeleteLocalRef(env, clazz);
    return field;
}

static WatercolorState *get_state(JNIEnv *env, jobject object) {
    jfieldID field = effect_id_field(env, object);
    if (field == NULL) {
        return NULL;
    }
    return (WatercolorState *)(uintptr_t)(*env)->GetLongField(env, object, field);
}

static void set_state(JNIEnv *env, jobject object, WatercolorState *state) {
    jfieldID field = effect_id_field(env, object);
    if (field != NULL) {
        (*env)->SetLongField(env, object, field, (jlong)(uintptr_t)state);
    }
}

static GLuint compile_shader(GLenum type, const char *source) {
    GLuint shader = glCreateShader(type);
    if (shader == 0) {
        return 0;
    }
    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled != GL_TRUE) {
        GLint length = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
        char *message = length > 1 ? (char *)calloc((size_t)length, 1) : NULL;
        if (message != NULL) {
            glGetShaderInfoLog(shader, length, NULL, message);
            LOGE("shader compile failed: %s", message);
            free(message);
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

static GLuint create_program(const char *vertex_source, const char *fragment_source) {
    GLuint vertex = compile_shader(GL_VERTEX_SHADER, vertex_source);
    GLuint fragment = compile_shader(GL_FRAGMENT_SHADER, fragment_source);
    if (vertex == 0 || fragment == 0) {
        if (vertex != 0) glDeleteShader(vertex);
        if (fragment != 0) glDeleteShader(fragment);
        return 0;
    }
    GLuint program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glLinkProgram(program);
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (linked != GL_TRUE) {
        GLint length = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &length);
        char *message = length > 1 ? (char *)calloc((size_t)length, 1) : NULL;
        if (message != NULL) {
            glGetProgramInfoLog(program, length, NULL, message);
            LOGE("program link failed: %s", message);
            free(message);
        }
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

static void cache_program_locations(WatercolorState *state) {
    BrushProgramLocations *brush = &state->brush_locations;
    brush->a_local = glGetAttribLocation(state->brush_program, "aLocal");
    brush->u_center = glGetUniformLocation(state->brush_program, "uCenter");
    brush->u_size = glGetUniformLocation(state->brush_program, "uSize");
    brush->u_screen = glGetUniformLocation(state->brush_program, "uScreen");
    brush->u_screen_ratio = glGetUniformLocation(state->brush_program, "uScreenRatio");
    brush->u_time_step = glGetUniformLocation(state->brush_program, "uTimeStep");
    brush->u_alpha = glGetUniformLocation(state->brush_program, "uAlpha");
    brush->u_mask = glGetUniformLocation(state->brush_program, "uMask");
    brush->u_tube = glGetUniformLocation(state->brush_program, "uTube");

    AdvectProgramLocations *advect = &state->advect_locations;
    advect->a_position = glGetAttribLocation(state->advect_program, "aPosition");
    advect->a_uv = glGetAttribLocation(state->advect_program, "aTexUV");
    advect->u_density = glGetUniformLocation(state->advect_program, "uDensity");
    advect->u_velocity = glGetUniformLocation(state->advect_program, "uVelocity");
    advect->u_radial = glGetUniformLocation(state->advect_program, "uRadial");
    advect->u_original = glGetUniformLocation(state->advect_program, "uOriginal");
    advect->u_noise_scalar = glGetUniformLocation(
            state->advect_program, "uNoiseVectorScalar");
    advect->u_radial_scalar = glGetUniformLocation(
            state->advect_program, "uRadialVectorScalar");

    MixProgramLocations *mix = &state->mix_locations;
    mix->a_position = glGetAttribLocation(state->mix_program, "aPosition");
    mix->a_uv = glGetAttribLocation(state->mix_program, "aTexUV");
    mix->u_density = glGetUniformLocation(state->mix_program, "uDensity");
    mix->u_alpha = glGetUniformLocation(state->mix_program, "uAlpha");
    mix->u_saturation = glGetUniformLocation(state->mix_program, "uSaturation");
    mix->u_brightness = glGetUniformLocation(state->mix_program, "uBrightness");
    mix->u_red_saturation = glGetUniformLocation(
            state->mix_program, "uRedSaturation");
    mix->u_green_saturation = glGetUniformLocation(
            state->mix_program, "uGreenSaturation");
    mix->u_blue_saturation = glGetUniformLocation(
            state->mix_program, "uBlueSaturation");
}

static void delete_texture(GLuint *texture) {
    if (*texture != 0) {
        glDeleteTextures(1, texture);
        *texture = 0;
    }
}

static void delete_fbo(GLuint *fbo) {
    if (*fbo != 0) {
        glDeleteFramebuffers(1, fbo);
        *fbo = 0;
    }
}

static void release_gl(WatercolorState *state) {
    if (state == NULL) return;
    for (int i = 0; i < 3; ++i) delete_texture(&state->mask_textures[i]);
    delete_texture(&state->tube_texture);
    delete_texture(&state->noise_texture);
    delete_texture(&state->background_texture);
    delete_texture(&state->radial_texture);
    delete_fbo(&state->radial_fbo);
    for (int i = 0; i < 2; ++i) {
        delete_texture(&state->density_textures[i]);
        delete_fbo(&state->density_fbos[i]);
    }
    if (state->brush_program != 0) glDeleteProgram(state->brush_program);
    if (state->advect_program != 0) glDeleteProgram(state->advect_program);
    if (state->mix_program != 0) glDeleteProgram(state->mix_program);
    state->brush_program = 0;
    state->advect_program = 0;
    state->mix_program = 0;
    state->initialized = 0;
    state->background_ready = 0;
    state->texture_assets_ready = 0;
    state->density_seeded = 0;
}

static int create_fbo_texture(int width, int height, GLuint *texture, GLuint *fbo) {
    glGenTextures(1, texture);
    glBindTexture(GL_TEXTURE_2D, *texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glGenFramebuffers(1, fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, *fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D, *texture, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("FBO incomplete size=%dx%d status=0x%x", width, height, status);
        delete_fbo(fbo);
        delete_texture(texture);
        return 0;
    }
    return 1;
}

static void clear_default_framebuffer(WatercolorState *state) {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, state->width, state->height);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_BLEND);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
}

static int initialize_gl(WatercolorState *state, int width, int height) {
    if (state == NULL || width <= 0 || height <= 0) return 0;
    delete_texture(&state->radial_texture);
    delete_fbo(&state->radial_fbo);
    for (int i = 0; i < 2; ++i) {
        delete_texture(&state->density_textures[i]);
        delete_fbo(&state->density_fbos[i]);
    }
    if (state->brush_program != 0) glDeleteProgram(state->brush_program);
    if (state->advect_program != 0) glDeleteProgram(state->advect_program);
    if (state->mix_program != 0) glDeleteProgram(state->mix_program);

    state->width = width;
    state->height = height;
    state->radial_width = (int)((float)width * kRadialFboScale);
    state->radial_height = (int)((float)height * kRadialFboScale);
    state->density_width = (int)((float)width * kDensityFboScale);
    state->density_height = (int)((float)height * kDensityFboScale);
    if (state->radial_width < 1) state->radial_width = 1;
    if (state->radial_height < 1) state->radial_height = 1;
    if (state->density_width < 1) state->density_width = 1;
    if (state->density_height < 1) state->density_height = 1;

    if (state->noise_source_argb != NULL) {
        GLuint rebuilt_noise = create_noise_gradient_texture(
                state->noise_source_argb, state->noise_source_width,
                state->noise_source_height, width, height);
        if (rebuilt_noise != 0) {
            delete_texture(&state->noise_texture);
            state->noise_texture = rebuilt_noise;
        }
    }

    state->brush_program = create_program(kBrushVertexShader, kBrushFragmentShader);
    state->advect_program = create_program(kAdvectVertexShader, kAdvectFragmentShader);
    state->mix_program = create_program(kFullVertexShader, kMixFragmentShader);
    if (state->brush_program == 0 || state->advect_program == 0
            || state->mix_program == 0
            || !create_fbo_texture(state->radial_width, state->radial_height,
                    &state->radial_texture, &state->radial_fbo)
            || !create_fbo_texture(state->density_width, state->density_height,
                    &state->density_textures[0], &state->density_fbos[0])
            || !create_fbo_texture(state->density_width, state->density_height,
                    &state->density_textures[1], &state->density_fbos[1])) {
        LOGE("Watercolor GL initialization failed");
        state->initialized = 0;
        return 0;
    }
    cache_program_locations(state);
    state->initialized = 1;
    state->clear_requested = 1;
    state->frame_number = 0;
    state->density_read_index = 0;
    state->density_seeded = 0;
    LOGI("initialized size=%dx%d radial=%dx%d density=%dx%d feedback=%s",
            width, height, state->radial_width, state->radial_height,
            state->density_width, state->density_height,
            LLE_WATERCOLOR_STOCK_FEEDBACK
                    ? "stock-same-texture-ab" : "stable-ping-pong");
    return 1;
}

static float base_brush_size(const WatercolorState *state) {
    int short_side = state->width < state->height ? state->width : state->height;
    if (state->width == state->height) {
        return kBrushScale * kSquareBrushFactor * (float)short_side;
    }
    return kBrushScale * kPortraitBrushFactor * (float)short_side;
}

static int random_mask(void) {
    int index = (int)((double)rand() * (2.99 / 2147483648.0));
    if (index < 0) return 0;
    if (index > 2) return 2;
    return index;
}

static float random_move_size(float base_size, float move_scale) {
    float unit = (float)((double)rand() / 2147483648.0);
    return base_size * move_scale * (kMoveSizeMin + unit * kMoveSizeRange);
}

static int reserve_stamps(WatercolorState *state, size_t required) {
    if (required <= state->stamp_capacity) return 1;
    size_t capacity = state->stamp_capacity > 0 ? state->stamp_capacity : 64U;
    while (capacity < required) {
        if (capacity > SIZE_MAX / 2U) return 0;
        capacity *= 2U;
    }
    if (capacity > SIZE_MAX / sizeof(Stamp)) return 0;
    Stamp *grown = (Stamp *)realloc(state->stamps, capacity * sizeof(Stamp));
    if (grown == NULL) return 0;
    state->stamps = grown;
    state->stamp_capacity = capacity;
    return 1;
}

static void add_stamp(WatercolorState *state, float x, float y,
        float size, int tube_path) {
    if (state == NULL || size <= 0.0f) return;
    if (!reserve_stamps(state, state->stamp_count + 1U)) {
        LOGE("unable to grow primary brush queue count=%zu", state->stamp_count);
        return;
    }
    Stamp *stamp = &state->stamps[state->stamp_count++];
    stamp->initial_size = size;
    stamp->baseline_size = size;
    stamp->size = size;
    stamp->alpha = 0.0f;
    stamp->x = x;
    stamp->y = y;
    stamp->mask_index = tube_path ? 0 : random_mask();
    stamp->tube_path = tube_path;
}

static void reset_brush_state(WatercolorState *state) {
    state->stamp_count = 0;
    state->secondary_count = 0;
    state->gesture_active = 0;
    state->unlock_special = 0;
    state->unlock_countdown = 30;
    state->unlock_gate = 1.0f;
    state->pending_affordance_reset = 0;
    state->current_mask_index = 0;
    state->move_scale = 1.0f;
}

static void create_unlock_snapshot(WatercolorState *state) {
    if (!state->unlock_special || state->stamp_count == 0
            || state->secondary_count != 0) {
        return;
    }
    size_t n = state->stamp_count;
    size_t indices[4];
    if (n >= 4U) {
        indices[0] = 0;
        indices[1] = n - 1U;
        indices[2] = n - 2U;
        indices[3] = n - 3U;
    } else if (n == 3U) {
        indices[0] = 0;
        indices[1] = 2;
        indices[2] = 1;
        indices[3] = 0;
    } else if (n == 2U) {
        indices[0] = 0;
        indices[1] = 1;
        indices[2] = 0;
        indices[3] = 1;
    } else {
        indices[0] = indices[1] = indices[2] = indices[3] = 0;
    }
    for (int i = 0; i < 4; ++i) {
        state->secondary_stamps[i] = state->stamps[indices[i]];
    }
    state->secondary_count = 4;
}

static void update_stamps(WatercolorState *state) {
    if (state->pending_affordance_reset) {
        reset_brush_state(state);
    }
    /* The stock cc8 stroke scalar recovers by 0.02 per 60 Hz update and is
     * reduced by 0.025 for every accepted MOVE stamp. */
    if (state->move_scale < 1.0f) state->move_scale += 0.02f;
    if (state->unlock_special) {
        if (state->unlock_countdown <= 0) state->unlock_gate -= 0.06f;
        state->unlock_countdown--;
        create_unlock_snapshot(state);
    }
    for (int i = 0; i < state->secondary_count; ++i) {
        state->secondary_stamps[i].size *= 1.1f;
        state->secondary_stamps[i].alpha = 0.5f;
    }
    size_t output = 0;
    for (size_t i = 0; i < state->stamp_count; ++i) {
        Stamp stamp = state->stamps[i];
        if (stamp.size < stamp.initial_size * 2.3f) {
            stamp.size *= 1.075f;
        } else if (stamp.size >= stamp.initial_size * 2.6f) {
            stamp.size *= stamp.size >= stamp.initial_size * 2.8f
                    ? 1.0045f : 1.005f;
        } else {
            stamp.size *= 1.025f;
        }
        if (stamp.size > stamp.initial_size * 2.8f) {
            stamp.alpha += kStampAlphaStep;
        }
        /* Samsung advances every event except the newest twenty once more. */
        if (state->stamp_count > 20U && i < state->stamp_count - 20U) {
            stamp.alpha += kStampAlphaStep;
        }
        if (stamp.alpha < kStampAlphaLimit) {
            state->stamps[output++] = stamp;
        }
    }
    state->stamp_count = output;
}

static void bind_texture_location(GLint location, GLuint texture, int unit) {
    if (location < 0) return;
    glActiveTexture((GLenum)(GL_TEXTURE0 + unit));
    glBindTexture(GL_TEXTURE_2D, texture);
    glUniform1i(location, unit);
}

static void draw_full_quad(GLint position, GLint uv) {
    if (position < 0 || uv < 0) return;
    glEnableVertexAttribArray((GLuint)position);
    glVertexAttribPointer((GLuint)position, 2, GL_FLOAT, GL_FALSE,
            4 * (GLsizei)sizeof(GLfloat), kFullQuad);
    glEnableVertexAttribArray((GLuint)uv);
    glVertexAttribPointer((GLuint)uv, 2, GL_FLOAT, GL_FALSE,
            4 * (GLsizei)sizeof(GLfloat), kFullQuad + 2);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray((GLuint)position);
    glDisableVertexAttribArray((GLuint)uv);
}

static void clear_radial_target(WatercolorState *state) {
    glBindFramebuffer(GL_FRAMEBUFFER, state->radial_fbo);
    glViewport(0, 0, state->radial_width, state->radial_height);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glClearColor(0.5f, 0.5f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
}

static void render_radial(WatercolorState *state) {
    clear_radial_target(state);

    if (!state->texture_assets_ready
            || (state->stamp_count == 0 && state->secondary_count == 0)) return;

    glUseProgram(state->brush_program);
    const BrushProgramLocations *locations = &state->brush_locations;
    GLint local = locations->a_local;
    if (local < 0) return;
    glEnableVertexAttribArray((GLuint)local);
    glVertexAttribPointer((GLuint)local, 2, GL_FLOAT, GL_FALSE, 0, kBrushQuad);
    glUniform2f(locations->u_screen,
            (float)state->width, (float)state->height);
    float minimum = (float)(state->width < state->height ? state->width : state->height);
    glUniform2f(locations->u_screen_ratio,
            minimum / (float)state->width, minimum / (float)state->height);
    bind_texture_location(locations->u_tube, state->tube_texture, 1);

    /* Stock radial draw composites every mask into the low-resolution field.
     * RGB uses source alpha while alpha itself accumulates as a union.
     * Disabling this blend would let transparent texels in a newer quad erase
     * the older path and collapse WaterColor into a single blurred spot. */
    glEnable(GL_BLEND);
    glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
            GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    if (state->secondary_count > 0) {
        bind_texture_location(locations->u_mask,
                state->mask_textures[state->current_mask_index], 0);
        for (int i = 0; i < state->secondary_count; ++i) {
            const Stamp *stamp = &state->secondary_stamps[i];
            glUniform2f(locations->u_center, stamp->x, stamp->y);
            float integer_size = (float)(int)stamp->size;
            glUniform2f(locations->u_size,
                    integer_size, integer_size);
            glUniform1f(locations->u_time_step, 0.8f);
            glUniform1f(locations->u_alpha, stamp->alpha);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }
    }
    for (size_t i = 0; i < state->stamp_count; ++i) {
        const Stamp *stamp = &state->stamps[i];
        state->current_mask_index = stamp->mask_index;
        bind_texture_location(locations->u_mask,
                state->mask_textures[stamp->mask_index], 0);
        glUniform2f(locations->u_center, stamp->x, stamp->y);
        float integer_size = (float)(int)stamp->size;
        glUniform2f(locations->u_size,
                integer_size, integer_size);
        /* Stock compares currentSize (+0x08) with the immutable size1
         * baseline (+0x04), not with the previous frame's size. */
        float time_step = (stamp->size - stamp->baseline_size) * 0.01f;
        if (time_step < 0.1f) time_step = 0.1f;
        if (time_step > 1.0f) time_step = 1.0f;
        if (state->unlock_special) time_step = 0.9f;
        glUniform1f(locations->u_time_step, time_step);
        glUniform1f(locations->u_alpha, stamp->alpha);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }
    glDisableVertexAttribArray((GLuint)local);
}

static void render_advection_pass(WatercolorState *state,
        int read_index, int write_index) {
    glBindFramebuffer(GL_FRAMEBUFFER, state->density_fbos[write_index]);
    glViewport(0, 0, state->density_width, state->density_height);
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    glUseProgram(state->advect_program);
    const AdvectProgramLocations *locations = &state->advect_locations;
    bind_texture_location(locations->u_density,
            state->density_textures[read_index], 0);
    bind_texture_location(locations->u_velocity, state->noise_texture, 1);
    bind_texture_location(locations->u_radial, state->radial_texture, 2);
    bind_texture_location(locations->u_original, state->background_texture, 3);
    glUniform1f(locations->u_noise_scalar, kNoiseVectorScalar);
    glUniform1f(locations->u_radial_scalar, kRadialVectorScalar);
    draw_full_quad(locations->a_position, locations->a_uv);
    state->density_read_index = write_index;
}

static void seed_density(WatercolorState *state) {
    clear_radial_target(state);
    for (int i = 0; i < 2; ++i) {
        glBindFramebuffer(GL_FRAMEBUFFER, state->density_fbos[i]);
        glViewport(0, 0, state->density_width, state->density_height);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_BLEND);
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
    /* The legacy driver rendered twice into one feedback texture. Stable
     * builds preserve the intended recurrence with defined ping-pong. The
     * opt-in stock topology exists only for controlled A/B captures. */
#if LLE_WATERCOLOR_STOCK_FEEDBACK
    render_advection_pass(state, 0, 0);
    render_advection_pass(state, 0, 0);
#else
    render_advection_pass(state, 0, 1);
    render_advection_pass(state, 1, 0);
#endif
    state->density_read_index = 0;
    state->density_seeded = 1;
}

static void render_advection(WatercolorState *state) {
#if LLE_WATERCOLOR_STOCK_FEEDBACK
    render_advection_pass(state,
            state->density_read_index, state->density_read_index);
#else
    int write_index = 1 - state->density_read_index;
    render_advection_pass(state, state->density_read_index, write_index);
#endif
}

static void render_mix(WatercolorState *state) {
    clear_default_framebuffer(state);
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    glUseProgram(state->mix_program);
    const MixProgramLocations *locations = &state->mix_locations;
    bind_texture_location(locations->u_density,
            state->density_textures[state->density_read_index], 0);
    bind_texture_location(locations->u_alpha, state->radial_texture, 1);
    glUniform1f(locations->u_saturation, kSaturation);
    glUniform1f(locations->u_brightness, kBrightness);
    glUniform1f(locations->u_red_saturation, kRedSaturation);
    glUniform1f(locations->u_green_saturation, kGreenSaturation);
    glUniform1f(locations->u_blue_saturation, kBlueSaturation);
    draw_full_quad(locations->a_position, locations->a_uv);
}

static GLuint upload_argb_texture(JNIEnv *env, jintArray pixels,
        int width, int height) {
    if (pixels == NULL || width <= 0 || height <= 0) return 0;
    jsize count = (*env)->GetArrayLength(env, pixels);
    int64_t required = (int64_t)width * (int64_t)height;
    if (required <= 0 || required > count) return 0;
    jint *argb = (*env)->GetIntArrayElements(env, pixels, NULL);
    if (argb == NULL) return 0;
    uint8_t *rgba = (uint8_t *)malloc((size_t)required * 4U);
    if (rgba == NULL) {
        (*env)->ReleaseIntArrayElements(env, pixels, argb, JNI_ABORT);
        return 0;
    }
    for (int64_t i = 0; i < required; ++i) {
        uint32_t color = (uint32_t)argb[i];
        rgba[i * 4 + 0] = (uint8_t)((color >> 16) & 0xffU);
        rgba[i * 4 + 1] = (uint8_t)((color >> 8) & 0xffU);
        rgba[i * 4 + 2] = (uint8_t)(color & 0xffU);
        rgba[i * 4 + 3] = (uint8_t)((color >> 24) & 0xffU);
    }
    (*env)->ReleaseIntArrayElements(env, pixels, argb, JNI_ABORT);

    GLuint texture = 0;
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    /* SPTextureManager defaults managed Mask/Tube/bg textures to mirrored
     * repeat. Generated Noise and FBO textures remain clamp-to-edge. */
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_MIRRORED_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_MIRRORED_REPEAT);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    free(rgba);
    return texture;
}

/*
 * The stock scene does not sample watercolor_noise.jpg as a colour texture.
 * It turns the grayscale image into a two-component finite-difference field,
 * with a one-pixel neutral border, and uploads that generated field as RGB.
 */
static GLuint create_noise_gradient_texture(const uint32_t *argb,
        int width, int height, int screen_width, int screen_height) {
    if (argb == NULL || width <= 0 || height <= 0) return 0;
    int64_t source_count = (int64_t)width * (int64_t)height;
    int gradient_width = width + 2;
    int gradient_height = height + 2;
    int64_t gradient_count = (int64_t)gradient_width * (int64_t)gradient_height;
    if (source_count <= 0 || gradient_count <= 0
            || (uint64_t)gradient_count > (uint64_t)SIZE_MAX / 3U
            || (uint64_t)gradient_count > (uint64_t)SIZE_MAX / sizeof(float)) {
        return 0;
    }

    float *height_map = (float *)calloc((size_t)gradient_count, sizeof(float));
    uint8_t *rgb = (uint8_t *)malloc((size_t)gradient_count * 3U);
    if (height_map == NULL || rgb == NULL) {
        free(height_map);
        free(rgb);
        return 0;
    }

    for (int y = 0; y < height; ++y) {
        int source_y = height - 1 - y;
        for (int x = 0; x < width; ++x) {
            uint32_t color = argb[(int64_t)source_y * width + x];
            float luminance = (float)(((color >> 16) & 0xffU)
                    + ((color >> 8) & 0xffU) + (color & 0xffU))
                    * (1.0f / 765.0f);
            height_map[(int64_t)y * gradient_width + x] = luminance;
        }
    }
    /* A zero gradient encodes as the neutral vector (127,127). */
    for (int64_t i = 0; i < gradient_count; ++i) {
        rgb[i * 3 + 0] = 127;
        rgb[i * 3 + 1] = 127;
        rgb[i * 3 + 2] = 0;
    }
    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            int64_t center = (int64_t)y * gradient_width + x;
            float gx = height_map[center + gradient_width]
                    - height_map[center - gradient_width];
            float gy = height_map[center - 1] - height_map[center + 1];
            if (screen_width < screen_height) {
                gy *= 0.5625f;
            } else if (screen_height < screen_width) {
                gx *= 0.5625f;
            }
            float encoded_x = (gx + 0.5f) * 255.0f;
            float encoded_y = (gy + 0.5f) * 255.0f;
            /* ARM32 uses VCVT followed by STRB: truncate, then retain the
             * low byte rather than saturating values above 255. */
            rgb[center * 3 + 0] = encoded_x > 0.0f
                    ? (uint8_t)(int32_t)encoded_x : 0;
            rgb[center * 3 + 1] = encoded_y > 0.0f
                    ? (uint8_t)(int32_t)encoded_y : 0;
        }
    }
    free(height_map);

    GLuint texture = 0;
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, gradient_width, gradient_height, 0,
            GL_RGB, GL_UNSIGNED_BYTE, rgb);
    free(rgb);
    LOGI("generated stock noise velocity field source=%dx%d output=%dx%d",
            width, height, gradient_width, gradient_height);
    return texture;
}

static GLuint upload_noise_gradient_texture(JNIEnv *env, jintArray pixels,
        int width, int height, WatercolorState *state) {
    if (pixels == NULL || state == NULL || width <= 0 || height <= 0) return 0;
    jsize count = (*env)->GetArrayLength(env, pixels);
    int64_t required = (int64_t)width * (int64_t)height;
    if (required <= 0 || required > count
            || (uint64_t)required > (uint64_t)SIZE_MAX / sizeof(uint32_t)) {
        return 0;
    }
    jint *source = (*env)->GetIntArrayElements(env, pixels, NULL);
    if (source == NULL) return 0;
    uint32_t *copy = (uint32_t *)malloc((size_t)required * sizeof(uint32_t));
    if (copy == NULL) {
        (*env)->ReleaseIntArrayElements(env, pixels, source, JNI_ABORT);
        return 0;
    }
    memcpy(copy, source, (size_t)required * sizeof(uint32_t));
    (*env)->ReleaseIntArrayElements(env, pixels, source, JNI_ABORT);
    free(state->noise_source_argb);
    state->noise_source_argb = copy;
    state->noise_source_width = width;
    state->noise_source_height = height;
    return create_noise_gradient_texture(copy, width, height,
            state->width, state->height);
}

static int all_asset_textures_ready(const WatercolorState *state) {
    return state->mask_textures[0] != 0 && state->mask_textures[1] != 0
            && state->mask_textures[2] != 0 && state->tube_texture != 0
            && state->noise_texture != 0;
}

JNIEXPORT jobjectArray JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_loadEffect(
        JNIEnv *env, jobject object, jstring path) {
    (void)path;
    WatercolorState *old = get_state(env, object);
    if (old != NULL) {
        release_gl(old);
        free(old->stamps);
        free(old->noise_source_argb);
        free(old);
    }
    WatercolorState *state = (WatercolorState *)calloc(1, sizeof(WatercolorState));
    if (state == NULL) {
        set_state(env, object, NULL);
        return NULL;
    }
    state->clear_requested = 1;
    reset_brush_state(state);
    srand((unsigned int)time(NULL));
    set_state(env, object, state);

    static const char *names[] = {
            "watercolor_mask1",
            "watercolor_mask2",
            "watercolor_mask3",
            "watercolor_noise",
            "waterbrush_tube",
    };
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    if (string_class == NULL) return NULL;
    jobjectArray result = (*env)->NewObjectArray(env, (jsize)ARRAY_SIZE(names),
            string_class, NULL);
    for (jsize i = 0; i < (jsize)ARRAY_SIZE(names) && result != NULL; ++i) {
        jstring name = (*env)->NewStringUTF(env, names[i]);
        (*env)->SetObjectArrayElement(env, result, i, name);
        (*env)->DeleteLocalRef(env, name);
    }
    (*env)->DeleteLocalRef(env, string_class);
    LOGI("loadEffect ARM64 bridge ready");
    return result;
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_loadTexture(
        JNIEnv *env, jobject object, jstring name, jintArray pixels,
        jint width, jint height) {
    WatercolorState *state = get_state(env, object);
    if (state == NULL || name == NULL) return;
    const char *texture_name = (*env)->GetStringUTFChars(env, name, NULL);
    if (texture_name == NULL) return;
    int is_noise = strcmp(texture_name, "watercolor_noise") == 0
            || strcmp(texture_name, "Noise") == 0;
    GLuint texture = is_noise
            ? upload_noise_gradient_texture(env, pixels, width, height, state)
            : upload_argb_texture(env, pixels, width, height);
    if (texture == 0) {
        LOGE("texture upload failed name=%s size=%dx%d", texture_name, width, height);
        (*env)->ReleaseStringUTFChars(env, name, texture_name);
        return;
    }

    GLuint *slot = NULL;
    if (strcmp(texture_name, "watercolor_mask1") == 0 || strcmp(texture_name, "Mask1") == 0) {
        slot = &state->mask_textures[0];
    } else if (strcmp(texture_name, "watercolor_mask2") == 0 || strcmp(texture_name, "Mask2") == 0) {
        slot = &state->mask_textures[1];
    } else if (strcmp(texture_name, "watercolor_mask3") == 0 || strcmp(texture_name, "Mask3") == 0) {
        slot = &state->mask_textures[2];
    } else if (strcmp(texture_name, "watercolor_noise") == 0 || strcmp(texture_name, "Noise") == 0) {
        slot = &state->noise_texture;
    } else if (strcmp(texture_name, "waterbrush_tube") == 0 || strcmp(texture_name, "Tube") == 0) {
        slot = &state->tube_texture;
    } else if (strcmp(texture_name, "bg") == 0) {
        slot = &state->background_texture;
        state->background_ready = 1;
        state->density_seeded = 0;
    }
    if (slot != NULL) {
        delete_texture(slot);
        *slot = texture;
    } else {
        glDeleteTextures(1, &texture);
    }
    state->texture_assets_ready = all_asset_textures_ready(state);
    LOGI("texture loaded name=%s size=%dx%d assetsReady=%d bgReady=%d",
            texture_name, width, height, state->texture_assets_ready,
            state->background_ready);
    (*env)->ReleaseStringUTFChars(env, name, texture_name);
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_init(
        JNIEnv *env, jobject object, jint width, jint height, jboolean force) {
    (void)force;
    WatercolorState *state = get_state(env, object);
    if (state != NULL) initialize_gl(state, width, height);
}

JNIEXPORT jboolean JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_draw(
        JNIEnv *env, jobject object) {
    WatercolorState *state = get_state(env, object);
    if (state == NULL || !state->initialized) return JNI_FALSE;
    if (g_paused || state->paused) {
        clear_default_framebuffer(state);
        return JNI_FALSE;
    }
    if (state->clear_requested) {
        state->clear_requested = 0;
        clear_default_framebuffer(state);
    }
    if (!state->background_ready || !state->texture_assets_ready) {
        clear_default_framebuffer(state);
        return JNI_FALSE;
    }
    if (!state->density_seeded) {
        seed_density(state);
    }
    update_stamps(state);
    int has_stamps = state->stamp_count > 0 || state->secondary_count > 0;
    if (has_stamps) {
        render_radial(state);
        render_advection(state);
    }
    render_mix(state);
    state->frame_number++;
    return has_stamps || state->gesture_active || state->unlock_special
            ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_onTouch(
        JNIEnv *env, jobject object, jint x, jint y, jint action) {
    WatercolorState *state = get_state(env, object);
    if (state == NULL || state->width <= 0 || state->height <= 0) return;
    if (state->unlock_special) {
        if (state->unlock_gate <= 0.0f && action == 0) {
            /* The first DOWN after the stock unlock hold/fade only resets the
             * special state; it is deliberately not turned into a stamp. */
            reset_brush_state(state);
        }
        return;
    }
    float fx = (float)x;
    /* Stock WaterColor stores brush coordinates with a bottom-left origin. */
    float fy = (float)state->height - (float)y;
    float base = base_brush_size(state);
    if (action == 0) {
        state->gesture_active = 1;
        state->last_x = fx;
        state->last_y = fy;
        add_stamp(state, fx, fy, base, 0);
    } else if (action == 1) {
        state->gesture_active = 0;
        add_stamp(state, fx, fy, base * state->move_scale, 0);
    } else if (action == 2) {
        if (!state->gesture_active) return;
        float dx = fx - state->last_x;
        float dy = fy - state->last_y;
        float distance = sqrtf(dx * dx + dy * dy);
        float threshold = (float)state->width * state->move_scale
                * kDragThresholdWidth;
        if (distance < threshold) return;
        float spacing = (float)state->width * kDragInterpolationWidth;
        int count = spacing > 0.0f ? (int)ceilf(distance / spacing) : 2;
        if (count < 2) count = 2;
        if (count > 101) count = 101;
        /* Samsung deliberately excludes the new endpoint. It becomes the
         * next lastPoint after the interpolated stamps have been queued. */
        for (int i = 1; i < count; ++i) {
            float t = (float)i / (float)count;
            if (state->move_scale > kMoveScaleMin) {
                state->move_scale -= kMoveScaleStep;
            }
            add_stamp(state, state->last_x + dx * t, state->last_y + dy * t,
                    random_move_size(base, state->move_scale), 1);
        }
        state->last_x = fx;
        state->last_y = fy;
    } else {
        state->gesture_active = 0;
    }
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_showUnlock(
        JNIEnv *env, jobject object) {
    WatercolorState *state = get_state(env, object);
    if (state == NULL) return;
    state->unlock_special = 1;
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_showAffordance(
        JNIEnv *env, jobject object, jint x, jint y) {
    WatercolorState *state = get_state(env, object);
    (void)x;
    (void)y;
    if (state != NULL) state->pending_affordance_reset = 1;
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_clear(
        JNIEnv *env, jobject object) {
    WatercolorState *state = get_state(env, object);
    if (state == NULL) return;
    reset_brush_state(state);
    state->clear_requested = 1;
    state->density_seeded = 0;
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_destroy(
        JNIEnv *env, jobject object) {
    WatercolorState *state = get_state(env, object);
    if (state == NULL) return;
    release_gl(state);
    free(state->stamps);
    free(state->noise_source_argb);
    free(state);
    set_state(env, object, NULL);
    LOGI("destroyed");
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_setParameters(
        JNIEnv *env, jobject object, jintArray numbers, jfloatArray values) {
    (void)env;
    (void)object;
    (void)numbers;
    (void)values;
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_loadModel(
        JNIEnv *env, jobject object, jstring name, jbyteArray bytes) {
    (void)env;
    (void)object;
    (void)name;
    (void)bytes;
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_pauseAnimation(
        JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    g_paused = 1;
}

JNIEXPORT void JNICALL
Java_com_samsung_android_visualeffect_lock_common_Native_resumeAnimation(
        JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    g_paused = 0;
}

/*
 * App-owned JNI aliases.  Keep the historical Samsung ABI above while ARM32 is
 * frozen, but let the active ARM64 application call the reconstructed engine
 * without loading any class from the vendor visual-effect DEX.
 */
JNIEXPORT jobjectArray JNICALL
Java_com_codex_lle_WatercolorArm64Native_loadEffect(
        JNIEnv *env, jobject object, jstring path) {
    return Java_com_samsung_android_visualeffect_lock_common_Native_loadEffect(
            env, object, path);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_loadTexture(
        JNIEnv *env, jobject object, jstring name, jintArray pixels,
        jint width, jint height) {
    Java_com_samsung_android_visualeffect_lock_common_Native_loadTexture(
            env, object, name, pixels, width, height);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_init(
        JNIEnv *env, jobject object, jint width, jint height, jboolean force) {
    Java_com_samsung_android_visualeffect_lock_common_Native_init(
            env, object, width, height, force);
}

JNIEXPORT jboolean JNICALL
Java_com_codex_lle_WatercolorArm64Native_draw(JNIEnv *env, jobject object) {
    return Java_com_samsung_android_visualeffect_lock_common_Native_draw(env, object);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_onTouch(
        JNIEnv *env, jobject object, jint x, jint y, jint action) {
    Java_com_samsung_android_visualeffect_lock_common_Native_onTouch(
            env, object, x, y, action);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_showUnlock(
        JNIEnv *env, jobject object) {
    Java_com_samsung_android_visualeffect_lock_common_Native_showUnlock(env, object);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_showAffordance(
        JNIEnv *env, jobject object, jint x, jint y) {
    Java_com_samsung_android_visualeffect_lock_common_Native_showAffordance(
            env, object, x, y);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_clear(JNIEnv *env, jobject object) {
    Java_com_samsung_android_visualeffect_lock_common_Native_clear(env, object);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_destroy(JNIEnv *env, jobject object) {
    Java_com_samsung_android_visualeffect_lock_common_Native_destroy(env, object);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_setParameters(
        JNIEnv *env, jobject object, jintArray numbers, jfloatArray values) {
    Java_com_samsung_android_visualeffect_lock_common_Native_setParameters(
            env, object, numbers, values);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_loadModel(
        JNIEnv *env, jobject object, jstring name, jbyteArray bytes) {
    Java_com_samsung_android_visualeffect_lock_common_Native_loadModel(
            env, object, name, bytes);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_pauseAnimation(
        JNIEnv *env, jclass clazz) {
    Java_com_samsung_android_visualeffect_lock_common_Native_pauseAnimation(
            env, clazz);
}

JNIEXPORT void JNICALL
Java_com_codex_lle_WatercolorArm64Native_resumeAnimation(
        JNIEnv *env, jclass clazz) {
    Java_com_samsung_android_visualeffect_lock_common_Native_resumeAnimation(
            env, clazz);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    return JNI_VERSION_1_6;
}
