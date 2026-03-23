package neunix.stego;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.InputStream;

public class JPGtoPNG {

    private JPGtoPNG() {}

    // ================= MAIN =================

    public static Bitmap convert(Context context, Uri uri) throws Exception {

        Bitmap bitmap = decodeRaw(context, uri);

        if (bitmap == null) {
            throw new Exception("Failed to decode image");
        }

        return sanitize(bitmap);
    }

    // ================= RAW DECODE (NO SCALING) =================

    private static Bitmap decodeRaw(Context context, Uri uri) throws Exception {

        BitmapFactory.Options opts = new BitmapFactory.Options();

        // 🔥 CRITICAL FLAGS
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inDither = false;
        opts.inScaled = false;          // 🚫 prevent auto scaling
        opts.inMutable = true;

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    // ================= SANITIZE (ONLY ONCE) =================

    private static Bitmap sanitize(Bitmap input) {

        if (input == null) {
            throw new RuntimeException("Bitmap null");
        }

        // force ARGB_8888
        if (input.getConfig() != Bitmap.Config.ARGB_8888) {
            input = input.copy(Bitmap.Config.ARGB_8888, true);
        }

        // ensure mutable
        if (!input.isMutable()) {
            input = input.copy(Bitmap.Config.ARGB_8888, true);
        }

        // 🔥 stability hint for some OEMs
        input.setHasAlpha(true);

        return input;
    }

    // ================= FILE NAME =================

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