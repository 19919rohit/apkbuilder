package com.neunix.appstore;

import android.app.DownloadManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

public class AppDetailActivity extends AppCompatActivity {

    private static final int REQ_INSTALL_PERMISSION = 1001;

    private AppModel app;
    private Button actionBtn;
    private ProgressBar downloadProgress;
    private TextView progressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_detail);

        try {
            app = (AppModel) getIntent().getSerializableExtra("app");
        } catch (Exception e) {
            finish();
            return;
        }

        bindUI();
        updateButtonState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtonState();
    }

    private void bindUI() {
        ImageView icon = findViewById(R.id.appIcon);
        TextView name = findViewById(R.id.appName);
        TextView version = findViewById(R.id.appVersion);
        TextView category = findViewById(R.id.appCategory);
        TextView desc = findViewById(R.id.appDesc);
        actionBtn = findViewById(R.id.actionBtn);
        downloadProgress = findViewById(R.id.downloadProgress);
        progressText = findViewById(R.id.downloadProgressText);

        RecyclerView screenshots = findViewById(R.id.screenshotList);
        screenshots.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        screenshots.setAdapter(new ScreenshotAdapter(app.screenshots));

        name.setText(app.name);
        version.setText("Version " + app.version);
        category.setText(app.category);
        desc.setText(app.description);

        Picasso.get().load(app.icon).into(icon);
    }

    /* ================= INSTALL / UPDATE LOGIC ================= */
    private void updateButtonState() {
        if (!isInstalled()) {
            actionBtn.setText("Install");
            actionBtn.setOnClickListener(v -> startDownload());
            return;
        }

        int installedVersion = getInstalledVersionCode();
        if (installedVersion < app.versionCode) {
            actionBtn.setText("Update");
            actionBtn.setOnClickListener(v -> startDownload());
        } else {
            actionBtn.setText("Open");
            actionBtn.setOnClickListener(v -> openApp());
        }
    }

    private boolean isInstalled() {
        try {
            getPackageManager().getPackageInfo(app.packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int getInstalledVersionCode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) getPackageManager().getPackageInfo(app.packageName, 0).getLongVersionCode();
            } else {
                return getPackageManager().getPackageInfo(app.packageName, 0).versionCode;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    /* ================= DOWNLOAD ================= */
    private void startDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !getPackageManager().canRequestPackageInstalls()) {

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_INSTALL_PERMISSION);
            return;
        }

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(app.apk));
        req.setTitle(app.name);
        req.setDescription("Downloading " + app.name);
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                "NeunixStore/" + app.packageName + "-" + app.version + ".apk");

        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = dm.enqueue(req);

        downloadProgress.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progressText.setText("Download started...");

        // Optional: Track download progress in a thread
        new Thread(() -> {
            boolean downloading = true;
            while (downloading) {
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(downloadId);
                Cursor cursor = dm.query(q);
                if (cursor != null && cursor.moveToFirst()) {
                    int bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    final int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);

                    runOnUiThread(() -> progressText.setText("Downloading: " + progress + "%"));

                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        downloading = false;
                        runOnUiThread(() -> {
                            downloadProgress.setVisibility(View.GONE);
                            progressText.setVisibility(View.GONE);
                        });
                    }
                    cursor.close();
                }
            }
        }).start();
    }

    /* ================= OPEN APP ================= */
    private void openApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (intent != null) startActivity(intent);
        else Toast.makeText(this, "App not launchable", Toast.LENGTH_SHORT).show();
    }

    /* ================= PERMISSION CALLBACK ================= */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_INSTALL_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    getPackageManager().canRequestPackageInstalls()) {
                startDownload();
            } else {
                Toast.makeText(this, "Install permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }
}