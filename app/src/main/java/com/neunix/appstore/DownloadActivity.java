package com.neunix.appstore;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private DownloadedAppAdapter adapter;
    private final List<DownloadedApp> downloadedApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        recyclerView = findViewById(R.id.downloadRecycler);
        emptyText = findViewById(R.id.emptyText);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadedAppAdapter(downloadedApps, this::installApk);
        recyclerView.setAdapter(adapter);

        loadDownloadedApps();
    }

    /* ---------------- LOAD DOWNLOADS ---------------- */

    private void loadDownloadedApps() {
        downloadedApps.clear();

        DownloadManager dm =
                (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);

        Cursor cursor = dm.query(query);

        if (cursor != null) {
            while (cursor.moveToNext()) {

                String localUri = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_LOCAL_URI));

                String title = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_TITLE));

                // Optional: only show NeunixStore downloads
                if (localUri != null && localUri.contains("NeunixStore")) {
                    downloadedApps.add(new DownloadedApp(title, localUri));
                }
            }
            cursor.close();
        }

        updateUI();
    }

    private void updateUI() {
        if (downloadedApps.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    /* ---------------- INSTALL APK ---------------- */

    private void installApk(DownloadedApp app) {
        try {
            File file = new File(Uri.parse(app.localUri).getPath());

            if (!file.exists()) {
                Toast.makeText(this,
                        "APK file missing",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Uri apkUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                    apkUri,
                    "application/vnd.android.package-archive"
            );
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(this,
                    "Unable to install APK",
                    Toast.LENGTH_LONG).show();
        }
    }
}