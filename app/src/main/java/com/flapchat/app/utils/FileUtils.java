package com.flapchat.app.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import java.io.File;
import java.io.IOException;

public class FileUtils {

    public static File createTempFile(Context context, String prefix, String extension) throws IOException {
        File cacheDir = context.getCacheDir();
        return File.createTempFile(prefix, extension, cacheDir);
    }

    public static File getExternalFile(String fileName) {
        File dir = Environment.getExternalStorageDirectory();
        return new File(dir, fileName);
    }
}