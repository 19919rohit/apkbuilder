package neunix.pageflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawingView extends View {

    public static class Stroke {
        public final Path  path;
        public final int   color;
        public final float width;
        public Stroke(Path path, int color, float width) {
            this.path = path; this.color = color; this.width = width;
        }
    }

    private final List<Stroke> mStrokes     = new ArrayList<>();
    private final Paint        mPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
    private Path               mCurrentPath;
    private Bitmap             mBitmap;
    private Canvas             mBitmapCanvas;
    private int                mPenColor    = Color.RED;
    private float              mPenWidth    = 6f;
    private boolean            mEnabled     = false;

    /** Owning GalleryZoomView — used to convert raw touch points into content space so strokes stay aligned while zoomed. */
    private GalleryZoomView mZoomHost;

    private float mLastX, mLastY;
    private static final float TOUCH_TOLERANCE = 4f;

    public DrawingView(Context context) { super(context); init(); }
    public DrawingView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** Call once after inflate — links this view to its zoom container for coordinate conversion. */
    public void attachZoomHost(GalleryZoomView host) {
        mZoomHost = host;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w > 0 && h > 0) {
            Bitmap nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas nc = new Canvas(nb);
            if (mBitmap != null) {
                nc.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
                mBitmap.recycle();
            }
            mBitmap = nb;
            mBitmapCanvas = nc;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap != null) canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        if (mCurrentPath != null && mEnabled) {
            mPaint.setColor(mPenColor);
            mPaint.setStrokeWidth(mPenWidth);
            canvas.drawPath(mCurrentPath, mPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mEnabled) return false;

        // Convert raw touch coords (screen space, post-zoom-matrix) into
        // content space so strokes land in the same place on the page
        // regardless of current zoom level. Since this DrawingView is a
        // child of GalleryZoomView, the canvas it draws into is ALREADY
        // transformed by the zoom matrix during dispatchDraw — but raw
        // MotionEvent coordinates are still in pre-transform screen space.
        // We must invert the zoom matrix to map them correctly.
        float rawX = event.getX();
        float rawY = event.getY();
        float x = rawX, y = rawY;
        if (mZoomHost != null) {
            android.graphics.PointF p = mZoomHost.screenToContent(rawX, rawY);
            x = p.x; y = p.y;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentPath = new Path();
                mCurrentPath.moveTo(x, y);
                mLastX = x; mLastY = y;
                invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if (mCurrentPath == null) return false;
                if (Math.abs(x - mLastX) >= TOUCH_TOLERANCE || Math.abs(y - mLastY) >= TOUCH_TOLERANCE) {
                    mCurrentPath.quadTo(mLastX, mLastY, (x + mLastX) / 2f, (y + mLastY) / 2f);
                    mLastX = x; mLastY = y;
                }
                invalidate(); return true;
            case MotionEvent.ACTION_UP:
                if (mCurrentPath == null) return false;
                mCurrentPath.lineTo(mLastX, mLastY);
                mPaint.setColor(mPenColor);
                mPaint.setStrokeWidth(mPenWidth);
                if (mBitmapCanvas != null) mBitmapCanvas.drawPath(mCurrentPath, mPaint);
                mStrokes.add(new Stroke(mCurrentPath, mPenColor, mPenWidth));
                mCurrentPath = null;
                invalidate(); return true;
        }
        return false;
    }

    // =========================================================
    // PUBLIC API
    // =========================================================
    public void setDrawingEnabled(boolean enabled) {
        mEnabled = enabled;
        setClickable(enabled);
        setFocusable(enabled);
    }
    public boolean isDrawingEnabled() { return mEnabled; }
    public void setPenColor(int color) { mPenColor = color; }
    public void setPenWidth(float width) { mPenWidth = width; }

    public void undoLastStroke() {
        if (mStrokes.isEmpty()) return;
        mStrokes.remove(mStrokes.size() - 1);
        redrawBitmap();
    }

    public void clearAll() {
        mStrokes.clear();
        if (mBitmapCanvas != null) mBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        invalidate();
    }

    public boolean hasStrokes() { return !mStrokes.isEmpty(); }

    public List<Stroke> detachStrokes() {
        List<Stroke> saved = new ArrayList<>(mStrokes);
        mStrokes.clear();
        if (mBitmapCanvas != null) mBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        mCurrentPath = null;
        invalidate();
        return saved;
    }

    public void attachStrokes(List<Stroke> strokes) {
        mStrokes.clear();
        if (strokes != null) mStrokes.addAll(strokes);
        redrawBitmap();
    }

    private void redrawBitmap() {
        if (mBitmap == null) return;
        mBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        for (Stroke s : mStrokes) {
            mPaint.setColor(s.color);
            mPaint.setStrokeWidth(s.width);
            mBitmapCanvas.drawPath(s.path, mPaint);
        }
        invalidate();
    }
}