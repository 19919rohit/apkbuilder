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

public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageVH> {

    private final Context context;
    private final PdfCore pdfCore;

    // ONLY 3 pages cache
    private final LruCache<Integer, Bitmap> cache =
            new LruCache<Integer, Bitmap>(3) {

                @Override
                protected int sizeOf(Integer key, Bitmap value) {
                    return 1;
                }

                @Override
                protected void entryRemoved(
                        boolean evicted,
                        Integer key,
                        Bitmap oldValue,
                        Bitmap newValue
                ) {

                    if (oldValue != null && !oldValue.isRecycled()) {
                        oldValue.recycle();
                    }
                }
            };

    public PdfPageAdapter(Context context, PdfCore pdfCore) {
        this.context = context;
        this.pdfCore = pdfCore;
    }

    @NonNull
    @Override
    public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_page, parent, false);

        return new PageVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageVH holder, int position) {

        Bitmap bitmap = cache.get(position);

        if (bitmap == null || bitmap.isRecycled()) {

            // lower resolution = huge RAM savings
            int width = 720;
            int height = 1280;

            bitmap = pdfCore.renderPage(position, width, height);

            cache.put(position, bitmap);
        }

        holder.imageView.setImageBitmap(bitmap);

        preload(position - 1);
        preload(position + 1);
    }

    private void preload(int page) {

        if (page < 0 || page >= getItemCount()) {
            return;
        }

        if (cache.get(page) != null) {
            return;
        }

        int width = 720;
        int height = 1280;

        Bitmap bmp = pdfCore.renderPage(page, width, height);

        cache.put(page, bmp);
    }

    @Override
    public int getItemCount() {
        return pdfCore.pageCount();
    }

    static class PageVH extends RecyclerView.ViewHolder {

        ImageView imageView;

        public PageVH(@NonNull View itemView) {
            super(itemView);

            imageView = itemView.findViewById(R.id.pageImage);
        }
    }
}