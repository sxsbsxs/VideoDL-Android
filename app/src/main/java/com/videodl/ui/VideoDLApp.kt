package com.videodl.ui

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.videodl.data.PythonBridge
import com.videodl.engine.NativeBridge
import com.videodl.model.VideoFormat
import com.videodl.model.VideoInfo
import com.videodl.service.DownloadForegroundService
import kotlinx.coroutines.launch

private enum class AppPage(val label: String, val symbol: String) {
    HOME("首页", "⌂"),
    DOWNLOADS("下载", "↓"),
    FILES("文件", "▤"),
    SETTINGS("设置", "⚙")
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
        bottomBar = {
            NavigationBar {
                AppPage.entries.forEach { item ->
                    NavigationBarItem(
                        selected = page == item.name,
                        onClick = { page = item.name },
                        icon = { Text(item.symbol) },
                        label = { Text(item.label) }
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
                            errorMessage = error.message?.take(240) ?: "解析失败，请检查网址或网络连接。"
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
                            Toast.makeText(context, it.message ?: "无法启动下载服务", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                contentPadding = contentPadding
            )

            AppPage.DOWNLOADS -> DownloadsScreen(contentPadding)
            AppPage.FILES -> FilesScreen(contentPadding)
            AppPage.SETTINGS -> SettingsScreen(contentPadding, ffmpegAvailable)
        }
    }
}
