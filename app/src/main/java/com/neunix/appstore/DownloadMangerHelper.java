package com.neunix.appstore;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class DownloadManagerHelper {

    private static final String PREFS = "downloads";
    private static final String KEY_HISTORY = "history";

    /* ================= DOWNLOAD APK ================= */

    public static void download(Context context, AppModel app) {

        try {
            Uri uri = Uri.parse(app.apkUrl);

            DownloadManager.Request request =
                    new DownloadManager.Request(uri);

            request.setTitle(app.name);
            request.setDescription("Downloading " + app.name);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            // ✅ App-scoped storage (Android 10+ safe)
            request.setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    app.name.replace(" ", "_") + ".apk"
            );

            DownloadManager dm =
                    (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

            dm.enqueue(request);

            saveToHistory(context, app);

        } catch (Exception e) {
            Toast.makeText(context,
                    "Download failed",
                    Toast.LENGTH_LONG).show();
        }
    }

    /* ================= DOWNLOAD HISTORY ================= */

    private static void saveToHistory(Context context, AppModel app) {
        try {
            SharedPreferences prefs =
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            Set<String> history =
                    new HashSet<>(prefs.getStringSet(KEY_HISTORY, new HashSet<>()));

            history.add(app.toJson().toString());

            prefs.edit().putStringSet(KEY_HISTORY, history).apply();

        } catch (Exception ignored) {}
    }

    public static Set<String> getHistory(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        return prefs.getStringSet(KEY_HISTORY, new HashSet<>());
    }
}