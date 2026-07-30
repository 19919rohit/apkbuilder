package neunix.pagevibe;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.Slider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfActivity extends AppCompatActivity implements PdfReaderController.Listener {

    private static final long HIDE_DELAY_MS = 3_000L;

    private GalleryZoomView      zoomContainer;
    private CurlView             curlView;
    private DrawingView          drawingView;
    private HighlightOverlayView highlightOverlay;
    private TextSelectionView    textSelectionView;
    private Slider               slider;
    private TextView             pageText, titleText, errorMessage;
    private View                 topBar, controlBar, loadingOverlay, errorView;
    private ImageButton          basketButton;

    // Hidden trigger buttons — still exist, still wired exactly as
    // before, just moved off-screen into the overflow menu.
    private ImageButton hiddenBtnSearch, hiddenBtnBookmark, hiddenBtnBookmarkList,
            hiddenBtnAddToBasket, hiddenBtnToc, hiddenBtnSelectText, hiddenBtnNotes, hiddenBtnOpenNew;

    private PdfReaderController    reader;
    private PdfSearchController    search;
    private PdfBookmarkController  bookmarks;
    private PdfTocController       toc;
    private PdfReadAloudController readAloud;
    private PdfDrawController      draw;
    private PdfSelectionController selection;
    private ReadingStatsController stats;
    private LibraryManager         libraryManager;
    private ThemeManager           themeManager;

    private final Handler  uiHandler            = new Handler(Looper.getMainLooper());
    private final Runnable hideControls         = () -> setControlsVisible(false);
    private boolean        controlsVisible      = true;
    private boolean        internalSliderUpdate = false;
    private Runnable       sliderDebounce;

    private boolean statsSessionActive = false;

    private final Map<Integer, List<DrawingView.Stroke>> pageStrokes = new HashMap<>();

    private ActivityResultLauncher<Intent> pdfPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        setContentView(R.layout.activity_pdf);

        AnalyticsHelper.logIfTagged(this, getIntent());

        bindViews();
        registerFilePicker();
        setupCurlView();
        setupSlider();

        reader         = new PdfReaderController(this, this);
        stats          = new ReadingStatsController(this);
        libraryManager = new LibraryManager(this);
        themeManager   = new ThemeManager(this);

        setupSearch();
        setupBookmarks();
        setupToc();
        setupReadAloud();
        setupDraw();
        setupSelection();
        setupBasketButton();
        setupNotesButton();
        setupOverflowMenu();

        draw.setOnActivateCallback(() -> selection.deactivate());
        selection.setOnActivateCallback(() -> draw.deactivate());

        findViewById(R.id.btnRetryOpen).setOnClickListener(v -> openFilePicker());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        hiddenBtnOpenNew.setOnClickListener(v -> openFilePicker());
        TooltipUtil.apply(findViewById(R.id.btnBack), "Back");
        TooltipUtil.apply(hiddenBtnOpenNew, "Open another PDF");

        applyTitleTheme();

        Uri incoming = getIntent().getData();
        if (incoming != null) openPdf(incoming);
        else openFilePicker();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        AnalyticsHelper.logIfTagged(this, intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        curlView.onResume();
        scheduleHideControls();
        NotificationHelper.isReaderForeground = true;
        applyTitleTheme(); // covers the user changing theme in Settings mid-session, then returning here
        if (reader.isReady() && !statsSessionActive) {
            stats.startSession(reader.getCurrentUri());
            ReadingPatternLearner.recordSessionStart(this);
            statsSessionActive = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        curlView.onPause();
        uiHandler.removeCallbacks(hideControls);
        reader.saveLastPage();
        readAloud.stop();
        NotificationHelper.isReaderForeground = false;
        if (statsSessionActive) {
            stats.endSession();
            statsSessionActive = false;
        }
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        reader.shutdown();
        readAloud.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!controlsVisible) setControlsVisible(true);
        scheduleHideControls();
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Light-touch theme application, scoped deliberately to just the
     * title text for now — the topBar/controlBar backgrounds use a
     * gradient-fade drawable (bg_bar_top/bg_bar_bottom) that would lose
     * its fade edge if flattened to a solid theme color, so full reader-
     * chrome retheming is left for a dedicated follow-up rather than
     * risking a visual regression here.
     */
    private void applyTitleTheme() {
        if (titleText == null || themeManager == null) return;
        ThemeManager.AppTheme theme = themeManager.getActiveTheme();
        titleText.setTextColor(theme.textPrimaryColor);
        try { titleText.setTypeface(Typeface.create(theme.fontFamily, Typeface.NORMAL)); } catch (Throwable ignored) {}
    }

    private void bindViews() {
        zoomContainer      = findViewById(R.id.zoomContainer);
        curlView           = findViewById(R.id.curlView);
        drawingView        = findViewById(R.id.drawingView);
        highlightOverlay   = findViewById(R.id.highlightOverlay);
        textSelectionView  = findViewById(R.id.textSelectionView);
        slider             = findViewById(R.id.pageSlider);
        pageText           = findViewById(R.id.pageText);
        titleText          = findViewById(R.id.titleText);
        topBar             = findViewById(R.id.topBar);
        controlBar         = findViewById(R.id.controlBar);
        loadingOverlay     = findViewById(R.id.loadingOverlay);
        errorView          = findViewById(R.id.errorView);
        errorMessage       = findViewById(R.id.errorMessage);

        hiddenBtnSearch        = findViewById(R.id.btnSearch);
        hiddenBtnBookmark      = findViewById(R.id.btnBookmark);
        hiddenBtnBookmarkList  = findViewById(R.id.btnBookmarkList);
        hiddenBtnAddToBasket   = findViewById(R.id.btnAddToBasket);
        hiddenBtnToc           = findViewById(R.id.btnToc);
        hiddenBtnSelectText    = findViewById(R.id.btnSelectText);
        hiddenBtnNotes         = findViewById(R.id.btnNotes);
        hiddenBtnOpenNew       = findViewById(R.id.btnOpenNew);
        basketButton           = hiddenBtnAddToBasket;
    }

    /**
     * Wires the new three-dot menu. Every row simply performClick()s the
     * ORIGINAL hidden trigger button — this is intentional: it means the
     * existing controller wiring (search.attachHighlightOverlay(...),
     * PdfBookmarkController's internal listener on this exact button,
     * PdfTocController's constructor-time click wiring, etc.) never had
     * to change at all, eliminating any risk of subtly breaking a
     * controller while reorganizing the visible layout around it.
     */
    private void setupOverflowMenu() {
        ImageButton btnOverflow = findViewById(R.id.btnPdfOverflow);
        btnOverflow.setOnClickListener(this::showOverflowMenu);
    }

    private void showOverflowMenu(View anchor) {
        View content = LayoutInflater.from(this).inflate(R.layout.popup_pdf_overflow_menu, null);

        TextView rowSearch       = content.findViewById(R.id.pdfMenuSearch);
        TextView rowBookmark     = content.findViewById(R.id.pdfMenuBookmark);
        TextView rowBookmarkList = content.findViewById(R.id.pdfMenuBookmarkList);
        TextView rowBasket       = content.findViewById(R.id.pdfMenuBasket);
        TextView rowToc          = content.findViewById(R.id.pdfMenuToc);
        TextView rowSelectText   = content.findViewById(R.id.pdfMenuSelectText);
        TextView rowNotes        = content.findViewById(R.id.pdfMenuNotes);
        TextView rowOpenNew      = content.findViewById(R.id.pdfMenuOpenNew);

        // Reflect current state before showing, so the label is always
        // accurate the moment the user opens the menu.
        boolean bookmarked = reader.isReady() && bookmarks.isBookmarked(reader.getSettledPage());
        rowBookmark.setText(bookmarked ? "Remove Bookmark" : "Bookmark this page");

        Uri uri = reader.getCurrentUri();
        boolean inBasket = reader.isReady() && uri != null
                && new PageBasketManager(this).contains(uri, reader.getSettledPage());
        rowBasket.setText(inBasket ? "Remove from Basket" : "Add to Basket");

        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(16f);

        rowSearch.setOnClickListener(v -> { popup.dismiss(); hiddenBtnSearch.performClick(); });
        rowBookmark.setOnClickListener(v -> { popup.dismiss(); hiddenBtnBookmark.performClick(); });
        rowBookmarkList.setOnClickListener(v -> { popup.dismiss(); hiddenBtnBookmarkList.performClick(); });
        rowBasket.setOnClickListener(v -> { popup.dismiss(); hiddenBtnAddToBasket.performClick(); });
        rowToc.setOnClickListener(v -> { popup.dismiss(); hiddenBtnToc.performClick(); });
        rowSelectText.setOnClickListener(v -> { popup.dismiss(); hiddenBtnSelectText.performClick(); });
        rowNotes.setOnClickListener(v -> { popup.dismiss(); hiddenBtnNotes.performClick(); });
        rowOpenNew.setOnClickListener(v -> { popup.dismiss(); hiddenBtnOpenNew.performClick(); });

        popup.showAsDropDown(anchor, 0, 8, Gravity.END);
    }

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
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pdfPickerLauncher.launch(i);
    }

    private void openPdf(Uri uri) {
        if (statsSessionActive) { stats.endSession(); statsSessionActive = false; }
        showLoading(true);
        hideError();
        pageStrokes.clear();
        if (highlightOverlay != null) {
            highlightOverlay.clearSearchHighlights();
            highlightOverlay.clearTtsHighlight();
            highlightOverlay.clearSelectionHighlights();
            highlightOverlay.clearPersistentHighlights();
        }
        search.reset();
        reader.open(uri);
    }

    @Override
    public void onPdfOpened(int totalPages, String title) {
        runOnUiThread(() -> {
            Uri currentUri = reader.getCurrentUri();
            String rawFileName = FileUtils.getFileName(this, currentUri);
            libraryManager.addOrTouch(currentUri, rawFileName);

            LibraryManager.Entry libEntry = libraryManager.findByUri(currentUri);
            titleText.setText(libEntry != null ? LibraryManager.displayName(libEntry) : title);
            applyTitleTheme();

            bookmarks.loadForUri(reader.getCurrentUri());
            setupSliderRange();

            PdfTextExtractor extractor = reader.getExtractor();
            List<PdfTextExtractor.TocEntry> outline = extractor != null ? extractor.extractOutline() : null;
            toc.buildFor(outline, totalPages);

            curlView.setPageProvider(reader.pageProvider);
            curlView.setCurrentIndex(reader.getSettledPage());
            curlView.setViewMode(CurlView.SHOW_ONE_PAGE);

            showLoading(false);
            updatePageText(reader.getSettledPage());
            bookmarks.updateIcon();
            updateHighlightPageSize(reader.getSettledPage());
            updateBasketIcon();
            selection.onPageChanged();
            selection.refreshPersistentHighlightsForPage(reader.getSettledPage());

            if (!statsSessionActive) {
                stats.startSession(reader.getCurrentUri());
                ReadingPatternLearner.recordSessionStart(this);
                statsSessionActive = true;
            }
        });
    }

    @Override
    public void onPdfOpenFailed(String message) {
        runOnUiThread(() -> { showLoading(false); showError(message); });
    }

    @Override
    public void onSettledPageChanged(int page) {
        runOnUiThread(() -> {
            updatePageText(page);
            syncSlider(page);
            bookmarks.updateIcon();
            updateHighlightPageSize(page);
            updateBasketIcon();
            search.onPageChanged(page);
            selection.onPageChanged();
            selection.refreshPersistentHighlightsForPage(page);
            if (highlightOverlay != null) highlightOverlay.clearTtsHighlight();
            if (statsSessionActive) stats.recordPageTurn();
        });
    }

    private void updateHighlightPageSize(int page) {
        if (highlightOverlay == null) return;
        PdfTextExtractor extractor = reader.getExtractor();
        if (extractor == null) return;
        float[] size = extractor.getPageSize(page);
        if (size != null) highlightOverlay.setPageSize(size[0], size[1]);
    }

    private void setupCurlView() {
        curlView.setBackgroundColor(0xFFFFFFFF);
        curlView.setAllowLastPageCurl(true);
        curlView.setRenderLeftPage(false);
        curlView.setMargins(0f, 0f, 0f, 0f);
        curlView.setSizeChangedObserver((w, h) -> reader.setScreenSize(w, h));
        curlView.post(() -> curlView.setBackgroundColor(0xFFFFFFFF));

        highlightOverlay.attachZoomHost(zoomContainer);
        zoomContainer.attachCurlView(curlView);

        curlView.setOnPageSettleListener(newIndex ->
                reader.reportSettledFromGesture(newIndex, drawingView, pageStrokes));
    }

    private void setupSlider() {
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser || internalSliderUpdate) return;
            int target = (int) value;
            updatePageText(target);
            if (sliderDebounce != null) uiHandler.removeCallbacks(sliderDebounce);
            sliderDebounce = () -> {
                if (!reader.isReady() || target == reader.getSettledPage()) return;
                reader.evictAroundSlider(target);
                reader.navigateToPage(curlView, drawingView, pageStrokes, target);
            };
            uiHandler.postDelayed(sliderDebounce, 150);
        });
    }

    private void setupSliderRange() {
        internalSliderUpdate = true;
        slider.setValueFrom(0f);
        float to = reader.getTotalPages() > 1 ? reader.getTotalPages() - 1 : 1f;
        slider.setValueTo(to);
        slider.setStepSize(1f);
        slider.setValue(Math.max(0f, Math.min(reader.getSettledPage(), to)));
        internalSliderUpdate = false;
        updatePageText(reader.getSettledPage());
    }

    private void setupSearch() {
        search = new PdfSearchController(this, reader,
                findViewById(R.id.searchPanel),
                (EditText)    findViewById(R.id.searchInput),
                (TextView)    findViewById(R.id.searchResultText),
                (ImageButton) findViewById(R.id.btnSearchPrev),
                (ImageButton) findViewById(R.id.btnSearchNext),
                (ImageButton) findViewById(R.id.btnSearchClose),
                hiddenBtnSearch,
                page -> reader.navigateToPage(curlView, drawingView, pageStrokes, page));
        search.attachHighlightOverlay(highlightOverlay);
    }

    private void setupBookmarks() {
        bookmarks = new PdfBookmarkController(this, reader,
                hiddenBtnBookmark,
                findViewById(R.id.bookmarkToast),
                (TextView)    findViewById(R.id.bookmarkToast),
                findViewById(R.id.bookmarkSheetBackdrop),
                findViewById(R.id.bookmarkSheet),
                (ImageButton) findViewById(R.id.btnBookmarkSheetClose),
                (RecyclerView) findViewById(R.id.bookmarkRecycler),
                findViewById(R.id.bookmarkEmptyState),
                page -> reader.navigateToPage(curlView, drawingView, pageStrokes, page));
        hiddenBtnBookmarkList.setOnClickListener(v -> bookmarks.showSheet());
        TooltipUtil.apply(hiddenBtnBookmarkList, "All bookmarks");
    }

    private void setupToc() {
        toc = new PdfTocController(this, reader,
                findViewById(R.id.tocSheetBackdrop),
                findViewById(R.id.tocSheet),
                (ImageButton) findViewById(R.id.btnTocClose),
                hiddenBtnToc,
                (RecyclerView) findViewById(R.id.tocRecycler),
                page -> reader.navigateToPage(curlView, drawingView, pageStrokes, page));
    }

    private void setupReadAloud() {
        readAloud = new PdfReadAloudController(this, reader,
                findViewById(R.id.readAloudBar),
                (TextView)    findViewById(R.id.ttsStatusText),
                (ImageButton) findViewById(R.id.btnTtsPlayPause),
                (ImageButton) findViewById(R.id.btnTtsStop),
                (ImageButton) findViewById(R.id.btnReadAloud));
        readAloud.attachHighlightOverlay(highlightOverlay);
        findViewById(R.id.btnReadAloud).setOnClickListener(v -> readAloud.toggle());
    }

    private void setupDraw() {
        draw = new PdfDrawController(
                drawingView, zoomContainer,
                findViewById(R.id.drawToolbar),
                (ImageButton) findViewById(R.id.btnDraw),
                (ImageButton) findViewById(R.id.btnDrawUndo),
                (ImageButton) findViewById(R.id.btnDrawClear),
                (ImageButton) findViewById(R.id.btnPen),
                (ImageButton) findViewById(R.id.btnHighlighter),
                (ImageButton) findViewById(R.id.penThin),
                (ImageButton) findViewById(R.id.penThick),
                findViewById(R.id.colorRed),
                findViewById(R.id.colorBlue),
                findViewById(R.id.colorYellow),
                findViewById(R.id.colorWhite),
                findViewById(R.id.colorGreen),
                () -> pageStrokes.remove(reader.getSettledPage()));
    }

    private void setupSelection() {
        selection = new PdfSelectionController(this, reader, readAloud,
                zoomContainer, textSelectionView, highlightOverlay,
                hiddenBtnSelectText);
    }

    private void setupBasketButton() {
        hiddenBtnAddToBasket.setOnClickListener(v -> {
            Uri uri = reader.getCurrentUri();
            if (uri == null || !reader.isReady()) return;

            int page = reader.getSettledPage();
            PageBasketManager basket = new PageBasketManager(this);

            if (basket.contains(uri, page)) {
                basket.removeEntry(uri, page);
                Toast.makeText(this, "Removed from basket", Toast.LENGTH_SHORT).show();
            } else {
                String name = titleText.getText() != null ? titleText.getText().toString() : "PDF";
                basket.addPage(uri, name, page);
                Toast.makeText(this, "Added page " + (page + 1) + " to basket", Toast.LENGTH_SHORT).show();
            }
            updateBasketIcon();
            popBasketIcon();
        });
        TooltipUtil.apply(hiddenBtnAddToBasket, "Add to basket");
    }

    private void setupNotesButton() {
        hiddenBtnNotes.setOnClickListener(v -> {
            Uri uri = reader.getCurrentUri();
            if (uri == null) return;
            Intent i = new Intent(this, NotesActivity.class);
            i.putExtra(NotesActivity.EXTRA_PDF_URI, uri.toString());
            i.putExtra(NotesActivity.EXTRA_PDF_NAME, titleText.getText() != null ? titleText.getText().toString() : "PDF");
            startActivity(i);
        });
        TooltipUtil.apply(hiddenBtnNotes, "Notes");
    }

    private void updateBasketIcon() {
        if (basketButton == null) return;
        Uri uri = reader.getCurrentUri();
        if (uri == null) { basketButton.setImageResource(R.drawable.ic_basket_outline); return; }
        boolean inBasket = new PageBasketManager(this).contains(uri, reader.getSettledPage());
        basketButton.setImageResource(inBasket ? R.drawable.ic_basket_filled : R.drawable.ic_basket_outline);
    }

    private void popBasketIcon() {
        if (basketButton == null) return;
        basketButton.animate().cancel();
        basketButton.setScaleX(0.7f);
        basketButton.setScaleY(0.7f);
        basketButton.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }

    private void syncSlider(int page) {
        if (reader.getTotalPages() <= 1) return;
        internalSliderUpdate = true;
        slider.setValue(Math.max(slider.getValueFrom(), Math.min(page, slider.getValueTo())));
        internalSliderUpdate = false;
    }

    private void updatePageText(int page) {
        int total = reader.getTotalPages();
        pageText.setText((Math.max(0, Math.min(page, total - 1)) + 1) + " / " + total);
    }

    private void showLoading(boolean show) { loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE); }
    private void showError(String msg) { errorView.setVisibility(View.VISIBLE); errorMessage.setText(msg); }
    private void hideError() { errorView.setVisibility(View.GONE); }

    private void setControlsVisible(boolean v) {
        controlsVisible = v;
        float a = v ? 1f : 0f;
        int vis = v ? View.VISIBLE : View.INVISIBLE;
        topBar.animate().alpha(a).setDuration(200).withEndAction(() -> topBar.setVisibility(vis)).start();
        controlBar.animate().alpha(a).setDuration(200).withEndAction(() -> controlBar.setVisibility(vis)).start();
    }

    private void scheduleHideControls() {
        uiHandler.removeCallbacks(hideControls);
        uiHandler.postDelayed(hideControls, HIDE_DELAY_MS);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}