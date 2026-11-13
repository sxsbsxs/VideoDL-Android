#include <jni.h>
#include <dlfcn.h>

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_videodl_engine_NativeBridge_ffmpegAvailable(JNIEnv *, jobject) {
    void *library = dlopen("libavformat.so", RTLD_NOW);
    if (library == nullptr) {
        return JNI_FALSE;
    }

    void *versionSymbol = dlsym(library, "avformat_version");
    dlclose(library);

    return versionSymbol != nullptr ? JNI_TRUE : JNI_FALSE;
}
