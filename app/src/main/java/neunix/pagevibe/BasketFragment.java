package neunix.pagevibe;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BasketFragment extends Fragment {

    private static final int THUMB_W = 220;
    private static final int THUMB_H = 300;
    private static final int THUMB_CACHE_SIZE = 40;

    private PageBasketManager basketManager;
    private LibraryManager    libraryManager;
    private final List<PageBasketManager.BasketEntry> entries = new ArrayList<>();
    private BasketAdapter adapter;

    private View emptyState;
    private View bottomBar;
    private TextView countLabel;
    private View btnExport;
    private View btnClearAll;
    private View exportingOverlay;
    private View rootView;

    private String pendingExportName = null;

    private final ExecutorService bgExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "BasketThumb"));

    private final LruCache<String, Bitmap> thumbnailCache =
            new LruCache<String, Bitmap>(THUMB_CACHE_SIZE) {
                @Override
                protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                    if (evicted && oldValue != null && !oldValue.isRecycled()) {
                        try { oldValue.recycle(); } catch (Throwable ignored) {}
                    }
                }
            };

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingExportName != null) {
                    doExport(pendingExportName);
                } else if (!granted) {
                    Toast.makeText(requireContext(),
                            "Storage permission is needed to save to Documents", Toast.LENGTH_LONG).show();
                }
                pendingExportName = null;
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_basket, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;
        basketManager  = new PageBasketManager(requireContext());
        libraryManager = new LibraryManager(requireContext());

        RecyclerView recycler = view.findViewById(R.id.basketRecycler);
        emptyState       = view.findViewById(R.id.basketEmptyState);
        bottomBar        = view.findViewById(R.id.basketBottomBar);
        countLabel       = view.findViewById(R.id.basketCountLabel);
        btnExport        = view.findViewById(R.id.btnExportBasket);
        btnClearAll      = view.findViewById(R.id.btnClearBasket);
        exportingOverlay = view.findViewById(R.id.exportingOverlay);

        adapter = new BasketAdapter();
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recycler.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                   @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                Collections.swap(entries, from, to);
                adapter.notifyItemMoved(from, to);
                basketManager.moveEntry(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Deliberately disabled — removal only via the explicit
                // button on each card.
            }
        });
        touchHelper.attachToRecyclerView(recycler);

        btnClearAll.setOnClickListener(v -> confirmClearAll());
        btnExport.setOnClickListener(v -> promptForFileName());

        refreshFromStorage();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFromStorage();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refreshFromStorage();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        thumbnailCache.evictAll();
        rootView = null;
    }

    private void refreshFromStorage() {
        if (basketManager == null) return;
        entries.clear();
        entries.addAll(basketManager.getAll());
        if (adapter != null) adapter.notifyDataSetChanged();

        boolean empty = entries.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(empty ? View.GONE : View.VISIBLE);
        countLabel.setText(entries.size() + (entries.size() == 1 ? " page" : " pages"));
    }

    /** Prefers the Library's current display name (which may have been
     *  renamed after this page was added to the basket) over the name
     *  frozen at add-time, so a rename in Library "appears everywhere"
     *  including here. */
    private String resolveDisplayName(PageBasketManager.BasketEntry entry) {
        LibraryManager.Entry libEntry = libraryManager.findByUri(entry.sourceUri);
        if (libEntry != null) return LibraryManager.displayName(libEntry);
        return entry.sourceName != null
                ? LibraryManager.cleanFileName(entry.sourceName)
                : "PDF";
    }

    private void confirmClearAll() {
        if (entries.isEmpty()) return;
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Clear basket?")
                .setMessage("This removes every page from your basket. The original PDF files are not affected.")
                .setPositiveButton("Clear all", (d, w) -> {
                    basketManager.clearAll();
                    refreshFromStorage();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.applyDestructiveConfirm(dialog);
        dialog.show();
    }

    private void promptForFileName() {
        if (entries.isEmpty()) return;

        android.content.Context ctx = requireContext();
        EditText input = new EditText(ctx);
        input.setText(defaultExportName());
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#666666"));
        int pad = dpToPx(20);
        input.setPadding(pad, pad, pad, pad);

        AlertDialog dialog = new AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Save as")
                .setMessage("Your " + entries.size() + " basket page" + (entries.size() == 1 ? "" : "s")
                        + " will be merged into one PDF and saved to your device's Documents folder.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = defaultExportName();
                    beginExport(name);
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.whitenButtons(dialog);
        dialog.show();
    }

    // FIXED: was "PageFlow Basket " — now matches the app's actual name.
    private String defaultExportName() {
        return "PageVibe Basket " + new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());
    }

    private void beginExport(String desiredName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(requireContext(),
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingExportName = desiredName;
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        doExport(desiredName);
    }

    private void doExport(String desiredName) {
        exportingOverlay.setAlpha(0f);
        exportingOverlay.setVisibility(View.VISIBLE);
        exportingOverlay.animate().alpha(1f).setDuration(150).start();

        List<PageBasketManager.BasketEntry> snapshot = new ArrayList<>(entries);
        PageBasketExporter.exportToDocuments(requireContext(), snapshot, desiredName, new PageBasketExporter.Callback() {
            @Override
            public void onSuccess(Uri savedUri, String finalFileName) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    hideExportingOverlay();
                    libraryManager.addOrTouch(savedUri, finalFileName);
                    showSuccessSnackbar(finalFileName);
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    hideExportingOverlay();
                    if (rootView != null) {
                        Snackbar.make(rootView, "Export failed: " + message, Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void hideExportingOverlay() {
        exportingOverlay.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> exportingOverlay.setVisibility(View.GONE)).start();
    }

    private void showSuccessSnackbar(String fileName) {
        if (rootView == null) return;
        Snackbar sb = Snackbar.make(rootView, "Saved \"" + fileName + "\" to Documents", Snackbar.LENGTH_LONG);
        sb.setAction("Open", v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToHomeTab();
            }
        });
        sb.setBackgroundTint(Color.parseColor("#1E1E1E"));
        sb.setActionTextColor(Color.parseColor("#4488FF"));
        sb.setTextColor(Color.WHITE);
        sb.show();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void loadThumbnailAsync(PageBasketManager.BasketEntry entry, ImageView thumbView, TextView initialView) {
        String cacheKey = entry.sourceUri.toString() + "#" + entry.pageIndex;
        android.content.Context appContext = requireContext().getApplicationContext();

        bgExecutor.execute(() -> {
            PdfCore core = new PdfCore();
            Bitmap owned = null;
            try {
                core.open(appContext, entry.sourceUri);
                if (entry.pageIndex >= 0 && entry.pageIndex < core.pageCount()) {
                    Bitmap rendered = core.renderPage(entry.pageIndex, THUMB_W, THUMB_H);
                    if (rendered != null && !rendered.isRecycled()) {
                        owned = rendered.copy(Bitmap.Config.ARGB_8888, false);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                core.close();
            }
            if (owned == null) return;
            thumbnailCache.put(cacheKey, owned);

            Bitmap finalOwned = owned;
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                Object tag = thumbView.getTag();
                if (cacheKey.equals(tag) && !finalOwned.isRecycled()) {
                    thumbView.setImageBitmap(finalOwned);
                    initialView.setVisibility(View.GONE);
                }
            });
        });
    }

    private class BasketAdapter extends RecyclerView.Adapter<BasketAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_basket_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            if (pos >= entries.size()) return;
            PageBasketManager.BasketEntry entry = entries.get(pos);

            String cleanName = resolveDisplayName(entry);
            h.title.setText(cleanName);
            h.pageBadge.setText("Pg " + (entry.pageIndex + 1));

            String cacheKey = entry.sourceUri.toString() + "#" + entry.pageIndex;
            h.thumbnail.setTag(cacheKey);
            h.thumbnail.setImageBitmap(null);
            h.initial.setVisibility(View.VISIBLE);
            h.initial.setText(cleanName.isEmpty() ? "?" : cleanName.substring(0, 1).toUpperCase());

            Bitmap cached = thumbnailCache.get(cacheKey);
            if (cached != null && !cached.isRecycled()) {
                h.thumbnail.setImageBitmap(cached);
                h.initial.setVisibility(View.GONE);
            } else {
                loadThumbnailAsync(entry, h.thumbnail, h.initial);
            }

            h.remove.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p == RecyclerView.NO_POSITION || p >= entries.size()) return;
                PageBasketManager.BasketEntry removed = entries.get(p);
                basketManager.removeById(removed.id);
                entries.remove(p);
                notifyItemRemoved(p);
                refreshFromStorage();
            });
        }

        @Override
        public int getItemCount() { return entries.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            TextView  initial;
            TextView  pageBadge;
            TextView  title;
            View      remove;

            VH(View v) {
                super(v);
                thumbnail = v.findViewById(R.id.basketCardThumbnail);
                initial   = v.findViewById(R.id.basketCardInitial);
                pageBadge = v.findViewById(R.id.basketCardPageBadge);
                title     = v.findViewById(R.id.basketCardTitle);
                remove    = v.findViewById(R.id.basketCardRemove);
            }
        }
    }
}