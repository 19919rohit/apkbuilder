package neunix.pagevibe;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Ensures a PDF opened via an external "Open With" intent has a durable,
 * permanent copy on the device. content:// URIs handed to us by other
 * apps (email clients, browsers, file managers, chat apps) frequently
 * point at TEMPORARY cache files that can be deleted the moment the
 * source app closes or clears its cache — silently breaking re-opening
 * from PageVibe's own Library later, even though the file appeared to
 * open fine the first time.
 *
 * This copies such files into the public Documents/PageVibe/PDF folder
 * once, and returns a URI to that permanent copy for PageVibe to actually
 * open and register into the Library — so from that point on, the file
 * genuinely belongs to the device, not to whichever app happened to hand
 * it to us.
 */
public class ExternalPdfPersister {

    private static final String SUBFOLDER = "PageVibe/PDF";

    public interface Callback {
        void onPersisted(Uri persistedUri, String fileName);
        void onFailed(Uri originalUri, String originalName);
    }

    public static void persistIfNeeded(Context context, Uri sourceUri, String suggestedName, Callback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                String safeName = sanitize(suggestedName);
                Uri persisted = copyToDocuments(appContext, sourceUri, safeName);
                if (persisted != null) {
                    callback.onPersisted(persisted, safeName);
                } else {
                    callback.onFailed(sourceUri, suggestedName);
                }
            } catch (Throwable t) {
                // Never let a persistence failure block the user from
                // reading the file at all — fall back to opening the
                // original (possibly transient) URI directly.
                callback.onFailed(sourceUri, suggestedName);
            }
        }, "PdfPersist").start();
    }

    private static Uri copyToDocuments(Context context, Uri sourceUri, String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + SUBFOLDER);
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 1);

            Uri collection = MediaStore.Files.getContentUri("external");
            Uri itemUri = context.getContentResolver().insert(collection, values);
            if (itemUri == null) return null;

            try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
                 OutputStream out = context.getContentResolver().openOutputStream(itemUri)) {
                if (in == null || out == null) return null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }

            values.clear();
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 0);
            context.getContentResolver().update(itemUri, values, null, null);
            return itemUri;
        } else {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), SUBFOLDER);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            File destFile = new File(dir, fileName);
            int counter = 1;
            while (destFile.exists()) {
                String base = fileName.replaceAll("(?i)\\.pdf$", "");
                destFile = new File(dir, base + " (" + counter + ").pdf");
                counter++;
            }
            try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
                 OutputStream out = new FileOutputStream(destFile)) {
                if (in == null) return null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            return androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", destFile);
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) name = "document.pdf";
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (!cleaned.toLowerCase(Locale.US).endsWith(".pdf")) cleaned = cleaned + ".pdf";
        return cleaned;
    }
}