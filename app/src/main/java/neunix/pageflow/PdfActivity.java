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
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PdfActivity extends AppCompatActivity {

    // =========================================================
    // PREFS
    // =========================================================

    private static final String PREFS_NAME    = "pageflow_prefs";
    private static final String KEY_LAST_PAGE = "last_page_";

    // =========================================================
    // VIEWS
    // =========================================================

    private CurlView    curlView;
    private Slider      slider;
    private TextView    pageText;
    private TextView    titleText;
    private View        topBar;
    private View        controlBar;
    private View        loadingOverlay;
    private View        errorView;
    private TextView    errorMessage;
    private ImageButton btnOpenNew;
    private ImageButton btnBack;

    // =========================================================
    // PDF STATE
    // =========================================================

    private PdfCore core;
    private Uri     currentUri;
    private String  currentFileName = "";

    private int currentPage = 0;
    private int totalPages  = 0;

    // =========================================================
    // RENDER
    // =========================================================

    private final Handler         uiHandler      =
            new Handler(Looper.getMainLooper());
    private final ExecutorService renderExecutor =
            Executors.newSingleThreadExecutor(r ->
                    new Thread(r, "PdfActivity-Render"));

    private volatile boolean   coreReady     = false;
    private volatile Future<?> pendingRender = null;

    // =========================================================
    // SLIDER DEBOUNCE
    // =========================================================

    private boolean         internalSliderUpdate = false;
    private Runnable        sliderDebounce;
    private List<Future<?>> prefetchFutures;

    // =========================================================
    // CONTROLS AUTO-HIDE
    // =========================================================

    private static final long HIDE_DELAY_MS   = 3_000L;
    private boolean           controlsVisible = true;
    private final Runnable    hideControls    = () -> setControlsVisible(false);

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

        Uri incomingUri = getIntent().getData();
        if (incomingUri != null) {
            openPdf(incomingUri);
        } else {
            openFilePicker();
        }
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        renderExecutor.shutdownNow();
        closePdfCore();
    }

    // =========================================================
    // BIND
    // =========================================================

    private void bindViews() {
        curlView       = findViewById(R.id.curlView);
        slider         = findViewById(R.id.pageSlider);
        pageText       = findViewById(R.id.pageText);
        titleText      = findViewById(R.id.titleText);
        topBar         = findViewById(R.id.topBar);
        controlBar     = findViewById(R.id.controlBar);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        errorView      = findViewById(R.id.errorView);
        errorMessage   = findViewById(R.id.errorMessage);
        btnOpenNew     = findViewById(R.id.btnOpenNew);
        btnBack        = findViewById(R.id.btnBack);

        findViewById(R.id.btnRetryOpen)
                .setOnClickListener(v -> openFilePicker());
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
                }
        );
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
        coreReady = false;

        currentUri = uri;

        renderExecutor.execute(() -> {
            try {
                PdfCore newCore = new PdfCore();
                newCore.open(this, uri);

                // DO NOT call setScreenSize here.
                // CurlView passes exact bitmap dimensions into updatePage()
                // and we use those directly. setScreenSize is only used by
                // prefetchAround which we set after the first render.

                String fileName = FileUtils.getFileName(this, uri);

                uiHandler.post(() -> {
                    core            = newCore;
                    currentFileName = fileName;
                    totalPages      = core.pageCount();
                    currentPage     = restoreLastPage(uri);
                    coreReady       = true;

                    titleText.setText(currentFileName);
                    setupSliderRange();

                    curlView.setPageProvider(pageProvider);
                    curlView.setCurrentIndex(currentPage);
                    curlView.setViewMode(CurlView.SHOW_ONE_PAGE);

                    showLoading(false);
                    updatePageText(currentPage);
                });

            } catch (Exception e) {
                uiHandler.post(() -> {
                    showLoading(false);
                    showError("Could not open PDF.\n" + e.getMessage());
                });
            }
        });
    }

    // =========================================================
    // PAGE PROVIDER
    // The entire integration point with CurlView.
    // updatePage() is called on the GL thread — must be sync.
    // =========================================================

    private final CurlView.PageProvider pageProvider =
            new CurlView.PageProvider() {

        @Override
        public int getPageCount() {
            return totalPages;
        }

        @Override
        public void updatePage(CurlPage page,
                               int width, int height, int index) {

            if (!coreReady || core == null) {
                page.setTexture(blankBitmap(width, height), CurlPage.SIDE_BOTH);
                return;
            }

            try {
                // Use the exact dimensions CurlView requests —
                // this is the correct page size for the GL surface.
                // Also update PdfCore's screen size so prefetch
                // renders at the right resolution.
                core.setScreenSize(width, height);

                // Front face — current page
                Bitmap front = core.renderPage(index, width, height);
                // Copy so PdfCore cache eviction never affects CurlPage
                page.setTexture(
                        front.copy(Bitmap.Config.ARGB_8888, false),
                        CurlPage.SIDE_FRONT);

                // Back face — next page visible through the curl
                if (index + 1 < totalPages) {
                    Bitmap back = core.renderPage(index + 1, width, height);
                    page.setTexture(
                            back.copy(Bitmap.Config.ARGB_8888, false),
                            CurlPage.SIDE_BACK);
                } else {
                    page.setTexture(
                            blankBitmap(width, height),
                            CurlPage.SIDE_BACK);
                }

                // Update UI on main thread
                uiHandler.post(() -> {
                    if (index != currentPage) {
                        currentPage = index;
                        updatePageText(index);
                        syncSlider(index);
                        if (core != null) {
                            prefetchFutures =
                                    core.prefetchAround(index, 2);
                        }
                    }
                });

            } catch (Exception e) {
                page.setTexture(blankBitmap(width, height), CurlPage.SIDE_BOTH);
            }
        }
    };

    // =========================================================
    // SETUP
    // =========================================================

    private void setupCurlView() {
        curlView.setBackgroundColor(0xFF0A0A0A);
        curlView.setAllowLastPageCurl(true);

        // Single page mode — no left page rendered
        curlView.setRenderLeftPage(false);

        // Small margins so the page fills the screen properly
        // without being stretched or cut off
        curlView.setMargins(0.0f, 0.0f, 0.0f, 0.0f);

        // Update screen size when GL surface dimensions are known
        curlView.setSizeChangedObserver((w, h) -> {
            if (core != null) core.setScreenSize(w, h);
        });

        // CRITICAL: Do NOT call setOnTouchListener on CurlView.
        // CurlView sets itself as its own touch listener in init()
        // via setOnTouchListener(this). Overwriting that with our
        // own listener breaks all page flip gestures.
        // Instead we intercept touches via onUserInteraction() below.
    }

    @Override
    public void onUserInteraction() {
        // Called on every touch event at the Activity level —
        // use this instead of setOnTouchListener on CurlView
        // so we don't break CurlView's internal gesture handling.
        if (!controlsVisible) {
            setControlsVisible(true);
            scheduleHideControls();
        } else {
            scheduleHideControls();
        }
    }

    private void setupSlider() {
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser || internalSliderUpdate) return;

            int target = (int) value;
            updatePageText(target);

            if (prefetchFutures != null && core != null) {
                core.cancelPrefetch(prefetchFutures);
            }

            if (sliderDebounce != null) {
                uiHandler.removeCallbacks(sliderDebounce);
            }

            sliderDebounce = () -> {
                if (target == currentPage || !coreReady) return;
                if (core != null) core.evictExcept(target - 3, target + 3);
                currentPage = target;
                curlView.setCurrentIndex(target);
                updatePageText(target);
            };

            uiHandler.postDelayed(sliderDebounce, 120);
        });
    }

    private void setupSliderRange() {
        internalSliderUpdate = true;
        slider.setValueFrom(0f);
        slider.setValueTo(Math.max(1f, totalPages - 1));
        slider.setStepSize(1f);
        slider.setValue(currentPage);
        internalSliderUpdate = false;
        updatePageText(currentPage);
    }

    private void setupButtons() {
        btnOpenNew.setOnClickListener(v -> openFilePicker());
        btnBack.setOnClickListener(v -> finish());
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private Bitmap blankBitmap(int width, int height) {
        Bitmap b = Bitmap.createBitmap(
                Math.max(width, 1),
                Math.max(height, 1),
                Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        return b;
    }

    private void syncSlider(int page) {
        internalSliderUpdate = true;
        if (page >= slider.getValueFrom() && page <= slider.getValueTo()) {
            slider.setValue(page);
        }
        internalSliderUpdate = false;
    }

    private void updatePageText(int page) {
        pageText.setText((page + 1) + " / " + totalPages);
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

    // =========================================================
    // CONTROLS AUTO-HIDE
    // =========================================================

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        float alpha = visible ? 1f : 0f;
        int   vis   = visible ? View.VISIBLE : View.INVISIBLE;

        topBar.animate()
                .alpha(alpha).setDuration(200)
                .withEndAction(() -> topBar.setVisibility(vis))
                .start();

        controlBar.animate()
                .alpha(alpha).setDuration(200)
                .withEndAction(() -> controlBar.setVisibility(vis))
                .start();
    }

    private void scheduleHideControls() {
        uiHandler.removeCallbacks(hideControls);
        uiHandler.postDelayed(hideControls, HIDE_DELAY_MS);
    }

    // =========================================================
    // IMMERSIVE MODE
    // =========================================================

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              | View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    // =========================================================
    // LAST PAGE PERSISTENCE
    // =========================================================

    private void saveLastPage() {
        if (currentUri == null) return;
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_PAGE + currentUri.hashCode(), currentPage)
                .apply();
    }

    private int restoreLastPage(Uri uri) {
        int saved = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_LAST_PAGE + uri.hashCode(), 0);
        return Math.max(0, Math.min(saved, totalPages - 1));
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    private void closePdfCore() {
        if (core != null) {
            try { core.close(); } catch (Exception ignored) { }
            core = null;
        }
    }
}