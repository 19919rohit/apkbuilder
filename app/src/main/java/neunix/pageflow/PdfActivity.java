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

    private PageFlipGLView pageFlipView;
    private PdfCore core;

    private int currentPage = 0;

    private Bitmap prevBmp, currBmp, nextBmp;

    private Slider slider;
    private TextView pageText;

    // SINGLE handler system (fix race + lag)
    private final Handler sliderHandler =
            new Handler(Looper.getMainLooper());

    private Runnable sliderRunnable;

    // prevent GL + PDF race condition
    private boolean isRendering = false;

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
            loadPages();

            pageFlipView.setOnFlipCompleteListener(direction -> {

                if (direction > 0 && currentPage < core.pageCount() - 1) {
                    currentPage++;
                } else if (direction < 0 && currentPage > 0) {
                    currentPage--;
                }

                loadPages();
            });
        }
    }

    private void setupSlider() {

        if (core == null) return;

        slider.setValueFrom(0f);
        slider.setValueTo(Math.max(0, core.pageCount() - 1));
        slider.setStepSize(1f);
        slider.setValue(currentPage);

        slider.addOnChangeListener((s, value, fromUser) -> {

            if (!fromUser) return;

            int target = (int) value;

            pageText.setText((target + 1) + " / " + core.pageCount());

            if (sliderRunnable != null) {
                sliderHandler.removeCallbacks(sliderRunnable);
            }

            sliderRunnable = () -> {

                if (target == currentPage) return;

                currentPage = target;
                loadPages();
            };

            sliderHandler.postDelayed(sliderRunnable, 120);
        });
    }

    private void loadPages() {

        if (core == null) return;

        if (isRendering) return;
        isRendering = true;

        try {

            // PREVIOUS PAGE
            prevBmp = (currentPage > 0)
                    ? core.renderPage(currentPage - 1, 1080, 1920)
                    : null;

            // CURRENT PAGE
            currBmp = core.renderPage(currentPage, 1080, 1920);

            // NEXT PAGE
            nextBmp = (currentPage < core.pageCount() - 1)
                    ? core.renderPage(currentPage + 1, 1080, 1920)
                    : null;

            // GL CONNECT (IMPORTANT)
            pageFlipView.setPages(prevBmp, currBmp, nextBmp);

            // UI UPDATE
            pageText.setText((currentPage + 1) + " / " + core.pageCount());

            // IMPORTANT: prevent slider loop trigger
            slider.setValue(currentPage, false);

        } catch (Exception e) {
            e.printStackTrace();
        }

        isRendering = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        sliderHandler.removeCallbacksAndMessages(null);

        if (core != null) core.close();
    }
}