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
 * Home tab — premium dashboard redesign. Replaces the old full-width
 * "Open PDF" bottom bar (which repeatedly read as a generic file-utility
 * screen in store review) with a floating action button, three compact
 * tappable stat chips instead of one plain insights card, and a
 * full-bleed "hero" Continue Reading card with real cover art and a
 * scrim overlay instead of a small thumbnail + plain text row.
 */
public class HomeFragment extends Fragment {

    private static final int RECENT_LIMIT = 5;

    private View          root;
    private TextView      greetingText;
    private ImageButton   btnSettings;

    private View          statChipStreak, statChipLibrary, statChipToday;
    private TextView      statStreakValue, statLibraryValue, statTodayValue;

    private View          continueSection;
    private View          continueHeroCard;
    private ImageView     continueHeroCover;
    private TextView      continueHeroInitial;
    private TextView      continueHeroTitle;
    private TextView      continueHeroSubtitle;
    private TextView      continueHeroPercentBadge;
    private View          continueHeroProgressTrack;
    private View          continueHeroProgressFill;

    private View          recentSection;
    private TextView      btnSeeAllRecent;
    private RecyclerView  recentStrip;
    private View          emptyState;
    private ImageButton   btnOpenPdfFab;

    private LibraryManager         libraryManager;
    private ReadingStatsController stats;
    private ThemeManager           themeManager;

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
        themeManager = new ThemeManager(requireContext());
        bindViews();
        setupRecycler();
        setupButtons();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUI(false);
        bindInsights();
        applyTheme();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            refreshUI(false);
            bindInsights();
            applyTheme();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        thumbExecutor.shutdown();
        clearThumbCache();
        root = null;
    }

    private void applyTheme() {
        if (root == null || themeManager == null) return;
        ThemeManager.AppTheme theme = themeManager.getActiveTheme();
        ThemeApplier.apply(root, theme);
        if (recentAdapter != null) recentAdapter.notifyDataSetChanged();
    }

    private void bindViews() {
        greetingText   = root.findViewById(R.id.greetingText);
        btnSettings    = root.findViewById(R.id.btnSettings);

        statChipStreak  = root.findViewById(R.id.statChipStreak);
        statChipLibrary = root.findViewById(R.id.statChipLibrary);
        statChipToday   = root.findViewById(R.id.statChipToday);
        statStreakValue  = root.findViewById(R.id.statStreakValue);
        statLibraryValue = root.findViewById(R.id.statLibraryValue);
        statTodayValue   = root.findViewById(R.id.statTodayValue);

        continueSection           = root.findViewById(R.id.continueSection);
        continueHeroCard          = root.findViewById(R.id.continueHeroCard);
        continueHeroCover         = root.findViewById(R.id.continueHeroCover);
        continueHeroInitial       = root.findViewById(R.id.continueHeroInitial);
        continueHeroTitle         = root.findViewById(R.id.continueHeroTitle);
        continueHeroSubtitle      = root.findViewById(R.id.continueHeroSubtitle);
        continueHeroPercentBadge  = root.findViewById(R.id.continueHeroPercentBadge);
        continueHeroProgressTrack = root.findViewById(R.id.continueHeroProgressTrack);
        continueHeroProgressFill  = root.findViewById(R.id.continueHeroProgressFill);

        recentSection   = root.findViewById(R.id.recentSection);
        btnSeeAllRecent = root.findViewById(R.id.btnSeeAllRecent);
        recentStrip     = root.findViewById(R.id.recentStrip);
        emptyState      = root.findViewById(R.id.emptyState);
        btnOpenPdfFab   = root.findViewById(R.id.btnOpenPdfFab);

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
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), SettingsActivity.class)));
            TooltipUtil.apply(btnSettings, "Settings");
        }

        View.OnClickListener openStats = v -> {
            bouncePress(v);
            startActivity(new Intent(requireContext(), StatsActivity.class));
        };
        statChipStreak.setOnClickListener(openStats);
        statChipLibrary.setOnClickListener(openStats);
        statChipToday.setOnClickListener(openStats);

        btnOpenPdfFab.setOnClickListener(v ->
                v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90)
                        .withEndAction(() ->
                                v.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(120)
                                        .setInterpolator(new OvershootInterpolator(3f))
                                        .withEndAction(this::openFilePicker)
                                        .start())
                        .start());
        TooltipUtil.apply(btnOpenPdfFab, "Open PDF");

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
        if (stats == null || libraryManager == null || statStreakValue == null) return;

        int streak = stats.getCurrentStreakDays();
        statStreakValue.setText(streak > 0 ? "🔥 " + streak : "0");

        statLibraryValue.setText(String.valueOf(libraryManager.size()));

        List<ReadingStatsController.DayEntry> days = stats.getRecentDayEntries();
        long todaySeconds = days.isEmpty() ? 0L : days.get(0).seconds;
        statTodayValue.setText(todaySeconds > 0 ? ReadingStatsController.formatDuration(todaySeconds) : "0m");
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
            bindContinueHero(recentEntries.get(0));
            recentAdapter.notifyDataSetChanged();
            for (LibraryManager.Entry e : recentEntries) loadThumbAsync(e);
        }

        if (animate) animateEntrance();
    }

    private void bindContinueHero(LibraryManager.Entry entry) {
        String name = LibraryManager.displayName(entry);
        continueHeroTitle.setText(name);
        continueHeroInitial.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());

        SharedPreferences prefs =
                requireContext().getSharedPreferences("pagevibe_prefs", Context.MODE_PRIVATE);
        int lastPage   = prefs.getInt("last_page_"   + entry.uri.hashCode(), 0);
        int totalPages = prefs.getInt("total_pages_" + entry.uri.hashCode(), 0);

        if (totalPages > 1) {
            continueHeroSubtitle.setText(
                    "Page " + (lastPage + 1) + " of " + totalPages
                    + "  ·  " + relativeTime(entry.lastOpenedAt));

            float pct = (float) lastPage / (totalPages - 1);
            int pctInt = Math.round(pct * 100);
            continueHeroPercentBadge.setVisibility(View.VISIBLE);
            continueHeroPercentBadge.setText(pctInt + "%");

            continueHeroProgressTrack.post(() -> {
                if (root == null) return;
                int maxW = continueHeroProgressTrack.getWidth();
                ViewGroup.LayoutParams lp = continueHeroProgressFill.getLayoutParams();
                lp.width = (int) (maxW * pct);
                continueHeroProgressFill.setLayoutParams(lp);
            });

        } else if (totalPages == 1) {
            continueHeroSubtitle.setText("1 page  ·  " + relativeTime(entry.lastOpenedAt));
            resetHeroProgress();
        } else {
            continueHeroSubtitle.setText(relativeTime(entry.lastOpenedAt));
            resetHeroProgress();
        }

        continueHeroCard.setOnClickListener(v -> openReader(entry.uri));

        applyHeroCover(entry);
    }

    private void resetHeroProgress() {
        continueHeroPercentBadge.setVisibility(View.GONE);
        ViewGroup.LayoutParams lp = continueHeroProgressFill.getLayoutParams();
        lp.width = 0;
        continueHeroProgressFill.setLayoutParams(lp);
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
                        core.setScreenSize(480, 640);
                        if (core.pageCount() > 0) {
                            int total = core.pageCount();
                            SharedPreferences prefs = appContext.getSharedPreferences("pagevibe_prefs", Context.MODE_PRIVATE);
                            if (prefs.getInt("total_pages_" + uri.hashCode(), 0) == 0) {
                                prefs.edit().putInt("total_pages_" + uri.hashCode(), total).apply();
                            }
                            Bitmap rendered = core.renderPage(0, 480, 640);
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
            applyHeroCover(recentEntries.get(0));
        }
        recentAdapter.notifyDataSetChanged();
    }

    private void applyHeroCover(LibraryManager.Entry entry) {
        String key   = entry.uri.toString() + "#" + entry.coverUpdatedAt;
        Bitmap thumb = thumbCache.get(key);

        continueHeroCover.setImageBitmap(null);

        if (thumb != null && !thumb.isRecycled()) {
            continueHeroCover.setImageBitmap(thumb);
            continueHeroCover.setVisibility(View.VISIBLE);
            continueHeroInitial.setVisibility(View.GONE);
            continueHeroCover.setAlpha(0f);
            continueHeroCover.animate().alpha(1f).setDuration(280).start();
        } else {
            continueHeroCover.setVisibility(View.GONE);
            String name = LibraryManager.displayName(entry);
            continueHeroInitial.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
            continueHeroInitial.setVisibility(View.VISIBLE);
        }
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
        if (continueHeroCover != null) continueHeroCover.setImageBitmap(null);
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
            h.remove.setVisibility(View.GONE);

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

            if (themeManager != null) {
                ThemeApplier.applyToSingleView(h.itemView, themeManager.getActiveTheme());
            }
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