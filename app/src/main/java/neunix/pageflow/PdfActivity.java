package neunix.pageflow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.google.android.material.slider.Slider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfActivity extends Activity {

    private static final int PICK = 100;

    private PageFlipGLView pageFlipView;
    private PdfCore core;

    private int currentPage = 0;

    // cached pages
    private Bitmap prevBmp;
    private Bitmap currBmp;
    private Bitmap nextBmp;

    private Slider slider;
    private TextView pageText;

    // UI
    private final Handler uiHandler =
            new Handler(Looper.getMainLooper());

    // render thread
    private final ExecutorService renderExecutor =
            Executors.newSingleThreadExecutor();

    // debounce
    private Runnable sliderRunnable;

    // rendering protection
    private volatile boolean isRendering = false;

    // prevents slider feedback loop
    private boolean internalSliderUpdate = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_pdf);

        pageFlipView =
                findViewById(R.id.pageFlipGL);

        slider =
                findViewById(R.id.pageSlider);

        pageText =
                findViewById(R.id.pageText);

        openPicker();
    }

    // ---------------------------------------------------
    // PDF PICKER
    // ---------------------------------------------------

    private void openPicker() {

        Intent i =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        i.setType("application/pdf");

        i.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(i, PICK);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != PICK) return;

        if (resultCode != RESULT_OK) return;

        if (data == null) return;

        try {

            Uri uri = data.getData();

            if (uri == null) return;

            core = new PdfCore();

            core.open(this, uri);

            setupSlider();

            setupFlipListener();

            loadPages();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    // ---------------------------------------------------
    // FLIP LISTENER
    // ---------------------------------------------------

    private void setupFlipListener() {

        pageFlipView.setFlipListener(direction -> {

            if (core == null) return;

            if (isRendering) return;

            // NEXT PAGE
            if (direction > 0) {

                if (currentPage
                        < core.pageCount() - 1) {

                    currentPage++;
                }
            }

            // PREVIOUS PAGE
            else if (direction < 0) {

                if (currentPage > 0) {

                    currentPage--;
                }
            }

            loadPages();
        });
    }

    // ---------------------------------------------------
    // SLIDER
    // ---------------------------------------------------

    private void setupSlider() {

        if (core == null) return;

        slider.setValueFrom(0f);

        slider.setValueTo(
                Math.max(
                        0,
                        core.pageCount() - 1
                )
        );

        slider.setStepSize(1f);

        internalSliderUpdate = true;
        slider.setValue((float) currentPage);
        internalSliderUpdate = false;

        slider.addOnChangeListener(
                (s, value, fromUser) -> {

                    if (!fromUser) return;

                    if (internalSliderUpdate) return;

                    final int target =
                            (int) value;

                    pageText.setText(
                            (target + 1)
                                    + " / "
                                    + core.pageCount()
                    );

                    // debounce
                    if (sliderRunnable != null) {

                        uiHandler.removeCallbacks(
                                sliderRunnable
                        );
                    }

                    sliderRunnable = () -> {

                        if (target == currentPage) {
                            return;
                        }

                        currentPage = target;

                        loadPages();
                    };

                    uiHandler.postDelayed(
                            sliderRunnable,
                            120
                    );
                }
        );
    }

    // ---------------------------------------------------
    // PAGE LOADER
    // ---------------------------------------------------

    private void loadPages() {

        if (core == null) return;

        if (isRendering) return;

        isRendering = true;

        final int targetPage = currentPage;

        renderExecutor.execute(() -> {

            try {

                Bitmap prev = null;
                Bitmap curr;
                Bitmap next = null;

                // PREVIOUS PAGE
                if (targetPage > 0) {

                    prev = core.renderPage(
                            targetPage - 1,
                            1080,
                            1920
                    );
                }

                // CURRENT PAGE
                curr = core.renderPage(
                        targetPage,
                        1080,
                        1920
                );

                // NEXT PAGE
                if (targetPage
                        < core.pageCount() - 1) {

                    next = core.renderPage(
                            targetPage + 1,
                            1080,
                            1920
                    );
                }

                Bitmap finalPrev = prev;
                Bitmap finalCurr = curr;
                Bitmap finalNext = next;

                uiHandler.post(() -> {

                    try {

                        prevBmp = finalPrev;
                        currBmp = finalCurr;
                        nextBmp = finalNext;

                        // CURRENT + NEXT
                        // renderer internally decides direction
                        pageFlipView.setPages(
                                currBmp,
                                nextBmp
                        );

                        pageText.setText(
                                (currentPage + 1)
                                        + " / "
                                        + core.pageCount()
                        );

                        // prevent callback loop
                        internalSliderUpdate = true;

                        slider.setValue(
                                (float) currentPage
                        );

                        internalSliderUpdate = false;

                    } catch (Exception e) {

                        e.printStackTrace();
                    }

                    isRendering = false;
                });

            } catch (Exception e) {

                e.printStackTrace();

                isRendering = false;
            }
        });
    }

    // ---------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {

            uiHandler.removeCallbacksAndMessages(
                    null
            );

        } catch (Exception ignored) {
        }

        try {

            renderExecutor.shutdownNow();

        } catch (Exception ignored) {
        }

        try {

            if (core != null) {
                core.close();
            }

        } catch (Exception ignored) {
        }

        recycleBitmap(prevBmp);
        recycleBitmap(currBmp);
        recycleBitmap(nextBmp);
    }

    // ---------------------------------------------------
    // BITMAP CLEANUP
    // ---------------------------------------------------

    private void recycleBitmap(Bitmap bmp) {

        try {

            if (bmp != null
                    && !bmp.isRecycled()) {

                bmp.recycle();
            }

        } catch (Exception ignored) {
        }
    }
}