package neunix.pagevibe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns bookmark state AND the dedicated bookmarks sheet — now rendered as
 * a 2-column grid of visual "Page Cards": each card shows a real
 * thumbnail of that bookmarked page (rendered on demand from the already-
 * open PdfCore, cached, never blocking the UI), a page-number badge, and
 * a remove button. Falls back to a plain page-number placeholder while a
 * thumbnail is still loading or if rendering ever fails.
 */
public class PdfBookmarkController {

    public interface OnBookmarkSelected { void onSelected(int page); }

    private static final String PREFS_NAME    = "pagevibe_prefs";
    private static final String KEY_BOOKMARKS = "bookmarks_";

    // Thumbnail render size — matches the convention already used for
    // library thumbnails elsewhere in the app (MainActivity).
    private static final int THUMB_W = 240;
    private static final int THUMB_H = 320;
    private static final int THUMB_CACHE_SIZE = 40;

    private final Context context;
    private final PdfReaderController reader;
    private final Set<Integer> bookmarkedPages = new HashSet<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final ImageButton toggleButton;
    private final View        toast;
    private final TextView    toastText;

    // Dedicated bookmarks sheet
    private final View         sheetBackdrop;
    private final View         sheet;
    private final ImageButton  btnSheetClose;
    private final RecyclerView recycler;
    private final View         emptyState;
    private final BookmarkAdapter adapter;
    private final OnBookmarkSelected onSelected;

    // Owned copies of rendered page thumbnails, keyed by page index.
    // Recycled automatically as entries are evicted, and fully cleared
    // whenever the document changes (loadForUri) so thumbnails never
    // leak across different open PDFs.
    private final LruCache<Integer, Bitmap> thumbnailCache =
            new LruCache<Integer, Bitmap>(THUMB_CACHE_SIZE) {
                @Override
                protected void entryRemoved(boolean evicted, Integer key, Bitmap oldValue, Bitmap newValue) {
                    if (evicted && oldValue != null && !oldValue.isRecycled()) {
                        try { oldValue.recycle(); } catch (Throwable ignored) {}
                    }
                }
            };

    public PdfBookmarkController(Context context, PdfReaderController reader,
                                  ImageButton toggleButton, View toast, TextView toastText,
                                  View sheetBackdrop, View sheet, ImageButton btnSheetClose,
                                  RecyclerView recycler, View emptyState,
                                  OnBookmarkSelected onSelected) {
        this.context = context;
        this.reader = reader;
        this.toggleButton = toggleButton;
        this.toast = toast;
        this.toastText = toastText;
        this.sheetBackdrop = sheetBackdrop;
        this.sheet = sheet;
        this.btnSheetClose = btnSheetClose;
        this.recycler = recycler;
        this.emptyState = emptyState;
        this.onSelected = onSelected;

        adapter = new BookmarkAdapter();
        recycler.setLayoutManager(new GridLayoutManager(context, 2));
        recycler.setAdapter(adapter);

        toggleButton.setOnClickListener(v -> toggleCurrentPageBookmark());
        btnSheetClose.setOnClickListener(v -> hideSheet());
        sheetBackdrop.setOnClickListener(v -> hideSheet());

        TooltipUtil.apply(toggleButton, "Bookmark this page");
        TooltipUtil.apply(btnSheetClose, "Close bookmarks");
    }

    // =========================================================
    // LIFECYCLE PER-DOCUMENT
    // =========================================================
    public void loadForUri(Uri uri) {
        bookmarkedPages.clear();
        thumbnailCache.evictAll();
        if (uri == null) return;
        try {
            String json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_BOOKMARKS + uri.hashCode(), "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) bookmarkedPages.add(arr.getInt(i));
        } catch (Exception ignored) {}
        adapter.notifyDataSetChanged();
    }

    private void save() {
        Uri uri = reader.getCurrentUri();
        if (uri == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (int p : bookmarkedPages) arr.put(p);
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_BOOKMARKS + uri.hashCode(), arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    // =========================================================
    // TOGGLE / UI
    // =========================================================
    public void toggleCurrentPageBookmark() {
        int page = reader.getSettledPage();
        if (bookmarkedPages.contains(page)) {
            bookmarkedPages.remove(page);
            showToast("Bookmark removed");
        } else {
            bookmarkedPages.add(page);
            showToast("Page " + (page + 1) + " bookmarked ★");
        }
        updateIcon();
        save();
        adapter.notifyDataSetChanged();
    }

    public void updateIcon() {
        boolean on = bookmarkedPages.contains(reader.getSettledPage());
        toggleButton.setImageResource(on ? R.drawable.ic_star_filled : R.drawable.ic_star);
        toggleButton.clearColorFilter();
    }

    public boolean isBookmarked(int page) { return bookmarkedPages.contains(page); }

    public List<Integer> getSortedBookmarks() {
        List<Integer> sorted = new ArrayList<>(bookmarkedPages);
        Collections.sort(sorted);
        return sorted;
    }

    private void showToast(String message) {
        toastText.setText(message);
        toast.setVisibility(View.VISIBLE);
        toast.setAlpha(1f);
        toast.animate().alpha(0f).setDuration(900).setStartDelay(1300)
                .withEndAction(() -> toast.setVisibility(View.GONE)).start();
    }

    // =========================================================
    // DEDICATED BOOKMARKS SHEET
    // =========================================================
    public void showSheet() {
        adapter.notifyDataSetChanged();
        sheetBackdrop.setVisibility(View.VISIBLE);
        sheetBackdrop.setAlpha(0f);
        sheetBackdrop.animate().alpha(1f).setDuration(180).start();
        sheet.setVisibility(View.VISIBLE);
        sheet.animate().translationY(0).setDuration(300).setInterpolator(new DecelerateInterpolator(2f)).start();
        emptyState.setVisibility(bookmarkedPages.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(bookmarkedPages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void hideSheet() {
        sheetBackdrop.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> sheetBackdrop.setVisibility(View.GONE)).start();
        sheet.animate().translationY(2000).setDuration(240)
                .withEndAction(() -> sheet.setVisibility(View.GONE)).start();
    }

    // =========================================================
    // THUMBNAIL LOADING
    // =========================================================

    /**
     * Renders (or fetches from cache) a thumbnail for the given page,
     * reusing the same PdfCore the reader already has open — no second
     * document is opened just to make a thumbnail. Runs on the reader's
     * existing single-thread background executor so it never contends
     * with the main render pipeline in a surprising way, and always
     * copies the result into an owned bitmap before caching (PdfCore's
     * own internal cache can recycle its bitmaps on eviction, so holding
     * onto that exact instance would risk an IllegalStateException later).
     */
    private void loadThumbnailAsync(int page, ImageView thumbView, TextView initialView) {
        reader.getBgExecutor().execute(() -> {
            PdfCore core = reader.getCore();
            if (core == null) return;

            Bitmap rendered;
            try {
                rendered = core.renderPage(page, THUMB_W, THUMB_H);
            } catch (Throwable t) {
                return;
            }
            if (rendered == null || rendered.isRecycled()) return;

            Bitmap owned;
            try {
                owned = rendered.copy(Bitmap.Config.ARGB_8888, false);
            } catch (Throwable t) {
                return;
            }
            if (owned == null) return;

            thumbnailCache.put(page, owned);

            uiHandler.post(() -> {
                Object tag = thumbView.getTag();
                // Guard against RecyclerView view-holder reuse: only apply
                // this result if the view is still showing the SAME page
                // it was asked to load a thumbnail for.
                if (tag instanceof Integer && (Integer) tag == page && !owned.isRecycled()) {
                    thumbView.setImageBitmap(owned);
                    initialView.setVisibility(View.GONE);
                }
            });
        });
    }

    // =========================================================
    // ADAPTER — GRID OF PAGE CARDS
    // =========================================================
    private class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_page_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            List<Integer> sorted = getSortedBookmarks();
            if (pos >= sorted.size()) return;
            int page = sorted.get(pos);

            h.pageBadge.setText("Page " + (page + 1));
            h.thumbnail.setTag(page);
            h.thumbnail.setImageBitmap(null);
            h.initial.setText(String.valueOf(page + 1));
            h.initial.setVisibility(View.VISIBLE);

            Bitmap cached = thumbnailCache.get(page);
            if (cached != null && !cached.isRecycled()) {
                h.thumbnail.setImageBitmap(cached);
                h.initial.setVisibility(View.GONE);
            } else {
                loadThumbnailAsync(page, h.thumbnail, h.initial);
            }

            h.itemView.setOnClickListener(v -> { hideSheet(); onSelected.onSelected(page); });
            h.remove.setOnClickListener(v -> {
                bookmarkedPages.remove(page);
                save();
                notifyDataSetChanged();
                updateIcon();
                emptyState.setVisibility(bookmarkedPages.isEmpty() ? View.VISIBLE : View.GONE);
                recycler.setVisibility(bookmarkedPages.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }

        @Override public int getItemCount() { return bookmarkedPages.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            TextView  initial;
            TextView  pageBadge;
            ImageButton remove;

            VH(View v) {
                super(v);
                thumbnail = v.findViewById(R.id.cardThumbnail);
                initial   = v.findViewById(R.id.cardInitial);
                pageBadge = v.findViewById(R.id.cardPageBadge);
                remove    = v.findViewById(R.id.cardRemove);
            }
        }
    }
}