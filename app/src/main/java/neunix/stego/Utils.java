package neunix.stego;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {

    private static final String ROOT = "StegoBox";
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);

    // /storage/emulated/0/StegoBox/Embedded or Extracted
    public static File getDir(String sub) throws IOException {
        File dir = new File(
                Environment.getExternalStorageDirectory(),
                ROOT + "/" + sub
        );
        if (!dir.exists() && !dir.mkdirs())
            throw new IOException("Failed to create " + dir.getAbsolutePath());
        return dir;
    }

    // horn.wav → horn_2026-01-18_14-32-10.wav
    public static File getTimestampedFile(String name, String sub)
            throws IOException {

        File dir = getDir(sub);

        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }

        String ts = TS.format(new Date());
        return new File(dir, base + "_" + ts + ext);
    }

    public static FileOutputStream getTimestampedStream(String name, String sub)
            throws IOException {
        return new FileOutputStream(getTimestampedFile(name, sub));
    }

    // Bytes → KB → MB
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.2f KB", kb);
        return String.format(Locale.US, "%.2f MB", kb / 1024.0);
    }
}