package neunix.pageflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.OverScroller;

/**
 * Gallery-style zoom container: pinch-to-zoom with focal point, double-tap
 * to zoom in/out, fling-to-pan with deceleration, and bounds clamping that
 * snaps back smoothly instead of hard-stopping at the edge.
 *
 * Exposes the live transform matrix so overlay views (DrawingView) can
 * convert between screen space and content space, allowing drawing while
 * zoomed in without misaligned strokes.
 */
public class GalleryZoomView extends FrameLayout {

    public interface OnZoomChangeListener {
        void onZoomChanged(float scale, float transX, float transY);
    }

    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 5.0f;
    private static final float DOUBLE_TAP_ZOOM = 2.5f;

    private final Matrix mMatrix = new Matrix();
    private final OverScroller mScroller;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector      mGestureDetector;

    private float mScaleFactor = 1f;
    private float mTransX = 0f;
    private float mTransY = 0f;

    private float mLastTouchX, mLastTouchY;
    private int   mActivePointer = MotionEvent.INVALID_POINTER_ID;
    private boolean mIsScaling = false;
    private boolean mDrawPassThrough = false; // when true, single-touch always passes to children (draw mode)

    private OnZoomChangeListener mZoomListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public GalleryZoomView(Context c) { super(c); mScroller = new OverScroller(c, new DecelerateInterpolator()); init(c); }
    public GalleryZoomView(Context c, AttributeSet a) { super(c, a); mScroller = new OverScroller(c, new DecelerateInterpolator()); init(c); }
    public GalleryZoomView(Context c, AttributeSet a, int d) { super(c, a, d); mScroller = new OverScroller(c, new DecelerateInterpolator()); init(c); }

    private void init(Context c) {
        mScaleDetector = new ScaleGestureDetector(c, new ScaleListener());
        mGestureDetector = new GestureDetector(c, new GestureListener());
        setWillNotDraw(false);
    }

    public void setOnZoomChangeListener(OnZoomChangeListener l) { mZoomListener = l; }

    /** When true, this view never intercepts single-finger touches — used while draw mode is active. */
    public void setDrawPassThrough(boolean passThrough) {
        mDrawPassThrough = passThrough;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mDrawPassThrough && ev.getPointerCount() == 1) {
            // Let DrawingView handle single-finger touches; we still want
            // two-finger pinch to zoom even while drawing is active.
            return false;
        }
        return ev.getPointerCount() > 1 || mScaleFactor > 1.01f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mScaleDetector.onTouchEvent(ev);
        mGestureDetector.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mScroller.forceFinished(true);
                mLastTouchX = ev.getX();
                mLastTouchY = ev.getY();
                mActivePointer = ev.getPointerId(0);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                mIsScaling = true;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mIsScaling && mScaleFactor > 1.01f) {
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
                    int newIdx = upIdx == 0 ? 1 : 0;
                    mLastTouchX = ev.getX(newIdx);
                    mLastTouchY = ev.getY(newIdx);
                    mActivePointer = ev.getPointerId(newIdx);
                }
                mIsScaling = false;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointer = MotionEvent.INVALID_POINTER_ID;
                mIsScaling = false;
                break;
        }
        return true;
    }

    // =========================================================
    // PINCH ZOOM
    // =========================================================
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector d) {
            float prev = mScaleFactor;
            mScaleFactor = clamp(mScaleFactor * d.getScaleFactor(), MIN_ZOOM, MAX_ZOOM);
            float ratio = mScaleFactor / prev;

            float fx = d.getFocusX();
            float fy = d.getFocusY();
            mTransX = fx - ratio * (fx - mTransX);
            mTransY = fy - ratio * (fy - mTransY);

            if (mScaleFactor <= 1.01f) { mScaleFactor = 1f; mTransX = 0; mTransY = 0; }
            clampTranslation();
            applyMatrix();
            return true;
        }
    }

    // =========================================================
    // DOUBLE TAP + FLING — the gallery-app feel
    // =========================================================
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mScaleFactor > 1.05f) {
                animateZoomTo(1f, 0f, 0f);
            } else {
                float targetScale = DOUBLE_TAP_ZOOM;
                float fx = e.getX(), fy = e.getY();
                float targetTransX = fx - targetScale * (fx - mTransX);
                float targetTransY = fy - targetScale * (fy - mTransY);
                animateZoomTo(targetScale, targetTransX, targetTransY);
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mScaleFactor <= 1.01f) return false; // no fling needed at rest scale
            float maxX = Math.max(0, (mScaleFactor - 1f) * getWidth()  / 2f);
            float maxY = Math.max(0, (mScaleFactor - 1f) * getHeight() / 2f);
            mScroller.forceFinished(true);
            mScroller.fling(
                    (int) mTransX, (int) mTransY,
                    (int) velocityX, (int) velocityY,
                    (int) -maxX, (int) maxX,
                    (int) -maxY, (int) maxY,
                    50, 50);
            postOnAnimation(mFlingRunnable);
            return true;
        }
    }

    private final Runnable mFlingRunnable = new Runnable() {
        @Override public void run() {
            if (mScroller.computeScrollOffset()) {
                mTransX = mScroller.getCurrX();
                mTransY = mScroller.getCurrY();
                applyMatrix();
                postOnAnimation(this);
            }
        }
    };

    /** Smooth animated zoom — used by double-tap. */
    private void animateZoomTo(float targetScale, float targetTransX, float targetTransY) {
        final float startScale = mScaleFactor;
        final float startTransX = mTransX;
        final float startTransY = mTransY;
        final long duration = 220L;
        final long startTime = System.currentTimeMillis();

        Runnable step = new Runnable() {
            @Override
            public void run() {
                float t = Math.min(1f, (System.currentTimeMillis() - startTime) / (float) duration);
                float eased = 1f - (1f - t) * (1f - t); // ease-out quad
                mScaleFactor = startScale + (targetScale - startScale) * eased;
                mTransX = startTransX + (targetTransX - startTransX) * eased;
                mTransY = startTransY + (targetTransY - startTransY) * eased;
                clampTranslation();
                applyMatrix();
                if (t < 1f) mHandler.postDelayed(this, 16);
                else if (mScaleFactor <= 1.01f) { mScaleFactor = 1f; mTransX = 0; mTransY = 0; applyMatrix(); }
            }
        };
        mHandler.post(step);
    }

    // =========================================================
    // BOUNDS
    // =========================================================
    private void clampTranslation() {
        float maxX = Math.max(0, (mScaleFactor - 1f) * getWidth()  / 2f);
        float maxY = Math.max(0, (mScaleFactor - 1f) * getHeight() / 2f);
        mTransX = clamp(mTransX, -maxX, maxX);
        mTransY = clamp(mTransY, -maxY, maxY);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(v, max));
    }

    private void applyMatrix() {
        mMatrix.reset();
        mMatrix.postScale(mScaleFactor, mScaleFactor, getWidth() / 2f, getHeight() / 2f);
        mMatrix.postTranslate(mTransX, mTransY);
        invalidate();
        if (mZoomListener != null) mZoomListener.onZoomChanged(mScaleFactor, mTransX, mTransY);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.concat(mMatrix);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    // =========================================================
    // PUBLIC API
    // =========================================================
    public void resetZoom() {
        mScroller.forceFinished(true);
        mScaleFactor = 1f; mTransX = 0f; mTransY = 0f;
        mMatrix.reset();
        invalidate();
        if (mZoomListener != null) mZoomListener.onZoomChanged(1f, 0f, 0f);
    }

    public boolean isZoomed() { return mScaleFactor > 1.01f; }
    public float getScaleFactor() { return mScaleFactor; }

    /** Converts a point in this view's local (screen) coordinates into content (unzoomed) coordinates. */
    public PointF screenToContent(float screenX, float screenY) {
        Matrix inverse = new Matrix();
        mMatrix.invert(inverse);
        float[] pts = { screenX, screenY };
        inverse.mapPoints(pts);
        return new PointF(pts[0], pts[1]);
    }
}