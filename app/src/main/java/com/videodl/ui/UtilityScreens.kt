package com.videodl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DownloadsScreen(contentPadding: PaddingValues) {
    PageFrame(contentPadding) {
        PageHeader(
            eyebrow = "YOUR DOWNLOADS",
            title = "下载任务",
            subtitle = "后台下载时，系统通知显示实时进度与速度。"
        )
        EmptyState(
            icon = Icons.Outlined.Download,
            title = "下载记录还在路上",
            description = "当前版本通过 Android 前台服务下载，系统通知栏会显示实时百分比、下载速度与预计剩余时间。持久化任务队列、应用内历史与暂停/继续仍在完善。"
        )
        InfoStrip(
            icon = Icons.Outlined.Info,
            title = "下载保存位置",
            description = "Download/VideoDL"
        )
    }
}

@Composable
fun FilesScreen(contentPadding: PaddingValues) {
    PageFrame(contentPadding) {
        PageHeader(
            eyebrow = "LOCAL LIBRARY",
            title = "本地文件",
            subtitle = "你保存到设备的视频与音频。"
        )
        EmptyState(
            icon = Icons.Outlined.FolderOpen,
            title = "暂时还没有文件列表",
            description = "下载完成的文件会保存到 Download/VideoDL。应用内文件索引、封面网格和文件操作将在下一阶段接入。"
        )
        InfoStrip(
            icon = Icons.Outlined.VideoFile,
            title = "默认目录",
            description = "Download/VideoDL"
        )
    }
}

@Composable
fun SettingsScreen(contentPadding: PaddingValues, ffmpegAvailable: Boolean) {
    PageFrame(contentPadding) {
        PageHeader(
            eyebrow = "PREFERENCES",
            title = "设置",
            subtitle = "引擎、存储与隐私选项。"
        )
        SettingsGroup("应用与引擎") {
            SettingRow(
                icon = Icons.Outlined.Tune,
                title = "解析引擎",
                subtitle = "Python 3.11 · yt-dlp 2026.10.26",
                trailing = "已集成"
            )
            SettingRow(
                icon = Icons.Outlined.Settings,
                title = "Android 界面",
                subtitle = "Kotlin · Jetpack Compose · Java 服务层",
                trailing = "原生"
            )
            SettingRow(
                icon = Icons.Outlined.VideoFile,
                title = "FFmpeg 处理模块",
                subtitle = if (ffmpegAvailable) {
                    "检测到 FFmpeg 动态库"
                } else {
                    "C++ JNI 已就绪，FFmpeg 库尚未打包"
                },
                trailing = if (ffmpegAvailable) "可用" else "待集成"
            )
        }
        SettingsGroup("存储与隐私") {
            SettingRow(
                icon = Icons.Outlined.FolderOpen,
                title = "默认保存目录",
                subtitle = "Download/VideoDL",
                trailing = null
            )
            SettingRow(
                icon = Icons.Outlined.Lock,
                title = "网络与隐私",
                subtitle = "不使用 VideoDL 自建服务器；解析请求直连目标网站",
                trailing = "本地"
            )
        }
        InfoStrip(
            icon = Icons.Outlined.Info,
            title = "VideoDL Android 0.3.0",
            description = "仅下载你拥有或获准保存的内容。DRM 与访问控制不会被绕过。"
        )
    }
}

@Composable
private fun PageFrame(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 20.dp,
                top = contentPadding.calculateTopPadding() + 19.dp,
                end = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 25.dp
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        content()
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            letterSpacing = 1.8.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(title, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 27.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(29.dp))
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 3.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(37.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
        if (trailing != null) {
            Text(
                trailing,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InfoStrip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
