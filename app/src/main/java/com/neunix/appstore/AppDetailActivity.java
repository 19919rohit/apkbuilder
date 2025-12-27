package com.neunix.appstore;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.io.File;

public class AppDetailActivity extends AppCompatActivity {

    private static final int REQ_INSTALL_PERMISSION = 1001;

    private AppModel app;
    private Button actionBtn;
    private ProgressBar progressBar;
    private TextView progressText;

    private long downloadId = -1;
    private DownloadManager dm;

    private BroadcastReceiver downloadReceiver;

    @Override
    protected void onResume() {
        super.onResume();
        updateButtonState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (downloadReceiver != null) {
            unregisterReceiver(downloadReceiver);
        }
    }

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

    private void bindUI() {

        ImageView icon = findViewById(R.id.appIcon);
        TextView name = findViewById(R.id.appName);
        TextView version = findViewById(R.id.appVersion);
        TextView category = findViewById(R.id.appCategory);
        TextView desc = findViewById(R.id.appDesc);
        TextView sizeText = findViewById(R.id.appSize);
        progressBar = findViewById(R.id.downloadProgress);
        progressText = findViewById(R.id.downloadProgressText);
        actionBtn = findViewById(R.id.actionBtn);

        RecyclerView screenshots = findViewById(R.id.screenshotList);
        screenshots.setLayoutManager(
                new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        screenshots.setAdapter(new ScreenshotAdapter(app.screenshots));

        name.setText(app.name);
        version.setText("Version " + app.version);
        category.setText(app.category);
        desc.setText(app.description);
        sizeText.setText("Size: " + app.size);

        Picasso.get().load(app.icon).into(icon);
    }

    /* ================= INSTALL / UPDATE LOGIC ================= */

    private void updateButtonState() {
        actionBtn.setEnabled(true);

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

    /* ================= DOWNLOAD ================= */

    private void startDownload() {

        actionBtn.setEnabled(false);
        actionBtn.setText("Downloading…");
        progressBar.setVisibility(ProgressBar.VISIBLE);
        progressText.setVisibility(TextView.VISIBLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !getPackageManager().canRequestPackageInstalls()) {

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_INSTALL_PERMISSION);
            return;
        }

        if (app.apk == null || !app.apk.startsWith("http")) {
            Toast.makeText(this, "Invalid download link", Toast.LENGTH_LONG).show();
            updateButtonState();
            return;
        }

        // Ensure directory exists
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "NeunixStore");
        if (!dir.exists()) dir.mkdirs();

        dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(app.apk));
        req.setTitle(app.name);
        req.setDescription("Downloading " + app.name);
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                "NeunixStore/" + app.packageName + "-" + app.version + ".apk");

        downloadId = dm.enqueue(req);

        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();

        trackDownloadProgress();
    }

    private void trackDownloadProgress() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor cursor = dm.query(query);

                if (cursor != null && cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_STATUS));

                    int bytesDownloaded = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytesTotal = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (bytesTotal > 0) {
                        int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);
                        progressBar.setProgress(progress);
                        progressText.setText(progress + "%");
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL ||
                        status == DownloadManager.STATUS_FAILED) {
                        progressBar.setVisibility(ProgressBar.GONE);
                        progressText.setVisibility(TextView.GONE);
                        updateButtonState();
                    }
                }
                if (cursor != null) cursor.close();
            }
        };

        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
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
                Toast.makeText(this,
                        "Install permission denied",
                        Toast.LENGTH_LONG).show();
                updateButtonState();
            }
        }
    }
}