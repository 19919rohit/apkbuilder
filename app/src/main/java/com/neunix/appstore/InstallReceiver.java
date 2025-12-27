package com.neunix.appstore;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

public class InstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()))
            return;

        long id = intent.getLongExtra(
                DownloadManager.EXTRA_DOWNLOAD_ID, -1);

        DownloadManager dm =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        Cursor cursor = dm.query(
                new DownloadManager.Query().setFilterById(id));

        if (cursor == null || !cursor.moveToFirst()) return;

        String uriString = cursor.getString(
                cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_LOCAL_URI));

        cursor.close();

        if (uriString == null) return;

        File apkFile = new File(Uri.parse(uriString).getPath());

        Uri apkUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".provider",
                apkFile
        );

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(
                apkUri,
                "application/vnd.android.package-archive"
        );
        installIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        context.startActivity(installIntent);
    }
}