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

    private Slider slider;
    private TextView pageText;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();

    private Runnable sliderRunnable;

    // prevent race conditions
    private volatile boolean isRendering = false;

    // cached bitmaps (important for GL stability)
    private Bitmap prevBmp, currBmp, nextBmp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_pdf);

        pageFlipView = findViewById(R.id.pageFlipGL);
        slider = findViewById(R.id.pageSlider);
        pageText = findViewById(R.id.pageText);

        openPicker();
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/pdf");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK);
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);

        if (r == PICK && c == RESULT_OK && d != null) {

            Uri uri = d.getData();

            core = new PdfCore();
            core.open(this, uri);

            setupSlider();
            loadPages(currentPage);

            pageFlipView.setOnFlipCompleteListener(direction -> {

                if (direction > 0 && currentPage < core.pageCount() - 1) {
                    currentPage++;
                } else if (direction < 0 && currentPage > 0) {
                    currentPage--;
                }

                loadPages(currentPage);
            });
        }
    }

    // ---------------- SLIDER ----------------

    private void setupSlider() {

        slider.setValueFrom(0f);
        slider.setValueTo(Math.max(0, core.pageCount() - 1));
        slider.setStepSize(1f);

        slider.setValue(currentPage);

        slider.addOnChangeListener((s, value, fromUser) -> {

            if (!fromUser) return;

            int target = (int) value;

            pageText.setText((target + 1) + " / " + core.pageCount());

            if (sliderRunnable != null) {
                uiHandler.removeCallbacks(sliderRunnable);
            }

            sliderRunnable = () -> loadPages(target);

            uiHandler.postDelayed(sliderRunnable, 120);
        });
    }

    // ---------------- CORE LOADER ----------------

    private void loadPages(int targetPage) {

        if (core == null || isRendering) return;

        isRendering = true;

        renderExecutor.execute(() -> {

            try {

                int count = core.pageCount();

                Bitmap prev = (targetPage > 0)
                        ? core.renderPage(targetPage - 1, 1080, 1920)
                        : null;

                Bitmap curr = core.renderPage(targetPage, 1080, 1920);

                Bitmap next = (targetPage < count - 1)
                        ? core.renderPage(targetPage + 1, 1080, 1920)
                        : null;

                uiHandler.post(() -> {

                    currentPage = targetPage;

                    prevBmp = prev;
                    currBmp = curr;
                    nextBmp = next;

                    // GL UPDATE (ONLY SAFE PLACE)
                    pageFlipView.setPages(currBmp, nextBmp);

                    pageText.setText((currentPage + 1) + " / " + count);

                    // prevent slider loop
                    slider.setValue((float) currentPage);

                    isRendering = false;
                });

            } catch (Exception e) {
                isRendering = false;
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        uiHandler.removeCallbacksAndMessages(null);
        renderExecutor.shutdownNow();

        if (core != null) core.close();
    }
}