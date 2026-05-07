package neunix.pageflow;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class FileUtils {

    public static File getFileFromUri(Context context, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);

            String name = getFileName(context, uri);
            File file = new File(context.getCacheDir(), name);

            FileOutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[4096];
            int len;

            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }

            outputStream.close();
            inputStream.close();

            return file;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getFileName(Context context, Uri uri) {
        String result = "temp.pdf";

        Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            result = cursor.getString(idx);
            cursor.close();
        }

        return result;
    }
}