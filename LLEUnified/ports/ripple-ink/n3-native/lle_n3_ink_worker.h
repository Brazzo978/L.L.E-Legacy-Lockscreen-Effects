#ifndef LLE_N3_INK_WORKER_H
#define LLE_N3_INK_WORKER_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * A one-frame delayed velocity producer for the Note 3 ENB4 Ripple Ink
 * pipeline.  It deliberately owns no GLES object: Java/GLES uploads the
 * returned RGBA8 field, advects density, and performs AddInk separately.
 */
typedef struct LleN3InkWorker LleN3InkWorker;

typedef struct LleN3InkWorkerStep {
  /* ENB4's worker mode: -1 state-1 press, 0 slow, 1 medium, 2 segment. */
  int mode;

  /* MotionEvent coordinates: screen pixels, top-left origin. */
  float current_x;
  float current_y_top;
  float previous_x;
  float previous_y_top;

  /* Per-profile fields written by onDraw before Fluid::Update's next tick. */
  float velocity_dissipation;
  float divergence_radius;
  float divergence_strength;
  bool force_projection;
} LleN3InkWorkerStep;

/* velocity_width/height are the recovered screen/12 grid dimensions. */
LleN3InkWorker *lle_n3_ink_worker_create(
    int velocity_width,
    int velocity_height,
    int screen_width,
    int screen_height);

/* Joins a pending ENB4 worker before zeroing its velocity surfaces. */
void lle_n3_ink_worker_reset(LleN3InkWorker *worker);
void lle_n3_ink_worker_destroy(LleN3InkWorker *worker);

/* RGBA8 size required for the completed N-1 velocity surface. */
size_t lle_n3_ink_worker_rgba_size(const LleN3InkWorker *worker);

/*
 * ENB4 chronology, intentionally one logical tick delayed:
 *
 *   join completed N-1 worker -> encode/upload N-1 -> launch N worker.
 *
 * The caller must use output_rgba for this tick's density advection before
 * sending the next step.  `output_rgba` may be NULL to only advance state.
 */
bool lle_n3_ink_worker_step(
    LleN3InkWorker *worker,
    const LleN3InkWorkerStep *step,
    uint8_t *output_rgba,
    size_t output_rgba_size);

#ifdef LLE_N3_INK_WORKER_TEST_API
/* POSIX/Bionic lrand48 default state: 0x1234abcd330e. Test-only probe. */
int32_t lle_n3_ink_worker_lrand48_for_test(void);
void lle_n3_ink_worker_reset_host_lrand48_for_test(void);
float lle_n3_ink_worker_margin_for_test(const LleN3InkWorker *worker);
bool lle_n3_ink_worker_last_direct_segment_admitted_for_test(
    const LleN3InkWorker *worker);
#endif

#ifdef __cplusplus
}
#endif

#endif
