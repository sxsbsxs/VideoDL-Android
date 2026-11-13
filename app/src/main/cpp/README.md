# Native C++ module

The NDK module is built into every APK and exposes a JNI capability check for FFmpeg.

The Android FFmpegKit AAR supplies the native FFmpeg shared libraries for arm64-v8a and x86_64. The JNI check loads libavformat.so and verifies the avformat_version symbol.

The Java foreground service uses the FFmpegKit API to mux separately downloaded video and audio streams into a local Matroska file using stream copy (no re-encoding). The application uses the non-GPL LGPL artifact; see THIRD_PARTY_NOTICES.md for license information.
