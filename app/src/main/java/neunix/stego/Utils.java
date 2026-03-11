package neunix.stego;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {

    // Base Stegora directory inside app-specific storage
    public static File getBaseDir(Context context, String subFolder) throws IOException {
        File base = new File(context.getExternalFilesDir(null), "Stegora");

        if (!base.exists() && !base.mkdirs())
            throw new IOException("Failed to create Stegora directory");

        File dir = new File(base, subFolder);
        if (!dir.exists() && !dir.mkdirs())
            throw new IOException("Failed to create subfolder: " + subFolder);

        return dir;
    }

    // Timestamped unique file
    public static File getTimestampedFile(Context context, String baseName, String subFolder) throws IOException {
        File dir = getBaseDir(context, subFolder);

        String name = baseName;
        String ext = "";
        int dot = baseName.lastIndexOf('.');
        if (dot != -1) {
            name = baseName.substring(0, dot);
            ext = baseName.substring(dot);
        }

        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.getDefault()
        ).format(new Date());

        return new File(dir, name + "_" + timestamp + ext);
    }

    public static FileOutputStream getTimestampedOutputStream(Context context,
                                                              String baseName,
                                                              String subFolder) throws IOException {
        return new FileOutputStream(getTimestampedFile(context, baseName, subFolder));
    }

    // Format bytes → KB / MB
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.2f KB", kb);
        return String.format(Locale.getDefault(), "%.2f MB", kb / 1024.0);
    }
}