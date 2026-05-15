package neunix.pageflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public class PageFlipGLView extends GLSurfaceView {

    public interface FlipListener {
        void onFlip(int direction);
    }

    private FlipListener flipListener;

    private final PageCurlRenderer renderer;

    public PageFlipGLView(Context c, AttributeSet a) {
        super(c, a);

        setEGLContextClientVersion(2);

        renderer = new PageCurlRenderer(direction -> {

            // forward renderer callback
            if (flipListener != null) {
                flipListener.onFlip(direction);
            }

            // keep rendering smooth
            requestRender();
        });

        setRenderer(renderer);

        // IMPORTANT
        // continuous animation for curl
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    // ---------------------------------------------------
    // LISTENER
    // ---------------------------------------------------

    public void setFlipListener(FlipListener listener) {
        this.flipListener = listener;
    }

    // ---------------------------------------------------
    // PAGE SETTER
    // ---------------------------------------------------

    public void setPages(Bitmap current, Bitmap next) {

        renderer.setPages(current, next);

        requestRender();
    }

    // ---------------------------------------------------
    // START FLIP
    // ---------------------------------------------------

    public void startFlip(int direction) {

        renderer.startFlip(direction);

        requestRender();
    }
}