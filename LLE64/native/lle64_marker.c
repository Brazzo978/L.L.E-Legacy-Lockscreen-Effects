#include <jni.h>

JNIEXPORT jstring JNICALL
Java_com_codex_lle_Lle64Abi_nativeAbi(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, "arm64-v8a");
}
