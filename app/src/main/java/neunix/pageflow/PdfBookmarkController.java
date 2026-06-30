package neunix.pageflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns bookmark state AND the dedicated bookmarks sheet — a standalone list
 * of every saved bookmark (separate from the TOC sheet), so the user can
 * see and jump to all their bookmarks in one place.
 */
public class PdfBookmarkController {

    public interface OnBookmarkSelected { void onSelected(int page); }

    private static final String PREFS_NAME    = "pageflow_prefs";
    private static final String KEY_BOOKMARKS = "bookmarks_";

    private final Context context;
    private final PdfReaderController reader;
    private final Set<Integer> bookmarkedPages = new HashSet<>();

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
        recycler.setLayoutManager(new LinearLayoutManager(context));
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
        toggleButton.setImageResource(on
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);
        toggleButton.setColorFilter(on ? Color.parseColor("#FFD700") : Color.WHITE);
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
    // ADAPTER
    // =========================================================
    private class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(parent.getContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(56, 24, 32, 24);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            TextView star = new TextView(parent.getContext());
            star.setText("★");
            star.setTextColor(Color.parseColor("#FFD700"));
            star.setTextSize(18f);
            android.widget.LinearLayout.LayoutParams starLp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            starLp.rightMargin = 24;
            row.addView(star, starLp);

            TextView label = new TextView(parent.getContext());
            label.setTextColor(Color.parseColor("#E8E8E8"));
            label.setTextSize(15f);
            android.widget.LinearLayout.LayoutParams labelLp = new android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(label, labelLp);

            ImageButton remove = new ImageButton(parent.getContext());
            remove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            remove.setBackground(null);
            remove.setColorFilter(Color.parseColor("#555555"));
            row.addView(remove, new android.widget.LinearLayout.LayoutParams(72, 72));

            return new VH(row, label, remove);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            List<Integer> sorted = getSortedBookmarks();
            int page = sorted.get(pos);
            h.label.setText("Page " + (page + 1));
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
            TextView label; ImageButton remove;
            VH(View v, TextView label, ImageButton remove) { super(v); this.label = label; this.remove = remove; }
        }
    }
}