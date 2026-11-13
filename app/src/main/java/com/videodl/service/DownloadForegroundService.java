package com.videodl.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadForegroundService extends Service {
    private static final String CHANNEL_ID = "videodl_downloads";
    private static final int NOTIFICATION_ID = 4101;
    private static final String EXTRA_URL = "videodl.extra.URL";
    private static final String EXTRA_FORMAT = "videodl.extra.FORMAT";

    private ExecutorService executor;

    public static void start(Context context, String url, String formatId) {
        Intent intent = new Intent(context, DownloadForegroundService.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_FORMAT, formatId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification("正在准备下载", "VideoDL 正在启动解析引擎。", true));

        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        String url = intent.getStringExtra(EXTRA_URL);
        String formatId = intent.getStringExtra(EXTRA_FORMAT);
        executor.execute(() -> runDownload(url, formatId, startId));
        return START_NOT_STICKY;
    }

    private void runDownload(String url, String formatId, int startId) {
        try {
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("视频网址为空。");
            }

            File base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (base == null) base = getFilesDir();
            File outputDir = new File(base, "VideoDL");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IllegalStateException("无法创建下载目录。");
            }

            updateNotification("正在下载", "正在通过 yt-dlp 获取视频数据。", true);

            if (!Python.isStarted()) {
                throw new IllegalStateException("Python 解析环境尚未启动，请重新打开应用后重试。");
            }

            PyObject result = Python.getInstance()
                    .getModule("videodl.bridge")
                    .callAttr("download_video", url, formatId == null ? "best" : formatId, outputDir.getAbsolutePath());

            File downloaded = new File(result.toString());
            if (!downloaded.isFile()) {
                throw new IllegalStateException("解析器没有返回有效的视频文件。");
            }

            String savedAt = publishToDownloads(downloaded);
            updateNotification("下载完成", downloaded.getName() + " · " + savedAt, false);
        } catch (Throwable error) {
            String message = error.getMessage();
            if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
            updateNotification("下载失败", message, false);
        } finally {
            stopSelf(startId);
        }
    }

    private String publishToDownloads(File source) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return source.getAbsolutePath();
        }

        String mime = mimeType(source.getName());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VideoDL");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri destination = getContentResolver().insert(collection, values);
        if (destination == null) {
            throw new IllegalStateException("无法写入系统下载目录。");
        }

        try (FileInputStream input = new FileInputStream(source);
             OutputStream output = getContentResolver().openOutputStream(destination)) {
            if (output == null) throw new IllegalStateException("无法打开目标文件。");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
        } catch (Exception error) {
            getContentResolver().delete(destination, null, null);
            throw error;
        }

        ContentValues completed = new ContentValues();
        completed.put(MediaStore.Downloads.IS_PENDING, 0);
        getContentResolver().update(destination, completed, null, null);
        return "Download/VideoDL";
    }

    private String mimeType(String filename) {
        String name = filename.toLowerCase();
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".m4a")) return "audio/mp4";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        return "video/mp4";
    }

    private Notification notification(String title, String message, boolean ongoing) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String title, String message, boolean ongoing) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification(title, message, ongoing));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VideoDL 下载",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("显示 VideoDL 后台下载状态");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
