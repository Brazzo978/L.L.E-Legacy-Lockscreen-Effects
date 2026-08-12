#include "../lle_n3_ink_worker.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void require(int condition, const char *message) {
  if (!condition) {
    fprintf(stderr, "%s\n", message);
    exit(1);
  }
}

static LleN3InkWorkerStep worker_step(float current_x, float current_y_top,
                                      float previous_x, float previous_y_top) {
  LleN3InkWorkerStep step;
  memset(&step, 0, sizeof(step));
  step.mode = 2;
  step.current_x = current_x;
  step.current_y_top = current_y_top;
  step.previous_x = previous_x;
  step.previous_y_top = previous_y_top;
  step.velocity_dissipation = 0.96f;
  step.divergence_radius = 4.0f;
  step.divergence_strength = 20.0f;
  step.force_projection = true;
  return step;
}

static int any_nonzero(const unsigned char *bytes, size_t count) {
  for (size_t index = 0; index < count; ++index) {
    /* 127 is the exact encoded zero high byte; low bytes are zero. */
    if ((index % 2U == 0U && bytes[index] != 127U) ||
        (index % 2U == 1U && bytes[index] != 0U)) return 1;
  }
  return 0;
}

static int first_random_after_one_worker_step(int mode, float x) {
  LleN3InkWorker *worker = lle_n3_ink_worker_create(20, 20, 240, 240);
  unsigned char bytes[20 * 20 * 4];
  LleN3InkWorkerStep step = worker_step(x, 120.0f, x, 120.0f);
  require(worker != NULL, "margin test worker create failed");
  step.mode = mode;
  step.force_projection = true;
  require(lle_n3_ink_worker_step(worker, &step, bytes, sizeof(bytes)),
          "margin test worker step failed");
  lle_n3_ink_worker_destroy(worker);
  return lle_n3_ink_worker_lrand48_for_test();
}

static void completed_field_at_fallback(int mode, float radius, float strength,
                                        unsigned char *output, size_t size) {
  LleN3InkWorker *worker = lle_n3_ink_worker_create(20, 20, 240, 240);
  LleN3InkWorkerStep step = worker_step(50.0f, 120.0f, 20.0f, 120.0f);
  unsigned char initial[20 * 20 * 4];
  require(worker != NULL, "fallback test worker create failed");
  step.mode = mode;
  step.divergence_radius = radius;
  step.divergence_strength = strength;
  step.force_projection = true;
  require(lle_n3_ink_worker_step(worker, &step, initial, sizeof(initial)),
          "fallback launch failed");
  /* The returned field is N-1; the second call's newly launched N+1 field is not in output. */
  require(lle_n3_ink_worker_step(worker, &step, output, size),
          "fallback join failed");
  lle_n3_ink_worker_destroy(worker);
}

static void check_mode_fallback_to_mode2_transition(int fallback_mode,
                                                     float expected_margin) {
  LleN3InkWorker *worker = lle_n3_ink_worker_create(20, 20, 240, 240);
  unsigned char bytes[20 * 20 * 4];
  LleN3InkWorkerStep fallback = worker_step(50.0f, 120.0f, 50.0f, 120.0f);
  LleN3InkWorkerStep segment = worker_step(50.0f, 120.0f, 20.0f, 120.0f);
  require(worker != NULL, "transition test worker create failed");
  fallback.mode = fallback_mode;
  fallback.force_projection = true;
  require(lle_n3_ink_worker_step(worker, &fallback, bytes, sizeof(bytes)),
          "fallback worker tick failed");
  require(lle_n3_ink_worker_margin_for_test(worker) == expected_margin,
          "fallback did not retain its ENB4 margin state");
  require(lle_n3_ink_worker_step(worker, &segment, bytes, sizeof(bytes)),
          "mode2 transition tick failed");
  require(lle_n3_ink_worker_last_direct_segment_admitted_for_test(worker),
          "mode2 Perturb did not consume previous fallback margin");
  require(lle_n3_ink_worker_margin_for_test(worker) == 60.0f,
          "mode2 worker did not restore 60px main margin");
  lle_n3_ink_worker_destroy(worker);
}

int main(void) {
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  const int expected[] = {851401618, 1804928587, 758783491, 959030623, 684387517};
  for (size_t index = 0; index < sizeof(expected) / sizeof(expected[0]); ++index) {
    require(lle_n3_ink_worker_lrand48_for_test() == expected[index],
            "lrand48 default sequence differs from POSIX 48-bit stream");
  }

  lle_n3_ink_worker_reset_host_lrand48_for_test();
  LleN3InkWorker *worker = lle_n3_ink_worker_create(20, 20, 240, 240);
  require(worker != NULL, "worker create failed");
  const size_t size = lle_n3_ink_worker_rgba_size(worker);
  unsigned char *first = calloc(size, 1U);
  unsigned char *second = calloc(size, 1U);
  unsigned char *third = calloc(size, 1U);
  require(first != NULL && second != NULL && third != NULL, "output allocation failed");

  /* Call N: only launch; returned N-1 remains the zero initial velocity field. */
  LleN3InkWorkerStep valid = worker_step(140.0f, 120.0f, 100.0f, 120.0f);
  require(lle_n3_ink_worker_step(worker, &valid, first, size), "first worker tick failed");
  require(!any_nonzero(first, size), "first tick did not preserve N-1 latency");

  /* Call N+1 joins the prior worker and exposes its field before launching N+1. */
  require(lle_n3_ink_worker_step(worker, &valid, second, size), "second worker tick failed");
  require(any_nonzero(second, size), "joined N-1 result was not emitted");

  /* The strict x == 60 mode-2 margin must consume neither random draw. */
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  LleN3InkWorkerStep edge = worker_step(60.0f, 120.0f, 60.0f, 120.0f);
  require(lle_n3_ink_worker_step(worker, &edge, third, size), "margin worker tick failed");
  require(lle_n3_ink_worker_step(worker, &edge, third, size), "margin join tick failed");
  require(lle_n3_ink_worker_lrand48_for_test() == 851401618,
          "strict margin invalid path consumed lrand48");

  /* ENB4 has three distinct projection gates; each endpoint comparison is strict. */
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  require(first_random_after_one_worker_step(0, 12.0f) == 851401618,
          "mode0 x==12 should fail its fallback gate");
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  require(first_random_after_one_worker_step(0, 50.0f) == 758783491,
          "mode0 12..60 fallback did not consume exactly two randoms");
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  require(first_random_after_one_worker_step(1, 10.0f) == 851401618,
          "mode1 x==10 should fail its fallback gate");
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  require(first_random_after_one_worker_step(1, 50.0f) == 758783491,
          "mode1 10..60 fallback did not consume exactly two randoms");
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  require(first_random_after_one_worker_step(-1, 60.0f) == 851401618,
          "state-1 x==60 should retain the main gate");

  unsigned char mode0_a[20 * 20 * 4];
  unsigned char mode0_b[20 * 20 * 4];
  unsigned char mode1_a[20 * 20 * 4];
  unsigned char mode1_b[20 * 20 * 4];
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  completed_field_at_fallback(0, 20.0f, 10.0f, mode0_a, sizeof(mode0_a));
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  completed_field_at_fallback(0, 99.0f, 3.0f, mode0_b, sizeof(mode0_b));
  require(memcmp(mode0_a, mode0_b, sizeof(mode0_a)) == 0,
          "mode0 fallback did not override the profile with 4/40");
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  completed_field_at_fallback(1, 20.0f, 10.0f, mode1_a, sizeof(mode1_a));
  lle_n3_ink_worker_reset_host_lrand48_for_test();
  completed_field_at_fallback(1, 99.0f, 3.0f, mode1_b, sizeof(mode1_b));
  require(memcmp(mode1_a, mode1_b, sizeof(mode1_a)) != 0,
          "mode1 fallback incorrectly replaced its selected profile");

  /* ARM ordering: fallback margin belongs to the following mode-2 Perturb. */
  check_mode_fallback_to_mode2_transition(0, 12.0f);
  check_mode_fallback_to_mode2_transition(1, 10.0f);

  lle_n3_ink_worker_reset(worker);
  require(lle_n3_ink_worker_step(worker, &valid, third, size), "reset tick failed");
  require(!any_nonzero(third, size), "reset did not clear completed velocity field");

  free(first);
  free(second);
  free(third);
  lle_n3_ink_worker_destroy(worker);
  puts("N3 Ripple Ink worker host tests passed");
  return 0;
}
