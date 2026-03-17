package neunix.stego;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.InputStream;

public class JPGtoPNG {

    public static Bitmap convert(Context context, Uri uri) throws Exception {

        Bitmap bitmap = decodeOptimized(context, uri);

        if (bitmap == null)
            throw new Exception("Failed to decode image");

        // 🔥 CRITICAL: enforce correct config
        bitmap = sanitize(bitmap);

        return bitmap;
    }

    private static Bitmap decodeOptimized(Context context, Uri uri) throws Exception {

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }

        int scale = 1;
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);

        // ⚠️ OPTIONAL: keep or remove depending on your strategy
        while (maxDim / scale > 1600) scale *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = scale;

        // 🔥 FORCE SAFE FORMAT
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    // 🔥 SAME SANITIZER AS EVERYWHERE
    private static Bitmap sanitize(Bitmap input) {

        if (input.getConfig() != Bitmap.Config.ARGB_8888) {
            input = input.copy(Bitmap.Config.ARGB_8888, true);
        }

        if (!input.isMutable()) {
            input = input.copy(Bitmap.Config.ARGB_8888, true);
        }

        return input;
    }

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