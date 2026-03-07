package neunix.stego;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class JPGtoPNG {

    private final Context context;

    public JPGtoPNG(Context ctx) {
        context = ctx;
    }

    /**
     * Converts any image Uri to an in-memory PNG bitmap
     * @param uri Input image Uri (JPG, JPEG, WEBP, PNG)
     * @return PNG-compressed byte array
     * @throws Exception on read/compression failure
     */
    public byte[] convert(Uri uri) throws Exception {

        Bitmap bitmap = decodeOptimized(uri);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos))
                throw new Exception("Failed to convert to PNG");
            return baos.toByteArray();
        }
    }

    /**
     * Gets the original file name from a Uri
     * @param uri Input Uri
     * @return File name as String
     */
    public String getFileName(Uri uri) {
        String result = null;

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception ignored) {}
        }

        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                result = (cut != -1) ? path.substring(cut + 1) : path;
            }
        }

        return (result != null) ? result : "file";
    }

    /**
     * Optimized bitmap decoding to avoid OOM on large images
     * @param uri Image Uri
     * @return Decoded Bitmap
     * @throws Exception on failure
     */
    private Bitmap decodeOptimized(Uri uri) throws Exception {

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
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            if (bmp == null) throw new Exception("Failed to decode bitmap");
            return bmp;
        }
    }
}