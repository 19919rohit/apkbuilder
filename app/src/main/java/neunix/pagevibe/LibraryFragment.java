package neunix.pagevibe;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LibraryFragment extends Fragment {

    private enum SortMode { RECENT, ALPHA }

    private static final int THUMB_W = 240;
    private static final int THUMB_H = 320;
    private static final int THUMB_CACHE_SIZE = 60;

    private LibraryManager libraryManager;
    private final List<LibraryManager.Entry> allEntries     = new ArrayList<>();
    private final List<LibraryManager.Entry> displayEntries = new ArrayList<>();

    private LibraryAdapter adapter;
    private RecyclerView   recycler;
    private View           emptyState;
    private TextView       noResultsText;
    private TextView       countLabel;
    private TextView       sortChip;
    private EditText       searchInput;
    private ImageButton    btnOverflow;

    private String   searchQuery = "";
    private SortMode sortMode    = SortMode.RECENT;

    private Uri pendingCoverTargetUri = null;

    private final ExecutorService bgExecutor =
            Executors.newFixedThreadPool(2, r -> new Thread(r, "LibraryThumb"));
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final LruCache<String, Bitmap> thumbnailCache =
            new LruCache<String, Bitmap>(THUMB_CACHE_SIZE) {
                @Override
                protected void entryRemoved(boolean evicted, String key, Bitmap old, Bitmap fresh) {
                    if (evicted && old != null && !old.isRecycled()) {
                        try { old.recycle(); } catch (Throwable ignored) {}
                    }
                }
            };

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || pendingCoverTargetUri == null) return;
                Uri targetUri = pendingCoverTargetUri;
                pendingCoverTargetUri = null;
                bgExecutor.execute(() -> {
                    libraryManager.setCoverFromImage(targetUri, uri);
                    uiHandler.post(this::reloadFromStorage);
                });
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        libraryManager = new LibraryManager(requireContext());

        recycler      = view.findViewById(R.id.libraryRecycler);
        emptyState    = view.findViewById(R.id.libraryEmptyState);
        noResultsText = view.findViewById(R.id.libraryNoResults);
        countLabel    = view.findViewById(R.id.libraryCountLabel);
        sortChip      = view.findViewById(R.id.librarySortChip);
        searchInput   = view.findViewById(R.id.librarySearchInput);
        btnOverflow   = view.findViewById(R.id.btnLibraryOverflow);

        adapter = new LibraryAdapter();
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recycler.setAdapter(adapter);

        emptyState.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.setType("application/pdf");
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                     | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, 9001);
        });

        sortChip.setOnClickListener(this::showSortPopup);
        btnOverflow.setOnClickListener(this::showOverflowPopup);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                searchQuery = s.toString();
                rebuildDisplayList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        reloadFromStorage();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9001 && resultCode == android.app.Activity.RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                requireContext().getContentResolver().takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            openReader(uri);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadFromStorage();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) reloadFromStorage();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        thumbnailCache.evictAll();
    }

    // =========================================================
    // DATA
    // =========================================================

    private void reloadFromStorage() {
        if (libraryManager == null) return;
        allEntries.clear();
        allEntries.addAll(libraryManager.getAll());
        rebuildDisplayList();
    }

    private void rebuildDisplayList() {
        displayEntries.clear();
        String q = searchQuery.trim().toLowerCase(Locale.getDefault());
        for (LibraryManager.Entry e : allEntries) {
            String name = LibraryManager.displayName(e).toLowerCase(Locale.getDefault());
            if (q.isEmpty() || name.contains(q)) displayEntries.add(e);
        }
        if (sortMode == SortMode.ALPHA) {
            Collections.sort(displayEntries, (a, b) ->
                    LibraryManager.displayName(a).compareToIgnoreCase(LibraryManager.displayName(b)));
        } else {
            Collections.sort(displayEntries, (a, b) -> Long.compare(b.lastOpenedAt, a.lastOpenedAt));
        }

        if (adapter != null) adapter.notifyDataSetChanged();

        boolean libraryEmpty = allEntries.isEmpty();
        boolean noSearchResults = !libraryEmpty && displayEntries.isEmpty();

        emptyState.setVisibility(libraryEmpty ? View.VISIBLE : View.GONE);
        noResultsText.setVisibility(noSearchResults ? View.VISIBLE : View.GONE);
        recycler.setVisibility((libraryEmpty || noSearchResults) ? View.GONE : View.VISIBLE);

        countLabel.setText(allEntries.size() + (allEntries.size() == 1 ? " PDF" : " PDFs"));
    }

    // =========================================================
    // SORT POPUP
    // =========================================================

    private void showSortPopup(View anchor) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.popup_sort_menu, null);
        TextView optRecent = content.findViewById(R.id.sortOptionRecent);
        TextView optAlpha  = content.findViewById(R.id.sortOptionAlpha);

        styleSortOption(optRecent, sortMode == SortMode.RECENT);
        styleSortOption(optAlpha,  sortMode == SortMode.ALPHA);

        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(16f);

        optRecent.setOnClickListener(v -> {
            sortMode = SortMode.RECENT;
            sortChip.setText("Sort: Recent");
            rebuildDisplayList();
            popup.dismiss();
        });
        optAlpha.setOnClickListener(v -> {
            sortMode = SortMode.ALPHA;
            sortChip.setText("Sort: A–Z");
            rebuildDisplayList();
            popup.dismiss();
        });

        popup.showAsDropDown(anchor, 0, 8, Gravity.END);
    }

    private void styleSortOption(TextView tv, boolean selected) {
        tv.setTextColor(selected ? Color.parseColor("#4488FF") : Color.parseColor("#EEEEEE"));
    }

    // =========================================================
    // OVERFLOW POPUP — Delete All PDFs
    // =========================================================

    private void showOverflowPopup(View anchor) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.popup_library_overflow, null);
        TextView deleteAll = content.findViewById(R.id.btnDeleteAllPdfs);

        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(16f);

        deleteAll.setOnClickListener(v -> {
            popup.dismiss();
            confirmDeleteAll();
        });

        popup.showAsDropDown(anchor, 0, 8, Gravity.END);
    }

    private void confirmDeleteAll() {
        if (allEntries.isEmpty()) return;
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Delete all PDFs?")
                .setMessage("This removes every PDF from your PageVibe library, including any custom names and covers. Your original PDF files on your device are NOT deleted.")
                .setPositiveButton("Delete All", (d, w) -> {
                    libraryManager.clearAll();
                    reloadFromStorage();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.applyDestructiveConfirm(dialog);
        dialog.show();
    }

    // =========================================================
    // ITEM LONG-PRESS OPTIONS
    // =========================================================

    private void showItemOptions(LibraryManager.Entry entry, View anchorForHaptics) {
        anchorForHaptics.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

        String[] options = { "Change Cover", "Rename", "Delete from Library" };
        new AlertDialog.Builder(requireContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(LibraryManager.displayName(entry))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) startCoverPick(entry);
                    else if (which == 1) showRenameDialog(entry);
                    else confirmDeleteSingle(entry);
                })
                .show();
    }

    private void startCoverPick(LibraryManager.Entry entry) {
        pendingCoverTargetUri = entry.uri;
        imagePickerLauncher.launch("image/*");
    }

    private void showRenameDialog(LibraryManager.Entry entry) {
        Context ctx = requireContext();
        EditText input = new EditText(ctx);
        input.setText(LibraryManager.displayName(entry));
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        int pad = dpToPx(20);
        input.setPadding(pad, pad, pad, pad);

        AlertDialog dialog = new AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Rename")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    libraryManager.setCustomName(entry.uri, newName);
                    reloadFromStorage();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.whitenButtons(dialog);
        dialog.show();
    }

    private void confirmDeleteSingle(LibraryManager.Entry entry) {
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Remove from library?")
                .setMessage("\"" + LibraryManager.displayName(entry) + "\" will be removed from your PageVibe library. The original PDF file on your device is not affected.")
                .setPositiveButton("Remove", (d, w) -> {
                    libraryManager.removeEntry(entry.uri);
                    reloadFromStorage();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.applyDestructiveConfirm(dialog);
        dialog.show();
    }

    // =========================================================
    // READER LAUNCH
    // =========================================================

    private void openReader(Uri uri) {
        android.content.Intent i = new android.content.Intent(requireContext(), PdfActivity.class);
        i.setData(uri);
        startActivity(i);
    }

    // =========================================================
    // THUMBNAILS
    // =========================================================

    private void loadThumbAsync(LibraryManager.Entry entry, ImageView imgView, TextView initialView) {
        String cacheKey = entry.uri.toString() + "#" + entry.coverUpdatedAt;
        Context appContext = requireContext().getApplicationContext();

        bgExecutor.execute(() -> {
            Bitmap owned = null;
            try {
                if (entry.coverPath != null) {
                    owned = BitmapFactory.decodeFile(entry.coverPath);
                }
                if (owned == null) {
                    PdfCore core = new PdfCore();
                    try {
                        core.open(appContext, entry.uri);
                        if (core.pageCount() > 0) {
                            Bitmap rendered = core.renderPage(0, THUMB_W, THUMB_H);
                            if (rendered != null && !rendered.isRecycled()) {
                                owned = rendered.copy(Bitmap.Config.ARGB_8888, false);
                            }
                        }
                    } finally {
                        core.close();
                    }
                }
            } catch (Throwable ignored) {}

            if (owned == null) return;
            thumbnailCache.put(cacheKey, owned);

            Bitmap finalOwned = owned;
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                Object tag = imgView.getTag();
                if (cacheKey.equals(tag) && !finalOwned.isRecycled()) {
                    imgView.setImageBitmap(finalOwned);
                    imgView.setVisibility(View.VISIBLE);
                    initialView.setVisibility(View.GONE);
                }
            });
        });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private String relativeTime(long timestamp) {
        long d = System.currentTimeMillis() - timestamp;
        if (d < 60_000L)       return "Just now";
        if (d < 3_600_000L)    return (d / 60_000L)    + "m ago";
        if (d < 86_400_000L)   return (d / 3_600_000L) + "h ago";
        if (d < 7*86_400_000L) return (d / 86_400_000L)+ "d ago";
        return "Opened long ago";
    }

    // =========================================================
    // ADAPTER
    // =========================================================

    private class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_library_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            LibraryManager.Entry entry = displayEntries.get(pos);
            String name = LibraryManager.displayName(entry);

            h.title.setText(name);
            h.subtitle.setText(relativeTime(entry.lastOpenedAt));

            int totalPages = requireContext()
                    .getSharedPreferences("pagevibe_prefs", Context.MODE_PRIVATE)
                    .getInt("total_pages_" + entry.uri.hashCode(), 0);
            if (totalPages > 0) {
                h.pageBadge.setVisibility(View.VISIBLE);
                h.pageBadge.setText(totalPages + (totalPages == 1 ? " page" : " pages"));
            } else {
                h.pageBadge.setVisibility(View.GONE);
            }

            String cacheKey = entry.uri.toString() + "#" + entry.coverUpdatedAt;
            h.cover.setTag(cacheKey);
            h.cover.setImageBitmap(null);
            h.cover.setVisibility(View.GONE);
            h.initial.setVisibility(View.VISIBLE);
            h.initial.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());

            Bitmap cached = thumbnailCache.get(cacheKey);
            if (cached != null && !cached.isRecycled()) {
                h.cover.setImageBitmap(cached);
                h.cover.setVisibility(View.VISIBLE);
                h.initial.setVisibility(View.GONE);
            } else {
                loadThumbAsync(entry, h.cover, h.initial);
            }

            h.itemView.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return;
                openReader(displayEntries.get(p).uri);
            });

            h.itemView.setOnLongClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return true;
                showItemOptions(displayEntries.get(p), v);
                return true;
            });
        }

        @Override
        public int getItemCount() { return displayEntries.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView cover;
            TextView  initial, pageBadge, title, subtitle;

            VH(View v) {
                super(v);
                cover     = v.findViewById(R.id.libraryCardCover);
                initial   = v.findViewById(R.id.libraryCardInitial);
                pageBadge = v.findViewById(R.id.libraryCardPageBadge);
                title     = v.findViewById(R.id.libraryCardTitle);
                subtitle  = v.findViewById(R.id.libraryCardSubtitle);
            }
        }
    }
}