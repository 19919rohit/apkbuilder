package neunix.pageflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public class PageFlipGLView extends GLSurfaceView {

    private final PageCurlRenderer renderer;

    public interface FlipListener {
        void onFlipComplete(int direction);
    }

    public PageFlipGLView(Context c, AttributeSet a) {
        super(c, a);

        setEGLContextClientVersion(2);

        renderer = new PageCurlRenderer(dir -> {
            if (flipListener != null)
                flipListener.onFlipComplete(dir);
        });

        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    private FlipListener flipListener;

    public void setOnFlipCompleteListener(FlipListener l) {
        flipListener = l;
    }

    public void setPages(Bitmap prev, Bitmap curr, Bitmap next) {
        renderer.setPages(prev, curr, next);
    }
}