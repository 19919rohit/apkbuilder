package neunix.stego;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SmartFile {

    private final File file;

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "png","jpg","jpeg","webp","bmp","gif","heic","tiff"
    ));

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
        "mp4","mkv","avi","mov","wmv","flv","webm","3gp"
    ));

    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
        "mp3","wav","ogg","flac","aac","m4a"
    ));

    private static final Set<String> DOCUMENT_EXTENSIONS = new HashSet<>(Arrays.asList(
        "pdf","doc","docx","ppt","pptx","xls","xlsx","txt","rtf","odt"
    ));

    private static final Set<String> ARCHIVE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "zip","rar","7z","tar","gz"
    ));

    public SmartFile(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    // 🔥 Detect if it's an image
    public boolean isImage() {
        String ext = getExtension(file);
        return IMAGE_EXTENSIONS.contains(ext);
    }

    // 🔥 Detect if it's a video
    public boolean isVideo() {
        String ext = getExtension(file);
        return VIDEO_EXTENSIONS.contains(ext);
    }

    // 🔥 Detect if it's audio
    public boolean isAudio() {
        String ext = getExtension(file);
        return AUDIO_EXTENSIONS.contains(ext);
    }

    // 🔥 Detect if it's document
    public boolean isDocument() {
        String ext = getExtension(file);
        return DOCUMENT_EXTENSIONS.contains(ext);
    }

    // 🔥 Detect if archive
    public boolean isArchive() {
        String ext = getExtension(file);
        return ARCHIVE_EXTENSIONS.contains(ext);
    }

    // 🔥 Return MIME type for Intent
    public String getMimeType() {
        String ext = getExtension(file);
        if (ext.isEmpty()) return "*/*";

        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return type != null ? type : "*/*";
    }

    // 🔥 Open file with appropriate app
    public void open(Context ctx) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getMimeType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toaster.show(ctx, "No app found to open this file");
        }
    }

    // 🔥 Share file as document
    public void share(Context ctx) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(getMimeType());
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(intent, "Share file"));
        } catch (Exception e) {
            Toaster.show(ctx, "Share failed");
        }
    }

    // 🔍 Utility: get lowercase file extension
    private static String getExtension(File file) {
        String name = file.getName();
        int i = name.lastIndexOf('.');
        if (i > 0 && i < name.length() - 1) {
            return name.substring(i + 1).toLowerCase();
        }
        return "";
    }
}