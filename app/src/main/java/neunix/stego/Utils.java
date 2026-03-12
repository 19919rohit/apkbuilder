package neunix.stego;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {

    // Folder constants (prevents typo bugs)
    public static final String DIR_EMBEDDED = "Embedded";
    public static final String DIR_EXTRACTED = "Extracted";

    // Get base directory inside Android/data/neunix.stego/files/
    public static File getBaseDir(Context context, String subFolder) throws IOException {

        File base = context.getExternalFilesDir(null);

        if (base == null)
            throw new IOException("External storage unavailable");

        File dir = new File(base, subFolder);

        if (!dir.exists() && !dir.mkdirs())
            throw new IOException("Failed to create folder: " + subFolder);

        return dir;
    }

    // Create timestamped file to prevent overwrite
    public static File getTimestampedFile(Context context,
                                          String baseName,
                                          String subFolder) throws IOException {

        File dir = getBaseDir(context, subFolder);

        String name = baseName;
        String ext = "";

        int dot = baseName.lastIndexOf('.');
        if (dot != -1) {
            name = baseName.substring(0, dot);
            ext = baseName.substring(dot);
        }

        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
        ).format(new Date());

        return new File(dir, name + "_" + timestamp + ext);
    }

    // Direct OutputStream for saving files
    public static FileOutputStream getTimestampedOutputStream(Context context,
                                                              String baseName,
                                                              String subFolder) throws IOException {

        File file = getTimestampedFile(context, baseName, subFolder);
        return new FileOutputStream(file);
    }

    // Format bytes into readable size
    public static String formatSize(long bytes) {

        if (bytes < 1024)
            return bytes + " B";

        double kb = bytes / 1024.0;

        if (kb < 1024)
            return String.format(Locale.getDefault(), "%.2f KB", kb);

        double mb = kb / 1024.0;

        return String.format(Locale.getDefault(), "%.2f MB", mb);
    }
}