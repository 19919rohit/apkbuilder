package neunix.pageflow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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

import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PdfActivity extends Activity {

    // =========================================================
    // PREFS KEYS
    // =========================================================

    private static final String PREFS_NAME    = "pageflow_prefs";
    private static final String KEY_LAST_PAGE = "last_page_";

    // =========================================================
    // VIEWS
    // =========================================================

    private PageFlipGLView pageFlipView;
    private Slider         slider;
    private TextView       pageText;
    private TextView       titleText;
    private View           topBar;
    private View           controlBar;
    private View           loadingOverlay;
    private View           errorView;
    private TextView       errorMessage;
    private ImageButton    btnOpenNew;
    private ImageButton    btnBack;

    // =========================================================
    // PDF STATE
    // =========================================================

    private PdfCore core;
    private Uri     currentUri;
    private String  currentFileName = "";

    private int currentPage = 0;
    private int totalPages  = 0;

    // =========================================================
    // RENDER STATE
    // =========================================================

    private final Handler        uiHandler      = new Handler(Looper.getMainLooper());
    private final ExecutorService renderExecutor =
            Executors.newSingleThreadExecutor(r ->
                    new Thread(r, "PdfActivity-Render"));

    private volatile boolean   rendering     = false;
    private volatile Future<?> pendingRender = null;

    private Bitmap bmpCurrent;
    private Bitmap bmpNext;
    private Bitmap bmpPrev;

    // =========================================================
    // SLIDER DEBOUNCE
    // =========================================================

    private boolean          internalSliderUpdate = false;
    private Runnable         sliderDebounce;
    private List<Future<?>>  prefetchFutures;

    // =========================================================
    // CONTROL BAR AUTO-HIDE
    // =========================================================

    private static final long HIDE_DELAY_MS  = 3_000L;
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
        setupFlipView();
        setupSlider();
        setupButtons();

        // If launched with a URI directly (e.g. from MainActivity recent list)
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
        pageFlipView.onResume();
        scheduleHideControls();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pageFlipView.onPause();
        uiHandler.removeCallbacks(hideControls);
        saveLastPage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        renderExecutor.shutdownNow();
        closePdfCore();
        recycleBitmaps();
    }

    // =========================================================
    // BIND VIEWS
    // =========================================================

    private void bindViews() {
        pageFlipView   = findViewById(R.id.pageFlipGL);
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

        // Retry button inside error view
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
        recycleBitmaps();

        currentUri = uri;

        renderExecutor.execute(() -> {
            try {
                PdfCore newCore = new PdfCore();
                newCore.open(this, uri);

                int w = pageFlipView.getWidth();
                int h = pageFlipView.getHeight();

                // If view not laid out yet, wait for it
                if (w == 0 || h == 0) {
                    pageFlipView.post(() -> {
                        int w2 = pageFlipView.getWidth();
                        int h2 = pageFlipView.getHeight();
                        if (w2 > 0 && h2 > 0) newCore.setScreenSize(w2, h2);
                    });
                } else {
                    newCore.setScreenSize(w, h);
                }

                String fileName = FileUtils.getFileName(this, uri);

                uiHandler.post(() -> {
                    core            = newCore;
                    currentFileName = fileName;
                    totalPages      = core.pageCount();
                    currentPage     = restoreLastPage(uri);

                    setupSliderRange();
                    titleText.setText(currentFileName);
                    loadPage(currentPage, true);
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
    // SETUP
    // =========================================================

    private void setupFlipView() {

        pageFlipView.setFlipListener(new PageFlipGLView.FlipListener() {

            @Override
            public void onFlipCommitted(int direction) {
                // Advance page index then load new triple
                currentPage = currentPage + direction;
                currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
                loadPage(currentPage, false);
                scheduleHideControls();
            }

            @Override
            public boolean canFlip(int direction) {
                if (core == null || rendering) return false;
                if (direction > 0) return currentPage < totalPages - 1;
                if (direction < 0) return currentPage > 0;
                return false;
            }
        });

        pageFlipView.setOnTouchInterceptListener(() -> {
            if (!controlsVisible) {
                setControlsVisible(true);
                scheduleHideControls();
            }
        });
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
                if (target == currentPage) return;
                if (core != null) {
                    core.evictExcept(target - 3, target + 3);
                }
                currentPage = target;
                loadPage(currentPage, false);
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
    // PAGE LOADING
    // =========================================================

    private void loadPage(int index, boolean showSpinner) {

        if (core == null) return;

        if (pendingRender != null) {
            pendingRender.cancel(false);
        }

        if (showSpinner) showLoading(true);

        rendering = true;

        final int target = index;

        pendingRender = renderExecutor.submit(() -> {
            try {
                Bitmap current = core.renderPage(target);
                Bitmap next    = (target < totalPages - 1)
                        ? core.renderPage(target + 1) : null;
                Bitmap prev    = (target > 0)
                        ? core.renderPage(target - 1) : null;

                uiHandler.post(() -> {
                    if (isDestroyed()) return;

                    bmpCurrent = current;
                    bmpNext    = next;
                    bmpPrev    = prev;

                    pageFlipView.setPages(bmpCurrent, bmpNext, bmpPrev);

                    syncSlider(target);
                    updatePageText(target);
                    showLoading(false);
                    hideError();

                    rendering = false;

                    prefetchFutures = core.prefetchAround(target, 2);
                });

            } catch (Exception e) {
                uiHandler.post(() -> {
                    if (isDestroyed()) return;
                    showLoading(false);
                    showError("Failed to render page "
                            + (target + 1) + ".\n" + e.getMessage());
                    rendering = false;
                });
            }
        });
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

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
        float alpha     = visible ? 1f : 0f;
        int   vis       = visible ? View.VISIBLE : View.INVISIBLE;

        topBar.animate()
                .alpha(alpha).setDuration(220)
                .withEndAction(() -> topBar.setVisibility(vis))
                .start();

        controlBar.animate()
                .alpha(alpha).setDuration(220)
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

    private void recycleBitmaps() {
        bmpCurrent = null;
        bmpNext    = null;
        bmpPrev    = null;
    }
}