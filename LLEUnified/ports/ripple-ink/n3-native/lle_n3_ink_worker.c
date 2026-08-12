#include "lle_n3_ink_worker.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#if defined(LLE_N3_INK_HOST)
#define LLE_N3_INK_USE_HOST_SYNCHRONOUS_THREAD 1
#else
#include <pthread.h>
#endif

/*
 * Recovered ENB4 constants.  Keep every comparison strict: `>`/`<` at the
 * 60 px worker gate and the 25 px local velocity override are visible at the
 * input boundary.
 */
#define LLE_N3_INK_MARGIN_PX 60.0f
#define LLE_N3_INK_LOCAL_RADIUS_SQUARED 625.0f
#define LLE_N3_INK_LOCAL_MODE_BIAS 1.5f
#define LLE_N3_INK_BACKTRACE_STEP 0.25f
#define LLE_N3_INK_DIVERGENCE_SCALE 0.2f
#define LLE_N3_INK_JACOBI_ALPHA -6.25f
#define LLE_N3_INK_JACOBI_INVERSE_BETA 0.25f
#define LLE_N3_INK_JACOBI_ITERATIONS 10
#define LLE_N3_INK_SEGMENT_RADIUS 30.0f
#define LLE_N3_INK_VELOCITY_MIN -127.0f
#define LLE_N3_INK_VELOCITY_MAX 127.0f
#define LLE_N3_INK_VELOCITY_BIAS 127.0f
#define LLE_N3_INK_LRAND48_DEFAULT UINT64_C(0x1234abcd330e)
#define LLE_N3_INK_LRAND48_A UINT64_C(0x0005deece66d)
#define LLE_N3_INK_LRAND48_C UINT64_C(0x00000000000b)
#define LLE_N3_INK_LRAND48_MASK UINT64_C(0x0000ffffffffffff)

struct LleN3InkWorker {
  int velocity_width;
  int velocity_height;
  int screen_width;
  int screen_height;
  size_t cell_count;
  float *flow_x;
  float *flow_y;
  float *scratch_x;
  float *scratch_y;
  float *pressure_a;
  float *pressure_b;
  float *divergence;
  /* ENB4's persisted direct-capsule gate (+0x12c), initially 60px. */
  float margin_state;
  LleN3InkWorkerStep pending_step;
  bool worker_pending;
#ifdef LLE_N3_INK_WORKER_TEST_API
  bool last_direct_segment_admitted;
#endif
#if !defined(LLE_N3_INK_USE_HOST_SYNCHRONOUS_THREAD)
  pthread_t worker_thread;
#endif
};

static float n3_finite_or_zero(float value) {
  return isfinite(value) ? value : 0.0f;
}

static float n3_clamp(float value, float minimum, float maximum) {
  if (value < minimum) return minimum;
  if (value > maximum) return maximum;
  return value;
}

static size_t n3_index(const LleN3InkWorker *worker, int x, int y) {
  return (size_t)y * (size_t)worker->velocity_width + (size_t)x;
}

static float n3_sample_cell(const LleN3InkWorker *worker,
                            const float *field,
                            int x,
                            int y) {
  if (x < 0) x = 0;
  if (x >= worker->velocity_width) x = worker->velocity_width - 1;
  if (y < 0) y = 0;
  if (y >= worker->velocity_height) y = worker->velocity_height - 1;
  return n3_finite_or_zero(field[n3_index(worker, x, y)]);
}

static float n3_sample_bilinear(const LleN3InkWorker *worker,
                                const float *field,
                                float u,
                                float v) {
  const float sample_x = n3_clamp(n3_finite_or_zero(u), 0.0f, 1.0f)
      * (float)(worker->velocity_width - 1);
  const float sample_y = n3_clamp(n3_finite_or_zero(v), 0.0f, 1.0f)
      * (float)(worker->velocity_height - 1);
  const int x0 = (int)sample_x;
  const int y0 = (int)sample_y;
  const int x1 = x0 + 1 < worker->velocity_width ? x0 + 1 : x0;
  const int y1 = y0 + 1 < worker->velocity_height ? y0 + 1 : y0;
  const float fx = sample_x - (float)x0;
  const float fy = sample_y - (float)y0;
  const float southwest = n3_sample_cell(worker, field, x0, y0);
  const float southeast = n3_sample_cell(worker, field, x1, y0);
  const float northwest = n3_sample_cell(worker, field, x0, y1);
  const float northeast = n3_sample_cell(worker, field, x1, y1);
  const float lower = southwest + (southeast - southwest) * fx;
  const float upper = northwest + (northeast - northwest) * fx;
  return n3_finite_or_zero(lower + (upper - lower) * fy);
}

static bool n3_within_margin(const LleN3InkWorker *worker,
                             float center_x,
                             float center_y_bottom,
                             float margin) {
  return center_x > margin && center_x < (float)worker->screen_width - margin
      && center_y_bottom > margin
      && center_y_bottom < (float)worker->screen_height - margin;
}

static void n3_swap_velocity(LleN3InkWorker *worker) {
  float *temporary = worker->flow_x;
  worker->flow_x = worker->scratch_x;
  worker->scratch_x = temporary;
  temporary = worker->flow_y;
  worker->flow_y = worker->scratch_y;
  worker->scratch_y = temporary;
}

/*
 * ENB4 imports lrand48 and no srand48/seed48.  On Android deliberately call
 * the Bionic process-global implementation, matching the library ABI and its
 * default 0x1234abcd330e state if nothing else has consumed it.  Host tests
 * emulate that process-global stream exactly.
 */
#if defined(LLE_N3_INK_USE_HOST_SYNCHRONOUS_THREAD)
static uint64_t g_n3_host_lrand48_state = LLE_N3_INK_LRAND48_DEFAULT;
static int32_t n3_lrand48(void) {
  g_n3_host_lrand48_state = (g_n3_host_lrand48_state * LLE_N3_INK_LRAND48_A
      + LLE_N3_INK_LRAND48_C) & LLE_N3_INK_LRAND48_MASK;
  return (int32_t)(g_n3_host_lrand48_state >> 17);
}
#else
static int32_t n3_lrand48(void) {
  return (int32_t)lrand48();
}
#endif

static float n3_jitter_from_lrand48(void) {
  const uint32_t random31 = (uint32_t)n3_lrand48() & UINT32_C(0x7fffffff);
  return (0.5f - (float)random31 * (1.0f / 2147483648.0f)) * 10.0f;
}

/* ENB4's direct mode-2 velocity capsule, before self-advection. */
static bool n3_add_segment_velocity(LleN3InkWorker *worker,
                                    const LleN3InkWorkerStep *step,
                                    float current_y_bottom,
                                    float previous_y_bottom) {
  const float center_x = step->current_x;
  const float segment_x = center_x - step->previous_x;
  const float segment_y = current_y_bottom - previous_y_bottom;
  const float length = sqrtf(segment_x * segment_x + segment_y * segment_y);
  if (step->mode != 2 || !n3_within_margin(worker, center_x, current_y_bottom,
                                             worker->margin_state)
      || !(length > 0.0f) || !isfinite(length)) {
    return false;
  }
  const float normal_x = segment_x / length;
  const float normal_y = segment_y / length;
  for (int y = 0; y < worker->velocity_height; ++y) {
    const float cell_y = ((float)y + 0.5f) * (float)worker->screen_height
        / (float)worker->velocity_height;
    for (int x = 0; x < worker->velocity_width; ++x) {
      const size_t index = n3_index(worker, x, y);
      const float cell_x = ((float)x + 0.5f) * (float)worker->screen_width
          / (float)worker->velocity_width;
      const float relative_x = cell_x - step->previous_x;
      const float relative_y = cell_y - previous_y_bottom;
      const float along = normal_x * relative_x + normal_y * relative_y;
      float output_x = n3_finite_or_zero(worker->flow_x[index]);
      float output_y = n3_finite_or_zero(worker->flow_y[index]);
      if (along > 0.0f && along < length) {
        const float projected_x = step->previous_x + along * normal_x;
        const float projected_y = previous_y_bottom + along * normal_y;
        const float radial_x = cell_x - projected_x;
        const float radial_y = cell_y - projected_y;
        const float distance = sqrtf(radial_x * radial_x + radial_y * radial_y);
        if (distance < LLE_N3_INK_SEGMENT_RADIUS) {
          output_x += distance * 0.1f * (radial_x + center_x - projected_x);
          output_y += distance * 0.1f
              * (radial_y + current_y_bottom - projected_y);
        }
      }
      worker->scratch_x[index] = n3_finite_or_zero(output_x);
      worker->scratch_y[index] = n3_finite_or_zero(output_y);
    }
  }
  n3_swap_velocity(worker);
  return true;
}

/* ENB4 FUN_00016dd8 normalized bilerp/self-advection. */
static void n3_self_advect_velocity(LleN3InkWorker *worker,
                                    const LleN3InkWorkerStep *step,
                                    float current_y_bottom,
                                    float previous_y_bottom) {
  const float inverse_width = 1.0f / (float)worker->velocity_width;
  const float inverse_height = 1.0f / (float)worker->velocity_height;
  const float delta_x = step->current_x - step->previous_x;
  const float delta_y = current_y_bottom - previous_y_bottom;
  for (int y = 0; y < worker->velocity_height; ++y) {
    const float cell_center_y = (float)y + 0.5f;
    const float anchor_y = (float)y * (float)worker->screen_height
        / (float)worker->velocity_height;
    for (int x = 0; x < worker->velocity_width; ++x) {
      const size_t index = n3_index(worker, x, y);
      const float source_x = n3_finite_or_zero(worker->flow_x[index]);
      const float source_y = n3_finite_or_zero(worker->flow_y[index]);
      const float u = ((float)x + 0.5f - LLE_N3_INK_BACKTRACE_STEP * source_x)
          * inverse_width;
      const float v = (cell_center_y - LLE_N3_INK_BACKTRACE_STEP * source_y)
          * inverse_height;
      float sampled_x = n3_sample_bilinear(worker, worker->flow_x, u, v);
      float sampled_y = n3_sample_bilinear(worker, worker->flow_y, u, v);
      if (step->mode > 0) {
        const float anchor_x = (float)x * (float)worker->screen_width
            / (float)worker->velocity_width;
        const float distance_x = anchor_x - step->current_x;
        const float distance_y = anchor_y - current_y_bottom;
        if (distance_x * distance_x + distance_y * distance_y
            < LLE_N3_INK_LOCAL_RADIUS_SQUARED) {
          const float multiplier = (float)step->mode - LLE_N3_INK_LOCAL_MODE_BIAS;
          sampled_x += multiplier * delta_x;
          sampled_y += multiplier * delta_y;
        }
      }
      worker->scratch_x[index] = n3_finite_or_zero(
          sampled_x * n3_finite_or_zero(step->velocity_dissipation));
      worker->scratch_y[index] = n3_finite_or_zero(
          sampled_y * n3_finite_or_zero(step->velocity_dissipation));
    }
  }
  n3_swap_velocity(worker);
}

static void n3_project_velocity(LleN3InkWorker *worker,
                                float impulse_x,
                                float impulse_y,
                                float divergence_radius,
                                float divergence_strength) {
  memset(worker->pressure_a, 0, worker->cell_count * sizeof(float));
  memset(worker->pressure_b, 0, worker->cell_count * sizeof(float));
  memset(worker->divergence, 0, worker->cell_count * sizeof(float));
  for (int y = 0; y < worker->velocity_height; ++y) {
    const float screen_y = (float)y * (float)worker->screen_height
        / (float)worker->velocity_height;
    for (int x = 0; x < worker->velocity_width; ++x) {
      const size_t index = n3_index(worker, x, y);
      const float screen_x = (float)x * (float)worker->screen_width
          / (float)worker->velocity_width;
      const float dx = screen_x - impulse_x;
      const float dy = screen_y - impulse_y;
      float divergence = LLE_N3_INK_DIVERGENCE_SCALE * (
          n3_sample_cell(worker, worker->flow_x, x + 1, y)
          - n3_sample_cell(worker, worker->flow_x, x - 1, y)
          + n3_sample_cell(worker, worker->flow_y, x, y + 1)
          - n3_sample_cell(worker, worker->flow_y, x, y - 1));
      if (dx * dx + dy * dy < divergence_radius * divergence_radius) {
        divergence -= divergence_strength;
      }
      worker->divergence[index] = n3_finite_or_zero(divergence);
    }
  }
  float *source = worker->pressure_a;
  float *target = worker->pressure_b;
  for (int iteration = 0; iteration < LLE_N3_INK_JACOBI_ITERATIONS; ++iteration) {
    for (int y = 0; y < worker->velocity_height; ++y) {
      for (int x = 0; x < worker->velocity_width; ++x) {
        const size_t index = n3_index(worker, x, y);
        target[index] = n3_finite_or_zero(LLE_N3_INK_JACOBI_INVERSE_BETA * (
            n3_sample_cell(worker, source, x - 1, y)
            + n3_sample_cell(worker, source, x + 1, y)
            + n3_sample_cell(worker, source, x, y - 1)
            + n3_sample_cell(worker, source, x, y + 1)
            + LLE_N3_INK_JACOBI_ALPHA * worker->divergence[index]));
      }
    }
    float *temporary = source;
    source = target;
    target = temporary;
  }
  for (int y = 0; y < worker->velocity_height; ++y) {
    for (int x = 0; x < worker->velocity_width; ++x) {
      const size_t index = n3_index(worker, x, y);
      worker->flow_x[index] = n3_finite_or_zero(worker->flow_x[index]
          - LLE_N3_INK_DIVERGENCE_SCALE * (
              n3_sample_cell(worker, source, x + 1, y)
              - n3_sample_cell(worker, source, x - 1, y)));
      worker->flow_y[index] = n3_finite_or_zero(worker->flow_y[index]
          - LLE_N3_INK_DIVERGENCE_SCALE * (
              n3_sample_cell(worker, source, x, y + 1)
              - n3_sample_cell(worker, source, x, y - 1)));
    }
  }
}

static void n3_run_worker(LleN3InkWorker *worker) {
  const LleN3InkWorkerStep step = worker->pending_step;
  const float current_y_bottom = (float)worker->screen_height - step.current_y_top;
  const float previous_y_bottom = (float)worker->screen_height - step.previous_y_top;
  const bool direct_segment_admitted = n3_add_segment_velocity(
      worker, &step, current_y_bottom, previous_y_bottom);
#ifdef LLE_N3_INK_WORKER_TEST_API
  worker->last_direct_segment_admitted = direct_segment_admitted;
#else
  (void)direct_segment_admitted;
#endif
  n3_self_advect_velocity(worker, &step, current_y_bottom, previous_y_bottom);
  /* ARM resets this after Perturb, so its fallback affects exactly next mode 2. */
  worker->margin_state = LLE_N3_INK_MARGIN_PX;
  if (step.mode != 2 && !step.force_projection) {
    return;
  }
  float margin = worker->margin_state;
  float divergence_radius = n3_finite_or_zero(step.divergence_radius);
  float divergence_strength = n3_finite_or_zero(step.divergence_strength);
  if (!n3_within_margin(worker, step.current_x, current_y_bottom, margin)) {
    if (step.mode == 0) {
      /* ENB4 0x17b38: mode-0 only falls back to a 12px gate, with this override. */
      margin = 12.0f;
      worker->margin_state = margin;
      divergence_radius = 4.0f;
      divergence_strength = 40.0f;
    } else if (step.mode == 1) {
      /* ENB4 0x17bc8: mode-1 retains its 20/10 profile down to the 10px gate. */
      margin = 10.0f;
      worker->margin_state = margin;
    }
    if (!n3_within_margin(worker, step.current_x, current_y_bottom, margin)) {
      return;
    }
  }
  const float jitter_x = n3_jitter_from_lrand48();
  const float jitter_y = n3_jitter_from_lrand48();
  n3_project_velocity(worker, step.current_x + jitter_x,
                      current_y_bottom + jitter_y,
                      divergence_radius, divergence_strength);
}

#if !defined(LLE_N3_INK_USE_HOST_SYNCHRONOUS_THREAD)
static void *n3_thread_main(void *argument) {
  n3_run_worker((LleN3InkWorker *)argument);
  return NULL;
}
#endif

static bool n3_join_worker(LleN3InkWorker *worker) {
  if (!worker->worker_pending) return true;
#if !defined(LLE_N3_INK_USE_HOST_SYNCHRONOUS_THREAD)
  if (pthread_join(worker->worker_thread, NULL) != 0) return false;
#endif
  worker->worker_pending = false;
  return true;
}

static bool n3_launch_worker(LleN3InkWorker *worker) {
#if defined(LLE_N3_INK_USE_HOST_SYNCHRONOUS_THREAD)
  n3_run_worker(worker);
  worker->worker_pending = true;
  return true;
#else
  if (pthread_create(&worker->worker_thread, NULL, n3_thread_main, worker) != 0) {
    return false;
  }
  worker->worker_pending = true;
  return true;
#endif
}

static uint8_t n3_encode_component(float value) {
  const float clamped = n3_clamp(n3_finite_or_zero(value),
                                 LLE_N3_INK_VELOCITY_MIN,
                                 LLE_N3_INK_VELOCITY_MAX);
  const float biased = clamped + LLE_N3_INK_VELOCITY_BIAS;
  const int whole = (int)biased;
  /* The caller writes this for the high and fractional bytes separately. */
  return (uint8_t)whole;
}

static void n3_encode_velocity(const LleN3InkWorker *worker, uint8_t *output) {
  for (size_t index = 0; index < worker->cell_count; ++index) {
    const float x = n3_clamp(n3_finite_or_zero(worker->flow_x[index]),
                             LLE_N3_INK_VELOCITY_MIN,
                             LLE_N3_INK_VELOCITY_MAX);
    const float y = n3_clamp(n3_finite_or_zero(worker->flow_y[index]),
                             LLE_N3_INK_VELOCITY_MIN,
                             LLE_N3_INK_VELOCITY_MAX);
    const float x_biased = x + LLE_N3_INK_VELOCITY_BIAS;
    const float y_biased = y + LLE_N3_INK_VELOCITY_BIAS;
    const int x_high = (int)x_biased;
    const int y_high = (int)y_biased;
    const size_t output_index = index * 4U;
    output[output_index] = n3_encode_component(x);
    output[output_index + 1U] = (uint8_t)((int)((x_biased - (float)x_high) * 255.0f));
    output[output_index + 2U] = n3_encode_component(y);
    output[output_index + 3U] = (uint8_t)((int)((y_biased - (float)y_high) * 255.0f));
  }
}

LleN3InkWorker *lle_n3_ink_worker_create(int velocity_width,
                                          int velocity_height,
                                          int screen_width,
                                          int screen_height) {
  if (velocity_width < 2 || velocity_height < 2 || screen_width < 1
      || screen_height < 1) {
    return NULL;
  }
  const size_t count = (size_t)velocity_width * (size_t)velocity_height;
  if (count / (size_t)velocity_width != (size_t)velocity_height
      || count > SIZE_MAX / sizeof(float)) {
    return NULL;
  }
  LleN3InkWorker *worker = calloc(1U, sizeof(*worker));
  if (worker == NULL) return NULL;
  worker->velocity_width = velocity_width;
  worker->velocity_height = velocity_height;
  worker->screen_width = screen_width;
  worker->screen_height = screen_height;
  worker->cell_count = count;
  worker->margin_state = LLE_N3_INK_MARGIN_PX;
  worker->flow_x = calloc(count, sizeof(float));
  worker->flow_y = calloc(count, sizeof(float));
  worker->scratch_x = calloc(count, sizeof(float));
  worker->scratch_y = calloc(count, sizeof(float));
  worker->pressure_a = calloc(count, sizeof(float));
  worker->pressure_b = calloc(count, sizeof(float));
  worker->divergence = calloc(count, sizeof(float));
  if (worker->flow_x == NULL || worker->flow_y == NULL || worker->scratch_x == NULL
      || worker->scratch_y == NULL || worker->pressure_a == NULL
      || worker->pressure_b == NULL || worker->divergence == NULL) {
    lle_n3_ink_worker_destroy(worker);
    return NULL;
  }
  return worker;
}

void lle_n3_ink_worker_reset(LleN3InkWorker *worker) {
  if (worker == NULL || !n3_join_worker(worker)) return;
  memset(worker->flow_x, 0, worker->cell_count * sizeof(float));
  memset(worker->flow_y, 0, worker->cell_count * sizeof(float));
  memset(worker->scratch_x, 0, worker->cell_count * sizeof(float));
  memset(worker->scratch_y, 0, worker->cell_count * sizeof(float));
  memset(worker->pressure_a, 0, worker->cell_count * sizeof(float));
  memset(worker->pressure_b, 0, worker->cell_count * sizeof(float));
  memset(worker->divergence, 0, worker->cell_count * sizeof(float));
  memset(&worker->pending_step, 0, sizeof(worker->pending_step));
  worker->margin_state = LLE_N3_INK_MARGIN_PX;
}

void lle_n3_ink_worker_destroy(LleN3InkWorker *worker) {
  if (worker == NULL) return;
  (void)n3_join_worker(worker);
  free(worker->flow_x);
  free(worker->flow_y);
  free(worker->scratch_x);
  free(worker->scratch_y);
  free(worker->pressure_a);
  free(worker->pressure_b);
  free(worker->divergence);
  free(worker);
}

size_t lle_n3_ink_worker_rgba_size(const LleN3InkWorker *worker) {
  return worker == NULL || worker->cell_count > SIZE_MAX / 4U
      ? 0U : worker->cell_count * 4U;
}

bool lle_n3_ink_worker_step(LleN3InkWorker *worker,
                            const LleN3InkWorkerStep *step,
                            uint8_t *output_rgba,
                            size_t output_rgba_size) {
  const size_t required_size = lle_n3_ink_worker_rgba_size(worker);
  if (worker == NULL || step == NULL
      || (output_rgba != NULL && output_rgba_size < required_size)
      || !n3_join_worker(worker)) {
    return false;
  }
  /* Exactly the ENB4 Update boundary: encode the completed N-1 surface first. */
  if (output_rgba != NULL) n3_encode_velocity(worker, output_rgba);
  worker->pending_step = *step;
  return n3_launch_worker(worker);
}

#ifdef LLE_N3_INK_WORKER_TEST_API
int32_t lle_n3_ink_worker_lrand48_for_test(void) {
  return n3_lrand48();
}

void lle_n3_ink_worker_reset_host_lrand48_for_test(void) {
#if defined(LLE_N3_INK_USE_HOST_SYNCHRONOUS_THREAD)
  g_n3_host_lrand48_state = LLE_N3_INK_LRAND48_DEFAULT;
#endif
}

float lle_n3_ink_worker_margin_for_test(const LleN3InkWorker *worker) {
  return worker == NULL ? 0.0f : worker->margin_state;
}

bool lle_n3_ink_worker_last_direct_segment_admitted_for_test(
    const LleN3InkWorker *worker) {
  return worker != NULL && worker->last_direct_segment_admitted;
}
#endif
