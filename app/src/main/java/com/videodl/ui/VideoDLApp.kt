package com.videodl.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.videodl.data.PythonBridge
import com.videodl.engine.NativeBridge
import com.videodl.model.VideoInfo
import com.videodl.service.DownloadForegroundService
import kotlinx.coroutines.launch

private enum class AppPage(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Outlined.Home),
    DOWNLOADS("下载", Icons.Outlined.Download),
    FILES("文件", Icons.Outlined.FolderOpen),
    SETTINGS("设置", Icons.Outlined.Settings)
}

@Composable
fun VideoDLApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var page by rememberSaveable { mutableStateOf(AppPage.HOME.name) }
    var url by rememberSaveable { mutableStateOf("") }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val ffmpegAvailable = remember {
        runCatching { NativeBridge().ffmpegAvailable() }.getOrDefault(false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                AppPage.entries.forEach { item ->
                    val selected = page == item.name
                    NavigationBarItem(
                        selected = selected,
                        onClick = { page = item.name },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null
                            )
                        },
                        label = { androidx.compose.material3.Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        val contentPadding = PaddingValues(
            start = padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            top = padding.calculateTopPadding(),
            end = padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            bottom = padding.calculateBottomPadding()
        )

        when (AppPage.valueOf(page)) {
            AppPage.HOME -> HomeScreen(
                url = url,
                onUrlChange = {
                    url = it
                    errorMessage = null
                },
                parsing = parsing,
                errorMessage = errorMessage,
                videoInfo = videoInfo,
                onParse = {
                    scope.launch {
                        parsing = true
                        errorMessage = null
                        videoInfo = null
                        try {
                            videoInfo = PythonBridge.parseVideo(context, url)
                        } catch (error: Exception) {
                            errorMessage = error.message?.take(260)
                                ?: "解析失败，请检查网址或网络连接。"
                        } finally {
                            parsing = false
                        }
                    }
                },
                onDownload = { format ->
                    val current = videoInfo
                    if (current != null) {
                        runCatching {
                            DownloadForegroundService.start(context, current.webpageUrl, format.id)
                        }.onSuccess {
                            Toast.makeText(context, "已提交下载任务", Toast.LENGTH_SHORT).show()
                            page = AppPage.DOWNLOADS.name
                        }.onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: "无法启动下载服务",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onPaste = {
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = manager?.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val pasted = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                        if (pasted.isNotBlank()) {
                            url = pasted
                            errorMessage = null
                        }
                    }
                },
                onClear = {
                    url = ""
                    videoInfo = null
                    errorMessage = null
                },
                contentPadding = contentPadding
            )

            AppPage.DOWNLOADS -> DownloadsScreen(contentPadding)
            AppPage.FILES -> FilesScreen(contentPadding)
            AppPage.SETTINGS -> SettingsScreen(contentPadding, ffmpegAvailable)
        }
    }
}
