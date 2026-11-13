package com.videodl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DownloadsScreen(contentPadding: PaddingValues) {
    PageColumn(contentPadding) {
        PageTitle("下载任务", "在通知栏查看当前下载状态。")
        InfoCard(
            title = "下载位置",
            body = "Android 10 及以上：Download/VideoDL。较旧系统：应用专属下载目录。"
        )
        InfoCard(
            title = "任务说明",
            body = "当前版本支持单任务后台下载和系统通知。多任务队列、暂停/继续和实时速度统计会在后续版本完善。"
        )
    }
}

@Composable
fun FilesScreen(contentPadding: PaddingValues) {
    PageColumn(contentPadding) {
        PageTitle("本地文件", "VideoDL 下载的文件会保存在手机本地。")
        InfoCard(
            title = "保存目录",
            body = "Download/VideoDL"
        )
        InfoCard(
            title = "文件管理",
            body = "可以通过手机系统的文件管理器打开 Download/VideoDL。应用内封面网格、重命名和批量管理仍在开发中。"
        )
    }
}

@Composable
fun SettingsScreen(contentPadding: PaddingValues, ffmpegAvailable: Boolean) {
    PageColumn(contentPadding) {
        PageTitle("设置", "简单、清晰，不让设置抢走内容。")
        InfoCard(
            title = "解析引擎",
            body = "Python 3.11 · yt-dlp 2026.10.26 · 本地运行"
        )
        InfoCard(
            title = "Android 层",
            body = "Kotlin + Java · Jetpack Compose · 前台下载服务"
        )
        InfoCard(
            title = "C++ / FFmpeg",
            body = if (ffmpegAvailable) {
                "已检测到 FFmpeg 原生库。"
            } else {
                "C++ NDK 桥接模块已编译，但当前 APK 尚未打包 FFmpeg 动态库。现在只显示可直接下载的单文件音视频格式。"
            }
        )
        InfoCard(
            title = "网络与隐私",
            body = "无需 VideoDL 自建服务器。解析时仍会直接连接目标视频网站。请仅下载你有权保存或获得授权的内容。"
        )
        InfoCard(
            title = "版本",
            body = "VideoDL Android 0.1.0"
        )
    }
}

@Composable
private fun PageColumn(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                top = contentPadding.calculateTopPadding() + 24.dp,
                end = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
