package neunix.pagevibe;

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
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.List;

public class GalleryZoomView extends FrameLayout {

    public interface OnZoomChangeListener {
        void onZoomChanged(float scale, float transX, float transY);
    }

    private static final float MIN_ZOOM        = 1.0f;
    private static final float MAX_ZOOM        = 5.0f;
    private static final float DOUBLE_TAP_ZOOM = 2.5f;

    private static final float OVERSCROLL_EXTRA_FRACTION = 0.35f;

    // =========================================================
    // CENTER ZOOM ZONE — a SQUARE centered in the view.
    // Everything OUTSIDE it (every edge, every corner) behaves as plain
    // page-flip. Inside it, a gesture can never trigger a flip, no
    // matter how it moves or ends. 0.5 = square side is half the
    // screen's shorter dimension, centered.
    // =========================================================
    private static final float CENTER_ZONE_FRACTION = 0.5f;

    private final Matrix      mMatrix  = new Matrix();
    private final OverScroller mScroller;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector      mGestureDetector;
    private int                  mTouchSlop;

    private float   mScaleFactor   = 1f;
    private float   mTransX        = 0f;
    private float   mTransY        = 0f;
    private float   mLastTouchX, mLastTouchY;
    private int     mActivePointer = MotionEvent.INVALID_POINTER_ID;
    private boolean mIsScaling     = false;
    private boolean mDrawPassThrough = false;

    private boolean mAnimating = false;

    private OnZoomChangeListener mZoomListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // =========================================================
    // GESTURE ARBITRATION
    //  IDLE                 - no gesture in progress.
    //  PENDING               - single finger down OUTSIDE the square;
    //                          still deciding tap / swipe / pinch-start.
    //  CENTER_ZONE           - single finger down INSIDE the square;
    //                          can never forward to CurlView.
    //  FORWARDING_TO_CHILD   - confirmed swipe/tap being forwarded live.
    //  OWNING_ZOOM           - a 2nd finger landed; this view owns the
    //                          gesture until it TRULY ends (see the fix
    //                          below — this used to get stuck here
    //                          forever after any pinch).
    // =========================================================
    private enum Arbitration { IDLE, PENDING, CENTER_ZONE, FORWARDING_TO_CHILD, OWNING_ZOOM }
    private Arbitration mArbitration = Arbitration.IDLE;
    private final List<MotionEvent> mPendingBuffer = new ArrayList<>();
    private float mPendingDownX, mPendingDownY;
    private CurlView mCurlView;

    public GalleryZoomView(Context c) {
        super(c);
        mScroller = new OverScroller(c, new DecelerateInterpolator());
        init(c);
    }
    public GalleryZoomView(Context c, AttributeSet a) {
        super(c, a);
        mScroller = new OverScroller(c, new DecelerateInterpolator());
        init(c);
    }
    public GalleryZoomView(Context c, AttributeSet a, int d) {
        super(c, a, d);
        mScroller = new OverScroller(c, new DecelerateInterpolator());
        init(c);
    }

    private void init(Context c) {
        mScaleDetector  = new ScaleGestureDetector(c, new ScaleListener());
        mGestureDetector = new GestureDetector(c, new GestureListener());
        mGestureDetector.setIsLongpressEnabled(false);
        mTouchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        setWillNotDraw(false);
    }

    public void setOnZoomChangeListener(OnZoomChangeListener l) { mZoomListener = l; }

    public void setDrawPassThrough(boolean passThrough) {
        mDrawPassThrough = passThrough;
    }

    public void attachCurlView(CurlView curlView) {
        mCurlView = curlView;
    }

    // =========================================================
    // ZONE DETECTION
    // =========================================================
    private boolean isOutsideCenterSquare(float x, float y) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return true;

        float squareSide = Math.min(w, h) * CENTER_ZONE_FRACTION;
        float half = squareSide / 2f;
        float cx = w / 2f;
        float cy = h / 2f;

        boolean insideSquare = x > (cx - half) && x < (cx + half)
                            && y > (cy - half) && y < (cy + half);
        return !insideSquare;
    }

    // =========================================================
    // INTERCEPT
    // =========================================================
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mDrawPassThrough && ev.getPointerCount() == 1) {
            return false;
        }
        return true;
    }

    // =========================================================
    // TOUCH — manual gesture arbitration with center-square zoom zone
    // =========================================================
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();

        if (mScaleFactor > 1.01f || mArbitration == Arbitration.OWNING_ZOOM) {
            return handleZoomGesture(ev);
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float downX = ev.getX();
                float downY = ev.getY();
                mPendingDownX = downX;
                mPendingDownY = downY;
                clearPendingBuffer();

                if (isOutsideCenterSquare(downX, downY)) {
                    mArbitration = Arbitration.PENDING;
                    mPendingBuffer.add(MotionEvent.obtain(ev));
                } else {
                    mArbitration = Arbitration.CENTER_ZONE;
                }

                mScaleDetector.onTouchEvent(ev);
                mGestureDetector.onTouchEvent(ev);
                return true;
            }

            case MotionEvent.ACTION_POINTER_DOWN:
                clearPendingBuffer();
                mArbitration = Arbitration.OWNING_ZOOM;
                mIsScaling = true;
                return handleZoomGesture(ev);

            case MotionEvent.ACTION_MOVE:
                if (mArbitration == Arbitration.PENDING) {
                    mPendingBuffer.add(MotionEvent.obtain(ev));
                    mScaleDetector.onTouchEvent(ev);
                    mGestureDetector.onTouchEvent(ev);
                    float dx = ev.getX() - mPendingDownX;
                    float dy = ev.getY() - mPendingDownY;
                    if (Math.hypot(dx, dy) > mTouchSlop) {
                        flushPendingToChild();
                    }
                    return true;
                } else if (mArbitration == Arbitration.CENTER_ZONE) {
                    mScaleDetector.onTouchEvent(ev);
                    mGestureDetector.onTouchEvent(ev);
                    return true;
                } else if (mArbitration == Arbitration.FORWARDING_TO_CHILD) {
                    forwardToChild(ev);
                    return true;
                }
                return handleZoomGesture(ev);

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mArbitration == Arbitration.PENDING) {
                    mPendingBuffer.add(MotionEvent.obtain(ev));
                    mGestureDetector.onTouchEvent(ev);
                    return true;
                } else if (mArbitration == Arbitration.CENTER_ZONE) {
                    mGestureDetector.onTouchEvent(ev);
                    endArbitration();
                    return true;
                } else if (mArbitration == Arbitration.FORWARDING_TO_CHILD) {
                    forwardToChild(ev);
                    endArbitration();
                    return true;
                } else {
                    boolean result = handleZoomGesture(ev);
                    endArbitration();
                    return result;
                }
        }
        return true;
    }

    private void flushPendingToChild() {
        mArbitration = Arbitration.FORWARDING_TO_CHILD;
        List<MotionEvent> toSend = new ArrayList<>(mPendingBuffer);
        mPendingBuffer.clear();
        for (MotionEvent buffered : toSend) {
            forwardToChild(buffered);
            try { buffered.recycle(); } catch (Throwable ignored) {}
        }
    }

    private void forwardToChild(MotionEvent ev) {
        if (mCurlView == null) return;
        try { mCurlView.dispatchTouchEvent(ev); } catch (Throwable ignored) {}
    }

    private void clearPendingBuffer() {
        for (MotionEvent e : mPendingBuffer) {
            try { e.recycle(); } catch (Throwable ignored) {}
        }
        mPendingBuffer.clear();
    }

    private void endArbitration() {
        mArbitration = Arbitration.IDLE;
        mIsScaling = false;
        mActivePointer = MotionEvent.INVALID_POINTER_ID;
    }

    // =========================================================
    // ACTUAL ZOOM/PAN GESTURE HANDLING
    // =========================================================
    private boolean handleZoomGesture(MotionEvent ev) {
        mScaleDetector.onTouchEvent(ev);
        mGestureDetector.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mScroller.forceFinished(true);
                mAnimating = false;
                mLastTouchX    = ev.getX();
                mLastTouchY    = ev.getY();
                mActivePointer = ev.getPointerId(0);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                mScroller.forceFinished(true);
                mIsScaling = true;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mIsScaling && !mAnimating && mScaleFactor > 1.01f) {
                    int idx = ev.findPointerIndex(mActivePointer);
                    if (idx >= 0) {
                        mTransX += ev.getX(idx) - mLastTouchX;
                        mTransY += ev.getY(idx) - mLastTouchY;
                        clampTranslationSoft();
                        applyMatrix();
                        mLastTouchX = ev.getX(idx);
                        mLastTouchY = ev.getY(idx);
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP: {
                // NOTE: this fires when ONE of several fingers lifts but
                // at least one remains down — it is deliberately NOT a
                // "gesture fully ended" signal, so arbitration state is
                // intentionally left untouched here. Only the true final
                // lift (ACTION_UP) or a cancel below resets arbitration.
                int upIdx = ev.getActionIndex();
                if (ev.getPointerId(upIdx) == mActivePointer) {
                    int newIdx     = upIdx == 0 ? 1 : 0;
                    mLastTouchX    = ev.getX(newIdx);
                    mLastTouchY    = ev.getY(newIdx);
                    mActivePointer = ev.getPointerId(newIdx);
                }
                mIsScaling = false;
                settleOverscroll();
                break;
            }

            case MotionEvent.ACTION_CANCEL:
                // FIX: this used to only reset mIsScaling/mActivePointer,
                // leaving mArbitration stuck at OWNING_ZOOM forever after
                // any pinch — which meant EVERY subsequent touch kept
                // routing straight into zoom handling and never reached
                // the tap/swipe logic that triggers a page flip, even
                // after zooming back out to 1.0x. endArbitration() resets
                // mArbitration to IDLE too, which is the actual fix.
                endArbitration();
                if (mScaleFactor < MIN_ZOOM) {
                    animateZoomTo(MIN_ZOOM, 0f, 0f);
                } else {
                    settleOverscroll();
                }
                break;

            case MotionEvent.ACTION_UP:
                // Same fix as ACTION_CANCEL above — this is the TRUE end
                // of the gesture (the last finger just lifted), so this
                // is exactly where arbitration must return to IDLE.
                endArbitration();
                if (mScroller.isFinished()) {
                    settleOverscroll();
                }
                break;
        }
        return true;
    }

    // =========================================================
    // PINCH ZOOM
    // =========================================================
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector d) {
            mIsScaling = true;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector d) {
            float prev      = mScaleFactor;
            float newScale  = clamp(prev * d.getScaleFactor(), MIN_ZOOM, MAX_ZOOM);
            float ratio     = newScale / prev;
            mScaleFactor    = newScale;

            float fx = d.getFocusX();
            float fy = d.getFocusY();
            mTransX = fx - ratio * (fx - mTransX);
            mTransY = fy - ratio * (fy - mTransY);

            clampTranslation();
            applyMatrix();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector d) {
            if (mScaleFactor <= 1.05f) {
                animateZoomTo(MIN_ZOOM, 0f, 0f);
            } else {
                settleOverscroll();
            }
            mIsScaling = false;
        }
    }

    // =========================================================
    // DOUBLE TAP + FLING + SINGLE TAP CONFIRMATION
    // =========================================================
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mArbitration == Arbitration.PENDING) {
                flushPendingToChild();
            }
            endArbitration();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            clearPendingBuffer();
            mArbitration = Arbitration.IDLE;

            if (mScaleFactor > 1.05f) {
                animateZoomTo(MIN_ZOOM, 0f, 0f);
            } else {
                float fx = e.getX();
                float fy = e.getY();
                float ts = DOUBLE_TAP_ZOOM;
                float targetTx = fx - ts * (fx - getWidth()  / 2f);
                float targetTy = fy - ts * (fy - getHeight() / 2f);
                float maxX = Math.max(0, (ts - 1f) * getWidth()  / 2f);
                float maxY = Math.max(0, (ts - 1f) * getHeight() / 2f);
                targetTx = clamp(targetTx, -maxX, maxX);
                targetTy = clamp(targetTy, -maxY, maxY);
                animateZoomTo(ts, targetTx, targetTy);
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,
                               float velocityX, float velocityY) {
            if (mScaleFactor <= 1.01f || mAnimating) return false;
            float maxX = Math.max(0, (mScaleFactor - 1f) * getWidth()  / 2f);
            float maxY = Math.max(0, (mScaleFactor - 1f) * getHeight() / 2f);
            mScroller.forceFinished(true);
            mScroller.fling(
                    (int) mTransX, (int) mTransY,
                    (int) velocityX, (int) velocityY,
                    (int) -maxX, (int) maxX,
                    (int) -maxY, (int) maxY,
                    30, 30);
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

    // =========================================================
    // SMOOTH ANIMATED ZOOM / SETTLE
    // =========================================================
    private void animateZoomTo(float targetScale, float targetTx, float targetTy) {
        mScroller.forceFinished(true);
        mAnimating = true;

        final float startScale = mScaleFactor;
        final float startTx    = mTransX;
        final float startTy    = mTransY;
        final long  duration   = 220L;
        final long  startTime  = System.currentTimeMillis();

        Runnable step = new Runnable() {
            @Override public void run() {
                float elapsed = System.currentTimeMillis() - startTime;
                float t       = Math.min(1f, elapsed / duration);
                float eased   = 1f - (1f - t) * (1f - t) * (1f - t);

                mScaleFactor = startScale + (targetScale - startScale) * eased;
                mTransX      = startTx    + (targetTx    - startTx)    * eased;
                mTransY      = startTy    + (targetTy    - startTy)    * eased;

                clampTranslation();
                applyMatrix();

                if (t < 1f) {
                    mHandler.postDelayed(this, 16);
                } else {
                    mScaleFactor = targetScale;
                    mTransX      = targetTx;
                    mTransY      = targetTy;
                    if (mScaleFactor <= 1.01f) {
                        mScaleFactor = 1f;
                        mTransX      = 0f;
                        mTransY      = 0f;
                    }
                    clampTranslation();
                    applyMatrix();
                    mAnimating = false;
                }
            }
        };
        mHandler.post(step);
    }

    private void settleOverscroll() {
        float maxX = Math.max(0, (mScaleFactor - 1f) * getWidth()  / 2f);
        float maxY = Math.max(0, (mScaleFactor - 1f) * getHeight() / 2f);
        float targetX = clamp(mTransX, -maxX, maxX);
        float targetY = clamp(mTransY, -maxY, maxY);
        if (Math.abs(targetX - mTransX) > 0.5f || Math.abs(targetY - mTransY) > 0.5f) {
            animateZoomTo(mScaleFactor, targetX, targetY);
        }
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

    private void clampTranslationSoft() {
        float maxX = Math.max(0, (mScaleFactor - 1f) * getWidth()  / 2f);
        float maxY = Math.max(0, (mScaleFactor - 1f) * getHeight() / 2f);
        mTransX = rubberBand(mTransX, -maxX, maxX, maxX * OVERSCROLL_EXTRA_FRACTION);
        mTransY = rubberBand(mTransY, -maxY, maxY, maxY * OVERSCROLL_EXTRA_FRACTION);
    }

    private static float rubberBand(float value, float min, float max, float range) {
        if (range <= 0.01f) return clamp(value, min, max);
        if (value < min) {
            float excess = min - value;
            return min - (float) (range * (1 - Math.exp(-excess / range)));
        }
        if (value > max) {
            float excess = value - max;
            return max + (float) (range * (1 - Math.exp(-excess / range)));
        }
        return value;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(v, max));
    }

    // =========================================================
    // MATRIX
    // =========================================================
    private void applyMatrix() {
        mMatrix.reset();
        mMatrix.postScale(mScaleFactor, mScaleFactor, getWidth() / 2f, getHeight() / 2f);
        mMatrix.postTranslate(mTransX, mTransY);
        invalidate();
        if (mZoomListener != null)
            mZoomListener.onZoomChanged(mScaleFactor, mTransX, mTransY);
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
        mAnimating   = false;
        mScaleFactor = 1f;
        mTransX      = 0f;
        mTransY      = 0f;
        mMatrix.reset();
        invalidate();
        if (mZoomListener != null) mZoomListener.onZoomChanged(1f, 0f, 0f);
    }

    public boolean isZoomed() { return mScaleFactor > 1.01f; }
    public float getScaleFactor() { return mScaleFactor; }
    public float getTransX() { return mTransX; }
    public float getTransY() { return mTransY; }

    public PointF screenToContent(float screenX, float screenY) {
        Matrix inv = new Matrix();
        mMatrix.invert(inv);
        float[] pts = { screenX, screenY };
        inv.mapPoints(pts);
        return new PointF(pts[0], pts[1]);
    }
}