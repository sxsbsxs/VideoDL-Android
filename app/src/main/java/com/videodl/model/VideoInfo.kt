package com.videodl.model

data class VideoInfo(
    val webpageUrl: String,
    val title: String,
    val extractor: String,
    val thumbnail: String,
    val durationSeconds: Double?,
    val formats: List<VideoFormat>
)

data class VideoFormat(
    val id: String,
    val label: String,
    val extension: String,
    val height: Int?,
    val fps: Double?,
    val fileSizeBytes: Long?
) {
    val fileSizeLabel: String
        get() {
            val size = fileSizeBytes ?: return "大小未知"
            if (size <= 0L) return "大小未知"
            val mb = size / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}
