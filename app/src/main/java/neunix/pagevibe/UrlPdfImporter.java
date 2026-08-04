package neunix.pagevibe.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads a PDF from a direct URL into Documents/PageVibe/PDF and hands
 * back a Uri ready for the Library. Every realistic failure mode is
 * caught and reported as a clear message rather than a raw stack trace:
 * malformed URL, unreachable host, timeout, non-200 response, a response
 * that isn't actually a PDF (checked via the real %PDF- file signature,
 * not just the URL's file extension), and a runaway/oversized download.
 */
public class UrlPdfImporter {

    public interface Callback {
        void onSuccess(Uri savedUri, String fileName);
        void onError(String message);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "UrlImport"));
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS    = 30_000;
    private static final long MAX_BYTES         = 200L * 1024 * 1024;

    public static void importFromUrl(Context context, String urlString, Callback callback) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            HttpURLConnection connection = null;
            File tempFile = null;
            try {
                String trimmed = urlString == null ? "" : urlString.trim();
                if (trimmed.isEmpty()) { callback.onError("Please enter a URL"); return; }
                if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                    trimmed = "https://" + trimmed;
                }

                URL url = new URL(trimmed);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) PageVibe");
                connection.connect();

                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    callback.onError("Server returned an error (HTTP " + code + ")");
                    return;
                }

                String fileName = guessFileName(url);
                File cacheDir = new File(appContext.getCacheDir(), "url_imports");
                if (!cacheDir.exists()) //noinspection ResultOfMethodCallIgnored
                    cacheDir.mkdirs();
                tempFile = new File(cacheDir, "import_" + System.currentTimeMillis() + ".pdf");

                long totalRead = 0L;
                boolean checkedSignature = false;
                boolean looksLikePdf = false;
                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        totalRead += read;
                        if (totalRead > MAX_BYTES) {
                            callback.onError("File is too large to import (over 200MB)");
                            return;
                        }
                        out.write(buffer, 0, read);
                        if (!checkedSignature && read >= 5) {
                            looksLikePdf = new String(buffer, 0, 5, "US-ASCII").equals("%PDF-");
                            checkedSignature = true;
                        }
                    }
                }

                if (totalRead == 0) { callback.onError("The download was empty"); return; }
                if (!looksLikePdf)  { callback.onError("That link doesn't point to a valid PDF file"); return; }

                Uri savedUri = copyToDocuments(appContext, tempFile, fileName);
                if (savedUri == null) { callback.onError("Could not save the file to Documents"); return; }
                callback.onSuccess(savedUri, fileName);

            } catch (SocketTimeoutException e) {
                callback.onError("The download timed out — check your connection and try again");
            } catch (UnknownHostException e) {
                callback.onError("Couldn't reach that address — check the URL and your connection");
            } catch (MalformedURLException e) {
                callback.onError("That doesn't look like a valid URL");
            } catch (Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Import failed");
            } finally {
                if (connection != null) connection.disconnect();
                if (tempFile != null) //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
            }
        });
    }

    private static String guessFileName(URL url) {
        String path = url.getPath();
        String name = (path != null && path.contains("/")) ? path.substring(path.lastIndexOf('/') + 1) : "";
        if (name.isEmpty() || !name.toLowerCase(Locale.US).endsWith(".pdf")) {
            name = "Imported_" + System.currentTimeMillis() + ".pdf";
        }
        return name;
    }

    private static Uri copyToDocuments(Context context, File sourceFile, String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PageVibe/PDF");
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 1);

            Uri collection = MediaStore.Files.getContentUri("external");
            Uri itemUri = context.getContentResolver().insert(collection, values);
            if (itemUri == null) return null;

            try (InputStream in = new FileInputStream(sourceFile);
                 OutputStream out = context.getContentResolver().openOutputStream(itemUri)) {
                if (out == null) return null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            values.clear();
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 0);
            context.getContentResolver().update(itemUri, values, null, null);
            return itemUri;
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "PageVibe/PDF");
            if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            File destFile = new File(dir, fileName);
            int counter = 1;
            while (destFile.exists()) {
                String base = fileName.replaceAll("(?i)\\.pdf$", "");
                destFile = new File(dir, base + " (" + counter + ").pdf");
                counter++;
            }
            try (InputStream in = new FileInputStream(sourceFile);
                 OutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", destFile);
        }
    }
}