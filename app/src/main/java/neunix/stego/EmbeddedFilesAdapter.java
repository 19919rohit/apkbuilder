package neunix.stego;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class EmbeddedFilesAdapter extends RecyclerView.Adapter<EmbeddedFilesAdapter.VH> {

    private final Context context;
    private final List<File> files;
    private final Runnable onListEmpty;

    public EmbeddedFilesAdapter(Context ctx, List<File> files, Runnable onListEmpty) {
        this.context = ctx;
        this.files = files;
        this.onListEmpty = onListEmpty;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_extracted_file, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {

        File file = files.get(position);
        holder.fileName.setText(file.getName());

        // 🔥 Show thumbnail if image, else generic file icon
        if (isImage(file)) {
            Glide.with(context)
                    .load(file)
                    .centerCrop()
                    .into(holder.icon);
        } else {
            holder.icon.setImageResource(R.drawable.ic_file);
        }

        // 📂 OPEN FILE
        holder.itemView.setOnClickListener(v -> {
            try {
                Uri uri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".provider",
                        file
                );

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, getMimeType(file)); // all as octet-stream
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                context.startActivity(Intent.createChooser(intent, "Open with"));

            } catch (Exception e) {
                Toaster.show(context, "No app found to open this file");
            }
        });

        // 🔗 SHARE FILE
        holder.btnShare.setOnClickListener(v -> {
            try {
                Uri uri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".provider",
                        file
                );

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(getMimeType(file)); // all as octet-stream
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                context.startActivity(Intent.createChooser(intent, "Share file"));

            } catch (Exception e) {
                Toaster.show(context, "Share failed");
            }
        });

        // 🗑 DELETE FILE
        holder.btnDelete.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            File f = files.get(pos);

            if (f.delete()) {
                files.remove(pos);
                notifyItemRemoved(pos);
                Toaster.show(context, "Deleted");

                if (files.isEmpty() && onListEmpty != null) {
                    onListEmpty.run();
                }

            } else {
                Toaster.show(context, "Delete failed");
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    // ================= HELPERS =================

    private boolean isImage(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") ||
               name.endsWith(".jpg") ||
               name.endsWith(".jpeg") ||
               name.endsWith(".webp");
    }

    // 🔥 FORCE all files as octet-stream
    private String getMimeType(File file) {
        return "application/octet-stream";
    }

    // ================= VIEW HOLDER =================

    static class VH extends RecyclerView.ViewHolder {

        ImageView icon;
        TextView fileName;
        ImageButton btnShare, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            fileName = itemView.findViewById(R.id.fileName);
            btnShare = itemView.findViewById(R.id.btnShare);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}