package neunix.stego;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {

    // Base StegoBox directory in Documents
    public static File getBaseDir(String subFolder) throws IOException {
        File base = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "StegoBox"
        );

        if (!base.exists() && !base.mkdirs())
            throw new IOException("Failed to create StegoBox directory");

        File dir = new File(base, subFolder);
        if (!dir.exists() && !dir.mkdirs())
            throw new IOException("Failed to create subfolder: " + subFolder);

        return dir;
    }

    // Timestamped unique file
    public static File getTimestampedFile(String baseName, String subFolder) throws IOException {
        File dir = getBaseDir(subFolder);

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

    public static FileOutputStream getTimestampedOutputStream(
            String baseName,
            String subFolder
    ) throws IOException {
        return new FileOutputStream(getTimestampedFile(baseName, subFolder));
    }

    // Format bytes → KB / MB
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.2f KB", kb);
        return String.format(Locale.getDefault(), "%.2f MB", kb / 1024.0);
    }
}