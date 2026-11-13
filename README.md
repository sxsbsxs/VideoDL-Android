# VideoDL Android

**VideoDL** is an open-source Android video downloader built with Kotlin, Java, Python and C++.

- **Kotlin + Jetpack Compose:** application UI and app state.
- **Java:** Android foreground download service and MediaStore publishing.
- **Python + yt-dlp:** on-device site extraction, metadata and downloading.
- **C++ + Android NDK:** native bridge prepared for FFmpeg integration.
- **No self-hosted backend:** extraction runs on the Android device and connects directly to the target website.

> This is the first implementation milestone (0.1.0), not a claim that every downloader feature is complete. Website support depends on yt-dlp extractors and each website's current access rules.

## Features in this milestone

- Paste an HTTP/HTTPS video URL and run a real yt-dlp extraction.
- Show title, thumbnail, duration and available progressive formats.
- Offer formats which contain both audio and video in one stream.
- Start a download in an Android foreground service.
- Publish completed downloads to Download/VideoDL on Android 10 and newer.
- Build a debug APK with GitHub Actions.

## Important current limitations

- The current APK does **not** bundle FFmpeg shared libraries. The C++ JNI module checks whether libavformat.so is present, but a clean build will report it as unavailable.
- To avoid silently creating video-only files, the first version only lists formats which already contain audio and video together. Separate high-resolution video/audio streams cannot yet be merged in this build.
- Pause/resume controls, a persistent multi-task queue, live speed/progress UI, cookie import, and in-app file browsing are planned follow-up work.
- DRM-protected streams and content which requires bypassing access controls are not supported.
- Some sites may require an authorized login or cookies, and some websites may not be supported by yt-dlp.

## Build

The project uses:

- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.2.20
- Chaquopy 17.2.0
- Python 3.11
- yt-dlp 2026.10.26
- Compose BOM 2026.06.01 (Compose 1.11.x, compatible with API 36)
- Android SDK 36
- NDK 27.2.12479018

The minimum Android version is API 24 (Android 7.0). The initial ABI targets are arm64-v8a and x86_64.

### GitHub Actions

Every push to main, pull request targeting main, or manual workflow dispatch runs the **Android APK** workflow. The debug APK is uploaded as an Actions artifact named VideoDL-debug-APK.

Open the repository's **Actions** tab, select the latest successful run, and download the artifact.

### Local build

Install JDK 17, Android SDK 36, NDK 27.2.12479018, CMake 3.22.1 and Gradle 8.13. Then run:

~~~bash
gradle :app:assembleDebug
~~~

Chaquopy downloads the Python runtime and the pinned yt-dlp package during the Gradle build, so the first build needs internet access and Python 3.11 on the build machine.

## Architecture

~~~text
VideoDL/
├── app/src/main/java/com/videodl/
│   ├── data/        # Kotlin-to-Python bridge
│   ├── engine/      # Kotlin-to-C++ JNI bridge
│   ├── model/       # Video metadata and formats
│   ├── service/     # Java foreground download service
│   └── ui/          # Jetpack Compose screens and theme
├── app/src/main/python/videodl/
│   └── bridge.py    # yt-dlp extraction and download entry points
├── app/src/main/cpp/
│   ├── CMakeLists.txt
│   └── ffmpeg_bridge.cpp
└── .github/workflows/android.yml
~~~

## Legal and responsible use

Use VideoDL only for content you own, content in the public domain, or content you are authorized to download. You are responsible for respecting copyright, website terms, privacy and applicable law. VideoDL does not provide a service for bypassing DRM or access controls.

## License

The VideoDL project source is licensed under the MIT License. See LICENSE and THIRD_PARTY_NOTICES.md for dependency notices.
