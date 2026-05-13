package neunix.pageflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class PageFlipView extends View {

    private Bitmap currentPage;
    private Bitmap nextPage;

    // 0 -> current visible
    // 1 -> next visible
    private float progress = 0f;

    private final Paint bitmapPaint =
            new Paint(Paint.FILTER_BITMAP_FLAG);

    private final Paint shadowPaint =
            new Paint();

    private float downX;

    private boolean dragging = false;

    private OnFlipListener listener;

    public interface OnFlipListener {
        void onNextPage();
        void onPreviousPage();
    }

    public PageFlipView(Context c) {
        super(c);
        init();
    }

    public PageFlipView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {

        setLayerType(
                LAYER_TYPE_HARDWARE,
                null
        );
    }

    public void setOnFlipListener(OnFlipListener l) {
        listener = l;
    }

    public void setBitmaps(Bitmap current, Bitmap next) {

        currentPage = current;
        nextPage = next;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentPage == null) {
            return;
        }

        int w = getWidth();
        int h = getHeight();

        // draw next page underneath
        if (nextPage != null) {

            canvas.drawBitmap(
                    nextPage,
                    null,
                    new Rect(0, 0, w, h),
                    bitmapPaint
            );
        } else {

            canvas.drawColor(Color.BLACK);
        }

        // current visible width
        float currentVisible =
                w * (1f - progress);

        // compressed fold effect
        float compressed =
                currentVisible * (1f - progress * 0.18f);

        canvas.save();

        canvas.clipRect(
                0,
                0,
                compressed,
                h
        );

        canvas.drawBitmap(
                currentPage,
                null,
                new Rect(0, 0, w, h),
                bitmapPaint
        );

        canvas.restore();

        // shadow
        if (progress > 0f && progress < 1f) {

            float shadowX = compressed;

            LinearGradient gradient =
                    new LinearGradient(
                            shadowX - 70,
                            0,
                            shadowX,
                            0,
                            new int[]{
                                    0x00000000,
                                    0x55000000,
                                    0xAA000000
                            },
                            null,
                            Shader.TileMode.CLAMP
                    );

            shadowPaint.setShader(gradient);

            canvas.drawRect(
                    shadowX - 70,
                    0,
                    shadowX,
                    h,
                    shadowPaint
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {

        switch (e.getAction()) {

            case MotionEvent.ACTION_DOWN:

                downX = e.getX();

                dragging = true;

                return true;

            case MotionEvent.ACTION_MOVE:

                if (!dragging) {
                    return true;
                }

                float dx = downX - e.getX();

                progress =
                        Math.max(
                                0f,
                                Math.min(
                                        1f,
                                        dx / getWidth()
                                )
                        );

                invalidate();

                return true;

            case MotionEvent.ACTION_UP:

            case MotionEvent.ACTION_CANCEL:

                dragging = false;

                if (progress > 0.5f) {

                    animateFlip(true);

                } else {

                    animateFlip(false);
                }

                return true;
        }

        return super.onTouchEvent(e);
    }

    private void animateFlip(boolean complete) {

        float target = complete ? 1f : 0f;

        ValueAnimator animator =
                ValueAnimator.ofFloat(
                        progress,
                        target
                );

        animator.setDuration(220);

        animator.addUpdateListener(a -> {

            progress = (float) a.getAnimatedValue();

            invalidate();
        });

        animator.start();

        if (complete && listener != null) {

            postDelayed(() -> {

                listener.onNextPage();

                progress = 0f;

                invalidate();

            }, 220);
        }
    }
}