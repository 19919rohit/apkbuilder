package neunix.pagevibe;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

/**
 * Single centralized entry point for cover-image loading. Keeping Glide
 * usage behind this thin wrapper (rather than calling Glide directly
 * from the adapter) means the image-loading implementation could be
 * swapped later without touching any UI code — matches the "keep UI
 * separate from [X] logic" architecture principle applied consistently.
 *
 * Memory cache: Glide's built-in LruResourceCache, automatic.
 * Disk cache: DiskCacheStrategy.AUTOMATIC — Glide's own recommended
 * default, which intelligently caches either the original or the
 * transformed bitmap depending on the request, balancing correctness
 * against disk usage (more conservative than ALL, which would double
 * cache every image at both stages).
 */
public class DiscoverImageLoader {

    private DiscoverImageLoader() {}

    public static void load(Context context, String url, ImageView target) {
        Glide.with(context)
                .load(url)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_error)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .into(target);
    }

    /** Call from onViewRecycled() to cancel any in-flight load for a
     *  reused card, avoiding a stale image flashing in briefly. */
    public static void clear(Context context, ImageView target) {
        try { Glide.with(context).clear(target); } catch (Throwable ignored) {}
    }
}