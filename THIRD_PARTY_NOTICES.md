# Third-party notices

VideoDL uses third-party software. Their respective licenses remain applicable.

## yt-dlp

- Project: https://github.com/yt-dlp/yt-dlp
- License: The yt-dlp project is released into the public domain under the Unlicense (see the upstream repository for the current license text).
- The APK obtains the pinned Python package during the build; the yt-dlp source is not vendored in this repository.

## FFmpeg

- Project: https://ffmpeg.org/
- FFmpeg shared libraries are **not included** in the current APK.
- Any future FFmpeg build must ship the exact license text and source/offers required by its configuration. FFmpeg licensing depends on enabled build options: a build with GPL components is GPL, while a build using only LGPL-compatible components can be distributed under the corresponding LGPL terms.

## Chaquopy

- Project: https://chaquo.com/chaquopy/
- Used to embed Python in the Android application. Consult the upstream license and notices for the version being distributed.
