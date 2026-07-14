// Probe-only loader shim. This is deliberately not suitable for APK packaging:
// std::cout is only storage, and exception paths abort instead of implementing
// the historical STLport ABI. It exists solely to reveal the next linker/load
// boundary in a disposable process.
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

void *stl_node_allocate(size_t *size)
        __asm__("_ZNSt12__node_alloc11_M_allocateERm");
void *stl_node_allocate(size_t *size) {
    return malloc(size == NULL ? 0 : *size);
}

void stl_node_deallocate(void *memory, size_t size)
        __asm__("_ZNSt12__node_alloc13_M_deallocateEPvm");
void stl_node_deallocate(void *memory, size_t size) {
    (void) size;
    free(memory);
}

__attribute__((noreturn)) void stl_throw_length_error(const char *message)
        __asm__("_ZSt24__stl_throw_length_errorPKc");
__attribute__((noreturn)) void stl_throw_length_error(const char *message) {
    (void) message;
    abort();
}

__attribute__((noreturn)) void stl_ios_throw_failure(void)
        __asm__("_ZNSt8ios_base16_M_throw_failureEv");
__attribute__((noreturn)) void stl_ios_throw_failure(void) {
    abort();
}

__attribute__((visibility("default"), aligned(16)))
unsigned char stl_cout_probe_storage[512] __asm__("_ZSt4cout");
