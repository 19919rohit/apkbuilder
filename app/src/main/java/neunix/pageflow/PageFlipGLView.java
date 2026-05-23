package neunix.pageflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class PageFlipGLView extends GLSurfaceView {

    public interface FlipListener {
        void onFlip(int direction);
    }

    private final PageCurlRenderer renderer;

    private FlipListener listener;

    private float downX;

    public PageFlipGLView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setEGLContextClientVersion(2);

        renderer = new PageCurlRenderer(direction -> {

            if (listener != null) {
                listener.onFlip(direction);
            }
        });

        setRenderer(renderer);

        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public void setPages(Bitmap current, Bitmap next) {

        queueEvent(() -> renderer.setPages(current, next));
    }

    public void setFlipListener(FlipListener l) {
        listener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {

        switch (e.getAction()) {

            case MotionEvent.ACTION_DOWN:

                downX = e.getX();
                return true;

            case MotionEvent.ACTION_UP:

                float dx = e.getX() - downX;

                if (Math.abs(dx) < 120f) {
                    return true;
                }

                if (dx < 0f) {
                    renderer.startFlip(1);
                } else {
                    renderer.startFlip(-1);
                }

                return true;
        }

        return true;
    }
}