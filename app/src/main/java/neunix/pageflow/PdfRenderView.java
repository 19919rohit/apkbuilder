package neunix.pageflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class PdfRenderView extends View {

    private Bitmap currentBitmap;
    private Paint paint = new Paint();

    private PdfCore pdfCore;
    private int pageIndex = 0;

    private GestureDetector gestureDetector;

    public PdfRenderView(Context context) {
        super(context);

        pdfCore = new PdfCore(context);

        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velocityX, float velocityY) {

                        if (velocityX < 0) {
                            nextPage();
                        } else {
                            prevPage();
                        }
                        return true;
                    }
                });
    }

    public void loadPdf(Uri uri) {
        pdfCore.open(uri);
        renderPage(0);
    }

    private void renderPage(int index) {
        pageIndex = index;
        currentBitmap = pdfCore.renderPage(index, getWidth(), getHeight());
        invalidate();
    }

    private void nextPage() {
        if (pdfCore.hasPage(pageIndex + 1)) {
            renderPage(pageIndex + 1);
        }
    }

    private void prevPage() {
        if (pageIndex > 0) {
            renderPage(pageIndex - 1);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentBitmap != null) {
            canvas.drawBitmap(currentBitmap, 0, 0, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }
}