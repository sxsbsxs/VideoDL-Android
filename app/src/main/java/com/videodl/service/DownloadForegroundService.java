package com.videodl.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DownloadForegroundService extends Service {
    private static final String CHANNEL_ID = "videodl_downloads";
    private static final int NOTIFICATION_ID = 4101;
    private static final String EXTRA_URL = "videodl.extra.URL";
    private static final String EXTRA_FORMAT = "videodl.extra.FORMAT";

    private ExecutorService executor;
    private ScheduledExecutorService progressExecutor;

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
        progressExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification(
                "正在准备下载",
                "VideoDL 正在启动解析引擎。",
                true
        ));

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
        File progressFile = null;
        ScheduledFuture<?> progressTask = null;

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

            progressFile = new File(outputDir, ".progress-" + startId + ".json");
            if (progressFile.exists()) progressFile.delete();

            updateNotification("正在下载", "正在连接目标网站…", true);

            if (!Python.isStarted()) {
                throw new IllegalStateException("Python 解析环境尚未启动，请返回首页重新解析后重试。");
            }

            final File watchedProgressFile = progressFile;
            progressTask = progressExecutor.scheduleWithFixedDelay(
                    () -> updateProgressFromFile(watchedProgressFile),
                    0,
                    1,
                    TimeUnit.SECONDS
            );

            PyObject result = Python.getInstance()
                    .getModule("videodl.bridge")
                    .callAttr(
                            "download_video",
                            url,
                            formatId == null ? "best" : formatId,
                            outputDir.getAbsolutePath(),
                            progressFile.getAbsolutePath()
                    );

            File downloaded = new File(result.toString());
            if (!downloaded.isFile()) {
                throw new IllegalStateException("解析器没有返回有效的视频文件。");
            }

            String savedAt = publishToDownloads(downloaded);
            updateNotification("下载完成", downloaded.getName() + " · " + savedAt, false);
        } catch (Throwable error) {
            String message = error.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = error.getClass().getSimpleName();
            }
            updateNotification("下载失败", message, false);
        } finally {
            if (progressTask != null) {
                progressTask.cancel(false);
            }
            if (progressFile != null && progressFile.exists()) {
                progressFile.delete();
            }
            stopSelf(startId);
        }
    }

    private void updateProgressFromFile(File progressFile) {
        if (progressFile == null || !progressFile.isFile()) return;

        try {
            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(progressFile))) {
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
            }

            JSONObject data = new JSONObject(json.toString());
            String status = data.optString("status", "");
            if ("downloading".equals(status)) {
                long downloaded = data.optLong("downloaded_bytes", 0L);
                long total = data.optLong("total_bytes", data.optLong("total_bytes_estimate", 0L));
                int percent = total > 0L
                        ? (int) Math.min(100L, Math.max(0L, downloaded * 100L / total))
                        : -1;
                double speed = data.optDouble("speed", 0.0);
                int eta = data.optInt("eta", -1);

                StringBuilder detail = new StringBuilder();
                if (percent >= 0) detail.append(percent).append("%");
                else detail.append("正在接收数据");
                if (speed > 0.0) detail.append(" · ").append(formatSpeed(speed));
                if (eta >= 0) detail.append(" · 剩余 ").append(formatDuration(eta));
                updateProgressNotification("正在下载", detail.toString(), percent, true);
            } else if ("finished".equals(status)) {
                updateProgressNotification("正在整理文件", "数据已下载，正在完成文件处理。", 100, true);
            }
        } catch (Exception ignored) {
            // The Python hook writes atomically; a partially replaced status file is simply retried.
        }
    }

    private String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond >= 1024.0 * 1024.0) {
            return String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        }
        if (bytesPerSecond >= 1024.0) {
            return String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0);
        }
        return String.format(Locale.US, "%.0f B/s", bytesPerSecond);
    }

    private String formatDuration(int seconds) {
        if (seconds < 0) return "未知";
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        if (minutes >= 60) {
            return String.format(Locale.US, "%d:%02d:%02d", minutes / 60, minutes % 60, remainder);
        }
        return String.format(Locale.US, "%d:%02d", minutes, remainder);
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
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".m4a")) return "audio/mp4";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        return "video/mp4";
    }

    private Notification.Builder notificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID);
        }
        return new Notification.Builder(this);
    }

    private Notification notification(String title, String message, boolean ongoing) {
        return notificationBuilder()
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

    private void updateProgressNotification(String title, String message, int progress, boolean ongoing) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        boolean indeterminate = progress < 0;
        Notification notification = notificationBuilder()
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setProgress(100, indeterminate ? 0 : progress, indeterminate)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VideoDL 下载",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("显示 VideoDL 后台下载进度与速度");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdown();
        if (progressExecutor != null) progressExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
