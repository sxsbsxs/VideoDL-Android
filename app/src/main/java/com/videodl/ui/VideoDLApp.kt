package com.videodl.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.videodl.data.DownloadQueueStore
import com.videodl.engine.NativeBridge
import com.videodl.model.VideoInfo
import com.videodl.model.DownloadedMedia
import com.videodl.service.DownloadForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

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
    var downloadTasks by remember { mutableStateOf(DownloadQueueStore.list(context)) }
    var hasCookie by remember { mutableStateOf(DownloadQueueStore.hasCookieFile(context)) }

    val cookiePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { importCookieFile(context, uri) }
                }
                result.onSuccess { file ->
                    DownloadQueueStore.setCookiePath(context, file.absolutePath)
                    hasCookie = true
                    Toast.makeText(context, "Cookie 文件已安全导入", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(context, error.message ?: "Cookie 文件导入失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(page, refreshFilesKey) {
        if (page == AppPage.DOWNLOADS.name || page == AppPage.FILES.name) {
            do {
                loadingFiles = true
                try {
                    localFiles = withContext(Dispatchers.IO) {
                        LocalMediaRepository.list(context)
                    }
                    downloadTasks = withContext(Dispatchers.IO) {
                        DownloadQueueStore.list(context)
                    }
                } catch (_: Exception) {
                    localFiles = emptyList()
                } finally {
                    loadingFiles = false
                }
                if (page == AppPage.DOWNLOADS.name) delay(1000L)
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
                            DownloadForegroundService.start(context, current.webpageUrl, format.id, current.title)
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
                onDelete = ::deleteDownloadedFile,
                tasks = downloadTasks,
                onPauseTask = { DownloadForegroundService.pauseTask(context, it) },
                onResumeTask = { DownloadForegroundService.resumeTask(context, it) },
                onRemoveTask = { DownloadForegroundService.removeTask(context, it); downloadTasks = DownloadQueueStore.list(context) }
            )
            AppPage.FILES -> FilesScreen(
                contentPadding = contentPadding,
                files = localFiles,
                loading = loadingFiles,
                onRefresh = { refreshFilesKey++ },
                onOpen = ::openDownloadedFile,
                onDelete = ::deleteDownloadedFile
            )
            AppPage.SETTINGS -> SettingsScreen(
                contentPadding = contentPadding,
                ffmpegAvailable = ffmpegAvailable,
                hasCookie = hasCookie,
                onImportCookies = { cookiePicker.launch(arrayOf("*/*")) },
                onClearCookies = {
                    DownloadQueueStore.getCookiePath(context).takeIf { it.isNotBlank() }?.let { File(it).delete() }
                    DownloadQueueStore.setCookiePath(context, null)
                    hasCookie = false
                    Toast.makeText(context, "已删除 Cookie 文件", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}


private fun importCookieFile(context: Context, uri: Uri): File {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("无法读取所选文件。")
    val bytes = input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 2 * 1024 * 1024) { "Cookie 文件不能超过 2 MiB。" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    val hasCookieRow = text.lineSequence().any { line ->
        val normalized = line.removePrefix("#HttpOnly_")
        (!line.startsWith("#") || line.startsWith("#HttpOnly_")) && normalized.split('\t').size >= 7
    }
    require(hasCookieRow) {
        "文件格式不正确。请导入浏览器导出的 Netscape cookies.txt 文件。"
    }

    val destination = File(File(context.noBackupFilesDir, "cookies"), "cookies.txt")
    destination.parentFile?.mkdirs()
    destination.writeBytes(bytes)
    destination.setReadable(false, false)
    destination.setWritable(false, false)
    destination.setReadable(true, true)
    destination.setWritable(true, true)
    return destination
}
