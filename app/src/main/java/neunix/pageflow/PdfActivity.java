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

public class PdfActivity extends Activity {

    private static final int PICK = 100;

    private PageFlipView pageFlipView;

    private PdfCore core;

    private int currentPage = 0;

    private Bitmap previousBitmap;
    private Bitmap currentBitmap;
    private Bitmap nextBitmap;

    private Slider slider;
    private TextView pageText;

    // slider debounce
    private final Handler sliderHandler =
            new Handler(Looper.getMainLooper());

    private Runnable sliderRunnable;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_pdf);

        pageFlipView =
                findViewById(R.id.pageFlipView);

        slider =
                findViewById(R.id.pageSlider);

        pageText =
                findViewById(R.id.pageText);

        openPicker();
    }

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

        if (requestCode == PICK
                && resultCode == RESULT_OK
                && data != null) {

            Uri uri = data.getData();

            try {

                core = new PdfCore();

                core.open(this, uri);

                setupSlider();

                loadPages();

                pageFlipView.setOnFlipListener(
                        new PageFlipView.OnFlipListener() {

                            @Override
                            public void onNextPage() {

                                if (currentPage <
                                        core.pageCount() - 1) {

                                    currentPage++;

                                    loadPages();
                                }
                            }

                            @Override
                            public void onPreviousPage() {

                                if (currentPage > 0) {

                                    currentPage--;

                                    loadPages();
                                }
                            }
                        }
                );

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }

    private void setupSlider() {

        slider.setValueFrom(0);

        slider.setValueTo(
                Math.max(
                        1,
                        core.pageCount() - 1
                )
        );

        slider.setStepSize(1f);

        slider.addOnChangeListener(
                (s, value, fromUser) -> {

                    if (!fromUser) {
                        return;
                    }

                    int target =
                            (int) value;

                    pageText.setText(
                            (target + 1)
                                    + " / "
                                    + core.pageCount()
                    );

                    // debounce
                    if (sliderRunnable != null) {

                        sliderHandler.removeCallbacks(
                                sliderRunnable
                        );
                    }

                    sliderRunnable = () -> {

                        currentPage = target;

                        loadPages();
                    };

                    sliderHandler.postDelayed(
                            sliderRunnable,
                            120
                    );
                }
        );
    }

    private void loadPages() {

        try {

            // PREVIOUS
            if (currentPage > 0) {

                previousBitmap =
                        core.renderPage(
                                currentPage - 1,
                                1080,
                                1920
                        );

            } else {

                previousBitmap = null;
            }

            // CURRENT
            currentBitmap =
                    core.renderPage(
                            currentPage,
                            1080,
                            1920
                    );

            // NEXT
            if (currentPage
                    < core.pageCount() - 1) {

                nextBitmap =
                        core.renderPage(
                                currentPage + 1,
                                1080,
                                1920
                        );

            } else {

                nextBitmap = null;
            }

            pageFlipView.setBitmaps(
                    previousBitmap,
                    currentBitmap,
                    nextBitmap
            );

            slider.setValue(currentPage);

            pageText.setText(
                    (currentPage + 1)
                            + " / "
                            + core.pageCount()
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        sliderHandler.removeCallbacksAndMessages(null);

        if (core != null) {
            core.close();
        }
    }
}