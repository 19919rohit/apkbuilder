package neunix.pageflow;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {

    // =========================================================
    // CACHE DIR NAME
    // =========================================================

    private static final String CACHE_DIR = "pdf_cache";

    // =========================================================
    // GET FILE FROM URI
    // Copies the content URI to a real file in our cache dir.
    // PdfRenderer requires a real file path — not a stream.
    // =========================================================

    public static File getFileFromUri(Context context, Uri uri) {

        String fileName = getFileName(context, uri);

        File cacheDir = getPdfCacheDir(context);

        File outFile = new File(cacheDir, sanitizeFileName(fileName));

        // If an identical file is already cached, reuse it
        if (outFile.exists() && outFile.length() > 0) {
            return outFile;
        }

        copyUriToFile(context, uri, outFile);

        return outFile;
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
            if (f.lastModified() < cutoff) {
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

        if (target.exists()) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
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

        try (
            InputStream  in  = context.getContentResolver().openInputStream(uri);
            OutputStream out = new FileOutputStream(outFile)
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

        } catch (Exception e) {
            // Clean up partial file on failure
            if (outFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            }
            throw new RuntimeException("Failed to copy URI to cache: " + e.getMessage(), e);
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