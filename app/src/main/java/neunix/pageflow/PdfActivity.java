package neunix.pageflow;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

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

    // Views
    private GalleryZoomView zoomContainer;
    private CurlView        curlView;
    private DrawingView     drawingView;
    private Slider          slider;
    private TextView        pageText, titleText, errorMessage;
    private View            topBar, controlBar, loadingOverlay, errorView;

    // Controllers
    private PdfReaderController    reader;
    private PdfSearchController    search;
    private PdfBookmarkController  bookmarks;
    private PdfTocController       toc;
    private PdfReadAloudController readAloud;
    private PdfDrawController      draw;

    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideControls = () -> setControlsVisible(false);
    private boolean controlsVisible = true;
    private boolean internalSliderUpdate = false;
    private Runnable sliderDebounce;

    private final Map<Integer, List<DrawingView.Stroke>> pageStrokes = new HashMap<>();

    private ActivityResultLauncher<Intent> pdfPickerLauncher;

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

        reader = new PdfReaderController(this, this);

        setupSearch();
        setupBookmarks();
        setupToc();
        setupReadAloud();
        setupDraw();

        findViewById(R.id.btnRetryOpen).setOnClickListener(v -> openFilePicker());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOpenNew).setOnClickListener(v -> openFilePicker());
        TooltipUtil.apply(findViewById(R.id.btnBack), "Back");
        TooltipUtil.apply(findViewById(R.id.btnOpenNew), "Open another PDF");

        Uri incoming = getIntent().getData();
        if (incoming != null) openPdf(incoming);
        else openFilePicker();
    }

    @Override protected void onResume() { super.onResume(); curlView.onResume(); scheduleHideControls(); }

    @Override
    protected void onPause() {
        super.onPause();
        curlView.onPause();
        uiHandler.removeCallbacks(hideControls);
        reader.saveLastPage();
        readAloud.stop();
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

    // =========================================================
    // BIND
    // =========================================================
    private void bindViews() {
        zoomContainer  = findViewById(R.id.zoomContainer);
        curlView       = findViewById(R.id.curlView);
        drawingView    = findViewById(R.id.drawingView);
        slider         = findViewById(R.id.pageSlider);
        pageText       = findViewById(R.id.pageText);
        titleText      = findViewById(R.id.titleText);
        topBar         = findViewById(R.id.topBar);
        controlBar     = findViewById(R.id.controlBar);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        errorView      = findViewById(R.id.errorView);
        errorMessage   = findViewById(R.id.errorMessage);
    }

    // =========================================================
    // FILE PICKER
    // =========================================================
    private void registerFilePicker() {
        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
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
        showLoading(true);
        hideError();
        pageStrokes.clear();
        search.reset();
        reader.open(uri);
    }

    // =========================================================
    // PdfReaderController.Listener
    // =========================================================
    @Override
    public void onPdfOpened(int totalPages, String title) {
        runOnUiThread(() -> {
            titleText.setText(title);
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
        });
    }

    // =========================================================
    // SETUP
    // =========================================================
    private void setupCurlView() {
        curlView.setBackgroundColor(0xFF0A0A0A);
        curlView.setAllowLastPageCurl(true);
        curlView.setRenderLeftPage(false);
        curlView.setMargins(0f, 0f, 0f, 0f);
        curlView.setSizeChangedObserver((w, h) -> reader.setScreenSize(w, h));
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
                (EditText) findViewById(R.id.searchInput),
                (TextView) findViewById(R.id.searchResultText),
                (ImageButton) findViewById(R.id.btnSearchPrev),
                (ImageButton) findViewById(R.id.btnSearchNext),
                (ImageButton) findViewById(R.id.btnSearchClose),
                (ImageButton) findViewById(R.id.btnSearch),
                page -> reader.navigateToPage(curlView, drawingView, pageStrokes, page));
    }

    private void setupBookmarks() {
        bookmarks = new PdfBookmarkController(this, reader,
                findViewById(R.id.btnBookmark),
                findViewById(R.id.bookmarkToast),
                (TextView) findViewById(R.id.bookmarkToast),
                findViewById(R.id.bookmarkSheetBackdrop),
                findViewById(R.id.bookmarkSheet),
                (ImageButton) findViewById(R.id.btnBookmarkSheetClose),
                (RecyclerView) findViewById(R.id.bookmarkRecycler),
                findViewById(R.id.bookmarkEmptyState),
                page -> reader.navigateToPage(curlView, drawingView, pageStrokes, page));

        findViewById(R.id.btnBookmarkList).setOnClickListener(v -> bookmarks.showSheet());
        TooltipUtil.apply(findViewById(R.id.btnBookmarkList), "All bookmarks");
    }

    private void setupToc() {
        toc = new PdfTocController(this, reader,
                findViewById(R.id.tocSheetBackdrop),
                findViewById(R.id.tocSheet),
                (ImageButton) findViewById(R.id.btnTocClose),
                (ImageButton) findViewById(R.id.btnToc),
                (RecyclerView) findViewById(R.id.tocRecycler),
                page -> reader.navigateToPage(curlView, drawingView, pageStrokes, page));
    }

    private void setupReadAloud() {
        readAloud = new PdfReadAloudController(this, reader,
                findViewById(R.id.readAloudBar),
                (TextView) findViewById(R.id.ttsStatusText),
                (ImageButton) findViewById(R.id.btnTtsPlayPause),
                (ImageButton) findViewById(R.id.btnTtsStop),
                (ImageButton) findViewById(R.id.btnReadAloud));
        findViewById(R.id.btnReadAloud).setOnClickListener(v -> readAloud.toggle());
    }

    private void setupDraw() {
        draw = new PdfDrawController(drawingView, zoomContainer,
                findViewById(R.id.drawToolbar),
                (ImageButton) findViewById(R.id.btnDraw),
                (ImageButton) findViewById(R.id.btnDrawUndo),
                (ImageButton) findViewById(R.id.btnDrawClear),
                (ImageButton) findViewById(R.id.penThin),
                (ImageButton) findViewById(R.id.penThick),
                findViewById(R.id.colorRed),
                findViewById(R.id.colorBlue),
                findViewById(R.id.colorYellow),
                findViewById(R.id.colorWhite),
                findViewById(R.id.colorGreen),
                () -> pageStrokes.remove(reader.getSettledPage()));
    }

    // =========================================================
    // UI HELPERS
    // =========================================================
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