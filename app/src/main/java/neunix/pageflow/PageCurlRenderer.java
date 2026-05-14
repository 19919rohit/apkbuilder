package neunix.pageflow;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import android.opengl.GLSurfaceView;

public class PageCurlRenderer implements GLSurfaceView.Renderer {

    interface Callback {
        void onEnd(int direction);
    }

    private final Callback callback;

    private Bitmap current;
    private Bitmap next;

    private int texCurrent = 0;
    private int texNext = 0;

    private float progress = 0f;
    private boolean animating = false;
    private int direction = 1;

    public PageCurlRenderer(Callback cb) {
        this.callback = cb;
    }

    public void setPages(Bitmap c, Bitmap n) {
        current = c;
        next = n;

        texCurrent = 0;
        texNext = 0;
    }

    public void startFlip(int dir) {
        direction = dir;
        progress = 0f;
        animating = true;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0, 0, 0, 1);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (current == null) return;

        if (texCurrent == 0) texCurrent = load(current);
        if (next != null && texNext == 0) texNext = load(next);

        draw(texCurrent);
        if (next != null) draw(texNext);

        if (animating) {
            progress += 0.04f;

            if (progress >= 1f) {
                progress = 1f;
                animating = false;
                if (callback != null) callback.onEnd(direction);
            }
        }
    }

    private void draw(int tex) {
        if (tex == 0) return;

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
    }

    private int load(Bitmap bmp) {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);

        return t[0];
    }
}