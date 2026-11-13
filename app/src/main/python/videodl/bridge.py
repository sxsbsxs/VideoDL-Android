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


def _bilibili_url_without_trailing_slash(url):
    parsed = urlparse(url)
    host = (parsed.hostname or "").lower()
    is_bilibili = host == "bilibili.com" or host.endswith(".bilibili.com")
    path = parsed.path.rstrip("/")
    if is_bilibili and path and path != parsed.path:
        return parsed._replace(path=path).geturl()
    return None


def _install_bilibili_412_fallback(yt_dlp):
    """Retry Bilibili playurl metadata once with its non-WBI endpoint after HTTP 412."""
    try:
        from yt_dlp.extractor.bilibili import BilibiliBaseIE
        from yt_dlp.utils import ExtractorError, join_nonempty, traverse_obj
    except Exception:
        return

    if getattr(BilibiliBaseIE, "_videodl_412_fallback_installed", False):
        return

    original = BilibiliBaseIE._download_playinfo

    def download_playinfo_with_fallback(self, bvid, cid, headers=None, query=None, fatal=True):
        try:
            return original(self, bvid, cid, headers=headers, query=query, fatal=fatal)
        except Exception as first_error:
            if not re.search(
                r"HTTP Error 412|HTTP 412|412: Precondition Failed|412 Precondition Failed",
                str(first_error),
                re.I,
            ):
                raise

        params = {
            "bvid": bvid,
            "cid": cid,
            "fnval": 4048,
            **self._dm_params,
            **(query or {}),
        }
        if self.is_logged_in:
            params.pop("try_look", None)

        quality = params.get("qn")
        note = (
            f"Retrying Bilibili video format {quality} for cid {cid}"
            if quality else f"Retrying Bilibili video formats for cid {cid}"
        )

        try:
            response = self._download_json(
                "https://api.bilibili.com/x/player/playurl",
                bvid,
                query=self._sign_wbi(params, bvid),
                headers=headers,
                note=note,
            )
        except Exception as fallback_error:
            raise RuntimeError(
                "Bilibili 标准 playurl 接口返回 HTTP 412，备用接口也未能获取视频信息："
                + str(fallback_error)
            ) from fallback_error

        code = traverse_obj(response, ("code", {lambda value: value * -1}))
        if code == 0:
            self.report_warning("Bilibili 标准接口返回 412；备用 playurl 接口已成功响应。")
            return response["data"]

        message = join_nonempty(
            "备用 playurl 接口无法获取视频信息",
            code,
            traverse_obj(response, ("message", {str})),
            delim=": ",
        )
        expected = code in (401, 352)
        if expected:
            message += "，请稍后重试或检查账号访问权限"
        if fatal:
            raise ExtractorError(message, expected=expected)
        self.report_warning(message)
        return None

    BilibiliBaseIE._download_playinfo = download_playinfo_with_fallback
    BilibiliBaseIE._videodl_412_fallback_installed = True


def _friendly_site_error(error):
    message = str(error)
    if re.search(r"HTTP Error 412|HTTP 412|412: Precondition Failed|412 Precondition Failed", message, re.I):
        raise RuntimeError(
            "目标网站返回 HTTP 412（Precondition Failed），拒绝了这次解析或取流请求。"
            "Bilibili 的播放信息阶段会尝试备用 playurl 接口，但网站仍可能因请求校验、"
            "登录状态、地区策略或临时访问限制而拒绝请求。请先确认视频能在官方页面正常播放。"
            "当前版本尚未提供 Cookie 导入。VideoDL 不会绕过 DRM 或网站访问控制。"
        ) from error

    if re.search(r"HTTP Error 429|HTTP 429|Too Many Requests", message, re.I):
        raise RuntimeError(
            "目标网站暂时限制了请求频率（HTTP 429）。请稍后再试，避免短时间重复解析。"
        ) from error

    raise RuntimeError(message[:600] or error.__class__.__name__) from error


def _extractor():
    try:
        import yt_dlp
    except Exception as error:
        raise RuntimeError("yt-dlp 引擎加载失败：" + str(error))
    _install_bilibili_412_fallback(yt_dlp)
    return yt_dlp


def _make_progress_hook(progress_file):
    if not progress_file:
        return None

    progress_file = os.path.abspath(progress_file)
    os.makedirs(os.path.dirname(progress_file), exist_ok=True)

    def write_progress(data):
        payload = {
            "status": data.get("status") or "unknown",
            "downloaded_bytes": data.get("downloaded_bytes") or 0,
            "total_bytes": data.get("total_bytes"),
            "total_bytes_estimate": data.get("total_bytes_estimate"),
            "speed": data.get("speed"),
            "eta": data.get("eta"),
        }
        temp_path = progress_file + ".tmp"
        try:
            with open(temp_path, "w", encoding="utf-8") as stream:
                json.dump(payload, stream, ensure_ascii=False)
            os.replace(temp_path, progress_file)
        except OSError:
            try:
                if os.path.exists(temp_path):
                    os.remove(temp_path)
            except OSError:
                pass

    return write_progress


def parse_video(url):
    value = _validate_url(url)
    yt_dlp = _extractor()

    options = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "ignoreerrors": False,
        "socket_timeout": 25,
        "retries": 2,
        "extractor_retries": 2,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        try:
            info = ydl.extract_info(value, download=False)
        except Exception as first_error:
            retry_url = _bilibili_url_without_trailing_slash(value)
            if not retry_url or not re.search(
                r"HTTP Error 412|HTTP 412|412: Precondition Failed|412 Precondition Failed",
                str(first_error),
                re.I,
            ):
                _friendly_site_error(first_error)
            try:
                ydl.report_warning("Bilibili 返回 HTTP 412；正在使用不带末尾斜杠的网址重试一次。")
                info = ydl.extract_info(retry_url, download=False)
                value = retry_url
            except Exception as retry_error:
                _friendly_site_error(retry_error)

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
        "thumbnail": info.get("thumbnail") or next(
            (item.get("url") for item in reversed(info.get("thumbnails") or []) if item.get("url")),
            ""
        ),
        "duration": info.get("duration"),
        "formats": cleaned_formats,
    }
    return json.dumps(result, ensure_ascii=False)


def download_video(url, format_id, output_dir, progress_file=None):
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
        "retries": 2,
        "format": selected_format if selected_format != "best" else "best[acodec!=none]/best",
        "outtmpl": os.path.join(directory, "%(title).180B [%(id)s] %(epoch)s.%(ext)s"),
        "windowsfilenames": False,
    }

    progress_hook = _make_progress_hook(progress_file)
    if progress_hook:
        options["progress_hooks"] = [progress_hook]

    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            ydl.download([value])
    except Exception as error:
        _friendly_site_error(error)

    excluded_progress_files = set()
    if progress_file:
        excluded_progress_files.add(os.path.abspath(progress_file))
        excluded_progress_files.add(os.path.abspath(progress_file) + ".tmp")

    new_files = []
    for filename in os.listdir(directory):
        if filename in before or filename.endswith((".part", ".ytdl", ".temp")):
            continue
        path = os.path.abspath(os.path.join(directory, filename))
        if path in excluded_progress_files:
            continue
        if os.path.isfile(path):
            new_files.append(path)

    if not new_files:
        raise RuntimeError(
            "下载没有生成完整文件。该格式可能需要 FFmpeg 合并独立音视频流，"
            "或网站要求登录、Cookie、DRM/特殊授权。"
        )

    new_files.sort(key=lambda path: os.path.getmtime(path), reverse=True)
    return new_files[0]
