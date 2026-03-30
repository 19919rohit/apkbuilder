package neunix.stego;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import java.io.File;

public class SmartFile {

    private final File file;

    public SmartFile(File file) {
        this.file = file;
    }

    /** Return the MIME type based on extension, fallback to octet-stream */
    public String getMimeType() {
        String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                ext != null ? ext.toLowerCase() : ""
        );
        return mime != null ? mime : "application/octet-stream";
    }

    /** Open the file in default viewer using Intent */
    public void open(Context context) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getMimeType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(Intent.createChooser(intent, "Open with"));

        } catch (Exception e) {
            Toaster.show(context, "Cannot open this file");
        }
    }

    /** Optional: expose the underlying file */
    public File getFile() {
        return file;
    }
}