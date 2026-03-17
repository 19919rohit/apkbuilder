package neunix.stego;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.*;
import android.widget.*;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;

public class FileAdapter extends ArrayAdapter<File> {

    private final Context context;
    private final List<File> files;
    private final Runnable onDeleteCallback;

    public FileAdapter(Context context, List<File> files, Runnable onDeleteCallback) {
        super(context, 0, files);
        this.context = context;
        this.files = files;
        this.onDeleteCallback = onDeleteCallback;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_file, parent, false);

            holder = new ViewHolder();
            holder.fileName = convertView.findViewById(R.id.tvFileName);
            holder.preview = convertView.findViewById(R.id.imagePreview);
            holder.shareBtn = convertView.findViewById(R.id.btnShare);
            holder.deleteBtn = convertView.findViewById(R.id.btnDelete);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        File file = files.get(position);

        holder.fileName.setText(file.getName());

        loadThumbnail(file, holder.preview);

        holder.shareBtn.setOnClickListener(v -> shareFile(file));
        holder.deleteBtn.setOnClickListener(v -> deleteFile(file));

        return convertView;
    }

    // ================= VIEW HOLDER =================
    static class ViewHolder {
        TextView fileName;
        ImageView preview;
        ImageButton shareBtn;
        ImageButton deleteBtn;
    }

    // ================= THUMBNAIL =================
    private void loadThumbnail(File file, ImageView preview) {

        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();

            // Step 1: get bounds
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            int scale = 1;
            while (opts.outWidth / scale > 200 || opts.outHeight / scale > 200) {
                scale *= 2;
            }

            // Step 2: decode thumbnail
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = scale;

            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            if (bmp != null) {
                preview.setImageBitmap(bmp);
                preview.setVisibility(View.VISIBLE);
            } else {
                preview.setVisibility(View.GONE);
            }

        } catch (Exception e) {
            preview.setVisibility(View.GONE);
        }
    }

    // ================= SHARE =================
    private void shareFile(File file) {

        try {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    "neunix.stego.provider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(intent, "Share File"));

        } catch (Exception e) {
            Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= DELETE =================
    private void deleteFile(File file) {

        if (file.delete()) {
            files.remove(file);
            notifyDataSetChanged();

            if (onDeleteCallback != null) {
                onDeleteCallback.run();
            }

            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show();

        } else {
            Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show();
        }
    }
}