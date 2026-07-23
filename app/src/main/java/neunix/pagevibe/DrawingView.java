package neunix.pagevibe;

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

    public enum Tool { PEN, HIGHLIGHTER }

    private static final float PEN_OPACITY         = 1.0f;
    private static final float HIGHLIGHTER_OPACITY = 0.40f;

    private static final float PEN_DEFAULT_WIDTH         = 6f;
    private static final float PEN_THIN_WIDTH            = 4f;
    private static final float PEN_THICK_WIDTH           = 14f;
    private static final float HIGHLIGHTER_DEFAULT_WIDTH = 28f;

    public static class Stroke {
        public final Path  path;
        public final int   color;
        public final float width;
        public final float opacity;
        public final Tool  tool;

        public Stroke(Path path, int color, float width, float opacity, Tool tool) {
            this.path    = path;
            this.color   = color;
            this.width   = width;
            this.opacity = opacity;
            this.tool    = tool;
        }
    }

    private static Paint makePaint(Stroke s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s.width);
        p.setColor(s.color);
        p.setAlpha(Math.round(s.opacity * 255f));
        return p;
    }

    private final List<Stroke> mStrokes     = new ArrayList<>();
    private final Paint        mBitmapPaint = new Paint(Paint.DITHER_FLAG);

    private Path  mCurrentPath;
    private float mLastX, mLastY;

    private static final float TOUCH_TOLERANCE = 2f;

    private Bitmap mPenBitmap;
    private Canvas mPenCanvas;
    private Bitmap mHighlightBitmap;
    private Canvas mHighlightCanvas;

    private Tool  mTool  = Tool.PEN;
    private int   mColor = Color.RED;
    private float mWidth = PEN_DEFAULT_WIDTH;

    private boolean mEnabled = false;

    private GalleryZoomView mZoomHost;

    public DrawingView(Context context) { super(context); }
    public DrawingView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void attachZoomHost(GalleryZoomView host) {
        mZoomHost = host;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w > 0 && h > 0) {
            Bitmap newPen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas newPenC = new Canvas(newPen);
            if (mPenBitmap != null) { newPenC.drawBitmap(mPenBitmap, 0, 0, mBitmapPaint); mPenBitmap.recycle(); }
            mPenBitmap = newPen; mPenCanvas = newPenC;

            Bitmap newHL = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas newHLC = new Canvas(newHL);
            if (mHighlightBitmap != null) { newHLC.drawBitmap(mHighlightBitmap, 0, 0, mBitmapPaint); mHighlightBitmap.recycle(); }
            mHighlightBitmap = newHL; mHighlightCanvas = newHLC;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mHighlightBitmap != null) {
            canvas.drawBitmap(mHighlightBitmap, 0, 0, mBitmapPaint);
        }
        if (mPenBitmap != null) {
            canvas.drawBitmap(mPenBitmap, 0, 0, mBitmapPaint);
        }
        if (mCurrentPath != null && mEnabled) {
            float liveOpacity = (mTool == Tool.HIGHLIGHTER) ? HIGHLIGHTER_OPACITY : PEN_OPACITY;
            Paint livePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            livePaint.setStyle(Paint.Style.STROKE);
            livePaint.setStrokeJoin(Paint.Join.ROUND);
            livePaint.setStrokeCap(Paint.Cap.ROUND);
            livePaint.setStrokeWidth(mWidth);
            livePaint.setColor(mColor);
            livePaint.setAlpha(Math.round(liveOpacity * 255f));
            canvas.drawPath(mCurrentPath, livePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mEnabled) return false;

        float rawX = event.getX(), rawY = event.getY();
        float x = rawX, y = rawY;
        if (mZoomHost != null) {
            android.graphics.PointF p = mZoomHost.screenToContent(rawX, rawY);
            x = p.x; y = p.y;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentPath = new Path();
                mCurrentPath.moveTo(x, y);
                mLastX = x;
                mLastY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mCurrentPath == null) return false;

                float dx = Math.abs(x - mLastX);
                float dy = Math.abs(y - mLastY);
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    float midX = (x + mLastX) / 2f;
                    float midY = (y + mLastY) / 2f;
                    mCurrentPath.quadTo(mLastX, mLastY, midX, midY);
                    mLastX = x;
                    mLastY = y;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (mCurrentPath == null) return false;
                mCurrentPath.lineTo(mLastX, mLastY);

                float opacity = (mTool == Tool.HIGHLIGHTER) ? HIGHLIGHTER_OPACITY : PEN_OPACITY;
                Stroke s = new Stroke(mCurrentPath, mColor, mWidth, opacity, mTool);
                commitStrokeToBitmap(s);
                mStrokes.add(s);
                mCurrentPath = null;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                // A 2nd finger arriving mid-stroke (e.g. to pinch-zoom)
                // causes the framework to cancel our gesture. Without
                // this, mCurrentPath was left dangling — invisible on
                // screen (onDraw only draws it while mEnabled, but the
                // stale Path object stuck around) and never committed or
                // cleared, which could resurface as a stray mark on the
                // next redraw. Discard it cleanly instead.
                mCurrentPath = null;
                invalidate();
                return true;
        }
        return false;
    }

    private void commitStrokeToBitmap(Stroke s) {
        Paint p = makePaint(s);
        if (s.tool == Tool.HIGHLIGHTER && mHighlightCanvas != null) {
            mHighlightCanvas.drawPath(s.path, p);
        } else if (mPenCanvas != null) {
            mPenCanvas.drawPath(s.path, p);
        }
    }

    public void setDrawingEnabled(boolean enabled) {
        mEnabled = enabled;
        setClickable(enabled);
        setFocusable(enabled);
    }
    public boolean isDrawingEnabled() { return mEnabled; }

    public void setTool(Tool tool) {
        mTool  = tool;
        mWidth = (tool == Tool.HIGHLIGHTER) ? HIGHLIGHTER_DEFAULT_WIDTH : PEN_DEFAULT_WIDTH;
    }
    public Tool getTool() { return mTool; }

    public void setPenColor(int color) { mColor = color; }
    public int  getPenColor()          { return mColor; }

    public void setPenWidth(float width) { mWidth = width; }
    public float getPenWidth()           { return mWidth; }

    public void setThinWidth()  { mWidth = PEN_THIN_WIDTH; }
    public void setThickWidth() { mWidth = PEN_THICK_WIDTH; }

    public void undoLastStroke() {
        if (mStrokes.isEmpty()) return;
        mStrokes.remove(mStrokes.size() - 1);
        redrawBitmaps();
    }

    public void clearAll() {
        mStrokes.clear();
        clearBitmaps();
        invalidate();
    }

    public boolean hasStrokes() { return !mStrokes.isEmpty(); }

    public List<Stroke> detachStrokes() {
        List<Stroke> saved = new ArrayList<>(mStrokes);
        mStrokes.clear();
        clearBitmaps();
        mCurrentPath = null;
        invalidate();
        return saved;
    }

    public void attachStrokes(List<Stroke> strokes) {
        mStrokes.clear();
        if (strokes != null) mStrokes.addAll(strokes);
        redrawBitmaps();
    }

    private void clearBitmaps() {
        if (mPenCanvas != null)
            mPenCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        if (mHighlightCanvas != null)
            mHighlightCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    private void redrawBitmaps() {
        clearBitmaps();
        for (Stroke s : mStrokes) {
            commitStrokeToBitmap(s);
        }
        invalidate();
    }
}