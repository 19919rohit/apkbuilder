package neunix.pageflow;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.slider.Slider;

public class PdfActivity extends Activity {

    private static final int PICK = 100;

    private ViewPager2 pager;
    private PdfCore core;
    private PdfPageAdapter adapter;
    private Slider slider;

    private boolean flipping = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_pdf);

        pager = findViewById(R.id.viewPager);
        slider = findViewById(R.id.pageSlider);

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

            try {

                core = new PdfCore();

                core.open(this, uri);

                adapter = new PdfPageAdapter(this, core);

                pager.setAdapter(adapter);

                // only keep nearby pages
                pager.setOffscreenPageLimit(1);

                // smooth ReadEra-like feel
                pager.setPageTransformer((page, position) -> {

                    float abs = Math.abs(position);

                    float scaleX = 1f - (abs * 0.25f);

                    page.setScaleX(scaleX);

                    page.setScaleY(1f - abs * 0.03f);

                    page.setTranslationX(
                            position * -page.getWidth() * 0.18f
                    );

                    page.setAlpha(1f - abs * 0.28f);

                    page.setCameraDistance(20000f);
                });

                setupTouchSystem();

                setupSlider();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupSlider() {

        slider.setValueFrom(0);

        slider.setValueTo(
                Math.max(1, adapter.getItemCount() - 1)
        );

        slider.setStepSize(1);

        slider.addOnChangeListener((s, value, fromUser) -> {

            if (!fromUser) return;

            int page = (int) value;

            pager.setCurrentItem(page, false);
        });

        pager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {

                    @Override
                    public void onPageSelected(int position) {

                        slider.setValue(position);
                    }
                }
        );
    }

    private void setupTouchSystem() {

        pager.getChildAt(0).setOnTouchListener(
                new View.OnTouchListener() {

                    float downX;
                    float downY;

                    @Override
                    public boolean onTouch(View v, MotionEvent e) {

                        switch (e.getAction()) {

                            case MotionEvent.ACTION_DOWN:

                                downX = e.getX();
                                downY = e.getY();

                                return false;

                            case MotionEvent.ACTION_UP:

                                if (pager.getScrollState()
                                        != ViewPager2.SCROLL_STATE_IDLE) {
                                    return false;
                                }

                                float upX = e.getX();
                                float upY = e.getY();

                                float dx = Math.abs(upX - downX);
                                float dy = Math.abs(upY - downY);

                                // if moved enough -> swipe
                                if (dx > 25 || dy > 25) {
                                    return false;
                                }

                                if (flipping) {
                                    return true;
                                }

                                int cur = pager.getCurrentItem();

                                float w = pager.getWidth();

                                flipping = true;

                                // NEXT PAGE
                                if (upX > w * 0.7f) {

                                    if (cur <
                                            adapter.getItemCount() - 1) {

                                        flip();

                                        pager.setCurrentItem(
                                                cur + 1,
                                                true
                                        );
                                    }
                                }

                                // PREVIOUS PAGE
                                else if (upX < w * 0.3f) {

                                    if (cur > 0) {

                                        flip();

                                        pager.setCurrentItem(
                                                cur - 1,
                                                true
                                        );
                                    }
                                }

                                pager.postDelayed(() ->
                                        flipping = false, 220);

                                return true;
                        }

                        return false;
                    }
                }
        );
    }

    private void flip() {

        Animation anim = AnimationUtils.loadAnimation(
                this,
                R.anim.page_flip
        );

        pager.startAnimation(anim);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (core != null) {
            core.close();
        }
    }
}