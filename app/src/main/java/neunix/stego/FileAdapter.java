package neunix.stego;

import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.bumptech.glide.Glide;
import java.io.File;
import java.util.*;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {

    private final List<File> files = new ArrayList<>();

    public void submitList(List<File> newList) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new Diff(files, newList));
        files.clear();
        files.addAll(newList);
        diff.dispatchUpdatesTo(this);
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView preview;
        TextView name;
        ImageButton share, delete;

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
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        File file = files.get(position);

        holder.name.setText(file.getName());

        Glide.with(holder.preview.getContext())
                .load(file)
                .centerCrop()
                .override(120, 120)
                .into(holder.preview);

        holder.share.setOnClickListener(v -> {
            Uri uri = Uri.fromFile(file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            v.getContext().startActivity(Intent.createChooser(intent, "Share"));
        });

        holder.delete.setOnClickListener(v -> {
            if (file.delete()) {
                int idx = holder.getAdapterPosition();
                files.remove(idx);
                notifyItemRemoved(idx);
                Toast.makeText(v.getContext(), "Deleted", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class Diff extends DiffUtil.Callback {
        List<File> oldList, newList;

        Diff(List<File> o, List<File> n) {
            oldList = o;
            newList = n;
        }

        @Override
        public int getOldListSize() { return oldList.size(); }

        @Override
        public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getAbsolutePath()
                    .equals(newList.get(newItemPosition).getAbsolutePath());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).lastModified()
                    == newList.get(newItemPosition).lastModified();
        }
    }
}