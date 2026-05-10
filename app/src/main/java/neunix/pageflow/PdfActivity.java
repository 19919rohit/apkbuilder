package neunix.pageflow;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.viewpager2.widget.ViewPager2;

public class PdfActivity extends Activity {

    private static final int PICK = 100;

    private ViewPager2 pager;
    private PdfCore core;
    private PdfPageAdapter adapter;

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

                pager.setOffscreenPageLimit(1);

                pager.setPageTransformer((page, position) -> {

                    float abs = Math.abs(position);

                    page.setAlpha(1 - abs * 0.25f);
                    page.setScaleY(1 - abs * 0.05f);
                    page.setRotationY(position * -15f);

                    page.setCameraDistance(20000f);
                });

                setupTap();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupTap() {

        pager.getChildAt(0).setOnTouchListener((v, e) -> {

            if (e.getAction() == MotionEvent.ACTION_UP) {

                float x = e.getX();
                float w = pager.getWidth();

                int cur = pager.getCurrentItem();

                if (x > w * 0.7f && cur < adapter.getItemCount() - 1) {

                    playFlip();
                    pager.setCurrentItem(cur + 1, true);

                } else if (x < w * 0.3f && cur > 0) {

                    playFlip();
                    pager.setCurrentItem(cur - 1, true);
                }
            }

            return false;
        });
    }

    private void playFlip() {

        Animation anim = AnimationUtils.loadAnimation(
                this,
                R.anim.page_flip
        );

        pager.startAnimation(anim);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (core != null) core.close();
    }
}