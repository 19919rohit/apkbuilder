package neunix.stego;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Utils {

    public static File getOutputFile(Context ctx, String name, String folder) throws IOException {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folder);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    public static FileOutputStream getOutputFileStream(Context ctx, String name, String folder) throws IOException {
        File file = getOutputFile(ctx, name, folder);
        return new FileOutputStream(file);
    }
}