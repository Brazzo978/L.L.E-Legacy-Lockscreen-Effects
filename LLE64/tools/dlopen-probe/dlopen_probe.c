#include <dlfcn.h>
#include <stdio.h>

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s /absolute/path/lib.so\n", argv[0]);
        return 2;
    }
    dlerror();
    void *handle = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        const char *error = dlerror();
        fprintf(stderr, "FAIL %s: %s\n", argv[1], error == NULL ? "unknown" : error);
        return 1;
    }
    dlerror();
    void *jni_on_load = dlsym(handle, "JNI_OnLoad");
    const char *error = dlerror();
    printf("PASS %s JNI_OnLoad=%p dlsym=%s\n", argv[1], jni_on_load,
            error == NULL ? "ok" : error);
    dlclose(handle);
    return 0;
}
