package neunix.pageflow;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PageCurlRenderer implements GLSurfaceView.Renderer {

    interface Callback {
        void onEnd(int direction);
    }

    private final Callback callback;

    private Bitmap current;
    private Bitmap next;

    private int texCurrent;
    private int texNext;

    // GRID
    private static final int COLS = 40;
    private static final int ROWS = 60;

    private final float[] vertices = new float[(COLS + 1) * (ROWS + 1) * 3];

    private final FloatBuffer vertexBuffer;

    private float progress = 0f;
    private int direction = 1;
    private boolean animating = false;

    public PageCurlRenderer(Callback c) {
        callback = c;

        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
    }

    public void setPages(Bitmap c, Bitmap n, Bitmap unused) {
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

        if (texCurrent == 0) texCurrent = loadTexture(current);
        if (next != null && texNext == 0) texNext = loadTexture(next);

        updateMesh(progress);

        drawPage(texNext, false);
        drawPage(texCurrent, true);

        if (animating) {

            progress += 0.03f;

            if (progress >= 1f) {

                progress = 1f;
                animating = false;

                callback.onEnd(direction);
            }
        }
    }

    // 🔥 REAL CURL MATH
    private void updateMesh(float p) {

        int index = 0;

        float width = 2f;
        float height = 2f;

        for (int y = 0; y <= ROWS; y++) {

            float fy = (float) y / ROWS;

            for (int x = 0; x <= COLS; x++) {

                float fx = (float) x / COLS;

                float px = fx * width - 1f;
                float py = fy * height - 1f;

                float fold = p * 2f;

                // center fold axis
                float dx = px;

                // CURL EFFECT (CORE MAGIC)
                float bend = (float) Math.sin(dx * Math.PI * fold) * 0.25f;

                float z = -Math.abs(dx) * fold * 0.5f;

                vertices[index++] = px + bend;
                vertices[index++] = py;
                vertices[index++] = z;
            }
        }

        vertexBuffer.clear();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);
    }

    private void drawPage(int texture, boolean front) {

        if (texture == 0) return;

        GLES20.glEnable(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // NOTE:
        // This is simplified renderer.
        // Full index buffer + shader version = next upgrade.

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0,
                (COLS + 1) * (ROWS + 1));
    }

    private int loadTexture(Bitmap bmp) {

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