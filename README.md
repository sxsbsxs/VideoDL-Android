# VideoDL Android

**VideoDL** is an open-source Android video downloader built with Kotlin, Java, Python and C++.

- **Kotlin + Jetpack Compose:** native app UI, navigation and app state.
- **Java:** Android foreground download service and MediaStore publishing.
- **Python + yt-dlp:** on-device site extraction, metadata and downloading.
- **C++ + Android NDK:** native bridge prepared for FFmpeg integration.
- **No self-hosted backend:** extraction runs on the Android device and connects directly to the target website.

## Current milestone: 0.4.0

- Refined monochrome UI with an Apple-inspired visual language and IDM-style workflow.
- Paste video links from the clipboard and parse them locally with yt-dlp.
- Show title, a larger landscape cover, duration and available progressive formats.
- Show visible cover-loading and cover-error placeholders when a site blocks or omits its thumbnail.
- Browse completed downloads in the app, including locally generated video thumbnails, file sizes and save dates.
- Open or delete saved files from the Downloads and Files tabs.
- Automatically refresh the Downloads tab while it is visible.
- Offer formats which contain both audio and video in one stream.
- Start a download in an Android foreground service.
- Show download percentage, transfer speed and estimated remaining time in the Android foreground notification.
- Publish completed downloads to Download/VideoDL on Android 10 and newer.
- Display a clearer explanation when a site responds with HTTP 412 or HTTP 429.
- For Bilibili only, retry the playurl metadata request once with the alternate endpoint if the standard WBI endpoint returns HTTP 412. This is a best-effort fallback based on community reports; it is not guaranteed for every video or account.
- Build a debug APK with GitHub Actions.

> Website support depends on yt-dlp extractors and each website's current access rules. A successful APK build does not guarantee that every website will accept an extraction request.

## Important current limitations

- The current APK does **not** bundle FFmpeg shared libraries. The C++ JNI module checks whether libavformat.so is present, but a clean build will report it as unavailable.
- To avoid silently creating video-only files, the current version only lists formats which already contain audio and video together. Separate high-resolution video/audio streams cannot yet be merged in this build.
- Pause/resume controls, a persistent multi-task queue, in-app progress history, secure cookie import, and merging separate high-resolution video/audio streams remain follow-up work.
- On Android 7–9, downloaded files live in the app-specific Download/VideoDL folder; on Android 10 and newer, completed files are published to Download/VideoDL.
- DRM-protected streams and content which requires bypassing access controls are not supported.
- Some sites may require an authorized login or cookies, and some websites may temporarily block automated requests.

## HTTP 412 troubleshooting

HTTP 412 is returned by the **target website**, not by the Android build system. Bilibili has had yt-dlp extractor reports where the webpage loads but its playurl metadata endpoint returns 412. VideoDL now tries one alternate playurl endpoint in that specific case. If both endpoints fail, check that the video opens normally on the official site and that your account/network is allowed to access it. The app does not bypass DRM or access controls.

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
│   └── bridge.py    # yt-dlp extraction, download and Bilibili fallback
├── app/src/main/cpp/
│   ├── CMakeLists.txt
│   └── ffmpeg_bridge.cpp
└── .github/workflows/android.yml
~~~

## Legal and responsible use

Use VideoDL only for content you own, content in the public domain, or content you are authorized to download. You are responsible for respecting copyright, website terms, privacy and applicable law. VideoDL does not provide a service for bypassing DRM or access controls.

## License

The VideoDL project source is licensed under the MIT License. See LICENSE and THIRD_PARTY_NOTICES.md for dependency notices.
