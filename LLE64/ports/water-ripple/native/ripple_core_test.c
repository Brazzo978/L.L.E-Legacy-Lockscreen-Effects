#include "ripple_core.h"

#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum {
    DETAIL = 104,
    CELL_COUNT = DETAIL * DETAIL,
    SAMSUNG_SURFACE = 100,
    SAMSUNG_VERTEX_COUNT = SAMSUNG_SURFACE * SAMSUNG_SURFACE,
    SAMSUNG_INDEX_COUNT =
            (SAMSUNG_SURFACE - 1) * (SAMSUNG_SURFACE - 1) * 6
};

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

static int vertex_nearly_equal(
        const float *vertices,
        int vertex,
        float expected_x,
        float expected_y,
        float tolerance) {
    const int offset = vertex * 3;
    return fabsf(vertices[offset] - expected_x) <= tolerance
            && fabsf(vertices[offset + 1] - expected_y) <= tolerance
            && vertices[offset + 2] == 0.0f;
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
    if (!nearly_equal(mesh_vertices[0], -2.0f)
            || !nearly_equal(mesh_vertices[1], 3.0f)
            || !nearly_equal(mesh_vertices[12], 0.6666667f)
            || !nearly_equal(mesh_vertices[13], 0.0f)
            || !nearly_equal(mesh_vertices[24], 2.0f)
            || !nearly_equal(mesh_vertices[25], -3.0f)
            || memcmp(mesh_indices, expected_indices, sizeof(expected_indices)) != 0) {
        fprintf(stderr, "mesh initialization mismatch\n");
        return 1;
    }

    float *samsung_vertices = (float *) calloc(
            SAMSUNG_VERTEX_COUNT * 3, sizeof(float));
    int16_t *samsung_indices = (int16_t *) calloc(
            SAMSUNG_INDEX_COUNT, sizeof(int16_t));
    if (samsung_vertices == NULL || samsung_indices == NULL) {
        fprintf(stderr, "Samsung mesh golden allocation failed\n");
        free(samsung_vertices);
        free(samsung_indices);
        return 6;
    }
    lle_ripple_init_waters(
            samsung_vertices,
            samsung_indices,
            SAMSUNG_VERTEX_COUNT,
            50,
            50,
            SAMSUNG_SURFACE,
            SAMSUNG_SURFACE);

    // Captured by executing the original ARM32 initWaters bulk path. Its VRECPE-based
    // division differs by a few ULP between architectures, so keep a narrow absolute
    // tolerance while requiring the historical 0.5-unit X shear to remain present.
    const float samsung_tolerance = 0.00005f;
    if (!vertex_nearly_equal(samsung_vertices, 0,
                    -25.0f, 25.0f, samsung_tolerance)
            || !vertex_nearly_equal(samsung_vertices, 1,
                    -24.99494934f, 24.49494934f, samsung_tolerance)
            || !vertex_nearly_equal(samsung_vertices, 99,
                    -24.5f, -24.99999619f, samsung_tolerance)
            || !vertex_nearly_equal(samsung_vertices, 100,
                    -24.49494934f, 25.0f, samsung_tolerance)
            || !vertex_nearly_equal(samsung_vertices, 5000,
                    0.25253487f, 25.0f, samsung_tolerance)
            || !vertex_nearly_equal(samsung_vertices, 9999,
                    25.50001907f, -24.99999619f, samsung_tolerance)) {
        fprintf(stderr, "Samsung 100x100 bulk mesh golden mismatch\n");
        free(samsung_vertices);
        free(samsung_indices);
        return 7;
    }
    free(samsung_vertices);
    free(samsung_indices);

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
