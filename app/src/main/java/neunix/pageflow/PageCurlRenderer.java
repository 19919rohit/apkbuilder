package neunix.pageflow;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import android.opengl.GLSurfaceView;

public class PageCurlRenderer implements GLSurfaceView.Renderer {

    public interface Callback {
        void onFlipEnd(int direction);
    }

    private final Callback callback;

    private Bitmap current;
    private Bitmap next;

    private int texCurrent = -1;
    private int texNext = -1;

    // ---------- MESH CONFIG ----------
    private static final int COLS = 30;
    private static final int ROWS = 40;

    private final float[] vertices =
            new float[(COLS + 1) * (ROWS + 1) * 3];

    private final float[] texCoords =
            new float[(COLS + 1) * (ROWS + 1) * 2];

    private final short[] indices =
            new short[COLS * ROWS * 6];

    private final FloatBuffer vBuffer;
    private final FloatBuffer tBuffer;
    private final ShortBuffer iBuffer;

    // ---------- ANIMATION ----------
    private float progress = 0f;
    private boolean animating = false;
    private int direction = 1;

    public PageCurlRenderer(Callback callback) {
        this.callback = callback;

        vBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        tBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        iBuffer = ByteBuffer.allocateDirect(indices.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();

        buildMesh();
    }

    // ---------- PUBLIC API ----------

    public void setPages(Bitmap current, Bitmap next) {
        this.current = current;
        this.next = next;

        texCurrent = -1;
        texNext = -1;
    }

    public void startFlip(int dir) {
        direction = dir;
        progress = 0f;
        animating = true;
    }

    // ---------- OPENGL LIFECYCLE ----------

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(1f, 1f, 1f, 1f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (current == null) return;

        if (texCurrent == -1) texCurrent = loadTexture(current);
        if (next != null && texNext == -1) texNext = loadTexture(next);

        updateMesh(progress);

        drawTexture(texNext);
        drawTexture(texCurrent);

        if (animating) {

            progress += 0.025f;

            if (progress >= 1f) {
                progress = 1f;
                animating = false;
                callback.onFlipEnd(direction);
            }
        }
    }

    // ---------- MESH CREATION ----------

    private void buildMesh() {

        int vIndex = 0;
        int tIndex = 0;
        int iIndex = 0;

        for (int y = 0; y <= ROWS; y++) {

            float fy = (float) y / ROWS;

            for (int x = 0; x <= COLS; x++) {

                float fx = (float) x / COLS;

                // vertex
                vertices[vIndex++] = fx * 2f - 1f;
                vertices[vIndex++] = fy * 2f - 1f;
                vertices[vIndex++] = 0f;

                // texture
                texCoords[tIndex++] = fx;
                texCoords[tIndex++] = 1f - fy;
            }
        }

        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {

                int topLeft = y * (COLS + 1) + x;
                int topRight = topLeft + 1;
                int bottomLeft = (y + 1) * (COLS + 1) + x;
                int bottomRight = bottomLeft + 1;

                indices[iIndex++] = (short) topLeft;
                indices[iIndex++] = (short) bottomLeft;
                indices[iIndex++] = (short) topRight;

                indices[iIndex++] = (short) topRight;
                indices[iIndex++] = (short) bottomLeft;
                indices[iIndex++] = (short) bottomRight;
            }
        }

        vBuffer.put(vertices).position(0);
        tBuffer.put(texCoords).position(0);
        iBuffer.put(indices).position(0);
    }

    // ---------- CURL MATH (CORE ENGINE) ----------

    private void updateMesh(float p) {

        int index = 0;

        for (int y = 0; y <= ROWS; y++) {

            for (int x = 0; x <= COLS; x++) {

                float fx = vertices[index * 3];
                float fy = vertices[index * 3 + 1];

                float dx = fx * direction;

                float curl = p * 2.2f;

                float bend = (float) Math.sin(dx * Math.PI * curl) * 0.25f;
                float depth = -Math.abs(dx) * curl * 0.8f;

                vertices[index * 3] = fx + bend;
                vertices[index * 3 + 1] = fy;
                vertices[index * 3 + 2] = depth;

                index++;
            }
        }

        vBuffer.clear();
        vBuffer.put(vertices).position(0);
    }

    // ---------- DRAW ----------

    private void drawTexture(int texture) {

        if (texture == -1) return;

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA
        );

        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                indices.length,
                GLES20.GL_UNSIGNED_SHORT,
                iBuffer
        );
    }

    // ---------- TEXTURE ----------

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