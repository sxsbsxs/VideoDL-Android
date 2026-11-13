package com.videodl.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DownloadQueueStore {
    private static final String PREFS = "videodl_private_store";
    private static final String KEY_QUEUE = "download_queue";
    private static final String KEY_COOKIE = "cookie_path";

    public static final class Task {
        public final String id;
        public final String url;
        public final String formatId;
        public final String title;
        public final String status;
        public final String detail;
        public final int progress;
        public final long createdAt;

        Task(JSONObject item) {
            id = item.optString("id", "");
            url = item.optString("url", "");
            formatId = item.optString("formatId", "best");
            title = item.optString("title", "视频下载");
            status = item.optString("status", "QUEUED");
            detail = item.optString("detail", "");
            progress = item.optInt("progress", 0);
            createdAt = item.optLong("createdAt", 0L);
        }
    }

    private DownloadQueueStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray read(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY_QUEUE, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void write(Context context, JSONArray queue) {
        prefs(context).edit().putString(KEY_QUEUE, queue.toString()).commit();
    }

    private static void safePut(JSONObject item, String key, Object value) {
        try { item.put(key, value); } catch (Exception ignored) {}
    }

    public static synchronized List<Task> list(Context context) {
        JSONArray data = read(context);
        List<Task> result = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item != null) result.add(new Task(item));
        }
        return result;
    }

    public static synchronized String enqueue(Context context, String url, String formatId, String title) {
        String id = UUID.randomUUID().toString().replace("-", "");
        JSONArray data = read(context);
        JSONObject item = new JSONObject();
        try {
            item.put("id", id);
            item.put("url", url);
            item.put("formatId", formatId == null ? "best" : formatId);
            item.put("title", title == null || title.trim().isEmpty() ? "视频下载" : title);
            item.put("status", "QUEUED");
            item.put("detail", "等待下载");
            item.put("progress", 0);
            item.put("createdAt", System.currentTimeMillis());
            data.put(item);
            while (data.length() > 100) data.remove(0);
        } catch (Exception ignored) {}
        write(context, data);
        return id;
    }

    public static synchronized Task nextQueued(Context context) {
        JSONArray data = read(context);
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item != null && "QUEUED".equals(item.optString("status"))) {
                safePut(item, "status", "RUNNING");
                safePut(item, "detail", "正在连接网站");
                write(context, data);
                return new Task(item);
            }
        }
        return null;
    }

    public static synchronized void update(Context context, String id, String status, int progress, String detail) {
        JSONArray data = read(context);
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) {
                if (status != null) safePut(item, "status", status);
                safePut(item, "progress", Math.max(0, Math.min(100, progress)));
                if (detail != null) safePut(item, "detail", detail);
                write(context, data);
                return;
            }
        }
    }

    public static synchronized void updateProgress(Context context, String id, int progress, String detail) {
        update(context, id, null, progress, detail);
    }

    public static synchronized void pause(Context context, String id) {
        Task task = find(context, id);
        if (task == null) return;
        if ("QUEUED".equals(task.status)) {
            update(context, id, "PAUSED", task.progress, "已暂停，等待继续");
        } else if ("RUNNING".equals(task.status)) {
            File flag = pauseFile(context, id);
            try { flag.getParentFile().mkdirs(); flag.createNewFile(); } catch (Exception ignored) {}
            update(context, id, "PAUSING", task.progress, "正在安全暂停…");
        }
    }

    public static synchronized void resume(Context context, String id) {
        Task task = find(context, id);
        if (task == null) return;
        if ("PAUSED".equals(task.status) || "FAILED".equals(task.status)) {
            pauseFile(context, id).delete();
            update(context, id, "QUEUED", task.progress, "已加入等待队列");
        }
    }

    public static synchronized boolean remove(Context context, String id) {
        JSONArray data = read(context);
        JSONArray next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            if (id.equals(item.optString("id"))) {
                String state = item.optString("status");
                if ("RUNNING".equals(state) || "PAUSING".equals(state)) return false;
                removed = true;
            } else next.put(item);
        }
        if (removed) write(context, next);
        pauseFile(context, id).delete();
        return removed;
    }

    public static synchronized Task find(Context context, String id) {
        for (Task task : list(context)) if (id.equals(task.id)) return task;
        return null;
    }

    public static synchronized void recoverInterrupted(Context context) {
        JSONArray data = read(context);
        boolean changed = false;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            String status = item.optString("status");
            if ("RUNNING".equals(status)) {
                safePut(item, "status", "QUEUED");
                safePut(item, "detail", "应用重启，准备从临时文件继续");
                changed = true;
            } else if ("PAUSING".equals(status)) {
                safePut(item, "status", "PAUSED");
                safePut(item, "detail", "已暂停，可继续下载");
                changed = true;
            }
        }
        if (changed) write(context, data);
    }

    public static File pauseFile(Context context, String id) {
        return new File(new File(context.getNoBackupFilesDir(), "download-control"), "pause-" + id);
    }

    public static String getCookiePath(Context context) {
        String path = prefs(context).getString(KEY_COOKIE, "");
        File file = path.isEmpty() ? null : new File(path);
        return file != null && file.isFile() ? file.getAbsolutePath() : "";
    }

    public static void setCookiePath(Context context, String path) {
        prefs(context).edit().putString(KEY_COOKIE, path == null ? "" : path).commit();
    }

    public static boolean hasCookieFile(Context context) {
        return !getCookiePath(context).isEmpty();
    }
}
