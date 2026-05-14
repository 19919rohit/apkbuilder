package neunix.pageflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class PageFlipView extends View {

    public interface OnFlipListener {
        void onNextPage();
        void onPreviousPage();
    }

    private Bitmap currentPage;
    private Bitmap nextPage;
    private Bitmap previousPage;

    private float progress = 0f;

    private final Paint bitmapPaint =
            new Paint(Paint.FILTER_BITMAP_FLAG);

    private final Paint shadowPaint =
            new Paint();

    private OnFlipListener listener;

    private float downX;
    private float downY;

    private boolean dragging = false;

    private boolean flippingNext = true;

    private boolean animating = false;

    private static final float TAP_DISTANCE = 18f;

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

    public void setBitmaps(
            Bitmap previous,
            Bitmap current,
            Bitmap next
    ) {

        previousPage = previous;
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

        Bitmap revealBitmap =
                flippingNext
                        ? nextPage
                        : previousPage;

        // background page
        if (revealBitmap != null) {

            canvas.drawBitmap(
                    revealBitmap,
                    null,
                    new Rect(0, 0, w, h),
                    bitmapPaint
            );
        }

        float visible;

        if (flippingNext) {

            visible = w * (1f - progress);

            canvas.save();

            canvas.clipRect(
                    0,
                    0,
                    visible,
                    h
            );

        } else {

            visible = w * progress;

            canvas.save();

            canvas.clipRect(
                    visible,
                    0,
                    w,
                    h
            );
        }

        canvas.drawBitmap(
                currentPage,
                null,
                new Rect(0, 0, w, h),
                bitmapPaint
        );

        canvas.restore();

        // shadow
        if (progress > 0f && progress < 1f) {

            float shadowX =
                    flippingNext
                            ? visible
                            : visible + 70;

            LinearGradient gradient;

            if (flippingNext) {

                gradient =
                        new LinearGradient(
                                shadowX - 70,
                                0,
                                shadowX,
                                0,
                                0x00000000,
                                0x99000000,
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

            } else {

                gradient =
                        new LinearGradient(
                                shadowX,
                                0,
                                shadowX + 70,
                                0,
                                0x99000000,
                                0x00000000,
                                Shader.TileMode.CLAMP
                        );

                shadowPaint.setShader(gradient);

                canvas.drawRect(
                        shadowX,
                        0,
                        shadowX + 70,
                        h,
                        shadowPaint
                );
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {

        if (animating) {
            return true;
        }

        switch (e.getAction()) {

            case MotionEvent.ACTION_DOWN:

                downX = e.getX();
                downY = e.getY();

                dragging = false;

                return true;

            case MotionEvent.ACTION_MOVE:

                float dx = e.getX() - downX;
                float dy = e.getY() - downY;

                if (!dragging) {

                    if (Math.abs(dx) > 12f &&
                            Math.abs(dx) > Math.abs(dy)) {

                        dragging = true;

                        flippingNext = dx < 0;
                    }
                }

                if (dragging) {

                    float amount =
                            Math.abs(dx) / getWidth();

                    progress =
                            Math.max(
                                    0f,
                                    Math.min(1f, amount)
                            );

                    invalidate();
                }

                return true;

            case MotionEvent.ACTION_UP:

            case MotionEvent.ACTION_CANCEL:

                if (!dragging) {

                    handleTap(e.getX());

                    return true;
                }

                finishDrag();

                return true;
        }

        return super.onTouchEvent(e);
    }

    private void handleTap(float x) {

        float w = getWidth();

        if (x > w * 0.7f) {

            flippingNext = true;

            animateToNext();

        } else if (x < w * 0.3f) {

            flippingNext = false;

            animateToPrevious();
        }
    }

    private void finishDrag() {

        if (progress > 0.5f) {

            if (flippingNext) {

                animateToNext();

            } else {

                animateToPrevious();
            }

        } else {

            cancelFlip();
        }
    }

    private void animateToNext() {

        if (nextPage == null) {
            cancelFlip();
            return;
        }

        animateProgress(
                progress,
                1f,
                () -> {

                    if (listener != null) {
                        listener.onNextPage();
                    }

                    resetState();
                }
        );
    }

    private void animateToPrevious() {

        if (previousPage == null) {
            cancelFlip();
            return;
        }

        animateProgress(
                progress,
                1f,
                () -> {

                    if (listener != null) {
                        listener.onPreviousPage();
                    }

                    resetState();
                }
        );
    }

    private void cancelFlip() {

        animateProgress(
                progress,
                0f,
                this::resetState
        );
    }

    private void animateProgress(
            float from,
            float to,
            Runnable end
    ) {

        animating = true;

        ValueAnimator animator =
                ValueAnimator.ofFloat(from, to);

        animator.setDuration(220);

        animator.addUpdateListener(a -> {

            progress =
                    (float) a.getAnimatedValue();

            invalidate();
        });

        animator.start();

        animator.addListener(
                new android.animation.AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(
                            android.animation.Animator animation
                    ) {

                        animating = false;

                        end.run();
                    }
                }
        );
    }

    private void resetState() {

        progress = 0f;

        dragging = false;

        invalidate();
    }
}