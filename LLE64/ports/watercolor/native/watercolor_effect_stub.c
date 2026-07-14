#include <stddef.h>

/*
 * The original Samsung common engine dlopened libsecveWaterColor.so and called
 * createScene().  LLE64's AArch64 common bridge owns the reconstructed scene
 * directly, but keeps this SONAME/export as a packaging and ABI sentinel.
 */
__attribute__((visibility("default"))) void *createScene(void) {
    return NULL;
}

__attribute__((visibility("default"))) int lle64_watercolor_effect_bridge_version(void) {
    return 1;
}
