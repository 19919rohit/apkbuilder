package neunix.pagevibe;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;

public class FileUtils {

    // =========================================================
    // CACHE DIR NAME
    // =========================================================

    private static final String CACHE_DIR = "pdf_cache";

    // =========================================================
    // PER-DESTINATION-FILE LOCKS
    //
    // Without this, two concurrent callers resolving the SAME uri race on
    // the same cache file. This genuinely happens in this app: opening a
    // PDF adds it to Recents and refreshes the home screen, which kicks
    // off a background thumbnail load for that same brand-new file at
    // almost the same instant PdfActivity is opening it for real. Both
    // paths call getFileFromUri() for the same destination with no
    // coordination — for a large/slow-to-copy file, both can see "not
    // cached yet" and both start writing the same .tmp path, and
    // whichever finishes (or renames) first can leave the other reading
    // a truncated or momentarily-missing file. That's what caused "PDF
    // failed to load" on the FIRST open of a large file while the SAME
    // file opened perfectly fine moments later once the race settled.
    //
    // Synchronizing per absolute destination path means the second
    // caller simply waits for the first to finish, then reuses the now-
    // complete, validated cache file instead of racing it.
    // =========================================================

    private static final ConcurrentHashMap<String, Object> fileLocks = new ConcurrentHashMap<>();

    private static Object lockFor(String absolutePath) {
        return fileLocks.computeIfAbsent(absolutePath, k -> new Object());
    }

    // =========================================================
    // GET FILE FROM URI
    // Copies the content URI to a real file in our cache dir.
    // PdfRenderer requires a real file path — not a stream.
    // =========================================================

    public static File getFileFromUri(Context context, Uri uri) {

        String fileName = getFileName(context, uri);

        File cacheDir = getPdfCacheDir(context);

        File outFile = new File(cacheDir, sanitizeFileName(fileName));

        synchronized (lockFor(outFile.getAbsolutePath())) {

            // Reuse an identical cached file ONLY if its size matches the
            // source. A previous interrupted copy (app killed mid-write,
            // storage full, etc.) can leave a partial file that exists()
            // and has length() > 0 but is truncated — causing PdfRenderer
            // to fail on a perfectly valid source PDF. Comparing against
            // the real source length avoids ever trusting a corrupt cache
            // entry.
            long sourceLength = getUriLength(context, uri);
            if (outFile.exists() && outFile.length() > 0
                    && (sourceLength <= 0 || outFile.length() == sourceLength)) {
                return outFile;
            }

            copyUriToFile(context, uri, outFile);

            return outFile;
        }
    }

    /**
     * Best-effort lookup of the source content's byte length, used to
     * validate cache integrity. Returns -1 if unknown (some providers
     * don't report size), in which case callers should not rely on it
     * as the sole correctness check.
     */
    private static long getUriLength(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{ OpenableColumns.SIZE }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) {
                    return cursor.getLong(idx);
                }
            }
        } catch (Exception ignored) { }
        return -1L;
    }

    // =========================================================
    // GET FILE NAME
    // =========================================================

    public static String getFileName(Context context, Uri uri) {

        // Try querying the content resolver first (most reliable)
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{ OpenableColumns.DISPLAY_NAME },
                null, null, null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) { }

        // Fall back to the last path segment
        String path = uri.getPath();
        if (path != null && path.contains("/")) {
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (!last.isEmpty()) return last;
        }

        return "document.pdf";
    }

    // =========================================================
    // CACHE MANAGEMENT
    // =========================================================

    /**
     * Deletes all cached PDF files older than maxAgeMs.
     * Call this from a background thread — e.g. on app startup.
     */
    public static void evictStaleCacheFiles(Context context, long maxAgeMs) {

        File cacheDir = getPdfCacheDir(context);

        File[] files = cacheDir.listFiles();
        if (files == null) return;

        long cutoff = System.currentTimeMillis() - maxAgeMs;

        for (File f : files) {
            // Always remove leftover .tmp files regardless of age — they are
            // never valid, complete cache entries (see copyUriToFile).
            boolean isStaleTemp = f.getName().endsWith(".tmp");
            if (isStaleTemp || f.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /**
     * Deletes the cached copy of a specific URI.
     * Call when the user removes a file from recent list.
     */
    public static void evictCacheForUri(Context context, Uri uri) {

        String fileName = getFileName(context, uri);
        File   target   = new File(getPdfCacheDir(context),
                                   sanitizeFileName(fileName));

        synchronized (lockFor(target.getAbsolutePath())) {
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
        }
    }

    /**
     * Returns total size of the PDF cache in bytes.
     * Useful for a "Clear cache" settings option.
     */
    public static long getCacheSizeBytes(Context context) {

        File   cacheDir = getPdfCacheDir(context);
        File[] files    = cacheDir.listFiles();
        if (files == null) return 0L;

        long total = 0L;
        for (File f : files) total += f.length();
        return total;
    }

    /**
     * Wipes the entire PDF cache.
     */
    public static void clearCache(Context context) {

        File   cacheDir = getPdfCacheDir(context);
        File[] files    = cacheDir.listFiles();
        if (files == null) return;

        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    // =========================================================
    // INTERNAL
    // =========================================================

    private static File getPdfCacheDir(Context context) {
        File dir = new File(context.getCacheDir(), CACHE_DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private static void copyUriToFile(Context context, Uri uri, File outFile) {

        File tempFile = new File(outFile.getParentFile(), outFile.getName() + ".tmp");

        try (
            InputStream  in  = context.getContentResolver().openInputStream(uri);
            OutputStream out = new FileOutputStream(tempFile)
        ) {
            if (in == null) {
                throw new RuntimeException("Cannot open input stream for URI: " + uri);
            }

            byte[] buffer = new byte[8192];
            int    read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            out.flush();
            ((FileOutputStream) out).getFD().sync(); // force bytes to disk before rename

        } catch (Exception e) {
            // Clean up partial temp file on failure
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
            throw new RuntimeException("Failed to copy URI to cache: " + e.getMessage(), e);
        }

        // Atomic-ish swap: only now does the "real" cache file exist,
        // and only as a fully-written copy. If the app dies before this
        // line, only the .tmp file is left behind (never the real name),
        // so getFileFromUri() will never mistake a partial copy for a
        // complete one.
        if (outFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
        }
        if (!tempFile.renameTo(outFile)) {
            throw new RuntimeException("Failed to finalize cached PDF file");
        }
    }

    /**
     * Strips characters that are illegal in file names on Android/Linux.
     * Replaces spaces with underscores for cleanliness.
     */
    private static String sanitizeFileName(String name) {
        return name
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .trim();
    }
}