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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable debounce;

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

        // cancel previous debounce
        if (sliderRunnable != null) {
            sliderHandler.removeCallbacks(sliderRunnable);
        }

        sliderRunnable = () -> {

            // prevent redundant reload
            if (target == currentPage) return;

            currentPage = target;
            loadPages();
        };

        sliderHandler.postDelayed(sliderRunnable, 120);
    });
}

    private void loadPages() {

        try {

            prevBmp = (currentPage > 0)
                    ? core.renderPage(currentPage - 1, 1080, 1920)
                    : null;

            currBmp = core.renderPage(currentPage, 1080, 1920);

            nextBmp = (currentPage < core.pageCount() - 1)
                    ? core.renderPage(currentPage + 1, 1080, 1920)
                    : null;

            // 🔥 ONLY GL CONNECT LINE
            pageFlipView.setPages(prevBmp, currBmp, nextBmp);

            pageText.setText((currentPage + 1) + " / " + core.pageCount());

            slider.setValue(currentPage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (core != null) core.close();
    }
}