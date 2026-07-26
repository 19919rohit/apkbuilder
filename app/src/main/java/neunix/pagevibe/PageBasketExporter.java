package neunix.pageflow;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Merges an ordered list of basket entries — each potentially from a
 * DIFFERENT source PDF — into one new, standalone PDF.
 *
 * FIXED (previously produced image-only, non-searchable output): the old
 * implementation rendered every source page to a bitmap via PdfCore
 * (PDFium) and drew that bitmap onto a fresh android.graphics.pdf.
 * PdfDocument page. A bitmap carries no text layer at all — TTS, search,
 * and copy-paste on the exported file all correctly reported "no text",
 * because there genuinely was none. Android's built-in PdfDocument can
 * only draw NEW content via Canvas; it cannot copy another PDF's actual
 * content stream.
 *
 * This now uses PDFBox-Android's PDDocument.importPage(PDPage) — which
 * copies the REAL page object (content stream, fonts, embedded text,
 * vector graphics — everything) into the output document, preserving
 * genuine extractable text. PDFium is untouched and still used for all
 * rendering/reading/search elsewhere in the app; this is the one place
 * that needs true page-copying rather than rendering, so it uses the
 * one library capable of that.
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
        try {
            int added = 0;
            for (PageBasketManager.BasketEntry entry : entries) {
                try {
                    File srcFile = FileUtils.getFileFromUri(context, entry.sourceUri);
                    PDDocument srcDoc = null;
                    try {
                        srcDoc = PDDocument.load(srcFile);
                        if (entry.pageIndex < 0 || entry.pageIndex >= srcDoc.getNumberOfPages()) continue;
                        PDPage sourcePage = srcDoc.getPage(entry.pageIndex);
                        // importPage copies the page's content stream and
                        // resources into outDoc — this is what preserves
                        // real, extractable text (unlike bitmap rendering).
                        outDoc.importPage(sourcePage);
                        added++;
                    } finally {
                        if (srcDoc != null) srcDoc.close();
                    }
                } catch (Throwable pageErr) {
                    // Skip a page that fails to import rather than aborting
                    // the whole merge — one corrupt source PDF shouldn't
                    // ruin a basket built from several good ones.
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
            outDoc.close();
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