package neunix.pageflow;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PageCurlRenderer implements GLSurfaceView.Renderer {

    public interface Callback {
        void onEnd(int direction);
    }

    private final Callback callback;

    private Bitmap currentBitmap;
    private Bitmap nextBitmap;

    private int currentTexture = -1;
    private int nextTexture = -1;

    private int program;

    private int aPosition;
    private int aTexCoord;

    private int uTexture;
    private int uCurl;

    private FloatBuffer vertexBuffer;
    private FloatBuffer texBuffer;

    private boolean animating = false;

    private float progress = 0f;

    private int direction = 1;

    private final float[] vertices = {

            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
    };

    private final float[] texCoords = {

            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
    };

    public PageCurlRenderer(Callback callback) {

        this.callback = callback;

        ByteBuffer vb =
                ByteBuffer.allocateDirect(
                        vertices.length * 4
                );

        vb.order(ByteOrder.nativeOrder());

        vertexBuffer = vb.asFloatBuffer();

        vertexBuffer.put(vertices);

        vertexBuffer.position(0);

        ByteBuffer tb =
                ByteBuffer.allocateDirect(
                        texCoords.length * 4
                );

        tb.order(ByteOrder.nativeOrder());

        texBuffer = tb.asFloatBuffer();

        texBuffer.put(texCoords);

        texBuffer.position(0);
    }

    // ---------------------------------------------------
    // SET PAGES
    // ---------------------------------------------------

    public void setPages(
            Bitmap current,
            Bitmap next
    ) {

        currentBitmap = current;

        nextBitmap = next;

        destroyTexture(currentTexture);
        destroyTexture(nextTexture);

        currentTexture = -1;
        nextTexture = -1;
    }

    // ---------------------------------------------------
    // START FLIP
    // ---------------------------------------------------

    public void startFlip(int dir) {

        if (animating) return;

        direction = dir;

        progress = 0f;

        animating = true;
    }

    // ---------------------------------------------------
    // OPENGL
    // ---------------------------------------------------

    @Override
    public void onSurfaceCreated(
            GL10 gl,
            EGLConfig config
    ) {

        GLES20.glClearColor(
                0f,
                0f,
                0f,
                1f
        );

        GLES20.glEnable(
                GLES20.GL_BLEND
        );

        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA
        );

        String vertexShader =

                "attribute vec4 aPosition;" +

                "attribute vec2 aTexCoord;" +

                "varying vec2 vTexCoord;" +

                "uniform float uCurl;" +

                "void main(){" +

                "vec4 pos = aPosition;" +

                // premium fake curl deformation
                "float strength = abs(pos.x);" +

                "pos.y += sin(strength * 3.1415) * uCurl * 0.18;" +

                "pos.x += uCurl * 0.25 * pos.x;" +

                "gl_Position = pos;" +

                "vTexCoord = aTexCoord;" +

                "}";

        String fragmentShader =

                "precision mediump float;" +

                "uniform sampler2D uTexture;" +

                "varying vec2 vTexCoord;" +

                "uniform float uCurl;" +

                "void main(){" +

                "vec4 color = texture2D(uTexture, vTexCoord);" +

                // dynamic shadow
                "float shadow = 1.0 - (uCurl * 0.35);" +

                "color.rgb *= shadow;" +

                "gl_FragColor = color;" +

                "}";

        int vs =
                loadShader(
                        GLES20.GL_VERTEX_SHADER,
                        vertexShader
                );

        int fs =
                loadShader(
                        GLES20.GL_FRAGMENT_SHADER,
                        fragmentShader
                );

        program = GLES20.glCreateProgram();

        GLES20.glAttachShader(program, vs);

        GLES20.glAttachShader(program, fs);

        GLES20.glLinkProgram(program);

        aPosition =
                GLES20.glGetAttribLocation(
                        program,
                        "aPosition"
                );

        aTexCoord =
                GLES20.glGetAttribLocation(
                        program,
                        "aTexCoord"
                );

        uTexture =
                GLES20.glGetUniformLocation(
                        program,
                        "uTexture"
                );

        uCurl =
                GLES20.glGetUniformLocation(
                        program,
                        "uCurl"
                );
    }

    @Override
    public void onSurfaceChanged(
            GL10 gl,
            int width,
            int height
    ) {

        GLES20.glViewport(
                0,
                0,
                width,
                height
        );
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT
        );

        if (currentBitmap == null) {
            return;
        }

        if (currentTexture == -1) {

            currentTexture =
                    createTexture(currentBitmap);
        }

        if (nextBitmap != null
                && nextTexture == -1) {

            nextTexture =
                    createTexture(nextBitmap);
        }

        // draw next under page
        if (animating && nextTexture != -1) {

            drawTexture(
                    nextTexture,
                    0f
            );
        }

        // curl intensity
        float curl =
                animating
                        ? progress
                        : 0f;

        // reverse effect
        if (direction < 0) {
            curl *= -1f;
        }

        drawTexture(
                currentTexture,
                curl
        );

        // animation engine
        if (animating) {

            progress += 0.045f;

            if (progress >= 1f) {

                progress = 1f;

                animating = false;

                if (callback != null) {
                    callback.onEnd(direction);
                }
            }
        }
    }

    // ---------------------------------------------------
    // DRAW
    // ---------------------------------------------------

    private void drawTexture(
            int texture,
            float curl
    ) {

        GLES20.glUseProgram(program);

        GLES20.glEnableVertexAttribArray(
                aPosition
        );

        GLES20.glVertexAttribPointer(
                aPosition,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
        );

        GLES20.glEnableVertexAttribArray(
                aTexCoord
        );

        GLES20.glVertexAttribPointer(
                aTexCoord,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                texBuffer
        );

        GLES20.glUniform1f(
                uCurl,
                curl
        );

        GLES20.glActiveTexture(
                GLES20.GL_TEXTURE0
        );

        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                texture
        );

        GLES20.glUniform1i(
                uTexture,
                0
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                0,
                4
        );

        GLES20.glDisableVertexAttribArray(
                aPosition
        );

        GLES20.glDisableVertexAttribArray(
                aTexCoord
        );
    }

    // ---------------------------------------------------
    // TEXTURES
    // ---------------------------------------------------

    private int createTexture(Bitmap bmp) {

        int[] textures = new int[1];

        GLES20.glGenTextures(
                1,
                textures,
                0
        );

        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                textures[0]
        );

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLUtils.texImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                bmp,
                0
        );

        return textures[0];
    }

    private void destroyTexture(int texture) {

        if (texture == -1) return;

        int[] textures = {texture};

        GLES20.glDeleteTextures(
                1,
                textures,
                0
        );
    }

    // ---------------------------------------------------
    // SHADER
    // ---------------------------------------------------

    private int loadShader(
            int type,
            String code
    ) {

        int shader =
                GLES20.glCreateShader(type);

        GLES20.glShaderSource(
                shader,
                code
        );

        GLES20.glCompileShader(shader);

        return shader;
    }
}