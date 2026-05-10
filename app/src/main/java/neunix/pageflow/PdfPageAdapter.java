package neunix.pageflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.VH> {

    private final Context context;
    private final PdfCore core;

    // STRICT 3 PAGE CACHE (previous, current, next)
    private final LruCache<Integer, Bitmap> cache =
            new LruCache<>(3);

    public PdfPageAdapter(Context context, PdfCore core) {
        this.context = context;
        this.core = core;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_page, parent, false);

        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {

        Bitmap bmp = cache.get(position);

        if (bmp == null) {

            bmp = core.renderPage(position, 720, 1280);

            cache.put(position, bmp);
        }

        Glide.with(context)
                .load(bmp)
                .into(holder.image);

        preload(position - 1);
        preload(position + 1);
    }

    private void preload(int pos) {

        if (pos < 0 || pos >= getItemCount()) return;

        if (cache.get(pos) != null) return;

        core.renderPageAsync(pos, 720, 1280, bmp -> {

            cache.put(pos, bmp);
        });
    }

    @Override
    public int getItemCount() {
        return core.pageCount();
    }

    static class VH extends RecyclerView.ViewHolder {

        ImageView image;

        VH(View v) {
            super(v);
            image = v.findViewById(R.id.pageImage);
        }
    }
}