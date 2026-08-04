package neunix.pagevibe;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Discover tab. Owns ALL Firestore-facing state; the adapter and UI
 * below it are pure display. Search/sort/Featured-filter operate on an
 * in-memory copy of the last fetched list — none of them ever touch
 * Firestore.
 *
 * READ MINIMIZATION: relies entirely on DiscoverBookRepository's own
 * simplified strategy — one real server read per fresh app process,
 * reused for the rest of that session, plus pull-to-refresh always
 * forcing a real read. See DiscoverBookRepository for the full
 * explanation of why the earlier TTL-based approach was replaced.
 */
public class DiscoverFragment extends Fragment implements DiscoverBookAdapter.Listener {

    private enum SortMode { FEATURED, RATING, DOWNLOADS }

    private View root;
    private TextView countLabel;
    private EditText searchInput;
    private TextView featuredToggle;
    private ImageButton sortButton;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private View skeletonContainer;
    private View emptyState;
    private TextView noResultsText;
    private View errorState;
    private TextView errorTitle, errorMessage, retryButton;
    private GameView gameView;

    private DiscoverBookRepository repository;
    private DiscoverDownloadManagerHelper downloadHelper;
    private DiscoverBookAdapter adapter;
    private ThemeManager themeManager;

    private final List<DiscoverBook> allBooks = new ArrayList<>();
    private String searchQuery = "";
    private boolean featuredOnly = false;
    private SortMode sortMode = SortMode.FEATURED;

    private ValueAnimator skeletonPulse;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final List<String> activeDownloadBookIds = new ArrayList<>();
    private Runnable progressPollRunnable;

    private DiscoverDownloadReceiver downloadReceiver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_discover, container, false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository     = DiscoverBookRepository.getInstance(requireContext());
        downloadHelper = new DiscoverDownloadManagerHelper(requireContext());
        themeManager   = new ThemeManager(requireContext());

        bindViews();
        setupRecycler();
        setupSearch();
        setupSortAndFilter();
        setupSwipeRefresh();
        setupRetry();

        loadBooks(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        applyTheme();
        registerDownloadReceiver();
        refreshVisibleDownloadStates();
        startProgressPollingIfNeeded();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterDownloadReceiver();
        stopProgressPolling();
        if (gameView != null) gameView.stopLoop();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            applyTheme();
            refreshVisibleDownloadStates();
            startProgressPollingIfNeeded();
        } else {
            stopProgressPolling();
            if (gameView != null) gameView.stopLoop();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (skeletonPulse != null) skeletonPulse.cancel();
        if (gameView != null) gameView.stopLoop();
        root = null;
    }

    private void applyTheme() {
        if (root == null) return;
        ThemeManager.AppTheme theme = themeManager.getActiveTheme();
        ThemeApplier.apply(root, theme);
        styleFeaturedToggle();
        if (gameView != null) gameView.applyTheme(theme);
    }

    private void bindViews() {
        countLabel        = root.findViewById(R.id.discoverCountLabel);
        searchInput        = root.findViewById(R.id.discoverSearchInput);
        featuredToggle       = root.findViewById(R.id.discoverFeaturedToggle);
        sortButton              = root.findViewById(R.id.discoverSortButton);
        swipeRefresh               = root.findViewById(R.id.discoverSwipeRefresh);
        recycler                      = root.findViewById(R.id.discoverRecycler);
        skeletonContainer                = root.findViewById(R.id.discoverSkeletonContainer);
        emptyState                          = root.findViewById(R.id.discoverEmptyState);
        noResultsText                          = root.findViewById(R.id.discoverNoResultsText);
        errorState                                = root.findViewById(R.id.discoverErrorState);
        errorTitle                                   = root.findViewById(R.id.discoverErrorTitle);
        errorMessage                                    = root.findViewById(R.id.discoverErrorMessage);
        retryButton                                        = root.findViewById(R.id.discoverRetryButton);
        gameView                                              = root.findViewById(R.id.discoverGameView);
    }

    private void setupRecycler() {
        adapter = new DiscoverBookAdapter(requireContext(), downloadHelper, this);
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recycler.setAdapter(adapter);
        recycler.setHasFixedSize(true);
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                searchQuery = s.toString();
                rebuildAndDisplay();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSortAndFilter() {
        sortButton.setOnClickListener(this::showSortPopup);
        featuredToggle.setOnClickListener(v -> {
            featuredOnly = !featuredOnly;
            styleFeaturedToggle();
            rebuildAndDisplay();
        });
    }

    private void styleFeaturedToggle() {
        if (featuredToggle == null || themeManager == null) return;
        ThemeManager.AppTheme theme = themeManager.getActiveTheme();
        if (featuredOnly) {
            ThemeApplier.setBackgroundColorPreservingShape(featuredToggle, theme.accentColor);
            featuredToggle.setTextColor(theme.buttonTextColor);
        } else {
            ThemeApplier.setBackgroundColorPreservingShape(featuredToggle, theme.cardColor);
            featuredToggle.setTextColor(theme.textPrimaryColor);
        }
    }

    private void showSortPopup(View anchor) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.popup_discover_sort_menu, null);
        ThemeApplier.apply(content, themeManager.getActiveTheme());

        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(16f);

        content.findViewById(R.id.discoverSortFeatured).setOnClickListener(v -> {
            sortMode = SortMode.FEATURED; rebuildAndDisplay(); popup.dismiss();
        });
        content.findViewById(R.id.discoverSortRating).setOnClickListener(v -> {
            sortMode = SortMode.RATING; rebuildAndDisplay(); popup.dismiss();
        });
        content.findViewById(R.id.discoverSortDownloads).setOnClickListener(v -> {
            sortMode = SortMode.DOWNLOADS; rebuildAndDisplay(); popup.dismiss();
        });

        popup.showAsDropDown(anchor, 0, 8, Gravity.END);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> loadBooks(true));
    }

    private void setupRetry() {
        retryButton.setOnClickListener(v -> loadBooks(false));
    }

    // =========================================================
    // FIRESTORE LOAD
    // =========================================================

    private void loadBooks(boolean forceRefresh) {
        if (allBooks.isEmpty()) showSkeleton();
        hideError();

        repository.fetchBooks(forceRefresh, new DiscoverBookRepository.FetchCallback() {
            @Override
            public void onSuccess(List<DiscoverBook> books, boolean servedFromLocalCache) {
                if (root == null) return;
                requireActivity().runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    allBooks.clear();
                    allBooks.addAll(books);
                    rebuildAndDisplay();
                });
            }

            @Override
            public void onError(String message) {
                if (root == null) return;
                requireActivity().runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    if (allBooks.isEmpty()) {
                        showError(message);
                    } else {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // =========================================================
    // IN-MEMORY SEARCH / SORT / FILTER — zero Firestore cost
    // =========================================================

    private void rebuildAndDisplay() {
        if (allBooks.isEmpty()) {
            showEmpty();
            return;
        }

        List<DiscoverBook> filtered = new ArrayList<>();
        String q = searchQuery.trim().toLowerCase(Locale.getDefault());

        for (DiscoverBook book : allBooks) {
            if (featuredOnly && !book.isFeatured()) continue;
            if (!q.isEmpty() && !matchesQuery(book, q)) continue;
            filtered.add(book);
        }

        sortList(filtered);

        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " book" : " books"));

        if (filtered.isEmpty()) {
            showNoResults();
        } else {
            showContent();
            adapter.updateBooks(filtered);
        }
    }

    private boolean matchesQuery(DiscoverBook book, String lowerQuery) {
        if (book.getTitle().toLowerCase(Locale.getDefault()).contains(lowerQuery)) return true;
        if (book.getAuthor().toLowerCase(Locale.getDefault()).contains(lowerQuery)) return true;
        if (book.getCategory().toLowerCase(Locale.getDefault()).contains(lowerQuery)) return true;
        for (String keyword : book.getSearchKeywords()) {
            if (keyword.toLowerCase(Locale.getDefault()).contains(lowerQuery)) return true;
        }
        return false;
    }

    private void sortList(List<DiscoverBook> list) {
        switch (sortMode) {
            case RATING:
                Collections.sort(list, (a, b) -> Double.compare(b.getRating(), a.getRating()));
                break;
            case DOWNLOADS:
                Collections.sort(list, (a, b) -> Long.compare(b.getDownloads(), a.getDownloads()));
                break;
            case FEATURED:
            default:
                Collections.sort(list, (a, b) -> {
                    if (a.isFeatured() != b.isFeatured()) return a.isFeatured() ? -1 : 1;
                    return Double.compare(b.getRating(), a.getRating());
                });
                break;
        }
    }

    // =========================================================
    // DOWNLOAD / OPEN / DETAIL — DiscoverBookAdapter.Listener
    // =========================================================

    @Override
    public void onDownloadClicked(DiscoverBook book) {
        if (!book.hasValidPdfUrl()) {
            Toast.makeText(requireContext(), "This book doesn't have a downloadable file", Toast.LENGTH_SHORT).show();
            adapter.notifyProgressChanged(book.getBookId());
            return;
        }
        if (!NetworkUtils.isOnline(requireContext())) {
            Toast.makeText(requireContext(), "You're offline — connect to the internet to download", Toast.LENGTH_LONG).show();
            adapter.notifyProgressChanged(book.getBookId());
            return;
        }

        long id = downloadHelper.startDownload(book);
        if (id == -1L) {
            Toast.makeText(requireContext(), "Couldn't start the download. Please try again.", Toast.LENGTH_SHORT).show();
        }
        adapter.notifyProgressChanged(book.getBookId());
        trackActiveDownload(book.getBookId());
        startProgressPollingIfNeeded();
    }

    @Override
    public void onOpenClicked(DiscoverBook book) {
        Uri fileUri = downloadHelper.getDownloadedUri(book.getBookId());
        if (fileUri == null) {
            Toast.makeText(requireContext(), "File not found — try downloading again", Toast.LENGTH_SHORT).show();
            adapter.notifyProgressChanged(book.getBookId());
            return;
        }
        Intent intent = new Intent(requireContext(), PdfActivity.class);
        intent.setData(fileUri);
        startActivity(intent);
    }

    @Override
    public void onBookClicked(DiscoverBook book) {
        startActivity(DiscoverBookDetailActivity.createIntent(requireContext(), book));
    }

    // =========================================================
    // DOWNLOAD COMPLETION
    // =========================================================

    private void registerDownloadReceiver() {
        if (downloadReceiver != null) return;
        downloadReceiver = new DiscoverDownloadReceiver(this::onSystemDownloadComplete);
        requireContext().registerReceiver(
                downloadReceiver, new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void unregisterDownloadReceiver() {
        if (downloadReceiver == null) return;
        try { requireContext().unregisterReceiver(downloadReceiver); } catch (Throwable ignored) {}
        downloadReceiver = null;
    }

    private void onSystemDownloadComplete(long downloadId) {
        for (DiscoverBook book : allBooks) {
            if (downloadHelper.matchesStoredId(book.getBookId(), downloadId)) {
                activeDownloadBookIds.remove(book.getBookId());
                Uri fileUri = downloadHelper.getDownloadedUri(book.getBookId());
                DiscoverLibrarySync.syncIfNeeded(requireContext(), book, fileUri);
                if (root != null) {
                    requireActivity().runOnUiThread(() -> adapter.notifyProgressChanged(book.getBookId()));
                }
                break;
            }
        }
    }

    private void refreshVisibleDownloadStates() {
        activeDownloadBookIds.clear();
        for (DiscoverBook book : allBooks) {
            DiscoverDownloadManagerHelper.DownloadState state = downloadHelper.getState(book.getBookId());
            if (state == DiscoverDownloadManagerHelper.DownloadState.DOWNLOADING
                    || state == DiscoverDownloadManagerHelper.DownloadState.PENDING
                    || state == DiscoverDownloadManagerHelper.DownloadState.PAUSED) {
                activeDownloadBookIds.add(book.getBookId());
            }
        }
        if (adapter != null && !allBooks.isEmpty()) {
            adapter.updateBooks(currentDisplayedListSnapshot());
        }
    }

    private List<DiscoverBook> currentDisplayedListSnapshot() {
        List<DiscoverBook> filtered = new ArrayList<>();
        String q = searchQuery.trim().toLowerCase(Locale.getDefault());
        for (DiscoverBook book : allBooks) {
            if (featuredOnly && !book.isFeatured()) continue;
            if (!q.isEmpty() && !matchesQuery(book, q)) continue;
            filtered.add(book);
        }
        sortList(filtered);
        return filtered;
    }

    private void trackActiveDownload(String bookId) {
        if (!activeDownloadBookIds.contains(bookId)) activeDownloadBookIds.add(bookId);
    }

    // =========================================================
    // PROGRESS POLLING
    // =========================================================

    private void startProgressPollingIfNeeded() {
        if (progressPollRunnable != null) return;
        progressPollRunnable = new Runnable() {
            @Override
            public void run() {
                if (root == null) return;
                if (activeDownloadBookIds.isEmpty()) {
                    progressPollRunnable = null;
                    return;
                }
                List<String> stillActive = new ArrayList<>();
                for (String bookId : new ArrayList<>(activeDownloadBookIds)) {
                    DiscoverDownloadManagerHelper.DownloadState state = downloadHelper.getState(bookId);
                    adapter.notifyProgressChanged(bookId);
                    if (state == DiscoverDownloadManagerHelper.DownloadState.DOWNLOADING
                            || state == DiscoverDownloadManagerHelper.DownloadState.PENDING
                            || state == DiscoverDownloadManagerHelper.DownloadState.PAUSED) {
                        stillActive.add(bookId);
                    }
                }
                activeDownloadBookIds.clear();
                activeDownloadBookIds.addAll(stillActive);

                if (!activeDownloadBookIds.isEmpty()) {
                    progressHandler.postDelayed(this, 800L);
                } else {
                    progressPollRunnable = null;
                }
            }
        };
        progressHandler.post(progressPollRunnable);
    }

    private void stopProgressPolling() {
        if (progressPollRunnable != null) {
            progressHandler.removeCallbacks(progressPollRunnable);
            progressPollRunnable = null;
        }
    }

    // =========================================================
    // STATE VISIBILITY
    // =========================================================

    private void showSkeleton() {
        recycler.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        noResultsText.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        if (gameView != null) { gameView.setVisibility(View.GONE); gameView.stopLoop(); }
        skeletonContainer.setVisibility(View.VISIBLE);
        startSkeletonPulse();
    }

    private void showContent() {
        stopSkeletonPulse();
        skeletonContainer.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        noResultsText.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        if (gameView != null) { gameView.setVisibility(View.GONE); gameView.stopLoop(); }
        recycler.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        stopSkeletonPulse();
        skeletonContainer.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        noResultsText.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        if (gameView != null) { gameView.setVisibility(View.GONE); gameView.stopLoop(); }
        emptyState.setVisibility(View.VISIBLE);
        countLabel.setText("0 books");
    }

    private void showNoResults() {
        stopSkeletonPulse();
        skeletonContainer.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        if (gameView != null) { gameView.setVisibility(View.GONE); gameView.stopLoop(); }
        recycler.setVisibility(View.GONE);
        noResultsText.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        stopSkeletonPulse();
        skeletonContainer.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        noResultsText.setVisibility(View.GONE);
        errorMessage.setText(message != null ? message : "Something went wrong.");
        errorState.setVisibility(View.VISIBLE);

        boolean offline = !NetworkUtils.isOnline(requireContext());
        if (gameView != null) {
            gameView.setVisibility(offline ? View.VISIBLE : View.GONE);
            if (offline) {
                gameView.applyTheme(themeManager.getActiveTheme());
                gameView.startLoop();
            } else {
                gameView.stopLoop();
            }
        }
    }

    private void hideError() {
        errorState.setVisibility(View.GONE);
    }

    private void startSkeletonPulse() {
        if (skeletonPulse != null && skeletonPulse.isRunning()) return;
        skeletonPulse = ValueAnimator.ofFloat(0.4f, 1f);
        skeletonPulse.setDuration(800);
        skeletonPulse.setRepeatMode(ValueAnimator.REVERSE);
        skeletonPulse.setRepeatCount(ValueAnimator.INFINITE);
        skeletonPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        skeletonPulse.addUpdateListener(a -> {
            if (skeletonContainer != null) skeletonContainer.setAlpha((float) a.getAnimatedValue());
        });
        skeletonPulse.start();
    }

    private void stopSkeletonPulse() {
        if (skeletonPulse != null) skeletonPulse.cancel();
    }
}