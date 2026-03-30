package neunix.stego;

import android.content.Context;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
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

        SmartFile sf = new SmartFile(files.get(position));
        holder.fileName.setText(sf.getFile().getName());

        // 🔥 Thumbnail if image
        if (sf.isImage()) {
            Glide.with(context)
                    .load(sf.getFile())
                    .centerCrop()
                    .into(holder.icon);
        } else {
            holder.icon.setImageResource(R.drawable.ic_file);
        }

        // 👆 Open file on click
        holder.itemView.setOnClickListener(v -> sf.open(context));

        // 🔗 Share file
        holder.btnShare.setOnClickListener(v -> sf.share(context));

        // 🗑 Delete file
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

        // Optional: click image opens file too
        holder.icon.setOnClickListener(v -> sf.open(context));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    // 🔍 ViewHolder
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