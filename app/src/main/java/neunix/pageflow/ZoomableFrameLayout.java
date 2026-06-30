package neunix.pageflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;

public class ZoomableFrameLayout extends FrameLayout {

    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 5.0f;

    private final Matrix mMatrix  = new Matrix();
    private ScaleGestureDetector mScaleDetector;

    private float mScaleFactor   = 1f;
    private float mTransX        = 0f;
    private float mTransY        = 0f;
    private float mLastTouchX    = 0f;
    private float mLastTouchY    = 0f;
    private boolean mIsScaling   = false;
    private int mActivePointer   = MotionEvent.INVALID_POINTER_ID;

    public ZoomableFrameLayout(Context c) { super(c); init(c); }
    public ZoomableFrameLayout(Context c, AttributeSet a) { super(c, a); init(c); }
    public ZoomableFrameLayout(Context c, AttributeSet a, int d) { super(c, a, d); init(c); }

    private void init(Context c) {
        mScaleDetector = new ScaleGestureDetector(c, new ScaleListener());
        setWillNotDraw(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Intercept multi-touch always; intercept single touch only when zoomed in
        return ev.getPointerCount() > 1 || mScaleFactor > 1.02f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mScaleDetector.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchX    = ev.getX();
                mLastTouchY    = ev.getY();
                mActivePointer = ev.getPointerId(0);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                mIsScaling = true;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mIsScaling && mScaleFactor > 1.02f) {
                    int idx = ev.findPointerIndex(mActivePointer);
                    if (idx >= 0) {
                        mTransX += ev.getX(idx) - mLastTouchX;
                        mTransY += ev.getY(idx) - mLastTouchY;
                        clampTranslation();
                        applyMatrix();
                        mLastTouchX = ev.getX(idx);
                        mLastTouchY = ev.getY(idx);
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                int upIdx = ev.getActionIndex();
                if (ev.getPointerId(upIdx) == mActivePointer) {
                    int newIdx     = upIdx == 0 ? 1 : 0;
                    mLastTouchX    = ev.getX(newIdx);
                    mLastTouchY    = ev.getY(newIdx);
                    mActivePointer = ev.getPointerId(newIdx);
                }
                mIsScaling = false;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointer = MotionEvent.INVALID_POINTER_ID;
                mIsScaling     = false;
                break;
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector d) {
            float prev  = mScaleFactor;
            mScaleFactor = Math.max(MIN_ZOOM, Math.min(mScaleFactor * d.getScaleFactor(), MAX_ZOOM));
            float ratio  = mScaleFactor / prev;

            // Zoom towards pinch focal point
            float fx = d.getFocusX();
            float fy = d.getFocusY();
            mTransX = fx - ratio * (fx - mTransX);
            mTransY = fy - ratio * (fy - mTransY);

            if (mScaleFactor <= 1.02f) { mScaleFactor = 1f; mTransX = 0; mTransY = 0; }
            clampTranslation();
            applyMatrix();
            return true;
        }
    }

    private void clampTranslation() {
        float maxX = Math.max(0, (mScaleFactor - 1f) * getWidth()  / 2f);
        float maxY = Math.max(0, (mScaleFactor - 1f) * getHeight() / 2f);
        mTransX = Math.max(-maxX, Math.min(mTransX, maxX));
        mTransY = Math.max(-maxY, Math.min(mTransY, maxY));
    }

    private void applyMatrix() {
        mMatrix.reset();
        mMatrix.postScale(mScaleFactor, mScaleFactor, getWidth() / 2f, getHeight() / 2f);
        mMatrix.postTranslate(mTransX, mTransY);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.concat(mMatrix);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    public void resetZoom() {
        mScaleFactor = 1f; mTransX = 0f; mTransY = 0f;
        mMatrix.reset(); invalidate();
    }

    public boolean isZoomed() { return mScaleFactor > 1.02f; }
}