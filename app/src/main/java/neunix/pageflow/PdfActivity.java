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

    private static final int PICK_PDF = 100;

    private PageFlipGLView pageFlipView;

    private PdfCore core;

    private Slider slider;

    private TextView pageText;

    private int currentPage = 0;

    private Bitmap currentBitmap;
    private Bitmap nextBitmap;

    private final Handler uiHandler =
            new Handler(Looper.getMainLooper());

    private final ExecutorService renderExecutor =
            Executors.newSingleThreadExecutor();

    private Runnable sliderRunnable;

    private volatile boolean rendering = false;

    private boolean internalSliderUpdate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pdf);

        pageFlipView =
                findViewById(R.id.pageFlipGL);

        slider =
                findViewById(R.id.pageSlider);

        pageText =
                findViewById(R.id.pageText);

        openPdfPicker();
    }

    // ----------------------------------------------------
    // PDF PICKER
    // ----------------------------------------------------

    private void openPdfPicker() {

        Intent i =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        i.setType("application/pdf");

        i.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(i, PICK_PDF);
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

        if (requestCode != PICK_PDF) return;

        if (resultCode != RESULT_OK) return;

        if (data == null) return;

        try {

            Uri uri = data.getData();

            if (uri == null) return;

            core = new PdfCore();

            core.open(this, uri);

            setupSlider();

            setupFlipListener();

            loadPage();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    // ----------------------------------------------------
    // PAGE FLIP
    // ----------------------------------------------------

    private void setupFlipListener() {

        pageFlipView.setFlipListener(direction -> {

            if (core == null) return;

            if (rendering) return;

            // NEXT
            if (direction > 0) {

                if (currentPage
                        < core.pageCount() - 1) {

                    currentPage++;
                }
            }

            // PREVIOUS
            else {

                if (currentPage > 0) {

                    currentPage--;
                }
            }

            loadPage();
        });
    }

    // ----------------------------------------------------
    // SLIDER
    // ----------------------------------------------------

    private void setupSlider() {

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

                    int target = (int) value;

                    pageText.setText(
                            (target + 1)
                                    + " / "
                                    + core.pageCount()
                    );

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

                        loadPage();
                    };

                    uiHandler.postDelayed(
                            sliderRunnable,
                            100
                    );
                }
        );
    }

    // ----------------------------------------------------
    // PAGE LOADING
    // ----------------------------------------------------

    private void loadPage() {

        if (core == null) return;

        if (rendering) return;

        rendering = true;

        final int targetPage = currentPage;

        renderExecutor.execute(() -> {

            try {

                Bitmap current =
                        core.renderPage(
                                targetPage,
                                1080,
                                1920
                        );

                Bitmap next = null;

                if (targetPage
                        < core.pageCount() - 1) {

                    next =
                            core.renderPage(
                                    targetPage + 1,
                                    1080,
                                    1920
                            );
                }

                Bitmap finalCurrent = current;

                Bitmap finalNext = next;

                uiHandler.post(() -> {

                    try {

                        recycleBitmap(currentBitmap);

                        recycleBitmap(nextBitmap);

                        currentBitmap =
                                finalCurrent;

                        nextBitmap =
                                finalNext;

                        pageFlipView.setPages(
                                currentBitmap,
                                nextBitmap
                        );

                        pageText.setText(
                                (currentPage + 1)
                                        + " / "
                                        + core.pageCount()
                        );

                        internalSliderUpdate = true;

                        slider.setValue(
                                (float) currentPage
                        );

                        internalSliderUpdate = false;

                    } catch (Exception e) {

                        e.printStackTrace();
                    }

                    rendering = false;
                });

            } catch (Exception e) {

                e.printStackTrace();

                rendering = false;
            }
        });
    }

    // ----------------------------------------------------
    // CLEANUP
    // ----------------------------------------------------

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

        recycleBitmap(currentBitmap);

        recycleBitmap(nextBitmap);
    }

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