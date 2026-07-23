package neunix.pagevibe;

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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home tab — a lightweight dashboard, not a duplicate of Library. Shows
 * the single most-recent book as a hero "Continue Reading" card, plus a
 * horizontal strip of the 5 most-recently-opened books. Search, sorting,
 * and full-library management (rename, cover, delete) all live in the
 * Library tab; Home only reads from LibraryManager, it never writes to it.
 */
public class HomeFragment extends Fragment {

    private static final int RECENT_LIMIT = 5;

    private View          root;
    private TextView      greetingText;
    private View          insightsCard;
    private TextView      insightsPrimaryText;
    private TextView      insightsSecondaryText;
    private View          continueSection;
    private View          continueCard;
    private ImageView     continueThumbnail;
    private TextView      continueInitial;
    private TextView      continueTitle;
    private TextView      continueSubtitle;
    private View          progressFill;
    private View          progressTrack;
    private TextView      progressLabel;
    private View          recentSection;
    private TextView      btnSeeAllRecent;
    private RecyclerView  recentStrip;
    private View          emptyState;
    private View          btnOpenPdf;
    private ImageButton   btnAbout;

    private LibraryManager         libraryManager;
    private ReadingStatsController stats;

    private final List<LibraryManager.Entry> recentEntries = new ArrayList<>();
    private final Map<String, Bitmap> thumbCache = new LinkedHashMap<>();

    private RecentAdapter recentAdapter;

    private final ExecutorService thumbExecutor =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "Thumb-Loader");
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> pickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != android.app.Activity.RESULT_OK) return;
                Intent data = result.getData();
                if (data == null || data.getData() == null) return;

                Uri uri = data.getData();
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }

                openReader(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.activity_home, container, false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        libraryManager = new LibraryManager(requireContext());
        stats = new ReadingStatsController(requireContext());
        bindViews();
        setupRecycler();
        setupButtons();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUI(false);
        bindInsights();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            refreshUI(false);
            bindInsights();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        thumbExecutor.shutdown();
        clearThumbCache();
        root = null;
    }

    private void bindViews() {
        greetingText          = root.findViewById(R.id.greetingText);
        insightsCard          = root.findViewById(R.id.insightsCard);
        insightsPrimaryText   = root.findViewById(R.id.insightsPrimaryText);
        insightsSecondaryText = root.findViewById(R.id.insightsSecondaryText);
        continueSection       = root.findViewById(R.id.continueSection);
        continueCard          = root.findViewById(R.id.continueCard);
        continueThumbnail     = root.findViewById(R.id.continueThumbnail);
        continueInitial       = root.findViewById(R.id.continueInitial);
        continueTitle         = root.findViewById(R.id.continueTitle);
        continueSubtitle      = root.findViewById(R.id.continueSubtitle);
        progressFill    = root.findViewById(R.id.progressFill);
        progressTrack   = root.findViewById(R.id.progressTrack);
        progressLabel   = root.findViewById(R.id.progressLabel);
        recentSection   = root.findViewById(R.id.recentSection);
        btnSeeAllRecent = root.findViewById(R.id.btnSeeAllRecent);
        recentStrip     = root.findViewById(R.id.recentStrip);
        emptyState      = root.findViewById(R.id.emptyState);
        btnOpenPdf      = root.findViewById(R.id.btnOpenPdf);
        btnAbout        = root.findViewById(R.id.btnAbout);

        greetingText.setText(greeting());
    }

    private void setupRecycler() {
        recentAdapter = new RecentAdapter();
        recentStrip.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        recentStrip.setAdapter(recentAdapter);
        recentStrip.setHasFixedSize(false);
    }

    private void setupButtons() {
        btnAbout.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AboutActivity.class)));

        insightsCard.setOnClickListener(v -> {
            bouncePress(insightsCard);
            startActivity(new Intent(requireContext(), StatsActivity.class));
        });

        btnOpenPdf.setOnClickListener(v ->
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                        .withEndAction(() ->
                                v.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(80)
                                        .withEndAction(this::openFilePicker)
                                        .start())
                        .start());

        emptyState.setOnClickListener(v -> openFilePicker());

        btnSeeAllRecent.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToLibraryTab();
            }
        });
    }

    private void bouncePress(View v) {
        v.animate().cancel();
        v.setScaleX(0.97f);
        v.setScaleY(0.97f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(new OvershootInterpolator(2.5f)).start();
    }

    private void bindInsights() {
        if (stats == null || insightsPrimaryText == null || libraryManager == null) return;

        int streak = stats.getCurrentStreakDays();
        List<ReadingStatsController.DayEntry> days = stats.getRecentDayEntries();
        long todaySeconds = days.isEmpty() ? 0L : days.get(0).seconds;

        if (streak > 0) {
            insightsPrimaryText.setText("🔥 " + streak + " day streak");
        } else if (stats.getTotalSeconds() > 0) {
            insightsPrimaryText.setText("Keep reading to build a streak");
        } else {
            insightsPrimaryText.setText("Track your reading progress");
        }

        if (todaySeconds > 0) {
            insightsSecondaryText.setText(ReadingStatsController.formatDuration(todaySeconds) + " read today");
        } else {
            int libSize = libraryManager.size();
            insightsSecondaryText.setText(libSize > 0
                    ? libSize + (libSize == 1 ? " PDF in your library" : " PDFs in your library")
                    : "Tap to see your stats");
        }
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/pdf");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                 | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pickerLauncher.launch(i);
    }

    private void openReader(Uri uri) {
        Intent i = new Intent(requireContext(), PdfActivity.class);
        i.setData(uri);
        startActivity(i);
        requireActivity().overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out);
    }

    private void refreshUI(boolean animate) {
        if (root == null || libraryManager == null) return;

        List<LibraryManager.Entry> all = libraryManager.getAll();
        Collections.sort(all, (a, b) -> Long.compare(b.lastOpenedAt, a.lastOpenedAt));

        recentEntries.clear();
        for (int i = 0; i < Math.min(all.size(), RECENT_LIMIT); i++) recentEntries.add(all.get(i));

        boolean empty = recentEntries.isEmpty();
        emptyState.setVisibility(     empty ? View.VISIBLE : View.GONE);
        continueSection.setVisibility(empty ? View.GONE    : View.VISIBLE);
        recentSection.setVisibility(  empty ? View.GONE    : View.VISIBLE);

        if (!empty) {
            bindContinueCard(recentEntries.get(0));
            recentAdapter.notifyDataSetChanged();
            for (LibraryManager.Entry e : recentEntries) loadThumbAsync(e);
        }

        if (animate) animateEntrance();
    }

    private void bindContinueCard(LibraryManager.Entry entry) {
        String name = LibraryManager.displayName(entry);
        continueTitle.setText(name);
        continueInitial.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());

        SharedPreferences prefs =
                requireContext().getSharedPreferences("pagevibe_prefs", Context.MODE_PRIVATE);
        int lastPage   = prefs.getInt("last_page_"   + entry.uri.hashCode(), 0);
        int totalPages = prefs.getInt("total_pages_" + entry.uri.hashCode(), 0);

        if (totalPages > 1) {
            continueSubtitle.setText(
                    "Page " + (lastPage + 1) + " of " + totalPages
                    + "  ·  " + relativeTime(entry.lastOpenedAt));

            float pct = (float) lastPage / (totalPages - 1);
            progressTrack.post(() -> {
                if (root == null) return;
                int maxW = progressTrack.getWidth();
                ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
                lp.width = (int)(maxW * pct);
                progressFill.setLayoutParams(lp);
            });
            progressLabel.setText((int)(pct * 100) + "% complete");

        } else if (totalPages == 1) {
            continueSubtitle.setText("1 page  ·  " + relativeTime(entry.lastOpenedAt));
            resetProgressBar();
        } else {
            continueSubtitle.setText(relativeTime(entry.lastOpenedAt));
            resetProgressBar();
        }

        continueCard.setOnClickListener(v -> openReader(entry.uri));

        applyThumb(continueThumbnail, continueInitial, entry, false);
    }

    private void resetProgressBar() {
        ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
        lp.width = 0;
        progressFill.setLayoutParams(lp);
        progressLabel.setText("");
    }

    private void loadThumbAsync(LibraryManager.Entry entry) {
        String key = entry.uri.toString() + "#" + entry.coverUpdatedAt;

        Bitmap existing = thumbCache.get(key);
        if (existing != null && !existing.isRecycled()) return;

        Context appContext = requireContext().getApplicationContext();
        String coverPath = entry.coverPath;
        Uri    uri        = entry.uri;

        thumbExecutor.submit(() -> {
            Bitmap owned = null;
            try {
                if (coverPath != null) {
                    Bitmap decoded = android.graphics.BitmapFactory.decodeFile(coverPath);
                    if (decoded != null) owned = decoded;
                }
                if (owned == null) {
                    PdfCore core = new PdfCore();
                    try {
                        core.open(appContext, uri);
                        core.setScreenSize(240, 320);
                        if (core.pageCount() > 0) {
                            int total = core.pageCount();
                            SharedPreferences prefs = appContext.getSharedPreferences("pagevibe_prefs", Context.MODE_PRIVATE);
                            if (prefs.getInt("total_pages_" + uri.hashCode(), 0) == 0) {
                                prefs.edit().putInt("total_pages_" + uri.hashCode(), total).apply();
                            }
                            Bitmap rendered = core.renderPage(0, 240, 320);
                            owned = rendered.copy(Bitmap.Config.ARGB_8888, false);
                        }
                    } finally {
                        core.close();
                    }
                }
            } catch (Throwable ignored) {}

            if (owned == null) return;
            Bitmap finalOwned = owned;

            uiHandler.post(() -> {
                if (root == null || finalOwned.isRecycled()) return;
                Bitmap stale = thumbCache.put(key, finalOwned);
                if (stale != null && stale != finalOwned && !stale.isRecycled()) stale.recycle();
                onThumbLoaded(uri);
            });
        });
    }

    private void onThumbLoaded(Uri uri) {
        if (!recentEntries.isEmpty() && recentEntries.get(0).uri.equals(uri)) {
            bindContinueCard(recentEntries.get(0));
        }
        recentAdapter.notifyDataSetChanged();
    }

    private void applyThumb(ImageView img, TextView initial, LibraryManager.Entry entry, boolean fade) {
        String key   = entry.uri.toString() + "#" + entry.coverUpdatedAt;
        Bitmap thumb = thumbCache.get(key);

        img.setImageBitmap(null);

        if (thumb != null && !thumb.isRecycled()) {
            img.setImageBitmap(thumb);
            img.setVisibility(View.VISIBLE);
            initial.setVisibility(View.GONE);
            if (fade) {
                img.setAlpha(0f);
                img.animate().alpha(1f).setDuration(250).start();
            }
        } else {
            img.setVisibility(View.GONE);
            String name = LibraryManager.displayName(entry);
            initial.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
            initial.setVisibility(View.VISIBLE);
        }
    }

    private void animateEntrance() {
        int[] ids = {
                R.id.headerSection,
                R.id.insightsCard,
                R.id.continueSection,
                R.id.recentSection,
                R.id.emptyState
        };
        DecelerateInterpolator interp = new DecelerateInterpolator(2f);
        for (int i = 0; i < ids.length; i++) {
            View v = root.findViewById(ids[i]);
            if (v == null || v.getVisibility() != View.VISIBLE) continue;
            v.setAlpha(0f);
            v.setTranslationY(20f);
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(380)
                    .setStartDelay(i * 60L)
                    .setInterpolator(interp)
                    .start();
        }
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 12) return "Good morning";
        if (h < 17) return "Good afternoon";
        return "Good evening";
    }

    private String relativeTime(long timestamp) {
        long d = System.currentTimeMillis() - timestamp;
        if (d < 60_000L)         return "Just now";
        if (d < 3_600_000L)      return (d / 60_000L)     + "m ago";
        if (d < 86_400_000L)     return (d / 3_600_000L)  + "h ago";
        if (d < 7*86_400_000L)   return (d / 86_400_000L) + "d ago";
        return "Long ago";
    }

    private void clearThumbCache() {
        if (continueThumbnail != null) continueThumbnail.setImageBitmap(null);
        for (Bitmap b : thumbCache.values()) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        thumbCache.clear();
    }

    private class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_book_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            LibraryManager.Entry entry = recentEntries.get(pos);
            String name = LibraryManager.displayName(entry);
            h.title.setText(name);
            h.time.setText(relativeTime(entry.lastOpenedAt));
            h.remove.setVisibility(View.GONE); // management lives in Library now

            applyThumb(h.cover, h.initial, entry, true);

            h.itemView.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return;
                openReader(recentEntries.get(p).uri);
            });

            h.itemView.setAlpha(0f);
            h.itemView.animate()
                    .alpha(1f)
                    .setDuration(260)
                    .setStartDelay(pos * 35L)
                    .start();
        }

        @Override public int getItemCount() { return recentEntries.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView   cover;
            TextView    initial, title, time;
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
}