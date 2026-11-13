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

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.videodl.data.DownloadQueueStore;

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
    private static final String ACTION_RUN = "com.videodl.action.RUN_QUEUE";

    private ExecutorService executor;
    private ScheduledExecutorService progressExecutor;
    private final Object workerLock = new Object();
    private boolean processing = false;

    public static String start(Context context, String url, String formatId, String title) {
        String id = DownloadQueueStore.enqueue(context, url, formatId, title);
        kick(context);
        return id;
    }

    public static void pauseTask(Context context, String taskId) {
        DownloadQueueStore.pause(context, taskId);
    }

    public static void resumeTask(Context context, String taskId) {
        DownloadQueueStore.resume(context, taskId);
        kick(context);
    }

    public static boolean removeTask(Context context, String taskId) {
        return DownloadQueueStore.remove(context, taskId);
    }

    private static void kick(Context context) {
        Intent intent = new Intent(context, DownloadForegroundService.class);
        intent.setAction(ACTION_RUN);
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
        DownloadQueueStore.recoverInterrupted(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification(
                "VideoDL 下载队列",
                "正在准备下载任务…",
                true
        ));
        synchronized (workerLock) {
            if (!processing) {
                processing = true;
                executor.execute(this::processQueue);
            }
        }
        return START_STICKY;
    }

    private void processQueue() {
        while (true) {
            DownloadQueueStore.Task task;
            synchronized (workerLock) {
                task = DownloadQueueStore.nextQueued(this);
                if (task == null) {
                    processing = false;
                    stopForeground(true);
                    stopSelf();
                    return;
                }
            }
            runTask(task);
        }
    }

    private void runTask(DownloadQueueStore.Task task) {
        File progressFile = null;
        ScheduledFuture<?> progressTask = null;
        File pauseFile = DownloadQueueStore.pauseFile(this, task.id);

        try {
            File base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (base == null) base = getFilesDir();
            File outputDir = new File(base, "VideoDL");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IllegalStateException("无法创建下载目录。");
            }

            progressFile = new File(outputDir, ".progress-" + task.id + ".json");
            if (progressFile.exists()) progressFile.delete();
            pauseFile.delete();

            updateNotification("正在下载", task.title, true);

            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(getApplicationContext()));
            }

            final File watchedProgressFile = progressFile;
            progressTask = progressExecutor.scheduleWithFixedDelay(
                    () -> updateProgressFromFile(task, watchedProgressFile),
                    0,
                    1,
                    TimeUnit.SECONDS
            );

            String cookiePath = DownloadQueueStore.getCookiePath(this);
            PyObject result = Python.getInstance()
                    .getModule("videodl.bridge")
                    .callAttr(
                            "download_video",
                            task.url,
                            task.formatId == null ? "best" : task.formatId,
                            outputDir.getAbsolutePath(),
                            progressFile.getAbsolutePath(),
                            cookiePath,
                            pauseFile.getAbsolutePath(),
                            task.id
                    );

            String resultText = result.toString();
            File downloaded;
            if (resultText.startsWith("{")) {
                JSONObject merge = new JSONObject(resultText);
                if (!merge.optBoolean("merge", false)) {
                    throw new IllegalStateException("下载引擎返回了无法识别的结果。");
                }
                File video = new File(merge.getString("video_path"));
                File audio = new File(merge.getString("audio_path"));
                downloaded = new File(merge.getString("output_path"));
                updateNotification("正在合并音视频", task.title, true);
                DownloadQueueStore.update(this, task.id, "RUNNING", 99, "正在使用 FFmpeg 合并音频与视频");
                String command = "-y -i " + quote(video.getAbsolutePath())
                        + " -i " + quote(audio.getAbsolutePath())
                        + " -map 0:v:0 -map 1:a:0 -c copy -map_metadata 0 "
                        + quote(downloaded.getAbsolutePath());
                com.arthenica.ffmpegkit.Session session = FFmpegKit.execute(command);
                if (!ReturnCode.isSuccess(session.getReturnCode()) || !downloaded.isFile() || downloaded.length() == 0L) {
                    String log = session.getFailStackTrace();
                    throw new IllegalStateException("FFmpeg 合并失败：" + (log == null ? "请检查音视频编码格式。" : log));
                }
                video.delete();
                audio.delete();
            } else {
                downloaded = new File(resultText);
            }

            if (!downloaded.isFile() || downloaded.length() == 0L) {
                throw new IllegalStateException("解析器没有返回有效的视频文件。");
            }
            if (pauseFile.exists()) {
                DownloadQueueStore.update(this, task.id, "PAUSED", 0, "已暂停，可继续下载");
                updateNotification("下载已暂停", task.title, false);
                return;
            }

            String savedAt = publishToDownloads(downloaded);
            DownloadQueueStore.update(this, task.id, "COMPLETED", 100, "下载完成 · " + savedAt);
            updateNotification("下载完成", task.title + " · " + savedAt, false);
        } catch (Throwable error) {
            if (pauseFile.exists() || (error.getMessage() != null && error.getMessage().contains("VIDEODL_PAUSED"))) {
                DownloadQueueStore.update(this, task.id, "PAUSED", 0, "已暂停，继续时会尝试续传");
                updateNotification("下载已暂停", task.title, false);
            } else {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
                if (message.length() > 180) message = message.substring(0, 180);
                DownloadQueueStore.update(this, task.id, "FAILED", 0, message);
                updateNotification("下载失败", task.title + " · " + message, false);
            }
        } finally {
            if (progressTask != null) progressTask.cancel(false);
            if (progressFile != null && progressFile.exists()) progressFile.delete();
            pauseFile.delete();
        }
    }

    private String quote(String path) {
        return "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void updateProgressFromFile(DownloadQueueStore.Task task, File progressFile) {
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
                        ? (int) Math.min(99L, Math.max(0L, downloaded * 100L / total))
                        : 0;
                double speed = data.optDouble("speed", 0.0);
                int eta = data.optInt("eta", -1);
                StringBuilder detail = new StringBuilder();
                if (percent > 0) detail.append(percent).append("%");
                else detail.append("正在接收数据");
                if (speed > 0.0) detail.append(" · ").append(formatSpeed(speed));
                if (eta >= 0) detail.append(" · 剩余 ").append(formatDuration(eta));
                DownloadQueueStore.updateProgress(this, task.id, percent, detail.toString());
                updateProgressNotification("正在下载", task.title + " · " + detail, percent, true);
            } else if ("finished".equals(status)) {
                updateProgressNotification("正在整理文件", task.title, 99, true);
            }
        } catch (Exception ignored) {
            // A progress file is atomically replaced by the Python hook; retry on the next tick.
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
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return minutes >= 60
                ? String.format(Locale.US, "%d:%02d:%02d", minutes / 60, minutes % 60, remainder)
                : String.format(Locale.US, "%d:%02d", minutes, remainder);
    }

    private String publishToDownloads(File source) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return source.getAbsolutePath();

        String mime = mimeType(source.getName());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VideoDL");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri destination = getContentResolver().insert(collection, values);
        if (destination == null) throw new IllegalStateException("无法写入系统下载目录。");

        try (FileInputStream input = new FileInputStream(source);
             OutputStream output = getContentResolver().openOutputStream(destination)) {
            if (output == null) throw new IllegalStateException("无法打开目标文件。");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
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
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
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
        if (manager != null) manager.notify(NOTIFICATION_ID, notification(title, message, ongoing));
    }

    private void updateProgressNotification(String title, String message, int progress, boolean ongoing) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        Notification note = notificationBuilder()
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setProgress(100, Math.max(0, Math.min(100, progress)), false)
                .build();
        manager.notify(NOTIFICATION_ID, note);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "VideoDL 下载", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("显示队列中下载任务的进度与速度");
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
