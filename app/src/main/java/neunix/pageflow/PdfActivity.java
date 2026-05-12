package neunix.pageflow;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.core.view.GestureDetectorCompat;
import androidx.viewpager2.widget.ViewPager2;

public class PdfActivity extends Activity {

    private static final int PICK = 100;

    private ViewPager2 pager;
    private PdfCore core;
    private PdfPageAdapter adapter;

    private GestureDetectorCompat gestureDetector;

    // prevents rapid double tap chaos
    private boolean flipping = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_pdf);

        pager = findViewById(R.id.viewPager);

        openPicker();
    }

    private void openPicker() {

        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/pdf");

        startActivityForResult(i, PICK);
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);

        if (r == PICK && c == RESULT_OK) {

            Uri uri = d.getData();

            try {

                core = new PdfCore();
                core.open(this, uri);

                adapter = new PdfPageAdapter(this, core);

                pager.setAdapter(adapter);

                // keep memory low
                pager.setOffscreenPageLimit(1);

                // smooth book feeling
                pager.setPageTransformer((page, position) -> {

                    float abs = Math.abs(position);

                    page.setAlpha(1f - abs * 0.25f);

                    page.setScaleY(1f - abs * 0.05f);

                    page.setRotationY(position * -12f);

                    page.setTranslationX(
                            position * -page.getWidth() * 0.06f
                    );

                    page.setCameraDistance(20000f);
                });

                setupTouchSystem();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupTouchSystem() {

        gestureDetector = new GestureDetectorCompat(
                this,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {

                        // ignore during active scrolling
                        if (pager.getScrollState()
                                != ViewPager2.SCROLL_STATE_IDLE) {
                            return false;
                        }

                        // prevent tap spam
                        if (flipping) {
                            return true;
                        }

                        float x = e.getX();
                        float w = pager.getWidth();

                        int cur = pager.getCurrentItem();

                        // RIGHT = NEXT PAGE
                        if (x > w * 0.7f) {

                            if (cur < adapter.getItemCount() - 1) {

                                flipping = true;

                                flip();

                                pager.setCurrentItem(
                                        cur + 1,
                                        true
                                );

                                pager.postDelayed(() ->
                                        flipping = false, 250);
                            }
                        }

                        // LEFT = PREVIOUS PAGE
                        else if (x < w * 0.3f) {

                            if (cur > 0) {

                                flipping = true;

                                flip();

                                pager.setCurrentItem(
                                        cur - 1,
                                        true
                                );

                                pager.postDelayed(() ->
                                        flipping = false, 250);
                            }
                        }

                        return false;
                    }
                }
        );

        pager.getChildAt(0).setOnTouchListener((v, e) -> {

            // gesture detector handles taps
            gestureDetector.onTouchEvent(e);

            // VERY IMPORTANT:
            // allow ViewPager to handle swipes normally
            return false;
        });
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