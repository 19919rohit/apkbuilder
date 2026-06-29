package neunix.pageflow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    // AUDIO (SOUNDPOOL)
    // =========================================================
    private SoundPool soundPool;
    private int       flipSoundId = -1;

    // Smart-audio touch state trackers
    private long    touchDownTime             = 0L;
    private boolean isUserTouching            = false;
    private int     indexAtTouchDown          = 0;

    // Proportional Audio Engine Configurations
    private float startX = 0f;
    private float maxPercentReached = 0f;
    private int flipStreamId = 0;
    private long playedDurationMs = 0;
    private long lastUpdateTime = 0;
    private boolean isSoundPlaying = false;
    private static final long SOUND_DURATION_MS = 600L; // Estimated playback length of page_flip.ogg

    // =========================================================
    // PDF STATE
    // =========================================================
    private PdfCore core;
    private Uri     currentUri;
    private String  currentFileName = "";

    // Guarded by uiHandler — only read/written on main thread
    private int currentPage = 0;
    private int totalPages  = 0;

    // Bitmap dimensions
    private volatile int bitmapWidth  = 0;
    private volatile int bitmapHeight = 0;

    // =========================================================
    // RENDER STATE
    // =========================================================
    private final Handler         uiHandler      = new Handler(Looper.getMainLooper());
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r ->
                    new Thread(r, "PdfActivity-Render"));

    private final AtomicBoolean coreReady = new AtomicBoolean(false);

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
    // TIME-SLICED AUDIO LOOP RUNNABLE
    // =========================================================
    private final Runnable soundProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (!isSoundPlaying || flipStreamId == 0) return;

            long now = System.currentTimeMillis();
            playedDurationMs += (now - lastUpdateTime);
            lastUpdateTime = now;

            long targetDuration = (long) (maxPercentReached * SOUND_DURATION_MS);
            if (playedDurationMs >= targetDuration) {
                if (soundPool != null && flipStreamId != 0) {
                    soundPool.pause(flipStreamId);
                }
                isSoundPlaying = false;
                playedDurationMs = targetDuration; // Clamp bounds
            } else {
                uiHandler.postDelayed(this, 16); // Sync to ~60Hz frames
            }
        }
    };

    // =========================================================
    // LIFECYCLE
    // =========================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        setContentView(R.layout.activity_pdf);

        initSoundPool();
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
        // 1. Clear UI updates immediately
        uiHandler.removeCallbacksAndMessages(null);
        
        // 2. Safely close PdfCore resources first
        closePdfCore();
        
        // 3. Terminate executor safely 
        renderExecutor.shutdownNow();
        
        // 4. Teardown audio engine
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        super.onDestroy();
    }

    // =========================================================
    // TOUCH INTERCEPT MOTION DISPATCH
    // Precise calculations for proportional page curl soundscapes
    // =========================================================
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownTime = System.currentTimeMillis();
                isUserTouching = true;
                indexAtTouchDown = currentPage;

                // Set up baseline coordinates
                startX = ev.getX();
                maxPercentReached = 0f;
                playedDurationMs = 0;
                isSoundPlaying = false;
                uiHandler.removeCallbacks(soundProgressUpdater);

                if (flipStreamId != 0) {
                    soundPool.stop(flipStreamId);
                    flipStreamId = 0;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isUserTouching) {
                    int width = getWindow().getDecorView().getWidth();
                    if (width <= 0) width = 1080; // Safety scale fallback

                    float deltaX = startX - ev.getX();
                    float currentPercent = Math.min(1.0f, Math.max(0.0f, Math.abs(deltaX) / width));

                    if (currentPercent > maxPercentReached) {
                        maxPercentReached = currentPercent;
                        long targetDuration = (long) (maxPercentReached * SOUND_DURATION_MS);

                        if (playedDurationMs < targetDuration) {
                            if (!isSoundPlaying && soundPool != null) {
                                isSoundPlaying = true;
                                lastUpdateTime = System.currentTimeMillis();
                                if (flipStreamId == 0) {
                                    flipStreamId = soundPool.play(flipSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
                                } else {
                                    soundPool.resume(flipStreamId);
                                }
                                uiHandler.post(soundProgressUpdater);
                            }
                        }
                    } else {
                        // User stopped moving forward or dragged backward: freeze audio track
                        if (isSoundPlaying) {
                            long now = System.currentTimeMillis();
                            playedDurationMs += (now - lastUpdateTime);
                            if (soundPool != null && flipStreamId != 0) {
                                soundPool.pause(flipStreamId);
                            }
                            isSoundPlaying = false;
                            uiHandler.removeCallbacks(soundProgressUpdater);
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isUserTouching = false;
                uiHandler.removeCallbacks(soundProgressUpdater);
                if (isSoundPlaying) {
                    if (soundPool != null && flipStreamId != 0) {
                        soundPool.pause(flipStreamId);
                    }
                    isSoundPlaying = false;
                }

                // Wait slightly to verify if the page flip transaction completed structurally
                uiHandler.postDelayed(() -> {
                    if (currentPage != indexAtTouchDown) {
                        // Complete Success! Finish playing out the remainder of the soundscape
                        if (flipStreamId != 0 && soundPool != null) {
                            soundPool.resume(flipStreamId);
                        } else {
                            playFlipSound();
                        }
                    } else {
                        // Flip was aborted or turned back: instantly kill audio stream
                        if (flipStreamId != 0 && soundPool != null) {
                            soundPool.stop(flipStreamId);
                            flipStreamId = 0;
                        }
                    }
                }, 80);
                break;
        }

        if (!controlsVisible) {
            setControlsVisible(true);
        }
        scheduleHideControls();
        
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onUserInteraction() {
        // Managed inside dispatchTouchEvent
    }

    // =========================================================
    // INITIALIZATION HELPERS
    // =========================================================
    private void initSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3) 
                .setAudioAttributes(audioAttributes)
                .build();

        try (AssetFileDescriptor afd = getAssets().openFd("page_flip.ogg")) {
            flipSoundId = soundPool.load(afd, 1);
        } catch (Exception ignored) { }
    }

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
        coreReady.set(false);
        bitmapWidth  = 0;
        bitmapHeight = 0;
        currentUri = uri;

        renderExecutor.execute(() -> {
            try {
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

                String fileName = FileUtils.getFileName(this, uri);

                uiHandler.post(() -> {
                    if (isDestroyed()) {
                        newCore.close();
                        return;
                    }

                    core            = newCore;
                    currentFileName = fileName;
                    totalPages      = pages;
                    currentPage     = restoreLastPage(uri);

                    coreReady.set(true);

                    titleText.setText(cleanFileName(currentFileName));
                    setupSliderRange();

                    curlView.setPageProvider(pageProvider);
                    curlView.setCurrentIndex(currentPage);
                    curlView.setViewMode(CurlView.SHOW_ONE_PAGE);

                    showLoading(false);
                    updatePageText(currentPage);
                });

            } catch (Exception e) {
                uiHandler.post(() -> {
                    if (isDestroyed()) return;
                    showLoading(false);
                    showError("Could not open PDF.\n" + safeMessage(e));
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
            if (bitmapWidth == 0 || bitmapHeight == 0) {
                bitmapWidth  = Math.max(width,  1);
                bitmapHeight = Math.max(height, 1);
                PdfCore snap = core;
                if (snap != null) snap.setScreenSize(bitmapWidth, bitmapHeight);
            }

            final int w = Math.max(width,  1);
            final int h = Math.max(height, 1);

            if (!coreReady.get() || core == null || index < 0 || index >= totalPages) {
                page.setTexture(blankBitmap(w, h), CurlPage.SIDE_BOTH);
                return;
            }

            try {
                // Front Face
                Bitmap front     = core.renderPage(index, w, h);
                Bitmap frontCopy = safeCopy(front, w, h);
                page.setTexture(frontCopy, CurlPage.SIDE_FRONT);

                // Back Face
                if (index + 1 < totalPages) {
                    Bitmap back     = core.renderPage(index + 1, w, h);
                    Bitmap backCopy = safeCopy(back, w, h);
                    page.setTexture(backCopy, CurlPage.SIDE_BACK);
                } else {
                    Bitmap backCover = blankBitmap(w, h);
                    backCover.eraseColor(0xFFF0EDE8); 
                    page.setTexture(backCover, CurlPage.SIDE_BACK);
                    page.setColor(0xFFE8E4DF, CurlPage.SIDE_BACK);
                }

                // Sync Layout State to Main UI thread
                final int capturedIndex = index;
                uiHandler.post(() -> {
                    if (isDestroyed()) return;
                    if (capturedIndex != currentPage) {
                        currentPage = capturedIndex;
                        updatePageText(capturedIndex);
                        syncSlider(capturedIndex);
                        
                        // Programmatic Trigger (only when jumping/clicking slider directly)
                        if (!isUserTouching) {
                            playFlipSound();
                        }

                        // Background prefetching optimization
                        PdfCore snap = core;
                        if (snap != null && bitmapWidth > 0) {
                            if (prefetchFutures != null) {
                                snap.cancelPrefetch(prefetchFutures);
                            }
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
    // AUDIO ENGINE PLAYBACK
    // =========================================================
    private void playFlipSound() {
        if (soundPool != null && flipSoundId != -1) {
            soundPool.play(flipSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    // =========================================================
    // SETUP
    // =========================================================
    private void setupCurlView() {
        curlView.setBackgroundColor(0xFF0A0A0A);
        curlView.setAllowLastPageCurl(true);
        curlView.setRenderLeftPage(false);
        curlView.setMargins(0.0f, 0.0f, 0.0f, 0.0f);

        curlView.setSizeChangedObserver((w, h) -> {
            bitmapWidth  = w;
            bitmapHeight = h;
            PdfCore snap = core;
            if (snap != null) snap.setScreenSize(w, h);
        });
    }

    private void setupSlider() {
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser || internalSliderUpdate) return;

            int target = (int) value;
            updatePageText(target);

            PdfCore snap = core;
            if (prefetchFutures != null && snap != null) {
                snap.cancelPrefetch(prefetchFutures);
                prefetchFutures = null;
            }

            if (sliderDebounce != null) {
                uiHandler.removeCallbacks(sliderDebounce);
            }

            sliderDebounce = () -> {
                if (!coreReady.get()) return;
                if (target == currentPage)  return;

                PdfCore s1 = core;
                if (s1 != null) s1.evictExcept(target - 3, target + 3);

                currentPage = target;
                curlView.setCurrentIndex(target);
                updatePageText(target);
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

        float safeValue = Math.max(0f, Math.min(currentPage, valueTo));
        slider.setValue(safeValue);

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
    private void syncSlider(int page) {
        if (totalPages <= 1) return;
        internalSliderUpdate = true;
        float v = Math.max(slider.getValueFrom(), Math.min(page, slider.getValueTo()));
        slider.setValue(v);
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
        if (currentUri == null || totalPages <= 0) return;
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
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
            return (copy != null) ? copy : src;
        } catch (OutOfMemoryError e) {
            return src;
        }
    }

    // =========================================================
    // STRING HELPERS
    // =========================================================
    private String cleanFileName(String name) {
        if (name == null || name.isEmpty()) return "PDF";
        return name.replaceAll("(?i)\\.pdf$", "")
                   .replace("_", " ")
                   .trim();
    }

    private String safeMessage(Exception e) {
        if (e == null) return "Unknown error";
        String msg = e.getMessage();
        return (msg != null && !msg.isEmpty()) ? msg : e.getClass().getSimpleName();
    }

    // =========================================================
    // CLEANUP (Thread-Safe Crash Avoidance Execution)
    // =========================================================
    private void closePdfCore() {
        coreReady.set(false);
        PdfCore old = core;
        core = null;
        if (old != null) {
            // Check if the current render executor can still process requests
            if (!renderExecutor.isShutdown()) {
                renderExecutor.execute(() -> {
                    try { old.close(); } catch (Exception ignored) { }
                });
            } else {
                // Safe Fallback: Handle asynchronous destruction on a quick custom thread 
                // to completely bypass RejectedExecutionExceptions inside onDestroy()
                new Thread(() -> {
                    try { old.close(); } catch (Exception ignored) { }
                }, "PdfClose-Fallback").start();
            }
        }
    }
}
