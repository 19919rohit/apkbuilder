package neunix.pageflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

public class PageFlipGLView extends GLSurfaceView {

    // =========================================================
    // LISTENERS
    // =========================================================

    public interface FlipListener {
        void onFlipCommitted(int direction);
        boolean canFlip(int direction);
    }

    public interface TouchInterceptListener {
        void onTouch();
    }

    private FlipListener         flipListener;
    private TouchInterceptListener touchInterceptListener;

    // =========================================================
    // RENDERER
    // =========================================================

    private final PageCurlRenderer renderer;

    // =========================================================
    // GESTURE STATE
    // =========================================================

    private static final float DRAG_THRESHOLD_DP  = 12f;
    private static final float FLING_MIN_VELOCITY  = 400f;
    private static final float COMMIT_THRESHOLD    = 0.42f;

    private final float density;
    private final float dragThresholdPx;
    private final float screenWidthPx;

    private VelocityTracker velocityTracker;
    private GestureDetector gestureDetector;

    private boolean tracking      = false;
    private boolean dragStarted   = false;
    private int     dragDirection = 0;
    private float   dragStartX    = 0f;
    private float   dragStartY    = 0f;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public PageFlipGLView(Context context) {
        super(context);
        density         = context.getResources().getDisplayMetrics().density;
        dragThresholdPx = DRAG_THRESHOLD_DP * density;
        screenWidthPx   = context.getResources().getDisplayMetrics().widthPixels;
        renderer        = buildRenderer();
        init();
    }

    public PageFlipGLView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        density         = context.getResources().getDisplayMetrics().density;
        dragThresholdPx = DRAG_THRESHOLD_DP * density;
        screenWidthPx   = context.getResources().getDisplayMetrics().widthPixels;
        renderer        = buildRenderer();
        init();
    }

    // =========================================================
    // INIT
    // =========================================================

    private void init() {
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        gestureDetector = new GestureDetector(getContext(), new FlipGestureListener());
        gestureDetector.setIsLongpressEnabled(false);
    }

    private PageCurlRenderer buildRenderer() {
        return new PageCurlRenderer(direction -> post(() -> {
            if (flipListener != null) {
                flipListener.onFlipCommitted(direction);
            }
        }));
    }

    // =========================================================
    // PUBLIC API
    // =========================================================

    public void setFlipListener(FlipListener listener) {
        flipListener = listener;
    }

    public void setOnTouchInterceptListener(TouchInterceptListener listener) {
        touchInterceptListener = listener;
    }

    public void setPages(Bitmap current, Bitmap next, Bitmap prev) {
        renderer.setPages(current, next, prev);
        requestRender();
    }

    // =========================================================
    // TOUCH
    // =========================================================

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Notify PdfActivity so it can show controls on any touch
        if (touchInterceptListener != null) {
            touchInterceptListener.onTouch();
        }

        gestureDetector.onTouchEvent(event);

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                tracking      = true;
                dragStarted   = false;
                dragStartX    = event.getX();
                dragStartY    = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (!tracking) break;
                handleMove(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragStarted) {
                    handleRelease(event);
                }
                resetTracking();
                break;
        }

        return true;
    }

    private void handleMove(MotionEvent event) {

        float dx = event.getX() - dragStartX;
        float dy = event.getY() - dragStartY;

        if (!dragStarted) {

            if (Math.abs(dx) < dragThresholdPx) return;

            // More vertical than horizontal — treat as scroll, ignore
            if (Math.abs(dy) > Math.abs(dx) * 1.2f) {
                tracking = false;
                return;
            }

            int tentativeDir = dx < 0 ? 1 : -1;

            if (flipListener != null && !flipListener.canFlip(tentativeDir)) {
                tracking = false;
                return;
            }

            dragDirection = tentativeDir;
            dragStarted   = true;
            renderer.onDragStart(dragDirection);
        }

        float travel = (dragDirection > 0)
                ? dragStartX - event.getX()
                : event.getX() - dragStartX;

        float progress = Math.max(0f, travel / screenWidthPx);
        renderer.setDragProgress(dragDirection, progress);
    }

    private void handleRelease(MotionEvent event) {

        velocityTracker.computeCurrentVelocity(
                1000, ViewConfiguration.getMaximumFlingVelocity());

        float vxDp = velocityTracker.getXVelocity() / density;

        float travel = (dragDirection > 0)
                ? dragStartX - event.getX()
                : event.getX() - dragStartX;

        float currentProgress = Math.max(0f, travel / screenWidthPx);

        boolean flingForward = (dragDirection > 0 && vxDp < -FLING_MIN_VELOCITY)
                            || (dragDirection < 0 && vxDp >  FLING_MIN_VELOCITY);

        boolean flingBack    = (dragDirection > 0 && vxDp >  FLING_MIN_VELOCITY)
                            || (dragDirection < 0 && vxDp < -FLING_MIN_VELOCITY);

        boolean commit;
        if      (flingForward) commit = true;
        else if (flingBack)    commit = false;
        else                   commit = currentProgress >= COMMIT_THRESHOLD;

        float remainingVelocity = Math.abs(vxDp);
        renderer.commitDrag(commit, remainingVelocity);

        if (commit && flipListener != null) {
            flipListener.onFlipCommitted(dragDirection);
        }
    }

    private void resetTracking() {
        tracking    = false;
        dragStarted = false;
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    // =========================================================
    // GESTURE DETECTOR
    // =========================================================

    private class FlipGestureListener
            extends GestureDetector.SimpleOnGestureListener {

        private static final float FLING_THRESHOLD_DP = 350f;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,
                               float vx, float vy) {

            if (dragStarted) return false;

            float vxDp = vx / density;
            if (Math.abs(vxDp) < FLING_THRESHOLD_DP) return false;

            int dir = vxDp < 0 ? 1 : -1;

            if (flipListener != null && !flipListener.canFlip(dir)) return false;

            float speed = Math.min(Math.abs(vxDp) / 1200f, 1.5f);
            renderer.startFlip(dir, speed);

            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {

            float x    = e.getX();
            float w    = getWidth();
            float zone = w * 0.30f;

            if (x < zone) {
                if (flipListener != null && flipListener.canFlip(-1)) {
                    renderer.startFlip(-1, 1f);
                }
            } else if (x > w - zone) {
                if (flipListener != null && flipListener.canFlip(1)) {
                    renderer.startFlip(1, 1f);
                }
            }

            return true;
        }
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}