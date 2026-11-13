# Native C++ module

The NDK module is built into every APK and exposes a JNI capability check for FFmpeg.

The first milestone does not bundle FFmpeg shared libraries yet. The check therefore returns false on a clean build. The Python downloader currently offers formats which already contain both audio and video, avoiding a false claim that separate streams can be merged without FFmpeg.

When FFmpeg is added, its Android ABI libraries must be built and packaged under the matching jniLibs ABI directory, with the corresponding license notices and build configuration.
