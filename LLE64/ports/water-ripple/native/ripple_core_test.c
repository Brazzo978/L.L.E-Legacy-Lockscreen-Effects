#include "ripple_core.h"

#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum { DETAIL = 104, CELL_COUNT = DETAIL * DETAIL };

static uint64_t hash_float_array(uint64_t hash, const float *values, size_t count) {
    for (size_t i = 0; i < count; ++i) {
        uint32_t bits = 0;
        memcpy(&bits, &values[i], sizeof(bits));
        for (int byte = 0; byte < 4; ++byte) {
            hash ^= (uint8_t) (bits >> (byte * 8));
            hash *= UINT64_C(1099511628211);
        }
    }
    return hash;
}

static int nearly_equal(float a, float b) {
    return fabsf(a - b) <= 0.00001f;
}

int main(void) {
    float mesh_vertices[27] = {0};
    int16_t mesh_indices[24] = {0};
    static const int16_t expected_indices[24] = {
        0, 1, 4, 0, 4, 3,
        1, 2, 5, 1, 5, 4,
        3, 4, 7, 3, 7, 6,
        4, 5, 8, 4, 8, 7
    };
    lle_ripple_init_waters(mesh_vertices, mesh_indices, 9, 4, 6, 3, 3);
    if (!nearly_equal(mesh_vertices[0], -3.0f)
            || !nearly_equal(mesh_vertices[1], 2.0f)
            || !nearly_equal(mesh_vertices[12], 0.0f)
            || !nearly_equal(mesh_vertices[13], 0.0f)
            || !nearly_equal(mesh_vertices[24], 3.0f)
            || !nearly_equal(mesh_vertices[25], -2.0f)
            || memcmp(mesh_indices, expected_indices, sizeof(expected_indices)) != 0) {
        fprintf(stderr, "mesh initialization mismatch\n");
        return 1;
    }

    float *velocity = (float *) calloc(CELL_COUNT, sizeof(float));
    float *height = (float *) calloc(CELL_COUNT, sizeof(float));
    if (velocity == NULL || height == NULL) {
        fprintf(stderr, "allocation failed\n");
        free(velocity);
        free(height);
        return 2;
    }

    const int center = 52 + 52 * DETAIL;
    if (!lle_ripple_move(velocity, height, 2, 2, 102, 102,
            DETAIL, DETAIL, true, 0.94f, 0.5f)) {
        fprintf(stderr, "zero field was not empty\n");
        return 3;
    }

    lle_ripple_inject(velocity, 50, 50, DETAIL, DETAIL, 0.0f, 0.0f, 2.0f);
    if (!nearly_equal(velocity[center], 6.0f)
            || !nearly_equal(velocity[center + 1], 4.0f)
            || !nearly_equal(velocity[center + DETAIL], 4.0f)) {
        fprintf(stderr, "injection mismatch center=%g right=%g down=%g\n",
                velocity[center], velocity[center + 1], velocity[center + DETAIL]);
        return 4;
    }

    const bool empty_after_first_move = lle_ripple_move(
            velocity, height, 2, 2, 102, 102,
            DETAIL, DETAIL, true, 0.94f, 0.5f);
    if (empty_after_first_move) {
        fprintf(stderr, "active field reported empty\n");
        return 5;
    }

    lle_ripple_inject(velocity, 50, 50, DETAIL, DETAIL,
            12.5f, -10.0f, 1.5f);
    (void) lle_ripple_move(velocity, height, 2, 2, 102, 102,
            DETAIL, DETAIL, true, 0.94f, 0.5f);

    uint64_t hash = UINT64_C(1469598103934665603);
    hash = hash_float_array(hash, velocity, CELL_COUNT);
    hash = hash_float_array(hash, height, CELL_COUNT);
    printf("PASS hash=%016llx centerVelocity=%.9g centerHeight=%.9g\n",
            (unsigned long long) hash, velocity[center], height[center]);

    free(velocity);
    free(height);
    return 0;
}
