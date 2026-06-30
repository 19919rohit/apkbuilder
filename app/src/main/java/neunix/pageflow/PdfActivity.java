package neunix.pageflow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.Slider;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class PdfActivity extends AppCompatActivity {

    // =========================================================
    // CONSTANTS
    // =========================================================
    private static final String PREFS_NAME    = "pageflow_prefs";
    private static final String KEY_LAST_PAGE = "last_page_";
    private static final String KEY_BOOKMARKS = "bookmarks_";
    private static final long   HIDE_DELAY_MS = 3_000L;

    // =========================================================
    // VIEWS
    // =========================================================
    private ZoomableFrameLayout zoomContainer;
    private CurlView            curlView;
    private DrawingView         drawingView;
    private Slider              slider;
    private TextView            pageText;
    private TextView            titleText;
    private TextView            errorMessage;
    private TextView            searchResultText;
    private TextView            ttsStatusText;
    private TextView            bookmarkToast;
    private EditText            searchInput;
    private View                topBar;
    private View                controlBar;
    private View                loadingOverlay;
    private View                errorView;
    private View                searchPanel;
    private View                drawToolbar;
    private View                readAloudBar;
    private View                tocSheetBackdrop;
    private View                tocSheet;
    private ImageButton         btnBack;
    private ImageButton         btnOpenNew;
    private ImageButton         btnSearch;
    private ImageButton         btnBookmark;
    private ImageButton         btnToc;
    private ImageButton         btnDraw;
    private ImageButton         btnReadAloud;
    private ImageButton         btnSearchPrev;
    private ImageButton         btnSearchNext;
    private ImageButton         btnSearchClose;
    private ImageButton         btnDrawUndo;
    private ImageButton         btnDrawClear;
    private ImageButton         btnTtsPlayPause;
    private ImageButton         btnTtsStop;
    private ImageButton         btnTocClose;
    private RecyclerView        tocRecycler;

    // =========================================================
    // PDF STATE
    // =========================================================
    private PdfCore          core;
    private PdfTextExtractor extractor;
    private Uri              currentUri;
    private String           currentFileName = "";
    private int              currentPage     = 0;
    private int              totalPages      = 0;
    private int              bitmapWidth     = 0;
    private int              bitmapHeight    = 0;

    // =========================================================
    // THREADING
    // =========================================================
    private final Handler         uiHandler      = new Handler(Looper.getMainLooper());
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "PdfRender"));
    private final ExecutorService bgExecutor     = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "PdfBg"));
    private final AtomicBoolean   coreReady      = new AtomicBoolean(false);

    // =========================================================
    // SLIDER STATE
    // =========================================================
    private boolean       internalSliderUpdate = false;
    private Runnable      sliderDebounce;
    private List<Future<?>> prefetchFutures;

    // =========================================================
    // CONTROLS AUTO-HIDE
    // =========================================================
    private boolean        controlsVisible = true;
    private final Runnable hideControls    = () -> setControlsVisible(false);

    // =========================================================
    // BOOKMARKS
    // =========================================================
    private final Set<Integer> bookmarkedPages = new HashSet<>();

    // =========================================================
    // DRAWING — per-page stroke storage
    // =========================================================
    private final Map<Integer, List<DrawingView.Stroke>> pageStrokes = new HashMap<>();
    private boolean drawModeActive = false;

    // =========================================================
    // SEARCH
    // =========================================================
    private final List<PdfTextExtractor.SearchResult> searchResults = new ArrayList<>();
    private int     searchResultIndex  = -1;
    private boolean searchPanelVisible = false;

    // =========================================================
    // TABLE OF CONTENTS
    // =========================================================
    private final List<PdfTextExtractor.TocEntry> tocEntries = new ArrayList<>();
    private TocAdapter tocAdapter;

    // =========================================================
    // TEXT TO SPEECH
    // =========================================================
    private TextToSpeech tts;
    private boolean      ttsReady   = false;
    private boolean      ttsPlaying = false;

    // =========================================================
    // FILE PICKER
    // =========================================================
    private ActivityResultLauncher<Intent> pdfPickerLauncher;

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        setContentView(R.layout.activity_pdf);

        bindViews();
        registerFilePicker();
        setupCurlView();
        setupSlider();
        setupButtons();
        setupSearchPanel();
        setupDrawToolbar();
        setupToc();
        setupTts();

        Uri incoming = getIntent().getData();
        if (incoming != null) openPdf(incoming);
        else openFilePicker();
    }

    @Override
    protected void onResume() {
        super.onResume();
        curlView.onResume();
        scheduleHideControls();
    }

    @Override
    protected void onPause() {
        super.onPause();
        curlView.onPause();
        uiHandler.removeCallbacks(hideControls);
        saveLastPage();
        stopTts();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        closePdfCore();
        renderExecutor.shutdownNow();
        bgExecutor.shutdownNow();
        if (extractor != null) { extractor.close(); extractor = null; }
        if (tts != null)       { tts.stop(); tts.shutdown(); tts = null; }
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!controlsVisible) setControlsVisible(true);
        scheduleHideControls();
        return super.dispatchTouchEvent(ev);
    }

    // =========================================================
    // BIND VIEWS
    // =========================================================

    private void bindViews() {
        zoomContainer    = findViewById(R.id.zoomContainer);
        curlView         = findViewById(R.id.curlView);
        drawingView      = findViewById(R.id.drawingView);
        slider           = findViewById(R.id.pageSlider);
        pageText         = findViewById(R.id.pageText);
        titleText        = findViewById(R.id.titleText);
        topBar           = findViewById(R.id.topBar);
        controlBar       = findViewById(R.id.controlBar);
        loadingOverlay   = findViewById(R.id.loadingOverlay);
        errorView        = findViewById(R.id.errorView);
        errorMessage     = findViewById(R.id.errorMessage);
        btnBack          = findViewById(R.id.btnBack);
        btnOpenNew       = findViewById(R.id.btnOpenNew);
        btnSearch        = findViewById(R.id.btnSearch);
        btnBookmark      = findViewById(R.id.btnBookmark);
        btnToc           = findViewById(R.id.btnToc);
        btnDraw          = findViewById(R.id.btnDraw);
        btnReadAloud     = findViewById(R.id.btnReadAloud);
        searchPanel      = findViewById(R.id.searchPanel);
        searchInput      = findViewById(R.id.searchInput);
        searchResultText = findViewById(R.id.searchResultText);
        btnSearchPrev    = findViewById(R.id.btnSearchPrev);
        btnSearchNext    = findViewById(R.id.btnSearchNext);
        btnSearchClose   = findViewById(R.id.btnSearchClose);
        drawToolbar      = findViewById(R.id.drawToolbar);
        btnDrawUndo      = findViewById(R.id.btnDrawUndo);
        btnDrawClear     = findViewById(R.id.btnDrawClear);
        readAloudBar     = findViewById(R.id.readAloudBar);
        btnTtsPlayPause  = findViewById(R.id.btnTtsPlayPause);
        btnTtsStop       = findViewById(R.id.btnTtsStop);
        ttsStatusText    = findViewById(R.id.ttsStatusText);
        tocSheetBackdrop = findViewById(R.id.tocSheetBackdrop);
        tocSheet         = findViewById(R.id.tocSheet);
        btnTocClose      = findViewById(R.id.btnTocClose);
        tocRecycler      = findViewById(R.id.tocRecycler);
        bookmarkToast    = findViewById(R.id.bookmarkToast);

        findViewById(R.id.btnRetryOpen).setOnClickListener(v -> openFilePicker());
    }

    // =========================================================
    // FILE PICKER
    // =========================================================

    private void registerFilePicker() {
        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) return;
                    Intent data = result.getData();
                    if (data == null || data.getData() == null) return;
                    openPdf(data.getData());
                });
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/pdf");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pdfPickerLauncher.launch(i);
    }

    // =========================================================
    // OPEN PDF
    // =========================================================

    private void openPdf(Uri uri) {
        showLoading(true);
        hideError();
        closePdfCore();
        coreReady.set(false);
        bitmapWidth  = 0;
        bitmapHeight = 0;
        currentUri   = uri;

        pageStrokes.clear();
        bookmarkedPages.clear();
        searchResults.clear();
        searchResultIndex = -1;
        tocEntries.clear();

        renderExecutor.execute(() -> {
            try {
                // Open rendering core
                PdfCore newCore = new PdfCore();
                newCore.open(this, uri);
                int pages = newCore.pageCount();
                if (pages <= 0) {
                    newCore.close();
                    uiHandler.post(() -> {
                        showLoading(false);
                        showError("This PDF appears to be empty.");
                    });
                    return;
                }

                // Resolve actual file for pdfbox
                File cacheFile = FileUtils.getFileFromUri(this, uri);
                String fileName = FileUtils.getFileName(this, uri);

                // Open text extractor (pdfbox — may take a moment on first call)
                PdfTextExtractor newExtractor = new PdfTextExtractor();
                newExtractor.init(this);
                newExtractor.open(cacheFile);

                // Extract PDF embedded outline (real TOC)
                List<PdfTextExtractor.TocEntry> outline = newExtractor.extractOutline();

                // Try to get PDF title from document metadata
                String pdfTitle = newExtractor.getTitle();

                uiHandler.post(() -> {
                    if (isDestroyed()) {
                        newCore.close();
                        newExtractor.close();
                        return;
                    }

                    core            = newCore;
                    extractor       = newExtractor;
                    currentFileName = fileName;
                    totalPages      = pages;
                    currentPage     = restoreLastPage(uri);
                    coreReady.set(true);

                    // Prefer PDF metadata title, fall back to filename
                    String displayTitle = (pdfTitle != null && !pdfTitle.trim().isEmpty())
                            ? pdfTitle.trim()
                            : cleanFileName(currentFileName);
                    titleText.setText(displayTitle);

                    loadBookmarks();
                    setupSliderRange();

                    // Populate TOC: real outline if available, else milestones
                    tocEntries.clear();
                    if (!outline.isEmpty()) {
                        tocEntries.addAll(outline);
                    } else {
                        buildFallbackToc();
                    }
                    if (tocAdapter != null) tocAdapter.notifyDataSetChanged();

                    curlView.setPageProvider(pageProvider);
                    curlView.setCurrentIndex(currentPage);
                    curlView.setViewMode(CurlView.SHOW_ONE_PAGE);

                    showLoading(false);
                    updatePageText(currentPage);
                    updateBookmarkIcon();
                });

            } catch (Exception e) {
                uiHandler.post(() -> {
                    if (isDestroyed()) return;
                    showLoading(false);
                    showError("Could not open PDF: " + safeMessage(e));
                });
            }
        });
    }

    // =========================================================
    // PAGE PROVIDER
    // =========================================================

    private final CurlView.PageProvider pageProvider = new CurlView.PageProvider() {

        @Override
        public int getPageCount() {
            return totalPages;
        }

        @Override
        public void updatePage(CurlPage page, int width, int height, int index) {
            // Capture bitmap dimensions on first call
            if (bitmapWidth == 0 || bitmapHeight == 0) {
                bitmapWidth  = Math.max(width, 1);
                bitmapHeight = Math.max(height, 1);
                PdfCore snap = core;
                if (snap != null) snap.setScreenSize(bitmapWidth, bitmapHeight);
            }

            final int w = Math.max(width, 1);
            final int h = Math.max(height, 1);

            if (!coreReady.get() || core == null || index < 0 || index >= totalPages) {
                page.setTexture(blankBitmap(w, h), CurlPage.SIDE_BOTH);
                return;
            }

            try {
                // Front face — current page
                Bitmap front = core.renderPage(index, w, h);
                page.setTexture(safeCopy(front, w, h), CurlPage.SIDE_FRONT);

                // Back face — next page, or cream cover if last
                if (index + 1 < totalPages) {
                    Bitmap back = core.renderPage(index + 1, w, h);
                    page.setTexture(safeCopy(back, w, h), CurlPage.SIDE_BACK);
                } else {
                    Bitmap cover = blankBitmap(w, h);
                    cover.eraseColor(0xFFF0EDE8);
                    page.setTexture(cover, CurlPage.SIDE_BACK);
                    page.setColor(0xFFE8E4DF, CurlPage.SIDE_BACK);
                }

                final int capturedIndex = index;
                uiHandler.post(() -> {
                    if (isDestroyed()) return;
                    if (capturedIndex != currentPage) {
                        // Save strokes for departing page, restore for arriving page
                        pageStrokes.put(currentPage, drawingView.detachStrokes());
                        currentPage = capturedIndex;
                        drawingView.attachStrokes(pageStrokes.get(capturedIndex));

                        updatePageText(capturedIndex);
                        syncSlider(capturedIndex);
                        updateBookmarkIcon();

                        // Background prefetch neighbours
                        PdfCore snap = core;
                        if (snap != null && bitmapWidth > 0) {
                            if (prefetchFutures != null) snap.cancelPrefetch(prefetchFutures);
                            prefetchFutures = snap.prefetchAround(capturedIndex, 2);
                        }
                    }
                });

            } catch (Exception e) {
                page.setTexture(blankBitmap(w, h), CurlPage.SIDE_BOTH);
            }
        }
    };

    // =========================================================
    // CURL VIEW SETUP
    // =========================================================

    private void setupCurlView() {
        curlView.setBackgroundColor(0xFF0A0A0A);
        curlView.setAllowLastPageCurl(true);
        curlView.setRenderLeftPage(false);
        curlView.setMargins(0f, 0f, 0f, 0f);
        curlView.setSizeChangedObserver((w, h) -> {
            bitmapWidth  = w;
            bitmapHeight = h;
            PdfCore snap = core;
            if (snap != null) snap.setScreenSize(w, h);
        });
    }

    // =========================================================
    // SLIDER
    // =========================================================

    private void setupSlider() {
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser || internalSliderUpdate) return;
            int target = (int) value;
            updatePageText(target);

            if (sliderDebounce != null) uiHandler.removeCallbacks(sliderDebounce);
            sliderDebounce = () -> {
                if (!coreReady.get() || target == currentPage) return;
                PdfCore snap = core;
                if (snap != null) snap.evictExcept(target - 3, target + 3);
                navigateToPage(target);
            };
            uiHandler.postDelayed(sliderDebounce, 150);
        });
    }

    private void setupSliderRange() {
        internalSliderUpdate = true;
        slider.setValueFrom(0f);
        float valueTo = totalPages > 1 ? totalPages - 1 : 1f;
        slider.setValueTo(valueTo);
        slider.setStepSize(1f);
        slider.setValue(Math.max(0f, Math.min(currentPage, valueTo)));
        internalSliderUpdate = false;
        updatePageText(currentPage);
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private void setupButtons() {
        btnBack.setOnClickListener(v -> finish());
        btnOpenNew.setOnClickListener(v -> openFilePicker());
        btnBookmark.setOnClickListener(v -> toggleBookmark());
        btnToc.setOnClickListener(v -> showToc());
        btnDraw.setOnClickListener(v -> toggleDrawMode());
        btnReadAloud.setOnClickListener(v -> toggleReadAloud());
    }

    // =========================================================
    // SEARCH
    // =========================================================

    private void setupSearchPanel() {
        btnSearch.setOnClickListener(v -> toggleSearchPanel());
        btnSearchClose.setOnClickListener(v -> hideSearchPanel());

        btnSearchNext.setOnClickListener(v -> {
            if (searchResults.isEmpty()) return;
            searchResultIndex = (searchResultIndex + 1) % searchResults.size();
            jumpToSearchResult();
        });

        btnSearchPrev.setOnClickListener(v -> {
            if (searchResults.isEmpty()) return;
            searchResultIndex = (searchResultIndex - 1 + searchResults.size()) % searchResults.size();
            jumpToSearchResult();
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(searchInput.getText().toString().trim());
                hideKeyboard();
                return true;
            }
            return false;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    searchResults.clear();
                    searchResultIndex = -1;
                    searchResultText.setText("");
                }
            }
        });
    }

    private void toggleSearchPanel() {
        if (searchPanelVisible) hideSearchPanel();
        else showSearchPanel();
    }

    private void showSearchPanel() {
        searchPanelVisible = true;
        searchPanel.setVisibility(View.VISIBLE);
        searchPanel.animate()
                .translationY(0)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        searchInput.requestFocus();
        showKeyboard(searchInput);
    }

    private void hideSearchPanel() {
        searchPanelVisible = false;
        hideKeyboard();
        searchPanel.animate()
                .translationY(-400)
                .setDuration(220)
                .withEndAction(() -> searchPanel.setVisibility(View.GONE))
                .start();
    }

    /**
     * Real full-text search using pdfbox-android.
     * Scans every page on a background thread; results include page index + context snippet.
     */
    private void runSearch(String query) {
        if (query.isEmpty() || extractor == null || !extractor.isOpen()) return;
        searchResultText.setText("Searching…");
        searchResults.clear();
        searchResultIndex = -1;

        bgExecutor.execute(() -> {
            List<PdfTextExtractor.SearchResult> found = extractor.searchAll(query, totalPages);
            uiHandler.post(() -> {
                if (isDestroyed()) return;
                searchResults.addAll(found);
                if (searchResults.isEmpty()) {
                    searchResultText.setText("No results for "" + query + """);
                } else {
                    searchResultIndex = 0;
                    updateSearchLabel();
                    jumpToSearchResult();
                }
            });
        });
    }

    private void jumpToSearchResult() {
        if (searchResultIndex < 0 || searchResultIndex >= searchResults.size()) return;
        PdfTextExtractor.SearchResult r = searchResults.get(searchResultIndex);
        updateSearchLabel();
        navigateToPage(r.page);
    }

    private void updateSearchLabel() {
        if (searchResults.isEmpty()) return;
        PdfTextExtractor.SearchResult r = searchResults.get(searchResultIndex);
        searchResultText.setText(
                "Result " + (searchResultIndex + 1) + " of " + searchResults.size()
                        + "  —  " + r.snippet);
    }

    // =========================================================
    // BOOKMARKS
    // =========================================================

    private void toggleBookmark() {
        if (bookmarkedPages.contains(currentPage)) {
            bookmarkedPages.remove(currentPage);
            showToast("Bookmark removed");
        } else {
            bookmarkedPages.add(currentPage);
            showToast("Page " + (currentPage + 1) + " bookmarked ★");
        }
        updateBookmarkIcon();
        saveBookmarks();
        // Keep fallback TOC in sync if we are using it
        if (tocEntries.isEmpty() || (tocEntries.get(0).depth == -1)) {
            buildFallbackToc();
            if (tocAdapter != null) tocAdapter.notifyDataSetChanged();
        }
    }

    private void updateBookmarkIcon() {
        boolean on = bookmarkedPages.contains(currentPage);
        btnBookmark.setImageResource(on
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);
        btnBookmark.setColorFilter(on ? Color.parseColor("#FFD700") : Color.WHITE);
    }

    private void saveBookmarks() {
        if (currentUri == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (int p : bookmarkedPages) arr.put(p);
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_BOOKMARKS + currentUri.hashCode(), arr.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private void loadBookmarks() {
        if (currentUri == null) return;
        bookmarkedPages.clear();
        try {
            String json = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_BOOKMARKS + currentUri.hashCode(), "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) bookmarkedPages.add(arr.getInt(i));
        } catch (Exception ignored) {}
    }

    // =========================================================
    // DRAW MODE
    // =========================================================

    private void setupDrawToolbar() {
        btnDrawUndo.setOnClickListener(v -> drawingView.undoLastStroke());
        btnDrawClear.setOnClickListener(v -> {
            drawingView.clearAll();
            pageStrokes.remove(currentPage);
        });

        // Pen sizes
        findViewById(R.id.penThin).setOnClickListener(v  -> drawingView.setPenWidth(3f));
        findViewById(R.id.penThick).setOnClickListener(v -> drawingView.setPenWidth(12f));

        // Colors
        findViewById(R.id.colorRed).setOnClickListener(v -> {
            drawingView.setPenColor(Color.RED);
            drawingView.setPenWidth(6f);
        });
        findViewById(R.id.colorBlue).setOnClickListener(v -> {
            drawingView.setPenColor(Color.parseColor("#4488FF"));
            drawingView.setPenWidth(6f);
        });
        findViewById(R.id.colorYellow).setOnClickListener(v -> {
            // Yellow with wide width = highlighter feel
            drawingView.setPenColor(Color.parseColor("#CCFFDD00"));
            drawingView.setPenWidth(22f);
        });
        findViewById(R.id.colorWhite).setOnClickListener(v -> {
            drawingView.setPenColor(Color.WHITE);
            drawingView.setPenWidth(6f);
        });
        findViewById(R.id.colorGreen).setOnClickListener(v -> {
            drawingView.setPenColor(Color.parseColor("#44DD88"));
            drawingView.setPenWidth(6f);
        });
    }

    private void toggleDrawMode() {
        drawModeActive = !drawModeActive;
        drawingView.setDrawingEnabled(drawModeActive);
        drawToolbar.setVisibility(drawModeActive ? View.VISIBLE : View.GONE);
        btnDraw.setColorFilter(drawModeActive
                ? Color.parseColor("#4488FF")
                : Color.parseColor("#555555"));
        // Reset zoom when entering draw mode so strokes align with page pixels
        if (drawModeActive) zoomContainer.resetZoom();
    }

    // =========================================================
    // TABLE OF CONTENTS
    // =========================================================

    private void setupToc() {
        tocAdapter = new TocAdapter(tocEntries, page -> {
            hideToc();
            navigateToPage(page);
        });
        tocRecycler.setLayoutManager(new LinearLayoutManager(this));
        tocRecycler.setAdapter(tocAdapter);
        btnTocClose.setOnClickListener(v -> hideToc());
        tocSheetBackdrop.setOnClickListener(v -> hideToc());
    }

    /**
     * Fallback TOC when the PDF has no embedded outline.
     * Shows bookmarked pages at the top, then evenly-spaced page milestones.
     */
    private void buildFallbackToc() {
        tocEntries.clear();

        // Bookmarks first, sorted ascending
        List<Integer> sorted = new ArrayList<>(bookmarkedPages);
        Collections.sort(sorted);
        for (int p : sorted) {
            // depth = -1 is our sentinel for "bookmark row"
            tocEntries.add(new PdfTextExtractor.TocEntry("★  Page " + (p + 1), p, -1));
        }

        // Page milestones — one per page if ≤20, else ~20 evenly spread
        int step = Math.max(1, totalPages / 20);
        for (int i = 0; i < totalPages; i += step) {
            tocEntries.add(new PdfTextExtractor.TocEntry("Page " + (i + 1), i, 0));
        }
        // Always include last page
        if (totalPages > 1) {
            int last = totalPages - 1;
            boolean alreadyIn = false;
            for (PdfTextExtractor.TocEntry e : tocEntries) {
                if (e.page == last && e.depth == 0) { alreadyIn = true; break; }
            }
            if (!alreadyIn) tocEntries.add(new PdfTextExtractor.TocEntry("Page " + totalPages, last, 0));
        }
    }

    private void showToc() {
        // Refresh bookmark rows in fallback TOC every time sheet opens
        if (!tocEntries.isEmpty() && tocEntries.get(0).depth == -1 || tocEntries.isEmpty()) {
            buildFallbackToc();
            if (tocAdapter != null) tocAdapter.notifyDataSetChanged();
        }

        tocSheetBackdrop.setVisibility(View.VISIBLE);
        tocSheetBackdrop.setAlpha(0f);
        tocSheetBackdrop.animate().alpha(1f).setDuration(180).start();

        tocSheet.setVisibility(View.VISIBLE);
        tocSheet.animate()
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
    }

    private void hideToc() {
        tocSheetBackdrop.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> tocSheetBackdrop.setVisibility(View.GONE)).start();
        tocSheet.animate().translationY(3000).setDuration(260)
                .withEndAction(() -> tocSheet.setVisibility(View.GONE)).start();
    }

    // =========================================================
    // READ ALOUD — real TTS with real pdfbox text extraction
    // =========================================================

    private void setupTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.getDefault());
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(0.92f);
                tts.setPitch(1.0f);
            }
        });

        btnTtsPlayPause.setOnClickListener(v -> {
            if (ttsPlaying) pauseTts();
            else resumeTts();
        });
        btnTtsStop.setOnClickListener(v -> stopTts());
    }

    private void toggleReadAloud() {
        if (ttsPlaying) {
            stopTts();
        } else {
            if (!ttsReady) { showToast("TTS engine not ready"); return; }
            startReadingPage(currentPage);
        }
    }

    /**
     * Extracts real text from the given 0-based page via pdfbox on a background
     * thread, then queues it to the TTS engine in ≤4000-char chunks.
     */
    private void startReadingPage(int pageIndex) {
        if (extractor == null || !extractor.isOpen()) {
            showToast("PDF not ready");
            return;
        }

        ttsStatusText.setText("Extracting text from page " + (pageIndex + 1) + "…");
        showReadAloudBar(true);

        bgExecutor.execute(() -> {
            // Real text extraction — blocking, done off main thread
            String text = extractor.extractPageText(pageIndex);

            uiHandler.post(() -> {
                if (isDestroyed()) return;

                if (text == null || text.isEmpty()) {
                    ttsStatusText.setText("Page " + (pageIndex + 1) + " has no extractable text (image PDF)");
                    ttsPlaying = false;
                    return;
                }

                speakChunked(text, pageIndex);
            });
        });
    }

    /**
     * Android TTS silently drops text longer than ~4000 chars.
     * Split into chunks and queue them all.
     */
    private void speakChunked(String text, int pageIndex) {
        ttsPlaying = true;
        btnTtsPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        btnReadAloud.setColorFilter(Color.parseColor("#4488FF"));
        ttsStatusText.setText("Reading page " + (pageIndex + 1) + "…");

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}

            @Override
            public void onDone(String id) {
                // Only update UI when the LAST chunk finishes
                if (id.endsWith("_last")) {
                    uiHandler.post(() -> {
                        ttsPlaying = false;
                        btnTtsPlayPause.setImageResource(android.R.drawable.ic_media_play);
                        btnReadAloud.setColorFilter(Color.parseColor("#555555"));
                        ttsStatusText.setText("Finished page " + (pageIndex + 1));
                    });
                }
            }

            @Override
            public void onError(String id) {
                uiHandler.post(() -> stopTts());
            }
        });

        // Flush any current speech then queue all chunks
        tts.stop();
        int chunkSize = 3800; // safe margin below 4000
        int offset = 0;
        int chunkIndex = 0;
        while (offset < text.length()) {
            int end = Math.min(offset + chunkSize, text.length());
            // Try to break at sentence boundary within last 200 chars of chunk
            if (end < text.length()) {
                int breakAt = findSentenceBreak(text, offset, end);
                if (breakAt > offset) end = breakAt;
            }
            String chunk = text.substring(offset, end).trim();
            boolean isLast = (end >= text.length());
            String utteranceId = "pg" + pageIndex + "_" + chunkIndex + (isLast ? "_last" : "");
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, utteranceId);
            offset = end;
            chunkIndex++;
        }
    }

    /** Finds the last sentence-ending punctuation before {@code end} within the chunk. */
    private int findSentenceBreak(String text, int start, int end) {
        for (int i = end - 1; i > start + (end - start) / 2; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') return i + 1;
        }
        return end;
    }

    private void pauseTts() {
        if (tts != null) tts.stop();
        ttsPlaying = false;
        btnTtsPlayPause.setImageResource(android.R.drawable.ic_media_play);
        ttsStatusText.setText("Paused");
    }

    private void resumeTts() {
        // Re-read current page from beginning (Android TTS has no resume)
        startReadingPage(currentPage);
    }

    private void stopTts() {
        if (tts != null) tts.stop();
        ttsPlaying = false;
        showReadAloudBar(false);
        if (btnReadAloud != null) btnReadAloud.setColorFilter(Color.parseColor("#555555"));
    }

    private void showReadAloudBar(boolean show) {
        if (show) {
            readAloudBar.setVisibility(View.VISIBLE);
            readAloudBar.setAlpha(0f);
            readAloudBar.animate().alpha(1f).setDuration(200).start();
        } else {
            readAloudBar.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> readAloudBar.setVisibility(View.GONE)).start();
        }
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    /**
     * Unified page navigation — saves/restores strokes, updates all UI.
     */
    private void navigateToPage(int page) {
        if (page < 0 || page >= totalPages) return;
        // Save current page strokes before leaving
        pageStrokes.put(currentPage, drawingView.detachStrokes());
        currentPage = page;
        // Restore strokes for destination page (null-safe: attachStrokes handles null)
        drawingView.attachStrokes(pageStrokes.get(page));
        curlView.setCurrentIndex(page);
        syncSlider(page);
        updatePageText(page);
        updateBookmarkIcon();
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private void syncSlider(int page) {
        if (totalPages <= 1) return;
        internalSliderUpdate = true;
        float clamped = Math.max(slider.getValueFrom(), Math.min(page, slider.getValueTo()));
        slider.setValue(clamped);
        internalSliderUpdate = false;
    }

    private void updatePageText(int page) {
        int display = Math.max(0, Math.min(page, totalPages - 1));
        pageText.setText((display + 1) + " / " + totalPages);
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        errorView.setVisibility(View.VISIBLE);
        errorMessage.setText(message);
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        float alpha = visible ? 1f : 0f;
        int   vis   = visible ? View.VISIBLE : View.INVISIBLE;
        topBar.animate().alpha(alpha).setDuration(200)
                .withEndAction(() -> topBar.setVisibility(vis)).start();
        controlBar.animate().alpha(alpha).setDuration(200)
                .withEndAction(() -> controlBar.setVisibility(vis)).start();
    }

    private void scheduleHideControls() {
        uiHandler.removeCallbacks(hideControls);
        uiHandler.postDelayed(hideControls, HIDE_DELAY_MS);
    }

    private void showToast(String message) {
        bookmarkToast.setText(message);
        bookmarkToast.setVisibility(View.VISIBLE);
        bookmarkToast.setAlpha(1f);
        bookmarkToast.animate()
                .alpha(0f)
                .setDuration(900)
                .setStartDelay(1300)
                .withEndAction(() -> bookmarkToast.setVisibility(View.GONE))
                .start();
    }

    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null)
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    // =========================================================
    // PERSISTENCE
    // =========================================================

    private void saveLastPage() {
        if (currentUri == null || totalPages <= 0) return;
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_LAST_PAGE + currentUri.hashCode(), currentPage)
                .apply();
    }

    private int restoreLastPage(Uri uri) {
        if (totalPages <= 0) return 0;
        int saved = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_LAST_PAGE + uri.hashCode(), 0);
        return Math.max(0, Math.min(saved, totalPages - 1));
    }

    // =========================================================
    // BITMAP HELPERS
    // =========================================================

    private Bitmap blankBitmap(int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            return b;
        } catch (OutOfMemoryError e) {
            Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            return b;
        }
    }

    private Bitmap safeCopy(Bitmap src, int w, int h) {
        if (src == null || src.isRecycled()) return blankBitmap(w, h);
        try {
            Bitmap copy = src.copy(Bitmap.Config.ARGB_8888, false);
            return copy != null ? copy : src;
        } catch (OutOfMemoryError e) {
            return src;
        }
    }

    // =========================================================
    // STRING HELPERS
    // =========================================================

    private String cleanFileName(String name) {
        if (name == null || name.isEmpty()) return "PDF";
        return name.replaceAll("(?i)\\.pdf$", "").replace("_", " ").trim();
    }

    private String safeMessage(Exception e) {
        if (e == null) return "Unknown error";
        String msg = e.getMessage();
        return (msg != null && !msg.isEmpty()) ? msg : e.getClass().getSimpleName();
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    private void closePdfCore() {
        coreReady.set(false);
        PdfCore old = core;
        core = null;
        if (old != null) {
            if (!renderExecutor.isShutdown()) {
                renderExecutor.execute(() -> {
                    try { old.close(); } catch (Exception ignored) {}
                });
            } else {
                new Thread(() -> {
                    try { old.close(); } catch (Exception ignored) {}
                }, "PdfClose-Fallback").start();
            }
        }
    }

    // =========================================================
    // TOC ADAPTER
    // =========================================================

    private static class TocAdapter extends RecyclerView.Adapter<TocAdapter.VH> {

        interface OnPageClick { void go(int page); }

        private final List<PdfTextExtractor.TocEntry> entries;
        private final OnPageClick                     listener;

        TocAdapter(List<PdfTextExtractor.TocEntry> entries, OnPageClick listener) {
            this.entries  = entries;
            this.listener = listener;
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int type) {
            TextView tv = new TextView(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            PdfTextExtractor.TocEntry e = entries.get(pos);

            // Indent child chapters; bookmarks (depth=-1) get gold color
            int paddingStart = e.depth <= 0 ? 56 : 56 + (e.depth * 28);
            h.tv.setPadding(paddingStart, 22, 40, 22);

            if (e.depth == -1) {
                // Bookmark row
                h.tv.setText(e.title);
                h.tv.setTextColor(Color.parseColor("#FFD700"));
                h.tv.setTextSize(13f);
            } else if (e.depth == 0) {
                // Top-level chapter
                h.tv.setText(e.title);
                h.tv.setTextColor(Color.parseColor("#E8E8E8"));
                h.tv.setTextSize(14f);
            } else {
                // Sub-section
                h.tv.setText(e.title);
                h.tv.setTextColor(Color.parseColor("#888888"));
                h.tv.setTextSize(13f);
            }

            h.tv.setOnClickListener(v -> listener.go(e.page));
        }

        @Override
        public int getItemCount() { return entries.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(TextView v) { super(v); tv = v; }
        }
    }
}