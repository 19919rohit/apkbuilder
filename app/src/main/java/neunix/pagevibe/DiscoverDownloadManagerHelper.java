package neunix.pagevibe;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

/**
 * Thin wrapper around Android's system DownloadManager — no custom
 * download engine, no custom progress protocol. DownloadManager already
 * handles pause/resume, network-loss recovery, and reboot survival as a
 * system service; this class's only job is to correctly re-derive
 * current state from it and remember which download ID belongs to which
 * book (SharedPreferences, one long per book — nothing ever written to
 * Firestore).
 */
public class DiscoverDownloadManagerHelper {

    public enum DownloadState { NOT_DOWNLOADED, PENDING, DOWNLOADING, PAUSED, DOWNLOADED, FAILED }

    private static final String PREFS_NAME = "pagevibe_prefs";
    private static final String KEY_PREFIX = "discover_download_id_";

    private final Context appContext;
    private final DownloadManager downloadManager;

    public DiscoverDownloadManagerHelper(Context context) {
        this.appContext = context.getApplicationContext();
        this.downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /** Returns the download id, or -1 on failure. No-ops (returns the
     *  existing id) if this book already has an active or completed
     *  download — this is the duplicate-download guard. */
    public long startDownload(DiscoverBook book) {
        DownloadState existing = getState(book.getBookId());
        if (existing != DownloadState.NOT_DOWNLOADED && existing != DownloadState.FAILED) {
            Long existingId = getStoredId(book.getBookId());
            return existingId != null ? existingId : -1L;
        }
        if (!book.hasValidPdfUrl()) return -1L;
        if (downloadManager == null) return -1L;

        try {
            Uri source = Uri.parse(book.getPdfUrl());
            String fileName = sanitizeFileName(book.getTitle()) + ".pdf";

            DownloadManager.Request request = new DownloadManager.Request(source);
            request.setTitle(book.getTitle());
            request.setDescription("Downloading via PageVibe Discover");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PageVibe/" + fileName);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

            long id = downloadManager.enqueue(request);
            storeId(book.getBookId(), id);
            return id;
        } catch (Throwable t) {
            return -1L;
        }
    }

    public DownloadState getState(String bookId) {
        Long id = getStoredId(bookId);
        if (id == null || downloadManager == null) return DownloadState.NOT_DOWNLOADED;

        try (Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) {
                clearStoredId(bookId); // row was cleared out from under us
                return DownloadState.NOT_DOWNLOADED;
            }
            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : -1;

            switch (status) {
                case DownloadManager.STATUS_SUCCESSFUL: return DownloadState.DOWNLOADED;
                case DownloadManager.STATUS_RUNNING:    return DownloadState.DOWNLOADING;
                case DownloadManager.STATUS_PENDING:    return DownloadState.PENDING;
                case DownloadManager.STATUS_PAUSED:     return DownloadState.PAUSED;
                case DownloadManager.STATUS_FAILED:
                    clearStoredId(bookId); // allow the user to retry cleanly
                    return DownloadState.FAILED;
                default: return DownloadState.NOT_DOWNLOADED;
            }
        } catch (Throwable t) {
            return DownloadState.NOT_DOWNLOADED;
        }
    }

    /** 0-100. Returns 0 if unknown or not currently downloading. */
    public int getProgress(String bookId) {
        Long id = getStoredId(bookId);
        if (id == null || downloadManager == null) return 0;

        try (Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) return 0;
            int soFarIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
            int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
            long soFar = soFarIdx >= 0 ? cursor.getLong(soFarIdx) : 0L;
            long total = totalIdx >= 0 ? cursor.getLong(totalIdx) : 0L;
            if (total <= 0) return 0;
            return (int) Math.min(100, Math.round((soFar * 100.0) / total));
        } catch (Throwable t) {
            return 0;
        }
    }

    /** A ready-to-open content:// Uri for a completed download — hand
     *  this straight to PdfActivity, no FileProvider needed since
     *  DownloadManager's own provider already exposes a safe content
     *  Uri. Returns null if not downloaded. */
    public Uri getDownloadedUri(String bookId) {
        Long id = getStoredId(bookId);
        if (id == null || downloadManager == null) return null;
        try { return downloadManager.getUriForDownloadedFile(id); }
        catch (Throwable t) { return null; }
    }

    public boolean matchesStoredId(String bookId, long downloadId) {
        Long stored = getStoredId(bookId);
        return stored != null && stored == downloadId;
    }

    private Long getStoredId(String bookId) {
        String key = KEY_PREFIX + bookId;
        SharedPreferences p = prefs();
        return p.contains(key) ? p.getLong(key, -1L) : null;
    }

    private void storeId(String bookId, long id) {
        prefs().edit().putLong(KEY_PREFIX + bookId, id).apply();
    }

    private void clearStoredId(String bookId) {
        prefs().edit().remove(KEY_PREFIX + bookId).apply();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "document";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}