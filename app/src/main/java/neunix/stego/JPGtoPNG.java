package neunix.stego;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class to convert JPG/JPEG images to PNG Bitmaps (lossless)
 */
public class JPGtoPNG {

    /**
     * Converts a JPG or JPEG image Uri to a PNG Bitmap.
     * If the image is already PNG, returns a mutable copy.
     *
     * @param context Context for content resolver
     * @param uri     Image Uri
     * @return PNG-compatible Bitmap (ARGB_8888, mutable)
     * @throws IOException if reading fails
     */
    public static Bitmap convert(Context context, Uri uri) throws IOException {

        String name = Utils.getFileName(context, uri).toLowerCase();

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            if (bmp == null) throw new IOException("Cannot decode image");
            // If JPG/JPEG, just make a mutable PNG copy
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                return bmp.copy(Bitmap.Config.ARGB_8888, true);
            }
            // Else, ensure mutable copy
            return bmp.copy(Bitmap.Config.ARGB_8888, true);
        }
    }

    /**
     * Optional: Convert Bitmap to byte array PNG (for debugging or saving)
     */
    public static byte[] toPNGBytes(Bitmap bmp) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, baos))
                throw new IOException("Failed to compress PNG");
            return baos.toByteArray();
        }
    }
}