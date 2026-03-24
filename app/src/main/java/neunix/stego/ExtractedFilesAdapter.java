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

public class ExtractedFilesAdapter extends RecyclerView.Adapter<ExtractedFilesAdapter.VH> {

    private final Context context;
    private final List<File> files;
    private final Runnable onListEmpty;

    public ExtractedFilesAdapter(Context ctx, List<File> files, Runnable onListEmpty) {
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

        // 🔥 Thumbnail if image
        if (isImage(file)) {
            Glide.with(context)
                    .load(file)
                    .centerCrop()
                    .into(holder.icon);
        } else {
            holder.icon.setImageResource(R.drawable.ic_file);
        }

        // 🔗 SHARE AS DOCUMENT
        holder.btnShare.setOnClickListener(v -> {
            try {
                Uri uri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".provider",
                        file
                );

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.putExtra(Intent.EXTRA_TITLE, file.getName());
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                context.startActivity(Intent.createChooser(intent, "Send as document"));

            } catch (Exception e) {
                Toaster.show(context, "Share failed");
            }
        });

        // 🗑 DELETE
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

        // 👆 click item → share
        holder.itemView.setOnClickListener(v -> holder.btnShare.performClick());
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    // 🔍 Detect image
    private boolean isImage(File file) {
        String n = file.getName().toLowerCase();
        return n.endsWith(".png") ||
               n.endsWith(".jpg") ||
               n.endsWith(".jpeg") ||
               n.endsWith(".webp");
    }

    // 📦 ViewHolder
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