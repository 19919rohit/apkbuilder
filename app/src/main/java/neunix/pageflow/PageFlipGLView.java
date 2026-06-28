package neunix.pageflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
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

    private FlipListener           flipListener;
    private TouchInterceptListener touchInterceptListener;

    // =========================================================
    // RENDERER
    // =========================================================

    private final PageCurlRenderer renderer;

    // =========================================================
    // GESTURE CONSTANTS
    // =========================================================

    private static final float DRAG_THRESHOLD_DP  = 8f;   // lower = more responsive
    private static final float FLING_MIN_VELOCITY  = 300f; // dp/s — easy to trigger
    private static final float COMMIT_THRESHOLD    = 0.38f;// slightly easier to commit

    // =========================================================
    // METRICS
    // =========================================================

    private final float density;
    private final float dragThresholdPx;
    private final float screenWidthPx;
    private final float screenHeightPx;

    // =========================================================
    // GESTURE STATE
    // =========================================================

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
        density          = context.getResources().getDisplayMetrics().density;
        dragThresholdPx  = DRAG_THRESHOLD_DP * density;
        screenWidthPx    = context.getResources().getDisplayMetrics().widthPixels;
        screenHeightPx   = context.getResources().getDisplayMetrics().heightPixels;
        renderer         = buildRenderer();
        init();
    }

    public PageFlipGLView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        density          = context.getResources().getDisplayMetrics().density;
        dragThresholdPx  = DRAG_THRESHOLD_DP * density;
        screenWidthPx    = context.getResources().getDisplayMetrics().widthPixels;
        screenHeightPx   = context.getResources().getDisplayMetrics().heightPixels;
        renderer         = buildRenderer();
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
            // Fire haptic on flip completion
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
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
                tracking    = true;
                dragStarted = false;
                dragStartX  = event.getX();
                dragStartY  = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (!tracking) break;
                handleMove(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragStarted) handleRelease(event);
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

            // More vertical than horizontal — scroll not swipe
            if (Math.abs(dy) > Math.abs(dx) * 1.4f) {
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

            // Pass touch origin in NDC to renderer
            float touchNdcX = pixelToNdcX(dragStartX);
            float touchNdcY = pixelToNdcY(dragStartY);
            renderer.onDragStart(dragDirection, touchNdcX, touchNdcY);

            // Subtle haptic on drag start
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
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

        float vxPx   = velocityTracker.getXVelocity();
        float vxDp   = vxPx / density;

        // Convert pixel velocity to NDC/ms for spring calculation
        float vxNdcMs = (vxPx / screenWidthPx) * 2f / 1000f;  // NDC per ms

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

        // Pass absolute velocity in NDC/ms — renderer uses this for spring
        renderer.commitDrag(commit, Math.abs(vxNdcMs));

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
    // NDC CONVERSION
    // =========================================================

    /** Pixel X → NDC X in [-1, +1] */
    private float pixelToNdcX(float px) {
        return (px / screenWidthPx) * 2f - 1f;
    }

    /** Pixel Y → NDC Y in [-1, +1] (Y axis flipped: top=+1, bottom=-1) */
    private float pixelToNdcY(float py) {
        return 1f - (py / screenHeightPx) * 2f;
    }

    // =========================================================
    // GESTURE DETECTOR
    // =========================================================

    private class FlipGestureListener
            extends GestureDetector.SimpleOnGestureListener {

        private static final float FLING_THRESHOLD_DP = 280f;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,
                               float vx, float vy) {

            if (dragStarted) return false;

            float vxDp = vx / density;
            if (Math.abs(vxDp) < FLING_THRESHOLD_DP) return false;

            int dir = vxDp < 0 ? 1 : -1;
            if (flipListener != null && !flipListener.canFlip(dir)) return false;

            float speed = clamp(Math.abs(vxDp) / 1000f, 0.6f, 2.0f);

            // Use fling start position as touch origin
            float touchNdcX = e1 != null ? pixelToNdcX(e1.getX()) :
                              (dir > 0 ? 1f : -1f);
            float touchNdcY = e1 != null ? pixelToNdcY(e1.getY()) : -0.8f;

            renderer.startFlip(dir, speed, touchNdcX, touchNdcY);
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {

            float x    = e.getX();
            float w    = getWidth();
            float zone = w * 0.28f;

            if (x < zone) {
                if (flipListener != null && flipListener.canFlip(-1)) {
                    float ndcX = pixelToNdcX(x);
                    float ndcY = pixelToNdcY(e.getY());
                    renderer.startFlip(-1, 1.1f, ndcX, ndcY);
                }
            } else if (x > w - zone) {
                if (flipListener != null && flipListener.canFlip(1)) {
                    float ndcX = pixelToNdcX(x);
                    float ndcY = pixelToNdcY(e.getY());
                    renderer.startFlip(1, 1.1f, ndcX, ndcY);
                }
            }

            return true;
        }
    }

    // =========================================================
    // MATH
    // =========================================================

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override public void onPause()  { super.onPause(); }
    @Override public void onResume() { super.onResume(); }
}