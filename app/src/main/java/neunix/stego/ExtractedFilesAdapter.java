package neunix.stego;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class ExtractedFilesAdapter extends RecyclerView.Adapter<ExtractedFilesAdapter.VH> {

    public interface OnListChanged {
        void onChanged();
    }

    private final Context context;
    private final List<File> files;
    private final OnListChanged callback;

    public ExtractedFilesAdapter(Context ctx, List<File> list, OnListChanged cb) {
        context = ctx;
        files = list;
        callback = cb;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView preview;
        TextView name;
        ImageButton share;
        ImageButton delete;

        VH(View v) {
            super(v);
            preview = v.findViewById(R.id.imagePreview);
            name = v.findViewById(R.id.tvFileName);
            share = v.findViewById(R.id.btnShare);
            delete = v.findViewById(R.id.btnDelete);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_file, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        File file = files.get(position);
        holder.name.setText(file.getName());

        // Show preview only for images
        if (isImage(file)) {
            Glide.with(context)
                    .load(file)
                    .centerCrop()
                    .override(120, 120)
                    .placeholder(R.drawable.ic_file) // default icon during load
                    .into(holder.preview);
        } else {
            holder.preview.setImageResource(R.drawable.ic_file); // default document icon
        }

        holder.share.setOnClickListener(v -> {
            Uri uri = Uri.fromFile(file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            context.startActivity(Intent.createChooser(intent, "Share"));
        });

        holder.delete.setOnClickListener(v -> {
            if (file.delete()) {
                files.remove(file);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, files.size());
                if (callback != null) callback.onChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    private boolean isImage(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
}