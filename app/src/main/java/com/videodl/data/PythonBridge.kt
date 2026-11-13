package com.videodl.data

import android.content.Context
import java.io.File
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.videodl.model.VideoFormat
import com.videodl.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object PythonBridge {
    suspend fun parseVideo(context: Context, url: String): VideoInfo = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        require(trimmedUrl.isNotBlank() && !trimmedUrl.contains(' ')) {
            "请输入有效的视频网址。"
        }
        val normalizedUrl = when {
            trimmedUrl.startsWith("https://", ignoreCase = true) -> trimmedUrl
            trimmedUrl.startsWith("http://", ignoreCase = true) -> trimmedUrl
            else -> "https://" + trimmedUrl
        }

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }

        val module = Python.getInstance().getModule("videodl.bridge")
        val jsonText = module.callAttr(
            "parse_video",
            normalizedUrl,
            File(context.cacheDir, "covers").absolutePath
        ).toString()
        val root = JSONObject(jsonText)

        val formats = mutableListOf<VideoFormat>()
        val formatsJson = root.optJSONArray("formats")
        if (formatsJson != null) {
            for (index in 0 until formatsJson.length()) {
                val item = formatsJson.optJSONObject(index) ?: continue
                val height = if (item.isNull("height")) null else item.optInt("height")
                val fps = if (item.isNull("fps")) null else item.optDouble("fps")
                val size = if (item.isNull("filesize")) null else item.optLong("filesize")
                val extension = item.optString("ext", "unknown")
                val heightLabel = if (height != null && height > 0) height.toString() + "p" else "原始画质"
                val fpsLabel = if (fps != null && fps > 0.0) " · " + fps.toInt() + "fps" else ""
                formats += VideoFormat(
                    id = item.optString("format_id"),
                    label = heightLabel + " · " + extension + fpsLabel,
                    extension = extension,
                    height = height,
                    fps = fps,
                    fileSizeBytes = size
                )
            }
        }

        VideoInfo(
            webpageUrl = root.optString("webpage_url", normalizedUrl),
            title = root.optString("title", "未知视频"),
            extractor = root.optString("extractor", "unknown"),
            thumbnail = root.optString("thumbnail", ""),
            thumbnailLocalPath = root.optString("thumbnail_local_path", ""),
            durationSeconds = if (root.isNull("duration")) null else root.optDouble("duration"),
            formats = formats
        )
    }
}
