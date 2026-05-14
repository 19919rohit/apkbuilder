package neunix.pageflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public class PageFlipGLView extends GLSurfaceView {

    private PageCurlRenderer renderer;

    public PageFlipGLView(Context c, AttributeSet a) {
        super(c, a);

        setEGLContextClientVersion(2);

        renderer = new PageCurlRenderer(direction -> {
            // optional callback
        });

        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }
    
    public void setPages(Bitmap current, Bitmap next) {
        renderer.setPages(current, next);
        requestRender();
    }

    public void startFlip(int direction) {
        renderer.startFlip(direction);
        requestRender();
    }
}