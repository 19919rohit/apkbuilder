package neunix.stego;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;

public class FileAdapter extends ArrayAdapter<File> {

    private final Context context;
    private final List<File> files;

    public FileAdapter(Context context, List<File> files) {
        super(context, 0, files);
        this.context = context;
        this.files = files;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_file, parent, false);
        }

        File file = files.get(position);

        TextView fileName = convertView.findViewById(R.id.tvFileName);
        ImageView preview = convertView.findViewById(R.id.imagePreview);
        ImageButton shareBtn = convertView.findViewById(R.id.btnShare);
        ImageButton deleteBtn = convertView.findViewById(R.id.btnDelete);

        fileName.setText(file.getName());

        loadPreview(file, preview);

        shareBtn.setOnClickListener(v -> shareFile(file));

        deleteBtn.setOnClickListener(v -> deleteFile(file));

        return convertView;
    }

    // ================= IMAGE PREVIEW =================
    private void loadPreview(File file, ImageView preview) {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4; // Downscale for memory

            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            if (bmp != null) {
                preview.setImageBitmap(bmp);
                preview.setVisibility(View.VISIBLE);
            } else {
                preview.setVisibility(View.GONE);
            }

        } else {
            preview.setVisibility(View.GONE);
        }
    }

    // ================= SHARE FILE =================
    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    "neunix.stego.provider", // Must match your manifest & file_paths.xml
                    file
            );

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream"); // Always share as document
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(intent, "Share File"));

        } catch (Exception e) {
            Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // ================= DELETE FILE =================
    private void deleteFile(File file) {
        boolean deleted = file.delete();

        if (deleted) {
            files.remove(file);
            notifyDataSetChanged();
            Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show();
        }
    }
}