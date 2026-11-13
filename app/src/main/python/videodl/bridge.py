import json
import os
import re
from urllib.parse import urlparse


def _validate_url(url):
    value = (url or "").strip()
    parsed = urlparse(value)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        raise ValueError("请输入有效的 http 或 https 视频网址。")
    return value


def _extractor():
    try:
        import yt_dlp
    except Exception as error:
        raise RuntimeError("yt-dlp 引擎加载失败：" + str(error))
    return yt_dlp


def parse_video(url):
    value = _validate_url(url)
    yt_dlp = _extractor()

    options = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "ignoreerrors": False,
        "socket_timeout": 20,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(value, download=False)

    if not info:
        raise RuntimeError("网站没有返回可用的视频信息。")

    raw_formats = info.get("formats") or []
    formats = []
    for item in raw_formats:
        video_codec = item.get("vcodec")
        audio_codec = item.get("acodec")
        if not video_codec or video_codec == "none":
            continue
        if not audio_codec or audio_codec == "none":
            continue
        if item.get("has_drm"):
            continue

        formats.append({
            "format_id": str(item.get("format_id") or ""),
            "height": item.get("height"),
            "fps": item.get("fps"),
            "ext": item.get("ext") or "unknown",
            "filesize": item.get("filesize") or item.get("filesize_approx"),
            "tbr": item.get("tbr") or 0,
        })

    formats.sort(
        key=lambda item: (
            item.get("height") or 0,
            item.get("fps") or 0,
            item.get("tbr") or 0
        ),
        reverse=True
    )

    cleaned_formats = []
    seen_ids = set()
    for item in formats:
        format_id = item["format_id"]
        if not format_id or format_id in seen_ids:
            continue
        seen_ids.add(format_id)
        cleaned_formats.append(item)
        if len(cleaned_formats) >= 30:
            break

    result = {
        "webpage_url": info.get("webpage_url") or value,
        "title": info.get("title") or "未知视频",
        "extractor": info.get("extractor_key") or info.get("extractor") or "unknown",
        "thumbnail": info.get("thumbnail") or "",
        "duration": info.get("duration"),
        "formats": cleaned_formats,
    }
    return json.dumps(result, ensure_ascii=False)


def download_video(url, format_id, output_dir):
    value = _validate_url(url)
    yt_dlp = _extractor()

    selected_format = (format_id or "best").strip()
    if not re.fullmatch(r"[A-Za-z0-9._,+-]{1,100}", selected_format):
        raise ValueError("下载格式标识无效。")

    directory = os.path.abspath(output_dir)
    os.makedirs(directory, exist_ok=True)
    before = set(os.listdir(directory))

    options = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "continuedl": True,
        "overwrites": False,
        "ignoreerrors": False,
        "socket_timeout": 30,
        "format": selected_format if selected_format != "best" else "best[acodec!=none]/best",
        "outtmpl": os.path.join(directory, "%(title).180B [%(id)s].%(ext)s"),
        "windowsfilenames": False,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        ydl.download([value])

    new_files = []
    for filename in os.listdir(directory):
        if filename in before or filename.endswith((".part", ".ytdl", ".temp")):
            continue
        path = os.path.join(directory, filename)
        if os.path.isfile(path):
            new_files.append(path)

    if not new_files:
        raise RuntimeError(
            "下载没有生成完整文件。该格式可能需要 FFmpeg 合并独立音视频流，"
            "或网站要求登录、Cookie、DRM/特殊授权。"
        )

    new_files.sort(key=lambda path: os.path.getmtime(path), reverse=True)
    return new_files[0]
