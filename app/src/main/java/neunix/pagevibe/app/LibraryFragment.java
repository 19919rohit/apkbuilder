package neunix.pagevibe.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
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

    private LibraryManager      libraryManager;
    private PdfHighlightManager highlightManager;
    private PdfNotesManager     notesManager;
    private ThemeManager        themeManager;

    private final List<LibraryManager.Entry> allEntries     = new ArrayList<>();
    private final List<LibraryManager.Entry> displayEntries = new ArrayList<>();

    private View    root;
    private LibraryAdapter adapter;
    private RecyclerView   recycler;
    private View           emptyState;
    private TextView       noResultsText;
    private TextView       countLabel;
    private TextView       sortChip;
    private EditText       searchInput;
    private ImageButton    btnOverflow;
    private View           importingOverlay;

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
        root = inflater.inflate(R.layout.fragment_library, container, false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        libraryManager   = new LibraryManager(requireContext());
        highlightManager = new PdfHighlightManager(requireContext());
        notesManager     = new PdfNotesManager(requireContext());
        themeManager     = new ThemeManager(requireContext());

        recycler         = view.findViewById(R.id.libraryRecycler);
        emptyState       = view.findViewById(R.id.libraryEmptyState);
        noResultsText    = view.findViewById(R.id.libraryNoResults);
        countLabel       = view.findViewById(R.id.libraryCountLabel);
        sortChip         = view.findViewById(R.id.librarySortChip);
        searchInput      = view.findViewById(R.id.librarySearchInput);
        btnOverflow      = view.findViewById(R.id.btnLibraryOverflow);
        importingOverlay = view.findViewById(R.id.libraryImportingOverlay);

        adapter = new LibraryAdapter();
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recycler.setAdapter(adapter);

        emptyState.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("application/pdf");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9001 && resultCode == android.app.Activity.RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                requireContext().getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            openReader(uri);
        }
    }

    @Override public void onResume() { super.onResume(); reloadFromStorage(); applyTheme(); }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) { reloadFromStorage(); applyTheme(); }
    }

    @Override public void onDestroyView() { super.onDestroyView(); thumbnailCache.evictAll(); root = null; }

    private void applyTheme() {
        if (root == null || themeManager == null) return;
        ThemeManager.AppTheme theme = themeManager.getActiveTheme();
        ThemeApplier.apply(root, theme);
        if (adapter != null) adapter.notifyDataSetChanged();
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
    // SORT POPUP — theme-aware reference implementation. The XML tags
    // handle the popup's own background/divider/font; the SELECTED
    // (accent) vs unselected (textPrimary) text color logic below has to
    // stay in code because it's stateful, so it now reads colors from
    // the active theme instead of hardcoded hex.
    // =========================================================

    private void showSortPopup(View anchor) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.popup_sort_menu, null);
        ThemeManager.AppTheme theme = themeManager.getActiveTheme();
        ThemeApplier.apply(content, theme);

        TextView optRecent = content.findViewById(R.id.sortOptionRecent);
        TextView optAlpha  = content.findViewById(R.id.sortOptionAlpha);

        styleSortOption(optRecent, sortMode == SortMode.RECENT, theme);
        styleSortOption(optAlpha,  sortMode == SortMode.ALPHA, theme);

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

    private void styleSortOption(TextView tv, boolean selected, ThemeManager.AppTheme theme) {
        tv.setTextColor(selected ? theme.accentColor : theme.textPrimaryColor);
    }

    // =========================================================
    // OVERFLOW POPUP — Import from URL, Delete All PDFs
    // =========================================================

    private void showOverflowPopup(View anchor) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.popup_library_overflow, null);
        ThemeApplier.apply(content, themeManager.getActiveTheme());

        TextView importUrl = content.findViewById(R.id.btnImportFromUrl);
        TextView deleteAll = content.findViewById(R.id.btnDeleteAllPdfs);

        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(16f);

        importUrl.setOnClickListener(v -> { popup.dismiss(); promptImportFromUrl(); });
        deleteAll.setOnClickListener(v -> { popup.dismiss(); confirmDeleteAll(); });

        popup.showAsDropDown(anchor, 0, 8, Gravity.END);
    }

    private void promptImportFromUrl() {
        Context ctx = requireContext();
        EditText input = new EditText(ctx);
        input.setHint("https://example.com/document.pdf");
        input.setHintTextColor(Color.parseColor("#666666"));
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        int pad = dpToPx(20);
        input.setPadding(pad, pad, pad, pad);

        AlertDialog dialog = new AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Import from URL")
                .setMessage("Paste a direct link to a PDF file. It will be downloaded and added to your library.")
                .setView(input)
                .setPositiveButton("Import", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) beginUrlImport(url);
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.whitenButtons(dialog);
        dialog.show();
    }

    private void beginUrlImport(String url) {
        if (importingOverlay != null) {
            importingOverlay.setAlpha(0f);
            importingOverlay.setVisibility(View.VISIBLE);
            importingOverlay.animate().alpha(1f).setDuration(150).start();
        }

        UrlPdfImporter.importFromUrl(requireContext(), url, new UrlPdfImporter.Callback() {
            @Override
            public void onSuccess(Uri savedUri, String fileName) {
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    hideImportingOverlay();
                    libraryManager.addOrTouch(savedUri, fileName);
                    reloadFromStorage();
                    Toast.makeText(requireContext(), "Imported \"" + fileName + "\"", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String message) {
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    hideImportingOverlay();
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void hideImportingOverlay() {
        if (importingOverlay == null) return;
        importingOverlay.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> importingOverlay.setVisibility(View.GONE)).start();
    }

    private void confirmDeleteAll() {
        if (allEntries.isEmpty()) return;
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Delete all PDFs?")
                .setMessage("This removes every PDF from your PageVibe library, including custom names, covers, notes, and highlights. Your original PDF files on your device are NOT deleted.")
                .setPositiveButton("Delete All", (d, w) -> {
                    for (LibraryManager.Entry e : allEntries) {
                        highlightManager.clearForDocument(e.uri);
                        notesManager.clearForDocument(e.uri);
                    }
                    libraryManager.clearAll();
                    reloadFromStorage();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.applyDestructiveConfirm(dialog);
        dialog.show();
    }

    // =========================================================
    // PER-ITEM THREE-DOT MENU
    // =========================================================

    private void showItemMenu(View anchor, LibraryManager.Entry entry) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.popup_library_item_menu, null);
        ThemeApplier.apply(content, themeManager.getActiveTheme());

        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(16f);

        content.findViewById(R.id.itemMenuChangeCover).setOnClickListener(v -> { popup.dismiss(); startCoverPick(entry); });
        content.findViewById(R.id.itemMenuRename).setOnClickListener(v -> { popup.dismiss(); showRenameDialog(entry); });
        content.findViewById(R.id.itemMenuShare).setOnClickListener(v -> { popup.dismiss(); shareEntry(entry); });
        content.findViewById(R.id.itemMenuNotes).setOnClickListener(v -> { popup.dismiss(); openNotes(entry); });
        content.findViewById(R.id.itemMenuDelete).setOnClickListener(v -> { popup.dismiss(); confirmDeleteSingle(entry); });

        popup.showAsDropDown(anchor, 0, 8, Gravity.END);
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

    private void shareEntry(LibraryManager.Entry entry) {
        try {
            Uri shareUri = entry.uri;
            if ("file".equals(shareUri.getScheme())) {
                shareUri = FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".fileprovider",
                        new java.io.File(shareUri.getPath()));
            }
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share PDF"));
        } catch (Throwable t) {
            Toast.makeText(requireContext(), "Could not share this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotes(LibraryManager.Entry entry) {
        Intent i = new Intent(requireContext(), NotesActivity.class);
        i.putExtra(NotesActivity.EXTRA_PDF_URI, entry.uri.toString());
        i.putExtra(NotesActivity.EXTRA_PDF_NAME, LibraryManager.displayName(entry));
        startActivity(i);
    }

    private void confirmDeleteSingle(LibraryManager.Entry entry) {
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Remove from library?")
                .setMessage("\"" + LibraryManager.displayName(entry) + "\" will be removed from your PageVibe library, including its notes and highlights. The original PDF file on your device is not affected.")
                .setPositiveButton("Remove", (d, w) -> {
                    highlightManager.clearForDocument(entry.uri);
                    notesManager.clearForDocument(entry.uri);
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
        Intent i = new Intent(requireContext(), PdfActivity.class);
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

            h.overflow.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return;
                showItemMenu(v, displayEntries.get(p));
            });

            if (themeManager != null) {
                ThemeApplier.applyToSingleView(h.itemView, themeManager.getActiveTheme());
            }
        }

        @Override
        public int getItemCount() { return displayEntries.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView   cover;
            TextView    initial, pageBadge, title, subtitle;
            ImageButton overflow;

            VH(View v) {
                super(v);
                cover     = v.findViewById(R.id.libraryCardCover);
                initial   = v.findViewById(R.id.libraryCardInitial);
                pageBadge = v.findViewById(R.id.libraryCardPageBadge);
                title     = v.findViewById(R.id.libraryCardTitle);
                subtitle  = v.findViewById(R.id.libraryCardSubtitle);
                overflow  = v.findViewById(R.id.libraryCardOverflow);
            }
        }
    }
}