package neunix.stego;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Utils {

    // Creates Pictures/StegoBox/
    public static File getBaseDir(String folder) throws IOException {
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                folder
        );
        if (!dir.exists() && !dir.mkdirs())
            throw new IOException("Failed to create directory");
        return dir;
    }

    // stego.png → stego(1).png → stego(2).png
    public static File getUniqueFile(String baseName, String folder) throws IOException {
        File dir = getBaseDir(folder);

        String name = baseName;
        String ext = "";
        int dot = baseName.lastIndexOf('.');
        if (dot != -1) {
            name = baseName.substring(0, dot);
            ext = baseName.substring(dot);
        }

        File f = new File(dir, baseName);
        int i = 1;
        while (f.exists()) {
            f = new File(dir, name + "(" + i + ")" + ext);
            i++;
        }
        return f;
    }

    public static FileOutputStream getUniqueOutputStream(String baseName, String folder)
            throws IOException {
        return new FileOutputStream(getUniqueFile(baseName, folder));
    }

    // Human-readable size
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.2f KB", kb);
        return String.format("%.2f MB", kb / 1024.0);
    }
}