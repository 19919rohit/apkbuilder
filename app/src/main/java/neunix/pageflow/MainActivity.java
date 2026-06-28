package neunix.pageflow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // =========================================================
    // PREFS
    // =========================================================

    private static final String PREFS_NAME      = "pageflow_prefs";
    private static final String KEY_RECENT      = "recent_files";
    private static final String KEY_LAST_PAGE   = "last_page_";
    private static final String KEY_TOTAL_PAGES = "total_pages_";
    private static final int    MAX_RECENT      = 20;

    // =========================================================
    // VIEWS
    // =========================================================

    private TextView      greetingText;
    private View          continueSection;
    private View          continueCard;
    private ImageView     continueThumbnail;
    private View          continueThumbnailShimmer;
    private TextView      continueTitle;
    private TextView      continuePageInfo;
    private View          continueProgress;
    private TextView      continueProgressText;
    private View          recentSection;
    private RecyclerView  recentRecycler;
    private View          allFilesSection;
    private RecyclerView  allFilesRecycler;
    private View          emptyState;
    private View          btnOpenPdf;
    private TextView      clearAllBtn;

    // =========================================================
    // DATA
    // =========================================================

    private final List<RecentFile>          recentFiles  = new ArrayList<>();
    private final Map<String, Bitmap>       thumbnailCache = new HashMap<>();
    private BookCardAdapter                 cardAdapter;
    private FileRowAdapter                  rowAdapter;

    // =========================================================
    // BACKGROUND
    // =========================================================

    private final ExecutorService thumbExecutor =
            Executors.newFixedThreadPool(2);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // =========================================================
    // FILE PICKER
    // =========================================================

    private ActivityResultLauncher<Intent> pickerLauncher;

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        bindViews();
        registerPicker();
        setupRecyclers();
        setupButtons();
        animateEntrance();

        // Handle PDFs opened from external apps
        Intent incoming = getIntent();
        if (Intent.ACTION_VIEW.equals(incoming.getAction())
                && incoming.getData() != null) {
            Uri    uri  = incoming.getData();
            String name = FileUtils.getFileName(this, uri);
            addToRecent(uri, name, 0);
            openReader(uri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecent();
        refreshUI();
    }

    @Override
protected void onDestroy() {
    super.onDestroy();

    thumbExecutor.shutdownNow();

    if (continueThumbnail != null) continueThumbnail.setImageDrawable(null);

    if (recentRecycler != null) recentRecycler.setAdapter(null);
    if (allFilesRecycler != null) allFilesRecycler.setAdapter(null);

    thumbnailCache.clear();
}
    // =========================================================
    // BIND
    // =========================================================

    private void bindViews() {
        greetingText            = findViewById(R.id.greetingText);
        continueSection         = findViewById(R.id.continueSection);
        continueCard            = findViewById(R.id.continueCard);
        continueThumbnail       = findViewById(R.id.continueThumbnail);
        continueThumbnailShimmer= findViewById(R.id.continueThumbnailShimmer);
        continueTitle           = findViewById(R.id.continueTitle);
        continuePageInfo        = findViewById(R.id.continuePageInfo);
        continueProgress        = findViewById(R.id.continueProgress);
        continueProgressText    = findViewById(R.id.continueProgressText);
        recentSection           = findViewById(R.id.recentSection);
        recentRecycler          = findViewById(R.id.recentRecycler);
        allFilesSection         = findViewById(R.id.allFilesSection);
        allFilesRecycler        = findViewById(R.id.allFilesRecycler);
        emptyState              = findViewById(R.id.emptyState);
        btnOpenPdf              = findViewById(R.id.btnOpenPdf);
        clearAllBtn             = findViewById(R.id.clearAllBtn);

        // Greeting
        greetingText.setText(getGreeting());
    }

    // =========================================================
    // RECYCLERS
    // =========================================================

    private void setupRecyclers() {
        // Horizontal book cards
        cardAdapter = new BookCardAdapter();
        recentRecycler.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recentRecycler.setAdapter(cardAdapter);
        recentRecycler.setHasFixedSize(false);

        // Vertical file rows
        rowAdapter = new FileRowAdapter();
        allFilesRecycler.setLayoutManager(
                new LinearLayoutManager(this));
        allFilesRecycler.setAdapter(rowAdapter);
        allFilesRecycler.setHasFixedSize(false);
        allFilesRecycler.setNestedScrollingEnabled(false);
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private void setupButtons() {
        btnOpenPdf.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                    .withEndAction(() ->
                            v.animate().scaleX(1f).scaleY(1f).setDuration(80)
                                    .withEndAction(this::openFilePicker).start())
                    .start();
        });

        clearAllBtn.setOnClickListener(v -> {
            for (RecentFile f : recentFiles) {
                FileUtils.evictCacheForUri(this, f.uri);
            }
            recentFiles.clear();
            thumbnailCache.clear();
            saveRecent();
            refreshUI();
        });
    }

    // =========================================================
    // FILE PICKER
    // =========================================================

    private void registerPicker() {
        pickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) return;
                    Intent data = result.getData();
                    if (data == null || data.getData() == null) return;

                    Uri uri = data.getData();
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) { }

                    String name = FileUtils.getFileName(this, uri);
                    addToRecent(uri, name, 0);
                    openReader(uri);
                }
        );
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/pdf");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                 | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pickerLauncher.launch(i);
    }

    // =========================================================
    // READER
    // =========================================================

    private void openReader(Uri uri) {
        Intent i = new Intent(this, PdfActivity.class);
        i.setData(uri);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // =========================================================
    // REFRESH UI
    // =========================================================

    private void refreshUI() {
        boolean empty = recentFiles.isEmpty();

        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        continueSection.setVisibility(empty ? View.GONE : View.VISIBLE);
        recentSection.setVisibility(empty ? View.GONE : View.VISIBLE);
        allFilesSection.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (!empty) {
            setupContinueCard(recentFiles.get(0));
            cardAdapter.notifyDataSetChanged();
            rowAdapter.notifyDataSetChanged();

            // Load thumbnails for visible items
            for (int i = 0; i < Math.min(recentFiles.size(), 6); i++) {
                loadThumbnailAsync(recentFiles.get(i));
            }
        }
    }

    // =========================================================
    // CONTINUE READING CARD
    // =========================================================

    private void setupContinueCard(RecentFile file) {
        continueTitle.setText(cleanFileName(file.name));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int lastPage   = prefs.getInt(KEY_LAST_PAGE   + file.uri.hashCode(), 0);
        int totalPages = prefs.getInt(KEY_TOTAL_PAGES + file.uri.hashCode(), 0);

        if (totalPages > 0) {
            continuePageInfo.setText("Page " + (lastPage + 1) + " of " + totalPages);
            float pct = (float) lastPage / Math.max(1, totalPages - 1);

            // Animate progress bar width
            continueProgress.post(() -> {
                int maxW = ((View) continueProgress.getParent()).getWidth();
                continueProgress.getLayoutParams().width = (int)(maxW * pct);
                continueProgress.requestLayout();
            });

            int pctInt = (int)(pct * 100);
            continueProgressText.setText(pctInt + "% complete");
        } else {
            continuePageInfo.setText("Not started");
            continueProgress.getLayoutParams().width = 0;
            continueProgressText.setText("");
        }

        continueCard.setOnClickListener(v -> {
            addToRecent(file.uri, file.name, totalPages);
            openReader(file.uri);
        });

        // Load thumbnail
        Bitmap cached = thumbnailCache.get(file.uri.toString());
        if (cached != null && !cached.isRecycled()) {
            continueThumbnail.setImageBitmap(cached);
            continueThumbnailShimmer.setVisibility(View.GONE);
        } else {
            continueThumbnailShimmer.setVisibility(View.VISIBLE);
            loadThumbnailAsync(file);
        }
    }

    // =========================================================
    // THUMBNAIL LOADING
    // =========================================================

    private void loadThumbnailAsync(RecentFile file) {
        String key = file.uri.toString();
        if (thumbnailCache.containsKey(key)) return;

        thumbExecutor.submit(() -> {
            try {
                PdfCore core = new PdfCore();
                core.open(this, file.uri);
                core.setScreenSize(240, 320);

                if (core.pageCount() == 0) {
                    core.close();
                    return;
                }

                // Save total pages while we have the core open
                int total = core.pageCount();
                SharedPreferences prefs =
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                if (prefs.getInt(KEY_TOTAL_PAGES + file.uri.hashCode(), 0) == 0) {
                    prefs.edit()
                         .putInt(KEY_TOTAL_PAGES + file.uri.hashCode(), total)
                         .apply();
                }

                Bitmap thumb = core.renderPage(0, 240, 320);
                core.close();

                uiHandler.post(() -> {
                    thumbnailCache.put(key, thumb);
                    onThumbnailLoaded(file.uri, thumb);
                });

            } catch (Exception ignored) { }
        });
    }

    private void onThumbnailLoaded(Uri uri, Bitmap thumb) {
        // Update continue card if it matches
        if (!recentFiles.isEmpty() && recentFiles.get(0).uri.equals(uri)) {
            continueThumbnail.setImageBitmap(thumb);
            continueThumbnailShimmer.setVisibility(View.GONE);
            continueThumbnail.setAlpha(0f);
            continueThumbnail.animate().alpha(1f).setDuration(300).start();
        }

        // Notify adapters
        cardAdapter.notifyDataSetChanged();
        rowAdapter.notifyDataSetChanged();
    }

    // =========================================================
    // ENTRANCE ANIMATION
    // =========================================================

    private void animateEntrance() {
        View[] views = {
                findViewById(R.id.headerSection),
                continueSection,
                recentSection,
                allFilesSection
        };

        for (int i = 0; i < views.length; i++) {
            if (views[i] == null) continue;
            views[i].setAlpha(0f);
            views[i].setTranslationY(24f);
            views[i].animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setStartDelay(i * 80L)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
        }
    }

    // =========================================================
    // RECENT PERSISTENCE
    // =========================================================

    private void loadRecent() {
        recentFiles.clear();
        try {
            SharedPreferences prefs =
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_RECENT, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                recentFiles.add(new RecentFile(
                        Uri.parse(obj.getString("uri")),
                        obj.getString("name"),
                        obj.optLong("timestamp", 0L),
                        obj.optInt("totalPages", 0)
                ));
            }
        } catch (Exception ignored) { }
    }

    private void saveRecent() {
        try {
            JSONArray arr = new JSONArray();
            for (RecentFile f : recentFiles) {
                JSONObject obj = new JSONObject();
                obj.put("uri",        f.uri.toString());
                obj.put("name",       f.name);
                obj.put("timestamp",  f.timestamp);
                obj.put("totalPages", f.totalPages);
                arr.put(obj);
            }
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_RECENT, arr.toString())
                    .apply();
        } catch (Exception ignored) { }
    }

    private void addToRecent(Uri uri, String name, int totalPages) {
        recentFiles.removeIf(f -> f.uri.equals(uri));
        recentFiles.add(0, new RecentFile(
                uri, name, System.currentTimeMillis(), totalPages));

        if (recentFiles.size() > MAX_RECENT) {
            recentFiles.subList(MAX_RECENT, recentFiles.size()).clear();
        }

        saveRecent();
    }

    private void removeFromRecent(int position) {
        if (position < 0 || position >= recentFiles.size()) return;
        RecentFile removed = recentFiles.get(position);
        FileUtils.evictCacheForUri(this, removed.uri);
        thumbnailCache.remove(removed.uri.toString());
        recentFiles.remove(position);
        saveRecent();
        refreshUI();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    /** Strips .pdf extension and underscores for display */
    private String cleanFileName(String name) {
        return name.replaceAll("(?i)\\.pdf$", "")
                   .replace("_", " ")
                   .trim();
    }

    // =========================================================
    // DATA MODEL
    // =========================================================

    private static class RecentFile {
        final Uri    uri;
        final String name;
        final long   timestamp;
        final int    totalPages;

        RecentFile(Uri uri, String name, long timestamp, int totalPages) {
            this.uri        = uri;
            this.name       = name;
            this.timestamp  = timestamp;
            this.totalPages = totalPages;
        }

        String relativeTime() {
            long diff = System.currentTimeMillis() - timestamp;
            if (diff < 60_000L)          return "Just now";
            if (diff < 3_600_000L)       return (diff / 60_000L)     + "m ago";
            if (diff < 86_400_000L)      return (diff / 3_600_000L)  + "h ago";
            if (diff < 7 * 86_400_000L)  return (diff / 86_400_000L) + "d ago";
            return "Long ago";
        }

        String initial() {
            return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        }
    }

    // =========================================================
    // ADAPTER: HORIZONTAL BOOK CARDS
    // =========================================================

    private class BookCardAdapter extends RecyclerView.Adapter<BookCardAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.item_book_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            RecentFile file = recentFiles.get(position);
            h.title.setText(cleanFileName(file.name));
            h.time.setText(file.relativeTime());

            Bitmap thumb = thumbnailCache.get(file.uri.toString());
            if (thumb != null && !thumb.isRecycled()) {
                h.cover.setImageBitmap(thumb);
                h.cover.setVisibility(View.VISIBLE);
                h.initial.setVisibility(View.GONE);
            } else {
                h.cover.setVisibility(View.GONE);
                h.initial.setText(file.initial());
                h.initial.setVisibility(View.VISIBLE);
            }

            h.itemView.setOnClickListener(v -> {
                addToRecent(file.uri, file.name, file.totalPages);
                openReader(file.uri);
            });

            h.remove.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) removeFromRecent(pos);
            });

            // Stagger animation
            h.itemView.setAlpha(0f);
            h.itemView.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setStartDelay(position * 50L)
                    .start();
        }

        @Override public int getItemCount() { return recentFiles.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView  cover;
            TextView   initial;
            TextView   title;
            TextView   time;
            ImageButton remove;

            VH(View v) {
                super(v);
                cover   = v.findViewById(R.id.bookCover);
                initial = v.findViewById(R.id.bookInitial);
                title   = v.findViewById(R.id.bookTitle);
                time    = v.findViewById(R.id.bookTime);
                remove  = v.findViewById(R.id.bookRemove);
            }
        }
    }

    // =========================================================
    // ADAPTER: VERTICAL FILE ROWS
    // =========================================================

    private class FileRowAdapter extends RecyclerView.Adapter<FileRowAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.item_file_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            RecentFile file = recentFiles.get(position);
            h.name.setText(cleanFileName(file.name));
            h.time.setText(file.relativeTime());

            Bitmap thumb = thumbnailCache.get(file.uri.toString());
            if (thumb != null && !thumb.isRecycled()) {
                h.thumbnail.setImageBitmap(thumb);
                h.thumbnail.setVisibility(View.VISIBLE);
                h.initial.setVisibility(View.GONE);
            } else {
                h.thumbnail.setVisibility(View.GONE);
                h.initial.setText(file.initial());
                h.initial.setVisibility(View.VISIBLE);
            }

            h.itemView.setOnClickListener(v -> {
                addToRecent(file.uri, file.name, file.totalPages);
                openReader(file.uri);
            });

            h.remove.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) removeFromRecent(pos);
            });
        }

        @Override public int getItemCount() { return recentFiles.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView   thumbnail;
            TextView    initial;
            TextView    name;
            TextView    time;
            ImageButton remove;

            VH(View v) {
                super(v);
                thumbnail = v.findViewById(R.id.rowThumbnail);
                initial   = v.findViewById(R.id.rowInitial);
                name      = v.findViewById(R.id.rowName);
                time      = v.findViewById(R.id.rowTime);
                remove    = v.findViewById(R.id.rowRemove);
            }
        }
    }
}