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
import androidx.compose.runtime.LaunchedEffect
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
import com.videodl.data.LocalMediaRepository
import com.videodl.engine.NativeBridge
import com.videodl.model.VideoInfo
import com.videodl.model.DownloadedMedia
import com.videodl.service.DownloadForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var localFiles by remember { mutableStateOf<List<DownloadedMedia>>(emptyList()) }
    var loadingFiles by remember { mutableStateOf(false) }
    var refreshFilesKey by remember { mutableStateOf(0) }

    LaunchedEffect(page, refreshFilesKey) {
        if (page == AppPage.DOWNLOADS.name || page == AppPage.FILES.name) {
            do {
                loadingFiles = true
                try {
                    localFiles = withContext(Dispatchers.IO) {
                        LocalMediaRepository.list(context)
                    }
                } catch (_: Exception) {
                    localFiles = emptyList()
                } finally {
                    loadingFiles = false
                }
                if (page == AppPage.DOWNLOADS.name) delay(2500L)
            } while (page == AppPage.DOWNLOADS.name)
        }
    }

    fun openDownloadedFile(file: DownloadedMedia) {
        runCatching {
            LocalMediaRepository.open(context, file)
        }.onFailure {
            Toast.makeText(context, it.message ?: "无法打开文件", Toast.LENGTH_LONG).show()
        }
    }

    fun deleteDownloadedFile(file: DownloadedMedia) {
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                LocalMediaRepository.delete(context, file)
            }
            if (deleted) {
                localFiles = localFiles.filterNot { it.uri == file.uri }
                Toast.makeText(context, "文件已删除", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "删除失败，请检查文件状态", Toast.LENGTH_LONG).show()
            }
        }
    }

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

            AppPage.DOWNLOADS -> DownloadsScreen(
                contentPadding = contentPadding,
                files = localFiles,
                loading = loadingFiles,
                onRefresh = { refreshFilesKey++ },
                onOpen = ::openDownloadedFile,
                onDelete = ::deleteDownloadedFile
            )
            AppPage.FILES -> FilesScreen(
                contentPadding = contentPadding,
                files = localFiles,
                loading = loadingFiles,
                onRefresh = { refreshFilesKey++ },
                onOpen = ::openDownloadedFile,
                onDelete = ::deleteDownloadedFile
            )
            AppPage.SETTINGS -> SettingsScreen(contentPadding, ffmpegAvailable)
        }
    }
}
