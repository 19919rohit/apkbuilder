package neunix.stego;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.util.*;

public class FastImageAdapter extends RecyclerView.Adapter<FastImageAdapter.VH> {

    private final List<File> files = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onClick(File file);
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    public void submitList(List<File> newList) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new Diff(files, newList));
        files.clear();
        files.addAll(newList);
        diff.dispatchUpdatesTo(this);
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView preview;
        TextView name;

        VH(View v) {
            super(v);
            preview = v.findViewById(R.id.imagePreview);
            name = v.findViewById(R.id.tvFileName);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_extract_img_file, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {

        File file = files.get(pos);

        h.name.setText(file.getName());

        // 🔥 FULL GLIDE PIPELINE
        Glide.with(h.preview.getContext())
                .load(file)
                .centerCrop()
                .override(120, 120)
                .thumbnail(0.25f) // instant preview
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(h.preview);

        // ✅ click handling (NO RecyclerItemClickListener needed anymore)
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(file);
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    // ================= DIFF =================
    static class Diff extends DiffUtil.Callback {

        List<File> oldList, newList;

        Diff(List<File> o, List<File> n) {
            oldList = o;
            newList = n;
        }

        public int getOldListSize() { return oldList.size(); }
        public int getNewListSize() { return newList.size(); }

        public boolean areItemsTheSame(int o, int n) {
            return oldList.get(o).getAbsolutePath()
                    .equals(newList.get(n).getAbsolutePath());
        }

        public boolean areContentsTheSame(int o, int n) {
            return oldList.get(o).lastModified()
                    == newList.get(n).lastModified();
        }
    }
}