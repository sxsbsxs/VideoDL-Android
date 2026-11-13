package com.videodl.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.videodl.model.DownloadedMedia
import java.io.File
import java.util.Locale

object LocalMediaRepository {
    private val supportedExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi",
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus"
    )

    fun list(context: Context): List<DownloadedMedia> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStore(context)
        } else {
            queryLegacyDirectory(context)
        }
    }

    private fun queryMediaStore(context: Context): List<DownloadedMedia> {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val result = mutableListOf<DownloadedMedia>()
        val selection = MediaStore.MediaColumns.RELATIVE_PATH + "=?"
        val selectionArgs = arrayOf(Environment.DIRECTORY_DOWNLOADS + "/VideoDL/")
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            MediaStore.MediaColumns.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn).orEmpty()
                val mime = cursor.getString(mimeColumn).orEmpty()
                if (!isSupported(name, mime)) continue
                val id = cursor.getLong(idColumn)
                result += DownloadedMedia(
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = name,
                    mimeType = mime.ifBlank { mimeForName(name) },
                    sizeBytes = cursor.getLong(sizeColumn),
                    modifiedAtMillis = cursor.getLong(dateColumn) * 1000L,
                    localFile = null
                )
            }
        }
        return result
    }

    private fun queryLegacyDirectory(context: Context): List<DownloadedMedia> {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val directory = File(base, "VideoDL")
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && isSupported(it.name, mimeForName(it.name)) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                DownloadedMedia(
                    uri = Uri.fromFile(file),
                    displayName = file.name,
                    mimeType = mimeForName(file.name),
                    sizeBytes = file.length(),
                    modifiedAtMillis = file.lastModified(),
                    localFile = file
                )
            }
    }

    fun loadThumbnail(context: Context, media: DownloadedMedia): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && media.localFile == null) {
            return runCatching {
                context.contentResolver.loadThumbnail(media.uri, Size(480, 270), null)
            }.getOrNull()
        }
        val file = media.localFile ?: return null
        return runCatching {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(
                file.absolutePath,
                MediaStore.Images.Thumbnails.MINI_KIND
            )
        }.getOrNull()
    }

    fun open(context: Context, media: DownloadedMedia) {
        val uri = if (media.localFile != null) {
            FileProvider.getUriForFile(
                context,
                context.packageName + ".files",
                media.localFile
            )
        } else {
            media.uri
        }
        val mime = media.mimeType.ifBlank { mimeForName(media.displayName) }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "打开文件")
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun delete(context: Context, media: DownloadedMedia): Boolean {
        return if (media.localFile != null) {
            media.localFile.delete()
        } else {
            context.contentResolver.delete(media.uri, null, null) > 0
        }
    }

    private fun isSupported(name: String, mime: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in supportedExtensions || mime.startsWith("video/") || mime.startsWith("audio/")
    }

    private fun mimeForName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (extension in setOf("mkv", "m4v")) "video/mp4" else "application/octet-stream"
    }
}
