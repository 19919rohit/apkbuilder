package com.neunix.appstore;

import android.app.DownloadManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
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
            app = AppModel.fromJson(getIntent().getStringExtra("app"));
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
        updateButtonState(); // refresh button state when returning
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
        screenshots.setLayoutManager(
                new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        screenshots.setAdapter(new ScreenshotAdapter(app.screenshots));

        name.setText(app.name);
        version.setText("Version " + app.version);
        category.setText(app.category);
        desc.setText(app.description);

        Picasso.get().load(app.icon).into(icon);
    }

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
                return (int) getPackageManager()
                        .getPackageInfo(app.packageName, 0)
                        .getLongVersionCode();
            } else {
                return getPackageManager()
                        .getPackageInfo(app.packageName, 0)
                        .versionCode;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private void startDownload() {
        // disable button
        actionBtn.setEnabled(false);
        actionBtn.setText("Downloading…");

        downloadProgress.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progressText.setText("0%");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !getPackageManager().canRequestPackageInstalls()) {

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_INSTALL_PERMISSION);
            return;
        }

        DownloadManager.Request req =
                new DownloadManager.Request(Uri.parse(app.apk));

        req.setTitle(app.name);
        req.setDescription("Downloading " + app.name);
        req.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        req.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "NeunixStore/" + app.packageName + "-" + app.version + ".apk"
        );

        DownloadManager dm =
                (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        long downloadId = dm.enqueue(req);

        // Optional: track progress in another thread
        new Thread(() -> {
            boolean downloading = true;
            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(downloadId);

            while (downloading) {
                Cursor cursor = dm.query(q);
                if (cursor != null && cursor.moveToFirst()) {
                    int bytesDownloaded =
                            cursor.getInt(cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytesTotal =
                            cursor.getInt(cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    final int progress = bytesTotal > 0
                            ? (int) ((bytesDownloaded * 100L) / bytesTotal)
                            : 0;

                    runOnUiThread(() -> progressText.setText(progress + "%"));

                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL
                            || status == DownloadManager.STATUS_FAILED) {
                        downloading = false;
                    }
                    cursor.close();
                }
            }
            runOnUiThread(() -> {
                downloadProgress.setVisibility(View.GONE);
                progressText.setVisibility(View.GONE);
                updateButtonState();
            });
        }).start();
    }

    private void openApp() {
        Intent intent =
                getPackageManager().getLaunchIntentForPackage(app.packageName);

        if (intent != null) startActivity(intent);
        else Toast.makeText(this, "App not launchable", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_INSTALL_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    getPackageManager().canRequestPackageInstalls()) {
                startDownload();
            } else {
                Toast.makeText(this,
                        "Install permission denied",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}