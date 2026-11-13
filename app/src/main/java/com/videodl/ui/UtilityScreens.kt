package com.videodl.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videodl.data.LocalMediaRepository
import com.videodl.data.DownloadQueueStore
import com.videodl.model.DownloadedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues,
    files: List<DownloadedMedia>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpen: (DownloadedMedia) -> Unit,
    onDelete: (DownloadedMedia) -> Unit,
    tasks: List<DownloadQueueStore.Task>,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit
) {
    PageFrame(contentPadding) {
        PageHeader(
            eyebrow = "YOUR DOWNLOADS",
            title = "下载任务",
            subtitle = "队列会保存在本机；每次同时执行一个任务。"
        )
        DownloadQueueSection(
            tasks = tasks,
            onPause = onPauseTask,
            onResume = onResumeTask,
            onRemove = onRemoveTask
        )
        MediaListSection(
            title = "最近下载",
            files = files,
            loading = loading,
            emptyTitle = "还没有已完成的下载",
            emptyDescription = "开始下载后，任务进度会显示在系统通知栏；完成后文件会自动出现在这里。",
            onRefresh = onRefresh,
            onOpen = onOpen,
            onDelete = onDelete
        )
        InfoStrip(
            icon = Icons.Outlined.Info,
            title = "下载保存位置",
            description = "Download/VideoDL"
        )
    }
}

@Composable
private fun DownloadQueueSection(
    tasks: List<DownloadQueueStore.Task>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("下载队列", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text("${tasks.count { it.status == "RUNNING" || it.status == "QUEUED" || it.status == "PAUSING" }} 个进行中",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        if (tasks.isEmpty()) {
            Text("目前没有下载任务。解析视频后点击下载，就会自动加入队列。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        } else {
            tasks.asReversed().take(20).forEach { task ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(task.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (task.status) {
                                    "QUEUED" -> "排队中"
                                    "RUNNING" -> "正在下载"
                                    "PAUSING" -> "正在暂停"
                                    "PAUSED" -> "已暂停"
                                    "COMPLETED" -> "已完成"
                                    "FAILED" -> "失败"
                                    else -> task.status
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (task.status == "FAILED") MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            if (task.status == "RUNNING" || task.status == "PAUSING" || task.status == "QUEUED") {
                                OutlinedButton(onClick = { onPause(task.id) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)) {
                                    Text(if (task.status == "QUEUED") "暂停排队" else "暂停", fontSize = 10.sp)
                                }
                            } else if (task.status == "PAUSED" || task.status == "FAILED") {
                                Button(onClick = { onResume(task.id) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)) {
                                    Text("继续", fontSize = 10.sp)
                                }
                            } else if (task.status == "COMPLETED") {
                                OutlinedButton(onClick = { onRemove(task.id) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)) {
                                    Text("移除", fontSize = 10.sp)
                                }
                            }
                        }
                        if (task.status == "RUNNING" || task.status == "PAUSING") {
                            LinearProgressIndicator(
                                progress = { task.progress.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (task.detail.isNotBlank()) {
                            Text(task.detail, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilesScreen(
    contentPadding: PaddingValues,
    files: List<DownloadedMedia>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpen: (DownloadedMedia) -> Unit,
    onDelete: (DownloadedMedia) -> Unit
) {
    PageFrame(contentPadding) {
        PageHeader(
            eyebrow = "LOCAL LIBRARY",
            title = "本地文件",
            subtitle = "查看、打开或删除 VideoDL 保存的文件。"
        )
        MediaListSection(
            title = "文件库",
            files = files,
            loading = loading,
            emptyTitle = "文件夹目前是空的",
            emptyDescription = "下载完成的视频或音频会自动整理到 Download/VideoDL。",
            onRefresh = onRefresh,
            onOpen = onOpen,
            onDelete = onDelete
        )
        InfoStrip(
            icon = Icons.Outlined.FolderOpen,
            title = "扫描范围",
            description = "仅显示 VideoDL 自己保存的文件，不扫描你的整个手机。"
        )
    }
}

@Composable
private fun MediaListSection(
    title: String,
    files: List<DownloadedMedia>,
    loading: Boolean,
    emptyTitle: String,
    emptyDescription: String,
    onRefresh: () -> Unit,
    onOpen: (DownloadedMedia) -> Unit,
    onDelete: (DownloadedMedia) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "${files.size} 项",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新文件列表")
            }
        }

        if (loading && files.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (files.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FolderOpen,
                title = emptyTitle,
                description = emptyDescription
            )
        } else {
            files.take(60).forEach { file ->
                MediaFileCard(file = file, onOpen = { onOpen(file) }, onDelete = { onDelete(file) })
            }
        }
    }
}

@Composable
private fun MediaFileCard(
    file: DownloadedMedia,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = file.uri) {
        value = withContext(Dispatchers.IO) {
            LocalMediaRepository.loadThumbnail(context, file)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 108.dp, height = 76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = "视频缩略图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Outlined.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    file.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatFileSize(file.sizeBytes)} · ${formatDate(file.modifiedAtMillis)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    maxLines = 2
                )
                Text(
                    file.mimeType.substringBefore('/').uppercase(Locale.ROOT),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = "打开文件")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "删除文件",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "大小未知"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "日期未知"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(timestamp))
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    ffmpegAvailable: Boolean,
    hasCookie: Boolean,
    onImportCookies: () -> Unit,
    onClearCookies: () -> Unit
) {
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
                    "FFmpegKit 原生库不可用，请重装或检查 APK"
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
        SettingsGroup("登录与画质") {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (hasCookie) "已导入 Cookie 文件" else "尚未导入 Cookie 文件",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "选择从浏览器导出的 Netscape cookies.txt 文件，可用于需要登录的网站。文件保存在应用私有且不参与备份的目录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
                Button(onClick = onImportCookies, modifier = Modifier.fillMaxWidth()) {
                    Text(if (hasCookie) "重新导入 cookies.txt" else "导入 cookies.txt")
                }
                if (hasCookie) {
                    OutlinedButton(onClick = onClearCookies, modifier = Modifier.fillMaxWidth()) {
                        Text("删除已保存的 Cookie")
                    }
                }
            }
        }
        InfoStrip(
            icon = Icons.Outlined.Info,
            title = "VideoDL Android 0.5.0",
            description = "仅下载你拥有或获准保存的内容。DRM 与访问控制不会被绕过。"
        )
    }
}

@Composable
private fun PageFrame(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
