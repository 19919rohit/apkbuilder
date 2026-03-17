package neunix.stego;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        holder.icon.setImageResource(R.drawable.ic_file_generic); // generic icon

        // Share as document
        holder.btnShare.setOnClickListener(v -> {
            try {
                Uri uri = Uri.fromFile(file);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("*/*"); // generic doc
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                context.startActivity(Intent.createChooser(intent, "Share file"));
            } catch (Exception e) {
                Toast.makeText(context, "Failed to share file", Toast.LENGTH_SHORT).show();
            }
        });

        // Delete file
        holder.btnDelete.setOnClickListener(v -> {
            if (file.delete()) {
                files.remove(file);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, files.size());
                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show();
                if (files.isEmpty() && onListEmpty != null) onListEmpty.run();
            } else {
                Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

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