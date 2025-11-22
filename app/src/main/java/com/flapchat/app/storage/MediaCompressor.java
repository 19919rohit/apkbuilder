package com.flapchat.app.storage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MediaCompressor {

    public static File compressImage(File original, File destDir, String name, int quality) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(original.getAbsolutePath());
        File outputFile = new File(destDir, name);
        FileOutputStream out = new FileOutputStream(outputFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        out.flush();
        out.close();
        return outputFile;
    }

    public static byte[] compressToBytes(Bitmap bitmap, int quality) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
        return stream.toByteArray();
    }
}