package neunix.stego;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * JPGtoPNG
 * Converts JPG/JPEG images (or other formats) to PNG in memory.
 * Returns a Bitmap ready for embedding in EmbedActivity.
 */
public class JPGtoPNG {

    /**
     * Converts an image Uri to a PNG Bitmap.
     *
     * @param context the Android context
     * @param uri     the image Uri (JPG, JPEG, etc.)
     * @return Bitmap in PNG format
     * @throws Exception if decoding or conversion fails
     */
    public static Bitmap convert(Context context, Uri uri) throws Exception {

        // Load original image optimized
        Bitmap bitmap = decodeOptimized(context, uri);
        if (bitmap == null) throw new Exception("Failed to decode image");

        // Convert to PNG in memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
            throw new Exception("Failed to convert to PNG");
        }

        byte[] pngBytes = baos.toByteArray();

        // Decode PNG bytes back to Bitmap
        return BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length);
    }

    /**
     * Optimized decoding: reduces memory usage for large images.
     * Scales down if max dimension > 1600 px
     */
    private static Bitmap decodeOptimized(Context context, Uri uri) throws Exception {

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }

        int scale = 1;
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxDim / scale > 1600) scale *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = scale;

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    /**
     * Optional helper to get the original file name from a Uri
     */
    public static String getFileName(Context context, Uri uri) {
        String name = "file";
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }
}