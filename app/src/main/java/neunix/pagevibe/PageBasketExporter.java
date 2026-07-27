package neunix.pagevibe;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Merges an ordered list of basket entries — each potentially from a
 * DIFFERENT source PDF — into one new, standalone PDF using PDFBox's
 * PDDocument.importPage(), which preserves real, extractable text/fonts/
 * vector content (unlike bitmap rendering).
 *
 * ROBUSTNESS FIX (was the source of "COSStream has been closed and cannot
 * be read" crashes): every source PDDocument is now kept open for the
 * ENTIRE merge — not closed right after its page is imported. PDFBox's
 * importPage() does not necessarily deep-copy the page's underlying
 * content stream at call time; closing the source document too early can
 * leave the imported page referencing an already-closed COSStream, which
 * only surfaces later at save() time. All source documents are now
 * closed together, in one place, only after the merged output has
 * actually been written to disk.
 */
public class PageBasketExporter {

    public interface Callback {
        void onSuccess(Uri savedUri, String finalFileName);
        void onError(String message);
    }

    private static final ExecutorService exportExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "BasketExport"));

    private static final AtomicBoolean resourceLoaderInitialized = new AtomicBoolean(false);

    public static void exportToDocuments(Context context, List<PageBasketManager.BasketEntry> entries,
                                          String desiredName, Callback callback) {
        Context appContext = context.getApplicationContext();
        if (resourceLoaderInitialized.compareAndSet(false, true)) {
            try { PDFBoxResourceLoader.init(appContext); } catch (Throwable ignored) {}
        }

        exportExecutor.execute(() -> {
            File tempFile = null;
            try {
                tempFile = mergeToTempFile(appContext, entries);
                String finalName = sanitizeName(desiredName) + ".pdf";
                Uri savedUri = writeToDocuments(appContext, tempFile, finalName);
                callback.onSuccess(savedUri, finalName);
            } catch (Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Export failed");
            } finally {
                if (tempFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        });
    }

    private static File mergeToTempFile(Context context, List<PageBasketManager.BasketEntry> entries) throws Exception {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalStateException("Basket is empty");
        }

        PDDocument outDoc = new PDDocument();
        // Every opened source document is tracked here and kept alive
        // until save() completes — see class doc for why this fixes the
        // "COSStream has been closed" crash.
        List<PDDocument> openSourceDocs = new ArrayList<>();

        try {
            int added = 0;
            for (PageBasketManager.BasketEntry entry : entries) {
                try {
                    File srcFile = FileUtils.getFileFromUri(context, entry.sourceUri);
                    PDDocument srcDoc = PDDocument.load(srcFile);
                    openSourceDocs.add(srcDoc);

                    if (entry.pageIndex < 0 || entry.pageIndex >= srcDoc.getNumberOfPages()) continue;
                    PDPage sourcePage = srcDoc.getPage(entry.pageIndex);
                    outDoc.importPage(sourcePage);
                    added++;
                } catch (Throwable pageErr) {
                    // Skip a page/source that fails to load or import
                    // rather than aborting the whole merge — one corrupt
                    // source PDF shouldn't ruin a basket built from
                    // several good ones.
                }
            }

            if (added == 0) {
                throw new IllegalStateException("None of the basket pages could be merged");
            }

            File outDir = new File(context.getCacheDir(), "basket_exports");
            if (!outDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outDir.mkdirs();
            }
            File tempFile = new File(outDir, "temp_" + System.currentTimeMillis() + ".pdf");
            outDoc.save(tempFile);
            return tempFile;
        } finally {
            for (PDDocument doc : openSourceDocs) {
                try { doc.close(); } catch (Throwable ignored) {}
            }
            try { outDoc.close(); } catch (Throwable ignored) {}
        }
    }

    private static Uri writeToDocuments(Context context, File sourceFile, String displayName) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 1);

            Uri collection = MediaStore.Files.getContentUri("external");
            Uri itemUri = context.getContentResolver().insert(collection, values);
            if (itemUri == null) throw new IllegalStateException("Could not create a Documents entry");

            try {
                try (OutputStream out = context.getContentResolver().openOutputStream(itemUri);
                     InputStream in = new FileInputStream(sourceFile)) {
                    if (out == null) throw new IllegalStateException("Could not open output stream");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }

                values.clear();
                values.put(MediaStore.Files.FileColumns.IS_PENDING, 0);
                context.getContentResolver().update(itemUri, values, null, null);

                return itemUri;
            } catch (Exception writeErr) {
                // Robustness: don't leave a zero-byte/pending, corrupt
                // MediaStore row behind if the copy itself failed partway.
                try { context.getContentResolver().delete(itemUri, null, null); } catch (Throwable ignored) {}
                throw writeErr;
            }
        } else {
            File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!docsDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                docsDir.mkdirs();
            }
            File destFile = new File(docsDir, displayName);
            int counter = 1;
            while (destFile.exists()) {
                String base = displayName.replaceAll("(?i)\\.pdf$", "");
                destFile = new File(docsDir, base + " (" + counter + ").pdf");
                counter++;
            }
            try (OutputStream out = new FileOutputStream(destFile);
                 InputStream in = new FileInputStream(sourceFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", destFile);
        }
    }

    private static String sanitizeName(String name) {
        if (name == null) return "PageVibe Basket";
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        cleaned = cleaned.replaceAll("(?i)\\.pdf$", "");
        return cleaned.isEmpty() ? "PageVibe Basket" : cleaned;
    }
}