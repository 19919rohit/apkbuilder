package neunix.pagevibe;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Merges an ordered list of basket entries — each potentially from a
 * DIFFERENT source PDF — into one new, standalone PDF, then saves it
 * directly into the device's public Documents folder:
 *  - Android 10+ (API 29+): via MediaStore, no permission required.
 *  - Android 9 and below: direct file write, gated behind a runtime
 *    WRITE_EXTERNAL_STORAGE permission request (handled by the caller).
 *
 * Rendering uses Android's built-in android.graphics.pdf.PdfDocument
 * writer — no native PDF-writing library needed, since PDFium already
 * renders each source page to a bitmap and PdfDocument draws that bitmap
 * onto a fresh page.
 */
public class PageBasketExporter {

    public interface Callback {
        void onSuccess(Uri savedUri, String finalFileName);
        void onError(String message);
    }

    private static final int EXPORT_WIDTH  = 1240;
    private static final int EXPORT_HEIGHT = 1754; // A4-ish portrait ratio

    private static final ExecutorService exportExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "BasketExport"));

    public static void exportToDocuments(Context context, List<PageBasketManager.BasketEntry> entries,
                                          String desiredName, Callback callback) {
        Context appContext = context.getApplicationContext();
        exportExecutor.execute(() -> {
            File tempFile = null;
            try {
                tempFile = renderToTempFile(appContext, entries);
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

    private static File renderToTempFile(Context context, List<PageBasketManager.BasketEntry> entries) throws Exception {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalStateException("Basket is empty");
        }

        PdfDocument outDoc = new PdfDocument();
        try {
            int pageNumber = 1;
            for (PageBasketManager.BasketEntry entry : entries) {
                Bitmap rendered = renderSourcePage(context, entry.sourceUri, entry.pageIndex);
                if (rendered == null) continue;

                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        rendered.getWidth(), rendered.getHeight(), pageNumber).create();
                PdfDocument.Page page = outDoc.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(rendered, 0f, 0f, null);
                outDoc.finishPage(page);
                pageNumber++;

                if (!rendered.isRecycled()) rendered.recycle();
            }

            if (pageNumber == 1) {
                throw new IllegalStateException("None of the basket pages could be rendered");
            }

            File outDir = new File(context.getCacheDir(), "basket_exports");
            if (!outDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outDir.mkdirs();
            }
            File tempFile = new File(outDir, "temp_" + System.currentTimeMillis() + ".pdf");

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                outDoc.writeTo(fos);
            }
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

    private static Bitmap renderSourcePage(Context context, Uri sourceUri, int pageIndex) {
        PdfCore core = new PdfCore();
        try {
            core.open(context, sourceUri);
            core.setScreenSize(EXPORT_WIDTH, EXPORT_HEIGHT);
            if (pageIndex < 0 || pageIndex >= core.pageCount()) return null;

            Bitmap rendered = core.renderPage(pageIndex, EXPORT_WIDTH, EXPORT_HEIGHT);
            if (rendered == null || rendered.isRecycled()) return null;
            return rendered.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable t) {
            return null;
        } finally {
            core.close();
        }
    }
}